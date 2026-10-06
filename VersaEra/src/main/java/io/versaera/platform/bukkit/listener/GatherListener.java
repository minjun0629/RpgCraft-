package io.versaera.platform.bukkit.listener;

import io.versaera.application.GameServices;
import io.versaera.domain.gathering.ResourceNode;
import io.versaera.domain.world.Region;
import io.versaera.platform.bukkit.Async;
import io.versaera.platform.bukkit.Ui;
import io.versaera.platform.bukkit.binding.ItemCodec;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.random.RandomGenerator;

/**
 * 채집 · 채광 · 벌목 · 낚시. 자원 블록을 캐면 바닐라 드롭 대신 숙련 · 지역에 따른 재료가 배달함으로 들어간다.
 * 캔 자리는 잠시 빈 자리(돌 · 공기)가 되었다가 자원마다 정해진 시간 뒤 되살아난다. 서버를 끄면 모두 되돌린다.
 */
public final class GatherListener implements Listener {
    private record Regrow(Location at, BlockData data, long at_ms) {}

    private final Plugin plugin;
    private final GameServices s;
    private final Async async;
    private final ItemCodec codec;
    private final SessionListener sessions;
    private final Map<Material, ResourceNode> byBlock = new EnumMap<>(Material.class);
    private final Map<Location, Regrow> regrow = new HashMap<>();
    private final ResourceNode river, sea;
    private final RandomGenerator rng = RandomGenerator.getDefault();

    public GatherListener(Plugin plugin, GameServices s, Async async, ItemCodec codec, SessionListener sessions) {
        this.plugin = plugin;
        this.s = s;
        this.async = async;
        this.codec = codec;
        this.sessions = sessions;
        ResourceNode r = null, d = null;
        for (ResourceNode n : s.content.resources()) {
            if (n.discipline().equals("fishing")) {
                if (n.minLevel() <= 1) r = n; else d = n;
                continue;
            }
            for (String b : n.blocks()) {
                Material m = Material.matchMaterial(b);
                if (m == null) plugin.getLogger().warning("resources.yml: 모르는 블록 " + b + " (" + n.id() + ")");
                else byBlock.put(m, n);
            }
        }
        river = r;
        sea = d;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickRegrow, 100L, 100L);
    }

    private Set<String> tagsAt(Location l) {
        Region r = s.regions.at(l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ());
        return r == null ? Set.of() : r.tags();
    }

    private boolean hasTool(Player p, String tag) {
        if (tag == null) return true;
        String t = codec.typeId(p.getInventory().getItemInMainHand());
        return t != null && codec.types().get(t).hasTag(tag);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        ResourceNode n = byBlock.get(b.getType());
        Player p = e.getPlayer();
        if (n == null || p.getGameMode() == GameMode.CREATIVE) return;
        e.setDropItems(false);
        e.setExpToDrop(0);
        if (!hasTool(p, n.tool())) {
            e.setCancelled(true);
            p.sendMessage(Ui.error("알맞은 도구가 필요합니다"));
            return;
        }
        BlockData before = b.getBlockData().clone();
        Location at = b.getLocation();
        Set<String> tags = tagsAt(at);
        String id = p.getUniqueId().toString();
        long seed = rng.nextLong();
        async.run("gather", () -> {
            int level = s.growth.level(id, n.discipline());
            if (level < n.minLevel()) return null;
            ResourceNode.Gather g = n.gather(level, tags, new SplittableRandom(seed));
            s.items.deliverBulk(id, n.yield(), g.quality(), g.amount(), "gather:" + n.id());
            s.growth.addXp(id, n.discipline(), n.xp(), n.actionLevel());
            s.growth.record(id, "gather." + n.discipline(), 1);
            return g;
        }, g -> {
            if (g == null) p.sendMessage(Ui.error("아직 다룰 수 없는 자원입니다"));
            else sessions.deliver(p);
        }, p);
        if (n.respawnSeconds() > 0) {
            Bukkit.getScheduler().runTask(plugin, () ->
                    at.getBlock().setType(n.discipline().equals("mining") ? Material.STONE : Material.AIR, false));
            regrow.put(at, new Regrow(at, before, System.currentTimeMillis() + n.respawnSeconds() * 1000L));
        }
    }

    private void tickRegrow() {
        long now = System.currentTimeMillis();
        for (Iterator<Regrow> it = regrow.values().iterator(); it.hasNext(); ) {
            Regrow r = it.next();
            if (r.at_ms() > now || !r.at().getWorld().isChunkLoaded(r.at().getBlockX() >> 4, r.at().getBlockZ() >> 4)) continue;
            r.at().getBlock().setBlockData(r.data(), false);
            it.remove();
        }
    }

    /** 서버 종료: 캐낸 자리를 모두 되살린다 */
    public void restoreAll() {
        for (Regrow r : regrow.values()) r.at().getBlock().setBlockData(r.data(), false);
        regrow.clear();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.CAUGHT_FISH || river == null) return;
        Player p = e.getPlayer();
        if (e.getCaught() != null) e.getCaught().remove();   // 바닐라 물고기 대신 지역 · 숙련에 맞는 어획
        e.setExpToDrop(0);
        Set<String> tags = tagsAt(e.getHook().getLocation());
        String id = p.getUniqueId().toString();
        long seed = rng.nextLong();
        async.run("fish", () -> {
            int level = s.growth.level(id, "fishing");
            ResourceNode n = sea != null && tags.contains("sea") && level >= sea.minLevel() ? sea : river;
            ResourceNode.Gather g = n.gather(level, tags, new SplittableRandom(seed));
            s.items.deliverBulk(id, n.yield(), g.quality(), g.amount(), "fish:" + n.id());
            s.growth.addXp(id, "fishing", n.xp(), n.actionLevel());
            s.growth.record(id, "gather.fishing", 1);
            return g;
        }, g -> sessions.deliver(p), p);
    }
}
