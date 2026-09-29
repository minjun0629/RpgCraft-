package kr.rpgcraft.minigame;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.data.PlayerData;
import kr.rpgcraft.gui.Gui;
import kr.rpgcraft.util.Text;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * 이벤트 광장 (v5.6.0) — /이벤트
 *  - 미니게임 4종 (두더지 잡기 · 벽돌깨기 · 지뢰찾기 · 같은 그림 찾기), 난이도 쉬움 · 보통 · 어려움
 *  - 성공하면 게임별 코인 (아이템이 아닌 디지털 재화, counters: coin_xxx). 하루에 받을 수 있는 횟수 제한 (minigames.daily-clears)
 *  - 이벤트 상점: 상점 목록 → 세부 상점 (plugins/RpgCraft/event-shop.yml 로 품목 · 가격 · 구매 제한을 바꿀 수 있음)
 */
public class MiniGameManager implements CommandExecutor {
    public record Offer(String item, int amount, long price, int limit) {}

    public record SubShop(String id, String name, Coin coin, Material icon, List<Offer> offers) {}

    private final RpgCraft plugin;
    private final List<SubShop> shops = new ArrayList<>();

    public MiniGameManager(RpgCraft plugin) {
        this.plugin = plugin;
        load();
    }

    public RpgCraft plugin() {
        return plugin;
    }

    public void load() {
        shops.clear();
        File f = new File(plugin.getDataFolder(), "event-shop.yml");
        if (!f.exists()) plugin.saveResource("event-shop.yml", false);
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        for (String id : y.getKeys(false)) {
            ConfigurationSection s = y.getConfigurationSection(id);
            if (s == null) continue;
            Coin c = Coin.of(s.getString("coin", ""));
            if (c == null) { plugin.getLogger().warning("event-shop.yml: " + id + " 의 coin 이 올바르지 않습니다"); continue; }
            Material icon = Material.matchMaterial(s.getString("icon", "CHEST"));
            List<Offer> offers = new ArrayList<>();
            for (Map<?, ?> m : s.getMapList("items")) {
                String item = String.valueOf(m.get("item"));
                if (plugin.items().get(item) == null) { plugin.getLogger().warning("event-shop.yml: 없는 아이템 " + item); continue; }
                offers.add(new Offer(item, num(m.get("amount"), 1), num(m.get("price"), 1), num(m.get("limit"), 0)));
            }
            shops.add(new SubShop(id, s.getString("name", id), c, icon == null ? Material.CHEST : icon, offers));
        }
    }

    private static int num(Object o, int def) {
        if (o instanceof Number n) return n.intValue();
        try { return o == null ? def : Integer.parseInt(String.valueOf(o)); } catch (NumberFormatException e) { return def; }
    }

    // ------------------------------------------------------------------ 보상
    private long today() {
        return LocalDate.now(ZoneId.of(plugin.getConfig().getString("events.timezone", "Asia/Seoul"))).toEpochDay();
    }

    private int clearsToday(PlayerData d, Game g) {
        if (d.counters.getOrDefault("mg_day", -1.0) != today()) {
            d.counters.put("mg_day", (double) today());
            for (Game x : Game.values()) d.counters.remove("mg_clears_" + x.key());
        }
        return (int) Math.round(d.counters.getOrDefault("mg_clears_" + g.key(), 0.0));
    }

    /** 성공 보상 지급, 받은 코인 수 반환 (하루 제한을 넘으면 0) */
    long reward(Player p, Game g, Diff diff) {
        PlayerData d = plugin.data().get(p);
        int done = clearsToday(d, g);
        d.counters.merge("mg_best_" + g.key() + "_" + diff.name().toLowerCase(), 1.0, Double::sum);
        if (done >= plugin.getConfig().getInt("minigames.daily-clears", 20)) { plugin.data().save(d); return 0; }
        List<Integer> r = plugin.getConfig().getIntegerList("minigames.reward." + g.key());
        long got = r.size() >= 3 ? r.get(diff.ordinal()) : diff.pick(1, 3, 6);
        g.coin.add(d, got);
        d.counters.put("mg_clears_" + g.key(), (double) (done + 1));
        plugin.data().save(d);
        Text.msg(p, "&a" + g.label + " " + diff.color + diff.label + " &a성공! " + g.coin.color + g.coin.label + " +" + got + " &7(보유 " + g.coin.get(d) + " · 오늘 " + (done + 1) + "/" + plugin.getConfig().getInt("minigames.daily-clears", 20) + "회)");
        return got;
    }

    // ------------------------------------------------------------------ 화면
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        if (args.length > 0 && (args[0].equals("상점") || args[0].equalsIgnoreCase("shop"))) { new ShopMain(p).open(p); return true; }
        new Hub(p).open(p);
        return true;
    }

    public void openDifficulty(Player p, Game g) {
        new DiffGui(p, g).open(p);
    }

    private class Hub extends Gui {
        Hub(Player p) {
            super(6, "&8이벤트 광장", "event");
            PlayerData d = plugin.data().get(p);
            int[] top = {10, 12, 14, 16}, bottom = {28, 30, 32, 34};
            Game[] gs = Game.values();
            for (int i = 0; i < gs.length; i++) {
                Game g = gs[i];
                int left = Math.max(0, plugin.getConfig().getInt("minigames.daily-clears", 20) - clearsToday(d, g));
                set(top[i], Icons.of(g.icon, "&e&l" + g.label, "&7" + g.how, "&7" + g.goal, "", "&f오늘 보상 남은 횟수 &e" + left, "&e▶ 클릭하여 난이도 선택"), e -> openDifficulty(p, g));
                set(bottom[i], Icons.of(g.coin.icon, (int) Math.max(1, Math.min(64, g.coin.get(d))), g.coin.color + "&l" + g.coin.label + " &f" + g.coin.get(d) + "개",
                        "&7" + g.label + " 성공 보상", "&7이벤트 상점의 " + g.label.replace("같은 ", "") + " 상점에서 사용"), e -> new ShopMain(p).open(p));
            }
            set(40, Gui.button(Material.EMERALD, "&a&l이벤트 상점", "&7코인으로 여러 보상을 살 수 있습니다", "&e▶ 클릭"), e -> new ShopMain(p).open(p));
            set(49, Gui.button(Material.BOOK, "&f도움말", "&7각 게임은 쉬움 · 보통 · 어려움 3단계", "&7어려울수록 코인을 많이 줍니다",
                    "&7코인은 아이템이 아니라 계정에 쌓이는 재화", "&7창을 닫으면 게임을 그만둡니다"));
            fill(0, 53);
        }
    }

    private class DiffGui extends Gui {
        DiffGui(Player p, Game g) {
            super(3, "&8" + g.label + " - 난이도");
            set(4, Icons.of(g.icon, "&e&l" + g.label, "&7" + g.how, "&7" + g.goal));
            int[] at = {11, 13, 15};
            for (Diff df : Diff.values()) {
                List<Integer> r = plugin.getConfig().getIntegerList("minigames.reward." + g.key());
                long coins = r.size() >= 3 ? r.get(df.ordinal()) : df.pick(1, 3, 6);
                set(at[df.ordinal()], Icons.of(df.icon(), df.color + "&l" + df.label, rule(g, df), "", "&f성공 보상 " + g.coin.color + g.coin.label + " " + coins + "개", "&e▶ 클릭하여 시작"),
                        e -> start(p, g, df));
            }
            fill(0, 26);
        }
    }

    private static String rule(Game g, Diff d) {
        return switch (g) {
            case MOLE -> "&7목표 " + d.pick(12, 20, 28) + "점" + (d == Diff.EASY ? " · 폭탄 없음" : d == Diff.NORMAL ? " · 폭탄 가끔" : " · 폭탄 많음 · 헛손질 -1");
            case BREAKOUT -> "&7벽돌 " + d.pick(2, 3, 4) + "줄 · 목숨 " + d.pick(3, 2, 1) + (d == Diff.HARD ? " · 강철 벽돌 · 짧은 받침대" : "");
            case MINES -> "&79x5 판 · 지뢰 " + d.pick(5, 8, 11) + "개";
            case MEMORY -> "&7" + d.pick(6, 10, 14) + "쌍 · 제한 " + d.pick(90, 120, 150) + "초";
        };
    }

    private void start(Player p, Game g, Diff d) {
        MiniGame mg = switch (g) {
            case MOLE -> new MoleGame(this, p, d);
            case BREAKOUT -> new BreakoutGame(this, p, d);
            case MINES -> new MinesGame(this, p, d);
            case MEMORY -> new MemoryGame(this, p, d);
        };
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.2f);
        mg.begin();
    }

    // ------------------------------------------------------------------ 이벤트 상점
    private class ShopMain extends Gui {
        ShopMain(Player p) {
            super(6, "&8이벤트 상점", "eshop");
            PlayerData d = plugin.data().get(p);
            int[] at = {19, 21, 23, 25};
            for (int i = 0; i < shops.size() && i < at.length; i++) {
                SubShop s = shops.get(i);
                set(at[i], Gui.button(s.icon(), "&e&l" + s.name(), "&7" + s.coin().color + s.coin().label + "&7로 사는 상점", "&7품목 " + s.offers().size() + "개",
                        "", "&f보유 " + s.coin().color + s.coin().get(d) + "개", "&e▶ 클릭"), e -> new ShopSub(p, s).open(p));
            }
            List<String> wallet = new ArrayList<>(List.of("&7미니게임에서 모은 코인"));
            for (Coin c : Coin.values()) wallet.add(c.color + c.label + " &f" + c.get(d) + "개");
            set(40, Gui.button(Material.CHEST, "&6&l내 코인 지갑", wallet.toArray(new String[0])));
            set(49, Gui.button(Material.NOTE_BLOCK, "&a미니게임 하러 가기", "&e▶ 클릭"), e -> new Hub(p).open(p));
            fill(0, 53);
        }
    }

    private static final int[] ITEM_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};

    private class ShopSub extends Gui {
        ShopSub(Player p, SubShop s) {
            super(6, "&8" + s.name(), "eshop_sub");
            PlayerData d = plugin.data().get(p);
            List<Offer> os = s.offers();
            int start = Math.max(0, (7 - Math.min(7, os.size())) / 2);   // 한 줄이 다 차지 않으면 가운데 정렬
            for (int i = 0; i < os.size() && i < ITEM_SLOTS.length; i++) {
                Offer o = os.get(i);
                ItemStack icon = plugin.items().create(o.item(), o.amount());
                if (icon == null) continue;
                ItemMeta m = icon.getItemMeta();
                List<String> lore = m.getLore() == null ? new ArrayList<>() : new ArrayList<>(m.getLore());
                lore.add("");
                lore.add(Text.c("&f가격 " + s.coin().color + o.price() + " " + s.coin().label));
                String key = "eshop_" + s.id() + "_" + o.item();
                int bought = (int) Math.round(d.counters.getOrDefault(key, 0.0));
                if (o.limit() > 0) lore.add(Text.c("&7구매 제한 " + bought + " / " + o.limit()));
                lore.add(Text.c("&e▶ 클릭하여 구매"));
                m.setLore(lore);
                icon.setItemMeta(m);
                int slot = os.size() <= 7 ? ITEM_SLOTS[7 + start + i] : ITEM_SLOTS[i];
                set(slot, icon, e -> buy(p, s, o));
            }
            set(49, Icons.of(s.coin().icon, (int) Math.max(1, Math.min(64, s.coin().get(d))), s.coin().color + "&l보유 " + s.coin().label + " &f" + s.coin().get(d) + "개"));
            fill(0, 53);
        }
    }

    private void buy(Player p, SubShop s, Offer o) {
        PlayerData d = plugin.data().get(p);
        String key = "eshop_" + s.id() + "_" + o.item();
        int bought = (int) Math.round(d.counters.getOrDefault(key, 0.0));
        if (o.limit() > 0 && bought >= o.limit()) { Text.actionBar(p, "&c구매 제한에 도달했습니다."); return; }
        if (s.coin().get(d) < o.price()) { Text.actionBar(p, "&c" + s.coin().label + "이(가) 부족합니다. &7(" + s.coin().get(d) + " / " + o.price() + ")"); p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f); return; }
        ItemStack it = plugin.items().create(o.item(), o.amount());
        if (it == null) return;
        s.coin().add(d, -o.price());
        d.counters.put(key, (double) (bought + 1));
        for (ItemStack l : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), l);
        plugin.data().save(d);
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
        Text.actionBar(p, "&a구매: " + plugin.items().get(o.item()).name + " x" + o.amount() + " &7(-" + o.price() + " " + s.coin().label + ")");
        new ShopSub(p, s).open(p);
    }

    /** 관리자: 코인 지급 */
    public boolean giveCoins(Player target, String coin, long n) {
        Coin c = Coin.of(coin);
        if (c == null) return false;
        PlayerData d = plugin.data().get(target);
        c.add(d, n);
        plugin.data().save(d);
        return true;
    }
}
