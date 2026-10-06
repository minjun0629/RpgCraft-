package io.versaera.domain.item;

import io.versaera.domain.common.DomainException;
import io.versaera.domain.skill.Mastery;

/**
 * 수리 규칙 (CANON 개념: 파괴된 물건은 못 고침 · 지나친 수리는 최대 내구도를 깎음 · 중급 이상은 최대 내구도를 되살림).
 * 수치는 ORIGINAL.
 */
public final class Repair {
    public record Result(int restored, int maxBefore, int maxAfter) {}

    private Repair() {
    }

    /**
     * @param baseMax   그 아이템 종류 · 품질 기준 원래 최대 내구도
     * @param level     수리 숙련 레벨 (Mastery 기준 1 ~ 31)
     */
    public static Result apply(ItemInstance it, int baseMax, int level) {
        DomainException.require(!it.ruined(), "repair.ruined", "완전히 망가진 물건은 고칠 수 없습니다");
        DomainException.require(it.custody().kind() != Custody.Kind.DESTROYED, "repair.destroyed", "사라진 아이템입니다");
        int maxBefore = it.maxDurability();
        int missing = maxBefore - it.durability();
        int maxAfter = maxBefore;
        if (Mastery.tierOf(level) >= Mastery.TIER_INTERMEDIATE) {
            // 중급 이상: 잃은 최대 내구도의 일부를 되살림 (고급 · 마스터일수록 많이)
            double share = Mastery.tierOf(level) == Mastery.TIER_INTERMEDIATE ? 0.25 : Mastery.tierOf(level) == Mastery.TIER_ADVANCED ? 0.5 : 0.8;
            maxAfter = Math.min(baseMax, maxBefore + (int) Math.ceil((baseMax - maxBefore) * share));
        } else if (missing > maxBefore / 2) {
            // 초급이 반 넘게 닳은 물건을 고치면 최대 내구도가 조금 줄어든다
            maxAfter = Math.max(1, maxBefore - Math.max(1, maxBefore / 50));
        }
        it.setDurability(maxAfter, maxAfter);
        return new Result(maxAfter - (maxBefore - missing), maxBefore, maxAfter);
    }
}
