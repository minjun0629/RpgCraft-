package kr.rpgcraft.feature;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.data.PlayerData;
import kr.rpgcraft.gui.Gui;
import kr.rpgcraft.item.Category;
import kr.rpgcraft.item.ItemData;
import kr.rpgcraft.item.ItemTemplate;
import kr.rpgcraft.passive.Passive;
import kr.rpgcraft.util.Text;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 포션가방: 회복 포션을 가방에 보관하고 단축키(F)로 즉시 사용.
 * 쉬프트+F 는 퀵슬롯 액티브 스킬 사용.
 */
public class PotionManager implements Listener {
    private static final List<String> POTIONS = List.of("potion_1", "potion_2", "potion_3", "potion_4");
    private final RpgCraft plugin;

    public PotionManager(RpgCraft plugin) {
        this.plugin = plugin;
    }

    public boolean drink(Player p, String id) {
        PlayerData d = plugin.data().get(p);
        if (d.onCooldown("potion")) {
            Text.actionBar(p, "&c포션 쿨타임 " + String.format("%.1f", d.remaining("potion") / 1000.0) + "초");
            return false;
        }
        ItemTemplate t = plugin.items().get(id);
        if (t == null) return false;
        d.cooldown("potion", (long) (plugin.getConfig().getDouble("potion.cooldown-seconds", 2) * 1000));
        plugin.health().heal(p, t.value * plugin.stats().hpScale());
        p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_DRINK, 1f, 1f);
        p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 2, 0), 5, 0.3, 0.2, 0.3);
        plugin.passives().track(p, "potions", 1);
        if (d.has(Passive.POTION_SIDE) && ThreadLocalRandom.current().nextDouble() < 0.25) {   // 너프: 25% · 10초 · +25
            d.potionBuffUntil = System.currentTimeMillis() + 10_000;
            Text.msg(p, "&d물약 부작용! 10초간 공격력 +25");
        }
        return true;
    }

    /** 가방에서 선택된 포션 사용 (없으면 가장 좋은 포션) */
    public void useFromBag(Player p) {
        PlayerData d = plugin.data().get(p);
        String id = d.selectedPotion != null && d.potionBag.getOrDefault(d.selectedPotion, 0) > 0 ? d.selectedPotion : null;
        if (id == null) {
            for (int i = POTIONS.size() - 1; i >= 0; i--) if (d.potionBag.getOrDefault(POTIONS.get(i), 0) > 0) id = POTIONS.get(i);
        }
        if (id == null) {
            Text.actionBar(p, "&c포션가방이 비어있습니다. (/포션가방)");
            return;
        }
        if (drink(p, id)) {
            d.potionBag.merge(id, -1, Integer::sum);
            Text.actionBar(p, "&a" + plugin.items().get(id).name + " 사용 &7(남은 개수 " + d.potionBag.get(id) + ")");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (!plugin.getConfig().getBoolean("potion.swap-key", true)) return;
        Player p = e.getPlayer();
        e.setCancelled(true);
        var qd = plugin.data().get(p);
        int qm = kr.rpgcraft.feature.MenuManager.quickMode(qd);
        if (qd.quickSkill != null && ((qm == 300 && !p.isSneaking()) || (qm == 301 && p.isSneaking()))) { plugin.passives().useQuick(p); return; }
        if (p.isSneaking()) plugin.menu().open(p);
        else useFromBag(p);   // 가방에서 골라 둔 포션을 바로 사용
    }

    /** F: 가방 속 포션 중 마실 것을 고르는 창 */
    public void openQuick(Player p) {
        var d = plugin.data().get(p);
        kr.rpgcraft.gui.Gui g = new kr.rpgcraft.gui.Gui(1, "&8포션 선택") {
        };
        int slot = 0;
        for (String id : POTIONS) {
            int n = d.potionBag.getOrDefault(id, 0);
            if (n <= 0 || slot > 8) continue;
            ItemStack icon = plugin.items().create(id, Math.min(64, n));
            if (icon == null) continue;
            g.set(slot++, icon, e -> {
                p.closeInventory();
                d.selectedPotion = id;
                useFromBag(p);
            });
        }
        if (slot == 0) { Text.actionBar(p, "&c포션 가방이 비었습니다"); return; }
        g.fill(0, 8);
        g.open(p);
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent e) {
        ItemStack it = e.getItem();
        if (ItemData.category(it) != Category.POTION) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (drink(p, ItemData.id(it))) {
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (ItemData.is(hand, ItemData.id(it))) hand.setAmount(hand.getAmount() - 1);
            else {
                ItemStack off = p.getInventory().getItemInOffHand();
                if (ItemData.is(off, ItemData.id(it))) off.setAmount(off.getAmount() - 1);
            }
        }
    }

    public void open(Player p) {
        new BagGui(p).open(p);
    }

    private class BagGui extends Gui {
        private final Player owner;

        BagGui(Player p) {
            super(3, "&c포션가방", "potion");
            owner = p;
            render();
        }

        void render() {
            PlayerData d = plugin.data().get(owner);
            clearButtons();
            for (int i = 0; i < POTIONS.size(); i++) {
                String id = POTIONS.get(i);
                int n = d.potionBag.getOrDefault(id, 0);
                ItemStack icon = plugin.items().create(id, Math.max(1, Math.min(64, n)));
                var m = icon.getItemMeta();
                List<String> lore = m.getLore();
                lore.add("");
                lore.add(Text.c("&f보관: &e" + n + "개" + (id.equals(d.selectedPotion) ? " &a[선택됨]" : "")));
                lore.add(Text.c("&7좌클릭: F키 사용 포션으로 선택"));
                lore.add(Text.c("&7우클릭: 1개 꺼내기 / 쉬프트+우클릭: 9개 꺼내기"));
                m.setLore(lore);
                if (id.equals(d.selectedPotion)) {   // 선택된 포션은 반짝이게
                    m.addEnchant(org.bukkit.enchantments.Enchantment.DURABILITY, 1, true);
                    m.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
                }
                icon.setItemMeta(m);
                set(10 + i * 2, icon, e -> {
                    if (e.isLeftClick()) {
                        d.selectedPotion = id;
                        Text.actionBar(owner, "&aF키 포션: &f" + plugin.items().get(id).name);
                        owner.playSound(owner.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
                    } else {
                        int take = Math.min(e.isShiftClick() ? 9 : 1, d.potionBag.getOrDefault(id, 0));
                        if (take > 0) {
                            d.potionBag.merge(id, -take, Integer::sum);
                            for (ItemStack left : owner.getInventory().addItem(plugin.items().create(id, take)).values())
                                owner.getWorld().dropItemNaturally(owner.getLocation(), left);
                        }
                    }
                    render();
                });
            }
            set(22, button(Material.HOPPER, "&a인벤토리의 포션 모두 넣기", "&7인벤토리 아이템을 쉬프트 클릭해도 넣을 수 있습니다.",
                    "&7F: 포션 사용 / 쉬프트+F: 메인 메뉴 / 쉬프트+Q: 퀵 스킬"), e -> {
                for (ItemStack it : owner.getInventory().getStorageContents()) deposit(it);
                render();
            });
            fill(0, 26);
        }

        void deposit(ItemStack it) {
            if (ItemData.category(it) != Category.POTION) return;
            plugin.data().get(owner).potionBag.merge(ItemData.id(it), it.getAmount(), Integer::sum);
            it.setAmount(0);
        }

        @Override
        protected void onBottomClick(InventoryClickEvent e) {
            if (!e.isShiftClick()) return;
            e.setCancelled(true);
            deposit(e.getCurrentItem());
            render();
        }
    }
}
