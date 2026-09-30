package kr.rpgcraft.boss;

import org.bukkit.*;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import kr.rpgcraft.util.FxBudget;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 보스 스킬 이펙트 (v5.7.0) — 블록 디스플레이로 만드는 큰 연출. 실제 지형은 바꾸지 않고 잠깐 뒤 사라진다.
 *  - 바닥 예고판: 빛나는 반투명 판이 위험 범위를 채우며 차오름 (원 · 띠 · 칸 · 고리 · 부채꼴) — 테두리가 벽 너머로도 빛남
 *  - 착탄: 땅이 솟구치는 지진 · 흩날리는 파편 · 땅에서 솟는 기둥 · 하늘에서 떨어지는 거대 운석 · 밀려오는 벽
 */
public final class BossFx {
    private BossFx() {}

    public static final NamespacedKey KEY = new NamespacedKey("rpgcraft", "boss_fx");
    public static final Material RED = Material.RED_STAINED_GLASS, ORANGE = Material.ORANGE_STAINED_GLASS, SAFE = Material.LIME_STAINED_GLASS;

    private static Plugin plugin;

    public static void init(Plugin p) {
        plugin = p;
        FxBudget.start(p);
        for (World w : Bukkit.getWorlds())   // 서버가 도중에 꺼져 남은 조각 치우기
            for (BlockDisplay d : w.getEntitiesByClass(BlockDisplay.class))
                if (((Entity) d).getPersistentDataContainer().has(KEY, org.bukkit.persistence.PersistentDataType.BYTE)) d.remove();
    }

    private static boolean on() {
        return plugin != null && plugin.getConfig().getBoolean("vfx.boss-blocks", true);
    }

    private static void later(long t, Runnable r) {
        if (plugin != null) Bukkit.getScheduler().runTaskLater(plugin, r, Math.max(0, t));
    }

    /** 보스별 테마 블록 (파편 · 지진 · 기둥) */
    public static Material theme(String bossId) {
        if (bossId == null) return Material.STONE;
        return switch (bossId) {
            case "witch", "field_swamp_witch" -> Material.MOSS_BLOCK;
            case "elf_queen" -> Material.AZALEA_LEAVES;
            case "dwarf_king", "field_ancient_golem" -> Material.DEEPSLATE_BRICKS;
            case "harpy_queen" -> Material.CALCITE;
            case "sea_gatekeeper", "megalodon", "kraken" -> Material.PRISMARINE_BRICKS;
            case "bungbung", "volcano_giant", "field_flame_knight" -> Material.MAGMA_BLOCK;
            case "desert_nightmare" -> Material.SANDSTONE;
            case "siphonia" -> Material.EMERALD_BLOCK;
            case "kain" -> Material.BLACKSTONE;
            case "frost_queen", "field_frost_bear", "field_frost_lich" -> Material.PACKED_ICE;
            case "void_apostle" -> Material.CRYING_OBSIDIAN;
            case "thunder_god" -> Material.GOLD_BLOCK;
            case "primordial_dragon" -> Material.PURPUR_BLOCK;
            case "vengeful_spirit", "field_deep_warden" -> Material.SCULK;
            case "balrog" -> Material.NETHERRACK;
            case "field_boar_king", "field_ravager" -> Material.COARSE_DIRT;
            case "field_bandit_lord" -> Material.COBBLESTONE;
            default -> Material.STONE;
        };
    }

    // ------------------------------------------------------------------ 기본 부품
    /** 로컬 상자 [0,1]^3 을 (sx,sy,sz) 로 늘리고 yaw 만큼 돌린 뒤, 기준점(anchor, 로컬 좌표)이 (0,lift,0) 에 오도록 */
    private static Transformation tf(float sx, float sy, float sz, float yaw, Vector3f anchor, float lift) {
        Quaternionf q = new Quaternionf().rotateY(yaw);
        Vector3f a = new Vector3f(anchor.x * sx, anchor.y * sy, anchor.z * sz);
        q.transform(a);
        return new Transformation(new Vector3f(-a.x, lift - a.y, -a.z), q, new Vector3f(sx, sy, sz), new Quaternionf());
    }

    private static BlockDisplay spawn(Location at, Material m, Transformation t0, boolean glow, Color glowColor) {
        Location l = at.clone();
        l.setYaw(0);
        l.setPitch(0);
        return l.getWorld().spawn(l, BlockDisplay.class, d -> {
            d.setBlock(m.createBlockData());
            d.setTransformation(t0);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setPersistent(false);
            ((Entity) d).getPersistentDataContainer().set(KEY, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
            if (glow) {
                d.setGlowing(true);
                if (glowColor != null) d.setGlowColorOverride(glowColor);
            }
        });
    }

    private static void animate(BlockDisplay d, Transformation to, int ticks) {
        later(1, () -> {
            if (!d.isValid()) return;
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(Math.max(1, ticks));
            d.setTransformation(to);
        });
    }

    private static void removeLater(BlockDisplay d, int ticks) {
        later(ticks, () -> { FxBudget.done(1); if (d.isValid()) d.remove(); });
    }

    private static final Vector3f CENTER_FLAT = new Vector3f(0.5f, 0f, 0.5f), ROOT_LINE = new Vector3f(0.5f, 0f, 0f), BOTTOM = new Vector3f(0.5f, 0f, 0.5f);

    private static float yawOf(Vector dir) {
        return (float) Math.atan2(dir.getX(), dir.getZ());   // 로컬 +z 가 dir 을 향하도록
    }

    // ------------------------------------------------------------------ 바닥 예고판
    /** 원: 3장의 판을 30° 씩 돌려 겹친 12각 별 모양이 가운데부터 차오름 */
    public static void disc(Location c, double r, int ticks, Material m, Color glow) {
        if (!on() || r <= 0) return;
        float a = (float) (r * 2 * 0.8);
        FxBudget.force(2);
        for (int k = 0; k < 2; k++) {   // v5.9.3 렉 줄이기: 3장 → 2장 (45° 겹친 8각 별)
            float yaw = (float) Math.toRadians(k * 45);
            BlockDisplay d = spawn(c, m, tf(0.05f, 0.04f, 0.05f, yaw, CENTER_FLAT, 0.02f), k == 0, glow);
            animate(d, tf(a, 0.04f, a, yaw, CENTER_FLAT, 0.02f), ticks);
            removeLater(d, ticks + 4);
        }
    }

    /** 띠: 뿌리에서 끝으로 뻗어 나감 */
    public static void rect(Location from, Vector dir, double len, double width, int ticks, Material m, Color glow) {
        if (!on()) return;
        Vector d0 = dir.clone().setY(0);
        if (d0.lengthSquared() < 1e-6) return;
        float yaw = yawOf(d0.normalize());
        FxBudget.force(1);
        BlockDisplay d = spawn(from, m, tf((float) width, 0.04f, 0.05f, yaw, ROOT_LINE, 0.03f), true, glow);
        animate(d, tf((float) width, 0.04f, (float) len, yaw, ROOT_LINE, 0.03f), ticks);
        removeLater(d, ticks + 4);
    }

    /** 사각 칸 (바둑판) */
    public static void square(Location c, double half, int ticks, Material m) {
        if (!on()) return;
        FxBudget.force(1);
        BlockDisplay d = spawn(c, m, tf(0.05f, 0.04f, 0.05f, 0, CENTER_FLAT, 0.02f), false, null);
        animate(d, tf((float) (half * 2), 0.04f, (float) (half * 2), 0, CENTER_FLAT, 0.02f), ticks);
        removeLater(d, ticks + 4);
    }

    /** 고리: 안쪽 r 은 비워 둔 채 r~R 을 16조각으로 */
    public static void ring(Location o, double r, double R, int ticks, Material m, Color glow) {
        if (!on() || R <= r) return;
        int n = 12;   // v5.9.3: 16 → 12 조각
        FxBudget.force(n);
        double mid = (r + R) / 2, seg = 2 * Math.PI * mid / n * 1.12;
        for (int i = 0; i < n; i++) {
            double ang = i * Math.PI * 2 / n;
            Vector out = new Vector(Math.cos(ang), 0, Math.sin(ang));
            Location at = o.clone().add(out.clone().multiply(r));
            float yaw = yawOf(out);
            BlockDisplay d = spawn(at, m, tf((float) seg, 0.04f, 0.05f, yaw, ROOT_LINE, 0.02f), i % 6 == 0, glow);   // 빛나는 테두리는 두 조각만 (외곽선 효과가 무거움)
            animate(d, tf((float) seg, 0.04f, (float) (R - r), yaw, ROOT_LINE, 0.02f), ticks);
            removeLater(d, ticks + 4);
        }
    }

    /** 부채꼴: 가는 띠 여러 장을 부채처럼 */
    public static void cone(Location o, Vector dir, double R, double halfDeg, int ticks, Material m, Color glow) {
        if (!on()) return;
        int n = Math.max(3, (int) Math.ceil(halfDeg * 2 / 18));   // v5.9.3: 12° → 18° 마다 한 장
        FxBudget.force(n);
        double step = Math.toRadians(halfDeg * 2 / n);
        double w = 2 * R * Math.tan(step / 2) * 1.05;
        for (int i = 0; i < n; i++) {
            Vector dv = dir.clone().setY(0).normalize().rotateAroundY(Math.toRadians(-halfDeg) + step * (i + 0.5));
            float yaw = yawOf(dv);
            BlockDisplay d = spawn(o, m, tf(0.2f, 0.04f, 0.05f, yaw, ROOT_LINE, 0.03f), i == 0 || i == n - 1, glow);
            animate(d, tf((float) w, 0.04f, (float) R, yaw, ROOT_LINE, 0.03f), ticks);
            removeLater(d, ticks + 4);
        }
    }

    // ------------------------------------------------------------------ 착탄 연출
    /** 지진: 반경 안의 땅 조각들이 튀어 올랐다가 가라앉음 */
    public static void quake(Location c, double r, Material block) {
        if (!on()) return;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int n = FxBudget.grant((int) Math.min(12, 4 + r * 1.2));   // v5.9.3: 최대 36 → 12, 여유가 없으면 더 적게
        for (int i = 0; i < n; i++) {
            double ang = rnd.nextDouble(Math.PI * 2), rr = r * Math.sqrt(rnd.nextDouble(0.08, 1));
            Location at = c.clone().add(Math.cos(ang) * rr, 0, Math.sin(ang) * rr);
            float s = (float) rnd.nextDouble(0.5, 1.1), yaw = (float) rnd.nextDouble(Math.PI * 2);
            long delay = (long) (rr / Math.max(1, r) * 6);   // 가운데부터 바깥으로 번짐
            later(delay, () -> {
                BlockDisplay d = spawn(at, block, tf(s, s * 0.6f, s, yaw, BOTTOM, -0.5f), false, null);
                animate(d, tf(s, s * 0.9f, s, yaw + 0.4f, BOTTOM, (float) rnd.nextDouble(0.3, 0.9)), 4);
                later(8, () -> animate(d, tf(s * 0.2f, s * 0.2f, s * 0.2f, yaw + 0.8f, BOTTOM, -0.3f), 14));
                removeLater(d, 26);
                at.getWorld().spawnParticle(Particle.BLOCK_CRACK, at.clone().add(0, 0.3, 0), 2, 0.3, 0.2, 0.3, 0, block.createBlockData());
            });
        }
    }

    /** 파편: 사방으로 튀었다가 떨어지며 뒹굶 */
    public static void debris(Location c, int n, Material block, double power) {
        if (!on()) return;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int cnt = FxBudget.grant(Math.min(8, n / 2 + 1));   // v5.9.3: 최대 24 → 8
        for (int i = 0; i < cnt; i++) {
            double ang = rnd.nextDouble(Math.PI * 2), dist = power * rnd.nextDouble(0.6, 1.4);
            float s = (float) rnd.nextDouble(0.25, 0.6), yaw = (float) rnd.nextDouble(Math.PI * 2);
            float dx = (float) (Math.cos(ang) * dist), dz = (float) (Math.sin(ang) * dist), up = (float) (power * rnd.nextDouble(0.5, 1.0));
            BlockDisplay d = spawn(c, block, tf(s, s, s, yaw, BOTTOM, 0.2f), false, null);
            later(1, () -> {   // 위로 솟음
                if (!d.isValid()) return;
                d.setInterpolationDelay(0);
                d.setInterpolationDuration(7);
                Transformation t = tf(s, s, s, yaw + 1.5f, BOTTOM, up);
                d.setTransformation(new Transformation(new Vector3f(t.getTranslation()).add(dx * 0.6f, 0, dz * 0.6f), t.getLeftRotation(), t.getScale(), t.getRightRotation()));
            });
            later(9, () -> {   // 떨어지며 작아짐
                if (!d.isValid()) return;
                d.setInterpolationDelay(0);
                d.setInterpolationDuration(9);
                Transformation t = tf(s * 0.3f, s * 0.3f, s * 0.3f, yaw + 3f, BOTTOM, 0f);
                d.setTransformation(new Transformation(new Vector3f(t.getTranslation()).add(dx, 0, dz), t.getLeftRotation(), t.getScale(), t.getRightRotation()));
            });
            removeLater(d, 20);
        }
    }

    /** 땅에서 솟는 기둥 (분출) */
    public static void pillar(Location c, double radius, double height, Material block) {
        if (!on()) return;
        if (FxBudget.grant(1) == 0) return;
        float w = (float) (radius * 1.3);
        BlockDisplay d = spawn(c, block, tf(w, 0.05f, w, 0.3f, BOTTOM, -0.2f), false, null);
        animate(d, tf(w, (float) height, w, 0.3f, BOTTOM, -0.2f), 4);
        later(12, () -> animate(d, tf(w * 0.4f, 0.05f, w * 0.4f, 0.3f, BOTTOM, -0.4f), 10));
        removeLater(d, 24);
        debris(c.clone().add(0, height * 0.6, 0), 4, block, 1.6);
    }

    /** 하늘에서 떨어지는 거대 운석 (fall 틱 동안 낙하, 도착하면 사라짐) */
    public static void meteor(Location target, double size, int fall, Material block) {
        if (!on() || fall <= 1 || FxBudget.grant(1) == 0) return;
        float s = (float) size;
        Location top = target.clone().add(-4, 22, -2);
        BlockDisplay d = spawn(top, block, tf(s, s, s, 0.6f, new Vector3f(0.5f, 0.5f, 0.5f), 0), true, Color.fromRGB(0xFF6A1F));
        later(1, () -> {
            if (!d.isValid()) return;
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(fall - 1);
            Transformation t = tf(s, s, s, 3.2f, new Vector3f(0.5f, 0.5f, 0.5f), 0);
            d.setTransformation(new Transformation(new Vector3f(t.getTranslation()).add(4, -22 + s * 0.4f, 2), t.getLeftRotation(), t.getScale(), t.getRightRotation()));
        });
        for (int t = 2; t < fall; t += 4) {   // v5.9.3: 불꼬리 입자 절반
            int tt = t;
            later(t, () -> {   // 불꼬리
                double k = tt / (double) fall;
                Location at = top.clone().add(4 * k, -22 * k + s * 0.4, 2 * k);
                at.getWorld().spawnParticle(Particle.FLAME, at, 6, s * 0.3, s * 0.3, s * 0.3, 0.02);
                at.getWorld().spawnParticle(Particle.SMOKE_LARGE, at, 2, s * 0.2, s * 0.2, s * 0.2, 0.01);
            });
        }
        removeLater(d, fall + 1);
    }

    /** 밀려오는 벽: 틈을 사이에 둔 두 장의 벽이 travel 칸을 ticks 동안 이동 */
    public static void wall(Location start, Vector fd, double halfWidth, double gapAt, double gapHalf, double travel, int ticks, int delay, Material block) {
        if (!on()) return;
        Vector f = fd.clone().setY(0).normalize();
        Vector side = new Vector(-f.getZ(), 0, f.getX());
        float yaw = yawOf(side);   // 벽의 긴 쪽(로컬 +z)이 옆 방향
        double[][] segs = {{-halfWidth, gapAt - gapHalf}, {gapAt + gapHalf, halfWidth}};
        for (double[] sg : segs) {
            double len = sg[1] - sg[0];
            if (len <= 0.2) continue;
            FxBudget.force(1);
            Location root = start.clone().add(side.clone().multiply(sg[0]));
            BlockDisplay d = spawn(root, block, tf(0.8f, 0.1f, (float) len, yaw, ROOT_LINE, 0f), false, null);
            later(Math.max(0, delay - 6), () -> animate(d, tf(0.8f, 3.2f, (float) len, yaw, ROOT_LINE, 0f), 6));   // 솟아오름
            later(delay, () -> {
                if (!d.isValid()) return;
                d.setInterpolationDelay(0);
                d.setInterpolationDuration(ticks);
                Transformation t = tf(0.8f, 3.2f, (float) len, yaw, ROOT_LINE, 0f);
                Vector mv = f.clone().multiply(travel);
                d.setTransformation(new Transformation(new Vector3f(t.getTranslation()).add((float) mv.getX(), 0, (float) mv.getZ()), t.getLeftRotation(), t.getScale(), t.getRightRotation()));
            });
            later(delay + ticks, () -> animate(d, tf(0.8f, 0.05f, (float) len, yaw, ROOT_LINE, 0f), 6));
            removeLater(d, delay + ticks + 8);
        }
    }

    /** 화면이 번쩍 + 땅울림 (큰 기술 착탄) */
    public static void boom(Location c, double r, Material block) {
        World w = c.getWorld();
        w.spawnParticle(Particle.FLASH, c.clone().add(0, 1, 0), 2);
        w.spawnParticle(Particle.EXPLOSION_HUGE, c, Math.max(1, (int) (r / 4)), r * 0.3, 0.3, r * 0.3);
        quake(c, r, block);
        debris(c, (int) Math.min(12, r), block, Math.min(5, 1.2 + r * 0.25));
        for (org.bukkit.entity.Player p : w.getPlayers())
            if (p.getLocation().distanceSquared(c) < (r + 16) * (r + 16)) p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 0.55f);
    }
}
