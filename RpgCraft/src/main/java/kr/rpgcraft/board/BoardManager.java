package kr.rpgcraft.board;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.data.PlayerData;
import kr.rpgcraft.gui.Gui;
import kr.rpgcraft.gui.UiIcon;
import kr.rpgcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * v5.10.45 보드게임 (오락실) — /보드게임
 *  - 윷놀이 · 부루마블 · 인디언 포커 (v5.10.59 2~4명: 컴퓨터와 하거나 방을 만들어 함께, 빈자리는 컴퓨터 · 판 수 제한 없음)
 *  - 이기면(지더라도 조금) 보드 칩: 미니게임 코인과 따로 쌓이는 디지털 재화. 하루에 얻을 수 있는 칩은 board.daily-chip-cap 까지
 *  - 보드 칩 상점: 전용 칭호 · 대미지 스킨 · 장신구 (장신구 · 스킨 아이템은 거래 가능)
 *  - 야차(1대1 결투) 명성: 이기면 오르고 지면 내림 (Elo). 명성 등급 칭호 · 랭킹 (v5.10.59 랭킹 창은 /야차 로 옮김)
 */
public class BoardManager implements CommandExecutor {
    public enum BoardGame {
        YUT("윷놀이", BoardIcons.HUB_YUT, "윷을 던져 말 2개를 먼저 모두 내보내기", "윷 · 모는 한 번 더 · 상대 말을 잡으면 한 번 더 · 내 말은 업어서 같이"),
        MARBLE("부루마블", BoardIcons.HUB_MARBLE, "주사위를 굴려 도시를 사고 통행료 받기", "상대를 파산시키거나 20바퀴 뒤 재산이 많으면 승리"),
        POKER("인디언 포커", BoardIcons.HUB_POKER, "상대 카드는 보이고 내 카드는 안 보임", "베팅 · 다이로 심리전, 칩을 모두 따면 승리");

        public final String label, how, rule;
        public final int icon;

        BoardGame(String label, int icon, String how, String rule) {
            this.label = label;
            this.icon = icon;
            this.how = how;
            this.rule = rule;
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** 보드 칩 상점 칭호 (칭호 kind 6, idx = 순서) */
    public static final String[] TITLES = {"&6윷놀이 명인", "&a부루마블 재벌", "&b포커페이스", "&d행운의 주사위", "&c&l보드게임 왕"};
    private static final int[] TITLE_PRICE = {60, 60, 60, 120, 300};
    /** 명성 등급 (야차) */
    public static final String[] TIER_NAME = {"브론즈", "실버", "골드", "플래티넘", "다이아몬드", "마스터", "그랜드마스터"};
    public static final String[] TIER_COLOR = {"&6", "&7", "&e", "&3", "&b", "&d", "&c"};
    private static final int[] TIER_MIN = {0, 1100, 1250, 1400, 1550, 1700, 1700};

    private final RpgCraft plugin;
    private final File fameFile;
    private final YamlConfiguration fame;

    public BoardManager(RpgCraft plugin) {
        this.plugin = plugin;
        this.fameFile = new File(plugin.getDataFolder(), "fame.yml");
        this.fame = YamlConfiguration.loadConfiguration(fameFile);
    }

    public RpgCraft plugin() {
        return plugin;
    }

    // =================================================================== 보드 칩
    public static final String CHIP = "coin_board";

    public long chips(PlayerData d) {
        return Math.round(d.counter(CHIP));
    }

    private long today() {
        return LocalDate.now(ZoneId.of(plugin.getConfig().getString("events.timezone", "Asia/Seoul"))).toEpochDay();
    }

    private long earnedToday(PlayerData d) {
        if (d.counter("board_chip_day") != today()) {
            d.counters.put("board_chip_day", (double) today());
            d.counters.put("board_chip_today", 0.0);
        }
        return Math.round(d.counter("board_chip_today"));
    }

    public int dailyCap() {
        return plugin.getConfig().getInt("board.daily-chip-cap", 60);
    }

    /** 게임 끝 보상. 하루 상한을 넘으면 칩은 없고 게임은 계속 할 수 있음 */
    public void reward(Player p, BoardGame g, boolean win) {
        PlayerData d = plugin.data().get(p);
        long want = plugin.getConfig().getLong("board.reward." + g.key() + (win ? ".win" : ".lose"), win ? (g == BoardGame.MARBLE ? 12 : 8) : 2);
        long room = Math.max(0, dailyCap() - earnedToday(d));
        long got = Math.min(want, room);
        if (got > 0) {
            d.counters.merge(CHIP, (double) got, Double::sum);
            d.counters.merge("board_chip_today", (double) got, Double::sum);
        }
        d.counters.merge("board_" + (win ? "win_" : "lose_") + g.key(), 1.0, Double::sum);
        if (plugin.seasonPass() != null) plugin.seasonPass().add(p, win ? 12 : 5, "보드게임");
        plugin.data().save(d);
        String tail = got > 0 ? " &d보드 칩 +" + got + " &7(보유 " + chips(d) + " · 오늘 " + earnedToday(d) + "/" + dailyCap() + ")"
                : " &7(오늘 얻을 수 있는 칩을 모두 받았습니다 · 자정에 초기화)";
        Text.msg(p, (win ? "&a&l승리! " : "&7패배… ") + "&f" + g.label + tail);
    }

    // =================================================================== 화면
    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (s instanceof Player p) {
            if (a.length > 0 && (a[0].equals("상점") || a[0].equalsIgnoreCase("shop"))) new ShopGui(p).open(p);
            else if (a.length > 1 && (a[0].equals("참가") || a[0].equalsIgnoreCase("join"))) join(p, Text.parseInt(a[1], -1));
            else new Hub(p).open(p);
        }
        return true;
    }

    public void openHub(Player p) {
        new Hub(p).open(p);
    }

    /** 게임 시작: players = 사람 (앞자리부터), seats = 전체 자리 (나머지는 컴퓨터) */
    public void start(List<Player> players, int seats, BoardGame g) {
        BoardGameBase game = switch (g) {
            case YUT -> new YutGame(this, players, seats);
            case MARBLE -> new MarbleGame(this, players, seats);
            case POKER -> new PokerGame(this, players, seats);
        };
        for (Player q : players) q.playSound(q.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.2f);
        game.begin();
    }

    // =================================================================== 방 (여러 명이 함께) — v5.10.59
    private static final class Room {
        int id;
        BoardGame game;
        UUID host;
        final List<UUID> members = new ArrayList<>();
        int bots;
        long created = System.currentTimeMillis();
    }

    private final Map<Integer, Room> rooms = new LinkedHashMap<>();
    private int nextRoom = 1;

    private void purgeRooms() {
        long ttl = plugin.getConfig().getLong("board.room-minutes", 10) * 60_000L;
        rooms.values().removeIf(r -> System.currentTimeMillis() - r.created > ttl || Bukkit.getPlayer(r.host) == null);
        for (Room r : rooms.values()) r.members.removeIf(u -> Bukkit.getPlayer(u) == null);
    }

    private Room roomOf(UUID u) {
        purgeRooms();
        for (Room r : rooms.values()) if (r.members.contains(u)) return r;
        return null;
    }

    private void createRoom(Player p, BoardGame g) {
        Room old = roomOf(p.getUniqueId());
        if (old != null) leaveRoom(p, old);
        Room r = new Room();
        r.id = nextRoom++;
        r.game = g;
        r.host = p.getUniqueId();
        r.members.add(p.getUniqueId());
        rooms.put(r.id, r);
        announce(r, p);
        new RoomGui(p, r).open(p);
    }

    /** 다른 사람들에게 채팅으로 [참가] 버튼 */
    private void announce(Room r, Player host) {
        var msg = new net.md_5.bungee.api.chat.ComponentBuilder("");
        msg.append(net.md_5.bungee.api.chat.TextComponent.fromLegacyText(Text.c("&d&l[보드게임] &f" + Text.name(host) + "&f님이 &e" + r.game.label + " &f방을 열었습니다 &7(" + r.members.size() + "/4) ")));
        net.md_5.bungee.api.chat.TextComponent btn = new net.md_5.bungee.api.chat.TextComponent(net.md_5.bungee.api.chat.TextComponent.fromLegacyText(Text.c("&a&l[참가]")));
        btn.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/보드게임 참가 " + r.id));
        btn.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT, new net.md_5.bungee.api.chat.hover.content.Text(Text.c("&e클릭하면 방에 들어갑니다"))));
        msg.append(btn, net.md_5.bungee.api.chat.ComponentBuilder.FormatRetention.NONE);
        var built = msg.create();
        for (Player o : Bukkit.getOnlinePlayers()) if (!r.members.contains(o.getUniqueId())) o.spigot().sendMessage(built);
    }

    private void join(Player p, int id) {
        purgeRooms();
        Room r = rooms.get(id);
        if (r == null) { Text.msg(p, "&c없거나 이미 시작한 방입니다."); return; }
        if (r.members.contains(p.getUniqueId())) { new RoomGui(p, r).open(p); return; }
        if (r.members.size() + r.bots >= 4) { Text.msg(p, "&c방이 가득 찼습니다. (최대 4명)"); return; }
        Room old = roomOf(p.getUniqueId());
        if (old != null) leaveRoom(p, old);
        r.members.add(p.getUniqueId());
        for (UUID u : r.members) {
            Player q = Bukkit.getPlayer(u);
            if (q != null && q != p) Text.actionBar(q, "&a" + Text.name(p) + "님이 방에 들어왔습니다 &7(" + r.members.size() + "명)");
        }
        refreshRoom(r);
        new RoomGui(p, r).open(p);
    }

    private void leaveRoom(Player p, Room r) {
        r.members.remove(p.getUniqueId());
        if (r.host.equals(p.getUniqueId())) {
            rooms.remove(r.id);
            for (UUID u : r.members) {
                Player q = Bukkit.getPlayer(u);
                if (q != null) { Text.msg(q, "&7방장이 나가서 방이 닫혔습니다."); if (q.getOpenInventory().getTopInventory().getHolder() instanceof RoomGui) new Hub(q).open(q); }
            }
            return;
        }
        refreshRoom(r);
    }

    /** 방 창을 보고 있는 사람들 창을 새로 그림 */
    private void refreshRoom(Room r) {
        for (UUID u : r.members) {
            Player q = Bukkit.getPlayer(u);
            if (q != null && q.getOpenInventory().getTopInventory().getHolder() instanceof RoomGui rg && rg.room == r) new RoomGui(q, r).open(q);
        }
    }

    private void startRoom(Player p, Room r) {
        purgeRooms();
        if (!r.host.equals(p.getUniqueId())) return;
        List<Player> ps = new ArrayList<>();
        for (UUID u : r.members) {
            Player q = Bukkit.getPlayer(u);
            if (q != null && !BoardGameBase.isView(q.getOpenInventory().getTopInventory().getHolder())) ps.add(q);
        }
        int seats = ps.size() + r.bots;
        if (seats < 2) { Text.msg(p, "&c2명 이상이어야 시작할 수 있습니다. &7(사람을 기다리거나 컴퓨터를 추가하세요)"); return; }
        rooms.remove(r.id);
        start(ps, Math.min(4, seats), r.game);
    }

    private class GameMenu extends Gui {
        GameMenu(Player p, BoardGame g) {
            super(4, "&8" + g.label);
            for (int k = 1; k <= 3; k++) {
                int seats = k + 1;
                set(9 + k, BoardIcons.of(BoardIcons.ROOM_BOT, k, "&f&l컴퓨터 " + k + "명과 하기", "&7나 + 컴퓨터 " + k + " (" + seats + "명 게임)", "&e▶ 클릭하여 바로 시작"),
                        e -> start(List.of(p), seats, g));
            }
            set(15, BoardIcons.of(BoardIcons.ROOM, "&a&l방 만들기", "&7다른 사람들과 함께 (최대 4명)", "&7채팅으로 [참가] 버튼이 퍼짐 · 빈자리는 컴퓨터로 채울 수 있음", "&e▶ 클릭"), e -> createRoom(p, g));
            purgeRooms();
            int slot = 19;
            for (Room r : rooms.values()) {
                if (r.game != g || slot > 25) continue;
                Player h = Bukkit.getPlayer(r.host);
                List<String> lore = new ArrayList<>();
                for (UUID u : r.members) { Player q = Bukkit.getPlayer(u); if (q != null) lore.add("&f● " + Text.name(q)); }
                if (r.bots > 0) lore.add("&7+ 컴퓨터 " + r.bots);
                lore.add("");
                lore.add(r.members.size() + r.bots >= 4 ? "&c가득 참" : "&e▶ 클릭하여 참가");
                int id = r.id;
                set(slot++, BoardIcons.of(BoardIcons.ROOM, Math.max(1, r.members.size()), "&e" + (h == null ? "?" : Text.name(h)) + "&f님의 방 &7(" + (r.members.size() + r.bots) + "/4)", lore), e -> join(p, id));
            }
            if (slot == 19) set(22, Gui.ui(UiIcon.LOCKED, false, "&7열린 방이 없습니다", "&7방을 만들어 친구를 불러 보세요"));
            set(31, BoardIcons.of(g.icon, "&e&l" + g.label, "&7" + g.how, "&7" + g.rule, "&72 ~ 4명"));
            fill(0, 35);
        }
    }

    private class RoomGui extends Gui {
        final Room room;

        RoomGui(Player p, Room r) {
            super(3, "&8" + r.game.label + " 방 #" + r.id);
            this.room = r;
            boolean host = r.host.equals(p.getUniqueId());
            int i = 0;
            for (UUID u : r.members) {
                Player q = Bukkit.getPlayer(u);
                if (q == null) continue;
                set(10 + i, BoardIcons.of(BoardIcons.TOK_SEAT + i, BoardGameBase.SEAT_COLOR[i] + "&l" + Text.name(q) + (u.equals(r.host) ? " &6(방장)" : ""), "&7" + BoardGameBase.SEAT_NAME[i] + " 자리"));
                i++;
            }
            for (int b = 0; b < r.bots && i < 4; b++, i++)
                set(10 + i, BoardIcons.of(BoardIcons.ROOM_BOT, BoardGameBase.SEAT_COLOR[i] + "&l컴퓨터", "&7" + BoardGameBase.SEAT_NAME[i] + " 자리"));
            for (; i < 4; i++) set(10 + i, Gui.ui(UiIcon.LOCKED, false, "&7빈자리", "&7/보드게임 참가 " + r.id));
            if (host) {
                set(15, BoardIcons.of(BoardIcons.ROOM_BOT, "&f컴퓨터 추가", r.members.size() + r.bots >= 4 ? "&c자리가 없음" : "&e▶ 클릭"), e -> {
                    if (r.members.size() + r.bots < 4) r.bots++;
                    refreshRoom(r);
                });
                set(16, BoardIcons.of(BoardIcons.FOLD, "&f컴퓨터 빼기", r.bots > 0 ? "&e▶ 클릭" : "&7컴퓨터 없음"), e -> {
                    if (r.bots > 0) r.bots--;
                    refreshRoom(r);
                });
                int total = r.members.size() + r.bots;
                set(22, BoardIcons.of(BoardIcons.ROOM_START, total >= 2 ? "&a&l게임 시작! &7(" + total + "명)" : "&7시작하려면 2명 이상", "&7사람 " + r.members.size() + " · 컴퓨터 " + r.bots), e -> startRoom(p, r));
                set(24, BoardIcons.of(BoardIcons.ROOM, "&e다시 알리기", "&7채팅으로 [참가] 버튼을 한 번 더"), e -> { announce(r, p); Text.actionBar(p, "&a방을 다시 알렸습니다."); });
            } else set(22, BoardIcons.of(BoardIcons.ROOM_START, "&7방장이 시작하기를 기다리는 중…", "&7사람 " + r.members.size() + " · 컴퓨터 " + r.bots));
            set(18, BoardIcons.of(BoardIcons.FOLD, "&c방 나가기"), e -> { leaveRoom(p, r); new Hub(p).open(p); });
            fill(0, 26);
        }
    }

    private class Hub extends Gui {
        Hub(Player p) {
            super(5, "&8보드게임 · 오락실");
            PlayerData d = plugin.data().get(p);
            int[] at = {11, 13, 15};
            for (BoardGame g : BoardGame.values()) {
                int w = (int) d.counter("board_win_" + g.key()), lo = (int) d.counter("board_lose_" + g.key());
                set(at[g.ordinal()], BoardIcons.of(g.icon, "&e&l" + g.label + " &a&lNEW", "&7" + g.how, "&7" + g.rule, "",
                        "&f전적 &a" + w + "승 &c" + lo + "패", "&f보상 &d보드 칩 &7(승리 " + plugin.getConfig().getLong("board.reward." + g.key() + ".win", g == BoardGame.MARBLE ? 12 : 8)
                                + " · 패배 " + plugin.getConfig().getLong("board.reward." + g.key() + ".lose", 2) + ")", "", "&f2 ~ 4명 &7(빈자리는 컴퓨터)", "&e▶ 클릭: 컴퓨터와 하기 · 방 만들기 · 참가"), e -> new GameMenu(p, g).open(p));
            }
            set(29, BoardIcons.of(BoardIcons.BOARD_CHIP, (int) Math.max(1, Math.min(64, chips(d))), "&d&l보드 칩 상점",
                    "&f보유 &d" + chips(d) + " 보드 칩", "&7오늘 얻은 칩 " + earnedToday(d) + " / " + dailyCap(),
                    "", "&7전용 칭호 · 대미지 스킨 · 장신구", "&e▶ 클릭"), e -> new ShopGui(p).open(p));
            set(33, Gui.ui(UiIcon.HELP, true, "&f도움말", "&7보드게임은 판 수 제한 없이 즐길 수 있음", "&7방을 만들어 2~4명이 함께 (빈자리는 컴퓨터)", "&7야차(1대1 결투) 명성 랭킹은 &e/야차",
                    "&7보드 칩은 하루 " + dailyCap() + "개까지 얻을 수 있음", "&7창을 닫으면 기권 (보상 없음 · 자리는 컴퓨터가 이어서)",
                    "&7미니게임(/미니게임) 코인과는 따로 쌓임"));
            fill(0, 44);
        }
    }

    // =================================================================== 보드 칩 상점
    private record Offer(String item, int amount, long price, int limit) {}

    private List<Offer> offers() {
        List<Offer> out = new ArrayList<>();
        out.add(new Offer("dmg_skin_candy", 1, 150, 1));
        out.add(new Offer("dmg_skin_toxic", 1, 220, 1));
        for (String k : new String[]{"ring", "neck", "ear"}) out.add(new Offer("acc_" + k + "_1", 1, 25, 0));
        for (String k : new String[]{"ring", "neck", "ear"}) out.add(new Offer("acc_" + k + "_2", 1, 90, 0));
        out.add(new Offer("pet_snack", 3, 15, 0));
        return out;
    }

    private class ShopGui extends Gui {
        ShopGui(Player p) {
            super(5, "&8보드 칩 상점");
            PlayerData d = plugin.data().get(p);
            set(4, BoardIcons.of(BoardIcons.BOARD_CHIP, (int) Math.max(1, Math.min(64, chips(d))), "&d&l보유 보드 칩 &f" + chips(d),
                    "&7보드게임에서 이기면 얻음 (/보드게임)"));
            // 1줄: 칭호
            int kind = (int) d.counter("title_kind"), sel = (int) d.counter("title_idx");
            for (int i = 0; i < TITLES.length; i++) {
                int idx = i;
                boolean own = d.counter("btitle_" + i) > 0, eq = own && kind == 6 && sel == i;
                set(11 + i, Gui.ui(UiIcon.TAG, own, TITLES[i] + (eq ? " &a(장착 중)" : ""), "&7보드게임 전용 칭호 (채팅 · 탭에 표시)", "",
                        own ? (eq ? "&b클릭하여 해제" : "&e▶ 클릭하여 장착") : "&f가격 &d" + TITLE_PRICE[i] + " 보드 칩", own ? "" : "&e▶ 클릭하여 구매"), e -> {
                    PlayerData pd = plugin.data().get(p);
                    if (pd.counter("btitle_" + idx) > 0) {
                        if (plugin.content() != null) plugin.content().equipTitle(pd, 6, idx);
                    } else {
                        if (chips(pd) < TITLE_PRICE[idx]) { Text.actionBar(p, "&c보드 칩이 부족합니다. &7(" + chips(pd) + " / " + TITLE_PRICE[idx] + ")"); p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f); return; }
                        pd.counters.merge(CHIP, (double) -TITLE_PRICE[idx], Double::sum);
                        pd.counters.put("btitle_" + idx, 1.0);
                        if (plugin.content() != null) plugin.content().equipTitle(pd, 6, idx);
                        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.4f);
                        Text.msg(p, "&d✦ 칭호 획득! " + TITLES[idx] + " &7(장착함 · 업적 · 칭호 메뉴에서도 바꿀 수 있음)");
                    }
                    plugin.data().save(pd);
                    new ShopGui(p).open(p);
                });
            }
            // 2 · 3줄: 아이템
            List<Offer> os = offers();
            int slot = 19;
            for (Offer o : os) {
                if (plugin.items().get(o.item()) == null) continue;
                ItemStack icon = plugin.items().create(o.item(), o.amount());
                if (icon == null) continue;
                ItemMeta m = icon.getItemMeta();
                List<String> lore = m.getLore() == null ? new ArrayList<>() : new ArrayList<>(m.getLore());
                lore.add("");
                lore.add(Text.c("&f가격 &d" + o.price() + " 보드 칩"));
                int bought = (int) d.counter("bshop_" + o.item());
                if (o.limit() > 0) lore.add(Text.c("&7구매 제한 " + bought + " / " + o.limit()));
                lore.add(Text.c("&e▶ 클릭하여 구매"));
                m.setLore(lore);
                icon.setItemMeta(m);
                set(slot, icon, e -> buy(p, o));
                slot++;
                if (slot % 9 == 8) slot += 2;
            }
            fill(0, 44);
        }
    }

    private void buy(Player p, Offer o) {
        PlayerData d = plugin.data().get(p);
        int bought = (int) d.counter("bshop_" + o.item());
        if (o.limit() > 0 && bought >= o.limit()) { Text.actionBar(p, "&c구매 제한에 도달했습니다."); return; }
        if (chips(d) < o.price()) { Text.actionBar(p, "&c보드 칩이 부족합니다. &7(" + chips(d) + " / " + o.price() + ")"); p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f); return; }
        ItemStack it = plugin.items().create(o.item(), o.amount());
        if (it == null) return;
        d.counters.merge(CHIP, (double) -o.price(), Double::sum);
        d.counters.put("bshop_" + o.item(), (double) (bought + 1));
        for (ItemStack left : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
        plugin.data().save(d);
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
        Text.actionBar(p, "&a구매: " + plugin.items().get(o.item()).name + " x" + o.amount() + " &7(-" + o.price() + " 보드 칩)");
        new ShopGui(p).open(p);
    }

    /** 칭호 kind 6 표시 (가진 것만) */
    public String boardTitle(PlayerData d, int idx) {
        return idx >= 0 && idx < TITLES.length && d.counter("btitle_" + idx) > 0 ? Text.c(TITLES[idx] + "&d") : "";
    }

    // =================================================================== 야차 명성
    public int fame(UUID u) {
        return fame.getInt(u + ".fame", 1000);
    }

    private List<String> ranked() {
        List<String> ids = new ArrayList<>(fame.getKeys(false));
        ids.removeIf(k -> fame.getInt(k + ".wins") + fame.getInt(k + ".losses") == 0);
        ids.sort((x, y) -> Integer.compare(fame.getInt(y + ".fame", 1000), fame.getInt(x + ".fame", 1000)));
        return ids;
    }

    /** 0 브론즈 ~ 5 마스터, 6 그랜드마스터 (명성 1700 이상 중 1위) */
    public int tier(UUID u) {
        int f = fame(u);
        int t = 0;
        for (int i = 0; i < 6; i++) if (f >= TIER_MIN[i]) t = i;
        if (t == 5) {
            List<String> r = ranked();
            if (!r.isEmpty() && r.get(0).equals(u.toString())) t = 6;
        }
        return t;
    }

    public String tierLabel(UUID u) {
        int t = tier(u);
        return TIER_COLOR[t] + TIER_NAME[t];
    }

    private final Map<String, Integer> pairToday = new HashMap<>();
    private long pairDay;

    /** 야차 승패 반영. 반환: [승자 변화, 패자 변화] (같은 상대와 하루 3판 넘으면 0) */
    public int[] recordDuel(Player w, Player l) {
        String pairKey = "duelpair_" + (w.getUniqueId().compareTo(l.getUniqueId()) < 0 ? w.getUniqueId() + "_" + l.getUniqueId() : l.getUniqueId() + "_" + w.getUniqueId());
        if (pairDay != today()) { pairDay = today(); pairToday.clear(); }
        int played = pairToday.merge(pairKey, 1, Integer::sum) - 1;
        for (Player q : new Player[]{w, l}) {
            String k = q.getUniqueId().toString();
            fame.set(k + ".name", q.getName());
        }
        int limit = plugin.getConfig().getInt("duel.fame-pair-daily", 3);
        int[] delta = {0, 0};
        if (played < limit) {
            int fw = fame(w.getUniqueId()), fl = fame(l.getUniqueId());
            double ew = 1.0 / (1 + Math.pow(10, (fl - fw) / 400.0));
            int k = plugin.getConfig().getInt("duel.fame-k", 32);
            int gain = (int) Math.max(1, Math.round(k * (1 - ew)));
            delta[0] = gain;
            delta[1] = -Math.min(gain, Math.max(0, fl - 800));   // 800 아래로는 안 내려감
            int beforeW = tier(w.getUniqueId()), beforeL = tier(l.getUniqueId());
            fame.set(w.getUniqueId() + ".fame", fw + delta[0]);
            fame.set(l.getUniqueId() + ".fame", fl + delta[1]);
            int afterW = tier(w.getUniqueId()), afterL = tier(l.getUniqueId());
            if (afterW > beforeW) Text.announce(Text.PREFIX + Text.c("&c⚔ &e" + Text.name(w) + "&f님이 야차 명성 " + TIER_COLOR[afterW] + "&l" + TIER_NAME[afterW] + "&f 등급이 되었습니다!"));
            if (afterL < beforeL) Text.msg(l, "&7명성 등급이 " + TIER_COLOR[afterL] + TIER_NAME[afterL] + "&7(으)로 내려갔습니다.");
        }
        fame.set(w.getUniqueId() + ".wins", fame.getInt(w.getUniqueId() + ".wins") + 1);
        fame.set(l.getUniqueId() + ".losses", fame.getInt(l.getUniqueId() + ".losses") + 1);
        save();
        return delta;
    }

    private void save() {
        try { fame.save(fameFile); } catch (IOException ex) { plugin.getLogger().warning("fame.yml 저장 실패: " + ex.getMessage()); }
    }

    /** 칭호 kind 7: 지금 명성 등급 */
    public String fameTitle(PlayerData d) {
        if (fame.getInt(d.uuid + ".wins") + fame.getInt(d.uuid + ".losses") == 0) return "";
        int t = tier(d.uuid);
        return Text.c(TIER_COLOR[t] + TIER_NAME[t] + " 결투가&d");
    }

    public class FameGui extends Gui {
        public FameGui(Player p) {
            super(6, "&8야차 명성 랭킹");
            List<String> r = ranked();
            int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
            for (int i = 0; i < Math.min(slots.length, r.size()); i++) {
                String k = r.get(i);
                UUID u = UUID.fromString(k);
                int t = tier(u);
                set(slots[i], BoardIcons.of(BoardIcons.TIER + t, Math.min(64, i + 1), (i < 3 ? new String[]{"&6&l1위", "&f&l2위", "&c&l3위"}[i] : "&7" + (i + 1) + "위") + " &f" + fame.getString(k + ".name", "?"),
                        TIER_COLOR[t] + TIER_NAME[t] + " &f명성 " + fame.getInt(k + ".fame", 1000),
                        "&7" + fame.getInt(k + ".wins") + "승 " + fame.getInt(k + ".losses") + "패"));
            }
            int my = r.indexOf(p.getUniqueId().toString());
            int t = tier(p.getUniqueId());
            PlayerData d = plugin.data().get(p);
            boolean eq = (int) d.counter("title_kind") == 7;
            boolean played = fame.getInt(p.getUniqueId() + ".wins") + fame.getInt(p.getUniqueId() + ".losses") > 0;
            List<String> lore = new ArrayList<>(List.of("&f명성 " + fame(p.getUniqueId()) + " &7· " + (my < 0 ? "순위 없음" : (my + 1) + "위"),
                    "&7" + fame.getInt(p.getUniqueId() + ".wins") + "승 " + fame.getInt(p.getUniqueId() + ".losses") + "패", ""));
            for (int i = 0; i < 6; i++) lore.add(TIER_COLOR[i] + TIER_NAME[i] + " &7명성 " + TIER_MIN[i] + "+");
            lore.add(TIER_COLOR[6] + TIER_NAME[6] + " &7마스터 중 1위");
            lore.add("");
            lore.add(played ? (eq ? "&b명성 칭호 장착 중 (클릭해서 해제)" : "&e▶ 클릭: 명성 등급을 칭호로 장착") : "&8야차를 한 번 이상 해야 칭호를 쓸 수 있음");
            set(49, BoardIcons.of(BoardIcons.TIER + t, "&e&l내 명성 " + TIER_COLOR[t] + TIER_NAME[t], lore), e -> {
                if (!played || plugin.content() == null) return;
                plugin.content().equipTitle(plugin.data().get(p), 7, 0);
                new FameGui(p).open(p);
            });
            set(4, BoardIcons.of(BoardIcons.HUB_DUEL, "&c&l야차 명성", "&7/야차 <닉네임> 으로 1대1 결투", "&7이긴 사람은 명성 +, 진 사람은 명성 -",
                    "&7강한 상대를 이길수록 많이 오름", "&7같은 상대와는 하루 " + plugin.getConfig().getInt("duel.fame-pair-daily", 3) + "판까지만 반영"));
            fill(0, 53);
        }
    }

    /** 칭호 장착 등 다른 곳에서 쓰는 등급 이름 (없으면 "") */
    public Collection<String> fameKeys() {
        return fame.getKeys(false);
    }

    public void shutdown() {
        save();
        for (Player p : Bukkit.getOnlinePlayers())
            if (BoardGameBase.isView(p.getOpenInventory().getTopInventory().getHolder())) p.closeInventory();
    }
}
