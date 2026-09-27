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
            // v5.3.0: 히든 직업은 원래 직업을 "대신" 하므로, 잃는 직업 보너스만큼 모든 스탯 % 를 더 준다
            new Tier("A", 1, "망령 사냥꾼", new String[]{"#lv:50", "spirit_summon:1", "loot_dust:50"}, StatMap.of(Stat.LIFESTEAL, 3, Stat.MAGIC, 300, Stat.STR_PCT, 12, Stat.DEX_PCT, 12, Stat.ADV_PCT, 12, Stat.HP_PCT, 8)),
            new Tier("A", 2, "영혼 수확자", new String[]{"#lv:120", "#ach_boss:10", "loot_eye:10"}, StatMap.of(Stat.LIFESTEAL, 6, Stat.MAGIC, 900, Stat.HP_PCT, 20, Stat.STR_PCT, 20, Stat.DEX_PCT, 20, Stat.ADV_PCT, 20)),
            new Tier("A", 3, "명계의 군주", new String[]{"#lv:200", "#ach_boss:30", "loot_core:30"}, StatMap.of(Stat.LIFESTEAL, 10, Stat.MAGIC, 2000, Stat.HP_PCT, 35, Stat.STR_PCT, 30, Stat.DEX_PCT, 30, Stat.ADV_PCT, 30)),
            new Tier("B", 1, "별빛 방랑자", new String[]{"#lv:50", "#ach_treasure:10", "loot_frost:30"}, StatMap.of(Stat.CRIT, 8, Stat.SPEED, 8, Stat.STR_PCT, 12, Stat.DEX_PCT, 12, Stat.ADV_PCT, 12, Stat.HP_PCT, 8)),
            new Tier("B", 2, "성운 기사", new String[]{"#lv:120", "#ach_elite:300", "loot_scale:30"}, StatMap.of(Stat.CRIT, 12, Stat.CRIT_DMG, 40, Stat.DODGE, 6, Stat.STR_PCT, 20, Stat.DEX_PCT, 20, Stat.ADV_PCT, 20, Stat.HP_PCT, 12)),
            new Tier("B", 3, "천구의 주재자", new String[]{"#lv:200", "#ach_boss:30", "loot_crown:10"}, StatMap.of(Stat.CRIT, 18, Stat.CRIT_DMG, 90, Stat.DODGE, 10, Stat.STR_PCT, 30, Stat.DEX_PCT, 30, Stat.ADV_PCT, 30, Stat.HP_PCT, 20)));

    /** 히든 전직서 아이템 ID (단계마다 하나) */
    public static String scrollId(Tier t) {
        return "hj_scroll_" + t.line() + t.tier();
    }

    private static final NamespacedKey OWNER = new NamespacedKey("rpgcraft", "hj_owner");

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
        if (cur != null && cur.line().equals(t.line()) && cur.tier() >= t.tier()) return;   // 이미 깬 히든 NPC 는 말을 걸 수 없음 (v5.3.6)
        // 순서: 같은 줄기의 바로 앞 단계여야 함 (1단계는 아무 숨은 길도 걷지 않은 사람만)
        boolean ready = t.tier() == 1 ? cur == null : cur != null && cur.line().equals(t.line()) && cur.tier() == t.tier() - 1;
        Gui g = new Gui(3, "&8…") {
        };
        if (ready && d.counter("hj_issued_" + t.line() + t.tier()) > 0) {   // 이미 퀘스트를 깼음: 전직서를 잃어버렸으면 다시 줌
            boolean holding = false;
            for (ItemStack it : p.getInventory().getContents()) if (scrollId(t).equals(ItemData.id(it))) { holding = true; break; }
            if (!holding) giveScroll(p, t);
            Text.msg(p, "&d히든 전직서를 들고 우클릭하면 &5&l" + t.label() + "&d(으)로 전직합니다.");
            return;
        }
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
            d.counters.put("hj_issued_" + t.line() + t.tier(), 1.0);
            p.closeInventory();
            giveScroll(p, t);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.6f);
            Text.msg(p, "&d✦ 히든 전직 퀘스트 완료! &f「히든 전직서: " + t.label() + "」&d를 받았습니다. &7(들고 우클릭하면 전직)");
        });
        g.fill(0, 26);
        g.open(p);
    }

    // ------------------------------------------------------------------ 히든 전직서 (v5.3.0)
    private void giveScroll(Player p, Tier t) {
        ItemStack it = plugin.items().create(scrollId(t), 1);
        if (it == null) return;
        var m = it.getItemMeta();
        m.getPersistentDataContainer().set(OWNER, PersistentDataType.STRING, p.getUniqueId().toString());
        List<String> lore = m.hasLore() ? new java.util.ArrayList<>(m.getLore()) : new java.util.ArrayList<>();
        lore.add(Text.c("&8주인: " + p.getName() + " (다른 사람은 사용 불가)"));
        m.setLore(lore);
        it.setItemMeta(m);
        for (ItemStack left : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
    }

    private Tier scrollTier(ItemStack it) {
        String id = ItemData.id(it);
        if (id == null || !id.startsWith("hj_scroll_")) return null;
        for (Tier t : TIERS) if (scrollId(t).equals(id)) return t;
        return null;
    }

    /** 전직서 우클릭 → 확인 창 → 원래 직업 대신 히든 직업으로 */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onUseScroll(org.bukkit.event.player.PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        var a = e.getAction();
        if (a != org.bukkit.event.block.Action.RIGHT_CLICK_AIR && a != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        Tier t = scrollTier(hand);
        if (t == null) return;
        e.setCancelled(true);
        String owner = hand.getItemMeta().getPersistentDataContainer().get(OWNER, PersistentDataType.STRING);
        if (owner != null && !owner.equals(p.getUniqueId().toString())) { Text.msg(p, "&c다른 사람의 전직서는 사용할 수 없습니다."); return; }
        PlayerData d = plugin.data().get(p);
        Tier cur = of(d);
        boolean ready = t.tier() == 1 ? cur == null : cur != null && cur.line().equals(t.line()) && cur.tier() == t.tier() - 1;
        if (!ready) { Text.msg(p, cur != null && cur.line().equals(t.line()) && cur.tier() >= t.tier() ? "&7이미 이 단계 이상의 히든 직업입니다." : "&c아직 이 전직서를 쓸 수 없습니다."); return; }
        String now = plugin.jobs().title(d);
        Gui g = new Gui(3, "&8히든 전직") {
        };
        g.set(11, Gui.button(Material.BARRIER, "&7취소"), ev -> p.closeInventory());
        g.set(15, Gui.button(Material.NETHER_STAR, "&5&l" + t.label() + " &d(으)로 전직",
                "&7현재 직업: &f" + now,
                t.tier() == 1 ? "&c원래 직업의 능력치 · 효과 · 직업 스킬은 사라지고" : "&7히든 직업이 한 단계 올라갑니다.",
                t.tier() == 1 ? "&c히든 직업으로 바뀝니다." : "",
                "", "&e▶ 클릭하여 전직"), ev -> {
            p.closeInventory();
            ItemStack h2 = p.getInventory().getItemInMainHand();
            if (scrollTier(h2) != t) return;
            Tier c2 = of(d);
            boolean ok = t.tier() == 1 ? c2 == null : c2 != null && c2.line().equals(t.line()) && c2.tier() == t.tier() - 1;
            if (!ok) return;
            h2.setAmount(h2.getAmount() - 1);
            d.counters.remove("hj_issued_" + t.line() + t.tier());
            promote(p, d, t);
        });
        g.fill(0, 26);
        g.open(p);
    }

    private void promote(Player p, PlayerData d, Tier t) {
        if (t.tier() == 1 && (d.job != null || d.subJob != null || d.thirdJob != null)) {   // 원래 직업은 기록만 남겨 둠 (관리자가 되돌릴 수 있게)
            d.counters.put("hj_prev_set", 1.0);
            plugin.getLogger().info("[히든 직업] " + p.getName() + " 원래 직업 " + d.job + "/" + d.subJob + "/" + d.thirdJob + " → " + t.label());
        }
        d.counters.remove("hj_line_A");
        d.counters.remove("hj_line_B");
        d.counters.put("hj_line_" + t.line(), 1.0);
        d.counters.put("hj_tier", (double) t.tier());
        plugin.stats().refresh(p);
        p.sendTitle(Text.c("&5&l" + t.label()), Text.c(t.tier() == 1 ? "&7히든 직업으로 전직했습니다" : "&7히든 직업 승급"), 10, 70, 20);
        if (plugin.content() != null) plugin.content().onHiddenJob(p, t.label());   // 히든 직업 칭호 · 공지
        p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.6f, 1.4f);
        p.getWorld().strikeLightningEffect(p.getLocation());
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
