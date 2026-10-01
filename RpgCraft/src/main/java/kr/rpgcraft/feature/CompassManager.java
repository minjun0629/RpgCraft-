package kr.rpgcraft.feature;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.data.Setting;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 화면 상단 나침반.
 * 보스바 제목으로 바라보는 방향 기준 좌우 90°의 방위(북·북동·동…)를 보여주고,
 * 히든 상인·필드 웨이브 깃발이 있으면 그 방향에 표시한다. (리소스팩 적용 시 흰색 보스바 막대는 투명 → 글자만 보임)
 */
public class CompassManager implements Listener {
    private final RpgCraft plugin;
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private static final String[] NAMES = {"북", "북동", "동", "남동", "남", "남서", "서", "북서"};

    public CompassManager(RpgCraft plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 3L);
    }

    private void tick() {
        if (!plugin.getConfig().getBoolean("compass.enabled", true)) {
            shutdown();
            return;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            BossBar bar = bars.get(p.getUniqueId());
            if (!Setting.COMPASS.get(plugin.data().get(p))) {
                if (bar != null) { bar.removeAll(); bars.remove(p.getUniqueId()); }
                continue;
            }
            if (bar == null) {
                bar = Bukkit.createBossBar("", BarColor.WHITE, BarStyle.SOLID);
                bar.setProgress(0);
                bar.addPlayer(p);
                bars.put(p.getUniqueId(), bar);
            }
            String t = strip(p);
            if (!t.equals(bar.getTitle())) bar.setTitle(t);
        }
    }

    private static double bearing(Location from, Location to) {
        double b = Math.toDegrees(Math.atan2(to.getX() - from.getX(), -(to.getZ() - from.getZ())));
        return (b + 360) % 360;
    }

    private static double diff(double a, double b) {
        double d = (a - b) % 360;
        if (d > 180) d -= 360;
        if (d < -180) d += 360;
        return d;
    }

    /** 방위 띠 문자열 (가운데 = 바라보는 방향) */
    private String strip(Player p) {
        double heading = (p.getLocation().getYaw() + 180 + 360) % 360; // 0 = 북, 90 = 동
        Location merchant = plugin.events() == null || !plugin.getConfig().getBoolean("hidden-merchant.reveal-direction", false) ? null : plugin.events().merchantLocation();
        Location flag = plugin.events() == null ? null : plugin.events().nearestFlag(p);
        Location bounty = plugin.content() == null ? null : plugin.content().bountyLocation();
        if (bounty != null && !bounty.getWorld().equals(p.getWorld())) bounty = null;
        Location treasure = plugin.content() == null ? null : plugin.content().mapTarget(p);
        Location wb = plugin.worldBoss() == null ? null : plugin.worldBoss().location();
        if (wb != null && !wb.getWorld().equals(p.getWorld())) wb = null;
        java.util.List<Location> mates = new java.util.ArrayList<>();
        var party = plugin.party() == null ? null : plugin.party().of(p);
        if (party != null) for (Player m : party.online()) if (!m.equals(p) && m.getWorld().equals(p.getWorld())) mates.add(m.getLocation());
        StringBuilder sb = new StringBuilder();
        double step = 7.5;
        for (int i = -12; i <= 12; i++) {
            double h = (heading + i * step + 360) % 360;
            String mark = null;
            if (merchant != null && merchant.getWorld().equals(p.getWorld()) && Math.abs(diff(bearing(p.getLocation(), merchant), h)) < step / 2) mark = "§d§l✦";
            else if (flag != null && Math.abs(diff(bearing(p.getLocation(), flag), h)) < step / 2) mark = "§c§l⚑";
            else if (bounty != null && Math.abs(diff(bearing(p.getLocation(), bounty), h)) < step / 2) mark = "§4§l⚔";
            else if (wb != null && Math.abs(diff(bearing(p.getLocation(), wb), h)) < step / 2) mark = "§c§l☠";
            else if (treasure != null && Math.abs(diff(bearing(p.getLocation(), treasure), h)) < step / 2) mark = "§6§l✚";
            if (mark == null) for (Location ml : mates) if (Math.abs(diff(bearing(p.getLocation(), ml), h)) < step / 2) { mark = "§b§l●"; break; }
            if (mark == null) {
                for (int k = 0; k < 8; k++) {
                    if (Math.abs(diff(k * 45, h)) < step / 2) {
                        boolean major = k % 2 == 0;
                        mark = (i == 0 ? "§e§l" : major ? "§6§l" : "§7") + NAMES[k];
                        break;
                    }
                }
            }
            if (mark == null) mark = i == 0 ? "§e§l│" : plugin.pack().hasPack(p) ? "§7|" : "§8·";
            sb.append(mark).append(i < 12 ? " " : "");
        }
        if (!plugin.pack().hasPack(p)) return sb.toString();
        // v5.10.34 마크에이지 4R 풍 틀 (은 테두리 · 양끝 마름모 · 가운데 금빛 바늘). 전체 폭 = 틀 폭 → 틀은 화면 가운데, 방위는 그 안 가운데
        String text = sb.toString();
        int fw = 232, adv = fw + 1, n = kr.rpgcraft.pack.HudFont.textWidth(text);
        return "§f" + '\uE073' + kr.rpgcraft.pack.PackManager.shift(fw / 2 - n / 2 - adv) + text + kr.rpgcraft.pack.PackManager.shift(fw / 2 - (n - n / 2));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        BossBar b = bars.remove(e.getPlayer().getUniqueId());
        if (b != null) b.removeAll();
    }

    public void shutdown() {
        for (BossBar b : bars.values()) b.removeAll();
        bars.clear();
    }
}
