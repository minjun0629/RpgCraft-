package io.versaera.domain.item;

import io.versaera.domain.common.DomainException;

import java.util.Map;
import java.util.Set;

/**
 * 아이템 종류 정의 (content/items.yml). 실제 아이템 하나하나는 {@link ItemInstance}.
 *
 * @param material   보여 줄 Minecraft 재질 이름 (Bukkit 없이 문자열로)
 * @param stats      공격력 · 방어력 등 (품질에 따라 배율이 붙음)
 * @param requires   착용 · 사용 조건 (예: "level" → 10, "mastery.smithing" → 12)
 */
public record ItemType(String id, String name, ItemCategory category, String material, int baseDurability, int weight,
                       Set<String> tags, Map<String, Integer> stats, Map<String, Integer> requires, String source) {
    public ItemType {
        DomainException.require(id != null && id.matches("[a-z0-9_.]+"), "item.bad_id", "아이템 id 형식이 잘못되었습니다: " + id);
        DomainException.require(baseDurability >= 0 && weight >= 0, "item.bad_numbers", "내구도 · 무게는 음수일 수 없습니다: " + id);
        tags = Set.copyOf(tags == null ? Set.of() : tags);
        stats = Map.copyOf(stats == null ? Map.of() : stats);
        requires = Map.copyOf(requires == null ? Map.of() : requires);
    }

    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }
}
