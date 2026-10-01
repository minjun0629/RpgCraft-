package kr.rpgcraft.minigame;

import kr.rpgcraft.pack.PackManager;
import kr.rpgcraft.util.Text;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ThreadLocalRandom;

/**
 * v5.10.31 뱀 게임: 9x5 풀밭에서 뱀을 움직여 사과를 먹고 길어지기.
 * - 맨 아래 줄 ◀ ▲ ▼ ▶ 로 방향, 또는 풀밭을 클릭하면 그쪽으로 꺾음.
 * - 황금 사과 +2 (잠깐만 있음). 쉬움 · 보통은 벽을 넘어 반대편으로, 어려움은 벽 · 바위에 부딪히면 끝.
 * - 목표 개수만큼 먹으면 성공, 내 몸에 부딪히면 실패.
 */
public class SnakeGame extends MiniGame {
    private static final int W = 9, H = 5;
    private static final int[] DX = {0, 1, 0, -1}, DY = {-1, 0, 1, 0};   // 0 ↑ 1 → 2 ↓ 3 ←
    private static final int[] BTN = {48, 52, 50, 46};                   // ↑ → ↓ ←

    private final Deque<int[]> body = new ArrayDeque<>();   // 앞이 머리
    private final boolean[][] rock = new boolean[H][W];
    private int dir = 1, next = 1, eaten, appleX = -1, appleY, goldX = -1, goldY, goldUntil, grow, wait = 20;
    private final int target, speed;
    private final boolean walls;

    public SnakeGame(MiniGameManager mgr, Player p, Diff d) {
        super(mgr, p, Game.SNAKE, d, "&8뱀 게임 &7- " + d.label, "snake");
        target = d.pick(10, 15, 18);
        speed = d.pick(7, 5, 4);
        walls = d == Diff.HARD;
        body.add(new int[]{3, 2});
        body.add(new int[]{2, 2});
        body.add(new int[]{1, 2});
        if (d == Diff.HARD) { rock[1][6] = true; rock[3][6] = true; rock[0][2] = true; rock[4][2] = true; }
        else if (d == Diff.NORMAL) { rock[1][6] = true; rock[3][2] = true; }
        placeApple();
    }

    @Override
    protected int period() {
        return 1;
    }

    @Override
    protected void setup() {
        render();
    }

    private boolean occupied(int x, int y) {
        if (rock[y][x]) return true;
        for (int[] b : body) if (b[0] == x && b[1] == y) return true;
        return false;
    }

    private void placeApple() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < 200; i++) {
            int x = r.nextInt(W), y = r.nextInt(H);
            if (!occupied(x, y) && !(x == goldX && y == goldY)) { appleX = x; appleY = y; return; }
        }
        appleX = -1;
    }

    private void placeGold() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < 100; i++) {
            int x = r.nextInt(W), y = r.nextInt(H);
            if (!occupied(x, y) && !(x == appleX && y == appleY)) { goldX = x; goldY = y; goldUntil = ticks + 100; return; }
        }
    }

    private void turn(int d) {
        if (over) return;
        if ((d + 2) % 4 == dir) return;   // 바로 뒤로는 못 돎
        next = d;
        if (wait > 0) wait = Math.min(wait, 4);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.4f, 1.8f);
    }

    /** 풀밭 클릭: 머리에서 그 칸 쪽으로 꺾기 (지금 가는 방향과 수직인 쪽) */
    private void clickCell(int x, int y) {
        int[] h = body.peekFirst();
        int dx = x - h[0], dy = y - h[1];
        if (dir == 1 || dir == 3) { if (dy != 0) turn(dy < 0 ? 0 : 2); }
        else if (dx != 0) turn(dx > 0 ? 1 : 3);
    }

    private void render() {
        int[] head = body.peekFirst();
        int[] tail = body.peekLast();
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) {
                int cx = x, cy = y;
                org.bukkit.inventory.ItemStack it;
                if (head[0] == x && head[1] == y) it = Icons.of(Icons.SNAKE_HEAD + dir, "&a뱀 &7(길이 " + body.size() + ")");
                else if (tail[0] == x && tail[1] == y) it = Icons.of(Icons.SNAKE_TAIL, "&2꼬리");
                else if (inBody(x, y)) it = Icons.of(Icons.SNAKE_BODY, "&2몸");
                else if (x == appleX && y == appleY) it = Icons.of(Icons.APPLE, "&c사과 &7+1");
                else if (x == goldX && y == goldY) it = Icons.of(Icons.APPLE_GOLD, "&6&l황금 사과 &e+2", "&7곧 사라집니다!");
                else if (rock[y][x]) it = Icons.of(Icons.ROCK, "&7바위");
                else it = PackManager.filler(Material.LIME_STAINED_GLASS_PANE);
                set(y * 9 + x, it, e -> clickCell(cx, cy));
            }
        String[] names = {"&f▲ 위", "&f▶ 오른쪽", "&f▼ 아래", "&f◀ 왼쪽"};
        for (int d = 0; d < 4; d++) {
            int dd = d;
            set(BTN[d], Icons.of(Icons.ARROW + d, (d == next ? "&a&l" : "") + names[d], "&7풀밭을 클릭해도 그쪽으로 꺾어요"), e -> turn(dd));
        }
        set(45, Icons.of(Icons.APPLE, Math.max(1, eaten), "&f먹은 사과 &e" + eaten + " &7/ " + target));
        set(53, Icons.of(Icons.EASY + diff.ordinal(), "&f" + diff.color + diff.label, walls ? "&c벽 · 바위에 부딪히면 끝" : "&a벽을 넘으면 반대편으로"));
        for (int s : new int[]{47, 49, 51}) set(s, PackManager.filler(Material.GRAY_STAINED_GLASS_PANE));
        Text.actionBar(p, "&a🐍 &f사과 &e" + eaten + "&7/" + target + " &8· &7길이 " + body.size() + (wait > 0 ? " &e· 준비…" : ""));
    }

    private boolean inBody(int x, int y) {
        for (int[] b : body) if (b[0] == x && b[1] == y) return true;
        return false;
    }

    @Override
    protected void tick() {
        if (wait > 0) { wait--; if (wait == 0) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 2f); return; }
        if (goldX >= 0 && ticks > goldUntil) { goldX = -1; render(); }
        if (ticks % speed != 0) return;
        dir = next;
        int[] h = body.peekFirst();
        int nx = h[0] + DX[dir], ny = h[1] + DY[dir];
        if (nx < 0 || nx >= W || ny < 0 || ny >= H) {
            if (walls) { crash("벽에 부딪혔습니다"); return; }
            nx = (nx + W) % W;
            ny = (ny + H) % H;
        }
        if (rock[ny][nx]) { crash("바위에 부딪혔습니다"); return; }
        int[] tail = body.peekLast();
        boolean tailMoves = grow == 0;
        for (int[] b : body) {
            if (b[0] == nx && b[1] == ny && !(tailMoves && b == tail)) { crash("몸에 부딪혔습니다"); return; }
        }
        body.addFirst(new int[]{nx, ny});
        if (grow > 0) grow--;
        else body.removeLast();
        if (nx == appleX && ny == appleY) {
            eaten++;
            grow++;
            p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EAT, 0.8f, 1.2f + eaten * 0.03f);
            placeApple();
            if (goldX < 0 && ThreadLocalRandom.current().nextDouble() < 0.25) placeGold();
        } else if (nx == goldX && ny == goldY) {
            eaten += 2;
            grow += 2;
            goldX = -1;
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.8f);
        } else p.playSound(p.getLocation(), Sound.BLOCK_GRASS_STEP, 0.25f, 1.6f);
        if (eaten >= target) { render(); finish(true, ""); return; }
        if (appleX < 0) { render(); finish(true, ""); return; }   // 꽉 참
        render();
    }

    private void crash(String why) {
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_HURT, 1f, 0.8f);
        render();
        finish(false, why + " (사과 " + eaten + " / " + target + ")");
    }
}
