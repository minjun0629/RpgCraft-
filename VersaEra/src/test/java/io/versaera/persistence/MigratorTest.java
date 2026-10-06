package io.versaera.persistence;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MigratorTest {
    @Test
    void appliesRealMigrationsOnceAndIsIdempotent() throws Exception {
        try (Database db = Database.open("jdbc:sqlite::memory:")) {
            Migrator m = new Migrator(db);
            List<Migrator.Migration> list = Migrator.fromClasspath(getClass().getClassLoader());
            assertEquals(list.size(), m.migrate(list));
            assertEquals(0, m.migrate(list), "두 번째 실행은 아무것도 적용하지 않아야 함");
            assertEquals(list.size(), m.currentVersion());
        }
    }

    @Test
    void refusesWhenAppliedMigrationWasEdited() throws Exception {
        try (Database db = Database.open("jdbc:sqlite::memory:")) {
            Migrator m = new Migrator(db);
            m.migrate(List.of(new Migrator.Migration(1, "V1__a.sql", "CREATE TABLE a (x INTEGER);")));
            Migrator.MigrationException ex = assertThrows(Migrator.MigrationException.class,
                    () -> m.migrate(List.of(new Migrator.Migration(1, "V1__a.sql", "CREATE TABLE a (x INTEGER, y INTEGER);"))));
            assertTrue(ex.getMessage().contains("바뀌었습니다"));
        }
    }

    @Test
    void failedMigrationRollsBackCompletely() throws Exception {
        try (Database db = Database.open("jdbc:sqlite::memory:")) {
            Migrator m = new Migrator(db);
            assertThrows(Migrator.MigrationException.class, () -> m.migrate(List.of(
                    new Migrator.Migration(1, "V1__bad.sql", "CREATE TABLE ok (x INTEGER); CREATE TABLE broken ("))));
            assertEquals(0, m.currentVersion());
            assertEquals(1, m.migrate(List.of(new Migrator.Migration(1, "V1__good.sql", "CREATE TABLE ok (x INTEGER);"))),
                    "실패한 마이그레이션의 앞부분(테이블 ok)이 남아 있으면 안 됨");
        }
    }

    @Test
    void rejectsGapsInNumbering() {
        Map<String, String> files = Map.of("migrations.txt", "V1__a.sql\nV3__c.sql\n", "V1__a.sql", "SELECT 1;", "V3__c.sql", "SELECT 1;");
        assertThrows(Migrator.MigrationException.class, () -> Migrator.load(n -> files.containsKey(n)
                ? new ByteArrayInputStream(files.get(n).getBytes(StandardCharsets.UTF_8)) : null));
    }
}
