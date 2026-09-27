"""
장신구 · 큐브 · 주문서 · 부적 · 봉인석 · 보물 지도 · 물고기 · 각인석 등 아이콘 (64x64 HD)
256px 로 그린 뒤 부드럽게 줄여서 반사광·그라데이션이 살아 있는 아이콘을 만든다.
"""
import math
from PIL import Image, ImageDraw, ImageFilter

S = 256


def _new():
    return Image.new("RGBA", (S, S), (0, 0, 0, 0))


def _done(img, outline=True):
    small = img.resize((64, 64), Image.LANCZOS)
    if outline:   # 얇은 어두운 외곽선
        a = small.split()[3]
        grown = a.filter(ImageFilter.MaxFilter(3))
        edge = Image.new("RGBA", small.size, (18, 14, 22, 255))
        edge.putalpha(Image.eval(grown, lambda v: 255 if v > 90 else 0))
        edge.alpha_composite(small)
        small = edge
    import pack_art
    pack_art.LAST_KEYS = {}
    return small


def _c(h, a=255):
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def _mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(4))


def _grad_ellipse(d, box, top, bot, steps=24):
    x0, y0, x1, y1 = box
    for i in range(steps):
        t = i / (steps - 1)
        inset = t * min(x1 - x0, y1 - y0) * 0.45
        d.ellipse((x0 + inset, y0 + inset * 0.8, x1 - inset, y1 - inset * 1.2), fill=_mix(bot, top, t))


def _gem(d, cx, cy, r, col, shape="round"):
    base = _c(col)
    dark = _mix(base, (0, 0, 0, 255), 0.45)
    light = _mix(base, (255, 255, 255, 255), 0.6)
    if shape == "drop":
        pts = [(cx, cy - r * 1.4), (cx + r, cy + r * 0.2), (cx, cy + r), (cx - r, cy + r * 0.2)]
        d.polygon(pts, fill=dark)
        d.polygon([(cx, cy - r * 1.1), (cx + r * 0.7, cy + r * 0.15), (cx, cy + r * 0.7), (cx - r * 0.7, cy + r * 0.15)], fill=base)
        d.polygon([(cx - r * 0.15, cy - r * 0.9), (cx - r * 0.55, cy + r * 0.05), (cx - r * 0.15, cy + r * 0.05)], fill=light)
    else:
        d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=dark)
        d.ellipse((cx - r * 0.82, cy - r * 0.82, cx + r * 0.82, cy + r * 0.82), fill=base)
        d.polygon([(cx, cy - r * 0.8), (cx + r * 0.8, cy), (cx, cy + r * 0.8), (cx - r * 0.8, cy)], fill=_mix(base, light, 0.35))
        d.ellipse((cx - r * 0.55, cy - r * 0.6, cx - r * 0.1, cy - r * 0.2), fill=light)
    d.ellipse((cx + r * 0.3, cy - r * 0.75, cx + r * 0.5, cy - r * 0.55), fill=(255, 255, 255, 255))


def _sparkle(d, x, y, r, col=(255, 255, 255, 255)):
    d.polygon([(x, y - r), (x + r * 0.22, y - r * 0.22), (x + r, y), (x + r * 0.22, y + r * 0.22), (x, y + r), (x - r * 0.22, y + r * 0.22), (x - r, y), (x - r * 0.22, y - r * 0.22)], fill=col)


METALS = {1: ("b87333", "e8a86a", "6a3a1a"), 2: ("c0c8d4", "ffffff", "6a7280"), 3: ("f0c040", "fff3b0", "8a6010")}
GEMS = {"ring": ["e04848", "3f7fff", "b050ff"], "neck": ["3fc0ff", "5aff9a", "ff5ab0"], "ear": ["b8a0ff", "ffd23f", "7fffe0"]}


def ring(tier):
    img = _new()
    d = ImageDraw.Draw(img)
    m, hi, lo = METALS[tier]
    _grad_ellipse(d, (48, 92, 208, 228), _c(hi), _c(lo))           # 링 몸통
    d.ellipse((82, 124, 174, 204), fill=(0, 0, 0, 0))                # 구멍
    d.arc((56, 100, 200, 220), 200, 330, fill=_c(hi), width=6)       # 윗면 반사광
    d.polygon([(96, 104), (128, 64), (160, 104), (128, 118)], fill=_c(m))   # 보석 받침
    _gem(d, 128, 78, 30 + tier * 4, GEMS["ring"][tier - 1])
    for k in range(tier):
        _sparkle(d, 196 - k * 18, 60 + k * 26, 12)
    return _done(img)


def necklace(tier):
    img = _new()
    d = ImageDraw.Draw(img)
    m, hi, lo = METALS[tier]
    for i in range(22):                                              # 사슬
        t = i / 21
        a = math.pi * (0.1 + 0.8 * t)
        x, y = 128 - math.cos(a) * 96, 40 + math.sin(a) * 110
        d.ellipse((x - 7, y - 7, x + 7, y + 7), fill=_c(lo))
        d.ellipse((x - 5, y - 6, x + 3, y + 2), fill=_c(hi))
    d.polygon([(128, 150), (164, 186), (128, 238), (92, 186)], fill=_c(m))   # 펜던트 틀
    d.polygon([(128, 160), (154, 186), (128, 226), (102, 186)], fill=_c(lo))
    _gem(d, 128, 190, 22 + tier * 3, GEMS["neck"][tier - 1], "drop")
    if tier >= 2:
        d.ellipse((118, 138, 138, 158), fill=_c(hi))
    _sparkle(d, 176, 170, 12 + tier * 2)
    return _done(img)


def earring(tier):
    img = _new()
    d = ImageDraw.Draw(img)
    m, hi, lo = METALS[tier]
    d.arc((92, 20, 164, 92), 120, 420, fill=_c(lo), width=12)        # 고리
    d.arc((92, 20, 164, 92), 150, 300, fill=_c(hi), width=5)
    d.rectangle((122, 86, 134, 118), fill=_c(m))                      # 연결부
    d.ellipse((114, 108, 142, 136), fill=_c(m))
    _gem(d, 128, 122, 10, GEMS["ear"][(tier + 1) % 3])
    _gem(d, 128, 190, 30 + tier * 5, GEMS["ear"][tier - 1], "drop")  # 늘어진 보석
    if tier == 3:
        for sx in (-1, 1):
            d.line((128 + sx * 16, 140, 128 + sx * 40, 214), fill=_c(hi), width=5)
            _gem(d, 128 + sx * 42, 218, 10, "ffffff")
    _sparkle(d, 176, 150, 14)
    return _done(img)


def scroll(col="a060ff"):
    img = _new()
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((52, 60, 204, 196), 18, fill=_c("f2e2b8"))   # 양피지
    for y in range(70, 190, 14):
        d.line((70, y, 186, y), fill=_c("d8c090"), width=2)
    for x in (40, 216):                                              # 말린 양 끝
        d.ellipse((x - 18, 50, x + 18, 206), fill=_c("e0cc98"))
        d.ellipse((x - 10, 58, x + 10, 198), fill=_c("c8b078"))
    d.ellipse((96, 96, 160, 160), outline=_c(col), width=6)          # 빛나는 룬
    d.polygon([(128, 104), (148, 128), (128, 152), (108, 128)], outline=_c(col), width=5)
    _sparkle(d, 128, 128, 14, _c(col))
    d.rectangle((118, 186, 138, 236), fill=_c(col))                  # 리본
    d.polygon([(118, 236), (128, 222), (138, 236), (138, 246), (118, 246)], fill=_c(col))
    return _done(img)


def cube(face, edge):
    img = _new()
    d = ImageDraw.Draw(img)
    top = [(128, 34), (216, 80), (128, 126), (40, 80)]
    left = [(40, 80), (128, 126), (128, 226), (40, 180)]
    right = [(216, 80), (128, 126), (128, 226), (216, 180)]
    f = _c(face)
    d.polygon(top, fill=_mix(f, (255, 255, 255, 255), 0.35))
    d.polygon(left, fill=f)
    d.polygon(right, fill=_mix(f, (0, 0, 0, 255), 0.35))
    e = _c(edge)
    for poly in (top, left, right):
        d.line(poly + [poly[0]], fill=e, width=6)
    d.line(((84, 103), (84, 203)), fill=e, width=3)                   # 면의 룬 줄
    d.line(((172, 103), (172, 203)), fill=e, width=3)
    d.line(((84, 57), (172, 103)), fill=e, width=3)
    _sparkle(d, 128, 80, 20, e)
    _sparkle(d, 200, 40, 14)
    return _done(img)


def talisman():
    img = _new()
    d = ImageDraw.Draw(img)
    d.polygon([(80, 40), (176, 40), (176, 214), (128, 236), (80, 214)], fill=_c("f2d86a"))   # 부적 종이
    d.rectangle((92, 52, 164, 204), outline=_c("c0302a"), width=5)
    for i, y in enumerate((74, 106, 138, 170)):                      # 붉은 글씨
        d.line((110, y, 146, y + (8 if i % 2 else -8)), fill=_c("c0302a"), width=7)
        d.line((128, y - 12, 128, y + 14), fill=_c("c0302a"), width=6)
    for k in range(6):                                               # 푸른 영혼불
        t = k / 5
        d.ellipse((150 + t * 30, 20 + t * 30 - 12, 190 + t * 20, 60 + t * 30), fill=_c("7fe8ff", 200 - k * 25))
    _sparkle(d, 196, 36, 14, _c("c8f8ff"))
    return _done(img)


def balrog_seal():
    img = _new()
    d = ImageDraw.Draw(img)
    d.polygon([(128, 20), (222, 84), (196, 212), (60, 212), (34, 84)], fill=_c("1e1418"))   # 흑요석 오각 석판
    d.polygon([(128, 36), (206, 90), (184, 200), (72, 200), (50, 90)], fill=_c("2e2028"))
    for (x0, y0, x1, y1) in [(128, 60, 104, 120), (104, 120, 128, 150), (128, 150, 160, 190), (150, 80, 176, 130)]:
        d.line((x0, y0, x1, y1), fill=_c("ff5a1f"), width=8)             # 용암 균열
        d.line((x0, y0, x1, y1), fill=_c("ffd23f"), width=3)
    d.polygon([(96, 96), (80, 64), (108, 88)], fill=_c("d8c8a8"))    # 뿔 문양
    d.polygon([(160, 96), (176, 64), (148, 88)], fill=_c("d8c8a8"))
    d.ellipse((104, 110, 152, 158), outline=_c("ff2a3a"), width=6)
    _sparkle(d, 128, 134, 12, _c("ff8a5a"))
    return _done(img)


def treasure_map():
    img = _new()
    d = ImageDraw.Draw(img)
    d.polygon([(40, 56), (100, 40), (156, 60), (216, 44), (216, 208), (156, 224), (100, 204), (40, 220)], fill=_c("e8d4a0"))
    d.line((100, 40, 100, 204), fill=_c("c8b078"), width=3)
    d.line((156, 60, 156, 224), fill=_c("c8b078"), width=3)
    d.ellipse((60, 150, 100, 186), fill=_c("7fb86a"))                # 섬
    d.ellipse((150, 80, 200, 130), fill=_c("7fb86a"))
    for k in range(9):                                               # 점선 길
        t = k / 8
        x, y = 80 + t * 96, 168 - t * 64 + math.sin(t * 6) * 10
        d.ellipse((x - 4, y - 4, x + 4, y + 4), fill=_c("8a5a2b"))
    d.line((166, 92, 186, 116), fill=_c("d02020"), width=9)           # X
    d.line((186, 92, 166, 116), fill=_c("d02020"), width=9)
    return _done(img)


FISH = {"fish_small": ("9aa4b0", "d8dee6", 0.8), "fish_carp": ("b08a3a", "e8cc80", 1.0), "fish_salmon": ("d06a5a", "f0b0a0", 1.05),
        "fish_deep": ("2a3a7a", "7fe8ff", 1.0), "fish_gold": ("e0a020", "fff0a0", 1.05), "fish_legend": ("3a2a8a", "ffd23f", 1.15)}


def fish(name):
    body, belly, sc = FISH[name]
    img = _new()
    d = ImageDraw.Draw(img)
    cx, cy = 118, 128
    L, H = 150 * sc, 70 * sc
    d.polygon([(cx + L * 0.45, cy), (cx + L * 0.75, cy - H * 0.6), (cx + L * 0.7, cy), (cx + L * 0.75, cy + H * 0.6)], fill=_c(body))   # 꼬리
    _grad_ellipse(d, (cx - L / 2, cy - H / 2, cx + L / 2, cy + H / 2), _c(belly), _c(body), 18)
    d.polygon([(cx - 10, cy - H / 2 + 4), (cx + 20, cy - H * 0.95), (cx + 40, cy - H / 2 + 6)], fill=_c(body))   # 등지느러미
    for k in range(5):                                                # 비늘
        x = cx - L * 0.1 + k * L * 0.1
        d.arc((x - 12, cy - 18, x + 12, cy + 6), 200, 340, fill=_mix(_c(body), (0, 0, 0, 255), 0.25), width=3)
    d.ellipse((cx - L * 0.36, cy - 12, cx - L * 0.24, cy), fill=(255, 255, 255, 255))   # 눈
    d.ellipse((cx - L * 0.33, cy - 9, cx - L * 0.27, cy - 3), fill=(20, 20, 30, 255))
    if name == "fish_legend":                                         # 용왕어: 수염 · 왕관 지느러미 · 빛
        d.line((cx - L / 2, cy + 6, cx - L / 2 - 30, cy + 30), fill=_c("ffd23f"), width=5)
        d.line((cx - L / 2, cy + 10, cx - L / 2 - 20, cy + 44), fill=_c("ffd23f"), width=4)
        for k in range(3):
            d.polygon([(cx - 20 + k * 22, cy - H / 2), (cx - 10 + k * 22, cy - H * 0.9), (cx + k * 22, cy - H / 2)], fill=_c("ffd23f"))
        _sparkle(d, 200, 60, 16, _c("fff3b0"))
    if name in ("fish_gold", "fish_deep"):
        _sparkle(d, 190, 70, 12)
    return _done(img)


def chest():
    img = _new()
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((40, 96, 216, 220), 10, fill=_c("8a5a2b"))
    d.pieslice((40, 40, 216, 152), 180, 360, fill=_c("a06a36"))
    for x in (40, 120, 200):
        d.rectangle((x, 60 if x == 120 else 96, x + 16, 220), fill=_c("e0b030"))
    d.rectangle((40, 96, 216, 110), fill=_c("e0b030"))
    d.rectangle((112, 110, 144, 142), fill=_c("fff0a0"))
    for (x, y) in [(60, 200), (190, 130), (80, 120)]:                  # 따개비
        d.ellipse((x - 8, y - 8, x + 8, y + 8), fill=_c("d8d0c0"))
    _sparkle(d, 200, 60, 14)
    return _done(img)


SEALS = {"tyrant": "ff3a3a", "immortal": "ffd23f", "storm_eye": "6bd8ff", "midas": "ffb030", "judge": "e8e0ff"}


def legend_seal(kind):
    col = SEALS[kind]
    img = _new()
    d = ImageDraw.Draw(img)
    d.polygon([(128, 16), (196, 88), (176, 220), (80, 220), (60, 88)], fill=_c("1a2a3a"))   # 각인석 결정
    d.polygon([(128, 30), (182, 92), (166, 206), (90, 206), (74, 92)], fill=_mix(_c(col), (0, 0, 0, 255), 0.55))
    d.polygon([(128, 30), (182, 92), (128, 120), (74, 92)], fill=_mix(_c(col), (255, 255, 255, 255), 0.25))
    d.ellipse((100, 104, 156, 160), outline=_c(col), width=7)          # 각인
    d.polygon([(128, 112), (146, 132), (128, 152), (110, 132)], fill=_c(col))
    _sparkle(d, 128, 132, 10)
    _sparkle(d, 190, 40, 16, _c(col))
    return _done(img)


def steel_shard():
    img = _new()
    d = ImageDraw.Draw(img)
    d.polygon([(70, 200), (100, 60), (150, 30), (190, 120), (160, 210)], fill=_c("6a7280"))
    d.polygon([(100, 60), (150, 30), (140, 110)], fill=_c("c0c8d4"))
    d.polygon([(140, 110), (190, 120), (160, 210)], fill=_c("4a5260"))
    d.line((110, 90, 150, 170), fill=_c("ff7a3a"), width=4)
    return _done(img)


# ================================================================ 전리품 아이콘
def _blob(d, cx, cy, rx, ry, top, bot, steps=16):
    _grad_ellipse(d, (cx - rx, cy - ry, cx + rx, cy + ry), _c(top), _c(bot), steps)


def loot(name):
    img = _new()
    d = ImageDraw.Draw(img)
    if name == "loot_hide":
        d.polygon([(50, 70), (90, 40), (128, 60), (170, 38), (206, 72), (196, 150), (214, 206), (150, 190), (106, 214), (60, 196), (44, 140)], fill=_c("8a5a34"))
        d.polygon([(70, 80), (128, 72), (186, 84), (178, 150), (128, 176), (78, 150)], fill=_c("a8703f"))
        for k in range(6):
            d.line((80 + k * 18, 100, 90 + k * 18, 140), fill=_c("7a4a28"), width=3)
    elif name == "loot_meat":
        _blob(d, 138, 118, 76, 58, "f08a7a", "b0403a")
        d.ellipse((96, 94, 168, 140), fill=_c("fff0e8"))                      # 지방 줄
        _blob(d, 138, 118, 30, 22, "e06a5a", "a03030")
        d.rounded_rectangle((34, 150, 96, 176), 12, fill=_c("f2ecd8"))        # 뼈
        d.ellipse((24, 140, 50, 166), fill=_c("f2ecd8")); d.ellipse((24, 160, 50, 186), fill=_c("f2ecd8"))
    elif name == "loot_feather":
        d.line((60, 220, 196, 36), fill=_c("c8c0b0"), width=6)
        for k in range(14):
            t = k / 13
            x, y = 60 + t * 136, 220 - t * 184
            ln = 50 * math.sin(math.pi * (0.15 + 0.85 * t))
            d.line((x, y, x - ln * 0.8, y - ln * 0.2), fill=_c("f4f4f8"), width=10)
            d.line((x, y, x + ln * 0.2, y + ln * 0.7), fill=_c("dadae6"), width=10)
    elif name == "loot_wool":
        for (x, y, r) in [(90, 130, 50), (150, 120, 56), (120, 90, 44), (170, 160, 40), (96, 170, 40)]:
            _blob(d, x, y, r, r * 0.9, "ffffff", "cfcfd8")
    elif name == "loot_horn":
        pts = [(60, 210), (70, 150), (100, 100), (150, 64), (206, 50)]
        for i in range(len(pts) - 1):
            w = 34 - i * 7
            d.line((pts[i], pts[i + 1]), fill=_c("d8c8a8"), width=w)
            d.line((pts[i][0] + 4, pts[i][1] - 4, pts[i + 1][0] + 4, pts[i + 1][1] - 4), fill=_c("f2ead8"), width=max(3, w // 4))
        for k in range(4):
            x, y = pts[k]
            d.line((x - 14, y - 6, x + 14, y + 6), fill=_c("a89878"), width=3)
    elif name == "loot_pelt":
        d.polygon([(40, 60), (100, 34), (128, 58), (156, 34), (216, 60), (208, 140), (224, 210), (128, 190), (32, 210), (48, 140)], fill=_c("d8b890"))
        d.polygon([(70, 70), (128, 66), (186, 70), (180, 150), (128, 170), (76, 150)], fill=_c("f0dcb8"))
        for (x, y) in [(90, 100), (150, 96), (120, 140), (170, 150), (80, 150)]:
            d.ellipse((x - 10, y - 8, x + 10, y + 8), fill=_c("8a6a44"))
        _sparkle(d, 200, 40, 14)
    elif name == "loot_bone":
        d.line((70, 190, 186, 66), fill=_c("e8e0cc"), width=26)
        for (x, y) in [(58, 186), (74, 204), (182, 52), (198, 70)]:
            d.ellipse((x - 18, y - 18, x + 18, y + 18), fill=_c("e8e0cc"))
        d.line((86, 170, 170, 80), fill=_c("fffaf0"), width=6)
        d.line((120, 120, 132, 134), fill=_c("a89878"), width=5)
    elif name == "loot_slime":
        _blob(d, 128, 150, 86, 66, "b8ff8a", "4ab83a")
        d.ellipse((92, 110, 124, 136), fill=_c("e8ffd8"))
        d.ellipse((150, 160, 164, 174), fill=_c("2a8a2a")); d.ellipse((104, 164, 116, 176), fill=_c("2a8a2a"))
    elif name == "loot_fang":
        d.polygon([(90, 40), (170, 40), (140, 220)], fill=_c("f2ead8"))
        d.polygon([(100, 40), (130, 40), (136, 200)], fill=_c("ffffff"))
        d.rounded_rectangle((80, 30, 180, 60), 10, fill=_c("c0b090"))
        d.line((110, 120, 150, 110), fill=_c("c0b090"), width=3)
    elif name == "loot_venom":
        _blob(d, 128, 150, 64, 70, "b8ff3a", "4a8a1a")                      # 독주머니
        d.rounded_rectangle((110, 60, 146, 96), 8, fill=_c("5a3a2a"))
        d.ellipse((96, 120, 126, 150), fill=_c("eaffc0"))
        for k in range(3):
            d.ellipse((150 + k * 12, 196 + k * 10, 162 + k * 12, 210 + k * 10), fill=_c("9cff4a"))
    elif name == "loot_dust":
        for k in range(40):
            a, r = k * 2.39, math.sqrt(k) * 13
            x, y = 128 + math.cos(a) * r, 160 + math.sin(a) * r * 0.45 - (40 - k)
            d.ellipse((x - 9, y - 9, x + 9, y + 9), fill=_c("fff0a0" if k % 3 else "ffd23f"))
        _sparkle(d, 170, 70, 18, _c("fff8d0"))
        _sparkle(d, 86, 100, 12, _c("fff8d0"))
    elif name == "loot_ink":
        d.rounded_rectangle((80, 90, 176, 220), 24, fill=_c("1a1428"))       # 병
        d.rounded_rectangle((104, 50, 152, 96), 8, fill=_c("2a2440"))
        d.rectangle((100, 40, 156, 56), fill=_c("8a5a2b"))
        d.rounded_rectangle((90, 130, 166, 210), 18, fill=_c("0a0610"))
        d.ellipse((100, 104, 120, 150), fill=_c("6a5a9a"))                    # 반사광
        for k in range(3):
            d.ellipse((150 + k * 16, 60 - k * 12, 166 + k * 16, 76 - k * 12), fill=_c("4a3a7a", 200 - k * 50))
    elif name == "loot_totem":
        d.polygon([(70, 220), (80, 60), (128, 30), (176, 60), (186, 220)], fill=_c("8a4a2a"))
        d.rectangle((90, 80, 166, 120), fill=_c("c06a3a"))
        d.ellipse((100, 88, 120, 108), fill=_c("fff0a0")); d.ellipse((136, 88, 156, 108), fill=_c("fff0a0"))
        d.polygon([(110, 140), (146, 140), (128, 170)], fill=_c("2a1a10"))
        d.line((70, 190, 186, 180), fill=_c("3a8a3a"), width=8)
        d.line((120, 30, 70, 20), fill=_c("e04848"), width=8)                   # 깃털 장식
    elif name == "loot_bandage":
        _blob(d, 128, 128, 80, 80, "f2ecd8", "b8ac88")
        for r in (60, 44, 28):
            d.arc((128 - r, 128 - r, 128 + r, 128 + r), 0, 300, fill=_c("a89868"), width=4)
        d.polygon([(190, 150), (240, 180), (230, 200), (180, 176)], fill=_c("e8dcc0"))
        d.ellipse((150, 100, 162, 112), fill=_c("8a6a3a"))
    elif name == "loot_frost":
        for (dx, h, w) in [(0, 150, 36), (-40, 100, 26), (40, 110, 26)]:
            x = 128 + dx
            d.polygon([(x, 230 - h - 40), (x + w, 220 - h * 0.4), (x, 226), (x - w, 220 - h * 0.4)], fill=_c("9fe0ff"))
            d.polygon([(x, 230 - h - 40), (x - w, 220 - h * 0.4), (x, 226)], fill=_c("e0f8ff"))
        _sparkle(d, 190, 50, 16)
    elif name == "loot_ember":
        _blob(d, 128, 160, 70, 56, "4a2a1a", "1a0a08")
        for (x, y, w) in [(128, 150, 70), (104, 170, 40), (154, 168, 44)]:
            d.polygon([(x - w / 2, y), (x, y - w * 1.4), (x + w / 2, y)], fill=_c("ff7a1f"))
            d.polygon([(x - w / 4, y), (x, y - w * 0.8), (x + w / 4, y)], fill=_c("ffd23f"))
        for (x, y) in [(90, 150), (170, 150), (128, 190)]:
            d.line((x - 10, y, x + 10, y + 6), fill=_c("ff5a1f"), width=4)
    elif name == "loot_scale":
        for (x, y) in [(100, 100), (156, 100), (128, 150), (80, 150), (176, 150), (104, 196), (152, 196)]:
            d.pieslice((x - 34, y - 40, x + 34, y + 28), 0, 180, fill=_c("5a3a8a"))
            d.pieslice((x - 26, y - 34, x + 26, y + 18), 0, 180, fill=_c("8a5ac0"))
            d.arc((x - 20, y - 26, x + 20, y + 10), 20, 80, fill=_c("d0b0ff"), width=4)
    elif name == "loot_eye":
        _blob(d, 128, 128, 86, 86, "f4f0f8", "b0a8c0")
        _blob(d, 128, 128, 46, 46, "c060ff", "4a1a8a")
        d.ellipse((112, 112, 144, 144), fill=_c("0a0610"))
        d.ellipse((104, 100, 120, 116), fill=(255, 255, 255, 255))
        for k in range(6):                                                   # 핏줄
            a = k * math.pi / 3 + 0.3
            d.line((128 + math.cos(a) * 60, 128 + math.sin(a) * 60, 128 + math.cos(a) * 82, 128 + math.sin(a) * 82), fill=_c("d04040"), width=3)
    elif name == "loot_core":
        for k in range(8):                                                   # 빛살
            a = k * math.pi / 4
            d.line((128, 128, 128 + math.cos(a) * 110, 128 + math.sin(a) * 110), fill=_c("c8a0ff", 180), width=6)
        d.polygon([(128, 40), (196, 128), (128, 216), (60, 128)], fill=_c("6a2ab0"))
        d.polygon([(128, 60), (176, 128), (128, 196), (80, 128)], fill=_c("a060ff"))
        d.polygon([(128, 60), (176, 128), (128, 128)], fill=_c("d8b0ff"))
        _blob(d, 128, 128, 20, 20, "ffffff", "d8b0ff")
        _sparkle(d, 196, 50, 16)
    elif name == "loot_crown":
        d.polygon([(40, 200), (50, 100), (90, 150), (128, 70), (160, 150), (150, 200)], fill=_c("c9a13b"))   # 부서진 왕관 조각
        d.polygon([(40, 200), (150, 200), (146, 180), (44, 180)], fill=_c("8a6a1a"))
        d.polygon([(150, 200), (160, 150), (176, 176), (170, 210)], fill=_c("8a6a1a"))
        _gem(d, 128, 150, 16, "e04848")
        _gem(d, 70, 150, 10, "3f7fff")
        for (x, y) in [(50, 100), (128, 70)]:
            d.ellipse((x - 9, y - 9, x + 9, y + 9), fill=_c("fff0a0"))
        _sparkle(d, 196, 60, 18, _c("fff3b0"))
    elif name == "loot_wave":
        d.line((70, 30, 70, 230), fill=_c("6a4a2a"), width=10)               # 깃대
        d.polygon([(76, 40), (200, 60), (170, 100), (206, 140), (76, 130)], fill=_c("c02a2a"))
        d.polygon([(76, 40), (200, 60), (186, 80), (76, 70)], fill=_c("e05a5a"))
        d.ellipse((110, 70, 146, 106), fill=_c("ffd23f"))
        d.line((150, 110, 200, 170), fill=_c("c02a2a"), width=6)               # 찢어진 자락
    else:
        return steel_shard()
    return _done(img)


LOOT_IDS = ["loot_hide", "loot_meat", "loot_feather", "loot_wool", "loot_horn", "loot_pelt", "loot_bone", "loot_slime", "loot_fang", "loot_venom",
            "loot_dust", "loot_ink", "loot_totem", "loot_bandage", "loot_frost", "loot_ember", "loot_scale", "loot_eye", "loot_core", "loot_crown", "loot_wave"]


# ================================================================ 잡화 상점 재료 · 약초 · 목재 · 결정
def _ingot(d, top, mid, bot):
    d.polygon([(40, 150), (90, 100), (216, 100), (166, 150)], fill=_c(top))
    d.polygon([(40, 150), (166, 150), (166, 196), (40, 196)], fill=_c(mid))
    d.polygon([(166, 150), (216, 100), (216, 146), (166, 196)], fill=_c(bot))
    d.line((60, 140, 190, 110), fill=(255, 255, 255, 160), width=5)


def goods(name):
    img = _new()
    d = ImageDraw.Draw(img)
    if name == "mat_stone":
        d.polygon([(50, 170), (70, 90), (130, 60), (200, 90), (214, 170), (150, 214), (86, 210)], fill=_c("7a7a80"))
        d.polygon([(70, 90), (130, 60), (200, 90), (128, 120)], fill=_c("a0a0a8"))
        for (x, y) in [(100, 150), (160, 140), (130, 180)]:
            d.ellipse((x - 8, y - 6, x + 8, y + 6), fill=_c("5a5a60"))
    elif name == "mat_iron":
        _ingot(d, "d8dce2", "a8b0b8", "7a828c")
    elif name == "mat_silver":
        _ingot(d, "f4f6fa", "c8ccd8", "9aa0b0")
        _sparkle(d, 196, 80, 14)
    elif name == "mat_gold":
        _ingot(d, "fff0a0", "f0c040", "b08010")
        _sparkle(d, 196, 80, 14)
    elif name == "mat_crystal":
        d.polygon([(128, 30), (200, 100), (128, 226), (56, 100)], fill=_c("5ad8ff"))
        d.polygon([(128, 30), (200, 100), (128, 110), (56, 100)], fill=_c("bff4ff"))
        d.polygon([(128, 110), (200, 100), (128, 226)], fill=_c("2a9ad0"))
        _sparkle(d, 90, 70, 14)
        _sparkle(d, 196, 50, 16)
    elif name == "herb_weed":
        for k, (x, h) in enumerate([(90, 150), (120, 190), (150, 160), (176, 120)]):
            d.polygon([(x - 10, 226), (x + (k - 1.5) * 12, 226 - h), (x + 10, 226)], fill=_c("5aa03a" if k % 2 else "3a8a2a"))
    elif name == "herb_mushroom":
        d.rounded_rectangle((110, 130, 146, 226), 14, fill=_c("e8d8b0"))
        d.pieslice((40, 50, 216, 200), 180, 360, fill=_c("8a2a1a"))
        d.pieslice((56, 66, 200, 190), 180, 360, fill=_c("b04028"))
        d.arc((70, 80, 186, 180), 200, 340, fill=_c("f0a060"), width=5)
    elif name == "herb_bell":
        d.line((128, 226, 120, 60), fill=_c("3a8a2a"), width=8)
        for (x, y) in [(90, 90), (150, 110), (100, 150), (160, 160)]:
            d.line((120, y - 20, x, y), fill=_c("3a8a2a"), width=4)
            d.pieslice((x - 22, y - 18, x + 22, y + 26), 180, 360, fill=_c("f8f8ff"))
            d.ellipse((x - 5, y + 2, x + 5, y + 12), fill=_c("e0e0f0"))
        d.polygon([(128, 200), (80, 176), (128, 214)], fill=_c("5aa03a"))
    elif name == "herb_star":
        d.line((128, 226, 128, 110), fill=_c("3a8a2a"), width=8)
        for k in range(5):
            a = k * math.pi * 2 / 5 - math.pi / 2
            d.ellipse((128 + math.cos(a) * 44 - 24, 90 + math.sin(a) * 44 - 24, 128 + math.cos(a) * 44 + 24, 90 + math.sin(a) * 44 + 24), fill=_c("fff0a0"))
        d.ellipse((108, 70, 148, 110), fill=_c("ffb030"))
        _sparkle(d, 200, 40, 16, _c("fff8d0"))
    elif name.startswith("herb_ginseng"):
        age = {"herb_ginseng1": 0, "herb_ginseng10": 1, "herb_ginseng100": 2}[name]
        body = ["e8d0a0", "f0c880", "ffd870"][age]
        d.polygon([(110, 70), (146, 70), (160, 140), (140, 200), (128, 226), (116, 200), (96, 140)], fill=_c(body))   # 뿌리 몸통
        for (x0, y0, x1, y1) in [(100, 150, 60, 210), (156, 150, 196, 206), (120, 200, 96, 236), (136, 200, 160, 238)]:
            d.line((x0, y0, x1, y1), fill=_c(body), width=8)
        for k in range(3 + age * 2):                                       # 잎
            a = -math.pi / 2 + (k - (1 + age)) * 0.45
            d.polygon([(128, 70), (128 + math.cos(a) * 60 - 12, 70 + math.sin(a) * 60), (128 + math.cos(a) * 60 + 12, 70 + math.sin(a) * 60)], fill=_c("3a9a3a"))
        d.ellipse((120, 18, 136, 34), fill=_c("e02a2a"))
        if age == 2:
            _sparkle(d, 200, 60, 18, _c("fff3b0"))
            _sparkle(d, 60, 120, 12, _c("fff3b0"))
    elif name.startswith("wood_"):
        bark, core, ring = {"wood_log": ("6a4a2a", "c8a070", "a07848"), "wood_glow": ("e0dcd0", "f8f0c0", "d8c878"), "wood_gold": ("b08010", "fff0a0", "f0c040")}[name]
        d.polygon([(40, 90), (180, 60), (216, 170), (76, 200)], fill=_c(bark))
        d.ellipse((150, 50, 222, 180), fill=_c(bark))
        d.ellipse((160, 62, 212, 168), fill=_c(core))
        for r in (18, 10):
            d.ellipse((186 - r, 115 - r * 2, 186 + r, 115 + r * 2), outline=_c(ring), width=3)
        for k in range(4):
            d.line((60 + k * 30, 100 - k * 6, 90 + k * 30, 190 - k * 6), fill=_mix(_c(bark), (0, 0, 0, 255), 0.3), width=3)
        if name != "wood_log":
            _sparkle(d, 60, 60, 16, _c("fff8d0"))
    elif name.startswith("crystal_"):
        col = {"crystal_low": "7fd8c0", "crystal_mid": "5ab8ff", "crystal_high": "b070ff", "crystal_top": "ff5ab0"}[name]
        n = {"crystal_low": 1, "crystal_mid": 2, "crystal_high": 3, "crystal_top": 4}[name]
        for k in range(n):
            x = 128 + (k - (n - 1) / 2) * 36
            h = 150 - abs(k - (n - 1) / 2) * 30
            d.polygon([(x, 220 - h), (x + 24, 200 - h * 0.5), (x, 222), (x - 24, 200 - h * 0.5)], fill=_c(col))
            d.polygon([(x, 220 - h), (x - 24, 200 - h * 0.5), (x, 222)], fill=_mix(_c(col), (255, 255, 255, 255), 0.45))
        for k in range(n):
            _sparkle(d, 60 + k * 44, 40 + (k % 2) * 30, 10 + n * 2)
    else:
        return steel_shard()
    return _done(img)


GOODS = [("mat_stone", "flint"), ("mat_iron", "iron_ingot"), ("mat_silver", "quartz"), ("mat_gold", "gold_ingot"), ("mat_crystal", "diamond"),
         ("herb_weed", "wheat_seeds"), ("herb_mushroom", "brown_dye"), ("herb_bell", "white_dye"), ("herb_star", "yellow_dye"),
         ("herb_ginseng1", "carrot"), ("herb_ginseng10", "golden_carrot"), ("herb_ginseng100", "golden_carrot"),
         ("wood_log", "stick"), ("wood_glow", "stick"), ("wood_gold", "stick"),
         ("crystal_low", "prismarine_shard"), ("crystal_mid", "prismarine_crystals"), ("crystal_high", "amethyst_shard"), ("crystal_top", "echo_shard")]


def club():
    """갈색 몽둥이: 대각선 방향 (손잡이 왼쪽 아래 → 머리 오른쪽 위), 옹이 · 나뭇결 · 쇠 징 · 가죽 손잡이"""
    img = _new()
    d = ImageDraw.Draw(img)
    d.line((40, 220, 150, 110), fill=_c("6a4a2a"), width=22)                          # 손잡이
    for k in range(4):                                                              # 가죽 감개
        x, y = 50 + k * 16, 210 - k * 16
        d.line((x - 10, y - 10, x + 10, y + 10), fill=_c("3a2414"), width=6)
    d.ellipse((30, 206, 58, 234), fill=_c("4a3018"))                                  # 손잡이 끝
    d.polygon([(118, 118), (170, 52), (222, 42), (232, 96), (196, 150), (138, 150)], fill=_c("8a5a30"))   # 굵은 머리
    d.polygon([(150, 80), (206, 56), (218, 92), (176, 120)], fill=_c("a8703f"))
    for (x0, y0, x1, y1) in [(140, 130, 210, 70), (130, 118, 190, 64), (160, 142, 222, 90)]:
        d.line((x0, y0, x1, y1), fill=_c("6a4020"), width=3)                           # 나뭇결
    d.ellipse((176, 92, 196, 110), fill=_c("5a3818"))                                 # 옹이
    for (x, y) in [(150, 70), (212, 50), (226, 100), (190, 144), (140, 136)]:           # 쇠 징
        d.polygon([(x, y - 12), (x + 8, y), (x, y + 8), (x - 8, y)], fill=_c("c0c8d0"))
        d.polygon([(x, y - 12), (x - 8, y), (x, y)], fill=_c("f0f4f8"))
    return _done(img)


def boss_crystal():
    """보스 수정: 붉은 기운이 소용돌이치는 큰 다면체 결정 + 금 받침 + 해골 문양"""
    img = _new()
    d = ImageDraw.Draw(img)
    d.polygon([(64, 206), (192, 206), (176, 236), (80, 236)], fill=_c("8a6a20"))   # 금 받침
    d.polygon([(72, 206), (184, 206), (178, 218), (78, 218)], fill=_c("e0b030"))
    outer = [(128, 12), (196, 80), (184, 200), (72, 200), (60, 80)]
    d.polygon(outer, fill=_c("3a0a1e"))
    d.polygon([(128, 24), (184, 84), (174, 190), (82, 190), (72, 84)], fill=_c("b01840"))
    d.polygon([(128, 24), (184, 84), (128, 110), (72, 84)], fill=_c("ff5a7a"))    # 윗면 (밝음)
    d.polygon([(72, 84), (128, 110), (82, 190)], fill=_c("8a0a2a"))               # 왼쪽 면 (어두움)
    d.polygon([(128, 110), (184, 84), (174, 190)], fill=_c("d0204a"))
    d.polygon([(128, 110), (82, 190), (174, 190)], fill=_c("a01034"))
    d.line([(128, 24), (128, 110), (82, 190)], fill=_c("ffb0c0"), width=3)         # 모서리 반사
    # 해골 문양
    d.ellipse((108, 118, 148, 156), fill=_c("fff0e0"))
    d.rectangle((116, 150, 140, 164), fill=_c("fff0e0"))
    d.ellipse((114, 128, 126, 140), fill=_c("3a0a1e"))
    d.ellipse((130, 128, 142, 140), fill=_c("3a0a1e"))
    for x in (120, 128, 136):
        d.line([(x, 156), (x, 164)], fill=_c("3a0a1e"), width=2)
    _sparkle(d, 176, 44, 18, _c("ffe0e8"))
    _sparkle(d, 70, 60, 10)
    return _done(img)
