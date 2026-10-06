package io.versaera.platform.bukkit.command;

import io.versaera.application.GameServices;
import io.versaera.domain.item.ItemInstance;
import io.versaera.platform.bukkit.Async;
import io.versaera.platform.bukkit.Ui;
import io.versaera.platform.bukkit.binding.ItemCodec;
import io.versaera.platform.bukkit.boss.BossRuntime;
import io.versaera.platform.bukkit.listener.NpcListener;
import io.versaera.security.Sealer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Consumer;

/**
 * 관리자 전용 /versaadmin (/va) — 일반 플레이어 명령과 분리. 권한 versaera.admin.
 * inspect · item · audit · give · money · npc spawn · boss spawn|stop · seal · perf
 */
public final class AdminCommand implements CommandExecutor, TabCompleter {
    private final GameServices s;
    private final Async async;
    private final ItemCodec codec;
    private final NpcListener npcs;
    private final BossRuntime bosses;
    private final File dataFolder;
    private final Sealer sealer;
    private final Consumer<Player> deliver;

    public AdminCommand(GameServices s, Async async, ItemCodec codec, NpcListener npcs, BossRuntime bosses, File dataFolder, Sealer sealer,
                        Consumer<Player> deliver) {
        this.s = s;
        this.async = async;
        this.codec = codec;
        this.npcs = npcs;
        this.bosses = bosses;
        this.dataFolder = dataFolder;
        this.sealer = sealer;
        this.deliver = deliver;
    }

    private static String uuidOf(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId().toString();
        OfflinePlayer o = Bukkit.getOfflinePlayer(name);
        return o.hasPlayedBefore() ? o.getUniqueId().toString() : null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] a) {
        if (!sender.hasPermission("versaera.admin")) { sender.sendMessage(Ui.error("권한이 없습니다")); return true; }
        String sub = a.length == 0 ? "help" : a[0].toLowerCase(Locale.ROOT);
        String req = "admin:" + (sender instanceof Player p ? p.getUniqueId() : "console") + ":" + UUID.randomUUID();
        try {
            run(sender, sub, a, req);
        } catch (NumberFormatException e) {
            sender.sendMessage(Ui.error("숫자가 잘못되었습니다: " + e.getMessage()));
        } catch (IllegalArgumentException | io.versaera.domain.common.DomainException e) {
            sender.sendMessage(Ui.error(e.getMessage()));
        }
        return true;
    }

    private void run(CommandSender sender, String sub, String[] a, String req) {
        switch (sub) {
            case "inspect" -> {
                String id = a.length > 1 ? uuidOf(a[1]) : null;
                if (id == null) { sender.sendMessage(Ui.error("/va inspect <이름>")); return; }
                async.run("inspect", () -> List.of("돈 " + s.economy.balance(id), "숙련 " + s.progress.allMastery(id), "스탯 " + s.growth.statPoints(id),
                        "기록 " + s.progress.allCounters(id).size() + "개", "보유 아이템 " + s.itemRepo.byCustody(io.versaera.domain.item.Custody.player(id)).size()),
                        lines -> lines.forEach(l -> sender.sendMessage(Ui.info(l))), sender);
            }
            case "item" -> {
                if (!(sender instanceof Player p)) return;
                String iid = codec.instanceId(p.getInventory().getItemInMainHand());
                if (iid == null) { sender.sendMessage(Ui.error("손에 든 고유 아이템이 없습니다")); return; }
                async.run("item-inspect", () -> {
                    ItemInstance it = s.items.find(iid).orElse(null);
                    return it == null ? List.of("DB 에 없음 (위조 의심)") : List.of(it.typeId() + " q=" + it.quality() + " " + it.durability() + "/" + it.maxDurability(),
                            "제작 " + it.creatorName() + " · " + it.method(), "보관 " + it.custody(), "속성 " + it.props(), "내력 " + s.itemRepo.historyOf(iid));
                }, lines -> lines.forEach(l -> sender.sendMessage(Ui.info(l))), sender);
            }
            case "audit" -> {
                String action = a.length > 1 ? a[1].toUpperCase(Locale.ROOT) : "TRADE_COMPLETED";
                async.run("audit", () -> s.audit.recent(action, 10), lines -> lines.forEach(l -> sender.sendMessage(Ui.c("&7" + l))), sender);
            }
            case "give" -> {   // /va give <이름> <아이템> [품질] [수량]
                if (a.length < 3) { sender.sendMessage(Ui.error("/va give <이름> <아이템> [품질] [수량]")); return; }
                String id = uuidOf(a[1]);
                if (id == null || !codec.types().has(a[2])) { sender.sendMessage(Ui.error("이름 또는 아이템이 잘못되었습니다")); return; }
                int q = a.length > 3 ? Integer.parseInt(a[3]) : 500, n = a.length > 4 ? Integer.parseInt(a[4]) : 1;
                boolean unique = codec.types().get(a[2]).category().unique();
                async.run("admin-give", () -> {
                    if (unique) for (int i = 0; i < Math.min(n, 36); i++) s.items.create(a[2], q, null, "관리자", "admin", Map.of(), id, req);
                    else s.items.deliverBulk(id, a[2], q, n, "admin");
                    return true;
                }, ok -> {
                    sender.sendMessage(Ui.info("지급 → 배달함"));
                    Player t = Bukkit.getPlayerExact(a[1]);
                    if (t != null) deliver.accept(t);
                }, sender);
            }
            case "money" -> {   // /va money <이름> <+금액|-금액>
                if (a.length < 3) { sender.sendMessage(Ui.error("/va money <이름> <+금액|-금액>")); return; }
                String id = uuidOf(a[1]);
                long v = Long.parseLong(a[2]);
                if (id == null || v == 0) { sender.sendMessage(Ui.error("이름 또는 금액이 잘못되었습니다")); return; }
                async.run("admin-money", () -> v > 0 ? s.economy.deposit(id, v, "admin", req) : s.economy.withdraw(id, -v, "admin", req),
                        ok -> sender.sendMessage(Ui.info("잔액 반영")), sender);
            }
            case "npc" -> {
                if (!(sender instanceof Player p) || a.length < 3 || !a[1].equals("spawn")) { sender.sendMessage(Ui.error("/va npc spawn <id>")); return; }
                npcs.spawn(s.relations.npc(a[2]), p.getLocation());
                sender.sendMessage(Ui.info("NPC " + a[2]));
            }
            case "boss" -> {
                if (a.length >= 2 && a[1].equals("stop")) { sender.sendMessage(Ui.info("보스 " + bosses.stopAll() + "마리 제거")); return; }
                if (!(sender instanceof Player p) || a.length < 3 || !a[1].equals("spawn")) { sender.sendMessage(Ui.error("/va boss spawn <id> · /va boss stop")); return; }
                bosses.spawn(a[2], p.getLocation());
            }
            case "seal" -> seal(sender);
            case "perf" -> {
                Runtime rt = Runtime.getRuntime();
                sender.sendMessage(Ui.info("지역 " + s.regions.all().size() + " · 레시피 " + s.crafting.all().size() + " · 히든 " + (s.hidden() == null ? 0 : s.hidden().ruleCount())
                        + " · 메모리 " + (rt.totalMemory() - rt.freeMemory()) / 1048576 + "MB"));
            }
            default -> sender.sendMessage(Ui.info("inspect · item · audit · give · money · npc spawn · boss spawn|stop · seal · perf"));
        }
    }

    /** hidden-src/*.yml 을 합쳐 hidden.sealed 로 봉인 (다음 재시작부터 적용). 원본 폴더는 서버에서 치우라고 알려 준다. */
    private void seal(CommandSender sender) {
        File src = new File(dataFolder, "hidden-src");
        File[] files = src.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null || files.length == 0) { sender.sendMessage(Ui.error("hidden-src/*.yml 이 없습니다")); return; }
        StringBuilder all = new StringBuilder("hidden:\n");
        try {
            for (File f : files) {
                String text = Files.readString(f.toPath(), StandardCharsets.UTF_8);
                Map<String, Object> root = io.versaera.content.ContentLoader.parse(text, f.getName());
                io.versaera.content.ContentLoader.hidden(root, f.getName());   // 형식 검사
                for (String line : text.split("\\R")) if (!line.startsWith("hidden:") && !line.isBlank()) all.append(line).append('\n');
            }
            Files.writeString(new File(dataFolder, "hidden.sealed").toPath(), sealer.seal(all.toString()), StandardCharsets.UTF_8);
            sender.sendMessage(Ui.info("봉인 완료 · " + files.length + "개 파일 · 재시작 후 적용"));
            sender.sendMessage(Ui.error("hidden-src 폴더를 서버에서 치우세요 (평문 조건)"));
        } catch (IOException | RuntimeException e) {
            sender.sendMessage(Ui.error("봉인 실패: " + e.getMessage()));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] a) {
        if (!sender.hasPermission("versaera.admin")) return List.of();
        if (a.length == 1) return filter(List.of("inspect", "item", "audit", "give", "money", "npc", "boss", "seal", "perf"), a[0]);
        if (a.length == 3 && a[0].equals("give")) return filter(codec.types().all().stream().map(t -> t.id()).toList(), a[2]);
        if (a.length == 3 && a[0].equals("boss")) return filter(s.content.bosses().stream().map(b -> b.id()).toList(), a[2]);
        if (a.length == 3 && a[0].equals("npc")) return filter(s.relations.all().stream().map(n -> n.id()).toList(), a[2]);
        return null;
    }

    private static List<String> filter(List<String> l, String p) {
        return l.stream().filter(x -> x.startsWith(p)).toList();
    }
}
