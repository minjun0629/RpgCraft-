package kr.rpgcraft.mob;

import kr.rpgcraft.Keys;
import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.boss.BossDefinition;
import kr.rpgcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** 몬스터 레벨/체력/대미지/보상. 레벨은 월드 스폰에서의 거리로 결정된다. */
public class MobManager implements Listener {
    public static class MobState {
        public int level;
        public UUID lastHitBy;
        public long lastHitAt;
        public double maxHp, hp, damage, def;
        public long exp, money;
        public String bossId, minionOf, baseName;
        public boolean awakened;
        public long shownUntil;
        public final Map<UUID, Double> contrib = new HashMap<>();
    }

    private static final Map<EntityType, String> KOREAN = new EnumMap<>(EntityType.class);

    static {
        String[][] n = {{"ZOMBIE", "좀비"}, {"SKELETON", "스켈레톤"}, {"SPIDER", "거미"}, {"CAVE_SPIDER", "동굴 거미"}, {"CREEPER", "크리퍼"},
                {"ENDERMAN", "엔더맨"}, {"WITCH", "마녀"}, {"SLIME", "슬라임"}, {"HUSK", "허스크"}, {"DROWNED", "드라운드"}, {"STRAY", "스트레이"},
                {"PHANTOM", "팬텀"}, {"PILLAGER", "약탈자"}, {"VINDICATOR", "변명자"}, {"EVOKER", "소환사"}, {"VEX", "벡스"}, {"BLAZE", "블레이즈"},
                {"WITHER_SKELETON", "위더 스켈레톤"}, {"PIGLIN", "피글린"}, {"PIGLIN_BRUTE", "난폭한 피글린"}, {"ZOMBIFIED_PIGLIN", "좀비 피글린"},
                {"GHAST", "가스트"}, {"MAGMA_CUBE", "마그마 큐브"}, {"GUARDIAN", "가디언"}, {"ELDER_GUARDIAN", "엘더 가디언"}, {"RAVAGER", "파괴수"},
                {"SILVERFISH", "좀벌레"}, {"ENDERMITE", "엔더마이트"}, {"SHULKER", "셜커"}, {"HOGLIN", "호글린"}, {"ZOGLIN", "조글린"},
                {"ZOMBIE_VILLAGER", "좀비 주민"}, {"WARDEN", "워든"}, {"IRON_GOLEM", "철 골렘"}, {"WOLF", "늑대"}, {"COW", "소"},
                {"PIG", "돼지"}, {"SHEEP", "양"}, {"CHICKEN", "닭"}, {"HORSE", "말"}};
        for (String[] a : n) {
            try {
                KOREAN.put(EntityType.valueOf(a[0]), a[1]);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private final RpgCraft plugin;
    private final Map<UUID, MobState> states = new HashMap<>();

    public MobManager(RpgCraft plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, () -> states.keySet().removeIf(id -> {
            Entity e = Bukkit.getEntity(id);
            return e == null || !e.isValid();
        }), 1200L, 1200L);
        // 최근 피격된 몬스터만 체력바 표시
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            for (Map.Entry<UUID, MobState> en : states.entrySet()) {
                MobState s = en.getValue();
                if (s.bossId != null || s.shownUntil == 0 || s.shownUntil > now) continue;
                s.shownUntil = 0;
                Entity e = Bukkit.getEntity(en.getKey());
                if (e != null) e.setCustomNameVisible(false);
            }
        }, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::revealNearby, 10L, 10L);
    }

    /** 때리지 않아도 이름표(레벨 · 이름 · 체력바)를 보여줌: 플레이어 근처에 있거나 플레이어가 바라보는 몬스터 */
    private void revealNearby() {
        double near = plugin.getConfig().getDouble("mobs.name-display.near", 16);
        double look = plugin.getConfig().getDouble("mobs.name-display.look", 32);
        if (near <= 0 && look <= 0) return;
        long until = System.currentTimeMillis() + 1500;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (near > 0) {
                for (Entity en : p.getNearbyEntities(near, near * 0.5, near)) reveal(en, until);
            }
            if (look > 0) {
                var hit = p.getWorld().rayTraceEntities(p.getEyeLocation(), p.getEyeLocation().getDirection(), look, 0.4,
                        en -> en != p && en instanceof LivingEntity && !(en instanceof Player));
                if (hit != null && hit.getHitEntity() != null) reveal(hit.getHitEntity(), until);
            }
        }
    }

    private void reveal(Entity en, long until) {
        if (!(en instanceof LivingEntity le) || en instanceof Player || !le.isValid()) return;
        MobState s = states.get(le.getUniqueId());
        if (s == null) {
            if (!(le instanceof Enemy) || le.getPersistentDataContainer().has(Keys.INDICATOR, PersistentDataType.BYTE)) return;
            List<String> worlds = plugin.getConfig().getStringList("mobs.enabled-worlds");
            if (!worlds.isEmpty() && !worlds.contains(le.getWorld().getName())) return;
            s = init(le);   // 서버 재시작 · 청크 로드로 아직 추적되지 않던 몬스터
        }
        if (s.bossId != null) return;
        if (!overheadName()) return;   // v5.10.46 머리 위 이름표 끔
        if (s.shownUntil == 0 && le.isCustomNameVisible()) return;   // 다른 기능이 항상 보이게 해 둔 이름표 (웨이브 몬스터 · 허수아비)
        boolean was = s.shownUntil > System.currentTimeMillis();
        s.shownUntil = Math.max(s.shownUntil, until);
        if (!was || !le.isCustomNameVisible()) le.setCustomNameVisible(true);
    }

    public static String korean(EntityType t) {
        String k = KOREAN.get(t);
        if (k != null) return k;
        return switch (t.name()) {  // 표시명 없는 종류도 영어 그대로 보이지 않게
            case "COW" -> "소"; case "PIG" -> "돼지"; case "RABBIT" -> "토끼"; case "GOAT" -> "산양"; case "LLAMA", "TRADER_LLAMA" -> "라마";
            case "MOOSHROOM" -> "무시룸"; case "DONKEY" -> "당나귀"; case "MULE" -> "노새"; case "FOX" -> "여우"; case "PANDA" -> "판다";
            case "POLAR_BEAR" -> "북극곰"; case "CAT", "OCELOT" -> "고양이"; case "PARROT" -> "앵무새"; case "TURTLE" -> "거북"; case "BEE" -> "벌";
            case "FROG" -> "개구리"; case "CAMEL" -> "낙타"; case "SNIFFER" -> "스니퍼"; case "VEX" -> "벡스"; case "BAT" -> "박쥐";
            case "SQUID", "GLOW_SQUID" -> "오징어"; case "DOLPHIN" -> "돌고래"; case "AXOLOTL" -> "아홀로틀"; case "STRIDER" -> "스트라이더";
            case "VILLAGER" -> "주민"; case "WANDERING_TRADER" -> "떠돌이 상인"; case "SNOWMAN" -> "눈 골렘"; case "ALLAY" -> "알레이";
            case "ILLUSIONER" -> "환술사"; case "GIANT" -> "거인"; case "ENDER_DRAGON" -> "엔더 드래곤"; case "WITHER" -> "위더";
            default -> "몬스터";
        };
    }

    public boolean tracked(Entity e) {
        return states.containsKey(e.getUniqueId());
    }

    public MobState peek(Entity e) {
        return states.get(e.getUniqueId());
    }

    public MobState state(LivingEntity e) {
        MobState s = states.get(e.getUniqueId());
        return s != null ? s : init(e);
    }

    public MobState init(LivingEntity e) {
        String bossId = e.getPersistentDataContainer().get(Keys.BOSS, PersistentDataType.STRING);
        if (bossId != null) {
            BossDefinition def = plugin.bosses().def(bossId);
            if (def != null) return plugin.bosses().initState(e, def);
        }
        Integer lv = e.getPersistentDataContainer().get(Keys.LEVEL, PersistentDataType.INTEGER);
        int level = lv != null ? lv : computeLevel(e.getLocation());
        e.getPersistentDataContainer().set(Keys.LEVEL, PersistentDataType.INTEGER, level);
        MobState s = new MobState();
        s.level = level;
        s.maxHp = hpFor(level);
        s.damage = damageFor(level);
        s.def = Math.min(40, level * plugin.getConfig().getDouble("mobs.defense-per-level", 0.2));
        s.exp = expFor(level);
        s.money = (long) (level * plugin.getConfig().getDouble("mobs.money-per-level", 100));
        s.minionOf = e.getPersistentDataContainer().get(Keys.MINION, PersistentDataType.STRING);
        s.baseName = korean(e.getType());
        var attr = e.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double ratio = attr == null ? 1 : e.getHealth() / attr.getValue();
        s.hp = s.maxHp * Math.max(0.05, Math.min(1, ratio));
        states.put(e.getUniqueId(), s);
        updateName(e, s);
        return s;
    }

    /** 보스/소환수용 수동 초기화 */
    public MobState initCustom(LivingEntity e, int level, double hp, double damage, double def, long exp, long money, String name) {
        MobState s = new MobState();
        s.level = level;
        s.maxHp = hp;
        s.hp = hp;
        s.damage = damage;
        s.def = def;
        s.exp = exp;
        s.money = money;
        s.baseName = name;
        s.minionOf = e.getPersistentDataContainer().get(Keys.MINION, PersistentDataType.STRING);
        e.getPersistentDataContainer().set(Keys.LEVEL, PersistentDataType.INTEGER, level);
        states.put(e.getUniqueId(), s);
        updateName(e, s);
        return s;
    }

    public void updateName(LivingEntity e, MobState s) {
        if (e instanceof Player) return;
        if (s.bossId != null) {
            e.setCustomName(Text.c("&4&l[보스] &c" + s.baseName));   // v5.10.45 체력은 오른쪽 위 대상 정보에
            e.setCustomNameVisible(true);
            return;
        }
        double ratio = s.maxHp <= 0 ? 0 : Math.max(0, Math.min(1, s.hp / s.maxHp));
        int full = (int) Math.ceil(ratio * 10);
        String col = ratio > 0.5 ? "&a" : ratio > 0.2 ? "&e" : "&c";
        String lvCol = s.minionOf != null ? "&5" : s.level >= 90 ? "&4" : s.level >= 60 ? "&c" : s.level >= 30 ? "&6" : "&7";
        boolean overheadBar = plugin.getConfig().getBoolean("mobs.overhead-hp-bar", false);   // v5.10.45 4R 처럼 체력은 오른쪽 위 대상 정보에 (머리 위엔 이름만)
        e.setCustomName(Text.c(lvCol + "Lv." + s.level + " &f" + s.baseName + (overheadBar ? " " + col + "▌".repeat(full) + "&8" + "▌".repeat(10 - full) : "")));
        e.setCustomNameVisible(overheadName() && s.shownUntil > System.currentTimeMillis());
    }

    /** v5.10.46 머리 위 이름표 (기본 끔: 이름 · 레벨 · 체력은 오른쪽 위 대상 정보에) */
    private boolean overheadName() {
        return plugin.getConfig().getBoolean("mobs.overhead-name", false);
    }

    public int computeLevel(Location l) {
        FileConfiguration c = plugin.getConfig();
        String w = l.getWorld().getName();
        ConfigurationSection o = c.getConfigurationSection("mobs.world-overrides." + w);
        int base = o != null && o.contains("base") ? o.getInt("base") : c.getInt("mobs.level.base", 1);
        double per = o != null && o.contains("blocks-per-level") ? o.getDouble("blocks-per-level") : c.getDouble("mobs.level.blocks-per-level", 40);
        int max = c.getInt("mobs.level.max", 300);
        int spread = c.getInt("mobs.level.random-spread", 2);
        Location spawn = l.getWorld().getSpawnLocation();
        double dist = Math.hypot(l.getX() - spawn.getX(), l.getZ() - spawn.getZ());
        // v5.10.57 스폰 왕국을 지었으면 성 밖(왕국 부지 끝)부터 Lv.1 → 멀어질수록 올라감 (네모 부지 바깥까지의 거리)
        if (w.equals(c.getString("kingdom.world", "")) && c.getBoolean("kingdom.level-from-wall", true)) {
            int half = c.getInt("kingdom.half", 500);
            double dx = Math.max(0, Math.abs(l.getX() - c.getDouble("kingdom.x")) - half);
            double dz = Math.max(0, Math.abs(l.getZ() - c.getDouble("kingdom.z")) - half);
            dist = Math.hypot(dx, dz);
        }
        int lv = base + (int) (dist / Math.max(1, per)) + ThreadLocalRandom.current().nextInt(-spread, spread + 1);
        return Math.max(1, Math.min(max, lv));
    }

    public double hpFor(int L) {
        FileConfiguration c = plugin.getConfig();
        double high = 1 + Math.max(0, L - 100) / c.getDouble("mobs.hp.high-level-step", 60);   // Lv.100 이후 추가 성장
        return c.getDouble("mobs.hp.base", 60) * Math.pow(1 + c.getDouble("mobs.hp.growth", 0.12) * L, 2) * high;
    }

    public double damageFor(int L) {
        FileConfiguration c = plugin.getConfig();
        double high = (1 + Math.max(0, L - 100) / c.getDouble("mobs.damage.high-level-step", 100))
                * c.getDouble("mobs.damage-mult", 1.2) * (L >= 15 ? c.getDouble("mobs.damage-mult-lv15", 1.15) : 1);   // 몬스터 피해 ×1.2, Lv.15+ 추가
        return c.getDouble("mobs.damage.base", 20) * Math.pow(1 + c.getDouble("mobs.damage.growth", 0.15) * L, 1.5) * high;
    }

    public long expFor(int L) {
        FileConfiguration c = plugin.getConfig();
        return (long) (c.getDouble("mobs.exp.base", 8) * Math.pow(1 + c.getDouble("mobs.exp.growth", 0.2) * L, 1.5));
    }

    public void forget(Entity e) {
        states.remove(e.getUniqueId());
    }

    // ------------------------------------------------------------------ events
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        if (e.getEntity() instanceof Zombie z && !z.isAdult() && plugin.getConfig().getBoolean("mobs.no-baby-zombies", true)) z.setAdult();
        if (e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM) return;
        if (!(e.getEntity() instanceof Enemy)) return;
        List<String> worlds = plugin.getConfig().getStringList("mobs.enabled-worlds");
        if (!worlds.isEmpty() && !worlds.contains(e.getLocation().getWorld().getName())) return;
        init(e.getEntity());
        if (e.getEntity() instanceof Creeper) { // 크리퍼: 체력 낮춤 (잡을 만하게)
            MobState s = peek(e.getEntity());
            if (s != null) {
                s.maxHp *= plugin.getConfig().getDouble("mobs.creeper-hp-mult", 0.35);
                s.hp = s.maxHp;
                s.exp *= plugin.getConfig().getDouble("mobs.creeper-exp-mult", 1.5);
                updateName(e.getEntity(), s);
            }
        }
    }

    /** 햇빛에 타는 것 방지 (좀비·스켈레톤·팬텀 등). 불·용암·불붙은 화살 등 다른 원인은 그대로 */
    @EventHandler(ignoreCancelled = true)
    public void onSunBurn(org.bukkit.event.entity.EntityCombustEvent e) {
        if (e.getEntity() instanceof Player || plugin.getConfig().getBoolean("mobs.sun-burn", false)) return;
        if (e instanceof org.bukkit.event.entity.EntityCombustByEntityEvent || e instanceof org.bukkit.event.entity.EntityCombustByBlockEvent) return;
        org.bukkit.block.Block b = e.getEntity().getLocation().getBlock();
        if (b.getType() == org.bukkit.Material.FIRE || b.getType() == org.bukkit.Material.LAVA || b.getType() == org.bukkit.Material.SOUL_FIRE) return;
        e.setCancelled(true);
    }

    /** 몬스터 처치 돈: 최소~최대 배율 사이 무작위 */
    public long moneyRoll(double base) {
        double lo = plugin.getConfig().getDouble("mobs.money-random-min", 0.6), hi = plugin.getConfig().getDouble("mobs.money-random-max", 1.5);
        double mult = plugin.getConfig().getDouble("economy.mob-money-mult", 0.3);
        return Math.max(1, Math.round(base * mult * (lo + java.util.concurrent.ThreadLocalRandom.current().nextDouble() * Math.max(0, hi - lo))));
    }

    /** 크리퍼 자폭: 마지막으로 때린 플레이어에게 처치 보상 */
    private final Map<UUID, UUID> creeperHitter = new HashMap<>();

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCreeperHit(org.bukkit.event.entity.EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Creeper c)) return;
        Entity d = e.getDamager();
        if (d instanceof Projectile pr && pr.getShooter() instanceof Player sp) d = sp;
        if (d instanceof Player p) creeperHitter.put(c.getUniqueId(), p.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCreeperExplode(org.bukkit.event.entity.EntityExplodeEvent e) {
        if (!(e.getEntity() instanceof Creeper c)) return;
        UUID pid = creeperHitter.remove(c.getUniqueId());
        MobState s = states.remove(c.getUniqueId());
        Player p = pid == null ? null : Bukkit.getPlayer(pid);
        if (p == null || s == null || !p.getWorld().equals(c.getWorld()) || p.getLocation().distanceSquared(c.getLocation()) > 32 * 32) return;
        double half = plugin.getConfig().getDouble("mobs.creeper-reward-mult", 0.5);
        plugin.levels().addExp(p, s.exp * half);
        plugin.economy().give(p, (long) (moneyRoll(s.money) * plugin.jobs().moneyMult(p) * half));
        if (java.util.concurrent.ThreadLocalRandom.current().nextDouble() < half) {
            ItemStack loot = plugin.items().create("loot_dust", 1);
            if (loot != null) c.getWorld().dropItemNaturally(c.getLocation(), loot);
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        LivingEntity ent = e.getEntity();
        if (ent instanceof Player) return;
        MobState s = states.remove(ent.getUniqueId());
        if (s == null) return;
        e.setDroppedExp(0);
        if (s.bossId != null) {
            plugin.bosses().onBossDeath(ent, s, e);
            return;
        }
        boolean enemy = (ent instanceof Enemy) || ent instanceof org.bukkit.entity.IronGolem || plugin.customMobs().of(ent) != null;
        if (ent instanceof Animals && ent.getKiller() != null) { // 야생 동물: 적은 경험치·돈
            double m = plugin.getConfig().getDouble("wild-animals.reward-mult", 0.4);
            plugin.party().giveKillReward(ent.getKiller(), s, s.exp * m, (long) (moneyRoll(s.money * m) * plugin.jobs().moneyMult(ent.getKiller())));
            return;
        }
        if (enemy && plugin.getConfig().getBoolean("mobs.clear-vanilla-drops", true)) e.getDrops().clear();
        Player killer = ent.getKiller();
        if (killer == null && s.lastHitBy != null && System.currentTimeMillis() - s.lastHitAt < 15_000) killer = Bukkit.getPlayer(s.lastHitBy);  // 낙사·불 등
        if (killer == null || (!enemy && s.minionOf == null)) return;
        kr.rpgcraft.data.PlayerData kd0 = plugin.data().get(killer);
        if (s.level > kd0.level && kd0.hp <= plugin.health().max(killer) * 0.05) kd0.counters.merge("hs_grit", 1.0, Double::sum);   // 투지 누적
        int under = plugin.data().get(killer).level - s.level - plugin.getConfig().getInt("mobs.overlevel-free", 20);
        double expMul = under > 0 ? Math.max(0.1, 1 - 0.03 * under) : 1;   // 나보다 20레벨 넘게 낮은 몬스터는 경험치 감소
        if (plugin.tiers() == null || plugin.tiers().tier(ent) == kr.rpgcraft.mob.MonsterTierManager.Tier.NORMAL)
            expMul *= plugin.getConfig().getDouble("mobs.normal-exp-mult", 0.75);   // v5.10.34 일반 몬스터 경험치 75%
        plugin.party().giveKillReward(killer, s, s.exp * expMul, (long) (moneyRoll(s.money) * plugin.jobs().moneyMult(killer)));
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double cc = plugin.getConfig().getDouble("mobs.crystal-chance", 0.06) * (s.level >= 90 ? 0.25 : s.level >= 60 ? 0.4 : s.level >= 30 ? 0.6 : 1.0);   // 고등급 결정일수록 드물게
        if (r.nextDouble() < cc) {
            String id = s.level >= 90 ? "crystal_top" : s.level >= 60 ? "crystal_high" : s.level >= 30 ? "crystal_mid" : "crystal_low";
            e.getDrops().add(plugin.items().create(id, 1));
        }
        if (s.minionOf != null) {
            for (ItemStack it : plugin.bosses().rollMinionDrops(s.minionOf)) e.getDrops().add(it);
        }
    }
}
