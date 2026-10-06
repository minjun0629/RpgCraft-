package io.versaera.domain.boss;

import java.util.*;

/**
 * 보스 전투 상태 기계 (순수 계산). 플랫폼 계층이 0.25초마다 update 를 부르고, 돌려받은 행동을 화면 · 피해로 바꾼다.
 * 패턴 선택은 확률이 아니라 "쓸 수 있는 패턴을 차례대로" — 플레이어가 배워서 대응할 수 있게.
 */
public final class BossFight {
    public sealed interface Action {
        record PhaseChanged(int phase, String announce) implements Action {}

        record Telegraph(String pattern, Vec origin, double yaw, long resolveAt) implements Action {}

        record Resolve(String pattern, List<UUID> hit, double damage, String effect) implements Action {}

        record Enraged() implements Action {}
    }

    private record Pending(BossDefinition.Pattern pattern, Vec origin, double yaw, long resolveAt) {}

    private final BossDefinition def;
    private final long startedAt;
    private final Map<String, Long> readyAt = new HashMap<>();
    private final List<Pending> pending = new ArrayList<>();
    private int phase;
    private int cursor;
    private boolean enraged;
    private long busyUntil;

    public BossFight(BossDefinition def, long now) {
        this.def = def;
        this.startedAt = now;
    }

    public int phase() {
        return phase;
    }

    public boolean enraged() {
        return enraged;
    }

    public double scaled(double v) {
        return v * def.scale();
    }

    /**
     * @param hpRatio  지금 체력 비율 0 ~ 1
     * @param pos      보스 위치 · yaw
     * @param targets  전투 공간 안의 플레이어 위치
     */
    public List<Action> update(long now, double hpRatio, Vec pos, double yaw, Map<UUID, Vec> targets) {
        List<Action> out = new ArrayList<>();
        int want = 0;
        for (int i = 0; i < def.phases().size(); i++) if (hpRatio <= def.phases().get(i).hpBelow()) want = i;
        if (want > phase) {
            phase = want;
            cursor = 0;
            out.add(new Action.PhaseChanged(phase, def.phases().get(phase).announce()));
        }
        if (!enraged && def.enrageMs() > 0 && now - startedAt >= def.enrageMs()) {
            enraged = true;
            out.add(new Action.Enraged());
        }
        for (Iterator<Pending> it = pending.iterator(); it.hasNext(); ) {
            Pending p = it.next();
            if (now < p.resolveAt()) continue;
            it.remove();
            BossDefinition.Pattern pt = p.pattern();
            List<UUID> hit = new ArrayList<>();
            for (Map.Entry<UUID, Vec> t : targets.entrySet())
                if (pt.shape().hits(p.origin(), p.yaw(), t.getValue(), scaled(pt.radius()), scaled(pt.inner()),
                        pt.shape() == Shape.CONE ? pt.widthOrAngle() : scaled(pt.widthOrAngle()), scaled(pt.height())))
                    hit.add(t.getKey());
            out.add(new Action.Resolve(pt.id(), hit, pt.damage() * (enraged ? 1.3 : 1), pt.effect()));
        }
        if (now >= busyUntil && !targets.isEmpty()) {
            List<String> list = def.phases().get(phase).patterns();
            for (int i = 0; i < list.size(); i++) {
                String id = list.get((cursor + i) % list.size());
                if (readyAt.getOrDefault(id, 0L) > now) continue;
                BossDefinition.Pattern pt = def.patterns().get(id);
                long wait = enraged ? pt.cooldownMs() / 2 : pt.cooldownMs();
                readyAt.put(id, now + pt.telegraphMs() + wait);
                long resolve = now + pt.telegraphMs();
                pending.add(new Pending(pt, pos, yaw, resolve));
                out.add(new Action.Telegraph(id, pos, yaw, resolve));
                busyUntil = resolve;   // 한 번에 하나씩: 예고가 끝날 때까지 다음 패턴을 시작하지 않음
                cursor = (cursor + i + 1) % list.size();
                break;
            }
        }
        return out;
    }

    /** 공격자가 보스 등 뒤 약점 각도 안에 있으면 true */
    public boolean weakPoint(Vec boss, double yaw, Vec attacker) {
        double dx = attacker.x() - boss.x(), dz = attacker.z() - boss.z();
        double d = Math.hypot(dx, dz);
        if (d < 1e-6) return false;
        double[] f = Vec.facing(yaw);
        double angleFromBack = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, -(dx * f[0] + dz * f[1]) / d))));
        return angleFromBack <= def.weakArc() / 2;
    }
}
