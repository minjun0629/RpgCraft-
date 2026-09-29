package kr.rpgcraft.pack;

import com.sun.net.httpserver.HttpServer;
import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * 전용 리소스팩 자동 적용.
 * - 플러그인에 내장된 resourcepack.zip 을 데이터 폴더로 꺼내 SHA-1 을 계산
 * - 내장 웹서버(self-host)로 배포하거나 외부 URL 사용
 * - 접속 시 자동 전송, 거절/실패 처리
 * - 모든 플레이어가 팩을 쓰는 환경(required)이면 GUI 제목에 폰트 글리프 배경을 씌운다
 */
public class PackManager implements Listener {
    private static final int GUI_WIDTH = 176;

    private final RpgCraft plugin;
    private final File packFile;
    private final Set<UUID> loaded = ConcurrentHashMap.newKeySet();
    private byte[] hash;
    private String hashHex = "";
    private HttpServer server;

    public PackManager(RpgCraft plugin) {
        this.plugin = plugin;
        this.packFile = new File(plugin.getDataFolder(), "resourcepack.zip");
        load();
        long every = Math.max(1, plugin.getConfig().getLong("resourcepack.refresh-minutes", 10)) * 60 * 20;
        Bukkit.getScheduler().runTaskTimer(plugin, () -> { if (enabled() && !externalUrl().isEmpty()) fetchExternalHash(); }, every, every);
    }

    // ------------------------------------------------------------------ 설정
    private boolean enabled() {
        return plugin.getConfig().getBoolean("resourcepack.enabled", true);
    }

    public boolean required() {
        return plugin.getConfig().getBoolean("resourcepack.required", false);
    }

    /** GUI 배경 글리프 사용 여부. 팩이 없는 사람에게는 네모 글자로 보이므로 기본은 required 일 때만 */
    public boolean overlay() {
        String v = plugin.getConfig().getString("resourcepack.gui-overlay", "auto");
        if ("true".equalsIgnoreCase(v)) return true;
        if ("false".equalsIgnoreCase(v)) return false;
        return enabled() && required();
    }

    public void load() {
        stopServer();
        syncBundledPack();
        if (!externalUrl().isEmpty()) fetchExternalHash();
        if (publicIp == null) detectPublicIp();
        hash = null;
        hashHex = "";
        if (packFile.exists()) {
            try {
                hash = MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(packFile.toPath()));
                StringBuilder sb = new StringBuilder();
                for (byte b : hash) sb.append(String.format("%02x", b));
                hashHex = sb.toString();
            } catch (Exception e) {
                plugin.getLogger().warning("리소스팩 해시 계산 실패: " + e.getMessage());
            }
        }
        if (enabled() && externalUrl().isEmpty() && plugin.getConfig().getBoolean("resourcepack.self-host.enabled", true)) startServer();
    }

    /**
     * 플러그인 jar 에 들어있는 최신 리소스팩으로 데이터 폴더의 resourcepack.zip 을 맞춘다.
     * (예전 버전의 팩이 남아 있으면 새 HUD 글리프가 없어 네모 글자로 보이는 문제 방지)
     * 직접 수정한 팩을 쓰려면 config: resourcepack.use-custom-file: true
     */
    private void syncBundledPack() {
        boolean custom = plugin.getConfig().getBoolean("resourcepack.use-custom-file", false);
        if (custom && packFile.exists()) return;
        try (java.io.InputStream in = plugin.getResource("resourcepack.zip")) {
            if (in == null) return;
            byte[] bundled = in.readAllBytes();
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] want = md.digest(bundled);
            if (packFile.exists() && java.util.Arrays.equals(want, MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(packFile.toPath())))) return;
            packFile.getParentFile().mkdirs();
            Files.write(packFile.toPath(), bundled);
            plugin.getLogger().info("리소스팩을 플러그인에 내장된 최신 버전으로 교체했습니다. (" + bundled.length / 1024 + " KB)");
        } catch (Exception e) {
            plugin.getLogger().warning("내장 리소스팩 갱신 실패: " + e.getMessage());
        }
    }

    private String externalUrl() {
        return normalize(plugin.getConfig().getString("resourcepack.url", "").trim());
    }

    /** GitHub 파일 페이지 주소(blob)를 직접 다운로드 주소(raw)로 바꾼다 */
    static String normalize(String u) {
        var m = java.util.regex.Pattern.compile("^https?://github\\.com/([^/]+)/([^/]+)/(?:blob|raw)/(.+)$").matcher(u);
        if (m.matches()) return "https://raw.githubusercontent.com/" + m.group(1) + "/" + m.group(2) + "/" + m.group(3).replaceFirst("\\?raw=true$", "");
        return u;
    }

    private volatile byte[] externalHash;
    private volatile String externalHashHex = "";

    /** 외부 팩을 내려받아 SHA-1 을 직접 계산 (config 의 sha1 을 따로 적지 않아도 됨) */
    private void fetchExternalHash() {
        fetchExternalHash(null);
    }

    private volatile long externalFetchedAt;
    private volatile boolean fetching;

    /**
     * 외부 팩의 SHA-1 을 다시 계산한다 (끝나면 done 을 메인 스레드에서 실행).
     * GitHub 의 zip 을 새 버전으로 바꿔도 서버가 예전 해시를 보내면 클라이언트가 "다운로드 실패" 로 거절하므로,
     * 주기적으로 · 접속할 때 · 실패했을 때 다시 확인한다. 캐시(CDN)를 피하려고 주소 뒤에 시각을 붙인다.
     */
    private void fetchExternalHash(Runnable done) {
        String u = externalUrl();
        if (fetching) {   // 이미 확인 중이면 끝난 뒤 실행
            if (done != null) Bukkit.getScheduler().runTaskLater(plugin, done, 60L);
            return;
        }
        fetching = true;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String bust = u + (u.contains("?") ? "&" : "?") + "t=" + System.currentTimeMillis();
                java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(bust).openConnection();
                c.setUseCaches(false);
                c.setInstanceFollowRedirects(true);
                c.setConnectTimeout(8000);
                c.setReadTimeout(20000);
                int code = c.getResponseCode();
                if (code != 200) {
                    plugin.getLogger().warning("외부 리소스팩 주소 응답 " + code + ": " + u);
                    return;
                }
                byte[] data;
                try (var in = c.getInputStream()) {
                    data = in.readAllBytes();
                }
                byte[] h = MessageDigest.getInstance("SHA-1").digest(data);
                StringBuilder sb = new StringBuilder();
                for (byte b : h) sb.append(String.format("%02x", b));
                if (externalHash != null && !java.util.Arrays.equals(externalHash, h))
                    plugin.getLogger().info("외부 리소스팩이 바뀐 것을 감지해 새 해시로 갱신했습니다. (" + externalHashHex + " → " + sb + ")");
                externalHash = h;
                externalHashHex = sb.toString();
                externalFetchedAt = System.currentTimeMillis();
                String cfg = plugin.getConfig().getString("resourcepack.sha1", "").trim();
                plugin.getLogger().info("외부 리소스팩 확인: " + u + " (" + data.length / 1024 + " KB, SHA-1 " + externalHashHex + ")");
                if (cfg.length() == 40 && !cfg.equalsIgnoreCase(externalHashHex))
                    plugin.getLogger().warning("config 의 resourcepack.sha1 이 실제 파일과 달라 실제 값을 사용합니다.");
                if (hash != null && !java.util.Arrays.equals(hash, h))
                    plugin.getLogger().info("참고: 외부 팩이 플러그인에 내장된 팩과 다릅니다. (플러그인을 업데이트했다면 GitHub 의 zip 도 새 파일로 교체하세요: dist/RpgCraft-ResourcePack.zip)");
            } catch (Exception e) {
                plugin.getLogger().warning("외부 리소스팩을 내려받지 못했습니다: " + u + " (" + e.getMessage() + ")");
            } finally {
                fetching = false;
                if (done != null) Bukkit.getScheduler().runTask(plugin, done);
            }
        });
    }

    public String url() {
        return url(null);
    }

    /**
     * 플레이어가 서버에 접속할 때 입력한 주소(도메인/IP)를 그대로 팩 주소로 사용한다.
     * → 공인 IP, 내부망 IP, 도메인 어느 쪽으로 접속해도 리소스팩을 받을 수 있다. (host: auto)
     */
    public String url(Player p) {
        String ext = externalUrl();
        if (!ext.isEmpty()) {   // 해시마다 다른 주소 → CDN · 클라이언트가 예전 파일을 주지 않도록
            String hx = externalHashHex;
            return hx.length() >= 8 && plugin.getConfig().getBoolean("resourcepack.url-version", true) ? ext + (ext.contains("?") ? "&" : "?") + "v=" + hx.substring(0, 8) : ext;
        }
        String host = plugin.getConfig().getString("resourcepack.self-host.host", "auto");
        if (p != null && (host.equalsIgnoreCase("auto") || host.startsWith("127.") || host.equalsIgnoreCase("localhost"))) {
            String vh = virtualHost(p);
            if (vh != null) host = vh;
        }
        if (host.equalsIgnoreCase("auto")) host = publicIp != null ? publicIp : "127.0.0.1";
        int port = plugin.getConfig().getInt("resourcepack.self-host.port", 8765);
        return "http://" + host + ":" + port + "/RpgCraft.zip?v=" + (hashHex.length() >= 8 ? hashHex.substring(0, 8) : "0");
    }

    private volatile String publicIp;

    /** 자동 감지한 공인 IP (못 찾았으면 null) */
    public String publicIp() { return publicIp; }

    /** Player#getVirtualHost (Spigot/Paper) → 접속에 쓴 주소 */
    private static String virtualHost(Player p) {
        try {
            Object a = p.getClass().getMethod("getVirtualHost").invoke(p);
            if (a instanceof InetSocketAddress isa) {
                String h = isa.getHostString();
                if (h != null && !h.isBlank()) return h.endsWith(".") ? h.substring(0, h.length() - 1) : h;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private void detectPublicIp() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (var in = new java.net.URL("https://api.ipify.org").openStream()) {
                publicIp = new String(in.readAllBytes()).trim();
                plugin.getLogger().info("공인 IP 감지: " + publicIp + " (접속 주소를 알 수 없는 경우에 사용)");
            } catch (Exception ignored) {
            }
        });
    }

    private byte[] hashFor() {
        if (externalUrl().isEmpty()) return hash;
        if (externalHash != null) return externalHash;
        String hex = plugin.getConfig().getString("resourcepack.sha1", "").trim();
        if (hex.length() != 40) return null;
        byte[] out = new byte[20];
        for (int i = 0; i < 20; i++) out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        return out;
    }

    public String hashHex() {
        if (!externalUrl().isEmpty() && !externalHashHex.isEmpty()) return externalHashHex + " (외부)";
        return hashHex;
    }

    // ------------------------------------------------------------------ 내장 웹서버
    private void startServer() {
        if (!packFile.exists()) {
            plugin.getLogger().warning("resourcepack.zip 이 없어 내장 웹서버를 시작하지 않습니다.");
            return;
        }
        int port = plugin.getConfig().getInt("resourcepack.self-host.port", 8765);
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", ex -> {
                try {
                    if (!ex.getRequestURI().getPath().endsWith(".zip")) {
                        ex.sendResponseHeaders(404, -1);
                        return;
                    }
                    byte[] data = Files.readAllBytes(packFile.toPath());
                    ex.getResponseHeaders().add("Content-Type", "application/zip");
                    ex.sendResponseHeaders(200, data.length);
                    try (OutputStream os = ex.getResponseBody()) {
                        os.write(data);
                    }
                } finally {
                    ex.close();
                }
            });
            server.setExecutor(Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r, "RpgCraft-ResourcePack");
                t.setDaemon(true);
                return t;
            }));
            server.start();
            plugin.getLogger().info("리소스팩 웹서버 시작: 포트 " + port + " (플레이어에게는 접속한 주소:" + port + " 로 전송, 외부 접속을 위해 TCP " + port + " 포트 개방 필요)");
            if (plugin.getConfig().getString("resourcepack.self-host.host", "127.0.0.1").startsWith("127."))
                plugin.getLogger().warning("resourcepack.self-host.host 가 127.0.0.1 입니다. 외부 플레이어는 서버 공인 IP/도메인으로 바꿔야 받을 수 있습니다.");
        } catch (IOException e) {
            plugin.getLogger().warning("리소스팩 웹서버 시작 실패(포트 " + port + "): " + e.getMessage());
        }
    }

    private void stopServer() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    public void shutdown() {
        stopServer();
    }

    // ------------------------------------------------------------------ 전송
    public void send(Player p) {
        if (!enabled()) return;
        String prompt = Text.c(plugin.getConfig().getString("resourcepack.prompt", "&6RpgCraft &f전용 리소스팩을 적용합니다."));
        byte[] h = hashFor();
        sentUrl.put(p.getUniqueId(), url(p));
        if (h != null) p.setResourcePack(url(p), h, prompt, required());
        else p.setResourcePack(url(p));
    }

    /** Paper 의 Player#getProtocolVersion (없으면 -1) */
    public static int protocol(Player p) {
        try {
            return (int) p.getClass().getMethod("getProtocolVersion").invoke(p);
        } catch (Throwable t) {
            return -1;
        }
    }

    private final java.util.Map<UUID, String> sentUrl = new java.util.concurrent.ConcurrentHashMap<>();

    public String sentUrl(Player p) {
        return sentUrl.get(p.getUniqueId());
    }

    /** 서버 스스로 팩 주소에 접속해 보는 진단 (비동기) */
    public void selfTest(org.bukkit.command.CommandSender s) {
        int port = plugin.getConfig().getInt("resourcepack.self-host.port", 8765);
        java.util.List<String> urls = new java.util.ArrayList<>();
        if (!externalUrl().isEmpty()) urls.add(externalUrl());
        else {
            urls.add("http://127.0.0.1:" + port + "/RpgCraft.zip");
            if (publicIp != null) urls.add("http://" + publicIp + ":" + port + "/RpgCraft.zip");
            for (Player p : Bukkit.getOnlinePlayers()) { String u = sentUrl.get(p.getUniqueId()); if (u != null && !urls.contains(u)) urls.add(u); }
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            for (String u : urls) {
                String r;
                try {
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(u).openConnection();
                    c.setConnectTimeout(4000);
                    c.setReadTimeout(4000);
                    r = c.getResponseCode() == 200 ? "&a접속 성공 (" + c.getContentLengthLong() / 1024 + " KB)" : "&c응답 코드 " + c.getResponseCode();
                    c.disconnect();
                } catch (Exception e) {
                    r = "&c접속 실패 (" + e.getClass().getSimpleName() + ")";
                }
                String line = r;
                Bukkit.getScheduler().runTask(plugin, () -> Text.msg(s, "&f" + u + " → " + line));
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                Text.msg(s, "&7127.0.0.1 만 성공하면: 웹서버는 정상이지만 포트 " + port + " 가 외부에 열려 있지 않습니다.");
                Text.msg(s, "&7공인 IP 가 실패해도 공유기 구조상 서버 자신은 못 들어가는 경우가 있으니, 다른 PC 브라우저로도 확인해 보세요.");
            });
        });
    }

    public boolean hasPack(Player p) {
        if (plugin.getConfig().getBoolean("resourcepack.assume-installed", false)) return true;
        return loaded.contains(p.getUniqueId());
    }

    public int loadedCount() {
        return loaded.size();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!enabled()) return;
        Player p = e.getPlayer();
        retried.remove(p.getUniqueId());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            // 외부 팩: 마지막 확인이 오래됐으면 해시를 새로 확인한 뒤 보냄 (GitHub 파일 교체 대응)
            if (!externalUrl().isEmpty() && System.currentTimeMillis() - externalFetchedAt > 120_000)
                fetchExternalHash(() -> { if (p.isOnline()) send(p); });
            else send(p);
        }, plugin.getConfig().getLong("resourcepack.send-delay-ticks", 20));
    }

    /** /리소스팩: 해시를 새로 확인한 뒤 다시 보냄 */
    public void resend(Player p) {
        retried.remove(p.getUniqueId());
        if (!externalUrl().isEmpty()) fetchExternalHash(() -> { if (p.isOnline()) send(p); });
        else send(p);
    }

    /** 이번 접속에서 자동 재시도를 한 플레이어 (무한 반복 방지) */
    private final Set<UUID> retried = ConcurrentHashMap.newKeySet();

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        loaded.remove(e.getPlayer().getUniqueId());
        retried.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent e) {
        Player p = e.getPlayer();
        String st = e.getStatus().name();
        plugin.getLogger().info("[리소스팩] " + p.getName() + " → " + st + " (클라이언트 프로토콜 " + protocol(p) + ")");
        switch (st) {
            case "SUCCESSFULLY_LOADED" -> {
                loaded.add(p.getUniqueId());
                plugin.hud().onPackLoaded(p);
                if (plugin.getConfig().getBoolean("resourcepack.welcome-title", true))
                    p.sendTitle(ChatColor.WHITE + HudFont.LOGO, Text.c("&7대규모 RPG · PvP 서버에 오신 것을 환영합니다"), 10, 50, 15);
            }
            case "DECLINED" -> {
                if (required() && plugin.getConfig().getBoolean("resourcepack.kick-on-decline", true))
                    Bukkit.getScheduler().runTask(plugin, () -> p.kickPlayer(Text.c("&c이 서버는 전용 리소스팩이 필요합니다.\n&7서버 목록 > 편집 > 서버 리소스팩: 사용")));
                else Text.msg(p, "&7리소스팩을 거절했습니다. 아이템/메뉴가 기본 모습으로 보입니다. &e/메뉴 &7는 그대로 사용 가능");
            }
            case "FAILED_DOWNLOAD", "INVALID_URL" -> {
                loaded.remove(p.getUniqueId());
                if (retried.add(p.getUniqueId())) {   // 한 번은 자동으로: 해시를 새로 확인하고 다시 보냄 (팩 파일이 바뀌었거나 일시적 오류)
                    Text.msg(p, "&e리소스팩 적용에 실패해 다시 시도합니다...");
                    if (!externalUrl().isEmpty()) fetchExternalHash(() -> { if (p.isOnline()) send(p); });
                    else Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline()) send(p); }, 60L);
                    return;
                }
                String u = sentUrl.getOrDefault(p.getUniqueId(), url(p));
                Text.msg(p, "&c리소스팩을 내려받지 못했습니다. 서버 관리자에게 알려주세요. &7(" + u + ")");
                plugin.getLogger().warning("리소스팩 다운로드 실패: " + p.getName() + " 이(가) " + u + " 에 접속하지 못했습니다.");
                plugin.getLogger().warning(" → 서버 PC/호스팅의 방화벽·공유기에서 TCP " + plugin.getConfig().getInt("resourcepack.self-host.port", 8765)
                        + " 포트를 열었는지 확인하세요. 다른 PC 브라우저에서 위 주소가 열리면 정상입니다.");
                plugin.getLogger().warning(" → 포트를 열 수 없는 호스팅이라면 리소스팩 zip 을 외부(깃허브 릴리스, mc-packs.net 등)에 올리고 config 의 resourcepack.url 에 주소를 넣으세요.");
                plugin.getLogger().warning(" → /rpg관리 pack test 로 서버에서 직접 접속 테스트를 할 수 있습니다.");
            }            case "FAILED_RELOAD", "DISCARDED" -> {
                loaded.remove(p.getUniqueId());
                if (retried.add(p.getUniqueId())) {
                    Text.msg(p, "&e리소스팩 적용에 실패해 다시 시도합니다...");
                    Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline()) send(p); }, 60L);
                    return;
                }
                Text.msg(p, "&c리소스팩 적용에 실패해 기본 화면으로 표시합니다. &7(설정 > 리소스팩에서 서버 팩을 지운 뒤 다시 접속해 보세요)");
            }
            default -> { }
        }
    }

    // ------------------------------------------------------------------ GUI 배경
    /** 픽셀 이동용 공백 글리프 (리소스팩 폰트의 space provider) */
    public static String shift(int px) {
        StringBuilder sb = new StringBuilder();
        int base = px < 0 ? 0xF801 : 0xF821;
        int v = Math.abs(px);
        for (int i = 8; i >= 0; i--) {
            int step = 1 << i;
            while (v >= step) {
                sb.append((char) (base + i));
                v -= step;
            }
        }
        return sb.toString();
    }

    private static char glyph(int rows, String bg) {
        if (bg != null) {
            switch (bg) {
                case "main": return '\uE000';
                case "shop": return '\uE001';
                case "enhance": return '\uE002';
                case "rune": return '\uE003';
                case "stat": return '\uE004';
                case "potion": return '\uE005';
                case "quest": return '\uE006';
                case "settings": return '\uE007';
                case "spirit": return '\uE008';
                default: break;
            }
        }
        return (char) (0xE010 + Math.max(1, Math.min(6, rows)));
    }

    /** GUI 제목에 배경 이미지 글리프를 씌운다. 오버레이를 쓰지 않으면 원래 제목 그대로 */
    /** 플레이어별: 리소스팩을 적용한 사람에게만 배경을 씌움 (gui-overlay: false 면 끔) */
    public static String decorateFor(Player p, int rows, String bg, String title) {
        RpgCraft pl = RpgCraft.get();
        if (pl == null || pl.pack() == null) return title;
        String mode = pl.getConfig().getString("resourcepack.gui-overlay", "auto");
        if ("false".equalsIgnoreCase(mode)) return title;
        if (!"true".equalsIgnoreCase(mode) && !pl.pack().hasPack(p)) return title;
        return build(pl, rows, bg, title);
    }

    public static String decorate(int rows, String bg, String title) {
        RpgCraft pl = RpgCraft.get();
        if (pl == null || pl.pack() == null || !pl.pack().overlay()) return title;
        return build(pl, rows, bg, title);
    }

    private static String build(RpgCraft pl, int rows, String bg, String title) {
        String color = Text.c(pl.getConfig().getString("resourcepack.title-color", "&f"));
        return ChatColor.WHITE + shift(-8) + glyph(rows, bg) + shift(8 - (GUI_WIDTH + 1)) + color + ChatColor.stripColor(title);
    }

    /** 오버레이 사용 시 투명 모델이 적용되는 칸 채우기 판유리 */
    public static ItemStack filler(Material m) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(" ");
        meta.getPersistentDataContainer().set(kr.rpgcraft.Keys.FILLER, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        RpgCraft pl = RpgCraft.get();
        // 팩 적용자에게는 투명(배경 그림이 보임), 팩이 없으면 그냥 판유리로 보이므로 항상 지정
        if (pl == null || !"false".equalsIgnoreCase(pl.getConfig().getString("resourcepack.gui-overlay", "auto"))) meta.setCustomModelData(1);
        it.setItemMeta(meta);
        return it;
    }
}
