package kr.rpgcraft.mob;

import kr.rpgcraft.RpgCraft;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

import java.lang.reflect.Method;
import java.util.*;

/**
 * 야생 동물 반격.
 * 플레이어가 동물(소·돼지·양·닭·말 등)을 때리면 그 동물이 화가 나서 쫓아와 공격한다.
 * 주변의 같은 종류 동물도 함께 달려든다 (무리 반격). 일정 시간이 지나거나 멀어지면 진정한다.
 * Paper 서버는 길찾기(Pathfinder)를 사용하고, 그 외에는 직접 밀어서 이동시킨다.
 */
public class AnimalAggro implements Listener {
    private record Anger(UUID target, long until) {}

    private final RpgCraft plugin;
    private final Map<UUID, Anger> angry = new HashMap<>();
    private final Map<UUID, Long> lastAttack = new HashMap<>();
    private int tick;

    public AnimalAggro(RpgCraft plugin) {
        INSTANCE = this;
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 5L, 2L);
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("wild-animals.enabled", true);
    }

    private boolean eligible(Entity e) {
        if (!(e instanceof Animals a) || plugin.combat().isNpc(e)) return false;
        if (a instanceof Tameable t && t.isTamed()) return false;
        List<String> ex = plugin.getConfig().getStringList("wild-animals.exclude");
        return !ex.contains(e.getType().name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!enabled() || !eligible(e.getEntity())) return;
        Player p = null;
        if (e.getDamager() instanceof Player dp) p = dp;
        else if (e.getDamager() instanceof Projectile pr) {
            ProjectileSource s = pr.getShooter();
            if (s instanceof Player sp) p = sp;
        }
        if (p == null || p.getGameMode() == GameMode.CREATIVE) return;
        provoke(p, (LivingEntity) e.getEntity());
    }

    public static AnimalAggro INSTANCE;

    /** 스킬·참격으로 맞은 동물도 화나서 반격 (무리도 함께) */
    public void provoke(Player p, LivingEntity animal) {
        if (!enabled() || !eligible(animal)) return;
        long until = System.currentTimeMillis() + plugin.getConfig().getLong("wild-animals.anger-seconds", 30) * 1000L;
        anger(animal, p, until);
        double r = plugin.getConfig().getDouble("wild-animals.herd-radius", 6);
        if (r > 0) {
            for (Entity n : animal.getNearbyEntities(r, 3, r)) {
                if (n.getType() == animal.getType() && eligible(n) && !angry.containsKey(n.getUniqueId())) anger((LivingEntity) n, p, until);
            }
        }
    }

    private void anger(LivingEntity a, Player p, long until) {
        boolean fresh = !angry.containsKey(a.getUniqueId());
        angry.put(a.getUniqueId(), new Anger(p.getUniqueId(), until));
        plugin.mobs().state(a); // 레벨·체력·공격력 부여 (거리 기반)
        if (fresh) {
            a.getWorld().spawnParticle(Particle.VILLAGER_ANGRY, a.getEyeLocation().add(0, 0.4, 0), 3, 0.2, 0.1, 0.2);
            a.getWorld().playSound(a.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 0.35f, 1.9f);
        }
    }

    private void tick() {
        if (angry.isEmpty()) return;
        tick++;
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Anger>> it = angry.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Anger> en = it.next();
            Entity ent = Bukkit.getEntity(en.getKey());
            Player t = Bukkit.getPlayer(en.getValue().target());
            if (!(ent instanceof LivingEntity a) || !a.isValid() || a.isDead() || t == null || !t.isValid() || t.isDead()
                    || !t.getWorld().equals(a.getWorld()) || en.getValue().until() < now
                    || t.getGameMode() == GameMode.CREATIVE || t.getGameMode() == GameMode.SPECTATOR
                    || a.getLocation().distanceSquared(t.getLocation()) > 28 * 28) {
                it.remove();
                if (ent instanceof Mob m) stop(m);
                continue;
            }
            chase(a, t);
            double reach = 1.6 + a.getWidth() / 2;
            if (a.getLocation().distanceSquared(t.getLocation()) <= reach * reach) {
                long last = lastAttack.getOrDefault(a.getUniqueId(), 0L);
                if (now - last >= plugin.getConfig().getLong("wild-animals.attack-interval-ms", 1200)) {
                    lastAttack.put(a.getUniqueId(), now);
                    attack(a, t);
                }
            }
        }
    }

    private void attack(LivingEntity a, Player t) {
        MobManager.MobState s = plugin.mobs().state(a);
        double dmg = s.damage * plugin.getConfig().getDouble("wild-animals.damage-mult", 0.6);
        plugin.combat().mobSkillDamage(a, t, dmg);
        Vector kb = t.getLocation().toVector().subtract(a.getLocation().toVector()).setY(0);
        if (kb.lengthSquared() > 0.01) t.setVelocity(kb.normalize().multiply(0.45).setY(0.3));
        a.getWorld().playSound(a.getLocation(), Sound.ENTITY_GOAT_RAM_IMPACT, 0.8f, 1.2f);
        a.getWorld().spawnParticle(Particle.CRIT, t.getLocation().add(0, 1, 0), 6, 0.3, 0.3, 0.3, 0.1);
    }

    // ------------------------------------------------------------------ 이동 (Paper 길찾기 → 없으면 직접 밀기)
    private static Method getPathfinder, moveTo, stopPath;
    private static boolean reflectTried;

    private static void reflect(Mob m) {
        if (reflectTried) return;
        reflectTried = true;
        try {
            getPathfinder = m.getClass().getMethod("getPathfinder");
            Class<?> pf = getPathfinder.getReturnType();
            moveTo = pf.getMethod("moveTo", LivingEntity.class, double.class);
            stopPath = pf.getMethod("stopPathfinding");
        } catch (Throwable ignored) {
            getPathfinder = null;
        }
    }

    private void chase(LivingEntity a, Player t) {
        Location al = a.getLocation();
        Location tl = t.getLocation();
        Vector dir = tl.toVector().subtract(al.toVector());
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ()));
        a.setRotation(yaw, 0);
        if (a instanceof Mob m) {
            reflect(m);
            if (getPathfinder != null) {
                if (tick % 5 == 0) {
                    try {
                        moveTo.invoke(getPathfinder.invoke(m), t, plugin.getConfig().getDouble("wild-animals.speed", 1.5));
                    } catch (Throwable ignored) {
                    }
                }
                return;
            }
        }
        Vector h = dir.clone().setY(0);
        if (h.lengthSquared() < 1.5) return;
        Vector v = h.normalize().multiply(0.26);
        double vy = a.getVelocity().getY();
        boolean blocked = !al.clone().add(v.clone().normalize()).getBlock().isPassable();
        if (a.isOnGround() && (blocked || tl.getY() - al.getY() > 0.6)) vy = 0.42;
        a.setVelocity(new Vector(v.getX(), vy, v.getZ()));
    }

    private void stop(Mob m) {
        if (getPathfinder == null) return;
        try {
            stopPath.invoke(getPathfinder.invoke(m));
        } catch (Throwable ignored) {
        }
    }

    public boolean isAngry(Entity e) {
        return angry.containsKey(e.getUniqueId());
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        angry.remove(e.getEntity().getUniqueId());
        lastAttack.remove(e.getEntity().getUniqueId());
    }
}
