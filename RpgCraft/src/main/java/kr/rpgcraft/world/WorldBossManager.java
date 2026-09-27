package kr.rpgcraft.world;

import kr.rpgcraft.Keys;
import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.util.Text;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * 월드보스 출현: 일정 시간마다 맵 어딘가에 월드보스 전용 거대 전장(보스마다 다른 건축물)이 솟아오르고, 그 안에 보스가 등장한다.
 *  사막의 악몽 → 사암 원형 신전 · 시포니아 → 이끼 수정 성역 · 카인 → 흑요석 요새
 * 보스가 쓰러지면 3분 뒤, 시간이 다 되면 보스와 함께 전장이 사라지고 원래 지형으로 돌아간다.
 */
public class WorldBossManager implements Listener {
    private final RpgCraft plugin;
    private final Random rnd = new Random();
    private static class Event {
        UUID boss;
        String id, arena;
        Location at;
        long until;
    }

    private final List<Event> events = new java.util.ArrayList<>();

    public WorldBossManager(RpgCraft plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        long every = plugin.getConfig().getLong("world-boss.interval-minutes", 0) * 60 * 20;
        if (every > 0)   // 0 = 정기 출현 없음 (운영자 소환만)
            Bukkit.getScheduler().runTaskTimer(plugin, () -> { if (!Bukkit.getOnlinePlayers().isEmpty()) start(null, null); }, every, every);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 200L, 200L);
    }

    private static String arenaOf(String id) {
        return switch (id) {
            case "desert_nightmare" -> "arena_desert";
            case "siphonia" -> "arena_forest";
            case "vengeful_spirit" -> "arena_spirit";
            case "kraken" -> null;   // 바다: 전장 없음
            default -> "arena_abyss";
        };
    }

    /** 가장 최근 월드보스 위치 (나침반 ☠) */
    public Location location() {
        return events.isEmpty() ? null : events.get(events.size() - 1).at;
    }

    public static boolean isWorldBoss(String id) {
        return id != null && (id.equals("desert_nightmare") || id.equals("siphonia") || id.equals("kain") || id.equals("vengeful_spirit") || id.equals("kraken"));
    }

    /** id 가 null 이면 무작위, where 가 null 이면 스폰에서 먼 무작위 장소 */
    public boolean start(String id, Location where) {
        boolean scheduled = where == null && id == null;
        if (scheduled && events.stream().anyMatch(ev -> !"vengeful_spirit".equals(ev.id))) return false;   // 정기 출현은 하나씩
        List<String> ids = plugin.getConfig().getStringList("world-boss.bosses");
        if (ids.isEmpty()) ids = List.of("desert_nightmare", "siphonia", "kain");
        if (id == null) id = ids.get(rnd.nextInt(ids.size()));
        World w = Bukkit.getWorlds().get(0);
        Location l = where;
        for (int i = 0; l == null && i < 20; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, d = 400 + rnd.nextDouble() * plugin.getConfig().getDouble("world-boss.spread", 1200);
            Block top = kr.rpgcraft.util.Locs.surface(w, w.getSpawnLocation().clone().add(Math.cos(a) * d, 0, Math.sin(a) * d));
            if (!top.isLiquid()) l = top.getLocation().add(0, 1, 0);
        }
        if (l == null) return false;
        Event ev = new Event();
        ev.id = id;
        ev.arena = arenaOf(id) == null ? null : plugin.structures().buildArena(arenaOf(id), l);
        LivingEntity b = plugin.bosses().spawn(id, l.clone().add(0.5, 0.2, 0.5));
        if (b == null) { plugin.structures().despawnSoon(ev.arena, 1000); return false; }
        ev.boss = b.getUniqueId();
        ev.at = l.clone();
        ev.until = System.currentTimeMillis() + plugin.getConfig().getLong("world-boss.stay-minutes", 30) * 60_000;
        plugin.structures().linkBoss(ev.arena, ev.boss, ev.until);   // 재시작해도 전장이 남지 않게
        events.add(ev);
        w.strikeLightningEffect(l);
        String name = plugin.bosses().def(id).name;
        Text.announce(Text.PREFIX + Text.c("&4&l☠ 월드보스 &c" + Text.strip(Text.c(name)) + "&f 출현! &7위치 &e" + l.getBlockX() + ", " + l.getBlockZ()));
        for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.8f, 0.7f);
        return true;
    }

    /** 지금 있는 월드보스를 모두 없애고 전장도 곧바로 원래 지형으로 (관리자) */
    public int clearAll() {
        int n = 0;
        for (Event ev : new java.util.ArrayList<>(events)) {
            Entity e = Bukkit.getEntity(ev.boss);
            if (e != null && !e.isDead()) e.remove();
            plugin.structures().despawnSoon(ev.arena, 1000);
            n++;
        }
        events.clear();
        plugin.structures().despawnArenas(1000);   // 기록이 끊긴 전장까지 모두
        return n;
    }

    public int count() {
        return events.size();
    }

    private void tick() {
        long now = System.currentTimeMillis();
        events.removeIf(ev -> {
            if (now <= ev.until) return false;
            Entity e = Bukkit.getEntity(ev.boss);
            if (e != null && !e.isDead()) e.remove();
            plugin.structures().despawnSoon(ev.arena, 5000);
            Text.announce(Text.PREFIX + Text.c("&7월드보스가 전장과 함께 사라졌습니다..."));
            return true;
        });
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        events.removeIf(ev -> {
            if (!e.getEntity().getUniqueId().equals(ev.boss)) return false;
            Text.announce(Text.PREFIX + Text.c("&6월드보스가 쓰러졌습니다! &7(전장이 곧 무너집니다)"));
            plugin.structures().despawnSoon(ev.arena, 20_000L);
            return true;
        });
    }
}
