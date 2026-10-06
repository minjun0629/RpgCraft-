package io.versaera.domain.crafting;

import io.versaera.domain.common.DomainException;

import java.util.List;

/**
 * 제작 레시피 (content/recipes.yml). 대장 · 재봉 · 요리 · 연금 · 조각 모두 같은 구조를 쓴다 — 조각만 특별 취급하지 않는다.
 *
 * @param minLevel      이 숙련 레벨 미만이면 만들 수 없음
 * @param actionLevel   권장 레벨 (경험치 · 품질 보정 기준)
 * @param tool          필요한 도구 태그 (null = 없음)
 * @param discovery     이 레시피를 알아야 만들 수 있음 (발견 콘텐츠). null = 처음부터 앎
 */
public record Recipe(String id, String name, String discipline, int minLevel, int actionLevel, String output, int outputCount,
                     List<MaterialSlot> slots, String tool, int timeTicks, long xp, String discovery, String source) {
    public Recipe {
        DomainException.require(id != null && id.matches("[a-z0-9_.]+"), "recipe.bad_id", "레시피 id 형식이 잘못되었습니다: " + id);
        DomainException.require(slots != null && !slots.isEmpty(), "recipe.no_slots", "재료 칸이 없습니다: " + id);
        DomainException.require(minLevel >= 1 && actionLevel >= 1, "recipe.bad_level", "레벨이 잘못되었습니다: " + id);
        DomainException.require(outputCount >= 1, "recipe.bad_output", "결과 수가 잘못되었습니다: " + id);
        DomainException.require(xp >= 0 && timeTicks >= 0, "recipe.bad_numbers", "숫자가 잘못되었습니다: " + id);
        slots = List.copyOf(slots);
    }
}
