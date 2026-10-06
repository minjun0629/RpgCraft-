package io.versaera.persistence;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** JDBC 도우미: SQLException 을 PersistenceException 으로 감싸 서비스 코드가 단순하게. */
final class Jdbc {
    interface Binder {
        void bind(PreparedStatement ps) throws SQLException;
    }

    interface Row<T> {
        T map(ResultSet rs) throws SQLException;
    }

    private final Database db;

    Jdbc(Database db) {
        this.db = db;
    }

    int update(String sql, Binder b) {
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            b.bind(ps);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new PersistenceException(sql, e);
        }
    }

    long insertKey(String sql, Binder b) {
        try (PreparedStatement ps = db.connection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            b.bind(ps);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        } catch (SQLException e) {
            throw new PersistenceException(sql, e);
        }
    }

    <T> List<T> query(String sql, Binder b, Row<T> row) {
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            b.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                List<T> out = new ArrayList<>();
                while (rs.next()) out.add(row.map(rs));
                return out;
            }
        } catch (SQLException e) {
            throw new PersistenceException(sql, e);
        }
    }

    <T> T one(String sql, Binder b, Row<T> row, T fallback) {
        List<T> l = query(sql, b, row);
        return l.isEmpty() ? fallback : l.get(0);
    }
}
