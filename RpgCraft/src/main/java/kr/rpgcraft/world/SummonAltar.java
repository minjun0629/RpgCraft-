package kr.rpgcraft.world;

import kr.rpgcraft.RpgCraft;
import org.bukkit.*;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;

/**
 * 보스 소환 제단 (v5.6.0)
 * 소환 아이템을 쓰면 그 자리에 제단이 바닥부터 한 조각씩 쌓여 올라가고(블록 디스플레이 — 실제 지형은 바꾸지 않음),
 * 10초가 차면 번개와 함께 보스가 나타난 뒤 제단은 가라앉아 사라진다.
 *  - 원혼: 심층암 바닥 · 영혼 등불 기둥 6개 · 우는 흑요석 오벨리스크
 *  - 발록: 네더 벽돌 · 마그마 바닥 · 흑암 기둥 4개 · 흑요석 지옥문
 */
public class SummonAltar {
    public static final String TAG = "rpg_altar";
    public static final int DURATION = 200;   // 10초

    private record Piece(double x, double y, double z, Material m, int at, boolean glow) {}

    private final RpgCraft plugin;
    private final Set<String> building = new HashSet<>();   // 지금 제단이 올라가는 중인 보스 (중복 소환 방지)
    private final List<Entity> live = new ArrayList<>();

    private final NamespacedKey key;

    public SummonAltar(RpgCraft plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, TAG);
        for (World w : Bukkit.getWorlds())   // 서버가 도중에 꺼져 남은 제단 조각 치우기
            for (BlockDisplay d : w.getEntitiesByClass(BlockDisplay.class)) if (((Entity) d).getPersistentDataContainer().has(key, org.bukkit.persistence.PersistentDataType.BYTE)) d.remove();
    }

    public boolean building(String bossId) {
        return building.contains(bossId);
    }

    /**
     * 제단을 세우고 10초 뒤 spawn 을 실행한다. spawn 이 false 를 돌려주면 onFail 실행 (아이템 돌려주기 등)
     */
    public void raise(String bossId, Location ground, Player by, java.util.function.BooleanSupplier spawn, Runnable onFail) {
        building.add(bossId);
        Location base = ground.getBlock().getLocation();
        World w = base.getWorld();
        List<Piece> pieces = bossId.equals("balrog") ? balrog() : spirit();
        boolean hell = bossId.equals("balrog");
        Color col = hell ? Color.fromRGB(0xFF5A1F) : Color.fromRGB(0x5AD8FF);
        Particle fx = hell ? Particle.FLAME : Particle.SOUL_FIRE_FLAME;
        List<BlockDisplay> mine = new ArrayList<>();
        for (Piece pc : pieces) later(pc.at(), () -> {
            BlockDisplay d = w.spawn(base.clone().add(pc.x(), pc.y(), pc.z()), BlockDisplay.class, x -> {
                x.setBlock(pc.m().createBlockData());
                x.setTransformation(tf(0.05f));
                ((Entity) x).getPersistentDataContainer().set(key, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
                x.setPersistent(false);
                if (pc.glow()) x.setBrightness(new Display.Brightness(15, 15));
            });
            mine.add(d);
            live.add(d);
            later(1, () -> { if (d.isValid()) { d.setInterpolationDelay(0); d.setInterpolationDuration(6); d.setTransformation(tf(1f)); } });
            if (pc.at() % 4 == 0) w.playSound(d.getLocation(), pc.glow() ? Sound.BLOCK_AMETHYST_BLOCK_CHIME : Sound.BLOCK_STONE_PLACE, 0.8f, 0.7f + pc.at() / 400f);
            w.spawnParticle(Particle.BLOCK_CRACK, d.getLocation().add(0.5, 0.5, 0.5), 6, 0.3, 0.3, 0.3, 0, pc.m().createBlockData());
        });
        Location mid = base.clone().add(0.5, 0, 0.5);
        for (int t = 0; t < DURATION; t += 4) {   // 모여드는 기운 (점점 빨라짐)
            int tt = t;
            later(t, () -> {
                double r = 6 - 4.5 * tt / DURATION;
                for (int i = 0; i < 3 + tt / 40; i++) {
                    double ang = tt * 0.15 + i * Math.PI * 2 / (3 + tt / 40);
                    w.spawnParticle(fx, mid.clone().add(Math.cos(ang) * r, 0.3 + tt / 60.0, Math.sin(ang) * r), 1, 0, 0, 0, 0);
                }
                w.spawnParticle(Particle.REDSTONE, mid.clone().add(0, 0.2, 0), 4, 2.5, 0.05, 2.5, 0, new Particle.DustOptions(col, 1.5f));
            });
        }
        for (int s = 0; s < 10; s++) {   // 초읽기
            int left = 10 - s;
            later(s * 20L, () -> {
                for (Player p : w.getPlayers()) if (p.getLocation().distanceSquared(mid) < 40 * 40)
                    p.sendTitle("", kr.rpgcraft.util.Text.c((hell ? "&6" : "&b") + "&l" + (hell ? "발록" : "몬스터의 원혼") + " 강림까지 &f&l" + left), 0, 22, 0);
                w.playSound(mid, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 1.4f, 0.5f + (10 - left) * 0.05f);
            });
        }
        later(DURATION - 20, () -> {   // 기둥 꼭대기에서 가운데로 빛줄기
            for (Piece pc : pieces) if (pc.glow() && pc.y() >= 3)
                kr.rpgcraft.util.Vfx.beam(base.clone().add(pc.x() + 0.5, pc.y() + 0.5, pc.z() + 0.5), mid.clone().add(0, 4, 0), 1.2, col);
            w.playSound(mid, Sound.BLOCK_BEACON_POWER_SELECT, 2f, 0.5f);
        });
        later(DURATION, () -> {
            building.remove(bossId);
            w.strikeLightningEffect(mid);
            w.spawnParticle(Particle.FLASH, mid.clone().add(0, 2, 0), 2);
            w.spawnParticle(Particle.EXPLOSION_HUGE, mid.clone().add(0, 1, 0), 2, 1, 0.5, 1);
            w.playSound(mid, Sound.ENTITY_WITHER_SPAWN, 1.6f, hell ? 0.5f : 0.8f);
            boolean ok;
            try { ok = spawn.getAsBoolean(); } catch (Exception ex) { ok = false; }
            if (!ok) onFail.run();
            for (BlockDisplay d : mine) if (d.isValid()) { d.setInterpolationDelay(0); d.setInterpolationDuration(30); d.setTransformation(sunk()); }
            later(32, () -> { for (BlockDisplay d : mine) { d.remove(); live.remove(d); } });
        });
    }

    private static Transformation tf(float s) {
        return new Transformation(new Vector3f(0.5f - s / 2, 0, 0.5f - s / 2), new AxisAngle4f(), new Vector3f(s), new AxisAngle4f());
    }

    private static Transformation sunk() {
        return new Transformation(new Vector3f(0.5f, -1.2f, 0.5f), new AxisAngle4f(), new Vector3f(0.02f), new AxisAngle4f());
    }

    private void later(long t, Runnable r) {
        Bukkit.getScheduler().runTaskLater(plugin, r, Math.max(0, t));
    }

    public void shutdown() {
        for (Entity e : live) e.remove();
        live.clear();
    }

    // ------------------------------------------------------------------ 설계
    /** 원혼 제단: 심층암 원형 바닥 → 영혼 등불 기둥 6개 → 우는 흑요석 오벨리스크 */
    private static List<Piece> spirit() {
        List<Piece> out = new ArrayList<>();
        int t = 0;
        for (int r = 0; r <= 5; r++) {   // 가운데부터 퍼지는 바닥
            for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) {
                int d = (int) Math.round(Math.sqrt(x * x + z * z));
                if (d != r) continue;
                Material m = r <= 1 ? Material.POLISHED_BLACKSTONE : r == 3 ? Material.SCULK : (x + z) % 2 == 0 ? Material.DEEPSLATE_TILES : Material.CRACKED_DEEPSLATE_TILES;
                out.add(new Piece(x, -0.94, z, m, t + d * 4, r == 3));
            }
        }
        t = 30;
        for (int i = 0; i < 6; i++) {   // 기둥 6개 (한 층씩)
            double ang = i * Math.PI / 3;
            int x = (int) Math.round(Math.cos(ang) * 5), z = (int) Math.round(Math.sin(ang) * 5);
            for (int y = 0; y < 4; y++) out.add(new Piece(x, y, z, y == 0 ? Material.CHISELED_DEEPSLATE : Material.DEEPSLATE_BRICKS, t + i * 3 + y * 14, false));
            out.add(new Piece(x, 4, z, Material.SOUL_LANTERN, t + i * 3 + 64, true));
        }
        t = 100;
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) out.add(new Piece(x, 0, z, Material.POLISHED_BLACKSTONE_BRICKS, t + (Math.abs(x) + Math.abs(z)) * 3, false));
        for (int y = 1; y <= 4; y++) out.add(new Piece(0, y, 0, y == 4 ? Material.CRYING_OBSIDIAN : Material.OBSIDIAN, t + 10 + y * 10, y == 4));
        out.add(new Piece(0, 5, 0, Material.SOUL_LANTERN, t + 62, true));
        for (int[] c : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) out.add(new Piece(c[0], 1, c[1], Material.SOUL_LANTERN, t + 40, true));
        return out;
    }

    /** 발록 제단: 네더 벽돌 · 마그마 바닥 → 흑암 기둥 4개(불꽃 머리) → 흑요석 지옥문 */
    private static List<Piece> balrog() {
        List<Piece> out = new ArrayList<>();
        for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) {
            double d = Math.sqrt(x * x + z * z);
            if (d > 6.3) continue;
            Material m = d < 1.6 ? Material.BLACKSTONE : d < 3.6 ? Material.MAGMA_BLOCK : d < 4.6 ? Material.RED_NETHER_BRICKS : Material.NETHER_BRICKS;
            out.add(new Piece(x, -0.94, z, m, (int) (d * 5), m == Material.MAGMA_BLOCK));
        }
        int t = 36;
        for (int i = 0; i < 4; i++) {   // 대각선 기둥 4개
            int x = i < 2 ? -4 : 4, z = i % 2 == 0 ? -4 : 4;
            for (int y = 0; y < 5; y++) out.add(new Piece(x, y, z, y % 2 == 0 ? Material.POLISHED_BLACKSTONE_BRICKS : Material.GILDED_BLACKSTONE, t + i * 3 + y * 12, false));
            out.add(new Piece(x, 5, z, Material.SHROOMLIGHT, t + i * 3 + 64, true));
            out.add(new Piece(x, 6, z, Material.MAGMA_BLOCK, t + i * 3 + 72, true));
        }
        t = 110;
        for (int y = 0; y < 6; y++) {   // 지옥문: 기둥 두 개 → 상인방
            out.add(new Piece(-2, y, 0, Material.OBSIDIAN, t + y * 8, false));
            out.add(new Piece(2, y, 0, Material.OBSIDIAN, t + y * 8, false));
        }
        for (int x = -2; x <= 2; x++) out.add(new Piece(x, 6, 0, x == 0 ? Material.CRYING_OBSIDIAN : Material.OBSIDIAN, t + 50 + Math.abs(x) * 3, x == 0));
        for (int x = -1; x <= 1; x++) for (int y = 0; y < 6; y++) out.add(new Piece(x, y, 0.4, Material.MAGMA_BLOCK, t + 60 + y * 2, true));   // 문 안쪽 불길
        return out;
    }
}
