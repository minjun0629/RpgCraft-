package kr.rpgcraft.combat;

import kr.rpgcraft.Keys;
import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.data.PlayerData;
import kr.rpgcraft.guild.Guild;
import kr.rpgcraft.item.WeaponClass;
import kr.rpgcraft.mob.MobManager;
import kr.rpgcraft.passive.Passive;
import kr.rpgcraft.stat.StatSnapshot;
import kr.rpgcraft.util.Text;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 대미지 계산의 핵심.
 * 공격력 → 공격 간격 보정 → 패시브 가산 → 크리티컬(×2, 방어 무시) → 방어/회피/패시브 경감 → 가상 체력 차감
 */
public class CombatService {
    /** 우리가 직접 발생시킨 처치용 대미지 이벤트는 리스너가 건드리지 않도록 표시 */
    private static final ThreadLocal<Boolean> SWORD_GOD_GUARD = ThreadLocal.withInitial(() -> false);
    public static final ThreadLocal<Boolean> BYPASS = ThreadLocal.withInitial(() -> false);

    private final RpgCraft plugin;

    public CombatService(RpgCraft plugin) {
        this.plugin = plugin;
    }

    public static final class Hit {
        public double amount;
        public boolean crit;
        public boolean dodged;

        Hit(double amount, boolean crit) {
            this.amount = amount;
            this.crit = crit;
        }
    }

    // ------------------------------------------------------------------ 공격측 계산
    /** 플레이어 근접 공격 대미지 (방어 적용 전) */
    public Hit melee(Player p, LivingEntity victim) {
        PlayerData d = plugin.data().get(p);
        StatSnapshot s = d.stats;
        long now = System.currentTimeMillis();
        if (!s.weaponOk && s.weaponProblem != null) {
            Text.actionBar(p, "&c무기를 사용할 수 없습니다: " + s.weaponProblem);
            d.actionBarLock = now + 1500;
        }
        double atk = s.holdingBow ? s.attack * 0.3 : s.attack;
        WeaponClass wc = s.weaponClass;
        long interval = wc == null ? WeaponClass.CLUB.interval : wc.interval;
        double factor = Math.max(0.2, Math.min(1.0, (now - d.lastAttack) / (double) interval));
        d.lastAttack = now;
        atk += passiveBonus(p, d, victim, now);
        if (!p.isOnGround() && p.getFallDistance() > 0) plugin.passives().track(p, "jump_attacks", 1);
        if (p.isSneaking()) plugin.passives().track(p, "sneak_attacks", 1);
        atk *= factor;
        atk *= plugin.jobs().outgoingMult(p, victim);
        boolean crit = factor >= 0.9 && ThreadLocalRandom.current().nextDouble() * 100 < s.crit;
        if (crit) atk *= 2 + s.critDmg / 100;
        return new Hit(atk, crit);
    }

    /** 플레이어 원거리 대미지. 화살에 발사 시점의 공격력이 저장되어 있다. */
    public Hit ranged(Player p, Projectile proj, LivingEntity victim) {
        PlayerData d = plugin.data().get(p);
        long now = System.currentTimeMillis();
        Double base = proj.getPersistentDataContainer().get(Keys.ARROW_ATK, PersistentDataType.DOUBLE);
        Double force = proj.getPersistentDataContainer().get(Keys.ARROW_FORCE, PersistentDataType.DOUBLE);
        double atk = base == null ? d.stats.attack * 0.25 : base;
        atk *= force == null ? 1 : Math.max(0.1, force);
        atk *= plugin.jobs().outgoingMult(p, victim);
        atk += passiveBonus(p, d, victim, now);
        plugin.passives().track(p, "arrows_hit", 1);
        if (d.has(Passive.SNIPER) && p.getWorld().equals(victim.getWorld()) && p.getLocation().distance(victim.getLocation()) >= 30) {
            atk *= 2;
            Text.actionBar(p, "&6스나이퍼!");
        }
        if (p.getWorld().equals(victim.getWorld()) && p.getLocation().distance(victim.getLocation()) >= 30)
            plugin.passives().track(p, "far_shots", 1);
        if (d.has(Passive.ARCHER)) {
            if (victim.getUniqueId().equals(d.archerTarget) && now - d.archerLast < 10_000) d.archerStacks = Math.min(20, d.archerStacks + 1);
            else d.archerStacks = 0;
            d.archerTarget = victim.getUniqueId();
            d.archerLast = now;
            atk += d.archerStacks * 20;
        }
        boolean crit = ThreadLocalRandom.current().nextDouble() * 100 < d.stats.crit;
        if (crit) atk *= 2 + d.stats.critDmg / 100;
        if (d.has(Passive.PARALYZE_ARROW) && ThreadLocalRandom.current().nextDouble() < 0.03) {
            stun(victim, 40);
            Text.actionBar(p, "&e마비화살!");
        }
        return new Hit(atk, crit);
    }

    public double passiveBonus(Player p, PlayerData d, LivingEntity victim, long now) {
        double b = 0;
        if (d.has(Passive.RUNNER) && d.runStart > 0) b += Math.min(100, 5 * ((now - d.runStart) / 5000));
        if (d.has(Passive.AIR_COMBAT) && !p.isOnGround()) b += 50;
        if (d.has(Passive.CROUCH_ATTACK) && p.isSneaking()) b += 50;
        if (d.potionBuffUntil > now) b += 25;
        if (d.has(Passive.BULLY) || d.has(Passive.UNDERDOG)) {
            int vl = levelOf(victim);
            if (d.has(Passive.BULLY) && vl < d.level) b += 100;
            if (d.has(Passive.UNDERDOG) && vl > d.level) b += 100;
        }
        return b;
    }

    public int levelOf(LivingEntity e) {
        if (e instanceof Player p) return plugin.data().get(p).level;
        MobManager.MobState s = plugin.mobs().peek(e);
        return s == null ? 1 : s.level;
    }

    /** 몬스터가 가하는 기본 대미지 */
    public double mobDamage(LivingEntity mob) {
        if (mob instanceof Player p) return plugin.data().get(p).stats.attack;
        return plugin.mobs().state(mob).damage * scale();
    }

    // ------------------------------------------------------------------ 방어측 계산
    /**
     * 방어/회피/패시브 경감을 적용한 최종 대미지.
     * @param attacker 공격한 플레이어 (없으면 null)
     * @param source   공격 주체 엔티티 (몬스터 포함, 없으면 null)
     */
    /** 플레이어 체력 단위 환산 비율 */
    public double scale() {
        return plugin.stats().hpScale();
    }

    public double defend(LivingEntity victim, Hit hit, Player attacker, Entity source) {
        double amount = hit.amount;
        if (victim instanceof Player && attacker != null) amount *= scale(); // PvP 도 플레이어 체력 단위로
        long now = System.currentTimeMillis();
        double pen = attacker == null ? 0 : plugin.data().get(attacker).stats.armorPen;
        if (victim instanceof Player vp) {
            PlayerData vd = plugin.data().get(vp);
            StatSnapshot vs = vd.stats;
            if (vd.invulnUntil > now) {
                hit.dodged = true;
                return 0;
            }
            if (ThreadLocalRandom.current().nextDouble() * 100 < vs.dodge) {
                hit.dodged = true;
                Text.actionBar(vp, "&b회피!");
                return 0;
            }
            if (!hit.crit) amount *= 1 - vs.def * (1 - pen / 100) / 100;
            if (vp.isSneaking()) {
                plugin.passives().track(vp, "sneak_hits", 1);
                if (vd.has(Passive.CURL)) amount *= 0.97;
                if (vd.has(Passive.FC_DOLDOL) && ThreadLocalRandom.current().nextDouble() < 0.8) amount *= 0.7;
            }
            if (source != null && isBehind(vp, source.getLocation())) {
                plugin.passives().track(vp, "back_hits", 1);
                if (vd.has(Passive.BACK_GUARD)) amount *= 0.5;
            }
            if (vd.has(Passive.STRONGEST) && attacker != null && plugin.data().get(attacker).level < vd.level) amount *= 0.5;
            if (vp.isBlocking()) amount *= 0.7;
            if (plugin.spirits().inXuanwuField(vp)) amount *= 0.6;
            if (!vp.isOnGround()) {
                plugin.passives().track(vp, "jump_hits", 1);
                if (vd.has(Passive.AIR_MASTER) && source instanceof LivingEntity le && ThreadLocalRandom.current().nextDouble() < 0.05) {
                    Text.actionBar(vp, "&d대미지 반사!");
                    double reflect = amount;
                    Bukkit.getScheduler().runTask(plugin, () -> applyDamage(le, reflect, vp, vp, false));
                    return 0;
                }
            }
            amount = plugin.effects().onDefend(vp, source, amount);
            amount = plugin.jobs().onDefend(vp, source, amount);
            amount *= plugin.legendary().incomingMult(vp);
            plugin.passives().track(vp, "hits_taken", 1);
            vd.hitsTakenNoAttack++;
            if (vd.has(Passive.ENDURANCE) && vd.hitsTakenNoAttack >= 10) {
                vd.hitsTakenNoAttack = 0;
                Bukkit.getScheduler().runTask(plugin, () -> plugin.health().heal(vp, 500 * scale()));
                Text.actionBar(vp, "&a맷집! 체력 +500");
            }
            vd.lastDamaged = now;
            vd.lastCombat = now;
            if (vd.hp <= plugin.health().max(vp) * 0.1) vd.counters.merge("hs_tenacity", 1.0, Double::sum);   // 끈기 누적
            if (vd.adv >= 250) amount *= 0.95;                                                          // 모험 250 강인함
            if (vd.adv >= 500 && vd.hp <= plugin.health().max(vp) * 0.3) amount *= 0.85;                // 모험 500 불굴
            amount *= 1 - 0.005 * kr.rpgcraft.stat.HiddenStat.TENACITY.points(vd);
            if (attacker != null) {   // PvP: 공격력 비중을 낮추고 한 방에 너무 많이 깎이지 않게
                amount *= plugin.getConfig().getDouble("pvp.damage-mult", 0.45);
                if (hit.crit) amount *= plugin.getConfig().getDouble("pvp.crit-mult", 0.75);
                amount = Math.min(amount, plugin.health().max(vp) * plugin.getConfig().getDouble("pvp.max-hit-ratio", 0.25));
            } else {                  // 몬스터 → 플레이어: 몬스터 레벨이 높으면 더 아프게
                Entity src = source instanceof org.bukkit.entity.Projectile pr && pr.getShooter() instanceof Entity sh ? sh : source;
                MobManager.MobState ms = src instanceof LivingEntity sl ? plugin.mobs().peek(sl) : null;
                if (ms != null) {
                    int gap = ms.level - vd.level;
                    if (gap > 0) amount *= Math.min(plugin.getConfig().getDouble("mobs.level-gap-damage-cap", 8), 1 + plugin.getConfig().getDouble("mobs.level-gap-damage", 0.06) * gap);
                }
            }
        } else {
            MobManager.MobState s = plugin.mobs().state(victim);
            if (!hit.crit) amount *= 1 - Math.min(90, s.def) * (1 - pen / 100) / 100;
            if (s.bossId != null) {   // 여럿이 때리면 보스가 단단해짐
                int n = plugin.bosses().crowd(victim);
                if (n > 1) amount /= 1 + plugin.getConfig().getDouble("bosses.crowd-defense", 0.3) * (n - 1);
            }
            if (attacker != null) amount *= 1 + 0.01 * kr.rpgcraft.stat.HiddenStat.GRIT.points(plugin.data().get(attacker));   // 투지
            if (attacker != null) {   // 레벨이 높은 몬스터일수록 잘 안 박힘 + 파티 기여도 기록
                int gap = s.level - plugin.data().get(attacker).level;
                if (gap > 0) amount *= Math.max(plugin.getConfig().getDouble("mobs.level-gap-floor", 0.02),
                        Math.pow(plugin.getConfig().getDouble("mobs.level-gap-resist-base", 0.95), gap));   // 10레벨 차 ≈ 60%, 30레벨 ≈ 21%, 60레벨 ≈ 5%
                s.contrib.merge(attacker.getUniqueId(), amount, Double::sum);
                s.lastHitBy = attacker.getUniqueId();
                CombatListener.PROVOKED.put(victim.getUniqueId(), System.currentTimeMillis());
                s.lastHitAt = System.currentTimeMillis();
            }
        }
        return Math.max(0, amount);
    }

    private boolean isBehind(Player victim, Location from) {
        Vector look = victim.getLocation().getDirection().setY(0);
        Vector to = from.toVector().subtract(victim.getLocation().toVector()).setY(0);
        if (look.lengthSquared() < 1e-6 || to.lengthSquared() < 1e-6) return false;
        return look.normalize().dot(to.normalize()) < -0.2;   // 뒤쪽 약 160° 범위
    }

    // ------------------------------------------------------------------ 적용
    /** 적중 후 공격자 효과 (체력흡수, 급소, 불타는 손, 붉은 초월체, 넉백 스킬) */
    public void afterHit(Player p, LivingEntity victim, double dealt, boolean crit, boolean melee) {
        if (dealt > 0 && !SWORD_GOD_GUARD.get() && plugin.data().get(p).dex >= 250 && ThreadLocalRandom.current().nextDouble() < 0.1 && victim.isValid() && !victim.isDead()) {
            SWORD_GOD_GUARD.set(true);   // 민첩 250 「질풍」: 연속 베기
            try {
                kr.rpgcraft.util.Vfx.slash(victim.getLocation().add(0, 1, 0), p.getLocation().getDirection().setY(0), 2.4, -45, Color.fromRGB(0x8CFF9A));
                dealSkillDamage(p, victim, dealt * 0.4, false);
            } finally {
                SWORD_GOD_GUARD.set(false);
            }
        }
        if (dealt > 0 && !SWORD_GOD_GUARD.get() && plugin.data().get(p).str >= 250 && ThreadLocalRandom.current().nextDouble() < 0.1 && victim.isValid() && !victim.isDead()) {
            SWORD_GOD_GUARD.set(true);   // 힘 250 「분쇄」: 강타
            try {
                kr.rpgcraft.util.Vfx.burst(victim.getLocation().add(0, 1, 0), 2.2, Color.fromRGB(0xFF5A1F));
                victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 1f, 0.7f);
                dealSkillDamage(p, victim, dealt * 0.5, false);
            } finally {
                SWORD_GOD_GUARD.set(false);
            }
        }
        if (crit && dealt > 0 && !SWORD_GOD_GUARD.get() && kr.rpgcraft.world.HiddenJobManager.starfall(plugin.data().get(p)) && victim.isValid() && !victim.isDead()) {
            SWORD_GOD_GUARD.set(true);   // 별똥별
            try {
                Location top = victim.getLocation().add(0, 12, 0);
                kr.rpgcraft.util.Vfx.beam(top, victim.getLocation().add(0, 1, 0), 1.6, Color.fromRGB(0xFFF3B0));
                kr.rpgcraft.util.Vfx.burst(victim.getLocation().add(0, 1, 0), 2.4, Color.fromRGB(0x6BD8FF));
                dealSkillDamage(p, victim, plugin.data().get(p).stats.attack * 0.8, false);
            } finally {
                SWORD_GOD_GUARD.set(false);
            }
        }
        if (crit && dealt > 0 && !SWORD_GOD_GUARD.get() && plugin.jobs().isThird(p, kr.rpgcraft.feature.JobManager.Third.SWORD_GOD) && victim.isValid() && !victim.isDead()) {
            SWORD_GOD_GUARD.set(true);   // 추가 참격이 또 추가 참격을 부르지 않게
            try {
                kr.rpgcraft.util.Vfx.slash(victim.getLocation().add(0, 1, 0), p.getLocation().getDirection().setY(0), 2.6, 70, Color.WHITE);
                dealSkillDamage(p, victim, plugin.data().get(p).stats.attack * 0.6, false);
            } finally {
                SWORD_GOD_GUARD.set(false);
            }
        }
        PlayerData d = plugin.data().get(p);
        long now = System.currentTimeMillis();
        d.hitsTakenNoAttack = 0;
        d.lastCombat = now;
        if (dealt <= 0) return;
        if (crit) plugin.passives().track(p, "crit_hits", 1);
        if (d.stats.lifesteal > 0) plugin.health().heal(p, dealt * d.stats.lifesteal / 100 * scale());
        else if (d.stats.lifesteal < 0) {   // 음수 흡수: 때릴 때마다 체력 감소 (죽지는 않음)
            double lose = Math.min(d.hp - 1, dealt * -d.stats.lifesteal / 100 * scale());
            if (lose > 0) d.hp -= lose;
        }
        if (crit && d.has(Passive.VITAL) && !d.onCooldown("vital")) {
            d.cooldown("vital", 3000);
            plugin.health().healPercent(p, 3);
        }
        if (d.has(Passive.BURNING_HAND) && ThreadLocalRandom.current().nextDouble() < 0.3) plugin.health().heal(p, 500 * scale());
        if (melee && d.has(Passive.FC_RED) && ThreadLocalRandom.current().nextDouble() < 0.6) burn(victim, p, d.stats.attack * 0.1, 3);
        if (melee && d.pendingKnock > 0) {
            knockback(victim, p.getLocation(), d.pendingKnock);
            d.pendingKnock = 0;
        }
        plugin.effects().onHit(p, victim, dealt, crit, melee);
    }

    /** 최종 대미지를 가상 체력에 적용. 치명상이면 실제로 처치한다. */
    public void applyDamage(LivingEntity victim, double amount, Player attacker, Entity source, boolean crit) {
        if (victim.isDead() || !victim.isValid()) return;
        if (victim instanceof Player vp && (vp.getGameMode() == GameMode.CREATIVE || vp.getGameMode() == GameMode.SPECTATOR)) return;
        indicator(victim, amount, crit);
        boolean lethal = plugin.health().damage(victim, amount, attacker);
        if (lethal) kill(victim, source);
        else {
            victim.playEffect(EntityEffect.HURT);
            victim.getWorld().playSound(victim.getLocation(), victim instanceof Player ? Sound.ENTITY_PLAYER_HURT : Sound.ENTITY_GENERIC_HURT, 0.6f, 1f);
        }
    }

    public void kill(LivingEntity victim, Entity killer) {
        BYPASS.set(true);
        try {
            if (killer != null) victim.damage(victim.getHealth() + 100000, killer);
            else victim.damage(victim.getHealth() + 100000);
        } finally {
            BYPASS.set(false);
        }
        if (!victim.isDead() && plugin.health().cur(victim) <= 0) victim.setHealth(0);
    }

    /** 스킬 대미지 (플레이어 → 대상). 크리티컬 판정 포함. */
    public void dealSkillDamage(Player p, LivingEntity target, double amount, boolean allowCrit) {
        if (!isEnemy(p, target)) return;
        if (!(target instanceof Enemy) && !(target instanceof Player) && kr.rpgcraft.mob.AnimalAggro.INSTANCE != null)
            kr.rpgcraft.mob.AnimalAggro.INSTANCE.provoke(p, target);
        PlayerData d = plugin.data().get(p);
        boolean crit = allowCrit && ThreadLocalRandom.current().nextDouble() * 100 < d.stats.crit;
        Hit hit = new Hit(crit ? amount * (2 + d.stats.critDmg / 100) : amount, crit);
        double dealt = defend(target, hit, p, p);
        applyDamage(target, dealt, p, p, crit);
        afterHit(p, target, dealt, crit, false);
    }

    /** 보스/몬스터 스킬 대미지 (몬스터 → 플레이어) */
    public void mobSkillDamage(LivingEntity mob, Player p, double amount) {
        if (p.isDead()) return;
        if (plugin.events() != null && plugin.events().waveShielded(p, mob)) return;
        Hit hit = new Hit(amount * scale(), false);
        double dealt = defend(p, hit, null, mob);
        applyDamage(p, dealt, null, mob, false);
    }

    // ------------------------------------------------------------------ 판정
    public boolean isNpc(Entity e) {
        return e.getPersistentDataContainer().has(Keys.NPC_SHOP, PersistentDataType.STRING);
    }

    /** 플레이어가 해당 대상을 공격할 수 있는지 (같은 길드, PvP 설정, 공성전) */
    public boolean canHit(Player attacker, LivingEntity victim) {
        if (isNpc(victim)) return false;
        if (!(victim instanceof Player vp)) return true;
        if (vp.equals(attacker)) return false;
        Guild ga = plugin.guilds().of(attacker.getUniqueId());
        Guild gv = plugin.guilds().of(vp.getUniqueId());
        if (ga != null && ga == gv && !plugin.getConfig().getBoolean("combat.guild-friendly-fire", false)) return false;
        if (ga != null && gv != null && plugin.wars().isAtWar(ga.name, gv.name)) return true;
        if (!plugin.getConfig().getBoolean("combat.pvp", true)) return false;
        int prot = plugin.getConfig().getInt("combat.newbie-protection-level", 10);
        if (plugin.data().get(vp).level < prot || plugin.data().get(attacker).level < prot) {
            Text.actionBar(attacker, "&cLv." + prot + " 미만은 PvP 보호 중입니다.");
            return false;
        }
        return true;
    }

    /** 범위 스킬 대상 판정 */
    public boolean isEnemy(Player p, Entity e) {
        if (!(e instanceof LivingEntity le) || e.equals(p) || e instanceof ArmorStand || e.isDead()) return false;
        if (isNpc(e)) return false;
        if (plugin.guildRaids() != null && !plugin.guildRaids().canHit(p, e)) return false;   // 다른 길드의 토벌 보스
        if (e instanceof Player) return ((Player) e).getGameMode() != GameMode.SPECTATOR && canHitSilently(p, (Player) e);
        if (le instanceof Enemy || plugin.mobs().tracked(le)) return true;
        // 동물·중립 몹(늑대·골렘·벌·곰 등)도 스킬에 맞음. 단, 주민·상인과 누군가 길들인 동물은 제외
        if (le instanceof org.bukkit.entity.AbstractVillager) return false;
        if (le instanceof org.bukkit.entity.Tameable t && t.isTamed()) return false;
        return le instanceof org.bukkit.entity.Mob;
    }

    private boolean canHitSilently(Player a, Player v) {
        if (plugin.party() != null && plugin.party().same(a, v)) return false; // 파티원끼리 공격 불가
        Guild ga = plugin.guilds().of(a.getUniqueId());
        Guild gv = plugin.guilds().of(v.getUniqueId());
        if (ga != null && ga == gv) return false;
        if (ga != null && gv != null && plugin.wars().isAtWar(ga.name, gv.name)) return true;
        int prot = plugin.getConfig().getInt("combat.newbie-protection-level", 10);
        return plugin.getConfig().getBoolean("combat.pvp", true)
                && plugin.data().get(v).level >= prot && plugin.data().get(a).level >= prot;
    }

    // ------------------------------------------------------------------ 상태이상 / 연출
    public void burn(LivingEntity victim, Player src, double perSec, int seconds) {
        new BukkitRunnable() {
            int n = 0;

            @Override
            public void run() {
                if (n++ >= seconds || victim.isDead() || !victim.isValid()) {
                    cancel();
                    return;
                }
                victim.getWorld().spawnParticle(Particle.FLAME, victim.getLocation().add(0, 1, 0), 12, 0.3, 0.5, 0.3, 0.01);
                Hit h = new Hit(perSec, false);
                double dealt = defend(victim, h, src, src);
                applyDamage(victim, dealt, src, src, false);
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public void stun(LivingEntity e, int ticks) {
        e.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, ticks, 250, false, false));
        e.addPotionEffect(new PotionEffect(PotionEffectType.JUMP, ticks, 250, false, false));
        e.getWorld().spawnParticle(Particle.CRIT_MAGIC, e.getLocation().add(0, e.getHeight() + 0.3, 0), 20, 0.3, 0.1, 0.3, 0.1);
        if (e instanceof Mob m) {   // AI 를 끄면 중력도 멈춰 공중에 뜨므로, 대상만 잃게 하고 AI 는 유지
            m.setTarget(null);
            m.setVelocity(m.getVelocity().setX(0).setZ(0));
        }
    }

    public void knockback(LivingEntity e, Location from, double blocks) {
        Vector dir = e.getLocation().toVector().subtract(from.toVector()).setY(0);
        if (dir.lengthSquared() < 1e-4) dir = from.getDirection().setY(0);
        Vector vel = dir.normalize().multiply(0.2 + blocks * 0.17).setY(0.35);
        Bukkit.getScheduler().runTask(plugin, () -> e.setVelocity(vel));
    }

    public void indicator(LivingEntity victim, double amount, boolean crit) {
        plugin.visuals().damageNumber(victim, amount, crit, false);
    }
}
