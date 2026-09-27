package kr.rpgcraft.pack;

import kr.rpgcraft.util.Text;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;

/**
 * 리소스팩 커스텀 HUD (폰트 rpgcraft:hud).
 * 액션바 한 줄에 서로 다른 높이(ascent)의 글리프를 섞어 바닐라 하트·갑옷 자리에 체력바/아이콘/숫자를 그린다.
 * 글리프 코드와 폭은 tools/ui_pack.py 와 반드시 일치해야 한다.
 *
 *   2행 (갑옷 줄)  ⚔ 공격력                         ✦ 크리 %
 *   1행 (하트 줄)  ❤[■■■■■■ 12345/20000 ]          🧪 n  ⚡ 쿨  🛡 방어 %
 *                 └──── 핫바 왼쪽 절반 ────┘        └──── 핫바 오른쪽 절반 ────┘
 */
public final class HudFont {
    public static final String FONT = "rpgcraft:hud";
    private static final int HP_BASE = 0xE200, HP_STEPS = 20;
    private static final char POTION = '\uE220', SKILL = '\uE221', SKILL_CD = '\uE222', DEF = '\uE223', SWORD = '\uE230', CRIT = '\uE231';
    private static final int ROW1 = 0xE240, ROW2 = 0xE260;
    private static final char QUICK = '\uE232', QUICK_CD = '\uE233';
    private static final String CHARS = "0123456789/,%.kMs+";
    private static final int BAR_W = 81, ICON_W = 9, LEFT = -91, RIGHT = 10, GAP = 3;

    private HudFont() {}

    private static int glyphWidth(char c) {
        return switch (c) {
            case '%', 'M' -> 5;
            case ',', '.' -> 1;
            default -> 3;
        };
    }

    /** 숫자 문자열 → 글리프 (row 1 또는 2), 폭을 out[0] 에 누적 */
    private static String digits(String s, int row, int[] cursor) {
        StringBuilder sb = new StringBuilder();
        int base = row == 1 ? ROW1 : ROW2;
        for (char c : s.toCharArray()) {
            int i = CHARS.indexOf(c);
            if (i < 0) continue;
            sb.append((char) (base + i));
            cursor[0] += glyphWidth(c) + 1;
        }
        return sb.toString();
    }

    private static int digitsWidth(String s) {
        int w = 0;
        for (char c : s.toCharArray()) if (CHARS.indexOf(c) >= 0) w += glyphWidth(c) + 1;
        return w;
    }

    private static String moveTo(int target, int[] cursor) {
        String s = PackManager.shift(target - cursor[0]);
        cursor[0] = target;
        return s;
    }

    private static String glyph(char g, int width, int[] cursor) {
        cursor[0] += width + 1;
        return String.valueOf(g);
    }

    public static String compact(double v) {
        long n = Math.round(v);
        if (n >= 10_000_000) return (n / 1_000_000) + "M";
        if (n >= 1_000_000) return (n / 1000) + "k";
        return String.valueOf(Math.max(0, n));
    }

    /**
     * @param skillCd 퀵 스킬 남은 초 (-1 = 퀵 스킬 없음, 0 = 사용 가능)
     */
    public static String build(double hp, double maxHp, int potions, int skillCd, double def, double atk, double crit) {
        return build(hp, maxHp, potions, skillCd, def, atk, crit, -1);
    }

    /** quickCd: 퀵 액티브 스킬 남은 초 (-1 = 없음) → 2행 오른쪽에 표시 */
    public static String build(double hp, double maxHp, int potions, int skillCd, double def, double atk, double crit, int quickCd) {
        int[] cur = {0};
        StringBuilder sb = new StringBuilder();
        // 1행 왼쪽: 체력 바 + 숫자
        double ratio = maxHp <= 0 ? 0 : Math.max(0, Math.min(1, hp / maxHp));
        int step = (int) Math.ceil(ratio * HP_STEPS);
        if (hp > 0 && step == 0) step = 1;
        sb.append(moveTo(LEFT, cur)).append(glyph((char) (HP_BASE + step), BAR_W, cur));
        String hpText = compact(hp) + "/" + compact(maxHp);
        int center = LEFT + 11 + 34;
        sb.append(moveTo(center - digitsWidth(hpText) / 2, cur)).append(digits(hpText, 1, cur));
        // 1행 오른쪽: 포션 · 퀵스킬 · 방어
        sb.append(moveTo(RIGHT, cur)).append(glyph(POTION, ICON_W, cur)).append(digits(String.valueOf(Math.min(999, potions)), 1, cur));
        if (skillCd >= 0) {
            sb.append(moveTo(cur[0] + GAP, cur));
            if (skillCd > 0) sb.append(glyph(SKILL_CD, ICON_W, cur)).append(digits(skillCd + "s", 1, cur));
            else sb.append(glyph(SKILL, ICON_W, cur));
        }
        sb.append(moveTo(cur[0] + GAP, cur)).append(glyph(DEF, ICON_W, cur)).append(digits(String.format("%.1f%%", def), 1, cur));
        // 2행: 공격력 · 크리티컬
        sb.append(moveTo(LEFT, cur)).append(glyph(SWORD, ICON_W, cur)).append(digits(compact(atk), 2, cur));
        sb.append(moveTo(RIGHT, cur)).append(glyph(CRIT, ICON_W, cur)).append(digits(String.format("%.1f%%", crit), 2, cur));
        if (quickCd >= 0) {
            sb.append(moveTo(cur[0] + GAP, cur));
            if (quickCd > 0) sb.append(glyph(QUICK_CD, ICON_W, cur)).append(digits(quickCd + "s", 2, cur));
            else sb.append(glyph(QUICK, ICON_W, cur));
        }
        // 전체 폭이 0 이 되도록 되돌려 화면 중앙 정렬을 고정
        sb.append(moveTo(0, cur));
        return sb.toString();
    }

    /**
     * 파티 HUD 에 쓸 이름. 파티 HUD 글꼴(rpgcraft:party0~4)은 화면 위쪽으로 올려 그리느라 영문·숫자 글리프만 있어서
     * /이름변경 으로 바꾼 한글 닉네임은 네모(□)로 깨지고 체력 바 위치도 어긋났다 → 그릴 수 없는 글자가 있으면 계정 이름(항상 영문)으로.
     */
    public static String hudName(String display, String account) {
        if (display == null || display.isEmpty()) return account;
        for (char ch : display.toCharArray()) if (ch < 0x20 || ch >= 0x7F) return account;
        return display;
    }

    /** 파티 HUD 한 줄 */
    public record PartyRow(String name, int level, double hp, double xp, boolean leader, boolean self, int dist) {}

    private static java.util.List<net.md_5.bungee.api.chat.BaseComponent> comp(String text, String font, net.md_5.bungee.api.ChatColor color) {
        java.util.List<net.md_5.bungee.api.chat.BaseComponent> l = new java.util.ArrayList<>();
        for (var b : TextComponent.fromLegacyText(text, color)) {
            b.setFont(font);
            b.setColor(color);
            l.add(b);
        }
        return l;
    }

    /**
     * 화면 왼쪽 파티원 목록: [아이콘] Lv.이름 / 체력 바 / 경험치 바.
     * 줄마다 다른 높이의 글꼴(rpgcraft:party0~4)을 쓰고, 한 줄을 그린 뒤 항상 제자리(0)로 돌아와 가운데 정렬을 유지한다.
     */
    public static java.util.List<net.md_5.bungee.api.chat.BaseComponent> party(java.util.List<PartyRow> rows, int leftX) {
        java.util.List<net.md_5.bungee.api.chat.BaseComponent> out = new java.util.ArrayList<>();
        var W = net.md_5.bungee.api.ChatColor.WHITE;
        for (int i = 0; i < rows.size() && i < 5; i++) {
            PartyRow r = rows.get(i);
            String font = "rpgcraft:party" + i;
            int x0 = -leftX, cur = 0;
            StringBuilder a = new StringBuilder();
            a.append(PackManager.shift(x0 - cur)).append(r.leader() ? '\uE301' : '\uE300').append(PackManager.shift(2));
            cur = x0 + 10;
            out.addAll(comp(a.toString(), font, W));
            String label = "Lv." + r.level() + " " + r.name() + (r.self() || r.dist() < 0 ? "" : " " + r.dist() + "m");
            int w = 0;
            for (char ch : label.toCharArray()) w += AsciiWidths.of(ch);
            String[] pal = {"#8CE8FF", "#8CFF9A", "#FFE066", "#FF9AE0", "#FFB060"};   // 파티원마다 다른 색
            out.addAll(comp(label, font, net.md_5.bungee.api.ChatColor.of(pal[i % pal.length])));
            cur += w;
            int hs = (int) Math.round(Math.max(0, Math.min(1, r.hp())) * 20), xs = (int) Math.round(Math.max(0, Math.min(1, r.xp())) * 20);
            if (r.hp() > 0 && hs == 0) hs = 1;
            StringBuilder b = new StringBuilder();
            b.append(PackManager.shift(x0 + 10 - cur)).append((char) (0xE310 + hs));
            cur = x0 + 10 + 42;
            b.append(PackManager.shift(x0 + 10 - cur)).append((char) (0xE330 + xs));
            cur = x0 + 10 + 42;
            b.append(PackManager.shift(-cur));
            out.addAll(comp(b.toString(), font, W));
        }
        return out;
    }

    /** HUD + 알림 문구 (기본 글꼴, HUD 위 줄). 가운데 정렬을 위해 폭을 추정해 좌우로 밀어준다 */
    public static void send(Player p, String hud, String notice) {
        send(p, hud, notice, java.util.List.of());
    }

    public static void send(Player p, String hud, String notice, java.util.List<net.md_5.bungee.api.chat.BaseComponent> extra) {
        if (!extra.isEmpty()) {
            java.util.List<net.md_5.bungee.api.chat.BaseComponent> all = new java.util.ArrayList<>();
            for (var b : TextComponent.fromLegacyText(hud, net.md_5.bungee.api.ChatColor.WHITE)) { b.setFont(FONT); all.add(b); }
            if (notice != null && !notice.isEmpty()) {
                int w = textWidth(notice);
                for (var b : TextComponent.fromLegacyText(PackManager.shift(-(w / 2)), net.md_5.bungee.api.ChatColor.WHITE)) { b.setFont(FONT); all.add(b); }
                all.addAll(java.util.Arrays.asList(TextComponent.fromLegacyText(notice, net.md_5.bungee.api.ChatColor.WHITE)));
                for (var b : TextComponent.fromLegacyText(PackManager.shift(-(w - w / 2)), net.md_5.bungee.api.ChatColor.WHITE)) { b.setFont(FONT); all.add(b); }
            }
            all.addAll(extra);
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR, all.toArray(new net.md_5.bungee.api.chat.BaseComponent[0]));
            return;
        }
        if (notice == null || notice.isEmpty()) {
            send(p, hud);
            return;
        }
        int w = textWidth(notice);
        java.util.List<net.md_5.bungee.api.chat.BaseComponent> out = new java.util.ArrayList<>();
        for (var b : TextComponent.fromLegacyText(hud + PackManager.shift(-(w / 2)), net.md_5.bungee.api.ChatColor.WHITE)) { b.setFont(FONT); out.add(b); }
        out.addAll(java.util.Arrays.asList(TextComponent.fromLegacyText(notice, net.md_5.bungee.api.ChatColor.WHITE)));
        for (var b : TextComponent.fromLegacyText(PackManager.shift(-(w - w / 2)), net.md_5.bungee.api.ChatColor.WHITE)) { b.setFont(FONT); out.add(b); }
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, out.toArray(new net.md_5.bungee.api.chat.BaseComponent[0]));
    }

    /** 기본 글꼴 기준 대략적인 글자 폭 (영문/숫자 표 + 한글·기호 9) */
    public static int textWidth(String legacy) {
        int w = 0;
        boolean bold = false;
        for (int i = 0; i < legacy.length(); i++) {
            char c = legacy.charAt(i);
            if (c == '§' && i + 1 < legacy.length()) {
                char k = Character.toLowerCase(legacy.charAt(++i));
                if (k == 'l') bold = true;
                else if ("0123456789abcdefr".indexOf(k) >= 0) bold = false;
                continue;
            }
            int cw;
            if (c == ' ') cw = 4;
            else if ("il!|.,:;'".indexOf(c) >= 0) cw = 2;
            else if ("`I[]t".indexOf(c) >= 0) cw = 4;
            else if ("fk\"()*<>{}".indexOf(c) >= 0) cw = 5;
            else if (c < 128) cw = 6;
            else cw = 9;
            w += cw + (bold ? 1 : 0);
        }
        return w;
    }

    public static void send(Player p, String hud) {
        net.md_5.bungee.api.chat.BaseComponent[] parts = TextComponent.fromLegacyText(hud, net.md_5.bungee.api.ChatColor.WHITE);
        for (net.md_5.bungee.api.chat.BaseComponent b : parts) b.setFont(FONT);
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR, parts);
    }

    /** 채팅/탭 레벨 배지 글리프 (기본 폰트) */
    public static char badge(int level) {
        int tier = level < 20 ? 0 : level < 40 ? 1 : level < 60 ? 2 : level < 80 ? 3 : level < 100 ? 4 : 5;
        return (char) (0xE040 + tier);
    }

    public static final String LOGO = "\uE030";

    @SuppressWarnings("unused")
    private static String c(String s) {
        return Text.c(s);
    }
}
