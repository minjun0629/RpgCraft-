package io.versaera.application;

import io.versaera.TestWorld;
import io.versaera.domain.common.DomainException;
import io.versaera.domain.item.Custody;
import io.versaera.domain.item.ItemInstance;
import io.versaera.domain.trade.TradeSession;
import io.versaera.persistence.DbExecutor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class TradeServiceTest {
    private static ItemInstance owned(TestWorld w, String type, String owner) {
        ItemInstance it = w.s.items.create(type, 500, owner, "o", "test", Map.of(), owner, null);
        assertTrue(w.s.items.confirmDelivered(it.id(), owner));
        return it;
    }

    @Test
    void successfulTradeMovesItemsAndMoneyAtomically() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String a = TestWorld.player(), b = TestWorld.player();
            ItemInstance sword = owned(w, "iron_longsword", a);
            w.s.economy.deposit(b, 1000, "x", null);
            TradeSession t = w.s.trades.open(a, b);
            w.s.trades.offerItem(t.id(), a, sword.id());
            assertEquals(Custody.escrow(t.id()), w.s.items.find(sword.id()).orElseThrow().custody(), "올리는 즉시 보관(ESCROW)");
            w.s.trades.setMoney(t.id(), b, 400);
            w.s.trades.lock(t.id(), a);
            w.s.trades.lock(t.id(), b);
            assertFalse(w.s.trades.confirm(t.id(), a, t.offerVersion()));
            assertTrue(w.s.trades.confirm(t.id(), b, t.offerVersion()));
            assertEquals(Custody.delivery(b), w.s.items.find(sword.id()).orElseThrow().custody());
            assertEquals(400, w.s.economy.balance(a));
            assertEquals(600, w.s.economy.balance(b));
            assertTrue(w.s.trades.of(a).isEmpty());
            assertEquals(1, w.s.audit.recent("TRADE_COMPLETED", 5).size());
            assertThrows(DomainException.class, () -> w.s.trades.confirm(t.id(), a, t.offerVersion()), "끝난 거래 재확정 불가");
        }
    }

    @Test
    void changingOfferResetsConfirmationsAndStaleConfirmFails() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String a = TestWorld.player(), b = TestWorld.player();
            ItemInstance s1 = owned(w, "iron_longsword", a), s2 = owned(w, "iron_dagger", a);
            TradeSession t = w.s.trades.open(a, b);
            w.s.trades.offerItem(t.id(), a, s1.id());
            w.s.trades.lock(t.id(), a);
            w.s.trades.lock(t.id(), b);
            int seen = t.offerVersion();
            w.s.trades.confirm(t.id(), b, seen);
            w.s.trades.offerItem(t.id(), a, s2.id());   // 확인 뒤 몰래 바꾸기
            assertFalse(t.locked(b) || t.confirmed(b), "제안이 바뀌면 상대의 확인이 풀린다");
            DomainException e = assertThrows(DomainException.class, () -> {
                w.s.trades.lock(t.id(), a);
                w.s.trades.lock(t.id(), b);
                w.s.trades.confirm(t.id(), b, seen);
            });
            assertEquals("trade.stale", e.code());
        }
    }

    @Test
    void cannotOfferSomeoneElsesOrTheSameItemTwice() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String a = TestWorld.player(), b = TestWorld.player();
            ItemInstance mine = owned(w, "iron_longsword", a), theirs = owned(w, "iron_dagger", b);
            TradeSession t = w.s.trades.open(a, b);
            assertEquals("trade.not_owner", assertThrows(DomainException.class, () -> w.s.trades.offerItem(t.id(), a, theirs.id())).code());
            w.s.trades.offerItem(t.id(), a, mine.id());
            assertThrows(DomainException.class, () -> w.s.trades.offerItem(t.id(), a, mine.id()), "같은 아이템 두 번");
            assertEquals(Custody.player(b), w.s.items.find(theirs.id()).orElseThrow().custody(), "실패한 시도는 아무것도 바꾸지 않는다");
        }
    }

    @Test
    void cancelReturnsEscrowToOwnersDeliveryBox() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String a = TestWorld.player(), b = TestWorld.player();
            ItemInstance sword = owned(w, "iron_longsword", a);
            TradeSession t = w.s.trades.open(a, b);
            w.s.trades.offerItem(t.id(), a, sword.id());
            w.s.trades.cancelFor(b, "b logged out");
            assertEquals(Custody.delivery(a), w.s.items.find(sword.id()).orElseThrow().custody());
            assertTrue(w.s.trades.of(a).isEmpty());
            assertTrue(w.s.items.confirmDelivered(sword.id(), a));
        }
    }

    @Test
    void commitFailureRollsBackEverything() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String a = TestWorld.player(), b = TestWorld.player();
            ItemInstance sword = owned(w, "iron_longsword", a);
            w.s.economy.deposit(b, 500, "x", null);
            TradeSession t = w.s.trades.open(a, b);
            w.s.trades.offerItem(t.id(), a, sword.id());
            w.s.trades.setMoney(t.id(), b, 500);
            w.s.economy.withdraw(b, 400, "spent elsewhere", null);   // 확인 전에 돈을 다른 데 씀
            w.s.trades.lock(t.id(), a);
            w.s.trades.lock(t.id(), b);
            w.s.trades.confirm(t.id(), a, t.offerVersion());
            assertEquals("money.insufficient", assertThrows(DomainException.class, () -> w.s.trades.confirm(t.id(), b, t.offerVersion())).code());
            assertEquals(Custody.escrow(t.id()), w.s.items.find(sword.id()).orElseThrow().custody(), "아이템은 그대로 보관 중");
            assertEquals(0, w.s.economy.balance(a));
            assertEquals(100, w.s.economy.balance(b));
            assertEquals(TradeSession.State.OPEN, t.state());
        }
    }

    @Test
    void restartRecoveryReturnsEscrowedItems() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String a = TestWorld.player(), b = TestWorld.player();
            ItemInstance sword = owned(w, "iron_longsword", a);
            TradeSession t = w.s.trades.open(a, b);
            w.s.trades.offerItem(t.id(), a, sword.id());
            // 서버가 꺼졌다 켜짐: 메모리의 거래는 사라지고 DB 만 남음 → 새 서비스로 복구
            var restarted = new io.versaera.application.GameServices(w.db, w.s.content, w.now::get, java.time.ZoneId.of("UTC"), Logger.getLogger("t"));
            assertEquals(1, restarted.trades.recover());
            assertEquals(Custody.delivery(a), restarted.items.find(sword.id()).orElseThrow().custody());
            assertEquals(0, restarted.trades.recover(), "복구는 한 번만");
        }
    }

    @Test
    void concurrentConfirmClicksCommitExactlyOnce() throws Exception {
        try (TestWorld w = new TestWorld(); DbExecutor db = new DbExecutor(Logger.getLogger("t"))) {
            String a = TestWorld.player(), b = TestWorld.player();
            ItemInstance sword = owned(w, "iron_longsword", a);
            w.s.economy.deposit(b, 1000, "x", null);
            TradeSession t = w.s.trades.open(a, b);
            w.s.trades.offerItem(t.id(), a, sword.id());
            w.s.trades.setMoney(t.id(), b, 300);
            w.s.trades.lock(t.id(), a);
            w.s.trades.lock(t.id(), b);
            int v = t.offerVersion();
            AtomicInteger commits = new AtomicInteger();
            List<CompletableFuture<Boolean>> fs = new java.util.concurrent.CopyOnWriteArrayList<>();
            List<Thread> threads = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                String who = i % 2 == 0 ? a : b;
                Thread th = new Thread(() -> fs.add(db.submit("confirm", () -> {
                    boolean done = w.s.trades.confirm(t.id(), who, v);
                    if (done) commits.incrementAndGet();
                    return done;
                })));
                threads.add(th);
                th.start();
            }
            for (Thread th : threads) th.join();
            assertEquals(40, fs.size());
            for (CompletableFuture<Boolean> f : fs) {
                try {
                    f.join();
                } catch (java.util.concurrent.CompletionException ex) {
                    assertInstanceOf(DomainException.class, ex.getCause(), "끝난 거래에 대한 클릭은 규칙 위반으로만 거부되어야 함");
                }
            }
            assertEquals(1, commits.get(), "연타 · 동시 요청에도 거래는 한 번만");
            assertEquals(300, w.s.economy.balance(a));
            assertEquals(700, w.s.economy.balance(b));
        }
    }
}
