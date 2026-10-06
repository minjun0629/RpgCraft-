package io.versaera.platform.bukkit.listener;

import io.versaera.application.GameServices;
import io.versaera.domain.crafting.CraftPlan;
import io.versaera.domain.crafting.MaterialInput;
import io.versaera.domain.crafting.MaterialSlot;
import io.versaera.domain.crafting.Recipe;
import io.versaera.domain.item.ItemInstance;
import io.versaera.domain.item.ItemType;
import io.versaera.domain.skill.Mastery;
import io.versaera.platform.bukkit.Async;
import io.versaera.platform.bukkit.Ui;
import io.versaera.platform.bukkit.binding.ItemCodec;
import io.versaera.platform.bukkit.ui.Menu;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * 제작대: 월드의 블록을 우클릭해 그 분야의 제작 창을 연다 (명령어 없이).
 * 모루=대장 · 베틀=재봉 · 제작대=가죽 · 훈연기=요리 · 양조기=연금 · 석재 절단기=조각 · 대장장이 작업대=수리.
 * 재료는 인벤토리에서 품질이 높은 것부터 골라 <b>먼저 빼고</b> 서버에 제작을 요청한다. 실패하면 재료는 배달함으로 돌아온다.
 */
public final class StationListener implements Listener {
    private static final Map<Material, String> STATIONS = Map.of(
            Material.ANVIL, "smithing", Material.LOOM, "tailoring", Material.CRAFTING_TABLE, "leatherwork",
            Material.SMOKER, "cooking", Material.BREWING_STAND, "alchemy", Material.STONECUTTER, "sculpting",
            Material.SMITHING_TABLE, "repair");

    private final GameServices s;
    private final Async async;
    private final ItemCodec codec;
    private final SessionListener sessions;
    private final Set<UUID> busy = new HashSet<>();

    public StationListener(GameServices s, Async async, ItemCodec codec, SessionListener sessions) {
        this.s = s;
        this.async = async;
        this.codec = codec;
        this.sessions = sessions;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND || e.getClickedBlock() == null) return;
        String d = STATIONS.get(e.getClickedBlock().getType());
        if (d == null || e.getPlayer().isSneaking()) return;
        e.setCancelled(true);
        if (d.equals("repair")) repair(e.getPlayer());
        else open(e.getPlayer(), d);
    }

    private void open(Player p, String discipline) {
        String id = p.getUniqueId().toString();
        async.run("station", () -> {
            int lv = s.growth.level(id, discipline);
            List<Recipe> known = new ArrayList<>();
            for (Recipe r : s.crafting.all()) if (r.discipline().equals(discipline) && s.crafting.knows(id, r)) known.add(r);
            return new Object[]{lv, known};
        }, res -> {
            int lv = (int) res[0];
            @SuppressWarnings("unchecked") List<Recipe> known = (List<Recipe>) res[1];
            Menu m = new Menu(Math.max(1, Math.min(6, (known.size() + 9) / 9 + 1)), "&8" + s.growth.discipline(discipline).name() + " · " + Mastery.label(lv));
            int slot = 0;
            for (Recipe r : known) {
                ItemType out = codec.types().get(r.output());
                List<String> lines = new ArrayList<>();
                for (MaterialSlot ms : r.slots()) lines.add((ms.optional() ? "&8+ " : "&7· ") + slotLabel(ms) + " ×" + ms.count());
                lines.add(lv >= r.minLevel() ? "&f" + Mastery.label(r.minLevel()) : "&c" + Mastery.label(r.minLevel()));
                Material icon = Material.matchMaterial(out.material());
                m.set(slot++, Menu.icon(icon == null ? Material.PAPER : icon, "&f" + r.name(), lines), ev -> {
                    p.closeInventory();
                    craft(p, r);
                });
            }
            m.open(p);
        }, p);
    }

    private String slotLabel(MaterialSlot ms) {
        if (ms.accepts().startsWith("type:")) return codec.types().get(ms.accepts().substring(5)).name();
        return switch (ms.accepts().substring(4)) {
            case "wood" -> "목재";
            case "metal" -> "금속";
            case "mineral" -> "광물";
            case "gem" -> "보석";
            case "dye" -> "염료";
            case "stone" -> "석재";
            case "marble" -> "대리석";
            case "cloth" -> "천";
            case "leather" -> "가죽";
            case "fiber" -> "섬유";
            case "fish" -> "생선";
            case "grain" -> "곡물";
            case "herb" -> "약초";
            case "seasoning" -> "양념";
            default -> ms.accepts().substring(4);
        };
    }

    /** 인벤토리의 묶음 재료를 MaterialInput 으로 (같은 종류 · 같은 품질끼리) */
    private Map<String, int[]> stock(Player p) {
        Map<String, int[]> m = new LinkedHashMap<>();
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (codec.instanceId(it) != null) continue;
            String t = codec.typeId(it);
            if (t == null) continue;
            int q = codec.bulkQuality(it);
            m.computeIfAbsent(t + "@" + q, k -> new int[]{q, 0})[1] += it.getAmount();
        }
        return m;
    }

    private String toolId(Player p, String tag) {
        if (tag == null) return null;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            String t = codec.typeId(it), iid = codec.instanceId(it);
            if (t != null && iid != null && codec.types().get(t).hasTag(tag)) return iid;
        }
        return null;
    }

    private void craft(Player p, Recipe r) {
        if (!busy.add(p.getUniqueId())) return;   // 연타 방지: 한 사람당 제작 하나씩
        List<MaterialInput> pool = new ArrayList<>();
        for (Map.Entry<String, int[]> e : stock(p).entrySet()) {
            String type = e.getKey().substring(0, e.getKey().indexOf('@'));
            pool.add(new MaterialInput(type, codec.types().get(type).tags(), e.getValue()[0], e.getValue()[1]));
        }
        List<CraftPlan.Assignment> plan;
        try {
            plan = CraftPlan.assign(r, pool);
        } catch (io.versaera.domain.common.DomainException ex) {
            busy.remove(p.getUniqueId());
            p.sendMessage(Ui.error(ex.getMessage()));
            return;
        }
        if (r.tool() != null && toolId(p, r.tool()) == null) {
            busy.remove(p.getUniqueId());
            p.sendMessage(Ui.error("도구가 필요합니다"));
            return;
        }
        // 재료를 먼저 뺀다 (실패하면 서비스가 배달함으로 돌려준다)
        List<MaterialInput> used = new ArrayList<>();
        for (CraftPlan.Assignment a : plan) {
            int need = a.slot().count();
            ItemStack[] c = p.getInventory().getStorageContents();
            for (int i = 0; i < c.length && need > 0; i++) {
                if (c[i] == null || codec.instanceId(c[i]) != null || !a.input().typeId().equals(codec.typeId(c[i])) || codec.bulkQuality(c[i]) != a.input().quality()) continue;
                int take = Math.min(need, c[i].getAmount());
                c[i].setAmount(c[i].getAmount() - take);
                if (c[i].getAmount() <= 0) c[i] = null;
                need -= take;
            }
            p.getInventory().setStorageContents(c);
            if (need > 0) {   // 그 사이 인벤토리가 바뀜 → 뺀 만큼 되돌리고 중단
                for (MaterialInput u : used) p.getInventory().addItem(codec.bulk(u.typeId(), u.quality(), u.count()));
                int got = a.slot().count() - need;
                if (got > 0) p.getInventory().addItem(codec.bulk(a.input().typeId(), a.input().quality(), got));
                busy.remove(p.getUniqueId());
                p.sendMessage(Ui.error("재료가 바뀌었습니다. 다시 시도하세요"));
                return;
            }
            used.add(new MaterialInput(a.input().typeId(), a.input().tags(), a.input().quality(), a.slot().count()));
        }
        String id = p.getUniqueId().toString(), name = p.getName(), tool = toolId(p, r.tool());
        async.run("craft", () -> {
            int tq = -1;
            if (tool != null) {
                ItemInstance t = s.items.find(tool).filter(x -> x.custody().ownedBy(id)).orElse(null);
                if (t != null && !t.broken()) {
                    tq = t.quality();
                    s.items.wear(tool, id, 1, false);
                }
            }
            return s.crafting.craft(id, name, r.id(), used, tq, null, new SplittableRandom(), null);
        }, res -> {
            busy.remove(p.getUniqueId());
            p.sendMessage(Ui.info(r.name() + " · " + io.versaera.domain.item.Quality.gradeName(res.quality()) + " " + res.quality() / 10
                    + (res.xp() > 0 ? "  &7+" + res.xp() : "")));
            sessions.deliver(p);
        }, err -> {
            busy.remove(p.getUniqueId());
            sessions.deliver(p);   // 실패 → 재료가 배달함으로 돌아왔으니 바로 돌려줌
        }, p);
    }

    private void repair(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        String iid = codec.instanceId(hand);
        if (iid == null) {
            p.sendMessage(Ui.error("고칠 장비를 손에 드세요"));
            return;
        }
        String id = p.getUniqueId().toString();
        async.run("repair", () -> {
            int lv = s.growth.level(id, "repair");
            var r = s.items.repair(iid, id, id, lv, null);
            s.growth.addXp(id, "repair", 8, Math.max(1, lv));
            s.growth.record(id, "repair.count", 1);
            return new Object[]{r, s.items.find(iid).orElseThrow()};
        }, res -> {
            var r = (io.versaera.domain.item.Repair.Result) res[0];
            ItemInstance it = (ItemInstance) res[1];
            int slot = p.getInventory().getHeldItemSlot();
            if (iid.equals(codec.instanceId(p.getInventory().getItem(slot)))) p.getInventory().setItem(slot, codec.unique(it));
            p.sendMessage(Ui.info("수리 " + it.durability() + "/" + it.maxDurability() + (r.maxAfter() < r.maxBefore() ? "  &c최대 -" + (r.maxBefore() - r.maxAfter()) : "")));
        }, p);
    }
}
