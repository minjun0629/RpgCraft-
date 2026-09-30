package kr.rpgcraft.minigame;

import kr.rpgcraft.data.PlayerData;

/**
 * 미니게임 코인 — 아이템이 아니라 플레이어 데이터에 숫자로 저장되는 디지털 재화 (counters: coin_event).
 * v5.6.4: 두더지 · 벽돌 · 지뢰 · 그림 코인을 하나로 통합. 예전 코인은 처음 볼 때 자동으로 합쳐짐.
 */
public enum Coin {
    EVENT("미니게임 코인", "&e", Icons.COIN + 4);

    /** v5.6.0~v5.6.3 의 게임별 코인 (합쳐서 없앰) */
    private static final String[] LEGACY = {"coin_mole", "coin_brick", "coin_mine", "coin_card"};

    public final String label, color;
    public final int icon;

    Coin(String label, String color, int icon) {
        this.label = label;
        this.color = color;
        this.icon = icon;
    }

    public String key() {
        return "coin_" + name().toLowerCase();
    }

    private void migrate(PlayerData d) {
        double sum = 0;
        for (String k : LEGACY) { Double v = d.counters.remove(k); if (v != null) sum += v; }
        if (sum > 0) d.counters.merge(key(), sum, Double::sum);
    }

    public long get(PlayerData d) {
        migrate(d);
        return Math.round(d.counters.getOrDefault(key(), 0.0));
    }

    public void add(PlayerData d, long n) {
        d.counters.put(key(), (double) Math.max(0, get(d) + n));
    }

    /** 설정 파일의 coin 값: 예전 이름(MOLE · BRICK · MINE · CARD)도 모두 미니게임 코인으로 */
    public static Coin of(String s) {
        return EVENT;
    }
}
