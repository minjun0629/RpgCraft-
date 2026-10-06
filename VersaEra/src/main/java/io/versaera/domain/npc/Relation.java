package io.versaera.domain.npc;

import java.util.Set;

/**
 * 플레이어 ↔ NPC 관계 (플레이어별 저장). 호감 -1000 ~ 1000. 단계 이름 · 수치는 ORIGINAL.
 * 매일 대화는 첫 번째만 크게 오르고(같은 날 반복 대화는 의미 없음), 선물은 취향에 따라 오르내린다.
 */
public final class Relation {
    public static final int MIN = -1000, MAX = 1000;
    private static final int[] STEPS = {-1000, -300, -50, 100, 400, 800};
    private static final String[] NAMES = {"적대", "냉담", "중립", "호의", "신뢰", "맹우"};

    private Relation() {
    }

    public static int clamp(long v) {
        return (int) Math.max(MIN, Math.min(MAX, v));
    }

    public static int tier(int affinity) {
        int t = 0;
        for (int i = 0; i < STEPS.length; i++) if (affinity >= STEPS[i]) t = i;
        return t;
    }

    public static String tierName(int affinity) {
        return NAMES[tier(affinity)];
    }

    /** 대화로 오르는 호감: 그날 첫 대화만 +5 (호감이 높을수록 오르기 어려움) */
    public static int talkGain(int affinity, boolean firstToday) {
        if (!firstToday) return 0;
        return affinity >= 400 ? 2 : affinity >= 100 ? 3 : 5;
    }

    /** 선물: 좋아하는 태그면 +(10 ~ 40, 품질 따라), 싫어하는 태그면 -15, 그 밖은 +2 */
    public static int giftGain(NpcDefinition npc, Set<String> itemTags, int quality) {
        for (String t : itemTags) if (npc.dislikes().contains(t)) return -15;
        for (String t : itemTags) if (npc.likes().contains(t)) return 10 + Math.max(0, Math.min(1000, quality)) * 30 / 1000;
        return 2;
    }
}
