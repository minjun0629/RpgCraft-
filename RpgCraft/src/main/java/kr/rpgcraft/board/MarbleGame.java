package kr.rpgcraft.board;

import kr.rpgcraft.util.Text;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * v5.10.45 부루마블 (컴퓨터와 1대1). 창 테두리 26칸이 판 (오른쪽 아래 = 출발).
 *  주사위 2개 → 도착한 도시를 사거나 (같은 색 3곳이면 통행료 2배 · 호텔 짓기 3배), 상대 도시면 통행료.
 *  출발점을 지나면 월급, 무인도는 한 번 쉼, 황금열쇠(?)는 무작위 사건, 우주여행은 다음 차례에 원하는 칸으로, 사회복지기금은 모아서 받기.
 *  더블은 한 번 더 (세 번 연속이면 무인도). 상대를 파산시키거나 20바퀴 뒤 재산이 많으면 승리.
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

    private final int salary, maxRounds;
    private final long[] money = new long[2];
    private final int[] at = new int[2], islandSkip = new int[2];
    private final boolean[] travelNext = new boolean[2];
    private final int[] owner = new int[N];       // -1 없음 · 0 나 · 1 컴퓨터
    private final boolean[] hotel = new boolean[N];
    private long fund;
    private int round = 1, doubles, d1 = 1, d2 = 1;
    private boolean myTurn = true;
    /** 0 굴리기 · 1 살지 정하기 · 2 우주여행 칸 고르기 */
    private int phase;
    private final Deque<String> log = new ArrayDeque<>();

    public MarbleGame(BoardManager mgr, Player p) {
        super(mgr, p, BoardManager.BoardGame.MARBLE, "&8부루마블 &7- 나(빨강) vs 컴퓨터(파랑)");
        Arrays.fill(owner, -1);
        long start = plugin.getConfig().getLong("board.marble-start-money", 1500);
        money[0] = money[1] = start;
        salary = plugin.getConfig().getInt("board.marble-salary", 200);
        maxRounds = plugin.getConfig().getInt("board.marble-rounds", 20);
        log("게임 시작! 주사위를 굴리세요.");
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

    private long assets(int side) {
        long a = money[side];
        for (int i = 0; i < N; i++) if (owner[i] == side) a += price(i) + (hotel[i] ? price(i) : 0);
        return a;
    }

    private void log(String s) {
        log.addFirst(s);
        while (log.size() > 6) log.removeLast();
    }

    // ------------------------------------------------------------------ 화면
    @Override
    protected void setup() {
        render();
    }

    private void render() {
        for (int i = 0; i < N; i++) {
            int idx = i;
            boolean me = at[0] == i, ai = at[1] == i;
            int c = cityOf(i);
            int icon;
            if (me && ai) icon = BoardIcons.TOK_BOTH;
            else if (me) icon = BoardIcons.TOK_ME;
            else if (ai) icon = BoardIcons.TOK_AI;
            else if (c >= 0) icon = (owner[i] == 0 ? BoardIcons.CITY_ME : owner[i] == 1 ? BoardIcons.CITY_AI : BoardIcons.CITY) + GROUP_COLOR[c / 3];
            else icon = i == GO ? BoardIcons.START : i == ISLAND ? BoardIcons.ISLAND : i == TRAVEL ? BoardIcons.TRAVEL : (i == FUND_GET || i == FUND_PAY) ? BoardIcons.FUND : BoardIcons.CHANCE;
            List<String> lore = new ArrayList<>();
            if (c >= 0) {
                lore.add("&f땅값 &e" + price(i) + " &7· 통행료 &e" + rent(i) + (fullSet(i) ? " &a(같은 색 독점 ×2)" : "") + (hotel[i] ? " &d(호텔 ×3)" : ""));
                lore.add(owner[i] < 0 ? "&7주인 없음" : owner[i] == 0 ? "&c내 땅" : "&9컴퓨터 땅");
            } else lore.add("&7" + switch (i) {
                case GO -> "지나가면 월급 " + salary;
                case ISLAND -> "도착하면 한 번 쉼";
                case FUND_GET -> "모인 기금 " + fund + " 을(를) 모두 받음";
                case FUND_PAY -> "기금 100 을 냄";
                case TRAVEL -> "다음 차례에 원하는 칸으로 날아감";
                default -> "무작위 사건 (좋을 수도 나쁠 수도)";
            });
            if (me) lore.add("&c● 내 말");
            if (ai) lore.add("&9● 컴퓨터 말");
            if (phase == 2 && myTurn) lore.add("&e▶ 클릭: 이 칸으로 우주여행");
            set(SLOT[i], BoardIcons.of(icon, (hotel[i] ? 2 : 1), name(i), lore), e -> clickTile(idx));
        }
        // 가운데
        set(10, BoardIcons.of(BoardIcons.TOK_ME, "&c&l나 &f" + Text.num(money[0]), "&7현금 " + money[0], "&7총 재산 " + assets(0), "&7땅 " + count(0) + "곳",
                islandSkip[0] > 0 ? "&b무인도에서 쉬는 중" : ""));
        set(16, BoardIcons.of(BoardIcons.TOK_AI, "&9&l컴퓨터 &f" + Text.num(money[1]), "&7현금 " + money[1], "&7총 재산 " + assets(1), "&7땅 " + count(1) + "곳",
                islandSkip[1] > 0 ? "&b무인도에서 쉬는 중" : ""));
        set(12, BoardIcons.of(BoardIcons.DICE + d1, "&f주사위 " + d1));
        set(14, BoardIcons.of(BoardIcons.DICE + d2, "&f주사위 " + d2));
        boolean canRoll = myTurn && phase == 0 && !busy;
        set(13, BoardIcons.of(phase == 2 && myTurn ? BoardIcons.TRAVEL : canRoll ? BoardIcons.ROLL : BoardIcons.DICE + 1,
                phase == 2 && myTurn ? "&9&l우주여행 — 갈 칸을 클릭" : canRoll ? "&e&l주사위 굴리기!" : myTurn ? "&7땅을 살지 정하세요" : "&7컴퓨터 차례…",
                canRoll ? "&7클릭하여 굴리기 (더블이면 한 번 더)" : ""), e -> roll());
        if (phase == 1 && myTurn) {
            int i = at[0];
            boolean build = owner[i] == 0;
            long cost = price(i);
            set(30, BoardIcons.of(BoardIcons.CALL, build ? "&a&l호텔 짓기 &f(" + cost + ")" : "&a&l땅 사기 &f(" + cost + ")",
                    "&7" + Text.strip(Text.c(name(i))), build ? "&7통행료 ×3" : "&7같은 색 3곳을 모으면 통행료 ×2", money[0] < cost ? "&c돈이 모자람" : "&e▶ 클릭"), e -> decide(true));
            set(32, BoardIcons.of(BoardIcons.FOLD, "&7넘기기", "&7사지 않고 차례를 마침"), e -> decide(false));
        } else {
            set(30, kr.rpgcraft.pack.PackManager.filler(org.bukkit.Material.GRAY_STAINED_GLASS_PANE), e -> { });
            set(32, kr.rpgcraft.pack.PackManager.filler(org.bukkit.Material.GRAY_STAINED_GLASS_PANE), e -> { });
        }
        set(28, BoardIcons.of(BoardIcons.START, Math.min(64, round), "&f" + round + " &7/ " + maxRounds + " 바퀴", "&7" + maxRounds + "바퀴가 끝나면 총 재산이 많은 쪽 승리"));
        set(34, BoardIcons.of(BoardIcons.FUND, "&6사회복지기금 &f" + fund, "&7'기금 받기' 칸에 멈추면 모두 받음"));
        List<String> ll = new ArrayList<>();
        for (String s : log) ll.add("&f" + s);
        set(40, BoardIcons.of(BoardIcons.MONEY, myTurn ? "&c&l내 차례" : "&9&l컴퓨터 차례", ll));
        fill(0, 53);
    }

    private int count(int side) {
        int c = 0;
        for (int i = 0; i < N; i++) if (owner[i] == side) c++;
        return c;
    }

    // ------------------------------------------------------------------ 진행
    private void roll() {
        if (over || busy || !myTurn || phase != 0) return;
        turnRoll(0);
    }

    /** side 가 주사위를 굴려 움직임 (무인도 · 우주여행 처리 포함) */
    private void turnRoll(int side) {
        String who = side == 0 ? "나" : "컴퓨터";
        if (islandSkip[side] > 0) {
            islandSkip[side]--;
            log(who + ": 무인도에서 한 번 쉼");
            endTurn(side, false);
            return;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        d1 = r.nextInt(1, 7);
        d2 = r.nextInt(1, 7);
        boolean dbl = d1 == d2;
        p.playSound(p.getLocation(), Sound.BLOCK_BAMBOO_HIT, 1f, 1.2f);
        if (dbl) doubles++; else doubles = 0;
        if (doubles >= 3) {
            doubles = 0;
            at[side] = ISLAND;
            islandSkip[side] = 1;
            log(who + ": 더블 세 번! 무인도로");
            endTurn(side, false);
            return;
        }
        log(who + ": " + d1 + " + " + d2 + (dbl ? " 더블!" : ""));
        moveBy(side, d1 + d2);
        land(side, dbl);
    }

    private void moveBy(int side, int k) {
        int to = at[side] + k;
        if (to >= N) {
            to -= N;
            money[side] += salary;
            log((side == 0 ? "나" : "컴퓨터") + ": 출발점 통과 월급 +" + salary);
        }
        if (to < 0) to += N;
        at[side] = to;
    }

    /** 도착 처리. dbl = 더블이라 한 번 더 */
    private void land(int side, boolean dbl) {
        int i = at[side];
        String who = side == 0 ? "나" : "컴퓨터";
        int c = cityOf(i);
        if (c >= 0) {
            if (owner[i] == 1 - side) {
                long rent = rent(i);
                pay(side, 1 - side, rent);
                log(who + ": " + Text.strip(Text.c(name(i))) + " 통행료 " + rent);
                if (side == 0) p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 1f);
                if (money[side] < 0) { bankrupt(side); return; }
                endTurn(side, dbl);
                return;
            }
            boolean canBuy = owner[i] < 0 && money[side] >= price(i), canBuild = owner[i] == side && !hotel[i] && money[side] >= price(i);
            if (canBuy || canBuild) {
                if (side == 0) {
                    phase = 1;
                    pendingDouble = dbl;
                    render();
                } else {
                    aiDecide(i, canBuild);
                    endTurn(1, dbl);
                }
                return;
            }
            endTurn(side, dbl);
            return;
        }
        switch (i) {
            case ISLAND -> { islandSkip[side] = 1; log(who + ": 무인도에 갇힘 (한 번 쉼)"); dbl = false; }
            case FUND_PAY -> { long f = Math.min(100, Math.max(0, money[side])); money[side] -= 100; fund += f; log(who + ": 기금 100 냄"); if (money[side] < 0) { bankrupt(side); return; } }
            case FUND_GET -> { money[side] += fund; log(who + ": 기금 " + fund + " 받음!"); fund = 0; }
            case TRAVEL -> { travelNext[side] = true; log(who + ": 우주여행 — 다음 차례에 원하는 칸으로"); }
            case GO -> { }
            default -> { if (CHANCE.contains(i)) { chance(side); return; } }
        }
        endTurn(side, dbl);
    }

    private boolean pendingDouble;

    private void pay(int from, int to, long n) {
        money[from] -= n;
        money[to] += Math.min(n, Math.max(0, money[from] + n));
    }

    private void chance(int side) {
        String who = side == 0 ? "나" : "컴퓨터";
        int k = ThreadLocalRandom.current().nextInt(7);
        switch (k) {
            case 0 -> { money[side] += 150; log(who + ": 황금열쇠 — 복권 당첨 +150"); }
            case 1 -> { money[side] -= 100; fund += 100; log(who + ": 황금열쇠 — 과속 벌금 100 (기금으로)"); if (money[side] < 0) { bankrupt(side); return; } }
            case 2 -> { at[side] = GO; money[side] += salary; log(who + ": 황금열쇠 — 출발점으로! 월급 +" + salary); }
            case 3 -> { at[side] = ISLAND; islandSkip[side] = 1; log(who + ": 황금열쇠 — 무인도로 표류"); endTurn(side, false); return; }
            case 4 -> { log(who + ": 황금열쇠 — 3칸 뒤로"); at[side] = (at[side] - 3 + N) % N; land(side, false); return; }
            case 5 -> { long n = Math.min(80, Math.max(0, money[1 - side])); money[1 - side] -= n; money[side] += n; log(who + ": 황금열쇠 — 상대에게 생일 축하금 " + n); }
            default -> { travelNext[side] = true; log(who + ": 황금열쇠 — 우주여행 초대권"); }
        }
        if (side == 0) p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.3f);
        endTurn(side, false);
    }

    private void decide(boolean yes) {
        if (over || busy || !myTurn || phase != 1) return;
        int i = at[0];
        if (yes) {
            long cost = price(i);
            if (money[0] < cost) { say("&c돈이 모자랍니다."); return; }
            money[0] -= cost;
            if (owner[i] == 0) { hotel[i] = true; log("나: " + Text.strip(Text.c(name(i))) + " 호텔 건설!"); }
            else { owner[i] = 0; log("나: " + Text.strip(Text.c(name(i))) + " 구매" + (fullSet(i) ? " — 같은 색 독점!" : "")); }
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
        }
        phase = 0;
        endTurn(0, pendingDouble);
    }

    private void clickTile(int idx) {
        if (over || busy || !myTurn || phase != 2) return;
        phase = 0;
        travelNext[0] = false;
        if (idx < at[0]) { money[0] += salary; log("나: 출발점 통과 월급 +" + salary); }
        at[0] = idx;
        log("나: 우주여행 → " + Text.strip(Text.c(name(idx))));
        p.playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 1f);
        land(0, false);
    }

    private void endTurn(int side, boolean again) {
        if (over) return;
        if (side == 0) {
            if (again) { log("더블! 한 번 더 굴리세요"); phase = 0; render(); return; }
            myTurn = false;
            busy = true;
            doubles = 0;
            render();
            later(22, this::aiTurn);
        } else {
            if (again) { render(); later(22, this::aiTurn); return; }
            doubles = 0;
            round++;
            if (round > maxRounds) { endByAssets(); return; }
            myTurn = true;
            busy = false;
            phase = travelNext[0] ? 2 : 0;
            if (phase == 2) log("우주여행: 갈 칸을 클릭하세요");
            render();
        }
    }

    // ------------------------------------------------------------------ 컴퓨터
    private void aiTurn() {
        if (over) return;
        if (travelNext[1]) {
            travelNext[1] = false;
            int best = GO;
            double bs = -1;
            for (int i = 0; i < N; i++) {   // 살 수 있는 비싼 땅 · 내 땅(호텔) · 기금
                double s = cityOf(i) >= 0 && owner[i] < 0 && money[1] >= price(i) ? price(i) + 50
                        : cityOf(i) >= 0 && owner[i] == 1 && !hotel[i] && money[1] >= price(i) * 2 ? price(i)
                        : i == FUND_GET ? fund : 0;
                if (s > bs) { bs = s; best = i; }
            }
            if (best < at[1]) money[1] += salary;
            at[1] = best;
            log("컴퓨터: 우주여행 → " + Text.strip(Text.c(name(best))));
            render();
            later(18, () -> land(1, false));
            return;
        }
        turnRoll(1);
        if (!over) render();
    }

    private void aiDecide(int i, boolean build) {
        long cost = price(i);
        long keep = 150 + rentThreat();   // 통행료를 낼 돈은 남겨 둠
        boolean wantSet = groupOwned(i, 1) >= 1;
        if (money[1] - cost >= keep || wantSet && money[1] - cost >= keep / 2) {
            money[1] -= cost;
            if (build) { hotel[i] = true; log("컴퓨터: " + Text.strip(Text.c(name(i))) + " 호텔 건설!"); }
            else { owner[i] = 1; log("컴퓨터: " + Text.strip(Text.c(name(i))) + " 구매"); }
        } else log("컴퓨터: " + Text.strip(Text.c(name(i))) + " 은(는) 넘김");
    }

    private int groupOwned(int idx, int side) {
        int g = group(idx), c = 0;
        for (int k = 0; k < 3; k++) if (owner[CITY_IDX[g * 3 + k]] == side) c++;
        return c;
    }

    private long rentThreat() {
        long m = 0;
        for (int i = 0; i < N; i++) if (owner[i] == 0) m = Math.max(m, rent(i));
        return m;
    }

    // ------------------------------------------------------------------ 끝
    private void bankrupt(int side) {
        render();
        if (side == 0) finish(false, "파산했습니다…");
        else finish(true, "컴퓨터를 파산시켰습니다!");
    }

    private void endByAssets() {
        long a = assets(0), b = assets(1);
        render();
        if (a >= b) finish(true, maxRounds + "바퀴 끝 — 재산 " + a + " vs " + b);
        else finish(false, maxRounds + "바퀴 끝 — 재산 " + a + " vs " + b);
    }
}
