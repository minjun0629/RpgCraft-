package kr.rpgcraft.feature;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.data.PlayerData;
import kr.rpgcraft.gui.Gui;
import kr.rpgcraft.gui.UiIcon;
import kr.rpgcraft.mob.MobManager;
import kr.rpgcraft.stat.Stat;
import kr.rpgcraft.stat.StatMap;
import kr.rpgcraft.util.Text;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.*;

/**
 * v5.10.59 몬스터 영혼석 수집 (/영혼석).
 * 몬스터를 잡으면 낮은 확률로 그 몬스터의 영혼석을 얻는다 (일반 0.4% · 정예 · 중간 보스 ×5 · 보스 35%, 근처에서 함께 싸운 사람 모두 굴림).
 *  - 같은 영혼석을 모으면 별이 오름: 1 · 3 · 7 · 15 · 30개 → 1~5성. 5성을 넘긴 영혼석은 별 조각 1개로 바뀜 (별자리)
 *  - 별마다 계열 능력치: 언데드 최대 체력 · 야수 치명타 피해 · 마법 치명타 · 인간형 방어력무시 · 기타 방어력 · 보스 힘 · 민첩 · 모험
 *  - 계열 세트: 그 계열의 별 합계가 10 · 25 · 50 이 되면 추가 효과
 * 저장: counters "soul_<키>" = 모은 수. 키 · 이름 · 계열은 plugins/RpgCraft/souls.yml (처음 얻을 때 기록)
 */
public class SoulManager {
    public enum Cat {
        UNDEAD("언데드", "&2", Stat.HP_PCT, 0.4), BEAST("야수", "&6", Stat.CRIT_DMG, 0.8), MAGIC("마법", "&d", Stat.CRIT, 0.2),
        HUMANOID("인간형", "&c", Stat.ARMOR_PEN, 0.3), OTHER("기타", "&7", Stat.DEF, 0.25), BOSS("보스", "&4", null, 0.4);

        final String label, color;
        final Stat stat;
        final double perStar;

        Cat(String label, String color, Stat stat, double perStar) {
            this.label = label;
            this.color = color;
            this.stat = stat;
            this.perStar = perStar;
        }
    }

    private record Info(String name, Cat cat, String type) {}

    private static final int[] STAR_AT = {1, 3, 7, 15, 30};
    private static final int[] SET_AT = {10, 25, 50};
    private final RpgCraft plugin;
    private final File file;
    private final Map<String, Info> infos = new LinkedHashMap<>();

    public SoulManager(RpgCraft plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "souls.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String k : y.getKeys(false)) {
            try {
                infos.put(k, new Info(y.getString(k + ".name", k), Cat.valueOf(y.getString(k + ".cat", "OTHER")), y.getString(k + ".type", "ZOMBIE")));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        infos.forEach((k, v) -> {
            y.set(k + ".name", v.name);
            y.set(k + ".cat", v.cat.name());
            y.set(k + ".type", v.type);
        });
        try {
            y.save(file);
        } catch (java.io.IOException ignored) {
        }
    }

    static Cat catOf(EntityType t) {
        switch (t.name()) {
            case "ZOMBIE", "SKELETON", "STRAY", "HUSK", "DROWNED", "ZOMBIE_VILLAGER", "WITHER_SKELETON", "PHANTOM", "ZOMBIFIED_PIGLIN", "ZOGLIN", "SKELETON_HORSE", "ZOMBIE_HORSE":
                return Cat.UNDEAD;
            case "SPIDER", "CAVE_SPIDER", "WOLF", "RAVAGER", "HOGLIN", "SILVERFISH", "ENDERMITE", "POLAR_BEAR", "BEE", "GUARDIAN", "ELDER_GUARDIAN", "PANDA", "GOAT", "LLAMA", "FOX":
                return Cat.BEAST;
            case "WITCH", "EVOKER", "VEX", "ILLUSIONER", "ENDERMAN", "BLAZE", "GHAST", "SHULKER", "SLIME", "MAGMA_CUBE", "ALLAY", "WARDEN":
                return Cat.MAGIC;
            case "PILLAGER", "VINDICATOR", "PIGLIN", "PIGLIN_BRUTE", "VILLAGER", "IRON_GOLEM", "CREEPER":
                return Cat.HUMANOID;
            default:
                return Cat.OTHER;
        }
    }

    public static int stars(int count) {
        int s = 0;
        for (int a : STAR_AT) if (count >= a) s++;
        return s;
    }

    private int count(PlayerData d, String k) {
        return (int) d.counter("soul_" + k);
    }

    /** 몬스터가 쓰러질 때 (MobManager). 근처에서 싸운 사람마다 굴림 */
    public void onKill(LivingEntity ent, MobManager.MobState s, Collection<Player> players) {
        if (!plugin.getConfig().getBoolean("souls.enabled", true) || s == null || players.isEmpty()) return;
        String key;
        Cat cat;
        double chance = plugin.getConfig().getDouble("souls.chance", 0.004);
        if (s.bossId != null) {
            key = "b_" + s.bossId;
            cat = Cat.BOSS;
            chance = plugin.getConfig().getDouble("souls.boss-chance", 0.35);
        } else {
            var def = plugin.customMobs().of(ent);
            key = def != null ? "c_" + def.id : "v_" + ent.getType().name();
            cat = catOf(ent.getType());
            if (plugin.tiers() != null && plugin.tiers().tier(ent) != kr.rpgcraft.mob.MonsterTierManager.Tier.NORMAL)
                chance *= plugin.getConfig().getDouble("souls.elite-mult", 5);
        }
        String name = s.baseName != null ? Text.strip(Text.c(s.baseName)) : ent.getType().name();
        for (Player p : players) {
            if (java.util.concurrent.ThreadLocalRandom.current().nextDouble() >= chance) continue;
            if (!infos.containsKey(key)) {
                infos.put(key, new Info(name, cat, ent.getType().name()));
                save();
            }
            give(p, key);
        }
    }

    private void give(Player p, String key) {
        PlayerData d = plugin.data().get(p);
        Info in = infos.get(key);
        int before = count(d, key);
        if (stars(before) >= 5 && before >= STAR_AT[4]) {   // 5성 넘김 → 별 조각
            plugin.constellation().gainShards(p, 1, in.name + " 영혼석 (5성)");
            return;
        }
        d.addCounter("soul_" + key, 1);
        int sb = stars(before), sa = stars(before + 1);
        p.getWorld().spawnParticle(Particle.SOUL, p.getLocation().add(0, 1.2, 0), 14, 0.4, 0.5, 0.4, 0.03);
        p.playSound(p.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 1f, 1.2f);
        if (sa > sb) {
            plugin.stats().refresh(p);
            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1.3f);
            Text.msg(p, "&3✦ 영혼석 " + in.cat.color + in.name + " &f" + "★".repeat(sa) + "&8" + "★".repeat(5 - sa) + " &f달성! &7(" + in.cat.label + " · " + bonusText(in.cat, 1) + " / 별)");
            if (sa == 5 && in.cat == Cat.BOSS)
                Text.announce(Text.PREFIX + Text.c("&3✦ &f" + Text.name(p) + "님이 보스 영혼석 &4" + in.name + " &f5성을 완성했습니다!"));
        } else Text.actionBar(p, "&3✦ 영혼석 " + in.cat.color + in.name + " &f+1 &7(" + (before + 1) + "/" + next(before + 1) + ")");
    }

    private static int next(int c) {
        for (int a : STAR_AT) if (c < a) return a;
        return STAR_AT[4];
    }

    private static String bonusText(Cat c, int stars) {
        if (c == Cat.BOSS) return "힘 · 민첩 · 모험 +" + fmt(c.perStar * stars) + "%";
        return c.stat.label + " +" + fmt(c.perStar * stars) + (c.stat.pct ? "%" : "");
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.format("%.1f", v);
    }

    private static StatMap setBonus(Cat c, int step) {
        StatMap m = new StatMap();
        if (step <= 0) return m;
        double k = step == 1 ? 1 : step == 2 ? 2 : 4;
        switch (c) {
            case UNDEAD -> m.add(Stat.HP_PCT, 3 * k);
            case BEAST -> m.add(Stat.CRIT_DMG, 5 * k);
            case MAGIC -> m.add(Stat.CRIT, 1 * k);
            case HUMANOID -> m.add(Stat.ARMOR_PEN, 2 * k);
            case OTHER -> m.add(Stat.DEF, 1 * k);
            case BOSS -> m.add(Stat.LIFESTEAL, 0.5 * k).add(Stat.EXP_PCT, 3 * k);
        }
        return m;
    }

    private int[] catStars(PlayerData d) {
        int[] t = new int[Cat.values().length];
        for (Map.Entry<String, Info> e : infos.entrySet()) t[e.getValue().cat.ordinal()] += stars(count(d, e.getKey()));
        return t;
    }

    private static int setStep(int stars) {
        int s = 0;
        for (int a : SET_AT) if (stars >= a) s++;
        return s;
    }

    /** 영혼석 능력치 합 (StatCalculator) */
    public StatMap bonus(PlayerData d) {
        StatMap t = new StatMap();
        int[] cs = catStars(d);
        for (Cat c : Cat.values()) {
            int st = cs[c.ordinal()];
            if (st == 0) continue;
            if (c == Cat.BOSS) t.add(Stat.STR_PCT, c.perStar * st).add(Stat.DEX_PCT, c.perStar * st).add(Stat.ADV_PCT, c.perStar * st);
            else t.add(c.stat, c.perStar * st);
            t.addAll(setBonus(c, setStep(st)));
        }
        return t;
    }

    public void open(Player p) {
        new SoulGui(p, Cat.UNDEAD, 0).open(p);
    }

    private ItemStack icon(Info in, int count) {
        Material m = in.cat == Cat.BOSS ? Material.WITHER_SKELETON_SKULL : Material.matchMaterial(in.type + "_SPAWN_EGG");
        if (m == null) m = Material.SOUL_LANTERN;
        int st = stars(count);
        return Gui.button(m, in.cat.color + "&l" + in.name + " &f" + "★".repeat(st) + "&8" + "★".repeat(5 - st),
                "&7모은 수 &f" + count + (st < 5 ? " &7/ 다음 별 " + next(count) : " &6(최대 · 이후 별 조각)"),
                "&7별마다: &f" + bonusText(in.cat, 1), "&7지금: &a" + bonusText(in.cat, st));
    }

    private class SoulGui extends Gui {
        SoulGui(Player p, Cat cat, int page) {
            super(6, "&8✦ 영혼석 · " + cat.label);
            PlayerData d = plugin.data().get(p);
            int[] cs = catStars(d);
            Cat[] cats = Cat.values();
            for (int i = 0; i < cats.length; i++) {
                Cat c = cats[i];
                int st = cs[i], step = setStep(st);
                List<String> lore = new ArrayList<>();
                lore.add("&7별 합계 &f" + st + " &7· 별마다 " + bonusText(c, 1));
                for (int k = 0; k < SET_AT.length; k++)
                    lore.add((step > k ? "&a✔ " : "&8✘ ") + SET_AT[k] + "성 세트 &7" + ConstellationManager.statText(setBonus(c, k + 1)));
                lore.add(c == cat ? "&a보는 중" : "&e▶ 클릭");
                set(1 + i + (i >= 3 ? 1 : 0), Gui.ui(UiIcon.SOUL, c == cat, c.color + "&l" + c.label + " 영혼석", lore.toArray(new String[0])), e -> new SoulGui(p, c, 0).open(p));
            }
            List<String> keys = new ArrayList<>();
            for (Map.Entry<String, Info> e : infos.entrySet()) if (e.getValue().cat == cat && count(d, e.getKey()) > 0) keys.add(e.getKey());
            keys.sort(Comparator.comparingInt((String k) -> -count(d, k)));
            int per = 36, pages = Math.max(1, (keys.size() + per - 1) / per);
            for (int i = 0; i < per && page * per + i < keys.size(); i++) {
                String k = keys.get(page * per + i);
                set(9 + i, icon(infos.get(k), count(d, k)));
            }
            if (keys.isEmpty()) set(22, Gui.ui(UiIcon.LOCKED, false, "&7아직 모은 " + cat.label + " 영혼석이 없습니다", "&7몬스터를 잡으면 낮은 확률로 얻습니다"));
            if (page > 0) set(45, Gui.ui(UiIcon.NAV_BACK, true, "&f이전"), e -> new SoulGui(p, cat, page - 1).open(p));
            if (page + 1 < pages) set(53, Gui.ui(UiIcon.NAV_NEXT, true, "&f다음"), e -> new SoulGui(p, cat, page + 1).open(p));
            StatMap all = bonus(d);
            set(49, Gui.ui(UiIcon.SOUL, true, "&3&l영혼석 효과 합계", "&f" + (ConstellationManager.statText(all).isEmpty() ? "&8없음" : ConstellationManager.statText(all)),
                    "", "&71 · 3 · 7 · 15 · 30개 → 1~5성", "&75성을 넘긴 영혼석은 별 조각으로 (/별자리)"));
            fill(0, 53);
        }
    }
}
