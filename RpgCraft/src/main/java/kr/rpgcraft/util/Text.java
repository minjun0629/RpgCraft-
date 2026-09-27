package kr.rpgcraft.util;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class Text {
    public static final String PREFIX = c("&6[RpgCraft] &f");

    private Text() {}

    public static String c(String s) {
        return s == null ? "" : ChatColor.translateAlternateColorCodes('&', s);
    }

    public static List<String> c(List<String> list) {
        List<String> out = new ArrayList<>();
        for (String s : list) out.add(c(s));
        return out;
    }

    public static String strip(String s) {
        return ChatColor.stripColor(c(s));
    }

    // ------------------------------------------------------------------ 닉네임 (/닉네임): 화면에 보이는 이름은 모두 이것으로
    /** 표시 이름: /닉네임 으로 정한 이름, 없으면 계정 이름 */
    public static String name(org.bukkit.OfflinePlayer p) {
        if (p == null) return "?";
        var pl = kr.rpgcraft.RpgCraft.get();
        if (pl != null && pl.data() != null) {
            var d = pl.data().get(p.getUniqueId());
            if (d != null && d.nick != null && !d.nick.isBlank()) return d.nick;
            if (p.getName() == null && d != null && d.name != null) return d.name;
        }
        return p.getName() == null ? "?" : p.getName();
    }

    public static String name(java.util.UUID id) {
        return id == null ? "?" : name(org.bukkit.Bukkit.getOfflinePlayer(id));
    }

    /** 접속 중인 플레이어 찾기: 계정 이름 또는 닉네임 (대소문자 무시) */
    public static Player player(String nameOrNick) {
        if (nameOrNick == null) return null;
        Player p = org.bukkit.Bukkit.getPlayerExact(nameOrNick);
        if (p != null) return p;
        for (Player op : org.bukkit.Bukkit.getOnlinePlayers()) if (name(op).equalsIgnoreCase(nameOrNick) || op.getName().equalsIgnoreCase(nameOrNick)) return op;
        return null;
    }

    /** Tab 자동완성용: 접속자 표시 이름 (닉네임이 있으면 닉네임) */
    public static List<String> onlineNames(String prefix, CommandSender except) {
        List<String> out = new ArrayList<>();
        String pre = prefix == null ? "" : prefix.toLowerCase(java.util.Locale.ROOT);
        for (Player op : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (op.equals(except)) continue;
            String n = name(op);
            if (n.toLowerCase(java.util.Locale.ROOT).startsWith(pre)) out.add(n);
            else if (op.getName().toLowerCase(java.util.Locale.ROOT).startsWith(pre)) out.add(op.getName());
        }
        return out;
    }

    public static void msg(CommandSender s, String m) {
        s.sendMessage(PREFIX + c(m));
    }

    /** 전체 공지 (설정에서 공지를 끈 플레이어 제외) */
    public static void announce(String msg) {
        org.bukkit.Bukkit.getConsoleSender().sendMessage(msg);
        kr.rpgcraft.RpgCraft pl = kr.rpgcraft.RpgCraft.get();
        for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (pl == null || kr.rpgcraft.data.Setting.ANNOUNCE.get(pl.data().get(p))) p.sendMessage(msg);
        }
    }

    public static void actionBar(Player p, String s) {
        kr.rpgcraft.RpgCraft pl = kr.rpgcraft.RpgCraft.get();
        if (pl != null && pl.hud() != null && pl.hud().notice(p, c(s))) return; // 리소스팩 HUD 사용 중: HUD 위에 알림
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(c(s)));
    }

    public static String money(long v) {
        return String.format("%,d원", v);
    }

    public static String num(double v) {
        return String.format("%,.0f", v);
    }

    public static String signed(double v, boolean pct) {
        String body = pct ? String.format("%.1f%%", Math.abs(v)) : String.format("%,.0f", Math.abs(v));
        return (v >= 0 ? "+" : "-") + body;
    }

    public static String bar(double ratio, int len, String fullColor, String emptyColor) {
        ratio = Math.max(0, Math.min(1, ratio));
        int full = (int) Math.round(ratio * len);
        StringBuilder sb = new StringBuilder(c(fullColor));
        for (int i = 0; i < len; i++) {
            if (i == full) sb.append(c(emptyColor));
            sb.append('|');
        }
        return sb.toString();
    }

    public static String time(long seconds) {
        seconds = Math.max(0, seconds);
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    public static long parseLong(String s, long def) {
        try {
            return Long.parseLong(s.replace(",", ""));
        } catch (Exception e) {
            return def;
        }
    }

    public static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return def;
        }
    }

    public static double parseDouble(String s, double def) {
        try {
            return Double.parseDouble(s);
        } catch (Exception e) {
            return def;
        }
    }
}
