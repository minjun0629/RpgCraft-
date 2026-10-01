package kr.rpgcraft.feature;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.data.PlayerData;
import kr.rpgcraft.gui.Gui;
import kr.rpgcraft.gui.UiIcon;
import kr.rpgcraft.stat.StatSnapshot;
import kr.rpgcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * v5.10.34 마크에이지 4R 처럼 인벤토리(E) 위 2x2 제작 칸에 스탯을 배치.
 *  제작 칸 1 힘 · 2 민첩 · 3 모험 · 4 남은 포인트, 결과 칸 = 현재 능력치 (클릭하면 스탯 창).
 *  좌클릭 +1 · 우클릭 +10 · 쉬프트 전부. 칸의 아이콘은 진짜 아이템이 아니라서 창을 닫거나 죽으면 지우고, 혹시 새어 나오면 바로 없앤다.
 */
public class InvStatManager implements Listener {
    private final RpgCraft plugin;
    private final NamespacedKey KEY;

    public InvStatManager(RpgCraft plugin) {
        this.plugin = plugin;
        this.KEY = new NamespacedKey(plugin, "invstat");
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> { for (Player p : Bukkit.getOnlinePlayers()) apply(p); }, 40L, 20L);
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("inventory-stats.enabled", true);
    }

    private boolean isOwnInv(InventoryView v) {
        return v != null && v.getTopInventory().getType() == InventoryType.CRAFTING;
    }

    private ItemStack tag(ItemStack it) {
        ItemMeta m = it.getItemMeta();
        m.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    public boolean isIcon(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(KEY, PersistentDataType.BYTE);
    }

    private ItemStack statIcon(UiIcon ui, String name, int invested, double total, List<String> body, int points) {
        List<String> lore = new ArrayList<>();
        lore.add("&f투자 &e" + invested + " &7· 최종 &f" + (int) total);
        lore.add("");
        lore.addAll(body);
        lore.add("");
        lore.add(points > 0 ? "&a좌클릭 +1 &7· &a우클릭 +10 &7· &a쉬프트 전부" : "&8남은 포인트가 없습니다");
        ItemStack it = Gui.ui(ui, true, name, lore.toArray(new String[0]));
        it.setAmount(Math.max(1, Math.min(64, invested)));
        return tag(it);
    }

    /** 지금 인벤토리 창의 제작 칸에 스탯 아이콘을 채움 (바뀐 칸만) */
    public void apply(Player p) {
        if (!enabled() || !p.isOnline() || p.isDead() || p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) return;
        InventoryView v = p.getOpenInventory();
        if (!isOwnInv(v) || !plugin.data().isLoaded(p.getUniqueId())) return;
        if (v.getCursor() != null && isIcon(v.getCursor())) v.setCursor(null);
        PlayerData d = plugin.data().get(p);
        StatSnapshot s = d.stats;
        var c = plugin.getConfig();
        int pts = d.statPoints;
        ItemStack str = statIcon(UiIcon.STAT_STR, "&c&l힘", d.str, s.str, List.of("&72포인트당 공격력 +" + c.getDouble("player.str-atk-per-2", 5),
                "&71포인트당 마력 +" + c.getDouble("player.str-magic-per-point", 2)), pts);
        ItemStack dex = statIcon(UiIcon.STAT_DEX, "&9&l민첩", d.dex, s.dex, List.of("&71포인트당 치명타 +" + c.getDouble("player.dex-crit-per-point", 0.12) + "%",
                "&7치명타 피해 +" + c.getDouble("player.dex-critdmg-per-point", 0.6) + "%"), pts);
        ItemStack adv = statIcon(UiIcon.STAT_ADV, "&a&l모험", d.adv, s.adv, List.of("&71포인트당 체력 +" + String.format("%.1f", c.getDouble("player.adv-hp-per-point", 90) * plugin.stats().hpScale()),
                "&7방어력 +" + c.getDouble("player.adv-def-per-point", 0.065) + "%"), pts);
        ItemStack point = tag(Gui.ui(UiIcon.STAT_POINT, true, "&e&l어빌리티 포인트 &f" + pts, "&7레벨업마다 +" + c.getInt("player.stat-per-level", 5),
                pts > 0 ? "&a힘 · 민첩 · 모험 칸을 눌러 분배" : "&7레벨을 올리면 포인트를 얻습니다"));
        point.setAmount(Math.max(1, Math.min(64, pts)));
        ItemStack info = tag(Gui.ui(UiIcon.STAT_INFO, true, "&f&l능력치 &7(Lv." + d.level + ")",
                "&f공격력 &6" + Text.num(s.attack) + "  &f체력 &c" + Text.num(s.maxHp),
                "&f마력 &d" + Text.num(s.magic) + "  &f크리 &e" + String.format("%.1f", s.crit) + "%",
                "&f방어 &b" + String.format("%.1f", s.def) + "%  &f흡혈 &c" + String.format("%.1f", s.lifesteal) + "%",
                "&f회피 &b" + String.format("%.1f", s.dodge) + "%  &f관통 &b" + String.format("%.1f", s.armorPen) + "%",
                "", "&e▶ 클릭: 자세한 스탯 창"));
        ItemStack[] want = {info, str, dex, adv, point};
        for (int i = 0; i < 5; i++) {
            ItemStack cur = v.getItem(i);
            if (cur != null && cur.isSimilar(want[i]) && cur.getAmount() == want[i].getAmount()) continue;
            if (cur != null && !cur.getType().isAir() && !isIcon(cur)) {   // 제작 칸에 진짜 아이템이 있으면 가방으로 돌려줌
                for (ItemStack left : p.getInventory().addItem(cur).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
            }
            v.setItem(i, want[i]);
        }
    }

    public void refreshSoon(Player p) {
        Bukkit.getScheduler().runTask(plugin, () -> apply(p));
    }

    private void clear(Player p) {
        InventoryView v = p.getOpenInventory();
        if (!isOwnInv(v)) return;
        for (int i = 0; i < 5; i++) if (isIcon(v.getItem(i))) v.setItem(i, null);
    }

    /** 혹시 가방 · 커서로 새어 나온 아이콘 지우기 */
    private void purge(Player p) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) if (isIcon(inv.getItem(i))) inv.setItem(i, null);
        if (isIcon(p.getItemOnCursor())) p.setItemOnCursor(null);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (isIcon(e.getCurrentItem()) && (!isOwnInv(e.getView()) || e.getRawSlot() > 4)) {   // 새어 나온 아이콘
            e.setCancelled(true);
            e.setCurrentItem(null);
            return;
        }
        if (!enabled() || !isOwnInv(e.getView())) return;
        if (e.getClick().isKeyboardClick() && e.getRawSlot() <= 4) { e.setCancelled(true); return; }
        int raw = e.getRawSlot();
        if (raw < 0 || raw > 4) return;
        e.setCancelled(true);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (isIcon(p.getItemOnCursor())) p.setItemOnCursor(null);
            if (raw == 0) { p.performCommand("stat"); return; }
            if (raw == 4) { apply(p); return; }
            if (plugin.playerCommands() != null) plugin.playerCommands().allocateStat(p, raw - 1, e.isShiftClick(), e.isRightClick());
            apply(p);
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent e) {
        if (!isOwnInv(e.getView())) return;
        for (int raw : e.getRawSlots()) if (raw <= 4) { e.setCancelled(true); return; }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        if (isOwnInv(e.getView())) clear(p);   // 닫을 때 제작 칸 물건이 가방으로 돌아오지 않게
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline()) { purge(p); apply(p); } }, 2L);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent e) {
        clear(e.getEntity());
        e.getDrops().removeIf(this::isIcon);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (e.getPlayer().isOnline()) { purge(e.getPlayer()); apply(e.getPlayer()); } }, 30L);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent e) {
        clear(e.getPlayer());
    }

    public void shutdown() {
        for (Player p : Bukkit.getOnlinePlayers()) clear(p);
    }
}
