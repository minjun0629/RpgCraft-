package kr.rpgcraft.feature;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.data.PlayerData;
import kr.rpgcraft.gui.Gui;
import kr.rpgcraft.stat.Stat;
import kr.rpgcraft.stat.StatMap;
import kr.rpgcraft.util.Text;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 환생 상점 (v5.4.4): 환생할 때마다 받는 「환생 포인트」로 영구 강화 · 특별 아이템을 산다.
 * 강화는 계정에 영구히 남고(환생해도 유지), 단계가 오를수록 비싸진다.
 */
public class RebirthShop {
    /** 영구 강화: id · 이름 · 아이콘 · 능력치 · 단계당 수치 · 최대 단계 */
    private record Upgrade(String id, String name, Material icon, Stat stat, double per, int max) {}

    private static final List<Upgrade> UPGRADES = List.of(
            new Upgrade("str", "힘의 각성", Material.IRON_SWORD, Stat.STR_PCT, 3, 10),
            new Upgrade("dex", "민첩의 각성", Material.FEATHER, Stat.DEX_PCT, 3, 10),
            new Upgrade("adv", "모험의 각성", Material.SHIELD, Stat.ADV_PCT, 3, 10),
            new Upgrade("hp", "불멸의 육체", Material.GOLDEN_APPLE, Stat.HP_PCT, 4, 10),
            new Upgrade("crit", "날카로운 눈", Material.SPIDER_EYE, Stat.CRIT, 1, 10),
            new Upgrade("critdmg", "치명의 일격", Material.BLAZE_POWDER, Stat.CRIT_DMG, 5, 10),
            new Upgrade("exp", "환생자의 지혜", Material.EXPERIENCE_BOTTLE, Stat.EXP_PCT, 5, 10),
            new Upgrade("enh", "장인의 손길", Material.ANVIL, Stat.ENHANCE_RATE, 1, 5));

    /** 특별 아이템: 아이템 ID · 수량 · 가격(포인트) */
    private record Goods(String item, int amount, int cost) {}

    private static final List<Goods> GOODS = List.of(
            new Goods("ticket_stat_reset", 1, 3),
            new Goods("ticket_job_reset", 1, 4),
            new Goods("ticket_protect", 1, 5),
            new Goods("potential_scroll", 3, 4),
            new Goods("cube_red", 3, 5),
            new Goods("crystal_top", 3, 6));

    private final RpgCraft plugin;

    public RebirthShop(RpgCraft plugin) {
        this.plugin = plugin;
    }

    private int perRebirth() {
        return plugin.getConfig().getInt("rebirth.shop-points", 10);
    }

    /** 환생할 때 (PlayerCommands.rebirth) */
    public void onRebirth(Player p) {
        PlayerData d = plugin.data().get(p);
        grantRetro(d, 1);   // 방금 한 환생은 아래에서 따로 지급
        d.counters.merge("rebirth_points", (double) perRebirth(), Double::sum);
        Text.msg(p, "&d✦ 환생 포인트 +" + perRebirth() + " &7(환생 상점: &e/환생 상점&7)");
    }

    /** 이 기능 전에 환생한 사람에게 지난 환생만큼 포인트를 한 번 지급 */
    private void grantRetro(PlayerData d) {
        grantRetro(d, 0);
    }

    private void grantRetro(PlayerData d, int exclude) {
        if (d.counter("rebirth_points_init") > 0) return;
        d.counters.put("rebirth_points_init", 1.0);
        int past = (int) d.counter("rebirth") - exclude;
        if (past > 0) d.counters.merge("rebirth_points", (double) past * perRebirth(), Double::sum);
    }

    public static int level(PlayerData d, String id) {
        return (int) d.counter("rb_up_" + id);
    }

    /** 다음 단계 가격: 1단계 2포인트, 이후 단계마다 +1 */
    private static int cost(int nextLevel) {
        return nextLevel + 1;
    }

    /** 영구 강화 능력치 (StatCalculator 에서 더함) */
    public static StatMap bonus(PlayerData d) {
        StatMap m = new StatMap();
        for (Upgrade u : UPGRADES) {
            int lv = level(d, u.id());
            if (lv > 0) m.add(u.stat(), u.per() * lv);
        }
        return m;
    }

    public void open(Player p) {
        PlayerData d = plugin.data().get(p);
        grantRetro(d);
        int pts = (int) d.counter("rebirth_points");
        Gui g = new Gui(6, "&8환생 상점 &7(포인트 " + pts + ")") {
        };
        g.set(4, Gui.button(Material.NETHER_STAR, "&d&l환생 포인트: " + pts,
                "&7환생 1회마다 &f" + perRebirth() + " 포인트",
                "&7지금까지 환생 &f" + (int) d.counter("rebirth") + "회",
                "&7강화는 영구히 유지됩니다 (환생해도 그대로)"), null);
        int[] slots = {19, 20, 21, 22, 23, 24, 25, 31};
        for (int i = 0; i < UPGRADES.size(); i++) {
            Upgrade u = UPGRADES.get(i);
            int lv = level(d, u.id());
            boolean maxed = lv >= u.max();
            int c = cost(lv + 1);
            List<String> lore = new ArrayList<>();
            lore.add("&7" + u.stat().label + " &a+" + fmt(u.per()) + (u.stat().pct ? "%" : "") + " &7/ 단계");
            lore.add("&7현재: &f" + lv + " / " + u.max() + " 단계 &8(" + u.stat().label + " +" + fmt(u.per() * lv) + (u.stat().pct ? "%" : "") + ")");
            lore.add("");
            lore.add(maxed ? "&6최대 단계" : (pts >= c ? "&e▶ 클릭: " : "&c포인트 부족: ") + c + " 포인트");
            g.set(slots[i], Gui.button(maxed ? Material.LIME_DYE : u.icon(), (maxed ? "&6&l" : "&b&l") + u.name() + " &7Lv." + lv, lore.toArray(new String[0])), e -> {
                if (maxed) return;
                if (d.counter("rebirth_points") < c) { Text.actionBar(p, "&c환생 포인트가 부족합니다."); return; }
                d.counters.merge("rebirth_points", (double) -c, Double::sum);
                d.counters.put("rb_up_" + u.id(), lv + 1.0);
                plugin.stats().refresh(p);
                p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.2f);
                Text.msg(p, "&d" + u.name() + " &f" + (lv + 1) + "단계! &7(" + u.stat().label + " +" + fmt(u.per() * (lv + 1)) + (u.stat().pct ? "%" : "") + ")");
                open(p);
            });
        }
        int[] gs = {46, 47, 48, 50, 51, 52};
        for (int i = 0; i < GOODS.size() && i < gs.length; i++) {
            Goods gd = GOODS.get(i);
            var t = plugin.items().get(gd.item());
            if (t == null) continue;
            ItemStack icon = plugin.items().create(gd.item(), gd.amount());
            var m = icon.getItemMeta();
            List<String> lore = m.hasLore() ? new ArrayList<>(m.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add(Text.c((pts >= gd.cost() ? "&e▶ 클릭: " : "&c포인트 부족: ") + gd.cost() + " 포인트"));
            m.setLore(lore);
            icon.setItemMeta(m);
            g.set(gs[i], icon, e -> {
                if (d.counter("rebirth_points") < gd.cost()) { Text.actionBar(p, "&c환생 포인트가 부족합니다."); return; }
                ItemStack it = plugin.items().create(gd.item(), gd.amount());
                if (it == null) return;
                d.counters.merge("rebirth_points", (double) -gd.cost(), Double::sum);
                for (ItemStack left : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                Text.msg(p, "&a구매: &f" + t.name + " x" + gd.amount());
                open(p);
            });
        }
        g.set(40, Gui.button(Material.BOOK, "&7아래 줄: 특별 아이템", "&7위 칸: 영구 강화"), null);
        g.fill(0, 53);
        g.open(p);
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.format("%.1f", v);
    }
}
