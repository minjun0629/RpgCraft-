package kr.rpgcraft.feature;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.util.Vfx;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

/**
 * 무기 스킬 연출 전용 (v5.1.6). 판정 · 피해 · 범위 · 쿨타임에는 전혀 관여하지 않고 보이는 것만 더한다.
 * 속성(화염 · 빙결 · 뇌전 …)마다 다른 입자, 시전 마법진, 궤적 잔상, 착지 크레이터 등.
 */
public final class WeaponFx {
    private WeaponFx() {}

    private static boolean on() {
        RpgCraft pl = RpgCraft.get();
        return pl != null && pl.getConfig().getBoolean("vfx.weapon-extra", true);
    }

    private static void later(long t, Runnable r) {
        RpgCraft pl = RpgCraft.get();
        if (pl != null) Bukkit.getScheduler().runTaskLater(pl, r, t);
    }

    static Color light(Color c, double t) {
        return Color.fromRGB((int) (c.getRed() + (255 - c.getRed()) * t), (int) (c.getGreen() + (255 - c.getGreen()) * t), (int) (c.getBlue() + (255 - c.getBlue()) * t));
    }

    private static void dust(Location l, Color c, float size, int n, double spread) {
        l.getWorld().spawnParticle(Particle.DUST_COLOR_TRANSITION, l, n, spread, spread, spread, 0, new Particle.DustTransition(c, light(c, 0.7), size));
    }

    // ------------------------------------------------------------------ 속성 입자
    /** 속성별 입자 한 무더기 (size ≈ 퍼지는 반경) */
    public static void element(Location at, SkillBook.Effect e, double size) {
        if (!on() || at.getWorld() == null) return;
        World w = at.getWorld();
        double s = Math.max(0.3, size);
        int n = (int) Math.max(4, Math.min(24, s * 8));
        Color c = Color.fromRGB(e.rgb);
        switch (e) {
            case FIRE -> {
                w.spawnParticle(Particle.FLAME, at, n, s * 0.4, s * 0.3, s * 0.4, 0.04);
                w.spawnParticle(Particle.LAVA, at, Math.max(1, n / 6), s * 0.3, 0.1, s * 0.3, 0);
                w.spawnParticle(Particle.SMOKE_NORMAL, at, n / 3, s * 0.3, s * 0.3, s * 0.3, 0.02);
            }
            case FROST -> {
                w.spawnParticle(Particle.SNOWFLAKE, at, n, s * 0.4, s * 0.3, s * 0.4, 0.03);
                w.spawnParticle(Particle.BLOCK_CRACK, at, n / 2, s * 0.3, s * 0.3, s * 0.3, 0, Material.PACKED_ICE.createBlockData());
            }
            case POISON -> {
                w.spawnParticle(Particle.SPELL_MOB, at, n, s * 0.4, s * 0.3, s * 0.4, 1);
                dust(at, c, 0.9f, n / 2, s * 0.35);
                w.spawnParticle(Particle.SNEEZE, at, n / 3, s * 0.3, s * 0.2, s * 0.3, 0.02);
            }
            case LIGHTNING -> {
                w.spawnParticle(Particle.ELECTRIC_SPARK, at, n * 2, s * 0.4, s * 0.4, s * 0.4, 0.3);
                w.spawnParticle(Particle.END_ROD, at, n / 3, s * 0.3, s * 0.3, s * 0.3, 0.1);
            }
            case EARTH -> {
                w.spawnParticle(Particle.BLOCK_CRACK, at, n * 2, s * 0.4, 0.2, s * 0.4, 0, Material.DIRT.createBlockData());
                w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, at, Math.max(1, n / 5), s * 0.3, 0.1, s * 0.3, 0.02);
            }
            case STUN -> {
                w.spawnParticle(Particle.CRIT, at, n, s * 0.4, s * 0.4, s * 0.4, 0.3);
                w.spawnParticle(Particle.END_ROD, at, n / 3, s * 0.3, s * 0.3, s * 0.3, 0.05);
            }
            case BLEED -> {
                dust(at, c, 1.1f, n, s * 0.35);
                w.spawnParticle(Particle.BLOCK_CRACK, at, n / 2, s * 0.3, s * 0.3, s * 0.3, 0, Material.REDSTONE_BLOCK.createBlockData());
                w.spawnParticle(Particle.DAMAGE_INDICATOR, at, Math.max(1, n / 4), s * 0.3, s * 0.3, s * 0.3, 0.1);
            }
            case HOLY -> {
                w.spawnParticle(Particle.END_ROD, at, n, s * 0.4, s * 0.4, s * 0.4, 0.05);
                w.spawnParticle(Particle.TOTEM, at, n / 2, s * 0.3, s * 0.4, s * 0.3, 0.2);
                dust(at, c, 1.0f, n / 2, s * 0.35);
            }
            case DARK -> {
                w.spawnParticle(Particle.REVERSE_PORTAL, at, n, s * 0.4, s * 0.4, s * 0.4, 0.05);
                w.spawnParticle(Particle.SQUID_INK, at, n / 3, s * 0.3, s * 0.3, s * 0.3, 0.02);
                dust(at, c, 1.0f, n / 2, s * 0.35);
            }
            case WIND -> {
                w.spawnParticle(Particle.CLOUD, at, n, s * 0.4, s * 0.3, s * 0.4, 0.06);
                w.spawnParticle(Particle.SWEEP_ATTACK, at, Math.max(1, n / 8), s * 0.3, 0.2, s * 0.3, 0);
            }
            case GUARD -> {
                dust(at, c, 1.1f, n, s * 0.4);
                w.spawnParticle(Particle.WAX_OFF, at, n / 2, s * 0.4, s * 0.4, s * 0.4, 0.3);
            }
            default -> {   // 강철: 불똥
                w.spawnParticle(Particle.CRIT, at, n, s * 0.35, s * 0.3, s * 0.35, 0.35);
                w.spawnParticle(Particle.ELECTRIC_SPARK, at, n / 3, s * 0.3, s * 0.3, s * 0.3, 0.2);
            }
        }
    }

    // ------------------------------------------------------------------ 시전 · 적중
    /** 시전: 발밑 2겹 마법진 + 속성 기둥 4개가 솟았다 사라짐 + 손끝 광휘 */
    public static void castCircle(Location feet, Location hand, SkillBook.Effect e) {
        if (!on()) return;
        Color c = Color.fromRGB(e.rgb);
        Vfx.ring(feet, 2.6, light(c, 0.3));
        later(2, () -> Vfx.ring(feet, 1.4, Color.WHITE));
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2 + Math.PI / 4;
            Location pl = feet.clone().add(Math.cos(a) * 1.6, 0.05, Math.sin(a) * 1.6);
            later(i, () -> {
                Vfx.beam(pl, pl.clone().add(0, 1.8, 0), 0.35, c);
                element(pl.clone().add(0, 0.6, 0), e, 0.3);
            });
        }
        element(hand, e, 0.5);
    }

    /** 적중: 속성 폭발 + 작은 충격 고리 */
    public static void impact(Location at, SkillBook.Effect e) {
        if (!on()) return;
        element(at, e, 0.8);
        Vfx.ring(at.clone().subtract(0, 0.6, 0), 1.3, Color.fromRGB(e.rgb));
    }

    // ------------------------------------------------------------------ 모양별 추가 연출
    /** 원뿔 참격: 부채꼴을 따라 속성 입자가 휩쓸고 지나감 */
    public static void coneSweep(Location feet, Vector f, double range, SkillBook.Effect e) {
        if (!on()) return;
        Vector side = new Vector(-f.getZ(), 0, f.getX());
        for (int i = -4; i <= 4; i++) {
            int ii = i;
            later(Math.abs(i) / 2, () -> {
                double a = ii * 0.14;
                Vector d = f.clone().multiply(Math.cos(a)).add(side.clone().multiply(Math.sin(a) * 2.2)).normalize();
                element(feet.clone().add(0, 1, 0).add(d.multiply(range * 0.7)), e, 0.5);
            });
        }
    }

    /** 폭발: 사방으로 속성 분출 기둥 */
    public static void eruption(Location c, double r, SkillBook.Effect e) {
        if (!on()) return;
        Color col = Color.fromRGB(e.rgb);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            Location pl = c.clone().add(Math.cos(a) * r * 0.7, 0.1, Math.sin(a) * r * 0.7);
            later(i % 3, () -> {
                Vfx.beam(pl, pl.clone().add(0, 2.2, 0), 0.5, col);
                element(pl.clone().add(0, 0.8, 0), e, 0.5);
            });
        }
        c.getWorld().spawnParticle(Particle.FLASH, c.clone().add(0, 1, 0), 1);
    }

    /** 관통 광선: 광선을 감싸고 도는 속성 입자 + 뿌리 · 끝 섬광 */
    public static void lineSpiral(Location from, Vector dir, double len, SkillBook.Effect e) {
        if (!on()) return;
        Vector u = dir.clone().normalize();
        Vector a = Math.abs(u.getY()) > 0.9 ? new Vector(1, 0, 0) : new Vector(0, 1, 0);
        Vector p1 = u.getCrossProduct(a).normalize(), p2 = u.getCrossProduct(p1).normalize();
        for (double t = 0.5; t <= len; t += 0.8) {
            double ang = t * 1.4;
            Location pnt = from.clone().add(u.clone().multiply(t)).add(p1.clone().multiply(Math.cos(ang) * 0.6)).add(p2.clone().multiply(Math.sin(ang) * 0.6));
            element(pnt, e, 0.2);
        }
        Vfx.burst(from.clone().add(u.clone().multiply(0.6)), 1.4, Color.WHITE);
    }

    /** 돌진 · 도약 중 잔상 (몸을 따라가는 빛 조각) */
    public static void afterimage(Entity p, SkillBook.Effect e, int ticks) {
        if (!on()) return;
        Color c = Color.fromRGB(e.rgb);
        for (int t = 0; t < ticks; t++) {
            later(t, () -> {
                if (!p.isValid()) return;
                Location l = p.getLocation().add(0, 1, 0);
                Vfx.burst(l, 1.2, light(c, 0.2));
                element(l, e, 0.3);
            });
        }
    }

    /** 도약 착지 크레이터: 갈라지는 땅 + 속성 분출 */
    public static void crater(Location c, double r, SkillBook.Effect e) {
        if (!on()) return;
        Color col = Color.fromRGB(e.rgb);
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3 + 0.3;
            Location end = c.clone().add(Math.cos(a) * r * 0.9, 0.15, Math.sin(a) * r * 0.9);
            Vfx.beam(c.clone().add(0, 0.15, 0), end, 0.45, col);
            later(2, () -> element(end.clone().add(0, 0.4, 0), e, 0.5));
        }
        c.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, c, 1);
        c.getWorld().spawnParticle(Particle.BLOCK_CRACK, c, 40, r * 0.4, 0.1, r * 0.4, 0, Material.DIRT.createBlockData());
        element(c.clone().add(0, 0.5, 0), e, r * 0.5);
    }

    /** 순간이동: 사라진 자리와 나타난 자리에 섬광 + 잔상 줄 */
    public static void blink(Location from, Location to, SkillBook.Effect e) {
        if (!on()) return;
        Color c = Color.fromRGB(e.rgb);
        Vfx.burst(from.clone().add(0, 1, 0), 2.2, c);
        element(from.clone().add(0, 1, 0), e, 0.7);
        Vector d = to.toVector().subtract(from.toVector());
        for (int i = 1; i <= 4; i++) {
            Location g = from.clone().add(d.clone().multiply(i / 5.0)).add(0, 1, 0);
            later(i / 2, () -> Vfx.burst(g, 1.4, light(c, 0.3)));
        }
        later(1, () -> { Vfx.burst(to.clone().add(0, 1, 0), 2.4, Color.WHITE); element(to.clone().add(0, 1, 0), e, 0.7); });
    }

    /** 구체 비행 중: 속성 입자 궤도 */
    public static void orbTrail(Location at, SkillBook.Effect e, int tick) {
        if (!on()) return;
        Color c = Color.fromRGB(e.rgb);
        for (int i = 0; i < 3; i++) {
            double a = tick * 0.9 + i * Math.PI * 2 / 3;
            dust(at.clone().add(Math.cos(a) * 0.5, Math.sin(a * 1.3) * 0.3, Math.sin(a) * 0.5), c, 1.0f, 1, 0.02);
        }
        element(at, e, 0.25);
    }

    /** 폭우 · 소용돌이 예고 원 (바닥에 속성 마법진) */
    public static void zone(Location c, double r, SkillBook.Effect e) {
        if (!on()) return;
        Color col = Color.fromRGB(e.rgb);
        int n = (int) Math.max(12, r * 8);
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            dust(c.clone().add(Math.cos(a) * r, 0.15, Math.sin(a) * r), col, 1.2f, 1, 0.02);
        }
        element(c.clone().add(0, 0.3, 0), e, r * 0.4);
    }

    /** 소용돌이: 바깥에서 안으로 도는 나선 */
    public static void vortex(Location c, double r, SkillBook.Effect e, int ticks) {
        if (!on()) return;
        Color col = Color.fromRGB(e.rgb);
        for (int t = 0; t < ticks; t += 2) {
            int tt = t;
            later(t, () -> {
                double rr = r * (1 - tt / (double) ticks);
                for (int i = 0; i < 8; i++) {
                    double a = tt * 0.45 + i * Math.PI / 4;
                    dust(c.clone().add(Math.cos(a) * rr, 0.4 + (i % 2) * 0.6, Math.sin(a) * rr), col, 1.1f, 1, 0.02);
                }
            });
        }
    }

    /** 화살 · 투척물 꼬리 */
    public static void trail(Entity proj, Color c, int ticks) {
        if (!on()) return;
        for (int t = 1; t < ticks; t++) {
            later(t, () -> {
                if (!proj.isValid() || proj.isOnGround()) return;
                dust(proj.getLocation(), c, 0.9f, 1, 0.02);
                proj.getWorld().spawnParticle(Particle.END_ROD, proj.getLocation(), 1, 0, 0, 0, 0);
            });
        }
    }
}
