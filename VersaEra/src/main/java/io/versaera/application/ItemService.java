package io.versaera.application;

import io.versaera.application.port.AuditLog;
import io.versaera.application.port.DeliveryRepository;
import io.versaera.application.port.ItemRepository;
import io.versaera.application.port.TxRunner;
import io.versaera.domain.common.DomainException;
import io.versaera.domain.common.GameClock;
import io.versaera.domain.event.EventBus;
import io.versaera.domain.event.GameEvents;
import io.versaera.domain.item.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 고유 아이템의 생성 · 배달 · 검증 · 파괴 · 마모 · 수리. 모든 변경은 트랜잭션 + 내력 + 감사 로그.
 * 새 아이템은 바로 인벤토리에 넣지 않고 DELIVERY 로 만든다 → 플랫폼이 인벤토리에 넣은 뒤 confirmDelivered.
 */
public final class ItemService {
    public enum Verdict { OK, UNKNOWN, DESTROYED, NOT_OWNER, IN_ESCROW, AWAITING_DELIVERY }

    private final TxRunner tx;
    private final ItemRepository items;
    private final DeliveryRepository bulk;
    private final ItemTypeRegistry types;
    private final AuditLog audit;
    private final EventBus bus;
    private final GameClock clock;

    public ItemService(TxRunner tx, ItemRepository items, DeliveryRepository bulk, ItemTypeRegistry types, AuditLog audit, EventBus bus, GameClock clock) {
        this.tx = tx;
        this.items = items;
        this.bulk = bulk;
        this.types = types;
        this.audit = audit;
        this.bus = bus;
        this.clock = clock;
    }

    public ItemTypeRegistry types() {
        return types;
    }

    /** 바깥 트랜잭션 안에서 쓰는 생성 (이벤트는 호출자가 커밋 뒤에 발행) */
    ItemInstance createInTx(String typeId, int quality, String creatorUuid, String creatorName, String method, Map<String, String> props,
                            String recipient, String requestId, AfterCommit after) {
        ItemType t = types.get(typeId);
        DomainException.require(t.category().unique(), "item.not_unique", "묶음 아이템은 고유 아이템으로 만들 수 없습니다: " + typeId);
        int q = Quality.clamp(quality);
        int max = (int) Math.round(t.baseDurability() * Quality.durabilityMultiplier(q));
        ItemInstance it = new ItemInstance(UUID.randomUUID().toString(), typeId, q, max, max, t.weight(), creatorUuid, creatorName, method, props,
                Custody.delivery(recipient), clock.nowMillis(), 0);
        items.insert(it);
        items.history(it.id(), "CREATED", creatorUuid, method, clock.nowMillis());
        audit.record("ITEM_CREATED", creatorUuid, it.id(), typeId + " q=" + q + " to=" + recipient, requestId);
        after.add(new GameEvents.ItemCreated(it.id(), typeId, q, recipient, method));
        return it;
    }

    public ItemInstance create(String typeId, int quality, String creatorUuid, String creatorName, String method, Map<String, String> props,
                               String recipient, String requestId) {
        AfterCommit after = new AfterCommit();
        ItemInstance it = tx.inTx(() -> createInTx(typeId, quality, creatorUuid, creatorName, method, props, recipient, requestId, after));
        after.publish(bus);
        return it;
    }

    public Optional<ItemInstance> find(String id) {
        return items.find(id);
    }

    /** 배달 대기 중인 고유 아이템 */
    public List<ItemInstance> pendingDeliveries(String uuid) {
        return items.byCustody(Custody.delivery(uuid));
    }

    /** 플랫폼이 인벤토리에 넣은 뒤 부른다. 이미 확정됐거나 다른 사람 배달이면 false (두 번 주지 않기 위해 확인용) */
    public boolean confirmDelivered(String itemId, String uuid) {
        boolean ok = tx.inTx(() -> {
            ItemInstance it = items.find(itemId).orElse(null);
            if (it == null || !it.custody().equals(Custody.delivery(uuid))) return false;
            it.custody(Custody.player(uuid));
            items.update(it);
            items.history(itemId, "DELIVERED", uuid, null, clock.nowMillis());
            return true;
        });
        if (ok) bus.publish(new GameEvents.ItemDelivered(itemId, uuid));
        return ok;
    }

    /** 인벤토리에 보이는 아이템 아이디가 그 플레이어가 가져도 되는 진짜 아이템인지 */
    public Verdict validate(String itemId, String holderUuid) {
        if (itemId == null || !isUuid(itemId)) return Verdict.UNKNOWN;
        ItemInstance it = items.find(itemId).orElse(null);
        if (it == null) return Verdict.UNKNOWN;
        return switch (it.custody().kind()) {
            case DESTROYED -> Verdict.DESTROYED;
            case ESCROW -> Verdict.IN_ESCROW;
            case DELIVERY -> it.custody().ref().equals(holderUuid) ? Verdict.AWAITING_DELIVERY : Verdict.NOT_OWNER;
            case PLAYER -> it.custody().ref().equals(holderUuid) ? Verdict.OK : Verdict.NOT_OWNER;
        };
    }

    static boolean isUuid(String s) {
        try {
            return UUID.fromString(s).toString().equals(s);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public void destroy(String itemId, String actor, String reason, String requestId) {
        tx.inTx(() -> {
            ItemInstance it = items.find(itemId).orElseThrow(() -> DomainException.of("item.unknown", "없는 아이템: " + itemId));
            DomainException.require(it.custody().kind() != Custody.Kind.DESTROYED, "item.already_destroyed", "이미 사라진 아이템입니다");
            it.custody(Custody.destroyed());
            items.update(it);
            items.history(itemId, "DESTROYED", actor, reason, clock.nowMillis());
            audit.record("ITEM_DESTROYED", actor, itemId, reason, requestId);
            return null;
        });
        bus.publish(new GameEvents.ItemDestroyed(itemId, actor, reason));
    }

    /** 사용 · 피격 마모. 소유자만, 실제로 손에 있는 아이템만 (플랫폼이 확인 후 호출) */
    public ItemInstance wear(String itemId, String owner, int amount, boolean heavy) {
        return tx.inTx(() -> {
            ItemInstance it = items.find(itemId).orElseThrow(() -> DomainException.of("item.unknown", "없는 아이템"));
            DomainException.require(it.custody().ownedBy(owner), "item.not_owner", "소유자가 아닙니다");
            it.wear(amount, heavy);
            items.update(it);
            return it;
        });
    }

    public Repair.Result repair(String itemId, String owner, String repairer, int repairLevel, String requestId) {
        return tx.inTx(() -> {
            ItemInstance it = items.find(itemId).orElseThrow(() -> DomainException.of("item.unknown", "없는 아이템"));
            DomainException.require(it.custody().ownedBy(owner), "item.not_owner", "소유자가 아닙니다");
            ItemType t = types.get(it.typeId());
            int baseMax = (int) Math.round(t.baseDurability() * Quality.durabilityMultiplier(it.quality()));
            Repair.Result r = Repair.apply(it, baseMax, repairLevel);
            items.update(it);
            items.history(itemId, "REPAIRED", repairer, r.maxBefore() + "->" + r.maxAfter(), clock.nowMillis());
            audit.record("ITEM_REPAIRED", repairer, itemId, "restored=" + r.restored(), requestId);
            return r;
        });
    }

    // ------------------------------------------------------------------ 묶음 재료 배달
    public long deliverBulk(String uuid, String typeId, int quality, int amount, String reason) {
        DomainException.require(amount > 0 && amount <= 64 * 36, "bulk.bad_amount", "수량이 잘못되었습니다: " + amount);
        types.get(typeId);
        return tx.inTx(() -> {
            long id = bulk.add(uuid, typeId, Quality.clamp(quality), amount, reason, clock.nowMillis());
            audit.record("ITEM_CREATED", null, "bulk:" + id, typeId + " x" + amount + " to=" + uuid + " (" + reason + ")", null);
            return id;
        });
    }

    public List<DeliveryRepository.Bulk> pendingBulk(String uuid) {
        return bulk.pending(uuid);
    }

    /** 플랫폼이 인벤토리에 넣기 직전에 부른다. true 를 받은 쪽만 실제로 지급 (중복 지급 방지) */
    public boolean takeBulk(long id) {
        return tx.inTx(() -> bulk.take(id));
    }
}
