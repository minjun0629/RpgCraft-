package kr.rpgcraft.stat;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/** 스탯 묶음. 아이템 PDC에 "ATK=124;CRIT=-20" 형태로 직렬화된다. */
public class StatMap {
    private final EnumMap<Stat, Double> map = new EnumMap<>(Stat.class);

    public static StatMap of(Object... kv) {
        StatMap m = new StatMap();
        for (int i = 0; i + 1 < kv.length; i += 2) m.add((Stat) kv[i], ((Number) kv[i + 1]).doubleValue());
        return m;
    }

    public double get(Stat s) {
        return map.getOrDefault(s, 0.0);
    }

    public StatMap set(Stat s, double v) {
        if (v == 0) map.remove(s);
        else map.put(s, v);
        return this;
    }

    public StatMap add(Stat s, double v) {
        return set(s, get(s) + v);
    }

    public StatMap addAll(StatMap o) {
        if (o != null) for (Map.Entry<Stat, Double> e : o.map.entrySet()) add(e.getKey(), e.getValue());
        return this;
    }

    public StatMap scaled(double f) {
        StatMap m = new StatMap();
        for (Map.Entry<Stat, Double> e : map.entrySet()) m.set(e.getKey(), e.getValue() * f);
        return m;
    }

    public Set<Map.Entry<Stat, Double>> entries() {
        return map.entrySet();
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    public StatMap copy() {
        return new StatMap().addAll(this);
    }

    public String serialize() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Stat, Double> e : map.entrySet()) {
            if (sb.length() > 0) sb.append(';');
            sb.append(e.getKey().name()).append('=').append(e.getValue());
        }
        return sb.toString();
    }

    public static StatMap parse(String s) {
        StatMap m = new StatMap();
        if (s == null || s.isEmpty()) return m;
        for (String part : s.split(";")) {
            String[] kv = part.split("=");
            if (kv.length != 2) continue;
            try {
                m.add(Stat.valueOf(kv[0]), Double.parseDouble(kv[1]));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return m;
    }
}
