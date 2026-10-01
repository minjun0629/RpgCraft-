package kr.rpgcraft.command;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.data.PlayerData;
import kr.rpgcraft.guild.Guild;
import kr.rpgcraft.item.Category;
import kr.rpgcraft.item.ItemData;
import kr.rpgcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class GuildCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBS = List.of("토벌전", "스킬", "생성", "초대", "수락", "거절", "탈퇴", "추방", "정보", "목록", "입금", "출금",
            "창고", "토템", "레벨업", "해산", "위임", "채팅");
    private final RpgCraft plugin;

    public GuildCommand(RpgCraft plugin) {
        this.plugin = plugin;
    }

    private void help(CommandSender s) {
        Text.msg(s, "&e/길드 생성 <이름> &7- 길드 생성");
        Text.msg(s, "&e/길드 초대 <플레이어> &7| &e/길드 수락 &7| &e/길드 거절 &7| &e/길드 탈퇴");
        Text.msg(s, "&e/길드 추방 <플레이어> &7| &e/길드 위임 <플레이어> &7| &e/길드 해산");
        Text.msg(s, "&e/길드 정보 [길드] &7| &e/길드 목록 &7| &e/길드 채팅");
        Text.msg(s, "&e/길드 입금 <금액> &7| &e/길드 출금 <금액> &7| &e/길드 창고 &7| &e/길드 레벨업 &7| &e/길드 스킬");
        Text.msg(s, "&e/길드 토템 &7[설치 | 해제 <번호>] - 손에 든 토템 설치");
        Text.msg(s, "&e/길드 토벌전 &7[1~5 | 기록 <단계>] - 길드 토벌전 (길드장이 시작)");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] a) {
        if (!(sender instanceof Player p)) {
            Text.msg(sender, "플레이어만 사용할 수 있습니다.");
            return true;
        }
        if (a.length == 0) {
            help(p);
            return true;
        }
        var gm = plugin.guilds();
        Guild g = gm.of(p.getUniqueId());
        PlayerData d = plugin.data().get(p);
        switch (a[0]) {
            case "생성", "create" -> {
                if (a.length < 2) { Text.msg(p, "&c/길드 생성 <이름>"); return true; }
                if (g != null) { Text.msg(p, "&c이미 길드에 소속되어 있습니다."); return true; }
                String name = a[1];
                if (!name.matches("[0-9A-Za-z가-힣_]{2,12}")) { Text.msg(p, "&c길드 이름은 한글/영문/숫자 2~12자입니다."); return true; }
                boolean free = p.hasPermission("rpgcraft.guild.create");
                if (!free && !plugin.getConfig().getBoolean("guild.allow-player-create", true)) {
                    Text.msg(p, "&c길드는 운영자가 지정한 길드장만 만들 수 있습니다.");
                    return true;
                }
                long cost = free ? 0 : plugin.getConfig().getLong("guild.create-cost", 1_000_000);
                if (!plugin.economy().has(p, cost)) { Text.msg(p, "&c길드 생성 비용 " + Text.money(cost) + "이 부족합니다."); return true; }
                if (gm.create(p, name) == null) { Text.msg(p, "&c이미 존재하는 길드 이름입니다."); return true; }
                plugin.economy().take(p, cost);
                kr.rpgcraft.util.Text.announce(Text.PREFIX + Text.c("&b" + Text.name(p) + "&f님이 길드 &b[" + name + "]&f을(를) 창설했습니다!"));
            }
            case "초대", "invite" -> {
                if (!leader(p, g)) return true;
                if (a.length < 2) { Text.msg(p, "&c/길드 초대 <플레이어>"); return true; }
                Player t = Text.player(a[1]);
                if (t == null) { Text.msg(p, "&c접속 중인 플레이어가 아닙니다."); return true; }
                if (gm.of(t.getUniqueId()) != null) { Text.msg(p, "&c이미 길드가 있는 플레이어입니다."); return true; }
                if (g.members.size() >= g.maxMembers()) { Text.msg(p, "&c길드 인원이 가득 찼습니다. (최대 " + g.maxMembers() + "명)"); return true; }
                gm.invite(g, t);
                Text.msg(p, "&a" + Text.name(t) + "님을 초대했습니다.");
                kr.rpgcraft.feature.TradeManager.buttons(t, Text.c(Text.PREFIX + "&b[" + g.name + "]&f 길드에서 초대했습니다. &7(60초) "), "/길드 수락", "/길드 거절");
            }
            case "수락", "accept" -> {
                Guild inv = gm.pendingInvite(p.getUniqueId());
                if (inv == null) { Text.msg(p, "&c받은 초대가 없습니다."); return true; }
                if (g != null) { Text.msg(p, "&c이미 길드에 소속되어 있습니다."); return true; }
                if (inv.members.size() >= inv.maxMembers()) { Text.msg(p, "&c길드 인원이 가득 찼습니다."); return true; }
                gm.clearInvite(p.getUniqueId());
                gm.addMember(inv, p.getUniqueId());
                inv.broadcast(Text.PREFIX + Text.c("&a" + Text.name(p) + "님이 길드에 가입했습니다."));
            }
            case "거절", "deny" -> {
                gm.clearInvite(p.getUniqueId());
                Text.msg(p, "초대를 거절했습니다.");
            }
            case "탈퇴", "leave" -> {
                if (g == null) { Text.msg(p, "&c길드가 없습니다."); return true; }
                if (g.isLeader(p.getUniqueId())) { Text.msg(p, "&c길드장은 위임 또는 해산만 가능합니다."); return true; }
                gm.removeMember(g, p.getUniqueId());
                d.guildChat = false;
                g.broadcast(Text.PREFIX + Text.c("&c" + Text.name(p) + "님이 길드를 탈퇴했습니다."));
                Text.msg(p, "길드를 탈퇴했습니다.");
            }
            case "추방", "kick" -> {
                if (!leader(p, g)) return true;
                if (a.length < 2) { Text.msg(p, "&c/길드 추방 <플레이어>"); return true; }
                UUID target = find(g, a[1]);
                if (target == null || target.equals(p.getUniqueId())) { Text.msg(p, "&c길드원이 아닙니다."); return true; }
                gm.removeMember(g, target);
                plugin.data().get(target).guildChat = false;
                g.broadcast(Text.PREFIX + Text.c("&c" + a[1] + "님이 길드에서 추방되었습니다."));
                Player t = Bukkit.getPlayer(target);
                if (t != null) Text.msg(t, "&c길드에서 추방되었습니다.");
            }
            case "위임" -> {
                if (!leader(p, g)) return true;
                if (a.length < 2) { Text.msg(p, "&c/길드 위임 <플레이어>"); return true; }
                UUID target = find(g, a[1]);
                if (target == null) { Text.msg(p, "&c길드원이 아닙니다."); return true; }
                g.leader = target;
                gm.save();
                g.broadcast(Text.PREFIX + Text.c("&e" + a[1] + "님이 새 길드장이 되었습니다."));
            }
            case "해산", "disband" -> {
                if (!leader(p, g)) return true;
                if (a.length < 2 || !a[1].equals("확인")) { Text.msg(p, "&c정말 해산하려면 &e/길드 해산 확인 &c(금고/창고/토템이 사라집니다)"); return true; }
                gm.disband(g);
                kr.rpgcraft.util.Text.announce(Text.PREFIX + Text.c("&7길드 [" + g.name + "]이(가) 해산되었습니다."));
            }
            case "정보", "info" -> {
                Guild t = a.length >= 2 ? gm.get(a[1]) : g;
                if (t == null) { Text.msg(p, "&c길드를 찾을 수 없습니다."); return true; }
                Text.msg(p, "&b&l[" + t.name + "] &7Lv." + t.level + " &f인원 " + t.members.size() + "/" + t.maxMembers());
                Text.msg(p, "&f길드장: &e" + nameOf(t.leader) + " &7| &f금고: &e" + Text.money(t.bank));
                StringBuilder sb = new StringBuilder();
                for (UUID m : t.members) {
                    if (sb.length() > 0) sb.append("&7, ");
                    sb.append(Bukkit.getPlayer(m) != null ? "&a" : "&7").append(nameOf(m));
                }
                Text.msg(p, "&f길드원: " + sb);
                for (int i = 0; i < t.totems.size(); i++) Text.msg(p, "&6토템 " + (i + 1) + ": &f" + gm.totemLabel(t.totems.get(i)));
                String castles = plugin.wars().castlesOwnedBy(t.name);
                if (!castles.isEmpty()) Text.msg(p, "&c보유 성: &f" + castles);
            }
            case "목록", "list" -> {
                Text.msg(p, "&e길드 목록 (" + gm.all().size() + ")");
                for (Guild t : gm.all()) p.sendMessage(Text.c(" &b" + t.name + " &7Lv." + t.level + " " + t.members.size() + "명 &8| 길드장 " + nameOf(t.leader)));
            }
            case "입금", "deposit" -> {
                if (g == null) { Text.msg(p, "&c길드가 없습니다."); return true; }
                long amt = a.length >= 2 ? Text.parseLong(a[1], -1) : -1;
                if (amt <= 0) { Text.msg(p, "&c/길드 입금 <금액>"); return true; }
                if (!plugin.economy().take(p, amt)) { Text.msg(p, "&c소지금이 부족합니다."); return true; }
                g.bank += amt;
                gm.save();
                g.broadcast(Text.PREFIX + Text.c("&e" + Text.name(p) + "님이 길드 금고에 " + Text.money(amt) + "을 입금했습니다."));
            }
            case "출금", "withdraw" -> {
                if (!leader(p, g)) return true;
                long amt = a.length >= 2 ? Text.parseLong(a[1], -1) : -1;
                if (amt <= 0 || amt > g.bank) { Text.msg(p, "&c금고 잔액이 부족합니다. (" + Text.money(g.bank) + ")"); return true; }
                g.bank -= amt;
                plugin.economy().give(p, amt);
                gm.save();
                g.broadcast(Text.PREFIX + Text.c("&e길드장이 길드 금고에서 " + Text.money(amt) + "을 출금했습니다."));
            }
            case "토벌전", "토벌", "raid" -> {   // 길드 토벌전
                if (a.length >= 2 && (a[1].equals("기록") || a[1].equals("순위") || a[1].equals("rank"))) {
                    plugin.guildRaids().ranking(p, a.length >= 3 ? (int) Text.parseLong(a[2], 1) : 1);
                } else if (a.length >= 2) plugin.guildRaids().start(p, (int) Text.parseLong(a[1], -1));
                else plugin.guildRaids().info(p);
            }
            case "스킬", "skill" -> gm.openSkills(p);   // v5.10.20 길드 스킬
            case "창고", "storage" -> {
                if (g == null) { Text.msg(p, "&c길드가 없습니다."); return true; }
                p.openInventory(g.storage());
            }
            case "레벨업", "levelup" -> {
                if (!leader(p, g)) return true;
                if (g.level >= gm.maxLevel()) { Text.msg(p, "&c최대 레벨입니다."); return true; }
                if (g.exp < gm.expNeed(g)) { Text.msg(p, "&c길드 경험치가 부족합니다. &7(" + Text.num(g.exp) + " / " + Text.num(gm.expNeed(g)) + ", 길드원이 사냥 · 보스 · 공성전으로 모음)"); return true; }
                long cost = gm.levelUpCost(g);
                if (g.bank < cost) { Text.msg(p, "&c길드 금고에 " + Text.money(cost) + "이 필요합니다."); return true; }
                g.bank -= cost;
                g.exp -= gm.expNeed(g);
                g.level++;
                gm.save();
                g.broadcast(Text.PREFIX + Text.c("&6길드 레벨이 &e" + g.level + "&6(으)로 올랐습니다! &7(인원 " + g.maxMembers() + ", 토템 " + g.totemSlots() + "칸, 창고 " + g.storageRows() + "줄, 스킬 포인트 +2 → /길드 스킬)"));
            }
            case "토템", "totem" -> {
                if (g == null) { Text.msg(p, "&c길드가 없습니다."); return true; }
                if (a.length == 1) {
                    Text.msg(p, "&6길드 토템 " + g.totems.size() + "/" + g.totemSlots());
                    for (int i = 0; i < g.totems.size(); i++) p.sendMessage(Text.c(" &e" + (i + 1) + ". &f" + gm.totemLabel(g.totems.get(i))));
                    Text.msg(p, "&7/길드 토템 설치 - 손에 든 토템 설치 | /길드 토템 해제 <번호>");
                    return true;
                }
                if (!leader(p, g)) return true;
                if (a[1].equals("설치")) {
                    ItemStack hand = p.getInventory().getItemInMainHand();
                    if (ItemData.category(hand) != Category.TOTEM) { Text.msg(p, "&c토템을 손에 들어주세요."); return true; }
                    if (g.totems.size() >= g.totemSlots()) { Text.msg(p, "&c토템 칸이 부족합니다. 길드 레벨을 올리세요."); return true; }
                    String t = ItemData.getString(hand, kr.rpgcraft.Keys.TOTEM);
                    if (t == null) { Text.msg(p, "&c빈 토템입니다."); return true; }
                    hand.setAmount(hand.getAmount() - 1);
                    g.totems.add(t);
                    gm.save();
                    g.broadcast(Text.PREFIX + Text.c("&6토템 설치: &f" + gm.totemLabel(t)));
                } else if (a[1].equals("해제") && a.length >= 3) {
                    int idx = Text.parseInt(a[2], 0) - 1;
                    if (idx < 0 || idx >= g.totems.size()) { Text.msg(p, "&c번호가 올바르지 않습니다."); return true; }
                    String t = g.totems.remove(idx);
                    gm.save();
                    for (ItemStack left : p.getInventory().addItem(gm.totemItem(t)).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
                    Text.msg(p, "&e토템을 해제했습니다: " + gm.totemLabel(t));
                }
            }
            case "채팅", "chat" -> {
                if (g == null) { Text.msg(p, "&c길드가 없습니다."); return true; }
                d.guildChat = !d.guildChat;
                Text.msg(p, d.guildChat ? "&a길드 채팅 모드 켜짐" : "&7길드 채팅 모드 꺼짐");
            }
            default -> help(p);
        }
        return true;
    }

    private boolean leader(Player p, Guild g) {
        if (g == null) {
            Text.msg(p, "&c길드가 없습니다.");
            return false;
        }
        if (!g.isLeader(p.getUniqueId())) {
            Text.msg(p, "&c길드장만 사용할 수 있습니다.");
            return false;
        }
        return true;
    }

    private UUID find(Guild g, String name) {
        for (UUID m : g.members) if (name.equalsIgnoreCase(nameOf(m))) return m;
        return null;
    }

    private String nameOf(UUID id) {
        return Text.name(id);   // 닉네임이 있으면 닉네임
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (a.length == 1) return SUBS.stream().filter(x -> x.startsWith(a[0])).toList();
        if (a.length == 2 && (a[0].equals("초대") || a[0].equals("추방") || a[0].equals("위임")))
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n -> n.toLowerCase().startsWith(a[1].toLowerCase())).toList();
        if (a.length == 2 && a[0].equals("토템")) return List.of("설치", "해제");
        if (a.length == 2 && a[0].equals("토벌전")) return List.of("1", "2", "3", "4", "5", "기록");
        if (a.length == 3 && a[0].equals("토벌전") && a[1].equals("기록")) return List.of("1", "2", "3", "4", "5");
        if (a.length == 2 && a[0].equals("정보")) return plugin.guilds().all().stream().map(g -> g.name).toList();
        return List.of();
    }
}
