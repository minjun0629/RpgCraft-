package io.versaera.platform.bukkit;

import io.versaera.domain.common.DomainException;
import io.versaera.persistence.DbExecutor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

/** 메인 스레드 → DB 스레드 → 메인 스레드. 메인 스레드는 DB 를 기다리지 않는다. */
public final class Async {
    private final Plugin plugin;
    private final DbExecutor db;

    public Async(Plugin plugin, DbExecutor db) {
        this.plugin = plugin;
        this.db = db;
    }

    public <T> void run(String what, Callable<T> job, Consumer<T> onMain, CommandSender errorsTo) {
        run(what, job, onMain, null, errorsTo);
    }

    /** onError: 실패했을 때 메인 스레드에서 정리할 일 (잠금 풀기 등) */
    public <T> void run(String what, Callable<T> job, Consumer<T> onMain, Consumer<Throwable> onError, CommandSender errorsTo) {
        db.submit(what, job).whenComplete((v, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (err == null) {
                if (onMain != null) onMain.accept(v);
                return;
            }
            Throwable t = err instanceof CompletionException && err.getCause() != null ? err.getCause() : err;
            if (onError != null) onError.accept(t);
            if (errorsTo != null) errorsTo.sendMessage(Ui.error(t instanceof DomainException de ? de.getMessage() : "처리하지 못했습니다. 잠시 뒤 다시 시도하세요."));
        }));
    }

    public void fire(String what, Callable<?> job) {
        db.submit(what, job);
    }
}
