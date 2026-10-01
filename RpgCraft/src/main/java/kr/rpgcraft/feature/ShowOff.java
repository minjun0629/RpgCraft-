package kr.rpgcraft.feature;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.data.PlayerData;
import kr.rpgcraft.gui.Gui;
import kr.rpgcraft.util.Text;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * v5.10.54 /자랑 — 손에 든 아이템을 전체 채팅에 자랑.
 *  아이템 이름에 마우스를 올리면 이름 · 설명(능력치) 이 보이고, 이름이나 [아이템 보기] 버튼을 누르면 그 아이템을 그대로 담은 보기 창이 열림 (꺼낼 수 없음).
 *  자랑한 아이템은 그 순간의 복사본으로 30분 동안 볼 수 있음. 쿨타임 30초.
 */
public class ShowOff {
    private static final long KEEP_MS = 30 * 60_000L;
    private final RpgCraft plugin;
    private final Map<String, Entry> shown = new LinkedHashMap<>();
    private int seq = 0;

    private record Entry(ItemStack item, String owner, long at) {}

    public ShowOff(RpgCraft plugin) {
        this.plugin = plugin;
    }

    public void handle(Player p, String[] a) {
        if (a.length >= 2 && (a[0].equals("보기") || a[0].equals("view"))) {
            view(p, a[1]);
            return;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR) {
            Text.msg(p, "&c자랑할 아이템을 손에 들어 주세요.");
            return;
        }
        PlayerData d = plugin.data().get(p);
        if (d.onCooldown("showoff") && !p.hasPermission("rpgcraft.admin")) {
            Text.msg(p, "&c잠시 후 다시 자랑할 수 있습니다. (" + (d.remaining("showoff") / 1000 + 1) + "초)");
            return;
        }
        d.cooldown("showoff", plugin.getConfig().getLong("showoff.cooldown-seconds", 30) * 1000);
        purge();
        String id = Integer.toString(++seq, 36);
        shown.put(id, new Entry(hand.clone(), Text.name(p), System.currentTimeMillis()));
        broadcast(p, hand, id);
    }

    private void broadcast(Player p, ItemStack it, String id) {
        String viewCmd = "/자랑 보기 " + id;
        ItemMeta m = it.getItemMeta();
        String itemName = m != null && m.hasDisplayName() ? m.getDisplayName() : Text.c("&f" + pretty(it.getType()));
        // 마우스를 올리면: 이름 + 설명 그대로
        StringBuilder hover = new StringBuilder(itemName);
        if (it.getAmount() > 1) hover.append(Text.c(" &7x" + it.getAmount()));
        if (m != null && m.hasLore() && m.getLore() != null)
            for (String line : m.getLore()) hover.append('\n').append(line);
        hover.append('\n').append(Text.c("\n&e클릭하면 아이템 보기 창이 열립니다"));
        HoverEvent hv = new HoverEvent(HoverEvent.Action.SHOW_TEXT, new net.md_5.bungee.api.chat.hover.content.Text(hover.toString()));
        ClickEvent click = new ClickEvent(ClickEvent.Action.RUN_COMMAND, viewCmd);

        ComponentBuilder msg = new ComponentBuilder("");
        msg.append(TextComponent.fromLegacyText(Text.c("&6&l[자랑] &f" + Text.name(p) + "&f님이 ")));
        TextComponent name = new TextComponent(TextComponent.fromLegacyText(Text.c("&7[") + itemName + (it.getAmount() > 1 ? Text.c(" &7x" + it.getAmount()) : "") + Text.c("&7]")));
        name.setHoverEvent(hv);
        name.setClickEvent(click);
        msg.append(name, ComponentBuilder.FormatRetention.NONE);
        msg.append(TextComponent.fromLegacyText(Text.c(" &f을(를) 자랑합니다! ")), ComponentBuilder.FormatRetention.NONE);
        TextComponent btn = new TextComponent(TextComponent.fromLegacyText(Text.c("&b&l[아이템 보기]")));
        btn.setHoverEvent(hv);
        btn.setClickEvent(click);
        msg.append(btn, ComponentBuilder.FormatRetention.NONE);
        var built = msg.create();
        for (Player o : Bukkit.getOnlinePlayers()) {
            o.spigot().sendMessage(built);
            if (!o.equals(p)) o.playSound(o.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.4f, 1.6f);
        }
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
        Bukkit.getConsoleSender().sendMessage(Text.strip(Text.c("[자랑] " + Text.name(p) + " → " + itemName)));
    }

    private void view(Player p, String id) {
        purge();
        Entry e = shown.get(id);
        if (e == null) {
            Text.msg(p, "&c이 자랑은 시간이 지나 더 이상 볼 수 없습니다.");
            return;
        }
        new ViewGui(e).open(p);
    }

    private void purge() {
        long now = System.currentTimeMillis();
        shown.values().removeIf(e -> now - e.at > KEEP_MS);
    }

    private static String pretty(Material m) {
        String s = m.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** 자랑한 아이템 보기 창 (꺼낼 수 없음) */
    private static class ViewGui extends Gui {
        ViewGui(Entry e) {
            super(3, "&6" + e.owner + "&f님의 자랑");
            set(13, e.item.clone());
            set(22, button(Material.BOOK, "&e" + e.owner + "&f님의 자랑",
                    "&7아이템에 마우스를 올려 정보를 확인하세요", "&8자랑한 순간의 모습입니다"));
            fill(0, 26);
        }
    }
}
