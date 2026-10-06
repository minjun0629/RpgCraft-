package io.versaera.domain;

import io.versaera.content.ContentBundle;
import io.versaera.domain.boss.BossDefinition;
import io.versaera.domain.boss.BossFight;
import io.versaera.domain.boss.Shape;
import io.versaera.domain.boss.Vec;
import io.versaera.domain.combat.DamageCalculator;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class BossAndCombatTest {
    private static BossDefinition colossus() {
        return ContentBundle.fromClasspath(BossAndCombatTest.class.getClassLoader()).bosses().stream()
                .filter(b -> b.id().equals("fallen_colossus")).findFirst().orElseThrow();
    }

    @Test
    void shapesUseMathNotEntities() {
        Vec o = new Vec(0, 64, 0);
        assertTrue(Shape.CIRCLE.hits(o, 0, new Vec(3, 64, 0), 4, 0, 0, 2));
        assertFalse(Shape.CIRCLE.hits(o, 0, new Vec(5, 64, 0), 4, 0, 0, 2));
        assertTrue(Shape.CONE.hits(o, 0, new Vec(0, 64, 5), 8, 0, 90, 2), "yaw 0 = +Z 정면");
        assertFalse(Shape.CONE.hits(o, 0, new Vec(0, 64, -5), 8, 0, 90, 2), "등 뒤는 부채꼴 밖");
        assertTrue(Shape.RING.hits(o, 0, new Vec(0, 64, 6), 8, 4, 0, 2));
        assertFalse(Shape.RING.hits(o, 0, new Vec(0, 64, 2), 8, 4, 0, 2), "링 안쪽은 안전지대");
        assertTrue(Shape.LINE.hits(o, 90, new Vec(-10, 64, 0.4), 20, 0, 1, 2), "yaw 90 = -X 방향 직선");
        assertFalse(Shape.CIRCLE.hits(o, 0, new Vec(1, 80, 0), 4, 0, 0, 2), "높이 밖");
    }

    @Test
    void bossIsHugeAndTelegraphsBeforeHitting() {
        BossDefinition d = colossus();
        assertTrue(d.scale() >= 7, "보스는 크게");
        BossFight f = new BossFight(d, 0);
        UUID near = UUID.randomUUID(), far = UUID.randomUUID();
        Map<UUID, Vec> targets = Map.of(near, new Vec(0, 64, 5), far, new Vec(0, 64, 30));
        List<BossFight.Action> a1 = f.update(0, 1.0, new Vec(0, 64, 0), 0, targets);
        BossFight.Action.Telegraph tg = (BossFight.Action.Telegraph) a1.stream().filter(x -> x instanceof BossFight.Action.Telegraph).findFirst().orElseThrow();
        assertEquals("stomp", tg.pattern());
        assertTrue(f.update(500, 1.0, new Vec(0, 64, 0), 0, targets).stream().noneMatch(x -> x instanceof BossFight.Action.Resolve), "예고 중에는 피할 시간");
        List<BossFight.Action> a2 = f.update(tg.resolveAt(), 1.0, new Vec(0, 64, 0), 0, targets);
        BossFight.Action.Resolve r = (BossFight.Action.Resolve) a2.stream().filter(x -> x instanceof BossFight.Action.Resolve).findFirst().orElseThrow();
        assertEquals(List.of(near), r.hit(), "발 구르기 범위(1.4 × 7 = 9.8칸) 안만 맞음");
    }

    @Test
    void phasesAdvanceWithHpAndEnrageWithTime() {
        BossDefinition d = colossus();
        BossFight f = new BossFight(d, 0);
        Map<UUID, Vec> t = Map.of(UUID.randomUUID(), new Vec(0, 64, 3));
        assertTrue(f.update(0, 0.5, new Vec(0, 64, 0), 0, t).stream().anyMatch(x -> x instanceof BossFight.Action.PhaseChanged));
        assertEquals(1, f.phase());
        assertTrue(f.update(d.enrageMs(), 0.5, new Vec(0, 64, 0), 0, t).stream().anyMatch(x -> x instanceof BossFight.Action.Enraged));
        assertTrue(f.weakPoint(new Vec(0, 64, 0), 0, new Vec(0, 64, -5)), "등 뒤는 약점");
        assertFalse(f.weakPoint(new Vec(0, 64, 0), 0, new Vec(0, 64, 5)));
    }

    @Test
    void damageRespectsQualityArmorBlockAndNeverZero() {
        SplittableRandom rng = new SplittableRandom(1);
        double low = DamageCalculator.compute(new DamageCalculator.Attack(20, 0, 1, 0, 1.5, false), new DamageCalculator.Defense(0, false, false), rng).damage();
        double high = DamageCalculator.compute(new DamageCalculator.Attack(20, 1000, 1, 0, 1.5, false), new DamageCalculator.Defense(0, false, false), rng).damage();
        assertTrue(high > low);
        double armored = DamageCalculator.compute(new DamageCalculator.Attack(20, 500, 1, 0, 1.5, false), new DamageCalculator.Defense(100000, false, false), rng).damage();
        assertTrue(armored >= 1);
        double blocked = DamageCalculator.compute(new DamageCalculator.Attack(20, 500, 1, 0, 1.5, false), new DamageCalculator.Defense(0, true, true), rng).damage();
        double open = DamageCalculator.compute(new DamageCalculator.Attack(20, 500, 1, 0, 1.5, false), new DamageCalculator.Defense(0, false, false), rng).damage();
        assertEquals(open * 0.4, blocked, 1e-9);
    }
}
