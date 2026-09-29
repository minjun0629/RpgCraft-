package kr.rpgcraft.war;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

/**
 * 공성전용 대형 성 3종 (v5.5.0).
 * 관리자가 서 있는 자리를 성 한가운데로 지형을 고르고 성을 짓는다. 블록은 여러 틱에 나눠 놓아 서버가 멈추지 않게 한다.
 * 다 지으면 성벽 구간(한 면을 3구간씩)과 신호기, 공격 · 방어 진영 스폰을 공성전에 자동 등록한다 → 따로 설정할 것 없음.
 *
 *  1. 왕성       — 돌벽돌 정사각 성벽(반지름 34), 모서리 탑 4 · 성문 탑 2 · 가운데 본성(탑 4), 신호기는 본성 앞 뜰
 *  2. 흑요 요새  — 흑암 이중 성벽: 바깥(반지름 38) + 안쪽 성채(반지름 17), 모서리 능보 4, 신호기는 안쪽 성채 한가운데
 *  3. 백악 성채  — 석회 · 석영 3단 계단식 성채: 바깥 성벽(반지름 40) · 가운데 단(반지름 26, 6칸 높이) · 꼭대기 단(반지름 12), 신호기는 꼭대기
 */
public class SiegeCastleBuilder {
    private record Op(int x, int y, int z, Material m) {}

    public static final String[] NAMES = {"", "왕성", "흑요 요새", "백악 성채"};

    private final RpgCraft plugin;
    private final List<Op> ops = new ArrayList<>();
    /** 지반을 채울 기둥 (x, z) — 짓는 순간 아래가 비어 있으면 채움 */
    private final List<int[]> foundation = new ArrayList<>();
    private final List<int[][]> wallBoxes = new ArrayList<>();
    private final List<String> wallIds = new ArrayList<>();
    private int[] beacon, attacker, defender;

    public SiegeCastleBuilder(RpgCraft plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ 블록 도구 (상대 좌표)
    private void set(int x, int y, int z, Material m) {
        ops.add(new Op(x, y, z, m));
    }

    private void box(int x1, int y1, int z1, int x2, int y2, int z2, Material m) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) set(x, y, z, m);
    }

    /** 부지 고르기: 반지름 r 안을 높이 h 까지 비우고, 바닥(y=-1)을 깔고 아래를 채움 */
    private void site(int r, int h, Material floor) {
        box(-r, 0, -r, r, h, r, Material.AIR);
        box(-r, -1, -r, r, -1, r, floor);
        for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) foundation.add(new int[]{x, z});
    }

    /** 정사각 성벽 한 바퀴 (두께 t, 높이 h, 바닥 y0). 위에 톱니 난간 · 안쪽 등불 */
    private void ring(int R, int t, int y0, int h, Material wall, Material base, Material trim, Material light) {
        box(-R, y0, -R, R, y0 + h, -R + t - 1, wall);
        box(-R, y0, R - t + 1, R, y0 + h, R, wall);
        box(-R, y0, -R, -R + t - 1, y0 + h, R, wall);
        box(R - t + 1, y0, -R, R, y0 + h, R, wall);
        box(-R, y0, -R, R, y0 + 1, -R + t - 1, base);   // 아랫단 (두꺼운 돌)
        box(-R, y0, R - t + 1, R, y0 + 1, R, base);
        box(-R, y0, -R, -R + t - 1, y0 + 1, R, base);
        box(R - t + 1, y0, -R, R, y0 + 1, R, base);
        for (int i = -R; i <= R; i++) {   // 윗단 띠 + 톱니 난간
            int top = y0 + h;
            for (int[] p : new int[][]{{i, -R}, {i, R}, {-R, i}, {R, i}}) set(p[0], top, p[1], trim);
            if (Math.floorMod(i, 2) == 0) for (int[] p : new int[][]{{i, -R}, {i, R}, {-R, i}, {R, i}}) set(p[0], top + 1, p[1], wall);
            if (Math.floorMod(i, 8) == 0 && Math.abs(i) < R - t) {
                set(i, top + 1, -R + t - 1, light);
                set(i, top + 1, R - t + 1, light);
                set(-R + t - 1, top + 1, i, light);
                set(R - t + 1, top + 1, i, light);
            }
        }
    }

    /** 성벽 한 면을 n 구간으로 나눠 공성전 성벽으로 등록할 상자 (탑 자리 margin 제외) */
    private void registerSides(String prefix, int R, int t, int y0, int h, int margin, int n) {
        String[] side = {"north", "south", "west", "east"};
        for (int s = 0; s < 4; s++) {
            int from = -R + margin, to = R - margin, len = to - from + 1;
            for (int k = 0; k < n; k++) {
                int a = from + len * k / n, b = from + len * (k + 1) / n - 1;
                int[][] bx = switch (s) {
                    case 0 -> new int[][]{{a, y0, -R}, {b, y0 + h, -R + t - 1}};
                    case 1 -> new int[][]{{a, y0, R - t + 1}, {b, y0 + h, R}};
                    case 2 -> new int[][]{{-R, y0, a}, {-R + t - 1, y0 + h, b}};
                    default -> new int[][]{{R - t + 1, y0, a}, {R, y0 + h, b}};
                };
                wallBoxes.add(bx);
                wallIds.add(prefix + side[s] + "_" + (k + 1));
            }
        }
    }

    /** 사각 탑 (반지름 r, 높이 h, 바닥 y0): 속이 빈 벽 · 6칸마다 층 · 창 · 뾰족 지붕 */
    private void tower(int cx, int cz, int r, int y0, int h, Material wall, Material floor, Material roof, Material trim, Material light) {
        for (int x = -r; x <= r; x++)
            for (int z = -r; z <= r; z++) {
                boolean edge = Math.abs(x) == r || Math.abs(z) == r;
                if (Math.abs(x) == r && Math.abs(z) == r) continue;   // 모서리를 깎아 둥글게
                for (int y = y0; y <= y0 + h; y++) set(cx + x, y, cz + z, edge ? wall : ((y - y0) % 6 == 0 ? floor : Material.AIR));
            }
        for (int y = y0 + 4; y < y0 + h - 1; y += 6)   // 화살 구멍
            for (int[] p : new int[][]{{0, -r}, {0, r}, {-r, 0}, {r, 0}}) { set(cx + p[0], y, cz + p[1], Material.AIR); set(cx + p[0], y + 1, cz + p[1], Material.AIR); }
        for (int x = -r - 1; x <= r + 1; x++)   // 꼭대기 띠 + 톱니
            for (int z = -r - 1; z <= r + 1; z++) {
                if (Math.abs(x) != r + 1 && Math.abs(z) != r + 1) continue;
                set(cx + x, y0 + h, cz + z, trim);
                if (Math.floorMod(x + z, 2) == 0) set(cx + x, y0 + h + 1, cz + z, wall);
            }
        for (int k = 0; k <= r; k++)   // 뾰족 지붕
            for (int x = -r + k; x <= r - k; x++)
                for (int z = -r + k; z <= r - k; z++) if (Math.abs(x) == r - k || Math.abs(z) == r - k) set(cx + x, y0 + h + 2 + k, cz + z, roof);
        set(cx, y0 + h + 3 + r, cz, light);
        set(cx, y0 + 1, cz, light);
    }

    /** 성문: 남쪽 벽 가운데를 뚫고 양옆에 문탑 */
    private void gate(int R, int t, int y0, int w, int gh, Material wall, Material floor, Material roof, Material trim, Material light) {
        box(-w / 2, y0, R - t, w / 2, y0 + gh, R + 1, Material.AIR);
        box(-w / 2 - 1, y0 + gh + 1, R - t, w / 2 + 1, y0 + gh + 1, R, trim);   // 상인방
        tower(-w / 2 - 4, R - 1, 3, y0, gh + 7, wall, floor, roof, trim, light);
        tower(w / 2 + 4, R - 1, 3, y0, gh + 7, wall, floor, roof, trim, light);
        box(-w / 2, y0 - 1, R - t - 2, w / 2, y0 - 1, R + 6, floor);   // 성문 앞 길
    }

    /** 본성 (속이 빈 큰 건물): 4면 문 · 2층 · 톱니 지붕 · 모서리 작은 탑 */
    private void keep(int cx, int cz, int hw, int y0, int h, Material wall, Material floor, Material roof, Material trim, Material light) {
        for (int x = -hw; x <= hw; x++)
            for (int z = -hw; z <= hw; z++) {
                boolean edge = Math.abs(x) == hw || Math.abs(z) == hw;
                for (int y = y0; y <= y0 + h; y++) set(cx + x, y, cz + z, edge ? wall : (y == y0 + h / 2 || y == y0 + h ? floor : Material.AIR));
            }
        for (int[] d : new int[][]{{0, -hw}, {0, hw}, {-hw, 0}, {hw, 0}})   // 문
            for (int s = -1; s <= 1; s++) for (int y = y0; y <= y0 + 3; y++) set(cx + d[0] + (d[0] == 0 ? s : 0), y, cz + d[1] + (d[1] == 0 ? s : 0), Material.AIR);
        for (int y = y0 + 3; y < y0 + h; y += 4)   // 창
            for (int i = -hw + 3; i <= hw - 3; i += 4) for (int[] p : new int[][]{{i, -hw}, {i, hw}, {-hw, i}, {hw, i}}) set(cx + p[0], y, cz + p[1], Material.GLASS_PANE);
        for (int x = -hw; x <= hw; x++) for (int z = -hw; z <= hw; z++)
            if ((Math.abs(x) == hw || Math.abs(z) == hw) && Math.floorMod(x + z, 2) == 0) set(cx + x, y0 + h + 1, cz + z, trim);
        for (int[] c : new int[][]{{-hw, -hw}, {hw, -hw}, {-hw, hw}, {hw, hw}}) tower(cx + c[0], cz + c[1], 2, y0, h + 6, wall, floor, roof, trim, light);
        box(cx - 1, y0 + h / 2, cz - 1, cx + 1, y0 + h / 2, cz + 1, Material.AIR);   // 윗층으로 올라가는 구멍
        int[][] spiral = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};   // 가운데 기둥을 도는 나선 계단 (한 칸씩 오름)
        set(cx, y0, cz, wall);
        for (int y = y0; y <= y0 + h / 2; y++) {
            int[] st = spiral[(y - y0) % spiral.length];
            set(cx + st[0], y, cz + st[1], floor);
            set(cx, y, cz, wall);
        }
        set(cx, y0 + h + 2, cz, light);
    }

    private void beaconAt(int x, int y, int z, Material pedestal) {
        box(x - 2, y - 1, z - 2, x + 2, y - 1, z + 2, pedestal);
        box(x - 1, y - 1, z - 1, x + 1, y - 1, z + 1, Material.IRON_BLOCK);
        set(x, y, z, Material.BEACON);
        box(x - 1, y + 1, z - 1, x + 1, y + 12, z + 1, Material.AIR);
        beacon = new int[]{x, y, z};
    }

    // ------------------------------------------------------------------ 설계 3종
    private void design1() {   // 왕성
        Material W = Material.STONE_BRICKS, B = Material.DEEPSLATE_BRICKS, T = Material.POLISHED_ANDESITE, F = Material.SPRUCE_PLANKS,
                ROOF = Material.DEEPSLATE_TILES, L = Material.LANTERN;
        int R = 34, t = 3, H = 14;
        site(R + 6, 34, Material.STONE_BRICKS);
        box(-R + t, -1, -R + t, R - t, -1, R - t, Material.GRASS_BLOCK);   // 안뜰 잔디
        box(-2, -1, R - t - 20, 2, -1, R, Material.COBBLESTONE);           // 성문 → 본성 길
        ring(R, t, 0, H, W, B, T, L);
        for (int[] c : new int[][]{{-R, -R}, {R, -R}, {-R, R}, {R, R}}) tower(c[0], c[1], 5, 0, H + 8, W, F, ROOF, T, L);
        for (int[] c : new int[][]{{0, -R}, {-R, 0}, {R, 0}}) tower(c[0], c[1], 3, 0, H + 4, W, F, ROOF, T, L);   // 벽 가운데 망루
        gate(R, t, 0, 7, 8, W, F, ROOF, T, L);
        keep(0, -8, 11, 0, 18, W, F, ROOF, T, L);
        beaconAt(0, 0, 12, T);
        for (int x = -R + 6; x <= R - 6; x += 10) for (int z : new int[]{-R + 6, R - 8}) if (Math.abs(x) > 4) { set(x, 0, z, Material.OAK_FENCE); set(x, 1, z, L); }
        registerSides("", R, t, 0, H, 6, 3);
        attacker = new int[]{0, 0, R + 18};
        defender = new int[]{0, 0, 6};
    }

    private void design2() {   // 흑요 요새 (이중 성벽)
        Material W = Material.POLISHED_BLACKSTONE_BRICKS, B = Material.BLACKSTONE, T = Material.GILDED_BLACKSTONE, F = Material.DARK_OAK_PLANKS,
                ROOF = Material.NETHER_BRICKS, L = Material.SOUL_LANTERN;
        int R = 38, t = 4, H = 12, r2 = 17, H2 = 16;
        site(R + 8, 36, Material.POLISHED_BLACKSTONE);
        box(-R + t, -1, -R + t, R - t, -1, R - t, Material.COARSE_DIRT);
        box(-2, -1, r2, 2, -1, R, Material.POLISHED_BLACKSTONE);
        ring(R, t, 0, H, W, B, T, L);
        for (int[] c : new int[][]{{-R, -R}, {R, -R}, {-R, R}, {R, R}}) {   // 모서리 능보 (큰 네모 보루)
            box(c[0] - 6, 0, c[1] - 6, c[0] + 6, H + 2, c[1] + 6, W);
            box(c[0] - 5, 1, c[1] - 5, c[0] + 5, H + 1, c[1] + 5, Material.AIR);
            box(c[0] - 5, H + 2, c[1] - 5, c[0] + 5, H + 2, c[1] + 5, F);
            tower(c[0], c[1], 3, H + 2, 8, W, F, ROOF, T, L);
        }
        gate(R, t, 0, 7, 8, W, F, ROOF, T, L);
        ring(r2, 3, 0, H2, W, B, T, L);   // 안쪽 성채
        for (int[] c : new int[][]{{-r2, -r2}, {r2, -r2}, {-r2, r2}, {r2, r2}}) tower(c[0], c[1], 4, 0, H2 + 6, W, F, ROOF, T, L);
        box(-2, 0, r2 - 3, 2, 6, r2 + 1, Material.AIR);   // 안쪽 성문
        box(-3, 7, r2 - 3, 3, 7, r2, T);
        beaconAt(0, 0, 0, T);
        registerSides("outer_", R, t, 0, H, 7, 2);
        registerSides("inner_", r2, 3, 0, H2, 5, 1);
        attacker = new int[]{0, 0, R + 20};
        defender = new int[]{0, 0, -8};
    }

    private void design3() {   // 백악 성채 (3단 계단식)
        Material W = Material.QUARTZ_BRICKS, B = Material.POLISHED_DIORITE, T = Material.SMOOTH_QUARTZ, F = Material.BIRCH_PLANKS,
                ROOF = Material.BLUE_TERRACOTTA, L = Material.LANTERN, FILL = Material.CALCITE;
        int R = 40, t = 3, H = 10, r2 = 26, h2 = 6, r3 = 12, h3 = 12;
        site(R + 8, 44, Material.POLISHED_DIORITE);
        box(-R + t, -1, -R + t, R - t, -1, R - t, Material.GRASS_BLOCK);
        ring(R, t, 0, H, W, B, T, L);
        for (int[] c : new int[][]{{-R, -R}, {R, -R}, {-R, R}, {R, R}}) tower(c[0], c[1], 5, 0, H + 8, W, F, ROOF, T, L);
        gate(R, t, 0, 7, 7, W, F, ROOF, T, L);
        box(-r2, 0, -r2, r2, h2 - 1, r2, FILL);   // 가운데 단 (속이 찬 대지)
        box(-r2, h2 - 1, -r2, r2, h2 - 1, r2, Material.GRASS_BLOCK);
        ring(r2, 2, h2, 8, W, B, T, L);
        for (int s = 0; s < h2; s++) box(-3, s, r2 + h2 - s, 3, s, r2 + h2 - s, B);   // 가운데 단으로 오르는 큰 계단 (남쪽)
        box(-3, h2, r2 - 1, 3, h2 + 5, r2, Material.AIR);
        box(-r3, h2, -r3, r3, h2 + h2 - 1, r3, FILL);   // 꼭대기 단
        box(-r3, h2 * 2 - 1, -r3, r3, h2 * 2 - 1, r3, T);
        for (int s = 0; s < h2; s++) box(-2, h2 + s, r3 + h2 - s, 2, h2 + s, r3 + h2 - s, B);
        ring(r3, 2, h2 * 2, h3, W, B, T, L);
        box(-2, h2 * 2, r3 - 1, 2, h2 * 2 + 4, r3, Material.AIR);
        for (int[] c : new int[][]{{-r3, -r3}, {r3, -r3}, {-r3, r3}, {r3, r3}}) tower(c[0], c[1], 3, h2 * 2, h3 + 8, W, F, ROOF, T, L);
        beaconAt(0, h2 * 2, 0, T);
        registerSides("outer_", R, t, 0, H, 6, 2);
        registerSides("middle_", r2, 2, h2, 8, 4, 1);
        attacker = new int[]{0, 0, R + 20};
        defender = new int[]{0, h2 * 2, -6};
    }

    // ------------------------------------------------------------------ 짓기
    /** @return 오류 메시지 (없으면 null) */
    public String start(CommandSender s, int design, String id, Location at) {
        WarManager wm = plugin.wars();
        if (wm.castle(id) != null) return "이미 있는 성 ID 입니다: " + id;
        switch (design) {
            case 1 -> design1();
            case 2 -> design2();
            case 3 -> design3();
            default -> { return "성 종류는 1 (왕성) · 2 (흑요 요새) · 3 (백악 성채) 중 하나입니다."; }
        }
        World w = at.getWorld();
        int ox = at.getBlockX(), oy = at.getBlockY(), oz = at.getBlockZ();
        if (oy + 60 > w.getMaxHeight()) return "너무 높은 곳입니다. 조금 낮은 땅에서 해 주세요.";
        int perTick = Math.max(2000, plugin.getConfig().getInt("war.castle-build-blocks-per-tick", 20000));
        Text.msg(s, "&e" + NAMES[design] + " &f건설을 시작합니다... &7(블록 " + String.format("%,d", ops.size()) + "개, 약 " + (ops.size() / perTick / 20 + 1) + "초)");
        new BukkitRunnable() {
            int i = 0, f = 0;

            @Override
            public void run() {
                int budget = perTick;
                while (f < foundation.size() && budget > 0) {   // 지반: 바닥 아래 빈 곳 채우기 (최대 12칸)
                    int[] c = foundation.get(f++);
                    for (int y = oy - 2; y > oy - 14; y--) {
                        Block b = w.getBlockAt(ox + c[0], y, oz + c[1]);
                        if (b.getType().isSolid()) break;
                        b.setType(Material.STONE, false);
                        budget--;
                    }
                }
                while (i < ops.size() && budget-- > 0) {
                    Op o = ops.get(i++);
                    Block b = w.getBlockAt(ox + o.x, oy + o.y, oz + o.z);
                    if (o.m == Material.AIR && b.getType().isAir()) continue;
                    b.setType(o.m, false);
                }
                if (i < ops.size() || f < foundation.size()) return;
                cancel();
                finish(s, design, id, w, ox, oy, oz);
            }
        }.runTaskTimer(plugin, 1L, 1L);
        return null;
    }

    private void finish(CommandSender s, int design, String id, World w, int ox, int oy, int oz) {
        WarManager wm = plugin.wars();
        Castle c = wm.create(id, NAMES[design] + " " + id);
        double hp = plugin.getConfig().getDouble("war.castle-wall-hp-large", 60000);
        for (int k = 0; k < wallBoxes.size(); k++) {
            int[][] bx = wallBoxes.get(k);
            wm.addWall(c, wallIds.get(k), new Location(w, ox + bx[0][0], oy + bx[0][1], oz + bx[0][2]), new Location(w, ox + bx[1][0], oy + bx[1][1], oz + bx[1][2]), hp);
        }
        c.beacon = new Location(w, ox + beacon[0], oy + beacon[1], oz + beacon[2]);
        c.attackerSpawn = new Location(w, ox + attacker[0] + 0.5, oy + attacker[1], oz + attacker[2] + 0.5, 180, 0);
        Block top = w.getHighestBlockAt(c.attackerSpawn);
        c.attackerSpawn.setY(Math.max(oy, top.getY() + 1));
        c.defenderSpawn = new Location(w, ox + defender[0] + 0.5, oy + defender[1], oz + defender[2] + 0.5);
        wm.save();
        Text.msg(s, "&a" + NAMES[design] + " '" + id + "' 완성! &7성벽 " + wallBoxes.size() + "구간 · 신호기 · 공격/방어 진영 스폰 자동 등록 (신호기 "
                + c.beacon.getBlockX() + ", " + c.beacon.getBlockY() + ", " + c.beacon.getBlockZ() + ")");
        plugin.getLogger().info("공성 성 " + id + " (" + NAMES[design] + ") 건설 완료 @ " + ox + ", " + oy + ", " + oz);
    }
}
