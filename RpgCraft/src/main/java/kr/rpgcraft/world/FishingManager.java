package kr.rpgcraft.world;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * RPG 낚시 미니게임.
 * 입질이 오면 액션바에 장력 게이지가 나타나고 표시(◆)가 좌우로 움직인다.
 * 제한 시간 없이, 원할 때 표시가 초록 구간에 오면 우클릭(릴 감기) — 3번 성공하면 낚는다. 놓치면 한 칸 물러날 뿐 실패하지 않는다.
 * 성공할수록 초록 구간이 좁아진다. 결과: 물고기(일반~전설), 보물 상자, 제작 재료. (바닐라 낚시 보상은 나오지 않음)
 */
public class FishingManager implements Listener {
    private static class Session {
        FishHook hook;
        int tick;
        double pos, speed, zoneStart, zoneWidth;
        int hit, miss;
        long until;
        BukkitTask task;
    }

    private final RpgCraft plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();

    public FishingManager(RpgCraft plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onFish(PlayerFishEvent e) {
        if (!plugin.getConfig().getBoolean("fishing.enabled", true)) return;
        Player p = e.getPlayer();
        Session s = sessions.get(p.getUniqueId());
        switch (e.getState()) {
            case BITE -> {
                if (s != null) return;
                start(p, e.getHook());
            }
            case CAUGHT_FISH, REEL_IN, IN_GROUND, FAILED_ATTEMPT -> {
                if (e.getState() == PlayerFishEvent.State.CAUGHT_FISH && e.getCaught() instanceof Item it) it.remove(); // 바닐라 보상 없음
                if (s == null) {
                    if (e.getState() == PlayerFishEvent.State.CAUGHT_FISH) e.setExpToDrop(0);
                    return;
                }
                e.setCancelled(true); // 미니게임 중 릴 감기 = 입력
                input(p, s);
            }
            default -> { }
        }
    }

    private void start(Player p, FishHook hook) {
        Session s = new Session();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        s.hook = hook;
        s.pos = r.nextDouble();
        s.speed = 0.025 + r.nextDouble() * 0.015;
        s.zoneWidth = 0.42;
        s.zoneStart = r.nextDouble() * (1 - s.zoneWidth);
        s.until = Long.MAX_VALUE;
        sessions.put(p.getUniqueId(), s);
        p.playSound(p.getLocation(), Sound.ENTITY_FISHING_BOBBER_SPLASH, 1f, 1.4f);
        s.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> step(p, s), 1L, 1L);
    }

    private void step(Player p, Session s) {
        if (!p.isOnline() || s.hook == null || !s.hook.isValid()) {   // 제한 시간 없음: 낚싯대를 거둘 때까지
            end(p, false);
            return;
        }
        s.pos += s.speed;
        if (s.pos > 1 || s.pos < 0) { s.speed = -s.speed; s.pos = Math.max(0, Math.min(1, s.pos)); }
        s.tick++;
        // 게이지: ▏어두운 물결 · 초록 목표 구간(가운데 밝게) · 흰 ◆ 표시
        int n = 34, at = (int) Math.round(s.pos * (n - 1));
        StringBuilder bar = new StringBuilder("&b⚓ ");
        for (int i = 0; i < n; i++) {
            double x = i / (double) (n - 1);
            boolean zone = x >= s.zoneStart && x <= s.zoneStart + s.zoneWidth;
            boolean zoneMid = Math.abs(x - (s.zoneStart + s.zoneWidth / 2)) < s.zoneWidth / 4;
            if (i == at) bar.append(zone ? "&e&l◆" : "&f&l◆");
            else if (zone) bar.append(zoneMid ? "&a&l▌" : "&2▌");
            else bar.append(((i + s.tick / 3) % 6 == 0) ? "&3▌" : "&8▌");
        }
        bar.append(" ");
        for (int i = 0; i < 2; i++) bar.append(i < s.hit ? "&b&l🐟" : "&8🐟");
        Text.actionBar(p, bar.toString());
        // 찌 주변 물보라 · 찌가 흔들림
        var hl = s.hook.getLocation();
        if (s.tick % 3 == 0) hl.getWorld().spawnParticle(org.bukkit.Particle.WATER_SPLASH, hl, 4, 0.2, 0.05, 0.2, 0.02);
        if (s.tick % 8 == 0) hl.getWorld().spawnParticle(org.bukkit.Particle.BUBBLE_POP, hl.clone().add(0, 0.1, 0), 3, 0.25, 0.05, 0.25, 0.01);
    }

    private void input(Player p, Session s) {
        boolean ok = s.pos >= s.zoneStart && s.pos <= s.zoneStart + s.zoneWidth;
        if (ok) {
            s.hit++;
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f + s.hit * 0.25f);
            s.zoneWidth = Math.max(0.3, s.zoneWidth - 0.05);
            s.zoneStart = ThreadLocalRandom.current().nextDouble() * (1 - s.zoneWidth);
            s.speed *= 1.05;
            if (s.hit >= 2) { end(p, true); return; }
            var hl = s.hook.getLocation();
            hl.getWorld().spawnParticle(org.bukkit.Particle.WATER_WAKE, hl, 20, 0.3, 0.1, 0.3, 0.1);
            p.playSound(hl, Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 0.8f, 1.2f + s.hit * 0.15f);
        } else {   // 놓쳐도 실패하지 않음: 한 칸 물러날 뿐
            s.miss++;
            s.hit = Math.max(0, s.hit - 1);
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.7f);
            s.hook.getWorld().spawnParticle(org.bukkit.Particle.WATER_SPLASH, s.hook.getLocation(), 12, 0.3, 0.1, 0.3, 0.1);
        }
    }

    private void end(Player p, boolean success) {
        Session s = sessions.remove(p.getUniqueId());
        if (s == null) return;
        s.task.cancel();
        if (s.hook != null && s.hook.isValid()) s.hook.remove();
        if (!success) {
            Text.actionBar(p, "&7물고기가 도망갔습니다...");
            return;
        }
        String id = roll(p);
        ItemStack it = plugin.items().create(id, 1);
        if (it == null) return;
        for (ItemStack l : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), l);
        if (id.equals("fish_treasure") && java.util.concurrent.ThreadLocalRandom.current().nextDouble() < plugin.getConfig().getDouble("treasure.map-fishing", 0.4)) {
            ItemStack map = plugin.items().create("treasure_map", 1);
            if (map != null) for (ItemStack l : p.getInventory().addItem(map).values()) p.getWorld().dropItemNaturally(p.getLocation(), l);
        }
        var t = plugin.items().get(id);
        Text.actionBar(p, "&b낚시 성공! &f" + t.name);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
        plugin.levels().addExp(p, plugin.levels().need(plugin.data().get(p).level) * 0.01);
        plugin.data().get(p).counters.merge("fish_caught", 1.0, Double::sum);
        if (plugin.questNpcs() != null) plugin.questNpcs().onFish(p);
    }

    /** 보상 추첨: 밤·비 오는 날엔 희귀 확률 ↑ */
    private String roll(Player p) {
        double luck = (p.getWorld().hasStorm() ? 1.3 : 1.0) * (plugin.cycle() != null && plugin.cycle().phase() != CycleManager.Phase.DAY ? 1.2 : 1.0);
        double r = ThreadLocalRandom.current().nextDouble() / luck;
        if (r < 0.004) return "fish_legend";
        if (r < 0.02) return "fish_treasure";
        if (r < 0.06) return "fish_gold";
        if (r < 0.14) return "fish_deep";
        if (r < 0.26) return "loot_scale";
        if (r < 0.46) return "fish_salmon";
        if (r < 0.70) return "fish_carp";
        return "fish_small";
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Session s = sessions.remove(e.getPlayer().getUniqueId());
        if (s != null) s.task.cancel();
    }
}
