package kr.rpgcraft.feature;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.data.PlayerData;
import kr.rpgcraft.passive.Passive;
import kr.rpgcraft.util.Text;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

/** 레벨 / 경험치. 레벨당 스탯 +5, 체력 +200 (나무위키 4R 기준) */
public class LevelService {
    private final RpgCraft plugin;

    public LevelService(RpgCraft plugin) {
        this.plugin = plugin;
    }

    public double need(int level) {
        FileConfiguration c = plugin.getConfig();
        return (c.getDouble("player.exp.base", 100) * Math.pow(level, c.getDouble("player.exp.power", 1.4)) + c.getDouble("player.exp.flat", 50))
                * c.getDouble("player.exp.need-mult", 0.5);   // 레벨업 빠르게
    }

    public int maxLevel() {
        return plugin.getConfig().getInt("player.max-level", 150);
    }

    /** 환생 1회마다 최대 레벨 +300 */
    public int maxLevel(PlayerData d) {
        return maxLevel() + plugin.getConfig().getInt("rebirth.max-level-step", 300) * (int) d.counter("rebirth");
    }

    public void addExp(Player p, double amount) {
        PlayerData d = plugin.data().get(p);
        if (d.level >= maxLevel(d)) return;
        amount *= (1 + d.stats.expPct / 100) * plugin.getConfig().getDouble("player.exp-gain-mult", 0.8);   // 전체 경험치 -20%
        amount *= weekendMult();   // v5.5.0: 주말 경험치 이벤트
        d.exp += amount;
        if (kr.rpgcraft.data.Setting.EXP_CHAT.get(d) && amount >= 1)   // 설정: 획득 경험치 채팅
            p.sendMessage(Text.c("&7+&a" + Text.num(amount) + " &7경험치 &8(" + String.format("%.1f", Math.min(100, d.exp / Math.max(1, need(d.level)) * 100)) + "%)"));
        int before = d.level;
        while (d.level < maxLevel(d) && d.exp >= need(d.level)) {
            d.exp -= need(d.level);
            d.level++;
            d.statPoints += d.has(Passive.GAMBLER) ? plugin.getConfig().getInt("player.gambler-stat-per-level", 30)
                    : plugin.getConfig().getInt("player.stat-per-level", 5) + plugin.getConfig().getInt("rebirth.stat-points", 2) * (int) d.counter("rebirth");
        }
        if (d.level >= maxLevel(d)) d.exp = 0;
        if (d.level > before) {
            plugin.stats().refresh(p);
            d.hp = d.stats.maxHp;
            Text.actionBar(p, "&6&l▲ 레벨 업! &eLv." + d.level + " &7(스탯 +" + (d.level - before) * plugin.getConfig().getInt("player.stat-per-level", 5) + ")");
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            plugin.visuals().levelUp(p);
        }
    }

    /** 주말(토 · 일, 서버 시간대) 경험치 배율 — 평일은 1 */
    public double weekendMult() {
        var c = plugin.getConfig();
        if (!c.getBoolean("events.weekend-exp.enabled", true)) return 1;
        java.time.DayOfWeek dw = java.time.ZonedDateTime.now(java.time.ZoneId.of(c.getString("events.timezone", "Asia/Seoul"))).getDayOfWeek();
        return dw == java.time.DayOfWeek.SATURDAY || dw == java.time.DayOfWeek.SUNDAY ? c.getDouble("events.weekend-exp.mult", 1.5) : 1;
    }

    public void setLevel(PlayerData d, int level) {
        d.level = Math.max(1, Math.min(maxLevel(), level));
        d.exp = 0;
    }
}
