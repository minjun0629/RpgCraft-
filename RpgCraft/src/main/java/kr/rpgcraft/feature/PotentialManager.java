package kr.rpgcraft.feature;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.gui.Gui;
import kr.rpgcraft.item.Category;
import kr.rpgcraft.item.ItemData;
import kr.rpgcraft.item.ItemTemplate;
import kr.rpgcraft.stat.Stat;
import kr.rpgcraft.stat.StatMap;
import kr.rpgcraft.util.Text;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 잠재능력 (메이플스토리식)
 *  · 등급: 레어 → 에픽 → 유니크 → 레전드리, 옵션 3줄
 *  · 첫 줄은 현재 등급, 둘째·셋째 줄은 20% 확률로 현재 등급 / 아니면 한 단계 아래 수치
 *  · 잠재능력 부여 주문서: 잠재능력이 없는 장비에 레어 잠재능력
 *  · 수상한 큐브: 옵션 재설정, 등급 상승 (레어→에픽 6%, 에픽→유니크 1.8%, 유니크까지)
 *  · 명장의 큐브: 옵션 재설정, 등급 상승 (12% / 4.7% / 유니크→레전드리 1.2%)
 *  무기와 방어구는 나오는 옵션이 다르다. /잠재능력 또는 강화 창에서 열기.
 */
public class PotentialManager {
    public enum Tier {
        RARE("레어", "&9"), EPIC("에픽", "&5"), UNIQUE("유니크", "&6"), LEGENDARY("레전드리", "&a");

        public final String label, color;

        Tier(String label, String color) {
            this.label = label;
            this.color = color;
        }
    }

    /** 옵션: 스탯, 등급별 수치(레어·에픽·유니크·레전드리) */
    private record Opt(Stat stat, double[] v) {}

    /** 올스탯: 힘·민첩·모험 % 를 한 줄로 (저장은 ALL) */
    private static final Opt ALL = new Opt(null, new double[]{2, 4, 6, 9});

    private static final List<Opt> WEAPON = List.of(
            new Opt(Stat.STR_PCT, new double[]{3, 6, 9, 12}), new Opt(Stat.DEX_PCT, new double[]{3, 6, 9, 12}),
            new Opt(Stat.CRIT, new double[]{2, 4, 6, 8}), new Opt(Stat.CRIT_DMG, new double[]{4, 8, 14, 22}),
            new Opt(Stat.ARMOR_PEN, new double[]{2, 4, 6, 9}), new Opt(Stat.ATK, new double[]{15, 35, 70, 120}),
            new Opt(Stat.MAGIC, new double[]{40, 90, 180, 300}), new Opt(Stat.LIFESTEAL, new double[]{0.5, 1, 1.5, 2.5}));
    private static final List<Opt> ARMOR = List.of(
            new Opt(Stat.HP_PCT, new double[]{3, 6, 9, 12}), new Opt(Stat.ADV_PCT, new double[]{3, 6, 9, 12}),
            new Opt(Stat.DEF, new double[]{1, 2, 3, 5}), new Opt(Stat.STR_PCT, new double[]{2, 4, 6, 9}),
            new Opt(Stat.DEX_PCT, new double[]{2, 4, 6, 9}), new Opt(Stat.SPEED, new double[]{2, 3, 4, 6}),
            new Opt(Stat.DODGE, new double[]{1, 2, 3, 5}), new Opt(Stat.EXP_PCT, new double[]{2, 4, 6, 10}));

    private static NamespacedKey KEY;
    private final RpgCraft plugin;

    public PotentialManager(RpgCraft plugin) {
        this.plugin = plugin;
        KEY = new NamespacedKey(plugin, "potential");
    }

    public static boolean eligible(ItemStack it) {
        ItemTemplate t = ItemData.template(it);
        return t != null && (t.category == Category.WEAPON || t.category == Category.BOW || t.category == Category.ARMOR);
    }

    // ------------------------------------------------------------------ 저장: "TIER|STAT:값;STAT:값;STAT:값"
    public static Tier tier(ItemStack it) {
        String s = raw(it);
        if (s == null) return null;
        try {
            return Tier.valueOf(s.split("\\|")[0]);
        } catch (Exception e) {
            return null;
        }
    }

    private static String raw(ItemStack it) {
        if (KEY == null || it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
    }

    /** 잠재능력 스탯 (없으면 빈 값) */
    public static StatMap stats(ItemStack it) {
        StatMap m = new StatMap();
        String s = raw(it);
        if (s == null || !s.contains("|")) return m;
        for (String part : s.split("\\|")[1].split(";")) {
            String[] kv = part.split(":");
            if (kv.length != 2) continue;
            try {
                if (kv[0].equals("ALL")) { double v = Double.parseDouble(kv[1]); m.add(Stat.STR_PCT, v).add(Stat.DEX_PCT, v).add(Stat.ADV_PCT, v); }
                else m.add(Stat.valueOf(kv[0]), Double.parseDouble(kv[1]));
            } catch (Exception ignored) {
            }
        }
        return m;
    }

    /** 아이템 설명에 넣을 줄 */
    public static List<String> lore(ItemStack it) {
        List<String> out = new ArrayList<>();
        Tier t = tier(it);
        if (t == null) return out;
        out.add(t.color + "✦ 잠재능력 [" + t.label + "]");
        String s = raw(it);
        for (String part : s.split("\\|")[1].split(";")) {
            String[] kv = part.split(":");
            if (kv.length != 2) continue;
            try {
                double v = Double.parseDouble(kv[1]);
                String vs = v == (long) v ? String.valueOf((long) v) : String.valueOf(v);
                if (kv[0].equals("ALL")) { out.add("  " + t.color + "· &f올스탯 +" + vs + "%"); continue; }
                Stat st = Stat.valueOf(kv[0]);
                out.add("  " + t.color + "· &f" + st.label + " +" + vs + (st.pct ? "%" : ""));
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    private static void write(ItemStack it, Tier t) {
        write(it, t, new boolean[3]);
    }

    /** locked[i] 가 true 인 줄은 그대로 두고 나머지만 다시 뽑음 */
    private static void write(ItemStack it, Tier t, boolean[] locked) {
        ItemTemplate tp = ItemData.template(it);
        List<Opt> pool = new ArrayList<>(tp.category == Category.ARMOR ? ARMOR : WEAPON);
        pool.add(ALL);
        String old = raw(it);
        String[] oldLines = old != null && old.contains("|") ? old.split("\\|")[1].split(";") : new String[0];
        ThreadLocalRandom r = ThreadLocalRandom.current();
        StringBuilder sb = new StringBuilder(t.name()).append("|");
        for (int i = 0; i < 3; i++) {
            if (locked[i] && i < oldLines.length) {
                if (i > 0) sb.append(";");
                sb.append(oldLines[i]);
                continue;
            }
            Opt o = pool.get(r.nextInt(pool.size()));
            int ti = t.ordinal();
            if (i > 0 && ti > 0 && r.nextDouble() >= 0.2) ti--;   // 2·3번째 줄은 대체로 한 단계 아래
            double v = o.v()[ti];
            if (i > 0) sb.append(";");
            sb.append(o == ALL ? "ALL" : o.stat().name()).append(":").append(v);
        }
        ItemMeta m = it.getItemMeta();
        m.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, sb.toString());
        it.setItemMeta(m);
        ItemData.refresh(it);
    }

    // ------------------------------------------------------------------ 사용
    private boolean take(Player p, String id) {
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (id.equals(ItemData.id(it))) {
                it.setAmount(it.getAmount() - 1);
                return true;
            }
        }
        return false;
    }

    /** 결과 메시지 (null 이면 실패 사유 없이 성공) */
    public String use(Player p, ItemStack eq, String tool) {
        return use(p, eq, tool, new boolean[3]);
    }

    public String use(Player p, ItemStack eq, String tool, boolean[] locked) {
        if (!eligible(eq)) return "잠재능력을 가질 수 없는 아이템입니다.";
        Tier cur = tier(eq);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        switch (tool) {
            case "potential_scroll" -> {
                if (cur != null) return "이미 잠재능력이 있습니다. 큐브로 재설정하세요.";
                if (!take(p, tool)) return "잠재능력 부여 주문서가 없습니다.";
                write(eq, Tier.RARE);
                return null;
            }
            case "cube_red", "cube_master" -> {
                if (cur == null) return "먼저 잠재능력 부여 주문서로 잠재능력을 여세요.";
                boolean master = tool.equals("cube_master");
                int nlock = 0;
                for (boolean b : locked) if (b) nlock++;
                int have = 0;
                for (ItemStack x : p.getInventory().getStorageContents()) if (tool.equals(ItemData.id(x))) have += x.getAmount();
                if (have < 1 + nlock) return (master ? "명장의 큐브" : "수상한 큐브") + "가 " + (1 + nlock) + "개 필요합니다 (잠금 " + nlock + "줄).";
                for (int k = 0; k <= nlock; k++) take(p, tool);
                Tier next = cur;
                double[] up = master ? new double[]{0.12, 0.047, 0.012} : new double[]{0.06, 0.018, 0};
                if (cur.ordinal() < 3 && r.nextDouble() < up[cur.ordinal()]) next = Tier.values()[cur.ordinal() + 1];
                if (next != cur) locked = new boolean[3];   // 등급이 오르면 잠금 해제
                write(eq, next, locked);
                if (next != cur) {
                    p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.3f);
                    p.sendTitle("", Text.c(next.color + "&l등급 상승! " + next.label), 0, 40, 10);
                    if (next == Tier.LEGENDARY) Text.announce(Text.PREFIX + Text.c("&e" + Text.name(p) + "&f님이 &a레전드리 잠재능력&f을 얻었습니다!"));
                }
                return null;
            }
            default -> {
                return "알 수 없는 아이템";
            }
        }
    }

    public void open(Player p) {
        new PotGui(p).open(p);
    }

    private class PotGui extends Gui {
        private final Player owner;
        private final boolean[] locked = new boolean[3];

        PotGui(Player p) {
            super(3, "&8잠재능력");
            owner = p;
            render();
        }

        private int count(String id) {
            int n = 0;
            for (ItemStack it : owner.getInventory().getStorageContents()) if (id.equals(ItemData.id(it))) n += it.getAmount();
            return n;
        }

        void render() {
            ItemStack eq = inv.getItem(11);
            set(2, button(Material.ARMOR_STAND, "&f▼ 장비"), null);
            Tier t = eq == null ? null : tier(eq);
            List<String> info = new ArrayList<>();
            if (eq == null) info.add("&7장비를 넣어 주세요");
            else if (!eligible(eq)) info.add("&c잠재능력을 가질 수 없는 아이템");
            else if (t == null) info.add("&7잠재능력 없음");
            else info.addAll(lore(eq));
            set(13, button(t == null ? Material.GRAY_DYE : Material.NETHER_STAR, "&e현재 잠재능력", info.toArray(new String[0])), null);
            List<String> lines = eq == null ? List.of() : lore(eq);
            for (int i = 0; i < 3; i++) {   // 옵션 잠그기 (잠근 줄 수만큼 큐브 추가 소모, 최대 2줄)
                int li = i;
                String txt = i + 1 < lines.size() ? lines.get(i + 1) : "&8(없음)";
                set(19 + i, button(locked[i] ? Material.IRON_BARS : Material.TRIPWIRE_HOOK, (locked[i] ? "&c🔒 잠김 " : "&7🔓 ") + (i + 1) + "번째 옵션", txt, "&e▶ 클릭: 잠금 전환"), e -> {
                    int n = 0;
                    for (boolean b : locked) if (b) n++;
                    if (!locked[li] && n >= 2) { Text.actionBar(owner, "&c최대 2줄까지 잠글 수 있습니다"); return; }
                    locked[li] = !locked[li];
                    render();
                });
            }
            String[][] tools = {{"potential_scroll", "잠재능력 부여 주문서"}, {"cube_red", "수상한 큐브"}, {"cube_master", "명장의 큐브"}};
            Material[] icons = {Material.PAPER, Material.REDSTONE_BLOCK, Material.LAPIS_BLOCK};
            for (int i = 0; i < 3; i++) {
                String id = tools[i][0];
                set(15 + (i == 0 ? 0 : i == 1 ? 1 : 2), button(icons[i], "&f" + tools[i][1], "&7보유 " + count(id) + "개", "&e▶ 클릭해서 사용"), e -> {
                    ItemStack cur = inv.getItem(11);
                    if (cur == null) { Text.actionBar(owner, "&c장비를 먼저 넣으세요"); return; }
                    String err = use(owner, cur, id, locked);
                    if (err != null) Text.actionBar(owner, "&c" + err);
                    else owner.playSound(owner.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.2f);
                    render();
                });
            }
            fill(0, 26);
        }

        @Override
        public boolean editable(int raw) {
            return raw == 11;
        }

        @Override
        protected void onEditableClick(InventoryClickEvent e) {
            ItemStack cursor = e.getCursor();
            if (cursor != null && !cursor.getType().isAir() && !eligible(cursor)) e.setCancelled(true);
            org.bukkit.Bukkit.getScheduler().runTask(plugin, this::render);
        }

        @Override
        protected void onBottomClick(InventoryClickEvent e) {
            if (!e.isShiftClick()) return;
            e.setCancelled(true);
            ItemStack cur = e.getCurrentItem();
            if (!eligible(cur) || inv.getItem(11) != null) return;
            inv.setItem(11, cur.clone());
            e.setCurrentItem(null);
            render();
        }

        @Override
        public void onClose(InventoryCloseEvent e) {
            giveBack(owner, 11);
        }
    }
}
