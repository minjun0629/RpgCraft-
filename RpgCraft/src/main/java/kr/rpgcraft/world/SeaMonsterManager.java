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
 *  · 바다에서 낚시에 성공할 때도 아주 낮은 확률 (v5.4.32): 메갈로돈 0.3% · 크라켄 0.05% (쿨타임 공유)
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

    /** 낚시 성공 때: 바다(찌 위치)에서 아주 낮은 확률로 메갈로돈 / 크라켄 */
    public void onFishCatch(Player p, Location hook) {
        if (hook == null || hook.getWorld() == null || hook.getWorld().getEnvironment() != World.Environment.NORMAL) return;
        if (!hook.getBlock().getBiome().name().contains("OCEAN") || plugin.bosses().nearSpawn(hook)) return;
        long now = System.currentTimeMillis();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        var c = plugin.getConfig();
        Location under = hook.clone().add(0, -3, 0);
        if (under.getBlock().getType() != Material.WATER) under = hook.clone().add(0, -1, 0);
        if (now > krakenCooldown && r.nextDouble() < c.getDouble("sea.fishing-kraken-chance", 0.0005)) {
            if (plugin.worldBoss().start("kraken", under)) {
                krakenCooldown = now + c.getLong("sea.kraken-cooldown-minutes", 120) * 60_000;
                p.sendTitle(Text.c("&5&l낚싯줄이 끊어졌다!"), Text.c("&d무언가 거대한 것이 깨어났다..."), 5, 60, 15);
                Text.announce(Text.PREFIX + Text.c("&d" + Text.name(p) + "&f님의 낚싯줄에 &5&l거대한 크라켄&f이 걸려 깨어났습니다!"));
                return;
            }
        }
        if (now > megaCooldown && r.nextDouble() < c.getDouble("sea.fishing-megalodon-chance", 0.003)) {
            LivingEntity b = plugin.bosses().spawn("megalodon", under);
            if (b != null) {
                megaCooldown = now + c.getLong("sea.megalodon-cooldown-minutes", 20) * 60_000;
                for (Player q : p.getWorld().getPlayers())
                    if (q.getLocation().distanceSquared(under) < 80 * 80) {
                        q.sendTitle(Text.c("&9&l메갈로돈"), Text.c("&7" + Text.name(p) + "의 낚싯줄에 거대한 것이 걸렸다!"), 5, 50, 10);
                        q.playSound(q.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1f, 0.6f);
                    }
            }
        }
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
