package kr.rpgcraft.feature;

import kr.rpgcraft.Keys;
import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.item.Category;
import kr.rpgcraft.item.Grade;
import kr.rpgcraft.item.ItemTemplate;
import kr.rpgcraft.item.WeaponClass;
import kr.rpgcraft.util.Fx;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.*;

/**
 * 무기별 고유 스킬.
 * 무기마다 우클릭(강공격) · Shift+좌클릭 · Shift+우클릭 스킬이 따로 있다.
 * 기본값은 무기의 "테마"(보스 세트·사신수·신화 유물·재료 등)와 종류로 모양(부채꼴·파동·돌진·관통·구체·연쇄 …)과
 * 속성(화염·빙결·맹독·뇌전·대지·출혈·성광 …)을 조합해 무기마다 다르게 만든다.
 * skills.yml 에 무기 ID 로 적으면 원하는 대로 덮어쓸 수 있다.
 */
public class SkillBook {
    public enum Shape {
        CONE("참격", 6, 1.0), WAVE("지면 가르기", 9, 1.0), DASH("돌진", 7, 0.9), LINE("관통", 7, 1.2), CIRCLE("폭발", 8, 0.85),
        LEAP("도약 강타", 9, 1.1), BLINK("그림자 습격", 8, 1.3), ORB("구체", 6, 1.0), RAIN("폭우", 10, 0.45), CHAIN("연쇄", 8, 0.9),
        PULL("소용돌이", 10, 1.0), FAN("난사", 6, 0.55), FLURRY("난도질", 7, 1.35), THROW("단검 투척", 6, 1.0);

        public final String label;
        public final double cd, factor;

        Shape(String label, double cd, double factor) {
            this.label = label;
            this.cd = cd;
            this.factor = factor;
        }
    }

    public enum Effect {
        NONE("강철", 0xE8EEF5, "추가 효과 없음"), FIRE("화염", 0xFF7A1F, "불태움"), FROST("빙결", 0x9FE0FF, "둔화"),
        POISON("맹독", 0x7DFF6A, "중독"), LIGHTNING("뇌전", 0x6BD8FF, "번개 추가 피해"), EARTH("대지", 0xC9A13B, "띄우기"),
        STUN("충격", 0xF4F4F4, "기절"), BLEED("혈흔", 0xB01030, "출혈 지속 피해"), HOLY("성광", 0xFFE9A0, "적중 시 체력 회복"),
        DARK("심연", 0x8A4AD0, "약화"), WIND("질풍", 0xCFFFF0, "밀쳐내기"), GUARD("수호", 0x7FC8FF, "사용 후 잠시 무적");

        public final String label, desc;
        public final int rgb;

        Effect(String label, int rgb, String desc) {
            this.label = label;
            this.rgb = rgb;
            this.desc = desc;
        }
    }

    public record SkillDef(String name, Shape shape, Effect effect, double power, double range, double cd, int rank) {
        public SkillDef(String name, Shape shape, Effect effect, double power, double range, double cd) {
            this(name, shape, effect, power, range, cd, 0);
        }
    }

    public record Set3(SkillDef strong, SkillDef shiftLeft, SkillDef shiftRight) {}

    private final RpgCraft plugin;
    private final Map<String, Set3> cache = new HashMap<>();
    private YamlConfiguration overrides = new YamlConfiguration();

    public SkillBook(RpgCraft plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        cache.clear();
        File f = new File(plugin.getDataFolder(), "skills.yml");
        overrides = f.exists() ? YamlConfiguration.loadConfiguration(f) : new YamlConfiguration();
    }

    // ------------------------------------------------------------------ 생성
    private static Effect theme(ItemTemplate t) {
        String id = t.id;
        if (id.contains("qinglong")) return Effect.LIGHTNING;
        if (id.contains("baihu")) return Effect.STUN;
        if (id.contains("zhuque")) return Effect.FIRE;
        if (id.contains("xuanwu")) return Effect.GUARD;
        if (id.contains("witch")) return Effect.POISON;
        if (id.contains("dwarf")) return Effect.EARTH;
        if (id.contains("harpy")) return Effect.WIND;
        if (id.contains("sea")) return Effect.FROST;
        if (id.contains("bungbung")) return Effect.FIRE;
        if (id.contains("elf")) return Effect.WIND;
        if (id.startsWith("relic_")) {
            String e = t.effect == null ? "" : t.effect;
            return switch (e) {
                case "JUDGEMENT" -> Effect.LIGHTNING;
                case "ABYSS" -> Effect.BLEED;
                case "ECLIPSE" -> Effect.DARK;
                default -> Effect.HOLY;
            };
        }
        if (id.startsWith("bs_")) return Effect.FIRE;          // 대장장이: 용광로
        if (id.startsWith("pender_")) return switch (id) {     // 렉스의 명작
            case "pender_sword" -> Effect.LIGHTNING; case "pender_dagger" -> Effect.FROST; case "pender_axe" -> Effect.EARTH;
            case "pender_shield" -> Effect.GUARD; default -> Effect.WIND; };
        if (id.startsWith("trans_")) return new Effect[]{Effect.HOLY, Effect.DARK, Effect.LIGHTNING, Effect.BLEED, Effect.FIRE}[Character.getNumericValue(id.charAt(id.length() - 1)) % 5];
        String nm = t.name == null ? "" : t.name;
        if (nm.contains("화염") || nm.contains("용암") || nm.contains("불") || nm.contains("화산")) return Effect.FIRE;
        if (nm.contains("서리") || nm.contains("얼음") || nm.contains("빙") || nm.contains("물")) return Effect.FROST;
        if (nm.contains("번개") || nm.contains("천둥") || nm.contains("폭풍")) return Effect.LIGHTNING;
        if (nm.contains("생명") || nm.contains("성") || nm.contains("수정") || nm.contains("현자")) return Effect.HOLY;
        if (nm.contains("심연") || nm.contains("그림자") || nm.contains("밤") || nm.contains("혼돈") || nm.contains("달")) return Effect.DARK;
        if (nm.contains("바람") || nm.contains("질풍")) return Effect.WIND;
        if (nm.contains("독") || nm.contains("맹독")) return Effect.POISON;
        if (id.contains("staff")) return new Effect[]{Effect.FROST, Effect.DARK, Effect.HOLY}[Math.abs(id.hashCode()) % 3];
        Effect[] plain = classEffects(t);
        return plain[Math.abs(id.hashCode()) % plain.length];
    }

    /** 테마가 없는 무기는 무기 종류의 컨셉에 맞는 속성만 (단검 = 출혈·맹독·그림자 …) */
    private static Effect[] classEffects(ItemTemplate t) {
        if (t.category == Category.BOW) return new Effect[]{Effect.NONE, Effect.WIND, Effect.POISON};
        WeaponClass wc = t.weaponClass;
        if (wc == null) return new Effect[]{Effect.STUN, Effect.EARTH};
        return switch (wc) {
            case DAGGER -> new Effect[]{Effect.BLEED, Effect.POISON, Effect.DARK};
            case AXE -> new Effect[]{Effect.EARTH, Effect.STUN, Effect.BLEED};
            case SHIELD -> new Effect[]{Effect.STUN, Effect.GUARD, Effect.EARTH};
            case SPEAR -> new Effect[]{Effect.WIND, Effect.BLEED, Effect.NONE};
            case CLUB -> new Effect[]{Effect.STUN, Effect.EARTH};
            default -> new Effect[]{Effect.NONE, Effect.BLEED, Effect.WIND, Effect.STUN};
        };
    }

    private static Shape[] pool(ItemTemplate t) {
        if (t.category == Category.BOW) return new Shape[]{Shape.FAN, Shape.RAIN, Shape.LINE, Shape.ORB, Shape.CHAIN};
        if (t.id.contains("staff")) return new Shape[]{Shape.ORB, Shape.RAIN, Shape.CHAIN, Shape.LINE, Shape.PULL};
        WeaponClass wc = t.weaponClass;
        if (wc == null) return new Shape[]{Shape.CIRCLE, Shape.CONE, Shape.LEAP};
        return switch (wc) {
            case DAGGER -> new Shape[]{Shape.BLINK, Shape.FLURRY, Shape.THROW, Shape.DASH};   // 암살자: 순간이동·연속 찌르기·투척·질주
            case AXE -> new Shape[]{Shape.LEAP, Shape.WAVE, Shape.CIRCLE, Shape.CONE, Shape.PULL};
            case SHIELD -> new Shape[]{Shape.DASH, Shape.CIRCLE, Shape.PULL, Shape.WAVE};
            case SPEAR -> new Shape[]{Shape.LINE, Shape.DASH, Shape.RAIN, Shape.LEAP};
            case CLUB -> new Shape[]{Shape.CIRCLE, Shape.LEAP, Shape.ORB};
            default -> new Shape[]{Shape.CONE, Shape.WAVE, Shape.DASH, Shape.LINE, Shape.CIRCLE, Shape.PULL};
        };
    }

    private static double gradePower(ItemTemplate t) {
        Grade g = t.grade == null ? Grade.NORMAL : t.grade;
        return switch (g) {
            case RARE -> 2.2;
            case UNIQUE -> 2.45;
            case LEGEND -> 2.75;
            case MYTHIC -> 3.05;
            default -> 2.0;
        };
    }

    private SkillDef make(ItemTemplate t, Shape s, Effect e, double mult) {
        double range = switch (s) {   // 넓은 범위
            case FLURRY -> 5; case THROW -> 16;
            case LINE -> 12; case DASH -> 9; case BLINK -> 13; case ORB -> 22; case RAIN -> 26; case CHAIN -> 14; case PULL -> 14; case WAVE -> 11; case FAN -> 24;
            case CIRCLE -> 7; case LEAP -> 5.5; default -> 7;
        };
        String sl = s.label;
        if (t.id.contains("staff")) sl = switch (s) { case ORB -> "마력구"; case RAIN -> "유성우"; case CHAIN -> "연쇄 마법"; case LINE -> "마력 광선"; case PULL -> "중력장"; default -> s.label; };
        String name = e.label + "의 " + sl;
        double cd = s.cd * (mult < 1 ? 1.3 : 1.0) * (t.category == Category.BOW ? 2.2 : 1.0);   // 활 스킬은 재사용 대기시간을 크게 늘림
        int rank = t.grade == null ? 0 : t.grade.ordinal() >= Grade.LEGEND.ordinal() ? 2 : t.grade.ordinal() >= Grade.UNIQUE.ordinal() ? 1 : 0;
        if (rank > 0) name = (rank == 2 ? "절기 · " : "오의 · ") + name;
        return new SkillDef(name, s, e, gradePower(t) * s.factor * mult, range * (1 + rank * 0.15), cd, rank);
    }

    private SkillDef fromYaml(ConfigurationSection c, SkillDef def) {
        if (c == null) return def;
        try {
            Shape s = Shape.valueOf(c.getString("shape", def.shape().name()).toUpperCase(Locale.ROOT));
            Effect e = Effect.valueOf(c.getString("effect", def.effect().name()).toUpperCase(Locale.ROOT));
            return new SkillDef(c.getString("name", def.name()), s, e, c.getDouble("power", def.power()), c.getDouble("range", def.range()), c.getDouble("cooldown", def.cd()), def.rank());
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("skills.yml 오류: " + ex.getMessage());
            return def;
        }
    }

    public Set3 of(ItemTemplate t) {
        if (t == null || (t.category != Category.WEAPON && t.category != Category.BOW)) return null;
        return cache.computeIfAbsent(t.id, k -> {
            Shape[] p = pool(t);
            int h = Math.abs(t.id.hashCode());
            Effect e = theme(t);
            Shape strong = p[h % p.length];
            Shape right = p[(h / 7 + 1) % p.length];
            if (right == strong) right = p[(Arrays.asList(p).indexOf(strong) + 1) % p.length];
            Shape left = t.weaponClass == WeaponClass.DAGGER && t.category == Category.WEAPON && !t.id.contains("staff")
                    ? (strong == Shape.BLINK || right == Shape.BLINK ? Shape.DASH : Shape.BLINK)
                    : t.id.contains("staff") ? (strong == Shape.CHAIN || right == Shape.CHAIN ? Shape.ORB : Shape.CHAIN)
                    : t.category == Category.BOW ? Shape.DASH : (strong == Shape.DASH || right == Shape.DASH) ? Shape.BLINK : Shape.DASH;
            Effect[] ce = classEffects(t);
            Effect e2 = e == Effect.NONE ? (ce[(h / 3) % ce.length] == Effect.NONE ? ce[(h / 3 + 1) % ce.length] : ce[(h / 3) % ce.length]) : e;
            SkillDef s1 = fromYaml(overrides.getConfigurationSection(t.id + ".strong"), make(t, strong, e, 1.0));
            SkillDef s2 = fromYaml(overrides.getConfigurationSection(t.id + ".shift-left"), make(t, left, e2, 0.7));
            SkillDef s3 = fromYaml(overrides.getConfigurationSection(t.id + ".shift-right"), make(t, right, e2, 0.85));
            return new Set3(s1, s2, s3);
        });
    }

    /** 아이템 설명(로어)에 넣을 줄 */
    public List<String> lore(ItemTemplate t) {
        Set3 s = of(t);
        List<String> out = new ArrayList<>();
        if (s == null) return out;
        if (t.skill == null) out.add("  &e우클릭 &f" + s.strong().name() + " &8(" + (int) s.strong().cd() + "초 · " + s.strong().effect().desc + ")");
        if (t.grade != null && t.grade.ordinal() >= Grade.RARE.ordinal()) {
            out.add("  &eShift+좌클릭 &f" + s.shiftLeft().name() + " &8(" + (int) s.shiftLeft().cd() + "초)");
            if (t.skill == null && t.weaponClass != WeaponClass.SHIELD) out.add("  &eShift+우클릭 &f" + s.shiftRight().name() + " &8(" + (int) s.shiftRight().cd() + "초)");
        }
        if (t.grade != null && t.grade.ordinal() >= Grade.LEGEND.ordinal()) out.add("  &6쉬프트 두 번 &f궁극기 &8(60초)");
        return out;
    }

    // ------------------------------------------------------------------ 시전
    public interface Hitter {
        void hit(Player p, LivingEntity t, double amount);
    }

    private List<LivingEntity> near(Player p, Location c, double r) {
        List<LivingEntity> out = new ArrayList<>();
        for (Entity en : c.getWorld().getNearbyEntities(c, r + 1, 4.5, r + 1)) {
            if (!(en instanceof LivingEntity le) || !plugin.combat().isEnemy(p, le)) continue;
            double dx = le.getLocation().getX() - c.getX(), dz = le.getLocation().getZ() - c.getZ();
            double half = le.getWidth() / 2;
            if (Math.hypot(dx, dz) <= r + 0.5 + half && Math.abs(le.getLocation().getY() + le.getHeight() / 2 - c.getY()) <= 4.5) out.add(le);
        }
        return out;
    }

    /** 발밑의 땅 높이 (공중이면 아래로 내려가 첫 블록 위) */
    private static Location ground(Location l) {
        Location g = l.clone();
        for (int i = 0; i < 12 && g.getBlockY() > g.getWorld().getMinHeight(); i++) {
            if (!g.clone().add(0, -0.1, 0).getBlock().isPassable()) break;
            g.add(0, -1, 0);
        }
        g.setY(Math.floor(g.getY()) + 0.05);
        return g;
    }

    private void apply(Player p, LivingEntity le, Effect e, double dmg, Hitter h) {
        switch (e) {
            case FIRE -> le.setFireTicks(Math.max(le.getFireTicks(), 60));
            case FROST -> le.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, 2));
            case POISON -> le.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 80, 0));
            case LIGHTNING -> { le.getWorld().strikeLightningEffect(le.getLocation()); h.hit(p, le, dmg * 0.3); }
            case EARTH -> le.setVelocity(le.getVelocity().setY(0.7));
            case STUN -> plugin.combat().stun(le, 25);
            case BLEED -> {
                for (int i = 1; i <= 3; i++) Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (le.isValid() && !le.isDead()) { h.hit(p, le, dmg * 0.12); le.getWorld().spawnParticle(Particle.REDSTONE, le.getLocation().add(0, 1, 0), 6, 0.2, 0.3, 0.2, 0, new Particle.DustOptions(Color.fromRGB(0xB01030), 1f)); }
                }, i * 20L);
            }
            case HOLY -> plugin.health().healPercent(p, 3);
            case DARK -> le.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 0));
            case WIND -> {
                Vector v = le.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
                if (v.lengthSquared() > 0.01) le.setVelocity(v.normalize().multiply(1.1).setY(0.35));
            }
            default -> { }
        }
    }

    public void cast(Player p, SkillDef s, double dmg, boolean bow, Hitter h) {
        cast(p, s, dmg, bow, false, h);
    }

    /** mage=true: 지팡이 — 조준 방향으로, 화살 대신 마법 */
    public void cast(Player p, SkillDef s, double dmg, boolean bow, boolean mage, Hitter h) {
        castOnce(p, s, dmg, bow, mage, h);
        if (s.rank() <= 0) return;
        Color col = Color.fromRGB(s.effect().rgb);
        // 잔향 연격: 같은 기술이 흰 빛으로 한 번 더 (유니크 1회 / 레전드 이상 2회)
        for (int i = 1; i <= s.rank(); i++) {
            int n = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline() || p.isDead()) return;
                SkillDef echo = new SkillDef(s.name(), s.shape() == Shape.DASH || s.shape() == Shape.LEAP || s.shape() == Shape.BLINK ? Shape.CIRCLE : s.shape(),
                        s.effect(), s.power(), s.range() * (1 + 0.1 * n), s.cd(), 0);
                castOnce(p, echo, dmg * 0.6, bow, mage, h);
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.8f, 1.2f + n * 0.2f);
            }, 7L * i);
        }
        if (s.rank() < 2) return;
        // 피니시: 앞쪽에 거대한 마법진이 펼쳐졌다가 대폭발
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline() || p.isDead()) return;
            Vector f = p.getLocation().getDirection().setY(0);
            if (f.lengthSquared() < 1e-4) f = new Vector(0, 0, 1);
            Location c = p.getLocation().add(f.normalize().multiply(Math.min(6, s.range() * 0.5)));
            kr.rpgcraft.util.Vfx.ring(c, s.range() * 0.9, col);
            kr.rpgcraft.util.Vfx.ring(c, s.range() * 0.5, Color.WHITE);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline()) return;
                kr.rpgcraft.util.Vfx.burst(c.clone().add(0, 1.2, 0), s.range() * 1.1, col);
                kr.rpgcraft.util.Vfx.burst(c.clone().add(0, 1.2, 0), s.range() * 0.6, Color.WHITE);
                for (int k = 0; k < 4; k++) kr.rpgcraft.util.Vfx.slash(c.clone().add(0, 1, 0), p.getLocation().getDirection().setY(0), s.range() * 1.2, k * 45, k % 2 == 0 ? col : Color.WHITE);
                for (int k = 0; k < 6; k++) {
                    double a = k * Math.PI / 3;
                    Location pl = c.clone().add(Math.cos(a) * s.range() * 0.6, 0, Math.sin(a) * s.range() * 0.6);
                    kr.rpgcraft.util.Vfx.beam(pl, pl.clone().add(0, 6, 0), 0.8, col);   // 빛기둥
                }
                c.getWorld().playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.6f);
                c.getWorld().playSound(c, Sound.ITEM_TRIDENT_THUNDER, 0.8f, 1.2f);
                c.getWorld().spawnParticle(Particle.FLASH, c.clone().add(0, 1, 0), 1);
                for (LivingEntity le : near(p, c, s.range() * 0.9)) {
                    h.hit(p, le, dmg * 0.9);
                    apply(p, le, s.effect(), dmg, h);
                    le.setVelocity(le.getVelocity().setY(0.6));
                }
            }, 8L);
        }, 7L * s.rank() + 6L);
    }

    private void castOnce(Player p, SkillDef s, double dmg, boolean bow, boolean mage, Hitter h) {
        World w = p.getWorld();
        Color col = Color.fromRGB(s.effect().rgb);
        Location eye = p.getEyeLocation();
        Vector look = eye.getDirection().normalize();
        Vector flat = look.clone().setY(0);
        if (flat.lengthSquared() < 1e-4) flat = new Vector(0, 0, 1);
        flat.normalize();
        Vector ff = flat;
        Vector dir = bow || mage ? look : flat;   // 근접 스킬은 수평 정면, 활·지팡이는 조준 방향
        boolean aim = bow || mage;
        Set<UUID> done = new HashSet<>();
        java.util.function.Consumer<LivingEntity> strike = le -> {
            if (!done.add(le.getUniqueId())) return;
            h.hit(p, le, dmg);
            apply(p, le, s.effect(), dmg, h);
            Location hc = le.getLocation().add(0, le.getHeight() * 0.55, 0);   // 맞은 자리마다 번쩍임 + 교차 베기
            kr.rpgcraft.util.Vfx.burst(hc, 1.6, col);
            kr.rpgcraft.util.Vfx.slash(hc, ff, 1.8, 60, Color.WHITE);
            w.spawnParticle(Particle.CRIT, hc, 8, 0.2, 0.3, 0.2, 0.4);
            WeaponFx.impact(hc, s.effect());   // (연출) 속성 폭발
        };
        // 시전 연출: 발밑 마법진 + 손끝 번쩍임
        kr.rpgcraft.util.Vfx.ring(p.getLocation(), 1.8, col);
        kr.rpgcraft.util.Vfx.burst(eye.clone().add(look.clone().multiply(1.1)).add(0, -0.3, 0), 1.6, col);
        w.spawnParticle(Particle.ENCHANTMENT_TABLE, p.getLocation().add(0, 1, 0), 30, 0.5, 0.6, 0.5, 1.2);
        WeaponFx.castCircle(p.getLocation(), eye.clone().add(look.clone().multiply(1.1)).add(0, -0.3, 0), s.effect());   // (연출) 마법진 · 속성 기둥
        switch (s.shape()) {
            case CONE -> {
                for (LivingEntity le : near(p, p.getLocation(), s.range())) {
                    Vector to = le.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
                    if (to.lengthSquared() < 0.5 || to.normalize().dot(ff) > 0.4) strike.accept(le);
                }
                kr.rpgcraft.util.Vfx.slash(p.getLocation().add(0, 1.1, 0).add(ff.clone().multiply(s.range() * 0.45)), ff, s.range() * 1.3, 0, col);
                kr.rpgcraft.util.Vfx.slash(p.getLocation().add(0, 0.9, 0).add(ff.clone().multiply(s.range() * 0.35)), ff, s.range() * 1.0, 180, Color.WHITE);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {   // 1·2틱 뒤 교차 참격 두 번 더
                    kr.rpgcraft.util.Vfx.slash(p.getLocation().add(0, 1.2, 0).add(ff.clone().multiply(s.range() * 0.5)), ff, s.range() * 1.4, 35, col);
                    kr.rpgcraft.util.Vfx.slash(p.getLocation().add(0, 1.2, 0).add(ff.clone().multiply(s.range() * 0.5)), ff, s.range() * 1.4, -35, col);
                }, 2L);
                w.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.7f);
                WeaponFx.coneSweep(p.getLocation(), ff, s.range(), s.effect());   // (연출)
            }
            case CIRCLE -> {
                for (LivingEntity le : near(p, p.getLocation(), s.range())) strike.accept(le);
                Fx.shockwave(plugin, p.getLocation(), s.range(), col);
                kr.rpgcraft.util.Vfx.burst(p.getLocation().add(0, 1, 0), s.range() * 0.9, col);
                Bukkit.getScheduler().runTaskLater(plugin, () -> kr.rpgcraft.util.Vfx.ring(p.getLocation(), s.range() * 1.2, Color.WHITE), 2L);
                w.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.3f);
                WeaponFx.eruption(p.getLocation(), s.range(), s.effect());   // (연출)
            }
            case LINE -> {
                if (bow) { heavyArrow(p, dmg); break; }
                for (Entity en : p.getNearbyEntities(s.range() + 1, 3, s.range() + 1)) {
                    if (!(en instanceof LivingEntity le) || !plugin.combat().isEnemy(p, le)) continue;
                    Vector to = le.getLocation().add(0, le.getHeight() / 2, 0).toVector().subtract(eye.toVector());
                    double along = to.dot(dir);
                    if (along >= 0 && along <= s.range() && to.clone().subtract(dir.clone().multiply(along)).length() <= 1.8 + le.getWidth() / 2) strike.accept(le);
                }
                kr.rpgcraft.util.Vfx.beam(eye.clone().add(0, -0.2, 0), eye.clone().add(0, -0.2, 0).add(dir.clone().multiply(s.range())), 1.6, col);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    kr.rpgcraft.util.Vfx.beam(eye.clone().add(0, -0.2, 0), eye.clone().add(0, -0.2, 0).add(dir.clone().multiply(s.range() + 1)), 0.7, Color.WHITE);
                    kr.rpgcraft.util.Vfx.burst(eye.clone().add(dir.clone().multiply(s.range())), 2.2, col);
                }, 1L);
                w.playSound(p.getLocation(), Sound.ITEM_TRIDENT_THROW, 1f, 1.2f);
                WeaponFx.lineSpiral(eye.clone().add(0, -0.2, 0), dir, s.range(), s.effect());   // (연출)
            }
            case DASH -> {
                p.setVelocity(ff.clone().multiply(bow ? -1.1 : 1.7).setY(bow ? 0.45 : 0.12));
                if (bow) { for (int i = -1; i <= 1; i++) arrow(p, rot(dir, i * 6).multiply(3.2), dmg, 1); break; }
                for (int i = 0; i < 7; i++) Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!p.isOnline()) return;
                    for (LivingEntity le : near(p, p.getLocation(), 1.9)) strike.accept(le);
                }, i);
                kr.rpgcraft.util.Vfx.beam(p.getLocation().add(0, 1, 0), p.getLocation().add(0, 1, 0).add(ff.clone().multiply(s.range())), 1.2, col);
                w.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.6f);
                WeaponFx.afterimage(p, s.effect(), 7);   // (연출) 잔상
            }
            case LEAP -> {
                p.setVelocity(ff.clone().multiply(0.9).setY(0.9));
                WeaponFx.afterimage(p, s.effect(), 10);   // (연출) 뛰어오르는 잔상
                new org.bukkit.scheduler.BukkitRunnable() {
                    int t;

                    @Override
                    public void run() {
                        if (!p.isOnline()) { cancel(); return; }
                        t++;
                        if (t < 5) return;                                  // 뛰어오르는 중
                        boolean landed = p.isOnGround() || t > 40;
                        if (!landed && t % 2 == 0) p.setVelocity(p.getVelocity().setY(Math.min(p.getVelocity().getY(), -0.9)));   // 빠르게 내려찍기
                        if (!landed) return;
                        cancel();
                        Location c = ground(p.getLocation());
                        for (LivingEntity le : near(p, c, s.range())) { strike.accept(le); le.setVelocity(le.getVelocity().setY(0.5)); }
                        Fx.shockwave(plugin, c, s.range(), col);
                        kr.rpgcraft.util.Vfx.burst(c.clone().add(0, 0.5, 0), 3.5, col);
                        w.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 0.9f);
                        WeaponFx.crater(c, s.range(), s.effect());   // (연출) 착지 크레이터
                    }
                }.runTaskTimer(plugin, 1L, 1L);
            }
            case BLINK -> {
                LivingEntity t = first(p, s.range());
                if (t == null) { Fx.dust(eye.clone().add(dir.multiply(2)), col, 1f); break; }
                Location behind = t.getLocation().subtract(t.getLocation().getDirection().setY(0).normalize().multiply(1.3));
                behind.setDirection(t.getLocation().toVector().subtract(behind.toVector()));
                w.spawnParticle(Particle.SMOKE_LARGE, p.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.02);
                Location blinkFrom = p.getLocation();
                if (behind.getBlock().isPassable() && behind.clone().add(0, 1, 0).getBlock().isPassable()) p.teleport(behind);
                WeaponFx.blink(blinkFrom, p.getLocation(), s.effect());   // (연출)
                strike.accept(t);
                kr.rpgcraft.util.Vfx.slash(t.getLocation().add(0, 1, 0), p.getLocation().getDirection(), 2.8, 45, col);
                kr.rpgcraft.util.Vfx.slash(t.getLocation().add(0, 1, 0), p.getLocation().getDirection(), 2.8, -45, col);
                w.playSound(t.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.8f);
            }
            case ORB -> {
                Location[] pos = {eye.clone()};
                Vector step = dir.clone().multiply(0.9);
                w.playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 0.8f, 1.4f);
                new org.bukkit.scheduler.BukkitRunnable() {
                    int n = 0;

                    @Override
                    public void run() {
                        pos[0].add(step);
                        if (n % 2 == 0) kr.rpgcraft.util.Vfx.burst(pos[0], 1.0, col);
                        WeaponFx.orbTrail(pos[0], s.effect(), n);   // (연출)
                        boolean boom = ++n * 0.9 >= s.range() || !pos[0].getBlock().isPassable() || !near(p, pos[0], 1.1).isEmpty();
                        if (boom) {
                            cancel();
                            for (LivingEntity le : near(p, pos[0], 2.8)) strike.accept(le);
                            kr.rpgcraft.util.Vfx.ring(pos[0], 2.8, col);
                            kr.rpgcraft.util.Vfx.burst(pos[0], 3.0, col);
                            w.playSound(pos[0], Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.5f);
                            WeaponFx.eruption(pos[0], 2.8, s.effect());   // (연출)
                        }
                    }
                }.runTaskTimer(plugin, 0L, 1L);
            }
            case RAIN -> {
                Location c = aim ? Optional.ofNullable(p.getTargetBlockExact((int) s.range())).map(b -> b.getLocation().add(0.5, 1, 0.5)).orElse(p.getLocation().add(ff.clone().multiply(8)))
                        : p.getLocation().add(ff.clone().multiply(Math.min(8, s.range() * 0.5)));
                WeaponFx.zone(c, 3, s.effect());   // (연출) 바닥 마법진
                for (int i = 0; i < 5; i++) Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    Fx.circle(c, 3, 18, col, 1.2f);
                    WeaponFx.zone(c, 3, s.effect());
                    for (int k = 0; k < 3; k++) {
                        Location hitAt = c.clone().add(Math.random() * 5 - 2.5, 0.3, Math.random() * 5 - 2.5);
                        kr.rpgcraft.util.Vfx.beam(hitAt.clone().add(0, 7, 0), hitAt, 0.6, col);
                        kr.rpgcraft.util.Vfx.burst(hitAt, 1.2, col);
                        WeaponFx.element(hitAt, s.effect(), 0.6);   // (연출)
                    }
                    done.clear();
                    for (LivingEntity le : near(p, c, 3)) strike.accept(le);
                    w.playSound(c, bow ? Sound.ENTITY_ARROW_HIT : Sound.ENTITY_GENERIC_BURN, 0.6f, 1.3f);
                }, i * 5L);
            }
            case CHAIN -> {
                LivingEntity cur = first(p, s.range());
                Location from = eye.clone();
                double d = dmg;
                for (int i = 0; i < 5 && cur != null; i++) {
                    Location to = cur.getLocation().add(0, cur.getHeight() / 2, 0);
                    Fx.line(from, to, 0.3, col, 1.2f);
                    kr.rpgcraft.util.Vfx.burst(to, 1.6, col);   // (연출) 연결 지점 번쩍
                    WeaponFx.element(to, s.effect(), 0.5);
                    if (done.add(cur.getUniqueId())) { h.hit(p, cur, d); apply(p, cur, s.effect(), d, h); }
                    from = to;
                    d *= 0.8;
                    LivingEntity next = null;
                    for (LivingEntity le : near(p, cur.getLocation(), 5)) if (!done.contains(le.getUniqueId())) { next = le; break; }
                    cur = next;
                }
                w.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.5f, 1.8f);
            }
            case PULL -> {
                Location c = p.getLocation().add(ff.clone().multiply(Math.min(7, s.range() * 0.5)));
                for (LivingEntity le : near(p, c, 5.5)) {
                    Vector v = c.toVector().subtract(le.getLocation().toVector());
                    if (v.lengthSquared() > 0.2) le.setVelocity(v.normalize().multiply(0.9).setY(0.2));
                }
                Fx.circle(c, 5, 24, col, 1.2f);
                WeaponFx.vortex(c, 5, s.effect(), 12);   // (연출) 빨려드는 나선
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    for (LivingEntity le : near(p, c, 3.2)) strike.accept(le);
                    Fx.shockwave(plugin, c, 3.2, col);
                    w.playSound(c, Sound.ENTITY_EVOKER_CAST_SPELL, 0.8f, 0.7f);
                    WeaponFx.eruption(c, 3.2, s.effect());   // (연출)
                }, 12L);
            }
            case WAVE -> {
                for (int i = 1; i <= (int) s.range(); i++) {
                    int step = i;
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        Location c = p.getLocation().add(ff.clone().multiply(step));
                        Fx.circle(c.clone().add(0, 0.2, 0), 1.2, 10, col, 1.3f);
                        kr.rpgcraft.util.Vfx.burst(c.clone().add(0, 0.6, 0), 1.6, col);
                        WeaponFx.element(c.clone().add(0, 0.4, 0), s.effect(), 0.7);   // (연출) 땅에서 솟는 속성
                        for (LivingEntity le : near(p, c, 1.7)) strike.accept(le);
                    }, step * 2L);
                }
                w.playSound(p.getLocation(), Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 0.6f, 0.8f);
            }
            case FLURRY -> {   // 난도질: 앞의 적 하나를 순식간에 6번 찌름
                LivingEntity t0 = first(p, s.range());
                if (t0 == null) { kr.rpgcraft.util.Vfx.slash(eye.clone().add(ff.clone().multiply(1.8)).add(0, -0.3, 0), ff, 3, 30, col); break; }
                for (int i = 0; i < 6; i++) {
                    int n = i;
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (!t0.isValid() || t0.isDead() || !p.isOnline()) return;
                        Location at = t0.getLocation().add(0, t0.getHeight() * 0.6, 0);
                        kr.rpgcraft.util.Vfx.slash(at, ff, 2.0, n % 2 == 0 ? 50 : -50, n == 5 ? Color.WHITE : col);
                        WeaponFx.element(at, s.effect(), 0.35);   // (연출)
                        h.hit(p, t0, dmg / 6);
                        if (n == 5) { apply(p, t0, s.effect(), dmg, h); kr.rpgcraft.util.Vfx.burst(at, 2.0, col); }
                        w.playSound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.7f, 1.6f + n * 0.08f);
                    }, i * 2L);
                }
            }
            case THROW -> {   // 단검 투척: 부채꼴로 단검 3자루, 처음 맞은 적에게 꽂힘
                for (int a = -12; a <= 12; a += 12) {
                    Vector d = rot(look.clone(), a).normalize();
                    Location from = eye.clone().add(0, -0.2, 0), hitAt = from.clone().add(d.clone().multiply(s.range()));
                    LivingEntity victim = null;
                    for (double t = 0.5; t <= s.range(); t += 0.5) {
                        Location c = from.clone().add(d.clone().multiply(t));
                        if (!c.getBlock().isPassable()) { hitAt = c; break; }
                        for (Entity en : w.getNearbyEntities(c, 0.8, 0.9, 0.8))
                            if (en instanceof LivingEntity le && plugin.combat().isEnemy(p, le)) { victim = le; break; }
                        if (victim != null) { hitAt = c; break; }
                    }
                    kr.rpgcraft.util.Vfx.beam(from, hitAt, 0.45, col);
                    kr.rpgcraft.util.Vfx.burst(hitAt, 1.1, col);
                    WeaponFx.lineSpiral(from, d, from.distance(hitAt), s.effect());   // (연출) 회전하는 단검 빛
                    if (victim != null) strike.accept(victim);
                }
                w.playSound(p.getLocation(), Sound.ENTITY_WITCH_THROW, 1f, 1.4f);
            }
            case FAN -> {
                int n = 7;
                for (int i = 0; i < n; i++) arrow(p, rot(dir, (i - n / 2) * 7).multiply(3.0), dmg, 2);
                w.playSound(p.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1f, 0.8f);
            }
        }
        if (s.effect() == Effect.GUARD) plugin.data().get(p).invulnUntil = Math.max(plugin.data().get(p).invulnUntil, System.currentTimeMillis() + 1200);
    }

    private LivingEntity first(Player p, double range) {
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().setY(0);
        if (dir.lengthSquared() < 1e-4) dir = new Vector(0, 0, 1);
        dir.normalize();
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (Entity en : p.getNearbyEntities(range, 4, range)) {
            if (!(en instanceof LivingEntity le) || !plugin.combat().isEnemy(p, le)) continue;
            Vector to = le.getLocation().add(0, 1, 0).toVector().subtract(eye.toVector());
            double along = to.dot(dir);
            Vector side = to.clone().subtract(dir.clone().multiply(along));
            side.setY(side.getY() * 0.4);   // 높이 차이는 너그럽게
            if (along < 0 || side.length() > 2.4) continue;
            if (along < bd) { bd = along; best = le; }
        }
        return best;
    }

    private static Vector rot(Vector v, double deg) {
        double r = Math.toRadians(deg), c = Math.cos(r), s = Math.sin(r);
        return new Vector(v.getX() * c - v.getZ() * s, v.getY(), v.getX() * s + v.getZ() * c);
    }

    private void arrow(Player p, Vector vel, double atk, int pierce) {
        Arrow ar = p.launchProjectile(Arrow.class, vel);
        ar.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        ar.setCritical(true);
        if (pierce > 0) ar.setPierceLevel(pierce);
        ar.getPersistentDataContainer().set(Keys.ARROW_ATK, PersistentDataType.DOUBLE, atk);
        ar.getPersistentDataContainer().set(Keys.ARROW_FORCE, PersistentDataType.DOUBLE, 1.0);
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (ar.isValid()) ar.remove(); }, 100L);
        WeaponFx.trail(ar, Color.fromRGB(0xFFE9A0), 30);   // (연출) 스킬 화살 꼬리
    }

    private void heavyArrow(Player p, double atk) {
        arrow(p, p.getEyeLocation().getDirection().multiply(4.2), atk, 6);
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_CROSSBOW_SHOOT, 1f, 0.6f);
    }
}
