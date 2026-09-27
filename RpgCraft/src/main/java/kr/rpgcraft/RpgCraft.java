package kr.rpgcraft;

import kr.rpgcraft.boss.BossManager;
import kr.rpgcraft.combat.CombatListener;
import kr.rpgcraft.combat.CombatService;
import kr.rpgcraft.combat.HealthManager;
import kr.rpgcraft.command.AdminCommand;
import kr.rpgcraft.command.GuildCommand;
import kr.rpgcraft.command.PlayerCommands;
import kr.rpgcraft.command.WarCommand;
import kr.rpgcraft.data.DataManager;
import kr.rpgcraft.data.RoundManager;
import kr.rpgcraft.economy.Economy;
import kr.rpgcraft.economy.ShopManager;
import kr.rpgcraft.feature.*;
import kr.rpgcraft.gui.GuiListener;
import kr.rpgcraft.guild.GuildManager;
import kr.rpgcraft.item.ItemRegistry;
import kr.rpgcraft.listener.PlayerListener;
import kr.rpgcraft.mob.MobManager;
import kr.rpgcraft.passive.PassiveManager;
import kr.rpgcraft.stat.StatCalculator;
import kr.rpgcraft.war.WarListener;
import kr.rpgcraft.war.WarManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/**
 * RpgCraft 플러그인 (Spigot/Paper 1.20.1)
 * 레벨/스탯, 가상 체력 전투, 무기군, 강화, 보스/월드보스, 채집, 대장장이, 룬, 사신수, 길드/토템, 공성전, 유적, 히든 패시브.
 */
public final class RpgCraft extends JavaPlugin {
    private static RpgCraft instance;

    private ItemRegistry items;
    private DataManager data;
    private RoundManager rounds;
    private StatCalculator stats;
    private LevelService levels;
    private HealthManager health;
    private MobManager mobs;
    private BossManager bosses;
    private CombatService combat;
    private Economy economy;
    private ShopManager shops;
    private EnhanceManager enhance;
    private BlacksmithManager blacksmith;
    private PotionManager potions;
    private RuneManager runes;
    private SpiritManager spirits;
    private RuinManager ruins;
    private GuildManager guilds;
    private WarManager wars;
    private PassiveManager passives;
    private HudManager hud;
    private QuestManager quests;
    private VisualManager visuals;
    private MenuManager menu;
    private ItemEffectManager effects;
    private WorldProtection protection;
    private JobManager jobs;
    private WeaponSkillManager weaponSkills;
    private WorldEventManager events;
    private TradeManager trades;
    private StructureManager structures;
    private CompassManager compass;
    private SkillBook skillBook;
    private AccessoryManager accessories;
    private PotentialManager potentials;
    private RuneFusion runeFusion;
    private kr.rpgcraft.command.PlayerCommands playerCommands;
    private kr.rpgcraft.world.ContentManager content;
    private kr.rpgcraft.world.PenderManager pender;
    private kr.rpgcraft.world.GuideManager guide;
    private kr.rpgcraft.world.DummyManager dummies;
    private kr.rpgcraft.world.WorldBossManager worldBoss;
    private kr.rpgcraft.feature.MountManager mounts;
    private kr.rpgcraft.feature.PetManager pets;
    private kr.rpgcraft.feature.NickManager nicks;
    private kr.rpgcraft.boss.FieldBossManager fieldBosses;
    private kr.rpgcraft.feature.DismantleManager dismantle;
    private kr.rpgcraft.guild.GuildRaidManager guildRaids;
    private kr.rpgcraft.feature.LimitBreakManager limitBreak;
    private kr.rpgcraft.world.HiddenJobManager hiddenJobs;
    private kr.rpgcraft.world.HiddenQuestManager hiddenQuests;
    private kr.rpgcraft.world.AuctionManager auction;
    private PartyManager party;
    private LegendaryManager legendary;
    private kr.rpgcraft.world.CycleManager cycle;
    private kr.rpgcraft.mob.MonsterTierManager tiers;
    private kr.rpgcraft.world.DungeonManager dungeons;
    private kr.rpgcraft.world.QuestNpcManager questNpcs;
    private kr.rpgcraft.world.CasinoManager casino;
    private kr.rpgcraft.boss.BossModelManager bossModels;
    private kr.rpgcraft.mob.CustomMobManager customMobs;
    private kr.rpgcraft.util.LegacyMigrator migrator;
    private kr.rpgcraft.pack.PackManager pack;

    public static RpgCraft get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        kr.rpgcraft.util.LegacyMigrator.migrateDataFolder(this);
        Keys.init(this);
        saveDefaultConfig();
        updateConfig();
        for (String res : new String[]{"bosses.yml", "shops.yml", "mobs.yml", "skills.yml"}) {
            if (!new File(getDataFolder(), res).exists()) saveResource(res, false);
        }

        pack = new kr.rpgcraft.pack.PackManager(this);
        data = new DataManager(this);
        rounds = new RoundManager(this);
        items = new ItemRegistry();
        guilds = new GuildManager(this);
        visuals = new VisualManager(this);
        quests = new QuestManager(this);
        effects = new ItemEffectManager(this);
        jobs = new JobManager(this);
        skillBook = new SkillBook(this);
        accessories = new AccessoryManager(this);
        potentials = new PotentialManager(this);
        runeFusion = new RuneFusion(this);
        trades = new TradeManager(this);
        stats = new StatCalculator(this);
        levels = new LevelService(this);
        health = new HealthManager(this);
        mobs = new MobManager(this);
        bosses = new BossManager(this);
        combat = new CombatService(this);
        economy = new Economy(this);
        runes = new RuneManager(this);
        enhance = new EnhanceManager(this);
        blacksmith = new BlacksmithManager(this);
        potions = new PotionManager(this);
        spirits = new SpiritManager(this);
        ruins = new RuinManager(this);
        wars = new WarManager(this);
        shops = new ShopManager(this);
        passives = new PassiveManager(this);
        hud = new HudManager(this);
        menu = new MenuManager(this);

        listen(mobs, new CombatListener(this), new GuiListener(), new PlayerListener(this), new GatherListener(this),
                potions, spirits, ruins, guilds, new WarListener(this), passives, hud, shops, visuals, menu, pack, migrator = new kr.rpgcraft.util.LegacyMigrator(this),
                protection = new WorldProtection(this), customMobs = new kr.rpgcraft.mob.CustomMobManager(this),
                new kr.rpgcraft.mob.AnimalAggro(this), weaponSkills = new WeaponSkillManager(this),
                events = new WorldEventManager(this), structures = new StructureManager(this), compass = new CompassManager(this),
                bossModels = new kr.rpgcraft.boss.BossModelManager(this),
                party = new PartyManager(this), legendary = new LegendaryManager(this), cycle = new kr.rpgcraft.world.CycleManager(this),
                tiers = new kr.rpgcraft.mob.MonsterTierManager(this), dungeons = new kr.rpgcraft.world.DungeonManager(this),
                questNpcs = new kr.rpgcraft.world.QuestNpcManager(this), new kr.rpgcraft.world.FishingManager(this),
                content = new kr.rpgcraft.world.ContentManager(this), pender = new kr.rpgcraft.world.PenderManager(this));
        guide = new kr.rpgcraft.world.GuideManager(this);
        dummies = new kr.rpgcraft.world.DummyManager(this);
        worldBoss = new kr.rpgcraft.world.WorldBossManager(this);
        new kr.rpgcraft.world.SeaMonsterManager(this);
        mounts = new kr.rpgcraft.feature.MountManager(this);
        command("mount", mounts);
        pets = new kr.rpgcraft.feature.PetManager(this);
        nicks = new kr.rpgcraft.feature.NickManager(this);
        fieldBosses = new kr.rpgcraft.boss.FieldBossManager(this);
        dismantle = new kr.rpgcraft.feature.DismantleManager(this);
        guildRaids = new kr.rpgcraft.guild.GuildRaidManager(this);
        command("pet", pets);
        limitBreak = new kr.rpgcraft.feature.LimitBreakManager(this);
        hiddenJobs = new kr.rpgcraft.world.HiddenJobManager(this);
        getServer().getPluginManager().registerEvents(hiddenJobs, this);
        auction = new kr.rpgcraft.world.AuctionManager(this);
        command("auction", auction);
        hiddenQuests = new kr.rpgcraft.world.HiddenQuestManager(this);
        getServer().getPluginManager().registerEvents(hiddenQuests, this);
        new kr.rpgcraft.world.BgmManager(this);
        command("guide", guide);
        casino = new kr.rpgcraft.world.CasinoManager(this);
        command("party", party);
        if (getCommand("party") != null) getCommand("party").setTabCompleter(party);
        command("legendary", legendary);

        PlayerCommands pc = new PlayerCommands(this);
        playerCommands = pc;
        getServer().getPluginManager().registerEvents(pc, this);
        for (String c : new String[]{"stat", "info", "money", "pay", "check", "potionbag", "rune", "skill", "enhance", "job",
                "craft", "rename", "look", "essence", "gc", "shop", "absorb", "menu", "trade", "escape", "casino", "call", "accessory", "coupon", "quickkey", "rebirth", "potential", "bounty", "dummy", "runefuse", "trash", "guidebook", "tpa", "tpaccept", "tpdeny", "ticket", "partychat", "nick", "enderchest", "limitbreak", "pack", "dismantle"}) command(c, pc);
        command("guild", new GuildCommand(this));
        command("war", new WarCommand(this));
        AdminCommand adminCmd = new AdminCommand(this);
        command("rpgadmin", adminCmd);
        command("notice", adminCmd);
        command("lottery", adminCmd);

        cleanupIndicators();
        for (World w : Bukkit.getWorlds())
            for (Entity e : w.getEntities()) kr.rpgcraft.util.LegacyMigrator.migrate(e.getPersistentDataContainer(), this);
        for (Player p : Bukkit.getOnlinePlayers()) {
            migrator.migrate(p.getInventory().getContents());
            data.get(p).name = p.getName();
            stats.refresh(p);
            hud.setup(p);
        }
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            data.saveAll();
            guilds.save();
        }, 6000L, 6000L);
        getLogger().info("서버 버전: " + Bukkit.getBukkitVersion());
        getLogger().info("RpgCraft 플러그인 활성화 - 아이템 " + items.all().size() + "종, 보스 " + bosses.ids().size() + "종");
    }

    @Override
    public void onDisable() {
        if (mounts != null) mounts.cleanup();
        if (pets != null) pets.cleanup();
        if (nicks != null) nicks.shutdown();
        if (fieldBosses != null) fieldBosses.shutdown();
        if (guildRaids != null) guildRaids.shutdown();
        for (org.bukkit.entity.Player op : getServer().getOnlinePlayers())   // 서버 종료 중에도 주문서 시간 보관
            if (data != null) kr.rpgcraft.listener.PlayerListener.pauseBuffs(data.get(op));
        if (pack != null) pack.shutdown();
        if (compass != null) compass.shutdown();
        if (dungeons != null) dungeons.shutdown();
        if (bossModels != null) bossModels.shutdown();
        if (events != null) events.shutdown();
        if (trades != null) trades.shutdown();
        if (protection != null) protection.restoreAll();
        if (wars != null) wars.shutdown();
        if (bosses != null) bosses.shutdown();
        if (hud != null) hud.shutdown();
        if (data != null) data.saveAll();
        if (guilds != null) guilds.save();
        if (rounds != null) rounds.save();
        cleanupIndicators();
        instance = null;
    }

    /**
     * 기존 config.yml 정리: 새 버전에 추가된 설정 항목을 채워 넣고,
     * 이전 이름(마크에이지 4R)이 남은 문구(리소스팩 안내 문구 등)를 RpgCraft 로 바꾼다. 사용자가 바꾼 다른 값은 유지.
     */
    private void updateConfig() {
        getConfig().options().copyDefaults(true);
        int renamed = 0;
        for (String key : getConfig().getKeys(true)) {
            if (getConfig().get(key) instanceof String v && (v.contains("마크에이지") || v.contains("MCAge4R"))) {
                getConfig().set(key, v.replace("마크에이지 4R", "RpgCraft").replace("마크에이지4R", "RpgCraft")
                        .replace("마크에이지", "RpgCraft").replace("MCAge4R", "RpgCraft"));
                renamed++;
            }
        }
        // 외부 리소스팩 주소가 비어 있으면 GitHub 배포 주소 사용 (포트를 열 수 없는 환경 대응)
        if (getConfig().getString("resourcepack.url", "").isBlank()) getConfig().set("resourcepack.url", getConfig().getDefaults().getString("resourcepack.url"));
        // 이전 버전 기본값 갱신
        if (getConfig().getInt("enhance.max-normal", 15) == 10) getConfig().set("enhance.max-normal", 15);
        if (Math.abs(getConfig().getDouble("spirit-summon.drop-chance", 0.0003) - 0.0008) < 1e-9) getConfig().set("spirit-summon.drop-chance", 0.0003);
        if (!getConfig().contains("spirit-summon.balrog-chance")) { getConfig().set("spirit-summon.balrog-chance", 0.00005); getConfig().set("spirit-summon.balrog-min-level", 120); }
        if (getConfig().getLong("mounts.draw-cost", 3000000) == 300000) getConfig().set("mounts.draw-cost", 3000000);
        if (Math.abs(getConfig().getDouble("boss.exp-even-share", 0.75) - 0.5) < 1e-9) getConfig().set("boss.exp-even-share", 0.75);   // 보스 경험치 더 고르게 (v5.4.0)
        if (Math.abs(getConfig().getDouble("boss.exp-max-share", 0.4) - 0.5) < 1e-9) getConfig().set("boss.exp-max-share", 0.4);
        if (Math.abs(getConfig().getDouble("player.max-defense", 90) - 85) < 1e-9) getConfig().set("player.max-defense", 90);   // 방어력 상한 85 → 90% (v5.3.8)
        if (Math.abs(getConfig().getDouble("guild-raid.cooldown-hours", 24) - 12) < 1e-9) getConfig().set("guild-raid.cooldown-hours", 24);   // 토벌전 쿨타임 12 → 24시간 (v5.3.3)
        String[][] v47 = {{"player.dex-crit-per-point", "0.2", "0.07"}, {"player.adv-hp-per-point", "140", "70"}, {"player.adv-def-per-point", "0.08", "0.05"},
                {"player.dex-critdmg-per-point", "0.8", "0.6"}, {"player.str-atk-per-2", "6", "5"}, {"player.hp-per-level", "60", "25"},
                {"world-boss.interval-minutes", "120", "0"}, {"world-boss.stay-minutes", "60", "30"}, {"enhance.max-normal", "15", "30"}, {"enhance.max-legend", "15", "30"},
                {"rebirth.exp-pct", "25", "50"}, {"mobs.potential-scroll-chance", "0.001", "0.0007"}};
        for (String[] e : v47) if (Math.abs(getConfig().getDouble(e[0], -1) - Double.parseDouble(e[1])) < 1e-6) getConfig().set(e[0], Double.parseDouble(e[2]));
        if (getConfig().getIntegerList("enhance.success").size() < 30)
            getConfig().set("enhance.success", java.util.List.of(95, 90, 85, 80, 75, 70, 65, 60, 50, 45, 40, 35, 30, 25, 20, 18, 16, 14, 12, 10, 9, 8, 7, 6, 5, 4, 3, 3, 2, 2));
        String[][] v46 = {{"player.dex-crit-per-point", "0.15", "0.2"}, {"player.adv-hp-per-point", "100", "140"}, {"player.adv-def-per-point", "0.05", "0.08"},
                {"player.dex-critdmg-per-point", "0.5", "0.8"}, {"mobs.crystal-chance", "0.2", "0.06"}};
        for (String[] e : v46) if (Math.abs(getConfig().getDouble(e[0], -1) - Double.parseDouble(e[1])) < 1e-6) getConfig().set(e[0], Double.parseDouble(e[2]));
        for (String[] e : new String[][]{{"player.adv-hp-per-point", "140", "90"}, {"player.adv-hp-per-point", "100", "90"}, {"player.adv-hp-per-point", "70", "90"},
                {"player.adv-def-per-point", "0.08", "0.065"}, {"player.adv-def-per-point", "0.05", "0.065"},
                {"player.dex-crit-per-point", "0.07", "0.1"}, {"player.dex-crit-per-point", "0.2", "0.1"}, {"player.dex-crit-per-point", "0.15", "0.1"},
                {"player.dex-critdmg-per-point", "0.6", "0.7"}, {"player.dex-critdmg-per-point", "0.8", "0.7"}, {"player.dex-critdmg-per-point", "0.5", "0.7"}})   // 모험 상향 (v5.0.3) · 민첩 치명타 0.1 (v5.0.4)
            if (Math.abs(getConfig().getDouble(e[0], -1) - Double.parseDouble(e[1])) < 1e-6) getConfig().set(e[0], Double.parseDouble(e[2]));
        if (!getConfig().contains("shops.show-stats"))
            getConfig().set("shops.show-stats", java.util.List.of("weapon", "weapon2", "armor_warrior", "armor_assassin", "armor_adventurer", "armor_set"));
        if (!getConfig().contains("mobs.level-gap-resist-base")) {   // 4.0 레벨 격차 밸런스
            getConfig().set("mobs.level-gap-resist", null);
            getConfig().set("mobs.level-gap-resist-base", 0.95);
            getConfig().set("mobs.level-gap-floor", 0.02);
            getConfig().set("mobs.level-gap-damage", 0.06);
            getConfig().set("mobs.level-gap-damage-cap", 8);
            getConfig().set("mobs.overlevel-free", 20);
            getConfig().set("mobs.hp.high-level-step", 60);
            getConfig().set("mobs.damage.high-level-step", 100);
        }
        if (Math.abs(getConfig().getDouble("party.exp-bonus-per-member", 0.03) - 0.1) < 1e-6) getConfig().set("party.exp-bonus-per-member", 0.03);
        if (getConfig().getInt("party.hud-x", 320) == 230) getConfig().set("party.hud-x", 320);
        double bm = getConfig().getDouble("weapon-skills.bow-damage-mult", 1.35);
        if (Math.abs(bm - 1.25) < 1e-6 || Math.abs(bm - 1.5) < 1e-6) getConfig().set("weapon-skills.bow-damage-mult", 1.35);
        long qs = getConfig().getLong("weapon-skills.quick-shot-ms", 1100);
        if (qs == 550 || qs == 1600) getConfig().set("weapon-skills.quick-shot-ms", 1100);
        if (getConfig().getLong("player.starting-money", 0) == 2000) getConfig().set("player.starting-money", 0);
        if (!getConfig().contains("gather.cooldown-by-tier")) getConfig().set("gather.cooldown-by-tier", java.util.List.of(180, 150, 120));
        if (!getConfig().contains("coupons")) getConfig().set("coupons.정식출시", java.util.List.of("scroll_exp:3"));
        if (Math.abs(getConfig().getDouble("boss.min-contribution", 0.07) - 0.03) < 1e-6) getConfig().set("boss.min-contribution", 0.07);
        if (!getConfig().contains("coupons.밸패")) getConfig().set("coupons.밸패", java.util.List.of("ticket_job_reset:1", "ticket_stat_reset:1"));
        String[][] v340 = {{"player.hp-per-level", "200", "60"}, {"player.str-atk-per-2", "3", "6"}, {"player.adv-hp-per-point", "40", "100"},
                {"player.dex-crit-per-point", "0.1", "0.15"}, {"economy.mob-money-mult", "0.1", "0.05"}, {"economy.boss-money-mult", "0.15", "0.1"},
                {"economy.quest-money-mult", "0.4", "0.25"}, {"economy.wave-money-mult", "0.12", "0.08"}, {"economy.loot-sell-mult", "0.25", "0.15"},
                {"mobs.hp.base", "60", "110"}, {"mobs.damage.base", "20", "40"}, {"party.share-radius", "32", "100"}, {"day-night.day-gather-cooldown", "0.7", "1.0"}};
        for (String[] e : v340) if (Math.abs(getConfig().getDouble(e[0], -1) - Double.parseDouble(e[1])) < 1e-6) getConfig().set(e[0], Double.parseDouble(e[2]));
        String[][] eco = {{"economy.mob-money-mult", "0.3", "0.1"}, {"economy.boss-money-mult", "0.35", "0.15"}, {"economy.quest-money-mult", "1.0", "0.4"},
                {"economy.wave-money-mult", "0.3", "0.12"}, {"economy.loot-sell-mult", "0.6", "0.25"}};
        for (String[] e : eco) if (Math.abs(getConfig().getDouble(e[0], -1) - Double.parseDouble(e[1])) < 1e-6) getConfig().set(e[0], Double.parseDouble(e[2]));
        java.util.List<String> herb = getConfig().getStringList("gather.nodes.herb.blocks");
        if (herb.contains("GRASS") || herb.contains("POPPY"))
            getConfig().set("gather.nodes.herb.blocks", java.util.List.of("DIRT", "GRASS_BLOCK", "COARSE_DIRT", "PODZOL", "ROOTED_DIRT", "MYCELIUM", "MUD", "DIRT_PATH"));
        if (getConfig().getInt("player.max-level", 300) == 150) getConfig().set("player.max-level", 300);
        if (getConfig().getInt("mobs.level.max", 300) == 110) getConfig().set("mobs.level.max", 300);
        if (getConfig().getDouble("world.keep-grass", 0) > 0) getConfig().set("world.keep-grass", 0.0);
        if (getConfig().getLong("player.starting-money", 2000) == 10000) getConfig().set("player.starting-money", 2000);
        if (getConfig().getInt("structures.ruin-steps", 48) == 18) getConfig().set("structures.ruin-steps", 48);
        if (getConfig().getInt("structures.ruin-min-adv", 10) == 0) getConfig().set("structures.ruin-min-adv", 10);
        // 블록 재생성 30초 → 5초
        if (getConfig().getInt("world-protection.regen-seconds", 5) == 30) getConfig().set("world-protection.regen-seconds", 5);
        // v5.1.0 패치 (한 번만): 민첩 치명타 0.12, 보물 상자·낚시 보물 상자에서 보물 지도 제거
        if (getConfig().getInt("config-patch", 0) < 510) {
            getConfig().set("player.dex-crit-per-point", 0.12);
            getConfig().set("treasure.map-fishing", null);
            getConfig().set("treasure.map-chest-per-tier", null);
            getConfig().set("mounts.scale", null);   // 탈것 크기는 이제 안장 높이(mounts.seat-height)로 자동 계산
            getConfig().set("config-patch", 510);
        }
        saveConfig();
        if (renamed > 0) getLogger().info("config.yml 의 이전 이름 문구 " + renamed + "곳을 RpgCraft 로 바꿨습니다.");
    }

    public void reload() {
        reloadConfig();
        updateConfig();
        bosses.load();
        shops.load();
        ruins.load();
        menu.reload();
        customMobs.load();
        skillBook.reload();
        pack.load();
    }

    private void listen(Listener... ls) {
        for (Listener l : ls) Bukkit.getPluginManager().registerEvents(l, this);
    }

    private void command(String name, CommandExecutor ex) {
        PluginCommand c = getCommand(name);
        if (c == null) {
            getLogger().warning("plugin.yml 에 명령어가 없습니다: " + name);
            return;
        }
        c.setExecutor(ex);
        if (ex instanceof TabCompleter tc) c.setTabCompleter(tc);
    }

    private void cleanupIndicators() {
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                if ((e instanceof ArmorStand || e instanceof org.bukkit.entity.TextDisplay)
                        && e.getPersistentDataContainer().has(Keys.INDICATOR, PersistentDataType.BYTE)) e.remove();
            }
        }
    }

    public ItemRegistry items() { return items; }
    public DataManager data() { return data; }
    public RoundManager rounds() { return rounds; }
    public StatCalculator stats() { return stats; }
    public LevelService levels() { return levels; }
    public HealthManager health() { return health; }
    public MobManager mobs() { return mobs; }
    public BossManager bosses() { return bosses; }
    public CombatService combat() { return combat; }
    public Economy economy() { return economy; }
    public ShopManager shops() { return shops; }
    public EnhanceManager enhance() { return enhance; }
    public BlacksmithManager blacksmith() { return blacksmith; }
    public PotionManager potions() { return potions; }
    public RuneManager runes() { return runes; }
    public SpiritManager spirits() { return spirits; }
    public RuinManager ruins() { return ruins; }
    public GuildManager guilds() { return guilds; }
    public WarManager wars() { return wars; }
    public PassiveManager passives() { return passives; }
    public HudManager hud() { return hud; }
    public QuestManager quests() { return quests; }
    public VisualManager visuals() { return visuals; }
    public MenuManager menu() { return menu; }
    public ItemEffectManager effects() { return effects; }
    public WorldProtection protection() { return protection; }
    public SkillBook skillBook() { return skillBook; }
    public AccessoryManager accessories() { return accessories; }
    public PotentialManager potentials() { return potentials; }
    public RuneFusion runeFusion() { return runeFusion; }
    public kr.rpgcraft.command.PlayerCommands playerCommands() { return playerCommands; }
    public kr.rpgcraft.world.ContentManager content() { return content; }
    public kr.rpgcraft.world.PenderManager pender() { return pender; }
    public kr.rpgcraft.world.GuideManager guide() { return guide; }
    public kr.rpgcraft.world.DummyManager dummies() { return dummies; }
    public kr.rpgcraft.world.WorldBossManager worldBoss() { return worldBoss; }
    public kr.rpgcraft.feature.MountManager mounts() { return mounts; }
    public kr.rpgcraft.feature.PetManager pets() { return pets; }
    public kr.rpgcraft.feature.NickManager nicks() { return nicks; }
    public kr.rpgcraft.boss.FieldBossManager fieldBosses() { return fieldBosses; }
    public kr.rpgcraft.feature.DismantleManager dismantle() { return dismantle; }
    public kr.rpgcraft.guild.GuildRaidManager guildRaids() { return guildRaids; }
    public kr.rpgcraft.feature.LimitBreakManager limitBreak() { return limitBreak; }
    public kr.rpgcraft.world.HiddenJobManager hiddenJobs() { return hiddenJobs; }
    public kr.rpgcraft.world.HiddenQuestManager hiddenQuests() { return hiddenQuests; }
    public kr.rpgcraft.world.AuctionManager auction() { return auction; }
    public PartyManager party() { return party; }
    public LegendaryManager legendary() { return legendary; }
    public kr.rpgcraft.world.CycleManager cycle() { return cycle; }
    public kr.rpgcraft.mob.MonsterTierManager tiers() { return tiers; }
    public kr.rpgcraft.world.DungeonManager dungeons() { return dungeons; }
    public kr.rpgcraft.world.QuestNpcManager questNpcs() { return questNpcs; }
    public kr.rpgcraft.world.CasinoManager casino() { return casino; }
    public kr.rpgcraft.boss.BossModelManager bossModels() { return bossModels; }
    public JobManager jobs() { return jobs; }
    public WeaponSkillManager weaponSkills() { return weaponSkills; }
    public WorldEventManager events() { return events; }
    public TradeManager trades() { return trades; }
    public StructureManager structures() { return structures; }
    public kr.rpgcraft.mob.CustomMobManager customMobs() { return customMobs; }
    public kr.rpgcraft.pack.PackManager pack() { return pack; }
}
