package kr.rpgcraft.world;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.mob.MobManager;
import kr.rpgcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * /허수아비 : 데미지 테스트용 허수아비 (1인 1개, 10분 뒤 사라짐)
 * 절대 죽지 않고, 머리 위에 마지막 피해 · 최근 10초 DPS · 누적 피해를 표시한다. 보상은 없다.
 */
public class DummyManager implements Listener {
    private static class Rec {
        UUID entity;
        final Deque<double[]> hits = new ArrayDeque<>();   // {시각, 피해}
        double last, total;
    }

    private final RpgCraft plugin;
    private final NamespacedKey KEY;
    private final Map<UUID, Rec> byOwner = new HashMap<>();
    private final Map<UUID, UUID> ownerOf = new HashMap<>();

    public DummyManager(RpgCraft plugin) {
        this.plugin = plugin;
        this.KEY = new NamespacedKey(plugin, "dummy");
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
    }

    public boolean isDummy(Entity e) {
        return e != null && e.getPersistentDataContainer().has(KEY, PersistentDataType.STRING);
    }

    public void spawn(Player p) {
        remove(p.getUniqueId());
        Location l = p.getLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(3));
        l.setY(p.getWorld().getHighestBlockYAt(l, org.bukkit.HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1);
        l.setYaw(p.getLocation().getYaw() + 180);
        Zombie z = p.getWorld().spawn(l, Zombie.class, x -> {
            x.setAI(false);
            x.setSilent(true);
            x.setAdult();
            x.setPersistent(false);
            x.setRemoveWhenFarAway(false);
            x.getEquipment().setHelmet(new ItemStack(Material.CARVED_PUMPKIN));
            x.getEquipment().setChestplate(new ItemStack(Material.LEATHER_CHESTPLATE));
            x.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, p.getUniqueId().toString());
        });
        MobManager.MobState s = plugin.mobs().initCustom(z, plugin.data().get(p).level, 1e15, 0, 0, 0, 0, "허수아비");
        s.hp = s.maxHp;
        z.setCustomNameVisible(true);
        z.setCustomName(Text.c("&e허수아비 &7(때려 보세요)"));
        Rec r = new Rec();
        r.entity = z.getUniqueId();
        byOwner.put(p.getUniqueId(), r);
        ownerOf.put(z.getUniqueId(), p.getUniqueId());
        Bukkit.getScheduler().runTaskLater(plugin, () -> remove(p.getUniqueId(), z.getUniqueId()), 20L * 60 * plugin.getConfig().getLong("dummy.minutes", 10));
        Text.msg(p, "&a허수아비를 세웠습니다. &7(" + plugin.getConfig().getLong("dummy.minutes", 10) + "분 뒤 사라짐)");
    }

    /** HealthManager 에서 호출: 피해 기록 (허수아비는 죽지 않음) */
    public void record(LivingEntity e, double amount) {
        UUID owner = ownerOf.get(e.getUniqueId());
        Rec r = owner == null ? null : byOwner.get(owner);
        if (r == null) return;
        long now = System.currentTimeMillis();
        r.hits.addLast(new double[]{now, amount});
        r.last = amount;
        r.total += amount;
        MobManager.MobState s = plugin.mobs().peek(e);
        if (s != null) s.hp = s.maxHp;
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Rec> en : byOwner.entrySet()) {
            Rec r = en.getValue();
            Entity e = Bukkit.getEntity(r.entity);
            if (!(e instanceof LivingEntity le)) continue;
            while (!r.hits.isEmpty() && now - r.hits.peekFirst()[0] > 10_000) r.hits.pollFirst();
            double sum = 0;
            for (double[] h : r.hits) sum += h[1];
            double span = r.hits.isEmpty() ? 1 : Math.max(1, (now - r.hits.peekFirst()[0]) / 1000.0);
            double dps = r.hits.isEmpty() ? 0 : sum / span;
            le.setCustomName(Text.c("&e허수아비 &f| 마지막 &c" + Text.num(r.last) + " &f| DPS &6" + Text.num(dps) + " &f| 누적 &7" + Text.num(r.total)));
        }
    }

    private void remove(UUID owner) {
        Rec r = byOwner.remove(owner);
        if (r == null) return;
        ownerOf.remove(r.entity);
        Entity e = Bukkit.getEntity(r.entity);
        if (e != null) e.remove();
    }

    private void remove(UUID owner, UUID entity) {
        Rec r = byOwner.get(owner);
        if (r != null && r.entity.equals(entity)) remove(owner);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        remove(e.getPlayer().getUniqueId());
    }
}
