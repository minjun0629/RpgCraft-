package kr.rpgcraft.passive;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.data.PlayerData;
import kr.rpgcraft.gui.Gui;
import kr.rpgcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 히든 패시브 해금(누적 행동 카운터), 액티브 스킬 사용, 사망/처치 시 발동 패시브.
 * 히든 패시브의 해금 조건은 원작에서 비공개였으므로 자체 설계했다.
 */
public class PassiveManager implements Listener {
    private record Unlock(String counter, double need, Passive passive) {}

    private static final List<Unlock> UNLOCKS = List.of(
            new Unlock("hits_taken", 500, Passive.HP_BOOST_1), new Unlock("hits_taken", 2000, Passive.HP_BOOST_2),
            new Unlock("hits_taken", 5000, Passive.HP_BOOST_3), new Unlock("hits_taken", 15000, Passive.HP_BOOST_4),
            new Unlock("hits_taken", 50000, Passive.HP_BOOST_5), new Unlock("hits_taken", 3000, Passive.ENDURANCE),
            new Unlock("mob_kills", 100, Passive.DELICATE_1), new Unlock("mob_kills", 500, Passive.DELICATE_2),
            new Unlock("mob_kills", 1500, Passive.DELICATE_3), new Unlock("mob_kills", 4000, Passive.DELICATE_4),
            new Unlock("mob_kills", 10000, Passive.DELICATE_5), new Unlock("mob_kills", 30000, Passive.DELICATE_6),
            new Unlock("mob_kills", 80000, Passive.DELICATE_7),
            new Unlock("enhance_attempts", 300, Passive.ENHANCE_GOD),
            new Unlock("sprint_seconds", 7200, Passive.RUNNER),
            new Unlock("jump_attacks", 1000, Passive.AIR_COMBAT),
            new Unlock("sneak_attacks", 1000, Passive.CROUCH_ATTACK),
            new Unlock("sneak_hits", 500, Passive.CURL),
            new Unlock("back_hits", 300, Passive.BACK_GUARD),
            new Unlock("jump_hits", 1000, Passive.AIR_MASTER),
            new Unlock("arrows_hit", 1000, Passive.ARCHER), new Unlock("arrows_hit", 2000, Passive.PARALYZE_ARROW),
            new Unlock("far_shots", 300, Passive.SNIPER),
            new Unlock("potions", 500, Passive.POTION_SIDE),
            new Unlock("fish_caught", 300, Passive.FISH_MASTER),
            new Unlock("gather_count", 1000, Passive.GATHER_MASTER),
            new Unlock("ach_treasure", 20, Passive.TREASURE_HUNTER),
            new Unlock("ach_boss", 20, Passive.BOSS_SLAYER),
            new Unlock("ach_wave", 30, Passive.WAVE_DEFENDER),
            new Unlock("ach_dungeon", 30, Passive.DUNGEON_DIVER),
            new Unlock("ach_elite", 500, Passive.ELITE_HUNTER),
            new Unlock("ach_quest", 100, Passive.QUEST_DEVOTEE),
            new Unlock("crit_hits", 3000, Passive.VITAL),
            new Unlock("player_kills", 10, Passive.PUSH), new Unlock("player_kills", 30, Passive.BULLY),
            new Unlock("deaths", 10, Passive.TRUCE), new Unlock("deaths", 30, Passive.UNDERDOG),
            new Unlock("gathers", 500, Passive.JACKPOT));

    private final RpgCraft plugin;

    public PassiveManager(RpgCraft plugin) {
        this.plugin = plugin;
        org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, this::periodic, 600L, 600L);
    }

    // ------------------------------------------------------------------ 해금
    /** 30초마다: 조건을 채운 히든 패시브 지급, 히든 스탯이 오르면 알림 */
    private void periodic() {
        for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
            PlayerData d = plugin.data().get(p);
            for (Unlock u : UNLOCKS) if (!d.has(u.passive) && d.counter(u.counter) >= u.need) grant(p, u.passive, true);
            for (kr.rpgcraft.stat.HiddenStat hs : kr.rpgcraft.stat.HiddenStat.values()) {
                int now = hs.points(d), seen = (int) d.counter("hs_seen_" + hs.name());
                if (now > seen) {
                    d.counters.put("hs_seen_" + hs.name(), (double) now);
                    p.sendTitle("", kr.rpgcraft.util.Text.c("&d" + hs.label + " +" + (now - seen)), 5, 40, 10);
                    p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);
                    plugin.stats().refresh(p);
                }
            }
        }
    }

    public void track(Player p, String counter, double amount) {
        PlayerData d = plugin.data().get(p);
        double before = d.counter(counter);
        d.addCounter(counter, amount);
        double after = before + amount;
        plugin.quests().onProgress(p, counter, before);
        for (Unlock u : UNLOCKS) {
            if (!u.counter.equals(counter) || before >= u.need || after < u.need || d.has(u.passive)) continue;
            grant(p, u.passive, true);
        }
    }

    public void grant(Player p, Passive ps, boolean announce) {
        PlayerData d = plugin.data().get(p);
        if (!d.passives.add(ps.name())) return;
        plugin.stats().refresh(p);
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        String tl = ps.isActive() ? ps.type.label.replace("패시브", "액티브 스킬") : ps.type.label;
        p.sendTitle(Text.c("&d&l" + tl), Text.c("&f" + ps.label), 10, 50, 10);
        Text.msg(p, "&d[" + tl + "] &f" + ps.label + " &7- " + ps.desc);
        if (announce) kr.rpgcraft.util.Text.announce(Text.PREFIX + Text.c("&d" + Text.name(p) + "&f님이 " + ps.type.label + " &d" + ps.label + "&f을(를) 획득했습니다!"));
    }

    public void revoke(PlayerData d, Passive ps) {
        d.passives.remove(ps.name());
    }

    // ------------------------------------------------------------------ 액티브
    public void useQuick(Player p) {
        PlayerData d = plugin.data().get(p);
        Passive ps = d.quickSkill == null ? null : Passive.find(d.quickSkill);
        if (ps == null) {
            Text.actionBar(p, "&7퀵 스킬이 없습니다. (/스킬 에서 우클릭으로 지정)");
            return;
        }
        useActive(p, ps);
    }

    public void useActive(Player p, Passive ps) {
        PlayerData d = plugin.data().get(p);
        if (!d.has(ps) || !ps.isActive()) {
            Text.actionBar(p, "&c사용할 수 없는 스킬입니다.");
            return;
        }
        String key = "active_" + ps.name();
        if (d.onCooldown(key)) {
            Text.actionBar(p, "&c" + ps.label + " 쿨타임 " + (d.remaining(key) / 1000 + 1) + "초");
            return;
        }
        Vector dir = p.getLocation().getDirection();
        switch (ps) {
            case PUSH -> {
                d.pendingKnock = 10;
                Text.actionBar(p, "&e밀치기 준비! 다음 타격이 상대를 10칸 밀칩니다");
            }
            case HARPY_WING -> {
                d.pendingKnock = 5;
                Text.actionBar(p, "&e하피의 날개짓 준비! 다음 타격이 상대를 5칸 넉백");
            }
            case TRUCE -> p.setVelocity(dir.clone().setY(0).normalize().multiply(-2.6).setY(0.35));
            case GOBLIN_HEADBUTT -> p.setVelocity(dir.clone().setY(0).normalize().multiply(1.7).setY(0.25));
            case FC_HARPY -> p.setVelocity(dir.clone().multiply(1.5).setY(1.2));
            case SEA_GOLEM -> {
                d.invulnUntil = System.currentTimeMillis() + 2000;
                p.getWorld().spawnParticle(Particle.WATER_SPLASH, p.getLocation().add(0, 1, 0), 60, 0.5, 1, 0.5);
                Text.actionBar(p, "&b바다골렘의 단단함! 2초간 무적");
            }
            case MUD_SORROW -> {
                for (Entity e : p.getNearbyEntities(4, 3, 4))
                    if (e instanceof LivingEntity le && plugin.combat().isEnemy(p, le)) plugin.combat().dealSkillDamage(p, le, d.stats.attack, true);
                p.getWorld().spawnParticle(Particle.BLOCK_CRACK, p.getLocation(), 50, 2, 0.2, 2, Material.MUD.createBlockData());
            }
            case DOLDOL_GIANT -> {
                d.doldolUntil = System.currentTimeMillis() + 300_000;
                plugin.stats().refresh(p);
                Text.msg(p, "&6돌돌이의 거대함! 5분간 최대 체력 +50%");
            }
            case PYRAMID_CURSE -> {
                LivingEntity target = null;
                double best = 0.95;
                for (Entity e : p.getNearbyEntities(10, 5, 10)) {
                    if (!(e instanceof LivingEntity le) || !plugin.combat().isEnemy(p, le)) continue;
                    Vector to = le.getEyeLocation().toVector().subtract(p.getEyeLocation().toVector()).normalize();
                    double dot = to.dot(dir);
                    if (dot > best && p.hasLineOfSight(le)) {
                        best = dot;
                        target = le;
                    }
                }
                if (target == null) {
                    Text.actionBar(p, "&c바라보는 방향 10칸 이내에 대상이 없습니다.");
                    return;
                }
                plugin.combat().stun(target, 40);
                Text.actionBar(p, "&6피라미드의 저주! 대상 2초 기절");
            }
            default -> {
                return;
            }
        }
        d.cooldown(key, ps.cooldown * 1000L);
        p.playSound(p.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1f, 1.2f);
    }

    // ------------------------------------------------------------------ 사망 / 처치
    public void onDeath(Player p, Player killer) {
        PlayerData d = plugin.data().get(p);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        track(p, "deaths", 1);
        if (d.has(Passive.FAMILIAR_FEAR) && d.counter("familiar_fear") < 500) {
            d.addCounter("familiar_fear", 15);
            Text.msg(p, "&8익숙한 공포... 공격력 +15 &7(누적 " + (int) Math.min(500, d.counter("familiar_fear")) + ")");
        }
        if (d.has(Passive.JACKPOT) && r.nextDouble() < 0.001) {
            d.money = Math.min(Long.MAX_VALUE / 40, d.money) * 10;
            kr.rpgcraft.util.Text.announce(Text.PREFIX + Text.c("&6&l잭팟! &e" + Text.name(p) + "&f님의 소지금이 10배가 되었습니다!"));
        }
        if (d.has(Passive.FC_GOBLIN) && r.nextDouble() < 0.1) {
            d.statPoints += 3;
            Text.msg(p, "&a고블린의 가호! 스탯 포인트 +3");
        }
        if (d.has(Passive.GAMBLER)) {
            d.level = 0;   // Lv.0 부터 다시 (Lv.1 이 될 때 스탯 포인트를 받도록)
            d.exp = 0;
            d.str = d.dex = d.adv = 0;
            d.statPoints = 0;
            kr.rpgcraft.util.Text.announce(Text.PREFIX + Text.c("&c" + Text.name(p) + "&7님이 도박에서 모든 것을 잃었습니다... (레벨/스탯 초기화)"));
        }
        if (killer != null && !killer.equals(p)) onKillPlayer(killer, p);
    }

    public void onKillPlayer(Player killer, Player victim) {
        PlayerData k = plugin.data().get(killer);
        PlayerData v = plugin.data().get(victim);
        track(killer, "player_kills", 1);
        int round = plugin.rounds().round();
        if (k.has(Passive.TRAINED_FEAR) && k.counter("trained_fear") < 500 && k.roundCounter("trained_fear", round) < 50) {
            k.addCounter("trained_fear", 5);
            k.addRoundCounter("trained_fear", 5, round);
            Text.msg(killer, "&8단련된 공포... 공격력 +5 &7(누적 " + (int) k.counter("trained_fear") + ")");
        }
        if (k.has(Passive.ABSORB) && victim.getUniqueId().equals(k.absorbTarget) && !k.onCooldown("absorb")) {
            int amount = ThreadLocalRandom.current().nextInt(3, 11);
            int taken = 0;
            for (int i = 0; i < amount; i++) {
                if (v.str >= v.dex && v.str >= v.adv && v.str > 0) v.str--;
                else if (v.dex >= v.adv && v.dex > 0) v.dex--;
                else if (v.adv > 0) v.adv--;
                else break;
                taken++;
            }
            k.statPoints += taken;
            k.absorbTarget = null;
            k.cooldown("absorb", 600_000);
            Text.msg(killer, "&5흡수! &f" + Text.name(victim) + "에게서 스탯 " + taken + "을(를) 빼앗았습니다.");
            Text.msg(victim, "&5" + Text.name(killer) + "에게 스탯 " + taken + "을(를) 흡수당했습니다...");
            plugin.stats().refresh(victim);
        }
        plugin.stats().refresh(killer);
    }

    public void designateAbsorb(Player p, String targetName) {
        PlayerData d = plugin.data().get(p);
        if (!d.has(Passive.ABSORB)) {
            Text.msg(p, "&c흡수 패시브가 없습니다.");
            return;
        }
        if (d.onCooldown("absorb_designate")) {
            Text.msg(p, "&c대상 지정 쿨타임 " + (d.remaining("absorb_designate") / 60000 + 1) + "분");
            return;
        }
        Player t = Text.player(targetName);
        if (t == null || t.equals(p)) {
            Text.msg(p, "&c접속 중인 다른 플레이어를 지정하세요.");
            return;
        }
        d.absorbTarget = t.getUniqueId();
        d.cooldown("absorb_designate", 3_600_000);
        Text.msg(p, "&5흡수 대상: &f" + Text.name(t));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMobDeath(EntityDeathEvent e) {
        if (e.getEntity() instanceof Player) return;
        Player k = e.getEntity().getKiller();
        if (k != null) track(k, "mob_kills", 1);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        Player p = e.getPlayer();
        PlayerData d = plugin.data().get(p);
        track(p, "fish", 1);
        if (d.has(Passive.MOON_FISHER) && d.counter("moon_fish") < 8000) {
            d.addCounter("moon_fish", 40);
            Text.actionBar(p, "&b달빛 낚시꾼: 체력 +40 &7(" + (int) d.counter("moon_fish") + "/8000)");
        }
        long money = plugin.getConfig().getLong("fishing.money", 300);
        plugin.economy().give(p, money);
    }

    // ------------------------------------------------------------------ GUI
    public void open(Player p) {
        new SkillGui(p).open(p);
    }

    private class SkillGui extends Gui {
        SkillGui(Player p) {
            super(6, "&d특수 스킬 / 패시브", "shop");
            PlayerData d = plugin.data().get(p);
            int slot = 0;
            for (Passive ps : Passive.values()) {
                if (!d.has(ps) || slot >= 45) continue;
                Material m = ps.isActive() ? Material.BLAZE_POWDER : switch (ps.type) {
                    case HIDDEN -> Material.ENCHANTED_BOOK;
                    case DUNGEON -> Material.BOOK;
                    case FIRST -> Material.NETHER_STAR;
                    case SECRET -> Material.ECHO_SHARD;
                };
                List<String> lore = new ArrayList<>();
                lore.add("&7" + (ps.isActive() ? ps.type.label.replace("패시브", "액티브 스킬") : ps.type.label));
                lore.add("&f" + ps.desc);
                if (ps.isActive()) {
                    lore.add("");
                    lore.add("&e쿨타임 " + ps.cooldown + "초");
                    String key = "active_" + ps.name();
                    if (d.onCooldown(key)) lore.add("&c남은 시간 " + (d.remaining(key) / 1000 + 1) + "초");
                    lore.add("&a좌클릭: 사용 &7| &b우클릭: 퀵슬롯(쉬프트+Q) 지정");
                    if (ps.name().equals(d.quickSkill)) lore.add("&b[퀵슬롯 지정됨]");
                }
                set(slot++, button(m, (ps.isActive() ? "&6" : "&d") + ps.label, lore.toArray(new String[0])), e -> {
                    if (!ps.isActive()) return;
                    if (e.isRightClick()) {
                        d.quickSkill = ps.name();
                        Text.msg(p, "&b퀵 스킬 지정: " + ps.label + " &7(쉬프트+Q)");
                        new SkillGui(p).open(p);
                    } else {
                        p.closeInventory();
                        useActive(p, ps);
                    }
                });
            }
            set(49, button(Material.BOOK, "&e보유 스킬 " + d.passives.size() + "개",
                    "&7히든 패시브는 특정 행동을 반복하면 해금됩니다.", "&7던전/최초 클리어 보상은 유적 클리어로 획득합니다."));
            fill(45, 53);
        }
    }
}
