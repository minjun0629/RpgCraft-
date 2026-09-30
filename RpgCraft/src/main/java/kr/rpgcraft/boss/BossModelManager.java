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
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
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
            "megalodon", "kraken", "mount_wolf", "mount_lizard", "mount_warhorse", "mount_icebear", "mount_lion", "mount_panther", "mount_griffin", "mount_dragon",
            "pet_slime", "pet_chick", "pet_bunny", "pet_fox", "pet_penguin", "pet_owl", "pet_golem", "pet_fairy", "pet_ghost", "pet_phoenix", "pet_dragon", "pet_star",
            // v5.4.9 필드 보스 (뒤에 붙여 기존 CustomModelData 번호 유지 — tools/boss_models.py BOSS_ORDER 와 같아야 함)
            "field_boar_king", "field_frost_bear", "field_bandit_lord", "field_ravager", "field_ancient_golem",
            "field_swamp_witch", "field_flame_knight", "field_deep_warden", "field_frost_lich");
    private static final Map<String, Float> SCALE = new HashMap<>();
    /** 보스 모델 좌표는 장식 공간을 위해 (8,8,8) 기준 0.75 배로 줄여 저장됨 (tools/boss_models.py BOSS_SHRINK) → 그릴 때 되돌림 */
    private static final float MODEL_SHRINK = 0.75f;
    /** 줄인 뒤 6px 아래로 내려 저장됨 (BOSS_DROP) → 6/16 × scale/0.75 = 0.5 × scale 블록 더 올림 */
    private static final float MODEL_LIFT = 0.5f;

    static {
        String[] ids = {"witch", "elf_queen", "dwarf_king", "harpy_queen", "sea_gatekeeper", "bungbung", "desert_nightmare", "siphonia", "kain",
                "frost_queen", "volcano_giant", "void_apostle", "thunder_god", "primordial_dragon", "vengeful_spirit", "balrog", "megalodon", "kraken"};
        // 월드보스는 압도적으로 크게, 나머지 보스도 전보다 크게
        float[] sc = {2.0f, 2.0f, 2.2f, 2.0f, 2.3f, 2.2f, 3.6f, 3.0f, 3.8f, 2.4f, 3.2f, 2.8f, 2.8f, 4.0f, 3.4f, 3.8f, 7.0f, 9.0f};   // 메갈로돈 · 크라켄은 압도적인 크기
        for (int i = 0; i < ids.length; i++) SCALE.put(ids[i], sc[i]);
        String[] fids = {"field_boar_king", "field_frost_bear", "field_bandit_lord", "field_ravager", "field_ancient_golem",
                "field_swamp_witch", "field_flame_knight", "field_deep_warden", "field_frost_lich"};
        float[] fsc = {2.4f, 2.6f, 2.2f, 2.8f, 3.0f, 2.2f, 2.5f, 2.8f, 2.6f};
        for (int i = 0; i < fids.length; i++) SCALE.put(fids[i], fsc[i]);
    }

    private final RpgCraft plugin;
    private final Map<UUID, UUID> displays = new HashMap<>();
    private final Map<UUID, Float> scales = new HashMap<>();
    /** v5.9.3 보스 → 체력 이름표 (게임이 모델을 태운 몹의 이름표를 그리지 않음) */
    private final Map<UUID, UUID> names = new HashMap<>();
    private final Map<UUID, String> ids = new HashMap<>();
    /** 보스 → 모델 크기의 판정 상자 (Interaction). 1.20.1 은 몹 크기를 못 바꾸므로 이걸로 대신 맞게 한다 */
    private final Map<UUID, UUID> hitboxes = new HashMap<>();
    private final Map<UUID, UUID> hitboxOwner = new HashMap<>();
    private int tick;

    /** v5.8.1 자연스러운 움직임: 보스마다 부드럽게 도는 방향 · 걸음 주기 · 공격 · 피격 자세 */
    private static final class Pose {
        float yaw = Float.NaN, speed, bank, pitch, climb;
        double phase, lastX, lastY, lastZ;
        int attack = -1, hurt = -1;
    }

    /** v5.9.5 보스마다 다른 움직임: 걷기 · 육중한 걸음 · 떠다니기 · 날기(활공) · 헤엄 · 맥동 · 일렁이는 불꽃 · 네발 걸음 */
    private enum Gait { WALK, HEAVY, FLOAT, FLY, SWIM, PULSE, FLAME, BEAST }

    private static Gait gait(String id) {
        if (id == null) return Gait.WALK;
        return switch (id) {
            case "primordial_dragon", "harpy_queen" -> Gait.FLY;
            case "megalodon" -> Gait.SWIM;
            case "kraken" -> Gait.PULSE;
            case "bungbung" -> Gait.FLAME;
            case "witch", "siphonia", "frost_queen", "void_apostle", "vengeful_spirit", "field_swamp_witch", "field_frost_lich", "thunder_god" -> Gait.FLOAT;
            case "volcano_giant", "balrog", "dwarf_king", "field_ancient_golem", "field_deep_warden" -> Gait.HEAVY;
            case "field_boar_king", "field_frost_bear", "field_ravager" -> Gait.BEAST;
            default -> Gait.WALK;
        };
    }

    private final Map<UUID, Pose> poses = new HashMap<>();

    public BossModelManager(RpgCraft plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::follow, 1L, 1L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::scan, 20L, 100L);   // 렉 줄이기: 전체 엔티티 검사는 5초마다 (새 보스는 등장 때 바로 붙임, v5.4.29)
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("boss-models.enabled", true);
    }

    /** 1초마다: 모델이 없는 보스에게 모델을 붙인다 (소환 직후, 서버 재시작 후 포함) */
    private void scan() {
        if (!enabled()) return;
        for (World w : Bukkit.getWorlds()) {
            // 잔상 제거 (v5.5.0): 플러그인을 다시 불러오는 등으로 주인을 잃은 보스 모델 · 판정 상자가 그 자리에 멈춰 남아 있었음
            for (ItemDisplay idp : w.getEntitiesByClass(ItemDisplay.class)) {
                if (!idp.getPersistentDataContainer().has(Keys.INDICATOR, PersistentDataType.BYTE) || displays.containsValue(idp.getUniqueId())) continue;
                ItemStack st = idp.getItemStack();
                int cmd = st != null && st.hasItemMeta() && st.getItemMeta().hasCustomModelData() ? st.getItemMeta().getCustomModelData() - 9000 : -1;
                if (cmd >= 0 && cmd < ORDER.size() && !ORDER.get(cmd).startsWith("mount_") && !ORDER.get(cmd).startsWith("pet_")) idp.remove();
            }
            for (Interaction box : w.getEntitiesByClass(Interaction.class))
                if (box.getPersistentDataContainer().has(Keys.INDICATOR, PersistentDataType.BYTE) && !hitboxOwner.containsKey(box.getUniqueId())) box.remove();
            for (LivingEntity le : w.getLivingEntities()) {
                String id = le.getPersistentDataContainer().get(Keys.BOSS, PersistentDataType.STRING);
                if (id == null || displays.containsKey(le.getUniqueId()) || !ORDER.contains(id)) continue;
                attach(le, id);
            }
        }
    }

    /** 보스가 등장할 때 바로 모델을 붙임 */
    public void ensure(LivingEntity le, String id) {
        if (enabled() && id != null && !displays.containsKey(le.getUniqueId()) && ORDER.contains(id)) attach(le, id);
    }

    private Transformation tf(float scale, float bob, float yawRad) {
        // 게임이 ItemDisplay 의 아이템을 Y축 180° 돌려 그리므로 π 를 더해 정면을 맞춤
        return new Transformation(new Vector3f(0, (0.5f + MODEL_LIFT) * scale + bob, 0), new AxisAngle4f(yawRad + (float) Math.PI, 0, 1, 0), new Vector3f(scale / MODEL_SHRINK), new AxisAngle4f());
    }

    public void attach(LivingEntity boss, String id) {
        boolean custom = plugin.getConfig().contains("boss-models.scale." + id);
        float scale = (float) (plugin.getConfig().getDouble("boss-models.scale." + id, SCALE.getOrDefault(id, 1.8f))
                * (custom ? 1 : plugin.getConfig().getDouble("boss-models.size-mult", 1.15)));   // v5.8.0 더 크고 웅장하게 (판정 상자도 같이 커짐)
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        m.setCustomModelData(9000 + ORDER.indexOf(id));
        it.setItemMeta(m);
        Location at = boss.getLocation().clone();
        at.setYaw(0);   // 방향은 변환(회전)으로 부드럽게 돌림 — 엔티티 자체는 0
        at.setPitch(0);
        ItemDisplay d = boss.getWorld().spawn(at, ItemDisplay.class, x -> {
            x.setItemStack(it);
            x.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            x.setPersistent(false);
            x.setShadowRadius(0.8f * scale);
            x.setShadowStrength(0.6f);
            x.setTransformation(tf(scale, 0, yawOffset()));
            x.getPersistentDataContainer().set(Keys.INDICATOR, PersistentDataType.BYTE, (byte) 1);
        });
        boss.addPassenger(d);   // v5.8.1: 보스에 태워서 클라이언트가 함께 부드럽게 움직이게 (매 틱 순간이동하면 1.20.1 에서는 뚝뚝 끊김)
        boss.setInvisible(true);
        if (boss.getEquipment() != null) boss.getEquipment().clear();
        displays.put(boss.getUniqueId(), d.getUniqueId());
        scales.put(boss.getUniqueId(), scale);
        ids.put(boss.getUniqueId(), id);
        names.put(boss.getUniqueId(), kr.rpgcraft.util.NameTag.spawn(boss).getUniqueId());
        attachHitbox(boss, scale);
    }

    private void attachHitbox(LivingEntity boss, float scale) {
        if (!plugin.getConfig().getBoolean("boss-models.hitbox", false)) return;
        double mult = plugin.getConfig().getDouble("boss-models.hitbox-mult", 1.0);
        float h = (float) (scale * mult), wdt = (float) (scale * 0.75 * mult);
        if (h <= boss.getHeight() + 0.2 && wdt <= boss.getWidth() + 0.2) return;   // 원래 몸이 더 크면 필요 없음
        Interaction box = boss.getWorld().spawn(boss.getLocation(), Interaction.class, x -> {
            x.setInteractionWidth(Math.max(wdt, (float) boss.getWidth()));
            x.setInteractionHeight(Math.max(h, (float) boss.getHeight()));
            x.setResponsive(true);
            x.setPersistent(false);
            x.getPersistentDataContainer().set(Keys.INDICATOR, PersistentDataType.BYTE, (byte) 1);
        });
        hitboxes.put(boss.getUniqueId(), box.getUniqueId());
        hitboxOwner.put(box.getUniqueId(), boss.getUniqueId());
    }

    /** 모델 꼭대기 높이 (블록) — 모델마다 대략 (사람형은 왕관 · 날개까지) */
    private static float top(String id, float s) {
        if (id == null) return s * 1.9f;
        return switch (id) {
            case "megalodon" -> s * 1.1f;
            case "kraken" -> s * 1.6f;
            case "primordial_dragon" -> s * 1.7f;
            case "field_boar_king", "field_frost_bear", "field_ravager" -> s * 1.25f;
            case "bungbung", "volcano_giant" -> s * 1.9f;
            default -> s * 1.95f;
        };
    }

    private void removeName(UUID boss) {
        ids.remove(boss);
        UUID t = names.remove(boss);
        Entity e = t == null ? null : Bukkit.getEntity(t);
        if (e != null) e.remove();
    }

    private void removeHitbox(UUID boss) {
        UUID hb = hitboxes.remove(boss);
        if (hb == null) return;
        hitboxOwner.remove(hb);
        Entity e = Bukkit.getEntity(hb);
        if (e != null) e.remove();
    }

    /** 투사체 · 광선형 스킬 판정용: 살아있는 개체는 그대로, 보스 판정 상자는 그 보스로 바꿔 준다 (아니면 null) */
    public LivingEntity resolve(Entity en) {
        if (en instanceof LivingEntity le) return le;
        if (!(en instanceof Interaction)) return null;
        UUID owner = hitboxOwner.get(en.getUniqueId());
        return owner != null && Bukkit.getEntity(owner) instanceof LivingEntity boss && !boss.isDead() ? boss : null;
    }

    /** 판정 상자를 때리면 보스를 때린 것으로 (공격 쿨다운 · 치명타 · 무기 스킬 모두 원래대로) */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onHitbox(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Interaction)) return;
        UUID owner = hitboxOwner.get(e.getEntity().getUniqueId());
        if (owner == null) return;
        e.setCancelled(true);
        if (!(e.getDamager() instanceof Player p) || !(Bukkit.getEntity(owner) instanceof LivingEntity boss) || boss.isDead()) return;
        if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;
        p.attack(boss);
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
                poses.remove(en.getKey());
                removeHitbox(en.getKey());
                removeName(en.getKey());
                it.remove();
                continue;
            }
            Location l = boss.getLocation();
            if (!boss.getPassengers().contains(d)) {   // 순간이동 등으로 내려졌으면 다시 태움
                d.teleport(new Location(l.getWorld(), l.getX(), l.getY(), l.getZ(), 0, 0));
                boss.addPassenger(d);
            }
            UUID hb = hitboxes.get(en.getKey());
            if (hb != null) {
                Entity box = Bukkit.getEntity(hb);
                if (box != null && box.isValid()) box.teleport(new Location(l.getWorld(), l.getX(), l.getY(), l.getZ()));
                else removeHitbox(en.getKey());
            }
            if (tick % 2 == 0 && d instanceof ItemDisplay id) animate(boss, id, en.getKey());
            if (tick % 5 == 0) {   // 체력 이름표
                UUID tid = names.get(en.getKey());
                if (tid != null && Bukkit.getEntity(tid) instanceof TextDisplay td && td.isValid()) {
                    float s = scales.getOrDefault(en.getKey(), 2f);
                    kr.rpgcraft.util.NameTag.update(boss, td, top(ids.get(en.getKey()), s), Math.max(1.2f, Math.min(3f, s * 0.45f)));
                } else names.put(en.getKey(), kr.rpgcraft.util.NameTag.spawn(boss).getUniqueId());
            }
        }
    }

    private static float wrap(float deg) {
        deg %= 360;
        if (deg > 180) deg -= 360;
        if (deg < -180) deg += 360;
        return deg;
    }

    /**
     * 2틱마다: 방향을 천천히 돌리고(급회전 없음), 걸을 때는 발걸음에 맞춰 위아래 · 좌우로 흔들리며 앞으로 기울고,
     * 서 있을 때는 숨쉬듯 부풀었다 가라앉는다. 공격하면 앞으로 내지르고, 맞으면 뒤로 젖혀진다. (모두 보간되어 매끄럽게)
     */
    private void animate(LivingEntity boss, ItemDisplay d, UUID id) {
        float s = scales.getOrDefault(id, 1.8f);
        Pose p = poses.computeIfAbsent(id, k -> new Pose());
        Gait g = gait(ids.get(id));
        Location l = boss.getLocation();
        float target = l.getYaw();
        if (Float.isNaN(p.yaw)) { p.yaw = target; p.lastX = l.getX(); p.lastY = l.getY(); p.lastZ = l.getZ(); }
        // 큰 몸일수록 천천히 돈다 (용 · 상어는 크게 선회)
        float maxTurn = switch (g) { case FLY, SWIM -> 7f; case HEAVY, PULSE -> 9f; case BEAST -> 11f; default -> 16f; };
        float diff = wrap(target - p.yaw), step = Math.max(-maxTurn, Math.min(maxTurn, diff * (g == Gait.FLY || g == Gait.SWIM ? 0.2f : 0.35f)));
        p.yaw = wrap(p.yaw + step);
        double moved = Math.hypot(l.getX() - p.lastX, l.getZ() - p.lastZ), vy = l.getY() - p.lastY;
        p.lastX = l.getX();
        p.lastY = l.getY();
        p.lastZ = l.getZ();
        float sp = (float) Math.min(1, moved / 0.28);
        p.speed = p.speed * 0.6f + sp * 0.4f;
        double t = tick * 0.05;
        float bob, roll, lean, sx = 1, sy = 1, lunge = 0, yawWobble = 0;
        switch (g) {
            case FLY -> {   // 날개로 활공: 뒤뚱거림 없이 날갯짓에 맞춰 크게 오르내리고, 도는 쪽으로 몸을 기울이며, 오를 땐 머리를 들고 내려갈 땐 숙임
                p.phase += 0.12 + p.speed * 0.1;
                p.bank += (Math.max(-28f, Math.min(28f, -step * 3.2f)) - p.bank) * 0.25f;
                p.climb += ((float) Math.max(-22, Math.min(22, vy * 60)) - p.climb) * 0.3f;
                bob = (float) (Math.sin(p.phase) * 0.07 * s);
                roll = p.bank + (float) Math.sin(p.phase * 0.5) * 2;
                lean = -p.climb + 4f * p.speed;
                sy = (float) (1 + 0.012 * Math.sin(p.phase));
            }
            case SWIM -> {   // 헤엄: 꼬리를 좌우로 저으며 (몸 방향이 살랑), 도는 쪽으로 기울고, 위아래로는 부드럽게
                p.phase += 0.2 + p.speed * 0.5;
                p.bank += (Math.max(-22f, Math.min(22f, -step * 2.6f)) - p.bank) * 0.25f;
                p.climb += ((float) Math.max(-18, Math.min(18, vy * 50)) - p.climb) * 0.3f;
                yawWobble = (float) (Math.sin(p.phase) * (4 + 5 * p.speed));
                bob = (float) (Math.sin(t * 0.9) * 0.02 * s);
                roll = p.bank;
                lean = -p.climb;
            }
            case PULSE -> {   // 크라켄: 외투막이 부풀었다 오그라들며 둥실
                p.phase += 0.1 + p.speed * 0.15;
                bob = (float) (Math.sin(p.phase) * 0.05 * s);
                roll = (float) (Math.sin(t * 0.45) * 3) - step * 0.2f;
                lean = 2f * p.speed;
                sy = (float) (1 + 0.05 * Math.sin(p.phase * 2));
                sx = (float) (1 - 0.03 * Math.sin(p.phase * 2));
            }
            case FLAME -> {   // 불꽃 정령: 떠서 일렁임 (가늘어졌다 부풀었다)
                p.phase += 0.3;
                bob = (float) (Math.sin(t * 2.1) * 0.035 * s);
                roll = (float) (Math.sin(t * 2.9) * 2.5) - step * 0.2f;
                lean = 6f * p.speed;
                sy = (float) (1 + 0.03 * Math.sin(t * 6.3) + 0.02 * Math.sin(t * 9.7));
                sx = (float) (1 - 0.02 * Math.sin(t * 6.3));
            }
            case FLOAT -> {   // 떠다니기: 걸음 없이 둥실 · 미끄러지듯 앞으로 기울어 이동
                p.phase += 0.1;
                bob = (float) (Math.sin(t * 1.4) * 0.04 * s);
                roll = (float) (Math.sin(t * 0.8) * 2) - step * 0.25f;
                lean = 6f * p.speed;
                sy = (float) (1 + 0.012 * Math.sin(t * 1.4));
            }
            case HEAVY -> {   // 육중한 걸음: 느리고 깊게 쿵쿵, 발 디딜 때 살짝 눌림
                p.phase += 0.14 + p.speed * 0.45;
                double foot = Math.abs(Math.sin(p.phase));
                bob = (float) (foot * 0.08 * s * p.speed + Math.sin(t * 1.1) * 0.02 * s * (1 - p.speed));
                roll = (float) (Math.sin(p.phase) * 2.2 * p.speed) - step * 0.2f;
                lean = 3f * p.speed;
                sy = (float) (1 - 0.035 * (1 - foot) * p.speed + 0.012 * Math.sin(t * 1.1) * (1 - p.speed));
                sx = (float) (1 + 0.02 * (1 - foot) * p.speed);
            }
            case BEAST -> {   // 네발 걸음: 좌우 흔들림 대신 앞뒤로 끄덕이며
                p.phase += 0.2 + p.speed * 0.8;
                bob = (float) (Math.abs(Math.sin(p.phase)) * 0.04 * s * p.speed + Math.sin(t * 1.5) * 0.015 * s * (1 - p.speed));
                roll = (float) (Math.sin(p.phase) * 1.2 * p.speed) - step * 0.2f;
                lean = 2f * p.speed + (float) (Math.sin(p.phase * 2) * 2.5 * p.speed);
                sy = (float) (1 + 0.015 * Math.sin(t * 1.5) * (1 - p.speed));
            }
            default -> {   // 사람형 걷기
                p.phase += 0.25 + p.speed * 0.9;
                bob = (float) (Math.abs(Math.sin(p.phase)) * 0.06 * s * p.speed + Math.sin(t * 1.6) * 0.025 * s * (1 - p.speed));
                roll = (float) (Math.sin(p.phase) * 3.5 * p.speed - step * 0.3);
                lean = 5f * p.speed;
                sy = (float) (1 + 0.018 * Math.sin(t * 1.6) * (1 - p.speed));
            }
        }
        if (p.attack >= 0) {   // 공격: 크게 앞으로 내지름 (5단계)
            float f = (float) Math.sin(Math.PI * p.attack / 5.0);
            lean += 14 * f;
            sx *= 1 + 0.1f * f;
            sy *= 1 + 0.1f * f;
            lunge = 0.28f * s * f;
            if (++p.attack > 5) p.attack = -1;
        }
        if (p.hurt >= 0) {   // 피격: 뒤로 젖혀졌다 돌아옴
            float f = 1 - p.hurt / 3f;
            lean -= 7 * f;
            sx *= 1 + 0.04f * f;
            sy *= 1 - 0.04f * f;
            if (++p.hurt > 3) p.hurt = -1;
        }
        double yr = Math.toRadians(p.yaw + yawWobble);
        float fx = (float) (-Math.sin(yr)) * lunge, fz = (float) Math.cos(yr) * lunge;
        float offY = boss.getPassengers().contains(d) ? (float) (d.getLocation().getY() - l.getY()) : 0;   // 탄 높이만큼 내려서 발을 땅에
        Quaternionf q = new Quaternionf().rotateY((float) (-yr + yawOffset() + Math.PI)).rotateX((float) Math.toRadians(-lean)).rotateZ((float) Math.toRadians(roll));
        float k = s / MODEL_SHRINK;
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(2);
        d.setTransformation(new Transformation(new Vector3f(fx, (0.5f + MODEL_LIFT) * s + bob - offY, fz), q, new Vector3f(k * sx, k * sy, k * sx), new Quaternionf()));
    }

    /** 보스 순간이동: 태운 모델을 내려 함께 옮긴 뒤 다시 태움 (탑승 중인 엔티티는 순간이동이 안 되므로) */
    public void teleportBoss(LivingEntity boss, Location to) {
        UUID did = displays.get(boss.getUniqueId());
        Entity d = did == null ? null : Bukkit.getEntity(did);
        if (d != null) boss.removePassenger(d);
        UUID tid = names.get(boss.getUniqueId());   // 체력 이름표도 내려야 순간이동됨
        Entity tag = tid == null ? null : Bukkit.getEntity(tid);
        if (tag != null) boss.removePassenger(tag);
        boss.teleport(to);
        if (tag != null && tag.isValid()) {
            tag.teleport(new Location(to.getWorld(), to.getX(), to.getY(), to.getZ(), 0, 0));
            Bukkit.getScheduler().runTaskLater(plugin, () -> { if (tag.isValid() && boss.isValid()) boss.addPassenger(tag); }, 1L);
        }
        if (d != null && d.isValid()) {
            d.teleport(new Location(to.getWorld(), to.getX(), to.getY(), to.getZ(), 0, 0));
            Bukkit.getScheduler().runTaskLater(plugin, () -> { if (d.isValid() && boss.isValid()) boss.addPassenger(d); }, 1L);
        }
        Pose p = poses.get(boss.getUniqueId());
        if (p != null) { p.lastX = to.getX(); p.lastY = to.getY(); p.lastZ = to.getZ(); p.yaw = to.getYaw(); }
    }

    /** 피격 시 뒤로 살짝 젖혀짐 (animate 에서 보간) */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent e) {
        if (!displays.containsKey(e.getEntity().getUniqueId())) return;
        Pose p = poses.computeIfAbsent(e.getEntity().getUniqueId(), k -> new Pose());
        if (p.attack < 0) p.hurt = 0;
    }

    /** 보스가 스킬을 쓸 때 (BossManager 에서 호출): 앞으로 크게 내지르는 자세 */
    public void attackPose(LivingEntity boss) {
        if (!displays.containsKey(boss.getUniqueId())) return;
        poses.computeIfAbsent(boss.getUniqueId(), k -> new Pose()).attack = 0;
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        UUID did = displays.remove(e.getEntity().getUniqueId());
        scales.remove(e.getEntity().getUniqueId());
        poses.remove(e.getEntity().getUniqueId());
        removeHitbox(e.getEntity().getUniqueId());
        removeName(e.getEntity().getUniqueId());
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
        for (UUID boss : new ArrayList<>(hitboxes.keySet())) removeHitbox(boss);
        for (UUID boss : new ArrayList<>(names.keySet())) removeName(boss);
    }
}
