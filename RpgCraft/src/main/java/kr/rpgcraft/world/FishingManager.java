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
 * 제한 시간 없이, 원할 때 표시가 초록 구간에 오면 우클릭(릴 감기) — 3번(fishing.hits) 성공하면 낚는다. 놓치면 한 칸 물러날 뿐 실패하지 않는다.
 * 성공할수록 초록 구간이 좁아진다. 입질 순간 무엇이 걸렸는지 정해지고, 희귀할수록 표시가 빠르다.
 * 결과: 바이옴 · 밤 · 비에 따라 50종의 어종 (FishSpecies), 보물 상자, 제작 재료. (바닐라 낚시 보상은 나오지 않음)
 */
public class FishingManager implements Listener {
    private static class Session {
        FishHook hook;
        int tick;
        double pos, speed, zoneStart, zoneWidth;
        int hit, miss;
        String catchId;
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
        s.catchId = roll(p, hook);
        var t = plugin.items().get(s.catchId);
        double hard = t == null ? 1 : switch (t.grade) { case RARE -> 1.1; case UNIQUE -> 1.22; case LEGEND -> 1.38; default -> 1.0; };
        s.speed = (0.025 + r.nextDouble() * 0.015) * hard;
        if (hard >= 1.22) Text.msg(p, hard >= 1.38 ? "&6&l⚡ 낚싯대가 부러질 듯 휘어집니다...! &e(엄청난 대물)" : "&e⚡ 묵직한 손맛! &7(대물이 걸렸습니다)");
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
        for (int i = 0; i < hits(); i++) bar.append(i < s.hit ? "&b&l🐟" : "&8🐟");
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
            s.zoneWidth = Math.max(0.26, s.zoneWidth - 0.05);
            s.zoneStart = ThreadLocalRandom.current().nextDouble() * (1 - s.zoneWidth);
            s.speed *= 1.05;
            if (s.hit >= hits()) { end(p, true); return; }
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
        org.bukkit.Location hookAt = s.hook != null ? s.hook.getLocation() : null;
        if (s.hook != null && s.hook.isValid()) s.hook.remove();
        if (!success) {
            Text.actionBar(p, "&7물고기가 도망갔습니다...");
            return;
        }
        String id = s.catchId;
        ItemStack it = plugin.items().create(id, 1);
        if (it == null) return;
        for (ItemStack l : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), l);
        var t = plugin.items().get(id);
        FishSpecies.Species sp = species(id);
        String size = "";
        if (sp != null) {   // 크기 (연출용)
            double base = switch (sp.grade()) { case RARE -> 50; case UNIQUE -> 90; case LEGEND -> 180; default -> 28; };
            size = String.format(" &7(%.1fcm)", base * sp.size() * (0.75 + ThreadLocalRandom.current().nextDouble() * 0.6));
        }
        Text.actionBar(p, "&b낚시 성공! " + t.grade.color + t.name + size);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
        var data = plugin.data().get(p);
        if (sp != null && data.counters.merge("fishdex_" + id, 1.0, Double::sum) == 1.0) {
            int found = 0;
            for (FishSpecies.Species o : FishSpecies.all()) if (data.counters.containsKey("fishdex_" + o.id())) found++;
            Text.msg(p, "&d✦ 새로운 어종 발견! " + t.grade.color + t.name + " &7(" + found + "/" + FishSpecies.all().size() + ")");
        }
        if (t.grade == kr.rpgcraft.item.Grade.LEGEND)
            Text.announce(Text.PREFIX + Text.c("&6&l" + Text.name(p) + "&e님이 전설의 어종 &6&l" + t.name + "&e" + size + "&e을(를) 낚았습니다!"));
        plugin.levels().addExp(p, plugin.levels().need(plugin.data().get(p).level) * 0.01);
        plugin.data().get(p).counters.merge("fish_caught", 1.0, Double::sum);
        if (plugin.questNpcs() != null) plugin.questNpcs().onFish(p);
        if (plugin.seaMonsters() != null) plugin.seaMonsters().onFishCatch(p, hookAt);   // 바다 낚시: 아주 낮은 확률로 메갈로돈 · 크라켄 (v5.4.32)
    }

    private int hits() {
        return Math.max(1, plugin.getConfig().getInt("fishing.hits", 3));
    }

    private static FishSpecies.Species species(String id) {
        for (FishSpecies.Species s : FishSpecies.all()) if (s.id().equals(id)) return s;
        return null;
    }

    /** 보상 추첨: 등급을 먼저 뽑고, 그 등급에서 찌가 있는 바이옴 · 밤 · 비에 맞는 어종을 고른다. 밤·비 오는 날엔 희귀 확률 ↑ */
    private String roll(Player p, FishHook hook) {
        boolean rain = p.getWorld().hasStorm(), night = plugin.cycle() != null && plugin.cycle().phase() != CycleManager.Phase.DAY;
        double luck = (rain ? 1.3 : 1.0) * (night ? 1.2 : 1.0);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double r = rnd.nextDouble() / luck;
        kr.rpgcraft.item.Grade g;
        if (r < 0.005) g = kr.rpgcraft.item.Grade.LEGEND;
        else if (r < 0.005 + plugin.getConfig().getDouble("fishing.treasure-chance", 0.008)) return "fish_treasure";
        else if (r < 0.065) g = kr.rpgcraft.item.Grade.UNIQUE;
        else if (r < 0.19) g = kr.rpgcraft.item.Grade.RARE;
        else if (r < 0.28) return "loot_scale";
        else g = kr.rpgcraft.item.Grade.NORMAL;
        String biome = (hook != null ? hook.getLocation() : p.getLocation()).getBlock().getBiome().name();
        List<FishSpecies.Species> pool = new ArrayList<>();
        double total = 0;
        for (FishSpecies.Species s : FishSpecies.all())
            if (s.grade() == g && s.fits(biome, night, rain) && plugin.items().get(s.id()) != null) { pool.add(s); total += s.anywhere() ? 1 : 3; }
        if (pool.isEmpty()) return switch (g) { case LEGEND -> "fish_legend"; case UNIQUE -> "fish_gold"; case RARE -> "fish_deep"; default -> "fish_small"; };
        double pick = rnd.nextDouble() * total;
        for (FishSpecies.Species s : pool) if ((pick -= s.anywhere() ? 1 : 3) < 0) return s.id();
        return pool.get(pool.size() - 1).id();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Session s = sessions.remove(e.getPlayer().getUniqueId());
        if (s != null) s.task.cancel();
    }
}
