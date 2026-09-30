package kr.rpgcraft.war;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.guild.Guild;
import kr.rpgcraft.item.Category;
import kr.rpgcraft.item.ItemData;
import kr.rpgcraft.util.Locs;
import kr.rpgcraft.util.Text;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * 공성전.
 * 선포(전쟁권 소모) → 준비 시간 → 전쟁 시작. 공격측은 채집도구로 성벽을 두드려 체력을 깎고,
 * 모든 성벽(설정값)이 무너지면 신호기를 부숴 승리한다. 시간 초과 시 방어측 승리.
 */
public class WarManager {
    public static class War {
        public Castle castle;
        public String attacker, defender;
        public long prepEnd, end;
        public boolean started;
        public BossBar bar;
    }

    private final RpgCraft plugin;
    private final File file;
    private final Map<String, Castle> castles = new LinkedHashMap<>();
    private final Map<String, War> wars = new HashMap<>();
    private final Map<UUID, Location[]> selections = new HashMap<>();
    private final Map<UUID, Long> repairStart = new HashMap<>();
    private final Map<UUID, Long> hitCooldown = new HashMap<>();

    public WarManager(RpgCraft plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "castles.yml");
        load();
        restoreLeftoverSnapshots();
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::repairTick, 10L, 10L);
    }

    // ------------------------------------------------------------------ 조회
    public Collection<Castle> castles() {
        return castles.values();
    }

    public Castle castle(String id) {
        return castles.get(id);
    }

    public War warOf(Castle c) {
        return wars.get(c.id);
    }

    public Collection<War> activeWars() {
        return wars.values();
    }

    public boolean isAtWar(String a, String b) {
        for (War w : wars.values()) {
            if (!w.started) continue;
            if ((w.attacker.equals(a) && b.equals(w.defender)) || (w.attacker.equals(b) && a.equals(w.defender))) return true;
        }
        return false;
    }

    public War warOfGuild(String guild) {
        for (War w : wars.values()) if (w.attacker.equals(guild) || guild.equals(w.defender)) return w;
        return null;
    }

    public String castlesOwnedBy(String guild) {
        StringJoiner j = new StringJoiner(", ");
        for (Castle c : castles.values()) if (guild.equals(c.owner)) j.add(c.name);
        return j.toString();
    }

    public Castle castleAt(Location l) {
        for (Castle c : castles.values()) if (c.wallAt(l) != null || c.isBeacon(l)) return c;
        return null;
    }

    /**
     * v5.10.13 이 위치가 성 부지 안인지 (가로세로만 봄 — 성 안 땅 · 성벽 위 · 탑 꼭대기 모두).
     * 자동으로 지은 성은 부지 반지름, 손으로 만든 성은 성벽 · 신호기를 감싸는 범위 + margin 칸
     */
    public Castle castleArea(Location l, int margin) {
        if (l == null || l.getWorld() == null) return null;
        for (Castle c : castles.values()) {
            if (c.center != null && c.r > 0) {
                if (!l.getWorld().equals(c.center.getWorld())) continue;
                if (Math.abs(l.getBlockX() - c.center.getBlockX()) <= c.r + margin && Math.abs(l.getBlockZ() - c.center.getBlockZ()) <= c.r + margin) return c;
                continue;
            }
            int x1 = Integer.MAX_VALUE, z1 = Integer.MAX_VALUE, x2 = Integer.MIN_VALUE, z2 = Integer.MIN_VALUE;
            boolean any = false;
            for (Castle.Wall w : c.walls) {
                if (w.min == null || !l.getWorld().equals(w.min.getWorld())) continue;
                any = true;
                x1 = Math.min(x1, w.min.getBlockX()); z1 = Math.min(z1, w.min.getBlockZ());
                x2 = Math.max(x2, w.max.getBlockX()); z2 = Math.max(z2, w.max.getBlockZ());
            }
            if (c.beacon != null && l.getWorld().equals(c.beacon.getWorld())) {
                any = true;
                x1 = Math.min(x1, c.beacon.getBlockX()); z1 = Math.min(z1, c.beacon.getBlockZ());
                x2 = Math.max(x2, c.beacon.getBlockX()); z2 = Math.max(z2, c.beacon.getBlockZ());
            }
            if (any && l.getBlockX() >= x1 - margin && l.getBlockX() <= x2 + margin && l.getBlockZ() >= z1 - margin && l.getBlockZ() <= z2 + margin) return c;
        }
        return null;
    }

    // ------------------------------------------------------------------ v5.10.14 성벽 설치권 (전쟁 상점)
    private record WallPlan(String castle, Location min, Location max, long at) {}
    private final Map<UUID, WallPlan> wallPlans = new HashMap<>();

    /** 우클릭 1번: 설치 자리 미리보기 / 5초 안에 같은 자리에서 한 번 더: 설치 */
    public void useWallTicket(Player p, ItemStack it, boolean large) {
        Guild g = plugin.guilds().of(p.getUniqueId());
        if (g == null) { Text.actionBar(p, "&c길드에 가입해야 쓸 수 있습니다."); return; }
        Castle c = castleArea(p.getLocation(), 0);
        if (c == null || !g.name.equals(c.owner)) { Text.actionBar(p, "&c우리 길드가 차지한 성 안에서만 성벽을 세울 수 있습니다."); return; }
        if (wars.containsKey(c.id)) { Text.actionBar(p, "&c공성전 중에는 성벽을 세울 수 없습니다."); return; }
        int max = plugin.getConfig().getInt("war.guild-walls-max", 6);
        long mine = c.walls.stream().filter(x -> x.id.startsWith("guild")).count();
        if (mine >= max) { Text.actionBar(p, "&c이 성에는 성벽을 " + max + "개까지만 더 세울 수 있습니다."); return; }
        int wd = large ? 11 : 7, h = large ? 7 : 5, t = large ? 3 : 2;
        // 바라보는 방향(동서남북)으로 3칸 앞에, 그 방향을 가로막는 벽
        float yaw = (p.getLocation().getYaw() % 360 + 360) % 360;
        boolean alongX = (yaw >= 315 || yaw < 45) || (yaw >= 135 && yaw < 225);   // 남 · 북을 보면 벽은 동서로 길게
        int fx = alongX ? 0 : (yaw < 135 ? -1 : 1), fz = alongX ? (yaw >= 135 && yaw < 225 ? -1 : 1) : 0;
        Location base = p.getLocation().getBlock().getLocation().add(fx * 3, 0, fz * 3);
        int x1, x2, z1, z2;
        if (alongX) { x1 = base.getBlockX() - wd / 2; x2 = x1 + wd - 1; z1 = fz > 0 ? base.getBlockZ() : base.getBlockZ() - t + 1; z2 = z1 + t - 1; }
        else { z1 = base.getBlockZ() - wd / 2; z2 = z1 + wd - 1; x1 = fx > 0 ? base.getBlockX() : base.getBlockX() - t + 1; x2 = x1 + t - 1; }
        World w = p.getWorld();
        int y1 = base.getBlockY(), y2 = y1 + h - 1;
        // v5.10.15 성문 · 뚫린 입구 앞이면 그 입구 크기에 딱 맞춰 막음 (소형: 너비 7 · 높이 9 까지, 대형: 너비 11 · 높이 13 까지)
        int[] fit = fitOpening(w, p.getLocation().getBlockX(), p.getLocation().getBlockY(), p.getLocation().getBlockZ(), fx, fz, alongX, large ? 11 : 7, large ? 13 : 9);
        boolean fitted = fit != null;
        if (fitted) { x1 = fit[0]; y1 = fit[1]; z1 = fit[2]; x2 = fit[3]; y2 = fit[4]; z2 = fit[5]; h = y2 - y1 + 1; }
        Location min = new Location(w, x1, y1, z1), max2 = new Location(w, x2, y2, z2);
        // 자리 확인: 모두 성 안 · 빈 공간(풀 · 꽃 정도는 괜찮음) · 다른 성벽 · 신호기와 겹치지 않음 · 사람이 서 있지 않음
        String bad = null;
        for (int x = x1; x <= x2 && bad == null; x++)
            for (int z = z1; z <= z2 && bad == null; z++) {
                if (castleArea(new Location(w, x, y1, z), 0) != c) { bad = "성 밖으로 나갑니다"; break; }
                for (int y = y1; y <= y2; y++) {
                    Block b = w.getBlockAt(x, y, z);
                    if (!b.isPassable() || b.isLiquid()) { bad = "막힌 곳이 있습니다 (" + x + ", " + y + ", " + z + ")"; break; }
                    if (c.isBeacon(b.getLocation())) { bad = "신호기와 겹칩니다"; break; }
                    Castle.Wall on = c.wallAt(b.getLocation());
                    if (on != null && (!fitted || on.id.startsWith("guild"))) { bad = "다른 성벽과 겹칩니다"; break; }
                }
            }
        if (bad == null)
            for (Player o : w.getPlayers()) {
                Location l = o.getLocation();
                if (l.getBlockX() >= x1 && l.getBlockX() <= x2 && l.getBlockZ() >= z1 && l.getBlockZ() <= z2 && l.getBlockY() >= y1 - 1 && l.getBlockY() <= y2) { bad = "설치 자리에 사람이 있습니다"; break; }
            }
        outline(min, max2, bad == null ? Color.fromRGB(0x5AFF7A) : Color.fromRGB(0xFF3A3A));
        if (bad != null) { Text.actionBar(p, "&c여기에는 세울 수 없습니다: " + bad); wallPlans.remove(p.getUniqueId()); return; }
        {
            WallPlan prev = wallPlans.get(p.getUniqueId());
            long now = System.currentTimeMillis();
            if (prev == null || now - prev.at() > 5000 || !prev.castle().equals(c.id) || !prev.min().equals(min) || !prev.max().equals(max2)) {
                wallPlans.put(p.getUniqueId(), new WallPlan(c.id, min, max2, now));
                Text.actionBar(p, (fitted ? "&b성문 크기에 맞춤 (" + (Math.abs(alongX ? x2 - x1 : z2 - z1) + 1) + "×" + h + ") &a" : "&a초록 테두리 자리에 성벽을 세웁니다. ")
                        + "&e5초 안에 한 번 더 우클릭 &7(자리를 옮기면 다시 미리보기)");
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.4f);
                return;
            }
        }
        wallPlans.remove(p.getUniqueId());
        it.setAmount(it.getAmount() - 1);
        // 성벽 쌓기: 석재 벽돌 몸통 · 이끼/금 간 벽돌 섞기 · 맨 윗줄은 톱니 난간
        java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++)
                for (int y = y1; y <= y2; y++) {
                    int along = alongX ? x - x1 : z - z1;
                    Material m;
                    if (y == y2 && !fitted) m = along % 2 == 0 ? Material.STONE_BRICKS : Material.AIR;
                    else if (fitted && y == y2) m = Material.STONE_BRICKS;
                    else if (y == y1) m = Material.CHISELED_STONE_BRICKS;
                    else { double q = r.nextDouble(); m = q < 0.12 ? Material.MOSSY_STONE_BRICKS : q < 0.24 ? Material.CRACKED_STONE_BRICKS : Material.STONE_BRICKS; }
                    w.getBlockAt(x, y, z).setType(m, false);
                }
        int n = 1;
        while (true) { String cand = "guild" + n; if (c.walls.stream().noneMatch(x -> x.id.equals(cand))) break; n++; }
        double hp = large ? plugin.getConfig().getDouble("war.guild-wall-hp-large", 60000) : plugin.getConfig().getDouble("war.guild-wall-hp-small", 30000);
        addWall(c, "guild" + n, min, new Location(w, x2, fitted ? y2 : y2 - 1, z2), hp);   // 톱니 윗줄 사이 빈칸은 성벽 범위에서 뺌 (성문 막이는 꽉 채움)
        Location mid = min.clone().add((x2 - x1) / 2.0 + 0.5, h / 2.0, (z2 - z1) / 2.0 + 0.5);
        w.spawnParticle(Particle.BLOCK_CRACK, mid, 60, (x2 - x1) / 2.0, h / 2.0, (z2 - z1) / 2.0, Material.STONE_BRICKS.createBlockData());
        w.playSound(mid, Sound.BLOCK_ANVIL_LAND, 1f, 0.6f);
        Text.msg(p, "&a" + c.name + "에 성벽 &eguild" + n + " &a을(를) 세웠습니다. &7(체력 " + Text.num(hp) + " · 이 성의 길드 성벽 " + (mine + 1) + "/" + max + ")");
    }

    /**
     * v5.10.15 앞쪽(최대 8칸)에서 양옆 · 위가 막힌 통로(성문)를 찾아 그 크기를 돌려줌 {x1,y1,z1,x2,y2,z2}, 없거나 너무 크면 null.
     * 통로 깊이(성벽 두께)만큼 꽉 채움 (최대 6칸)
     */
    private static int[] fitOpening(World w, int px, int py, int pz, int fx, int fz, boolean alongX, int maxW, int maxH) {
        int ax = alongX ? 1 : 0, az = alongX ? 0 : 1;
        for (int s = 1; s <= 8; s++) {
            int cx = px + fx * s, cz = pz + fz * s;
            if (!open(w, cx, py, cz)) continue;
            int l = 0, r = 0;
            while (l <= maxW && open(w, cx - ax * (l + 1), py, cz - az * (l + 1))) l++;
            while (r <= maxW && open(w, cx + ax * (r + 1), py, cz + az * (r + 1))) r++;
            int width = l + r + 1;
            if (width > maxW || width < 2) continue;
            int h = 0;
            while (h <= maxH && open(w, cx, py + h, cz)) h++;
            if (h > maxH || h < 2) continue;
            // 양옆 기둥이 입구 높이만큼 막혀 있어야 성문으로 봄
            boolean framed = true;
            for (int y = py; y < py + h && framed; y++)
                framed = !open(w, cx - ax * (l + 1), y, cz - az * (l + 1)) && !open(w, cx + ax * (r + 1), y, cz + az * (r + 1));
            if (!framed) continue;
            int depth = 0;   // 양옆과 위가 막힌 채로 이어지는 만큼 (성벽 두께)
            while (depth < 6) {
                int dx = cx + fx * depth, dz = cz + fz * depth;
                if (!open(w, dx, py, dz) || open(w, dx - ax * (l + 1), py, dz - az * (l + 1)) || open(w, dx + ax * (r + 1), py, dz + az * (r + 1)) || open(w, dx, py + h, dz)) break;
                depth++;
            }
            if (depth == 0) continue;
            int ex = cx + fx * (depth - 1), ez = cz + fz * (depth - 1);
            int x1 = Math.min(cx - ax * l, ex - ax * l), x2 = Math.max(cx + ax * r, ex + ax * r);
            int z1 = Math.min(cz - az * l, ez - az * l), z2 = Math.max(cz + az * r, ez + az * r);
            return new int[]{x1, py, z1, x2, py + h - 1, z2};
        }
        return null;
    }

    private static boolean open(World w, int x, int y, int z) {
        Block b = w.getBlockAt(x, y, z);
        return b.isPassable() && !b.isLiquid();
    }

    private void outline(Location min, Location max, Color col) {
        World w = min.getWorld();
        Particle.DustOptions o = new Particle.DustOptions(col, 1.2f);
        double x1 = min.getBlockX(), y1 = min.getBlockY(), z1 = min.getBlockZ(), x2 = max.getBlockX() + 1, y2 = max.getBlockY() + 1, z2 = max.getBlockZ() + 1;
        for (int k = 0; k < 4; k++) {
            long delay = k * 20L;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (double x = x1; x <= x2; x += 0.5) for (double[] yz : new double[][]{{y1, z1}, {y1, z2}, {y2, z1}, {y2, z2}}) w.spawnParticle(Particle.REDSTONE, new Location(w, x, yz[0], yz[1]), 1, 0, 0, 0, 0, o);
                for (double z = z1; z <= z2; z += 0.5) for (double[] xy : new double[][]{{x1, y1}, {x2, y1}, {x1, y2}, {x2, y2}}) w.spawnParticle(Particle.REDSTONE, new Location(w, xy[0], xy[1], z), 1, 0, 0, 0, 0, o);
                for (double y = y1; y <= y2; y += 0.5) for (double[] xz : new double[][]{{x1, z1}, {x1, z2}, {x2, z1}, {x2, z2}}) w.spawnParticle(Particle.REDSTONE, new Location(w, xz[0], y, xz[1]), 1, 0, 0, 0, 0, o);
            }, delay);
        }
    }

    /** 성 안에서는 몬스터가 나오지 않음 (설정 war.no-mob-spawn) */
    public boolean noMobs(Location l) {
        return plugin.getConfig().getBoolean("war.no-mob-spawn", true) && castleArea(l, plugin.getConfig().getInt("war.no-mob-margin", 4)) != null;
    }

    // ------------------------------------------------------------------ 선포 / 진행
    public void declare(Player p, String castleId) {
        Guild g = plugin.guilds().of(p.getUniqueId());
        if (g == null || !g.isLeader(p.getUniqueId())) { Text.msg(p, "&c길드장만 전쟁을 선포할 수 있습니다."); return; }
        Castle c = castles.get(castleId);
        if (c == null) { Text.msg(p, "&c존재하지 않는 성입니다. &7(/전쟁 목록)"); return; }
        if (g.name.equals(c.owner)) { Text.msg(p, "&c자신의 성에는 선포할 수 없습니다."); return; }
        if (wars.containsKey(c.id)) { Text.msg(p, "&c이미 전쟁이 진행 중인 성입니다."); return; }
        if (warOfGuild(g.name) != null) { Text.msg(p, "&c이미 다른 전쟁에 참여 중입니다."); return; }
        if (c.owner != null && warOfGuild(c.owner) != null) { Text.msg(p, "&c상대 길드가 다른 전쟁 중입니다."); return; }
        if (c.walls.isEmpty() || c.beacon == null) { Text.msg(p, "&c아직 준비되지 않은 성입니다. (관리자 설정 필요)"); return; }
        var st = plugin.rounds().state();
        String key = "war-declares." + g.name;
        int limit = plugin.getConfig().getInt("war.declares-per-round", 1);
        if (st.getInt(key) >= limit) { Text.msg(p, "&c이번 회차의 전쟁 선포 횟수를 모두 사용했습니다."); return; }
        if (!consume(p, "ticket_war")) { Text.msg(p, "&c전쟁권이 필요합니다."); return; }
        st.set(key, st.getInt(key) + 1);
        plugin.rounds().save();

        War w = new War();
        w.castle = c;
        w.attacker = g.name;
        w.defender = c.owner;
        long now = System.currentTimeMillis();
        w.prepEnd = now + plugin.getConfig().getLong("war.prepare-seconds", 600) * 1000;
        w.end = w.prepEnd + plugin.getConfig().getLong("war.duration-seconds", 1200) * 1000;
        w.bar = Bukkit.createBossBar(Text.c("&c전쟁 준비"), BarColor.RED, BarStyle.SEGMENTED_10);
        for (Player o : Bukkit.getOnlinePlayers()) w.bar.addPlayer(o);
        wars.put(c.id, w);
        kr.rpgcraft.util.Text.announce(Text.PREFIX + Text.c("&c&l[전쟁 선포] &b" + g.name + "&f 길드가 &e" + c.name
                + (c.owner == null ? "" : "&f(&b" + c.owner + "&f)") + "&f에 전쟁을 선포했습니다! &7"
                + plugin.getConfig().getLong("war.prepare-seconds", 600) / 60 + "분 후 시작"));
        for (Player o : Bukkit.getOnlinePlayers()) o.playSound(o.getLocation(), Sound.EVENT_RAID_HORN, 1f, 1f);
    }

    private boolean consume(Player p, String id) {
        for (ItemStack it : p.getInventory().getContents()) {
            if (ItemData.is(it, id)) {
                it.setAmount(it.getAmount() - 1);
                return true;
            }
        }
        return false;
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (War w : new ArrayList<>(wars.values())) {
            for (Player o : Bukkit.getOnlinePlayers()) if (!w.bar.getPlayers().contains(o)) w.bar.addPlayer(o);
            if (!w.started) {
                long left = (w.prepEnd - now) / 1000;
                w.bar.setTitle(Text.c("&e" + w.castle.name + " &f전쟁 준비 중 &7- &c" + Text.time(left)));
                w.bar.setProgress(Math.max(0, Math.min(1, left / (double) plugin.getConfig().getLong("war.prepare-seconds", 600))));
                if (now >= w.prepEnd) start(w);
                continue;
            }
            long left = (w.end - now) / 1000;
            Castle c = w.castle;
            StringBuilder sb = new StringBuilder("&c⚔ &b" + w.attacker + " &fvs &b" + (w.defender == null ? "무주지" : w.defender) + " &7| ");
            double sum = 0, max = 0;
            for (Castle.Wall wall : c.walls) {
                sb.append(wall.broken ? "&8■" : "&a■");
                sum += Math.max(0, wall.hp);
                max += wall.maxHp;
            }
            sb.append(" &7| &e").append(Text.time(left));
            w.bar.setTitle(Text.c(sb.toString()));
            w.bar.setProgress(max <= 0 ? 0 : Math.max(0, Math.min(1, sum / max)));
            if (now >= w.end) {
                kr.rpgcraft.util.Text.announce(Text.PREFIX + Text.c("&e" + c.name + " &f공성전 시간 종료! &b"
                        + (w.defender == null ? "성을 지켜낸 길드가 없어 무효" : w.defender + " &f길드가 성을 지켜냈습니다.")));
                end(w);
            }
        }
    }

    private void start(War w) {
        w.started = true;
        Castle c = w.castle;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (Castle.Wall wall : c.walls) {
            wall.hp = wall.maxHp;
            wall.broken = false;
            y.set(c.id + ".snapshot." + wall.id, snapshot(wall));
        }
        saveYaml(y);
        kr.rpgcraft.util.Text.announce(Text.PREFIX + Text.c("&c&l[공성전 시작] &e" + c.name + " &f- 채집도구로 성벽을 부수고 신호기를 파괴하세요!"));
        for (Player o : Bukkit.getOnlinePlayers()) {
            o.playSound(o.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7f, 1f);
            Guild g = plugin.guilds().of(o.getUniqueId());
            if (g == null) continue;
            if (g.name.equals(w.attacker) && c.attackerSpawn != null) o.teleport(c.attackerSpawn);
            if (g.name.equals(w.defender) && c.defenderSpawn != null) o.teleport(c.defenderSpawn);
        }
    }

    private List<String> snapshot(Castle.Wall wall) {
        List<String> out = new ArrayList<>();
        World wd = wall.min.getWorld();
        int limit = plugin.getConfig().getInt("war.max-wall-blocks", 30000);
        for (int x = wall.min.getBlockX(); x <= wall.max.getBlockX(); x++)
            for (int y = wall.min.getBlockY(); y <= wall.max.getBlockY(); y++)
                for (int z = wall.min.getBlockZ(); z <= wall.max.getBlockZ(); z++) {
                    Block b = wd.getBlockAt(x, y, z);
                    if (b.getType().isAir()) continue;
                    out.add(x + "," + y + "," + z + "|" + b.getBlockData().getAsString());
                    if (out.size() >= limit) return out;
                }
        return out;
    }

    /** 채집도구로 성벽 타격 */
    public boolean hitWall(Player p, Block block, ItemStack tool) {
        Castle c = castleAt(block.getLocation());
        if (c == null) return false;
        War w = wars.get(c.id);
        Castle.Wall wall = c.wallAt(block.getLocation());
        if (wall == null || w == null || !w.started || wall.broken) return true;
        Guild g = plugin.guilds().of(p.getUniqueId());
        if (g == null || !g.name.equals(w.attacker)) {
            Text.actionBar(p, "&c공격측 길드원만 성벽을 공격할 수 있습니다.");
            return true;
        }
        if (ItemData.category(tool) != Category.TOOL) {
            Text.actionBar(p, "&c채집도구로만 성벽을 부술 수 있습니다.");
            return true;
        }
        long now = System.currentTimeMillis();
        if (plugin.getConfig().getBoolean("war.wall-game", true)) {   // v5.10.15 타이밍 미니게임으로만 성벽 피해
            wallGame(p, c, w, wall, block, tool, now);
            return true;
        }
        if (hitCooldown.getOrDefault(p.getUniqueId(), 0L) > now) return true;
        hitCooldown.put(p.getUniqueId(), now + plugin.getConfig().getLong("war.hit-interval-ms", 400));
        double dmg = ItemData.value(tool) * plugin.getConfig().getDouble("war.wall-damage-mult", 1.0);
        damageWall(p, c, w, wall, block, dmg, "");
        return true;
    }

    private void damageWall(Player p, Castle c, War w, Castle.Wall wall, Block block, double dmg, String extra) {
        wall.hp -= dmg;
        block.getWorld().spawnParticle(Particle.BLOCK_CRACK, block.getLocation().add(0.5, 0.5, 0.5), 12, 0.3, 0.3, 0.3, block.getBlockData());
        block.getWorld().playSound(block.getLocation(), Sound.BLOCK_STONE_HIT, 1f, 0.8f);
        Text.actionBar(p, extra + "&e성벽 " + wall.id + " &c❤ " + Text.num(Math.max(0, wall.hp)) + " / " + Text.num(wall.maxHp) + " &7(-" + Text.num(dmg) + ")");
        if (wall.hp <= 0) breakWall(c, w, wall);
    }

    // ------------------------------------------------------------------ v5.10.15 성벽 부수기 미니게임 (타이밍 게이지)
    private static final int BAR = 31;

    private static final class WallGame {
        String castle, wall;
        Location block;
        long start, lastClick, lockUntil, showUntil;
        int combo, zone, half, perfect;
        double speed;   // 칸 / 틱
        int tier;
    }

    private final Map<UUID, WallGame> games = new HashMap<>();
    private org.bukkit.scheduler.BukkitTask gameTask;

    /** 좌클릭 1번: 게이지 시작 / 게이지가 떠 있을 때 좌클릭: 표시가 초록 칸이면 명중 (금색 칸 = 치명타) */
    private void wallGame(Player p, Castle c, War w, Castle.Wall wall, Block block, ItemStack tool, long now) {
        WallGame g = games.get(p.getUniqueId());
        int tier = Math.max(1, ItemData.template(tool) == null ? 1 : ItemData.template(tool).tier);
        if (g == null || !g.castle.equals(c.id) || now - g.lastClick > 5000) {
            g = new WallGame();
            g.castle = c.id;
            g.tier = tier;
            g.start = now;
            games.put(p.getUniqueId(), g);
            g.wall = wall.id;
            g.block = block.getLocation();
            g.lastClick = now;
            newZone(g);
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1.2f);
            ensureGameTask();
            return;
        }
        g.wall = wall.id;
        g.block = block.getLocation();
        g.tier = tier;
        if (now < g.lockUntil || now - g.lastClick < 150) return;   // 빗나간 뒤 잠깐 · 너무 빠른 연타
        g.lastClick = now;
        int pos = marker(g, now);
        int d = Math.abs(pos - g.zone);
        double base = ItemData.value(tool) * plugin.getConfig().getDouble("war.wall-damage-mult", 1.0);
        if (d <= g.perfect) {
            g.combo++;
            double dmg = base * plugin.getConfig().getDouble("war.wall-game-perfect-mult", 2.5) * comboMult(g);
            p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.7f, 1.6f);
            block.getWorld().spawnParticle(Particle.CRIT, block.getLocation().add(0.5, 0.5, 0.5), 20, 0.4, 0.4, 0.4, 0.2);
            damageWall(p, c, w, wall, block, dmg, "&6&l완벽! &e콤보 " + g.combo + " &8| ");
            g.showUntil = now + 450;
        } else if (d <= g.half) {
            g.combo++;
            double dmg = base * plugin.getConfig().getDouble("war.wall-game-hit-mult", 1.5) * comboMult(g);
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.0f + Math.min(1f, g.combo * 0.08f));
            damageWall(p, c, w, wall, block, dmg, "&a명중 &e콤보 " + g.combo + " &8| ");
            g.showUntil = now + 450;
        } else {
            g.combo = 0;
            g.lockUntil = now + 900;
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.6f);
            Text.actionBar(p, "&c빗나감! &7(잠깐 쉬었다가 다시)");
        }
        newZone(g);
    }

    private double comboMult(WallGame g) {
        return Math.min(2.0, 1 + 0.1 * Math.max(0, g.combo - 1));
    }

    /** 성공 칸을 새 자리에 · 도구가 좋을수록 넓고, 콤보가 쌓일수록 표시가 빨라짐 */
    private void newZone(WallGame g) {
        java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
        g.half = g.tier >= 3 ? 3 : g.tier == 2 ? 2 : 1;
        g.perfect = 0;
        g.zone = r.nextInt(g.half + 2, BAR - g.half - 2);
        g.speed = Math.min(2.2, 0.9 + 0.06 * g.combo);
    }

    /** 표시 위치: 게이지를 왕복 (0 ~ BAR-1) */
    private static int marker(WallGame g, long now) {
        double t = (now - g.start) / 50.0 * g.speed;
        int period = (BAR - 1) * 2;
        int k = (int) (t % period);
        return k < BAR ? k : period - k;
    }

    private void ensureGameTask() {
        if (gameTask != null) return;
        gameTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            for (Iterator<Map.Entry<UUID, WallGame>> it = games.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<UUID, WallGame> en = it.next();
                Player p = Bukkit.getPlayer(en.getKey());
                WallGame g = en.getValue();
                War w = wars.get(g.castle);
                if (p == null || w == null || !w.started || now - g.lastClick > 5000
                        || !p.getWorld().equals(g.block.getWorld()) || p.getLocation().distanceSquared(g.block) > 64) { it.remove(); continue; }
                if (now < g.lockUntil) continue;
                if (now < g.showUntil) continue;   // 방금 맞힌 결과 글자를 잠깐 보여 줌
                int pos = marker(g, now);
                StringBuilder sb = new StringBuilder("&8[");
                for (int i = 0; i < BAR; i++) {
                    int d = Math.abs(i - g.zone);
                    if (i == pos) sb.append("&f&l|");
                    else if (d <= g.perfect) sb.append("&6|");
                    else if (d <= g.half) sb.append("&a|");
                    else sb.append("&7|");
                }
                sb.append("&8] &e콤보 ").append(g.combo).append(" &7좌클릭!");
                Text.actionBar(p, sb.toString());
            }
            if (games.isEmpty()) { gameTask.cancel(); gameTask = null; }
        }, 1L, 1L);
    }

    private void breakWall(Castle c, War w, Castle.Wall wall) {
        wall.broken = true;
        wall.hp = 0;
        World wd = wall.min.getWorld();
        for (int x = wall.min.getBlockX(); x <= wall.max.getBlockX(); x++)
            for (int y = wall.min.getBlockY(); y <= wall.max.getBlockY(); y++)
                for (int z = wall.min.getBlockZ(); z <= wall.max.getBlockZ(); z++) wd.getBlockAt(x, y, z).setType(Material.AIR, false);
        Location mid = wall.min.clone().add(wall.max.clone().subtract(wall.min).multiply(0.5));
        wd.spawnParticle(Particle.EXPLOSION_HUGE, mid, 4, 1, 1, 1);
        wd.playSound(mid, Sound.ENTITY_GENERIC_EXPLODE, 3f, 0.6f);
        kr.rpgcraft.util.Text.announce(Text.PREFIX + Text.c("&c" + c.name + "의 성벽 &e" + wall.id + "&c이(가) 무너졌습니다! &7(" + c.brokenWalls() + "/" + requiredWalls(c) + ")"));
        if (c.brokenWalls() >= requiredWalls(c))
            kr.rpgcraft.util.Text.announce(Text.PREFIX + Text.c("&6&l" + c.name + "의 신호기가 노출되었습니다! 신호기를 파괴하면 승리합니다!"));
    }

    public int requiredWalls(Castle c) {
        int req = plugin.getConfig().getInt("war.walls-required", 3);
        return Math.min(c.walls.size(), req <= 0 ? c.walls.size() : req);
    }

    /** 신호기 파괴 시도. @return 이벤트를 취소해야 하면 true */
    public boolean breakBeacon(Player p, Block block) {
        Castle c = null;
        for (Castle cc : castles.values()) if (cc.isBeacon(block.getLocation())) c = cc;
        if (c == null) return false;
        War w = wars.get(c.id);
        Guild g = plugin.guilds().of(p.getUniqueId());
        if (w == null || !w.started || g == null || !g.name.equals(w.attacker)) {
            Text.actionBar(p, "&c지금은 신호기를 부술 수 없습니다.");
            return true;
        }
        if (c.brokenWalls() < requiredWalls(c)) {
            Text.actionBar(p, "&c성벽을 먼저 " + requiredWalls(c) + "개 무너뜨려야 합니다. (" + c.brokenWalls() + ")");
            return true;
        }
        win(w, p);
        return true;
    }

    private void win(War w, Player breaker) {
        Castle c = w.castle;
        Guild att = plugin.guilds().get(w.attacker);
        Guild def = w.defender == null ? null : plugin.guilds().get(w.defender);
        long loot = 0;
        if (def != null && att != null) {
            loot = (long) (def.bank * plugin.getConfig().getDouble("war.loot-percent", 30) / 100.0);
            def.bank -= loot;
            att.bank += loot;
        }
        c.owner = w.attacker;
        kr.rpgcraft.util.Text.announce(Text.PREFIX + Text.c("&6&l[공성전 승리] &b" + w.attacker + "&f 길드가 &e" + c.name + "&f을(를) 점령했습니다! &7(신호기 파괴: "
                + Text.name(breaker) + ", 약탈 " + Text.money(loot) + ")"));
        for (Player o : Bukkit.getOnlinePlayers()) {
            o.sendTitle(Text.c("&6&l" + c.name + " 함락"), Text.c("&b" + w.attacker + " &f길드 승리"), 10, 60, 10);
            o.playSound(o.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
        long reward = plugin.getConfig().getLong("war.win-reward", 10_000_000);
        if (att != null) att.bank += reward;
        end(w);
        save();
        plugin.guilds().save();
    }

    public void end(War w) {
        w.bar.removeAll();
        wars.remove(w.castle.id);
        restore(w.castle);
    }

    public boolean stop(String castleId) {
        War w = wars.get(castleId);
        if (w == null) return false;
        kr.rpgcraft.util.Text.announce(Text.PREFIX + Text.c("&7" + w.castle.name + " 공성전이 관리자에 의해 중단되었습니다."));
        end(w);
        return true;
    }

    /** 성벽/신호기 복구 */
    public void restore(Castle c) {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (Castle.Wall wall : c.walls) {
            List<String> snap = y.getStringList(c.id + ".snapshot." + wall.id);
            if (!snap.isEmpty()) {
                World wd = wall.min.getWorld();
                for (String line : snap) {
                    try {
                        String[] parts = line.split("\\|", 2);
                        String[] xyz = parts[0].split(",");
                        wd.getBlockAt(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2]))
                                .setBlockData(Bukkit.createBlockData(parts[1]), false);
                    } catch (Exception ignored) {
                    }
                }
            }
            wall.broken = false;
            wall.hp = wall.maxHp;
        }
        y.set(c.id + ".snapshot", null);
        saveYaml(y);
        if (c.beacon != null && c.beacon.getBlock().getType() != Material.BEACON) c.beacon.getBlock().setType(Material.BEACON);
    }

    private void restoreLeftoverSnapshots() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (Castle c : castles.values()) if (y.isConfigurationSection(c.id + ".snapshot")) restore(c);
    }

    public void onGuildDisband(String guild) {
        for (War w : new ArrayList<>(wars.values())) if (w.attacker.equals(guild) || guild.equals(w.defender)) end(w);
        boolean demolish = plugin.getConfig().getBoolean("war.demolish-on-disband", true);
        for (Castle c : new ArrayList<>(castles.values())) {
            if (!guild.equals(c.owner)) continue;
            if (demolish) {   // v5.10.10 길드가 사라지면 그 길드의 성도 허물고 원래 땅으로
                Text.announce(Text.PREFIX + Text.c("&7길드 " + guild + "이(가) 사라져 &f" + c.name + "&7도 무너집니다..."));
                demolish(c, null);
            } else c.owner = null;
        }
        save();
    }

    // ------------------------------------------------------------------ v5.10.10 성 철거 (짓기 전 땅으로 되돌림)
    static File snapFile(RpgCraft plugin, String id) {
        return new File(new File(plugin.getDataFolder(), "castle-sites"), id + ".bin.gz");
    }

    /** 성을 목록에서 지우고 블록을 허묾. 지을 때 저장한 원래 지형이 있으면 그대로 되돌리고, 없으면 성 자리를 비우고 풀밭으로 */
    public void demolish(Castle c, org.bukkit.command.CommandSender who) {
        War w = wars.get(c.id);
        if (w != null) end(w);
        castles.remove(c.id);
        save();
        File f = snapFile(plugin, c.id);
        if (f.isFile()) {
            try {
                SiteSnapshot snap = SiteSnapshot.read(f);
                World wd = Bukkit.getWorld(snap.world);
                if (wd != null) {
                    snap.restore(plugin, wd, plugin.getConfig().getInt("war.castle-build-blocks-per-tick", 20000), () -> {
                        f.delete();
                        if (who != null) Text.msg(who, "&a" + c.name + " 철거 완료 &7(짓기 전 땅으로 되돌림)");
                        plugin.getLogger().info("공성 성 " + c.id + " 철거 완료");
                    });
                    if (who != null) Text.msg(who, "&e" + c.name + " 철거 중... &7(블록 " + String.format("%,d", snap.size()) + "개)");
                    return;
                }
            } catch (IOException ex) {
                plugin.getLogger().warning("성 부지 기록을 읽지 못했습니다 (" + c.id + "): " + ex.getMessage());
            }
        }
        clearArea(c, who);
    }

    /** 원래 지형 기록이 없는 성 (예전에 지은 성 · 손으로 만든 성): 성벽 · 신호기를 감싸는 범위를 비움 */
    private void clearArea(Castle c, org.bukkit.command.CommandSender who) {
        World wd;
        int x1, y1, z1, x2, y2, z2;
        if (c.center != null && c.r > 0 && c.center.getWorld() != null) {
            wd = c.center.getWorld();
            x1 = c.center.getBlockX() - c.r; x2 = c.center.getBlockX() + c.r;
            z1 = c.center.getBlockZ() - c.r; z2 = c.center.getBlockZ() + c.r;
            y1 = c.center.getBlockY(); y2 = c.center.getBlockY() + c.up;
        } else {
            List<Location> pts = new ArrayList<>();
            for (Castle.Wall wl : c.walls) { pts.add(wl.min); pts.add(wl.max); }
            if (c.beacon != null) pts.add(c.beacon);
            pts.removeIf(l -> l == null || l.getWorld() == null);
            if (pts.isEmpty()) { if (who != null) Text.msg(who, "&e" + c.name + " 목록에서 지웠습니다. &7(허물 블록 위치를 몰라 건물은 그대로)"); return; }
            wd = pts.get(0).getWorld();
            x1 = y1 = z1 = Integer.MAX_VALUE; x2 = y2 = z2 = Integer.MIN_VALUE;
            for (Location l : pts) {
                x1 = Math.min(x1, l.getBlockX()); y1 = Math.min(y1, l.getBlockY()); z1 = Math.min(z1, l.getBlockZ());
                x2 = Math.max(x2, l.getBlockX()); y2 = Math.max(y2, l.getBlockY()); z2 = Math.max(z2, l.getBlockZ());
            }
            x1 -= 3; z1 -= 3; x2 += 3; z2 += 3; y2 += 20;
        }
        int fx1 = x1, fy1 = y1, fz1 = z1, fx2 = x2, fy2 = y2, fz2 = z2;
        int perTick = Math.max(2000, plugin.getConfig().getInt("war.castle-build-blocks-per-tick", 20000));
        if (who != null) Text.msg(who, "&e" + c.name + " 철거 중... &7(짓기 전 땅 기록이 없어 성 자리를 비우고 풀밭으로)");
        new org.bukkit.scheduler.BukkitRunnable() {
            int x = fx1, z = fz1;

            @Override
            public void run() {
                int budget = perTick;
                while (budget > 0 && x <= fx2) {
                    for (int y = fy2; y >= fy1; y--) {
                        Block b = wd.getBlockAt(x, y, z);
                        if (!b.getType().isAir()) { b.setType(Material.AIR, false); budget--; }
                    }
                    Block floor = wd.getBlockAt(x, fy1 - 1, z);
                    if (!floor.getType().isAir()) floor.setType(Material.GRASS_BLOCK, false);
                    budget--;
                    if (++z > fz2) { z = fz1; x++; }
                }
                if (x <= fx2) return;
                cancel();
                if (who != null) Text.msg(who, "&a" + c.name + " 철거 완료");
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    public void shutdown() {
        for (War w : new ArrayList<>(wars.values())) end(w);
        save();
    }

    // ------------------------------------------------------------------ 성벽 수리 망치
    private void repairTick() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (!p.isSneaking() || ItemData.category(hand) != Category.HAMMER) {
                repairStart.remove(p.getUniqueId());
                continue;
            }
            Guild g = plugin.guilds().of(p.getUniqueId());
            Castle.Wall target = null;
            Castle owner = null;
            for (Castle c : castles.values()) {
                if (g == null || !g.name.equals(c.owner)) continue;
                for (Castle.Wall w : c.walls) {
                    if (!w.broken && w.hp < w.maxHp && w.distance(p.getLocation()) <= 6) {
                        target = w;
                        owner = c;
                    }
                }
            }
            if (target == null) {
                repairStart.remove(p.getUniqueId());
                Text.actionBar(p, "&7수리할 수 있는 우리 길드 성벽이 근처에 없습니다.");
                continue;
            }
            long start = repairStart.computeIfAbsent(p.getUniqueId(), k -> now);
            long need = plugin.getConfig().getLong("war.repair-seconds", 10) * 1000;
            double prog = (now - start) / (double) need;
            Text.actionBar(p, "&e성벽 수리 중 " + Text.bar(prog, 20, "&a", "&7") + " &f" + (int) Math.min(100, prog * 100) + "%");
            p.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, p.getLocation().add(0, 1, 0), 3, 0.4, 0.4, 0.4);
            if (prog >= 1) {
                double v = ItemData.value(hand);
                target.hp = Math.min(target.maxHp, target.hp + v);
                hand.setAmount(hand.getAmount() - 1);
                repairStart.remove(p.getUniqueId());
                p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1.2f);
                Text.msg(p, "&a" + owner.name + " 성벽 " + target.id + " 수리 +" + Text.num(v) + " &7(" + Text.num(target.hp) + "/" + Text.num(target.maxHp) + ")");
            }
        }
    }

    // ------------------------------------------------------------------ 관리자 설정
    public Location[] selection(Player p) {
        return selections.computeIfAbsent(p.getUniqueId(), k -> new Location[2]);
    }

    public Castle create(String id, String name) {
        Castle c = new Castle(id, name);
        castles.put(id, c);
        save();
        return c;
    }

    public void delete(String id) {
        War w = wars.get(id);
        if (w != null) end(w);
        castles.remove(id);
        save();
    }

    public void addWall(Castle c, String wallId, Location a, Location b, double hp) {
        Castle.Wall w = new Castle.Wall();
        w.id = wallId;
        w.min = new Location(a.getWorld(), Math.min(a.getBlockX(), b.getBlockX()), Math.min(a.getBlockY(), b.getBlockY()), Math.min(a.getBlockZ(), b.getBlockZ()));
        w.max = new Location(a.getWorld(), Math.max(a.getBlockX(), b.getBlockX()), Math.max(a.getBlockY(), b.getBlockY()), Math.max(a.getBlockZ(), b.getBlockZ()));
        w.maxHp = hp;
        w.hp = hp;
        c.walls.removeIf(x -> x.id.equals(wallId));
        c.walls.add(w);
        save();
    }

    private void load() {
        castles.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String id : y.getKeys(false)) {
            ConfigurationSection s = y.getConfigurationSection(id);
            if (s == null) continue;
            Castle c = new Castle(id, s.getString("name", id));
            c.owner = s.getString("owner");
            c.beacon = Locs.parse(s.getString("beacon"));
            c.attackerSpawn = Locs.parse(s.getString("attacker-spawn"));
            c.defenderSpawn = Locs.parse(s.getString("defender-spawn"));
            c.center = Locs.parse(s.getString("area.center"));
            c.r = s.getInt("area.r");
            c.down = s.getInt("area.down");
            c.up = s.getInt("area.up");
            ConfigurationSection ws = s.getConfigurationSection("walls");
            if (ws != null) {
                for (String wid : ws.getKeys(false)) {
                    Castle.Wall w = new Castle.Wall();
                    w.id = wid;
                    w.min = Locs.parse(ws.getString(wid + ".min"));
                    w.max = Locs.parse(ws.getString(wid + ".max"));
                    w.maxHp = ws.getDouble(wid + ".hp", 10000);
                    w.hp = w.maxHp;
                    if (w.min != null && w.max != null) c.walls.add(w);
                }
            }
            castles.put(id, c);
        }
    }

    public void save() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String k : y.getKeys(false)) if (!castles.containsKey(k)) y.set(k, null);
        for (Castle c : castles.values()) {
            y.set(c.id + ".name", c.name);
            y.set(c.id + ".owner", c.owner);
            y.set(c.id + ".beacon", c.beacon == null ? null : Locs.block(c.beacon));
            y.set(c.id + ".attacker-spawn", c.attackerSpawn == null ? null : Locs.full(c.attackerSpawn));
            y.set(c.id + ".defender-spawn", c.defenderSpawn == null ? null : Locs.full(c.defenderSpawn));
            y.set(c.id + ".area", null);
            if (c.center != null && c.r > 0) {
                y.set(c.id + ".area.center", Locs.block(c.center));
                y.set(c.id + ".area.r", c.r);
                y.set(c.id + ".area.down", c.down);
                y.set(c.id + ".area.up", c.up);
            }
            y.set(c.id + ".walls", null);
            for (Castle.Wall w : c.walls) {
                y.set(c.id + ".walls." + w.id + ".min", Locs.block(w.min));
                y.set(c.id + ".walls." + w.id + ".max", Locs.block(w.max));
                y.set(c.id + ".walls." + w.id + ".hp", w.maxHp);
            }
        }
        saveYaml(y);
    }

    private void saveYaml(YamlConfiguration y) {
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("castles.yml 저장 실패: " + e.getMessage());
        }
    }
}
