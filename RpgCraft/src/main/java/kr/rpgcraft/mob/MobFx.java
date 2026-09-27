package kr.rpgcraft.mob;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.feature.SkillBook;
import kr.rpgcraft.feature.WeaponFx;
import kr.rpgcraft.util.Vfx;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

/**
 * 몬스터 스킬 연출 전용 (v5.1.7). 판정 · 피해 · 범위 · 예고 시간에는 관여하지 않고 보이는 것만 더한다.
 */
public final class MobFx {
    private MobFx() {}

    static final Color RED = Color.fromRGB(0xFF2A2A), EARTH = Color.fromRGB(0x8B6B4A), ICE = Color.fromRGB(0xA8E0FF),
            FIRE = Color.fromRGB(0xFF7A1F), VOID = Color.fromRGB(0x9A5AFF), LIFE = Color.fromRGB(0x5AFF7A), TOXIC = Color.fromRGB(0x5FE05A);

    private static boolean on() {
        RpgCraft pl = RpgCraft.get();
        return pl != null && pl.getConfig().getBoolean("vfx.mob-extra", true);
    }

    private static void later(long t, Runnable r) {
        RpgCraft pl = RpgCraft.get();
        if (pl != null) Bukkit.getScheduler().runTaskLater(pl, r, t);
    }

    private static void dust(Location l, Color c, float size, int n, double spread) {
        l.getWorld().spawnParticle(Particle.REDSTONE, l, n, spread, spread, spread, 0, new Particle.DustOptions(c, size));
    }

    private static void circle(Location c, double r, Color col, float size) {
        int n = (int) Math.max(10, Math.min(60, r * 8));
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            dust(c.clone().add(Math.cos(a) * r, 0.12, Math.sin(a) * r), col, size, 1, 0);
        }
    }

    // ------------------------------------------------------------------ 예고
    /** 원형 예고: 테두리 + 가운데부터 차오르는 붉은 원 (ticks 동안, 기존 예고 시간과 같게) */
    public static void warnCircle(Location c, double r, int ticks) {
        if (!on()) return;
        Location o = c.clone();
        for (int t = 0; t <= ticks; t += 2) {
            int tt = t;
            later(t, () -> {
                double f = Math.max(0.1, tt / (double) ticks);
                if (tt % 4 == 0) circle(o, r, RED, 1.3f);
                circle(o, r * f, Color.fromRGB(0xFF7A6A), 1.0f);
            });
        }
    }

    /** 직선 예고: 붉은 띠가 대상 쪽으로 뻗어 나감 */
    public static void warnLine(Location from, Location to, int ticks) {
        if (!on()) return;
        Vector d = to.toVector().subtract(from.toVector()).setY(0);
        double len = d.length();
        if (len < 0.5) return;
        d.normalize();
        Vector side = new Vector(-d.getZ(), 0, d.getX()).multiply(0.6);
        for (int t = 0; t <= ticks; t += 2) {
            int tt = t;
            later(t, () -> {
                double f = Math.max(0.15, tt / (double) ticks);
                for (double s = 0; s <= len * f; s += 0.6) {
                    Location p = from.clone().add(d.clone().multiply(s)).add(0, 0.15, 0);
                    dust(p.clone().add(side), RED, 1.0f, 1, 0);
                    dust(p.clone().subtract(side), RED, 1.0f, 1, 0);
                }
            });
        }
    }

    /** 기 모으기: 몸 주위로 빨려드는 빛 + 번쩍이는 눈 */
    public static void windup(LivingEntity le, Color c, int ticks) {
        if (!on()) return;
        for (int t = 0; t < ticks; t += 2) {
            int tt = t;
            later(t, () -> {
                if (!le.isValid()) return;
                Location o = le.getLocation().add(0, le.getHeight() * 0.55, 0);
                double r = 1.6 - 1.2 * tt / (double) ticks;
                for (int i = 0; i < 4; i++) {
                    double a = tt * 0.6 + i * Math.PI / 2;
                    dust(o.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r), c, 1.1f, 1, 0.02);
                }
            });
        }
        later(ticks - 1, () -> { if (le.isValid()) le.getWorld().spawnParticle(Particle.FLASH, le.getEyeLocation(), 1); });
    }

    // ------------------------------------------------------------------ 기술별
    /** 도약: 뛰어오르는 잔상 */
    public static void leapTrail(LivingEntity le, int ticks) {
        if (!on()) return;
        for (int t = 1; t < ticks; t += 2) later(t, () -> {
            if (!le.isValid()) return;
            dust(le.getLocation().add(0, 0.5, 0), EARTH, 1.2f, 3, 0.2);
            le.getWorld().spawnParticle(Particle.CLOUD, le.getLocation(), 2, 0.2, 0.1, 0.2, 0.01);
        });
    }

    /** 착지 · 강타 크레이터: 충격파 + 균열 + 흙 · 먼지 */
    public static void quake(Location c, double r, Color col) {
        if (!on()) return;
        Vfx.ring(c, r, col);
        Vfx.burst(c.clone().add(0, 0.6, 0), r * 0.8, col);
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3 + 0.4;
            Vfx.beam(c.clone().add(0, 0.15, 0), c.clone().add(Math.cos(a) * r, 0.15, Math.sin(a) * r), 0.4, col);
        }
        World w = c.getWorld();
        w.spawnParticle(Particle.BLOCK_CRACK, c, 40, r * 0.4, 0.1, r * 0.4, 0, Material.DIRT.createBlockData());
        w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, c, 6, r * 0.3, 0.1, r * 0.3, 0.02);
    }

    /** 돌진: 몸을 따라가는 먼지 · 속도선 */
    public static void chargeTrail(LivingEntity le, int ticks) {
        if (!on()) return;
        for (int t = 0; t < ticks; t++) later(t, () -> {
            if (!le.isValid()) return;
            Location l = le.getLocation();
            le.getWorld().spawnParticle(Particle.CLOUD, l.clone().add(0, 0.2, 0), 3, 0.3, 0.05, 0.3, 0.02);
            dust(l.clone().add(0, le.getHeight() * 0.6, 0), RED, 1.0f, 2, 0.25);
            if (le.isOnGround()) le.getWorld().spawnParticle(Particle.BLOCK_CRACK, l, 6, 0.3, 0.05, 0.3, 0, Material.DIRT.createBlockData());
        });
    }

    public static void hitFlash(Location at, Color c) {
        if (!on()) return;
        Vfx.burst(at, 1.8, c);
        Vfx.slash(at, new Vector(1, 0, 0), 2.2, 45, Color.WHITE);
        at.getWorld().spawnParticle(Particle.CRIT, at, 10, 0.3, 0.3, 0.3, 0.3);
    }

    /** 냉기 폭발: 얼음 고리 + 솟는 얼음 가시 + 눈보라 */
    public static void frostNova(Location c, double r) {
        if (!on()) return;
        Vfx.ring(c, r, ICE);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            Location sp = c.clone().add(Math.cos(a) * r * 0.7, 0, Math.sin(a) * r * 0.7);
            later(i % 3, () -> Vfx.beam(sp, sp.clone().add(0, 1.4, 0), 0.35, ICE));
        }
        WeaponFx.element(c.clone().add(0, 0.6, 0), SkillBook.Effect.FROST, r * 0.6);
        c.getWorld().spawnParticle(Particle.SNOWFLAKE, c.clone().add(0, 1, 0), 40, r * 0.5, 0.8, r * 0.5, 0.05);
    }

    /** 원거리 발사: 손끝 광휘 + 투사체 꼬리 */
    public static void shot(LivingEntity le, Entity proj, Color c) {
        if (!on()) return;
        Vfx.burst(le.getEyeLocation().add(le.getLocation().getDirection().multiply(0.8)), 1.1, c);
        WeaponFx.trail(proj, c, 25);
    }

    /** 순간이동: 사라진 자리 · 나타난 자리 섬광 + 잔상 */
    public static void blink(Location from, Location to) {
        if (!on()) return;
        WeaponFx.blink(from, to, SkillBook.Effect.DARK);
    }

    /** 회복: 초록 나선 + 발밑 고리 + 빛 */
    public static void heal(LivingEntity le) {
        if (!on()) return;
        Location o = le.getLocation();
        Vfx.ring(o, 1.8, LIFE);
        for (int t = 0; t < 16; t++) {
            int tt = t;
            later(t, () -> {
                if (!le.isValid()) return;
                double a = tt * 0.8, y = tt / 16.0 * le.getHeight() * 1.2;
                dust(le.getLocation().add(Math.cos(a) * 0.8, y, Math.sin(a) * 0.8), LIFE, 1.2f, 1, 0);
                dust(le.getLocation().add(Math.cos(a + Math.PI) * 0.8, y, Math.sin(a + Math.PI) * 0.8), Color.WHITE, 1.0f, 1, 0);
            });
        }
        le.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, o.clone().add(0, le.getHeight() * 0.6, 0), 12, 0.4, 0.5, 0.4, 0);
    }

    /** 소환: 발밑 마법진 + 부하가 나오는 자리마다 빛기둥 */
    public static void summonCircle(Location c) {
        if (!on()) return;
        Vfx.ring(c, 3, VOID);
        later(2, () -> Vfx.ring(c, 1.8, Color.WHITE));
        c.getWorld().spawnParticle(Particle.REVERSE_PORTAL, c.clone().add(0, 0.5, 0), 30, 1.2, 0.3, 1.2, 0.05);
    }

    public static void summonPillar(Location at) {
        if (!on()) return;
        Vfx.beam(at, at.clone().add(0, 4, 0), 0.7, VOID);
        Vfx.burst(at.clone().add(0, 1, 0), 1.6, VOID);
    }

    /** 독구름: 떠 있는 동안 바닥 테두리 · 독 포자 (연출만, 구름 효과는 그대로) */
    public static void toxic(Location c, double r, int ticks) {
        if (!on()) return;
        Vfx.burst(c.clone().add(0, 0.8, 0), r, TOXIC);
        for (int t = 0; t < ticks; t += 10) later(t, () -> {
            circle(c, r, TOXIC, 1.2f);
            WeaponFx.element(c.clone().add(0, 0.5, 0), SkillBook.Effect.POISON, r * 0.5);
        });
    }

    /** 후퇴: 제자리에 잔상 */
    public static void backstep(Location from) {
        if (!on()) return;
        Vfx.burst(from.clone().add(0, 1, 0), 1.3, Color.fromRGB(0xC8C8C8));
    }
}
