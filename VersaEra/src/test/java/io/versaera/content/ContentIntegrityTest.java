package io.versaera.content;

import io.versaera.domain.skill.ActionStat;
import io.versaera.domain.skill.Mastery;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ContentIntegrityTest {
    private final ContentBundle c = ContentBundle.fromClasspath(getClass().getClassLoader());

    @Test
    void everyReferenceResolves() {
        Set<String> items = new HashSet<>(), disc = new HashSet<>();
        c.items().forEach(i -> items.add(i.id()));
        c.disciplines().forEach(d -> disc.add(d.id()));
        c.recipes().forEach(r -> {
            assertTrue(items.contains(r.output()), r.id());
            assertTrue(disc.contains(r.discipline()), r.id());
            r.slots().forEach(s -> {
                if (s.accepts().startsWith("type:")) assertTrue(items.contains(s.accepts().substring(5)), r.id() + " " + s.accepts());
                else assertTrue(c.items().stream().anyMatch(i -> i.hasTag(s.accepts().substring(4))), "태그를 가진 재료가 없음: " + r.id() + " " + s.accepts());
            });
        });
        c.resources().forEach(r -> {
            assertTrue(items.contains(r.yield()), r.id());
            assertTrue(disc.contains(r.discipline()), r.id());
        });
        c.npcs().forEach(n -> assertTrue(c.regions().stream().anyMatch(r -> r.id().equals(n.region())), n.id()));
    }

    @Test
    void productionIsNotOnlySculpting() {
        long sculpt = c.recipes().stream().filter(r -> r.discipline().equals("sculpting")).count();
        long other = c.recipes().stream().filter(r -> !r.discipline().equals("sculpting")).count();
        assertTrue(other >= sculpt * 3, "조각 레시피가 전체를 지배하지 않게");
        assertTrue(c.recipes().stream().map(r -> r.discipline()).distinct().count() >= 6);
    }

    @Test
    void masteryCurveIsLongButFinite() {
        long total = Mastery.cumulative(Mastery.MAX_LEVEL);
        assertTrue(total > 200_000 && total < 400_000, "마스터까지: " + total);
        for (int lv = 1; lv < Mastery.MAX_LEVEL; lv++) assertEquals(lv + 1, Mastery.levelOf(Mastery.cumulative(lv + 1)));
        assertEquals("초급 1", Mastery.label(1));
        assertEquals("중급 1", Mastery.label(11));
        assertEquals("고급 10", Mastery.label(30));
        assertEquals("마스터", Mastery.label(31));
    }

    @Test
    void actionStatsGetHarderEachPoint() {
        ActionStat s = new ActionStat("x", "x", "k", 10, 0, "", "ORIGINAL");
        assertEquals(0, s.points(9));
        assertEquals(1, s.points(10));
        assertEquals(1, s.points(29));
        assertEquals(2, s.points(30));
        assertEquals(3, s.points(60));
        assertEquals(30, s.remaining(30));
    }

    @Test
    void malformedContentFailsLoudly() {
        assertThrows(ContentLoader.ContentException.class, () -> ContentLoader.regions(ContentLoader.parse(
                "regions: {bad: {name: X, source: CANON, danger: 1, min: [0,0,0], max: [1,1,1], purpose: p}}", "t"), "t"),
                "원작 지명에 changed 가 없으면 실패");
        assertThrows(Exception.class, () -> ContentLoader.parse("items: !!javax.script.ScriptEngineManager []", "t"), "안전하지 않은 YAML 태그 거부");
        assertThrows(Exception.class, () -> ContentLoader.parse("a: 1\na: 2", "t"), "중복 키 거부");
    }
}
