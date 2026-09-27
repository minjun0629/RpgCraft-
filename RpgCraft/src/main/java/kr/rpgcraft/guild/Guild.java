package kr.rpgcraft.guild;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.*;

public class Guild implements InventoryHolder {
    public final String name;
    public UUID leader;
    public final Set<UUID> members = new LinkedHashSet<>();
    public int level = 1;
    public long bank;
    public final List<String> totems = new ArrayList<>();
    private Inventory storage;

    public Guild(String name, UUID leader) {
        this.name = name;
        this.leader = leader;
        members.add(leader);
    }

    public boolean isLeader(UUID id) {
        return leader.equals(id);
    }

    public int maxMembers() {
        return 5 + level * 3;
    }

    public int totemSlots() {
        return level;
    }

    public int storageRows() {
        return Math.min(6, 1 + level);
    }

    public Inventory storage() {
        if (storage == null || storage.getSize() != storageRows() * 9) {
            Inventory n = Bukkit.createInventory(this, storageRows() * 9, "§8[길드 창고] " + name);
            if (storage != null) {
                for (int i = 0; i < Math.min(storage.getSize(), n.getSize()); i++) n.setItem(i, storage.getItem(i));
            }
            storage = n;
        }
        return storage;
    }

    @Override
    public Inventory getInventory() {
        return storage();
    }

    public List<Player> online() {
        List<Player> out = new ArrayList<>();
        for (UUID id : members) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) out.add(p);
        }
        return out;
    }

    public void broadcast(String msg) {
        for (Player p : online()) p.sendMessage(msg);
    }
}
