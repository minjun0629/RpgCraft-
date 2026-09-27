package kr.rpgcraft.gui;

import kr.rpgcraft.RpgCraft;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public class GuiListener implements Listener {
    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Gui g) g.handleClick(e);
        else if (Gui.isFiller(e.getCurrentItem())) { // 어떤 경로로든 새어나온 판유리는 클릭하는 순간 제거
            e.setCancelled(true);
            e.setCurrentItem(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Gui g) g.handleDrag(e);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof Gui g) {
            g.onClose(e);
            if (e.getPlayer() instanceof Player p) Bukkit.getScheduler().runTask(RpgCraft.get(), () -> Gui.purge(p));
        }
    }

    /** 이전 버전에서 이미 인벤토리에 들어간 판유리 정리 */
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        int n = Gui.purge(e.getPlayer());
        if (n > 0) RpgCraft.get().getLogger().info(e.getPlayer().getName() + " 의 인벤토리에서 메뉴 판유리 " + n + "개를 정리했습니다.");
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (Gui.isFiller(e.getItem().getItemStack())) {
            e.setCancelled(true);
            e.getItem().remove();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (Gui.isFiller(e.getItemDrop().getItemStack())) e.getItemDrop().remove();
    }
}
