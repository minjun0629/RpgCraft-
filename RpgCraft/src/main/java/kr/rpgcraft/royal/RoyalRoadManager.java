package kr.rpgcraft.royal;

import kr.rpgcraft.Keys;
import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.data.PlayerData;
import kr.rpgcraft.gui.Gui;
import kr.rpgcraft.stat.HiddenStat;
import kr.rpgcraft.stat.Stat;
import kr.rpgcraft.stat.StatMap;
import kr.rpgcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * v5.11.0 「로열 로드」 — 소설 · 웹툰 『달빛 조각사』의 가상현실 게임 로열 로드의 규칙을 서버에 들인다.
 * <ul>
 *   <li><b>행동으로 오르는 스탯</b> (인내 · 예술 · 매력 · 행운 · 신앙 · 통솔력) — {@link RoyalStat}</li>
 *   <li><b>명성</b>과 명성 칭호 · 명성 순위</li>
 *   <li><b>사망 페널티</b>: 레벨 하락 · 스킬 숙련도 감소 · (설정 시) 일정 시간 접속 불가</li>
 *   <li><b>캐릭터 정보창</b> (/상태창) · <b>기도</b> (/기도) · 로열 로드 메뉴 (/로열로드)</li>
 * </ul>
 * 조각술 · 달빛 조각사 · 조각 생명부여는 {@link SculptManager}.
 */
public class RoyalRoadManager implements Listener, CommandExecutor, TabCompleter {
    private static final String[] FAME_TITLES = {"무명의 초보자", "이름이 알려지기 시작한 모험가", "제법 알려진 모험가", "유명한 모험가", "대륙의 영웅", "살아 있는 전설"};
    private static final int[] FAME_STEPS = {0, 30, 150, 600, 2500, 10000};

    private final RpgCraft plugin;
    private final File file;
    private final YamlConfiguration data;
    /** 사망으로 접속이 막힌 사람 → 풀리는 시각 (비동기 로그인 이벤트에서 읽으므로 동시성 맵) */
    private final Map<UUID, Long> lockouts = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastHurtGain = new HashMap<>();
    private final Map<UUID, Set<UUID>> talkedToday = new HashMap<>();
    private final Set<UUID> praying = new HashSet<>();
    private long talkDay;
    private boolean dirty;

    public RoyalRoadManager(RpgCraft plugin) {
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(), "royalroad.yml");
        data = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection lk = data.getConfigurationSection("lockouts");
        if (lk != null) for (String k : lk.getKeys(false)) {
            try { lockouts.put(UUID.fromString(k), lk.getLong(k)); } catch (IllegalArgumentException ignored) { }
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> { if (dirty) save(); }, 1200L, 1200L);
    }

    private boolean on() {
        return plugin.getConfig().getBoolean("royal-road.enabled", true);
    }

    private double cfg(String k, double def) {
        return plugin.getConfig().getDouble("royal-road." + k, def);
    }

    public void save() {
        data.set("lockouts", null);
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Long> e : lockouts.entrySet()) if (e.getValue() > now) data.set("lockouts." + e.getKey(), e.getValue());
        try {
            data.save(file);
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().warning("royalroad.yml 저장 실패: " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------ 스탯
    public int max(RoyalStat s) {
        return plugin.getConfig().getInt("royal-road.stat-max", 1000);
    }

    /** 로열 로드 스탯을 올린다. 정수가 바뀌면 소설처럼 알려 준다. */
    public void gain(Player p, RoyalStat s, double amount) {
        if (!on() || amount <= 0 || p == null) return;
        PlayerData d = plugin.data().get(p);
        int before = s.points(d);
        if (before >= max(s)) return;
        d.counters.put(s.key(), Math.min(max(s), s.raw(d) + amount * cfg("stat-gain-mult", 1.0)));
        int after = s.points(d);
        if (after > before) {
            Text.msg(p, s.color + s.label + " &f스탯이 &e" + (after - before) + " &f상승하였습니다. &7(" + after + ")");
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.6f);
            plugin.stats().refresh(p);
        }
    }

    public int stat(PlayerData d, RoyalStat s) {
        return s.points(d);
    }

    /** 로열 로드 스탯 · 조각품 버프 · 조각 검술로 오르는 능력치 (StatCalculator 가 합산) */
    public StatMap bonus(Player p, PlayerData d) {
        StatMap t = new StatMap();
        if (!on()) return t;
        int end = RoyalStat.ENDURANCE.points(d), art = RoyalStat.ART.points(d), luck = RoyalStat.LUCK.points(d),
                faith = RoyalStat.FAITH.points(d), lead = RoyalStat.LEADERSHIP.points(d);
        t.add(Stat.HP_PCT, Math.min(30, end * 0.1)).add(Stat.DEF, Math.min(10, end * 0.02));
        t.add(Stat.MAGIC, Math.min(1500, art * 1.5)).add(Stat.EXP_PCT, Math.min(10, art * 0.02));
        t.add(Stat.CRIT, Math.min(8, luck * 0.03)).add(Stat.DODGE, Math.min(4, luck * 0.015));
        t.add(Stat.HP, Math.min(6000, faith * 15.0)).add(Stat.DEF, Math.min(5, faith * 0.01));
        t.add(Stat.EXP_PCT, Math.min(5, lead * 0.01));
        if (plugin.sculpt() != null) t.addAll(plugin.sculpt().bonus(p, d));
        return t;
    }

    /** 매력 · 명성에 따른 NPC 상점 할인 (0 ~ 0.2) */
    public double discount(PlayerData d) {
        if (!on()) return 0;
        double charm = Math.min(0.15, RoyalStat.CHARM.points(d) * 0.0005);
        double fame = Math.min(0.05, fame(d) / 10000.0 * 0.05);
        return Math.min(0.2, charm + fame);
    }

    // ------------------------------------------------------------------ 명성
    public long fame(PlayerData d) {
        return (long) d.counter("rr_fame");
    }

    public void addFame(Player p, long amount, String why) {
        if (!on() || amount <= 0 || p == null) return;
        PlayerData d = plugin.data().get(p);
        String before = fameTitle(fame(d));
        d.addCounter("rr_fame", amount);
        Text.msg(p, "&6명성&f이 &e" + amount + " &f올랐습니다. &7(" + why + " · 명성 " + Text.num(fame(d)) + ")");
        data.set("fame." + p.getUniqueId() + ".name", p.getName());
        data.set("fame." + p.getUniqueId() + ".fame", fame(d));
        dirty = true;
        String after = fameTitle(fame(d));
        if (!after.equals(before)) {
            p.sendTitle(Text.c("&6&l명성 칭호"), Text.c("&e" + after), 10, 60, 15);
            Text.announce(Text.c("&6[로열 로드] &f" + Text.name(p) + " &7님이 이제 &e「" + after + "」 &7(으)로 불립니다."));
        }
    }

    public static String fameTitle(long fame) {
        String t = FAME_TITLES[0];
        for (int i = 0; i < FAME_STEPS.length; i++) if (fame >= FAME_STEPS[i]) t = FAME_TITLES[i];
        return t;
    }

    private List<Map.Entry<String, Long>> fameTop(int n) {
        List<Map.Entry<String, Long>> out = new ArrayList<>();
        ConfigurationSection s = data.getConfigurationSection("fame");
        if (s != null) for (String k : s.getKeys(false)) out.add(Map.entry(s.getString(k + ".name", "?"), s.getLong(k + ".fame")));
        out.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        return out.subList(0, Math.min(n, out.size()));
    }

    // ------------------------------------------------------------------ 스탯이 오르는 행동
    private static boolean fromMob(EntityDamageByEntityEvent e) {
        Entity src = e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Entity sh ? sh : e.getDamager();
        return src instanceof LivingEntity && !(src instanceof Player);
    }

    /** 인내: 몬스터에게 맞으며 버티기 (전투 피해는 가상 체력으로 처리되어 바닐라 이벤트가 취소되므로 취소 여부와 관계없이 봄) */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onHurt(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player p) || !fromMob(e) || p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) return;
        long now = System.currentTimeMillis();
        if (now - lastHurtGain.getOrDefault(p.getUniqueId(), 0L) < 500) return;
        lastHurtGain.put(p.getUniqueId(), now);
        PlayerData d = plugin.data().get(p);
        double ratio = d.stats.maxHp <= 0 ? 1 : d.hp / d.stats.maxHp;
        gain(p, RoyalStat.ENDURANCE, ratio < 0.3 ? 0.06 : 0.02);
    }

    /** 매력: 하루에 NPC 마다 한 번, 말을 걸면 (하루 최대 2) */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onTalk(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !(e.getRightClicked() instanceof org.bukkit.entity.AbstractVillager v)) return;
        if (!plugin.combat().isNpc(v) && v.getCustomName() == null) return;
        long day = LocalDate.now(ZoneId.of(plugin.getConfig().getString("events.timezone", "Asia/Seoul"))).toEpochDay();
        if (day != talkDay) { talkDay = day; talkedToday.clear(); }
        Set<UUID> seen = talkedToday.computeIfAbsent(e.getPlayer().getUniqueId(), k -> new HashSet<>());
        if (seen.size() >= 8 || !seen.add(v.getUniqueId())) return;
        gain(e.getPlayer(), RoyalStat.CHARM, 0.25);
    }

    /** 행운: 낚시 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        if (e.getState() == PlayerFishEvent.State.CAUGHT_FISH) gain(e.getPlayer(), RoyalStat.LUCK, 0.04);
    }

    /** 처치: 행운 · 통솔력 · 보스 명성 */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent e) {
        Player k = e.getEntity().getKiller();
        if (k == null || e.getEntity() instanceof Player) return;
        boolean boss = e.getEntity().getPersistentDataContainer().has(Keys.BOSS, PersistentDataType.STRING);
        if (boss) {
            gain(k, RoyalStat.LUCK, 0.5);
            addFame(k, (long) cfg("fame.boss", 3), "보스 처치");
        } else {
            gain(k, RoyalStat.LUCK, 0.002);
            if (ThreadLocalRandom.current().nextDouble() < 0.0005) {
                Text.msg(k, "&a행운의 여신이 미소 짓습니다.");
                gain(k, RoyalStat.LUCK, 1);
            }
        }
        double lead = 0;
        var party = plugin.party() == null ? null : plugin.party().of(k);
        if (party != null && party.members.size() >= 2) lead += 0.01;
        if (plugin.sculpt() != null && plugin.sculpt().hasLifeOut(k)) lead += 0.01;
        if (lead > 0) gain(k, RoyalStat.LEADERSHIP, boss ? lead * 20 : lead);
    }

    // ------------------------------------------------------------------ 기도 (신앙)
    private long today() {
        return LocalDate.now(ZoneId.of(plugin.getConfig().getString("events.timezone", "Asia/Seoul"))).toEpochDay();
    }

    public void pray(Player p) {
        PlayerData d = plugin.data().get(p);
        long day = today();
        if ((long) d.counter("rr_pray_day") == day) { Text.msg(p, "&7오늘은 이미 기도를 올렸습니다. 내일 다시 기도하세요."); return; }
        if (System.currentTimeMillis() - d.lastCombat < 8000) { Text.msg(p, "&c전투 중에는 기도할 수 없습니다."); return; }
        if (!praying.add(p.getUniqueId())) return;
        Location at = p.getLocation();
        Text.msg(p, "&f무릎을 꿇고 기도를 올립니다... &7(3초 동안 움직이지 마세요)");
        p.getWorld().playSound(at, Sound.BLOCK_BEACON_AMBIENT, 1f, 1.4f);
        final int[] n = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!p.isOnline() || p.isDead() || !p.getWorld().equals(at.getWorld()) || p.getLocation().distanceSquared(at) > 0.5) {
                praying.remove(p.getUniqueId());
                task.cancel();
                if (p.isOnline()) Text.msg(p, "&c기도가 흐트러졌습니다.");
                return;
            }
            p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 2.2, 0), 4, 0.3, 0.1, 0.3, 0.01);
            if (++n[0] < 6) return;
            task.cancel();
            praying.remove(p.getUniqueId());
            long streak = (long) d.counter("rr_pray_day") == day - 1 ? (long) d.counter("rr_pray_streak") + 1 : 1;
            d.counters.put("rr_pray_day", (double) day);
            d.counters.put("rr_pray_streak", (double) streak);
            double amount = 1 + (streak % 7 == 0 ? 2 : 0);
            p.getWorld().spawnParticle(Particle.TOTEM, p.getLocation().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.2);
            p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BELL_RESONATE, 1f, 1.2f);
            Text.msg(p, "&f여신 프레야의 축복이 내립니다. &7(연속 " + streak + "일" + (streak % 7 == 0 ? " · 7일 보너스!" : "") + ")");
            gain(p, RoyalStat.FAITH, amount);
            d.hp = d.stats.maxHp;
        }, 10L, 10L);
    }

    // ------------------------------------------------------------------ 사망 페널티
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        if (!on() || !plugin.getConfig().getBoolean("royal-road.death.enabled", true)) return;
        Player p = e.getEntity();
        if (plugin.duels() != null && plugin.duels().inDuel(p)) return;   // 친선 PVP 는 페널티 없음
        PlayerData d = plugin.data().get(p);
        List<String> lost = new ArrayList<>();
        int loss = plugin.getConfig().getInt("royal-road.death.level-loss", 1);
        int min = plugin.getConfig().getInt("royal-road.death.min-level", 20);
        if (loss > 0 && d.level > min) {
            int drop = Math.min(loss, d.level - min);
            int perLevel = plugin.getConfig().getInt("player.stat-per-level", 5) + plugin.getConfig().getInt("rebirth.stat-points", 2) * (int) d.counter("rebirth");
            int pts = drop * perLevel;
            int fromFree = Math.min(pts, d.statPoints);
            d.statPoints -= fromFree;
            if (pts > fromFree) d.addCounter("rr_stat_debt", pts - fromFree);   // 이미 찍은 스탯은 다음 레벨 업 때 갚음
            d.level -= drop;
            d.exp = 0;
            lost.add("레벨 " + drop);
        }
        double sk = cfg("death.skill-loss", 0.3);
        if (sk > 0 && plugin.sculpt() != null && plugin.sculpt().loseSkill(d, sk)) lost.add("조각술 숙련도");
        if (!lost.isEmpty()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline()) return;
                Text.msg(p, "&c사망하셨습니다. &7로열 로드의 규칙에 따라 &c" + String.join(" · ", lost) + "&7이(가) 하락했습니다.");
                plugin.stats().refresh(p);
            }, 20L);
        }
        int lock = plugin.getConfig().getInt("royal-road.death.lockout-minutes", 0);
        if (lock > 0 && !p.hasPermission("rpgcraft.admin")) {
            long until = System.currentTimeMillis() + lock * 60_000L;
            lockouts.put(p.getUniqueId(), until);
            dirty = true;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline()) p.kickPlayer(Text.c("&c&l캐릭터가 사망하였습니다.\n\n&f로열 로드의 규칙에 따라 &e" + lock + "분 &f동안 접속할 수 없습니다."));
            }, 60L);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        Long until = lockouts.get(e.getUniqueId());
        if (until == null) return;
        long left = until - System.currentTimeMillis();
        if (left <= 0) { lockouts.remove(e.getUniqueId()); return; }
        e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Text.c("&c&l사망 페널티 중입니다.\n\n&f다시 접속할 수 있을 때까지 &e" + (left / 60_000 + 1) + "분&f 남았습니다."));
    }

    /** 사망으로 진 스탯 빚을 새로 얻은 스탯 포인트로 갚는다 (LevelService 가 레벨 업 뒤 부름) */
    public void payDebt(PlayerData d) {
        double debt = d.counter("rr_stat_debt");
        if (debt <= 0 || d.statPoints <= 0) return;
        int pay = (int) Math.min(debt, d.statPoints);
        d.statPoints -= pay;
        d.counters.put("rr_stat_debt", debt - pay);
        if (d.counter("rr_stat_debt") <= 0) d.counters.remove("rr_stat_debt");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        if (!on() || !plugin.getConfig().getBoolean("royal-road.welcome-title", true)) return;
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) p.sendTitle(Text.c("&6&l로열 로드"), Text.c("&f베르사 대륙에 오신 것을 환영합니다"), 10, 50, 20);
        }, 40L);
    }

    // ------------------------------------------------------------------ 정보창 · 메뉴
    public void status(CommandSender to, Player p) {
        PlayerData d = plugin.data().get(p);
        var s = d.stats;
        List<String> l = new ArrayList<>();
        l.add("&8&m                                                  ");
        l.add("&6&l  캐릭터 정보창");
        l.add("&f 이름: &e" + Text.name(p) + "   &f레벨: &e" + d.level + "   &f직업: &e" + jobLabel(d));
        l.add("&f 칭호: &e" + fameTitle(fame(d)) + "   &f명성: &e" + Text.num(fame(d)));
        l.add("&f 생명력: &c" + Text.num(d.hp) + " &7/ &c" + Text.num(s.maxHp) + "   &f공격력: &e" + Text.num(s.attack) + "   &f방어력: &e" + String.format("%.1f%%", s.def));
        l.add("&f 힘 &e" + d.str + "  &f민첩 &e" + d.dex + "  &f모험 &e" + d.adv + "  &7(남은 포인트 " + d.statPoints
                + (d.counter("rr_stat_debt") > 0 ? " · 사망 빚 " + (int) d.counter("rr_stat_debt") : "") + ")");
        StringBuilder rs = new StringBuilder(" ");
        for (RoyalStat r : RoyalStat.values()) rs.append(r.color).append(r.label).append(" &f").append(r.points(d)).append("  ");
        l.add(rs.toString());
        StringBuilder hs = new StringBuilder(" &8");
        for (HiddenStat h : HiddenStat.values()) if (h.points(d) > 0) hs.append(h.label).append(' ').append(h.points(d)).append("  ");
        if (hs.length() > 4) l.add(hs.toString());
        if (plugin.sculpt() != null) l.add("&f 조각술: &d" + plugin.sculpt().skillLabel(d) + (plugin.sculpt().isMoonlight(d) ? "   &b☾ 달빛 조각사" : ""));
        double disc = discount(d);
        if (disc > 0) l.add("&7 NPC 상점 할인 " + String.format("%.1f%%", disc * 100));
        l.add("&8&m                                                  ");
        for (String x : l) to.sendMessage(Text.c(x));
    }

    private String jobLabel(PlayerData d) {
        String j = plugin.jobs() == null ? "무직" : plugin.jobs().title(d);
        return plugin.sculpt() != null && plugin.sculpt().isMoonlight(d) ? j + " · 달빛 조각사" : j;
    }

    public void openMenu(Player p) {
        PlayerData d = plugin.data().get(p);
        Gui g = new Gui(5, "&8로열 로드") {
        };
        g.set(4, Gui.button(Material.NETHER_STAR, "&6&l로열 로드", "&7행동해야 오르는 스탯 · 명성 · 조각술 · 사망 페널티",
                "&7『달빛 조각사』의 가상현실 게임 규칙", "", "&f명성 &e" + Text.num(fame(d)) + " &7— " + fameTitle(fame(d))), null);
        int[] slots = {19, 20, 21, 23, 24, 25};
        int i = 0;
        for (RoyalStat r : RoyalStat.values()) {
            double raw = r.raw(d);
            g.set(slots[i++], Gui.button(r.icon, r.color + "&l" + r.label + " &f" + r.points(d),
                    "&7다음 1 까지 " + String.format("%.0f%%", (raw - Math.floor(raw)) * 100),
                    "&7오르는 법: &f" + r.howTo, "&7효과: &f" + r.effect), null);
        }
        g.set(38, Gui.button(Material.PAPER, "&e캐릭터 정보창", "&7/상태창"), e -> { p.closeInventory(); status(p, p); });
        g.set(39, Gui.button(Material.BELL, "&f기도하기", "&7하루 한 번 · 신앙 +1 (7일 연속마다 +2)", "&7/기도"), e -> { p.closeInventory(); pray(p); });
        g.set(40, Gui.button(Material.FLINT, "&d조각술", "&7조각칼 · 조각 재료 · 내 조각품 · 달빛 조각사", "&7/조각"), e -> { if (plugin.sculpt() != null) plugin.sculpt().openMenu(p); });
        g.set(41, Gui.button(Material.BONE, "&a생명체", "&7조각품에 생명을 불어넣은 동료", "&7/생명체"), e -> { if (plugin.sculpt() != null) plugin.sculpt().openLife(p); });
        List<String> top = new ArrayList<>(List.of("&7명성 순위"));
        int r = 1;
        for (Map.Entry<String, Long> en : fameTop(10)) top.add("&e" + r++ + ". &f" + en.getKey() + " &7- " + Text.num(en.getValue()));
        g.set(42, Gui.button(Material.GOLDEN_HELMET, "&6명성 순위", top.toArray(new String[0])), null);
        int loss = plugin.getConfig().getInt("royal-road.death.level-loss", 1), lock = plugin.getConfig().getInt("royal-road.death.lockout-minutes", 0);
        g.set(44, Gui.button(Material.SKELETON_SKULL, "&c사망 페널티",
                "&7레벨 &f" + loss + " &7하락 (Lv." + plugin.getConfig().getInt("royal-road.death.min-level", 20) + " 초과일 때)",
                "&7조각술 숙련도 감소", lock > 0 ? "&7사망 후 &f" + lock + "분 &7동안 접속 불가" : "&7접속 제한 없음 (서버 설정)",
                "&8친선 PVP(/pvp) 사망은 제외"), null);
        g.fill(0, 44);
        g.open(p);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String c = cmd.getName();
        if (c.equals("status")) {
            if (args.length > 0 && sender.hasPermission("rpgcraft.admin")) {
                Player t = Text.player(args[0]);
                if (t == null) { Text.msg(sender, "&c접속 중인 플레이어가 아닙니다."); return true; }
                status(sender, t);
                return true;
            }
            if (sender instanceof Player p) status(p, p);
            return true;
        }
        if (c.equals("pray")) {
            if (sender instanceof Player p) pray(p);
            return true;
        }
        if (args.length >= 2 && args[0].equals("해제") && sender.hasPermission("rpgcraft.admin")) {
            org.bukkit.OfflinePlayer t = Bukkit.getOfflinePlayer(args[1]);
            Text.msg(sender, lockouts.remove(t.getUniqueId()) != null ? "&a" + args[1] + " 의 사망 접속 제한을 풀었습니다." : "&7접속 제한 중이 아닙니다.");
            dirty = true;
            return true;
        }
        if (args.length >= 4 && args[0].equals("스탯") && sender.hasPermission("rpgcraft.admin")) {   // /로열로드 스탯 <플레이어> <스탯> <값>
            Player t = Text.player(args[1]);
            RoyalStat rs = null;
            for (RoyalStat r : RoyalStat.values()) if (r.label.equals(args[2]) || r.name().equalsIgnoreCase(args[2])) rs = r;
            if (t == null || rs == null) { Text.msg(sender, "&c/로열로드 스탯 <플레이어> <인내|예술|매력|행운|신앙|통솔력> <값>"); return true; }
            plugin.data().get(t).counters.put(rs.key(), Math.max(0, Text.parseDouble(args[3], 0)));
            plugin.stats().refresh(t);
            Text.msg(sender, "&a" + t.getName() + " 의 " + rs.label + " = " + args[3]);
            return true;
        }
        if (args.length >= 3 && args[0].equals("명성") && sender.hasPermission("rpgcraft.admin")) {   // /로열로드 명성 <플레이어> <더할 값>
            Player t = Text.player(args[1]);
            if (t == null) { Text.msg(sender, "&c접속 중인 플레이어가 아닙니다."); return true; }
            addFame(t, Text.parseLong(args[2], 0), "관리자");
            return true;
        }
        if (!(sender instanceof Player p)) { Text.msg(sender, "&7/로열로드 해제 <플레이어> · 스탯 <플레이어> <스탯> <값> · 명성 <플레이어> <값>"); return true; }
        if (args.length > 0 && (args[0].equals("상태창") || args[0].equals("정보"))) status(p, p);
        else if (args.length > 0 && args[0].equals("기도")) pray(p);
        else openMenu(p);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (!cmd.getName().equals("royalroad") || args.length != 1) return List.of();
        List<String> o = new ArrayList<>(List.of("상태창", "기도"));
        if (sender.hasPermission("rpgcraft.admin")) o.addAll(List.of("해제", "스탯", "명성"));
        o.removeIf(x -> !x.startsWith(args[0]));
        return o;
    }
}
