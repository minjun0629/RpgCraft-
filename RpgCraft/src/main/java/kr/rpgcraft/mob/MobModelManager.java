package kr.rpgcraft.mob;

import kr.rpgcraft.Keys;
import kr.rpgcraft.RpgCraft;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustByBlockEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 일반 몬스터 3D 모델 (v5.9.0).
 * mobs.yml 커스텀 몬스터를 투명하게 하고, 리소스팩의 복셀 모델(PAPER CustomModelData 9100+)을 ItemDisplay 로 태워 함께 움직인다.
 * 모델 번호 · 키는 tools/mob_models.py 가 만든 mob-models.yml (플러그인 안) 에서 읽는다.
 * 걸음(위아래 · 좌우 흔들림 · 앞으로 기울기) · 숨쉬기 · 공격(내지르기) · 피격(젖혀짐)을 보간해 부드럽게. 판정은 원래 몹 그대로.
 * 가까이에 플레이어가 없으면 움직임 계산을 건너뛰어 가볍게.
 */
public class MobModelManager implements Listener {
    private record Def(int cmd, float height, float size) { }

    private static final class Pose {
        float yaw = Float.NaN, speed;
        double phase, lastX, lastZ;
        int attack = -1, hurt = -1;
    }

    private final RpgCraft plugin;
    private final NamespacedKey mobKey, modelKey;
    private final Map<String, Def> defs = new HashMap<>();
    private final Map<UUID, UUID> displays = new HashMap<>();
    private final Map<UUID, Float> scales = new HashMap<>();
    private final Map<UUID, Pose> poses = new HashMap<>();
    private int tick;

    public MobModelManager(RpgCraft plugin) {
        this.plugin = plugin;
        this.mobKey = new NamespacedKey(plugin, "custom_mob");
        this.modelKey = new NamespacedKey(plugin, "mob_model");
        load();
        Bukkit.getScheduler().runTaskTimer(plugin, this::follow, 1L, 1L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::scan, 40L, 100L);
    }

    private void load() {
        defs.clear();
        try (var in = plugin.getResource("mob-models.yml")) {
            if (in == null) return;
            YamlConfiguration y = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
            ConfigurationSection s = y.getConfigurationSection("models");
            if (s == null) return;
            for (String id : s.getKeys(false)) {
                ConfigurationSection m = s.getConfigurationSection(id);
                if (m != null) defs.put(id, new Def(m.getInt("cmd"), (float) m.getDouble("height", 16), (float) m.getDouble("size", 1)));
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("mob-models.yml 을 읽지 못했습니다: " + ex.getMessage());
        }
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("mob-models.enabled", true);
    }

    public boolean has(Entity e) {
        return displays.containsKey(e.getUniqueId());
    }

    /** 5초마다: 모델이 없는 커스텀 몬스터에 붙이고(재시작 · 청크 로드 후), 주인을 잃은 모델은 지움 */
    private void scan() {
        if (!enabled()) return;
        for (World w : Bukkit.getWorlds()) {
            for (ItemDisplay d : w.getEntitiesByClass(ItemDisplay.class))
                if (d.getPersistentDataContainer().has(modelKey, PersistentDataType.BYTE) && !displays.containsValue(d.getUniqueId())) d.remove();
            for (LivingEntity le : w.getLivingEntities()) {
                if (displays.containsKey(le.getUniqueId()) || le.isDead()) continue;
                String id = le.getPersistentDataContainer().get(mobKey, PersistentDataType.STRING);
                if (id != null && !le.getPersistentDataContainer().has(Keys.BOSS, PersistentDataType.STRING)) attach(le, id);
            }
        }
    }

    /** 커스텀 몬스터가 생길 때 (CustomMobManager.setup) 바로 붙임 */
    public void attach(LivingEntity le, String id) {
        if (!enabled() || le.isDead() || displays.containsKey(le.getUniqueId())) return;
        Def def = defs.get(id);
        if (def == null) return;
        CustomMobManager.MobDef md = plugin.customMobs() == null ? null : plugin.customMobs().def(id);
        // 몹 키에 맞춰 그림. 1.20.1 은 몹 크기 속성이 없어 강한 몬스터(scale)는 모델만 크게 그린다 (슬라임 · 팬텀은 몸 크기가 이미 커짐)
        double mult = md == null || le instanceof Slime || le instanceof Phantom || hasScaleAttr(le) ? 1 : Math.max(1, md.scale);
        float scale = (float) (le.getHeight() * mult * def.size() * plugin.getConfig().getDouble("mob-models.size-mult", 1.0));
        float k = scale * 16f / def.height();
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        m.setCustomModelData(def.cmd());
        it.setItemMeta(m);
        Location at = le.getLocation().clone();
        at.setYaw(0);
        at.setPitch(0);
        ItemDisplay d = le.getWorld().spawn(at, ItemDisplay.class, x -> {
            x.setItemStack(it);
            x.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            x.setPersistent(false);
            x.setShadowRadius(0.45f * scale);
            x.setShadowStrength(0.5f);
            x.setViewRange((float) plugin.getConfig().getDouble("mob-models.view-range", 1.0));
            x.setTransformation(new Transformation(new Vector3f(0, 0.5f * k, 0), new Quaternionf().rotateY((float) Math.PI), new Vector3f(k), new Quaternionf()));
            x.getPersistentDataContainer().set(modelKey, PersistentDataType.BYTE, (byte) 1);
            x.getPersistentDataContainer().set(Keys.INDICATOR, PersistentDataType.BYTE, (byte) 1);
        });
        le.addPassenger(d);
        le.setInvisible(true);
        hideGear(le);
        displays.put(le.getUniqueId(), d.getUniqueId());
        scales.put(le.getUniqueId(), k);
    }

    private static boolean hasScaleAttr(LivingEntity le) {
        try {
            Object attr = org.bukkit.attribute.Attribute.class.getField("GENERIC_SCALE").get(null);
            return le.getAttribute((org.bukkit.attribute.Attribute) attr) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 투명해도 장비는 보이므로 치움. 활 · 쇠뇌 · 삼지창은 쏘는 데 필요해서 손에 남김 (모델에는 그리지 않음) */
    private static void hideGear(LivingEntity le) {
        EntityEquipment eq = le.getEquipment();
        if (eq == null) return;
        ItemStack hand = eq.getItemInMainHand();
        Material t = hand == null ? Material.AIR : hand.getType();
        boolean keep = t == Material.BOW || t == Material.CROSSBOW || t == Material.TRIDENT;
        eq.setHelmet(null);
        eq.setChestplate(null);
        eq.setLeggings(null);
        eq.setBoots(null);
        eq.setItemInOffHand(null);
        if (!keep) eq.setItemInMainHand(null);
    }

    private void follow() {
        if (displays.isEmpty()) return;
        tick++;
        Iterator<Map.Entry<UUID, UUID>> it = displays.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, UUID> en = it.next();
            Entity b = Bukkit.getEntity(en.getKey());
            Entity d = Bukkit.getEntity(en.getValue());
            if (!(b instanceof LivingEntity mob) || !mob.isValid() || mob.isDead() || d == null || !d.isValid()) {
                if (d != null) d.remove();
                if (b instanceof LivingEntity le && le.isValid() && !le.isDead()) le.setInvisible(false);
                scales.remove(en.getKey());
                poses.remove(en.getKey());
                it.remove();
                continue;
            }
            if (!mob.getPassengers().contains(d)) {   // 순간이동 등으로 내려졌으면 다시 태움
                Location l = mob.getLocation();
                d.teleport(new Location(l.getWorld(), l.getX(), l.getY(), l.getZ(), 0, 0));
                mob.addPassenger(d);
            }
            if ((tick + (en.getKey().hashCode() & 1)) % 2 == 0 && d instanceof ItemDisplay id && near(mob)) animate(mob, id, en.getKey());
        }
    }

    private boolean near(LivingEntity mob) {
        double r = plugin.getConfig().getDouble("mob-models.animate-range", 40);
        Location l = mob.getLocation();
        for (Player p : mob.getWorld().getPlayers()) if (p.getLocation().distanceSquared(l) < r * r) return true;
        return false;
    }

    private static float wrap(float deg) {
        deg %= 360;
        if (deg > 180) deg -= 360;
        if (deg < -180) deg += 360;
        return deg;
    }

    private void animate(LivingEntity mob, ItemDisplay d, UUID id) {
        float k = scales.getOrDefault(id, 1f);
        Pose p = poses.computeIfAbsent(id, x -> new Pose());
        Location l = mob.getLocation();
        float target = l.getYaw();
        if (Float.isNaN(p.yaw)) { p.yaw = target; p.lastX = l.getX(); p.lastZ = l.getZ(); }
        float diff = wrap(target - p.yaw), step = Math.max(-24f, Math.min(24f, diff * 0.45f));
        p.yaw = wrap(p.yaw + step);
        double moved = Math.hypot(l.getX() - p.lastX, l.getZ() - p.lastZ);
        p.lastX = l.getX();
        p.lastZ = l.getZ();
        float sp = (float) Math.min(1, moved / 0.3);
        p.speed = p.speed * 0.55f + sp * 0.45f;
        p.phase += 0.3 + p.speed * 1.1;
        double t = (tick + (id.hashCode() & 63)) * 0.05;
        float h = k / 16f;   // 모델 1칸(1/16) → 블록
        boolean flying = mob instanceof Phantom || mob instanceof Vex || mob instanceof Blaze;
        float bob = flying ? (float) (Math.sin(t * 2.2) * 0.9 * h) : (float) (Math.abs(Math.sin(p.phase)) * 0.9 * h * p.speed + Math.sin(t * 1.8) * 0.25 * h * (1 - p.speed));
        float roll = flying ? (float) (Math.sin(t * 1.3) * 6) : (float) (Math.sin(p.phase) * 4.5 * p.speed - step * 0.35);
        float lean = 6f * p.speed;
        float sx = 1, sy = (float) (1 + 0.02 * Math.sin(t * 1.8) * (1 - p.speed)), lunge = 0;
        if (p.attack >= 0) {
            float f = (float) Math.sin(Math.PI * p.attack / 4.0);
            lean += 16 * f;
            sx *= 1 + 0.08f * f;
            sy *= 1 + 0.08f * f;
            lunge = 2.4f * h * f;
            if (++p.attack > 4) p.attack = -1;
        }
        if (p.hurt >= 0) {
            float f = 1 - p.hurt / 3f;
            lean -= 9 * f;
            sx *= 1 + 0.05f * f;
            sy *= 1 - 0.05f * f;
            if (++p.hurt > 3) p.hurt = -1;
        }
        double yr = Math.toRadians(p.yaw);
        float fx = (float) (-Math.sin(yr)) * lunge, fz = (float) Math.cos(yr) * lunge;
        float offY = (float) (d.getLocation().getY() - l.getY());   // 탄 높이만큼 내려서 발을 땅에
        Quaternionf q = new Quaternionf().rotateY((float) (-yr + Math.PI)).rotateX((float) Math.toRadians(-lean)).rotateZ((float) Math.toRadians(roll));
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(2);
        d.setTransformation(new Transformation(new Vector3f(fx, 0.5f * k + bob - offY, fz), q, new Vector3f(k * sx, k * sy, k * sx), new Quaternionf()));
    }

    /** 모델을 태운 몹은 그대로는 순간이동이 안 됨 → 내려서 옮기고 다시 태움 */
    public boolean teleport(LivingEntity mob, Location to) {
        UUID did = displays.get(mob.getUniqueId());
        Entity d = did == null ? null : Bukkit.getEntity(did);
        if (d != null) mob.removePassenger(d);
        boolean ok = mob.teleport(to);
        if (d != null && d.isValid()) {
            d.teleport(new Location(to.getWorld(), to.getX(), to.getY(), to.getZ(), 0, 0));
            Bukkit.getScheduler().runTaskLater(plugin, () -> { if (d.isValid() && mob.isValid()) mob.addPassenger(d); }, 1L);
        }
        Pose p = poses.get(mob.getUniqueId());
        if (p != null) { p.lastX = to.getX(); p.lastZ = to.getZ(); }
        return ok;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent e) {
        if (!displays.containsKey(e.getEntity().getUniqueId())) return;
        Pose p = poses.computeIfAbsent(e.getEntity().getUniqueId(), x -> new Pose());
        if (p.attack < 0) p.hurt = 0;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent e) {
        Entity src = e.getDamager();
        if (!displays.containsKey(src.getUniqueId())) return;
        poses.computeIfAbsent(src.getUniqueId(), x -> new Pose()).attack = 0;
    }

    /** 모델을 쓴 몹은 투구를 치웠으므로 햇빛에 타지 않게 (원래 투구를 쓴 몬스터가 많음) */
    @EventHandler(ignoreCancelled = true)
    public void onSun(EntityCombustEvent e) {
        if (e instanceof EntityCombustByEntityEvent || e instanceof EntityCombustByBlockEvent) return;
        if (displays.containsKey(e.getEntity().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        UUID did = displays.remove(e.getEntity().getUniqueId());
        scales.remove(e.getEntity().getUniqueId());
        poses.remove(e.getEntity().getUniqueId());
        if (did != null) {
            Entity d = Bukkit.getEntity(did);
            if (d != null) d.remove();
        }
    }

    public void shutdown() {
        for (Map.Entry<UUID, UUID> en : displays.entrySet()) {
            Entity d = Bukkit.getEntity(en.getValue());
            if (d != null) d.remove();
            if (Bukkit.getEntity(en.getKey()) instanceof LivingEntity le) le.setInvisible(false);   // 모델 없이 투명하게 남지 않게
        }
        displays.clear();
    }
}
