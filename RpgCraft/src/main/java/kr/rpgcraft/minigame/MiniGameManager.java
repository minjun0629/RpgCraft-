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
 *  - 성공하면 이벤트 코인 (아이템이 아닌 디지털 재화, counters: coin_event — v5.6.4 에서 하나로 통합)
 *  - 하루에 할 수 있는 판 수 제한 (minigames.daily-plays, 기본 4종 합쳐 5판 — 시작할 때 1판 차감, 도중에 닫아도 차감) (v5.6.4)
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
        if (y.getInt("version", 1) < 3) {   // v5.7.0 품목 종류별 상점 · v5.7.2 「특별」 줄 제거 → 기본 파일로 교체. 예전 파일은 event-shop.old.yml 로 보관
            f.renameTo(new File(plugin.getDataFolder(), "event-shop.old.yml"));
            plugin.saveResource("event-shop.yml", true);
            y = YamlConfiguration.loadConfiguration(f);
            plugin.getLogger().info("이벤트 상점을 이벤트 코인 통합 상점으로 바꿨습니다 (예전 파일: event-shop.old.yml)");
        }
        for (String id : y.getKeys(false)) {
            ConfigurationSection s = y.getConfigurationSection(id);
            if (s == null) continue;
            Coin c = Coin.EVENT;   // 모든 상점이 이벤트 코인
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

    /** 날짜가 바뀌었으면 오늘 판 수 초기화 */
    private void rollDay(PlayerData d) {
        if (d.counters.getOrDefault("mg_day", -1.0) != today()) {
            d.counters.put("mg_day", (double) today());
            d.counters.remove("mg_plays_all");
            for (Game x : Game.values()) { d.counters.remove("mg_plays_" + x.key()); d.counters.remove("mg_clears_" + x.key()); }
        }
    }

    /** 하루 판 수를 게임마다 따로 세는지 (per-game), 전부 합쳐 세는지 (total) */
    private boolean total() {
        return !"per-game".equalsIgnoreCase(plugin.getConfig().getString("minigames.daily-plays-scope", "total"));
    }

    private String playKey(Game g) {
        return total() ? "mg_plays_all" : "mg_plays_" + g.key();
    }

    public int dailyPlays() {
        return plugin.getConfig().getInt("minigames.daily-plays", 5);
    }

    /** 오늘 남은 판 수 */
    public int playsLeft(PlayerData d, Game g) {
        rollDay(d);
        return Math.max(0, dailyPlays() - (int) Math.round(d.counters.getOrDefault(playKey(g), 0.0)));
    }

    /** 성공 보상 지급, 받은 코인 수 반환 */
    long reward(Player p, Game g, Diff diff) {
        PlayerData d = plugin.data().get(p);
        d.counters.merge("mg_best_" + g.key() + "_" + diff.name().toLowerCase(), 1.0, Double::sum);
        List<Integer> r = plugin.getConfig().getIntegerList("minigames.reward." + g.key());
        long got = r.size() >= 3 ? r.get(diff.ordinal()) : diff.pick(1, 3, 6);
        g.coin.add(d, got);
        plugin.data().save(d);
        Text.msg(p, "&a" + g.label + " " + diff.color + diff.label + " &a성공! " + g.coin.color + g.coin.label + " +" + got + " &7(보유 " + g.coin.get(d) + " · 오늘 남은 판 " + playsLeft(d, g) + "/" + dailyPlays() + ")");
        return got;
    }

    // ------------------------------------------------------------------ 화면
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        if (args.length > 0 && (args[0].equals("상점") || args[0].equalsIgnoreCase("shop"))) { new EventShop(p).open(p); return true; }
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
                int left = playsLeft(d, g);
                set(top[i], Icons.of(g.icon, "&e&l" + g.label, "&7" + g.how, "&7" + g.goal, "",
                        "&f오늘 남은 판 " + (left > 0 ? "&e" : "&c") + left + " &7/ " + dailyPlays() + (total() ? " &8(모든 게임 합계)" : ""),
                        left > 0 ? "&e▶ 클릭하여 난이도 선택" : "&c오늘은 더 할 수 없습니다 (자정에 초기화)"), e -> openDifficulty(p, g));
                List<Integer> rw = plugin.getConfig().getIntegerList("minigames.reward." + g.key());
                String rs = rw.size() >= 3 ? rw.get(0) + " / " + rw.get(1) + " / " + rw.get(2) : "1 / 3 / 6";
                set(bottom[i], Icons.of(Coin.EVENT.icon, "&e성공 보상", "&7쉬움 / 보통 / 어려움", "&e이벤트 코인 " + rs + "개"));
            }
            set(40, Icons.of(Coin.EVENT.icon, (int) Math.max(1, Math.min(64, Coin.EVENT.get(d))), "&a&l이벤트 상점", "&7이벤트 코인으로 여러 보상을 살 수 있습니다",
                    "&f보유 &e" + Coin.EVENT.get(d) + " 이벤트 코인", "", "&f오늘 남은 판 " + (playsLeft(d, Game.MOLE) > 0 ? "&e" : "&c") + playsLeft(d, Game.MOLE) + " &7/ " + dailyPlays()
                            + (total() ? " &8(4종 합계)" : ""), "&e▶ 클릭"), e -> new EventShop(p).open(p));
            set(49, Gui.button(Material.BOOK, "&f도움말", "&7각 게임은 쉬움 · 보통 · 어려움 3단계", "&7어려울수록 코인을 많이 줍니다",
                    "&7하루에 " + (total() ? "모든 게임 합쳐 " : "게임마다 ") + dailyPlays() + "판 (시작하면 1판 차감)",
                    "&7이벤트 코인은 아이템이 아니라 계정에 쌓이는 재화", "&7창을 닫으면 게임을 그만둡니다"));
            fill(0, 53);
        }
    }

    private class DiffGui extends Gui {
        DiffGui(Player p, Game g) {
            super(3, "&8" + g.label + " - 난이도");
            int left = playsLeft(plugin.data().get(p), g);
            set(4, Icons.of(g.icon, "&e&l" + g.label, "&7" + g.how, "&7" + g.goal, "", "&f오늘 남은 판 " + (left > 0 ? "&e" : "&c") + left + " &7/ " + dailyPlays()));
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
            case MEMORY -> "&7" + d.pick(6, 10, 14) + "쌍 · 제한 " + d.pick(90, 120, 100) + "초";
        };
    }

    private void start(Player p, Game g, Diff d) {
        PlayerData pd = plugin.data().get(p);
        if (playsLeft(pd, g) <= 0) {   // v5.6.2: 하루 판 수 제한
            Text.actionBar(p, "&c오늘은 " + (total() ? "미니게임을" : g.label + "을(를)") + " 더 할 수 없습니다. &7(하루 " + dailyPlays() + "판 · 자정에 초기화)");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        pd.counters.merge(playKey(g), 1.0, Double::sum);   // 시작하는 순간 1판 차감 (도중에 닫아도 차감 — 다시 시작해서 쉬운 판 고르기 방지)
        plugin.data().save(pd);
        Text.actionBar(p, "&e" + g.label + " 시작! &7오늘 남은 판 " + playsLeft(pd, g) + "/" + dailyPlays());
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
    /** v5.7.1: 이벤트 코인 상점 하나 — 모든 품목을 한 창에 (품목 종류마다 한 줄, 맨 왼쪽 칸에 종류 이름) */
    private class EventShop extends Gui {
        EventShop(Player p) {
            super(6, "&8이벤트 코인 상점", "eshop_sub");
            PlayerData d = plugin.data().get(p);
            for (int row = 0; row < shops.size() && row < 4; row++) {
                SubShop s = shops.get(row);
                List<Offer> os = s.offers();
                int first = 10 + row * 9, start = Math.max(0, (7 - Math.min(7, os.size())) / 2);   // 한 줄 7칸, 모자라면 가운데 정렬
                set(first - 1, Gui.button(s.icon(), "&6&l" + s.name(), "&7오른쪽 줄의 품목"));
                for (int i = 0; i < os.size() && i < 7; i++) {
                    Offer o = os.get(i);
                    ItemStack icon = plugin.items().create(o.item(), o.amount());
                    if (icon == null) continue;
                    ItemMeta m = icon.getItemMeta();
                    List<String> lore = m.getLore() == null ? new ArrayList<>() : new ArrayList<>(m.getLore());
                    lore.add("");
                    lore.add(Text.c("&f가격 &e" + o.price() + " 이벤트 코인"));
                    String key = "eshop_" + s.id() + "_" + o.item();
                    int bought = (int) Math.round(d.counters.getOrDefault(key, 0.0));
                    if (o.limit() > 0) lore.add(Text.c("&7구매 제한 " + bought + " / " + o.limit()));
                    lore.add(Text.c("&e▶ 클릭하여 구매"));
                    m.setLore(lore);
                    icon.setItemMeta(m);
                    set(first + start + i, icon, e -> buy(p, s, o));
                }
            }
            set(49, Icons.of(Coin.EVENT.icon, (int) Math.max(1, Math.min(64, Coin.EVENT.get(d))), "&e&l보유 이벤트 코인 &f" + Coin.EVENT.get(d) + "개",
                    "&7미니게임 4종 성공 보상 (/이벤트)"));
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
        new EventShop(p).open(p);
    }

    /** 관리자: 코인 지급 */
    public boolean giveCoins(Player target, String coin, long n) {
        Coin c = Coin.EVENT;
        PlayerData d = plugin.data().get(target);
        c.add(d, n);
        plugin.data().save(d);
        return true;
    }
}
