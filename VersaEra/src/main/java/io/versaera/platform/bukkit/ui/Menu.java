package io.versaera.platform.bukkit.ui;

import io.versaera.platform.bukkit.Ui;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 임시 메뉴 (상자 창 기반). 최종 UI 는 리소스팩 폰트 · 전용 배경으로 바꿀 예정 → Feature Registry UI-01 (PARTIAL).
 * 규칙: 아이콘 + 짧은 이름 + 숫자. 설명 문장은 쓰지 않는다. 모든 클릭은 취소되고, 정해진 칸만 동작한다.
 */
public class Menu implements InventoryHolder {
    private final Inventory inv;
    private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();

    public Menu(int rows, String title) {
        inv = Bukkit.createInventory(this, rows * 9, Ui.c(title));
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    public static ItemStack icon(Material m, String name, List<String> lines) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(Ui.c(name));
        meta.setLore(lines.stream().map(Ui::c).toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        it.setItemMeta(meta);
        return it;
    }

    public void set(int slot, ItemStack icon, Consumer<InventoryClickEvent> action) {
        inv.setItem(slot, icon);
        if (action == null) actions.remove(slot);
        else actions.put(slot, action);
    }

    public void open(Player p) {
        p.openInventory(inv);
    }

    /** MenuListener 가 부른다 */
    public void click(InventoryClickEvent e) {
        e.setCancelled(true);
        if (e.getClickedInventory() != inv) return;
        Consumer<InventoryClickEvent> a = actions.get(e.getRawSlot());
        if (a != null) a.accept(e);
    }

    public void closed(Player p) {
    }
}
