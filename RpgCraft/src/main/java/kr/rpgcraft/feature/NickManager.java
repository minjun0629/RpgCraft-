package kr.rpgcraft.feature;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * /닉네임: 머리 위 이름표까지 닉네임으로.
 * 바닐라 이름표는 계정 이름만 쓸 수 있으므로, 닉네임이 있는 플레이어는 스코어보드 팀으로 바닐라 이름표를 숨기고
 * 머리 위에 닉네임 글자(TextDisplay)를 매 틱 따라다니게 한다. (승객으로 태우면 순간이동이 막혀서 따라다니는 방식)
 */
public class NickManager implements Listener {
    private static final String TEAM = "rpgcraft_nick";
    private final RpgCraft plugin;
    private final NamespacedKey KEY;
    private final Map<UUID, UUID> tags = new HashMap<>();

    public NickManager(RpgCraft plugin) {
        this.plugin = plugin;
        this.KEY = new NamespacedKey(plugin, "nick_tag");
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        for (World w : Bukkit.getWorlds())
            for (Entity e : w.getEntities()) if (e.getPersistentDataContainer().has(KEY, PersistentDataType.STRING)) e.remove();
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("nick.overhead", true);
    }

    private static boolean hasNick(RpgCraft plugin, Player p) {
        String n = plugin.data().get(p).nick;
        return n != null && !n.isBlank();
    }

    /** 닉네임 적용 (접속 · 변경 시): 표시 이름 · 목록 이름 · 이름표 숨김 팀 */
    public void apply(Player p) {
        String n = Text.name(p);
        p.setDisplayName(n);
        if (plugin.hud() != null) plugin.hud().refreshTab(p);
        else p.setPlayerListName(n);
        for (Scoreboard sb : boards()) registerTeam(sb);
        removeTag(p.getUniqueId());
    }

    /** 스코어보드마다 「바닐라 이름표 숨김」 팀 — 닉네임이 있는 사람만 */
    public void registerTeam(Scoreboard sb) {
        Team t = sb.getTeam(TEAM);
        if (t == null) {
            t = sb.registerNewTeam(TEAM);
            t.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            boolean want = enabled() && hasNick(plugin, p);
            if (want && !t.hasEntry(p.getName())) t.addEntry(p.getName());
            else if (!want && t.hasEntry(p.getName())) t.removeEntry(p.getName());
        }
    }

    private java.util.List<Scoreboard> boards() {
        java.util.List<Scoreboard> list = new java.util.ArrayList<>(plugin.hud() == null ? java.util.List.of() : plugin.hud().boards());
        list.add(Bukkit.getScoreboardManager().getMainScoreboard());
        return list;
    }

    private void tick() {
        if (!enabled()) { for (UUID u : new java.util.ArrayList<>(tags.keySet())) removeTag(u); return; }
        for (Player p : Bukkit.getOnlinePlayers()) {
            boolean show = hasNick(plugin, p) && !p.isDead() && p.getGameMode() != GameMode.SPECTATOR && !p.hasPotionEffect(PotionEffectType.INVISIBILITY) && !p.isInvisible();
            if (!show) { removeTag(p.getUniqueId()); continue; }
            Location at = p.getLocation().add(0, p.getHeight() + (p.isSneaking() ? 0.15 : 0.3), 0);
            Entity e = tags.containsKey(p.getUniqueId()) ? Bukkit.getEntity(tags.get(p.getUniqueId())) : null;
            if (e == null || !e.isValid() || !e.getWorld().equals(p.getWorld())) {
                if (e != null) e.remove();
                spawn(p, at);
            } else {
                e.teleport(at);
                if (e instanceof TextDisplay td) td.setSeeThrough(!p.isSneaking());   // 웅크리면 벽 너머로 안 보임 (바닐라와 같게)
            }
        }
    }

    private void spawn(Player p, Location at) {
        TextDisplay td = at.getWorld().spawn(at, TextDisplay.class, x -> {
            x.setText(Text.c("&f" + Text.name(p)));
            x.setBillboard(Display.Billboard.CENTER);
            x.setSeeThrough(true);
            x.setShadowed(true);
            x.setDefaultBackground(true);
            x.setPersistent(false);
            x.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, p.getUniqueId().toString());
        });
        p.hideEntity(plugin, td);   // 자기 머리 위 이름은 자기에게 안 보이게 (바닐라와 같게)
        tags.put(p.getUniqueId(), td.getUniqueId());
    }

    private void removeTag(UUID id) {
        UUID t = tags.remove(id);
        Entity e = t == null ? null : Bukkit.getEntity(t);
        if (e != null) e.remove();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (e.getPlayer().isOnline()) apply(e.getPlayer()); }, 5L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        removeTag(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent e) {
        removeTag(e.getPlayer().getUniqueId());
    }

    public void shutdown() {
        for (UUID u : new java.util.ArrayList<>(tags.keySet())) removeTag(u);
    }
}
