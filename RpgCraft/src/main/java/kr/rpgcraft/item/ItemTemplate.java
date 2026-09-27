package kr.rpgcraft.item;

import kr.rpgcraft.stat.StatMap;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 아이템 원형. ItemRegistry 에서 코드로 등록된다. */
public class ItemTemplate {
    public final String id;
    public String name;
    public final Material material;
    public final Category category;
    public Grade grade = Grade.NORMAL;
    public StatMap stats = new StatMap();
    public WeaponClass weaponClass;
    public ArmorSlot armorSlot;
    public boolean enhanceable = true;
    public String setId;
    public int setN;
    public StatMap setBonus;
    public List<String> desc = new ArrayList<>();
    public long buy = -1, sell = -1;
    public int modelData;
    public Integer color;
    public double value;     // 회복량 / 수리량 / 성벽 대미지 등
    public int tier;
    public int cooldown;     // 초
    public int amount = 1;   // 바닐라 묶음 수량
    public String skill;
    public boolean glow;
    /** 칭호 (로어의 『』 부제) */
    public String epithet;
    /** 세계관 한두 줄 */
    public List<String> flavor = new ArrayList<>();
    /** 특수 효과 ID (ItemEffectManager.Effect) */
    public String effect;
    /** 방어구 장식(트림): 착용 모습에 무늬를 넣는다 */
    public String trimPattern, trimMaterial;

    public ItemTemplate(String id, String name, Material material, Category category) {
        this.id = id;
        this.name = name;
        this.material = material;
        this.category = category;
    }

    public ItemTemplate grade(Grade g) { this.grade = g; return this; }
    public ItemTemplate stats(StatMap s) { this.stats = s; return this; }
    public ItemTemplate weapon(WeaponClass w) { this.weaponClass = w; return this; }
    public ItemTemplate slot(ArmorSlot s) { this.armorSlot = s; return this; }
    public ItemTemplate noEnhance() { this.enhanceable = false; return this; }
    public ItemTemplate set(String id, int n, StatMap bonus) { this.setId = id; this.setN = n; this.setBonus = bonus; return this; }
    public ItemTemplate desc(String... d) { this.desc.addAll(Arrays.asList(d)); return this; }
    public ItemTemplate price(long buy, long sell) { this.buy = buy; this.sell = sell; return this; }
    public ItemTemplate model(int m) { this.modelData = m; return this; }
    public ItemTemplate color(int rgb) { this.color = rgb; return this; }
    public ItemTemplate value(double v) { this.value = v; return this; }
    public ItemTemplate tier(int t) { this.tier = t; return this; }
    public ItemTemplate cooldown(int c) { this.cooldown = c; return this; }
    public ItemTemplate amount(int a) { this.amount = a; return this; }
    public ItemTemplate skill(String s) { this.skill = s; return this; }
    public ItemTemplate glow() { this.glow = true; return this; }
    public ItemTemplate epithet(String e) { this.epithet = e; return this; }
    public ItemTemplate flavor(String... f) { this.flavor = new ArrayList<>(Arrays.asList(f)); return this; }
    public ItemTemplate effect(String e) { this.effect = e; return this; }
    public ItemTemplate trim(String pattern, String material) { this.trimPattern = pattern; this.trimMaterial = material; return this; }
}
