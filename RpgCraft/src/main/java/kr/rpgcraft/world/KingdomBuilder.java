package kr.rpgcraft.world;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.util.Text;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * v5.10.56 스폰 왕국 짓기 — /rpg관리 kingdom build confirm.
 * 서 있는 블록을 한가운데(스폰)로 1000 x 1000 부지를 고르고 KingdomPlan 대로 짓는다.
 *  1) 설계도는 비동기로 계산 (약 2~3초)
 *  2) 청크마다: 부지 고르기 (원래 지형 · 나무를 걷어 내고 바닥을 잔디로, 빈 아래는 흙으로 메움) → 설계도 블록 배치. 한 틱에 kingdom.ms-per-tick 밀리초씩만 일해서 서버가 멈추지 않음
 *  3) 다 지으면 상점 NPC (시장 노점) · 의뢰 NPC (모험가 광장 · 호숫가 · 농장 · 대장간 · 왕성) 를 세우고, 월드 스폰을 광장 분수 앞으로 옮김
 * 왕국 안 (kingdom.half 칸, 기본 500) 에서는 몬스터가 자연적으로 생기지 않는다 (WarManager.noMobs → 모든 자연 · 이벤트 몬스터 생성에 적용).
 */
public class KingdomBuilder {
    private final RpgCraft plugin;
    private final NamespacedKey NPC_KEY;
    private BukkitRunnable task;
    private int done, total;

    public KingdomBuilder(RpgCraft plugin) {
        this.plugin = plugin;
        this.NPC_KEY = new NamespacedKey(plugin, "kingdom_npc");
    }

    /** 이 자리가 왕국(몬스터 없는 곳) 안인지. 왕국을 짓기 전에는 월드 스폰 둘레 kingdom.half 칸 */
    public static boolean inKingdom(RpgCraft plugin, Location l) {
        if (l == null || l.getWorld() == null || !plugin.getConfig().getBoolean("kingdom.no-mobs", true)) return false;
        String wn = plugin.getConfig().getString("kingdom.world", "");
        World w = l.getWorld();
        double cx, cz;
        if (!wn.isEmpty()) {
            if (!wn.equals(w.getName())) return false;
            cx = plugin.getConfig().getDouble("kingdom.x");
            cz = plugin.getConfig().getDouble("kingdom.z");
        } else {
            if (w != Bukkit.getWorlds().get(0)) return false;
            Location s = w.getSpawnLocation();
            cx = s.getX();
            cz = s.getZ();
        }
        int half = plugin.getConfig().getInt("kingdom.half", KingdomPlan.HALF);
        return Math.abs(l.getX() - cx) <= half && Math.abs(l.getZ() - cz) <= half;
    }

    /**
     * v5.10.58 "스폰에서 몇 칸" 안전 거리의 기준 거리. 이 월드에 왕국을 지었으면 왕국 성벽 부지 끝(성 밖)까지의 거리 (성 안은 0),
     * 아니면 예전처럼 월드 스폰까지의 거리. 보스 소환 · 자연 보스 · 토벌전 · 공성 성 짓기 금지 거리에 씀.
     */
    public static double safeDistance(RpgCraft plugin, Location l) {
        var c = plugin.getConfig();
        World w = l.getWorld();
        if (w != null && w.getName().equals(c.getString("kingdom.world", ""))) {
            int half = c.getInt("kingdom.half", KingdomPlan.HALF);
            double dx = Math.max(0, Math.abs(l.getX() - c.getDouble("kingdom.x")) - half);
            double dz = Math.max(0, Math.abs(l.getZ() - c.getDouble("kingdom.z")) - half);
            return Math.hypot(dx, dz);
        }
        Location sp = w.getSpawnLocation();
        return Math.hypot(l.getX() - sp.getX(), l.getZ() - sp.getZ());
    }

    /** 안전 거리 문구: 왕국이 있으면 "성 밖 N칸", 없으면 "스폰 N칸" */
    public static String safeLabel(RpgCraft plugin, World w, int n) {
        return w != null && w.getName().equals(plugin.getConfig().getString("kingdom.world", "")) ? "성 밖 " + n + "칸" : "스폰 " + n + "칸";
    }

    public boolean running() {
        return task != null;
    }

    public String status() {
        return task == null ? "&7짓는 중인 왕국이 없습니다." : "&e왕국 건설 중 &f" + done + "&7/&f" + total + " 청크 (" + (total == 0 ? 0 : done * 100 / total) + "%)";
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
    }

    public void start(CommandSender s, Location center) {
        if (task != null) { Text.msg(s, "&c이미 짓고 있습니다. " + status()); return; }
        World w = center.getWorld();
        int bx = center.getBlockX(), by = center.getBlockY(), bz = center.getBlockZ();
        List<String> shops = new ArrayList<>();
        for (var sh : plugin.shops().all()) if (!sh.id.equals("wandering") && !sh.id.equals("hidden")) shops.add(sh.id);
        List<String> quests = new ArrayList<>();
        for (QuestNpcManager.Type t : QuestNpcManager.Type.values()) quests.add(t.name());
        Text.msg(s, "&e왕국 설계도를 계산합니다... &7(중심 " + bx + ", " + by + ", " + bz + " · 1000 x 1000)");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            KingdomPlan plan = new KingdomPlan(bx * 31L + bz, shops, quests).build();
            Bukkit.getScheduler().runTask(plugin, () -> place(s, w, bx, by, bz, plan));
        });
    }

    private void place(CommandSender s, World w, int bx, int by, int bz, KingdomPlan plan) {
        BlockData[] data = new BlockData[plan.palette.size()];
        int bad = 0;
        for (int i = 0; i < data.length; i++) {
            try {
                data[i] = Bukkit.createBlockData(plan.palette.get(i));
            } catch (IllegalArgumentException ex) {
                bad++;
                plugin.getLogger().warning("[Kingdom] unknown block: " + plan.palette.get(i));
            }
        }
        BlockData grass = Material.GRASS_BLOCK.createBlockData(), dirt = Material.DIRT.createBlockData(), air = Material.AIR.createBlockData();
        // 가운데에서 바깥으로 (가까운 곳이 먼저 보이게)
        int r = Math.floorDiv(KingdomPlan.HALF, 16) + 1;
        List<int[]> order = new ArrayList<>();
        for (int cx = -r; cx <= r; cx++) for (int cz = -r; cz <= r; cz++) order.add(new int[]{cx, cz});
        order.sort(Comparator.comparingInt(a -> a[0] * a[0] + a[1] * a[1]));
        total = order.size();
        done = 0;
        plugin.getConfig().set("kingdom.world", w.getName());
        plugin.getConfig().set("kingdom.x", bx);
        plugin.getConfig().set("kingdom.y", by);
        plugin.getConfig().set("kingdom.z", bz);
        plugin.saveConfig();
        long budget = plugin.getConfig().getLong("kingdom.ms-per-tick", 30);
        Text.msg(s, "&a설계 완료: 블록 " + String.format("%,d", plan.ops) + "개 · " + total + " 청크" + (bad > 0 ? " &c(모르는 블록 " + bad + "종은 건너뜀)" : "") + " &7— 짓는 동안 서버가 조금 느릴 수 있습니다.");
        final int half = KingdomPlan.HALF;
        task = new BukkitRunnable() {
            int i = 0;
            long lastMsg = 0;

            @Override
            public void run() {
                long until = System.currentTimeMillis() + budget;
                while (i < order.size() && System.currentTimeMillis() < until) {
                    int[] c = order.get(i++);
                    // 1) 부지 고르기
                    for (int lx = 0; lx < 16; lx++)
                        for (int lz = 0; lz < 16; lz++) {
                            int rx = c[0] * 16 + lx, rz = c[1] * 16 + lz;
                            if (Math.abs(rx) > half || Math.abs(rz) > half) continue;
                            int x = bx + rx, z = bz + rz;
                            int top = w.getHighestBlockYAt(x, z);
                            for (int y = Math.max(top, by); y >= by; y--) {
                                Block b = w.getBlockAt(x, y, z);
                                if (!b.getType().isAir()) b.setBlockData(air, false);
                            }
                            w.getBlockAt(x, by - 1, z).setBlockData(grass, false);
                            for (int y = by - 2; y >= Math.max(w.getMinHeight() + 1, by - 24); y--) {
                                Block b = w.getBlockAt(x, y, z);
                                Material m = b.getType();
                                if (m.isAir() || m == Material.WATER || m == Material.LAVA || m.name().endsWith("_LEAVES") || m.name().endsWith("_LOG") || m == Material.SEAGRASS || m == Material.TALL_SEAGRASS || m == Material.KELP || m == Material.KELP_PLANT)
                                    b.setBlockData(dirt, false);
                                else if (y < by - 4) break;
                                else if (m == Material.GRASS_BLOCK) b.setBlockData(dirt, false);
                            }
                        }
                    // 2) 설계도 블록
                    long k = KingdomPlan.key(c[0], c[1]);
                    int[] arr = plan.chunks.get(k);
                    if (arr != null) {
                        int n = plan.size(k);
                        for (int j = 0; j < n; j++) {
                            int v = arr[j];
                            BlockData d = data[v >>> 17];
                            if (d == null) continue;
                            int x = bx + c[0] * 16 + (v & 15), z = bz + c[1] * 16 + ((v >> 4) & 15), y = by + ((v >> 8) & 511) - 64;
                            w.getBlockAt(x, y, z).setBlockData(d, false);
                        }
                    }
                    done = i;
                }
                if (System.currentTimeMillis() - lastMsg > 3000) {
                    lastMsg = System.currentTimeMillis();
                    for (Player p : Bukkit.getOnlinePlayers()) if (p.hasPermission("rpgcraft.admin")) Text.actionBar(p, status());
                }
                if (i >= order.size()) {
                    cancel();
                    task = null;
                    finish(s, w, bx, by, bz, plan);
                }
            }
        };
        task.runTaskTimer(plugin, 1L, 1L);
    }

    private void finish(CommandSender s, World w, int bx, int by, int bz, KingdomPlan plan) {
        // 예전에 세운 왕국 NPC 정리 (불러와진 청크) → 새로 세움
        for (Entity e : w.getEntities())
            if (e.getPersistentDataContainer().has(NPC_KEY, PersistentDataType.BYTE)) e.remove();
        int shops = 0, quests = 0;
        for (KingdomPlan.Npc n : plan.npcs) {
            Location l = new Location(w, bx + n.x() + 0.5, by + n.y(), bz + n.z() + 0.5, n.yaw(), 0);
            Villager v = null;
            if (n.kind().equals("shop")) {
                v = plugin.shops().spawnNpc(l, n.id());
                if (v != null) shops++;
            } else {
                try {
                    v = plugin.questNpcs().spawn(l, QuestNpcManager.Type.valueOf(n.id()));
                    quests++;
                } catch (IllegalArgumentException ignored) {
                }
            }
            if (v != null) {
                v.setRotation(n.yaw(), 0);
                v.getPersistentDataContainer().set(NPC_KEY, PersistentDataType.BYTE, (byte) 1);
            }
        }
        w.setSpawnLocation(bx, by, bz + 22);
        // 짓기 전 부지에 있던 몬스터 정리
        int cleared = 0;
        for (Entity e : w.getEntities())
            if (e instanceof org.bukkit.entity.Enemy && e.getCustomName() == null && !e.isPersistent() && plugin.mobs().peek(e) == null && inKingdom(plugin, e.getLocation())) {
                e.remove();
                cleared++;
            }
        Text.msg(s, "&a&l왕국 완성! &f상점 NPC " + shops + "명 · 의뢰 NPC " + quests + "명을 배치하고 월드 스폰을 광장 분수 앞으로 옮겼습니다." + (cleared > 0 ? " &7(몬스터 " + cleared + "마리 정리)" : ""));
        Text.announce(Text.PREFIX + Text.c("&6&l새 왕국이 세워졌습니다! &f스폰 광장에서 상점 · 의뢰 NPC 를 만나 보세요."));
    }
}
