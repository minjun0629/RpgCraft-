package io.versaera.platform.bukkit.command;

import io.versaera.application.GameServices;
import io.versaera.domain.skill.Discipline;
import io.versaera.domain.skill.Mastery;
import io.versaera.domain.world.Region;
import io.versaera.platform.bukkit.Async;
import io.versaera.platform.bukkit.Ui;
import io.versaera.platform.bukkit.binding.ItemCodec;
import io.versaera.platform.bukkit.ui.Menu;
import io.versaera.platform.bukkit.ui.TradeMenu;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.function.Consumer;

/**
 * 플레이어 명령은 최소한만: /versa (캐릭터 · 기록) · /versa 지도 · /거래 <이름> · /거래 수락.
 * 나머지 플레이는 월드 상호작용(제작대 · NPC · 자원 · 보스)으로 한다.
 */
public final class PlayerCommand implements CommandExecutor {
    private final GameServices s;
    private final Async async;
    private final ItemCodec codec;
    private final Consumer<Player> deliver;
    private final Map<UUID, UUID> requests = new HashMap<>();   // 받는 사람 → 보낸 사람

    public PlayerCommand(GameServices s, Async async, ItemCodec codec, Consumer<Player> deliver) {
        this.s = s;
        this.async = async;
        this.codec = codec;
        this.deliver = deliver;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        if (cmd.getName().equals("trade")) return trade(p, args);
        if (args.length > 0 && (args[0].equals("지도") || args[0].equalsIgnoreCase("map"))) map(p);
        else character(p);
        return true;
    }

    private void character(Player p) {
        String id = p.getUniqueId().toString();
        async.run("character", () -> new Object[]{s.progress.allMastery(id), s.growth.statPoints(id), s.economy.balance(id)}, r -> {
            @SuppressWarnings("unchecked") Map<String, Long> mastery = (Map<String, Long>) r[0];
            @SuppressWarnings("unchecked") Map<String, Integer> stats = (Map<String, Integer>) r[1];
            long money = (long) r[2];
            Menu m = new Menu(4, "&8" + p.getName());
            m.set(4, Menu.icon(Material.GOLD_INGOT, "&e" + money, List.of()), null);
            int slot = 9;
            List<Map.Entry<String, Long>> top = new ArrayList<>(mastery.entrySet());
            top.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
            for (Map.Entry<String, Long> e : top) {
                if (slot > 26) break;
                Discipline d = s.growth.discipline(e.getKey());
                int lv = Mastery.levelOf(e.getValue());
                m.set(slot++, Menu.icon(iconOf(d), "&f" + d.name(), List.of("&7" + Mastery.label(lv), "&8" + Math.round(Mastery.progress(e.getValue()) * 100) + "%")), null);
            }
            slot = 27;
            for (Map.Entry<String, Integer> e : stats.entrySet()) {
                if (slot > 35) break;
                String name = s.growth.stats().stream().filter(x -> x.id().equals(e.getKey())).findFirst().map(x -> x.name()).orElse(e.getKey());
                m.set(slot++, Menu.icon(Material.NETHER_STAR, "&f" + name + " &e" + e.getValue(), List.of()), null);
            }
            m.open(p);
        }, p);
    }

    private static Material iconOf(Discipline d) {
        return switch (d.category()) {
            case COMBAT -> Material.IRON_SWORD;
            case GATHERING -> Material.IRON_PICKAXE;
            case PRODUCTION -> Material.ANVIL;
            case ART -> Material.ARMOR_STAND;
            case SOCIAL -> Material.EMERALD;
            case EXPLORATION -> Material.COMPASS;
            case SUPPORT -> Material.SHEARS;
        };
    }

    /** 지도 (MAP-01 PARTIAL): 발견한 지역만, 이름 · 위험도 · 최초 발견자. 미발견 지역은 "???" */
    private void map(Player p) {
        String id = p.getUniqueId().toString();
        async.run("map", () -> {
            List<Object[]> out = new ArrayList<>();
            for (Region r : s.regions.all()) {
                boolean known = s.exploration.discovered(id, "region", r.id());
                String first = known ? s.exploration.worldFirst("region", r.id()).map(w -> w.name()).orElse("") : "";
                out.add(new Object[]{r, known, first});
            }
            return out;
        }, rows -> {
            Menu m = new Menu(3, "&8지도");
            int slot = 0;
            for (Object[] row : rows) {
                if (slot >= 27) break;
                Region r = (Region) row[0];
                boolean known = (boolean) row[1];
                m.set(slot++, known
                        ? Menu.icon(r.danger() == 0 ? Material.LIME_BANNER : r.danger() <= 2 ? Material.YELLOW_BANNER : r.danger() <= 4 ? Material.ORANGE_BANNER : Material.RED_BANNER,
                        "&f" + r.name(), List.of(Ui.danger(r.danger()), "&8" + row[2]))
                        : Menu.icon(Material.GRAY_STAINED_GLASS_PANE, "&8???", List.of()), null);
            }
            m.open(p);
        }, p);
    }

    private boolean trade(Player p, String[] args) {
        if (args.length == 1 && (args[0].equals("수락") || args[0].equalsIgnoreCase("accept"))) {
            UUID from = requests.remove(p.getUniqueId());
            Player o = from == null ? null : Bukkit.getPlayer(from);
            if (o == null) { p.sendMessage(Ui.error("받은 거래 요청이 없습니다")); return true; }
            if (o.getLocation().getWorld() != p.getWorld() || o.getLocation().distance(p.getLocation()) > 8) {
                p.sendMessage(Ui.error("가까이 있어야 거래할 수 있습니다"));
                return true;
            }
            String a = o.getUniqueId().toString(), b = p.getUniqueId().toString();
            async.run("trade-open", () -> s.trades.open(a, b).id(), tid -> {
                for (Player x : List.of(o, p)) {
                    TradeMenu tm = new TradeMenu(s, async, codec, x, tid, deliver);
                    tm.open(x);
                    tm.refresh();
                }
            }, p);
            return true;
        }
        if (args.length != 1) { p.sendMessage(Ui.error("/거래 <이름> · /거래 수락")); return true; }
        Player t = Bukkit.getPlayerExact(args[0]);
        if (t == null || t.equals(p)) { p.sendMessage(Ui.error("상대를 찾을 수 없습니다")); return true; }
        requests.put(t.getUniqueId(), p.getUniqueId());
        p.sendMessage(Ui.info(t.getName() + " 님에게 거래 요청"));
        t.sendMessage(Ui.info(p.getName() + " 님의 거래 요청 · /거래 수락"));
        return true;
    }
}
