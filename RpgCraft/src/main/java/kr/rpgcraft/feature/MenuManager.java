package kr.rpgcraft.feature;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.boss.BossDefinition;
import kr.rpgcraft.data.PlayerData;
import kr.rpgcraft.data.Setting;
import kr.rpgcraft.economy.ShopManager;
import kr.rpgcraft.gui.Gui;
import kr.rpgcraft.guild.Guild;
import kr.rpgcraft.item.Category;
import kr.rpgcraft.item.ItemTemplate;
import kr.rpgcraft.passive.Passive;
import kr.rpgcraft.stat.Power;
import kr.rpgcraft.stat.StatSnapshot;
import kr.rpgcraft.util.ItemBuilder;
import kr.rpgcraft.util.Locs;
import kr.rpgcraft.util.Text;
import kr.rpgcraft.war.Castle;
import kr.rpgcraft.war.WarManager;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Predicate;

/**
 * 메인 메뉴 (쉬프트 + F 또는 /메뉴) 와 모든 하위 메뉴.
 * 프로필 · 스탯 · 스킬 · 강화 · 룬 · 포션가방 · 기운 · 대장장이 · 일일 의뢰 · 상점 · 워프 · 길드 · 공성전 · 랭킹 · 도감 · 설정 · 도움말
 */
public class MenuManager implements Listener {
    public record Warp(String id, String name, Material icon, Location loc, int minLevel) {}

    public record Rank(String name, int level, double exp, long money, long power) {}

    private final RpgCraft plugin;
    private final File warpFile;
    private final Map<String, Warp> warps = new LinkedHashMap<>();
    private final Set<UUID> channeling = new HashSet<>();
    private volatile List<Rank> ranks = List.of();
    private long ranksBuilt;

    public MenuManager(RpgCraft plugin) {
        this.plugin = plugin;
        this.warpFile = new File(plugin.getDataFolder(), "warps.yml");
        loadWarps();
        Bukkit.getScheduler().runTaskTimer(plugin, this::rebuildRanks, 100L, 6000L);
    }

    // =================================================================== 공통
    private static final Material BORDER = Material.BLACK_STAINED_GLASS_PANE;

    private static void border(Gui g, int rows) {
        ItemStack pane = kr.rpgcraft.pack.PackManager.filler(BORDER);
        int size = rows * 9;
        for (int i = 0; i < size; i++) {
            boolean edge = i < 9 || i >= size - 9 || i % 9 == 0 || i % 9 == 8;
            if (edge && g.getInventory().getItem(i) == null) g.getInventory().setItem(i, pane);
        }
    }

    private ItemStack head(Player p, String name, List<String> lore) {
        ItemStack it = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta m = (SkullMeta) it.getItemMeta();
        m.setOwningPlayer(p);
        m.setDisplayName(Text.c(name));
        m.setLore(Text.c(lore));
        it.setItemMeta(m);
        return it;
    }

    private ItemStack headOf(UUID id, String name, List<String> lore) {
        ItemStack it = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta m = (SkullMeta) it.getItemMeta();
        m.setOwningPlayer(Bukkit.getOfflinePlayer(id));
        m.setDisplayName(Text.c(name));
        m.setLore(Text.c(lore));
        it.setItemMeta(m);
        return it;
    }

    private static ItemStack icon(Material m, String name, List<String> lore) {
        return new ItemBuilder(m).name(name).lore(lore).hideAll().build();
    }

    private void back(Gui g, int slot, Player p) {
        g.set(slot, Gui.button(Material.ARROW, "&f◀ 메인 메뉴"), e -> open(p));
    }

    private static String bar(double ratio, int len) {
        ratio = Math.max(0, Math.min(1, ratio));
        int full = (int) Math.round(ratio * len);
        return "&a" + "▮".repeat(full) + "&8" + "▮".repeat(len - full);
    }

    // =================================================================== 입력
    /** 쉬프트 + Q = 퀵 액티브 스킬 */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        PlayerData d = plugin.data().get(p);
        var dt = kr.rpgcraft.item.ItemData.template(e.getItemDrop().getItemStack());
        boolean equip = dt != null && (dt.category.isEquipment() || dt.category == kr.rpgcraft.item.Category.BOW);
        int qm = quickMode(d);
        boolean quick = ((qm == 0 && p.isSneaking()) || (qm == 400 && !p.isSneaking())) && plugin.getConfig().getBoolean("keys.sneak-drop-quick-skill", true);
        if (equip && !p.isSneaking() && qm != 400 && plugin.jobs().castJobSkill(p)) { e.setCancelled(true); return; }   // Q: 직업 스킬
        if (equip) {   // 장비는 버릴 수 없음 (/쓰레기통 이용)
            e.setCancelled(true);
            if (!quick) { kr.rpgcraft.util.Text.actionBar(p, "&c장비는 버릴 수 없습니다 &7(/쓰레기통)"); return; }
        }
        if (!quick) return;
        if (d.quickSkill == null) return;
        e.setCancelled(true);
        plugin.passives().useQuick(p);
    }

    /** 퀵 스킬 키 코드 해석 */
    public static int parseQuickKey(String k) {
        k = k.replace("+", "").replace(" ", "").toLowerCase();
        boolean shift = k.startsWith("쉬프트") || k.startsWith("shift");
        String key = k.replace("쉬프트", "").replace("shift", "");
        if (key.equals("q")) return shift ? 0 : 400;
        if (key.equals("f")) return shift ? 301 : 300;
        if (key.length() == 1 && key.charAt(0) >= '1' && key.charAt(0) <= '9') return (shift ? 100 : 200) + (key.charAt(0) - '0');
        return -1;
    }

    public static String quickKeyName(int m) {
        if (m == 1) m = 109;          // 예전 설정 호환
        if (m == 2) m = 209;
        return switch (m) {
            case 0 -> "쉬프트+Q";
            case 400 -> "Q (손에 아이템이 있을 때)";
            case 300 -> "F";
            case 301 -> "쉬프트+F";
            default -> m > 200 ? String.valueOf(m - 200) : m > 100 ? "쉬프트+" + (m - 100) : "쉬프트+Q";
        };
    }

    public static int quickMode(PlayerData d) {
        int m = (int) d.counter("quick_key");
        return m == 1 ? 109 : m == 2 ? 209 : m;
    }

    /** 숫자 키 퀵 스킬 (빈손이어도 동작) */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onQuickSlot(org.bukkit.event.player.PlayerItemHeldEvent e) {
        Player p = e.getPlayer();
        PlayerData d = plugin.data().get(p);
        int mode = quickMode(d), n = e.getNewSlot() + 1;
        if (d.quickSkill == null) return;
        if (mode == 200 + n || (mode == 100 + n && p.isSneaking())) {
            e.setCancelled(true);
            plugin.passives().useQuick(p);
        }
    }

    // =================================================================== 메인 메뉴
    public void open(Player p) {
        plugin.data().get(p).counters.put("guide_menu", 1.0);
        new MainMenu(p).open(p);
        if (Setting.SOUND.get(plugin.data().get(p))) p.playSound(p.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.5f, 1.4f);
    }

    private class MainMenu extends Gui {
        MainMenu(Player p) {
            super(6, "&8✦ &6&lRpgCraft &8✦ 메뉴", "main");
            PlayerData d = plugin.data().get(p);
            StatSnapshot s = d.stats;
            plugin.quests().ensure(d);
            Guild g = plugin.guilds().of(p.getUniqueId());
            double need = plugin.levels().need(d.level);
            double ratio = d.level >= plugin.levels().maxLevel(d) ? 1 : d.exp / need;

            set(4, head(p, "&e&l" + Text.name(p) + " &7Lv." + d.level, List.of(
                    "&8" + plugin.rounds().round() + "회차 · " + (d.blacksmith ? "대장장이" : "모험가"),
                    "",
                    "&f경험치 " + bar(ratio, 20) + " &7" + String.format("%.1f", ratio * 100) + "%",
                    "&f전투력 &6&l" + Text.num(Power.of(s)),
                    "&f소지금 &e" + Text.money(d.money),
                    "&f길드 &b" + (g == null ? "없음" : g.name),
                    "",
                    "&c❤ " + Text.num(d.hp) + "/" + Text.num(s.maxHp) + "  &6⚔ " + Text.num(s.attack),
                    "&e✦ 크리 " + String.format("%.1f", s.crit) + "%  &b🛡 방어 " + String.format("%.1f", s.def) + "%",
                    "&c힘 " + (int) s.str + "  &a민첩 " + (int) s.dex + "  &b모험 " + (int) s.adv)));

            ItemStack stat = icon(Material.NETHER_STAR, "&e&l스탯", List.of("&7힘 / 민첩 / 모험 분배", "",
                    d.statPoints > 0 ? "&a남은 포인트 " + d.statPoints + " !" : "&7남은 포인트 0", "&e▶ 클릭"));
            stat.setAmount(Math.max(1, Math.min(64, d.statPoints)));
            set(19, stat, e -> p.performCommand("stat"));
            set(20, icon(Material.ENCHANTED_BOOK, "&d&l특수 스킬", List.of("&7히든/던전/??? 패시브와 액티브", "&7보유 " + d.passives.size() + "개",
                    "&7퀵 스킬: &f" + quickName(d), "&e▶ 클릭")), e -> plugin.passives().open(p));
            set(21, icon(Material.ANVIL, "&6&l강화", List.of("&7+8부터 파괴 위험", "&7파괴 방지권 / 확률 증가권 사용 가능", "&e▶ 클릭")), e -> plugin.enhance().open(p));
            set(22, icon(Material.FIREWORK_STAR, "&5&l룬", List.of("&e▶ 좌클릭: 룬 장착", "&e▶ 우클릭: 룬 합성")), e -> {
                if (e.isRightClick()) plugin.runeFusion().open(p);
                else plugin.runes().open(p);
            });
            int potions = d.potionBag.values().stream().mapToInt(Integer::intValue).sum();
            set(23, icon(Material.POTION, "&c&l포션가방", List.of("&7보관 중 " + potions + "개", "&7F 키로 즉시 사용", "&e▶ 클릭")), e -> plugin.potions().open(p));
            set(24, icon(Material.AMETHYST_CLUSTER, "&d&l기운 조합", List.of("&7기운 파편 → 결정 → 기운", "&7사신수 무기 · 사흉수 갑주", "&e▶ 클릭")), e -> plugin.spirits().open(p));
            if (d.blacksmith) set(25, icon(Material.SMITHING_TABLE, "&6&l대장장이 제작", List.of("&7유니크 무기/방어구 제작", "&e▶ 클릭")), e -> plugin.blacksmith().open(p));
            else set(25, icon(Material.SMITHING_TABLE, "&7&l대장장이 전직", List.of("&7레벨 " + plugin.getConfig().getInt("blacksmith.required-level", 20)
                    + " · " + Text.money(plugin.getConfig().getLong("blacksmith.cost", 1_000_000)), "&7유니크 장비 제작 · 강화 확률 +5%", "&e▶ 쉬프트 클릭으로 전직")), e -> {
                if (e.isShiftClick()) {
                    plugin.blacksmith().changeJob(p);
                    MenuManager.this.open(p);
                }
            });

            int done = plugin.quests().completedCount(d);
            ItemStack quest = icon(Material.WRITABLE_BOOK, "&a&l일일 의뢰", List.of("&7매일 자정(한국 시간) 초기화", "&f진행 " + done + "/3", "&e▶ 클릭"));
            if (done > 0) quest.setAmount(done);
            set(28, quest, e -> new QuestGui(p).open(p));
            set(29, icon(Material.EMERALD, "&a&l상점", List.of("&7이용 가능한 상점 목록", "&e▶ 클릭")), e -> new ShopListGui(p).open(p));
            set(30, icon(Material.ENDER_PEARL, "&3&l워프", List.of("&7" + warps.size() + "개 지역", "&73초 시전 후 이동 (움직이거나 피격 시 취소)", "&e▶ 클릭")), e -> new WarpGui(p).open(p));
            set(31, icon(Material.WHITE_BANNER, "&b&l길드", List.of(g == null ? "&7소속 길드 없음" : "&f" + g.name + " &7Lv." + g.level + " · " + g.members.size() + "명", "&e▶ 클릭")),
                    e -> new GuildGui(p).open(p));
            set(32, icon(Material.NETHER_STAR, "&d&l잠재능력", List.of("&e▶ 클릭")), e -> plugin.potentials().open(p));   // 공성전은 길드 메뉴에서
            set(33, icon(Material.GOLDEN_HELMET, "&6&l랭킹", List.of("&7레벨 · 전투력 · 재산 · 길드", "&e▶ 클릭")), e -> new RankGui(p, 0).open(p));
            set(34, icon(Material.BOOKSHELF, "&f&l도감", List.of("&7모든 아이템과 보스 정보", "&7획득처 · 드롭 확률", "&e▶ 클릭")), e -> new CodexGui(p).open(p));

            var cm = plugin.content();
            set(37, icon(Material.NAME_TAG, "&b&l업적 · 칭호", List.of("&7목표를 달성하고 칭호를 얻으세요", "&7현재 칭호: &f" + (cm.title(d).isEmpty() ? "없음" : cm.title(d)), "&e▶ 클릭")),
                    e -> cm.new AchGui(p).open(p));
            set(38, icon(cm.canClaim(d) ? Material.CHEST_MINECART : Material.MINECART, cm.canClaim(d) ? "&a&l출석 보상 &e(받기 가능!)" : "&7&l출석 보상",
                    List.of("&7연속 " + cm.streak(d) + "일", "&e▶ 클릭")), e -> cm.new AttendGui(p).open(p));
            set(42, icon(Material.HEART_OF_THE_SEA, "&6&l장신구", List.of("&7반지 · 목걸이 · 귀걸이 장착", "&7무작위 옵션으로 추가 스펙업", "&e▶ 클릭")),
                    e -> plugin.accessories().open(p));
            set(43, icon(Material.SUNFLOWER, "&6&l행운의 룰렛", List.of("&7게임 머니로 배율에 도전!", "&7×0 ~ ×10", "&e▶ 클릭")), e -> plugin.casino().open(p));
            set(39, icon(Material.COMPARATOR, "&7&l설정", List.of("&7사이드바 · HUD · 대미지 표시 · 효과음", "&e▶ 클릭")), e -> new SettingsGui(p).open(p));
            set(40, icon(Material.IRON_SWORD, "&6&l직업", List.of("&7현재: &f" + plugin.jobs().title(d), "&7Lv.10 기초 직업 · Lv.40 전직", "&e▶ 클릭")), e -> plugin.jobs().open(p));
            set(41, icon(Material.OAK_SIGN, "&f&l도움말", List.of("&7조작키와 시스템 안내", "&e▶ 클릭")), e -> new HelpGui(p).open(p));
            set(49, Gui.button(Material.BARRIER, "&c닫기"), e -> p.closeInventory());
            border(this, 6);
            // UI 설명 제거: 값(숫자)이나 ▶ 가 있는 줄만 남긴다
            for (int i = 0; i < 54; i++) {
                ItemStack it = inv.getItem(i);
                if (it == null || !it.hasItemMeta() || !it.getItemMeta().hasLore()) continue;
                var m = it.getItemMeta();
                List<String> keep = new ArrayList<>();
                for (String l : m.getLore()) if (l.matches(".*[0-9▶].*")) keep.add(l);
                m.setLore(keep);
                it.setItemMeta(m);
            }
            fill(0, 53);
        }
    }

    private String quickName(PlayerData d) {
        Passive ps = d.quickSkill == null ? null : Passive.find(d.quickSkill);
        return ps == null ? "없음" : ps.label;
    }

    // =================================================================== 일일 의뢰
    private class QuestGui extends Gui {
        QuestGui(Player p) {
            super(3, "&8일일 의뢰", "quest");
            PlayerData d = plugin.data().get(p);
            QuestManager qm = plugin.quests();
            for (int i = 0; i < 3; i++) {
                int slot = i;
                QuestManager.Quest q = qm.quest(d, i);
                int prog = Math.max(0, qm.progress(d, i));
                boolean done = qm.done(d, i), claimed = qm.claimed(d, i);
                ItemStack it = icon(claimed ? Material.LIME_STAINED_GLASS_PANE : q.icon(),
                        (claimed ? "&7&m" : done ? "&a&l" : "&e&l") + String.format(q.label(), q.amount()),
                        List.of("&f진행 " + bar(prog / (double) q.amount(), 16) + " &7" + prog + "/" + q.amount(), "",
                                "&f보상: &e" + Text.money(qm.moneyReward(d, i)) + " &f+ 경험치 &a" + Text.num(qm.expReward(d)), "",
                                claimed ? "&7수령 완료" : done ? "&a▶ 클릭하여 보상 수령" : "&7진행 중..."));
                set(11 + i * 2, it, e -> {
                    qm.claim(p, slot);
                    new QuestGui(p).open(p);
                });
            }
            set(4, Gui.button(Material.CHEST, "&6올클리어 보상", "&73개 모두 수령 시 결정 3개 (+10% 확률로 강화 확률 증가권)",
                    d.counter("dq_bonus") > 0 ? "&a수령 완료" : "&7미수령"));
            back(this, 18, p);
            fill(0, 26);
        }
    }

    // =================================================================== 상점 목록
    private class ShopListGui extends Gui {
        ShopListGui(Player p) { this(p, 0); }

        /** 한 쪽에 28개 (상점이 많아져 페이지로 나눔 — 이전엔 14개까지만 보였음) */
        ShopListGui(Player p, int page) {
            super(6, "&8상점 목록" + (page > 0 ? " (" + (page + 1) + ")" : ""));
            boolean admin = p.hasPermission("rpgcraft.admin");
            List<ShopManager.Shop> list = new ArrayList<>();
            for (ShopManager.Shop s : plugin.shops().all()) if (!s.id.equals("hidden")) list.add(s);   // 히든 상인에게서만
            int per = 28, pages = Math.max(1, (list.size() + per - 1) / per);
            int slot = 10;
            for (int i = page * per; i < list.size() && i < (page + 1) * per; i++) {
                ShopManager.Shop s = list.get(i);
                boolean ok = s.command || admin;
                Material m = s.id.contains("weapon") || s.id.startsWith("armory") ? Material.IRON_SWORD : s.id.equals("fish") ? Material.COD : s.id.contains("armor") ? Material.IRON_CHESTPLATE
                        : s.id.contains("war") ? Material.TNT : s.id.contains("wander") ? Material.LEAD : s.id.contains("special") ? Material.AMETHYST_SHARD : Material.EMERALD;
                set(slot, icon(ok ? m : Material.GRAY_DYE, (ok ? "&a" : "&8") + Text.strip(s.title),
                        List.of("&7상품 " + s.entries.size() + "종" + (s.multiplier != 1 ? " &c(가격 x" + s.multiplier + ")" : ""),
                                ok ? "&e▶ 클릭하여 열기" : "&cNPC 를 찾아가야 이용할 수 있습니다")), e -> {
                    if (ok) plugin.shops().open(p, s.id, 0);
                });
                slot++;
                if (slot % 9 == 8) slot += 2;
            }
            if (page > 0) set(45, icon(Material.ARROW, "&f이전 페이지", List.of()), e -> new ShopListGui(p, page - 1).open(p));
            if (page + 1 < pages) set(53, icon(Material.ARROW, "&f다음 페이지", List.of()), e -> new ShopListGui(p, page + 1).open(p));
            back(this, 49, p);
            fill(0, 53);
        }
    }

    // =================================================================== 워프
    private void loadWarps() {
        warps.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(warpFile);
        for (String id : y.getKeys(false)) {
            ConfigurationSection s = y.getConfigurationSection(id);
            if (s == null) continue;
            Location l = Locs.parse(s.getString("loc"));
            if (l == null) continue;
            Material m = Material.matchMaterial(s.getString("icon", "ENDER_PEARL"));
            warps.put(id, new Warp(id, s.getString("name", id), m == null ? Material.ENDER_PEARL : m, l, s.getInt("min-level", 1)));
        }
    }

    public void reload() {
        loadWarps();
    }

    private void saveWarps() {
        YamlConfiguration y = new YamlConfiguration();
        for (Warp w : warps.values()) {
            y.set(w.id() + ".name", w.name());
            y.set(w.id() + ".icon", w.icon().name());
            y.set(w.id() + ".loc", Locs.full(w.loc()));
            y.set(w.id() + ".min-level", w.minLevel());
        }
        try {
            y.save(warpFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("warps.yml 저장 실패: " + ex.getMessage());
        }
    }

    public void setWarp(String id, String name, Material icon, Location loc, int minLevel) {
        warps.put(id, new Warp(id, name, icon, loc.clone(), minLevel));
        saveWarps();
    }

    public boolean deleteWarp(String id) {
        boolean r = warps.remove(id) != null;
        saveWarps();
        return r;
    }

    public Collection<Warp> warps() {
        return warps.values();
    }

    public void warp(Player p, Warp w) {
        PlayerData d = plugin.data().get(p);
        if (d.level < w.minLevel()) { Text.msg(p, "&c레벨 " + w.minLevel() + " 이상만 이동할 수 있습니다."); return; }
        if (!channeling.add(p.getUniqueId())) return;
        p.closeInventory();
        Location start = p.getLocation();
        long startDamaged = d.lastDamaged;
        int ticks = plugin.getConfig().getInt("warp.channel-ticks", 60);
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead() || p.getLocation().distanceSquared(start) > 0.5 || d.lastDamaged != startDamaged) {
                    channeling.remove(p.getUniqueId());
                    if (p.isOnline()) Text.actionBar(p, "&c워프가 취소되었습니다.");
                    cancel();
                    return;
                }
                double prog = t / (double) ticks;
                Text.actionBar(p, "&3워프 " + w.name() + " " + bar(prog, 20) + " &f" + (int) (prog * 100) + "%");
                d.actionBarLock = System.currentTimeMillis() + 400;
                kr.rpgcraft.util.Fx.circle(p.getLocation().add(0, 0.1 + prog * 2, 0), 0.9, 16, Color.fromRGB(0x6BE3FF), 1.1f);
                p.getWorld().spawnParticle(Particle.PORTAL, p.getLocation().add(0, 1, 0), 10, 0.4, 0.8, 0.4, 0.3);
                if (t >= ticks) {
                    channeling.remove(p.getUniqueId());
                    p.teleport(w.loc());
                    p.playSound(w.loc(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.2f);
                    p.sendTitle(Text.c("&3" + w.name()), "", 5, 25, 5);
                    cancel();
                    return;
                }
                t += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private class WarpGui extends Gui {
        WarpGui(Player p) {
            super(4, "&8워프");
            int lv = plugin.data().get(p).level;
            int slot = 10;
            for (Warp w : warps.values()) {
                if (slot > 25) break;
                boolean ok = lv >= w.minLevel();
                set(slot, icon(ok ? w.icon() : Material.GRAY_DYE, (ok ? "&b&l" : "&8") + w.name(),
                        List.of("&7" + w.loc().getWorld().getName() + " " + w.loc().getBlockX() + ", " + w.loc().getBlockY() + ", " + w.loc().getBlockZ(),
                                "&7요구 레벨 " + w.minLevel(), "", ok ? "&e▶ 클릭하여 이동" : "&c레벨 부족")), e -> warp(p, w));
                slot++;
                if (slot % 9 == 8) slot += 2;
            }
            if (warps.isEmpty()) set(13, Gui.button(Material.MAP, "&7등록된 워프가 없습니다", "&7관리자: /rpg관리 warp set <id> <이름> [아이콘] [레벨]"));
            back(this, 27, p);
            fill(0, 35);
        }
    }

    // =================================================================== 길드
    private class GuildGui extends Gui {
        GuildGui(Player p) {
            super(6, "&8길드");
            var gm = plugin.guilds();
            Guild g = gm.of(p.getUniqueId());
            if (g == null) {
                Guild inv = gm.pendingInvite(p.getUniqueId());
                set(4, Gui.button(Material.WHITE_BANNER, "&7소속 길드 없음", "&7/길드 생성 <이름> 으로 창설",
                        "&7비용 " + Text.money(plugin.getConfig().getLong("guild.create-cost", 1_000_000))));
                if (inv != null) {
                    set(21, Gui.button(Material.LIME_WOOL, "&a" + inv.name + " 초대 수락"), e -> { p.performCommand("guild 수락"); new GuildGui(p).open(p); });
                    set(23, Gui.button(Material.RED_WOOL, "&c초대 거절"), e -> { p.performCommand("guild 거절"); new GuildGui(p).open(p); });
                }
                int slot = 28;
                for (Guild o : gm.all()) {
                    if (slot > 43) break;
                    set(slot, Gui.button(Material.BLUE_BANNER, "&b" + o.name, "&7Lv." + o.level + " · " + o.members.size() + "/" + o.maxMembers() + "명",
                            "&7금고 " + Text.money(o.bank), "&7보유 성: " + emptyTo(plugin.wars().castlesOwnedBy(o.name), "없음")));
                    slot++;
                    if (slot % 9 == 8) slot += 2;
                }
            } else {
                boolean leader = g.isLeader(p.getUniqueId());
                List<String> info = new ArrayList<>(List.of("&7Lv." + g.level + " · 인원 " + g.members.size() + "/" + g.maxMembers(),
                        "&f금고 &e" + Text.money(g.bank), "&f보유 성 &c" + emptyTo(plugin.wars().castlesOwnedBy(g.name), "없음"), "", "&6토템 " + g.totems.size() + "/" + g.totemSlots()));
                for (String t : g.totems) info.add(" &f" + gm.totemLabel(t));
                set(4, icon(Material.BLUE_BANNER, "&b&l" + g.name, info));
                set(10, Gui.button(Material.CHEST, "&6길드 창고", "&7" + g.storageRows() + "줄 공유 창고", "&e▶ 클릭"), e -> p.openInventory(g.storage()));
                set(11, Gui.button(Material.LIGHTNING_ROD, "&e토템", "&7손에 토템을 들고 /길드 토템 설치", "&7해제: /길드 토템 해제 <번호>"));
                set(12, Gui.button(Material.EXPERIENCE_BOTTLE, "&a길드 레벨업", g.level >= gm.maxLevel() ? "&7최대 레벨" : "&7비용 " + Text.money(gm.levelUpCost(g)) + " (금고)",
                        leader ? "&e▶ 쉬프트 클릭" : "&7길드장 전용"), e -> {
                    if (leader && e.isShiftClick()) { p.performCommand("guild 레벨업"); new GuildGui(p).open(p); }
                });
                PlayerData d = plugin.data().get(p);
                set(14, Gui.button(d.guildChat ? Material.LIME_DYE : Material.GRAY_DYE, "&a길드 채팅 " + (d.guildChat ? "ON" : "OFF"), "&e▶ 클릭하여 전환"), e -> {
                    d.guildChat = !d.guildChat;
                    new GuildGui(p).open(p);
                });
                set(15, Gui.button(Material.BEACON, "&c공성전", "&e▶ 클릭"), e -> new WarGui(p).open(p));
                set(16, Gui.button(Material.GOLD_INGOT, "&e금고 입금", "&7/길드 입금 <금액>", leader ? "&7출금: /길드 출금 <금액>" : ""));
                int slot = 28;
                for (UUID m : g.members) {
                    if (slot > 43) break;
                    Player op = Bukkit.getPlayer(m);
                    PlayerData md = plugin.data().isLoaded(m) ? plugin.data().get(m) : null;
                    String name = Text.name(m);
                    set(slot, headOf(m, (g.isLeader(m) ? "&6♛ " : "&f") + name, List.of(op != null ? "&a● 접속 중" : "&7● 오프라인",
                            md != null ? "&7Lv." + md.level + " · 전투력 " + Text.num(md.counter("power")) : "",
                            leader && !g.isLeader(m) ? "&c쉬프트+우클릭: 추방" : "")), e -> {
                        if (leader && !g.isLeader(m) && e.isShiftClick() && e.isRightClick()) {
                            p.performCommand("guild 추방 " + name);
                            new GuildGui(p).open(p);
                        }
                    });
                    slot++;
                    if (slot % 9 == 8) slot += 2;
                }
            }
            back(this, 45, p);
            border(this, 6);
            fill(0, 53);
        }
    }

    private static String emptyTo(String s, String d) {
        return s == null || s.isEmpty() ? d : s;
    }

    // =================================================================== 공성전
    private class WarGui extends Gui {
        WarGui(Player p) {
            super(4, "&8공성전");
            Guild g = plugin.guilds().of(p.getUniqueId());
            boolean leader = g != null && g.isLeader(p.getUniqueId());
            int slot = 10;
            for (Castle c : plugin.wars().castles()) {
                if (slot > 25) break;
                WarManager.War w = plugin.wars().warOf(c);
                List<String> lore = new ArrayList<>();
                lore.add("&f소유 &b" + (c.owner == null ? "없음" : c.owner));
                for (Castle.Wall wall : c.walls)
                    lore.add("&7성벽 " + wall.id + " " + (wall.broken ? "&8붕괴" : bar(wall.hp / Math.max(1, wall.maxHp), 10)));
                lore.add("");
                if (w != null) lore.add(w.started ? "&c⚔ 전쟁 중 &7(" + w.attacker + " → " + (w.defender == null ? "무주지" : w.defender) + ")"
                        : "&6준비 중 &7(" + w.attacker + " 선포)");
                else if (leader && !g.name.equals(c.owner)) lore.add("&e▶ 쉬프트 클릭: 전쟁 선포 (전쟁권 소모)");
                set(slot, icon(w != null && w.started ? Material.TNT : Material.BEACON, "&e&l" + c.name + " &7(" + c.id + ")", lore), e -> {
                    if (w == null && leader && e.isShiftClick()) {
                        p.closeInventory();
                        plugin.wars().declare(p, c.id);
                    }
                });
                slot++;
                if (slot % 9 == 8) slot += 2;
            }
            if (plugin.wars().castles().isEmpty()) set(13, Gui.button(Material.MAP, "&7등록된 성이 없습니다"));
            set(31, Gui.button(Material.BOOK, "&f공성전 규칙", "&7전쟁권으로 선포 → 준비 " + plugin.getConfig().getLong("war.prepare-seconds", 600) / 60 + "분",
                    "&7공격측은 채집도구로 성벽을 두드려 파괴", "&7성벽 " + plugin.getConfig().getInt("war.walls-required", 3) + "개 파괴 후 신호기 파괴 시 점령",
                    "&7방어측은 성벽 수리 망치(쉬프트 10초)로 수리", "&7시간 초과 시 방어측 승리"));
            back(this, 27, p);
            fill(0, 35);
        }
    }

    // =================================================================== 랭킹
    private void rebuildRanks() {
        File folder = new File(plugin.getDataFolder(), "players");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<String, Rank> map = new HashMap<>();
            File[] files = folder.listFiles((dir, n) -> n.endsWith(".yml"));
            if (files != null) for (File f : files) {
                YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
                String n = y.getString("nick", y.getString("name"));   // 랭킹도 닉네임으로
                if (n == null) continue;
                map.put(f.getName().replace(".yml", ""), new Rank(n, y.getInt("level", 1), y.getDouble("exp"), y.getLong("money"), (long) y.getDouble("counters.power")));
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    PlayerData d = plugin.data().get(p);
                    map.put(p.getUniqueId().toString(), new Rank(Text.name(p), d.level, d.exp, d.money, Power.of(d.stats)));
                }
                ranks = new ArrayList<>(map.values());
                ranksBuilt = System.currentTimeMillis();
            });
        });
    }

    private class RankGui extends Gui {
        private static final String[] TABS = {"레벨", "전투력", "재산", "길드"};

        RankGui(Player p, int tab) {
            super(6, "&8랭킹 - " + TABS[tab]);
            Material[] tabIcons = {Material.EXPERIENCE_BOTTLE, Material.DIAMOND_SWORD, Material.GOLD_BLOCK, Material.BLUE_BANNER};
            for (int i = 0; i < 4; i++) {
                int t = i;
                set(2 + i + (i >= 2 ? 1 : 0), icon(tabIcons[i], (i == tab ? "&a&l▶ " : "&f") + TABS[i], List.of("&e▶ 클릭")), e -> new RankGui(p, t).open(p));
            }
            if (System.currentTimeMillis() - ranksBuilt > 60_000) rebuildRanks();
            int[] slots = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
            String[] medal = {"&6&l1위", "&f&l2위", "&c&l3위"};
            if (tab == 3) {
                List<Guild> gs = new ArrayList<>(plugin.guilds().all());
                gs.sort((a, b) -> a.level != b.level ? Integer.compare(b.level, a.level) : Long.compare(b.bank, a.bank));
                for (int i = 0; i < Math.min(slots.length, gs.size()); i++) {
                    Guild g = gs.get(i);
                    set(slots[i], icon(Material.BLUE_BANNER, (i < 3 ? medal[i] : "&7" + (i + 1) + "위") + " &b" + g.name,
                            List.of("&7Lv." + g.level + " · " + g.members.size() + "명", "&7금고 " + Text.money(g.bank),
                                    "&7보유 성: " + emptyTo(plugin.wars().castlesOwnedBy(g.name), "없음"))));
                }
            } else {
                List<Rank> list = new ArrayList<>(ranks);
                Comparator<Rank> cmp = switch (tab) {
                    case 1 -> Comparator.comparingLong(Rank::power).reversed();
                    case 2 -> Comparator.comparingLong(Rank::money).reversed();
                    default -> Comparator.comparingInt(Rank::level).reversed().thenComparing(Comparator.comparingDouble(Rank::exp).reversed());
                };
                list.sort(cmp);
                for (int i = 0; i < Math.min(slots.length, list.size()); i++) {
                    Rank r = list.get(i);
                    Player online = Text.player(r.name());
                    List<String> lore = List.of("&7Lv." + r.level(), "&7전투력 " + Text.num(r.power()), "&7재산 " + Text.money(r.money()),
                            online != null ? "&a● 접속 중" : "&8● 오프라인");
                    String title = (i < 3 ? medal[i] : "&7" + (i + 1) + "위") + " &f" + r.name();
                    set(slots[i], online != null ? head(online, title, lore) : icon(Material.SKELETON_SKULL, title, lore));
                }
                int my = -1;
                for (int i = 0; i < list.size(); i++) if (list.get(i).name().equals(p.getName())) my = i + 1;
                set(49, head(p, "&e내 순위: " + (my < 0 ? "-" : my + "위"), List.of("&7" + list.size() + "명 중 · 5분마다 갱신")));
            }
            back(this, 45, p);
            border(this, 6);
            fill(0, 53);
        }
    }

    // =================================================================== 도감
    private record Section(String name, Material icon, Predicate<ItemTemplate> filter) {}

    private static boolean bossSet(String id) {
        return id.matches("(witch|dwarf|harpy|sea|bungbung)_[wa]\\d");
    }

    private final List<Section> sections = List.of(
            new Section("무기", Material.IRON_SWORD, t -> (t.category == Category.WEAPON || t.category == Category.BOW) && !bossSet(t.id) && !t.id.startsWith("spirit") && !t.id.startsWith("bs_") && !t.id.startsWith("relic_")),
            new Section("신화 유물", Material.NETHER_STAR, t -> t.id.startsWith("relic_")),
            new Section("방어구", Material.IRON_CHESTPLATE, t -> t.category == Category.ARMOR && !bossSet(t.id) && !t.id.startsWith("spirit") && !t.id.startsWith("fiend") && !t.id.startsWith("bs_")),
            new Section("보스 세트", Material.WITHER_SKELETON_SKULL, t -> bossSet(t.id)),
            new Section("사신수 · 사흉수", Material.DRAGON_HEAD, t -> t.id.startsWith("spirit") || t.id.startsWith("fiend")),
            new Section("대장장이 제작", Material.SMITHING_TABLE, t -> t.id.startsWith("bs_")),
            new Section("재료", Material.IRON_INGOT, t -> t.category == Category.MATERIAL),
            new Section("기운", Material.AMETHYST_CLUSTER, t -> t.category == Category.SHARD || t.category == Category.CRYSTAL || t.category == Category.ESSENCE),
            new Section("소모품 · 도구", Material.POTION, t -> t.category == Category.POTION || t.category == Category.TICKET || t.category == Category.HAMMER
                    || t.category == Category.TOOL || t.category == Category.RUNE || t.category == Category.TOTEM));

    /** 아이템 ID → 획득처 설명 */
    private Map<String, List<String>> sources() {
        Map<String, List<String>> src = new HashMap<>();
        for (ShopManager.Shop s : plugin.shops().all())
            for (ShopManager.Entry en : s.entries)
                if (plugin.shops().buyPrice(s, en) > 0) src.computeIfAbsent(en.id(), k -> new ArrayList<>()).add("&a상점: &f" + Text.strip(s.title) + " &7(" + Text.money(plugin.shops().buyPrice(s, en)) + ")");
        for (String bid : plugin.bosses().ids()) {
            BossDefinition b = plugin.bosses().def(bid);
            for (BossDefinition.Drop dr : b.drops)
                src.computeIfAbsent(dr.item, k -> new ArrayList<>()).add("&c보스: &f" + b.name + " &7(" + pct(dr.chance) + ")");
            for (BossDefinition.Drop dr : b.minionDrops)
                src.computeIfAbsent(dr.item, k -> new ArrayList<>()).add("&c보스 소환수: &f" + b.name + " &7(" + pct(dr.chance) + ")");
        }
        ConfigurationSection nodes = plugin.getConfig().getConfigurationSection("gather.nodes");
        if (nodes != null) for (String n : nodes.getKeys(false)) {
            ConfigurationSection dr = nodes.getConfigurationSection(n + ".drops");
            if (dr != null) for (String id : dr.getKeys(false)) src.computeIfAbsent(id, k -> new ArrayList<>()).add("&2채집: &f" + nodeName(n));
        }
        for (String c : List.of("crystal_low", "crystal_mid", "crystal_high", "crystal_top"))
            src.computeIfAbsent(c, k -> new ArrayList<>()).add("&7몬스터 처치 (레벨에 따라 등급 결정)");
        for (String el : List.of("nature", "earth")) src.computeIfAbsent("shard_" + el, k -> new ArrayList<>()).add("&2채집 중 낮은 확률");
        for (ItemTemplate t : plugin.items().all()) {
            if (t.id.startsWith("bs_")) src.computeIfAbsent(t.id, k -> new ArrayList<>()).add("&6대장장이 제작 (/제작)");
            if (t.id.startsWith("spirit") || t.id.startsWith("fiend") || t.id.startsWith("essence_")) src.computeIfAbsent(t.id, k -> new ArrayList<>()).add("&d기운 조합 (/기운)");
            if (t.id.startsWith("crystal_") && t.category == Category.CRYSTAL) src.computeIfAbsent(t.id, k -> new ArrayList<>()).add("&d기운 파편 5개 교환 (/기운)");
            if (t.id.equals("totem")) src.computeIfAbsent(t.id, k -> new ArrayList<>()).add("&6토템 뽑기권 사용");
            if (t.id.equals("check")) src.computeIfAbsent(t.id, k -> new ArrayList<>()).add("&e/수표 <금액>");
        }
        return src;
    }

    private static String nodeName(String n) {
        return switch (n) {
            case "herb" -> "약초 (풀/꽃)";
            case "wood" -> "나무 (원목)";
            case "ore" -> "광석 (돌/광석)";
            default -> n;
        };
    }

    private static String pct(double c) {
        return c >= 1 ? "확정" : String.format("%.1f%%", c * 100);
    }

    /** 메인 메뉴의 도감 창 (탈것 · 펫 도감의 ◀ 버튼에서도 사용) */
    public void openCodex(Player p) {
        new CodexGui(p).open(p);
    }

    private class CodexGui extends Gui {
        CodexGui(Player p) {
            super(4, "&8도감");
            int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20};
            for (int i = 0; i < sections.size(); i++) {
                Section s = sections.get(i);
                PlayerData pd = plugin.data().get(p);
                long n = plugin.items().all().stream().filter(s.filter()).count();
                long found = plugin.items().all().stream().filter(s.filter()).filter(t -> pd.seen(t.id)).count();
                set(slots[i], icon(found > 0 ? s.icon() : Material.GRAY_DYE, "&e&l" + s.name(),
                        List.of("&f발견 &a" + found + " &7/ " + n, "&7한 번이라도 얻은 아이템만 표시됩니다", "&e▶ 클릭")), e -> new CodexPage(p, s, 0).open(p));
            }
            set(21, icon(Material.ZOMBIE_HEAD, "&2&l몬스터 도감", List.of("&7잡아 본 몬스터의 드롭 확률", "&e▶ 클릭")), e -> new MobCodex(p, 0).open(p));
            set(22, icon(Material.DRAGON_EGG, "&c&l보스 도감", List.of("&7" + plugin.bosses().ids().size() + "종", "&7스킬 · 드롭 확률", "&e▶ 클릭")), e -> new BossCodex(p).open(p));
            PlayerData cd = plugin.data().get(p);
            if (plugin.mounts() != null)
                set(24, icon(Material.SADDLE, "&6&l탈것 도감", List.of("&f모은 탈것 &a" + plugin.mounts().ownedCount(cd) + " &7/ " + MountManager.Mount.values().length,
                        "&7얻지 못한 탈것의 능력치는 가려집니다", "&e▶ 클릭")), e -> plugin.mounts().openCollection(p));
            if (plugin.pets() != null)
                set(25, icon(Material.EGG, "&d&l펫 도감", List.of("&f모은 펫 &a" + plugin.pets().ownedCount(cd) + " &7/ " + PetManager.Pet.values().length,
                        "&7얻지 못한 펫의 능력치는 가려집니다", "&e▶ 클릭")), e -> plugin.pets().open(p));
            back(this, 27, p);
            fill(0, 35);
        }
    }

    private class CodexPage extends Gui {
        CodexPage(Player p, Section sec, int page) {
            super(6, "&8도감 - " + sec.name(), "shop");
            PlayerData pd = plugin.data().get(p);
            List<ItemTemplate> list = plugin.items().all().stream().filter(sec.filter()).filter(t -> pd.seen(t.id)).toList();
            if (list.isEmpty()) set(22, Gui.button(Material.GRAY_DYE, "&7아직 발견한 아이템이 없습니다", "&7아이템을 얻으면 도감에 기록됩니다"));
            Map<String, List<String>> src = sources();
            boolean admin = p.hasPermission("rpgcraft.admin");
            int pages = Math.max(1, (list.size() + 44) / 45);
            for (int i = 0; i < 45 && page * 45 + i < list.size(); i++) {
                ItemTemplate t = list.get(page * 45 + i);
                ItemStack it = plugin.items().create(t.id, 1);
                if (it == null) continue;
                ItemMeta m = it.getItemMeta();
                List<String> lore = m.hasLore() ? new ArrayList<>(m.getLore()) : new ArrayList<>();
                lore.add("");
                lore.add(Text.c("&f&l획득처"));
                List<String> s = src.getOrDefault(t.id, List.of("&7특수 경로 (이벤트/운영자 지급)"));
                for (String line : s.subList(0, Math.min(6, s.size()))) lore.add(Text.c(" " + line));
                if (admin) lore.add(Text.c("&8[관리자] 쉬프트 클릭: 지급"));
                m.setLore(lore);
                it.setItemMeta(m);
                set(i, it, e -> {
                    if (admin && e.isShiftClick()) p.getInventory().addItem(plugin.items().create(t.id, 1));
                });
            }
            if (page > 0) set(48, Gui.button(Material.ARROW, "&f이전"), e -> new CodexPage(p, sec, page - 1).open(p));
            if (page + 1 < pages) set(50, Gui.button(Material.ARROW, "&f다음"), e -> new CodexPage(p, sec, page + 1).open(p));
            set(49, Gui.button(Material.BOOK, "&7" + (page + 1) + " / " + pages + " 페이지"));
            set(45, Gui.button(Material.ARROW, "&f◀ 도감"), e -> new CodexGui(p).open(p));
            fill(45, 53);
        }
    }

    private static String skillName(String type) {
        return switch (type) {
            case "SLAM" -> "대지 강타";
            case "METEOR" -> "낙하 폭발";
            case "SUMMON" -> "소환";
            case "FIREBALL" -> "화염구";
            case "PULL" -> "끌어당기기";
            case "BLINK" -> "순간이동 기습";
            default -> type;
        };
    }

    /** 보스 기술 패턴 설명 (범위 · 피하는 법) */
    private static String skillPattern(BossDefinition.Skill k) {
        return switch (k.type) {
            case "SLAM" -> "대지 강타 &8— 주변 " + (int) k.radius + "칸 충격파 · 빨간 고리 밖으로";
            case "METEOR" -> "낙하 폭발 &8— 대상 발밑 " + (int) k.radius + "칸 · 고리에서 벗어나기";
            case "SUMMON" -> "소환 &8— " + (k.name != null ? k.name : kr.rpgcraft.mob.MobManager.korean(org.bukkit.entity.EntityType.valueOf(k.entity == null ? "ZOMBIE" : k.entity))) + " " + k.amount + "마리";
            case "FIREBALL" -> "화염구 &8— 대상에게 날아옴 · 옆으로 피하기";
            case "PULL" -> "끌어당기기 &8— " + (int) k.radius + "칸 안을 당김 · 멀리 떨어지기";
            case "BLINK" -> "순간이동 기습 &8— 대상 뒤로 이동해 공격";
            default -> k.type;
        };
    }

    /** 보스를 만나는 곳 (실제 설정에서 계산) */
    private String where(String id) {
        List<String> out = new ArrayList<>();
        if (id.equals("vengeful_spirit")) out.add("월드 보스 (원혼의 부적으로 소환)");
        else if (kr.rpgcraft.world.WorldBossManager.isWorldBoss(id)) out.add("월드 보스 (운영자 소환)");
        if (id.equals("balrog")) out.add("발록의 봉인석으로 소환");
        String[] tb = kr.rpgcraft.world.DungeonManager.TIER_BOSS;
        for (int t = 1; t < tb.length; t++)
            if (id.equals(tb[t])) out.add("던전 " + t + "단계 「" + kr.rpgcraft.world.DungeonManager.TIER_NAME[t] + "」");
        if (out.isEmpty()) out.add("특별 이벤트 보스 (운영자 소환)");
        return String.join(" · ", out);
    }

    /** 몬스터 도감: 한 번이라도 잡은 몬스터만, 레벨 · 처치 수 · 드롭 확률 */
    private class MobCodex extends Gui {
        MobCodex(Player p, int page) {
            super(6, "&8몬스터 도감");
            PlayerData pd = plugin.data().get(p);
            List<kr.rpgcraft.mob.CustomMobManager.MobDef> known = new ArrayList<>();
            for (var d : plugin.customMobs().defs()) if (pd.counter("mk_" + d.id) > 0) known.add(d);
            known.sort(java.util.Comparator.comparingInt(d -> d.minLevel));
            if (known.isEmpty()) set(22, Gui.button(Material.GRAY_DYE, "&7아직 잡아 본 몬스터가 없습니다"));
            int pages = Math.max(1, (known.size() + 44) / 45);
            for (int i = 0; i < 45 && page * 45 + i < known.size(); i++) {
                var d = known.get(page * 45 + i);
                Material egg = Material.matchMaterial(d.type.name() + "_SPAWN_EGG");
                List<String> lore = new ArrayList<>(List.of("&7Lv." + d.minLevel + " ~ " + d.maxLevel + " &8· &f처치 " + (long) pd.counter("mk_" + d.id), "", "&f&l드롭"));
                for (var dr : d.drops) {
                    ItemTemplate t = plugin.items().get(dr.item);
                    lore.add(" " + (t == null ? dr.item : t.grade.color + t.name) + " &7" + pct(dr.chance));
                }
                if (d.drops.isEmpty()) lore.add(" &8없음");
                set(i, icon(egg == null ? Material.SPAWNER : egg, "&f" + d.name, lore));
            }
            if (page > 0) set(45, Gui.button(Material.ARROW, "&f이전"), e -> new MobCodex(p, page - 1).open(p));
            if (page + 1 < pages) set(53, Gui.button(Material.ARROW, "&f다음"), e -> new MobCodex(p, page + 1).open(p));
            fill(0, 53);
        }
    }

    private class BossCodex extends Gui {
        BossCodex(Player p) {
            super(6, "&8보스 도감");
            List<String> ids = new ArrayList<>(plugin.bosses().ids());
            ids.sort(java.util.Comparator.comparingInt(x -> plugin.bosses().def(x).level));
            int[] slots = new int[28];
            for (int r = 0, n = 0; r < 4; r++) for (int c = 1; c <= 7; c++) slots[n++] = (r + 1) * 9 + c;
            for (int i = 0; i < ids.size() && i < slots.length; i++) {
                BossDefinition b = plugin.bosses().def(ids.get(i));
                Material egg = Material.matchMaterial(b.type.name() + "_SPAWN_EGG");
                List<String> lore = new ArrayList<>(List.of("&7Lv." + b.level + " &8· &e" + where(b.id),
                        "&c체력 " + Text.num(b.hp) + (b.awaken ? " &4(체력 절반에서 각성 ×" + b.awakenMultiplier + ")" : ""),
                        "&6공격력 " + Text.num(b.damage) + " &b방어 " + (int) b.defense + "%", "", "&f&l기술 패턴"));
                for (BossDefinition.Skill k : b.skills) lore.add(" &c▸ &f" + skillPattern(k) + " &8(" + k.interval + "초)");
                lore.add(" &8여럿이 싸우면 기술이 잦아지고 단단해짐");
                lore.add("");
                lore.add("&f&l드롭");
                int n = 0;
                for (BossDefinition.Drop dr : b.drops) {
                    if (n++ >= 7) { lore.add(" &8..."); break; }
                    ItemTemplate t = plugin.items().get(dr.item);
                    lore.add(" " + (t == null ? dr.item : t.grade.color + t.name) + " &7" + pct(dr.chance));
                }
                set(slots[i], icon(egg == null ? Material.DRAGON_EGG : egg, "&c&l" + b.name, lore));
            }
            set(45, Gui.button(Material.ARROW, "&f◀ 도감"), e -> new CodexGui(p).open(p));
            fill(0, 53);
        }
    }

    // =================================================================== 설정
    private class SettingsGui extends Gui {
        SettingsGui(Player p) {
            super(3, "&8설정", "settings");
            PlayerData d = plugin.data().get(p);
            int slot = 10;
            for (Setting s : Setting.values()) {
                boolean on = s.get(d);
                set(slot, icon(on ? s.icon : Material.GRAY_DYE, (on ? "&a&l" : "&7&l") + s.label + (on ? " ON" : " OFF"),
                        List.of("&7" + s.desc, "", "&e▶ 클릭하여 전환")), e -> {
                    boolean now = s.toggle(d);
                    plugin.data().save(d);   // 나갔다 와도 유지
                    if (s == Setting.SIDEBAR) plugin.hud().applySidebar(p);
                    Text.actionBar(p, "&f" + s.label + " " + (now ? "&aON" : "&7OFF"));
                    new SettingsGui(p).open(p);
                });
                slot++;
            }
            back(this, 18, p);
            fill(0, 26);
        }
    }

    // =================================================================== 도움말
    private class HelpGui extends Gui {
        HelpGui(Player p) {
            super(4, "&8도움말");
            set(10, Gui.button(Material.KNOWLEDGE_BOOK, "&e&l조작키", "&fF &7- 포션가방 포션 사용", "&f쉬프트 + F &7- 메인 메뉴",
                    "&f쉬프트 + Q &7- 퀵 액티브 스킬 (스킬 메뉴에서 우클릭으로 지정)", "&f우클릭 &7- 사신수 무기 스킬 / 기운 결정 개봉"));
            set(11, Gui.button(Material.NETHER_STAR, "&e&l레벨과 스탯", "&7레벨업마다 스탯 5, 체력 200", "&c힘 &72포인트당 공격력 +3",
                    "&a민첩 &71포인트당 크리티컬 +" + plugin.getConfig().getDouble("player.dex-crit-per-point", 0.12) + "%", "&b모험 &71포인트당 체력 +40", "&7장비는 요구 스탯을 채워야 효과 발동"));
            set(12, Gui.button(Material.IRON_SWORD, "&e&l전투", "&7크리티컬: 대미지 2배 + 방어 무시", "&7무기마다 공격 속도가 다름 (단검 빠름, 도끼 느림)",
                    "&7너무 빨리 때리면 대미지 감소", "&7검은 크리티컬 -20%, 방패는 방어력 제공"));
            set(13, Gui.button(Material.ANVIL, "&e&l강화", "&7+8 부터 실패 시 파괴 가능", "&7파괴 방지권: 파괴 1회 방지", "&7강화 확률 10% 증가권: 성공률 +10%",
                    "&7보스 세트는 강화 불가"));
            set(14, Gui.button(Material.WOODEN_PICKAXE, "&e&l채집", "&7채집도구로 풀/꽃, 원목, 돌/광석을 부수면 재료 획득",
                    "&7초보 3분 / 중급 2분 / 숙련 1분 쿨타임", "&7낮은 확률로 기운 파편 획득"));
            set(15, Gui.button(Material.AMETHYST_CLUSTER, "&e&l사신수", "&7월드보스와 채집에서 기운 파편 획득", "&7파편 5개 → 결정 → 30% 확률로 기운",
                    "&7기운 5개 + 유니크 무기 = 사신수 무기"));
            set(16, Gui.button(Material.BEACON, "&e&l공성전", "&7전쟁권으로 선포, 채집도구로 성벽 파괴", "&7성벽 3개 파괴 후 신호기를 부수면 점령"));
            set(19, Gui.button(Material.ENCHANTED_BOOK, "&e&l히든 패시브", "&7특정 행동을 계속 반복하면 해금", "&7무엇을 반복해야 할지는 비밀!"));
            set(20, Gui.button(Material.MOSSY_STONE_BRICKS, "&e&l유적", "&7시작 블록을 밟고 도착 블록까지 도달", "&7모험 스탯이 입장 조건", "&7최초 클리어 시 특별 패시브"));
            back(this, 27, p);
            fill(0, 35);
        }
    }
}
