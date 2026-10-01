package kr.rpgcraft.combat;

import kr.rpgcraft.Keys;
import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.data.PlayerData;
import kr.rpgcraft.mob.MobManager;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;

/** 바닐라 대미지 이벤트를 가로채 커스텀 대미지로 변환한다 */
public class CombatListener implements Listener {
    private final RpgCraft plugin;

    public CombatListener(RpgCraft plugin) {
        this.plugin = plugin;
    }

    /** 몬스터 → 플레이어 선공 금지: 플레이어에게 먼저 맞은 몬스터만 반격 */
    public static final java.util.Map<java.util.UUID, Long> PROVOKED = new java.util.concurrent.ConcurrentHashMap<>();

    public static boolean provoked(Entity mob) {
        Long t = PROVOKED.get(mob.getUniqueId());
        return t != null && System.currentTimeMillis() - t < 60_000;
    }

    private boolean aggressiveAnyway(Entity e) {
        var pdc = e.getPersistentDataContainer();
        if (pdc.has(Keys.BOSS, PersistentDataType.STRING) || pdc.has(Keys.MINION, PersistentDataType.STRING)) return true;
        if (plugin.events() != null && plugin.events().isWaveMob(e)) return true;
        if (plugin.dungeons() != null && plugin.dungeons().isDungeonMob(e)) return true;
        if (plugin.tower() != null && plugin.tower().isTowerMob(e)) return true;
        return pdc.has(new org.bukkit.NamespacedKey(plugin, "bounty"), PersistentDataType.STRING);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFirstStrike(org.bukkit.event.entity.EntityTargetLivingEntityEvent e) {
        if (!(e.getTarget() instanceof Player) || e.getEntity() instanceof Player || !plugin.getConfig().getBoolean("mobs.passive-until-hit", true)) return;
        if (aggressiveAnyway(e.getEntity()) || provoked(e.getEntity())) return;
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProvoke(EntityDamageByEntityEvent e) {
        Entity src = e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Entity sh ? sh : e.getDamager();
        if (src instanceof Player pl && !(e.getEntity() instanceof Player)) {
            PROVOKED.put(e.getEntity().getUniqueId(), System.currentTimeMillis());
            // v5.10.45 반격: 대미지를 0 으로 바꿔 처리하다 보니 바닐라가 "누가 때렸는지" 를 기억하지 못해 몬스터가 맞고도 가만히 있었음 → 직접 노리게
            if (e.getEntity() instanceof org.bukkit.entity.Mob m && !plugin.combat().isNpc(m) && pl.getGameMode() != org.bukkit.GameMode.CREATIVE
                    && pl.getGameMode() != org.bukkit.GameMode.SPECTATOR && !kr.rpgcraft.world.NecromancyManager.isMinion(m)
                    && !(m instanceof org.bukkit.entity.Tameable t && t.isTamed()) && plugin.getConfig().getBoolean("mobs.retaliate", true))
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (m.isValid() && !m.isDead() && pl.isOnline() && !pl.isDead() && m.hasAI() && m.getWorld().equals(pl.getWorld())) m.setTarget(pl);
                });
        }
    }

    /** 몬스터끼리는 서로 노리거나 때리지 않음 */
    @EventHandler(ignoreCancelled = true)
    public void onMobInfight(org.bukkit.event.entity.EntityTargetLivingEntityEvent e) {
        if (!(e.getTarget() instanceof Player) && e.getTarget() != null && !(e.getEntity() instanceof Player)
                && !(e.getEntity() instanceof org.bukkit.entity.Tameable t && t.isTamed())
                && !kr.rpgcraft.world.NecromancyManager.isMinion(e.getTarget())
                && !kr.rpgcraft.feature.MobFightStick.fighting(e.getEntity(), e.getTarget())) e.setCancelled(true);   // v5.10.9 · v5.10.45 결투 막대기로 붙인 짝은 예외 네크로맨서 군단원은 몬스터가 노릴 수 있음
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMobHitMob(EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Player || !(e.getEntity() instanceof LivingEntity) || kr.rpgcraft.world.NecromancyManager.isMinion(e.getEntity())) return;
        Entity src = e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Entity sh ? sh : e.getDamager();
        if (src instanceof Player || src instanceof org.bukkit.entity.Tameable t && t.isTamed()) return;
        if (kr.rpgcraft.feature.MobFightStick.fighting(src, e.getEntity())) return;   // v5.10.45 관리자 몬스터 결투
        if (src instanceof LivingEntity) e.setCancelled(true);
    }

    /** 블레이즈 등은 비·물에 닿아도 피해 없음 */
    @EventHandler(ignoreCancelled = true)
    public void onRain(EntityDamageEvent e) {
        if (e.getEntity() instanceof org.bukkit.entity.Blaze && (e.getCause() == EntityDamageEvent.DamageCause.DROWNING || e.getCause() == EntityDamageEvent.DamageCause.MELTING))
            e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPartyHit(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player v)) return;
        Entity src = e.getDamager() instanceof org.bukkit.entity.Projectile pr && pr.getShooter() instanceof Entity sh ? sh : e.getDamager();
        if (src instanceof Player a && !a.equals(v) && plugin.party() != null && plugin.party().same(a, v)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onArrowHit(org.bukkit.event.entity.ProjectileHitEvent e) {
        if (e.getHitEntity() instanceof LivingEntity le && e.getEntity().getPersistentDataContainer().has(Keys.ARROW_ATK, PersistentDataType.DOUBLE))
            le.setNoDamageTicks(0);   // 스킬 화살이 앞 화살의 무적 시간에 막혀 "맞았는데 안 맞는" 문제
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (CombatService.BYPASS.get()) return;
        if (!(e.getEntity() instanceof LivingEntity victim) || victim instanceof ArmorStand) return;
        CombatService c = plugin.combat();
        if (c.isNpc(victim)) {
            e.setCancelled(true);
            return;
        }
        if (victim instanceof Player vp && plugin.data().get(vp).invulnUntil > System.currentTimeMillis()) {
            e.setCancelled(true);
            return;
        }
        DamageCause cause = e.getCause();
        if (cause == DamageCause.ENTITY_SWEEP_ATTACK) {
            e.setCancelled(true);
            return;
        }
        if (e instanceof EntityDamageByEntityEvent ev) {
            Entity damager = ev.getDamager();
            // 플레이어 근접
            if (damager instanceof Player p && cause == DamageCause.ENTITY_ATTACK) {
                if (!c.canHit(p, victim)) {
                    e.setCancelled(true);
                    return;
                }
                CombatService.Hit hit = c.melee(p, victim);
                double dealt = c.defend(victim, hit, p, p);
                finish(e, victim, dealt, p, p, hit.crit);
                c.afterHit(p, victim, dealt, hit.crit, true);
                return;
            }
            // 투사체
            if (damager instanceof Projectile proj) {
                ProjectileSource src = proj.getShooter();
                if (src instanceof Player p) {
                    if (!c.canHit(p, victim)) {
                        e.setCancelled(true);
                        return;
                    }
                    CombatService.Hit hit = c.ranged(p, proj, victim);
                    double dealt = c.defend(victim, hit, p, p);
                    finish(e, victim, dealt, p, p, hit.crit);
                    c.afterHit(p, victim, dealt, hit.crit, false);
                    return;
                }
                if (src instanceof LivingEntity shooter) {
                    if (!(victim instanceof Player) && plugin.mobs().tracked(victim) && shooter instanceof Enemy && victim instanceof Enemy
                            && !kr.rpgcraft.feature.MobFightStick.fighting(shooter, victim)) {
                        e.setCancelled(true); // 몬스터끼리 오사 방지
                        return;
                    }
                    Double power = proj.getPersistentDataContainer().get(Keys.POWER, PersistentDataType.DOUBLE);
                    CombatService.Hit hit = new CombatService.Hit(c.mobDamage(shooter) * (power == null ? 0.8 : power), false);
                    if (kr.rpgcraft.feature.MobFightStick.fighting(shooter, victim)) { e.setCancelled(true); return; }   // v5.10.53
                    double dealt = c.defend(victim, hit, null, shooter);
                    finish(e, victim, dealt, null, shooter, false);
                    return;
                }
                environmental(e, victim);
                return;
            }
            // 몬스터 근접 / 폭발
            if (damager instanceof LivingEntity mob) {
                double mult = cause == DamageCause.ENTITY_EXPLOSION ? 1.5 * Math.min(1, e.getDamage() / 20) : 1;
                if (mob instanceof Player) mult = 0.3; // 플레이어가 일으킨 기타 대미지(가시 등)
                CombatService.Hit hit = new CombatService.Hit(c.mobDamage(mob) * mult, false);
                if (kr.rpgcraft.feature.MobFightStick.fighting(mob, victim)) { e.setCancelled(true); return; }   // v5.10.53 결투 막대기 싸움은 능력치로 따로 계산 (MobFightStick)
                double dealt = c.defend(victim, hit, null, mob);
                finish(e, victim, dealt, null, mob, false);
                return;
            }
            if (damager instanceof TNTPrimed || damager instanceof EnderCrystal || damager instanceof Firework) {
                environmental(e, victim);
                return;
            }
            environmental(e, victim);
            return;
        }
        environmental(e, victim);
    }

    /** 낙하, 화염, 익사 등 환경 대미지는 최대 체력 비율로 환산 */
    private void environmental(EntityDamageEvent e, LivingEntity victim) {
        DamageCause cause = e.getCause();
        if (cause == DamageCause.VOID || cause == DamageCause.SUICIDE || cause.name().equals("KILL")) {
            plugin.health().set(victim, 0);
            e.setDamage(victim.getHealth() + 100000);
            return;
        }
        MobManager.MobState st = victim instanceof Player ? null : plugin.mobs().peek(victim);
        if (st != null && st.bossId != null) {
            e.setCancelled(true);
            return;
        }
        if (!(victim instanceof Player) && st == null) return; // 관리하지 않는 엔티티는 바닐라 처리
        AttributeInstance a = victim.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double vmax = a == null ? 20 : a.getValue();
        double scale = victim instanceof Player ? plugin.getConfig().getDouble("combat.environment-scale", 0.6) : 0.5;
        // 허수아비는 체력이 1e15 라서 최대 체력 비율로 환산하면 불 · 독 등 한 번에 수조가 찍혔음 → 같은 레벨 일반 몬스터 체력 기준 (v5.4.37)
        double base = st != null && plugin.dummies() != null && plugin.dummies().isDummy(victim) ? plugin.mobs().hpFor(st.level) : plugin.health().max(victim);
        double amount = e.getDamage() / Math.max(1, vmax) * base * scale;
        if (victim instanceof Player vp) {
            PlayerData d = plugin.data().get(vp);
            amount *= 1 - d.stats.def / 200; // 방어력은 환경 대미지에 절반만 적용
        }
        finish(e, victim, amount, null, null, false);
    }

    private void finish(EntityDamageEvent e, LivingEntity victim, double amount, Player attacker, Entity source, boolean crit) {
        if (amount > 0) plugin.combat().indicator(victim, amount, crit, attacker);
        if (attacker != null && victim instanceof Player vp && !vp.equals(attacker)) plugin.combat().markPvp(attacker, vp);
        if (attacker == null && victim instanceof Player hp && source instanceof LivingEntity sm && !(source instanceof Player) && plugin.targetHud() != null)
            plugin.targetHud().mark(hp, sm);   // v5.10.45 나를 때린 몬스터도 오른쪽 위에
        boolean lethal = amount > 0 && plugin.health().damage(victim, amount, attacker);
        if (lethal) {
            e.setDamage(victim.getHealth() + 100000);
            // 방패 막기 등으로 바닐라 대미지가 무효화된 경우를 대비
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!victim.isDead() && victim.isValid() && plugin.health().cur(victim) <= 0 && !plugin.health().deathGuard(victim)) plugin.combat().kill(victim, source);
            });
        } else {
            e.setDamage(0);
            Bukkit.getScheduler().runTask(plugin, () -> plugin.health().sync(victim));
        }
    }

    /** 바닐라 회복은 최대 체력 비율로 환산 (자연 회복은 HudManager 가 담당) */
    @EventHandler(ignoreCancelled = true)
    public void onRegain(EntityRegainHealthEvent e) {
        if (!(e.getEntity() instanceof LivingEntity le)) return;
        if (!(le instanceof Player) && !plugin.mobs().tracked(le)) return;
        e.setCancelled(true);
        EntityRegainHealthEvent.RegainReason r = e.getRegainReason();
        if (r == EntityRegainHealthEvent.RegainReason.SATIATED || r == EntityRegainHealthEvent.RegainReason.REGEN) return;
        double amount = e.getAmount() / 20.0 * plugin.health().max(le) * 0.5;
        if (le instanceof org.bukkit.entity.Witch) amount *= 0.5 * 2 / 3.0;   // 마녀 회복: 절반 → 그 2/3 (v5.4.31)
        plugin.health().heal(le, amount);
    }

    @EventHandler(ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player p) || !(e.getProjectile() instanceof Projectile proj)) return;
        PlayerData d = plugin.data().get(p);
        double atk = d.stats.holdingBow && d.stats.weaponOk ? d.stats.ranged : Math.max(5, d.stats.attack * 0.2);
        proj.getPersistentDataContainer().set(Keys.ARROW_ATK, PersistentDataType.DOUBLE, atk);
        proj.getPersistentDataContainer().set(Keys.ARROW_FORCE, PersistentDataType.DOUBLE, (double) e.getForce());
    }

    @EventHandler(ignoreCancelled = true)
    public void onTrident(ProjectileLaunchEvent e) {
        if (!(e.getEntity() instanceof Trident t) || !(t.getShooter() instanceof Player p)) return;
        t.getPersistentDataContainer().set(Keys.ARROW_ATK, PersistentDataType.DOUBLE, plugin.data().get(p).stats.attack);
        t.getPersistentDataContainer().set(Keys.ARROW_FORCE, PersistentDataType.DOUBLE, 1.0);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onResurrect(EntityResurrectEvent e) {
        LivingEntity le = e.getEntity();
        if (!(le instanceof Player) && !plugin.mobs().tracked(le)) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!le.isDead()) plugin.health().set(le, plugin.health().max(le) * 0.1);
        });
    }

    /** 몬스터가 NPC 상인을 노리지 않도록 */
    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent e) {
        if (e.getTarget() != null && plugin.combat().isNpc(e.getTarget())) e.setCancelled(true);
    }
}
