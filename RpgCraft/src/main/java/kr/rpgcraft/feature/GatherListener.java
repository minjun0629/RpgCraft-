package kr.rpgcraft.feature;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.data.PlayerData;
import kr.rpgcraft.item.Category;
import kr.rpgcraft.item.ItemData;
import kr.rpgcraft.item.ItemTemplate;
import kr.rpgcraft.passive.Passive;
import kr.rpgcraft.util.Text;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 채집: 채집도구를 들고 채집 포인트(약초/나무/광석 블록)를 부수면 블록은 그대로 남고 재료를 얻는다.
 * 도구 등급별 쿨타임 3/2/1분(나무위키 4R), 상위 도구일수록 희귀 재료 확률 증가.
 */
public class GatherListener implements Listener {
    private final RpgCraft plugin;

    public GatherListener(RpgCraft plugin) {
        this.plugin = plugin;
    }

    private static final java.util.Set<String> STONES = java.util.Set.of("STONE", "COBBLESTONE", "MOSSY_COBBLESTONE", "DEEPSLATE", "COBBLED_DEEPSLATE",
            "GRANITE", "DIORITE", "ANDESITE", "TUFF", "CALCITE", "DRIPSTONE_BLOCK", "BLACKSTONE", "BASALT", "NETHERRACK", "END_STONE",
            "SANDSTONE", "RED_SANDSTONE", "SMOOTH_BASALT", "AMETHYST_BLOCK", "RAW_IRON_BLOCK", "RAW_COPPER_BLOCK");
    private static final java.util.Set<String> SOILS = java.util.Set.of("DIRT", "GRASS_BLOCK", "COARSE_DIRT", "PODZOL", "ROOTED_DIRT", "MYCELIUM",
            "MUD", "DIRT_PATH", "MOSS_BLOCK", "MUDDY_MANGROVE_ROOTS", "FARMLAND", "CRIMSON_NYLIUM", "WARPED_NYLIUM");

    /**
     * 채집 포인트 종류. config 목록에 없어도 이름으로 판별 (v5.10.35: 화강암 · 안산암 · 섬록암 · 조약돌 · 응회암 · 구리/석탄/레드스톤 광석 ·
     * 나무껍질(_WOOD) · 벗긴 원목 · 진홍/뒤틀린 줄기 등이 채집되지 않던 버그 — 서버의 config.yml 은 예전 목록이 남아 있어서 코드에서 처리)
     */
    private String nodeOf(Material m) {
        ConfigurationSection nodes = plugin.getConfig().getConfigurationSection("gather.nodes");
        if (nodes != null) {
            for (String k : nodes.getKeys(false)) {
                for (String b : nodes.getStringList(k + ".blocks")) {
                    Material bm = Material.matchMaterial(b);
                    if (bm == m) return k;
                }
            }
        }
        String n = m.name();
        if (n.endsWith("_LOG") || n.endsWith("_WOOD") || n.endsWith("_STEM") || n.endsWith("_HYPHAE")) return has(nodes, "wood");
        if (n.endsWith("_ORE") || STONES.contains(n)) return has(nodes, "ore");
        if (SOILS.contains(n)) return has(nodes, "herb");
        return null;
    }

    private static String has(ConfigurationSection nodes, String k) {
        return nodes == null || nodes.isConfigurationSection(k) ? k : null;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)   // v5.10.35 다른 보호 처리보다 먼저 (보호 구역 안의 채집 포인트도 채집)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        ItemStack tool = p.getInventory().getItemInMainHand();
        ItemTemplate t = ItemData.template(tool);
        if (t == null || t.category != Category.TOOL || t.id.startsWith("rod_")) return;
        Block b = e.getBlock();
        if (plugin.wars().castleAt(b.getLocation()) != null) return;
        String node = nodeOf(b.getType());
        if (node == null) return;
        e.setCancelled(true);
        PlayerData d = plugin.data().get(p);
        if (d.onCooldown("gather")) {
            long s = d.remaining("gather") / 1000 + 1;
            Text.actionBar(p, "&c채집 쿨타임 " + Text.time(s));
            return;
        }
        java.util.List<Integer> cds = plugin.getConfig().getIntegerList("gather.cooldown-by-tier");
        int sec = cds.size() >= 3 ? cds.get(Math.max(0, Math.min(2, t.tier - 1))) : new int[]{180, 150, 120}[Math.max(0, Math.min(2, t.tier - 1))];
        var ms = plugin.mastery();   // v5.10.30 채집 숙련
        d.cooldown("gather", (long) (sec * 1000L * (ms != null ? ms.gatherCooldownMult(d) : 1)));
        d.counters.merge("gather_count", 1.0, Double::sum);
        if (plugin.questNpcs() != null) plugin.questNpcs().onGather(p);
        ConfigurationSection drops = plugin.getConfig().getConfigurationSection("gather.nodes." + node + ".drops");
        String id = roll(drops, t.tier + (ms != null ? ms.gatherTierBonus(d) : 0));
        int amount = 1 + (t.tier >= 3 && ThreadLocalRandom.current().nextDouble() < 0.3 ? 1 : 0)
                + (ms != null && ThreadLocalRandom.current().nextDouble() < ms.gatherExtraChance(d) ? 1 : 0);
        if (ms != null) ms.add(p, MasteryManager.Life.GATHER, plugin.getConfig().getDouble("mastery.gather-exp", 8));   // v5.10.44 40 → 8 (거의 캘 때마다 오르던 문제)
        if (id != null) give(p, plugin.items().create(id, amount));
        double shardChance = plugin.getConfig().getDouble("gather.shard-chance", 0.02);
        if (ThreadLocalRandom.current().nextDouble() < shardChance) {
            String shard = node.equals("ore") ? "shard_earth" : "shard_nature";
            give(p, plugin.items().create(shard, 1));
            Text.msg(p, "&d기운 파편을 발견했습니다! &f" + plugin.items().get(shard).name);
        }
        if (d.has(Passive.MOON_GATHERER) && d.counter("moon_gather") < 8000) d.addCounter("moon_gather", 100);
        plugin.passives().track(p, "gathers", 1);
        b.getWorld().spawnParticle(Particle.BLOCK_CRACK, b.getLocation().add(0.5, 0.5, 0.5), 25, 0.3, 0.3, 0.3, b.getBlockData());
        p.playSound(b.getLocation(), node.equals("ore") ? Sound.BLOCK_STONE_BREAK : node.equals("wood") ? Sound.BLOCK_WOOD_BREAK : Sound.BLOCK_GRASS_BREAK, 1f, 1f);
        if (id != null) Text.actionBar(p, "&a채집 성공: &f" + plugin.items().get(id).name + " x" + amount + " &7(다음 채집 " + Text.time(t.cooldown) + ")");
    }

    /** 가중치 표에서 뽑기. 상위 도구는 가중치가 낮은(희귀) 재료의 확률을 올린다. */
    private String roll(ConfigurationSection drops, int tier) {
        if (drops == null) return null;
        Map<String, Double> w = new LinkedHashMap<>();
        double max = 0;
        for (String k : drops.getKeys(false)) max = Math.max(max, drops.getDouble(k));
        double total = 0;
        for (String k : drops.getKeys(false)) {
            double v = drops.getDouble(k);
            if (v < max) v *= 1 + 0.6 * (tier - 1);
            w.put(k, v);
            total += v;
        }
        double r = ThreadLocalRandom.current().nextDouble() * total;
        for (Map.Entry<String, Double> en : w.entrySet()) {
            r -= en.getValue();
            if (r <= 0) return en.getKey();
        }
        return null;
    }

    private void give(Player p, ItemStack it) {
        if (it == null) return;
        for (ItemStack left : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
    }
}
