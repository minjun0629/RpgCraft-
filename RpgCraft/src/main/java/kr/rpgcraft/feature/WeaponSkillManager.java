package kr.rpgcraft.feature;

import kr.rpgcraft.Keys;
import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.data.PlayerData;
import kr.rpgcraft.item.Category;
import kr.rpgcraft.item.ItemData;
import kr.rpgcraft.item.ItemTemplate;
import kr.rpgcraft.item.WeaponClass;
import kr.rpgcraft.stat.StatSnapshot;
import kr.rpgcraft.util.Fx;
import kr.rpgcraft.util.Vfx;
import kr.rpgcraft.util.Text;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * 무기 스킬.
 *  좌클릭 = 평타 스킬 : 근접 무기는 3타(검성 2타)마다 무기별 추가 기술 발동, 활은 좌클릭 즉시 속사
 *  우클릭 = 강공격    : 무기별 쿨타임 스킬
 *  활은 당겨서 조준하는 방식을 없애고, 좌클릭 속사 / 우클릭 폭풍 화살 스킬로 쏜다 (화살 불필요)
 *  위력 = 공격력 × 배율 + 마력 × 배율 (힘 → 마력)
 *  사신수 무기는 기존 전용 우클릭 스킬을 유지한다.
 */
public class WeaponSkillManager implements Listener {
    private final RpgCraft plugin;
    private final Map<UUID, Integer> combo = new HashMap<>();
    private final Set<UUID> inSkill = new HashSet<>();

    public WeaponSkillManager(RpgCraft plugin) {
        this.plugin = plugin;
    }

    private enum Kind { SWORD, DAGGER, AXE, SHIELD, SPEAR, CLUB, BOW, STAFF }

    private Kind kind(Player p) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        ItemTemplate t = ItemData.template(hand);
        if (t == null) return null;
        if (t.category == Category.BOW) return Kind.BOW;
        if (t.category != Category.WEAPON) return null;
        if (t.id.contains("staff")) return Kind.STAFF;   // 지팡이 = 마법사 (원거리)
        WeaponClass wc = ItemData.weaponClass(hand);
        if (wc == null) return Kind.CLUB;
        return switch (wc) {
            case SWORD -> Kind.SWORD;
            case DAGGER -> Kind.DAGGER;
            case AXE -> Kind.AXE;
            case SHIELD -> Kind.SHIELD;
            case SPEAR -> Kind.SPEAR;
            default -> Kind.CLUB;
        };
    }

    private double power(Player p, double atkMult, double magicMult) {
        StatSnapshot s = plugin.data().get(p).stats;
        return s.attack * atkMult + s.magic * magicMult;
    }

    public double cooldownMultPublic(Player p) {
        return cdMult(p);
    }

    private double cdMult(Player p) {
        double j = plugin.jobs().isThird(p, JobManager.Third.STORM_ARCHER) ? 0.6 : plugin.jobs().is(p, JobManager.Sub.GALE) ? 0.7 : 1;
        if (plugin.jobs().isThird(p, JobManager.Third.ELEMENT_LORD)) j = Math.min(j, 0.6);   // 원소의 군주 -40%
        else if (plugin.jobs().is(p, JobManager.Sub.ELEMENTALIST)) j = Math.min(j, 0.75);   // 원소술사 -25%
        return j * (plugin.legendary() == null ? 1 : plugin.legendary().cooldownMult(p));
    }

    private boolean gradeAtLeast(Player p, kr.rpgcraft.item.Grade g) {
        kr.rpgcraft.item.Grade have = ItemData.grade(p.getInventory().getItemInMainHand());
        return have != null && have.ordinal() >= g.ordinal();
    }

    private boolean cd(Player p, String key, double sec) {
        PlayerData d = plugin.data().get(p);
        if (d.onCooldown(key)) {
            Text.actionBar(p, "&7재사용 대기 &c" + String.format("%.1f", d.remaining(key) / 1000.0) + "초");
            return false;
        }
        d.cooldown(key, (long) (sec * 1000 * cdMult(p)));
        return true;
    }

    // ------------------------------------------------------------------ 추가 스킬 (희귀 이상)
    private void extra(Player p, Kind k, boolean left) {
        PlayerData d = plugin.data().get(p);
        if (!d.stats.weaponOk) return;
        ItemTemplate t = ItemData.template(p.getInventory().getItemInMainHand());
        SkillBook.Set3 set = plugin.skillBook().of(t);
        if (set == null) return;
        SkillBook.SkillDef def = left ? set.shiftLeft() : set.shiftRight();
        if (!cd(p, left ? "extra_l" : "extra_r", def.cd())) return;
        double dmg = (k == Kind.BOW ? d.stats.ranged + d.stats.magic * 0.3 : k == Kind.STAFF ? d.stats.magic + d.stats.attack * 0.3 : d.stats.attack + d.stats.magic * 0.4) * def.power();
        plugin.skillBook().cast(p, def, dmg, k == Kind.BOW, k == Kind.STAFF, this::hit);
        Text.actionBar(p, "&b" + def.name());
    }

    // ------------------------------------------------------------------ 궁극기: 쉬프트 두 번 (레전드 등급 이상 무기)
    private final Map<UUID, Long> lastSneak = new HashMap<>();

    @EventHandler
    public void onSneak(org.bukkit.event.player.PlayerToggleSneakEvent e) {
        if (!e.isSneaking()) return;
        Player p = e.getPlayer();
        long now = System.currentTimeMillis();
        Long last = lastSneak.put(p.getUniqueId(), now);
        if (last == null || now - last > 350 || kind(p) == null || !gradeAtLeast(p, kr.rpgcraft.item.Grade.LEGEND)) return;
        lastSneak.remove(p.getUniqueId());
        if (!plugin.data().get(p).stats.weaponOk || !cd(p, "ultimate", plugin.getConfig().getDouble("weapon-skills.ultimate-cd", 60))) return;
        ultimate(p);
    }

    private void ultimate(Player p) {
        World w = p.getWorld();
        String id = ItemData.id(p.getInventory().getItemInMainHand());
        Color col = id == null ? Color.WHITE : id.contains("qinglong") ? Color.fromRGB(0x6BD8FF) : id.contains("baihu") ? Color.fromRGB(0xF4F4F4)
                : id.contains("zhuque") ? Color.fromRGB(0xFF7A1F) : id.contains("xuanwu") ? Color.fromRGB(0x2FBF71) : Color.fromRGB(0xE080FF);
        p.sendTitle("", Text.c("&6&l궁극기"), 0, 15, 5);
        w.playSound(p.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 0.8f, 1.3f);
        Fx.circle(p.getLocation().add(0, 0.1, 0), 3, 24, col, 1.4f);
        ultimateChargeFx(p, col);   // (연출) 기 모으기 1초
        if (id != null && id.contains("xuanwu")) { // 현무: 수호 결계
            plugin.health().healPercent(p, 30);
            plugin.data().get(p).invulnUntil = System.currentTimeMillis() + 3000;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            double pw = power(p, 5.0, 2.0);
            for (LivingEntity le : enemiesNear(p, p.getLocation(), 7)) {
                hit(p, le, pw);
                le.setVelocity(new Vector(0, 0.8, 0));
                if (id != null && id.contains("qinglong")) w.strikeLightningEffect(le.getLocation());
                if (id != null && id.contains("zhuque")) le.setFireTicks(80);
            }
            Fx.shockwave(plugin, p.getLocation(), 7, col);
            Vfx.burst(p.getLocation().add(0, 1.2, 0), 7, col);
            ultimateBlastFx(p, col);   // (연출) 빛기둥 · 3겹 충격파 · 하늘로 솟는 광선
            w.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.7f);
        }, 20L);
    }

    // ------------------------------------------------------------------ 연출 전용 (v5.1.6)
    /** 회전 베기: 몸 둘레를 한 바퀴 도는 참격 6장 */
    private void spinFx(Player p, Color c, double r) {
        for (int i = 0; i < 6; i++) {
            int ii = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline()) return;
                double a = ii * Math.PI / 3;
                Vector f = new Vector(Math.cos(a), 0, Math.sin(a));
                Vfx.slash(p.getLocation().add(0, 1, 0).add(f.clone().multiply(r * 0.6)), f, r * 1.3, ii % 2 == 0 ? 20 : -20, ii % 3 == 0 ? Color.WHITE : c);
            }, i / 2);
        }
        Vfx.ring(p.getLocation(), r, c);
    }

    /** 궁극기 기 모으기: 발밑 마법진 3겹 + 빨려드는 빛 + 나선 */
    private void ultimateChargeFx(Player p, Color col) {
        Fx.helix(plugin, p, 3.2, 1.2, 20, col, Color.WHITE);
        for (int t = 0; t < 20; t += 4) {
            int tt = t;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline()) return;
                Vfx.ring(p.getLocation(), 4.5 - tt * 0.15, tt % 8 == 0 ? col : Color.WHITE);
                p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 12, 2.2, 1.2, 2.2, -0.12);
            }, t);
        }
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.3f);
    }

    /** 궁극기 폭발: 8방향 빛기둥 · 3겹 충격파 · 하늘로 솟는 광선 · 섬광 */
    private void ultimateBlastFx(Player p, Color col) {
        Location o = p.getLocation();
        World w = p.getWorld();
        w.spawnParticle(Particle.FLASH, o.clone().add(0, 1, 0), 1);
        Vfx.beam(o.clone(), o.clone().add(0, 20, 0), 3.0, col);
        Vfx.beam(o.clone(), o.clone().add(0, 20, 0), 1.2, Color.WHITE);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            Location pl = o.clone().add(Math.cos(a) * 5, 0, Math.sin(a) * 5);
            Bukkit.getScheduler().runTaskLater(plugin, () -> { Vfx.beam(pl, pl.clone().add(0, 7, 0), 0.9, col); Vfx.burst(pl.clone().add(0, 1, 0), 2, col); }, i % 3);
        }
        for (int k = 1; k <= 3; k++) {
            int kk = k;
            Bukkit.getScheduler().runTaskLater(plugin, () -> Vfx.ring(o, 3.0 * kk, kk % 2 == 0 ? Color.WHITE : col), k * 2L);
        }
        for (int k = 0; k < 4; k++) Vfx.slash(o.clone().add(0, 1.2, 0), p.getLocation().getDirection().setY(0), 9, k * 45, k % 2 == 0 ? col : Color.WHITE);
        w.spawnParticle(Particle.EXPLOSION_HUGE, o, 2, 1.5, 0.3, 1.5);
        w.playSound(o, Sound.ITEM_TRIDENT_THUNDER, 1f, 0.8f);
    }

    private long strongCd(Kind k) {
        return (long) (plugin.getConfig().getDouble("weapon-skills.cooldown." + k.name().toLowerCase(Locale.ROOT),
                switch (k) {
                    case DAGGER -> 7;
                    case AXE -> 9;
                    case SHIELD -> 8;
                    case BOW -> 6;
                    default -> 6;
                }) * 1000);
    }

    /** HUD 표시용: 강공격 남은 초 (-1 = 무기 없음) */
    public int strongCooldown(Player p) {
        Kind k = kind(p);
        if (k == null) return -1;
        return (int) Math.ceil(plugin.data().get(p).remaining("strong") / 1000.0);
    }

    // ------------------------------------------------------------------ 입력
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !plugin.getConfig().getBoolean("weapon-skills.enabled", true)) return;
        Player p = e.getPlayer();
        Kind k = kind(p);
        if (k == null) return;
        ItemTemplate t = ItemData.template(p.getInventory().getItemInMainHand());
        Action a = e.getAction();
        boolean right = a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK;
        boolean left = a == Action.LEFT_CLICK_AIR || a == Action.LEFT_CLICK_BLOCK;
        // 희귀 등급 이상: Shift+좌클릭 / Shift+우클릭 추가 스킬 (방패는 Shift+우클릭 = 막기 유지)
        if (p.isSneaking() && gradeAtLeast(p, kr.rpgcraft.item.Grade.RARE)) {
            if (a == Action.LEFT_CLICK_AIR) { extra(p, k, true); return; }
            if (right && t.skill == null && k != Kind.SHIELD && !(a == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null && e.getClickedBlock().getType().isInteractable())) {
                e.setUseItemInHand(Event.Result.DENY);
                e.setCancelled(true);
                extra(p, k, false);
                return;
            }
        }
        if (left && k == Kind.STAFF && !p.isSneaking()) {   // 지팡이 좌클릭 = 마력탄
            magicBolt(p);
            return;
        }
        if (left && a == Action.LEFT_CLICK_AIR && k != Kind.BOW && !p.isSneaking()) { // 허공 좌클릭 = 참격 (전방 범위 공격)
            slash(p, k);
            return;
        }
        if (k == Kind.BOW) {
            if (right) {
                e.setUseItemInHand(Event.Result.DENY); // 활 당기기(차징·조준) 비활성화
                e.setCancelled(true);
                strong(p, k);
            } else if (left && a == Action.LEFT_CLICK_AIR) {
                quickShot(p);
            }
            return;
        }
        if (!right || (t.skill != null && p.isSneaking())) return; // 사신수: 쉬프트+우클릭은 전용 기술
        if (a == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null && e.getClickedBlock().getType().isInteractable()) return;
        if (k == Kind.SHIELD && p.isSneaking()) return; // 쉬프트+우클릭은 방패 막기
        e.setCancelled(true);
        strong(p, k);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBowShoot(EntityShootBowEvent e) {
        if (e.getEntity() instanceof Player p && ItemData.id(e.getBow()) != null && plugin.getConfig().getBoolean("weapon-skills.enabled", true)) {
            e.setCancelled(true); // 혹시 당겨진 경우에도 바닐라 화살은 쏘지 않음
        }
    }

    /** 몬스터를 직접 때려도 바닐라 타격 대신 입자 참격으로 공격 */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDirectHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p) || e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK || inSkill.contains(p.getUniqueId())) return;
        if (kr.rpgcraft.combat.CombatService.BYPASS.get()) return;   // 스킬·참격으로 체력이 0 이 된 대상의 마무리 처치는 막지 않음
        if (!plugin.getConfig().getBoolean("weapon-skills.particle-melee", true) || !(e.getEntity() instanceof LivingEntity victim)) return;
        Kind k = kind(p);
        if (k == null || k == Kind.BOW || !plugin.combat().isEnemy(p, victim)) return;  // 동물 등은 일반 타격
        e.setCancelled(true);
        if (k == Kind.STAFF) magicBolt(p);
        else slash(p, k, victim);
    }

    /** 근접 평타 → 콤보 카운트 → N타째 평타 스킬 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMelee(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p) || e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        if (!(e.getEntity() instanceof LivingEntity victim) || inSkill.contains(p.getUniqueId())) return;
        if (!plugin.getConfig().getBoolean("weapon-skills.enabled", true)) return;
        Kind k = kind(p);
        if (k == null || k == Kind.BOW || !plugin.data().get(p).stats.weaponOk) return;
        drawSlash(p, k, false);  // 직접 때릴 때도 같은 참격 이펙트
        countCombo(p, k, victim);
    }

    private void countCombo(Player p, Kind k, LivingEntity victim) {
        int need = plugin.jobs().is(p, JobManager.Sub.BLADEMASTER) ? 2 : 3;
        int c = combo.merge(p.getUniqueId(), 1, Integer::sum);
        if (c < need) return;
        combo.put(p.getUniqueId(), 0);
        Bukkit.getScheduler().runTask(plugin, () -> basic(p, k, victim));
    }

    // ------------------------------------------------------------------ 참격 (좌클릭)
    private long interval(Kind k) {
        return switch (k) {
            case DAGGER -> WeaponClass.DAGGER.interval;
            case AXE -> WeaponClass.AXE.interval;
            case SHIELD -> WeaponClass.SHIELD.interval;
            case SPEAR -> WeaponClass.SPEAR.interval;
            case CLUB -> WeaponClass.CLUB.interval;
            case STAFF -> 700L;
            default -> WeaponClass.SWORD.interval;
        };
    }

    /** 허공을 휘둘러도 전방 부채꼴(3.5칸, 약 110°) 안의 적을 벤다. 무기 공격 간격마다 한 번 */
    private void slash(Player p, Kind k) {
        slash(p, k, null);
    }

    /** 무기 공격 간격마다 전방 부채꼴(창은 직선)의 적을 입자 참격으로 벤다. forced: 직접 때린 대상(범위 밖이어도 포함) */
    private void slash(Player p, Kind k, LivingEntity forced) {
        PlayerData d = plugin.data().get(p);
        if (!d.stats.weaponOk || d.onCooldown("slash")) return;
        d.cooldown("slash", interval(k));
        drawSlash(p, k, true);
        double range = k == Kind.SPEAR ? 6.5 : k == Kind.DAGGER ? 4.2 : 5.0;
        Vector look = p.getLocation().getDirection().setY(0).normalize();
        double mult = plugin.getConfig().getDouble("weapon-skills.slash-damage", 0.85);
        Set<UUID> hitOnce = new HashSet<>();
        Color col = slashColor(p);
        List<LivingEntity> targets = new ArrayList<>();
        if (forced != null && plugin.combat().isEnemy(p, forced)) targets.add(forced);
        for (Entity en : p.getNearbyEntities(range, 2.5, range)) {
            if (!(en instanceof LivingEntity le) || !plugin.combat().isEnemy(p, le)) continue;
            Vector to = le.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
            double dist = to.length();
            if (dist > range + le.getWidth() / 2) continue;
            if (dist > 0.6 && to.normalize().dot(look) < (k == Kind.SPEAR ? 0.85 : 0.55)) continue;
            targets.add(le);
        }
        long nowT = System.currentTimeMillis();
        if (!targets.isEmpty()) {   // 참격도 점프·웅크리기 공격으로 인정 (히든 패시브 조건)
            if (!p.isOnGround()) plugin.passives().track(p, "jump_attacks", 1);
            if (p.isSneaking()) plugin.passives().track(p, "sneak_attacks", 1);
        }
        for (LivingEntity le : targets) {
            if (!hitOnce.add(le.getUniqueId())) continue;
            hit(p, le, d.stats.attack * mult + plugin.combat().passiveBonus(p, d, le, nowT));   // 달리기·공중전투·약자/강자 멸시 등 보너스
            Fx.impact(le.getLocation().add(0, le.getHeight() * 0.6, 0), col);
            if (combo.getOrDefault(p.getUniqueId(), 0) == 0) plugin.combat().knockback(le, p.getLocation(), 0.35);   // 약하게, 가끔만
            countCombo(p, k, le);
            if (k == Kind.SPEAR && hitOnce.size() >= 2) break; // 창은 앞의 두 대상까지
        }
        if (!targets.isEmpty()) p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.7f, 1.3f);
    }

    // ------------------------------------------------------------------ 지팡이: 마력탄 (평타)
    private Color magicColor(Player p) {
        SkillBook.Set3 set = plugin.skillBook().of(ItemData.template(p.getInventory().getItemInMainHand()));
        return set == null ? Color.fromRGB(0x8FB8FF) : Color.fromRGB(set.strong().effect().rgb);
    }

    /** 조준 방향으로 빛나는 마력탄: 처음 맞은 적에게 마력 피해 + 작은 폭발, 3발마다 마력 폭발(평타 스킬) */
    private void magicBolt(Player p) {
        PlayerData d = plugin.data().get(p);
        if (!d.stats.weaponOk) {
            Text.actionBar(p, "&c무기 요구 조건 미충족: " + d.stats.weaponProblem);
            return;
        }
        if (d.onCooldown("slash")) return;
        d.cooldown("slash", 700);
        Color c = magicColor(p);
        Location start = p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(0.8)).add(0, -0.2, 0);
        Vector step = p.getEyeLocation().getDirection().normalize().multiply(1.3);
        double dmg = (d.stats.magic + d.stats.attack * 0.3) * plugin.getConfig().getDouble("weapon-skills.bolt-damage", 0.9);
        World w = p.getWorld();
        w.playSound(p.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 0.6f, 1.8f);
        Vfx.burst(start, 0.9, c);
        new org.bukkit.scheduler.BukkitRunnable() {
            final Location pos = start.clone();
            int n;

            @Override
            public void run() {
                if (!p.isOnline() || ++n > 18) { cancel(); return; }
                pos.add(step);
                if (n % 2 == 0) Vfx.burst(pos, 0.7, c);
                w.spawnParticle(Particle.END_ROD, pos, 1, 0, 0, 0, 0);
                LivingEntity target = null;
                for (Entity en : w.getNearbyEntities(pos, 1.2, 1.2, 1.2))
                    if (plugin.bossModels().resolve(en) instanceof LivingEntity le && plugin.combat().isEnemy(p, le)) { target = le; break; }   // 큰 보스는 모델 크기 판정
                if (target == null && pos.getBlock().isPassable()) return;
                cancel();
                Vfx.burst(pos, 1.8, c);
                Vfx.ring(pos.clone().add(0, -0.6, 0), 1.2, c);
                w.playSound(pos, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.6f, 1.6f);
                if (target != null) {
                    hit(p, target, dmg);
                    for (LivingEntity le : enemiesNear(p, pos, 1.6)) if (!le.equals(target)) hit(p, le, dmg * 0.4);
                    countCombo(p, Kind.STAFF, target);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private Color slashColor(Player p) {
        String id = ItemData.id(p.getInventory().getItemInMainHand());
        if (id != null && id.startsWith("spirit_")) {
            if (id.contains("qinglong")) return Color.fromRGB(0x6BD8FF);
            if (id.contains("baihu")) return Color.fromRGB(0xF4F4F4);
            if (id.contains("zhuque")) return Color.fromRGB(0xFF7A1F);
            if (id.contains("xuanwu")) return Color.fromRGB(0x2FBF71);
        }
        kr.rpgcraft.item.Grade g = ItemData.grade(p.getInventory().getItemInMainHand());
        if (g == null) return Color.fromRGB(0xE8EEF5);
        return switch (g) {
            case RARE -> Color.fromRGB(0x7CFF8A);
            case UNIQUE -> Color.fromRGB(0xFFE066);
            case LEGEND -> Color.fromRGB(0xFF5A5A);
            case MYTHIC -> Color.fromRGB(0xD08BFF);
            default -> Color.fromRGB(0xE8EEF5);
        };
    }

    /** 무기별 참격 모양: 검(가로 초승달) · 단검(X자 두 번) · 도끼(세로 내려찍기) · 창(직선 찌르기) · 방패/몽둥이(짧은 부채꼴) */
    /** 휘두를 때마다 +1 → 짝수/홀수로 좌→우, 우→좌 번갈아 */
    private final Map<UUID, Integer> swing = new HashMap<>();

    private void drawSlash(Player p, Kind k, boolean air) {
        Location eye = p.getEyeLocation();
        Vector f0 = eye.getDirection().setY(0);                      // 위아래 시선과 상관없이 수평 정면
        if (f0.lengthSquared() < 1e-4) f0 = new Vector(-Math.sin(Math.toRadians(p.getLocation().getYaw())), 0, Math.cos(Math.toRadians(p.getLocation().getYaw())));
        final Vector f = f0.normalize();
        Vector right = f.clone().crossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < 1e-4) right = new Vector(1, 0, 0);
        right.normalize();
        Vector up = right.clone().crossProduct(f).normalize();
        Color c = slashColor(p);
        World w = p.getWorld();
        boolean flip = swing.merge(p.getUniqueId(), 1, Integer::sum) % 2 == 0;
        Vector R = right, U = up;
        // 모양별 점 목록 (궤적 순서대로)
        List<Location> path = new ArrayList<>();
        switch (k) {
            case DAGGER -> {
                for (int s = -1; s <= 1; s += 2)
                    for (double t = -1; t <= 1.001; t += 0.12)
                        path.add(eye.clone().add(f.clone().multiply(1.55)).add(R.clone().multiply(t * 0.85)).add(U.clone().multiply(t * 0.85 * s - 0.2)));
            }
            case AXE -> {
                for (double a = 80; a >= -80; a -= 5) {
                    double r = Math.toRadians(a);
                    path.add(eye.clone().add(f.clone().multiply(1.3 + Math.cos(r) * 1.3)).add(U.clone().multiply(Math.sin(r) * 1.5 - 0.3)));
                }
            }
            case SPEAR -> {
                for (double t = 0.6; t <= 4.4; t += 0.18) path.add(eye.clone().add(f.clone().multiply(t)).add(0, -0.25, 0));
            }
            case SHIELD, CLUB -> {
                for (double a = -55; a <= 55; a += 5) {
                    double r = Math.toRadians(a * (flip ? -1 : 1));
                    path.add(eye.clone().add(f.clone().multiply(Math.cos(r) * 1.9)).add(R.clone().multiply(Math.sin(r) * 1.9)).add(0, -0.4, 0));
                }
            }
            default -> {
                double tilt = flip ? -0.4 : 0.4;
                for (double a = -75; a <= 75; a += 4) {
                    double r = Math.toRadians(a);
                    double rad = 2.15 + 0.3 * Math.cos(r);
                    path.add(eye.clone().add(f.clone().multiply(Math.cos(r) * rad)).add(R.clone().multiply(Math.sin(r) * rad * (flip ? -1 : 1)))
                            .add(U.clone().multiply(Math.sin(r) * tilt - 0.25)));
                }
            }
        }
        int n = path.size();
        if (Vfx.enabled()) {   // 잔상 없이 한 번에: 모델 참격 + 흰 중심 (4틱 뒤 사라짐)
            Location mid = eye.clone().add(f.clone().multiply(1.8)).add(0, -0.3, 0);
            Location far = eye.clone().add(0, -0.3, 0);
            switch (k) {
                case DAGGER -> {   // 빠른 찌르기 + 대각 베기 (번갈아 반대 대각)
                    Vfx.beam(far.clone().add(f.clone().multiply(0.4)), far.clone().add(f.clone().multiply(4.2)), 0.7, c);
                    Vfx.slash(mid, f, 3.4, flip ? 45 : -45, c);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> Vfx.burst(far.clone().add(f.clone().multiply(4.0)), 1.2, c), 1L);
                }
                case AXE -> {      // 내려찍기 ↔ 비스듬히 올려베기 번갈아
                    Vfx.slash(mid, f, 4.6, flip ? 90 : 60, c);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> Vfx.ring(p.getLocation().add(f.clone().multiply(2.6)), 1.6, c), 2L);
                }
                case SPEAR -> {   // 창: 끝까지 쭉 뻗는 찌르기 두 번 + 끝 번쩍
                    Vfx.beam(far.clone().add(f.clone().multiply(0.6)), far.clone().add(f.clone().multiply(6.4)), 1.0, c);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        Vfx.beam(far.clone().add(f.clone().multiply(1.2)), far.clone().add(f.clone().multiply(6.8)), 0.6, Color.WHITE);
                        Vfx.burst(far.clone().add(f.clone().multiply(6.4)), 1.6, c);
                    }, 1L);
                }
                case SHIELD, CLUB -> Vfx.slash(mid, f, 3.8, flip ? -40 : 40, c);   // 왼쪽 위→오른쪽 아래 ↔ 반대
                default -> {      // 검: 대각 베기 번갈아 + 1틱 뒤 흰 궤적
                    double roll = flip ? -35 : 35;
                    Vfx.slash(mid, f, 5.2, roll, c);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> Vfx.slash(mid.clone().add(f.clone().multiply(0.4)), f, 5.8, roll, Color.WHITE), 1L);
                }
            }
        } else {
            for (int i = 0; i < n; i += 2) Fx.dust(path.get(i), c, 0.6f);
        }
        Location center = path.get(n / 2);
        if (k == Kind.SWORD || k == Kind.AXE) w.spawnParticle(Particle.SWEEP_ATTACK, center.clone().add(0, -0.2, 0), 1);
        if (k == Kind.DAGGER || k == Kind.SPEAR) w.spawnParticle(Particle.CRIT, center, 6, 0.15, 0.15, 0.15, 0.2);
        if (air) w.playSound(p.getLocation(), k == Kind.AXE ? Sound.ENTITY_PLAYER_ATTACK_STRONG : Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.6f, k == Kind.DAGGER ? 1.7f : 1.2f);
    }

    // ------------------------------------------------------------------ 평타 스킬
    /** 평타 스킬 이름 알림 (기본 꺼짐: 매 타마다 화면을 가리지 않도록) */
    private void basicNotice(Player p, String msg) {
        if (plugin.getConfig().getBoolean("weapon-skills.basic-notice", false)) Text.actionBar(p, msg);
    }

    private void hit(Player p, LivingEntity t, double amount) {
        inSkill.add(p.getUniqueId());
        try {
            plugin.combat().dealSkillDamage(p, t, amount * plugin.jobs().outgoingMult(p, t), true);
        } finally {
            inSkill.remove(p.getUniqueId());
        }
    }

    private List<LivingEntity> enemiesNear(Player p, Location c, double r) {
        List<LivingEntity> out = new ArrayList<>();
        for (Entity en : c.getWorld().getNearbyEntities(c, r + 0.5, 4.5, r + 0.5))
            if (en instanceof LivingEntity le && plugin.combat().isEnemy(p, le)) out.add(le);   // 위아래 넉넉하게 (공중에서도 아래 적)
        return out;
    }

    private void basic(Player p, Kind k, LivingEntity target) {
        World w = p.getWorld();
        switch (k) {
            case STAFF -> {   // 마력 폭발: 대상 주변에 마력 고리가 터짐
                if (target == null) return;
                Color c = magicColor(p);
                Location at = target.getLocation().add(0, 0.2, 0);
                Vfx.ring(at, 3.2, c);
                Vfx.burst(at.clone().add(0, 1, 0), 3.0, c);
                for (LivingEntity le : enemiesNear(p, at, 3.2)) hit(p, le, power(p, 0.3, 1.4));
                w.playSound(at, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.8f, 1.2f);
                WeaponFx.eruption(at, 3.2, SkillBook.Effect.HOLY);   // (연출)
                basicNotice(p, "&b마력 폭발");
            }
            case SWORD -> { // 회전 베기
                for (LivingEntity le : enemiesNear(p, p.getLocation(), 2.8)) hit(p, le, power(p, 0.8, 0.3));
                w.spawnParticle(Particle.SWEEP_ATTACK, p.getLocation().add(0, 1, 0), 6, 1.2, 0.2, 1.2);
                w.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.2f);
                spinFx(p, slashColor(p), 2.8);   // (연출) 한 바퀴 도는 참격
                basicNotice(p, "&e평타 스킬 · 회전 베기");
            }
            case DAGGER -> { // 연속 찌르기
                for (int i = 1; i <= 2; i++) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (target.isValid() && !target.isDead()) {
                            hit(p, target, power(p, 0.35, 0.15));
                            w.spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 8, 0.2, 0.3, 0.2, 0.2);
                            Vfx.slash(target.getLocation().add(0, 1, 0), p.getLocation().getDirection().setY(0), 2.2, java.util.concurrent.ThreadLocalRandom.current().nextBoolean() ? 60 : -60, slashColor(p));   // (연출)
                            Vfx.burst(target.getLocation().add(0, 1, 0), 1.3, Color.WHITE);
                            w.playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.7f, 1.8f);
                        }
                    }, i * 3L);
                }
                basicNotice(p, "&e평타 스킬 · 연속 찌르기");
            }
            case AXE -> { // 내려찍기
                Location c = target.getLocation();
                for (LivingEntity le : enemiesNear(p, c, 2.2)) {
                    hit(p, le, power(p, 1.0, 0.3));
                    le.setVelocity(le.getVelocity().setY(0.45));
                }
                w.spawnParticle(Particle.BLOCK_CRACK, c, 30, 1, 0.1, 1, Material.DIRT.createBlockData());
                Vfx.ring(c, 2.2, slashColor(p));   // (연출) 내려찍은 자리 충격파 · 균열
                WeaponFx.crater(c, 2.2, SkillBook.Effect.EARTH);
                w.playSound(c, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 0.6f, 1.2f);
                basicNotice(p, "&e평타 스킬 · 내려찍기");
            }
            case SHIELD -> { // 방패 강타
                hit(p, target, power(p, 0.6, 0.4));
                plugin.combat().stun(target, 20);
                w.playSound(target.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 0.7f);
                Vfx.burst(target.getLocation().add(0, 1, 0), 2.2, Color.WHITE);   // (연출) 기절 별빛
                WeaponFx.element(target.getLocation().add(0, target.getHeight() + 0.3, 0), SkillBook.Effect.STUN, 0.6);
                basicNotice(p, "&e평타 스킬 · 방패 강타 (기절)");
            }
            case SPEAR -> { // 관통 찌르기
                for (LivingEntity le : line(p, 4.5, 1.2)) hit(p, le, power(p, 0.8, 0.3));
                Fx.line(p.getEyeLocation(), p.getEyeLocation().add(p.getLocation().getDirection().multiply(4.5)), 0.3, Color.fromRGB(0xA0FFCF), 1f);
                WeaponFx.lineSpiral(p.getEyeLocation(), p.getLocation().getDirection(), 4.5, SkillBook.Effect.WIND);   // (연출)
                basicNotice(p, "&e평타 스킬 · 관통 찌르기");
            }
            default -> { // 몽둥이 강타
                hit(p, target, power(p, 0.5, 0.2));
                plugin.combat().knockback(target, p.getLocation(), 4);
                Vfx.burst(target.getLocation().add(0, 1, 0), 2.0, slashColor(p));   // (연출)
                WeaponFx.element(target.getLocation().add(0, 1, 0), SkillBook.Effect.STUN, 0.6);
                basicNotice(p, "&e평타 스킬 · 강타");
            }
        }
    }

    private List<LivingEntity> line(Player p, double len, double width) {
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        List<LivingEntity> out = new ArrayList<>();
        for (Entity en : p.getNearbyEntities(len + 1, 3, len + 1)) {
            if (!(en instanceof LivingEntity le) || !plugin.combat().isEnemy(p, le)) continue;
            Vector to = le.getLocation().add(0, le.getHeight() / 2, 0).toVector().subtract(eye.toVector());
            double along = to.dot(dir);
            if (along < 0 || along > len) continue;
            if (to.clone().subtract(dir.clone().multiply(along)).length() <= width + le.getWidth() / 2) out.add(le);
        }
        return out;
    }

    // ------------------------------------------------------------------ 활
    private void arrow(Player p, Vector vel, double atk, int pierce) {
        Arrow ar = p.launchProjectile(Arrow.class, vel);
        ar.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        ar.setCritical(true);
        if (pierce > 0) ar.setPierceLevel(Math.min(127, pierce));
        ar.getPersistentDataContainer().set(Keys.ARROW_ATK, PersistentDataType.DOUBLE, atk);
        ar.getPersistentDataContainer().set(Keys.ARROW_FORCE, PersistentDataType.DOUBLE, 1.0);
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (ar.isValid()) ar.remove(); }, 100L);
        WeaponFx.trail(ar, slashColor(p), 20);   // (연출) 화살 꼬리
    }

    private void quickShot(Player p) {
        PlayerData d = plugin.data().get(p);
        if (!d.stats.weaponOk || d.onCooldown("quickshot")) return;
        d.cooldown("quickshot", (long) (plugin.getConfig().getLong("weapon-skills.quick-shot-ms", 1100) * cdMult(p)));
        double atk = d.stats.ranged + d.stats.magic * 0.1;
        int n = combo.merge(p.getUniqueId(), 1, Integer::sum);
        Vector dir = p.getEyeLocation().getDirection();
        if (n >= 3) { // 3발째: 부채꼴 3연사
            combo.put(p.getUniqueId(), 0);
            for (int i = -1; i <= 1; i++) arrow(p, rotate(dir, i * 0.12).multiply(3.0), atk * 0.8, 0);
            basicNotice(p, "&e평타 스킬 · 부채 사격");
        } else arrow(p, dir.clone().multiply(3.0), atk * 0.7, 0);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ARROW_SHOOT, 0.8f, 1.4f);
    }

    private static Vector rotate(Vector v, double yawRad) {
        double c = Math.cos(yawRad), s = Math.sin(yawRad);
        return new Vector(v.getX() * c - v.getZ() * s, v.getY(), v.getX() * s + v.getZ() * c);
    }

    // ------------------------------------------------------------------ 강공격
    private void strong(Player p, Kind k) {
        PlayerData d = plugin.data().get(p);
        if (!d.stats.weaponOk) {
            Text.actionBar(p, "&c무기 요구 조건 미충족: " + d.stats.weaponProblem);
            return;
        }
        ItemTemplate t = ItemData.template(p.getInventory().getItemInMainHand());
        SkillBook.Set3 set = plugin.skillBook().of(t);
        if (set == null) return;
        SkillBook.SkillDef def = set.strong();
        if (d.onCooldown("strong")) {
            Text.actionBar(p, "&7" + def.name() + " 재사용 대기 &c" + String.format("%.1f", d.remaining("strong") / 1000.0) + "초");
            return;
        }
        d.cooldown("strong", (long) (def.cd() * 1000 * cdMult(p)));
        double mult = plugin.jobs().is(p, JobManager.Sub.BERSERKER) ? 1.3 : 1;
        if (k == Kind.BOW && plugin.jobs().is(p, JobManager.Sub.SNIPER)) mult = 1.5;
        double dmg = (k == Kind.BOW ? d.stats.ranged + d.stats.magic * 0.3 : k == Kind.STAFF ? d.stats.magic + d.stats.attack * 0.3 : d.stats.attack + d.stats.magic * 0.4) * def.power() * mult;
        plugin.skillBook().cast(p, def, dmg, k == Kind.BOW, k == Kind.STAFF, (pl, le, amt) -> {
            hit(pl, le, amt);
            onStrongHit(pl, le);
        });
        Text.actionBar(p, "&6" + def.name());
        afterStrong(p, d);
    }

    private void onStrongHit(Player p, LivingEntity le) {
        if (plugin.jobs().isThird(p, JobManager.Third.BEAST_KING)) le.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80, 0));
        if (plugin.jobs().is(p, JobManager.Sub.RANGER)) le.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 60, 1));
        if (plugin.jobs().is(p, JobManager.Sub.BERSERKER)) plugin.health().heal(p, plugin.data().get(p).stats.attack * 0.2 * plugin.stats().hpScale());
    }

    private void afterStrong(Player p, PlayerData d) {
        long now = System.currentTimeMillis();
        JobManager.Third th = JobManager.third(d);
        if (plugin.jobs().is(p, JobManager.Sub.PALADIN) || th == JobManager.Third.CRUSADER) plugin.health().healPercent(p, th == JobManager.Third.CRUSADER ? 15 : 10);
        if (th == JobManager.Third.CRUSADER && plugin.party() != null && plugin.party().of(p) != null)
            for (Player m : plugin.party().of(p).online())
                if (!m.equals(p) && m.getWorld().equals(p.getWorld()) && m.getLocation().distanceSquared(p.getLocation()) < 100) plugin.health().healPercent(m, 8);
        if (plugin.jobs().is(p, JobManager.Sub.SHADOW) || th == JobManager.Third.NIGHT_SHADE) d.invulnUntil = Math.max(d.invulnUntil, now + (th == JobManager.Third.NIGHT_SHADE ? 2500 : 1500));
        if (plugin.jobs().is(p, JobManager.Sub.WARLORD) || th == JobManager.Third.CONQUEROR) d.counters.put("warlord_until", (double) (now + (th == JobManager.Third.CONQUEROR ? 6000 : 4000)));
    }
}
