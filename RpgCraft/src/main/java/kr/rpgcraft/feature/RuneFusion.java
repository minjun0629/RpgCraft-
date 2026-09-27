package kr.rpgcraft.feature;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.gui.Gui;
import kr.rpgcraft.item.ItemData;
import kr.rpgcraft.util.Text;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 룬 합성 (/룬합성, 룬 창의 버튼)
 *  · 재구성: 같은 등급 룬 3개 → 같은 등급 새 룬 1개 (옵션 무작위)
 *  · 승급  : 같은 등급 룬 6개 → 한 단계 위 룬 1개 (하급 → 중급 → 상급)
 */
public class RuneFusion {
    private static final String[] IDS = {"rune_low", "rune_mid", "rune_high"};
    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
    private final RpgCraft plugin;

    public RuneFusion(RpgCraft plugin) {
        this.plugin = plugin;
    }

    public void open(Player p) {
        new FuseGui(p).open(p);
    }

    private class FuseGui extends Gui {
        private final Player owner;

        FuseGui(Player p) {
            super(4, "&8룬 합성");
            owner = p;
            render();
        }

        private List<ItemStack> runes() {
            List<ItemStack> out = new ArrayList<>();
            for (int s : SLOTS) {
                ItemStack it = inv.getItem(s);
                if (it != null && !it.getType().isAir()) out.add(it);
            }
            return out;
        }

        /** 넣은 룬이 모두 같은 등급이면 그 등급 번호, 아니면 -1 */
        private int tierOf(List<ItemStack> rs) {
            int tier = -1;
            for (ItemStack it : rs) {
                String id = ItemData.id(it);
                int t = -1;
                for (int i = 0; i < 3; i++) if (IDS[i].equals(id)) t = i;
                if (t < 0 || (tier >= 0 && t != tier)) return -1;
                tier = t;
            }
            return tier;
        }

        void render() {
            List<ItemStack> rs = runes();
            int n = 0;
            for (ItemStack it : rs) n += it.getAmount();
            int tier = tierOf(rs);
            String tn = tier < 0 ? "-" : new String[]{"하급", "중급", "상급"}[tier];
            set(4, button(Material.BOOK, "&e룬 합성", "&f넣은 룬: " + n + "개 (" + tn + ")", "&7같은 등급의 룬만 함께 넣을 수 있습니다"), null);
            set(30, button(n >= 3 && tier >= 0 ? Material.LIME_DYE : Material.GRAY_DYE, "&a재구성 &7(3개 → 1개)", "&f같은 등급 새 룬 (옵션 무작위)"),
                    e -> fuse(3, false));
            set(32, button(n >= 6 && tier >= 0 && tier < 2 ? Material.NETHER_STAR : Material.GRAY_DYE, "&6승급 &7(6개 → 1개)", "&f한 단계 위 등급의 룬"),
                    e -> fuse(6, true));
            fill(0, 35);
        }

        private void fuse(int need, boolean up) {
            List<ItemStack> rs = runes();
            int n = 0;
            for (ItemStack it : rs) n += it.getAmount();
            int tier = tierOf(rs);
            if (tier < 0) { Text.actionBar(owner, "&c같은 등급의 룬만 넣으세요"); return; }
            if (n < need) { Text.actionBar(owner, "&c룬이 " + need + "개 필요합니다"); return; }
            if (up && tier >= 2) { Text.actionBar(owner, "&c상급 룬은 더 올릴 수 없습니다"); return; }
            int left = need;
            for (int s : SLOTS) {
                ItemStack it = inv.getItem(s);
                if (it == null || left <= 0) continue;
                int t = Math.min(left, it.getAmount());
                it.setAmount(it.getAmount() - t);
                if (it.getAmount() <= 0) inv.setItem(s, null);
                left -= t;
            }
            ItemStack out = plugin.items().create(IDS[up ? tier + 1 : tier], 1);
            for (ItemStack l : owner.getInventory().addItem(out).values()) owner.getWorld().dropItemNaturally(owner.getLocation(), l);
            owner.playSound(owner.getLocation(), up ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.2f);
            Text.actionBar(owner, "&a" + out.getItemMeta().getDisplayName() + " &f획득");
            render();
        }

        @Override
        public boolean editable(int raw) {
            for (int s : SLOTS) if (s == raw) return true;
            return false;
        }

        @Override
        protected void onEditableClick(InventoryClickEvent e) {
            ItemStack c = e.getCursor();
            if (c != null && !c.getType().isAir() && !RuneManager.isRune(c)) e.setCancelled(true);
            org.bukkit.Bukkit.getScheduler().runTask(plugin, this::render);
        }

        @Override
        protected void onBottomClick(InventoryClickEvent e) {
            if (!e.isShiftClick()) return;
            e.setCancelled(true);
            ItemStack cur = e.getCurrentItem();
            if (!RuneManager.isRune(cur)) return;
            for (int s : SLOTS) if (inv.getItem(s) == null) { inv.setItem(s, cur.clone()); e.setCurrentItem(null); break; }
            render();
        }

        @Override
        public void onClose(InventoryCloseEvent e) {
            for (int s : SLOTS) giveBack(owner, s);
        }
    }
}
