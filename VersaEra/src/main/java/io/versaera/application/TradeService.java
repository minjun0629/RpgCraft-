package io.versaera.application;

import io.versaera.application.port.*;
import io.versaera.domain.common.DomainException;
import io.versaera.domain.common.GameClock;
import io.versaera.domain.event.EventBus;
import io.versaera.domain.event.GameEvents;
import io.versaera.domain.item.Custody;
import io.versaera.domain.item.ItemInstance;
import io.versaera.domain.trade.TradeSession;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 1:1 거래. 올린 아이템은 즉시 ESCROW 로 옮기고(인벤토리에서 빠짐), 확정은 트랜잭션 하나로 처리한다.
 * 취소 · 접속 끊김 · 서버 재시작이면 ESCROW 아이템은 원래 주인의 배달함(DELIVERY)으로 돌아간다.
 * <p>모든 메서드는 DB 스레드 하나에서만 부른다 (동시 요청이 들어와도 순서대로 처리됨).</p>
 */
public final class TradeService {
    private final TxRunner tx;
    private final TradeRepository trades;
    private final ItemRepository items;
    private final ItemTypeRegistry types;
    private final WalletRepository wallets;
    private final EconomyService economy;
    private final AuditLog audit;
    private final EventBus bus;
    private final GameClock clock;
    private final Map<String, TradeSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> byPlayer = new ConcurrentHashMap<>();

    public TradeService(TxRunner tx, TradeRepository trades, ItemRepository items, ItemTypeRegistry types, WalletRepository wallets,
                        EconomyService economy, AuditLog audit, EventBus bus, GameClock clock) {
        this.tx = tx;
        this.trades = trades;
        this.items = items;
        this.types = types;
        this.wallets = wallets;
        this.economy = economy;
        this.audit = audit;
        this.bus = bus;
        this.clock = clock;
    }

    public Optional<TradeSession> of(String uuid) {
        String id = byPlayer.get(uuid);
        return id == null ? Optional.empty() : Optional.ofNullable(sessions.get(id));
    }

    private TradeSession session(String tradeId) {
        TradeSession s = sessions.get(tradeId);
        if (s == null) throw DomainException.of("trade.unknown", "없거나 끝난 거래입니다");
        return s;
    }

    public TradeSession open(String a, String b) {
        DomainException.require(!byPlayer.containsKey(a) && !byPlayer.containsKey(b), "trade.busy", "이미 다른 거래 중입니다");
        TradeSession s = new TradeSession(UUID.randomUUID().toString(), a, b, clock.nowMillis());
        tx.inTx(() -> {
            trades.open(s.id(), a, b, s.createdAt());
            return null;
        });
        sessions.put(s.id(), s);
        byPlayer.put(a, s.id());
        byPlayer.put(b, s.id());
        return s;
    }

    /** 아이템을 거래창에 올린다 → 인벤토리에서 빠지고 ESCROW. 플랫폼은 이 호출이 성공한 뒤에만 인벤토리에서 지운다. */
    public void offerItem(String tradeId, String party, String itemId) {
        TradeSession s = session(tradeId);
        synchronized (s) {
            tx.inTx(() -> {
                ItemInstance it = items.find(itemId).orElseThrow(() -> DomainException.of("item.unknown", "없는 아이템입니다"));
                DomainException.require(it.custody().ownedBy(party), "trade.not_owner", "내 아이템만 올릴 수 있습니다");
                DomainException.require(!types.get(it.typeId()).hasTag("bound"), "trade.bound", "거래할 수 없는 아이템입니다");
                it.custody(Custody.escrow(tradeId));
                items.update(it);
                trades.putOffer(tradeId, itemId, party);
                items.history(itemId, "ESCROW", party, tradeId, clock.nowMillis());
                s.addItem(party, itemId);   // 규칙 위반이면 여기서 예외 → 위 DB 변경도 모두 되돌림
                return null;
            });
        }
    }

    /** 올린 아이템을 내린다 → 주인의 배달함으로 (플랫폼이 인벤토리에 다시 넣음) */
    public void withdrawItem(String tradeId, String party, String itemId) {
        TradeSession s = session(tradeId);
        synchronized (s) {
            tx.inTx(() -> {
                DomainException.require(s.items(party).contains(itemId), "trade.no_such_item", "올리지 않은 아이템입니다");
                returnToOwner(tradeId, itemId, party);
                trades.removeOffer(itemId);
                s.removeItem(party, itemId);
                return null;
            });
        }
    }

    private void returnToOwner(String tradeId, String itemId, String owner) {
        ItemInstance it = items.find(itemId).orElseThrow();
        if (!it.custody().equals(Custody.escrow(tradeId))) return;   // 이미 처리됨
        it.custody(Custody.delivery(owner));
        items.update(it);
        items.history(itemId, "ESCROW_RETURNED", owner, tradeId, clock.nowMillis());
    }

    public void setMoney(String tradeId, String party, long amount) {
        TradeSession s = session(tradeId);
        synchronized (s) {
            DomainException.require(wallets.balance(party) >= amount, "money.insufficient", "가진 돈보다 많이 올릴 수 없습니다");
            s.setMoney(party, amount);
        }
    }

    public void lock(String tradeId, String party) {
        TradeSession s = session(tradeId);
        synchronized (s) {
            s.lock(party);
        }
    }

    /** @return 이 확인으로 거래가 성사되었으면 true */
    public boolean confirm(String tradeId, String party, int seenVersion) {
        TradeSession s = session(tradeId);
        synchronized (s) {
            s.confirm(party, seenVersion);
            if (!s.readyToCommit()) return false;
            commit(s);
            return true;
        }
    }

    private void commit(TradeSession s) {
        AfterCommit after = new AfterCommit();
        tx.inTx(() -> {
            DomainException.require("OPEN".equals(trades.state(s.id())), "trade.closed", "이미 끝난 거래입니다");
            for (String party : List.of(s.a(), s.b())) {
                String to = s.other(party);
                for (String itemId : s.items(party)) {
                    ItemInstance it = items.find(itemId).orElseThrow(() -> DomainException.of("trade.item_missing", "아이템이 사라졌습니다"));
                    DomainException.require(it.custody().equals(Custody.escrow(s.id())), "trade.item_moved", "거래 중인 아이템의 상태가 바뀌었습니다");
                    it.custody(Custody.delivery(to));
                    items.update(it);
                    items.history(itemId, "TRADED", party, s.id() + " -> " + to, clock.nowMillis());
                }
                long m = s.money(party);
                if (m > 0) economy.transferInTx(party, to, m, "trade", "trade:" + s.id() + ":" + party, after);
            }
            trades.close(s.id(), "COMMITTED", s.money(s.a()), s.money(s.b()), clock.nowMillis());
            audit.record("TRADE_COMPLETED", s.a(), s.b(), "items=" + s.items(s.a()).size() + "/" + s.items(s.b()).size()
                    + " money=" + s.money(s.a()) + "/" + s.money(s.b()), s.id());
            s.markCommitted();
            return null;
        });
        forget(s);
        after.publish(bus);
        bus.publish(new GameEvents.TradeCompleted(s.id(), s.a(), s.b()));
    }

    public void cancel(String tradeId, String reason) {
        TradeSession s = sessions.get(tradeId);
        if (s == null) return;
        synchronized (s) {
            if (s.state() != TradeSession.State.OPEN) return;
            tx.inTx(() -> {
                for (String party : List.of(s.a(), s.b())) for (String itemId : s.items(party)) returnToOwner(s.id(), itemId, party);
                trades.close(s.id(), "CANCELLED", 0, 0, clock.nowMillis());
                audit.record("TRADE_CANCELLED", s.a(), s.b(), reason, s.id());
                s.cancel();
                return null;
            });
            forget(s);
        }
        bus.publish(new GameEvents.TradeCancelled(tradeId, reason));
    }

    /** 플레이어가 나가면 그 사람의 거래를 취소 */
    public void cancelFor(String uuid, String reason) {
        String id = byPlayer.get(uuid);
        if (id != null) cancel(id, reason);
    }

    private void forget(TradeSession s) {
        sessions.remove(s.id());
        byPlayer.remove(s.a(), s.id());
        byPlayer.remove(s.b(), s.id());
    }

    /** 서버 시작 시: 지난 실행에서 열린 채 남은 거래를 모두 취소하고 아이템을 원래 주인에게 돌려보낸다 */
    public int recover() {
        int n = 0;
        for (String id : trades.openTradeIds()) {
            tx.inTx(() -> {
                for (String[] o : trades.offers(id)) returnToOwner(id, o[0], o[1]);
                trades.close(id, "CANCELLED", 0, 0, clock.nowMillis());
                audit.record("TRADE_CANCELLED", null, id, "server restart recovery", id);
                return null;
            });
            n++;
        }
        return n;
    }
}
