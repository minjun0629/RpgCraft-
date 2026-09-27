package kr.rpgcraft.feature;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.util.Text;
import kr.rpgcraft.war.Castle;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Display;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * 구조물 자동 건설.
 *  - /rpg관리 structure <종류> [ID] : 서 있는 자리에 구조물을 짓는다
 *    castle(성: 성벽 4면 + 신호기, 공성전 자동 등록) · ruin(점프맵 유적, 유적 자동 등록)
 *    temple · tower · camp · altar · gate · mage_tower · mine · stonehenge · totem · crypt · well · graveyard (장식 + 보물 상자)
 *  - 새로 생성되는 청크에 확률적으로 장식 구조물이 자연 생성된다 (config structures.natural-chance)
 * 블록은 물리 연산 없이 배치하며, 보물 상자는 캘 수 없고(야생 방지) 열어서 전리품만 가져갈 수 있다.
 */
public class StructureManager implements Listener {
    public static final List<String> DECOR = List.of("temple", "tower", "camp", "altar", "gate", "mage_tower", "mine", "stonehenge", "totem", "crypt", "well", "graveyard",
            "lighthouse", "witch_hut", "crystal_spire", "arena", "shrine", "watch_fort");
    public static final Map<String, String> NAMES = new LinkedHashMap<>();

    static {
        NAMES.put("castle", "성 (공성전)");
        NAMES.put("ruin", "유적 점프맵");
        NAMES.put("temple", "폐허 신전");
        NAMES.put("tower", "감시탑");
        NAMES.put("camp", "도적 야영지");
        NAMES.put("altar", "고대 제단");
        NAMES.put("gate", "무너진 성문");
        NAMES.put("mage_tower", "마법사의 탑");
        NAMES.put("mine", "버려진 광산 입구");
        NAMES.put("stonehenge", "거석 원형진");
        NAMES.put("totem", "오크 토템 기지");
        NAMES.put("crypt", "지하 묘지 입구");
        NAMES.put("well", "마른 우물");
        NAMES.put("graveyard", "버려진 묘지");
        NAMES.put("lighthouse", "버려진 등대");
        NAMES.put("witch_hut", "마녀의 오두막");
        NAMES.put("crystal_spire", "수정 첨탑");
        NAMES.put("arena", "고대 투기장");
        NAMES.put("shrine", "숲속 사당");
        NAMES.put("watch_fort", "국경 요새 폐허");
    }

    private final RpgCraft plugin;
    private final Random rnd = new Random();

    private final NamespacedKey REQ_ADV;

    public StructureManager(RpgCraft plugin) {
        this.plugin = plugin;
        long every = plugin.getConfig().getLong("structures.random-minutes", 20) * 60 * 20;
        org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, this::randomSpawn, every, every);
        org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, this::autoRuins, 20L * 60, 20L * 60 * plugin.getConfig().getLong("structures.ruin-minutes", 15));
        this.REQ_ADV = new NamespacedKey(plugin, "req_adv");
        loadRecords();
        Bukkit.getScheduler().runTaskTimer(plugin, this::despawnTick, 20L * 60, 20L * 60);
    }

    // ------------------------------------------------------------------ 블록 도구
    private World w;
    private int ox, oy, oz;

    private void origin(Location l) {
        w = l.getWorld();
        ox = l.getBlockX();
        oy = l.getBlockY();
        oz = l.getBlockZ();
    }

    /** 건설 중 바꾼 블록의 원래 모습 (구조물이 사라질 때 복구) */
    private Map<String, String> recording;
    private final List<Location> recordChests = new ArrayList<>();

    private void remember(Block b) {
        if (recording == null) return;
        String k = b.getX() + "," + b.getY() + "," + b.getZ();
        recording.putIfAbsent(k, b.getBlockData().getAsString());
    }

    private void set(int x, int y, int z, Material m) {
        Block b = w.getBlockAt(ox + x, oy + y, oz + z);
        remember(b);
        b.setType(m, false);
    }

    private void set(int x, int y, int z, BlockData d) {
        Block b = w.getBlockAt(ox + x, oy + y, oz + z);
        remember(b);
        b.setBlockData(d, false);
    }

    private void box(int x1, int y1, int z1, int x2, int y2, int z2, Material m) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) set(x, y, z, m);
    }

    /** 폐허 느낌: 일부 블록을 이끼·금 간 벽돌로 바꾸거나 빼먹음 */
    private void ruinBox(int x1, int y1, int z1, int x2, int y2, int z2, double missing) {
        Material[] mats = {Material.STONE_BRICKS, Material.MOSSY_STONE_BRICKS, Material.CRACKED_STONE_BRICKS, Material.COBBLESTONE, Material.MOSSY_COBBLESTONE};
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    if (y > Math.min(y1, y2) && rnd.nextDouble() < missing * (y - Math.min(y1, y2) + 1) / (Math.abs(y2 - y1) + 1)) continue;
                    set(x, y, z, mats[rnd.nextInt(mats.length)]);
                }
    }

    private void clear(int x1, int y1, int z1, int x2, int y2, int z2) {
        box(x1, y1, z1, x2, y2, z2, Material.AIR);
    }

    /** 바닥 고르기: 발밑이 비어 있으면 흙으로 채움 */
    private void foundation(int x1, int z1, int x2, int z2, Material top) {
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                set(x, -1, z, top);
                for (int y = -2; y > -8; y--) {
                    Block b = w.getBlockAt(ox + x, oy + y, oz + z);
                    if (b.getType().isSolid()) break;
                    remember(b);
                    b.setType(Material.DIRT, false);
                }
            }
    }

    private void chest(int x, int y, int z, int tier) {
        if (chestReqAdv > 0) tier = Math.min(tier + 1, chestReqAdv < 40 ? 1 : chestReqAdv < 100 ? 2 : 3); // 먼 곳일수록 좋은 상자
        Block b = w.getBlockAt(ox + x, oy + y, oz + z);
        remember(b);
        b.setType(Material.CHEST, false);
        recordChests.add(b.getLocation());
        if (chestReqAdv > 0 && b.getState() instanceof Chest ch) {
            ch.getPersistentDataContainer().set(REQ_ADV, org.bukkit.persistence.PersistentDataType.INTEGER, chestReqAdv);
            ch.update(true, false);
        }
        // 내용물은 넣지 않고 등급만 기록 → 여는 플레이어마다 개인 전리품 (다른 사람이 먼저 열어도 내 몫은 그대로)
        if (b.getState() instanceof Chest c) {
            c.getPersistentDataContainer().set(LOOT_TIER, org.bukkit.persistence.PersistentDataType.INTEGER, tier);
            c.update(true, false);
        }
    }

    private final NamespacedKey LOOT_TIER = new NamespacedKey("rpgcraft", "loot_tier");
    private final Map<String, org.bukkit.inventory.Inventory> personal = new HashMap<>();

    private static class PersonalChest implements org.bukkit.inventory.InventoryHolder {
        org.bukkit.inventory.Inventory inv;

        @Override
        public org.bukkit.inventory.Inventory getInventory() {
            return inv;
        }
    }

    private void fillLoot(org.bukkit.inventory.Inventory inv, int tier) {
        List<String> pool = new ArrayList<>(List.of("potion_1", "potion_2", "crystal_low", "mat_iron", "mat_silver", "loot_bone", "loot_fang", "herb_ginseng1"));
        if (tier >= 2) pool.addAll(List.of("potion_3", "crystal_mid", "mat_gold", "rune_low", "loot_totem", "ticket_rune"));
        if (tier >= 3) pool.addAll(List.of("crystal_high", "rune_mid", "mat_crystal", "ticket_rate10", "loot_core", "shard_nature", "shard_earth"));
        int n = 3 + rnd.nextInt(3 + tier);
        for (int i = 0; i < n; i++) {
            ItemStack it = plugin.items().create(pool.get(rnd.nextInt(pool.size())), 1 + rnd.nextInt(tier == 1 ? 3 : 2));
            if (it != null) inv.setItem(rnd.nextInt(27), it);
        }
        if (rnd.nextDouble() < plugin.getConfig().getDouble("treasure.map-chest-per-tier", 0.03) * tier) inv.setItem(rnd.nextInt(27), plugin.items().create("treasure_map", 1));
    }

    // ------------------------------------------------------------------ 건설
    public String build(String type, Location at, String id, Player by) {
        return build(type, at, id, by, false);
    }

    /** @param temporary true 면 보물 상자가 열린 뒤 일정 시간이 지나면 구조물이 사라진다 (자연 생성) */
    public String build(String type, Location at, String id, Player by, boolean temporary) {
        origin(at);
        recording = temporary && (DECOR.contains(type) || type.startsWith("arena_")) ? new LinkedHashMap<>() : null;
        recordChests.clear();
        try {
            return doBuild(type, id, by, at);
        } finally {
            if (recording != null && !recording.isEmpty()) saveRecord(type, at);
            recording = null;
        }
    }

    private String doBuild(String type, String id, Player by, Location at) {
        // 장식 구조물 보물 상자: 스폰 거리 + 구조물 난이도로 모험 제한
        int extra = switch (type) { case "mage_tower", "crypt", "altar" -> 15; case "temple", "stonehenge", "totem" -> 10; default -> 0; };
        chestReqAdv = DECOR.contains(type) ? advByDistance(at, extra) : 0;
        switch (type) {
            case "castle" -> { return castle(id == null ? "castle_" + (plugin.wars().castles().size() + 1) : id, by); }
            case "ruin" -> { return ruin(id == null ? "ruin_" + (plugin.ruins().all().size() + 1) : id); }
            case "temple" -> temple();
            case "tower" -> tower();
            case "camp" -> camp();
            case "altar" -> altar();
            case "gate" -> gate();
            case "mage_tower" -> mageTower();
            case "mine" -> mine();
            case "stonehenge" -> stonehenge();
            case "totem" -> totem();
            case "crypt" -> crypt();
            case "well" -> well();
            case "graveyard" -> graveyard();
            case "lighthouse" -> lighthouse();
            case "witch_hut" -> witchHut();
            case "crystal_spire" -> crystalSpire();
            case "arena" -> arena();
            case "shrine" -> shrine();
            case "watch_fort" -> watchFort();
            case "arena_desert" -> arenaDesert();
            case "arena_forest" -> arenaForest();
            case "arena_abyss" -> arenaAbyss();
            case "arena_spirit" -> arenaSpirit();
            default -> { return null; }
        }
        return NAMES.get(type);
    }

    private String castle(String id, Player by) {
        int R = 12, H = 7;
        foundation(-R - 2, -R - 2, R + 2, R + 2, Material.STONE_BRICKS);
        clear(-R, 0, -R, R, H + 3, R);
        box(-R + 1, -1, -R + 1, R - 1, -1, R - 1, Material.POLISHED_ANDESITE);
        // 성벽 4면 (각각 공성전 성벽으로 등록)
        box(-R, 0, -R, R, H, -R, Material.STONE_BRICKS);     // 북
        box(-R, 0, R, R, H, R, Material.STONE_BRICKS);       // 남 (성문)
        box(-R, 0, -R + 1, -R, H, R - 1, Material.STONE_BRICKS); // 서
        box(R, 0, -R + 1, R, H, R - 1, Material.STONE_BRICKS);   // 동
        for (int i = -R; i <= R; i += 2) { set(i, H + 1, -R, Material.STONE_BRICK_WALL); set(i, H + 1, R, Material.STONE_BRICK_WALL); set(-R, H + 1, i, Material.STONE_BRICK_WALL); set(R, H + 1, i, Material.STONE_BRICK_WALL); }
        clear(-1, 0, R, 1, 3, R); // 성문
        box(-2, 4, R, 2, 4, R, Material.CHISELED_STONE_BRICKS);
        // 모서리 탑
        for (int[] c : new int[][]{{-R, -R}, {R, -R}, {-R, R}, {R, R}}) {
            box(c[0] - 1, 0, c[1] - 1, c[0] + 1, H + 3, c[1] + 1, Material.DEEPSLATE_BRICKS);
            set(c[0], H + 4, c[1], Material.LANTERN);
        }
        // 성채 + 신호기
        box(-3, 0, -3, 3, 0, 3, Material.IRON_BLOCK);
        box(-4, 1, -4, 4, 4, -4, Material.DEEPSLATE_BRICKS);
        box(-4, 1, -4, -4, 4, 4, Material.DEEPSLATE_BRICKS);
        box(4, 1, -4, 4, 4, 4, Material.DEEPSLATE_BRICKS);
        set(0, 1, 0, Material.BEACON);
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) set(x, 0, z, Material.IRON_BLOCK);
        set(-3, 1, -3, Material.RED_BANNER);
        set(3, 1, -3, Material.RED_BANNER);
        // 공성전 등록
        var wm = plugin.wars();
        Castle c = wm.castle(id);
        if (c == null) c = wm.create(id, "자동 건설 성 " + id);
        double hp = plugin.getConfig().getDouble("structures.castle-wall-hp", 30000);
        wm.addWall(c, "north", loc(-R + 1, 0, -R), loc(R - 1, H, -R), hp);
        wm.addWall(c, "south", loc(-R + 1, 0, R), loc(R - 1, H, R), hp);
        wm.addWall(c, "west", loc(-R, 0, -R + 1), loc(-R, H, R - 1), hp);
        wm.addWall(c, "east", loc(R, 0, -R + 1), loc(R, H, R - 1), hp);
        c.beacon = loc(0, 1, 0);
        c.defenderSpawn = loc(0, 1, 6).add(0.5, 0, 0.5);
        c.attackerSpawn = loc(0, 0, R + 14).add(0.5, 0, 0.5);
        wm.save();
        return "성 '" + id + "' (성벽 4 · 신호기 · 진영 스폰 자동 등록)";
    }

    private Location loc(int x, int y, int z) {
        return new Location(w, ox + x, oy + y, oz + z);
    }

    /** 이번에 짓는 구조물 보물 상자의 모험 스탯 제한 */
    private int chestReqAdv;

    /** 스폰에서 멀수록 높은 모험 제한 (5 단위) */
    private int advByDistance(Location at, int extra) {
        double dist = at.distance(at.getWorld().getSpawnLocation());
        int base = plugin.getConfig().getInt("structures.ruin-min-adv", 10);
        int v = (int) (Math.round(dist / plugin.getConfig().getDouble("structures.adv-per-blocks", 25) / 5.0) * 5) + extra;
        return Math.max(base, Math.min(plugin.getConfig().getInt("structures.max-adv", 250), v));
    }

    /**
     * 점프맵 유적: 누구나 평범한 점프로 오를 수 있는 코스만 만든다.
     *  - 발판은 전부 온 블록(반 블록 없음), 한 칸 올라갈 땐 가로 2칸 이내, 같은 높이는 3칸 이내
     *  - 구간: 징검다리 → 사다리 벽 오르기 → 좁은 다리 → 계단, 12발판마다 쉼터(체크포인트)
     *  - 이동 경로 위 3칸은 항상 비워 머리가 걸리지 않게
     */
    private String ruin(String id) {
        int adv = advByDistance(loc(0, 0, 0), 0);
        chestReqAdv = adv;
        ruinBox(-3, 0, -3, 3, 3, -3, 0.4);
        ruinBox(-3, 0, -3, -3, 3, 3, 0.4);
        box(-2, -1, -2, 2, -1, 2, Material.CHISELED_STONE_BRICKS);
        clear(-2, 0, -2, 2, 4, 2);
        set(0, 0, 0, Material.GOLD_BLOCK); // 시작 발판
        int steps = plugin.getConfig().getInt("structures.ruin-steps", 48);
        int[] cur = {0, 0, 0};
        Material[] mats = {Material.STONE_BRICKS, Material.MOSSY_STONE_BRICKS, Material.CRACKED_STONE_BRICKS, Material.CHISELED_STONE_BRICKS};
        int[][] flat = {{0, 3}, {1, 2}, {-1, 2}, {2, 2}, {-2, 2}};
        int[][] up = {{0, 2}, {1, 2}, {-1, 2}, {1, 1}, {-1, 1}};
        int placed = 0;
        while (placed < steps) {
            int section = (placed / 6) % 4;
            if (placed > 0 && placed % 12 == 0) {                  // 쉼터
                step(cur, 0, 3, 0, Material.POLISHED_ANDESITE);
                box(cur[0] - 1, cur[1], cur[2] - 1, cur[0] + 1, cur[1], cur[2] + 1, Material.POLISHED_ANDESITE);
                set(cur[0] + 1, cur[1] + 1, cur[2] + 1, Material.LANTERN);
                placed++;
                continue;
            }
            if (section == 1 && placed % 6 == 0) {                 // 사다리 벽 오르기 (5칸)
                int h = 5;
                int zx = cur[0], zz = cur[2] + 2;
                clear(zx - 1, cur[1] + 1, cur[2], zx + 1, cur[1] + h + 3, zz + 1);
                box(zx, cur[1], zz, zx, cur[1] + h, zz, Material.STONE_BRICKS);
                box(zx, cur[1], zz - 1, zx, cur[1], zz - 1, Material.STONE_BRICKS);   // 사다리 앞 발판
                for (int yy = cur[1] + 1; yy <= cur[1] + h; yy++) {
                    Block lb = w.getBlockAt(ox + zx, oy + yy, oz + zz - 1);
                    remember(lb);
                    lb.setType(Material.LADDER, false);
                    if (lb.getBlockData() instanceof Directional dl) { dl.setFacing(BlockFace.NORTH); lb.setBlockData(dl, false); }
                }
                cur[1] += h;
                cur[2] = zz;
                placed += 2;
                continue;
            }
            int[] mv;
            int dy;
            if (section == 2) { mv = new int[]{0, 1}; dy = 0; }       // 좁은 다리 (한 줄로 이어진 블록)
            else if (section == 3) { mv = new int[]{0, 1}; dy = 1; }  // 계단
            else if (rnd.nextInt(3) > 0) { mv = up[rnd.nextInt(up.length)]; dy = 1; }
            else { mv = flat[rnd.nextInt(flat.length)]; dy = 0; }
            step(cur, mv[0], mv[1], dy, mats[rnd.nextInt(mats.length)]);
            if (section == 0 && placed % 4 == 0) for (int yy = cur[1] - 1; yy > cur[1] - 8; yy--) set(cur[0], yy, cur[2], Material.STONE_BRICK_WALL);
            placed++;
        }
        int x = cur[0], y = cur[1], z = cur[2] + 3;
        clear(x - 3, y + 1, cur[2], x + 3, y + 5, z + 3);
        box(x - 2, y, cur[2] + 1, x + 2, y, z + 2, Material.CHISELED_STONE_BRICKS);
        set(x, y + 1, z, Material.DIAMOND_BLOCK); // 도착 발판
        for (int[] c : new int[][]{{-2, -1}, {2, -1}, {-2, 2}, {2, 2}}) box(x + c[0], y + 1, z + c[1], x + c[0], y + 3, z + c[1], Material.MOSSY_STONE_BRICKS);
        chest(x + 1, y + 1, z + 1, adv >= 100 ? 3 : 2);
        var rm = plugin.ruins();
        var r = rm.get(id);
        if (r == null) r = rm.create(id, "고대 유적 " + id.replaceAll("\\D", ""));
        rm.setPoint(r, loc(0, 0, 0), true);
        rm.setPoint(r, loc(x, y + 1, z), false);
        r.minAdv = adv;
        r.money = 50_000L + steps * 4_000L + adv * 800L;
        rm.save();
        String rn = r.name;
        TextDisplay label = w.spawn(loc(0, 3, 0).add(0.5, 0, 0.5), TextDisplay.class, t -> {
            t.setText(Text.c("&6&l" + rn + "\n&7필요 모험 &e" + adv + "\n&8금 블록을 밟으면 시작"));
            t.setBillboard(Display.Billboard.CENTER);
        });
        return "유적 '" + id + "' (" + steps + "발판, 필요 모험 " + adv + ", 금 블록 = 시작, 다이아 블록 = 도착)";
    }

    /** 발판 하나: 이전 발판과 새 발판 사이 머리 공간을 비우고 온 블록을 놓는다 */
    private void step(int[] cur, int dx, int dz, int dy, Material m) {
        int nx = cur[0] + dx, ny = cur[1] + dy, nz = cur[2] + dz;
        clear(Math.min(cur[0], nx) - 1, Math.min(cur[1], ny) + 1, Math.min(cur[2], nz), Math.max(cur[0], nx) + 1, Math.max(cur[1], ny) + 4, Math.max(cur[2], nz));
        set(nx, ny, nz, m);
        cur[0] = nx;
        cur[1] = ny;
        cur[2] = nz;
    }

    // ------------------------------------------------------------------ 월드보스 전장 (거대 건축물)
    private BlockData leaves(Material m) {
        BlockData d = m.createBlockData();
        if (d instanceof org.bukkit.block.data.type.Leaves l) l.setPersistent(true);
        return d;
    }

    /** 사막의 악몽: 계단식 사암 성벽의 원형 신전 · 오벨리스크 8개 · 태양 제단 · 사방 성문 · 화톳불 */
    private void arenaDesert() {
        int R = 18;
        for (int x = -R - 1; x <= R + 1; x++)
            for (int z = -R - 1; z <= R + 1; z++) {
                double r = Math.hypot(x, z);
                if (r > R + 1.2) continue;
                set(x, -1, z, r < 3 ? Material.GOLD_BLOCK : ((int) r) % 5 == 0 ? Material.CUT_SANDSTONE : Material.SMOOTH_SANDSTONE);
                set(x, -2, z, Material.SANDSTONE);
                for (int y = 0; y <= 16; y++) set(x, y, z, Material.AIR);
                if (r > R - 2.5) {                                                     // 계단식 성벽
                    int hgt = 3 + (int) ((r - (R - 2.5)) * 2);
                    for (int y = 0; y < hgt; y++) set(x, y, z, y % 3 == 2 ? Material.CHISELED_SANDSTONE : Material.SANDSTONE);
                    set(x, hgt, z, Material.SMOOTH_SANDSTONE_SLAB);
                }
            }
        for (int[] g : new int[][]{{0, 1}, {0, -1}, {1, 0}, {-1, 0}})                      // 사방 성문
            for (int k = R - 4; k <= R + 2; k++)
                for (int t = -2; t <= 2; t++)
                    for (int y = 0; y <= 5; y++) set(g[0] * k + g[1] * t, y, g[1] * k + g[0] * t, Material.AIR);
        for (int i = 0; i < 8; i++) {                                                    // 오벨리스크
            double a = i * Math.PI / 4;
            int x = (int) Math.round(Math.cos(a) * 12), z = (int) Math.round(Math.sin(a) * 12);
            box(x, 0, z, x, 8, z, Material.CUT_SANDSTONE);
            set(x, 4, z, Material.CHISELED_SANDSTONE);
            set(x, 9, z, Material.GOLD_BLOCK);
            set(x, 10, z, Material.GLOWSTONE);
        }
        box(-3, 0, -R + 4, 3, 0, -R + 6, Material.SMOOTH_SANDSTONE);                  // 태양 제단 (북쪽)
        box(-2, 1, -R + 4, 2, 1, -R + 5, Material.SMOOTH_SANDSTONE);
        box(-1, 2, -R + 4, 1, 5, -R + 4, Material.GOLD_BLOCK);
        set(0, 6, -R + 4, Material.SHROOMLIGHT);
        for (int[] c : new int[][]{{-3, R - 4}, {3, R - 4}, {-3, -R + 8}, {3, -R + 8}}) {  // 화톳불
            box(c[0], 0, c[1], c[0], 2, c[1], Material.CUT_SANDSTONE);
            set(c[0], 3, c[1], Material.CAMPFIRE);
        }
    }

    /** 시포니아: 이끼 바닥의 원형 성역 · 빛나는 룬 고리 · 수정 기둥 12개 · 잎 지붕 고리 */
    private void arenaForest() {
        int R = 17;
        for (int x = -R - 1; x <= R + 1; x++)
            for (int z = -R - 1; z <= R + 1; z++) {
                double r = Math.hypot(x, z);
                if (r > R + 1.2) continue;
                boolean ring = Math.abs(r - 10) < 0.6 || Math.abs(r - 5) < 0.5;
                set(x, -1, z, ring ? Material.DARK_PRISMARINE : r < 1.5 ? Material.SEA_LANTERN : Material.MOSS_BLOCK);
                for (int y = 0; y <= 15; y++) set(x, y, z, Material.AIR);
                if (Math.abs(r - (R - 0.5)) < 0.8) { set(x, 0, z, Material.MOSSY_STONE_BRICKS); set(x, 1, z, Material.MOSSY_STONE_BRICK_WALL); }
            }
        for (int i = 0; i < 12; i++) {                                                   // 룬 등불 + 수정 기둥
            double a = i * Math.PI / 6;
            int lx = (int) Math.round(Math.cos(a) * 10), lz = (int) Math.round(Math.sin(a) * 10);
            set(lx, -1, lz, Material.SEA_LANTERN);
            int x = (int) Math.round(Math.cos(a) * 14), z = (int) Math.round(Math.sin(a) * 14);
            int hgt = 7 + (i % 3) * 2;
            box(x, 0, z, x, hgt, z, i % 2 == 0 ? Material.PRISMARINE_BRICKS : Material.DARK_PRISMARINE);
            set(x, hgt + 1, z, Material.AMETHYST_BLOCK);
            set(x, hgt + 2, z, Material.AMETHYST_CLUSTER);
            set(x, hgt - 2, z, Material.SEA_LANTERN);
        }
        for (int x = -R; x <= R; x++)                                                    // 잎 지붕 고리
            for (int z = -R; z <= R; z++) {
                double r = Math.hypot(x, z);
                if (r > 12.5 && r < 16.5 && rnd.nextDouble() < 0.8) set(x, 12 + rnd.nextInt(2), z, leaves(rnd.nextBoolean() ? Material.FLOWERING_AZALEA_LEAVES : Material.AZALEA_LEAVES));
            }
        box(-1, 0, -1, 1, 0, 1, Material.CHISELED_STONE_BRICKS);                        // 중앙 제단
    }

    /** 카인: 흑요석 요새 · 흉벽 성벽 · 모서리 탑 4개(영혼 등불) · 붉은 길 · 왕좌 · 쇠사슬 · 영혼 화로 */
    private void arenaAbyss() {
        int R = 18, H = 8;
        for (int x = -R - 2; x <= R + 2; x++)
            for (int z = -R - 2; z <= R + 2; z++) {
                set(x, -1, z, (x == 0 || z == 0) ? Material.RED_NETHER_BRICKS : (Math.abs(x) + Math.abs(z)) % 6 == 0 ? Material.MAGMA_BLOCK : Material.POLISHED_BLACKSTONE_BRICKS);
                for (int y = 0; y <= H + 6; y++) set(x, y, z, Material.AIR);
                boolean wall = Math.abs(x) >= R || Math.abs(z) >= R;
                if (wall) {
                    for (int y = 0; y < H; y++) set(x, y, z, y % 4 == 3 ? Material.GILDED_BLACKSTONE : Material.BLACKSTONE);
                    if ((x + z) % 2 == 0) set(x, H, z, Material.POLISHED_BLACKSTONE_BRICK_WALL);
                }
            }
        for (int k = -2; k <= 2; k++) for (int y = 0; y <= 5; y++) set(k, y, R, Material.AIR);           // 남쪽 성문
        for (int[] c : new int[][]{{-R, -R}, {R, -R}, {-R, R}, {R, R}}) {                                // 모서리 탑
            box(c[0] - 2, 0, c[1] - 2, c[0] + 2, H + 5, c[1] + 2, Material.POLISHED_BLACKSTONE_BRICKS);
            box(c[0] - 1, H + 1, c[1] - 1, c[0] + 1, H + 5, c[1] + 1, Material.AIR);
            set(c[0], H + 1, c[1], Material.SOUL_CAMPFIRE);
            for (int[] d : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) set(c[0] + d[0], H + 6, c[1] + d[1], Material.SOUL_LANTERN);
        }
        for (int z = -R + 5; z <= R - 1; z++) set(0, 0, z, Material.RED_CARPET);                         // 붉은 길
        box(-3, 0, -R + 1, 3, 0, -R + 4, Material.POLISHED_BLACKSTONE);                                   // 왕좌
        box(-2, 1, -R + 1, 2, 1, -R + 3, Material.POLISHED_BLACKSTONE);
        box(-1, 2, -R + 1, 1, 6, -R + 1, Material.CRYING_OBSIDIAN);
        set(0, 2, -R + 2, Material.POLISHED_BLACKSTONE_STAIRS);
        set(-2, 2, -R + 2, Material.SOUL_LANTERN);
        set(2, 2, -R + 2, Material.SOUL_LANTERN);
        for (int[] c : new int[][]{{-9, -9}, {9, -9}, {-9, 9}, {9, 9}}) {                                // 쇠사슬 + 영혼 화로
            box(c[0], 0, c[1], c[0], 1, c[1], Material.POLISHED_BLACKSTONE_BRICKS);
            set(c[0], 2, c[1], Material.SOUL_CAMPFIRE);
            for (int y = 6; y <= H + 4; y++) set(c[0], y, c[1], Material.IRON_BARS);
        }
    }

    /** 몬스터의 원혼: 저주받은 묘역 · 영혼의 모래 바닥 · 비석 · 말라 죽은 나무 · 영혼 화톳불 기둥 · 무너진 영묘 */
    private void arenaSpirit() {
        int R = 17;
        for (int x = -R - 1; x <= R + 1; x++)
            for (int z = -R - 1; z <= R + 1; z++) {
                double r = Math.hypot(x, z);
                if (r > R + 1.2) continue;
                boolean ring = Math.abs(r - 7) < 0.6 || Math.abs(r - 13) < 0.5;
                set(x, -1, z, ring ? Material.SOUL_SOIL : r < 2.5 ? Material.CRYING_OBSIDIAN : rnd.nextDouble() < 0.3 ? Material.SOUL_SAND : Material.PODZOL);
                for (int y = 0; y <= 14; y++) set(x, y, z, Material.AIR);
                if (Math.abs(r - (R - 0.5)) < 0.8) {                                     // 무너진 묘역 담
                    set(x, 0, z, Material.DEEPSLATE_BRICKS);
                    if (rnd.nextDouble() < 0.6) set(x, 1, z, Material.DEEPSLATE_BRICK_WALL);
                }
            }
        for (int i = 0; i < 16; i++) {                                                   // 비석
            double a = i * Math.PI / 8 + 0.2;
            int x = (int) Math.round(Math.cos(a) * 10), z = (int) Math.round(Math.sin(a) * 10);
            set(x, 0, z, Material.COBBLED_DEEPSLATE);
            set(x, 1, z, i % 3 == 0 ? Material.CHISELED_DEEPSLATE : Material.POLISHED_DEEPSLATE);
            if (i % 4 == 0) set(x, 2, z, Material.SOUL_LANTERN);
        }
        for (int i = 0; i < 6; i++) {                                                    // 영혼 화톳불 기둥
            double a = i * Math.PI / 3;
            int x = (int) Math.round(Math.cos(a) * 14), z = (int) Math.round(Math.sin(a) * 14);
            box(x, 0, z, x, 5, z, Material.POLISHED_BLACKSTONE_BRICKS);
            set(x, 6, z, Material.SOUL_CAMPFIRE);
        }
        for (int[] t : new int[][]{{-6, 11}, {7, -11}, {12, 6}, {-12, -5}}) {           // 말라 죽은 나무
            box(t[0], 0, t[1], t[0], 5, t[1], Material.STRIPPED_DARK_OAK_LOG);
            set(t[0] + 1, 4, t[1], Material.STRIPPED_DARK_OAK_LOG);
            set(t[0] - 1, 5, t[1], Material.STRIPPED_DARK_OAK_LOG);
            set(t[0], 6, t[1] + 1, Material.STRIPPED_DARK_OAK_LOG);
            set(t[0] + 1, 3, t[1], Material.COBWEB);
        }
        box(-3, 0, -R + 2, 3, 0, -R + 5, Material.DEEPSLATE_TILES);                       // 무너진 영묘
        box(-3, 1, -R + 2, -3, 5, -R + 2, Material.DEEPSLATE_BRICKS);
        box(3, 1, -R + 2, 3, 4, -R + 2, Material.CRACKED_DEEPSLATE_BRICKS);
        box(-3, 5, -R + 2, 1, 5, -R + 2, Material.DEEPSLATE_BRICK_SLAB);
        set(0, 1, -R + 3, Material.SOUL_LANTERN);
        for (int k = 0; k < 10; k++) set(rnd.nextInt(2 * R) - R, 0, rnd.nextInt(2 * R) - R, Material.COBWEB);
    }

    // ------------------------------------------------------------------ 추가 구조물
    /** 버려진 등대: 원통 탑 + 꼭대기 등불 + 나선 계단 대신 사다리 */
    private void lighthouse() {
        int H = 16;
        for (int y = -1; y <= H; y++)
            for (int x = -3; x <= 3; x++)
                for (int z = -3; z <= 3; z++) {
                    double r = Math.hypot(x, z);
                    if (r > 3.2) continue;
                    if (y == -1) set(x, y, z, Material.STONE_BRICKS);
                    else if (r > 2.2) set(x, y, z, (y / 3) % 2 == 0 ? Material.WHITE_TERRACOTTA : Material.RED_TERRACOTTA);
                    else set(x, y, z, Material.AIR);
                }
        for (int y = 0; y < H; y++) {
            Block lb = w.getBlockAt(ox, oy + y, oz - 2);
            remember(lb);
            lb.setType(Material.LADDER, false);
            if (lb.getBlockData() instanceof Directional dl) { dl.setFacing(BlockFace.SOUTH); lb.setBlockData(dl, false); }
        }
        box(-2, H, -2, 2, H, 2, Material.SMOOTH_STONE);
        set(0, H, -2, Material.AIR);
        box(-1, H + 1, -1, 1, H + 3, 1, Material.GLASS);
        set(0, H + 2, 0, Material.GLOWSTONE);
        box(-2, H + 4, -2, 2, H + 4, 2, Material.DARK_OAK_SLAB);
        clear(-1, 0, 2, 1, 2, 3);
        chest(1, H + 1, 2, 2);
    }

    /** 마녀의 오두막: 기둥 위 오두막 + 가마솥 */
    private void witchHut() {
        for (int[] c : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) box(c[0], -1, c[1], c[0], 2, c[1], Material.SPRUCE_LOG);
        box(-3, 3, -3, 3, 3, 3, Material.SPRUCE_PLANKS);
        box(-3, 4, -3, 3, 6, 3, Material.DARK_OAK_PLANKS);
        clear(-2, 4, -2, 2, 6, 2);
        clear(0, 4, 3, 0, 5, 3);
        box(-3, 7, -3, 3, 7, 3, Material.DARK_OAK_STAIRS);
        box(-2, 8, -2, 2, 8, 2, Material.DARK_OAK_SLAB);
        set(-1, 4, -1, Material.CAULDRON);
        set(1, 4, -2, Material.BREWING_STAND);
        set(-2, 4, 1, Material.POTTED_RED_MUSHROOM);
        for (int y = 0; y <= 3; y++) {
            Block lb = w.getBlockAt(ox, oy + y, oz + 4);
            remember(lb);
            lb.setType(Material.LADDER, false);
            if (lb.getBlockData() instanceof Directional dl) { dl.setFacing(BlockFace.SOUTH); lb.setBlockData(dl, false); }
        }
        chest(2, 4, 1, 2);
    }

    /** 수정 첨탑: 자수정 기둥이 솟은 바위 언덕 */
    private void crystalSpire() {
        for (int x = -5; x <= 5; x++)
            for (int z = -5; z <= 5; z++) {
                double r = Math.hypot(x, z);
                if (r > 5.2) continue;
                int h = (int) Math.max(0, 2 - r * 0.4);
                box(x, -1, z, x, h, z, rnd.nextBoolean() ? Material.CALCITE : Material.SMOOTH_BASALT);
            }
        int[][] sp = {{0, 0, 11}, {2, 1, 7}, {-2, 2, 6}, {1, -3, 5}, {-3, -1, 4}};
        for (int[] c : sp) {
            box(c[0], 1, c[1], c[0], c[2], c[1], Material.AMETHYST_BLOCK);
            set(c[0], c[2] + 1, c[1], Material.AMETHYST_CLUSTER);
        }
        box(-1, 3, -1, 1, 5, 1, Material.BUDDING_AMETHYST);
        chest(3, 2, -2, 3);
    }

    /** 고대 투기장: 원형 모래 바닥 + 계단식 관중석 */
    private void arena() {
        int R = 9;
        for (int x = -R - 3; x <= R + 3; x++)
            for (int z = -R - 3; z <= R + 3; z++) {
                double r = Math.hypot(x, z);
                if (r > R + 3.2) continue;
                if (r <= R) { set(x, -1, z, Material.SAND); clear(x, 0, z, x, 5, z); }
                else {
                    int tier = (int) (r - R);
                    box(x, -1, z, x, tier, z, Material.SMOOTH_SANDSTONE);
                    if (rnd.nextDouble() < 0.15) set(x, tier, z, Material.AIR);
                }
            }
        for (int a = 0; a < 360; a += 45) {
            int x = (int) Math.round(Math.cos(Math.toRadians(a)) * (R + 3)), z = (int) Math.round(Math.sin(Math.toRadians(a)) * (R + 3));
            box(x, 0, z, x, 5 + rnd.nextInt(3), z, Material.CUT_SANDSTONE);
        }
        clear(-1, 0, R, 1, 3, R + 4);
        chest(0, 0, 0, 2);
    }

    /** 숲속 사당: 작은 제단 + 등불 + 이끼 */
    private void shrine() {
        box(-3, -1, -3, 3, -1, 3, Material.MOSSY_COBBLESTONE);
        box(-2, 0, -2, 2, 0, 2, Material.STONE_BRICK_SLAB);
        for (int[] c : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
            box(c[0], 0, c[1], c[0], 3, c[1], Material.STRIPPED_OAK_LOG);
            set(c[0], 4, c[1], Material.LANTERN);
        }
        box(-2, 4, -2, 2, 4, 2, Material.SPRUCE_SLAB);
        set(-2, 4, -2, Material.LANTERN);
        set(0, 1, 0, Material.CHISELED_STONE_BRICKS);
        set(0, 2, 0, Material.CANDLE);
        chest(1, 1, -1, 1);
    }

    /** 국경 요새 폐허: 무너진 사각 성벽 + 망루 */
    private void watchFort() {
        ruinBox(-7, 0, -7, 7, 4, -7, 0.3);
        ruinBox(-7, 0, 7, 7, 4, 7, 0.3);
        ruinBox(-7, 0, -7, -7, 4, 7, 0.3);
        ruinBox(7, 0, -7, 7, 4, 7, 0.35);
        box(-6, -1, -6, 6, -1, 6, Material.COBBLESTONE);
        for (int[] c : new int[][]{{-7, -7}, {7, 7}}) {
            box(c[0] - 1, 0, c[1] - 1, c[0] + 1, 8, c[1] + 1, Material.STONE_BRICKS);
            clear(c[0], 1, c[1], c[0], 8, c[1]);
            box(c[0] - 1, 9, c[1] - 1, c[0] + 1, 9, c[1] + 1, Material.STONE_BRICK_SLAB);
        }
        clear(-1, 0, 7, 1, 3, 7);
        set(0, 0, 0, Material.CAMPFIRE);
        chest(-4, 0, 3, 2);
        chest(4, 0, -3, 1);
    }

    private void temple() {
        foundation(-6, -6, 6, 6, Material.STONE_BRICKS);
        for (int[] c : new int[][]{{-5, -5}, {5, -5}, {-5, 5}, {5, 5}, {-5, 0}, {5, 0}}) ruinBox(c[0], 0, c[1], c[0], 5, c[1], 0.35);
        ruinBox(-5, 6, -5, 5, 6, -5, 0.5);
        box(-2, 0, -2, 2, 0, 2, Material.CHISELED_STONE_BRICKS);
        set(0, 1, 0, Material.LODESTONE);
        chest(0, 1, 2, 2);
    }

    private void tower() {
        foundation(-3, -3, 3, 3, Material.COBBLESTONE);
        for (int y = 0; y < 12; y++)
            for (int x = -2; x <= 2; x++)
                for (int z = -2; z <= 2; z++)
                    if (Math.abs(x) == 2 || Math.abs(z) == 2) set(x, y, z, y % 4 == 3 ? Material.SPRUCE_PLANKS : Material.COBBLESTONE);
        clear(0, 0, 2, 0, 1, 2);
        for (int y = 0; y < 11; y++) set(0, y, 0, Material.LADDER);
        box(-3, 12, -3, 3, 12, 3, Material.SPRUCE_PLANKS);
        for (int x = -3; x <= 3; x += 2) { set(x, 13, -3, Material.SPRUCE_FENCE); set(x, 13, 3, Material.SPRUCE_FENCE); set(-3, 13, x, Material.SPRUCE_FENCE); set(3, 13, x, Material.SPRUCE_FENCE); }
        set(1, 13, 1, Material.CAMPFIRE);
        chest(-1, 13, -1, 2);
    }

    private void camp() {
        foundation(-6, -6, 6, 6, Material.COARSE_DIRT);
        set(0, 0, 0, Material.CAMPFIRE);
        for (int[] t : new int[][]{{-4, -3}, {4, -3}, {0, 4}}) {
            for (int i = 0; i < 3; i++) box(t[0] - 1 + i, i, t[1] - 1, t[0] + 1 - i, i, t[1] + 1, Material.BROWN_WOOL);
            set(t[0], 0, t[1], Material.AIR);
        }
        set(2, 0, 2, Material.BARREL);
        set(-2, 0, 2, Material.CRAFTING_TABLE);
        set(3, 0, -1, Material.OAK_LOG);
        chest(-3, 0, 1, 1);
    }

    private void altar() {
        foundation(-4, -4, 4, 4, Material.POLISHED_BLACKSTONE_BRICKS);
        box(-3, 0, -3, 3, 0, 3, Material.POLISHED_BLACKSTONE);
        box(-1, 1, -1, 1, 1, 1, Material.CHISELED_POLISHED_BLACKSTONE);
        set(0, 2, 0, Material.ENCHANTING_TABLE);
        for (int[] c : new int[][]{{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) { box(c[0], 1, c[1], c[0], 3, c[1], Material.POLISHED_BLACKSTONE_WALL); set(c[0], 4, c[1], Material.SOUL_LANTERN); }
        chest(0, 1, 2, 3);
    }

    private void gate() {
        foundation(-7, -2, 7, 2, Material.STONE_BRICKS);
        ruinBox(-7, 0, 0, -3, 7, 0, 0.3);
        ruinBox(3, 0, 0, 7, 7, 0, 0.3);
        ruinBox(-2, 6, 0, 2, 7, 0, 0.5);
        set(0, 5, 0, Material.CHISELED_STONE_BRICKS);
        box(-2, 0, 0, 2, 0, 0, Material.GRAVEL);
        chest(-5, 0, 1, 1);
    }

    private void mageTower() {
        foundation(-4, -4, 4, 4, Material.PURPUR_BLOCK);
        for (int y = 0; y < 16; y++) {
            double r = 3.5 - y * 0.08;
            for (int x = -4; x <= 4; x++)
                for (int z = -4; z <= 4; z++) {
                    double d = Math.sqrt(x * x + z * z);
                    if (d <= r && d > r - 1.1) set(x, y, z, y % 5 == 4 ? Material.AMETHYST_BLOCK : Material.PURPUR_BLOCK);
                }
        }
        clear(0, 0, 3, 0, 1, 3);
        for (int y = 16; y < 20; y++) set(0, y, 0, y == 19 ? Material.END_ROD : Material.PURPUR_PILLAR);
        box(-2, 8, -2, 2, 8, 2, Material.PURPUR_SLAB);
        set(0, 9, 0, Material.ENCHANTING_TABLE);
        chest(1, 9, 1, 3);
        chest(0, 0, 0, 2);
    }

    private void mine() {
        foundation(-3, -2, 3, 6, Material.GRAVEL);
        for (int z = 0; z < 6; z++) {
            box(-2, 0, z, -2, 3, z, Material.OAK_LOG);
            box(2, 0, z, 2, 3, z, Material.OAK_LOG);
            box(-2, 4, z, 2, 4, z, Material.OAK_PLANKS);
            clear(-1, 0, z, 1, 3, z);
            if (z % 2 == 0) set(0, 3, z, Material.LANTERN);
        }
        box(-1, -1, 0, 1, -1, 5, Material.RAIL);
        box(-1, -1, 0, 1, -1, 5, Material.GRAVEL);
        set(0, 0, 1, Material.RAIL);
        set(1, 0, 4, Material.IRON_ORE);
        chest(-1, 0, 5, 2);
    }

    private void stonehenge() {
        foundation(-8, -8, 8, 8, Material.GRASS_BLOCK);
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI * 2 / 10;
            int x = (int) Math.round(Math.cos(a) * 7), z = (int) Math.round(Math.sin(a) * 7);
            int h = 3 + rnd.nextInt(3);
            box(x, 0, z, x, h, z, rnd.nextBoolean() ? Material.ANDESITE : Material.MOSSY_COBBLESTONE);
        }
        box(-1, 0, -1, 1, 0, 1, Material.MOSSY_COBBLESTONE);
        chest(0, 1, 0, 2);
    }

    private void totem() {
        foundation(-6, -6, 6, 6, Material.PACKED_MUD);
        box(0, 0, 0, 0, 7, 0, Material.STRIPPED_DARK_OAK_LOG);
        set(0, 8, 0, Material.CARVED_PUMPKIN);
        box(-1, 5, 0, 1, 5, 0, Material.DARK_OAK_FENCE);
        for (int[] c : new int[][]{{-5, -5}, {5, -5}, {-5, 5}, {5, 5}}) { box(c[0], 0, c[1], c[0], 3, c[1], Material.DARK_OAK_FENCE); set(c[0], 4, c[1], Material.SKELETON_SKULL); }
        set(2, 0, 2, Material.CAMPFIRE);
        chest(-2, 0, 2, 2);
    }

    private void crypt() {
        foundation(-4, -4, 4, 4, Material.DEEPSLATE_TILES);
        ruinBox(-4, 0, -4, 4, 4, -4, 0.2);
        ruinBox(-4, 0, -4, -4, 4, 4, 0.2);
        ruinBox(4, 0, -4, 4, 4, 4, 0.2);
        box(-4, 5, -4, 4, 5, 4, Material.DEEPSLATE_TILES);
        clear(-3, 0, -3, 3, 4, 4);
        for (int y = -1; y > -6; y--) clear(0, y, 1 + (-y), 0, y + 2, 1 + (-y));
        clear(-2, -6, 6, 2, -3, 10);
        box(-3, -7, 5, 3, -7, 11, Material.DEEPSLATE_TILES);
        set(0, -6, 8, Material.SOUL_LANTERN);
        chest(0, -6, 10, 3);
    }

    private void well() {
        foundation(-2, -2, 2, 2, Material.COBBLESTONE);
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) if (Math.abs(x) == 2 || Math.abs(z) == 2) set(x, 0, z, Material.MOSSY_COBBLESTONE);
        clear(-1, -6, -1, 1, 0, 1);
        box(-1, -7, -1, 1, -7, 1, Material.COBBLESTONE);
        for (int y = -6; y <= 0; y++) {   // 빠져도 올라올 수 있는 사다리
            set(0, y, -2, Material.COBBLESTONE);
            Block lb = w.getBlockAt(ox, oy + y, oz - 1);
            remember(lb);
            lb.setType(Material.LADDER, false);
            if (lb.getBlockData() instanceof Directional dl) { dl.setFacing(BlockFace.SOUTH); lb.setBlockData(dl, false); }
        }
        for (int[] c : new int[][]{{-2, -2}, {2, 2}}) box(c[0], 1, c[1], c[0], 3, c[1], Material.OAK_FENCE);
        box(-2, 4, -2, 2, 4, 2, Material.OAK_SLAB);
        chest(0, -6, 0, 2);
    }

    private void graveyard() {
        foundation(-6, -5, 6, 5, Material.PODZOL);
        for (int x = -6; x <= 6; x += 2) { set(x, 0, -5, Material.IRON_BARS); set(x, 0, 5, Material.IRON_BARS); }
        for (int z = -5; z <= 5; z += 2) { set(-6, 0, z, Material.IRON_BARS); set(6, 0, z, Material.IRON_BARS); }
        for (int x = -4; x <= 4; x += 3)
            for (int z = -3; z <= 3; z += 3) {
                set(x, 0, z, Material.COBBLESTONE_WALL);
                set(x, -1, z + 1, Material.COARSE_DIRT);
            }
        set(0, 0, 0, Material.SOUL_LANTERN);
        chest(4, 0, -3, 1);
    }

    // ------------------------------------------------------------------ 자연 생성
    @EventHandler
    public void onChunk(ChunkLoadEvent e) {
        if (!e.isNewChunk() || e.getWorld().getEnvironment() != World.Environment.NORMAL) return;
        double chance = plugin.getConfig().getDouble("structures.natural-chance", 0.004);
        if (rnd.nextDouble() >= chance) return;
        if (records.size() >= plugin.getConfig().getInt("structures.max-natural", 25)) return;
        Chunk c = e.getChunk();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!c.isLoaded()) return;
            Block top = c.getWorld().getHighestBlockAt(c.getX() * 16 + 8, c.getZ() * 16 + 8);
            if (top.isLiquid() || top.getType() == Material.ICE || top.getType().name().contains("LEAVES")) return;
            Location spawn = c.getWorld().getSpawnLocation();
            if (top.getLocation().distanceSquared(spawn) < 64 * 64) return;
            double minD = plugin.getConfig().getDouble("structures.min-distance", 300);
            for (Record r : records.values())
                if (r.world.equals(top.getWorld().getName()) && Math.hypot(r.x - top.getX(), r.z - top.getZ()) < minD) return;
            String type = DECOR.get(rnd.nextInt(DECOR.size()));
            build(type, top.getLocation().add(0, 1, 0), null, null, true);
            plugin.getLogger().info("구조물 자연 생성: " + NAMES.get(type) + " @ " + top.getX() + ", " + top.getY() + ", " + top.getZ());
        }, 40L);
    }

    @SuppressWarnings("unused")
    private static BlockFace face(BlockData d) {
        return d instanceof Directional dd ? dd.getFacing() : BlockFace.NORTH;
    }

    // ------------------------------------------------------------------ 기록 · 소멸
    private static class Record {
        String id, world, type;
        int x, y, z;
        long despawnAt;
        List<String> chests = new ArrayList<>();
        Map<String, String> snapshot = new LinkedHashMap<>();
    }

    private final Map<String, Record> records = new LinkedHashMap<>();
    private final java.io.File recFile() { return new java.io.File(plugin.getDataFolder(), "structures.yml"); }

    private String lastRecordId;

    /** 월드보스 전장: 지은 뒤 기록 ID 를 돌려줌 (나중에 despawnSoon 으로 원래 지형 복구) */
    public String buildArena(String type, Location at) {
        lastRecordId = null;
        build(type, at, null, null, true);
        return lastRecordId;
    }

    public void despawnSoon(String recordId, long ms) {
        Record r = recordId == null ? null : records.get(recordId);
        if (r == null) return;
        r.despawnAt = System.currentTimeMillis() + ms;
        saveRecords();
    }

    private void saveRecord(String type, Location at) {
        Record r = new Record();
        r.id = "s" + System.currentTimeMillis() + rnd.nextInt(1000);
        r.world = at.getWorld().getName();
        r.type = type;
        r.x = at.getBlockX(); r.y = at.getBlockY(); r.z = at.getBlockZ();
        for (Location c : recordChests) r.chests.add(c.getBlockX() + "," + c.getBlockY() + "," + c.getBlockZ());
        r.snapshot.putAll(recording);
        records.put(r.id, r);
        lastRecordId = r.id;
        saveRecords();
    }

    public void loadRecords() {
        records.clear();
        var y = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(recFile());
        for (String id : y.getKeys(false)) {
            var s = y.getConfigurationSection(id);
            if (s == null) continue;
            Record r = new Record();
            r.id = id; r.world = s.getString("world"); r.type = s.getString("type");
            r.x = s.getInt("x"); r.y = s.getInt("y"); r.z = s.getInt("z");
            r.despawnAt = s.getLong("despawn-at");
            r.chests.addAll(s.getStringList("chests"));
            for (String line : s.getStringList("snapshot")) {
                int i = line.indexOf('|');
                if (i > 0) r.snapshot.put(line.substring(0, i), line.substring(i + 1));
            }
            records.put(id, r);
        }
    }

    private void saveRecords() {
        var y = new org.bukkit.configuration.file.YamlConfiguration();
        for (Record r : records.values()) {
            y.set(r.id + ".world", r.world); y.set(r.id + ".type", r.type);
            y.set(r.id + ".x", r.x); y.set(r.id + ".y", r.y); y.set(r.id + ".z", r.z);
            y.set(r.id + ".despawn-at", r.despawnAt);
            y.set(r.id + ".chests", r.chests);
            List<String> snap = new ArrayList<>();
            r.snapshot.forEach((k, v) -> snap.add(k + "|" + v));
            y.set(r.id + ".snapshot", snap);
        }
        try { y.save(recFile()); } catch (java.io.IOException ignored) { }
    }

    /** 유적(점프맵) 자동 생성: 목표 개수보다 적으면 스폰에서 300~1500칸 사이에 하나씩 */
    private void autoRuins() {
        int target = plugin.getConfig().getInt("structures.ruin-target", 6);
        if (plugin.ruins().all().size() >= target) return;
        World w = Bukkit.getWorlds().get(0);
        for (int i = 0; i < 15; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, d = 300 + rnd.nextDouble() * 1200;
            Location l = w.getSpawnLocation().clone().add(Math.cos(a) * d, 0, Math.sin(a) * d);
            Block top = kr.rpgcraft.util.Locs.surface(w, l);
            if (top.isLiquid()) continue;
            String res = doBuild("ruin", null, null, top.getLocation().add(0, 1, 0));
            plugin.getLogger().info("유적 자동 생성 @ " + top.getX() + ", " + top.getZ() + " (" + res + ")");
            return;
        }
    }

    /** 일정 시간마다 무작위 플레이어 근처(150~400칸)에 무작위 구조물 (자연 생성 상한 안에서) */
    private void randomSpawn() {
        var online = new ArrayList<>(org.bukkit.Bukkit.getOnlinePlayers());
        online.removeIf(p -> p.getWorld().getEnvironment() != World.Environment.NORMAL);
        if (online.isEmpty()) return;
        if (records.size() >= plugin.getConfig().getInt("structures.max-natural", 25)) return;
        Player p = online.get(rnd.nextInt(online.size()));
        for (int i = 0; i < 10; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, d = 150 + rnd.nextDouble() * 250;
            Location l = p.getLocation().clone().add(Math.cos(a) * d, 0, Math.sin(a) * d);
            Block top = p.getWorld().getHighestBlockAt(l, org.bukkit.HeightMap.MOTION_BLOCKING_NO_LEAVES);
            if (top.isLiquid()) continue;
            boolean close = false;
            for (Record r : records.values())
                if (r.world.equals(p.getWorld().getName()) && Math.hypot(r.x - top.getX(), r.z - top.getZ()) < plugin.getConfig().getInt("structures.min-distance", 300) / 2.0) close = true;
            if (close) continue;
            String type = DECOR.get(rnd.nextInt(DECOR.size()));
            doBuild(type, null, null, top.getLocation().add(0, 1, 0));
            return;
        }
    }

    /** 모험 스탯이 부족하면 보물 상자를 열 수 없다 */
    @EventHandler(priority = org.bukkit.event.EventPriority.LOW)
    public void onLocked(org.bukkit.event.inventory.InventoryOpenEvent e) {
        if (!(e.getInventory().getHolder() instanceof Chest c) || !(e.getPlayer() instanceof Player p)) return;
        Integer req = c.getPersistentDataContainer().get(REQ_ADV, org.bukkit.persistence.PersistentDataType.INTEGER);
        if (req == null || p.hasPermission("rpgcraft.admin") && p.getGameMode() == GameMode.CREATIVE) return;
        double adv = plugin.data().get(p).stats.adv;
        if (adv < req) {
            e.setCancelled(true);
            Text.actionBar(p, "&c모험 " + req + " 이상만 열 수 있는 상자입니다. &7(현재 " + (int) adv + ")");
            p.playSound(p.getLocation(), Sound.BLOCK_CHEST_LOCKED, 1f, 1f);
        }
    }

    /** 개인 전리품 상자: 플레이어마다 따로 채워진 창을 연다 */
    @EventHandler(priority = org.bukkit.event.EventPriority.NORMAL)
    public void onPersonal(org.bukkit.event.inventory.InventoryOpenEvent e) {
        if (e.isCancelled() || !(e.getInventory().getHolder() instanceof Chest c) || !(e.getPlayer() instanceof Player p)) return;
        Integer tier = c.getPersistentDataContainer().get(LOOT_TIER, org.bukkit.persistence.PersistentDataType.INTEGER);
        if (tier == null) return;
        e.setCancelled(true);
        Location l = c.getLocation();
        String locKey = l.getWorld().getName() + "_" + l.getBlockX() + "_" + l.getBlockY() + "_" + l.getBlockZ();
        String key = p.getUniqueId() + "|" + locKey;
        var d = plugin.data().get(p);
        org.bukkit.inventory.Inventory inv = personal.get(key);
        if (inv == null) {
            PersonalChest holder = new PersonalChest();
            inv = Bukkit.createInventory(holder, 27, Text.c("&8보물 상자 &7(나만의 전리품)"));
            holder.inv = inv;
            if (d.counters.putIfAbsent("pchest_" + locKey, 1.0) == null) fillLoot(inv, tier);  // 처음 여는 사람에게만 새로 채움
            personal.put(key, inv);
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            p.openInventory(personal.get(key));
            p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1f);
        });
        scheduleDespawn(l, p);
    }

    private void scheduleDespawn(Location l, Player p) {
        String key = l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
        for (Record r : records.values()) {
            if (!r.world.equals(l.getWorld().getName()) || !r.chests.contains(key) || r.despawnAt > 0) continue;
            long min = plugin.getConfig().getLong("structures.despawn-minutes", 10);
            r.despawnAt = System.currentTimeMillis() + min * 60_000;
            saveRecords();
            Text.msg(p, "&7이 " + NAMES.getOrDefault(r.type, "구조물") + "은(는) &e" + min + "분 뒤&7 무너져 사라집니다.");
        }
    }

    /** 보물 상자를 열면 소멸 예약 */
    @EventHandler(ignoreCancelled = true)
    public void onOpen(org.bukkit.event.inventory.InventoryOpenEvent e) {
        if (!(e.getInventory().getHolder() instanceof Chest c)) return;
        Location l = c.getLocation();
        String key = l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
        for (Record r : records.values()) {
            if (!r.world.equals(l.getWorld().getName()) || !r.chests.contains(key) || r.despawnAt > 0) continue;
            long min = plugin.getConfig().getLong("structures.despawn-minutes", 10);
            r.despawnAt = System.currentTimeMillis() + min * 60_000;
            saveRecords();
            Text.msg(e.getPlayer(), "&7이 " + NAMES.getOrDefault(r.type, "구조물") + "은(는) &e" + min + "분 뒤&7 무너져 사라집니다.");
        }
    }

    /** 1분마다: 소멸 시간이 된 구조물 복구 */
    public void despawnTick() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (Record r : new ArrayList<>(records.values())) {
            if (r.despawnAt <= 0 || r.despawnAt > now) continue;
            World wd = Bukkit.getWorld(r.world);
            if (wd == null) continue;
            for (Map.Entry<String, String> en : r.snapshot.entrySet()) {
                String[] p = en.getKey().split(",");
                try {
                    Block b = wd.getBlockAt(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
                    if (b.getState() instanceof Chest ch) ch.getInventory().clear();
                    b.setBlockData(Bukkit.createBlockData(en.getValue()), false);
                } catch (Exception ignored) { }
            }
            wd.spawnParticle(Particle.CLOUD, new Location(wd, r.x, r.y + 2, r.z), 60, 3, 2, 3, 0.02);
            records.remove(r.id);
            changed = true;
        }
        if (changed) saveRecords();
    }

    public static String list() {
        StringJoiner j = new StringJoiner(", ");
        NAMES.forEach((k, v) -> j.add(k + "(" + v + ")"));
        return j.toString();
    }

    public void msg(Player p, String s) {
        Text.msg(p, s);
    }
}
