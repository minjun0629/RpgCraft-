package kr.rpgcraft.minigame;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.concurrent.ThreadLocalRandom;

/** 지뢰찾기: 9x5 판. 좌클릭 열기 · 우클릭 깃발. 첫 칸은 항상 안전. 지뢰가 아닌 칸을 모두 열면 성공 */
public class MinesGame extends MiniGame {
    private static final int W = 9, H = 5;
    private final boolean[] mine = new boolean[W * H], open = new boolean[W * H], flag = new boolean[W * H];
    private final int mines;
    private boolean placed;
    private int opened, boomAt = -1;

    public MinesGame(MiniGameManager mgr, Player p, Diff d) {
        super(mgr, p, Game.MINES, d, "&8지뢰찾기 &7- " + d.label, "mines");
        mines = d.pick(5, 8, 11);
    }

    @Override
    protected int period() {
        return 20;
    }

    @Override
    protected void setup() {
        render();
        fill(0, 53);
    }

    private int around(int i) {
        int n = 0, x = i % W, y = i / W;
        for (int dy = -1; dy <= 1; dy++)
            for (int dx = -1; dx <= 1; dx++) {
                int xx = x + dx, yy = y + dy;
                if ((dx != 0 || dy != 0) && xx >= 0 && xx < W && yy >= 0 && yy < H && mine[yy * W + xx]) n++;
            }
        return n;
    }

    private void place(int safe) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int sx = safe % W, sy = safe / W, put = 0;
        while (put < mines) {
            int i = r.nextInt(W * H), x = i % W, y = i / W;
            if (mine[i] || Math.abs(x - sx) <= 1 && Math.abs(y - sy) <= 1) continue;   // 첫 칸과 그 주변은 안전
            mine[i] = true;
            put++;
        }
        placed = true;
    }

    private void click(int i, InventoryClickEvent e) {
        if (over) return;
        if (e.isRightClick()) {
            if (!open[i]) { flag[i] = !flag[i]; p.playSound(p.getLocation(), Sound.BLOCK_WOOL_PLACE, 0.8f, 1.4f); }
            render();
            return;
        }
        if (flag[i] || open[i]) return;
        if (!placed) place(i);
        if (mine[i]) {
            boomAt = i;
            for (int k = 0; k < W * H; k++) if (mine[k]) open[k] = true;
            p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1f);
            render();
            finish(false, "지뢰를 밟았습니다!");
            return;
        }
        reveal(i);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 1.2f);
        if (opened >= W * H - mines) { render(); finish(true, ""); return; }
        render();
    }

    private void reveal(int i) {
        if (open[i] || flag[i] || mine[i]) return;
        open[i] = true;
        opened++;
        if (around(i) != 0) return;
        int x = i % W, y = i / W;   // 0 이면 주변도 모두 열기
        for (int dy = -1; dy <= 1; dy++)
            for (int dx = -1; dx <= 1; dx++) {
                int xx = x + dx, yy = y + dy;
                if (xx >= 0 && xx < W && yy >= 0 && yy < H) reveal(yy * W + xx);
            }
    }

    private void render() {
        int flags = 0;
        for (int i = 0; i < W * H; i++) {
            int idx = i;
            if (flag[i]) flags++;
            org.bukkit.inventory.ItemStack it;
            if (open[i] && mine[i]) it = Icons.of(i == boomAt ? Icons.MINE_BOOM : Icons.MINE, "&c지뢰");
            else if (open[i]) {
                int n = around(i);
                it = n == 0 ? Icons.of(Icons.OPEN, "&7빈 칸") : Icons.of(Icons.num(n), plugin.pack() != null && plugin.pack().hasPack(p) ? 1 : n, "&f주변 지뢰 " + n + "개");
            } else if (flag[i]) it = Icons.of(Icons.FLAG, "&c깃발", "&7우클릭: 깃발 떼기");
            else it = Icons.of(Icons.TILE, "&f?", "&7좌클릭: 열기", "&7우클릭: 깃발");
            set(i, it, e -> click(idx, e));
        }
        set(47, Icons.of(Icons.FLAG, Math.max(1, mines - flags), "&f남은 지뢰 &c" + (mines - flags)));
        set(49, Icons.of(Icons.EASY + diff.ordinal(), Math.max(1, Math.min(64, ticks / 20)), "&f걸린 시간 &e" + ticks / 20 + "초"));
        set(51, Icons.of(Icons.MINE, mines, "&f" + diff.color + diff.label + " &7- 지뢰 " + mines + "개"));
    }

    @Override
    protected void tick() {
        if (placed) render();
    }
}
