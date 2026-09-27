package kr.rpgcraft.feature;

import kr.rpgcraft.Keys;
import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.data.PlayerData;
import kr.rpgcraft.gui.Gui;
import kr.rpgcraft.item.ItemData;
import kr.rpgcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 강화. +8 부터 파괴 확률 발생(나무위키 4R), 파괴 방지권 / 강화 확률 10% 증가권 사용 가능.
 * 노말~유니크 최대 +10, 레전드 +15. 성공/파괴 확률 표는 config.yml 에서 조정.
 */
public class EnhanceManager {
    public enum Result { SUCCESS, FAIL, DOWN, DESTROY, PROTECTED }

    private final RpgCraft plugin;

    public EnhanceManager(RpgCraft plugin) {
        this.plugin = plugin;
    }

    private double tableValue(String path, int lvl, double def) {
        List<Double> l = plugin.getConfig().getDoubleList(path);
        if (l.isEmpty()) return def;
        return l.get(Math.min(lvl, l.size() - 1));
    }

    public double successRate(Player p, ItemStack it, boolean rateTicket) {
        int lvl = ItemData.enh(it);
        PlayerData d = plugin.data().get(p);
        double rate = tableValue("enhance.success", lvl, 50) + d.stats.enhanceRate;
        if (d.blacksmith) rate += plugin.getConfig().getDouble("enhance.blacksmith-bonus", 5);
        if (rateTicket) rate += 10;
        return Math.max(1, Math.min(100, rate));
    }

    public double destroyRate(ItemStack it) {
        return tableValue("enhance.destroy", ItemData.enh(it), 0);
    }

    public long cost(ItemStack it) {
        int lvl = ItemData.enh(it);
        return (long) (plugin.getConfig().getLong("enhance.money-base", 2000) * Math.pow(lvl + 1, 2));
    }

    public String materialId(ItemStack it) {
        int lvl = ItemData.enh(it);
        return lvl < 5 ? "mat_iron" : lvl < 8 ? "mat_silver" : lvl < 11 ? "mat_gold" : "mat_crystal";
    }

    public int materialAmount(ItemStack it) {
        return ItemData.enh(it) + 1;
    }

    public String check(Player p, ItemStack it) {
        if (it == null || it.getType().isAir()) return "강화할 장비를 넣어주세요.";
        if (!ItemData.enhanceable(it)) return "강화할 수 없는 아이템입니다.";
        if (ItemData.enh(it) >= ItemData.maxEnhance(it)) return "이미 최대 강화입니다.";
        if (!plugin.economy().has(p, cost(it))) return "강화 비용 " + Text.money(cost(it)) + "이 부족합니다.";
        if (count(p, materialId(it)) < materialAmount(it)) return plugin.items().get(materialId(it)).name + " " + materialAmount(it) + "개가 필요합니다.";
        return null;
    }

    public Result attempt(Player p, ItemStack it, boolean protect, boolean rateTicket) {
        plugin.data().get(p).counters.merge("enhance_tries", 1.0, Double::sum);
        double rate = successRate(p, it, rateTicket);
        double destroy = destroyRate(it);
        int lvl = ItemData.enh(it);
        plugin.economy().take(p, cost(it));
        take(p, materialId(it), materialAmount(it));
        plugin.passives().track(p, "enhance_attempts", 1);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (r.nextDouble() * 100 < rate) {
            ItemData.setInt(it, Keys.ENH, lvl + 1);
            ItemData.refresh(it);
            if (lvl + 1 >= 8) kr.rpgcraft.util.Text.announce(Text.PREFIX + Text.c("&e" + p.getName() + "&f님이 " + it.getItemMeta().getDisplayName() + "&f 강화에 성공했습니다!"));
            return Result.SUCCESS;
        }
        if (destroy > 0 && r.nextDouble() * 100 < destroy) {
            if (protect) return Result.PROTECTED;
            return Result.DESTROY;
        }
        if (lvl >= plugin.getConfig().getInt("enhance.fail-down-from", 5) && r.nextDouble() < plugin.getConfig().getDouble("enhance.fail-down-chance", 0.5)) {
            ItemData.setInt(it, Keys.ENH, lvl - 1);
            ItemData.refresh(it);
            return Result.DOWN;
        }
        return Result.FAIL;
    }

    public int count(Player p, String id) {
        int n = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) if (ItemData.is(it, id)) n += it.getAmount();
        return n;
    }

    public boolean take(Player p, String id, int amount) {
        if (count(p, id) < amount) return false;
        ItemStack[] cont = p.getInventory().getStorageContents();
        for (ItemStack it : cont) {
            if (amount <= 0) break;
            if (!ItemData.is(it, id)) continue;
            int t = Math.min(amount, it.getAmount());
            it.setAmount(it.getAmount() - t);
            amount -= t;
        }
        return true;
    }

    public void open(Player p) {
        new EnhanceGui(p).open(p);
    }

    /** 10: 장비, 12: 파괴 방지권, 14: 확률 증가권, 16: 강화 버튼 */
    private class EnhanceGui extends Gui {
        private final Player owner;

        EnhanceGui(Player p) {
            super(3, "&6장비 강화", "enhance");
            owner = p;
            render();
        }

        void render() {
            for (int i = 0; i < 27; i++) if (!editable(i)) inv.setItem(i, null);
            clearButtons();
            set(1, button(Material.IRON_SWORD, "&f▼ 장비"));
            set(26, button(Material.NETHER_STAR, "&d잠재능력 →"), e -> plugin.potentials().open(owner));
            set(18, button(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE, "&d한계 돌파 →"), e -> plugin.limitBreak().open(owner));
            set(3, button(Material.PAPER, "&f▼ 파괴 방지권 (선택)"));
            set(5, button(Material.PAPER, "&f▼ 강화 확률 10% 증가권 (선택)"));
            ItemStack eq = inv.getItem(10);
            FileConfiguration c = plugin.getConfig();
            String problem = check(owner, eq);
            if (eq != null && !eq.getType().isAir() && ItemData.enhanceable(eq) && ItemData.enh(eq) < ItemData.maxEnhance(eq)) {
                boolean rt = ItemData.is(inv.getItem(14), "ticket_rate10");
                int lvl = ItemData.enh(eq);
                set(16, button(problem == null ? Material.ANVIL : Material.BARRIER, problem == null ? "&a&l강화하기" : "&c강화 불가",
                        "&f+" + lvl + " → &e+" + (lvl + 1),
                        "&f성공 확률: &a" + String.format("%.1f", successRate(owner, eq, rt)) + "%",
                        "&f파괴 확률: &c" + String.format("%.1f", destroyRate(eq)) + "% &7(실패 시)",
                        lvl >= c.getInt("enhance.fail-down-from", 5) ? "&7실패 시 일정 확률로 강화 단계 하락" : "&7실패 시 단계 유지",
                        "&f비용: &e" + Text.money(cost(eq)),
                        "&f재료: &e" + plugin.items().get(materialId(eq)).name + " x" + materialAmount(eq),
                        problem == null ? "" : "&c" + problem), e -> doEnhance());
            } else {
                set(16, button(Material.BARRIER, "&7강화할 장비를 넣어주세요", problem == null ? "" : "&c" + problem));
            }
            fill(0, 26);
        }

        void doEnhance() {
            ItemStack eq = inv.getItem(10);
            String problem = check(owner, eq);
            if (problem != null) {
                Text.msg(owner, "&c" + problem);
                return;
            }
            ItemStack prot = inv.getItem(12);
            ItemStack rate = inv.getItem(14);
            boolean useProt = ItemData.is(prot, "ticket_protect") && ItemData.enh(eq) >= 7;
            boolean useRate = ItemData.is(rate, "ticket_rate10");
            Result r = attempt(owner, eq, useProt, useRate);
            if (useRate) rate.setAmount(rate.getAmount() - 1);
            switch (r) {
                case SUCCESS -> {
                    owner.playSound(owner.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1.4f);
                    owner.getWorld().spawnParticle(Particle.TOTEM, owner.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.2);
                    kr.rpgcraft.util.Fx.sphere(owner.getLocation().add(0, 1, 0), 1.4, 60, org.bukkit.Color.fromRGB(0xFFD23F), 1.2f);
                    kr.rpgcraft.util.Fx.shockwave(plugin, owner.getLocation(), 3, org.bukkit.Color.fromRGB(0xFFF1B0));
                    int now = ItemData.enh(eq);
                    if (now == kr.rpgcraft.item.ItemLore.STAGE_PERFECT) plugin.visuals().perfected(owner, eq);
                    else if (now == kr.rpgcraft.item.ItemLore.STAGE_ENGRAVED) owner.sendTitle(Text.c("&e&l『각인』"), Text.c("&f무기에 룬이 새겨지며 빛나기 시작합니다"), 0, 35, 10);
                    else owner.sendTitle(Text.c("&a&l강화 성공!"), Text.c("&e+" + now), 0, 30, 10);
                }
                case FAIL -> {
                    owner.playSound(owner.getLocation(), Sound.BLOCK_ANVIL_LAND, 1f, 0.8f);
                    owner.sendTitle(Text.c("&c강화 실패"), Text.c("&7단계 유지"), 0, 30, 10);
                }
                case DOWN -> {
                    owner.playSound(owner.getLocation(), Sound.BLOCK_ANVIL_LAND, 1f, 0.6f);
                    owner.sendTitle(Text.c("&c강화 실패"), Text.c("&7강화 단계가 하락했습니다 (+" + ItemData.enh(eq) + ")"), 0, 30, 10);
                }
                case PROTECTED -> {
                    prot.setAmount(prot.getAmount() - 1);
                    owner.playSound(owner.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 1f);
                    owner.sendTitle(Text.c("&6파괴 방지!"), Text.c("&7파괴 방지권이 장비를 지켰습니다"), 0, 30, 10);
                }
                case DESTROY -> {
                    inv.setItem(10, null);
                    owner.playSound(owner.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 0.6f);
                    owner.getWorld().spawnParticle(Particle.SMOKE_LARGE, owner.getLocation().add(0, 1, 0), 40, 0.4, 0.5, 0.4, 0.05);
                    kr.rpgcraft.util.Fx.shockwave(plugin, owner.getLocation(), 3, org.bukkit.Color.fromRGB(0x444444));
                    owner.sendTitle(Text.c("&4&l장비 파괴"), Text.c("&7장비가 산산조각 났습니다..."), 0, 40, 10);
                    kr.rpgcraft.util.Text.announce(Text.PREFIX + Text.c("&7" + owner.getName() + "님의 장비가 강화 중 파괴되었습니다..."));
                }
            }
            render();
        }

        @Override
        public boolean editable(int raw) {
            return raw == 10 || raw == 12 || raw == 14;
        }

        @Override
        public boolean allowShiftIn() {
            return false;
        }

        @Override
        protected void onEditableClick(InventoryClickEvent e) {
            Bukkit.getScheduler().runTask(plugin, this::render);
        }

        @Override
        protected void onBottomClick(InventoryClickEvent e) {
            if (!e.isShiftClick()) return;
            ItemStack cur = e.getCurrentItem();
            if (cur == null || cur.getType().isAir()) return;
            int target = ItemData.enhanceable(cur) ? 10 : ItemData.is(cur, "ticket_protect") ? 12 : ItemData.is(cur, "ticket_rate10") ? 14 : -1;
            if (target < 0 || inv.getItem(target) != null) return;
            if (target == 10) {
                inv.setItem(10, cur.clone());
                e.setCurrentItem(null);
            } else {
                ItemStack one = cur.clone();
                one.setAmount(1);
                inv.setItem(target, one);
                cur.setAmount(cur.getAmount() - 1);
            }
            Bukkit.getScheduler().runTask(plugin, this::render);
        }

        @Override
        public void onClose(InventoryCloseEvent e) {
            giveBack(owner, 10, 12, 14);
            plugin.stats().refresh(owner);
        }
    }
}
