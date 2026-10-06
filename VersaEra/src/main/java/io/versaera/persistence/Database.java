package io.versaera.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

/**
 * SQLite 연결 하나와 트랜잭션 도우미.
 * <p>모든 호출은 같은 스레드(DbExecutor 의 versa-db)에서만 한다 — SQLite 는 쓰기 1개가 안전하고,
 * 연결 하나를 공유하므로 트랜잭션이 섞이지 않게 하는 책임은 호출 스레드 규칙에 있다.</p>
 */
public final class Database implements AutoCloseable {
    @FunctionalInterface
    public interface Work<T> {
        T run(Connection c) throws SQLException;
    }

    private final Connection connection;
    private boolean inTx;

    private Database(Connection connection) {
        this.connection = connection;
    }

    /** jdbcUrl 예: "jdbc:sqlite:plugins/VersaEra/data.db" · 테스트는 "jdbc:sqlite::memory:" */
    public static Database open(String jdbcUrl) throws SQLException {
        Objects.requireNonNull(jdbcUrl, "jdbcUrl");
        Connection c = DriverManager.getConnection(jdbcUrl);
        try (Statement s = c.createStatement()) {
            s.execute("PRAGMA foreign_keys = ON");
            s.execute("PRAGMA busy_timeout = 5000");
            if (!jdbcUrl.contains(":memory:")) {
                s.execute("PRAGMA journal_mode = WAL");
                s.execute("PRAGMA synchronous = NORMAL");
            }
        }
        return new Database(c);
    }

    public Connection connection() {
        return connection;
    }

    /**
     * work 를 트랜잭션 하나로 실행한다. 예외가 나면 전부 되돌리고 예외를 그대로 던진다.
     * 이미 트랜잭션 안이면 바깥 트랜잭션에 합쳐진다 (중첩 호출 허용).
     */
    public <T> T tx(Work<T> work) throws SQLException {
        if (inTx) return work.run(connection);
        boolean auto = connection.getAutoCommit();
        connection.setAutoCommit(false);
        inTx = true;
        try {
            T r = work.run(connection);
            connection.commit();
            return r;
        } catch (SQLException | RuntimeException e) {
            try {
                connection.rollback();
            } catch (SQLException re) {
                e.addSuppressed(re);
            }
            throw e;
        } finally {
            inTx = false;
            connection.setAutoCommit(auto);
        }
    }

    public boolean inTransaction() {
        return inTx;
    }

    @Override
    public void close() throws SQLException {
        connection.close();
    }
}
