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
        if (hitCooldown.getOrDefault(p.getUniqueId(), 0L) > now) return true;
        hitCooldown.put(p.getUniqueId(), now + plugin.getConfig().getLong("war.hit-interval-ms", 400));
        double dmg = ItemData.value(tool) * plugin.getConfig().getDouble("war.wall-damage-mult", 1.0);
        wall.hp -= dmg;
        block.getWorld().spawnParticle(Particle.BLOCK_CRACK, block.getLocation().add(0.5, 0.5, 0.5), 12, 0.3, 0.3, 0.3, block.getBlockData());
        block.getWorld().playSound(block.getLocation(), Sound.BLOCK_STONE_HIT, 1f, 0.8f);
        Text.actionBar(p, "&e성벽 " + wall.id + " &c❤ " + Text.num(Math.max(0, wall.hp)) + " / " + Text.num(wall.maxHp) + " &7(-" + Text.num(dmg) + ")");
        if (wall.hp <= 0) breakWall(c, w, wall);
        return true;
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
                + breaker.getName() + ", 약탈 " + Text.money(loot) + ")"));
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
        for (Castle c : castles.values()) if (guild.equals(c.owner)) c.owner = null;
        for (War w : new ArrayList<>(wars.values())) if (w.attacker.equals(guild) || guild.equals(w.defender)) end(w);
        save();
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
