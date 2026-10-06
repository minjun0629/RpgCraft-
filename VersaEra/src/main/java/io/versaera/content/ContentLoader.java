package io.versaera.content;

import io.versaera.domain.boss.BossDefinition;
import io.versaera.domain.boss.Shape;
import io.versaera.domain.crafting.MaterialSlot;
import io.versaera.domain.crafting.Recipe;
import io.versaera.domain.gathering.ResourceNode;
import io.versaera.domain.hidden.Condition;
import io.versaera.domain.hidden.HiddenRule;
import io.versaera.domain.item.ItemCategory;
import io.versaera.domain.item.ItemType;
import io.versaera.domain.npc.NpcDefinition;
import io.versaera.domain.skill.ActionStat;
import io.versaera.domain.skill.Discipline;
import io.versaera.domain.world.Region;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.util.*;

/**
 * content/*.yml → 도메인 정의. SafeConstructor 만 써서 YAML 로 임의 자바 객체가 만들어지지 않게 한다.
 * 잘못된 항목은 어느 파일 · 어느 id 인지 알려 주는 예외로 실패한다 (조용히 건너뛰지 않음).
 */
public final class ContentLoader {
    public static final class ContentException extends RuntimeException {
        public ContentException(String where, Throwable cause) {
            super(where + ": " + cause.getMessage(), cause);
        }

        public ContentException(String msg) {
            super(msg);
        }
    }

    private ContentLoader() {
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parse(String yamlText, String file) {
        LoaderOptions o = new LoaderOptions();
        o.setAllowDuplicateKeys(false);
        o.setMaxAliasesForCollections(20);
        Object root = new Yaml(new SafeConstructor(o)).load(yamlText);
        if (root == null) return Map.of();
        if (!(root instanceof Map)) throw new ContentException(file + ": 최상위가 맵이 아닙니다");
        return (Map<String, Object>) root;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Map<String, Object> root, String key, String file) {
        Object v = root.get(key);
        if (v == null) return Map.of();
        if (!(v instanceof Map)) throw new ContentException(file + ": '" + key + "' 는 맵이어야 합니다");
        return (Map<String, Object>) v;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object v) {
        return v instanceof Map ? (Map<String, Object>) v : Map.of();
    }

    private static String str(Map<String, Object> m, String k, String def) {
        Object v = m.get(k);
        return v == null ? def : String.valueOf(v);
    }

    private static String req(Map<String, Object> m, String k) {
        Object v = m.get(k);
        if (v == null) throw new IllegalArgumentException("'" + k + "' 가 없습니다");
        return String.valueOf(v);
    }

    private static int i(Map<String, Object> m, String k, int def) {
        Object v = m.get(k);
        return v instanceof Number n ? n.intValue() : v == null ? def : Integer.parseInt(String.valueOf(v));
    }

    private static long l(Map<String, Object> m, String k, long def) {
        Object v = m.get(k);
        return v instanceof Number n ? n.longValue() : v == null ? def : Long.parseLong(String.valueOf(v));
    }

    private static double d(Map<String, Object> m, String k, double def) {
        Object v = m.get(k);
        return v instanceof Number n ? n.doubleValue() : v == null ? def : Double.parseDouble(String.valueOf(v));
    }

    private static boolean b(Map<String, Object> m, String k, boolean def) {
        Object v = m.get(k);
        return v instanceof Boolean x ? x : v == null ? def : Boolean.parseBoolean(String.valueOf(v));
    }

    private static List<String> list(Map<String, Object> m, String k) {
        Object v = m.get(k);
        if (v == null) return List.of();
        if (v instanceof List<?> l) {
            List<String> out = new ArrayList<>();
            for (Object o : l) out.add(String.valueOf(o));
            return out;
        }
        return List.of(String.valueOf(v));
    }

    private static Map<String, Integer> intMap(Map<String, Object> m, String k) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : map(m.get(k)).entrySet()) out.put(e.getKey(), ((Number) e.getValue()).intValue());
        return out;
    }

    private interface Builder<T> {
        T build(String id, Map<String, Object> m);
    }

    private static <T> List<T> each(Map<String, Object> root, String key, String file, Builder<T> b) {
        List<T> out = new ArrayList<>();
        for (Map.Entry<String, Object> e : section(root, key, file).entrySet()) {
            try {
                out.add(b.build(e.getKey(), map(e.getValue())));
            } catch (RuntimeException ex) {
                throw new ContentException(file + " / " + e.getKey(), ex);
            }
        }
        return out;
    }

    public static List<ItemType> items(Map<String, Object> root, String file) {
        return each(root, "items", file, (id, m) -> new ItemType(id, req(m, "name"), ItemCategory.valueOf(req(m, "category")), req(m, "material"),
                i(m, "durability", 0), i(m, "weight", 0), new LinkedHashSet<>(list(m, "tags")), intMap(m, "stats"), intMap(m, "requires"),
                str(m, "source", "ORIGINAL")));
    }

    public static List<Discipline> disciplines(Map<String, Object> root, String file) {
        return each(root, "disciplines", file, (id, m) -> new Discipline(id, req(m, "name"), Discipline.Category.valueOf(req(m, "category")),
                b(m, "hand", false), str(m, "source", "ORIGINAL")));
    }

    public static List<ActionStat> stats(Map<String, Object> root, String file) {
        return each(root, "stats", file, (id, m) -> new ActionStat(id, req(m, "name"), req(m, "counter"), l(m, "per", 1), l(m, "unlock_at", 0),
                str(m, "effect", ""), str(m, "source", "ORIGINAL")));
    }

    public static List<Recipe> recipes(Map<String, Object> root, String file) {
        return each(root, "recipes", file, (id, m) -> {
            List<MaterialSlot> slots = new ArrayList<>();
            for (Map.Entry<String, Object> s : map(m.get("slots")).entrySet()) {
                Map<String, Object> sm = map(s.getValue());
                slots.add(new MaterialSlot(s.getKey(), req(sm, "accepts"), i(sm, "count", 1), d(sm, "weight", 1), b(sm, "optional", false), i(sm, "bonus", 0)));
            }
            return new Recipe(id, req(m, "name"), req(m, "discipline"), i(m, "min_level", 1), i(m, "action_level", 1), req(m, "output"),
                    i(m, "output_count", 1), slots, str(m, "tool", null), i(m, "time_ticks", 40), l(m, "xp", 10), str(m, "discovery", null),
                    str(m, "source", "ORIGINAL"));
        });
    }

    public static List<ResourceNode> resources(Map<String, Object> root, String file) {
        return each(root, "resources", file, (id, m) -> new ResourceNode(id, req(m, "name"), req(m, "discipline"), i(m, "min_level", 1),
                i(m, "action_level", 1), new LinkedHashSet<>(list(m, "blocks")), req(m, "yield"), i(m, "amount", 1), i(m, "bonus", 0),
                new LinkedHashSet<>(list(m, "rich_in")), i(m, "respawn_seconds", 60), str(m, "tool", null), l(m, "xp", 5), str(m, "source", "ORIGINAL")));
    }

    public static List<Region> regions(Map<String, Object> root, String file) {
        return each(root, "regions", file, (id, m) -> {
            List<String> min = list(m, "min"), max = list(m, "max");
            if (min.size() != 3 || max.size() != 3) throw new IllegalArgumentException("min / max 는 [x, y, z]");
            return new Region(id, req(m, "name"), str(m, "source", "ORIGINAL"), i(m, "danger", 0), str(m, "world", "world"),
                    Integer.parseInt(min.get(0)), Integer.parseInt(min.get(1)), Integer.parseInt(min.get(2)),
                    Integer.parseInt(max.get(0)), Integer.parseInt(max.get(1)), Integer.parseInt(max.get(2)),
                    i(m, "priority", 0), str(m, "parent", null), new LinkedHashSet<>(list(m, "tags")), str(m, "purpose", null),
                    str(m, "changed", null), list(m, "resources"), list(m, "factions"));
        });
    }

    public static List<NpcDefinition> npcs(Map<String, Object> root, String file) {
        return each(root, "npcs", file, (id, m) -> new NpcDefinition(id, req(m, "name"), req(m, "job"), str(m, "personality", ""),
                str(m, "faction", null), req(m, "region"), new LinkedHashSet<>(list(m, "likes")), new LinkedHashSet<>(list(m, "dislikes")),
                list(m, "schedule"), str(m, "source", "ORIGINAL")));
    }

    public static List<BossDefinition> bosses(Map<String, Object> root, String file) {
        return each(root, "bosses", file, (id, m) -> {
            Map<String, BossDefinition.Pattern> pats = new LinkedHashMap<>();
            for (Map.Entry<String, Object> p : map(m.get("patterns")).entrySet()) {
                Map<String, Object> pm = map(p.getValue());
                pats.put(p.getKey(), new BossDefinition.Pattern(p.getKey(), Shape.valueOf(req(pm, "shape")), d(pm, "radius", 4), d(pm, "inner", 0),
                        d(pm, "width", 3), d(pm, "height", 4), l(pm, "telegraph_ms", 1500), d(pm, "damage", 10), l(pm, "cooldown_ms", 4000),
                        str(pm, "effect", null)));
            }
            List<BossDefinition.Phase> phases = new ArrayList<>();
            Object pl = m.get("phases");
            if (pl instanceof List<?> l) for (Object o : l) {
                Map<String, Object> pm = map(o);
                phases.add(new BossDefinition.Phase(d(pm, "hp_below", 1.0), list(pm, "patterns"), str(pm, "announce", "")));
            }
            return new BossDefinition(id, req(m, "name"), d(m, "scale", 1), d(m, "hit_radius", 2), d(m, "max_hp", 1000), d(m, "arena_radius", 40),
                    d(m, "weak_arc", 90), l(m, "enrage_ms", 0), phases, pats, str(m, "model", null), str(m, "source", "ORIGINAL"));
        });
    }

    // ------------------------------------------------------------------ 히든 규칙 (봉인을 연 뒤의 YAML)
    public static List<HiddenRule> hidden(Map<String, Object> root, String file) {
        return each(root, "hidden", file, (id, m) -> new HiddenRule(id, req(m, "title"), condition(m.get("when")), str(m, "rumor", ""),
                new LinkedHashMap<>(stringMap(map(m.get("reward"))))));
    }

    private static Map<String, String> stringMap(Map<String, Object> m) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : m.entrySet()) out.put(e.getKey(), String.valueOf(e.getValue()));
        return out;
    }

    /** when: {all: [...]} · {any: [...]} · {counter: key, at_least: n} · {mastery: d, level: n} · {affinity: npc, at_least: n}
     *  · {region: id} · {hours: [from, to]} · {discovered: "kind:ref"} */
    static Condition condition(Object o) {
        Map<String, Object> m = map(o);
        if (m.isEmpty()) throw new IllegalArgumentException("조건이 비었습니다");
        if (m.containsKey("all")) return new Condition.All(parts(m.get("all")));
        if (m.containsKey("any")) return new Condition.Any(parts(m.get("any")));
        if (m.containsKey("counter")) return new Condition.Counter(req(m, "counter"), l(m, "at_least", 1));
        if (m.containsKey("mastery")) return new Condition.MasteryAtLeast(req(m, "mastery"), i(m, "level", 1));
        if (m.containsKey("affinity")) return new Condition.AffinityAtLeast(req(m, "affinity"), i(m, "at_least", 0));
        if (m.containsKey("region")) return new Condition.InRegion(req(m, "region"));
        if (m.containsKey("hours")) {
            List<String> h = list(m, "hours");
            return new Condition.Hours(Integer.parseInt(h.get(0)), Integer.parseInt(h.get(1)));
        }
        if (m.containsKey("discovered")) {
            String v = req(m, "discovered");
            int c = v.indexOf(':');
            if (c <= 0) throw new IllegalArgumentException("discovered 는 kind:ref");
            return new Condition.Discovered(v.substring(0, c), v.substring(c + 1));
        }
        throw new IllegalArgumentException("알 수 없는 조건: " + m.keySet());
    }

    private static List<Condition> parts(Object o) {
        if (!(o instanceof List<?> l) || l.isEmpty()) throw new IllegalArgumentException("all / any 는 비지 않은 목록이어야 합니다");
        List<Condition> out = new ArrayList<>();
        for (Object x : l) out.add(condition(x));
        return out;
    }
}
