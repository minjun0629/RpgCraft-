package kr.rpgcraft.economy;

import kr.rpgcraft.Keys;
import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.gui.Gui;
import kr.rpgcraft.item.Category;
import kr.rpgcraft.item.ItemData;
import kr.rpgcraft.item.ItemTemplate;
import kr.rpgcraft.util.Text;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.*;

/** shops.yml 기반 상점. NPC(주민) 또는 명령어로 연다. 떠돌이 상인은 회차별 한정 재고를 가진다. */
public class ShopManager implements Listener {
    public record Entry(String id, long buy, long sell) {}

    public static class Shop {
        public String id, title, npcName;
        public boolean command;
        public double multiplier = 1;
        public final List<Entry> entries = new ArrayList<>();
        public final Map<String, Integer> limited = new HashMap<>();
    }

    private final RpgCraft plugin;
    private final Map<String, Shop> shops = new LinkedHashMap<>();

    public ShopManager(RpgCraft plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        shops.clear();
        File file = new File(plugin.getDataFolder(), "shops.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        // 새 버전에서 추가된 상점(전리품·히든 등)을 기존 shops.yml 에 자동 추가
        try (var in = plugin.getResource("shops.yml")) {
            if (in != null) {
                YamlConfiguration def = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
                boolean added = false;
                for (String k : def.getKeys(false)) if (!y.contains(k)) { y.set(k, def.get(k)); added = true; }
                if (added) y.save(file);
            }
        } catch (Exception ignored) {
        }
        // 잡화 상점: 양털 제거, 보트 추가 (기존 파일도)
        java.util.List<String> gen = y.getStringList("general.items");
        if (!gen.isEmpty()) {
            boolean ch = gen.removeIf(x -> x.startsWith("van_wool") || x.startsWith("boat"));   // 보트 대신 물갈퀴 신발
            if (gen.stream().noneMatch(x -> x.startsWith("boots_flipper"))) { gen.add("boots_flipper"); ch = true; }
            if (ch) { y.set("general.items", gen); try { y.save(file); } catch (Exception ignored) { } }
        }
        for (String id : y.getKeys(false)) {
            ConfigurationSection s = y.getConfigurationSection(id);
            if (s == null) continue;
            Shop sh = new Shop();
            sh.id = id;
            sh.title = s.getString("title", id);
            sh.npcName = s.getString("npc-name", sh.title);
            sh.command = s.getBoolean("command", false);
            sh.multiplier = s.getDouble("multiplier", 1);
            for (String line : s.getStringList("items")) {
                String[] p = line.split(":");
                ItemTemplate t = plugin.items().get(p[0]);
                if (t == null) {
                    plugin.getLogger().warning("상점 " + id + ": 알 수 없는 아이템 " + p[0]);
                    continue;
                }
                long buy = p.length > 1 ? Text.parseLong(p[1], t.buy) : t.buy;
                long sell = p.length > 2 ? Text.parseLong(p[2], t.sell) : t.sell;
                sh.entries.add(new Entry(p[0], buy, sell));
            }
            ConfigurationSection lim = s.getConfigurationSection("limited");
            if (lim != null) for (String k : lim.getKeys(false)) sh.limited.put(k, lim.getInt(k));
            shops.put(id, sh);
        }
        Shop lootShop = shops.get("loot");
        if (lootShop != null) {
            Set<String> have = new HashSet<>();
            for (Entry en : lootShop.entries) have.add(en.id());
            for (ItemTemplate t : plugin.items().all())
                if (t.id.startsWith("loot_") && t.sell > 0 && !have.contains(t.id)) lootShop.entries.add(new Entry(t.id, -1, t.sell));
        }
        Shop fishShop = shops.get("fish");   // 새 어종은 기존 서버의 shops.yml 에도 자동으로 매입 목록에 추가
        if (fishShop != null) {
            Set<String> have = new HashSet<>();
            for (Entry en : fishShop.entries) have.add(en.id());
            for (ItemTemplate t : plugin.items().all())
                if (t.id.startsWith("fish_") && t.sell > 0 && !have.contains(t.id)) fishShop.entries.add(new Entry(t.id, -1, t.sell));
        }
        // 전당포: 판매가가 있는 모든 전리품·재료·물고기·파편 등을 사들임 (어디서도 못 팔던 물건 해결)
        Shop pawn = new Shop();
        pawn.id = "pawn";
        pawn.title = "&8전당포 (무엇이든 매입)";
        pawn.npcName = "&6전당포 주인";
        pawn.command = true;
        pawn.multiplier = 1;
        for (ItemTemplate t : plugin.items().all())
            if (t.sell > 0 && !t.category.isEquipment()) pawn.entries.add(new Entry(t.id, -1, t.sell));
        shops.put("pawn", pawn);
    }

    public Collection<Shop> all() {
        return shops.values();
    }

    public Shop get(String id) {
        return shops.get(id);
    }

    public long buyPrice(Shop s, Entry e) {
        if (e.buy() <= 0) return -1;
        double m = s.multiplier;
        var t = plugin.items().get(e.id());
        if (t != null && (t.category.isEquipment())) m *= plugin.getConfig().getDouble("economy.equipment-price-mult", 2.5);
        return (long) Math.ceil(e.buy() * m);
    }

    public long sellPrice(Entry e) {
        if (e.sell() <= 0) return -1;
        var t = plugin.items().get(e.id());
        double m = t != null && (e.id().startsWith("loot_") || e.id().startsWith("fish_")) ? plugin.getConfig().getDouble("economy.loot-sell-mult", 0.25) : 1.0;
        if (e.id().startsWith("loot_") || e.id().startsWith("fish_")) m *= plugin.cycle() == null ? 1 : plugin.cycle().sellMult();
        return Math.max(1, Math.round(e.sell() * m));
    }

    /** 히든 상인 이번 등장 재고 (품목 → 남은 수) */
    private final Map<String, Integer> hiddenStock = new HashMap<>();

    public void rollHiddenStock() {
        hiddenStock.clear();
        Shop h = shops.get("hidden");
        if (h == null) return;
        java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
        for (Entry en : h.entries) hiddenStock.put(en.id(), r.nextDouble() < plugin.getConfig().getDouble("hidden-merchant.absent-chance", 0.35) ? 0 : 1 + r.nextInt(plugin.getConfig().getInt("hidden-merchant.max-stock", 2)));
    }

    private int stockLeft(Shop s, String item) {
        if (s.id.equals("hidden")) return hiddenStock.getOrDefault(item, 0);
        Integer lim = s.limited.get(item);
        if (lim == null) return Integer.MAX_VALUE;
        return lim - plugin.rounds().state().getInt("shop-stock." + s.id + "." + item);
    }

    private void addSold(Shop s, String item, int n) {
        if (s.id.equals("hidden")) { hiddenStock.merge(item, -n, Integer::sum); return; }
        String k = "shop-stock." + s.id + "." + item;
        plugin.rounds().state().set(k, plugin.rounds().state().getInt(k) + n);
        plugin.rounds().save();
    }

    public void open(Player p, String id, int page) {
        Shop s = shops.get(id);
        if (s == null) {
            Text.msg(p, "&c존재하지 않는 상점입니다: " + id);
            return;
        }
        new ShopGui(p, s, page).open(p);
    }

    public void buy(Player p, Shop s, Entry e, int amount) {
        plugin.data().get(p).counters.merge("shop_buys", 1.0, Double::sum);
        ItemTemplate t = plugin.items().get(e.id());
        long price = buyPrice(s, e);
        if (price <= 0) {
            Text.msg(p, "&c구매할 수 없는 아이템입니다.");
            return;
        }
        if (t.category.isEquipment() || t.category == Category.RUNE || t.category == Category.TOTEM) amount = 1;
        amount = Math.min(amount, stockLeft(s, e.id()));
        if (amount <= 0) {
            Text.msg(p, s.id.equals("hidden") ? "&c품절입니다." : "&c이번 회차 재고가 모두 소진되었습니다.");
            return;
        }
        long total = price * amount;
        if (!plugin.economy().take(p, total)) {
            Text.msg(p, "&c소지금이 부족합니다. (" + Text.money(total) + ")");
            return;
        }
        ItemStack it = plugin.items().create(e.id(), amount);
        for (ItemStack left : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
        if (s.limited.containsKey(e.id())) addSold(s, e.id(), amount);
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
        Text.actionBar(p, "&a구매: " + t.name + " x" + amount + " &7(-" + Text.money(total) + ")");
    }

    public void sell(Player p, Entry e, boolean all) {
        if (e.sell() <= 0) {
            Text.msg(p, "&c판매할 수 없는 아이템입니다.");
            return;
        }
        int sold = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (!ItemData.is(it, e.id()) || ItemData.enh(it) > 0) continue;
            int n = all ? it.getAmount() : Math.min(1 - sold, it.getAmount());
            it.setAmount(it.getAmount() - n);
            sold += n;
            if (!all && sold >= 1) break;
        }
        if (sold == 0) {
            Text.msg(p, "&c판매할 아이템이 없습니다. (강화된 장비는 판매 불가)");
            return;
        }
        plugin.economy().give(p, sellPrice(e) * sold);
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 0.8f);
        Text.actionBar(p, "&e판매: " + plugin.items().get(e.id()).name + " x" + sold + " &7(+" + Text.money(sellPrice(e) * sold) + ")");
    }

    // ------------------------------------------------------------------ NPC
    public Villager spawnNpc(Location l, String shopId) {
        Shop s = shops.get(shopId);
        if (s == null) return null;
        return l.getWorld().spawn(l, Villager.class, v -> {
            v.setAI(false);
            v.setInvulnerable(true);
            v.setSilent(true);
            v.setPersistent(true);
            v.setRemoveWhenFarAway(false);
            v.setCollidable(false);
            v.setCustomName(Text.c(s.npcName));
            v.setCustomNameVisible(true);
            v.setProfession(Villager.Profession.CARTOGRAPHER);
            v.getPersistentDataContainer().set(Keys.NPC_SHOP, PersistentDataType.STRING, shopId);
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onNpc(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Entity en = e.getRightClicked();
        String id = en.getPersistentDataContainer().get(Keys.NPC_SHOP, PersistentDataType.STRING);
        if (id == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (p.isSneaking() && p.hasPermission("rpgcraft.admin") && p.getInventory().getItemInMainHand().getType() == Material.BARRIER) {
            en.remove();
            Text.msg(p, "&cNPC를 제거했습니다.");
            return;
        }
        open(p, id, 0);
    }

    // ------------------------------------------------------------------ GUI
    private class ShopGui extends Gui {
        ShopGui(Player p, Shop s, int page) {
            super(6, s.title, "shop");
            int pages = Math.max(1, (s.entries.size() + 44) / 45);
            int from = page * 45;
            for (int i = 0; i < 45 && from + i < s.entries.size(); i++) {
                Entry en = s.entries.get(from + i);
                ItemStack icon = plugin.items().create(en.id(), 1);
                ItemMeta m = icon.getItemMeta();
                var tpl = plugin.items().get(en.id());
                boolean equip = tpl != null && (tpl.category.isEquipment() || tpl.category == kr.rpgcraft.item.Category.BOW)
                        && !plugin.getConfig().getStringList("shops.show-stats").contains(s.id);   // 지정한 상점은 능력치 전부 표시
                List<String> lore = equip ? new ArrayList<>() : m.hasLore() ? new ArrayList<>(m.getLore()) : new ArrayList<>();   // 장비는 능력치 숨김
                if (equip) {   // 능력치는 숨기되 스탯 제한은 표시
                    var bs = ItemData.baseStats(icon);
                    List<String> req = new ArrayList<>();
                    if (bs.get(kr.rpgcraft.stat.Stat.REQ_STR) > 0) req.add("힘 " + (int) bs.get(kr.rpgcraft.stat.Stat.REQ_STR));
                    if (bs.get(kr.rpgcraft.stat.Stat.REQ_DEX) > 0) req.add("민첩 " + (int) bs.get(kr.rpgcraft.stat.Stat.REQ_DEX));
                    if (bs.get(kr.rpgcraft.stat.Stat.REQ_ADV) > 0) req.add("모험 " + (int) bs.get(kr.rpgcraft.stat.Stat.REQ_ADV));
                    lore.add(Text.c(req.isEmpty() ? "&7요구 스탯 없음" : "&c요구: &f" + String.join(" &7· &f", req)));
                }
                lore.add("");
                long bp = buyPrice(s, en);
                if (bp > 0) lore.add(Text.c("&a구매가: &f" + Text.money(bp) + " &7(좌클릭 1개 / 쉬프트 10개)"));
                if (en.sell() > 0) lore.add(Text.c("&e판매가: &f" + Text.money(sellPrice(en)) + " &7(우클릭 1개 / 쉬프트 전부)"));
                int left = stockLeft(s, en.id());
                if (s.id.equals("hidden") && left <= 0) { lore.add(Text.c("&8이번엔 팔지 않음 · 품절")); }
                if (left != Integer.MAX_VALUE) lore.add(Text.c(s.id.equals("hidden") ? "&c남은 수량: " + Math.max(0, left) : "&c이번 회차 남은 재고: " + Math.max(0, left)));
                m.setLore(lore);
                icon.setItemMeta(m);
                set(i, icon, e -> {
                    if (e.isLeftClick()) buy(p, s, en, e.isShiftClick() ? 10 : 1);
                    else if (e.isRightClick()) sell(p, en, e.isShiftClick());
                    new ShopGui(p, s, page).open(p);
                });
            }
            if (page > 0) set(45, button(Material.ARROW, "&f이전 페이지"), e -> new ShopGui(p, s, page - 1).open(p));
            if (page + 1 < pages) set(53, button(Material.ARROW, "&f다음 페이지"), e -> new ShopGui(p, s, page + 1).open(p));
            set(51, button(Material.HOPPER, "&6전체 판매", "&7인벤토리에서 이 상점이 사는 물건을 모두 판매"), e -> {
                Map<String, Entry> sellable = new HashMap<>();
                for (Entry en : s.entries) if (en.sell() > 0) sellable.put(en.id(), en);
                long total = 0;
                int count = 0;
                ItemStack[] inv = p.getInventory().getStorageContents();
                for (int i = 0; i < inv.length; i++) {
                    ItemStack it = inv[i];
                    Entry en = sellable.get(ItemData.id(it));
                    if (en == null || ItemData.enh(it) > 0) continue;   // 강화한 장비는 보호
                    total += sellPrice(en) * it.getAmount();
                    count += it.getAmount();
                    p.getInventory().setItem(i, null);
                }
                if (count == 0) { Text.actionBar(p, "&7팔 물건이 없습니다"); return; }
                plugin.economy().give(p, total);
                p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
                Text.actionBar(p, "&a" + count + "개 판매 · " + Text.money(total));
                new ShopGui(p, s, page).open(p);
            });
            set(49, button(Material.GOLD_INGOT, "&e소지금: " + Text.money(plugin.economy().balance(p)),
                    "&7페이지 " + (page + 1) + "/" + pages + (s.multiplier != 1 ? " &c(가격 x" + s.multiplier + ")" : "")));
            fill(45, 53);
        }
    }
}
