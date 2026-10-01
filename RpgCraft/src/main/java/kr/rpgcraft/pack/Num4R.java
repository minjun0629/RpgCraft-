package kr.rpgcraft.pack;

/** 자동 생성 (tools/num4r.py): 마크에이지 4R 풍 숫자 글꼴의 글자 폭 (다음 글자까지 = 폭 + 1) */
public final class Num4R {
    private Num4R() {}

    public static final String HUD_CHARS = "0123456789/,%.kMs+";
    public static final int[] HUD_W = {5, 5, 6, 5, 6, 6, 5, 5, 6, 6, 5, 3, 7, 3, 6, 7, 5, 5};
    public static final String LV_CHARS = "Lv.0123456789";
    public static final int[] LV_W = {6, 6, 3, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6};

    /** 표에 있는 글자의 다음 글자까지 거리 (없으면 0) */
    public static int adv(String chars, int[] w, char c) {
        int i = chars.indexOf(c);
        return i < 0 ? 0 : w[i] + 1;
    }

    public static int width(String chars, int[] w, String s) {
        int n = 0;
        for (char c : s.toCharArray()) n += adv(chars, w, c);
        return n;
    }
}
