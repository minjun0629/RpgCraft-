package kr.rpgcraft.data;

import org.bukkit.Material;

/** 플레이어 개인 설정. PlayerData.counters 에 "opt_이름" = 1/0 으로 저장된다. */
public enum Setting {
    SIDEBAR("사이드바", "오른쪽 정보창 표시", Material.PAINTING, true),
    COMPASS("나침반", "화면 위 방위 표시 (히든 상인·웨이브 깃발 방향 포함)", Material.COMPASS, true),
    HUD("액션바 HUD", "체력/공격력 상태줄 표시", Material.CLOCK, true),
    INDICATOR("대미지 표시", "때린 대미지 숫자 표시", Material.REDSTONE, true),
    SOUND("메뉴 효과음", "GUI 클릭음", Material.NOTE_BLOCK, true),
    LOOT_NOTICE("희귀 드롭 알림", "레어 이상 아이템 획득 시 알림", Material.DIAMOND, true),
    ANNOUNCE("서버 공지", "강화 성공/보스 등 전체 공지 수신", Material.BELL, true),
    EXP_CHAT("획득 경험치 채팅", "경험치를 얻을 때마다 채팅에 표시", Material.EXPERIENCE_BOTTLE, false),
    PVP("PvP", "끄면 다른 플레이어와 서로 공격할 수 없음 (둘 다 켜야 PvP · 길드전은 예외 · 전투 후 15초간 못 끔)", Material.IRON_SWORD, true);

    public final String label, desc;
    public final Material icon;
    public final boolean def;

    Setting(String label, String desc, Material icon, boolean def) {
        this.label = label;
        this.desc = desc;
        this.icon = icon;
        this.def = def;
    }

    private String key() {
        return "opt_" + name();
    }

    public boolean get(PlayerData d) {
        Double v = d.counters.get(key());
        return v == null ? def : v > 0;
    }

    public void set(PlayerData d, boolean on) {
        d.counters.put(key(), on ? 1.0 : 0.0);
    }

    public boolean toggle(PlayerData d) {
        boolean n = !get(d);
        set(d, n);
        return n;
    }
}
