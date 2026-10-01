package kr.rpgcraft.world;

import kr.rpgcraft.Keys;
import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.data.PlayerData;
import kr.rpgcraft.data.Setting;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 상황별 배경음악 (v5.5.0)
 *  마을 · 로비 → 필드 → 전투 → 보스 → 던전 · 신전, 상황이 바뀌면 곡을 바꾸고 곡이 끝나면 같은 상황의 다른 곡을 튼다.
 *
 * 곡은 리소스팩에 들어 있다 (tools/bgm/&lt;상황&gt;/*.ogg → rpgcraft:bgm.&lt;상황&gt;.&lt;번호&gt;).
 * 팩을 만들 때 곡 길이가 assets/rpgcraft/bgm_tracks.txt 에 기록되고, 서버는 그 목록을 읽어 반복 시점을 계산한다.
 * 곡이 없는 상황은 config 의 bgm.fallback.* (게임 내장 음악) 을 쓰고, 그것도 비어 있으면 아무것도 틀지 않는다.
 * 소리는 "음반" 카테고리로 플레이어에게 붙여서 틀기 때문에 움직여도 작아지지 않는다.
 */
public class BgmManager implements Listener {
    public static final String[] SITUATIONS = {"town", "field", "battle", "boss", "dungeon"};

    private record Track(String sound, int seconds) {}
    private record Playing(String situation, String sound, long until) {}

    private final RpgCraft plugin;
    private final Map<String, List<Track>> tracks = new HashMap<>();
    private final Map<UUID, Playing> now = new HashMap<>();
    /** 전투 음악이 켜진 뒤 유지되는 동안 (자꾸 필드 ↔ 전투로 바뀌지 않게) */
    private final Map<UUID, Long> battleUntil = new HashMap<>();

    public BgmManager(RpgCraft plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskLater(plugin, this::loadTracks, 20L);   // 팩 파일이 데이터 폴더에 풀린 뒤
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 100L, 40L);
    }

    // ------------------------------------------------------------------ 곡 목록
    /** 팩(데이터 폴더의 resourcepack.zip)에 기록된 곡 목록 + config 의 bgm.tracks.* 를 합친다 */
    public void loadTracks() {
        tracks.clear();
        File pack = new File(plugin.getDataFolder(), "resourcepack.zip");
        if (pack.exists()) {
            try (ZipFile z = new ZipFile(pack)) {
                ZipEntry e = z.getEntry("assets/rpgcraft/bgm_tracks.txt");   // 한 줄에 "상황 사운드이름 길이초"
                if (e != null) try (InputStream in = z.getInputStream(e)) {
                    for (String line : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\n")) {
                        String[] f = line.trim().split("\\s+");
                        if (f.length == 3 && !f[0].startsWith("#")) try { add(f[0], f[1], Integer.parseInt(f[2])); } catch (NumberFormatException ignored) { }
                    }
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("배경음악 목록을 읽지 못했습니다: " + ex.getMessage());
            }
        }
        // 직접 추가: bgm.tracks.field: ["mypack:music.forest:180", ...]  (사운드 이름:길이초)
        ConfigurationSection extra = plugin.getConfig().getConfigurationSection("bgm.tracks");
        if (extra != null) for (String sit : extra.getKeys(false))
            for (String s : extra.getStringList(sit)) {
                int i = s.lastIndexOf(':');
                try { add(sit, s.substring(0, i), Integer.parseInt(s.substring(i + 1).trim())); } catch (Exception ignored) { }
            }
        int n = tracks.values().stream().mapToInt(List::size).sum();
        if (n > 0) plugin.getLogger().info("배경음악 " + n + "곡 불러옴 " + counts());
    }

    private void add(String sit, String sound, int seconds) {
        if (seconds <= 0) return;
        tracks.computeIfAbsent(sit, k -> new ArrayList<>()).add(new Track(sound, seconds));
    }

    public String counts() {
        StringBuilder sb = new StringBuilder("(");
        for (String sit : SITUATIONS) sb.append(sb.length() > 1 ? " · " : "").append(label(sit)).append(" ").append(tracks.getOrDefault(sit, List.of()).size());
        return sb.append(")").toString();
    }

    public static String label(String sit) {
        return switch (sit) {
            case "town" -> "마을";
            case "field" -> "필드";
            case "battle" -> "전투";
            case "boss" -> "보스";
            case "dungeon" -> "던전";
            default -> sit;
        };
    }

    // ------------------------------------------------------------------ 상황 판단
    public String situation(Player p) {
        Location l = p.getLocation();
        for (Entity e : p.getNearbyEntities(40, 20, 40))
            if (e instanceof LivingEntity && !e.isDead() && e.getPersistentDataContainer().has(Keys.BOSS, PersistentDataType.STRING)) return "boss";
        if (plugin.dungeons() != null && plugin.dungeons().runOf(p) != null) return "dungeon";
        if (plugin.tower() != null && plugin.tower().inRun(p)) return "dungeon";
        if (plugin.getConfig().getStringList("bgm.dungeon-worlds").contains(p.getWorld().getName())) return "dungeon";
        long t = System.currentTimeMillis();
        if (plugin.events() != null && plugin.events().inWave(p)) return "battle";
        PlayerData d = plugin.data().get(p);
        if (d != null && t - d.lastCombat < 2500 && battleUntil.getOrDefault(p.getUniqueId(), 0L) < t) {
            // 몬스터 여러 마리와 싸울 때만 전투 음악 (한 마리 툭 치는 걸로는 바뀌지 않게)
            int mobs = 0;
            for (Entity e : p.getNearbyEntities(14, 8, 14)) if (e instanceof Monster && !e.isDead()) mobs++;
            if (mobs >= plugin.getConfig().getInt("bgm.battle-min-mobs", 2) || plugin.combat().inPvp(p))
                battleUntil.put(p.getUniqueId(), t + plugin.getConfig().getLong("bgm.battle-hold-seconds", 15) * 1000);
        }
        if (d != null && battleUntil.getOrDefault(p.getUniqueId(), 0L) > t) {
            if (t - d.lastCombat < plugin.getConfig().getLong("bgm.battle-hold-seconds", 15) * 1000) return "battle";
            battleUntil.remove(p.getUniqueId());
        }
        if (plugin.getConfig().getStringList("bgm.town-worlds").contains(p.getWorld().getName())) return "town";
        Location sp = p.getWorld().getSpawnLocation();
        if (Math.hypot(l.getX() - sp.getX(), l.getZ() - sp.getZ()) < plugin.getConfig().getDouble("bgm.town-radius", 150)) return "town";
        return "field";
    }

    // ------------------------------------------------------------------ 재생
    private Track pick(String sit, String avoid) {
        List<Track> list = tracks.get(sit);
        if (list == null || list.isEmpty()) {
            String fb = plugin.getConfig().getString("bgm.fallback." + sit, "");
            if (fb == null || fb.isBlank()) return null;
            return new Track(fb, plugin.getConfig().getInt("bgm.fallback-seconds", 170));
        }
        if (list.size() == 1) return list.get(0);
        Track t;
        do t = list.get(ThreadLocalRandom.current().nextInt(list.size())); while (t.sound().equals(avoid));
        return t;
    }

    private void tick() {
        boolean on = plugin.getConfig().getBoolean("bgm.enabled", true);
        long t = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData d = plugin.data().get(p);
            if (!on || d == null || !Setting.BGM.get(d)) {
                stop(p);
                continue;
            }
            String sit = situation(p);
            Playing cur = now.get(p.getUniqueId());
            if (cur != null && cur.situation().equals(sit) && t < cur.until()) continue;
            Track next = pick(sit, cur == null ? null : cur.sound());
            if (cur != null) p.stopSound(cur.sound(), SoundCategory.RECORDS);
            if (next == null) {   // 이 상황엔 틀 곡이 없음 → 게임 기본 음악에 맡김
                now.remove(p.getUniqueId());
                continue;
            }
            p.stopSound(SoundCategory.MUSIC);   // 게임 기본 음악과 겹치지 않게
            p.playSound(p, next.sound(), SoundCategory.RECORDS, (float) plugin.getConfig().getDouble("bgm.volume", 0.7), 1f);
            now.put(p.getUniqueId(), new Playing(sit, next.sound(), t + next.seconds() * 1000L + 1500));
        }
    }

    /** 지금 나오는 배경음악 끄기 */
    public void stop(Player p) {
        Playing cur = now.remove(p.getUniqueId());
        if (cur != null) p.stopSound(cur.sound(), SoundCategory.RECORDS);
    }

    public String nowPlaying(Player p) {
        Playing cur = now.get(p.getUniqueId());
        return cur == null ? null : label(cur.situation()) + " · " + cur.sound();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        now.remove(e.getPlayer().getUniqueId());
        battleUntil.remove(e.getPlayer().getUniqueId());
    }
}
