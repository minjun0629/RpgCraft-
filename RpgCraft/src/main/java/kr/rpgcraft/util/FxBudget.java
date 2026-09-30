package kr.rpgcraft.util;

import kr.rpgcraft.RpgCraft;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

/**
 * 연출용 디스플레이 개수 제한 (v5.9.3 렉 줄이기).
 * 보스 기술은 한 번에 지진 조각 · 파편 · 기둥 · 충격파 · 광선을 수십 개씩 겹쳐 띄워 서버와 클라이언트 모두 버벅였다.
 * 순수 연출(파편 · 지진 · 충격파 등)은 한 틱에 새로 띄우는 수와 동시에 떠 있는 수를 넘으면 줄여서 띄운다.
 * 피해 범위를 알려 주는 바닥 예고판은 제한하지 않는다 (피하는 데 필요).
 */
public final class FxBudget {
    private FxBudget() {}

    private static int tick, spent, active;
    private static boolean started;

    public static void start(Plugin plugin) {
        if (started) return;
        started = true;
        Bukkit.getScheduler().runTaskTimer(plugin, () -> { tick++; spent = 0; }, 1L, 1L);
    }

    private static int cfg(String path, int def) {
        RpgCraft pl = RpgCraft.get();
        return pl == null ? def : pl.getConfig().getInt(path, def);
    }

    /** 연출 want 개를 띄워도 되는지: 허락된 개수(0~want)를 돌려주고 그만큼 센다. 다 쓰면 done() 으로 돌려놓기 */
    public static int grant(int want) {
        if (want <= 0) return 0;
        int perTick = cfg("vfx.max-new-per-tick", 18), maxActive = cfg("vfx.max-active", 70);
        int n = Math.max(0, Math.min(want, Math.min(perTick - spent, maxActive - active)));
        spent += n;
        active += n;
        return n;
    }

    /** 제한 없이 세기만 (바닥 예고판처럼 꼭 보여야 하는 것) */
    public static void force(int n) {
        spent += n;
        active += n;
    }

    public static void done(int n) {
        active = Math.max(0, active - n);
    }
}
