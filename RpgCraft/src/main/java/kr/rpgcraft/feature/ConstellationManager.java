package kr.rpgcraft.feature;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.data.PlayerData;
import kr.rpgcraft.gui.Gui;
import kr.rpgcraft.gui.UiIcon;
import kr.rpgcraft.stat.Stat;
import kr.rpgcraft.stat.StatMap;
import kr.rpgcraft.util.Text;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * v5.10.59 별자리 특성 트리 (/별자리).
 * 별 조각을 모아 네 별자리의 별(노드)을 차례로 밝힌다. 밝힌 별의 능력치는 영구 적용.
 *  - 별 조각: 레벨 업마다 1 · 보스 처치(근처 참여자) 3 · 5성을 넘긴 영혼석 1
 *  - 각 별자리 12개 별: 앞 별을 밝혀야 다음 별. 가운데에서 두 갈래로 나뉘었다가 마지막 별(1등성)에서 다시 만남
 *  - 초기화: 돈을 내면 쓴 별 조각을 모두 돌려받음
 * 저장: counters "star_shard" (가진 조각), "cst_<별자리>_<번호>" = 1 (밝힌 별)
 */
public class ConstellationManager {
    public record Node(int tree, int idx, String name, int cost, int[] req, StatMap bonus) {}

    public static final String[] TREES = {"전사자리", "그림자자리", "방랑자자리", "수호자자리"};
    private static final String[] TREE_COLOR = {"&c", "&9", "&a", "&e"};
    private static final String[] TREE_DESC = {"힘 · 공격력 · 방어력무시 · 치명타 피해", "민첩 · 치명타 · 회피 · 이동속도", "모험 · 체력 · 방어력 · 경험치", "모두에게: 체력 · 방어력 · 체력흡수 · 강화 확률"};
    /** 별 자리 (GUI 칸) — 별자리 모양: 0~4 줄기, 5·7·9 왼쪽 갈래, 6·8·10 오른쪽 갈래, 11 1등성 */
    private static final int[] SLOTS = {36, 28, 20, 21, 13, 3, 23, 4, 33, 5, 34, 16};
    private static final int[] COST = {3, 5, 8, 10, 14, 18, 18, 24, 24, 32, 32, 60};
    private static final int[][] REQ = {{}, {0}, {1}, {2}, {3}, {4}, {4}, {5}, {6}, {7}, {8}, {9, 10}};
    private final List<Node> nodes = new ArrayList<>();
    private final RpgCraft plugin;

    public ConstellationManager(RpgCraft plugin) {
        this.plugin = plugin;
        // 전사자리
        tree(0, new String[]{"투지의 별", "검날의 별", "강철 심장", "관통의 별", "전사의 맥박", "분노의 갈래", "방패의 갈래", "학살자", "철벽", "파괴의 별", "불굴의 별", "1등성 · 군신"},
                new Object[][]{{Stat.STR_PCT, 2}, {Stat.ATK, 20}, {Stat.HP_PCT, 2}, {Stat.ARMOR_PEN, 2}, {Stat.STR_PCT, 3}, {Stat.CRIT_DMG, 6}, {Stat.DEF, 2},
                        {Stat.ATK, 50}, {Stat.HP_PCT, 4}, {Stat.CRIT_DMG, 10}, {Stat.DEF, 3}, {Stat.STR_PCT, 8, Stat.ARMOR_PEN, 6}});
        // 그림자자리
        tree(1, new String[]{"민첩의 별", "칼끝의 별", "그림자 걸음", "날카로운 눈", "암살자의 맥박", "치명의 갈래", "회피의 갈래", "급소 찌르기", "잔상", "사냥꾼의 별", "질풍의 별", "1등성 · 밤의 여왕"},
                new Object[][]{{Stat.DEX_PCT, 2}, {Stat.CRIT_DMG, 4}, {Stat.SPEED, 2}, {Stat.CRIT, 1}, {Stat.DEX_PCT, 3}, {Stat.CRIT_DMG, 8}, {Stat.DODGE, 1},
                        {Stat.CRIT, 2}, {Stat.DODGE, 1.5}, {Stat.CRIT_DMG, 12}, {Stat.SPEED, 4}, {Stat.CRIT, 4, Stat.CRIT_DMG, 20}});
        // 방랑자자리
        tree(2, new String[]{"모험의 별", "길잡이 별", "단단한 피부", "배움의 별", "방랑자의 맥박", "생명의 갈래", "지혜의 갈래", "거인의 몸", "현자의 눈", "개척자의 별", "탐험가의 별", "1등성 · 북극성"},
                new Object[][]{{Stat.ADV_PCT, 2}, {Stat.HP, 150}, {Stat.DEF, 1}, {Stat.EXP_PCT, 3}, {Stat.ADV_PCT, 3}, {Stat.HP_PCT, 4}, {Stat.EXP_PCT, 5},
                        {Stat.HP, 500}, {Stat.EXP_PCT, 6}, {Stat.HP_PCT, 6}, {Stat.ADV_PCT, 5}, {Stat.ADV_PCT, 8, Stat.EXP_PCT, 10}});
        // 수호자자리
        tree(3, new String[]{"수호의 별", "갑옷의 별", "피의 별", "마력의 별", "수호자의 맥박", "흡혈의 갈래", "장인의 갈래", "생명의 샘", "대장장이의 별", "궁수의 별", "요새의 별", "1등성 · 수호신"},
                new Object[][]{{Stat.HP_PCT, 2}, {Stat.DEF, 1}, {Stat.LIFESTEAL, 0.5}, {Stat.MAGIC, 30}, {Stat.HP_PCT, 3}, {Stat.LIFESTEAL, 1}, {Stat.ENHANCE_RATE, 1},
                        {Stat.HP_PCT, 4}, {Stat.ENHANCE_RATE, 1}, {Stat.RANGED_ATK, 40}, {Stat.DEF, 3}, {Stat.HP_PCT, 8, Stat.DEF, 4, Stat.LIFESTEAL, 1}});
    }

    private void tree(int t, String[] names, Object[][] stats) {
        for (int i = 0; i < 12; i++) {
            StatMap m = new StatMap();
            Object[] s = stats[i];
            for (int k = 0; k + 1 < s.length; k += 2) m.add((Stat) s[k], ((Number) s[k + 1]).doubleValue());
            double mult = plugin.getConfig().getDouble("constellation.cost-mult", 1.0);
            nodes.add(new Node(t, i, names[i], (int) Math.max(1, Math.round(COST[i] * mult)), REQ[i], m));
        }
    }

    private Node node(int t, int i) {
        return nodes.get(t * 12 + i);
    }

    private static String key(Node n) {
        return "cst_" + n.tree + "_" + n.idx;
    }

    public boolean lit(PlayerData d, Node n) {
        return d.counter(key(n)) > 0;
    }

    public long shards(PlayerData d) {
        return (long) d.counter("star_shard");
    }

    public int litCount(PlayerData d) {
        int c = 0;
        for (Node n : nodes) if (lit(d, n)) c++;
        return c;
    }

    /** 밝힌 별 능력치 합 (StatCalculator) */
    public StatMap bonus(PlayerData d) {
        StatMap t = new StatMap();
        for (Node n : nodes) if (lit(d, n)) t.addAll(n.bonus);
        return t;
    }

    public void gainShards(Player p, int n, String why) {
        if (n <= 0 || p == null) return;
        PlayerData d = plugin.data().get(p);
        d.addCounter("star_shard", n);
        Text.actionBar(p, "&b✦ 별 조각 +" + n + " &7(" + why + ") &f보유 " + shards(d));
    }

    private boolean canLight(PlayerData d, Node n) {
        for (int r : n.req) if (!lit(d, node(n.tree, r))) return false;
        return true;
    }

    private void light(Player p, Node n) {
        PlayerData d = plugin.data().get(p);
        if (lit(d, n)) return;
        if (!canLight(d, n)) { Text.msg(p, "&c앞의 별을 먼저 밝혀야 합니다."); return; }
        if (shards(d) < n.cost) { Text.msg(p, "&c별 조각이 부족합니다. &7(" + shards(d) + "/" + n.cost + ")"); return; }
        d.addCounter("star_shard", -n.cost);
        d.addCounter(key(n), 1);
        plugin.stats().refresh(p);
        p.playSound(p.getLocation(), n.idx == 11 ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, n.idx == 11 ? 1f : 1.4f);
        if (n.idx == 11)
            Text.announce(Text.PREFIX + Text.c("&b✦ &f" + Text.name(p) + "님이 " + TREE_COLOR[n.tree] + TREES[n.tree] + "&f의 1등성을 밝혔습니다!"));
        else Text.actionBar(p, TREE_COLOR[n.tree] + "✦ " + n.name + " &f밝힘! " + statText(n.bonus));
    }

    private void reset(Player p) {
        PlayerData d = plugin.data().get(p);
        long cost = plugin.getConfig().getLong("constellation.reset-cost", 3_000_000);
        int refund = 0;
        for (Node n : nodes) if (lit(d, n)) refund += n.cost;
        if (refund == 0) { Text.msg(p, "&7밝힌 별이 없습니다."); return; }
        if (!plugin.economy().take(p, cost)) { Text.msg(p, "&c초기화 비용 " + Text.money(cost) + "이 부족합니다."); return; }
        for (Node n : nodes) d.counters.remove(key(n));
        d.addCounter("star_shard", refund);
        plugin.stats().refresh(p);
        Text.msg(p, "&b별자리를 초기화했습니다. &f별 조각 " + refund + "개를 돌려받았습니다.");
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1f, 1.2f);
    }

    static String statText(StatMap m) {
        StringBuilder sb = new StringBuilder();
        for (Stat s : Stat.values()) {
            double v = m.get(s);
            if (v == 0) continue;
            if (sb.length() > 0) sb.append(" · ");
            sb.append(s.label).append(" +").append(v == Math.floor(v) ? String.valueOf((long) v) : String.format("%.1f", v)).append(s.pct ? "%" : "");
        }
        return sb.toString();
    }

    public void open(Player p) {
        open(p, 0);
    }

    public void open(Player p, int tree) {
        new TreeGui(p, tree).open(p);
    }

    private class TreeGui extends Gui {
        TreeGui(Player p, int t) {
            super(6, "&8✦ 별자리 · " + TREES[t]);
            PlayerData d = plugin.data().get(p);
            for (int i = 0; i < 12; i++) {
                Node n = node(t, i);
                boolean on = lit(d, n), can = !on && canLight(d, n);
                List<String> lore = new ArrayList<>();
                lore.add("&f" + statText(n.bonus));
                lore.add("");
                if (on) lore.add("&a✔ 밝힌 별");
                else {
                    lore.add("&7비용: &b별 조각 " + n.cost + " &7(보유 " + shards(d) + ")");
                    if (n.req.length > 0) {
                        StringBuilder r = new StringBuilder();
                        for (int q : n.req) r.append(r.length() > 0 ? ", " : "").append(node(t, q).name);
                        lore.add((can ? "&a" : "&c") + "선행: " + r);
                    }
                    lore.add(can ? (shards(d) >= n.cost ? "&e▶ 클릭하여 밝히기" : "&c별 조각 부족") : "&8앞의 별을 먼저 밝히세요");
                }
                String title = (on ? TREE_COLOR[t] + "&l✦ " : can ? "&f✧ " : "&8✧ ") + n.name + (i == 11 ? " &6(1등성)" : "");
                set(SLOTS[i], Gui.ui(i == 11 ? UiIcon.CROWN : UiIcon.STAR, on, title, lore.toArray(new String[0])), e -> {
                    if (!on) light(p, n);
                    new TreeGui(p, t).open(p);
                });
            }
            // 별자리 탭
            int litHere = 0;
            for (int i = 0; i < 12; i++) if (lit(d, node(t, i))) { litHere++; }
            for (int k = 0; k < 4; k++) {
                int kk = k, c = 0;
                for (int i = 0; i < 12; i++) if (lit(d, node(k, i))) c++;
                set(45 + k, Gui.ui(UiIcon.CONSTELLATION, k == t, TREE_COLOR[k] + "&l" + TREES[k] + " &7(" + c + "/12)", "&7" + TREE_DESC[k], k == t ? "&a보는 중" : "&e▶ 클릭"),
                        e -> new TreeGui(p, kk).open(p));
            }
            StatMap all = bonus(d);
            set(49, Gui.ui(UiIcon.CONSTELLATION, true, "&b&l별 조각 &f" + shards(d), "&7이 별자리: " + litHere + "/12 · 전체 " + litCount(d) + "/48",
                    "&7얻는 곳: 레벨 업 1 · 보스 처치 3 · 5성 넘긴 영혼석 1", "", "&f모든 별자리 합계:", "&f" + (statText(all).isEmpty() ? "&8없음" : statText(all))));
            long cost = plugin.getConfig().getLong("constellation.reset-cost", 3_000_000);
            set(53, Gui.ui(UiIcon.UPGRADE, true, "&c별자리 초기화", "&7비용 " + Text.money(cost), "&7쓴 별 조각을 모두 돌려받음", "&e▶ 쉬프트 + 클릭"), e -> {
                if (!e.isShiftClick()) return;
                reset(p);
                new TreeGui(p, t).open(p);
            });
            fill(0, 53);
        }
    }
}
