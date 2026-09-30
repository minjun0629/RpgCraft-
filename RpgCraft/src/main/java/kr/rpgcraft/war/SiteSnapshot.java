package kr.rpgcraft.war;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * v5.10.10 공성 성을 짓기 전 부지의 원래 블록 기록 (철거할 때 그대로 되돌림).
 * 상자 모양 범위의 블록을 팔레트(블록 종류 목록) + 칸마다 번호로 저장한다 (gzip).
 */
public class SiteSnapshot {
    final String world;
    final int x0, y0, z0, sx, sy, sz;
    final List<String> palette = new ArrayList<>();
    final int[] cells;

    SiteSnapshot(String world, int x0, int y0, int z0, int sx, int sy, int sz) {
        this.world = world;
        this.x0 = x0; this.y0 = y0; this.z0 = z0;
        this.sx = sx; this.sy = sy; this.sz = sz;
        this.cells = new int[sx * sy * sz];
    }

    int size() {
        return cells.length;
    }

    private int index(int x, int y, int z) {
        return (x * sz + z) * sy + y;
    }

    /** 여러 틱에 나눠 읽고, 다 읽으면 done 호출 */
    static void capture(Plugin plugin, World w, int x0, int y0, int z0, int x1, int y1, int z1, int perTick, java.util.function.Consumer<SiteSnapshot> done) {
        SiteSnapshot s = new SiteSnapshot(w.getName(), x0, y0, z0, x1 - x0 + 1, y1 - y0 + 1, z1 - z0 + 1);
        Map<String, Integer> ids = new HashMap<>();
        new BukkitRunnable() {
            int x = 0, z = 0;

            @Override
            public void run() {
                int budget = perTick;
                while (budget > 0 && x < s.sx) {
                    for (int y = 0; y < s.sy; y++) {
                        String d = w.getBlockAt(s.x0 + x, s.y0 + y, s.z0 + z).getBlockData().getAsString();
                        Integer id = ids.get(d);
                        if (id == null) { id = s.palette.size(); s.palette.add(d); ids.put(d, id); }
                        s.cells[s.index(x, y, z)] = id;
                    }
                    budget -= s.sy;
                    if (++z >= s.sz) { z = 0; x++; }
                }
                if (x < s.sx) return;
                cancel();
                done.accept(s);
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    void write(File f) throws IOException {
        f.getParentFile().mkdirs();
        try (DataOutputStream o = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(new FileOutputStream(f))))) {
            o.writeInt(1);
            o.writeUTF(world);
            o.writeInt(x0); o.writeInt(y0); o.writeInt(z0);
            o.writeInt(sx); o.writeInt(sy); o.writeInt(sz);
            o.writeInt(palette.size());
            for (String p : palette) o.writeUTF(p);
            for (int c : cells) o.writeInt(c);
        }
    }

    static SiteSnapshot read(File f) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(f))))) {
            if (in.readInt() != 1) throw new IOException("알 수 없는 형식");
            String w = in.readUTF();
            SiteSnapshot s = new SiteSnapshot(w, in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readInt());
            int n = in.readInt();
            for (int i = 0; i < n; i++) s.palette.add(in.readUTF());
            for (int i = 0; i < s.cells.length; i++) s.cells[i] = in.readInt();
            return s;
        }
    }

    /** 여러 틱에 나눠 원래 블록으로 되돌리고, 끝나면 done 호출 */
    void restore(Plugin plugin, World w, int perTick, Runnable done) {
        BlockData[] data = new BlockData[palette.size()];
        for (int i = 0; i < data.length; i++) {
            try { data[i] = Bukkit.createBlockData(palette.get(i)); } catch (IllegalArgumentException ex) { data[i] = Bukkit.createBlockData("minecraft:air"); }
        }
        int budgetMax = Math.max(2000, perTick);
        new BukkitRunnable() {
            int x = 0, z = 0;

            @Override
            public void run() {
                int budget = budgetMax;
                while (budget > 0 && x < sx) {
                    for (int y = sy - 1; y >= 0; y--) {   // 위에서부터 (모래 · 자갈이 떨어지지 않게 아래는 나중에)
                        var b = w.getBlockAt(x0 + x, y0 + y, z0 + z);
                        BlockData want = data[cells[index(x, y, z)]];
                        if (!b.getBlockData().matches(want)) b.setBlockData(want, false);
                    }
                    budget -= sy;
                    if (++z >= sz) { z = 0; x++; }
                }
                if (x < sx) return;
                cancel();
                done.run();
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }
}
