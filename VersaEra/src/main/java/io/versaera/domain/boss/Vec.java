package io.versaera.domain.boss;

/** 보스 판정용 2D(수평) + 높이 좌표. Bukkit Location 과 무관한 순수 수학. */
public record Vec(double x, double y, double z) {
    public double horizontalDistance(Vec o) {
        return Math.hypot(o.x - x, o.z - z);
    }

    /** yaw(Minecraft 규칙: 0 = +Z, 90 = -X) 방향 단위 벡터 */
    public static double[] facing(double yawDeg) {
        double r = Math.toRadians(yawDeg);
        return new double[]{-Math.sin(r), Math.cos(r)};
    }
}
