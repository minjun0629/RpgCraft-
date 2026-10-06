package io.versaera.platform.bukkit.listener;

import io.versaera.application.GameServices;
import io.versaera.application.ItemService;
import io.versaera.application.port.DeliveryRepository;
import io.versaera.domain.item.ItemInstance;
import io.versaera.platform.bukkit.Async;
import io.versaera.platform.bukkit.Ui;
import io.versaera.platform.bukkit.binding.ItemCodec;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * 접속 · 퇴장 · 배달함.
 * <p>고유 아이템 배달: <b>먼저 인벤토리에 넣고</b> → DB 에서 확정. 확정이 거부되면 넣은 것을 다시 뺀다.
 * 넣은 뒤 확정 전에 서버가 꺼지면, 다음 접속 때 InventoryGuard 가 "배달 대기" 아이템을 확인하고 확정한다 → 사라지지도 두 개가 되지도 않음.</p>
 * <p>묶음 배달: <b>먼저 DB 에서 지우고</b>(take) → 지운 쪽만 지급. 두 번 받는 일이 없다.</p>
 */
public final class SessionListener implements Listener {
    private final Plugin plugin;
    private final GameServices s;
    private final Async async;
    private final ItemCodec codec;
    private final Logger log;
    private final Set<UUID> delivering = ConcurrentHashMap.newKeySet();

    public SessionListener(Plugin plugin, GameServices s, Async async, ItemCodec codec) {
        this.plugin = plugin;
        this.s = s;
        this.async = async;
        this.codec = codec;
        this.log = plugin.getLogger();
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) deliver(p);
        }, 200L, 200L);   // 10초마다 (인벤토리가 가득 차 못 받은 배달 재시도)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        String id = p.getUniqueId().toString(), name = p.getName();
        async.run("join", () -> s.profiles.join(id, name), first -> {
            if (first && p.isOnline()) p.sendTitle(Ui.c("&6베르사 새벽기"), Ui.c("&7개척력 37년"), 10, 60, 20);
            deliver(p);
        }, p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        String id = e.getPlayer().getUniqueId().toString();
        async.fire("quit", () -> {
            s.trades.cancelFor(id, "quit");
            s.profiles.quit(id);
            return null;
        });
        delivering.remove(e.getPlayer().getUniqueId());
    }

    /** 배달함을 인벤토리로 (한 번에 한 사람당 하나의 배달 작업만) */
    public void deliver(Player p) {
        if (!p.isOnline() || !delivering.add(p.getUniqueId())) return;
        String id = p.getUniqueId().toString();
        async.run("deliveries", () -> new Pending(s.items.pendingDeliveries(id), s.items.pendingBulk(id)), pend -> {
            if (!p.isOnline()) { delivering.remove(p.getUniqueId()); return; }
            Set<String> already = new HashSet<>();
            for (ItemStack it : p.getInventory().getContents()) {
                String iid = codec.instanceId(it);
                if (iid != null) already.add(iid);
            }
            for (ItemInstance it : pend.unique()) {
                if (already.contains(it.id())) {   // 지난번에 넣고 확정 전에 꺼진 경우 → 확정만
                    async.fire("confirm", () -> s.items.confirmDelivered(it.id(), id));
                    continue;
                }
                if (p.getInventory().firstEmpty() < 0) break;
                ItemStack stack = codec.unique(it);
                p.getInventory().addItem(stack);
                // 확정이 거부되면: 그 사이 InventoryGuard 가 먼저 확정했을 수 있으므로, 실제로 내 것이 아닐 때만 회수
                async.run("confirm", () -> s.items.confirmDelivered(it.id(), id) || s.items.validate(it.id(), id) == ItemService.Verdict.OK, ok -> {
                    if (!ok) {
                        p.getInventory().removeItem(stack);
                        log.warning("배달 확정 거부 → 지급 취소: item=" + it.id() + " player=" + id);
                    } else p.sendMessage(Ui.info("받음: " + Ui.c(stack.getItemMeta().getDisplayName())));
                }, null);
            }
            List<DeliveryRepository.Bulk> bulk = pend.bulk();
            deliverBulk(p, bulk, 0);
        }, null);
    }

    private void deliverBulk(Player p, List<DeliveryRepository.Bulk> list, int i) {
        if (i >= list.size() || !p.isOnline()) {
            delivering.remove(p.getUniqueId());
            return;
        }
        DeliveryRepository.Bulk b = list.get(i);
        ItemStack probe = codec.bulk(b.typeId(), b.quality(), Math.min(64, b.amount()));
        if (!fits(p, probe, b.amount())) {
            delivering.remove(p.getUniqueId());
            p.sendMessage(Ui.error("가방이 가득 차 배달을 받지 못했습니다"));
            return;
        }
        async.run("take-bulk", () -> s.items.takeBulk(b.id()), ok -> {
            if (ok && p.isOnline()) {
                int left = b.amount();
                while (left > 0) {
                    int n = Math.min(64, left);
                    for (ItemStack over : p.getInventory().addItem(codec.bulk(b.typeId(), b.quality(), n)).values())
                        p.getWorld().dropItemNaturally(p.getLocation(), over);
                    left -= n;
                }
            } else if (ok) {   // 지운 뒤 나가 버림 → 다시 배달함으로
                async.fire("re-bulk", () -> s.items.deliverBulk(b.uuid(), b.typeId(), b.quality(), b.amount(), "redelivery"));
            }
            deliverBulk(p, list, i + 1);
        }, null);
    }

    private static boolean fits(Player p, ItemStack probe, int amount) {
        int room = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (it == null) room += 64;
            else if (it.isSimilar(probe)) room += 64 - it.getAmount();
            if (room >= amount) return true;
        }
        return false;
    }

    private record Pending(List<ItemInstance> unique, List<DeliveryRepository.Bulk> bulk) {}
}
