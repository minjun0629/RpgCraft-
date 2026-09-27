package kr.rpgcraft.feature;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.data.PlayerData;
import kr.rpgcraft.item.ItemData;
import kr.rpgcraft.stat.Stat;
import kr.rpgcraft.stat.StatMap;
import kr.rpgcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * 레전더리 패시브 — 종류마다 서버 전체에서 단 한 명만 소유한다.
 * (히든 패시브는 여러 명이 같은 것을 가질 수 있는 것과 다름)
 *
 * 획득: 월드보스가 낮은 확률로 떨어뜨리는 「레전더리 각인석」을 우클릭. 이미 주인이 있으면 실패.
 * 반환: /레전더리 포기, 관리자 초기화 → 해당 종류만 다시 획득 가능.
 * 소유권은 legendary.yml 에 즉시 저장되며, 모든 판정이 서버 메인 스레드에서 한 번에 처리되어 동시 획득이 불가능하다.
 */
public class LegendaryManager implements Listener, CommandExecutor {
    public enum Legend {
        TYRANT("폭군의 왕관", "힘 +20%, 받는 피해 +10%"),
        IMMORTAL("불사의 맹약", "치명상을 입으면 체력 30%로 버팀 (10분에 한 번)"),
        STORM_EYE("폭풍의 눈", "모든 무기 스킬 재사용 대기시간 -35%"),
        MIDAS("미다스의 손", "처치로 얻는 돈 +50%"),
        JUDGE("심판자", "치명타 확률 +15%, 치명타 피해 +60%");

        public final String label, desc;

        Legend(String label, String desc) {
            this.label = label;
            this.desc = desc;
        }

        public String sealId() {
            return "legend_seal_" + name().toLowerCase(Locale.ROOT);
        }
    }

    private final RpgCraft plugin;
    private final Map<Legend, UUID> owners = new EnumMap<>(Legend.class);
    private final File file;

    public LegendaryManager(RpgCraft plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "legendary.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (Legend l : Legend.values()) {
            String u = y.getString(l.name());
            if (u != null && !u.isEmpty()) owners.put(l, UUID.fromString(u));
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        owners.forEach((l, u) -> y.set(l.name(), u.toString()));
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("legendary.yml save failed: " + e.getMessage());
        }
    }

    public boolean has(Player p, Legend l) {
        return p != null && p.getUniqueId().equals(owners.get(l));
    }

    public List<Legend> owned(UUID u) {
        List<Legend> out = new ArrayList<>();
        owners.forEach((l, o) -> { if (o.equals(u)) out.add(l); });
        return out;
    }

    /** 원자적 획득: 주인이 없을 때만 (메인 스레드에서만 호출) */
    public synchronized boolean acquire(Player p, Legend l) {
        if (owners.containsKey(l)) return false;
        int max = plugin.getConfig().getInt("legendary.max-per-player", 1);
        if (owned(p.getUniqueId()).size() >= max) return false;
        owners.put(l, p.getUniqueId());
        save();
        return true;
    }

    public synchronized void release(UUID u, Legend l) {
        if (u.equals(owners.get(l))) {
            owners.remove(l);
            save();
        }
    }

    public synchronized void releaseAll(UUID u) {
        if (owners.values().removeIf(u::equals)) save();
    }

    // ------------------------------------------------------------------ 효과
    public StatMap bonus(UUID u) {
        StatMap m = new StatMap();
        for (Legend l : owned(u)) {
            switch (l) {
                case TYRANT -> m.add(Stat.STR_PCT, 20);
                case JUDGE -> { m.add(Stat.CRIT, 15); m.add(Stat.CRIT_DMG, 60); }
                default -> { }
            }
        }
        return m;
    }

    public double moneyMult(Player p) {
        return has(p, Legend.MIDAS) ? 1.5 : 1;
    }

    public double cooldownMult(Player p) {
        return has(p, Legend.STORM_EYE) ? 0.65 : 1;
    }

    public double incomingMult(Player p) {
        return has(p, Legend.TYRANT) ? 1.1 : 1;
    }

    /** 불사의 맹약: 치명상 시 버티면 true */
    public boolean tryImmortal(Player p) {
        if (!has(p, Legend.IMMORTAL)) return false;
        PlayerData d = plugin.data().get(p);
        if (d.onCooldown("legend_immortal")) return false;
        d.cooldown("legend_immortal", 600_000);
        d.invulnUntil = Math.max(d.invulnUntil, System.currentTimeMillis() + 2000);
        p.getWorld().spawnParticle(org.bukkit.Particle.TOTEM, p.getLocation().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.3);
        p.playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 0.8f, 1.2f);
        Text.actionBar(p, "&6&l불사의 맹약 발동!");
        return true;
    }

    // ------------------------------------------------------------------ 각인석 사용
    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)) return;
        ItemStack it = e.getItem();
        String id = ItemData.id(it);
        if (id == null || !id.startsWith("legend_seal_")) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        Legend l;
        try {
            l = Legend.valueOf(id.substring(12).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return;
        }
        if (owners.containsKey(l)) {
            OfflinePlayer o = Bukkit.getOfflinePlayer(owners.get(l));
            Text.msg(p, "&c「" + l.label + "」은(는) 이미 " + (o.getName() == null ? "다른 플레이어" : o.getName()) + "님이 가지고 있습니다. 주인이 포기해야 얻을 수 있습니다.");
            return;
        }
        if (!acquire(p, l)) {
            Text.msg(p, "&c레전더리 패시브는 한 사람이 " + plugin.getConfig().getInt("legendary.max-per-player", 1) + "개까지만 가질 수 있습니다. (/레전더리 포기)");
            return;
        }
        it.setAmount(it.getAmount() - 1);
        plugin.stats().refresh(p);
        p.sendTitle(Text.c("&6&l✦ " + l.label + " ✦"), Text.c("&f" + l.desc), 5, 70, 15);
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.7f);
        Text.announce(Text.PREFIX + Text.c("&6&l" + p.getName() + "&f님이 레전더리 패시브 &6「" + l.label + "」&f의 주인이 되었습니다!"));
    }

    // ------------------------------------------------------------------ /레전더리
    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (a.length >= 1 && a[0].equals("포기") && s instanceof Player p) {
            List<Legend> mine = owned(p.getUniqueId());
            if (mine.isEmpty()) { Text.msg(p, "&c가진 레전더리 패시브가 없습니다."); return true; }
            Legend target = mine.get(0);
            if (a.length >= 2) for (Legend l : mine) if (l.label.replace(" ", "").equals(a[1].replace("_", "")) || l.name().equalsIgnoreCase(a[1])) target = l;
            if (a.length < 3 || !a[a.length - 1].equals("확인")) {
                Text.msg(p, "&e정말 「" + target.label + "」을(를) 포기하려면: &f/레전더리 포기 " + target.label.replace(" ", "_") + " 확인");
                return true;
            }
            release(p.getUniqueId(), target);
            plugin.stats().refresh(p);
            Text.announce(Text.PREFIX + Text.c("&7레전더리 패시브 「" + target.label + "」이(가) 다시 주인을 찾고 있습니다..."));
            return true;
        }
        Text.msg(s, "&6&l레전더리 패시브 &7(종류마다 서버에 단 한 명)");
        for (Legend l : Legend.values()) {
            UUID o = owners.get(l);
            String who = o == null ? "&a주인 없음" : "&e" + Optional.ofNullable(Bukkit.getOfflinePlayer(o).getName()).orElse("?");
            s.sendMessage(Text.c(" &6" + l.label + " &8- &7" + l.desc + " &8| " + who));
        }
        Text.msg(s, "&7월드보스가 드물게 떨어뜨리는 &6레전더리 각인석&7을 우클릭해 얻습니다. &8(/레전더리 포기)");
        return true;
    }
}
