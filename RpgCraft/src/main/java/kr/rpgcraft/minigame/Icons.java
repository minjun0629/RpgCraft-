package kr.rpgcraft.minigame;

import kr.rpgcraft.util.Text;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** 미니게임 아이콘 (PAPER + CustomModelData 12000~, 그림은 tools/minigame_art.py 와 번호가 같아야 함) */
public final class Icons {
    private Icons() {}

    public static final int BASE = 12000;
    public static final int HOLE = 0, MOLE = 1, MOLE_HIT = 2, MOLE_GOLD = 3, BOMB = 4, BOOM = 5;
    public static final int BRICK = 10, STEEL = 16, CRACKED = 17, BALL = 18, PAD_L = 19, PAD_M = 20, PAD_R = 21;
    public static final int TILE = 30, OPEN = 31, FLAG = 40, MINE = 41, MINE_BOOM = 42;
    public static final int CARD = 50, FACE = 51, FACES = 14;
    public static final int COIN = 70, EASY = 80;

    /** 지뢰 숫자 1~8 */
    public static int num(int n) {
        return OPEN + n;
    }

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
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        it.setItemMeta(m);
        return it;
    }
}
