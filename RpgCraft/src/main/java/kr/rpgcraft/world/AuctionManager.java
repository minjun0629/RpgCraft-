package kr.rpgcraft.world;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.gui.Gui;
import kr.rpgcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * 옥션 (/옥션)
 *  /옥션                 : 목록 (좌클릭 구매)
 *  /옥션 등록 <가격>      : 손에 든 아이템 등록 (기본 48시간, 1인 10개)
 *  /옥션 내물건           : 내 등록 목록 (클릭하면 취소 → 보관함)
 *  /옥션 수령             : 판매 대금 · 돌려받은 아이템 받기
 * 판매 대금은 수수료 5% 를 뺀 금액. 기간이 지나면 보관함으로 돌아간다. (auctions.yml 에 저장)
 */
public class AuctionManager implements CommandExecutor {
    private static class Listing {
        String id;
        UUID seller;
        String sellerName;
        ItemStack item;
        long price, expires;
    }

    private final RpgCraft plugin;
    private final File file;
    private final LinkedHashMap<String, Listing> list = new LinkedHashMap<>();
    private final Map<UUID, Long> pendingMoney = new HashMap<>();
    private final Map<UUID, List<ItemStack>> pendingItems = new HashMap<>();

    public AuctionManager(RpgCraft plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "auctions.yml");
        load();
        Bukkit.getScheduler().runTaskTimer(plugin, this::expire, 20L * 60, 20L * 60);
    }

    // ------------------------------------------------------------------ 저장
    private void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        var ls = y.getConfigurationSection("listings");
        if (ls != null) for (String id : ls.getKeys(false)) {
            Listing l = new Listing();
            l.id = id;
            l.seller = UUID.fromString(ls.getString(id + ".seller"));
            l.sellerName = ls.getString(id + ".name", "?");
            l.item = ls.getItemStack(id + ".item");
            l.price = ls.getLong(id + ".price");
            l.expires = ls.getLong(id + ".expires");
            if (l.item != null) list.put(id, l);
        }
        var pm = y.getConfigurationSection("money");
        if (pm != null) for (String u : pm.getKeys(false)) pendingMoney.put(UUID.fromString(u), pm.getLong(u));
        var pi = y.getConfigurationSection("items");
        if (pi != null) for (String u : pi.getKeys(false)) {
            List<ItemStack> items = new ArrayList<>();
            for (Object o : pi.getList(u, List.of())) if (o instanceof ItemStack it) items.add(it);
            pendingItems.put(UUID.fromString(u), items);
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Listing l : list.values()) {
            String k = "listings." + l.id;
            y.set(k + ".seller", l.seller.toString());
            y.set(k + ".name", l.sellerName);
            y.set(k + ".item", l.item);
            y.set(k + ".price", l.price);
            y.set(k + ".expires", l.expires);
        }
        pendingMoney.forEach((u, v) -> y.set("money." + u, v));
        pendingItems.forEach((u, v) -> y.set("items." + u, v));
        try {
            y.save(file);
        } catch (IOException ignored) {
        }
    }

    /** 게임 전체 초기화: 올라온 물건 · 받지 않은 판매 대금 · 돌려받을 물건 모두 삭제 */
    public int clearAll() {
        int n = list.size();
        list.clear();
        pendingMoney.clear();
        pendingItems.clear();
        save();
        return n;
    }

    /** 한 사람 초기화: 그 사람이 올린 물건과 받지 않은 대금 · 물건 삭제 */
    public int clearPlayer(UUID id) {
        int before = list.size();
        list.values().removeIf(l -> l.seller.equals(id));
        pendingMoney.remove(id);
        pendingItems.remove(id);
        save();
        return before - list.size();
    }

    private void expire() {
        long now = System.currentTimeMillis();
        boolean ch = false;
        for (Iterator<Listing> it = list.values().iterator(); it.hasNext(); ) {
            Listing l = it.next();
            if (l.expires > now) continue;
            it.remove();
            pendingItems.computeIfAbsent(l.seller, k -> new ArrayList<>()).add(l.item);
            ch = true;
            Player s = Bukkit.getPlayer(l.seller);
            if (s != null) Text.msg(s, "&7옥션 등록 기간이 끝나 보관함으로 돌아왔습니다. &e/옥션 수령");
        }
        if (ch) save();
    }

    // ------------------------------------------------------------------ 동작
    private void register(Player p, String priceText) {
        long price;
        try {
            price = Long.parseLong(priceText.replace(",", ""));
        } catch (NumberFormatException e) {
            Text.msg(p, "&c/옥션 등록 <가격>");
            return;
        }
        if (price <= 0 || price > 1_000_000_000_000L) { Text.msg(p, "&c올바른 가격을 입력하세요."); return; }
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) { Text.msg(p, "&c등록할 아이템을 손에 드세요."); return; }
        long mine = list.values().stream().filter(l -> l.seller.equals(p.getUniqueId())).count();
        if (mine >= plugin.getConfig().getInt("auction.max-listings", 10)) { Text.msg(p, "&c더 등록할 수 없습니다."); return; }
        Listing l = new Listing();
        l.id = Long.toString(System.nanoTime(), 36);
        l.seller = p.getUniqueId();
        l.sellerName = Text.name(p);
        l.item = hand.clone();
        l.price = price;
        l.expires = System.currentTimeMillis() + plugin.getConfig().getLong("auction.hours", 48) * 3_600_000L;
        p.getInventory().setItemInMainHand(null);
        list.put(l.id, l);
        save();
        Text.msg(p, "&a옥션에 등록했습니다: &f" + Text.money(price));
    }

    private void buy(Player p, Listing l) {
        if (!list.containsKey(l.id)) { Text.actionBar(p, "&c이미 팔린 물건입니다"); return; }
        if (l.seller.equals(p.getUniqueId())) { Text.actionBar(p, "&c내 물건은 살 수 없습니다"); return; }
        if (!plugin.economy().take(p, l.price)) { Text.actionBar(p, "&c돈이 부족합니다"); return; }
        list.remove(l.id);
        for (ItemStack left : p.getInventory().addItem(l.item.clone()).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
        long got = Math.round(l.price * (1 - plugin.getConfig().getDouble("auction.fee", 0.05)));
        Player s = Bukkit.getPlayer(l.seller);
        if (s != null) {
            plugin.economy().give(s, got);
            Text.msg(s, "&a옥션 판매 완료! &f+" + Text.money(got));
        } else pendingMoney.merge(l.seller, got, Long::sum);
        save();
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        Text.msg(p, "&a구매했습니다: &f" + Text.money(l.price));
    }

    private void claim(Player p) {
        Long m = pendingMoney.remove(p.getUniqueId());
        List<ItemStack> items = pendingItems.remove(p.getUniqueId());
        if (m == null && (items == null || items.isEmpty())) { Text.msg(p, "&7받을 것이 없습니다."); return; }
        if (m != null) { plugin.economy().give(p, m); Text.msg(p, "&a판매 대금 " + Text.money(m) + " 수령"); }
        if (items != null) for (ItemStack it : items)
            for (ItemStack left : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
        save();
    }

    // ------------------------------------------------------------------ 회수 (v5.2.6)
    /** 인벤토리에 넣고, 자리가 없으면 보관함으로 (땅에 떨어뜨리지 않음). @return 보관함으로 간 수 */
    private int giveOrStore(Player p, ItemStack it) {
        int stored = 0;
        for (ItemStack left : p.getInventory().addItem(it.clone()).values()) {
            pendingItems.computeIfAbsent(p.getUniqueId(), k -> new ArrayList<>()).add(left);
            stored++;
        }
        return stored;
    }

    /** 내가 올린 물건 하나를 내려서 바로 돌려받음 */
    private void retrieve(Player p, Listing l) {
        if (!l.seller.equals(p.getUniqueId()) || list.remove(l.id) == null) { Text.actionBar(p, "&c이미 팔렸거나 없는 물건입니다"); return; }
        int stored = giveOrStore(p, l.item);
        save();
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
        Text.msg(p, stored > 0 ? "&e인벤토리가 가득 차 보관함으로 옮겼습니다. &f/옥션 수령" : "&a옥션에서 회수했습니다: &f" + name(l.item));
    }

    /** 내가 올린 물건 전부 회수 */
    private void retrieveAll(Player p) {
        int n = 0, stored = 0;
        for (Iterator<Listing> it = list.values().iterator(); it.hasNext(); ) {
            Listing l = it.next();
            if (!l.seller.equals(p.getUniqueId())) continue;
            it.remove();
            n++;
            stored += giveOrStore(p, l.item);
        }
        if (n == 0) { Text.msg(p, "&7옥션에 올린 물건이 없습니다."); return; }
        save();
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 0.9f);
        Text.msg(p, "&a옥션에 올린 물건 " + n + "개를 회수했습니다." + (stored > 0 ? " &e(인벤토리가 가득 차 일부는 보관함에: /옥션 수령)" : ""));
    }

    private static String name(ItemStack it) {
        ItemMeta m = it.getItemMeta();
        return (m != null && m.hasDisplayName() ? m.getDisplayName() : it.getType().name()) + (it.getAmount() > 1 ? Text.c(" &7x" + it.getAmount()) : "");
    }

    private ItemStack icon(Listing l, String... extra) {
        ItemStack it = l.item.clone();
        ItemMeta m = it.getItemMeta();
        List<String> lore = m != null && m.hasLore() ? new ArrayList<>(m.getLore()) : new ArrayList<>();
        lore.add("");
        lore.add(Text.c("&e가격: &f" + Text.money(l.price)));
        lore.add(Text.c("&7판매자: " + l.sellerName));
        long left = Math.max(0, l.expires - System.currentTimeMillis()) / 60000;
        lore.add(Text.c("&7남은 시간: " + left / 60 + "시간 " + left % 60 + "분"));
        for (String x : extra) lore.add(Text.c(x));
        if (m != null) { m.setLore(lore); it.setItemMeta(m); }
        return it;
    }

    private class BrowseGui extends Gui {
        BrowseGui(Player p, int page, boolean mineOnly) {
            super(6, mineOnly ? "&8옥션 · 내 물건" : "&8옥션");
            List<Listing> ls = new ArrayList<>();
            for (Listing l : list.values()) if (!mineOnly || l.seller.equals(p.getUniqueId())) ls.add(l);
            int pages = Math.max(1, (ls.size() + 44) / 45);
            for (int i = 0; i < 45 && page * 45 + i < ls.size(); i++) {
                Listing l = ls.get(page * 45 + i);
                boolean mine = l.seller.equals(p.getUniqueId());
                set(i, icon(l, mine ? "&c▶ 클릭: 회수 (등록 취소 후 바로 돌려받기)" : "&a▶ 좌클릭: 구매"), e -> {
                    if (mine) retrieve(p, l);
                    else if (e.isLeftClick()) buy(p, l);
                    new BrowseGui(p, page, mineOnly).open(p);
                });
            }
            if (page > 0) set(45, button(Material.ARROW, "&f이전"), e -> new BrowseGui(p, page - 1, mineOnly).open(p));
            if (page + 1 < pages) set(53, button(Material.ARROW, "&f다음"), e -> new BrowseGui(p, page + 1, mineOnly).open(p));
            set(48, button(Material.CHEST, "&e내 물건", "&7/옥션 등록 <가격> 으로 등록", "&7내 물건을 클릭하면 회수"), e -> new BrowseGui(p, 0, true).open(p));
            if (mineOnly) set(47, button(Material.BARREL, "&c모두 회수", "&7옥션에 올린 물건을 전부 내려 돌려받습니다"), e -> { retrieveAll(p); new BrowseGui(p, 0, true).open(p); });
            set(49, button(Material.GOLD_INGOT, "&6소지금 " + Text.money(plugin.economy().balance(p)), "&7" + (page + 1) + " / " + pages), null);
            set(50, button(Material.HOPPER, "&a수령하기", "&7판매 대금 · 돌려받은 물건"), e -> { p.closeInventory(); claim(p); });
            fill(45, 53);
        }
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!(s instanceof Player p)) return true;
        if (a.length >= 2 && (a[0].equals("등록") || a[0].equals("sell"))) register(p, a[1]);
        else if (a.length >= 1 && (a[0].equals("수령") || a[0].equals("claim"))) claim(p);
        else if (a.length >= 1 && (a[0].equals("내물건") || a[0].equals("mine"))) new BrowseGui(p, 0, true).open(p);
        else if (a.length >= 1 && (a[0].equals("회수") || a[0].equals("취소") || a[0].equals("cancel"))) retrieveAll(p);
        else new BrowseGui(p, 0, false).open(p);
        return true;
    }

    public void onJoinNotice(Player p) {
        if (pendingMoney.containsKey(p.getUniqueId()) || pendingItems.containsKey(p.getUniqueId()))
            Text.msg(p, "&e옥션 보관함에 받을 것이 있습니다. &f/옥션 수령");
    }
}
