"""
v5.10.34 메뉴 아이콘 (마크에이지 4R 풍): 32x32, 은 테두리 메달(또는 방패 문장) + 기능별 그림.
PAPER + CustomModelData ICON_BASE + 순번. 순번 · 이름은 Java kr.rpgcraft.gui.UiIcon (자동 생성) 과 같다.
그림은 4배(128px)로 그리고 32px 로 줄여 가장자리를 부드럽게.
"""
import math
import os

from PIL import Image, ImageDraw

ICON_BASE = 12600
S = 128          # 그리는 크기
OUT = 32         # 텍스처 크기
K = S / 32.0     # 32px 좌표 → 그리는 좌표


def _rgb(h, a=255):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (a,)


def _sh(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c[:3]) + (c[3] if len(c) > 3 else 255,)


def P(*pts):
    return [(x * K, y * K) for x, y in pts]


def B(x0, y0, x1, y1):
    return [x0 * K, y0 * K, x1 * K, y1 * K]


# ------------------------------------------------------------------ 바탕 (메달 · 방패 문장)
SILVER, SILVER_D, SILVER_L = _rgb("c8ccd6"), _rgb("5a5e6a"), _rgb("f4f6fa")


def medal(d, col):
    """둥근 사각 메달: 바깥 어두운 테 → 은 테 → 색 그라데이션 판 + 위쪽 빛"""
    c = _rgb(col)
    d.rounded_rectangle(B(1, 1, 31, 31), 6 * K, fill=_rgb("1c1a20"))
    d.rounded_rectangle(B(2, 2, 30, 30), 5 * K, fill=SILVER)
    d.rounded_rectangle(B(3.4, 3.4, 28.6, 28.6), 4 * K, fill=SILVER_D)
    for i in range(int(24 * K)):
        t = i / (24 * K)
        y = 4 * K + i
        d.line([(4.6 * K, y), (27.4 * K, y)], fill=_sh(c, 1.15 - 0.55 * t))
    d.rounded_rectangle(B(4, 4, 28, 28), 3.6 * K, outline=_sh(c, 0.45), width=int(0.8 * K))
    d.arc(B(5, 5, 27, 22), 200, 340, fill=(255, 255, 255, 70), width=int(1.2 * K))
    for (x, y) in ((2.6, 2.6), (29.4, 2.6), (2.6, 29.4), (29.4, 29.4)):   # 모서리 리벳
        d.ellipse(B(x - 1, y - 1, x + 1, y + 1), fill=SILVER_L)


def crest(d, col):
    """방패 문장 (스탯용): 은 테두리 방패 + 색 판"""
    c = _rgb(col)
    outer = P((3, 2), (29, 2), (29, 17), (16, 31), (3, 17))
    d.polygon(outer, fill=_rgb("1c1a20"))
    d.polygon(P((4, 3), (28, 3), (28, 16.6), (16, 29.6), (4, 16.6)), fill=SILVER)
    inner = P((5.6, 4.6), (26.4, 4.6), (26.4, 16), (16, 27.6), (5.6, 16))
    d.polygon(inner, fill=_sh(c, 0.9))
    for i in range(int(23 * K)):
        t = i / (23 * K)
        y = 4.6 * K + i
        w = 10.4 if y < 16 * K else 10.4 * (1 - (y / K - 16) / 11.6)
        if w > 0:
            d.line([((16 - w) * K, y), ((16 + w) * K, y)], fill=_sh(c, 1.2 - 0.6 * t))
    d.polygon(inner, outline=_sh(c, 0.45), width=int(0.8 * K))
    d.line(P((6.5, 6), (25.5, 6)), fill=(255, 255, 255, 80), width=int(0.8 * K))
    for x in (4, 28):
        d.ellipse(B(x - 1.1, 2.5, x + 1.1, 4.7), fill=SILVER_L)


# ------------------------------------------------------------------ 그림 도구 (흰 그림 + 아래 그림자)
INK, SHADOW = _rgb("fbf7ee"), (16, 12, 20, 170)


def ink(d, fn, col=INK):
    """같은 모양을 1px 아래에 그림자로 한 번, 위에 본색으로 한 번"""
    fn(d, (0, 0.9), SHADOW)
    fn(d, (0, 0), col)


def _o(pts, off):
    return [(x + off[0] * K, y + off[1] * K) for x, y in pts]


def _b(box, off):
    return [box[0] + off[0] * K, box[1] + off[1] * K, box[2] + off[0] * K, box[3] + off[1] * K]


def poly(*pts):
    return lambda d, off, c: d.polygon(_o(P(*pts), off), fill=c)


def ell(x0, y0, x1, y1, w=None):
    return (lambda d, off, c: d.ellipse(_b(B(x0, y0, x1, y1), off), outline=c, width=int(w * K))) if w else \
        (lambda d, off, c: d.ellipse(_b(B(x0, y0, x1, y1), off), fill=c))


def rect(x0, y0, x1, y1, r=0, w=None):
    if w:
        return lambda d, off, c: d.rounded_rectangle(_b(B(x0, y0, x1, y1), off), r * K, outline=c, width=int(w * K))
    return lambda d, off, c: d.rounded_rectangle(_b(B(x0, y0, x1, y1), off), r * K, fill=c)


def line(pts, w):
    return lambda d, off, c: d.line(_o(P(*pts), off), fill=c, width=int(w * K), joint="curve")


def arc(x0, y0, x1, y1, a0, a1, w):
    return lambda d, off, c: d.arc(_b(B(x0, y0, x1, y1), off), a0, a1, fill=c, width=int(w * K))


def star(cx, cy, r1, r2, n=5, rot=-90):
    pts = []
    for i in range(n * 2):
        a = math.radians(rot + i * 180 / n)
        r = r1 if i % 2 == 0 else r2
        pts.append((cx + math.cos(a) * r, cy + math.sin(a) * r))
    return poly(*pts)


def many(*fns):
    return lambda d, off, c: [f(d, off, c) for f in fns]


GOLD, RED, BLUE, GREEN, PURPLE = _rgb("ffd46a"), _rgb("ff6a6a"), _rgb("7ac8ff"), _rgb("8af07a"), _rgb("d49aff")


# ------------------------------------------------------------------ 그림들
def sym_season(d):
    ink(d, rect(7, 9, 25, 23, 2))
    ink(d, many(ell(5, 14, 9, 18), ell(23, 14, 27, 18)), _rgb("2a2430"))
    ink(d, line([(12, 9.5), (12, 22.5)], 0.8), _rgb("c8a060"))
    ink(d, star(18.5, 16, 4.6, 2.0), GOLD)


def sym_skill(d):
    ink(d, many(poly((5, 10), (15.5, 12), (15.5, 25), (5, 23)), poly((26.5, 10), (16.5, 12), (16.5, 25), (26.5, 23))))
    ink(d, many(line([(7, 14), (13.5, 15.2)], 0.7), line([(7, 17.5), (13.5, 18.7)], 0.7), line([(25, 14), (18.5, 15.2)], 0.7)), _rgb("9a8ab0"))
    ink(d, star(21, 7, 3.6, 1.2, 4), PURPLE)


def sym_enhance(d):
    ink(d, many(poly((6, 16), (26, 16), (24, 19), (19, 19), (19, 22), (23, 25), (9, 25), (13, 22), (13, 19), (8, 19))))
    ink(d, many(line([(9, 13), (19, 5)], 2.2)), _rgb("c8a060"))
    ink(d, rect(16.5, 3, 23.5, 7.5, 1), _rgb("d8dce6"))
    ink(d, many(star(24, 11, 2.6, 0.8, 4), star(8, 9, 1.8, 0.6, 4)), GOLD)


def sym_rune(d):
    ink(d, poly((10, 5), (22, 5), (26, 13), (22, 27), (10, 27), (6, 13)), _rgb("cfc8e0"))
    ink(d, many(line([(16, 9), (16, 23)], 1.4), line([(16, 12), (21, 9)], 1.4), line([(16, 16), (11, 13)], 1.4), line([(16, 20), (21, 17)], 1.4)), PURPLE)


def sym_potion(d):
    ink(d, many(rect(13, 4, 19, 10, 1), ell(7, 10, 25, 28)))
    ink(d, many(ell(9, 15, 23, 26)), RED)
    ink(d, many(ell(11, 17, 14, 20)), _rgb("ffd0d0"))
    ink(d, rect(12.5, 3, 19.5, 5.5, 1), _rgb("a07040"))


def sym_aura(d):
    ink(d, many(poly((16, 4), (20, 12), (17, 26), (12, 12)), poly((9, 10), (12, 16), (10, 26), (6, 16)), poly((23, 10), (26, 16), (22, 26), (20, 16))), _rgb("e0b8ff"))
    ink(d, line([(16, 6), (15, 22)], 0.6), (255, 255, 255, 255))


def sym_smith(d):
    ink(d, many(line([(9, 25), (20, 12)], 2.4)), _rgb("c8a060"))
    ink(d, poly((14, 6), (24, 4), (27, 10), (22, 14), (17, 12)), _rgb("d8dce6"))
    ink(d, many(star(9, 9, 2.2, 0.7, 4), star(25, 20, 1.8, 0.6, 4)), GOLD)


def sym_quest(d):
    ink(d, many(rect(8, 6, 24, 26, 1.5)), _rgb("f2e6c8"))
    ink(d, many(ell(6, 4, 10, 9), ell(22, 23, 26, 28)), _rgb("d8c49a"))
    ink(d, many(line([(11, 11), (21, 11)], 0.8), line([(11, 15), (21, 15)], 0.8)), _rgb("9a8460"))
    ink(d, line([(12, 20), (15, 23), (21, 17)], 1.6), GREEN)


def sym_shop(d):
    ink(d, many(ell(6, 11, 26, 28), poly((11, 6), (21, 6), (19, 12), (13, 12))), _rgb("c89a5a"))
    ink(d, rect(10, 10, 22, 13, 1), _rgb("8a5a2a"))
    ink(d, many(ell(11, 15, 21, 25)), GOLD)
    ink(d, line([(16, 17), (16, 23)], 1.2), _rgb("a07010"))


def sym_warp(d):
    for i, r in enumerate((10, 7, 4)):
        ink(d, arc(16 - r, 16 - r, 16 + r, 16 + r, 30 + i * 60, 300 + i * 60, 1.6), [_rgb("8af0ff"), _rgb("c8a8ff"), INK][i])
    ink(d, ell(14.5, 14.5, 17.5, 17.5))


def sym_guild(d):
    ink(d, line([(9, 4), (9, 28)], 1.6), _rgb("c8a060"))
    ink(d, poly((10, 6), (25, 6), (25, 19), (17.5, 16.5), (10, 19)), BLUE)
    ink(d, star(17.5, 11.5, 3.4, 1.4), GOLD)
    ink(d, ell(7.6, 2.6, 10.4, 5.4), GOLD)


def sym_potential(d):
    ink(d, star(16, 16, 12, 5.2, 8, -90), _rgb("ffe6a0"))
    ink(d, ell(10, 12, 22, 20), _rgb("2a1f3a"))
    ink(d, ell(13.5, 13, 18.5, 19), _rgb("b06aff"))
    ink(d, ell(15, 14.5, 17, 16.5), INK)


def sym_rank(d):
    ink(d, poly((5, 12), (10, 17), (16, 7), (22, 17), (27, 12), (25, 24), (7, 24)), GOLD)
    ink(d, rect(7, 23, 25, 27, 1), _rgb("e0a830"))
    ink(d, many(ell(14.5, 15, 17.5, 18)), RED)
    ink(d, many(ell(3.5, 10, 6.5, 13), ell(14.5, 5, 17.5, 8), ell(25.5, 10, 28.5, 13)), INK)


def sym_codex(d):
    ink(d, rect(7, 5, 25, 27, 2), _rgb("8a5a3a"))
    ink(d, rect(10, 7, 24, 25, 1), _rgb("f2e6c8"))
    ink(d, rect(7, 5, 10, 27, 1), _rgb("6a3a24"))
    ink(d, star(17, 15, 4.4, 1.8), GOLD)
    ink(d, line([(13, 21), (21, 21)], 0.8), _rgb("9a8460"))


def sym_ach(d):
    ink(d, many(poly((10, 4), (14, 4), (17, 13), (13, 14)), poly((22, 4), (18, 4), (15, 13), (19, 14))), RED)
    ink(d, ell(8, 11, 24, 27), GOLD)
    ink(d, ell(11, 14, 21, 24), _rgb("e0a830"))
    ink(d, star(16, 19, 3.6, 1.5), INK)


def sym_attend(d):
    ink(d, rect(6, 7, 26, 27, 2))
    ink(d, rect(6, 7, 26, 12, 2), RED)
    ink(d, many(rect(10, 4, 12, 9, 0.5), rect(20, 4, 22, 9, 0.5)), _rgb("5a5e6a"))
    ink(d, line([(11, 19), (15, 23), (22, 15)], 1.8), GREEN)


def sym_accessory(d):
    ink(d, ell(7, 10, 25, 28, 2.6), GOLD)
    ink(d, poly((16, 3), (21, 8), (16, 13), (11, 8)), _rgb("6ae8ff"))
    ink(d, poly((16, 4.5), (18.5, 8), (16, 9)), INK)


def sym_roulette(d):
    ink(d, ell(5, 5, 27, 27))
    cols = [RED, _rgb("2a2430"), GOLD, _rgb("2a2430"), GREEN, _rgb("2a2430"), BLUE, _rgb("2a2430")]
    for i, c in enumerate(cols):
        d.pieslice(B(6.5, 6.5, 25.5, 25.5), i * 45 - 90, (i + 1) * 45 - 90, fill=c)
    ink(d, ell(13, 13, 19, 19), _rgb("e0e0e8"))
    ink(d, poly((14, 2), (18, 2), (16, 7)), RED)


def sym_settings(d):
    teeth = []
    for i in range(16):
        a = math.radians(i * 22.5)
        r = 11 if i % 2 == 0 else 8.5
        teeth.append((16 + math.cos(a) * r, 16 + math.sin(a) * r))
    ink(d, poly(*teeth), _rgb("d8dce6"))
    ink(d, ell(12, 12, 20, 20), _rgb("4a4e5a"))


def sym_job(d):
    ink(d, many(line([(7, 25), (24, 7)], 1.8), line([(25, 25), (8, 7)], 1.8)), _rgb("e0e4ee"))
    ink(d, many(line([(9, 19), (13, 23)], 1.6), line([(23, 19), (19, 23)], 1.6)), GOLD)
    ink(d, many(ell(5, 24, 9, 28), ell(23, 24, 27, 28)), _rgb("a07040"))


def sym_hidden(d):
    ink(d, poly((16, 3), (27, 12), (25, 26), (16, 29), (7, 26), (5, 12)), _rgb("3a2a4a"))
    ink(d, poly((8, 14), (15, 13), (14, 17), (9, 17)), _rgb("d070ff"))
    ink(d, poly((24, 14), (17, 13), (18, 17), (23, 17)), _rgb("d070ff"))
    ink(d, line([(12, 23), (16, 21), (20, 23)], 1), _rgb("9a6ab0"))


def sym_help(d):
    ink(d, arc(9, 5, 23, 19, 180, 405, 3), GOLD)
    ink(d, line([(16, 18), (16, 21)], 3), GOLD)
    ink(d, ell(14, 23, 18, 27), GOLD)


def sym_tower(d):
    ink(d, poly((10, 27), (11, 11), (21, 11), (22, 27)), _rgb("cfc8e0"))
    ink(d, poly((9, 11), (16, 3), (23, 11)), PURPLE)
    ink(d, many(rect(14, 15, 18, 20, 1.5), rect(14, 22, 18, 27, 1.5)), _rgb("3a2a4a"))
    ink(d, many(rect(9, 10, 11, 12), rect(21, 10, 23, 12)), _rgb("cfc8e0"))


def sym_mastery(d):
    ink(d, many(arc(5, 6, 27, 30, 110, 250, 1.8), arc(5, 6, 27, 30, -70, 70, 1.8)), GREEN)
    ink(d, star(16, 16, 8, 3.4), GOLD)
    ink(d, many(ell(4, 14, 7, 17), ell(25, 14, 28, 17), ell(6, 21, 9, 24), ell(23, 21, 26, 24)), GREEN)


# ---- 스탯
def sym_str(d):
    ink(d, many(line([(9, 23), (22, 9)], 1.8), line([(23, 23), (10, 9)], 1.8)), _rgb("eef0f6"))
    ink(d, many(line([(10, 18), (14, 22)], 1.4), line([(22, 18), (18, 22)], 1.4)), GOLD)


def sym_dex(d):
    ink(d, poly((22, 6), (25, 7), (13, 22), (10, 23), (11, 20)), _rgb("eef0f6"))
    ink(d, poly((10, 23), (7, 24), (8, 21)), _rgb("b0c8ff"))
    ink(d, many(line([(20, 10), (14, 9)], 0.9), line([(18, 13), (12, 12)], 0.9), line([(16, 16), (11, 15.4)], 0.9)), _rgb("b0c8ff"))


def sym_adv(d):
    ink(d, poly((10, 7), (17, 7), (17, 18), (23, 20), (24, 24), (10, 24)), _rgb("c89a5a"))
    ink(d, rect(10, 7, 17, 10, 1), _rgb("eef0f6"))
    ink(d, many(ell(19, 5, 25, 11)), _rgb("9af07a"))


def sym_point(d):
    ink(d, poly((16, 4), (25, 12), (16, 27), (7, 12)), _rgb("ffe08a"))
    ink(d, poly((16, 4), (20, 12), (16, 27)), _rgb("ffb840"))
    ink(d, line([(7, 12), (25, 12)], 0.8), _rgb("fff6c8"))


def sym_info(d):
    ink(d, rect(8, 5, 24, 27, 2), _rgb("f2e6c8"))
    ink(d, many(line([(11, 10), (21, 10)], 1), line([(11, 14), (21, 14)], 1), line([(11, 18), (21, 18)], 1), line([(11, 22), (17, 22)], 1)), _rgb("9a8460"))


# ---- 설정
def sym_sidebar(d):
    ink(d, rect(9, 5, 25, 27, 1.5), _rgb("3a3640"))
    ink(d, many(*[line([(12, 9 + i * 4), (22 - (i % 2) * 3, 9 + i * 4)], 1.2) for i in range(5)]), INK)


def sym_compass(d):
    ink(d, ell(5, 5, 27, 27), _rgb("d8c49a"))
    ink(d, ell(7, 7, 25, 25), _rgb("2a2430"))
    ink(d, poly((16, 8), (18.5, 16), (13.5, 16)), RED)
    ink(d, poly((16, 24), (18.5, 16), (13.5, 16)), INK)


def sym_hud(d):
    ink(d, rect(4, 9, 28, 15, 1.5), _rgb("2a2430"))
    ink(d, rect(5, 10, 21, 14, 1), RED)
    ink(d, rect(4, 18, 28, 24, 1.5), _rgb("2a2430"))
    ink(d, rect(5, 19, 14, 23, 1), GREEN)


def sym_indicator(d):
    ink(d, star(16, 16, 12, 6, 7), _rgb("ffb040"))
    ink(d, many(rect(14.5, 9, 17.5, 18, 1), ell(14.5, 20, 17.5, 23)), _rgb("c02020"))


def sym_sound(d):
    ink(d, poly((6, 13), (11, 13), (17, 7), (17, 25), (11, 19), (6, 19)))
    ink(d, many(arc(14, 10, 24, 22, -50, 50, 1.4), arc(14, 6, 28, 26, -50, 50, 1.4)), _rgb("8af0ff"))


def sym_loot(d):
    ink(d, poly((10, 7), (22, 7), (27, 13), (16, 27), (5, 13)), _rgb("6ae8ff"))
    ink(d, poly((10, 7), (16, 13), (22, 7)), _rgb("c8fbff"))
    ink(d, line([(5, 13), (27, 13)], 0.8), _rgb("2a9ab8"))


def sym_announce(d):
    ink(d, poly((10, 22), (11, 12), (16, 7), (21, 12), (22, 22)), GOLD)
    ink(d, rect(7, 21, 25, 24, 1.5), _rgb("e0a830"))
    ink(d, ell(14, 24, 18, 28), GOLD)
    ink(d, many(arc(2, 8, 12, 22, 120, 240, 1.2), arc(20, 8, 30, 22, -60, 60, 1.2)), INK)


def sym_expchat(d):
    ink(d, rect(5, 6, 24, 20, 3), INK)
    ink(d, poly((9, 19), (14, 19), (8, 25)), INK)
    ink(d, ell(18, 14, 28, 24), _rgb("9af04a"))
    ink(d, ell(20, 16, 23, 19), _rgb("e6ffb0"))


def sym_pvp(d):
    ink(d, many(line([(6, 26), (20, 8)], 1.8), line([(26, 26), (12, 8)], 1.8)), _rgb("eef0f6"))
    ink(d, star(16, 17, 5, 2.2, 6), RED)


def sym_bgm(d):
    ink(d, many(ell(6, 20, 13, 26), ell(18, 17, 25, 23), line([(12, 23), (12, 7)], 1.6), line([(24, 20), (24, 5)], 1.6), poly((12, 7), (24, 4), (24, 8), (12, 11))), _rgb("ff9ad8"))


# ---- 이동 단추
def sym_back(d):
    ink(d, poly((6, 16), (15, 8), (15, 13), (26, 13), (26, 19), (15, 19), (15, 24)))


def sym_next(d):
    ink(d, poly((26, 16), (17, 8), (17, 13), (6, 13), (6, 19), (17, 19), (17, 24)))


def sym_close(d):
    ink(d, many(line([(9, 9), (23, 23)], 3), line([(23, 9), (9, 23)], 3)), _rgb("ff8a8a"))


# ---- v5.10.42 상점 아이콘
def sym_shop_general(d):
    ink(d, many(ell(6, 11, 26, 28), poly((11, 6), (21, 6), (19, 12), (13, 12))), _rgb("c89a5a"))
    ink(d, rect(10, 10, 22, 13, 1), _rgb("8a5a2a"))
    ink(d, many(ell(10, 15, 17, 24)), RED)
    ink(d, rect(17, 16, 23, 24, 1.5), _rgb("8af0ff"))


def sym_weapon(d):
    ink(d, many(line([(7, 25), (24, 8)], 2.2)), _rgb("e8ecf6"))
    ink(d, many(line([(10, 18), (14, 22)], 1.6)), GOLD)
    ink(d, poly((17, 15), (27, 15), (27, 22), (22, 27), (17, 22)), BLUE)
    ink(d, star(22, 19.5, 2.6, 1.1), GOLD)


def _chest(d, col, trim):
    ink(d, poly((8, 7), (13, 6), (16, 9), (19, 6), (24, 7), (27, 13), (24, 15), (23, 27), (9, 27), (8, 15), (5, 13)), col)
    ink(d, many(line([(16, 9), (16, 27)], 0.9), line([(9, 20), (23, 20)], 0.9)), trim)


def sym_arm_warrior(d):
    _chest(d, _rgb("d8dce6"), RED)


def sym_arm_assassin(d):
    ink(d, poly((16, 3), (26, 12), (25, 27), (7, 27), (6, 12)), _rgb("3a3448"))
    ink(d, ell(10, 10, 22, 22), _rgb("15121c"))
    ink(d, many(ell(12, 14, 15, 16), ell(17, 14, 20, 16)), RED)


def sym_arm_adventurer(d):
    _chest(d, _rgb("a8784a"), GREEN)
    ink(d, many(ell(10, 13, 13, 16), ell(19, 13, 22, 16)), GOLD)


def sym_special(d):
    ink(d, many(poly((16, 3), (22, 12), (16, 29), (10, 12))), _rgb("c88aff"))
    ink(d, poly((16, 3), (19, 12), (16, 29)), _rgb("e8c8ff"))
    ink(d, many(star(25, 7, 2.4, 0.8, 4), star(7, 22, 2, 0.7, 4)), INK)


def sym_wandering(d):
    ink(d, rect(8, 9, 24, 27, 3), _rgb("a8784a"))
    ink(d, rect(10, 5, 22, 11, 3, 1.4), _rgb("6a4a2a"))
    ink(d, rect(11, 15, 21, 21, 1.5), _rgb("8a5a2a"))
    ink(d, ell(14.5, 16.5, 17.5, 19.5), GOLD)


def sym_war(d):
    ink(d, line([(8, 4), (8, 28)], 1.6), _rgb("c8a060"))
    ink(d, poly((9, 5), (25, 8), (9, 14)), RED)
    ink(d, many(line([(14, 27), (27, 16)], 1.8), line([(26, 27), (16, 18)], 1.8)), _rgb("e8ecf6"))


def sym_loot(d):
    ink(d, line([(16, 5), (16, 25)], 1.4), _rgb("d8c49a"))
    ink(d, line([(6, 9), (26, 9)], 1.4), _rgb("d8c49a"))
    ink(d, many(arc(3, 9, 11, 19, 0, 180, 1.2), arc(21, 9, 29, 19, 0, 180, 1.2)), _rgb("d8c49a"))
    ink(d, many(ell(4, 12, 10, 17), ell(22, 12, 28, 17)), GOLD)
    ink(d, rect(10, 24, 22, 27, 1), _rgb("a8842a"))


def sym_armor_set(d):
    ink(d, ell(12, 3, 20, 10), _rgb("e8c060"))
    _chest(d, _rgb("e8c060"), _rgb("fff0b0"))


def sym_scroll_shop(d):
    sym_quest(d)


def sym_fish(d):
    ink(d, many(ell(5, 10, 23, 22), poly((21, 16), (28, 9), (28, 23))), _rgb("6ac8e8"))
    ink(d, ell(8, 13, 11, 16), _rgb("102030"))
    ink(d, many(line([(14, 11), (14, 21)], 0.8), line([(18, 11), (18, 21)], 0.8)), _rgb("3a8ab0"))


def sym_transcend(d):
    ink(d, star(16, 16, 13, 4, 8), _rgb("fff0a0"))
    ink(d, line([(10, 23), (22, 9)], 2), INK)
    ink(d, line([(11, 18), (14, 21)], 1.4), GOLD)


def sym_cook(d):
    ink(d, many(rect(6, 14, 26, 26, 3), rect(4, 13, 28, 16, 1.5)), _rgb("8a8f9a"))
    ink(d, many(arc(9, 4, 15, 14, 180, 360, 1), arc(15, 2, 21, 12, 180, 360, 1)), INK)


def sym_wpn_sword(d):
    ink(d, line([(8, 24), (23, 9)], 2.4), _rgb("e8ecf6"))
    ink(d, line([(9, 17), (15, 23)], 1.8), GOLD)
    ink(d, ell(5, 24, 9, 28), _rgb("a07040"))


def sym_wpn_dagger(d):
    ink(d, poly((12, 20), (22, 8), (24, 10), (14, 22)), _rgb("e8ecf6"))
    ink(d, line([(10, 18), (16, 24)], 1.6), GOLD)
    ink(d, line([(8, 26), (12, 22)], 1.8), _rgb("6a4a2a"))


def sym_wpn_axe(d):
    ink(d, line([(9, 27), (21, 6)], 1.8), _rgb("a07040"))
    ink(d, poly((18, 6), (27, 8), (27, 18), (18, 15)), _rgb("e8ecf6"))


def sym_wpn_shield(d):
    ink(d, poly((6, 5), (26, 5), (26, 16), (16, 28), (6, 16)), BLUE)
    ink(d, many(line([(16, 6), (16, 26)], 1.2), line([(7, 13), (25, 13)], 1.2)), GOLD)


def sym_wpn_bow(d):
    ink(d, arc(7, 4, 21, 28, -80, 80, 2.2), _rgb("c8a060"))
    ink(d, line([(15.5, 5), (15.5, 27)], 0.8), INK)
    ink(d, line([(6, 16), (24, 16)], 1.2), _rgb("e8ecf6"))
    ink(d, poly((24, 13), (28, 16), (24, 19)), _rgb("e8ecf6"))


def sym_wpn_staff(d):
    ink(d, line([(9, 27), (20, 10)], 1.8), _rgb("8a5a32"))
    ink(d, ell(17, 3, 27, 13), _rgb("8af0ff"))
    ink(d, ell(20, 6, 23, 9), INK)


def sym_wpn_spear(d):
    ink(d, line([(6, 27), (22, 11)], 1.6), _rgb("8a5a32"))
    ink(d, poly((20, 9), (27, 4), (23, 12)), _rgb("e8ecf6"))
    ink(d, line([(17, 12), (21, 16)], 1.4), RED)


def sym_wpn_special(d):
    sym_wpn_sword(d)
    ink(d, many(star(24, 22, 3.6, 1.2, 4), star(9, 8, 2.4, 0.8, 4)), _rgb("e0b0ff"))


# (이름, 바탕, 색, 그림)
ICONS = [
    ("season_pass", "medal", "3a6ac8", sym_season), ("skill", "medal", "6a3aa8", sym_skill), ("enhance", "medal", "a86a2a", sym_enhance),
    ("rune", "medal", "4a2a7a", sym_rune), ("potion_bag", "medal", "a83a4a", sym_potion), ("aura", "medal", "7a3aa8", sym_aura),
    ("smith", "medal", "8a5a2a", sym_smith), ("quest", "medal", "3a7a4a", sym_quest), ("shop", "medal", "2a8a5a", sym_shop),
    ("warp", "medal", "2a5a8a", sym_warp), ("guild", "medal", "2a4a8a", sym_guild), ("potential", "medal", "5a2a8a", sym_potential),
    ("ranking", "medal", "a8842a", sym_rank), ("codex", "medal", "6a4a2a", sym_codex), ("achievement", "medal", "a8562a", sym_ach),
    ("attendance", "medal", "3a8a6a", sym_attend), ("accessory", "medal", "2a7a8a", sym_accessory), ("roulette", "medal", "8a2a3a", sym_roulette),
    ("settings", "medal", "4a4e5a", sym_settings), ("job", "medal", "8a3a2a", sym_job), ("hidden_job", "medal", "3a1a4a", sym_hidden),
    ("help", "medal", "3a5a7a", sym_help), ("tower", "medal", "4a2a6a", sym_tower), ("mastery", "medal", "2a6a3a", sym_mastery),
    ("stat_str", "crest", "b8282e", sym_str), ("stat_dex", "crest", "2a5ab8", sym_dex), ("stat_adv", "crest", "2a8a3a", sym_adv),
    ("stat_point", "medal", "8a6a1a", sym_point), ("stat_info", "medal", "5a4a3a", sym_info),
    ("opt_sidebar", "medal", "4a4e5a", sym_sidebar), ("opt_compass", "medal", "6a5a3a", sym_compass), ("opt_hud", "medal", "4a3a3a", sym_hud),
    ("opt_indicator", "medal", "8a3a1a", sym_indicator), ("opt_sound", "medal", "2a5a6a", sym_sound), ("opt_loot_notice", "medal", "2a6a8a", sym_loot),
    ("opt_announce", "medal", "8a6a1a", sym_announce), ("opt_exp_chat", "medal", "3a6a2a", sym_expchat), ("opt_pvp", "medal", "8a2a2a", sym_pvp),
    ("opt_bgm", "medal", "7a2a5a", sym_bgm),
    ("nav_back", "medal", "4a4e5a", sym_back), ("nav_next", "medal", "4a4e5a", sym_next), ("nav_close", "medal", "5a2a2a", sym_close),
    # v5.10.42 상점 (뒤에 붙여 기존 번호 유지)
    ("shop_general", "medal", "2a7a4a", sym_shop_general), ("shop_weapon", "medal", "4a4e6a", sym_weapon),
    ("shop_armor_warrior", "medal", "6a2a2a", sym_arm_warrior), ("shop_armor_assassin", "medal", "2a2434", sym_arm_assassin),
    ("shop_armor_adventurer", "medal", "3a5a2a", sym_arm_adventurer), ("shop_special", "medal", "4a2a6a", sym_special),
    ("shop_wandering", "medal", "6a4a2a", sym_wandering), ("shop_war", "medal", "5a2a1a", sym_war), ("shop_loot", "medal", "6a5a1a", sym_loot),
    ("shop_armor_set", "medal", "6a5a1a", sym_armor_set), ("shop_scroll", "medal", "3a5a3a", sym_scroll_shop), ("shop_fish", "medal", "1a4a6a", sym_fish),
    ("shop_transcend", "medal", "7a5a1a", sym_transcend), ("shop_cook", "medal", "5a3a2a", sym_cook),
    ("wpn_sword", "medal", "3a3e4a", sym_wpn_sword), ("wpn_dagger", "medal", "3a3e4a", sym_wpn_dagger), ("wpn_axe", "medal", "3a3e4a", sym_wpn_axe),
    ("wpn_shield", "medal", "3a3e4a", sym_wpn_shield), ("wpn_bow", "medal", "3a3e4a", sym_wpn_bow), ("wpn_staff", "medal", "3a3e4a", sym_wpn_staff),
    ("wpn_spear", "medal", "3a3e4a", sym_wpn_spear), ("wpn_special", "medal", "4a2a6a", sym_wpn_special),
]


def draw(entry, gray=False):
    name, base, col, fn = entry
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    (medal if base == "medal" else crest)(d, col)
    fn(d)
    out = img.resize((OUT, OUT), Image.LANCZOS)
    if gray:   # 꺼진 설정 · 잠긴 기능
        px = out.load()
        for y in range(OUT):
            for x in range(OUT):
                r, g, b, a = px[x, y]
                v = int((r * 0.3 + g * 0.59 + b * 0.11) * 0.75)
                px[x, y] = (v, v, v, a)
    return out


def write_icons(pack, ns, write_json):
    """아이콘 텍스처 + 모델. 반환: [(cmd, 모델)] — 순번 i 는 켜진 그림, 100 + i 는 회색(꺼짐)"""
    tex = os.path.join(pack, "assets", ns, "textures", "item", "ui")
    os.makedirs(tex, exist_ok=True)
    out = []
    for i, e in enumerate(ICONS):
        for gray in (False, True):
            n = "ui_" + e[0] + ("_off" if gray else "")
            draw(e, gray).save(os.path.join(tex, n + ".png"))
            write_json(os.path.join(pack, "assets", ns, "models", "item", "ui", n + ".json"),
                       {"parent": "minecraft:item/generated", "textures": {"layer0": ns + ":item/ui/" + n}})
            out.append((ICON_BASE + i + (100 if gray else 0), ns + ":item/ui/" + n))
    return out


def write_java(path):
    """kr.rpgcraft.gui.UiIcon enum 생성 (순번 = CustomModelData - ICON_BASE)"""
    names = ",\n    ".join(e[0].upper() for e in ICONS)
    src = ('package kr.rpgcraft.gui;\n\n/** 자동 생성 (tools/ui_icons.py): 4R 풍 메뉴 아이콘. PAPER CustomModelData = ' + str(ICON_BASE)
           + ' + 순번 (꺼진 회색 그림은 +100) */\npublic enum UiIcon {\n    ' + names + ';\n\n'
           '    public static final int BASE = ' + str(ICON_BASE) + ';\n\n'
           '    public int cmd(boolean on) {\n        return BASE + ordinal() + (on ? 0 : 100);\n    }\n}\n')
    with open(path, "w", encoding="utf-8") as f:
        f.write(src)
