package kr.rpgcraft.boss;

import kr.rpgcraft.Keys;
import kr.rpgcraft.RpgCraft;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;

/**
 * 보스 3D 모델.
 * 보스 몹은 투명하게 만들고, 리소스팩의 보스 모델(PAPER CustomModelData 9000+)을 ItemDisplay 로 띄워 따라다니게 한다.
 * 숨쉬기(위아래), 피격 흔들림, 공격 시 커지는 연출. 히트박스는 원래 몹 그대로.
 */
public class BossModelManager implements Listener {
    public static final List<String> ORDER = List.of("witch", "elf_queen", "dwarf_king", "harpy_queen", "sea_gatekeeper",
            "bungbung", "desert_nightmare", "siphonia", "kain", "frost_queen", "volcano_giant", "void_apostle", "thunder_god", "primordial_dragon", "vengeful_spirit", "balrog",
            "megalodon", "kraken", "mount_wolf", "mount_lizard", "mount_warhorse", "mount_icebear", "mount_lion", "mount_panther", "mount_griffin", "mount_dragon");
    private static final Map<String, Float> SCALE = new HashMap<>();

    static {
        String[] ids = {"witch", "elf_queen", "dwarf_king", "harpy_queen", "sea_gatekeeper", "bungbung", "desert_nightmare", "siphonia", "kain",
                "frost_queen", "volcano_giant", "void_apostle", "thunder_god", "primordial_dragon", "vengeful_spirit", "balrog", "megalodon", "kraken"};
        // 월드보스는 압도적으로 크게, 나머지 보스도 전보다 크게
        float[] sc = {2.0f, 2.0f, 2.2f, 2.0f, 2.3f, 2.2f, 3.6f, 3.0f, 3.8f, 2.4f, 3.2f, 2.8f, 2.8f, 4.0f, 3.4f, 3.8f, 7.0f, 9.0f};   // 메갈로돈 · 크라켄은 압도적인 크기
        for (int i = 0; i < ids.length; i++) SCALE.put(ids[i], sc[i]);
    }

    private final RpgCraft plugin;
    private final Map<UUID, UUID> displays = new HashMap<>();
    private final Map<UUID, Float> scales = new HashMap<>();
    private int tick;

    public BossModelManager(RpgCraft plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::follow, 1L, 1L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::scan, 20L, 20L);
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("boss-models.enabled", true);
    }

    /** 1초마다: 모델이 없는 보스에게 모델을 붙인다 (소환 직후, 서버 재시작 후 포함) */
    private void scan() {
        if (!enabled()) return;
        for (World w : Bukkit.getWorlds()) {
            for (LivingEntity le : w.getLivingEntities()) {
                String id = le.getPersistentDataContainer().get(Keys.BOSS, PersistentDataType.STRING);
                if (id == null || displays.containsKey(le.getUniqueId()) || !ORDER.contains(id)) continue;
                attach(le, id);
            }
        }
    }

    private Transformation tf(float scale, float bob, float yawRad) {
        // 게임이 ItemDisplay 의 아이템을 Y축 180° 돌려 그리므로 π 를 더해 정면을 맞춤
        return new Transformation(new Vector3f(0, 0.5f * scale + bob, 0), new AxisAngle4f(yawRad + (float) Math.PI, 0, 1, 0), new Vector3f(scale), new AxisAngle4f());
    }

    public void attach(LivingEntity boss, String id) {
        float scale = (float) plugin.getConfig().getDouble("boss-models.scale." + id, SCALE.getOrDefault(id, 1.8f));
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        m.setCustomModelData(9000 + ORDER.indexOf(id));
        it.setItemMeta(m);
        ItemDisplay d = boss.getWorld().spawn(boss.getLocation(), ItemDisplay.class, x -> {
            x.setItemStack(it);
            x.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            x.setPersistent(false);
            x.setShadowRadius(0.8f * scale);
            x.setShadowStrength(0.6f);
            x.setTransformation(tf(scale, 0, yawOffset()));
            x.getPersistentDataContainer().set(Keys.INDICATOR, PersistentDataType.BYTE, (byte) 1);
        });
        boss.setInvisible(true);
        if (boss.getEquipment() != null) boss.getEquipment().clear();
        displays.put(boss.getUniqueId(), d.getUniqueId());
        scales.put(boss.getUniqueId(), scale);
    }

    private float yawOffset() {
        return (float) Math.toRadians(plugin.getConfig().getDouble("boss-models.yaw-offset", 0));
    }

    private void follow() {
        if (displays.isEmpty()) return;
        tick++;
        Iterator<Map.Entry<UUID, UUID>> it = displays.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, UUID> en = it.next();
            Entity b = Bukkit.getEntity(en.getKey());
            Entity d = Bukkit.getEntity(en.getValue());
            if (!(b instanceof LivingEntity boss) || !boss.isValid() || boss.isDead() || d == null || !d.isValid()) {
                if (d != null) d.remove();
                scales.remove(en.getKey());
                it.remove();
                continue;
            }
            Location l = boss.getLocation();
            float yaw = boss instanceof Mob ? ((Mob) boss).getLocation().getYaw() : l.getYaw();
            d.teleport(new Location(l.getWorld(), l.getX(), l.getY(), l.getZ(), yaw, 0));
            if (tick % 20 == 0 && d instanceof ItemDisplay id) { // 숨쉬기
                float s = scales.getOrDefault(en.getKey(), 1.8f);
                id.setInterpolationDelay(0);
                id.setInterpolationDuration(20);
                id.setTransformation(tf(s, (tick / 20) % 2 == 0 ? 0.08f * s : 0f, yawOffset()));
            }
        }
    }

    /** 피격 시 살짝 흔들림 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent e) {
        UUID did = displays.get(e.getEntity().getUniqueId());
        if (did == null || !(Bukkit.getEntity(did) instanceof ItemDisplay d)) return;
        float s = scales.getOrDefault(e.getEntity().getUniqueId(), 1.8f);
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(2);
        d.setTransformation(new Transformation(new Vector3f(0, 0.5f * s, 0), new AxisAngle4f(yawOffset() + 0.15f, 0, 1, 0), new Vector3f(s * 0.95f), new AxisAngle4f()));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!d.isValid()) return;
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(4);
            d.setTransformation(tf(s, 0, yawOffset()));
        }, 3L);
    }

    /** 보스가 스킬을 쓸 때 (BossManager 에서 호출): 크게 부풀었다 돌아옴 */
    public void attackPose(LivingEntity boss) {
        UUID did = displays.get(boss.getUniqueId());
        if (did == null || !(Bukkit.getEntity(did) instanceof ItemDisplay d)) return;
        float s = scales.getOrDefault(boss.getUniqueId(), 1.8f);
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(4);
        d.setTransformation(tf(s * 1.12f, 0.2f * s, yawOffset()));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!d.isValid()) return;
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(8);
            d.setTransformation(tf(s, 0, yawOffset()));
        }, 6L);
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        UUID did = displays.remove(e.getEntity().getUniqueId());
        scales.remove(e.getEntity().getUniqueId());
        if (did != null) {
            Entity d = Bukkit.getEntity(did);
            if (d != null) d.remove();
        }
    }

    public void shutdown() {
        for (UUID did : displays.values()) {
            Entity d = Bukkit.getEntity(did);
            if (d != null) d.remove();
        }
        displays.clear();
    }
}
