package kr.rpgcraft.guild;

import kr.rpgcraft.Keys;
import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.data.PlayerData;
import kr.rpgcraft.item.ItemData;
import kr.rpgcraft.stat.Stat;
import kr.rpgcraft.stat.StatMap;
import kr.rpgcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/** 길드: 멤버, 길드 금고, 공유 창고, 토템(길드원 전원 버프) */
public class GuildManager implements Listener {
    /** 토템 종류: 표시명, 단계별 수치 */
    public enum TotemType {
        HP_PCT("최대 체력", Stat.HP_PCT, new double[]{5, 10, 15}, true),
        LIFESTEAL("체력흡수", Stat.LIFESTEAL, new double[]{1, 2, 3}, true),
        ATK("공격력", Stat.ATK, new double[]{100, 250, 500}, false),
        ENHANCE("강화 확률", Stat.ENHANCE_RATE, new double[]{0.5, 1, 1.5}, true),
        DEF("방어력", Stat.DEF, new double[]{2, 4, 6}, true),
        EXP("경험치 획득량", Stat.EXP_PCT, new double[]{5, 10, 20}, true);

        public final String label;
        public final Stat stat;
        public final double[] values;
        public final boolean pct;

        TotemType(String label, Stat stat, double[] values, boolean pct) {
            this.label = label;
            this.stat = stat;
            this.values = values;
            this.pct = pct;
        }
    }

    private final RpgCraft plugin;
    private final File file;
    private final Map<String, Guild> guilds = new ConcurrentHashMap<>();
    private final Map<UUID, Guild> byMember = new ConcurrentHashMap<>();
    private final Map<UUID, String> invites = new HashMap<>();
    private final Map<UUID, Long> inviteTime = new HashMap<>();

    public GuildManager(RpgCraft plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "guilds.yml");
        load();
    }

    // ------------------------------------------------------------------ 조회
    public Guild of(UUID id) {
        Guild g = byMember.get(id);
        if (g != null && (guilds.get(g.name) != g || !g.members.contains(id))) {   // 해산됐거나 이미 빠진 길드 기록은 정리 (초기화 등으로 어긋난 경우)
            byMember.remove(id);
            return null;
        }
        return g;
    }

    public Guild get(String name) {
        for (Guild g : guilds.values()) if (g.name.equalsIgnoreCase(name)) return g;
        return null;
    }

    public Collection<Guild> all() {
        return guilds.values();
    }

    public String nameOf(UUID id) {
        Guild g = of(id);
        return g == null ? null : g.name;
    }

    // ------------------------------------------------------------------ 조작
    public Guild create(Player leader, String name) {
        if (get(name) != null || of(leader.getUniqueId()) != null) return null;
        Guild g = new Guild(name, leader.getUniqueId());
        guilds.put(name, g);
        byMember.put(leader.getUniqueId(), g);
        save();
        return g;
    }

    public void addMember(Guild g, UUID id) {
        g.members.add(id);
        byMember.put(id, g);
        save();
    }

    public void removeMember(Guild g, UUID id) {
        g.members.remove(id);
        byMember.remove(id);
        save();
    }

    public void disband(Guild g) {
        for (UUID id : g.members) byMember.remove(id);
        byMember.values().removeIf(x -> x == g);   // 목록에서 먼저 빠진 사람의 기록까지
        guilds.remove(g.name);
        for (Player p : g.online()) if (p.getOpenInventory().getTopInventory().getHolder() == g) p.closeInventory();
        plugin.wars().onGuildDisband(g.name);
        save();
    }

    public void invite(Guild g, Player target) {
        invites.put(target.getUniqueId(), g.name);
        inviteTime.put(target.getUniqueId(), System.currentTimeMillis());
    }

    public Guild pendingInvite(UUID id) {
        String n = invites.get(id);
        Long t = inviteTime.get(id);
        if (n == null || t == null || System.currentTimeMillis() - t > 60_000) return null;
        return get(n);
    }

    public void clearInvite(UUID id) {
        invites.remove(id);
        inviteTime.remove(id);
    }

    public long levelUpCost(Guild g) {
        return plugin.getConfig().getLong("guild.levelup-cost-base", 5_000_000) * g.level;
    }

    public int maxLevel() {
        return plugin.getConfig().getInt("guild.max-level", 5);
    }

    // ------------------------------------------------------------------ 토템
    public String randomTotem() {
        TotemType[] ts = TotemType.values();
        TotemType t = ts[ThreadLocalRandom.current().nextInt(ts.length)];
        double r = ThreadLocalRandom.current().nextDouble();
        int tier = r < 0.05 ? 3 : r < 0.30 ? 2 : 1;
        return t.name() + ":" + tier;
    }

    public ItemStack totemItem(String totem) {
        ItemStack it = plugin.items().create("totem", 1);
        ItemData.setString(it, Keys.TOTEM, totem);
        ItemData.refresh(it);
        return it;
    }

    private static TotemType typeOf(String s) {
        try {
            return TotemType.valueOf(s.split(":")[0]);
        } catch (Exception e) {
            return null;
        }
    }

    private static int tierOf(String s) {
        try {
            return Math.max(1, Math.min(3, Integer.parseInt(s.split(":")[1])));
        } catch (Exception e) {
            return 1;
        }
    }

    public String totemLabel(String s) {
        TotemType t = typeOf(s);
        if (t == null) return "알 수 없는 토템";
        int tier = tierOf(s);
        return "[" + "★".repeat(tier) + "] " + t.label + " " + Text.signed(t.values[tier - 1], t.pct);
    }

    public StatMap totemStats(UUID id) {
        StatMap m = new StatMap();
        Guild g = of(id);
        if (g == null) return m;
        for (String s : g.totems) {
            TotemType t = typeOf(s);
            if (t != null) m.add(t.stat, t.values[tierOf(s) - 1]);
        }
        return m;
    }

    // ------------------------------------------------------------------ 채팅
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        Guild g = of(p.getUniqueId());
        PlayerData d = plugin.data().isLoaded(p.getUniqueId()) ? plugin.data().get(p) : null;
        int lv = d == null ? 1 : d.level;
        if (g != null && d != null && d.guildChat) {
            e.setCancelled(true);
            String msg = Text.c("&a[길드] &f" + p.getDisplayName() + "&7: &a") + e.getMessage();
            Bukkit.getScheduler().runTask(plugin, () -> {
                g.broadcast(msg);
                Bukkit.getConsoleSender().sendMessage(msg);
            });
            return;
        }
        String lvTag = plugin.pack().overlay() ? "&f" + kr.rpgcraft.pack.HudFont.badge(lv) + " &7" + lv + " " : "&7[Lv." + lv + "] ";
        String title = plugin.content() == null ? "" : plugin.content().title(plugin.data().get(e.getPlayer()));
        int reb = (int) plugin.data().get(e.getPlayer()).counter("rebirth");
        String prefix = Text.c(lvTag + (reb > 0 ? "&d✦" + reb + " " : "") + (title.isEmpty() ? "" : "&d«" + title + "» ") + (g == null ? "" : "&b[" + g.name + "] ") + "&f");
        e.setFormat(prefix + "%1$s&7: &f%2$s".replace("&7", "§7").replace("&f", "§f"));
    }

    // ------------------------------------------------------------------ 저장
    private void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String key : y.getKeys(false)) {
            var s = y.getConfigurationSection(key);
            if (s == null) continue;
            try {
                String name = s.getString("name", key);
                Guild g = new Guild(name, UUID.fromString(s.getString("leader")));
                g.level = s.getInt("level", 1);
                g.bank = s.getLong("bank");
                for (String m : s.getStringList("members")) g.members.add(UUID.fromString(m));
                g.totems.addAll(s.getStringList("totems"));
                Inventory inv = g.storage();
                for (int i = 0; i < inv.getSize(); i++) {
                    ItemStack st = s.getItemStack("storage." + i);
                    if (st != null && st.hasItemMeta()) {
                        var meta = st.getItemMeta();
                        if (kr.rpgcraft.util.LegacyMigrator.migrate(meta.getPersistentDataContainer(), plugin)) st.setItemMeta(meta);
                    }
                    inv.setItem(i, st);
                }
                guilds.put(name, g);
                for (UUID m : g.members) byMember.put(m, g);
            } catch (Exception ex) {
                plugin.getLogger().warning("길드 로드 실패 " + key + ": " + ex.getMessage());
            }
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        int i = 0;
        for (Guild g : guilds.values()) {
            String k = "g" + (i++);
            y.set(k + ".name", g.name);
            y.set(k + ".leader", g.leader.toString());
            y.set(k + ".level", g.level);
            y.set(k + ".bank", g.bank);
            List<String> ms = new ArrayList<>();
            for (UUID m : g.members) ms.add(m.toString());
            y.set(k + ".members", ms);
            y.set(k + ".totems", g.totems);
            Inventory inv = g.storage();
            for (int s = 0; s < inv.getSize(); s++) {
                ItemStack it = inv.getItem(s);
                if (it != null && !it.getType().isAir()) y.set(k + ".storage." + s, it);
            }
        }
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("guilds.yml 저장 실패: " + e.getMessage());
        }
    }
}
