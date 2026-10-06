package io.versaera.domain.hidden;

import io.versaera.domain.common.DomainException;

import java.util.Map;
import java.util.Set;

/**
 * 히든 콘텐츠 하나 (ORIGINAL — 원작의 히든 조건은 쓰지 않는다).
 *
 * @param rumor  누군가 처음 발견하면 서버에 퍼지는 소문 (조건 전체가 아니라 실마리 한 줄)
 * @param reward 보상 종류 → 값 (예: "recipe" → "art.windchime", "title" → "바람을 읽는 자", "stat" → "insight")
 */
public record HiddenRule(String id, String title, Condition when, String rumor, Map<String, String> reward) {
    public HiddenRule {
        DomainException.require(id != null && id.matches("[a-z0-9_.]+"), "hidden.bad_id", "히든 id 형식이 잘못되었습니다: " + id);
        DomainException.require(when != null, "hidden.no_condition", "조건이 없습니다: " + id);
        reward = Map.copyOf(reward == null ? Map.of() : reward);
    }

    public Set<String> counterKeys() {
        Set<String> s = new java.util.HashSet<>();
        Condition.counters(when, s);
        return s;
    }
}
