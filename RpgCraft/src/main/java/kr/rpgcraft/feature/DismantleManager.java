package kr.rpgcraft.feature;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.item.Grade;
import kr.rpgcraft.item.ItemData;
import kr.rpgcraft.item.ItemTemplate;
import kr.rpgcraft.stat.Stat;
import kr.rpgcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * 장비 분해 (v5.2.4): /분해 창의 위 5줄에 장비(무기 · 방어구 · 활)를 넣고 모루를 누르면 재료로 바꿔 준다.
 * 받는 재료는 장비의 레벨 제한 · 등급 · 강화 수치로 정해지며(무작위 없음), 버튼에 미리 표시된다.
 * 장비가 아닌 물건은 분해하지 않고 창을 닫을 때 돌려준다.
 */
public class DismantleManager implements Listener {
    private static final int BUTTON = 49, INFO = 45, AREA = 45;

    public static class Holder implements InventoryHolder {
        long confirmUntil;
        public Inventory getInventory() { return null; }
    }

    private final RpgCraft plugin;

    public DismantleManager(RpgCraft plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player p) {
        Holder h = new Holder();
        Inventory inv = Bukkit.createInventory(h, 54, Text.c("&8장비 분해 &7(장비를 넣고 모루 클릭)"));
        ItemStack pane = pane();
        for (int i = AREA; i < 54; i++) inv.setItem(i, pane);
        inv.setItem(INFO, icon(Material.BOOK, "&e분해 안내", List.of(
                "&7위 칸에 무기 · 방어구 · 활을 넣으세요.",
                "&7레벨 제한 · 등급 · 강화 수치가 높을수록",
                "&7더 좋은 재료가 나옵니다.",
                "&7장비가 아닌 물건은 닫을 때 돌려받습니다.",
                "&c분해한 장비는 되돌릴 수 없습니다.")));
        p.openInventory(inv);
        refresh(inv);
    }

    // ------------------------------------------------------------------ 보상 계산
    /** 장비 한 개(수량 1)의 분해 결과 (무작위 없음) */
    public Map<String, Integer> rewards(ItemStack it) {
        Map<String, Integer> out = new LinkedHashMap<>();
        ItemTemplate t = ItemData.template(it);
        if (t == null || !(t.category.isEquipment() || t.category == kr.rpgcraft.item.Category.BOW)) return out;
        int lv = (int) ItemData.baseStats(it).get(Stat.LEVEL_REQ);
        Grade g = ItemData.grade(it);
        double gm = switch (g) { case RARE -> 1.5; case UNIQUE -> 2.2; case LEGEND -> 3.2; case MYTHIC -> 4.5; default -> 1.0; };
        int enh = ItemData.enh(it);
        String crystal;
        if (lv < 20) { add(out, "mat_stone", 3 * gm); add(out, "mat_iron", 1 * gm); add(out, "wood_log", 1 * gm); crystal = "crystal_low"; }
        else if (lv < 40) { add(out, "mat_iron", 4 * gm); add(out, "mat_silver", 1 * gm); crystal = "crystal_low"; }
        else if (lv < 70) { add(out, "mat_silver", 4 * gm); add(out, "mat_gold", 1 * gm); add(out, "crystal_low", 2 * gm); crystal = "crystal_mid"; }
        else if (lv < 110) { add(out, "mat_gold", 4 * gm); add(out, "mat_crystal", 1 * gm); add(out, "crystal_mid", 2 * gm); crystal = "crystal_mid"; }
        else if (lv < 200) { add(out, "mat_crystal", 3 * gm); add(out, "crystal_high", 1 * gm); crystal = "crystal_high"; }
        else { add(out, "mat_crystal", 5 * gm); add(out, "crystal_high", 2 * gm); crystal = "crystal_top"; }
        if (enh >= 3) add(out, crystal, enh / 3.0);   // 강화 3단계마다 결정 1개
        if (g == Grade.LEGEND || g == Grade.MYTHIC) add(out, "ore_" + new String[]{"black", "green", "red", "blue", "gray"}[Math.floorMod(t.id.hashCode(), 5)], g == Grade.MYTHIC ? 2 : 1);
        if (g == Grade.MYTHIC) add(out, "crystal_top", 1);
        out.keySet().removeIf(id -> plugin.items().get(id) == null);
        return out;
    }

    private static void add(Map<String, Integer> m, String id, double n) {
        int v = (int) Math.max(1, Math.round(n));
        m.merge(id, v, Integer::sum);
    }

    private boolean dismantlable(ItemStack it) {
        return it != null && it.getType() != Material.AIR && !rewards(it).isEmpty();
    }

    private Map<String, Integer> total(Inventory inv) {
        Map<String, Integer> sum = new LinkedHashMap<>();
        for (int i = 0; i < AREA; i++) {
            ItemStack it = inv.getItem(i);
            if (!dismantlable(it)) continue;
            for (var en : rewards(it).entrySet()) sum.merge(en.getKey(), en.getValue() * it.getAmount(), Integer::sum);
        }
        return sum;
    }

    private boolean valuable(Inventory inv) {
        for (int i = 0; i < AREA; i++) {
            ItemStack it = inv.getItem(i);
            if (!dismantlable(it)) continue;
            Grade g = ItemData.grade(it);
            if (ItemData.enh(it) >= 10 || g == Grade.LEGEND || g == Grade.MYTHIC) return true;
        }
        return false;
    }

    private void refresh(Inventory inv) {
        Map<String, Integer> sum = total(inv);
        int n = 0;
        for (int i = 0; i < AREA; i++) if (dismantlable(inv.getItem(i))) n += inv.getItem(i).getAmount();
        List<String> lore = new ArrayList<>();
        if (sum.isEmpty()) lore.add("&7분해할 장비가 없습니다.");
        else {
            lore.add("&7장비 &f" + n + "개 &7→ 받을 재료:");
            for (var en : sum.entrySet()) lore.add("  &f" + plugin.items().get(en.getKey()).name + " &ex" + en.getValue());
            lore.add("");
            if (valuable(inv)) lore.add("&c⚠ 레전드 이상 또는 +10 이상 장비 포함 &7(두 번 클릭해 확인)");
            lore.add("&e▶ 클릭하여 분해");
        }
        inv.setItem(BUTTON, icon(sum.isEmpty() ? Material.GRAY_DYE : Material.ANVIL, sum.isEmpty() ? "&7분해하기" : "&6&l분해하기", lore));
    }

    // ------------------------------------------------------------------ 이벤트
    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h) || !(e.getWhoClicked() instanceof Player p)) return;
        Inventory top = e.getView().getTopInventory();
        int raw = e.getRawSlot();
        if (raw >= AREA && raw < 54) {   // 아래 줄(버튼 · 장식)은 못 건드림
            e.setCancelled(true);
            if (raw == BUTTON) dismantle(p, top, h);
            return;
        }
        if (e.getAction() == org.bukkit.event.inventory.InventoryAction.COLLECT_TO_CURSOR) { e.setCancelled(true); return; }
        Bukkit.getScheduler().runTask(plugin, () -> { if (p.getOpenInventory().getTopInventory() == top) refresh(top); });
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder)) return;
        for (int s : e.getRawSlots()) if (s >= AREA && s < 54) { e.setCancelled(true); return; }
        Inventory top = e.getView().getTopInventory();
        Bukkit.getScheduler().runTask(plugin, () -> refresh(top));
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder) || !(e.getPlayer() instanceof Player p)) return;
        for (int i = 0; i < AREA; i++) {   // 남은 물건은 돌려줌
            ItemStack it = e.getInventory().getItem(i);
            if (it == null || it.getType() == Material.AIR) continue;
            e.getInventory().setItem(i, null);
            for (ItemStack left : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
        }
    }

    private void dismantle(Player p, Inventory inv, Holder h) {
        Map<String, Integer> sum = total(inv);
        if (sum.isEmpty()) { Text.actionBar(p, "&7분해할 장비를 위 칸에 넣으세요."); return; }
        long now = System.currentTimeMillis();
        if (valuable(inv) && now > h.confirmUntil) {
            h.confirmUntil = now + 4000;
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.8f);
            Text.msg(p, "&c⚠ 레전드 이상 또는 +10 이상 장비가 있습니다. &f4초 안에 한 번 더 누르면 분해합니다.");
            return;
        }
        h.confirmUntil = 0;
        int n = 0;
        for (int i = 0; i < AREA; i++) {
            ItemStack it = inv.getItem(i);
            if (!dismantlable(it)) continue;
            n += it.getAmount();
            inv.setItem(i, null);
        }
        for (var en : sum.entrySet()) {
            int left = en.getValue();
            while (left > 0) {
                ItemStack give = plugin.items().create(en.getKey(), 1);
                if (give == null) break;
                int stack = Math.min(left, give.getMaxStackSize());
                give.setAmount(stack);
                left -= stack;
                for (ItemStack over : p.getInventory().addItem(give).values()) p.getWorld().dropItemNaturally(p.getLocation(), over);
            }
        }
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.1f);
        p.playSound(p.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 1f, 1f);
        StringBuilder sb = new StringBuilder();
        for (var en : sum.entrySet()) sb.append(sb.length() == 0 ? "" : "&7, ").append("&f").append(plugin.items().get(en.getKey()).name).append(" x").append(en.getValue());
        Text.msg(p, "&a장비 " + n + "개를 분해했습니다: " + sb);
        refresh(inv);
    }

    // ------------------------------------------------------------------ 도구
    private static ItemStack pane() {
        ItemStack it = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(" ");
        it.setItemMeta(m);
        return it;
    }

    private static ItemStack icon(Material mat, String name, List<String> lore) {
        if (kr.rpgcraft.gui.Gui.iconFor(mat, name) != null) return kr.rpgcraft.gui.Gui.button(mat, name, lore.toArray(new String[0]));   // v5.10.44 디자인 아이콘
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(Text.c(name));
        List<String> l = new ArrayList<>();
        for (String s : lore) l.add(Text.c(s));
        m.setLore(l);
        it.setItemMeta(m);
        return it;
    }
}
