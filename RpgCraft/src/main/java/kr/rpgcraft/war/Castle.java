package kr.rpgcraft.war;

import org.bukkit.Location;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.List;

/** 공성전용 성. 성벽 여러 개 + 신호기 1개 */
public class Castle {
    public static class Wall {
        public String id;
        public Location min, max;
        public double maxHp, hp;
        public boolean broken;

        public boolean contains(Location l) {
            if (l.getWorld() == null || !l.getWorld().equals(min.getWorld())) return false;
            return l.getBlockX() >= min.getBlockX() && l.getBlockX() <= max.getBlockX()
                    && l.getBlockY() >= min.getBlockY() && l.getBlockY() <= max.getBlockY()
                    && l.getBlockZ() >= min.getBlockZ() && l.getBlockZ() <= max.getBlockZ();
        }

        public double distance(Location l) {
            if (l.getWorld() == null || !l.getWorld().equals(min.getWorld())) return Double.MAX_VALUE;
            BoundingBox box = new BoundingBox(min.getBlockX(), min.getBlockY(), min.getBlockZ(), max.getBlockX() + 1, max.getBlockY() + 1, max.getBlockZ() + 1);
            double dx = Math.max(0, Math.max(box.getMinX() - l.getX(), l.getX() - box.getMaxX()));
            double dy = Math.max(0, Math.max(box.getMinY() - l.getY(), l.getY() - box.getMaxY()));
            double dz = Math.max(0, Math.max(box.getMinZ() - l.getZ(), l.getZ() - box.getMaxZ()));
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }

        public long volume() {
            return (long) (max.getBlockX() - min.getBlockX() + 1) * (max.getBlockY() - min.getBlockY() + 1) * (max.getBlockZ() - min.getBlockZ() + 1);
        }
    }

    public final String id;
    public String name;
    public String owner;
    public Location beacon, attackerSpawn, defenderSpawn;
    /** v5.10.10 자동으로 지은 성의 부지 (철거할 때 씀): 한가운데 · 가로세로 반지름 · 아래/위 높이 (r = 0 이면 모름) */
    public Location center;
    public int r, down, up;
    public final List<Wall> walls = new ArrayList<>();

    public Castle(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public Wall wallAt(Location l) {
        for (Wall w : walls) if (w.contains(l)) return w;
        return null;
    }

    public int brokenWalls() {
        int n = 0;
        for (Wall w : walls) if (w.broken) n++;
        return n;
    }

    public boolean isBeacon(Location l) {
        return beacon != null && l.getWorld() != null && l.getWorld().equals(beacon.getWorld())
                && l.getBlockX() == beacon.getBlockX() && l.getBlockY() == beacon.getBlockY() && l.getBlockZ() == beacon.getBlockZ();
    }
}
