"""
v5.10.45 보드게임 그림 (윷놀이 · 부루마블 · 인디언 포커 · 야차 명성 등급) — PAPER CustomModelData 13400 + 번호.
번호는 Java kr.rpgcraft.board.BoardIcons 와 같아야 함. 32px (4배로 그려 줄임).
"""
import math
import os

from PIL import Image, ImageDraw, ImageFont

BASE = 13400
S, OUT = 128, 32
K = S / 32.0


def _rgb(h, a=255):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (a,)


def _sh(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c[:3]) + (c[3] if len(c) > 3 else 255,)


def B(x0, y0, x1, y1):
    return [x0 * K, y0 * K, x1 * K, y1 * K]


def P(*pts):
    return [(x * K, y * K) for x, y in pts]


def new():
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    return img, ImageDraw.Draw(img)


def done(img):
    return img.resize((OUT, OUT), Image.LANCZOS)


def tile(d, col, edge="2a2018", r=5):
    c = _rgb(col)
    d.rounded_rectangle(B(1, 1, 31, 31), r * K, fill=_rgb(edge))
    d.rounded_rectangle(B(2.2, 2.2, 29.8, 29.8), (r - 1) * K, fill=_sh(c, 0.75))
    for i in range(int(25 * K)):
        t = i / (25 * K)
        y = 3.4 * K + i
        d.line([(3.6 * K, y), (28.4 * K, y)], fill=_sh(c, 1.12 - 0.35 * t))
    d.arc(B(4, 4, 28, 20), 200, 340, fill=(255, 255, 255, 60), width=int(1 * K))


def token(d, cx, cy, col, r=6.5):
    c = _rgb(col)
    d.ellipse(B(cx - r, cy - r + 1.4, cx + r, cy + r + 1.4), fill=(20, 14, 20, 170))
    d.ellipse(B(cx - r, cy - r, cx + r, cy + r), fill=_sh(c, 0.7))
    d.ellipse(B(cx - r + 1, cy - r + 0.6, cx + r - 1, cy + r - 1.6), fill=c)
    d.ellipse(B(cx - r * 0.45, cy - r * 0.6, cx + r * 0.1, cy - r * 0.15), fill=(255, 255, 255, 150))


def text(d, x, y, s, col, size=14):
    try:
        f = ImageFont.truetype("DejaVuSans-Bold.ttf", int(size * K))
    except OSError:
        f = ImageFont.load_default()
    w = d.textlength(s, font=f)
    d.text((x * K - w / 2 + K * 0.8, y * K - size * K * 0.62 + K * 0.8), s, font=f, fill=(20, 12, 20, 180))
    d.text((x * K - w / 2, y * K - size * K * 0.62), s, font=f, fill=_rgb(col))


def stick(d, x, flat, col="e8c890"):
    """윷가락 하나 (flat = 평평한 면이 보임 · 아니면 둥근 등)"""
    c = _rgb(col if flat else "8a5a32")
    d.rounded_rectangle(B(x - 2.2, 4, x + 2.2, 28), 2 * K, fill=_rgb("3a2414"))
    d.rounded_rectangle(B(x - 1.6, 4.6, x + 1.6, 27.4), 1.6 * K, fill=c)
    if flat:
        for yy in (10, 16, 22):
            d.line(P((x - 1, yy - 1), (x + 1, yy + 1)), fill=_rgb("6a3a1a"), width=int(0.6 * K))
            d.line(P((x + 1, yy - 1), (x - 1, yy + 1)), fill=_rgb("6a3a1a"), width=int(0.6 * K))


# ------------------------------------------------------------------ 윷놀이
def yut_station(kind):
    img, d = new()
    if kind == "corner":
        tile(d, "c89a5a")
        d.ellipse(B(6, 6, 26, 26), fill=_rgb("6a3a1a"))
        d.ellipse(B(8, 8, 24, 24), fill=_rgb("f2e0b0"))
    elif kind == "center":
        tile(d, "b8463a")
        d.ellipse(B(5, 5, 27, 27), fill=_rgb("5a1a10"))
        d.ellipse(B(7, 7, 25, 25), fill=_rgb("f2c060"))
        d.ellipse(B(12, 12, 20, 20), fill=_rgb("b8463a"))
    else:
        tile(d, "a8784a")
        d.ellipse(B(10, 10, 22, 22), fill=_rgb("5a3a1a"))
        d.ellipse(B(11.4, 11.4, 20.6, 20.6), fill=_rgb("e8d0a0"))
    return done(img)


SEAT_COLS = ["e84a4a", "3a8aff", "4ac85a", "f0c83a"]


def yut_seat(k):
    img, d = new()
    tile(d, "a8784a")
    token(d, 16, 16, SEAT_COLS[k])
    return done(img)


def yut_multi():
    img, d = new()
    tile(d, "a8784a")
    for k, (x, y) in enumerate(((11, 11), (21, 11), (11, 21), (21, 21))):
        token(d, x, y, SEAT_COLS[k], 4.6)
    return done(img)


def tok_seat(k):
    img, d = new()
    tile(d, "f4ecd8", "3a2a1a", 3)
    token(d, 16, 16, SEAT_COLS[k], 7)
    return done(img)


def tok_multi():
    img, d = new()
    tile(d, "f4ecd8", "3a2a1a", 3)
    for k, (x, y) in enumerate(((11, 11), (21, 11), (11, 21), (21, 21))):
        token(d, x, y, SEAT_COLS[k], 4.6)
    return done(img)


def room(kind):
    img, d = new()
    if kind == "room":
        tile(d, "3a5a8a")
        for k, x in enumerate((9, 16, 23)):
            d.ellipse(B(x - 3, 8, x + 3, 14), fill=_rgb(SEAT_COLS[k]))
            d.rectangle(B(x - 4, 15, x + 4, 24), fill=_rgb(SEAT_COLS[k]))
    elif kind == "bot":
        tile(d, "4a4e5a")
        d.rectangle(B(8, 10, 24, 24), fill=_rgb("c8ccd8"))
        d.ellipse(B(11, 14, 15, 18), fill=_rgb("3a8aff"))
        d.ellipse(B(17, 14, 21, 18), fill=_rgb("3a8aff"))
        d.line(P((16, 10), (16, 6)), fill=_rgb("c8ccd8"), width=int(1.2 * K))
        d.ellipse(B(14.5, 4, 17.5, 7), fill=_rgb("ff6a6a"))
    else:
        tile(d, "3ab84a")
        d.polygon(P((12, 8), (25, 16), (12, 24)), fill=_rgb("f8fff0"))
    return done(img)


def yut_piece(who):
    img, d = new()
    tile(d, "a8784a")
    if who in ("me", "both"):
        token(d, 12 if who == "both" else 16, 13 if who == "both" else 16, "e84a4a")
    if who in ("ai", "both"):
        token(d, 20 if who == "both" else 16, 19 if who == "both" else 16, "3a8aff")
    return done(img)


def yut_home(col):
    img, d = new()
    tile(d, "4a3a2a")
    token(d, 16, 15, col, 8.5)
    return done(img)


YUT = [("도", 1), ("개", 2), ("걸", 3), ("윷", 4), ("모", 5), ("빽도", -1)]


def yut_result(i):
    img, d = new()
    tile(d, "f2e0b0" if i < 5 else "e89a9a", "3a2414")
    n = YUT[i][1]
    flats = {1: 1, 2: 2, 3: 3, 4: 4, 5: 0, -1: 1}[n]
    for k in range(4):
        stick(d, 7 + k * 6, k < flats)
    if n == -1:
        d.ellipse(B(4, 4, 10, 10), fill=_rgb("c82a2a"))
    return done(img)


def yut_throw():
    img, d = new()
    tile(d, "c8463a")
    for k, (x, a) in enumerate(((9, -20), (16, 5), (23, 25))):
        ln = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        dd = ImageDraw.Draw(ln)
        stick(dd, 16, k != 1)
        ln = ln.rotate(a, resample=Image.BICUBIC, center=(16 * K, 16 * K), translate=((x - 16) * K, 0))
        img.alpha_composite(ln)
    return done(img)


def yut_goal():
    img, d = new()
    tile(d, "3a8a4a")
    d.polygon(P((8, 17), (14, 23), (25, 9), (27, 12), (14, 27), (6, 19)), fill=_rgb("f8fff0"))
    return done(img)


def yut_select():
    img, d = new()
    tile(d, "f2d060", "8a5a10")
    d.polygon(P((16, 5), (25, 14), (20, 14), (20, 26), (12, 26), (12, 14), (7, 14)), fill=_rgb("c83a2a"))
    return done(img)


# ------------------------------------------------------------------ 부루마블
GROUPS = ["8a5a3a", "6ac8ff", "e86ab8", "ff9a3a", "e83a3a", "f2d03a", "3ab84a", "3a5ad8"]


def city(gi, owner=None):
    img, d = new()
    tile(d, "f4ecd8", "3a2a1a", 3)
    d.rectangle(B(2.2, 2.2, 29.8, 10), fill=_rgb(GROUPS[gi]))
    d.line(P((3, 10), (29, 10)), fill=_rgb("3a2a1a"), width=int(0.8 * K))
    for k in range(3):   # 건물 실루엣
        x = 7 + k * 7
        d.rectangle(B(x, 19 - k * 2, x + 5, 27), fill=_rgb("b8ac94"))
        d.rectangle(B(x + 1.2, 21 - k * 2, x + 2.4, 22.4 - k * 2), fill=_rgb("6a5e4a"))
    if owner:
        col = SEAT_COLS[owner] if isinstance(owner, int) else "e84a4a" if owner == "me" else "3a8aff"
        d.line(P((22, 9), (22, 28)), fill=_rgb("3a2a1a"), width=int(1.2 * K))
        d.polygon(P((22.6, 9), (30, 12.5), (22.6, 16)), fill=_rgb(col))
    return done(img)


def special(kind):
    img, d = new()
    if kind == "start":
        tile(d, "3ab84a")
        text(d, 16, 17, "GO", "f8fff0", 13)
    elif kind == "island":
        tile(d, "6ac8ff")
        d.ellipse(B(5, 18, 27, 28), fill=_rgb("f2d890"))
        d.line(P((15, 22), (17, 8)), fill=_rgb("8a5a32"), width=int(1.6 * K))
        for a in (-60, -20, 20, 60):
            r = math.radians(a - 90)
            d.line(P((17, 8), (17 + math.cos(r) * 8, 8 + math.sin(r) * 4 + 3)), fill=_rgb("3a9a3a"), width=int(1.6 * K))
    elif kind == "chance":
        tile(d, "a85ad8")
        text(d, 16, 17, "?", "fff0a0", 22)
    elif kind == "travel":
        tile(d, "1a1a4a")
        for (x, y) in ((6, 7), (25, 9), (9, 24), (27, 25), (20, 5)):
            d.ellipse(B(x - 0.8, y - 0.8, x + 0.8, y + 0.8), fill=_rgb("ffffff"))
        d.polygon(P((16, 5), (20, 13), (20, 22), (12, 22), (12, 13)), fill=_rgb("e8ecf6"))
        d.ellipse(B(14, 11, 18, 15), fill=_rgb("6ac8ff"))
        d.polygon(P((16, 22), (19, 28), (13, 28)), fill=_rgb("ff8a3a"))
    elif kind == "fund":
        tile(d, "f2b83a")
        d.ellipse(B(7, 10, 25, 28), fill=_rgb("c88a2a"))
        d.polygon(P((12, 10), (20, 10), (18, 5), (14, 5)), fill=_rgb("c88a2a"))
        text(d, 16, 19.5, "$", "fff8d0", 13)
    return done(img)


def tok(who):
    img, d = new()
    tile(d, "f4ecd8", "3a2a1a", 3)
    if who in ("me", "both"):
        token(d, 11 if who == "both" else 16, 16, "e84a4a", 7)
    if who in ("ai", "both"):
        token(d, 21 if who == "both" else 16, 16, "3a8aff", 7)
    return done(img)


PIPS = {1: [(16, 16)], 2: [(10, 10), (22, 22)], 3: [(9, 9), (16, 16), (23, 23)], 4: [(10, 10), (22, 10), (10, 22), (22, 22)],
        5: [(9, 9), (23, 9), (16, 16), (9, 23), (23, 23)], 6: [(10, 8), (22, 8), (10, 16), (22, 16), (10, 24), (22, 24)]}


def dice(n):
    img, d = new()
    d.rounded_rectangle(B(2, 2, 30, 30), 6 * K, fill=_rgb("2a2430"))
    d.rounded_rectangle(B(3, 3, 29, 29), 5 * K, fill=_rgb("f8f4ec"))
    for (x, y) in PIPS[n]:
        d.ellipse(B(x - 2.6, y - 2.6, x + 2.6, y + 2.6), fill=_rgb("c82a2a" if n == 1 else "2a2430"))
    return done(img)


def roll_btn():
    img, d = new()
    tile(d, "e8463a")
    for (ox, oy, n) in ((5, 7, 5), (14, 13, 3)):
        d.rounded_rectangle(B(ox, oy, ox + 13, oy + 13), 3 * K, fill=_rgb("f8f4ec"), outline=_rgb("2a2430"), width=int(0.8 * K))
        for (x, y) in PIPS[n]:
            d.ellipse(B(ox + x * 13 / 32 - 1.1, oy + y * 13 / 32 - 1.1, ox + x * 13 / 32 + 1.1, oy + y * 13 / 32 + 1.1), fill=_rgb("2a2430"))
    return done(img)


def money():
    img, d = new()
    tile(d, "3a7a4a")
    d.rounded_rectangle(B(5, 9, 27, 23), 2 * K, fill=_rgb("8ae08a"), outline=_rgb("1a4a2a"), width=int(0.8 * K))
    d.ellipse(B(12, 11, 20, 21), fill=_rgb("3a9a4a"))
    text(d, 16, 16.5, "$", "e8ffe8", 8)
    return done(img)


# ------------------------------------------------------------------ 인디언 포커
def card_back():
    img, d = new()
    d.rounded_rectangle(B(5, 2, 27, 30), 3 * K, fill=_rgb("1a1420"))
    d.rounded_rectangle(B(6, 3, 26, 29), 2.4 * K, fill=_rgb("b83a3a"))
    d.rounded_rectangle(B(8, 5, 24, 27), 1.6 * K, outline=_rgb("f2d060"), width=int(0.8 * K))
    d.polygon(P((16, 9), (21, 16), (16, 23), (11, 16)), fill=_rgb("f2d060"))
    return done(img)


def card(n):
    img, d = new()
    d.rounded_rectangle(B(5, 2, 27, 30), 3 * K, fill=_rgb("1a1420"))
    d.rounded_rectangle(B(6, 3, 26, 29), 2.4 * K, fill=_rgb("fbf7ee"))
    col = "c82a2a" if n % 2 else "2a2430"
    text(d, 16, 17, str(n), col, 15 if n < 10 else 12)
    d.polygon(P((10, 6), (12, 8.5), (10, 11), (8, 8.5)), fill=_rgb(col))
    d.polygon(P((22, 21), (24, 23.5), (22, 26), (20, 23.5)), fill=_rgb(col))
    return done(img)


def chip(col="e8463a"):
    img, d = new()
    c = _rgb(col)
    d.ellipse(B(3, 5, 29, 31), fill=_sh(c, 0.55))
    d.ellipse(B(3, 3, 29, 29), fill=c)
    for k in range(8):
        a = math.radians(k * 45)
        x, y = 16 + math.cos(a) * 11, 16 + math.sin(a) * 11
        d.ellipse(B(x - 1.8, y - 1.8, x + 1.8, y + 1.8), fill=_rgb("fbf7ee"))
    d.ellipse(B(9, 9, 23, 23), fill=_sh(c, 0.8))
    d.ellipse(B(10, 10, 22, 22), outline=_rgb("fbf7ee"), width=int(0.8 * K))
    return done(img)


def poker_btn(kind):
    img, d = new()
    if kind == "call":
        tile(d, "3aa84a")
        d.polygon(P((8, 16.5), (13.5, 22), (24, 10.5), (26, 13), (13.5, 26), (6, 18.5)), fill=_rgb("f8fff0"))
    else:
        tile(d, "8a8f9a")
        d.line(P((9, 9), (23, 23)), fill=_rgb("fbf7ee"), width=int(3 * K))
        d.line(P((23, 9), (9, 23)), fill=_rgb("fbf7ee"), width=int(3 * K))
    return done(img)


# ------------------------------------------------------------------ 야차 명성 등급
TIERS = [("bronze", "b8784a"), ("silver", "c8ccd6"), ("gold", "f2c03a"), ("platinum", "6ae0d0"), ("diamond", "6aa8ff"), ("master", "c86aff"), ("grandmaster", "ff4a4a")]


def emblem(i):
    img, d = new()
    col = _rgb(TIERS[i][1])
    pts = P((16, 1.5), (28, 7), (27, 19), (16, 30.5), (5, 19), (4, 7))
    d.polygon(pts, fill=_rgb("1c1a20"))
    d.polygon(P((16, 3.2), (26.4, 8), (25.5, 18.4), (16, 28.4), (6.5, 18.4), (5.6, 8)), fill=_sh(col, 0.65))
    d.polygon(P((16, 5), (24.8, 9), (24, 17.6), (16, 26.2), (8, 17.6), (7.2, 9)), fill=col)
    d.polygon(P((16, 5), (24.8, 9), (16, 12)), fill=_sh(col, 1.25) if max(col[:3]) < 220 else (255, 255, 255, 120))
    # 교차한 검
    d.line(P((10, 22), (22, 10)), fill=_rgb("fbf7ee"), width=int(1.6 * K))
    d.line(P((22, 22), (10, 10)), fill=_rgb("fbf7ee"), width=int(1.6 * K))
    d.line(P((9, 19), (13, 23)), fill=_rgb("3a2a1a"), width=int(1.2 * K))
    d.line(P((23, 19), (19, 23)), fill=_rgb("3a2a1a"), width=int(1.2 * K))
    stars = min(3, i // 2 + 1) if i < 6 else 3
    for k in range(stars):
        x = 16 + (k - (stars - 1) / 2) * 5
        r1, r2 = 2.2, 0.9
        st = []
        for j in range(10):
            a = math.radians(-90 + j * 36)
            r = r1 if j % 2 == 0 else r2
            st.append((x + math.cos(a) * r, 2.6 + math.sin(a) * r + (i >= 5) * 0))
        d.polygon(P(*st), fill=_rgb("fff0a0"))
    if i == 6:
        d.polygon(P((10, 4), (12, 0.5), (14, 3.5), (16, 0), (18, 3.5), (20, 0.5), (22, 4)), fill=_rgb("ffd04a"))
    return done(img)


def hub(kind):
    img, d = new()
    if kind == "chip":
        return chip("e8463a")
    if kind == "yut":
        tile(d, "a8784a")
        for k in range(4):
            stick(d, 7 + k * 6, k % 2 == 0)
    elif kind == "marble":
        tile(d, "3a8a4a")
        for k, g in enumerate(GROUPS[:4]):
            d.rectangle(B(4 + k * 6.2, 4, 9.4 + k * 6.2, 9), fill=_rgb(g))
        d.rounded_rectangle(B(9, 13, 23, 27), 3 * K, fill=_rgb("f8f4ec"))
        for (x, y) in PIPS[5]:
            d.ellipse(B(9 + x * 14 / 32 - 1.1, 13 + y * 14 / 32 - 1.1, 9 + x * 14 / 32 + 1.1, 13 + y * 14 / 32 + 1.1), fill=_rgb("2a2430"))
    elif kind == "poker":
        tile(d, "1a5a3a")
        c = card(7).resize((S, S), Image.LANCZOS).rotate(12, center=(18 * K, 16 * K))
        b = card_back().resize((S, S), Image.LANCZOS).rotate(-12, center=(14 * K, 16 * K))
        img.alpha_composite(b.resize((int(S * 0.8),) * 2), (int(1 * K), int(4 * K)))
        img.alpha_composite(c.resize((int(S * 0.8),) * 2), (int(8 * K), int(3 * K)))
    elif kind == "duel":
        return emblem(2)
    return done(img)


def entries():
    """(번호, 이름, 그림 함수) — Java BoardIcons 와 같은 번호"""
    out = [(0, "yut_station", lambda: yut_station("normal")), (1, "yut_corner", lambda: yut_station("corner")), (2, "yut_center", lambda: yut_station("center")),
           (3, "yut_me", lambda: yut_piece("me")), (4, "yut_ai", lambda: yut_piece("ai")), (5, "yut_both", lambda: yut_piece("both"))]
    for i in range(6):
        out.append((6 + i, "yut_res%d" % i, lambda i=i: yut_result(i)))
    out += [(12, "yut_throw", yut_throw), (13, "yut_home_me", lambda: yut_home("e84a4a")), (14, "yut_home_ai", lambda: yut_home("3a8aff")),
            (15, "yut_goal", yut_goal), (16, "yut_select", yut_select)]
    for g in range(8):
        out.append((20 + g, "city%d" % g, lambda g=g: city(g)))
        out.append((28 + g, "city%d_me" % g, lambda g=g: city(g, "me")))
        out.append((36 + g, "city%d_ai" % g, lambda g=g: city(g, "ai")))
    out += [(44, "start", lambda: special("start")), (45, "island", lambda: special("island")), (46, "chance", lambda: special("chance")),
            (47, "travel", lambda: special("travel")), (48, "fund", lambda: special("fund")),
            (49, "tok_me", lambda: tok("me")), (50, "tok_ai", lambda: tok("ai")), (51, "tok_both", lambda: tok("both"))]
    for n in range(1, 7):
        out.append((51 + n, "dice%d" % n, lambda n=n: dice(n)))
    out += [(58, "roll", roll_btn), (59, "money", money), (60, "card_back", card_back)]
    for n in range(1, 11):
        out.append((60 + n, "card%d" % n, lambda n=n: card(n)))
    out += [(71, "chip", chip), (72, "call", lambda: poker_btn("call")), (73, "fold", lambda: poker_btn("fold"))]
    for i in range(7):
        out.append((80 + i, "tier_" + TIERS[i][0], lambda i=i: emblem(i)))
    out += [(87, "board_chip", lambda: hub("chip")), (88, "hub_yut", lambda: hub("yut")), (89, "hub_marble", lambda: hub("marble")),
            (90, "hub_poker", lambda: hub("poker")), (91, "hub_duel", lambda: hub("duel"))]
    # v5.10.59 여러 명 (자리 색)
    for k in range(4):
        out.append((100 + k, "yut_seat%d" % k, lambda k=k: yut_seat(k)))
        out.append((105 + k, "yut_home_seat%d" % k, lambda k=k: yut_home(SEAT_COLS[k])))
        out.append((110 + k, "tok_seat%d" % k, lambda k=k: tok_seat(k)))
        for g in range(8):
            out.append((120 + k * 8 + g, "city%d_seat%d" % (g, k), lambda g=g, k=k: city(g, k)))
    out += [(104, "yut_multi", yut_multi), (114, "tok_multi", tok_multi),
            (160, "room", lambda: room("room")), (161, "room_bot", lambda: room("bot")), (162, "room_start", lambda: room("start"))]
    return out


def write_icons(pack, ns, write_json):
    tex = os.path.join(pack, "assets", ns, "textures", "item", "board")
    os.makedirs(tex, exist_ok=True)
    out = []
    for i, name, fn in entries():
        fn().save(os.path.join(tex, name + ".png"))
        write_json(os.path.join(pack, "assets", ns, "models", "item", "board", name + ".json"),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": ns + ":item/board/" + name}})
        out.append((BASE + i, ns + ":item/board/" + name))
    return out
