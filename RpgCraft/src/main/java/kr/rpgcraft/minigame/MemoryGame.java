package kr.rpgcraft.minigame;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.*;

/** 같은 그림 찾기: 카드 두 장을 뒤집어 같은 그림이면 맞춤. 제한 시간 안에 모두 맞추면 성공 */
public class MemoryGame extends MiniGame {
    private final int[] slots;
    private final int[] face;
    private final boolean[] done, up;
    private int first = -1, second = -1, flipBack, pairs, found;
    private final int limit;

    public MemoryGame(MiniGameManager mgr, Player p, Diff d) {
        super(mgr, p, Game.MEMORY, d, "&8같은 그림 찾기 &7- " + d.label, "memory");
        slots = switch (d) {   // 가운데 줄을 비운 좌우 대칭 배치
            case EASY -> new int[]{11, 12, 14, 15, 20, 21, 23, 24, 29, 30, 32, 33};
            case NORMAL -> new int[]{2, 3, 5, 6, 11, 12, 14, 15, 20, 21, 23, 24, 29, 30, 32, 33, 38, 39, 41, 42};
            default -> new int[]{1, 2, 3, 5, 6, 7, 10, 11, 12, 14, 15, 16, 19, 20, 21, 23, 24, 25, 28, 29, 30, 32, 33, 34, 37, 38, 42, 43};
        };
        pairs = slots.length / 2;
        limit = d.pick(90, 120, 100) * 20;
        List<Integer> deck = new ArrayList<>();
        List<Integer> kinds = new ArrayList<>();
        for (int k = 0; k < Icons.FACES; k++) kinds.add(k);
        Collections.shuffle(kinds);
        for (int k = 0; k < pairs; k++) { deck.add(kinds.get(k)); deck.add(kinds.get(k)); }
        Collections.shuffle(deck);
        face = deck.stream().mapToInt(Integer::intValue).toArray();
        done = new boolean[slots.length];
        up = new boolean[slots.length];
    }

    @Override
    protected void setup() {
        render();
        fill(0, 53);
    }

    private static final String[] NAMES = {"하트", "물방울", "나뭇잎", "별", "보석", "불꽃", "달", "꽃", "버섯", "해골", "검", "방패", "물고기", "열쇠"};

    private void render() {
        for (int i = 0; i < slots.length; i++) {
            int idx = i;
            boolean show = done[i] || up[i];
            set(slots[i], show ? Icons.of(Icons.FACE + face[i], (done[i] ? "&a✔ " : "&f") + NAMES[face[i]]) : Icons.of(Icons.CARD, "&6카드", "&7클릭해서 뒤집기"), e -> flip(idx));
        }
        set(47, Icons.of(Icons.CARD, Math.max(1, found), "&f맞춘 짝 &a" + found + " &7/ " + pairs));
        set(49, Icons.of(Icons.EASY + diff.ordinal(), Math.max(1, Math.min(64, (limit - ticks) / 20)), "&f남은 시간 &e" + timeLeft(limit)));
        set(51, Icons.of(Icons.FACE + 3, pairs, "&f" + diff.color + diff.label + " &7- " + pairs + "쌍"));
    }

    private void flip(int i) {
        if (over || done[i] || up[i] || second >= 0) return;
        up[i] = true;
        p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1.3f);
        if (first < 0) first = i;
        else {
            second = i;
            if (face[first] == face[second]) {
                done[first] = done[second] = true;
                up[first] = up[second] = false;
                first = second = -1;
                found++;
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f);
                if (found >= pairs) { render(); finish(true, ""); return; }
            } else flipBack = ticks + 16;
        }
        render();
    }

    @Override
    protected void tick() {
        if (second >= 0 && ticks >= flipBack) {
            up[first] = up[second] = false;
            first = second = -1;
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);
        }
        if (ticks >= limit) { render(); finish(false, "시간 초과 (" + found + "/" + pairs + "쌍)"); return; }
        if (ticks % 20 == 0 || second < 0) render();
    }
}
