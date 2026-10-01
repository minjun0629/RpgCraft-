package kr.rpgcraft.feature;

import kr.rpgcraft.Keys;
import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.data.PlayerData;
import kr.rpgcraft.gui.Gui;
import kr.rpgcraft.item.ItemData;
import kr.rpgcraft.item.WeaponClass;
import kr.rpgcraft.mob.MonsterTierManager;
import kr.rpgcraft.stat.Stat;
import kr.rpgcraft.stat.StatMap;
import kr.rpgcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.LivingEntity;
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
 * v5.10.30 숙련도 (/숙련). 각각 1 ~ 50 레벨.
 *  생활: 채집 · 낚시 · 요리 · 대장장이 — 하면 할수록 결과가 좋아진다.
 *  전투: 무기 종류마다 (검 · 단검 · 도끼 · 방패 · 창 · 몽둥이 · 활 · 지팡이) — 그 무기를 들고 처치하면 오르고, 그 무기를 들면 주는 피해 ↑.
 *  직업: 지금 직업(히든 직업 포함)으로 처치하면 오르고, 직업에 맞는 능력치가 붙는다.
 */
public class MasteryManager implements Listener, CommandExecutor {
    public static final int MAX = 50;

    public enum Life {
        GATHER("채집", Material.IRON_PICKAXE, "더 얻을 확률 +1%/Lv · 희귀 재료 ↑ · 쿨타임 -0.6%/Lv"),
        FISH("낚시", Material.FISHING_ROD, "희귀 어종 확률 +1%/Lv · 물고기 크기 ↑"),
        COOK("요리", Material.CAMPFIRE, "두 개 만들 확률 +0.4%/Lv · 음식 지속 +1%/Lv"),
        SMITH("대장장이", Material.ANVIL, "최저 품질 +0.3%p/Lv (75% → 90%)");

        public final String label, effect;
        public final Material icon;

        Life(String label, Material icon, String effect) {
            this.label = label;
            this.icon = icon;
            this.effect = effect;
        }
    }

    /** 전투 숙련 무기 종류 */
    public static final String[] WEAPONS = {"SWORD", "DAGGER", "AXE", "SHIELD", "SPEAR", "CLUB", "BOW", "STAFF"};
    private static final String[] WEAPON_LABEL = {"검", "단검", "도끼", "방패", "창", "몽둥이", "활", "지팡이"};
    private static final Material[] WEAPON_ICON = {Material.IRON_SWORD, Material.SHEARS, Material.IRON_AXE, Material.SHIELD, Material.TRIDENT,
            Material.STICK, Material.BOW, Material.BLAZE_ROD};

    private final RpgCraft plugin;

    public MasteryManager(RpgCraft plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // ------------------------------------------------------------------ 레벨 · 경험치
    private static double need(int lv, boolean combat) {
        return combat ? 30 + lv * 20.0 : 20 + lv * 12.0;
    }

    public int level(PlayerData d, String key) {
        return Math.min(MAX, (int) d.counter("ms_lv_" + key));
    }

    public int level(PlayerData d, Life l) {
        return level(d, l.name());
    }

    private void add(Player p, String key, String label, double n, boolean combat) {
        if (p == null || n <= 0) return;
        PlayerData d = plugin.data().get(p);
        int lv = level(d, key);
        if (lv >= MAX) return;
        double xp = d.counter("ms_xp_" + key) + n * plugin.getConfig().getDouble("mastery.exp-mult", 1.0);
        boolean up = false;
        while (lv < MAX && xp >= need(lv, combat)) {
            xp -= need(lv, combat);
            lv++;
            up = true;
        }
        d.counters.put("ms_lv_" + key, (double) lv);
        d.counters.put("ms_xp_" + key, lv >= MAX ? 0 : xp);
        if (up) {
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.8f);
            Text.msg(p, "&b✦ " + label + " 숙련도 &e" + lv + "레벨" + (lv >= MAX ? " &6(최고!)" : "") + " &7(/숙련)");
            if (combat) plugin.stats().refresh(p);
        }
    }

    public void add(Player p, Life l, double n) {
        add(p, l.name(), l.label, n, false);
    }

    // ------------------------------------------------------------------ 생활 효과 (각 기능에서 부름)
    public double gatherExtraChance(PlayerData d) { return level(d, Life.GATHER) * 0.01; }

    public int gatherTierBonus(PlayerData d) { return level(d, Life.GATHER) / 25; }

    public double gatherCooldownMult(PlayerData d) { return 1 - level(d, Life.GATHER) * 0.006; }

    public double fishLuck(PlayerData d) { return 1 + level(d, Life.FISH) * 0.01; }

    public double fishSize(PlayerData d) { return level(d, Life.FISH) * 0.004; }

    public double cookDouble(PlayerData d) { return level(d, Life.COOK) * 0.004; }

    public double cookDuration(PlayerData d) { return 1 + level(d, Life.COOK) * 0.01; }

    public double smithMinQuality(PlayerData d) { return 0.75 + level(d, Life.SMITH) * 0.003; }

    // ------------------------------------------------------------------ 전투 · 직업
    public static String weaponKey(ItemStack it) {
        if (it == null || it.getType().isAir()) return null;
        if (it.getType() == Material.BOW || it.getType() == Material.CROSSBOW) return "BOW";
        String id = ItemData.id(it);
        if (id != null && (id.contains("staff") || id.contains("wand"))) return "STAFF";
        WeaponClass wc = ItemData.weaponClass(it);
        return wc == null ? null : wc.name();
    }

    private static int weaponIndex(String key) {
        for (int i = 0; i < WEAPONS.length; i++) if (WEAPONS[i].equals(key)) return i;
        return -1;
    }

    /** 지금 직업의 숙련 키 (히든 직업은 갈래마다, 무직이면 null) */
    public static String jobKey(PlayerData d) {
        var hj = kr.rpgcraft.world.HiddenJobManager.of(d);
        if (hj != null) return "JOB_H_" + hj.line();
        JobManager.Base b = JobManager.base(d);
        return b == null ? null : "JOB_" + b.name();
    }

    private static String jobLabel(PlayerData d) {
        var hj = kr.rpgcraft.world.HiddenJobManager.of(d);
        if (hj != null) return "히든 직업";
        JobManager.Base b = JobManager.base(d);
        return b == null ? "무직" : b.label;
    }

    /** 들고 있는 무기 숙련에 따른 주는 피해 배율 (JobManager.outgoingMult 에서 곱함) */
    public double damageMult(Player p) {
        String k = weaponKey(p.getInventory().getItemInMainHand());
        if (k == null) return 1;
        return 1 + level(plugin.data().get(p), "W_" + k) * plugin.getConfig().getDouble("mastery.weapon-dmg-per-level", 0.004);
    }

    /** 직업 숙련 능력치 (StatCalculator 에서 더함) */
    public StatMap bonus(PlayerData d) {
        String k = jobKey(d);
        StatMap m = new StatMap();
        if (k == null) return m;
        int lv = level(d, k);
        if (lv <= 0) return m;
        if (k.startsWith("JOB_H_")) return m.add(Stat.STR_PCT, lv * 0.15).add(Stat.DEX_PCT, lv * 0.15).add(Stat.ADV_PCT, lv * 0.15).add(Stat.HP_PCT, lv * 0.2);
        return switch (k) {
            case "JOB_WARRIOR" -> m.add(Stat.STR_PCT, lv * 0.3).add(Stat.HP_PCT, lv * 0.2);
            case "JOB_ARCHER" -> m.add(Stat.DEX_PCT, lv * 0.3).add(Stat.CRIT, lv * 0.1);
            case "JOB_ROGUE" -> m.add(Stat.CRIT_DMG, lv * 0.8).add(Stat.DODGE, lv * 0.1);
            case "JOB_GUARDIAN" -> m.add(Stat.ADV_PCT, lv * 0.3).add(Stat.DEF, lv * 0.2);
            case "JOB_MAGE" -> m.add(Stat.MAGIC, lv * 15.0).add(Stat.HP_PCT, lv * 0.2);
            default -> m;
        };
    }

    private String jobEffect(String k) {
        if (k == null) return "&8직업이 없습니다";
        if (k.startsWith("JOB_H_")) return "힘 · 민첩 · 모험 +0.15%/Lv · 체력 +0.2%/Lv";
        return switch (k) {
            case "JOB_WARRIOR" -> "힘 +0.3%/Lv · 체력 +0.2%/Lv";
            case "JOB_ARCHER" -> "민첩 +0.3%/Lv · 치명타 +0.1/Lv";
            case "JOB_ROGUE" -> "치명타 피해 +0.8%/Lv · 회피 +0.1%/Lv";
            case "JOB_GUARDIAN" -> "모험 +0.3%/Lv · 방어력 +0.2/Lv";
            case "JOB_MAGE" -> "마력 +15/Lv · 체력 +0.2%/Lv";
            default -> "";
        };
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent e) {
        Player k = e.getEntity().getKiller();
        LivingEntity v = e.getEntity();
        if (k == null || v instanceof Player) return;
        boolean boss = v.getPersistentDataContainer().has(Keys.BOSS, PersistentDataType.STRING);
        MonsterTierManager.Tier t = plugin.tiers() != null ? plugin.tiers().tier(v) : MonsterTierManager.Tier.NORMAL;
        double n = boss ? 60 : t == MonsterTierManager.Tier.MINIBOSS ? 15 : t == MonsterTierManager.Tier.ELITE ? 6
                : plugin.customMobs() != null && plugin.customMobs().of(v) != null ? 3 : 2;
        PlayerData d = plugin.data().get(k);
        String w = weaponKey(k.getInventory().getItemInMainHand());
        int wi = weaponIndex(w);
        if (wi >= 0) add(k, "W_" + w, WEAPON_LABEL[wi], n, true);
        String jk = jobKey(d);
        if (jk != null) add(k, jk, jobLabel(d), n, true);
    }

    // ------------------------------------------------------------------ 창
    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (s instanceof Player p) open(p);
        return true;
    }

    private ItemStack icon(PlayerData d, String key, String label, Material m, String effect, boolean combat, String now) {
        int lv = level(d, key);
        double xp = d.counter("ms_xp_" + key), nd = need(lv, combat);
        List<String> lore = new ArrayList<>();
        lore.add("&f레벨 &e" + lv + " &7/ " + MAX);
        if (lv < MAX) {
            lore.add(Text.bar(xp / nd, 20, "&b", "&8"));
            lore.add("&7다음 레벨까지 &f" + (int) xp + " / " + (int) nd);
        } else lore.add("&6최고 레벨!");
        lore.add("");
        lore.add("&a" + effect);
        if (now != null) lore.add("&b지금: " + now);
        return Gui.button(lv >= MAX ? Material.NETHER_STAR : m, (lv >= MAX ? "&6&l" : "&f&l") + label + " 숙련", lore.toArray(new String[0]));
    }

    public void open(Player p) {
        PlayerData d = plugin.data().get(p);
        Gui g = new Gui(6, "&8숙련도") {
        };
        g.set(4, Gui.button(Material.BOOK, "&b&l숙련도", "&7할수록 좋아지는 생활 · 전투 · 직업 숙련 (최대 " + MAX + ")",
                "&7생활: 채집 · 낚시 · 요리 · 대장장이를 하면 오름", "&7전투: 그 무기를 들고 처치하면 오름 (일반 2 · 정예 6 · 보스 60)",
                "&7직업: 지금 직업으로 처치하면 오름"), null);
        g.set(9, Gui.button(Material.OAK_SIGN, "&a&l생활 숙련"), null);
        int i = 0;
        for (Life l : Life.values()) {
            String now = switch (l) {
                case GATHER -> "더 얻을 확률 +" + Math.round(gatherExtraChance(d) * 100) + "% · 쿨타임 -" + Math.round((1 - gatherCooldownMult(d)) * 100) + "%";
                case FISH -> "희귀 확률 ×" + String.format("%.2f", fishLuck(d));
                case COOK -> "두 개 +" + String.format("%.1f", cookDouble(d) * 100) + "% · 지속 ×" + String.format("%.2f", cookDuration(d));
                case SMITH -> "품질 " + Math.round(smithMinQuality(d) * 100) + "% ~ 100%";
            };
            g.set(11 + i * 2, icon(d, l.name(), l.label, l.icon, l.effect, false, now), null);
            i++;
        }
        g.set(27, Gui.button(Material.IRON_SWORD, "&c&l전투 숙련", "&7무기를 들면 주는 피해 +0.4%/Lv"), null);
        String held = weaponKey(p.getInventory().getItemInMainHand());
        for (int w = 0; w < WEAPONS.length; w++) {
            int lv = level(d, "W_" + WEAPONS[w]);
            String now = "주는 피해 +" + String.format("%.1f", lv * plugin.getConfig().getDouble("mastery.weapon-dmg-per-level", 0.004) * 100) + "%"
                    + (WEAPONS[w].equals(held) ? " &a(지금 들고 있음)" : "");
            g.set(28 + w, icon(d, "W_" + WEAPONS[w], WEAPON_LABEL[w], WEAPON_ICON[w], "이 무기를 들면 주는 피해 +0.4%/Lv", true, now), null);
        }
        String jk = jobKey(d);
        if (jk != null) g.set(40, icon(d, jk, jobLabel(d), Material.NAME_TAG, jobEffect(jk), true, null), null);
        else g.set(40, Gui.button(Material.GRAY_DYE, "&8직업 숙련", "&7직업을 고르면 직업 숙련이 생깁니다 (/직업)"), null);
        g.set(36, Gui.button(Material.NAME_TAG, "&d&l직업 숙련", "&7직업마다 따로 쌓임 (바꾸면 그 직업 숙련으로)"), null);
        g.fill(0, 53);
        g.open(p);
    }
}
