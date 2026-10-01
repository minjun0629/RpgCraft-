package kr.rpgcraft.command;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.util.Text;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * v5.10.45 /명령어 [쪽 | 검색어] — plugin.yml 의 모든 명령어를 한글 이름 · 설명과 함께 보여 줌 (쓸 수 있는 것만).
 * 줄을 클릭하면 채팅창에 그 명령어가 입력됨. 새 명령어를 추가해도 자동으로 목록에 나옴.
 */
public class CommandList implements CommandExecutor {
    private static final int PER_PAGE = 10;
    private final RpgCraft plugin;

    public CommandList(RpgCraft plugin) {
        this.plugin = plugin;
    }

    private record Entry(String main, String desc, List<String> aliases) {}

    private org.bukkit.configuration.ConfigurationSection cmds;

    private org.bukkit.configuration.ConfigurationSection commands() {
        if (cmds == null) {
            try (java.io.InputStream in = plugin.getResource("plugin.yml")) {
                org.bukkit.configuration.file.YamlConfiguration y = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                        new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
                cmds = y.getConfigurationSection("commands");
            } catch (Exception ex) {
                plugin.getLogger().warning("plugin.yml 을 읽지 못했습니다: " + ex.getMessage());
            }
        }
        return cmds;
    }

    private List<Entry> entries(CommandSender s) {
        List<Entry> out = new ArrayList<>();
        org.bukkit.configuration.ConfigurationSection all = commands();
        if (all == null) return out;
        for (String key : all.getKeys(false)) {
            String perm = all.getString(key + ".permission");
            if (perm != null && !s.hasPermission(perm)) continue;
            if (all.getString(key + ".description", "").contains("관리자") && !s.hasPermission("rpgcraft.admin")) continue;   // 관리자 명령어는 관리자에게만
            List<String> aliases = new ArrayList<>(all.getStringList(key + ".aliases"));
            if (aliases.isEmpty() && all.isString(key + ".aliases")) aliases.add(all.getString(key + ".aliases"));
            String main = key;
            for (String x : aliases) if (!x.isEmpty() && x.charAt(0) >= 0xAC00 && x.charAt(0) <= 0xD7A3) { main = x; break; }   // 한글 이름을 대표로
            out.add(new Entry(main, all.getString(key + ".description", ""), aliases));
        }
        out.sort(Comparator.comparing(Entry::main));
        return out;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        List<Entry> all = entries(s);
        int page = 1;
        String query = null;
        if (a.length > 0) {
            try { page = Integer.parseInt(a[0]); } catch (NumberFormatException ex) { query = String.join(" ", a); }
        }
        if (query != null) {
            String q = query;
            all.removeIf(e -> !e.main().contains(q) && !e.desc().contains(q) && e.aliases().stream().noneMatch(x -> x.contains(q)));
        }
        int pages = Math.max(1, (all.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.max(1, Math.min(pages, page));
        s.sendMessage(Text.c("&6&l━━━ 명령어 목록 " + (query != null ? "&7(검색: " + query + ") " : "") + "&e" + page + "&7/" + pages + " &6&l━━━"));
        if (all.isEmpty()) s.sendMessage(Text.c("&7찾는 명령어가 없습니다."));
        for (int i = (page - 1) * PER_PAGE; i < all.size() && i < page * PER_PAGE; i++) {
            Entry e = all.get(i);
            String others = String.join(", ", e.aliases().stream().filter(x -> !x.equals(e.main())).toList());
            String line = "&e/" + e.main() + " &7- &f" + e.desc();
            if (s instanceof Player p) {
                TextComponent tc = new TextComponent(TextComponent.fromLegacyText(Text.c(line)));
                tc.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/" + e.main() + " "));
                tc.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new net.md_5.bungee.api.chat.hover.content.Text(Text.c("&e클릭하면 채팅창에 입력" + (others.isEmpty() ? "" : "\n&7다른 이름: /" + others.replace(", ", ", /"))))));
                p.spigot().sendMessage(tc);
            } else s.sendMessage(Text.c(line));
        }
        if (s instanceof Player p && pages > 1) {
            ComponentBuilder nav = new ComponentBuilder("");
            if (page > 1) {
                TextComponent prev = new TextComponent(Text.c("&a[◀ 이전] "));
                prev.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/명령어 " + (page - 1)));
                nav.append(prev);
            }
            nav.append(new TextComponent(Text.c("&7" + page + " / " + pages + " ")), ComponentBuilder.FormatRetention.NONE);
            if (page < pages) {
                TextComponent next = new TextComponent(Text.c("&a[다음 ▶]"));
                next.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/명령어 " + (page + 1)));
                nav.append(next, ComponentBuilder.FormatRetention.NONE);
            }
            p.spigot().sendMessage(nav.create());
        }
        s.sendMessage(Text.c("&8/명령어 <쪽> · /명령어 <검색어> (예: /명령어 상점)"));
        return true;
    }
}
