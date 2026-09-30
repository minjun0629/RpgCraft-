package kr.rpgcraft.feature;

import kr.rpgcraft.Keys;
import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.mob.CustomMobManager;
import kr.rpgcraft.mob.MobManager;
import kr.rpgcraft.util.Fx;
import kr.rpgcraft.util.Text;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 월드 이벤트
 *  1) 필드 웨이브 — 가끔 플레이어 주변에 깃발이 꽂히고, 우클릭하면 3단계 몬스터 웨이브 → 경험치·돈·전리품·희귀 보상
 *  2) 히든 상인 — 30분마다 맵 어딘가에 5분 동안 등장하는 특별 상인 (힌트: 스폰 기준 방향·거리)
 *  3) 전리품 — 야생 동물·일반 몬스터 처치 시 전리품 상인에게 팔 수 있는 재료 드롭
 */
public class WorldEventManager implements Listener {
    private final RpgCraft plugin;
    private final Random rnd = new Random();
    private final NamespacedKey HIDDEN, HIDDEN_UNTIL;

    public WorldEventManager(RpgCraft plugin) {
        this.plugin = plugin;
        this.HIDDEN = new NamespacedKey(plugin, "hidden_merchant");
        this.HIDDEN_UNTIL = new NamespacedKey(plugin, "hidden_merchant_until");
        Bukkit.getScheduler().runTaskTimer(plugin, this::flagTick, 20L * 30, 20L * 30);
        Bukkit.getScheduler().runTaskTimer(plugin, this::waveTick, 20L, 20L);
        long every = plugin.getConfig().getLong("hidden-merchant.interval-minutes", 30) * 60 * 20;
        Bukkit.getScheduler().runTaskTimer(plugin, this::spawnHiddenMerchant, every, every);
        Bukkit.getScheduler().runTask(plugin, this::cleanupMerchants);
        Bukkit.getScheduler().runTaskTimer(plugin, this::senseTick, 100L, 100L);
    }

    // =================================================================== 필드 웨이브
    private static class Flag {
        Location loc;
        BlockData old;
        UUID owner;
        long expire;
        TextDisplay label;
        Wave wave;
    }

    private static class Wave {
        final Map<UUID, Integer> deaths = new HashMap<>();
        final Set<UUID> failed = new HashSet<>();
        int stage;
        final Set<UUID> mobs = new HashSet<>();
        final Set<UUID> players = new HashSet<>();
        long stageDeadline, nextStageAt, startedAt = System.currentTimeMillis();
        BossBar bar;
        int level;
    }

    private final List<Flag> flags = new ArrayList<>();

    // ------------------------------------------------------------------ 웨이브 몬스터 표시
    private final NamespacedKey WAVE_MARK = new NamespacedKey("rpgcraft", "wave_mark");

    /** 머리 위에 크고 밝은 빨간 ▼ 표시 (몬스터를 따라다님, 벽에는 가려짐) + 이름 항상 표시 */
    private void markWave(LivingEntity m) {
        m.setCustomNameVisible(true);
        TextDisplay t = m.getWorld().spawn(m.getLocation().add(0, m.getHeight() + 0.6, 0), TextDisplay.class, x -> {
            x.setText(Text.c("&c&l▼"));
            x.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
            x.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
            x.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            x.setShadowed(true);
            x.setPersistent(false);
            // 이름표(레벨 · 체력바)는 머리 위 약 0.5칸에 그려지므로 ▼ 는 그보다 한참 위에 둠 (v5.4.15: 겹쳐서 체력바가 가려졌음)
            x.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(0, 2.1f, 0), new org.joml.AxisAngle4f(),
                    new org.joml.Vector3f(1.8f, 1.8f, 1.8f), new org.joml.AxisAngle4f()));
            x.getPersistentDataContainer().set(WAVE_MARK, PersistentDataType.BYTE, (byte) 1);
        });
        m.addPassenger(t);
        markers.add(t.getUniqueId());
    }

    private final Set<UUID> markers = new HashSet<>();
    private int markerTick;

    private void cleanMarkers() {
        markers.removeIf(id -> {
            Entity e = Bukkit.getEntity(id);
            if (e == null) return true;
            Entity v = e.getVehicle();
            if (v == null || !v.isValid() || v.isDead()) { e.remove(); return true; }
            return false;
        });
    }

    private void unmarkWave(Entity m) {
        for (Entity pas : new ArrayList<>(m.getPassengers()))
            if (pas.getPersistentDataContainer().has(WAVE_MARK, PersistentDataType.BYTE)) pas.remove();
    }

    @EventHandler
    public void onChunkMarkers(org.bukkit.event.world.ChunkLoadEvent e) {
        for (Entity en : e.getChunk().getEntities())
            if (en.getPersistentDataContainer().has(WAVE_MARK, PersistentDataType.BYTE) && en.getVehicle() == null) en.remove();
    }

    @EventHandler
    public void onWaveMobDeath(org.bukkit.event.entity.EntityDeathEvent e) {
        unmarkWave(e.getEntity());
    }

    public boolean isWaveMob(Entity e) {
        for (Flag f : flags) if (f.wave != null && f.wave.mobs.contains(e.getUniqueId())) return true;
        return false;
    }

    public boolean inWave(Player p) {
        for (Flag f : flags) if (f.wave != null && f.wave.players.contains(p.getUniqueId())) return true;
        return false;
    }

    /** 웨이브 참가자는 웨이브 몬스터에게만 공격받음 */
    public boolean waveShielded(Player p, Entity attacker) {
        if (attacker == null) return false;
        for (Flag f : flags) {
            Wave w = f.wave;
            if (w != null && w.players.contains(p.getUniqueId()) && !w.mobs.contains(attacker.getUniqueId())) return true;
        }
        return false;
    }

    @EventHandler(ignoreCancelled = true)
    public void onWildTarget(org.bukkit.event.entity.EntityTargetLivingEntityEvent e) {
        if (e.getTarget() instanceof Player p && !(e.getEntity() instanceof Player) && waveShielded(p, e.getEntity())) e.setCancelled(true);
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST, ignoreCancelled = true)
    public void onWildHit(org.bukkit.event.entity.EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Entity src = e.getDamager() instanceof org.bukkit.entity.Projectile pr && pr.getShooter() instanceof Entity sh ? sh : e.getDamager();
        if (!(src instanceof Player) && waveShielded(p, src)) e.setCancelled(true);
    }

    /** 웨이브 참가자가 치명상: 첫 번째는 부활(true), 두 번째는 탈락(false → 그대로 사망) */
    public boolean tryWaveRevive(Player p) {
        for (Flag f : flags) {
            Wave w = f.wave;
            if (w == null || !w.players.contains(p.getUniqueId()) || w.failed.contains(p.getUniqueId())) continue;
            if (!p.getWorld().equals(f.loc.getWorld()) || p.getLocation().distanceSquared(f.loc) > 45 * 45) continue;
            int n = w.deaths.merge(p.getUniqueId(), 1, Integer::sum);
            if (n <= plugin.getConfig().getInt("field-wave.revives", 1)) {
                plugin.data().get(p).invulnUntil = System.currentTimeMillis() + 3000;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    p.teleport(f.loc.clone().add(0.5, 0.2, 0.5));
                    p.sendTitle(Text.c("&e&l부활!"), Text.c("&7한 번 더 쓰러지면 웨이브에서 탈락합니다"), 5, 40, 10);
                    p.playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 0.8f, 1.2f);
                    p.getWorld().spawnParticle(Particle.TOTEM, p.getLocation().add(0, 1, 0), 30, 0.4, 0.8, 0.4, 0.3);
                });
                return true;
            }
            w.failed.add(p.getUniqueId());
            w.players.remove(p.getUniqueId());
            w.bar.removePlayer(p);
            Text.msg(p, "&c웨이브에서 탈락했습니다... &7(보상을 받을 수 없습니다)");
            return false;
        }
        return false;
    }

    public boolean isFlag(Location l) {
        for (Flag f : flags) if (same(f.loc, l)) return true;
        return false;
    }

    private static boolean same(Location a, Location b) {
        return a.getWorld().equals(b.getWorld()) && a.getBlockX() == b.getBlockX() && a.getBlockY() == b.getBlockY() && a.getBlockZ() == b.getBlockZ();
    }

    private void flagTick() {
        if (!plugin.getConfig().getBoolean("field-wave.enabled", true)) return;
        double chance = plugin.getConfig().getDouble("field-wave.chance-per-30s", 0.08);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) continue;
            if (p.getWorld().getEnvironment() != World.Environment.NORMAL || rnd.nextDouble() >= chance) continue;
            if (p.getLocation().distanceSquared(p.getWorld().getSpawnLocation()) < Math.pow(plugin.getConfig().getDouble("field-wave.min-spawn-distance", 64), 2)) continue;
            if (flags.stream().anyMatch(f -> f.owner.equals(p.getUniqueId()) || (f.loc.getWorld().equals(p.getWorld()) && f.loc.distanceSquared(p.getLocation()) < 60 * 60))) continue;
            spawnFlag(p);
        }
    }

    public boolean spawnFlag(Player p) {
        for (int tries = 0; tries < 10; tries++) {
            double a = rnd.nextDouble() * Math.PI * 2, r = 6 + rnd.nextDouble() * 6;
            Location at = p.getLocation().add(Math.cos(a) * r, 0, Math.sin(a) * r);
            Block top = kr.rpgcraft.util.Locs.surface(p.getWorld(), at);
            if (top.isLiquid() || Math.abs(top.getY() - p.getLocation().getY()) > 8) continue;
            Block b = top.getRelative(0, 1, 0);
            Flag f = new Flag();
            f.loc = b.getLocation();
            f.old = b.getBlockData().clone();
            f.owner = p.getUniqueId();
            f.expire = System.currentTimeMillis() + 120_000;
            b.setType(Material.RED_BANNER, false);
            f.label = b.getWorld().spawn(f.loc.clone().add(0.5, 2.4, 0.5), TextDisplay.class, d -> {
                d.setText(Text.c("&c&l⚑ 필드 웨이브\n&f우클릭하여 도전 &7(2분 뒤 사라짐)"));
                d.setBillboard(Display.Billboard.CENTER);
                d.setPersistent(false);
                d.getPersistentDataContainer().set(Keys.INDICATOR, PersistentDataType.BYTE, (byte) 1);
            });
            flags.add(f);
            p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 0.6f, 1.4f);
            Text.msg(p, "&c⚑ 근처에 &l필드 웨이브 깃발&c이 꽂혔습니다! &7(" + f.loc.getBlockX() + ", " + f.loc.getBlockY() + ", " + f.loc.getBlockZ() + ")");
            return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onFlag(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        for (Flag f : flags) {
            if (!same(f.loc, e.getClickedBlock().getLocation())) continue;
            e.setCancelled(true);
            if (f.wave == null) startWave(f, e.getPlayer());
            return;
        }
    }

    private void startWave(Flag f, Player starter) {
        Wave w = new Wave();
        w.level = plugin.mobs().computeLevel(f.loc) + 2;
        w.bar = Bukkit.createBossBar(Text.c("&c⚑ 필드 웨이브"), BarColor.RED, BarStyle.SEGMENTED_6);
        f.wave = w;
        if (f.label != null) f.label.setText(Text.c("&c&l⚑ 필드 웨이브 진행 중"));
        Text.msg(starter, "&c필드 웨이브 시작! 3단계의 몬스터를 모두 처치하세요.");
        nextStage(f);
    }

    private void nextStage(Flag f) {
        Wave w = f.wave;
        w.stage++;
        int count = 3 + w.stage * 2 + (int) Math.min(6, playersNear(f.loc, 30).size() * 1.5);
        int lv = w.level + (w.stage - 1) * 3;
        List<CustomMobManager.MobDef> pool = new ArrayList<>();
        for (CustomMobManager.MobDef d : plugin.customMobs().defs())
            if (lv >= d.minLevel && lv <= d.maxLevel && d.type != EntityType.PHANTOM && d.type != EntityType.VEX) pool.add(d);
        for (int i = 0; i < count; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            Location at = kr.rpgcraft.util.Locs.surface(f.loc.getWorld(), f.loc.clone().add(Math.cos(a) * 9, 0, Math.sin(a) * 9)).getLocation().add(0.5, 1, 0.5);
            LivingEntity m;
            if (!pool.isEmpty() && rnd.nextDouble() < 0.7) m = plugin.customMobs().spawn(pool.get(rnd.nextInt(pool.size())), at, lv);
            else {
                EntityType t = new EntityType[]{EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.HUSK}[rnd.nextInt(4)];
                m = (LivingEntity) at.getWorld().spawnEntity(at, t);
                MobManager mm = plugin.mobs();
                mm.initCustom(m, lv, mm.hpFor(lv), mm.damageFor(lv), lv * 0.2, mm.expFor(lv), lv * 60L, "웨이브 " + MobManager.korean(t));
            }
            if (m == null) continue;
            if (m instanceof Mob mob) {
                mob.setRemoveWhenFarAway(false);
                Player t = nearest(at);
                if (t != null) mob.setTarget(t);
            }
            plugin.tiers().aura(m, Color.fromRGB(0xFF5050), true);
            markWave(m);
            MobManager.MobState ws = plugin.mobs().peek(m);
            if (ws != null && !ws.baseName.contains("[웨이브]")) { ws.baseName = "&c[웨이브] &f" + ws.baseName.replace("웨이브 ", ""); plugin.mobs().updateName(m, ws); }
            w.mobs.add(m.getUniqueId());
            at.getWorld().spawnParticle(Particle.SMOKE_LARGE, at, 12, 0.3, 0.6, 0.3, 0.02);
        }
        w.stageDeadline = System.currentTimeMillis() + plugin.getConfig().getLong("field-wave.stage-seconds", 150) * 1000;
        w.nextStageAt = 0;
        for (Player p : playersNear(f.loc, 30)) {
            p.sendTitle(Text.c("&c&l웨이브 " + w.stage + " / 3"), Text.c("&f몬스터 " + count + "마리"), 5, 30, 5);
            p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 0.8f, 1f);
        }
    }

    private Player nearest(Location l) {
        Player best = null;
        double bd = Double.MAX_VALUE;
        for (Player p : l.getWorld().getPlayers()) {
            double d = p.getLocation().distanceSquared(l);
            if (d < bd) { bd = d; best = p; }
        }
        return best;
    }

    private List<Player> playersNear(Location l, double r) {
        List<Player> out = new ArrayList<>();
        for (Player p : l.getWorld().getPlayers()) if (p.getLocation().distanceSquared(l) <= r * r && !p.isDead()) out.add(p);
        return out;
    }

    private void waveTick() {
        if (++markerTick % 2 == 0) cleanMarkers();
        long now = System.currentTimeMillis();
        for (Flag f : new ArrayList<>(flags)) {
            if (f.wave == null) {
                if (f.expire < now) removeFlag(f);
                continue;
            }
            Wave w = f.wave;
            w.mobs.removeIf(id -> {
                Entity e = Bukkit.getEntity(id);
                return e == null || e.isDead() || !e.isValid();
            });
            List<Player> near = playersNear(f.loc, 35);
            near.removeIf(pl -> w.failed.contains(pl.getUniqueId()));   // 탈락한 사람은 제외
            double R = plugin.getConfig().getDouble("field-wave.arena-radius", 18);
            drawArena(f.loc, R, near);
            for (UUID id : w.mobs) {  // 웨이브 몬스터도 전장 밖으로 못 나감
                Entity me = Bukkit.getEntity(id);
                if (me == null) continue;
                double md = Math.hypot(me.getLocation().getX() - f.loc.getX() - 0.5, me.getLocation().getZ() - f.loc.getZ() - 0.5);
                if (md > R + 6) {
                    Location back = f.loc.clone().add(0.5 + (Math.random() * 2 - 1) * R * 0.5, 0, 0.5 + (Math.random() * 2 - 1) * R * 0.5);
                    back.setY(back.getWorld().getHighestBlockYAt(back) + 1);
                    me.teleport(back);
                } else if (md > R - 1) {
                    org.bukkit.util.Vector in = f.loc.clone().add(0.5, 0, 0.5).toVector().subtract(me.getLocation().toVector()).setY(0).normalize().multiply(0.7).setY(0.2);
                    me.setVelocity(in);
                }
            }
            for (Player p : near) {  // 경계 밖으로 나가면 안쪽으로 밀어 넣기
                double dd = Math.hypot(p.getLocation().getX() - f.loc.getX() - 0.5, p.getLocation().getZ() - f.loc.getZ() - 0.5);
                if (w.players.contains(p.getUniqueId()) && dd > R && dd < R + 12) {
                    org.bukkit.util.Vector in = f.loc.clone().add(0.5, 0, 0.5).toVector().subtract(p.getLocation().toVector()).setY(0).normalize().multiply(0.8).setY(0.25);
                    p.setVelocity(in);
                    Text.actionBar(p, "&c웨이브 전장을 벗어날 수 없습니다!");
                }
            }
            boolean open = System.currentTimeMillis() - w.startedAt < 5000;   // 시작 5초 안에 있던 사람만 참가
            for (Player p : near) {
                if (!w.players.contains(p.getUniqueId())) {
                    double dd = Math.hypot(p.getLocation().getX() - f.loc.getX() - 0.5, p.getLocation().getZ() - f.loc.getZ() - 0.5);
                    if (!open && !w.failed.contains(p.getUniqueId()) && dd < R + 1.5) {   // 바깥 사람은 밀어냄
                        org.bukkit.util.Vector out = p.getLocation().toVector().subtract(f.loc.clone().add(0.5, 0, 0.5).toVector()).setY(0);
                        if (out.lengthSquared() < 0.01) out = new org.bukkit.util.Vector(1, 0, 0);
                        p.setVelocity(out.normalize().multiply(0.9).setY(0.3));
                        Text.actionBar(p, "&c웨이브가 진행 중입니다");
                    }
                    if (!open) continue;
                }
                w.players.add(p.getUniqueId());
                if (!w.bar.getPlayers().contains(p)) w.bar.addPlayer(p);
            }
            for (Player p : new ArrayList<>(w.bar.getPlayers())) if (!near.contains(p)) w.bar.removePlayer(p);
            w.bar.setTitle(Text.c("&c⚑ 필드 웨이브 " + w.stage + "/3 &7- 남은 몬스터 &f" + w.mobs.size()
                    + " &7- " + Math.max(0, (w.stageDeadline - now) / 1000) + "초"));
            w.bar.setProgress(Math.max(0, Math.min(1, (w.stage - 1 + (w.mobs.isEmpty() ? 1 : 0)) / 3.0)));
            if (near.isEmpty() || (now > w.stageDeadline && !w.mobs.isEmpty())) {
                for (UUID id : w.players) {
                    Player p = Bukkit.getPlayer(id);
                    if (p != null) Text.msg(p, "&7필드 웨이브에 실패했습니다...");
                }
                for (UUID id : w.mobs) { Entity e = Bukkit.getEntity(id); if (e != null) { unmarkWave(e); e.remove(); } }
                removeFlag(f);
                continue;
            }
            if (w.mobs.isEmpty()) {
                if (w.stage >= 3) {
                    reward(f);
                    removeFlag(f);
                } else if (w.nextStageAt == 0) {
                    w.nextStageAt = now + 5000;
                    for (Player p : near) Text.actionBar(p, "&a웨이브 " + w.stage + " 클리어! &75초 뒤 다음 웨이브");
                } else if (now >= w.nextStageAt) nextStage(f);
            }
        }
    }

    /** 원형 경계: 짧게 반짝이는 점선 (화면을 가리지 않도록 적은 수의 입자, 참가자에게만) */
    private void drawArena(Location c, double r, List<Player> viewers) {
        int n = (int) Math.max(24, r * 3);
        double off = (System.currentTimeMillis() / 1000.0) % 1;
        for (int i = 0; i < n; i++) {
            double a = (i + off) * Math.PI * 2 / n;
            Location l = new Location(c.getWorld(), c.getX() + 0.5 + Math.cos(a) * r, c.getY() + 0.2, c.getZ() + 0.5 + Math.sin(a) * r);
            l.setY(c.getWorld().getHighestBlockYAt(l) + 1.1);
            for (Player p : viewers) p.spawnParticle(Particle.REDSTONE, l, 1, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(0xFF3030), 1.3f));
        }
    }

    private void reward(Flag f) {
        Wave w = f.wave;
        for (UUID id : w.players) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.getWorld().equals(f.loc.getWorld()) || p.getLocation().distanceSquared(f.loc) > 60 * 60) continue;
            var d = plugin.data().get(p);
            double exp = plugin.levels().need(d.level) * plugin.getConfig().getDouble("field-wave.exp-ratio", 0.3);
            long money = (long) (w.level * plugin.getConfig().getLong("field-wave.money-per-level", 3000) * plugin.getConfig().getDouble("economy.wave-money-mult", 0.3));
            plugin.levels().addExp(p, exp);
            plugin.economy().give(p, money);
            List<ItemStack> items = new ArrayList<>();
            items.add(plugin.items().create("loot_wave", 1 + rnd.nextInt(2)));
            String crystal = w.level >= 90 ? "crystal_top" : w.level >= 60 ? "crystal_high" : w.level >= 30 ? "crystal_mid" : "crystal_low";
            items.add(plugin.items().create(crystal, 1 + rnd.nextInt(3)));
            if (rnd.nextDouble() < 0.25) items.add(plugin.items().create(w.level >= 60 ? "rune_mid" : "rune_low", 1));
            if (rnd.nextDouble() < 0.05) items.add(plugin.items().create("ticket_rate10", 1));
            if (rnd.nextDouble() < 0.02) items.add(plugin.items().create("ticket_protect", 1));
            for (ItemStack it : items) if (it != null) for (ItemStack left : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
            d.counters.merge("ach_wave", 1.0, Double::sum);
            p.sendTitle(Text.c("&6&l필드 웨이브 클리어!"), Text.c("&f경험치 " + Text.num(exp) + " · " + Text.money(money)), 5, 50, 10);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            Fx.helix(plugin, p, 2.4, 0.9, 20, Color.fromRGB(0xFFD23F), Color.RED);
        }
    }

    private void removeFlag(Flag f) {
        flags.remove(f);
        if (f.wave != null) f.wave.bar.removeAll();
        if (f.label != null) f.label.remove();
        f.loc.getBlock().setBlockData(f.old, false);
    }

    public void shutdown() {
        for (Flag f : new ArrayList<>(flags)) {
            if (f.wave != null) for (UUID id : f.wave.mobs) { Entity e = Bukkit.getEntity(id); if (e != null) { unmarkWave(e); e.remove(); } }
            removeFlag(f);
        }
        cleanupMerchants();
    }

    // =================================================================== 히든 상인
    private UUID merchant;

    private final Set<UUID> sensed = new HashSet<>();

    /** 5초마다: 상인 근처(기본 500블록)에 들어온 플레이어에게 기척만 알림 (좌표·방향·거리 비공개) */
    private void senseTick() {
        Location m = merchantLocation();
        if (m == null) { sensed.clear(); return; }
        double r = plugin.getConfig().getDouble("hidden-merchant.sense-radius", 500);
        for (Player p : m.getWorld().getPlayers()) {
            boolean in = p.getLocation().distanceSquared(m) <= r * r;
            if (in && sensed.add(p.getUniqueId())) {
                Text.msg(p, "&d근처에 수상한 상인의 기척이 느껴진다...");
                p.playSound(p.getLocation(), Sound.ENTITY_WANDERING_TRADER_AMBIENT, 0.6f, 0.7f);
            } else if (!in) sensed.remove(p.getUniqueId());
        }
    }

    public Location merchantLocation() {
        if (merchant == null) return null;
        org.bukkit.entity.Entity e = Bukkit.getEntity(merchant);
        return e == null || !e.isValid() ? null : e.getLocation();
    }

    public Location nearestFlag(Player p) {
        Location best = null;
        double bd = 80 * 80;
        for (Flag f : flags) {
            if (!f.loc.getWorld().equals(p.getWorld())) continue;
            double d = f.loc.distanceSquared(p.getLocation());
            if (d < bd) { bd = d; best = f.loc; }
        }
        return best;
    }

    /** v5.10.1: 끝났는데 남아 있는 히든 상인인가 (재시작 · 지역이 로딩되지 않아 못 지운 경우 — 재고가 없어 아무것도 안 팔았음) */
    private boolean staleMerchant(Entity e) {
        var pdc = e.getPersistentDataContainer();
        if (!pdc.has(HIDDEN, PersistentDataType.BYTE)) return false;
        Long until = pdc.get(HIDDEN_UNTIL, PersistentDataType.LONG);
        return !e.getUniqueId().equals(merchant) || until == null || System.currentTimeMillis() > until;
    }

    @EventHandler
    public void onMerchantLoad(org.bukkit.event.world.EntitiesLoadEvent e) {
        for (Entity en : e.getEntities()) if (en instanceof Villager && staleMerchant(en)) en.remove();
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onMerchantClick(org.bukkit.event.player.PlayerInteractEntityEvent e) {
        Entity en = e.getRightClicked();
        if (!(en instanceof Villager) || !staleMerchant(en)) return;
        e.setCancelled(true);
        en.getWorld().spawnParticle(Particle.PORTAL, en.getLocation().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.5);
        en.remove();
        Text.msg(e.getPlayer(), "&7상인은 이미 떠난 뒤였다...");
    }

    private void cleanupMerchants() {
        for (World w : Bukkit.getWorlds())
            for (Villager v : w.getEntitiesByClass(Villager.class))
                if (v.getPersistentDataContainer().has(HIDDEN, PersistentDataType.BYTE)) v.remove();
        merchant = null;
    }

    public boolean spawnHiddenMerchant() {
        if (!plugin.getConfig().getBoolean("hidden-merchant.enabled", true) || plugin.shops().get("hidden") == null) return false;
        cleanupMerchants();
        World w = Bukkit.getWorlds().get(0);
        Location spawn = w.getSpawnLocation();
        int radius = plugin.getConfig().getInt("hidden-merchant.radius", 400);
        for (int tries = 0; tries < 20; tries++) {
            double a = rnd.nextDouble() * Math.PI * 2, r = radius * (0.3 + rnd.nextDouble() * 0.7);
            Location at = spawn.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r);
            Block top = kr.rpgcraft.util.Locs.surface(w, at);
            if (top.isLiquid() || top.getType() == Material.ICE) continue;
            Location l = top.getLocation().add(0.5, 1, 0.5);
            plugin.shops().rollHiddenStock();
            Villager v = plugin.shops().spawnNpc(l, "hidden");
            if (v == null) return false;
            v.setProfession(Villager.Profession.NITWIT);
            v.setGlowing(false);
            v.getPersistentDataContainer().set(HIDDEN, PersistentDataType.BYTE, (byte) 1);
            merchant = v.getUniqueId();
            long stay = plugin.getConfig().getLong("hidden-merchant.stay-minutes", 5);
            v.getPersistentDataContainer().set(HIDDEN_UNTIL, PersistentDataType.LONG, System.currentTimeMillis() + stay * 60_000L);   // v5.10.1 만료 시각
            String dir = direction(l.getX() - spawn.getX(), l.getZ() - spawn.getZ());
            int dist = (int) Math.round(l.distance(spawn) / 50.0) * 50;
            if (plugin.getConfig().getBoolean("hidden-merchant.reveal-direction", false))
                Text.announce(Text.PREFIX + Text.c("&d&l✦ 히든 상인&f이 어딘가에 나타났습니다! &7(스폰에서 &f" + dir + " &7약 &f" + dist + "블록&7, " + stay + "분 뒤 사라짐)"));
            else
                Text.announce(Text.PREFIX + Text.c("&d&l✦ 수상한 상인&f이 이 세계 어딘가에 나타났습니다..."));
            for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), Sound.ENTITY_WANDERING_TRADER_AMBIENT, 1f, 0.8f);
            UUID id = v.getUniqueId();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Entity e = Bukkit.getEntity(id);
                if (e != null) {
                    e.getWorld().spawnParticle(Particle.PORTAL, e.getLocation().add(0, 1, 0), 60, 0.4, 0.8, 0.4, 0.5);
                    e.remove();
                }
                if (id.equals(merchant)) {   // 그 지역이 로딩되지 않아 못 지웠어도 끝난 것으로 (다시 로딩될 때 지움)
                    merchant = null;
                    Text.announce(Text.PREFIX + Text.c("&7히든 상인이 사라졌습니다..."));
                }
            }, stay * 60 * 20);
            return true;
        }
        return false;
    }

    private static String direction(double dx, double dz) {
        double ang = Math.toDegrees(Math.atan2(dx, -dz));
        if (ang < 0) ang += 360;
        String[] d = {"북쪽", "북동쪽", "동쪽", "남동쪽", "남쪽", "남서쪽", "서쪽", "북서쪽"};
        return d[(int) Math.round(ang / 45) % 8];
    }

    // =================================================================== 전리품
    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(EntityDeathEvent e) {
        LivingEntity ent = e.getEntity();
        Player k = ent.getKiller();
        if (k == null || ent instanceof Player || plugin.customMobs().of(ent) != null) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (ent instanceof Animals) {
            e.getDrops().clear();
            switch (ent.getType().name()) {
                case "COW", "MOOSHROOM" -> { add(e, "loot_meat", 1, 2); add(e, "loot_hide", 1, 1); }
                case "PIG" -> add(e, "loot_meat", 1, 3);
                case "SHEEP" -> { add(e, "loot_wool", 1, 2); add(e, "loot_meat", 1, 1); }
                case "CHICKEN" -> { add(e, "loot_feather", 1, 2); add(e, "loot_meat", 1, 1); }
                case "GOAT" -> { if (r.nextDouble() < 0.25) add(e, "loot_horn", 1, 1); add(e, "loot_meat", 1, 1); }
                default -> add(e, "loot_hide", 1, 2);
            }
            if (r.nextDouble() < 0.04) add(e, "loot_pelt", 1, 1);
            return;
        }
        if (ent instanceof Golem) {   // 철 골렘·눈 골렘: 철괴 대신 전리품
            e.getDrops().clear();
            add(e, "loot_steel", 1, 2);
            if (r.nextDouble() < 0.1) add(e, "loot_core", 1, 1);
            return;
        }
        if (!(ent instanceof Enemy)) { e.getDrops().clear(); return; }   // 그 외 바닐라 드롭 없음
        if (r.nextDouble() >= plugin.getConfig().getDouble("mobs.loot-chance", 0.35)) return;
        switch (ent.getType().name()) {
            case "SPIDER", "CAVE_SPIDER" -> add(e, r.nextBoolean() ? "loot_venom" : "loot_fang", 1, 1);
            case "CREEPER", "VEX" -> add(e, "loot_dust", 1, 1);
            case "SLIME" -> add(e, "loot_slime", 1, 2);
            case "MAGMA_CUBE", "BLAZE" -> add(e, "loot_ember", 1, 1);
            case "ENDERMAN" -> {
                var st = plugin.mobs().peek(ent);
                add(e, st != null && st.level >= 40 && r.nextDouble() < 0.15 ? "loot_eye" : "loot_ink", 1, 1);
            }
            case "WITCH" -> add(e, "loot_venom", 1, 1);
            case "PILLAGER", "VINDICATOR", "EVOKER" -> add(e, "loot_totem", 1, 1);
            case "PHANTOM" -> add(e, "loot_scale", 1, 1);
            case "HUSK" -> add(e, "loot_bandage", 1, 1);
            case "STRAY" -> add(e, "loot_frost", 1, 1);
            case "WITHER_SKELETON" -> { add(e, "loot_bone", 1, 2); add(e, "loot_dust", 1, 1); }
            default -> add(e, "loot_bone", 1, 2);
        }
    }

    private void add(EntityDeathEvent e, String id, int min, int max) {
        ItemStack it = plugin.items().create(id, ThreadLocalRandom.current().nextInt(min, max + 1));
        if (it != null) e.getDrops().add(it);
    }
}
