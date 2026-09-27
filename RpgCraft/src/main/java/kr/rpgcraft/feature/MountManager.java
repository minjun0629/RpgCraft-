package kr.rpgcraft.feature;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.gui.Gui;
import kr.rpgcraft.item.ItemData;
import kr.rpgcraft.util.Text;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 탈것: /탈것 뽑기 상점에서 뽑고, 탈것 아이템을 우클릭하면 소환해서 바로 탄다 (내리면 사라짐).
 * 보이지 않는 말 위에 전용 3D 모델을 얹어 움직인다. 등급이 높을수록 빠르고 높이 뛴다.
 */
public class MountManager implements Listener, CommandExecutor {
    public enum Mount {
        // model, 이름, 등급, 안장 높이(모델 단위, 1/16블록)
        WOLF("mount_wolf", "잿빛 늑대", 0, 12), LIZARD("mount_lizard", "사막 도마뱀", 0, 8),
        WARHORSE("mount_warhorse", "기사의 군마", 1, 15.9), ICEBEAR("mount_icebear", "빙하 곰", 1, 13.5),
        LION("mount_lion", "불꽃 사자", 2, 13.5), PANTHER("mount_panther", "그림자 표범", 2, 12),
        GRIFFIN("mount_griffin", "황금 그리핀", 3, 14), DRAGON("mount_dragon", "심연의 용", 3, 14);

        public final String model, label;
        public final int grade;
        public final double saddle;

        Mount(String model, String label, int grade, double saddle) {
            this.model = model;
            this.label = label;
            this.grade = grade;
            this.saddle = saddle;
        }

        /** 등급별 속도 (일반 < 희귀 < 영웅 < 전설) — config mounts.speed 로 변경 가능 */
        public double speed() {
            return gradeValue("mounts.speed", SPEED, grade);
        }

        /** 등급별 점프력 */
        public double jump() {
            return gradeValue("mounts.jump", JUMP, grade);
        }

        /** 전설 등급은 하늘을 난다 */
        public boolean flies() {
            return grade >= 3;
        }
    }

    private static final double[] SPEED = {0.24, 0.29, 0.34, 0.40}, JUMP = {0.55, 0.7, 0.85, 1.0};

    private static double gradeValue(String key, double[] def, int grade) {
        List<Double> l = RpgCraft.get() == null ? List.of() : RpgCraft.get().getConfig().getDoubleList(key);
        return grade < l.size() ? l.get(grade) : def[grade];
    }

    private static final String[] GRADE = {"&f일반", "&9희귀", "&5영웅", "&6전설"};
    private static final double[] ODDS = {0.63, 0.26, 0.10, 0.01};   // 전설 1% (config mounts.odds)

    private final RpgCraft plugin;
    private final NamespacedKey KEY, RIDE;
    private final Map<UUID, UUID[]> riding = new HashMap<>();   // 플레이어 → {말, 모델}
    private final Map<UUID, Mount> ridingType = new HashMap<>();
    private final Map<UUID, Long> mountedAt = new HashMap<>();
    private final Set<UUID> flying = new HashSet<>();

    public MountManager(RpgCraft plugin) {
        this.plugin = plugin;
        this.KEY = new NamespacedKey(plugin, "mount");
        this.RIDE = new NamespacedKey(plugin, "mount_entity");
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    private double odds(int grade) {
        List<Double> l = plugin.getConfig().getDoubleList("mounts.odds");
        return grade < l.size() ? l.get(grade) : ODDS[grade];
    }

    public ItemStack token(Mount m) {
        ItemStack it = new ItemStack(Material.PAPER);   // 아이콘 = 탈것 3D 모델
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(Text.c(GRADE[m.grade].substring(0, 2) + "&l" + m.label));
        List<String> lore = new ArrayList<>(List.of(Text.c(GRADE[m.grade] + " &7탈것"), Text.c("&7속도 " + (int) (m.speed() * 300) + " · 점프 " + (int) (m.jump() * 100))));
        if (m.flies()) {
            lore.add(Text.c("&b✈ 비행 가능"));
            lore.add(Text.c("&7 점프(스페이스) → 이륙 · 바라보는 방향으로 비행"));
            lore.add(Text.c("&7 비행 중 다시 점프 → 착지"));
        }
        lore.add("");
        lore.add(Text.c("&e▶ 우클릭: 타기"));
        meta.setLore(lore);
        meta.setCustomModelData(9000 + kr.rpgcraft.boss.BossModelManager.ORDER.indexOf(m.model));
        meta.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, m.name());
        it.setItemMeta(meta);
        return it;
    }

    // ------------------------------------------------------------------ 뽑기 상점
    public void openShop(Player p) {
        Gui g = new Gui(3, "&8탈것 뽑기") {
        };
        long cost = plugin.getConfig().getLong("mounts.draw-cost", 3000000);
        List<String> lore = new ArrayList<>();
        lore.add("&7비용 " + Text.money(cost));
        lore.add("");
        for (int i = 0; i < 4; i++) {
            StringBuilder names = new StringBuilder();
            for (Mount m : Mount.values()) if (m.grade == i) names.append(names.length() > 0 ? ", " : "").append(m.label);
            lore.add(GRADE[i] + " &7" + String.format(odds(i) < 0.1 ? "%.1f" : "%.0f", odds(i) * 100) + "% &8(" + names + ")");
        }
        lore.add("");
        lore.add("&e▶ 클릭하여 뽑기");
        g.set(13, Gui.button(Material.SADDLE, "&6&l탈것 뽑기", lore.toArray(new String[0])), e -> draw(p));
        g.set(22, Gui.button(Material.BOOK, "&e&l탈것 도감 &f" + ownedCount(plugin.data().get(p)) + " / " + Mount.values().length, "&e▶ 클릭"), e -> openCollection(p));
        g.fill(0, 26);
        g.open(p);
    }

    private void draw(Player p) {
        long cost = plugin.getConfig().getLong("mounts.draw-cost", 3000000);
        if (!plugin.economy().take(p, cost)) { Text.actionBar(p, "&c돈이 부족합니다 (" + Text.money(cost) + ")"); return; }
        double r = ThreadLocalRandom.current().nextDouble(), acc = 0;
        int grade = 0;
        for (int i = 0; i < 4; i++) { acc += odds(i); if (r < acc) { grade = i; break; } }
        List<Mount> pool = new ArrayList<>();
        for (Mount m : Mount.values()) if (m.grade == grade) pool.add(m);
        Mount got = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        markOwned(p, got);
        for (ItemStack l : p.getInventory().addItem(token(got)).values()) p.getWorld().dropItemNaturally(p.getLocation(), l);
        p.playSound(p.getLocation(), grade >= 2 ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        p.sendTitle(Text.c(GRADE[grade]), Text.c("&f" + got.label), 5, 40, 10);
        if (grade == 3) Text.announce(Text.PREFIX + Text.c("&6&l" + Text.name(p) + "&f님이 전설 탈것 &6" + got.label + "&f을(를) 뽑았습니다!"));
        openShop(p);
    }

    // ------------------------------------------------------------------ 도감
    public boolean owns(kr.rpgcraft.data.PlayerData d, Mount m) {
        return d.counter("mount_own_" + m.name()) > 0;
    }

    public int ownedCount(kr.rpgcraft.data.PlayerData d) {
        int n = 0;
        for (Mount m : Mount.values()) if (owns(d, m)) n++;
        return n;
    }

    private void markOwned(Player p, Mount m) {
        var d = plugin.data().get(p);
        if (owns(d, m)) return;
        d.counters.put("mount_own_" + m.name(), 1.0);
        Text.actionBar(p, "&e탈것 도감에 &f" + m.label + "&e이(가) 등록되었습니다! &7(메뉴 → 도감)");
    }

    /** 가진 탈것 아이템을 도감에 등록 (도감이 생기기 전에 뽑은 탈것 포함) */
    private void scanTokens(Player p) {
        for (ItemStack[] inv : new ItemStack[][]{p.getInventory().getContents(), p.getEnderChest().getContents()})
            for (ItemStack it : inv) {
                if (it == null || !it.hasItemMeta()) continue;
                String id = it.getItemMeta().getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
                if (id == null) continue;
                try { markOwned(p, Mount.valueOf(id)); } catch (IllegalArgumentException ignored) { }
            }
    }

    @EventHandler
    public void onJoinScan(org.bukkit.event.player.PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline()) scanTokens(p); }, 40L);
    }

    /** 탈것 도감: 얻은 탈것은 모델 · 능력치, 얻지 못한 탈것은 가림 */
    public void openCollection(Player p) {
        scanTokens(p);
        var d = plugin.data().get(p);
        Gui g = new Gui(4, "&8탈것 도감") {
        };
        for (Mount m : Mount.values()) {
            int slot = 10 + m.grade * 2 + (m.ordinal() % 2) * 9;
            boolean own = owns(d, m);
            List<String> lore = new ArrayList<>();
            lore.add(GRADE[m.grade] + " &7탈것");
            lore.add("");
            if (own) {
                lore.add("&a이동 속도 &f" + (int) (m.speed() * 300));
                lore.add("&a점프력 &f" + (int) (m.jump() * 100));
                if (m.flies()) lore.add("&b✈ 비행 가능");
                lore.add("");
                lore.add("&a✔ 보유 &7(탈것 아이템을 우클릭해 타기)");
            } else {
                lore.add("&8능력치: ???");
                lore.add("");
                lore.add("&8미보유 — /탈것 에서 뽑을 수 있습니다");
            }
            ItemStack icon;
            if (own) {
                icon = Gui.button(Material.PAPER, GRADE[m.grade].substring(0, 2) + "&l" + m.label, lore.toArray(new String[0]));
                ItemMeta im = icon.getItemMeta();
                im.setCustomModelData(9000 + kr.rpgcraft.boss.BossModelManager.ORDER.indexOf(m.model));
                icon.setItemMeta(im);
            } else icon = Gui.button(Material.GRAY_DYE, "&8??? " + GRADE[m.grade].substring(0, 2) + "(" + m.label + ")", lore.toArray(new String[0]));
            g.set(slot, icon);
        }
        int n = ownedCount(d);
        g.set(4, Gui.button(Material.BOOK, "&e&l탈것 도감 &f" + n + " / " + Mount.values().length, "&7모은 탈것: " + (n * 100 / Mount.values().length) + "%",
                "&7얻지 못한 탈것의 능력치는 가려집니다"));
        g.set(27, Gui.button(Material.SADDLE, "&6탈것 뽑기", "&e▶ 클릭"), e -> openShop(p));
        g.set(31, Gui.button(Material.ARROW, "&f◀ 도감"), e -> plugin.menu().openCodex(p));
        g.fill(0, 35);
        g.open(p);
    }

    // ------------------------------------------------------------------ 타기 · 내리기
    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)) return;
        ItemStack it = e.getItem();
        if (it == null || !it.hasItemMeta()) return;
        String id = it.getItemMeta().getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
        if (id == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        // 한 번의 우클릭에 블록 클릭 + 허공 클릭 이벤트가 연달아 올 수 있다 → 쿨타임을 먼저 확인해야
        // 소환하자마자 두 번째 이벤트로 바로 내려버리는(나왔다가 바로 사라지는) 일이 없다
        if (plugin.data().get(p).onCooldown("mount")) return;
        plugin.data().get(p).cooldown("mount", 1000);
        if (riding.containsKey(p.getUniqueId())) { dismount(p); return; }
        if (plugin.dungeons() != null && plugin.dungeons().runOf(p) != null) { Text.actionBar(p, "&c던전에서는 탈 수 없습니다"); return; }
        Mount m;
        try { m = Mount.valueOf(id); } catch (IllegalArgumentException ex) { return; }
        markOwned(p, m);
        summon(p, m);
    }

    /** 모델 크기: 안장 높이가 탑승 좌석 높이(mounts.seat-height)에 오도록 탈것마다 다르게 */
    private float scaleOf(Mount m) {
        double seat = plugin.getConfig().getDouble("mounts.seat-height", 1.45);
        double sc = seat * 16 / m.saddle * plugin.getConfig().getDouble("mounts.scale-mult", 1.0);
        return (float) Math.max(1.2, Math.min(2.6, sc));
    }

    private void summon(Player p, Mount m) {
        if (p.isInsideVehicle()) p.leaveVehicle();
        Location l = p.getLocation();
        Horse h = l.getWorld().spawn(l, Horse.class, x -> {
            x.setTamed(true);
            x.setOwner(p);
            x.setAdult();
            x.setInvisible(true);
            x.setSilent(true);
            x.setInvulnerable(true);
            x.setPersistent(false);
            x.getInventory().setSaddle(new ItemStack(Material.SADDLE));
            x.setJumpStrength(m.jump());
            if (x.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED) != null) x.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(m.speed());
            x.getPersistentDataContainer().set(RIDE, PersistentDataType.STRING, p.getUniqueId().toString());
        });
        ItemDisplay d = l.getWorld().spawn(l, ItemDisplay.class, x -> {
            ItemStack model = new ItemStack(Material.PAPER);
            ItemMeta mm = model.getItemMeta();
            mm.setCustomModelData(9000 + kr.rpgcraft.boss.BossModelManager.ORDER.indexOf(m.model));
            model.setItemMeta(mm);
            x.setItemStack(model);
            x.setPersistent(false);
            float sc = scaleOf(m);
            // 모델은 말의 승객으로 태워 함께 부드럽게 움직인다 (1.20.1 에는 순간이동 보간이 없음).
            // 승객 위치 = 말 발밑 + ride-height(말 키 1.6 × 0.75). 아이템 모델은 중심이 원점이라 바닥이 -0.5 × 크기 →
            // 모델 바닥을 말 발밑(땅)에 맞추려면 y 이동 = -ride-height + 0.5 × 크기 (예전 값은 땅을 뚫고 들어갔음)
            float ty = (float) (-plugin.getConfig().getDouble("mounts.ride-height", 1.2) + 0.5 * sc + plugin.getConfig().getDouble("mounts.model-lift", 0.0));
            x.setTransformation(new Transformation(new Vector3f(0, ty, 0),
                    new AxisAngle4f((float) Math.PI, 0, 1, 0), new Vector3f(sc), new AxisAngle4f()));
            x.getPersistentDataContainer().set(RIDE, PersistentDataType.STRING, p.getUniqueId().toString());
        });
        if (!h.addPassenger(p)) {   // 다른 플러그인이 막았거나 탈 수 없는 상태
            h.remove();
            d.remove();
            Text.actionBar(p, "&c지금은 탈것에 탈 수 없습니다");
            return;
        }
        h.addPassenger(d);
        riding.put(p.getUniqueId(), new UUID[]{h.getUniqueId(), d.getUniqueId()});
        ridingType.put(p.getUniqueId(), m);
        mountedAt.put(p.getUniqueId(), System.currentTimeMillis());
        p.playSound(l, Sound.ENTITY_HORSE_SADDLE, 1f, 1f);
        l.getWorld().spawnParticle(Particle.CLOUD, l.add(0, 1, 0), 20, 0.6, 0.4, 0.6, 0.02);
        if (m.flies()) Text.actionBar(p, "&b✈ 점프(스페이스)로 이륙 · 바라보는 방향으로 비행 · 다시 점프하면 착지");
    }

    /** 매 틱: 모델을 말 방향에 맞춤, 전설 탈것 비행, 내렸으면 정리 */
    private void tick() {
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<UUID, UUID[]>> it = riding.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            UUID id = en.getKey();
            Player p = Bukkit.getPlayer(id);
            Entity h = Bukkit.getEntity(en.getValue()[0]), d = Bukkit.getEntity(en.getValue()[1]);
            boolean seated = p != null && h != null && h.getPassengers().contains(p);
            // 막 탄 직후(0.5초)에는 클라이언트 동기화 전이라 승객 목록이 비어 보일 수 있다 → 바로 지우지 않음
            if (!seated && p != null && h != null && d != null && now - mountedAt.getOrDefault(id, 0L) < 500) continue;
            if (!seated || d == null) {
                if (h != null) h.remove();
                if (d != null) d.remove();
                it.remove();
                forget(id);
                continue;
            }
            d.setRotation(h.getLocation().getYaw(), 0);   // 모델 방향 = 말 방향
            if (flying.contains(id)) fly(p, h, ridingType.get(id));
        }
    }

    /** 비행: 바라보는 방향으로 날아간다 (위를 보면 상승, 아래를 보면 하강). 땅에 닿으면서 아래를 보면 착지 */
    private void fly(Player p, Entity h, Mount m) {
        double sp = plugin.getConfig().getDouble("mounts.fly-speed", 0.75);
        org.bukkit.util.Vector dir = p.getLocation().getDirection();
        org.bukkit.util.Vector v = dir.multiply(sp);
        if (h.getLocation().getY() > h.getWorld().getMaxHeight() - 2 && v.getY() > 0) v.setY(0);
        h.setVelocity(v);
        h.setFallDistance(0);
        p.setFallDistance(0);
        if (h.isOnGround() && p.getLocation().getPitch() > 30) { land(p, h); return; }
        if (ThreadLocalRandom.current().nextInt(3) == 0)
            h.getWorld().spawnParticle(m == Mount.DRAGON ? Particle.DRAGON_BREATH : Particle.END_ROD, h.getLocation().add(0, 0.6, 0), 2, 0.6, 0.2, 0.6, 0.01);
    }

    private void takeOff(Player p, Entity h) {
        flying.add(p.getUniqueId());
        h.setGravity(false);
        h.setVelocity(new org.bukkit.util.Vector(0, 0.9, 0));
        h.getWorld().playSound(h.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1f, 1.2f);
        h.getWorld().spawnParticle(Particle.CLOUD, h.getLocation(), 30, 1, 0.2, 1, 0.05);
        Text.actionBar(p, "&b✈ 비행 중 &7— 바라보는 방향으로 이동, 점프하면 착지");
    }

    private void land(Player p, Entity h) {
        flying.remove(p.getUniqueId());
        h.setGravity(true);
        h.setFallDistance(0);
        Text.actionBar(p, "&7착지합니다");
    }

    /** 전설 탈것: 점프(스페이스 충전 후 놓기)로 이륙 / 비행 중 점프하면 착지 */
    @EventHandler
    public void onJump(org.bukkit.event.entity.HorseJumpEvent e) {
        Entity h = e.getEntity();
        String owner = h.getPersistentDataContainer().get(RIDE, PersistentDataType.STRING);
        if (owner == null) return;
        UUID id;
        try { id = UUID.fromString(owner); } catch (IllegalArgumentException ex) { return; }
        Mount m = ridingType.get(id);
        Player p = Bukkit.getPlayer(id);
        if (m == null || p == null || !m.flies()) return;
        e.setCancelled(true);
        if (flying.contains(id)) land(p, h);
        else takeOff(p, h);
    }

    private void forget(UUID id) {
        ridingType.remove(id);
        mountedAt.remove(id);
        flying.remove(id);
    }

    public void dismount(Player p) {
        UUID[] r = riding.remove(p.getUniqueId());
        forget(p.getUniqueId());
        if (r == null) return;
        Entity h = Bukkit.getEntity(r[0]), d = Bukkit.getEntity(r[1]);
        if (h != null) h.remove();
        if (d != null) d.remove();
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity().getPersistentDataContainer().has(RIDE, PersistentDataType.STRING)) e.setCancelled(true);
        // 탈것에 탄 채 떨어져도(비행 후 착지 등) 낙하 피해 없음
        else if (e.getCause() == EntityDamageEvent.DamageCause.FALL && riding.containsKey(e.getEntity().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        dismount(e.getPlayer());
    }

    public void cleanup() {
        for (UUID u : new ArrayList<>(riding.keySet())) { Player p = Bukkit.getPlayer(u); if (p != null) dismount(p); }
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (s instanceof Player p) openShop(p);
        return true;
    }
}
