package kr.rpgcraft.feature;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.mob.MobManager;
import kr.rpgcraft.pack.AsciiWidths;
import kr.rpgcraft.pack.PackManager;
import kr.rpgcraft.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * v5.10.45 마크에이지 4R 풍 대상 정보 (화면 오른쪽 위) — 머리 위 체력 바 대신.
 *  때리거나 나를 때린 몬스터의 종류 아이콘 · 레벨 · 이름 · 체력 바 · 체력 수치를 5초 동안 보여 줌.
 *  리소스팩: 흰 보스바(바는 투명) 제목에 틀 · 바 글리프, 코어 셰이더가 약속한 색 글자만 오른쪽 끝으로 옮김 (tools/target_hud.py).
 *  팩 없음: 빨간 보스바 + 글자.
 */
public class TargetHud implements Listener {
    // tools/target_hud.py 와 같아야 함 (v5.10.47 마크에이지 4R 참고 화면대로: 이름 칸 + 보라→파랑 바 + 날개 문장)
    private static final char FRAME = '\uE0A0', BAR0 = '\uE0A1', EMB0 = '\uE0BB', DIG0 = '\uE0C0';
    private static final String DIGITS = "0123456789/,.kM";
    private static final char LV0 = '\uE0D0';   // "Lv.0123456789" (tools/target_hud.py LV_CHARS)
    private static final int W = 184, FRAME_ADV = 111, FILL_X = 4, FILL_ADV = 102, EMB_X = 98, EMB_ADV = 87, CENTER = 54, INNER = 100, STEPS = 25;   // v5.10.48 참고 화면 비율로 줄임
    private static final String WHITE = hex("fcfcf8"), GOLD = hex("fce080"), RED = hex("fc6060"), GRAY = hex("c8c8c4"), TXT = hex("f8fcfc");   // WHITE = 그림(뒤) · 나머지 = 글자(앞)

    private record Target(UUID mob, long until, boolean look) {}

    private final RpgCraft plugin;
    private final Map<UUID, Target> targets = new HashMap<>();
    private final Map<UUID, BossBar> bars = new HashMap<>();

    /** v5.10.49 모델 키 → 초상 글리프 번호 (tools/portraits.py → portraits.yml) */
    private final Map<String, Integer> portraits = new HashMap<>();
    private final org.bukkit.NamespacedKey customMobKey;

    public TargetHud(RpgCraft plugin) {
        this.plugin = plugin;
        this.customMobKey = new org.bukkit.NamespacedKey(plugin, "custom_mob");
        try (java.io.InputStream in = plugin.getResource("portraits.yml")) {
            if (in != null) {
                var y = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
                for (String k : y.getKeys(false)) portraits.put(k, y.getInt(k));
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("portraits.yml 을 읽지 못했습니다: " + ex.getMessage());
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 4L);
    }

    private static String hex(String h) {
        StringBuilder sb = new StringBuilder("§x");
        for (char c : h.toCharArray()) sb.append('§').append(c);
        return sb.toString();
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("mobs.target-hud", true);
    }

    /** 그 몬스터의 초상 글리프 (없으면 0) — 보스 id · 커스텀 몬스터 id · 바닐라 종류 순 */
    public char portrait(LivingEntity le) {
        var pdc = le.getPersistentDataContainer();
        String key = pdc.get(kr.rpgcraft.Keys.BOSS, org.bukkit.persistence.PersistentDataType.STRING);
        if (key == null) key = pdc.get(customMobKey, org.bukkit.persistence.PersistentDataType.STRING);
        if (key == null) key = kr.rpgcraft.mob.MobModelManager.vanillaKey(le.getType());
        Integer n = key == null ? null : portraits.get(key);
        return n == null ? 0 : (char) (0xE100 + n);
    }

    /** 보스는 보스 전용 판(BossManager)이 따로 있음 */
    private boolean isBoss(LivingEntity le) {
        var s = plugin.mobs().peek(le);
        return s != null && s.bossId != null && plugin.getConfig().getBoolean("bosses.panel", true);
    }

    /** 플레이어가 몬스터를 때렸거나 몬스터에게 맞았을 때 */
    public void mark(Player p, LivingEntity mob) {
        if (!enabled() || p == null || mob == null || mob instanceof Player || !plugin.mobs().tracked(mob) || isBoss(mob)) return;
        if (bossView.getOrDefault(p.getUniqueId(), 0L) > System.currentTimeMillis()) return;
        targets.put(p.getUniqueId(), new Target(mob.getUniqueId(), System.currentTimeMillis() + 5000, false));
        update(p);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        double look = plugin.getConfig().getDouble("mobs.target-hud-look", 24);
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (bossView.getOrDefault(p.getUniqueId(), 0L) > now) continue;   // 보스 판이 보이는 중
            Target cur = targets.get(p.getUniqueId());
            // v5.10.46 싸우는 중이 아니면 바라보는 몬스터의 이름 · 레벨 · 체력 (머리 위 이름표 대신)
            if (enabled() && look > 0 && (cur == null || cur.look() || cur.until() - now < 3500)) {
                var hit = p.getWorld().rayTraceEntities(p.getEyeLocation(), p.getEyeLocation().getDirection(), look, 0.4,
                        en -> en != p && en instanceof LivingEntity && !(en instanceof Player) && plugin.mobs().tracked((Entity) en) && !isBoss((LivingEntity) en));
                if (hit != null && hit.getHitEntity() instanceof LivingEntity le && (cur == null || cur.look() || !le.getUniqueId().equals(cur.mob())))
                    targets.put(p.getUniqueId(), new Target(le.getUniqueId(), now + 1200, true));
            }
            if (targets.containsKey(p.getUniqueId()) || bars.containsKey(p.getUniqueId())) update(p);
        }
    }

    private void update(Player p) {
        Target t = targets.get(p.getUniqueId());
        Entity en = t == null ? null : Bukkit.getEntity(t.mob());
        long now = System.currentTimeMillis();
        boolean alive = en instanceof LivingEntity le && le.isValid() && !le.isDead();
        if (t == null || now > t.until() || en == null || !alive && now > t.until() - 4000) {
            targets.remove(p.getUniqueId());
            BossBar b = bars.remove(p.getUniqueId());
            if (b != null) b.removeAll();
            return;
        }
        LivingEntity le = (LivingEntity) en;
        MobManager.MobState s = plugin.mobs().peek(le);
        if (s == null) return;
        double ratio = alive && s.maxHp > 0 ? Math.max(0, Math.min(1, s.hp / s.maxHp)) : 0;
        boolean boss = s.bossId != null, elite = !boss && plugin.customMobs() != null && plugin.customMobs().of(le) != null;
        String name = s.baseName == null ? MobManager.korean(le.getType()) : Text.strip(Text.c(s.baseName));
        boolean pack = plugin.pack() != null && plugin.pack().hasPack(p);
        BossBar b = bars.get(p.getUniqueId());
        if (b == null) {
            b = Bukkit.createBossBar("", pack ? BarColor.WHITE : BarColor.RED, BarStyle.SOLID);
            b.addPlayer(p);
            bars.put(p.getUniqueId(), b);
        }
        b.setColor(pack ? BarColor.WHITE : BarColor.RED);
        b.setProgress(ratio);
        String hp = Text.num(Math.max(0, alive ? s.hp : 0)) + "/" + Text.num(s.maxHp);
        String title = pack ? panel(s.level, name, alive ? s.hp : 0, s.maxHp, ratio, boss, elite, portrait(le))
                : Text.c((boss ? "&4&l[보스] " : elite ? "&c[정예] " : "") + "&6Lv." + s.level + " &f" + name + " &c" + hp);
        if (!title.equals(b.getTitle())) b.setTitle(title);
    }

    private static int width(String s) {
        int w = 0;
        for (char c : s.toCharArray()) w += c < 0x80 ? AsciiWidths.of(c) : 9;
        return w;
    }

    private static String fit(String s, int max) {
        if (width(s) <= max) return s;
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (width(sb.toString() + c + "..") > max) break;
            sb.append(c);
        }
        return sb + "..";
    }

    /** 바 안 숫자 (전용 글리프) */
    private static String digitGlyphs(String hp) {
        StringBuilder sb = new StringBuilder();
        for (char c : hp.toCharArray()) {
            int i = DIGITS.indexOf(c);
            if (i >= 0) sb.append((char) (DIG0 + i));
        }
        return sb.toString();
    }

    private static int digitWidth(String hp) {   // v5.10.49 4R 풍 숫자 (HUD 숫자와 같은 글꼴 · 크기)
        int w = 0;
        for (char c : hp.toCharArray()) if (DIGITS.indexOf(c) >= 0) w += kr.rpgcraft.pack.Num4R.adv(kr.rpgcraft.pack.Num4R.HUD_CHARS, kr.rpgcraft.pack.Num4R.HUD_W, c);
        return w;
    }

    /** 폭이 정확히 W 인 패널 문자열 (보스바가 가운데 정렬 → 셰이더가 오른쪽 위로) */
    private static String panel(int level, String name, double hp, double maxHp, double ratio, boolean boss, boolean elite, char face) {
        StringBuilder sb = new StringBuilder();
        sb.append(WHITE).append(FRAME);
        int cur = FRAME_ADV;
        int step = (int) Math.round(ratio * STEPS);
        if (ratio > 0 && step == 0) step = 1;
        sb.append(PackManager.shift(FILL_X - cur)).append((char) (BAR0 + step));
        cur = FILL_X + FILL_ADV;
        // 바 안 가운데: 30/30 (너무 길면 k · M 으로 줄임)
        String hpText = Text.num(Math.max(0, hp)) + "/" + Text.num(maxHp);
        if (digitWidth(hpText) > INNER - 6) hpText = kr.rpgcraft.pack.HudFont.compact(hp) + "/" + kr.rpgcraft.pack.HudFont.compact(maxHp);
        int hw = digitWidth(hpText);
        int hx = CENTER - hw / 2;
        sb.append(PackManager.shift(hx - cur)).append(TXT).append(digitGlyphs(hpText));
        cur = hx + hw;
        // 이름 칸 가운데: (정예 · 보스) Lv.12 이름
        String tag = boss ? "보스 " : elite ? "정예 " : "";
        String lvText = "Lv." + level;
        StringBuilder lvG = new StringBuilder();
        for (char c : lvText.toCharArray()) lvG.append((char) (LV0 + kr.rpgcraft.pack.Num4R.LV_CHARS.indexOf(c)));
        int lvW = kr.rpgcraft.pack.Num4R.width(kr.rpgcraft.pack.Num4R.LV_CHARS, kr.rpgcraft.pack.Num4R.LV_W, lvText) + 4;   // 이름과 4px 띄움
        String nm = fit(name, Math.max(18, INNER - width(tag) - lvW));
        int nw = width(tag) + lvW + width(nm);
        int nx = CENTER - nw / 2;
        sb.append(PackManager.shift(nx - cur));
        if (!tag.isEmpty()) sb.append(RED).append(tag);
        sb.append(GOLD).append(lvG).append(PackManager.shift(4)).append(TXT).append(nm);
        cur = nx + nw;
        // 오른쪽 문장 (일반 · 정예 · 보스)
        sb.append(WHITE).append(PackManager.shift(EMB_X - cur)).append((char) (EMB0 + (boss ? 2 : elite ? 1 : 0)));
        cur = EMB_X + EMB_ADV;
        if (face != 0) {   // v5.10.49 문장 원 안에 몬스터 초상 (앞 글자 색 → 문장 위에)
            sb.append(PackManager.shift(EMB_X + 12 - cur)).append(TXT).append(face);
            cur = EMB_X + 12 + 37;
        }
        sb.append(PackManager.shift(W - cur));
        return sb.toString();
    }

    // ------------------------------------------------------------------ v5.10.49 보스 전용 판 (BossManager 가 씀)
    private static final char B_FRAME = '\uE0E0', B_BAR0 = '\uE0E1';
    private static final int B_X = -50, B_FRAME_ADV = 161, B_FILL_ADV = 152, B_CENTER = B_X + 79, B_INNER = 150;

    /** 보스 판 문자열 (폭 W 로 맞춰 일반 적 정보와 같은 자리 · 크기 기준, 왼쪽으로 50px 더 넓음) */
    public String bossPanel(LivingEntity boss, String name, String phase, double hp, double maxHp) {
        double ratio = maxHp <= 0 ? 0 : Math.max(0, Math.min(1, hp / maxHp));
        StringBuilder sb = new StringBuilder();
        sb.append(PackManager.shift(B_X)).append(WHITE).append(B_FRAME);
        int cur = B_X + B_FRAME_ADV;
        int step = (int) Math.round(ratio * STEPS);
        if (ratio > 0 && step == 0) step = 1;
        sb.append(PackManager.shift(B_X + FILL_X - cur)).append((char) (B_BAR0 + step));
        cur = B_X + FILL_X + B_FILL_ADV;
        String hpText = Text.num(Math.max(0, hp)) + "/" + Text.num(maxHp);
        if (digitWidth(hpText) > B_INNER - 6) hpText = kr.rpgcraft.pack.HudFont.compact(hp) + "/" + kr.rpgcraft.pack.HudFont.compact(maxHp);
        int hw = digitWidth(hpText), hx = B_CENTER - hw / 2;
        sb.append(PackManager.shift(hx - cur)).append(TXT).append(digitGlyphs(hpText));
        cur = hx + hw;
        String ph = phase == null || phase.isEmpty() ? "" : phase + " ";
        String nm = fit(name, Math.max(30, B_INNER - width(ph)));
        int nw = width(ph) + width(nm), nx = B_CENTER - nw / 2;
        sb.append(PackManager.shift(nx - cur));
        if (!ph.isEmpty()) sb.append(RED).append(ph);
        sb.append(GOLD).append(nm);
        cur = nx + nw;
        sb.append(WHITE).append(PackManager.shift(EMB_X - cur)).append((char) (EMB0 + 2));
        cur = EMB_X + EMB_ADV;
        char face = portrait(boss);
        if (face != 0) {
            sb.append(PackManager.shift(EMB_X + 12 - cur)).append(TXT).append(face);
            cur = EMB_X + 12 + 37;
        }
        sb.append(PackManager.shift(W - cur));
        return sb.toString();
    }

    /** 보스 판이 보이는 동안엔 일반 적 정보를 숨김 (겹치지 않게) */
    private final Map<UUID, Long> bossView = new HashMap<>();

    public void suppress(Player p) {
        bossView.put(p.getUniqueId(), System.currentTimeMillis() + 1500);
        targets.remove(p.getUniqueId());
        BossBar b = bars.remove(p.getUniqueId());
        if (b != null) b.removeAll();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        targets.remove(e.getPlayer().getUniqueId());
        BossBar b = bars.remove(e.getPlayer().getUniqueId());
        if (b != null) b.removeAll();
    }

    public void shutdown() {
        for (BossBar b : bars.values()) b.removeAll();
        bars.clear();
    }
}
