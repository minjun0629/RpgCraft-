package kr.rpgcraft.command;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.util.Text;
import kr.rpgcraft.war.Castle;
import kr.rpgcraft.war.WarManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class WarCommand implements CommandExecutor, TabCompleter {
    private final RpgCraft plugin;

    public WarCommand(RpgCraft plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (a.length == 0 || a[0].equals("목록") || a[0].equals("list")) {
            Text.msg(s, "&c공성전 성 목록");
            for (Castle c : plugin.wars().castles()) {
                WarManager.War w = plugin.wars().warOf(c);
                s.sendMessage(Text.c(" &e" + c.id + " &f" + c.name + " &7| 소유: &b" + (c.owner == null ? "없음" : c.owner)
                        + " &7| 성벽 " + c.walls.size() + "개" + (w == null ? "" : w.started ? " &c[전쟁 중]" : " &6[준비 중]")));
            }
            Text.msg(s, "&7/전쟁 선포 <성ID> - 전쟁권 1장 소모 (길드장)");
            return true;
        }
        if ((a[0].equals("선포") || a[0].equals("declare")) && s instanceof Player p) {
            if (a.length < 2) {
                Text.msg(p, "&c/전쟁 선포 <성ID>");
                return true;
            }
            plugin.wars().declare(p, a[1]);
            return true;
        }
        if (a[0].equals("정보") && a.length >= 2) {
            Castle c = plugin.wars().castle(a[1]);
            if (c == null) {
                Text.msg(s, "&c없는 성입니다.");
                return true;
            }
            Text.msg(s, "&e" + c.name + " &7(" + c.id + ") 소유: &b" + (c.owner == null ? "없음" : c.owner));
            for (Castle.Wall w : c.walls)
                s.sendMessage(Text.c(" &f성벽 " + w.id + " &c❤ " + Text.num(w.hp) + "/" + Text.num(w.maxHp) + (w.broken ? " &8(붕괴)" : "")));
            return true;
        }
        Text.msg(s, "&e/전쟁 목록 &7| &e/전쟁 선포 <성ID> &7| &e/전쟁 정보 <성ID>");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (a.length == 1) return List.of("목록", "선포", "정보");
        List<String> ids = new ArrayList<>();
        for (Castle ca : plugin.wars().castles()) ids.add(ca.id);
        return ids;
    }
}
