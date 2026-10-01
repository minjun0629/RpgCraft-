package kr.rpgcraft.feature;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.gui.Gui;
import kr.rpgcraft.item.ItemData;
import kr.rpgcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * v5.10.20 우편함 (/우편). 접속하지 않은 사람에게도 돈 · 아이템을 보낼 수 있다.
 * 시즌 패스 보상이 가방에 안 들어가면 우편으로 오고, 관리자는 /rpg관리 mail 로 보낸다. 30일 지나면 사라짐.
 */
public class MailManager implements Listener, CommandExecutor, TabCompleter {
    public static final class Letter {
        public String id, from, text;
        public long money, time;
        public final List<ItemStack> items = new ArrayList<>();
    }

    private final RpgCraft plugin;
    private final File file;
    private final YamlConfiguration data;
    private final Map<UUID, List<Letter>> boxes = new HashMap<>();

    public MailManager(RpgCraft plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "mail.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        load();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void load() {
        long expire = System.currentTimeMillis() - plugin.getConfig().getLong("mail.expire-days", 30) * 86_400_000L;
        for (String u : data.getKeys(false)) {
            ConfigurationSection s = data.getConfigurationSection(u);
            if (s == null) continue;
            UUID id;
            try { id = UUID.fromString(u); } catch (IllegalArgumentException ex) { continue; }
            List<Letter> box = new ArrayList<>();
            for (String k : s.getKeys(false)) {
                ConfigurationSection x = s.getConfigurationSection(k);
                if (x == null) continue;
                Letter l = new Letter();
                l.id = k;
                l.from = x.getString("from", "?");
                l.text = x.getString("text", "");
                l.money = x.getLong("money");
                l.time = x.getLong("time");
                if (l.time < expire) continue;
                List<?> its = x.getList("items");
                if (its != null) for (Object o : its) if (o instanceof ItemStack it) l.items.add(it);
                box.add(l);
            }
            box.sort(Comparator.comparingLong(l -> -l.time));
            boxes.put(id, box);
        }
    }

    public void save() {
        for (String k : new ArrayList<>(data.getKeys(false))) data.set(k, null);
        for (Map.Entry<UUID, List<Letter>> en : boxes.entrySet())
            for (Letter l : en.getValue()) {
                String p = en.getKey() + "." + l.id;
                data.set(p + ".from", l.from);
                data.set(p + ".text", l.text);
                data.set(p + ".money", l.money);
                data.set(p + ".time", l.time);
                data.set(p + ".items", new ArrayList<>(l.items));
            }
        try {
            data.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("mail.yml 저장 실패: " + ex.getMessage());
        }
    }

    /** 우편 보내기 (어디서든 씀) */
    public void send(UUID to, String from, String text, long money, List<ItemStack> items) {
        Letter l = new Letter();
        l.id = Long.toString(System.nanoTime(), 36) + Integer.toString(new Random().nextInt(1000), 36);
        l.from = from;
        l.text = text == null ? "" : text;
        l.money = Math.max(0, money);
        l.time = System.currentTimeMillis();
        if (items != null) for (ItemStack it : items) if (it != null && !it.getType().isAir()) l.items.add(it.clone());
        boxes.computeIfAbsent(to, k -> new ArrayList<>()).add(0, l);
        save();
        Player p = Bukkit.getPlayer(to);
        if (p != null) {
            Text.msg(p, "&e✉ 새 우편이 왔습니다! &7(" + from + ") &f/우편");
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.6f);
        }
    }

    /** 아이템을 가방에 넣고, 남는 것은 우편으로 */
    public void giveOrMail(Player p, ItemStack it, String from, String text) {
        Map<Integer, ItemStack> left = p.getInventory().addItem(it);
        if (left.isEmpty()) return;
        send(p.getUniqueId(), from, text + " &7(가방이 가득 차 우편으로 보냄)", 0, new ArrayList<>(left.values()));
    }

    public int count(UUID id) {
        List<Letter> b = boxes.get(id);
        return b == null ? 0 : b.size();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        int n = count(p.getUniqueId());
        if (n > 0) Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline()) Text.msg(p, "&e✉ 받지 않은 우편이 " + n + "통 있습니다. &f/우편"); }, 80L);
    }

    // ------------------------------------------------------------------ GUI
    public void open(Player p) {
        List<Letter> box = boxes.getOrDefault(p.getUniqueId(), new ArrayList<>());
        Gui g = new Gui(6, "&e✉ 우편함 &7(" + box.size() + "통)") {
        };
        for (int i = 0; i < box.size() && i < 45; i++) {
            Letter l = box.get(i);
            List<String> lore = new ArrayList<>();
            lore.add("&7보낸 사람: &f" + l.from);
            lore.add("&7" + new java.text.SimpleDateFormat("MM/dd HH:mm").format(new Date(l.time)));
            if (!l.text.isEmpty()) { lore.add(""); lore.add("&f" + l.text); }
            lore.add("");
            if (l.money > 0) lore.add("&6💰 " + Text.money(l.money));
            for (ItemStack it : l.items) lore.add("&b📦 " + name(it) + " &7x" + it.getAmount());
            if (l.money <= 0 && l.items.isEmpty()) lore.add("&8(첨부 없음)");
            lore.add("");
            lore.add("&e▶ 클릭하여 받기");
            boolean gift = l.money > 0 || !l.items.isEmpty();
            g.set(i, Gui.button(gift ? Material.CHEST_MINECART : Material.PAPER, "&e✉ " + Text.strip(Text.c(l.from)) + "님의 우편", lore.toArray(new String[0])), e -> {
                take(p, l);
                open(p);
            });
        }
        g.set(49, Gui.button(Material.HOPPER, "&a&l모두 받기", "&7첨부된 돈 · 아이템을 모두 받음"), e -> {
            for (Letter l : new ArrayList<>(box)) if (!take(p, l)) break;
            open(p);
        });
        g.set(53, Gui.button(Material.WRITABLE_BOOK, "&e우편 보내기", "&f/우편 보내기 <플레이어> [금액] [메시지]",
                "&7손에 든 아이템과 돈을 함께 보냄 (접속하지 않은 사람에게도)",
                "&7수수료 " + Text.money(plugin.getConfig().getLong("mail.fee", 1000)) + " · 받지 않은 우편은 " + plugin.getConfig().getLong("mail.expire-days", 30) + "일 뒤 사라짐"), null);
        g.fill(45, 53);
        g.open(p);
    }

    private static String name(ItemStack it) {
        if (it.hasItemMeta() && it.getItemMeta().hasDisplayName()) return it.getItemMeta().getDisplayName();
        return it.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    /** 우편 하나 받기. 가방이 가득 차 다 못 받으면 남은 것을 그대로 두고 false */
    private boolean take(Player p, Letter l) {
        List<Letter> box = boxes.get(p.getUniqueId());
        if (box == null || !box.contains(l)) return true;
        if (l.money > 0) { plugin.economy().give(p, l.money); l.money = 0; }
        for (Iterator<ItemStack> it = l.items.iterator(); it.hasNext(); ) {
            ItemStack x = it.next();
            Map<Integer, ItemStack> left = p.getInventory().addItem(x.clone());
            if (left.isEmpty()) { it.remove(); continue; }
            x.setAmount(left.values().iterator().next().getAmount());
            save();
            Text.msg(p, "&c가방이 가득 찼습니다. 남은 아이템은 우편함에 그대로 있습니다.");
            return false;
        }
        box.remove(l);
        save();
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.2f);
        return true;
    }

    // ------------------------------------------------------------------ /우편
    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (!(s instanceof Player p)) return true;
        if (a.length == 0) { open(p); return true; }
        if (!a[0].equals("보내기") && !a[0].equalsIgnoreCase("send")) { Text.msg(p, "&e/우편 &7| &e/우편 보내기 <플레이어> [금액] [메시지]"); return true; }
        if (a.length < 2) { Text.msg(p, "&c/우편 보내기 <플레이어> [금액] [메시지] &7(손에 든 아이템도 함께)"); return true; }
        @SuppressWarnings("deprecation") OfflinePlayer to = Bukkit.getOfflinePlayer(a[1]);
        if (to == null || (!to.isOnline() && !to.hasPlayedBefore())) { Text.msg(p, "&c그런 플레이어가 없습니다."); return true; }
        if (to.getUniqueId().equals(p.getUniqueId())) { Text.msg(p, "&c자신에게는 보낼 수 없습니다."); return true; }
        long money = 0;
        int textFrom = 2;
        if (a.length > 2) {
            try { money = Long.parseLong(a[2].replace(",", "")); textFrom = 3; } catch (NumberFormatException ignored) { }
        }
        if (money < 0) { Text.msg(p, "&c금액이 올바르지 않습니다."); return true; }
        String text = a.length > textFrom ? String.join(" ", Arrays.copyOfRange(a, textFrom, a.length)) : "";
        if (text.length() > 80) text = text.substring(0, 80);
        ItemStack hand = p.getInventory().getItemInMainHand();
        boolean withItem = hand != null && !hand.getType().isAir() && !kr.rpgcraft.gui.Gui.isFiller(hand);
        if (withItem && ItemData.id(hand) != null && ItemData.id(hand).startsWith("hj_scroll_")) { Text.msg(p, "&c히든 전직서는 보낼 수 없습니다."); return true; }
        if (!withItem && money == 0) { Text.msg(p, "&c보낼 돈이나 손에 든 아이템이 없습니다."); return true; }
        long fee = plugin.getConfig().getLong("mail.fee", 1000);
        if (!plugin.economy().take(p, money + fee)) { Text.msg(p, "&c돈이 부족합니다. &7(보낼 돈 + 수수료 " + Text.money(fee) + ")"); return true; }
        List<ItemStack> items = new ArrayList<>();
        if (withItem) { items.add(hand.clone()); p.getInventory().setItemInMainHand(null); }
        send(to.getUniqueId(), Text.name(p), text, money, items);
        Text.msg(p, "&a" + to.getName() + "님에게 우편을 보냈습니다." + (money > 0 ? " &6" + Text.money(money) : "") + (withItem ? " &b+ 아이템" : ""));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (a.length == 1) return List.of("보내기");
        if (a.length == 2) return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n -> n.toLowerCase().startsWith(a[1].toLowerCase())).toList();
        return List.of();
    }
}
