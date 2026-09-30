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
import org.bukkit.event.entity.EntityShootBowEvent;
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
 * 일반 몬스터 3D 모델 (v5.9.0, v5.9.1 관절 애니메이션).
 * mobs.yml 커스텀 몬스터를 투명하게 하고, 리소스팩의 복셀 모델을 부위(몸통 · 머리 · 팔 · 다리 · 날개)마다 ItemDisplay 로 태운다.
 * 부위 모델은 관절 중심이 모델 가운데에 있어서, 몸 전체 회전 위에 관절 회전을 곱해 그린다 (tools/mob_models.py → mob-models.yml).
 *  - 걷기: 다리가 번갈아 앞뒤로, 팔은 반대로 흔들림 (네발짐승은 대각선 걸음), 몸은 걸음에 맞춰 위아래 · 좌우
 *  - 서 있기: 숨쉬기, 팔이 살짝 흔들리고 고개를 두리번거림
 *  - 머리는 노리는 플레이어 쪽으로 돌아감 · 날개는 퍼덕임
 *  - 공격: 무기 든 팔을 들어 올렸다 내려찍음 / 피격: 뒤로 젖혀짐 / 등장: 땅에서 솟아남 / 죽음: 옆으로 쓰러지며 가라앉음
 * 가까운 몬스터는 2틱, 먼 몬스터는 4틱마다 (보간으로 부드럽게), 멀리 있으면 계산하지 않음.
 */
public class MobModelManager implements Listener {
    private record Part(String name, int cmd, float px, float py, float pz, int undead) { }   // undead: v5.10.11 언데드 색 변형 번호 (0 = 없음)

    private record Def(String rig, float height, float size, List<Part> parts) { }

    /** 몬스터 하나의 모델 상태 */
    private static final class Rig {
        final Def def;
        final List<UUID> ids = new ArrayList<>();
        UUID nameId;   // v5.9.3 체력 이름표 (게임이 모델을 태운 몹의 이름표를 그리지 않음)
        float k, h, yaw = Float.NaN, speed, headYaw;
        double phase, lastX, lastZ;
        int attack = -1, hurt = -1, age, nextAt;
        final int seed;

        Rig(Def def, int seed) {
            this.def = def;
            this.seed = seed;
        }
    }

    private final RpgCraft plugin;
    private final NamespacedKey mobKey, modelKey;
    private final Map<String, Def> defs = new HashMap<>();
    private final Map<UUID, Rig> rigs = new HashMap<>();
    private final Set<UUID> owned = new HashSet<>();
    private int tick;

    public MobModelManager(RpgCraft plugin) {
        this.plugin = plugin;
        this.mobKey = new NamespacedKey(plugin, "custom_mob");
        this.modelKey = new NamespacedKey(plugin, "mob_model");
        load();
        Bukkit.getScheduler().runTaskTimer(plugin, this::follow, 1L, 1L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::scan, 40L, 100L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::wander, 30L, 20L);
    }

    // ------------------------------------------------------------------ v5.10.0 배회
    // 게임은 무언가를 태운 몹이 혼자 돌아다니지 않게 한다 (말에 사람이 탔을 때처럼) → 모델을 태운 몬스터가 제자리에만 서 있었음.
    // 노리는 대상이 없을 때 가끔 근처 아무 곳으로 걸어가게 한다 (Paper 길찾기, 없으면 직접 밀기).
    private static java.lang.reflect.Method getPf, moveLoc;
    private static boolean pfTried;

    private static void reflectPf(Mob m) {
        if (pfTried) return;
        pfTried = true;
        try {
            getPf = m.getClass().getMethod("getPathfinder");
            moveLoc = getPf.getReturnType().getMethod("moveTo", Location.class, double.class);
        } catch (Throwable ignored) {
            getPf = null;
        }
    }

    private void wander() {
        if (rigs.isEmpty() || !plugin.getConfig().getBoolean("mob-models.wander", true)) return;
        java.util.concurrent.ThreadLocalRandom rnd = java.util.concurrent.ThreadLocalRandom.current();
        for (UUID id : new ArrayList<>(rigs.keySet())) {
            if (!(Bukkit.getEntity(id) instanceof Mob m) || !m.isValid() || m.isDead() || !m.isOnGround()) continue;
            if (kr.rpgcraft.world.NecromancyManager.isMinion(m)) continue;   // 군단원은 주인을 따라다님
            if (m.getTarget() != null && m.getTarget().isValid()) continue;   // 싸우는 중이면 게임 AI 가 쫓아감
            if (m instanceof Slime || rnd.nextDouble() > 0.22) continue;
            if (nearest(m, 48) > 48) continue;
            Location l = m.getLocation();
            double a = rnd.nextDouble(Math.PI * 2), dist = rnd.nextDouble(3, 8);
            Location dest = standable(l.clone().add(Math.cos(a) * dist, 0, Math.sin(a) * dist));
            if (dest == null) continue;
            reflectPf(m);
            if (getPf != null) {
                try {
                    moveLoc.invoke(getPf.invoke(m), dest, 1.0);
                    continue;
                } catch (Throwable ignored) {
                }
            }
            nudge(m, dest);
        }
    }

    /** 그 근처(위아래 2칸)에서 발 디딜 수 있는 자리 */
    private static Location standable(Location at) {
        org.bukkit.block.Block b = at.getBlock();
        for (int dy = 2; dy >= -2; dy--) {
            org.bukkit.block.Block feet = b.getRelative(0, dy, 0);
            if (feet.isPassable() && feet.getRelative(0, 1, 0).isPassable() && feet.getRelative(0, -1, 0).getType().isSolid() && !feet.isLiquid())
                return feet.getLocation().add(0.5, 0, 0.5);
        }
        return null;
    }

    /** 길찾기가 없는 서버: 몇 틱 동안 그쪽으로 살살 밀어 걷게 함 (막히면 뜀) */
    private void nudge(Mob m, Location dest) {
        int[] n = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (++n[0] > 30 || !m.isValid() || m.isDead() || (m.getTarget() != null && m.getTarget().isValid())) { task.cancel(); return; }
            Location l = m.getLocation();
            org.bukkit.util.Vector d = dest.toVector().subtract(l.toVector()).setY(0);
            if (d.lengthSquared() < 0.5) { task.cancel(); return; }
            d.normalize();
            m.setRotation((float) Math.toDegrees(Math.atan2(-d.getX(), d.getZ())), 0);
            double vy = m.getVelocity().getY();
            boolean blocked = !l.clone().add(d).getBlock().isPassable();
            if (m.isOnGround() && blocked) vy = 0.42;
            org.bukkit.util.Vector v = d.multiply(0.17);
            m.setVelocity(new org.bukkit.util.Vector(v.getX(), vy, v.getZ()));
        }, 1L, 1L);
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
                ConfigurationSection ps = m == null ? null : m.getConfigurationSection("parts");
                if (ps == null) continue;
                List<Part> parts = new ArrayList<>();
                for (String pn : ps.getKeys(false)) {
                    List<?> v = ps.getList(pn);
                    if (v == null || v.size() < 4) continue;
                    parts.add(new Part(pn, ((Number) v.get(0)).intValue(), ((Number) v.get(1)).floatValue(), ((Number) v.get(2)).floatValue(), ((Number) v.get(3)).floatValue(),
                            v.size() > 4 ? ((Number) v.get(4)).intValue() : 0));
                }
                defs.put(id, new Def(m.getString("rig", "solid"), (float) m.getDouble("height", 16), (float) m.getDouble("size", 1), parts));
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("mob-models.yml 을 읽지 못했습니다: " + ex.getMessage());
        }
    }

    private boolean enabled() {
        if (!plugin.getConfig().getBoolean("mob-models.enabled", true)) return false;
        // v5.9.2: 플레이어가 받는 팩이 이 플러그인의 팩과 다르면(예전 팩) 부위가 다른 몬스터로 보여 여러 마리가 겹쳐 보임 → 쓰지 않음
        return !plugin.getConfig().getBoolean("mob-models.require-matching-pack", true) || plugin.pack() == null || plugin.pack().matchesBundled();
    }

    public boolean has(Entity e) {
        return rigs.containsKey(e.getUniqueId());
    }

    /** 5초마다: 모델이 없는 커스텀 몬스터에 붙이고(재시작 · 청크 로드 후), 주인을 잃은 모델은 지움 */
    private void scan() {
        if (!enabled()) return;
        for (World w : Bukkit.getWorlds()) {
            for (ItemDisplay d : w.getEntitiesByClass(ItemDisplay.class))
                if (d.getPersistentDataContainer().has(modelKey, PersistentDataType.BYTE) && !owned.contains(d.getUniqueId()) && !dying.contains(d.getUniqueId())) d.remove();
            for (LivingEntity le : w.getLivingEntities()) {
                if (rigs.containsKey(le.getUniqueId()) || le.isDead()) continue;
                String id = le.getPersistentDataContainer().get(mobKey, PersistentDataType.STRING);
                if (id != null && !le.getPersistentDataContainer().has(Keys.BOSS, PersistentDataType.STRING)) attach(le, id);
            }
        }
    }

    /** 커스텀 몬스터가 생길 때 (CustomMobManager.setup) 바로 붙임 */
    public void attach(LivingEntity le, String id) {
        attach(le, id, -1);
    }

    /** height > 0 이면 몹 키 대신 그 높이로 모델을 그림 (v5.10.9 네크로맨서 군단원: 늑대 몸에 원래 몬스터 크기의 모델) */
    public void attach(LivingEntity le, String id, double height) {
        attach(le, id, height, false);
    }

    /** undead: 네크로맨서 군단원 — 창백한 회녹색 피부 · 영혼빛 눈의 언데드 색으로 (v5.10.11) */
    public void attach(LivingEntity le, String id, double height, boolean undead) {
        if (!enabled() || le.isDead() || rigs.containsKey(le.getUniqueId())) return;
        for (Entity old : le.getPassengers())   // 혹시 남아 있던 예전 모델 조각은 치우고 새로 붙임 (겹침 방지)
            if (old instanceof ItemDisplay od && od.getPersistentDataContainer().has(modelKey, PersistentDataType.BYTE)) od.remove();
        Def def = defs.get(id);
        if (def == null || def.parts().isEmpty()) return;
        CustomMobManager.MobDef md = plugin.customMobs() == null ? null : plugin.customMobs().def(id);
        // 몹 키에 맞춰 그림. 1.20.1 은 몹 크기 속성이 없어 강한 몬스터(scale)는 모델만 크게 그린다 (슬라임 · 팬텀은 몸 크기가 이미 커짐)
        double mult = md == null || le instanceof Slime || le instanceof Phantom || hasScaleAttr(le) ? 1 : Math.max(1, md.scale);
        float scale = (float) ((height > 0 ? height : le.getHeight() * mult) * def.size() * plugin.getConfig().getDouble("mob-models.size-mult", 1.0));
        Rig r = new Rig(def, le.getUniqueId().hashCode());
        r.k = scale * 16f / def.height();
        r.h = scale;
        Location at = le.getLocation().clone();
        at.setYaw(0);
        at.setPitch(0);
        float view = (float) plugin.getConfig().getDouble("mob-models.view-range", 0.6);
        for (Part p : def.parts()) {
            ItemStack it = new ItemStack(Material.PAPER);
            ItemMeta m = it.getItemMeta();
            m.setCustomModelData(undead && p.undead() > 0 ? p.undead() : p.cmd());
            it.setItemMeta(m);
            boolean body = p.name().equals("body");
            ItemDisplay d = le.getWorld().spawn(at, ItemDisplay.class, x -> {
                x.setItemStack(it);
                x.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                x.setPersistent(false);
                x.setShadowRadius(0);   // v5.10.2 프레임: 몬스터 모델 그림자 없음
                x.setViewRange(view);
                x.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.01f), new Quaternionf()));   // 등장 전에는 작게
                x.getPersistentDataContainer().set(modelKey, PersistentDataType.BYTE, (byte) 1);
                x.getPersistentDataContainer().set(Keys.INDICATOR, PersistentDataType.BYTE, (byte) 1);
            });
            le.addPassenger(d);
            r.ids.add(d.getUniqueId());
            owned.add(d.getUniqueId());
        }
        TextDisplay tag = kr.rpgcraft.util.NameTag.spawn(le);
        tag.getPersistentDataContainer().set(modelKey, PersistentDataType.BYTE, (byte) 1);
        r.nameId = tag.getUniqueId();
        owned.add(r.nameId);
        le.setInvisible(true);
        hideGear(le);
        rigs.put(le.getUniqueId(), r);
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

    private void removeRig(UUID mob, Rig r) {
        removeName(r);
        for (UUID id : r.ids) {
            owned.remove(id);
            Entity d = Bukkit.getEntity(id);
            if (d != null) d.remove();
        }
        rigs.remove(mob);
    }

    private void removeName(Rig r) {
        if (r.nameId == null) return;
        owned.remove(r.nameId);
        Entity t = Bukkit.getEntity(r.nameId);
        if (t != null) t.remove();
        r.nameId = null;
    }

    private void follow() {
        tick++;
        if (rigs.isEmpty()) return;
        double far = plugin.getConfig().getDouble("mob-models.animate-range", 40);
        for (Map.Entry<UUID, Rig> en : new ArrayList<>(rigs.entrySet())) {
            Rig r = en.getValue();
            Entity b = Bukkit.getEntity(en.getKey());
            if (!(b instanceof LivingEntity mob) || !mob.isValid() || mob.isDead()) {
                if (b instanceof LivingEntity le && le.isValid() && !le.isDead()) le.setInvisible(false);
                removeRig(en.getKey(), r);
                continue;
            }
            List<ItemDisplay> ds = new ArrayList<>(r.ids.size());
            boolean broken = false;
            for (UUID id : r.ids) {
                if (Bukkit.getEntity(id) instanceof ItemDisplay d && d.isValid()) ds.add(d);
                else broken = true;
            }
            if (broken) {   // 부위 하나라도 사라졌으면 통째로 다시 붙임 (다음 검사 때)
                removeRig(en.getKey(), r);
                continue;
            }
            Location l = mob.getLocation();
            for (ItemDisplay d : ds) {
                if (!mob.getPassengers().contains(d)) {   // 순간이동 등으로 내려졌으면 다시 태움
                    d.teleport(new Location(l.getWorld(), l.getX(), l.getY(), l.getZ(), 0, 0));
                    mob.addPassenger(d);
                }
            }
            if ((tick + (r.seed & 3)) % 4 == 0) {   // 체력 이름표: 내용 · 높이 맞추기 (없어졌으면 다시 띄움)
                Entity te = r.nameId == null ? null : Bukkit.getEntity(r.nameId);
                if (!(te instanceof TextDisplay td) || !td.isValid()) {
                    if (r.nameId != null) owned.remove(r.nameId);
                    TextDisplay nt = kr.rpgcraft.util.NameTag.spawn(mob);
                    nt.getPersistentDataContainer().set(modelKey, PersistentDataType.BYTE, (byte) 1);
                    r.nameId = nt.getUniqueId();
                    owned.add(r.nameId);
                } else kr.rpgcraft.util.NameTag.update(mob, td, r.h);
            }
            if (tick < r.nextAt) continue;
            double dist = nearest(mob, far);
            if (dist > far) { r.nextAt = tick + 10; continue; }
            int step = dist < 16 ? 2 : 4;   // v5.10.2: 16블록 밖은 절반 빈도
            r.nextAt = tick + step;
            animate(mob, r, ds, step);
        }
    }

    private static double nearest(LivingEntity mob, double cap) {
        double best = cap * cap + 1;
        Location l = mob.getLocation();
        for (Player p : mob.getWorld().getPlayers()) best = Math.min(best, p.getLocation().distanceSquared(l));
        return Math.sqrt(best);
    }

    private static float wrap(float deg) {
        deg %= 360;
        if (deg > 180) deg -= 360;
        if (deg < -180) deg += 360;
        return deg;
    }

    private static float rad(double deg) {
        return (float) Math.toRadians(deg);
    }

    private static float ease(float x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }

    private void animate(LivingEntity mob, Rig r, List<ItemDisplay> ds, int step) {
        Location l = mob.getLocation();
        float target = l.getYaw();
        if (Float.isNaN(r.yaw)) { r.yaw = target; r.lastX = l.getX(); r.lastZ = l.getZ(); }
        float diff = wrap(target - r.yaw), turn = Math.max(-12f * step, Math.min(12f * step, diff * 0.5f));
        r.yaw = wrap(r.yaw + turn);
        double moved = Math.hypot(l.getX() - r.lastX, l.getZ() - r.lastZ) / step;
        r.lastX = l.getX();
        r.lastZ = l.getZ();
        float sp = (float) Math.min(1, moved / 0.15);
        r.speed = r.speed * 0.6f + sp * 0.4f;
        r.phase += step * (0.08 + r.speed * 0.32);
        r.age += step;
        double t = (tick + (r.seed & 255)) * 0.05;
        float k = r.k, u = k / 16f;   // u: 모델 1칸 → 블록
        boolean flying = mob instanceof Phantom || mob instanceof Vex || mob instanceof Blaze;
        boolean quad = r.def.rig().equals("quad");

        // ---- 몸 전체
        float bob = flying ? (float) (Math.sin(t * 2.2) * 0.9 * u)
                : (float) (Math.abs(Math.sin(r.phase * 2)) * 0.55 * u * r.speed + Math.sin(t * 1.8) * 0.18 * u * (1 - r.speed));
        float roll = flying ? (float) (Math.sin(t * 1.3) * 5) : (float) (Math.sin(r.phase) * (quad ? 2.0 : 3.5) * r.speed - turn / step * 0.4);
        float lean = (quad ? 3f : 7f) * r.speed;
        float sx = 1, sy = (float) (1 + 0.018 * Math.sin(t * 1.8) * (1 - r.speed)), lunge = 0;
        float atkArm = 0, atkArmL = 0;
        if (r.attack >= 0) {   // 공격: 들어 올렸다(0~45%) 내려찍고(45~70%) 돌아옴
            float f = r.attack / 12f;
            if (f < 0.45f) atkArm = -125 * ease(f / 0.45f);
            else if (f < 0.7f) atkArm = -125 + 110 * ease((f - 0.45f) / 0.25f);   // 앞 아래로 내려찍음
            else atkArm = -15 * (1 - ease((f - 0.7f) / 0.3f));
            atkArmL = atkArm * 0.35f;
            float push = f < 0.45f ? -0.4f * ease(f / 0.45f) : f < 0.7f ? -0.4f + 1.4f * ease((f - 0.45f) / 0.25f) : 1 - ease((f - 0.7f) / 0.3f);
            lean += 10 * push;
            lunge = 1.8f * u * Math.max(0, push);
            r.attack += step;
            if (r.attack > 12) r.attack = -1;
        }
        float hurt = 0;
        if (r.hurt >= 0) {   // 피격: 뒤로 젖혀졌다 돌아옴
            hurt = 1 - r.hurt / 6f;
            lean -= 11 * hurt;
            sx *= 1 + 0.05f * hurt;
            sy *= 1 - 0.05f * hurt;
            r.hurt += step;
            if (r.hurt > 6) r.hurt = -1;
        }
        float grow = r.age >= 10 ? 1 : ease(r.age / 10f);   // 등장: 땅에서 솟아남
        float sink = (1 - grow) * -0.6f * k;

        // ---- 머리: 노리는 플레이어 쪽으로 (몸 기준 ±50°), 없으면 두리번
        float headT;
        LivingEntity tgt = mob instanceof Mob mm ? mm.getTarget() : null;
        if (tgt != null && tgt.getWorld().equals(mob.getWorld())) {
            double dx = tgt.getLocation().getX() - l.getX(), dz = tgt.getLocation().getZ() - l.getZ();
            float want = (float) Math.toDegrees(-Math.atan2(dx, dz));
            headT = Math.max(-50, Math.min(50, wrap(want - r.yaw)));
        } else headT = (float) (Math.sin(t * 0.35 + (r.seed & 7)) * 28 * (1 - r.speed));
        r.headYaw += (headT - r.headYaw) * 0.35f;

        double yr = Math.toRadians(r.yaw);
        float fx = (float) (-Math.sin(yr)) * lunge, fz = (float) Math.cos(yr) * lunge;
        float offY = (float) (ds.get(0).getLocation().getY() - l.getY());   // 탄 높이만큼 내려서 발을 땅에
        Quaternionf qb = new Quaternionf().rotateY((float) (-yr + Math.PI)).rotateX(rad(-lean)).rotateZ(rad(roll));
        Vector3f base = new Vector3f(fx, bob - offY + sink, fz);

        float swing = (float) Math.sin(r.phase) * r.speed;
        float idle = (float) Math.sin(t * 1.6) * (1 - r.speed);
        for (int i = 0; i < ds.size(); i++) {
            Part p = r.def.parts().get(i);
            Quaternionf q = new Quaternionf();
            switch (p.name()) {
                case "head" -> q.rotateY(rad(-r.headYaw)).rotateX(rad(Math.sin(t * 0.9) * 3 * (1 - r.speed) + Math.abs(swing) * 3 + hurt * 12));
                case "leg_l" -> q.rotateX(rad(-34 * swing));
                case "leg_r" -> q.rotateX(rad(34 * swing));
                case "arm_l" -> q.rotateX(rad(26 * swing + 3 * idle + atkArmL + 15 * hurt)).rotateZ(rad(-3 - 2 * idle));
                case "arm_r" -> q.rotateX(rad((r.attack >= 0 ? 0 : -26 * swing) - 3 * idle + atkArm + 15 * hurt)).rotateZ(rad(3 + 2 * idle));
                case "leg_fl", "leg_br" -> q.rotateX(rad(-30 * swing));
                case "leg_fr", "leg_bl" -> q.rotateX(rad(30 * swing));
                case "wing_l", "wing_r" -> {
                    float a = flying ? (float) (Math.sin(t * 5.5) * 32) : (float) (Math.sin(t * 1.4) * 7 + 4 + r.speed * 10 * Math.sin(r.phase * 2));
                    q.rotateZ(rad(p.name().equals("wing_r") ? a : -a));
                }
                default -> { }
            }
            // 관절 위치 (발 기준, 블록 단위) → 몸 회전 적용
            Vector3f piv = new Vector3f((p.px() - 8) * u * sx, p.py() * u * sy, (p.pz() - 8) * u * sx);
            qb.transform(piv);
            Vector3f tr = new Vector3f(base).add(piv);
            Quaternionf left = new Quaternionf(qb).mul(q);
            float g = grow;
            Transformation tf = new Transformation(tr, left, new Vector3f(k * sx * g, k * sy * g, k * sx * g), new Quaternionf());
            ItemDisplay d = ds.get(i);
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(step);
            d.setTransformation(tf);
        }
    }

    /** 스킬을 쓸 때 (CustomMobManager) 무기를 휘두르는 자세 */
    public void attackPose(LivingEntity mob) {
        Rig r = rigs.get(mob.getUniqueId());
        if (r != null && r.attack < 0) r.attack = 0;
    }

    /** 모델을 태운 몹은 그대로는 순간이동이 안 됨 → 내려서 옮기고 다시 태움 */
    public boolean teleport(LivingEntity mob, Location to) {
        Rig r = rigs.get(mob.getUniqueId());
        List<Entity> ds = new ArrayList<>();
        if (r != null) {
            List<UUID> all = new ArrayList<>(r.ids);
            if (r.nameId != null) all.add(r.nameId);   // 체력 이름표도 함께 내림
            for (UUID id : all) {
                Entity d = Bukkit.getEntity(id);
                if (d != null) { mob.removePassenger(d); ds.add(d); }
            }
        }
        boolean ok = mob.teleport(to);
        for (Entity d : ds) {
            if (!d.isValid()) continue;
            d.teleport(new Location(to.getWorld(), to.getX(), to.getY(), to.getZ(), 0, 0));
            Bukkit.getScheduler().runTaskLater(plugin, () -> { if (d.isValid() && mob.isValid()) mob.addPassenger(d); }, 1L);
        }
        if (r != null) { r.lastX = to.getX(); r.lastZ = to.getZ(); }
        return ok;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent e) {
        Rig r = rigs.get(e.getEntity().getUniqueId());
        if (r != null && r.attack < 0) r.hurt = 0;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof LivingEntity le) attackPose(le);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent e) {
        attackPose(e.getEntity());
    }

    /** v5.9.4: 물약 · 화염구 · 삼지창 던지기도 공격 자세 (전에는 가만히 서서 던지는 것처럼 보였음) */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onThrow(org.bukkit.event.entity.ProjectileLaunchEvent e) {
        if (e.getEntity().getShooter() instanceof LivingEntity le) attackPose(le);
    }

    /** 주문 (소환사 · 흑마법사의 송곳니 · 소환) 도 공격 자세 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpell(org.bukkit.event.entity.EntitySpellCastEvent e) {
        if (e.getEntity() instanceof LivingEntity le) attackPose(le);
    }

    /** 모델을 쓴 몹은 투구를 치웠으므로 햇빛에 타지 않게 (원래 투구를 쓴 몬스터가 많음) */
    @EventHandler(ignoreCancelled = true)
    public void onSun(EntityCombustEvent e) {
        if (e instanceof EntityCombustByEntityEvent || e instanceof EntityCombustByBlockEvent) return;
        if (rigs.containsKey(e.getEntity().getUniqueId())) e.setCancelled(true);
    }

    private final Set<UUID> dying = new HashSet<>();

    /** 죽음: 모델을 내려 그 자리에서 옆으로 쓰러지며 가라앉은 뒤 사라짐 */
    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        LivingEntity mob = e.getEntity();
        Rig r = rigs.remove(mob.getUniqueId());
        if (r == null) return;
        removeName(r);
        Location l = mob.getLocation();
        List<ItemDisplay> ds = new ArrayList<>();
        List<Transformation> base = new ArrayList<>();
        for (UUID id : r.ids) {
            owned.remove(id);
            if (!(Bukkit.getEntity(id) instanceof ItemDisplay d)) continue;
            float offY = (float) (d.getLocation().getY() - l.getY());
            mob.removePassenger(d);
            d.teleport(new Location(l.getWorld(), l.getX(), l.getY(), l.getZ(), 0, 0));
            Transformation tf = d.getTransformation();
            base.add(new Transformation(new Vector3f(tf.getTranslation()).add(0, offY, 0), tf.getLeftRotation(), tf.getScale(), tf.getRightRotation()));
            ds.add(d);
            dying.add(d.getUniqueId());
        }
        if (ds.isEmpty()) return;
        double yr = Math.toRadians(Float.isNaN(r.yaw) ? l.getYaw() : r.yaw);
        Vector3f side = new Vector3f((float) Math.cos(yr), 0, (float) Math.sin(yr));   // 몸의 옆 방향 축으로 쓰러짐
        int[] n = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            n[0] += 2;
            float f = Math.min(1, n[0] / 12f), fade = n[0] <= 16 ? 1 : Math.max(0, 1 - (n[0] - 16) / 8f);
            Quaternionf fall = new Quaternionf().rotateAxis(rad(-85 * ease(f)), side.x, side.y, side.z);
            for (int i = 0; i < ds.size(); i++) {
                ItemDisplay d = ds.get(i);
                if (!d.isValid()) continue;
                Transformation b = base.get(i);
                Vector3f tr = new Vector3f(b.getTranslation());
                fall.transform(tr);
                tr.add(0, -0.08f * r.k * f - (1 - fade) * 0.3f * r.k, 0);   // 쓰러지며 살짝 가라앉고, 마지막에 땅속으로
                d.setInterpolationDelay(0);
                d.setInterpolationDuration(2);
                d.setTransformation(new Transformation(tr, new Quaternionf(fall).mul(b.getLeftRotation()), new Vector3f(b.getScale()).mul(0.3f + 0.7f * fade), b.getRightRotation()));
            }
            if (n[0] >= 24) {
                for (ItemDisplay d : ds) { dying.remove(d.getUniqueId()); d.remove(); }
                task.cancel();
            }
        }, 1L, 2L);
    }

    public void shutdown() {
        for (Map.Entry<UUID, Rig> en : new ArrayList<>(rigs.entrySet())) {
            removeRig(en.getKey(), en.getValue());
            if (Bukkit.getEntity(en.getKey()) instanceof LivingEntity le) le.setInvisible(false);   // 모델 없이 투명하게 남지 않게
        }
        rigs.clear();
    }
}
