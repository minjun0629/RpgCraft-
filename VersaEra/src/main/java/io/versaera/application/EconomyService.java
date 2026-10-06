package io.versaera.application;

import io.versaera.application.port.AuditLog;
import io.versaera.application.port.TxRunner;
import io.versaera.application.port.WalletRepository;
import io.versaera.domain.common.DomainException;
import io.versaera.domain.common.GameClock;
import io.versaera.domain.common.Money;
import io.versaera.domain.event.EventBus;
import io.versaera.domain.event.GameEvents;

/**
 * 돈. 모든 변경은 트랜잭션 + 원장(ledger) + 감사 로그. idempotencyKey 가 같은 요청은 한 번만 처리한다.
 * 클라이언트 · 아이템 PDC 의 값을 금액으로 믿지 않는다 — 금액은 항상 서버 코드가 정한다.
 */
public final class EconomyService {
    private final TxRunner tx;
    private final WalletRepository wallets;
    private final AuditLog audit;
    private final EventBus bus;
    private final GameClock clock;

    public EconomyService(TxRunner tx, WalletRepository wallets, AuditLog audit, EventBus bus, GameClock clock) {
        this.tx = tx;
        this.wallets = wallets;
        this.audit = audit;
        this.bus = bus;
        this.clock = clock;
    }

    public long balance(String uuid) {
        return wallets.balance(uuid);
    }

    /** @return 처리했으면 true, 같은 key 로 이미 처리했으면 false */
    public boolean deposit(String uuid, long amount, String reason, String key) {
        Money.requirePositive(amount);
        AfterCommit after = new AfterCommit();
        boolean done = tx.inTx(() -> depositInTx(uuid, amount, reason, key, after));
        after.publish(bus);
        return done;
    }

    boolean depositInTx(String uuid, long amount, String reason, String key, AfterCommit after) {
        if (wallets.ledgerExists(key)) return false;
        long nb = Money.add(wallets.balance(uuid), amount);
        wallets.setBalance(uuid, nb);
        wallets.ledger(null, uuid, amount, reason, key, clock.nowMillis());
        audit.record("MONEY_ADDED", uuid, uuid, amount + " (" + reason + ")", key);
        after.add(new GameEvents.MoneyChanged(uuid, amount, nb, reason));
        return true;
    }

    public boolean withdraw(String uuid, long amount, String reason, String key) {
        Money.requirePositive(amount);
        AfterCommit after = new AfterCommit();
        boolean done = tx.inTx(() -> {
            if (wallets.ledgerExists(key)) return false;
            long b = wallets.balance(uuid);
            DomainException.require(b >= amount, "money.insufficient", "돈이 부족합니다");
            wallets.setBalance(uuid, b - amount);
            wallets.ledger(uuid, null, amount, reason, key, clock.nowMillis());
            audit.record("MONEY_REMOVED", uuid, uuid, amount + " (" + reason + ")", key);
            after.add(new GameEvents.MoneyChanged(uuid, -amount, b - amount, reason));
            return true;
        });
        after.publish(bus);
        return done;
    }

    public boolean transfer(String from, String to, long amount, String reason, String key) {
        Money.requirePositive(amount);
        DomainException.require(!from.equals(to), "money.self", "자기 자신에게는 보낼 수 없습니다");
        AfterCommit after = new AfterCommit();
        boolean done = tx.inTx(() -> transferInTx(from, to, amount, reason, key, after));
        after.publish(bus);
        return done;
    }

    boolean transferInTx(String from, String to, long amount, String reason, String key, AfterCommit after) {
        if (wallets.ledgerExists(key)) return false;
        long fb = wallets.balance(from);
        DomainException.require(fb >= amount, "money.insufficient", "돈이 부족합니다");
        long tb = Money.add(wallets.balance(to), amount);
        wallets.setBalance(from, fb - amount);
        wallets.setBalance(to, tb);
        wallets.ledger(from, to, amount, reason, key, clock.nowMillis());
        audit.record("MONEY_TRANSFERRED", from, to, amount + " (" + reason + ")", key);
        after.add(new GameEvents.MoneyChanged(from, -amount, fb - amount, reason));
        after.add(new GameEvents.MoneyChanged(to, amount, tb, reason));
        return true;
    }
}
