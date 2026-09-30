package kr.rpgcraft.boss;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.util.Locs;
import kr.rpgcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 필드 보스 (v5.1.9): bosses.yml 에서 field: true 인 보스를 일정 시간마다 접속자 근처 야외에 등장시킨다.
 * 지역 레벨 ±level-range 안, 바이옴이 맞는 보스만 고른다. 오래 아무도 없으면 조용히 사라진다.
 */
public class FieldBossManager implements org.bukkit.event.Listener {
    private final RpgCraft plugin;
    private final Map<UUID, Long> alive = new HashMap<>();   // 보스 → 마지막으로 근처에 사람이 있던 시각
    /** 보스가 있는 청크를 붙잡아 둠: 불러낸 플레이어가 멀어져도 다른 사람이 찾아올 때까지 사라지지 않게 */
    private final Map<UUID, org.bukkit.Chunk> held = new HashMap<>();
    private long nextAt;

    public FieldBossManager(RpgCraft plugin) {
        this.plugin = plugin;
        nextAt = System.currentTimeMillis() + intervalMs();
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 200L, 200L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::keepAlive, 40L, 40L);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private static boolean isBoss(org.bukkit.entity.Entity e) {
        return e.getPersistentDataContainer().has(kr.rpgcraft.Keys.BOSS, org.bukkit.persistence.PersistentDataType.STRING);
    }

    // ------------------------------------------------------------------ 바닐라 소멸 막기 (모든 보스)
    /** 호글린 → 조글린(오버월드 15초), 좀비 → 드라운드 등 변신하면 보스가 사라지므로 막음 */
    @org.bukkit.event.EventHandler(ignoreCancelled = true)
    public void onTransform(org.bukkit.event.entity.EntityTransformEvent e) {
        if (isBoss(e.getEntity())) e.setCancelled(true);
    }

    /** 햇빛에 타지 않음 (서리 리치 등 언데드 보스) */
    @org.bukkit.event.EventHandler(ignoreCancelled = true)
    public void onCombust(org.bukkit.event.entity.EntityCombustEvent e) {
        if (e.getClass() == org.bukkit.event.entity.EntityCombustEvent.class && isBoss(e.getEntity())) e.setCancelled(true);
    }

    /** 2초마다: 워든은 가까운 플레이어에게 계속 화가 나 있게(땅속으로 숨지 않도록), 청크 붙잡기 갱신 */
    private void keepAlive() {
        for (Iterator<Map.Entry<UUID, org.bukkit.Chunk>> it = held.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, org.bukkit.Chunk> en = it.next();
            Entity e = Bukkit.getEntity(en.getKey());
            if (!(e instanceof LivingEntity le) || !le.isValid() || le.isDead()) { en.getValue().removePluginChunkTicket(plugin); it.remove(); continue; }
            org.bukkit.Chunk now = le.getLocation().getChunk();
            if (!now.equals(en.getValue())) { en.getValue().removePluginChunkTicket(plugin); now.addPluginChunkTicket(plugin); en.setValue(now); }
        }
        for (World w : Bukkit.getWorlds())
            for (org.bukkit.entity.Warden wd : w.getEntitiesByClass(org.bukkit.entity.Warden.class)) {
                if (!isBoss(wd)) continue;
                Player near = null;
                double best = 48 * 48;
                for (Player p : w.getPlayers()) {
                    if (p.getGameMode() == GameMode.SPECTATOR || p.getGameMode() == GameMode.CREATIVE) continue;
                    double d = p.getLocation().distanceSquared(wd.getLocation());
                    if (d < best) { best = d; near = p; }
                }
                if (near != null) wd.setAnger(near, 150);
            }
    }

    private void hold(LivingEntity e) {
        org.bukkit.Chunk c = e.getLocation().getChunk();
        c.addPluginChunkTicket(plugin);
        held.put(e.getUniqueId(), c);
    }

    private void release(UUID id) {
        org.bukkit.Chunk c = held.remove(id);
        if (c != null) c.removePluginChunkTicket(plugin);
    }

    private FileConfiguration cfg() { return plugin.getConfig(); }

    private long intervalMs() {
        return (long) (Math.max(1, cfg().getDouble("field-bosses.interval-minutes", 25)) * 60_000);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        long idle = (long) (cfg().getDouble("field-bosses.despawn-minutes", 10) * 60_000);
        for (Iterator<Map.Entry<UUID, Long>> it = alive.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Long> en = it.next();
            Entity e = Bukkit.getEntity(en.getKey());
            if (!(e instanceof LivingEntity le) || !le.isValid() || le.isDead()) { release(en.getKey()); it.remove(); continue; }
            boolean near = false;
            for (Player p : le.getWorld().getPlayers())
                if (p.getLocation().distanceSquared(le.getLocation()) < 80 * 80) { near = true; break; }
            if (near) en.setValue(now);
            else if (now - en.getValue() > idle) { release(en.getKey()); le.remove(); it.remove(); }
        }
        if (!cfg().getBoolean("field-bosses.enabled", true) || now < nextAt) return;
        nextAt = now + intervalMs();
        if (alive.size() < cfg().getInt("field-bosses.max-alive", 2)) spawnRandom(null);
    }

    public int aliveCount() {
        alive.keySet().removeIf(id -> { Entity e = Bukkit.getEntity(id); return e == null || !e.isValid(); });
        return alive.size();
    }

    private boolean allowedWorld(World w) {
        List<String> ws = cfg().getStringList("field-bosses.worlds");
        if (!ws.isEmpty()) return ws.contains(w.getName());
        return w.getEnvironment() != World.Environment.THE_END && Bukkit.getWorlds().indexOf(w) <= 1;
    }

    private boolean eligible(Player p) {
        if (p.isDead() || p.getGameMode() == GameMode.SPECTATOR || !allowedWorld(p.getWorld())) return false;
        return plugin.dungeons() == null || plugin.dungeons().runOf(p) == null;
    }

    /** @param near null 이면 무작위 접속자 근처 */
    public String spawnRandom(Player near) {
        List<Player> cands = new ArrayList<>();
        if (near != null) cands.add(near);
        else for (Player p : Bukkit.getOnlinePlayers()) if (eligible(p)) cands.add(p);
        if (near == null) cands.removeIf(p -> plugin.bosses().nearSpawn(p.getLocation()));
        if (near == null && plugin.wars() != null) cands.removeIf(p -> plugin.wars().castleArea(p.getLocation(), 40) != null);   // v5.10.13 성 안 · 바로 옆에 있는 사람 옆에는 필드 보스가 나오지 않음   // 스폰 근처에 있는 사람 옆에는 나오지 않음
        if (cands.isEmpty()) return null;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Player p = cands.get(r.nextInt(cands.size()));
        double min = cfg().getDouble("field-bosses.min-distance", 30), max = Math.max(min + 1, cfg().getDouble("field-bosses.max-distance", 50));
        for (int tries = 0; tries < 12; tries++) {
            double a = r.nextDouble() * Math.PI * 2, d = min + r.nextDouble() * (max - min);
            Location at = p.getLocation().add(Math.cos(a) * d, 0, Math.sin(a) * d);
            Block top = Locs.surface(p.getWorld(), at);
            if (top.isLiquid() || Math.abs(top.getY() - p.getLocation().getY()) > 20) continue;
            Location loc = top.getLocation().add(0.5, 1, 0.5);
            if (plugin.bosses().nearSpawn(loc)) continue;   // 스폰 300칸 안에는 등장 금지 (v5.4.27)
            BossDefinition d0 = pick(loc);
            if (d0 == null) return null;
            LivingEntity e = plugin.bosses().spawn(d0.id, loc);
            if (e == null) return null;
            e.setPersistent(false);   // 청크가 내려가면 함께 사라짐 (쌓이지 않도록)
            alive.put(e.getUniqueId(), System.currentTimeMillis());
            hold(e);
            String title = Text.c("&6&l⚔ 필드 보스 출현");
            String sub = Text.c("&f" + d0.name + " &7Lv." + d0.level);
            for (Player o : loc.getWorld().getPlayers())
                if (o.getLocation().distanceSquared(loc) < 120 * 120) {
                    o.sendTitle(title, sub, 10, 60, 20);
                    o.playSound(o.getLocation(), Sound.EVENT_RAID_HORN, 1f, 0.8f);
                }
            return d0.id;
        }
        return null;
    }

    /** 지역 레벨과 바이옴에 맞는 필드 보스 하나 (없으면 레벨이 가장 가까운 것) */
    private BossDefinition pick(Location loc) {
        int lv = plugin.mobs().computeLevel(loc), range = cfg().getInt("field-bosses.level-range", 25);
        String biome = loc.getBlock().getBiome().name();
        List<BossDefinition> ok = new ArrayList<>();
        BossDefinition closest = null;
        for (String id : plugin.bosses().ids()) {
            BossDefinition d = plugin.bosses().def(id);
            if (d == null || !d.field) continue;
            boolean bio = d.biomes.isEmpty();
            for (String b : d.biomes) if (biome.contains(b)) { bio = true; break; }
            if (!bio) continue;
            if (Math.abs(d.level - lv) <= range) ok.add(d);
            if (closest == null || Math.abs(d.level - lv) < Math.abs(closest.level - lv)) closest = d;
        }
        if (!ok.isEmpty()) return ok.get(ThreadLocalRandom.current().nextInt(ok.size()));
        return closest;
    }

    public void shutdown() {
        for (UUID id : alive.keySet()) { Entity e = Bukkit.getEntity(id); if (e != null) e.remove(); }
        alive.clear();
        for (UUID id : new ArrayList<>(held.keySet())) release(id);
    }
}
