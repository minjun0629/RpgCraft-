package io.versaera.application;

import io.versaera.application.port.ProgressRepository;
import io.versaera.application.port.TxRunner;
import io.versaera.domain.common.DomainException;
import io.versaera.domain.event.EventBus;
import io.versaera.domain.event.GameEvents;
import io.versaera.domain.skill.ActionStat;
import io.versaera.domain.skill.Discipline;
import io.versaera.domain.skill.Mastery;

import java.util.*;

/**
 * 숙련(분야별 경험치)과 행동 기록(카운터) → 행동 스탯. "무엇을 하든 기록이 남고 숙련이 쌓인다" 의 중심.
 */
public final class GrowthService {
    public static final String HAND_DISCIPLINE = "dexterity";

    public record XpResult(long gained, int before, int after) {
        public boolean levelUp() { return after > before; }
    }

    private final TxRunner tx;
    private final ProgressRepository progress;
    private final Map<String, Discipline> disciplines = new LinkedHashMap<>();
    private final Map<String, List<ActionStat>> statsByCounter = new HashMap<>();
    private final List<ActionStat> stats;
    private final EventBus bus;
    private final List<CounterListener> counterListeners = new ArrayList<>();

    /** 카운터가 바뀌면 알림 (히든 엔진 등) */
    @FunctionalInterface
    public interface CounterListener {
        void changed(String uuid, String key, long value);
    }

    public GrowthService(TxRunner tx, ProgressRepository progress, Collection<Discipline> disciplines, Collection<ActionStat> stats, EventBus bus) {
        this.tx = tx;
        this.progress = progress;
        for (Discipline d : disciplines) if (this.disciplines.putIfAbsent(d.id(), d) != null) throw new IllegalArgumentException("숙련 id 중복: " + d.id());
        this.stats = List.copyOf(stats);
        for (ActionStat s : stats) statsByCounter.computeIfAbsent(s.counter(), k -> new ArrayList<>()).add(s);
        this.bus = bus;
    }

    public void onCounter(CounterListener l) {
        counterListeners.add(l);
    }

    public Discipline discipline(String id) {
        Discipline d = disciplines.get(id);
        if (d == null) throw DomainException.of("discipline.unknown", "없는 숙련 분야: " + id);
        return d;
    }

    public Collection<Discipline> disciplines() {
        return Collections.unmodifiableCollection(disciplines.values());
    }

    public List<ActionStat> stats() {
        return stats;
    }

    public int level(String uuid, String discipline) {
        return Mastery.levelOf(progress.masteryXp(uuid, discipline));
    }

    public long xp(String uuid, String discipline) {
        return progress.masteryXp(uuid, discipline);
    }

    /**
     * 행동 하나의 숙련 경험치. 너무 쉬운 행동은 거의 오르지 않는다 (Mastery.gain).
     * 손을 쓰는 분야면 손재주 숙련이 속도를 높여 준다 (CANON 개념).
     */
    public XpResult addXp(String uuid, String discipline, long base, int actionLevel) {
        Discipline d = discipline(discipline);
        XpResult r = tx.inTx(() -> {
            long xp = progress.masteryXp(uuid, discipline);
            int before = Mastery.levelOf(xp);
            double hand = d.hand() && disciplines.containsKey(HAND_DISCIPLINE) ? 1 + Mastery.levelOf(progress.masteryXp(uuid, HAND_DISCIPLINE)) * 0.015 : 1;
            long g = Mastery.gain(base, actionLevel, before, hand);
            if (g == 0) return new XpResult(0, before, before);
            long cap = Mastery.cumulative(Mastery.MAX_LEVEL);
            long nx = Math.min(cap, xp + g);
            progress.setMasteryXp(uuid, discipline, nx);
            return new XpResult(nx - xp, before, Mastery.levelOf(nx));
        });
        if (r.levelUp()) bus.publish(new GameEvents.MasteryLevelUp(uuid, discipline, r.after(), Mastery.tierOf(r.after()) > Mastery.tierOf(r.before())));
        return r;
    }

    /** 행동 기록 +delta. 그 기록에 걸린 행동 스탯이 오르면 이벤트. @return 새 값 */
    public long record(String uuid, String key, long delta) {
        DomainException.require(delta > 0 && delta <= 1_000_000, "counter.bad_delta", "기록 증가량이 잘못되었습니다: " + delta);
        long[] beforeAfter = tx.inTx(() -> {
            long before = progress.counter(uuid, key);
            long after = progress.addCounter(uuid, key, delta);
            return new long[]{before, after};
        });
        for (ActionStat s : statsByCounter.getOrDefault(key, List.of())) {
            int pb = s.points(beforeAfter[0]), pa = s.points(beforeAfter[1]);
            boolean unlocked = !s.unlocked(beforeAfter[0]) && s.unlocked(beforeAfter[1]);
            if (pa > pb || unlocked) bus.publish(new GameEvents.StatGained(uuid, s.id(), pa, unlocked));
        }
        for (CounterListener l : counterListeners) l.changed(uuid, key, beforeAfter[1]);
        return beforeAfter[1];
    }

    public long counter(String uuid, String key) {
        return progress.counter(uuid, key);
    }

    /** 행동 스탯 id → 포인트 (아직 생기지 않은 스탯은 빠짐) */
    public Map<String, Integer> statPoints(String uuid) {
        Map<String, Long> c = progress.allCounters(uuid);
        Map<String, Integer> out = new LinkedHashMap<>();
        for (ActionStat s : stats) {
            long v = c.getOrDefault(s.counter(), 0L);
            if (s.unlocked(v)) out.put(s.id(), s.points(v));
        }
        return out;
    }

    public int statPoints(String uuid, String statId) {
        for (ActionStat s : stats) if (s.id().equals(statId)) return s.points(progress.counter(uuid, s.counter()));
        return 0;
    }
}
