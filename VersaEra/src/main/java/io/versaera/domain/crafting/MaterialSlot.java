package io.versaera.domain.crafting;

import io.versaera.domain.common.DomainException;

/**
 * 레시피의 재료 칸 하나. accepts 는 "type:iron_ingot" (정확한 종류) 또는 "tag:gem" (태그) 형식.
 *
 * @param weight   이 칸 재료 품질이 결과 품질에 미치는 비중
 * @param optional 비워도 되는 칸 (채우면 품질 보너스 bonus)
 */
public record MaterialSlot(String role, String accepts, int count, double weight, boolean optional, int bonus) {
    public MaterialSlot {
        DomainException.require(role != null && !role.isBlank(), "recipe.bad_slot", "재료 칸 이름이 비었습니다");
        DomainException.require(accepts != null && (accepts.startsWith("type:") || accepts.startsWith("tag:")), "recipe.bad_slot",
                "accepts 는 type: 또는 tag: 로 시작해야 합니다: " + accepts);
        DomainException.require(count >= 1 && count <= 64, "recipe.bad_slot", "재료 수는 1 ~ 64: " + count);
        DomainException.require(weight >= 0, "recipe.bad_slot", "weight 는 음수일 수 없습니다");
    }

    public boolean matches(MaterialInput in) {
        if (accepts.startsWith("type:")) return accepts.substring(5).equals(in.typeId());
        return in.tags().contains(accepts.substring(4));
    }
}
