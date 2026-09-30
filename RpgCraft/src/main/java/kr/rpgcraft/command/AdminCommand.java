package kr.rpgcraft.command;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.data.PlayerData;
import kr.rpgcraft.feature.RuinManager;
import kr.rpgcraft.feature.StructureManager;
import kr.rpgcraft.feature.RuneManager;
import kr.rpgcraft.item.Category;
import kr.rpgcraft.item.ItemData;
import kr.rpgcraft.item.ItemTemplate;
import kr.rpgcraft.passive.Passive;
import kr.rpgcraft.util.Locs;
import kr.rpgcraft.util.Text;
import kr.rpgcraft.war.Castle;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /rpg관리 - 운영자 명령어 */
public class AdminCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBS = List.of("give", "items", "money", "level", "exp", "stat", "passive", "heal", "starter",
            "boss", "npc", "castle", "war", "ruin", "round", "reload", "rune", "warp", "pack", "build", "mob", "structure", "reset", "wave", "merchant", "dungeon", "questnpc", "plants", "title", "rex", "bounty", "hiddennpc", "worldboss", "fieldboss", "npcs", "npcbring", "inv", "enderchest", "time", "tickets", "auction", "enhance", "stock", "coin");
    private final RpgCraft plugin;

    public AdminCommand(RpgCraft plugin) {
        this.plugin = plugin;
    }

    private void help(CommandSender s) {
        Text.msg(s, "&e/rpg관리 give <플레이어> <아이템ID> [수량]");
        Text.msg(s, "&e/rpg관리 items [검색어] &7- 아이템 ID 목록");
        Text.msg(s, "&e/rpg관리 money <플레이어> <set|add|take> <금액>");
        Text.msg(s, "&e/rpg관리 level <플레이어> <레벨> &7| &eexp <플레이어> <양>");
        Text.msg(s, "&e/rpg관리 stat <플레이어> <포인트> &7- 스탯 포인트 지급");
        Text.msg(s, "&e/rpg관리 passive <플레이어> <add|remove|list> [패시브]");
        Text.msg(s, "&e/rpg관리 heal [플레이어]");
        Text.msg(s, "&e/rpg관리 starter [플레이어] &7- 기본 지급품 다시 주기");
        Text.msg(s, "&e/rpg관리 enhance <수치> [플레이어] &7- 손에 든 장비의 강화 수치 설정");
        Text.msg(s, "&e/rpg관리 stock <종목> <가격> &7- 주식 가격 직접 지정");
        Text.msg(s, "&e/rpg관리 coin <플레이어> <수> &7- 미니게임 코인 지급 (음수면 회수)");
        Text.msg(s, "&e/rpg관리 castle build <1|2|3> <id> &7- 내 자리에 대형 공성 성 (1 왕성 · 2 흑요 요새 · 3 백악 성채, 성벽 · 신호기 자동 등록)");
        Text.msg(s, "&e/rpg관리 ruin build [테마|random] [here|random] &7- 점프맵 유적 짓기 (테마: /rpg관리 ruin themes)");
        Text.msg(s, "&e/rpg관리 auction [list|remove|return|player|clear] &7- 옥션 물건 관리 (그냥 입력하면 관리 창)");
        Text.msg(s, "&e/rpg관리 boss <spawn <id>|list|killall>");
        Text.msg(s, "&e/rpg관리 npc <상점ID> &7- 현재 위치에 상점 NPC (제거: 쉬프트+방벽 우클릭)");
        Text.msg(s, "&e/rpg관리 castle demolish <id> &7- 성을 허물고 짓기 전 땅으로 되돌림 (길드가 사라지면 그 길드의 성도 자동으로)");
        Text.msg(s, "&e/rpg관리 castle <create|pos1|pos2|wall|beacon|spawn|owner|delete|list|restore> ...");
        Text.msg(s, "&e/rpg관리 war stop <성ID>");
        Text.msg(s, "&e/rpg관리 ruin <create|start|end|adv|limit|passive|first|delete|list> ...");
        Text.msg(s, "&e/rpg관리 warp set <id> <이름> [아이콘] [최소레벨] &7| &ewarp delete <id> &7| &ewarp list");
        Text.msg(s, "&e/rpg관리 pack <status|test|reload|send [플레이어|all]> &7- 리소스팩 (test: 접속 진단)");
        Text.msg(s, "&e/rpg관리 mob <list|spawn <id> [레벨]|killall> &7- 커스텀 몬스터");
        Text.msg(s, "&e/rpg관리 structure <종류> [ID] &7- 구조물 자동 건설 (castle, ruin, temple, tower ...)");
        Text.msg(s, "&e/rpg관리 wave [플레이어] &7| &emerchant &7- 필드 웨이브 깃발 / 히든 상인 즉시 등장");
        Text.msg(s, "&e/rpg관리 hiddennpc [respawn] &7- 히든 NPC 위치 / 사라진 히든 NPC 다시 배치");
        Text.msg(s, "&e/rpg관리 reset <all confirm|player <이름> confirm|auction confirm> &7- 게임 초기화 / 옥션만 초기화");
        Text.msg(s, "&e/rpg관리 dungeon <create <ID> <단계>|generate [개수]|list|delete <ID|all>> &7- 대형 던전");
        Text.msg(s, "&e/rpg관리 questnpc <scatter <수>|here <유형>> &7- 의뢰 NPC 배치");
        Text.msg(s, "&e/rpg관리 worldboss [random|desert_nightmare|siphonia|kain] [here] &7- 월드보스 + 전장");
        Text.msg(s, "&e/rpg관리 fieldboss &7- 내 근처에 지역 레벨·바이옴에 맞는 필드 보스 등장");
        Text.msg(s, "&e/rpg관리 worldboss remove &7- 지금 있는 월드보스 모두 제거 (전장 복구)");
        Text.msg(s, "&e/rpg관리 npcs &7| &enpcbring &7- NPC 위치 목록 / 가까운 NPC 를 내 자리로");
        Text.msg(s, "&e/rpg관리 inv <플레이어> &7| &eenderchest <플레이어> &7- 인벤토리 · 엔더 상자 열기");
        Text.msg(s, "&e/rpg관리 time <day|night|bloodmoon> &7| &etickets [close <번호>] &7| &e/공지 <내용> &7| &e/추첨 <아이템 이름> [개수]");
        Text.msg(s, "&e/rpg관리 rex &7| &ebounty &7- 렉스(전설의 대장장이) / 현상수배범 즉시 등장");
        Text.msg(s, "&e/rpg관리 title <create|delete|give|take|list> &7- 관리자 칭호");
        Text.msg(s, "&e/rpg관리 plants [반경] &7- 주변 해바라기·풀 정리");
        Text.msg(s, "&e/rpg관리 build &7- 건축 모드 켜기/끄기 (야생 방지 무시, 크리에이티브 + rpgcraft.build 권한)");
        Text.msg(s, "&e/rpg관리 round next &7| &ereload &7| &erune &7(손에 든 룬 무료 재설정)");
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (cmd.getName().equals("notice")) {   // /공지 <내용>
            if (a.length == 0) { Text.msg(s, "&e/공지 <내용>"); return true; }
            String msg = Text.c(String.join(" ", a));
            for (Player op : Bukkit.getOnlinePlayers()) {
                op.sendMessage("");
                op.sendMessage(Text.c("&6&l[공지] &f") + msg);
                op.sendMessage("");
                op.sendTitle(Text.c("&6&l공지"), msg, 10, 70, 20);
                op.playSound(op.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1f);
            }
            return true;
        }
        if (cmd.getName().equals("lottery")) {   // /추첨 <아이템 한국어 이름> [개수]
            if (a.length == 0) { Text.msg(s, "&e/추첨 <아이템 이름> [개수]"); return true; }
            int amt = 1;
            String nm = String.join(" ", a);
            if (a.length > 1 && a[a.length - 1].matches("\\d+")) { amt = Integer.parseInt(a[a.length - 1]); nm = String.join(" ", java.util.Arrays.copyOf(a, a.length - 1)); }
            ItemTemplate found = null;
            for (ItemTemplate t : plugin.items().all()) if (Text.strip(Text.c(t.name)).equals(nm)) { found = t; break; }
            if (found == null) for (ItemTemplate t : plugin.items().all()) if (Text.strip(Text.c(t.name)).contains(nm)) { found = t; break; }
            if (found == null) { Text.msg(s, "&c그런 이름의 아이템이 없습니다."); return true; }
            java.util.List<Player> ps = new java.util.ArrayList<>(Bukkit.getOnlinePlayers());
            if (ps.isEmpty()) return true;
            Player win = ps.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(ps.size()));
            for (org.bukkit.inventory.ItemStack l : win.getInventory().addItem(plugin.items().create(found.id, amt)).values()) win.getWorld().dropItemNaturally(win.getLocation(), l);
            Text.announce(Text.PREFIX + Text.c("&d&l추첨! &f" + found.name + " x" + amt + " &7→ &e&l" + Text.name(win)));
            for (Player op : Bukkit.getOnlinePlayers()) op.playSound(op.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
            return true;
        }
        if (!s.hasPermission("rpgcraft.admin")) {
            Text.msg(s, "&c권한이 없습니다.");
            return true;
        }
        if (a.length == 0) {
            help(s);
            return true;
        }
        try {
            switch (a[0].toLowerCase(Locale.ROOT)) {
                case "give" -> give(s, a);
                case "items" -> {
                    String q = a.length > 1 ? a[1] : "";
                    StringBuilder sb = new StringBuilder();
                    int n = 0;
                    for (ItemTemplate t : plugin.items().all()) {
                        if (!q.isEmpty() && !t.id.contains(q) && !t.name.contains(q)) continue;
                        sb.append(t.grade.color).append(t.name.replace(' ', '_')).append(" &8(").append(t.id).append(")&7, ");
                        n++;
                    }
                    Text.msg(s, "&e아이템 " + n + "개 &7- /rpg관리 give <플레이어> <이름 또는 ID> [수량]\n" + sb);
                }
                case "money" -> {
                    PlayerData d = target(s, a, 1);
                    if (d == null || a.length < 4) return true;
                    long v = Text.parseLong(a[3], 0);
                    switch (a[2]) {
                        case "set" -> d.money = Math.max(0, v);
                        case "add" -> d.money += v;
                        case "take" -> d.money = Math.max(0, d.money - v);
                        default -> { Text.msg(s, "&cset|add|take"); return true; }
                    }
                    Text.msg(s, d.name + " 소지금: " + Text.money(d.money));
                }
                case "level" -> {
                    PlayerData d = target(s, a, 1);
                    if (d == null || a.length < 3) return true;
                    int before = d.level;
                    plugin.levels().setLevel(d, Text.parseInt(a[2], 1));
                    d.statPoints += Math.max(0, d.level - before) * plugin.getConfig().getInt("player.stat-per-level", 5);
                    Text.msg(s, d.name + " 레벨: " + d.level);
                }
                case "exp" -> {
                    Player p = a.length > 2 ? Bukkit.getPlayerExact(a[1]) : null;
                    if (p == null) { Text.msg(s, "&c접속 중인 플레이어가 아닙니다."); return true; }
                    plugin.levels().addExp(p, Text.parseDouble(a[2], 0));
                    Text.msg(s, "경험치 지급 완료");
                }
                case "stat" -> {
                    PlayerData d = target(s, a, 1);
                    if (d == null || a.length < 3) return true;
                    d.statPoints += Text.parseInt(a[2], 0);
                    Text.msg(s, d.name + " 스탯 포인트: " + d.statPoints);
                }
                case "passive" -> passive(s, a);
                case "heal" -> {
                    Player p = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : s instanceof Player pp ? pp : null;
                    if (p == null) return true;
                    plugin.health().set(p, plugin.health().max(p));
                    Text.msg(s, "회복 완료");
                }
                case "auction" -> auction(s, a);
                case "coin", "코인" -> {   // /rpg관리 coin <플레이어> <종류> <수> (v5.6.0)
                    if (a.length < 3) { Text.msg(s, "&e/rpg관리 coin <플레이어> <수> &7- 미니게임 코인"); return true; }
                    Player t = Bukkit.getPlayerExact(a[1]);
                    if (t == null) { Text.msg(s, "&c접속 중인 플레이어가 아닙니다."); return true; }
                    long n;
                    try { n = Long.parseLong(a[a.length - 1]); } catch (NumberFormatException ex) { Text.msg(s, "&c수는 숫자로 적어 주세요."); return true; }   // 예전 형식(<종류> <수>)도 됨
                    plugin.minigames().giveCoins(t, "event", n);
                    Text.msg(s, "&a" + t.getName() + " 에게 미니게임 코인 " + n + "개");
                    return true;
                }
                case "stock", "주식" -> {   // /rpg관리 stock <종목> <가격> (v5.6.0)
                    if (a.length < 3) { Text.msg(s, "&e/rpg관리 stock <종목> <가격> &7종목: " + String.join(", ", kr.rpgcraft.economy.StockManager.STOCKS.stream().map(kr.rpgcraft.economy.StockManager.Stock::id).toList())); return true; }
                    double v;
                    try { v = Double.parseDouble(a[2]); } catch (NumberFormatException ex) { Text.msg(s, "&c가격은 숫자로 적어 주세요."); return true; }
                    Text.msg(s, plugin.stocks().setPrice(a[1], v) ? "&a" + a[1] + " 가격을 " + Text.money(Math.round(plugin.stocks().price(a[1]))) + "(으)로 정했습니다." : "&c없는 종목입니다.");
                    return true;
                }
                case "enhance", "강화" -> {   // /rpg관리 enhance <수치> [플레이어] : 손에 든 장비의 강화 수치를 바로 정함 (v5.4.39)
                    if (a.length < 2) { Text.msg(s, "&e/rpg관리 enhance <수치> [플레이어] &7- 손에 든 장비의 강화 수치 설정 (0 ~ 최대 강화)"); return true; }
                    Player t = a.length > 2 ? Text.player(a[2]) : s instanceof Player pp ? pp : null;
                    if (t == null) { Text.msg(s, "&c접속 중인 플레이어를 입력하세요."); return true; }
                    ItemStack it = t.getInventory().getItemInMainHand();
                    if (it == null || it.getType().isAir() || !ItemData.enhanceable(it)) { Text.msg(s, "&c" + Text.name(t) + "님이 강화할 수 있는 장비를 손에 들고 있지 않습니다."); return true; }
                    int want = Text.parseInt(a[1], -1), max = ItemData.maxEnhance(it);
                    if (want < 0) { Text.msg(s, "&c강화 수치는 0 이상의 숫자로 입력하세요."); return true; }
                    int lv = Math.min(want, max);
                    ItemData.setInt(it, kr.rpgcraft.Keys.ENH, lv);
                    ItemData.refresh(it);
                    t.getInventory().setItemInMainHand(it);
                    plugin.stats().refresh(t);
                    Text.msg(s, "&a" + Text.name(t) + "님의 " + it.getItemMeta().getDisplayName() + " &a강화를 &e+" + lv + "&a로 설정했습니다." + (want > max ? " &7(최대 +" + max + ")" : ""));
                }
                case "starter" -> {
                    Player p = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : s instanceof Player pp ? pp : null;
                    if (p == null) { Text.msg(s, "&c접속 중인 플레이어를 입력하세요."); return true; }
                    int n = kr.rpgcraft.data.ResetPending.giveStarter(plugin, p);
                    Text.msg(s, "&a" + p.getName() + " 에게 기본 지급품 " + n + "종을 주었습니다.");
                }
                case "boss" -> boss(s, a);
                case "npc" -> {
                    if (!(s instanceof Player p) || a.length < 2) { Text.msg(s, "&c/rpg관리 npc <상점ID>"); return true; }
                    if (plugin.shops().spawnNpc(p.getLocation(), a[1]) == null) Text.msg(s, "&c없는 상점입니다.");
                    else Text.msg(s, "&aNPC 생성 완료");
                }
                case "castle" -> castle(s, a);
                case "war" -> {
                    if (a.length >= 3 && a[1].equals("stop")) Text.msg(s, plugin.wars().stop(a[2]) ? "중단했습니다." : "&c진행 중인 전쟁이 없습니다.");
                }
                case "ruin" -> ruin(s, a);
                case "structure" -> {
                    if (!(s instanceof Player p)) return true;
                    if (a.length < 2) { Text.msg(p, "&e/rpg관리 structure <종류> [ID] &7- " + StructureManager.list()); return true; }
                    String r = plugin.structures().build(a[1].toLowerCase(Locale.ROOT), p.getLocation().getBlock().getLocation(), a.length > 2 ? a[2] : null, p);
                    Text.msg(p, r == null ? "&c없는 구조물 종류입니다. " + StructureManager.list() : "&a건설 완료: " + r);
                }
                case "wave" -> {
                    Player t = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : s instanceof Player pp ? pp : null;
                    if (t == null) return true;
                    Text.msg(s, plugin.events().spawnFlag(t) ? "&a" + t.getName() + " 근처에 웨이브 깃발 생성" : "&c깃발을 놓을 자리를 찾지 못했습니다.");
                }
                case "merchant" -> Text.msg(s, plugin.events().spawnHiddenMerchant() ? "&a히든 상인 등장" : "&c히든 상인을 소환하지 못했습니다.");
                case "fieldboss" -> {   // 필드 보스 즉시 등장 (내 근처 또는 무작위 접속자 근처)
                    String id = plugin.fieldBosses().spawnRandom(s instanceof Player fp ? fp : null);
                    Text.msg(s, id != null ? "&a필드 보스 등장: &f" + plugin.bosses().def(id).name : "&c필드 보스를 부를 자리를 찾지 못했습니다.");
                }
                case "worldboss" -> {
                    if (a.length >= 2 && (a[1].equals("remove") || a[1].equals("clear") || a[1].equals("제거"))) {
                        int n = plugin.worldBoss().clearAll();
                        Text.msg(s, n > 0 ? "&a월드보스 " + n + "마리를 없앴습니다. &7(전장은 약 1분 안에 원래 지형으로)" : "&7지금 있는 월드보스가 없습니다.");
                        return true;
                    }
                    Location here = s instanceof Player ap && a.length >= 3 && a[2].equals("here") ? ap.getLocation().getBlock().getLocation() : null;
                    boolean ok = plugin.worldBoss().start(a.length >= 2 && !a[1].equals("random") ? a[1] : null, here);
                    Text.msg(s, ok ? "&a월드보스를 불렀습니다." : "&c이미 월드보스가 있거나 부를 수 없습니다.");
                }
                case "time" -> {
                    if (a.length < 2) { Text.msg(s, "&e/rpg관리 time <day|night|bloodmoon>"); return true; }
                    var ph = switch (a[1]) { case "night", "밤" -> kr.rpgcraft.world.CycleManager.Phase.NIGHT; case "bloodmoon", "핏빛달" -> kr.rpgcraft.world.CycleManager.Phase.BLOOD_MOON; default -> kr.rpgcraft.world.CycleManager.Phase.DAY; };
                    plugin.cycle().force(ph);
                    Text.msg(s, "&a시간대를 바꿨습니다.");
                }
                case "tickets" -> {
                    java.io.File f = new java.io.File(plugin.getDataFolder(), "tickets.yml");
                    var y = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f);
                    if (a.length >= 3 && a[1].equals("close")) {
                        y.set("open." + a[2], null);
                        try { y.save(f); } catch (java.io.IOException ignored) { }
                        Text.msg(s, "&a티켓 #" + a[2] + " 을(를) 닫았습니다.");
                        return true;
                    }
                    var sec = y.getConfigurationSection("open");
                    if (sec == null || sec.getKeys(false).isEmpty()) { Text.msg(s, "&7열린 티켓이 없습니다."); return true; }
                    for (String id : sec.getKeys(false))
                        Text.msg(s, "&d#" + id + " &7" + sec.getString(id + ".time") + " &f" + sec.getString(id + ".player") + "&7: &f" + sec.getString(id + ".message"));
                    Text.msg(s, "&7닫기: /rpg관리 tickets close <번호>");
                }
                case "npcs" -> {   // 모든 NPC 위치
                    int n = 0;
                    for (World w : Bukkit.getWorlds())
                        for (org.bukkit.entity.Villager v : w.getEntitiesByClass(org.bukkit.entity.Villager.class)) {
                            if (v.hasAI() || v.getCustomName() == null) continue;
                            var l = v.getLocation();
                            var comp = new net.md_5.bungee.api.chat.TextComponent(net.md_5.bungee.api.chat.TextComponent.fromLegacyText(
                                    Text.c("&f" + Text.strip(v.getCustomName()) + " &7" + w.getName() + " " + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ() + " &b[이동]")));
                            comp.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND,
                                    "/tp " + s.getName() + " " + l.getBlockX() + " " + (l.getBlockY() + 1) + " " + l.getBlockZ()));
                            if (s instanceof Player ap) ap.spigot().sendMessage(comp); else s.sendMessage(Text.strip(v.getCustomName()) + " " + l);
                            n++;
                        }
                    Text.msg(s, "&7(불러온 청크에 있는 NPC " + n + "명) · 가까운 NPC 를 내 자리로: /rpg관리 npcbring");
                }
                case "npcbring" -> {   // 반경 64칸 안에서 가장 가까운 NPC 를 내 자리로
                    if (!(s instanceof Player ap)) return true;
                    org.bukkit.entity.Villager best = null;
                    for (Entity en : ap.getNearbyEntities(64, 64, 64))
                        if (en instanceof org.bukkit.entity.Villager v && !v.hasAI() && v.getCustomName() != null
                                && (best == null || v.getLocation().distanceSquared(ap.getLocation()) < best.getLocation().distanceSquared(ap.getLocation()))) best = v;
                    if (best == null) { Text.msg(s, "&c64칸 안에 NPC 가 없습니다."); return true; }
                    best.teleport(ap.getLocation());
                    Text.msg(s, "&a" + Text.strip(best.getCustomName()) + " 을(를) 옮겼습니다.");
                }
                case "inv", "enderchest" -> {   // 플레이어 인벤토리 / 엔더 상자 열기
                    if (!(s instanceof Player ap) || a.length < 2) { Text.msg(s, "&e/rpg관리 inv <플레이어> | enderchest <플레이어>"); return true; }
                    Player t = Bukkit.getPlayerExact(a[1]);
                    if (t == null) { Text.msg(s, "&c접속 중인 플레이어가 아닙니다."); return true; }
                    ap.openInventory(a[0].equals("inv") ? t.getInventory() : t.getEnderChest());
                }
                case "necro" -> {   // v5.10.9 네크로맨서 시험용: /rpg관리 necro <플레이어> <정수> [몬스터id 영혼수]
                    if (a.length < 3) { Text.msg(s, "&e/rpg관리 necro <플레이어> <사령 정수> [커스텀몬스터id 영혼수]"); return true; }
                    Player t = Bukkit.getPlayerExact(a[1]);
                    if (t == null) { Text.msg(s, "&c접속 중인 플레이어가 아닙니다."); return true; }
                    long ess;
                    int souls = 0;
                    try { ess = Long.parseLong(a[2]); if (a.length > 4) souls = Integer.parseInt(a[4]); } catch (NumberFormatException ex) { Text.msg(s, "&c숫자를 넣어 주세요."); return true; }
                    plugin.necro().grant(t, ess, a.length > 3 ? a[3] : null, souls);
                    Text.msg(s, "&a" + t.getName() + " 에게 사령 정수 " + ess + (souls > 0 ? " · " + a[3] + " 영혼 " + souls : "") + " 지급");
                }
                case "hiddennpc" -> {
                    if (a.length > 1 && (a[1].equalsIgnoreCase("respawn") || a[1].equals("재배치"))) {   // 사라진 히든 NPC 다시 세우기
                        plugin.hiddenQuests().respawnMissing(s);
                        plugin.hiddenJobs().respawnMissing(s);
                        Text.msg(s, "&a히든 NPC 확인 중... 없어진 NPC 는 원래 자리에 다시 세웁니다. &7(몇 초 걸림)");
                        return true;
                    }
                    for (String hl : plugin.hiddenJobs().locations()) Text.msg(s, "&5[숨은 직업] &f" + hl);
                    var y = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.File(plugin.getDataFolder(), "hidden_quests.yml"));
                    var sec = y.getConfigurationSection("placed");
                    if (sec == null) { Text.msg(s, "&7아직 배치된 히든 NPC 가 없습니다."); return true; }
                    for (String id : sec.getKeys(false)) {
                        String[] xz = sec.getString(id, "0,0").split(",");
                        int owners = y.getStringList("owners." + id).size();
                        Text.msg(s, "&d" + id + " &f" + xz[0] + ", " + xz[1] + " &7(보상 받은 사람 " + owners + "/3)");
                        if (s instanceof Player ap) {
                            var comp = new net.md_5.bungee.api.chat.TextComponent(net.md_5.bungee.api.chat.TextComponent.fromLegacyText(Text.c("  &b[이동]")));
                            var w0 = Bukkit.getWorlds().get(0);
                            int hx = Integer.parseInt(xz[0].trim()), hz = Integer.parseInt(xz[1].trim());
                            comp.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND,
                                    "/tp " + ap.getName() + " " + hx + " " + (w0.getHighestBlockYAt(hx, hz) + 2) + " " + hz));
                            ap.spigot().sendMessage(comp);
                        }
                    }
                }
                case "rex", "pender" -> Text.msg(s, plugin.pender().appear() ? "&a렉스 등장" : "&c렉스를 부르지 못했습니다.");
                case "bounty" -> { plugin.content().spawnBounty(); Text.msg(s, "&a현상수배범 출현 시도"); }
                case "reset" -> reset(s, a);
                case "dungeon" -> {
                    var dm = plugin.dungeons();
                    String sub = a.length > 1 ? a[1] : "list";
                    if (sub.equals("create") && a.length >= 4 && s instanceof Player p) {
                        var d = dm.create(a[2], Text.parseInt(a[3], 1), p.getLocation().getBlock().getLocation());
                        Text.msg(p, "&a던전 생성: &f" + d.name + " &7(" + d.tier + "단계, 입구 = 우는 흑요석 우클릭)");
                    } else if (sub.equals("delete") && a.length >= 3) {
                        if (a[2].equals("all")) {
                            int n = 0;
                            for (var d : new ArrayList<>(dm.all())) if (dm.delete(d.id)) n++;
                            Text.msg(s, "&a던전 " + n + "개를 삭제했습니다.");
                        } else Text.msg(s, dm.delete(a[2]) ? "&a던전을 삭제했습니다." : "&c없는 던전입니다.");
                    } else if (sub.equals("generate")) {
                        var w = s instanceof Player p ? p.getWorld() : Bukkit.getWorlds().get(0);
                        Text.msg(s, "&a던전 " + dm.generate(w, a.length > 2 ? Text.parseInt(a[2], 5) : 5) + "개를 맵 곳곳에 생성했습니다.");
                    } else {
                        for (var d : dm.all()) Text.msg(s, "&e" + d.id + " &f" + d.name + " &7" + d.tier + "단계 @ " + d.entrance.getBlockX() + ", " + d.entrance.getBlockY() + ", " + d.entrance.getBlockZ());
                        Text.msg(s, "&7/rpg관리 dungeon create <ID> <단계1~4> | generate [개수]");
                    }
                }
                case "questnpc" -> {
                    var qm = plugin.questNpcs();
                    if (a.length >= 3 && a[1].equals("scatter")) {
                        var w = s instanceof Player p ? p.getWorld() : Bukkit.getWorlds().get(0);
                        Text.msg(s, "&a의뢰 NPC " + qm.scatter(w, Text.parseInt(a[2], 10)) + "명을 맵 곳곳에 배치했습니다.");
                    } else if (a.length >= 3 && a[1].equals("here") && s instanceof Player p) {
                        try {
                            qm.spawn(p.getLocation(), kr.rpgcraft.world.QuestNpcManager.Type.valueOf(a[2].toUpperCase(Locale.ROOT)));
                            Text.msg(p, "&a의뢰 NPC 배치 완료");
                        } catch (IllegalArgumentException ex) {
                            Text.msg(p, "&c유형: hunter(사냥꾼), collector(수집가), herder(목동), explorer(탐험가)");
                        }
                    } else Text.msg(s, "&e/rpg관리 questnpc scatter <수> &7| &ehere <hunter|collector|herder|explorer> &7(현재 " + qm.count() + "명)");
                }
                case "title" -> {
                    var cm = plugin.content();
                    String sub = a.length > 1 ? a[1] : "list";
                    if (sub.equals("create") && a.length >= 4) {
                        cm.createTitle(a[2], String.join(" ", Arrays.copyOfRange(a, 3, a.length)));
                        Text.msg(s, "&a칭호 생성: " + a[2] + " → " + Text.c(cm.customTitles().get(a[2].toLowerCase(Locale.ROOT))));
                    } else if (sub.equals("delete") && a.length >= 3) {
                        Text.msg(s, cm.deleteTitle(a[2]) ? "&a삭제했습니다." : "&c없는 칭호입니다.");
                    } else if ((sub.equals("give") || sub.equals("take")) && a.length >= 4) {
                        PlayerData d = target(s, a, 2);
                        if (d == null) return true;
                        if (!cm.customTitles().containsKey(a[3].toLowerCase(Locale.ROOT))) { Text.msg(s, "&c없는 칭호입니다."); return true; }
                        cm.grantCustom(d, a[3].toLowerCase(Locale.ROOT), sub.equals("give"));
                        Text.msg(s, "&a" + d.name + " 에게 칭호를 " + (sub.equals("give") ? "주었습니다." : "회수했습니다."));
                        Player tp = Bukkit.getPlayer(d.uuid);
                        if (tp != null && sub.equals("give")) Text.msg(tp, "&d새 칭호를 받았습니다: " + Text.c(cm.customTitles().get(a[3].toLowerCase(Locale.ROOT))) + " &7(메뉴 > 업적 · 칭호)");
                    } else {
                        cm.customTitles().forEach((k, v) -> Text.msg(s, "&e" + k + " &f→ " + Text.c(v)));
                        Text.msg(s, "&7/rpg관리 title create <ID(영문)> <표시 (색코드 & 가능)> | delete <ID> | give <플레이어> <ID> | take <플레이어> <ID>");
                    }
                }
                case "plants" -> {
                    if (!(s instanceof Player p)) return true;
                    int r = a.length > 1 ? Text.parseInt(a[1], 4) : 4;
                    Text.msg(p, "&a주변 식물 " + plugin.cycle().cleanAround(p.getLocation(), Math.min(12, r)) + "개를 정리했습니다.");
                }
                case "mob" -> {
                    var cm = plugin.customMobs();
                    String sub = a.length > 1 ? a[1] : "list";
                    if (sub.equals("list")) {
                        for (var d : cm.defs()) Text.msg(s, "&e" + d.id + " &f" + d.name + " &7Lv." + d.minLevel + "~" + d.maxLevel + " " + d.type + " · " + cm.biomeHint(d));
                    } else if (sub.equals("spawn") && a.length >= 3 && s instanceof Player p) {
                        var d = cm.def(a[2]);
                        if (d == null) for (var x : cm.defs()) if (norm(x.name).equals(norm(a[2]))) d = x;
                        if (d == null) { Text.msg(p, "&c없는 몬스터입니다. /rpg관리 mob list"); return true; }
                        int lv = a.length >= 4 ? Text.parseInt(a[3], d.minLevel) : Math.max(d.minLevel, plugin.mobs().computeLevel(p.getLocation()));
                        cm.spawn(d, p.getLocation(), lv);
                        Text.msg(p, "&a" + d.name + " Lv." + lv + " 소환");
                    } else if (sub.equals("killall")) {
                        Text.msg(s, cm.killAll() + "마리 제거");
                    } else Text.msg(s, "&c/rpg관리 mob <list|spawn <id> [레벨]|killall>");
                }
                case "build" -> {
                    if (!(s instanceof Player p)) return true;
                    if (!p.hasPermission("rpgcraft.build")) { Text.msg(p, "&c권한이 없습니다. (rpgcraft.build)"); return true; }
                    boolean on = plugin.protection().toggleBuilder(p);
                    Text.msg(p, on ? "&a건축 모드 켜짐 &7- 크리에이티브 모드에서 블록을 자유롭게 설치/파괴할 수 있습니다." : "&7건축 모드 꺼짐");
                    if (on && p.getGameMode() != org.bukkit.GameMode.CREATIVE) Text.msg(p, "&e건축 모드는 크리에이티브 모드일 때만 적용됩니다. (/gamemode creative)");
                }
                case "pack" -> {
                    var pm = plugin.pack();
                    String sub = a.length > 1 ? a[1] : "status";
                    switch (sub) {
                        case "test" -> pm.selfTest(s);
                        case "reload" -> {
                            plugin.reloadConfig();
                            pm.load();
                            Text.msg(s, "&a리소스팩을 다시 불러왔습니다. SHA-1 " + pm.hashHex());
                        }
                        case "send" -> {
                            if (a.length > 2 && !a[2].equals("all")) {
                                Player t = Bukkit.getPlayerExact(a[2]);
                                if (t != null) pm.send(t);
                            } else Bukkit.getOnlinePlayers().forEach(pm::send);
                            Text.msg(s, "&a리소스팩 전송");
                        }
                        default -> {
                            Text.msg(s, "&fURL: &e" + pm.url());
                            Text.msg(s, "&fSHA-1: &7" + pm.hashHex() + " &f| 필수: " + pm.required() + " &f| 메뉴 배경: " + pm.overlay());
                            Text.msg(s, "&f적용 완료 플레이어: &a" + pm.loadedCount() + "/" + Bukkit.getOnlinePlayers().size() + " &7| 서버 " + Bukkit.getBukkitVersion());
                            for (Player op : Bukkit.getOnlinePlayers())
                                s.sendMessage(Text.c(" &7- " + op.getName() + ": " + (pm.hasPack(op) ? "&a적용됨" : "&c미적용") + " &8" + pm.sentUrl(op)
                                        + " &8(프로토콜 " + kr.rpgcraft.pack.PackManager.protocol(op) + ")"));
                        }
                    }
                }
                case "warp" -> {
                    String sub = a.length > 1 ? a[1] : "list";
                    if (sub.equals("list")) {
                        for (var w : plugin.menu().warps())
                            Text.msg(s, "&e" + w.id() + " &f" + w.name() + " &7Lv." + w.minLevel() + " " + Locs.block(w.loc()));
                    } else if (sub.equals("set") && a.length >= 4 && s instanceof Player p) {
                        Material icon = a.length > 4 ? Material.matchMaterial(a[4]) : null;
                        plugin.menu().setWarp(a[2], a[3].replace('_', ' '), icon == null ? Material.ENDER_PEARL : icon, p.getLocation(),
                                a.length > 5 ? Text.parseInt(a[5], 1) : 1);
                        Text.msg(s, "&a워프 " + a[2] + " 설정 (현재 위치)");
                    } else if (sub.equals("delete") && a.length >= 3) {
                        Text.msg(s, plugin.menu().deleteWarp(a[2]) ? "삭제 완료" : "&c없는 워프입니다.");
                    } else Text.msg(s, "&c/rpg관리 warp set <id> <이름(_=띄어쓰기)> [아이콘] [최소레벨] | delete <id> | list");
                }
                case "round" -> {
                    if (a.length >= 2 && a[1].equals("next")) plugin.rounds().next();
                    else Text.msg(s, "현재 " + plugin.rounds().round() + "회차 &7(/rpg관리 round next)");
                }
                case "reload" -> {
                    plugin.reload();
                    Text.msg(s, "&a설정/보스/상점/유적을 다시 불러왔습니다.");
                }
                case "rune" -> {
                    if (!(s instanceof Player p)) return true;
                    ItemStack h = p.getInventory().getItemInMainHand();
                    if (ItemData.category(h) != Category.RUNE) { Text.msg(p, "&c룬을 들어주세요."); return true; }
                    RuneManager.roll(h, ItemData.template(h).tier);
                    Text.msg(p, "재설정 완료");
                }
                default -> help(s);
            }
        } catch (Exception ex) {
            Text.msg(s, "&c오류: " + ex.getMessage());
            plugin.getLogger().warning("관리자 명령 오류: " + ex);
        }
        return true;
    }

    /** 옥션 관리: 창 / 목록 / 삭제 / 돌려보내기 / 한 사람 / 전체 */
    private void auction(CommandSender s, String[] a) {
        var au = plugin.auction();
        if (au == null) { Text.msg(s, "&c옥션이 꺼져 있습니다."); return; }
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "list" -> au.adminList(s, a.length > 2 ? Text.parseInt(a[2], 1) - 1 : 0);
            case "remove", "delete", "삭제", "return", "돌려보내기" -> {
                if (a.length < 3) { Text.msg(s, "&c/rpg관리 auction " + sub + " <번호> &7(번호는 /rpg관리 auction list)"); return; }
                boolean back = sub.equals("return") || sub.equals("돌려보내기");
                Text.msg(s, au.adminRemove(a[2], back) ? (back ? "&e판매자에게 돌려보냈습니다." : "&c삭제했습니다.") : "&c그 번호의 물건이 없습니다.");
            }
            case "player" -> {
                PlayerData d = target(s, a, 2);
                if (d == null) return;
                boolean back = a.length > 3 && (a[3].equals("return") || a[3].equals("돌려보내기"));
                int n = au.adminRemoveSeller(d.uuid, back);
                Text.msg(s, "&a" + d.name + " 님의 옥션 물건 " + n + "개를 " + (back ? "돌려보냈습니다." : "삭제했습니다."));
            }
            case "clear" -> {
                if (a.length < 3 || !a[2].equals("confirm")) { Text.msg(s, "&c정말 옥션 물건을 모두 지우려면: /rpg관리 auction clear confirm"); return; }
                Text.msg(s, "&c옥션 물건 " + au.clearAll() + "개를 모두 삭제했습니다. &7(받지 않은 대금 · 물건 포함)");
            }
            default -> {
                if (s instanceof Player p) au.openAdmin(p, 0);
                else au.adminList(s, 0);
            }
        }
    }

    /** 게임 초기화 */
    private void reset(CommandSender s, String[] a) {
        if (a.length >= 2 && a[1].equals("auction")) {   // 옥션만 초기화 (v5.4.35) — /rpg관리 auction clear confirm 과 같음
            if (a.length < 3 || !a[2].equals("confirm")) { Text.msg(s, "&c정말 옥션만 초기화하려면: /rpg관리 reset auction confirm &7(올라온 물건 · 받지 않은 대금 · 돌려받을 물건 모두 삭제)"); return; }
            if (plugin.auction() == null) { Text.msg(s, "&c옥션이 꺼져 있습니다."); return; }
            int n = plugin.auction().clearAll();
            Text.msg(s, "&a옥션을 초기화했습니다. &7(올라온 물건 " + n + "개 삭제, 다른 데이터는 그대로)");
            return;
        }
        if (a.length >= 3 && a[1].equals("player")) {
            PlayerData d = target(s, a, 2);
            if (d == null) return;
            if (a.length < 4 || !a[3].equals("confirm")) { Text.msg(s, "&c정말 초기화하려면: /rpg관리 reset player " + a[2] + " confirm"); return; }
            resetPlayer(d);
            Text.msg(s, "&a" + d.name + " 초기화 완료");
            return;
        }
        if (a.length >= 3 && a[1].equals("all") && a[2].equals("confirm")) {
            plugin.bosses().killAll();
            plugin.customMobs().killAll();
            if (plugin.auction() != null) plugin.auction().clearAll();   // 옥션 물건 · 대금 모두 삭제 (v5.4.18)
            for (var g : new ArrayList<>(plugin.guilds().all())) plugin.guilds().disband(g);
            if (plugin.getConfig().getBoolean("war.demolish-on-reset", true))   // v5.10.10 초기화: 길드와 함께 공성 성도 모두 허묾
                for (var c : new ArrayList<>(plugin.wars().castles())) plugin.wars().demolish(c, s);
            for (var c : plugin.wars().castles()) c.owner = null;
            plugin.wars().save();
            java.io.File folder = new java.io.File(plugin.getDataFolder(), "players");
            java.io.File[] files = folder.listFiles((dir, n) -> n.endsWith(".yml"));
            Set<String> online = new HashSet<>();
            for (Player p : Bukkit.getOnlinePlayers()) online.add(p.getUniqueId() + ".yml");
            if (files != null) for (java.io.File f : files) if (!online.contains(f.getName())) {
                try { kr.rpgcraft.data.ResetPending.mark(plugin, UUID.fromString(f.getName().replace(".yml", ""))); } catch (IllegalArgumentException ignored) { }   // 다음 접속 때 인벤토리도 초기화
                f.delete();
            }
            for (PlayerData d : new ArrayList<>(plugin.data().loaded())) resetPlayer(d);
            plugin.rounds().state().set("round", 1);
            plugin.rounds().state().set("shop-stock", null);
            plugin.rounds().state().set("war-declares", null);
            plugin.rounds().save();
            plugin.data().saveAll();
            Text.announce(Text.PREFIX + Text.c("&c&l게임이 초기화되었습니다! &f모든 플레이어가 처음부터 시작합니다."));
            return;
        }
        Text.msg(s, "&e/rpg관리 reset all confirm &7- 모든 플레이어 데이터·길드·성 소유·옥션·회차 초기화 (맵·구조물·설정은 유지)");
        Text.msg(s, "&e/rpg관리 reset player <이름> confirm &7- 한 명만 초기화");
        Text.msg(s, "&e/rpg관리 reset auction confirm &7- 옥션만 초기화 (올라온 물건 · 받지 않은 대금 · 돌려받을 물건)");
    }

    private void resetPlayer(PlayerData d) {
        if (plugin.auction() != null) plugin.auction().clearPlayer(d.uuid);   // 옥션에 올린 물건도 삭제 (v5.4.18)
        d.level = 0;   // 새로 온 사람과 같이 Lv.0 부터 → Lv.1 이 될 때 스탯 포인트를 받음 (예전엔 Lv.1 로 되돌려 첫 스탯을 못 받았음)
        d.exp = 0;
        d.statPoints = 0;
        d.str = d.dex = d.adv = 0;
        d.money = plugin.getConfig().getLong("player.starting-money", 10000);
        d.passives.clear();
        d.counters.clear();
        d.potionBag.clear();
        Arrays.fill(d.runes, null);
        d.blacksmith = false;
        d.job = null;
        d.subJob = null;
        d.thirdJob = null;
        d.quickSkill = null;
        plugin.legendary().releaseAll(d.uuid);
        var gd = plugin.guilds().of(d.uuid);   // 길드에서도 나가기 (길드장이면 위임, 혼자면 해산)
        if (gd != null) {
            plugin.guilds().removeMember(gd, d.uuid);   // 길드 목록과 "누가 어느 길드" 기록을 함께 지움
            if (gd.members.isEmpty()) {
                plugin.guilds().disband(gd);
                Text.announce(Text.PREFIX + Text.c("&7길드 [" + gd.name + "]이(가) 해산되었습니다."));
            } else {
                if (d.uuid.equals(gd.leader)) gd.leader = gd.members.iterator().next();
                plugin.guilds().save();
            }
        }
        d.guildChat = false;
        d.starterGiven = true;
        Arrays.fill(d.accessories, null);
        Player p = Bukkit.getPlayer(d.uuid);
        if (p != null) kr.rpgcraft.data.ResetPending.freshStart(plugin, p);
        else kr.rpgcraft.data.ResetPending.mark(plugin, d.uuid);   // 접속하지 않은 사람: 다음 접속 때 인벤토리 · 위치까지 마저 초기화 (v5.4.7)
        plugin.data().save(d);   // 접속하지 않은 사람도 바로 저장
    }

    private PlayerData target(CommandSender s, String[] a, int idx) {
        if (a.length <= idx) {
            Text.msg(s, "&c플레이어를 입력하세요.");
            return null;
        }
        Player p = Bukkit.getPlayerExact(a[idx]);
        PlayerData d = p != null ? plugin.data().get(p) : plugin.data().findByName(a[idx]);
        if (d == null) Text.msg(s, "&c플레이어를 찾을 수 없습니다.");
        return d;
    }

    private void give(CommandSender s, String[] a) {
        if (a.length < 3) { Text.msg(s, "&c/rpg관리 give <플레이어> <아이템ID> [수량]"); return; }
        Player p = Bukkit.getPlayerExact(a[1]);
        if (p == null) { Text.msg(s, "&c접속 중인 플레이어가 아닙니다."); return; }
        int amount = a.length > 3 ? Text.parseInt(a[3], 1) : 1;
        ItemTemplate t = resolve(a[2]);
        if (t == null) {
            List<String> sug = new ArrayList<>();
            String q = norm(a[2]);
            for (ItemTemplate x : plugin.items().all()) if (norm(x.name).contains(q) || x.id.contains(a[2])) sug.add(x.name.replace(' ', '_'));
            Text.msg(s, "&c아이템을 찾을 수 없습니다: " + a[2] + (sug.isEmpty() ? " &7(/rpg관리 items 검색어)" : " &7혹시: &f" + String.join(", ", sug.subList(0, Math.min(8, sug.size())))));
            return;
        }
        a[2] = t.id;
        boolean single = t.category.isEquipment() || t.category == Category.RUNE || t.category == Category.TOTEM || t.category == Category.CHECK;
        int loops = single ? Math.min(36, amount) : 1;
        for (int i = 0; i < loops; i++) {
            ItemStack it = plugin.items().create(a[2], single ? 1 : amount);
            if (t.category == Category.TOTEM) it = plugin.guilds().totemItem(plugin.guilds().randomTotem());
            for (ItemStack left : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
        }
        Text.msg(s, "&a" + p.getName() + "에게 " + t.name + " x" + amount + " 지급");
    }

    private static String norm(String s) {
        return s.replace("_", "").replace(" ", "").toLowerCase(Locale.ROOT);
    }

    /** 아이템 ID 또는 한글 이름(띄어쓰기는 _ 또는 생략)으로 찾기 */
    private ItemTemplate resolve(String q) {
        ItemTemplate t = plugin.items().get(q);
        if (t != null) return t;
        String n = norm(q);
        for (ItemTemplate x : plugin.items().all()) if (norm(x.name).equals(n)) return x;
        ItemTemplate only = null;
        for (ItemTemplate x : plugin.items().all()) {
            if (norm(x.name).contains(n)) {
                if (only != null) return null;
                only = x;
            }
        }
        return only;
    }

    private void passive(CommandSender s, String[] a) {
        PlayerData d = target(s, a, 1);
        if (d == null || a.length < 3) return;
        if (a[2].equals("list")) {
            StringBuilder sb = new StringBuilder();
            for (String id : d.passives) {
                Passive ps = Passive.find(id);
                sb.append(ps == null ? id : ps.label).append(", ");
            }
            Text.msg(s, d.name + " 패시브: " + sb);
            StringBuilder all = new StringBuilder();
            for (Passive ps : Passive.values()) all.append(ps.name()).append(' ');
            s.sendMessage(Text.c("&7전체: " + all));
            return;
        }
        if (a.length < 4) return;
        Passive ps = Passive.find(a[3]);
        if (ps == null) { Text.msg(s, "&c없는 패시브입니다."); return; }
        Player online = Bukkit.getPlayer(d.uuid);
        if (a[2].equals("add")) {
            if (online != null) plugin.passives().grant(online, ps, false);
            else d.passives.add(ps.name());
        } else if (a[2].equals("remove")) plugin.passives().revoke(d, ps);
        if (online != null) plugin.stats().refresh(online);
        Text.msg(s, "&a완료: " + ps.label);
    }

    private void boss(CommandSender s, String[] a) {
        if (a.length < 2 || a[1].equals("list")) {
            List<String> names = new ArrayList<>();
            for (String bid : plugin.bosses().ids()) names.add("&f" + Text.strip(Text.c(plugin.bosses().def(bid).name)) + " &8(" + bid + ")");
            Text.msg(s, "&e보스 목록: " + String.join("&7, ", names));
            return;
        }
        if (a[1].equals("killall")) {
            Text.msg(s, plugin.bosses().killAll() + "마리 제거");
            return;
        }
        if (a[1].equals("spawn") && a.length >= 3 && s instanceof Player p) {
            String id = a[2];
            if (plugin.bosses().def(id) == null)   // 한글 이름으로 입력한 경우
                for (String bid : plugin.bosses().ids()) if (norm(Text.strip(Text.c(plugin.bosses().def(bid).name))).equals(norm(id))) id = bid;
            LivingEntity e = plugin.bosses().def(id) == null ? null : plugin.bosses().spawn(id, p.getLocation());
            Text.msg(s, e == null ? "&c없는 보스입니다. &7(/rpg관리 boss list)" : "&a" + Text.strip(Text.c(plugin.bosses().def(id).name)) + "을(를) 소환했습니다.");
        }
    }

    private Location targetBlock(Player p) {
        Block b = p.getTargetBlockExact(8);
        return b == null ? p.getLocation().getBlock().getLocation() : b.getLocation();
    }

    private void castle(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) return;
        var wm = plugin.wars();
        String sub = a.length > 1 ? a[1] : "list";
        switch (sub) {
            case "build" -> {   // /rpg관리 castle build <1|2|3> <id> : 내 자리에 대형 공성 성 (성벽 · 신호기 자동 등록, v5.5.0)
                if (a.length < 4) {
                    Text.msg(p, "&e/rpg관리 castle build <1|2|3> <id> &7- 1 왕성 · 2 흑요 요새 · 3 백악 성채 (내 자리가 성 한가운데)");
                    return;
                }
                String err = new kr.rpgcraft.war.SiegeCastleBuilder(plugin).start(p, Text.parseInt(a[2], 0), a[3], p.getLocation().getBlock().getLocation());
                if (err != null) Text.msg(p, "&c" + err);
                return;
            }
            case "list" -> {
                for (Castle c : wm.castles())
                    Text.msg(p, "&e" + c.id + " &f" + c.name + " &7소유 " + c.owner + " 성벽 " + c.walls.size() + " 신호기 " + (c.beacon == null ? "없음" : Locs.block(c.beacon)));
            }
            case "create" -> {
                if (a.length < 4) { Text.msg(p, "&c/rpg관리 castle create <id> <이름>"); return; }
                wm.create(a[2], a[3]);
                Text.msg(p, "&a성 생성. pos1/pos2 로 성벽 영역을 잡고 wall 명령으로 등록하세요.");
            }
            case "pos1", "pos2" -> {
                Location l = targetBlock(p);
                wm.selection(p)[sub.equals("pos1") ? 0 : 1] = l;
                Text.msg(p, sub + " = " + Locs.block(l));
            }
            case "wall" -> {
                if (a.length < 5) { Text.msg(p, "&c/rpg관리 castle wall <성ID> <성벽ID> <체력>"); return; }
                Castle c = wm.castle(a[2]);
                Location[] sel = wm.selection(p);
                if (c == null || sel[0] == null || sel[1] == null) { Text.msg(p, "&c성 ID 또는 pos1/pos2 를 확인하세요."); return; }
                wm.addWall(c, a[3], sel[0], sel[1], Text.parseDouble(a[4], 10000));
                Text.msg(p, "&a성벽 " + a[3] + " 등록 (" + c.walls.get(c.walls.size() - 1).volume() + "블록)");
            }
            case "beacon" -> {
                Castle c = a.length > 2 ? wm.castle(a[2]) : null;
                Block b = p.getTargetBlockExact(8);
                if (c == null || b == null || b.getType() != Material.BEACON) { Text.msg(p, "&c성 ID를 입력하고 신호기를 바라보세요."); return; }
                c.beacon = b.getLocation();
                wm.save();
                Text.msg(p, "&a신호기 설정 완료");
            }
            case "spawn" -> {
                Castle c = a.length > 3 ? wm.castle(a[2]) : null;
                if (c == null) { Text.msg(p, "&c/rpg관리 castle spawn <성ID> <attacker|defender>"); return; }
                if (a[3].equals("attacker")) c.attackerSpawn = p.getLocation();
                else c.defenderSpawn = p.getLocation();
                wm.save();
                Text.msg(p, "&a스폰 설정 완료");
            }
            case "owner" -> {
                Castle c = a.length > 3 ? wm.castle(a[2]) : null;
                if (c == null) { Text.msg(p, "&c/rpg관리 castle owner <성ID> <길드|none>"); return; }
                c.owner = a[3].equals("none") ? null : a[3];
                wm.save();
                Text.msg(p, "&a소유 길드: " + c.owner);
            }
            case "delete" -> {
                if (a.length > 2) wm.delete(a[2]);
                Text.msg(p, "삭제 완료 &7(목록에서만 지움, 건물은 그대로 · 건물까지 허물려면 castle demolish)");
            }
            case "demolish", "remove", "철거" -> {   // v5.10.10 성 없애기: 목록에서 지우고 짓기 전 땅으로 되돌림
                Castle c = a.length > 2 ? wm.castle(a[2]) : null;
                if (c == null) { Text.msg(p, "&c/rpg관리 castle demolish <성ID> &7- 성을 허물고 짓기 전 땅으로 되돌림"); return; }
                wm.demolish(c, p);
            }
            case "restore" -> {
                Castle c = a.length > 2 ? wm.castle(a[2]) : null;
                if (c != null) wm.restore(c);
                Text.msg(p, "복구 완료");
            }
            default -> help(p);
        }
    }

    private void ruin(CommandSender s, String[] a) {
        RuinManager rm = plugin.ruins();
        String sub = a.length > 1 ? a[1] : "list";
        if (sub.equals("themes")) {
            StringBuilder sb = new StringBuilder("&e유적 테마: ");
            for (var t : StructureManager.RUIN_THEMES) sb.append("&f").append(t.key()).append("&7(").append(t.label()).append(") ");
            Text.msg(s, sb.toString());
            return;
        }
        if (sub.equals("build")) {   // /rpg관리 ruin build [테마|random] [here|random]
            var theme = a.length > 2 && !a[2].equalsIgnoreCase("random") ? StructureManager.ruinTheme(a[2]) : null;
            if (a.length > 2 && !a[2].equalsIgnoreCase("random") && theme == null) { Text.msg(s, "&c없는 테마입니다. &7/rpg관리 ruin themes"); return; }
            boolean here = s instanceof Player && !(a.length > 3 && a[3].equalsIgnoreCase("random"));
            if (here) {
                Player p = (Player) s;
                String res = plugin.structures().buildRuin(p.getLocation().getBlock().getLocation(), theme, null);
                Text.msg(s, "&a유적 생성: " + res);
            } else {
                Object[] res = plugin.structures().buildRuinRandom(theme);
                if (res == null) { Text.msg(s, "&c유적을 지을 땅을 찾지 못했습니다."); return; }
                Location at = (Location) res[1];
                Text.msg(s, "&a유적 생성: " + res[0] + " &7@ " + at.getBlockX() + ", " + at.getBlockY() + ", " + at.getBlockZ());
            }
            return;
        }
        if (sub.equals("tp")) {
            if (!(s instanceof Player p) || a.length < 3) { Text.msg(s, "&c/rpg관리 ruin tp <id>"); return; }
            RuinManager.Ruin r = rm.get(a[2]);
            Location l = r == null ? null : rm.startOf(r);
            if (l == null) { Text.msg(s, "&c없는 유적입니다."); return; }
            p.teleport(l);
            return;
        }
        if (sub.equals("list")) {
            for (RuinManager.Ruin r : rm.all())
                Text.msg(s, "&e" + r.id + " &f" + r.name + " &7모험 " + r.minAdv + " 시작 " + r.start + " 도착 " + r.end + " 보상패시브 " + r.passive + "/" + r.firstPassive);
            return;
        }
        if (a.length < 3) { Text.msg(s, "&c/rpg관리 ruin " + sub + " <id> ..."); return; }
        if (sub.equals("create")) {
            rm.create(a[2], a.length > 3 ? a[3] : a[2]);
            Text.msg(s, "&a유적 생성. 시작 블록 위에서 start, 도착 블록 위에서 end");
            return;
        }
        RuinManager.Ruin r = rm.get(a[2]);
        if (r == null) { Text.msg(s, "&c없는 유적입니다."); return; }
        switch (sub) {
            case "start", "end" -> {
                if (s instanceof Player p) rm.setPoint(r, p, sub.equals("start"));
            }
            case "adv" -> r.minAdv = a.length > 3 ? Text.parseInt(a[3], 0) : 0;
            case "limit" -> r.timeLimit = a.length > 3 ? Text.parseInt(a[3], 0) : 0;
            case "passive" -> r.passive = a.length > 3 ? a[3] : null;
            case "first" -> r.firstPassive = a.length > 3 ? a[3] : null;
            case "delete" -> {
                rm.delete(r.id);
                Text.msg(s, "삭제 완료");
                return;
            }
            default -> { Text.msg(s, "&c알 수 없는 하위 명령"); return; }
        }
        rm.save();
        Text.msg(s, "&a유적 " + r.id + " 설정 완료 &7(보상 금액/경험치/아이템은 ruins.yml 수정 후 reload)");
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) return SUBS.stream().filter(x -> x.startsWith(a[0].toLowerCase())).toList();
        switch (a[0]) {
            case "give" -> {
                if (a.length == 2) Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
                if (a.length == 3) {
                    String q = a[2].toLowerCase(Locale.ROOT);
                    for (ItemTemplate t : plugin.items().all()) {
                        String nm = t.name.replace(' ', '_');
                        if (nm.startsWith(a[2]) || nm.contains(a[2])) out.add(nm);
                        else if (t.id.startsWith(q)) out.add(t.id);
                    }
                }
            }
            case "boss" -> {
                if (a.length == 2) out.addAll(List.of("spawn", "list", "killall"));
                if (a.length == 3) for (String bid : plugin.bosses().ids()) {
                    out.add(bid);
                    out.add(Text.strip(Text.c(plugin.bosses().def(bid).name)).replace(' ', '_'));
                }
            }
            case "stock" -> {
                if (a.length == 2) for (var st : kr.rpgcraft.economy.StockManager.STOCKS) out.add(st.id());
            }
            case "coin" -> {
                if (a.length == 2) Bukkit.getOnlinePlayers().forEach(pl -> out.add(pl.getName()));
                if (a.length == 3) out.addAll(List.of("10", "50", "100"));
            }
            case "enhance" -> {
                if (a.length == 2) out.addAll(List.of("0", "5", "8", "10", "12", "15"));
                if (a.length == 3) out.addAll(Text.onlineNames(a[2], s));
            }
            case "auction" -> {
                if (a.length == 2) out.addAll(List.of("list", "remove", "return", "player", "clear"));
                if (a.length == 3 && (a[1].equals("remove") || a[1].equals("return")) && plugin.auction() != null) out.addAll(plugin.auction().ids());
                if (a.length == 3 && a[1].equals("player")) Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
                if (a.length == 4 && a[1].equals("player")) out.addAll(List.of("delete", "return"));
            }
            case "npc" -> plugin.shops().all().forEach(sh -> out.add(sh.id));
            case "castle" -> {
                if (a.length == 2) out.addAll(List.of("build", "create", "pos1", "pos2", "wall", "beacon", "spawn", "owner", "delete", "demolish", "list", "restore"));
                if (a.length == 3 && a[1].equals("build")) out.addAll(List.of("1", "2", "3"));
                else if (a.length == 3) plugin.wars().castles().forEach(ca -> out.add(ca.id));
                if (a.length == 4 && a[1].equals("build")) out.add("castle_" + (plugin.wars().castles().size() + 1));
            }
            case "ruin" -> {
                if (a.length == 2) out.addAll(List.of("build", "themes", "tp", "create", "start", "end", "adv", "limit", "passive", "first", "delete", "list"));
                if (a.length == 3 && a[1].equals("build")) { out.add("random"); StructureManager.RUIN_THEMES.forEach(t -> out.add(t.key())); }
                else if (a.length == 3) plugin.ruins().all().forEach(r -> out.add(r.id));
                if (a.length == 4 && a[1].equals("build")) out.addAll(List.of("here", "random"));
                if (a.length == 4 && (a[1].equals("passive") || a[1].equals("first"))) for (Passive p : Passive.values()) out.add(p.name());
            }
            case "passive" -> {
                if (a.length == 2) Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
                if (a.length == 3) out.addAll(List.of("add", "remove", "list"));
                if (a.length == 4) for (Passive p : Passive.values()) if (p.name().startsWith(a[3].toUpperCase())) out.add(p.name());
            }
            case "money" -> {
                if (a.length == 2) Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
                if (a.length == 3) out.addAll(List.of("set", "add", "take"));
            }
            case "round" -> out.add("next");
            case "structure" -> { if (a.length == 2) out.addAll(StructureManager.NAMES.keySet()); }
            case "worldboss" -> { if (a.length == 2) out.addAll(List.of("random", "remove", "desert_nightmare", "siphonia", "kain", "vengeful_spirit")); if (a.length == 3) out.add("here"); }
            case "title" -> {
                if (a.length == 2) out.addAll(List.of("create", "delete", "give", "take", "list"));
                if (a.length == 4 && (a[1].equals("give") || a[1].equals("take"))) out.addAll(plugin.content().customTitles().keySet());
                if (a.length == 3 && a[1].equals("delete")) out.addAll(plugin.content().customTitles().keySet());
            }
            case "dungeon" -> { if (a.length == 2) out.addAll(List.of("create", "generate", "list", "delete"));
                if (a.length == 3 && a[1].equals("delete")) { out.add("all"); for (var d : plugin.dungeons().all()) out.add(d.id); } if (a.length == 4 && a[1].equals("create")) out.addAll(List.of("1", "2", "3", "4")); }
            case "questnpc" -> { if (a.length == 2) out.addAll(List.of("scatter", "here")); if (a.length == 3 && a[1].equals("here")) out.addAll(List.of("hunter", "collector", "herder", "explorer")); }
            case "reset" -> {
                if (a.length == 2) out.addAll(List.of("all", "player", "auction"));
                if (a.length == 3 && (a[1].equals("all") || a[1].equals("auction"))) out.add("confirm");
            }
            case "mob" -> {
                if (a.length == 2) out.addAll(List.of("list", "spawn", "killall"));
                if (a.length == 3 && a[1].equals("spawn")) plugin.customMobs().defs().forEach(d -> { out.add(d.id); out.add(d.name.replace(' ', '_')); });
            }
            case "pack" -> { if (a.length == 2) out.addAll(List.of("status", "test", "reload", "send")); }
            case "warp" -> {
                if (a.length == 2) out.addAll(List.of("set", "delete", "list"));
                if (a.length == 3) plugin.menu().warps().forEach(w -> out.add(w.id()));
            }
            case "war" -> out.add("stop");
            default -> Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        }
        return out;
    }
}
