package kr.rpgcraft.world;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.gui.Gui;
import kr.rpgcraft.item.ItemData;
import kr.rpgcraft.util.Fx;
import kr.rpgcraft.util.Text;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * 전설의 대장장이 「렉스」.
 * 3시간마다 10분 동안 스폰 근처에 나타나, 재료를 가져오면 「렉스의 명작」을 만들어 준다.
 * 명작은 상점에서 팔지 않는 최상위 무기(신화 등급, Lv.220)이며 무기마다 고유 스킬을 가진다.
 * 재료: 초월 4단계 무기 + 고레벨 전리품 + 최상급 결정 + 돈
 */
public class PenderManager implements Listener {
    private record Recipe(String result, String baseWeapon, Map<String, Integer> mats, long money) {}

    private static final List<Recipe> RECIPES = List.of(
            new Recipe("pender_sword", "trans_sword_4", Map.of("loot_crown", 3, "loot_core", 15, "crystal_top", 20, "loot_eye", 5), 15_000_000),
            new Recipe("pender_dagger", "trans_dagger_4", Map.of("loot_crown", 3, "loot_ink", 30, "crystal_top", 20, "loot_frost", 20), 15_000_000),
            new Recipe("pender_axe", "trans_axe_4", Map.of("loot_crown", 3, "loot_horn", 25, "crystal_top", 20, "loot_core", 15), 15_000_000),
            new Recipe("pender_shield", "trans_shield_4", Map.of("loot_crown", 3, "loot_scale", 30, "crystal_top", 20, "loot_totem", 25), 15_000_000),
            new Recipe("pender_spear", "spirit_xuanwu", Map.of("loot_crown", 4, "loot_eye", 10, "crystal_top", 25, "fish_legend", 1), 20_000_000));

    private final RpgCraft plugin;
    private final NamespacedKey KEY;
    private UUID npc;
    private long until;

    public PenderManager(RpgCraft plugin) {
        this.plugin = plugin;
        this.KEY = new NamespacedKey(plugin, "pender");
        long every = plugin.getConfig().getLong("pender.interval-minutes", 180) * 60 * 20;
        Bukkit.getScheduler().runTaskTimer(plugin, this::appear, every, every);
        Bukkit.getScheduler().runTask(plugin, this::cleanup);
    }

    private void cleanup() {
        for (World w : Bukkit.getWorlds())
            for (Villager v : w.getEntitiesByClass(Villager.class))
                if (v.getPersistentDataContainer().has(KEY, PersistentDataType.BYTE)) v.remove();
        npc = null;
    }

    public boolean present() {
        return npc != null && System.currentTimeMillis() < until;
    }

    public boolean appear() {
        cleanup();
        World w = Bukkit.getWorlds().get(0);
        Location base = w.getSpawnLocation();
        Random r = new Random();
        for (int i = 0; i < 20; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = 15 + r.nextDouble() * 35;
            Block top = kr.rpgcraft.util.Locs.surface(w, base.clone().add(Math.cos(a) * d, 0, Math.sin(a) * d));
            if (top.isLiquid()) continue;
            Location l = top.getLocation().add(0.5, 1, 0.5);
            Villager v = w.spawn(l, Villager.class, x -> {
                x.setAI(false);
                x.setInvulnerable(true);
                x.setSilent(true);
                x.setProfession(Villager.Profession.WEAPONSMITH);
                x.setVillagerLevel(5);
                x.setCustomName(Text.c("&6&l⚒ 전설의 대장장이 렉스"));
                x.setCustomNameVisible(true);
                x.setPersistent(false);
                x.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, (byte) 1);
            });
            top.getRelative(1, 1, 0).setType(Material.ANVIL, false);
            npc = v.getUniqueId();
            long stay = plugin.getConfig().getLong("pender.stay-minutes", 10);
            until = System.currentTimeMillis() + stay * 60_000;
            Text.announce(Text.PREFIX + Text.c("&6&l⚒ 전설의 대장장이 렉스&f가 스폰 근처에 나타났습니다!"));
            for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.7f, 0.6f);
            Block anvil = top.getRelative(1, 1, 0);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Entity e = Bukkit.getEntity(v.getUniqueId());
                if (e != null) {
                    e.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, e.getLocation().add(0, 1, 0), 20, 0.3, 0.6, 0.3, 0.02);
                    e.remove();
                    Text.announce(Text.PREFIX + Text.c("&7렉스가 떠났습니다."));
                }
                if (anvil.getType() == Material.ANVIL) anvil.setType(Material.AIR, false);
                npc = null;
            }, stay * 60 * 20);
            return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !e.getRightClicked().getPersistentDataContainer().has(KEY, PersistentDataType.BYTE)) return;
        e.setCancelled(true);
        new PenderGui(e.getPlayer()).open(e.getPlayer());
    }

    private int count(Player p, String id) {
        int n = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) if (id.equals(ItemData.id(it))) n += it.getAmount();
        return n;
    }

    private void take(Player p, String id, int amount) {
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (amount <= 0) return;
            if (!id.equals(ItemData.id(it))) continue;
            int t = Math.min(amount, it.getAmount());
            it.setAmount(it.getAmount() - t);
            amount -= t;
        }
    }

    private class PenderGui extends Gui {
        PenderGui(Player p) {
            super(3, "&8⚒ 렉스의 대장간");
            int slot = 11;
            for (Recipe r : RECIPES) {
                var t = plugin.items().get(r.result());
                List<String> lore = new ArrayList<>();
                lore.add("&7필요 재료:");
                boolean ok = count(p, r.baseWeapon()) >= 1;
                lore.add((ok ? "&a✔ " : "&c✘ ") + plugin.items().get(r.baseWeapon()).name + " x1 &8(바탕이 될 무기)");
                for (Map.Entry<String, Integer> m : r.mats().entrySet()) {
                    int have = count(p, m.getKey());
                    boolean enough = have >= m.getValue();
                    ok &= enough;
                    lore.add((enough ? "&a✔ " : "&c✘ ") + plugin.items().get(m.getKey()).name + " " + have + "/" + m.getValue());
                }
                boolean money = plugin.economy().has(p, r.money());
                ok &= money;
                lore.add((money ? "&a✔ " : "&c✘ ") + Text.money(r.money()));
                lore.add("");
                lore.add(ok ? "&e▶ 클릭해서 제작 의뢰" : "&8재료를 모두 모아 오게. — 렉스");
                boolean can = ok;
                ItemStack icon = plugin.items().create(r.result(), 1);
                var m = icon.getItemMeta();
                List<String> full = new ArrayList<>();   // 능력치는 숨김
                full.add("");
                for (String l : lore) full.add(Text.c(l));
                m.setLore(full);
                icon.setItemMeta(m);
                set(slot, icon, e -> {
                    if (!can || !present()) return;
                    if (count(p, r.baseWeapon()) < 1 || !plugin.economy().take(p, r.money())) return;
                    take(p, r.baseWeapon(), 1);
                    r.mats().forEach((id, n) -> take(p, id, n));
                    ItemStack out = plugin.items().create(r.result(), 1);
                    for (ItemStack l : p.getInventory().addItem(out).values()) p.getWorld().dropItemNaturally(p.getLocation(), l);
                    p.closeInventory();
                    p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 0.8f);
                    p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                    Fx.helix(plugin, p, 2.6, 1.0, 30, Color.fromRGB(0xFFD23F), Color.fromRGB(0xFF7A1F));
                    Text.announce(Text.PREFIX + Text.c("&6&l" + Text.name(p) + "&f님이 &6「" + t.name + "」&f을(를) 손에 넣었습니다!"));
                });
                slot++;
            }
            set(22, button(Material.ANVIL, "&6렉스", "&7\"좋은 재료 없이는 명작도 없지.\"", "&7초월 4단계 무기를 바탕으로 두드려 준다네."));
            fill(0, 26);
        }
    }
}
