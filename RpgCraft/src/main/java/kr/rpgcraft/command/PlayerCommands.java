package kr.rpgcraft.command;

import kr.rpgcraft.Keys;
import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.data.PlayerData;
import kr.rpgcraft.economy.ShopManager;
import kr.rpgcraft.feature.JobManager;
import kr.rpgcraft.gui.Gui;
import kr.rpgcraft.guild.Guild;
import kr.rpgcraft.item.ItemData;
import kr.rpgcraft.stat.StatSnapshot;
import kr.rpgcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** 일반 플레이어 명령어 모음 (명령어 이름으로 분기) */
public class PlayerCommands implements CommandExecutor, TabCompleter, org.bukkit.event.Listener {
    private final RpgCraft plugin;

    public PlayerCommands(RpgCraft plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] a) {
        String name = cmd.getName();
        if (name.equals("money") && !(sender instanceof Player)) {
            Text.msg(sender, "플레이어만 사용할 수 있습니다.");
            return true;
        }
        if (!(sender instanceof Player p)) {
            Text.msg(sender, "플레이어만 사용할 수 있습니다.");
            return true;
        }
        PlayerData d = plugin.data().get(p);
        switch (name) {
            case "menu" -> plugin.menu().open(p);
            case "stat" -> new StatGui(p).open(p);
            case "info" -> info(p, a.length > 0 ? Text.player(a[0]) : p);
            case "money" -> Text.msg(p, "&f소지금: &e" + Text.money(d.money));
            case "pay" -> {
                if (a.length < 2) { Text.msg(p, "&c/송금 <플레이어> <금액>"); return true; }
                Player t = Text.player(a[0]);
                long amt = Text.parseLong(a[1], -1);
                if (t == null || t.equals(p)) { Text.msg(p, "&c접속 중인 다른 플레이어를 입력하세요."); return true; }
                if (amt <= 0) { Text.msg(p, "&c금액이 올바르지 않습니다."); return true; }
                if (!plugin.economy().take(p, amt)) { Text.msg(p, "&c소지금이 부족합니다."); return true; }
                plugin.economy().give(t.getUniqueId(), amt);
                Text.msg(p, "&a" + Text.name(t) + "님에게 " + Text.money(amt) + "을 송금했습니다.");
                Text.msg(t, "&a" + Text.name(p) + "님이 " + Text.money(amt) + "을 송금했습니다.");
            }
            case "check" -> {
                long amt = a.length > 0 ? Text.parseLong(a[0], -1) : -1;
                if (amt < 1000) { Text.msg(p, "&c/수표 <금액> &7(1,000원 이상)"); return true; }
                if (!plugin.economy().take(p, amt)) { Text.msg(p, "&c소지금이 부족합니다."); return true; }
                ItemStack it = plugin.items().create("check", 1);
                ItemData.setDouble(it, Keys.VALUE, amt);
                ItemData.refresh(it);
                for (ItemStack left : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
                Text.msg(p, "&a" + Text.money(amt) + " 수표를 발행했습니다.");
            }
            case "potionbag" -> plugin.potions().open(p);
            case "rune" -> {
                if (a.length > 0 && (a[0].equals("변경") || a[0].equals("reroll"))) plugin.runes().reroll(p);
                else plugin.runes().open(p);
            }
            case "skill" -> plugin.passives().open(p);
            case "enhance" -> plugin.enhance().open(p);
            case "trade" -> {
                if (a.length == 0) { Text.msg(p, "&e/거래 <플레이어> &7| &e/거래 수락 &7| &e/거래 거절 &7| &e/거래 돈 <금액>"); return true; }
                switch (a[0]) {
                    case "수락", "accept" -> plugin.trades().accept(p);
                    case "거절", "deny" -> plugin.trades().deny(p);
                    case "돈", "money" -> plugin.trades().offerMoney(p, a.length > 1 ? Text.parseLong(a[1], -1) : -1);
                    default -> plugin.trades().request(p, a[0]);
                }
            }
            case "escape" -> escape(p);
            case "casino" -> plugin.casino().open(p);
            case "call" -> callAdmin(p, String.join(" ", a));
            case "dismantle" -> plugin.dismantle().open(p);   // 장비 → 재료
            case "pack" -> {   // 리소스팩 다시 받기 (적용 실패 시)
                Text.msg(p, "&e리소스팩을 다시 보냅니다...");
                plugin.pack().resend(p);
            }
            case "coupon" -> {
                if (a.length == 0) { Text.msg(p, "&e/쿠폰 <코드>" + (p.hasPermission("rpgcraft.admin") ? " &7· 관리자: /쿠폰 list" : "")); return true; }
                if (a.length == 1 && (a[0].equalsIgnoreCase("list") || a[0].equals("목록")) && p.hasPermission("rpgcraft.admin")) {
                    listCoupons(p);
                    return true;
                }
                String code = String.join(" ", a).trim();
                java.util.List<String> rewards = plugin.getConfig().getStringList("coupons." + code);
                if (rewards.isEmpty()) { Text.msg(p, "&c없는 쿠폰입니다."); return true; }
                if (d.counters.putIfAbsent("coupon_" + code, 1.0) != null) { Text.msg(p, "&c이미 사용한 쿠폰입니다."); return true; }
                for (String r : rewards) {
                    String[] kv = r.split(":");
                    if (kv[0].equals("money")) { plugin.economy().give(p, Long.parseLong(kv[1])); continue; }
                    org.bukkit.inventory.ItemStack it = plugin.items().create(kv[0], kv.length > 1 ? Integer.parseInt(kv[1]) : 1);
                    if (it != null) for (org.bukkit.inventory.ItemStack l : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), l);
                }
                p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
                Text.msg(p, "&a쿠폰 보상을 받았습니다!");
            }
            case "rebirth" -> {
                if (a.length > 0 && (a[0].equals("상점") || a[0].equals("shop"))) plugin.rebirthShop().open(p);
                else rebirth(p, a.length > 0 && a[0].equals("확인"));
            }
            case "rebirthshop" -> plugin.rebirthShop().open(p);
            case "potential" -> plugin.potentials().open(p);
            case "bounty" -> plugin.content().tellBounty(p);
            case "dummy" -> plugin.dummies().spawn(p);
            case "runefuse" -> plugin.runeFusion().open(p);
            case "tpa" -> {   // 순간이동 요청
                if (a.length < 1) { Text.msg(p, "&e/tpa <플레이어> &7(이름 입력 중 Tab 키로 자동완성)"); return true; }
                Player t = Text.player(a[0]);
                if (t == null || t.equals(p)) { Text.msg(p, "&c접속 중인 다른 플레이어를 입력하세요."); return true; }
                if (d.onCooldown("tpa") && !p.hasPermission("rpgcraft.admin")) {
                    long left = d.remaining("tpa") / 1000 + 1;
                    Text.msg(p, "&c순간이동 쿨타임 " + (left / 60 > 0 ? left / 60 + "분 " : "") + left % 60 + "초 남았습니다.");
                    return true;
                }
                tpaReq.put(t.getUniqueId(), new Object[]{p.getUniqueId(), System.currentTimeMillis()});
                Text.msg(p, "&a" + Text.name(t) + "님에게 순간이동을 요청했습니다.");
                // 받은 사람은 채팅의 [수락] / [거절] 을 클릭 (명령어 /tpaccept · /tpdeny 도 그대로 됨)
                kr.rpgcraft.feature.TradeManager.buttons(t, Text.c(Text.PREFIX + "&e" + Text.name(p) + "&f님이 순간이동을 요청했습니다 &7(60초) "), "/tpaccept", "/tpdeny");
                t.playSound(t.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.4f);
            }
            case "tpaccept", "tpdeny" -> {
                Object[] r = tpaReq.remove(p.getUniqueId());
                if (r == null || System.currentTimeMillis() - (long) r[1] > 60_000) { Text.msg(p, "&7받은 요청이 없습니다."); return true; }
                Player from = Bukkit.getPlayer((java.util.UUID) r[0]);
                if (from == null) { Text.msg(p, "&7요청한 플레이어가 없습니다."); return true; }
                if (name.equals("tpdeny")) { Text.msg(from, "&c" + Text.name(p) + "님이 순간이동을 거절했습니다."); Text.msg(p, "&7거절했습니다."); return true; }
                if (plugin.dungeons() != null && (plugin.dungeons().runOf(from) != null || plugin.dungeons().runOf(p) != null)) { Text.msg(p, "&c던전 안에서는 할 수 없습니다."); return true; }
                if (plugin.tower() != null && (plugin.tower().inRun(from) || plugin.tower().inRun(p))) { Text.msg(p, "&c무한의 탑 안에서는 할 수 없습니다."); return true; }
                Text.msg(from, "&a3초 뒤 이동합니다. 움직이지 마세요.");
                org.bukkit.Location start = from.getLocation().clone();
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!from.isOnline() || !p.isOnline()) return;
                    if (from.getLocation().distanceSquared(start) > 1) { Text.msg(from, "&c움직여서 취소되었습니다."); return; }
                    from.teleport(p.getLocation());
                    plugin.data().get(from).cooldown("tpa", plugin.getConfig().getLong("tpa.cooldown-seconds", 300) * 1000L);   // 이동 성공 시 5분 쿨타임
                    from.playSound(from.getLocation(), org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.2f);
                }, 60L);
            }
            case "ticket" -> {   // 문의
                if (a.length == 0) { Text.msg(p, "&e/티켓 <문의 내용>"); return true; }
                String msg = String.join(" ", a);
                java.io.File f = new java.io.File(plugin.getDataFolder(), "tickets.yml");
                var y = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f);
                int id = y.getInt("next", 1);
                y.set("next", id + 1);
                y.set("open." + id + ".player", p.getName());
                y.set("open." + id + ".message", msg);
                y.set("open." + id + ".time", new java.text.SimpleDateFormat("MM-dd HH:mm").format(new java.util.Date()));
                try { y.save(f); } catch (java.io.IOException ignored) { }
                Text.msg(p, "&a문의 #" + id + " 이(가) 접수되었습니다.");
                for (Player op : Bukkit.getOnlinePlayers()) if (op.hasPermission("rpgcraft.admin")) Text.msg(op, "&d[티켓 #" + id + "] &f" + Text.name(p) + "(" + p.getName() + ")&7: &f" + msg);
            }
            case "partychat" -> {   // 파티 채팅
                var party = plugin.party().of(p);
                if (party == null) { Text.msg(p, "&c파티가 없습니다."); return true; }
                if (a.length == 0) { Text.msg(p, "&e/pc <메시지>"); return true; }
                String msg = Text.c("&b[파티] &f" + Text.name(p) + "&7: &b") + String.join(" ", a);
                for (Player m : party.online()) m.sendMessage(msg);
            }
            case "nick" -> {   // 이름 바꾸기 — 채팅 · 목록 · 머리 위 이름표 · 공지 · 메뉴 등 보이는 모든 곳
                if (a.length == 0) { Text.msg(p, "&e/닉네임 <새 이름> &7(2~12자, 한글·영문·숫자) · &e/닉네임 해제"); return true; }
                if (a[0].equals("해제") || a[0].equalsIgnoreCase("reset")) {
                    d.nick = null;
                    if (plugin.nicks() != null) plugin.nicks().apply(p);
                    Text.msg(p, "&a닉네임을 해제했습니다. &7(" + p.getName() + ")");
                    return true;
                }
                String nn = a[0];
                if (!nn.matches("[가-힣A-Za-z0-9_]{2,12}")) { Text.msg(p, "&c2~12자의 한글·영문·숫자만 쓸 수 있습니다."); return true; }
                for (Player op : Bukkit.getOnlinePlayers())   // 다른 사람의 닉네임 · 계정 이름과 겹치면 안 됨 (사칭 방지)
                    if (!op.equals(p) && (nn.equalsIgnoreCase(Text.name(op)) || nn.equalsIgnoreCase(op.getName()))) { Text.msg(p, "&c이미 쓰는 이름입니다."); return true; }
                d.nick = nn;
                if (plugin.nicks() != null) plugin.nicks().apply(p);
                else { p.setDisplayName(nn); p.setPlayerListName(nn); }
                Text.msg(p, "&a이름을 &f" + nn + "&a(으)로 바꿨습니다. &7(채팅 · 목록 · 머리 위 이름 · 공지 등 모든 곳)");
            }
            case "enderchest" -> p.openInventory(p.getEnderChest());
            case "limitbreak" -> plugin.limitBreak().open(p);
            case "trash" -> {
                org.bukkit.inventory.Inventory inv = Bukkit.createInventory(new TrashHolder(), 54, Text.c("&8쓰레기통 &7(닫으면 사라짐)"));
                p.openInventory(inv);
            }
            case "guidebook" -> p.getInventory().addItem(guideBook());
            case "quickkey" -> {
                if (a.length > 0) {
                    int m = kr.rpgcraft.feature.MenuManager.parseQuickKey(String.join("", a));
                    if (m < 0) { Text.msg(p, "&e/퀵키 <쉬프트Q | Q | F | 쉬프트F | 1~9 | 쉬프트1~9>"); return true; }
                    d.counters.put("quick_key", (double) m);
                }
                Text.msg(p, "&b퀵 스킬 키: &f" + kr.rpgcraft.feature.MenuManager.quickKeyName((int) d.counter("quick_key")));
            }
            case "accessory" -> {
                if (a.length > 0 && a[0].equals("변경")) plugin.accessories().reroll(p);
                else plugin.accessories().open(p);
            }
            case "job" -> {
                if (a.length == 0) { plugin.jobs().open(p); return true; }
                if (a[0].equals("선택") && a.length > 1) {
                    for (JobManager.Base b : JobManager.Base.values()) if (b.label.equals(a[1]) || b.name().equalsIgnoreCase(a[1])) { plugin.jobs().choose(p, b); return true; }
                    Text.msg(p, "&c전사 / 궁수 / 도적 / 수호자 / 마법사 중에서 고르세요.");
                } else if (a[0].equals("전직")) plugin.jobs().advance(p);
                else if (a[0].equals("대장장이") || a[0].equalsIgnoreCase("blacksmith")) plugin.blacksmith().changeJob(p);
                else Text.msg(p, "&e/직업 대장장이 &7- 레벨 " + plugin.getConfig().getInt("blacksmith.required-level", 20)
                        + ", " + Text.money(plugin.getConfig().getLong("blacksmith.cost", 1_000_000)) + " &7(현재: " + (d.blacksmith ? "대장장이" : "없음") + ")");
            }
            case "craft" -> plugin.blacksmith().open(p);
            case "rename" -> {
                if (a.length == 0) { Text.msg(p, "&c/명명 <이름> &7(& 색코드 가능)"); return true; }
                plugin.blacksmith().rename(p, String.join(" ", a));
            }
            case "look" -> plugin.blacksmith().look(p, a.length > 0 ? a[0] : "");
            case "essence" -> plugin.spirits().open(p);
            case "gc" -> {
                Guild g = plugin.guilds().of(p.getUniqueId());
                if (g == null) { Text.msg(p, "&c길드가 없습니다."); return true; }
                if (a.length == 0) {
                    d.guildChat = !d.guildChat;
                    Text.msg(p, d.guildChat ? "&a길드 채팅 모드 켜짐" : "&7길드 채팅 모드 꺼짐");
                } else g.broadcast(Text.c("&a[길드] &f" + Text.name(p) + "&7: &a") + String.join(" ", a));
            }
            case "shop" -> {
                if (a.length == 0) {
                    Text.msg(p, "&e명령어로 열 수 있는 상점:");
                    for (ShopManager.Shop s : plugin.shops().all())
                        if (s.command || p.hasPermission("rpgcraft.admin")) p.sendMessage(Text.c(" &f/상점 " + s.id + " &7- " + s.title));
                    return true;
                }
                if (a[0].equals("hidden")) { Text.msg(p, "&d히든 상점은 맵 어딘가에 나타나는 히든 상인에게서만 이용할 수 있습니다."); return true; }
                ShopManager.Shop s = plugin.shops().get(a[0]);
                if (s == null || (!s.command && !p.hasPermission("rpgcraft.admin"))) { Text.msg(p, "&c이 상점은 NPC를 통해서만 이용할 수 있습니다."); return true; }
                plugin.shops().open(p, a[0], 0);
            }
            case "absorb" -> {
                if (a.length == 0) { Text.msg(p, "&c/흡수 <플레이어>"); return true; }
                plugin.passives().designateAbsorb(p, a[0]);
            }
            default -> { return false; }
        }
        return true;
    }

    /** /호출 <내용> : 접속 중인 관리자에게 알림 (클릭하면 텔레포트). 관리자가 없으면 접속할 때 전달 */
    private final java.util.List<String> pendingCalls = new java.util.ArrayList<>();

    private void callAdmin(Player p, String msg) {
        PlayerData d = plugin.data().get(p);
        if (d.onCooldown("call")) { Text.msg(p, "&c잠시 후 다시 호출할 수 있습니다. (" + (d.remaining("call") / 1000 + 1) + "초)"); return; }
        if (msg.isBlank()) { Text.msg(p, "&e/호출 <내용> &7- 관리자에게 도움을 요청합니다"); return; }
        d.cooldown("call", 60_000);
        String line = Text.c("&c&l[호출] &e" + p.getName() + " &7(" + p.getWorld().getName() + " " + p.getLocation().getBlockX() + ", " + p.getLocation().getBlockY() + ", " + p.getLocation().getBlockZ() + ") &f" + msg);
        int n = 0;
        for (Player op : Bukkit.getOnlinePlayers()) {
            if (!op.hasPermission("rpgcraft.admin")) continue;
            var comp = new net.md_5.bungee.api.chat.TextComponent(net.md_5.bungee.api.chat.TextComponent.fromLegacyText(line + Text.c(" &b[이동]")));
            comp.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/tp " + p.getName()));
            op.spigot().sendMessage(comp);
            op.playSound(op.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.5f);
            n++;
        }
        plugin.getLogger().info("[호출] " + p.getName() + ": " + msg);
        if (n == 0) pendingCalls.add(line);
        Text.msg(p, n > 0 ? "&a관리자 " + n + "명에게 호출을 보냈습니다." : "&e지금 접속한 관리자가 없어 다음에 접속하면 전달됩니다.");
    }

    public void deliverCalls(Player op) {
        if (pendingCalls.isEmpty() || !op.hasPermission("rpgcraft.admin")) return;
        Text.msg(op, "&c접속하지 않은 동안 호출 " + pendingCalls.size() + "건:");
        for (String l : pendingCalls) op.sendMessage(l);
        pendingCalls.clear();
    }

    private final java.util.Map<java.util.UUID, Object[]> tpaReq = new java.util.HashMap<>();

    public static class TrashHolder implements org.bukkit.inventory.InventoryHolder {
        public org.bukkit.inventory.Inventory getInventory() { return null; }
    }

    @org.bukkit.event.EventHandler
    public void onTrashClose(org.bukkit.event.inventory.InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof TrashHolder)) return;
        int n = 0;
        for (org.bukkit.inventory.ItemStack it : e.getInventory().getContents()) if (it != null) n += it.getAmount();
        e.getInventory().clear();
        if (n > 0) Text.actionBar((Player) e.getPlayer(), "&7" + n + "개를 버렸습니다.");
    }

    /** /가이드 : 기초 정보가 적힌 책 */
    private org.bukkit.inventory.ItemStack guideBook() {
        org.bukkit.inventory.ItemStack book = new org.bukkit.inventory.ItemStack(org.bukkit.Material.WRITTEN_BOOK);
        var m = (org.bukkit.inventory.meta.BookMeta) book.getItemMeta();
        m.setTitle("모험가 안내서");
        m.setAuthor("RpgCraft");
        m.addPage(
                "§l§6모험가 안내서§r\n\n§0환영합니다!\n\n§8· 쉬프트+F§0 메뉴\n§8· F§0 포션 마시기\n§8· 좌클릭§0 참격\n§8· 우클릭§0 무기 스킬\n§8· Q§0 직업 스킬\n§8· /길라잡이§0 시스템 안내",
                "§l§6레벨 · 스탯§r\n\n§0레벨업마다 스탯 포인트.\n\n§c힘§0 공격력\n§a민첩§0 치명타·속도\n§b모험§0 체력·방어\n\n장비는 §l직접 찍은 스탯§r§0으로 조건을 채워야 합니다.",
                "§l§6성장§r\n\n§0· Lv.10 직업\n· Lv.40 2차 전직\n· Lv.100 3차 전직\n· 강화 최대 +15\n· 룬 · 장신구 · 잠재능력\n· 최대 레벨에서 환생 (+300 레벨)",
                "§l§6사냥 요령§r\n\n§0· 나보다 높은 몬스터는 피해가 잘 안 들어갑니다.\n· 빨간 고리 = 곧 공격!\n· 낮엔 인간형, 밤엔 몬스터가 많습니다.\n· 보스는 기여도 7% 이상이어야 보상.",
                "§l§6돈 벌기§r\n\n§0· 전리품은 /상점 loot\n· 무엇이든 /상점 pawn\n· 의뢰 NPC · 현상수배\n· 보물 지도\n· /옥션 거래\n\n§8행운을 빕니다!");
        book.setItemMeta(m);
        return book;
    }

    /** 관리자: /쿠폰 list — 등록된 쿠폰 코드와 보상 전부 */
    private void listCoupons(Player p) {
        var sec = plugin.getConfig().getConfigurationSection("coupons");
        if (sec == null || sec.getKeys(false).isEmpty()) { Text.msg(p, "&7등록된 쿠폰이 없습니다. &8(config.yml 의 coupons)"); return; }
        Text.msg(p, "&6&l쿠폰 목록 &7(" + sec.getKeys(false).size() + "개)");
        for (String code : sec.getKeys(false)) {
            java.util.List<String> names = new java.util.ArrayList<>();
            for (String r : sec.getStringList(code)) {
                String[] kv = r.split(":");
                if (kv[0].equals("money")) { names.add("&e" + Text.money(kv.length > 1 ? Text.parseLong(kv[1], 0) : 0)); continue; }
                var t = plugin.items().get(kv[0]);
                names.add("&f" + (t == null ? kv[0] : t.name) + (kv.length > 1 ? " &7x" + kv[1] : ""));
            }
            int used = 0;
            for (Player op : Bukkit.getOnlinePlayers()) if (plugin.data().get(op).counter("coupon_" + code) > 0) used++;
            p.sendMessage(Text.c(" &a" + code + " &8→ " + String.join("&7, ", names) + " &8(접속자 중 사용 " + used + "명)"));
        }
    }

    private void rebirth(Player p, boolean confirm) {
        PlayerData d = plugin.data().get(p);
        var c = plugin.getConfig();
        int max = plugin.levels().maxLevel(d), n = (int) d.counter("rebirth"), cap = c.getInt("rebirth.max", 5);
        if (d.level < max) { Text.msg(p, "&c레벨 " + max + " 필요 &7(환생 " + n + "/" + cap + ")"); return; }
        if (n >= cap) { Text.msg(p, "&c더 이상 환생할 수 없습니다."); return; }
        if (!confirm) { Text.msg(p, "&d환생 상점: &e/환생 상점 &7(환생 포인트로 영구 강화)"); Text.msg(p, "&e/환생 확인 &7— 레벨만 1로 초기화 (직업 · 히든 직업 · 스탯 · 장비 · 돈 등 나머지는 모두 유지)"); return; }
        String job = d.job, sub = d.subJob, third = d.thirdJob;   // 직업은 환생해도 그대로 (1·2·3차 · 히든 직업 모두)
        boolean smith = d.blacksmith;
        d.counters.put("rebirth", n + 1.0);
        d.level = 1;   // 환생하면 레벨만 처음부터 (최대 레벨은 +300). 스탯 · 스탯 포인트 · 장비 · 돈은 그대로
        d.exp = 0;
        d.job = job;
        d.subJob = sub;
        d.thirdJob = third;
        d.blacksmith = smith;
        plugin.rebirthShop().onRebirth(p);   // 환생 포인트
        plugin.stats().refresh(p);
        p.sendTitle(Text.c("&d&l✦ 환생 " + (n + 1) + " ✦"), Text.c("&f최대 레벨 " + plugin.levels().maxLevel(d) + " &7· 직업 " + plugin.jobs().title(d) + " 유지"), 10, 70, 20);
        p.playSound(p.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.6f);
        p.getWorld().strikeLightningEffect(p.getLocation());
        Text.announce(Text.PREFIX + Text.c("&d&l" + Text.name(p) + "&f님이 &d" + (n + 1) + "번째 환생&f을 했습니다!"));
    }

    /** 동굴 탈출: 3초 동안 가만히 있으면 바로 위 지상으로 이동 (쿨타임) */
    private void escape(Player p) {
        PlayerData d = plugin.data().get(p);
        if (d.onCooldown("escape")) { Text.msg(p, "&c탈출 쿨타임 " + (d.remaining("escape") / 1000 + 1) + "초"); return; }
        var top = p.getWorld().getHighestBlockAt(p.getLocation());
        if (p.getWorld().getEnvironment() != org.bukkit.World.Environment.NORMAL || top.getY() - p.getLocation().getY() < 4) {
            Text.msg(p, "&c지하(동굴)에 있을 때만 사용할 수 있습니다.");
            return;
        }
        if (System.currentTimeMillis() - d.lastCombat < 5000) { Text.msg(p, "&c전투 중에는 사용할 수 없습니다."); return; }
        org.bukkit.Location start = p.getLocation();
        Text.msg(p, "&e3초 동안 움직이지 마세요... 지상으로 탈출합니다.");
        new org.bukkit.scheduler.BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!p.isOnline() || p.getLocation().distanceSquared(start) > 0.5 || System.currentTimeMillis() - d.lastCombat < 1000) {
                    Text.actionBar(p, "&c탈출이 취소되었습니다.");
                    cancel();
                    return;
                }
                p.getWorld().spawnParticle(org.bukkit.Particle.PORTAL, p.getLocation().add(0, 1, 0), 15, 0.3, 0.6, 0.3, 0.3);
                if (++t >= 6) {
                    cancel();
                    var top2 = p.getWorld().getHighestBlockAt(p.getLocation());
                    p.teleport(top2.getLocation().add(0.5, 1, 0.5).setDirection(p.getLocation().getDirection()));
                    d.cooldown("escape", plugin.getConfig().getLong("escape.cooldown-seconds", 120) * 1000);
                    p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.2f);
                    Text.msg(p, "&a지상으로 탈출했습니다.");
                }
            }
        }.runTaskTimer(plugin, 10L, 10L);
    }

    private void info(Player viewer, Player t) {
        if (t == null) {
            Text.msg(viewer, "&c접속 중인 플레이어가 아닙니다.");
            return;
        }
        PlayerData d = plugin.data().get(t);
        StatSnapshot s = d.stats;
        Guild g = plugin.guilds().of(t.getUniqueId());
        viewer.sendMessage(Text.c("&6&m          &r &e" + Text.name(t) + " &7Lv." + d.level + " &6&m          "));
        viewer.sendMessage(Text.c("&f길드: &b" + (g == null ? "없음" : g.name) + " &7| &f직업: " + (d.blacksmith ? "&6대장장이" : "&7없음")));
        viewer.sendMessage(Text.c("&f체력: &c" + Text.num(d.hp) + "/" + Text.num(s.maxHp) + " &7| &f공격력: &6" + Text.num(s.attack)
                + " &7| &f원거리: &e" + Text.num(s.ranged)));
        viewer.sendMessage(Text.c("&f크리티컬: &e" + String.format("%.1f", s.crit) + "% &7(+" + String.format("%.0f", 100 + s.critDmg) + "% 추가)"
                + " &7| &f방어력: &b" + String.format("%.1f", s.def) + "% &7| &f회피: &b" + String.format("%.1f", s.dodge) + "%"));
        viewer.sendMessage(Text.c("&f체력흡수: &a" + String.format("%.1f", s.lifesteal) + "% &7| &f방어무시: &a" + String.format("%.1f", s.armorPen)
                + "% &7| &f이동속도: &a" + String.format("%+.1f", s.speed) + "%"));
        viewer.sendMessage(Text.c("&f힘 &c" + (int) s.str + " &7(투자 " + d.str + ") &f민첩 &a" + (int) s.dex + " &7(투자 " + d.dex + ") &f모험 &b" + (int) s.adv + " &7(투자 " + d.adv + ")"));
        viewer.sendMessage(Text.c("&f특수 스킬: &d" + d.passives.size() + "개 &7| &f무기군: &f" + (s.weaponClass == null ? "없음" : s.weaponClass.label)));
    }

    /** 스탯 분배 GUI */
    private class StatGui extends Gui {
        StatGui(Player p) {
            super(3, "&6스탯 분배", "stat");
            render(p);
        }

        void render(Player p) {
            PlayerData d = plugin.data().get(p);
            StatSnapshot s = d.stats;
            var c = plugin.getConfig();
            set(4, button(Material.NETHER_STAR, "&e남은 스탯 포인트: &a" + d.statPoints, "&7레벨업마다 +" + c.getInt("player.stat-per-level", 5)));
            java.util.List<String> strLore = new java.util.ArrayList<>(java.util.List.of("&72포인트당 공격력 +" + c.getDouble("player.str-atk-per-2", 5),
                    "&71포인트당 마력 +" + c.getDouble("player.str-magic-per-point", 2) + " &8(스킬 위력)", ""));
            strLore.addAll(kr.rpgcraft.stat.StatCalculator.strMilestones(d));
            strLore.add("");
            strLore.add("&a좌클릭 +1 &7| &a우클릭 +10 &7| &a쉬프트 전부");
            set(11, button(Material.IRON_SWORD, "&c힘 &f" + d.str + finalOf(d.str, s.str), strLore.toArray(new String[0])), e -> add(p, e, 0));
            java.util.List<String> dexLore = new java.util.ArrayList<>(java.util.List.of("&71포인트당 치명타 확률 +" + c.getDouble("player.dex-crit-per-point", 0.12) + "%, 치명타 피해 +" + c.getDouble("player.dex-critdmg-per-point", 0.6) + "%", ""));
            dexLore.addAll(kr.rpgcraft.stat.StatCalculator.dexMilestones(d));
            dexLore.add("");
            dexLore.add("&a좌클릭 +1 &7| &a우클릭 +10 &7| &a쉬프트 전부");
            set(13, button(Material.FEATHER, "&a민첩 &f" + d.dex + finalOf(d.dex, s.dex), dexLore.toArray(new String[0])), e -> add(p, e, 1));
            java.util.List<String> advLore = new java.util.ArrayList<>(java.util.List.of("&71포인트당 체력 +" + String.format("%.2f", c.getDouble("player.adv-hp-per-point", 90) * plugin.stats().hpScale()) + ", 방어력 +" + c.getDouble("player.adv-def-per-point", 0.065) + "%", "&7유적 · 보물 상자는 탐험도로도 도전 가능 &8(/숙련)", ""));
            advLore.addAll(kr.rpgcraft.stat.StatCalculator.advMilestones(d));
            advLore.add("");
            advLore.add("&a좌클릭 +1 &7| &a우클릭 +10 &7| &a쉬프트 전부");
            set(15, button(Material.LEATHER_BOOTS, "&b모험 &f" + d.adv + finalOf(d.adv, s.adv), advLore.toArray(new String[0])), e -> add(p, e, 2));
            java.util.List<String> info = new java.util.ArrayList<>(java.util.List.of(
                    "&f공격력 &6" + Text.num(s.attack), "&f체력 &c" + Text.num(s.maxHp),
                    "&f마력 &d" + Text.num(s.magic), "&f크리티컬 &e" + String.format("%.1f", s.crit) + "% &7(피해 +" + String.format("%.0f", 100 + s.critDmg) + "%)",
                    "&f방어력 &b" + String.format("%.1f", s.def) + "%", "&f이동속도 &a" + String.format("%+.1f", s.speed) + "%",
                    "&f흡혈 &c" + String.format("%.1f", s.lifesteal) + "% &7(주는 피해만큼 체력 회복)",
                    "&f회피 &b" + String.format("%.1f", s.dodge) + "% &f방어 관통 &b" + String.format("%.1f", s.armorPen) + "%",
                    "&f직업 &e" + plugin.jobs().title(d)));
            info.addAll(passiveLines(p, d));   // v5.10.45 히든 패시브 보너스도 한눈에
            set(22, button(Material.BOOK, "&f현재 능력치 &7(모든 보너스 포함)", info.toArray(new String[0])));
            fill(0, 26);
        }

        void add(Player p, InventoryClickEvent e, int which) {
            if (allocateStat(p, which, e.isShiftClick(), e.isRightClick())) render(p);
        }

        private String finalOf(int invested, double total) {
            return (int) Math.round(total) != invested ? " &7→ 최종 &e" + (int) Math.round(total) : "";
        }
    }

    /** v5.10.45 히든 · 던전 · 최초 보상 패시브가 주는 능력치 (이미 최종 능력치에 포함) — 스탯 창 · 인벤토리 스탯 칸 공용 */
    public java.util.List<String> passiveLines(Player p, PlayerData d) {
        java.util.List<String> out = new java.util.ArrayList<>();
        kr.rpgcraft.stat.StatMap pb = plugin.stats().passiveBonus(p, d);
        java.util.List<String> parts = new java.util.ArrayList<>();
        for (var en : pb.entries()) {
            double v = en.getValue();
            if (Math.abs(v) < 1e-9 || en.getKey().isRequirement()) continue;
            String num = en.getKey().pct || en.getKey() == kr.rpgcraft.stat.Stat.SPEED ? (v == Math.rint(v) ? String.valueOf((long) v) : String.format("%.1f", v)) + (en.getKey().pct ? "%" : "")
                    : Text.num(v);
            parts.add("&f" + en.getKey().label + " &a" + (v > 0 ? "+" : "") + num);
        }
        if (parts.isEmpty()) return out;
        out.add("");
        out.add("&d히든 패시브 보너스 &8(위 능력치에 이미 포함)");
        StringBuilder line = new StringBuilder(" ");
        int n = 0;
        for (String part : parts) {
            if (n == 3) { out.add(line.toString()); line = new StringBuilder(" "); n = 0; }
            if (n > 0) line.append(" &8· ");
            line.append(part);
            n++;
        }
        if (n > 0) out.add(line.toString());
        return out;
    }

    /** 스탯 분배 (스탯 창 · v5.10.34 인벤토리 위 스탯 칸 공용). 좌클릭 +1 · 우클릭 +10 · 쉬프트 전부. 분배했으면 true */
    public boolean allocateStat(Player p, int which, boolean all, boolean ten) {
        {
            PlayerData d = plugin.data().get(p);
            int n = all ? d.statPoints : ten ? 10 : 1;
            n = Math.min(n, d.statPoints);
            if (n <= 0) {
                Text.msg(p, "&c스탯 포인트가 없습니다.");
                return false;
            }
            d.statPoints -= n;
            int beforeStr = d.str, beforeDex = d.dex, beforeAdv = d.adv;
            if (which == 0) d.str += n;
            else if (which == 1) d.dex += n;
            else d.adv += n;
            plugin.stats().refresh(p);
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.3f);
            if (which == 0 && d.str / 50 > beforeStr / 50) {   // 힘 50 구간 달성 알림
                int step = d.str / 50;
                String extra = beforeStr < 1000 && d.str >= 1000 ? " &6+ 파괴자!" : beforeStr < 500 && d.str >= 500 ? " &6+ 괴력!" : beforeStr < 250 && d.str >= 250 ? " &6+ 분쇄!" : "";
                p.sendTitle("", Text.c("&c힘 " + step * 50 + " 달성 &7(힘 +" + 4 * step + "% · 관통 +" + 2 * step + ")" + extra), 5, 40, 10);
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);
            }
            if (which == 1 && d.dex / 50 > beforeDex / 50) {   // 민첩 50 구간
                int step = d.dex / 50;
                String extra = beforeDex < 1000 && d.dex >= 1000 ? " &6+ 그림자 칼날!" : beforeDex < 500 && d.dex >= 500 ? " &6+ 명사수!" : beforeDex < 250 && d.dex >= 250 ? " &6+ 질풍!" : "";
                p.sendTitle("", Text.c("&a민첩 " + step * 50 + " 달성 &7(치명타 피해 +" + 6 * step + "%)" + extra), 5, 40, 10);
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.6f);
            }
            if (which == 2 && d.adv / 50 > beforeAdv / 50) {   // 모험 50 구간
                int step = d.adv / 50;
                String extra = beforeAdv < 1000 && d.adv >= 1000 ? " &6+ 수호신의 가호!" : beforeAdv < 500 && d.adv >= 500 ? " &6+ 불굴!" : beforeAdv < 250 && d.adv >= 250 ? " &6+ 강인함!" : "";
                p.sendTitle("", Text.c("&b모험 " + step * 50 + " 달성 &7(체력 +" + 3 * step + "% · 방어력 +" + step + ")" + extra), 5, 40, 10);
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
            }
            if (plugin.invStats() != null) plugin.invStats().refreshSoon(p);
            return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        List<String> out = new ArrayList<>();
        switch (c.getName()) {
            case "shop" -> { if (a.length == 1) for (ShopManager.Shop sh : plugin.shops().all()) if (sh.command) out.add(sh.id); }
            case "rune" -> { if (a.length == 1) out.add("변경"); }
            case "job" -> {
                if (a.length == 1) out.addAll(List.of("선택", "전직", "대장장이"));
                if (a.length == 2 && a[0].equals("선택")) out.addAll(List.of("전사", "궁수", "도적", "수호자", "마법사"));
            }
            case "trade" -> {
                if (a.length == 1) { out.addAll(List.of("수락", "거절", "돈")); out.addAll(Text.onlineNames(a[0], s)); }
            }
            case "pay", "info", "absorb" -> { if (a.length == 1) out.addAll(Text.onlineNames(a[0], null)); }
            case "tpa" -> {   // /tpa <Tab> → 접속 중인 다른 플레이어 (닉네임, 입력한 글자로 시작하는 이름만)
                if (a.length == 1) out.addAll(Text.onlineNames(a[0], s));
            }
            default -> { }
        }
        return out;
    }
}
