package kr.rpgcraft.world;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.data.PlayerData;
import kr.rpgcraft.gui.Gui;
import kr.rpgcraft.mob.CustomMobManager;
import kr.rpgcraft.mob.MonsterTierManager;
import kr.rpgcraft.util.Fx;
import kr.rpgcraft.util.Text;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.*;

/**
 * v5.10.30 무한의 탑 (/탑). 혼자 도전하는 층 오르기.
 * - 하늘 높이 떠 있는 개인 전투장에서 층마다 몬스터를 모두 쓰러뜨리면 다음 층 (층마다 제한 시간).
 * - 5층마다 정예 층, 10층마다 보스 층. 층이 오를수록 몬스터 레벨 ↑ · 수 ↑.
 * - 10층마다 체크포인트: 다음 도전은 마지막으로 넘은 10층 다음부터 시작할 수 있다.
 * - 처음 오른 층마다 돈 (10층마다 아이템), 주간 최고 층 랭킹 → 주가 바뀌면 순위 보상을 우편으로.
 * - 죽거나 나가면 도전 끝 (원래 있던 곳으로).
 */
public class TowerManager implements Listener, CommandExecutor {
    private static final int SPACING = 80, R = 12, WALL = 7;

    private class Run {
        final UUID player;
        final int slot;
        final Location back;
        final Set<UUID> mobs = new HashSet<>();
        int floor;
        long deadline, nextAt;
        BossBar bar;

        Run(Player p, int slot, int floor) {
            this.player = p.getUniqueId();
            this.slot = slot;
            this.back = p.getLocation().clone();
            this.floor = floor;
        }
    }

    private final RpgCraft plugin;
    private final Map<UUID, Run> runs = new HashMap<>();
    private final Set<Integer> built = new HashSet<>();
    private final Map<UUID, Integer> weekly = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    private final File file;
    private int week;
    private final Random rnd = new Random();

    public TowerManager(RpgCraft plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "tower.yml");
        load();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 10L);
    }

    // ------------------------------------------------------------------ 저장 · 주간
    private static int weekId() {
        LocalDate d = LocalDate.now();
        return d.get(IsoFields.WEEK_BASED_YEAR) * 100 + d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
    }

    private void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        week = y.getInt("week", weekId());
        var sec = y.getConfigurationSection("scores");
        if (sec != null) for (String k : sec.getKeys(false)) {
            try {
                UUID u = UUID.fromString(k);
                weekly.put(u, sec.getInt(k + ".floor"));
                names.put(u, sec.getString(k + ".name", "?"));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("week", week);
        for (var en : weekly.entrySet()) {
            y.set("scores." + en.getKey() + ".floor", en.getValue());
            y.set("scores." + en.getKey() + ".name", names.getOrDefault(en.getKey(), "?"));
        }
        try {
            y.save(file);
        } catch (IOException ignored) {
        }
    }

    private List<Map.Entry<UUID, Integer>> ranking() {
        List<Map.Entry<UUID, Integer>> l = new ArrayList<>(weekly.entrySet());
        l.sort((a, b) -> b.getValue() - a.getValue());
        return l;
    }

    /** 주가 바뀌면 지난주 순위 보상을 우편으로 보내고 새 주 시작 */
    private void rollWeek() {
        int now = weekId();
        if (now == week) return;
        List<Map.Entry<UUID, Integer>> rank = ranking();
        for (int i = 0; i < rank.size(); i++) {
            UUID u = rank.get(i).getKey();
            int floor = rank.get(i).getValue();
            if (floor < 5 || plugin.mail() == null) continue;
            long money = (long) floor * plugin.getConfig().getLong("tower.weekly-money-per-floor", 20000);
            List<ItemStack> items = new ArrayList<>();
            String place;
            if (i == 0) { money += 15_000_000; add(items, "cube_master", 3); place = "1위"; }
            else if (i == 1) { money += 10_000_000; add(items, "cube_master", 2); place = "2위"; }
            else if (i == 2) { money += 5_000_000; add(items, "cube_master", 1); place = "3위"; }
            else if (i < 10) { money += 2_500_000; add(items, "cube_red", 3); place = (i + 1) + "위"; }
            else place = (i + 1) + "위";
            plugin.mail().send(u, "&5무한의 탑", "지난주 무한의 탑 " + place + " (최고 " + floor + "층) 보상", money, items);
        }
        if (!rank.isEmpty()) Text.announce(Text.PREFIX + Text.c("&5&l무한의 탑 &f주간 순위가 마감되었습니다! 1위 &e"
                + names.getOrDefault(rank.get(0).getKey(), "?") + " &7(" + rank.get(0).getValue() + "층) &f— 보상은 우편으로"));
        weekly.clear();
        names.clear();
        week = now;
        save();
    }

    private void add(List<ItemStack> l, String id, int n) {
        ItemStack it = plugin.items().create(id, n);
        if (it != null) l.add(it);
    }

    // ------------------------------------------------------------------ 전투장
    private World world() {
        World w = Bukkit.getWorld(plugin.getConfig().getString("tower.world", "world"));
        return w != null ? w : Bukkit.getWorlds().get(0);
    }

    private Location center(int slot) {
        World w = world();
        int y = Math.min(w.getMaxHeight() - 20, plugin.getConfig().getInt("tower.y", 260));
        return new Location(w, plugin.getConfig().getInt("tower.x", 30000) + slot * SPACING + 0.5, y, plugin.getConfig().getInt("tower.z", 30000) + 0.5);
    }

    /** 이 위치가 탑 전투장 구역인지 */
    public boolean inArena(Location l) {
        Location c = center(0);
        if (l.getWorld() == null || !l.getWorld().equals(c.getWorld())) return false;
        double dx = l.getX() - c.getX(), dz = l.getZ() - c.getZ();
        return dz > -R - 3 && dz < R + 3 && dx > -R - 3 && dx < SPACING * 64 && l.getY() > c.getY() - 4 && l.getY() < c.getY() + WALL + 4;
    }

    private void build(int slot) {
        if (!built.add(slot)) return;
        Location c = center(slot);
        World w = c.getWorld();
        int cx = c.getBlockX(), cy = c.getBlockY(), cz = c.getBlockZ();
        for (int x = -R - 1; x <= R + 1; x++) for (int z = -R - 1; z <= R + 1; z++) {
            boolean edge = Math.abs(x) == R + 1 || Math.abs(z) == R + 1;
            Material floor = edge ? Material.CHISELED_DEEPSLATE : (x % 4 == 0 && z % 4 == 0) ? Material.SEA_LANTERN
                    : ((x + z) & 1) == 0 ? Material.POLISHED_DEEPSLATE : Material.DEEPSLATE_TILES;
            w.getBlockAt(cx + x, cy - 1, cz + z).setType(floor, false);
            w.getBlockAt(cx + x, cy - 2, cz + z).setType(Material.OBSIDIAN, false);
            for (int y = 0; y <= WALL; y++)
                w.getBlockAt(cx + x, cy + y, cz + z).setType(edge ? (y == 0 || y == WALL ? Material.CRYING_OBSIDIAN : Material.PURPLE_STAINED_GLASS) : Material.AIR, false);
            w.getBlockAt(cx + x, cy + WALL + 1, cz + z).setType(Material.BARRIER, false);
        }
        for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2)   // 모서리 기둥 불빛
            w.getBlockAt(cx + sx * R, cy, cz + sz * R).setType(Material.SOUL_LANTERN, false);
    }

    private int freeSlot() {
        Set<Integer> used = new HashSet<>();
        for (Run r : runs.values()) used.add(r.slot);
        int s = 0;
        while (used.contains(s)) s++;
        return s;
    }

    // ------------------------------------------------------------------ 도전
    public boolean inRun(Player p) {
        return runs.containsKey(p.getUniqueId());
    }

    public boolean isTowerMob(Entity e) {
        for (Run r : runs.values()) if (r.mobs.contains(e.getUniqueId())) return true;
        return false;
    }

    private int best(PlayerData d) {
        return (int) d.counter("tower_best");
    }

    private int checkpoint(PlayerData d) {
        return best(d) / 10 * 10;
    }

    private void start(Player p, int from) {
        if (inRun(p)) return;
        if (plugin.dungeons() != null && plugin.dungeons().runOf(p) != null) { Text.msg(p, "&c던전 공략 중에는 들어갈 수 없습니다."); return; }
        PlayerData d = plugin.data().get(p);
        int lim = plugin.getConfig().getInt("tower.daily-entries", 10);
        String key = "tower_day_" + LocalDate.now().toString().replace("-", "");
        if (lim > 0 && d.counter(key) >= lim) { Text.msg(p, "&c오늘 도전 횟수(" + lim + "회)를 모두 썼습니다."); return; }
        d.counters.merge(key, 1.0, Double::sum);
        Run run = new Run(p, freeSlot(), from - 1);
        build(run.slot);
        run.bar = Bukkit.createBossBar(Text.c("&5무한의 탑"), BarColor.PURPLE, BarStyle.SOLID);
        run.bar.addPlayer(p);
        runs.put(p.getUniqueId(), run);
        p.teleport(center(run.slot).add(0, 0.1, -R + 2));
        p.sendTitle(Text.c("&5&l무한의 탑"), Text.c("&f" + from + "층부터 도전 · 층마다 제한 시간 " + floorSeconds() + "초"), 10, 50, 10);
        p.playSound(p.getLocation(), Sound.BLOCK_END_PORTAL_SPAWN, 0.6f, 1.3f);
        run.nextAt = System.currentTimeMillis() + 2500;
    }

    private int floorSeconds() {
        return Math.max(20, plugin.getConfig().getInt("tower.floor-seconds", 120));
    }

    private int level(int floor) {
        return Math.min(plugin.getConfig().getInt("tower.max-level", 400),
                plugin.getConfig().getInt("tower.base-level", 8) + (int) Math.round(floor * plugin.getConfig().getDouble("tower.level-per-floor", 3.0)));
    }

    private static final String[] BOSSES = {"witch", "dwarf_king", "sea_gatekeeper", "bungbung", "frost_queen", "volcano_giant", "void_apostle", "thunder_god", "primordial_dragon"};

    private void nextFloor(Run run) {
        Player p = Bukkit.getPlayer(run.player);
        if (p == null) return;
        run.floor++;
        run.nextAt = 0;
        run.deadline = System.currentTimeMillis() + floorSeconds() * 1000L;
        Location c = center(run.slot);
        int f = run.floor, lv = level(f);
        List<CustomMobManager.MobDef> pool = new ArrayList<>();
        int topMax = 0;
        for (CustomMobManager.MobDef def : plugin.customMobs().defs()) if (def.natural) topMax = Math.max(topMax, def.maxLevel);
        int lvPick = Math.min(lv, topMax);
        for (CustomMobManager.MobDef def : plugin.customMobs().defs())
            if (def.natural && lvPick >= def.minLevel - 5 && lvPick <= def.maxLevel + 5 && def.type != EntityType.PHANTOM && def.type != EntityType.VEX
                    && def.type != EntityType.ENDERMITE && def.type != EntityType.GHAST) pool.add(def);
        String kind;
        if (f % 10 == 0) {
            kind = "&c&l보스 층";
            LivingEntity b = plugin.bosses().spawn(BOSSES[Math.min(BOSSES.length - 1, f / 10 - 1)], c.clone().add(0, 0.2, 4));
            if (b != null) run.mobs.add(b.getUniqueId());
            int extra = f >= 100 ? 2 + (f - 100) / 30 : 0;   // 100층부터는 정예 보스 호위 추가
            for (int i = 0; i < extra; i++) {
                LivingEntity m = spawn(pool, c, lv);
                if (m != null) { plugin.tiers().apply(m, plugin.mobs().peek(m), MonsterTierManager.Tier.MINIBOSS); run.mobs.add(m.getUniqueId()); }
            }
        } else {
            boolean elite = f % 5 == 0;
            kind = elite ? "&6&l정예 층" : "&f일반 층";
            int n = Math.min(10, 3 + f / 8) - (elite ? 1 : 0);
            for (int i = 0; i < n; i++) {
                LivingEntity m = spawn(pool, c, lv);
                if (m == null) continue;
                if (elite || (f > 30 && i % 4 == 0)) plugin.tiers().apply(m, plugin.mobs().peek(m), MonsterTierManager.Tier.ELITE);
                run.mobs.add(m.getUniqueId());
            }
        }
        p.sendTitle(Text.c("&5&l" + f + "층"), Text.c(kind + " &7· 몬스터 Lv." + lv), 5, 30, 8);
        p.playSound(p.getLocation(), f % 10 == 0 ? Sound.ENTITY_WITHER_SPAWN : Sound.BLOCK_BEACON_ACTIVATE, f % 10 == 0 ? 0.5f : 1f, 1.2f);
    }

    private LivingEntity spawn(List<CustomMobManager.MobDef> pool, Location c, int lv) {
        Location at = c.clone().add(rnd.nextDouble() * 16 - 8, 0.2, 2 + rnd.nextDouble() * 8);
        if (!pool.isEmpty()) return plugin.customMobs().spawn(pool.get(rnd.nextInt(pool.size())), at, lv);
        LivingEntity m = (LivingEntity) at.getWorld().spawnEntity(at, EntityType.ZOMBIE);
        var mm = plugin.mobs();
        mm.initCustom(m, lv, mm.hpFor(lv), mm.damageFor(lv), lv * 0.2, mm.expFor(lv), lv * 60L, "탑의 파수꾼");
        return m;
    }

    private void tick() {
        rollWeek();
        long now = System.currentTimeMillis();
        for (Run run : new ArrayList<>(runs.values())) {
            Player p = Bukkit.getPlayer(run.player);
            if (p == null) { end(run, null, "접속 종료"); continue; }
            if (p.isDead()) continue;
            Location c = center(run.slot);
            if (!p.getWorld().equals(c.getWorld()) || p.getLocation().distanceSquared(c) > 40 * 40) { end(run, p, "전투장을 벗어났습니다."); continue; }
            if (run.nextAt > 0) {
                if (now >= run.nextAt) nextFloor(run);
                continue;
            }
            run.mobs.removeIf(u -> { Entity e = Bukkit.getEntity(u); return e == null || e.isDead() || !e.isValid(); });
            for (UUID u : run.mobs) {   // 벽 밖으로 밀려난 몬스터는 다시 안으로
                Entity e = Bukkit.getEntity(u);
                if (e != null && e.getLocation().distanceSquared(c) > (R + 2) * (R + 2) * 2) e.teleport(c.clone().add(0, 0.2, 4));
            }
            long left = Math.max(0, (run.deadline - now) / 1000);
            run.bar.setTitle(Text.c("&5무한의 탑 &f" + run.floor + "층 &7| 남은 몬스터 &c" + run.mobs.size() + " &7| &e" + left / 60 + ":" + String.format("%02d", left % 60)));
            run.bar.setProgress(Math.max(0, Math.min(1, (run.deadline - now) / (floorSeconds() * 1000.0))));
            if (run.mobs.isEmpty()) { cleared(run, p); continue; }
            if (now > run.deadline) end(run, p, "제한 시간이 끝났습니다.");
        }
    }

    private void cleared(Run run, Player p) {
        PlayerData d = plugin.data().get(p);
        int f = run.floor;
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.4f);
        plugin.levels().addExp(p, plugin.levels().need(d.level) * plugin.getConfig().getDouble("tower.exp-ratio-per-floor", 0.015) * (f % 10 == 0 ? 3 : 1));
        if (plugin.seasonPass() != null) plugin.seasonPass().add(p, f % 10 == 0 ? 20 : 4, "무한의 탑");
        if (f > best(d)) {   // 처음 오른 층 보상
            d.counters.put("tower_best", (double) f);
            long money = (long) (f * plugin.getConfig().getLong("tower.money-per-floor", 8000) * (f % 10 == 0 ? 3 : 1));
            plugin.economy().give(p, money);
            String msg = "&d✦ 최고 기록 " + f + "층! &e+" + Text.money(money);
            if (f % 10 == 0) {
                String[] pool = f <= 30 ? new String[]{"crystal_mid", "rune_low", "ticket_rate10"} : f <= 60 ? new String[]{"crystal_high", "rune_mid", "ticket_rate10"}
                        : new String[]{"crystal_top", "rune_high", "ticket_protect"};
                for (String id : pool) {
                    ItemStack it = plugin.items().create(id, 1);
                    if (it != null && plugin.mail() != null) plugin.mail().giveOrMail(p, it, "&5무한의 탑", f + "층 첫 돌파 보상");
                }
                msg += " &7+ 체크포인트 · 돌파 보상";
                if (f >= 50) Text.announce(Text.PREFIX + Text.c("&5&l" + Text.name(p) + "&f님이 무한의 탑 &d" + f + "층&f을 처음 돌파했습니다!"));
            }
            Text.msg(p, msg);
        }
        if (f > weekly.getOrDefault(p.getUniqueId(), 0)) {
            weekly.put(p.getUniqueId(), f);
            names.put(p.getUniqueId(), Text.name(p));
            save();
        }
        Fx.helix(plugin, p, 2.0, 0.8, 14, Color.fromRGB(0xB060FF), Color.fromRGB(0xFFFFFF));
        Text.actionBar(p, "&a" + f + "층 돌파! &7잠시 후 " + (f + 1) + "층 · &f/탑 나가기 &7로 그만두기");
        run.nextAt = System.currentTimeMillis() + 4000;
        plugin.health().healPercent(p, 30);   // 층 사이 체력 30% 회복
    }

    /** 도전 끝: 몬스터 정리 · 원래 자리로 */
    private void end(Run run, Player p, String why) {
        runs.remove(run.player);
        if (run.bar != null) run.bar.removeAll();
        for (UUID u : run.mobs) { Entity e = Bukkit.getEntity(u); if (e != null) e.remove(); }
        Location c = center(run.slot);
        for (Entity e : c.getWorld().getNearbyEntities(c, R + 3, WALL + 3, R + 3))   // 떨어진 아이템 · 남은 것 정리
            if (!(e instanceof Player)) e.remove();
        if (p == null) return;
        int reached = Math.max(0, run.floor - (run.mobs.isEmpty() && run.nextAt > 0 ? 0 : 1));
        if (why != null) Text.msg(p, "&5무한의 탑 &f도전 끝 &7— " + why + " &f(도달 " + reached + "층 · 최고 " + best(plugin.data().get(p)) + "층)");
        if (!p.isDead()) p.teleport(run.back);
    }

    public void leave(Player p) {
        Run r = runs.get(p.getUniqueId());
        if (r != null) end(r, p, "스스로 나왔습니다.");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent e) {
        Run r = runs.get(e.getPlayer().getUniqueId());
        if (r == null) return;
        e.setRespawnLocation(r.back);
        end(r, e.getPlayer(), "쓰러졌습니다.");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Run r = runs.get(e.getPlayer().getUniqueId());
        if (r != null) end(r, e.getPlayer(), null);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (inArena(p.getLocation()) && !inRun(p)) p.teleport(p.getWorld().getSpawnLocation());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (inArena(e.getBlock().getLocation()) && !e.getPlayer().isOp()) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (inArena(e.getBlock().getLocation()) && !e.getPlayer().isOp()) e.setCancelled(true);
    }

    public void shutdown() {
        for (Run r : new ArrayList<>(runs.values())) end(r, Bukkit.getPlayer(r.player), "서버가 종료되었습니다.");
        save();
    }

    // ------------------------------------------------------------------ 창
    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player p)) return true;
        if (a.length > 0 && (a[0].equals("나가기") || a[0].equalsIgnoreCase("leave"))) { leave(p); return true; }
        if (inRun(p)) { Text.msg(p, "&7도전 중입니다. &f/탑 나가기 &7로 그만둘 수 있습니다."); return true; }
        open(p);
        return true;
    }

    public void open(Player p) {
        PlayerData d = plugin.data().get(p);
        int best = best(d), cp = checkpoint(d);
        int lim = plugin.getConfig().getInt("tower.daily-entries", 10);
        int used = (int) d.counter("tower_day_" + LocalDate.now().toString().replace("-", ""));
        Gui g = new Gui(5, "&5무한의 탑") {
        };
        g.set(4, Gui.button(Material.END_CRYSTAL, "&5&l무한의 탑", "&7혼자 도전하는 끝없는 층 오르기",
                "&7층의 몬스터를 모두 쓰러뜨리면 다음 층", "&75층: 정예 층 · 10층: 보스 층 · 층마다 " + floorSeconds() + "초",
                "&7죽거나 시간이 끝나면 도전 종료", "", "&f최고 기록 &d" + best + "층 &7· 이번 주 &d" + weekly.getOrDefault(p.getUniqueId(), 0) + "층",
                lim > 0 ? "&f오늘 도전 &e" + used + " / " + lim : "&f도전 횟수 제한 없음"), null);
        g.set(20, Gui.button(Material.STONE_STAIRS, "&f&l1층부터 도전", "&7몬스터 Lv." + level(1) + "부터", "", "&e▶ 클릭"), e -> { p.closeInventory(); start(p, 1); });
        if (cp > 0) g.set(22, Gui.button(Material.PURPUR_STAIRS, "&d&l" + (cp + 1) + "층부터 도전 &7(체크포인트)", "&710층마다 체크포인트가 생깁니다",
                "&7몬스터 Lv." + level(cp + 1) + "부터", "", "&e▶ 클릭"), e -> { p.closeInventory(); start(p, cp + 1); });
        else g.set(22, Gui.button(Material.GRAY_DYE, "&8체크포인트 없음", "&710층을 넘으면 그 다음 층부터 시작할 수 있습니다"), null);
        List<String> rw = new ArrayList<>(List.of("&7처음 오른 층마다 &e" + Text.money(plugin.getConfig().getLong("tower.money-per-floor", 8000)) + " × 층",
                "&710층마다 3배 + 결정 · 룬 · 주문서", "", "&f주간 순위 보상 &7(월요일 우편)", "&61위 &f1500만 + 마스터 큐브 3", "&e2위 &f1천만 + 마스터 큐브 2",
                "&e3위 &f5백만 + 마스터 큐브 1", "&f4~10위 &f250만 + 레드 큐브 3", "&7모두: 최고 층 × " + Text.money(plugin.getConfig().getLong("tower.weekly-money-per-floor", 20000))));
        g.set(24, Gui.button(Material.CHEST, "&6&l보상", rw.toArray(new String[0])), null);
        List<String> rk = new ArrayList<>();
        List<Map.Entry<UUID, Integer>> rank = ranking();
        for (int i = 0; i < Math.min(10, rank.size()); i++)
            rk.add((i == 0 ? "&6" : i < 3 ? "&e" : "&f") + (i + 1) + ". " + names.getOrDefault(rank.get(i).getKey(), "?") + " &7— &d" + rank.get(i).getValue() + "층");
        if (rk.isEmpty()) rk.add("&7아직 기록이 없습니다");
        int mine = -1;
        for (int i = 0; i < rank.size(); i++) if (rank.get(i).getKey().equals(p.getUniqueId())) mine = i;
        rk.add("");
        rk.add(mine >= 0 ? "&f내 순위: &d" + (mine + 1) + "위" : "&7이번 주 기록 없음");
        g.set(31, Gui.button(Material.GOLDEN_HELMET, "&e&l이번 주 순위", rk.toArray(new String[0])), null);
        g.fill(0, 44);
        g.open(p);
    }
}
