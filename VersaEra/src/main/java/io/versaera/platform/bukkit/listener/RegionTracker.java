package io.versaera.platform.bukkit.listener;

import io.versaera.application.GameServices;
import io.versaera.domain.world.Region;
import io.versaera.platform.bukkit.Async;
import io.versaera.platform.bukkit.Ui;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 지역 판정. 블록을 옮겼을 때만 청크 격자 인덱스를 한 번 본다 (매 틱 검사 없음).
 * 지역이 바뀌면 이름 · 위험도를 잠깐 보여 주고, 처음이면 발견 기록 (최초 발견자는 서버에 알림).
 */
public final class RegionTracker implements Listener {
    private final GameServices s;
    private final Async async;
    private final Map<UUID, String> current = new ConcurrentHashMap<>();

    public RegionTracker(GameServices s, Async async) {
        this.s = s;
        this.async = async;
    }

    public String regionOf(UUID player) {
        return current.get(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Location to = e.getTo();
        if (to == null || (e.getFrom().getBlockX() == to.getBlockX() && e.getFrom().getBlockY() == to.getBlockY() && e.getFrom().getBlockZ() == to.getBlockZ()))
            return;
        Player p = e.getPlayer();
        Region r = s.regions.at(to.getWorld().getName(), to.getBlockX(), to.getBlockY(), to.getBlockZ());
        String now = r == null ? null : r.id(), before = current.get(p.getUniqueId());
        if (java.util.Objects.equals(now, before)) return;
        if (now == null) current.remove(p.getUniqueId());
        else current.put(p.getUniqueId(), now);
        if (r == null) return;
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(Ui.c("&f" + r.name() + "  " + Ui.danger(r.danger()))));
        String id = p.getUniqueId().toString(), name = p.getName();
        async.run("enter-region", () -> {
            var d = s.exploration.enterRegion(id, name, r);
            if (s.hidden() != null) s.hidden().checkAll(id);
            return d;
        }, d -> {
            if (!d.isNew()) return;
            p.sendTitle(Ui.c("&6" + r.name()), Ui.c("&7새로운 지역 · " + Ui.danger(r.danger())), 10, 50, 15);
            if (d.worldFirst()) org.bukkit.Bukkit.broadcastMessage(Ui.info(name + " 님이 「" + r.name() + "」을(를) 처음 발견했습니다"));
        }, null);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        current.remove(e.getPlayer().getUniqueId());
    }
}
