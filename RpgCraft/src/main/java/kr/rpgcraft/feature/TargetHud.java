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
    private static final char FRAME = '', BAR0 = '', ICON0 = '';
    private static final int W = 164, BAR_X = 34, STEPS = 25, ICON_X = 8;
    // tools/target_hud.py MARK 와 같아야 함 (흰 · 금 · 빨강 · 회색)
    private static final String WHITE = hex("fcfcf8"), GOLD = hex("fce080"), RED = hex("fc6060"), GRAY = hex("c8c8c4");

    private record Target(UUID mob, long until) {}

    private final RpgCraft plugin;
    private final Map<UUID, Target> targets = new HashMap<>();
    private final Map<UUID, BossBar> bars = new HashMap<>();

    public TargetHud(RpgCraft plugin) {
        this.plugin = plugin;
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

    /** 플레이어가 몬스터를 때렸거나 몬스터에게 맞았을 때 */
    public void mark(Player p, LivingEntity mob) {
        if (!enabled() || p == null || mob == null || mob instanceof Player || !plugin.mobs().tracked(mob)) return;
        targets.put(p.getUniqueId(), new Target(mob.getUniqueId(), System.currentTimeMillis() + 5000));
        update(p);
    }

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) if (targets.containsKey(p.getUniqueId()) || bars.containsKey(p.getUniqueId())) update(p);
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
        String title = pack ? panel(s.level, name, hp, ratio, boss, elite)
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

    /** 폭이 정확히 W 인 패널 문자열 (보스바가 가운데 정렬 → 셰이더가 오른쪽 끝으로) */
    private static String panel(int level, String name, String hp, double ratio, boolean boss, boolean elite) {
        StringBuilder sb = new StringBuilder();
        int cur = 0;
        sb.append(WHITE).append(FRAME);
        cur += W + 1;
        sb.append(PackManager.shift(ICON_X - cur)).append(boss ? (char) (ICON0 + 2) : elite ? (char) (ICON0 + 1) : ICON0);
        cur = ICON_X + 14;
        int step = (int) Math.round(ratio * STEPS);
        if (ratio > 0 && step == 0) step = 1;
        sb.append(PackManager.shift(BAR_X - cur)).append((char) (BAR0 + step));
        cur = BAR_X + 125;
        // 이름 줄: [정예] Lv.12 이름 ................ 1.2만/3만
        String tag = boss ? "보스 " : elite ? "정예 " : "";
        String lv = "Lv." + level + " ";
        int hpW = width(hp);
        int nameMax = (W - 8) - BAR_X - hpW - 6 - width(tag) - width(lv);
        String nm = fit(name, Math.max(18, nameMax));
        sb.append(PackManager.shift(BAR_X - cur));
        cur = BAR_X;
        if (!tag.isEmpty()) { sb.append(RED).append(tag); cur += width(tag); }
        sb.append(GOLD).append(lv);
        cur += width(lv);
        sb.append(WHITE).append(nm);
        cur += width(nm);
        int hpX = W - 8 - hpW;
        sb.append(PackManager.shift(hpX - cur)).append(GRAY).append(hp);
        cur = hpX + hpW;
        sb.append(PackManager.shift(W - cur));
        return sb.toString();
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
