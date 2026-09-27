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
                        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (a.entity.isValid()) cast(a, k, s2, s); }, 18L);
                    }
                }
                // 예고(차오르는 위험 지역 · 표식)는 각 기술이 직접 그린다
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
    private void telegraph(Location c, double r, int ticks, Color col) {
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
                Location o = b.getLocation().add(0, b.getHeight() * 0.6, 0);
                double r = 3.5 - 3 * tt / (double) ticks;
                for (int i = 0; i < 6; i++) {
                    double a = tt * 0.5 + i * Math.PI / 3;
                    Location p = o.clone().add(Math.cos(a) * r, Math.sin(a * 2) * 0.6, Math.sin(a) * r);
                    dustAt(p, c, 1.3f);
                }
                b.getWorld().spawnParticle(tp, o, 2, 0.3, 0.3, 0.3, 0.01);
            });
        }
        b.getWorld().playSound(b.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.4f, 0.7f);
    }

    private static final class Vfx2 {
        static Color light(Color c, double t) {
            return Color.fromRGB((int) (c.getRed() + (255 - c.getRed()) * t), (int) (c.getGreen() + (255 - c.getGreen()) * t), (int) (c.getBlue() + (255 - c.getBlue()) * t));
        }
    }

    // =================================================================== 보스 기술 (레이드 패턴)
    private void cast(Active a, BossDefinition.Skill k, Player target, MobManager.MobState s) {
        LivingEntity b = a.entity;
        World w = b.getWorld();
        Color c = theme(a.def.id);
        Color red = Color.fromRGB(0xFF2A2A);
        boolean aw = s.awakened;
        double dmg = s.damage * k.power;
        Particle tp = themeParticle(a.def.id);
        if (plugin.bossModels() != null) plugin.bossModels().attackPose(b);
        switch (k.type) {
            case "SLAM" -> {   // 대지 분쇄: 붉은 원이 차오르는 동안 보스가 높이 뛰어올랐다가 내려찍음 → 크레이터 + 바깥으로 번지는 여진 (각성: 두 번째 더 넓게)
                for (int rep = 0; rep < (aw ? 2 : 1); rep++) {
                    double R = k.radius * (1 + rep * 0.4);
                    long base = 18L + rep * 30L;
                    later(base - 18, () -> {
                        if (!b.isValid()) return;
                        telegraph(b.getLocation(), R, 18, red);
                        b.setVelocity(new Vector(0, 0.9, 0));
                        w.playSound(b.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1.2f, 0.7f);
                    });
                    later(base, () -> {
                        if (!b.isValid()) return;
                        Location o = b.getLocation();
                        Set<UUID> once = new HashSet<>();
                        w.spawnParticle(Particle.FLASH, o.clone().add(0, 0.5, 0), 1);
                        w.spawnParticle(Particle.EXPLOSION_HUGE, o, 1);
                        kr.rpgcraft.util.Vfx.burst(o.clone().add(0, 1, 0), R * 0.7, Color.WHITE);
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
                List<Player> targets = new ArrayList<>(playersNear(b.getLocation(), 30));
                Collections.shuffle(targets);
                if (!targets.contains(target)) targets.add(0, target);
                int n = Math.min(targets.size(), aw ? 3 : 1);
                w.playSound(b.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1.4f, 0.5f);
                for (int i = 0; i < n; i++) {
                    Location at = targets.get(i).getLocation().clone();
                    telegraph(at, k.radius, 24, red);
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
                Location o = b.getLocation();
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
                    Location l = b.getLocation().add(ThreadLocalRandom.current().nextDouble(-5, 5), 0.5, ThreadLocalRandom.current().nextDouble(-5, 5));
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
                                plugin.mobs().expFor(lv), lv * 100L, k.name == null ? MobManager.korean(ft) : k.name);
                        if (!k.ai) m.setAI(false);
                        if (m instanceof Mob mob) mob.setTarget(target);
                    });
                }
            }
            case "FIREBALL" -> {   // 마탄 일제 사격: 기를 모은 뒤 부채꼴로 발사, 꼬리를 남기며 살짝 유도 → 폭발
                int n = Math.max(1, k.amount) + (aw ? 2 : 0);
                charge(b, 12, c);
                later(12, () -> w.playSound(b.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.4f, 0.6f));
                for (int i = 0; i < n; i++) {
                    double spread = (i - (n - 1) / 2.0) * 0.25;
                    new org.bukkit.scheduler.BukkitRunnable() {
                        Location pos;
                        Vector v;
                        int t;

                        @Override
                        public void run() {
                            if (!b.isValid() || ++t > 60) { cancel(); return; }
                            if (pos == null) {
                                pos = b.getEyeLocation().add(0, 1, 0);
                                Vector dir = target.getEyeLocation().toVector().subtract(pos.toVector()).normalize();
                                v = new Vector(dir.getX() * Math.cos(spread) - dir.getZ() * Math.sin(spread), dir.getY(), dir.getX() * Math.sin(spread) + dir.getZ() * Math.cos(spread)).multiply(0.8);
                                kr.rpgcraft.util.Vfx.burst(pos, 2, Color.WHITE);
                            }
                            if (target.isOnline() && target.getWorld().equals(pos.getWorld())) {   // 살짝 유도
                                Vector want = target.getEyeLocation().toVector().subtract(pos.toVector()).normalize().multiply(0.8);
                                v = v.multiply(0.92).add(want.multiply(0.08));
                            }
                            pos.add(v);
                            if (t % 2 == 0) kr.rpgcraft.util.Vfx.burst(pos, 1.5, c);
                            w.spawnParticle(Particle.FLAME, pos, 3, 0.12, 0.12, 0.12, 0.01);
                            w.spawnParticle(tp, pos, 2, 0.1, 0.1, 0.1, 0.01);
                            dustAt(pos.clone().subtract(v.clone().multiply(0.6)), c, 1.6f);
                            boolean hit = !pos.getBlock().isPassable();
                            for (Player p : playersNear(pos, 1.4)) { hit = true; break; }
                            if (!hit) return;
                            cancel();
                            kr.rpgcraft.util.Vfx.burst(pos, 3.4, c);
                            kr.rpgcraft.util.Vfx.ring(pos.clone().add(0, -1, 0), 2.6, Color.WHITE);
                            w.spawnParticle(Particle.EXPLOSION_LARGE, pos, 1);
                            w.playSound(pos, Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 1.3f);
                            hurt(b, playersNear(pos, 2.6), dmg, null);
                        }
                    }.runTaskTimer(plugin, 12L + i * 3L, 1L);
                }
            }
            case "PULL" -> {   // 심연의 소용돌이: 중심에 위험 원이 차오르는 동안 나선으로 빨아들임 → 한가운데서 내파 (밖으로 버텨서 벗어나야 함)
                Location o = b.getLocation();
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
                    w.playSound(o, Sound.ENTITY_GENERIC_EXPLODE, 1.6f, 0.7f);
                    hurt(b, playersNear(o, 4), dmg, null);
                });
            }
            case "BLINK" -> {   // 그림자 습격: 대상에게 표식(!) + 돌진 경로 예고 → 잔상을 남기며 뒤로 순간이동해 X 베기 (각성: 두 번)
                for (int rep = 0; rep < (aw ? 2 : 1); rep++) {
                    later(rep * 22L, () -> {
                        if (!b.isValid() || !target.isOnline()) return;
                        markTarget(target, 12, red);
                        Vector path = target.getLocation().toVector().subtract(b.getLocation().toVector()).setY(0);
                        if (path.lengthSquared() > 0.01) telegraphLine(b.getLocation(), path, path.length() + 2, 1.6, 12, red);
                        w.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.5f);
                    });
                    later(rep * 22L + 12, () -> {
                        if (!b.isValid() || !target.isOnline()) return;
                        Location from = b.getLocation();
                        Location behind = target.getLocation().clone().subtract(target.getLocation().getDirection().setY(0).normalize().multiply(2));
                        for (int g = 1; g <= 4; g++) {   // 잔상
                            Location ghost = from.clone().add(behind.toVector().subtract(from.toVector()).multiply(g / 5.0)).add(0, 1, 0);
                            kr.rpgcraft.util.Vfx.burst(ghost, 2.2, Vfx2.light(c, g * 0.15));
                            w.spawnParticle(Particle.SMOKE_LARGE, ghost, 4, 0.2, 0.4, 0.2, 0.01);
                        }
                        kr.rpgcraft.util.Vfx.beam(from.clone().add(0, 1, 0), behind.clone().add(0, 1, 0), 1.6, c);
                        b.teleport(behind);
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
        Location o = b.getLocation();
        Particle tp = themeParticle(a.def.id);
        int pick = ThreadLocalRandom.current().nextInt(6);
        if (plugin.bossModels() != null) plugin.bossModels().attackPose(b);
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
                charge(b, 30, c);
                w.playSound(o, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.6f, 0.6f);
                later(30, () -> {
                    if (!b.isValid()) return;
                    for (double r = safe + 2; r <= outer; r += 3) {
                        double rr = r;
                        later((long) ((r - safe) / 3), () -> { kr.rpgcraft.util.Vfx.ring(o, rr, c); kr.rpgcraft.util.Vfx.ring(o, rr - 0.8, Color.WHITE); w.spawnParticle(tp, o, 20, rr * 0.6, 0.3, rr * 0.6, 0.05); });
                    }
                    for (Player p : playersNear(o, outer)) if (p.getLocation().distance(o) > safe) plugin.combat().mobSkillDamage(b, p, dmg * 1.2);
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
                    later(delay, () -> telegraph(fat, 3, 16, red));
                    later(delay + 16, () -> {
                        if (!b.isValid()) return;
                        kr.rpgcraft.util.Vfx.beam(fat.clone().add(-3, 18, -2), fat, 1.8, c);
                        kr.rpgcraft.util.Vfx.burst(fat.clone().add(0, 0.8, 0), 3.6, c);
                        w.spawnParticle(Particle.LAVA, fat, 8, 1, 0.2, 1, 0.1);
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
                        Location at = b.getLocation().add(f.clone().multiply(5)).add(0, 1, 0);
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
                charge(b, 40, c);
                w.playSound(o, Sound.ENTITY_WITHER_SPAWN, 1f, 0.8f);
                later(40, () -> {
                    if (!b.isValid()) return;
                    for (int k2 = 0; k2 < 8; k2++) {
                        Location at = o.clone().add(ThreadLocalRandom.current().nextDouble(-R, R), 0, ThreadLocalRandom.current().nextDouble(-R, R));
                        later(k2, () -> { kr.rpgcraft.util.Vfx.burst(at.clone().add(0, 1, 0), 5, c); w.spawnParticle(Particle.EXPLOSION_HUGE, at, 1); });
                    }
                    kr.rpgcraft.util.Vfx.ring(o, R, wv(c));
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
                    Text.announce(Text.PREFIX + Text.c("&6&l" + Text.name(top) + "&f님이 &6" + Text.strip(Text.c(d.name)) + "의 상자&f를 얻었습니다!"));
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
                String n = Text.name(en.getKey());
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
