package io.versaera.application.port;

import io.versaera.domain.item.Custody;
import io.versaera.domain.item.ItemInstance;

import java.util.List;
import java.util.Optional;

public interface ItemRepository {
    Optional<ItemInstance> find(String id);

    void insert(ItemInstance item);

    /** 낙관적 잠금: 저장된 version 이 item.version() 과 다르면 {@link ConcurrentModification}. 성공하면 item.version +1 */
    void update(ItemInstance item);

    List<ItemInstance> byCustody(Custody custody);

    void history(String itemId, String event, String actorUuid, String detail, long at);

    List<String> historyOf(String itemId);

    final class ConcurrentModification extends RuntimeException {
        public ConcurrentModification(String id) {
            super("아이템이 동시에 바뀌었습니다: " + id);
        }
    }
}
