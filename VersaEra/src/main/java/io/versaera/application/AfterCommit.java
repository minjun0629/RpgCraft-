package io.versaera.application;

import io.versaera.domain.event.DomainEvent;
import io.versaera.domain.event.EventBus;

import java.util.ArrayList;
import java.util.List;

/** 트랜잭션 안에서 생긴 이벤트를 모아 두었다가, 커밋이 끝난 뒤에만 발행한다 (롤백된 일을 알리지 않기 위해). */
final class AfterCommit {
    private final List<DomainEvent> events = new ArrayList<>();

    void add(DomainEvent e) {
        events.add(e);
    }

    void publish(EventBus bus) {
        for (DomainEvent e : events) bus.publish(e);
        events.clear();
    }
}
