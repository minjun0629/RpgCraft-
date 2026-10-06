package io.versaera.domain.crafting;

import io.versaera.domain.common.DomainException;
import io.versaera.domain.item.Quality;
import io.versaera.domain.skill.Mastery;

import java.util.*;
import java.util.random.RandomGenerator;

/**
 * 재료를 레시피 칸에 맞추고 결과 품질을 계산한다 (순수 계산 — 아이템을 실제로 소모 · 생성하지 않음).
 * 품질 = 숙련 40% + 재료(칸 비중) 35% + 도구 15% + 관련 스탯 10% + 선택 칸 보너스 ± 작은 흔들림(±40).
 * 희귀 결과를 낮은 확률에 걸지 않는다 — 좋은 결과는 숙련 · 재료 · 도구로 만든다.
 */
public final class CraftPlan {
    public record Assignment(MaterialSlot slot, MaterialInput input) {}

    public record Outcome(int quality, long xp, List<Assignment> used) {}

    private CraftPlan() {
    }

    /** 재료를 칸에 배정 (칸 순서대로, 각 칸에 품질이 가장 높은 맞는 재료). 필수 칸을 못 채우면 예외. */
    public static List<Assignment> assign(Recipe r, List<MaterialInput> inputs) {
        List<MaterialInput> pool = new ArrayList<>(inputs);
        pool.sort(Comparator.comparingInt(MaterialInput::quality).reversed());
        List<Assignment> out = new ArrayList<>();
        for (MaterialSlot s : r.slots()) {
            MaterialInput pick = null;
            for (MaterialInput in : pool) if (s.matches(in) && in.count() >= s.count()) { pick = in; break; }
            if (pick == null) {
                if (s.optional()) continue;
                throw DomainException.of("craft.missing_material", "재료가 모자랍니다: " + s.role());
            }
            pool.remove(pick);
            out.add(new Assignment(s, pick));
        }
        return out;
    }

    /**
     * @param level       제작자의 그 분야 숙련 레벨
     * @param toolQuality 도구 품질 (도구가 필요 없으면 -1)
     * @param statPoints  관련 행동 스탯 포인트 (예: 조각 → 예술)
     */
    public static Outcome evaluate(Recipe r, List<MaterialInput> inputs, int level, int toolQuality, int statPoints, RandomGenerator rng) {
        DomainException.require(level >= r.minLevel(), "craft.low_level", "숙련이 부족합니다: " + Mastery.label(r.minLevel()) + " 필요");
        DomainException.require(r.tool() == null || toolQuality >= 0, "craft.no_tool", "도구가 필요합니다: " + r.tool());
        List<Assignment> used = assign(r, inputs);
        double wsum = 0, msum = 0;
        int bonus = 0;
        for (Assignment a : used) {
            wsum += a.slot().weight();
            msum += a.slot().weight() * a.input().quality();
            if (a.slot().optional()) bonus += a.slot().bonus();
        }
        double material = wsum > 0 ? msum / wsum : 500;
        double mastery = Math.min(1000, level / (double) Mastery.MAX_LEVEL * 1000);
        double tool = r.tool() == null ? 500 : toolQuality;
        double stat = Math.min(1000, statPoints * 20.0);
        double q = 0.40 * mastery + 0.35 * material + 0.15 * tool + 0.10 * stat + bonus;
        if (level < r.actionLevel()) q -= (r.actionLevel() - level) * 25;   // 아직 어려운 레시피
        q += rng.nextInt(-40, 41);
        long xp = Mastery.gain(r.xp(), r.actionLevel(), level, 1.0);
        return new Outcome(Quality.clamp(q), xp, List.copyOf(used));
    }
}
