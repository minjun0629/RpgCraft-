package kr.rpgcraft.mob;

import kr.rpgcraft.Keys;
import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.util.Fx;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 커스텀 몬스터 (mobs.yml).
 * 자연 스폰되는 적대 몹이 일정 확률로 그 지역 레벨·바이옴에 맞는 커스텀 몬스터로 바뀐다.
 * 몬스터마다 이름, 장비, 체력/공격력 배율, 이동속도, 스킬(도약·독·화살 난사·화염구·치유·순간이동·소환·돌진·냉기),
 * 공격 시 상태이상, 전용 드롭을 가진다.
 */
public class CustomMobManager implements Listener {
    public static class Ability {
        public String type;
        public int interval = 8;
        public double radius = 3, power = 1.0;
        public int amount = 1;
        public String entity;
    }

    public static class Drop {
        public String item;
        public double chance;
        public int min = 1, max = 1;
    }

    public static class MobDef {
        public String id, name;
        public EntityType type;
        public int minLevel = 1, maxLevel = 999, weight = 10, size = 0;
        public boolean baby, natural = true, day;
        public double hpMult = 1, damageMult = 1, def = 0, expMult = 1.2, moneyMult = 1.2, speed = 1, scale = 0;
        public List<String> biomes = new ArrayList<>(), worlds = new ArrayList<>();
        public Map<String, String> equipment = new HashMap<>();
        public List<Ability> abilities = new ArrayList<>();
        public Map<String, Integer> onHit = new LinkedHashMap<>();
        public List<Drop> drops = new ArrayList<>();
        public String desc = "";
    }

    private final RpgCraft plugin;
    private final NamespacedKey KEY;
    private final Map<String, MobDef> defs = new LinkedHashMap<>();
    private final Map<UUID, MobDef> alive = new HashMap<>();
    private final Map<String, Long> cooldowns = new HashMap<>();

    public CustomMobManager(RpgCraft plugin) {
        this.plugin = plugin;
        this.KEY = new NamespacedKey(plugin, "custom_mob");
        load();
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 10L);
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (World w : Bukkit.getWorlds()) for (LivingEntity e : w.getLivingEntities()) reattach(e);
        });
    }

    // ------------------------------------------------------------------ 로드
    public void load() {
        defs.clear();
        File f = new File(plugin.getDataFolder(), "mobs.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        try (var in = plugin.getResource("mobs.yml")) {   // 새 버전에서 추가된 몬스터를 기존 파일에 채워 넣기
            if (in != null) {
                YamlConfiguration def = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
                boolean added = false;
                for (String k : def.getKeys(false)) if (!y.contains(k)) { y.set(k, def.get(k)); added = true; }
                if (added) y.save(f);
            }
        } catch (Exception ignored) {
        }
        if ("ZOMBIE".equalsIgnoreCase(y.getString("goblin.type", "")) || y.getDouble("goblin.speed", 0.55) > 0.7) { // 이전 버전 고블린 보정
            y.set("goblin.type", "PIGLIN");
            y.set("goblin.baby", false);
            y.set("goblin.speed", 0.55);   // 피글린은 원래 빨라서 좀비 정도로 낮춤
            try { y.save(f); } catch (java.io.IOException ignored) { }
        }
        for (String id : y.getKeys(false)) {
            ConfigurationSection s = y.getConfigurationSection(id);
            if (s == null) continue;
            try {
                MobDef d = new MobDef();
                d.id = id;
                d.name = s.getString("name", id);
                d.type = EntityType.valueOf(s.getString("type", "ZOMBIE").toUpperCase(Locale.ROOT));
                d.minLevel = s.getInt("level.min", 1);
                d.maxLevel = s.getInt("level.max", 999);
                d.weight = s.getInt("weight", 10);
                d.size = s.getInt("size", 0);
                d.baby = s.getBoolean("baby", false);
                d.natural = s.getBoolean("natural", true);   // false: 자연 스폰 대신 전용 스폰(낮·보물 수호자 등)만
                d.day = s.getBoolean("day", false);
                if (d.baby && plugin.getConfig().getBoolean("mobs.no-baby-zombies", true)
                        && (d.type.name().contains("ZOMBIE") || d.type == EntityType.HUSK || d.type == EntityType.DROWNED)) d.baby = false;
                d.hpMult = s.getDouble("hp-mult", 1);
                d.damageMult = s.getDouble("damage-mult", 1);
                d.def = s.getDouble("defense", 0);
                d.expMult = s.getDouble("exp-mult", 1.2);
                d.moneyMult = s.getDouble("money-mult", 1.2);
                d.speed = s.getDouble("speed", 1);
                // 크기: 지정이 없으면 체력 배율이 높은(강한) 몬스터일수록 크게
                d.scale = s.getDouble("scale", d.hpMult >= 2.4 ? 1.5 : d.hpMult >= 1.6 ? 1.25 : 1.0);
                d.desc = s.getString("desc", "");
                for (String b : s.getStringList("biomes")) d.biomes.add(b.toUpperCase(Locale.ROOT));
                d.worlds.addAll(s.getStringList("worlds"));
                ConfigurationSection eq = s.getConfigurationSection("equipment");
                if (eq != null) for (String k : eq.getKeys(false)) d.equipment.put(k, eq.getString(k));
                ConfigurationSection hit = s.getConfigurationSection("on-hit");
                if (hit != null) for (String k : hit.getKeys(false)) d.onHit.put(k.toUpperCase(Locale.ROOT), hit.getInt(k));
                for (Map<?, ?> m : s.getMapList("abilities")) {
                    Ability a = new Ability();
                    a.type = String.valueOf(m.get("type")).toUpperCase(Locale.ROOT);
                    if (m.get("interval") instanceof Number n) a.interval = n.intValue();
                    if (m.get("radius") instanceof Number n) a.radius = n.doubleValue();
                    if (m.get("power") instanceof Number n) a.power = n.doubleValue();
                    if (m.get("amount") instanceof Number n) a.amount = n.intValue();
                    if (m.get("entity") != null) a.entity = String.valueOf(m.get("entity"));
                    d.abilities.add(a);
                }
                for (Map<?, ?> m : s.getMapList("drops")) {
                    Drop dr = new Drop();
                    dr.item = String.valueOf(m.get("item"));
                    dr.chance = m.get("chance") instanceof Number n ? n.doubleValue() : 0.1;
                    if (m.get("min") instanceof Number n) dr.min = n.intValue();
                    if (m.get("max") instanceof Number n) dr.max = n.intValue();
                    d.drops.add(dr);
                }
                defs.put(id, d);
            } catch (Exception ex) {
                plugin.getLogger().warning("mobs.yml '" + id + "' 로드 실패: " + ex.getMessage());
            }
        }
        plugin.getLogger().info("커스텀 몬스터 " + defs.size() + "종 로드");
    }

    public Collection<MobDef> defs() {
        return defs.values();
    }

    public MobDef def(String id) {
        return defs.get(id);
    }

    public MobDef of(Entity e) {
        return alive.get(e.getUniqueId());
    }

    // ------------------------------------------------------------------ 자연 스폰 교체
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        if (!plugin.getConfig().getBoolean("custom-mobs.enabled", true)) return;
        if (e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) return;
        if (!(e.getEntity() instanceof Enemy)) return;
        double ch = plugin.getConfig().getDouble("custom-mobs.chance", 0.3) * (plugin.cycle() == null ? 1 : plugin.cycle().customMobChanceMult());
        if (ThreadLocalRandom.current().nextDouble() >= Math.min(0.9, ch)) return;
        LivingEntity orig = e.getEntity();
        Location l = orig.getLocation();
        int level = plugin.mobs().computeLevel(l);
        MobDef d = pick(l, level);
        if (d == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!orig.isValid()) return;
            Location at = orig.getLocation();
            plugin.mobs().forget(orig);
            orig.remove();
            LivingEntity sp = spawn(d, at, level);
            if (sp != null && plugin.tiers() != null) plugin.tiers().roll(sp);
        });
    }

    private MobDef pick(Location l, int level) {
        String biome = l.getBlock().getBiome().name();
        List<MobDef> ok = new ArrayList<>();
        int total = 0;
        for (MobDef d : defs.values()) {
            if (!d.natural || level < d.minLevel || level > d.maxLevel) continue;
            if (!d.worlds.isEmpty() && !d.worlds.contains(l.getWorld().getName())) continue;
            if (!d.biomes.isEmpty() && d.biomes.stream().noneMatch(biome::contains)) continue;
            ok.add(d);
            total += Math.max(1, d.weight);
        }
        if (ok.isEmpty()) return null;
        int r = ThreadLocalRandom.current().nextInt(total);
        for (MobDef d : ok) {
            r -= Math.max(1, d.weight);
            if (r < 0) return d;
        }
        return ok.get(0);
    }

    // ------------------------------------------------------------------ 소환 / 설정
    public LivingEntity spawn(MobDef d, Location l, int level) {
        Entity e = l.getWorld().spawnEntity(l, d.type);
        if (!(e instanceof LivingEntity le)) {
            e.remove();
            return null;
        }
        le.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, d.id);
        le.getPersistentDataContainer().set(Keys.LEVEL, PersistentDataType.INTEGER, level);
        setup(le, d, level);
        return le;
    }

    private void setup(LivingEntity le, MobDef d, int level) {
        MobManager mm = plugin.mobs();
        if (le instanceof Slime s && d.size > 0) s.setSize(d.size);
        if (le instanceof Ageable a) {
            if (d.baby) a.setBaby();
            else a.setAdult();
        }
        // 오버월드에서 좀비화되지 않도록 (고블린·피글린 광전사·네더 멧돼지)
        if (le instanceof PiglinAbstract pa) pa.setImmuneToZombification(true);
        if (le instanceof Hoglin h) h.setImmuneToZombification(true);
        EntityEquipment eq = le.getEquipment();
        if (eq != null) {
            eq.clear();
            for (Map.Entry<String, String> en : d.equipment.entrySet()) {
                ItemStack it = item(en.getValue());
                if (it == null) continue;
                switch (en.getKey().toLowerCase(Locale.ROOT)) {
                    case "helmet" -> { eq.setHelmet(it); eq.setHelmetDropChance(0); }
                    case "chest" -> { eq.setChestplate(it); eq.setChestplateDropChance(0); }
                    case "legs" -> { eq.setLeggings(it); eq.setLeggingsDropChance(0); }
                    case "boots" -> { eq.setBoots(it); eq.setBootsDropChance(0); }
                    case "hand" -> { eq.setItemInMainHand(it); eq.setItemInMainHandDropChance(0); }
                    case "offhand" -> { eq.setItemInOffHand(it); eq.setItemInOffHandDropChance(0); }
                    default -> { }
                }
            }
        }
        AttributeInstance sp = le.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (sp != null && d.speed != 1) sp.setBaseValue(sp.getDefaultValue() * d.speed);
        if (le instanceof Mob m) m.setRemoveWhenFarAway(true);
        long money = (long) (plugin.getConfig().getLong("mobs.money-per-level", 100) * level * d.moneyMult);
        mm.initCustom(le, level, mm.hpFor(level) * d.hpMult, mm.damageFor(level) * d.damageMult,
                Math.min(80, level * plugin.getConfig().getDouble("mobs.defense-per-level", 0.2) + d.def),
                (long) (mm.expFor(level) * d.expMult), money, d.name);
        alive.put(le.getUniqueId(), d);
        if (d.scale > 1 && plugin.tiers() != null) plugin.tiers().resize(le, d.scale);
    }

    private ItemStack item(String s) {
        if (s == null) return null;
        if (plugin.items().get(s) != null) return plugin.items().create(s, 1);
        Material m = Material.matchMaterial(s);
        return m == null ? null : new ItemStack(m);
    }

    /** 서버 재시작/청크 로드 후 커스텀 몬스터 정보 복구 */
    private void reattach(LivingEntity le) {
        String id = le.getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
        if (id == null || alive.containsKey(le.getUniqueId())) return;
        MobDef d = defs.get(id);
        if (d == null) return;
        Integer lv = le.getPersistentDataContainer().get(Keys.LEVEL, PersistentDataType.INTEGER);
        setup(le, d, lv == null ? plugin.mobs().computeLevel(le.getLocation()) : lv);
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        for (Entity en : e.getEntities()) if (en instanceof LivingEntity le) reattach(le);
    }

    // ------------------------------------------------------------------ 공격 시 상태이상
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        Entity src = e.getDamager();
        if (src instanceof Projectile pr && pr.getShooter() instanceof LivingEntity sh) src = sh;
        MobDef d = alive.get(src.getUniqueId());
        if (d == null || d.onHit.isEmpty() || !(e.getEntity() instanceof Player p)) return;
        for (Map.Entry<String, Integer> en : d.onHit.entrySet()) {
            PotionEffectType t = PotionEffectType.getByName(en.getKey());
            if (t != null) p.addPotionEffect(new PotionEffect(t, en.getValue() * 20, 0));
            else if (en.getKey().equals("FIRE")) p.setFireTicks(Math.max(p.getFireTicks(), en.getValue() * 20));
        }
    }

    // ------------------------------------------------------------------ 드롭
    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(EntityDeathEvent e) {
        UUID deadId = e.getEntity().getUniqueId();
        MobDef d = alive.get(deadId);
        if (d != null && e.getEntity().getKiller() != null) plugin.data().get(e.getEntity().getKiller()).counters.merge("mk_" + d.id, 1.0, Double::sum);   // 몬스터 도감
        Bukkit.getScheduler().runTask(plugin, () -> alive.remove(deadId));   // 의뢰(처치 대상) 판정이 끝난 뒤 정리
        if (d == null || e.getEntity().getKiller() == null) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (Drop dr : d.drops) {
            if (r.nextDouble() >= dr.chance) continue;
            ItemStack it = plugin.items().create(dr.item, r.nextInt(dr.min, Math.max(dr.min, dr.max) + 1));
            if (it != null) e.getDrops().add(it);
        }
    }

    // ------------------------------------------------------------------ 스킬
    private Player target(LivingEntity le) {
        if (le instanceof Mob m && m.getTarget() instanceof Player p && p.isValid() && p.getWorld().equals(le.getWorld())) return p;
        Player best = null;
        double bd = 14 * 14;
        for (Player p : le.getWorld().getPlayers()) {
            if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) continue;
            double dd = p.getLocation().distanceSquared(le.getLocation());
            if (dd < bd) {
                bd = dd;
                best = p;
            }
        }
        return best;
    }

    private void tick() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, MobDef>> it = alive.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, MobDef> en = it.next();
            Entity ent = Bukkit.getEntity(en.getKey());
            if (!(ent instanceof LivingEntity le) || !le.isValid() || le.isDead()) {
                it.remove();
                continue;
            }
            MobDef d = en.getValue();
            if (d.abilities.isEmpty()) continue;
            Player t = target(le);
            if (t == null) continue;
            for (int i = 0; i < d.abilities.size(); i++) {
                Ability a = d.abilities.get(i);
                String key = en.getKey() + ":" + i;
                if (cooldowns.getOrDefault(key, 0L) > now) continue;
                if ((!plugin.getConfig().getBoolean("mobs.passive-until-hit", true) || kr.rpgcraft.combat.CombatListener.provoked(le) || le.getPersistentDataContainer().has(kr.rpgcraft.Keys.MINION, org.bukkit.persistence.PersistentDataType.STRING)) && telegraph(le, t, a)) cooldowns.put(key, now + a.interval * 1000L + ThreadLocalRandom.current().nextInt(1500));
            }
        }
        vanillaSkills(now);
        if (cooldowns.size() > 5000) cooldowns.entrySet().removeIf(x -> x.getValue() < now);
    }

    // ------------------------------------------------------------------ 일반 몬스터도 약한 기술
    private static final Map<EntityType, Ability> WEAK = new HashMap<>();

    static {
        Object[][] t = {{EntityType.ZOMBIE, "CHARGE", 9, 0.5}, {EntityType.HUSK, "CHARGE", 9, 0.5}, {EntityType.DROWNED, "CHARGE", 9, 0.5},
                {EntityType.ZOMBIE_VILLAGER, "CHARGE", 9, 0.5}, {EntityType.SKELETON, "BACKSTEP", 7, 0.0}, {EntityType.STRAY, "BACKSTEP", 7, 0.0},
                {EntityType.SPIDER, "LEAP", 8, 0.5}, {EntityType.CAVE_SPIDER, "LEAP", 8, 0.4}, {EntityType.WITCH, "POISON_CLOUD", 12, 0.5},
                {EntityType.ENDERMAN, "BLINK", 10, 0.6}, {EntityType.PILLAGER, "VOLLEY", 10, 0.4}, {EntityType.VINDICATOR, "CHARGE", 8, 0.6},
                {EntityType.SLIME, "LEAP", 7, 0.4}, {EntityType.MAGMA_CUBE, "LEAP", 7, 0.4}, {EntityType.BLAZE, "FIREBALL", 9, 0.5},
                {EntityType.WITHER_SKELETON, "CHARGE", 8, 0.6}, {EntityType.PIGLIN_BRUTE, "CHARGE", 8, 0.6}, {EntityType.SILVERFISH, "LEAP", 9, 0.3}};
        for (Object[] o : t) {
            Ability a = new Ability();
            a.type = (String) o[1];
            a.interval = (Integer) o[2];
            a.power = (Double) o[3];
            a.amount = 2;
            a.radius = 3;
            WEAK.put((EntityType) o[0], a);
        }
    }

    private int vanillaTick;

    private void vanillaSkills(long now) {
        if (++vanillaTick % 4 != 0 || !plugin.getConfig().getBoolean("mobs.weak-skills", true)) return;
        Set<UUID> seen = new HashSet<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) continue;
            for (Entity e : p.getNearbyEntities(16, 8, 16)) {
                if (!(e instanceof LivingEntity le) || alive.containsKey(le.getUniqueId()) || !seen.add(le.getUniqueId())) continue;
                if (!plugin.getConfig().getBoolean("mobs.passive-until-hit", true) && (le instanceof org.bukkit.entity.Enderman || le instanceof org.bukkit.entity.PigZombie || le instanceof org.bukkit.entity.Spider
                        || le instanceof org.bukkit.entity.Piglin || le instanceof org.bukkit.entity.PolarBear || le instanceof org.bukkit.entity.Wolf w && !w.isTamed())
                        && le instanceof org.bukkit.entity.Mob mb && mb.getTarget() == null && le.getLocation().distanceSquared(p.getLocation()) < 14 * 14) {   // 중립 몹도 공격
                    if (le instanceof org.bukkit.entity.PigZombie pz) pz.setAngry(true);
                    if (le instanceof org.bukkit.entity.Wolf wf) wf.setAngry(true);
                    mb.setTarget(p);
                }
                if (!plugin.getConfig().getBoolean("mobs.passive-until-hit", true) && le instanceof org.bukkit.entity.IronGolem g && !g.isPlayerCreated() && g.getTarget() == null
                        && plugin.getConfig().getBoolean("mobs.golem-hostile", true)) g.setTarget(p);
                Ability a = WEAK.get(le.getType());
                if (a == null || plugin.mobs().peek(le) == null) continue;
                if (plugin.getConfig().getBoolean("mobs.passive-until-hit", true) && !kr.rpgcraft.combat.CombatListener.provoked(le)) continue;
                String key = le.getUniqueId() + ":w";
                if (cooldowns.getOrDefault(key, 0L) > now) continue;
                if (telegraph(le, p, a)) cooldowns.put(key, now + a.interval * 1000L + ThreadLocalRandom.current().nextInt(3000));
            }
        }
    }

    // ------------------------------------------------------------------ 낮: 인간형 몬스터 (산적·약탈단·용병…)
    public LivingEntity spawnDay(Player p) {
        World w = p.getWorld();
        int lv = plugin.mobs().computeLevel(p.getLocation());
        List<MobDef> ok = new ArrayList<>();
        for (MobDef d : defs.values()) if (d.day && lv >= d.minLevel && lv <= d.maxLevel) ok.add(d);
        if (ok.isEmpty()) return null;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < 6; i++) {
            double a = r.nextDouble() * Math.PI * 2, dist = 18 + r.nextDouble() * 16;
            Location l = p.getLocation().add(Math.cos(a) * dist, 0, Math.sin(a) * dist);
            org.bukkit.block.Block top = kr.rpgcraft.util.Locs.surface(w, l);
            if (top.isLiquid() || top.getType().name().contains("LEAVES")) continue;
            MobDef d = ok.get(r.nextInt(ok.size()));
            return spawn(d, top.getLocation().add(0.5, 1, 0.5), Math.max(d.minLevel, Math.min(d.maxLevel, lv + r.nextInt(3) - 1)));
        }
        return null;
    }

    public MobDef def(String id, boolean any) {
        return defs.get(id);
    }

    private double dmg(LivingEntity le) {
        MobManager.MobState s = plugin.mobs().peek(le);
        return s == null ? 10 : s.damage;
    }

    /** 범위 기술은 쓰기 전에 빨간 범위를 먼저 보여 준다 (피할 시간 0.6초) */
    private boolean telegraph(LivingEntity le, Player t, Ability a) {
        Color red = Color.fromRGB(0xFF2A2A);
        switch (a.type) {
            case "SLAM", "FROST", "POISON_CLOUD" -> kr.rpgcraft.util.Vfx.ring(le.getLocation(), Math.max(2, a.radius), red);
            case "LEAP", "METEOR" -> kr.rpgcraft.util.Vfx.ring(t.getLocation(), Math.max(2, a.radius), red);
            case "CHARGE" -> kr.rpgcraft.util.Vfx.beam(le.getLocation().add(0, 0.3, 0), t.getLocation().add(0, 0.3, 0), 1.2, red);
            default -> { return cast(le, t, a); }   // 사격·순간이동 등은 바로
        }
        le.getWorld().playSound(le.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (le.isValid() && !le.isDead() && t.isOnline()) cast(le, t, a); }, 12L);
        return true;
    }

    private boolean cast(LivingEntity le, Player t, Ability a) {
        World w = le.getWorld();
        Location ml = le.getLocation(), tl = t.getLocation();
        double dist = ml.distance(tl);
        switch (a.type) {
            case "LEAP" -> {
                if (dist < 3 || dist > 12 || !le.isOnGround()) return false;
                Vector v = tl.toVector().subtract(ml.toVector()).setY(0).normalize().multiply(Math.min(1.6, dist * 0.18)).setY(0.55);
                le.setVelocity(v);
                w.playSound(ml, Sound.ENTITY_RAVAGER_STEP, 1f, 1.4f);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!le.isValid()) return;
                    for (Player p : le.getWorld().getPlayers())
                        if (p.getLocation().distanceSquared(le.getLocation()) <= a.radius * a.radius) plugin.combat().mobSkillDamage(le, p, dmg(le) * a.power);
                    le.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, le.getLocation(), 1);
                }, 14L);
                return true;
            }
            case "BACKSTEP" -> {   // 뒤로 훌쩍 물러나며 거리 벌리기
                org.bukkit.util.Vector away = le.getLocation().toVector().subtract(t.getLocation().toVector()).setY(0);
                if (away.lengthSquared() < 0.01 || away.lengthSquared() > 64) return false;   // 가까울 때만
                le.setVelocity(away.normalize().multiply(1.1).setY(0.35));
                le.getWorld().spawnParticle(Particle.CLOUD, le.getLocation(), 6, 0.2, 0.05, 0.2, 0.02);
                le.getWorld().playSound(le.getLocation(), Sound.ENTITY_SKELETON_STEP, 1f, 1.5f);
                return true;
            }
            case "CHARGE" -> {
                if (dist < 4 || dist > 16) return false;
                Vector v = tl.toVector().subtract(ml.toVector()).setY(0).normalize().multiply(1.6).setY(0.15);
                le.setVelocity(v);
                w.playSound(ml, Sound.ENTITY_HORSE_ANGRY, 1f, 0.6f);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (le.isValid() && t.getLocation().distanceSquared(le.getLocation()) < 6) {
                        plugin.combat().mobSkillDamage(le, t, dmg(le) * a.power);
                        t.setVelocity(v.clone().multiply(0.6).setY(0.4));
                    }
                }, 8L);
                return true;
            }
            case "SLAM" -> {
                if (dist > a.radius + 1) return false;
                Fx.shockwave(plugin, ml, a.radius, Color.fromRGB(0x8B6B4A));
                w.playSound(ml, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.3f);
                for (Player p : w.getPlayers())
                    if (p.getLocation().distanceSquared(ml) <= a.radius * a.radius) {
                        plugin.combat().mobSkillDamage(le, p, dmg(le) * a.power);
                        p.setVelocity(p.getVelocity().setY(0.6));
                    }
                return true;
            }
            case "FROST" -> {
                if (dist > a.radius + 1) return false;
                Fx.circle(ml.clone().add(0, 0.2, 0), a.radius, 30, Color.fromRGB(0xA8E0FF), 1.4f);
                w.playSound(ml, Sound.BLOCK_GLASS_BREAK, 1f, 0.6f);
                for (Player p : w.getPlayers())
                    if (p.getLocation().distanceSquared(ml) <= a.radius * a.radius) {
                        plugin.combat().mobSkillDamage(le, p, dmg(le) * a.power);
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, 2));
                    }
                return true;
            }
            case "VOLLEY" -> {
                if (dist > 18 || !le.hasLineOfSight(t)) return false;
                for (int i = 0; i < a.amount; i++) {
                    int delay = i * 3;
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (!le.isValid() || !t.isValid()) return;
                        Vector dir = t.getEyeLocation().toVector().subtract(le.getEyeLocation().toVector()).normalize();
                        Arrow ar = le.launchProjectile(Arrow.class, dir.multiply(1.8));
                        ar.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                        ar.getPersistentDataContainer().set(Keys.POWER, PersistentDataType.DOUBLE, a.power);
                    }, delay);
                }
                return true;
            }
            case "FIREBALL" -> {
                if (dist > 20 || !le.hasLineOfSight(t)) return false;
                Vector dir = t.getEyeLocation().toVector().subtract(le.getEyeLocation().toVector()).normalize();
                SmallFireball fb = le.launchProjectile(SmallFireball.class, dir);
                fb.getPersistentDataContainer().set(Keys.POWER, PersistentDataType.DOUBLE, a.power);
                w.playSound(ml, Sound.ENTITY_BLAZE_SHOOT, 1f, 1f);
                return true;
            }
            case "BLINK" -> {
                if (dist < 4 || dist > 20) return false;
                Location behind = tl.clone().subtract(tl.getDirection().setY(0).normalize().multiply(2));
                behind.setY(tl.getY());
                if (!behind.getBlock().isPassable() || !behind.clone().add(0, 1, 0).getBlock().isPassable()) return false;
                w.spawnParticle(Particle.PORTAL, ml.clone().add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0.3);
                le.teleport(behind.setDirection(tl.toVector().subtract(behind.toVector())));
                w.playSound(behind, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
                return true;
            }
            case "HEAL" -> {
                MobManager.MobState s = plugin.mobs().peek(le);
                if (s == null || s.hp > s.maxHp * 0.5) return false;
                double hr = le instanceof org.bukkit.entity.Witch || (alive.get(le.getUniqueId()) != null && alive.get(le.getUniqueId()).name.contains("마녀")) ? 0.1 : 0.2;   // 마녀 회복 절반
                plugin.health().heal(le, s.maxHp * hr * a.power);
                w.spawnParticle(Particle.HEART, le.getLocation().add(0, le.getHeight() + 0.3, 0), 6, 0.4, 0.3, 0.4);
                w.playSound(ml, Sound.ENTITY_WITCH_DRINK, 1f, 1f);
                return true;
            }
            case "SUMMON" -> {
                if (dist > 16) return false;
                EntityType type = EntityType.ZOMBIE;
                try {
                    if (a.entity != null) type = EntityType.valueOf(a.entity.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ignored) {
                }
                MobManager.MobState s = plugin.mobs().peek(le);
                int lv = s == null ? 1 : Math.max(1, s.level - 3);
                for (int i = 0; i < a.amount; i++) {
                    Location at = ml.clone().add(ThreadLocalRandom.current().nextDouble(-2, 2), 0, ThreadLocalRandom.current().nextDouble(-2, 2));
                    Entity m = w.spawnEntity(at, type);
                    if (m instanceof LivingEntity ml2) {
                        MobManager mm = plugin.mobs();
                        mm.initCustom(ml2, lv, mm.hpFor(lv) * 0.4, mm.damageFor(lv) * 0.6, 0, mm.expFor(lv) / 4, 0, "소환된 " + MobManager.korean(type));
                        if (m instanceof Mob mob) mob.setTarget(t);
                    }
                    w.spawnParticle(Particle.SMOKE_LARGE, at.add(0, 0.5, 0), 10, 0.3, 0.5, 0.3, 0.02);
                }
                return true;
            }
            case "POISON_CLOUD" -> {
                if (dist > 10) return false;
                AreaEffectCloud c = (AreaEffectCloud) w.spawnEntity(tl, EntityType.AREA_EFFECT_CLOUD);
                c.setRadius((float) a.radius);
                c.setDuration(80);
                c.setColor(Color.fromRGB(0x5FE05A));
                c.addCustomEffect(new PotionEffect(PotionEffectType.POISON, 60, 0), true);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    public int killAll() {
        int n = 0;
        for (UUID id : new ArrayList<>(alive.keySet())) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) {
                e.remove();
                n++;
            }
        }
        alive.clear();
        return n;
    }

    public String biomeHint(MobDef d) {
        if (d.biomes.isEmpty()) return "어디서나";
        StringJoiner j = new StringJoiner(", ");
        for (String b : d.biomes) j.add(biomeKo(b));
        return j.toString();
    }

    private static String biomeKo(String b) {
        return switch (b) {
            case "DESERT" -> "사막";
            case "SNOW", "FROZEN", "ICE" -> "설원";
            case "SWAMP" -> "늪";
            case "FOREST" -> "숲";
            case "JUNGLE" -> "정글";
            case "PLAINS" -> "평원";
            case "OCEAN" -> "바다";
            case "MOUNTAIN", "PEAKS", "HILLS" -> "산악";
            case "BADLANDS" -> "악지";
            case "NETHER", "BASALT", "CRIMSON", "WARPED", "SOUL" -> "네더";
            case "END" -> "엔드";
            case "CAVE", "DRIPSTONE", "LUSH", "DEEP_DARK" -> "동굴";
            default -> b.toLowerCase(Locale.ROOT);
        };
    }
}
