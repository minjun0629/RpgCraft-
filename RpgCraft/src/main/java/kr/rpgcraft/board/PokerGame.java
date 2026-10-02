package kr.rpgcraft.board;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 인디언 포커 (v5.10.59 2~4명 · 빈자리는 컴퓨터, 심리전).
 *  카드 1~10 (두 벌). 각자 한 장씩 이마에 붙임 → 다른 사람 카드는 보이고 내 카드는 안 보임 (창이 사람마다 따로라 내 카드만 가려짐).
 *  판마다 1칩씩 걸고 시작. 차례대로: 아직 아무도 안 걸었으면 체크 · 베팅(+1 · +3), 누가 걸었으면 콜 · 다이.
 *  베팅한 뒤에는 앞서 체크한 사람에게도 콜 · 다이를 물어봄. 남은 사람 중 가장 큰 수가 판돈을 모두, 공동 1위면 다음 판으로 이월.
 *  10 을 들고 다이하면 벌칙 2칩 (판돈으로). 칩이 0 이 되면 탈락. 혼자 남거나 정해진 판 수가 끝났을 때 칩이 가장 많은 사람이 승리.
 */
public class PokerGame extends BoardGameBase {
    private static final int[] CARD_SLOT = {11, 15, 29, 33};
    private static final int[] CHIP_SLOT = {10, 16, 28, 34};
    private final int maxRounds;
    private final int[] chips, card, put;
    private final boolean[] folded, acted, out;
    private final List<Integer> deck = new ArrayList<>();
    private int pot, carry, round = 0, bet, first = -1;
    private boolean reveal;
    private final Deque<String> log = new ArrayDeque<>();

    public PokerGame(BoardManager mgr, List<Player> players, int seatCount) {
        super(mgr, players, seatCount, BoardManager.BoardGame.POKER, "&8인디언 포커");
        chips = new int[seats];
        card = new int[seats];
        put = new int[seats];
        folded = new boolean[seats];
        acted = new boolean[seats];
        out = new boolean[seats];
        Arrays.fill(chips, plugin.getConfig().getInt("board.poker-chips", 10));
        maxRounds = plugin.getConfig().getInt("board.poker-rounds", 15);
    }

    private int draw() {
        if (deck.isEmpty()) {
            for (int k = 0; k < 2; k++) for (int n = 1; n <= 10; n++) deck.add(n);
            Collections.shuffle(deck);
        }
        return deck.remove(deck.size() - 1);
    }

    private void log(String s) {
        log.addFirst(s);
        while (log.size() > 7) log.removeLast();
    }

    private int playing() {
        int c = 0;
        for (int s = 0; s < seats; s++) if (!out[s]) c++;
        return c;
    }

    private int inHand() {
        int c = 0;
        for (int s = 0; s < seats; s++) if (!out[s] && !folded[s]) c++;
        return c;
    }

    @Override
    protected void setup() {
        newRound(false);
    }

    private void newRound(boolean live) {
        round++;
        for (int s = 0; s < seats; s++) if (!out[s] && chips[s] <= 0) { out[s] = true; log(plain(s) + ": 칩이 없어 탈락"); }
        if (playing() <= 1 || round > maxRounds) { end(); return; }
        reveal = false;
        deck.clear();
        bet = 0;
        pot = carry;
        carry = 0;
        for (int s = 0; s < seats; s++) {
            folded[s] = out[s];
            acted[s] = false;
            put[s] = 0;
            if (out[s]) continue;
            card[s] = draw();
            chips[s]--;
            pot++;
        }
        do first = (first + 1) % seats; while (out[first]);
        turn = first;
        log(round + "판 시작 — 1칩씩 걸었습니다 · " + plain(first) + " 먼저");
        sound(Sound.ITEM_BOOK_PAGE_TURN, 1f, 1.1f);
        if (live) {
            refresh();
            if (isAI(turn)) startAi();
        }
    }

    // ------------------------------------------------------------------ 화면
    @Override
    protected void render(View v) {
        int me = v.seat;
        for (int s = 0; s < seats; s++) {
            List<String> l = new ArrayList<>();
            l.add(seatLine(s, me));
            if (out[s]) l.add("&8탈락");
            else if (folded[s]) l.add("&7다이");
            else if (put[s] > 0) l.add("&e이번 판 베팅 " + put[s]);
            if (s == turn && !reveal) l.add("&e◀ 지금 차례");
            boolean hidden = s == me && !reveal && !out[s];
            if (out[s]) v.set(CARD_SLOT[s], BoardIcons.of(BoardIcons.FOLD, who(s) + " &8탈락", l));
            else if (hidden) {
                l.add("&7내 이마의 카드는 볼 수 없다");
                l.add("&7다른 사람 반응으로 짐작해 보자");
                v.set(CARD_SLOT[s], BoardIcons.of(BoardIcons.CARD_BACK, who(s) + " &7카드 (안 보임)" + (s == turn ? " &e◀" : ""), l));
            } else v.set(CARD_SLOT[s], BoardIcons.of(BoardIcons.CARD + card[s], who(s) + " &f카드 " + card[s] + (folded[s] ? " &7(다이)" : "") + (s == turn && !reveal ? " &e◀" : ""), l));
            v.set(CHIP_SLOT[s], BoardIcons.of(BoardIcons.CHIP, Math.max(1, chips[s]), who(s) + " &f칩 " + chips[s]));
        }
        v.set(22, BoardIcons.of(BoardIcons.BOARD_CHIP, Math.max(1, pot), "&e&l판돈 &f" + pot, carry > 0 ? "&7이월된 칩 포함" : "", bet > 0 ? "&7지금 베팅 " + bet : "&7아직 아무도 안 걸었음"));
        v.set(4, BoardIcons.of(BoardIcons.HUB_POKER, Math.min(64, Math.max(1, round)), "&f" + round + " &7/ " + maxRounds + " 판", "&7혼자 남거나 " + maxRounds + "판 뒤 칩이 가장 많으면 승리"));
        List<String> ll = new ArrayList<>();
        for (String s : log) ll.add("&f" + s);
        v.set(40, BoardIcons.of(BoardIcons.MONEY, "&f진행", ll));
        boolean mine = myMove(v) && !reveal && !folded[me] && !out[me];
        if (mine && bet == 0) {
            v.btn(47, BoardIcons.of(BoardIcons.CALL, "&a&l체크", "&7걸지 않고 넘김"), () -> act(me, 0));
            v.btn(48, BoardIcons.of(BoardIcons.CHIP, "&e&l베팅 +1", chips[me] >= 1 ? "&7칩 1개를 걺" : "&c칩이 모자람"), () -> act(me, 1));
            v.btn(50, BoardIcons.of(BoardIcons.CHIP, 3, "&6&l베팅 +3", chips[me] >= 3 ? "&7칩 3개를 걺 (허세?)" : "&c칩이 모자람"), () -> act(me, 3));
            v.btn(51, BoardIcons.of(BoardIcons.FOLD, "&7다이", "&7이번 판 포기", "&c10 을 들고 다이하면 벌칙 2칩"), () -> act(me, -1));
        } else if (mine) {
            int need = Math.min(bet - put[me], chips[me]);
            v.btn(47, BoardIcons.of(BoardIcons.CALL, "&a&l콜 &f(" + need + "칩)", "&7같이 걸고 버팀"), () -> act(me, 0));
            v.btn(51, BoardIcons.of(BoardIcons.FOLD, "&7다이", "&7이번 판 포기", "&c10 을 들고 다이하면 벌칙 2칩"), () -> act(me, -1));
        }
        v.btn(45, kr.rpgcraft.gui.Gui.ui(kr.rpgcraft.gui.UiIcon.HELP, true, "&f규칙", "&7다른 사람 카드는 보이고 내 카드는 안 보임", "&7아무도 안 걸었으면 체크 · 베팅, 누가 걸면 콜 · 다이", "&7남은 사람 중 가장 큰 수가 판돈을 모두"), () -> { });
        v.set(49, BoardIcons.of(BoardIcons.TOK_SEAT + turn, reveal ? "&f결과 확인 중…" : (turn == me ? "&l내 차례 " : "") + who(turn) + " &f차례",
                "&7이번 판 먼저: " + plain(first)));
    }

    // ------------------------------------------------------------------ 행동 (b: -1 다이 · 0 체크/콜 · 1/3 베팅)
    private void act(int s, int b) {
        if (over || reveal || s != turn || folded[s] || out[s]) return;
        if (b < 0) {
            folded[s] = true;
            log(plain(s) + ": 다이" + (card[s] == 10 ? " — 10 을 버려서 벌칙 2칩!" : ""));
            if (card[s] == 10) { int pen = Math.min(2, chips[s]); chips[s] -= pen; pot += pen; }
            sound(Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.8f);
        } else if (bet == 0 && b > 0) {
            b = Math.min(b, chips[s]);
            if (b == 0) { log(plain(s) + ": 체크"); }
            else {
                chips[s] -= b;
                put[s] += b;
                pot += b;
                bet = b;
                for (int o = 0; o < seats; o++) if (o != s) acted[o] = false;   // 다른 사람은 다시 콜 · 다이
                log(plain(s) + ": 베팅 +" + b + (b >= 3 ? " (자신 있는 걸까…?)" : ""));
                sound(Sound.BLOCK_CHAIN_PLACE, 1f, b >= 3 ? 0.8f : 1.2f);
            }
        } else if (bet > 0) {
            int need = Math.min(bet - put[s], chips[s]);
            chips[s] -= need;
            put[s] += need;
            pot += need;
            log(plain(s) + ": 콜");
            sound(Sound.BLOCK_CHAIN_PLACE, 0.8f, 1.4f);
        } else log(plain(s) + ": 체크");
        acted[s] = true;
        advance();
    }

    /** 다음 사람에게 묻거나, 다 끝났으면 결과 */
    private void advance() {
        if (inHand() <= 1) {
            for (int s = 0; s < seats; s++) if (!out[s] && !folded[s]) { win(List.of(s), false); return; }
        }
        for (int k = 1; k <= seats; k++) {
            int n = (turn + k) % seats;
            if (out[n] || folded[n]) continue;
            boolean owes = bet > 0 && put[n] < bet && chips[n] > 0;
            if (!acted[n] || owes) {
                turn = n;
                refresh();
                if (isAI(turn)) startAi();
                return;
            }
        }
        showdown();
    }

    // ------------------------------------------------------------------ 컴퓨터
    @Override
    protected void aiTurn() {
        if (over || reveal || !isAI(turn)) return;
        int s = turn;
        // 보이는 카드(남은 사람들)보다 내 카드가 클 확률
        int top = 0;
        for (int o = 0; o < seats; o++) if (o != s && !out[o] && !folded[o]) top = Math.max(top, card[o]);
        int better = 0, total = 0;
        for (int n = 1; n <= 10; n++) { total += 2; if (n > top) better += 2; else if (n == top) better += 1; }
        double pw = (double) better / total;
        if (inHand() > 2) pw *= 0.9;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (bet == 0) {
            int b;
            if (r.nextDouble() < 0.15) b = r.nextBoolean() ? 3 : 1;   // 허세
            else b = pw > 0.7 ? 3 : pw > 0.45 ? 1 : 0;
            act(s, b);
        } else {
            double need = bet >= 3 ? 0.42 : 0.33;
            if (bet >= 3 && put[s] == 0) pw *= 0.75;   // 크게 건 사람은 내 카드가 낮은 걸 봤을지도
            act(s, pw + r.nextDouble(-0.12, 0.12) > need ? 0 : -1);
        }
    }

    // ------------------------------------------------------------------ 결과
    private void showdown() {
        reveal = true;
        int best = -1;
        List<Integer> top = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (int s = 0; s < seats; s++) {
            if (out[s] || folded[s]) continue;
            sb.append(sb.length() > 0 ? " · " : "").append(plain(s)).append(" ").append(card[s]);
            if (card[s] > best) { best = card[s]; top.clear(); top.add(s); }
            else if (card[s] == best) top.add(s);
        }
        log("공개: " + sb);
        if (top.size() > 1) {
            log("공동 1위 — 판돈 " + pot + " 이월");
            carry = pot;
            pot = 0;
            next();
            return;
        }
        win(top, true);
    }

    private void win(List<Integer> ws, boolean shown) {
        reveal = true;
        int w = ws.get(0);
        chips[w] += pot;
        log(plain(w) + ": 판돈 " + pot + "칩 획득" + (shown ? "" : " (모두 다이)"));
        sound(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.3f);
        pot = 0;
        next();
    }

    private void next() {
        busy = true;
        refresh();
        later(50, () -> { busy = false; newRound(true); });
    }

    private void end() {
        int best = -1, bc = -1;
        StringBuilder sb = new StringBuilder();
        for (int s = 0; s < seats; s++) {
            sb.append(sb.length() > 0 ? " · " : "").append(plain(s)).append(" ").append(chips[s]);
            if (chips[s] > bc) { bc = chips[s]; best = s; }
        }
        reveal = true;
        finish(best, "칩 " + sb);
    }
}
