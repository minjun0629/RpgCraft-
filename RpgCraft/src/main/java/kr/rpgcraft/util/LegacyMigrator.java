package kr.rpgcraft.util;

import kr.rpgcraft.RpgCraft;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 이전 이름(MCAge4R) 에서 RpgCraft 로 옮겨오기.
 * - plugins/MCAge4R 데이터 폴더 → plugins/RpgCraft (새 폴더가 없을 때 1회 복사)
 * - 아이템/NPC 의 PDC 키 네임스페이스 mcage4r:* → rpgcraft:* (접속 시 · 엔티티 로드 시 자동 변환)
 */
public class LegacyMigrator implements Listener {
    public static final String LEGACY_NS = "mcage4r";
    private static final PersistentDataType<?, ?>[] TYPES = {PersistentDataType.STRING, PersistentDataType.INTEGER,
            PersistentDataType.DOUBLE, PersistentDataType.LONG, PersistentDataType.BYTE, PersistentDataType.FLOAT, PersistentDataType.SHORT};

    private final RpgCraft plugin;

    public LegacyMigrator(RpgCraft plugin) {
        this.plugin = plugin;
    }

    /** onEnable 맨 처음에 호출 */
    public static void migrateDataFolder(RpgCraft plugin) {
        File now = plugin.getDataFolder();
        File old = new File(now.getParentFile(), "MCAge4R");
        if (now.exists() || !old.isDirectory()) return;
        try {
            Path src = old.toPath(), dst = now.toPath();
            List<Path> all = new ArrayList<>();
            try (var walk = Files.walk(src)) {
                walk.forEach(all::add);
            }
            for (Path p : all) {
                if (p.getFileName().toString().equals("resourcepack.zip")) continue; // 팩은 항상 최신 내장본 사용
                Path t = dst.resolve(src.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(t);
                else Files.copy(p, t, StandardCopyOption.REPLACE_EXISTING);
            }
            plugin.getLogger().info("이전 데이터 폴더(plugins/MCAge4R)를 plugins/RpgCraft 로 복사했습니다. 확인 후 이전 폴더는 지워도 됩니다.");
        } catch (IOException e) {
            plugin.getLogger().warning("이전 데이터 복사 실패: " + e.getMessage());
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static boolean migrate(PersistentDataContainer pdc, RpgCraft plugin) {
        boolean changed = false;
        for (NamespacedKey k : new ArrayList<>(pdc.getKeys())) {
            if (!LEGACY_NS.equals(k.getNamespace())) continue;
            NamespacedKey nk = new NamespacedKey(plugin, k.getKey());
            for (PersistentDataType t : TYPES) {
                if (pdc.has(k, t)) {
                    Object v = pdc.get(k, t);
                    if (v != null && !pdc.has(nk, t)) pdc.set(nk, t, v);
                    break;
                }
            }
            pdc.remove(k);
            changed = true;
        }
        return changed;
    }

    public boolean migrate(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return false;
        ItemMeta m = it.getItemMeta();
        if (!migrate(m.getPersistentDataContainer(), plugin)) return false;
        it.setItemMeta(m);
        return true;
    }

    public int migrate(ItemStack[] items) {
        int n = 0;
        if (items == null) return 0;
        for (ItemStack it : items) if (migrate(it)) n++;
        return n;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        int n = migrate(p.getInventory().getContents()) + migrate(p.getEnderChest().getContents()) + migrate(plugin.data().get(p).runes);
        if (n > 0) plugin.getLogger().info(p.getName() + " 의 이전 버전 아이템 " + n + "개를 RpgCraft 형식으로 변환했습니다.");
    }

    @EventHandler
    public void onEntities(EntitiesLoadEvent e) {
        for (Entity en : e.getEntities()) migrate(en.getPersistentDataContainer(), plugin);
    }
}
