package kr.rpgcraft.minigame;

import kr.rpgcraft.pack.PackManager;
import kr.rpgcraft.util.Text;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * v5.10.31 리듬 게임: 네 줄에서 음표가 내려오면, 판정선(5번째 줄)에 닿는 순간 그 줄을 클릭.
 * - 맞추면 그 음이 소리 나서 노래가 연주됨 (쉬움 「반짝반짝 작은 별」 · 보통 「환희의 송가」 · 어려움 「학교 종」 + 「작은 별」 빠르게).
 * - 퍼펙트 100% · 굿 60% · 미스 0%. 정확도가 목표 이상이면 성공. 콤보가 이어질수록 소리가 화려해짐.
 */
public class RhythmGame extends MiniGame {
    private static final int[] LANE_COL = {1, 3, 5, 7};
    private static final String[] LANE_NAME = {"&c", "&e", "&a", "&b"};

    /** 음 높이(노트 블록 0~24: 0 = F#3, 6 = C4, 18 = C5) */
    private static final int C = 6, D = 8, E = 10, F = 11, G = 13, A = 15, B = 17, C2 = 18;
    private static final int[] TWINKLE = {C, C, G, G, A, A, G, -1, F, F, E, E, D, D, C, -1, G, G, F, F, E, E, D, -1, G, G, F, F, E, E, D, -1,
            C, C, G, G, A, A, G, -1, F, F, E, E, D, D, C};
    private static final int[] ODE = {E, E, F, G, G, F, E, D, C, C, D, E, E, -1, D, D, -1, E, E, F, G, G, F, E, D, C, C, D, E, D, -1, C, C, -1,
            D, D, E, C, D, E, F, E, C, D, E, F, E, D, C, D, G, -1, E, E, F, G, G, F, E, D, C, C, D, E, D, C, C};
    private static final int[] SCHOOL = {G, G, A, A, G, G, E, -1, G, G, E, E, D, -1, -1, -1, G, G, A, A, G, G, E, -1, G, E, D, E, C, -1, -1, -1,
            C2, B, A, G, A, B, C2, -1, G, G, E, E, D, -1, G, A, G, E, D, E, C};

    private record Note(int time, int lane, int pitch) {}

    private final List<Note> notes = new ArrayList<>();
    private final boolean[] done;
    private final int step, lead;
    private int perfect, good, miss, combo, best, end;
    private final int[] flash = new int[4], flashKind = new int[4];
    private final double goal;

    public RhythmGame(MiniGameManager mgr, Player p, Diff d) {
        super(mgr, p, Game.RHYTHM, d, "&8리듬 게임 &7- " + d.label, "rhythm");
        step = d.pick(4, 3, 3);             // 한 칸 내려오는 데 걸리는 틱
        int gap = d.pick(9, 7, 5);           // 음 사이 간격 (틱)
        goal = d.pick(0.65, 0.75, 0.8);
        lead = step * 4;                     // 맨 위에서 판정선까지
        int[] song = d == Diff.EASY ? TWINKLE : d == Diff.NORMAL ? ODE : concat(SCHOOL, TWINKLE);
        int lo = 99, hi = -1;
        for (int n : song) if (n >= 0) { lo = Math.min(lo, n); hi = Math.max(hi, n); }
        int t = 30 + lead;
        int prevLane = -1;
        for (int n : song) {
            if (n >= 0) {
                int lane = Math.min(3, (n - lo) * 4 / Math.max(1, hi - lo + 1));
                if (lane == prevLane && d != Diff.EASY && notes.size() % 5 == 4) lane = (lane + 2) % 4;   // 같은 줄만 계속 나오지 않게 가끔 섞기
                notes.add(new Note(t, lane, n));
                prevLane = lane;
            }
            t += gap;
        }
        done = new boolean[notes.size()];
        end = t + step * 3;
    }

    private static int[] concat(int[] a, int[] b) {
        int[] o = new int[a.length + 1 + b.length];
        System.arraycopy(a, 0, o, 0, a.length);
        o[a.length] = -1;
        System.arraycopy(b, 0, o, a.length + 1, b.length);
        return o;
    }

    @Override
    protected int period() {
        return 1;
    }

    @Override
    protected void setup() {
        render();
    }

    private static float pitch(int n) {
        return (float) Math.pow(2, (n - 12) / 12.0);
    }

    private void press(int lane) {
        if (over) return;
        int bestI = -1, bestD = Integer.MAX_VALUE;
        for (int i = 0; i < notes.size(); i++) {
            Note n = notes.get(i);
            if (done[i] || n.lane != lane) continue;
            int dd = Math.abs(ticks - n.time);
            if (dd < bestD) { bestD = dd; bestI = i; }
            if (n.time > ticks + lead) break;
        }
        if (bestI < 0 || bestD > step * 1.5) {   // 헛누름: 콤보만 끊김
            combo = 0;
            flash[lane] = ticks + 3;
            flashKind[lane] = 0;
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 0.5f, 0.7f);
            render();
            return;
        }
        Note n = notes.get(bestI);
        done[bestI] = true;
        boolean perf = bestD <= step * 0.6;
        if (perf) perfect++;
        else good++;
        combo++;
        best = Math.max(best, combo);
        flash[lane] = ticks + 4;
        flashKind[lane] = perf ? 2 : 1;
        p.playSound(p.getLocation(), combo >= 20 ? Sound.BLOCK_NOTE_BLOCK_BELL : Sound.BLOCK_NOTE_BLOCK_HARP, 1f, pitch(n.pitch));
        if (combo >= 10) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.35f, pitch(n.pitch));
        if (combo > 0 && combo % 10 == 0) p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.6f);
        Text.actionBar(p, (perf ? "&b&lPERFECT" : "&a&lGOOD") + (combo > 1 ? " &e" + combo + " 콤보" : "") + " &7· 정확도 " + accuracyText());
        render();
    }

    private double accuracy() {
        int judged = perfect + good + miss;
        return judged == 0 ? 1 : (perfect + good * 0.6) / judged;
    }

    private double finalAccuracy() {
        return notes.isEmpty() ? 1 : (perfect + good * 0.6) / notes.size();
    }

    private String accuracyText() {
        return String.format("%.0f%%", accuracy() * 100);
    }

    private void render() {
        for (int r = 0; r < 6; r++) for (int c = 0; c < 9; c++) {
            boolean laneCol = c % 2 == 1 && c <= 7;
            set(r * 9 + c, PackManager.filler(laneCol ? Material.BLACK_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE));
        }
        for (int l = 0; l < 4; l++) {   // 판정선 + 버튼
            int lane = l;
            int col = LANE_COL[l];
            boolean f = flash[l] > ticks;
            set(36 + col, Icons.of(f && flashKind[l] == 2 ? Icons.PAD_PERFECT : f && flashKind[l] == 1 ? Icons.PAD_HIT : Icons.LANE, LANE_NAME[l] + "판정선",
                    "&7음표가 여기 닿을 때 클릭!"), e -> press(lane));
            set(45 + col, Icons.of(Icons.PAD + l, LANE_NAME[l] + "&l" + (l + 1) + "번 줄", "&7클릭: 이 줄 연주"), e -> press(lane));
        }
        for (int i = 0; i < notes.size(); i++) {
            Note n = notes.get(i);
            if (done[i]) continue;
            int dt = n.time - ticks;
            if (dt > lead + step - 1) break;
            int row = dt < 0 ? 4 : 4 - (int) Math.floor(dt / (double) step);
            if (row < 0 || row > 4) continue;
            int lane = n.lane;
            set(row * 9 + LANE_COL[n.lane], Icons.of(Icons.NOTE + n.lane, LANE_NAME[n.lane] + "♪"), e -> press(lane));
        }
        set(0, Icons.of(Icons.NOTE_ICON, Math.max(1, Math.min(64, combo)), "&e" + combo + " 콤보", "&7최고 " + best));
        set(8, Icons.of(Icons.EASY + diff.ordinal(), "&f정확도 &e" + accuracyText(), "&7목표 " + Math.round(goal * 100) + "%",
                "&bPERFECT " + perfect + " &aGOOD " + good + " &cMISS " + miss));
    }

    @Override
    protected void tick() {
        for (int i = 0; i < notes.size(); i++) {   // 놓친 음표
            Note n = notes.get(i);
            if (done[i]) continue;
            if (n.time > ticks) break;
            if (ticks - n.time > step * 1.5) {
                done[i] = true;
                miss++;
                combo = 0;
                flash[n.lane] = ticks + 3;
                flashKind[n.lane] = 0;
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.5f);
                Text.actionBar(p, "&c&lMISS &7· 정확도 " + accuracyText());
            }
        }
        if (ticks == 10) p.sendTitle("", Text.c("&e♪ 음표가 판정선에 닿으면 클릭!"), 0, 25, 5);
        if (ticks >= end) {
            render();
            double acc = finalAccuracy();
            finish(acc >= goal, String.format("정확도 %.0f%% / 목표 %.0f%% (최고 %d 콤보)", acc * 100, goal * 100, best));
            return;
        }
        render();
    }
}
