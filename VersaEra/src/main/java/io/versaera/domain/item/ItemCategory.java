package io.versaera.domain.item;

public enum ItemCategory {
    WEAPON, ARMOR, TOOL, MATERIAL, FOOD, ARTWORK, CONSUMABLE, MISC;

    /** 고유 인스턴스로 관리하는 종류 (재료 · 음식 같은 묶음 아이템은 아님) */
    public boolean unique() {
        return this == WEAPON || this == ARMOR || this == TOOL || this == ARTWORK;
    }
}
