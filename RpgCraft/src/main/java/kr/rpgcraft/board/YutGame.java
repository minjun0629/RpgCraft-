package kr.rpgcraft.board;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 윷놀이 (v5.10.59 2~4명 · 빈자리는 컴퓨터). 창 왼쪽 6x6 이 윷판:
 *  바깥 20칸 (오른쪽 아래 = 출발 · 도착) + 모서리 지름길 (오른쪽 위 · 왼쪽 위 모서리에서 멈추면 대각선으로) + 가운데 방.
 *  말은 2개씩 (board.yut-pieces). 윷 · 모는 한 번 더, 다른 사람 말을 잡아도 한 번 더, 내 말끼리 겹치면 업어서 같이 움직임.
 *  빽도는 한 칸 뒤로. 말이 출발점을 지나면 도착. 말을 모두 먼저 내보낸 사람이 승리.
 */
public class YutGame extends BoardGameBase {
    // 칸 번호: 0~19 바깥 (0 = 출발 · 도착 모서리), 20 A (오른쪽 위 모서리 → 가운데), 21 가운데, 22 B (가운데 → 왼쪽 아래), 23 D (왼쪽 위 → 가운데), 24 E (가운데 → 출발)
    private static final int A = 20, CENTER = 21, B = 22, D = 23, E = 24, HOME = -1, GOAL = -2;
    private static final int[] SLOT = {50, 41, 32, 23, 14, 5, 4, 3, 2, 1, 0, 9, 18, 27, 36, 45, 46, 47, 48, 49, 13, 20, 37, 10, 40};
    private static final int[] CENTER_SLOTS = {20, 21, 29, 30};
    private static final int[] SEAT_SLOT = {7, 8, 16, 17};
    private static final String[] RES = {"도", "개", "걸", "윷", "모", "빽도"};

    private final int n;
    private final int[][] pos, prev;   // [자리][말]
    private final List<Integer> queue = new ArrayList<>();   // 던진 결과 (1~5, -1 빽도)
    private int sel;
    private boolean canThrow = true;
    private String log = "윷을 던지세요!";

    public YutGame(BoardManager mgr, List<Player> players, int seatCount) {
        super(mgr, players, seatCount, BoardManager.BoardGame.YUT, "&8윷놀이");
        n = Math.max(1, Math.min(4, plugin.getConfig().getInt("board.yut-pieces", 2)));
        pos = new int[seats][n];
        prev = new int[seats][n];
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
        if (flats == 1 && marked) return -1; // 빽도
        return flats;
    }

    private static int resIdx(int r) {
        return r == -1 ? 5 : r - 1;
    }

    private static int step(int at, int from, boolean first) {
        if (at == HOME) return 1;
        if (first && at == 5) return A;
        if (first && at == 10) return D;
        if (at == A) return CENTER;
        if (at == CENTER) return first ? E : from == A ? B : E;
        if (at == B) return 15;
        if (at == D) return CENTER;
        if (at == E) return 0;
        if (at == 0) return GOAL;
        if (at == 19) return 0;
        return at + 1;
    }

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

    /** 옮기고 잡은 말 수 */
    private int move(int side, int i, int r) {
        int[] d = dest(side, i, r);
        if (d == null) return 0;
        for (int j : group(side, i)) { pos[side][j] = d[0]; prev[side][j] = d[1]; }
        int caught = 0;
        if (d[0] >= 0)
            for (int o = 0; o < seats; o++) {
                if (o == side) continue;
                for (int j = 0; j < n; j++) if (pos[o][j] == d[0]) { pos[o][j] = HOME; prev[o][j] = HOME; caught++; }
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
    }

    private String where(int at) {
        if (at == GOAL) return "&a도착!";
        if (at == 0) return "출발점 (한 걸음 더 가면 도착)";
        if (at == CENTER) return "가운데 방";
        if (at == 5 || at == 10 || at == 15) return "모서리 (멈추면 지름길)";
        return at >= 20 ? "지름길" : "바깥 " + at + "번째 칸";
    }

    @Override
    protected void render(View v) {
        int me = v.seat;
        boolean mine = myMove(v);
        for (int at = 0; at < SLOT.length; at++) {
            int[] cnt = new int[seats];
            int kinds = 0, only = -1, total = 0;
            for (int s = 0; s < seats; s++) {
                for (int i = 0; i < n; i++) if (pos[s][i] == at) cnt[s]++;
                if (cnt[s] > 0) { kinds++; only = s; total += cnt[s]; }
            }
            int icon = kinds > 1 ? BoardIcons.YUT_MULTI : kinds == 1 ? BoardIcons.YUT_SEAT + only
                    : at == CENTER ? BoardIcons.YUT_CENTER : (at == 0 || at == 5 || at == 10 || at == 15) ? BoardIcons.YUT_CORNER : BoardIcons.YUT_STATION;
            List<String> lore = new ArrayList<>();
            lore.add("&7" + where(at));
            for (int s = 0; s < seats; s++) if (cnt[s] > 0) lore.add(SEAT_COLOR[s] + who(s).substring(2) + " 말 " + cnt[s] + "개" + (cnt[s] > 1 ? " (업힘)" : "") + (s == me ? " &7(나)" : ""));
            int myPiece = -1;
            for (int i = 0; i < n; i++) if (pos[me][i] == at) { myPiece = i; break; }
            if (myPiece >= 0 && mine && !queue.isEmpty() && !canThrow) {
                int[] d = dest(me, myPiece, queue.get(sel));
                lore.add("");
                lore.add(d == null ? "&8이 결과로는 못 움직임" : "&e▶ 클릭: " + RES[resIdx(queue.get(sel))] + " → " + where(d[0]));
            }
            String name = kinds == 0 ? (at == CENTER ? "&6가운데 방" : "&f윷판") : kinds == 1 ? who(only) + " &f말" : "&f여러 사람 말";
            int pi = myPiece;
            if (at == CENTER) {
                for (int s : CENTER_SLOTS) v.btn(s, BoardIcons.of(icon, Math.max(1, total), name, lore), () -> clickPiece(v, pi));
            } else v.btn(SLOT[at], BoardIcons.of(icon, Math.max(1, total), name, lore), () -> clickPiece(v, pi));
        }
        // 자리 (대기 말 · 도착)
        for (int s = 0; s < seats; s++) {
            int h = home(s), side = s;
            List<String> hl = new ArrayList<>();
            hl.add(seatLine(s, me));
            hl.add("&7대기 말 " + h + "개 · 도착 " + finished(s) + " / " + n);
            if (s == me && h > 0 && mine && !queue.isEmpty() && !canThrow) {
                int r = queue.get(sel);
                hl.add(r == -1 ? "&8빽도로는 새 말을 낼 수 없음" : "&e▶ 클릭: " + RES[resIdx(r)] + " 로 새 말 출발");
            }
            if (s == turn) hl.add("&e◀ 지금 차례");
            v.btn(SEAT_SLOT[s], BoardIcons.of(h > 0 ? BoardIcons.YUT_HOME_SEAT + s : BoardIcons.YUT_GOAL, Math.max(1, h > 0 ? h : finished(s)),
                    who(s) + (s == turn ? " &e◀" : ""), hl), () -> { if (side == me) clickPiece(v, firstHome(me) >= 0 ? -100 : -1); });
        }
        int[] qs = {25, 26, 34, 35};
        for (int k = 0; k < qs.length; k++) {
            if (k < queue.size()) {
                int idx = k;
                boolean on = k == sel;
                v.btn(qs[k], BoardIcons.of(BoardIcons.YUT_RESULT + resIdx(queue.get(k)), (on ? "&e&l▶ " : "&f") + RES[resIdx(queue.get(k))],
                        on ? "&a지금 쓸 결과" : "&7클릭하면 이 결과를 먼저 씀"), () -> { if (myMove(v)) { sel = idx; refresh(); } });
            }
        }
        boolean t = mine && canThrow;
        v.btn(43, BoardIcons.of(t ? BoardIcons.YUT_THROW : BoardIcons.YUT_STATION, t ? "&e&l윷 던지기!" : mine ? "&7말을 옮기세요" : who(turn) + " &7차례…",
                t ? "&7클릭하여 윷 던지기" : mine ? "&7결과를 골라 판의 내 말을 클릭" : ""), () -> doThrow(v));
        v.set(44, BoardIcons.of(BoardIcons.YUT_SEAT + turn, (turn == me ? "&l내 차례 " : "") + who(turn) + " &f차례", "&f" + log));
        v.set(52, kr.rpgcraft.gui.Gui.ui(kr.rpgcraft.gui.UiIcon.HELP, true, "&f규칙", "&7윷 · 모: 한 번 더 던짐", "&7다른 사람 말을 잡으면 한 번 더",
                "&7내 말끼리 겹치면 업어서 함께 움직임", "&7오른쪽 위 · 왼쪽 위 모서리, 가운데에 멈추면 지름길로", "&7빽도: 한 칸 뒤로", "&7말 " + n + "개를 먼저 모두 내보내면 승리"));
    }

    // ------------------------------------------------------------------ 사람 차례
    private void doThrow(View v) {
        if (!myMove(v) || !canThrow) return;
        throwOnce();
    }

    private void throwOnce() {
        int r = throwSticks();
        queue.add(r);
        sound(Sound.BLOCK_WOOD_HIT, 1f, 0.9f + ThreadLocalRandom.current().nextFloat() * 0.3f);
        log = plain(turn) + ": " + RES[resIdx(r)] + (r >= 4 ? " — 한 번 더!" : "");
        if (r >= 4) { sound(Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.6f); refresh(); return; }
        canThrow = false;
        sel = 0;
        skipDead();
        refresh();
    }

    /** 움직일 수 없는 결과는 버림 · 다 쓰면 차례 넘김 */
    private void skipDead() {
        queue.removeIf(r -> !anyMovable(turn, r));
        if (sel >= queue.size()) sel = 0;
        if (queue.isEmpty() && !canThrow) endTurn();
    }

    private void clickPiece(View v, int idx) {
        if (!myMove(v) || canThrow || queue.isEmpty()) return;
        int me = v.seat;
        int piece = idx == -100 ? firstHome(me) : idx;
        if (piece < 0) return;
        int r = queue.get(sel);
        if (dest(me, piece, r) == null) { say(v, "&c이 결과로는 그 말을 움직일 수 없습니다."); return; }
        queue.remove(sel);
        sel = 0;
        apply(piece, r);
    }

    /** 지금 차례 자리가 말을 옮김 (사람 · 컴퓨터 공통) */
    private void apply(int piece, int r) {
        int caught = move(turn, piece, r);
        sound(Sound.BLOCK_WOOD_PLACE, 1f, 1.2f);
        log = plain(turn) + ": " + RES[resIdx(r)] + " 로 이동" + (caught > 0 ? " — 말을 잡았다! 한 번 더!" : "");
        if (finished(turn) >= n) { finish(turn, plain(turn) + " 님이 말을 모두 내보냈습니다!"); return; }
        if (caught > 0) {
            sound(Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1.2f);
            canThrow = true;
        }
        skipDead();
        refresh();
    }

    private void endTurn() {
        queue.clear();
        sel = 0;
        canThrow = true;
        log = log + " → 다음 차례";
        nextTurn(null);
    }

    // ------------------------------------------------------------------ 컴퓨터
    @Override
    protected void aiTurn() {
        if (over || !isAI(turn)) return;
        int side = turn;
        if (canThrow) {
            throwOnce();
            if (!over && turn == side && isAI(side)) later(18, this::aiTurn);
            return;
        }
        queue.removeIf(r -> !anyMovable(side, r));
        if (queue.isEmpty()) { endTurn(); return; }
        int bestR = -1, bestP = -1;
        double best = -1e9;
        for (int qi = 0; qi < queue.size(); qi++) {
            int r = queue.get(qi);
            Set<Integer> seen = new HashSet<>();
            for (int i = 0; i < n; i++) {
                int at = pos[side][i];
                if (at == GOAL || !seen.add(at)) continue;
                int[] d = dest(side, i, r);
                if (d == null) continue;
                double sc = score(side, i, d[0], r);
                if (sc > best) { best = sc; bestR = qi; bestP = i; }
            }
        }
        int r = queue.remove(bestR);
        sel = 0;
        apply(bestP, r);
        if (!over && turn == side && isAI(side)) later(20, this::aiTurn);
    }

    private double score(int side, int piece, int to, int r) {
        int stack = group(side, piece).size();
        double s = 0;
        if (to == GOAL) s += 90 * stack;
        else {
            int caught = 0;
            for (int o = 0; o < seats; o++) if (o != side) for (int j = 0; j < n; j++) if (pos[o][j] == to) caught++;
            s += caught * 120;
            for (int j = 0; j < n; j++) if (pos[side][j] == to && j != piece && pos[side][piece] != to) s += 12;
            if (to == 5 || to == 10 || to == CENTER) s += 22;
            s -= danger(side, to) * 18 * stack;
            s += (r > 0 ? r : -4) * 2;
            if (pos[side][piece] == HOME) s += 6;
        }
        return s + ThreadLocalRandom.current().nextDouble(3);
    }

    /** 다른 사람 말이 1~5 칸 뒤에서 이 칸을 노릴 수 있는 정도 */
    private int danger(int side, int to) {
        int c = 0;
        for (int o = 0; o < seats; o++) {
            if (o == side) continue;
            for (int i = 0; i < n; i++) {
                if (pos[o][i] == GOAL) continue;
                for (int k = 1; k <= 5; k++) if (walk(pos[o][i], k)[0] == to) { c++; break; }
            }
        }
        return c;
    }
}
