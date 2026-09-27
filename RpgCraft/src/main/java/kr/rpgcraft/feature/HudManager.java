package kr.rpgcraft.feature;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.data.PlayerData;
import kr.rpgcraft.data.Setting;
import kr.rpgcraft.stat.Power;
import kr.rpgcraft.guild.Guild;
import kr.rpgcraft.passive.Passive;
import kr.rpgcraft.stat.StatSnapshot;
import kr.rpgcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 10틱마다 스탯 재계산, 이동속도, 자연회복, 경험치바, 액션바, 사이드바 갱신 */
public class HudManager implements Listener {
    private static final int LINES = 12;
    private final RpgCraft plugin;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private int tick;

    public HudManager(RpgCraft plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::loop, 10L, 10L);
    }

    private void loop() {
        tick++;
        boolean second = tick % 2 == 0;
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData d = plugin.data().get(p);
            StatSnapshot s = plugin.stats().refresh(p);

            float walk = (float) Math.max(0.05, Math.min(1.0, 0.2 * (1 + s.speed / 100.0)));
            if (Math.abs(p.getWalkSpeed() - walk) > 0.001) p.setWalkSpeed(walk);

            if (p.isSprinting()) {
                if (d.runStart == 0) d.runStart = now;
                if (second) plugin.passives().track(p, "sprint_seconds", 1);
            } else d.runStart = 0;

            if (second && !p.isDead()) {
                boolean combat = now - d.lastCombat < plugin.getConfig().getLong("player.regen-delay-seconds", 8) * 1000;
                double pct = combat ? plugin.getConfig().getDouble("player.regen-combat-percent", 0.2)
                        : plugin.getConfig().getDouble("player.regen-percent", 2);
                if (d.hp < s.maxHp) plugin.health().heal(p, s.maxHp * pct / 100);
                if (d.has(Passive.FC_SEA_GOLEM) && !d.onCooldown("sea_golem")
                        && (p.isInWater() || p.getLocation().getBlock().getType() == Material.WATER) && d.hp < s.maxHp) {
                    d.cooldown("sea_golem", 600_000);
                    plugin.health().set(p, s.maxHp);
                    Text.msg(p, "&b바다골렘의 가호! 체력이 모두 회복되었습니다.");
                }
            }
            if (!p.isDead() && p.getGameMode() != GameMode.CREATIVE && p.getGameMode() != GameMode.SPECTATOR) plugin.health().sync(p);

            double need = plugin.levels().need(d.level);
            p.setLevel(d.level);
            p.setExp((float) Math.max(0, Math.min(0.999, d.exp / need)));

            d.counters.put("power", (double) Power.of(s));
            if (packHud(p, d)) {
                drawPackHud(p, d, s);
            } else if (d.actionBarLock < now && Setting.HUD.get(d)) {
                double r = s.maxHp <= 0 ? 0 : Math.max(0, Math.min(1, d.hp / s.maxHp));
                int full = (int) Math.round(r * 12);
                String hc = r > 0.5 ? "&c" : r > 0.25 ? "&6" : "&4";
                String quick = d.quickSkill == null ? "" : quickState(d);
                Text.actionBar(p, hc + "❤ " + Text.num(d.hp) + " " + hc + "▮".repeat(full) + "&8" + "▮".repeat(12 - full)
                        + "  &6⚔ " + Text.num(s.attack) + (s.holdingBow ? " &e➶ " + Text.num(s.ranged) : "")
                        + "  &e✦ " + String.format("%.1f", s.crit) + "%"
                        + "  &b🛡 " + String.format("%.1f", s.def) + "%" + quick
                        + (s.weaponOk ? "" : "  &c[무기 조건 미충족]"));
            }
            if (tick % 8 == 0) { // 4초마다: 가진 아이템을 도감에 기록
                for (org.bukkit.inventory.ItemStack it : p.getInventory().getContents()) d.markSeen(kr.rpgcraft.item.ItemData.id(it));
            }
            if (second) {
                updateSidebar(p, d, s);
                if (tick % 4 == 0) updateTab(p, d, s);
            }
        }
    }

    private final Map<UUID, String> notice = new HashMap<>();
    private final Map<UUID, Long> noticeUntil = new HashMap<>();

    private boolean packHud(Player p, PlayerData d) {
        return Setting.HUD.get(d) && plugin.getConfig().getBoolean("hud.pack-hud", true) && plugin.pack().hasPack(p);
    }

    /** 리소스팩 HUD 사용 중이면 알림을 HUD 위 한 줄로 표시하고 true */
    public boolean notice(Player p, String text) {
        PlayerData d = plugin.data().isLoaded(p.getUniqueId()) ? plugin.data().get(p) : null;
        if (d == null || !packHud(p, d)) return false;
        notice.put(p.getUniqueId(), text);
        noticeUntil.put(p.getUniqueId(), System.currentTimeMillis() + plugin.getConfig().getLong("hud.notice-ms", 2200));
        drawPackHud(p, d, d.stats);
        return true;
    }

    /** 파티에 속해 있으면 화면 왼쪽에 파티원(자신 포함) 아이콘 · 체력 · 경험치 */
    private java.util.List<net.md_5.bungee.api.chat.BaseComponent> partyRows(Player p) {
        var party = plugin.party() == null ? null : plugin.party().of(p);
        if (party == null || !plugin.getConfig().getBoolean("party.hud", true)) return java.util.List.of();
        java.util.List<Player> members = new java.util.ArrayList<>(party.online());
        if (members.size() < 2) return java.util.List.of();
        members.remove(p);
        members.add(0, p);
        java.util.List<kr.rpgcraft.pack.HudFont.PartyRow> rows = new java.util.ArrayList<>();
        for (Player m : members) {
            PlayerData md = plugin.data().get(m);
            double need = Math.max(1, plugin.levels().need(md.level));
            int dist = m.getWorld().equals(p.getWorld()) ? (int) m.getLocation().distance(p.getLocation()) : -1;
            rows.add(new kr.rpgcraft.pack.HudFont.PartyRow(kr.rpgcraft.pack.HudFont.hudName(Text.strip(m.getDisplayName()), m.getName()), md.level, md.hp / Math.max(1, md.stats.maxHp), md.exp / need,
                    party.leader.equals(m.getUniqueId()), m.equals(p), dist));
        }
        return kr.rpgcraft.pack.HudFont.party(rows, plugin.getConfig().getInt("party.hud-x", 320));
    }

    private void drawPackHud(Player p, PlayerData d, StatSnapshot s) {
        int potions = d.potionBag.values().stream().mapToInt(Integer::intValue).sum();
        int strong = plugin.weaponSkills().strongCooldown(p);
        Passive qp = d.quickSkill == null ? null : Passive.find(d.quickSkill);
        int quick = qp == null ? -1 : (int) Math.ceil(d.remaining("active_" + qp.name()) / 1000.0);
        String hud = kr.rpgcraft.pack.HudFont.build(d.hp, s.maxHp, potions, strong, s.def, s.attack, s.crit, quick);
        String n = noticeUntil.getOrDefault(p.getUniqueId(), 0L) > System.currentTimeMillis() ? notice.get(p.getUniqueId()) : null;
        kr.rpgcraft.pack.HudFont.send(p, hud, n, partyRows(p));
    }

    private String quickState(PlayerData d) {
        Passive ps = Passive.find(d.quickSkill);
        if (ps == null) return "";
        long rem = d.remaining("active_" + ps.name());
        return rem > 0 ? "  &8[" + ps.label + " " + (rem / 1000 + 1) + "s]" : "  &a[" + ps.label + " ✔]";
    }

    /** 사이드바 오른쪽 빨간 점수 숨기기 (Paper 1.20.4+ 의 NumberFormat.blank, 없는 서버에선 그대로) */
    private static void hideScores(Objective o) {
        try {
            Class<?> nf = Class.forName("io.papermc.paper.scoreboard.numbers.NumberFormat");
            Object blank = nf.getMethod("blank").invoke(null);
            o.getClass().getMethod("numberFormat", nf).invoke(o, blank);
        } catch (Throwable ignored) {
        }
    }

    /** 리소스팩 적용 완료 시: 사이드바 제목을 로고로 */
    public void onPackLoaded(Player p) {
        Scoreboard sb = boards.get(p.getUniqueId());
        Objective o = sb == null ? null : sb.getObjective("rpgcraft");
        if (o != null) o.setDisplayName(Text.c("&f" + kr.rpgcraft.pack.HudFont.LOGO));
        updateTab(p, plugin.data().get(p), plugin.data().get(p).stats);
    }

    /** 탭 목록 머리말/꼬리말 + 이름 (배지는 모두가 리소스팩을 쓰는 필수 모드에서만) */
    private void updateTab(Player p, PlayerData d, StatSnapshot s) {
        boolean pack = plugin.pack().hasPack(p);
        String header = pack ? "\n" + Text.c("&f" + kr.rpgcraft.pack.HudFont.LOGO) + "\n" + Text.c("&7대규모 RPG · PvP 서버") + "\n"
                : Text.c("\n&6&l✦ RpgCraft ✦\n&7대규모 RPG · PvP 서버\n");
        Guild g = plugin.guilds().of(p.getUniqueId());
        String footer = Text.c("\n&f접속 &a" + Bukkit.getOnlinePlayers().size() + "명 &8| &f전투력 &e"
                + Text.num(Power.of(s)) + (g == null ? "" : " &8| &b" + g.name) + "\n&8쉬프트+F 메뉴 · F 포션 · 쉬프트+Q 퀵 스킬\n");
        p.setPlayerListHeaderFooter(header, footer);
        String badge = plugin.pack().overlay() ? kr.rpgcraft.pack.HudFont.badge(d.level) + " " : Text.c("&7[" + d.level + "] ");
        String title = plugin.content() == null ? "" : plugin.content().title(d);
        p.setPlayerListName(badge + Text.c((title.isEmpty() ? "" : "&d«" + title + "» ") + (g == null ? "&f" : "&b") + Text.name(p)));
    }

    /** 닉네임을 바꿨을 때 탭 목록 이름 바로 갱신 */
    public void refreshTab(Player p) {
        PlayerData d = plugin.data().get(p);
        updateTab(p, d, d.stats);
    }

    /** 설정에 맞춰 사이드바 표시/숨김 */
    public void applySidebar(Player p) {
        boolean want = plugin.getConfig().getBoolean("hud.sidebar", true) && Setting.SIDEBAR.get(plugin.data().get(p));
        if (want && !boards.containsKey(p.getUniqueId())) setup(p);
        else if (!want && boards.remove(p.getUniqueId()) != null) p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    public void setup(Player p) {
        if (!plugin.getConfig().getBoolean("hud.sidebar", true) || !Setting.SIDEBAR.get(plugin.data().get(p))) return;
        Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
        @SuppressWarnings("deprecation")
        Objective o = sb.registerNewObjective("rpgcraft", "dummy",
                plugin.pack().hasPack(p) ? Text.c("&f" + kr.rpgcraft.pack.HudFont.LOGO) : Text.c("&6&l✦ RpgCraft ✦"));
        o.setDisplaySlot(DisplaySlot.SIDEBAR);
        hideScores(o);
        for (int i = 0; i < LINES; i++) {
            String entry = ChatColor.values()[i].toString() + ChatColor.RESET;
            Team t = sb.registerNewTeam("l" + i);
            t.addEntry(entry);
            o.getScore(entry).setScore(LINES - i);
        }
        plugin.visuals().registerTeams(sb);
        if (plugin.nicks() != null) plugin.nicks().registerTeam(sb);   // 닉네임 사용자는 바닐라 이름표 숨김
        boards.put(p.getUniqueId(), sb);
        p.setScoreboard(sb);
    }

    private void updateSidebar(Player p, PlayerData d, StatSnapshot s) {
        Scoreboard sb = boards.get(p.getUniqueId());
        if (sb == null) return;
        Guild g = plugin.guilds().of(p.getUniqueId());
        double need = plugin.levels().need(d.level);
        List<String> lines = new ArrayList<>();
        lines.add(" ");
        lines.add("&f직업 &e" + plugin.jobs().title(d));
        lines.add("&f레벨 &eLv." + d.level + " &7(" + String.format("%.1f", d.exp / need * 100) + "%)");
        lines.add("&f소지금 &e" + Text.money(d.money));
        lines.add("&f스탯 포인트 &a" + d.statPoints);
        lines.add("  ");
        lines.add("&f힘 &6" + (int) s.str + " &f민첩 &a" + (int) s.dex + " &f모험 &b" + (int) s.adv);
        lines.add("&f전투력 &e&l" + Text.num(Power.of(s)));
        lines.add("&f일일 의뢰 &a" + plugin.quests().completedCount(d) + "/3");
        lines.add("&f길드 &b" + (g == null ? "없음" : g.name));
        StringBuilder buffs = new StringBuilder();
        long now = System.currentTimeMillis();
        String[][] B = {{"buff_atk", "공격"}, {"buff_def", "수호"}, {"buff_speed", "질풍"}, {"buff_exp", "지혜"}};
        for (String[] b : B) {
            long left = (long) d.counter(b[0]) - now;
            if (left > 0) buffs.append("&d").append(b[1]).append(" &f").append(left / 60000).append(":").append(String.format("%02d", left / 1000 % 60)).append("  ");
        }
        lines.add(buffs.length() > 0 ? "&7📜 " + buffs.toString().trim() : " ");
        String gl = plugin.guide() == null ? null : plugin.guide().line(d);
        if (gl != null) lines.add(gl);
        for (int i = 0; i < LINES; i++) {
            Team t = sb.getTeam("l" + i);
            if (t != null) t.setPrefix(Text.c(i < lines.size() ? lines.get(i) : ""));
        }
    }

    public java.util.Collection<Scoreboard> boards() {
        return boards.values();
    }

    public void remove(Player p) {
        boards.remove(p.getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        remove(e.getPlayer());
    }

    public void shutdown() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (boards.containsKey(p.getUniqueId())) p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            p.setWalkSpeed(0.2f);
        }
        boards.clear();
    }
}
