package kr.rpgcraft.feature;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.gui.Gui;
import kr.rpgcraft.item.Category;
import kr.rpgcraft.item.ItemData;
import kr.rpgcraft.item.ItemTemplate;
import kr.rpgcraft.stat.Stat;
import kr.rpgcraft.stat.StatMap;
import kr.rpgcraft.util.Text;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 한계 돌파 (/한계돌파, 강화 창 버튼): 장비의 스탯 조건이 단계마다 +10% 오르는 대신
 * 능력치가 최소~최대 사이에서 무작위로 오른다. 최대 5단계.
 *  무기: 공격력 +3~9% (기본 공격력 기준), 치명타 피해 +0~6%
 *  방어구: 방어력 +1~3, 체력 +3~8% (기본 체력 기준)
 * 비용: 상급 결정 (단계+1)×2 개 + (단계+1)×1,000,000원
 */
public class LimitBreakManager {
    private final RpgCraft plugin;

    public LimitBreakManager(RpgCraft plugin) {
        this.plugin = plugin;
    }

    public static List<String> lore(ItemStack it) {
        List<String> out = new ArrayList<>();
        int n = ItemData.limit(it);
        if (n <= 0) return out;
        out.add("&d✦ 한계 돌파 " + "◆".repeat(n) + "&8" + "◇".repeat(Math.max(0, 5 - n)));
        return out;
    }

    private boolean eligible(ItemStack it) {
        ItemTemplate t = ItemData.template(it);
        return t != null && (t.category == Category.WEAPON || t.category == Category.BOW || t.category == Category.ARMOR);
    }

    private int crystals(Player p) {
        int n = 0;
        for (ItemStack x : p.getInventory().getStorageContents()) if ("crystal_high".equals(ItemData.id(x))) n += x.getAmount();
        return n;
    }

    private String doBreak(Player p, ItemStack eq) {
        if (!eligible(eq)) return "한계 돌파를 할 수 없는 아이템입니다.";
        int n = ItemData.limit(eq);
        if (n >= 5) return "이미 한계를 모두 돌파했습니다.";
        int needC = (n + 1) * 2;
        long cost = (n + 1) * 1_000_000L;
        if (crystals(p) < needC) return "상급 결정 " + needC + "개가 필요합니다.";
        if (!plugin.economy().take(p, cost)) return Text.money(cost) + "이 필요합니다.";
        int left = needC;
        for (ItemStack x : p.getInventory().getStorageContents()) {
            if (left <= 0) break;
            if (!"crystal_high".equals(ItemData.id(x))) continue;
            int t = Math.min(left, x.getAmount());
            x.setAmount(x.getAmount() - t);
            left -= t;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        StatMap base = StatMap.parse(ItemData.getString(eq, kr.rpgcraft.Keys.STATS));
        StatMap add = ItemData.limitStats(eq);
        List<String> got = new ArrayList<>();
        if (ItemData.template(eq).category == Category.ARMOR) {
            double def = 1 + r.nextInt(3);
            double hp = Math.round(Math.max(100, base.get(Stat.HP)) * (0.03 + r.nextDouble() * 0.05));
            add.add(Stat.DEF, def).add(Stat.HP, hp);
            got.add("방어력 +" + (int) def);
            got.add("체력 +" + (int) hp);
        } else {
            double atk = Math.round(Math.max(10, base.get(Stat.ATK)) * (0.03 + r.nextDouble() * 0.06));
            double cd = r.nextInt(7);
            add.add(Stat.ATK, atk);
            if (cd > 0) add.add(Stat.CRIT_DMG, cd);
            got.add("공격력 +" + (int) atk);
            if (cd > 0) got.add("치명타 피해 +" + (int) cd + "%");
        }
        ItemMeta m = eq.getItemMeta();
        m.getPersistentDataContainer().set(ItemData.LIMIT, PersistentDataType.INTEGER, n + 1);
        m.getPersistentDataContainer().set(ItemData.LIMIT_STATS, PersistentDataType.STRING, add.serialize());
        eq.setItemMeta(m);
        ItemData.refresh(eq);
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 0.8f);
        p.sendTitle(Text.c("&d&l한계 돌파 " + (n + 1) + "단계"), Text.c("&f" + String.join(" · ", got)), 5, 50, 10);
        return null;
    }

    public void open(Player p) {
        new LbGui(p).open(p);
    }

    private class LbGui extends Gui {
        private final Player owner;

        LbGui(Player p) {
            super(3, "&8한계 돌파");
            owner = p;
            render();
        }

        void render() {
            ItemStack eq = inv.getItem(11);
            set(2, button(Material.ARMOR_STAND, "&f▼ 장비"), null);
            int n = eq == null ? 0 : ItemData.limit(eq);
            List<String> lore = new ArrayList<>();
            if (eq == null) lore.add("&7장비를 넣어 주세요");
            else if (!eligible(eq)) lore.add("&c돌파할 수 없는 아이템");
            else {
                lore.add("&f현재 " + n + " / 5 단계");
                if (n < 5) {
                    lore.add("&7비용: 상급 결정 " + (n + 1) * 2 + "개 · " + Text.money((n + 1) * 1_000_000L));
                    lore.add("&7스탯 조건 +10% · 능력치 무작위 상승");
                    lore.add("&e▶ 클릭");
                }
            }
            set(15, button(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE, "&d&l한계 돌파", lore.toArray(new String[0])), e -> {
                ItemStack cur = inv.getItem(11);
                if (cur == null) { Text.actionBar(owner, "&c장비를 먼저 넣으세요"); return; }
                String err = doBreak(owner, cur);
                if (err != null) Text.actionBar(owner, "&c" + err);
                render();
            });
            fill(0, 26);
        }

        @Override
        public boolean editable(int raw) {
            return raw == 11;
        }

        @Override
        protected void onEditableClick(InventoryClickEvent e) {
            org.bukkit.Bukkit.getScheduler().runTask(plugin, this::render);
        }

        @Override
        protected void onBottomClick(InventoryClickEvent e) {
            if (!e.isShiftClick()) return;
            e.setCancelled(true);
            ItemStack cur = e.getCurrentItem();
            if (!eligible(cur) || inv.getItem(11) != null) return;
            inv.setItem(11, cur.clone());
            e.setCurrentItem(null);
            render();
        }

        @Override
        public void onClose(InventoryCloseEvent e) {
            giveBack(owner, 11);
        }
    }
}
