package io.versaera.domain.npc;

import io.versaera.domain.common.DomainException;

import java.util.List;
import java.util.Set;

/**
 * NPC 정의 (content/npcs.yml). 퀘스트 지급기가 아니라 이름 · 직업 · 성격 · 소속 · 일과 · 취향을 가진 주민.
 *
 * @param schedule "06-12:market", "12-20:forge" 처럼 시간대(게임 시각) → 장소 키
 */
public record NpcDefinition(String id, String name, String job, String personality, String faction, String region,
                            Set<String> likes, Set<String> dislikes, List<String> schedule, String source) {
    public NpcDefinition {
        DomainException.require(id != null && id.matches("[a-z0-9_]+"), "npc.bad_id", "NPC id 형식이 잘못되었습니다: " + id);
        likes = Set.copyOf(likes == null ? Set.of() : likes);
        dislikes = Set.copyOf(dislikes == null ? Set.of() : dislikes);
        schedule = List.copyOf(schedule == null ? List.of() : schedule);
    }

    /** 게임 시각(0 ~ 23시)에 있는 장소 키 (일과가 없으면 null) */
    public String placeAt(int hour) {
        for (String s : schedule) {
            int c = s.indexOf(':'), d = s.indexOf('-');
            if (c < 0 || d < 0 || d > c) continue;
            int from = Integer.parseInt(s.substring(0, d)), to = Integer.parseInt(s.substring(d + 1, c));
            boolean in = from <= to ? hour >= from && hour < to : hour >= from || hour < to;
            if (in) return s.substring(c + 1);
        }
        return null;
    }
}
