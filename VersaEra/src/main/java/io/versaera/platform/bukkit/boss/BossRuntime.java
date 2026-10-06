package io.versaera.platform.bukkit.boss;

import io.versaera.application.GameServices;
import io.versaera.domain.boss.BossDefinition;
import io.versaera.domain.boss.BossFight;
import io.versaera.domain.boss.Shape;
import io.versaera.domain.boss.Vec;
import io.versaera.domain.event.GameEvents;
import io.versaera.platform.bukkit.Ui;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

/**
 * 거대 보스 실행부 (BOS-02, PARTIAL). 엔티티는 <b>판정 상자 1개(Interaction) + 모델 1개(ItemDisplay)</b>만 쓴다.
 * 공격 범위는 BossFight 가 수학으로 판정하고, 예고는 범위 외곽선 파티클(최대 48개)로만 보여 준다.
 * 모델은 리소스팩 모델(EXTERNAL_ASSET_REQUIRED)이 없을 때 임시로 큰 블록 아이템을 쓴다.
 */
public final class BossRuntime implements Listener {
    private final class Live {
        final BossDefinition def;
        final BossFight fight;
        final Interaction hitbox;
        final ItemDisplay model;
        final BossBar bar;
        final Location home;
        double hp;
        double yaw;
        final Set<UUID> participants = new HashSet<>();
        final List<Object[]> telegraphs = new ArrayList<>();   // [pattern, origin, yaw, resolveAt]

        Live(BossDefinition def, Location at) {
            this.def = def;
            this.home = at.clone();
            this.hp = def.maxHp();
            this.fight = new BossFight(def, System.currentTimeMillis());
            float w = (float) (def.hitRadius() * def.scale() * 2), h = (float) (def.scale() * 2);
            hitbox = at.getWorld().spawn(at, Interaction.class, x -> {
                x.setInteractionWidth(w);
                x.setInteractionHeight(h);
                x.setResponsive(true);
                x.setPersistent(false);
            });
            model = at.getWorld().spawn(at, ItemDisplay.class, x -> {
                x.setItemStack(new ItemStack(Material.DEEPSLATE_BRICKS));   // 임시 모델 (리소스팩 모델 필요)
                float sc = (float) (def.scale() * 1.8);
                x.setTransformation(new Transformation(new Vector3f(0, sc / 2, 0), new Quaternionf(), new Vector3f(sc, sc, sc), new Quaternionf()));
                x.setPersistent(false);
                x.setViewRange(4f);
            });
            bar = Bukkit.createBossBar(Ui.c("&6" + def.name()), BarColor.RED, BarStyle.SEGMENTED_10);
        }
    }

    private final Plugin plugin;
    private final GameServices s;
    private final Map<UUID, Live> byHitbox = new HashMap<>();

    public BossRuntime(Plugin plugin, GameServices s) {
        this.plugin = plugin;
        this.s = s;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 5L, 5L);
    }

    public BossDefinition def(String id) {
        return s.content.bosses().stream().filter(b -> b.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("없는 보스: " + id));
    }

    public void spawn(String id, Location at) {
        Live l = new Live(def(id), at);
        byHitbox.put(l.hitbox.getUniqueId(), l);
        at.getWorld().playSound(at, Sound.ENTITY_WITHER_SPAWN, 2f, 0.6f);
    }

    public int stopAll() {
        int n = byHitbox.size();
        for (Live l : byHitbox.values()) remove(l);
        byHitbox.clear();
        return n;
    }

    private void remove(Live l) {
        l.hitbox.remove();
        l.model.remove();
        l.bar.removeAll();
    }

    @EventHandler(ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        Live l = byHitbox.get(e.getEntity().getUniqueId());
        if (l == null) return;
        e.setCancelled(true);
        if (!(e.getDamager() instanceof Player p)) return;
        Location c = l.hitbox.getLocation();
        boolean weak = l.fight.weakPoint(new Vec(c.getX(), c.getY(), c.getZ()), l.yaw, new Vec(p.getLocation().getX(), p.getLocation().getY(), p.getLocation().getZ()));
        double dmg = e.getDamage() * 5 * (weak ? 1.5 : 1);
        l.hp = Math.max(0, l.hp - dmg);
        l.participants.add(p.getUniqueId());
        if (weak) p.spawnParticle(Particle.CRIT, p.getEyeLocation().add(p.getLocation().getDirection()), 6);
        if (l.hp <= 0) defeat(l);
    }

    private void defeat(Live l) {
        Location c = l.hitbox.getLocation();
        c.getWorld().playSound(c, Sound.ENTITY_ENDER_DRAGON_DEATH, 2f, 0.8f);
        Bukkit.broadcastMessage(Ui.info(l.def.name() + " 토벌"));
        List<String> who = l.participants.stream().map(UUID::toString).toList();
        byHitbox.remove(l.hitbox.getUniqueId());
        remove(l);
        s.bus.publish(new GameEvents.BossDefeated(l.def.id(), who));
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Live l : new ArrayList<>(byHitbox.values())) {
            if (!l.hitbox.isValid()) { byHitbox.remove(l.hitbox.getUniqueId()); remove(l); continue; }
            Location c = l.hitbox.getLocation();
            double arena = l.def.arenaRadius();
            Map<UUID, Vec> targets = new HashMap<>();
            Player nearest = null;
            double best = Double.MAX_VALUE;
            for (Player p : c.getWorld().getPlayers()) {
                if (p.getGameMode() == GameMode.SPECTATOR || p.getGameMode() == GameMode.CREATIVE) continue;
                double d = p.getLocation().distance(c);
                if (d > arena) { l.bar.removePlayer(p); continue; }
                l.bar.addPlayer(p);
                targets.put(p.getUniqueId(), new Vec(p.getLocation().getX(), p.getLocation().getY(), p.getLocation().getZ()));
                if (d < best) { best = d; nearest = p; }
            }
            l.bar.setProgress(Math.max(0, Math.min(1, l.hp / l.def.maxHp())));
            if (nearest != null) {
                Vector dir = nearest.getLocation().toVector().subtract(c.toVector());
                l.yaw = Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ()));
                Location m = l.model.getLocation();
                m.setYaw((float) l.yaw);
                l.model.teleport(m);
            }
            for (BossFight.Action a : l.fight.update(now, l.hp / l.def.maxHp(), new Vec(c.getX(), c.getY(), c.getZ()), l.yaw, targets)) {
                switch (a) {
                    case BossFight.Action.PhaseChanged pc -> {
                        if (pc.announce() != null && !pc.announce().isBlank())
                            for (UUID u : targets.keySet()) { Player p = Bukkit.getPlayer(u); if (p != null) p.sendTitle("", Ui.c("&6" + pc.announce()), 5, 50, 10); }
                    }
                    case BossFight.Action.Telegraph tg -> l.telegraphs.add(new Object[]{l.def.patterns().get(tg.pattern()), tg.origin(), tg.yaw(), tg.resolveAt()});
                    case BossFight.Action.Resolve r -> resolve(l, r);
                    case BossFight.Action.Enraged en -> l.bar.setColor(BarColor.PURPLE);
                }
            }
            l.telegraphs.removeIf(t -> (long) t[3] <= now);
            for (Object[] t : l.telegraphs) outline(c.getWorld(), (BossDefinition.Pattern) t[0], (Vec) t[1], (double) t[2], l.def.scale());
        }
    }

    /** 예고: 범위 외곽선만 (파티클 최대 48개) */
    private static void outline(World w, BossDefinition.Pattern p, Vec o, double yaw, double scale) {
        double r = p.radius() * scale;
        int n = 48;
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(255, 70, 40), 1.6f);
        double[] f = Vec.facing(yaw);
        for (int i = 0; i < n; i++) {
            double x, z;
            if (p.shape() == Shape.LINE) {
                double along = r * i / n, side = (i % 2 == 0 ? 1 : -1) * p.widthOrAngle() * scale / 2;
                x = o.x() + f[0] * along + f[1] * side;
                z = o.z() + f[1] * along - f[0] * side;
            } else {
                double half = p.shape() == Shape.CONE ? Math.toRadians(p.widthOrAngle() / 2) : Math.PI;
                double base = Math.atan2(f[1], f[0]);
                double a = base - half + 2 * half * i / n;
                x = o.x() + Math.cos(a) * r;
                z = o.z() + Math.sin(a) * r;
            }
            w.spawnParticle(Particle.REDSTONE, x, o.y() + 0.2, z, 1, 0, 0, 0, 0, dust);
        }
    }

    private void resolve(Live l, BossFight.Action.Resolve r) {
        Location c = l.hitbox.getLocation();
        c.getWorld().playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.7f);
        for (UUID u : r.hit()) {
            Player p = Bukkit.getPlayer(u);
            if (p == null) continue;
            p.damage(r.damage() / 5.0);
            if (r.effect() == null) continue;
            switch (r.effect()) {
                case "knockback" -> p.setVelocity(p.getLocation().toVector().subtract(c.toVector()).setY(0).normalize().multiply(1.4).setY(0.5));
                case "knockup" -> p.setVelocity(new Vector(0, 1.1, 0));
                case "pull" -> p.setVelocity(c.toVector().subtract(p.getLocation().toVector()).setY(0).normalize().multiply(1.2).setY(0.3));
                case "slow", "freeze" -> p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, r.effect().equals("freeze") ? 60 : 100, r.effect().equals("freeze") ? 4 : 1));
                case "blind" -> p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
                case "stagger" -> p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_DIGGING, 60, 1));
                default -> plugin.getLogger().warning("모르는 보스 효과: " + r.effect());
            }
        }
    }
}
