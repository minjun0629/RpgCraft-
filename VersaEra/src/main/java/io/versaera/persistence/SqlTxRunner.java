package io.versaera.persistence;

import io.versaera.application.port.TxRunner;

import java.sql.SQLException;

public final class SqlTxRunner implements TxRunner {
    private final Database db;

    public SqlTxRunner(Database db) {
        this.db = db;
    }

    @Override
    public <T> T inTx(Work<T> work) {
        try {
            return db.tx(c -> {
                try {
                    return work.run();
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    throw new TxFailure(e);
                }
            });
        } catch (TxFailure f) {
            throw new IllegalStateException(f.getCause().getMessage(), f.getCause());
        } catch (SQLException e) {
            throw new PersistenceException("transaction", e);
        }
    }

    private static final class TxFailure extends RuntimeException {
        TxFailure(Throwable cause) {
            super(cause);
        }
    }
}
