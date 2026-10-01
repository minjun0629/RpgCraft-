package kr.rpgcraft.feature;

import kr.rpgcraft.RpgCraft;
import kr.rpgcraft.data.PlayerData;

import java.util.ArrayList;
import java.util.List;

/**
 * v5.10.45 탐험도: 유적 · 보물 상자 도전 조건을 모험 스탯 대신 "얼마나 돌아다녔나" 로도 채울 수 있게.
 * 걸은 거리 · 몬스터 처치 · 채집 · 낚시 · 도감 발견 · 유적 클리어 · 보물 상자를 항목마다 상한까지 더한 점수.
 * ruins.requirement: either (모험 또는 탐험도 중 높은 쪽 · 기본) / explore (탐험도만) / adv (예전처럼 모험만)
 */
public final class Exploration {
    private Exploration() {}

    /** (이름, 카운터, 1점에 필요한 양, 최대 점수) */
    private record Part(String label, String counter, double per, int cap) {}

    private static final Part[] PARTS = {
            new Part("걸은 거리", "walk_blocks", 800, 80),
            new Part("몬스터 처치", "mob_kills", 40, 100),
            new Part("채집", "gathers", 30, 70),
            new Part("낚시", "fish", 20, 40),
            new Part("도감 발견", "#codex", 2, 60),
            new Part("유적 클리어", "ruin_clears", 0.25, 40),
            new Part("보물 상자", "chests_opened", 1, 30),
    };

    private static double amount(PlayerData d, Part p) {
        if (p.counter.equals("#codex")) {
            int n = 0;
            for (String k : d.counters.keySet()) if (k.startsWith("seen_")) n++;
            return n;
        }
        return d.counter(p.counter);
    }

    public static int score(PlayerData d) {
        double s = 0;
        for (Part p : PARTS) s += Math.min(p.cap, amount(d, p) / p.per);
        return (int) s;
    }

    private static String mode() {
        RpgCraft pl = RpgCraft.get();
        return pl == null ? "either" : pl.getConfig().getString("ruins.requirement", "either").toLowerCase();
    }

    /** 유적 · 보물 상자 조건에 쓰는 값 (모드에 따라 모험 · 탐험도 · 둘 중 높은 쪽) */
    public static int value(PlayerData d) {
        return switch (mode()) {
            case "adv" -> (int) d.stats.adv;
            case "explore" -> score(d);
            default -> Math.max((int) d.stats.adv, score(d));
        };
    }

    public static boolean meets(PlayerData d, int req) {
        return value(d) >= req;
    }

    /** "모험" / "탐험도" / "모험 또는 탐험도" */
    public static String label() {
        return switch (mode()) {
            case "adv" -> "모험";
            case "explore" -> "탐험도";
            default -> "탐험도(또는 모험)";
        };
    }

    /** 숙련 창 · 안내용 항목별 점수 */
    public static List<String> lines(PlayerData d) {
        List<String> out = new ArrayList<>();
        for (Part p : PARTS) {
            double v = Math.min(p.cap, amount(d, p) / p.per);
            out.add("&7" + p.label + " &f" + (int) v + " &8/ " + p.cap + (v >= p.cap ? " &a(최대)" : ""));
        }
        return out;
    }
}
