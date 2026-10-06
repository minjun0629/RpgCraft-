package io.versaera.domain.event;

/** 시스템끼리 주고받는 도메인 이벤트 모음 (04_ARCHITECTURE §7). 트랜잭션이 성공한 뒤에만 발행한다. */
public final class GameEvents {
    private GameEvents() {
    }

    public record ItemCreated(String itemId, String typeId, int quality, String recipient, String method) implements DomainEvent {}

    public record ItemDestroyed(String itemId, String actor, String reason) implements DomainEvent {}

    public record ItemDelivered(String itemId, String uuid) implements DomainEvent {}

    public record MoneyChanged(String uuid, long delta, long balance, String reason) implements DomainEvent {}

    public record TradeCompleted(String tradeId, String a, String b) implements DomainEvent {}

    public record TradeCancelled(String tradeId, String reason) implements DomainEvent {}

    public record PlayerCrafted(String uuid, String recipeId, String itemId, int quality) implements DomainEvent {}

    public record MasteryLevelUp(String uuid, String discipline, int level, boolean newTier) implements DomainEvent {}

    public record StatGained(String uuid, String statId, int points, boolean newlyUnlocked) implements DomainEvent {}

    public record PlayerDiscovered(String uuid, String kind, String ref, boolean worldFirst) implements DomainEvent {}

    public record NpcRelationChanged(String uuid, String npcId, int affinity, int delta) implements DomainEvent {}

    public record HiddenUnlocked(String uuid, String ruleId, boolean worldFirst, String rumor) implements DomainEvent {}

    public record BossDefeated(String bossId, java.util.List<String> participants) implements DomainEvent {}
}
