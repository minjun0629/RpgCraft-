package kr.rpgcraft.stat;

import kr.rpgcraft.data.PlayerData;

/**
 * 히든 스탯: 특별한 행동을 쌓으면 조용히 오르는 숨은 능력치 (설명 없이, 오를 때만 알림)
 *  투지 : 체력 5% 이하에서 나보다 레벨 높은 몬스터 100마리 처치마다 +1 → 주는 피해 +1%
 *  끈기 : 체력 10% 이하에서 버틴 피격 200회마다 +1 → 받는 피해 -0.5%
 *  집중 : 치명타 2,000회마다 +1 → 치명타 +0.3%
 *  질주 : 달린 시간 1시간마다 +1 → 이동속도 +1%
 *  대담 : 보스 처치 5회마다 +1 → 치명타 피해 +2%
 *  관록 : 사망 20회마다 +1 → 체력 +1%
 */
public enum HiddenStat {
    GRIT("투지", "hs_grit", 100, 20), TENACITY("끈기", "hs_tenacity", 200, 20), FOCUS("집중", "crit_hits", 2000, 20),
    SWIFT("질주", "sprint_seconds", 3600, 20), DARING("대담", "ach_boss", 5, 20), VETERAN("관록", "deaths", 20, 20);

    public final String label, counter;
    public final int per, max;

    HiddenStat(String label, String counter, int per, int max) {
        this.label = label;
        this.counter = counter;
        this.per = per;
        this.max = max;
    }

    public int points(PlayerData d) {
        return (int) Math.min(max, Math.floor(d.counter(counter) / per));
    }

    /** 스탯으로 반영되는 것 (투지·끈기는 전투 계산에서 직접 적용) */
    public static void apply(PlayerData d, StatMap t) {
        t.add(Stat.CRIT, FOCUS.points(d) * 0.3);
        t.add(Stat.SPEED, SWIFT.points(d));
        t.add(Stat.CRIT_DMG, DARING.points(d) * 2);
        t.add(Stat.HP_PCT, VETERAN.points(d));
    }
}
