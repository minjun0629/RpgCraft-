package kr.rpgcraft.war;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.guild.Guild;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class WarListener implements Listener {
    /** v5.10.14 성벽 설치권 사용 (다른 곳에서 "설치 방지"로 취소돼도 여기서 처리) */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWallTicket(org.bukkit.event.player.PlayerInteractEvent e) {
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        var a = e.getAction();
        if (a != org.bukkit.event.block.Action.RIGHT_CLICK_AIR && a != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        org.bukkit.inventory.ItemStack it = e.getPlayer().getInventory().getItemInMainHand();
        String id = kr.rpgcraft.item.ItemData.id(it);
        if (!"wall_small".equals(id) && !"wall_large".equals(id)) return;
        e.setCancelled(true);
        plugin.wars().useWallTicket(e.getPlayer(), it, id.equals("wall_large"));
    }

    /** v5.10.13 성 안(부지 + 여유 4칸)에서는 몬스터가 자연적으로 생기지 않음 (자연 · 스포너 · 습격 · 순찰대 · 증원 등). 플러그인이 일부러 부르는 보스 등은 그대로 */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMobSpawn(org.bukkit.event.entity.CreatureSpawnEvent e) {
        if (!(e.getEntity() instanceof org.bukkit.entity.Enemy)) return;
        if (e.getSpawnReason() == org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM) return;
        if (plugin.wars().noMobs(e.getLocation())) e.setCancelled(true);
        else if (plugin.tower() != null && plugin.tower().inArena(e.getLocation())) e.setCancelled(true);   // v5.10.30 무한의 탑 전투장
    }

    private final RpgCraft plugin;

    public WarListener(RpgCraft plugin) {
        this.plugin = plugin;
    }

    private boolean admin(Player p) {
        return p.getGameMode() == GameMode.CREATIVE && p.hasPermission("rpgcraft.admin");
    }

    /** 좌클릭으로 성벽 타격 */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(BlockDamageEvent e) {
        if (admin(e.getPlayer())) return;
        if (plugin.wars().hitWall(e.getPlayer(), e.getBlock(), e.getItemInHand())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        Player p = e.getPlayer();
        if (admin(p)) return;
        if (plugin.wars().breakBeacon(p, b)) {
            e.setCancelled(true);
            return;
        }
        if (plugin.wars().castleAt(b.getLocation()) != null) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent e) {
        if (admin(e.getPlayer())) return;
        if (plugin.wars().castleAt(e.getBlock().getLocation()) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> plugin.wars().castleAt(b.getLocation()) != null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(b -> plugin.wars().castleAt(b.getLocation()) != null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        for (Block b : e.getBlocks()) if (plugin.wars().castleAt(b.getLocation()) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        for (Block b : e.getBlocks()) if (plugin.wars().castleAt(b.getLocation()) != null) e.setCancelled(true);
    }

    /** 공성전 중 사망 시 진영 스폰으로 부활 */
    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent e) {
        Guild g = plugin.guilds().of(e.getPlayer().getUniqueId());
        if (g == null) return;
        WarManager.War w = plugin.wars().warOfGuild(g.name);
        if (w == null || !w.started) return;
        Castle c = w.castle;
        if (g.name.equals(w.attacker) && c.attackerSpawn != null) e.setRespawnLocation(c.attackerSpawn);
        else if (g.name.equals(w.defender) && c.defenderSpawn != null) e.setRespawnLocation(c.defenderSpawn);
    }
}
