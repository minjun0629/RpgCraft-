package io.versaera;

import io.versaera.application.GameServices;
import io.versaera.content.ContentBundle;
import io.versaera.domain.common.GameClock;
import io.versaera.domain.event.DomainEvent;
import io.versaera.persistence.Database;
import io.versaera.persistence.Migrator;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/** 테스트용: 메모리 DB + 실제 마이그레이션 + 실제 콘텐츠로 서비스 전체를 조립한다. */
public final class TestWorld implements AutoCloseable {
    public final Database db;
    public final GameServices s;
    public final AtomicLong now = new AtomicLong(1_700_000_000_000L);
    public final List<DomainEvent> events = new ArrayList<>();

    public TestWorld() throws Exception {
        db = Database.open("jdbc:sqlite::memory:");
        new Migrator(db).migrate(Migrator.fromClasspath(getClass().getClassLoader()));
        GameClock clock = now::get;
        s = new GameServices(db, ContentBundle.fromClasspath(getClass().getClassLoader()), clock, ZoneId.of("Asia/Seoul"), Logger.getLogger("test"));
        s.bus.subscribe(DomainEvent.class, events::add);
    }

    public static String player() {
        return UUID.randomUUID().toString();
    }

    public <E> List<E> eventsOf(Class<E> type) {
        List<E> out = new ArrayList<>();
        for (DomainEvent e : events) if (type.isInstance(e)) out.add(type.cast(e));
        return out;
    }

    @Override
    public void close() throws Exception {
        db.close();
    }
}
