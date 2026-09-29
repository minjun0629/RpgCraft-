"""미니게임 · 이벤트 상점 · 주식 리소스팩 그림 (v5.6.0)

- 아이콘: PAPER CustomModelData 12000~ (16x16, item/generated). 번호는 kr.rpgcraft.minigame.Icons 와 같아야 함
- GUI 배경: 게임마다 판을 새로 그림 (두더지 잔디밭, 벽돌깨기 네온 경기장, 지뢰찾기 판, 카드 테이블, 이벤트 광장, 주식 차트)
"""
import math
import os

from PIL import Image, ImageDraw

ICON_BASE = 12000


def _img():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def _rgb(h, a=255):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (a,)


def _shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c[:3]) + (c[3],)


# ------------------------------------------------------------------ 두더지
def hole():
    im = _img(); d = ImageDraw.Draw(im)
    d.ellipse([1, 7, 14, 14], fill=_rgb("5a3a1e"))
    d.ellipse([2, 8, 13, 13], fill=_rgb("1e120a"))
    d.arc([1, 7, 14, 14], 200, 340, fill=_rgb("8a5a2e"))
    return im


def mole(kind="normal"):
    im = hole(); d = ImageDraw.Draw(im)
    fur = {"normal": _rgb("7a5234"), "gold": _rgb("f2c23a"), "hit": _rgb("7a5234")}[kind]
    d.ellipse([3, 2, 12, 13], fill=fur)
    d.ellipse([5, 7, 10, 12], fill=_shade(fur, 1.35))                     # 배
    d.ellipse([6, 6, 9, 9], fill=_rgb("f4a0a8"))                           # 코
    if kind == "hit":
        for (x, y) in ((4, 4), (10, 4)):
            d.line([x, y, x + 2, y + 2], fill=_rgb("111111")); d.line([x + 2, y, x, y + 2], fill=_rgb("111111"))
        for k in range(4):
            a = k * math.pi / 2
            d.point((int(7.5 + math.cos(a) * 6), int(1 + math.sin(a) * 1.5)), fill=_rgb("ffe14a"))
    else:
        d.point((5, 4), fill=_rgb("111111")); d.point((10, 4), fill=_rgb("111111"))
        d.point((5, 3), fill=_rgb("ffffff")); d.point((10, 3), fill=_rgb("ffffff"))
    d.line([3, 12, 5, 11], fill=_rgb("f4c0a0")); d.line([12, 12, 10, 11], fill=_rgb("f4c0a0"))   # 손
    if kind == "gold":
        d.point((12, 2), fill=_rgb("ffffff")); d.point((3, 5), fill=_rgb("fff6b0"))
    d.ellipse([1, 11, 14, 15], outline=_rgb("5a3a1e"))
    return im


def bomb(boom=False):
    im = hole() if not boom else _img(); d = ImageDraw.Draw(im)
    if boom:
        for r, c in ((7, "ff5a1f"), (5, "ffb030"), (3, "fff0a0")):
            pts = []
            for k in range(16):
                a = k * math.pi / 8
                rr = r if k % 2 == 0 else r * 0.6
                pts.append((7.5 + math.cos(a) * rr, 7.5 + math.sin(a) * rr))
            d.polygon(pts, fill=_rgb(c))
        return im
    d.ellipse([3, 3, 12, 12], fill=_rgb("24242e"))
    d.ellipse([5, 4, 7, 6], fill=_rgb("6a6a7a"))
    d.line([10, 4, 12, 1], fill=_rgb("c8a060"))
    d.point((13, 1), fill=_rgb("ffb030")); d.point((12, 0), fill=_rgb("ff5a1f"))
    return im


# ------------------------------------------------------------------ 벽돌깨기
def brick(col, steel=False, cracked=False):
    im = _img(); d = ImageDraw.Draw(im)
    c = _rgb(col)
    d.rectangle([0, 3, 15, 12], fill=c)
    d.line([0, 3, 15, 3], fill=_shade(c, 1.35)); d.line([0, 3, 0, 12], fill=_shade(c, 1.2))
    d.line([0, 12, 15, 12], fill=_shade(c, 0.55)); d.line([15, 3, 15, 12], fill=_shade(c, 0.65))
    d.line([1, 7, 14, 7], fill=_shade(c, 0.8)); d.line([7, 3, 7, 7], fill=_shade(c, 0.8)); d.line([4, 8, 4, 12], fill=_shade(c, 0.8)); d.line([11, 8, 11, 12], fill=_shade(c, 0.8))
    if steel:
        for (x, y) in ((2, 5), (13, 5), (2, 10), (13, 10)):
            d.point((x, y), fill=_rgb("f0f0f0"))
    if cracked:
        d.line([5, 4, 8, 8, 6, 11], fill=_rgb("1a1a22")); d.line([8, 8, 11, 9], fill=_rgb("1a1a22"))
    return im


def ball():
    im = _img(); d = ImageDraw.Draw(im)
    d.ellipse([4, 4, 11, 11], fill=_rgb("e8f6ff"))
    d.ellipse([5, 5, 8, 8], fill=_rgb("ffffff"))
    d.arc([4, 4, 11, 11], 20, 160, fill=_rgb("7fb8ff"))
    for (x, y) in ((2, 7), (13, 8), (7, 2), (8, 13)):
        d.point((x, y), fill=_rgb("7fe8ff", 160))
    return im


def paddle(part):
    im = _img(); d = ImageDraw.Draw(im)
    x0 = 3 if part == "l" else 0
    x1 = 12 if part == "r" else 15
    d.rectangle([x0, 6, x1, 10], fill=_rgb("4ad8ff"))
    d.line([x0, 6, x1, 6], fill=_rgb("c8f6ff")); d.line([x0, 10, x1, 10], fill=_rgb("1f7aa8"))
    if part == "l": d.line([x0, 6, x0, 10], fill=_rgb("c8f6ff"))
    if part == "r": d.line([x1, 6, x1, 10], fill=_rgb("1f7aa8"))
    if part == "m": d.rectangle([6, 7, 9, 9], fill=_rgb("ffe14a"))
    return im


# ------------------------------------------------------------------ 지뢰찾기
def tile(opened):
    im = _img(); d = ImageDraw.Draw(im)
    if opened:
        d.rectangle([0, 0, 15, 15], fill=_rgb("c8c0b0"))
        d.rectangle([0, 0, 15, 15], outline=_rgb("9a9284"))
    else:
        d.rectangle([0, 0, 15, 15], fill=_rgb("6a8ab0"))
        d.line([0, 0, 15, 0], fill=_rgb("a8c8ec")); d.line([0, 0, 0, 15], fill=_rgb("a8c8ec"))
        d.line([1, 1, 14, 1], fill=_rgb("8aaad0")); d.line([1, 1, 1, 14], fill=_rgb("8aaad0"))
        d.line([0, 15, 15, 15], fill=_rgb("34506e")); d.line([15, 0, 15, 15], fill=_rgb("34506e"))
        d.line([1, 14, 14, 14], fill=_rgb("48668a")); d.line([14, 1, 14, 14], fill=_rgb("48668a"))
    return im


DIGIT = {
    1: ["..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###."],
    2: [".###.", "#...#", "....#", "..##.", ".#...", "#....", "#####"],
    3: [".###.", "#...#", "....#", "..##.", "....#", "#...#", ".###."],
    4: ["...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#."],
    5: ["#####", "#....", "####.", "....#", "....#", "#...#", ".###."],
    6: [".###.", "#....", "####.", "#...#", "#...#", "#...#", ".###."],
    7: ["#####", "....#", "...#.", "..#..", "..#..", "..#..", "..#.."],
    8: [".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###."],
}
NUM_COL = {1: "1f5aff", 2: "1f8a2a", 3: "e02a2a", 4: "1a1a8a", 5: "8a1a1a", 6: "1a8a8a", 7: "222222", 8: "6a6a6a"}


def number(n):
    im = tile(True); d = ImageDraw.Draw(im)
    c = _rgb(NUM_COL[n])
    for y, row in enumerate(DIGIT[n]):
        for x, ch in enumerate(row):
            if ch == "#":
                d.rectangle([3 + x * 2, 1 + y * 2, 3 + x * 2 + 1, 1 + y * 2 + 1], fill=c)
    return im


def flag():
    im = tile(False); d = ImageDraw.Draw(im)
    d.line([6, 3, 6, 12], fill=_rgb("222222"))
    d.polygon([(7, 3), (12, 5), (7, 7)], fill=_rgb("ff2a2a"))
    d.rectangle([4, 12, 9, 13], fill=_rgb("222222"))
    return im


def mine(boom=False):
    im = tile(True); d = ImageDraw.Draw(im)
    if boom: d.rectangle([1, 1, 14, 14], fill=_rgb("ff4a3a"))
    for a in range(4):
        ang = a * math.pi / 4
        d.line([7.5 - math.cos(ang) * 6, 7.5 - math.sin(ang) * 6, 7.5 + math.cos(ang) * 6, 7.5 + math.sin(ang) * 6], fill=_rgb("111111"))
    d.ellipse([4, 4, 11, 11], fill=_rgb("111111"))
    d.rectangle([5, 5, 6, 6], fill=_rgb("ffffff"))
    return im


# ------------------------------------------------------------------ 같은 그림 찾기
def card_back():
    im = _img(); d = ImageDraw.Draw(im)
    d.rounded_rectangle([1, 0, 14, 15], 2, fill=_rgb("6a1f3a"), outline=_rgb("f2c23a"))
    d.rounded_rectangle([3, 2, 12, 13], 1, outline=_rgb("c8943a"))
    d.polygon([(7.5, 4), (10, 7.5), (7.5, 11), (5, 7.5)], fill=_rgb("f2c23a"))
    d.point((7, 7), fill=_rgb("fff6b0")); d.point((8, 8), fill=_rgb("fff6b0"))
    return im


def _face_frame():
    im = _img(); d = ImageDraw.Draw(im)
    d.rounded_rectangle([1, 0, 14, 15], 2, fill=_rgb("f6f0e0"), outline=_rgb("c8b890"))
    return im, d


def face(k):
    """14가지 그림 (서로 확실히 달라 보이게 모양 + 색)"""
    im, d = _face_frame()
    c = ["e02a2a", "1f7aff", "2ab84a", "f2c23a", "a04aff", "ff7a1f", "1ab8b8", "e04aa0", "6a4a2a", "222222", "ff4a4a", "3a8a3a", "4a6aff", "c8a060"][k]
    col = _rgb(c)
    if k == 0:   # 하트
        d.polygon([(7.5, 12), (3, 7), (3, 4), (5, 3), (7.5, 5), (10, 3), (12, 4), (12, 7)], fill=col)
    elif k == 1:   # 물방울
        d.polygon([(7.5, 2), (11, 8), (4, 8)], fill=col); d.ellipse([4, 6, 11, 13], fill=col)
    elif k == 2:   # 나뭇잎
        d.ellipse([4, 3, 11, 12], fill=col); d.line([4, 13, 11, 3], fill=_shade(col, 0.6))
    elif k == 3:   # 별
        pts = [(7.5 + math.cos(-math.pi / 2 + i * math.pi / 5) * (5.5 if i % 2 == 0 else 2.4), 7.5 + math.sin(-math.pi / 2 + i * math.pi / 5) * (5.5 if i % 2 == 0 else 2.4)) for i in range(10)]
        d.polygon(pts, fill=col)
    elif k == 4:   # 보석
        d.polygon([(4, 6), (6, 3), (9, 3), (11, 6), (7.5, 13)], fill=col); d.line([4, 6, 11, 6], fill=_shade(col, 1.5))
    elif k == 5:   # 불꽃
        d.polygon([(7.5, 2), (11, 8), (10, 12), (5, 12), (4, 8), (6, 6)], fill=col); d.polygon([(7.5, 7), (9, 10), (7.5, 12), (6, 10)], fill=_rgb("ffe14a"))
    elif k == 6:   # 달
        d.ellipse([3, 3, 12, 12], fill=col); d.ellipse([6, 2, 14, 10], fill=_rgb("f6f0e0"))
    elif k == 7:   # 꽃
        for a in range(5):
            ang = a * 2 * math.pi / 5
            x, y = 7.5 + math.cos(ang) * 3, 7.5 + math.sin(ang) * 3
            d.ellipse([x - 2, y - 2, x + 2, y + 2], fill=col)
        d.ellipse([6, 6, 9, 9], fill=_rgb("ffe14a"))
    elif k == 8:   # 버섯
        d.pieslice([2, 2, 13, 12], 180, 360, fill=_rgb("e02a2a")); d.rectangle([6, 7, 9, 13], fill=_rgb("e8dcc0"))
        d.point((5, 4), fill=_rgb("ffffff")); d.point((9, 5), fill=_rgb("ffffff"))
    elif k == 9:   # 해골
        d.ellipse([3, 2, 12, 10], fill=_rgb("e8e4d8")); d.rectangle([5, 9, 10, 12], fill=_rgb("e8e4d8"))
        d.rectangle([5, 5, 6, 7], fill=col); d.rectangle([9, 5, 10, 7], fill=col)
    elif k == 10:   # 검
        d.line([4, 12, 11, 3], fill=_rgb("c8d8ec"), width=2); d.line([3, 9, 7, 13], fill=_rgb("c8943a"), width=1); d.point((3, 13), fill=_rgb("6a4a2a"))
    elif k == 11:   # 방패
        d.polygon([(3, 3), (12, 3), (12, 8), (7.5, 13), (3, 8)], fill=col); d.line([7.5, 3, 7.5, 12], fill=_rgb("f2c23a"))
    elif k == 12:   # 물고기
        d.ellipse([3, 5, 11, 10], fill=col); d.polygon([(10, 7.5), (13, 5), (13, 10)], fill=col); d.point((5, 7), fill=_rgb("ffffff"))
    else:   # 열쇠
        d.ellipse([3, 3, 8, 8], outline=col, width=2); d.line([7, 7, 12, 12], fill=col, width=2); d.line([10, 12, 12, 10], fill=col)
    return im


# ------------------------------------------------------------------ 코인 · 버튼
def coin(col, glyph):
    im = _img(); d = ImageDraw.Draw(im)
    c = _rgb(col)
    d.ellipse([1, 1, 14, 14], fill=_shade(c, 0.6))
    d.ellipse([1, 0, 14, 13], fill=c)
    d.ellipse([3, 2, 12, 11], outline=_shade(c, 1.35))
    d.arc([1, 0, 14, 13], 200, 290, fill=_rgb("ffffff"))
    gi = {"mole": mole, "brick": lambda: brick("e0402a"), "mine": lambda: mine(), "card": card_back}[glyph]()
    small = gi.resize((8, 8), Image.NEAREST)
    im.alpha_composite(small, (4, 3))
    return im


def medal(col, stars):
    im = _img(); d = ImageDraw.Draw(im)
    d.polygon([(4, 0), (7, 5), (5, 5)], fill=_rgb("c83a3a")); d.polygon([(11, 0), (8, 5), (10, 5)], fill=_rgb("3a5ac8"))
    d.ellipse([2, 4, 13, 15], fill=_rgb(col), outline=_shade(_rgb(col), 0.6))
    for i in range(stars):
        x = 7.5 + (i - (stars - 1) / 2) * 3.2
        d.polygon([(x, 7.5), (x + 1.2, 9.5), (x, 11.5), (x - 1.2, 9.5)], fill=_rgb("ffffff"))
    return im


ICONS = [   # (이름, 번호, 그림)
    ("mg_hole", 0, hole), ("mg_mole", 1, lambda: mole()), ("mg_mole_hit", 2, lambda: mole("hit")), ("mg_mole_gold", 3, lambda: mole("gold")),
    ("mg_bomb", 4, lambda: bomb()), ("mg_boom", 5, lambda: bomb(True)),
    ("mg_brick_red", 10, lambda: brick("e0402a")), ("mg_brick_orange", 11, lambda: brick("ff8a1f")), ("mg_brick_yellow", 12, lambda: brick("f2c23a")),
    ("mg_brick_green", 13, lambda: brick("3ab84a")), ("mg_brick_blue", 14, lambda: brick("2a7aff")), ("mg_brick_purple", 15, lambda: brick("a04aff")),
    ("mg_brick_steel", 16, lambda: brick("8a94a8", steel=True)), ("mg_brick_cracked", 17, lambda: brick("8a94a8", steel=True, cracked=True)),
    ("mg_ball", 18, ball), ("mg_paddle_l", 19, lambda: paddle("l")), ("mg_paddle_m", 20, lambda: paddle("m")), ("mg_paddle_r", 21, lambda: paddle("r")),
    ("mg_tile", 30, lambda: tile(False)), ("mg_tile_open", 31, lambda: tile(True)),
] + [("mg_num_%d" % n, 31 + n, (lambda n=n: number(n))) for n in range(1, 9)] + [
    ("mg_flag", 40, flag), ("mg_mine", 41, lambda: mine()), ("mg_mine_boom", 42, lambda: mine(True)),
    ("mg_card_back", 50, card_back),
] + [("mg_face_%d" % k, 51 + k, (lambda k=k: face(k))) for k in range(14)] + [
    ("mg_coin_mole", 70, lambda: coin("c8864a", "mole")), ("mg_coin_brick", 71, lambda: coin("e0602a", "brick")),
    ("mg_coin_mine", 72, lambda: coin("6a8ab0", "mine")), ("mg_coin_card", 73, lambda: coin("c83a6a", "card")),
    ("mg_easy", 80, lambda: medal("3ab84a", 1)), ("mg_normal", 81, lambda: medal("f2a01f", 2)), ("mg_hard", 82, lambda: medal("e02a2a", 3)),
]


def write_icons(pack, ns, write_json):
    """아이콘 텍스처 + 모델. 반환: [(cmd, 모델)] (PAPER 에 붙임)"""
    tex = os.path.join(pack, "assets", ns, "textures", "item", "minigame")
    os.makedirs(tex, exist_ok=True)
    out = []
    for name, off, fn in ICONS:
        fn().save(os.path.join(tex, name + ".png"))
        write_json(os.path.join(pack, "assets", ns, "models", "item", "minigame", name + ".json"),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": ns + ":item/minigame/" + name}})
        out.append((ICON_BASE + off, ns + ":item/minigame/" + name))
    return out


# ------------------------------------------------------------------ GUI 배경
def _sx(idx):
    return 7 + (idx % 9) * 18, 17 + (idx // 9) * 18


def _area(img, top_rows, painter):
    """제목 줄 아래 ~ top_rows 줄까지 (슬롯 영역) 를 painter(x, y) 색으로 다시 칠함"""
    px = img.load()
    for y in range(16, 17 + top_rows * 18 + 1):
        for x in range(4, 172):
            c = painter(x, y)
            if c: px[x, y] = c


def _n(x, y, k=3):
    return ((x * 73856093) ^ (y * 19349663)) % (2 * k + 1) - k


def bg_mole(img):
    _area(img, 5, lambda x, y: (58 + _n(x, y, 6), 128 + _n(x, y, 8) + (6 if (x // 3 + y // 5) % 7 == 0 else 0), 52 + _n(x, y, 5), 255))
    d = ImageDraw.Draw(img)
    for i in range(4, 172, 6):   # 위쪽 울타리
        d.rectangle([i, 17, i + 2, 24], fill=(150, 104, 58, 255))
    d.line([(4, 19), (171, 19)], fill=(120, 80, 40, 255)); d.line([(4, 23), (171, 23)], fill=(120, 80, 40, 255))
    for idx in (11, 13, 15, 20, 22, 24, 29, 31, 33):   # 두더지 구멍 흙 둔덕
        x, y = _sx(idx)
        d.ellipse([x - 3, y + 6, x + 20, y + 20], fill=(122, 84, 46, 255))
        d.ellipse([x - 1, y + 8, x + 18, y + 18], fill=(92, 62, 32, 255))
    for k in range(20):   # 꽃
        x, y = 8 + (k * 37) % 160, 30 + (k * 53) % 70
        d.point((x, y), fill=[(255, 230, 80, 255), (255, 120, 160, 255), (255, 255, 255, 255)][k % 3])
    d.rectangle([4, 17 + 5 * 18 - 1, 171, 17 + 6 * 18 + 1], fill=(64, 44, 28, 255), outline=(201, 161, 59, 255))
    return img


def bg_breakout(img):
    _area(img, 6, lambda x, y: (12 + (8 if (x - 7) % 18 == 0 or (y - 17) % 18 == 0 else 0) + _n(x, y, 2), 14 + _n(x, y, 2) + (10 if (x - 7) % 18 == 0 or (y - 17) % 18 == 0 else 0), 34 + _n(x, y, 3) + (20 if (x - 7) % 18 == 0 or (y - 17) % 18 == 0 else 0), 255))
    d = ImageDraw.Draw(img)
    d.rectangle([5, 15, 170, 17 + 6 * 18 + 1], outline=(74, 216, 255, 255))
    d.rectangle([6, 16, 169, 17 + 6 * 18], outline=(30, 90, 140, 255))
    y = 17 + 5 * 18 - 1
    d.line([(6, y), (169, y)], fill=(255, 80, 120, 255))   # 바닥선 (여기로 떨어지면 목숨 -1)
    for k in range(30):
        d.point((10 + (k * 41) % 156, 20 + (k * 29) % 80), fill=(200, 220, 255, 160))
    return img


def bg_mines(img):
    _area(img, 6, lambda x, y: (46 + _n(x, y, 3), 52 + _n(x, y, 3), 62 + _n(x, y, 3), 255))
    d = ImageDraw.Draw(img)
    d.rectangle([5, 15, 170, 17 + 5 * 18 + 1], outline=(201, 161, 59, 255))
    d.rectangle([4, 17 + 5 * 18 + 2, 171, 17 + 6 * 18 + 1], fill=(34, 30, 40, 255), outline=(110, 84, 30, 255))
    return img


def bg_memory(img):
    _area(img, 6, lambda x, y: (22 + _n(x, y, 4), 96 + _n(x, y, 6) + (4 if (x + y) % 9 == 0 else 0), 52 + _n(x, y, 4), 255))
    d = ImageDraw.Draw(img)
    d.rectangle([5, 15, 170, 17 + 5 * 18 + 1], outline=(201, 161, 59, 255))
    d.rectangle([7, 17, 168, 17 + 5 * 18 - 1], outline=(14, 60, 32, 255))
    d.rectangle([4, 17 + 5 * 18 + 2, 171, 17 + 6 * 18 + 1], fill=(60, 30, 20, 255), outline=(110, 84, 30, 255))
    return img


def bg_event(img):
    d = ImageDraw.Draw(img)
    cols = [(224, 64, 64, 255), (255, 200, 60, 255), (60, 170, 255, 255), (80, 200, 100, 255)]
    for i, x in enumerate(range(6, 170, 12)):   # 축제 깃발 줄
        d.polygon([(x, 17), (x + 10, 17), (x + 5, 24)], fill=cols[i % 4])
    d.line([(4, 17), (171, 17)], fill=(80, 60, 40, 255))
    for (top, bottom), col in zip(((10, 28), (12, 30), (14, 32), (16, 34)), [(200, 136, 74, 255), (224, 96, 42, 255), (106, 138, 176, 255), (200, 58, 106, 255)]):
        xa, ya = _sx(top); _, yb = _sx(bottom)   # 게임 · 코인 칸을 세로로 묶는 테두리
        d.rectangle([xa - 3, ya - 3, xa + 20, yb + 20], outline=col)
        d.rectangle([xa - 2, ya + 19, xa + 19, yb - 2], fill=tuple(int(v * 0.35) for v in col[:3]) + (255,))
    return img


def bg_stock(img):
    _area(img, 5, lambda x, y: (14 + _n(x, y, 2) + (10 if y % 12 == 0 else 0), 20 + _n(x, y, 2) + (12 if y % 12 == 0 else 0), 26 + _n(x, y, 2) + (14 if y % 12 == 0 else 0), 255))
    d = ImageDraw.Draw(img)
    pts, v = [], 100
    for x in range(6, 170, 4):   # 오르내리는 차트 선
        v += math.sin(x * 0.21) * 6 + math.cos(x * 0.07) * 3 - 0.6
        pts.append((x, max(22, min(104, int(v)))))
    for a, b in zip(pts, pts[1:]):
        d.line([a, b], fill=(255, 70, 70, 120) if b[1] < a[1] else (70, 130, 255, 120))
    d.rectangle([4, 17 + 5 * 18 - 1, 171, 17 + 6 * 18 + 1], fill=(30, 26, 20, 255), outline=(201, 161, 59, 255))
    return img


def bg_eshop(img):
    d = ImageDraw.Draw(img)
    for i, x in enumerate(range(4, 172, 8)):   # 천막 줄무늬
        d.rectangle([x, 16, x + 7, 22], fill=(224, 64, 64, 255) if i % 2 == 0 else (250, 240, 220, 255))
    for x in range(4, 172, 8):
        d.pieslice([x, 18, x + 7, 26], 0, 180, fill=(224, 64, 64, 255) if (x // 8) % 2 == 0 else (250, 240, 220, 255))
    return img


# 키: (글리프, 줄 수, 슬롯 테두리를 그릴 칸, 그리기)
SHOP_ITEMS = set(range(10, 17)) | set(range(19, 26)) | set(range(28, 35)) | set(range(37, 44))
BACKGROUNDS = {
    "event": ("\ue009", 6, {10, 12, 14, 16, 28, 30, 32, 34, 40, 49}, bg_event),
    "mole": ("\ue00a", 6, {47, 49, 51}, bg_mole),
    "breakout": ("\ue00b", 6, set(), bg_breakout),
    "mines": ("\ue00c", 6, set(range(45)) | {47, 49, 51}, bg_mines),
    "memory": ("\ue00d", 6, {47, 49, 51}, bg_memory),
    "stock": ("\ue00e", 6, {11, 12, 14, 15, 20, 21, 23, 24, 29, 30, 32, 33, 38, 39, 41, 42, 45, 48, 49, 50}, bg_stock),
    "eshop": ("\ue00f", 6, {19, 21, 23, 25, 40, 45, 49}, bg_eshop),
    "eshop_sub": ("\ue010", 6, SHOP_ITEMS | {45, 49}, bg_eshop),
}


def backgrounds(gui_background):
    """빈 판 → 게임별 그림 → 마지막에 슬롯 테두리 (그림이 슬롯을 덮지 않게)"""
    slot = gui_background.__globals__["_slot"]
    out = {}
    for key, (ch, rows, slots, fn) in BACKGROUNDS.items():
        img = fn(gui_background(rows, set()))
        d = ImageDraw.Draw(img)
        for idx in sorted(slots):
            x, y = _sx(idx)
            slot(d, x, y, True, True)
        out[key] = (ch, rows, img)
    return out
