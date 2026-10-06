package io.versaera.domain.skill;

import io.versaera.domain.common.DomainException;

/**
 * 숙련 곡선. 단계 이름 초급 · 중급 · 고급은 CANON, 수치 · 마스터 단계는 ORIGINAL.
 * <pre>
 * 레벨 1 ~ 10  초급 1 ~ 10
 * 레벨 11 ~ 20 중급 1 ~ 10
 * 레벨 21 ~ 30 고급 1 ~ 10
 * 레벨 31      마스터
 * </pre>
 * 다음 레벨까지 필요 경험치 = 100 × 레벨^1.6 → 마스터까지 약 26만. 같은 일만 반복하면 얻는 경험치가 줄어든다.
 */
public final class Mastery {
    public static final int MAX_LEVEL = 31;
    public static final int TIER_BEGINNER = 0, TIER_INTERMEDIATE = 1, TIER_ADVANCED = 2, TIER_MASTER = 3;
    private static final String[] TIER_NAMES = {"초급", "중급", "고급", "마스터"};
    private static final long[] CUMULATIVE = new long[MAX_LEVEL + 1];

    static {
        long sum = 0;
        CUMULATIVE[1] = 0;
        for (int lv = 1; lv < MAX_LEVEL; lv++) {
            sum += need(lv);
            CUMULATIVE[lv + 1] = sum;
        }
    }

    private Mastery() {
    }

    /** lv → lv+1 에 필요한 경험치 */
    public static long need(int lv) {
        DomainException.require(lv >= 1 && lv < MAX_LEVEL, "mastery.bad_level", "레벨 범위 밖: " + lv);
        return Math.round(100 * Math.pow(lv, 1.6));
    }

    /** 레벨 lv 에 처음 도달하는 누적 경험치 */
    public static long cumulative(int lv) {
        DomainException.require(lv >= 1 && lv <= MAX_LEVEL, "mastery.bad_level", "레벨 범위 밖: " + lv);
        return CUMULATIVE[lv];
    }

    public static int levelOf(long xp) {
        DomainException.require(xp >= 0, "mastery.negative_xp", "경험치는 음수일 수 없습니다");
        int lv = 1;
        while (lv < MAX_LEVEL && xp >= CUMULATIVE[lv + 1]) lv++;
        return lv;
    }

    public static int tierOf(int level) {
        if (level >= MAX_LEVEL) return TIER_MASTER;
        return (level - 1) / 10;
    }

    public static String label(int level) {
        int t = tierOf(level);
        return t == TIER_MASTER ? TIER_NAMES[t] : TIER_NAMES[t] + " " + ((level - 1) % 10 + 1);
    }

    /** 다음 레벨까지 진행도 0 ~ 1 */
    public static double progress(long xp) {
        int lv = levelOf(xp);
        if (lv >= MAX_LEVEL) return 1;
        return (xp - CUMULATIVE[lv]) / (double) need(lv);
    }

    /**
     * 행동 하나로 얻는 경험치.
     *
     * @param base           행동의 기본 경험치
     * @param actionLevel    그 행동의 권장 레벨 (쉬운 재료 · 약한 몬스터 = 낮음)
     * @param currentLevel   지금 숙련 레벨
     * @param handBonus      손재주 보너스 배율 (1.0 = 없음)
     * 너무 쉬운 행동(권장 레벨이 지금보다 5 넘게 낮음)은 경험치가 크게 줄고, 어려운 행동은 최대 1.5배.
     */
    public static long gain(long base, int actionLevel, int currentLevel, double handBonus) {
        DomainException.require(base >= 0, "mastery.negative_gain", "경험치는 음수일 수 없습니다");
        if (currentLevel >= MAX_LEVEL) return 0;
        int gap = currentLevel - actionLevel;
        double mult;
        if (gap > 5) mult = Math.max(0.05, 1 - (gap - 5) * 0.15);
        else if (gap < 0) mult = Math.min(1.5, 1 + (-gap) * 0.1);
        else mult = 1;
        return Math.max(0, Math.round(base * mult * Math.max(1, Math.min(1.5, handBonus))));
    }
}
