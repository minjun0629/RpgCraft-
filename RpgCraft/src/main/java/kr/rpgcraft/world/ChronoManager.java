package kr.rpgcraft.world;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.data.PlayerData;
import kr.rpgcraft.util.Text;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

/**
 * v5.10.18 히든 직업 「시간술사」(D 계열) — 시간의 길.
 * <ul>
 *   <li>직업 스킬(Q) 「되감기」: 3초 전 자리 · 체력으로 돌아감. 2단계부터 떠난 자리에 시간 균열(피해 + 둔화), 3단계는 도착 지점 주변 적의 시간을 멈춤(기절)</li>
 *   <li>「시간 가속」: 적을 처치할 때마다 모든 재사용 대기시간이 10 / 15 / 20% 줄어듦</li>
 *   <li>3단계 「시간 역행」: 체력이 20% 아래로 떨어지면 3초 전 체력으로 되돌아감 (90초에 한 번)</li>
 * </ul>
 */
public class ChronoManager implements Listener {
    private record Snap(Location loc, double hp) {}

    private static final int KEEP = 12;   // 5틱마다 12개 = 3초
    private final RpgCraft plugin;
    private final Map<UUID, ArrayDeque<Snap>> history = new HashMap<>();
    private long tick;

    public ChronoManager(RpgCraft plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::record, 20L, 5L);
    }

    public static int tier(PlayerData d) {
        HiddenJobManager.Tier t = HiddenJobManager.of(d);
        return t != null && t.line().equals("D") ? t.tier() : 0;
    }

    private void record() {
        tick++;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (tier(plugin.data().get(p)) == 0 || p.isDead()) { history.remove(p.getUniqueId()); continue; }
            ArrayDeque<Snap> q = history.computeIfAbsent(p.getUniqueId(), k -> new ArrayDeque<>());
            q.addLast(new Snap(p.getLocation(), plugin.health().cur(p)));
            while (q.size() > KEEP) q.removeFirst();
            if (plugin.getConfig().getBoolean("chrono.trail", true) && q.size() > 1 && tick % 2 == 0) {   // 되돌아갈 자리에 희미한 잔상
                Location at = q.peekFirst().loc();
                if (at.getWorld() != null && at.getWorld().equals(p.getWorld()))
                    p.spawnParticle(Particle.END_ROD, at.clone().add(0, 1, 0), 1, 0.1, 0.3, 0.1, 0);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        history.remove(e.getPlayer().getUniqueId());
    }

    /** 직업 스킬(Q) 「되감기」. JobManager 가 부름. @return 처리했으면 true */
    public boolean rewind(Player p) {
        PlayerData d = plugin.data().get(p);
        int tr = tier(d);
        if (tr == 0) return false;
        if (d.onCooldown("job_skill")) {
            Text.actionBar(p, "&c되감기 재사용 대기 " + String.format("%.1f", d.remaining("job_skill") / 1000.0) + "초");
            return true;
        }
        ArrayDeque<Snap> q = history.get(p.getUniqueId());
        Snap s = q == null || q.isEmpty() ? null : q.peekFirst();
        if (s == null || s.loc().getWorld() == null || !s.loc().getWorld().equals(p.getWorld()) || !s.loc().getBlock().isPassable()) {
            Text.actionBar(p, "&7되돌아갈 시간이 없습니다.");
            return true;
        }
        long cd = (long) ((16 - 2 * (tr - 1)) * 1000 * plugin.weaponSkills().cooldownMultPublic(p));
        d.cooldown("job_skill", cd);
        World w = p.getWorld();
        Location from = p.getLocation();
        double atk = Math.max(d.stats.attack, d.stats.magic);
        // 떠난 자리: 잔상 + (2단계부터) 시간 균열
        w.spawnParticle(Particle.REVERSE_PORTAL, from.clone().add(0, 1, 0), 60, 0.4, 0.8, 0.4, 0.05);
        w.playSound(from, Sound.BLOCK_BEACON_DEACTIVATE, 1.2f, 1.6f);
        if (tr >= 2) {
            kr.rpgcraft.util.Vfx.ring(from.clone().add(0, 0.2, 0), 4, Color.fromRGB(0x7FE3FF));
            Location rift = from.clone();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                w.spawnParticle(Particle.FLASH, rift.clone().add(0, 1, 0), 1);
                w.playSound(rift, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.6f, 0.5f);
                for (Entity en : w.getNearbyEntities(rift, 4, 3, 4))
                    if (en instanceof LivingEntity le && plugin.combat().isEnemy(p, le)) {
                        plugin.combat().dealSkillDamage(p, le, atk * (2.2 + 0.4 * tr), true);
                        le.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, 2, false, true));
                    }
            }, 6L);
        }
        // 3초 전 자리 · 체력으로
        Location to = s.loc().clone();
        p.teleport(to);
        double hp = plugin.health().cur(p);
        if (s.hp() > hp) plugin.health().set(p, Math.min(plugin.health().max(p), s.hp()));
        q.clear();
        w.spawnParticle(Particle.END_ROD, to.clone().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.05);
        w.playSound(to, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f);
        if (tr >= 3) {   // 시간 정지: 도착 지점 주변 적 기절
            kr.rpgcraft.util.Vfx.ring(to.clone().add(0, 0.2, 0), 7, Color.fromRGB(0xFFE9A0));
            for (Entity en : w.getNearbyEntities(to, 7, 4, 7))
                if (en instanceof LivingEntity le && plugin.combat().isEnemy(p, le) && !(le instanceof Player)) plugin.combat().stun(le, 50);
        }
        Text.actionBar(p, "&b⟲ 되감기" + (tr >= 3 ? " &e· 시간 정지" : tr >= 2 ? " &7· 시간 균열" : ""));
        return true;
    }

    /** 시간 가속: 처치할 때마다 모든 재사용 대기시간 감소 */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent e) {
        Player k = e.getEntity().getKiller();
        if (k == null) return;
        PlayerData d = plugin.data().get(k);
        int tr = tier(d);
        if (tr == 0) return;
        double cut = tr >= 3 ? 0.20 : tr == 2 ? 0.15 : 0.10;
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Long> en : d.cooldowns.entrySet())
            if (en.getValue() > now && !en.getKey().startsWith("chrono_")) en.setValue(now + (long) ((en.getValue() - now) * (1 - cut)));
    }

    /** 3단계 시간 역행: 체력 20% 아래로 떨어지면 3초 전 체력으로 (90초에 한 번) */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        PlayerData d = plugin.data().get(p);
        if (tier(d) < 3 || d.onCooldown("chrono_auto")) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline() || p.isDead() || d.onCooldown("chrono_auto")) return;
            double max = plugin.health().max(p), cur = plugin.health().cur(p);
            if (cur <= 0 || cur / max >= 0.2) return;
            ArrayDeque<Snap> q = history.get(p.getUniqueId());
            double back = q == null || q.isEmpty() ? 0 : q.peekFirst().hp();
            double want = Math.max(back, max * 0.35);
            if (want <= cur) return;
            d.cooldown("chrono_auto", 90_000);
            plugin.health().set(p, Math.min(max, want));
            p.getWorld().spawnParticle(Particle.REVERSE_PORTAL, p.getLocation().add(0, 1, 0), 80, 0.5, 1, 0.5, 0.08);
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.5f);
            Text.actionBar(p, "&e⟲ 시간 역행! &7체력이 되돌아왔습니다 (90초 뒤 다시)");
        });
    }
}
