package kr.rpgcraft.world;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.data.PlayerData;
import kr.rpgcraft.gui.Gui;
import kr.rpgcraft.item.ItemData;
import kr.rpgcraft.passive.Passive;
import kr.rpgcraft.util.Text;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * 히든 퀘스트 NPC 3명 — 맵 먼 곳 어딘가에 숨어 있다 (이름표 "???", 지도·나침반 표시 없음).
 * 조건을 채우고 찾아가면 그 NPC 만의 히든 패시브를 준다. 각 패시브는 서버 전체에서 3명까지만.
 *  은둔한 검성: 정예 100 처치 + 타락한 왕관 조각 5 → 검성의 깨달음
 *  떠돌이 현자: 보물 지도 10장 해독 + 전설의 용왕어 1 → 현자의 지혜
 *  몰락한 왕 : 보스 3회 처치 + 5,000,000원 → 몰락한 왕의 위엄
 */
public class HiddenQuestManager implements Listener {
    private record HQ(String id, String name, Villager.Profession prof, Passive reward, String[] need, String hint) {}

    private static final List<HQ> LIST = List.of(
            new HQ("sword_saint", "은둔한 검성", Villager.Profession.WEAPONSMITH, Passive.SWORD_SAINT,
                    new String[]{"#ach_elite:100", "loot_crown:5"}, "\"칼끝에 쌓인 피가 부족하군.\""),
            new HQ("sage", "떠돌이 현자", Villager.Profession.LIBRARIAN, Passive.SAGE_WISDOM,
                    new String[]{"#ach_treasure:10", "fish_legend:1"}, "\"세상의 비밀을 더 보고 오게.\""),
            new HQ("fallen_king", "몰락한 왕", Villager.Profession.CLERIC, Passive.KINGS_MAJESTY,
                    new String[]{"#ach_boss:3", "money:5000000"}, "\"왕좌는 힘과 재물로 되찾는 것.\""));

    private final RpgCraft plugin;
    private final NamespacedKey KEY;
    private final File file;
    private final YamlConfiguration data;

    public HiddenQuestManager(RpgCraft plugin) {
        this.plugin = plugin;
        this.KEY = new NamespacedKey(plugin, "hidden_quest");
        this.file = new File(plugin.getDataFolder(), "hidden_quests.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        Bukkit.getScheduler().runTaskLater(plugin, this::ensurePlaced, 200L);
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException ignored) {
        }
    }

    /** 아직 배치되지 않은 히든 NPC 를 먼 곳에 한 번만 배치 */
    private void ensurePlaced() {
        World w = Bukkit.getWorlds().get(0);
        Random r = new Random();
        for (HQ q : LIST) {
            if (data.contains("placed." + q.id())) continue;
            for (int i = 0; i < 30; i++) {
                double a = r.nextDouble() * Math.PI * 2, d = plugin.getConfig().getDouble("hidden-quests.min-distance", 1500)
                        + r.nextDouble() * plugin.getConfig().getDouble("hidden-quests.spread", 2500);
                Location l = w.getSpawnLocation().clone().add(Math.cos(a) * d, 0, Math.sin(a) * d);
                w.getChunkAt(l).load(true);
                Block top = kr.rpgcraft.util.Locs.surface(w, l);
                if (top.isLiquid() || top.getType().name().contains("LEAVES")) continue;
                w.spawn(top.getLocation().add(0.5, 1, 0.5), Villager.class, v -> {
                    v.setAI(false);
                    v.setInvulnerable(true);
                    v.setSilent(true);
                    v.setPersistent(true);
                    v.setRemoveWhenFarAway(false);
                    v.setProfession(q.prof());
                    v.setVillagerLevel(5);
                    v.setCustomName(Text.c("&8???"));
                    v.setCustomNameVisible(true);
                    v.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, q.id());
                    v.getPersistentDataContainer().set(kr.rpgcraft.Keys.INDICATOR, PersistentDataType.BYTE, (byte) 0);
                });
                top.getRelative(1, 1, 0).setType(Material.SOUL_LANTERN, false);
                data.set("placed." + q.id(), top.getX() + "," + top.getZ());
                save();
                break;
            }
        }
    }

    private int count(Player p, String id) {
        int n = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) if (id.equals(ItemData.id(it))) n += it.getAmount();
        return n;
    }

    private boolean meets(Player p, PlayerData d, String need) {
        String[] kv = need.split(":");
        int n = Integer.parseInt(kv[1]);
        if (kv[0].startsWith("#")) return d.counter(kv[0].substring(1)) >= n;
        if (kv[0].equals("money")) return plugin.economy().has(p, n);
        return count(p, kv[0]) >= n;
    }

    private String label(String need) {
        String[] kv = need.split(":");
        return switch (kv[0]) {
            case "#ach_elite" -> "정예 몬스터 " + kv[1] + "마리 처치";
            case "#ach_treasure" -> "보물 지도 " + kv[1] + "장 해독";
            case "#ach_boss" -> "보스 " + kv[1] + "회 처치";
            case "money" -> Text.money(Long.parseLong(kv[1]));
            default -> plugin.items().get(kv[0]).name + " " + kv[1] + "개";
        };
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        String id = e.getRightClicked().getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
        if (id == null) return;
        e.setCancelled(true);
        HQ q = LIST.stream().filter(x -> x.id().equals(id)).findFirst().orElse(null);
        if (q == null) return;
        Player p = e.getPlayer();
        PlayerData d = plugin.data().get(p);
        List<String> owners = data.getStringList("owners." + q.id());
        int cap = plugin.getConfig().getInt("hidden-quests.max-owners", 3);
        boolean mine = owners.contains(p.getUniqueId().toString());
        Gui g = new Gui(3, "&8" + q.name()) {
        };
        List<String> lore = new ArrayList<>();
        boolean all = true;
        for (String n : q.need()) {
            boolean ok = meets(p, d, n);
            all &= ok;
            lore.add((ok ? "&a✔ " : "&c✘ ") + label(n));
        }
        lore.add("");
        lore.add("&7남은 자리 " + Math.max(0, cap - owners.size()) + " / " + cap);
        boolean can = all && !mine && owners.size() < cap;
        if (can) lore.add("&e▶ 클릭");
        g.set(13, Gui.button(mine ? Material.LIME_DYE : Material.NETHER_STAR, "&d&l" + q.reward().label, lore.toArray(new String[0])), ev -> {
            if (!can) return;
            List<String> now = data.getStringList("owners." + q.id());   // 다시 확인 (동시에 누른 경우)
            if (now.size() >= cap || now.contains(p.getUniqueId().toString())) return;
            for (String n : q.need()) if (!meets(p, d, n)) return;
            for (String n : q.need()) {
                String[] kv = n.split(":");
                if (kv[0].equals("money")) plugin.economy().take(p, Long.parseLong(kv[1]));
                else if (!kv[0].startsWith("#")) {
                    int left = Integer.parseInt(kv[1]);
                    for (ItemStack it : p.getInventory().getStorageContents()) {
                        if (left <= 0) break;
                        if (!kv[0].equals(ItemData.id(it))) continue;
                        int t = Math.min(left, it.getAmount());
                        it.setAmount(it.getAmount() - t);
                        left -= t;
                    }
                }
            }
            now.add(p.getUniqueId().toString());
            data.set("owners." + q.id(), now);
            save();
            plugin.passives().grant(p, q.reward(), true);
            p.closeInventory();
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.7f);
        });
        g.set(22, Gui.button(Material.BOOK, "&7" + q.name(), "&8" + q.hint()), null);
        g.fill(0, 26);
        g.open(p);
    }
}
