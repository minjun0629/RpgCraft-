package io.versaera.persistence;

import io.versaera.application.port.ProfileRepository;

import java.util.Optional;

public final class JdbcProfileRepository implements ProfileRepository {
    private final Jdbc j;

    public JdbcProfileRepository(Database db) {
        this.j = new Jdbc(db);
    }

    @Override
    public Optional<Profile> find(String uuid) {
        return Optional.ofNullable(j.one("SELECT uuid, name, first_seen, last_seen, level, exp FROM player_profile WHERE uuid = ?",
                ps -> ps.setString(1, uuid), rs -> new Profile(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getLong(4), rs.getInt(5), rs.getLong(6)), null));
    }

    @Override
    public void upsert(Profile p) {
        j.update("""
                INSERT INTO player_profile (uuid, name, first_seen, last_seen, level, exp) VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (uuid) DO UPDATE SET name = excluded.name, last_seen = excluded.last_seen, level = excluded.level, exp = excluded.exp,
                    version = version + 1""", ps -> {
            ps.setString(1, p.uuid());
            ps.setString(2, p.name());
            ps.setLong(3, p.firstSeen());
            ps.setLong(4, p.lastSeen());
            ps.setInt(5, p.level());
            ps.setLong(6, p.exp());
        });
    }
}
