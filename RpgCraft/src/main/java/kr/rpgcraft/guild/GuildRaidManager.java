package kr.rpgcraft.guild;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.boss.BossDefinition;
import kr.rpgcraft.mob.MobManager;
import kr.rpgcraft.util.Text;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 길드 토벌전 (v5.3.2)
 * 길드장이 /길드 토벌전 <1~5> 로 길드 금고에서 참가비를 내고 시작 → 길드장 근처에 그 단계의 토벌 보스 등장.
 * 그 길드원만 때릴 수 있고, 제한 시간(15분) 안에 잡으면 길드 금고 · 참가자에게 보상, 클리어 기록(최단 시간) 저장.
 * 실패하면 보스는 사라지고 참가비는 돌아오지 않는다. 길드마다 재도전 대기 시간이 있다.
 */
public class GuildRaidManager implements Listener {
    /** 단계: 목표 보스 레벨 · 체력 배율 · 참가비 · 길드 금고 보상 · 참가자 1인 보상 · 참가자 아이템 · 필요 길드 레벨 */
    private record Stage(int level, double hpMult, long cost, long bankReward, long memberReward, String item, int itemAmount, int guildLevel) {}

    private static final Stage[] STAGES = {
            new Stage(60, 3.0, 1_000_000, 1_500_000, 200_000, "crystal_mid", 5, 1),
            new Stage(100, 4.0, 3_000_000, 4_500_000, 500_000, "crystal_high", 2, 2),
            new Stage(150, 5.0, 8_000_000, 12_000_000, 1_200_000, "crystal_high", 5, 3),
            new Stage(220, 6.5, 20_000_000, 30_000_000, 3_000_000, "crystal_top", 2, 4),
            new Stage(300, 8.0, 50_000_000, 75_000_000, 7_000_000, "crystal_top", 5, 5)};

    private static class Raid {
        String guild;
        int stage;
        UUID boss;
        long started, ends;
        BossBar timer;
        Chunk held;
        final Set<UUID> parts = new LinkedHashSet<>();   // 피해를 준 길드원 (매초 기록)
    }

    private final RpgCraft plugin;
    private final NamespacedKey KEY;
    private final File file;
    private final YamlConfiguration data;
    private final Map<String, Raid> raids = new HashMap<>();   // 길드 이름 → 진행 중인 토벌

    public GuildRaidManager(RpgCraft plugin) {
        this.plugin = plugin;
        this.KEY = new NamespacedKey(plugin, "guild_raid");
        this.file = new File(plugin.getDataFolder(), "guild_raids.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private void save() {
        try { data.save(file); } catch (IOException ignored) { }
    }

    private long cooldownMs() {
        return (long) (plugin.getConfig().getDouble("guild-raid.cooldown-hours", 12) * 3_600_000L);
    }

    private long timeLimitMs() {
        return (long) (plugin.getConfig().getDouble("guild-raid.time-limit-minutes", 15) * 60_000L);
    }

    private static String mmss(long ms) {
        long s = Math.max(0, ms / 1000);
        return String.format("%d:%02d", s / 60, s % 60);
    }

    // ------------------------------------------------------------------ 판정: 그 길드원만 때릴 수 있음
    /** 이 토벌 보스를 p 가 때릴 수 있는가 (토벌 보스가 아니면 항상 true) */
    public boolean canHit(Player p, Entity e) {
        String g = e.getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
        if (g == null) return true;
        Guild pg = plugin.guilds().of(p.getUniqueId());
        return pg != null && pg.name.equalsIgnoreCase(g);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        String g = e.getEntity().getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
        if (g == null) return;
        Entity src = e.getDamager();
        if (src instanceof Projectile pr && pr.getShooter() instanceof Entity sh) src = sh;
        if (!(src instanceof Player p)) return;   // 토벌 보스끼리 · 환경 피해는 그대로
        if (!canHit(p, e.getEntity())) {
            e.setCancelled(true);
            Text.actionBar(p, "&c[" + g + "] 길드의 토벌 대상입니다.");
        }
    }

    // ------------------------------------------------------------------ 시작
    public void start(Player p, int stageNo) {
        Guild g = plugin.guilds().of(p.getUniqueId());
        if (g == null) { Text.msg(p, "&c길드가 없습니다."); return; }
        if (!g.isLeader(p.getUniqueId())) { Text.msg(p, "&c길드장만 토벌전을 시작할 수 있습니다."); return; }
        if (!plugin.getConfig().getBoolean("guild-raid.enabled", true)) { Text.msg(p, "&c지금은 길드 토벌전을 할 수 없습니다."); return; }
        if (stageNo < 1 || stageNo > STAGES.length) { Text.msg(p, "&c단계는 1~" + STAGES.length + " 입니다."); return; }
        Stage st = STAGES[stageNo - 1];
        if (raids.containsKey(g.name)) { Text.msg(p, "&c이미 토벌전이 진행 중입니다."); return; }
        if (g.level < st.guildLevel()) { Text.msg(p, "&c" + stageNo + "단계는 길드 레벨 " + st.guildLevel() + " 이상이어야 합니다."); return; }
        long last = data.getLong(g.name + ".last", 0), left = last + cooldownMs() - System.currentTimeMillis();
        if (left > 0) { Text.msg(p, "&c다음 토벌전까지 " + Text.time(left / 1000) + " 남았습니다."); return; }
        int min = plugin.getConfig().getInt("guild-raid.min-members", 2);
        List<Player> near = new ArrayList<>();
        for (Player m : g.online()) if (m.getWorld() == p.getWorld() && m.getLocation().distanceSquared(p.getLocation()) < 40 * 40 && !m.isDead()) near.add(m);
        if (near.size() < min) { Text.msg(p, "&c근처(40칸)에 길드원이 " + min + "명 이상 있어야 합니다. &7(지금 " + near.size() + "명)"); return; }
        if (plugin.dungeons() != null && plugin.dungeons().runOf(p) != null) { Text.msg(p, "&c던전 안에서는 시작할 수 없습니다."); return; }
        if (g.bank < st.cost()) { Text.msg(p, "&c길드 금고에 참가비 " + Text.money(st.cost()) + "이 필요합니다. &7(금고 " + Text.money(g.bank) + ")"); return; }
        BossDefinition def = pick(st.level());
        if (def == null) { Text.msg(p, "&c토벌할 보스를 찾지 못했습니다."); return; }
        Location at = spot(p);
        if (at == null) { Text.msg(p, "&c보스가 나올 자리가 없습니다. 탁 트인 곳에서 시도하세요."); return; }

        g.bank -= st.cost();
        plugin.guilds().save();
        LivingEntity boss = plugin.bosses().spawn(def.id, at);
        if (boss == null) { g.bank += st.cost(); plugin.guilds().save(); Text.msg(p, "&c보스 소환에 실패했습니다. (참가비 반환)"); return; }
        boss.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, g.name);
        boss.setPersistent(false);
        MobManager.MobState s = plugin.mobs().state(boss);
        s.maxHp *= st.hpMult();
        s.hp = s.maxHp;
        s.baseName = Text.c("&b[토벌 " + stageNo + "단계] &f") + s.baseName;

        Raid r = new Raid();
        r.guild = g.name;
        r.stage = stageNo;
        r.boss = boss.getUniqueId();
        r.started = System.currentTimeMillis();
        r.ends = r.started + timeLimitMs();
        r.timer = Bukkit.createBossBar(Text.c("&b⚔ 길드 토벌전"), BarColor.BLUE, BarStyle.SEGMENTED_20);
        r.held = boss.getLocation().getChunk();
        r.held.addPluginChunkTicket(plugin);
        raids.put(g.name, r);
        data.set(g.name + ".last", r.started);
        save();
        g.broadcast(Text.PREFIX + Text.c("&b&l⚔ 길드 토벌전 " + stageNo + "단계 시작! &f" + def.name + " &7(제한 " + mmss(timeLimitMs()) + ", 길드원만 공격 가능)"));
        for (Player m : g.online()) {
            m.sendTitle(Text.c("&b&l길드 토벌전"), Text.c("&f" + stageNo + "단계 · " + def.name), 10, 60, 20);
            m.playSound(m.getLocation(), Sound.EVENT_RAID_HORN, 1f, 1f);
        }
    }

    private BossDefinition pick(int level) {
        List<BossDefinition> near = new ArrayList<>();
        BossDefinition closest = null;
        for (String id : plugin.bosses().ids()) {
            if (id.equals("megalodon") || id.equals("kraken")) continue;   // 바다 전용
            BossDefinition d = plugin.bosses().def(id);
            if (d == null) continue;
            if (Math.abs(d.level - level) <= 30) near.add(d);
            if (closest == null || Math.abs(d.level - level) < Math.abs(closest.level - level)) closest = d;
        }
        return near.isEmpty() ? closest : near.get(ThreadLocalRandom.current().nextInt(near.size()));
    }

    private Location spot(Player p) {
        for (int tries = 0; tries < 10; tries++) {
            double a = Math.toRadians(p.getLocation().getYaw() + 90) + (ThreadLocalRandom.current().nextDouble() - 0.5) * (tries < 3 ? 0.6 : 3);
            Location l = p.getLocation().add(Math.cos(a) * 10, 0, Math.sin(a) * 10);
            Block top = kr.rpgcraft.util.Locs.surface(p.getWorld(), l);
            if (top.isLiquid() || Math.abs(top.getY() - p.getLocation().getY()) > 8) continue;
            return top.getLocation().add(0.5, 1, 0.5);
        }
        return null;
    }

    // ------------------------------------------------------------------ 진행 · 종료
    private void tick() {
        long now = System.currentTimeMillis();
        for (Iterator<Raid> it = raids.values().iterator(); it.hasNext(); ) {
            Raid r = it.next();
            Guild g = plugin.guilds().get(r.guild);
            Entity e = Bukkit.getEntity(r.boss);
            if (g == null || e == null || !e.isValid()) {   // 죽었으면 onDeath 에서 처리됨. 그 밖엔 실패
                if (e == null || !e.isValid()) {
                    end(r, false, g);
                    it.remove();
                }
                continue;
            }
            if (now >= r.ends) {
                e.getWorld().spawnParticle(Particle.SMOKE_LARGE, e.getLocation().add(0, 1, 0), 40, 1, 1, 1, 0.05);
                e.remove();
                end(r, false, g);
                it.remove();
                continue;
            }
            MobManager.MobState ms = e instanceof LivingEntity le ? plugin.mobs().peek(le) : null;
            if (ms != null) for (UUID u : ms.contrib.keySet()) if (g.members.contains(u)) r.parts.add(u);
            long left = r.ends - now;
            r.timer.setTitle(Text.c("&b⚔ 길드 토벌전 " + r.stage + "단계 &f· 남은 시간 &e" + mmss(left)));
            r.timer.setProgress(Math.max(0, Math.min(1, left / (double) timeLimitMs())));
            r.timer.setColor(left < 60_000 ? BarColor.RED : BarColor.BLUE);
            Set<Player> show = new HashSet<>(g.online());
            for (Player pl : new ArrayList<>(r.timer.getPlayers())) if (!show.contains(pl)) r.timer.removePlayer(pl);
            for (Player pl : show) r.timer.addPlayer(pl);
            Chunk c = e.getLocation().getChunk();
            if (!c.equals(r.held)) { r.held.removePluginChunkTicket(plugin); c.addPluginChunkTicket(plugin); r.held = c; }
        }
    }

    private void end(Raid r, boolean win, Guild g) {
        r.timer.removeAll();
        if (r.held != null) r.held.removePluginChunkTicket(plugin);
        if (!win && g != null) {
            g.broadcast(Text.PREFIX + Text.c("&c길드 토벌전 " + r.stage + "단계 실패... &7(다음 도전까지 " + Text.time(cooldownMs() / 1000) + ")"));
            for (Player m : g.online()) m.playSound(m.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.5f, 1.4f);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent e) {
        String gname = e.getEntity().getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
        if (gname == null) return;
        Raid r = raids.remove(gname);
        if (r == null || !r.boss.equals(e.getEntity().getUniqueId())) return;
        Guild g = plugin.guilds().get(gname);
        end(r, true, g);
        if (g == null) return;
        Stage st = STAGES[r.stage - 1];
        long took = System.currentTimeMillis() - r.started;
        g.bank += st.bankReward();
        plugin.guilds().save();
        // 참가자: 이 보스에 피해를 준 길드원
        MobManager.MobState s = plugin.mobs().peek(e.getEntity());
        Set<UUID> parts = new LinkedHashSet<>(r.parts);
        if (s != null) for (UUID u : s.contrib.keySet()) if (g.members.contains(u)) parts.add(u);
        for (UUID u : parts) {
            Player m = Bukkit.getPlayer(u);
            if (m == null) continue;
            plugin.economy().give(m, st.memberReward());
            ItemStack it = plugin.items().create(st.item(), st.itemAmount());
            if (it != null) for (ItemStack left : m.getInventory().addItem(it).values()) m.getWorld().dropItemNaturally(m.getLocation(), left);
            Text.msg(m, "&b토벌 보상: &f" + Text.money(st.memberReward()) + (it != null ? " &7+ &f" + plugin.items().get(st.item()).name + " x" + st.itemAmount() : ""));
            plugin.data().get(m).counters.merge("guild_raid_clears", 1.0, Double::sum);
        }
        // 기록
        String k = gname + ".stage" + r.stage;
        data.set(k + ".clears", data.getInt(k + ".clears") + 1);
        long best = data.getLong(k + ".best", Long.MAX_VALUE);
        boolean record = took < best;
        if (record) data.set(k + ".best", took);
        save();
        for (Player m : g.online()) {
            m.sendTitle(Text.c("&b&l토벌 성공!"), Text.c("&f" + r.stage + "단계 · " + mmss(took) + (record ? " &e(최고 기록!)" : "")), 10, 70, 20);
            m.playSound(m.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
        g.broadcast(Text.PREFIX + Text.c("&b길드 금고 +" + Text.money(st.bankReward()) + " &7(참가 " + parts.size() + "명)"));
        Text.announce(Text.PREFIX + Text.c("&b&l[" + gname + "] &f길드가 &b토벌전 " + r.stage + "단계&f를 &e" + mmss(took) + "&f 만에 클리어했습니다!" + (record ? " &6(길드 최고 기록)" : "")));
    }

    // ------------------------------------------------------------------ 정보 · 기록
    public void info(Player p) {
        Guild g = plugin.guilds().of(p.getUniqueId());
        Text.msg(p, "&b&l⚔ 길드 토벌전 &7— 길드장이 &e/길드 토벌전 <단계> &7로 시작 (근처 길드원 " + plugin.getConfig().getInt("guild-raid.min-members", 2) + "명 이상)");
        for (int i = 0; i < STAGES.length; i++) {
            Stage st = STAGES[i];
            String rec = "";
            if (g != null) {
                long best = data.getLong(g.name + ".stage" + (i + 1) + ".best", 0);
                int clears = data.getInt(g.name + ".stage" + (i + 1) + ".clears");
                if (clears > 0) rec = " &a(클리어 " + clears + "회, 최고 " + mmss(best) + ")";
            }
            p.sendMessage(Text.c(" &b" + (i + 1) + "단계 &7보스 Lv." + st.level() + " · 길드 Lv." + st.guildLevel() + " · 참가비 &f" + Text.money(st.cost())
                    + " &7→ 금고 &e+" + Text.money(st.bankReward()) + " &7· 1인 &e" + Text.money(st.memberReward()) + rec));
        }
        Text.msg(p, "&7제한 시간 " + mmss(timeLimitMs()) + ", 재도전 대기 " + Text.time(cooldownMs() / 1000) + ", 실패하면 참가비는 돌아오지 않습니다.");
        if (g != null) {
            long left = data.getLong(g.name + ".last", 0) + cooldownMs() - System.currentTimeMillis();
            Text.msg(p, left > 0 ? "&7우리 길드 다음 토벌전까지 &f" + Text.time(left / 1000) : "&a지금 토벌전을 시작할 수 있습니다.");
        }
    }

    /** 단계별 최단 기록 순위 */
    public void ranking(Player p, int stageNo) {
        if (stageNo < 1 || stageNo > STAGES.length) stageNo = 1;
        List<Map.Entry<String, Long>> list = new ArrayList<>();
        for (String gname : data.getKeys(false)) {
            long best = data.getLong(gname + ".stage" + stageNo + ".best", 0);
            if (best > 0 && plugin.guilds().get(gname) != null) list.add(Map.entry(gname, best));
        }
        list.sort(Map.Entry.comparingByValue());
        Text.msg(p, "&b&l토벌전 " + stageNo + "단계 최단 기록");
        if (list.isEmpty()) p.sendMessage(Text.c(" &7아직 기록이 없습니다."));
        for (int i = 0; i < Math.min(10, list.size()); i++)
            p.sendMessage(Text.c(" &e" + (i + 1) + ". &b" + list.get(i).getKey() + " &f" + mmss(list.get(i).getValue())));
    }

    public void shutdown() {
        for (Raid r : raids.values()) {
            Entity e = Bukkit.getEntity(r.boss);
            if (e != null) e.remove();
            r.timer.removeAll();
            if (r.held != null) r.held.removePluginChunkTicket(plugin);
            Guild g = plugin.guilds().get(r.guild);
            if (g != null) {   // 서버 종료로 끝난 토벌은 참가비 반환 · 대기 시간 초기화
                g.bank += STAGES[r.stage - 1].cost();
                data.set(r.guild + ".last", 0);
            }
        }
        if (!raids.isEmpty()) { plugin.guilds().save(); save(); }
        raids.clear();
    }
}
