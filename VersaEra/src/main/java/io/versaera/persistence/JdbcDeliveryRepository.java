package io.versaera.persistence;

import io.versaera.application.port.DeliveryRepository;

import java.util.List;

public final class JdbcDeliveryRepository implements DeliveryRepository {
    private final Jdbc j;

    public JdbcDeliveryRepository(Database db) {
        this.j = new Jdbc(db);
    }

    @Override
    public long add(String uuid, String typeId, int quality, int amount, String reason, long at) {
        return j.insertKey("INSERT INTO delivery_bulk (uuid, type_id, quality, amount, reason, created_at) VALUES (?, ?, ?, ?, ?, ?)", ps -> {
            ps.setString(1, uuid);
            ps.setString(2, typeId);
            ps.setInt(3, quality);
            ps.setInt(4, amount);
            ps.setString(5, reason);
            ps.setLong(6, at);
        });
    }

    @Override
    public List<Bulk> pending(String uuid) {
        return j.query("SELECT id, uuid, type_id, quality, amount, reason FROM delivery_bulk WHERE uuid = ? ORDER BY id", ps -> ps.setString(1, uuid),
                rs -> new Bulk(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getInt(5), rs.getString(6)));
    }

    @Override
    public boolean take(long id) {
        return j.update("DELETE FROM delivery_bulk WHERE id = ?", ps -> ps.setLong(1, id)) == 1;
    }
}
