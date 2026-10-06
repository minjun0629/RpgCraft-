package io.versaera.persistence;

import io.versaera.application.port.AuditLog;
import io.versaera.domain.common.GameClock;

import java.util.List;

public final class JdbcAuditLog implements AuditLog {
    private final Jdbc j;
    private final GameClock clock;

    public JdbcAuditLog(Database db, GameClock clock) {
        this.j = new Jdbc(db);
        this.clock = clock;
    }

    @Override
    public void record(String action, String actor, String target, String detail, String requestId) {
        j.update("INSERT INTO audit_log (action, actor_uuid, target, detail, request_id, created_at) VALUES (?, ?, ?, ?, ?, ?)", ps -> {
            ps.setString(1, action);
            ps.setString(2, actor);
            ps.setString(3, target);
            ps.setString(4, detail);
            ps.setString(5, requestId);
            ps.setLong(6, clock.nowMillis());
        });
    }

    @Override
    public List<String> recent(String action, int limit) {
        return j.query("SELECT action || ' ' || COALESCE(target, '') || ' ' || COALESCE(detail, '') FROM audit_log WHERE action = ? ORDER BY id DESC LIMIT ?",
                ps -> { ps.setString(1, action); ps.setInt(2, limit); }, rs -> rs.getString(1));
    }
}
