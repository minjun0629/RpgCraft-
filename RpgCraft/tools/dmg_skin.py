"""
v5.10.35 대미지 스킨 (메이플스토리 느낌): 스킨마다 다른 숫자 글꼴 (기본 폰트 개인용 글자 영역).
 - 스킨 i (1 부터): 글자 시작 = 0xE500 + (i-1) * 32
   +0~9 숫자 · +10 쉼표 (보통) / +16~25 숫자 · +26 쉼표 · +27 치명타 별 (치명타, 더 크게)
 - 스킨 아이템 아이콘: PAPER CustomModelData 12800 + i
순서 · 이름은 Java kr.rpgcraft.feature.DamageSkinManager.Skin 과 같아야 함.
"""
import math
import os

from PIL import Image, ImageDraw

GLYPH_BASE, ICON_BASE = 0xE500, 12800

# 굵은 숫자 5x7 (안쪽 칸) — 테두리 · 그림자를 더해 7x10 정도가 된다
DIG = {
    "0": ["01110", "11011", "11011", "11011", "11011", "11011", "01110"],
    "1": ["00110", "01110", "11110", "00110", "00110", "00110", "11111"],
    "2": ["01110", "11011", "00011", "00110", "01100", "11000", "11111"],
    "3": ["11110", "00011", "00011", "01110", "00011", "00011", "11110"],
    "4": ["00110", "01110", "11010", "11010", "11111", "00010", "00010"],
    "5": ["11111", "11000", "11110", "00011", "00011", "11011", "01110"],
    "6": ["01110", "11000", "11110", "11011", "11011", "11011", "01110"],
    "7": ["11111", "00011", "00110", "00110", "01100", "01100", "01100"],
    "8": ["01110", "11011", "11011", "01110", "11011", "11011", "01110"],
    "9": ["01110", "11011", "11011", "01111", "00011", "00011", "01110"],
    ",": ["0", "0", "0", "0", "1", "1", "0"],
}


def _rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


# (이름, 위 색, 아래 색, 테두리, 반짝임 색, 무늬)
SKINS = [
    ("gold", "fff3a0", "e09a10", "4a2a00", "ffffff", None),
    ("ice", "f0ffff", "48b8f0", "0a2a4a", "ffffff", "crystal"),
    ("fire", "fff07a", "ff3a10", "3a0800", "ffe8a0", "flame"),
    ("arcane", "f0c8ff", "8a3aff", "1e0838", "ffffff", "spark"),
    ("candy", "ffffff", "ff6ab8", "5a0a30", "ffffff", "stripe"),
    ("toxic", "e8ff7a", "3ac83a", "0a2a08", "f8ffc8", "bubble"),
    ("rainbow", None, None, "1a1a24", "ffffff", "rainbow"),
]
RAINBOW = ["ff4a4a", "ffa83a", "ffe84a", "5ae85a", "4ac8ff", "6a7aff", "c86aff"]


def _digit(ch, skin, scale, crit, idx=0):
    name, top, bot, edge, shine, pat = skin
    rows = DIG[ch]
    w, h = len(rows[0]), len(rows)
    W, H = w * scale + 2 * scale, h * scale + 2 * scale + scale
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    px = img.load()
    if pat == "rainbow":
        c = _rgb(RAINBOW[idx % len(RAINBOW)])
        top_c, bot_c = tuple(min(255, int(v * 0.5 + 128)) for v in c), tuple(int(v * 0.8) for v in c)
    else:
        top_c, bot_c = _rgb(top), _rgb(bot)
    if crit:   # 치명타: 조금 더 밝고 따뜻하게
        top_c = tuple(min(255, int(v * 0.8 + 60)) for v in top_c)
        bot_c = tuple(min(255, int(v * 0.85 + 40)) for v in bot_c)
    fill = set()
    for y in range(h):
        for x in range(w):
            if rows[y][x] == "1":
                for dy in range(scale):
                    for dx in range(scale):
                        fill.add((scale + x * scale + dx, scale + y * scale + dy))
    e = _rgb(edge) + (255,)
    for (x, y) in fill:   # 테두리 (주변 8칸) + 아래 그림자 한 줄
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1, 2):
                q = (x + dx, y + dy)
                if 0 <= q[0] < W and 0 <= q[1] < H and q not in fill:
                    px[q] = e if dy < 2 or dx == 0 else px[q]
    y0, y1 = scale, scale + h * scale
    for (x, y) in fill:
        t = (y - y0) / max(1, (y1 - y0 - 1))
        c = tuple(int(top_c[i] + (bot_c[i] - top_c[i]) * t) for i in range(3))
        if pat == "stripe" and ((x + y) // max(1, scale)) % 3 == 0:
            c = _rgb("ff2a8a")
        if pat == "crystal" and (x - y) % (3 * scale) == 0:
            c = (255, 255, 255)
        if pat == "flame" and t < 0.3 and (x * 7 + y * 3) % 5 == 0:
            c = (255, 255, 210)
        if pat == "bubble" and (x * 5 + y * 11) % 17 == 0:
            c = (240, 255, 200)
        if pat == "spark" and (x * 3 + y * 7) % 13 == 0:
            c = (255, 240, 255)
        px[x, y] = c + (255,)
    sh = _rgb(shine) + (255,)
    for (x, y) in fill:   # 위 왼쪽 반짝임
        if (x - 1, y) not in fill and (x, y - 1) not in fill:
            px[x, y] = sh
    return img


def _star(scale, skin):
    S = 9 * scale
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    pts = []
    for i in range(10):
        a = math.radians(-90 + i * 36)
        r = S * 0.48 if i % 2 == 0 else S * 0.2
        pts.append((S / 2 + math.cos(a) * r, S / 2 + math.sin(a) * r))
    top = _rgb(skin[1] or "ffe84a")
    d.polygon(pts, fill=top + (255,), outline=_rgb(skin[3]) + (255,))
    return img


def providers(pack_dir, ns):
    tex = os.path.join(pack_dir, "assets", ns, "textures", "font", "dmg")
    os.makedirs(tex, exist_ok=True)
    out = []
    for si, skin in enumerate(SKINS):
        base = GLYPH_BASE + si * 32
        for crit in (False, True):
            scale = 2 if crit else 1
            for i, ch in enumerate("0123456789,"):
                img = _digit(ch, skin, scale, crit, i)
                name = "%s_%s_%s" % (skin[0], "c" if crit else "n", "comma" if ch == "," else ch)
                img.save(os.path.join(tex, name + ".png"))
                h = img.height
                out.append({"type": "bitmap", "file": "%s:font/dmg/%s.png" % (ns, name), "ascent": h - 3 if not crit else h - 5, "height": h,
                            "chars": [chr(base + (16 if crit else 0) + i)]})
        st = _star(2, skin)
        st.save(os.path.join(tex, skin[0] + "_star.png"))
        out.append({"type": "bitmap", "file": "%s:font/dmg/%s_star.png" % (ns, skin[0]), "ascent": 14, "height": 18, "chars": [chr(base + 27)]})
    return out


def icon(si):
    """스킨 아이템 아이콘 (32x32): 어두운 카드 위에 그 스킨의 '777'"""
    skin = SKINS[si]
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([1, 3, 30, 28], 4, fill=(28, 24, 30, 255), outline=(200, 204, 214, 255))
    d.rounded_rectangle([3, 5, 28, 26], 3, outline=_rgb(skin[2] or "ff6a6a") + (255,))
    x = 5
    for i, ch in enumerate("777"):
        g = _digit(ch, skin, 1, False, i + 2)
        img.alpha_composite(g, (x, 9))
        x += g.width - 1
    d.polygon([(25, 4), (27, 8), (31, 9), (27, 10), (25, 14), (23, 10), (19, 9), (23, 8)], fill=(255, 255, 255, 230))
    return img


def write_icons(pack, ns, write_json):
    tex = os.path.join(pack, "assets", ns, "textures", "item", "ui")
    os.makedirs(tex, exist_ok=True)
    out = []
    for si, skin in enumerate(SKINS):
        n = "dmg_skin_" + skin[0]
        icon(si).save(os.path.join(tex, n + ".png"))
        write_json(os.path.join(pack, "assets", ns, "models", "item", "ui", n + ".json"),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": ns + ":item/ui/" + n}})
        out.append((ICON_BASE + si + 1, ns + ":item/ui/" + n))
    return out
