package io.versaera.domain.hidden;

/** 히든 조건 판정에 쓰는 플레이어 사실 (서비스가 DB · 캐시에서 채워 넘김) */
public interface PlayerFacts {
    long counter(String key);

    int mastery(String discipline);

    int affinity(String npcId);

    String region();

    boolean discovered(String kind, String ref);

    /** 게임 시각 0 ~ 23 */
    int hour();
}
