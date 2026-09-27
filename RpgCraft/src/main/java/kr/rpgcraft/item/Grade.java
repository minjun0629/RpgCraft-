package kr.rpgcraft.item;

/**
 * 아이템 등급. 순서(ordinal)가 곧 희귀도이므로 새 등급은 반드시 뒤에 추가한다.
 * (PDC 에 이름으로 저장되므로 기존 아이템과 호환)
 */
public enum Grade {
    NORMAL("노말", "&f", "◇", false, 0xDDDDDD),
    RARE("레어", "&a", "◆", false, 0x55FF55),
    UNIQUE("유니크", "&e", "✦", false, 0xFFD23F),
    LEGEND("레전드", "&c", "❖", true, 0xFF4040),
    MYTHIC("신화", "&d", "✧", true, 0xE070FF);

    public final String label, color, icon;
    public final boolean bold;
    public final int rgb;

    Grade(String label, String color, String icon, boolean bold, int rgb) {
        this.label = label;
        this.color = color;
        this.icon = icon;
        this.bold = bold;
        this.rgb = rgb;
    }

    /** 이름 앞 색상 (레전드 이상은 굵게) */
    public String nameColor() {
        return color + (bold ? "&l" : "");
    }

    /** 로어 머리표: "✦ 유니크" / "✧ 신화 ✧" */
    public String tag() {
        return this == MYTHIC ? "&d&l✧ 신화 ✧" : color + icon + " " + label;
    }

    public boolean atLeast(Grade g) {
        return ordinal() >= g.ordinal();
    }
}
