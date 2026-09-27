package kr.rpgcraft.economy;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.data.PlayerData;
import kr.rpgcraft.util.Text;
import org.bukkit.entity.Player;

import java.util.UUID;

/** 원(₩) 화폐. 소지금은 PlayerData.money 에 저장된다. */
public class Economy {
    private final RpgCraft plugin;

    public Economy(RpgCraft plugin) {
        this.plugin = plugin;
    }

    public long balance(Player p) {
        return plugin.data().get(p).money;
    }

    public boolean has(Player p, long amount) {
        return balance(p) >= amount;
    }

    public void give(Player p, long amount) {
        if (amount <= 0) return;
        give(p.getUniqueId(), amount);
        PlayerData d = plugin.data().get(p);
        if (d.actionBarLock < System.currentTimeMillis()) {
            Text.actionBar(p, "&e+" + Text.money(amount));
            d.actionBarLock = System.currentTimeMillis() + 800;
        }
    }

    public void give(UUID id, long amount) {
        if (amount <= 0) return;
        PlayerData d = plugin.data().get(id);
        d.money = Math.min(Long.MAX_VALUE / 4, d.money + amount);
    }

    public boolean take(Player p, long amount) {
        if (amount < 0) return false;
        PlayerData d = plugin.data().get(p);
        if (d.money < amount) return false;
        d.money -= amount;
        return true;
    }

    public void set(PlayerData d, long amount) {
        d.money = Math.max(0, amount);
    }
}
