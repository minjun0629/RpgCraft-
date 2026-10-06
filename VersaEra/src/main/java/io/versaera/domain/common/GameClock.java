package io.versaera.domain.common;

/** 현재 시각 (테스트에서 바꿔 끼움) */
@FunctionalInterface
public interface GameClock {
    long nowMillis();

    GameClock SYSTEM = System::currentTimeMillis;
}
