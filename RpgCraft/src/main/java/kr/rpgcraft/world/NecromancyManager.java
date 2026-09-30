package kr.rpgcraft.world;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.data.PlayerData;
import kr.rpgcraft.gui.Gui;
import kr.rpgcraft.mob.CustomMobManager;
import kr.rpgcraft.util.Text;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * v5.10.9 히든 직업 「네크로맨서」(C 계열) — 쓰러뜨린 몬스터의 영혼으로 나만의 언데드 군단을 만든다.
 * <ul>
 *   <li>네크로맨서가 커스텀 몬스터를 잡으면 낮은 확률로 그 몬스터의 <b>영혼</b>, 무엇을 잡든 <b>사령 정수</b>를 얻음</li>
 *   <li>영혼 + 정수로 군단원을 일으킴 (성공 확률 · 등급은 운). 영혼의 주인이 모습과 역할(전사 · 수호 · 궁수 · 술사)을 정하고, 각인(특성)은 직접 고름</li>
 *   <li>군단원은 주인의 공격력 · 체력에 비례해 강해지고, 정수 · 영혼으로 강화 (실패하면 재료만 사라짐)</li>
 *   <li>소환해 두는 동안 1분마다 정수를 유지비로 씀. 쓰러진 군단원은 한동안 부를 수 없음 (정수로 즉시 부활)</li>
 * </ul>
 * 몸은 길들인 늑대(주인을 따라다니고 주인의 적을 무는 AI)에 커스텀 몬스터 3D 모델을 씌운 것.
 */
public class NecromancyManager implements Listener, CommandExecutor {
    enum Role {
        WARRIOR("전사", "&c", 0.70, 0.25, 0.15, "적에게 달려들어 벰"),
        TANK("수호", "&9", 1.40, 0.12, 0.35, "주인을 노리는 적을 도발해 대신 맞음"),
        ARCHER("궁수", "&a", 0.45, 0.20, 0.0, "주인 곁에서 멀리 있는 적을 쏨"),
        CASTER("술사", "&d", 0.40, 0.17, 0.0, "주인 곁에서 적 무리에 저주 폭발");
        final String label, color, desc;
        final double hp, atk, guard;

        Role(String label, String color, double hp, double atk, double guard, String desc) {
            this.label = label; this.color = color; this.hp = hp; this.atk = atk; this.guard = guard; this.desc = desc;
        }
    }

    enum Trait {
        VAMPIRE("흡혈", Material.REDSTONE, 1.0, 1.0, 0, "가한 피해의 20% 만큼 자기 체력 회복"),
        FURY("광폭", Material.BLAZE_POWDER, 0.8, 1.3, 0, "공격력 +30% · 체력 -20%"),
        IRON("철갑", Material.IRON_INGOT, 1.4, 0.9, 0.10, "체력 +40% · 받는 피해 -10% · 공격력 -10%"),
        SWIFT("신속", Material.FEATHER, 1.0, 1.0, 0, "이동 속도 +30% · 공격 간격 -25%");
        final String label, desc;
        final Material icon;
        final double hp, atk, guard;

        Trait(String label, Material icon, double hp, double atk, double guard, String desc) {
            this.label = label; this.icon = icon; this.hp = hp; this.atk = atk; this.guard = guard; this.desc = desc;
        }
    }

    private static final String[] GRADE = {"&f일반", "&9희귀", "&5영웅", "&6&l전설"};
    private static final double[] GRADE_MULT = {1.0, 1.3, 1.7, 2.3};
    private static final double[] GRADE_ODDS = {0.55, 0.28, 0.13, 0.04};
    private static final String[] STAT = {"체력", "공격", "속도"};
    private static final int MAX_LV = 10;
    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 22};

    static final class Minion {
        String mob, name;
        int grade;
        Role role;
        Trait trait;
        final int[] lv = new int[3];
        long downUntil;
        // 소환 중에만
        UUID entity;
        double hp, maxHp;
        long nextAct;
    }

    static final class Book {
        final Map<String, Integer> souls = new HashMap<>();
        long essence;
        final List<Minion> minions = new ArrayList<>();
        boolean summoned;
        long nextSummon, nextUpkeep;
    }

    private static NamespacedKey KEY;
    private final RpgCraft plugin;
    private final File file;
    private final YamlConfiguration data;
    private final Map<UUID, Book> books = new HashMap<>();
    private final Map<UUID, UUID> owners = new HashMap<>();   // 군단원 엔티티 → 주인
    private boolean dirty;
    private long tick;

    public NecromancyManager(RpgCraft plugin) {
        this.plugin = plugin;
        KEY = new NamespacedKey(plugin, "necro_minion");
        file = new File(plugin.getDataFolder(), "necromancy.yml");
        data = YamlConfiguration.loadConfiguration(file);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 10L);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> { if (dirty) save(); }, 1200L, 1200L);
        for (World w : Bukkit.getWorlds())   // 이전 실행에서 남은 군단원 정리
            for (Wolf e : w.getEntitiesByClass(Wolf.class)) if (e.getPersistentDataContainer().has(KEY, PersistentDataType.STRING)) e.remove();
    }

    /** 이 엔티티가 네크로맨서의 군단원인지 (전투 · 모델 코드에서 씀) */
    public static boolean isMinion(Entity e) {
        return KEY != null && e != null && e.getPersistentDataContainer().has(KEY, PersistentDataType.STRING);
    }

    /** 네크로맨서 단계 (아니면 0) */
    public static int tier(PlayerData d) {
        HiddenJobManager.Tier t = HiddenJobManager.of(d);
        return t != null && t.line().equals("C") ? t.tier() : 0;
    }

    private int tier(Player p) {
        return tier(plugin.data().get(p));
    }

    private double cfg(String k, double def) {
        return plugin.getConfig().getDouble("necromancer." + k, def);
    }

    private int slots(int tier) {
        List<Integer> l = plugin.getConfig().getIntegerList("necromancer.slots");
        int[] def = {3, 5, 8};
        int i = Math.max(0, Math.min(2, tier - 1));
        return Math.min(SLOTS.length, i < l.size() ? l.get(i) : def[i]);
    }

    private double successRate(int tier) {
        List<Double> l = plugin.getConfig().getDoubleList("necromancer.raise-success");
        double[] def = {0.5, 0.6, 0.7};
        int i = Math.max(0, Math.min(2, tier - 1));
        return i < l.size() ? l.get(i) : def[i];
    }

    // ------------------------------------------------------------------ 저장
    private Book book(UUID id) {
        return books.computeIfAbsent(id, k -> {
            Book b = new Book();
            ConfigurationSection s = data.getConfigurationSection("players." + k);
            if (s == null) return b;
            b.essence = s.getLong("essence");
            ConfigurationSection ss = s.getConfigurationSection("souls");
            if (ss != null) for (String m : ss.getKeys(false)) b.souls.put(m, ss.getInt(m));
            ConfigurationSection ms = s.getConfigurationSection("minions");
            if (ms != null) for (String i : ms.getKeys(false)) {
                ConfigurationSection x = ms.getConfigurationSection(i);
                if (x == null) continue;
                Minion m = new Minion();
                m.mob = x.getString("mob");
                m.name = x.getString("name", "군단원");
                m.grade = x.getInt("grade");
                try { m.role = Role.valueOf(x.getString("role", "WARRIOR")); } catch (IllegalArgumentException ex) { m.role = Role.WARRIOR; }
                try { m.trait = Trait.valueOf(x.getString("trait", "IRON")); } catch (IllegalArgumentException ex) { m.trait = Trait.IRON; }
                m.lv[0] = x.getInt("hp");
                m.lv[1] = x.getInt("atk");
                m.lv[2] = x.getInt("spd");
                m.downUntil = x.getLong("down");
                b.minions.add(m);
            }
            return b;
        });
    }

    private void write(UUID id) {
        Book b = books.get(id);
        if (b == null) return;
        String base = "players." + id;
        data.set(base, null);
        data.set(base + ".essence", b.essence);
        for (Map.Entry<String, Integer> en : b.souls.entrySet()) if (en.getValue() > 0) data.set(base + ".souls." + en.getKey(), en.getValue());
        for (int i = 0; i < b.minions.size(); i++) {
            Minion m = b.minions.get(i);
            String p = base + ".minions." + i;
            data.set(p + ".mob", m.mob);
            data.set(p + ".name", m.name);
            data.set(p + ".grade", m.grade);
            data.set(p + ".role", m.role.name());
            data.set(p + ".trait", m.trait.name());
            data.set(p + ".hp", m.lv[0]);
            data.set(p + ".atk", m.lv[1]);
            data.set(p + ".spd", m.lv[2]);
            data.set(p + ".down", m.downUntil);
        }
        dirty = true;
    }

    public void save() {
        for (UUID id : books.keySet()) write(id);
        try {
            data.save(file);
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().warning("necromancy.yml 저장 실패: " + ex.getMessage());
        }
    }

    public void shutdown() {
        for (UUID id : new ArrayList<>(books.keySet())) {
            Player p = Bukkit.getPlayer(id);
            dismiss(id, p, null);
        }
        save();
    }

    // ------------------------------------------------------------------ 능력치
    private static Role roleOf(CustomMobManager.MobDef d) {
        if (d == null) return Role.WARRIOR;
        String n = d.name == null ? "" : d.name;
        EntityType t = d.type;
        String hand = d.equipment.getOrDefault("hand", d.equipment.getOrDefault("HAND", "")).toUpperCase(Locale.ROOT);
        if (t == EntityType.WITCH || t == EntityType.EVOKER || t == EntityType.ILLUSIONER || t == EntityType.BLAZE || t == EntityType.GHAST
                || n.contains("마법") || n.contains("술사") || n.contains("사제") || n.contains("주술") || n.contains("마녀") || n.contains("리치")) return Role.CASTER;
        if (t == EntityType.PILLAGER || hand.contains("BOW") || n.contains("궁") || n.contains("명사수") || n.contains("사수")) return Role.ARCHER;
        if (d.scale >= 1.3 || t == EntityType.IRON_GOLEM || t == EntityType.RAVAGER || t == EntityType.HOGLIN || t == EntityType.ZOGLIN
                || n.contains("거인") || n.contains("골렘") || n.contains("거한") || n.contains("파괴수")) return Role.TANK;
        return Role.WARRIOR;
    }

    private static double typeHeight(EntityType t) {
        return switch (t) {
            case SPIDER, HOGLIN, ZOGLIN -> 1.0;
            case CAVE_SPIDER, SILVERFISH, ENDERMITE -> 0.5;
            case CREEPER -> 1.7;
            case ENDERMAN -> 2.9;
            case IRON_GOLEM -> 2.7;
            case RAVAGER -> 2.2;
            case WITHER_SKELETON -> 2.4;
            case SLIME, MAGMA_CUBE -> 1.0;
            case PHANTOM -> 0.6;
            default -> 1.9;
        };
    }

    private double tierMult(int tier) {
        return tier >= 3 ? 1.45 : tier == 2 ? 1.2 : 1.0;
    }

    private double maxHp(Player owner, Minion m) {
        double base = plugin.data().get(owner).stats.maxHp;
        return base * m.role.hp * GRADE_MULT[m.grade] * (1 + 0.10 * m.lv[0]) * m.trait.hp * tierMult(tier(owner));
    }

    private double attack(Player owner, Minion m) {
        var s = plugin.data().get(owner).stats;
        return Math.max(s.attack, s.magic) * m.role.atk * GRADE_MULT[m.grade] * (1 + 0.10 * m.lv[1]) * m.trait.atk * tierMult(tier(owner));
    }

    private long interval(Minion m) {
        long base = m.role == Role.CASTER ? 2500 : 1500;
        double f = (1 - 0.04 * m.lv[2]) * (m.trait == Trait.SWIFT ? 0.75 : 1);
        return (long) (base * f);
    }

    private String mobName(String id) {
        CustomMobManager.MobDef d = plugin.customMobs() == null ? null : plugin.customMobs().def(id, true);
        return d == null ? id : Text.strip(Text.c(d.name));
    }

    // ------------------------------------------------------------------ 영혼 · 정수 모으기
    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent e) {
        LivingEntity dead = e.getEntity();
        if (isMinion(dead)) { e.getDrops().clear(); e.setDroppedExp(0); return; }
        Player k = dead.getKiller();
        if (k == null || tier(k) == 0) return;
        Book b = book(k.getUniqueId());
        boolean boss = dead.getPersistentDataContainer().has(kr.rpgcraft.Keys.BOSS, PersistentDataType.STRING);
        CustomMobManager.MobDef d = plugin.customMobs() == null ? null : plugin.customMobs().of(dead);
        b.essence += boss ? 60 : d != null ? 3 : 1;
        if (d != null && !boss && ThreadLocalRandom.current().nextDouble() < cfg("soul-chance", 0.05)) {
            int n = b.souls.merge(d.id, 1, Integer::sum);
            Text.actionBar(k, "&5☠ 영혼 획득: &f" + mobName(d.id) + " &7(" + n + ")");
            k.playSound(k.getLocation(), Sound.PARTICLE_SOUL_ESCAPE, 1f, 0.7f);
            dead.getWorld().spawnParticle(Particle.SOUL, dead.getLocation().add(0, 1, 0), 12, 0.3, 0.5, 0.3, 0.03);
        }
        dirty = true;
    }

    // ------------------------------------------------------------------ 소환 · 해산
    private void summon(Player p) {
        int tr = tier(p);
        Book b = book(p.getUniqueId());
        if (tr == 0) return;
        long now = System.currentTimeMillis();
        if (b.summoned) { dismiss(p.getUniqueId(), p, "&7군단을 거두었습니다."); return; }
        if (now < b.nextSummon) { Text.msg(p, "&c군단을 다시 부르려면 " + ((b.nextSummon - now) / 1000 + 1) + "초 기다려야 합니다."); return; }
        long upkeep = upkeep(b, tr);
        if (upkeep == 0) { Text.msg(p, "&c부를 수 있는 군단원이 없습니다. &7(쓰러진 군단원은 부활이 필요합니다)"); return; }
        if (b.essence < upkeep) { Text.msg(p, "&c사령 정수가 부족합니다. &7(첫 1분 유지비 " + upkeep + " 필요)"); return; }
        b.essence -= upkeep;
        b.nextUpkeep = now + 60_000;
        b.summoned = true;
        int n = 0;
        for (int i = 0; i < b.minions.size() && i < slots(tr); i++) {
            Minion m = b.minions.get(i);
            if (m.downUntil > now) continue;
            double a = Math.PI * 2 * n++ / Math.max(1, b.minions.size());
            spawn(p, i, m, p.getLocation().add(Math.cos(a) * 2, 0.2, Math.sin(a) * 2));
        }
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_WITHER_SKELETON_AMBIENT, 1.2f, 0.6f);
        p.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, p.getLocation().add(0, 0.2, 0), 60, 2, 0.1, 2, 0.02);
        Text.msg(p, "&5☠ 군단 소환! &7(" + n + "마리, 1분마다 사령 정수 " + upkeep + " 소모)");
        dirty = true;
    }

    private long upkeep(Book b, int tr) {
        long now = System.currentTimeMillis(), sum = 0;
        double per = cfg("upkeep-per-minion", 2);
        for (int i = 0; i < b.minions.size() && i < slots(tr); i++) {
            Minion m = b.minions.get(i);
            if (m.downUntil <= now) sum += Math.round(per * (m.grade + 1));
        }
        return sum;
    }

    private void spawn(Player p, int idx, Minion m, Location at) {
        CustomMobManager.MobDef md = plugin.customMobs() == null ? null : plugin.customMobs().def(m.mob, true);
        Wolf w = p.getWorld().spawn(at, Wolf.class, x -> {
            x.setTamed(true);
            x.setOwner(p);
            x.setAdult();
            x.setSilent(true);
            x.setPersistent(false);
            x.setRemoveWhenFarAway(false);
            x.setCanPickupItems(false);
            x.setCollarColor(DyeColor.BLACK);
            x.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, p.getUniqueId() + ":" + idx);
            x.setCustomNameVisible(true);
        });
        AttributeInstance sp = w.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (sp != null) sp.setBaseValue(0.3 * (1 + 0.04 * m.lv[2]) * (m.trait == Trait.SWIFT ? 1.3 : 1));
        m.entity = w.getUniqueId();
        m.maxHp = maxHp(p, m);
        m.hp = m.maxHp;
        m.nextAct = 0;
        owners.put(w.getUniqueId(), p.getUniqueId());
        rename(w, m);
        if (plugin.mobModels() != null && md != null) {
            double h = typeHeight(md.type) * Math.max(1, Math.min(1.6, md.scale)) * cfg("model-scale", 0.9);
            plugin.mobModels().attach(w, m.mob, h, true);   // v5.10.11 언데드 색 (원래 몬스터와 구분)
        }
        w.getWorld().spawnParticle(Particle.SOUL, at.clone().add(0, 0.5, 0), 20, 0.4, 0.6, 0.4, 0.03);
    }

    private void rename(LivingEntity w, Minion m) {
        int bars = 10, fill = (int) Math.ceil(Math.max(0, m.hp) / Math.max(1, m.maxHp) * bars);
        w.setCustomName(Text.c("&5☠ " + m.role.color + m.name + " &a" + "|".repeat(fill) + "&8" + "|".repeat(bars - fill)));
    }

    /** 군단 해산 (주인 퇴장 · 사망 · 월드 이동 · 유지비 부족) */
    private void dismiss(UUID owner, Player p, String msg) {
        Book b = books.get(owner);
        if (b == null || !b.summoned) return;
        b.summoned = false;
        b.nextSummon = System.currentTimeMillis() + (long) (cfg("summon-cooldown", 20) * 1000);
        for (Minion m : b.minions) {
            if (m.entity == null) continue;
            Entity e = Bukkit.getEntity(m.entity);
            if (e != null) {
                e.getWorld().spawnParticle(Particle.SOUL, e.getLocation().add(0, 0.6, 0), 10, 0.3, 0.4, 0.3, 0.02);
                e.remove();
            }
            owners.remove(m.entity);
            m.entity = null;
        }
        if (p != null && msg != null) Text.msg(p, msg);
        dirty = true;
    }

    private Minion minion(Entity e) {
        if (!isMinion(e)) return null;
        String v = e.getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
        if (v == null) return null;
        String[] kv = v.split(":");
        try {
            Book b = books.get(UUID.fromString(kv[0]));
            if (b == null) return null;
            for (Minion m : b.minions) if (e.getUniqueId().equals(m.entity)) return m;
        } catch (IllegalArgumentException ignored) {
        }
        return null;
    }

    private void fall(Player owner, Minion m, LivingEntity w) {
        m.downUntil = System.currentTimeMillis() + (long) (cfg("revive-minutes", 5) * 60_000);
        m.entity = null;
        owners.remove(w.getUniqueId());
        w.getWorld().spawnParticle(Particle.SOUL, w.getLocation().add(0, 0.8, 0), 30, 0.4, 0.6, 0.4, 0.05);
        w.getWorld().playSound(w.getLocation(), Sound.ENTITY_SKELETON_DEATH, 1f, 0.6f);
        w.remove();
        if (owner != null) Text.msg(owner, "&c☠ 군단원 " + m.name + "&c이(가) 쓰러졌습니다. &7(" + (int) cfg("revive-minutes", 5) + "분 뒤 다시 부를 수 있음 · /군단 에서 즉시 부활)");
        dirty = true;
    }

    // ------------------------------------------------------------------ 전투
    /** 군단원이 맞음: 플레이어 · 다른 군단원 · 환경 피해는 막고, 몬스터에게 맞은 만큼만 자기 체력에서 깎음 */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        Entity victim = e.getEntity();
        if (isMinion(victim)) {
            e.setCancelled(true);
            Minion m = minion(victim);
            if (m == null) { victim.remove(); return; }
            Player owner = Bukkit.getPlayer(owners.getOrDefault(victim.getUniqueId(), new UUID(0, 0)));
            if (e.getCause() == EntityDamageEvent.DamageCause.VOID) { fall(owner, m, (LivingEntity) victim); return; }
            if (!(e instanceof EntityDamageByEntityEvent ev)) return;
            Entity src = ev.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Entity sh ? sh : ev.getDamager();
            if (!(src instanceof LivingEntity mob) || src instanceof Player || isMinion(src)) return;
            double dmg = plugin.combat().mobDamage(mob) * (ev.getDamager() instanceof Projectile ? 0.8 : 1);
            dmg *= 1 - Math.min(0.7, m.role.guard + m.trait.guard);
            m.hp -= dmg;
            plugin.combat().indicator((LivingEntity) victim, dmg, false);
            ((LivingEntity) victim).playEffect(EntityEffect.HURT);
            if (m.hp <= 0) fall(owner, m, (LivingEntity) victim);
            else rename((LivingEntity) victim, m);
            return;
        }
        if (e instanceof EntityDamageByEntityEvent ev && isMinion(ev.getDamager()) && victim instanceof LivingEntity target) {
            e.setCancelled(true);   // 늑대 이빨 대신 군단원 공격력으로
            Minion m = minion(ev.getDamager());
            Player owner = Bukkit.getPlayer(owners.getOrDefault(ev.getDamager().getUniqueId(), new UUID(0, 0)));
            if (m == null || owner == null || isMinion(target)) return;
            strike(owner, m, (LivingEntity) ev.getDamager(), target, attack(owner, m));
        }
    }

    private void strike(Player owner, Minion m, LivingEntity self, LivingEntity target, double dmg) {
        if (!plugin.combat().isEnemy(owner, target)) return;
        if (plugin.mobModels() != null) plugin.mobModels().attackPose(self);
        plugin.combat().dealSkillDamage(owner, target, dmg, false);   // 군단원 공격은 주인 치명타 없음
        if (m.trait == Trait.VAMPIRE) { m.hp = Math.min(m.maxHp, m.hp + dmg * 0.2); rename(self, m); }
        // 맞은 적은 군단원에게 달려들기도 함 (수호는 항상) — 군단원도 맞고 쓰러질 수 있음
        if (target instanceof Mob mob && !(target instanceof Player) && target.isValid() && !target.isDead()
                && ThreadLocalRandom.current().nextDouble() < (m.role == Role.TANK ? 1.0 : 0.35)) mob.setTarget(self);
    }

    /** 주인과 파티 · 누구든 군단원에게 먹이를 주거나 앉히지 못하게 */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEntityEvent e) {
        if (isMinion(e.getRightClicked())) e.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        dismiss(e.getPlayer().getUniqueId(), null, null);
        write(e.getPlayer().getUniqueId());
        books.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent e) {
        dismiss(e.getPlayer().getUniqueId(), e.getPlayer(), "&7월드를 옮겨 군단이 흩어졌습니다.");
    }

    @EventHandler
    public void onOwnerDeath(org.bukkit.event.entity.PlayerDeathEvent e) {
        dismiss(e.getEntity().getUniqueId(), e.getEntity(), "&7주인이 쓰러져 군단이 흩어졌습니다.");
    }

    private boolean foe(Player owner, Entity e) {
        return e instanceof LivingEntity le && !(e instanceof Player) && !isMinion(e) && !e.isDead()
                && (le instanceof Enemy || plugin.mobs().tracked(le)) && plugin.combat().isEnemy(owner, le);
    }

    private LivingEntity nearestFoe(Player owner, Location from, double r) {
        LivingEntity best = null;
        double bd = r * r;
        for (Entity e : from.getWorld().getNearbyEntities(from, r, 6, r)) {
            if (!foe(owner, e)) continue;
            double d = e.getLocation().distanceSquared(from);
            if (d < bd) { bd = d; best = (LivingEntity) e; }
        }
        return best;
    }

    private void tick() {
        tick++;
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Book> en : new ArrayList<>(books.entrySet())) {
            Book b = en.getValue();
            if (!b.summoned) continue;
            Player p = Bukkit.getPlayer(en.getKey());
            if (p == null || !p.isOnline()) { dismiss(en.getKey(), null, null); continue; }
            int tr = tier(p);
            if (tr == 0 || p.isDead() || p.getGameMode() == GameMode.SPECTATOR) { dismiss(en.getKey(), p, null); continue; }
            if (now >= b.nextUpkeep) {
                long cost = upkeep(b, tr);
                if (b.essence < cost) { dismiss(en.getKey(), p, "&c사령 정수가 떨어져 군단이 흩어졌습니다."); continue; }
                b.essence -= cost;
                b.nextUpkeep = now + 60_000;
                dirty = true;
            }
            boolean any = false;
            for (Minion m : b.minions) {
                if (m.entity == null) continue;
                Entity e = Bukkit.getEntity(m.entity);
                if (!(e instanceof Wolf w) || !w.isValid()) { owners.remove(m.entity); m.entity = null; continue; }
                any = true;
                if (!w.getWorld().equals(p.getWorld()) || w.getLocation().distanceSquared(p.getLocation()) > 28 * 28) {
                    Location to = p.getLocation().add(ThreadLocalRandom.current().nextDouble(-2, 2), 0.2, ThreadLocalRandom.current().nextDouble(-2, 2));
                    if (plugin.mobModels() == null || !plugin.mobModels().teleport(w, to)) w.teleport(to);
                    w.setTarget(null);
                    continue;
                }
                w.setSitting(false);
                double newMax = maxHp(p, m);   // 주인 장비가 바뀌면 따라감
                if (Math.abs(newMax - m.maxHp) > 1) { m.hp = m.hp / Math.max(1, m.maxHp) * newMax; m.maxHp = newMax; }
                if (tick % 4 == 0 && m.hp < m.maxHp) m.hp = Math.min(m.maxHp, m.hp + m.maxHp * 0.01);   // 2초마다 1% 회복
                rename(w, m);
                if (tick % 4 == 0) {   // v5.10.11 언데드 표시: 발밑 영혼불 (원래 몬스터와 구분)
                    Location feet = w.getLocation().add(0, 0.1, 0);
                    w.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, feet, 3, 0.3, 0.05, 0.3, 0.005);
                    w.getWorld().spawnParticle(Particle.SOUL, feet.add(0, 0.6, 0), 1, 0.25, 0.4, 0.25, 0.01);
                }
                if (m.role == Role.WARRIOR || m.role == Role.TANK) {
                    LivingEntity t = w.getTarget();
                    if (t == null || !t.isValid() || t.isDead() || t.getLocation().distanceSquared(p.getLocation()) > 20 * 20) {
                        LivingEntity f = nearestFoe(p, p.getLocation(), 12);
                        w.setTarget(f);
                    }
                    if (m.role == Role.TANK && now >= m.nextAct) {   // 도발: 주인을 노리는 적을 끌어옴
                        m.nextAct = now + 3000;
                        int n = 0;
                        for (Entity x : w.getNearbyEntities(7, 4, 7))
                            if (x instanceof Mob mob && foe(p, x) && p.equals(mob.getTarget())) { mob.setTarget(w); n++; }
                        if (n > 0) w.getWorld().spawnParticle(Particle.VILLAGER_ANGRY, w.getLocation().add(0, 1.6, 0), 3, 0.3, 0.2, 0.3, 0);
                    }
                    continue;
                }
                w.setTarget(null);   // 궁수 · 술사는 주인 곁에 머물며 원거리 공격
                if (now < m.nextAct) continue;
                LivingEntity f = nearestFoe(p, w.getLocation(), m.role == Role.ARCHER ? 16 : 12);
                if (f == null) continue;
                m.nextAct = now + interval(m);
                Location from = w.getLocation().add(0, 1.2, 0), to = f.getLocation().add(0, f.getHeight() * 0.5, 0);
                double atk = attack(p, m);
                if (plugin.mobModels() != null) plugin.mobModels().attackPose(w);
                if (m.role == Role.ARCHER) {
                    line(from, to, Color.fromRGB(0x9CFFB0));
                    w.getWorld().playSound(from, Sound.ENTITY_ARROW_SHOOT, 0.8f, 0.8f);
                    strike(p, m, w, f, atk);
                } else {
                    line(from, to, Color.fromRGB(0xB060FF));
                    w.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, to, 20, 1.2, 0.4, 1.2, 0.02);
                    w.getWorld().playSound(to, Sound.ENTITY_EVOKER_CAST_SPELL, 0.8f, 0.7f);
                    for (Entity x : f.getWorld().getNearbyEntities(f.getLocation(), 3, 2, 3))
                        if (foe(p, x)) strike(p, m, w, (LivingEntity) x, atk);
                }
            }
            if (!any) dismiss(en.getKey(), p, "&7군단원이 모두 쓰러져 군단이 흩어졌습니다.");
        }
    }

    private static void line(Location a, Location b, Color c) {
        org.bukkit.util.Vector d = b.toVector().subtract(a.toVector());
        double len = d.length();
        if (len < 0.1) return;
        d.normalize().multiply(0.6);
        Location p = a.clone();
        Particle.DustOptions o = new Particle.DustOptions(c, 1.0f);
        for (double s = 0; s < len; s += 0.6) {
            a.getWorld().spawnParticle(Particle.REDSTONE, p, 1, 0, 0, 0, 0, o);
            p.add(d);
        }
    }

    /** 직업 스킬(Q)을 쓰면 군단 체력 25% 회복 */
    public void rally(Player p) {
        Book b = books.get(p.getUniqueId());
        if (b == null || !b.summoned) return;
        for (Minion m : b.minions) {
            if (m.entity == null || !(Bukkit.getEntity(m.entity) instanceof LivingEntity w)) continue;
            m.hp = Math.min(m.maxHp, m.hp + m.maxHp * 0.25);
            rename(w, m);
            w.getWorld().spawnParticle(Particle.SOUL, w.getLocation().add(0, 1, 0), 8, 0.3, 0.4, 0.3, 0.02);
        }
    }

    // ------------------------------------------------------------------ GUI (/군단)
    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player p)) return true;
        if (tier(p) == 0) { Text.msg(p, "&8…"); return true; }
        if (a.length > 0 && (a[0].equals("소환") || a[0].equals("summon") || a[0].equals("해산"))) { summon(p); return true; }
        open(p);
        return true;
    }

    public void open(Player p) {
        int tr = tier(p);
        if (tr == 0) return;
        Book b = book(p.getUniqueId());
        long now = System.currentTimeMillis();
        int slots = slots(tr);
        Gui g = new Gui(6, "&5☠ 사령 군단") {
        };
        int soulTotal = b.souls.values().stream().mapToInt(Integer::intValue).sum();
        g.set(4, Gui.button(Material.WITHER_SKELETON_SKULL, "&5&l" + HiddenJobManager.of(plugin.data().get(p)).label(),
                "&7사령 정수: &d" + b.essence, "&7모은 영혼: &f" + soulTotal + "개 &8(" + b.souls.size() + "종)",
                "&7군단 자리: &f" + Math.min(slots, b.minions.size()) + " / " + slots,
                "&7소환 유지비: &d1분마다 " + upkeep(b, tr) + " 정수", "",
                "&8네크로맨서가 커스텀 몬스터를 잡으면 가끔 영혼이 남는다.",
                "&8무엇을 잡든 사령 정수가 모인다 (보스는 많이)."), null);
        for (int i = 0; i < SLOTS.length; i++) {
            if (i >= slots) { g.set(SLOTS[i], Gui.button(Material.BLACK_STAINED_GLASS_PANE, "&8잠긴 자리", "&7더 높은 단계의 네크로맨서만"), null); continue; }
            if (i >= b.minions.size()) { g.set(SLOTS[i], Gui.button(Material.GRAY_DYE, "&7빈 자리"), null); continue; }
            Minion m = b.minions.get(i);
            int idx = i;
            boolean down = m.downUntil > now;
            List<String> lore = new ArrayList<>(minionLore(p, m));
            lore.add("");
            if (down) lore.add("&c쓰러짐 &7(" + ((m.downUntil - now) / 60_000 + 1) + "분 뒤 부를 수 있음)");
            else if (m.entity != null) lore.add("&a소환 중");
            lore.add("&e▶ 클릭: 강화 · 부활 · 해방");
            g.set(SLOTS[i], Gui.button(down ? Material.BONE : egg(m.mob), GRADE[m.grade] + " " + m.role.color + m.name, lore.toArray(new String[0])), e -> openMinion(p, idx));
        }
        boolean full = b.minions.size() >= slots;
        g.set(31, Gui.button(full ? Material.BARRIER : Material.SOUL_LANTERN, full ? "&7군단 자리가 가득 찼습니다" : "&d&l새 군단원 일으키기",
                "&7영혼 " + (int) cfg("souls-per-raise", 12) + "개 + 사령 정수 " + (long) cfg("essence-per-raise", 300),
                "&7성공 확률 &f" + Math.round(successRate(tr) * 100) + "% &8(실패하면 재료가 사라짐)",
                "&7등급: 일반 55% · 희귀 28% · 영웅 13% · 전설 4%", full ? "" : "&e▶ 클릭하여 영혼 고르기"), e -> {
            if (!full) openSouls(p);
        });
        g.set(49, Gui.button(b.summoned ? Material.SOUL_CAMPFIRE : Material.SOUL_TORCH, b.summoned ? "&c군단 거두기" : "&5&l군단 소환",
                b.summoned ? "&7불러낸 군단원을 모두 거둠" : "&7살아 있는 군단원을 모두 불러냄", "&7유지비: 1분마다 정수 " + upkeep(b, tr),
                "&8(/군단 소환 으로도 부르거나 거둘 수 있음)"), e -> { p.closeInventory(); summon(p); });
        g.set(45, Gui.button(Material.BOOK, "&e군단 안내",
                "&c전사&7: 적에게 달려들어 벰", "&9수호&7: 체력이 높고 주인을 노리는 적을 도발", "&a궁수&7: 주인 곁에서 멀리 쏨",
                "&d술사&7: 주인 곁에서 범위 저주", "", "&7군단원 능력치는 주인의 공격력 · 체력을 따라감",
                "&7직업 스킬(Q)을 쓰면 군단 체력 25% 회복", "&7쓰러진 군단원은 " + (int) cfg("revive-minutes", 5) + "분 동안 부를 수 없음"), null);
        g.fill(0, 53);
        g.open(p);
    }

    private List<String> minionLore(Player p, Minion m) {
        List<String> l = new ArrayList<>();
        l.add("&7영혼: &f" + mobName(m.mob) + " &8| " + m.role.color + m.role.label + " &8| &7각인: &f" + m.trait.label);
        l.add("&7체력 &a" + Text.num(maxHp(p, m)) + " &8| &7공격 &c" + Text.num(attack(p, m)));
        l.add("&7강화: 체력 +" + m.lv[0] + " · 공격 +" + m.lv[1] + " · 속도 +" + m.lv[2]);
        return l;
    }

    private Material egg(String mob) {
        CustomMobManager.MobDef d = plugin.customMobs() == null ? null : plugin.customMobs().def(mob, true);
        Material m = d == null ? null : Material.matchMaterial(d.type.name() + "_SPAWN_EGG");
        return m == null ? Material.SKELETON_SKULL : m;
    }

    private void openSouls(Player p) {
        Book b = book(p.getUniqueId());
        int need = (int) cfg("souls-per-raise", 12);
        Gui g = new Gui(6, "&5영혼 고르기") {
        };
        List<Map.Entry<String, Integer>> list = new ArrayList<>(b.souls.entrySet());
        list.removeIf(x -> x.getValue() <= 0 || plugin.customMobs() == null || plugin.customMobs().def(x.getKey(), true) == null);
        list.sort((x, y) -> Integer.compare(y.getValue(), x.getValue()));
        if (list.isEmpty()) g.set(22, Gui.button(Material.GRAY_DYE, "&7모은 영혼이 없습니다", "&7커스텀 몬스터를 잡으면 가끔 영혼이 남습니다"), null);
        for (int i = 0; i < list.size() && i < 45; i++) {
            String id = list.get(i).getKey();
            int n = list.get(i).getValue();
            Role r = roleOf(plugin.customMobs().def(id, true));
            boolean ok = n >= need;
            g.set(i, Gui.button(egg(id), (ok ? "&d" : "&7") + mobName(id), "&7영혼 " + (ok ? "&a" : "&c") + n + "&7/" + need,
                    "&7역할: " + r.color + r.label + " &8- " + r.desc, ok ? "&e▶ 클릭하여 각인 고르기" : "&8영혼이 부족합니다"), e -> {
                if (ok) openTraits(p, id);
            });
        }
        g.fill(0, 53);
        g.open(p);
    }

    private void openTraits(Player p, String mob) {
        Gui g = new Gui(3, "&5각인 고르기: " + mobName(mob)) {
        };
        int[] at = {10, 12, 14, 16};
        for (int i = 0; i < Trait.values().length; i++) {
            Trait t = Trait.values()[i];
            g.set(at[i], Gui.button(t.icon, "&d&l" + t.label, "&7" + t.desc, "", "&e▶ 클릭하여 의식 시작"), e -> raise(p, mob, t));
        }
        g.fill(0, 26);
        g.open(p);
    }

    private void raise(Player p, String mob, Trait trait) {
        int tr = tier(p);
        Book b = book(p.getUniqueId());
        int need = (int) cfg("souls-per-raise", 12);
        long cost = (long) cfg("essence-per-raise", 300);
        p.closeInventory();
        if (tr == 0 || b.minions.size() >= slots(tr)) return;
        if (b.souls.getOrDefault(mob, 0) < need || b.essence < cost) { Text.msg(p, "&c재료가 부족합니다."); return; }
        b.souls.merge(mob, -need, Integer::sum);
        b.essence -= cost;
        dirty = true;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1f, 0.5f);
        if (r.nextDouble() >= successRate(tr)) {
            p.getWorld().spawnParticle(Particle.SMOKE_LARGE, p.getLocation().add(0, 1, 0), 30, 0.5, 0.6, 0.5, 0.02);
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SKELETON_DEATH, 1f, 0.6f);
            Text.msg(p, "&8☠ 의식 실패… 영혼이 흩어졌습니다.");
            return;
        }
        double roll = r.nextDouble();
        int grade = 0;
        double acc = 0;
        for (int g = GRADE_ODDS.length - 1; g >= 0; g--) { acc += GRADE_ODDS[g]; if (roll < acc) { grade = g; break; } }
        Minion m = new Minion();
        m.mob = mob;
        m.grade = grade;
        m.trait = trait;
        m.role = roleOf(plugin.customMobs().def(mob, true));
        m.name = trait.label + "의 " + mobName(mob);
        b.minions.add(m);
        p.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, p.getLocation().add(0, 0.2, 0), 80, 1.5, 0.1, 1.5, 0.03);
        p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.6f, 1.6f);
        Text.msg(p, "&5☠ 의식 성공! " + GRADE[grade] + " &f" + m.name + " &7(" + m.role.color + m.role.label + "&7)이(가) 군단에 들어왔습니다.");
        if (grade == 3) Text.announce(Text.PREFIX + Text.c("&5" + Text.name(p) + "&f님이 &6&l전설 &5군단원 &f" + m.name + "&f을(를) 일으켰습니다!"));
        open(p);
    }

    private void openMinion(Player p, int idx) {
        Book b = book(p.getUniqueId());
        if (idx >= b.minions.size()) return;
        Minion m = b.minions.get(idx);
        long now = System.currentTimeMillis();
        Gui g = new Gui(3, "&5" + Text.strip(Text.c(m.name))) {
        };
        List<String> info = new ArrayList<>(minionLore(p, m));
        info.add(0, GRADE[m.grade] + " " + m.role.color + m.role.label + " &8- " + m.role.desc);
        info.add("&7각인 효과: &f" + m.trait.desc);
        g.set(4, Gui.button(egg(m.mob), "&5&l" + m.name, info.toArray(new String[0])), null);
        Material[] icons = {Material.GOLDEN_APPLE, Material.IRON_SWORD, Material.SUGAR};
        for (int s = 0; s < 3; s++) {
            int st = s, lv = m.lv[s];
            long cost = (long) (cfg("upgrade-essence", 150) * (lv + 1));
            int souls = (lv + 1) * 2;
            int rate = Math.max(20, 100 - 8 * lv);
            boolean max = lv >= MAX_LV;
            g.set(11 + s * 2, Gui.button(icons[s], "&e" + STAT[s] + " 강화 &7(+" + lv + ")",
                    max ? "&6최대 강화" : "&7사령 정수 " + cost + " &8(보유 " + b.essence + ")",
                    max ? "" : "&7" + mobName(m.mob) + " 영혼 " + souls + " &8(보유 " + b.souls.getOrDefault(m.mob, 0) + ")",
                    max ? "" : "&7성공 확률 " + rate + "% &8(실패하면 재료만 사라짐)",
                    max ? "" : "&e▶ 클릭하여 강화"), e -> {
                if (max) return;
                if (b.essence < cost || b.souls.getOrDefault(m.mob, 0) < souls) { Text.msg(p, "&c재료가 부족합니다."); return; }
                b.essence -= cost;
                b.souls.merge(m.mob, -souls, Integer::sum);
                dirty = true;
                if (ThreadLocalRandom.current().nextInt(100) < rate) {
                    m.lv[st]++;
                    p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.8f, 1.2f);
                    Text.msg(p, "&a" + m.name + " " + STAT[st] + " 강화 성공! &7(+" + m.lv[st] + ")");
                } else {
                    p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_DESTROY, 0.8f, 0.8f);
                    Text.msg(p, "&c강화 실패… 재료가 사라졌습니다.");
                }
                openMinion(p, idx);
            });
        }
        boolean down = m.downUntil > now;
        long revive = (long) (cfg("revive-essence", 100) * (m.grade + 1));
        g.set(22, Gui.button(down ? Material.TOTEM_OF_UNDYING : Material.GRAY_DYE, down ? "&a즉시 부활" : "&7쓰러지지 않음",
                down ? "&7사령 정수 " + revive + " &8(보유 " + b.essence + ")" : "", down ? "&e▶ 클릭하여 부활" : ""), e -> {
            if (!down) return;
            if (b.essence < revive) { Text.msg(p, "&c사령 정수가 부족합니다."); return; }
            b.essence -= revive;
            m.downUntil = 0;
            dirty = true;
            p.playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 0.6f, 1.2f);
            Text.msg(p, "&a" + m.name + " 부활!");
            if (b.summoned && m.entity == null) spawn(p, idx, m, p.getLocation().add(1, 0.2, 1));
            openMinion(p, idx);
        });
        g.set(26, Gui.button(Material.LAVA_BUCKET, "&c해방", "&7이 군단원을 영원히 놓아줌 (되돌릴 수 없음)", "&c▶ 쉬프트 + 우클릭"), e -> {
            if (!e.isShiftClick() || !e.isRightClick()) return;
            if (m.entity != null) {
                Entity en = Bukkit.getEntity(m.entity);
                if (en != null) en.remove();
                owners.remove(m.entity);
                m.entity = null;
            }
            b.minions.remove(m);
            renumber(p, b);
            dirty = true;
            Text.msg(p, "&7" + m.name + "을(를) 놓아주었습니다.");
            open(p);
        });
        g.fill(0, 26);
        g.open(p);
    }

    /** 해방 뒤 칸 번호가 바뀌므로 소환 중인 군단원 표식도 새로 */
    private void renumber(Player p, Book b) {
        for (int i = 0; i < b.minions.size(); i++) {
            Minion m = b.minions.get(i);
            if (m.entity != null && Bukkit.getEntity(m.entity) instanceof LivingEntity le)
                le.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, p.getUniqueId() + ":" + i);
        }
    }

    /** 관리자: 정수 · 영혼 지급 (시험용) */
    public void grant(Player p, long essence, String mob, int souls) {
        Book b = book(p.getUniqueId());
        b.essence += essence;
        if (mob != null && souls > 0) b.souls.merge(mob, souls, Integer::sum);
        dirty = true;
    }
}
