package io.versaera.persistence;

import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * DB 전용 단일 스레드. 모든 서비스 호출을 여기로 보내 순서대로 처리한다 → 동시 거래 · 중복 클릭도 한 줄로 서서 처리됨.
 * 메인 스레드는 결과(CompletableFuture)를 기다리지 않고, 끝나면 콜백으로 돌아간다.
 */
public final class DbExecutor implements AutoCloseable {
    private final ExecutorService exec;
    private final Logger log;

    public DbExecutor(Logger log) {
        this.log = log;
        this.exec = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "versa-db");
            t.setDaemon(true);
            return t;
        });
    }

    public <T> CompletableFuture<T> submit(String what, Callable<T> job) {
        CompletableFuture<T> f = new CompletableFuture<>();
        exec.execute(() -> {
            try {
                f.complete(job.call());
            } catch (Throwable t) {
                if (!(t instanceof io.versaera.domain.common.DomainException)) log.log(Level.WARNING, "DB 작업 실패: " + what, t);
                f.completeExceptionally(t);
            }
        });
        return f;
    }

    /** 서버 종료: 남은 작업을 모두 끝낼 때까지 기다린다 (데이터 손실 방지) */
    @Override
    public void close() {
        exec.shutdown();
        try {
            if (!exec.awaitTermination(30, TimeUnit.SECONDS)) log.severe("DB 작업이 30초 안에 끝나지 않았습니다");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
