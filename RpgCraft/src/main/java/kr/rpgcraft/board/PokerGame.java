package kr.rpgcraft.board;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * v5.10.45 인디언 포커 (심리전, 컴퓨터와 1대1).
 *  카드 1~10 (두 벌). 각자 한 장씩 받아 이마에 붙임 → 상대 카드는 보이고 내 카드는 안 보임.
 *  판마다 1칩씩 걸고 시작. 먼저 하는 쪽이 체크 · 베팅(+1 · +3), 상대는 콜 · 다이.
 *  큰 수가 이기면 판돈을 모두, 같으면 다음 판으로 이월. 10 을 들고 다이하면 벌칙 2칩.
 *  컴퓨터도 내 카드를 보고, 내 베팅 크기로 자기 카드를 짐작하며, 가끔 허세를 부림.
 *  상대 칩을 모두 따거나 15판 뒤 칩이 많으면 승리.
 */
public class PokerGame extends BoardGameBase {
    private final int maxRounds;
    private final int[] chips = new int[2];
    private final List<Integer> deck = new ArrayList<>();
    private int myCard, aiCard, pot, carry, round = 0, bet;
    /** 이번 판 먼저 하는 쪽 (0 나 · 1 컴퓨터) */
    private int first = 1;
    /** 0 내 첫 행동 · 1 컴퓨터 베팅에 대한 내 응답 · 2 결과 보는 중 */
    private int phase;
    private boolean reveal;
    private final Deque<String> log = new ArrayDeque<>();

    public PokerGame(BoardManager mgr, Player p) {
        super(mgr, p, BoardManager.BoardGame.POKER, "&8인디언 포커 &7- 심리전");
        chips[0] = chips[1] = plugin.getConfig().getInt("board.poker-chips", 10);
        maxRounds = plugin.getConfig().getInt("board.poker-rounds", 15);
    }

    private int draw() {
        if (deck.size() < 2) {
            deck.clear();
            for (int k = 0; k < 2; k++) for (int n = 1; n <= 10; n++) deck.add(n);
            Collections.shuffle(deck);
        }
        return deck.remove(deck.size() - 1);
    }

    private void log(String s) {
        log.addFirst(s);
        while (log.size() > 6) log.removeLast();
    }

    @Override
    protected void setup() {
        newRound();
    }

    private void newRound() {
        round++;
        if (chips[0] <= 0 || chips[1] <= 0 || round > maxRounds) { end(); return; }
        reveal = false;
        myCard = draw();
        aiCard = draw();
        chips[0]--;
        chips[1]--;
        pot = 2 + carry;
        carry = 0;
        bet = 0;
        first = 1 - first;
        log(round + "판 시작 — 1칩씩 걸었습니다");
        p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1.1f);
        if (first == 0) { phase = 0; busy = false; render(); }
        else { phase = -1; busy = true; render(); later(25, this::aiOpen); }
    }

    // ------------------------------------------------------------------ 화면
    private void render() {
        set(13, BoardIcons.of(BoardIcons.CARD + aiCard, "&9&l컴퓨터 카드 &f" + aiCard, "&7상대 이마의 카드 (상대는 못 봄)", "&7컴퓨터는 내 카드를 보고 있음…"));
        set(40, reveal ? BoardIcons.of(BoardIcons.CARD + myCard, "&c&l내 카드 &f" + myCard, "&7공개!")
                : BoardIcons.of(BoardIcons.CARD_BACK, "&c&l내 카드 &7(안 보임)", "&7내 이마의 카드는 볼 수 없다", "&7컴퓨터의 반응으로 짐작해 보자"));
        set(10, BoardIcons.of(BoardIcons.CHIP, Math.max(1, chips[1]), "&9컴퓨터 칩 &f" + chips[1]));
        set(37, BoardIcons.of(BoardIcons.CHIP, Math.max(1, chips[0]), "&c내 칩 &f" + chips[0]));
        set(22, BoardIcons.of(BoardIcons.BOARD_CHIP, Math.max(1, pot), "&e&l판돈 &f" + pot, carry > 0 ? "&7이월된 칩 포함" : ""));
        set(16, BoardIcons.of(BoardIcons.HUB_POKER, Math.min(64, Math.max(1, round)), "&f" + round + " &7/ " + maxRounds + " 판", "&7칩을 모두 따거나 " + maxRounds + "판 뒤 많으면 승리"));
        List<String> ll = new ArrayList<>();
        for (String s : log) ll.add("&f" + s);
        set(43, BoardIcons.of(BoardIcons.MONEY, "&f진행", ll));
        boolean act0 = phase == 0 && !busy, act1 = phase == 1 && !busy;
        if (act0) {
            set(47, BoardIcons.of(BoardIcons.CALL, "&a&l체크", "&7걸지 않고 바로 카드 공개"), e -> myOpen(0));
            set(48, BoardIcons.of(BoardIcons.CHIP, "&e&l베팅 +1", chips[0] >= 1 ? "&7칩 1개를 더 검" : "&c칩이 모자람"), e -> myOpen(1));
            set(50, BoardIcons.of(BoardIcons.CHIP, 3, "&6&l베팅 +3", chips[0] >= 3 ? "&7칩 3개를 더 검 (허세?)" : "&c칩이 모자람"), e -> myOpen(3));
            set(51, BoardIcons.of(BoardIcons.FOLD, "&7다이", "&7이번 판 포기 (건 칩을 잃음)", "&c10 을 들고 다이하면 벌칙 2칩"), e -> fold(0));
        } else if (act1) {
            set(47, BoardIcons.of(BoardIcons.CALL, "&a&l콜 &f(" + Math.min(bet, chips[0]) + "칩)", "&7같이 걸고 카드 공개"), e -> respond(true));
            set(48, kr.rpgcraft.pack.PackManager.filler(org.bukkit.Material.GRAY_STAINED_GLASS_PANE), e -> { });
            set(50, kr.rpgcraft.pack.PackManager.filler(org.bukkit.Material.GRAY_STAINED_GLASS_PANE), e -> { });
            set(51, BoardIcons.of(BoardIcons.FOLD, "&7다이", "&7이번 판 포기", "&c10 을 들고 다이하면 벌칙 2칩"), e -> respond(false));
        } else for (int s : new int[]{47, 48, 50, 51}) set(s, kr.rpgcraft.pack.PackManager.filler(org.bukkit.Material.GRAY_STAINED_GLASS_PANE), e -> { });
        set(49, BoardIcons.of(first == 0 ? BoardIcons.TOK_ME : BoardIcons.TOK_AI, phase == 2 ? "&f결과 확인 중…" : (phase == 0 || phase == 1) && !busy ? "&c&l내 차례" : "&9&l컴퓨터 생각 중…",
                "&7이번 판 먼저: " + (first == 0 ? "나" : "컴퓨터")));
        fill(0, 53);
    }

    // ------------------------------------------------------------------ 컴퓨터 판단
    /** 컴퓨터가 보는 자기 승률 (내 카드는 보이고, 자기 카드는 모름) */
    private double aiWinChance(int myBet) {
        int higher = 0, total = 0;
        for (int n = 1; n <= 10; n++) { total += 2; if (n > myCard) higher += 2; else if (n == myCard) higher += 1; }
        double pw = (double) higher / total;
        // 내가 크게 걸면 컴퓨터 카드가 낮다고 짐작 (내가 컴퓨터 카드를 보고 걸었으니까)
        if (myBet >= 3) pw *= 0.6;
        else if (myBet == 1) pw *= 0.88;
        else if (myBet == 0) pw = Math.min(1, pw * 1.15);
        return pw;
    }

    private void aiOpen() {
        if (over) return;
        double pw = aiWinChance(-1);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int b;
        if (r.nextDouble() < 0.15) b = r.nextBoolean() ? 3 : 1;   // 허세
        else b = pw > 0.7 ? 3 : pw > 0.45 ? 1 : 0;
        b = Math.min(b, chips[1]);
        if (b == 0) {
            log("컴퓨터: 체크");
            showdown();
            return;
        }
        chips[1] -= b;
        pot += b;
        bet = b;
        log("컴퓨터: 베팅 +" + b + (b >= 3 ? " (자신 있는 걸까…?)" : ""));
        p.playSound(p.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1f, b >= 3 ? 0.8f : 1.2f);
        phase = 1;
        busy = false;
        render();
    }

    // ------------------------------------------------------------------ 내 행동
    private void myOpen(int b) {
        if (over || busy || phase != 0) return;
        if (b > chips[0]) { say("&c칩이 모자랍니다."); return; }
        if (b == 0) { log("나: 체크"); showdown(); return; }
        chips[0] -= b;
        pot += b;
        log("나: 베팅 +" + b);
        p.playSound(p.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1f, 1f);
        busy = true;
        render();
        int bb = b;
        later(25, () -> {
            double pw = aiWinChance(bb);
            ThreadLocalRandom r = ThreadLocalRandom.current();
            boolean call = pw + r.nextDouble(-0.12, 0.12) > (bb >= 3 ? 0.42 : 0.33) && chips[1] > 0;
            if (call) {
                int c = Math.min(bb, chips[1]);
                chips[1] -= c;
                pot += c;
                log("컴퓨터: 콜!");
                showdown();
            } else {
                log("컴퓨터: 다이");
                win(0, false);
            }
        });
    }

    private void respond(boolean call) {
        if (over || busy || phase != 1) return;
        if (!call) { fold(0); return; }
        int c = Math.min(bet, chips[0]);
        chips[0] -= c;
        pot += c;
        log("나: 콜");
        showdown();
    }

    private void fold(int side) {
        if (over || busy && side == 0) return;
        int card = side == 0 ? myCard : aiCard;
        log((side == 0 ? "나" : "컴퓨터") + ": 다이" + (card == 10 ? " — 10 을 버려서 벌칙 2칩!" : ""));
        if (card == 10) { int pen = Math.min(2, chips[side]); chips[side] -= pen; chips[1 - side] += pen; }
        win(1 - side, false);
    }

    // ------------------------------------------------------------------ 결과
    private void showdown() {
        reveal = true;
        if (myCard == aiCard) {
            log("공개: 나 " + myCard + " vs 컴퓨터 " + aiCard + " — 비김 (판돈 이월)");
            carry = pot;
            pot = 0;
            next();
            return;
        }
        win(myCard > aiCard ? 0 : 1, true);
    }

    private void win(int side, boolean shown) {
        reveal = true;
        chips[side] += pot;
        if (shown) log("공개: 나 " + myCard + " vs 컴퓨터 " + aiCard + " — " + (side == 0 ? "내가 " : "컴퓨터가 ") + pot + "칩 획득");
        else log((side == 0 ? "내가 " : "컴퓨터가 ") + pot + "칩 획득 (내 카드는 " + myCard + ")");
        p.playSound(p.getLocation(), side == 0 ? Sound.ENTITY_EXPERIENCE_ORB_PICKUP : Sound.BLOCK_NOTE_BLOCK_BASS, 1f, side == 0 ? 1.3f : 0.7f);
        pot = 0;
        next();
    }

    private void next() {
        phase = 2;
        busy = true;
        render();
        later(50, this::newRound);
    }

    private void end() {
        render();
        if (chips[0] > chips[1]) finish(true, "칩 " + chips[0] + " vs " + chips[1]);
        else finish(false, "칩 " + chips[0] + " vs " + chips[1]);
    }
}
