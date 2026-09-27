package kr.rpgcraft.world;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.data.PlayerData;
import kr.rpgcraft.gui.Gui;
import kr.rpgcraft.item.ItemData;
import kr.rpgcraft.stat.Stat;
import kr.rpgcraft.stat.StatMap;
import kr.rpgcraft.util.Text;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Random;

/**
 * (숨김) 맵 먼 곳의 이름 없는 NPC 6명. 설명은 하지 않는다.
 */
public class HiddenJobManager implements Listener {
    public record Tier(String line, int tier, String label, String[] need, StatMap bonus) {}

    public static final List<Tier> TIERS = List.of(
            new Tier("A", 1, "망령 사냥꾼", new String[]{"#lv:50", "spirit_summon:1", "loot_dust:50"}, StatMap.of(Stat.LIFESTEAL, 3, Stat.MAGIC, 300)),
            new Tier("A", 2, "영혼 수확자", new String[]{"#lv:120", "#ach_boss:10", "loot_eye:10"}, StatMap.of(Stat.LIFESTEAL, 6, Stat.MAGIC, 900, Stat.HP_PCT, 8)),
            new Tier("A", 3, "명계의 군주", new String[]{"#lv:200", "#ach_boss:30", "loot_core:30"}, StatMap.of(Stat.LIFESTEAL, 10, Stat.MAGIC, 2000, Stat.HP_PCT, 15)),
            new Tier("B", 1, "별빛 방랑자", new String[]{"#lv:50", "#ach_treasure:10", "loot_frost:30"}, StatMap.of(Stat.CRIT, 8, Stat.SPEED, 8)),
            new Tier("B", 2, "성운 기사", new String[]{"#lv:120", "#ach_elite:300", "loot_scale:30"}, StatMap.of(Stat.CRIT, 12, Stat.CRIT_DMG, 40, Stat.DODGE, 6)),
            new Tier("B", 3, "천구의 주재자", new String[]{"#lv:200", "#ach_boss:30", "loot_crown:10"}, StatMap.of(Stat.CRIT, 18, Stat.CRIT_DMG, 90, Stat.DODGE, 10)));

    private final RpgCraft plugin;
    private final NamespacedKey KEY;
    private final File file;
    private final YamlConfiguration data;

    public HiddenJobManager(RpgCraft plugin) {
        this.plugin = plugin;
        this.KEY = new NamespacedKey(plugin, "hj_npc");
        this.file = new File(plugin.getDataFolder(), "hidden_jobs.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        Bukkit.getScheduler().runTaskLater(plugin, this::ensurePlaced, 260L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> respawnMissing(null), 460L);   // 사라진 NPC 는 원래 자리에 다시
    }

    public static Tier of(PlayerData d) {
        String line = d.counters.containsKey("hj_line_A") ? "A" : d.counters.containsKey("hj_line_B") ? "B" : null;
        if (line == null) return null;
        int t = (int) d.counter("hj_tier");
        for (Tier x : TIERS) if (x.line().equals(line) && x.tier() == t) return x;
        return null;
    }

    public static StatMap bonus(PlayerData d) {
        Tier t = of(d);
        return t == null ? new StatMap() : t.bonus();
    }

    private void ensurePlaced() {
        World w = Bukkit.getWorlds().get(0);
        Random r = new Random();
        for (Tier t : TIERS) {
            String id = t.line() + t.tier();
            if (data.contains("placed." + id)) continue;
            for (int i = 0; i < 30; i++) {
                double a = r.nextDouble() * Math.PI * 2, dd = 1800 + r.nextDouble() * 2600;
                Location l = w.getSpawnLocation().clone().add(Math.cos(a) * dd, 0, Math.sin(a) * dd);
                w.getChunkAt(l).load(true);
                Block top = kr.rpgcraft.util.Locs.surface(w, l);
                if (top.isLiquid()) continue;
                spawnNpc(w, t, top);
                data.set("placed." + id, top.getX() + "," + top.getZ());
                save();
                break;
            }
        }
    }

    private void spawnNpc(World w, Tier t, Block top) {
        String id = t.line() + t.tier();
        w.spawn(top.getLocation().add(0.5, 1, 0.5), Villager.class, v -> {
            v.setAI(false);
            v.setInvulnerable(true);
            v.setSilent(true);
            v.setPersistent(true);
            v.setRemoveWhenFarAway(false);
            v.setProfession(t.line().equals("A") ? Villager.Profession.CLERIC : Villager.Profession.CARTOGRAPHER);
            v.setVillagerLevel(5);
            v.setCustomName(Text.c("&8…"));
            v.setCustomNameVisible(true);
            v.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, id);
        });
    }

    /** 배치 기록은 있는데 NPC 가 없어졌으면 같은 자리에 다시 세운다 */
    public void respawnMissing(org.bukkit.command.CommandSender who) {
        World w = Bukkit.getWorlds().get(0);
        for (Tier t : TIERS) {
            String id = t.line() + t.tier();
            String xz = data.getString("placed." + id);
            if (xz == null) continue;
            String[] v = xz.split(",");
            int x = Integer.parseInt(v[0].trim()), z = Integer.parseInt(v[1].trim());
            kr.rpgcraft.util.NpcRespawn.ensure(plugin, w, x, z, KEY, id, top -> spawnNpc(w, t, top), again -> {
                if (!again) return;
                String msg = "숨은 직업 NPC " + id + " 다시 배치: " + x + ", " + z;
                if (who != null) who.sendMessage(Text.c("&5" + msg));
                else plugin.getLogger().info(msg);
            });
        }
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException ignored) {
        }
    }

    public List<String> locations() {
        List<String> out = new java.util.ArrayList<>();
        var sec = data.getConfigurationSection("placed");
        if (sec != null) for (String k : sec.getKeys(false)) out.add(k + " " + sec.getString(k));
        return out;
    }

    private int count(Player p, String id) {
        int n = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) if (id.equals(ItemData.id(it))) n += it.getAmount();
        return n;
    }

    private boolean meets(Player p, PlayerData d, String need) {
        String[] kv = need.split(":");
        int n = Integer.parseInt(kv[1]);
        if (kv[0].equals("#lv")) return d.level >= n;
        if (kv[0].startsWith("#")) return d.counter(kv[0].substring(1)) >= n;
        return count(p, kv[0]) >= n;
    }

    private String label(String need) {
        String[] kv = need.split(":");
        return switch (kv[0]) {
            case "#lv" -> "Lv." + kv[1];
            case "#ach_boss" -> "보스 " + kv[1];
            case "#ach_elite" -> "정예 " + kv[1];
            case "#ach_treasure" -> "보물 " + kv[1];
            default -> plugin.items().get(kv[0]).name + " " + kv[1];
        };
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        String id = e.getRightClicked().getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
        if (id == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        PlayerData d = plugin.data().get(p);
        Tier t = TIERS.stream().filter(x -> (x.line() + x.tier()).equals(id)).findFirst().orElse(null);
        if (t == null) return;
        Tier cur = of(d);
        // 순서: 같은 줄기의 바로 앞 단계여야 함 (1단계는 아무 숨은 길도 걷지 않은 사람만)
        boolean ready = t.tier() == 1 ? cur == null : cur != null && cur.line().equals(t.line()) && cur.tier() == t.tier() - 1;
        Gui g = new Gui(3, "&8…") {
        };
        if (!ready) {
            g.set(13, Gui.button(Material.GRAY_DYE, "&8…"), null);
            g.fill(0, 26);
            g.open(p);
            return;
        }
        List<String> lore = new java.util.ArrayList<>();
        boolean all = true;
        for (String n : t.need()) {
            boolean ok = meets(p, d, n);
            all &= ok;
            lore.add((ok ? "&a✔ " : "&c✘ ") + label(n));
        }
        boolean fAll = all;
        if (all) lore.add("&e▶");
        g.set(13, Gui.button(Material.NETHER_STAR, "&5???", lore.toArray(new String[0])), ev -> {
            if (!fAll) return;
            for (String n : t.need()) {
                String[] kv = n.split(":");
                if (kv[0].startsWith("#")) continue;
                int left = Integer.parseInt(kv[1]);
                for (ItemStack it : p.getInventory().getStorageContents()) {
                    if (left <= 0) break;
                    if (!kv[0].equals(ItemData.id(it))) continue;
                    int take = Math.min(left, it.getAmount());
                    it.setAmount(it.getAmount() - take);
                    left -= take;
                }
            }
            d.counters.remove("hj_line_A");
            d.counters.remove("hj_line_B");
            d.counters.put("hj_line_" + t.line(), 1.0);
            d.counters.put("hj_tier", (double) t.tier());
            plugin.stats().refresh(p);
            p.closeInventory();
            p.sendTitle(Text.c("&5&l" + t.label()), "", 10, 70, 20);
            if (plugin.content() != null) plugin.content().onHiddenJob(p, t.label());   // 히든 직업 칭호
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.6f, 1.4f);
            p.getWorld().strikeLightningEffect(p.getLocation());
        });
        g.fill(0, 26);
        g.open(p);
    }

    /** 숨은 직업 전용 효과: 망령(A) 처치 시 회복 · 3단계는 영혼 폭발 / 별(B) 3단계는 치명타 때 별똥별 */
    @EventHandler
    public void onKill(EntityDeathEvent e) {
        Player k = e.getEntity().getKiller();
        if (k == null) return;
        Tier t = of(plugin.data().get(k));
        if (t == null || !t.line().equals("A")) return;
        if (t.tier() >= 2) plugin.health().healPercent(k, 5);
        if (t.tier() >= 3) {
            Location at = e.getEntity().getLocation().add(0, 1, 0);
            kr.rpgcraft.util.Vfx.burst(at, 3.5, Color.fromRGB(0x7FE8FF));
            kr.rpgcraft.util.Vfx.ring(at.clone().add(0, -1, 0), 4, Color.fromRGB(0xC060FF));
            for (var en : at.getWorld().getNearbyEntities(at, 4, 3, 4))
                if (en instanceof LivingEntity le && !le.equals(e.getEntity()) && plugin.combat().isEnemy(k, le))
                    plugin.combat().dealSkillDamage(k, le, plugin.data().get(k).stats.magic * 0.5 + plugin.data().get(k).stats.attack * 0.3, false);
        }
    }

    public static boolean starfall(PlayerData d) {
        Tier t = of(d);
        return t != null && t.line().equals("B") && t.tier() >= 3;
    }
}
