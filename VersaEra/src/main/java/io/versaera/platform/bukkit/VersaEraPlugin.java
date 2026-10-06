package io.versaera.platform.bukkit;

import io.versaera.application.GameServices;
import io.versaera.content.ContentBundle;
import io.versaera.content.ContentLoader;
import io.versaera.domain.common.GameClock;
import io.versaera.domain.hidden.HiddenRule;
import io.versaera.domain.hidden.PlayerFacts;
import io.versaera.persistence.Database;
import io.versaera.persistence.DbExecutor;
import io.versaera.persistence.Migrator;
import io.versaera.platform.bukkit.binding.ItemCodec;
import io.versaera.platform.bukkit.boss.BossRuntime;
import io.versaera.platform.bukkit.command.AdminCommand;
import io.versaera.platform.bukkit.command.PlayerCommand;
import io.versaera.platform.bukkit.listener.*;
import io.versaera.platform.bukkit.ui.MenuListener;
import io.versaera.security.Sealer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * VersaEra — 「베르사 새벽기」. RpgCraft 와 코드를 공유하지 않는 독립 플러그인.
 * 시작 순서: DB 열기 → 마이그레이션 → 콘텐츠 → 서비스 → 지난 거래 복구 → 히든 봉인 열기 → 리스너 · 명령.
 * 어느 단계든 실패하면 플러그인을 끄고 이유를 로그에 남긴다 (반쯤 켜진 상태로 돌지 않게).
 */
public final class VersaEraPlugin extends JavaPlugin {
    private Database db;
    private DbExecutor exec;
    private GameServices services;
    private GatherListener gather;
    private BossRuntime bosses;
    /** 게임 시각(0 ~ 23). 메인 스레드가 5초마다 갱신하고, DB 스레드의 히든 판정은 이 값만 읽는다 */
    private volatile int gameHour = 12;

    @Override
    public void onEnable() {
        try {
            saveDefaultConfig();
            for (String f : ContentBundle.FILES) if (!new File(getDataFolder(), "content/" + f).exists()) saveResource("content/" + f, false);
            db = Database.open("jdbc:sqlite:" + new File(getDataFolder(), "versaera.db").getAbsolutePath());
            int applied = new Migrator(db).migrate(Migrator.fromClasspath(getClassLoader()));
            getLogger().info("DB 마이그레이션 " + applied + "개 적용");
            ContentBundle content = ContentBundle.load(f -> open(new File(getDataFolder(), "content/" + f)));
            ZoneId zone = ZoneId.of(getConfig().getString("timezone", "Asia/Seoul"));
            services = new GameServices(db, content, GameClock.SYSTEM, zone, getLogger());
            exec = new DbExecutor(getLogger());
            int recovered = exec.submit("recover", services.trades::recover).join();
            if (recovered > 0) getLogger().warning("지난 실행에서 끝나지 않은 거래 " + recovered + "건을 취소하고 아이템을 주인에게 돌려보냈습니다");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "VersaEra 시작 실패 — 플러그인을 끕니다", e);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        Async async = new Async(this, exec);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            World w = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
            if (w != null) gameHour = (int) ((w.getTime() / 1000 + 6) % 24);
        }, 0L, 100L);
        ItemCodec codec = new ItemCodec(this, services.items.types());
        Sealer sealer = new Sealer(serverKey());
        SessionListener sessions = new SessionListener(this, services, async, codec);
        RegionTracker regions = new RegionTracker(services, async);
        loadHidden(sealer, regions);
        NpcListener npcs = new NpcListener(this, services, async);
        gather = new GatherListener(this, services, async, codec, sessions);
        bosses = new BossRuntime(this, services);
        CombatListener combat = new CombatListener(this, services, async, codec);
        for (var l : List.of(sessions, new InventoryGuard(this, services, async, codec), regions, npcs, gather, combat, bosses,
                new StationListener(services, async, codec, sessions), new MenuListener()))
            Bukkit.getPluginManager().registerEvents(l, this);
        PlayerCommand pc = new PlayerCommand(services, async, codec, sessions::deliver);
        getCommand("versa").setExecutor(pc);
        getCommand("trade").setExecutor(pc);
        AdminCommand ac = new AdminCommand(services, async, codec, npcs, bosses, getDataFolder(), sealer, sessions::deliver);
        getCommand("versaadmin").setExecutor(ac);
        getCommand("versaadmin").setTabCompleter(ac);
        for (Player p : Bukkit.getOnlinePlayers()) {   // /reload 대비
            String id = p.getUniqueId().toString();
            async.fire("warm", () -> { combat.warm(id); return null; });
            sessions.deliver(p);
        }
        getLogger().info("VersaEra 시작 — 지역 " + services.regions.all().size() + " · 레시피 " + services.crafting.all().size()
                + " · 보스 " + services.content.bosses().size() + " · NPC " + services.relations.all().size());
    }

    private static InputStream open(File f) {
        try {
            return new FileInputStream(f);
        } catch (IOException e) {
            return null;
        }
    }

    /** 서버별 봉인 키 (처음 실행 때 생성, 소유자만 읽기) */
    private byte[] serverKey() {
        File f = new File(getDataFolder(), "secret.key");
        try {
            if (f.exists()) return java.util.Base64.getDecoder().decode(Files.readString(f.toPath()).strip());
            byte[] k = Sealer.newKey();
            Files.writeString(f.toPath(), java.util.Base64.getEncoder().encodeToString(k));
            try {
                Files.setPosixFilePermissions(f.toPath(), PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException e) {
                getLogger().info("이 OS 는 파일 권한을 설정할 수 없습니다: secret.key 를 직접 보호하세요");
            }
            return k;
        } catch (IOException e) {
            throw new IllegalStateException("secret.key 를 읽거나 만들 수 없습니다", e);
        }
    }

    private void loadHidden(Sealer sealer, RegionTracker regions) {
        File f = new File(getDataFolder(), "hidden.sealed");
        if (!f.exists()) {
            getLogger().info("히든 콘텐츠 봉인 파일 없음 (hidden.sealed) — 히든 콘텐츠 없이 시작");
            return;
        }
        try {
            String plain = sealer.open(Files.readString(f.toPath(), StandardCharsets.UTF_8));
            List<HiddenRule> rules = ContentLoader.hidden(ContentLoader.parse(plain, "hidden.sealed"), "hidden.sealed");
            services.attachHidden(rules, uuid -> facts(uuid, regions));
            getLogger().info("히든 규칙 " + rules.size() + "개 (내용은 로그에 남기지 않음)");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "hidden.sealed 를 열 수 없습니다 (키가 다르거나 변조됨) — 히든 콘텐츠 없이 시작", e);
        }
    }

    /** DB 스레드에서 불림 — Bukkit API 는 부르지 않고 메인 스레드가 미리 적어 둔 값만 읽는다 */
    private PlayerFacts facts(String uuid, RegionTracker regions) {
        int hour = gameHour;
        String region = regions.regionOf(UUID.fromString(uuid));
        return new PlayerFacts() {
            public long counter(String key) { return services.growth.counter(uuid, key); }
            public int mastery(String d) { return services.growth.level(uuid, d); }
            public int affinity(String npc) { return services.relations.affinity(uuid, npc); }
            public String region() { return region; }
            public boolean discovered(String kind, String ref) { return services.exploration.discovered(uuid, kind, ref); }
            public int hour() { return hour; }
        };
    }

    @Override
    public void onDisable() {
        if (bosses != null) bosses.stopAll();
        if (gather != null) gather.restoreAll();
        if (exec != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                String id = p.getUniqueId().toString();
                exec.submit("shutdown-trade", () -> { services.trades.cancelFor(id, "server stop"); return null; });
            }
            exec.close();   // 남은 DB 작업을 끝까지 기다림
        }
        if (db != null) {
            try {
                db.close();
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "DB 닫기 실패", e);
            }
        }
    }
}
