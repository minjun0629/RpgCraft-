package kr.rpgcraft.item;

import kr.rpgcraft.Keys;
import kr.rpgcraft.feature.RuneManager;
import kr.rpgcraft.stat.Stat;
import kr.rpgcraft.stat.StatMap;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

import static kr.rpgcraft.stat.Stat.*;

/**
 * 모든 아이템 원형 등록소.
 * 노말 무기/방어구와 보스 세트 수치는 나무위키 RpgCraft 아이템 문서 기준,
 * 사신수/사흉수/대장장이/포션 수치는 공개 자료가 없어 자체 밸런스로 설계.
 */
public class ItemRegistry {
    private final Map<String, ItemTemplate> templates = new LinkedHashMap<>();

    public ItemRegistry() {
        registerMaterials();
        registerNormalWeapons();
        registerNormalArmor();
        registerBossSets();
        registerBows();
        registerSpirit();
        registerMisc();
        registerRelics();
        registerLoot();
        registerExpansion();
        registerTranscend();
        registerArmory();
        registerArmory2();
        for (kr.rpgcraft.world.HiddenJobManager.Tier t : kr.rpgcraft.world.HiddenJobManager.TIERS)   // 히든 전직서 (v5.3.0)
            reg(new ItemTemplate(kr.rpgcraft.world.HiddenJobManager.scrollId(t), "히든 전직서: " + t.label(), Material.ENCHANTED_BOOK, Category.TICKET)
                    .grade(t.tier() >= 3 ? Grade.MYTHIC : Grade.LEGEND).price(-1, -1)
                    .desc(t.tier() == 1 ? "들고 우클릭하면 원래 직업 대신 히든 직업으로 전직" : "들고 우클릭하면 히든 직업이 한 단계 오름", "히든 전직 퀘스트 보상"));
        registerLife();
        ItemFlavor.apply(this);
    }

    /** v5.10.20 요리 재료 · 음식 · 히든 직업 전용 무기 */
    private void registerLife() {
        for (var g : kr.rpgcraft.feature.CookingManager.INGREDIENTS)
            reg(new ItemTemplate(g.id(), "[식재료] " + g.name(), g.icon(), Category.MATERIAL).price(g.price(), g.price() / 4).desc("/요리 에서 음식 재료로 씀"));
        for (var r : kr.rpgcraft.feature.CookingManager.RECIPES)
            reg(new ItemTemplate(kr.rpgcraft.feature.CookingManager.foodId(r), r.name(), r.icon(), Category.TICKET).grade(r.minutes() >= 30 ? Grade.UNIQUE : Grade.RARE).price(-1, 2000)
                    .desc("들고 우클릭하면 먹음", "효과: " + r.effect() + " (" + r.minutes() + "분)", "버프는 하나만 (새로 먹으면 바뀜)"));
        reg(new ItemTemplate("pet_snack", "[펫] 펫 간식", Material.COOKIE, Category.MATERIAL).grade(Grade.RARE).price(25000, 5000)
                .desc("/펫 → 펫 우클릭 → 먹이 주기", "꺼내 둔 펫 경험치 +60"));   // v5.10.30 펫 성장
        // 히든 직업 전용 무기: 그 히든 직업만 쓸 수 있고, 단계가 오를수록 고유 효과가 강해짐 (히든 직업창에서 제작)
        reg(new ItemTemplate("hjw_a", "명계의 낫", Material.NETHERITE_HOE, Category.WEAPON).weapon(WeaponClass.AXE).grade(Grade.MYTHIC)
                .stats(StatMap.of(ATK, 9000, MAGIC, 1500, LIFESTEAL, 5, CRIT, 10, LEVEL_REQ, 50)).glow().price(-1, -1)
                .desc("[히든 전용] 망령의 길", "영혼 베기: 공격 시 15% 확률로 추가 피해 + 체력 회복"));
        reg(new ItemTemplate("hjw_b", "성운검 스텔라", Material.NETHERITE_SWORD, Category.WEAPON).weapon(WeaponClass.SWORD).grade(Grade.MYTHIC)
                .stats(StatMap.of(ATK, 9500, CRIT, 20, CRIT_DMG, 60, LEVEL_REQ, 50)).glow().price(-1, -1)
                .desc("[히든 전용] 별의 길", "별빛 일격: 치명타 때 25% 확률로 별이 떨어져 주변까지 피해"));
        reg(new ItemTemplate("hjw_c", "망자의 홀", Material.BLAZE_ROD, Category.WEAPON).weapon(WeaponClass.CLUB).grade(Grade.MYTHIC)
                .stats(StatMap.of(ATK, 6000, MAGIC, 2500, HP_PCT, 12, LEVEL_REQ, 50)).glow().price(-1, -1)
                .desc("[히든 전용] 죽음의 길", "사령의 지휘: 들고 있으면 군단원 공격력 +15% / 단계", "공격 시 20% 확률로 군단 체력 회복"));
        reg(new ItemTemplate("hjw_d", "시간의 바늘", Material.ECHO_SHARD, Category.WEAPON).weapon(WeaponClass.DAGGER).grade(Grade.MYTHIC)
                .stats(StatMap.of(ATK, 7000, CRIT, 25, DODGE, 6, SPEED, 10, LEVEL_REQ, 50)).glow().price(-1, -1)
                .desc("[히든 전용] 시간의 길", "초침: 공격할 때마다 되감기 재사용 대기 -0.25초 / 단계"));
    }

    public ItemTemplate get(String id) { return templates.get(id); }
    public Collection<ItemTemplate> all() { return templates.values(); }
    public Set<String> ids() { return templates.keySet(); }

    public ItemTemplate reg(ItemTemplate t) {
        templates.put(t.id, t);
        return t;
    }

    // ------------------------------------------------------------------ create
    public ItemStack create(String id, int amount) {
        ItemStack it = createRaw(id, amount);
        if (it != null && "boots_flipper".equals(id)) {   // 물갈퀴 신발: 물속에서 빠르게 (물갈퀴 III)
            it.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.DEPTH_STRIDER, 3);
            ItemMeta m = it.getItemMeta();
            if (m instanceof org.bukkit.inventory.meta.LeatherArmorMeta lm) lm.setColor(org.bukkit.Color.fromRGB(0x2E8CA8));
            it.setItemMeta(m);
        }
        return it;
    }

    private ItemStack createRaw(String id, int amount) {
        ItemTemplate t = templates.get(id);
        if (t == null) return null;
        if (t.category == Category.VANILLA) return new ItemStack(t.material, Math.max(1, amount) * t.amount);
        boolean unique = t.category.isEquipment() || t.category == Category.RUNE || t.category == Category.TOTEM || t.category == Category.CHECK;
        ItemStack it = new ItemStack(t.material, unique ? 1 : Math.max(1, amount));
        ItemMeta m = it.getItemMeta();
        PersistentDataContainer c = m.getPersistentDataContainer();
        c.set(Keys.ID, PersistentDataType.STRING, t.id);
        if (t.category.isEquipment()) {
            c.set(Keys.STATS, PersistentDataType.STRING, t.stats.serialize());
            c.set(Keys.ENH, PersistentDataType.INTEGER, 0);
            c.set(Keys.GRADE, PersistentDataType.STRING, t.grade.name());
            if (t.weaponClass != null) c.set(Keys.WCLASS, PersistentDataType.STRING, t.weaponClass.name());
            if (t.armorSlot != null) c.set(Keys.ASLOT, PersistentDataType.STRING, t.armorSlot.name());
            if (t.setId != null) {
                c.set(Keys.SET, PersistentDataType.STRING, t.setId);
                c.set(Keys.SET_N, PersistentDataType.INTEGER, t.setN);
                c.set(Keys.SET_BONUS, PersistentDataType.STRING, t.setBonus.serialize());
            }
            m.setUnbreakable(true);
        }
        if (unique) c.set(Keys.UNIQUE, PersistentDataType.STRING, UUID.randomUUID().toString());
        if (t.modelData > 0) m.setCustomModelData(t.modelData);
        if (t.color != null && m instanceof LeatherArmorMeta lm) lm.setColor(Color.fromRGB(t.color));
        if (t.color != null && m instanceof PotionMeta pm) pm.setColor(Color.fromRGB(t.color));
        if (t.glow) m.addEnchant(Enchantment.DURABILITY, 1, true);
        if (t.material == Material.BOW && t.id.equals("bow_elf_war")) m.addEnchant(Enchantment.ARROW_KNOCKBACK, 2, true);
        m.addItemFlags(ItemFlag.values());
        it.setItemMeta(m);
        if (t.category == Category.RUNE) {
            if (t.id.startsWith("acc_")) kr.rpgcraft.feature.AccessoryManager.roll(it, t.tier);
            else RuneManager.roll(it, t.tier);
        }
        ItemData.refresh(it);
        return it;
    }

    // ------------------------------------------------------------------ data
    private ItemTemplate mat(String id, String name, Material m, long buy, long sell, String desc) {
        return reg(new ItemTemplate(id, name, m, Category.MATERIAL).price(buy, sell).desc(desc));
    }

    private void registerMaterials() {
        mat("mat_stone", "돌멩이", Material.FLINT, 1000, 500, "길거리에서 흔히 보이는 작은 돌").model(1480);
        mat("mat_iron", "철괴", Material.IRON_INGOT, 4000, 2000, "불순물이 섞인 거무스름한 철괴").model(1481);
        mat("mat_silver", "은괴", Material.QUARTZ, 20000, 10000, "대장장이가 제련하면 철보다 단단해진다").model(1482);
        mat("mat_gold", "금괴", Material.GOLD_INGOT, 50000, 25000, "세공에 꼭 필요한 광물").model(1483);
        mat("mat_crystal", "크리스탈", Material.DIAMOND, 100000, 50000, "모든 대장장이의 꿈의 광물").model(1484);
        mat("ore_black", "검은 광석", Material.NETHERITE_SCRAP, 500000, 300000, "빛을 흡수하는 정체불명의 광물").grade(Grade.UNIQUE).model(800);
        mat("ore_green", "녹색 광석", Material.EMERALD, 500000, 300000, "녹색으로 빛나는 광물").grade(Grade.UNIQUE).model(800);
        mat("ore_red", "붉은 광석", Material.REDSTONE, 500000, 300000, "숙련된 대장장이만 제련할 수 있다").grade(Grade.UNIQUE).model(800);
        mat("ore_blue", "푸른 광석", Material.LAPIS_LAZULI, 500000, 300000, "귀를 기울이면 파도 소리가 들린다").grade(Grade.UNIQUE).model(800);
        mat("ore_gray", "회색 광석", Material.CLAY_BALL, 500000, 300000, "열을 가하면 색이 바뀐다고 한다").grade(Grade.UNIQUE).model(800);

        mat("herb_weed", "잡초", Material.WHEAT_SEEDS, 1000, 500, "먹어도 될까 싶은 잡초").model(1485);
        mat("herb_mushroom", "영지버섯", Material.BROWN_DYE, 2000, 800, "쓴맛이 강한 숲의 버섯").model(1486);
        mat("herb_bell", "방울꽃", Material.WHITE_DYE, 5000, 2000, "딸랑딸랑 방울 소리가 난다").model(1487);
        mat("herb_star", "별꽃", Material.YELLOW_DYE, 10000, 5000, "밤에도 빛나는 꽃").model(1488);
        mat("herb_ginseng1", "1년근 산삼", Material.CARROT, 100000, 50000, "귀한 산삼").model(1489);
        mat("herb_ginseng10", "10년근 산삼", Material.GOLDEN_CARROT, 200000, 80000, "매우 귀한 산삼").model(1490);
        mat("herb_ginseng100", "100년근 산삼", Material.GOLDEN_CARROT, 400000, 200000, "죽은 사람도 살린다는 전설의 약초").glow().grade(Grade.UNIQUE).model(1491);

        mat("wood_log", "통나무", Material.STICK, 1000, 500, "흔한 나무 목재").model(1492);
        mat("wood_glow", "빛나는 통나무", Material.STICK, 5000, 3000, "보통 목재보다 단단하다").glow().model(1493);
        mat("wood_gold", "황금나무", Material.STICK, 400000, 200000, "전설로만 전해지는 황금나무").glow().grade(Grade.UNIQUE).model(1494);

        mat("crystal_low", "하급 결정", Material.PRISMARINE_SHARD, -1, 1500, "몬스터에게서 나온 작은 결정").model(1495);
        mat("crystal_mid", "중급 결정", Material.PRISMARINE_CRYSTALS, -1, 12000, "은은한 푸른빛이 난다").model(1496);
        mat("crystal_high", "상급 결정", Material.AMETHYST_SHARD, -1, 120000, "어딘가 쓸 수 있을 것 같은 크기").grade(Grade.RARE).model(1497);
        mat("crystal_top", "최상급 결정", Material.ECHO_SHARD, -1, 400000, "강한 기운이 흘러나온다").grade(Grade.UNIQUE).model(1498);
    }

    private ItemTemplate weapon(String id, String name, Material m, WeaponClass wc, StatMap st, long price) {
        return reg(new ItemTemplate(id, name, m, Category.WEAPON).weapon(wc).stats(st).price(price, -1));
    }

    private void registerNormalWeapons() {
        weapon("weapon_club", "갈색 몽둥이", Material.STICK, WeaponClass.CLUB, StatMap.of(ATK, 2), 0).model(1520).desc("이걸로 맞으면 아프겠지?");
        String[] swords = {"초보자용 대검", "날카로운 강철장검", "길들어진 전쟁장검", "전장의 공포장검"};
        int[] swordAtk = {26, 56, 86, 124};
        String[] daggers = {"기본 단검", "빛나는 강철단검", "푸른 은장단검", "붉은 전쟁단검"};
        int[] daggerAtk = {19, 32, 49, 71};
        String[] axes = {"녹슨 손도끼", "사냥용 푸른도끼", "날카로운 전쟁도끼", "금빛 공포도끼"};
        int[] axeAtk = {15, 42, 64, 93};
        String[] shields = {"나무 방패", "강철 방패", "단단한 전쟁방패", "찬란한 전쟁공포 방패"};
        int[] shieldAtk = {9, 21, 32, 46};
        double[] shieldDef = {3.5, 4.5, 5.5, 6.5};
        long[] price = {5000, 30000, 100000, 300000};
        Material[] sm = {Material.STONE_SWORD, Material.IRON_SWORD, Material.IRON_SWORD, Material.DIAMOND_SWORD};
        Material[] am = {Material.STONE_AXE, Material.IRON_AXE, Material.IRON_AXE, Material.DIAMOND_AXE};
        for (int i = 0; i < 4; i++) {
            int lv = (i + 1) * 10, req = (i + 1) * 50;
            weapon("sword_" + lv, swords[i], sm[i], WeaponClass.SWORD, StatMap.of(ATK, swordAtk[i], CRIT, -20, REQ_STR, req, LEVEL_REQ, lv), price[i]).model(100 + i);
            weapon("dagger_" + lv, daggers[i], Material.SHEARS, WeaponClass.DAGGER, StatMap.of(ATK, daggerAtk[i], REQ_DEX, req, LEVEL_REQ, lv), price[i]).model(100 + i);
            weapon("axe_" + lv, axes[i], am[i], WeaponClass.AXE, StatMap.of(ATK, axeAtk[i], REQ_STR, req / 2, REQ_DEX, req / 2, LEVEL_REQ, lv), price[i]).model(100 + i);
            weapon("shield_" + lv, shields[i], Material.SHIELD, WeaponClass.SHIELD, StatMap.of(ATK, shieldAtk[i], DEF, shieldDef[i], REQ_ADV, req, LEVEL_REQ, lv), price[i]).model(100 + i);
        }
    }

    private void registerNormalArmor() {
        String[] slotsW = {"모자", "갑옷", "바지", "신발"};
        ArmorSlot[] slots = ArmorSlot.values();
        long[] price = {3000, 10000, 50000, 150000, 500000};
        int[] req = {5, 50, 100, 150, 200};
        // 전사(힘)
        String[] warrior = {"초보 전사의 %s", "숙련된 전사의 %s", "금장 철%s", "보라빛나는 강철%s", "푸른 바다 강철%s"};
        double[] wDef = {1.5, 2.5, 3.5, 4.5, 5.5};
        // 암살자(민첩)
        String[] assassin = {"초보 암살자의 %s", "숙련된 암살자의 %s", "실전용 암살자의 %s", "검붉은 암살전용 %s", "금장한 검붉은 암살전용 %s"};
        double[] aDef = {0.5, 1.5, 2.5, 3.5, 4.5};
        int[] aColor = {0x3C5AA6, 0x2B3F7A, 0x1E2A55, 0x7A1E1E, 0x5A1010};
        // 모험가(모험)
        String[] adv = {"초보 모험가의 %s", "숙련된 모험가의 가죽 %s", "탐험용 모험가의 %s", "유적 중급 탐험전용 %s", "유적 상급 탐험전용 %s"};
        double[] vDef = {2.5, 3.5, 4.5, 5.5, 6.5};
        int[] vColor = {0x9C7A4A, 0x7A5A30, 0x6B4A2A, 0x556B2F, 0x3E5A2A};
        for (int t = 0; t < 5; t++) {
            int lv = t * 10;
            for (int s = 0; s < 4; s++) {
                Material wm = armorMat(t <= 1 ? "CHAINMAIL" : t <= 3 ? "IRON" : "DIAMOND", slots[s]);
                String wName = String.format(warrior[t], t >= 2 ? slotsW[s] : slotsW[s]);
                ItemTemplate wt = reg(new ItemTemplate("warrior_" + t + "_" + s, wName, wm, Category.ARMOR).slot(slots[s])
                        .stats(StatMap.of(DEF, wDef[t], REQ_STR, req[t], LEVEL_REQ, lv)).price(price[t], -1).model(560 + t * 4 + s));
                ItemTemplate at = reg(new ItemTemplate("assassin_" + t + "_" + s, String.format(assassin[t], slotsW[s]), armorMat("LEATHER", slots[s]), Category.ARMOR).slot(slots[s])
                        .stats(StatMap.of(DEF, aDef[t], REQ_DEX, req[t], LEVEL_REQ, lv)).price(price[t], -1).color(aColor[t]).model(580 + t * 4 + s));
                ItemTemplate vt = reg(new ItemTemplate("adventurer_" + t + "_" + s, String.format(adv[t], slotsW[s]), armorMat("LEATHER", slots[s]), Category.ARMOR).slot(slots[s])
                        .stats(StatMap.of(DEF, vDef[t], REQ_ADV, req[t], LEVEL_REQ, lv)).price(price[t], -1).color(vColor[t]).model(600 + t * 4 + s));
                // 착용 모습: 상위 단계일수록 장식 무늬(트림)
                if (t >= 2) {
                    wt.trim(new String[]{"sentry", "rib", "tide"}[t - 2], new String[]{"gold", "amethyst", "lapis"}[t - 2]);
                    at.trim(new String[]{"silence", "vex", "vex"}[t - 2], new String[]{"iron", "redstone", "gold"}[t - 2]);
                    vt.trim(new String[]{"wayfinder", "shaper", "wild"}[t - 2], new String[]{"copper", "emerald", "gold"}[t - 2]);
                }
            }
        }
    }

    public static Material armorMat(String prefix, ArmorSlot s) {
        String suf = switch (s) {
            case HELMET -> "_HELMET";
            case CHEST -> "_CHESTPLATE";
            case LEGS -> "_LEGGINGS";
            case BOOTS -> "_BOOTS";
        };
        Material m = Material.matchMaterial(prefix + suf);
        return m == null ? Material.LEATHER_HELMET : m;
    }

    /** 보스 세트 (레어, 강화 불가). 수치는 나무위키 표 기준 */
    private void registerBossSets() {
        bossSet("witch", "마녀의 오염된 ", 20, 100, new int[]{244, 162, 184, 126}, 5, 5, "LEATHER", 0x5B2C6F,
                new StatMap[]{StatMap.of(ATK, 20), StatMap.of(ATK, 50), StatMap.of(ATK, 30), StatMap.of(ATK, 30)},
                new StatMap[]{StatMap.of(DEF, 1), StatMap.of(CRIT, 5), StatMap.of(DEF, 1), StatMap.of(SPEED, 10)},
                new int[]{3, 3, 3, 3}, new String[]{"모자", "갑옷", "바지", "신발"}, "오염된 마녀에게서 얻을 수 있는 장비");
        bossSet("dwarf", "드워프의 예리한 금빛 ", 40, 225, new int[]{456, 300, 342, 170}, 7, 5, "GOLDEN", null,
                new StatMap[]{StatMap.of(ATK, 40), StatMap.of(ATK, 70), StatMap.of(ATK, 55), StatMap.of(ATK, 55)},
                new StatMap[]{StatMap.of(RANGED_ATK, 100), StatMap.of(CRIT, -20), StatMap.of(CRIT, -20), StatMap.of(RANGED_ATK, 100)},
                new int[]{3, 3, 3, 3}, new String[]{"투구", "갑옷", "각반", "신발"}, "드워프 왕에게서 얻을 수 있는 장비");
        bossSet("harpy", "하피의 날렵한 깃털 ", 60, 350, new int[]{786, 520, 590, 296}, 9, 9, "CHAINMAIL", null,
                new StatMap[]{StatMap.of(ATK, 60), StatMap.of(ATK, 100), StatMap.of(ATK, 70), StatMap.of(ATK, 70)},
                new StatMap[]{StatMap.of(ATK, 100), StatMap.of(HP, -100), StatMap.of(HP, -100), StatMap.of(CRIT, 10)},
                new int[]{4, 2, 2, 4}, new String[]{"투구", "갑옷", "각반", "신발"}, "하피 여왕에게서 얻을 수 있는 장비");
        bossSet("sea", "심해수문장의 견고한 산호 ", 80, 450, new int[]{1218, 808, 914, 458}, 11, 11, "LEATHER", 0x1F7A8C,
                new StatMap[]{StatMap.of(ATK, 80), StatMap.of(ATK, 150), StatMap.of(ATK, 100), StatMap.of(ATK, 100)},
                new StatMap[]{StatMap.of(HP, 200), StatMap.of(HP, 200), StatMap.of(HP, 200), StatMap.of(HP, 200)},
                new int[]{3, 3, 3, 3}, new String[]{"투구", "갑옷", "각반", "신발"}, "심해수문장에게서 얻을 수 있는 장비");
        bossSet("bungbung", "타오르는 붕붕이의 탄화된 정령 ", 99, 550, new int[]{1624, 1338, 1218, 596}, 13, 13, "NETHERITE", null,
                new StatMap[]{StatMap.of(ATK, 100), StatMap.of(ATK, 200), StatMap.of(ATK, 150), StatMap.of(ATK, 120)},
                new StatMap[]{StatMap.of(ATK, 100), StatMap.of(HP, 250), StatMap.of(RANGED_ATK, 300), StatMap.of(CRIT, 15)},
                new int[]{4, 4, 4, 4}, new String[]{"투구", "갑옷", "각반", "신발"}, "타오르는 붕붕이에게서 얻을 수 있는 장비");
    }

    private int bossSetIndex = 0;

    private void bossSet(String set, String prefix, int lv, int req, int[] atk, double shieldDef, double armorDef, String armorPrefix, Integer color,
                         StatMap[] wBonus, StatMap[] aBonus, int[] aN, String[] aNames, String desc) {
        Material[] wm = {Material.IRON_SWORD, Material.SHEARS, Material.IRON_AXE, Material.SHIELD};
        if (lv >= 80) { wm[0] = Material.DIAMOND_SWORD; wm[2] = Material.DIAMOND_AXE; }
        if (lv >= 99) { wm[0] = Material.NETHERITE_SWORD; wm[2] = Material.NETHERITE_AXE; }
        int base = 200 + (bossSetIndex++) * 10;   // 마녀 200, 드워프 210, 하피 220, 심해 230, 붕붕이 240
        String[] wn = {"검", "단검", "도끼", "방패"};
        WeaponClass[] wc = {WeaponClass.SWORD, WeaponClass.DAGGER, WeaponClass.AXE, WeaponClass.SHIELD};
        for (int i = 0; i < 4; i++) {
            StatMap st = StatMap.of(ATK, atk[i], LEVEL_REQ, lv);
            switch (i) {
                case 0 -> st.add(CRIT, -20).add(REQ_STR, req);
                case 1 -> st.add(REQ_DEX, req);
                case 2 -> st.add(REQ_STR, req / 2).add(REQ_DEX, req / 2);
                default -> st.add(DEF, shieldDef).add(REQ_ADV, req);
            }
            reg(new ItemTemplate(set + "_w" + i, prefix + wn[i], wm[i], Category.WEAPON).weapon(wc[i]).stats(st)
                    .grade(Grade.RARE).noEnhance().set(set, 3, wBonus[i]).desc(desc).model(base + i));
        }
        ArmorSlot[] slots = ArmorSlot.values();
        for (int i = 0; i < 4; i++) {
            ItemTemplate t = reg(new ItemTemplate(set + "_a" + i, prefix + aNames[i], armorMat(armorPrefix, slots[i]), Category.ARMOR)
                    .slot(slots[i]).stats(StatMap.of(DEF, armorDef, LEVEL_REQ, lv)).grade(Grade.RARE).noEnhance()
                    .set(set, aN[i], aBonus[i]).desc(desc).model(620 + (base - 200) / 10 * 4 + i));
            if (color != null) t.color(color);
            int si = (base - 200) / 10;
            t.trim(new String[]{"eye", "dune", "coast", "tide", "snout"}[si], new String[]{"amethyst", "lapis", "quartz", "gold", "redstone"}[si]);
        }
    }

    private void registerBows() {
        reg(new ItemTemplate("bow_hunt", "사냥용 활", Material.BOW, Category.BOW).stats(StatMap.of(RANGED_ATK, 30)).price(10000, -1));
        reg(new ItemTemplate("bow_elf", "엘프활", Material.BOW, Category.BOW).grade(Grade.RARE)
                .stats(StatMap.of(RANGED_ATK, 100, CRIT, 5, LEVEL_REQ, 10)).desc("엘프가 사용하던 정교한 활"));
        reg(new ItemTemplate("bow_elf_war", "전쟁용 엘프활", Material.BOW, Category.BOW).grade(Grade.RARE)
                .stats(StatMap.of(RANGED_ATK, 500, CRIT, 5, LEVEL_REQ, 10)).price(1000000, -1).desc("밀어내기 II"));
    }

    /** 사신수 무기/방어구, 사흉수 갑주 (자체 설계 수치) */
    private void registerSpirit() {
        reg(new ItemTemplate("spirit_qinglong", "청룡의 검", Material.NETHERITE_SWORD, Category.WEAPON).weapon(WeaponClass.SWORD)
                .grade(Grade.MYTHIC).stats(StatMap.of(ATK, 17000, CRIT, 20, CRIT_DMG, 60, ARMOR_PEN, 40, REQ_STR, 1400, LEVEL_REQ, 280)).skill("QINGLONG").glow().model(301).desc("바람(청룡)의 기운이 깃든 검"));
        reg(new ItemTemplate("spirit_baihu", "백호의 도끼", Material.NETHERITE_AXE, Category.WEAPON).weapon(WeaponClass.AXE)
                .grade(Grade.MYTHIC).stats(StatMap.of(ATK, 19000, CRIT, 15, ARMOR_PEN, 55, HP_PCT, 10, REQ_STR, 800, REQ_DEX, 800, LEVEL_REQ, 280)).skill("BAIHU").glow().model(302).desc("땅(백호)의 기운이 깃든 도끼"));
        reg(new ItemTemplate("spirit_zhuque", "주작의 단검", Material.SHEARS, Category.WEAPON).weapon(WeaponClass.DAGGER)
                .grade(Grade.MYTHIC).stats(StatMap.of(ATK, 12500, CRIT, 40, CRIT_DMG, 120, DODGE, 8, REQ_DEX, 1400, LEVEL_REQ, 280)).skill("ZHUQUE").glow().model(300).desc("불(주작)의 기운이 깃든 단검"));
        reg(new ItemTemplate("spirit_xuanwu", "현무의 창", Material.NETHERITE_SHOVEL, Category.WEAPON).weapon(WeaponClass.SPEAR)
                .grade(Grade.MYTHIC).stats(StatMap.of(ATK, 11000, DEF, 25, HP, 40000, HP_PCT, 20, REQ_ADV, 1400, LEVEL_REQ, 280)).skill("XUANWU").glow().model(303).desc("자연(현무)의 기운이 깃든 창"));

        String[][] beasts = {{"qinglong", "청룡"}, {"baihu", "백호"}, {"zhuque", "주작"}, {"xuanwu", "현무"}};
        StatMap[] per = {StatMap.of(ATK, 300), StatMap.of(ATK, 150, CRIT, 5), StatMap.of(CRIT_DMG, 15), StatMap.of(HP, 3000)};
        StatMap[] set4 = {StatMap.of(ATK, 250), StatMap.of(CRIT, 2.5), StatMap.of(CRIT_DMG, 10), StatMap.of(HP_PCT, 5)};
        int[] colors = {0x2E6BD9, 0xE8E8E8, 0xD9352E, 0x2E8C4A};
        String[] parts = {"투구", "갑옷", "각반", "신발"};
        ArmorSlot[] slots = ArmorSlot.values();
        for (int b = 0; b < 4; b++) {
            for (int s = 0; s < 4; s++) {
                reg(new ItemTemplate("spirit_" + beasts[b][0] + "_a" + s, beasts[b][1] + "의 " + parts[s], armorMat("LEATHER", slots[s]), Category.ARMOR)
                        .slot(slots[s]).grade(Grade.LEGEND).stats(StatMap.of(DEF, 10, LEVEL_REQ, 70).addAll(per[b]))
                        .set("spirit_" + beasts[b][0], 4, set4[b]).color(colors[b]).glow().model(530 + b * 4 + s)
                        .trim(new String[]{"vex", "wild", "spire", "coast"}[b], new String[]{"gold", "lapis", "gold", "emerald"}[b]));
            }
        }
        String[][] fiends = {{"hundun", "혼돈의 투구"}, {"taotie", "도철의 갑옷"}, {"taowu", "도올의 바지"}, {"qiongqi", "궁기의 신발"}};
        for (int s = 0; s < 4; s++) {
            reg(new ItemTemplate("fiend_" + fiends[s][0], fiends[s][1], armorMat("NETHERITE", slots[s]), Category.ARMOR)
                    .slot(slots[s]).grade(Grade.LEGEND).stats(StatMap.of(DEF, 15, HP, 3000, ATK, 300, LEVEL_REQ, 80))
                    .set("fiend", 4, StatMap.of(LIFESTEAL, 2)).glow().model(550 + s).trim("silence", "redstone").desc("흑룡의 기운이 서린 사흉수 갑주"));
        }
        // 기운 파편/결정/기운
        String[][] el = {{"fire", "불", "주작"}, {"wind", "바람", "청룡"}, {"dark", "어둠", "흑룡"}, {"nature", "자연", "현무"}, {"earth", "땅", "백호"}};
        Material[] shard = {Material.BLAZE_POWDER, Material.FEATHER, Material.INK_SAC, Material.SLIME_BALL, Material.FLINT};
        Material[] ess = {Material.BLAZE_ROD, Material.HEART_OF_THE_SEA, Material.DRAGON_BREATH, scute(), Material.RAW_GOLD};
        for (int i = 0; i < el.length; i++) {
            reg(new ItemTemplate("shard_" + el[i][0], el[i][1] + "의 기운 파편", shard[i], Category.SHARD).grade(Grade.RARE)
                    .model(500).desc("5개를 모아 기운 뽑기 결정으로 교환 (/기운)"));
            reg(new ItemTemplate("crystal_" + el[i][0], el[i][1] + "의 기운 뽑기 결정", Material.AMETHYST_CLUSTER, Category.CRYSTAL).grade(Grade.UNIQUE).model(510 + i)
                    .desc("우클릭 시 30% 확률로 " + el[i][1] + "의 기운 획득"));
            reg(new ItemTemplate("essence_" + el[i][0], el[i][1] + "(" + el[i][2] + ")의 기운", ess[i], Category.ESSENCE).grade(Grade.LEGEND).glow()
                    .model(520).desc("사신수 장비 조합 재료 (/기운)"));
        }
    }

    private void registerMisc() {
        reg(new ItemTemplate("tool_gather_1", "초보자용 채집도구", Material.WOODEN_PICKAXE, Category.TOOL).tier(1).cooldown(180).value(15).price(3000, -1).model(700));
        reg(new ItemTemplate("tool_gather_2", "중급자용 채집도구", Material.IRON_PICKAXE, Category.TOOL).tier(2).cooldown(150).value(70).price(150000, -1).model(701));
        reg(new ItemTemplate("tool_gather_3", "숙련자용 채집도구", Material.DIAMOND_PICKAXE, Category.TOOL).tier(3).cooldown(120).value(100).price(750000, -1).model(702));

        // 회복 포션: 1.20.1 의 POTION 은 1개씩만 쌓이므로 64개씩 쌓이는 토끼발(RABBIT_FOOT)에 포션 모델을 씌움 · 우클릭으로 마심 (v5.4.22)
        reg(new ItemTemplate("potion_1", "하급 체력 회복 포션", Material.RABBIT_FOOT, Category.POTION).value(500).price(1000, -1).model(700).color(0xFF6B6B));
        reg(new ItemTemplate("potion_2", "중급 체력 회복 포션", Material.RABBIT_FOOT, Category.POTION).value(2000).price(5000, -1).model(701).color(0xE03131));
        reg(new ItemTemplate("potion_3", "상급 체력 회복 포션", Material.RABBIT_FOOT, Category.POTION).value(6000).price(25000, -1).model(702).color(0xA61E4D).grade(Grade.RARE));
        reg(new ItemTemplate("potion_4", "최상급 체력 회복 포션", Material.RABBIT_FOOT, Category.POTION).value(15000).price(80000, -1).model(703).color(0x5F0F40).grade(Grade.UNIQUE));

        reg(new ItemTemplate("ticket_protect", "파괴 방지권", Material.PAPER, Category.TICKET).grade(Grade.UNIQUE).price(1000000, -1).model(10).desc("강화 창에 넣으면 강화 파괴를 1회 방지"));
        reg(new ItemTemplate("ticket_rate10", "강화 확률 10% 증가권", Material.PAPER, Category.TICKET).grade(Grade.UNIQUE).price(500000, -1).model(11).desc("강화 창에 넣으면 성공 확률 +10% (중복 불가)"));
        reg(new ItemTemplate("ticket_war", "전쟁권", Material.PAPER, Category.TICKET).grade(Grade.LEGEND).price(3000000, -1).model(12).desc("길드장이 /전쟁 선포 <길드> 시 소모"));
        reg(new ItemTemplate("ticket_rune", "룬 변경권", Material.PAPER, Category.TICKET).price(10000, -1).model(13).desc("룬을 들고 /룬 변경 시 소모되어 옵션 재설정"));
        reg(new ItemTemplate("ticket_totem", "토템 뽑기권", Material.PAPER, Category.TICKET).grade(Grade.RARE).price(2000000, -1).model(14).desc("우클릭 시 랜덤 길드 토템 획득"));
        reg(new ItemTemplate("ticket_stat_reset", "스탯 초기화권", Material.PAPER, Category.TICKET).grade(Grade.RARE).price(1000000, -1).model(15).desc("우클릭 시 투자한 스탯을 모두 돌려받는다"));

        long[] hp = {100, 1000, 10000, 100000};
        long[] hpPrice = {10000, 80000, 600000, 4000000};
        for (int i = 0; i < 4; i++)
            reg(new ItemTemplate("hammer_" + hp[i], "[길드 성벽] 성벽 수리 망치 (" + String.format("%,d", hp[i]) + ")", Material.STONE_AXE, Category.HAMMER)
                    .value(hp[i]).price(hpPrice[i], -1).model(400 + i).desc("성벽 근처에서 쉬프트 10초 유지 시 성벽 회복"));

        // v5.10.14 성벽 설치권: 우리 길드 성 안에서 우클릭 두 번 → 바라보는 방향에 성벽을 세우고 체력 있는 성벽으로 등록
        reg(new ItemTemplate("wall_small", "[길드 성벽] 성벽 설치권 (소형)", Material.PAPER, Category.TICKET).grade(Grade.UNIQUE).price(2000000, -1).model(12)
                .desc("우리 길드 성 안에서 우클릭 → 설치 자리 미리보기", "한 번 더 우클릭하면 설치 (너비 7 · 높이 5 · 두께 2)", "성벽 체력 30,000 · 전쟁 중에는 설치 불가"));
        reg(new ItemTemplate("wall_large", "[길드 성벽] 성벽 설치권 (대형)", Material.PAPER, Category.TICKET).grade(Grade.LEGEND).price(5000000, -1).model(12)
                .desc("우리 길드 성 안에서 우클릭 → 설치 자리 미리보기", "한 번 더 우클릭하면 설치 (너비 11 · 높이 7 · 두께 3)", "성벽 체력 60,000 · 전쟁 중에는 설치 불가"));

        reg(new ItemTemplate("rune_low", "하급룬", Material.FIREWORK_STAR, Category.RUNE).tier(1).model(600).desc("/룬 에서 장착"));
        reg(new ItemTemplate("rune_mid", "중급룬", Material.FIREWORK_STAR, Category.RUNE).tier(2).grade(Grade.RARE).glow().model(601).desc("/룬 에서 장착"));
        reg(new ItemTemplate("rune_high", "상급룬", Material.FIREWORK_STAR, Category.RUNE).tier(3).grade(Grade.UNIQUE).glow().model(602).desc("/룬 에서 장착"));
        reg(new ItemTemplate("totem", "길드 토템", Material.LIGHTNING_ROD, Category.TOTEM).grade(Grade.UNIQUE).desc("길드장이 /길드 토템 에서 설치"));
        reg(new ItemTemplate("check", "수표", Material.PAPER, Category.CHECK).model(1).desc("우클릭 시 입금"));

        reg(new ItemTemplate("van_wool", "양털 블럭 x16", Material.WHITE_WOOL, Category.VANILLA).amount(16).price(2000, -1));
        reg(new ItemTemplate("van_arrow", "화살 x32", Material.ARROW, Category.VANILLA).amount(32).price(1000, -1));
    }

    /**
     * 신화 등급 유물. 월드보스에서 극히 낮은 확률로만 얻는 전용 디자인 무기 (특수 효과 보유)
     * CustomModelData 900 (+3000 각인 / +5000 완성)
     */
    private void registerRelics() {
        reg(new ItemTemplate("relic_sword", "여명을 가른 서약", Material.NETHERITE_SWORD, Category.WEAPON).weapon(WeaponClass.SWORD)
                .grade(Grade.MYTHIC).stats(StatMap.of(ATK, 3600, CRIT, 15, CRIT_DMG, 20, REQ_STR, 450, LEVEL_REQ, 95))
                .effect("JUDGEMENT").glow().model(900));
        reg(new ItemTemplate("relic_dagger", "심연의 쌍송곳니", Material.SHEARS, Category.WEAPON).weapon(WeaponClass.DAGGER)
                .grade(Grade.MYTHIC).stats(StatMap.of(ATK, 2400, CRIT, 30, CRIT_DMG, 40, LIFESTEAL, 2, REQ_DEX, 450, LEVEL_REQ, 95))
                .effect("ABYSS").glow().model(900));
        reg(new ItemTemplate("relic_axe", "일식", Material.NETHERITE_AXE, Category.WEAPON).weapon(WeaponClass.AXE)
                .grade(Grade.MYTHIC).stats(StatMap.of(ATK, 3200, CRIT, 10, ARMOR_PEN, 12, REQ_STR, 250, REQ_DEX, 250, LEVEL_REQ, 95))
                .effect("ECLIPSE").glow().model(900));
        reg(new ItemTemplate("relic_shield", "무너지지 않는 맹세", Material.SHIELD, Category.WEAPON).weapon(WeaponClass.SHIELD)
                .grade(Grade.MYTHIC).stats(StatMap.of(ATK, 1500, DEF, 16, HP, 8000, REQ_ADV, 450, LEVEL_REQ, 95))
                .effect("OATH").model(900));
    }

    /** 거북 비늘: 1.20.1 은 SCUTE, 1.20.5 이후는 TURTLE_SCUTE */
    private static Material scute() {
        Material m = Material.matchMaterial("SCUTE");
        if (m == null) m = Material.matchMaterial("TURTLE_SCUTE");
        return m == null ? Material.SLIME_BALL : m;
    }

    /** 무기고: 검·단검·도끼·방패·활·지팡이·창 종류 확장 (Lv.5~95) — ID armory_<종류>_<번호>, 모델 1300+ */
    public static final String[][] ARMORY_NAMES = {
            {"sword", "녹슨 장검", "기사의 장검", "은빛 세이버", "폭풍의 검", "흑요석 검", "용린검", "성기사의 검", "혼돈의 검"},
            {"dagger", "도둑의 단검", "쌍날 비수", "독사의 이빨", "달빛 단도", "암살자의 송곳", "그림자 발톱", "혈월의 단검", "허공의 비수"},
            {"axe", "나무꾼의 도끼", "전투 손도끼", "바이킹 도끼", "광전사의 도끼", "용암 도끼", "빙하 도끼", "거인의 도끼", "파멸의 도끼"},
            {"shield", "나무 방패", "철테 방패", "기사단 방패", "탑 방패", "거북 등껍질 방패", "용비늘 방패", "수호자의 방패", "영원의 방패"},
            {"bow", "단궁", "사냥꾼의 장궁", "엘프 장궁", "폭풍 활", "흑단 활", "불사조 활", "별빛 활", "천궁"},
            {"staff", "나뭇가지 지팡이", "수정 지팡이", "화염 지팡이", "서리 지팡이", "번개 지팡이", "생명의 지팡이", "심연의 지팡이", "대마법사의 지팡이"},
            {"spear", "훈련용 창", "기병창", "삼지창", "번개창", "용창", "천공창", "심판의 창", "신창"}};
    public static final int[] ARMORY_LV = {5, 15, 25, 35, 50, 65, 80, 95};

    public static String tierMat(int lv) {
        return lv < 10 ? "WOODEN" : lv < 20 ? "STONE" : lv < 40 ? "IRON" : lv < 60 ? "GOLDEN" : lv < 85 ? "DIAMOND" : "NETHERITE";
    }

    public static final String[][] ARMORY2_NAMES = {
            {"sword", "견습 기사의 검", "해적의 커틀러스", "사막의 시미터", "서리 칼날", "불꽃 검", "달빛 대검", "천둥의 검", "용살자의 검"},
            {"dagger", "사냥용 칼", "쌍둥이 비수", "맹독 단검", "은빛 스틸레토", "화염 단도", "밤의 발톱", "천둥 송곳", "영혼 도려내기"},
            {"axe", "손도끼", "벌목꾼의 큰도끼", "양날 전투도끼", "얼음 도끼", "화산 도끼", "달의 도끼", "천둥 도끼", "용골 도끼"},
            {"shield", "가죽 방패", "원형 방패", "무쇠 방패", "서리 방패", "화염 방패", "월광 방패", "천둥 방패", "용의 비늘 방패"},
            {"bow", "나무 활", "사수의 활", "합성궁", "서리 활", "화염 활", "월광 활", "천둥 활", "용의 뿔 활"},
            {"staff", "견습생의 지팡이", "물의 지팡이", "바람의 지팡이", "얼음 지팡이", "용암 지팡이", "달의 지팡이", "천둥의 지팡이", "현자의 지팡이"},
            {"spear", "대나무 창", "사냥꾼의 창", "철창", "서리 창", "화염 창", "월광 창", "천둥 창", "용의 창"}};
    public static final int[] ARMORY2_LV = {10, 20, 30, 45, 60, 75, 90, 110};

    private void registerArmory2() {
        registerArmorySet("armory2_", ARMORY2_NAMES, ARMORY2_LV, 1309);
        registerArmorySet("armory3_", ARMORY3_NAMES, ARMORY3_LV, 1329);
    }

    /** 무기고 III (v5.2.0): 지팡이 · 창 10종씩 (Lv.12~130) — 모델 1330+ */
    public static final String[][] ARMORY3_NAMES = {
            {"staff", "초승달 지팡이", "불씨 지팡이", "세계수 가지", "별똥별 지팡이", "봉인구 지팡이", "수정 군락 지팡이", "폭풍의 마도봉", "혹한의 마도봉", "성운의 마도봉", "태고의 마도봉"},
            {"spear", "미늘창", "언월도", "해신의 삼지창", "천마의 날개창", "사행 물결창", "방천화극", "폭염 미늘창", "한빙 언월도", "뇌신의 삼지창", "천룡 방천극"}};
    public static final int[] ARMORY3_LV = {12, 22, 35, 48, 58, 70, 85, 100, 115, 130};

    private void registerArmory() {
        registerArmorySet("armory_", ARMORY_NAMES, ARMORY_LV, 1299);
    }

    private void registerArmorySet(String prefix, String[][] names, int[] lvs, int cmdBase) {
        for (String[] row : names) {
            String kind = row[0];
            for (int i = 1; i < row.length; i++) {
                int L = lvs[i - 1];
                double base = 3 * L + 0.02 * L * L;
                Grade g = L <= 25 ? Grade.NORMAL : L <= 50 ? Grade.RARE : Grade.UNIQUE;
                long price = (long) (1500 * Math.pow(L, 1.55));
                String id = prefix + kind + "_" + i;
                int req = (int) (L * 2.5);
                ItemTemplate t = switch (kind) {
                    case "sword" -> new ItemTemplate(id, row[i], Material.valueOf(tierMat(L) + "_SWORD"), Category.WEAPON).weapon(WeaponClass.SWORD)
                            .stats(StatMap.of(ATK, base, CRIT, -5, REQ_STR, req, LEVEL_REQ, L));
                    case "dagger" -> new ItemTemplate(id, row[i], Material.SHEARS, Category.WEAPON).weapon(WeaponClass.DAGGER)
                            .stats(StatMap.of(ATK, base * 0.6, CRIT, 5 + i, CRIT_DMG, 5 + i * 4, REQ_DEX, req, LEVEL_REQ, L));
                    case "axe" -> new ItemTemplate(id, row[i], Material.valueOf(tierMat(L) + "_AXE"), Category.WEAPON).weapon(WeaponClass.AXE)
                            .stats(StatMap.of(ATK, base * 1.3, ARMOR_PEN, 2 + i * 2, REQ_STR, req / 2, REQ_DEX, req / 2, LEVEL_REQ, L));
                    case "shield" -> new ItemTemplate(id, row[i], Material.SHIELD, Category.WEAPON).weapon(WeaponClass.SHIELD)
                            .stats(StatMap.of(ATK, base * 0.5, DEF, 1 + i * 0.8, HP, 60 * L, REQ_ADV, req, LEVEL_REQ, L));
                    case "bow" -> new ItemTemplate(id, row[i], Material.BOW, Category.BOW)
                            .stats(StatMap.of(RANGED_ATK, base * 0.9, CRIT, 2 + i, REQ_DEX, req, LEVEL_REQ, L));
                    case "staff" -> new ItemTemplate(id, row[i], Material.STICK, Category.WEAPON).weapon(WeaponClass.CLUB)
                            .stats(StatMap.of(ATK, base * 0.3, MAGIC, base * 3.2, REQ_STR, req, LEVEL_REQ, L));
                    default -> new ItemTemplate(id, row[i], Material.valueOf(tierMat(L) + "_SHOVEL"), Category.WEAPON).weapon(WeaponClass.SPEAR)
                            .stats(StatMap.of(ATK, base * 0.95, ARMOR_PEN, 3 + i * 2, REQ_STR, req, REQ_DEX, req, LEVEL_REQ, L));
                };
                t.grade(g).price(price, price / 4).model(cmdBase + i);
                if (g != Grade.NORMAL) t.glow();
                reg(t);
            }
        }
    }

    /**
     * 초월 장비 (Lv.120 ~ 300, 5단계) + 렉스의 명작 5종 (렉스에게 재료를 주고 제작)
     */
    private void registerTranscend() {
        String[] tn = {"여명", "황혼", "성운", "공허", "태초"};
        int[] lv = {120, 160, 200, 250, 300};
        // v5.1.0 대폭 너프: 공격력 약 60% 감소 (이전 3800~9500), 부가 옵션 · 방어구 체력 · 세트 효과 절반 이하
        double[] atk = {1500, 1950, 2500, 3050, 3700};
        // 가격 5배 인상 (이전 300만~9000만). 판매가는 이전 수준 유지 (이전 구매가의 1/5)
        long[] price = {15_000_000, 40_000_000, 100_000_000, 225_000_000, 450_000_000};
        long[] sell = {600_000, 1_600_000, 4_000_000, 9_000_000, 18_000_000};
        Grade[] gr = {Grade.UNIQUE, Grade.UNIQUE, Grade.LEGEND, Grade.LEGEND, Grade.MYTHIC};
        String[] parts = {"투구", "갑주", "각반", "군화"};
        ArmorSlot[] slots = ArmorSlot.values();
        Material[] am = {Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE, Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS};
        for (int t = 0; t < 5; t++) {
            int L = lv[t], req = (int) (L * 3.2);
            String pre = "초월 · " + tn[t] + "의 ";
            reg(new ItemTemplate("trans_sword_" + (t + 1), pre + "대검", Material.NETHERITE_SWORD, Category.WEAPON).weapon(WeaponClass.SWORD).grade(gr[t])
                    .stats(StatMap.of(ATK, atk[t], CRIT, -10, ARMOR_PEN, 4 + t * 2, REQ_STR, req, LEVEL_REQ, L)).price(price[t], sell[t]).model(1101 + t).glow());
            reg(new ItemTemplate("trans_dagger_" + (t + 1), pre + "단검", Material.SHEARS, Category.WEAPON).weapon(WeaponClass.DAGGER).grade(gr[t])
                    .stats(StatMap.of(ATK, atk[t] * 0.6, CRIT, 4 + t, CRIT_DMG, 8 + t * 3, REQ_DEX, req, LEVEL_REQ, L)).price(price[t], sell[t]).model(1101 + t).glow());
            reg(new ItemTemplate("trans_axe_" + (t + 1), pre + "전투 도끼", Material.NETHERITE_AXE, Category.WEAPON).weapon(WeaponClass.AXE).grade(gr[t])
                    .stats(StatMap.of(ATK, atk[t] * 1.3, ARMOR_PEN, 6 + t * 2, REQ_STR, req * 0.7, REQ_DEX, req * 0.7, LEVEL_REQ, L)).price(price[t], sell[t]).model(1101 + t).glow());
            reg(new ItemTemplate("trans_shield_" + (t + 1), pre + "방패", Material.SHIELD, Category.WEAPON).weapon(WeaponClass.SHIELD).grade(gr[t])
                    .stats(StatMap.of(ATK, atk[t] * 0.5, DEF, 3 + t, HP, 300 + t * 200, REQ_ADV, req, LEVEL_REQ, L)).price(price[t], sell[t]).model(1101 + t).glow());
            for (int s = 0; s < 4; s++) {
                reg(new ItemTemplate("trans_armor_" + (t + 1) + "_" + s, pre + parts[s], am[s], Category.ARMOR).slot(slots[s]).grade(gr[t])
                        .stats(StatMap.of(DEF, 2 + t, HP, 1500 + t * 1100, REQ_ADV, req, LEVEL_REQ, L)).set("trans_" + (t + 1), 4, StatMap.of(HP_PCT, 3 + t, DEF, 2 + t * 0.5))
                        .price(price[t] / 2, sell[t] / 2).model(1200 + t * 4 + s).trim(new String[]{"sentry", "rib", "silence", "eye", "spire"}[t], new String[]{"quartz", "amethyst", "gold", "diamond", "netherite"}[t]));
            }
        }
        // 렉스의 명작 (상점 판매 없음, 렉스에게 제작)
        Object[][] M = {
                {"pender_sword", "렉스의 명작: 황혼 가르기", Material.NETHERITE_SWORD, WeaponClass.SWORD, 950, StatMap.of(ATK, 10500, CRIT, 15, ARMOR_PEN, 35, LIFESTEAL, 4, REQ_STR, 1100, LEVEL_REQ, 220)},
                {"pender_dagger", "렉스의 명작: 서리 송곳니", Material.SHEARS, WeaponClass.DAGGER, 951, StatMap.of(ATK, 6800, CRIT, 35, CRIT_DMG, 90, DODGE, 6, REQ_DEX, 1100, LEVEL_REQ, 220)},
                {"pender_axe", "렉스의 명작: 산맥 쪼개기", Material.NETHERITE_AXE, WeaponClass.AXE, 952, StatMap.of(ATK, 13000, ARMOR_PEN, 50, HP_PCT, 10, REQ_STR, 600, REQ_DEX, 600, LEVEL_REQ, 220)},
                {"pender_shield", "렉스의 명작: 꺾이지 않는 성벽", Material.SHIELD, WeaponClass.SHIELD, 953, StatMap.of(ATK, 5200, DEF, 22, HP, 20000, HP_PCT, 15, REQ_ADV, 1100, LEVEL_REQ, 220)},
                {"pender_spear", "렉스의 명작: 하늘 꿰뚫기", Material.NETHERITE_SHOVEL, WeaponClass.SPEAR, 954, StatMap.of(ATK, 9500, ARMOR_PEN, 40, SPEED, 10, REQ_STR, 650, REQ_DEX, 650, LEVEL_REQ, 220)}};
        for (Object[] m : M) {
            reg(new ItemTemplate((String) m[0], (String) m[1], (Material) m[2], Category.WEAPON).weapon((WeaponClass) m[3]).grade(Grade.MYTHIC)
                    .stats((StatMap) m[5]).price(-1, 50_000_000).model((Integer) m[4]).glow()
                    .desc("전설의 대장장이 렉스가 두드려 만든 명작", "세상에 몇 자루 없는 걸작"));
        }
    }

    /**
     * 확장 장비/아이템: 창 4종, 마법 지팡이 3종, 세트 방어구 3종(12부위), 주문서 5종
     */
    private void registerExpansion() {
        // 창 (민첩+힘, 긴 사거리 스킬) — 삽 계열 재료
        Material[] sp = {Material.STONE_SHOVEL, Material.IRON_SHOVEL, Material.GOLDEN_SHOVEL, Material.DIAMOND_SHOVEL};
        String[] spn = {"수련생의 창", "철갑 기병창", "황금 투창", "폭풍의 장창"};
        double[] spAtk = {30, 70, 150, 290};
        long[] spPrice = {20000, 90000, 400000, 1500000};
        for (int i = 0; i < 4; i++) {
            int lv = (i + 1) * 10;
            reg(new ItemTemplate("spear_" + lv, spn[i], sp[i], Category.WEAPON).weapon(WeaponClass.SPEAR)
                    .stats(StatMap.of(ATK, spAtk[i], ARMOR_PEN, 3 + i * 3, REQ_STR, lv * 2, REQ_DEX, lv * 2, LEVEL_REQ, lv)).price(spPrice[i], spPrice[i] / 4).model(100 + i));
        }
        // 마법 지팡이 (마력 위주: 무기 스킬 위력↑)
        String[] stn = {"견습 마법사의 지팡이", "비전 결정 지팡이", "별을 부르는 지팡이"};
        double[][] st = {{10, 120}, {30, 380}, {60, 900}};
        int[] stLv = {15, 35, 55};
        long[] stPrice = {60000, 450000, 2000000};
        for (int i = 0; i < 3; i++) {
            reg(new ItemTemplate("staff_" + (i + 1), stn[i], Material.STICK, Category.WEAPON).weapon(WeaponClass.CLUB).grade(i == 2 ? Grade.RARE : Grade.NORMAL)
                    .stats(StatMap.of(ATK, st[i][0], MAGIC, st[i][1], REQ_STR, stLv[i] * 3, LEVEL_REQ, stLv[i])).price(stPrice[i], stPrice[i] / 4).model(100 + i).glow());
        }
        // 세트 방어구: 야수 가죽(Lv25) · 비전 로브(Lv35) · 강철 기사(Lv50)
        Object[][] sets = {
                {"beast", "야수 가죽", "LEATHER", 0x7A4A22, 25, StatMap.of(DEF, 3, HP, 350, SPEED, 2), StatMap.of(SPEED, 8, CRIT, 3), 60000L},
                {"arcane", "비전 로브", "LEATHER", 0x5A2A9A, 35, StatMap.of(DEF, 2, HP, 250, MAGIC, 60), StatMap.of(MAGIC, 300, CRIT_DMG, 15), 150000L},
                {"knight", "강철 기사", "IRON", null, 50, StatMap.of(DEF, 6, HP, 900), StatMap.of(DEF, 8, HP_PCT, 10), 400000L}};
        String[] parts = {"투구", "갑옷", "각반", "장화"};
        ArmorSlot[] slots = ArmorSlot.values();
        for (Object[] s : sets) {
            for (int k = 0; k < 4; k++) {
                ItemTemplate t = new ItemTemplate("set_" + s[0] + "_" + k, s[1] + " " + parts[k], armorMat((String) s[2], slots[k]), Category.ARMOR)
                        .slot(slots[k]).grade(Grade.RARE).stats(((StatMap) s[5]).copy().add(LEVEL_REQ, (int) s[4]))
                        .set("set_" + s[0], 4, (StatMap) s[6]).price((Long) s[7], (Long) s[7] / 4)
                        .model(640 + java.util.Arrays.asList(sets).indexOf(s) * 4 + k)
                        .trim(new String[]{"wild", "vex", "sentry"}[java.util.Arrays.asList(sets).indexOf(s)], new String[]{"copper", "amethyst", "gold"}[java.util.Arrays.asList(sets).indexOf(s)]);
                if (s[3] != null) t.color((Integer) s[3]);
                reg(t);
            }
        }
        // 주문서 (우클릭 사용)
        Object[][] scrolls = {{"scroll_atk", "힘의 주문서", "10분간 힘 +15%", 20}, {"scroll_def", "수호의 주문서", "10분간 방어력 +5%", 21},
                {"scroll_speed", "질풍의 주문서", "10분간 이동속도 +15%", 22}, {"scroll_exp", "지혜의 주문서", "30분간 경험치 +50%", 23},
                {"scroll_return", "귀환 주문서", "5초 후 스폰으로 귀환", 24}};
        long[] scPrice = {30000, 30000, 25000, 80000, 5000};
        for (int i = 0; i < scrolls.length; i++) {
            Object[] sc = scrolls[i];
            reg(new ItemTemplate((String) sc[0], (String) sc[1], Material.PAPER, Category.TICKET).grade(i == 3 ? Grade.RARE : Grade.NORMAL)
                    .price(scPrice[i], -1).model((Integer) sc[3]).desc("우클릭: " + sc[2]));
        }
    }

    /** 몬스터·야생 동물 전리품 (전리품 상인에게 판매) + 직업 초기화권 */
    private void registerLoot() {
        Object[][] L = {
                {"loot_hide", "짐승 가죽", Material.RABBIT_HIDE, 300L, Grade.NORMAL, "야생 동물에게서 벗겨낸 가죽"},
                {"loot_meat", "신선한 고기", Material.PORKCHOP, 200L, Grade.NORMAL, "전리품 상인이 좋은 값에 사 간다"},
                {"loot_feather", "부드러운 깃털", Material.FEATHER, 150L, Grade.NORMAL, "닭과 새에게서 얻는다"},
                {"loot_wool", "양털 뭉치", Material.STRING, 150L, Grade.NORMAL, "깨끗한 양털"},
                {"loot_horn", "짐승의 뿔", Material.NAUTILUS_SHELL, 1500L, Grade.RARE, "거대한 짐승의 단단한 뿔"},
                {"loot_pelt", "최상급 모피", Material.LEATHER, 4000L, Grade.RARE, "흠집 하나 없는 귀한 모피"},
                {"loot_bone", "부서진 뼈", Material.BONE, 100L, Grade.NORMAL, "언데드의 잔해"},
                {"loot_slime", "끈적한 젤리", Material.SLIME_BALL, 120L, Grade.NORMAL, "탱글탱글하다"},
                {"loot_fang", "날카로운 송곳니", Material.PRISMARINE_SHARD, 500L, Grade.NORMAL, "사냥꾼들이 수집한다"},
                {"loot_venom", "독주머니", Material.FERMENTED_SPIDER_EYE, 700L, Grade.NORMAL, "조심해서 다룰 것"},
                {"loot_dust", "망령의 가루", Material.GLOWSTONE_DUST, 900L, Grade.NORMAL, "희미하게 빛나는 가루"},
                {"loot_ink", "그림자 잉크", Material.INK_SAC, 1500L, Grade.RARE, "빛을 빨아들이는 검은 액체"},
                {"loot_totem", "부족 토템 조각", Material.BRICK, 2500L, Grade.RARE, "오크 부족의 문양이 새겨져 있다"},
                {"loot_bandage", "미라의 붕대", Material.PAPER, 1500L, Grade.NORMAL, "수천 년 된 붕대"},
                {"loot_frost", "서리 결정", Material.LIGHT_BLUE_DYE, 1800L, Grade.RARE, "녹지 않는 얼음"},
                {"loot_ember", "불씨 조각", Material.BLAZE_POWDER, 1800L, Grade.RARE, "아직 따뜻하다"},
                {"loot_scale", "드레이크 비늘", Material.PHANTOM_MEMBRANE, 3000L, Grade.RARE, "하늘을 나는 것들의 비늘"},
                {"loot_eye", "심연의 눈", Material.SPIDER_EYE, 6000L, Grade.UNIQUE, "들여다보면 누군가 마주 본다"},
                {"loot_core", "마력 핵", Material.NETHER_STAR, 12000L, Grade.UNIQUE, "강한 마물의 심장에서 나온 결정"},
                {"loot_crown", "타락한 왕관 조각", Material.GOLD_NUGGET, 30000L, Grade.LEGEND, "몰락한 왕국의 흔적"},
                {"loot_steel", "골렘의 강철 파편", Material.IRON_NUGGET, 400L, Grade.NORMAL, "철 골렘의 몸에서 떨어진 파편"},
                {"loot_wave", "승리의 깃발 조각", Material.RED_DYE, 5000L, Grade.RARE, "필드 웨이브를 막아낸 증표"},
        };
        for (Object[] l : L) {
            reg(new ItemTemplate((String) l[0], (String) l[1], (Material) l[2], Category.MATERIAL).grade((Grade) l[4])
                    .price(-1, (Long) l[3]).desc((String) l[5], "전리품 상인에게 판매 (/상점 loot)"));
        }
        // 낚시 보상
        Object[][] F = {
                {"fish_small", "작은 피라미", Material.COD, 150L, Grade.NORMAL}, {"fish_carp", "통통한 붕어", Material.COD, 300L, Grade.NORMAL},
                {"fish_salmon", "은빛 연어", Material.SALMON, 500L, Grade.NORMAL}, {"fish_deep", "심해 초롱어", Material.PUFFERFISH, 1500L, Grade.RARE},
                {"fish_gold", "황금 잉어", Material.TROPICAL_FISH, 4000L, Grade.UNIQUE}, {"fish_treasure", "가라앉은 보물 상자", Material.CHEST_MINECART, 8000L, Grade.UNIQUE},
                {"fish_legend", "전설의 용왕어", Material.TROPICAL_FISH, 30000L, Grade.LEGEND}};
        int fi = 0;
        for (Object[] f : F)
            reg(new ItemTemplate((String) f[0], (String) f[1], (Material) f[2], Category.MATERIAL).grade((Grade) f[4]).price(-1, (Long) f[3]).model(1430 + fi++)
                    .desc("낚시로 얻은 물고기", "어부에게 판매 (/상점 fish)"));
        int ni = 0;   // v5.2.0 어종 (모델 1600 + 순번)
        for (kr.rpgcraft.world.FishSpecies.Species f : kr.rpgcraft.world.FishSpecies.NEW) {
            String when = f.cond().contains("NIGHT") ? " &8(밤)" : f.cond().contains("RAIN") ? " &8(비 올 때)" : "";
            reg(new ItemTemplate(f.id(), f.name(), f.material(), Category.MATERIAL).grade(f.grade()).price(-1, f.price()).model(1600 + ni++)
                    .desc("낚시로 얻은 물고기", "&7서식지: &f" + kr.rpgcraft.world.FishSpecies.habitatKo(f) + when, "어부에게 판매 (/상점 fish)"));
        }
        // 장신구 (반지·목걸이·귀걸이 × 하급·중급·상급)
        Material[] am = {Material.GOLD_NUGGET, Material.HEART_OF_THE_SEA, Material.AMETHYST_SHARD};
        String[] tn = {"하급", "중급", "상급"};
        long[] ap = {30000, 250000, -1};
        for (int k = 0; k < 3; k++)
            for (int t = 1; t <= 3; t++)
                reg(new ItemTemplate("acc_" + kr.rpgcraft.feature.AccessoryManager.KIND[k] + "_" + t, tn[t - 1] + " " + kr.rpgcraft.feature.AccessoryManager.KIND_KO[k],
                        am[k], Category.RUNE).tier(t).grade(t == 1 ? Grade.NORMAL : t == 2 ? Grade.RARE : Grade.UNIQUE).price(ap[t - 1], -1).model(1400 + k * 3 + t - 1)
                        .desc("/장신구 에서 장착", "무작위 옵션 " + (t == 3 ? 4 : 3) + "줄"));
        if (templates.get("loot_steel") != null) templates.get("loot_steel").model(1445);
        String[] lootIds = {"loot_hide", "loot_meat", "loot_feather", "loot_wool", "loot_horn", "loot_pelt", "loot_bone", "loot_slime", "loot_fang", "loot_venom",
                "loot_dust", "loot_ink", "loot_totem", "loot_bandage", "loot_frost", "loot_ember", "loot_scale", "loot_eye", "loot_core", "loot_crown", "loot_wave"};
        for (int i = 0; i < lootIds.length; i++) if (templates.get(lootIds[i]) != null) templates.get(lootIds[i]).model(1450 + i);   // 전리품 전용 모델
        reg(new ItemTemplate("boat", "나무 보트", Material.OAK_BOAT, Category.MATERIAL).price(50, -1));
        reg(new ItemTemplate("boots_flipper", "물갈퀴 신발", Material.LEATHER_BOOTS, Category.MATERIAL).price(500, -1)
                .desc("신으면 물속에서 빠르게 움직입니다"));
        reg(new ItemTemplate("boss_crystal", "보스 수정", Material.ECHO_SHARD, Category.TICKET).grade(Grade.LEGEND).glow().model(1446)
                .desc("보스를 토벌하면 기여자에게 주어지는 수정", "우클릭: 확률에 따라 그 보스의 장비 획득"));
        reg(new ItemTemplate("boss_chest", "보스 상자", Material.CHEST_MINECART, Category.TICKET).grade(Grade.UNIQUE).glow().model(1436)
                .desc("우클릭: 보스 드롭 중 하나를 뽑습니다"));
        reg(new ItemTemplate("balrog_seal", "발록의 봉인석", Material.FIRE_CHARGE, Category.TICKET).grade(Grade.MYTHIC).glow().model(1424)
                .desc("우클릭: 「발록」 소환", "봉인된 불꽃 악마가 깨어난다"));
        reg(new ItemTemplate("spirit_summon", "원혼의 부적", Material.GHAST_TEAR, Category.TICKET).grade(Grade.LEGEND).glow().model(1423)
                .desc("우클릭: 「몬스터의 원혼」 소환", "쓰러진 몬스터들의 원한이 깃들어 있다"));
        // 잠재능력
        reg(new ItemTemplate("potential_scroll", "잠재능력 부여 주문서", Material.PAPER, Category.TICKET).grade(Grade.RARE).price(300000, -1).model(1420)
                .desc("잠재능력이 없는 장비에 레어 잠재능력을 엽니다", "/잠재능력"));
        reg(new ItemTemplate("cube_red", "수상한 큐브", Material.RED_DYE, Category.TICKET).grade(Grade.RARE).price(500000, -1).model(1421)
                .desc("잠재능력 옵션을 다시 정합니다", "낮은 확률로 등급 상승 (유니크까지)"));
        reg(new ItemTemplate("cube_master", "명장의 큐브", Material.LIGHT_BLUE_DYE, Category.TICKET).grade(Grade.UNIQUE).glow().model(1422)
                .desc("잠재능력 옵션을 다시 정합니다", "등급 상승 확률이 높고 레전드리까지 가능"));
        reg(new ItemTemplate("treasure_map", "보물 지도", Material.MAP, Category.TICKET).grade(Grade.RARE).model(1425)
                .desc("우클릭: 해독 → 보물 위치가 정해집니다", "지도를 들면 거리, 나침반에 ✚ 방향이 표시됩니다", "도착하면 보물 상자와 수호자가 나타납니다"));
        reg(new ItemTemplate("rod_basic", "어부의 낚싯대", Material.FISHING_ROD, Category.TOOL).price(5000, -1)
                .desc("입질이 오면 장력 게이지가 나타납니다", "표시가 초록 구간일 때 우클릭 3번!"));
        // 레전더리 각인석
        for (var l : kr.rpgcraft.feature.LegendaryManager.Legend.values())
            reg(new ItemTemplate(l.sealId(), "레전더리 각인석: " + l.label, Material.ECHO_SHARD, Category.TICKET).grade(Grade.LEGEND).glow().model(1440 + l.ordinal())
                    .desc("우클릭: 레전더리 패시브 「" + l.label + "」 획득", l.desc, "서버 전체에서 단 한 명만 가질 수 있습니다"));
        reg(new ItemTemplate("ticket_job_reset", "직업 초기화권", Material.PAPER, Category.TICKET).price(500000, -1).model(15)
                .desc("우클릭: 기초·세부 직업을 초기화"));
    }

    /** 대장장이/보스 등에서 동적으로 장비를 만들 때 사용 */
    public ItemStack createEquipment(String baseId, String name, Grade grade, StatMap stats) {
        ItemStack it = create(baseId, 1);
        ItemData.setString(it, Keys.STATS, stats.serialize());
        ItemData.setString(it, Keys.GRADE, grade.name());
        if (name != null) ItemData.setString(it, Keys.NAME, name);
        ItemData.refresh(it);
        return it;
    }

    public static boolean isAir(ItemStack it) {
        return it == null || it.getType().isAir();
    }

    public static String statLine(Stat s, double v) {
        return s.label + " " + kr.rpgcraft.util.Text.signed(v, s.pct);
    }
}
