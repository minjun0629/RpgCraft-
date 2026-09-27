package kr.rpgcraft.stat;

public enum Stat {
    ATK("공격력", false),
    MAGIC("마력", false),
    RANGED_ATK("원거리 공격력", false),
    CRIT("크리티컬 확률", true),
    CRIT_DMG("크리티컬 대미지", true),
    DEF("방어력", true),
    HP("체력", false),
    HP_PCT("최대 체력", true),
    LIFESTEAL("체력흡수", true),
    ARMOR_PEN("방어력무시", true),
    DODGE("회피", true),
    SPEED("이동속도", false),
    STR("힘", false),
    DEX("민첩", false),
    ADV("모험", false),
    STR_PCT("힘", true),
    DEX_PCT("민첩", true),
    ADV_PCT("모험", true),
    EXP_PCT("경험치 획득량", true),
    ENHANCE_RATE("강화 확률", true),
    REQ_STR("요구 힘", false),
    REQ_DEX("요구 민첩", false),
    REQ_ADV("요구 모험", false),
    LEVEL_REQ("레벨제한", false);

    public final String label;
    public final boolean pct;

    Stat(String label, boolean pct) {
        this.label = label;
        this.pct = pct;
    }

    public boolean isRequirement() {
        return this == REQ_STR || this == REQ_DEX || this == REQ_ADV || this == LEVEL_REQ;
    }
}
