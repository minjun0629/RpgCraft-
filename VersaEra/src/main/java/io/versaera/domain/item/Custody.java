package io.versaera.domain.item;

import java.util.Objects;

/**
 * 아이템이 지금 어디에 있는가 (서버가 판단하는 진실).
 * PLAYER: 그 플레이어 소유 · ESCROW: 거래 중 보관 · DELIVERY: 그 플레이어 인벤토리로 배달 대기 · DESTROYED: 사라짐
 */
public record Custody(Kind kind, String ref) {
    public enum Kind { PLAYER, ESCROW, DELIVERY, DESTROYED }

    public Custody {
        Objects.requireNonNull(kind, "kind");
        if (kind != Kind.DESTROYED) Objects.requireNonNull(ref, "ref");
    }

    public static Custody player(String uuid) {
        return new Custody(Kind.PLAYER, uuid);
    }

    public static Custody escrow(String tradeId) {
        return new Custody(Kind.ESCROW, tradeId);
    }

    public static Custody delivery(String uuid) {
        return new Custody(Kind.DELIVERY, uuid);
    }

    public static Custody destroyed() {
        return new Custody(Kind.DESTROYED, null);
    }

    public boolean ownedBy(String uuid) {
        return kind == Kind.PLAYER && ref.equals(uuid);
    }
}
