package kr.rpgcraft.feature;

import kr.rpgcraft.Keys;
import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.data.PlayerData;
import kr.rpgcraft.gui.Gui;
import kr.rpgcraft.util.Text;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * v5.10.20 시즌 패스 (/시즌패스). 회차(/rpg관리 round next)마다 새로 시작하는 1~50레벨 보상 트랙.
 * 사냥 · 보스 · 낚시 · 미니게임 · 요리 · 공성전으로 패스 경험치를 모으고, 레벨마다 무료 보상 + (구매하면) 프리미엄 보상.
 * 가방이 가득 차면 보상은 우편으로.
 */
public class SeasonPassManager implements Listener, CommandExecutor {
    public static final int MAX = 50;

    private record Reward(String label, Material icon, long money, String item, int amount) {}

    private final RpgCraft plugin;

    public SeasonPassManager(RpgCraft plugin) {
        this.plugin = plugin;
        org.bukkit.Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private int need() {
        return Math.max(10, plugin.getConfig().getInt("season-pass.exp-per-level", 150));
    }

    /** 회차가 바뀌었으면 패스를 새로 */
    private void roll(PlayerData d) {
        int round = plugin.rounds().round();
        if ((int) d.counter("sp_round") == round) return;
        d.counters.put("sp_round", (double) round);
        d.counters.remove("sp_exp");
        d.counters.remove("sp_prem");
        d.counters.remove("sp_cf");
        d.counters.remove("sp_cp");
    }

    public int level(PlayerData d) {
        roll(d);
        return (int) Math.min(MAX, d.counter("sp_exp") / need());
    }

    /** 패스 경험치 더하기 (어디서든 씀) */
    public void add(Player p, double n, String why) {
        if (p == null || n <= 0 || !plugin.getConfig().getBoolean("season-pass.enabled", true)) return;
        PlayerData d = plugin.data().get(p);
        int before = level(d);
        d.counters.merge("sp_exp", n * plugin.getConfig().getDouble("season-pass.exp-mult", 1.0), Double::sum);
        int after = level(d);
        if (after > before) {
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.4f);
            Text.msg(p, "&b✦ 시즌 패스 &e" + after + "레벨 &b달성! &7(/시즌패스 에서 보상 받기)");
        }
    }

    // ------------------------------------------------------------------ 보상표
    private Reward free(int lv) {
        if (lv == MAX) return item("rune_mid", 1, Material.FIREWORK_STAR, 5_000_000);
        if (lv % 10 == 0) return item("ticket_protect", 1, Material.PAPER, 1_000_000);
        if (lv % 5 == 0) return item("crystal_high", 2, Material.AMETHYST_SHARD, 300_000);
        return new Reward(Text.money(20_000L * lv), Material.GOLD_NUGGET, 20_000L * lv, null, 0);
    }

    private Reward premium(int lv) {
        if (lv == MAX) return item("cube_master", 3, Material.LIGHT_BLUE_DYE, 50_000_000);
        if (lv % 10 == 0) return item("cube_master", 1, Material.LIGHT_BLUE_DYE, 10_000_000);
        if (lv % 5 == 0) return item("ticket_rate10", 1, Material.PAPER, 2_000_000);
        if (lv % 3 == 0) return item("cube_red", 1, Material.RED_DYE, 500_000);
        if (lv % 2 == 0) return item("potential_scroll", 1, Material.PAPER, 300_000);
        return new Reward(Text.money(60_000L * lv), Material.GOLD_INGOT, 60_000L * lv, null, 0);
    }

    /** 아이템 보상 (그 아이템이 없으면 같은 값어치의 돈) */
    private Reward item(String id, int n, Material icon, long fallback) {
        var t = plugin.items().get(id);
        if (t == null) return new Reward(Text.money(fallback), Material.GOLD_INGOT, fallback, null, 0);
        return new Reward(Text.strip(Text.c(t.name)) + " x" + n, t.material, 0, id, n);
    }

    private boolean claimed(PlayerData d, boolean prem, int lv) {
        long bits = (long) d.counter(prem ? "sp_cp" : "sp_cf");
        return (bits >> lv & 1L) != 0;
    }

    private void mark(PlayerData d, boolean prem, int lv) {
        long bits = (long) d.counter(prem ? "sp_cp" : "sp_cf");
        d.counters.put(prem ? "sp_cp" : "sp_cf", (double) (bits | 1L << lv));
    }

    private boolean claim(Player p, boolean prem, int lv) {
        PlayerData d = plugin.data().get(p);
        if (lv > level(d) || claimed(d, prem, lv)) return false;
        if (prem && d.counter("sp_prem") <= 0) return false;
        Reward r = prem ? premium(lv) : free(lv);
        mark(d, prem, lv);
        if (r.money() > 0) plugin.economy().give(p, r.money());
        if (r.item() != null) {
            ItemStack it = plugin.items().create(r.item(), r.amount());
            if (it != null) {
                if (plugin.mail() != null) plugin.mail().giveOrMail(p, it, "&b시즌 패스", "시즌 패스 " + lv + "레벨 보상");
                else for (ItemStack left : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ 경험치 얻는 곳: 사냥 · 보스
    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent e) {
        Player k = e.getEntity().getKiller();
        if (k == null || e.getEntity() instanceof Player) return;
        boolean boss = e.getEntity().getPersistentDataContainer().has(Keys.BOSS, PersistentDataType.STRING);
        boolean custom = plugin.customMobs() != null && plugin.customMobs().of(e.getEntity()) != null;
        add(k, boss ? 100 : custom ? 2 : 1, "사냥");
    }

    // ------------------------------------------------------------------ GUI
    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (s instanceof Player p) open(p, -1);
        return true;
    }

    public void open(Player p, int page) {
        PlayerData d = plugin.data().get(p);
        int lv = level(d);
        if (page < 0) page = Math.max(0, Math.min((MAX - 1) / 9, (Math.max(1, lv) - 1) / 9));
        int pg = page, pages = (MAX + 8) / 9;
        boolean prem = d.counter("sp_prem") > 0;
        Gui g = new Gui(6, "&b✦ 시즌 패스 &7(" + (pg + 1) + "/" + pages + ")") {
        };
        double exp = d.counter("sp_exp");
        int need = need();
        g.set(4, Gui.button(Material.NETHER_STAR, "&b&l시즌 " + plugin.rounds().round() + " 패스 &e" + lv + "레벨",
                lv >= MAX ? "&6최고 레벨!" : "&7다음 레벨까지 &f" + (int) (exp - lv * (double) need) + " / " + need,
                lv >= MAX ? "" : Text.bar((exp - lv * (double) need) / need, 20, "&b", "&8"),
                "", "&7패스 경험치: 사냥 1 · 커스텀 몬스터 2 · 보스 100", "&7낚시 3 · 미니게임 15 · 요리 5 · 공성전 승리 300",
                "&8회차가 바뀌면 새 시즌이 시작됩니다"), null);
        long cost = plugin.getConfig().getLong("season-pass.premium-cost", 30_000_000);
        g.set(8, Gui.button(prem ? Material.DIAMOND : Material.DIAMOND_BLOCK, prem ? "&b&l프리미엄 활성화됨" : "&d&l프리미엄 패스 구매",
                prem ? "&7아래 줄의 프리미엄 보상도 받을 수 있습니다" : "&7가격 &e" + Text.money(cost) + " &7(이번 시즌만)",
                prem ? "" : "&7이미 지난 레벨의 프리미엄 보상도 모두 받을 수 있음", prem ? "" : "&e▶ 쉬프트 클릭하여 구매"), e -> {
            if (prem || !e.isShiftClick()) return;
            if (!plugin.economy().take(p, cost)) { Text.msg(p, "&c돈이 부족합니다."); return; }
            d.counters.put("sp_prem", 1.0);
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
            Text.msg(p, "&d✦ 프리미엄 시즌 패스를 구매했습니다!");
            open(p, pg);
        });
        g.set(0, Gui.button(Material.OAK_SIGN, "&7무료 / 프리미엄", "&f2번째 줄: 레벨", "&f3번째 줄: 무료 보상", "&f4번째 줄: 프리미엄 보상"), null);
        for (int i = 0; i < 9; i++) {
            int L = pg * 9 + i + 1;
            if (L > MAX) break;
            boolean reached = L <= lv;
            g.set(9 + i, Gui.button(reached ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE, (reached ? "&a" : "&7") + L + "레벨"), null);
            for (int row = 0; row < 2; row++) {
                boolean pr = row == 1;
                Reward r = pr ? premium(L) : free(L);
                boolean got = claimed(d, pr, L), can = reached && !got && (!pr || prem);
                List<String> lore = new ArrayList<>();
                lore.add((pr ? "&d프리미엄" : "&f무료") + " 보상");
                lore.add("");
                lore.add(got ? "&8받음" : can ? "&e▶ 클릭하여 받기" : !reached ? "&7" + L + "레벨에 받을 수 있음" : "&c프리미엄 패스가 필요합니다");
                Material icon = got ? Material.MINECART : r.icon();
                g.set(18 + row * 9 + i, Gui.button(icon, (got ? "&8" : pr ? "&d" : "&f") + r.label(), lore.toArray(new String[0])), e -> {
                    if (claim(p, pr, L)) {
                        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.3f);
                        open(p, pg);
                    }
                });
            }
        }
        if (pg > 0) g.set(45, Gui.button(Material.ARROW, "&e◀ 이전"), e -> open(p, pg - 1));
        if (pg < pages - 1) g.set(53, Gui.button(Material.ARROW, "&e다음 ▶"), e -> open(p, pg + 1));
        g.set(49, Gui.button(Material.HOPPER, "&a&l받을 수 있는 보상 모두 받기"), e -> {
            int n = 0;
            for (int L = 1; L <= level(d); L++) {
                if (claim(p, false, L)) n++;
                if (claim(p, true, L)) n++;
            }
            Text.msg(p, n > 0 ? "&a시즌 패스 보상 " + n + "개를 받았습니다." : "&7받을 보상이 없습니다.");
            open(p, pg);
        });
        g.fill(0, 53);
        g.open(p);
    }
}
