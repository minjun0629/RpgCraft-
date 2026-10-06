package io.versaera.application;

import io.versaera.application.port.ProgressRepository;
import io.versaera.application.port.TxRunner;
import io.versaera.domain.common.DomainException;
import io.versaera.domain.common.GameClock;
import io.versaera.domain.event.EventBus;
import io.versaera.domain.event.GameEvents;
import io.versaera.domain.npc.NpcDefinition;
import io.versaera.domain.npc.Relation;

import java.time.Instant;
import java.time.ZoneId;
import java.util.*;

/** NPC 관계 (플레이어별). 대화 · 선물 · 의뢰로 오르내리고, 관계 단계가 상점 · 정보 · 숨은 콘텐츠를 연다. */
public final class RelationService {
    private final TxRunner tx;
    private final ProgressRepository progress;
    private final Map<String, NpcDefinition> npcs = new LinkedHashMap<>();
    private final GrowthService growth;
    private final EventBus bus;
    private final GameClock clock;
    private final ZoneId zone;

    public RelationService(TxRunner tx, ProgressRepository progress, Collection<NpcDefinition> npcs, GrowthService growth, EventBus bus,
                           GameClock clock, ZoneId zone) {
        this.tx = tx;
        this.progress = progress;
        for (NpcDefinition n : npcs) if (this.npcs.putIfAbsent(n.id(), n) != null) throw new IllegalArgumentException("NPC id 중복: " + n.id());
        this.growth = growth;
        this.bus = bus;
        this.clock = clock;
        this.zone = zone;
    }

    public NpcDefinition npc(String id) {
        NpcDefinition n = npcs.get(id);
        if (n == null) throw DomainException.of("npc.unknown", "없는 NPC: " + id);
        return n;
    }

    public Collection<NpcDefinition> all() {
        return Collections.unmodifiableCollection(npcs.values());
    }

    public int affinity(String uuid, String npcId) {
        return progress.relation(uuid, npcId).affinity();
    }

    private long day(long millis) {
        return Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toEpochDay();
    }

    /** @return 오른 호감 (같은 날 두 번째 대화부터는 0) */
    public int talk(String uuid, String npcId) {
        npc(npcId);
        long now = clock.nowMillis();
        int[] r = tx.inTx(() -> {
            ProgressRepository.RelationRow row = progress.relation(uuid, npcId);
            boolean first = row.lastTalk() == 0 || day(row.lastTalk()) != day(now);
            int gain = Relation.talkGain(row.affinity(), first);
            int na = Relation.clamp((long) row.affinity() + gain);
            progress.setRelation(uuid, npcId, na, now);
            return new int[]{na, gain};
        });
        if (r[1] != 0) {
            growth.record(uuid, "talk.npc", 1);
            bus.publish(new GameEvents.NpcRelationChanged(uuid, npcId, r[0], r[1]));
        }
        return r[1];
    }

    /** 선물 (아이템은 플랫폼이 먼저 소모) */
    public int gift(String uuid, String npcId, Set<String> itemTags, int quality) {
        NpcDefinition n = npc(npcId);
        int gain = Relation.giftGain(n, itemTags, quality);
        int na = tx.inTx(() -> {
            ProgressRepository.RelationRow row = progress.relation(uuid, npcId);
            int v = Relation.clamp((long) row.affinity() + gain);
            progress.setRelation(uuid, npcId, v, row.lastTalk());
            return v;
        });
        growth.record(uuid, "gift.npc", 1);
        bus.publish(new GameEvents.NpcRelationChanged(uuid, npcId, na, gain));
        return gain;
    }
}
