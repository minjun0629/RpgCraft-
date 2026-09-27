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
    public static final int SLASH = 9500, RING = 9501, BEAM = 9502, BURST = 9503;

    private Vfx() {}

    public static boolean enabled() {
        RpgCraft pl = RpgCraft.get();
        return pl != null && pl.getConfig().getBoolean("vfx.enabled", true);
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
        spawn(at, SLASH, c, orient(dir, rollDeg), (float) size * 0.7f, (float) size, 1f, 4, false);
    }

    /** 바닥 충격파 고리: 반지름 r 까지 퍼지며 사라짐 */
    public static void ring(Location c, double r, Color col) {
        if (!enabled()) return;
        spawn(c.clone().add(0, 0.15, 0), RING, col, new Quaternionf(), (float) (r * 0.5), (float) (r * 2.4), 1f, 5, false);
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
    }

    /** 타격 지점 별 폭발 (항상 카메라를 향함) */
    public static void burst(Location at, double size, Color c) {
        if (!enabled()) return;
        Quaternionf q = new Quaternionf().rotateX((float) Math.toRadians(90));
        spawn(at, BURST, c, q, (float) size * 0.5f, (float) size, 1f, 4, true);
    }
}
