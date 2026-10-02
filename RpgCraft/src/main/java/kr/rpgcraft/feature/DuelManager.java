package kr.rpgcraft.feature;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.data.PlayerData;
import kr.rpgcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.*;

/**
 * PVP (1대1 결투) — v5.4.34
 *  /pvp <닉네임> 으로 신청 → 상대가 채팅의 [수락]/[거절] 클릭 (또는 /pvp 수락 · /pvp 거절)
 *  수락하면 두 사람이 하늘의 결투장으로 이동해 3초 뒤 시작.
 *  체력이 0 이 되면 진짜로 죽지 않고 패배 처리 → 경험치 · 돈 · 사망 패널티 없음.
 *  끝나면 (승패 · 무승부 · 나감) 두 사람 모두 체력을 채우고 오기 전 자리로 돌아간다.
 *  v5.10.61 친선전 (명성 변화 없음) · 랭킹전 (명성 · 전적 반영, 랭킹 메뉴 PVP 탭) 으로 나뉨.
 *   /pvp <닉네임> → 채팅의 [친선전] [랭킹전] 중 골라 신청 · /pvp 친선 <닉네임> · /pvp 랭킹전 <닉네임>
 */
public class DuelManager implements CommandExecutor, TabCompleter, Listener {
    private static final int SLOTS = 8, ARENA_R = 12, WALL_H = 6;

    private static class Duel {
        final UUID a, b;
        final Location backA, backB;
        final int slot;
        long startAt, endAt;
        boolean over, ranked;

        Duel(UUID a, UUID b, Location backA, Location backB, int slot) {
            this.a = a;
            this.b = b;
            this.backA = backA;
            this.backB = backB;
            this.slot = slot;
        }

        UUID other(UUID u) {
            return u.equals(a) ? b : a;
        }
    }

    private final RpgCraft plugin;
    /** 받은 사람 → [보낸 사람, 시각] */
    private final Map<UUID, Object[]> requests = new HashMap<>();
    private final Map<UUID, Duel> duels = new HashMap<>();
    private final boolean[] slotUsed = new boolean[SLOTS];
    private final Set<Integer> built = new HashSet<>();
    /** 결투장으로 / 원래 자리로 보내는 순간이동은 막지 않음 */
    private final Set<UUID> moving = new HashSet<>();

    public DuelManager(RpgCraft plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    // ------------------------------------------------------------------ 판정 (다른 시스템에서 호출)
    public boolean inDuel(Player p) {
        return duels.containsKey(p.getUniqueId());
    }

    /** 결투 중인 두 사람이 서로인지 */
    public boolean pair(Player x, Player y) {
        Duel d = duels.get(x.getUniqueId());
        return d != null && d.other(x.getUniqueId()).equals(y.getUniqueId());
    }

    /** 결투가 시작돼(카운트다운 끝) 서로 때릴 수 있는지 */
    public boolean fighting(Player x, Player y) {
        Duel d = duels.get(x.getUniqueId());
        return d != null && !d.over && pair(x, y) && System.currentTimeMillis() >= d.startAt;
    }

    /**
     * 결투 중 치명상: 진짜로 죽지 않고 패배 처리.
     * @return true 면 HealthManager 가 사망 처리를 하지 않는다
     */
    public boolean onLethal(Player p) {
        Duel d = duels.get(p.getUniqueId());
        if (d == null) return false;
        if (!d.over) finish(d, d.other(p.getUniqueId()), p.getUniqueId(), false);
        return true;
    }

    // ------------------------------------------------------------------ 명령어
    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!(s instanceof Player p)) return true;
        if (a.length < 1) {
            if (plugin.boards() != null) plugin.boards().new FameGui(p).open(p);   // v5.10.59 명성 랭킹은 /pvp 에서 (보드게임에서 옮김)
            Text.msg(p, "&e/pvp <닉네임> &7- 1대1 결투 신청 ([친선전] · [랭킹전] 중 고르기)");
            Text.msg(p, "&e/pvp 친선 <닉네임> &7- 친선전 (명성 변화 없음) · &e/pvp 랭킹전 <닉네임> &7- 랭킹전 (명성 · 전적 반영)");
            Text.msg(p, "&e/pvp 수락 &7· &e/pvp 거절 &7- 받은 신청에 답하기");
            Text.msg(p, "&e/pvp 랭킹 &7- 명성 랭킹 · 등급 칭호 (랭킹전에서 이기면 명성 +, 지면 -)");
            return true;
        }
        Boolean ranked = null;
        if (a.length >= 2 && (a[0].equals("친선") || a[0].equals("친선전") || a[0].equalsIgnoreCase("friendly"))) { ranked = false; a = new String[]{a[1]}; }
        else if (a.length >= 2 && (a[0].equals("랭킹전") || a[0].equals("랭크") || a[0].equalsIgnoreCase("ranked"))) { ranked = true; a = new String[]{a[1]}; }
        switch (a[0]) {
            case "수락", "accept" -> { respond(p, true); return true; }
            case "랭킹", "명성", "rank" -> { if (plugin.boards() != null) plugin.boards().new FameGui(p).open(p); return true; }   // v5.10.45
            case "거절", "deny" -> { respond(p, false); return true; }
            default -> { }
        }
        Player t = Text.player(a[0]);
        if (t == null || t.equals(p)) { Text.msg(p, "&c접속 중인 다른 플레이어를 입력하세요."); return true; }
        String why = blocked(p);
        if (why == null) why = blocked(t);
        if (why != null) { Text.msg(p, "&c" + why); return true; }
        if (ranked == null) {   // 종류를 고르게 채팅 버튼
            String nm = t.getName();
            var msg = new net.md_5.bungee.api.chat.ComponentBuilder("");
            msg.append(net.md_5.bungee.api.chat.TextComponent.fromLegacyText(Text.c(Text.PREFIX + "&e" + Text.name(t) + "&f님에게 어떤 PVP를 신청할까요? ")));
            net.md_5.bungee.api.chat.TextComponent f = new net.md_5.bungee.api.chat.TextComponent(net.md_5.bungee.api.chat.TextComponent.fromLegacyText(Text.c("&a&l[친선전]")));
            f.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/pvp 친선 " + nm));
            f.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT, new net.md_5.bungee.api.chat.hover.content.Text(Text.c("&a친선전\n&7명성 · 전적 변화 없음 · 연습용"))));
            net.md_5.bungee.api.chat.TextComponent r = new net.md_5.bungee.api.chat.TextComponent(net.md_5.bungee.api.chat.TextComponent.fromLegacyText(Text.c(" &c&l[랭킹전]")));
            r.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/pvp 랭킹전 " + nm));
            r.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT, new net.md_5.bungee.api.chat.hover.content.Text(Text.c("&c랭킹전\n&7이기면 명성 +, 지면 - · 전적 기록\n&7랭킹 메뉴 PVP 탭에 반영"))));
            msg.append(f, net.md_5.bungee.api.chat.ComponentBuilder.FormatRetention.NONE);
            msg.append(r, net.md_5.bungee.api.chat.ComponentBuilder.FormatRetention.NONE);
            p.spigot().sendMessage(msg.create());
            return true;
        }
        Object[] prev = requests.get(t.getUniqueId());
        if (prev != null && p.getUniqueId().equals(prev[0]) && System.currentTimeMillis() - (long) prev[1] < 30_000) {
            Text.msg(p, "&7이미 신청했습니다. 상대의 답을 기다리세요.");
            return true;
        }
        requests.put(t.getUniqueId(), new Object[]{p.getUniqueId(), System.currentTimeMillis(), ranked});
        String kind = ranked ? "&c랭킹전" : "&a친선전";
        Text.msg(p, "&a" + Text.name(t) + "님에게 PVP " + kind + "&a을 신청했습니다. &7(30초)");
        TradeManager.buttons(t, Text.c(Text.PREFIX + "&c⚔ &e" + Text.name(p) + "&f님이 PVP " + kind + "&f을 신청했습니다 " + (ranked ? "&7(명성 반영 · 30초) " : "&7(명성 변화 없음 · 30초) ")), "/pvp 수락", "/pvp 거절");
        t.playSound(t.getLocation(), Sound.ITEM_TRIDENT_RETURN, 1f, 0.8f);
        return true;
    }

    /** 결투를 할 수 없는 상태면 이유 */
    private String blocked(Player p) {
        if (inDuel(p)) return Text.name(p) + "님은 이미 PVP 중입니다.";
        if (p.isDead()) return Text.name(p) + "님은 쓰러져 있습니다.";
        if (plugin.dungeons() != null && plugin.dungeons().runOf(p) != null) return Text.name(p) + "님은 던전 공략 중입니다.";
        if (plugin.tower() != null && plugin.tower().inRun(p)) return Text.name(p) + "님은 무한의 탑 도전 중입니다.";
        return null;
    }

    private void respond(Player p, boolean ok) {
        Object[] r = requests.remove(p.getUniqueId());
        if (r == null || System.currentTimeMillis() - (long) r[1] > 30_000) { Text.msg(p, "&7받은 PVP 신청이 없습니다."); return; }
        Player from = Bukkit.getPlayer((UUID) r[0]);
        if (from == null) { Text.msg(p, "&7신청한 플레이어가 없습니다."); return; }
        if (!ok) {
            Text.msg(from, "&c" + Text.name(p) + "님이 PVP를 거절했습니다.");
            Text.msg(p, "&7거절했습니다.");
            return;
        }
        String why = blocked(from);
        if (why == null) why = blocked(p);
        if (why != null) { Text.msg(p, "&c" + why); Text.msg(from, "&c" + why); return; }
        start(from, p, r.length > 2 && Boolean.TRUE.equals(r[2]));
    }

    // ------------------------------------------------------------------ 진행
    private void start(Player x, Player y, boolean ranked) {
        int slot = -1;
        for (int i = 0; i < SLOTS; i++) if (!slotUsed[i]) { slot = i; break; }
        if (slot < 0) { Text.msg(x, "&c결투장이 모두 사용 중입니다. 잠시 후 다시 시도하세요."); Text.msg(y, "&c결투장이 모두 사용 중입니다."); return; }
        slotUsed[slot] = true;
        Location center = center(slot);
        buildArena(slot, center);
        Duel d = new Duel(x.getUniqueId(), y.getUniqueId(), x.getLocation().clone(), y.getLocation().clone(), slot);
        d.ranked = ranked;
        d.startAt = System.currentTimeMillis() + 3000;
        d.endAt = d.startAt + plugin.getConfig().getLong("duel.time-limit-seconds", 180) * 1000;
        duels.put(x.getUniqueId(), d);
        duels.put(y.getUniqueId(), d);
        Location sa = center.clone().add(-8.5, 1, 0.5), sb = center.clone().add(9.5, 1, 0.5);
        sa.setYaw(-90);
        sb.setYaw(90);
        for (Player q : new Player[]{x, y}) {
            PlayerData pd = plugin.data().get(q);
            plugin.stats().refresh(q);
            pd.hp = pd.stats.maxHp;
            plugin.health().sync(q);
        }
        send(x, sa);
        send(y, sb);
        Text.announce(Text.PREFIX + Text.c("&c⚔ &e" + Text.name(x) + " &fvs &e" + Text.name(y) + " &fPVP " + (ranked ? "&c랭킹전" : "&a친선전") + " &f시작!"));
        for (int i = 3; i >= 1; i--) {
            int n = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (Player q : online(d)) { q.sendTitle(Text.c("&e&l" + n), Text.c("&7PVP 준비"), 0, 20, 0); q.playSound(q.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f); }
            }, (3 - i) * 20L);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (d.over) return;
            for (Player q : online(d)) { q.sendTitle(Text.c("&c&lPVP!"), Text.c("&7상대의 체력을 0 으로 만드세요"), 0, 25, 10); q.playSound(q.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.8f, 1.4f); }
        }, 60L);
    }

    private List<Player> online(Duel d) {
        List<Player> out = new ArrayList<>(2);
        Player pa = Bukkit.getPlayer(d.a), pb = Bukkit.getPlayer(d.b);
        if (pa != null) out.add(pa);
        if (pb != null) out.add(pb);
        return out;
    }

    private void send(Player p, Location to) {
        moving.add(p.getUniqueId());
        try { p.teleport(to); } finally { moving.remove(p.getUniqueId()); }
    }

    /** @param winner null 이면 무승부 */
    private void finish(Duel d, UUID winner, UUID loser, boolean draw) {
        if (d.over) return;
        d.over = true;
        Player w = winner == null ? null : Bukkit.getPlayer(winner), l = loser == null ? null : Bukkit.getPlayer(loser);
        if (draw) Text.announce(Text.PREFIX + Text.c("&7⚔ PVP 무승부 — 제한 시간이 끝났습니다."));
        else {
            String wn = w != null ? Text.name(w) : "?", ln = l != null ? Text.name(l) : "?";
            Text.announce(Text.PREFIX + Text.c("&c⚔ &e" + wn + "&f님이 PVP " + (d.ranked ? "&c랭킹전" : "&a친선전") + "&f에서 &e" + ln + "&f님을 이겼습니다!"));
            if (w != null) { w.sendTitle(Text.c("&6&l승리"), Text.c("&7잠시 후 원래 자리로 돌아갑니다"), 5, 40, 10); w.playSound(w.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f); }
            if (l != null) { l.sendTitle(Text.c("&c&l패배"), Text.c("&7PVP에서는 잃는 것이 없습니다"), 5, 40, 10); l.playSound(l.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 0.8f); }
            if (w != null && l != null && plugin.boards() != null && d.ranked) {   // v5.10.45 명성 (v5.10.61 랭킹전만)
                int[] dl = plugin.boards().recordDuel(w, l);
                if (dl[0] != 0 || dl[1] != 0) {
                    Text.msg(w, "&c⚔ 명성 &a+" + dl[0] + " &7→ " + plugin.boards().fame(w.getUniqueId()) + " (" + plugin.boards().tierLabel(w.getUniqueId()) + "&7)");
                    Text.msg(l, "&c⚔ 명성 &c" + dl[1] + " &7→ " + plugin.boards().fame(l.getUniqueId()) + " (" + plugin.boards().tierLabel(l.getUniqueId()) + "&7)");
                } else {
                    Text.msg(w, "&7오늘 같은 상대와는 명성이 더 오르지 않습니다. (하루 " + plugin.getConfig().getInt("duel.fame-pair-daily", 3) + "판)");
                    Text.msg(l, "&7오늘 같은 상대와는 명성이 더 바뀌지 않습니다.");
                }
            }
        }
        for (Player q : online(d)) {   // 체력 가득 (패배자도 바로 회복, 진짜 사망 없음)
            PlayerData pd = plugin.data().get(q);
            pd.hp = pd.stats.maxHp;
            q.setFireTicks(0);
            plugin.health().sync(q);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> close(d), 60L);
    }

    /** 원래 자리로 되돌리고 결투장 비우기 */
    private void close(Duel d) {
        duels.remove(d.a, d);
        duels.remove(d.b, d);
        slotUsed[d.slot] = false;
        Player pa = Bukkit.getPlayer(d.a), pb = Bukkit.getPlayer(d.b);
        if (pa != null) returnHome(pa, d.backA);
        if (pb != null) returnHome(pb, d.backB);
    }

    private void returnHome(Player p, Location back) {
        PlayerData pd = plugin.data().get(p);
        pd.hp = pd.stats.maxHp;
        plugin.health().sync(p);
        send(p, back);
        p.getWorld().spawnParticle(Particle.PORTAL, p.getLocation().add(0, 1, 0), 30, 0.4, 0.8, 0.4, 0.2);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        requests.values().removeIf(r -> now - (long) r[1] > 30_000);
        for (Duel d : new HashSet<>(duels.values())) {
            if (d.over) continue;
            if (now >= d.endAt) { finish(d, null, null, true); continue; }
            long left = (d.endAt - now) / 1000;
            if (now >= d.startAt) for (Player q : online(d)) Text.actionBar(q, "&c⚔ PVP &7남은 시간 &e" + left / 60 + ":" + String.format("%02d", left % 60));
            for (Player q : online(d)) {   // 결투장 밖으로 떨어지면 (공허 등) 패배
                Location c = center(d.slot);
                if (q.getWorld() != c.getWorld() || q.getLocation().getY() < c.getY() - 8) {
                    finish(d, d.other(q.getUniqueId()), q.getUniqueId(), false);
                    break;
                }
            }
        }
    }

    /** 서버 종료: 결투 중인 사람을 모두 원래 자리로 */
    public void shutdown() {
        for (Duel d : new HashSet<>(duels.values())) { d.over = true; close(d); }
    }

    // ------------------------------------------------------------------ 이벤트
    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        requests.remove(p.getUniqueId());
        Duel d = duels.get(p.getUniqueId());
        if (d == null) return;
        if (!d.over) {
            d.over = true;
            Player o = Bukkit.getPlayer(d.other(p.getUniqueId()));
            Text.announce(Text.PREFIX + Text.c("&c⚔ &e" + Text.name(p) + "&f님이 나가서 " + (o != null ? "&e" + Text.name(o) + "&f님이 PVP에서 이겼습니다." : "PVP가 끝났습니다.")));
        }
        send(p, p.getUniqueId().equals(d.a) ? d.backA : d.backB);   // 나가는 사람도 원래 자리에 저장되게
        duels.remove(p.getUniqueId());
        Bukkit.getScheduler().runTaskLater(plugin, () -> close(d), 20L);
    }

    /** 결투 중에는 /tpa · 귀환 주문서 등으로 빠져나갈 수 없음 (엔더 진주는 결투장 안에서만) */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        Duel d = duels.get(p.getUniqueId());
        if (d == null || moving.contains(p.getUniqueId()) || d.over) return;
        Location to = e.getTo();
        Location c = center(d.slot);
        boolean inside = to != null && to.getWorld() == c.getWorld() && Math.abs(to.getX() - c.getX()) <= ARENA_R + 1 && Math.abs(to.getZ() - c.getZ()) <= ARENA_R + 1;
        if (!inside) {
            e.setCancelled(true);
            Text.actionBar(p, "&cPVP 중에는 결투장을 떠날 수 없습니다.");
        }
    }

    // ------------------------------------------------------------------ 결투장 (하늘 위, 자리 8개)
    private Location center(int slot) {
        World w = Bukkit.getWorlds().get(0);
        Location sp = w.getSpawnLocation();
        int y = Math.min(w.getMaxHeight() - 20, plugin.getConfig().getInt("duel.arena-y", 250));
        return new Location(w, sp.getBlockX() + plugin.getConfig().getInt("duel.arena-offset-x", 20000) + slot * 64, y, sp.getBlockZ());
    }

    private void buildArena(int slot, Location c) {
        if (built.contains(slot) && c.getBlock().getType() == Material.POLISHED_BLACKSTONE_BRICKS) return;
        built.add(slot);
        World w = c.getWorld();
        int cx = c.getBlockX(), cy = c.getBlockY(), cz = c.getBlockZ();
        for (int x = -ARENA_R; x <= ARENA_R; x++)
            for (int z = -ARENA_R; z <= ARENA_R; z++) {
                boolean edge = Math.abs(x) == ARENA_R || Math.abs(z) == ARENA_R;
                boolean ring = Math.max(Math.abs(x), Math.abs(z)) == 4;
                Material floor = (x == 0 && z == 0) ? Material.POLISHED_BLACKSTONE_BRICKS
                        : ring ? Material.RED_NETHER_BRICKS
                        : (x + z) % 2 == 0 ? Material.POLISHED_BLACKSTONE : Material.POLISHED_DEEPSLATE;
                w.getBlockAt(cx + x, cy, cz + z).setType(edge ? Material.CHISELED_POLISHED_BLACKSTONE : floor, false);
                w.getBlockAt(cx + x, cy - 1, cz + z).setType(Material.BLACKSTONE, false);
                for (int h = 1; h <= WALL_H + 3; h++)
                    w.getBlockAt(cx + x, cy + h, cz + z).setType(edge ? (h <= WALL_H ? Material.BARRIER : Material.AIR) : Material.AIR, false);
                if (edge) w.getBlockAt(cx + x, cy + 1, cz + z).setType(Material.RED_STAINED_GLASS, false);
            }
        for (int[] k : new int[][]{{-ARENA_R + 1, -ARENA_R + 1}, {ARENA_R - 1, -ARENA_R + 1}, {-ARENA_R + 1, ARENA_R - 1}, {ARENA_R - 1, ARENA_R - 1}}) {   // 네 귀퉁이 불빛 기둥
            w.getBlockAt(cx + k[0], cy + 1, cz + k[1]).setType(Material.POLISHED_BLACKSTONE_WALL, false);
            w.getBlockAt(cx + k[0], cy + 2, cz + k[1]).setType(Material.SHROOMLIGHT, false);
        }
        for (int x = -ARENA_R; x <= ARENA_R; x++)
            for (int z = -ARENA_R; z <= ARENA_R; z++) w.getBlockAt(cx + x, cy + WALL_H + 1, cz + z).setType(Material.BARRIER, false);   // 투명 천장 (날아서 넘지 못하게)
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (a.length < 1 || a.length > 2) return List.of();
        if (a.length == 2 && !(a[0].startsWith("친선") || a[0].equals("랭킹전"))) return List.of();
        List<String> out = new ArrayList<>();
        if (a.length == 2 && (a[0].startsWith("친선") || a[0].equals("랭킹전"))) return Text.onlineNames(a[1], s);
        for (String x : List.of("수락", "거절", "랭킹", "친선", "랭킹전")) if (x.startsWith(a[0])) out.add(x);
        out.addAll(Text.onlineNames(a[0], s));
        return out;
    }
}
