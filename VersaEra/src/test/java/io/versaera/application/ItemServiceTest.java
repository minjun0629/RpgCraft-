package io.versaera.application;

import io.versaera.TestWorld;
import io.versaera.domain.common.DomainException;
import io.versaera.domain.item.Custody;
import io.versaera.domain.item.ItemInstance;
import io.versaera.domain.item.Repair;
import io.versaera.domain.skill.Mastery;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ItemServiceTest {
    @Test
    void newItemsWaitInDeliveryAndAreConfirmedExactlyOnce() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String p = TestWorld.player();
            ItemInstance it = w.s.items.create("iron_longsword", 700, p, "Smith", "test", Map.of(), p, "req-1");
            assertEquals(Custody.delivery(p), it.custody());
            assertEquals(ItemService.Verdict.AWAITING_DELIVERY, w.s.items.validate(it.id(), p));
            assertEquals(1, w.s.items.pendingDeliveries(p).size());
            assertTrue(w.s.items.confirmDelivered(it.id(), p));
            assertFalse(w.s.items.confirmDelivered(it.id(), p), "두 번 확정 = 두 번 지급이 되면 안 됨");
            assertEquals(ItemService.Verdict.OK, w.s.items.validate(it.id(), p));
            assertEquals(ItemService.Verdict.NOT_OWNER, w.s.items.validate(it.id(), TestWorld.player()));
            assertEquals(java.util.List.of("CREATED", "DELIVERED"), w.s.itemRepo.historyOf(it.id()));
        }
    }

    @Test
    void forgedOrUnknownIdsAreRejected() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String p = TestWorld.player();
            assertEquals(ItemService.Verdict.UNKNOWN, w.s.items.validate("not-a-uuid'; DROP TABLE item_instance;--", p));
            assertEquals(ItemService.Verdict.UNKNOWN, w.s.items.validate(null, p));
            assertEquals(ItemService.Verdict.UNKNOWN, w.s.items.validate(TestWorld.player(), p));
        }
    }

    @Test
    void qualityScalesMaxDurabilityAndBulkTypesCannotBeUnique() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String p = TestWorld.player();
            ItemInstance low = w.s.items.create("iron_longsword", 0, p, "a", "t", Map.of(), p, null);
            ItemInstance high = w.s.items.create("iron_longsword", 1000, p, "a", "t", Map.of(), p, null);
            assertTrue(high.maxDurability() > low.maxDurability());
            assertThrows(DomainException.class, () -> w.s.items.create("iron_ore", 500, p, "a", "t", Map.of(), p, null));
        }
    }

    @Test
    void destroyedItemsStayDestroyed() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String p = TestWorld.player();
            ItemInstance it = w.s.items.create("figurine", 500, p, "a", "t", Map.of(), p, null);
            w.s.items.confirmDelivered(it.id(), p);
            w.s.items.destroy(it.id(), p, "dropped in lava", null);
            assertEquals(ItemService.Verdict.DESTROYED, w.s.items.validate(it.id(), p));
            assertThrows(DomainException.class, () -> w.s.items.destroy(it.id(), p, "again", null));
            assertFalse(w.s.items.confirmDelivered(it.id(), p));
        }
    }

    @Test
    void repairFollowsDurabilityRules() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String p = TestWorld.player();
            ItemInstance it = w.s.items.create("iron_longsword", 500, p, "a", "t", Map.of(), p, null);
            w.s.items.confirmDelivered(it.id(), p);
            int base = it.maxDurability();
            w.s.items.wear(it.id(), p, base - 10, false);
            Repair.Result beginner = w.s.items.repair(it.id(), p, p, 1, null);
            assertTrue(beginner.maxAfter() < base, "초급이 반 넘게 닳은 물건을 고치면 최대 내구도가 준다");
            Repair.Result skilled = w.s.items.repair(it.id(), p, p, 15, null);
            assertTrue(skilled.maxAfter() > beginner.maxAfter(), "중급 이상은 최대 내구도를 되살린다");
            assertEquals(Mastery.TIER_INTERMEDIATE, Mastery.tierOf(15));
            assertThrows(DomainException.class, () -> w.s.items.repair(it.id(), TestWorld.player(), p, 15, null), "남의 물건은 고칠 수 없음");
        }
    }

    @Test
    void ruinedItemsCannotBeRepaired() {
        ItemInstance it = new ItemInstance(TestWorld.player(), "iron_longsword", 500, 0, 1, 1, null, null, "t", Map.of(), Custody.player("x"), 0, 0);
        it.wear(0, true);
        assertTrue(it.ruined());
        DomainException e = assertThrows(DomainException.class, () -> Repair.apply(it, 400, 31));
        assertEquals("repair.ruined", e.code());
    }

    @Test
    void bulkDeliveryIsTakenOnlyOnce() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String p = TestWorld.player();
            long id = w.s.items.deliverBulk(p, "iron_ore", 400, 5, "test");
            assertEquals(1, w.s.items.pendingBulk(p).size());
            assertTrue(w.s.items.takeBulk(id));
            assertFalse(w.s.items.takeBulk(id), "같은 배달을 두 번 받으면 복제");
            assertThrows(DomainException.class, () -> w.s.items.deliverBulk(p, "iron_ore", 400, -1, "x"));
            assertThrows(DomainException.class, () -> w.s.items.deliverBulk(p, "no_such_item", 400, 1, "x"));
        }
    }
}
