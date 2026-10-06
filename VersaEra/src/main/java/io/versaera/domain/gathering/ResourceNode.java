package io.versaera.domain.gathering;

import io.versaera.domain.common.DomainException;
import io.versaera.domain.item.Quality;

import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * 채집 · 채광 · 벌목 · 낚시 자원 (content/resources.yml). blocks 는 이 자원으로 취급하는 Minecraft 블록 이름.
 * 지역(region tags)마다 풍부도가 달라 교역이 생긴다.
 */
public record ResourceNode(String id, String name, String discipline, int minLevel, int actionLevel, Set<String> blocks, String yield,
                           int baseAmount, int bonusAmount, Set<String> richIn, int respawnSeconds, String tool, long xp, String source) {
    public record Gather(int amount, int quality, long xp) {}

    public ResourceNode {
        DomainException.require(baseAmount >= 1 && bonusAmount >= 0, "resource.bad_amount", "양이 잘못되었습니다: " + id);
        DomainException.require(minLevel >= 1 && respawnSeconds >= 0, "resource.bad_numbers", "숫자가 잘못되었습니다: " + id);
        blocks = Set.copyOf(blocks);
        richIn = Set.copyOf(richIn == null ? Set.of() : richIn);
    }

    /**
     * @param level     그 분야 숙련 레벨
     * @param regionTags 지금 지역 태그 (richIn 과 겹치면 풍부한 산지)
     */
    public Gather gather(int level, Set<String> regionTags, RandomGenerator rng) {
        DomainException.require(level >= minLevel, "gather.low_level", "숙련이 부족합니다");
        boolean rich = regionTags.stream().anyMatch(richIn::contains);
        double skill = Math.min(1.0, Math.max(0, (level - minLevel) / 20.0));
        int amount = baseAmount + (int) Math.floor(bonusAmount * skill) + (rich ? 1 : 0);
        int quality = Quality.clamp(250 + level * 18 + (rich ? 120 : 0) + rng.nextInt(-30, 31));
        long xp = io.versaera.domain.skill.Mastery.gain(this.xp, actionLevel, level, 1.0);
        return new Gather(amount, quality, xp);
    }
}
