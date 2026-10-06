package io.versaera.application.port;

import java.util.List;

/** 묶음 재료 배달함 */
public interface DeliveryRepository {
    record Bulk(long id, String uuid, String typeId, int quality, int amount, String reason) {}

    long add(String uuid, String typeId, int quality, int amount, String reason, long at);

    List<Bulk> pending(String uuid);

    /** @return 지웠으면 true (이미 배달됐으면 false — 두 번 주지 않기 위해 지운 쪽만 지급) */
    boolean take(long id);
}
