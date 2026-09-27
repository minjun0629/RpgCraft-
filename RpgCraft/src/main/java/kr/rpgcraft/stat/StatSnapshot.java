package kr.rpgcraft.stat;

import kr.rpgcraft.item.WeaponClass;

/** 플레이어 최종 능력치 스냅샷 (10틱마다 재계산) */
public class StatSnapshot {
    public static final StatSnapshot EMPTY = new StatSnapshot();

    public double attack = 5, ranged, crit, critDmg, def, maxHp = 1000, lifesteal, armorPen, dodge, speed, expPct, enhanceRate;
    public double str, dex, adv, magic;
    public WeaponClass weaponClass;
    public boolean weaponOk = true;
    public boolean holdingBow;
    public String weaponProblem;
}
