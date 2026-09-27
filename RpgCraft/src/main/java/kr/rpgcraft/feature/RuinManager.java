package kr.rpgcraft.feature;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.data.PlayerData;
import kr.rpgcraft.passive.Passive;
import kr.rpgcraft.util.Locs;
import kr.rpgcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * 유적(점프맵/던전). 시작 블록을 밟으면 기록 시작, 도착 블록을 밟으면 클리어.
 * 모험 스탯 요구치, 회차당 1회 보상, 최초 클리어 패시브 보상을 지원한다.
 */
public class RuinManager implements Listener, org.bukkit.command.CommandExecutor {
    public static class Ruin {
        public String id, name, start, end, passive, firstPassive;
        public int minAdv, timeLimit;
        public long money;
        public double exp;
        public List<String> items = new ArrayList<>();
        public boolean firstCleared;
    }

    private final RpgCraft plugin;
    private final File file;
    private final Map<String, Ruin> ruins = new LinkedHashMap<>();
    private final Map<String, String> startIndex = new HashMap<>(), endIndex = new HashMap<>();

    public RuinManager(RpgCraft plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "ruins.yml");
        load();
        Bukkit.getScheduler().runTaskTimer(plugin, this::timeoutTick, 20L, 20L);
    }

    public Collection<Ruin> all() {
        return ruins.values();
    }

    public Ruin get(String id) {
        return ruins.get(id);
    }

    public void load() {
        ruins.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String id : y.getKeys(false)) {
            ConfigurationSection s = y.getConfigurationSection(id);
            if (s == null) continue;
            Ruin r = new Ruin();
            r.id = id;
            r.name = s.getString("name", id);
            r.start = s.getString("start");
            r.end = s.getString("end");
            r.minAdv = s.getInt("min-adv", 0);
            r.timeLimit = s.getInt("time-limit", 0);
            r.money = s.getLong("reward.money", 100000);
            r.exp = s.getDouble("reward.exp", 1000);
            r.items = s.getStringList("reward.items");
            r.passive = s.getString("reward.passive");
            r.firstPassive = s.getString("reward.first-passive");
            r.firstCleared = s.getBoolean("first-cleared", false);
            ruins.put(id, r);
        }
        reindex();
    }

    private void reindex() {
        startIndex.clear();
        endIndex.clear();
        for (Ruin r : ruins.values()) {
            if (r.start != null) startIndex.put(r.start, r.id);
            if (r.end != null) endIndex.put(r.end, r.id);
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Ruin r : ruins.values()) {
            String k = r.id + ".";
            y.set(k + "name", r.name);
            y.set(k + "start", r.start);
            y.set(k + "end", r.end);
            y.set(k + "min-adv", r.minAdv);
            y.set(k + "time-limit", r.timeLimit);
            y.set(k + "reward.money", r.money);
            y.set(k + "reward.exp", r.exp);
            y.set(k + "reward.items", r.items);
            y.set(k + "reward.passive", r.passive);
            y.set(k + "reward.first-passive", r.firstPassive);
            y.set(k + "first-cleared", r.firstCleared);
        }
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("ruins.yml 저장 실패: " + e.getMessage());
        }
        reindex();
    }

    // ------------------------------------------------------------------ 관리자
    public Ruin create(String id, String name) {
        Ruin r = new Ruin();
        r.id = id;
        r.name = name;
        ruins.put(id, r);
        save();
        return r;
    }

    public void delete(String id) {
        ruins.remove(id);
        save();
    }

    private static Block standing(Location l) {
        Block feet = l.getBlock();
        return feet.getType().isSolid() ? feet : feet.getRelative(BlockFace.DOWN);
    }

    public void setPoint(Ruin r, Location l, boolean start) {
        String key = Locs.block(l);
        if (start) r.start = key;
        else r.end = key;
        save();
    }

    public void setPoint(Ruin r, Player p, boolean start) {
        String key = Locs.block(standing(p.getLocation()));
        if (start) r.start = key;
        else r.end = key;
        save();
    }

    // ------------------------------------------------------------------ 진행
    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location to = e.getTo();
        if (to == null || (e.getFrom().getBlockX() == to.getBlockX() && e.getFrom().getBlockY() == to.getBlockY() && e.getFrom().getBlockZ() == to.getBlockZ()))
            return;
        Player p = e.getPlayer();
        String key = Locs.block(standing(to));
        String startId = startIndex.get(key);
        if (startId != null) start(p, ruins.get(startId));
        String endId = endIndex.get(key);
        if (endId != null) finish(p, ruins.get(endId));
    }

    private void start(Player p, Ruin r) {
        PlayerData d = plugin.data().get(p);
        if (r.id.equals(d.ruinId)) return;
        if (d.stats.adv < r.minAdv) {
            Text.actionBar(p, "&c이 유적은 모험 " + r.minAdv + " 이상만 도전할 수 있습니다. (현재 " + (int) d.stats.adv + ")");
            return;
        }
        d.ruinId = r.id;
        d.ruinStart = System.currentTimeMillis();
        p.sendTitle(Text.c("&6" + r.name), Text.c(r.timeLimit > 0 ? "&f제한 시간 " + Text.time(r.timeLimit) : "&f도전 시작!"), 5, 30, 5);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f);
    }

    private void finish(Player p, Ruin r) {
        PlayerData d = plugin.data().get(p);
        if (!r.id.equals(d.ruinId)) return;
        long ms = System.currentTimeMillis() - d.ruinStart;
        d.ruinId = null;
        int round = plugin.rounds().round();
        String counter = "ruin_" + r.id;
        kr.rpgcraft.util.Text.announce(Text.PREFIX + Text.c("&e" + Text.name(p) + "&f님이 유적 &6" + r.name + "&f을(를) 클리어했습니다! &7(" + String.format("%.1f", ms / 1000.0) + "초)"));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        if (!r.firstCleared && r.firstPassive != null) {
            r.firstCleared = true;
            save();
            Passive fp = Passive.find(r.firstPassive);
            if (fp != null) plugin.passives().grant(p, fp, true);
        }
        if (d.roundCounter(counter, round) >= 1) {
            Text.msg(p, "&7이번 회차에는 이미 보상을 받은 유적입니다.");
            return;
        }
        d.addRoundCounter(counter, 1, round);
        plugin.economy().give(p, r.money);
        plugin.levels().addExp(p, r.exp);
        for (String s : r.items) {
            String[] kv = s.split(":");
            ItemStack it = plugin.items().create(kv[0], kv.length > 1 ? Text.parseInt(kv[1], 1) : 1);
            if (it != null) for (ItemStack left : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
        }
        if (r.passive != null) {
            Passive ps = Passive.find(r.passive);
            if (ps != null) plugin.passives().grant(p, ps, false);
        }
        Text.msg(p, "&a유적 보상: " + Text.money(r.money) + ", 경험치 " + Text.num(r.exp));
    }

    // ------------------------------------------------------------------ /유적 (v5.4.29): 위치 안내
    public Location startOf(Ruin r) {
        Location l = Locs.parse(r.start);
        return l == null ? null : l.add(0.5, 1, 0.5);
    }

    private static String dir(double dx, double dz) {
        String[] n = {"남", "남서", "서", "북서", "북", "북동", "동", "남동"};   // 마인크래프트: +z = 남
        double deg = (Math.toDegrees(Math.atan2(-dx, dz)) + 360 + 22.5) % 360;
        return n[(int) (deg / 45) % 8];
    }

    /** 가까운 순 유적 목록 */
    private List<Ruin> sorted(Player p) {
        List<Ruin> ls = new ArrayList<>();
        for (Ruin r : ruins.values()) if (startOf(r) != null) ls.add(r);
        ls.sort(Comparator.comparingDouble(r -> {
            Location l = startOf(r);
            return l.getWorld() == p.getWorld() ? l.distanceSquared(p.getLocation()) : Double.MAX_VALUE;
        }));
        return ls;
    }

    @Override
    public boolean onCommand(org.bukkit.command.CommandSender s, org.bukkit.command.Command c, String label, String[] a) {
        if (!(s instanceof Player p)) return true;
        PlayerData d = plugin.data().get(p);
        List<Ruin> ls = sorted(p);
        if (ls.isEmpty()) { Text.msg(p, "&7아직 발견된 유적이 없습니다."); return true; }
        int round = plugin.rounds().round();
        if (a.length >= 1) {   // /유적 <번호> : 방향 안내 + 나침반
            int i = Text.parseInt(a[0], 0) - 1;
            Ruin r = i >= 0 && i < ls.size() ? ls.get(i) : get(a[0]);
            if (r == null) { Text.msg(p, "&c/유적 <번호> &7(번호는 /유적 목록)"); return true; }
            Location l = startOf(r);
            if (l.getWorld() != p.getWorld()) { Text.msg(p, "&e" + r.name + "&7은(는) 다른 월드(" + l.getWorld().getName() + ")에 있습니다."); return true; }
            p.setCompassTarget(l);
            double dx = l.getX() - p.getLocation().getX(), dz = l.getZ() - p.getLocation().getZ();
            Text.msg(p, "&6" + r.name + " &f→ &e" + dir(dx, dz) + "쪽 " + (int) Math.hypot(dx, dz) + "칸 &7(좌표 " + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ() + ")");
            Text.msg(p, "&7나침반이 이 유적을 가리킵니다. 필요 모험 &e" + r.minAdv + (d.stats.adv < r.minAdv ? " &c(현재 " + (int) d.stats.adv + " — 부족)" : " &a(입장 가능)"));
            return true;
        }
        Text.msg(p, "&6&l유적 목록 &7(가까운 순 · /유적 <번호> 로 방향 안내)");
        for (int i = 0; i < ls.size() && i < 15; i++) {
            Ruin r = ls.get(i);
            Location l = startOf(r);
            String where = l.getWorld() == p.getWorld()
                    ? (int) Math.hypot(l.getX() - p.getLocation().getX(), l.getZ() - p.getLocation().getZ()) + "칸 " + dir(l.getX() - p.getLocation().getX(), l.getZ() - p.getLocation().getZ()) + "쪽"
                    : l.getWorld().getName();
            boolean done = d.roundCounter("ruin_" + r.id, round) >= 1;
            p.sendMessage(Text.c(" &e" + (i + 1) + ". &f" + r.name + " &7모험 " + (d.stats.adv >= r.minAdv ? "&a" : "&c") + r.minAdv
                    + " &7· " + where + " &8(" + l.getBlockX() + ", " + l.getBlockZ() + ")" + (done ? " &a[이번 회차 클리어]" : "")));
        }
        return true;
    }

    private void timeoutTick() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData d = plugin.data().get(p);
            if (d.ruinId == null) continue;
            Ruin r = ruins.get(d.ruinId);
            if (r == null) {
                d.ruinId = null;
                continue;
            }
            if (r.timeLimit <= 0) continue;
            long left = r.timeLimit - (now - d.ruinStart) / 1000;
            if (left <= 0) {
                d.ruinId = null;
                p.sendTitle(Text.c("&c시간 초과"), Text.c("&7" + r.name + " 도전 실패"), 5, 30, 5);
            } else if (d.actionBarLock < now) {
                Text.actionBar(p, "&6" + r.name + " &f남은 시간 &e" + Text.time(left));
                d.actionBarLock = now + 900;
            }
        }
    }
}
