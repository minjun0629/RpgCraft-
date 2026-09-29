package kr.rpgcraft.minigame;

/** 난이도 */
public enum Diff {
    EASY("쉬움", "&a"), NORMAL("보통", "&6"), HARD("어려움", "&c");

    public final String label, color;

    Diff(String label, String color) {
        this.label = label;
        this.color = color;
    }

    public int icon() {
        return Icons.EASY + ordinal();
    }

    /** 난이도별 값 고르기 */
    public int pick(int easy, int normal, int hard) {
        return this == EASY ? easy : this == NORMAL ? normal : hard;
    }

    public double pick(double easy, double normal, double hard) {
        return this == EASY ? easy : this == NORMAL ? normal : hard;
    }
}
