package kr.rpgcraft.world;

import kr.rpgcraft.Keys;
import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.data.PlayerData;
import kr.rpgcraft.gui.Gui;
import kr.rpgcraft.item.ItemData;
import kr.rpgcraft.mob.CustomMobManager;
import kr.rpgcraft.mob.MobManager;
import kr.rpgcraft.mob.MonsterTierManager;
import kr.rpgcraft.util.Fx;
import kr.rpgcraft.util.Text;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 할 거리 모음
 *  1) 출석 보상 — 7일 주기, 매일 접속해 메뉴에서 받기 (연속 출석일수록 좋은 보상)
 *  2) 업적 · 칭호 — 사냥·보스·낚시·던전·웨이브·보물 등 20종, 달성 보상 + 채팅에 표시되는 칭호
 *  3) 현상수배 — 40분마다 맵 어딘가에 강력한 현상수배범 출현 (나침반 ⚔ 표시), 처치한 사람이 현상금 획득
 *  4) 보물 지도 — 정예·중간 보스·낚시 보물에서 획득, 우클릭하면 보물 위치가 정해지고 나침반 ✚ 로 안내,
 *     도착하면 보물 상자와 수호자가 나타남
 */
public class ContentManager implements Listener {
    private final RpgCraft plugin;
    private final NamespacedKey MAP_KEY, BOUNTY_KEY;
    private final Random rnd = new Random();

    public ContentManager(RpgCraft plugin) {
        this.plugin = plugin;
        this.MAP_KEY = new NamespacedKey(plugin, "treasure_target");
        this.BOUNTY_KEY = new NamespacedKey(plugin, "bounty");
        long every = plugin.getConfig().getLong("bounty.interval-minutes", 40) * 60 * 20;
        Bukkit.getScheduler().runTaskTimer(plugin, this::spawnBounty, 20L * 120, every);
        Bukkit.getScheduler().runTaskTimer(plugin, this::mapTick, 40L, 40L);
    }

    private static long today() {
        return LocalDate.now().toEpochDay();
    }

    private void give(Player p, String id, int n) {
        ItemStack it = plugin.items().create(id, n);
        if (it != null) for (ItemStack l : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), l);
    }

    // =================================================================== 1. 출석
    /** 30일 출석 보상 (7·14·21·30일째는 특별 보상) */
    private static final String[][] ATT = buildAtt();

    private static String[][] buildAtt() {
        String[][] a = new String[30][];
        for (int i = 0; i < 30; i++) {
            int d = i + 1;
            String money = "money:" + (2000 + d * 300);
            a[i] = switch (d) {
                case 7 -> new String[]{"ticket_rate10:1", "treasure_map:1", money};
                case 14 -> new String[]{"rune_mid:1", "scroll_exp:2", money};
                case 21 -> new String[]{"ticket_protect:1", "crystal_high:3", money};
                case 30 -> new String[]{"rune_high:1", "ticket_protect:2", "treasure_map:2", "money:100000"};
                default -> switch (d % 5) {
                    case 0 -> new String[]{"crystal_mid:2", money};
                    case 1 -> new String[]{"potion_2:3", money};
                    case 2 -> new String[]{"scroll_atk:1", money};
                    case 3 -> new String[]{"ticket_rune:1", money};
                    default -> new String[]{"crystal_low:3", money};
                };
            };
        }
        return a;
    }

    public int streak(PlayerData d) {
        long last = (long) d.counter("att_last");
        if (last != 0 && today() - last > 1) return 0; // 하루라도 빠지면 처음부터
        return (int) d.counter("att_streak");
    }

    public boolean canClaim(PlayerData d) {
        return (long) d.counter("att_last") != today();
    }

    public void claim(Player p) {
        PlayerData d = plugin.data().get(p);
        if (!canClaim(d)) { Text.msg(p, "&7오늘 출석 보상은 이미 받았습니다. 내일 또 오세요!"); return; }
        int day = streak(d) % ATT.length;
        for (String r : ATT[day]) {
            String[] kv = r.split(":");
            if (kv[0].equals("money")) plugin.economy().give(p, Long.parseLong(kv[1]));
            else give(p, kv[0], Integer.parseInt(kv[1]));
        }
        d.counters.put("att_streak", (double) (streak(d) + 1));
        d.counters.put("att_last", (double) today());
        d.counters.merge("ach_attend", 1.0, Double::sum);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.3f);
        Text.msg(p, "&a출석 " + (day + 1) + "일차 보상을 받았습니다! &7(연속 " + (streak(d)) + "일)");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        PlayerData d = plugin.data().get(e.getPlayer());
        if (canClaim(d)) Bukkit.getScheduler().runTaskLater(plugin, () ->
                Text.msg(e.getPlayer(), "&e오늘의 출석 보상이 준비되었습니다!"), 60L);
    }

    public class AttendGui extends Gui {
        public AttendGui(Player p) {
            super(6, "&8출석 보상 (30일)");
            PlayerData d = plugin.data().get(p);
            int cur = streak(d) % ATT.length;
            boolean can = canClaim(d);
            for (int i = 0; i < ATT.length; i++) {
                List<String> lore = new ArrayList<>();
                for (String r : ATT[i]) {
                    String[] kv = r.split(":");
                    lore.add("&f· " + (kv[0].equals("money") ? Text.money(Long.parseLong(kv[1])) : plugin.items().get(kv[0]).name + " x" + kv[1]));
                }
                boolean got = i < cur;              // 받은 날 = 연속 출석 일수만큼
                boolean today = i == cur && can;
                boolean special = (i + 1) % 7 == 0 || i == 29;
                if (today) lore.add("&e▶ 클릭해서 받기");
                int slot = i < 27 ? i + (i / 9) * 0 : i;
                set(slot, button(got ? Material.LIME_STAINED_GLASS_PANE : today ? Material.CHEST : special ? Material.GOLD_BLOCK : Material.PAPER,
                        (got ? "&a✔ " : today ? "&e&l" : special ? "&6" : "&7") + (i + 1) + "일차" + (special ? " ★" : ""), lore.toArray(new String[0])), e -> {
                    if (today) { claim(p); p.closeInventory(); }
                });
            }
            set(49, button(Material.CLOCK, "&6연속 출석 " + streak(d) + "일", "&7하루라도 빠지면 1일차부터 다시 시작합니다", "&730일을 채우면 처음부터 다시"));
            fill(30, 53);
        }
    }

    // =================================================================== 2. 업적 · 칭호
    public record Ach(String id, String name, String desc, String key, int target, long money, String title) {}

    public static final List<Ach> ACH = List.of(
            new Ach("kill100", "사냥꾼의 첫걸음", "몬스터 100마리 처치", "ach_kills", 100, 20000, "초보 사냥꾼"),
            new Ach("kill1000", "학살자", "몬스터 1,000마리 처치", "ach_kills", 1000, 150000, "학살자"),
            new Ach("kill10000", "전설의 사냥꾼", "몬스터 10,000마리 처치", "ach_kills", 10000, 1500000, "전설의 사냥꾼"),
            new Ach("elite50", "정예 사냥꾼", "정예 몬스터 50마리 처치", "ach_elite", 50, 80000, "정예 사냥꾼"),
            new Ach("mini10", "거인 살해자", "중간 보스 10마리 처치", "ach_mini", 10, 200000, "거인 살해자"),
            new Ach("boss1", "첫 보스", "보스 처치에 참여", "ach_boss", 1, 50000, "도전자"),
            new Ach("boss25", "보스 헌터", "보스 25회 처치에 참여", "ach_boss", 25, 800000, "보스 헌터"),
            new Ach("fish30", "낚시꾼", "물고기 30마리 낚기", "fish_caught", 30, 30000, "낚시꾼"),
            new Ach("fish300", "강태공", "물고기 300마리 낚기", "fish_caught", 300, 300000, "강태공"),
            new Ach("dungeon1", "던전 탐험가", "던전 클리어", "ach_dungeon", 1, 60000, "던전 탐험가"),
            new Ach("dungeon20", "던전 정복자", "던전 20회 클리어", "ach_dungeon", 20, 700000, "던전 정복자"),
            new Ach("wave5", "방어선", "필드 웨이브 5회 클리어", "ach_wave", 5, 100000, "수호자"),
            new Ach("bounty1", "현상금 사냥꾼", "현상수배범 처치", "ach_bounty", 1, 100000, "현상금 사냥꾼"),
            new Ach("bounty10", "무법자의 천적", "현상수배범 10명 처치", "ach_bounty", 10, 1000000, "무법자의 천적"),
            new Ach("treasure5", "보물 사냥꾼", "보물 지도 5장 해독", "ach_treasure", 5, 150000, "보물 사냥꾼"),
            new Ach("quest30", "해결사", "의뢰 30개 완료", "ach_quest", 30, 200000, "해결사"),
            new Ach("attend7", "개근상", "출석 7일", "ach_attend", 7, 70000, "개근생"),
            new Ach("attend30", "터줏대감", "출석 30일", "ach_attend", 30, 500000, "터줏대감"),
            new Ach("jackpot", "행운아", "룰렛에서 ×5 이상 당첨", "ach_jackpot", 1, 0, "행운아"),
            new Ach("level50", "숙련자", "레벨 50 달성", "#level", 50, 300000, "숙련자"),
            new Ach("level100", "초월자", "레벨 100 달성", "#level", 100, 2000000, "초월자"));

    public int progress(PlayerData d, Ach a) {
        return a.key().equals("#level") ? d.level : (int) d.counter(a.key());
    }

    // ------------------------------------------------------------------ 칭호: 업적(1) · 레전더리 전용(2) · 관리자 제작(3)
    private static final String[] LEGEND_TITLE = {"폭군", "불사자", "폭풍의 주인", "황금의 손", "심판관"};
    /** 히든 패시브(서버에 3명까지) 칭호 (v5.2.8) — kind 5, idx = 이 배열 순서 */
    private static final kr.rpgcraft.passive.Passive[] HIDDEN_PASSIVES = {kr.rpgcraft.passive.Passive.SWORD_SAINT, kr.rpgcraft.passive.Passive.SAGE_WISDOM, kr.rpgcraft.passive.Passive.KINGS_MAJESTY};
    private static final String[] HIDDEN_PASSIVE_TITLE = {"검성", "대현자", "왕좌의 계승자"};

    /** 히든 직업을 얻거나 승급했을 때: 칭호를 주고 바로 장착 (kind 4 = 지금 단계의 직업 이름) */
    public void onHiddenJob(Player p, String label) {
        PlayerData d = plugin.data().get(p);
        d.counters.put("title_kind", 4.0);
        d.counters.put("title_idx", 0.0);
        Text.msg(p, "&d✦ 칭호 획득! &5&l[" + label + "] &7(업적 · 칭호 메뉴에서 바꿀 수 있습니다)");
        Text.announce(Text.PREFIX + Text.c("&5" + Text.name(p) + "&f님이 히든 직업 &5&l[" + label + "]&f을(를) 얻었습니다!"));
        for (Player o : Bukkit.getOnlinePlayers()) if (o != p) o.playSound(o.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.35f, 1.6f);
    }

    /** 히든 패시브를 얻었을 때: 칭호를 주고 바로 장착 */
    public void onHiddenPassive(Player p, kr.rpgcraft.passive.Passive ps) {
        for (int i = 0; i < HIDDEN_PASSIVES.length; i++) {
            if (HIDDEN_PASSIVES[i] != ps) continue;
            PlayerData d = plugin.data().get(p);
            d.counters.put("title_kind", 5.0);
            d.counters.put("title_idx", (double) i);
            Text.msg(p, "&d✦ 칭호 획득! &b&l[" + HIDDEN_PASSIVE_TITLE[i] + "] &7(업적 · 칭호 메뉴에서 바꿀 수 있습니다)");
            Text.announce(Text.PREFIX + Text.c("&b" + Text.name(p) + "&f님이 히든 칭호 &b&l[" + HIDDEN_PASSIVE_TITLE[i] + "]&f을(를) 얻었습니다!"));
        }
    }
    private final java.io.File titleFile = new java.io.File(RpgCraft.get().getDataFolder(), "titles.yml");
    private final LinkedHashMap<String, String> customTitles = new LinkedHashMap<>();

    {
        var y = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(titleFile);
        for (String k : y.getKeys(false)) customTitles.put(k, y.getString(k));
    }

    private void saveTitles() {
        var y = new org.bukkit.configuration.file.YamlConfiguration();
        customTitles.forEach(y::set);
        try { y.save(titleFile); } catch (java.io.IOException ignored) { }
    }

    public Map<String, String> customTitles() {
        return customTitles;
    }

    public void createTitle(String id, String display) { customTitles.put(id.toLowerCase(Locale.ROOT), display); saveTitles(); }

    public boolean deleteTitle(String id) { boolean ok = customTitles.remove(id.toLowerCase(Locale.ROOT)) != null; if (ok) saveTitles(); return ok; }

    public boolean hasCustom(PlayerData d, String id) { return d.counter("ctitle_" + id) > 0; }

    public void grantCustom(PlayerData d, String id, boolean give) {
        if (give) d.counters.put("ctitle_" + id, 1.0); else { d.counters.remove("ctitle_" + id); if ((int) d.counter("title_kind") == 3) d.counters.put("title_kind", 0.0); }
    }

    /** 채팅·탭에 붙는 칭호 (없으면 "") */
    public String title(PlayerData d) {
        if (d.counter("ach_title") > 0 && d.counter("title_kind") == 0) { // 이전 버전 저장값 이전
            d.counters.put("title_kind", 1.0);
            d.counters.put("title_idx", d.counter("ach_title") - 1);
            d.counters.remove("ach_title");
        }
        int kind = (int) d.counter("title_kind"), idx = (int) d.counter("title_idx");
        switch (kind) {
            case 1 -> { if (idx < ACH.size() && d.counter("ach_done_" + ACH.get(idx).id()) > 0) return ACH.get(idx).title(); }
            case 2 -> {
                var ls = kr.rpgcraft.feature.LegendaryManager.Legend.values();
                if (idx < ls.length && plugin.legendary().owned(d.uuid).contains(ls[idx])) return Text.c("&6" + LEGEND_TITLE[idx] + "&d");
            }
            case 3 -> {
                List<String> keys = new ArrayList<>(customTitles.keySet());
                if (idx < keys.size() && hasCustom(d, keys.get(idx))) return Text.c(customTitles.get(keys.get(idx)) + "&d");
            }
            case 4 -> {   // 히든 직업: 지금 단계의 직업 이름
                var t = kr.rpgcraft.world.HiddenJobManager.of(d);
                if (t != null) return Text.c("&5" + t.label() + "&d");
            }
            case 5 -> {   // 히든 패시브 (가지고 있는 동안만)
                if (idx < HIDDEN_PASSIVES.length && d.passives.contains(HIDDEN_PASSIVES[idx].name())) return Text.c("&b" + HIDDEN_PASSIVE_TITLE[idx] + "&d");
            }
            default -> { }
        }
        return "";
    }

    private void equip(PlayerData d, int kind, int idx) {
        boolean same = (int) d.counter("title_kind") == kind && (int) d.counter("title_idx") == idx;
        d.counters.put("title_kind", same ? 0.0 : kind);
        d.counters.put("title_idx", (double) idx);
    }

    public class AchGui extends Gui {
        public AchGui(Player p) {
            super(6, "&8업적 · 칭호");
            PlayerData d = plugin.data().get(p);
            int kind = (int) d.counter("title_kind"), sel = (int) d.counter("title_idx");
            for (int i = 0; i < ACH.size() && i < 27; i++) {
                Ach a = ACH.get(i);
                boolean claimed = d.counter("ach_done_" + a.id()) > 0;
                boolean ready = !claimed && progress(d, a) >= a.target();
                boolean equipped = kind == 1 && sel == i;
                int idx = i;
                // 달성 전에는 조건을 알려주지 않는다
                set(i, button(claimed ? (equipped ? Material.NAME_TAG : Material.LIME_DYE) : ready ? Material.GOLD_INGOT : Material.GRAY_DYE,
                        (claimed ? "&a✔ " : ready ? "&e&l" : "&7") + (claimed || ready ? a.name() : "??? 업적"),
                        claimed ? "&7" + a.desc() : "&8달성 조건: ???",
                        claimed ? "&6칭호 &f[" + a.title() + "]" : ready ? "&e무언가를 해냈습니다!" : "&8모험을 계속하다 보면...",
                        claimed ? (equipped ? "&b현재 칭호 (클릭해서 해제)" : "&e▶ 클릭해서 칭호 장착") : ready ? "&e▶ 클릭해서 보상 받기" : ""), e -> {
                    if (ready) {
                        d.counters.put("ach_done_" + a.id(), 1.0);
                        if (a.money() > 0) plugin.economy().give(p, a.money());
                        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
                        Text.announce(Text.PREFIX + Text.c("&e" + p.getName() + "&f님이 업적 &6「" + a.name() + "」&f을(를) 달성했습니다!"));
                        new AchGui(p).open(p);
                    } else if (claimed) {
                        equip(d, 1, idx);
                        new AchGui(p).open(p);
                    }
                });
            }
            // 레전더리 전용 칭호 (해당 레전더리 패시브를 가진 동안만)
            var ls = kr.rpgcraft.feature.LegendaryManager.Legend.values();
            var owned = plugin.legendary().owned(p.getUniqueId());
            for (int i = 0; i < ls.length; i++) {
                boolean has = owned.contains(ls[i]);
                boolean equipped = kind == 2 && sel == i;
                int idx = i;
                set(29 + i, button(has ? (equipped ? Material.NAME_TAG : Material.NETHER_STAR) : Material.BLACK_DYE,
                        has ? "&6&l" + LEGEND_TITLE[i] : "&8레전더리 칭호", has ? "&7「" + ls[i].label + "」 주인 전용 칭호" : "&8???",
                        has ? (equipped ? "&b현재 칭호 (클릭해서 해제)" : "&e▶ 클릭해서 장착") : ""), e -> {
                    if (has) { equip(d, 2, idx); new AchGui(p).open(p); }
                });
            }
            // 히든 직업 · 히든 패시브 칭호 (가지고 있는 동안만)
            var hj = kr.rpgcraft.world.HiddenJobManager.of(d);
            boolean hjEq = kind == 4;
            set(46, button(hj != null ? (hjEq ? Material.NAME_TAG : Material.WITHER_SKELETON_SKULL) : Material.BLACK_DYE,
                    hj != null ? "&5&l" + hj.label() : "&8히든 직업 칭호", hj != null ? "&7히든 직업 전용 칭호 (승급하면 이름도 바뀜)" : "&8???",
                    hj != null ? (hjEq ? "&b현재 칭호 (클릭해서 해제)" : "&e▶ 클릭해서 장착") : ""), e -> {
                if (hj != null) { equip(d, 4, 0); new AchGui(p).open(p); }
            });
            for (int i = 0; i < HIDDEN_PASSIVES.length; i++) {
                boolean has = d.passives.contains(HIDDEN_PASSIVES[i].name());
                boolean equipped = kind == 5 && sel == i;
                int idx = i;
                set(48 + i, button(has ? (equipped ? Material.NAME_TAG : Material.HEART_OF_THE_SEA) : Material.BLACK_DYE,
                        has ? "&b&l" + HIDDEN_PASSIVE_TITLE[i] : "&8히든 칭호", has ? "&7「" + HIDDEN_PASSIVES[i].label + "」 주인 전용 칭호 &8(서버에 3명)" : "&8???",
                        has ? (equipped ? "&b현재 칭호 (클릭해서 해제)" : "&e▶ 클릭해서 장착") : ""), e -> {
                    if (has) { equip(d, 5, idx); new AchGui(p).open(p); }
                });
            }
            // 관리자 제작 칭호 (받은 것만 표시)
            List<String> keys = new ArrayList<>(customTitles.keySet());
            int slot = 36;
            for (int i = 0; i < keys.size() && slot < 45; i++) {
                if (!hasCustom(d, keys.get(i))) continue;
                boolean equipped = kind == 3 && sel == i;
                int idx = i;
                set(slot++, button(equipped ? Material.NAME_TAG : Material.PAPER, Text.c(customTitles.get(keys.get(i))), "&7특별 칭호",
                        equipped ? "&b현재 칭호 (클릭해서 해제)" : "&e▶ 클릭해서 장착"), e -> { equip(d, 3, idx); new AchGui(p).open(p); });
            }
            fill(27, 53);
        }
    }

    /** 업적용 처치 기록 */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent e) {
        Player k = e.getEntity().getKiller();
        if (k == null) return;
        LivingEntity ent = e.getEntity();
        PlayerData d = plugin.data().get(k);
        if (ent instanceof Enemy) d.counters.merge("ach_kills", 1.0, Double::sum);
        MonsterTierManager.Tier t = plugin.tiers().tier(ent);
        if (t == MonsterTierManager.Tier.ELITE) d.counters.merge("ach_elite", 1.0, Double::sum);
        if (t == MonsterTierManager.Tier.MINIBOSS) d.counters.merge("ach_mini", 1.0, Double::sum);
        if (ent.getPersistentDataContainer().has(Keys.BOSS, PersistentDataType.STRING)) d.counters.merge("ach_boss", 1.0, Double::sum);
        // 발록의 봉인석 (Lv.120 이상 몬스터, 원혼의 부적보다 더 희귀)
        var ls = plugin.mobs().peek(ent);
        if (ls != null && ls.level >= plugin.getConfig().getInt("spirit-summon.balrog-min-level", 120)
                && ThreadLocalRandom.current().nextDouble() < plugin.getConfig().getDouble("spirit-summon.balrog-chance", 0.00005)) {
            e.getDrops().add(plugin.items().create("balrog_seal", 1));
            Text.announce(Text.PREFIX + Text.c("&4&l" + Text.name(k) + "&f님이 &4발록의 봉인석&f을 얻었습니다!"));
        }
        // 원혼의 부적 (극악의 확률)
        if (ThreadLocalRandom.current().nextDouble() < plugin.getConfig().getDouble("spirit-summon.drop-chance", 0.0008)) {
            e.getDrops().add(plugin.items().create("spirit_summon", 1));
            Text.announce(Text.PREFIX + Text.c("&5&l" + Text.name(k) + "&f님이 &5원혼의 부적&f을 얻었습니다!"));
        }
        // 보물 지도 드롭
        var tc = plugin.getConfig();
        double mapChance = t == MonsterTierManager.Tier.MINIBOSS ? tc.getDouble("treasure.map-miniboss", 0.12)
                : t == MonsterTierManager.Tier.ELITE ? tc.getDouble("treasure.map-elite", 0.025) : tc.getDouble("treasure.map-normal", 0.001);
        if (ThreadLocalRandom.current().nextDouble() < mapChance) e.getDrops().add(plugin.items().create("treasure_map", 1));
        // 현상수배범
        if (ent.getPersistentDataContainer().has(BOUNTY_KEY, PersistentDataType.BYTE)) bountyKilled(k, ent);
    }

    // =================================================================== 3. 현상수배
    private UUID bounty;
    private long bountyUntil;

    /** /현상수배 : 현재 현상수배범 위치 */
    public void tellBounty(Player p) {
        Location l = bountyLocation();
        if (l == null) { Text.msg(p, "&7지금은 현상수배범이 없습니다."); return; }
        String dist = l.getWorld().equals(p.getWorld()) ? " &7(" + (int) l.distance(p.getLocation()) + "m)" : "";
        Text.msg(p, "&4⚔ 현상수배범 위치: &e" + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ() + dist + " &7(나침반 ⚔)");
    }

    public Location bountyLocation() {
        if (bounty == null || System.currentTimeMillis() > bountyUntil) return null;
        Entity e = Bukkit.getEntity(bounty);
        return e == null || !e.isValid() ? null : e.getLocation();
    }

    public void spawnBounty() {
        if (!plugin.getConfig().getBoolean("bounty.enabled", true) || Bukkit.getOnlinePlayers().isEmpty()) return;
        Location old = bountyLocation();
        if (old != null) return;
        World w = Bukkit.getWorlds().get(0);
        int lv = 0;
        for (Player p : Bukkit.getOnlinePlayers()) lv += plugin.data().get(p).level;
        lv = Math.max(10, lv / Bukkit.getOnlinePlayers().size() + 5);
        List<CustomMobManager.MobDef> pool = new ArrayList<>();
        for (CustomMobManager.MobDef d : plugin.customMobs().defs())
            if (d.type != EntityType.PHANTOM && d.type != EntityType.VEX && d.type != EntityType.ENDERMITE && Math.abs((d.minLevel + d.maxLevel) / 2 - lv) < 35) pool.add(d);
        if (pool.isEmpty()) return;
        for (int tries = 0; tries < 20; tries++) {
            double a = rnd.nextDouble() * Math.PI * 2, r = 250 + rnd.nextDouble() * 900;
            Block top = kr.rpgcraft.util.Locs.surface(w, w.getSpawnLocation().clone().add(Math.cos(a) * r, 0, Math.sin(a) * r));
            if (top.isLiquid()) continue;
            CustomMobManager.MobDef d = pool.get(rnd.nextInt(pool.size()));
            LivingEntity m = plugin.customMobs().spawn(d, top.getLocation().add(0.5, 1, 0.5), lv);
            if (m == null) return;
            MobManager.MobState s = plugin.mobs().peek(m);
            plugin.tiers().apply(m, s, MonsterTierManager.Tier.MINIBOSS);
            s.maxHp *= 1.8;
            s.hp = s.maxHp;
            s.baseName = "&4&l[현상수배] &c" + d.name;
            plugin.mobs().updateName(m, s);
            if (m instanceof Mob mob) mob.setRemoveWhenFarAway(false);
            m.getPersistentDataContainer().set(BOUNTY_KEY, PersistentDataType.BYTE, (byte) 1);
            bounty = m.getUniqueId();
            bountyUntil = System.currentTimeMillis() + plugin.getConfig().getLong("bounty.stay-minutes", 30) * 60_000;
            long prize = prize(lv);
            Text.announce(Text.PREFIX + Text.c("&4&l⚔ 현상수배! &c" + d.name + " &7Lv." + s.level + " &f· 현상금 &e" + Text.money(prize)
                    + " &f· 위치 &e" + top.getX() + ", " + top.getZ()));
            for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 0.7f, 0.8f);
            UUID id = m.getUniqueId();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Entity e = Bukkit.getEntity(id);
                if (e != null && e.isValid()) { e.remove(); Text.announce(Text.PREFIX + Text.c("&7현상수배범이 자취를 감췄습니다...")); }
            }, plugin.getConfig().getLong("bounty.stay-minutes", 30) * 60 * 20);
            return;
        }
    }

    private long prize(int lv) {
        return (long) (plugin.getConfig().getLong("bounty.prize-base", 800) * (1 + lv / 10.0));
    }

    private void bountyKilled(Player k, LivingEntity ent) {
        MobManager.MobState s = plugin.mobs().peek(ent);
        int lv = s == null ? 30 : s.level;
        long money = prize(lv);
        plugin.economy().give(k, money);
        give(k, lv >= 90 ? "crystal_top" : lv >= 60 ? "crystal_high" : lv >= 30 ? "crystal_mid" : "crystal_low", 3);
        if (ThreadLocalRandom.current().nextDouble() < plugin.getConfig().getDouble("treasure.map-bounty", 0.5)) give(k, "treasure_map", 1);
        if (rnd.nextDouble() < 0.3) give(k, lv >= 60 ? "rune_mid" : "rune_low", 1);
        plugin.data().get(k).counters.merge("ach_bounty", 1.0, Double::sum);
        bounty = null;
        Text.announce(Text.PREFIX + Text.c("&e" + Text.name(k) + "&f님이 현상수배범을 처치하고 현상금 &e" + Text.money(money) + "&f을(를) 받았습니다!"));
    }

    // =================================================================== 4. 보물 지도
    @EventHandler(priority = EventPriority.HIGH)
    public void onBossChest(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)) return;
        ItemStack it = e.getItem();
        if (!"boss_chest".equals(ItemData.id(it))) return;
        e.setCancelled(true);
        String bid = it.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(plugin, "boss_chest"), PersistentDataType.STRING);
        Player p = e.getPlayer();
        java.util.List<ItemStack> pool = plugin.bosses().chestPreview(bid);
        ItemStack result = plugin.bosses().pickChest(bid);
        if (pool.isEmpty() || result == null) { Text.msg(p, "&7상자가 비어 있었습니다..."); it.setAmount(it.getAmount() - 1); return; }
        it.setAmount(it.getAmount() - 1);
        openChestRoulette(p, pool, result);
    }

    /** 보스 수정 우클릭: 확률에 따라 그 보스의 장비 (쉬프트: 한 번에 모두) */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBossCrystal(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)) return;
        ItemStack it = e.getItem();
        if (!"boss_crystal".equals(ItemData.id(it))) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        var d = plugin.data().get(p);
        if (d.onCooldown("boss_crystal")) return;
        d.cooldown("boss_crystal", 400);
        String bid = plugin.bosses().crystalBoss(it);
        if (bid == null || plugin.bosses().gearDrops(bid).isEmpty()) { Text.msg(p, "&7힘을 잃은 수정입니다."); it.setAmount(it.getAmount() - 1); return; }
        int n = p.isSneaking() ? it.getAmount() : 1;
        it.setAmount(it.getAmount() - n);
        int got = 0;
        for (int i = 0; i < n; i++) got += plugin.bosses().openCrystal(p, bid).size();
        p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1.2, 0), 20, 0.4, 0.4, 0.4, 0.08);
        if (got > 0) {
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
            Text.msg(p, "&d수정 " + n + "개에서 장비 &f" + got + "개&d를 얻었습니다!");
        } else {
            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1f, 0.8f);
            Text.msg(p, "&7수정 " + n + "개가 빛을 잃고 부서졌습니다... &8(장비 없음)");
        }
    }

    /** 가운데 칸에서 아이템이 돌다가 점점 느려지며 결과에서 멈춤, 위쪽 줄엔 뽑힐 수 있는 아이템과 확률 */
    private void openChestRoulette(Player p, java.util.List<ItemStack> pool, ItemStack result) {
        kr.rpgcraft.gui.Gui g = new kr.rpgcraft.gui.Gui(4, "&6&l보스 상자") {
        };
        for (int i = 0; i < pool.size() && i < 9; i++) g.set(i, pool.get(i), null);
        g.set(31, kr.rpgcraft.gui.Gui.button(Material.GOLD_NUGGET, "&e뽑는 중..."), null);
        g.fill(0, 35);
        g.open(p);
        int[] delays = {1, 1, 1, 1, 1, 2, 2, 2, 3, 3, 4, 5, 6, 8, 10};
        long t = 0;
        for (int k = 0; k < delays.length; k++) {
            t += delays[k];
            final boolean last = k == delays.length - 1;
            final int kk = k;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline()) return;
                ItemStack show = last ? result.clone() : pool.get((kk * 3 + 1) % pool.size()).clone();
                g.getInventory().setItem(22, show);
                p.playSound(p.getLocation(), last ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.BLOCK_NOTE_BLOCK_HAT, 0.7f, last ? 1.2f : 1.6f);
                if (last) {
                    for (ItemStack l : p.getInventory().addItem(result.clone()).values()) p.getWorld().dropItemNaturally(p.getLocation(), l);
                    Text.msg(p, "&6보스 상자: &f" + (result.hasItemMeta() && result.getItemMeta().hasDisplayName() ? result.getItemMeta().getDisplayName() : result.getType().name()) + " &7x" + result.getAmount());
                }
            }, t);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSummonSpirit(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)) return;
        ItemStack it = e.getItem();
        String sid = ItemData.id(it);
        String bossId = "spirit_summon".equals(sid) ? "vengeful_spirit" : "balrog_seal".equals(sid) ? "balrog" : null;
        if (bossId == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        double safe = plugin.getConfig().getDouble("summon.spawn-safe-radius", 300);   // 스폰 근처에서는 보스 소환 금지 (v5.4.5)
        Location sp = p.getWorld().getSpawnLocation();
        double dist = Math.hypot(p.getLocation().getX() - sp.getX(), p.getLocation().getZ() - sp.getZ());
        if (safe > 0 && dist < safe) {
            Text.msg(p, "&c스폰 " + (int) safe + "칸 안에서는 소환할 수 없습니다. &7(지금 스폰에서 " + (int) dist + "칸)");
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.7f);
            return;
        }
        if (bossId.equals("vengeful_spirit")) {   // v5.5.0: 원혼은 한 번에 하나 — 쓰러뜨리기 전에는 다시 소환 불가
            Location alive = plugin.worldBoss().aliveAt("vengeful_spirit");
            if (alive != null) {
                Text.msg(p, "&c원혼이 이미 깨어나 있습니다. 쓰러뜨린 뒤에 다시 소환할 수 있습니다. &7(위치 " + alive.getBlockX() + ", " + alive.getBlockY() + ", " + alive.getBlockZ() + ")");
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.7f);
                return;
            }
        }
        if (plugin.altar().building(bossId)) {   // v5.6.0: 제단이 올라가는 중이면 한 번 더 못 씀
            Text.msg(p, "&c이미 소환 제단이 올라가고 있습니다.");
            return;
        }
        Location at = p.getLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply("spirit_summon".equals(sid) ? 12 : 8));
        at.setY(p.getWorld().getHighestBlockYAt(at, org.bukkit.HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1);
        ItemStack one = it.clone();
        one.setAmount(1);
        it.setAmount(it.getAmount() - 1);   // 제단을 세우는 순간 소모 (실패하면 돌려줌)
        Location ground = at.getBlock().getLocation();
        Text.announce(Text.PREFIX + Text.c("&e" + p.getName() + "&f님이 " + (bossId.equals("balrog") ? "&6&l발록" : "&b&l몬스터의 원혼") + "&f을(를) 부르는 제단을 세웁니다... &7(10초)"));
        plugin.altar().raise(bossId, ground, p, () -> bossId.equals("vengeful_spirit")
                        ? plugin.worldBoss().start("vengeful_spirit", ground)   // 원혼은 월드보스: 저주받은 묘역 전장과 함께 등장
                        : plugin.bosses().spawn(bossId, ground.clone().add(0.5, 0, 0.5)) != null,
                () -> {
                    Text.msg(p, "&c소환에 실패해 아이템을 돌려드렸습니다.");
                    if (p.isOnline()) for (ItemStack l : p.getInventory().addItem(one).values()) p.getWorld().dropItemNaturally(p.getLocation(), l);
                });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMap(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)) return;
        ItemStack it = e.getItem();
        if (!"treasure_map".equals(ItemData.id(it))) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        ItemMeta m = it.getItemMeta();
        String tgt = m.getPersistentDataContainer().get(MAP_KEY, PersistentDataType.STRING);
        if (tgt == null) {
            if (it.getAmount() > 1) { Text.msg(p, "&c보물 지도는 한 장씩 들고 해독하세요."); return; }
            World w = p.getWorld();
            double a = rnd.nextDouble() * Math.PI * 2, r = 150 + rnd.nextDouble() * 450;
            int x = (int) (p.getLocation().getX() + Math.cos(a) * r), z = (int) (p.getLocation().getZ() + Math.sin(a) * r);
            m.getPersistentDataContainer().set(MAP_KEY, PersistentDataType.STRING, w.getName() + "," + x + "," + z);
            List<String> lore = m.getLore() == null ? new ArrayList<>() : new ArrayList<>(m.getLore());
            lore.add(Text.c("&e해독됨: 나침반의 ✚ 방향으로 약 " + (int) r + "블록"));
            m.setLore(lore);
            it.setItemMeta(m);
            p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 0.8f);
            Text.msg(p, "&e보물 지도를 해독했습니다! 지도를 들고 있으면 거리가 표시되고, 나침반에 ✚ 로 방향이 나옵니다.");
        } else Text.actionBar(p, "&e지도를 들고 표시된 곳으로 가세요.");
    }

    public Location mapTarget(Player p) {
        ItemStack it = p.getInventory().getItemInMainHand();
        if (!"treasure_map".equals(ItemData.id(it)) || !it.hasItemMeta()) return null;
        String tgt = it.getItemMeta().getPersistentDataContainer().get(MAP_KEY, PersistentDataType.STRING);
        if (tgt == null) return null;
        String[] v = tgt.split(",");
        World w = Bukkit.getWorld(v[0]);
        if (w == null || !w.equals(p.getWorld())) return null;
        return new Location(w, Integer.parseInt(v[1]) + 0.5, p.getLocation().getY(), Integer.parseInt(v[2]) + 0.5);
    }

    private void mapTick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Location t = mapTarget(p);
            if (t == null) continue;
            double dist = Math.hypot(t.getX() - p.getLocation().getX(), t.getZ() - p.getLocation().getZ());
            if (dist > 5) { Text.actionBar(p, "&e✚ 보물까지 약 " + (int) dist + "블록"); continue; }
            // 도착: 보물 상자 + 수호자
            ItemStack it = p.getInventory().getItemInMainHand();
            it.setAmount(it.getAmount() - 1);
            Block top = kr.rpgcraft.util.Locs.surface(p.getWorld(), t).getRelative(0, 1, 0);
            top.setType(Material.CHEST, false);
            int lv = plugin.mobs().computeLevel(top.getLocation());
            if (top.getState() instanceof Chest c) {   // 내용물은 넣지 않고 등급만 → 여는 사람마다 개인 전리품
                c.getPersistentDataContainer().set(new NamespacedKey("rpgcraft", "loot_tier"), PersistentDataType.INTEGER, lv >= 60 ? 3 : lv >= 30 ? 2 : 1);
                c.update(true, false);
            }
            plugin.economy().give(p, (long) (5000 * (1 + lv / 10.0) * plugin.getConfig().getDouble("economy.boss-money-mult", 0.1)));
            CustomMobManager.MobDef guard = plugin.customMobs().def("treasure_guardian");
            for (int i = 0; i < 2 && guard != null; i++) plugin.customMobs().spawn(guard, top.getLocation().add(2 - i * 4, 0, 1), Math.max(1, lv));
            Bukkit.getScheduler().runTaskLater(plugin, () -> top.setType(Material.AIR, false),
                    20L * plugin.getConfig().getLong("treasure.chest-seconds", 180));   // 3분 뒤 사라짐
            Fx.helix(plugin, p, 2, 0.8, 16, Color.fromRGB(0xFFD23F), Color.fromRGB(0xFFFFFF));
            p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 1f, 0.8f);
            plugin.data().get(p).counters.merge("ach_treasure", 1.0, Double::sum);
            Text.msg(p, "&6&l보물을 찾았습니다! &7수호자를 조심하세요!");
        }
    }
}
