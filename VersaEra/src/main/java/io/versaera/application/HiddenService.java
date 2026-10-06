package io.versaera.application;

import io.versaera.application.port.ProgressRepository;
import io.versaera.application.port.TxRunner;
import io.versaera.domain.common.GameClock;
import io.versaera.domain.event.EventBus;
import io.versaera.domain.event.GameEvents;
import io.versaera.domain.hidden.HiddenRule;
import io.versaera.domain.hidden.PlayerFacts;

import java.util.*;
import java.util.function.Function;

/**
 * 히든 콘텐츠 판정 (규칙은 봉인 파일에서 읽어 메모리에만 둔다).
 * 카운터가 바뀌면 그 카운터를 쓰는 규칙만 다시 판정하고, 지역 이동 · 시간 변화 때는 전체를 판정한다.
 * 처음 발견하면 서버 최초 발견자로 기록되고, 다른 플레이어에게는 소문(실마리 한 줄)만 퍼진다.
 */
public final class HiddenService {
    private final TxRunner tx;
    private final ProgressRepository progress;
    private final EventBus bus;
    private final GameClock clock;
    private final List<HiddenRule> rules;
    private final Map<String, List<HiddenRule>> byCounter = new HashMap<>();
    private final Function<String, PlayerFacts> facts;

    public HiddenService(TxRunner tx, ProgressRepository progress, Collection<HiddenRule> rules, Function<String, PlayerFacts> facts,
                         EventBus bus, GameClock clock) {
        this.tx = tx;
        this.progress = progress;
        this.rules = List.copyOf(rules);
        for (HiddenRule r : rules) for (String k : r.counterKeys()) byCounter.computeIfAbsent(k, x -> new ArrayList<>()).add(r);
        this.facts = facts;
        this.bus = bus;
        this.clock = clock;
    }

    public int ruleCount() {
        return rules.size();
    }

    /** GrowthService.onCounter 에 연결 */
    public void counterChanged(String uuid, String key, long value) {
        List<HiddenRule> l = byCounter.get(key);
        if (l != null) check(uuid, l);
    }

    /** 지역 이동 · 접속 · 주기적 확인 */
    public void checkAll(String uuid) {
        check(uuid, rules);
    }

    private void check(String uuid, List<HiddenRule> list) {
        PlayerFacts f = null;
        for (HiddenRule r : list) {
            if (progress.hiddenUnlocked(uuid, r.id())) continue;
            if (f == null) f = facts.apply(uuid);
            if (!r.when().test(f)) continue;
            unlock(uuid, r);
        }
    }

    private void unlock(String uuid, HiddenRule r) {
        long now = clock.nowMillis();
        boolean[] first = {false};
        boolean fresh = tx.inTx(() -> {
            if (!progress.unlockHidden(uuid, r.id(), now)) return false;
            first[0] = progress.claimWorldFirst("hidden", r.id(), uuid, uuid, now);
            if (r.reward().containsKey("recipe")) progress.discover(uuid, "recipe", r.reward().get("recipe"), now);
            return true;
        });
        if (fresh) bus.publish(new GameEvents.HiddenUnlocked(uuid, r.id(), first[0], r.rumor()));
    }

    public Optional<HiddenRule> rule(String id) {
        return rules.stream().filter(r -> r.id().equals(id)).findFirst();
    }
}
