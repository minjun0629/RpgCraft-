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
        BossBar bar;
        final Map<Integer, Long> next = new HashMap<>();
        long nextSignature;
        final long born = System.currentTimeMillis();
        int phase = 1;              // 1: 평상 · 2: 분노(60%) · 3: 광폭(30%)
        boolean seenAwake;
        double aura;                // 주변 기운 회전 각도
    }

    private final RpgCraft plugin;
    private final Map<String, BossDefinition> defs = new LinkedHashMap<>();
    private final Map<UUID, Active> active = new HashMap<>();

    public BossManager(RpgCraft plugin) {
        this.plugin = plugin;
        load();
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
        e.setPersistent(true);
        var kb = e.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE);
        if (kb != null) kb.setBaseValue(1.0);
        initState(e, d);
        kr.rpgcraft.util.Text.announce(Text.PREFIX + Text.c("&c&l" + d.name + "&f(이)가 &e" + loc.getWorld().getName() + " "
                + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + "&f에 나타났습니다!"));
        for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.6f, 1f);
        intro(e, d);
        return e;
    }

    public MobManager.MobState initState(LivingEntity e, BossDefinition d) {
        MobManager.MobState s = plugin.mobs().initCustom(e, d.level, d.hp, d.damage * plugin.getConfig().getDouble("bosses.damage-mult", 1.15), d.defense, d.exp, d.money, d.name);
        s.bossId = d.id;
        plugin.mobs().updateName(e, s);
        if (!active.containsKey(e.getUniqueId())) {
            Active a = new Active();
            a.entity = e;
            a.def = d;
            // WHITE 는 나침반 문구 전용 투명 바(리소스팩)라서 보스는 파란 바로
            a.bar = Bukkit.createBossBar(Text.c("&c" + d.name), d.color == org.bukkit.boss.BarColor.WHITE ? org.bukkit.boss.BarColor.BLUE : d.color, BarStyle.SEGMENTED_10);
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
                a.bar.removeAll();
                it.remove();
                continue;
            }
            MobManager.MobState s = plugin.mobs().state(a.entity);
            double frac = Math.max(0, Math.min(1, s.hp / s.maxHp));
            phase(a, s, frac);
            a.bar.setTitle(barTitle(a, s, frac));
            a.bar.setProgress(frac);
            auraTick(a);
            Location bl = a.entity.getLocation();
            Set<Player> near = new HashSet<>();
            for (Player p : bl.getWorld().getPlayers()) if (p.getLocation().distanceSquared(bl) < 64 * 64) near.add(p);
            for (Player p : new ArrayList<>(a.bar.getPlayers())) if (!near.contains(p)) a.bar.removePlayer(p);
            for (Player p : near) a.bar.addPlayer(p);
            a.bar.setVisible(true);
            if (now - a.born > plugin.getConfig().getLong("bosses.lifetime-minutes", 30) * 60_000) {   // 등장 30분 뒤 사라짐 (주변에 아무도 없어도)
                a.bar.removeAll();
                a.entity.remove();
                Text.announce(Text.PREFIX + Text.c("&7" + a.def.name + "&7이(가) 사라졌습니다..."));
                continue;
            }
            Player target = nearest(a.entity, 30);
            if (target == null) continue;
            if (a.entity instanceof Mob mob && (mob.getTarget() == null || !(mob.getTarget() instanceof Player))) mob.setTarget(target);
            if (now - a.born > plugin.getConfig().getLong("bosses.lifetime-minutes", 30) * 60_000) {   // 등장 30분 뒤 사라짐
                a.bar.removeAll();
                a.entity.getWorld().spawnParticle(Particle.SMOKE_LARGE, a.entity.getLocation().add(0, 1, 0), 40, 1, 1, 1, 0.05);
                a.entity.remove();
                Text.announce(Text.PREFIX + Text.c("&7" + a.def.name + "&7이(가) 사라졌습니다..."));
                continue;
            }
            if (a.def.level >= plugin.getConfig().getInt("bosses.signature-min-level", 80) && now >= a.nextSignature) {
                boolean aw = s.awakened;
                a.nextSignature = now + (aw ? 11_000 : 17_000) + ThreadLocalRandom.current().nextInt(4000);
                if (a.nextSignature > 0 && a.next.size() > 0) signature(a, target, s);
            }
            for (int i = 0; i < a.def.skills.size(); i++) {
                if (now < a.next.getOrDefault(i, 0L)) continue;
                BossDefinition.Skill k = a.def.skills.get(i);
                int crowd = Math.max(1, near.size());
                double faster = (1 + plugin.getConfig().getDouble("bosses.crowd-skill-speed", 0.25) * (crowd - 1))   // 여럿이면 기술 간격 단축
                        * (a.phase == 3 ? 1.45 : a.phase == 2 ? 1.2 : 1.0);                                          // 분노 · 광폭 단계는 더 자주
                a.next.put(i, now + (long) (k.interval * 1000L / faster));
                if (crowd >= 3 && ThreadLocalRandom.current().nextDouble() < 0.35) {   // 3명 이상이면 다른 사람에게도 같은 기술
                    Player second = null;
                    for (Player q : near) if (!q.equals(target) && q.getLocation().distanceSquared(bl) < 30 * 30) { second = q; break; }
                    if (second != null) {
                        Player s2 = second;
                        kr.rpgcraft.util.Vfx.ring(s2.getLocation(), Math.max(3, k.radius), Color.fromRGB(0xFF2A2A));
                        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (a.entity.isValid()) cast(a, k, s2, s); }, 18L);
                    }
                }
                // 보스 기술도 범위를 먼저 표시하고 0.75초 뒤 발동
                Color red = Color.fromRGB(0xFF2A2A);
                switch (k.type) {
                    case "SLAM", "PULL" -> kr.rpgcraft.util.Vfx.ring(a.entity.getLocation(), Math.max(3, k.radius), red);
                    case "METEOR" -> kr.rpgcraft.util.Vfx.ring(target.getLocation(), Math.max(3, k.radius), red);
                    default -> { }
                }
                Player tg = target;
                Bukkit.getScheduler().runTaskLater(plugin, () -> { if (a.entity.isValid() && !a.entity.isDead()) cast(a, k, tg, s); }, 8L);   // 예고 짧게 (명중률↑)
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
            default -> Color.fromRGB(0xFF5050);
        };
    }

    private void later(long ticks, Runnable r) {
        Bukkit.getScheduler().runTaskLater(plugin, r, ticks);
    }

    private void hurt(LivingEntity b, Collection<Player> ps, double dmg, Set<UUID> once) {
        for (Player p : ps) if (once == null || once.add(p.getUniqueId())) plugin.combat().mobSkillDamage(b, p, dmg);
    }

    private void cast(Active a, BossDefinition.Skill k, Player target, MobManager.MobState s) {
        LivingEntity b = a.entity;
        World w = b.getWorld();
        Color c = theme(a.def.id);
        boolean aw = s.awakened;
        double dmg = s.damage * k.power;
        if (plugin.bossModels() != null) plugin.bossModels().attackPose(b);
        switch (k.type) {
            case "SLAM" -> {   // 대지 강타: 몸을 띄웠다가 내려찍기 → 충격파 3겹이 퍼지고 땅이 갈라짐 (각성: 한 번 더, 더 넓게)
                b.setVelocity(new Vector(0, 0.6, 0));
                for (int rep = 0; rep < (aw ? 2 : 1); rep++) {
                    double R = k.radius * (1 + rep * 0.4);
                    long base = 10L + rep * 22L;
                    if (rep > 0) later(base - 12, () -> kr.rpgcraft.util.Vfx.ring(b.getLocation(), R, Color.fromRGB(0xFF2A2A)));
                    later(base, () -> {
                        if (!b.isValid()) return;
                        Location o = b.getLocation();
                        Set<UUID> once = new HashSet<>();
                        for (int wv = 1; wv <= 3; wv++) {
                            double rr = R * wv / 3;
                            later(wv * 2L, () -> {
                                kr.rpgcraft.util.Vfx.ring(o, rr, wv(c));
                                kr.rpgcraft.util.Vfx.ring(o, rr * 0.9, Color.WHITE);
                                for (Player p : playersNear(o, rr)) if (once.add(p.getUniqueId())) {
                                    plugin.combat().mobSkillDamage(b, p, dmg);
                                    p.setVelocity(p.getLocation().toVector().subtract(o.toVector()).setY(0).normalize().multiply(0.9).setY(0.6));
                                }
                            });
                        }
                        for (int d = 0; d < 8; d++) {   // 갈라지는 땅
                            double ang = d * Math.PI / 4;
                            Location crack = o.clone().add(Math.cos(ang) * R * 0.7, 0.3, Math.sin(ang) * R * 0.7);
                            kr.rpgcraft.util.Vfx.beam(o.clone().add(0, 0.3, 0), crack, 0.8, c);
                            kr.rpgcraft.util.Vfx.burst(crack, 1.6, c);
                        }
                        kr.rpgcraft.util.Vfx.burst(o.clone().add(0, 1, 0), R * 0.6, Color.WHITE);
                        w.spawnParticle(Particle.EXPLOSION_LARGE, o, 6, R / 3, 0.2, R / 3);
                        w.playSound(o, Sound.ENTITY_GENERIC_EXPLODE, 1.6f, 0.6f);
                        w.playSound(o, Sound.ENTITY_RAVAGER_ROAR, 1f, 0.6f);
                    });
                }
            }
            case "METEOR" -> {   // 낙하 폭발: 하늘에서 떨어지는 빛기둥 → 대폭발 (각성·여럿이면 여러 개)
                List<Player> targets = new ArrayList<>(playersNear(b.getLocation(), 30));
                Collections.shuffle(targets);
                if (!targets.contains(target)) targets.add(0, target);
                int n = Math.min(targets.size(), aw ? 3 : 1);
                for (int i = 0; i < n; i++) {
                    Location at = targets.get(i).getLocation().clone();
                    kr.rpgcraft.util.Vfx.ring(at, k.radius, Color.fromRGB(0xFF2A2A));
                    later(12, () -> kr.rpgcraft.util.Vfx.ring(at, k.radius * 0.6, Color.fromRGB(0xFF2A2A)));
                    for (int f = 0; f < 4; f++) {   // 떨어지는 유성
                        int ff = f;
                        later(14 + f * 3L, () -> {
                            Location hi = at.clone().add(0, 20 - ff * 5, 0), lo = at.clone().add(0, 15 - ff * 5, 0);
                            kr.rpgcraft.util.Vfx.beam(hi, lo, 2.2, c);
                            kr.rpgcraft.util.Vfx.burst(lo, 2.4, c);
                        });
                    }
                    later(18, () -> {
                        if (!b.isValid()) return;
                        kr.rpgcraft.util.Vfx.burst(at.clone().add(0, 1, 0), k.radius * 1.3, c);
                        kr.rpgcraft.util.Vfx.burst(at.clone().add(0, 1, 0), k.radius * 0.7, Color.WHITE);
                        kr.rpgcraft.util.Vfx.ring(at, k.radius * 1.4, wv(c));
                        for (int q = 0; q < 4; q++) kr.rpgcraft.util.Vfx.slash(at.clone().add(0, 1, 0), new Vector(1, 0, 0), k.radius * 1.4, q * 45, q % 2 == 0 ? c : Color.WHITE);
                        w.spawnParticle(Particle.FLASH, at.clone().add(0, 1, 0), 1);
                        w.spawnParticle(Particle.EXPLOSION_HUGE, at, 1);
                        w.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.5f);
                        w.playSound(at, Sound.ITEM_TRIDENT_THUNDER, 1f, 0.6f);
                        hurt(b, playersNear(at, k.radius * 1.25), dmg, null);
                    });
                }
            }
            case "SUMMON" -> {   // 소환: 바닥 마법진 → 빛기둥에서 부하가 솟아남
                EntityType type;
                try {
                    type = EntityType.valueOf(k.entity == null ? "ZOMBIE" : k.entity);
                } catch (IllegalArgumentException ex) {
                    type = EntityType.ZOMBIE;
                }
                EntityType ft = type;
                int lv = k.level > 0 ? k.level : Math.max(1, a.def.level - 10);
                kr.rpgcraft.util.Vfx.ring(b.getLocation(), 6, c);
                kr.rpgcraft.util.Vfx.ring(b.getLocation(), 4, Color.WHITE);
                w.playSound(b.getLocation(), Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1.5f, 0.8f);
                for (int i = 0; i < k.amount + (aw ? 1 : 0); i++) {
                    Location l = b.getLocation().add(ThreadLocalRandom.current().nextDouble(-5, 5), 0.5, ThreadLocalRandom.current().nextDouble(-5, 5));
                    kr.rpgcraft.util.Vfx.beam(l, l.clone().add(0, 8, 0), 1.2, c);
                    later(10 + i * 3L, () -> {
                        if (!b.isValid()) return;
                        kr.rpgcraft.util.Vfx.burst(l.clone().add(0, 1, 0), 2.2, c);
                        Entity raw = w.spawnEntity(l, ft);
                        if (!(raw instanceof LivingEntity m)) { raw.remove(); return; }
                        m.getPersistentDataContainer().set(Keys.MINION, PersistentDataType.STRING, a.def.id);
                        m.setRemoveWhenFarAway(true);
                        plugin.mobs().initCustom(m, lv, plugin.mobs().hpFor(lv), plugin.mobs().damageFor(lv), Math.min(40, lv * 0.2),
                                plugin.mobs().expFor(lv), lv * 100L, k.name == null ? MobManager.korean(ft) : k.name);
                        if (!k.ai) m.setAI(false);
                        if (m instanceof Mob mob) mob.setTarget(target);
                    });
                }
            }
            case "FIREBALL" -> {   // 마탄: 빛나는 구체가 부채꼴로 날아가 (조금 따라감) 폭발
                int n = Math.max(1, k.amount) + (aw ? 2 : 0);
                w.playSound(b.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.2f, 0.7f);
                for (int i = 0; i < n; i++) {
                    double spread = (i - (n - 1) / 2.0) * 0.25;
                    Location eye = b.getEyeLocation().add(0, 1, 0);
                    Vector dir = target.getEyeLocation().toVector().subtract(eye.toVector()).normalize();
                    dir = new Vector(dir.getX() * Math.cos(spread) - dir.getZ() * Math.sin(spread), dir.getY(), dir.getX() * Math.sin(spread) + dir.getZ() * Math.cos(spread));
                    Vector start = dir.clone();
                    new org.bukkit.scheduler.BukkitRunnable() {
                        final Location pos = eye.clone();
                        Vector v = start.multiply(0.8);
                        int t;

                        @Override
                        public void run() {
                            if (!b.isValid() || ++t > 60) { cancel(); return; }
                            if (target.isOnline() && target.getWorld().equals(pos.getWorld())) {   // 살짝 유도
                                Vector want = target.getEyeLocation().toVector().subtract(pos.toVector()).normalize().multiply(0.8);
                                v = v.multiply(0.92).add(want.multiply(0.08));
                            }
                            pos.add(v);
                            if (t % 2 == 0) kr.rpgcraft.util.Vfx.burst(pos, 1.4, c);
                            w.spawnParticle(Particle.FLAME, pos, 2, 0.1, 0.1, 0.1, 0.01);
                            boolean hit = !pos.getBlock().isPassable();
                            for (Player p : playersNear(pos, 1.4)) { hit = true; break; }
                            if (!hit) return;
                            cancel();
                            kr.rpgcraft.util.Vfx.burst(pos, 3.2, c);
                            kr.rpgcraft.util.Vfx.ring(pos.clone().add(0, -1, 0), 2.6, Color.WHITE);
                            w.playSound(pos, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.3f);
                            hurt(b, playersNear(pos, 2.6), dmg, null);
                        }
                    }.runTaskTimer(plugin, i * 3L, 1L);
                }
            }
            case "PULL" -> {   // 소용돌이: 고리가 좁혀지며 끌어당긴 뒤 한가운데서 내파
                Location o = b.getLocation();
                w.playSound(o, Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.2f, 0.7f);
                for (int st = 0; st < 5; st++) {
                    int stt = st;
                    later(st * 4L, () -> {
                        double rr = k.radius * (1 - stt * 0.18);
                        kr.rpgcraft.util.Vfx.ring(o, rr, stt % 2 == 0 ? c : Color.WHITE);
                        for (Player p : playersNear(o, k.radius)) {
                            Vector v = o.toVector().subtract(p.getLocation().toVector());
                            if (v.lengthSquared() > 1) p.setVelocity(v.normalize().multiply(0.7).setY(0.15));
                        }
                    });
                }
                later(22, () -> {
                    if (!b.isValid()) return;
                    kr.rpgcraft.util.Vfx.burst(o.clone().add(0, 1, 0), 5, c);
                    kr.rpgcraft.util.Vfx.burst(o.clone().add(0, 1, 0), 2.5, Color.WHITE);
                    w.spawnParticle(Particle.FLASH, o.clone().add(0, 1, 0), 1);
                    w.playSound(o, Sound.ENTITY_GENERIC_EXPLODE, 1.4f, 0.8f);
                    hurt(b, playersNear(o, 4), dmg, null);
                });
            }
            case "BLINK" -> {   // 그림자 습격: 잔상을 남기고 대상 뒤로 → X 베기 (각성: 두 번)
                for (int rep = 0; rep < (aw ? 2 : 1); rep++) {
                    later(rep * 14L, () -> {
                        if (!b.isValid() || !target.isOnline()) return;
                        Location from = b.getLocation();
                        Location behind = target.getLocation().clone().subtract(target.getLocation().getDirection().setY(0).normalize().multiply(2));
                        kr.rpgcraft.util.Vfx.burst(from.clone().add(0, 1.5, 0), 3, c);
                        kr.rpgcraft.util.Vfx.beam(from.clone().add(0, 1, 0), behind.clone().add(0, 1, 0), 1.4, c);
                        b.teleport(behind);
                        Vector f = target.getLocation().toVector().subtract(behind.toVector()).setY(0);
                        if (f.lengthSquared() < 0.01) f = new Vector(1, 0, 0);
                        kr.rpgcraft.util.Vfx.slash(target.getLocation().add(0, 1, 0), f, 4, 45, c);
                        kr.rpgcraft.util.Vfx.slash(target.getLocation().add(0, 1, 0), f, 4, -45, Color.WHITE);
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

    private static Color wv(Color c) {
        return Color.fromRGB(Math.min(255, c.getRed() + 60), Math.min(255, c.getGreen() + 60), Math.min(255, c.getBlue() + 60));
    }

    /** 고유 대형 패턴 (레벨 80 이상 보스, 각성하면 더 자주) */
    private void signature(Active a, Player target, MobManager.MobState s) {
        LivingEntity b = a.entity;
        World w = b.getWorld();
        Color c = theme(a.def.id);
        Color red = Color.fromRGB(0xFF2A2A);
        double dmg = s.damage * 1.6;
        Location o = b.getLocation();
        int pick = ThreadLocalRandom.current().nextInt(4);
        if (plugin.bossModels() != null) plugin.bossModels().attackPose(b);
        switch (pick) {
            case 0 -> {   // 십자 광선: 빨간 선이 먼저 → 네 방향 광선 (각성: 대각선까지 8방향)
                int dirs = s.awakened ? 8 : 4;
                double L = 18;
                for (int d = 0; d < dirs; d++) {
                    double ang = d * Math.PI * 2 / dirs;
                    Location end = o.clone().add(Math.cos(ang) * L, 0.5, Math.sin(ang) * L);
                    kr.rpgcraft.util.Vfx.beam(o.clone().add(0, 0.5, 0), end, 0.6, red);
                    later(10, () -> kr.rpgcraft.util.Vfx.beam(o.clone().add(0, 0.5, 0), end, 0.6, red));
                }
                w.playSound(o, Sound.BLOCK_BEACON_POWER_SELECT, 1.2f, 0.6f);
                later(22, () -> {
                    if (!b.isValid()) return;
                    Set<UUID> once = new HashSet<>();
                    for (int d = 0; d < dirs; d++) {
                        double ang = d * Math.PI * 2 / dirs;
                        Vector dv = new Vector(Math.cos(ang), 0, Math.sin(ang));
                        Location end = o.clone().add(dv.clone().multiply(L)).add(0, 1, 0);
                        kr.rpgcraft.util.Vfx.beam(o.clone().add(0, 1, 0), end, 3.0, c);
                        kr.rpgcraft.util.Vfx.beam(o.clone().add(0, 1, 0), end, 1.2, Color.WHITE);
                        kr.rpgcraft.util.Vfx.burst(end, 2.5, c);
                        for (Player p : playersNear(o, L + 1)) {
                            Vector to = p.getLocation().toVector().subtract(o.toVector()).setY(0);
                            double along = to.dot(dv);
                            if (along > 0 && along < L && to.clone().subtract(dv.clone().multiply(along)).length() < 1.8 && once.add(p.getUniqueId()))
                                plugin.combat().mobSkillDamage(b, p, dmg);
                        }
                    }
                    w.playSound(o, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 0.8f);
                });
                announce(a, "&c십자 광선! &7빨간 선을 피하세요");
            }
            case 1 -> {   // 파멸의 고리: 가까이 붙어야 안전 (바깥 고리가 폭발)
                double safe = 4, outer = 16;
                kr.rpgcraft.util.Vfx.ring(o, safe, Color.fromRGB(0x5AFF7A));
                kr.rpgcraft.util.Vfx.ring(o, outer, red);
                later(12, () -> { kr.rpgcraft.util.Vfx.ring(o, safe, Color.fromRGB(0x5AFF7A)); kr.rpgcraft.util.Vfx.ring(o, outer, red); });
                w.playSound(o, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.5f, 0.6f);
                later(30, () -> {
                    if (!b.isValid()) return;
                    for (double r = safe + 2; r <= outer; r += 3) {
                        double rr = r;
                        later((long) ((r - safe) / 3), () -> { kr.rpgcraft.util.Vfx.ring(o, rr, c); kr.rpgcraft.util.Vfx.ring(o, rr - 0.8, Color.WHITE); });
                    }
                    for (Player p : playersNear(o, outer)) if (p.getLocation().distance(o) > safe) plugin.combat().mobSkillDamage(b, p, dmg * 1.2);
                    kr.rpgcraft.util.Vfx.burst(o.clone().add(0, 2, 0), 8, c);
                    w.playSound(o, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.5f);
                });
                announce(a, "&a파멸의 고리! &7보스 가까이(초록 원 안)로");
            }
            case 2 -> {   // 유성 폭격: 전장 곳곳에 빨간 원 10~16개가 차례로 폭발
                int n = s.awakened ? 16 : 10;
                w.playSound(o, Sound.ENTITY_WITHER_SHOOT, 1.2f, 0.5f);
                for (int i = 0; i < n; i++) {
                    Location at = o.clone().add(ThreadLocalRandom.current().nextDouble(-14, 14), 0, ThreadLocalRandom.current().nextDouble(-14, 14));
                    if (i < 3 && target.isOnline()) at = target.getLocation().clone().add(ThreadLocalRandom.current().nextDouble(-2, 2), 0, ThreadLocalRandom.current().nextDouble(-2, 2));
                    Location fat = at;
                    long delay = i * 3L;
                    later(delay, () -> kr.rpgcraft.util.Vfx.ring(fat, 3, red));
                    later(delay + 16, () -> {
                        if (!b.isValid()) return;
                        kr.rpgcraft.util.Vfx.beam(fat.clone().add(0, 16, 0), fat, 1.6, c);
                        kr.rpgcraft.util.Vfx.burst(fat.clone().add(0, 0.8, 0), 3.4, c);
                        w.playSound(fat, Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 0.9f);
                        hurt(b, playersNear(fat, 3), dmg * 0.7, null);
                    });
                }
                announce(a, "&6유성 폭격! &7빨간 원을 피하세요");
            }
            default -> {   // 칼날 폭풍: 보스 주위를 도는 참격이 3바퀴 휩쓸고 지나감
                w.playSound(o, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.5f, 0.5f);
                kr.rpgcraft.util.Vfx.ring(o, 9, red);
                Set<UUID> once = new HashSet<>();
                for (int t = 0; t < 18; t++) {
                    int tt = t;
                    later(10 + t * 2L, () -> {
                        if (!b.isValid()) return;
                        double ang = tt * Math.PI / 3;
                        Vector f = new Vector(Math.cos(ang), 0, Math.sin(ang));
                        Location at = b.getLocation().add(f.clone().multiply(5)).add(0, 1, 0);
                        kr.rpgcraft.util.Vfx.slash(at, f, 7, tt % 2 == 0 ? 30 : -30, tt % 3 == 0 ? Color.WHITE : c);
                        for (Player p : playersNear(at, 4)) if (once.add(p.getUniqueId())) {
                            plugin.combat().mobSkillDamage(b, p, dmg * 0.8);
                            p.setVelocity(f.clone().multiply(0.8).setY(0.4));
                        }
                        if (tt % 6 == 5) once.clear();   // 한 바퀴마다 다시 맞을 수 있음
                    });
                }
                announce(a, "&c칼날 폭풍! &7멀리 떨어지세요");
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
        Location o = b.getLocation();
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
        Location o = b.getLocation();
        double r = Math.max(1.4, b.getWidth() * 0.9);
        int pts = 6 + a.phase * 3;
        a.aura += 0.5;
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
        Location o = b.getLocation();
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
            a.bar.removeAll();
        }
        BossDefinition d = defs.get(s.bossId);
        if (d == null) return;
        if (ThreadLocalRandom.current().nextDouble() < plugin.getConfig().getDouble("bosses.chest-chance", 0.3)) {
            ItemStack chest = plugin.items().create("boss_chest", 1);
            if (chest != null) {
                var cm = chest.getItemMeta();
                cm.getPersistentDataContainer().set(new NamespacedKey(plugin, "boss_chest"), org.bukkit.persistence.PersistentDataType.STRING, d.id);
                cm.setDisplayName(Text.c("&6&l" + Text.strip(Text.c(d.name)) + "의 상자"));
                chest.setItemMeta(cm);
                Player top = null;
                double best = 0;
                for (Map.Entry<UUID, Double> en : s.contrib.entrySet()) {
                    Player cand = Bukkit.getPlayer(en.getKey());
                    if (cand != null && en.getValue() > best) { best = en.getValue(); top = cand; }
                }
                if (top != null) {
                    for (ItemStack l : top.getInventory().addItem(chest).values()) top.getWorld().dropItemNaturally(top.getLocation(), l);
                    top.playSound(top.getLocation(), Sound.BLOCK_CHEST_LOCKED, 1f, 1.2f);
                    Text.announce(Text.PREFIX + Text.c("&6&l" + top.getName() + "&f님이 &6" + Text.strip(Text.c(d.name)) + "의 상자&f를 얻었습니다!"));
                } else {
                    e.getWorld().dropItemNaturally(e.getLocation(), chest);
                    Text.announce(Text.PREFIX + Text.c("&6보스 상자&f가 떨어졌습니다!"));
                }
            }
        }
        double total = s.contrib.values().stream().mapToDouble(Double::doubleValue).sum();
        double minShare = plugin.getConfig().getDouble("boss.min-contribution", 0.07);
        List<Map.Entry<UUID, Double>> ranking = new ArrayList<>(s.contrib.entrySet());
        ranking.sort((x, y) -> Double.compare(y.getValue(), x.getValue()));
        kr.rpgcraft.util.Text.announce(Text.PREFIX + Text.c("&c&l" + d.name + "&f이(가) 토벌되었습니다!"));
        int rank = 0;
        for (Map.Entry<UUID, Double> en : ranking) {
            Player p = Bukkit.getPlayer(en.getKey());
            double share = total <= 0 ? 0 : en.getValue() / total;
            if (rank < 3) {
                String n = p != null ? p.getName() : plugin.data().get(en.getKey()).name;
                kr.rpgcraft.util.Text.announce(Text.c("  &e" + (rank + 1) + "위 &f" + n + " &7- " + Text.num(en.getValue()) + " (" + String.format("%.1f", share * 100) + "%)"));
            }
            rank++;
            if (p == null || share < minShare) continue;
            double mult = Math.max(0.1, share);
            plugin.levels().addExp(p, d.exp * mult);
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
                Text.announce(Text.PREFIX + Text.c("&d&l" + p.getName() + "&f님이 보스 수정에서 " + tp.grade.nameColor() + tp.name + "&f을(를) 얻었습니다!"));
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
            a.bar.removeAll();
            if (a.entity != null) a.entity.remove();
            n++;
        }
        active.clear();
        return n;
    }

    public void shutdown() {
        for (Active a : active.values()) a.bar.removeAll();
    }
}
