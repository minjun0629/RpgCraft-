package kr.rpgcraft.util;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class ItemBuilder {
    private final ItemStack item;
    private final ItemMeta meta;

    public ItemBuilder(Material m) {
        this(m, 1);
    }

    public ItemBuilder(Material m, int amount) {
        item = new ItemStack(m, Math.max(1, amount));
        meta = item.getItemMeta();
    }

    public ItemBuilder(ItemStack base) {
        item = base.clone();
        meta = item.getItemMeta();
    }

    public ItemBuilder name(String n) {
        meta.setDisplayName(Text.c(n));
        return this;
    }

    public ItemBuilder lore(String... lines) {
        List<String> l = new ArrayList<>();
        for (String s : lines) l.add(Text.c(s));
        meta.setLore(l);
        return this;
    }

    public ItemBuilder lore(List<String> lines) {
        meta.setLore(Text.c(lines));
        return this;
    }

    public ItemBuilder addLore(List<String> lines) {
        List<String> l = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        l.addAll(Text.c(lines));
        meta.setLore(l);
        return this;
    }

    public ItemBuilder glow(boolean glow) {
        if (glow) {
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        return this;
    }

    public ItemBuilder hideAll() {
        meta.addItemFlags(ItemFlag.values());
        return this;
    }

    public ItemStack build() {
        item.setItemMeta(meta);
        return item;
    }
}
