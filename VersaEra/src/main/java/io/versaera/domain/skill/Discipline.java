package io.versaera.domain.skill;

import io.versaera.domain.common.DomainException;

/**
 * 숙련 분야 하나 (전투 무기 · 채집 · 채광 · 낚시 · 요리 · 대장 · 재봉 · 연금 · 조각 · 수리 · 손재주 …).
 * content/disciplines.yml 에서 읽는다.
 *
 * @param hand   손을 쓰는 분야 — 손재주 숙련이 숙련 속도를 높여 준다 (CANON 개념)
 * @param source CANON / SOURCE-BASED / ORIGINAL / RESEARCH_REQUIRED
 */
public record Discipline(String id, String name, Category category, boolean hand, String source) {
    public enum Category { COMBAT, GATHERING, PRODUCTION, ART, SOCIAL, EXPLORATION, SUPPORT }

    public Discipline {
        DomainException.require(id != null && id.matches("[a-z_]+"), "discipline.bad_id", "숙련 id 형식이 잘못되었습니다: " + id);
    }
}
