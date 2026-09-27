package kr.rpgcraft.util;

import kr.rpgcraft.RpgCraft;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 잔상 없는 고품질 스킬 이펙트.
 * 리소스팩의 이펙트 모델(초승달 참격 · 충격파 고리 · 광선 · 별 폭발)을 ItemDisplay 로 최대 밝기로 띄우고,
 * 2~3틱 동안 커지며 번쩍인 뒤 곧바로 지운다 → 화면에 남는 잔상이 없다.
 * 색은 가죽 말 갑옷 염색으로 자유롭게 바꾸고, 흰 중심 판이 겹쳐 빛나는 칼날처럼 보인다.
 * 리소스팩을 받지 않은 플레이어에게는 보이지 않게 숨긴다.
 */
public final class Vfx {
    public static final int SLASH = 9500, RING = 9501, BEAM = 9502, BURST = 9503, SPARK = 9504, WAVE = 9505, TRAIL = 9506;

    private Vfx() {}

    public static boolean enabled() {
        RpgCraft pl = RpgCraft.get();
        return pl != null && pl.getConfig().getBoolean("vfx.enabled", true);
    }

    /** 겹 이펙트 (잔상 · 반짝임 · 원판 · 입자). 기술의 판정 · 피해 · 범위와는 무관한 순수 연출 — config vfx.layers */
    private static boolean layers() {
        RpgCraft pl = RpgCraft.get();
        return pl != null && pl.getConfig().getBoolean("vfx.layers", true);
    }

    private static void later(long ticks, Runnable r) {
        RpgCraft pl = RpgCraft.get();
        if (pl != null) Bukkit.getScheduler().runTaskLater(pl, r, ticks);
    }

    private static Color white(Color c, double t) {
        return Color.fromRGB((int) (c.getRed() + (255 - c.getRed()) * t), (int) (c.getGreen() + (255 - c.getGreen()) * t), (int) (c.getBlue() + (255 - c.getBlue()) * t));
    }

    private static Color dark(Color c, double t) {
        return Color.fromRGB((int) (c.getRed() * (1 - t)), (int) (c.getGreen() * (1 - t)), (int) (c.getBlue() * (1 - t)));
    }

    private static void dust(Location l, Color c, float size, int n, double spread) {
        l.getWorld().spawnParticle(org.bukkit.Particle.DUST_COLOR_TRANSITION, l, n, spread, spread, spread, 0,
                new org.bukkit.Particle.DustTransition(c, white(c, 0.7), size));
    }

    private static ItemStack item(int cmd, Color c) {
        ItemStack it = new ItemStack(Material.LEATHER_HORSE_ARMOR);
        LeatherArmorMeta m = (LeatherArmorMeta) it.getItemMeta();
        m.setColor(c);
        m.setCustomModelData(cmd);
        it.setItemMeta(m);
        return it;
    }

    /** 이펙트 하나 띄우기: 시작 크기 → 끝 크기로 커지며 life 틱 뒤 삭제 */
    private static void spawn(Location at, int cmd, Color c, Quaternionf rot, float s0, float s1, float sy, int life, boolean billboard) {
        spawn(at, cmd, c, rot, new Vector3f(s0, 1, s0), new Vector3f(s1, 1, s1), life, billboard);
    }

    private static void spawn(Location at0, int cmd, Color c, Quaternionf rot0, Vector3f sc0, Vector3f sc1, int life, boolean billboard) {
        RpgCraft pl = RpgCraft.get();
        if (pl == null || at0.getWorld() == null) return;
        // (방향 보정 1) 엔티티 자체의 yaw/pitch 를 0 으로 — 위치를 눈 위치에서 복사하면 시선 방향 회전이 한 번 더 들어가 어긋났음
        Location at = at0.clone();
        at.setYaw(0);
        at.setPitch(0);
        // (방향 보정 2) 게임이 ItemDisplay 의 아이템을 Y축으로 180° 돌려 그리므로 미리 180° 돌려 상쇄 → 참격이 정확히 앞을 향함
        Quaternionf rot = new Quaternionf(rot0).rotateY((float) Math.PI);
        ItemDisplay d = at.getWorld().spawn(at, ItemDisplay.class, x -> {
            x.setItemStack(item(cmd, c));
            x.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            x.setBrightness(new Display.Brightness(15, 15));
            x.setPersistent(false);
            x.setShadowRadius(0);
            x.setViewRange(1.5f);
            if (billboard) x.setBillboard(Display.Billboard.CENTER);
            x.setTransformation(new Transformation(new Vector3f(), rot, sc0, new Quaternionf()));
        });
        for (Player p : at.getWorld().getPlayers())                           // 리소스팩이 없는 사람에게는 숨김
            if (!pl.pack().hasPack(p)) p.hideEntity(pl, d);
        Bukkit.getScheduler().runTask(pl, () -> {
            if (!d.isValid()) return;
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(Math.max(1, life - 1));
            d.setTransformation(new Transformation(new Vector3f(), rot, sc1, new Quaternionf()));
        });
        Bukkit.getScheduler().runTaskLater(pl, d::remove, life);
    }

    private static Quaternionf orient(Vector dir, double rollDeg) {
        double yaw = Math.atan2(-dir.getX(), dir.getZ());
        double pitch = Math.asin(Math.max(-1, Math.min(1, -dir.clone().normalize().getY())));
        return new Quaternionf().rotateY((float) -yaw).rotateX((float) pitch).rotateZ((float) Math.toRadians(rollDeg));
    }

    /** 초승달 참격: 중심 at, 바라보는 방향 dir 쪽으로 볼록, size 블록, roll 로 기울기 (90 = 세로 내려베기) */
    public static void slash(Location at, Vector dir, double size, double rollDeg, Color c) {
        if (!enabled()) return;
        Quaternionf q = orient(dir, rollDeg);
        spawn(at, SLASH, c, q, (float) size * 0.7f, (float) size, 1f, 4, false);
        if (!layers()) return;
        // 흰 칼심: 조금 작게 한 겹 더 → 칼날 한가운데가 하얗게 달아오름
        spawn(at, SLASH, white(c, 0.75), q, (float) size * 0.55f, (float) size * 0.85f, 1f, 3, false);
        // 잔상: 한 틱 늦게, 살짝 뒤로 기울어진 넓은 여운
        Location back = at.clone().subtract(dir.clone().normalize().multiply(size * 0.12));
        later(1, () -> spawn(back, TRAIL, dark(c, 0.15), orient(dir, rollDeg - 10), (float) size * 0.95f, (float) size * 1.2f, 1f, 5, false));
        // 칼바람 입자: 궤적을 따라 흩날림
        Vector side = dir.clone().setY(0).lengthSquared() < 1e-6 ? new Vector(1, 0, 0) : new Vector(-dir.getZ(), 0, dir.getX()).normalize();
        double roll = Math.toRadians(rollDeg);
        Vector across = side.clone().multiply(Math.cos(roll)).add(new Vector(0, Math.sin(roll), 0));
        Vector fwd = dir.clone().normalize();
        for (int i = -3; i <= 3; i++) {
            double t = i / 3.0;
            Location pnt = at.clone().add(across.clone().multiply(t * size * 0.5)).add(fwd.clone().multiply((1 - t * t) * size * 0.25));
            dust(pnt, c, 0.6f, 1, 0.05);
            if (i % 3 == 0) at.getWorld().spawnParticle(org.bukkit.Particle.CRIT, pnt, 2, 0.05, 0.05, 0.05, 0.15);
        }
        // 칼끝 반짝임
        Location tip = at.clone().add(across.clone().multiply(size * 0.5)).add(fwd.clone().multiply(size * 0.05));
        spawn(tip, SPARK, white(c, 0.5), new Quaternionf().rotateX((float) Math.toRadians(90)), (float) size * 0.25f, (float) size * 0.05f, 1f, 3, true);
    }

    /** 바닥 충격파 고리: 반지름 r 까지 퍼지며 사라짐 */
    public static void ring(Location c, double r, Color col) {
        if (!enabled()) return;
        spawn(c.clone().add(0, 0.15, 0), RING, col, new Quaternionf(), (float) (r * 0.5), (float) (r * 2.4), 1f, 5, false);
        if (!layers()) return;
        // 바닥 파동 원판 + 한 틱 늦게 따라오는 흰 안쪽 고리
        spawn(c.clone().add(0, 0.1, 0), WAVE, dark(col, 0.1), new Quaternionf(), (float) (r * 0.4), (float) (r * 2.1), 1f, 4, false);
        later(1, () -> spawn(c.clone().add(0, 0.2, 0), RING, white(col, 0.7), new Quaternionf(), (float) (r * 0.4), (float) (r * 1.9), 1f, 4, false));
        // 가장자리 먼지 · 흙먼지
        int n = (int) Math.max(8, Math.min(28, r * 5));
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            Location e = c.clone().add(Math.cos(a) * r, 0.25, Math.sin(a) * r);
            dust(e, col, 0.9f, 1, 0.08);
            if (i % 3 == 0) c.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, e, 1, 0.1, 0.02, 0.1, 0.02);
        }
    }

    /** 광선: from → to 로 뻗는 빛 (width 블록) */
    public static void beam(Location from, Location to, double width, Color c) {
        if (!enabled() || !from.getWorld().equals(to.getWorld())) return;
        Vector d = to.toVector().subtract(from.toVector());
        double len = d.length();
        if (len < 0.1) return;
        Location mid = from.clone().add(d.clone().multiply(0.5));
        // 광선 텍스처는 모델 X 축 방향 → X 가 진행 방향을 향하도록 회전 (길이 = X 배율, 폭 = Z 배율)
        Quaternionf q = orient(d, 0).rotateY((float) Math.toRadians(-90));
        float w = (float) width;
        spawn(mid, BEAM, c, q, new Vector3f((float) len, 1, w * 0.6f), new Vector3f((float) len, 1, w), 4, false);
        if (!layers()) return;
        // 흰 심 광선 (가늘게) + 90° 돌린 판으로 옆에서 봐도 두께감
        spawn(mid, BEAM, white(c, 0.8), q, new Vector3f((float) len, 1, w * 0.2f), new Vector3f((float) len, 1, w * 0.35f), 3, false);
        spawn(mid, BEAM, c, new Quaternionf(q).rotateX((float) Math.toRadians(90)), new Vector3f((float) len, 1, w * 0.5f), new Vector3f((float) len, 1, w * 0.8f), 4, false);
        // 광선을 감싸는 나선 입자 + 끝점 섬광
        Vector u = d.clone().normalize();
        Vector a = Math.abs(u.getY()) > 0.9 ? new Vector(1, 0, 0) : new Vector(0, 1, 0);
        Vector p1 = u.getCrossProduct(a).normalize(), p2 = u.getCrossProduct(p1).normalize();
        int steps = (int) Math.min(40, len * 3);
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) Math.max(1, steps), ang = t * Math.PI * 2 * Math.max(1, len / 3);
            Location pnt = from.clone().add(d.clone().multiply(t)).add(p1.clone().multiply(Math.cos(ang) * w * 0.45)).add(p2.clone().multiply(Math.sin(ang) * w * 0.45));
            dust(pnt, c, 0.5f, 1, 0.02);
        }
        spawn(to, SPARK, white(c, 0.6), new Quaternionf().rotateX((float) Math.toRadians(90)), w * 0.9f, w * 0.2f, 1f, 3, true);
    }

    /** 타격 지점 별 폭발 (항상 카메라를 향함) */
    public static void burst(Location at, double size, Color c) {
        if (!enabled()) return;
        Quaternionf q = new Quaternionf().rotateX((float) Math.toRadians(90));
        spawn(at, BURST, c, q, (float) size * 0.5f, (float) size, 1f, 4, true);
        if (!layers()) return;
        // 45° 돌린 흰 섬광 + 중심 반짝임 + 작은 충격 원판
        spawn(at, BURST, white(c, 0.7), new Quaternionf(q).rotateY((float) Math.toRadians(45)), (float) size * 0.3f, (float) size * 0.7f, 1f, 3, true);
        spawn(at, SPARK, Color.WHITE, q, (float) size * 0.9f, (float) size * 0.2f, 1f, 3, true);
        spawn(at, WAVE, c, q, (float) size * 0.2f, (float) size * 1.3f, 1f, 3, true);
        dust(at, c, 0.7f, (int) Math.max(4, Math.min(14, size * 4)), size * 0.25);
        at.getWorld().spawnParticle(org.bukkit.Particle.END_ROD, at, (int) Math.max(2, Math.min(8, size * 2)), 0.05, 0.05, 0.05, 0.12 * size);
    }
}
