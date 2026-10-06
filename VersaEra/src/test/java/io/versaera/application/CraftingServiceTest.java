package io.versaera.application;

import io.versaera.TestWorld;
import io.versaera.domain.common.DomainException;
import io.versaera.domain.crafting.MaterialInput;
import io.versaera.domain.item.ItemInstance;
import io.versaera.domain.skill.Mastery;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.*;

class CraftingServiceTest {
    private static MaterialInput in(String type, Set<String> tags, int q, int n) {
        return new MaterialInput(type, tags, q, n);
    }

    @Test
    void sculptureIsARealItemMadeFromSeveralMaterials() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String p = TestWorld.player();
            var r = w.s.crafting.craft(p, "Ari", "carve_figurine", List.of(
                    in("oak_timber", Set.of("wood"), 600, 2), in("iron_ingot", Set.of("metal", "mineral"), 500, 1),
                    in("raw_gem", Set.of("gem"), 700, 1), in("indigo_dye", Set.of("dye"), 400, 1)),
                    500, Map.of("subject", "늑대", "title", "새벽의 늑대"), new SplittableRandom(1), "req");
            ItemInstance art = w.s.items.find(r.itemId()).orElseThrow();
            assertEquals("figurine", art.typeId());
            assertEquals(p, art.creatorUuid());
            assertEquals("Ari", art.creatorName());
            assertEquals("새벽의 늑대", art.props().get("title"));
            assertTrue(art.props().get("materials").contains("accent:raw_gem@700"), "재료 조합이 아이템에 기록됨: " + art.props());
            assertEquals(ItemService.Verdict.AWAITING_DELIVERY, w.s.items.validate(art.id(), p));
            assertTrue(w.s.growth.xp(p, "sculpting") > 0);
            assertTrue(w.s.growth.counter(p, "art.experience") > 0, "예술 활동 기록");
        }
    }

    @Test
    void optionalAccentMaterialsRaiseQualityWithoutRandomLuck() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String p = TestWorld.player();
            var base = List.of(in("oak_timber", Set.of("wood"), 500, 2), in("iron_ingot", Set.of("metal", "mineral"), 500, 1));
            var plain = w.s.crafting.craft(p, "a", "carve_figurine", base, 500, null, new SplittableRandom(7), null);
            var rich = w.s.crafting.craft(p, "a", "carve_figurine", List.of(base.get(0), base.get(1),
                    in("raw_gem", Set.of("gem"), 500, 1), in("indigo_dye", Set.of("dye"), 500, 1)), 500, null, new SplittableRandom(7), null);
            assertTrue(rich.quality() > plain.quality(), "같은 운(시드)에서 보석 · 염료를 더하면 품질이 오른다");
        }
    }

    @Test
    void missingMaterialsAreRefundedToDeliveryBox() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String p = TestWorld.player();
            DomainException e = assertThrows(DomainException.class, () -> w.s.crafting.craft(p, "a", "carve_figurine",
                    List.of(in("oak_timber", Set.of("wood"), 500, 2)), 500, null, new SplittableRandom(1), null));
            assertEquals("craft.missing_material", e.code());
            assertEquals(1, w.s.items.pendingBulk(p).size(), "이미 뺀 재료는 배달함으로 돌아온다");
            assertEquals(2, w.s.items.pendingBulk(p).get(0).amount());
        }
    }

    @Test
    void levelToolAndDiscoveryGatesApply() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String p = TestWorld.player();
            assertEquals("craft.low_level", assertThrows(DomainException.class, () -> w.s.crafting.craft(p, "a", "carve_statuette",
                    List.of(in("marble_block", Set.of("stone", "marble"), 500, 4), in("iron_ingot", Set.of("metal"), 500, 2)), 500, null,
                    new SplittableRandom(1), null)).code());
            assertEquals("craft.no_tool", assertThrows(DomainException.class, () -> w.s.crafting.craft(p, "a", "carve_figurine",
                    List.of(in("oak_timber", Set.of("wood"), 500, 2), in("iron_ingot", Set.of("mineral"), 500, 1)), -1, null,
                    new SplittableRandom(1), null)).code());
            assertEquals("craft.unknown_recipe", assertThrows(DomainException.class, () -> w.s.crafting.craft(p, "a", "craft_wind_chime",
                    List.of(in("sky_metal", Set.of("metal"), 500, 3), in("oak_timber", Set.of("wood"), 500, 1)), 500, null,
                    new SplittableRandom(1), null)).code(), "발견하지 못한 제작법");
        }
    }

    @Test
    void bulkOutputsGoToDeliveryNotUniqueItems() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String p = TestWorld.player();
            var r = w.s.crafting.craft(p, "a", "grill_fish", List.of(in("salmon", Set.of("fish"), 400, 1)), -1, null, new SplittableRandom(3), null);
            assertNull(r.itemId());
            assertEquals("grilled_fish", w.s.items.pendingBulk(p).get(0).typeId());
        }
    }

    @Test
    void unknownExtraPropsAreRejected() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String p = TestWorld.player();
            assertEquals("craft.bad_prop", assertThrows(DomainException.class, () -> w.s.crafting.craft(p, "a", "carve_figurine",
                    List.of(in("oak_timber", Set.of("wood"), 500, 2), in("iron_ingot", Set.of("mineral"), 500, 1)), 500,
                    Map.of("creator", "someone-else"), new SplittableRandom(1), null)).code(), "제작자 위조 같은 속성은 받지 않음");
        }
    }

    @Test
    void repeatingTrivialRecipesStopsGivingMuchXp() {
        long early = Mastery.gain(10, 1, 1, 1.0);
        long late = Mastery.gain(10, 1, 25, 1.0);
        assertTrue(late < early / 5, "너무 쉬운 일 반복은 의미 없는 노가다가 되지 않게 경험치가 크게 줄어든다");
    }
}
