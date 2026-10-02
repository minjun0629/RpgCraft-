package kr.rpgcraft.royal;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.data.PlayerData;
import kr.rpgcraft.gui.Gui;
import kr.rpgcraft.stat.Stat;
import kr.rpgcraft.stat.StatMap;
import kr.rpgcraft.util.ItemBuilder;
import kr.rpgcraft.util.Text;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * v5.11.0 조각술 — 『달빛 조각사』 위드처럼 조각칼로 조각품을 깎는다.
 * <ul>
 *   <li><b>조각</b>: 조각칼을 들고 웅크린 채 땅을 우클릭 → 주제 · 재료(인벤토리의 블록)를 고르면 몇 초 동안 깎아 조각상을 세운다.
 *       등급(졸작 · 평작 · 수작 · 명작 · 대작)은 조각술 숙련 · 예술 · 행운 · 재료 · 주제 난이도 · 달빛에 따라 정해진다.</li>
 *   <li><b>감상</b>: 수작 이상 조각품을 우클릭하면 주제에 맞는 버프. 대작은 근처 모두에게 저절로 버프. 처음 감상하면 예술이 오른다.</li>
 *   <li><b>달빛 조각사</b> (히든 클래스): 조각술 중급 · 예술 100 · 명작 1개 이상이면 전직. 달빛 조각술(밤 · 하늘 아래서 품질 ↑, 조각품이 빛남),
 *       조각 검술(조각칼을 무기로), 조각 파괴술(내 조각품을 부숴 힘 · 민첩 · 모험 ↑), 조각 생명부여(조각품을 살아 있는 동료로).</li>
 * </ul>
 */
public class SculptManager implements Listener, CommandExecutor {
    public static final String[] GRADE = {"&8졸작", "&7평작", "&a수작", "&d명작", "&6&l대작"};
    private static final double[] LIFE_GRADE_MULT = {0.5, 0.65, 0.8, 1.0, 1.4};
    private static final int MAX_SKILL = 30;
    private static final double[][] PEDESTAL = {{-7, 0, -7, 7, 3, 7}};

    /** 재료 상점 9종 — 5줄 창의 4번째 줄 (27 ~ 35). 가격은 royal-road.sculpt.material-price-mult 로 조절 */
    private record Offer(Material mat, String label, long price) {}

    private static final Offer[] OFFERS = {
            new Offer(Material.OAK_LOG, "참나무 원목", 300), new Offer(Material.STONE, "돌", 300),
            new Offer(Material.PACKED_ICE, "얼음 덩어리", 2000), new Offer(Material.QUARTZ_BLOCK, "대리석(석영)", 3000), new Offer(Material.OBSIDIAN, "흑요석", 8000),
            new Offer(Material.AMETHYST_BLOCK, "자수정", 12000), new Offer(Material.GOLD_BLOCK, "금", 50000), new Offer(Material.EMERALD_BLOCK, "에메랄드", 120000),
            new Offer(Material.DIAMOND_BLOCK, "다이아몬드", 250000)};

    static final class Sculpture {
        String id, name, ownerName, world, material;
        UUID owner;
        Subject subject;
        int grade;
        double x, y, z;
        float yaw;
        boolean moon;
        long created;
        final List<UUID> parts = new ArrayList<>();

        Location base() {
            World w = Bukkit.getWorld(world);
            return w == null ? null : new Location(w, x, y, z);
        }
    }

    static final class Life {
        String name;
        Subject subject;
        int grade, lv = 1;
        double exp;
        long downUntil;
        // 불러낸 동안만
        UUID entity;
        double hp, maxHp;
        long nextAct;
    }

    static final class LifeBook {
        final List<Life> list = new ArrayList<>();
        boolean out;
    }

    private static final class Session {
        Subject subject;
        Material mat;
        Location base, anchor;
        float yaw;
        int ticks, total;
    }

    private static NamespacedKey KNIFE, STATUE, LIFE;
    private final RpgCraft plugin;
    private final File file;
    private final YamlConfiguration data;
    private final Map<String, Sculpture> sculptures = new LinkedHashMap<>();
    private final Map<UUID, LifeBook> books = new HashMap<>();
    private final Map<UUID, UUID> lifeOwners = new HashMap<>();
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, String> naming = new HashMap<>();
    private final Map<UUID, Long> namingUntil = new HashMap<>();
    private final Map<String, Long> seenCooldown = new HashMap<>();
    private final Map<UUID, Long> releaseConfirm = new HashMap<>();
    private boolean dirty;
    private long tick;

    public SculptManager(RpgCraft plugin) {
        this.plugin = plugin;
        KNIFE = new NamespacedKey(plugin, "rr_knife");
        STATUE = new NamespacedKey(plugin, "rr_statue");
        LIFE = new NamespacedKey(plugin, "rr_life");
        file = new File(plugin.getDataFolder(), "sculptures.yml");
        data = YamlConfiguration.loadConfiguration(file);
        load();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 10L);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> { if (dirty) save(); }, 1200L, 1200L);
        for (World w : Bukkit.getWorlds()) {
            for (Wolf e : w.getEntitiesByClass(Wolf.class)) if (isLife(e)) e.remove();   // 이전 실행에서 남은 생명체
            cleanOrphans(w.getEntities());
        }
    }

    // ------------------------------------------------------------------ 판정 (다른 코드에서 씀)
    /** 조각 생명부여로 태어난 생명체인지 (전투 코드가 몬스터처럼 다루지 않게) */
    public static boolean isLife(Entity e) {
        return LIFE != null && e != null && e.getPersistentDataContainer().has(LIFE, PersistentDataType.STRING);
    }

    public boolean isKnife(ItemStack it) {
        return it != null && it.getType() == Material.FLINT && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(KNIFE, PersistentDataType.BYTE);
    }

    public ItemStack knife() {
        ItemStack it = new ItemBuilder(Material.FLINT).name("&f&l조각칼")
                .lore("&7조각사의 손때가 묻은 작은 칼", "", "&e웅크리고 땅 우클릭 &7→ 조각하기", "&e조각품 우클릭 &7→ 감상 &8(웅크리면 내 조각품 관리)",
                        "&e허공 우클릭 &7→ 조각술 메뉴", "", "&b달빛 조각사&7는 무기로도 씀 (조각 검술)").build();
        var m = it.getItemMeta();
        m.getPersistentDataContainer().set(KNIFE, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    private double cfg(String k, double def) {
        return plugin.getConfig().getDouble("royal-road.sculpt." + k, def);
    }

    // ------------------------------------------------------------------ 저장
    private void load() {
        ConfigurationSection s = data.getConfigurationSection("sculptures");
        if (s != null) for (String id : s.getKeys(false)) {
            ConfigurationSection x = s.getConfigurationSection(id);
            Subject subj = x == null ? null : Subject.of(x.getString("subject"));
            if (subj == null) continue;
            Sculpture sc = new Sculpture();
            sc.id = id;
            sc.subject = subj;
            sc.name = x.getString("name", subj.label);
            sc.ownerName = x.getString("owner-name", "?");
            try { sc.owner = UUID.fromString(x.getString("owner", "")); } catch (IllegalArgumentException ex) { continue; }
            sc.world = x.getString("world");
            sc.material = x.getString("material", "STONE");
            sc.grade = x.getInt("grade");
            sc.x = x.getDouble("x");
            sc.y = x.getDouble("y");
            sc.z = x.getDouble("z");
            sc.yaw = (float) x.getDouble("yaw");
            sc.moon = x.getBoolean("moon");
            sc.created = x.getLong("created");
            for (String u : x.getStringList("parts")) {
                try { sc.parts.add(UUID.fromString(u)); } catch (IllegalArgumentException ignored) { }
            }
            sculptures.put(id, sc);
        }
    }

    private LifeBook book(UUID id) {
        return books.computeIfAbsent(id, k -> {
            LifeBook b = new LifeBook();
            ConfigurationSection s = data.getConfigurationSection("life." + k);
            if (s != null) for (String i : s.getKeys(false)) {
                ConfigurationSection x = s.getConfigurationSection(i);
                Subject subj = x == null ? null : Subject.of(x.getString("subject"));
                if (subj == null || !subj.canLive()) continue;
                Life l = new Life();
                l.subject = subj;
                l.name = x.getString("name", subj.label);
                l.grade = x.getInt("grade", 2);
                l.lv = Math.max(1, x.getInt("lv", 1));
                l.exp = x.getDouble("exp");
                l.downUntil = x.getLong("down");
                b.list.add(l);
            }
            return b;
        });
    }

    private void writeBook(UUID id) {
        LifeBook b = books.get(id);
        if (b == null) return;
        data.set("life." + id, null);
        for (int i = 0; i < b.list.size(); i++) {
            Life l = b.list.get(i);
            String p = "life." + id + "." + i;
            data.set(p + ".subject", l.subject.name());
            data.set(p + ".name", l.name);
            data.set(p + ".grade", l.grade);
            data.set(p + ".lv", l.lv);
            data.set(p + ".exp", l.exp);
            data.set(p + ".down", l.downUntil);
        }
        dirty = true;
    }

    public void save() {
        data.set("sculptures", null);
        for (Sculpture s : sculptures.values()) {
            String p = "sculptures." + s.id;
            data.set(p + ".subject", s.subject.name());
            data.set(p + ".name", s.name);
            data.set(p + ".owner", s.owner.toString());
            data.set(p + ".owner-name", s.ownerName);
            data.set(p + ".world", s.world);
            data.set(p + ".material", s.material);
            data.set(p + ".grade", s.grade);
            data.set(p + ".x", s.x);
            data.set(p + ".y", s.y);
            data.set(p + ".z", s.z);
            data.set(p + ".yaw", (double) s.yaw);
            data.set(p + ".moon", s.moon);
            data.set(p + ".created", s.created);
            List<String> parts = new ArrayList<>();
            for (UUID u : s.parts) parts.add(u.toString());
            data.set(p + ".parts", parts);
        }
        for (UUID id : books.keySet()) writeBook(id);
        try {
            data.save(file);
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().warning("sculptures.yml 저장 실패: " + ex.getMessage());
        }
    }

    public void shutdown() {
        for (UUID id : new ArrayList<>(books.keySet())) dismiss(id, null, null);
        for (Session s : sessions.values()) refundSession(s, null);
        sessions.clear();
        save();
    }

    // ------------------------------------------------------------------ 숙련 (초급 1 ~ 고급 10, 마스터)
    private static double need(int lv) {
        return 30 + lv * 25.0;
    }

    private static double cum(int lv) {
        double s = 0;
        for (int i = 0; i < lv; i++) s += need(i);
        return s;
    }

    public int skillLevel(PlayerData d) {
        double xp = d.counter("rr_sculpt_xp");
        int lv = 0;
        while (lv < MAX_SKILL && xp >= need(lv)) { xp -= need(lv); lv++; }
        return lv;
    }

    public String skillLabel(PlayerData d) {
        int lv = skillLevel(d);
        if (lv >= MAX_SKILL) return "마스터";
        String tier = lv < 10 ? "초급" : lv < 20 ? "중급" : "고급";
        double pct = (d.counter("rr_sculpt_xp") - cum(lv)) / need(lv) * 100;
        return tier + " " + (lv % 10 + 1) + String.format(" (%.1f%%)", Math.max(0, pct));
    }

    private void addSkill(Player p, double xp) {
        PlayerData d = plugin.data().get(p);
        int before = skillLevel(d);
        d.addCounter("rr_sculpt_xp", xp);
        int after = skillLevel(d);
        if (after > before) {
            Text.msg(p, "&d조각술 &f스킬의 숙련도가 올라 &e" + skillLabel(d).replaceAll(" \\(.*", "") + "&f이(가) 되었습니다.");
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.4f);
        }
    }

    /** 사망 페널티: 이번 단계에서 쌓은 숙련도를 frac 만큼 잃음 (단계는 내려가지 않음) */
    public boolean loseSkill(PlayerData d, double frac) {
        int lv = skillLevel(d);
        if (lv >= MAX_SKILL) return false;
        double floor = cum(lv), xp = d.counter("rr_sculpt_xp");
        if (xp <= floor) return false;
        d.counters.put("rr_sculpt_xp", Math.max(floor, xp - need(lv) * frac));
        return true;
    }

    private static int requiredSkill(Subject s) {
        return (s.difficulty - 1) * 4;
    }

    // ------------------------------------------------------------------ 달빛 조각사
    public boolean isMoonlight(PlayerData d) {
        return d.counter("rr_moonlight") > 0;
    }

    private List<String> moonlightProblems(PlayerData d) {
        List<String> out = new ArrayList<>();
        if (skillLevel(d) < 10) out.add("조각술 중급 (지금 " + skillLabel(d) + ")");
        if (RoyalStat.ART.points(d) < 100) out.add("예술 100 (지금 " + RoyalStat.ART.points(d) + ")");
        if (d.counter("rr_masterpieces") < 1) out.add("명작 이상 조각품 1개");
        return out;
    }

    private void becomeMoonlight(Player p) {
        PlayerData d = plugin.data().get(p);
        if (isMoonlight(d)) return;
        List<String> prob = moonlightProblems(d);
        if (!prob.isEmpty()) { Text.msg(p, "&c아직 자격이 없습니다: &f" + String.join(", ", prob)); return; }
        d.counters.put("rr_moonlight", 1.0);
        p.sendTitle(Text.c("&b&l☾ 달빛 조각사"), Text.c("&f히든 클래스로 전직하셨습니다"), 10, 80, 20);
        p.getWorld().playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.8f);
        p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 120, 0.6, 1.2, 0.6, 0.05);
        Text.announce(Text.c("&b[로열 로드] &f" + Text.name(p) + " &7님이 전설의 히든 클래스 &b「달빛 조각사」&7로 전직했습니다!"));
        Text.msg(p, "&f새 스킬: &b달빛 조각술 · 조각 검술 · 조각 파괴술 · 조각 생명부여 &7(/조각 에서 확인)");
        if (plugin.royal() != null) plugin.royal().addFame(p, 50, "달빛 조각사 전직");
        plugin.stats().refresh(p);
    }

    /** 달빛 조각술: 밤 · 맑은 하늘 아래 (보름달이면 더) */
    private static int moonlight(Location l) {
        World w = l.getWorld();
        if (w == null || w.getEnvironment() != World.Environment.NORMAL || w.hasStorm()) return 0;
        long t = w.getTime();
        if (t < 13000 || t > 23000 || l.getBlock().getLightFromSky() < 13) return 0;
        return (w.getFullTime() / 24000) % 8 == 0 ? 2 : 1;
    }

    // ------------------------------------------------------------------ 능력치
    private boolean holdingKnife(Player p) {
        return isKnife(p.getInventory().getItemInMainHand());
    }

    /** 감상 버프 · 조각 파괴술 · 조각 검술 */
    public StatMap bonus(Player p, PlayerData d) {
        StatMap t = new StatMap();
        long now = System.currentTimeMillis();
        if (d.counter("rr_buff_until") > now) {
            Subject s = Subject.values()[Math.max(0, Math.min(Subject.values().length - 1, (int) d.counter("rr_buff_subj")))];
            int g = Math.max(2, Math.min(4, (int) d.counter("rr_buff_grade")));
            t.add(s.buffStat, s.buffValues[g - 2]);
        }
        if (d.counter("rr_destroy_until") > now) {
            double pct = d.counter("rr_destroy_pct");
            t.add(Stat.STR_PCT, pct).add(Stat.DEX_PCT, pct).add(Stat.ADV_PCT, pct);
        }
        if (isMoonlight(d) && holdingKnife(p)) {
            int art = RoyalStat.ART.points(d);
            t.add(Stat.ATK, Math.min(3000, 50 + art * 2.0)).add(Stat.CRIT, 5).add(Stat.ARMOR_PEN, 5);
        }
        return t;
    }

    // ------------------------------------------------------------------ 재료
    /** 재료 품질 보너스 (조각할 수 없는 블록이면 -1) */
    static int materialBonus(Material m) {
        if (m == null || !m.isBlock() || !m.isSolid()) return -1;
        String n = m.name();
        if (n.equals("NETHERITE_BLOCK")) return 25;
        if (n.equals("DIAMOND_BLOCK")) return 18;
        if (n.equals("EMERALD_BLOCK")) return 14;
        if (n.equals("GOLD_BLOCK")) return 12;
        if (n.equals("IRON_BLOCK")) return 9;
        if (n.equals("AMETHYST_BLOCK") || n.contains("OBSIDIAN")) return 8;
        if (n.contains("PRISMARINE") || n.contains("COPPER_BLOCK") || n.equals("LAPIS_BLOCK")) return 7;
        if (n.contains("QUARTZ") || n.equals("CALCITE")) return 6;
        if (n.equals("ICE") || n.equals("PACKED_ICE") || n.equals("BLUE_ICE")) return 4;
        if (n.equals("STONE") || n.contains("COBBLESTONE") || n.contains("ANDESITE") || n.contains("DIORITE") || n.contains("GRANITE")
                || n.contains("DEEPSLATE") || n.equals("TUFF") || n.contains("SANDSTONE") || n.contains("STONE_BRICKS") || n.equals("SMOOTH_STONE")
                || n.contains("BLACKSTONE") || n.contains("BASALT") || n.endsWith("TERRACOTTA")) return 2;
        if (n.equals("CLAY") || n.equals("SNOW_BLOCK") || n.equals("BONE_BLOCK")) return 1;
        if (n.endsWith("_LOG") || n.endsWith("_WOOD") || n.endsWith("_PLANKS") || n.endsWith("_STEM") || n.endsWith("_HYPHAE")) return 0;
        return -1;
    }

    private static boolean isIce(Material m) {
        return m.name().contains("ICE") || m == Material.SNOW_BLOCK;
    }

    private Map<Material, Integer> carvable(Player p) {
        Map<Material, Integer> out = new LinkedHashMap<>();
        for (ItemStack it : p.getInventory().getStorageContents())
            if (it != null && !it.hasItemMeta() && materialBonus(it.getType()) >= 0) out.merge(it.getType(), it.getAmount(), Integer::sum);
        return out;
    }

    private boolean take(Player p, Material m, int n) {
        if (carvable(p).getOrDefault(m, 0) < n) return false;
        int left = n;
        ItemStack[] cont = p.getInventory().getStorageContents();
        for (int i = 0; i < cont.length && left > 0; i++) {
            ItemStack it = cont[i];
            if (it == null || it.getType() != m || it.hasItemMeta()) continue;
            int t = Math.min(left, it.getAmount());
            it.setAmount(it.getAmount() - t);
            left -= t;
        }
        p.getInventory().setStorageContents(cont);
        return true;
    }

    private void give(Player p, Material m, int n) {
        for (ItemStack left : p.getInventory().addItem(new ItemStack(m, n)).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
    }

    // ------------------------------------------------------------------ 조각하기
    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !isKnife(e.getItem())) return;
        Player p = e.getPlayer();
        if (e.getAction() == Action.RIGHT_CLICK_AIR || (e.getAction() == Action.RIGHT_CLICK_BLOCK && !p.isSneaking())) {
            e.setCancelled(true);
            openMenu(p);
            return;
        }
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        e.setCancelled(true);
        if (e.getBlockFace() != BlockFace.UP) { Text.actionBar(p, "&c조각상은 땅(블록 윗면)에 세웁니다."); return; }
        Block ground = e.getClickedBlock(), above = ground.getRelative(BlockFace.UP);
        if (!above.isPassable() || !above.getRelative(BlockFace.UP).isPassable()) { Text.actionBar(p, "&c조각상을 세울 자리가 비어 있지 않습니다."); return; }
        if (sessions.containsKey(p.getUniqueId())) { Text.actionBar(p, "&c이미 조각하는 중입니다."); return; }
        Location base = above.getLocation().add(0.5, 0, 0.5);
        String why = placeProblem(p, base);
        if (why != null) { Text.msg(p, "&c" + why); return; }
        float yaw = Math.round((p.getLocation().getYaw() + 180) / 90f) * 90f;
        openSubjects(p, base, yaw);
    }

    private String placeProblem(Player p, Location base) {
        int mine = 0;
        for (Sculpture s : sculptures.values()) {
            if (s.owner.equals(p.getUniqueId())) mine++;
            if (s.world.equals(base.getWorld().getName()) && Math.abs(s.x - base.getX()) < 2 && Math.abs(s.z - base.getZ()) < 2 && Math.abs(s.y - base.getY()) < 3)
                return "바로 옆에 다른 조각품이 있습니다.";
        }
        int max = plugin.getConfig().getInt("royal-road.sculpt.max-per-player", 12);
        if (mine >= max && !p.hasPermission("rpgcraft.admin")) return "조각품은 한 사람당 " + max + "개까지 세울 수 있습니다. (/조각 → 내 조각품에서 철거)";
        return null;
    }

    private void openSubjects(Player p, Location base, float yaw) {
        PlayerData d = plugin.data().get(p);
        int lv = skillLevel(d);
        Gui g = new Gui(3, "&8무엇을 조각할까?") {
        };
        g.set(4, Gui.button(Material.FLINT, "&d조각술 &f" + skillLabel(d), "&7주제를 고르고, 다음 창에서 재료(인벤토리의 블록)를 고릅니다.",
                "&7난이도가 높을수록 좋은 등급이 어렵지만 보상이 큽니다."), null);
        int slot = 9;
        for (Subject s : Subject.values()) {
            boolean ok = lv >= requiredSkill(s);
            List<String> lore = new ArrayList<>(List.of("&7난이도 &e" + "★".repeat(s.difficulty) + "&8" + "★".repeat(5 - s.difficulty),
                    "&7재료 블록 &f" + s.blocks + "개", "&7감상 효과: &f" + s.buffStat.label + " ↑",
                    s.canLive() ? "&b생명부여 가능 &7(" + s.role.label + ": " + s.role.desc + ")" : "&8생명부여 불가 (조각상 전용)"));
            if (!ok) lore.add("&c조각술 " + labelOf(requiredSkill(s)) + " 이상 필요");
            g.set(slot++, Gui.button(ok ? s.icon : Material.GRAY_DYE, (ok ? "&f&l" : "&7") + s.label, lore.toArray(new String[0])),
                    ok ? ev -> openMaterials(p, base, yaw, s) : null);
        }
        g.fill(0, 26);
        g.open(p);
    }

    private static String labelOf(int lv) {
        return lv >= MAX_SKILL ? "마스터" : (lv < 10 ? "초급 " : lv < 20 ? "중급 " : "고급 ") + (lv % 10 + 1);
    }

    private void openMaterials(Player p, Location base, float yaw, Subject s) {
        Map<Material, Integer> have = carvable(p);
        Gui g = new Gui(3, "&8재료 고르기 · " + s.label) {
        };
        g.set(4, Gui.button(s.icon, "&f&l" + s.label, "&7재료 블록 " + s.blocks + "개가 필요합니다.", "&7재료가 좋을수록 품질 ↑",
                "&7재료는 /조각 → 재료 상점에서 살 수 있습니다."), null);
        int slot = 9;
        for (Map.Entry<Material, Integer> en : have.entrySet()) {
            if (slot > 17) break;
            Material m = en.getKey();
            boolean ok = en.getValue() >= s.blocks;
            int bonus = materialBonus(m) + (s == Subject.ICE_DRAGON && isIce(m) ? 6 : 0);
            g.set(slot++, Gui.button(ok ? m : Material.BARRIER, (ok ? "&f" : "&c") + m.name().toLowerCase(Locale.ROOT).replace('_', ' '),
                    "&7가진 개수 &f" + en.getValue() + " &7/ 필요 " + s.blocks, "&7재료 품질 &a+" + bonus, ok ? "&e클릭해 조각 시작" : "&c재료가 모자랍니다"),
                    ok ? ev -> { p.closeInventory(); start(p, base, yaw, s, m); } : null);
        }
        if (have.isEmpty()) g.set(13, Gui.button(Material.BARRIER, "&c조각할 재료가 없습니다", "&7돌 · 원목 · 얼음 · 대리석 · 금 블록 등", "&7/조각 → 재료 상점"), null);
        g.fill(0, 26);
        g.open(p);
    }

    private void start(Player p, Location base, float yaw, Subject s, Material m) {
        if (sessions.containsKey(p.getUniqueId())) return;
        String why = placeProblem(p, base);
        if (why != null) { Text.msg(p, "&c" + why); return; }
        if (!take(p, m, s.blocks)) { Text.msg(p, "&c재료가 모자랍니다."); return; }
        PlayerData d = plugin.data().get(p);
        Session ss = new Session();
        ss.subject = s;
        ss.mat = m;
        ss.base = base;
        ss.anchor = p.getLocation();
        ss.yaw = yaw;
        ss.total = (int) ((60 + s.difficulty * 40) * (1 - Math.min(0.5, skillLevel(d) * 0.017)) * cfg("time-mult", 1.0));
        sessions.put(p.getUniqueId(), ss);
        Text.msg(p, "&d" + s.label + "&f 조각을 시작합니다. &7(조각칼을 든 채 가까이 있어야 합니다)");
    }

    private void refundSession(Session s, Player p) {
        if (p != null) give(p, s.mat, s.subject.blocks);
    }

    private void sessionTick() {
        for (Iterator<Map.Entry<UUID, Session>> it = sessions.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Session> en = it.next();
            Session s = en.getValue();
            Player p = Bukkit.getPlayer(en.getKey());
            if (p == null || !p.isOnline() || p.isDead()) { it.remove(); continue; }   // 재료는 사라짐 (조각 도중 사망 · 퇴장)
            if (!holdingKnife(p) || !p.getWorld().equals(s.base.getWorld()) || p.getLocation().distanceSquared(s.base) > 25
                    || p.getLocation().distanceSquared(s.anchor) > 9) {
                it.remove();
                refundSession(s, p);
                Text.msg(p, "&c조각이 중단되었습니다. &7(재료는 돌려받았습니다)");
                continue;
            }
            s.ticks += 10;
            BlockData bd = s.mat.createBlockData();
            Location fx = s.base.clone().add(0, 0.2 + s.subject.height() * s.ticks / Math.max(1, s.total), 0);
            p.getWorld().spawnParticle(Particle.BLOCK_CRACK, fx, 12, 0.35, 0.3, 0.35, bd);
            p.getWorld().playSound(fx, bd.getSoundGroup().getHitSound(), 0.8f, 0.9f + ThreadLocalRandom.current().nextFloat() * 0.3f);
            Text.actionBar(p, "&d조각하는 중 &f" + s.subject.label + " " + Text.bar((double) s.ticks / s.total, 20, "&d", "&8") + " &f" + Math.min(100, s.ticks * 100 / Math.max(1, s.total)) + "%");
            if (s.ticks >= s.total) {
                it.remove();
                finish(p, s);
            }
        }
    }

    private int rollGrade(PlayerData d, Subject s, Material m, int moon) {
        int lv = skillLevel(d);
        double score = 10 + lv * 2.5 + Math.min(500, RoyalStat.ART.points(d)) * 0.06 + Math.min(300, RoyalStat.LUCK.points(d)) * 0.02
                + materialBonus(m) + (s == Subject.ICE_DRAGON && isIce(m) ? 6 : 0) - s.difficulty * 8
                + (isMoonlight(d) ? moon * 10 : 0) + ThreadLocalRandom.current().nextDouble(-15, 25);
        int g = score < 10 ? 0 : score < 35 ? 1 : score < 60 ? 2 : score < 85 ? 3 : 4;
        if (g == 4 && lv < 10) g = 3;   // 대작은 중급 이상만
        return g;
    }

    private void finish(Player p, Session s) {
        PlayerData d = plugin.data().get(p);
        int moon = moonlight(s.base);
        boolean moonUsed = isMoonlight(d) && moon > 0;
        int g = rollGrade(d, s.subject, s.mat, moon);
        Sculpture sc = new Sculpture();
        sc.id = Long.toString(System.currentTimeMillis(), 36) + Integer.toString(ThreadLocalRandom.current().nextInt(1296), 36);
        sc.owner = p.getUniqueId();
        sc.ownerName = p.getName();
        sc.subject = s.subject;
        sc.grade = g;
        sc.material = s.mat.name();
        sc.name = s.subject.label;
        sc.world = s.base.getWorld().getName();
        sc.x = s.base.getX();
        sc.y = s.base.getY();
        sc.z = s.base.getZ();
        sc.yaw = s.yaw;
        sc.moon = moonUsed;
        sc.created = System.currentTimeMillis();
        sculptures.put(sc.id, sc);
        build(sc);
        dirty = true;

        double diff = s.subject.difficulty;
        double art = new double[]{0.3, 1, 2, 5, 15}[g] * (1 + 0.2 * diff) * (isMoonlight(d) ? 1.2 : 1);
        long fame = Math.round(new double[]{0, 0, 1, 5, 30}[g] * (1 + 0.25 * (diff - 1)));
        d.addCounter("rr_sculptures_made", 1);
        if (g >= 3) d.addCounter("rr_masterpieces", 1);
        addSkill(p, new double[]{3, 6, 10, 18, 40}[g] * (1 + 0.3 * diff));
        Location top = s.base.clone().add(0, s.subject.height() + 0.3, 0);
        World w = s.base.getWorld();
        w.spawnParticle(Particle.BLOCK_CRACK, s.base.clone().add(0, 0.8, 0), 60, 0.5, 0.6, 0.5, s.mat.createBlockData());
        String msg = "&f조각품 &e「" + sc.name + "」&f을(를) 완성하였습니다. 등급: " + GRADE[g];
        switch (g) {
            case 0 -> Text.msg(p, msg + " &7— 손이 미끄러졌습니다...");
            case 1 -> Text.msg(p, msg);
            case 2 -> { Text.msg(p, msg); w.playSound(top, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.3f); }
            case 3 -> {
                p.sendTitle(Text.c("&d&l명작!"), Text.c("&f「" + sc.name + "」"), 5, 60, 15);
                w.playSound(top, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.1f);
                w.spawnParticle(Particle.FIREWORKS_SPARK, top, 60, 0.6, 0.6, 0.6, 0.08);
                Text.msg(p, msg + " &d— 명작이 탄생했습니다! 이 조각품을 보는 이들에게 힘이 깃듭니다.");
            }
            default -> {
                p.sendTitle(Text.c("&6&l대작!"), Text.c("&f「" + sc.name + "」"), 5, 80, 20);
                w.playSound(top, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.2f, 0.7f);
                w.strikeLightningEffect(s.base.clone().add(2, 0, 2));
                w.spawnParticle(Particle.END_ROD, top, 200, 1.2, 1.5, 1.2, 0.1);
                Text.announce(Text.c("&6[대작] &f조각사 &e" + Text.name(p) + "&f이(가) " + s.subject.label + " 조각상 「" + sc.name + "」을(를) 완성했습니다! &7("
                        + sc.world + " " + (int) sc.x + ", " + (int) sc.y + ", " + (int) sc.z + ") — 근처의 모두에게 축복이 내립니다."));
                for (Player o : Bukkit.getOnlinePlayers()) if (!o.equals(p)) o.playSound(o.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 0.6f);
            }
        }
        if (moonUsed) Text.msg(p, "&b☾ 달빛이 조각칼에 스며들었습니다." + (moon == 2 ? " &7(보름달)" : ""));
        if (plugin.royal() != null) {
            plugin.royal().gain(p, RoyalStat.ART, art);
            if (g >= 3) plugin.royal().gain(p, RoyalStat.CHARM, g == 4 ? 2 : 0.5);
            if (fame > 0) plugin.royal().addFame(p, fame, GRADE[g].replaceAll("&.", "") + " 조각품");
        }
        if (!isMoonlight(d) && moonlightProblems(d).isEmpty())
            Text.msg(p, "&b☾ 조각칼에서 은은한 달빛이 느껴집니다... &7(/조각 → 「달빛 조각사」 전직 가능)");
        naming.put(p.getUniqueId(), sc.id);
        namingUntil.put(p.getUniqueId(), System.currentTimeMillis() + 30_000);
        Text.msg(p, "&e작품의 이름을 채팅으로 입력하세요. &7(30초 · '그대로' 를 입력하면 「" + sc.name + "」)");
    }

    // ------------------------------------------------------------------ 조각상 그리기
    private void build(Sculpture sc) {
        Location base = sc.base();
        if (base == null) return;
        Material mm = Material.matchMaterial(sc.material);
        BlockData bd = (mm == null || !mm.isBlock() ? Material.STONE : mm).createBlockData();
        Quaternionf q = new Quaternionf().rotateY((float) Math.toRadians(-sc.yaw));
        Location at = base.clone();
        at.setYaw(0);
        at.setPitch(0);
        World w = base.getWorld();
        List<double[]> boxes = new ArrayList<>(Arrays.asList(PEDESTAL));
        boxes.addAll(Arrays.asList(sc.subject.boxes));
        double halfWidth = 0.55;
        for (double[] b : boxes) {
            halfWidth = Math.max(halfWidth, Math.max(Math.abs(b[0]), Math.abs(b[3])) / 16.0);
            Vector3f tr = q.transform(new Vector3f((float) (b[0] / 16), (float) (b[1] / 16), (float) (b[2] / 16)));
            Vector3f sc3 = new Vector3f((float) ((b[3] - b[0]) / 16), (float) ((b[4] - b[1]) / 16), (float) ((b[5] - b[2]) / 16));
            BlockDisplay d = w.spawn(at, BlockDisplay.class, x -> {
                x.setBlock(bd);
                x.setTransformation(new Transformation(tr, new Quaternionf(q), sc3, new Quaternionf()));
                tag(x, sc);
                if (sc.moon) {
                    x.setGlowing(true);
                    x.setGlowColorOverride(Color.fromRGB(0xA8D8FF));
                    x.setBrightness(new Display.Brightness(15, 15));
                }
            });
            sc.parts.add(d.getUniqueId());
        }
        double h = sc.subject.height();
        float width = (float) Math.min(2.4, halfWidth * 2);
        Interaction hit = w.spawn(at, Interaction.class, x -> {
            x.setInteractionWidth(width);
            x.setInteractionHeight((float) (h + 0.1));
            x.setResponsive(true);
            tag(x, sc);
        });
        sc.parts.add(hit.getUniqueId());
        TextDisplay label = w.spawn(at.clone().add(0, h + 0.35, 0), TextDisplay.class, x -> {
            x.setBillboard(Display.Billboard.CENTER);
            tag(x, sc);
        });
        sc.parts.add(label.getUniqueId());
        relabel(sc);
    }

    private void tag(Entity x, Sculpture sc) {
        x.setPersistent(true);
        x.getPersistentDataContainer().set(STATUE, PersistentDataType.STRING, sc.id);
    }

    private void relabel(Sculpture sc) {
        for (UUID u : sc.parts)
            if (Bukkit.getEntity(u) instanceof TextDisplay td)
                td.setText(Text.c(GRADE[sc.grade] + " &f「" + sc.name + "」" + (sc.moon ? " &b☾" : "") + "\n&7" + sc.subject.label + " · " + sc.ownerName + " 작"));
    }

    private void unbuild(Sculpture sc) {
        for (UUID u : sc.parts) {
            Entity e = Bukkit.getEntity(u);
            if (e != null) e.remove();
        }
        sc.parts.clear();
        Location b = sc.base();
        if (b != null && b.getWorld().isChunkLoaded(b.getBlockX() >> 4, b.getBlockZ() >> 4))   // 혹시 UUID 를 잃은 조각
            for (Entity e : b.getWorld().getNearbyEntities(b.clone().add(0, 1, 0), 2.5, 3, 2.5))
                if (sc.id.equals(e.getPersistentDataContainer().get(STATUE, PersistentDataType.STRING))) e.remove();
    }

    private void remove(Sculpture sc) {
        unbuild(sc);
        sculptures.remove(sc.id);
        dirty = true;
    }

    /** 철거된 조각품의 남은 조각 (청크가 꺼져 있던 동안 철거된 것) */
    private void cleanOrphans(Collection<? extends Entity> list) {
        for (Entity e : list) {
            String id = e.getPersistentDataContainer().get(STATUE, PersistentDataType.STRING);
            if (id != null && !sculptures.containsKey(id)) e.remove();
        }
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        cleanOrphans(e.getEntities());
    }

    private Sculpture of(Entity e) {
        String id = e == null ? null : e.getPersistentDataContainer().get(STATUE, PersistentDataType.STRING);
        return id == null ? null : sculptures.get(id);
    }

    // ------------------------------------------------------------------ 이름 짓기
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        String sid = naming.get(id);
        if (sid == null) return;
        Long until = namingUntil.get(id);
        if (until == null || until < System.currentTimeMillis()) { naming.remove(id); namingUntil.remove(id); return; }
        e.setCancelled(true);
        naming.remove(id);
        namingUntil.remove(id);
        String name = ChatColor.stripColor(Text.c(e.getMessage())).trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Sculpture sc = sculptures.get(sid);
            if (sc == null) return;
            if (!name.isEmpty() && !name.equals("그대로") && !name.equals("취소")) {
                sc.name = name.length() > 20 ? name.substring(0, 20) : name;
                relabel(sc);
                dirty = true;
            }
            Text.msg(e.getPlayer(), "&f작품명: &e「" + sc.name + "」");
        });
    }

    // ------------------------------------------------------------------ 감상 · 관리
    @EventHandler(priority = EventPriority.HIGH)
    public void onStatueClick(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof Interaction it)) return;
        Sculpture sc = of(it);
        if (sc == null) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        if (p.isSneaking() && holdingKnife(p) && (sc.owner.equals(p.getUniqueId()) || p.hasPermission("rpgcraft.admin"))) openStatue(p, sc);
        else appreciate(p, sc);
    }

    private void appreciate(Player p, Sculpture sc) {
        String key = p.getUniqueId() + ":" + sc.id;
        long now = System.currentTimeMillis();
        if (seenCooldown.getOrDefault(key, 0L) > now) return;
        seenCooldown.put(key, now + 30_000);
        PlayerData d = plugin.data().get(p);
        boolean first = d.counter("rr_seen_" + sc.id) == 0 && !sc.owner.equals(p.getUniqueId());
        p.sendMessage(Text.c("&8&m                              "));
        p.sendMessage(Text.c(" " + GRADE[sc.grade] + " &f「" + sc.name + "」 &7— " + sc.subject.label + " · " + sc.ownerName + " 작" + (sc.moon ? " &b☾ 달빛" : "")));
        String[] words = {"&7투박하고 어설픈 솜씨다.", "&7평범한 조각품이다.", "&f정성이 느껴지는 조각품이다.", "&d보는 이의 마음을 울리는 명작이다.", "&6숨이 멎을 듯한 대작이다. 살아 숨 쉬는 것만 같다."};
        p.sendMessage(Text.c(" " + words[sc.grade]));
        if (sc.grade >= 2) {
            int dur = new int[]{0, 0, 10, 30, 60}[sc.grade];
            boolean better = d.counter("rr_buff_until") < now || d.counter("rr_buff_grade") <= sc.grade;
            if (better) {
                d.counters.put("rr_buff_until", (double) (now + dur * 60_000L));
                d.counters.put("rr_buff_subj", (double) sc.subject.ordinal());
                d.counters.put("rr_buff_grade", (double) sc.grade);
                plugin.stats().refresh(p);
                p.sendMessage(Text.c(" &a감상 효과: &f" + sc.subject.buffStat.label + " +" + Text.num(sc.subject.buffValues[sc.grade - 2])
                        + (sc.subject.buffStat.pct ? "%" : "") + " &7(" + dur + "분)"));
            } else p.sendMessage(Text.c(" &7이미 더 좋은 조각품의 감흥이 남아 있습니다."));
        }
        p.sendMessage(Text.c("&8&m                              "));
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.8f + sc.grade * 0.15f);
        if (first) {
            d.counters.put("rr_seen_" + sc.id, 1.0);
            if (plugin.royal() != null) plugin.royal().gain(p, RoyalStat.ART, new double[]{0.05, 0.1, 0.3, 0.8, 2.5}[sc.grade]);
        }
    }

    private void openStatue(Player p, Sculpture sc) {
        PlayerData d = plugin.data().get(p);
        boolean owner = sc.owner.equals(p.getUniqueId());
        Gui g = new Gui(3, "&8조각품 · " + sc.name) {
        };
        g.set(4, Gui.button(sc.subject.icon, GRADE[sc.grade] + " &f「" + sc.name + "」", "&7" + sc.subject.label + " · " + sc.ownerName + " 작",
                "&7재료: " + sc.material.toLowerCase(Locale.ROOT).replace('_', ' '), sc.moon ? "&b☾ 달빛 조각품" : ""), null);
        g.set(10, Gui.button(Material.NAME_TAG, "&e이름 바꾸기", "&7채팅으로 새 이름을 입력합니다 (30초)"), e -> {
            p.closeInventory();
            naming.put(p.getUniqueId(), sc.id);
            namingUntil.put(p.getUniqueId(), System.currentTimeMillis() + 30_000);
            Text.msg(p, "&e새 작품명을 채팅으로 입력하세요.");
        });
        boolean moon = isMoonlight(d);
        int lifeLv = plugin.getConfig().getInt("royal-road.life.level-cost", 1);
        double lifeArt = 5.0 * sc.grade;
        if (sc.subject.canLive()) {
            List<String> lore = new ArrayList<>(List.of("&7조각품에 생명을 불어넣어 함께 싸우는 동료로 만듭니다.",
                    "&7역할: &f" + sc.subject.role.label + " &7— " + sc.subject.role.desc,
                    "&7대가: &c레벨 -" + lifeLv + " · 예술 -" + (int) lifeArt, "&7조건: 달빛 조각사 · 수작 이상"));
            boolean ok = owner && moon && sc.grade >= 2;
            lore.add(ok ? "&e쉬프트 클릭으로 생명부여" : "&c조건이 맞지 않습니다");
            g.set(12, Gui.button(ok ? Material.TOTEM_OF_UNDYING : Material.GRAY_DYE, "&b&l조각 생명부여", lore.toArray(new String[0])),
                    ok ? e -> { if (e.isShiftClick()) { p.closeInventory(); bestow(p, sc, lifeLv, lifeArt); } } : null);
        } else g.set(12, Gui.button(Material.GRAY_DYE, "&7조각 생명부여", "&8이 주제는 생명을 불어넣을 수 없습니다."), null);
        int art = RoyalStat.ART.points(d);
        double cost = Math.max(5, Math.floor(art * 0.05));
        double pct = Math.min(40, (sc.grade + 1) * 4 + art * 0.01);
        int mins = Math.max(20, sc.grade * 30);
        boolean canDestroy = owner && moon;
        g.set(14, Gui.button(canDestroy ? Material.TNT : Material.GRAY_DYE, "&c&l조각 파괴술",
                        "&7내 조각품을 부숴 그 예술혼을 힘으로 바꿉니다.", "&7효과: &f힘 · 민첩 · 모험 +" + String.format("%.1f", pct) + "% &7(" + mins + "분)",
                        "&7대가: &c예술 -" + (int) cost + " &7· 조각품이 사라짐", canDestroy ? "&e쉬프트 클릭으로 파괴" : "&c달빛 조각사 전용"),
                canDestroy ? e -> { if (e.isShiftClick()) { p.closeInventory(); destroy(p, sc, cost, pct, mins); } } : null);
        g.set(16, Gui.button(Material.BARRIER, "&7철거하기", "&7조각품을 치우고 재료를 돌려받습니다.", "&e쉬프트 클릭"), e -> {
            if (!e.isShiftClick()) return;
            p.closeInventory();
            remove(sc);
            Material m = Material.matchMaterial(sc.material);
            if (owner && m != null) give(p, m, sc.subject.blocks);
            Text.msg(p, "&7조각품 「" + sc.name + "」을(를) 철거했습니다.");
        });
        g.fill(0, 26);
        g.open(p);
    }

    private void destroy(Player p, Sculpture sc, double cost, double pct, int mins) {
        if (!sculptures.containsKey(sc.id)) return;
        PlayerData d = plugin.data().get(p);
        Location b = sc.base();
        remove(sc);
        d.counters.put(RoyalStat.ART.key(), Math.max(0, RoyalStat.ART.raw(d) - cost));
        d.counters.put("rr_destroy_until", (double) (System.currentTimeMillis() + mins * 60_000L));
        d.counters.put("rr_destroy_pct", pct);
        plugin.stats().refresh(p);
        if (b != null) {
            Material m = Material.matchMaterial(sc.material);
            b.getWorld().spawnParticle(Particle.BLOCK_CRACK, b.clone().add(0, 0.8, 0), 120, 0.6, 0.8, 0.6, (m == null ? Material.STONE : m).createBlockData());
            b.getWorld().playSound(b, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.3f);
        }
        p.sendTitle(Text.c("&c&l조각 파괴술"), Text.c("&f힘 · 민첩 · 모험 +" + String.format("%.1f", pct) + "%"), 5, 40, 10);
        Text.msg(p, "&c「" + sc.name + "」이(가) 부서지며 예술혼이 몸에 깃듭니다. &7(" + mins + "분 · 예술 -" + (int) cost + ")");
    }

    // ------------------------------------------------------------------ 조각 생명부여
    private int maxLives(PlayerData d) {
        return Math.min(5, 1 + RoyalStat.LEADERSHIP.points(d) / 40);
    }

    private void bestow(Player p, Sculpture sc, int lvCost, double artCost) {
        if (!sculptures.containsKey(sc.id)) return;
        PlayerData d = plugin.data().get(p);
        LifeBook b = book(p.getUniqueId());
        if (b.list.size() >= maxLives(d)) { Text.msg(p, "&c생명체는 지금 " + maxLives(d) + "마리까지 데리고 있을 수 있습니다. &7(통솔력 40마다 +1, 최대 5)"); return; }
        int minLv = plugin.getConfig().getInt("royal-road.life.min-level", 30);
        if (d.level - lvCost < minLv) { Text.msg(p, "&c생명부여의 대가(레벨 " + lvCost + ")를 치르려면 Lv." + (minLv + lvCost) + " 이상이어야 합니다."); return; }
        if (RoyalStat.ART.raw(d) < artCost) { Text.msg(p, "&c예술 스탯이 모자랍니다. (" + (int) artCost + " 필요)"); return; }
        Location at = sc.base();
        remove(sc);
        d.level -= lvCost;
        d.exp = 0;
        d.counters.put(RoyalStat.ART.key(), RoyalStat.ART.raw(d) - artCost);
        int perLevel = plugin.getConfig().getInt("player.stat-per-level", 5);
        int pts = lvCost * perLevel, fromFree = Math.min(pts, d.statPoints);
        d.statPoints -= fromFree;
        if (pts > fromFree) d.addCounter("rr_stat_debt", pts - fromFree);
        Life l = new Life();
        l.subject = sc.subject;
        l.name = sc.name;
        l.grade = sc.grade;
        b.list.add(l);
        writeBook(p.getUniqueId());
        plugin.stats().refresh(p);
        if (at != null) {
            at.getWorld().spawnParticle(Particle.END_ROD, at.clone().add(0, 1, 0), 150, 0.6, 1.2, 0.6, 0.08);
            at.getWorld().spawnParticle(Particle.TOTEM, at.clone().add(0, 1, 0), 80, 0.5, 1, 0.5, 0.3);
            at.getWorld().playSound(at, Sound.ITEM_TOTEM_USE, 1f, 1.2f);
        }
        p.sendTitle(Text.c("&b&l조각 생명부여"), Text.c("&f「" + l.name + "」에게 생명이 깃들었습니다"), 10, 70, 20);
        Text.msg(p, "&b「" + l.name + "」&f이(가) 살아 움직이기 시작합니다. &7(레벨 -" + lvCost + " · 예술 -" + (int) artCost + " · /생명체 로 부르기)");
        if (b.out && at != null) spawnLife(p, b, l, at);
    }

    public boolean hasLifeOut(Player p) {
        LifeBook b = books.get(p.getUniqueId());
        return b != null && b.out;
    }

    private double leadMult(Player owner) {
        return 1 + Math.min(2.0, RoyalStat.LEADERSHIP.points(plugin.data().get(owner)) * 0.01);
    }

    private double lifeMaxHp(Player owner, Life l) {
        return plugin.data().get(owner).stats.maxHp * 0.6 * l.subject.hpMult * LIFE_GRADE_MULT[l.grade] * (1 + 0.03 * l.lv) * leadMult(owner);
    }

    private double lifeAttack(Player owner, Life l) {
        var s = plugin.data().get(owner).stats;
        return Math.max(s.attack, s.magic * 0.5) * 0.3 * l.subject.atkMult * LIFE_GRADE_MULT[l.grade] * (1 + 0.03 * l.lv) * leadMult(owner);
    }

    private void toggleLives(Player p) {
        LifeBook b = book(p.getUniqueId());
        if (b.out) { dismiss(p.getUniqueId(), p, "&7생명체들을 쉬게 했습니다."); return; }
        long now = System.currentTimeMillis();
        int n = 0;
        b.out = true;
        for (Life l : b.list) {
            if (l.downUntil > now) continue;
            double a = Math.PI * 2 * n++ / Math.max(1, b.list.size());
            spawnLife(p, b, l, p.getLocation().add(Math.cos(a) * 2, 0.2, Math.sin(a) * 2));
        }
        if (n == 0) { b.out = false; Text.msg(p, "&c부를 수 있는 생명체가 없습니다."); return; }
        Text.msg(p, "&b생명체 " + n + "마리를 불렀습니다.");
    }

    private void spawnLife(Player p, LifeBook b, Life l, Location at) {
        Wolf w = p.getWorld().spawn(at, Wolf.class, x -> {
            x.setTamed(true);
            x.setOwner(p);
            x.setAdult();
            x.setSilent(true);
            x.setPersistent(false);
            x.setRemoveWhenFarAway(false);
            x.setCanPickupItems(false);
            x.setCollarColor(DyeColor.LIGHT_BLUE);
            x.getPersistentDataContainer().set(LIFE, PersistentDataType.STRING, p.getUniqueId().toString());
            x.setCustomNameVisible(true);
        });
        AttributeInstance sp = w.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (sp != null) sp.setBaseValue(l.subject.role == Subject.Role.SKIRMISHER ? 0.38 : 0.32);
        l.entity = w.getUniqueId();
        l.maxHp = lifeMaxHp(p, l);
        l.hp = l.maxHp;
        l.nextAct = 0;
        lifeOwners.put(w.getUniqueId(), p.getUniqueId());
        rename(w, l);
        if (plugin.mobModels() != null) plugin.mobModels().attach(w, l.subject.model, l.subject.modelHeight, false);
        w.getWorld().spawnParticle(Particle.END_ROD, at.clone().add(0, 0.6, 0), 20, 0.4, 0.6, 0.4, 0.03);
    }

    private void rename(LivingEntity w, Life l) {
        int bars = 10, fill = (int) Math.ceil(Math.max(0, l.hp) / Math.max(1, l.maxHp) * bars);
        w.setCustomName(Text.c("&b✦ " + l.name + " &7Lv." + l.lv + " &a" + "|".repeat(fill) + "&8" + "|".repeat(bars - fill)));
    }

    private void dismiss(UUID owner, Player p, String msg) {
        LifeBook b = books.get(owner);
        if (b == null || !b.out) return;
        b.out = false;
        for (Life l : b.list) {
            if (l.entity == null) continue;
            Entity e = Bukkit.getEntity(l.entity);
            if (e != null) {
                e.getWorld().spawnParticle(Particle.END_ROD, e.getLocation().add(0, 0.6, 0), 10, 0.3, 0.4, 0.3, 0.02);
                e.remove();
            }
            lifeOwners.remove(l.entity);
            l.entity = null;
        }
        if (p != null && msg != null) Text.msg(p, msg);
        writeBook(owner);
    }

    private Life life(Entity e) {
        if (!isLife(e)) return null;
        UUID owner = lifeOwners.get(e.getUniqueId());
        LifeBook b = owner == null ? null : books.get(owner);
        if (b == null) return null;
        for (Life l : b.list) if (e.getUniqueId().equals(l.entity)) return l;
        return null;
    }

    private void fall(Player owner, Life l, LivingEntity w) {
        l.downUntil = System.currentTimeMillis() + (long) (plugin.getConfig().getDouble("royal-road.life.revive-minutes", 3) * 60_000);
        l.entity = null;
        lifeOwners.remove(w.getUniqueId());
        w.getWorld().spawnParticle(Particle.BLOCK_CRACK, w.getLocation().add(0, 0.6, 0), 40, 0.4, 0.5, 0.4, Material.STONE.createBlockData());
        w.getWorld().playSound(w.getLocation(), Sound.BLOCK_STONE_BREAK, 1f, 0.7f);
        w.remove();
        if (owner != null) Text.msg(owner, "&c생명체 「" + l.name + "」이(가) 쓰러져 잠시 조각상으로 돌아갔습니다. &7("
                + (int) plugin.getConfig().getDouble("royal-road.life.revive-minutes", 3) + "분 뒤 다시 부를 수 있음)");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onLifeDamage(EntityDamageEvent e) {
        Entity victim = e.getEntity();
        if (isLife(victim)) {
            e.setCancelled(true);
            Life l = life(victim);
            if (l == null) { victim.remove(); return; }
            Player owner = Bukkit.getPlayer(lifeOwners.getOrDefault(victim.getUniqueId(), new UUID(0, 0)));
            if (e.getCause() == EntityDamageEvent.DamageCause.VOID) { fall(owner, l, (LivingEntity) victim); return; }
            if (!(e instanceof EntityDamageByEntityEvent ev)) return;
            Entity src = ev.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Entity sh ? sh : ev.getDamager();
            if (!(src instanceof LivingEntity mob) || src instanceof Player || isLife(src)) return;
            double dmg = plugin.combat().mobDamage(mob) * (ev.getDamager() instanceof Projectile ? 0.8 : 1);
            if (l.subject.role == Subject.Role.GUARDIAN) dmg *= 0.7;
            l.hp -= dmg;
            plugin.combat().indicator((LivingEntity) victim, dmg, false);
            ((LivingEntity) victim).playEffect(EntityEffect.HURT);
            if (l.hp <= 0) fall(owner, l, (LivingEntity) victim);
            else rename((LivingEntity) victim, l);
            return;
        }
        if (e instanceof EntityDamageByEntityEvent ev && isLife(ev.getDamager()) && victim instanceof LivingEntity target) {
            e.setCancelled(true);
            Life l = life(ev.getDamager());
            Player owner = Bukkit.getPlayer(lifeOwners.getOrDefault(ev.getDamager().getUniqueId(), new UUID(0, 0)));
            if (l == null || owner == null || isLife(target)) return;
            strike(owner, l, (LivingEntity) ev.getDamager(), target);
        }
    }

    private void strike(Player owner, Life l, LivingEntity self, LivingEntity target) {
        if (!plugin.combat().isEnemy(owner, target)) return;
        double dmg = lifeAttack(owner, l);
        if (l.subject.role == Subject.Role.SKIRMISHER && ThreadLocalRandom.current().nextDouble() < 0.3) {
            dmg *= 1.8;
            target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 10, 0.3, 0.4, 0.3, 0.1);
        }
        if (plugin.mobModels() != null) plugin.mobModels().attackPose(self);
        plugin.combat().dealSkillDamage(owner, target, dmg, false);
        if (l.subject.role == Subject.Role.CHARGER) plugin.combat().knockback(target, self.getLocation(), 1.5);
        if (target instanceof Mob mob && target.isValid() && !target.isDead()
                && ThreadLocalRandom.current().nextDouble() < (l.subject.role == Subject.Role.GUARDIAN ? 1.0 : 0.3)) mob.setTarget(self);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLifeInteract(PlayerInteractEntityEvent e) {
        if (isLife(e.getRightClicked())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onOwnerKill(EntityDeathEvent e) {
        if (isLife(e.getEntity())) { e.getDrops().clear(); e.setDroppedExp(0); return; }
        Player k = e.getEntity().getKiller();
        if (k == null || e.getEntity() instanceof Player) return;
        LifeBook b = books.get(k.getUniqueId());
        if (b == null || !b.out) return;
        boolean boss = e.getEntity().getPersistentDataContainer().has(kr.rpgcraft.Keys.BOSS, PersistentDataType.STRING);
        for (Life l : b.list) {
            if (l.entity == null || l.lv >= 50) continue;
            l.exp += boss ? 25 : 1;
            while (l.lv < 50 && l.exp >= 15 + l.lv * 10) {
                l.exp -= 15 + l.lv * 10;
                l.lv++;
                Text.msg(k, "&b생명체 「" + l.name + "」의 레벨이 올랐습니다! &7(Lv." + l.lv + ")");
                if (Bukkit.getEntity(l.entity) instanceof LivingEntity le) {
                    l.maxHp = lifeMaxHp(k, l);
                    l.hp = l.maxHp;
                    le.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, le.getLocation().add(0, 1, 0), 12, 0.4, 0.5, 0.4);
                }
            }
        }
        dirty = true;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        Session s = sessions.remove(id);
        if (s != null) refundSession(s, e.getPlayer());
        dismiss(id, null, null);
        writeBook(id);
        books.remove(id);
        naming.remove(id);
        namingUntil.remove(id);
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent e) {
        dismiss(e.getPlayer().getUniqueId(), e.getPlayer(), "&7월드를 옮겨 생명체들이 쉬러 갔습니다.");
    }

    @EventHandler
    public void onOwnerDeath(PlayerDeathEvent e) {
        dismiss(e.getEntity().getUniqueId(), e.getEntity(), "&7주인이 쓰러져 생명체들이 쉬러 갔습니다.");
    }

    private boolean foe(Player owner, Entity e) {
        return e instanceof LivingEntity le && !(e instanceof Player) && !isLife(e) && !e.isDead()
                && (le instanceof Enemy || plugin.mobs().tracked(le)) && plugin.combat().isEnemy(owner, le);
    }

    private LivingEntity nearestFoe(Player owner, Location from, double r) {
        LivingEntity best = null;
        double bd = r * r;
        for (Entity e : from.getWorld().getNearbyEntities(from, r, 6, r)) {
            if (!foe(owner, e)) continue;
            double d = e.getLocation().distanceSquared(from);
            if (d < bd) { bd = d; best = (LivingEntity) e; }
        }
        return best;
    }

    // ------------------------------------------------------------------ 주기 작업
    private void tick() {
        tick++;
        sessionTick();
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, LifeBook> en : new ArrayList<>(books.entrySet())) {
            LifeBook b = en.getValue();
            if (!b.out) continue;
            Player p = Bukkit.getPlayer(en.getKey());
            if (p == null || !p.isOnline() || p.isDead() || p.getGameMode() == GameMode.SPECTATOR) { dismiss(en.getKey(), p, null); continue; }
            boolean any = false;
            for (Life l : b.list) {
                if (l.entity == null) continue;
                if (!(Bukkit.getEntity(l.entity) instanceof Wolf w) || !w.isValid()) { lifeOwners.remove(l.entity); l.entity = null; continue; }
                any = true;
                if (!w.getWorld().equals(p.getWorld()) || w.getLocation().distanceSquared(p.getLocation()) > 24 * 24) {
                    Location to = p.getLocation().add(ThreadLocalRandom.current().nextDouble(-2, 2), 0.2, ThreadLocalRandom.current().nextDouble(-2, 2));
                    if (plugin.mobModels() == null || !plugin.mobModels().teleport(w, to)) w.teleport(to);
                    w.setTarget(null);
                    continue;
                }
                w.setSitting(false);
                double newMax = lifeMaxHp(p, l);
                if (Math.abs(newMax - l.maxHp) > 1) { l.hp = l.hp / Math.max(1, l.maxHp) * newMax; l.maxHp = newMax; }
                if (tick % 4 == 0 && l.hp < l.maxHp) l.hp = Math.min(l.maxHp, l.hp + l.maxHp * 0.01);
                rename(w, l);
                if (l.subject.role != Subject.Role.RANGER) {
                    LivingEntity t = w.getTarget();
                    if (t == null || !t.isValid() || t.isDead() || t.getLocation().distanceSquared(p.getLocation()) > 20 * 20) w.setTarget(nearestFoe(p, p.getLocation(), 12));
                    if (l.subject.role == Subject.Role.GUARDIAN && now >= l.nextAct) {
                        l.nextAct = now + 3000;
                        for (Entity x : w.getNearbyEntities(7, 4, 7))
                            if (x instanceof Mob mob && foe(p, x) && p.equals(mob.getTarget())) mob.setTarget(w);
                    }
                    continue;
                }
                w.setTarget(null);
                if (now < l.nextAct) continue;
                LivingEntity f = nearestFoe(p, w.getLocation(), 16);
                if (f == null) continue;
                l.nextAct = now + 1500;
                Location from = w.getLocation().add(0, 1.2, 0), to = f.getLocation().add(0, f.getHeight() * 0.5, 0);
                org.bukkit.util.Vector dir = to.toVector().subtract(from.toVector());
                double len = dir.length();
                dir.normalize().multiply(0.5);
                Location c = from.clone();
                for (double t = 0; t < len; t += 0.5) {
                    w.getWorld().spawnParticle(Particle.SWEEP_ATTACK, c, 1, 0, 0, 0, 0);
                    c.add(dir);
                }
                w.getWorld().playSound(from, Sound.ENTITY_PHANTOM_FLAP, 0.8f, 1.4f);
                if (plugin.mobModels() != null) plugin.mobModels().attackPose(w);
                strike(p, l, w, f);
            }
            if (!any) { b.out = false; writeBook(en.getKey()); }
        }
        if (tick % 20 == 0) auraTick(now);
        if (tick % 120 == 0) {
            namingUntil.entrySet().removeIf(en -> {
                if (en.getValue() >= now) return false;
                naming.remove(en.getKey());
                return true;
            });
            seenCooldown.values().removeIf(v -> v < now);
        }
    }

    /** 대작: 근처 16칸 안의 모두에게 저절로 감상 버프 (10초마다) */
    private void auraTick(long now) {
        for (Sculpture sc : sculptures.values()) {
            if (sc.grade < 4) continue;
            World w = Bukkit.getWorld(sc.world);
            if (w == null || !w.isChunkLoaded(((int) Math.floor(sc.x)) >> 4, ((int) Math.floor(sc.z)) >> 4)) continue;
            Location l = new Location(w, sc.x, sc.y, sc.z);
            for (Player p : w.getPlayers()) {
                if (p.getLocation().distanceSquared(l) > 16 * 16) continue;
                PlayerData d = plugin.data().get(p);
                boolean active = d.counter("rr_buff_until") > now;
                if (active && d.counter("rr_buff_grade") >= 4 && d.counter("rr_buff_until") - now > 4 * 60_000) continue;
                if (active && d.counter("rr_buff_grade") < 4 && d.counter("rr_buff_subj") != sc.subject.ordinal() && d.counter("rr_buff_until") - now > 5 * 60_000) continue;
                d.counters.put("rr_buff_until", (double) (now + 5 * 60_000L));
                d.counters.put("rr_buff_subj", (double) sc.subject.ordinal());
                d.counters.put("rr_buff_grade", 4.0);
                plugin.stats().refresh(p);
                if (!active) Text.actionBar(p, "&6대작 「" + sc.name + "」의 기운: &f" + sc.subject.buffStat.label + " ↑");
            }
        }
    }

    // ------------------------------------------------------------------ 메뉴
    public void openMenu(Player p) {
        PlayerData d = plugin.data().get(p);
        Gui g = new Gui(5, "&8조각술") {
        };
        int mine = 0;
        for (Sculpture s : sculptures.values()) if (s.owner.equals(p.getUniqueId())) mine++;
        g.set(4, Gui.button(Material.FLINT, "&d&l조각술 &f" + skillLabel(d),
                "&7예술 &f" + RoyalStat.ART.points(d) + " &7· 만든 조각품 &f" + (int) d.counter("rr_sculptures_made") + " &7· 명작 이상 &f" + (int) d.counter("rr_masterpieces"),
                "&7세워 둔 조각품 &f" + mine + " / " + plugin.getConfig().getInt("royal-road.sculpt.max-per-player", 12),
                "", "&e조각칼을 들고 웅크린 채 땅을 우클릭 &7→ 주제 · 재료 고르기 → 몇 초 동안 깎기",
                "&7등급: 졸작 · 평작 · 수작 · 명작 · 대작", "&7수작 이상은 감상(우클릭)하면 버프, 대작은 근처 모두에게 버프",
                "&7처음 보는 남의 조각품을 감상하면 예술 ↑"), null);
        g.set(10, Gui.button(Material.FLINT, "&f조각칼 받기", "&7조각을 하려면 조각칼이 필요합니다."), e -> {
            for (ItemStack it : p.getInventory().getContents()) if (isKnife(it)) { Text.msg(p, "&7이미 조각칼을 가지고 있습니다."); return; }
            give(p, knife());
        });
        boolean moon = isMoonlight(d);
        List<String> ml = new ArrayList<>(List.of("&7전설의 히든 클래스", "&b달빛 조각술 &7밤 · 하늘 아래 조각하면 품질 ↑(보름달 더), 조각품이 빛남",
                "&b조각 검술 &7조각칼을 무기로 (공격력 + 예술 × 2)", "&b조각 파괴술 &7내 조각품을 부숴 힘 · 민첩 · 모험 ↑",
                "&b조각 생명부여 &7조각품을 살아 있는 동료로", ""));
        List<String> prob = moonlightProblems(d);
        if (moon) ml.add("&a전직 완료");
        else if (prob.isEmpty()) ml.add("&e클릭해 전직");
        else { ml.add("&c조건:"); for (String x : prob) ml.add("&c - " + x); }
        g.set(12, Gui.button(moon ? Material.SEA_LANTERN : Material.LANTERN, "&b&l☾ 달빛 조각사", ml.toArray(new String[0])), moon ? null : e -> { p.closeInventory(); becomeMoonlight(p); });
        g.set(14, Gui.button(Material.BONE, "&a생명체", "&7조각 생명부여로 태어난 동료 (/생명체)"), e -> openLife(p));
        List<String> list = new ArrayList<>();
        for (Sculpture s : sculptures.values())
            if (s.owner.equals(p.getUniqueId()) && list.size() < 14)
                list.add(GRADE[s.grade] + " &f" + s.name + " &7(" + s.world + " " + (int) s.x + ", " + (int) s.y + ", " + (int) s.z + ")");
        if (list.isEmpty()) list.add("&7아직 없습니다");
        list.add("");
        list.add("&7관리: 조각칼을 들고 웅크린 채 조각품 우클릭");
        g.set(16, Gui.button(Material.ITEM_FRAME, "&e내 조각품", list.toArray(new String[0])), null);
        double mult = cfg("material-price-mult", 1.0);
        int slot = 27;
        for (Offer o : OFFERS) {
            long price = Math.round(o.price() * mult);
            g.set(slot++, Gui.button(o.mat(), "&f" + o.label(), "&7재료 품질 &a+" + materialBonus(o.mat()), "&7가격 &e" + Text.money(price),
                    "&e좌클릭 1개 · 쉬프트 클릭 8개"), e -> {
                int n = e.isShiftClick() ? 8 : 1;
                if (!plugin.economy().take(p, price * n)) { Text.msg(p, "&c소지금이 부족합니다. (" + Text.money(price * n) + ")"); return; }
                give(p, o.mat(), n);
                Text.actionBar(p, "&a구매: " + o.label() + " x" + n + " &7(-" + Text.money(price * n) + ")");
            });
        }
        g.fill(0, 44);
        g.open(p);
    }

    private void give(Player p, ItemStack it) {
        for (ItemStack left : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
        Text.msg(p, "&f조각칼을 받았습니다.");
    }

    public void openLife(Player p) {
        PlayerData d = plugin.data().get(p);
        LifeBook b = book(p.getUniqueId());
        Gui g = new Gui(3, "&8생명체") {
        };
        g.set(4, Gui.button(Material.TOTEM_OF_UNDYING, "&b&l생명체 &f" + b.list.size() + " / " + maxLives(d),
                "&7달빛 조각사가 조각품에 생명을 불어넣은 동료", "&7주인의 공격력 · 체력과 통솔력에 비례해 강해지고, 함께 사냥하면 레벨이 오릅니다.",
                "&7만들기: 조각칼을 들고 웅크린 채 내 조각품 우클릭 → 조각 생명부여"), null);
        g.set(22, Gui.button(b.out ? Material.RED_CONCRETE : Material.LIME_CONCRETE, b.out ? "&c모두 쉬게 하기" : "&a모두 부르기"), e -> { p.closeInventory(); toggleLives(p); });
        long now = System.currentTimeMillis();
        int slot = 11;
        for (Life l : new ArrayList<>(b.list)) {
            if (slot > 15) break;
            String state = l.entity != null ? "&a함께 있음" : l.downUntil > now ? "&c쓰러짐 (" + ((l.downUntil - now) / 1000 + 1) + "초)" : "&7쉬는 중";
            g.set(slot++, Gui.button(l.subject.icon, "&b" + l.name + " &7Lv." + l.lv, "&7" + l.subject.label + " · " + GRADE[l.grade],
                    "&7역할: &f" + l.subject.role.label + " &7— " + l.subject.role.desc, "&7경험치 " + (int) l.exp + " / " + (15 + l.lv * 10), state,
                    "", "&8쉬프트 우클릭 두 번: 놓아주기"), e -> {
                if (!e.isShiftClick() || !e.isRightClick()) return;
                Long c = releaseConfirm.get(p.getUniqueId());
                if (c == null || c < System.currentTimeMillis()) {
                    releaseConfirm.put(p.getUniqueId(), System.currentTimeMillis() + 5000);
                    Text.msg(p, "&c정말 「" + l.name + "」을(를) 놓아줄까요? 5초 안에 한 번 더 쉬프트 우클릭하세요.");
                    return;
                }
                releaseConfirm.remove(p.getUniqueId());
                if (l.entity != null && Bukkit.getEntity(l.entity) != null) Bukkit.getEntity(l.entity).remove();
                if (l.entity != null) lifeOwners.remove(l.entity);
                b.list.remove(l);
                writeBook(p.getUniqueId());
                Text.msg(p, "&7「" + l.name + "」이(가) 자유를 찾아 떠났습니다.");
                openLife(p);
            });
        }
        g.fill(0, 26);
        g.open(p);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { Text.msg(sender, "&c게임 안에서만 쓸 수 있습니다."); return true; }
        if (cmd.getName().equals("life")) { openLife(p); return true; }
        if (args.length > 0 && args[0].equals("칼")) {
            for (ItemStack it : p.getInventory().getContents()) if (isKnife(it)) { Text.msg(p, "&7이미 조각칼을 가지고 있습니다."); return true; }
            give(p, knife());
            return true;
        }
        if (args.length > 0 && args[0].equals("철거") && p.hasPermission("rpgcraft.admin")) {   // 관리자: 가장 가까운 조각품 철거
            Sculpture best = null;
            double bd = 36;
            for (Sculpture s : sculptures.values()) {
                Location b = s.base();
                if (b == null || !b.getWorld().equals(p.getWorld())) continue;
                double dd = b.distanceSquared(p.getLocation());
                if (dd < bd) { bd = dd; best = s; }
            }
            if (best == null) { Text.msg(p, "&c6칸 안에 조각품이 없습니다."); return true; }
            remove(best);
            Text.msg(p, "&a조각품 「" + best.name + "」(" + best.ownerName + ")을(를) 철거했습니다.");
            return true;
        }
        if (args.length >= 3 && args[0].equals("숙련") && p.hasPermission("rpgcraft.admin")) {   // /조각 숙련 <플레이어> <경험치>
            Player t = Text.player(args[1]);
            if (t == null) { Text.msg(p, "&c접속 중인 플레이어가 아닙니다."); return true; }
            addSkill(t, Text.parseDouble(args[2], 0));
            Text.msg(p, "&a" + t.getName() + " 조각술: " + skillLabel(plugin.data().get(t)));
            return true;
        }
        openMenu(p);
        return true;
    }
}
