package kr.rpgcraft.royal;

import kr.rpgcraft.stat.Stat;
import org.bukkit.Material;

/**
 * v5.11.0 조각 주제. 조각상은 고른 재료 블록을 깎은 상자들(1칸 = 16)로 그린다 — 앞이 +Z, 받침대 위에 선다.
 * model 이 있는 주제는 「조각 생명부여」로 살아 있는 생명체가 될 수 있다 (몸은 길들인 늑대 + 그 3D 모델).
 */
public enum Subject {
    WOLF("늑대", 1, 1, Material.BONE, Stat.STR_PCT, new double[]{3, 6, 10}, "an_wolf", 0.85, Role.FIGHTER, 1.0, 1.0, new double[][]{
            {-3, 8, -6, 3, 14, 4}, {-3.5, 8, 1, 3.5, 15, 5}, {-3, 11, 4, 3, 17, 9}, {-1.5, 11, 9, 1.5, 13.5, 12},
            {-3, 17, 5, -1, 19, 7}, {1, 17, 5, 3, 19, 7},
            {-3, 3, -5, -1, 8, -3}, {1, 3, -5, 3, 8, -3}, {-3, 3, 1, -1, 8, 3}, {1, 3, 1, 3, 8, 3},
            {-1, 11, -10, 1, 13, -6}}),
    FOX("여우", 1, 1, Material.SWEET_BERRIES, Stat.CRIT, new double[]{2, 4, 7}, "an_fox", 0.7, Role.SKIRMISHER, 0.8, 1.1, new double[][]{
            {-2.5, 7, -5, 2.5, 11, 3}, {-3, 9, 3, 3, 14, 7}, {-1, 9, 7, 1, 11, 10},
            {-3, 14, 4, -1, 17, 5.5}, {1, 14, 4, 3, 17, 5.5},
            {-2.5, 3, -4, -1, 7, -2.5}, {1, 3, -4, 2.5, 7, -2.5}, {-2.5, 3, 0.5, -1, 7, 2}, {1, 3, 0.5, 2.5, 7, 2},
            {-2, 6, -11, 2, 10, -5}}),
    GOAT("산양", 2, 2, Material.WHEAT, Stat.SPEED, new double[]{3, 6, 10}, "an_goat", 1.3, Role.CHARGER, 1.1, 1.0, new double[][]{
            {-3.5, 9, -6, 3.5, 16, 5}, {-2.5, 14, 5, 2.5, 20, 9}, {-1, 11, 8, 1, 14, 9},
            {-2.5, 20, 4, -1, 24, 6}, {1, 20, 4, 2.5, 24, 6}, {-2.5, 22, 1, -1, 24, 4}, {1, 22, 1, 2.5, 24, 4},
            {-3.5, 3, -5, -1.5, 9, -3}, {1.5, 3, -5, 3.5, 9, -3}, {-3.5, 3, 2, -1.5, 9, 4}, {1.5, 3, 2, 3.5, 9, 4},
            {-1, 14, -8, 1, 16, -6}}),
    BEAR("곰", 3, 3, Material.HONEYCOMB, Stat.HP_PCT, new double[]{5, 10, 18}, "an_polar_bear", 1.4, Role.GUARDIAN, 1.8, 0.85, new double[][]{
            {-6, 8, -8, 6, 19, 6}, {-4, 13, 6, 4, 20, 12}, {-2, 13, 12, 2, 16, 15},
            {-4, 20, 7, -2, 22, 9}, {2, 20, 7, 4, 22, 9},
            {-6, 3, -7, -2, 8, -3}, {2, 3, -7, 6, 8, -3}, {-6, 3, 1, -2, 8, 5}, {2, 3, 1, 6, 8, 5},
            {-1.5, 14, -10, 1.5, 17, -8}}),
    KNIGHT("기사", 3, 3, Material.IRON_SWORD, Stat.DEF, new double[]{2, 4, 7}, "day_knight", 1.9, Role.FIGHTER, 1.3, 1.15, new double[][]{
            {-3.5, 3, -1.5, -0.5, 14, 1.5}, {0.5, 3, -1.5, 3.5, 14, 1.5}, {-4, 14, -2, 4, 25, 2},
            {-6.5, 14, -1.5, -4, 25, 1.5}, {4, 15, -1.5, 6.5, 25, 1.5}, {-3, 25, -3, 3, 31, 3}, {-0.5, 31, -2, 0.5, 34, 2},
            {-0.5, 4, 3, 0.5, 20, 4}, {-3, 19, 3, 3, 20, 4}, {-1, 20, 3, 1, 23, 4}, {-7.5, 12, -3, -6.5, 24, 3}}),
    HARPY("하피", 4, 3, Material.FEATHER, Stat.DODGE, new double[]{2, 4, 6}, "storm_harpy", 1.8, Role.RANGER, 0.9, 1.25, new double[][]{
            {-2.5, 3, -1, -1, 10, 1}, {1, 3, -1, 2.5, 10, 1}, {-3, 10, -2, 3, 20, 2}, {-2.5, 20, -2.5, 2.5, 26, 2.5},
            {-3, 22, -3.5, 3, 27, -1.5}, {-15, 13, -1, -3, 23, 0}, {3, 13, -1, 15, 23, 0},
            {-17, 17, -1, -15, 25, 0}, {15, 17, -1, 17, 25, 0}}),
    GOLEM("바위 거인", 4, 5, Material.COBBLESTONE, Stat.ADV_PCT, new double[]{4, 8, 14}, "wild_golem", 2.2, Role.GUARDIAN, 2.4, 0.9, new double[][]{
            {-6, 3, -3, -1, 13, 3}, {1, 3, -3, 6, 13, 3}, {-8, 13, -4, 8, 28, 4}, {-3.5, 28, -2, 3.5, 34, 4},
            {-12, 8, -2.5, -8, 28, 2.5}, {8, 8, -2.5, 12, 28, 2.5}, {-11, 26, -3, -6, 31, 3}, {6, 26, -3, 11, 31, 3}}),
    ICE_DRAGON("빙룡", 5, 8, Material.PACKED_ICE, Stat.MAGIC, new double[]{300, 700, 1500}, null, 0, null, 0, 0, new double[][]{
            {-4, 8, -10, 4, 16, 6}, {-2.5, 14, 5, 2.5, 24, 9}, {-3, 22, 9, 3, 27, 16}, {-2, 20, 10, 2, 22, 16},
            {-3, 27, 10, -2, 31, 12}, {2, 27, 10, 3, 31, 12},
            {-18, 17, -8, -4, 18, 4}, {4, 17, -8, 18, 18, 4}, {-18, 18, -8, -16, 26, -6}, {16, 18, -8, 18, 26, -6},
            {-4, 3, -8, -1, 9, -5}, {1, 3, -8, 4, 9, -5}, {-4, 3, 1, -1, 9, 4}, {1, 3, 1, 4, 9, 4},
            {-1.5, 8, -18, 1.5, 11, -10}, {-1, 6, -24, 1, 8, -18}}),
    GODDESS("여신 프레야", 5, 6, Material.GOLDEN_APPLE, Stat.EXP_PCT, new double[]{10, 20, 40}, null, 0, null, 0, 0, new double[][]{
            {-5, 3, -4, 5, 16, 4}, {-3.5, 16, -2.5, 3.5, 22, 2.5}, {-4, 22, -2.5, 4, 27, 2.5},
            {-6.5, 20, -1, -4, 30, 1}, {4, 20, -1, 6.5, 30, 1}, {-2.5, 27, -2.5, 2.5, 32, 2.5},
            {-3, 24, -3.5, 3, 32, -2}, {-2.5, 32, -2.5, 2.5, 33.5, 2.5}, {-1.5, 34, -1.5, 1.5, 37, 1.5}});

    /** 생명체가 된 뒤의 싸움 방식 */
    public enum Role {
        FIGHTER("근접", "적에게 달려들어 공격"),
        SKIRMISHER("기습", "빠르게 물고 치명타를 노림"),
        CHARGER("돌진", "들이받아 적을 밀쳐냄"),
        GUARDIAN("수호", "주인을 노리는 적을 도발해 대신 맞음"),
        RANGER("원거리", "주인 곁에서 바람 칼날을 날림");
        public final String label, desc;

        Role(String label, String desc) {
            this.label = label;
            this.desc = desc;
        }
    }

    public final String label;
    /** 난이도 1 ~ 5: 높을수록 좋은 등급이 어렵지만 보상이 큼 */
    public final int difficulty;
    /** 필요한 재료 블록 수 */
    public final int blocks;
    public final Material icon;
    /** 감상 버프로 오르는 능력치와 등급별(수작 · 명작 · 대작) 수치 */
    public final Stat buffStat;
    public final double[] buffValues;
    /** 생명부여 뒤 모습 (null = 생명을 불어넣을 수 없는 주제) */
    public final String model;
    public final double modelHeight;
    public final Role role;
    public final double hpMult, atkMult;
    public final double[][] boxes;

    Subject(String label, int difficulty, int blocks, Material icon, Stat buffStat, double[] buffValues, String model, double modelHeight,
            Role role, double hpMult, double atkMult, double[][] boxes) {
        this.label = label;
        this.difficulty = difficulty;
        this.blocks = blocks;
        this.icon = icon;
        this.buffStat = buffStat;
        this.buffValues = buffValues;
        this.model = model;
        this.modelHeight = modelHeight;
        this.role = role;
        this.hpMult = hpMult;
        this.atkMult = atkMult;
        this.boxes = boxes;
    }

    public boolean canLive() {
        return model != null;
    }

    /** 조각상 높이 (칸) — 상호작용 판정 높이로 씀 */
    public double height() {
        double top = 0;
        for (double[] b : boxes) top = Math.max(top, b[4]);
        return top / 16.0;
    }

    public static Subject of(String name) {
        try {
            return valueOf(name);
        } catch (Exception e) {
            return null;
        }
    }
}
