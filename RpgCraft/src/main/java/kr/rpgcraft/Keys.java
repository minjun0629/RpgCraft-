package kr.rpgcraft;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/** PersistentDataContainer 키 모음 */
public final class Keys {
    public static NamespacedKey ID, STATS, ENH, GRADE, WCLASS, ASLOT, SET, SET_BONUS, SET_N, CRAFTER, CRAFTER_UUID,
            UNIQUE, VALUE, RUNE, TOTEM, NAME, LEVEL, BOSS, MINION, NPC_SHOP, ARROW_ATK, ARROW_FORCE, POWER, INDICATOR, FILLER, FISH_LEN, FISH_KG, FISH_MULT;

    private Keys() {}

    public static void init(Plugin p) {
        ID = new NamespacedKey(p, "id");
        STATS = new NamespacedKey(p, "stats");
        ENH = new NamespacedKey(p, "enh");
        GRADE = new NamespacedKey(p, "grade");
        WCLASS = new NamespacedKey(p, "wclass");
        ASLOT = new NamespacedKey(p, "aslot");
        SET = new NamespacedKey(p, "set");
        SET_BONUS = new NamespacedKey(p, "set_bonus");
        SET_N = new NamespacedKey(p, "set_n");
        CRAFTER = new NamespacedKey(p, "crafter");
        CRAFTER_UUID = new NamespacedKey(p, "crafter_uuid");
        UNIQUE = new NamespacedKey(p, "unique");
        VALUE = new NamespacedKey(p, "value");
        RUNE = new NamespacedKey(p, "rune");
        TOTEM = new NamespacedKey(p, "totem");
        NAME = new NamespacedKey(p, "custom_name");
        LEVEL = new NamespacedKey(p, "mob_level");
        BOSS = new NamespacedKey(p, "boss");
        MINION = new NamespacedKey(p, "minion");
        NPC_SHOP = new NamespacedKey(p, "npc_shop");
        ARROW_ATK = new NamespacedKey(p, "arrow_atk");
        ARROW_FORCE = new NamespacedKey(p, "arrow_force");
        POWER = new NamespacedKey(p, "power");
        INDICATOR = new NamespacedKey(p, "indicator");
        FILLER = new NamespacedKey(p, "gui_filler");
        FISH_LEN = new NamespacedKey(p, "fish_len");     // v5.6.0 물고기 길이(cm)
        FISH_KG = new NamespacedKey(p, "fish_kg");       // 무게(kg)
        FISH_MULT = new NamespacedKey(p, "fish_mult");   // 무게에 따른 판매가 배율
    }
}
