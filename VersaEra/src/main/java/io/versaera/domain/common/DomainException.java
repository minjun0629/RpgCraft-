package io.versaera.domain.common;

/** 규칙 위반 (잘못된 입력 · 상태). code 는 로그 · 테스트 · UI 메시지 키로 쓴다. */
public class DomainException extends RuntimeException {
    private final String code;

    public DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static DomainException of(String code, String message) {
        return new DomainException(code, message);
    }

    public static void require(boolean ok, String code, String message) {
        if (!ok) throw new DomainException(code, message);
    }
}
