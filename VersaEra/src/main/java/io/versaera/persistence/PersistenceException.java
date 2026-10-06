package io.versaera.persistence;

/** DB 오류. 원인 SQL 문장(값 없이)을 함께 남겨 추적할 수 있게 한다. */
public final class PersistenceException extends RuntimeException {
    public PersistenceException(String sql, Throwable cause) {
        super("DB 오류 [" + sql.lines().findFirst().orElse("").strip() + "]: " + cause.getMessage(), cause);
    }
}
