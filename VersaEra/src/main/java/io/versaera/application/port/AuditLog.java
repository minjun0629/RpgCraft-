package io.versaera.application.port;

import java.util.List;

/** 경제 · 아이템 감사 로그 (ITEM_CREATED · MONEY_ADDED · TRADE_COMPLETED …) */
public interface AuditLog {
    void record(String action, String actorUuid, String target, String detail, String requestId);

    List<String> recent(String action, int limit);
}
