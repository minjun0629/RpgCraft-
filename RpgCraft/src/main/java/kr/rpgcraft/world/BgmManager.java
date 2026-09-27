package kr.rpgcraft.world;

import kr.rpgcraft.Keys;
import kr.rpgcraft.RpgCraft;
import org.bukkit.Bukkit;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 전투 BGM: 보스 근처 → 웅장한 보스 음악, 웨이브·던전 → 긴박한 전투 음악.
 * 게임 안에 들어 있는 음악(바닐라)을 상황에 맞게 틀고, 상황이 끝나면 멈춘다. (config: bgm.*)
 */
public class BgmManager {
    private record Playing(String sound, long until) {}

    private final RpgCraft plugin;
    private final Map<UUID, Playing> now = new HashMap<>();

    public BgmManager(RpgCraft plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 100L, 60L);
    }

    private String want(Player p) {
        for (Entity e : p.getNearbyEntities(48, 24, 48))
            if (e.getPersistentDataContainer().has(Keys.BOSS, PersistentDataType.STRING)) return plugin.getConfig().getString("bgm.boss", "minecraft:music.dragon");
        if (plugin.dungeons() != null && plugin.dungeons().runOf(p) != null) return plugin.getConfig().getString("bgm.dungeon", "minecraft:music_disc.pigstep");
        if (plugin.events() != null && plugin.events().inWave(p)) return plugin.getConfig().getString("bgm.wave", "minecraft:music_disc.otherside");
        return null;
    }

    private void tick() {
        if (!plugin.getConfig().getBoolean("bgm.enabled", true)) return;
        long t = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            String w = want(p);
            Playing cur = now.get(p.getUniqueId());
            if (w == null) {
                if (cur != null) { p.stopSound(cur.sound(), SoundCategory.RECORDS); now.remove(p.getUniqueId()); }
                continue;
            }
            if (cur != null && cur.sound().equals(w) && t < cur.until()) continue;
            if (cur != null) p.stopSound(cur.sound(), SoundCategory.RECORDS);
            p.stopSound(SoundCategory.MUSIC);
            p.playSound(p.getLocation(), w, SoundCategory.RECORDS, 0.6f, 1f);
            now.put(p.getUniqueId(), new Playing(w, t + plugin.getConfig().getLong("bgm.loop-seconds", 170) * 1000));
        }
    }
}
