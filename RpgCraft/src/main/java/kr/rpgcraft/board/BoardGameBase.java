package kr.rpgcraft.board;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.gui.Gui;
import kr.rpgcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * 보드게임 공통 (v5.10.59 여러 명이 함께): 자리(2~4) 마다 사람 또는 컴퓨터.
 * 게임 상태는 하나, 창은 사람마다 따로 (View) — 같은 판을 보지만 "내 말 · 내 카드" 는 자기 자리 기준으로 그린다 (인디언 포커는 자기 카드만 가려짐).
 * 차례인 자리의 사람만 누를 수 있고, 컴퓨터 자리는 스스로 둔다. 창을 닫거나 나가면 그 자리는 컴퓨터가 이어서 둠 (나간 사람은 기권 · 보상 없음).
 */
public abstract class BoardGameBase {
    public static final String[] SEAT_COLOR = {"&c", "&9", "&a", "&e"};
    public static final String[] SEAT_NAME = {"빨강", "파랑", "초록", "노랑"};

    protected final BoardManager mgr;
    protected final RpgCraft plugin;
    protected final BoardManager.BoardGame game;
    protected final int seats;
    /** 자리별 사람 (null = 컴퓨터) */
    protected final Player[] human;
    private final String[] seatName;
    private final Map<UUID, View> views = new LinkedHashMap<>();
    private final String title;
    protected BukkitTask task;
    protected boolean over;
    protected int ticks;
    /** 지금 차례인 자리 */
    protected int turn;
    /** 애니메이션 · 컴퓨터 생각 중 (누르기 막기) */
    protected boolean busy;

    protected BoardGameBase(BoardManager mgr, List<Player> players, int seatCount, BoardManager.BoardGame game, String title) {
        this.mgr = mgr;
        this.plugin = mgr.plugin();
        this.game = game;
        this.title = title;
        this.seats = Math.max(2, Math.min(4, seatCount));
        this.human = new Player[seats];
        this.seatName = new String[seats];
        int ai = 0;
        for (int i = 0; i < seats; i++) {
            human[i] = i < players.size() ? players.get(i) : null;
            seatName[i] = human[i] != null ? Text.name(human[i]) : "컴퓨터" + (seats - players.size() > 1 ? " " + (++ai) : "");
        }
    }

    /** 창 하나 (사람마다) */
    public class View extends Gui {
        final int seat;
        final Player viewer;

        View(Player viewer, int seat) {
            super(6, title + " &7- " + SEAT_COLOR[seat] + "나: " + SEAT_NAME[seat]);
            this.viewer = viewer;
            this.seat = seat;
        }

        /** 버튼: 누른 사람이 이 창 주인이고, 게임이 진행 중일 때만 */
        public void btn(int slot, org.bukkit.inventory.ItemStack it, Runnable r) {
            set(slot, it, e -> { if (!over && e.getWhoClicked() == viewer) r.run(); });
        }

        @Override
        public void onClose(InventoryCloseEvent e) {
            if (e.getPlayer() == viewer) Bukkit.getScheduler().runTask(plugin, () -> {
                if (viewer.isOnline() && viewer.getOpenInventory().getTopInventory().getHolder() == this) return;   // 다시 그린 것
                leave(seat);
            });
        }
    }

    protected abstract void setup();

    /** 이 창(자리 v.seat 기준)을 그림 */
    protected abstract void render(View v);

    /** 컴퓨터 자리 차례가 되면 호출 (게임마다) */
    protected abstract void aiTurn();

    protected void tick() {
    }

    public void begin() {
        setup();
        for (int i = 0; i < seats; i++) {
            if (human[i] == null) continue;
            View v = new View(human[i], i);
            views.put(human[i].getUniqueId(), v);
        }
        refresh();
        for (View v : views.values()) v.open(v.viewer);
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (over) return;
            for (int i = 0; i < seats; i++) if (human[i] != null && !human[i].isOnline()) leave(i);
            ticks += 2;
            tick();
        }, 10L, 2L);
        if (isAI(turn)) startAi();
    }

    /** 모든 창을 다시 그림 */
    protected void refresh() {
        for (View v : views.values()) {
            v.clearButtons();
            v.getInventory().clear();
            render(v);
            v.fill(0, 53);
        }
    }

    public boolean isAI(int s) {
        return human[s] == null;
    }

    public String who(int s) {
        return SEAT_COLOR[s] + seatName[s];
    }

    /** 로그용 (색 없이) */
    public String plain(int s) {
        return seatName[s];
    }

    /** 사람 자리 수 */
    protected int humans() {
        int c = 0;
        for (Player h : human) if (h != null) c++;
        return c;
    }

    /** 사람이 나감 → 그 자리는 컴퓨터가 이어서 둠 */
    private void leave(int s) {
        if (over || human[s] == null) return;
        Player h = human[s];
        views.remove(h.getUniqueId());
        human[s] = null;
        seatName[s] = seatName[s] + "(컴퓨터)";
        if (h.isOnline()) Text.msg(h, "&7" + game.label + " 에서 나갔습니다. &8(기권 · 보상 없음, 자리는 컴퓨터가 이어서 둠)");
        if (humans() == 0) {
            over = true;
            if (task != null) task.cancel();
            return;
        }
        broadcast("&7" + Text.strip(Text.c(who(s))) + " 님이 나가서 컴퓨터가 대신 둡니다.");
        refresh();
        if (s == turn && !busy) startAi();
    }

    /** 차례 넘기기 (다음 자리, skip 이 true 인 자리는 건너뜀) */
    protected void nextTurn(java.util.function.IntPredicate skip) {
        for (int k = 1; k <= seats; k++) {
            int n = (turn + k) % seats;
            if (skip == null || !skip.test(n)) { turn = n; break; }
        }
        refresh();
        if (isAI(turn)) startAi();
    }

    /** 컴퓨터 차례 시작 (조금 쉬었다가) */
    protected void startAi() {
        busy = true;
        refresh();
        later(20, () -> { busy = false; aiTurn(); });
    }

    /** 사람 자리 s 가 지금 둘 수 있는지 */
    protected boolean myMove(View v) {
        return !over && !busy && v.seat == turn && !isAI(turn);
    }

    protected void later(long delay, Runnable r) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (!over) r.run(); }, delay);
    }

    protected void sound(Sound s, float vol, float pitch) {
        for (Player h : human) if (h != null && h.isOnline()) h.playSound(h.getLocation(), s, vol, pitch);
    }

    protected void broadcast(String msg) {
        for (Player h : human) if (h != null && h.isOnline()) Text.actionBar(h, msg);
    }

    protected void say(View v, String msg) {
        Text.actionBar(v.viewer, msg);
    }

    /** 끝: winner 자리가 이김 (나머지 사람은 패배) */
    protected void finish(int winner, String why) {
        if (over) return;
        refresh();
        over = true;
        if (task != null) task.cancel();
        for (int i = 0; i < seats; i++) {
            Player h = human[i];
            if (h == null || !h.isOnline()) continue;
            boolean win = i == winner;
            mgr.reward(h, game, win);
            if (win) {
                h.sendTitle(Text.c("&a&l승리!"), Text.c("&7" + why), 0, 50, 10);
                h.playSound(h.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
            } else {
                h.sendTitle(Text.c("&c&l패배"), Text.c(who(winner) + " &7승리 · " + why), 0, 50, 10);
                h.playSound(h.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 0.9f);
            }
        }
        List<View> vs = new ArrayList<>(views.values());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (View v : vs) if (v.viewer.isOnline() && v.viewer.getOpenInventory().getTopInventory().getHolder() == v) mgr.openHub(v.viewer);
        }, 70L);
    }

    /** 이 창이 보드게임 창인지 (서버 종료 시 닫기) */
    public static boolean isView(Object holder) {
        return holder instanceof BoardGameBase.View;
    }

    /** 자리 아이콘 줄 (누가 무슨 색인지) */
    protected String seatLine(int s, int me) {
        return SEAT_COLOR[s] + "● " + seatName[s] + (s == me ? " &7(나)" : isAI(s) ? " &8(컴퓨터)" : "");
    }

    protected static void noop(InventoryClickEvent e) {
    }
}
