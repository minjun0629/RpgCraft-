package kr.rpgcraft.feature;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.data.PlayerData;
import kr.rpgcraft.gui.Gui;
import kr.rpgcraft.stat.Stat;
import kr.rpgcraft.stat.StatMap;
import kr.rpgcraft.util.Fx;
import kr.rpgcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 직업.
 * 1차(기초 직업): Lv.10 에 직접 선택 — 전사 / 궁수 / 도적 / 수호자
 * 2차(세부 직업): Lv.40 에 전직 — 그 순간 가장 많이 투자한 스탯(힘·민첩·모험)에 따라 결정 → 나만의 직업
 * 직업은 능력치 보너스와 함께 무기 스킬·전투에 특수 효과(perk)를 준다.
 * (대장장이는 제작 전문 직업으로 별도)
 */
public class JobManager {
    public enum Base {
        WARRIOR("전사", Material.IRON_SWORD, "근접 전투의 달인. 힘과 체력이 오른다.", StatMap.of(Stat.STR_PCT, 10, Stat.HP_PCT, 5)),
        ARCHER("궁수", Material.BOW, "원거리 사격. 민첩과 치명타가 오른다.", StatMap.of(Stat.DEX_PCT, 10, Stat.CRIT, 5)),
        ROGUE("도적", Material.SHEARS, "빠르고 교활한 암살자. 회피와 치명타 피해가 오른다.", StatMap.of(Stat.DODGE, 5, Stat.CRIT_DMG, 20, Stat.SPEED, 5)),
        GUARDIAN("수호자", Material.SHIELD, "굳건한 방패. 모험과 방어력이 오른다.", StatMap.of(Stat.ADV_PCT, 10, Stat.DEF, 5));

        public final String label, desc;
        public final Material icon;
        public final StatMap bonus;

        Base(String label, Material icon, String desc, StatMap bonus) {
            this.label = label;
            this.icon = icon;
            this.desc = desc;
            this.bonus = bonus;
        }
    }

    /** 세부 직업: 기초 직업 × 주 스탯(0=힘, 1=민첩, 2=모험) */
    public enum Sub {
        BERSERKER(Base.WARRIOR, 0, "버서커", "강공격 피해 +30%, 강공격 적중 시 피해의 10% 회복", StatMap.of(Stat.STR_PCT, 15, Stat.LIFESTEAL, 3)),
        BLADEMASTER(Base.WARRIOR, 1, "검성", "평타 스킬이 2타마다 발동", StatMap.of(Stat.CRIT, 10, Stat.CRIT_DMG, 40)),
        WARLORD(Base.WARRIOR, 2, "전장의 군주", "강공격 후 4초간 받는 피해 30% 감소", StatMap.of(Stat.HP_PCT, 20, Stat.DEF, 6)),
        SNIPER(Base.ARCHER, 0, "저격수", "활 강공격 피해 +50%", StatMap.of(Stat.ARMOR_PEN, 15, Stat.CRIT_DMG, 30)),
        GALE(Base.ARCHER, 1, "질풍 궁수", "모든 무기 스킬 쿨타임 -30%", StatMap.of(Stat.SPEED, 10, Stat.DEX_PCT, 10)),
        RANGER(Base.ARCHER, 2, "레인저", "강공격에 맞은 적이 3초간 둔화", StatMap.of(Stat.HP_PCT, 15, Stat.DODGE, 5)),
        EXECUTIONER(Base.ROGUE, 0, "처형자", "체력 30% 이하의 적에게 피해 +50%", StatMap.of(Stat.ARMOR_PEN, 10, Stat.STR_PCT, 5)),
        SHADOW(Base.ROGUE, 1, "그림자", "강공격 후 1.5초간 무적", StatMap.of(Stat.DODGE, 10, Stat.CRIT, 10)),
        PLUNDERER(Base.ROGUE, 2, "약탈자", "몬스터·동물 처치 시 돈 +30%", StatMap.of(Stat.EXP_PCT, 10, Stat.HP_PCT, 10)),
        PALADIN(Base.GUARDIAN, 0, "성기사", "강공격 시 최대 체력 10% 회복", StatMap.of(Stat.HP_PCT, 10, Stat.MAGIC, 150)),
        SENTINEL(Base.GUARDIAN, 1, "파수꾼", "피격 시 20% 확률로 공격력 100% 반격", StatMap.of(Stat.DODGE, 8, Stat.CRIT, 5)),
        FORTRESS(Base.GUARDIAN, 2, "철옹성", "받는 피해의 10%를 공격자에게 반사", StatMap.of(Stat.DEF, 12, Stat.HP_PCT, 25));

        public final Base base;
        public final int stat;
        public final String label, perk;
        public final StatMap bonus;

        Sub(Base base, int stat, String label, String perk, StatMap bonus) {
            this.base = base;
            this.stat = stat;
            this.label = label;
            this.perk = perk;
            this.bonus = bonus;
        }

        static Sub of(Base b, int stat) {
            for (Sub s : values()) if (s.base == b && s.stat == stat) return s;
            return null;
        }
    }

    private final RpgCraft plugin;

    /** 3차 전직 (Lv.100): 2차 직업마다 하나씩, 2차 효과는 그대로 유지하고 더 강한 효과가 붙는다 */
    public enum Third {
        BLOOD_LORD(Sub.BERSERKER, "블러드 로드", "체력 50% 이하일 때 주는 피해 +40%", StatMap.of(Stat.STR_PCT, 25, Stat.LIFESTEAL, 4)),
        SWORD_GOD(Sub.BLADEMASTER, "검신", "치명타 시 추가 참격 (공격력 60%)", StatMap.of(Stat.CRIT, 15, Stat.CRIT_DMG, 80)),
        CONQUEROR(Sub.WARLORD, "정복자", "강공격 후 6초간 받는 피해 -40%, 주는 피해 +20%", StatMap.of(Stat.HP_PCT, 30, Stat.DEF, 10)),
        MARKSMAN(Sub.SNIPER, "명사수", "10칸 이상 떨어진 적에게 피해 +50%", StatMap.of(Stat.ARMOR_PEN, 25, Stat.CRIT_DMG, 50)),
        STORM_ARCHER(Sub.GALE, "폭풍의 사수", "모든 무기 스킬 쿨타임 -40%", StatMap.of(Stat.SPEED, 15, Stat.DEX_PCT, 20)),
        BEAST_KING(Sub.RANGER, "야수왕", "받는 피해 -15%, 강공격에 맞은 적 약화", StatMap.of(Stat.HP_PCT, 25, Stat.DODGE, 8)),
        REAPER(Sub.EXECUTIONER, "사신", "체력 40% 이하의 적에게 피해 +80%", StatMap.of(Stat.ARMOR_PEN, 20, Stat.STR_PCT, 15)),
        NIGHT_SHADE(Sub.SHADOW, "암영", "강공격 후 2.5초간 무적", StatMap.of(Stat.DODGE, 15, Stat.CRIT, 15)),
        GRAND_THIEF(Sub.PLUNDERER, "대도", "처치 시 돈 +60%", StatMap.of(Stat.EXP_PCT, 20, Stat.HP_PCT, 15)),
        CRUSADER(Sub.PALADIN, "성전사", "강공격 시 최대 체력 15% 회복, 근처 파티원 8% 회복", StatMap.of(Stat.HP_PCT, 20, Stat.MAGIC, 400)),
        GUARDIAN_GOD(Sub.SENTINEL, "수호신", "피격 시 35% 확률로 공격력 150% 반격", StatMap.of(Stat.DODGE, 12, Stat.CRIT, 10)),
        UNBREAKABLE(Sub.FORTRESS, "불괴", "받는 피해 -10%, 받은 피해의 20% 반사", StatMap.of(Stat.DEF, 18, Stat.HP_PCT, 40));

        public final Sub sub;
        public final String label, perk;
        public final StatMap bonus;

        Third(Sub sub, String label, String perk, StatMap bonus) {
            this.sub = sub;
            this.label = label;
            this.perk = perk;
            this.bonus = bonus;
        }

        public static Third of(Sub s) {
            for (Third t : values()) if (t.sub == s) return t;
            return null;
        }
    }

    /** 히든 직업이면 원래 직업(1·2·3차)은 쓰지 않음 (v5.3.0: 히든 직업이 원래 직업을 대신함) */
    private static boolean hidden(PlayerData d) {
        return kr.rpgcraft.world.HiddenJobManager.of(d) != null;
    }

    public static Third third(PlayerData d) {
        if (d.thirdJob == null || hidden(d)) return null;
        try {
            return Third.valueOf(d.thirdJob);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public boolean isThird(Player p, Third t) {
        return third(plugin.data().get(p)) == t;
    }

    /** 3차 전직: Lv.100, 돈 + 마력 핵 + 상급 결정 */
    // ------------------------------------------------------------------ 직업 스킬 (Q)
    private record JobSkill(String name, kr.rpgcraft.feature.SkillBook.Shape shape, kr.rpgcraft.feature.SkillBook.Effect effect, double power, double range, double cd) {}

    private static JobSkill jobSkill(Base b, Sub s) {
        if (s != null) return switch (s) {
            case BERSERKER -> new JobSkill("광폭한 회전베기", kr.rpgcraft.feature.SkillBook.Shape.CIRCLE, kr.rpgcraft.feature.SkillBook.Effect.BLEED, 2.6, 6, 12);
            case BLADEMASTER -> new JobSkill("천검난무", kr.rpgcraft.feature.SkillBook.Shape.CONE, kr.rpgcraft.feature.SkillBook.Effect.WIND, 2.8, 7, 11);
            case WARLORD -> new JobSkill("군주의 진격", kr.rpgcraft.feature.SkillBook.Shape.DASH, kr.rpgcraft.feature.SkillBook.Effect.STUN, 2.4, 10, 12);
            case SNIPER -> new JobSkill("관통 저격", kr.rpgcraft.feature.SkillBook.Shape.LINE, kr.rpgcraft.feature.SkillBook.Effect.NONE, 3.2, 22, 12);
            case GALE -> new JobSkill("폭풍 화살비", kr.rpgcraft.feature.SkillBook.Shape.RAIN, kr.rpgcraft.feature.SkillBook.Effect.WIND, 2.4, 16, 12);
            case RANGER -> new JobSkill("사냥꾼의 덫", kr.rpgcraft.feature.SkillBook.Shape.PULL, kr.rpgcraft.feature.SkillBook.Effect.POISON, 2.0, 9, 13);
            case EXECUTIONER -> new JobSkill("처형", kr.rpgcraft.feature.SkillBook.Shape.BLINK, kr.rpgcraft.feature.SkillBook.Effect.BLEED, 3.4, 12, 12);
            case SHADOW -> new JobSkill("그림자 난도질", kr.rpgcraft.feature.SkillBook.Shape.FLURRY, kr.rpgcraft.feature.SkillBook.Effect.DARK, 3.0, 6, 10);
            case PLUNDERER -> new JobSkill("단검 폭풍", kr.rpgcraft.feature.SkillBook.Shape.THROW, kr.rpgcraft.feature.SkillBook.Effect.POISON, 2.6, 16, 10);
            case PALADIN -> new JobSkill("성스러운 심판", kr.rpgcraft.feature.SkillBook.Shape.CIRCLE, kr.rpgcraft.feature.SkillBook.Effect.HOLY, 2.4, 7, 13);
            case SENTINEL -> new JobSkill("파수꾼의 방벽", kr.rpgcraft.feature.SkillBook.Shape.WAVE, kr.rpgcraft.feature.SkillBook.Effect.GUARD, 2.2, 9, 12);
            case FORTRESS -> new JobSkill("대지 분쇄", kr.rpgcraft.feature.SkillBook.Shape.LEAP, kr.rpgcraft.feature.SkillBook.Effect.EARTH, 2.6, 7, 13);
        };
        if (b == null) return null;
        return switch (b) {
            case WARRIOR -> new JobSkill("전사의 일격", kr.rpgcraft.feature.SkillBook.Shape.CONE, kr.rpgcraft.feature.SkillBook.Effect.NONE, 1.8, 6, 12);
            case ARCHER -> new JobSkill("속사", kr.rpgcraft.feature.SkillBook.Shape.FAN, kr.rpgcraft.feature.SkillBook.Effect.NONE, 1.6, 14, 12);
            case ROGUE -> new JobSkill("급습", kr.rpgcraft.feature.SkillBook.Shape.BLINK, kr.rpgcraft.feature.SkillBook.Effect.BLEED, 1.8, 10, 12);
            case GUARDIAN -> new JobSkill("방패 충격파", kr.rpgcraft.feature.SkillBook.Shape.CIRCLE, kr.rpgcraft.feature.SkillBook.Effect.STUN, 1.6, 6, 12);
        };
    }

    /** Q 키: 직업 스킬 (없으면 false) */
    public boolean castJobSkill(Player p) {
        PlayerData d = plugin.data().get(p);
        JobSkill js = jobSkill(base(d), sub(d));
        var hj = kr.rpgcraft.world.HiddenJobManager.of(d);
        if (hj != null) js = hj.line().equals("A")
                ? new JobSkill(hj.tier() >= 3 ? "명계 강림" : "영혼 수확", kr.rpgcraft.feature.SkillBook.Shape.PULL, kr.rpgcraft.feature.SkillBook.Effect.DARK, 2.4 + hj.tier() * 0.5, 9 + hj.tier(), 11)
                : new JobSkill(hj.tier() >= 3 ? "천구 붕괴" : "유성 낙하", kr.rpgcraft.feature.SkillBook.Shape.RAIN, kr.rpgcraft.feature.SkillBook.Effect.HOLY, 2.4 + hj.tier() * 0.5, 14 + hj.tier(), 11);
        if (js == null) return false;
        if (d.onCooldown("job_skill")) {
            Text.actionBar(p, "&c" + js.name() + " 재사용 대기 " + String.format("%.1f", d.remaining("job_skill") / 1000.0) + "초");
            return true;
        }
        Third th = third(d);
        int rank = th != null ? 2 : sub(d) != null ? 1 : 0;
        var def = new kr.rpgcraft.feature.SkillBook.SkillDef(js.name(), js.shape(), js.effect(), js.power() * (1 + rank * 0.35), js.range() * (1 + rank * 0.15), js.cd(), 0);   // 잔향 없이 한 번 (3차는 위력↑)
        boolean bow = d.stats.holdingBow;
        double dmg = (bow ? d.stats.ranged : Math.max(d.stats.attack, d.stats.magic)) * js.power() * (1 + d.level / 300.0);
        d.cooldown("job_skill", (long) (js.cd() * 1000 * plugin.weaponSkills().cooldownMultPublic(p)));
        plugin.skillBook().cast(p, def, dmg, bow, false, (pl, le, amt) -> plugin.combat().dealSkillDamage(pl, le, amt, true));
        Text.actionBar(p, "&6" + def.name());
        return true;
    }

    /** 3차 전직: 같은 기초 직업의 3차 직업 3가지 중에서 고름 */
    public void openThirdChoice(Player p) {
        PlayerData d = plugin.data().get(p);
        Base b = base(d);
        if (b == null || sub(d) == null) { Text.msg(p, "&c2차 전직을 먼저 하세요."); return; }
        if (d.thirdJob != null) { Text.msg(p, "&c이미 " + title(d) + " 입니다."); return; }
        kr.rpgcraft.gui.Gui g = new kr.rpgcraft.gui.Gui(3, "&83차 전직 선택") {
        };
        int slot = 11;
        for (Third t : Third.values()) {
            if (t.sub.base != b) continue;
            g.set(slot, kr.rpgcraft.gui.Gui.button(Material.DRAGON_HEAD, "&c&l" + t.label, "&f" + t.perk, "", "&e▶ 쉬프트 클릭하여 선택"), e -> {
                if (!e.isShiftClick()) return;
                p.closeInventory();
                advanceThird(p, t);
            });
            slot += 2;
        }
        g.fill(0, 26);
        g.open(p);
    }

    public void advanceThird(Player p) {
        openThirdChoice(p);
    }

    public void advanceThird(Player p, Third choice) {
        PlayerData d = plugin.data().get(p);
        Sub s = sub(d);
        int lv = plugin.getConfig().getInt("jobs.third-level", 100);
        long cost = plugin.getConfig().getLong("jobs.third-cost", 10_000_000);
        int cores = plugin.getConfig().getInt("jobs.third-cores", 10);
        if (hidden(d)) { Text.msg(p, "&5히든 직업은 다른 직업으로 전직할 수 없습니다."); return; }
        if (s == null) { Text.msg(p, "&c2차 전직을 먼저 하세요."); return; }
        if (d.thirdJob != null) { Text.msg(p, "&c이미 " + title(d) + " 입니다."); return; }
        if (d.level < lv) { Text.msg(p, "&c레벨 " + lv + " 부터 3차 전직할 수 있습니다."); return; }
        int have = 0;
        for (org.bukkit.inventory.ItemStack it : p.getInventory().getStorageContents()) if ("loot_core".equals(kr.rpgcraft.item.ItemData.id(it))) have += it.getAmount();
        if (have < cores) { Text.msg(p, "&c마력 핵 " + cores + "개가 필요합니다. (" + have + "/" + cores + ")"); return; }
        if (!plugin.economy().take(p, cost)) { Text.msg(p, "&c전직 비용 " + Text.money(cost) + "이 부족합니다."); return; }
        int left = cores;
        for (org.bukkit.inventory.ItemStack it : p.getInventory().getStorageContents()) {
            if (left <= 0) break;
            if (!"loot_core".equals(kr.rpgcraft.item.ItemData.id(it))) continue;
            int t = Math.min(left, it.getAmount());
            it.setAmount(it.getAmount() - t);
            left -= t;
        }
        Third t = choice;
        d.thirdJob = t.name();
        plugin.stats().refresh(p);
        Fx.helix(plugin, p, 3.0, 1.2, 40, Color.fromRGB(0xFF3A6A), Color.fromRGB(0xFFD23F));
        p.getWorld().strikeLightningEffect(p.getLocation());
        p.sendTitle(Text.c("&c&l3차 전직: " + t.label), Text.c("&f" + t.perk), 5, 70, 15);
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.6f);
        Text.announce(Text.PREFIX + Text.c("&c&l" + Text.name(p) + "&f님이 &c" + t.label + "&f(으)로 3차 전직했습니다!"));
    }

    public JobManager(RpgCraft plugin) {
        this.plugin = plugin;
    }

    public static Base base(PlayerData d) {
        if (hidden(d)) return null;
        try {
            return d.job == null ? null : Base.valueOf(d.job);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static Sub sub(PlayerData d) {
        if (hidden(d)) return null;
        try {
            return d.subJob == null ? null : Sub.valueOf(d.subJob);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public boolean is(Player p, Sub s) {
        return sub(plugin.data().get(p)) == s;
    }

    public String title(PlayerData d) {
        Sub s = sub(d);
        Base b = base(d);
        Third th = third(d);
        var hj = kr.rpgcraft.world.HiddenJobManager.of(d);
        String t = hj != null ? hj.label() : th != null ? th.label : s != null ? s.label : b != null ? b.label : "무직";
        return d.blacksmith ? t + " · 대장장이" : t;
    }

    public StatMap bonus(PlayerData d) {
        StatMap m = new StatMap();
        Base b = base(d);
        if (b != null) m.addAll(b.bonus);
        Sub s = sub(d);
        if (s != null) m.addAll(s.bonus);
        Third th = third(d);
        if (th != null) m.addAll(th.bonus);
        return m;
    }

    // ------------------------------------------------------------------ 선택 / 전직
    public void choose(Player p, Base b) {
        PlayerData d = plugin.data().get(p);
        int lv = plugin.getConfig().getInt("jobs.base-level", 10);
        if (hidden(d)) { Text.msg(p, "&5히든 직업은 다른 직업을 가질 수 없습니다."); return; }
        if (d.job != null) { Text.msg(p, "&c이미 " + title(d) + " 입니다. (직업 초기화권으로 초기화 가능)"); return; }
        if (d.level < lv) { Text.msg(p, "&c레벨 " + lv + " 부터 직업을 고를 수 있습니다."); return; }
        d.job = b.name();
        plugin.stats().refresh(p);
        p.sendTitle(Text.c("&6&l" + b.label), Text.c("&7" + b.desc), 5, 50, 10);
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
        Text.announce(Text.PREFIX + Text.c("&e" + Text.name(p) + "&f님이 &6" + b.label + "&f의 길을 걷기 시작했습니다."));
    }

    public int dominant(PlayerData d) {
        if (d.str >= d.dex && d.str >= d.adv) return 0;
        return d.dex >= d.adv ? 1 : 2;
    }

    public Sub preview(PlayerData d) {
        Base b = base(d);
        return b == null ? null : Sub.of(b, dominant(d));
    }

    public void advance(Player p) {
        PlayerData d = plugin.data().get(p);
        Base b = base(d);
        int lv = plugin.getConfig().getInt("jobs.advance-level", 40);
        long cost = plugin.getConfig().getLong("jobs.advance-cost", 500_000);
        if (hidden(d)) { Text.msg(p, "&5히든 직업은 다른 직업으로 전직할 수 없습니다."); return; }
        if (b == null) { Text.msg(p, "&c먼저 기초 직업을 선택하세요. (/직업 선택 <전사|궁수|도적|수호자>)"); return; }
        if (d.subJob != null) { Text.msg(p, "&c이미 " + title(d) + " 로 전직했습니다."); return; }
        if (d.level < lv) { Text.msg(p, "&c레벨 " + lv + " 부터 전직할 수 있습니다."); return; }
        if (!plugin.economy().take(p, cost)) { Text.msg(p, "&c전직 비용 " + Text.money(cost) + "이 부족합니다."); return; }
        Sub s = Sub.of(b, dominant(d));
        d.subJob = s.name();
        plugin.stats().refresh(p);
        Fx.helix(plugin, p, 2.6, 1.0, 30, Color.fromRGB(0xFFD23F), Color.fromRGB(0xE070FF));
        p.sendTitle(Text.c("&d&l전직: " + s.label), Text.c("&f" + s.perk), 5, 60, 15);
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.8f);
        Text.announce(Text.PREFIX + Text.c("&d" + Text.name(p) + "&f님이 &d" + s.label + "&f(으)로 전직했습니다!"));
    }

    public void reset(PlayerData d) {
        d.job = null;
        d.subJob = null;
        d.thirdJob = null;
    }

    // ------------------------------------------------------------------ 전투 특수 효과
    /** 가하는 피해 배율 (처형자) */
    public double outgoingMult(Player p, LivingEntity victim) {
        PlayerData d = plugin.data().get(p);
        Third th = third(d);
        double m = 1;
        double max = plugin.health().max(victim), ratio = max > 0 ? plugin.health().cur(victim) / max : 1;
        if (th == Third.REAPER && ratio <= 0.4) m *= 1.8;
        else if (is(p, Sub.EXECUTIONER) && ratio <= 0.3) m *= 1.5;
        if (th == Third.BLOOD_LORD && plugin.health().cur(p) / Math.max(1, plugin.health().max(p)) <= 0.5) m *= 1.4;
        if (th == Third.MARKSMAN && victim.getLocation().distanceSquared(p.getLocation()) >= 100) m *= 1.5;
        if (th == Third.CONQUEROR && d.counter("warlord_until") > System.currentTimeMillis()) m *= 1.2;
        return m;
    }

    /** 받는 피해 보정 (파수꾼 반격 / 철옹성 반사 / 전장의 군주 감소) */
    public double onDefend(Player vp, Entity source, double amount) {
        PlayerData d = plugin.data().get(vp);
        Sub s = sub(d);
        if (s == null) return amount;
        Third th = third(d);
        if ((s == Sub.WARLORD || th == Third.CONQUEROR) && d.counter("warlord_until") > System.currentTimeMillis()) amount *= th == Third.CONQUEROR ? 0.6 : 0.7;
        if (th == Third.BEAST_KING) amount *= 0.85;
        if (th == Third.UNBREAKABLE) amount *= 0.9;
        if (source instanceof LivingEntity le && !le.equals(vp)) {
            if (s == Sub.FORTRESS || th == Third.UNBREAKABLE) {
                double r = amount * (th == Third.UNBREAKABLE ? 0.2 : 0.1);
                Bukkit.getScheduler().runTask(plugin, () -> plugin.combat().applyDamage(le, r, vp, vp, false));
            } else if ((s == Sub.SENTINEL || th == Third.GUARDIAN_GOD) && ThreadLocalRandom.current().nextDouble() < (th == Third.GUARDIAN_GOD ? 0.35 : 0.2)) {
                double pw = th == Third.GUARDIAN_GOD ? 1.5 : 1.0;
                Bukkit.getScheduler().runTask(plugin, () -> plugin.combat().dealSkillDamage(vp, le, d.stats.attack * pw, true));
                Text.actionBar(vp, "&e반격!");
            }
        }
        return amount;
    }

    public double moneyMult(Player p) {
        if (isThird(p, Third.GRAND_THIEF)) return 1.6;
        return is(p, Sub.PLUNDERER) ? 1.3 : 1;
    }

    // ------------------------------------------------------------------ GUI
    public void open(Player p) {
        new JobGui(p).open(p);
    }

    private class JobGui extends Gui {
        JobGui(Player p) {
            super(3, "&8직업");
            PlayerData d = plugin.data().get(p);
            Base b = base(d);
            int slot = 10;
            for (Base x : Base.values()) {
                List<String> lore = new ArrayList<>(List.of("&7" + x.desc, ""));
                for (Sub s : Sub.values()) {
                    if (s.base != x) continue;
                    Third th3 = Third.of(s);
                    lore.add("&8 · " + new String[]{"힘", "민첩", "모험"}[s.stat] + " 위주 → &f" + s.label + " &8→ &c" + th3.label);
                    lore.add("&8     2차: " + s.perk);
                    lore.add("&8     3차: " + th3.perk);
                }
                lore.add("");
                lore.add(b == null ? "&e▶ 클릭하여 선택 (Lv." + plugin.getConfig().getInt("jobs.base-level", 10) + ")" : b == x ? "&a현재 직업" : "&8선택 불가");
                set(slot, button(x.icon, (b == x ? "&a&l" : "&6&l") + x.label, lore.toArray(new String[0])), e -> {
                    if (b == null) {
                        choose(p, x);
                        p.closeInventory();
                    }
                });
                slot += 2;
            }
            Sub pv = preview(d);
            set(22, button(Material.NETHER_STAR, "&d&l2차 전직", sub(d) != null ? "&a현재: " + sub(d).label : pv == null ? "&7기초 직업을 먼저 선택하세요" : "&f지금 전직하면 → &d" + pv.label,
                    pv == null ? "" : "&7" + pv.perk, "&7가장 많이 투자한 스탯으로 결정됩니다",
                    "&7조건: Lv." + plugin.getConfig().getInt("jobs.advance-level", 40) + ", " + Text.money(plugin.getConfig().getLong("jobs.advance-cost", 500_000)),
                    sub(d) == null ? "&e▶ 쉬프트 클릭하여 전직" : ""), e -> {
                if (e.isShiftClick() && sub(d) == null) {
                    p.closeInventory();
                    advance(p);
                }
            });
            Third th = third(d);
            Third next = sub(d) == null ? null : Third.of(sub(d));
            java.util.List<String> thirdLore = new java.util.ArrayList<>();
            if (b != null) {   // 내 기초 직업에서 갈 수 있는 3차 직업 전부
                thirdLore.add("&7" + b.label + " 의 3차 직업:");
                for (Sub s2 : Sub.values()) {
                    if (s2.base != b) continue;
                    Third t3 = Third.of(s2);
                    thirdLore.add((sub(d) == s2 ? "&e▶ " : "&8 · ") + "&f" + s2.label + " &8→ &c" + t3.label + " &8- " + t3.perk);
                }
                thirdLore.add("");
            }
            set(26, button(Material.DRAGON_HEAD, "&c&l3차 전직",
                    th != null ? "&a현재: " + th.label : next == null ? "&72차 전직을 먼저 하세요" : "&f전직하면 → &c" + next.label,
                    next == null ? "" : "&7" + next.perk,
                    "&7조건: Lv." + plugin.getConfig().getInt("jobs.third-level", 100) + ", " + Text.money(plugin.getConfig().getLong("jobs.third-cost", 10_000_000))
                            + ", 마력 핵 " + plugin.getConfig().getInt("jobs.third-cores", 10) + "개",
                    th == null && next != null ? "&e▶ 클릭하여 3차 직업 고르기" : ""), e -> {
                if (th == null && next != null) openThirdChoice(p);
            });
            org.bukkit.inventory.ItemStack tb = inv.getItem(26);
            if (tb != null && !thirdLore.isEmpty()) {
                var tm = tb.getItemMeta();
                java.util.List<String> all = new java.util.ArrayList<>();
                for (String l : thirdLore) all.add(Text.c(l));
                if (tm.getLore() != null) all.addAll(tm.getLore());
                tm.setLore(all);
                tb.setItemMeta(tm);
            }
            fill(0, 26);
        }
    }
}
