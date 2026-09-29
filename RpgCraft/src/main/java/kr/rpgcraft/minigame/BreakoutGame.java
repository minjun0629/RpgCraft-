package kr.rpgcraft.minigame;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 벽돌깨기: 창 전체(9x6)가 경기장. 맨 아래 줄을 클릭하면 받침대가 그 칸으로 이동.
 * 공이 받침대 왼쪽 끝에 맞으면 왼쪽, 오른쪽 끝이면 오른쪽으로 튄다. 강철 벽돌은 두 번 맞아야 깨짐. 벽돌을 모두 깨면 성공.
 */
public class BreakoutGame extends MiniGame {
    private final int[][] brick = new int[5][9];   // 0 없음 · 1~6 색 벽돌 · 7 강철 · 8 금 간 강철
    private int bx, by, vx, vy, pad = 4, lives, left, wait;
    private final int width, speed;

    public BreakoutGame(MiniGameManager mgr, Player p, Diff d) {
        super(mgr, p, Game.BREAKOUT, d, "&8벽돌깨기 &7- " + d.label, "breakout");
        int rows = d.pick(2, 3, 4);
        lives = d.pick(3, 2, 1);
        width = d.pick(3, 3, 2);
        speed = d.pick(6, 4, 3);
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < 9; c++) {
                brick[r][c] = d == Diff.HARD && r == 0 && c % 2 == 0 ? 7 : 1 + (r + (d == Diff.EASY ? 0 : c / 3)) % 6;
                left++;
            }
        resetBall();
    }

    @Override
    protected int period() {
        return 1;
    }

    private void resetBall() {
        bx = pad;
        by = 4;
        vx = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
        vy = -1;
        wait = 20;
    }

    @Override
    protected void setup() {
        render();
    }

    private boolean onPad(int x) {
        int l = width == 3 ? pad - 1 : pad - 1, r = width == 3 ? pad + 1 : pad;
        return x >= l && x <= r;
    }

    private void render() {
        for (int r = 0; r < 6; r++)
            for (int c = 0; c < 9; c++) {
                int slot = r * 9 + c, col = c;
                org.bukkit.inventory.ItemStack it = null;
                if (r < 5 && brick[r][c] > 0) {
                    int b = brick[r][c];
                    it = Icons.of(b == 7 ? Icons.STEEL : b == 8 ? Icons.CRACKED : Icons.BRICK + b - 1, b >= 7 ? "&7강철 벽돌 &8(" + (b == 7 ? 2 : 1) + "번)" : "&f벽돌");
                }
                if (r == by && c == bx) it = Icons.of(Icons.BALL, "&f공");
                if (r == 5 && onPad(c)) {
                    int l = width == 3 ? pad - 1 : pad - 1;
                    int part = c == l ? Icons.PAD_L : (width == 3 ? (c == pad ? Icons.PAD_M : Icons.PAD_R) : Icons.PAD_R);
                    it = Icons.of(part, "&b받침대");
                }
                if (it == null) it = kr.rpgcraft.pack.PackManager.filler(org.bukkit.Material.GRAY_STAINED_GLASS_PANE);
                set(slot, it, r == 5 ? e -> move(col) : null);
            }
        kr.rpgcraft.util.Text.actionBar(p, "&c" + "♥".repeat(Math.max(0, lives)) + " &f남은 벽돌 &e" + left + " &7· 맨 아래 줄 클릭 = 받침대 이동");
    }

    private void move(int c) {
        if (over) return;
        pad = Math.max(width == 3 ? 1 : 1, Math.min(width == 3 ? 7 : 8, c));
        if (wait > 0) bx = pad;
        render();
    }

    private void hit(int r, int c) {
        if (brick[r][c] == 7) { brick[r][c] = 8; p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.4f, 1.8f); return; }
        brick[r][c] = 0;
        left--;
        p.playSound(p.getLocation(), Sound.BLOCK_STONE_BREAK, 0.8f, 1.2f + ThreadLocalRandom.current().nextFloat() * 0.4f);
    }

    private boolean isBrick(int x, int y) {
        return y >= 0 && y < 5 && x >= 0 && x < 9 && brick[y][x] > 0;
    }

    @Override
    protected void tick() {
        if (wait > 0) { wait--; if (wait % 5 == 0) render(); return; }
        if (ticks % speed != 0) return;
        int nx = bx + vx, ny = by + vy;
        if (nx < 0 || nx > 8) { vx = -vx; nx = bx + vx; p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.6f); }
        if (ny < 0) { vy = 1; ny = by + vy; p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.6f); }
        if (isBrick(nx, ny)) { hit(ny, nx); vy = -vy; ny = by; nx = bx; }
        else if (isBrick(bx, ny)) { hit(ny, bx); vy = -vy; ny = by; nx = bx + vx; if (nx < 0 || nx > 8) { vx = -vx; nx = bx; } }
        else if (isBrick(nx, by)) { hit(by, nx); vx = -vx; nx = bx; }
        if (ny == 5) {
            if (onPad(nx) || onPad(bx)) {   // 받침대에 튕김
                int l = pad - 1, r = width == 3 ? pad + 1 : pad;
                if (nx <= l) vx = -1;
                else if (nx >= r) vx = 1;
                vy = -1;
                ny = by;
                nx = Math.max(0, Math.min(8, bx + vx));
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.5f);
            } else {
                lives--;
                p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 0.8f);
                if (lives <= 0) { bx = nx; by = 5; render(); finish(false, "공을 놓쳤습니다 (남은 벽돌 " + left + ")"); return; }
                resetBall();
                render();
                return;
            }
        }
        bx = Math.max(0, Math.min(8, nx));
        by = Math.max(0, Math.min(4, ny));
        if (left <= 0) { render(); finish(true, ""); return; }
        render();
    }
}
