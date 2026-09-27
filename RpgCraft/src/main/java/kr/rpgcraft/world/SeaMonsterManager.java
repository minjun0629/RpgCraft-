package kr.rpgcraft.world;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.util.Text;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 바다의 괴물: 바다에서 헤엄치는 플레이어 근처에 아주 드물게 등장
 *  · 메갈로돈 (Lv.50 보스급)       — 20초마다 0.4% (헤엄치는 동안)
 *  · 거대한 크라켄 (Lv.150 월드보스급) — 20초마다 0.05%, 전체 공지 · 나침반 ☠
 */
public class SeaMonsterManager {
    private final RpgCraft plugin;
    private long megaCooldown, krakenCooldown;

    public SeaMonsterManager(RpgCraft plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 400L, 400L);
    }

    private boolean swimmingInOcean(Player p) {
        if (!p.isInWater() || p.getWorld().getEnvironment() != World.Environment.NORMAL) return false;
        String biome = p.getLocation().getBlock().getBiome().name();
        return biome.contains("OCEAN");
    }

    private Location seaSpot(Player p, double dist) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < 10; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            Location l = p.getLocation().clone().add(Math.cos(a) * dist, -2, Math.sin(a) * dist);
            Block b = l.getBlock();
            if (b.getType() == Material.WATER) return l;
        }
        return p.getLocation().clone().add(0, -3, 0);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        var c = plugin.getConfig();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR || !swimmingInOcean(p)) continue;
            if (plugin.bosses().nearSpawn(p.getLocation())) continue;   // 스폰 300칸 안 바다에는 크라켄 · 메갈로돈이 나오지 않음 (v5.4.27)
            if (now > krakenCooldown && r.nextDouble() < c.getDouble("sea.kraken-chance", 0.0005)) {
                Location l = seaSpot(p, 20);
                if (plugin.worldBoss().start("kraken", l)) {
                    krakenCooldown = now + c.getLong("sea.kraken-cooldown-minutes", 120) * 60_000;
                    Text.announce(Text.PREFIX + Text.c("&5&l심해가 요동칩니다... &d거대한 크라켄&f이 깨어났습니다!"));
                    continue;
                }
            }
            if (now > megaCooldown && r.nextDouble() < c.getDouble("sea.megalodon-chance", 0.004)) {
                Location l = seaSpot(p, 14);
                LivingEntity b = plugin.bosses().spawn("megalodon", l);
                if (b != null) {
                    megaCooldown = now + c.getLong("sea.megalodon-cooldown-minutes", 20) * 60_000;
                    for (Player q : p.getWorld().getPlayers())
                        if (q.getLocation().distanceSquared(l) < 80 * 80) {
                            q.sendTitle(Text.c("&9&l메갈로돈"), Text.c("&7무언가 거대한 것이 다가온다..."), 5, 50, 10);
                            q.playSound(q.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1f, 0.6f);
                        }
                }
            }
        }
    }
}
