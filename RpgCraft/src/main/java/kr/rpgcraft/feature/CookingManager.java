package kr.rpgcraft.feature;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.data.PlayerData;
import kr.rpgcraft.gui.Gui;
import kr.rpgcraft.item.ItemData;
import kr.rpgcraft.stat.Stat;
import kr.rpgcraft.stat.StatMap;
import kr.rpgcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * v5.10.20 요리 (/요리). 식재료(요리 재료 상점 · 낚시 · 사냥 전리품, 일반 마인크래프트 재료도 됨)로 버프 음식을 만든다.
 * 음식을 들고 우클릭하면 먹고 일정 시간 능력치 버프 (한 번에 하나, 새로 먹으면 바뀜). 요리를 많이 할수록 두 개 만들어질 확률이 오름.
 */
public class CookingManager implements Listener, CommandExecutor {
    /** 식재료: 아이템 ID (요리 재료 상점) + 대신 쓸 수 있는 일반 재료 */
    public record Ingredient(String id, String name, Material icon, long price, Material... vanilla) {}

    public record Recipe(String key, String name, Material icon, int minutes, String effect, StatMap buff, String[] needs) {}

    public static final List<Ingredient> INGREDIENTS = List.of(
            new Ingredient("ing_potato", "감자", Material.POTATO, 800, Material.POTATO, Material.BAKED_POTATO),
            new Ingredient("ing_carrot", "당근", Material.CARROT, 800, Material.CARROT),
            new Ingredient("ing_meat", "고기", Material.BEEF, 2000, Material.BEEF, Material.COOKED_BEEF, Material.PORKCHOP, Material.COOKED_PORKCHOP, Material.MUTTON, Material.COOKED_MUTTON),
            new Ingredient("ing_chicken", "닭고기", Material.CHICKEN, 1500, Material.CHICKEN, Material.COOKED_CHICKEN),
            new Ingredient("ing_mushroom", "버섯", Material.BROWN_MUSHROOM, 1000, Material.BROWN_MUSHROOM, Material.RED_MUSHROOM),
            new Ingredient("ing_flour", "밀가루", Material.WHEAT, 1000, Material.WHEAT),
            new Ingredient("ing_sugar", "설탕", Material.SUGAR, 1000, Material.SUGAR, Material.SUGAR_CANE),
            new Ingredient("ing_egg", "달걀", Material.EGG, 1200, Material.EGG),
            new Ingredient("ing_apple", "사과", Material.APPLE, 1500, Material.APPLE),
            new Ingredient("ing_spice", "향신료", Material.GLOWSTONE_DUST, 5000));

    /** 재료 표기: "ID:개수", 생선은 "#fish:개수" (낚시로 잡은 아무 물고기), 전리품은 그 아이템 ID */
    public static final List<Recipe> RECIPES = List.of(
            new Recipe("stew", "든든한 스튜", Material.RABBIT_STEW, 15, "체력 +10%",
                    StatMap.of(Stat.HP_PCT, 10), new String[]{"ing_potato:3", "ing_carrot:3", "ing_meat:2"}),
            new Recipe("skewer", "불꽃 고기 꼬치", Material.COOKED_BEEF, 15, "힘 · 민첩 · 모험 +6%",
                    StatMap.of(Stat.STR_PCT, 6, Stat.DEX_PCT, 6, Stat.ADV_PCT, 6), new String[]{"ing_meat:4", "ing_spice:1"}),
            new Recipe("fish_set", "생선 구이 정식", Material.COOKED_SALMON, 15, "치명타 +5% · 치명타 피해 +20%",
                    StatMap.of(Stat.CRIT, 5, Stat.CRIT_DMG, 20), new String[]{"#fish:3", "ing_potato:1"}),
            new Recipe("mushroom", "숲의 버섯 수프", Material.MUSHROOM_STEW, 15, "방어력 +6",
                    StatMap.of(Stat.DEF, 6), new String[]{"ing_mushroom:4", "ing_carrot:1"}),
            new Recipe("cake", "꿀 케이크", Material.PUMPKIN_PIE, 15, "경험치 +15%",
                    StatMap.of(Stat.EXP_PCT, 15), new String[]{"ing_flour:3", "ing_sugar:2", "ing_egg:1"}),
            new Recipe("lunchbox", "사냥꾼 도시락", Material.BREAD, 15, "이동 속도 +12 · 회피 +3%",
                    StatMap.of(Stat.SPEED, 12, Stat.DODGE, 3), new String[]{"ing_flour:2", "ing_chicken:2", "ing_apple:1"}),
            new Recipe("feast", "왕의 만찬", Material.GOLDEN_CARROT, 30, "힘 · 민첩 · 모험 +10% · 체력 +10% · 경험치 +10%",
                    StatMap.of(Stat.STR_PCT, 10, Stat.DEX_PCT, 10, Stat.ADV_PCT, 10, Stat.HP_PCT, 10, Stat.EXP_PCT, 10),
                    new String[]{"#fish:5", "ing_meat:3", "ing_spice:2", "loot_core:1"}));

    public static String foodId(Recipe r) {
        return "food_" + r.key();
    }

    private final RpgCraft plugin;

    public CookingManager(RpgCraft plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /** 지금 먹은 음식의 버프 */
    public StatMap bonus(PlayerData d) {
        if (d.counter("food_until") < System.currentTimeMillis() || !d.counters.containsKey("food_key")) return new StatMap();
        int i = (int) d.counter("food_key");
        return i >= 0 && i < RECIPES.size() ? RECIPES.get(i).buff() : new StatMap();
    }

    // ------------------------------------------------------------------ 재료 세기 · 빼기
    private boolean matches(ItemStack it, String need) {
        if (it == null || it.getType().isAir()) return false;
        String id = ItemData.id(it);
        if (need.equals("#fish")) return id != null && id.startsWith("fish_") && !id.equals("fish_treasure");
        if (id != null) return id.equals(need);
        for (Ingredient g : INGREDIENTS) if (g.id().equals(need)) for (Material m : g.vanilla()) if (it.getType() == m) return true;
        return false;
    }

    private int count(Player p, String need) {
        int n = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) if (matches(it, need)) n += it.getAmount();
        return n;
    }

    private void take(Player p, String need, int n) {
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (n <= 0) return;
            if (!matches(it, need)) continue;
            int t = Math.min(n, it.getAmount());
            it.setAmount(it.getAmount() - t);
            n -= t;
        }
    }

    private String needName(String need) {
        if (need.equals("#fish")) return "아무 물고기";
        for (Ingredient g : INGREDIENTS) if (g.id().equals(need)) return g.name();
        var t = plugin.items().get(need);
        return t == null ? need : Text.strip(Text.c(t.name));
    }

    // ------------------------------------------------------------------ GUI
    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (s instanceof Player p) open(p);
        return true;
    }

    public void open(Player p) {
        PlayerData d = plugin.data().get(p);
        int cooked = (int) d.counter("cook_count");
        double dbl = doubleChance(d);
        Gui g = new Gui(4, "&6🍳 요리") {
        };
        g.set(4, Gui.button(Material.CAMPFIRE, "&6&l요리 솜씨 &e" + cooked + "회",
                "&7두 개 만들어질 확률 &e" + Math.round(dbl * 100) + "% &8(50회마다 +5%, 최대 25%)",
                "", "&7음식을 들고 우클릭하면 먹고 버프를 받습니다", "&7버프는 한 번에 하나 (새로 먹으면 바뀜)",
                bonusLine(d)), null);
        int[] slots = {10, 11, 12, 13, 14, 15, 16};
        for (int i = 0; i < RECIPES.size() && i < slots.length; i++) {
            Recipe r = RECIPES.get(i);
            List<String> lore = new ArrayList<>();
            lore.add("&a" + r.effect() + " &7(" + r.minutes() + "분)");
            lore.add("");
            boolean ok = true;
            for (String n : r.needs()) {
                String[] kv = n.split(":");
                int have = count(p, kv[0]), want = Integer.parseInt(kv[1]);
                ok &= have >= want;
                lore.add((have >= want ? "&a✔ " : "&c✘ ") + "&f" + needName(kv[0]) + " &7" + have + "/" + want);
            }
            lore.add("");
            lore.add(ok ? "&e▶ 클릭하여 요리 (쉬프트: 가능한 만큼)" : "&8재료가 부족합니다");
            boolean fok = ok;
            g.set(slots[i], Gui.button(r.icon(), "&6&l" + r.name(), lore.toArray(new String[0])), e -> {
                if (!fok) return;
                int times = e.isShiftClick() ? 16 : 1, made = 0;
                for (int t = 0; t < times && cook(p, r); t++) made++;
                if (made > 0) open(p);
            });
        }
        g.set(31, Gui.button(Material.BARREL, "&e요리 재료 상점", "&7감자 · 고기 · 밀가루 · 향신료 …", "&7생선은 낚시, 마력 핵은 사냥으로", "&e▶ 클릭"), e -> {
            if (plugin.shops().get("cook") != null) plugin.shops().open(p, "cook", 0);
        });
        g.fill(0, 35);
        g.open(p);
    }

    private String bonusLine(PlayerData d) {
        long left = (long) d.counter("food_until") - System.currentTimeMillis();
        if (left <= 0 || !d.counters.containsKey("food_key")) return "&8지금 먹은 음식 없음";
        return "&a지금: " + RECIPES.get((int) d.counter("food_key")).name() + " &7(" + Text.time(left / 1000) + " 남음)";
    }

    private double doubleChance(PlayerData d) {
        return Math.min(0.25, (int) (d.counter("cook_count") / 50) * 0.05) + (plugin.mastery() != null ? plugin.mastery().cookDouble(d) : 0);   // + 요리 숙련
    }

    private boolean cook(Player p, Recipe r) {
        for (String n : r.needs()) {
            String[] kv = n.split(":");
            if (count(p, kv[0]) < Integer.parseInt(kv[1])) return false;
        }
        for (String n : r.needs()) {
            String[] kv = n.split(":");
            take(p, kv[0], Integer.parseInt(kv[1]));
        }
        PlayerData d = plugin.data().get(p);
        int n = ThreadLocalRandom.current().nextDouble() < doubleChance(d) ? 2 : 1;
        d.counters.merge("cook_count", 1.0, Double::sum);
        ItemStack food = plugin.items().create(foodId(r), n);
        if (food != null) for (ItemStack left : p.getInventory().addItem(food).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
        p.playSound(p.getLocation(), Sound.BLOCK_SMOKER_SMOKE, 1f, 1.2f);
        p.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, p.getLocation().add(0, 1.6, 0), 4, 0.2, 0.1, 0.2, 0.01);
        Text.actionBar(p, "&6🍳 " + r.name() + (n > 1 ? " &ex2 &7(솜씨 덕분에 하나 더!)" : "") + " 완성");
        if (plugin.seasonPass() != null) plugin.seasonPass().add(p, 5, "요리");
        if (plugin.mastery() != null) plugin.mastery().add(p, MasteryManager.Life.COOK, 12);
        return true;
    }

    // ------------------------------------------------------------------ 먹기
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEat(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        ItemStack it = p.getInventory().getItemInMainHand();
        String id = ItemData.id(it);
        if (id == null || !id.startsWith("food_")) return;
        e.setCancelled(true);
        int idx = -1;
        for (int i = 0; i < RECIPES.size(); i++) if (foodId(RECIPES.get(i)).equals(id)) idx = i;
        if (idx < 0) return;
        Recipe r = RECIPES.get(idx);
        PlayerData d = plugin.data().get(p);
        it.setAmount(it.getAmount() - 1);
        double dm = plugin.mastery() != null ? plugin.mastery().cookDuration(d) : 1;   // 요리 숙련: 지속 시간 ↑
        long until = System.currentTimeMillis() + (long) (r.minutes() * 60_000L * dm);
        d.counters.put("food_key", (double) idx);
        d.counters.put("food_until", (double) until);
        plugin.stats().refresh(p);
        p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EAT, 1f, 1f);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_BURP, 0.6f, 1f);
        p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 2, 0), 3, 0.3, 0.2, 0.3);
        Text.msg(p, "&6🍳 " + r.name() + "&f을(를) 먹었습니다. &a" + r.effect() + " &7(" + r.minutes() + "분)");
        Bukkit.getScheduler().runTaskLater(plugin, () -> {   // 끝나면 능력치 다시 계산
            if (p.isOnline() && plugin.data().get(p).counter("food_until") <= System.currentTimeMillis() + 500) {
                plugin.stats().refresh(p);
                Text.actionBar(p, "&7" + r.name() + " 효과가 끝났습니다.");
            }
        }, (long) (r.minutes() * 1200L * dm) + 20);
    }
}
