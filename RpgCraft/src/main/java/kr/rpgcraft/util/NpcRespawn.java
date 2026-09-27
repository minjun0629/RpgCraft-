package kr.rpgcraft.util;

import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.function.Consumer;

/**
 * 한 번 배치한 NPC 가 사라졌으면 기록된 자리에 다시 세운다.
 * 1.17+ 는 청크를 불러와도 엔티티가 늦게 올라오므로, 청크를 붙잡아 두고 엔티티가 로드된 뒤에 확인한다 (중복 생성 방지).
 */
public final class NpcRespawn {
    private NpcRespawn() {}

    /**
     * @param spawn 없을 때 실행: 인자 = 땅 표면 블록 (그 위에 세우면 됨)
     * @param done  확인이 끝나면 true(다시 세움) / false(이미 있음)
     */
    public static void ensure(Plugin plugin, World w, int x, int z, NamespacedKey key, String id, Consumer<Block> spawn, Consumer<Boolean> done) {
        Chunk c = w.getChunkAt(x >> 4, z >> 4);
        c.addPluginChunkTicket(plugin);
        new org.bukkit.scheduler.BukkitRunnable() {
            int tries;

            @Override
            public void run() {
                if (!c.isEntitiesLoaded() && ++tries < 20) return;   // 최대 10초 대기
                cancel();
                boolean found = false;
                for (Entity e : c.getEntities())
                    if (id.equals(e.getPersistentDataContainer().get(key, PersistentDataType.STRING))) { found = true; break; }
                if (!found) spawn.accept(Locs.surface(w, new org.bukkit.Location(w, x + 0.5, 64, z + 0.5)));
                c.removePluginChunkTicket(plugin);
                if (done != null) done.accept(!found);
            }
        }.runTaskTimer(plugin, 10L, 10L);
    }
}
