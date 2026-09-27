package kr.rpgcraft.feature;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.gui.Gui;
import kr.rpgcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * 플레이어 거래.
 * /거래 <플레이어> → 상대가 /거래 수락 → 거래창 (왼쪽 4줄×4칸 = 내 물건, 오른쪽 = 상대 물건)
 * /거래 돈 <금액> 으로 돈 제시, 양쪽이 [준비 완료]를 누르면 3초 뒤 교환. 내용이 바뀌면 준비가 풀린다.
 * 창을 닫으면 거래 취소, 올려둔 물건은 돌려받는다.
 */
public class TradeManager {
    private static final int[] MINE = {0, 1, 2, 3, 9, 10, 11, 12, 18, 19, 20, 21, 27, 28, 29, 30};
    private static final int[] THEIRS = {5, 6, 7, 8, 14, 15, 16, 17, 23, 24, 25, 26, 32, 33, 34, 35};

    private final RpgCraft plugin;
    private final Map<UUID, UUID> requests = new HashMap<>();
    private final Map<UUID, Long> requestTime = new HashMap<>();
    private final Map<UUID, Session> sessions = new HashMap<>();

    public TradeManager(RpgCraft plugin) {
        this.plugin = plugin;
    }

    private class Session {
        final Player a, b;
        final TradeGui ga, gb;
        long moneyA, moneyB;
        boolean readyA, readyB, done;
        int countdown = -1;

        Session(Player a, Player b) {
            this.a = a;
            this.b = b;
            ga = new TradeGui(this, a, b);
            gb = new TradeGui(this, b, a);
            // (버그 수정) 두 창이 모두 만들어진 뒤에 그린다 — 생성 도중 상대 창이 null 이라 수락 시 내부 오류가 났음
            ga.render();
            gb.render();
        }

        TradeGui guiOf(Player p) {
            return p.equals(a) ? ga : gb;
        }

        TradeGui other(TradeGui g) {
            return g == ga ? gb : ga;
        }

        void changed() {
            readyA = readyB = false;
            countdown = -1;
            Bukkit.getScheduler().runTask(plugin, () -> {
                ga.render();
                gb.render();
            });
        }
    }

    // ------------------------------------------------------------------ 요청
    public void request(Player from, String targetName) {
        Player to = Bukkit.getPlayerExact(targetName);
        if (to == null || to.equals(from)) { Text.msg(from, "&c접속 중인 다른 플레이어를 입력하세요."); return; }
        if (sessions.containsKey(from.getUniqueId()) || sessions.containsKey(to.getUniqueId())) { Text.msg(from, "&c이미 거래 중입니다."); return; }
        double max = plugin.getConfig().getDouble("trade.max-distance", 16);
        if (!to.getWorld().equals(from.getWorld()) || to.getLocation().distance(from.getLocation()) > max) {
            Text.msg(from, "&c" + (int) max + "칸 안에 있는 플레이어와만 거래할 수 있습니다.");
            return;
        }
        requests.put(to.getUniqueId(), from.getUniqueId());
        requestTime.put(to.getUniqueId(), System.currentTimeMillis());
        Text.msg(from, "&a" + to.getName() + "님에게 거래를 요청했습니다.");
        buttons(to, Text.c(Text.PREFIX + "&e" + from.getName() + "&f님이 거래를 요청했습니다. &7(30초) "), "/거래 수락", "/거래 거절");
        to.playSound(to.getLocation(), Sound.ENTITY_VILLAGER_TRADE, 1f, 1.2f);
    }

    /** 채팅 [수락] [거절] 버튼 */
    public static void buttons(Player p, String prefix, String acceptCmd, String denyCmd) {
        var msg = new net.md_5.bungee.api.chat.ComponentBuilder("");
        msg.append(net.md_5.bungee.api.chat.TextComponent.fromLegacyText(prefix));
        net.md_5.bungee.api.chat.TextComponent ok = new net.md_5.bungee.api.chat.TextComponent(net.md_5.bungee.api.chat.TextComponent.fromLegacyText(Text.c("&a&l[수락]")));
        ok.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, acceptCmd));
        net.md_5.bungee.api.chat.TextComponent no = new net.md_5.bungee.api.chat.TextComponent(net.md_5.bungee.api.chat.TextComponent.fromLegacyText(Text.c(" &c&l[거절]")));
        no.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, denyCmd));
        msg.append(ok, net.md_5.bungee.api.chat.ComponentBuilder.FormatRetention.NONE);
        msg.append(no, net.md_5.bungee.api.chat.ComponentBuilder.FormatRetention.NONE);
        p.spigot().sendMessage(msg.create());
    }

    public void accept(Player to) {
        UUID fromId = requests.remove(to.getUniqueId());
        Long t = requestTime.remove(to.getUniqueId());
        Player from = fromId == null ? null : Bukkit.getPlayer(fromId);
        if (from == null || !from.isOnline() || t == null || System.currentTimeMillis() - t > 30_000) { Text.msg(to, "&c받은 거래 요청이 없거나 만료되었습니다."); return; }
        if (sessions.containsKey(from.getUniqueId()) || sessions.containsKey(to.getUniqueId())) { Text.msg(to, "&c이미 거래 중입니다."); return; }
        if (from.isDead() || to.isDead() || !from.getWorld().equals(to.getWorld())
                || from.getLocation().distance(to.getLocation()) > plugin.getConfig().getDouble("trade.max-distance", 16)) {
            Text.msg(to, "&c상대가 너무 멀리 있어 거래할 수 없습니다.");
            return;
        }
        Session s = new Session(from, to);
        sessions.put(from.getUniqueId(), s);
        sessions.put(to.getUniqueId(), s);
        s.ga.open(from);
        s.gb.open(to);
    }

    public void deny(Player to) {
        UUID fromId = requests.remove(to.getUniqueId());
        requestTime.remove(to.getUniqueId());
        Text.msg(to, "거래 요청을 거절했습니다.");
        Player from = fromId == null ? null : Bukkit.getPlayer(fromId);
        if (from != null) Text.msg(from, "&c" + to.getName() + "님이 거래를 거절했습니다.");
    }

    public void offerMoney(Player p, long amount) {
        Session s = sessions.get(p.getUniqueId());
        if (s == null) { Text.msg(p, "&c거래 중이 아닙니다."); return; }
        if (amount < 0 || amount > 1_000_000_000_000L) { Text.msg(p, "&c올바른 금액을 입력하세요."); return; }
        if (!plugin.economy().has(p, amount)) { Text.msg(p, "&c소지금이 부족합니다."); return; }
        if (p.equals(s.a)) s.moneyA = amount;
        else s.moneyB = amount;
        s.changed();
    }

    // ------------------------------------------------------------------ GUI
    private class TradeGui extends Gui {
        final Session s;
        final Player me, other;

        TradeGui(Session s, Player me, Player other) {
            super(6, "&8거래: " + me.getName() + " ⇄ " + other.getName());
            this.s = s;
            this.me = me;
            this.other = other;
        }

        boolean mine(int raw) {
            for (int m : MINE) if (m == raw) return true;
            return false;
        }

        ItemStack[] offer() {
            ItemStack[] out = new ItemStack[MINE.length];
            for (int i = 0; i < MINE.length; i++) out[i] = inv.getItem(MINE[i]);
            return out;
        }

        void render() {
            TradeGui o = s.other(this);
            if (o == null || s.done) return;
            ItemStack[] theirs = o.offer();
            for (int i = 0; i < THEIRS.length; i++) inv.setItem(THEIRS[i], theirs[i] == null ? null : theirs[i].clone());
            boolean myReady = me.equals(s.a) ? s.readyA : s.readyB;
            boolean theirReady = me.equals(s.a) ? s.readyB : s.readyA;
            long myMoney = me.equals(s.a) ? s.moneyA : s.moneyB, theirMoney = me.equals(s.a) ? s.moneyB : s.moneyA;
            for (int r = 0; r < 5; r++) set(r * 9 + 4, button(Material.IRON_BARS, " "));
            set(45, button(Material.GOLD_INGOT, "&e내 제시 금액: " + Text.money(myMoney), "&7/거래 돈 <금액> 으로 변경"));
            set(53, button(Material.GOLD_INGOT, "&e상대 제시 금액: " + Text.money(theirMoney)));
            set(48, button(myReady ? Material.LIME_WOOL : Material.RED_WOOL, myReady ? "&a&l준비 완료" : "&c&l준비 (클릭)",
                    "&7양쪽 모두 준비되면 3초 뒤 교환", "&7물건이나 금액이 바뀌면 준비가 풀립니다"), e -> {
                if (me.equals(s.a)) s.readyA = !s.readyA;
                else s.readyB = !s.readyB;
                s.countdown = -1;
                s.ga.render();
                s.gb.render();
                if (s.readyA && s.readyB) startCountdown(s);
            });
            set(50, button(theirReady ? Material.LIME_WOOL : Material.GRAY_WOOL, theirReady ? "&a상대 준비 완료" : "&7상대 준비 중..."));
            set(49, button(Material.BARRIER, "&c거래 취소"), e -> me.closeInventory());
            fill(36, 53);
        }

        @Override
        public boolean editable(int raw) {
            return mine(raw);
        }

        @Override
        protected void onEditableClick(InventoryClickEvent e) {
            s.changed();
        }

        @Override
        protected void onBottomClick(InventoryClickEvent e) {
            if (!e.isShiftClick()) return;
            ItemStack cur = e.getCurrentItem();
            if (cur == null || cur.getType().isAir() || isFiller(cur)) return;
            for (int m : MINE) {
                if (inv.getItem(m) == null) {
                    inv.setItem(m, cur.clone());
                    e.setCurrentItem(null);
                    s.changed();
                    return;
                }
            }
        }

        @Override
        public void onClose(InventoryCloseEvent e) {
            if (s.done) return;
            cancel(s, me.getName() + "님이 거래를 취소했습니다.");
        }
    }

    private void startCountdown(Session s) {
        s.countdown = 3;
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (s.done || s.countdown < 0 || !(s.readyA && s.readyB)) { task.cancel(); return; }
            if (s.countdown == 0) {
                task.cancel();
                complete(s);
                return;
            }
            for (Player p : List.of(s.a, s.b)) {
                Text.actionBar(p, "&a거래 " + s.countdown + "초 전...");
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1.5f);
            }
            s.countdown--;
        }, 0L, 20L);
    }

    private void complete(Session s) {
        if (s.done) return;
        if (!s.a.isOnline() || !s.b.isOnline() || s.a.isDead() || s.b.isDead()) {
            cancel(s, "상대가 없어 거래가 취소되었습니다.");
            return;
        }
        if (!plugin.economy().has(s.a, s.moneyA) || !plugin.economy().has(s.b, s.moneyB)) {
            cancel(s, "소지금이 부족해 거래가 취소되었습니다.");
            return;
        }
        s.done = true;
        ItemStack[] fromA = s.ga.offer(), fromB = s.gb.offer();
        for (int m : MINE) { s.ga.getInventory().setItem(m, null); s.gb.getInventory().setItem(m, null); }
        plugin.economy().take(s.a, s.moneyA);
        plugin.economy().take(s.b, s.moneyB);
        plugin.economy().give(s.b.getUniqueId(), s.moneyA);
        plugin.economy().give(s.a.getUniqueId(), s.moneyB);
        give(s.b, fromA);
        give(s.a, fromB);
        end(s);
        for (Player p : List.of(s.a, s.b)) {
            p.closeInventory();
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_YES, 1f, 1f);
            Text.msg(p, "&a거래가 완료되었습니다.");
        }
        plugin.getLogger().info("[거래] " + s.a.getName() + " ⇄ " + s.b.getName() + " (" + s.moneyA + "원 / " + s.moneyB + "원)");
    }

    private void cancel(Session s, String msg) {
        if (s.done) return;
        s.done = true;
        give(s.a, s.ga.offer());
        give(s.b, s.gb.offer());
        for (int m : MINE) { s.ga.getInventory().setItem(m, null); s.gb.getInventory().setItem(m, null); }
        end(s);
        for (Player p : List.of(s.a, s.b)) {
            Text.msg(p, "&c" + msg);
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof TradeGui) Bukkit.getScheduler().runTask(plugin, () -> p.closeInventory());
        }
    }

    private void end(Session s) {
        sessions.remove(s.a.getUniqueId());
        sessions.remove(s.b.getUniqueId());
    }

    private void give(Player p, ItemStack[] items) {
        for (ItemStack it : items) {
            if (it == null || it.getType().isAir() || Gui.isFiller(it)) continue;
            for (ItemStack left : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
        }
    }

    /** 거래 중 접속 종료 · 사망 → 즉시 취소하고 물건은 각자에게 돌려준다 (복제 방지) */
    public void abort(Player p, String why) {
        Session s = sessions.get(p.getUniqueId());
        if (s != null) {
            try {
                cancel(s, why);
            } catch (Exception e) {
                plugin.getLogger().warning("[Trade] abort failed: " + e);
            }
        }
        requests.remove(p.getUniqueId());
        requests.values().removeIf(v -> v.equals(p.getUniqueId()));
    }

    public void shutdown() {
        for (Session s : new HashSet<>(sessions.values())) cancel(s, "서버가 종료되어 거래가 취소되었습니다.");
    }
}
