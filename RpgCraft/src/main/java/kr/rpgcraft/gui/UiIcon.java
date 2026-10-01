package kr.rpgcraft.gui;

/** 자동 생성 (tools/ui_icons.py): 4R 풍 메뉴 아이콘. PAPER CustomModelData = 12600 + 순번 (꺼진 회색 그림은 +100) */
public enum UiIcon {
    SEASON_PASS,
    SKILL,
    ENHANCE,
    RUNE,
    POTION_BAG,
    AURA,
    SMITH,
    QUEST,
    SHOP,
    WARP,
    GUILD,
    POTENTIAL,
    RANKING,
    CODEX,
    ACHIEVEMENT,
    ATTENDANCE,
    ACCESSORY,
    ROULETTE,
    SETTINGS,
    JOB,
    HIDDEN_JOB,
    HELP,
    TOWER,
    MASTERY,
    STAT_STR,
    STAT_DEX,
    STAT_ADV,
    STAT_POINT,
    STAT_INFO,
    OPT_SIDEBAR,
    OPT_COMPASS,
    OPT_HUD,
    OPT_INDICATOR,
    OPT_SOUND,
    OPT_LOOT_NOTICE,
    OPT_ANNOUNCE,
    OPT_EXP_CHAT,
    OPT_PVP,
    OPT_BGM,
    NAV_BACK,
    NAV_NEXT,
    NAV_CLOSE;

    public static final int BASE = 12600;

    public int cmd(boolean on) {
        return BASE + ordinal() + (on ? 0 : 100);
    }
}
