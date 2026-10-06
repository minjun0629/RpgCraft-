package io.versaera.platform.bukkit.listener;

import io.versaera.application.GameServices;
import io.versaera.domain.npc.NpcDefinition;
import io.versaera.domain.npc.Relation;
import io.versaera.platform.bukkit.Async;
import io.versaera.platform.bukkit.Ui;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * NPC: 관리자가 세운 주민 엔티티에 NPC id 를 붙여 둔다. 우클릭 = 대화 → 그날 첫 대화면 호감 +, 관계 단계에 맞는 한 줄.
 * NPC 는 AI 를 끄고 서 있으며 매 틱 갱신하지 않는다 (일과에 따른 이동은 NPC-02, 아직 PLANNED).
 */
public final class NpcListener implements Listener {
    private final GameServices s;
    private final Async async;
    private final NamespacedKey key;

    public NpcListener(Plugin plugin, GameServices s, Async async) {
        this.s = s;
        this.async = async;
        this.key = new NamespacedKey(plugin, "npc");
    }

    public Villager spawn(NpcDefinition n, Location at) {
        return at.getWorld().spawn(at, Villager.class, v -> {
            v.setAI(false);
            v.setInvulnerable(true);
            v.setSilent(true);
            v.setPersistent(true);
            v.setRemoveWhenFarAway(false);
            v.setCustomName(Ui.c("&f" + n.name() + " &7" + n.job()));
            v.setCustomNameVisible(true);
            v.getPersistentDataContainer().set(key, PersistentDataType.STRING, n.id());
        });
    }

    private String npcId(Entity e) {
        return e.getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onTalk(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        String id = npcId(e.getRightClicked());
        if (id == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        String uuid = p.getUniqueId().toString();
        async.run("talk", () -> {
            NpcDefinition n = s.relations.npc(id);
            int gain = s.relations.talk(uuid, id);
            s.exploration.discover(uuid, p.getName(), "npc", id);
            return new Object[]{n, s.relations.affinity(uuid, id), gain};
        }, r -> {
            NpcDefinition n = (NpcDefinition) r[0];
            int aff = (int) r[1], gain = (int) r[2];
            p.sendMessage(Ui.c("&f" + n.name() + "&7: " + line(n, aff)));
            p.sendMessage(Ui.c("&8" + Relation.tierName(aff) + " " + aff + (gain > 0 ? " &a+" + gain : "")));
        }, p);
    }

    /** 관계 단계에 따라 다른 한 줄 (짧게) */
    private static String line(NpcDefinition n, int aff) {
        return switch (Relation.tier(aff)) {
            case 0, 1 -> "...볼일 없으면 가 보시오.";
            case 2 -> "처음 보는 얼굴이군.";
            case 3 -> "또 왔군. " + n.job() + " 일은 오늘도 바쁘네.";
            case 4 -> "자네라면 믿고 맡길 만하지.";
            default -> "자네 덕에 이 동네가 살아났어.";
        };
    }

    @EventHandler(ignoreCancelled = true)
    public void onHurt(EntityDamageEvent e) {
        if (npcId(e.getEntity()) != null) e.setCancelled(true);
    }
}
