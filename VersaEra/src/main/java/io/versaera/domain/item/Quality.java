package io.versaera.domain.item;

/**
 * 품질 0 ~ 1000. 등급 이름은 ORIGINAL.
 * 품질은 능력치 배율(0.7 ~ 1.4)과 최대 내구도 배율(0.8 ~ 1.3)에 반영된다.
 */
public final class Quality {
    public static final int MIN = 0, MAX = 1000;
    private static final int[] STEPS = {0, 200, 400, 600, 800, 950};
    private static final String[] NAMES = {"조악", "보통", "양품", "상품", "명품", "걸작"};

    private Quality() {
    }

    public static int clamp(double q) {
        return (int) Math.max(MIN, Math.min(MAX, Math.round(q)));
    }

    public static int grade(int q) {
        int g = 0;
        for (int i = 0; i < STEPS.length; i++) if (q >= STEPS[i]) g = i;
        return g;
    }

    public static String gradeName(int q) {
        return NAMES[grade(q)];
    }

    public static double statMultiplier(int q) {
        return 0.7 + 0.7 * clamp(q) / (double) MAX;
    }

    public static double durabilityMultiplier(int q) {
        return 0.8 + 0.5 * clamp(q) / (double) MAX;
    }
}
