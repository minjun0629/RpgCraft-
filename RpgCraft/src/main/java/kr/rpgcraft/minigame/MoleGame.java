package kr.rpgcraft.minigame;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 두더지 잡기: 구멍 9개에서 두더지가 잠깐 튀어나옴 → 클릭해서 잡기.
 * 황금 두더지 +3, 폭탄(보통 · 어려움)을 누르면 -3, 어려움에서는 빈 구멍을 눌러도 -1. 30초 뒤 목표 점수 이상이면 성공.
 */
public class MoleGame extends MiniGame {
    static final int[] HOLES = {11, 13, 15, 20, 22, 24, 29, 31, 33};
    private static final int LIMIT = 600;   // 30초

    private final int[] kind = new int[9];      // 0 빈 · 1 두더지 · 2 황금 · 3 폭탄 · 4 맞음 · 5 펑
    private final int[] until = new int[9];
    private int score, nextSpawn;
    private final int target, spawnEvery, stay;
    private final double gold, bombs;

    public MoleGame(MiniGameManager mgr, Player p, Diff d) {
        super(mgr, p, Game.MOLE, d, "&8두더지 잡기 &7- " + d.label, "mole");
        target = d.pick(12, 20, 28);
        spawnEvery = d.pick(14, 10, 7);
        stay = d.pick(26, 17, 11);
        gold = 0.1;
        bombs = d.pick(0.0, 0.12, 0.2);
    }

    @Override
    protected void setup() {
        render();
        fill(0, 53);
    }

    private void render() {
        for (int i = 0; i < 9; i++) {
            int idx = i;
            int icon = switch (kind[i]) { case 1 -> Icons.MOLE; case 2 -> Icons.MOLE_GOLD; case 3 -> Icons.BOMB; case 4 -> Icons.MOLE_HIT; case 5 -> Icons.BOOM; default -> Icons.HOLE; };
            String name = switch (kind[i]) { case 1 -> "&6두더지!"; case 2 -> "&e&l황금 두더지! +3"; case 3 -> "&c폭탄 — 누르지 마세요"; default -> "&8구멍"; };
            set(HOLES[i], Icons.of(icon, name), e -> whack(idx));
        }
        set(47, Icons.of(Icons.MOLE, Math.max(1, score), "&f점수 &e" + score, "&7목표 " + target));
        set(49, Icons.of(Icons.EASY + diff.ordinal(), Math.max(1, (LIMIT - ticks) / 20), "&f남은 시간 &e" + timeLeft(LIMIT)));
        set(51, Icons.of(Icons.MOLE_GOLD, target, "&f목표 &a" + target + "점", "&7" + diff.color + diff.label));
    }

    private void whack(int i) {
        if (over) return;
        switch (kind[i]) {
            case 1, 2 -> {
                boolean g = kind[i] == 2;
                int add = g ? 3 : 1;
                score += add;
                kind[i] = 4;
                until[i] = ticks + 6;
                p.playSound(p.getLocation(), g ? Sound.ENTITY_EXPERIENCE_ORB_PICKUP : Sound.BLOCK_WOOD_HIT, 1f, g ? 1.6f : 1.2f);
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.6f, 1.4f);
            }
            case 3 -> {
                score = Math.max(0, score - 3);
                kind[i] = 5;
                until[i] = ticks + 8;
                p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.4f);
            }
            case 0 -> {
                if (diff == Diff.HARD) { score = Math.max(0, score - 1); p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f); }
            }
            default -> { }
        }
        render();
    }

    @Override
    protected void tick() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < 9; i++) if (kind[i] != 0 && ticks >= until[i]) kind[i] = 0;   // 숨음
        if (ticks >= nextSpawn) {
            nextSpawn = ticks + spawnEvery + r.nextInt(-2, 3);
            int n = diff == Diff.HARD && r.nextInt(3) == 0 ? 2 : 1;
            for (int k = 0; k < n; k++) {
                int i = r.nextInt(9);
                if (kind[i] != 0) continue;
                double roll = r.nextDouble();
                kind[i] = roll < bombs ? 3 : roll < bombs + gold ? 2 : 1;
                until[i] = ticks + stay + (kind[i] == 2 ? -4 : 0);
                p.playSound(p.getLocation(), Sound.BLOCK_GRAVEL_BREAK, 0.5f, 1.3f);
            }
        }
        if (ticks >= LIMIT) {
            render();
            finish(score >= target, score + "점 / 목표 " + target + "점");
            return;
        }
        render();
    }
}
