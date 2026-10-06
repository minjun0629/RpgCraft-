package io.versaera.domain.common;

/** 돈 금액 검사. 돈은 long(동화 단위)으로만 다루고, 0 이하 · 상한 초과 요청은 거부한다. */
public final class Money {
    /** 한 번에 움직일 수 있는 최대 금액 (오버플로 · 악용 방지) */
    public static final long MAX_TRANSFER = 1_000_000_000_000L;
    /** 지갑 최대 잔액 */
    public static final long MAX_BALANCE = 9_000_000_000_000_000L;

    private Money() {
    }

    public static long requirePositive(long amount) {
        if (amount <= 0) throw DomainException.of("money.non_positive", "금액은 0 보다 커야 합니다: " + amount);
        if (amount > MAX_TRANSFER) throw DomainException.of("money.too_large", "한 번에 움직일 수 있는 금액을 넘었습니다: " + amount);
        return amount;
    }

    public static long requireNonNegative(long amount) {
        if (amount < 0) throw DomainException.of("money.negative", "금액은 음수일 수 없습니다: " + amount);
        if (amount > MAX_TRANSFER) throw DomainException.of("money.too_large", "한 번에 움직일 수 있는 금액을 넘었습니다: " + amount);
        return amount;
    }

    public static long add(long balance, long amount) {
        if (balance > MAX_BALANCE - amount) throw DomainException.of("money.overflow", "잔액 상한을 넘습니다");
        return balance + amount;
    }
}
