package kr.rpgcraft.data;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.util.Text;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.List;
import java.util.UUID;

/**
 * 접속하지 않은 플레이어를 초기화했을 때, 다음 접속 때 마저 처리할 것 (v5.4.7).
 * 데이터는 바로 초기화되지만 인벤토리 · 엔더 상자 · 위치는 접속해 있어야 바꿀 수 있으므로 여기 적어 두었다가 접속하면 적용.
 */
public final class ResetPending {
    private ResetPending() {}

    private static File file(RpgCraft pl) {
        return new File(pl.getDataFolder(), "reset_pending.yml");
    }

    public static void mark(RpgCraft pl, UUID id) {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file(pl));
        List<String> l = new java.util.ArrayList<>(y.getStringList("players"));
        if (!l.contains(id.toString())) l.add(id.toString());
        y.set("players", l);
        try { y.save(file(pl)); } catch (java.io.IOException ignored) { }
    }

    /** 대기 중이면 목록에서 지우고 true */
    public static boolean consume(RpgCraft pl, UUID id) {
        File f = file(pl);
        if (!f.exists()) return false;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        List<String> l = new java.util.ArrayList<>(y.getStringList("players"));
        if (!l.remove(id.toString())) return false;
        y.set("players", l);
        try { y.save(f); } catch (java.io.IOException ignored) { }
        return true;
    }

    /** 인벤토리 · 엔더 상자 비우고 기본 지급품, 체력 가득, 스폰으로 */
    public static void freshStart(RpgCraft pl, Player p) {
        PlayerData d = pl.data().get(p);
        p.getInventory().clear();
        p.getEnderChest().clear();
        for (String k : pl.getConfig().getStringList("player.starter-kit")) {
            String[] kv = k.split(":");
            ItemStack it = pl.items().create(kv[0], kv.length > 1 ? Text.parseInt(kv[1], 1) : 1);
            if (it != null) p.getInventory().addItem(it);
        }
        pl.stats().refresh(p);
        d.hp = d.stats.maxHp;
        p.teleport(p.getWorld().getSpawnLocation());
        p.sendTitle(Text.c("&c&l초기화"), Text.c("&f처음부터 다시 시작합니다"), 10, 50, 10);
    }
}
