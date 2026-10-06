package io.versaera.domain.boss;

/**
 * 보스 공격 범위. 엔티티를 쓰지 않고 수학으로 맞았는지 판정한다 (거대 보스에서도 성능이 일정).
 * radius · width · angle 은 블록 / 도 단위. 보스 크기(scale)를 곱해 쓴다.
 */
public enum Shape {
    CIRCLE, CONE, LINE, RING;

    public boolean hits(Vec origin, double yawDeg, Vec p, double radius, double inner, double widthOrAngle, double height) {
        if (Math.abs(p.y() - origin.y()) > height) return false;
        double dx = p.x() - origin.x(), dz = p.z() - origin.z();
        double dist = Math.hypot(dx, dz);
        double[] f = Vec.facing(yawDeg);
        return switch (this) {
            case CIRCLE -> dist <= radius;
            case RING -> dist <= radius && dist >= inner;
            case CONE -> {
                if (dist > radius) yield false;
                if (dist < 1e-6) yield true;
                double cos = (dx * f[0] + dz * f[1]) / dist;
                yield Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, cos)))) <= widthOrAngle / 2;
            }
            case LINE -> {
                double along = dx * f[0] + dz * f[1];
                double side = Math.abs(dx * f[1] - dz * f[0]);
                yield along >= 0 && along <= radius && side <= widthOrAngle / 2;
            }
        };
    }
}
