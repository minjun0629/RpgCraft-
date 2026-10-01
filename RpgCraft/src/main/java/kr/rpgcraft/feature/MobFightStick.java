package kr.rpgcraft.feature;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * v5.10.45 관리자용 "몬스터 결투 막대기" (/rpg관리 싸움막대) — 몬스터 두 마리를 차례로 좌클릭하면 서로 싸움.
 *  (평소엔 몬스터끼리 노리거나 때리지 않게 막혀 있으므로, 싸움 중인 짝만 예외로 풀어 줌)
 *  쉬프트 + 좌클릭: 고른 몬스터 취소. 한쪽이 죽거나 2분이 지나면 끝.
 *  v5.10.53 승부는 능력치로: 1초마다 서로 (공격력 × (내 레벨 / 상대 레벨)²) 만큼 — 체력 · 공격력 · 레벨이 높은 쪽이 반드시 이김.
 *  (평소 몹끼리 치고받는 피해는 막음 → 공격 속도 · AI 차이로 약한 보스가 이기던 문제) 빠른 쪽이 약 12초에 끝내도록 전체 배율만 맞춤.
 */
public class MobFightStick implements Listener {
    private final RpgCraft plugin;
    private final NamespacedKey KEY;
    private final Map<UUID, UUID> picked = new HashMap<>();
    /** 몬스터 → 싸울 상대 */
    private static final Map<UUID, UUID> FIGHTS = new HashMap<>();
    private static final Map<UUID, Long> UNTIL = new HashMap<>();
    /** 몬스터 → 1초마다 상대에게 주는 피해 */
    private static final Map<UUID, Double> DPS = new HashMap<>();

    public MobFightStick(RpgCraft plugin) {
        this.plugin = plugin;
        this.KEY = new NamespacedKey(plugin, "mobfight_stick");
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public ItemStack stick() {
        ItemStack it = new ItemStack(Material.BLAZE_ROD);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(Text.c("&c&l몬스터 결투 막대기 &7(관리자)"));
        m.setLore(List.of(Text.c("&7몬스터 두 마리를 차례로 좌클릭하면 서로 싸웁니다"), Text.c("&7쉬프트 + 좌클릭: 고른 몬스터 취소")));
        m.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    private boolean isStick(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(KEY, PersistentDataType.BYTE);
    }

    /** 이 몬스터가 결투 막대기로 붙인 싸움 중인지 (보스 AI 가 플레이어로 눈을 돌리지 않게) */
    public static boolean isFighting(Entity e) {
        return e != null && FIGHTS.containsKey(e.getUniqueId());
    }

    /** 이 두 몬스터가 지금 서로 싸우는 중인지 (CombatListener 의 몬스터끼리 막기 예외) */
    public static boolean fighting(Entity a, Entity b) {
        if (a == null || b == null) return false;
        return b.getUniqueId().equals(FIGHTS.get(a.getUniqueId())) || a.getUniqueId().equals(FIGHTS.get(b.getUniqueId()));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p) || !isStick(p.getInventory().getItemInMainHand())) return;
        e.setCancelled(true);
        if (!p.hasPermission("rpgcraft.admin")) return;
        if (!(e.getEntity() instanceof Mob mob)) { Text.actionBar(p, "&c몬스터만 고를 수 있습니다."); return; }
        if (p.isSneaking()) {
            picked.remove(p.getUniqueId());
            Text.actionBar(p, "&7고른 몬스터를 취소했습니다.");
            return;
        }
        UUID first = picked.get(p.getUniqueId());
        Entity a = first == null ? null : Bukkit.getEntity(first);
        if (a == null || !a.isValid() || a.equals(mob)) {
            picked.put(p.getUniqueId(), mob.getUniqueId());
            mob.getWorld().spawnParticle(Particle.VILLAGER_ANGRY, mob.getLocation().add(0, mob.getHeight() + 0.3, 0), 3, 0.2, 0.1, 0.2, 0);
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.4f);
            Text.actionBar(p, "&e첫 번째: &f" + name(mob) + " &7— 싸울 상대를 좌클릭하세요");
            return;
        }
        picked.remove(p.getUniqueId());
        start((Mob) a, mob);
        Text.msg(p, "&c⚔ &f" + name((LivingEntity) a) + " &7vs &f" + name(mob) + " &c싸움 시작!");
    }

    private String name(LivingEntity le) {
        var s = plugin.mobs().peek(le);
        return s != null && s.baseName != null ? Text.strip(Text.c("Lv." + s.level + " " + s.baseName)) : le.getType().name().toLowerCase(Locale.ROOT);
    }

    private void start(Mob a, Mob b) {
        long until = System.currentTimeMillis() + plugin.getConfig().getLong("admin.mob-fight-seconds", 120) * 1000;
        FIGHTS.put(a.getUniqueId(), b.getUniqueId());
        FIGHTS.put(b.getUniqueId(), a.getUniqueId());
        UNTIL.put(a.getUniqueId(), until);
        UNTIL.put(b.getUniqueId(), until);
        for (Mob m : new Mob[]{a, b}) {
            if (!m.hasAI()) m.setAI(true);
            m.getWorld().spawnParticle(Particle.FLAME, m.getLocation().add(0, m.getHeight() / 2, 0), 20, 0.3, 0.4, 0.3, 0.02);
        }
        a.getWorld().playSound(a.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.6f);
        a.setTarget(b);
        b.setTarget(a);
        // 능력치 대결: 공격력 × 레벨 비율² · 빠른 쪽이 약 12초에 끝나게 배율
        double hpA = plugin.health().max(a), hpB = plugin.health().max(b);
        double dA = Math.max(1, plugin.combat().mobDamage(a)), dB = Math.max(1, plugin.combat().mobDamage(b));
        double lA = Math.max(1, level(a)), lB = Math.max(1, level(b));
        double r = Math.max(0.1, Math.min(10, Math.pow(lA / lB, 2)));
        double effA = dA * r, effB = dB / r;
        double tA = hpB / effA, tB = hpA / effB;   // A 가 B 를 쓰러뜨리는 데 걸리는 초 · 반대
        double k = Math.min(tA, tB) / plugin.getConfig().getDouble("admin.mob-fight-seconds-to-win", 12);
        DPS.put(a.getUniqueId(), effA * k);
        DPS.put(b.getUniqueId(), effB * k);
    }

    private int level(LivingEntity le) {
        var s = plugin.mobs().peek(le);
        return s == null ? 1 : s.level;
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (UUID id : new ArrayList<>(FIGHTS.keySet())) {
            Entity me = Bukkit.getEntity(id), foe = Bukkit.getEntity(FIGHTS.get(id));
            boolean done = !(me instanceof Mob m) || !m.isValid() || m.isDead() || !(foe instanceof LivingEntity f) || !f.isValid() || f.isDead()
                    || UNTIL.getOrDefault(id, 0L) < now;
            if (done) {
                FIGHTS.remove(id);
                UNTIL.remove(id);
                DPS.remove(id);
                if (me instanceof Mob m2 && m2.isValid() && foe != null && foe.equals(m2.getTarget())) m2.setTarget(null);
                continue;
            }
            Mob mm = (Mob) me;
            if (mm.getTarget() == null || !mm.getTarget().equals(foe)) mm.setTarget((LivingEntity) foe);   // 다른 데로 눈 돌리지 않게
            // 1초마다 능력치대로 피해 (서로 붙어 있을 때 · 12칸 안)
            LivingEntity f = (LivingEntity) foe;
            if (mm.getLocation().distanceSquared(f.getLocation()) > 24 * 24) continue;
            double dmg = DPS.getOrDefault(id, 0.0) * java.util.concurrent.ThreadLocalRandom.current().nextDouble(0.85, 1.15);
            if (dmg <= 0) continue;
            plugin.combat().indicator(f, dmg, false);
            f.getWorld().spawnParticle(Particle.CRIT, f.getLocation().add(0, f.getHeight() * 0.6, 0), 8, 0.3, 0.4, 0.3, 0.2);
            boolean lethal = plugin.health().damage(f, dmg, null);
            if (lethal) {
                plugin.combat().kill(f, mm);
                FIGHTS.remove(id);
                FIGHTS.remove(f.getUniqueId());
                for (Player op : Bukkit.getOnlinePlayers())
                    if (op.hasPermission("rpgcraft.admin")) Text.msg(op, "&c⚔ &f" + name(mm) + " &7이(가) &f" + name(f) + " &7을(를) 쓰러뜨렸습니다!");
            }
        }
    }
}
