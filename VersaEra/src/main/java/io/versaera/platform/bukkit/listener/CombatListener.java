package io.versaera.platform.bukkit.listener;

import io.versaera.application.GameServices;
import io.versaera.domain.combat.DamageCalculator;
import io.versaera.domain.item.ItemInstance;
import io.versaera.domain.item.ItemType;
import io.versaera.domain.skill.Mastery;
import io.versaera.platform.bukkit.Async;
import io.versaera.platform.bukkit.binding.ItemCodec;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.random.RandomGenerator;

/**
 * 전투 (CMB-01 기본형). 무기 · 방어구는 고유 아이템 값으로 계산하고, 결과는 매 타격 DB 에 쓰지 않고 모아서 쓴다.
 * <ul>
 *   <li>공격: 손에 든 고유 무기의 공격력 · 품질 · 무기 숙련 → 피해. 등 뒤(약점)는 1.5배.</li>
 *   <li>방어: 입은 고유 방어구의 방어력 합 → 피해 감소. 방패로 막으면 크게 감소.</li>
 *   <li>기록: 맞은 횟수(인내) · 무기 숙련 경험치 · 무기 내구도 마모를 5초마다 한 번에 저장.</li>
 * </ul>
 * 스킬 · 콤보 · 회피 · 상태 이상은 CMB-02 (PLANNED).
 */
public final class CombatListener implements Listener {
    /** 메인 스레드에서 쓰는 캐시: 아이템 id → (종류, 품질). DB 에서 한 번 읽어 둔다. */
    private record Cached(String typeId, int quality) {}

    private final GameServices s;
    private final Async async;
    private final ItemCodec codec;
    private final Map<String, Cached> items = new ConcurrentHashMap<>();
    private final Map<String, Integer> weaponMastery = new ConcurrentHashMap<>();
    private final Map<String, Long> pendingHits = new ConcurrentHashMap<>(), pendingXp = new ConcurrentHashMap<>();
    private final Map<String, String> pendingXpDiscipline = new ConcurrentHashMap<>();
    private final Map<String, Integer> pendingWear = new ConcurrentHashMap<>();
    private final Map<String, String> wearOwner = new ConcurrentHashMap<>();
    private final RandomGenerator rng = RandomGenerator.getDefault();

    public CombatListener(Plugin plugin, GameServices s, Async async, ItemCodec codec) {
        this.s = s;
        this.async = async;
        this.codec = codec;
        Bukkit.getScheduler().runTaskTimer(plugin, this::flush, 100L, 100L);
    }

    private static String disciplineOf(ItemType t) {
        if (t.hasTag("sword") || t.hasTag("dagger")) return "swordsmanship";
        if (t.hasTag("spear")) return "spearmanship";
        if (t.hasTag("bow")) return "archery";
        return null;
    }

    /** 처음 보는 아이템은 이번 타격에는 기본값으로 계산하고, DB 에서 읽어 다음부터 정확히 */
    private Cached lookup(String itemId, String owner) {
        Cached c = items.get(itemId);
        if (c != null) return c;
        async.fire("combat-cache", () -> {
            s.items.find(itemId).filter(it -> it.custody().ownedBy(owner)).ifPresent(it -> items.put(itemId, new Cached(it.typeId(), it.quality())));
            return null;
        });
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof LivingEntity victim)) return;
        double damage = e.getDamage();
        if (e.getDamager() instanceof Player attacker) {
            ItemStack hand = attacker.getInventory().getItemInMainHand();
            String iid = codec.instanceId(hand), owner = attacker.getUniqueId().toString();
            Cached w = iid == null ? null : lookup(iid, owner);
            if (w != null) {
                ItemType t = codec.types().get(w.typeId());
                String d = disciplineOf(t);
                int lv = d == null ? 1 : weaponMastery.getOrDefault(owner + ":" + d, 1);
                boolean back = victim.getLocation().getDirection().setY(0).normalize()
                        .dot(attacker.getLocation().toVector().subtract(victim.getLocation().toVector()).setY(0).normalize()) < -0.5;
                double attack = t.stats().getOrDefault("attack", 0) * Math.max(0.2, attacker.getAttackCooldown());
                damage = DamageCalculator.compute(new DamageCalculator.Attack(attack, w.quality(), lv, 0.05, 1.5, back),
                        new DamageCalculator.Defense(0, false, false), rng).damage();
                if (d != null) {
                    pendingXp.merge(owner + ":" + d, 1L, Long::sum);
                    pendingXpDiscipline.put(owner + ":" + d, d);
                }
                pendingWear.merge(iid, 1, Integer::sum);
                wearOwner.put(iid, owner);
            }
        }
        if (victim instanceof Player defender) {
            String owner = defender.getUniqueId().toString();
            double armor = 0;
            for (ItemStack it : defender.getInventory().getArmorContents()) {
                String iid = codec.instanceId(it);
                Cached c = iid == null ? null : lookup(iid, owner);
                if (c != null) armor += codec.types().get(c.typeId()).stats().getOrDefault("defense", 0)
                        * io.versaera.domain.item.Quality.statMultiplier(c.quality());
            }
            damage = damage * 100.0 / (100.0 + armor * 4);
            if (defender.isBlocking()) damage *= 0.4;
            pendingHits.merge(owner, 1L, Long::sum);
        }
        e.setDamage(Math.max(0.5, damage));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent e) {
        Player k = e.getEntity().getKiller();
        if (k == null || e.getEntity() instanceof Player) return;
        String id = k.getUniqueId().toString();
        async.fire("kill", () -> s.growth.record(id, "kill.monster", 1));
    }

    /** 5초마다 모아서 저장 (전투 중 매 타격 DB 쓰기를 피함) */
    private void flush() {
        Map<String, Long> hits = drain(pendingHits), xp = drain(pendingXp);
        Map<String, Integer> wear = drain(pendingWear);
        Map<String, String> xpDisc = new HashMap<>(pendingXpDiscipline), owners = new HashMap<>(wearOwner);
        if (hits.isEmpty() && xp.isEmpty() && wear.isEmpty()) return;
        async.fire("combat-flush", () -> {
            for (var h : hits.entrySet()) s.growth.record(h.getKey(), "hit_taken", h.getValue());
            for (var x : xp.entrySet()) {
                String uuid = x.getKey().substring(0, x.getKey().indexOf(':'));
                String d = xpDisc.get(x.getKey());
                var r = s.growth.addXp(uuid, d, 3 * x.getValue(), 1);
                weaponMastery.put(x.getKey(), r.after());
            }
            for (var w : wear.entrySet()) {
                String owner = owners.get(w.getKey());
                try {
                    ItemInstance it = s.items.wear(w.getKey(), owner, (w.getValue() + 9) / 10, false);   // 10번 칠 때마다 1
                    if (it.broken()) items.remove(w.getKey());
                } catch (io.versaera.domain.common.DomainException ex) {
                    items.remove(w.getKey());   // 그 사이 거래 · 파괴됨 → 캐시에서 지움 (다음 검사에서 InventoryGuard 가 처리)
                }
            }
            return null;
        });
    }

    private static <V> Map<String, V> drain(Map<String, V> m) {
        Map<String, V> out = new HashMap<>();
        for (String k : new ArrayList<>(m.keySet())) {
            V v = m.remove(k);
            if (v != null) out.put(k, v);
        }
        return out;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        String prefix = e.getPlayer().getUniqueId() + ":";
        weaponMastery.keySet().removeIf(k -> k.startsWith(prefix));
    }

    /** 처음 접속 시 무기 숙련 캐시 (DB 스레드에서 부름) */
    public void warm(String uuid) {
        for (String d : List.of("swordsmanship", "spearmanship", "archery")) weaponMastery.put(uuid + ":" + d, Mastery.levelOf(s.growth.xp(uuid, d)));
    }
}
