package kr.rpgcraft.feature;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.util.Text;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;

import java.util.*;

/**
 * 야생 방지 (월드 보호).
 * - 캔 블록은 드롭 없이 사라졌다가 일정 시간 뒤 원래 모습으로 다시 생긴다 (주변 블록이 무너지지 않도록 물리 연산 없이 처리)
 * - 블록 설치, 양동이, 불 번짐, 폭발 파괴(→ 재생성), 엔더맨·경작지 밟기, 그림/액자 파괴 등을 막는다
 * - 상자·표지판 같은 특수 블록과 문·침대처럼 두 칸짜리 블록은 아예 캘 수 없다
 * - 관리자: 권한 rpgcraft.build + 크리에이티브 모드일 때만 자유롭게 건축 (/rpg관리 build 로 켜고 끔)
 * 채집 포인트(채집도구)와 공성전 성벽은 각자의 규칙이 먼저 처리한다.
 */
public class WorldProtection implements Listener {
    private record Pending(BlockData data, long restoreAt) {}

    private final RpgCraft plugin;
    private final Map<Location, Pending> pending = new LinkedHashMap<>();
    private final Set<UUID> builders = new HashSet<>();

    public WorldProtection(RpgCraft plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    // ------------------------------------------------------------------ 설정
    private boolean enabled(World w) {
        if (!plugin.getConfig().getBoolean("world-protection.enabled", true)) return false;
        List<String> only = plugin.getConfig().getStringList("world-protection.worlds");
        List<String> exclude = plugin.getConfig().getStringList("world-protection.exclude-worlds");
        if (exclude.contains(w.getName())) return false;
        return only.isEmpty() || only.contains(w.getName());
    }

    private long regenMillis() {
        return plugin.getConfig().getLong("world-protection.regen-seconds", 30) * 1000L;
    }

    public boolean isBuilder(Player p) {
        return p.getGameMode() == GameMode.CREATIVE && p.hasPermission("rpgcraft.build") && builders.contains(p.getUniqueId());
    }

    public boolean toggleBuilder(Player p) {
        if (!builders.remove(p.getUniqueId())) {
            builders.add(p.getUniqueId());
            return true;
        }
        return false;
    }

    private boolean bypass(Player p) {
        return p != null && isBuilder(p);
    }

    // ------------------------------------------------------------------ 블록 캐기 → 재생성
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        Player p = e.getPlayer();
        if (!enabled(b.getWorld()) || bypass(p)) return;
        e.setCancelled(true);
        if (plugin.events() != null && plugin.events().isFlag(b.getLocation())) return;
        if (p.getGameMode() == GameMode.CREATIVE) {
            Text.actionBar(p, "&7야생 방지 중입니다. 관리자는 &e/rpg관리 build &7로 건축 모드를 켜세요.");
            return;
        }
        BlockState st = b.getState();
        BlockData data = b.getBlockData();
        if (st instanceof TileState || data instanceof Bisected || data instanceof Bed) {
            Text.actionBar(p, "&c이 블록은 캘 수 없습니다.");
            return;
        }
        remove(b);
        b.getWorld().playSound(b.getLocation(), data.getSoundGroup().getBreakSound(), 1f, 1f);
        b.getWorld().spawnParticle(Particle.BLOCK_CRACK, b.getLocation().add(0.5, 0.5, 0.5), 20, 0.3, 0.3, 0.3, data);
    }

    /** 원래 블록을 기억하고 물리 연산 없이 공기로 바꾼다 */
    private void remove(Block b) {
        Location key = b.getLocation();
        pending.putIfAbsent(key, new Pending(b.getBlockData().clone(), System.currentTimeMillis() + regenMillis()));
        b.setType(Material.AIR, false);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<Location, Pending>> it = pending.entrySet().iterator();
        int budget = plugin.getConfig().getInt("world-protection.max-restore-per-second", 400);
        while (it.hasNext() && budget > 0) {
            Map.Entry<Location, Pending> en = it.next();
            if (en.getValue().restoreAt() > now) continue;
            restore(en.getKey(), en.getValue().data());
            it.remove();
            budget--;
        }
    }

    private void restore(Location l, BlockData data) {
        Block b = l.getBlock();
        if (!b.getType().isAir() && !b.isLiquid()) return; // 그 사이 다른 블록이 생겼으면 건드리지 않음
        b.setBlockData(data, false);
        for (Entity en : l.getWorld().getNearbyEntities(l.clone().add(0.5, 0.5, 0.5), 0.6, 1.0, 0.6)) {
            if (en instanceof LivingEntity le) {   // 블록 안에 끼지 않게 (모델을 태운 몬스터는 내렸다 다시 태움, v5.9.0)
                if (plugin.mobModels() != null && plugin.mobModels().has(le)) plugin.mobModels().teleport(le, le.getLocation().add(0, 1.1, 0));
                else le.teleport(le.getLocation().add(0, 1.1, 0));
            }
        }
        l.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, l.clone().add(0.5, 0.5, 0.5), 4, 0.3, 0.3, 0.3);
    }

    /** 서버 종료 시 아직 재생성되지 않은 블록 전부 복구 */
    public void restoreAll() {
        for (Map.Entry<Location, Pending> en : pending.entrySet()) restore(en.getKey(), en.getValue().data());
        pending.clear();
    }

    public int pendingCount() {
        return pending.size();
    }

    // ------------------------------------------------------------------ 폭발 → 드롭 없이 재생성
    private void explode(List<Block> blocks, World w) {
        if (!enabled(w)) return;
        List<Block> copy = new ArrayList<>(blocks);
        blocks.clear(); // 바닐라 파괴 대신 우리가 처리 (드롭 없음)
        for (Block b : copy) {
            if (b.getType().isAir() || b.getState() instanceof TileState || b.getBlockData() instanceof Bisected || b.getBlockData() instanceof Bed) continue;
            if (plugin.wars().castleAt(b.getLocation()) != null) continue;
            remove(b);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        explode(e.blockList(), e.getLocation().getWorld());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        explode(e.blockList(), e.getBlock().getWorld());
    }

    // ------------------------------------------------------------------ 그 밖의 지형 변경 차단
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (enabled(e.getBlock().getWorld()) && !bypass(e.getPlayer())) {
            e.setCancelled(true);
            Text.actionBar(e.getPlayer(), "&7이 서버에서는 블록을 설치할 수 없습니다.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent e) {
        if (enabled(e.getBlock().getWorld()) && !bypass(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (enabled(e.getBlock().getWorld()) && !bypass(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) {
        if (!enabled(e.getBlock().getWorld())) return;
        if (e.getCause() == BlockIgniteEvent.IgniteCause.SPREAD || e.getCause() == BlockIgniteEvent.IgniteCause.LAVA
                || e.getCause() == BlockIgniteEvent.IgniteCause.LIGHTNING || !bypass(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        if (enabled(e.getBlock().getWorld())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDecay(LeavesDecayEvent e) {
        if (enabled(e.getBlock().getWorld())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFade(BlockFadeEvent e) {
        if (enabled(e.getBlock().getWorld()) && e.getBlock().getType() == Material.FARMLAND) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityChange(EntityChangeBlockEvent e) {
        if (!enabled(e.getBlock().getWorld()) || e.getEntity() instanceof FallingBlock) return;
        if (e.getEntity() instanceof Player p && bypass(p)) return;
        e.setCancelled(true); // 엔더맨, 위더, 파괴수, 양 등의 지형 변경
    }

    @EventHandler(ignoreCancelled = true)
    public void onTrample(PlayerInteractEvent e) {
        if (e.getAction() == Action.PHYSICAL && e.getClickedBlock() != null && e.getClickedBlock().getType() == Material.FARMLAND
                && enabled(e.getClickedBlock().getWorld())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent e) {
        if (!enabled(e.getEntity().getWorld())) return;
        if (e instanceof HangingBreakByEntityEvent be && be.getRemover() instanceof Player p && bypass(p)) return;
        e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent e) {
        if (enabled(e.getEntity().getWorld()) && !bypass(e.getPlayer())) e.setCancelled(true);
    }
}
