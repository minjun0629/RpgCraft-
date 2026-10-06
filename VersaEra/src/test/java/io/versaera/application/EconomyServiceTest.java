package io.versaera.application;

import io.versaera.TestWorld;
import io.versaera.domain.common.DomainException;
import io.versaera.domain.common.Money;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EconomyServiceTest {
    @Test
    void depositWithdrawTransfer() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String a = TestWorld.player(), b = TestWorld.player();
            assertTrue(w.s.economy.deposit(a, 1000, "reward", "r1"));
            assertTrue(w.s.economy.transfer(a, b, 300, "gift", "t1"));
            assertTrue(w.s.economy.withdraw(b, 100, "fee", "f1"));
            assertEquals(700, w.s.economy.balance(a));
            assertEquals(200, w.s.economy.balance(b));
            assertEquals(1, w.s.audit.recent("MONEY_TRANSFERRED", 10).size());
        }
    }

    @Test
    void sameIdempotencyKeyIsProcessedOnce() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String a = TestWorld.player();
            assertTrue(w.s.economy.deposit(a, 500, "quest", "quest:1:" + a));
            assertFalse(w.s.economy.deposit(a, 500, "quest", "quest:1:" + a), "중복 보상 지급 금지");
            assertEquals(500, w.s.economy.balance(a));
        }
    }

    @Test
    void insufficientFundsChangesNothing() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String a = TestWorld.player(), b = TestWorld.player();
            w.s.economy.deposit(a, 100, "x", null);
            DomainException e = assertThrows(DomainException.class, () -> w.s.economy.transfer(a, b, 101, "x", "k"));
            assertEquals("money.insufficient", e.code());
            assertEquals(100, w.s.economy.balance(a));
            assertEquals(0, w.s.economy.balance(b));
            assertTrue(w.s.economy.transfer(a, b, 100, "x", "k"), "실패한 요청의 키는 기록되지 않아 다시 쓸 수 있어야 함");
        }
    }

    @Test
    void rejectsZeroNegativeOverflowAndSelf() throws Exception {
        try (TestWorld w = new TestWorld()) {
            String a = TestWorld.player();
            assertThrows(DomainException.class, () -> w.s.economy.deposit(a, 0, "x", null));
            assertThrows(DomainException.class, () -> w.s.economy.deposit(a, -5, "x", null));
            assertThrows(DomainException.class, () -> w.s.economy.deposit(a, Money.MAX_TRANSFER + 1, "x", null));
            assertThrows(DomainException.class, () -> w.s.economy.transfer(a, a, 1, "x", null));
            assertThrows(DomainException.class, () -> w.s.economy.withdraw(a, Long.MIN_VALUE, "x", null));
            assertEquals(0, w.s.economy.balance(a));
        }
    }
}
