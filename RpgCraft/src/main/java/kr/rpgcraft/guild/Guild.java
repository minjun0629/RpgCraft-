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
    /** v5.10.20 길드 경험치 (길드원 사냥 · 보스 · 공성전 · 성 수입) — 레벨업에 필요 */
    public long exp;
    /** v5.10.20 길드 스킬 단계 (스킬 키 → 단계) */
    public final Map<String, Integer> skills = new LinkedHashMap<>();
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

    public int skill(String key) {
        return skills.getOrDefault(key, 0);
    }

    /** 레벨마다 스킬 포인트 2 */
    public int skillPoints() {
        int used = 0;
        for (int v : skills.values()) used += v;
        return level * 2 - used;
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
