package io.versaera.application;

import io.versaera.application.port.ProgressRepository;
import io.versaera.application.port.TxRunner;
import io.versaera.domain.common.GameClock;
import io.versaera.domain.event.EventBus;
import io.versaera.domain.event.GameEvents;
import io.versaera.domain.world.Region;
import io.versaera.domain.world.RegionIndex;

import java.util.Optional;

/**
 * 탐험 기록. 지역 · 랜드마크 · NPC · 몬스터 · 던전 · 레시피 등 무엇이든 "발견" 으로 남고, 서버 최초 발견자는 따로 기록된다.
 * 지도 UI 는 플레이어가 발견한 것만 보여 준다.
 */
public final class ExplorationService {
    public record Discovery(boolean isNew, boolean worldFirst) {}

    private final TxRunner tx;
    private final ProgressRepository progress;
    private final RegionIndex regions;
    private final GrowthService growth;
    private final EventBus bus;
    private final GameClock clock;

    public ExplorationService(TxRunner tx, ProgressRepository progress, RegionIndex regions, GrowthService growth, EventBus bus, GameClock clock) {
        this.tx = tx;
        this.progress = progress;
        this.regions = regions;
        this.growth = growth;
        this.bus = bus;
        this.clock = clock;
    }

    public RegionIndex regions() {
        return regions;
    }

    public Discovery discover(String uuid, String name, String kind, String ref) {
        Discovery d = tx.inTx(() -> {
            boolean isNew = progress.discover(uuid, kind, ref, clock.nowMillis());
            boolean first = isNew && progress.claimWorldFirst(kind, ref, uuid, name, clock.nowMillis());
            return new Discovery(isNew, first);
        });
        if (d.isNew()) {
            growth.record(uuid, "discover." + kind, 1);
            if ("region".equals(kind)) {
                Region r = regions.byId(ref);
                if (r != null) growth.addXp(uuid, "exploration", 40L + r.danger() * 30L, 1 + r.danger() * 5);
            }
            bus.publish(new GameEvents.PlayerDiscovered(uuid, kind, ref, d.worldFirst()));
        }
        return d;
    }

    /** 지역에 들어옴 (플랫폼이 블록 이동 때 지역이 바뀌면 부름) */
    public Discovery enterRegion(String uuid, String name, Region r) {
        return discover(uuid, name, "region", r.id());
    }

    public boolean discovered(String uuid, String kind, String ref) {
        return progress.discovered(uuid, kind, ref);
    }

    public Optional<ProgressRepository.WorldFirst> worldFirst(String kind, String ref) {
        return progress.worldFirst(kind, ref);
    }
}
