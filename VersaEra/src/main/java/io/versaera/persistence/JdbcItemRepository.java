package io.versaera.persistence;

import io.versaera.application.port.ItemRepository;
import io.versaera.domain.item.Custody;
import io.versaera.domain.item.ItemInstance;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public final class JdbcItemRepository implements ItemRepository {
    private final Jdbc j;

    public JdbcItemRepository(Database db) {
        this.j = new Jdbc(db);
    }

    private static ItemInstance map(ResultSet rs) throws SQLException {
        Custody.Kind kind = Custody.Kind.valueOf(rs.getString("custody_kind"));
        return new ItemInstance(rs.getString("id"), rs.getString("type_id"), rs.getInt("quality"), rs.getInt("durability"),
                rs.getInt("max_durability"), rs.getInt("weight"), rs.getString("creator_uuid"), rs.getString("creator_name"),
                rs.getString("method"), Props.decode(rs.getString("props")), new Custody(kind, rs.getString("custody_ref")),
                rs.getLong("created_at"), rs.getLong("version"));
    }

    @Override
    public Optional<ItemInstance> find(String id) {
        return Optional.ofNullable(j.one("SELECT * FROM item_instance WHERE id = ?", ps -> ps.setString(1, id), JdbcItemRepository::map, null));
    }

    @Override
    public void insert(ItemInstance it) {
        j.update("""
                INSERT INTO item_instance (id, type_id, quality, durability, max_durability, weight, creator_uuid, creator_name, method, props,
                    custody_kind, custody_ref, created_at, version) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", ps -> {
            ps.setString(1, it.id());
            ps.setString(2, it.typeId());
            ps.setInt(3, it.quality());
            ps.setInt(4, it.durability());
            ps.setInt(5, it.maxDurability());
            ps.setInt(6, it.weight());
            ps.setString(7, it.creatorUuid());
            ps.setString(8, it.creatorName());
            ps.setString(9, it.method());
            ps.setString(10, Props.encode(it.props()));
            ps.setString(11, it.custody().kind().name());
            ps.setString(12, it.custody().ref());
            ps.setLong(13, it.createdAt());
            ps.setLong(14, it.version());
        });
    }

    @Override
    public void update(ItemInstance it) {
        int n = j.update("""
                UPDATE item_instance SET durability = ?, max_durability = ?, props = ?, custody_kind = ?, custody_ref = ?, version = version + 1
                WHERE id = ? AND version = ?""", ps -> {
            ps.setInt(1, it.durability());
            ps.setInt(2, it.maxDurability());
            ps.setString(3, Props.encode(it.props()));
            ps.setString(4, it.custody().kind().name());
            ps.setString(5, it.custody().ref());
            ps.setString(6, it.id());
            ps.setLong(7, it.version());
        });
        if (n != 1) throw new ConcurrentModification(it.id());
        it.bumpVersion();
    }

    @Override
    public List<ItemInstance> byCustody(Custody c) {
        return j.query("SELECT * FROM item_instance WHERE custody_kind = ? AND custody_ref IS ? ORDER BY created_at", ps -> {
            ps.setString(1, c.kind().name());
            ps.setString(2, c.ref());
        }, JdbcItemRepository::map);
    }

    @Override
    public void history(String itemId, String event, String actor, String detail, long at) {
        j.update("INSERT INTO item_history (item_id, event, actor_uuid, detail, created_at) VALUES (?, ?, ?, ?, ?)", ps -> {
            ps.setString(1, itemId);
            ps.setString(2, event);
            ps.setString(3, actor);
            ps.setString(4, detail);
            ps.setLong(5, at);
        });
    }

    @Override
    public List<String> historyOf(String itemId) {
        return j.query("SELECT event FROM item_history WHERE item_id = ? ORDER BY id", ps -> ps.setString(1, itemId), rs -> rs.getString(1));
    }
}
