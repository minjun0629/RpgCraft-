package kr.rpgcraft.item;

public enum Category {
    WEAPON("무기"), ARMOR("방어구"), BOW("활"), MATERIAL("재료"), TOOL("채집도구"), POTION("포션"),
    TICKET("특수"), HAMMER("성벽 수리 망치"), RUNE("룬"), TOTEM("토템"), SHARD("기운 파편"), CRYSTAL("기운 뽑기 결정"),
    ESSENCE("기운"), CHECK("수표"), VANILLA("일반");

    public final String label;

    Category(String label) {
        this.label = label;
    }

    public boolean isEquipment() {
        return this == WEAPON || this == ARMOR || this == BOW;
    }
}
