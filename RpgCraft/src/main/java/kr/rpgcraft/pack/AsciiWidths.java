package kr.rpgcraft.pack;

/** 자동 생성 (tools/build_resourcepack.py): 바닐라 ascii 글꼴 0x20~0x7E 글자 폭 */
public final class AsciiWidths {
    private AsciiWidths() {}

    public static final int[] W = {4, 2, 4, 6, 6, 6, 6, 2, 4, 4, 4, 6, 2, 6, 2, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 2, 2, 5, 6, 5, 6, 7, 6, 6, 6, 6, 6, 6, 6, 6, 4, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 4, 6, 4, 6, 6, 3, 6, 6, 6, 6, 6, 5, 6, 6, 2, 6, 5, 3, 6, 6, 6, 6, 6, 6, 6, 4, 6, 6, 6, 6, 6, 6, 4, 2, 4, 7};

    public static int of(char c) {
        return c >= 0x20 && c < 0x7F ? W[c - 0x20] : 6;
    }
}
