package io.versaera.platform.bukkit.listener;

import io.versaera.application.GameServices;
import io.versaera.application.ItemService;
import io.versaera.platform.bukkit.Async;
import io.versaera.platform.bukkit.Ui;
import io.versaera.platform.bukkit.binding.ItemCodec;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.*;

/**
 * 인벤토리 속 고유 아이템을 DB 와 대조한다 (접속 시 + 30초마다 한 명씩 돌아가며).
 * <ul>
 *   <li>DB 에 없거나 · 파괴됐거나 · 남의 것이거나 · 거래 보관 중인 아이템 → 격리(제거) + 감사 로그</li>
 *   <li>같은 아이디가 두 칸 이상 → 전부 격리 (복제 의심)</li>
 *   <li>배달 대기 상태인데 이미 갖고 있으면 → 배달 확정 (서버가 중간에 꺼진 경우)</li>
 * </ul>
 */
public final class InventoryGuard implements Listener {
    private final GameServices s;
    private final Async async;
    private final ItemCodec codec;
    private final Deque<UUID> rotation = new ArrayDeque<>();

    public InventoryGuard(Plugin plugin, GameServices s, Async async, ItemCodec codec) {
        this.s = s;
        this.async = async;
        this.codec = codec;
        Bukkit.getScheduler().runTaskTimer(plugin, this::rotate, 600L, 40L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        scan(e.getPlayer());
    }

    /** 2초마다 한 명씩 — 접속자가 많아도 한 틱에 몰리지 않게 */
    private void rotate() {
        if (rotation.isEmpty()) for (Player p : Bukkit.getOnlinePlayers()) rotation.add(p.getUniqueId());
        UUID next = rotation.poll();
        Player p = next == null ? null : Bukkit.getPlayer(next);
        if (p != null) scan(p);
    }

    public void scan(Player p) {
        String owner = p.getUniqueId().toString();
        Map<String, Integer> seen = new HashMap<>();
        for (Inventory inv : List.of(p.getInventory(), p.getEnderChest()))
            for (ItemStack it : inv.getContents()) {
                String id = codec.instanceId(it);
                if (id != null) seen.merge(id, it.getAmount(), Integer::sum);
            }
        if (seen.isEmpty()) return;
        async.run("guard", () -> {
            Map<String, ItemService.Verdict> out = new HashMap<>();
            for (Map.Entry<String, Integer> e : seen.entrySet()) {
                ItemService.Verdict v = s.items.validate(e.getKey(), owner);
                if (e.getValue() > 1) {
                    s.audit.record("ITEM_QUARANTINED", owner, e.getKey(), "duplicate x" + e.getValue(), null);
                    v = ItemService.Verdict.UNKNOWN;
                } else if (v == ItemService.Verdict.AWAITING_DELIVERY) {
                    s.items.confirmDelivered(e.getKey(), owner);
                    v = ItemService.Verdict.OK;
                } else if (v != ItemService.Verdict.OK) {
                    s.audit.record("ITEM_QUARANTINED", owner, e.getKey(), v.name(), null);
                }
                out.put(e.getKey(), v);
            }
            return out;
        }, verdicts -> {
            int removed = 0;
            for (Inventory inv : List.of(p.getInventory(), p.getEnderChest())) {
                ItemStack[] c = inv.getContents();
                for (int i = 0; i < c.length; i++) {
                    String id = codec.instanceId(c[i]);
                    if (id == null) continue;
                    ItemService.Verdict v = verdicts.get(id);
                    if (v != null && v != ItemService.Verdict.OK) {
                        c[i] = null;
                        removed++;
                    }
                }
                inv.setContents(c);
            }
            if (removed > 0) p.sendMessage(Ui.error("확인되지 않은 아이템 " + removed + "개를 회수했습니다"));
        }, null);
    }
}
