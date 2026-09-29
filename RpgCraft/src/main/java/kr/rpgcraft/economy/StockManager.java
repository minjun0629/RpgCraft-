package kr.rpgcraft.economy;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.data.PlayerData;
import kr.rpgcraft.gui.Gui;
import kr.rpgcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 주식 (v5.6.0) — /주식
 * 16개 종목의 가격이 1분마다 움직인다 (무작위 등락 + 종목별 성향 + 제자리로 돌아가려는 힘 + 가끔 뉴스로 급등락).
 * 소지금으로 사고팔며, 보유 주식은 플레이어 데이터(counters: stk_종목 = 주 수, stkc_종목 = 산 값 합계)에 저장된다.
 * 사고팔 때 수수료(stocks.fee, 기본 0.5%)가 붙는다.
 */
public class StockManager implements CommandExecutor {
    /** 종목: id · 이름 · 업종 · 아이콘 · 기준가 · 변동성(1분 표준편차) · 성향(1분 평균 등락) · 설명 */
    public record Stock(String id, String name, String sector, Material icon, double base, double vol, double drift, String desc) {}

    public static final List<Stock> STOCKS = List.of(
            new Stock("dwarf", "드워프 제철", "철강", Material.IRON_BLOCK, 12000, 0.010, 0.0001, "드워프 왕국의 대장간을 모두 거느린 제철 회사"),
            new Stock("elfwood", "엘프 목재", "임업", Material.OAK_LOG, 6500, 0.008, 0.0001, "천 년 숲의 목재를 다루는 믿음직한 회사"),
            new Stock("dragon", "용비늘 무역", "무역", Material.DRAGON_BREATH, 48000, 0.018, 0.0002, "용의 비늘을 사고파는 대형 무역상"),
            new Stock("deepsea", "심해 수산", "수산", Material.COD, 3800, 0.012, 0.0000, "낚시 대회와 심해 어획을 책임지는 수산 회사"),
            new Stock("goldbank", "황금 은행", "금융", Material.GOLD_INGOT, 30000, 0.006, 0.0002, "서버 최대의 은행. 느리지만 꾸준하다"),
            new Stock("arcane", "아케인 연구소", "마법", Material.AMETHYST_SHARD, 22000, 0.020, 0.0002, "새 마법을 만드는 연구소. 발표 때마다 크게 출렁인다"),
            new Stock("potion", "물약 제약", "제약", Material.BREWING_STAND, 9000, 0.011, 0.0001, "회복 물약을 만드는 제약 회사"),
            new Stock("guild", "모험가 길드", "서비스", Material.FILLED_MAP, 15000, 0.009, 0.0001, "의뢰를 중개하는 모험가 길드 본부"),
            new Stock("harpy", "하피 항공", "운송", Material.FEATHER, 7200, 0.016, 0.0000, "하늘길 택배 회사. 날씨에 민감하다"),
            new Stock("volcano", "화산 에너지", "에너지", Material.MAGMA_CREAM, 18000, 0.014, 0.0001, "화산의 열로 도시를 밝히는 에너지 회사"),
            new Stock("rune", "룬 전자", "기술", Material.REDSTONE, 26000, 0.017, 0.0003, "룬 회로를 만드는 기술 회사"),
            new Stock("spice", "사막 향신료", "식품", Material.SUGAR, 4200, 0.010, 0.0000, "사막 상단이 모는 향신료 회사"),
            new Stock("frost", "서리 건설", "건설", Material.BRICKS, 11000, 0.009, 0.0001, "얼음 성채를 짓는 건설 회사"),
            new Stock("obsidian", "흑요 방산", "방산", Material.NETHERITE_INGOT, 40000, 0.013, 0.0002, "공성 무기를 만드는 방위 산업체"),
            new Stock("spirit", "원혼 엔터", "엔터", Material.JUKEBOX, 8800, 0.022, 0.0000, "유령 가수들의 소속사. 인기에 따라 널뛴다"),
            new Stock("void", "공허 코인", "가상자산", Material.ENDER_PEARL, 1500, 0.045, 0.0000, "공허에서 캔다는 코인. 가장 위험하고 가장 크게 움직인다"));

    private static final int HISTORY = 60;

    private final RpgCraft plugin;
    private final Map<String, Double> price = new HashMap<>();
    private final Map<String, Deque<Double>> history = new HashMap<>();
    private final File file;

    public StockManager(RpgCraft plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "stocks.yml");
        load();
        long every = Math.max(10, plugin.getConfig().getLong("stocks.update-seconds", 60)) * 20L;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, every, every);
    }

    // ------------------------------------------------------------------ 가격
    public Stock stock(String id) {
        for (Stock s : STOCKS) if (s.id().equals(id)) return s;
        return null;
    }

    public double price(String id) {
        return price.getOrDefault(id, stock(id) == null ? 0 : stock(id).base());
    }

    private double ago(String id, int n) {
        Deque<Double> h = history.get(id);
        if (h == null || h.isEmpty()) return price(id);
        Iterator<Double> it = h.descendingIterator();
        double v = price(id);
        for (int i = 0; i < n && it.hasNext(); i++) v = it.next();
        return v;
    }

    private void tick() {
        if (!plugin.getConfig().getBoolean("stocks.enabled", true)) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (Stock s : STOCKS) {
            double p = price(s.id());
            history.computeIfAbsent(s.id(), k -> new ArrayDeque<>()).addLast(p);
            while (history.get(s.id()).size() > HISTORY) history.get(s.id()).pollFirst();
            double pull = Math.log(s.base() / p) * 0.015;   // 기준가에서 멀어질수록 돌아가려는 힘
            double ret = s.drift() + pull + r.nextGaussian() * s.vol();
            price.put(s.id(), clamp(s, p * Math.exp(ret)));
        }
        if (r.nextDouble() < plugin.getConfig().getDouble("stocks.news-chance", 0.06)) news(r);
        save();
        for (Player p : Bukkit.getOnlinePlayers())
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof StockGui g) g.render();
    }

    private static double clamp(Stock s, double v) {
        return Math.max(s.base() * 0.08, Math.min(s.base() * 25, v));
    }

    private static final String[] GOOD = {"대형 계약 체결", "신제품 대박", "실적 깜짝 발표", "왕실 납품 확정", "해외 진출 성공"};
    private static final String[] BAD = {"창고 화재", "대표 잠적설", "리콜 사태", "세무 조사", "경쟁사에 밀려"};

    private void news(ThreadLocalRandom r) {
        Stock s = STOCKS.get(r.nextInt(STOCKS.size()));
        boolean up = r.nextBoolean();
        double pct = 0.08 + r.nextDouble() * 0.17 * (s.id().equals("void") ? 2 : 1);
        double before = price(s.id());
        price.put(s.id(), clamp(s, before * (up ? 1 + pct : 1 - pct)));
        double real = (price(s.id()) / before - 1) * 100;
        Text.announce(Text.PREFIX + Text.c("&f📰 &e[주식 뉴스] &f" + s.name() + " " + (up ? GOOD : BAD)[r.nextInt(5)] + "! "
                + (real >= 0 ? "&c▲ " : "&9▼ ") + String.format("%.1f%%", Math.abs(real)) + " &7(/주식)"));
    }

    private void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (Stock s : STOCKS) {
            price.put(s.id(), y.getDouble(s.id() + ".price", s.base()));
            Deque<Double> h = new ArrayDeque<>(y.getDoubleList(s.id() + ".history"));
            history.put(s.id(), h);
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Stock s : STOCKS) {
            y.set(s.id() + ".price", Math.round(price(s.id()) * 100) / 100.0);
            y.set(s.id() + ".history", new ArrayList<>(history.getOrDefault(s.id(), new ArrayDeque<>())));
        }
        try { y.save(file); } catch (Exception ex) { plugin.getLogger().warning("stocks.yml 저장 실패: " + ex.getMessage()); }
    }

    /** 관리자: 가격 직접 지정 */
    public boolean setPrice(String id, double v) {
        Stock s = stock(id);
        if (s == null) return false;
        price.put(id, clamp(s, v));
        save();
        return true;
    }

    // ------------------------------------------------------------------ 매매
    public long shares(PlayerData d, String id) {
        return Math.round(d.counters.getOrDefault("stk_" + id, 0.0));
    }

    private double fee() {
        return plugin.getConfig().getDouble("stocks.fee", 0.005);
    }

    public void buy(Player p, Stock s, long n) {
        PlayerData d = plugin.data().get(p);
        long max = plugin.getConfig().getLong("stocks.max-shares", 100000);
        n = Math.min(n, max - shares(d, s.id()));
        if (n <= 0) { Text.actionBar(p, "&c한 종목은 최대 " + Text.num(max) + "주까지 가질 수 있습니다."); return; }
        long cost = Math.round(price(s.id()) * n * (1 + fee()));
        if (!plugin.economy().take(p, cost)) {
            long can = (long) (plugin.economy().balance(p) / (price(s.id()) * (1 + fee())));
            if (can <= 0) { Text.actionBar(p, "&c소지금이 부족합니다. &7(1주 " + Text.money(Math.round(price(s.id()) * (1 + fee()))) + ")"); return; }
            buy(p, s, can);
            return;
        }
        d.counters.merge("stk_" + s.id(), (double) n, Double::sum);
        d.counters.merge("stkc_" + s.id(), (double) cost, Double::sum);
        plugin.data().save(d);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.4f);
        Text.actionBar(p, "&a매수 &f" + s.name() + " " + Text.num(n) + "주 &7(-" + Text.money(cost) + ")");
    }

    public void sell(Player p, Stock s, long n) {
        PlayerData d = plugin.data().get(p);
        long have = shares(d, s.id());
        n = Math.min(n, have);
        if (n <= 0) { Text.actionBar(p, "&c가진 주식이 없습니다."); return; }
        long gain = Math.round(price(s.id()) * n * (1 - fee()));
        double cost = d.counters.getOrDefault("stkc_" + s.id(), 0.0);
        double costPart = have == 0 ? 0 : cost * n / have;
        d.counters.put("stk_" + s.id(), (double) (have - n));
        d.counters.put("stkc_" + s.id(), Math.max(0, cost - costPart));
        if (have - n <= 0) { d.counters.remove("stk_" + s.id()); d.counters.remove("stkc_" + s.id()); }
        plugin.economy().give(p, gain);
        plugin.data().save(d);
        long pl = Math.round(gain - costPart);
        p.playSound(p.getLocation(), pl >= 0 ? Sound.ENTITY_EXPERIENCE_ORB_PICKUP : Sound.BLOCK_NOTE_BLOCK_BASS, 1f, pl >= 0 ? 1.2f : 0.8f);
        Text.actionBar(p, "&e매도 &f" + s.name() + " " + Text.num(n) + "주 &7(+" + Text.money(gain) + ") " + (pl >= 0 ? "&c수익 +" : "&9손실 -") + Text.money(Math.abs(pl)));
    }

    // ------------------------------------------------------------------ 화면
    private static String pct(double now, double before) {
        double c = before <= 0 ? 0 : (now / before - 1) * 100;
        return (c > 0.005 ? "&c▲ " : c < -0.005 ? "&9▼ " : "&7- ") + String.format("%.2f%%", Math.abs(c));
    }

    private String spark(String id) {
        List<Double> h = new ArrayList<>(history.getOrDefault(id, new ArrayDeque<>()));
        h.add(price(id));
        if (h.size() > 24) h = h.subList(h.size() - 24, h.size());
        double lo = Collections.min(h), hi = Collections.max(h);
        String bars = "▁▂▃▄▅▆▇█";
        StringBuilder sb = new StringBuilder();
        double prev = h.get(0);
        for (double v : h) {
            int k = hi - lo < 1e-9 ? 3 : (int) Math.round((v - lo) / (hi - lo) * 7);
            sb.append(v >= prev ? "&c" : "&9").append(bars.charAt(k));
            prev = v;
        }
        return sb.toString();
    }

    public static final int[] SLOTS = {11, 12, 14, 15, 20, 21, 23, 24, 29, 30, 32, 33, 38, 39, 41, 42};   // 4x4, 가운데 줄 비움 (좌우 대칭)

    public class StockGui extends Gui {
        private final Player p;

        StockGui(Player p) {
            super(6, "&8주식 시장", "stock");
            this.p = p;
            render();
        }

        void render() {
            PlayerData d = plugin.data().get(p);
            double total = 0, cost = 0;
            for (int i = 0; i < STOCKS.size(); i++) {
                Stock s = STOCKS.get(i);
                double now = price(s.id());
                long have = shares(d, s.id());
                double c = d.counters.getOrDefault("stkc_" + s.id(), 0.0);
                total += now * have;
                cost += c;
                List<String> lore = new ArrayList<>(List.of(
                        "&8" + s.sector() + " · " + s.desc(),
                        "",
                        "&f현재가 &e" + Text.money(Math.round(now)),
                        "&7직전 대비 " + pct(now, ago(s.id(), 1)) + "   &71시간 " + pct(now, ago(s.id(), HISTORY)),
                        "&7흐름 " + spark(s.id()),
                        "&7위험도 " + risk(s.vol())));
                if (have > 0) {
                    double pl = now * have * (1 - fee()) - c;
                    lore.add("");
                    lore.add("&b보유 &f" + Text.num(have) + "주 &8(평단 " + Text.money(Math.round(c / have)) + ")");
                    lore.add("&7평가 " + Text.money(Math.round(now * have)) + "  " + (pl >= 0 ? "&c+" : "&9-") + Text.money(Math.round(Math.abs(pl))));
                }
                lore.add("");
                lore.add("&e좌클릭 &f1주 매수  &e쉬프트+좌클릭 &f10주  &eQ &f100주");
                lore.add("&e우클릭 &f1주 매도  &e쉬프트+우클릭 &f전부 매도");
                set(SLOTS[i], button(s.icon(), (have > 0 ? "&b" : "&f") + "&l" + s.name() + " &7" + Text.money(Math.round(now)) + " " + pct(now, ago(s.id(), 1)),
                        lore.toArray(new String[0])), e -> {
                    ClickType ct = e.getClick();
                    if (ct == ClickType.DROP || ct == ClickType.CONTROL_DROP) buy(p, s, 100);
                    else if (e.isLeftClick()) buy(p, s, e.isShiftClick() ? 10 : 1);
                    else if (e.isRightClick()) sell(p, s, e.isShiftClick() ? Long.MAX_VALUE : 1);
                    render();
                });
            }
            double pl = total * (1 - fee()) - cost;
            set(49, button(Material.BOOK, "&6&l내 주식 계좌",
                    "&7평가 금액 &f" + Text.money(Math.round(total)),
                    "&7산 금액 &f" + Text.money(Math.round(cost)),
                    "&7손익 " + (pl >= 0 ? "&c+" : "&9-") + Text.money(Math.round(Math.abs(pl))) + (cost > 0 ? String.format(" &8(%+.1f%%)", pl / cost * 100) : ""),
                    "&7소지금 &e" + Text.money(plugin.economy().balance(p))));
            set(48, button(Material.CLOCK, "&e가격은 " + plugin.getConfig().getLong("stocks.update-seconds", 60) + "초마다 바뀝니다",
                    "&7가끔 뉴스가 떠서 크게 오르내립니다", "&7사고팔 때 수수료 " + String.format("%.1f%%", fee() * 100)));
            set(50, button(Material.PAPER, "&f도움말", "&7싸게 사서 비싸게 팔면 이득!", "&7위험도가 높을수록 크게 움직입니다", "&7보유 주식은 나갔다 와도 그대로입니다"));
            fill(0, 53);
        }

        private String risk(double vol) {
            return vol >= 0.03 ? "&4■■■■■" : vol >= 0.018 ? "&c■■■■&8■" : vol >= 0.013 ? "&6■■■&8■■" : vol >= 0.009 ? "&e■■&8■■■" : "&a■&8■■■■";
        }
    }

    public void open(Player p) {
        new StockGui(p).open(p);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        if (!plugin.getConfig().getBoolean("stocks.enabled", true)) { Text.msg(p, "&c주식 시장이 닫혀 있습니다."); return true; }
        open(p);
        return true;
    }
}
