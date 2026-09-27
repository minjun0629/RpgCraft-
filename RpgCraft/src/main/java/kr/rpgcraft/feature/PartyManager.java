package kr.rpgcraft.feature;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;

/**
 * 파티.
 * /파티 생성 · 초대 <플레이어> · 수락 · 거절 · 탈퇴 · 추방 <플레이어> · 목록 · 해산 · 위임 <플레이어>
 * - 파티원끼리는 공격할 수 없다
 * - 몬스터 처치 경험치는 근처(32칸) 파티원과 나누며, 인원 보너스(+10%/명)가 붙는다
 * - 던전은 파티원이 함께 입장한다
 * 모든 처치 보상은 giveKillReward 를 거친다 (낮/밤 배율 · 레전더리 「미다스의 손」 포함)
 */
public class PartyManager implements Listener, CommandExecutor, TabCompleter {
    public static class Party {
        public UUID leader;
        public final LinkedHashSet<UUID> members = new LinkedHashSet<>();

        public List<Player> online() {
            List<Player> out = new ArrayList<>();
            for (UUID u : members) {
                Player p = Bukkit.getPlayer(u);
                if (p != null) out.add(p);
            }
            return out;
        }
    }

    private final RpgCraft plugin;
    private final Map<UUID, Party> byMember = new HashMap<>();
    private final Map<UUID, Party> invites = new HashMap<>();
    private final Map<UUID, Long> inviteTime = new HashMap<>();

    public PartyManager(RpgCraft plugin) {
        this.plugin = plugin;
    }

    public Party of(Player p) {
        return p == null ? null : byMember.get(p.getUniqueId());
    }

    public boolean same(Player a, Player b) {
        Party pa = of(a);
        return pa != null && pa == of(b);
    }

    private int max() {
        return plugin.getConfig().getInt("party.max-members", 5);
    }

    // ------------------------------------------------------------------ 보상
    /** 처치 보상 지급 (파티원이 없으면 처치자 혼자) */
    public void giveKillReward(Player killer, double exp, long money) {
        giveKillReward(killer, null, exp, money);
    }

    /**
     * 처치 보상: 경험치는 100칸 안의 파티원에게 입힌 피해(기여도)에 비례해 나누고 인원 보너스(+10%/명),
     * 돈은 처치자에게. 낮/밤 배율 · 레전더리 「미다스의 손」 포함.
     */
    public void giveKillReward(Player killer, kr.rpgcraft.mob.MobManager.MobState st, double exp, long money) {
        double timeMult = plugin.cycle() == null ? 1 : plugin.cycle().rewardMult();
        exp *= timeMult;
        money = (long) (money * timeMult * (plugin.legendary() == null ? 1 : plugin.legendary().moneyMult(killer)));
        plugin.economy().give(killer, money);
        Party party = of(killer);
        Map<Player, Double> share = new LinkedHashMap<>();
        if (party != null) {
            double r = plugin.getConfig().getDouble("party.share-radius", 100);
            for (Player m : party.online()) {
                if (m.isDead() || !m.getWorld().equals(killer.getWorld()) || m.getLocation().distanceSquared(killer.getLocation()) > r * r) continue;
                double c = st == null ? 0 : st.contrib.getOrDefault(m.getUniqueId(), 0.0);
                if (c > 0 || m.equals(killer)) share.put(m, Math.max(c, m.equals(killer) && c <= 0 ? 1 : c));
            }
        }
        if (share.size() <= 1) {
            plugin.levels().addExp(killer, exp);
            return;
        }
        double total = 0;
        for (double v : share.values()) total += v;
        double bonus = 1 + plugin.getConfig().getDouble("party.exp-bonus-per-member", 0.1) * (share.size() - 1);
        for (Map.Entry<Player, Double> en : share.entrySet()) plugin.levels().addExp(en.getKey(), exp * bonus * en.getValue() / total);
    }

    // ------------------------------------------------------------------ 명령어
    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player p)) return true;
        String sub = a.length == 0 ? "목록" : a[0];
        switch (sub) {
            case "생성", "create" -> {
                if (of(p) != null) { Text.msg(p, "&c이미 파티에 속해 있습니다."); return true; }
                Party party = new Party();
                party.leader = p.getUniqueId();
                party.members.add(p.getUniqueId());
                byMember.put(p.getUniqueId(), party);
                Text.msg(p, "&a파티를 만들었습니다. &e/파티 초대 <플레이어>");
            }
            case "초대", "invite" -> {
                Party party = of(p);
                if (party == null) { party = new Party(); party.leader = p.getUniqueId(); party.members.add(p.getUniqueId()); byMember.put(p.getUniqueId(), party); }
                if (!party.leader.equals(p.getUniqueId())) { Text.msg(p, "&c파티장만 초대할 수 있습니다."); return true; }
                if (a.length < 2) { Text.msg(p, "&e/파티 초대 <플레이어>"); return true; }
                Player t = Bukkit.getPlayerExact(a[1]);
                if (t == null || t.equals(p)) { Text.msg(p, "&c접속 중인 다른 플레이어를 입력하세요."); return true; }
                if (of(t) != null) { Text.msg(p, "&c이미 다른 파티에 속한 플레이어입니다."); return true; }
                if (party.members.size() >= max()) { Text.msg(p, "&c파티 인원이 가득 찼습니다. (최대 " + max() + "명)"); return true; }
                invites.put(t.getUniqueId(), party);
                inviteTime.put(t.getUniqueId(), System.currentTimeMillis());
                Text.msg(p, "&a" + t.getName() + "님을 파티에 초대했습니다.");
                TradeManager.buttons(t, Text.c(Text.PREFIX + "&e" + p.getName() + "&f님의 파티 초대 &7(60초) "), "/파티 수락", "/파티 거절");
            }
            case "수락", "accept" -> {
                Party party = invites.remove(p.getUniqueId());
                Long t = inviteTime.remove(p.getUniqueId());
                if (party == null || t == null || System.currentTimeMillis() - t > 60_000 || party.members.isEmpty()) { Text.msg(p, "&c받은 파티 초대가 없습니다."); return true; }
                if (of(p) != null) { Text.msg(p, "&c이미 파티에 속해 있습니다."); return true; }
                if (party.members.size() >= max()) { Text.msg(p, "&c파티 인원이 가득 찼습니다."); return true; }
                party.members.add(p.getUniqueId());
                byMember.put(p.getUniqueId(), party);
                broadcast(party, "&a" + p.getName() + "님이 파티에 들어왔습니다.");
            }
            case "거절", "deny" -> {
                invites.remove(p.getUniqueId());
                Text.msg(p, "파티 초대를 거절했습니다.");
            }
            case "탈퇴", "leave" -> leave(p, "&7" + p.getName() + "님이 파티를 떠났습니다.");
            case "추방", "kick" -> {
                Party party = of(p);
                if (party == null || !party.leader.equals(p.getUniqueId())) { Text.msg(p, "&c파티장만 추방할 수 있습니다."); return true; }
                Player t = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : null;
                UUID tid = t != null ? t.getUniqueId() : null;
                if (tid == null || !party.members.contains(tid) || tid.equals(p.getUniqueId())) { Text.msg(p, "&c파티원 이름을 입력하세요."); return true; }
                party.members.remove(tid);
                byMember.remove(tid);
                Text.msg(t, "&c파티에서 추방되었습니다.");
                broadcast(party, "&7" + t.getName() + "님이 파티에서 추방되었습니다.");
            }
            case "위임", "leader" -> {
                Party party = of(p);
                Player t = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : null;
                if (party == null || !party.leader.equals(p.getUniqueId()) || t == null || !party.members.contains(t.getUniqueId())) { Text.msg(p, "&c/파티 위임 <파티원>"); return true; }
                party.leader = t.getUniqueId();
                broadcast(party, "&e" + t.getName() + "님이 새 파티장이 되었습니다.");
            }
            case "위치", "where" -> {
                Party party = of(p);
                if (party == null) { Text.msg(p, "&c파티가 없습니다."); return true; }
                for (Player m : party.online()) {
                    if (m.equals(p)) continue;
                    var ml = m.getLocation();
                    String dist = m.getWorld().equals(p.getWorld()) ? " &7(" + (int) ml.distance(p.getLocation()) + "m)" : " &7(" + ml.getWorld().getName() + ")";
                    p.sendMessage(Text.c(" &b● &f" + m.getName() + " &e" + ml.getBlockX() + ", " + ml.getBlockY() + ", " + ml.getBlockZ() + dist));
                }
            }
            case "해산", "disband" -> {
                Party party = of(p);
                if (party == null || !party.leader.equals(p.getUniqueId())) { Text.msg(p, "&c파티장만 해산할 수 있습니다."); return true; }
                broadcast(party, "&c파티가 해산되었습니다.");
                for (UUID u : party.members) byMember.remove(u);
                party.members.clear();
            }
            default -> { // 목록
                Party party = of(p);
                if (party == null) { Text.msg(p, "&7파티가 없습니다. &e/파티 생성 &7또는 &e/파티 초대 <플레이어>"); return true; }
                Text.msg(p, "&e파티원 (" + party.members.size() + "/" + max() + ")");
                for (UUID u : party.members) {
                    Player m = Bukkit.getPlayer(u);
                    String name = m != null ? m.getName() : Optional.ofNullable(Bukkit.getOfflinePlayer(u).getName()).orElse("?");
                    p.sendMessage(Text.c(" " + (u.equals(party.leader) ? "&6♔ " : "&7- ") + (m != null ? "&f" : "&8") + name
                            + (m != null ? " &7Lv." + plugin.data().get(m).level + " &c" + Text.num(plugin.data().get(m).hp) + "HP" : " (오프라인)")));
                }
            }
        }
        return true;
    }

    private void broadcast(Party party, String msg) {
        for (Player m : party.online()) Text.msg(m, msg);
    }

    private void leave(Player p, String msg) {
        Party party = byMember.remove(p.getUniqueId());
        if (party == null) { Text.msg(p, "&c파티에 속해 있지 않습니다."); return; }
        party.members.remove(p.getUniqueId());
        Text.msg(p, "&7파티를 떠났습니다.");
        if (party.members.isEmpty()) return;
        if (party.leader.equals(p.getUniqueId())) party.leader = party.members.iterator().next();
        broadcast(party, msg);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        invites.remove(e.getPlayer().getUniqueId());
        Party party = of(e.getPlayer());
        if (party == null) return;
        // 파티장이 나가면 다른 접속 중인 파티원에게 위임 (파티는 유지)
        if (party.leader.equals(e.getPlayer().getUniqueId())) {
            for (Player m : party.online()) if (!m.equals(e.getPlayer())) { party.leader = m.getUniqueId(); broadcast(party, "&e" + m.getName() + "님이 파티장이 되었습니다."); break; }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (a.length == 1) return List.of("생성", "초대", "수락", "거절", "탈퇴", "추방", "위임", "해산", "목록", "위치");
        if (a.length == 2 && List.of("초대", "추방", "위임").contains(a[0])) {
            List<String> out = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
            return out;
        }
        return List.of();
    }
}
