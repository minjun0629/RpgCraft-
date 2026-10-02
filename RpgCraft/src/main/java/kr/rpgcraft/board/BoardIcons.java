package kr.rpgcraft.board;

import kr.rpgcraft.util.Text;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** v5.10.45 보드게임 그림 (PAPER CustomModelData 13400 + 번호, tools/board_art.py 와 번호가 같아야 함) */
public final class BoardIcons {
    private BoardIcons() {}

    public static final int BASE = 13400;
    // 윷놀이
    public static final int YUT_STATION = 0, YUT_CORNER = 1, YUT_CENTER = 2, YUT_ME = 3, YUT_AI = 4, YUT_BOTH = 5, YUT_RESULT = 6,   // 결과: 도 개 걸 윷 모 빽도 (+0~5)
            YUT_THROW = 12, YUT_HOME_ME = 13, YUT_HOME_AI = 14, YUT_GOAL = 15, YUT_SELECT = 16;
    // 부루마블
    public static final int CITY = 20, CITY_ME = 28, CITY_AI = 36,   // + 색 무리 0~7
            START = 44, ISLAND = 45, CHANCE = 46, TRAVEL = 47, FUND = 48, TOK_ME = 49, TOK_AI = 50, TOK_BOTH = 51, DICE = 51,   // DICE + 1~6
            ROLL = 58, MONEY = 59;
    // 인디언 포커
    public static final int CARD_BACK = 60, CARD = 60, CHIP = 71, CALL = 72, FOLD = 73;   // CARD + 1~10
    // PVP 명성 등급 · 허브
    public static final int TIER = 80, BOARD_CHIP = 87, HUB_YUT = 88, HUB_MARBLE = 89, HUB_POKER = 90, HUB_DUEL = 91;
    // v5.10.59 여러 명 (자리 0~3: 빨강 · 파랑 · 초록 · 노랑)
    public static final int YUT_SEAT = 100, YUT_MULTI = 104, YUT_HOME_SEAT = 105, TOK_SEAT = 110, TOK_MULTI = 114, CITY_SEAT = 120,   // CITY_SEAT + 자리 × 8 + 색 무리
            ROOM = 160, ROOM_BOT = 161, ROOM_START = 162;

    public static ItemStack of(int off, String name, String... lore) {
        return of(off, 1, name, lore);
    }

    public static ItemStack of(int off, int amount, String name, String... lore) {
        ItemStack it = new ItemStack(Material.PAPER, Math.max(1, Math.min(64, amount)));
        ItemMeta m = it.getItemMeta();
        m.setCustomModelData(BASE + off);
        m.setDisplayName(Text.c(name));
        List<String> l = new ArrayList<>();
        for (String s : lore) l.add(Text.c(s));
        m.setLore(l);
        m.addItemFlags(ItemFlag.values());
        it.setItemMeta(m);
        return it;
    }

    public static ItemStack of(int off, String name, List<String> lore) {
        return of(off, 1, name, lore.toArray(new String[0]));
    }

    public static ItemStack of(int off, int amount, String name, List<String> lore) {
        return of(off, amount, name, lore.toArray(new String[0]));
    }
}
