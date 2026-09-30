package kr.rpgcraft.util;

import kr.rpgcraft.Keys;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 모델을 태운 몹의 이름표 (v5.9.3).
 * 게임은 투명하거나 무언가를 태운 몹의 이름표를 그리지 않아서, 3D 모델을 쓰는 몬스터 · 보스의 체력 표시(이름표)가 사라졌다.
 * → 몹의 이름표 내용(레벨 · 이름 · 체력 줄)을 그대로 옮겨 모델 머리 위에 글자 디스플레이로 띄운다.
 */
public final class NameTag {
    private NameTag() {}

    private static final Color SHOWN = Color.fromARGB(64, 0, 0, 0), HIDDEN = Color.fromARGB(0, 0, 0, 0);

    public static TextDisplay spawn(LivingEntity host) {
        Location at = host.getLocation().clone();
        at.setYaw(0);
        at.setPitch(0);
        TextDisplay t = host.getWorld().spawn(at, TextDisplay.class, x -> {
            x.setBillboard(Display.Billboard.CENTER);
            x.setPersistent(false);
            x.setText("");
            x.setShadowed(true);
            x.setBackgroundColor(HIDDEN);
            x.setBrightness(new Display.Brightness(15, 15));
            x.setViewRange(0.6f);   // v5.10.2 프레임
            x.getPersistentDataContainer().set(Keys.INDICATOR, PersistentDataType.BYTE, (byte) 1);
        });
        host.addPassenger(t);
        return t;
    }

    /** 몹 이름표 내용 · 보이기 여부를 옮기고, 발에서 height 블록 위에 띄움 */
    public static void update(LivingEntity host, TextDisplay t, float height) {
        update(host, t, height, 1f);
    }

    /** textScale: 글자 크기 (큰 보스는 멀리서도 보이게 크게) */
    public static void update(LivingEntity host, TextDisplay t, float height, float textScale) {
        if (!host.getPassengers().contains(t)) {
            Location l = host.getLocation();
            t.teleport(new Location(l.getWorld(), l.getX(), l.getY(), l.getZ(), 0, 0));
            host.addPassenger(t);
        }
        String name = host.getCustomName();
        boolean show = name != null && !name.isEmpty() && host.isCustomNameVisible();
        String want = show ? name : "";
        if (!want.equals(t.getText())) {
            t.setText(want);
            t.setBackgroundColor(show ? SHOWN : HIDDEN);
        }
        float offY = (float) (t.getLocation().getY() - host.getLocation().getY());
        float y = height + 0.35f - offY;
        Vector3f cur = t.getTransformation().getTranslation();
        if (Math.abs(cur.y - y) > 0.05f || Math.abs(t.getTransformation().getScale().x - textScale) > 0.01f)
            t.setTransformation(new Transformation(new Vector3f(0, y, 0), new Quaternionf(), new Vector3f(textScale), new Quaternionf()));
    }
}
