package kr.rpgcraft.item;

/** 무기 종류와 공격 간격(ms). 간격보다 빨리 때리면 대미지가 비례 감소한다. */
public enum WeaponClass {
    SWORD("검", 600), DAGGER("단검", 300), AXE("도끼", 900), SHIELD("방패", 700), SPEAR("창", 800), CLUB("몽둥이", 500);

    public final String label;
    public final long interval;

    WeaponClass(String label, long interval) {
        this.label = label;
        this.interval = interval;
    }
}
