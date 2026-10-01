package kr.rpgcraft.world;

import java.util.*;

/**
 * v5.10.56 스폰 왕국 설계도 (1000 x 1000) — 블록 배치를 순수 계산으로 만든다 (Bukkit 없이 돌아가므로 미리보기 · 비동기 계산 가능).
 * 좌표: 스폰 블록 = (0, 0), x 동쪽 · z 남쪽, y=0 은 걸어 다니는 높이 (땅 블록은 y=-1).
 * 블록은 청크마다 int 하나로 묶어 저장: 청크 안 x(4) · z(4) · y+64 (9) · 블록 번호(15). 나중에 넣은 블록이 먼저 넣은 것을 덮는다.
 *
 * 배치 (가운데에서 바깥으로)
 *  - 중앙 광장 (스폰): 3단 분수 · 무늬 포석 · 오벨리스크 · 화단 · 벤치 · 가로등
 *  - 북쪽 왕성: 해자 · 성벽(원형 탑 8) · 남쪽 성문루 · 정원 · 본궁(대전 · 모서리 탑 4 · 가운데 대탑 · 첨탑) · 옥좌의 방
 *  - 동쪽 시장 거리: 큰길 양옆 줄무늬 천막 노점 (상점 NPC)
 *  - 서쪽 모험가 광장: 모험가 길드 회관 · 의뢰 게시판 (의뢰 NPC)
 *  - 남동 대성당 · 남서 원형 투기장 · 북서 호수 공원(섬 정자 · 다리) · 북동 대장간 거리 · 바깥 둘레 농장 · 풍차
 *  - 나머지는 골목길 격자를 따라 늘어선 반목조 집 수백 채 (층수 · 크기 · 지붕 · 벽 색이 저마다 다름)
 *  - 바깥 성벽 (두께 6 · 높이 18 · 톱니 난간 · 돌출 장식), 70칸마다 원형 탑, 네 방향 성문루 · 해자와 다리
 */
public final class KingdomPlan {
    public static final int HALF = 500;          // 부지 반너비
    public static final int WALL = 470;          // 바깥 성벽 안쪽 면
    public static final int CASTLE_Z = -215;     // 왕성 가운데 z
    public static final int CASTLE_HALF = 105;   // 왕성 성벽 반너비

    /** NPC 자리 (kind: shop · quest, id: 상점 ID 또는 의뢰 유형) */
    public record Npc(int x, int y, int z, float yaw, String kind, String id) {}

    public final List<String> palette = new ArrayList<>();
    private final Map<String, Integer> pidx = new HashMap<>();
    public final Map<Long, int[]> chunks = new HashMap<>();
    private final Map<Long, Integer> sizes = new HashMap<>();
    public final List<Npc> npcs = new ArrayList<>();
    public long ops;
    private final Random rnd;
    private final List<String> shops;
    private final List<String> quests;

    public KingdomPlan(long seed, List<String> shopIds, List<String> questTypes) {
        this.rnd = new Random(seed);
        this.shops = shopIds;
        this.quests = questTypes;
        state("minecraft:air");
    }

    public static long key(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xffffffffL);
    }

    public int state(String s) {
        if (!s.startsWith("minecraft:")) s = "minecraft:" + s;
        Integer i = pidx.get(s);
        if (i != null) return i;
        palette.add(s);
        pidx.put(s, palette.size() - 1);
        return palette.size() - 1;
    }

    public int size(long k) {
        return sizes.getOrDefault(k, 0);
    }

    // ================================================================== 기본 도구
    private void put(int x, int y, int z, int st) {
        if (Math.abs(x) > HALF || Math.abs(z) > HALF || y < -60 || y > 440) return;
        long k = key(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
        int[] a = chunks.get(k);
        int n = sizes.getOrDefault(k, 0);
        if (a == null) {
            a = new int[256];
            chunks.put(k, a);
        } else if (n == a.length) {
            a = Arrays.copyOf(a, n * 2);
            chunks.put(k, a);
        }
        a[n] = (x & 15) | (z & 15) << 4 | (y + 64) << 8 | st << 17;
        sizes.put(k, n + 1);
        ops++;
    }

    void set(int x, int y, int z, String s) {
        put(x, y, z, state(s));
    }

    void box(int x1, int y1, int z1, int x2, int y2, int z2, String s) {
        int st = state(s);
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
                for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) put(x, y, z, st);
    }

    interface Tex {
        String at(int x, int y, int z);
    }

    void boxT(int x1, int y1, int z1, int x2, int y2, int z2, Tex t) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
                for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) set(x, y, z, t.at(x, y, z));
    }

    static double h(int x, int y, int z) {
        long v = x * 73856093L ^ y * 19349663L ^ z * 83492791L;
        v ^= v >>> 13;
        v *= 0x5bd1e995L;
        v ^= v >>> 15;
        return (v & 0xffffff) / (double) 0x1000000;
    }

    /** 성벽 돌 (돌벽돌 + 이끼 · 금 간 돌벽돌 · 안산암 섞기) */
    static String stone(int x, int y, int z) {
        double r = h(x, y, z);
        return r < 0.70 ? "stone_bricks" : r < 0.82 ? "mossy_stone_bricks" : r < 0.93 ? "cracked_stone_bricks" : "andesite";
    }

    static String light(int x, int y, int z) {
        double r = h(x, y, z);
        return r < 0.75 ? "polished_diorite" : r < 0.9 ? "calcite" : "smooth_quartz";
    }

    void cyl(int cx, int cz, double r, int y1, int y2, Tex t, boolean hollow) {
        int R = (int) Math.ceil(r);
        for (int dx = -R; dx <= R; dx++)
            for (int dz = -R; dz <= R; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > r + 0.3) continue;
                if (hollow && d < r - 0.75) continue;
                for (int y = y1; y <= y2; y++) set(cx + dx, y, cz + dz, t.at(cx + dx, y, cz + dz));
            }
    }

    void disc(int cx, int y, int cz, double r, String s) {
        cyl(cx, cz, r, y, y, (a, b, c) -> s, false);
    }

    /** 원뿔 첨탑 (반지름 r → 0, 높이 hgt) + 금 장식 */
    void spire(int cx, int cz, double r, int y0, int hgt, String mat, String tip) {
        for (int k = 0; k < hgt; k++) {
            double rr = r * (1 - (double) k / hgt);
            if (rr < 0.4) rr = 0.4;
            cyl(cx, cz, rr, y0 + k, y0 + k, (a, b, c) -> mat, true);
        }
        set(cx, y0 + hgt, cz, tip);
        set(cx, y0 + hgt + 1, cz, "lightning_rod");
    }

    void tree(int x, int z, int kind) {
        int hgt = 5 + (int) (h(x, 7, z) * 3);
        String log = kind == 1 ? "birch_log" : kind == 2 ? "spruce_log" : kind == 3 ? "cherry_log" : "oak_log";
        String leaf = (kind == 1 ? "birch_leaves" : kind == 2 ? "spruce_leaves" : kind == 3 ? "cherry_leaves" : "oak_leaves") + "[persistent=true]";
        for (int y = 0; y < hgt; y++) set(x, y, z, log);
        if (kind == 2) {   // 원뿔 가문비
            for (int y = 2; y <= hgt + 1; y++) {
                double r = (hgt + 2 - y) * 0.55;
                for (int dx = -3; dx <= 3; dx++)
                    for (int dz = -3; dz <= 3; dz++)
                        if ((dx != 0 || dz != 0 || y >= hgt) && dx * dx + dz * dz <= r * r + 0.3) set(x + dx, y, z + dz, leaf);
            }
            return;
        }
        int cy = hgt - 1;
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -3; dz <= 3; dz++)
                for (int dy = -2; dy <= 3; dy++) {
                    double d = dx * dx / 9.0 + dz * dz / 9.0 + dy * dy / 6.0;
                    if (d <= 1.0 && h(x + dx, cy + dy, z + dz) > 0.08 && !(dx == 0 && dz == 0 && dy < 1)) set(x + dx, cy + dy, z + dz, leaf);
                }
    }

    /** 가로등: 기둥 · 팔 · 매단 등불 */
    void lamp(int x, int z, String dir) {
        set(x, 0, z, "polished_andesite");
        for (int y = 1; y <= 4; y++) set(x, y, z, "dark_oak_fence");
        set(x, 5, z, "dark_oak_planks");
        int dx = dir.equals("east") ? 1 : dir.equals("west") ? -1 : 0, dz = dir.equals("south") ? 1 : dir.equals("north") ? -1 : 0;
        set(x + dx, 5, z + dz, "dark_oak_fence");
        set(x + dx, 4, z + dz, "lantern[hanging=true]");
        set(x, 6, z, "dark_oak_slab");
    }

    void bench(int x, int z, String facing, int len, boolean alongX) {
        for (int i = 0; i < len; i++) set(alongX ? x + i : x, 0, alongX ? z : z + i, "spruce_stairs[facing=" + facing + "]");
    }

    void planter(int x1, int z1, int x2, int z2) {
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean edge = x == x1 || x == x2 || z == z1 || z == z2;
                set(x, 0, z, edge ? "stone_brick_slab" : "grass_block");
                if (!edge) {
                    double r = h(x, 1, z);
                    set(x, 1, z, r < 0.3 ? "red_tulip" : r < 0.5 ? "oxeye_daisy" : r < 0.65 ? "cornflower" : r < 0.8 ? "allium" : "poppy");
                }
            }
    }

    // ================================================================== 전체
    public KingdomPlan build() {
        ground();
        roads();
        plaza();
        royalCastle();
        market();
        guildSquare();
        cathedral(150, 200);
        arena(-190, 210);
        lakePark(-300, -260);
        forgeRow(180, -150);
        farms();
        districts();
        outerWall();
        return this;
    }

    // ------------------------------------------------------------------ 땅 · 길
    private boolean inCastle(int x, int z, int m) {
        return Math.abs(x) <= CASTLE_HALF + m && Math.abs(z - CASTLE_Z) <= CASTLE_HALF + m;
    }

    private void ground() {
        // 성벽 바깥: 해자 (482~489) · 둘레길
        for (int x = -HALF; x <= HALF; x++)
            for (int z = -HALF; z <= HALF; z++) {
                int m = Math.max(Math.abs(x), Math.abs(z));
                if (m >= 482 && m <= 489) {
                    boolean edge = m == 482 || m == 489;
                    set(x, -1, z, edge ? "stone_bricks" : "water");
                    if (!edge) {
                        set(x, -2, z, "water");
                        set(x, -3, z, "water");
                        set(x, -4, z, "gravel");
                    } else set(x, 0, z, "stone_brick_slab");
                } else if (m >= 492 && m <= 494) set(x, -1, z, "dirt_path");
            }
    }

    private String pave(int x, int z, boolean center) {
        double r = h(x, -1, z);
        if (center) return r < 0.8 ? "polished_andesite" : "andesite";
        return r < 0.55 ? "stone_bricks" : r < 0.75 ? "cobblestone" : r < 0.9 ? "andesite" : "mossy_stone_bricks";
    }

    private void roads() {
        // 네 방향 큰길 (폭 13): 가운데 광택 안산암 · 양옆 돌벽돌 · 경계석
        for (int t = -6; t <= 6; t++)
            for (int s = -WALL; s <= WALL; s++) {
                boolean c = Math.abs(t) <= 1;
                set(t, -1, s, pave(t, s, c));
                set(s, -1, t, pave(s, t, c));
            }
        for (int s = -WALL; s <= WALL; s += 24) {   // 가로등 · 가로수
            if (Math.abs(s) < 40) continue;
            boolean castleSeg = s < 0 && s > CASTLE_Z - CASTLE_HALF - 10;
            if (!castleSeg) {
                lamp(-8, s, "east");
                lamp(8, s + 12, "west");
            }
            lamp(s, -8, "south");
            lamp(s + 12, 8, "north");
            if (!castleSeg && Math.abs(s) > 60) {
                tree(-10, s + 6, (int) (h(s, 1, 3) * 3) == 1 ? 1 : 0);
                tree(10, s - 6, 0);
            }
            if (Math.abs(s) > 60 && !(s > 50 && s < 210)) {
                tree(s + 6, -10, 0);
                tree(s - 6, 10, (int) (h(s, 2, 3) * 3) == 1 ? 1 : 0);
            }
        }
        // 둘레 순환로 (반너비 330, 폭 7) · 골목 격자 (64칸마다, 폭 5 · 흙길)
        for (int s = -WALL; s <= WALL; s++)
            for (int t = -3; t <= 3; t++)
                for (int R : new int[]{330}) {
                    if (inCastle(s, -R + t, 4) || inCastle(-R + t, s, 4)) continue;
                    set(s, -1, -R + t, pave(s, -R + t, false));
                    set(s, -1, R + t, pave(s, R + t, false));
                    set(-R + t, -1, s, pave(-R + t, s, false));
                    set(R + t, -1, s, pave(R + t, s, false));
                }
        for (int g = -448; g <= 448; g += 64) {
            if (Math.abs(g) < 20) continue;
            for (int s = -WALL; s <= WALL; s++)
                for (int t = -2; t <= 2; t++) {
                    String p = Math.abs(t) == 2 ? "coarse_dirt" : h(g + t, 0, s) < 0.85 ? "dirt_path" : "gravel";
                    if (!inCastle(g + t, s, 6) && !reserved(g + t, s)) set(g + t, -1, s, p);
                    if (!inCastle(s, g + t, 6) && !reserved(s, g + t)) set(s, -1, g + t, p);
                }
        }
    }

    /** 특수 구역 (집을 짓지 않음) */
    private boolean reserved(int x, int z) {
        if (Math.abs(x) <= 50 && Math.abs(z) <= 50) return true;                         // 광장
        if (x >= 56 && x <= 215 && Math.abs(z) <= 34) return true;                       // 시장
        if (x <= -56 && x >= -215 && Math.abs(z) <= 50) return true;                     // 모험가 광장
        if (Math.abs(x - 150) <= 52 && Math.abs(z - 200) <= 70) return true;              // 대성당
        if (Math.abs(x + 190) <= 58 && Math.abs(z - 210) <= 58) return true;              // 투기장
        if (Math.abs(x + 300) <= 80 && Math.abs(z + 260) <= 75) return true;              // 호수 공원
        if (Math.abs(x - 180) <= 52 && Math.abs(z + 150) <= 34) return true;              // 대장간 거리
        if (Math.max(Math.abs(x), Math.abs(z)) >= 400) return true;                       // 바깥 농장 띠
        return false;
    }

    // ------------------------------------------------------------------ 중앙 광장 (스폰)
    private void plaza() {
        for (int x = -50; x <= 50; x++)
            for (int z = -50; z <= 50; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > 44.5 && !(Math.abs(x) <= 6 || Math.abs(z) <= 6)) continue;
                int ring = (int) d;
                String s = d > 43.5 ? "stone_bricks"
                        : ring % 8 == 0 ? "polished_andesite"
                        : ring % 8 == 4 ? "chiseled_stone_bricks"
                        : ((int) (Math.atan2(z, x) / Math.PI * 16 + 16) % 2 == 0) ? "smooth_stone" : "polished_diorite";
                set(x, -1, z, s);
            }
        // 3단 분수
        cyl(0, 0, 9.5, 0, 0, (a, b, c) -> "stone_bricks", true);
        cyl(0, 0, 9.5, 1, 1, (a, b, c) -> "stone_brick_slab", true);
        disc(0, -1, 0, 8.6, "water");
        disc(0, -2, 0, 8.6, "stone_bricks");
        cyl(0, 0, 2.5, 0, 3, (a, b, c) -> "quartz_pillar", false);
        cyl(0, 0, 4.6, 4, 4, (a, b, c) -> "smooth_quartz", false);
        cyl(0, 0, 4.6, 5, 5, (a, b, c) -> "quartz_slab", true);
        disc(0, 5, 0, 3.6, "water");
        cyl(0, 0, 1.5, 5, 8, (a, b, c) -> "quartz_pillar", false);
        cyl(0, 0, 2.6, 9, 9, (a, b, c) -> "smooth_quartz", false);
        cyl(0, 0, 2.6, 10, 10, (a, b, c) -> "quartz_slab", true);
        disc(0, 10, 0, 1.6, "water");
        set(0, 10, 0, "sea_lantern");
        set(0, 11, 0, "gold_block");
        set(0, 12, 0, "end_rod");
        for (int k = 0; k < 8; k++) {   // 물 뿜는 돌 사자 머리 대신 장식 등
            double a = k * Math.PI / 4;
            int x = (int) Math.round(Math.cos(a) * 9.5), z = (int) Math.round(Math.sin(a) * 9.5);
            set(x, 2, z, "lantern");
        }
        // 오벨리스크 4 · 화단 · 벤치 · 가로등
        for (int[] q : new int[][]{{1, 1}, {1, -1}, {-1, 1}, {-1, -1}}) {
            int ox = q[0] * 26, oz = q[1] * 26;
            box(ox - 2, 0, oz - 2, ox + 2, 0, oz + 2, "polished_andesite");
            box(ox - 1, 1, oz - 1, ox + 1, 1, oz + 1, "chiseled_stone_bricks");
            for (int y = 2; y <= 11; y++) set(ox, y, oz, y % 4 == 0 ? "chiseled_quartz_block" : "quartz_pillar");
            set(ox, 12, oz, "gold_block");
            set(ox, 13, oz, "end_rod");
            for (String f : new String[]{"north", "south", "east", "west"}) {
                int dx = f.equals("east") ? 2 : f.equals("west") ? -2 : 0, dz = f.equals("south") ? 2 : f.equals("north") ? -2 : 0;
                set(ox + dx / 2, 6, oz + dz / 2, "blue_wall_banner[facing=" + f + "]");
            }
            planter(q[0] * 15 - 3, q[1] * 15 - 3, q[0] * 15 + 3, q[1] * 15 + 3);
            tree(q[0] * 15, q[1] * 15, 3);
            bench(q[0] * 36 - 2, q[1] * 14, q[1] > 0 ? "north" : "south", 5, true);
            bench(q[0] * 14, q[1] * 36 - 2, q[0] > 0 ? "west" : "east", 5, false);
            lamp(q[0] * 34, q[1] * 34, q[0] > 0 ? "west" : "east");
        }
        for (int[] q : new int[][]{{1, 1}, {1, -1}, {-1, 1}, {-1, -1}}) {   // 광장 모서리 숲
            tree(q[0] * 44, q[1] * 44, 3);
            tree(q[0] * 38, q[1] * 47, 0);
            tree(q[0] * 47, q[1] * 38, 1);
        }
        // 스폰 표지 (분수 남쪽): 금 테 원판
        cyl(0, 22, 2.5, -1, -1, (a, b, c) -> "gold_block", true);
        set(0, -1, 22, "chiseled_quartz_block");
    }

    // ------------------------------------------------------------------ 바깥 성벽 · 성문 · 탑
    private void outerWall() {
        int in = WALL, out = WALL + 5, top = 18;
        for (int s = -out; s <= out; s++)
            for (int t = in; t <= out; t++)
                for (int[] p : new int[][]{{s, t}, {s, -t}, {t, s}, {-t, s}}) {
                    int x = p[0], z = p[1];
                    for (int y = -1; y <= top; y++) set(x, y, z, y <= 1 ? "polished_andesite" : stone(x, y, z));
                }
        for (int s = -out; s <= out; s++) {   // 톱니 난간 · 돌출 띠 · 성벽 위 길
            for (int[] p : new int[][]{{s, out}, {s, -out}, {out, s}, {-out, s}}) {
                set(p[0], top + 1, p[1], Math.floorMod(s, 3) == 0 ? "air" : stone(p[0], top + 1, p[1]));
                set(p[0], top + 2, p[1], Math.floorMod(s, 3) == 0 ? "air" : "stone_brick_slab");
            }
            for (int[] p : new int[][]{{s, in}, {s, -in}, {in, s}, {-in, s}}) set(p[0], top + 1, p[1], "stone_brick_wall");
            String f1 = "stone_brick_stairs[facing=north,half=top]", f2 = "stone_brick_stairs[facing=south,half=top]";
            set(s, top - 2, out + 1, f1);
            set(s, top - 2, -out - 1, f2);
            set(out + 1, top - 2, s, "stone_brick_stairs[facing=west,half=top]");
            set(-out - 1, top - 2, s, "stone_brick_stairs[facing=east,half=top]");
            if (Math.floorMod(s, 12) == 6 && Math.abs(s) < in - 8) {   // 안쪽 벽 횃불 · 바깥 화살 구멍
                for (int[] p : new int[][]{{s, in - 1, 0}, {s, -in + 1, 1}, {in - 1, s, 2}, {-in + 1, s, 3}}) {
                    String f = new String[]{"north", "south", "west", "east"}[p[2]];
                    set(p[0], 4, p[1], "wall_torch[facing=" + f + "]");
                }
                for (int y = 9; y <= 11; y++) {
                    set(s, y, out, "air");
                    set(s, y, -out, "air");
                    set(out, y, s, "air");
                    set(-out, y, s, "air");
                }
            }
        }
        // 원형 탑 (70칸마다) · 모서리 큰 탑
        for (int s = -420; s <= 420; s += 70) {
            if (Math.abs(s) < 30) continue;
            for (int[] p : new int[][]{{s, in + 3}, {s, -in - 3}, {in + 3, s}, {-in - 3, s}}) wallTower(p[0], p[1], 7, 30, false);
        }
        for (int[] p : new int[][]{{1, 1}, {1, -1}, {-1, 1}, {-1, -1}}) wallTower(p[0] * (in + 3), p[1] * (in + 3), 10, 38, true);
        // 네 방향 성문루 + 해자 다리
        gatehouse(0, in + 3, 0);
        gatehouse(0, -in - 3, 1);
        gatehouse(in + 3, 0, 2);
        gatehouse(-in - 3, 0, 3);
    }

    private void wallTower(int cx, int cz, int r, int hgt, boolean big) {
        cyl(cx, cz, r + 1, -1, 1, (a, b, c) -> "polished_andesite", false);
        cyl(cx, cz, r, 2, hgt, KingdomPlan::stone, true);
        cyl(cx, cz, r - 1, 2, hgt - 1, (a, b, c) -> "air", false);
        for (int y = 8; y < hgt; y += 7) cyl(cx, cz, r - 1, y, y, (a, b, c) -> "spruce_planks", false);
        for (int y = 5; y < hgt - 2; y += 7)   // 창
            for (int k = 0; k < 8; k++) {
                double a = k * Math.PI / 4;
                int x = cx + (int) Math.round(Math.cos(a) * r), z = cz + (int) Math.round(Math.sin(a) * r);
                set(x, y, z, "air");
                set(x, y + 1, z, "air");
            }
        cyl(cx, cz, r + 1, hgt + 1, hgt + 1, (a, b, c) -> "polished_andesite", false);   // 돌출 갓돌
        cyl(cx, cz, r + 1, hgt + 2, hgt + 2, (a, b, c) -> h(a, b, c) < 0.5 ? "stone_brick_wall" : "stone_bricks", true);
        spire(cx, cz, r + 0.5, hgt + 3, big ? 18 : 13, "deepslate_tiles", "gold_block");
        for (int k = 0; k < 4; k++) {   // 깃발
            String f = new String[]{"south", "north", "east", "west"}[k];
            int dx = k == 2 ? r + 1 : k == 3 ? -r - 1 : 0, dz = k == 0 ? r + 1 : k == 1 ? -r - 1 : 0;
            set(cx + dx, hgt - 3, cz + dz, "blue_wall_banner[facing=" + f + "]");
        }
    }

    /** side: 0 남 · 1 북 · 2 동 · 3 서 */
    private void gatehouse(int cx, int cz, int side) {
        boolean ns = side <= 1;
        int sgn = side == 0 || side == 2 ? 1 : -1;
        for (int k = -1; k <= 1; k += 2) {   // 쌍둥이 탑
            int tx = ns ? cx + k * 13 : cx, tz = ns ? cz : cz + k * 13;
            cyl(tx, tz, 9, -1, 1, (a, b, c) -> "polished_andesite", false);
            cyl(tx, tz, 8, 2, 34, KingdomPlan::stone, true);
            cyl(tx, tz, 7, 2, 33, (a, b, c) -> "air", false);
            for (int y = 9; y < 34; y += 8) cyl(tx, tz, 7, y, y, (a, b, c) -> "spruce_planks", false);
            cyl(tx, tz, 9, 35, 35, (a, b, c) -> "polished_andesite", false);
            cyl(tx, tz, 9, 36, 36, (a, b, c) -> h(a, b, c) < 0.5 ? "stone_brick_wall" : "stone_bricks", true);
            spire(tx, tz, 8.5, 37, 17, "deepslate_tiles", "gold_block");
        }
        // 문 사이 성문 건물: 폭 19 · 깊이 12 · 높이 26
        int w = 9, d = 7;
        for (int a = -w; a <= w; a++)
            for (int b = -d; b <= d; b++) {
                int x = ns ? cx + a : cx + b, z = ns ? cz + b : cz + a;
                for (int y = -1; y <= 26; y++) {
                    boolean opening = Math.abs(a) <= 5 && y >= 0 && (y <= 13 || (y == 14 && Math.abs(a) <= 4) || (y == 15 && Math.abs(a) <= 2));
                    set(x, y, z, opening ? "air" : y <= 1 ? "polished_andesite" : stone(x, y, z));
                }
                set(x, 27, z, Math.floorMod(a + b, 2) == 0 ? "stone_bricks" : "air");
                if (Math.abs(a) <= 5) set(x, -1, z, pave(x, z, Math.abs(a) <= 1));
            }
        for (int a = -5; a <= 5; a++) {   // 내린 쇠창살 (위쪽만) · 아치 장식
            int x = ns ? cx + a : cx - sgn * d, z = ns ? cz - sgn * d : cz + a;
            for (int y = 11; y <= 13; y++) set(x, y, z, "iron_bars");
            int x2 = ns ? cx + a : cx + sgn * (d + 1), z2 = ns ? cz + sgn * (d + 1) : cz + a;
            set(x2, 14, z2, "chiseled_stone_bricks");
        }
        String face = new String[]{"south", "north", "east", "west"}[side];
        for (int a = -7; a <= 7; a += 14) {
            int x = ns ? cx + a : cx + sgn * (d + 1), z = ns ? cz + sgn * (d + 1) : cz + a;
            set(x, 18, z, "blue_wall_banner[facing=" + face + "]");
            set(x, 8, z, "lantern");
        }
        // 해자 다리 (성벽 바깥 끝까지) · 다리 난간 가로등
        for (int s = WALL + 6; s <= 494; s++)
            for (int a = -6; a <= 6; a++) {
                int x = ns ? cx + a : sgn * s, z = ns ? sgn * s : cz + a;
                set(x, -1, z, Math.abs(a) == 6 ? "stone_bricks" : pave(x, z, Math.abs(a) <= 1));
                set(x, -2, z, "stone_bricks");
                if (Math.abs(a) == 6) set(x, 0, z, Math.floorMod(s, 6) == 0 ? "stone_bricks" : "stone_brick_wall");
                if (Math.abs(a) == 6 && Math.floorMod(s, 6) == 0) set(x, 1, z, "lantern");
            }
    }

    // ------------------------------------------------------------------ 왕성
    private void royalCastle() {
        int cz = CASTLE_Z, R = CASTLE_HALF;
        // 성 해자 (성벽 바깥 4~10)
        for (int x = -R - 12; x <= R + 12; x++)
            for (int z = cz - R - 12; z <= cz + R + 12; z++) {
                int m = Math.max(Math.abs(x), Math.abs(z - cz));
                if (m >= R + 5 && m <= R + 11) {
                    boolean edge = m == R + 5 || m == R + 11;
                    set(x, -1, z, edge ? "stone_bricks" : "water");
                    if (!edge) {
                        set(x, -2, z, "water");
                        set(x, -3, z, "water");
                        set(x, -4, z, "dark_prismarine");
                    } else set(x, 0, z, "stone_brick_wall");
                } else if (m < R + 5) set(x, -1, z, m > R ? "polished_andesite" : grassOrPath(x, z));
            }
        // 성벽 (흰 돌 · 두께 5 · 높이 24)
        int top = 24;
        for (int s = -R; s <= R; s++)
            for (int t = R - 4; t <= R; t++)
                for (int[] p : new int[][]{{s, cz + t}, {s, cz - t}, {t, cz + s}, {-t, cz + s}}) {
                    for (int y = -1; y <= top; y++) set(p[0], y, p[1], y <= 2 ? "polished_andesite" : light(p[0], y, p[1]));
                }
        for (int s = -R; s <= R; s++) {
            for (int[] p : new int[][]{{s, cz + R}, {s, cz - R}, {R, cz + s}, {-R, cz + s}}) {
                set(p[0], top + 1, p[1], Math.floorMod(s, 3) == 0 ? "air" : "smooth_quartz");
                set(p[0], top + 2, p[1], Math.floorMod(s, 3) == 0 ? "air" : "quartz_slab");
            }
            set(s, top - 1, cz + R + 1, "quartz_stairs[facing=north,half=top]");
            set(s, top - 1, cz - R - 1, "quartz_stairs[facing=south,half=top]");
            set(R + 1, top - 1, cz + s, "quartz_stairs[facing=west,half=top]");
            set(-R - 1, top - 1, cz + s, "quartz_stairs[facing=east,half=top]");
            if (Math.floorMod(s, 10) == 5) {
                set(s, top - 3, cz + R + 1, "blue_wall_banner[facing=south]");
                set(R + 1, top - 3, cz + s, "blue_wall_banner[facing=east]");
                set(-R - 1, top - 3, cz + s, "blue_wall_banner[facing=west]");
            }
        }
        for (int[] q : new int[][]{{1, 1}, {1, -1}, {-1, 1}, {-1, -1}}) castleTower(q[0] * R, cz + q[1] * R, 11, 44);
        for (int[] q : new int[][]{{0, -1}, {1, 0}, {-1, 0}}) castleTower(q[0] * R, cz + q[1] * R, 8, 36);
        // 남쪽 성문루 (광장을 바라봄)
        int gz = cz + R;
        for (int k = -1; k <= 1; k += 2) castleTower(k * 14, gz, 8, 40);
        for (int a = -10; a <= 10; a++)
            for (int b = -6; b <= 6; b++) {
                int x = a, z = gz + b;
                for (int y = 0; y <= 30; y++) {
                    boolean open = Math.abs(a) <= 5 && (y <= 15 || (y == 16 && Math.abs(a) <= 4) || (y == 17 && Math.abs(a) <= 2));
                    set(x, y, z, open ? "air" : y <= 2 ? "polished_andesite" : light(x, y, z));
                }
                set(x, 31, z, Math.floorMod(a + b, 2) == 0 ? "smooth_quartz" : "air");
                if (Math.abs(a) <= 5) set(x, -1, z, pave(x, z, Math.abs(a) <= 1));
            }
        for (int a = -5; a <= 5; a++) for (int y = 13; y <= 15; y++) set(a, y, gz - 6, "iron_bars");
        set(0, 20, gz + 7, "gold_block");
        for (int k = -1; k <= 1; k += 2) for (int y = 18; y <= 24; y++) set(k * 7, y, gz + 7, y == 24 ? "gold_block" : (y % 2 == 0 ? "blue_wool" : "light_blue_wool"));
        for (int z = gz + 5; z <= gz + 12; z++)   // 성문 다리
            for (int a = -6; a <= 6; a++) {
                set(a, -1, z, Math.abs(a) == 6 ? "polished_andesite" : pave(a, z, Math.abs(a) <= 1));
                set(a, -2, z, "stone_bricks");
                if (Math.abs(a) == 6) set(a, 0, z, z % 3 == 0 ? "polished_andesite" : "stone_brick_wall");
                if (Math.abs(a) == 6 && z % 3 == 0) set(a, 1, z, "lantern");
            }
        // 앞뜰: 정원 · 생울타리 · 분수 · 길
        for (int z = cz + 20; z <= gz - 6; z++)
            for (int x = -5; x <= 5; x++) set(x, -1, z, Math.abs(x) <= 1 ? "polished_diorite" : "smooth_stone");
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int i = 0; i < 3; i++) {
                int x0 = sx * 14, x1 = sx * 90, z0 = cz + 34 + i * 22, z1 = z0 + 16;
                for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++)
                    for (int z = z0; z <= z1; z++) {
                        boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                        boolean path = Math.abs(z - (z0 + z1) / 2) <= 0 || Math.floorMod(x, 19) == 0;
                        if (edge) { set(x, 0, z, "oak_leaves[persistent=true]"); set(x, -1, z, "grass_block"); }
                        else if (path) set(x, -1, z, "gravel");
                        else if (h(x, 2, z) < 0.25) {
                            String fl = h(x, 3, z) < 0.5 ? "rose_bush" : "peony";
                            set(x, 0, z, fl + "[half=lower]");
                            set(x, 1, z, fl + "[half=upper]");
                        }
                    }
                for (int x = Math.min(x0, x1) + 10; x < Math.max(x0, x1); x += 19) tree(x, z0 + 4, 3);
            }
            fountainSmall(sx * 52, cz + 18);
        }
        palace(cz);
        // 안뜰 동서: 근위대 막사 · 왕실 마구간 회관 · 성벽 안쪽 가로수
        hall(-96, cz - 70, -62, cz - 40, "east");
        hall(62, cz - 70, 96, cz - 40, "west");
        for (int z = cz - 92; z <= cz + 10; z += 12) {
            tree(-92, z, 0);
            tree(92, z, 0);
        }
        for (int x = -80; x <= 80; x += 16) if (Math.abs(x) > 50) tree(x, cz - 92, 2);
        for (int k = 0; k < 5; k++) {   // 훈련용 과녁 (막사 앞)
            int x = -88 + k * 6, z = cz - 30;
            set(x, 0, z, "hay_block");
            set(x, 1, z, "target");
        }
        npcs.add(new Npc(-70, 0, cz - 32, 180f, "quest", "GUARD"));
    }

    private String grassOrPath(int x, int z) {
        return "grass_block";
    }

    private void fountainSmall(int cx, int cz) {
        cyl(cx, cz, 4.5, 0, 0, (a, b, c) -> "smooth_quartz", true);
        disc(cx, -1, cz, 3.6, "water");
        disc(cx, -2, cz, 3.6, "smooth_quartz");
        for (int y = 0; y <= 3; y++) set(cx, y, cz, "quartz_pillar");
        set(cx, 4, cz, "sea_lantern");
    }

    private void castleTower(int cx, int cz, int r, int hgt) {
        cyl(cx, cz, r + 1, -1, 2, (a, b, c) -> "polished_andesite", false);
        cyl(cx, cz, r, 3, hgt, KingdomPlan::light, true);
        cyl(cx, cz, r - 1, 3, hgt - 1, (a, b, c) -> "air", false);
        for (int y = 9; y < hgt; y += 8) cyl(cx, cz, r - 1, y, y, (a, b, c) -> "dark_oak_planks", false);
        for (int y = 6; y < hgt - 2; y += 8)
            for (int k = 0; k < 12; k++) {
                double a = k * Math.PI / 6;
                int x = cx + (int) Math.round(Math.cos(a) * r), z = cz + (int) Math.round(Math.sin(a) * r);
                set(x, y, z, "light_blue_stained_glass");
                set(x, y + 1, z, "light_blue_stained_glass");
                set(x, y + 2, z, "light_blue_stained_glass");
            }
        for (int y = hgt - 6; y <= hgt - 5; y++) cyl(cx, cz, r, y, y, (a, b, c) -> "chiseled_quartz_block", true);   // 금 띠 위 장식 띠
        cyl(cx, cz, r + 1, hgt + 1, hgt + 1, (a, b, c) -> "smooth_quartz", false);
        cyl(cx, cz, r + 1, hgt + 2, hgt + 2, (a, b, c) -> h(a, b, c) < 0.55 ? "quartz_slab" : "smooth_quartz", true);
        cyl(cx, cz, r + 1, hgt + 1, hgt + 1, (a, b, c) -> "gold_block", true);
        spire(cx, cz, r + 0.6, hgt + 3, (int) (r * 2.0), "deepslate_tiles", "gold_block");
        for (int k = 0; k < 4; k++) {
            String f = new String[]{"south", "north", "east", "west"}[k];
            int dx = k == 2 ? r + 1 : k == 3 ? -r - 1 : 0, dz = k == 0 ? r + 1 : k == 1 ? -r - 1 : 0;
            for (int y = hgt - 10; y <= hgt - 8; y++) set(cx + dx, y, cz + dz, y == hgt - 8 ? "blue_wall_banner[facing=" + f + "]" : "air");
        }
    }

    /** 본궁: 대전(동서 92 x 남북 50) · 모서리 탑 4 · 가운데 대탑 · 정문 계단 · 옥좌의 방 */
    private void palace(int cz) {
        int x0 = -46, x1 = 46, z0 = cz - 40, z1 = cz + 14, hgt = 30;
        // 기단 (3칸 높이) + 정문 큰 계단
        box(x0 - 3, 0, z0 - 3, x1 + 3, 2, z1 + 3, "polished_andesite");
        for (int x = x0 - 3; x <= x1 + 3; x++) set(x, 3, z1 + 3, "quartz_slab");
        for (int i = 0; i < 4; i++) for (int x = -9; x <= 9; x++) set(x, i - 1 + 1, z1 + 7 - i, "quartz_stairs[facing=north]");
        // 벽: 버팀벽 · 큰 아치 창 · 띠
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                for (int y = 3; y <= hgt; y++) {
                    if (!edge) { set(x, y, z, y == 3 ? "polished_deepslate" : y == 18 ? "dark_oak_planks" : "air"); continue; }
                    int u = (x == x0 || x == x1) ? z : x;
                    boolean pier = Math.floorMod(u, 8) == 0;
                    boolean window = !pier && Math.floorMod(u, 8) >= 2 && Math.floorMod(u, 8) <= 6 && ((y >= 6 && y <= 14) || (y >= 21 && y <= 27));
                    boolean arch = window && (y == 14 || y == 27) && (Math.floorMod(u, 8) == 2 || Math.floorMod(u, 8) == 6);
                    String s = pier ? "chiseled_quartz_block" : y == 17 || y == 18 ? "gold_block" : window && !arch ? "blue_stained_glass_pane" : light(x, y, z);
                    if (pier && (y == 17 || y == 18)) s = "gold_block";
                    set(x, y, z, s);
                }
            }
        for (int x = x0; x <= x1; x += 8)   // 버팀벽 (남북 면 바깥)
            for (int y = 3; y <= hgt - 4; y++) {
                set(x, y, z1 + 1, y > hgt - 7 ? "quartz_stairs[facing=north]" : "quartz_bricks");
                set(x, y, z0 - 1, y > hgt - 7 ? "quartz_stairs[facing=south]" : "quartz_bricks");
            }
        // 정문 (높이 12 아치) · 문장
        for (int x = -4; x <= 4; x++) for (int y = 3; y <= 14 - (Math.abs(x) >= 3 ? 1 : 0) - (Math.abs(x) == 4 ? 1 : 0); y++) set(x, y, z1, "air");
        for (int y = 3; y <= 13; y++) { set(-5, y, z1 + 1, "gold_block"); set(5, y, z1 + 1, "gold_block"); }
        set(0, 16, z1 + 1, "blue_wall_banner[facing=south]");
        set(0, 20, z1 + 1, "gold_block");
        // 지붕: 박공 (짙은 슬레이트 타일 계단) · 용마루 금 장식
        int W = (z1 - z0) / 2 + 2;
        for (int k = 0; k <= W; k++) {
            int y = hgt + 1 + k;
            for (int x = x0 - 2; x <= x1 + 2; x++) {
                set(x, y, z0 - 2 + k, "deepslate_tile_stairs[facing=south]");
                set(x, y, z1 + 2 - k, "deepslate_tile_stairs[facing=north]");
                if (z0 - 1 + k <= z1 + 1 - k) for (int z = z0 - 1 + k; z <= z1 + 1 - k; z++) if (x == x0 - 2 || x == x1 + 2) set(x, y, z, "deepslate_tiles");
            }
        }
        for (int x = x0 - 2; x <= x1 + 2; x++) set(x, hgt + W + 2, (z0 + z1) / 2, x % 4 == 0 ? "gold_block" : "deepslate_tile_slab");
        for (int x = x0 + 6; x <= x1 - 6; x += 12)   // 지붕창 (dormer)
            for (int s : new int[]{z0 + 5, z1 - 5}) {
                box(x - 1, hgt + 4, s - 1, x + 1, hgt + 7, s + 1, "smooth_quartz");
                set(x, hgt + 5, s + (s < (z0 + z1) / 2 ? -1 : 1), "light_blue_stained_glass");
                set(x, hgt + 6, s + (s < (z0 + z1) / 2 ? -1 : 1), "light_blue_stained_glass");
                spire(x, s, 1.6, hgt + 8, 4, "deepslate_tiles", "gold_block");
            }
        // 모서리 탑 · 가운데 대탑
        for (int[] q : new int[][]{{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) castleTower(q[0], q[1], 8, 52);
        castleTower(0, z0 - 6, 12, 74);
        castleTower(-26, z0 - 2, 6, 60);
        castleTower(26, z0 - 2, 6, 60);
        // 옥좌의 방: 붉은 융단 · 기둥 · 샹들리에 · 옥좌
        for (int z = z0 + 4; z <= z1 - 1; z++) for (int x = -2; x <= 2; x++) set(x, 3, z, Math.abs(x) == 2 ? "yellow_carpet" : "red_carpet");
        for (int z = z0 + 6; z <= z1 - 4; z += 8)
            for (int x : new int[]{-12, 12, -30, 30}) {
                for (int y = 3; y <= 17; y++) set(x, y, z, y == 3 || y == 17 ? "chiseled_quartz_block" : "quartz_pillar");
                if (Math.abs(x) == 12) { set(x, 16, z - 4, "chain"); set(x, 15, z - 4, "chain"); set(x, 14, z - 4, "lantern[hanging=true]"); }
            }
        box(-4, 3, z0 + 1, 4, 4, z0 + 4, "polished_blackstone");
        box(-3, 5, z0 + 1, 3, 5, z0 + 3, "red_carpet");
        set(0, 5, z0 + 2, "gold_block");
        set(0, 6, z0 + 2, "gold_block");
        set(0, 7, z0 + 1, "gold_block");
        set(0, 8, z0 + 1, "gold_block");
        set(-1, 5, z0 + 2, "quartz_stairs[facing=east]");
        set(1, 5, z0 + 2, "quartz_stairs[facing=west]");
        for (int y = 5; y <= 16; y++) { set(-6, y, z0 + 1, "blue_wool"); set(6, y, z0 + 1, "blue_wool"); }
        for (int x = -6; x <= 6; x++) set(x, 17, z0 + 1, "gold_block");
        // 대전 안 측면 등불
        for (int x = x0 + 4; x <= x1 - 4; x += 8) {
            set(x, 8, z0 + 1, "wall_torch[facing=south]");
            set(x, 8, z1 - 1, "wall_torch[facing=north]");
        }
    }

    // ------------------------------------------------------------------ 시장 (동쪽 큰길 양옆)
    private void market() {
        String[][] canopy = {{"red_wool", "white_wool"}, {"blue_wool", "white_wool"}, {"yellow_wool", "white_wool"}, {"green_wool", "white_wool"},
                {"orange_wool", "white_wool"}, {"purple_wool", "white_wool"}, {"cyan_wool", "white_wool"}, {"lime_wool", "white_wool"}};
        for (int x = 56; x <= 215; x++)
            for (int z = -34; z <= 34; z++)
                if (Math.abs(z) > 6) set(x, -1, z, h(x, 0, z) < 0.6 ? "smooth_stone" : h(x, 0, z) < 0.85 ? "polished_andesite" : "stone_bricks");
        int i = 0;
        for (int side = -1; side <= 1; side += 2)
            for (int k = 0; k < 15; k++) {
                int sx = 60 + k * 10, sz = side * 14;
                String[] c = canopy[(k + (side > 0 ? 3 : 0)) % canopy.length];
                stall(sx, sz, side, c[0], c[1], k);
                if (i < shops.size()) npcs.add(new Npc(sx + 3, 0, sz + side * 2, side > 0 ? 180f : 0f, "shop", shops.get(i)));
                i++;
            }
        for (int k = 0; k < 12; k++) {   // 노점 뒤 2층 상가 (큰길을 바라봄)
            int x = 60 + k * 12;
            house(x, -33, 10, 9, 2, 9100 + k, "south");
            house(x, 24, 10, 9, 2, 9200 + k, "north");
        }
        // 시장 입구 아치 (광장 쪽)
        for (int z = -9; z <= 9; z += 18) for (int y = 0; y <= 10; y++) set(56, y, z, "stone_bricks");
        for (int z = -9; z <= 9; z++) { set(56, 11, z, "spruce_planks"); set(56, 12, z, Math.abs(z) % 3 == 0 ? "spruce_fence" : "air"); }
        for (int z = -6; z <= 6; z += 3) set(56, 10, z, "lantern[hanging=true]");
    }

    /** 노점: 7 x 6 · 줄무늬 천막 · 계산대 · 상품 */
    private void stall(int x0, int z0, int side, String c1, String c2, int k) {
        int z1 = z0 + side * 5;
        int za = Math.min(z0, z1), zb = Math.max(z0, z1);
        for (int x = x0; x <= x0 + 6; x++) for (int z = za; z <= zb; z++) set(x, -1, z, "spruce_planks");
        for (int[] p : new int[][]{{x0, z0}, {x0 + 6, z0}, {x0, z1}, {x0 + 6, z1}}) for (int y = 0; y <= 3; y++) set(p[0], y, p[1], "spruce_fence");
        for (int x = x0 - 1; x <= x0 + 7; x++)   // 천막 (앞쪽이 낮게 기울어짐)
            for (int z = za - 1; z <= zb + 1; z++) {
                int dz = side > 0 ? z - za : zb - z;
                int y = 4 + (dz >= 3 ? 1 : 0);
                set(x, y, z, Math.floorMod(x, 2) == 0 ? c1 : c2);
            }
        for (int x = x0 + 1; x <= x0 + 5; x++) set(x, 0, z0 + side, "barrel[facing=up]");   // 계산대
        for (int x = x0 + 1; x <= x0 + 5; x += 2) set(x, 1, z0 + side, new String[]{"melon", "pumpkin", "hay_block", "chest[facing=" + (side > 0 ? "north" : "south") + "]", "beehive[facing=" + (side > 0 ? "north" : "south") + "]"}[(k + x) % 5]);
        set(x0 + 1, 0, z1 - side, "crafting_table");
        set(x0 + 5, 0, z1 - side, "barrel[facing=up]");
        set(x0 + 5, 1, z1 - side, "lantern");
        set(x0 + 3, 3, z0 + side * 2, "lantern[hanging=true]");
    }

    // ------------------------------------------------------------------ 모험가 광장 (의뢰)
    private void guildSquare() {
        for (int x = -215; x <= -56; x++)
            for (int z = -50; z <= 50; z++)
                if (Math.abs(z) > 6) set(x, -1, z, h(x, 1, z) < 0.5 ? "cobblestone" : h(x, 1, z) < 0.8 ? "stone_bricks" : "mossy_cobblestone");
        // 길드 회관 (북쪽): 반목조 큰 집 40 x 22
        int x0 = -170, x1 = -120, z0 = -48, z1 = -22;
        hall(x0, z0, x1, z1, "south");
        // 의뢰 게시판 (광장 가운데) + 의뢰 NPC 반원 배치
        int bx = -135, bz = 22;
        box(bx - 4, 0, bz, bx + 4, 0, bz, "spruce_planks");
        for (int y = 1; y <= 4; y++) { set(bx - 4, y, bz, "spruce_log"); set(bx + 4, y, bz, "spruce_log"); }
        for (int x = bx - 3; x <= bx + 3; x++) for (int y = 1; y <= 3; y++) set(x, y, bz, "spruce_planks");
        for (int x = bx - 3; x <= bx + 3; x++) set(x, 2, bz - 1, (x & 1) == 0 ? "white_wall_banner[facing=north]" : "brown_wall_banner[facing=north]");
        for (int x = bx - 5; x <= bx + 5; x++) set(x, 5, bz, "spruce_slab");
        set(bx - 5, 4, bz, "lantern[hanging=true]");
        set(bx + 5, 4, bz, "lantern[hanging=true]");
        int n = Math.min(10, quests.size());
        for (int k = 0; k < n; k++) {
            double a = Math.PI * (0.1 + 0.8 * k / Math.max(1, n - 1));
            int x = bx + (int) Math.round(Math.cos(a) * 16), z = bz - 4 - (int) Math.round(Math.sin(a) * 14);
            box(x - 1, -1, z - 1, x + 1, -1, z + 1, "polished_andesite");
            set(x + 1, 0, z + 1, "lantern");
            float yaw = (float) Math.toDegrees(Math.atan2(-(bx - x), bz - z));
            npcs.add(new Npc(x, 0, z, yaw, "quest", quests.get(k % quests.size())));
        }
        // 모닥불 · 훈련 허수아비 · 통
        for (int k = 0; k < 4; k++) {
            int x = -200 + k * 8, z = 35;
            set(x, 0, z, "hay_block");
            set(x, 1, z, "hay_block");
            set(x, 2, z, "carved_pumpkin[facing=north]");
            set(x - 1, 1, z, "spruce_fence");
            set(x + 1, 1, z, "spruce_fence");
        }
        set(-100, 0, 30, "campfire[lit=true]");
        bench(-104, 33, "north", 3, true);
        bench(-104, 27, "south", 3, true);
        for (int k = 0; k < 6; k++) set(-210 + k, 0, -10, "barrel[facing=up]");
    }

    /** 반목조 큰 회관 (문은 door 방향) */
    private void hall(int x0, int z0, int x1, int z1, String door) {
        int hgt = 12;
        box(x0 - 1, -1, z0 - 1, x1 + 1, -1, z1 + 1, "cobblestone");
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                for (int y = 0; y <= hgt; y++) {
                    if (!edge) { set(x, y, z, y == 0 ? "spruce_planks" : y == 6 ? "spruce_planks" : "air"); continue; }
                    boolean post = (x == x0 || x == x1) && Math.floorMod(z - z0, 5) == 0 || (z == z0 || z == z1) && Math.floorMod(x - x0, 5) == 0;
                    boolean beam = y == 5 || y == 6 || y == hgt;
                    boolean win = !post && !beam && (y == 2 || y == 3 || y == 8 || y == 9) && Math.floorMod((z == z0 || z == z1 ? x : z) - x0, 5) == 2;
                    set(x, y, z, post || beam ? "dark_oak_log" : win ? "glass_pane" : y <= 4 ? "cobblestone" : "white_terracotta");
                }
            }
        gable(x0 - 1, z0 - 1, x1 + 1, z1 + 1, hgt + 1, "dark_oak", "white_terracotta");
        int mx = (x0 + x1) / 2, mz = (z0 + z1) / 2;
        if (door.equals("south")) {
            for (int x = mx - 2; x <= mx + 2; x++) for (int y = 0; y <= 4; y++) set(x, y, z1, "air");
            set(mx, 7, z1 + 1, "orange_wall_banner[facing=south]");
            for (int x = mx - 3; x <= mx + 3; x += 6) set(x, 3, z1 + 1, "lantern");
        } else {
            int wx = door.equals("east") ? x1 : x0, o = door.equals("east") ? 1 : -1;
            for (int z = mz - 2; z <= mz + 2; z++) for (int y = 0; y <= 4; y++) set(wx, y, z, "air");
            set(wx + o, 7, mz, "blue_wall_banner[facing=" + door + "]");
            for (int z = mz - 3; z <= mz + 3; z += 6) set(wx + o, 3, z, "lantern");
        }
        for (int x = x0 + 3; x < x1; x += 6) { set(x, 1, z0 + 1, "bookshelf"); set(x + 1, 1, z0 + 1, "bookshelf"); set(x, 2, z0 + 1, "bookshelf"); }
        for (int x = x0 + 4; x < x1 - 2; x += 8) { set(x, 1, (z0 + z1) / 2, "spruce_fence"); set(x, 2, (z0 + z1) / 2, "spruce_pressure_plate"); set(x, 5, (z0 + z1) / 2, "lantern[hanging=true]"); }
    }

    /** 박공지붕 (긴 쪽을 따라 용마루) — 재질: oak · spruce · dark_oak · deepslate_tile · brick · mud_brick 등 계단이 있는 이름 */
    void gable(int x0, int z0, int x1, int z1, int y0, String mat, String gableFill) {
        String stairs = mat.equals("deepslate_tile") || mat.equals("brick") || mat.equals("mud_brick") || mat.equals("stone_brick") ? mat + "_stairs" : mat + "_stairs";
        String slab = mat.equals("deepslate_tile") || mat.equals("brick") || mat.equals("mud_brick") || mat.equals("stone_brick") ? mat + "_slab" : mat + "_slab";
        boolean alongX = (x1 - x0) >= (z1 - z0);
        int span = alongX ? z1 - z0 : x1 - x0;
        int half = span / 2;
        for (int k = 0; k <= half; k++) {
            int y = y0 + k;
            if (alongX) {
                for (int x = x0; x <= x1; x++) {
                    set(x, y, z0 + k, stairs + "[facing=south]");
                    set(x, y, z1 - k, stairs + "[facing=north]");
                }
                for (int z = z0 + k + 1; z <= z1 - k - 1; z++) { set(x0 + 1, y, z, gableFill); set(x1 - 1, y, z, gableFill); }
            } else {
                for (int z = z0; z <= z1; z++) {
                    set(x0 + k, y, z, stairs + "[facing=east]");
                    set(x1 - k, y, z, stairs + "[facing=west]");
                }
                for (int x = x0 + k + 1; x <= x1 - k - 1; x++) { set(x, y, z0 + 1, gableFill); set(x, y, z1 - 1, gableFill); }
            }
        }
        if (span % 2 == 0) {
            int y = y0 + half;
            if (alongX) for (int x = x0; x <= x1; x++) set(x, y, z0 + half, slab);
            else for (int z = z0; z <= z1; z++) set(x0 + half, y, z, slab);
        }
    }

    // ------------------------------------------------------------------ 대성당
    private void cathedral(int cx, int cz) {
        int x0 = cx - 16, x1 = cx + 16, z0 = cz - 48, z1 = cz + 40, hgt = 26;
        for (int x = cx - 52; x <= cx + 52; x++) for (int z = cz - 70; z <= cz + 70; z++) set(x, -1, z, h(x, 5, z) < 0.7 ? "grass_block" : "moss_block");
        for (int z = z1; z <= cz + 70; z++) for (int x = cx - 4; x <= cx + 4; x++) set(x, -1, z, Math.abs(x - cx) <= 1 ? "polished_diorite" : "smooth_stone");
        box(x0 - 2, -1, z0 - 2, x1 + 2, 0, z1 + 2, "polished_andesite");
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                for (int y = 1; y <= hgt; y++) {
                    if (!edge) { set(x, y, z, "air"); continue; }
                    int u = (x == x0 || x == x1) ? z : x;
                    boolean pier = Math.floorMod(u - z0, 6) == 0;
                    boolean win = !pier && y >= 5 && y <= 20 && Math.floorMod(u - z0, 6) >= 2 && Math.floorMod(u - z0, 6) <= 4;
                    String glass = new String[]{"red_stained_glass_pane", "blue_stained_glass_pane", "yellow_stained_glass_pane", "purple_stained_glass_pane"}[Math.floorMod(y / 3 + u, 4)];
                    set(x, y, z, pier ? "polished_andesite" : win ? glass : light(x, y, z));
                }
            }
        for (int z = z0; z <= z1; z += 6)   // 날아 버팀벽
            for (int s = -1; s <= 1; s += 2) {
                int xx = s < 0 ? x0 : x1;
                for (int k = 1; k <= 4; k++) for (int y = 1; y <= hgt - 4 - k * 3; y++) set(xx + s * k, y, z, k == 4 ? "stone_bricks" : "polished_andesite");
                for (int k = 1; k <= 4; k++) set(xx + s * k, hgt - 4 - k * 3 + 1, z, "stone_brick_stairs[facing=" + (s < 0 ? "east" : "west") + "]");
                set(xx + s * 4, hgt - 15, z, "stone_brick_wall");
            }
        // 가파른 지붕
        for (int k = 0; k <= 17; k++)
            for (int z = z0 - 1; z <= z1 + 1; z++) {
                set(x0 - 1 + k, hgt + 1 + k, z, "deepslate_tile_stairs[facing=east]");
                set(x1 + 1 - k, hgt + 1 + k, z, "deepslate_tile_stairs[facing=west]");
                if (x0 + k <= x1 - k) for (int x = x0 + k; x <= x1 - k; x++) { if (z == z0 || z == z1) set(x, hgt + 1 + k, z, light(x, hgt + 1 + k, z)); }
            }
        // 앞면 쌍둥이 종탑 · 장미창 · 정문
        for (int s = -1; s <= 1; s += 2) {
            int tx = cx + s * 13, tz = z1 + 2;
            box(tx - 4, 1, tz - 4, tx + 4, 46, tz + 4, "stone_bricks");
            box(tx - 3, 1, tz - 3, tx + 3, 45, tz + 3, "air");
            for (int y = 30; y <= 36; y++) for (int k = -1; k <= 1; k++) { set(tx + k, y, tz + 4, "air"); set(tx + k, y, tz - 4, "air"); set(tx + 4, y, tz + k, "air"); set(tx - 4, y, tz + k, "air"); }
            set(tx, 33, tz, "bell[attachment=ceiling]");
            set(tx, 34, tz, "chain");
            box(tx - 4, 46, tz - 4, tx + 4, 46, tz + 4, "polished_andesite");
            for (int k = 0; k < 14; k++) {
                int r = 4 - k * 4 / 14;
                for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) if (Math.abs(x) == r || Math.abs(z) == r) set(tx + x, 47 + k, tz + z, "deepslate_tiles");
            }
            set(tx, 61, tz, "gold_block");
            set(tx, 62, tz, "lightning_rod");
        }
        for (int dx = -6; dx <= 6; dx++)
            for (int dy = -6; dy <= 6; dy++) {
                double d = Math.sqrt(dx * dx + dy * dy);
                if (d > 6.3) continue;
                double a = Math.atan2(dy, dx);
                String g = d < 1.5 ? "yellow_stained_glass" : d > 5.4 ? "chiseled_quartz_block" : ((int) ((a + Math.PI) / (Math.PI / 6)) % 2 == 0 ? "red_stained_glass" : "blue_stained_glass");
                set(cx + dx, 18 + dy, z1, g);
            }
        for (int x = cx - 3; x <= cx + 3; x++) for (int y = 1; y <= 8 - (Math.abs(x - cx) == 3 ? 1 : 0); y++) set(x, y, z1, "air");
        for (int z = z0 + 2; z < z1 - 1; z += 3) for (int s = -1; s <= 1; s += 2) for (int k = 2; k <= 10; k++) set(cx + s * k, 1, z, "spruce_stairs[facing=north]");
        for (int z = z0 + 1; z < z1; z++) set(cx, 1, z, "red_carpet");
        box(cx - 3, 1, z0 + 1, cx + 3, 2, z0 + 3, "smooth_quartz");
        set(cx, 3, z0 + 2, "gold_block");
        set(cx, 4, z0 + 2, "end_rod");
        for (int z = z0 + 6; z < z1; z += 12) set(cx, hgt - 1, z, "lantern[hanging=true]");
        for (int k = 0; k < 10; k++) tree(cx - 44 + (k % 2) * 88, cz - 60 + k * 12, 3);
    }

    // ------------------------------------------------------------------ 원형 투기장
    private void arena(int cx, int cz) {
        int R = 46, r = 24;
        for (int dx = -R - 8; dx <= R + 8; dx++) for (int dz = -R - 8; dz <= R + 8; dz++) set(cx + dx, -1, cz + dz, "grass_block");
        for (int dx = -R; dx <= R; dx++)
            for (int dz = -R; dz <= R; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                int x = cx + dx, z = cz + dz;
                if (d <= r) { set(x, -1, z, h(x, 0, z) < 0.8 ? "sand" : "smooth_sandstone"); continue; }
                if (d > R + 0.4) continue;
                int tier = (int) ((d - r) / 2.2);
                int top = 3 + tier * 2;
                double ang = Math.atan2(dz, dx);
                boolean gate = Math.abs(Math.sin(ang)) < 0.08 || Math.abs(Math.cos(ang)) < 0.08;
                if (gate && d < R - 1) { set(x, -1, z, "smooth_sandstone"); continue; }
                for (int y = 0; y <= top; y++) set(x, y, z, y == top ? (d > R - 1.2 ? "cut_sandstone" : "smooth_sandstone") : d > R - 1.2 ? (y % 6 == 3 && (int) (ang * 20) % 2 == 0 ? "air" : "cut_sandstone") : "sandstone");
                if (d > R - 1.2) for (int y = top + 1; y <= top + 3; y++) set(x, y, z, (int) Math.round(ang * 24) % 3 == 0 ? "cut_sandstone" : y == top + 3 ? "sandstone_slab" : "air");
            }
        for (int k = 0; k < 16; k++) {
            double a = k * Math.PI / 8;
            int x = cx + (int) Math.round(Math.cos(a) * (R + 1)), z = cz + (int) Math.round(Math.sin(a) * (R + 1));
            for (int y = 0; y <= 26; y++) set(x, y, z, y >= 24 ? "chiseled_sandstone" : "cut_sandstone");
            set(x, 27, z, "red_wall_banner[facing=" + (Math.abs(Math.cos(a)) > Math.abs(Math.sin(a)) ? (Math.cos(a) > 0 ? "east" : "west") : (Math.sin(a) > 0 ? "south" : "north")) + "]");
        }
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4 + 0.2;
            set(cx + (int) (Math.cos(a) * 20), 0, cz + (int) (Math.sin(a) * 20), "lantern");
        }
    }

    // ------------------------------------------------------------------ 호수 공원
    private void lakePark(int cx, int cz) {
        for (int dx = -80; dx <= 80; dx++)
            for (int dz = -75; dz <= 75; dz++) {
                int x = cx + dx, z = cz + dz;
                double e = Math.pow(dx / 62.0, 2) + Math.pow(dz / 50.0, 2) + (h(x / 4, 0, z / 4) - 0.5) * 0.15;
                if (e < 1.0) {
                    int depth = e < 0.4 ? 4 : e < 0.75 ? 3 : 2;
                    for (int y = -1; y >= -depth; y--) set(x, y, z, "water");
                    set(x, -depth - 1, z, e < 0.6 ? "clay" : "sand");
                    if (e > 0.8 && h(x, 1, z) < 0.06) set(x, 0, z, "lily_pad");
                } else if (e < 1.12) set(x, -1, z, "sand");
                else {
                    set(x, -1, z, h(x, 2, z) < 0.8 ? "grass_block" : "moss_block");
                    if (h(x, 3, z) < 0.012) tree(x, z, (int) (h(x, 4, z) * 4));
                    else if (h(x, 4, z) < 0.05) set(x, 0, z, h(x, 5, z) < 0.5 ? "dandelion" : "azure_bluet");
                }
            }
        // 섬 · 정자
        disc(cx, -1, cz, 9.5, "grass_block");
        disc(cx, -2, cz, 9.5, "dirt");
        cyl(cx, cz, 5.5, 0, 0, (a, b, c) -> "smooth_quartz", false);
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            int x = cx + (int) Math.round(Math.cos(a) * 5), z = cz + (int) Math.round(Math.sin(a) * 5);
            for (int y = 1; y <= 5; y++) set(x, y, z, "quartz_pillar");
        }
        for (int k = 0; k <= 5; k++) cyl(cx, cz, 6.5 - k * 1.2, 6 + k, 6 + k, (a, b, c) -> "dark_oak_planks", false);
        set(cx, 12, cz, "gold_block");
        set(cx, 5, cz, "lantern[hanging=true]");
        for (int x = cx + 9; x <= cx + 62; x++) {   // 다리 (동쪽 호숫가로)
            int arch = (int) Math.round(2.5 * Math.sin(Math.PI * (x - cx - 9) / 53.0));
            for (int z = cz - 2; z <= cz + 2; z++) {
                set(x, -1 + arch, z, "spruce_planks");
                if (Math.abs(z - cz) == 2) set(x, arch, z, "spruce_fence");
            }
            if ((x - cx) % 8 == 0) { set(x, arch + 1, cz - 2, "lantern"); set(x, arch + 1, cz + 2, "lantern"); }
        }
        npcs.add(new Npc(cx + 66, 0, cz + 4, 90f, "quest", "FISHER"));
    }

    // ------------------------------------------------------------------ 대장간 거리
    private void forgeRow(int cx, int cz) {
        for (int x = cx - 52; x <= cx + 52; x++) for (int z = cz - 34; z <= cz + 34; z++) set(x, -1, z, h(x, 6, z) < 0.6 ? "cobblestone" : "gravel");
        for (int k = 0; k < 4; k++) {
            int x0 = cx - 48 + k * 25, z0 = cz - 26;
            forge(x0, z0);
        }
        for (int k = 0; k < 4; k++) {
            int x0 = cx - 48 + k * 25, z0 = cz + 10;
            house(x0, z0, 18, 14, 2, k + 7, "north");
        }
        npcs.add(new Npc(cx, 0, cz - 2, 0f, "quest", "MINER"));
    }

    private void forge(int x0, int z0) {
        int x1 = x0 + 18, z1 = z0 + 14;
        box(x0, -1, z0, x1, -1, z1, "stone_bricks");
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                boolean post = (x == x0 || x == x1) && (z == z0 || z == z1 || z == (z0 + z1) / 2) || (z == z0 || z == z1) && (x - x0) % 6 == 0;
                for (int y = 0; y <= 6; y++) {
                    if (post) set(x, y, z, "spruce_log");
                    else if ((z == z0) && y <= 6) set(x, y, z, y <= 2 ? "cobblestone" : y == 6 ? "spruce_log" : "air");
                    else if (x == x0 || x == x1 || z == z1) set(x, y, z, y <= 6 ? (y == 6 ? "spruce_log" : "bricks") : "air");
                }
            }
        gable(x0 - 1, z0 - 1, x1 + 1, z1 + 1, 7, "brick", "bricks");
        int fx = x0 + 3, fz = z1 - 3;   // 화덕 · 굴뚝
        box(fx - 1, 0, fz - 1, fx + 1, 2, fz + 1, "bricks");
        set(fx, 1, fz, "lava");
        set(fx, 1, fz - 1, "iron_bars");
        for (int y = 3; y <= 16; y++) set(fx, y, fz, "bricks");
        set(fx, 17, fz, "campfire[lit=true]");
        set(x0 + 8, 0, z0 + 6, "anvil[facing=east]");
        set(x0 + 11, 0, z0 + 6, "smithing_table");
        set(x0 + 13, 0, z0 + 8, "grindstone[face=floor,facing=north]");
        set(x0 + 6, 0, z1 - 1, "blast_furnace[facing=south,lit=true]");
        set(x0 + 7, 0, z1 - 1, "barrel[facing=up]");
        set(x0 + 9, 4, z0 + 7, "lantern[hanging=true]");
        for (int y = 0; y <= 1; y++) set(x0 + 15, y, z0 + 2, y == 0 ? "stone_bricks" : "iron_block");
    }

    // ------------------------------------------------------------------ 바깥 농장 띠 · 풍차
    private void farms() {
        String[] crops = {"wheat[age=7]", "carrots[age=7]", "potatoes[age=7]", "beetroots[age=3]"};
        for (int x = -WALL + 2; x <= WALL - 2; x++)
            for (int z = -WALL + 2; z <= WALL - 2; z++) {
                int m = Math.max(Math.abs(x), Math.abs(z));
                if (m < 404 || m > WALL - 4 || Math.abs(x) <= 8 || Math.abs(z) <= 8) continue;
                int fx = Math.floorDiv(x, 18), fz = Math.floorDiv(z, 18);
                int lx = Math.floorMod(x, 18), lz = Math.floorMod(z, 18);
                if (lx == 0 || lz == 0) { set(x, -1, z, "dirt_path"); continue; }
                if (lx == 9) { set(x, -1, z, "water"); continue; }
                set(x, -1, z, "farmland[moisture=7]");
                set(x, 0, z, crops[Math.floorMod(fx * 7 + fz * 3, crops.length)]);
            }
        for (int[] p : new int[][]{{430, 300}, {-430, -300}, {300, -430}, {-300, 430}, {430, -150}, {-150, 430}}) windmill(p[0], p[1]);
        npcs.add(new Npc(420, 0, 120, 270f, "quest", "HERDER"));
        npcs.add(new Npc(-420, 0, -120, 90f, "quest", "COLLECTOR"));
    }

    private void windmill(int cx, int cz) {
        box(cx - 7, -1, cz - 7, cx + 7, -1, cz + 7, "grass_block");
        cyl(cx, cz, 5, 0, 16, (a, b, c) -> b < 3 ? "cobblestone" : h(a, b, c) < 0.5 ? "stripped_birch_wood" : "white_terracotta", true);
        cyl(cx, cz, 4, 0, 15, (a, b, c) -> "air", false);
        for (int k = 0; k <= 6; k++) cyl(cx, cz, 6 - k, 17 + k, 17 + k, (a, b, c) -> "spruce_planks", true);
        set(cx, 0, cz + 5, "air");
        set(cx, 1, cz + 5, "air");
        for (int y = 3; y <= 13; y += 5) set(cx, y, cz + 5, "glass_pane");
        int hub = 18, hz = cz + 6;
        set(cx, hub, hz, "spruce_log");
        for (int k = 0; k < 4; k++) {
            double a = k * Math.PI / 2 + 0.4;
            for (int r = 1; r <= 11; r++) {
                int x = cx + (int) Math.round(Math.cos(a) * r), y = hub + (int) Math.round(Math.sin(a) * r);
                set(x, y, hz, "spruce_fence");
                if (r > 3) {
                    int ox = (int) Math.round(-Math.sin(a) * 1.5), oy = (int) Math.round(Math.cos(a) * 1.5);
                    set(x + ox, y + oy, hz, "white_wool");
                    set(x + ox / 2, y + oy / 2, hz, "white_wool");
                }
            }
        }
    }

    // ------------------------------------------------------------------ 주거 구역 (골목 격자의 칸마다 집)
    private void districts() {
        String[] walls = {"white_terracotta", "smooth_sandstone", "stripped_birch_wood", "mushroom_stem", "light_gray_terracotta", "calcite"};
        for (int gx = -448; gx < 448; gx += 64)
            for (int gz = -448; gz < 448; gz += 64) {
                int cx0 = gx + 3, cz0 = gz + 3, cx1 = gx + 61, cz1 = gz + 61;   // 골목 안쪽
                // 큰길 · 순환로를 피해 잘라냄
                for (int qx = cx0; qx <= cx1; qx += 30)
                    for (int qz = cz0; qz <= cz1; qz += 30) {
                        int x0 = qx, z0 = qz, x1 = Math.min(cx1, qx + 28), z1 = Math.min(cz1, qz + 28);
                        if (blockedRect(x0, z0, x1, z1)) continue;
                        lot(x0, z0, x1, z1, walls);
                    }
            }
    }

    private boolean blockedRect(int x0, int z0, int x1, int z1) {
        for (int x = x0; x <= x1; x += 4)
            for (int z = z0; z <= z1; z += 4) {
                if (reserved(x, z) || inCastle(x, z, 14)) return true;
                if (Math.abs(x) <= 9 || Math.abs(z) <= 9) return true;
                if (Math.abs(Math.abs(x) - 330) <= 5 || Math.abs(Math.abs(z) - 330) <= 5) return true;
            }
        if (reserved(x1, z1) || inCastle(x1, z1, 14)) return true;
        return false;
    }

    /** 29 x 29 칸: 집 4채 (네 모서리) + 가운데 마당 · 우물 · 나무 */
    private void lot(int x0, int z0, int x1, int z1, String[] walls) {
        int id = (int) (h(x0, 9, z0) * 1000);
        int[][] corners = {{x0, z0, 0}, {x1, z0, 1}, {x0, z1, 2}, {x1, z1, 3}};
        for (int k = 0; k < 4; k++) {
            int w = 9 + (int) (h(x0 + k, 1, z0) * 4), d = 8 + (int) (h(x0, 2, z0 + k) * 3);
            int floors = 1 + (int) (h(x0 + k, 3, z0 + k) * 2.6);
            int[] c = corners[k];
            int hx0 = c[2] % 2 == 0 ? c[0] : c[0] - w, hz0 = c[2] < 2 ? c[1] : c[1] - d;
            String door = c[2] < 2 ? "south" : "north";
            if (h(x0 + k, 4, z0) < 0.12) { garden(hx0, hz0, w, d); continue; }
            house(hx0, hz0, w, d, floors, id + k, door);
        }
        int mx = (x0 + x1) / 2, mz = (z0 + z1) / 2;
        double r = h(mx, 5, mz);
        if (r < 0.35) well(mx, mz);
        else if (r < 0.75) tree(mx, mz, (int) (h(mx, 6, mz) * 4));
        else { planter(mx - 2, mz - 2, mx + 2, mz + 2); set(mx, 1, mz, "lantern"); }
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) if (h(x, 8, z) < 0.03) set(x, 0, z, h(x, 9, z) < 0.5 ? "fern" : "poppy");
    }

    private void garden(int x0, int z0, int w, int d) {
        for (int x = x0; x <= x0 + w; x++)
            for (int z = z0; z <= z0 + d; z++) {
                boolean edge = x == x0 || x == x0 + w || z == z0 || z == z0 + d;
                if (edge) set(x, 0, z, "oak_fence");
                else if (Math.floorMod(x - x0, 3) == 0) set(x, -1, z, "dirt_path");
                else { set(x, -1, z, "farmland[moisture=7]"); set(x, 0, z, (x + z) % 2 == 0 ? "carrots[age=7]" : "potatoes[age=7]"); }
            }
        set(x0 + w / 2, 0, z0, "oak_fence_gate[facing=south]");
    }

    private void well(int x, int z) {
        cyl(x, z, 2, 0, 0, (a, b, c) -> "cobblestone", true);
        disc(x, -1, z, 1.2, "water");
        for (int[] p : new int[][]{{-2, 0}, {2, 0}}) for (int y = 1; y <= 3; y++) set(x + p[0], y, z + p[1], "spruce_fence");
        for (int dx = -2; dx <= 2; dx++) set(x + dx, 4, z, "spruce_slab");
        set(x, 3, z, "chain");
    }

    /** 반목조 집: 1층 돌 · 위층 회벽 + 나무 뼈대 · 박공지붕 · 굴뚝 · 창 · 문 · 꽃 상자 · 등 */
    void house(int x0, int z0, int w, int d, int floors, int seed, String door) {
        String[] roofs = {"spruce", "dark_oak", "deepslate_tile", "brick", "oak", "mud_brick", "stone_brick", "mangrove"};
        String[] plaster = {"white_terracotta", "smooth_sandstone", "stripped_birch_wood", "mushroom_stem", "calcite", "light_gray_terracotta"};
        String[] frames = {"dark_oak_log", "spruce_log", "oak_log", "stripped_dark_oak_log"};
        String[] bases = {"cobblestone", "stone_bricks", "mossy_cobblestone", "bricks", "andesite"};
        Random r = new Random(seed * 7919L);
        String roof = roofs[r.nextInt(roofs.length)], wall = plaster[r.nextInt(plaster.length)], frame = frames[r.nextInt(frames.length)], base = bases[r.nextInt(bases.length)];
        String floor = new String[]{"spruce_planks", "oak_planks", "dark_oak_planks", "birch_planks"}[r.nextInt(4)];
        int x1 = x0 + w, z1 = z0 + d, story = 4, hgt = floors * story;
        box(x0 - 1, -1, z0 - 1, x1 + 1, -1, z1 + 1, base);
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                for (int y = 0; y < hgt; y++) {
                    if (!edge) { set(x, y, z, y % story == 0 && y > 0 ? floor : "air"); continue; }
                    boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1);
                    int u = (z == z0 || z == z1) ? x - x0 : z - z0;
                    boolean post = corner || u % 4 == 0;
                    boolean beam = y % story == 0 && y > 0;
                    boolean win = !post && !beam && (y % story == 1 || y % story == 2) && u % 4 == 2;
                    String s = beam || post && y >= story ? frame : win ? "glass_pane" : y < story ? (post ? frame : base) : wall;
                    if (y >= story && !post && !beam && !win && (y % story == 3) && u % 4 != 2 && r.nextInt(5) == 0) s = frame;   // 대각 가새 느낌
                    set(x, y, z, s);
                }
                set(x, -1, z, floor);
            }
        gable(x0 - 1, z0 - 1, x1 + 1, z1 + 1, hgt, roof, wall);
        // 문 · 문 위 등 · 계단
        int dx = (x0 + x1) / 2, dz = door.equals("south") ? z1 : z0;
        int out = door.equals("south") ? 1 : -1;
        set(dx, 0, dz, "spruce_door[facing=" + door + ",half=lower]");
        set(dx, 1, dz, "spruce_door[facing=" + door + ",half=upper]");
        set(dx + 1, 2, dz + out, "wall_torch[facing=" + door + "]");
        set(dx, -1, dz + out, "dirt_path");
        // 꽃 상자 (위층 창 아래) · 굴뚝
        for (int x = x0 + 2; x < x1; x += 4) {
            if (floors > 1 && r.nextInt(2) == 0) {
                set(x, story, dz + out, "spruce_trapdoor[facing=" + door + ",half=top,open=false]");
                set(x, story + 1, dz + out, new String[]{"potted_red_tulip", "potted_azure_bluet", "potted_oxeye_daisy", "potted_cornflower"}[r.nextInt(4)]);
            }
        }
        int chx = r.nextBoolean() ? x0 + 1 : x1 - 1, chz = (z0 + z1) / 2;
        for (int y = 0; y <= hgt + (Math.min(w, d) / 2) + 2; y++) set(chx, y, chz, "bricks");
        if (r.nextInt(3) == 0) set(chx, hgt + Math.min(w, d) / 2 + 3, chz, "campfire[lit=true]");
        // 안: 등 · 탁자 · 상자 · 책장
        set(x0 + 2, 0, z0 + 2, "barrel[facing=up]");
        set(x1 - 2, 0, z0 + 2, r.nextBoolean() ? "crafting_table" : "bookshelf");
        set((x0 + x1) / 2, story - 1, (z0 + z1) / 2, "lantern[hanging=true]");
        set((x0 + x1) / 2 + 1, 0, (z0 + z1) / 2, "oak_fence");
        set((x0 + x1) / 2 + 1, 1, (z0 + z1) / 2, "oak_pressure_plate");
        // 집 앞 덤불
        if (r.nextInt(2) == 0) for (int x = x0; x <= x1; x += 3) if (Math.abs(x - dx) > 1) set(x, 0, dz + out, "oak_leaves[persistent=true]");
    }
}
