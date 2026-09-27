package kr.rpgcraft.item;

import kr.rpgcraft.Keys;
import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.stat.Stat;
import kr.rpgcraft.stat.StatMap;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** ItemStack 의 PDC 를 읽고 쓰는 헬퍼. 장비 스탯은 아이템 자체에 저장된다(대장장이 랜덤 스탯 지원). */
public final class ItemData {
    private ItemData() {}

    private static PersistentDataContainer pdc(ItemStack it) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer();
    }

    public static String id(ItemStack it) {
        PersistentDataContainer c = pdc(it);
        return c == null ? null : c.get(Keys.ID, PersistentDataType.STRING);
    }

    public static boolean is(ItemStack it, String id) {
        return id.equals(id(it));
    }

    public static ItemTemplate template(ItemStack it) {
        String id = id(it);
        return id == null ? null : RpgCraft.get().items().get(id);
    }

    public static Category category(ItemStack it) {
        ItemTemplate t = template(it);
        return t == null ? null : t.category;
    }

    public static String getString(ItemStack it, org.bukkit.NamespacedKey k) {
        PersistentDataContainer c = pdc(it);
        return c == null ? null : c.get(k, PersistentDataType.STRING);
    }

    public static int getInt(ItemStack it, org.bukkit.NamespacedKey k, int def) {
        PersistentDataContainer c = pdc(it);
        if (c == null) return def;
        Integer v = c.get(k, PersistentDataType.INTEGER);
        return v == null ? def : v;
    }

    public static double getDouble(ItemStack it, org.bukkit.NamespacedKey k, double def) {
        PersistentDataContainer c = pdc(it);
        if (c == null) return def;
        Double v = c.get(k, PersistentDataType.DOUBLE);
        return v == null ? def : v;
    }

    public static void setString(ItemStack it, org.bukkit.NamespacedKey k, String v) {
        ItemMeta m = it.getItemMeta();
        if (v == null) m.getPersistentDataContainer().remove(k);
        else m.getPersistentDataContainer().set(k, PersistentDataType.STRING, v);
        it.setItemMeta(m);
    }

    public static void setInt(ItemStack it, org.bukkit.NamespacedKey k, int v) {
        ItemMeta m = it.getItemMeta();
        m.getPersistentDataContainer().set(k, PersistentDataType.INTEGER, v);
        it.setItemMeta(m);
    }

    public static void setDouble(ItemStack it, org.bukkit.NamespacedKey k, double v) {
        ItemMeta m = it.getItemMeta();
        m.getPersistentDataContainer().set(k, PersistentDataType.DOUBLE, v);
        it.setItemMeta(m);
    }

    public static int enh(ItemStack it) {
        return getInt(it, Keys.ENH, 0);
    }

    public static Grade grade(ItemStack it) {
        String g = getString(it, Keys.GRADE);
        if (g == null) {
            ItemTemplate t = template(it);
            return t == null ? Grade.NORMAL : t.grade;
        }
        return Grade.valueOf(g);
    }

    public static WeaponClass weaponClass(ItemStack it) {
        String s = getString(it, Keys.WCLASS);
        return s == null ? null : WeaponClass.valueOf(s);
    }

    public static ArmorSlot armorSlot(ItemStack it) {
        String s = getString(it, Keys.ASLOT);
        return s == null ? null : ArmorSlot.valueOf(s);
    }

    public static final org.bukkit.NamespacedKey LIMIT = new org.bukkit.NamespacedKey("rpgcraft", "limit"), LIMIT_STATS = new org.bukkit.NamespacedKey("rpgcraft", "limit_stats");

    public static int limit(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return 0;
        Integer v = it.getItemMeta().getPersistentDataContainer().get(LIMIT, org.bukkit.persistence.PersistentDataType.INTEGER);
        return v == null ? 0 : v;
    }

    public static StatMap limitStats(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return new StatMap();
        return StatMap.parse(it.getItemMeta().getPersistentDataContainer().get(LIMIT_STATS, org.bukkit.persistence.PersistentDataType.STRING));
    }

    public static StatMap baseStats(ItemStack it) {
        StatMap m = StatMap.parse(getString(it, Keys.STATS));
        int n = limit(it);
        if (n > 0) {   // 한계 돌파: 스탯 조건이 단계마다 +10%
            for (kr.rpgcraft.stat.Stat r : new kr.rpgcraft.stat.Stat[]{kr.rpgcraft.stat.Stat.REQ_STR, kr.rpgcraft.stat.Stat.REQ_DEX, kr.rpgcraft.stat.Stat.REQ_ADV})
                if (m.get(r) > 0) m.add(r, Math.ceil(m.get(r) * 0.1 * n));
        }
        return m;
    }

    public static StatMap setBonus(ItemStack it) {
        return StatMap.parse(getString(it, Keys.SET_BONUS));
    }

    public static double value(ItemStack it) {
        PersistentDataContainer c = pdc(it);
        if (c != null && c.has(Keys.VALUE, PersistentDataType.DOUBLE)) return c.get(Keys.VALUE, PersistentDataType.DOUBLE);
        ItemTemplate t = template(it);
        return t == null ? 0 : t.value;
    }

    public static boolean enhanceable(ItemStack it) {
        ItemTemplate t = template(it);
        return t != null && t.category.isEquipment() && t.enhanceable;
    }

    public static int maxEnhance(ItemStack it) {
        var ec = RpgCraft.get().getConfig();
        return grade(it).atLeast(Grade.LEGEND) ? ec.getInt("enhance.max-legend", 15) : ec.getInt("enhance.max-normal", 15);
    }

    /** 강화 수치가 반영된 최종 스탯 */
    public static StatMap effectiveStats(ItemStack it) {
        StatMap out = effectiveStatsNoPotential(it).copy();
        out.addAll(kr.rpgcraft.feature.PotentialManager.stats(it));   // 잠재능력
        out.addAll(limitStats(it));   // 한계 돌파
        return out;
    }

    private static StatMap effectiveStatsNoPotential(ItemStack it) {
        StatMap base = baseStats(it);
        int e = enh(it);
        if (e <= 0) return base;
        var cfg = RpgCraft.get().getConfig();
        StatMap out = base.copy();
        WeaponClass wc = weaponClass(it);
        if (wc != null || base.get(Stat.RANGED_ATK) > 0) {
            double per = wc == WeaponClass.SHIELD ? cfg.getDouble("enhance.shield-atk-per-level", 0.04)
                    : cfg.getDouble("enhance.weapon-atk-per-level", 0.08);
            out.set(Stat.ATK, base.get(Stat.ATK) * (1 + per * e) + 5 * e);
            if (base.get(Stat.RANGED_ATK) > 0) out.set(Stat.RANGED_ATK, base.get(Stat.RANGED_ATK) * (1 + per * e));
            if (wc == WeaponClass.SHIELD) out.add(Stat.DEF, 0.2 * e);
        }
        if (armorSlot(it) != null) {
            out.add(Stat.DEF, cfg.getDouble("enhance.armor-def-per-level", 0.3) * e);
            out.add(Stat.HP, cfg.getDouble("enhance.armor-hp-per-level", 50) * e);
        }
        return out;
    }

    /** 이름·로어·모델을 PDC 기준으로 다시 그린다 (실제 구성은 ItemLore) */
    public static void refresh(ItemStack it) {
        ItemLore.apply(it);
    }

    private static String color(String s) {
        return kr.rpgcraft.util.Text.c(s);
    }
}
