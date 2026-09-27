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
        WOLF("mount_wolf", "잿빛 늑대", 0, 0.27, 0.6), LIZARD("mount_lizard", "사막 도마뱀", 0, 0.28, 0.55),
        WARHORSE("mount_warhorse", "기사의 군마", 1, 0.31, 0.75), ICEBEAR("mount_icebear", "빙하 곰", 1, 0.30, 0.7),
        LION("mount_lion", "불꽃 사자", 2, 0.34, 0.85), PANTHER("mount_panther", "그림자 표범", 2, 0.35, 0.8),
        GRIFFIN("mount_griffin", "황금 그리핀", 3, 0.38, 1.0), DRAGON("mount_dragon", "심연의 용", 3, 0.39, 1.0);

        public final String model, label;
        public final int grade;
        public final double speed, jump;

        Mount(String model, String label, int grade, double speed, double jump) {
            this.model = model;
            this.label = label;
            this.grade = grade;
            this.speed = speed;
            this.jump = jump;
        }
    }

    private static final String[] GRADE = {"&f일반", "&9희귀", "&5영웅", "&6전설"};
    private static final double[] ODDS = {0.62, 0.26, 0.10, 0.02};

    private final RpgCraft plugin;
    private final NamespacedKey KEY, RIDE;
    private final Map<UUID, UUID[]> riding = new HashMap<>();   // 플레이어 → {말, 모델}

    public MountManager(RpgCraft plugin) {
        this.plugin = plugin;
        this.KEY = new NamespacedKey(plugin, "mount");
        this.RIDE = new NamespacedKey(plugin, "mount_entity");
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public ItemStack token(Mount m) {
        ItemStack it = new ItemStack(Material.PAPER);   // 아이콘 = 탈것 3D 모델
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(Text.c(GRADE[m.grade].substring(0, 2) + "&l" + m.label));
        meta.setLore(List.of(Text.c(GRADE[m.grade] + " &7탈것"), Text.c("&7속도 " + (int) (m.speed * 300) + " · 점프 " + (int) (m.jump * 100)), "", Text.c("&e▶ 우클릭: 타기")));
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
            lore.add(GRADE[i] + " &7" + String.format("%.0f", ODDS[i] * 100) + "% &8(" + names + ")");
        }
        lore.add("");
        lore.add("&e▶ 클릭하여 뽑기");
        g.set(13, Gui.button(Material.SADDLE, "&6&l탈것 뽑기", lore.toArray(new String[0])), e -> draw(p));
        g.fill(0, 26);
        g.open(p);
    }

    private void draw(Player p) {
        long cost = plugin.getConfig().getLong("mounts.draw-cost", 3000000);
        if (!plugin.economy().take(p, cost)) { Text.actionBar(p, "&c돈이 부족합니다 (" + Text.money(cost) + ")"); return; }
        double r = ThreadLocalRandom.current().nextDouble(), acc = 0;
        int grade = 0;
        for (int i = 0; i < 4; i++) { acc += ODDS[i]; if (r < acc) { grade = i; break; } }
        List<Mount> pool = new ArrayList<>();
        for (Mount m : Mount.values()) if (m.grade == grade) pool.add(m);
        Mount got = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
        for (ItemStack l : p.getInventory().addItem(token(got)).values()) p.getWorld().dropItemNaturally(p.getLocation(), l);
        p.playSound(p.getLocation(), grade >= 2 ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        p.sendTitle(Text.c(GRADE[grade]), Text.c("&f" + got.label), 5, 40, 10);
        if (grade == 3) Text.announce(Text.PREFIX + Text.c("&6&l" + p.getName() + "&f님이 전설 탈것 &6" + got.label + "&f을(를) 뽑았습니다!"));
        openShop(p);
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
        if (riding.containsKey(p.getUniqueId())) { dismount(p); return; }
        if (plugin.dungeons() != null && plugin.dungeons().runOf(p) != null) { Text.actionBar(p, "&c던전에서는 탈 수 없습니다"); return; }
        if (plugin.data().get(p).onCooldown("mount")) return;
        plugin.data().get(p).cooldown("mount", 2000);
        Mount m;
        try { m = Mount.valueOf(id); } catch (IllegalArgumentException ex) { return; }
        summon(p, m);
    }

    private void summon(Player p, Mount m) {
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
            x.setJumpStrength(m.jump);
            if (x.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED) != null) x.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(m.speed);
            x.getPersistentDataContainer().set(RIDE, PersistentDataType.STRING, p.getUniqueId().toString());
        });
        ItemDisplay d = l.getWorld().spawn(l, ItemDisplay.class, x -> {
            ItemStack model = new ItemStack(Material.PAPER);
            ItemMeta mm = model.getItemMeta();
            mm.setCustomModelData(9000 + kr.rpgcraft.boss.BossModelManager.ORDER.indexOf(m.model));
            model.setItemMeta(mm);
            x.setItemStack(model);
            x.setPersistent(false);
            float sc = (float) plugin.getConfig().getDouble("mounts.scale", 2.4);
            // 1.20.1 에는 순간이동 보간이 없으므로, 모델을 말의 승객으로 태워 함께 부드럽게 움직이게 한다 (좌석 높이만큼 아래로)
            x.setTransformation(new Transformation(new Vector3f(0, (float) -plugin.getConfig().getDouble("mounts.seat-offset", 1.35), 0),
                    new AxisAngle4f((float) Math.PI, 0, 1, 0), new Vector3f(sc), new AxisAngle4f()));
            x.getPersistentDataContainer().set(RIDE, PersistentDataType.STRING, p.getUniqueId().toString());
        });
        h.addPassenger(p);   // 조종은 첫 번째 승객(플레이어)
        h.addPassenger(d);
        riding.put(p.getUniqueId(), new UUID[]{h.getUniqueId(), d.getUniqueId()});
        p.playSound(l, Sound.ENTITY_HORSE_SADDLE, 1f, 1f);
        l.getWorld().spawnParticle(Particle.CLOUD, l.add(0, 1, 0), 20, 0.6, 0.4, 0.6, 0.02);
    }

    /** 매 틱: 모델을 말 위치 · 방향에 맞춤, 내렸으면 정리 */
    private void tick() {
        for (Iterator<Map.Entry<UUID, UUID[]>> it = riding.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            Player p = Bukkit.getPlayer(en.getKey());
            Entity h = Bukkit.getEntity(en.getValue()[0]), d = Bukkit.getEntity(en.getValue()[1]);
            if (p == null || h == null || d == null || !h.getPassengers().contains(p)) {
                if (h != null) h.remove();
                if (d != null) d.remove();
                it.remove();
                continue;
            }
            d.setRotation(h.getLocation().getYaw(), 0);   // 모델 방향 = 말 방향
        }
    }

    public void dismount(Player p) {
        UUID[] r = riding.remove(p.getUniqueId());
        if (r == null) return;
        Entity h = Bukkit.getEntity(r[0]), d = Bukkit.getEntity(r[1]);
        if (h != null) h.remove();
        if (d != null) d.remove();
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity().getPersistentDataContainer().has(RIDE, PersistentDataType.STRING)) e.setCancelled(true);
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
