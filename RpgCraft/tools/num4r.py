"""
v5.10.49 마크에이지 4R 풍 숫자 글꼴 — 예전 3x5 픽셀 숫자 대신 굵은 기울임 숫자 + 어두운 테두리 (4배 해상도로 그려 부드럽게).
HUD 숫자 · 레벨 · 적 정보 체력 숫자 · 기본 대미지 숫자가 모두 이 글꼴을 쓴다.
비트맵 글꼴은 텍스처가 커도 height 로 화면 크기를 정하므로 (높이 7 → 텍스처 28px) 화면 배율이 클수록 매끈하게 보인다.
Java kr.rpgcraft.pack.Num4R (자동 생성) 의 폭 표와 같아야 함.
"""
import os

from PIL import Image, ImageDraw, ImageFont

SS = 4                                   # 4배로 그림
FONT = "/usr/share/fonts/truetype/liberation/LiberationSans-BoldItalic.ttf"
HUD_CHARS = "0123456789/,%.kMs+"


def _font(px):
    try:
        return ImageFont.truetype(FONT, px)
    except OSError:
        return ImageFont.truetype("DejaVuSans-Bold.ttf", px)


def glyph(ch, height, fill=(255, 255, 255), fill2=None, outline=(30, 18, 40), stroke=1.0, squeeze=0.82):
    """글자 하나 → (이미지, 화면 폭(px)). height: 화면 높이(px). fill2 가 있으면 위 → 아래 그라데이션"""
    H = height * SS
    st = max(1, int(round(stroke * SS)))
    f = _font(int(H * 1.18))
    pad = st + 2
    bbox = f.getbbox("0", stroke_width=st)
    asc_top = bbox[1]
    w = int(f.getlength(ch)) + pad * 2 + int(H * 0.25)
    img = Image.new("RGBA", (w, H + pad * 2), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # 테두리
    d.text((pad, pad - asc_top), ch, font=f, fill=outline + (255,), stroke_width=st, stroke_fill=outline + (255,))
    # 채움 (그라데이션)
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).text((pad, pad - asc_top), ch, font=f, fill=255)
    grad = Image.new("RGBA", img.size, fill + (255,))
    if fill2:
        gd = ImageDraw.Draw(grad)
        for y in range(img.height):
            t = max(0.0, min(1.0, (y - pad) / max(1, H)))
            gd.line([(0, y), (img.width, y)], fill=tuple(int(fill[i] + (fill2[i] - fill[i]) * t) for i in range(3)) + (255,))
    img.paste(grad, (0, 0), mask)
    # 세로 크기를 H 로 맞춰 자름 (위아래 여백 제거 후 H+테두리)
    bb = img.getbbox()
    if bb is None:
        return img.crop((0, 0, 1, H)), 1
    img = img.crop((0, max(0, pad - st), bb[2], max(0, pad - st) + H + 2 * st))
    img = img.resize((max(1, int(img.width * squeeze)), H), Image.LANCZOS)
    img.putpixel((img.width - 1, 0), (0, 0, 0, 1))   # 폭 고정
    # 마인크래프트 비트맵: 화면 폭 = round(텍스처 폭 * height / 텍스처 높이), 다음 글자까지 = 화면 폭 + 1
    screen_w = int(0.5 + img.width * height / img.height)
    return img, screen_w


def sheet(chars, height, **kw):
    """[(글자, 이미지, 화면 폭)]"""
    return [(c,) + glyph(c, height, **kw) for c in chars]


def write_java(path, tables):
    """tables: {이름: (글자들, [폭])} → Num4R.java (advance = 폭 + 1)"""
    lines = ["package kr.rpgcraft.pack;", "", "/** 자동 생성 (tools/num4r.py): 마크에이지 4R 풍 숫자 글꼴의 글자 폭 (다음 글자까지 = 폭 + 1) */",
             "public final class Num4R {", "    private Num4R() {}", ""]
    for name, (chars, widths) in tables.items():
        lines.append('    public static final String %s_CHARS = "%s";' % (name, chars))
        lines.append("    public static final int[] %s_W = {%s};" % (name, ", ".join(str(w) for w in widths)))
    lines += ["", "    /** 표에 있는 글자의 다음 글자까지 거리 (없으면 0) */",
              "    public static int adv(String chars, int[] w, char c) {",
              "        int i = chars.indexOf(c);", "        return i < 0 ? 0 : w[i] + 1;", "    }", "",
              "    public static int width(String chars, int[] w, String s) {",
              "        int n = 0;", "        for (char c : s.toCharArray()) n += adv(chars, w, c);", "        return n;", "    }", "}", ""]
    with open(path, "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines))


# ------------------------------------------------------------------ 기본 대미지 숫자 (기본 폰트, 대미지 스킨이 없을 때)
DMG_N, DMG_C = 0xE480, 0xE490          # 0~9 · 쉼표 (Java DamageSkinManager 와 같아야 함)
DMG_CHARS = "0123456789,"


def damage_providers(pack_dir, ns):
    tex = os.path.join(pack_dir, "assets", ns, "textures", "font", "dmg4r")
    os.makedirs(tex, exist_ok=True)
    out = []
    for crit in (False, True):
        h = 12 if crit else 9
        for i, ch in enumerate(DMG_CHARS):
            img, _ = glyph(ch, h, fill=(255, 255, 210) if crit else (255, 252, 240), fill2=(255, 186, 36) if crit else (255, 150, 70),
                           outline=(110, 22, 0) if crit else (66, 14, 10), stroke=1.2 if crit else 1.0, squeeze=0.9)
            n = ("c_" if crit else "n_") + ("comma" if ch == "," else ch)
            img.save(os.path.join(tex, n + ".png"))
            out.append({"type": "bitmap", "file": ns + ":font/dmg4r/" + n + ".png", "ascent": h - 2, "height": h,
                        "chars": [chr((DMG_C if crit else DMG_N) + i)]})
    return out


def hud_table():
    """Java Num4R 폭 표: HUD (HUD 숫자 높이 7 · 적 정보 바 숫자도 같은 글꼴), LV (적 정보 이름 칸 레벨, 높이 8)"""
    lv = "Lv.0123456789"
    return {"HUD": (HUD_CHARS, [glyph(c, 7)[1] for c in HUD_CHARS]), "LV": (lv, [glyph(c, 8)[1] for c in lv])}
