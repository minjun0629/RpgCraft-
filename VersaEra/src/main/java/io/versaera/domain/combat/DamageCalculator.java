package io.versaera.domain.combat;

import io.versaera.domain.common.DomainException;
import io.versaera.domain.item.Quality;

import java.util.random.RandomGenerator;

/**
 * 피해 계산 (ORIGINAL 수치). 무기 공격력 · 품질 · 무기 숙련 · 상대 방어 · 막기 · 치명타 · 약점을 반영한다.
 * 방어 감소 = 100 / (100 + 방어) — 방어가 아무리 높아도 피해가 0 이 되지 않는다.
 */
public final class DamageCalculator {
    public record Attack(double weaponAttack, int weaponQuality, int weaponMastery, double critChance, double critMultiplier,
                         boolean weakPoint) {}

    public record Defense(double armor, boolean blocking, boolean blockFacing) {}

    public record Hit(double damage, boolean crit) {}

    private DamageCalculator() {
    }

    public static Hit compute(Attack a, Defense d, RandomGenerator rng) {
        DomainException.require(a.weaponAttack() >= 0 && d.armor() >= 0, "combat.bad_input", "공격력 · 방어력은 음수일 수 없습니다");
        double dmg = a.weaponAttack() * Quality.statMultiplier(a.weaponQuality()) * (1 + Math.min(31, Math.max(0, a.weaponMastery())) * 0.015);
        boolean crit = a.critChance() > 0 && rng.nextDouble() < Math.min(0.75, a.critChance());
        if (crit) dmg *= Math.max(1.0, a.critMultiplier());
        if (a.weakPoint()) dmg *= 1.5;
        dmg *= 100.0 / (100.0 + d.armor());
        if (d.blocking() && d.blockFacing()) dmg *= 0.4;
        return new Hit(Math.max(1, dmg), crit);
    }
}
