package io.versaera.persistence;

import io.versaera.application.port.WalletRepository;

public final class JdbcWalletRepository implements WalletRepository {
    private final Jdbc j;

    public JdbcWalletRepository(Database db) {
        this.j = new Jdbc(db);
    }

    @Override
    public long balance(String uuid) {
        return j.one("SELECT balance FROM wallet WHERE uuid = ?", ps -> ps.setString(1, uuid), rs -> rs.getLong(1), 0L);
    }

    @Override
    public void setBalance(String uuid, long balance) {
        j.update("INSERT INTO wallet (uuid, balance) VALUES (?, ?) ON CONFLICT (uuid) DO UPDATE SET balance = excluded.balance", ps -> {
            ps.setString(1, uuid);
            ps.setLong(2, balance);
        });
    }

    @Override
    public boolean ledgerExists(String key) {
        return key != null && j.one("SELECT 1 FROM ledger WHERE idempotency_key = ?", ps -> ps.setString(1, key), rs -> true, false);
    }

    @Override
    public void ledger(String from, String to, long amount, String reason, String key, long at) {
        j.update("INSERT INTO ledger (from_uuid, to_uuid, amount, reason, idempotency_key, created_at) VALUES (?, ?, ?, ?, ?, ?)", ps -> {
            ps.setString(1, from);
            ps.setString(2, to);
            ps.setLong(3, amount);
            ps.setString(4, reason);
            ps.setString(5, key);
            ps.setLong(6, at);
        });
    }
}
