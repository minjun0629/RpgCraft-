package io.versaera.domain.hidden;

import io.versaera.domain.common.DomainException;

import java.util.List;

/**
 * 히든 콘텐츠 조건. 행동 기록 · 숙련 · 관계 · 장소 · 시간 · 발견을 **조합**한다 (확률 조건은 일부러 없음).
 */
public sealed interface Condition {
    boolean test(PlayerFacts f);

    record Counter(String key, long atLeast) implements Condition {
        public boolean test(PlayerFacts f) { return f.counter(key) >= atLeast; }
    }

    record MasteryAtLeast(String discipline, int level) implements Condition {
        public boolean test(PlayerFacts f) { return f.mastery(discipline) >= level; }
    }

    record AffinityAtLeast(String npc, int value) implements Condition {
        public boolean test(PlayerFacts f) { return f.affinity(npc) >= value; }
    }

    record InRegion(String region) implements Condition {
        public boolean test(PlayerFacts f) { return region.equals(f.region()); }
    }

    record Hours(int from, int to) implements Condition {
        public Hours {
            DomainException.require(from >= 0 && from < 24 && to >= 0 && to <= 24, "hidden.bad_hours", "시간 범위가 잘못되었습니다");
        }

        public boolean test(PlayerFacts f) {
            int h = f.hour();
            return from <= to ? h >= from && h < to : h >= from || h < to;
        }
    }

    record Discovered(String kind, String ref) implements Condition {
        public boolean test(PlayerFacts f) { return f.discovered(kind, ref); }
    }

    record All(List<Condition> parts) implements Condition {
        public All { parts = List.copyOf(parts); }

        public boolean test(PlayerFacts f) { return parts.stream().allMatch(c -> c.test(f)); }
    }

    record Any(List<Condition> parts) implements Condition {
        public Any { parts = List.copyOf(parts); }

        public boolean test(PlayerFacts f) { return parts.stream().anyMatch(c -> c.test(f)); }
    }

    /** 이 조건이 기대는 행동 기록 키들 (카운터가 바뀔 때 다시 판정할 규칙만 고르는 데 씀) */
    static void counters(Condition c, java.util.Set<String> out) {
        switch (c) {
            case Counter k -> out.add(k.key());
            case All a -> a.parts().forEach(p -> counters(p, out));
            case Any a -> a.parts().forEach(p -> counters(p, out));
            default -> { }
        }
    }
}
