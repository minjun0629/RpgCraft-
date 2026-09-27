package kr.rpgcraft.mob;

import kr.rpgcraft.RpgCraft;
import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 몬스터 등급: 일반 → 정예 → 중간 보스 (→ 보스는 BossManager)
 *  정예: 체력 ×2.5, 공격 ×1.4, 보상 ×3, 금빛 이름, 추가 전리품
 *  중간 보스: 체력 ×6, 공격 ×1.8, 보상 ×8, 붉은 이름·발광, 희귀 전리품
 * 밤·핏빛 달에는 등장 확률이 오르고, 핏빛 달에는 모든 몬스터가 강해진다.
 */
public class MonsterTierManager implements Listener {
    public enum Tier { NORMAL, ELITE, MINIBOSS }

    private final RpgCraft plugin;
    private final NamespacedKey KEY;

    public MonsterTierManager(RpgCraft plugin) {
        this.plugin = plugin;
        this.KEY = new NamespacedKey(plugin, "mob_tier");
        org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, this::auraTick, 20L, 8L);
    }

    // ------------------------------------------------------------------ 오라 (발광 대신: 벽 너머로는 보이지 않는 입자)
    private final java.util.Map<java.util.UUID, Color> auras = new java.util.HashMap<>();

    private final java.util.Set<java.util.UUID> strong = new java.util.HashSet<>();

    public void aura(LivingEntity le, Color c) {
        aura(le, c, false);
    }

    /** strong=true: 웨이브 몬스터처럼 멀리서도 잘 보이는 진한 표시 */
    public void aura(LivingEntity le, Color c, boolean isStrong) {
        le.setGlowing(false);
        auras.put(le.getUniqueId(), c);
        if (isStrong) strong.add(le.getUniqueId());
    }

    private void auraTick() {
        if (auras.isEmpty()) return;
        double t = System.currentTimeMillis() / 300.0;
        auras.entrySet().removeIf(en -> {
            org.bukkit.entity.Entity e = org.bukkit.Bukkit.getEntity(en.getKey());
            if (!(e instanceof LivingEntity le) || !le.isValid() || le.isDead()) { strong.remove(en.getKey()); return true; }
            boolean near = false;
            for (org.bukkit.entity.Player p : le.getWorld().getPlayers()) if (p.getLocation().distanceSquared(le.getLocation()) < 32 * 32) { near = true; break; }
            if (!near) return false;
            if (strong.contains(le.getUniqueId())) {   // 발밑 고리 12점 + 몸을 따라 오르는 빛 기둥
                double rr = Math.max(0.8, le.getWidth() * 1.1);
                for (int i = 0; i < 12; i++) {
                    double a2 = t + i * Math.PI / 6;
                    le.getWorld().spawnParticle(Particle.DUST_COLOR_TRANSITION, le.getLocation().add(Math.cos(a2) * rr, 0.1, Math.sin(a2) * rr), 1, 0, 0, 0, 0,
                            new Particle.DustTransition(en.getValue(), Color.fromRGB(0xFFD0D0), 1.0f));
                }
                double hy = (t * 0.6) % Math.max(1, le.getHeight());
                le.getWorld().spawnParticle(Particle.DUST_COLOR_TRANSITION, le.getLocation().add(0, hy, 0), 3, rr * 0.4, 0.05, rr * 0.4, 0,
                        new Particle.DustTransition(en.getValue(), Color.WHITE, 0.8f));
                return false;
            }
            double r = Math.max(0.6, le.getWidth() * 0.9);
            for (int i = 0; i < 4; i++) {
                double a = t + i * Math.PI / 2;
                le.getWorld().spawnParticle(Particle.REDSTONE, le.getLocation().add(Math.cos(a) * r, 0.15 + (i % 2) * le.getHeight() * 0.5, Math.sin(a) * r),
                        1, 0, 0, 0, 0, new Particle.DustOptions(en.getValue(), 1.1f));
            }
            return false;
        });
    }

    // ------------------------------------------------------------------ 크기
    /**
     * 강한 몬스터를 크게: 슬라임·마그마 큐브·팬텀은 크기 값, 그 외는 1.20.5+ 서버의 크기 속성(GENERIC_SCALE)이 있으면 사용.
     * (1.20.1 바닐라에는 일반 몹 크기를 바꾸는 기능이 없다)
     */
    public void resize(LivingEntity le, double scale) {
        if (scale <= 1.0) return;
        if (le instanceof org.bukkit.entity.Slime s) { s.setSize(Math.min(8, (int) Math.round(s.getSize() * scale + 0.4))); return; }
        if (le instanceof org.bukkit.entity.Phantom ph) { ph.setSize(Math.min(20, (int) Math.round(ph.getSize() + scale * 3))); return; }
        try {
            Object attr = org.bukkit.attribute.Attribute.class.getField("GENERIC_SCALE").get(null);
            var inst = le.getAttribute((org.bukkit.attribute.Attribute) attr);
            if (inst != null) inst.setBaseValue(scale);
        } catch (Throwable ignored) {
        }
    }

    public Tier tier(LivingEntity e) {
        String t = e.getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
        try {
            return t == null ? Tier.NORMAL : Tier.valueOf(t);
        } catch (IllegalArgumentException ex) {
            return Tier.NORMAL;
        }
    }

    /** 자연 스폰 몬스터에 등급 굴리기 (커스텀 몬스터는 CustomMobManager 가 소환 후 호출) */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        if (e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL || !(e.getEntity() instanceof Enemy)) return;
        roll(e.getEntity());
    }

    public void roll(LivingEntity le) {
        MobManager.MobState s = plugin.mobs().peek(le);
        if (s == null || s.bossId != null) return;
        double mult = plugin.cycle() == null ? 1 : plugin.cycle().eliteChanceMult();
        double r = ThreadLocalRandom.current().nextDouble();
        if (plugin.cycle() != null && plugin.cycle().bloodMoon()) { // 핏빛 달: 모두 강화
            s.maxHp *= 1.3; s.hp = s.maxHp; s.damage *= 1.2;
        }
        // 너무 낮은 레벨의 몬스터는 정예·중간 보스가 되지 않는다 (스폰 근처의 약한 중간 보스 방지)
        boolean canMini = s.level >= plugin.getConfig().getInt("mobs.miniboss-min-level", 15);
        boolean canElite = s.level >= plugin.getConfig().getInt("mobs.elite-min-level", 5);
        if (canMini && r < plugin.getConfig().getDouble("mobs.miniboss-chance", 0.012) * mult) apply(le, s, Tier.MINIBOSS);
        else if (canElite && r < plugin.getConfig().getDouble("mobs.elite-chance", 0.07) * mult) apply(le, s, Tier.ELITE);
        else plugin.mobs().updateName(le, s);
    }

    public void apply(LivingEntity le, MobManager.MobState s, Tier t) {
        if (t == Tier.NORMAL) return;
        boolean mini = t == Tier.MINIBOSS;
        // 레벨을 올린 뒤(정예 +2, 중간 보스 +5) 그 레벨 기준으로 강화 → 약한 지역에서도 이름값을 하게
        MobManager mm = plugin.mobs();
        int oldL = Math.max(1, s.level), newL = oldL + (mini ? 5 : 2);
        double hpF = mm.hpFor(newL) / Math.max(1, mm.hpFor(oldL)), dmgF = mm.damageFor(newL) / Math.max(1, mm.damageFor(oldL));
        s.level = newL;
        s.maxHp *= hpF * (mini ? 5 : 2.2);
        s.hp = s.maxHp;
        s.damage *= dmgF * (mini ? 1.6 : 1.3);
        s.def = Math.min(85, s.def + (mini ? 12 : 5));
        s.exp *= mini ? 4 : 2;
        s.money = (long) (s.money * (mini ? 4 : 2));
        s.baseName = (mini ? "&c&l[중간 보스] &c" : "&6[정예] &e") + s.baseName.replaceAll("^&.", "");
        le.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, t.name());
        aura(le, mini ? Color.fromRGB(0xFF3030) : Color.fromRGB(0xFFB020));
        resize(le, mini ? plugin.getConfig().getDouble("mobs.miniboss-scale", 1.6) : plugin.getConfig().getDouble("mobs.elite-scale", 1.25));
        plugin.mobs().updateName(le, s);
        le.getWorld().spawnParticle(Particle.REDSTONE, le.getLocation().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0,
                new Particle.DustOptions(mini ? Color.RED : Color.ORANGE, 1.3f));
    }

    /**
     * 레벨 구간별 전리품 (낮은 레벨 몬스터가 고급 재료를 떨어뜨리지 않게)
     *  ~29 / 30~59 / 60~89 / 90~   정예 1개, 중간 보스 2개 + 확률 추가
     */
    private java.util.List<String> loot(Tier t, int lv, ThreadLocalRandom r) {
        java.util.List<String> out = new java.util.ArrayList<>();
        int b = lv < 30 ? 0 : lv < 60 ? 1 : lv < 90 ? 2 : 3;
        String[][] elite = {{"loot_fang", "loot_bone", "potion_1", "crystal_low"}, {"loot_totem", "crystal_low", "crystal_mid", "potion_2"},
                {"crystal_mid", "loot_ink", "loot_totem", "potion_3"}, {"crystal_high", "loot_eye", "loot_scale", "potion_3"}};
        String[][] mini = {{"crystal_low", "rune_low", "loot_totem", "potion_2"}, {"crystal_mid", "rune_low", "ticket_rune", "loot_ink"},
                {"crystal_high", "rune_mid", "loot_core", "loot_eye"}, {"crystal_top", "rune_mid", "loot_core", "loot_crown"}};
        String acc = "acc_" + kr.rpgcraft.feature.AccessoryManager.KIND[r.nextInt(3)] + "_" + (b >= 3 ? 3 : b >= 1 ? 2 : 1);
        if (r.nextDouble() < (t == Tier.MINIBOSS ? 0.25 : 0.05)) out.add(acc);
        if (t == Tier.MINIBOSS && r.nextDouble() < 0.08) out.add("cube_master");
        if (r.nextDouble() < (t == Tier.MINIBOSS ? 0.12 : 0.02)) out.add("cube_red");
        if (t == Tier.ELITE) {
            out.add(elite[b][r.nextInt(elite[b].length)]);
            if (b >= 2 && r.nextDouble() < 0.1) out.add("rune_mid");
        } else {
            out.add(mini[b][r.nextInt(mini[b].length)]);
            out.add(mini[b][r.nextInt(mini[b].length)]);
            if (b >= 1 && r.nextDouble() < 0.15) out.add(b >= 3 ? "rune_high" : "rune_mid");
            if (b >= 2 && r.nextDouble() < 0.08) out.add("ticket_rate10");
            if (b >= 3 && r.nextDouble() < 0.05) out.add(new String[]{"shard_fire", "shard_wind", "shard_nature", "shard_earth"}[r.nextInt(4)]);
            if (b >= 3 && r.nextDouble() < 0.02) out.add("ticket_protect");
        }
        return out;
    }

    private static final String[] RARE_PREFIX = {"crystal_mid", "crystal_high", "crystal_top", "loot_core", "loot_crown", "loot_eye",
            "rune_", "ticket_", "acc_", "treasure_map", "shard_", "cube_", "scroll_"};

    /** 일반 등급 몬스터(정예·중간 보스·보스 제외)가 떨어뜨리는 희귀 아이템을 배율만큼만 남김 */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onNormalRareDrops(EntityDeathEvent e) {
        LivingEntity ent = e.getEntity();
        if (!(ent instanceof org.bukkit.entity.Player) && !ent.getPersistentDataContainer().has(kr.rpgcraft.Keys.BOSS, PersistentDataType.STRING)) {
            e.getDrops().removeIf(it -> { String id = kr.rpgcraft.item.ItemData.id(it); return id != null && id.startsWith("potion_"); });   // 사냥으로 포션 안 나옴
            if (ent.getKiller() != null && plugin.mobs().peek(ent) != null
                    && ThreadLocalRandom.current().nextDouble() < plugin.getConfig().getDouble("mobs.potential-scroll-chance", 0.0007))
                e.getDrops().add(plugin.items().create("potential_scroll", 1));   // 모든 몬스터 0.1%
        }
        if (tier(ent) != Tier.NORMAL || ent.getPersistentDataContainer().has(kr.rpgcraft.Keys.BOSS, PersistentDataType.STRING)) return;
        double keep = plugin.getConfig().getDouble("mobs.normal-rare-drop-mult", 0.4);
        var lsd = plugin.mobs().peek(ent);
        if (lsd != null && lsd.level >= 100) {   // Lv.100 이상: 전리품·희귀 드롭 추가로 줄임
            double hk = plugin.getConfig().getDouble("mobs.high-level-loot-mult", 0.6);
            keep *= hk;
            ThreadLocalRandom rr = ThreadLocalRandom.current();
            e.getDrops().removeIf(it -> { String id = kr.rpgcraft.item.ItemData.id(it); return id != null && id.startsWith("loot_") && rr.nextDouble() >= hk; });
        }
        if (keep >= 1) return;
        final double keepF = keep;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        e.getDrops().removeIf(it -> {
            String id = kr.rpgcraft.item.ItemData.id(it);
            if (id == null) return false;
            for (String pre : RARE_PREFIX) if (id.startsWith(pre)) return r.nextDouble() >= keepF;
            return false;
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(EntityDeathEvent e) {
        String boss = e.getEntity().getPersistentDataContainer().get(kr.rpgcraft.Keys.BOSS, PersistentDataType.STRING);
        if (boss != null && kr.rpgcraft.world.WorldBossManager.isWorldBoss(boss)
                && ThreadLocalRandom.current().nextDouble() < plugin.getConfig().getDouble("legendary.world-boss-drop-chance", 0.03)) {
            var ls = kr.rpgcraft.feature.LegendaryManager.Legend.values();
            e.getDrops().add(plugin.items().create(ls[ThreadLocalRandom.current().nextInt(ls.length)].sealId(), 1));
            kr.rpgcraft.util.Text.announce(kr.rpgcraft.util.Text.PREFIX + kr.rpgcraft.util.Text.c("&6&l레전더리 각인석&f이 떨어졌습니다!"));
        }
        Tier t = tier(e.getEntity());
        if (t == Tier.NORMAL || e.getEntity().getKiller() == null) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        MobManager.MobState st = plugin.mobs().peek(e.getEntity());
        int lv = st == null ? 1 : st.level;
        for (String id : loot(t, lv, r)) {
            ItemStack it = plugin.items().create(id, 1);
            if (it != null) e.getDrops().add(it);
        }
        if (t == Tier.MINIBOSS && lv >= plugin.getConfig().getInt("mobs.legend-seal-min-level", 70)
                && r.nextDouble() < plugin.getConfig().getDouble("mobs.miniboss-legend-seal-chance", 0.002)) {
            var ls = kr.rpgcraft.feature.LegendaryManager.Legend.values();
            e.getDrops().add(plugin.items().create(ls[r.nextInt(ls.length)].sealId(), 1));
        }
    }
}
