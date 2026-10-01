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
 * v5.10.31 날아라 새: 아무 칸이나 클릭하면 새가 한 칸 날아오르고, 가만히 있으면 떨어짐.
 * 오른쪽에서 다가오는 기둥 사이 틈으로 빠져나가기. 목표 개수만큼 통과하면 성공, 기둥이나 땅에 부딪히면 실패.
 */
public class FlappyGame extends MiniGame {
    private static final int H = 5, BIRD_X = 2;

    private static final class Pipe {
        int x;
        final int gapTop, gap;
        boolean passed;

        Pipe(int x, int gapTop, int gap) {
            this.x = x;
            this.gapTop = gapTop;
            this.gap = gap;
        }

        boolean solid(int y) {
            return y < gapTop || y >= gapTop + gap;
        }
    }

    private final List<Pipe> pipes = new ArrayList<>();
    private int y = 2, fallAt, scrollAt, passed, flapUntil, wait = 30, sinceSpawn, lastGap = 1;
    private final int target, fall, scroll, gap, spacing;
    private boolean dead;

    public FlappyGame(MiniGameManager mgr, Player p, Diff d) {
        super(mgr, p, Game.FLAPPY, d, "&8날아라 새 &7- " + d.label, "flappy");
        target = d.pick(8, 12, 16);
        fall = d.pick(9, 8, 7);
        scroll = d.pick(8, 7, 5);
        gap = d.pick(3, 2, 2);
        spacing = d.pick(4, 4, 3);
    }

    @Override
    protected int period() {
        return 1;
    }

    @Override
    protected void setup() {
        render();
    }

    private void flap() {
        if (over || dead) return;
        if (wait > 0) wait = 0;
        y = Math.max(0, y - 1);
        fallAt = ticks + fall + 2;
        flapUntil = ticks + 4;
        p.playSound(p.getLocation(), Sound.ENTITY_PARROT_FLY, 0.7f, 1.4f);
        if (hit()) { crash("기둥에 부딪혔습니다"); return; }
        render();
    }

    private boolean hit() {
        for (Pipe pp : pipes) if (pp.x == BIRD_X && pp.solid(y)) return true;
        return false;
    }

    private void render() {
        for (int r = 0; r < H; r++) for (int c = 0; c < 9; c++) {
            org.bukkit.inventory.ItemStack it = null;
            for (Pipe pp : pipes) {
                if (pp.x != c || !pp.solid(r)) continue;
                boolean capTop = r == pp.gapTop - 1, capBottom = r == pp.gapTop + pp.gap;
                it = Icons.of(capTop ? Icons.PIPE_TOP : capBottom ? Icons.PIPE_BOTTOM : Icons.PIPE, "&2기둥");
            }
            if (c == BIRD_X && r == y) it = Icons.of(dead ? Icons.BIRD_DEAD : flapUntil > ticks ? Icons.BIRD_FLAP : Icons.BIRD, "&e&l새 &7(클릭: 날기)");
            set(r * 9 + c, it != null ? it : PackManager.filler(Material.LIGHT_BLUE_STAINED_GLASS_PANE), e -> flap());
        }
        for (int c = 0; c < 9; c++) set(45 + c, Icons.of(Icons.GROUND, "&a땅 &7(클릭: 날기)"), e -> flap());
        set(45, Icons.of(Icons.PIPE_BOTTOM, Math.max(1, passed), "&f통과 &e" + passed + " &7/ " + target), e -> flap());
        set(53, Icons.of(Icons.EASY + diff.ordinal(), "&f" + diff.color + diff.label, "&7틈 " + gap + "칸"), e -> flap());
        Text.actionBar(p, "&e🐤 &f통과 &e" + passed + "&7/" + target + (wait > 0 ? " &e· 아무 칸이나 클릭하면 날아요!" : ""));
    }

    @Override
    protected void tick() {
        if (wait > 0) {
            wait--;
            if (ticks % 6 == 0) { y = y == 2 ? 1 : 2; render(); }   // 시작 전 둥실둥실
            if (wait == 0) { fallAt = ticks + fall; scrollAt = ticks + scroll; }
            return;
        }
        boolean changed = false;
        if (ticks >= fallAt) {
            fallAt = ticks + fall;
            y++;
            changed = true;
            if (y >= H) { y = H - 1; crash("땅에 떨어졌습니다"); return; }
            if (hit()) { crash("기둥에 부딪혔습니다"); return; }
        }
        if (ticks >= scrollAt) {
            scrollAt = ticks + scroll;
            changed = true;
            for (Pipe pp : pipes) pp.x--;
            pipes.removeIf(pp -> pp.x < 0);
            if (++sinceSpawn >= spacing || pipes.isEmpty()) {
                sinceSpawn = 0;
                int maxTop = H - gap;   // 틈이 너무 크게 뛰지 않게 (최대 2칸)
                int top = Math.max(0, Math.min(maxTop, lastGap + ThreadLocalRandom.current().nextInt(-2, 3)));
                lastGap = top;
                pipes.add(new Pipe(8, top, gap));
            }
            if (hit()) { crash("기둥에 부딪혔습니다"); return; }
            for (Pipe pp : pipes) if (!pp.passed && pp.x < BIRD_X) {
                pp.passed = true;
                passed++;
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f + Math.min(0.8f, passed * 0.05f));
                if (passed >= target) { render(); finish(true, ""); return; }
            }
        }
        if (changed || flapUntil == ticks) render();
    }

    private void crash(String why) {
        dead = true;
        p.playSound(p.getLocation(), Sound.ENTITY_CHICKEN_HURT, 1f, 1.2f);
        p.playSound(p.getLocation(), Sound.BLOCK_WOOD_BREAK, 0.8f, 0.8f);
        render();
        finish(false, why + " (통과 " + passed + " / " + target + ")");
    }
}
