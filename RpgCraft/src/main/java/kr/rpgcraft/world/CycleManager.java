package kr.rpgcraft.world;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.util.Text;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

import java.util.Random;

/**
 * 낮 / 밤 콘텐츠 + 식물 정리.
 * 낮: 채집 재사용 대기시간 -30%, 전리품 판매가 +10%
 * 밤: 커스텀 몬스터·정예 출현 ↑, 처치 보상 +25%
 * 핏빛 달(밤의 일부): 몬스터 체력 +30%·공격력 +20%, 처치 보상 +60%, 정예·중간 보스 대량 출현
 * 새로 생성되는 청크의 해바라기·큰 풀·풀 과다 생성을 정리한다 (/rpg관리 plants 로 주변 청크도 정리)
 */
public class CycleManager implements Listener {
    public enum Phase { DAY, NIGHT, BLOOD_MOON }

    private final RpgCraft plugin;
    private final Random rnd = new Random();
    private Phase phase = Phase.DAY;

    public CycleManager(RpgCraft plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 100L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::daySpawn, 200L, 300L);
        if (plugin.getConfig().getBoolean("day-night.no-rain", true))   // 비 · 눈 · 뇌우 없음
            for (World w : Bukkit.getWorlds()) { w.setStorm(false); w.setThundering(false); w.setGameRule(GameRule.DO_WEATHER_CYCLE, false); }
    }

    public Phase phase() {
        return phase;
    }

    private void tick() {
        World w = Bukkit.getWorlds().get(0);
        long t = w.getTime();
        boolean night = t >= 13000 && t < 23000;
        if (night && phase == Phase.DAY) {
            phase = forceBlood || rnd.nextDouble() < plugin.getConfig().getDouble("day-night.blood-moon-chance", 0.15) ? Phase.BLOOD_MOON : Phase.NIGHT;
            if (phase == Phase.BLOOD_MOON) {
                Text.announce(Text.PREFIX + Text.c("&4&l☾ 핏빛 달이 떠올랐다!"));
                for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.4f, 0.6f);
            } else Text.announce(Text.PREFIX + Text.c("&9☾ 밤이 찾아왔습니다."));
        } else if (!night && phase != Phase.DAY) {
            phase = Phase.DAY;
            Text.announce(Text.PREFIX + Text.c("&e☀ 날이 밝았습니다."));
        }
    }

    /** 낮에는 햇빛 때문에 몬스터가 거의 안 나오므로 인간형 몬스터를 플레이어 주변에 스폰 */
    private void daySpawn() {
        if (phase != Phase.DAY || !plugin.getConfig().getBoolean("day-night.day-humanoids", true)) return;
        int cap = plugin.getConfig().getInt("day-night.day-humanoid-cap", 4);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld().getEnvironment() != World.Environment.NORMAL || p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) continue;
            if (p.getLocation().distanceSquared(p.getWorld().getSpawnLocation()) < 60 * 60) continue;
            int near = 0;
            for (var e : p.getNearbyEntities(40, 20, 40)) {
                var d = e instanceof org.bukkit.entity.LivingEntity le ? plugin.customMobs().of(le) : null;
                if (d != null && d.day) near++;
            }
            if (near < cap) plugin.customMobs().spawnDay(p);
        }
    }

    /** 관리자: 시간대 강제 변경 */
    public void force(Phase ph) {
        World w = Bukkit.getWorlds().get(0);
        w.setTime(ph == Phase.DAY ? 1000 : 14000);
        if (ph != Phase.DAY && phase != Phase.DAY) phase = Phase.DAY;   // 밤 → 핏빛 달 전환도 되도록
        if (ph == Phase.BLOOD_MOON) forceBlood = true;
        tick();
        forceBlood = false;
    }

    private boolean forceBlood;

    public double rewardMult() {
        return switch (phase) {
            case DAY -> 1.0;
            case NIGHT -> plugin.getConfig().getDouble("day-night.night-reward", 1.25);
            case BLOOD_MOON -> plugin.getConfig().getDouble("day-night.blood-moon-reward", 1.6);
        };
    }

    public double customMobChanceMult() {
        return phase == Phase.DAY ? 1.0 : phase == Phase.NIGHT ? 1.6 : 2.2;
    }

    public double eliteChanceMult() {
        return phase == Phase.DAY ? 1.0 : phase == Phase.NIGHT ? 1.5 : 3.0;
    }

    public double gatherCooldownMult() {
        return phase == Phase.DAY ? plugin.getConfig().getDouble("day-night.day-gather-cooldown", 0.7) : 1.0;
    }

    public double sellMult() {
        return phase == Phase.DAY ? 1.1 : 1.0;
    }

    public boolean bloodMoon() {
        return phase == Phase.BLOOD_MOON;
    }

    // ------------------------------------------------------------------ 식물 정리
    private final NamespacedKey CLEANED = new NamespacedKey("rpgcraft", "plants_cleaned");

    @EventHandler
    public void onChunk(ChunkLoadEvent e) {
        if (!plugin.getConfig().getBoolean("world.clean-plants", true) || e.getWorld().getEnvironment() != World.Environment.NORMAL) return;
        Chunk c = e.getChunk();
        if (c.getPersistentDataContainer().has(CLEANED, org.bukkit.persistence.PersistentDataType.BYTE)) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!c.isLoaded()) return;
            clean(c);
            c.getPersistentDataContainer().set(CLEANED, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        }, 20L);
    }

    private static boolean plant(Material m) {
        return m == Material.SUNFLOWER || m == Material.TALL_GRASS || m == Material.LARGE_FERN || m == Material.FERN
                || m.name().equals("GRASS") || m.name().equals("SHORT_GRASS") || Tag.FLOWERS.isTagged(m) || Tag.SMALL_FLOWERS.isTagged(m)
                || m == Material.LILAC || m == Material.ROSE_BUSH || m == Material.PEONY || m == Material.DEAD_BUSH;
    }

    /** 해바라기 전부, 큰 풀·큰 고사리 대부분, 풀·고사리 일부 제거 */
    public int clean(Chunk c) {
        int removed = 0;
        double keepGrass = plugin.getConfig().getDouble("world.keep-grass", 0.35);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                Block top = c.getWorld().getHighestBlockAt(c.getX() * 16 + x, c.getZ() * 16 + z);
                for (int dy = 1; dy <= 2; dy++) {
                    Block b = top.getRelative(0, dy, 0);
                    Material m = b.getType();
                    if (plant(m) && !(Tag.LEAVES.isTagged(m)) && (keepGrass <= 0 || rnd.nextDouble() > keepGrass)) {
                        Block up = b.getRelative(0, 1, 0);
                        if (up.getType() == m) up.setType(Material.AIR, false);
                        b.setType(Material.AIR, false);
                        removed++;
                        break;
                    }
                }
            }
        }
        return removed;
    }

    public int cleanAround(Location l, int radiusChunks) {
        int n = 0;
        for (int dx = -radiusChunks; dx <= radiusChunks; dx++)
            for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
                Chunk c = l.getWorld().getChunkAt(l.getChunk().getX() + dx, l.getChunk().getZ() + dz);
                n += clean(c);
            }
        return n;
    }
}
