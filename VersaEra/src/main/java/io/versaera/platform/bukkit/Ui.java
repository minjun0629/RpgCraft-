package io.versaera.platform.bukkit;

import io.versaera.domain.item.Quality;
import org.bukkit.ChatColor;

/**
 * 화면 문구 규칙: 짧게 · 숫자 · 상태만. 긴 설명은 도감 · 기록에.
 * 색: 금색(제목) · 흰색(값) · 회색(보조) · 빨강(오류). 그 밖의 장식은 쓰지 않는다.
 */
public final class Ui {
    private Ui() {
    }

    public static String c(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    public static String info(String s) {
        return c("&6◆ &f" + s);
    }

    public static String error(String s) {
        return c("&c✕ &7" + s);
    }

    public static String gradeColor(int quality) {
        return switch (Quality.grade(quality)) {
            case 0 -> "&8";
            case 1 -> "&7";
            case 2 -> "&f";
            case 3 -> "&a";
            case 4 -> "&b";
            default -> "&6";
        };
    }

    public static String danger(int d) {
        return d == 0 ? "&a안전" : d <= 2 ? "&e위험 " + d : d <= 4 ? "&6위험 " + d : "&c위험 " + d;
    }
}
