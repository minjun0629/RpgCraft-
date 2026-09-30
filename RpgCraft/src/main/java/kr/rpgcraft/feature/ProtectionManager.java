package kr.rpgcraft.feature;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.util.CachedServerIcon;

import java.io.File;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 서버 보호 (v5.5.0)
 *  1. 서버 목록: 아이콘 + 두 줄 설명(주소 포함)
 *  2. 접속 폭주 · 봇 방어: 같은 IP 가 짧은 시간에 여러 번 접속 시도 → 잠시 차단, IP 당 동시 접속 수 제한,
 *     전체 접속 시도가 폭주하면 잠시 "처음 온 사람" 접속만 막는 잠금 모드
 *     (진짜 디도스(대역폭 공격)는 플러그인으로 막을 수 없음 — 호스팅 업체의 디도스 방어 / TCPShield 같은 프록시가 필요)
 *  3. 엑스레이 의심 알림: 최근 채굴에서 돌 대비 다이아 · 고대 잔해 비율이 비정상이면 관리자에게 알림 + 로그
 *     (실제 엑스레이 차단은 Paper 의 anti-xray 설정으로 — README 참고)
 */
public class ProtectionManager implements Listener {
    private final RpgCraft plugin;
    private CachedServerIcon icon;

    /** IP → 최근 접속 시도 시각들 */
    private final Map<String, Deque<Long>> attempts = new ConcurrentHashMap<>();
    private final Map<String, Long> blockedUntil = new ConcurrentHashMap<>();
    private final Deque<Long> globalAttempts = new java.util.concurrent.ConcurrentLinkedDeque<>();
    private volatile long lockdownUntil;

    /** 플레이어 → 최근 채굴 기록 {시각, 종류(0 돌 · 1 다이아 · 2 고대 잔해 · 3 에메랄드)} */
    private final Map<UUID, Deque<long[]>> mining = new HashMap<>();
    private final Map<UUID, Long> lastAlert = new HashMap<>();

    public ProtectionManager(RpgCraft plugin) {
        this.plugin = plugin;
        loadIcon();
    }

    private FileConfiguration c() {
        return plugin.getConfig();
    }

    // ------------------------------------------------------------------ 1. 서버 목록
    private void loadIcon() {
        try {
            File f = new File(plugin.getDataFolder(), "server-icon.png");   // 직접 바꾸고 싶으면 이 파일을 64x64 PNG 로 교체
            if (!f.exists()) try (InputStream in = plugin.getResource("server-icon.png")) {
                if (in != null) java.nio.file.Files.copy(in, f.toPath());
            }
            if (f.exists()) icon = Bukkit.loadServerIcon(javax.imageio.ImageIO.read(f));
        } catch (Exception ex) {
            plugin.getLogger().warning("서버 아이콘을 불러오지 못했습니다: " + ex.getMessage());
        }
    }

    @EventHandler
    public void onPing(ServerListPingEvent e) {
        if (!c().getBoolean("server-list.enabled", true)) return;
        String addr = address();
        String l1 = c().getString("server-list.motd-line1", "&6&l⚔ RpgCraft &8| &e대규모 RPG · PvP 서버");
        String l2 = c().getString("server-list.motd-line2", "&f주소: &b{address}");
        if (addr == null || addr.isBlank()) l2 = c().getString("server-list.motd-line2-no-address", "&7레벨 · 보스 · 길드 · 공성전 · 던전");
        e.setMotd(Text.c(l1) + "\n" + Text.c(l2.replace("{address}", addr == null ? "" : addr)));
        if (icon != null) {
            try { e.setServerIcon(icon); } catch (Exception ignored) { }
        }
    }

    /**
     * server-list.address 에 적은 도메인 (예: play.rpgcraft.kr).
     * v5.6.4: 숫자 IP 는 보여 주지 않음 — 비어 있거나 IP 면 두 번째 줄은 소개 문구(motd-line2-no-address).
     * auto-address: true 로 켜면 예전처럼 서버 IP 를 자동으로 표시.
     */
    public String address() {
        String a = c().getString("server-list.address", "");
        if (a != null && !a.isBlank()) {
            a = a.trim();
            boolean ip = a.matches("[0-9.:\\[\\]]+") || a.matches("(?i)[0-9a-f:\\[\\]]+:[0-9a-f:\\[\\]]*");   // IPv4 · IPv6 (포트 포함)
            return ip && !c().getBoolean("server-list.show-ip", false) ? "" : a;
        }
        if (!c().getBoolean("server-list.auto-address", false)) return "";
        String ip = Bukkit.getIp();
        if ((ip == null || ip.isBlank()) && plugin.pack() != null) ip = plugin.pack().publicIp();
        if (ip == null || ip.isBlank()) return "";
        return Bukkit.getPort() == 25565 ? ip : ip + ":" + Bukkit.getPort();
    }

    // ------------------------------------------------------------------ 2. 접속 폭주 · 봇
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        if (!c().getBoolean("anti-bot.enabled", true) || e.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        String ip = e.getAddress() == null ? "?" : e.getAddress().getHostAddress();
        long now = System.currentTimeMillis();
        Long until = blockedUntil.get(ip);
        if (until != null && until > now) {
            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Text.c("&c접속 시도가 너무 잦습니다.\n&7" + ((until - now) / 1000 + 1) + "초 뒤에 다시 접속해 주세요."));
            return;
        }
        // 같은 IP: window 초 안에 max 번 넘게 시도하면 block 초 차단
        int window = c().getInt("anti-bot.ip-window-seconds", 30), max = c().getInt("anti-bot.ip-max-attempts", 5);
        Deque<Long> q = attempts.computeIfAbsent(ip, k -> new java.util.concurrent.ConcurrentLinkedDeque<>());
        q.addLast(now);
        while (!q.isEmpty() && now - q.peekFirst() > window * 1000L) q.pollFirst();
        if (q.size() > max) {
            blockedUntil.put(ip, now + c().getInt("anti-bot.ip-block-seconds", 120) * 1000L);
            plugin.getLogger().warning("[봇 방어] 접속 시도가 너무 잦은 IP 차단: " + ip + " (" + q.size() + "회/" + window + "초)");
            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Text.c("&c접속 시도가 너무 잦습니다. 잠시 뒤 다시 접속해 주세요."));
            return;
        }
        // IP 당 동시 접속 수
        int perIp = c().getInt("anti-bot.max-accounts-per-ip", 3);
        if (perIp > 0) {
            int same = 0;
            for (Player p : Bukkit.getOnlinePlayers())
                if (p.getAddress() != null && p.getAddress().getAddress() != null && ip.equals(p.getAddress().getAddress().getHostAddress())) same++;
            if (same >= perIp) {
                e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Text.c("&c한 IP 에서 동시에 " + perIp + "명까지 접속할 수 있습니다."));
                return;
            }
        }
        // 전체 폭주: 10초 안에 flood 번 넘게 시도하면 lockdown 초 동안 "처음 오는 사람" 접속 잠금
        globalAttempts.addLast(now);
        while (!globalAttempts.isEmpty() && now - globalAttempts.peekFirst() > 10_000) globalAttempts.pollFirst();
        if (globalAttempts.size() > c().getInt("anti-bot.flood-per-10s", 20) && lockdownUntil < now) {
            lockdownUntil = now + c().getInt("anti-bot.lockdown-seconds", 60) * 1000L;
            plugin.getLogger().warning("[봇 방어] 접속 폭주 감지 — " + c().getInt("anti-bot.lockdown-seconds", 60) + "초 동안 처음 오는 사람 접속을 막습니다.");
        }
        if (lockdownUntil > now && !new File(new File(plugin.getDataFolder(), "players"), e.getUniqueId() + ".yml").exists())
            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Text.c("&e지금 접속이 몰려 새로운 분의 접속을 잠시 막고 있습니다.\n&71분 뒤에 다시 접속해 주세요."));
    }

    // ------------------------------------------------------------------ 3. 엑스레이 의심 알림
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (!c().getBoolean("anti-xray-alert.enabled", true)) return;
        Material m = e.getBlock().getType();
        int kind = switch (m) {
            case STONE, DEEPSLATE, TUFF, NETHERRACK, ANDESITE, DIORITE, GRANITE, BLACKSTONE, BASALT -> 0;
            case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE -> 1;
            case ANCIENT_DEBRIS -> 2;
            case EMERALD_ORE, DEEPSLATE_EMERALD_ORE -> 3;
            default -> -1;
        };
        if (kind < 0) return;
        Player p = e.getPlayer();
        if (p.hasPermission("rpgcraft.admin")) return;
        long now = System.currentTimeMillis(), window = c().getLong("anti-xray-alert.window-minutes", 30) * 60_000;
        Deque<long[]> q = mining.computeIfAbsent(p.getUniqueId(), k -> new ArrayDeque<>());
        q.addLast(new long[]{now, kind});
        while (!q.isEmpty() && now - q.peekFirst()[0] > window) q.pollFirst();
        if (kind == 0) return;
        int stone = 0, rare = 0;
        for (long[] r : q) if (r[1] == 0) stone++; else rare++;
        int minRare = c().getInt("anti-xray-alert.min-rare", 12);
        double ratio = c().getDouble("anti-xray-alert.max-rare-per-100-stone", 4.0);
        if (rare < minRare || rare * 100.0 / Math.max(1, stone) <= ratio) return;
        if (now - lastAlert.getOrDefault(p.getUniqueId(), 0L) < 10 * 60_000) return;
        lastAlert.put(p.getUniqueId(), now);
        String msg = "&c[엑스레이 의심] &e" + p.getName() + " &7— 최근 " + (window / 60_000) + "분: 희귀 광석 " + rare + "개 / 돌 " + stone + "개 &8("
                + e.getBlock().getX() + ", " + e.getBlock().getY() + ", " + e.getBlock().getZ() + ")";
        plugin.getLogger().warning(Text.strip(Text.c(msg)));
        for (Player a : Bukkit.getOnlinePlayers()) if (a.hasPermission("rpgcraft.admin")) Text.msg(a, msg);
    }
}
