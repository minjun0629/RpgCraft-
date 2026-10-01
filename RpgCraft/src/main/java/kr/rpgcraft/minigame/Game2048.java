package kr.rpgcraft.minigame;

import kr.rpgcraft.pack.PackManager;
import kr.rpgcraft.util.Text;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * v5.10.31 2048: 4x4 판의 숫자 타일을 한쪽으로 밀어 같은 숫자끼리 합치기.
 * 오른쪽 ▲ ◀ ▶ ▼ 로 밀기. 목표 숫자(쉬움 128 · 보통 256 · 어려움 512)를 만들면 성공, 더 밀 수 없으면 실패.
 * 어려움은 새 타일에 4가 더 자주 나오고, 제한 시간 6분.
 */
public class Game2048 extends MiniGame {
    private static final int N = 4, X0 = 1;
    private static final int[] BTN = {16, 26, 34, 24};   // ↑ → ↓ ←

    private final int[][] g = new int[N][N];   // 0 빈칸, 그 외 지수 (1 = 2, 2 = 4 …)
    private final boolean[][] merged = new boolean[N][N], fresh = new boolean[N][N];
    private int score, moves;
    private final int goalExp, limit;
    private final double four;

    public Game2048(MiniGameManager mgr, Player p, Diff d) {
        super(mgr, p, Game.G2048, d, "&82048 &7- " + d.label, "g2048");
        goalExp = d.pick(7, 8, 9);
        four = d.pick(0.1, 0.1, 0.25);
        limit = d.pick(0, 0, 7200);
        spawn();
        spawn();
    }

    @Override
    protected int period() {
        return 10;
    }

    @Override
    protected void setup() {
        render();
    }

    private void spawn() {
        List<int[]> empty = new ArrayList<>();
        for (int r = 0; r < N; r++) for (int c = 0; c < N; c++) if (g[r][c] == 0) empty.add(new int[]{r, c});
        if (empty.isEmpty()) return;
        int[] e = empty.get(ThreadLocalRandom.current().nextInt(empty.size()));
        g[e[0]][e[1]] = ThreadLocalRandom.current().nextDouble() < four ? 2 : 1;
        fresh[e[0]][e[1]] = true;
    }

    /** dir: 0 ↑ 1 → 2 ↓ 3 ← */
    private void push(int dir) {
        if (over) return;
        for (boolean[] row : merged) java.util.Arrays.fill(row, false);
        for (boolean[] row : fresh) java.util.Arrays.fill(row, false);
        boolean moved = false;
        int bestMerge = 0;
        for (int line = 0; line < N; line++) {
            int[] vals = new int[N];
            int[][] pos = new int[N][];
            for (int k = 0; k < N; k++) {   // k = 0 이 미는 쪽 끝
                int r, c;
                switch (dir) {
                    case 0 -> { r = k; c = line; }
                    case 2 -> { r = N - 1 - k; c = line; }
                    case 3 -> { r = line; c = k; }
                    default -> { r = line; c = N - 1 - k; }
                }
                pos[k] = new int[]{r, c};
                vals[k] = g[r][c];
            }
            int[] out = new int[N];
            boolean[] mOut = new boolean[N];
            int w = 0;
            for (int k = 0; k < N; k++) {
                if (vals[k] == 0) continue;
                if (w > 0 && out[w - 1] == vals[k] && !mOut[w - 1]) {
                    out[w - 1]++;
                    mOut[w - 1] = true;
                    score += 1 << out[w - 1];
                    bestMerge = Math.max(bestMerge, out[w - 1]);
                } else out[w++] = vals[k];
            }
            for (int k = 0; k < N; k++) {
                int[] ps = pos[k];
                if (g[ps[0]][ps[1]] != out[k]) moved = true;
                g[ps[0]][ps[1]] = out[k];
                merged[ps[0]][ps[1]] = mOut[k];
            }
        }
        if (!moved) { p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.6f); return; }
        moves++;
        spawn();
        if (bestMerge > 0) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, (float) Math.min(2.0, 0.6 + bestMerge * 0.13));
        else p.playSound(p.getLocation(), Sound.BLOCK_WOOD_PLACE, 0.5f, 1.4f);
        if (bestMerge >= 6 && bestMerge > 0) p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f);
        render();
        if (max() >= goalExp) { finish(true, ""); return; }
        if (!canMove()) finish(false, "더 밀 수 없습니다 (최고 " + (1 << max()) + " / 목표 " + (1 << goalExp) + ")");
    }

    private int max() {
        int m = 0;
        for (int[] row : g) for (int v : row) m = Math.max(m, v);
        return m;
    }

    private boolean canMove() {
        for (int r = 0; r < N; r++) for (int c = 0; c < N; c++) {
            if (g[r][c] == 0) return true;
            if (r + 1 < N && g[r + 1][c] == g[r][c]) return true;
            if (c + 1 < N && g[r][c + 1] == g[r][c]) return true;
        }
        return false;
    }

    private void render() {
        for (int i = 0; i < 54; i++) set(i, PackManager.filler(Material.GRAY_STAINED_GLASS_PANE));
        for (int r = 0; r < N; r++) for (int c = 0; c < N; c++) {
            int v = g[r][c];
            int slot = (r + 1) * 9 + X0 + c;
            if (v == 0) set(slot, Icons.of(Icons.EMPTY2048, "&8빈칸"));
            else {
                String col = v >= goalExp ? "&6&l" : v >= 6 ? "&e&l" : "&f";
                set(slot, Icons.of(Icons.TILE2048 + Math.min(11, v - 1), col + (1 << v) + (merged[r][c] ? " &a+합침" : fresh[r][c] ? " &7(새로)" : "")));
            }
        }
        String[] names = {"&f&l▲ 위로 밀기", "&f&l▶ 오른쪽으로 밀기", "&f&l▼ 아래로 밀기", "&f&l◀ 왼쪽으로 밀기"};
        for (int d = 0; d < 4; d++) {
            int dd = d;
            set(BTN[d], Icons.of(Icons.ARROW + d, names[d]), e -> push(dd));
        }
        set(25, Icons.of(Icons.GAME2048, "&e&l2048", "&7같은 숫자를 밀어 합치기", "&7목표 &6" + (1 << goalExp)));
        set(7, Icons.of(Icons.TILE2048 + Math.min(11, Math.max(0, max() - 1)), "&f최고 타일 &e" + (1 << Math.max(1, max())), "&7목표 &6" + (1 << goalExp)));
        set(43, Icons.of(Icons.EASY + diff.ordinal(), "&f점수 &e" + score, "&7움직인 횟수 " + moves + (limit > 0 ? " · 남은 시간 " + timeLeft(limit) : "")));
        Text.actionBar(p, "&e2048 &7· &f점수 " + score + " &7· 최고 &e" + (1 << Math.max(1, max())) + " &7/ 목표 &6" + (1 << goalExp)
                + (limit > 0 ? " &7· " + timeLeft(limit) : ""));
    }

    @Override
    protected void tick() {
        if (limit > 0 && ticks >= limit) { finish(false, "시간이 끝났습니다 (최고 " + (1 << max()) + ")"); return; }
        if (limit > 0 && ticks % 20 == 0) render();
    }
}
