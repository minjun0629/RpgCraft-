package kr.rpgcraft.minigame;

import kr.rpgcraft.data.PlayerData;

/** 미니게임 코인 — 아이템이 아니라 플레이어 데이터에 숫자로 저장되는 디지털 재화 (counters: coin_xxx) */
public enum Coin {
    MOLE("두더지 코인", "&6", 0),
    BRICK("벽돌 코인", "&c", 1),
    MINE("지뢰 코인", "&b", 2),
    CARD("그림 코인", "&d", 3);

    public final String label, color;
    public final int icon;

    Coin(String label, String color, int idx) {
        this.label = label;
        this.color = color;
        this.icon = Icons.COIN + idx;
    }

    public String key() {
        return "coin_" + name().toLowerCase();
    }

    public long get(PlayerData d) {
        return Math.round(d.counters.getOrDefault(key(), 0.0));
    }

    public void add(PlayerData d, long n) {
        d.counters.put(key(), (double) Math.max(0, get(d) + n));
    }

    public static Coin of(String s) {
        for (Coin c : values()) if (c.name().equalsIgnoreCase(s)) return c;
        return null;
    }
}
