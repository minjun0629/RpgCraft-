package kr.rpgcraft.stat;

import kr.rpgcraft.Keys;
import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.data.PlayerData;
import kr.rpgcraft.item.Category;
import kr.rpgcraft.item.ItemData;
import kr.rpgcraft.item.ItemTemplate;
import kr.rpgcraft.passive.Passive;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

import static kr.rpgcraft.stat.Stat.*;

/** 장비 + 스탯 + 룬 + 패시브 + 길드 토템을 합산해 최종 능력치를 만든다 */
public class StatCalculator {
    private final RpgCraft plugin;

    public StatCalculator(RpgCraft plugin) {
        this.plugin = plugin;
    }

    public StatSnapshot refresh(Player p) {
        PlayerData d = plugin.data().get(p);
        StatSnapshot s = compute(p, d);
        d.stats = s;
        if (d.hp < 0 || d.hp > s.maxHp) d.hp = s.maxHp;
        return s;
    }

    /** 요구 조건 미충족 사유 (충족 시 null) */
    private double baseHpEstimate(PlayerData d) {
        var c = plugin.getConfig();
        return c.getDouble("player.base-hp", 1000) + c.getDouble("player.hp-per-level", 60) * d.level + d.adv * c.getDouble("player.adv-hp-per-point", 140);
    }

    /** 힘 구간 효과 설명 (스탯 창) */
    public static java.util.List<String> strMilestones(PlayerData d) {
        int s50 = d.str / 50;
        java.util.List<String> out = new java.util.ArrayList<>();
        out.add("&6힘 50마다: &f힘 +4% · 관통 +2 · 치명타 피해 +3%");
        out.add("&7 현재 " + s50 + "단계 → 힘 +" + 4 * s50 + "% · 관통 +" + 2 * s50 + " · 치명타 피해 +" + 3 * s50 + "%");
        out.add("&7 다음: 힘 " + (s50 + 1) * 50);
        out.add((d.str >= 250 ? "&a✔" : "&8✘") + " 250 분쇄 &7관통 +5, 공격 시 10% 확률로 강타(+50%)");
        out.add((d.str >= 500 ? "&a✔" : "&8✘") + " 500 괴력 &7힘 +10%");
        out.add((d.str >= 1000 ? "&a✔" : "&8✘") + " 1000 파괴자 &7치명타 +10%");
        return out;
    }

    public static java.util.List<String> dexMilestones(PlayerData d) {
        int k = d.dex / 50;
        java.util.List<String> out = new java.util.ArrayList<>();
        out.add("&a민첩 50마다: &f치명타 피해 +6% · 이동속도 +1% · 회피 +0.5%");
        out.add("&7 현재 " + k + "단계 → 치명타 피해 +" + 6 * k + "% · 이동속도 +" + k + "% · 회피 +" + String.format("%.1f", 0.5 * k) + "%");
        out.add("&7 다음: 민첩 " + (k + 1) * 50);
        out.add((d.dex >= 250 ? "&a✔" : "&8✘") + " 250 질풍 &7회피 +3%, 공격 시 10% 확률로 연속 베기(+40%)");
        out.add((d.dex >= 500 ? "&a✔" : "&8✘") + " 500 명사수 &7치명타 +8%");
        out.add((d.dex >= 1000 ? "&a✔" : "&8✘") + " 1000 그림자 칼날 &7치명타 피해 +40%");
        return out;
    }

    public static java.util.List<String> advMilestones(PlayerData d) {
        int k = d.adv / 50;
        java.util.List<String> out = new java.util.ArrayList<>();
        out.add("&b모험 50마다: &f체력 +3% · 방어력 +1 · 경험치 +1%");
        out.add("&7 현재 " + k + "단계 → 체력 +" + 3 * k + "% · 방어력 +" + k + " · 경험치 +" + k + "%");
        out.add("&7 다음: 모험 " + (k + 1) * 50);
        out.add((d.adv >= 250 ? "&a✔" : "&8✘") + " 250 강인함 &7받는 피해 -5%");
        out.add((d.adv >= 500 ? "&a✔" : "&8✘") + " 500 불굴 &7체력 30% 이하일 때 받는 피해 -15%");
        out.add((d.adv >= 1000 ? "&a✔" : "&8✘") + " 1000 수호신의 가호 &7체력 +15%");
        return out;
    }

    public String requirementProblem(PlayerData d, StatMap base) {
        return requirementProblem(d, base, false, 0, 0, 0);
    }

    /** weapon=true: 레벨 제한 없음. bs/bd/ba: 장비·룬·장신구 등으로 더해진 힘/민첩/모험 */
    public String requirementProblem(PlayerData d, StatMap base, boolean weapon, double bs, double bd, double ba) {
        if (!weapon && d.level < base.get(LEVEL_REQ)) return "레벨 " + (int) base.get(LEVEL_REQ) + " 필요";
        if (d.str + bs + 1e-6 < base.get(REQ_STR)) return "힘 " + (int) base.get(REQ_STR) + " 필요";
        if (d.dex + bd + 1e-6 < base.get(REQ_DEX)) return "민첩 " + (int) base.get(REQ_DEX) + " 필요";
        if (d.adv + ba + 1e-6 < base.get(REQ_ADV)) return "모험 " + (int) base.get(REQ_ADV) + " 필요";
        return null;
    }

    private static StatMap withoutReq(StatMap m) {
        StatMap o = m.copy();
        for (Stat s : new Stat[]{REQ_STR, REQ_DEX, REQ_ADV, LEVEL_REQ}) o.set(s, 0);
        return o;
    }

    public StatSnapshot compute(Player p, PlayerData d) {
        FileConfiguration c = plugin.getConfig();
        StatSnapshot s = new StatSnapshot();
        StatMap t = new StatMap();
        List<ItemStack> worn = new ArrayList<>();

        ItemStack main = p.getInventory().getItemInMainHand();
        ItemTemplate mt = ItemData.template(main);
        if (mt != null && (mt.category == Category.WEAPON || mt.category == Category.BOW)) {
            // 무기 외의 곳(방어구·룬·장신구·직업)에서 오른 힘/민첩/모험도 요구 조건에 포함 (스탯 창에 보이는 값과 같게)
            StatMap pre = new StatMap();
            for (ItemStack a : p.getInventory().getArmorContents())
                if (a != null && ItemData.template(a) != null && requirementProblem(d, ItemData.baseStats(a), true, 0, 0, 0) == null) pre.addAll(ItemData.effectiveStats(a));
            for (ItemStack r : d.runes) if (r != null) pre.addAll(StatMap.parse(ItemData.getString(r, kr.rpgcraft.Keys.RUNE)));
            if (plugin.accessories() != null) pre.addAll(plugin.accessories().bonus(d));
            pre.addAll(plugin.jobs().bonus(d));
            String problem = requirementProblem(d, ItemData.baseStats(main), true, 0, 0, 0);   // 직접 찍은 스탯만
            if (problem == null && mt.id.startsWith("hjw_")) {   // v5.10.20 히든 직업 전용 무기
                var hj = kr.rpgcraft.world.HiddenJobManager.of(d);
                String want = mt.id.substring(4, 5).toUpperCase(java.util.Locale.ROOT);
                if (hj == null || !hj.line().equals(want)) problem = "히든 직업 전용 무기";
            }
            if (problem == null) {
                t.addAll(withoutReq(ItemData.effectiveStats(main)));
                if (mt.weaponClass == kr.rpgcraft.item.WeaponClass.SPEAR)   // 창 방어 관통 너프
                    t.add(ARMOR_PEN, -ItemData.effectiveStats(main).get(ARMOR_PEN) * plugin.getConfig().getDouble("weapons.spear-pen-mult-cut", 0.3));
                s.weaponClass = ItemData.weaponClass(main);
                s.holdingBow = mt.category == Category.BOW;
                worn.add(main);
            } else {
                s.weaponOk = false;
                s.weaponProblem = problem;
            }
        }
        StatMap preA = new StatMap();
        for (ItemStack r : d.runes) if (r != null) preA.addAll(StatMap.parse(ItemData.getString(r, kr.rpgcraft.Keys.RUNE)));
        if (plugin.accessories() != null) preA.addAll(plugin.accessories().bonus(d));
        preA.addAll(plugin.jobs().bonus(d));
        for (ItemStack a : p.getInventory().getArmorContents()) {
            ItemTemplate at = ItemData.template(a);
            if (at == null || at.category != Category.ARMOR) continue;
            if (requirementProblem(d, ItemData.baseStats(a), true, 0, 0, 0) != null) continue;   // 직접 찍은 스탯만
            double negHp = ItemData.effectiveStats(a).get(HP);
            if (negHp < 0 && -negHp >= baseHpEstimate(d)) continue;   // 최대 체력을 0 이하로 만드는 장비는 착용 불가
            t.addAll(withoutReq(ItemData.effectiveStats(a)));
            worn.add(a);
        }
        // 세트 효과: 세트별 착용 수가 각 부위의 발동 조건 이상이면 그 부위의 보너스 적용
        Map<String, Integer> setCount = new HashMap<>();
        for (ItemStack it : worn) {
            String set = ItemData.getString(it, Keys.SET);
            if (set != null) setCount.merge(set, 1, Integer::sum);
        }
        for (ItemStack it : worn) {
            String set = ItemData.getString(it, Keys.SET);
            if (set != null && setCount.get(set) >= ItemData.getInt(it, Keys.SET_N, 3)) t.addAll(ItemData.setBonus(it));
        }
        for (ItemStack r : d.runes) {
            if (r != null && ItemData.category(r) == Category.RUNE) {
                StatMap rs = StatMap.parse(ItemData.getString(r, Keys.RUNE));
                double rm = plugin.getConfig().getDouble("player.rune-hp-mult", 1.5);   // 룬 체력 효과 상향
                if (rs.get(HP) > 0) rs.add(HP, rs.get(HP) * (rm - 1));
                if (rs.get(HP_PCT) > 0) rs.add(HP_PCT, rs.get(HP_PCT) * (rm - 1));
                t.addAll(rs);
            }
        }
        for (String id : d.passives) {
            try {
                Passive ps = Passive.valueOf(id);
                if (ps.flat != null) t.addAll(ps.flat);
            } catch (IllegalArgumentException ignored) {
            }
        }
        applySpecialPassives(p, d, t);
        t.addAll(plugin.jobs().bonus(d));
        if (plugin.accessories() != null) t.addAll(plugin.accessories().bonus(d));
        if (plugin.pets() != null) t.addAll(plugin.pets().bonus(d));   // 꺼내 둔 펫
        t.addAll(plugin.constellation().bonus(d));   // v5.10.59 별자리
        t.addAll(plugin.souls().bonus(d));           // v5.10.59 영혼석
        HiddenStat.apply(d, t);   // 히든 스탯
        t.addAll(kr.rpgcraft.world.HiddenJobManager.bonus(d));
        int reb = (int) d.counter("rebirth");
        if (reb > 0) {
            double cp = plugin.getConfig().getDouble("rebirth.core-pct", 30) * reb;
            t.add(STR_PCT, cp).add(DEX_PCT, cp).add(ADV_PCT, cp).add(EXP_PCT, plugin.getConfig().getDouble("rebirth.exp-pct", 25) * reb);
        }
        t.addAll(kr.rpgcraft.feature.RebirthShop.bonus(d));   // 환생 상점 영구 강화
        if (plugin.legendary() != null) t.addAll(plugin.legendary().bonus(d.uuid));
        // 스탯 50포인트마다 특별 효과: 힘 → 공격력·방어 관통 / 민첩 → 치명타 피해·이동속도 / 모험 → 체력%·방어력
        int s50 = (int) (d.str / 50), d50 = (int) (d.dex / 50), a50 = (int) (d.adv / 50);
        if (s50 > 0) { t.add(STR_PCT, 4 * s50); t.add(ARMOR_PEN, 2 * s50); t.add(CRIT_DMG, 3 * s50); }   // 힘 50마다
        if (d.str >= 250) t.add(ARMOR_PEN, 5);    // 힘 250: 분쇄
        if (d.str >= 500) t.add(STR_PCT, 10);     // 힘 500: 괴력
        if (d.str >= 1000) t.add(CRIT, 10);       // 힘 1000: 파괴자
        if (d50 > 0) { t.add(CRIT_DMG, 6 * d50); t.add(SPEED, 1 * d50); t.add(DODGE, 0.5 * d50); }   // 민첩 50마다
        if (d.dex >= 250) t.add(DODGE, 3);        // 민첩 250: 질풍
        if (d.dex >= 500) t.add(CRIT, 8);         // 민첩 500: 명사수
        if (d.dex >= 1000) t.add(CRIT_DMG, 40);   // 민첩 1000: 그림자 칼날
        if (a50 > 0) { t.add(HP_PCT, 3 * a50); t.add(DEF, 1 * a50); t.add(EXP_PCT, 1 * a50); }   // 모험 50마다
        if (d.adv >= 1000) t.add(HP_PCT, 15);     // 모험 1000: 수호신의 가호
        long nowMs = System.currentTimeMillis();                         // 주문서 버프
        if (d.counter("buff_atk") > nowMs) t.add(STR_PCT, 15);
        if (d.counter("buff_def") > nowMs) t.add(DEF, 5);
        if (d.counter("buff_speed") > nowMs) t.add(SPEED, 15);
        if (d.counter("buff_exp") > nowMs) t.add(EXP_PCT, 50);
        t.addAll(plugin.guilds().totemStats(p.getUniqueId()));
        t.addAll(plugin.guilds().skillStats(p.getUniqueId()));   // v5.10.20 길드 스킬
        if (plugin.wars() != null) t.addAll(plugin.wars().castleBonus(p.getUniqueId()));   // v5.10.20 성 소유 혜택
        if (plugin.cooking() != null) t.addAll(plugin.cooking().bonus(d));   // v5.10.20 요리 버프
        if (plugin.mastery() != null) t.addAll(plugin.mastery().bonus(d));   // v5.10.30 직업 숙련

        s.str = Math.max(0, (d.str + t.get(STR)) * (1 + t.get(STR_PCT) / 100));
        s.dex = Math.max(0, (d.dex + t.get(DEX)) * (1 + t.get(DEX_PCT) / 100));
        s.adv = Math.max(0, (d.adv + t.get(ADV)) * (1 + t.get(ADV_PCT) / 100));

        s.attack = Math.max(1, c.getDouble("player.base-attack", 5) + t.get(ATK)
                + Math.floor(s.str / 2) * c.getDouble("player.str-atk-per-2", 3.0));
        s.ranged = Math.max(0, t.get(RANGED_ATK) + s.attack * 0.25) * plugin.getConfig().getDouble("weapon-skills.bow-damage-mult", 1.35);   // 활 피해 배율
        // 힘 → 공격력·마력 / 민첩 → 치명타 확률·치명타 피해·이동속도 / 모험 → 체력·방어력
        s.magic = Math.max(0, s.str * c.getDouble("player.str-magic-per-point", 2.0) + t.get(MAGIC));
        s.crit = clamp(s.dex * c.getDouble("player.dex-crit-per-point", 0.12) + t.get(CRIT), 0, 100);
        s.critDmg = t.get(CRIT_DMG) + s.dex * c.getDouble("player.dex-critdmg-per-point", 0.5);
        s.def = clamp(t.get(DEF) + s.adv * c.getDouble("player.adv-def-per-point", 0.05), 0, c.getDouble("player.max-defense", 90));
        double hp = c.getDouble("player.base-hp", 1000) + d.level * c.getDouble("player.hp-per-level", 200)
                + s.adv * c.getDouble("player.adv-hp-per-point", 40) + t.get(HP) * c.getDouble("player.item-hp-mult", 0.4);
        double hpPct = t.get(HP_PCT) * c.getDouble("player.item-hp-mult", 0.4) + (d.doldolUntil > System.currentTimeMillis() ? 50 : 0);   // 체력% 효과 너프
        s.maxHp = Math.max(1, Math.round(hp * (1 + hpPct / 100) * hpScale() * 10) / 10.0);
        s.lifesteal = Math.max(0, t.get(LIFESTEAL)) * plugin.getConfig().getDouble("player.lifesteal-mult", 0.85);   // v5.10.34 흡혈 소폭 너프 (-15%)
        s.armorPen = clamp(t.get(ARMOR_PEN), 0, 100);
        s.dodge = clamp(t.get(DODGE), 0, 75);
        s.speed = (t.get(SPEED) + Math.min(c.getDouble("player.dex-speed-max", 30), s.dex * c.getDouble("player.dex-speed-per-point", 0.05)))
                * c.getDouble("player.speed-mult", 0.7);   // 이동속도 증가 너프
        s.expPct = t.get(EXP_PCT);
        s.enhanceRate = t.get(ENHANCE_RATE);
        return s;
    }

    /** v5.10.45 히든 · 던전 · 최초 보상 패시브가 주는 능력치 합 (스탯 창 표시용, 이미 최종 능력치에 포함됨) */
    public StatMap passiveBonus(Player p, PlayerData d) {
        StatMap t = new StatMap();
        for (String id : d.passives) {
            try {
                Passive ps = Passive.valueOf(id);
                if (ps.flat != null) t.addAll(ps.flat);
            } catch (IllegalArgumentException ignored) {
            }
        }
        applySpecialPassives(p, d, t);
        return t;
    }

    private void applySpecialPassives(Player p, PlayerData d, StatMap t) {
        if (d.has(Passive.TRAINED_FEAR)) t.add(ATK, Math.min(500, d.counter("trained_fear")));
        if (d.has(Passive.FAMILIAR_FEAR)) t.add(ATK, Math.min(500, d.counter("familiar_fear")));
        if (d.has(Passive.HIDDEN_POWER)) {
            t.add(ATK, d.statPoints * 3.0);
            t.add(HP, d.statPoints * 8.0);
        }
        if (d.has(Passive.WEALTH)) {
            if (d.money >= 100_000_000L) t.add(ATK, 900).add(HP, 3000);
            else if (d.money >= 30_000_000L) t.add(ATK, 300).add(HP, 1000);
            else if (d.money >= 10_000_000L) t.add(ATK, 80).add(HP, 300);
        }
        if (d.has(Passive.STRONGEST)) {
            int top = 0;
            for (Player o : Bukkit.getOnlinePlayers()) top = Math.max(top, plugin.data().get(o).level);
            if (d.level >= top) t.add(ATK, 500).add(HP, 2000);
        }
        if (d.has(Passive.MOON_FISHER)) t.add(HP, Math.min(8000, d.counter("moon_fish")));
        if (d.has(Passive.MOON_GATHERER)) t.add(HP, Math.min(8000, d.counter("moon_gather")));
        if (d.has(Passive.BLUE_TRANSCEND)) {
            if (d.level >= 100) t.add(HP, 1000);
            if (d.level >= 105) t.add(HP, 1000);
        }
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /**
     * 플레이어 체력 단위 환산: 1레벨 기본 체력이 config player.start-hp(기본 20)이 되도록 전체 체력을 축소한다.
     * 몬스터가 플레이어에게 주는 피해·회복량도 같은 비율로 줄여 난이도는 그대로 유지된다.
     */
    public double hpScale() {
        var c = plugin.getConfig();
        double lv1 = c.getDouble("player.base-hp", 1000) + c.getDouble("player.hp-per-level", 200);
        return c.getDouble("player.start-hp", 20) / Math.max(1, lv1);
    }
}
