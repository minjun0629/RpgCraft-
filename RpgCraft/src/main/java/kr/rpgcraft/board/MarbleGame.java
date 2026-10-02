package kr.rpgcraft.board;

import kr.rpgcraft.util.Text;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 부루마블 (v5.10.59 2~4명 · 빈자리는 컴퓨터). 창 테두리 26칸이 판 (오른쪽 아래 = 출발).
 *  주사위 2개 → 도착한 도시를 사거나 (같은 색 3곳이면 통행료 2배 · 호텔 짓기 3배), 남의 도시면 통행료.
 *  출발점을 지나면 월급, 무인도는 한 번 쉼, 황금열쇠(?)는 무작위 사건, 우주여행은 다음 차례에 원하는 칸으로, 사회복지기금은 모아서 받기.
 *  더블은 한 번 더 (세 번 연속이면 무인도). 파산한 사람은 빠지고 땅은 주인이 없어짐.
 *  마지막까지 남거나, 정해진 바퀴가 끝났을 때 재산이 가장 많은 사람이 승리.
 */
public class MarbleGame extends BoardGameBase {
    private static final int[] SLOT = {53, 52, 51, 50, 49, 48, 47, 46, 45, 36, 27, 18, 9, 0, 1, 2, 3, 4, 5, 6, 7, 8, 17, 26, 35, 44};
    private static final int N = SLOT.length;
    private static final int GO = 0, ISLAND = 8, FUND_GET = 13, TRAVEL = 21, FUND_PAY = 11;
    private static final Set<Integer> CHANCE = Set.of(4, 17, 24);
    private static final int[] CITY_IDX = {1, 2, 3, 5, 6, 7, 9, 10, 12, 14, 15, 16, 18, 19, 20, 22, 23, 25};
    private static final String[] CITY_NAME = {"타이베이", "베이징", "마닐라", "카이로", "이스탄불", "아테네", "코펜하겐", "스톡홀름", "베른",
            "베를린", "오타와", "상파울루", "시드니", "하와이", "리스본", "마드리드", "도쿄", "서울"};
    private static final int[] GROUP_PRICE = {60, 100, 140, 180, 220, 280};
    private static final int[] GROUP_COLOR = {0, 1, 2, 3, 4, 7};
    private static final String[] GROUP_CC = {"&6", "&b", "&d", "&6", "&c", "&9"};
    private static final int[] PANEL = {10, 16, 37, 43};

    private final int salary, maxRounds;
    private final long[] money;
    private final int[] at, islandSkip;
    private final boolean[] travelNext, broke;
    private final int[] owner = new int[N];       // -1 없음 · 자리 번호
    private final boolean[] hotel = new boolean[N];
    private long fund;
    private int round = 1, doubles, d1 = 1, d2 = 1, first;
    /** 0 굴리기 · 1 살지 정하기 · 2 우주여행 칸 고르기 */
    private int phase;
    private boolean pendingDouble;
    private final Deque<String> log = new ArrayDeque<>();

    public MarbleGame(BoardManager mgr, List<Player> players, int seatCount) {
        super(mgr, players, seatCount, BoardManager.BoardGame.MARBLE, "&8부루마블");
        Arrays.fill(owner, -1);
        money = new long[seats];
        at = new int[seats];
        islandSkip = new int[seats];
        travelNext = new boolean[seats];
        broke = new boolean[seats];
        long start = plugin.getConfig().getLong("board.marble-start-money", 1500);
        Arrays.fill(money, start);
        salary = plugin.getConfig().getInt("board.marble-salary", 200);
        maxRounds = plugin.getConfig().getInt("board.marble-rounds", 20);
        log("게임 시작! " + plain(0) + " 님부터");
    }

    // ------------------------------------------------------------------ 판 정보
    private static int cityOf(int idx) {
        for (int i = 0; i < CITY_IDX.length; i++) if (CITY_IDX[i] == idx) return i;
        return -1;
    }

    private static int group(int idx) {
        int c = cityOf(idx);
        return c < 0 ? -1 : c / 3;
    }

    private static int price(int idx) {
        return GROUP_PRICE[group(idx)];
    }

    private boolean fullSet(int idx) {
        int g = group(idx), o = owner[idx];
        if (o < 0) return false;
        for (int k = 0; k < 3; k++) if (owner[CITY_IDX[g * 3 + k]] != o) return false;
        return true;
    }

    private long rent(int idx) {
        long r = Math.round(price(idx) * 0.35);
        if (fullSet(idx)) r *= 2;
        if (hotel[idx]) r *= 3;
        return r;
    }

    private String name(int idx) {
        int c = cityOf(idx);
        if (c >= 0) return GROUP_CC[c / 3] + CITY_NAME[c];
        if (idx == GO) return "&a출발";
        if (idx == ISLAND) return "&b무인도";
        if (idx == FUND_GET) return "&6사회복지기금 받기";
        if (idx == FUND_PAY) return "&6사회복지기금 내기";
        if (idx == TRAVEL) return "&9우주여행";
        return "&5황금열쇠";
    }

    private String nm(int idx) {
        return Text.strip(Text.c(name(idx)));
    }

    private long assets(int side) {
        long a = money[side];
        for (int i = 0; i < N; i++) if (owner[i] == side) a += price(i) + (hotel[i] ? price(i) : 0);
        return a;
    }

    private int count(int side) {
        int c = 0;
        for (int i = 0; i < N; i++) if (owner[i] == side) c++;
        return c;
    }

    private int alive() {
        int c = 0;
        for (boolean b : broke) if (!b) c++;
        return c;
    }

    private void log(String s) {
        log.addFirst(s);
        while (log.size() > 7) log.removeLast();
    }

    // ------------------------------------------------------------------ 화면
    @Override
    protected void setup() {
        if (travelNext[turn]) phase = 2;
    }

    @Override
    protected void render(View v) {
        int me = v.seat;
        boolean mine = myMove(v);
        for (int i = 0; i < N; i++) {
            int idx = i;
            List<Integer> here = new ArrayList<>();
            for (int s = 0; s < seats; s++) if (!broke[s] && at[s] == i) here.add(s);
            int c = cityOf(i);
            int icon;
            if (here.size() > 1) icon = BoardIcons.TOK_MULTI;
            else if (here.size() == 1) icon = BoardIcons.TOK_SEAT + here.get(0);
            else if (c >= 0) icon = owner[i] >= 0 ? BoardIcons.CITY_SEAT + owner[i] * 8 + GROUP_COLOR[c / 3] : BoardIcons.CITY + GROUP_COLOR[c / 3];
            else icon = i == GO ? BoardIcons.START : i == ISLAND ? BoardIcons.ISLAND : i == TRAVEL ? BoardIcons.TRAVEL : (i == FUND_GET || i == FUND_PAY) ? BoardIcons.FUND : BoardIcons.CHANCE;
            List<String> lore = new ArrayList<>();
            if (c >= 0) {
                lore.add("&f땅값 &e" + price(i) + " &7· 통행료 &e" + rent(i) + (fullSet(i) ? " &a(같은 색 독점 ×2)" : "") + (hotel[i] ? " &d(호텔 ×3)" : ""));
                lore.add(owner[i] < 0 ? "&7주인 없음" : who(owner[i]) + " &7땅" + (owner[i] == me ? " (내 땅)" : ""));
            } else lore.add("&7" + switch (i) {
                case GO -> "지나가면 월급 " + salary;
                case ISLAND -> "도착하면 한 번 쉼";
                case FUND_GET -> "모인 기금 " + fund + " 을(를) 모두 받음";
                case FUND_PAY -> "기금 100 을 냄";
                case TRAVEL -> "다음 차례에 원하는 칸으로 날아감";
                default -> "무작위 사건 (좋을 수도 나쁠 수도)";
            });
            for (int s : here) lore.add(SEAT_COLOR[s] + "● " + who(s).substring(2) + " 말" + (s == me ? " &7(나)" : ""));
            if (phase == 2 && mine) lore.add("&e▶ 클릭: 이 칸으로 우주여행");
            v.btn(SLOT[i], BoardIcons.of(icon, (hotel[i] ? 2 : 1), name(i), lore), () -> clickTile(v, idx));
        }
        // 자리 판
        for (int s = 0; s < seats; s++) {
            List<String> pl = new ArrayList<>();
            pl.add(seatLine(s, me));
            if (broke[s]) pl.add("&8파산 — 탈락");
            else {
                pl.add("&7현금 " + money[s] + " · 총 재산 " + assets(s));
                pl.add("&7땅 " + count(s) + "곳" + (islandSkip[s] > 0 ? " · &b무인도에서 쉬는 중" : ""));
            }
            if (s == turn) pl.add("&e◀ 지금 차례");
            v.set(PANEL[s], BoardIcons.of(broke[s] ? BoardIcons.FOLD : BoardIcons.TOK_SEAT + s, who(s) + " &f" + (broke[s] ? "파산" : Text.num(money[s])) + (s == turn ? " &e◀" : ""), pl));
        }
        v.set(12, BoardIcons.of(BoardIcons.DICE + d1, "&f주사위 " + d1));
        v.set(14, BoardIcons.of(BoardIcons.DICE + d2, "&f주사위 " + d2));
        boolean canRoll = mine && phase == 0;
        v.btn(13, BoardIcons.of(phase == 2 && mine ? BoardIcons.TRAVEL : canRoll ? BoardIcons.ROLL : BoardIcons.DICE + 1,
                phase == 2 && mine ? "&9&l우주여행 — 갈 칸을 클릭" : canRoll ? "&e&l주사위 굴리기!" : mine ? "&7땅을 살지 정하세요" : who(turn) + " &7차례…",
                canRoll ? "&7클릭하여 굴리기 (더블이면 한 번 더)" : ""), () -> roll(v));
        if (phase == 1 && mine) {
            int i = at[me];
            boolean build = owner[i] == me;
            long cost = price(i);
            v.btn(30, BoardIcons.of(BoardIcons.CALL, build ? "&a&l호텔 짓기 &f(" + cost + ")" : "&a&l땅 사기 &f(" + cost + ")",
                    "&7" + nm(i), build ? "&7통행료 ×3" : "&7같은 색 3곳을 모으면 통행료 ×2", money[me] < cost ? "&c돈이 모자람" : "&e▶ 클릭"), () -> decide(v, true));
            v.btn(32, BoardIcons.of(BoardIcons.FOLD, "&7넘기기", "&7사지 않고 차례를 마침"), () -> decide(v, false));
        }
        v.set(28, BoardIcons.of(BoardIcons.START, Math.min(64, round), "&f" + round + " &7/ " + maxRounds + " 바퀴", "&7" + maxRounds + "바퀴가 끝나면 총 재산이 가장 많은 사람 승리"));
        v.set(34, BoardIcons.of(BoardIcons.FUND, "&6사회복지기금 &f" + fund, "&7'기금 받기' 칸에 멈추면 모두 받음"));
        List<String> ll = new ArrayList<>();
        for (String s : log) ll.add("&f" + s);
        v.set(40, BoardIcons.of(BoardIcons.MONEY, (turn == me ? "&l내 차례 " : "") + who(turn) + " &f차례", ll));
    }

    // ------------------------------------------------------------------ 진행
    private void roll(View v) {
        if (!myMove(v) || phase != 0) return;
        turnRoll();
    }

    /** 지금 차례 자리가 주사위를 굴려 움직임 */
    private void turnRoll() {
        int side = turn;
        if (islandSkip[side] > 0) {
            islandSkip[side]--;
            log(plain(side) + ": 무인도에서 한 번 쉼");
            endTurn(false);
            return;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        d1 = r.nextInt(1, 7);
        d2 = r.nextInt(1, 7);
        boolean dbl = d1 == d2;
        sound(Sound.BLOCK_BAMBOO_HIT, 1f, 1.2f);
        if (dbl) doubles++; else doubles = 0;
        if (doubles >= 3) {
            doubles = 0;
            at[side] = ISLAND;
            islandSkip[side] = 1;
            log(plain(side) + ": 더블 세 번! 무인도로");
            endTurn(false);
            return;
        }
        log(plain(side) + ": " + d1 + " + " + d2 + (dbl ? " 더블!" : ""));
        moveBy(side, d1 + d2);
        land(side, dbl);
    }

    private void moveBy(int side, int k) {
        int to = at[side] + k;
        if (to >= N) {
            to -= N;
            money[side] += salary;
            log(plain(side) + ": 출발점 통과 월급 +" + salary);
        }
        if (to < 0) to += N;
        at[side] = to;
    }

    private void land(int side, boolean dbl) {
        int i = at[side];
        int c = cityOf(i);
        if (c >= 0) {
            int o = owner[i];
            if (o >= 0 && o != side) {
                long rent = rent(i);
                pay(side, o, rent);
                log(plain(side) + ": " + nm(i) + " 통행료 " + rent + " → " + plain(o));
                sound(Sound.ENTITY_VILLAGER_NO, 0.5f, 1f);
                if (money[side] < 0) { bankrupt(side); return; }
                endTurn(dbl);
                return;
            }
            boolean canBuy = o < 0 && money[side] >= price(i), canBuild = o == side && !hotel[i] && money[side] >= price(i);
            if (canBuy || canBuild) {
                if (!isAI(side)) {
                    phase = 1;
                    pendingDouble = dbl;
                    refresh();
                } else {
                    aiDecide(side, i, canBuild);
                    endTurn(dbl);
                }
                return;
            }
            endTurn(dbl);
            return;
        }
        switch (i) {
            case ISLAND -> { islandSkip[side] = 1; log(plain(side) + ": 무인도에 갇힘 (한 번 쉼)"); dbl = false; }
            case FUND_PAY -> { long f = Math.min(100, Math.max(0, money[side])); money[side] -= 100; fund += f; log(plain(side) + ": 기금 100 냄"); if (money[side] < 0) { bankrupt(side); return; } }
            case FUND_GET -> { money[side] += fund; log(plain(side) + ": 기금 " + fund + " 받음!"); fund = 0; }
            case TRAVEL -> { travelNext[side] = true; log(plain(side) + ": 우주여행 — 다음 차례에 원하는 칸으로"); }
            case GO -> { }
            default -> { if (CHANCE.contains(i)) { chance(side); return; } }
        }
        endTurn(dbl);
    }

    private void pay(int from, int to, long n) {
        money[from] -= n;
        money[to] += Math.min(n, Math.max(0, money[from] + n));
    }

    private void chance(int side) {
        int k = ThreadLocalRandom.current().nextInt(7);
        String w = plain(side);
        switch (k) {
            case 0 -> { money[side] += 150; log(w + ": 황금열쇠 — 복권 당첨 +150"); }
            case 1 -> { money[side] -= 100; fund += 100; log(w + ": 황금열쇠 — 과속 벌금 100 (기금으로)"); if (money[side] < 0) { bankrupt(side); return; } }
            case 2 -> { at[side] = GO; money[side] += salary; log(w + ": 황금열쇠 — 출발점으로! 월급 +" + salary); }
            case 3 -> { at[side] = ISLAND; islandSkip[side] = 1; log(w + ": 황금열쇠 — 무인도로 표류"); endTurn(false); return; }
            case 4 -> { log(w + ": 황금열쇠 — 3칸 뒤로"); at[side] = (at[side] - 3 + N) % N; land(side, false); return; }
            case 5 -> {
                long got = 0;
                for (int o = 0; o < seats; o++) {
                    if (o == side || broke[o]) continue;
                    long n = Math.min(50, Math.max(0, money[o]));
                    money[o] -= n;
                    got += n;
                }
                money[side] += got;
                log(w + ": 황금열쇠 — 생일 축하금 " + got + " (모두에게서)");
            }
            default -> { travelNext[side] = true; log(w + ": 황금열쇠 — 우주여행 초대권"); }
        }
        sound(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.3f);
        endTurn(false);
    }

    private void decide(View v, boolean yes) {
        if (!myMove(v) || phase != 1) return;
        int me = v.seat, i = at[me];
        if (yes) {
            long cost = price(i);
            if (money[me] < cost) { say(v, "&c돈이 모자랍니다."); return; }
            money[me] -= cost;
            if (owner[i] == me) { hotel[i] = true; log(plain(me) + ": " + nm(i) + " 호텔 건설!"); }
            else { owner[i] = me; log(plain(me) + ": " + nm(i) + " 구매" + (fullSet(i) ? " — 같은 색 독점!" : "")); }
            sound(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
        }
        phase = 0;
        endTurn(pendingDouble);
    }

    private void clickTile(View v, int idx) {
        if (!myMove(v) || phase != 2) return;
        phase = 0;
        int me = v.seat;
        travelNext[me] = false;
        if (idx < at[me]) { money[me] += salary; log(plain(me) + ": 출발점 통과 월급 +" + salary); }
        at[me] = idx;
        log(plain(me) + ": 우주여행 → " + nm(idx));
        sound(Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 1f);
        land(me, false);
    }

    private void endTurn(boolean again) {
        if (over) return;
        if (again && !broke[turn]) {
            log("더블! " + plain(turn) + " 한 번 더");
            phase = 0;
            refresh();
            if (isAI(turn)) startAi();
            return;
        }
        doubles = 0;
        phase = 0;
        int before = turn;
        // 다음 자리 (파산한 사람은 건너뜀). 처음 자리로 돌아오면 한 바퀴
        int nx = before;
        for (int k = 1; k <= seats; k++) {
            int c = (before + k) % seats;
            if (!broke[c]) { nx = c; break; }
        }
        if (nx <= before) {
            round++;
            if (round > maxRounds) { endByAssets(); return; }
        }
        turn = nx;
        if (travelNext[turn] && !isAI(turn)) { phase = 2; log("우주여행: " + plain(turn) + " 님, 갈 칸을 클릭하세요"); }
        refresh();
        if (isAI(turn)) startAi();
    }

    // ------------------------------------------------------------------ 컴퓨터
    @Override
    protected void aiTurn() {
        if (over || !isAI(turn)) return;
        int side = turn;
        if (phase == 1) {   // 사람이 고르다 나간 경우
            phase = 0;
            int i = at[side];
            aiDecide(side, i, owner[i] == side);
            endTurn(pendingDouble);
            return;
        }
        if (travelNext[side]) {
            travelNext[side] = false;
            phase = 0;
            int best = GO;
            double bs = -1;
            for (int i = 0; i < N; i++) {
                double s = cityOf(i) >= 0 && owner[i] < 0 && money[side] >= price(i) ? price(i) + 50
                        : cityOf(i) >= 0 && owner[i] == side && !hotel[i] && money[side] >= price(i) * 2 ? price(i)
                        : i == FUND_GET ? fund : 0;
                if (s > bs) { bs = s; best = i; }
            }
            if (best < at[side]) money[side] += salary;
            at[side] = best;
            log(plain(side) + ": 우주여행 → " + nm(best));
            refresh();
            later(18, () -> land(side, false));
            return;
        }
        turnRoll();
    }

    private void aiDecide(int side, int i, boolean build) {
        long cost = price(i);
        long keep = 150 + rentThreat(side);
        boolean wantSet = groupOwned(i, side) >= 1;
        if (money[side] >= cost && (money[side] - cost >= keep || wantSet && money[side] - cost >= keep / 2)) {
            money[side] -= cost;
            if (build) { hotel[i] = true; log(plain(side) + ": " + nm(i) + " 호텔 건설!"); }
            else { owner[i] = side; log(plain(side) + ": " + nm(i) + " 구매"); }
        } else log(plain(side) + ": " + nm(i) + " 은(는) 넘김");
    }

    private int groupOwned(int idx, int side) {
        int g = group(idx), c = 0;
        for (int k = 0; k < 3; k++) if (owner[CITY_IDX[g * 3 + k]] == side) c++;
        return c;
    }

    private long rentThreat(int side) {
        long m = 0;
        for (int i = 0; i < N; i++) if (owner[i] >= 0 && owner[i] != side) m = Math.max(m, rent(i));
        return m;
    }

    // ------------------------------------------------------------------ 끝
    private void bankrupt(int side) {
        broke[side] = true;
        for (int i = 0; i < N; i++) if (owner[i] == side) { owner[i] = -1; hotel[i] = false; }
        log(plain(side) + ": 파산! 탈락 (땅은 주인이 없어짐)");
        sound(Sound.ENTITY_WITHER_HURT, 0.5f, 1.4f);
        if (alive() <= 1) {
            for (int s = 0; s < seats; s++) if (!broke[s]) { finish(s, "마지막까지 살아남았습니다!"); return; }
        }
        endTurn(false);
    }

    private void endByAssets() {
        int best = -1;
        long ba = -1;
        StringBuilder sb = new StringBuilder();
        for (int s = 0; s < seats; s++) {
            if (broke[s]) continue;
            long a = assets(s);
            sb.append(sb.length() > 0 ? " · " : "").append(plain(s)).append(" ").append(a);
            if (a > ba) { ba = a; best = s; }
        }
        finish(best, maxRounds + "바퀴 끝 — 재산 " + sb);
    }
}
