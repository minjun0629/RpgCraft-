package kr.rpgcraft.board;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * v5.10.45 윷놀이 (컴퓨터와 1대1). 창 왼쪽 6x6 이 윷판:
 *  바깥 20칸 (오른쪽 아래 = 출발 · 도착) + 모서리 지름길 (오른쪽 위 · 왼쪽 위 모서리에서 멈추면 대각선으로) + 가운데 방.
 *  말은 2개씩 (board.yut-pieces). 윷 · 모는 한 번 더, 상대 말을 잡아도 한 번 더, 내 말끼리 겹치면 업어서 같이 움직임.
 *  빽도는 한 칸 뒤로. 말이 출발점을 지나면 도착.
 */
public class YutGame extends BoardGameBase {
    // 칸 번호: 0~19 바깥 (0 = 출발 · 도착 모서리), 20 A (오른쪽 위 모서리 → 가운데), 21 가운데, 22 B (가운데 → 왼쪽 아래), 23 D (왼쪽 위 → 가운데), 24 E (가운데 → 출발)
    private static final int A = 20, CENTER = 21, B = 22, D = 23, E = 24, HOME = -1, GOAL = -2;
    private static final int[] SLOT = {50, 41, 32, 23, 14, 5, 4, 3, 2, 1, 0, 9, 18, 27, 36, 45, 46, 47, 48, 49, 13, 20, 37, 10, 40};
    private static final int[] CENTER_SLOTS = {20, 21, 29, 30};
    private static final String[] RES = {"도", "개", "걸", "윷", "모", "빽도"};

    private final int n;
    private final int[][] pos, prev;   // [0 = 나, 1 = 컴퓨터][말]
    private final List<Integer> queue = new ArrayList<>();   // 던진 결과 (1~5, -1 빽도)
    private int sel;            // 고른 결과
    private boolean myTurn = true, canThrow = true;
    private String log = "윷을 던지세요!";

    public YutGame(BoardManager mgr, Player p) {
        super(mgr, p, BoardManager.BoardGame.YUT, "&8윷놀이 &7- 나(빨강) vs 컴퓨터(파랑)");
        n = Math.max(1, Math.min(4, plugin.getConfig().getInt("board.yut-pieces", 2)));
        pos = new int[2][n];
        prev = new int[2][n];
        for (int[] a : pos) Arrays.fill(a, HOME);
        for (int[] a : prev) Arrays.fill(a, HOME);
    }

    // ------------------------------------------------------------------ 규칙
    private static int throwSticks() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int flats = 0;
        boolean marked = false;
        for (int i = 0; i < 4; i++) if (r.nextBoolean()) { flats++; if (i == 0) marked = true; }
        if (flats == 0) return 5;            // 모
        if (flats == 1 && marked) return -1; // 빽도 (표시한 가락 하나만 엎어짐)
        return flats;                        // 도 개 걸 윷
    }

    private static int resIdx(int r) {
        return r == -1 ? 5 : r - 1;
    }

    /** 한 칸 앞으로 (first = 이번 이동의 첫 걸음, from = 바로 전 칸) */
    private static int step(int at, int from, boolean first) {
        if (at == HOME) return 1;
        if (first && at == 5) return A;
        if (first && at == 10) return D;
        if (at == A) return CENTER;
        if (at == CENTER) return first ? E : from == A ? B : E;
        if (at == B) return 15;
        if (at == D) return CENTER;
        if (at == E) return 0;
        if (at == 0) return GOAL;   // 출발점에 멈춰 있던 말은 다음 걸음에 도착
        if (at == 19) return 0;
        return at + 1;
    }

    /** 앞으로 k 칸 → {도착 칸, 그 전 칸} (출발점을 지나치면 GOAL) */
    private static int[] walk(int at, int k) {
        int cur = at, from = HOME;
        for (int i = 0; i < k && cur != GOAL; i++) {
            int nx = step(cur, from, i == 0);
            from = cur;
            cur = nx;
        }
        return new int[]{cur, from};
    }

    private static int defaultPrev(int at) {
        if (at >= 1 && at <= 19) return at - 1;
        return switch (at) {
            case 0 -> 19;
            case A -> 5;
            case CENTER -> A;
            case B -> CENTER;
            case D -> 10;
            case E -> CENTER;
            default -> HOME;
        };
    }

    /** side 의 말 i 를 결과 r 로 옮겼을 때 도착 칸 (못 움직이면 null) */
    private int[] dest(int side, int i, int r) {
        int at = pos[side][i];
        if (at == GOAL) return null;
        if (r == -1) {
            if (at == HOME) return null;
            int back = prev[side][i] == HOME ? (at == 1 ? 0 : defaultPrev(at)) : prev[side][i];
            return new int[]{back, defaultPrev(back)};
        }
        return walk(at, r);
    }

    private List<Integer> group(int side, int i) {
        List<Integer> g = new ArrayList<>();
        int at = pos[side][i];
        if (at == HOME) { g.add(i); return g; }
        for (int j = 0; j < n; j++) if (pos[side][j] == at) g.add(j);
        return g;
    }

    /** 옮기고 잡았는지 */
    private boolean move(int side, int i, int r) {
        int[] d = dest(side, i, r);
        if (d == null) return false;
        List<Integer> g = group(side, i);
        for (int j : g) { pos[side][j] = d[0]; prev[side][j] = d[1]; }
        boolean caught = false;
        if (d[0] >= 0) {
            int o = 1 - side;
            for (int j = 0; j < n; j++) if (pos[o][j] == d[0]) { pos[o][j] = HOME; prev[o][j] = HOME; caught = true; }
        }
        return caught;
    }

    private boolean anyMovable(int side, int r) {
        for (int i = 0; i < n; i++) if (dest(side, i, r) != null) return true;
        return false;
    }

    private int finished(int side) {
        int c = 0;
        for (int i = 0; i < n; i++) if (pos[side][i] == GOAL) c++;
        return c;
    }

    private int home(int side) {
        int c = 0;
        for (int i = 0; i < n; i++) if (pos[side][i] == HOME) c++;
        return c;
    }

    private int firstHome(int side) {
        for (int i = 0; i < n; i++) if (pos[side][i] == HOME) return i;
        return -1;
    }

    // ------------------------------------------------------------------ 화면
    @Override
    protected void setup() {
        render();
    }

    private String where(int at) {
        if (at == GOAL) return "&a도착!";
        if (at == 0) return "출발점 (한 걸음 더 가면 도착)";
        if (at == CENTER) return "가운데 방";
        if (at == 5 || at == 10 || at == 15) return "모서리 (멈추면 지름길)";
        return at >= 20 ? "지름길" : "바깥 " + at + "번째 칸";
    }

    private void render() {
        // 판
        for (int at = 0; at < SLOT.length; at++) {
            int me = 0, ai = 0;
            for (int i = 0; i < n; i++) { if (pos[0][i] == at) me++; if (pos[1][i] == at) ai++; }
            int icon = me > 0 && ai > 0 ? BoardIcons.YUT_BOTH : me > 0 ? BoardIcons.YUT_ME : ai > 0 ? BoardIcons.YUT_AI
                    : at == CENTER ? BoardIcons.YUT_CENTER : (at == 0 || at == 5 || at == 10 || at == 15) ? BoardIcons.YUT_CORNER : BoardIcons.YUT_STATION;
            List<String> lore = new ArrayList<>();
            lore.add("&7" + where(at));
            if (me > 0) lore.add("&c내 말 " + me + "개" + (me > 1 ? " (업힘)" : ""));
            if (ai > 0) lore.add("&9컴퓨터 말 " + ai + "개");
            int myPiece = -1;
            for (int i = 0; i < n; i++) if (pos[0][i] == at) { myPiece = i; break; }
            if (myPiece >= 0 && myTurn && !queue.isEmpty() && !canThrowNow()) {
                int[] d = dest(0, myPiece, queue.get(sel));
                lore.add("");
                lore.add(d == null ? "&8이 결과로는 못 움직임" : "&e▶ 클릭: " + RES[resIdx(queue.get(sel))] + " → " + where(d[0]));
            }
            int amount = Math.max(1, Math.max(me, ai));
            String name = me > 0 ? "&c&l내 말" : ai > 0 ? "&9&l컴퓨터 말" : at == CENTER ? "&6가운데 방" : "&f윷판";
            int pi = myPiece;
            if (at == CENTER) {
                for (int s : CENTER_SLOTS) set(s, BoardIcons.of(icon, amount, name, lore), e -> clickPiece(pi));
            } else set(SLOT[at], BoardIcons.of(icon, amount, name, lore), e -> clickPiece(pi));
        }
        for (int s : new int[]{11, 12, 19, 22, 28, 31, 38, 39}) set(s, kr.rpgcraft.pack.PackManager.filler(org.bukkit.Material.GRAY_STAINED_GLASS_PANE), e -> { });
        // 오른쪽
        int mh = home(0), ah = home(1);
        List<String> hl = new ArrayList<>(List.of("&7아직 출발하지 않은 말 " + mh + "개"));
        if (mh > 0 && myTurn && !queue.isEmpty() && !canThrowNow()) {
            int r = queue.get(sel);
            hl.add(r == -1 ? "&8빽도로는 새 말을 낼 수 없음" : "&e▶ 클릭: " + RES[resIdx(r)] + " 로 새 말 출발");
        }
        set(7, BoardIcons.of(mh > 0 ? BoardIcons.YUT_HOME_ME : BoardIcons.YUT_STATION, Math.max(1, mh), "&c&l내 대기 말 &f" + mh, hl), e -> clickPiece(firstHome(0) >= 0 ? -100 : -1));
        set(8, BoardIcons.of(BoardIcons.YUT_GOAL, Math.max(1, finished(0)), "&c내 도착 &f" + finished(0) + " / " + n));
        set(16, BoardIcons.of(ah > 0 ? BoardIcons.YUT_HOME_AI : BoardIcons.YUT_STATION, Math.max(1, ah), "&9&l컴퓨터 대기 말 &f" + ah));
        set(17, BoardIcons.of(BoardIcons.YUT_GOAL, Math.max(1, finished(1)), "&9컴퓨터 도착 &f" + finished(1) + " / " + n));
        int[] qs = {25, 26, 34, 35};
        for (int k = 0; k < qs.length; k++) {
            if (k < queue.size()) {
                int idx = k;
                boolean on = k == sel;
                set(qs[k], BoardIcons.of(BoardIcons.YUT_RESULT + resIdx(queue.get(k)), (on ? "&e&l▶ " : "&f") + RES[resIdx(queue.get(k))],
                        on ? "&a지금 쓸 결과" : "&7클릭하면 이 결과를 먼저 씀"), e -> { if (myTurn && !busy) { sel = idx; render(); } });
            } else set(qs[k], kr.rpgcraft.pack.PackManager.filler(org.bukkit.Material.GRAY_STAINED_GLASS_PANE), e -> { });
        }
        boolean t = myTurn && canThrowNow();
        set(43, BoardIcons.of(t ? BoardIcons.YUT_THROW : BoardIcons.YUT_STATION, t ? "&e&l윷 던지기!" : myTurn ? "&7말을 옮기세요" : "&7컴퓨터 차례…",
                t ? "&7클릭하여 윷 던지기" : myTurn ? "&7오른쪽 결과를 골라 판의 내 말(빨강)을 클릭" : ""), e -> doThrow());
        set(44, BoardIcons.of(myTurn ? BoardIcons.YUT_ME : BoardIcons.YUT_AI, myTurn ? "&c&l내 차례" : "&9&l컴퓨터 차례", "&f" + log));
        set(52, kr.rpgcraft.gui.Gui.ui(kr.rpgcraft.gui.UiIcon.HELP, true, "&f규칙", "&7윷 · 모: 한 번 더 던짐", "&7상대 말을 잡으면 한 번 더",
                "&7내 말끼리 겹치면 업어서 함께 움직임", "&7오른쪽 위 · 왼쪽 위 모서리, 가운데에 멈추면 지름길로", "&7빽도: 한 칸 뒤로", "&7말 " + n + "개를 먼저 모두 내보내면 승리"));
        fill(0, 53);
    }

    private boolean canThrowNow() {
        return canThrow;
    }

    // ------------------------------------------------------------------ 내 차례
    private void doThrow() {
        if (over || busy || !myTurn || !canThrow) return;
        int r = throwSticks();
        queue.add(r);
        p.playSound(p.getLocation(), Sound.BLOCK_WOOD_HIT, 1f, 0.9f + ThreadLocalRandom.current().nextFloat() * 0.3f);
        log = "던짐: " + RES[resIdx(r)] + (r >= 4 ? " — 한 번 더!" : "");
        if (r >= 4) { p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f); render(); return; }
        canThrow = false;
        sel = 0;
        skipDead(0);
        render();
    }

    /** 움직일 수 없는 결과는 버림 · 다 쓰면 차례 넘김 */
    private void skipDead(int side) {
        queue.removeIf(r -> !anyMovable(side, r));
        if (sel >= queue.size()) sel = 0;
        if (queue.isEmpty() && !canThrow) endTurn(side);
    }

    /** idx: 판의 내 말 번호, -100 = 대기 말 */
    private void clickPiece(int idx) {
        if (over || busy || !myTurn || canThrow || queue.isEmpty()) return;
        int piece = idx == -100 ? firstHome(0) : idx;
        if (piece < 0) return;
        int r = queue.get(sel);
        if (dest(0, piece, r) == null) { say("&c이 결과로는 그 말을 움직일 수 없습니다."); return; }
        queue.remove(sel);
        sel = 0;
        boolean caught = move(0, piece, r);
        p.playSound(p.getLocation(), Sound.BLOCK_WOOD_PLACE, 1f, 1.2f);
        if (finished(0) >= n) { render(); finish(true, "말을 모두 내보냈습니다!"); return; }
        if (caught) {
            log = "상대 말을 잡았다! 한 번 더!";
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1.2f);
            canThrow = true;
        }
        skipDead(0);
        render();
    }

    private void endTurn(int side) {
        queue.clear();
        sel = 0;
        if (side == 0) {
            myTurn = false;
            canThrow = true;
            busy = true;
            render();
            later(20, this::aiStep);
        } else {
            myTurn = true;
            canThrow = true;
            busy = false;
            log = "내 차례 — 윷을 던지세요!";
            render();
        }
    }

    // ------------------------------------------------------------------ 컴퓨터
    private void aiStep() {
        if (over) return;
        if (canThrow) {
            int r = throwSticks();
            queue.add(r);
            p.playSound(p.getLocation(), Sound.BLOCK_WOOD_HIT, 0.8f, 0.8f);
            log = "컴퓨터: " + RES[resIdx(r)] + (r >= 4 ? " — 한 번 더!" : "");
            if (r < 4) canThrow = false;
            render();
            later(18, this::aiStep);
            return;
        }
        queue.removeIf(r -> !anyMovable(1, r));
        if (queue.isEmpty()) { endTurn(1); return; }
        // 결과와 말을 모두 따져 가장 좋은 수
        int bestR = -1, bestP = -1;
        double best = -1e9;
        for (int qi = 0; qi < queue.size(); qi++) {
            int r = queue.get(qi);
            Set<Integer> seen = new HashSet<>();
            for (int i = 0; i < n; i++) {
                int at = pos[1][i];
                if (at == GOAL || !seen.add(at)) continue;
                int[] d = dest(1, i, r);
                if (d == null) continue;
                double sc = score(i, d[0], r);
                if (sc > best) { best = sc; bestR = qi; bestP = i; }
            }
        }
        int r = queue.remove(bestR);
        boolean caught = move(1, bestP, r);
        p.playSound(p.getLocation(), Sound.BLOCK_WOOD_PLACE, 0.8f, 0.9f);
        log = "컴퓨터가 " + RES[resIdx(r)] + " 로 말을 옮김" + (caught ? " &c— 내 말이 잡혔다!" : "");
        if (caught) p.playSound(p.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 0.6f, 1.4f);
        if (finished(1) >= n) { render(); finish(false, "컴퓨터가 먼저 말을 모두 내보냈습니다"); return; }
        if (caught) canThrow = true;
        render();
        later(20, this::aiStep);
    }

    private double score(int piece, int to, int r) {
        int stack = group(1, piece).size();
        double s = 0;
        if (to == GOAL) s += 90 * stack;
        else {
            int caught = 0;
            for (int j = 0; j < n; j++) if (pos[0][j] == to) caught++;
            s += caught * 120;
            for (int j = 0; j < n; j++) if (pos[1][j] == to && j != piece && pos[1][piece] != to) s += 12;   // 업기
            if (to == 5 || to == 10 || to == CENTER) s += 22;
            s -= danger(to) * 18 * stack;
            s += (r > 0 ? r : -4) * 2;
            if (pos[1][piece] == HOME) s += 6;
        }
        s += ThreadLocalRandom.current().nextDouble(3);
        return s;
    }

    /** 내 말이 1~5 칸 뒤에서 이 칸을 노릴 수 있는 정도 */
    private int danger(int to) {
        int c = 0;
        for (int i = 0; i < n; i++) {
            if (pos[0][i] == GOAL) continue;
            for (int k = 1; k <= 5; k++) {
                int[] w = walk(pos[0][i], k);
                if (w[0] == to) { c++; break; }
            }
        }
        return c;
    }
}
