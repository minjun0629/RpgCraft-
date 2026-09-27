package kr.rpgcraft.feature;

import kr.rpgcraft.Keys;
import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.data.PlayerData;
import kr.rpgcraft.gui.Gui;
import kr.rpgcraft.item.*;
import kr.rpgcraft.item.ItemFlavor;
import kr.rpgcraft.passive.Passive;
import kr.rpgcraft.stat.Stat;
import kr.rpgcraft.stat.StatMap;
import kr.rpgcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 대장장이 직업 (자체 설계 수치).
 * 레벨 20 + 전직 비용으로 전직, 5단계 유니크 무기/방어구를 랜덤 스탯으로 제작하고 이름(/명명)과 외형(/외형)을 바꿀 수 있다.
 */
public class BlacksmithManager {
    private static final int[] LEVEL = {20, 40, 60, 80, 99};
    private static final int[] REQ = {100, 225, 350, 450, 550};
    private static final double[] SWORD_ATK = {90, 220, 480, 950, 1800};
    private static final double[] SHIELD_DEF = {5, 7, 9, 11, 13};
    private static final double[] ARMOR_DEF = {4, 6, 8, 10, 12};
    private static final double[] ARMOR_HP = {200, 500, 1000, 2000, 4000};
    private static final long[] COST = {50_000, 300_000, 1_000_000, 5_000_000, 20_000_000};
    private static final String[] TIER_NAME = {"무쇠맹세", "서리벼림", "장인긍지", "용광로심장", "창세불씨"};
    private static final WeaponClass[] CLASSES = {WeaponClass.SWORD, WeaponClass.DAGGER, WeaponClass.AXE, WeaponClass.SHIELD};

    public record Recipe(String baseId, String display, int tier, boolean weapon, WeaponClass wc, ArmorSlot slot,
                         Map<String, Integer> materials, long money) {}

    private final RpgCraft plugin;
    private final List<Recipe> recipes = new ArrayList<>();

    public BlacksmithManager(RpgCraft plugin) {
        this.plugin = plugin;
        registerTemplates();
    }

    private Material weaponMat(WeaponClass wc, int t) {
        String pre = t <= 1 ? "IRON" : t <= 3 ? "DIAMOND" : "NETHERITE";
        return switch (wc) {
            case SWORD -> Material.valueOf(pre + "_SWORD");
            case AXE -> Material.valueOf(pre + "_AXE");
            case DAGGER -> Material.SHEARS;
            default -> Material.SHIELD;
        };
    }

    private Map<String, Integer> mats(int t, String ore) {
        Map<String, Integer> m = new LinkedHashMap<>();
        switch (t) {
            case 0 -> { m.put("mat_iron", 20); m.put("mat_stone", 20); m.put("wood_log", 10); }
            case 1 -> { m.put("mat_silver", 15); m.put("mat_iron", 30); m.put("wood_glow", 10); }
            case 2 -> { m.put("mat_gold", 15); m.put("mat_silver", 20); m.put("crystal_mid", 10); }
            case 3 -> { m.put("mat_crystal", 10); m.put("mat_gold", 20); m.put("crystal_high", 5); m.put(ore, 1); }
            default -> { m.put("mat_crystal", 30); m.put(ore, 3); m.put("crystal_top", 5); m.put("wood_gold", 1); }
        }
        return m;
    }

    private void registerTemplates() {
        String[] ores = {"ore_red", "ore_black", "ore_gray", "ore_blue"};
        for (int t = 0; t < 5; t++) {
            for (int c = 0; c < 4; c++) {
                WeaponClass wc = CLASSES[c];
                String id = "bs_" + wc.name().toLowerCase(Locale.ROOT) + "_" + (t + 1);
                String name = TIER_NAME[t] + " " + wc.label;
                ItemTemplate tp = new ItemTemplate(id, name, weaponMat(wc, t), Category.WEAPON).weapon(wc).grade(Grade.UNIQUE)
                        .desc("대장장이가 직접 제작한 무기");
                tp.model(150 + t);
                plugin.items().reg(tp);
                recipes.add(new Recipe(id, name, t, true, wc, null, mats(t, ores[c]), COST[t]));
            }
            for (ArmorSlot s : ArmorSlot.values()) {
                String id = "bs_armor_" + (t + 1) + "_" + s.name().toLowerCase(Locale.ROOT);
                String name = TIER_NAME[t] + " " + s.label;
                String pre = t <= 1 ? "IRON" : t <= 3 ? "DIAMOND" : "NETHERITE";
                plugin.items().reg(new ItemTemplate(id, name, ItemRegistry.armorMat(pre, s), Category.ARMOR).slot(s).grade(Grade.UNIQUE)
                        .desc("대장장이가 직접 제작한 방어구"));
                recipes.add(new Recipe(id, name, t, false, null, s, mats(t, "ore_green"), COST[t] / 2));
            }
        }
        ItemFlavor.apply(plugin.items());
    }

    public List<Recipe> recipes() {
        return recipes;
    }

    // ------------------------------------------------------------------ 전직
    public void changeJob(Player p) {
        PlayerData d = plugin.data().get(p);
        if (d.blacksmith) {
            Text.msg(p, "&e이미 대장장이입니다. &7(/제작)");
            return;
        }
        int lv = plugin.getConfig().getInt("blacksmith.required-level", 20);
        long cost = plugin.getConfig().getLong("blacksmith.cost", 1_000_000);
        if (d.level < lv) {
            Text.msg(p, "&c레벨 " + lv + " 이상만 전직할 수 있습니다.");
            return;
        }
        if (!plugin.economy().take(p, cost)) {
            Text.msg(p, "&c전직 비용 " + Text.money(cost) + "이 부족합니다.");
            return;
        }
        d.blacksmith = true;
        kr.rpgcraft.util.Text.announce(Text.PREFIX + Text.c("&6" + Text.name(p) + "&f님이 &6대장장이&f로 전직했습니다!"));
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1f);
    }

    // ------------------------------------------------------------------ 제작
    public ItemStack craft(Player p, Recipe r) {
        PlayerData d = plugin.data().get(p);
        EnhanceManager em = plugin.enhance();
        if (!d.blacksmith) { Text.msg(p, "&c대장장이만 제작할 수 있습니다. (/직업 대장장이)"); return null; }
        if (d.level < LEVEL[r.tier()]) { Text.msg(p, "&c제작하려면 레벨 " + LEVEL[r.tier()] + "이 필요합니다."); return null; }
        for (Map.Entry<String, Integer> m : r.materials().entrySet()) {
            if (em.count(p, m.getKey()) < m.getValue()) {
                Text.msg(p, "&c재료 부족: " + plugin.items().get(m.getKey()).name + " x" + m.getValue());
                return null;
            }
        }
        if (!plugin.economy().take(p, r.money())) { Text.msg(p, "&c제작 비용 " + Text.money(r.money()) + "이 부족합니다."); return null; }
        for (Map.Entry<String, Integer> m : r.materials().entrySet()) em.take(p, m.getKey(), m.getValue());

        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int t = r.tier();
        double roll = 0.75 + rnd.nextDouble() * 0.25;
        StatMap st = new StatMap();
        st.set(Stat.LEVEL_REQ, LEVEL[t]);
        if (r.weapon()) {
            double atk = SWORD_ATK[t] * roll;
            switch (r.wc()) {
                case SWORD -> st.add(Stat.ATK, Math.round(atk)).add(Stat.CRIT, -20).add(Stat.REQ_STR, REQ[t]);
                case DAGGER -> st.add(Stat.ATK, Math.round(atk * 0.66)).add(Stat.REQ_DEX, REQ[t]);
                case AXE -> st.add(Stat.ATK, Math.round(atk * 0.75)).add(Stat.REQ_STR, REQ[t] / 2).add(Stat.REQ_DEX, REQ[t] / 2);
                default -> st.add(Stat.ATK, Math.round(atk * 0.38)).add(Stat.DEF, Math.round(SHIELD_DEF[t] * roll * 10) / 10.0).add(Stat.REQ_ADV, REQ[t]);
            }
        } else {
            st.add(Stat.DEF, Math.round(ARMOR_DEF[t] * roll * 10) / 10.0).add(Stat.HP, Math.round(ARMOR_HP[t] * roll));
        }
        // 랜덤 추가 옵션 1줄
        switch (rnd.nextInt(4)) {
            case 0 -> st.add(Stat.CRIT, 1 + rnd.nextInt(5));
            case 1 -> st.add(Stat.HP, (1 + rnd.nextInt(10)) * 100L * (t + 1));
            case 2 -> st.add(Stat.LIFESTEAL, Math.round((0.5 + rnd.nextDouble() * 1.5) * 10) / 10.0);
            default -> st.add(Stat.ARMOR_PEN, 2 + rnd.nextInt(7));
        }
        ItemStack it = plugin.items().createEquipment(r.baseId(), null, Grade.UNIQUE, st);
        ItemData.setString(it, Keys.CRAFTER, Text.name(p));
        ItemData.setString(it, Keys.CRAFTER_UUID, p.getUniqueId().toString());
        if (d.has(Passive.GOLDEN_HAND)) ItemData.setInt(it, Keys.ENH, r.weapon() ? 8 : 3);
        ItemData.refresh(it);
        ItemStack shown = it.clone();
        for (ItemStack left : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
        p.playSound(p.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 1f, 1f);
        plugin.visuals().obtain(p, shown);
        Text.msg(p, "&6제작 완료: &f" + it.getItemMeta().getDisplayName() + " &7(품질 " + (int) (roll * 100) + "%)");
        if (roll >= 0.97) kr.rpgcraft.util.Text.announce(Text.PREFIX + Text.c("&6" + Text.name(p) + "&f님이 최상급 품질의 " + it.getItemMeta().getDisplayName() + "&f을(를) 제작했습니다!"));
        return it;
    }

    /** 제작 창 미리보기: 품질 75%~100% 일 때의 능력치 범위 + 추가 옵션 */
    private List<String> preview(Recipe r) {
        int t = r.tier();
        List<String> out = new ArrayList<>();
        out.add("&f능력치 &7(품질 75% ~ 100%):");
        if (r.weapon()) {
            double lo = SWORD_ATK[t] * 0.75, hi = SWORD_ATK[t];
            switch (r.wc()) {
                case SWORD -> { out.add(" &7공격력 &f" + Math.round(lo) + " ~ " + Math.round(hi)); out.add(" &7치명타 &c-20"); out.add(" &7요구 힘 &f" + REQ[t]); }
                case DAGGER -> { out.add(" &7공격력 &f" + Math.round(lo * 0.66) + " ~ " + Math.round(hi * 0.66)); out.add(" &7요구 민첩 &f" + REQ[t]); }
                case AXE -> { out.add(" &7공격력 &f" + Math.round(lo * 0.75) + " ~ " + Math.round(hi * 0.75)); out.add(" &7요구 힘 &f" + REQ[t] / 2 + " &7· 민첩 &f" + REQ[t] / 2); }
                default -> {
                    out.add(" &7공격력 &f" + Math.round(lo * 0.38) + " ~ " + Math.round(hi * 0.38));
                    out.add(" &7방어력 &f" + Math.round(SHIELD_DEF[t] * 0.75 * 10) / 10.0 + " ~ " + SHIELD_DEF[t]);
                    out.add(" &7요구 모험 &f" + REQ[t]);
                }
            }
        } else {
            out.add(" &7방어력 &f" + Math.round(ARMOR_DEF[t] * 0.75 * 10) / 10.0 + " ~ " + ARMOR_DEF[t]);
            out.add(" &7체력 &f" + Math.round(ARMOR_HP[t] * 0.75) + " ~ " + Math.round(ARMOR_HP[t]));
        }
        out.add(" &7레벨 제한 &f" + LEVEL[t]);
        out.add("&f추가 옵션 1줄 &7(각 25%):");
        out.add(" &7치명타 +1~5 · 체력 +" + 100 * (t + 1) + "~" + 1000 * (t + 1));
        out.add(" &7흡혈 +0.5~2.0% · 방어 관통 +2~8");
        return out;
    }

    private boolean isCrafter(Player p, ItemStack it) {
        return p.getUniqueId().toString().equals(ItemData.getString(it, Keys.CRAFTER_UUID)) || p.hasPermission("rpgcraft.admin");
    }

    public void rename(Player p, String name) {
        ItemStack it = p.getInventory().getItemInMainHand();
        if (!isCrafter(p, it)) { Text.msg(p, "&c직접 제작한 장비만 이름을 바꿀 수 있습니다."); return; }
        if (name.length() > 20) { Text.msg(p, "&c이름은 20자 이하입니다."); return; }
        ItemData.setString(it, Keys.NAME, name);
        ItemData.refresh(it);
        Text.msg(p, "&a이름을 변경했습니다: " + it.getItemMeta().getDisplayName());
    }

    public void look(Player p, String arg) {
        ItemStack it = p.getInventory().getItemInMainHand();
        if (!isCrafter(p, it)) { Text.msg(p, "&c직접 제작한 장비만 외형을 바꿀 수 있습니다."); return; }
        ItemMeta m = it.getItemMeta();
        int model = Text.parseInt(arg, -1);
        if (model < 0) { Text.msg(p, "&c/외형 <모델번호> &7(리소스팩 모델 번호, 0=기본)"); return; }
        m.setCustomModelData(model == 0 ? null : model);
        it.setItemMeta(m);
        Text.msg(p, "&a외형(모델 " + model + ")을 적용했습니다.");
    }

    public void open(Player p) {
        new CraftGui(p, 0).open(p);
    }

    private class CraftGui extends Gui {
        CraftGui(Player p, int tier) {
            super(6, "&6대장장이 제작 - " + TIER_NAME[tier] + " (Lv." + LEVEL[tier] + ")");
            for (int t = 0; t < 5; t++) {
                int tt = t;
                set(45 + t, button(t == tier ? Material.LIME_STAINED_GLASS_PANE : Material.WHITE_STAINED_GLASS_PANE,
                        (t == tier ? "&a" : "&f") + TIER_NAME[t] + " 단계", "&7Lv." + LEVEL[t]), e -> new CraftGui(p, tt).open(p));
            }
            int slot = 10;
            for (Recipe r : recipes) {
                if (r.tier() != tier) continue;
                List<String> lore = new ArrayList<>();
                lore.add("&7제작 레벨 " + LEVEL[tier]);
                lore.add("");
                lore.add("&f재료:");
                for (Map.Entry<String, Integer> m : r.materials().entrySet()) {
                    int have = plugin.enhance().count(p, m.getKey());
                    lore.add((have >= m.getValue() ? " &a" : " &c") + plugin.items().get(m.getKey()).name + " " + have + "/" + m.getValue());
                }
                lore.add("&f비용: &e" + Text.money(r.money()));
                lore.add("");
                lore.addAll(preview(r));   // 능력치 (v5.4.7)
                lore.add("");
                lore.add("&7능력치는 제작 시 75~100% 품질로 랜덤 결정");
                lore.add("&e클릭하여 제작");
                ItemTemplate tp = plugin.items().get(r.baseId());
                ItemStack icon = button(tp.material, "&e" + r.display(), lore.toArray(new String[0]));
                set(slot, icon, e -> {
                    p.closeInventory();
                    craft(p, r);
                });
                slot++;
                if (slot % 9 == 8) slot += 2;
            }
            fill(0, 53);
        }
    }
}
