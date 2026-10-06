package io.versaera.domain.skill;

import io.versaera.domain.common.DomainException;

/**
 * 행동으로 오르는 스탯 (CANON 개념: 같은 행동을 반복하면 스탯이 오른다 · 조건을 만족하면 새 스탯이 생긴다).
 * 정의는 content/action_stats.yml. 수치 곡선은 ORIGINAL.
 *
 * @param counter   쌓이는 행동 기록 키 (예: "hit_taken", "art_appreciated")
 * @param per       첫 1 포인트에 필요한 횟수. n 번째 포인트는 n × per 가 더 필요하다 (점점 어려워짐)
 * @param unlockAt  이 횟수에 처음 닿아야 스탯이 "생긴다" (0 = 처음부터 있음)
 */
public record ActionStat(String id, String name, String counter, long per, long unlockAt, String effect, String source) {
    public ActionStat {
        DomainException.require(per > 0, "stat.bad_per", "per 는 0 보다 커야 합니다: " + id);
        DomainException.require(unlockAt >= 0, "stat.bad_unlock", "unlockAt 은 음수일 수 없습니다: " + id);
    }

    public boolean unlocked(long count) {
        return count >= unlockAt;
    }

    /** 누적 횟수 → 포인트. 1 + 2 + … + n 개의 per 가 필요 (삼각수) */
    public int points(long count) {
        if (!unlocked(count) || count <= 0) return 0;
        double k = count / (double) per;
        return (int) Math.floor((Math.sqrt(1 + 8 * k) - 1) / 2 + 1e-9);
    }

    /** 다음 포인트까지 남은 횟수 */
    public long remaining(long count) {
        int n = points(count) + 1;
        long need = per * (long) n * (n + 1) / 2;
        return Math.max(0, need - Math.max(count, 0));
    }
}
