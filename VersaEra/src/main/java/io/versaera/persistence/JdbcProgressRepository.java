package io.versaera.persistence;

import io.versaera.application.port.ProgressRepository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class JdbcProgressRepository implements ProgressRepository {
    private final Jdbc j;

    public JdbcProgressRepository(Database db) {
        this.j = new Jdbc(db);
    }

    @Override
    public long masteryXp(String uuid, String d) {
        return j.one("SELECT xp FROM mastery WHERE uuid = ? AND discipline = ?", ps -> { ps.setString(1, uuid); ps.setString(2, d); }, rs -> rs.getLong(1), 0L);
    }

    @Override
    public void setMasteryXp(String uuid, String d, long xp) {
        j.update("INSERT INTO mastery (uuid, discipline, xp) VALUES (?, ?, ?) ON CONFLICT (uuid, discipline) DO UPDATE SET xp = excluded.xp", ps -> {
            ps.setString(1, uuid);
            ps.setString(2, d);
            ps.setLong(3, xp);
        });
    }

    @Override
    public Map<String, Long> allMastery(String uuid) {
        Map<String, Long> m = new LinkedHashMap<>();
        j.query("SELECT discipline, xp FROM mastery WHERE uuid = ? ORDER BY discipline", ps -> ps.setString(1, uuid), rs -> m.put(rs.getString(1), rs.getLong(2)));
        return m;
    }

    @Override
    public long counter(String uuid, String key) {
        return j.one("SELECT value FROM action_counter WHERE uuid = ? AND key = ?", ps -> { ps.setString(1, uuid); ps.setString(2, key); }, rs -> rs.getLong(1), 0L);
    }

    @Override
    public long addCounter(String uuid, String key, long delta) {
        j.update("INSERT INTO action_counter (uuid, key, value) VALUES (?, ?, ?) ON CONFLICT (uuid, key) DO UPDATE SET value = value + excluded.value", ps -> {
            ps.setString(1, uuid);
            ps.setString(2, key);
            ps.setLong(3, delta);
        });
        return counter(uuid, key);
    }

    @Override
    public Map<String, Long> allCounters(String uuid) {
        Map<String, Long> m = new LinkedHashMap<>();
        j.query("SELECT key, value FROM action_counter WHERE uuid = ?", ps -> ps.setString(1, uuid), rs -> m.put(rs.getString(1), rs.getLong(2)));
        return m;
    }

    @Override
    public boolean discover(String uuid, String kind, String ref, long at) {
        return j.update("INSERT OR IGNORE INTO discovery (uuid, kind, ref, created_at) VALUES (?, ?, ?, ?)", ps -> {
            ps.setString(1, uuid);
            ps.setString(2, kind);
            ps.setString(3, ref);
            ps.setLong(4, at);
        }) == 1;
    }

    @Override
    public boolean discovered(String uuid, String kind, String ref) {
        return j.one("SELECT 1 FROM discovery WHERE uuid = ? AND kind = ? AND ref = ?", ps -> {
            ps.setString(1, uuid);
            ps.setString(2, kind);
            ps.setString(3, ref);
        }, rs -> true, false);
    }

    @Override
    public boolean claimWorldFirst(String kind, String ref, String uuid, String name, long at) {
        return j.update("INSERT OR IGNORE INTO world_first (kind, ref, uuid, name, created_at) VALUES (?, ?, ?, ?, ?)", ps -> {
            ps.setString(1, kind);
            ps.setString(2, ref);
            ps.setString(3, uuid);
            ps.setString(4, name);
            ps.setLong(5, at);
        }) == 1;
    }

    @Override
    public Optional<WorldFirst> worldFirst(String kind, String ref) {
        return Optional.ofNullable(j.one("SELECT uuid, name, created_at FROM world_first WHERE kind = ? AND ref = ?", ps -> {
            ps.setString(1, kind);
            ps.setString(2, ref);
        }, rs -> new WorldFirst(rs.getString(1), rs.getString(2), rs.getLong(3)), null));
    }

    @Override
    public RelationRow relation(String uuid, String npc) {
        return j.one("SELECT affinity, last_talk FROM npc_relation WHERE uuid = ? AND npc_id = ?", ps -> {
            ps.setString(1, uuid);
            ps.setString(2, npc);
        }, rs -> new RelationRow(rs.getInt(1), rs.getLong(2)), new RelationRow(0, 0));
    }

    @Override
    public void setRelation(String uuid, String npc, int affinity, long lastTalk) {
        j.update("INSERT INTO npc_relation (uuid, npc_id, affinity, last_talk) VALUES (?, ?, ?, ?) "
                + "ON CONFLICT (uuid, npc_id) DO UPDATE SET affinity = excluded.affinity, last_talk = excluded.last_talk", ps -> {
            ps.setString(1, uuid);
            ps.setString(2, npc);
            ps.setInt(3, affinity);
            ps.setLong(4, lastTalk);
        });
    }

    @Override
    public boolean unlockHidden(String uuid, String rule, long at) {
        return j.update("INSERT OR IGNORE INTO hidden_unlock (uuid, rule_id, created_at) VALUES (?, ?, ?)", ps -> {
            ps.setString(1, uuid);
            ps.setString(2, rule);
            ps.setLong(3, at);
        }) == 1;
    }

    @Override
    public boolean hiddenUnlocked(String uuid, String rule) {
        return j.one("SELECT 1 FROM hidden_unlock WHERE uuid = ? AND rule_id = ?", ps -> { ps.setString(1, uuid); ps.setString(2, rule); }, rs -> true, false);
    }
}
