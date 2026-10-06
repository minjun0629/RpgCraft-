package io.versaera.domain.boss;

import io.versaera.domain.common.DomainException;

import java.util.List;
import java.util.Map;

/**
 * 거대 보스 정의 (content/bosses.yml). 크기(scale)는 모델 · 판정 · 공격 범위 · 전투 공간에 모두 곱해진다.
 *
 * @param arenaRadius 전투 공간 반지름 (블록) — 이 밖으로 끌려가면 보스가 돌아간다
 * @param weakArc     등 뒤 약점 각도 (도). 이 각도 안에서 때리면 1.5배
 * @param enrageMs    이 시간이 지나면 광폭화 (패턴 대기 절반)
 */
public record BossDefinition(String id, String name, double scale, double hitRadius, double maxHp, double arenaRadius, double weakArc,
                             long enrageMs, List<Phase> phases, Map<String, Pattern> patterns, String model, String source) {
    /** hpBelow: 체력 비율이 이 값 이하가 되면 이 페이즈 (1.0 = 처음) */
    public record Phase(double hpBelow, List<String> patterns, String announce) {
        public Phase { patterns = List.copyOf(patterns); }
    }

    /**
     * @param telegraphMs 예고 시간 — 바닥에 범위를 보여 주고 이 시간 뒤에 판정 (피할 시간)
     */
    public record Pattern(String id, Shape shape, double radius, double inner, double widthOrAngle, double height, long telegraphMs,
                          double damage, long cooldownMs, String effect) {
        public Pattern {
            DomainException.require(radius > 0 && telegraphMs >= 300 && cooldownMs >= 0 && damage >= 0, "boss.bad_pattern",
                    "패턴 값이 잘못되었습니다 (예고는 0.3초 이상): " + id);
        }
    }

    public BossDefinition {
        DomainException.require(scale >= 1 && maxHp > 0 && hitRadius > 0, "boss.bad_numbers", "보스 수치가 잘못되었습니다: " + id);
        DomainException.require(phases != null && !phases.isEmpty() && phases.get(0).hpBelow() >= 1.0, "boss.bad_phases",
                "첫 페이즈는 hpBelow 1.0 이어야 합니다: " + id);
        for (Phase p : phases) for (String pid : p.patterns())
            DomainException.require(patterns.containsKey(pid), "boss.unknown_pattern", "없는 패턴: " + pid + " (" + id + ")");
        phases = List.copyOf(phases);
        patterns = Map.copyOf(patterns);
    }
}
