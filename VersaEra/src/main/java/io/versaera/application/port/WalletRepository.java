package io.versaera.application.port;

public interface WalletRepository {
    long balance(String uuid);

    void setBalance(String uuid, long balance);

    /** idempotencyKey 가 이미 있으면 false (같은 요청을 두 번 처리하지 않음) */
    boolean ledgerExists(String idempotencyKey);

    void ledger(String from, String to, long amount, String reason, String idempotencyKey, long at);
}
