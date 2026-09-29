package kr.rpgcraft.boss;

import org.bukkit.boss.BarColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BossDefinition {
    public static class Skill {
        public String type;
        public int interval = 10, amount = 1, level;
        public double radius = 5, power = 1, speed = 0.7;   // speed: 투사체 속도 (칸/틱)
        public String entity, name;
        public boolean ai = true;
    }

    public static class Drop {
        public String item;
        public int min = 1, max = 1;
        public double chance = 1;
    }

    public String id, name;
    public EntityType type;
    public int level;
    public double hp, damage, defense, awakenMultiplier = 1.5;
    public long exp, money;
    public boolean awaken, field;
    public final List<String> biomes = new ArrayList<>();
    public BarColor color = BarColor.RED;
    public final List<Skill> skills = new ArrayList<>();
    public final List<Drop> drops = new ArrayList<>();
    public final List<Drop> minionDrops = new ArrayList<>();

    public static BossDefinition load(String id, ConfigurationSection s) {
        BossDefinition d = new BossDefinition();
        d.id = id;
        d.name = s.getString("name", id);
        d.type = EntityType.valueOf(s.getString("type", "ZOMBIE"));
        d.level = s.getInt("level", 10);
        d.hp = s.getDouble("hp", 10000);
        d.damage = s.getDouble("damage", 100);
        d.defense = s.getDouble("defense", 0);
        d.exp = s.getLong("exp", 1000);
        d.money = s.getLong("money", 10000);
        d.awaken = s.getBoolean("awaken", false);
        d.awakenMultiplier = s.getDouble("awaken-multiplier", 1.5);
        d.field = s.getBoolean("field", false);
        for (String b : s.getStringList("biomes")) d.biomes.add(b.toUpperCase());
        try {
            d.color = BarColor.valueOf(s.getString("bossbar-color", "RED"));
            if (d.color == BarColor.WHITE) d.color = BarColor.PINK; // 흰색 보스바는 나침반 전용 (리소스팩에서 투명)
        } catch (IllegalArgumentException ignored) {
        }
        for (Map<?, ?> m : s.getMapList("skills")) {
            Skill k = new Skill();
            k.type = String.valueOf(m.get("type")).toUpperCase();
            k.interval = num(m.get("interval"), 10).intValue();
            k.radius = num(m.get("radius"), 5).doubleValue();
            k.power = num(m.get("power"), 1).doubleValue();
            k.amount = num(m.get("amount"), 1).intValue();
            k.speed = num(m.get("speed"), 0.7).doubleValue();
            k.level = num(m.get("level"), 0).intValue();
            k.entity = m.get("entity") == null ? null : String.valueOf(m.get("entity"));
            k.name = m.get("name") == null ? null : String.valueOf(m.get("name"));
            k.ai = m.get("ai") == null || Boolean.parseBoolean(String.valueOf(m.get("ai")));
            d.skills.add(k);
        }
        readDrops(s.getMapList("drops"), d.drops);
        readDrops(s.getMapList("minion-drops"), d.minionDrops);
        return d;
    }

    private static void readDrops(List<Map<?, ?>> list, List<Drop> into) {
        for (Map<?, ?> m : list) {
            Drop dr = new Drop();
            dr.item = String.valueOf(m.get("item"));
            dr.min = num(m.get("min"), 1).intValue();
            dr.max = num(m.get("max"), dr.min).intValue();
            dr.chance = num(m.get("chance"), 1).doubleValue();
            into.add(dr);
        }
    }

    private static Number num(Object o, Number def) {
        if (o instanceof Number n) return n;
        if (o == null) return def;
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
