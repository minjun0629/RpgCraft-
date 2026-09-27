package kr.rpgcraft.util;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/** 파티클 도형 유틸 (원, 나선, 선, 구, 폭발) - 스킬/레벨업/강화 연출용 */
public final class Fx {
    private Fx() {}

    public static void dust(Location l, Color c, float size) {
        // 먼지 입자의 수명은 크기에 비례 → 작게 뿌려 잔상이 남지 않게
        l.getWorld().spawnParticle(Particle.DUST_COLOR_TRANSITION, l, 1, 0, 0, 0, 0, new Particle.DustTransition(c, light(c), Math.min(size, 0.7f)));
    }

    /** 밝게 섞은 색 (입자가 사라지며 빛나는 느낌) */
    public static Color light(Color c) {
        return Color.fromRGB(Math.min(255, c.getRed() + (255 - c.getRed()) * 2 / 3), Math.min(255, c.getGreen() + (255 - c.getGreen()) * 2 / 3),
                Math.min(255, c.getBlue() + (255 - c.getBlue()) * 2 / 3));
    }

    /** 타격 지점 폭발: 색 입자 고리 + 마법 불꽃 */
    public static void impact(Location l, Color c) {
        if (Vfx.enabled()) {
            Vfx.burst(l, 1.4, c);
            l.getWorld().spawnParticle(Particle.CRIT, l, 6, 0.15, 0.15, 0.15, 0.3);
            return;
        }
        World w = l.getWorld();
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI / 5;
            w.spawnParticle(Particle.DUST_COLOR_TRANSITION, l.clone().add(Math.cos(a) * 0.45, Math.sin(a) * 0.45, Math.sin(a + 1) * 0.2), 1, 0, 0, 0, 0,
                    new Particle.DustTransition(c, Color.WHITE, 1.1f));
        }
        w.spawnParticle(Particle.CRIT_MAGIC, l, 8, 0.15, 0.15, 0.15, 0.35);
    }

    public static void circle(Location c, double r, int points, Color color, float size) {
        if (Vfx.enabled()) { Vfx.ring(c, r, color); return; }
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            dust(c.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r), color, size);
        }
    }

    public static void circle(Location c, double r, int points, Particle p) {
        World w = c.getWorld();
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            w.spawnParticle(p, c.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r), 1, 0, 0, 0, 0);
        }
    }

    public static void line(Location from, Location to, double step, Color color, float size) {
        if (Vfx.enabled()) { Vfx.beam(from, to, Math.max(0.5, size * 0.6), color); return; }
        Vector d = to.toVector().subtract(from.toVector());
        double len = d.length();
        if (len < 1e-3) return;
        d.normalize().multiply(step);
        Location cur = from.clone();
        for (double t = 0; t <= len; t += step) {
            dust(cur, color, size);
            cur.add(d);
        }
    }

    public static void sphere(Location c, double r, int points, Color color, float size) {
        double phi = Math.PI * (3 - Math.sqrt(5));
        for (int i = 0; i < points; i++) {
            double y = 1 - (i / (double) (points - 1)) * 2;
            double rad = Math.sqrt(1 - y * y);
            double th = phi * i;
            dust(c.clone().add(Math.cos(th) * rad * r, y * r, Math.sin(th) * rad * r), color, size);
        }
    }

    /** 대상 주위로 올라가는 이중 나선 (레벨업/각성 연출) */
    public static void helix(Plugin plugin, org.bukkit.entity.Entity target, double height, double r, int ticks, Color a, Color b) {
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t >= ticks || !target.isValid()) {
                    cancel();
                    return;
                }
                Location base = target.getLocation();
                for (int k = 0; k < 3; k++) {
                    double prog = (t * 3 + k) / (double) (ticks * 3);
                    double ang = prog * Math.PI * 6;
                    double y = prog * height;
                    dust(base.clone().add(Math.cos(ang) * r, y, Math.sin(ang) * r), a, 1.3f);
                    dust(base.clone().add(Math.cos(ang + Math.PI) * r, y, Math.sin(ang + Math.PI) * r), b, 1.3f);
                }
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** 퍼져나가는 충격파 */
    public static void shockwave(Plugin plugin, Location c, double maxR, Color color) {
        if (Vfx.enabled()) { Vfx.ring(c, maxR, color); return; }
        new BukkitRunnable() {
            double r = 0.5;

            @Override
            public void run() {
                if (r > maxR) {
                    cancel();
                    return;
                }
                circle(c.clone().add(0, 0.15, 0), r, (int) (r * 14) + 8, color, 1.6f);
                r += 0.6;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    public static Color grade(String g) {
        return switch (g) {
            case "RARE" -> Color.fromRGB(0x55FF55);
            case "UNIQUE" -> Color.fromRGB(0xFFD23F);
            case "LEGEND" -> Color.fromRGB(0xFF4040);
            case "MYTHIC" -> Color.fromRGB(0xE070FF);
            default -> Color.WHITE;
        };
    }
}
