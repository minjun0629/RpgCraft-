package io.versaera.domain.item;

import io.versaera.domain.common.DomainException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 고유 아이템 하나. 같은 종류라도 품질 · 내구도 · 제작자 · 내력이 다르다.
 * version 은 DB 낙관적 잠금용 (저장할 때마다 +1).
 */
public final class ItemInstance {
    private final String id;
    private final String typeId;
    private final int quality;
    private int durability;
    private int maxDurability;
    private final int weight;
    private final String creatorUuid, creatorName, method;
    private final Map<String, String> props;
    private Custody custody;
    private final long createdAt;
    private long version;

    public ItemInstance(String id, String typeId, int quality, int durability, int maxDurability, int weight, String creatorUuid,
                        String creatorName, String method, Map<String, String> props, Custody custody, long createdAt, long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.typeId = Objects.requireNonNull(typeId, "typeId");
        DomainException.require(quality >= Quality.MIN && quality <= Quality.MAX, "item.bad_quality", "품질 범위 밖: " + quality);
        DomainException.require(maxDurability >= 0 && durability >= 0 && durability <= maxDurability, "item.bad_durability",
                "내구도가 잘못되었습니다: " + durability + "/" + maxDurability);
        DomainException.require(weight >= 0, "item.bad_weight", "무게는 음수일 수 없습니다");
        this.quality = quality;
        this.durability = durability;
        this.maxDurability = maxDurability;
        this.weight = weight;
        this.creatorUuid = creatorUuid;
        this.creatorName = creatorName;
        this.method = Objects.requireNonNull(method, "method");
        this.props = new LinkedHashMap<>(props == null ? Map.of() : props);
        this.custody = Objects.requireNonNull(custody, "custody");
        this.createdAt = createdAt;
        this.version = version;
    }

    public String id() { return id; }
    public String typeId() { return typeId; }
    public int quality() { return quality; }
    public int durability() { return durability; }
    public int maxDurability() { return maxDurability; }
    public int weight() { return weight; }
    public String creatorUuid() { return creatorUuid; }
    public String creatorName() { return creatorName; }
    public String method() { return method; }
    public Map<String, String> props() { return Collections.unmodifiableMap(props); }
    public Custody custody() { return custody; }
    public long createdAt() { return createdAt; }
    public long version() { return version; }

    public boolean broken() {
        return durability == 0;
    }

    /** 수리할 수 없을 만큼 망가짐 (CANON: 최대 내구도가 0 이면 더 고칠 수 없다) */
    public boolean ruined() {
        return maxDurability == 0;
    }

    public void custody(Custody c) {
        this.custody = Objects.requireNonNull(c);
    }

    /** 사용 · 피격으로 내구도 감소. 강한 충격(heavy)이면 최대 내구도도 1 줄어든다 (CANON 개념). */
    public void wear(int amount, boolean heavy) {
        DomainException.require(amount >= 0, "item.bad_wear", "내구도 감소량은 음수일 수 없습니다");
        durability = Math.max(0, durability - amount);
        if (heavy && maxDurability > 0) {
            maxDurability--;
            durability = Math.min(durability, maxDurability);
        }
    }

    void setDurability(int durability, int maxDurability) {
        this.maxDurability = maxDurability;
        this.durability = durability;
    }

    public void bumpVersion() {
        version++;
    }

    public void prop(String key, String value) {
        props.put(key, value);
    }
}
