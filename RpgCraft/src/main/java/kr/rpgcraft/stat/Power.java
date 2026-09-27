package kr.rpgcraft.stat;

import java.util.Map;

/** 전투력: 장비/플레이어의 종합 강함을 하나의 숫자로 */
public final class Power {
    private Power() {}

    private static double weight(Stat s) {
        return switch (s) {
            case ATK -> 1.0;
            case MAGIC -> 0.6;
            case RANGED_ATK -> 0.5;
            case CRIT -> 20;
            case CRIT_DMG -> 8;
            case DEF -> 40;
            case HP -> 0.2;
            case HP_PCT -> 50;
            case LIFESTEAL -> 60;
            case ARMOR_PEN -> 30;
            case DODGE -> 50;
            case SPEED -> 15;
            case STR, DEX, ADV -> 1.5;
            case STR_PCT, DEX_PCT, ADV_PCT -> 10;
            case EXP_PCT -> 5;
            case ENHANCE_RATE -> 30;
            default -> 0;
        };
    }

    public static long of(StatMap m) {
        double p = 0;
        for (Map.Entry<Stat, Double> e : m.entries()) p += e.getValue() * weight(e.getKey());
        return Math.max(0, Math.round(p));
    }

    public static long of(StatSnapshot s) {
        double p = s.attack + s.magic * 0.6 + s.ranged * 0.3 + s.crit * 20 + s.critDmg * 8 + s.def * 40 + s.maxHp * 0.2
                + s.lifesteal * 60 + s.armorPen * 30 + s.dodge * 50 + s.speed * 15;
        return Math.round(p);
    }
}
