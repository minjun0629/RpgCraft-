package kr.rpgcraft.feature;

import kr.rpgcraft.Keys;
import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.data.PlayerData;
import kr.rpgcraft.gui.Gui;
import kr.rpgcraft.item.ItemData;
import kr.rpgcraft.stat.Stat;
import kr.rpgcraft.stat.StatMap;
import kr.rpgcraft.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 장신구 — 룬과 같은 무작위 옵션 성장 요소 (반지 · 목걸이 · 귀걸이 3칸)
 *  · 하급/중급/상급, 옵션 3~4줄 (상급은 4줄), 부위마다 잘 나오는 옵션이 다름
 *    반지: 공격력·치명타·방어 관통 / 목걸이: 체력%·방어력·흡혈 / 귀걸이: 마력·치명타 피해·이동속도
 *  · 룬 변경권으로 옵션 재설정 (/장신구 변경)
 */
public class AccessoryManager {
    public static final String[] KIND = {"ring", "neck", "ear"};
    public static final String[] KIND_KO = {"반지", "목걸이", "귀걸이"};
    private static final Object[][][] POOL = {
            {{Stat.ATK, 8.0, 40.0}, {Stat.CRIT, 0.8, 3.0}, {Stat.ARMOR_PEN, 1.0, 3.0}, {Stat.STR, 2.0, 8.0}, {Stat.CRIT_DMG, 3.0, 10.0}},
            {{Stat.HP_PCT, 1.0, 3.0}, {Stat.DEF, 0.5, 2.0}, {Stat.LIFESTEAL, 0.2, 0.8}, {Stat.ADV, 2.0, 8.0}, {Stat.HP, 80.0, 400.0}},
            {{Stat.MAGIC, 15.0, 80.0}, {Stat.CRIT_DMG, 3.0, 12.0}, {Stat.SPEED, 0.8, 3.0}, {Stat.DEX, 2.0, 8.0}, {Stat.EXP_PCT, 1.0, 4.0}}};
    private static final double[] TIER_MULT = {1, 1, 3, 8};

    private final RpgCraft plugin;

    public AccessoryManager(RpgCraft plugin) {
        this.plugin = plugin;
    }

    public static boolean isAccessory(ItemStack it) {
        String id = ItemData.id(it);
        return id != null && id.startsWith("acc_");
    }

    public static int kindOf(ItemStack it) {
        String id = ItemData.id(it);
        if (id == null) return -1;
        for (int i = 0; i < 3; i++) if (id.startsWith("acc_" + KIND[i])) return i;
        return -1;
    }

    public static void roll(ItemStack it, int tier) {
        int k = kindOf(it);
        if (k < 0) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        tier = Math.max(1, Math.min(3, tier));
        List<Object[]> opts = new ArrayList<>(Arrays.asList(POOL[k]));
        Collections.shuffle(opts, r);
        StatMap m = new StatMap();
        int lines = tier == 3 ? 4 : 3;
        for (int i = 0; i < lines && i < opts.size(); i++) {
            Object[] o = opts.get(i);
            Stat s = (Stat) o[0];
            double v = ((double) o[1] + r.nextDouble() * ((double) o[2] - (double) o[1])) * TIER_MULT[tier];
            if (r.nextDouble() < (tier == 1 ? 0.15 : 0.06)) v = -v * 0.4;
            v = s.pct ? Math.round(v * 10) / 10.0 : Math.round(v);
            if (v == 0) v = s.pct ? 0.1 : 1;
            m.add(s, v);
        }
        ItemData.setString(it, Keys.RUNE, m.serialize());
        ItemData.refresh(it);
    }

    public StatMap bonus(PlayerData d) {
        StatMap m = new StatMap();
        for (ItemStack it : d.accessories) if (it != null && isAccessory(it)) m.addAll(StatMap.parse(ItemData.getString(it, Keys.RUNE)));
        return m;
    }

    public void reroll(Player p) {
        ItemStack it = p.getInventory().getItemInMainHand();
        if (!isAccessory(it)) { Text.msg(p, "&c장신구를 손에 들고 사용하세요."); return; }
        for (ItemStack t : p.getInventory().getContents()) {
            if ("ticket_rune".equals(ItemData.id(t))) {
                t.setAmount(t.getAmount() - 1);
                roll(it, ItemData.template(it).tier);
                Text.msg(p, "&a장신구 옵션이 새로 정해졌습니다.");
                return;
            }
        }
        Text.msg(p, "&c룬 변경권이 필요합니다.");
    }

    public void open(Player p) {
        new AccGui(p).open(p);
    }

    private class AccGui extends Gui {
        private final int[] SLOTS = {11, 13, 15};
        private final Player owner;

        AccGui(Player p) {
            super(3, "&6장신구", "rune");
            owner = p;
            PlayerData d = plugin.data().get(p);
            for (int i = 0; i < 3; i++) inv.setItem(SLOTS[i], d.accessories[i] == null ? null : d.accessories[i].clone());
            for (int i = 0; i < 3; i++) set(SLOTS[i] - 9, button(Material.GRAY_STAINED_GLASS_PANE, "&7▼ " + KIND_KO[i] + " 자리"));
            set(22, button(Material.BOOK, "&e장신구 안내", "&7반지 · 목걸이 · 귀걸이를 각 자리에 넣으면 장착됩니다.",
                    "&7정예·중간 보스·던전·보물에서 얻거나 상점(/상점 accessory)에서 삽니다.", "&7옵션 재설정: 장신구를 들고 &e/장신구 변경 &7(룬 변경권)"));
            fill(0, 26);
        }

        @Override
        public boolean editable(int raw) {
            return raw == 11 || raw == 13 || raw == 15;
        }

        @Override
        protected void onEditableClick(InventoryClickEvent e) {
            ItemStack cursor = e.getCursor();
            if (cursor == null || cursor.getType().isAir()) return;
            int want = e.getRawSlot() == 11 ? 0 : e.getRawSlot() == 13 ? 1 : 2;
            if (kindOf(cursor) != want || cursor.getAmount() > 1) e.setCancelled(true);
        }

        @Override
        protected void onBottomClick(InventoryClickEvent e) {
            if (!e.isShiftClick()) return;
            e.setCancelled(true);
            ItemStack cur = e.getCurrentItem();
            int k = kindOf(cur);
            if (k < 0 || inv.getItem(SLOTS[k]) != null) return;
            inv.setItem(SLOTS[k], cur.clone());
            e.setCurrentItem(null);
        }

        @Override
        public void onClose(InventoryCloseEvent e) {
            PlayerData d = plugin.data().get(owner);
            for (int i = 0; i < 3; i++) {
                ItemStack it = inv.getItem(SLOTS[i]);
                if (it != null && kindOf(it) != i) {
                    giveBack(owner, SLOTS[i]);
                    it = null;
                }
                d.accessories[i] = it == null ? null : it.clone();
            }
            plugin.stats().refresh(owner);
            Text.msg(owner, "&6장신구 장착 정보가 저장되었습니다.");
        }
    }
}
