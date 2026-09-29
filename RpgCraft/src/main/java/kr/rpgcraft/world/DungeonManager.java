package kr.rpgcraft.world;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.feature.PartyManager;
import kr.rpgcraft.mob.CustomMobManager;
import kr.rpgcraft.mob.MobManager;
import kr.rpgcraft.mob.MonsterTierManager;
import kr.rpgcraft.util.Fx;
import kr.rpgcraft.util.Text;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.*;

/**
 * 대형 던전.
 * 지상에는 입구(석조 문 + 이름표), 그 아래 깊은 곳에 방 4개짜리 던전이 자동으로 지어진다.
 *  1번 방: 일반 몬스터 무리 → 2번 방: 정예 → 3번 방: 중간 보스 → 4번 방: 최종 보스
 * 방을 모두 정리하면 다음 방의 창살 문이 열린다. 제한 시간 안에 최종 보스를 쓰러뜨리면 클리어 보상.
 * - 솔로 가능. 파티는 함께 입장하며 인원만큼 몬스터 수·정예 비율이 늘어난다 (체력 곱셈이 아닌 구성 변화)
 * - 클리어 보상은 던전마다 하루 1번 (재입장·재접속·파티 이동으로 중복 수령 불가)
 * - 던전 안에서 죽거나 나가면 입구로 돌아간다
 */
public class DungeonManager implements Listener {
    public static final String[] TIER_NAME = {"", "버려진 지하 묘소", "잊힌 드워프 광산", "가라앉은 심해 신전", "심연의 성채",
            "얼어붙은 왕좌", "화산의 심장", "공허의 문", "뇌신의 제단", "태초의 둥지"};
    public static final String[] TIER_BOSS = {"", "witch", "dwarf_king", "sea_gatekeeper", "bungbung", "frost_queen", "volcano_giant", "void_apostle", "thunder_god", "primordial_dragon"};
    private static final int[] TIER_LEVEL = {0, 20, 45, 75, 100, 130, 170, 210, 250, 300};
    private static final int ROOM = 15, GAP = 24, H = 9;

    public static class Dungeon {
        public String id, name;
        public int tier;
        public Location entrance, interior;
    }

    public class Run {
        final Dungeon d;
        final Set<UUID> players = new LinkedHashSet<>();
        final Set<UUID> mobs = new HashSet<>();
        final Map<UUID, Integer> deaths = new HashMap<>();
        final Set<UUID> eliminated = new HashSet<>();   // 데스 한도로 탈락한 사람 — 이번 공략에 다시 못 들어옴 (v5.4.30)
        int room = -1;
        long deadline, nextAt;
        UUID boss;
        BossBar bar;
        boolean done;

        Run(Dungeon d) {
            this.d = d;
        }
    }

    private final RpgCraft plugin;
    private final Map<String, Dungeon> dungeons = new LinkedHashMap<>();
    private final Map<String, Run> runs = new HashMap<>();
    private final File file;
    private final NamespacedKey KEY;
    private final Random rnd = new Random();

    public DungeonManager(RpgCraft plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "dungeons.yml");
        this.KEY = new NamespacedKey(plugin, "dungeon_entrance");
        load();
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    // ------------------------------------------------------------------ 저장
    private void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String id : y.getKeys(false)) {
            Dungeon d = new Dungeon();
            d.id = id;
            d.name = y.getString(id + ".name");
            d.tier = y.getInt(id + ".tier", 1);
            d.entrance = y.getLocation(id + ".entrance");
            d.interior = y.getLocation(id + ".interior");
            if (d.entrance != null && d.interior != null) dungeons.put(id, d);
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Dungeon d : dungeons.values()) {
            y.set(d.id + ".name", d.name);
            y.set(d.id + ".tier", d.tier);
            y.set(d.id + ".entrance", d.entrance);
            y.set(d.id + ".interior", d.interior);
        }
        try {
            y.save(file);
        } catch (IOException ignored) {
        }
    }

    public Collection<Dungeon> all() {
        return dungeons.values();
    }

    // ------------------------------------------------------------------ 건설
    private void set(World w, int x, int y, int z, Material m) {
        w.getBlockAt(x, y, z).setType(m, false);
    }

    public Dungeon create(String id, int tier, Location at) {
        tier = Math.max(1, Math.min(9, tier));
        World w = at.getWorld();
        int ex = at.getBlockX(), ey = at.getBlockY(), ez = at.getBlockZ();
        // 입구: 석조 아치 + 입구 블록
        Material wall = tier >= 4 ? Material.POLISHED_BLACKSTONE_BRICKS : tier == 3 ? Material.PRISMARINE_BRICKS : Material.DEEPSLATE_BRICKS;
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) set(w, ex + x, ey - 1, ez + z, wall);
        for (int y = 0; y <= 5; y++) { set(w, ex - 3, ey + y, ez, wall); set(w, ex + 3, ey + y, ez, wall); }
        for (int x = -3; x <= 3; x++) set(w, ex + x, ey + 5, ez, wall);
        for (int x = -2; x <= 2; x++) for (int y = 0; y <= 4; y++) set(w, ex + x, ey + y, ez, Material.AIR);
        set(w, ex, ey, ez, Material.CRYING_OBSIDIAN);
        set(w, ex - 3, ey + 6, ez, Material.SOUL_LANTERN);
        set(w, ex + 3, ey + 6, ez, Material.SOUL_LANTERN);
        // 내부: 입구 아래 깊은 곳
        int iy = Math.max(w.getMinHeight() + 6, Math.min(ey - 40, 0));
        int ix = ex + 20, iz = ez;
        Material floor = tier >= 4 ? Material.POLISHED_BLACKSTONE : tier == 3 ? Material.DARK_PRISMARINE : Material.DEEPSLATE_TILES;
        int len = GAP * 3 + ROOM;
        for (int x = -2; x <= len + 2; x++)
            for (int z = -ROOM / 2 - 2; z <= ROOM / 2 + 2; z++)
                for (int y = -1; y <= H + 1; y++) {
                    boolean inRoom = false;
                    for (int r = 0; r < 4; r++) if (x >= r * GAP && x <= r * GAP + ROOM - 1 && Math.abs(z) <= ROOM / 2) inRoom = true;
                    boolean inHall = Math.abs(z) <= 2 && x >= 0 && x <= len && y <= 5;
                    Material m = (inRoom || inHall) && y >= 0 && y < (inRoom ? H : 5) ? Material.AIR : y == -1 ? floor : wall;
                    set(w, ix + x, iy + y, iz + z, m);
                }
        for (int r = 0; r < 4; r++) { // 조명 · 창살 문
            int cx = ix + r * GAP + ROOM / 2;
            for (int[] c : new int[][]{{-6, -6}, {6, -6}, {-6, 6}, {6, 6}}) set(w, cx + c[0], iy + H - 1, iz + c[1], Material.SOUL_LANTERN);
            if (r < 3) gate(w, ix + r * GAP + ROOM + 4, iy, iz, true);
        }
        Dungeon d = new Dungeon();
        d.id = id;
        d.tier = tier;
        d.name = TIER_NAME[tier];
        d.entrance = new Location(w, ex + 0.5, ey + 1, ez + 1.5);
        d.interior = new Location(w, ix + 2.5, iy, iz + 0.5, -90, 0);
        dungeons.put(id, d);
        save();
        TextDisplay label = w.spawn(new Location(w, ex + 0.5, ey + 3.2, ez + 0.5), TextDisplay.class, t -> {
            t.setText(Text.c("&6&l" + d.name + "\n&7권장 Lv." + TIER_LEVEL[d.tier] + " &8| &f입구 블록 우클릭"));
            t.setBillboard(Display.Billboard.CENTER);
        });
        label.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, id);
        return d;
    }

    private void gate(World w, int x, int y, int z, boolean close) {
        for (int dz = -2; dz <= 2; dz++) for (int dy = 0; dy < 5; dy++) set(w, x, y + dy, z + dz, close ? Material.IRON_BARS : Material.AIR);
    }

    /** 스폰에서 떨어진 곳 곳곳에 자동 배치 (거리가 멀수록 높은 단계) */
    public int generate(World w, int count) {
        int made = 0;
        Location spawn = w.getSpawnLocation();
        for (int i = 0; i < count * 10 && made < count; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, r = 300 + rnd.nextDouble() * plugin.getConfig().getDouble("dungeon.generate-radius", 6000);
            Location l = spawn.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r);
            Block top = w.getHighestBlockAt(l);
            if (top.isLiquid()) continue;
            boolean close = false;
            for (Dungeon d : dungeons.values()) if (d.entrance.getWorld().equals(w) && d.entrance.distance(top.getLocation()) < 400) close = true;
            if (close) continue;
            int lvHere = plugin.mobs().computeLevel(top.getLocation());
            int tier = 1;
            for (int t = 1; t < TIER_LEVEL.length; t++) if (lvHere >= TIER_LEVEL[t] - 10) tier = t;
            create("dungeon_" + (dungeons.size() + 1), tier, top.getLocation().add(0, 1, 0));
            made++;
        }
        return made;
    }

    // ------------------------------------------------------------------ 입장
    @EventHandler
    public void onEnter(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null
                || e.getClickedBlock().getType() != Material.CRYING_OBSIDIAN) return;
        Location b = e.getClickedBlock().getLocation();
        for (Dungeon d : dungeons.values()) {
            if (!d.entrance.getWorld().equals(b.getWorld()) || d.entrance.distanceSquared(b.clone().add(0.5, 1, 1.5)) > 2) continue;
            e.setCancelled(true);
            enter(e.getPlayer(), d);
            return;
        }
    }

    private void enter(Player p, Dungeon d) {
        Run run = runs.get(d.id);
        PartyManager.Party party = plugin.party().of(p);
        if (run != null) {
            boolean ally = false;
            for (UUID u : run.players) { Player q = Bukkit.getPlayer(u); if (q != null && plugin.party().same(p, q)) ally = true; }
            if (!ally) { Text.msg(p, "&c다른 플레이어가 공략 중입니다. 잠시 후 다시 시도하세요."); return; }
            if (run.eliminated.contains(p.getUniqueId())
                    || run.deaths.getOrDefault(p.getUniqueId(), 0) >= plugin.getConfig().getInt("dungeon.death-limit", 2)) {
                Text.msg(p, "&c이번 공략에서 탈락했습니다. 파티가 공략을 마칠 때까지 다시 들어갈 수 없습니다.");
                return;
            }
            run.players.add(p.getUniqueId());
            p.teleport(d.interior);
            Text.msg(p, "&a파티원이 공략 중인 " + d.name + "에 합류했습니다.");
            return;
        }
        run = new Run(d);
        List<Player> team = new ArrayList<>();
        team.add(p);
        if (party != null) for (Player m : party.online())   // 파티원은 어디에 있든 함께 입장
            if (!m.equals(p) && !m.isDead() && runOf(m) == null) team.add(m);
        for (Player m : team) run.players.add(m.getUniqueId());
        run.deadline = System.currentTimeMillis() + plugin.getConfig().getLong("dungeon.time-limit-minutes", 20) * 60_000;
        run.bar = Bukkit.createBossBar(Text.c("&6" + d.name), BarColor.YELLOW, BarStyle.SEGMENTED_10);
        runs.put(d.id, run);
        for (int r = 0; r < 3; r++) gate(d.interior.getWorld(), d.interior.getBlockX() - 2 + r * GAP + ROOM + 4, d.interior.getBlockY(), d.interior.getBlockZ(), true);
        for (Player m : team) {
            m.teleport(d.interior);
            run.bar.addPlayer(m);
            m.sendTitle(Text.c("&6&l" + d.name), Text.c("&7" + (team.size() > 1 ? team.size() + "인 파티" : "솔로") + " 입장 · 제한 시간 " + plugin.getConfig().getLong("dungeon.time-limit-minutes", 20) + "분"), 10, 50, 10);
        }
        nextRoom(run);
    }

    /** 던전 몬스터 레벨 = 권장 레벨 (±2) — 들어온 사람의 레벨과 상관없음 */
    private int lv(Run run) {
        return Math.max(1, TIER_LEVEL[run.d.tier] + rnd.nextInt(5) - 2);
    }

    private void nextRoom(Run run) {
        run.room++;
        run.nextAt = 0;
        Dungeon d = run.d;
        World w = d.interior.getWorld();
        int size = run.players.size();
        Location c = d.interior.clone().add(run.room * GAP + ROOM / 2.0 - 2, 0, 0);
        int level = lv(run);
        List<CustomMobManager.MobDef> pool = new ArrayList<>();
        for (CustomMobManager.MobDef def : plugin.customMobs().defs())
            if (def.natural && level >= def.minLevel - 5 && level <= def.maxLevel + 5 && def.type != EntityType.PHANTOM && def.type != EntityType.VEX && def.type != EntityType.ENDERMITE) pool.add(def);
        String[] msg = {"몬스터 무리가 몰려옵니다!", "정예 몬스터가 나타났습니다!", "중간 보스가 길을 막아섭니다!", "최종 보스 등장!"};
        switch (run.room) {
            case 0, 1 -> {
                int n = (run.room == 0 ? 6 : 4) + (size - 1) * 2;
                for (int i = 0; i < n; i++) {
                    LivingEntity m = spawnMob(pool, c, level, w);
                    if (m == null) continue;
                    if (run.room == 1 || (size >= 3 && i % 3 == 0)) plugin.tiers().apply(m, plugin.mobs().peek(m), MonsterTierManager.Tier.ELITE);
                    run.mobs.add(m.getUniqueId());
                }
            }
            case 2 -> {
                LivingEntity m = spawnMob(pool, c, level + 3, w);
                if (m != null) { plugin.tiers().apply(m, plugin.mobs().peek(m), MonsterTierManager.Tier.MINIBOSS); run.mobs.add(m.getUniqueId()); }
                for (int i = 0; i < 2 + size; i++) { LivingEntity a = spawnMob(pool, c, level, w); if (a != null) run.mobs.add(a.getUniqueId()); }
            }
            default -> {
                LivingEntity b = plugin.bosses().spawn(TIER_BOSS[d.tier], c);
                if (b != null) { run.boss = b.getUniqueId(); run.mobs.add(b.getUniqueId()); }
                for (int i = 0; i < size - 1; i++) { LivingEntity a = spawnMob(pool, c, level, w); if (a != null) run.mobs.add(a.getUniqueId()); }
            }
        }
        for (UUID u : run.players) { Player p = Bukkit.getPlayer(u); if (p != null) Text.actionBar(p, "&c" + (run.room + 1) + "번째 방 · " + msg[Math.min(3, run.room)]); }
    }

    private LivingEntity spawnMob(List<CustomMobManager.MobDef> pool, Location c, int level, World w) {
        Location at = c.clone().add(rnd.nextDouble() * 10 - 5, 0.2, rnd.nextDouble() * 10 - 5);
        if (!pool.isEmpty()) return plugin.customMobs().spawn(pool.get(rnd.nextInt(pool.size())), at, level);
        LivingEntity m = (LivingEntity) w.spawnEntity(at, EntityType.ZOMBIE);
        MobManager mm = plugin.mobs();
        mm.initCustom(m, level, mm.hpFor(level), mm.damageFor(level), level * 0.2, mm.expFor(level), level * 60L, "던전 파수꾼");
        return m;
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Run run : new ArrayList<>(runs.values())) {
            if (run.done) continue;
            run.players.removeIf(u -> { Player p = Bukkit.getPlayer(u); return p == null || !p.getWorld().equals(run.d.interior.getWorld())
                    || p.getLocation().distanceSquared(run.d.interior) > 150 * 150; });
            if (run.players.isEmpty()) { finish(run, false, "공략자가 모두 나가 던전이 닫혔습니다."); continue; }
            if (now > run.deadline) { finish(run, false, "제한 시간이 끝났습니다."); continue; }
            run.mobs.removeIf(u -> { Entity e = Bukkit.getEntity(u); return e == null || e.isDead() || !e.isValid(); });
            long left = (run.deadline - now) / 1000;
            run.bar.setTitle(Text.c("&6" + run.d.name + " &7| &f" + (run.room + 1) + "/4번째 방 &7| 남은 몬스터 &c" + run.mobs.size() + " &7| &e" + left / 60 + ":" + String.format("%02d", left % 60)));
            run.bar.setProgress(Math.max(0, Math.min(1, (run.room + (run.mobs.isEmpty() ? 1 : 0)) / 4.0)));
            if (!run.mobs.isEmpty()) continue;
            if (run.room >= 3) { finish(run, true, null); continue; }
            if (run.nextAt == 0) {
                run.nextAt = now + 3000;
                gate(run.d.interior.getWorld(), run.d.interior.getBlockX() - 2 + run.room * GAP + ROOM + 4, run.d.interior.getBlockY(), run.d.interior.getBlockZ(), false);
                for (UUID u : run.players) { Player p = Bukkit.getPlayer(u); if (p != null) { Text.actionBar(p, "&a방을 정리했습니다! 다음 방의 문이 열립니다."); p.playSound(p.getLocation(), Sound.BLOCK_IRON_DOOR_OPEN, 1f, 0.8f); } }
            } else if (now >= run.nextAt) nextRoom(run);
        }
    }

    private void finish(Run run, boolean clear, String why) {
        if (clear) Bukkit.getScheduler().runTaskLater(plugin, () -> {   // 클리어한 던전은 사라지고, 잠시 뒤 다른 곳에 새로 생김
            World w = run.d.entrance.getWorld();
            int tier = run.d.tier;
            delete(run.d.id);
            Text.announce(Text.PREFIX + Text.c("&7" + run.d.name + " 이(가) 무너져 사라졌습니다."));
            long delay = plugin.getConfig().getLong("dungeon.respawn-minutes", 10) * 60 * 20;
            Bukkit.getScheduler().runTaskLater(plugin, () -> generate(w, 1), delay);
        }, 80L);
        run.done = true;
        runs.remove(run.d.id);
        run.bar.removeAll();
        for (UUID u : run.mobs) { Entity e = Bukkit.getEntity(u); if (e != null) e.remove(); }
        String day = LocalDate.now().toString();
        for (UUID u : run.players) {
            Player p = Bukkit.getPlayer(u);
            if (p == null) continue;
            if (clear) {
                var d = plugin.data().get(p);
                String key = "dungeon_" + run.d.id + "_" + day.replace("-", "");
                d.counters.merge("ach_dungeon", 1.0, Double::sum);
                if (d.counters.putIfAbsent(key, 1.0) == null) {
                    double exp = plugin.levels().need(d.level) * plugin.getConfig().getDouble("dungeon.exp-ratio", 0.5) * run.d.tier / 2.0;
                    long money = (long) (plugin.getConfig().getLong("dungeon.money-per-tier", 60000) * run.d.tier * plugin.getConfig().getDouble("economy.boss-money-mult", 0.35));
                    plugin.levels().addExp(p, exp);
                    plugin.economy().give(p, money);
                    String[][] pools = {{}, {"crystal_low", "rune_low", "potion_2", "scroll_exp"}, {"crystal_mid", "rune_low", "ticket_rune", "loot_totem"},
                            {"crystal_high", "rune_mid", "ticket_rate10", "loot_core"}, {"crystal_top", "rune_high", "loot_core", "ticket_rate10"},
                            {"crystal_top", "acc_ring_3", "loot_crown", "ticket_rate10"}, {"crystal_top", "acc_neck_3", "loot_crown", "loot_core"},
                            {"crystal_top", "acc_ear_3", "loot_crown", "ticket_protect"}, {"crystal_top", "rune_high", "loot_crown", "ticket_protect"},
                            {"crystal_top", "rune_high", "loot_crown", "ticket_protect"}};
                    String[] pool = pools[Math.min(run.d.tier, pools.length - 1)];   // 단계에 맞는 보상만
                    for (int i = 0; i < 2 + run.d.tier; i++) {
                        ItemStack it = plugin.items().create(pool[rnd.nextInt(pool.length)], 1);
                        if (it != null) for (ItemStack l : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), l);
                    }
                    if (rnd.nextDouble() < 0.02 * run.d.tier) p.getInventory().addItem(plugin.items().create("ticket_protect", 1));
                    p.sendTitle(Text.c("&6&l던전 클리어!"), Text.c("&f경험치 " + Text.num(exp) + " · " + Text.money(money)), 5, 60, 10);
                } else p.sendTitle(Text.c("&6&l던전 클리어!"), Text.c("&7오늘은 이미 이 던전의 보상을 받았습니다"), 5, 60, 10);
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                Fx.helix(plugin, p, 2.4, 0.9, 20, Color.fromRGB(0xFFD23F), Color.fromRGB(0xFFFFFF));
                Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline()) p.teleport(run.d.entrance); }, 60L);
            } else {
                Text.msg(p, "&c던전 공략 실패: " + why);
                p.teleport(run.d.entrance);
            }
        }
    }

    public Object runOfAny(Player p) { return runOf(p); }

    /** 던전 삭제: 입구·내부 블록과 이름표를 지우고 목록에서 뺌 */
    public boolean delete(String id) {
        Dungeon d = dungeons.remove(id);
        if (d == null) return false;
        Run r = runs.get(id);
        if (r != null) finish(r, false, "던전이 사라졌습니다.");
        World w = d.entrance.getWorld();
        int ex = d.entrance.getBlockX(), ey = d.entrance.getBlockY() - 1, ez = d.entrance.getBlockZ() - 1;
        Material floor = w.getBlockAt(ex + 7, ey - 1, ez + 7).getType();   // 입구 바깥의 원래 바닥
        if (floor.isAir() || !floor.isSolid()) floor = Material.GRASS_BLOCK;
        for (int x = -5; x <= 5; x++) for (int y = -2; y <= 8; y++) for (int z = -5; z <= 5; z++) {
            org.bukkit.block.Block b = w.getBlockAt(ex + x, ey + y, ez + z);
            b.setType(y < 0 ? (y == -1 ? floor : Material.DIRT) : Material.AIR, false);
        }
        int ix = d.interior.getBlockX() - 2, iy = d.interior.getBlockY(), iz = d.interior.getBlockZ();
        int len = GAP * 3 + ROOM;
        for (int x = -2; x <= len + 2; x++) for (int z = -ROOM / 2 - 2; z <= ROOM / 2 + 2; z++) for (int y = -1; y <= H + 1; y++)
            w.getBlockAt(ix + x, iy + y, iz + z).setType(Material.DEEPSLATE, false);
        for (Entity e : w.getNearbyEntities(new Location(w, ex + 0.5, ey + 4, ez + 0.5), 3, 4, 3))
            if (e.getPersistentDataContainer().has(KEY, PersistentDataType.STRING)) e.remove();
        save();
        return true;
    }

    public boolean isDungeonMob(Entity e) {
        for (Run r : runs.values()) if (r.mobs.contains(e.getUniqueId())) return true;
        return false;
    }

    public Run runOf(Player p) {
        for (Run r : runs.values()) if (r.players.contains(p.getUniqueId())) return r;
        return null;
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Run r = runOf(e.getPlayer());
        if (r == null) return;
        int deaths = r.deaths.merge(e.getPlayer().getUniqueId(), 1, Integer::sum);
        int max = plugin.getConfig().getInt("dungeon.death-limit", 2);
        if (deaths < max) {   // 데스 카운트가 남아 있으면 던전 입구 방에서 다시
            e.setRespawnLocation(r.d.interior.clone().add(2, 1, 0));
            Text.msg(e.getPlayer(), "&c쓰러졌습니다! &7(데스 " + deaths + " / " + max + ")");
            return;
        }
        r.players.remove(e.getPlayer().getUniqueId());
        r.eliminated.add(e.getPlayer().getUniqueId());
        r.bar.removePlayer(e.getPlayer());
        e.setRespawnLocation(r.d.entrance);
        Text.msg(e.getPlayer(), "&7데스 " + max + "회 — 던전에서 탈락했습니다.");
        if (r.players.isEmpty() && !r.done) {   // 모두 탈락 → 던전 붕괴
            finish(r, false, "모두 쓰러졌습니다.");
            Dungeon dd = r.d;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                World w = dd.entrance.getWorld();
                delete(dd.id);
                Text.announce(Text.PREFIX + Text.c("&7" + dd.name + " 이(가) 무너져 사라졌습니다."));
                Bukkit.getScheduler().runTaskLater(plugin, () -> generate(w, 1), plugin.getConfig().getLong("dungeon.respawn-minutes", 10) * 60 * 20);
            }, 60L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Run r = runOf(e.getPlayer());
        if (r != null) { r.players.remove(e.getPlayer().getUniqueId()); r.bar.removePlayer(e.getPlayer()); }
    }

    /** 던전 안에서 접속하면(공략 중이 아니면) 입구로 */
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Location l = e.getPlayer().getLocation();
        for (Dungeon d : dungeons.values()) {
            if (!d.interior.getWorld().equals(l.getWorld())) continue;
            if (Math.abs(l.getY() - d.interior.getY()) < 12 && l.distanceSquared(d.interior) < 120 * 120 && runOf(e.getPlayer()) == null) {
                e.getPlayer().teleport(d.entrance);
                return;
            }
        }
    }

    public void shutdown() {
        for (Run r : new ArrayList<>(runs.values())) finish(r, false, "서버가 종료되었습니다.");
    }
}
