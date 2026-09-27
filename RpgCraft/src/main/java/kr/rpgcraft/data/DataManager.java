package kr.rpgcraft.data;

import kr.rpgcraft.RpgCraft;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class DataManager {
    private final RpgCraft plugin;
    private final File folder;
    private final Map<UUID, PlayerData> cache = new HashMap<>();

    public DataManager(RpgCraft plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "players");
        folder.mkdirs();
    }

    public PlayerData get(Player p) {
        return get(p.getUniqueId());
    }

    public PlayerData get(UUID id) {
        PlayerData d = cache.get(id);
        if (d == null) {
            d = load(id);
            cache.put(id, d);
        }
        return d;
    }

    public boolean isLoaded(UUID id) {
        return cache.containsKey(id);
    }

    public Collection<PlayerData> loaded() {
        return cache.values();
    }

    public void unload(UUID id) {
        PlayerData d = cache.remove(id);
        if (d != null) save(d);
    }

    /** 오프라인 포함 이름으로 찾기 (파일 스캔) */
    public PlayerData findByName(String name) {
        for (PlayerData d : cache.values()) if (name.equalsIgnoreCase(d.name)) return d;
        File[] files = folder.listFiles();
        if (files == null) return null;
        for (File f : files) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            if (name.equalsIgnoreCase(y.getString("name"))) {
                try {
                    return get(UUID.fromString(f.getName().replace(".yml", "")));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return null;
    }

    private PlayerData load(UUID id) {
        PlayerData d = new PlayerData(id);
        File f = new File(folder, id + ".yml");
        if (!f.exists()) return d;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        d.name = y.getString("name");
        d.level = y.getInt("level", 0);
        d.exp = y.getDouble("exp");
        d.statPoints = y.getInt("stat-points");
        d.str = y.getInt("str");
        d.dex = y.getInt("dex");
        d.adv = y.getInt("adv");
        d.money = y.getLong("money");
        d.blacksmith = y.getBoolean("blacksmith");
        d.job = y.getString("job");
        d.subJob = y.getString("sub-job");
        d.thirdJob = y.getString("third-job");
        d.nick = y.getString("nick");
        d.starterGiven = y.getBoolean("starter-given");
        d.starterWorld = y.getString("starter-world");
        d.passives.addAll(y.getStringList("passives"));
        d.hp = y.getDouble("hp", -1);
        d.quickSkill = y.getString("quick-skill");
        d.selectedPotion = y.getString("selected-potion");
        d.guildChat = y.getBoolean("guild-chat");
        String at = y.getString("absorb-target");
        if (at != null) d.absorbTarget = UUID.fromString(at);
        d.counterRound = y.getInt("counter-round");
        readMap(y.getConfigurationSection("counters"), d.counters);
        readMap(y.getConfigurationSection("round-counters"), d.roundCounters);
        ConfigurationSection pb = y.getConfigurationSection("potion-bag");
        if (pb != null) for (String k : pb.getKeys(false)) d.potionBag.put(k, pb.getInt(k));
        for (int i = 0; i < 3; i++) d.runes[i] = y.getItemStack("runes." + i);
        for (int i = 0; i < 3; i++) d.accessories[i] = y.getItemStack("accessories." + i);
        return d;
    }

    private void readMap(ConfigurationSection s, Map<String, Double> into) {
        if (s == null) return;
        for (String k : s.getKeys(false)) into.put(k.replace("__", "."), s.getDouble(k));
    }

    public void save(PlayerData d) {
        YamlConfiguration y = new YamlConfiguration();
        y.set("name", d.name);
        y.set("level", d.level);
        y.set("exp", d.exp);
        y.set("stat-points", d.statPoints);
        y.set("str", d.str);
        y.set("dex", d.dex);
        y.set("adv", d.adv);
        y.set("money", d.money);
        y.set("blacksmith", d.blacksmith);
        y.set("job", d.job);
        y.set("sub-job", d.subJob);
        y.set("third-job", d.thirdJob);
        y.set("nick", d.nick);
        y.set("starter-given", d.starterGiven);
        y.set("starter-world", d.starterWorld);
        y.set("passives", new ArrayList<>(d.passives));
        y.set("hp", d.hp);
        y.set("quick-skill", d.quickSkill);
        y.set("selected-potion", d.selectedPotion);
        y.set("guild-chat", d.guildChat);
        y.set("absorb-target", d.absorbTarget == null ? null : d.absorbTarget.toString());
        y.set("counter-round", d.counterRound);
        d.counters.forEach((k, v) -> y.set("counters." + k.replace(".", "__"), v));
        d.roundCounters.forEach((k, v) -> y.set("round-counters." + k.replace(".", "__"), v));
        d.potionBag.forEach((k, v) -> y.set("potion-bag." + k, v));
        for (int i = 0; i < 3; i++) y.set("runes." + i, d.runes[i]);
        for (int i = 0; i < 3; i++) y.set("accessories." + i, d.accessories[i]);
        try {
            y.save(new File(folder, d.uuid + ".yml"));
        } catch (IOException e) {
            plugin.getLogger().warning("플레이어 데이터 저장 실패: " + d.uuid + " " + e.getMessage());
        }
    }

    public void saveAll() {
        for (PlayerData d : cache.values()) save(d);
    }

    public static ItemStack[] copy(ItemStack[] arr) {
        ItemStack[] out = new ItemStack[arr.length];
        for (int i = 0; i < arr.length; i++) out[i] = arr[i] == null ? null : arr[i].clone();
        return out;
    }
}
