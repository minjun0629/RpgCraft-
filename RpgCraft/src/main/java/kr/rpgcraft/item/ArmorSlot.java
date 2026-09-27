package kr.rpgcraft.item;

public enum ArmorSlot {
    HELMET("모자"), CHEST("갑옷"), LEGS("바지"), BOOTS("신발");

    public final String label;

    ArmorSlot(String label) {
        this.label = label;
    }
}
