package io.versaera.domain.trade;

import io.versaera.domain.common.DomainException;
import io.versaera.domain.common.Money;

import java.util.*;

/**
 * 1:1 거래 상태 기계 (순수 규칙).
 * <pre>
 * OPEN ── 양쪽 LOCK(제안 고정) ── 양쪽 CONFIRM(같은 제안 버전에) ── COMMITTED
 *   └──────────────── 아무 때나 cancel ─────────────────────────────── CANCELLED
 * </pre>
 * 제안이 바뀌면(아이템 · 돈) offerVersion 이 오르고 양쪽 LOCK · CONFIRM 이 모두 풀린다
 * → "확인 직전에 몰래 아이템을 바꾸는" 사기를 막는다.
 */
public final class TradeSession {
    public enum State { OPEN, COMMITTED, CANCELLED }

    public static final int MAX_ITEMS_PER_SIDE = 12;

    private final String id;
    private final String a, b;
    private final long createdAt;
    private State state = State.OPEN;
    private final Map<String, LinkedHashSet<String>> items = new HashMap<>();
    private final Map<String, Long> money = new HashMap<>();
    private final Set<String> locked = new HashSet<>();
    private final Map<String, Integer> confirmedAt = new HashMap<>();
    private int offerVersion;

    public TradeSession(String id, String a, String b, long createdAt) {
        DomainException.require(!a.equals(b), "trade.self", "자기 자신과는 거래할 수 없습니다");
        this.id = id;
        this.a = a;
        this.b = b;
        this.createdAt = createdAt;
        items.put(a, new LinkedHashSet<>());
        items.put(b, new LinkedHashSet<>());
        money.put(a, 0L);
        money.put(b, 0L);
    }

    public String id() { return id; }
    public String a() { return a; }
    public String b() { return b; }
    public long createdAt() { return createdAt; }
    public State state() { return state; }
    public int offerVersion() { return offerVersion; }

    public String other(String party) {
        requireParty(party);
        return party.equals(a) ? b : a;
    }

    public Set<String> items(String party) {
        requireParty(party);
        return Collections.unmodifiableSet(items.get(party));
    }

    public long money(String party) {
        requireParty(party);
        return money.get(party);
    }

    public boolean locked(String party) {
        return locked.contains(party);
    }

    public boolean confirmed(String party) {
        return confirmedAt.getOrDefault(party, -1) == offerVersion;
    }

    private void requireParty(String p) {
        DomainException.require(a.equals(p) || b.equals(p), "trade.not_party", "이 거래의 당사자가 아닙니다");
    }

    private void requireOpen() {
        DomainException.require(state == State.OPEN, "trade.closed", "이미 끝난 거래입니다");
    }

    private void changed() {
        offerVersion++;
        locked.clear();
        confirmedAt.clear();
    }

    public void addItem(String party, String itemId) {
        requireParty(party);
        requireOpen();
        DomainException.require(!items.get(a).contains(itemId) && !items.get(b).contains(itemId), "trade.duplicate_item", "이미 올린 아이템입니다");
        DomainException.require(items.get(party).size() < MAX_ITEMS_PER_SIDE, "trade.too_many", "한 번에 " + MAX_ITEMS_PER_SIDE + "개까지 올릴 수 있습니다");
        items.get(party).add(itemId);
        changed();
    }

    public void removeItem(String party, String itemId) {
        requireParty(party);
        requireOpen();
        DomainException.require(items.get(party).remove(itemId), "trade.no_such_item", "올리지 않은 아이템입니다");
        changed();
    }

    public void setMoney(String party, long amount) {
        requireParty(party);
        requireOpen();
        Money.requireNonNegative(amount);
        if (money.get(party) == amount) return;
        money.put(party, amount);
        changed();
    }

    public void lock(String party) {
        requireParty(party);
        requireOpen();
        locked.add(party);
    }

    /** 확인은 양쪽이 모두 LOCK 한 뒤에만, 그리고 지금 제안 버전에 대해서만 유효하다 */
    public void confirm(String party, int seenVersion) {
        requireParty(party);
        requireOpen();
        DomainException.require(locked.contains(a) && locked.contains(b), "trade.not_locked", "양쪽이 제안을 고정해야 확인할 수 있습니다");
        DomainException.require(seenVersion == offerVersion, "trade.stale", "제안이 바뀌었습니다. 다시 확인하세요");
        confirmedAt.put(party, offerVersion);
    }

    public boolean readyToCommit() {
        return state == State.OPEN && confirmed(a) && confirmed(b) && !(items.get(a).isEmpty() && items.get(b).isEmpty() && money.get(a) == 0 && money.get(b) == 0);
    }

    public void markCommitted() {
        DomainException.require(readyToCommit(), "trade.not_ready", "양쪽 확인이 끝나지 않았습니다");
        state = State.COMMITTED;
    }

    public void cancel() {
        requireOpen();
        state = State.CANCELLED;
    }
}
