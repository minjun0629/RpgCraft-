package kr.rpgcraft.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

public final class Locs {
    private Locs() {}

    public static String block(Location l) {
        return l.getWorld().getName() + ";" + l.getBlockX() + ";" + l.getBlockY() + ";" + l.getBlockZ();
    }

    public static String block(Block b) {
        return block(b.getLocation());
    }

    public static String full(Location l) {
        return l.getWorld().getName() + ";" + l.getX() + ";" + l.getY() + ";" + l.getZ() + ";" + l.getYaw() + ";" + l.getPitch();
    }

    public static Location parse(String s) {
        if (s == null || s.isEmpty()) return null;
        String[] p = s.split(";");
        World w = Bukkit.getWorld(p[0]);
        if (w == null) return null;
        Location l = new Location(w, Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]));
        if (p.length >= 6) {
            l.setYaw(Float.parseFloat(p[4]));
            l.setPitch(Float.parseFloat(p[5]));
        }
        return l;
    }

    /** 나무(통나무·나뭇잎)·풀 위가 아닌 진짜 땅 블록 */
    public static org.bukkit.block.Block surface(org.bukkit.World w, org.bukkit.Location at) {
        org.bukkit.block.Block b = w.getHighestBlockAt(at, org.bukkit.HeightMap.MOTION_BLOCKING_NO_LEAVES);
        for (int i = 0; i < 40 && b.getY() > w.getMinHeight(); i++) {
            String n = b.getType().name();
            if (!(n.endsWith("_LOG") || n.endsWith("_WOOD") || n.contains("LEAVES") || n.endsWith("_STEM") || b.isPassable())) break;
            b = b.getRelative(0, -1, 0);
        }
        return b;
    }
}
