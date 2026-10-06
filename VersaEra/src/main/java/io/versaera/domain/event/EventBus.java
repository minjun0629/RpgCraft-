package io.versaera.domain.event;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 동기 이벤트 버스. 구독자는 발행한 스레드에서 호출된다.
 * 한 구독자의 예외가 다른 구독자나 발행자를 망가뜨리지 않게, 예외는 로그로 남기고 계속한다.
 */
public final class EventBus {
    private final Map<Class<?>, List<Consumer<Object>>> handlers = new ConcurrentHashMap<>();
    private final Logger log;

    public EventBus(Logger log) {
        this.log = log;
    }

    @SuppressWarnings("unchecked")
    public <E extends DomainEvent> void subscribe(Class<E> type, Consumer<? super E> handler) {
        handlers.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add(e -> ((Consumer<Object>) (Consumer<?>) handler).accept(e));
    }

    public void publish(DomainEvent event) {
        for (Map.Entry<Class<?>, List<Consumer<Object>>> en : handlers.entrySet()) {
            if (!en.getKey().isInstance(event)) continue;
            for (Consumer<Object> h : en.getValue()) {
                try {
                    h.accept(event);
                } catch (RuntimeException ex) {
                    log.log(Level.WARNING, "이벤트 처리 실패: " + event.getClass().getSimpleName(), ex);
                }
            }
        }
    }
}
