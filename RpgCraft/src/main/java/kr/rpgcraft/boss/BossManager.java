package kr.rpgcraft.boss;

import kr.rpgcraft.Keys;
import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.mob.MobManager;
import kr.rpgcraft.util.Text;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** 보스 / 월드보스 레이드 (사막의 악몽, 시포니아, 카인 등). 정의는 bosses.yml */
public class BossManager {
    private static class Active {
        LivingEntity entity;
        BossDefinition def;
        BossBar bar;                // 보스 주변 플레이어 명단 (v5.10.49: 화면에는 안 보임 — 아래 두 바가 보임)
        BossBar plainBar, panelBar; // v5.10.49 팩 없는 사람: 예전 보스바 · 팩 있는 사람: 오른쪽 위 보스 전용 판
        final Map<Integer, Long> next = new HashMap<>();
        long nextSignature;
        final long born = System.currentTimeMillis();
        int phase = 1;              // 1: 평상 · 2: 분노(60%) · 3: 광폭(30%)
        boolean seenAwake;
        double aura;                // 주변 기운 회전 각도
        Location home;              // 등장 위치 — 여기서 너무 멀어지면 되돌아감 (v5.4.24)
        Location lastAura;          // 직전 기운 위치 (움직이는 중인지 판단)
        long staggerUntil;          // v5.6.0: 기술을 쓴 직후 경직 (이때가 공격 기회)
        long ultUntil, nextUlt;     // v5.7.0: 궁극기 진행 중 · 다음 주기 궁극기
        final Set<Integer> ultDone = new HashSet<>();   // 이미 쓴 체력 구간 궁극기 (%)
        long nextAny;               // v5.9.7: 다음 기술을 쓸 수 있는 때 (기술끼리 겹쳐 난사하지 않게 — 예고를 보고 피할 틈)
    }

    private final RpgCraft plugin;
    private final Map<String, BossDefinition> defs = new LinkedHashMap<>();
    private final Map<UUID, Active> active = new HashMap<>();

    public BossManager(RpgCraft plugin) {
        this.plugin = plugin;
        load();
        BossFx.init(plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
    }

    public void load() {
        defs.clear();
        File f = new File(plugin.getDataFolder(), "bosses.yml");
        try (var in = plugin.getResource("bosses.yml")) {   // 새 버전 보스를 기존 파일에 채워 넣기
            if (in != null && f.exists()) {
                var cur = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f);
                var def = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
                boolean added = false;
                for (String k : def.getKeys(false)) if (!cur.contains(k)) { cur.set(k, def.get(k)); added = true; }
                if (cur.getDouble("vengeful_spirit.hp", 0) == 30000000) { cur.set("vengeful_spirit", def.get("vengeful_spirit")); added = true; }   // 원혼 약화
                for (String k : def.getKeys(false)) {   // v5.5.0: 필드 보스 새 패턴 (돌진 · 파동 · 분출 · 십자 · 서리 장판 · 포효) — 아직 하나도 없으면 기본 기술 목록으로
                    if (!k.startsWith("field_") || !cur.contains(k + ".skills")) continue;
                    String cs = String.valueOf(cur.get(k + ".skills"));
                    if (!cs.matches("(?s).*(CHARGE|NOVA|ERUPTION|CROSS|FROST_FIELD|ROAR).*")) { cur.set(k + ".skills", def.get(k + ".skills")); added = true; }
                }
                for (String k : def.getKeys(false)) {   // v5.6.0: 보스 컨셉 패턴 개편 — 기본 파일의 patterns 번호가 더 크면 기술 목록을 새로 받음
                    int want = def.getInt(k + ".patterns", 0);
                    if (want > 0 && cur.getInt(k + ".patterns", 0) < want) { cur.set(k + ".skills", def.get(k + ".skills")); cur.set(k + ".patterns", want); added = true; }
                }
                if (added) cur.save(f);
            }
        } catch (Exception ignored) {
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        for (String id : y.getKeys(false)) {
            try {
                defs.put(id, BossDefinition.load(id, y.getConfigurationSection(id)));
            } catch (Exception ex) {
                plugin.getLogger().warning("보스 로드 실패 " + id + ": " + ex.getMessage());
            }
        }
    }

    /** 월드 스폰에서 bosses.spawn-safe-radius(기본 300칸) 안인지 — 이 안에는 보스가 자연히 나오지 않음 (v5.4.27) */
    public boolean nearSpawn(Location l) {
        if (l == null || l.getWorld() == null) return false;
        double safe = plugin.getConfig().getDouble("bosses.spawn-safe-radius", 300);
        if (safe <= 0) return false;
        Location sp = l.getWorld().getSpawnLocation();
        return Math.hypot(l.getX() - sp.getX(), l.getZ() - sp.getZ()) < safe;
    }

    public BossDefinition def(String id) {
        return defs.get(id);
    }

    public Set<String> ids() {
        return defs.keySet();
    }

    public LivingEntity spawn(String id, Location loc) {
        BossDefinition d = defs.get(id);
        if (d == null) return null;
        Entity raw = loc.getWorld().spawnEntity(loc, d.type);
        if (!(raw instanceof LivingEntity e)) {
            raw.remove();
            return null;
        }
        e.getPersistentDataContainer().set(Keys.BOSS, PersistentDataType.STRING, id);
        e.setRemoveWhenFarAway(false);
        if (e instanceof org.bukkit.entity.Hoglin h) h.setImmuneToZombification(true);   // 오버월드에서 15초 뒤 조글린으로 변해 사라지던 문제
        if (e instanceof org.bukkit.entity.PiglinAbstract pa) pa.setImmuneToZombification(true);
        e.setPersistent(true);
        var kb = e.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE);
        if (kb != null) kb.setBaseValue(1.0);
        initState(e, d);
        if (plugin.bossModels() != null) plugin.bossModels().ensure(e, id);
        kr.rpgcraft.util.Text.announce(Text.PREFIX + Text.c("&c&l" + d.name + "&f(이)가 &e" + loc.getWorld().getName() + " "
                + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + "&f에 나타났습니다!"));
        for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.6f, 1f);
        intro(e, d);
        return e;
    }

    /**
     * 보스 처치 경험치 (참가자 전체 합계, 혼자면 전부).
     * bosses.yml 의 exp 는 레벨 수십~수백 개 분량이라 너무 많아서, "그 보스 레벨에서 레벨업에 필요한 경험치 × 배율" 로 계산 (v5.4.3).
     * 필드 보스 1.5 · 일반 보스 2.5 · 월드보스 4 (레벨 분량). bosses.yml 값이 더 작으면 그 값.
     */
    public double bossExp(BossDefinition d) {
        var c = plugin.getConfig();
        double lv = d.field ? c.getDouble("field-bosses.exp-levels", 1.5)
                : kr.rpgcraft.world.WorldBossManager.isWorldBoss(d.id) ? c.getDouble("bosses.world-exp-levels", 4)
                : c.getDouble("bosses.exp-levels", 2.5);
        if (lv <= 0) return d.exp;   // 0 이하로 두면 bosses.yml 값 그대로
        return Math.min(d.exp, plugin.levels().need(d.level) * lv);
    }

    public MobManager.MobState initState(LivingEntity e, BossDefinition d) {
        MobManager.MobState s = plugin.mobs().initCustom(e, d.level, d.hp, d.damage * plugin.getConfig().getDouble("bosses.damage-mult", 1.15), d.defense, (long) bossExp(d), d.money, d.name);
        s.bossId = d.id;
        plugin.mobs().updateName(e, s);
        if (!active.containsKey(e.getUniqueId())) {
            Active a = new Active();
            a.entity = e;
            a.def = d;
            a.home = e.getLocation().clone();
            var fr = e.getAttribute(Attribute.GENERIC_FOLLOW_RANGE);   // 멀리 있는 사람까지 알아채지 않게
            if (fr != null) fr.setBaseValue(plugin.getConfig().getDouble("bosses.chase-radius", 48));
            // WHITE 는 나침반 문구 전용 투명 바(리소스팩)라서 보스는 파란 바로
            a.bar = Bukkit.createBossBar(Text.c("&c" + d.name), d.color == org.bukkit.boss.BarColor.WHITE ? org.bukkit.boss.BarColor.BLUE : d.color, BarStyle.SEGMENTED_10);
            a.bar.setVisible(false);
            a.plainBar = Bukkit.createBossBar(Text.c("&c" + d.name), d.color == org.bukkit.boss.BarColor.WHITE ? org.bukkit.boss.BarColor.BLUE : d.color, BarStyle.SEGMENTED_10);
            a.panelBar = Bukkit.createBossBar("", org.bukkit.boss.BarColor.WHITE, BarStyle.SOLID);   // 흰 바 = 리소스팩에서 투명
            long now = System.currentTimeMillis();
            for (int i = 0; i < d.skills.size(); i++) a.next.put(i, now + d.skills.get(i).interval * 1000L);
            active.put(e.getUniqueId(), a);
        }
        return s;
    }

    /** 보스 주변(40칸) 플레이어 수 */
    public int crowd(LivingEntity boss) {
        int n = 0;
        for (Player p : boss.getWorld().getPlayers()) if (p.getLocation().distanceSquared(boss.getLocation()) < 40 * 40 && !p.isDead()) n++;
        return Math.max(1, n);
    }

    public boolean tryAwaken(LivingEntity e, MobManager.MobState s) {
        BossDefinition d = defs.get(s.bossId);
        if (d == null || !d.awaken || s.awakened) return false;
        s.awakened = true;
        s.maxHp *= d.awakenMultiplier;
        s.hp = s.maxHp;
        s.damage *= 1.3;
        s.baseName = "각성 " + d.name;
        plugin.mobs().updateName(e, s);
        e.getWorld().spawnParticle(Particle.EXPLOSION_HUGE, e.getLocation(), 3);
        kr.rpgcraft.util.Fx.shockwave(plugin, e.getLocation(), 10, org.bukkit.Color.fromRGB(0x8B0000));
        kr.rpgcraft.util.Fx.helix(plugin, e, 4, 1.6, 40, org.bukkit.Color.fromRGB(0x8B0000), org.bukkit.Color.BLACK);
        e.getWorld().playSound(e.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 2f, 0.6f);
        kr.rpgcraft.util.Text.announce(Text.PREFIX + Text.c("&4&l" + d.name + "&c이(가) 각성했습니다! &7(체력 " + Text.num(s.maxHp) + ")"));
        return true;
    }

    private void tick() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Active>> it = active.entrySet().iterator();
        while (it.hasNext()) {
            Active a = it.next().getValue();
            if (a.entity == null || a.entity.isDead() || !a.entity.isValid()) {
                clearBars(a);
                it.remove();
                continue;
            }
            MobManager.MobState s = plugin.mobs().state(a.entity);
            double frac = Math.max(0, Math.min(1, s.hp / s.maxHp));
            phase(a, s, frac);
            a.bar.setProgress(frac);
            auraTick(a);
            if (now >= a.staggerUntil) holdAltitude(a.entity);
            Location bl = a.entity.getLocation();
            Set<Player> near = new HashSet<>();
            for (Player p : bl.getWorld().getPlayers()) if (p.getLocation().distanceSquared(bl) < 64 * 64) near.add(p);
            for (Player p : new ArrayList<>(a.bar.getPlayers())) if (!near.contains(p)) a.bar.removePlayer(p);
            for (Player p : near) a.bar.addPlayer(p);
            a.bar.setVisible(false);
            showBars(a, s, frac, near);
            if (now - a.born > plugin.getConfig().getLong("bosses.lifetime-minutes", 30) * 60_000) {   // 등장 30분 뒤 사라짐 (주변에 아무도 없어도)
                clearBars(a);
                a.entity.remove();
                Text.announce(Text.PREFIX + Text.c("&7" + a.def.name + "&7이(가) 사라졌습니다..."));
                continue;
            }
            // 끝없이 쫓아가지 않게 (v5.4.24): 등장 위치에서 leash 칸 넘게 벗어나면 제자리로 돌아가고,
            // chase 칸보다 멀어지거나 등장 위치에서 너무 먼 플레이어는 포기
            double leash = plugin.getConfig().getDouble("bosses.leash-radius", 0), chase = plugin.getConfig().getDouble("bosses.chase-radius", 48);   // v5.5.0: 48칸 추격 · 제자리 복귀 끔
            boolean homeHere = a.home != null && leash > 0 && a.home.getWorld() == bl.getWorld();
            if (homeHere && bl.distanceSquared(a.home) > leash * leash) {
                if (a.entity instanceof Mob mob) mob.setTarget(null);
                bl.getWorld().spawnParticle(Particle.SMOKE_LARGE, bl.clone().add(0, 1, 0), 30, 0.8, 1, 0.8, 0.03);
                if (plugin.bossModels() != null) plugin.bossModels().teleportBoss(a.entity, a.home); else a.entity.teleport(a.home);
                a.entity.getWorld().spawnParticle(Particle.PORTAL, a.home.clone().add(0, 1, 0), 60, 1, 1.5, 1, 0.3);
                a.entity.getWorld().playSound(a.home, Sound.ENTITY_ENDERMAN_TELEPORT, 1.5f, 0.6f);
                continue;
            }
            if (a.entity instanceof Mob mob && mob.getTarget() instanceof Player tp
                    && (tp.getWorld() != bl.getWorld() || tp.getLocation().distanceSquared(bl) > chase * chase
                        || homeHere && tp.getLocation().distanceSquared(a.home) > (leash + 6) * (leash + 6))) mob.setTarget(null);
            Player target = nearest(a.entity, chase);
            if (target == null) continue;
            if (homeHere && target.getLocation().distanceSquared(a.home) > (leash + 6) * (leash + 6)) continue;   // 등장 위치에서 너무 먼 사람은 노리지 않음
            if (a.entity instanceof Mob mob && (mob.getTarget() == null || !(mob.getTarget() instanceof Player))) mob.setTarget(target);
            if (now - a.born > plugin.getConfig().getLong("bosses.lifetime-minutes", 30) * 60_000) {   // 등장 30분 뒤 사라짐
                clearBars(a);
                a.entity.getWorld().spawnParticle(Particle.SMOKE_LARGE, a.entity.getLocation().add(0, 1, 0), 40, 1, 1, 1, 0.05);
                a.entity.remove();
                Text.announce(Text.PREFIX + Text.c("&7" + a.def.name + "&7이(가) 사라졌습니다..."));
                continue;
            }
            if (a.def.level >= plugin.getConfig().getInt("bosses.signature-min-level", 80) && now >= a.nextSignature && now >= a.ultUntil && now >= a.nextAny) {
                boolean aw = s.awakened;
                a.nextSignature = now + (aw ? 11_000 : 17_000) + ThreadLocalRandom.current().nextInt(4000);
                if (a.nextSignature > 0 && a.next.size() > 0) {
                    signature(a, target, s);
                    a.nextAny = now + castGap(a) + 2500;   // 대표 기술은 크니까 뒤에 조금 더 쉼
                }
            }
            if (now >= a.ultUntil) ultimateCheck(a, s, frac, target, now);   // v5.7.0 고레벨 보스 궁극기 (체력 구간 · 주기)
            if (now < a.staggerUntil || now < a.ultUntil) continue;   // 경직 · 궁극기 중에는 다른 기술을 쓰지 않음
            if (now < a.nextAny) continue;   // v5.9.7: 기술 사이 공통 간격 (여러 기술이 동시에 준비돼도 한 번에 하나씩)
            for (int i = 0; i < a.def.skills.size(); i++) {
                if (now < a.next.getOrDefault(i, 0L)) continue;
                BossDefinition.Skill k = a.def.skills.get(i);
                int crowd = Math.max(1, near.size());
                double faster = (1 + plugin.getConfig().getDouble("bosses.crowd-skill-speed", 0.25) * (crowd - 1))   // 여럿이면 기술 간격 단축
                        * (a.phase == 3 ? 1.25 : a.phase == 2 ? 1.1 : 1.0)                                           // 분노 · 광폭 단계는 더 자주 (v5.9.7: 1.45/1.2 → 1.25/1.1)
                        * TIER_SPEED[tier(a) - 1];                                                                   // v5.7.0: 레벨이 높은 보스일수록 더 자주
                a.next.put(i, now + (long) (k.interval * 1000L / faster));
                if (crowd >= 3 && ThreadLocalRandom.current().nextDouble() < 0.35) {   // 3명 이상이면 다른 사람에게도 같은 기술
                    Player second = null;
                    for (Player q : near) if (!q.equals(target) && q.getLocation().distanceSquared(bl) < 30 * 30) { second = q; break; }
                    if (second != null) {
                        Player s2 = second;
                        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (a.entity.isValid()) cast(a, k, s2, s); }, 18L);
                    }
                }
                // 예고(차오르는 위험 지역 · 표식)는 각 기술이 직접 그린다
                Player tg = target;
                Bukkit.getScheduler().runTaskLater(plugin, () -> { if (a.entity.isValid() && !a.entity.isDead()) cast(a, k, tg, s); }, 8L);   // 예고 짧게 (명중률↑)
                a.nextAny = now + 400 + windup(k, s.awakened) * 50 + castGap(a);
                if (tier(a) >= 4 && ThreadLocalRandom.current().nextDouble() < plugin.getConfig().getDouble("bosses.tier4-double-cast", 0.1)) {   // 최상위 보스는 가끔 기술 두 개를 겹쳐 씀 (v5.9.7: 25% → 10%, 간격 0.7 → 1.4초)
                    BossDefinition.Skill k2 = otherSkill(a, k);
                    if (k2 != null) {
                        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (a.entity.isValid() && !a.entity.isDead()) cast(a, k2, tg, s, 1); }, 28L);
                        a.nextAny = Math.max(a.nextAny, now + 1400 + windup(k2, s.awakened) * 50 + castGap(a));
                    }
                }
                break;   // 한 번에 기술 하나
            }
        }
    }

    private Player nearest(LivingEntity e, double r) {
        Player best = null;
        double bd = r * r;
        for (Player p : e.getWorld().getPlayers()) {
            if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR || p.isDead()) continue;
            double d = p.getLocation().distanceSquared(e.getLocation());
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    /**
     * v5.9.7 기술 기준점: 보스 발밑. 공중에 떠 있으면 바로 아래 땅 (날아다니는 보스가 기술을 쓰면 예고판 · 이펙트 · 범위가 공중에 떠 있던 문제).
     * 투사체가 나가는 위치(몸 · 머리)는 그대로 몸에서.
     */
    private static Location ground(LivingEntity b) {
        Location l = b.getLocation();
        if (b.isOnGround()) return l;
        org.bukkit.util.RayTraceResult r = l.getWorld().rayTraceBlocks(l, new Vector(0, -1, 0), 64);
        if (r == null) return l;
        Vector hp = r.getHitPosition();
        Location g = new Location(l.getWorld(), hp.getX(), hp.getY(), hp.getZ());
        g.setYaw(l.getYaw());
        g.setPitch(l.getPitch());
        return g;
    }

    /** v5.10.7 발밑 땅에서 몸까지 높이 (땅에 서 있거나 아래에 땅이 없으면 0) */
    private static double airHeight(LivingEntity b) {
        if (b.isOnGround()) return 0;
        return Math.max(0, b.getLocation().getY() - ground(b).getY());
    }

    /** v5.10.8 공중에 뜬 보스는 그 자리에서 기술을 쓰되, 몸에서 발밑 땅으로 기운이 내려꽂히는 연출 (기술 범위는 땅 기준) */
    private void skyStrike(LivingEntity b, long at, Color c) {
        if (airHeight(b) <= 1.2) return;
        later(Math.max(0, at - 6), () -> {
            if (!b.isValid() || airHeight(b) <= 1.2) return;
            Location body = b.getLocation().add(0, b.getHeight() * 0.4, 0), g = ground(b).add(0, 0.1, 0);
            kr.rpgcraft.util.Vfx.beam(body, g, 1.1, c);
            b.getWorld().spawnParticle(Particle.CLOUD, g, 10, 0.6, 0.1, 0.6, 0.04);
            b.getWorld().playSound(g, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.2f, 0.8f);
        });
    }

    /** v5.10.7 나는 보스가 너무 높이 뜨지 않게: max-fly-height 칸 위로는 끌어내리고, 한참 높으면 바로 내려놓음 */
    private void holdAltitude(LivingEntity b) {
        double max = plugin.getConfig().getDouble("bosses.max-fly-height", 8);
        if (max <= 0) return;
        double h = airHeight(b);
        if (h <= max) return;
        if (h > max + 5) {
            Location g = b.getLocation().subtract(0, h - max, 0);
            if (plugin.bossModels() != null) plugin.bossModels().teleportBoss(b, g); else b.teleport(g);
        } else {
            Vector v = b.getVelocity();
            b.setVelocity(new Vector(v.getX(), -Math.min(0.8, 0.25 + (h - max) * 0.12), v.getZ()));
        }
    }

    private List<Player> playersNear(Location l, double r) {
        List<Player> out = new ArrayList<>();
        for (Player p : l.getWorld().getPlayers()) {
            if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR || p.isDead()) continue;
            if (p.getLocation().distanceSquared(l) <= r * r) out.add(p);
        }
        return out;
    }

    // =================================================================== 보스 기술 (고품질 패턴)
    /** 보스마다 기술 색 */
    private static Color theme(String id) {
        return switch (id == null ? "" : id) {
            case "kain", "balrog" -> Color.fromRGB(0xFF2A3A);
            case "desert_nightmare" -> Color.fromRGB(0xFFB030);
            case "siphonia", "elf_queen" -> Color.fromRGB(0x5AFFA0);
            case "vengeful_spirit", "frost_queen", "sea_gatekeeper" -> Color.fromRGB(0x7FE8FF);
            case "witch" -> Color.fromRGB(0x9CFF4A);
            case "volcano_giant", "bungbung" -> Color.fromRGB(0xFF7A1F);
            case "void_apostle", "primordial_dragon" -> Color.fromRGB(0xC060FF);
            case "thunder_god", "harpy_queen" -> Color.fromRGB(0x6BD8FF);
            case "field_boar_king", "field_ravager" -> Color.fromRGB(0xC8864A);
            case "field_frost_bear", "field_frost_lich" -> Color.fromRGB(0xA8E0FF);
            case "field_bandit_lord" -> Color.fromRGB(0xD0D0D0);
            case "field_ancient_golem" -> Color.fromRGB(0x9FB0A0);
            case "field_swamp_witch" -> Color.fromRGB(0x7AE05A);
            case "field_flame_knight" -> Color.fromRGB(0xFF6A1F);
            case "field_deep_warden" -> Color.fromRGB(0x1FC8C8);
            default -> Color.fromRGB(0xFF5050);
        };
    }

    /** 점 p 에서 from + dir * [0, len] 선분까지의 수평 거리 */
    private static double distToLine(Location from, Vector dir, double len, Location p) {
        Vector v = p.toVector().subtract(from.toVector()).setY(0);
        double t = Math.max(0, Math.min(len, v.dot(dir)));
        return v.subtract(dir.clone().multiply(t)).length();
    }

    private void later(long ticks, Runnable r) {
        Bukkit.getScheduler().runTaskLater(plugin, r, ticks);
    }

    private void hurt(LivingEntity b, Collection<Player> ps, double dmg, Set<UUID> once) {
        for (Player p : ps) if (once == null || once.add(p.getUniqueId())) plugin.combat().mobSkillDamage(b, p, dmg);
    }

    // =================================================================== 레이드 연출 도구 (v5.1.5)
    /** 보스별 분위기 입자 */
    private static Particle themeParticle(String id) {
        return switch (id == null ? "" : id) {
            case "kain", "balrog", "volcano_giant", "bungbung" -> Particle.LAVA;
            case "frost_queen", "sea_gatekeeper" -> Particle.SNOWFLAKE;
            case "vengeful_spirit" -> Particle.SOUL_FIRE_FLAME;
            case "void_apostle", "primordial_dragon" -> Particle.REVERSE_PORTAL;
            case "thunder_god", "harpy_queen" -> Particle.ELECTRIC_SPARK;
            case "witch" -> Particle.SPELL_WITCH;
            case "siphonia", "elf_queen" -> Particle.COMPOSTER;
            case "desert_nightmare" -> Particle.ASH;
            case "field_frost_bear", "field_frost_lich" -> Particle.SNOWFLAKE;
            case "field_flame_knight" -> Particle.LAVA;
            case "field_swamp_witch" -> Particle.SPELL_WITCH;
            case "field_deep_warden" -> Particle.SCULK_SOUL;
            case "field_boar_king", "field_ravager", "field_ancient_golem", "field_bandit_lord" -> Particle.CAMPFIRE_COSY_SMOKE;
            default -> Particle.FLAME;
        };
    }

    private static void dustAt(Location l, Color c, float size) {
        l.getWorld().spawnParticle(Particle.REDSTONE, l, 1, 0, 0, 0, 0, new Particle.DustOptions(c, size));
    }

    private static void dustCircle(Location c, double r, Color col, float size) {
        int n = (int) Math.max(10, Math.min(90, r * 7));
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            dustAt(c.clone().add(Math.cos(a) * r, 0.12, Math.sin(a) * r), col, size);
        }
    }

    /**
     * 바닥 위험 지역 (레이드 예고): 테두리가 표시되고 가운데부터 붉게 차오르다가 가득 차는 순간 발동.
     * 판정은 호출한 쪽에서 ticks 뒤에 한다 (여기는 연출만).
     */
    /** 예고판 색 → 반투명 유리 (초록 = 안전, 하늘 = 서리, 나머지 = 위험) */
    private static Material glass(Color c) {
        if (c.asRGB() == 0x5AFF7A) return BossFx.SAFE;   // 안전지대 초록
        if (c.getBlue() > 200 && c.getRed() < 190) return Material.LIGHT_BLUE_STAINED_GLASS;
        if (c.getRed() > 200 && c.getGreen() > 120) return BossFx.ORANGE;
        return BossFx.RED;
    }

    private void telegraph(Location c, double r, int ticks, Color col) {
        BossFx.disc(c, r, ticks, glass(col), col);   // v5.7.0 빛나는 바닥 예고판
        Location o = c.clone();
        for (int t = 0; t <= ticks; t += 2) {
            int tt = t;
            later(t, () -> {
                double f = Math.max(0.08, tt / (double) ticks);
                if (tt % 4 == 0) dustCircle(o, r, col, 1.6f);                                  // 테두리
                dustCircle(o, r * f, Vfx2.light(col, 0.35), 1.2f);                              // 차오르는 선
                if (tt % 6 == 0) for (int i = 0; i < (int) (r * r * f * 0.6) + 2; i++) {         // 안쪽 채움
                    double a = ThreadLocalRandom.current().nextDouble(Math.PI * 2), rr = Math.sqrt(ThreadLocalRandom.current().nextDouble()) * r * f;
                    dustAt(o.clone().add(Math.cos(a) * rr, 0.1, Math.sin(a) * rr), col, 1.0f);
                }
            });
        }
        later(ticks, () -> kr.rpgcraft.util.Vfx.ring(o, r, Color.WHITE));                         // 발동 순간 번쩍
    }

    /** 직선 위험 지역: 폭 width 의 띠가 뿌리부터 끝까지 차오름 */
    private void telegraphLine(Location from, Vector dir, double len, double width, int ticks, Color col) {
        BossFx.rect(from, dir, len, width, ticks, glass(col), col);
        Vector d = dir.clone().setY(0).normalize(), side = new Vector(-d.getZ(), 0, d.getX()).multiply(width / 2);
        Location o = from.clone();
        for (int t = 0; t <= ticks; t += 2) {
            int tt = t;
            later(t, () -> {
                double f = Math.max(0.1, tt / (double) ticks);
                for (double s = 0; s <= len; s += 0.7) {
                    Location p = o.clone().add(d.clone().multiply(s)).add(0, 0.12, 0);
                    if (tt % 4 == 0) { dustAt(p.clone().add(side), col, 1.3f); dustAt(p.clone().subtract(side), col, 1.3f); }
                    if (s <= len * f && tt % 4 == 2) dustAt(p, Vfx2.light(col, 0.3), 1.1f);
                }
            });
        }
    }

    /** 대상 머리 위 표식 (!) — 이 사람을 노린다 */
    private void markTarget(Player p, int ticks, Color col) {
        for (int t = 0; t < ticks; t += 3) {
            later(t, () -> {
                if (!p.isOnline()) return;
                Location h = p.getLocation().add(0, 2.6, 0);
                for (int i = 0; i < 4; i++) dustAt(h.clone().add(0, i * 0.18, 0), col, 1.1f);
                dustAt(h.clone().add(0, -0.3, 0), col, 1.3f);
                dustCircle(p.getLocation(), 1.2, col, 1.0f);
            });
        }
    }

    /** 착탄 흔적: 연기 · 불씨가 잠깐 남음 (연출만) */
    private void scorch(Location at, double r, String bossId) {
        Particle tp = themeParticle(bossId);
        for (int t = 0; t < 30; t += 5) {
            later(t, () -> {
                at.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, at.clone().add(0, 0.2, 0), 2, r * 0.3, 0.05, r * 0.3, 0.01);
                at.getWorld().spawnParticle(tp, at.clone().add(0, 0.3, 0), 4, r * 0.4, 0.1, r * 0.4, 0.02);
            });
        }
    }

    /** 보스가 기를 모으는 연출 (입자가 몸으로 빨려 들어옴) */
    private void charge(LivingEntity b, int ticks, Color c) {
        Particle tp = themeParticle(b.getPersistentDataContainer().get(Keys.BOSS, PersistentDataType.STRING));
        for (int t = 0; t < ticks; t += 2) {
            int tt = t;
            later(t, () -> {
                if (!b.isValid()) return;
                Location o = ground(b).add(0, b.getHeight() * 0.6, 0);
                double r = 3.5 - 3 * tt / (double) ticks;
                for (int i = 0; i < 6; i++) {
                    double a = tt * 0.5 + i * Math.PI / 3;
                    Location p = o.clone().add(Math.cos(a) * r, Math.sin(a * 2) * 0.6, Math.sin(a) * r);
                    dustAt(p, c, 1.3f);
                }
                b.getWorld().spawnParticle(tp, o, 2, 0.3, 0.3, 0.3, 0.01);
            });
        }
        b.getWorld().playSound(ground(b), Sound.BLOCK_BEACON_ACTIVATE, 1.4f, 0.7f);
    }

    private static final class Vfx2 {
        static Color light(Color c, double t) {
            return Color.fromRGB((int) (c.getRed() + (255 - c.getRed()) * t), (int) (c.getGreen() + (255 - c.getGreen()) * t), (int) (c.getBlue() + (255 - c.getBlue()) * t));
        }
    }

    // =================================================================== 보스 기술 (레이드 패턴)
    private void cast(Active a, BossDefinition.Skill k, Player target, MobManager.MobState s) {
        cast(a, k, target, s, 0);
    }

    /** depth: 연계(콤보)로 이어진 횟수 — 연계 중에는 경직 없이 다음 기술로 넘어가고, 마지막 기술 뒤에만 경직 */
    private void cast(Active a, BossDefinition.Skill k, Player target, MobManager.MobState s, int depth) {
        LivingEntity b = a.entity;
        World w = b.getWorld();
        Color c = theme(a.def.id);
        Color red = Color.fromRGB(0xFF2A2A), green = Color.fromRGB(0x5AFF7A);
        Material mat = BossFx.theme(a.def.id);   // v5.7.0 파편 · 지진 블록
        boolean aw = s.awakened;
        double dmg = s.damage * k.power;
        long fireAt = windup(k, aw);
        int tr = tier(a);
        double combo = COMBO[tr - 1] * (depth == 0 ? 1 : 0.6) * (a.phase == 3 ? 1.3 : 1);
        BossDefinition.Skill next = fireAt > 0 && depth < 1 && !"SUMMON".equals(k.type)   // v5.9.7: 연계는 한 번까지
                && ThreadLocalRandom.current().nextDouble() < combo ? otherSkill(a, k) : null;
        if (next != null) {   // v5.7.0 연계: 앞 기술이 터진 뒤 다른 기술로 이어짐 (v5.9.7: 바로 → 0.8초 틈, 그만큼 다음 기술도 늦춤)
            a.nextAny = Math.max(a.nextAny, System.currentTimeMillis() + (fireAt + 16 + windup(next, aw)) * 50 + castGap(a));
            later(fireAt + 16, () -> {
                if (!b.isValid() || b.isDead() || target == null || !target.isOnline()) { stagger(a); return; }
                for (Player p : a.bar.getPlayers()) Text.actionBar(p, "&c&l⚡ 연계! &f" + skillLabel(next.type));
                cast(a, next, target, s, depth + 1);
            });
        } else if (fireAt > 0) later(fireAt, () -> stagger(a));   // v5.6.0: 선딜(예고)이 끝나 기술이 터진 직후 잠시 경직
        Particle tp = themeParticle(a.def.id);
        if (!"SUMMON".equals(k.type) && !k.type.contains("VOLLEY") && !"FIREBALL".equals(k.type)) skyStrike(b, fireAt, c);   // v5.10.8: 공중이면 몸에서 땅으로 내려꽂는 연출
        if (plugin.bossModels() != null) plugin.bossModels().attackPose(b);
        switch (k.type) {
            // ---------------------------------------------------------- v5.6.0 컨셉 패턴 — 그냥 달려서는 못 피하고, 보고 판단해야 하는 기믹
            case "VOLLEY", "FIREBALL" -> {   // 직선 일제 사격: 탄도(띠)가 먼저 그려진 뒤 그 띠를 따라 곧게 날아감 (유도 없음) — 띠 사이 틈에 서면 안전
                if (target == null) return;
                int n = Math.max(1, k.amount) + (aw ? 2 : 0);
                Vector base = target.getLocation().toVector().subtract(ground(b).toVector()).setY(0);
                if (base.lengthSquared() < 0.01) base = ground(b).getDirection().setY(0);
                double aim = Math.max(2, Math.min(20, base.length()));   // v5.10.8 공중에서 쏠 때 조준할 거리
                base.normalize();
                double spread = n <= 1 ? 0 : Math.toRadians(Math.min(80, 15 * (n - 1)));
                int wind = 16;
                charge(b, wind, c);
                for (int i = 0; i < n; i++) {
                    Vector d = base.clone().rotateAroundY(n <= 1 ? 0 : -spread / 2 + spread * i / (n - 1));
                    telegraphLine(ground(b), d, 22, 1.5, wind, red);
                    shootStraight(a, d, k.speed, 22, dmg, 1.3, wind + i * 2L, c, tp, aim);
                }
                later(wind, () -> w.playSound(ground(b), Sound.ENTITY_BLAZE_SHOOT, 1.4f, 0.6f));
            }
            case "BACKSTEP_VOLLEY" -> {   // 백스텝 사격: 뒤로 크게 물러난 뒤 부채꼴로 화살 — 화살 띠 사이의 틈으로 들어가야 함
                if (target == null) return;
                Vector away = ground(b).toVector().subtract(target.getLocation().toVector()).setY(0);
                if (away.lengthSquared() < 0.01) away = new Vector(1, 0, 0);
                away.normalize();
                b.setVelocity(away.multiply(1.5).setY(b.isOnGround() ? 0.55 : 0));
                w.spawnParticle(Particle.CLOUD, ground(b), 12, 0.4, 0.1, 0.4, 0.05);
                w.playSound(ground(b), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.2f, 1.5f);
                int n = Math.max(3, k.amount) + (aw ? 2 : 0);
                later(12, () -> {
                    if (!b.isValid() || !target.isOnline() || target.getWorld() != b.getWorld()) return;
                    Vector base = target.getLocation().toVector().subtract(ground(b).toVector()).setY(0);
                    if (base.lengthSquared() < 0.01) return;
                    double aim = Math.max(2, Math.min(22, base.length()));
                    base.normalize();
                    double spread = Math.toRadians(13 * (n - 1));
                    for (int i = 0; i < n; i++) {
                        Vector d = base.clone().rotateAroundY(-spread / 2 + spread * i / (n - 1));
                        telegraphLine(ground(b), d, 24, 1.3, 16, red);
                        shootStraight(a, d, Math.max(0.9, k.speed), 24, dmg, 1.1, 16 + (i % 2) * 4L, c, Particle.CRIT, aim);
                    }
                    w.playSound(ground(b), Sound.ENTITY_ARROW_SHOOT, 1.4f, 0.7f);
                });
            }
            case "CHECKER" -> {   // 바둑판 폭발: 반씩 두 번 터짐 — 첫 폭발이 끝난 칸으로 옮겨 서야 함
                Location o = ground(b).getBlock().getLocation().add(0.5, 0, 0.5);
                double cell = 3;
                int half = (int) Math.ceil(Math.max(6, k.radius) / cell);
                for (int wv = 0; wv < 2; wv++) {
                    int par = wv;
                    long at = 26 + wv * 26L;
                    List<Location> cells = new ArrayList<>();
                    for (int i = -half; i <= half; i++) for (int j = -half; j <= half; j++) if (((i + j) & 1) == par) cells.add(o.clone().add(i * cell, 0, j * cell));
                    later(at - 26, () -> { for (Location cl : cells) telegraphSquare(cl, cell / 2 - 0.15, 26, red); });
                    later(at, () -> {
                        if (!b.isValid()) return;
                        for (Location cl : cells) {
                            BossFx.debris(cl, 2, mat, 0.9);
                            w.spawnParticle(tp, cl.clone().add(0, 0.5, 0), 4, 0.6, 0.3, 0.6, 0.03);
                            if (ThreadLocalRandom.current().nextInt(3) == 0) w.spawnParticle(Particle.EXPLOSION_LARGE, cl.clone().add(0, 0.5, 0), 1);
                        }
                        Set<UUID> once = new HashSet<>();
                        for (Player p : playersNear(o, (half + 1) * cell * 1.5)) {
                            int ci = (int) Math.round((p.getLocation().getX() - o.getX()) / cell), cj = (int) Math.round((p.getLocation().getZ() - o.getZ()) / cell);
                            if (Math.abs(ci) > half || Math.abs(cj) > half || ((ci + cj) & 1) != par || !once.add(p.getUniqueId())) continue;
                            plugin.combat().mobSkillDamage(b, p, dmg);
                        }
                        w.playSound(o, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 1.1f);
                    });
                }
                announce(a, "&c바둑판 폭발! &7먼저 터진 칸으로 옮겨 서세요");
            }
            case "SAFE_ZONE" -> {   // 안전지대: 넓은 범위 전체가 터지고 초록 원 안만 안전 — 원을 찾아 들어가야 함
                Location o = ground(b);
                double R = Math.max(12, k.radius), sr = 2.6;
                int n = Math.max(1, k.amount), wind = aw ? 32 : 40;
                List<Location> safes = new ArrayList<>();
                for (int i = 0; i < n; i++) {
                    double ang = ThreadLocalRandom.current().nextDouble(Math.PI * 2), d = R * (0.45 + ThreadLocalRandom.current().nextDouble(0.35));
                    safes.add(o.clone().add(Math.cos(ang) * d, 0, Math.sin(ang) * d));
                }
                telegraph(o, R, wind, red);
                for (int t = 0; t <= wind; t += 3) later(t, () -> { for (Location sf : safes) { dustCircle(sf, sr, green, 1.8f); w.spawnParticle(Particle.VILLAGER_HAPPY, sf.clone().add(0, 0.5, 0), 3, 1, 0.2, 1, 0); } });
                for (Location sf : safes) kr.rpgcraft.util.Vfx.beam(sf, sf.clone().add(0, 10, 0), 1.1, green);
                for (Location sf : safes) BossFx.disc(sf, sr, wind, BossFx.SAFE, green);
                charge(b, wind, c);
                later(wind, () -> {
                    if (!b.isValid()) return;
                    kr.rpgcraft.util.Vfx.ring(o, R, wv(c));
                    BossFx.boom(o, Math.min(R, 14), mat);
                    for (int q = 0; q < 6; q++) {
                        Location at = o.clone().add(ThreadLocalRandom.current().nextDouble(-R, R), 0, ThreadLocalRandom.current().nextDouble(-R, R));
                        later(q, () -> { kr.rpgcraft.util.Vfx.burst(at.clone().add(0, 1, 0), 4, c); w.spawnParticle(Particle.EXPLOSION_LARGE, at, 1); });
                    }
                    w.playSound(o, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.5f);
                    for (Player p : playersNear(o, R)) {
                        boolean ok = false;
                        for (Location sf : safes) if (p.getLocation().distanceSquared(sf) <= (sr + 0.4) * (sr + 0.4)) { ok = true; break; }
                        if (!ok) plugin.combat().mobSkillDamage(b, p, dmg * 1.3);
                    }
                });
                announce(a, "&a초록 원 안으로! &7나머지는 전부 터집니다");
            }
            case "DONUT" -> {   // 안팎 교대: 안쪽 원 → 바깥 고리 (또는 반대로) 연달아 터짐 — 나갔다가 다시 들어와야 함
                Location o = ground(b);
                double r = Math.max(3.5, k.radius * 0.45), R = Math.max(10, k.radius * 1.4);
                boolean innerFirst = ThreadLocalRandom.current().nextBoolean();
                for (int st = 0; st < 2; st++) {
                    boolean inner = (st == 0) == innerFirst;
                    long at = 26 + st * 22L;
                    int dur = st == 0 ? 26 : 22;
                    later(at - dur, () -> { if (inner) telegraph(o, r, dur, red); else telegraphRing(o, r, R, dur, red); });
                    later(at, () -> {
                        if (!b.isValid()) return;
                        if (inner) { kr.rpgcraft.util.Vfx.burst(o.clone().add(0, 1, 0), r * 1.4, c); w.spawnParticle(Particle.EXPLOSION_LARGE, o, 3, r * 0.4, 0.2, r * 0.4); }
                        else for (double rr = r + 1; rr <= R; rr += 2) kr.rpgcraft.util.Vfx.ring(o, rr, rr % 4 < 2 ? c : Color.WHITE);
                        w.playSound(o, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, inner ? 1.2f : 0.7f);
                        if (inner) BossFx.boom(o, r, mat);
                        else { BossFx.quake(o, R, mat); BossFx.debris(o, 14, mat, R * 0.45); }
                        for (Player p : playersNear(o, R)) {
                            double d = Math.hypot(p.getLocation().getX() - o.getX(), p.getLocation().getZ() - o.getZ());
                            if (inner ? d <= r + 0.3 : d > r - 0.3) plugin.combat().mobSkillDamage(b, p, dmg);
                        }
                    });
                }
                announce(a, innerFirst ? "&c안쪽 → 바깥 순서! &7밖으로 나갔다가 곧바로 보스 곁으로" : "&c바깥 → 안쪽 순서! &7보스 곁에 붙었다가 곧바로 밖으로");
            }
            case "SWEEP" -> {   // 회전 베기: 보스를 축으로 긴 띠가 한 바퀴 돎 — 시작 방향과 도는 방향이 먼저 보임, 보스 발밑 초록 원은 안전
                Location o = ground(b);
                double L = Math.max(10, k.radius * 1.8), safeR = 2.6;
                double start = target == null ? 0 : Math.atan2(target.getLocation().getZ() - o.getZ(), target.getLocation().getX() - o.getX());
                int sgn = ThreadLocalRandom.current().nextBoolean() ? 1 : -1, wind = 22, spin = aw ? 30 : 40;
                telegraphLine(o, new Vector(Math.cos(start), 0, Math.sin(start)), L, 2.2, wind, red);
                for (int t = 0; t <= wind; t += 4) later(t, () -> {   // 도는 방향 화살표 (시작 띠 옆으로 번지는 점선)
                    for (int q = 1; q <= 4; q++) {
                        double ang = start + sgn * q * 0.12;
                        dustAt(o.clone().add(Math.cos(ang) * L * 0.8, 0.2, Math.sin(ang) * L * 0.8), Vfx2.light(red, q * 0.15), 1.4f);
                    }
                });
                for (int t = 0; t <= wind + spin; t += 4) later(t, () -> dustCircle(o, safeR, green, 1.5f));
                BossFx.disc(o, safeR, wind + spin, BossFx.SAFE, green);
                Set<UUID> once = new HashSet<>();
                for (int t = 0; t <= spin; t += 2) {
                    int tt = t;
                    later(wind + t, () -> {
                        if (!b.isValid()) return;
                        double ang = start + sgn * Math.PI * 2 * tt / spin;
                        Vector dv = new Vector(Math.cos(ang), 0, Math.sin(ang));
                        kr.rpgcraft.util.Vfx.beam(o.clone().add(dv.clone().multiply(safeR)).add(0, 1, 0), o.clone().add(dv.clone().multiply(L)).add(0, 1, 0), 1.6, c);
                        if (tt % 6 == 0) w.playSound(o, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.6f + tt * 0.01f);
                        if (tt % 4 == 0) BossFx.debris(o.clone().add(dv.clone().multiply(L * 0.75)), 2, mat, 1.1);
                        for (Player p : playersNear(o, L + 1)) {
                            double d = Math.hypot(p.getLocation().getX() - o.getX(), p.getLocation().getZ() - o.getZ());
                            if (d > safeR && distToLine(o, dv, L, p.getLocation()) <= 1.4 && once.add(p.getUniqueId())) {
                                plugin.combat().mobSkillDamage(b, p, dmg);
                                p.setVelocity(new Vector(-dv.getZ() * sgn, 0, dv.getX() * sgn).multiply(0.9).setY(0.4));
                            }
                        }
                    });
                }
                announce(a, "&c회전 베기! &7보스 발밑 초록 원으로 파고들거나 띠보다 앞서 도세요");
            }
            case "WAVE_WALL" -> {   // 해일 벽: 넓은 벽이 밀려옴 — 초록으로 빛나는 틈으로만 통과할 수 있음
                if (target == null) return;
                Location o = ground(b);
                Vector d = target.getLocation().toVector().subtract(o.toVector()).setY(0);
                if (d.lengthSquared() < 0.01) d = new Vector(1, 0, 0);
                Vector fd = d.normalize(), side = new Vector(-fd.getZ(), 0, fd.getX());
                double W = 13, gapHalf = 1.7, gap = ThreadLocalRandom.current().nextDouble(-W + 3, W - 3), travel = 30, speed = 0.7;
                Location start = o.clone().subtract(fd.clone().multiply(3));
                int wind = 24;
                for (int t = 0; t <= wind; t += 4) later(t, () -> {
                    for (double s2 = -W; s2 <= W; s2 += 0.8) {
                        boolean inGap = Math.abs(s2 - gap) <= gapHalf;
                        dustAt(start.clone().add(side.clone().multiply(s2)).add(0, 0.3, 0), inGap ? green : red, 1.5f);
                        if (inGap) for (double f = 2; f < travel; f += 3) dustAt(start.clone().add(side.clone().multiply(s2)).add(fd.clone().multiply(f)).add(0, 0.15, 0), green, 1.0f);
                    }
                });
                w.playSound(o, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.2f, 0.6f);
                Set<UUID> once = new HashSet<>();
                int steps = (int) (travel / speed);
                BossFx.wall(start, fd, W, gap, gapHalf, travel, steps, wind, mat);   // 실제로 밀려오는 벽 (틈 포함)
                BossFx.rect(start.clone().add(side.clone().multiply(gap)).subtract(fd.clone().multiply(0.5)), fd, travel, gapHalf * 2, wind, BossFx.SAFE, green);
                for (int t = 0; t <= steps; t += 2) {
                    int tt = t;
                    later(wind + t, () -> {
                        if (!b.isValid()) return;
                        Location front = start.clone().add(fd.clone().multiply(tt * speed));
                        for (double s2 = -W; s2 <= W; s2 += 1.2) {
                            if (Math.abs(s2 - gap) <= gapHalf) continue;
                            Location pt = front.clone().add(side.clone().multiply(s2));
                            w.spawnParticle(tp, pt.clone().add(0, 1, 0), 2, 0.2, 0.8, 0.2, 0.02);
                            if (tt % 4 == 0) dustAt(pt.clone().add(0, 2.2, 0), c, 1.8f);
                        }
                        for (Player p : playersNear(front, W + 2)) {
                            Vector rel = p.getLocation().toVector().subtract(front.toVector()).setY(0);
                            double along = rel.dot(fd), lat = rel.dot(side);
                            if (Math.abs(along) > 1.0 || Math.abs(lat) > W || Math.abs(lat - gap) <= gapHalf || !once.add(p.getUniqueId())) continue;
                            plugin.combat().mobSkillDamage(b, p, dmg);
                            p.setVelocity(fd.clone().multiply(1.4).setY(0.5));
                        }
                    });
                }
                announce(a, "&b밀려오는 벽! &7초록 틈을 찾아 통과하세요");
            }
            case "GUST" -> {   // 힘껏 밀기: 보스 앞 부채꼴 돌풍 — 부채꼴 밖(옆 · 뒤)으로 피해야 함, 맞으면 멀리 날아감
                if (target == null) return;
                Location o = ground(b);
                Vector f = target.getLocation().toVector().subtract(o.toVector()).setY(0);
                if (f.lengthSquared() < 0.01) f = new Vector(1, 0, 0);
                Vector fd = f.normalize();
                double R = Math.max(10, k.radius), half = 50;
                int wind = 22;
                telegraphCone(o, fd, R, half, wind, red);
                charge(b, wind, c);
                w.playSound(o, Sound.ENTITY_PHANTOM_FLAP, 1.6f, 0.6f);
                later(wind, () -> {
                    if (!b.isValid()) return;
                    for (int q = 0; q < 12; q++) {
                        Vector dv = fd.clone().rotateAroundY(Math.toRadians(-half + 2 * half * q / 11.0));
                        for (double dd = 1; dd < R; dd += 1.5) w.spawnParticle(Particle.CLOUD, o.clone().add(dv.clone().multiply(dd)).add(0, 1, 0), 1, 0.1, 0.2, 0.1, 0.25);
                    }
                    w.playSound(o, Sound.ENTITY_ENDER_DRAGON_FLAP, 2f, 0.5f);
                    BossFx.debris(o.clone().add(fd.clone().multiply(3)), 12, mat, R * 0.4);
                    for (Player p : playersNear(o, R)) {
                        Vector v = p.getLocation().toVector().subtract(o.toVector()).setY(0);
                        if (v.lengthSquared() < 0.01) v = fd.clone();
                        if (Math.toDegrees(v.angle(fd)) > half) continue;
                        plugin.combat().mobSkillDamage(b, p, dmg * 0.8);
                        p.setVelocity(v.normalize().multiply(2.6).setY(0.7));
                    }
                });
                announce(a, "&b돌풍! &7보스 옆이나 뒤로 돌아가세요");
            }
            case "FRONT_BACK" -> {   // 앞뒤 베기: 앞 반원 → 뒤 반원 차례로 터짐 — 먼저 뒤로 돌았다가 곧바로 앞으로
                if (target == null) return;
                Location o = ground(b);
                Vector f = target.getLocation().toVector().subtract(o.toVector()).setY(0);
                if (f.lengthSquared() < 0.01) f = new Vector(1, 0, 0);
                Vector fd = f.normalize();
                double R = Math.max(7, k.radius);
                boolean frontFirst = ThreadLocalRandom.current().nextInt(3) > 0;
                for (int st = 0; st < 2; st++) {
                    Vector dir = (st == 0) == frontFirst ? fd.clone() : fd.clone().multiply(-1);
                    int dur = st == 0 ? 24 : 18;
                    long at = 24 + st * 18L;
                    later(at - dur, () -> telegraphCone(o, dir, R, 90, dur, red));
                    later(at, () -> {
                        if (!b.isValid()) return;
                        kr.rpgcraft.util.Vfx.slash(o.clone().add(dir.clone().multiply(R * 0.5)).add(0, 1, 0), dir, R, 0, c);
                        kr.rpgcraft.util.Vfx.slash(o.clone().add(dir.clone().multiply(R * 0.5)).add(0, 1.2, 0), dir, R * 0.8, 20, Color.WHITE);
                        w.playSound(o, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.8f, 0.5f);
                        BossFx.quake(o.clone().add(dir.clone().multiply(R * 0.5)), R * 0.5, mat);
                        for (Player p : playersNear(o, R)) {
                            Vector v = p.getLocation().toVector().subtract(o.toVector()).setY(0);
                            if (v.dot(dir) >= -0.3) plugin.combat().mobSkillDamage(b, p, dmg);
                        }
                    });
                }
                announce(a, frontFirst ? "&c앞 → 뒤! &7보스 뒤로 돌았다가 다시 앞으로" : "&c뒤 → 앞! &7보스 앞에 있다가 뒤로");
            }
            case "SPREAD" -> {   // 낙뢰 표식: 모두의 자리에 낙뢰 표식 → 그 자리에 떨어짐. 겹치면 피해도 겹침 — 흩어지고 자리에서 벗어나기
                double rr = Math.max(3, k.radius * 0.5);
                int wind = 30;
                List<Location> spots = new ArrayList<>();
                for (Player p : playersNear(ground(b), 26)) {
                    Location at = p.getLocation().clone();
                    spots.add(at);
                    markTarget(p, 16, red);
                    telegraph(at, rr, wind, red);
                }
                later(wind, () -> {
                    if (!b.isValid()) return;
                    for (Location at : spots) {
                        at.getWorld().strikeLightningEffect(at);
                        BossFx.pillar(at, rr * 0.5, 5, mat);
                        kr.rpgcraft.util.Vfx.burst(at.clone().add(0, 1, 0), rr * 1.2, c);
                        hurt(b, playersNear(at, rr), dmg * 0.8, null);   // 여러 표식이 겹친 곳은 여러 번 맞음
                    }
                });
                announce(a, "&e낙뢰 표식! &7서로 흩어지고 표식에서 벗어나세요");
            }
            // ---------------------------------------------------------- v5.5.0 새 패턴 (필드 보스 등)
            case "CHARGE" -> {   // 돌진: 대상 쪽으로 붉은 띠가 차오른 뒤 띠를 따라 돌진, 띠 안에 있으면 피해 + 튕겨 나감
                if (target == null) return;
                Location from = ground(b);
                Vector dir = target.getLocation().toVector().subtract(from.toVector()).setY(0);
                if (dir.lengthSquared() < 0.01) dir = from.getDirection().setY(0);
                dir.normalize();
                double len = Math.max(8, k.radius * 2.5), width = 3.2;
                Vector fd = dir.clone();
                telegraphLine(from, fd, len, width, 20, red);
                w.playSound(from, Sound.ENTITY_RAVAGER_ROAR, 1.4f, 0.9f);
                later(20, () -> {
                    if (!b.isValid()) return;
                    b.setVelocity(fd.clone().multiply(Math.min(3.2, len / 5.0)).setY(0.25));
                    w.playSound(ground(b), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.6f, 0.6f);
                    Set<UUID> once = new HashSet<>();
                    for (Player p : playersNear(from, len + 2)) {
                        if (distToLine(from, fd, len, p.getLocation()) > width / 2 + 0.4 || !once.add(p.getUniqueId())) continue;
                        plugin.combat().mobSkillDamage(b, p, dmg);
                        p.setVelocity(fd.clone().multiply(0.6).add(new Vector(0, 0.7, 0)));
                    }
                    for (double d = 0; d <= len; d += 1.5) w.spawnParticle(tp, from.clone().add(fd.clone().multiply(d)).add(0, 0.5, 0), 3, 0.4, 0.3, 0.4, 0.02);
                    for (double d = 2; d <= len; d += 3) { double dd = d; later((long) (d / 3), () -> BossFx.debris(from.clone().add(fd.clone().multiply(dd)), 3, mat, 1.3)); }
                });
            }
            case "NOVA" -> {   // 파동: 보스 주변으로 고리 3개가 차례로 퍼짐 — 고리 사이 틈에 서거나 고리를 뛰어넘어 피함
                Location o = ground(b);
                double[] rings = {k.radius * 0.45, k.radius * 0.8, k.radius * 1.15};
                charge(b, 14, c);
                for (int i = 0; i < rings.length; i++) {
                    double rr = rings[i];
                    long at = 18L + i * 10L;
                    later(at - 12, () -> { dustCircle(o, rr, red, 1.6f); dustCircle(o, rr - 1.1, Vfx2.light(red, 0.4), 1.1f); dustCircle(o, rr + 1.1, Vfx2.light(red, 0.4), 1.1f); });
                    later(at, () -> {
                        kr.rpgcraft.util.Vfx.ring(o, rr, c);
                        BossFx.ring(o, Math.max(0, rr - 1.2), rr + 1.2, 3, BossFx.ORANGE, c);
                        BossFx.debris(o, 8, mat, rr * 0.5);
                        w.spawnParticle(tp, o, (int) (rr * 5), rr * 0.7, 0.2, rr * 0.7, 0.03);
                        w.playSound(o, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.8f, 1.4f);
                        for (Player p : playersNear(o, rr + 1.3)) {
                            double dd = p.getLocation().distance(o);
                            if (Math.abs(dd - rr) <= 1.3 && p.isOnGround()) plugin.combat().mobSkillDamage(b, p, dmg * 0.7);
                        }
                    });
                }
            }
            case "ERUPTION" -> {   // 분출: 주변 모든 사람 발밑에 원이 생기고 잠시 뒤 땅이 솟구침 (각성 시 한 번 더, 따라다님)
                int waves = Math.max(1, k.amount) + (aw ? 1 : 0);
                for (int wv = 0; wv < waves; wv++) {
                    later(wv * 26L, () -> {
                        if (!b.isValid()) return;
                        for (Player p : playersNear(ground(b), 22)) {
                            Location at = p.getLocation().clone();
                            double rr = Math.max(2.2, k.radius * 0.5);
                            telegraph(at, rr, 22, red);
                            markTarget(p, 18, red);
                            later(22, () -> {
                                w.spawnParticle(Particle.EXPLOSION_LARGE, at, 2, 0.5, 0.2, 0.5, 0);
                                BossFx.pillar(at, rr * 0.6, 4.5, mat);
                                w.spawnParticle(tp, at, 25, rr * 0.4, 1.2, rr * 0.4, 0.05);
                                kr.rpgcraft.util.Vfx.beam(at, at.clone().add(0, 5, 0), 0.9, c);
                                w.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.2f);
                                for (Player q : playersNear(at, rr)) {
                                    plugin.combat().mobSkillDamage(b, q, dmg * 0.8);
                                    q.setVelocity(new Vector(0, 0.95, 0));
                                }
                            });
                        }
                    });
                }
            }
            case "CROSS" -> {   // 십자 베기: 보스를 중심으로 십자(각성: 팔방) 띠가 차오른 뒤 한꺼번에 폭발
                Location o = ground(b);
                double len = Math.max(8, k.radius * 2);
                List<Vector> dirs = new ArrayList<>(List.of(new Vector(1, 0, 0), new Vector(-1, 0, 0), new Vector(0, 0, 1), new Vector(0, 0, -1)));
                if (aw) for (int[] d : new int[][]{{1, 1}, {1, -1}, {-1, 1}, {-1, -1}}) dirs.add(new Vector(d[0], 0, d[1]).normalize());
                double rot = ThreadLocalRandom.current().nextBoolean() ? 0 : Math.PI / 4;   // 가끔 X자
                for (Vector d : dirs) d.rotateAroundY(rot);
                for (Vector d : dirs) telegraphLine(o, d, len, 2.6, 22, red);
                charge(b, 20, c);
                later(22, () -> {
                    Set<UUID> once = new HashSet<>();
                    for (Vector d : dirs) {
                        for (double t = 0; t <= len; t += 1.2) kr.rpgcraft.util.Vfx.burst(o.clone().add(d.clone().multiply(t)).add(0, 0.6, 0), 0.9, c);
                        for (double t = 2; t <= len; t += 4) BossFx.debris(o.clone().add(d.clone().multiply(t)), 3, mat, 1.1);
                        for (Player p : playersNear(o, len + 1)) if (distToLine(o, d, len, p.getLocation()) <= 1.7 && once.add(p.getUniqueId())) plugin.combat().mobSkillDamage(b, p, dmg);
                    }
                    w.playSound(o, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 2f, 0.6f);
                    w.playSound(o, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 1.4f);
                });
            }
            case "FROST_FIELD" -> {   // 서리 장판: 대상 자리에 큰 원이 생겨 5초 동안 안에 있으면 계속 피해 + 느려짐
                if (target == null) return;
                Location at = target.getLocation().clone();
                double rr = Math.max(3, k.radius);
                telegraph(at, rr, 16, Color.fromRGB(0x9FD8FF));
                for (int t = 0; t < 5; t++) {
                    later(16L + t * 20L, () -> {
                        dustCircle(at, rr, Color.fromRGB(0x9FD8FF), 1.5f);
                        w.spawnParticle(Particle.SNOWFLAKE, at.clone().add(0, 0.4, 0), (int) (rr * 8), rr * 0.6, 0.3, rr * 0.6, 0.01);
                        for (Player p : playersNear(at, rr)) {
                            plugin.combat().mobSkillDamage(b, p, dmg * 0.3);
                            p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOW, 30, 1));
                        }
                    });
                }
                w.playSound(at, Sound.BLOCK_GLASS_BREAK, 1.2f, 0.5f);
            }
            case "ROAR" -> {   // 포효: 짧게 기를 모은 뒤 주변을 밀쳐내고 잠시 약화
                double rr = Math.max(4, k.radius);
                Location o = ground(b);
                telegraph(o, rr, 14, red);
                w.playSound(o, Sound.ENTITY_RAVAGER_ROAR, 2f, 0.6f);
                later(14, () -> {
                    if (!b.isValid()) return;
                    w.playSound(ground(b), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.6f, 0.8f);
                    kr.rpgcraft.util.Fx.shockwave(plugin, ground(b), (int) rr, c);
                    for (Player p : playersNear(ground(b), rr)) {
                        plugin.combat().mobSkillDamage(b, p, dmg * 0.6);
                        p.setVelocity(p.getLocation().toVector().subtract(ground(b).toVector()).setY(0).normalize().multiply(1.3).setY(0.5));
                        p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.WEAKNESS, 60, 0));
                    }
                });
            }
            case "SLAM" -> {   // 대지 분쇄: 붉은 원이 차오르는 동안 보스가 높이 뛰어올랐다가 내려찍음 → 크레이터 + 바깥으로 번지는 여진 (각성: 두 번째 더 넓게)
                for (int rep = 0; rep < (aw ? 2 : 1); rep++) {
                    double R = k.radius * (1 + rep * 0.4);
                    long base = 18L + rep * 30L;
                    later(base - 18, () -> {
                        if (!b.isValid()) return;
                        telegraph(ground(b), R, 18, red);
                        b.setVelocity(new Vector(0, 0.9, 0));
                        w.playSound(ground(b), Sound.ENTITY_RAVAGER_ROAR, 1.2f, 0.7f);
                    });
                    later(base, () -> {
                        if (!b.isValid()) return;
                        Location o = ground(b);
                        Set<UUID> once = new HashSet<>();
                        w.spawnParticle(Particle.FLASH, o.clone().add(0, 0.5, 0), 1);
                        w.spawnParticle(Particle.EXPLOSION_HUGE, o, 1);
                        kr.rpgcraft.util.Vfx.burst(o.clone().add(0, 1, 0), R * 0.7, Color.WHITE);
                        BossFx.boom(o, R, mat);
                        for (int wv = 1; wv <= 4; wv++) {   // 여진 4겹 (안쪽 → 바깥)
                            double rr = R * wv / 4;
                            later(wv * 2L, () -> {
                                kr.rpgcraft.util.Vfx.ring(o, rr, wv(c));
                                w.spawnParticle(tp, o, (int) (rr * 4), rr * 0.6, 0.2, rr * 0.6, 0.05);
                                for (Player p : playersNear(o, rr)) if (once.add(p.getUniqueId())) {
                                    plugin.combat().mobSkillDamage(b, p, dmg);
                                    p.setVelocity(p.getLocation().toVector().subtract(o.toVector()).setY(0).normalize().multiply(0.9).setY(0.6));
                                }
                            });
                        }
                        for (int d = 0; d < 10; d++) {   // 갈라지는 땅 (빛나는 균열)
                            double ang = d * Math.PI / 5 + ThreadLocalRandom.current().nextDouble(0.3);
                            Location crack = o.clone().add(Math.cos(ang) * R * 0.85, 0.2, Math.sin(ang) * R * 0.85);
                            kr.rpgcraft.util.Vfx.beam(o.clone().add(0, 0.2, 0), crack, 0.7, c);
                            later(4, () -> kr.rpgcraft.util.Vfx.burst(crack, 1.8, c));
                        }
                        w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, o, 30, R * 0.4, 0.2, R * 0.4, 0.03);
                        scorch(o, R * 0.6, a.def.id);
                        w.playSound(o, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.5f);
                        w.playSound(o, Sound.ENTITY_WARDEN_ATTACK_IMPACT, 1.6f, 0.6f);
                        for (Player p : playersNear(o, 20)) p.playSound(p.getLocation(), Sound.ENTITY_IRON_GOLEM_DAMAGE, 0.7f, 0.5f);   // 땅울림
                    });
                }
            }
            case "METEOR" -> {   // 유성 낙하: 대상 발밑에 차오르는 원 → 하늘이 갈라지며 불타는 유성이 떨어짐 → 크레이터 (각성 · 여럿이면 여러 개)
                List<Player> targets = new ArrayList<>(playersNear(ground(b), 30));
                Collections.shuffle(targets);
                if (!targets.contains(target)) targets.add(0, target);
                int n = Math.min(targets.size(), aw ? 3 : 1);
                w.playSound(ground(b), Sound.ENTITY_WITHER_SHOOT, 1.4f, 0.5f);
                for (int i = 0; i < n; i++) {
                    Location at = targets.get(i).getLocation().clone();
                    telegraph(at, k.radius, 24, red);
                    BossFx.meteor(at, 2 + k.radius * 0.25, 24, mat);
                    for (int f = 0; f < 8; f++) {   // 떨어지는 유성 (꼬리)
                        int ff = f;
                        later(12 + f * 1.5 > 23 ? 23 : 12 + (long) (f * 1.5), () -> {
                            Location hi = at.clone().add(-6 + ff * 0.75, 24 - ff * 3, -3 + ff * 0.37);
                            kr.rpgcraft.util.Vfx.burst(hi, 2.6 - ff * 0.1, c);
                            w.spawnParticle(Particle.FLAME, hi, 12, 0.4, 0.4, 0.4, 0.05);
                            w.spawnParticle(Particle.SMOKE_LARGE, hi, 6, 0.3, 0.3, 0.3, 0.02);
                        });
                    }
                    later(24, () -> {
                        if (!b.isValid()) return;
                        kr.rpgcraft.util.Vfx.beam(at.clone().add(-6, 24, -3), at.clone().add(0, 1, 0), 2.4, c);
                        kr.rpgcraft.util.Vfx.burst(at.clone().add(0, 1, 0), k.radius * 1.4, c);
                        kr.rpgcraft.util.Vfx.burst(at.clone().add(0, 1, 0), k.radius * 0.8, Color.WHITE);
                        kr.rpgcraft.util.Vfx.ring(at, k.radius * 1.5, wv(c));
                        for (int q = 0; q < 4; q++) kr.rpgcraft.util.Vfx.slash(at.clone().add(0, 1, 0), new Vector(1, 0, 0), k.radius * 1.4, q * 45, q % 2 == 0 ? c : Color.WHITE);
                        w.spawnParticle(Particle.FLASH, at.clone().add(0, 1, 0), 1);
                        w.spawnParticle(Particle.EXPLOSION_HUGE, at, 1);
                        w.spawnParticle(Particle.LAVA, at, 20, k.radius * 0.5, 0.3, k.radius * 0.5, 0.1);
                        scorch(at, k.radius * 0.7, a.def.id);
                        BossFx.quake(at, k.radius * 1.2, mat);
                        BossFx.debris(at, 12, mat, 2.5);
                        w.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.5f);
                        w.playSound(at, Sound.ITEM_TRIDENT_THUNDER, 1f, 0.6f);
                        hurt(b, playersNear(at, k.radius * 1.25), dmg, null);
                    });
                }
            }
            case "SUMMON" -> {   // 소환 의식: 룬 마법진 + 빛기둥 여섯 개 → 기둥에서 부하가 솟아남
                EntityType type;
                try {
                    type = EntityType.valueOf(k.entity == null ? "ZOMBIE" : k.entity);
                } catch (IllegalArgumentException ex) {
                    type = EntityType.ZOMBIE;
                }
                EntityType ft = type;
                int lv = k.level > 0 ? k.level : Math.max(1, a.def.level - 10);
                Location o = ground(b);
                for (int t = 0; t < 3; t++) {
                    int tt = t;
                    later(t * 5L, () -> { kr.rpgcraft.util.Vfx.ring(o, 7 - tt, tt % 2 == 0 ? c : Color.WHITE); dustCircle(o, 5.5, c, 1.4f); });
                }
                for (int i = 0; i < 6; i++) {
                    double ang = i * Math.PI / 3;
                    Location pl = o.clone().add(Math.cos(ang) * 5.5, 0, Math.sin(ang) * 5.5);
                    kr.rpgcraft.util.Vfx.beam(pl, pl.clone().add(0, 7, 0), 0.8, c);
                    w.spawnParticle(tp, pl.clone().add(0, 1, 0), 10, 0.2, 1.5, 0.2, 0.02);
                }
                w.playSound(o, Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1.6f, 0.7f);
                w.playSound(o, Sound.BLOCK_END_PORTAL_FRAME_FILL, 1.2f, 0.6f);
                for (int i = 0; i < k.amount + (aw ? 1 : 0); i++) {
                    Location l = ground(b).add(ThreadLocalRandom.current().nextDouble(-5, 5), 0.5, ThreadLocalRandom.current().nextDouble(-5, 5));
                    later(6, () -> {
                        telegraph(l.clone().subtract(0, 0.5, 0), 1.5, 8, c);
                        kr.rpgcraft.util.Vfx.beam(l, l.clone().add(0, 10, 0), 1.4, c);
                    });
                    later(14 + i * 3L, () -> {
                        if (!b.isValid()) return;
                        kr.rpgcraft.util.Vfx.burst(l.clone().add(0, 1, 0), 2.6, c);
                        w.spawnParticle(Particle.REVERSE_PORTAL, l, 30, 0.3, 1, 0.3, 0.1);
                        Entity raw = w.spawnEntity(l, ft);
                        if (!(raw instanceof LivingEntity m)) { raw.remove(); return; }
                        m.getPersistentDataContainer().set(Keys.MINION, PersistentDataType.STRING, a.def.id);
                        m.setRemoveWhenFarAway(true);
                        plugin.mobs().initCustom(m, lv, plugin.mobs().hpFor(lv), plugin.mobs().damageFor(lv), Math.min(40, lv * 0.2),
                                (long) (plugin.mobs().expFor(lv) * plugin.getConfig().getDouble("bosses.minion-exp-mult", 0.05)),   // v5.9.6: 보스 부하 경험치 40% → 5% (보스 레벨 기준이라 많이 줬음)
                                lv * 100L, k.name == null ? MobManager.korean(ft) : k.name);
                        if (!k.ai) m.setAI(false);
                        if (m instanceof Mob mob) mob.setTarget(target);
                    });
                }
            }
            case "PULL" -> {   // 심연의 소용돌이: 중심에 위험 원이 차오르는 동안 나선으로 빨아들임 → 한가운데서 내파 (밖으로 버텨서 벗어나야 함)
                Location o = ground(b);
                telegraph(o, 4, 24, red);
                w.playSound(o, Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.4f, 0.7f);
                for (int st = 0; st < 6; st++) {
                    int stt = st;
                    later(st * 4L, () -> {
                        double rr = k.radius * (1 - stt * 0.15);
                        kr.rpgcraft.util.Vfx.ring(o, rr, stt % 2 == 0 ? c : Color.WHITE);
                        for (int i = 0; i < 12; i++) {   // 나선 기류
                            double ang = stt * 0.6 + i * Math.PI / 6;
                            dustAt(o.clone().add(Math.cos(ang) * rr, 0.6 + (i % 3) * 0.5, Math.sin(ang) * rr), c, 1.3f);
                        }
                        w.spawnParticle(Particle.REVERSE_PORTAL, o.clone().add(0, 1, 0), 20, rr * 0.5, 0.5, rr * 0.5, 0.05);
                        for (Player p : playersNear(o, k.radius)) {
                            Vector v = o.toVector().subtract(p.getLocation().toVector());
                            if (v.lengthSquared() > 1) p.setVelocity(v.normalize().multiply(0.7).setY(0.15));
                        }
                    });
                }
                later(24, () -> {
                    if (!b.isValid()) return;
                    kr.rpgcraft.util.Vfx.burst(o.clone().add(0, 1, 0), 6, c);
                    kr.rpgcraft.util.Vfx.burst(o.clone().add(0, 1, 0), 3, Color.WHITE);
                    kr.rpgcraft.util.Vfx.ring(o, 5, wv(c));
                    w.spawnParticle(Particle.FLASH, o.clone().add(0, 1, 0), 1);
                    w.spawnParticle(Particle.SONIC_BOOM, o.clone().add(0, 1, 0), 1);
                    BossFx.boom(o, 5, mat);
                    w.playSound(o, Sound.ENTITY_GENERIC_EXPLODE, 1.6f, 0.7f);
                    hurt(b, playersNear(o, 4), dmg, null);
                });
            }
            case "BLINK" -> {   // 그림자 습격: 대상에게 표식(!) + 돌진 경로 예고 → 잔상을 남기며 뒤로 순간이동해 X 베기 (각성: 두 번)
                for (int rep = 0; rep < (aw ? 2 : 1); rep++) {
                    later(rep * 22L, () -> {
                        if (!b.isValid() || !target.isOnline()) return;
                        markTarget(target, 12, red);
                        Vector path = target.getLocation().toVector().subtract(ground(b).toVector()).setY(0);
                        if (path.lengthSquared() > 0.01) telegraphLine(ground(b), path, path.length() + 2, 1.6, 12, red);
                        w.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.5f);
                    });
                    later(rep * 22L + 12, () -> {
                        if (!b.isValid() || !target.isOnline()) return;
                        Location from = ground(b);
                        Location behind = target.getLocation().clone().subtract(target.getLocation().getDirection().setY(0).normalize().multiply(2));
                        for (int g = 1; g <= 4; g++) {   // 잔상
                            Location ghost = from.clone().add(behind.toVector().subtract(from.toVector()).multiply(g / 5.0)).add(0, 1, 0);
                            kr.rpgcraft.util.Vfx.burst(ghost, 2.2, Vfx2.light(c, g * 0.15));
                            w.spawnParticle(Particle.SMOKE_LARGE, ghost, 4, 0.2, 0.4, 0.2, 0.01);
                        }
                        kr.rpgcraft.util.Vfx.beam(from.clone().add(0, 1, 0), behind.clone().add(0, 1, 0), 1.6, c);
                        if (plugin.bossModels() != null) plugin.bossModels().teleportBoss(b, behind); else b.teleport(behind);   // 모델을 태운 채로는 순간이동이 안 됨
                        BossFx.debris(behind, 6, mat, 1.4);
                        Vector f = target.getLocation().toVector().subtract(behind.toVector()).setY(0);
                        if (f.lengthSquared() < 0.01) f = new Vector(1, 0, 0);
                        kr.rpgcraft.util.Vfx.slash(target.getLocation().add(0, 1, 0), f, 4.5, 45, c);
                        kr.rpgcraft.util.Vfx.slash(target.getLocation().add(0, 1, 0), f, 4.5, -45, Color.WHITE);
                        w.spawnParticle(Particle.SWEEP_ATTACK, target.getLocation().add(0, 1, 0), 3, 0.4, 0.3, 0.4, 0);
                        w.playSound(behind, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.6f);
                        w.playSound(behind, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, 0.6f);
                        plugin.combat().mobSkillDamage(b, target, dmg);
                    });
                }
            }
            default -> {
            }
        }
    }

    /** v5.6.0: 기술 예고(선딜)가 끝나 실제로 터지는 시점 (틱) — 이 뒤에 경직 */
    private static long windup(BossDefinition.Skill k, boolean aw) {
        return switch (k.type) {
            case "CHARGE" -> 30;
            case "NOVA" -> 38;
            case "ERUPTION" -> 22 + 26L * (Math.max(1, k.amount) + (aw ? 1 : 0) - 1);
            case "CROSS" -> 22;
            case "FROST_FIELD", "VOLLEY", "FIREBALL" -> 16;
            case "ROAR", "SUMMON" -> 14;
            case "SLAM" -> aw ? 48 : 18;
            case "METEOR", "PULL", "WAVE_WALL" -> 24;
            case "BLINK" -> aw ? 34 : 12;
            case "BACKSTEP_VOLLEY" -> 32;
            case "CHECKER" -> 52;
            case "SAFE_ZONE" -> aw ? 32 : 40;
            case "DONUT" -> 48;
            case "SWEEP" -> 22 + (aw ? 30 : 40);
            case "GUST" -> 22;
            case "FRONT_BACK" -> 42;
            case "SPREAD" -> 30;
            default -> 0;
        };
    }

    /** v5.6.0 경직: 잠시 멈춰 서서 공격도 이동도 못 함 (머리 위에 별이 돎) — 이때가 공격 기회 */
    private void stagger(Active a) {
        LivingEntity b = a.entity;
        int ticks = (int) Math.round(plugin.getConfig().getInt("bosses.stagger-ticks", 0) * TIER_STAGGER[tier(a) - 1]);   // v5.7.0: 높은 보스일수록 짧게
        if (b == null || !b.isValid() || b.isDead() || ticks <= 0) return;
        a.staggerUntil = System.currentTimeMillis() + ticks * 50L;
        if (b instanceof Mob mob) { mob.setTarget(null); b.setAI(false); }
        b.getWorld().playSound(ground(b), Sound.ENTITY_IRON_GOLEM_DAMAGE, 1.2f, 0.6f);
        for (int t = 0; t < ticks; t += 3) {
            int tt = t;
            later(t, () -> {
                if (!b.isValid()) return;
                Location h = b.getLocation().add(0, b.getHeight() + 0.5, 0);
                for (int i = 0; i < 5; i++) {
                    double ang = tt * 0.35 + i * Math.PI * 2 / 5;
                    dustAt(h.clone().add(Math.cos(ang) * 0.9, 0, Math.sin(ang) * 0.9), Color.fromRGB(0xFFE14A), 1.3f);
                }
            });
        }
        for (Player p : a.bar.getPlayers()) if (p.getLocation().distanceSquared(ground(b)) < 30 * 30) Text.actionBar(p, "&e&l✦ 보스 경직! &7지금이 공격 기회");
        later(ticks, () -> { if (b.isValid() && System.currentTimeMillis() >= a.staggerUntil - 60) b.setAI(true); });
    }

    /** v5.6.0: 곧게 날아가는 투사체 (유도 없음). 처음 맞은 사람에게 터짐 */
    private void shootStraight(Active a, Vector dir, double speed, double range, double dmg, double hitR, long delay, Color c, Particle tp, double aim) {
        LivingEntity b = a.entity;
        Vector flat = dir.clone().setY(0).normalize();
        new org.bukkit.scheduler.BukkitRunnable() {
            Location pos;
            Vector v;
            double gone, len = range;

            @Override
            public void run() {
                if (!b.isValid()) { cancel(); return; }
                if (pos == null) {   // v5.10.8: 공중에 떠 있으면 몸에서 대상이 있던 땅 쪽으로 비스듬히 내리꽂음 (띠 끝 쪽은 땅에서 터짐)
                    Location g = ground(b);
                    if (airHeight(b) > 1.2 && aim > 0) {
                        pos = b.getLocation().add(0, b.getHeight() * 0.5, 0);
                        Location to = g.clone().add(flat.clone().multiply(aim)).add(0, 1.0, 0);
                        Vector d = to.toVector().subtract(pos.toVector());
                        len = d.length() + Math.max(0, range - aim);
                        v = d.normalize().multiply(Math.max(0.2, speed));
                    } else {
                        pos = g.add(0, 1.3, 0);
                        v = flat.clone().multiply(Math.max(0.2, speed));
                    }
                    kr.rpgcraft.util.Vfx.burst(pos, 1.6, Color.WHITE);
                }
                pos.add(v);
                gone += v.length();
                if (gone > len) { cancel(); return; }
                w().spawnParticle(tp, pos, 2, 0.08, 0.08, 0.08, 0.01);
                dustAt(pos, c, 1.7f);
                dustAt(pos.clone().subtract(v.clone().multiply(0.5)), Vfx2.light(c, 0.4), 1.2f);
                boolean hit = !pos.getBlock().isPassable();
                Player who = null;
                for (Player p : playersNear(pos, hitR)) { who = p; break; }
                if (!hit && who == null) return;
                cancel();
                kr.rpgcraft.util.Vfx.burst(pos, 2.2, c);
                w().playSound(pos, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.5f);
                if (who != null) plugin.combat().mobSkillDamage(b, who, dmg);
            }

            private World w() { return pos.getWorld(); }
        }.runTaskTimer(plugin, delay, 1L);
    }

    /** 사각 칸 예고 (바둑판 패턴) */
    private void telegraphSquare(Location c, double h, int ticks, Color col) {
        BossFx.square(c, h, ticks, glass(col));
        for (int t = 0; t <= ticks; t += 4) {
            int tt = t;
            later(t, () -> {
                double f = Math.max(0.15, tt / (double) ticks) * h;
                for (double s = -h; s <= h; s += 0.75) {
                    dustAt(c.clone().add(s, 0.12, -h), col, 1.1f); dustAt(c.clone().add(s, 0.12, h), col, 1.1f);
                    dustAt(c.clone().add(-h, 0.12, s), col, 1.1f); dustAt(c.clone().add(h, 0.12, s), col, 1.1f);
                }
                if (tt % 8 == 0) for (double s = -f; s <= f; s += 0.9) { dustAt(c.clone().add(s, 0.1, -f), Vfx2.light(col, 0.35), 0.9f); dustAt(c.clone().add(s, 0.1, f), Vfx2.light(col, 0.35), 0.9f); }
            });
        }
    }

    /** 고리 모양 예고 (안쪽 r 은 안전, r~R 이 위험) */
    private void telegraphRing(Location o, double r, double R, int ticks, Color col) {
        BossFx.ring(o, r, R, ticks, glass(col), col);
        for (int t = 0; t <= ticks; t += 2) {
            int tt = t;
            later(t, () -> {
                if (tt % 4 == 0) { dustCircle(o, R, col, 1.6f); dustCircle(o, r, col, 1.6f); }
                double f = Math.max(0.08, tt / (double) ticks);
                dustCircle(o, R - (R - r) * f, Vfx2.light(col, 0.35), 1.2f);
            });
        }
    }

    /** 부채꼴 예고 (half = 반각, 도) */
    private void telegraphCone(Location o, Vector dir, double R, double half, int ticks, Color col) {
        BossFx.cone(o, dir, R, half, ticks, glass(col), col);
        Vector d = dir.clone().setY(0).normalize();
        for (int t = 0; t <= ticks; t += 2) {
            int tt = t;
            later(t, () -> {
                double f = Math.max(0.1, tt / (double) ticks);
                for (double ang = -half; ang <= half; ang += Math.max(4, 240 / R / 2)) {
                    Vector dv = d.clone().rotateAroundY(Math.toRadians(ang));
                    if (tt % 4 == 0) dustAt(o.clone().add(dv.clone().multiply(R)).add(0, 0.12, 0), col, 1.5f);
                    dustAt(o.clone().add(dv.clone().multiply(R * f)).add(0, 0.12, 0), Vfx2.light(col, 0.35), 1.1f);
                }
                if (tt % 4 == 0) for (double s = 0; s <= R; s += 0.8)
                    for (double side : new double[]{-half, half}) dustAt(o.clone().add(d.clone().rotateAroundY(Math.toRadians(side)).multiply(s)).add(0, 0.12, 0), col, 1.3f);
            });
        }
    }

    // =================================================================== v5.7.0 보스 등급 (레벨이 높을수록 어렵게)
    /** 등급 1: Lv.40 미만 · 2: 80 미만 · 3: 140 미만 · 4: 그 이상. 월드 보스는 한 등급 위 */
    public int tier(Active a) {
        int lv = a.def.level;
        int t = lv < 40 ? 1 : lv < 80 ? 2 : lv < 140 ? 3 : 4;
        if (kr.rpgcraft.world.WorldBossManager.isWorldBoss(a.def.id)) t++;
        return Math.max(1, Math.min(4, t));
    }

    private static final double[] TIER_SPEED = {0.85, 1.0, 1.08, 1.15};   // 기술 빈도 (v5.9.7: 1.18/1.38 → 1.08/1.15)
    private static final double[] TIER_STAGGER = {1.5, 1.0, 0.7, 0.5};    // 경직 길이
    private static final double[] COMBO = {0.0, 0.12, 0.2, 0.3};           // 연계 확률 (v5.9.7: 0.35/0.55 → 0.2/0.3)

    /** v5.9.7 기술이 끝난 뒤 다음 기술까지 쉬는 시간 (ms). 높은 보스일수록 조금 짧지만 예고를 보고 피할 틈은 항상 남김 */
    private long castGap(Active a) {
        double base = plugin.getConfig().getDouble("bosses.cast-gap-seconds", 1.6);
        double[] mult = {1.3, 1.1, 1.0, 0.9};
        return (long) (base * 1000 * mult[tier(a) - 1] / (a.phase == 3 ? 1.15 : 1));
    }

    /** 이 기술과 다른 기술 하나 (소환 제외) */
    private BossDefinition.Skill otherSkill(Active a, BossDefinition.Skill not) {
        List<BossDefinition.Skill> pool = new ArrayList<>();
        for (BossDefinition.Skill x : a.def.skills) if (!x.type.equals(not.type) && !"SUMMON".equals(x.type)) pool.add(x);
        return pool.isEmpty() ? null : pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }

    private static String skillLabel(String type) {
        return switch (type) {
            case "SLAM" -> "대지 강타"; case "METEOR" -> "낙하 폭발"; case "PULL" -> "소용돌이"; case "BLINK" -> "그림자 습격";
            case "CHARGE" -> "돌진"; case "NOVA" -> "파동"; case "ERUPTION" -> "분출"; case "CROSS" -> "십자 베기"; case "FROST_FIELD" -> "서리 장판";
            case "ROAR" -> "포효"; case "VOLLEY", "FIREBALL" -> "일제 사격"; case "BACKSTEP_VOLLEY" -> "백스텝 사격"; case "CHECKER" -> "바둑판 폭발";
            case "SAFE_ZONE" -> "안전지대"; case "DONUT" -> "안팎 교대"; case "SWEEP" -> "회전 베기"; case "WAVE_WALL" -> "밀려오는 벽";
            case "GUST" -> "힘껏 밀기"; case "FRONT_BACK" -> "앞뒤 베기"; case "SPREAD" -> "낙뢰 표식";
            default -> type;
        };
    }

    /** 궁극기 발동 조건: 등급 3 은 체력 50%, 등급 4 는 75 · 45 · 15% + 50초마다 */
    private void ultimateCheck(Active a, MobManager.MobState s, double frac, Player target, long now) {
        int tr = tier(a);
        if (tr < 3 || !plugin.getConfig().getBoolean("bosses.ultimates", true)) return;
        int[] th = tr >= 4 ? new int[]{75, 45, 15} : new int[]{50};
        Integer hit = null;
        for (int t : th) if (frac * 100 <= t && !a.ultDone.contains(t)) { hit = t; break; }
        if (hit != null) a.ultDone.add(hit);
        else if (!(tr >= 4 && a.nextUlt > 0 && now >= a.nextUlt)) {
            if (tr >= 4 && a.nextUlt == 0) a.nextUlt = now + 50_000;
            return;
        }
        if (tr >= 4) a.nextUlt = now + 50_000;
        ultimate(a, s, target);
    }

    private static final String[] ULTS = {"DOOM", "COLLAPSE", "LASERS", "STORM"};

    /** 궁극기 4종 — 보스마다 순서가 다르게 돌아가며 사용 */
    private void ultimate(Active a, MobManager.MobState s, Player target) {
        LivingEntity b = a.entity;
        if (b == null || !b.isValid()) return;
        int first = Math.abs(a.def.id.hashCode()) % ULTS.length;
        String kind = ULTS[(first + a.ultDone.size() + (a.nextUlt > 0 ? (int) (a.nextUlt / 50_000 % 4) : 0)) % ULTS.length];
        Color c = theme(a.def.id), red = Color.fromRGB(0xFF2A2A), green = Color.fromRGB(0x5AFF7A);
        skyStrike(b, 20, c);   // v5.10.8
        Material mat = BossFx.theme(a.def.id);
        World w = b.getWorld();
        Location o = ground(b);
        Particle tp = themeParticle(a.def.id);
        double dmg = s.damage * 2.0;
        int dur = switch (kind) { case "DOOM" -> 170; case "COLLAPSE" -> 140; case "LASERS" -> 150; default -> 140; };
        a.ultUntil = System.currentTimeMillis() + dur * 50L;
        if (b instanceof Mob mob) { mob.setTarget(null); b.setAI(false); }   // 궁극기 동안 제자리
        later(dur, () -> { if (b.isValid() && System.currentTimeMillis() >= a.staggerUntil) b.setAI(true); });
        String name = switch (kind) { case "DOOM" -> "파멸의 주문"; case "COLLAPSE" -> "대붕괴"; case "LASERS" -> "심판의 광선"; default -> "운석 폭풍"; };
        String hint = switch (kind) { case "DOOM" -> "8초 안에 보호막을 부수세요! 못 부수면 전멸"; case "COLLAPSE" -> "무너지는 땅을 따라 이미 무너진 곳으로";
            case "LASERS" -> "도는 광선 사이 틈을 따라 움직이세요"; default -> "초록 안전지대를 따라 옮겨 다니세요"; };
        for (Player p : a.bar.getPlayers()) {
            p.sendTitle(Text.c("&4&l☠ " + name + " ☠"), Text.c("&c" + hint), 5, 50, 10);
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.2f, 0.6f);
        }
        kr.rpgcraft.util.Fx.helix(plugin, b, 5, 2.2, 40, c, Color.BLACK);
        BossFx.pillar(o, 1.6, 7, mat);
        switch (kind) {
            case "DOOM" -> {   // DPS 체크: 8초 동안 정해진 만큼 때려 보호막을 깨야 함
                int ch = 160;
                double need = s.maxHp * (tier(a) >= 4 ? 0.09 : 0.06) * (1 + 0.3 * Math.max(0, a.bar.getPlayers().size() - 1));
                double[] startHp = {plugin.mobs().state(b) == null ? s.hp : plugin.mobs().state(b).hp};
                boolean[] broken = {false};
                BossFx.disc(o, 24, ch, BossFx.RED, red);
                for (int t = 0; t <= ch; t += 5) {
                    int tt = t;
                    later(t, () -> {
                        if (!b.isValid() || broken[0]) return;
                        MobManager.MobState st = plugin.mobs().state(b);
                        double done = st == null ? 0 : Math.max(0, startHp[0] - st.hp);
                        double f = Math.min(1, done / need);
                        Location h = b.getLocation().add(0, b.getHeight() * 0.6, 0);
                        for (int i = 0; i < 14; i++) {   // 보호막 구체
                            double ang = tt * 0.2 + i * Math.PI / 7, y = Math.sin(tt * 0.1 + i) * 1.4;
                            dustAt(h.clone().add(Math.cos(ang) * 2.4, y, Math.sin(ang) * 2.4), f < 1 ? Color.fromRGB(0x8A2AFF) : Color.WHITE, 1.8f);
                        }
                        kr.rpgcraft.util.Vfx.beam(ground(b), ground(b).add(0, 14, 0), 1.2 + tt / 80.0, Color.fromRGB(0x5A0A8A));
                        int bars = (int) Math.round(f * 20);
                        for (Player p : a.bar.getPlayers())
                            Text.actionBar(p, "&5&l파멸의 주문 &f[" + "&d■".repeat(bars) + "&8" + "■".repeat(20 - bars) + "&f] &e" + (int) (f * 100) + "% &7- " + String.format("%.1f", (ch - tt) / 20.0) + "초");
                        if (f >= 1) {
                            broken[0] = true;
                            a.ultUntil = 0;
                            BossFx.boom(ground(b), 6, mat);
                            w.playSound(ground(b), Sound.BLOCK_GLASS_BREAK, 2f, 0.5f);
                            for (Player p : a.bar.getPlayers()) p.sendTitle(Text.c("&a&l보호막 파괴!"), Text.c("&f파멸의 주문을 막아냈습니다"), 0, 30, 8);
                            b.setAI(true);
                            a.staggerUntil = 0;
                            stagger(a);
                            later(20, () -> stagger(a));   // 두 배로 긴 경직
                        }
                    });
                }
                later(ch, () -> {
                    if (!b.isValid() || broken[0]) return;
                    Location at = ground(b);
                    for (int r = 3; r <= 24; r += 3) { int rr = r; later(r / 3, () -> { kr.rpgcraft.util.Vfx.ring(at, rr, rr % 6 == 0 ? c : Color.fromRGB(0x5A0A8A)); BossFx.debris(at, 6, mat, rr * 0.4); }); }
                    BossFx.boom(at, 16, mat);
                    w.playSound(at, Sound.ENTITY_WARDEN_SONIC_BOOM, 2f, 0.5f);
                    for (Player p : playersNear(at, 30)) {
                        plugin.combat().mobSkillDamage(b, p, dmg * 2.5);
                        p.setVelocity(p.getLocation().toVector().subtract(at.toVector()).setY(0).normalize().multiply(1.6).setY(0.8));
                    }
                    for (Player p : a.bar.getPlayers()) p.sendTitle(Text.c("&4&l파멸"), Text.c("&c보호막을 깨지 못했습니다"), 0, 30, 8);
                });
            }
            case "COLLAPSE" -> {   // 대붕괴: 땅이 나선을 그리며 한 칸씩 무너짐 — 이미 무너진 칸 뒤를 따라가야 함
                double cell = 4, R = 18;
                double base = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
                int sgn = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
                List<double[]> cells = new ArrayList<>();
                for (double x = -R; x <= R; x += cell) for (double z = -R; z <= R; z += cell) {
                    double d = Math.hypot(x, z);
                    if (d > R + 1) continue;
                    double ang = (Math.atan2(z, x) - base) * sgn;
                    ang = ((ang % (Math.PI * 2)) + Math.PI * 2) % (Math.PI * 2);
                    cells.add(new double[]{x, z, ang + d / R * 0.6});   // 각도 순서 + 바깥은 살짝 늦게 → 나선
                }
                cells.sort(Comparator.comparingDouble(v -> v[2]));
                int step = 2, warn = 18;
                for (int i = 0; i < cells.size(); i++) {
                    double[] cl = cells.get(i);
                    Location at = o.clone().add(cl[0], 0, cl[1]);
                    long t0 = 20 + (long) i * step;
                    later(t0 - warn, () -> telegraphSquare(at, cell / 2 - 0.1, warn, red));
                    later(t0, () -> {
                        if (!b.isValid()) return;
                        BossFx.quake(at, cell * 0.5, mat);
                        w.spawnParticle(Particle.EXPLOSION_LARGE, at.clone().add(0, 0.5, 0), 1);
                        if (ThreadLocalRandom.current().nextInt(4) == 0) w.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.8f);
                        for (Player p : playersNear(at, cell)) {
                            Location pl = p.getLocation();
                            if (Math.abs(pl.getX() - at.getX()) <= cell / 2 && Math.abs(pl.getZ() - at.getZ()) <= cell / 2) {
                                plugin.combat().mobSkillDamage(b, p, dmg * 0.9);
                                p.setVelocity(new Vector(0, 0.9, 0));
                            }
                        }
                    });
                }
            }
            case "LASERS" -> {   // 심판의 광선: 보스에게서 뻗은 광선들이 천천히 돌고(최상위는 도중에 방향을 바꿈), 주기적으로 파동
                int n = tier(a) >= 4 ? 4 : 3;
                double L = 22, start = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
                int sgn = ThreadLocalRandom.current().nextBoolean() ? 1 : -1, warn = 24, spin = 120;
                for (int i = 0; i < n; i++) telegraphLine(o, new Vector(Math.cos(start + i * Math.PI * 2 / n), 0, Math.sin(start + i * Math.PI * 2 / n)), L, 2.4, warn, red);
                BossFx.disc(o, 2.2, warn + spin, BossFx.SAFE, green);
                Map<UUID, Long> hitAt = new HashMap<>();
                for (int t = 0; t <= spin; t += 2) {
                    int tt = t;
                    later(warn + t, () -> {
                        if (!b.isValid()) return;
                        double prog = tier(a) >= 4 && tt > spin / 2 ? (spin - tt) : tt;   // 최상위: 절반쯤에서 반대로
                        double rot = start + sgn * Math.PI * prog / spin;
                        for (int i = 0; i < n; i++) {
                            double ang = rot + i * Math.PI * 2 / n;
                            Vector dv = new Vector(Math.cos(ang), 0, Math.sin(ang));
                            Location from = o.clone().add(dv.clone().multiply(2.2)).add(0, 1, 0), to = o.clone().add(dv.clone().multiply(L)).add(0, 1, 0);
                            kr.rpgcraft.util.Vfx.beam(from, to, 1.8, c);
                            if (tt % 4 == 0) kr.rpgcraft.util.Vfx.beam(from, to, 0.7, Color.WHITE);
                            if (tt % 8 == 0) BossFx.debris(o.clone().add(dv.clone().multiply(L * 0.8)), 2, mat, 1.2);
                            for (Player p : playersNear(o, L + 1)) {
                                double d = Math.hypot(p.getLocation().getX() - o.getX(), p.getLocation().getZ() - o.getZ());
                                if (d <= 2.2 || distToLine(o, dv, L, p.getLocation()) > 1.4) continue;
                                long last = hitAt.getOrDefault(p.getUniqueId(), 0L);
                                if (System.currentTimeMillis() - last < 600) continue;
                                hitAt.put(p.getUniqueId(), System.currentTimeMillis());
                                plugin.combat().mobSkillDamage(b, p, dmg * 0.6);
                            }
                        }
                        if (tt % 10 == 0) w.playSound(o, Sound.BLOCK_BEACON_AMBIENT, 1.5f, 1.6f);
                    });
                }
                if (tier(a) >= 4) for (int pulse = 1; pulse <= 3; pulse++) {   // 파동: 광선 사이를 달리다 점프로 넘기
                    long at = warn + pulse * 36L;
                    later(at - 12, () -> telegraphRing(o, 2.2, L, 12, Color.fromRGB(0xFF9A1A)));
                    for (int r = 3; r <= (int) L; r += 2) {
                        int rr = r;
                        later(at + r / 2, () -> {
                            kr.rpgcraft.util.Vfx.ring(o, rr, Color.fromRGB(0xFF9A1A));
                            for (Player p : playersNear(o, rr + 1)) {
                                double d = Math.hypot(p.getLocation().getX() - o.getX(), p.getLocation().getZ() - o.getZ());
                                if (Math.abs(d - rr) <= 1 && p.isOnGround()) plugin.combat().mobSkillDamage(b, p, dmg * 0.4);
                            }
                        });
                    }
                }
            }
            default -> {   // 운석 폭풍: 4번 몰아치는 운석 — 매번 안전지대(초록)가 옮겨 가고, 다음 자리가 주황으로 미리 보임
                double R = 18, sr = 3.2;
                List<Location> safes = new ArrayList<>();
                for (int i = 0; i < 4; i++) {
                    double ang = ThreadLocalRandom.current().nextDouble(Math.PI * 2), d = ThreadLocalRandom.current().nextDouble(5, 12);
                    safes.add(o.clone().add(Math.cos(ang) * d, 0, Math.sin(ang) * d));
                }
                for (int wv = 0; wv < 4; wv++) {
                    int ww = wv;
                    long at = 34 + wv * 30L;
                    Location safe = safes.get(wv);
                    later(at - 34, () -> {
                        telegraph(o, R, 34, red);
                        BossFx.disc(safe, sr, 34, BossFx.SAFE, green);
                        kr.rpgcraft.util.Vfx.beam(safe, safe.clone().add(0, 12, 0), 1.3, green);
                        if (ww + 1 < safes.size()) BossFx.disc(safes.get(ww + 1), sr, 34, BossFx.ORANGE, Color.fromRGB(0xFF9A1A));   // 다음 자리 미리보기
                        for (int m = 0; m < 6; m++) {
                            Location mt = o.clone().add(ThreadLocalRandom.current().nextDouble(-R, R), 0, ThreadLocalRandom.current().nextDouble(-R, R));
                            if (mt.distanceSquared(safe) < sr * sr * 2) continue;
                            BossFx.meteor(mt, 1.6 + ThreadLocalRandom.current().nextDouble(1.4), 30, mat);
                        }
                    });
                    later(at, () -> {
                        if (!b.isValid()) return;
                        BossFx.boom(o, 12, mat);
                        w.playSound(o, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.5f);
                        for (Player p : playersNear(o, R)) {
                            if (p.getLocation().distanceSquared(safe) <= (sr + 0.4) * (sr + 0.4)) continue;
                            plugin.combat().mobSkillDamage(b, p, dmg * 0.9);
                        }
                    });
                }
            }
        }
    }

    private static Color wv(Color c) {
        return Color.fromRGB(Math.min(255, c.getRed() + 60), Math.min(255, c.getGreen() + 60), Math.min(255, c.getBlue() + 60));
    }

    /** 고유 대형 패턴 (레벨 80 이상 보스, 각성하면 더 자주) — 레이드 기믹 6종 */
    private void signature(Active a, Player target, MobManager.MobState s) {
        LivingEntity b = a.entity;
        World w = b.getWorld();
        Color c = theme(a.def.id);
        Color red = Color.fromRGB(0xFF2A2A), green = Color.fromRGB(0x5AFF7A);
        double dmg = s.damage * 1.6;
        skyStrike(b, 20, c);   // v5.10.8
        Location o = ground(b);
        Particle tp = themeParticle(a.def.id);
        Material mat = BossFx.theme(a.def.id);
        int pick = ThreadLocalRandom.current().nextInt(6);
        if (plugin.bossModels() != null) plugin.bossModels().attackPose(b);
        long[] sigWind = {22, 30, 46, 10, 60, 40};
        later(sigWind[pick], () -> stagger(a));   // v5.6.0: 대형 패턴도 끝나면 경직
        switch (pick) {
            case 0 -> {   // 십자 광선: 붉은 띠가 차오른 뒤 네 방향 광선 (각성: 8방향)
                int dirs = s.awakened ? 8 : 4;
                double L = 18;
                charge(b, 22, c);
                for (int d = 0; d < dirs; d++) {
                    double ang = d * Math.PI * 2 / dirs;
                    telegraphLine(o, new Vector(Math.cos(ang), 0, Math.sin(ang)), L, 3.6, 22, red);
                }
                w.playSound(o, Sound.BLOCK_BEACON_POWER_SELECT, 1.4f, 0.6f);
                later(22, () -> {
                    if (!b.isValid()) return;
                    Set<UUID> once = new HashSet<>();
                    for (int d = 0; d < dirs; d++) {
                        double ang = d * Math.PI * 2 / dirs;
                        Vector dv = new Vector(Math.cos(ang), 0, Math.sin(ang));
                        Location end = o.clone().add(dv.clone().multiply(L)).add(0, 1, 0);
                        kr.rpgcraft.util.Vfx.beam(o.clone().add(0, 1, 0), end, 3.4, c);
                        kr.rpgcraft.util.Vfx.beam(o.clone().add(0, 1, 0), end, 1.3, Color.WHITE);
                        later(3, () -> kr.rpgcraft.util.Vfx.beam(o.clone().add(0, 1, 0), end, 2.4, c));
                        kr.rpgcraft.util.Vfx.burst(end, 3, c);
                        for (double t = 2; t < L; t += 3) w.spawnParticle(tp, o.clone().add(dv.clone().multiply(t)).add(0, 1, 0), 3, 0.3, 0.3, 0.3, 0.02);
                        for (Player p : playersNear(o, L + 1)) {
                            Vector to = p.getLocation().toVector().subtract(o.toVector()).setY(0);
                            double along = to.dot(dv);
                            if (along > 0 && along < L && to.clone().subtract(dv.clone().multiply(along)).length() < 1.8 && once.add(p.getUniqueId()))
                                plugin.combat().mobSkillDamage(b, p, dmg);
                        }
                    }
                    w.spawnParticle(Particle.SONIC_BOOM, o.clone().add(0, 1, 0), 1);
                    w.playSound(o, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.6f, 0.8f);
                });
                announce(a, "&c십자 광선! &7붉은 띠에서 벗어나세요");
            }
            case 1 -> {   // 파멸의 고리: 보스 곁 초록 원만 안전, 바깥 전체가 폭발
                double safe = 4, outer = 16;
                telegraph(o, outer, 30, red);
                for (int t = 0; t <= 30; t += 4) later(t, () -> dustCircle(o, safe, green, 1.8f));
                BossFx.disc(o, safe, 30, BossFx.SAFE, green);
                charge(b, 30, c);
                w.playSound(o, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.6f, 0.6f);
                later(30, () -> {
                    if (!b.isValid()) return;
                    for (double r = safe + 2; r <= outer; r += 3) {
                        double rr = r;
                        later((long) ((r - safe) / 3), () -> { kr.rpgcraft.util.Vfx.ring(o, rr, c); kr.rpgcraft.util.Vfx.ring(o, rr - 0.8, Color.WHITE); w.spawnParticle(tp, o, 20, rr * 0.6, 0.3, rr * 0.6, 0.05); });
                    }
                    for (Player p : playersNear(o, outer)) if (p.getLocation().distance(o) > safe) plugin.combat().mobSkillDamage(b, p, dmg * 1.2);
                    BossFx.quake(o, outer, mat);
                    BossFx.debris(o, 20, mat, 5);
                    kr.rpgcraft.util.Vfx.burst(o.clone().add(0, 2, 0), 9, c);
                    w.spawnParticle(Particle.EXPLOSION_HUGE, o, 3, 5, 0.5, 5);
                    w.playSound(o, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.5f);
                });
                announce(a, "&a파멸의 고리! &7보스 곁 초록 원 안으로");
            }
            case 2 -> {   // 유성 폭격: 전장 곳곳에 차오르는 원 10~16개 → 차례로 유성 낙하
                int n = s.awakened ? 16 : 10;
                w.playSound(o, Sound.ENTITY_WITHER_SHOOT, 1.4f, 0.5f);
                for (int i = 0; i < n; i++) {
                    Location at = o.clone().add(ThreadLocalRandom.current().nextDouble(-14, 14), 0, ThreadLocalRandom.current().nextDouble(-14, 14));
                    if (i < 3 && target.isOnline()) at = target.getLocation().clone().add(ThreadLocalRandom.current().nextDouble(-2, 2), 0, ThreadLocalRandom.current().nextDouble(-2, 2));
                    Location fat = at;
                    long delay = i * 3L;
                    later(delay, () -> { telegraph(fat, 3, 16, red); BossFx.meteor(fat, 1.8, 16, mat); });
                    later(delay + 16, () -> {
                        if (!b.isValid()) return;
                        kr.rpgcraft.util.Vfx.beam(fat.clone().add(-3, 18, -2), fat, 1.8, c);
                        kr.rpgcraft.util.Vfx.burst(fat.clone().add(0, 0.8, 0), 3.6, c);
                        w.spawnParticle(Particle.LAVA, fat, 8, 1, 0.2, 1, 0.1);
                        BossFx.quake(fat, 3, mat);
                        w.spawnParticle(Particle.EXPLOSION_LARGE, fat, 1);
                        w.playSound(fat, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.9f);
                        hurt(b, playersNear(fat, 3), dmg * 0.7, null);
                    });
                }
                announce(a, "&6유성 폭격! &7차오르는 원을 피하세요");
            }
            case 3 -> {   // 칼날 폭풍: 보스 주위를 도는 참격 3바퀴 (가까울수록 위험)
                telegraph(o, 9, 10, red);
                w.playSound(o, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.6f, 0.5f);
                Set<UUID> once = new HashSet<>();
                for (int t = 0; t < 18; t++) {
                    int tt = t;
                    later(10 + t * 2L, () -> {
                        if (!b.isValid()) return;
                        double ang = tt * Math.PI / 3;
                        Vector f = new Vector(Math.cos(ang), 0, Math.sin(ang));
                        Location at = ground(b).add(f.clone().multiply(5)).add(0, 1, 0);
                        kr.rpgcraft.util.Vfx.slash(at, f, 7.5, tt % 2 == 0 ? 30 : -30, tt % 3 == 0 ? Color.WHITE : c);
                        w.spawnParticle(Particle.SWEEP_ATTACK, at, 2, 1, 0.3, 1, 0);
                        if (tt % 3 == 0) w.playSound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.7f + tt * 0.02f);
                        for (Player p : playersNear(at, 4)) if (once.add(p.getUniqueId())) {
                            plugin.combat().mobSkillDamage(b, p, dmg * 0.8);
                            p.setVelocity(f.clone().multiply(0.8).setY(0.4));
                        }
                        if (tt % 6 == 5) once.clear();   // 한 바퀴마다 다시 맞을 수 있음
                    });
                }
                announce(a, "&c칼날 폭풍! &7멀리 떨어지세요");
            }
            case 4 -> {   // 심판의 낙인: 최대 3명에게 낙인 → 3초 뒤 그 자리 주변 폭발 (다른 사람과 흩어지세요)
                List<Player> ps = new ArrayList<>(playersNear(o, 30));
                Collections.shuffle(ps);
                int n = Math.min(ps.size(), s.awakened ? 3 : 2);
                double R = 4.5;
                for (int i = 0; i < n; i++) {
                    Player p = ps.get(i);
                    markTarget(p, 60, c);
                    for (int t = 0; t < 60; t += 4) {
                        int tt = t;
                        later(t, () -> { if (p.isOnline()) dustCircle(p.getLocation(), R, tt > 44 ? red : c, 1.4f); });
                    }
                    p.sendTitle(Text.c("&4&l낙인"), Text.c("&c다른 사람에게서 떨어지세요!"), 0, 40, 10);
                    p.playSound(p.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1f, 1f);
                    later(60, () -> {
                        if (!b.isValid() || !p.isOnline()) return;
                        Location at = p.getLocation();
                        kr.rpgcraft.util.Vfx.beam(at.clone().add(0, 20, 0), at, 2.4, c);
                        BossFx.pillar(at, R * 0.4, 6, mat);
                        kr.rpgcraft.util.Vfx.burst(at.clone().add(0, 1, 0), R * 1.3, c);
                        kr.rpgcraft.util.Vfx.ring(at, R, Color.WHITE);
                        w.spawnParticle(Particle.FLASH, at.clone().add(0, 1, 0), 1);
                        w.spawnParticle(tp, at, 30, R * 0.5, 0.5, R * 0.5, 0.05);
                        w.playSound(at, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.2f, 0.8f);
                        hurt(b, playersNear(at, R), dmg * 0.9, null);
                    });
                }
                announce(a, "&5심판의 낙인! &7낙인 찍힌 사람은 흩어지세요");
            }
            default -> {   // 안전지대: 전장 전체가 폭발, 초록 원 3곳만 안전
                double R = 18;
                List<Location> safes = new ArrayList<>();
                for (int i = 0; i < 3; i++) {
                    double ang = ThreadLocalRandom.current().nextDouble(Math.PI * 2), d = ThreadLocalRandom.current().nextDouble(6, 13);
                    safes.add(o.clone().add(Math.cos(ang) * d, 0, Math.sin(ang) * d));
                }
                telegraph(o, R, 40, red);
                for (int t = 0; t <= 40; t += 3) later(t, () -> { for (Location sf : safes) { dustCircle(sf, 3, green, 1.8f); w.spawnParticle(Particle.VILLAGER_HAPPY, sf.clone().add(0, 0.5, 0), 3, 1, 0.2, 1, 0); } });
                for (Location sf : safes) kr.rpgcraft.util.Vfx.beam(sf, sf.clone().add(0, 12, 0), 1.2, green);
                for (Location sf : safes) BossFx.disc(sf, 3, 40, BossFx.SAFE, green);
                charge(b, 40, c);
                w.playSound(o, Sound.ENTITY_WITHER_SPAWN, 1f, 0.8f);
                later(40, () -> {
                    if (!b.isValid()) return;
                    for (int k2 = 0; k2 < 8; k2++) {
                        Location at = o.clone().add(ThreadLocalRandom.current().nextDouble(-R, R), 0, ThreadLocalRandom.current().nextDouble(-R, R));
                        later(k2, () -> { kr.rpgcraft.util.Vfx.burst(at.clone().add(0, 1, 0), 5, c); w.spawnParticle(Particle.EXPLOSION_HUGE, at, 1); });
                    }
                    kr.rpgcraft.util.Vfx.ring(o, R, wv(c));
                    BossFx.boom(o, 14, mat);
                    w.playSound(o, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.4f);
                    for (Player p : playersNear(o, R)) {
                        boolean ok = false;
                        for (Location sf : safes) if (p.getLocation().distanceSquared(sf) <= 3.2 * 3.2) { ok = true; break; }
                        if (!ok) plugin.combat().mobSkillDamage(b, p, dmg * 1.1);
                    }
                });
                announce(a, "&a안전지대! &7초록 원 안으로 들어가세요");
            }
        }
    }

    private void announce(Active a, String msg) {
        for (Player p : a.bar.getPlayers()) {
            p.sendTitle("", Text.c(msg), 0, 30, 8);
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 0.5f);
        }
    }

    // =================================================================== 보스 연출 (v5.1.0)
    private static final String[] PHASE_NAME = {"", "", "분노", "광폭화"};

    /** 보스바 제목: 리소스팩 모드면 금속 틀 글리프로 바를 감싸고 이름·체력·단계를 가운데에 */
    /** v5.10.49 보스 체력 표시: 리소스팩 → 오른쪽 위 보스 전용 판, 팩 없음 → 예전 보스바 */
    private void showBars(Active a, MobManager.MobState s, double frac, Set<Player> near) {
        boolean usePanel = plugin.getConfig().getBoolean("bosses.panel", true) && plugin.targetHud() != null;
        Set<Player> packs = new HashSet<>(), plains = new HashSet<>();
        for (Player p : near) (usePanel && plugin.pack().hasPack(p) ? packs : plains).add(p);
        for (Player p : new ArrayList<>(a.plainBar.getPlayers())) if (!plains.contains(p)) a.plainBar.removePlayer(p);
        for (Player p : new ArrayList<>(a.panelBar.getPlayers())) if (!packs.contains(p)) a.panelBar.removePlayer(p);
        if (!plains.isEmpty()) {
            String t = barTitle(a, s, frac);
            if (!t.equals(a.plainBar.getTitle())) a.plainBar.setTitle(t);
            a.plainBar.setProgress(frac);
            for (Player p : plains) a.plainBar.addPlayer(p);
        }
        if (!packs.isEmpty()) {
            String phase = a.phase >= 2 ? PHASE_NAME[a.phase] : "";
            String t = plugin.targetHud().bossPanel(a.entity, Text.strip(Text.c(s.baseName)), phase, Math.max(0, s.hp), s.maxHp);
            if (!t.equals(a.panelBar.getTitle())) a.panelBar.setTitle(t);
            a.panelBar.setProgress(frac);
            for (Player p : packs) {
                a.panelBar.addPlayer(p);
                plugin.targetHud().suppress(p);   // 일반 적 정보와 겹치지 않게
            }
        }
    }

    private void clearBars(Active a) {
        a.bar.removeAll();
        if (a.plainBar != null) a.plainBar.removeAll();
        if (a.panelBar != null) a.panelBar.removeAll();
    }

    private String barTitle(Active a, MobManager.MobState s, double frac) {
        String phase = a.phase >= 2 ? (a.phase == 3 ? " &4&l" : " &6&l") + "[" + PHASE_NAME[a.phase] + "]" : "";
        String text = Text.c("&c&l☠ " + s.baseName + phase + " &f" + Text.num(s.hp) + " &7/ " + Text.num(s.maxHp) + " &8(" + String.format("%.1f", frac * 100) + "%)");
        if (!plugin.pack().overlay()) return text;
        int fw = 202, adv = fw + 1, n = kr.rpgcraft.pack.HudFont.textWidth(text);   // 틀 이미지 202px (tools/ui_pack.py FRAME_W)
        // 전체 폭 = 틀 폭 → 틀은 바 가운데, 이름은 그 위 가운데
        return "§f" + '\uE050' + kr.rpgcraft.pack.PackManager.shift(fw / 2 - n / 2 - adv) + text + kr.rpgcraft.pack.PackManager.shift(fw / 2 - (n - n / 2));
    }

    /** 체력 60% · 30% 에서 단계 전환: 공격력 · 기술 빈도 상승, 주변 밀쳐내기 + 연출 */
    private void phase(Active a, MobManager.MobState s, double frac) {
        if (s.awakened && !a.seenAwake) { a.seenAwake = true; a.phase = 1; }   // 각성하면 체력이 다시 차므로 단계도 처음부터
        int want = frac <= 0.3 ? 3 : frac <= 0.6 ? 2 : 1;
        if (want <= a.phase) return;
        a.phase = want;
        LivingEntity b = a.entity;
        s.damage *= want == 3 ? 1.15 : 1.12;
        var sp = b.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MOVEMENT_SPEED);
        if (sp != null && want == 3) sp.setBaseValue(sp.getBaseValue() * 1.2);
        Location o = ground(b);
        Color c = want == 3 ? Color.fromRGB(0xFF1A1A) : Color.fromRGB(0xFF9A1A);
        kr.rpgcraft.util.Fx.shockwave(plugin, o, 9, c);
        kr.rpgcraft.util.Fx.helix(plugin, b, 3.5, 1.4, 30, c, Color.BLACK);
        kr.rpgcraft.util.Vfx.burst(o.clone().add(0, 1.5, 0), 4, c);
        b.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, o, 4, 1.5, 0.5, 1.5);
        b.getWorld().playSound(o, want == 3 ? Sound.ENTITY_ENDER_DRAGON_GROWL : Sound.ENTITY_RAVAGER_ROAR, 2f, want == 3 ? 0.7f : 0.8f);
        for (Player p : playersNear(o, 7)) p.setVelocity(p.getLocation().toVector().subtract(o.toVector()).setY(0).normalize().multiply(1.1).setY(0.45));
        String msg = want == 3 ? "&4&l광폭화! &c" + s.baseName + "&7이(가) 이성을 잃었습니다" : "&6&l분노! &e" + s.baseName + "&7의 공격이 거세집니다";
        for (Player p : a.bar.getPlayers()) p.sendTitle(Text.c(want == 3 ? "&4&l광폭화" : "&6&l분노"), Text.c(msg), 5, 40, 12);
    }

    /** 보스 주변 기운: 발밑 회전 고리 + 단계가 오를수록 진해짐 (0.5초마다) */
    private void auraTick(Active a) {
        LivingEntity b = a.entity;
        Color c = a.phase == 3 ? Color.fromRGB(0xFF1A1A) : theme(a.def.id);
        Location o = ground(b);
        double r = Math.max(1.4, b.getWidth() * 0.9);
        int pts = 6 + a.phase * 3;
        a.aura += 0.5;
        // 잔상 제거 (v5.5.0): 발밑 가루 고리는 1초쯤 남아서 움직이면 자국이 줄줄이 생겼음 → 가만히 있을 때만
        if (b.getVelocity().setY(0).lengthSquared() > 0.0025 || (a.lastAura != null && a.lastAura.getWorld() == o.getWorld() && a.lastAura.distanceSquared(o) > 0.04)) pts = 0;
        a.lastAura = o.clone();
        for (int i = 0; i < pts; i++) {
            double ang = a.aura + Math.PI * 2 * i / pts;
            b.getWorld().spawnParticle(Particle.REDSTONE, o.clone().add(Math.cos(ang) * r, 0.15, Math.sin(ang) * r), 1, 0, 0, 0, 0,
                    new Particle.DustOptions(c, 1.4f));
        }
        if (a.phase >= 2) b.getWorld().spawnParticle(a.phase == 3 ? Particle.FLAME : Particle.SMOKE_NORMAL, o.clone().add(0, b.getHeight() * 0.6, 0), 3 * a.phase, r * 0.5, 0.6, r * 0.5, 0.01);
    }

    /** 등장 연출: 주변(64칸) 플레이어에게 경고 제목 + 번개 · 충격파 */
    private void intro(LivingEntity e, BossDefinition d) {
        Location o = e.getLocation();
        e.getWorld().strikeLightningEffect(o);
        kr.rpgcraft.util.Fx.shockwave(plugin, o, 12, theme(d.id));
        kr.rpgcraft.util.Vfx.beam(o.clone(), o.clone().add(0, 18, 0), 2.2, theme(d.id));
        for (Player p : o.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(o) > 64 * 64) continue;
            p.sendTitle(Text.c("&4&l⚠ 보스 출현 ⚠"), Text.c("&c&l" + d.name + " &7Lv." + d.level), 10, 50, 15);
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.8f, 0.6f);
        }
    }

    /** 토벌 연출: 빛기둥 + 폭죽 같은 폭발 + 참가자에게 제목 */
    private void victory(Active a) {
        LivingEntity b = a.entity;
        if (b == null) return;
        Location o = ground(b);
        Color c = theme(a.def.id);
        for (int k = 0; k < 4; k++) {
            int kk = k;
            later(k * 6L, () -> {
                kr.rpgcraft.util.Vfx.burst(o.clone().add(0, 1.5 + kk, 0), 3 + kk, kk % 2 == 0 ? c : Color.fromRGB(0xFFD23F));
                o.getWorld().spawnParticle(Particle.FIREWORKS_SPARK, o.clone().add(0, 2 + kk, 0), 40, 1.2, 1.2, 1.2, 0.15);
                o.getWorld().playSound(o, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.5f, 0.8f + kk * 0.1f);
            });
        }
        kr.rpgcraft.util.Vfx.beam(o.clone(), o.clone().add(0, 25, 0), 1.6, Color.fromRGB(0xFFD23F));
        for (Player p : a.bar.getPlayers()) {
            p.sendTitle(Text.c("&6&l토벌 성공!"), Text.c("&e" + a.def.name + " &7처치"), 5, 50, 15);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
    }

    private void ring(Location c, double r, Particle p) {
        for (int i = 0; i < 24; i++) {
            double ang = Math.PI * 2 * i / 24;
            c.getWorld().spawnParticle(p, c.clone().add(Math.cos(ang) * r, 0.2, Math.sin(ang) * r), 1, 0, 0, 0, 0);
        }
    }

    public void onBossDeath(LivingEntity e, MobManager.MobState s, EntityDeathEvent ev) {
        ev.getDrops().clear();
        Active a = active.remove(e.getUniqueId());
        if (a != null) {
            victory(a);
            clearBars(a);
        }
        BossDefinition d = defs.get(s.bossId);
        if (d == null) return;
        // v5.10.4: 보스 상자(아이템) 드롭 없앰 — 이미 가진 상자는 그대로 열 수 있음
        double total = s.contrib.values().stream().mapToDouble(Double::doubleValue).sum();
        double minShare = plugin.getConfig().getDouble("boss.min-contribution", 0.07);
        List<Map.Entry<UUID, Double>> ranking = new ArrayList<>(s.contrib.entrySet());
        ranking.sort((x, y) -> Double.compare(y.getValue(), x.getValue()));
        kr.rpgcraft.util.Text.announce(Text.PREFIX + Text.c("&c&l" + d.name + "&f이(가) 토벌되었습니다!"));
        // 경험치 분배 (v5.3.9): 한 명이 독식하지 않도록 절반은 참가자끼리 똑같이, 절반은 기여도대로 + 1인 상한
        var cfg = plugin.getConfig();
        double expMin = cfg.getDouble("boss.exp-min-contribution", 0.02), even = cfg.getDouble("boss.exp-even-share", 0.75), cap = cfg.getDouble("boss.exp-max-share", 0.4);
        Map<UUID, Double> expShare = new HashMap<>();
        double eligTotal = 0;
        for (Map.Entry<UUID, Double> en : ranking)
            if (total > 0 && en.getValue() / total >= expMin && Bukkit.getPlayer(en.getKey()) != null) { expShare.put(en.getKey(), en.getValue()); eligTotal += en.getValue(); }
        int parts = expShare.size();
        for (Map.Entry<UUID, Double> en : expShare.entrySet())
            en.setValue(even / parts + (1 - even) * (eligTotal <= 0 ? 0 : en.getValue() / eligTotal));
        if (parts >= 2) {   // 상한을 넘는 몫은 나머지 참가자에게 고루 (2명이면 상한 60%)
            double lim = Math.max(cap, 1.2 / parts);
            for (int it = 0; it < 5; it++) {
                double excess = 0;
                int under = 0;
                for (Map.Entry<UUID, Double> en : expShare.entrySet()) {
                    if (en.getValue() > lim) { excess += en.getValue() - lim; en.setValue(lim); }
                    else if (en.getValue() < lim) under++;
                }
                if (excess <= 1e-9 || under == 0) break;
                for (Map.Entry<UUID, Double> en : expShare.entrySet()) if (en.getValue() < lim) en.setValue(en.getValue() + excess / under);
            }
        }
        for (Map.Entry<UUID, Double> en : expShare.entrySet()) {
            Player p = Bukkit.getPlayer(en.getKey());
            if (p != null) {
                plugin.levels().addExp(p, bossExp(d) * en.getValue());
                plugin.passives().track(p, "boss_kills", 1);   // 주간 의뢰: 보스 처치 (v5.5.0)
            }
        }
        int rank = 0;
        for (Map.Entry<UUID, Double> en : ranking) {
            Player p = Bukkit.getPlayer(en.getKey());
            double share = total <= 0 ? 0 : en.getValue() / total;
            if (rank < 3) {
                String n = Text.name(en.getKey());
                kr.rpgcraft.util.Text.announce(Text.c("  &e" + (rank + 1) + "위 &f" + n + " &7- " + Text.num(en.getValue()) + " (" + String.format("%.1f", share * 100) + "%)"));
            }
            rank++;
            if (p == null || share < minShare) continue;
            double mult = Math.max(0.1, share);   // 돈 · 재료는 기여도대로 (경험치는 위에서 따로 분배)
            plugin.economy().give(p, (long) (d.money * mult * plugin.getConfig().getDouble("economy.boss-money-mult", 0.35)));
            // 재료 등은 바로 지급, 장비는 「보스 수정」으로 (마크에이지식: 수정을 쓰면 확률로 장비)
            for (BossDefinition.Drop dr : d.drops) {
                if (isGear(dr.item) || ThreadLocalRandom.current().nextDouble() >= dr.chance) continue;
                int amt = dr.max > dr.min ? ThreadLocalRandom.current().nextInt(dr.min, dr.max + 1) : dr.min;
                ItemStack it = plugin.items().create(dr.item, amt);
                if (it != null) give(p, it);
            }
        }
        // 보스 수정 (마크에이지식): 30% 확률로 시체 자리에 수정 1개가 떨어짐 → 우클릭하면 확률로 장비 (최대 2개)
        if (!gearDrops(d.id).isEmpty() && ThreadLocalRandom.current().nextDouble() < plugin.getConfig().getDouble("boss-crystal.drop-chance", 0.3)) {
            ItemStack cr = crystal(d.id, 1);
            if (cr != null) {
                e.getWorld().dropItemNaturally(e.getLocation(), cr);
                e.getWorld().spawnParticle(Particle.END_ROD, e.getLocation().add(0, 1, 0), 40, 0.5, 1.5, 0.5, 0.05);
                e.getWorld().playSound(e.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 2f, 0.8f);
                Text.announce(Text.PREFIX + Text.c("&d&l" + Text.strip(Text.c(d.name)) + "의 수정&f이 떨어졌습니다!"));
            }
        }
    }

    // =================================================================== 보스 수정
    private final NamespacedKey CRYSTAL = new NamespacedKey("rpgcraft", "boss_crystal");

    private boolean isGear(String itemId) {
        var tp = plugin.items().get(itemId);
        return tp != null && (tp.category.isEquipment() || tp.category == kr.rpgcraft.item.Category.BOW);
    }

    /** 보스 드롭표 중 장비만 */
    public List<BossDefinition.Drop> gearDrops(String bossId) {
        BossDefinition d = defs.get(bossId);
        List<BossDefinition.Drop> out = new ArrayList<>();
        if (d != null) for (BossDefinition.Drop dr : d.drops) if (isGear(dr.item)) out.add(dr);
        return out;
    }

    private double crystalMult() {
        return plugin.getConfig().getDouble("boss-crystal.chance-mult", 1.0);
    }

    /** 「○○의 수정」 아이템 (같은 보스 수정끼리 겹쳐짐) */
    public ItemStack crystal(String bossId, int amount) {
        BossDefinition d = defs.get(bossId);
        ItemStack it = plugin.items().create("boss_crystal", Math.max(1, amount));
        if (it == null || d == null) return it;
        var m = it.getItemMeta();
        m.getPersistentDataContainer().set(CRYSTAL, PersistentDataType.STRING, bossId);
        m.setDisplayName(Text.c("&d&l" + Text.strip(Text.c(d.name)) + "의 수정"));
        List<String> lore = new ArrayList<>();
        lore.add(Text.c("&7보스의 힘이 응축된 수정"));
        lore.add("");
        lore.add(Text.c("&e사용하면 확률에 따라 장비가 나옵니다 &7(최대 " + plugin.getConfig().getInt("boss-crystal.max-items", 2) + "개)"));
        for (BossDefinition.Drop dr : gearDrops(bossId)) {
            var tp = plugin.items().get(dr.item);
            lore.add(Text.c(" &8· " + tp.grade.nameColor() + tp.name + " &7" + String.format("%.1f", Math.min(1, dr.chance * crystalMult()) * 100) + "%"));
        }
        lore.add("");
        lore.add(Text.c("&e▶ 우클릭: 사용 &7· &e쉬프트+우클릭: 모두 사용"));
        m.setLore(lore);
        it.setItemMeta(m);
        return it;
    }

    /** 수정 사용: 장비마다 확률 판정 (한 번에 최대 boss-crystal.max-items 개, 기본 2). 반환: 얻은 장비 */
    public List<ItemStack> openCrystal(Player p, String bossId) {
        List<ItemStack> got = new ArrayList<>();
        List<BossDefinition.Drop> pool = gearDrops(bossId);
        Collections.shuffle(pool);   // 최대 개수에 걸려도 특정 장비만 유리하지 않게 판정 순서를 섞음
        int max = plugin.getConfig().getInt("boss-crystal.max-items", 2);
        for (BossDefinition.Drop dr : pool) {
            if (got.size() >= max) break;
            if (ThreadLocalRandom.current().nextDouble() >= dr.chance * crystalMult()) continue;
            ItemStack it = plugin.items().create(dr.item, 1);
            if (it == null) continue;
            got.add(it);
            give(p, it);
            var tp = plugin.items().get(dr.item);
            if (tp != null && tp.grade.atLeast(kr.rpgcraft.item.Grade.LEGEND))
                Text.announce(Text.PREFIX + Text.c("&d&l" + Text.name(p) + "&f님이 보스 수정에서 " + tp.grade.nameColor() + tp.name + "&f을(를) 얻었습니다!"));
        }
        return got;
    }

    public String crystalBoss(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(CRYSTAL, PersistentDataType.STRING);
    }

    /** 보스 상자 우클릭: 그 보스의 드롭표로 한 번 뽑기 (아무것도 안 나오면 한 번 더) */
    public List<ItemStack> rollChest(String bossId) {
        ItemStack one = pickChest(bossId);
        return one == null ? List.of() : List.of(one);
    }

    /** 드롭 확률을 가중치로 보스 아이템 하나 */
    public ItemStack pickChest(String bossId) {
        BossDefinition d = defs.get(bossId);
        if (d == null || d.drops.isEmpty()) return null;
        double total = 0;
        for (BossDefinition.Drop dr : d.drops) total += dr.chance;
        double r = ThreadLocalRandom.current().nextDouble() * total;
        for (BossDefinition.Drop dr : d.drops) {
            r -= dr.chance;
            if (r <= 0) {
                int amt = dr.max > dr.min ? ThreadLocalRandom.current().nextInt(dr.min, dr.max + 1) : dr.min;
                return plugin.items().create(dr.item, amt);
            }
        }
        return null;
    }

    public List<ItemStack> chestPreview(String bossId) {
        BossDefinition d = defs.get(bossId);
        List<ItemStack> out = new ArrayList<>();
        if (d == null) return out;
        double total = 0;
        for (BossDefinition.Drop dr : d.drops) total += dr.chance;
        for (BossDefinition.Drop dr : d.drops) {
            ItemStack it = plugin.items().create(dr.item, 1);
            if (it == null) continue;
            var m = it.getItemMeta();
            List<String> lore = m.hasLore() ? new ArrayList<>(m.getLore()) : new ArrayList<>();
            lore.add(0, Text.c("&e뽑힐 확률 " + String.format("%.1f", dr.chance / total * 100) + "%"));
            m.setLore(lore);
            it.setItemMeta(m);
            out.add(it);
        }
        return out;
    }

    public List<ItemStack> rollMinionDrops(String bossId) {
        BossDefinition d = defs.get(bossId);
        return d == null ? List.of() : roll(d.minionDrops);
    }

    private List<ItemStack> roll(List<BossDefinition.Drop> drops) {
        List<ItemStack> out = new ArrayList<>();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (BossDefinition.Drop dr : drops) {
            if (r.nextDouble() >= dr.chance) continue;
            int amt = dr.max > dr.min ? r.nextInt(dr.min, dr.max + 1) : dr.min;
            ItemStack it = plugin.items().create(dr.item, amt);
            if (it != null) out.add(it);
        }
        return out;
    }

    private void give(Player p, ItemStack it) {
        ItemStack shown = it.clone();
        for (ItemStack left : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
        plugin.visuals().obtain(p, shown);
        Text.msg(p, "&a보스 보상: &f" + (it.hasItemMeta() ? it.getItemMeta().getDisplayName() : it.getType().name()) + " &7x" + it.getAmount());
    }

    public int killAll() {
        int n = 0;
        for (Active a : new ArrayList<>(active.values())) {
            clearBars(a);
            if (a.entity != null) a.entity.remove();
            n++;
        }
        active.clear();
        return n;
    }

    public void shutdown() {
        for (Active a : active.values()) clearBars(a);
    }
}
