package kr.rpgcraft.feature;

import kr.rpgcraft.Keys;
import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.data.PlayerData;
import kr.rpgcraft.gui.Gui;
import kr.rpgcraft.item.Category;
import kr.rpgcraft.item.ItemData;
import kr.rpgcraft.stat.Stat;
import kr.rpgcraft.stat.StatMap;
import kr.rpgcraft.util.Text;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 룬: 하급/중급/상급. 랜덤 옵션 3줄 (일부 확률로 마이너스 옵션).
 * 룬 변경권(10,000원)으로 옵션 재설정, /룬 에서 3칸 장착.
 */
public class RuneManager {
    private static final Object[][] OPTIONS = {
            {Stat.ATK, 5.0, 30.0}, {Stat.HP, 50.0, 300.0}, {Stat.CRIT, 0.5, 2.0}, {Stat.CRIT_DMG, 2.0, 8.0},
            {Stat.DEF, 0.3, 1.5}, {Stat.EXP_PCT, 1.0, 3.0}, {Stat.STR, 1.0, 5.0}, {Stat.DEX, 1.0, 5.0},
            {Stat.ADV, 1.0, 5.0}, {Stat.LIFESTEAL, 0.1, 0.5}, {Stat.ARMOR_PEN, 0.5, 2.0}, {Stat.DODGE, 0.3, 1.0},
            {Stat.HP_PCT, 0.5, 2.0}, {Stat.SPEED, 0.5, 2.0}, {Stat.ENHANCE_RATE, 0.1, 0.5}
    };
    private static final double[] TIER_MULT = {1, 1, 3, 8};

    private final RpgCraft plugin;

    public RuneManager(RpgCraft plugin) {
        this.plugin = plugin;
    }

    public static boolean isRune(ItemStack it) {
        return ItemData.category(it) == Category.RUNE && !AccessoryManager.isAccessory(it);
    }

    public static void roll(ItemStack rune, int tier) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        tier = Math.max(1, Math.min(3, tier));
        List<Integer> idx = new ArrayList<>();
        for (int i = 0; i < OPTIONS.length; i++) idx.add(i);
        Collections.shuffle(idx, r);
        StatMap m = new StatMap();
        double negChance = tier == 1 ? 0.2 : tier == 2 ? 0.12 : 0.06;
        for (int i = 0; i < 3; i++) {
            Object[] o = OPTIONS[idx.get(i)];
            Stat s = (Stat) o[0];
            double min = (double) o[1], max = (double) o[2];
            double v = (min + r.nextDouble() * (max - min)) * TIER_MULT[tier];
            if (r.nextDouble() < negChance) v = -v * 0.5;
            v = s.pct ? Math.round(v * 10) / 10.0 : Math.round(v);
            if (v == 0) v = s.pct ? 0.1 : 1;
            m.add(s, v);
        }
        ItemData.setString(rune, Keys.RUNE, m.serialize());
        ItemData.refresh(rune);
    }

    /** 손에 든 룬을 룬 변경권으로 재설정 */
    public void reroll(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (ItemData.category(hand) != Category.RUNE) {
            Text.msg(p, "&c재설정할 룬을 손에 들어주세요.");
            return;
        }
        if (!consume(p, "ticket_rune")) {
            Text.msg(p, "&c룬 변경권이 필요합니다. (상점 10,000원)");
            return;
        }
        roll(hand, ItemData.template(hand).tier);
        p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.2f);
        Text.msg(p, "&d룬 옵션이 재설정되었습니다!");
        for (String line : hand.getItemMeta().getLore()) p.sendMessage(line);
    }

    private boolean consume(Player p, String id) {
        for (ItemStack it : p.getInventory().getContents()) {
            if (ItemData.is(it, id)) {
                it.setAmount(it.getAmount() - 1);
                return true;
            }
        }
        return false;
    }

    public void open(Player p) {
        new RuneGui(p).open(p);
    }

    /** 룬 장착 GUI (11, 13, 15 칸) */
    private class RuneGui extends Gui {
        private static final int[] SLOTS = {11, 13, 15};
        private final Player owner;

        RuneGui(Player p) {
            super(3, "&5룬 장착", "rune");
            owner = p;
            PlayerData d = plugin.data().get(p);
            for (int i = 0; i < 3; i++) inv.setItem(SLOTS[i], d.runes[i] == null ? null : d.runes[i].clone());
            set(22, button(Material.BOOK, "&e룬 안내", "&7빈 칸에 룬을 넣으면 장착됩니다.", "&7창을 닫으면 저장됩니다.",
                    "&7룬 변경권: 룬을 손에 들고 &e/룬 변경"));
            set(26, button(Material.ANVIL, "&6룬 합성 →", "&73개 → 새 룬 / 6개 → 상위 룬"), e -> plugin.runeFusion().open(owner));
            fill(0, 26);
        }

        @Override
        public boolean editable(int raw) {
            return raw == 11 || raw == 13 || raw == 15;
        }

        @Override
        public boolean allowShiftIn() {
            return true;
        }

        @Override
        protected void onEditableClick(InventoryClickEvent e) {
            ItemStack cursor = e.getCursor();
            if (cursor != null && !cursor.getType().isAir() && !isRune(cursor)) e.setCancelled(true);
            if (cursor != null && cursor.getAmount() > 1 && isRune(cursor)) e.setCancelled(true);
        }

        @Override
        protected void onBottomClick(InventoryClickEvent e) {
            if (!e.isShiftClick()) return;
            e.setCancelled(true);
            ItemStack cur = e.getCurrentItem();
            if (!isRune(cur)) return;
            for (int s : SLOTS) {
                if (inv.getItem(s) == null) {
                    inv.setItem(s, cur.clone());
                    e.setCurrentItem(null);
                    return;
                }
            }
        }

        @Override
        public void onClose(InventoryCloseEvent e) {
            PlayerData d = plugin.data().get(owner);
            for (int i = 0; i < 3; i++) {
                ItemStack it = inv.getItem(SLOTS[i]);
                if (it != null && !isRune(it)) {
                    giveBack(owner, SLOTS[i]);
                    it = null;
                }
                d.runes[i] = it == null ? null : it.clone();
            }
            plugin.stats().refresh(owner);
            Text.msg(owner, "&d룬 장착 정보가 저장되었습니다.");
        }
    }
}
