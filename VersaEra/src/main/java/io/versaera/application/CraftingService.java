package io.versaera.application;

import io.versaera.application.port.AuditLog;
import io.versaera.application.port.ProgressRepository;
import io.versaera.application.port.TxRunner;
import io.versaera.domain.common.DomainException;
import io.versaera.domain.crafting.CraftPlan;
import io.versaera.domain.crafting.MaterialInput;
import io.versaera.domain.crafting.Recipe;
import io.versaera.domain.event.EventBus;
import io.versaera.domain.event.GameEvents;
import io.versaera.domain.item.ItemInstance;
import io.versaera.domain.item.ItemType;
import io.versaera.domain.skill.Discipline;

import java.util.*;
import java.util.random.RandomGenerator;

/**
 * 제작 (대장 · 재봉 · 요리 · 연금 · 조각 · 목공 …). 결과는 고유 아이템(장비 · 도구 · 예술품)이거나 묶음(음식 · 시약).
 * 플랫폼은 재료를 인벤토리에서 먼저 빼고 이 메서드를 부른다. 규칙 위반으로 실패하면 재료를 배달함으로 돌려준다.
 */
public final class CraftingService {
    public record Result(int quality, String itemId, long xp, CraftPlan.Outcome outcome) {}

    private final TxRunner tx;
    private final Map<String, Recipe> recipes = new LinkedHashMap<>();
    private final ItemService items;
    private final GrowthService growth;
    private final ProgressRepository progress;
    private final AuditLog audit;
    private final EventBus bus;

    public CraftingService(TxRunner tx, Collection<Recipe> recipes, ItemService items, GrowthService growth, ProgressRepository progress,
                           AuditLog audit, EventBus bus) {
        this.tx = tx;
        for (Recipe r : recipes) {
            if (this.recipes.putIfAbsent(r.id(), r) != null) throw new IllegalArgumentException("레시피 id 중복: " + r.id());
            items.types().get(r.output());
            growth.discipline(r.discipline());
        }
        this.items = items;
        this.growth = growth;
        this.progress = progress;
        this.audit = audit;
        this.bus = bus;
    }

    public Recipe recipe(String id) {
        Recipe r = recipes.get(id);
        if (r == null) throw DomainException.of("recipe.unknown", "없는 레시피: " + id);
        return r;
    }

    public Collection<Recipe> all() {
        return Collections.unmodifiableCollection(recipes.values());
    }

    /** 이 플레이어가 아는 레시피 (발견 레시피는 발견한 뒤에만) */
    public boolean knows(String uuid, Recipe r) {
        return r.discovery() == null || progress.discovered(uuid, "recipe", r.id());
    }

    /**
     * @param extraProps 결과물에 붙일 값 (예: 조각품의 주제 · 제목). 키는 정해진 것만 받는다.
     */
    public Result craft(String uuid, String name, String recipeId, List<MaterialInput> inputs, int toolQuality, Map<String, String> extraProps,
                        RandomGenerator rng, String requestId) {
        boolean[] produced = {false};
        try {
            return doCraft(uuid, name, recipeId, inputs, toolQuality, extraProps, rng, requestId, produced);
        } catch (RuntimeException e) {
            // 결과물을 만들기 전에 실패했을 때만 재료를 돌려준다 (만든 뒤에 돌려주면 복제가 됨)
            if (!produced[0]) refund(uuid, inputs, "craft_failed:" + (e instanceof DomainException de ? de.code() : "error"));
            throw e;
        }
    }

    private Result doCraft(String uuid, String name, String recipeId, List<MaterialInput> inputs, int toolQuality, Map<String, String> extra,
                           RandomGenerator rng, String requestId, boolean[] produced) {
        Recipe r = recipe(recipeId);
        DomainException.require(knows(uuid, r), "craft.unknown_recipe", "아직 모르는 제작법입니다");
        int level = growth.level(uuid, r.discipline());
        int statPts = growth.discipline(r.discipline()).category() == Discipline.Category.ART ? growth.statPoints(uuid, "artistry") : 0;
        CraftPlan.Outcome o = CraftPlan.evaluate(r, inputs, level, toolQuality, statPts, rng);
        Map<String, String> props = new LinkedHashMap<>();
        StringBuilder mats = new StringBuilder();
        for (CraftPlan.Assignment a : o.used()) {
            if (mats.length() > 0) mats.append(',');
            mats.append(a.slot().role()).append(':').append(a.input().typeId()).append('@').append(a.input().quality());
        }
        props.put("materials", mats.toString());
        props.put("recipe", r.id());
        if (extra != null) for (Map.Entry<String, String> e : extra.entrySet()) {
            DomainException.require(Set.of("subject", "title").contains(e.getKey()), "craft.bad_prop", "허용하지 않는 속성: " + e.getKey());
            String v = e.getValue() == null ? "" : e.getValue().strip();
            DomainException.require(v.length() <= 32, "craft.bad_prop", "속성 값이 너무 깁니다");
            props.put(e.getKey(), v);
        }
        ItemType out = items.types().get(r.output());
        AfterCommit after = new AfterCommit();
        String itemId = tx.inTx(() -> {
            String id = null;
            if (out.category().unique()) {
                for (int i = 0; i < r.outputCount(); i++)
                    id = items.createInTx(out.id(), o.quality(), uuid, name, "craft:" + r.id(), props, uuid, requestId, after).id();
            } else {
                items.deliverBulk(uuid, out.id(), o.quality(), r.outputCount(), "craft:" + r.id());
            }
            audit.record("CRAFTED", uuid, r.id(), "q=" + o.quality() + " " + mats, requestId);
            return id;
        });
        produced[0] = true;
        after.publish(bus);
        GrowthService.XpResult xr = growth.addXp(uuid, r.discipline(), r.xp(), r.actionLevel());
        growth.record(uuid, "craft." + r.discipline(), 1);
        if (growth.discipline(r.discipline()).category() == Discipline.Category.ART) growth.record(uuid, "art.experience", 2);
        bus.publish(new GameEvents.PlayerCrafted(uuid, r.id(), itemId, o.quality()));
        return new Result(o.quality(), itemId, xr.gained(), o);
    }

    private void refund(String uuid, List<MaterialInput> inputs, String reason) {
        for (MaterialInput in : inputs) if (items.types().has(in.typeId())) items.deliverBulk(uuid, in.typeId(), in.quality(), in.count(), reason);
    }

    /** 고유 아이템 출력이 실제로 무엇인지 (UI 용) */
    public ItemType outputType(String recipeId) {
        return items.types().get(recipe(recipeId).output());
    }

    public Optional<ItemInstance> item(String id) {
        return items.find(id);
    }
}
