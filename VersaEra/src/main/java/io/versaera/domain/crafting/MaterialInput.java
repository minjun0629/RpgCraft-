package io.versaera.domain.crafting;

import io.versaera.domain.common.DomainException;

import java.util.Set;

/** 제작에 넣은 재료 묶음 (서버가 인벤토리에서 확인한 값). quality 는 0 ~ 1000. */
public record MaterialInput(String typeId, Set<String> tags, int quality, int count) {
    public MaterialInput {
        DomainException.require(count > 0, "craft.bad_input", "재료 수가 잘못되었습니다: " + count);
        DomainException.require(quality >= 0 && quality <= 1000, "craft.bad_input", "재료 품질이 잘못되었습니다: " + quality);
        tags = Set.copyOf(tags == null ? Set.of() : tags);
    }
}
