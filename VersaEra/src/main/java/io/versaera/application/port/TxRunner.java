package io.versaera.application.port;

/** 트랜잭션 하나 안에서 실행. 예외가 나면 그 안의 모든 쓰기가 되돌려진다. */
public interface TxRunner {
    @FunctionalInterface
    interface Work<T> {
        T run() throws Exception;
    }

    <T> T inTx(Work<T> work);
}
