package io.versaera.persistence;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Function;

/**
 * db/migration/migrations.txt 에 적힌 V{n}__*.sql 을 순서대로 한 번씩 적용한다.
 * 적용한 파일의 해시를 기록하고, 이미 적용한 파일이 바뀌었으면 시작을 거부한다 (스키마 덮어쓰기 방지).
 */
public final class Migrator {
    public record Migration(int version, String name, String sql) {}

    public static final class MigrationException extends Exception {
        public MigrationException(String msg) {
            super(msg);
        }

        public MigrationException(String msg, Throwable cause) {
            super(msg, cause);
        }
    }

    private final Database db;

    public Migrator(Database db) {
        this.db = db;
    }

    /** 클래스패스의 db/migration 에서 읽는다 */
    public static List<Migration> fromClasspath(ClassLoader cl) throws MigrationException {
        return load(path -> cl.getResourceAsStream("db/migration/" + path));
    }

    static List<Migration> load(Function<String, InputStream> open) throws MigrationException {
        String index = read(open, "migrations.txt");
        List<Migration> out = new ArrayList<>();
        int last = 0;
        for (String line : index.split("\\R")) {
            String name = line.strip();
            if (name.isEmpty() || name.startsWith("#")) continue;
            if (!name.matches("V\\d+__[A-Za-z0-9_]+\\.sql")) throw new MigrationException("잘못된 마이그레이션 이름: " + name);
            int v = Integer.parseInt(name.substring(1, name.indexOf("__")));
            if (v != last + 1) throw new MigrationException("마이그레이션 번호가 이어지지 않음: " + name + " (기대: V" + (last + 1) + ")");
            last = v;
            out.add(new Migration(v, name, read(open, name)));
        }
        return out;
    }

    private static String read(Function<String, InputStream> open, String name) throws MigrationException {
        try (InputStream in = open.apply(name)) {
            if (in == null) throw new MigrationException("마이그레이션 파일 없음: " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new MigrationException("마이그레이션 파일 읽기 실패: " + name, e);
        }
    }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** @return 이번에 새로 적용한 마이그레이션 수 */
    public int migrate(List<Migration> migrations) throws MigrationException {
        try {
            Connection c = db.connection();
            try (Statement s = c.createStatement()) {
                s.execute("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER PRIMARY KEY, name TEXT NOT NULL, checksum TEXT NOT NULL, applied_at INTEGER NOT NULL)");
            }
            int applied = 0;
            for (Migration m : migrations) {
                String sum = sha256(m.sql());
                String existing = null;
                try (PreparedStatement ps = c.prepareStatement("SELECT checksum FROM schema_version WHERE version = ?")) {
                    ps.setInt(1, m.version());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) existing = rs.getString(1);
                    }
                }
                if (existing != null) {
                    if (!existing.equals(sum))
                        throw new MigrationException("이미 적용한 마이그레이션이 바뀌었습니다: " + m.name() + " — 기존 파일은 고치지 말고 새 버전을 추가하세요");
                    continue;
                }
                db.tx(tx -> {
                    for (String stmt : splitStatements(m.sql())) {
                        try (Statement s = tx.createStatement()) {
                            s.execute(stmt);
                        }
                    }
                    try (PreparedStatement ps = tx.prepareStatement("INSERT INTO schema_version (version, name, checksum, applied_at) VALUES (?, ?, ?, ?)")) {
                        ps.setInt(1, m.version());
                        ps.setString(2, m.name());
                        ps.setString(3, sum);
                        ps.setLong(4, System.currentTimeMillis());
                        ps.executeUpdate();
                    }
                    return null;
                });
                applied++;
            }
            return applied;
        } catch (SQLException e) {
            throw new MigrationException("마이그레이션 실패: " + e.getMessage(), e);
        }
    }

    /** ';' 로 나눈다 (주석 줄 제거). 트리거처럼 본문에 ';' 가 있는 문장은 쓰지 않는다. */
    static List<String> splitStatements(String sql) {
        StringBuilder clean = new StringBuilder();
        for (String line : sql.split("\\R")) {
            String t = line.strip();
            if (t.startsWith("--")) continue;
            clean.append(line).append('\n');
        }
        List<String> out = new ArrayList<>();
        for (String part : clean.toString().split(";")) if (!part.isBlank()) out.add(part.strip());
        return out;
    }

    public int currentVersion() throws SQLException {
        try (Statement s = db.connection().createStatement();
             ResultSet rs = s.executeQuery("SELECT COALESCE(MAX(version), 0) FROM schema_version")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }
}
