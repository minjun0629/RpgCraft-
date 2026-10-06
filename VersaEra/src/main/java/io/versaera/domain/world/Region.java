package io.versaera.domain.world;

import io.versaera.domain.common.DomainException;

import java.util.List;
import java.util.Set;

/**
 * 지역 (content/regions.yml). 상자 영역 하나로 정의하고, 겹치면 priority 가 높은(더 작은 · 구체적인) 지역이 이긴다.
 *
 * @param danger   위험도 0 ~ 6 (02_WORLD §3)
 * @param purpose  이 지역이 존재하는 이유 (설계 검증용 — 비면 로드 실패)
 * @param changed  원작 이후 무엇이 바뀌었나 (원작 지명이면 필수)
 */
public record Region(String id, String name, String source, int danger, String world, int minX, int minY, int minZ, int maxX, int maxY,
                     int maxZ, int priority, String parent, Set<String> tags, String purpose, String changed, List<String> resources,
                     List<String> factions) {
    public Region {
        DomainException.require(id != null && id.matches("[a-z0-9_]+"), "region.bad_id", "지역 id 형식이 잘못되었습니다: " + id);
        DomainException.require(danger >= 0 && danger <= 6, "region.bad_danger", "위험도는 0 ~ 6: " + id);
        DomainException.require(minX <= maxX && minY <= maxY && minZ <= maxZ, "region.bad_bounds", "영역이 잘못되었습니다: " + id);
        DomainException.require(purpose != null && !purpose.isBlank(), "region.no_purpose", "존재 이유(purpose)가 없습니다: " + id);
        DomainException.require(!"CANON".equals(source) || (changed != null && !changed.isBlank()), "region.no_change",
                "원작 지명은 후대의 변화(changed)를 적어야 합니다: " + id);
        tags = Set.copyOf(tags == null ? Set.of() : tags);
        resources = List.copyOf(resources == null ? List.of() : resources);
        factions = List.copyOf(factions == null ? List.of() : factions);
    }

    public boolean contains(String w, int x, int y, int z) {
        return world.equals(w) && x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }
}
