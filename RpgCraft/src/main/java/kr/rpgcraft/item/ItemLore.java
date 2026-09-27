package kr.rpgcraft.item;

import kr.rpgcraft.Keys;
import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.feature.ItemEffectManager;
import kr.rpgcraft.stat.Power;
import kr.rpgcraft.stat.Stat;
import kr.rpgcraft.stat.StatMap;
import kr.rpgcraft.util.Text;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 아이템 이름·로어·모델 구성.
 * 정보 계층: ① 등급/종류 ② 강화 별 ③ 칭호 + 세계관 ④ 능력치 ⑤ 세트/특수 효과 ⑥ 요구치·강화·전투력
 * 강화 +7 / +10 에 도달한 무기는 전용 모델(CustomModelData +3000 / +5000)로 바뀐다.
 */
public final class ItemLore {
    public static final int STAGE_ENGRAVED = 7, STAGE_PERFECT = 10;
    public static final int OFFSET_ENGRAVED = 3000, OFFSET_PERFECT = 5000;
    private static final String SEP = "&8&m                              ";

    private ItemLore() {}

    /** 설명에 적히는 체력 = 실제로 화면 체력에 더해지는 값 (예전: 내부 수치라 800 이라 적혀도 실제로는 훨씬 적게 올랐음) */
    private static double shown(Stat st, double v) {
        RpgCraft pl = RpgCraft.get();
        if (pl == null) return v;
        if (st == Stat.HP) return Math.round(v * pl.getConfig().getDouble("player.item-hp-mult", 0.4) * pl.stats().hpScale() * 10) / 10.0;
        if (st == Stat.HP_PCT) return Math.round(v * pl.getConfig().getDouble("player.item-hp-mult", 0.4) * 10) / 10.0;
        if (st == Stat.SPEED) return Math.round(v * pl.getConfig().getDouble("player.speed-mult", 0.7) * 10) / 10.0;
        return v;
    }

    public static void apply(ItemStack it) {
        ItemTemplate t = ItemData.template(it);
        if (t == null) return;
        resyncTranscend(it, t);
        ItemMeta m = it.getItemMeta();
        Grade g = ItemData.grade(it);
        String custom = m.getPersistentDataContainer().get(Keys.NAME, PersistentDataType.STRING);
        String name = custom != null ? custom : t.name;
        int e = ItemData.enh(it);
        List<String> lore = new ArrayList<>();

        if (t.category.isEquipment()) {
            String enhTag = e <= 0 ? "" : e >= STAGE_PERFECT ? " &6&l『+" + e + "』" : e >= STAGE_ENGRAVED ? " &e&l+" + e : " &e+" + e;
            m.setDisplayName(Text.c(g.nameColor() + name + enhTag));
            WeaponClass wc = ItemData.weaponClass(it);
            ArmorSlot as = ItemData.armorSlot(it);
            String type = wc != null ? wc.label : as != null ? as.label : t.category.label;
            StatMap eff = ItemData.effectiveStats(it);

            lore.add(g.tag() + " &8· &7" + type + (ItemData.getString(it, Keys.SET) != null ? " &8· &b세트" : ""));
            if (t.enhanceable) {
                int max = ItemData.maxEnhance(it);
                String star = e >= STAGE_PERFECT ? "&6" : "&e";
                int row = 15;   // 15개씩 두 줄
                for (int r0 = 0; r0 < max; r0 += row) {
                    int filled = Math.max(0, Math.min(row, e - r0)), cnt = Math.min(row, max - r0);
                    lore.add(star + "★".repeat(filled) + "&8" + "☆".repeat(Math.max(0, cnt - filled))
                            + (r0 + row >= max ? (e >= STAGE_PERFECT ? " &6완성" : e >= STAGE_ENGRAVED ? " &e각인" : "") : ""));
                }
            }
            flavor(t, lore);
            lore.add(SEP);

            lore.add("&f▸ 능력치");
            for (Map.Entry<Stat, Double> en : eff.entries()) {
                if (en.getKey().isRequirement()) continue;
                double v = en.getKey() == Stat.HP ? Math.round(en.getValue() * RpgCraft.get().stats().hpScale() * 10) / 10.0 : en.getValue();
                lore.add("  &7" + en.getKey().label + " " + (v >= 0 ? "&f" : "&c") + Text.signed(shown(en.getKey(), v), en.getKey().pct));
            }

            String setId = ItemData.getString(it, Keys.SET);
            if (setId != null) {
                lore.add("");
                lore.add("&b▸ 세트 효과 &8(" + ItemData.getInt(it, Keys.SET_N, 3) + "부위)");
                for (Map.Entry<Stat, Double> en : ItemData.setBonus(it).entries())
                    lore.add("  &3" + en.getKey().label + " " + Text.signed(shown(en.getKey(), en.getValue()), en.getKey().pct));
            }
            if (t.effect != null) {
                ItemEffectManager.Effect fx = ItemEffectManager.Effect.find(t.effect);
                if (fx != null) {
                    lore.add("");
                    lore.add("&d✦ 특수 효과 &8· &d&l" + fx.label);
                    for (String line : wrap(fx.desc, 22)) lore.add("  &7" + line);
                }
            }
            List<String> lim = kr.rpgcraft.feature.LimitBreakManager.lore(it);
            if (!lim.isEmpty()) { lore.add(""); lore.addAll(lim); }
            List<String> pot = kr.rpgcraft.feature.PotentialManager.lore(it);
            if (!pot.isEmpty()) {
                lore.add("");
                lore.addAll(pot);
            }
            if (RpgCraft.get() != null && RpgCraft.get().skillBook() != null) {
                List<String> sk = RpgCraft.get().skillBook().lore(t);
                if (!sk.isEmpty()) {
                    lore.add("");
                    lore.add("&b✦ 무기 스킬");
                    lore.addAll(sk);
                }
            }
            if (t.skill != null) {
                lore.add("");
                lore.add("&d✦ 우클릭 스킬");
                for (String line : wrap(RpgCraft.get().spirits().skillLabel(t.skill), 24)) lore.add("  &7" + line);
            }

            lore.add(SEP);
            StatMap base = ItemData.baseStats(it);
            List<String> req = new ArrayList<>();
            if (base.get(Stat.LEVEL_REQ) > 0 && t.category != Category.WEAPON && t.category != Category.BOW && t.category != Category.ARMOR) req.add("Lv." + (int) base.get(Stat.LEVEL_REQ));
            if (base.get(Stat.REQ_STR) > 0) req.add("힘 " + (int) base.get(Stat.REQ_STR));
            if (base.get(Stat.REQ_DEX) > 0) req.add("민첩 " + (int) base.get(Stat.REQ_DEX));
            if (base.get(Stat.REQ_ADV) > 0) req.add("모험 " + (int) base.get(Stat.REQ_ADV));
            if (!req.isEmpty()) lore.add("&8요구 " + String.join(" · ", req));
            lore.add("&8전투력 &7" + Text.num(Power.of(eff)) + " &8· " + (t.enhanceable ? "강화 " + e + "/" + ItemData.maxEnhance(it) : "강화 불가"));
            applyModel(m, t, e);
            applyTrim(m, t);
        } else {
            m.setDisplayName(Text.c(g.nameColor() + name));
            lore.add(g.tag() + " &8· &7" + t.category.label);
            flavor(t, lore);
            List<String> info = new ArrayList<>();
            if (t.category == Category.RUNE) {
                for (Map.Entry<Stat, Double> en : StatMap.parse(ItemData.getString(it, Keys.RUNE)).entries()) {
                    double v = en.getValue();
                    if (t.id.startsWith("rune_") && v > 0 && (en.getKey() == Stat.HP || en.getKey() == Stat.HP_PCT) && RpgCraft.get() != null)
                        v *= RpgCraft.get().getConfig().getDouble("player.rune-hp-mult", 1.5);   // 룬 체력 상향 반영
                    info.add("  &7" + en.getKey().label + " " + (v >= 0 ? "&a" : "&c") + Text.signed(shown(en.getKey(), v), en.getKey().pct));
                }
            }
            if (t.category == Category.TOTEM) {
                String tt = ItemData.getString(it, Keys.TOTEM);
                if (tt != null) info.add("  &e" + RpgCraft.get().guilds().totemLabel(tt));
            }
            if (t.category == Category.CHECK) info.add("  &e금액 " + Text.money((long) ItemData.value(it)));
            if (t.category == Category.POTION) info.add("  &c체력 회복 +" + Text.num(t.value * RpgCraft.get().stats().hpScale()));
            if (t.category == Category.HAMMER) info.add("  &e성벽 회복 +" + Text.num(t.value));
            if (t.category == Category.TOOL) {
                info.add("  &7채집 쿨타임 &f" + Text.time(t.cooldown));
                info.add("  &7성벽 대미지 &f" + (int) t.value);
            }
            if (!info.isEmpty()) {
                lore.add(SEP);
                lore.addAll(info);
            }
        }
        if (!t.desc.isEmpty()) {
            lore.add("");
            for (String d : t.desc) lore.add("&8" + d);
        }
        String crafter = m.getPersistentDataContainer().get(Keys.CRAFTER, PersistentDataType.STRING);
        if (crafter != null) lore.add("&6⚒ 제작: " + crafter);
        if (t.sell > 0) {
            double sm = 1;
            if (t.id.startsWith("loot_") || t.id.startsWith("fish_")) sm = RpgCraft.get() == null ? 1 : RpgCraft.get().getConfig().getDouble("economy.loot-sell-mult", 0.15);
            lore.add("&8판매가 " + Text.money(Math.max(1, Math.round(t.sell * sm))));
        }
        m.setLore(Text.c(lore));
        it.setItemMeta(m);
    }

    private static void flavor(ItemTemplate t, List<String> lore) {
        if (t.epithet == null && t.flavor.isEmpty()) return;
        lore.add("");
        if (t.epithet != null) lore.add("&6『" + t.epithet + "』");
        for (String f : t.flavor) for (String line : wrap(f, 26)) lore.add("&7&o" + line);
    }

    /** 강화 단계에 따른 모델 교체. 대장장이 /외형 으로 바꾼 모델은 건드리지 않는다. */
    /** 초월 장비는 밸런스 패치(너프)를 이미 가진 장비에도 적용: 기본 스탯 · 세트 효과를 현재 정의로 맞춤 (강화 · 잠재 · 한계 돌파는 유지) */
    private static void resyncTranscend(ItemStack it, ItemTemplate t) {
        if (!t.id.startsWith("trans_") || !t.category.isEquipment()) return;
        String want = t.stats.serialize();
        if (!want.equals(ItemData.getString(it, Keys.STATS))) ItemData.setString(it, Keys.STATS, want);
        if (t.setBonus != null) {
            String sb = t.setBonus.serialize();
            if (!sb.equals(ItemData.getString(it, Keys.SET_BONUS))) ItemData.setString(it, Keys.SET_BONUS, sb);
        }
    }

    private static void applyModel(ItemMeta m, ItemTemplate t, int e) {
        if (t.category != Category.WEAPON || !t.enhanceable || t.modelData <= 0) return;
        Integer cur = m.hasCustomModelData() ? m.getCustomModelData() : null;
        int b = t.modelData;
        if (cur != null && cur != b && cur != b + OFFSET_ENGRAVED && cur != b + OFFSET_PERFECT) return;
        int want = b + (e >= STAGE_PERFECT ? OFFSET_PERFECT : e >= STAGE_ENGRAVED ? OFFSET_ENGRAVED : 0);
        m.setCustomModelData(want);
    }

    /** 사신수·사흉수 방어구: 착용했을 때 보이는 장식 무늬 */
    private static void applyTrim(ItemMeta m, ItemTemplate t) {
        if (t.trimPattern == null || !(m instanceof org.bukkit.inventory.meta.ArmorMeta am)) return;
        try {
            var pat = org.bukkit.Registry.TRIM_PATTERN.get(org.bukkit.NamespacedKey.minecraft(t.trimPattern));
            var mat = org.bukkit.Registry.TRIM_MATERIAL.get(org.bukkit.NamespacedKey.minecraft(t.trimMaterial));
            if (pat != null && mat != null) am.setTrim(new org.bukkit.inventory.meta.trim.ArmorTrim(mat, pat));
        } catch (Throwable ignored) {
        }
    }

    /** 긴 문장을 공백 기준으로 줄바꿈 */
    public static List<String> wrap(String s, int width) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String w : s.split(" ")) {
            if (line.length() > 0 && line.length() + w.length() + 1 > width) {
                out.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            line.append(w);
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }
}
