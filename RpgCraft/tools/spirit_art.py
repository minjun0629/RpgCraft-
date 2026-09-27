"""
사신수 · 사흉수 전용 아트 (32x32)

  청룡의 검   : 용머리 가드(벌린 턱에서 칼날이 뻗어 나옴) · 바람에 깎인 톱니 칼날 · 용비늘 · 여의주 폼멜 · 바람 입자
  백호의 도끼 : 세 갈래 발톱 도끼날(할퀸 자국) · 흑백 줄무늬 · 푸른 호안(虎眼) 보석 · 털 감은 자루
  주작의 단검 : 불꽃 칼날(붉음→주황→황금 그라데이션) · 펼친 날개 가드 · 꼬리 깃털 폼멜 · 불티
  현무의 창   : 거북 등껍질 가드 · 자루를 휘감은 검은 뱀 · 비취 잎날
  사신수 방어구 4세트 / 사흉수 갑주 4부위 / 기운 뽑기 결정 5속성
"""
import math
import pack_art as A
from pack_art import Canvas, palette, blade, grip, poly_st, disc_st, gem_st, line_st, shaft, finish, axe_finish, engrave, poly_xy, disc_xy, gem_xy, N


def const(v):
    return lambda u: v


# ================================================================ 무기
def qinglong(cv):
    jag = lambda u: 1.9 - 0.55 * u + (0.45 if math.sin(u * 22) > 0.2 else 0)
    blade(cv, 12.5, 35.6, jag, lambda u: 1.7 - 0.45 * u, tip=0.2, runes=(0.08, 0.85))
    cv.fill_st(lambda s, t: 14 <= s <= 31 and -1.2 < t < 1.2 and (int(s) + int((t + 2) * 1.6)) % 3 == 0 and cv.stage == 0, "x")
    cv.fill_st(lambda s, t: 15 <= s <= 30 and abs(t) < 0.45 and int(s) % 2 == 0, "c")
    grip(cv, 2.8, 8.2, 1.05)
    disc_st(cv, 9.8, 0, 2.5, lambda s, t: "g" if t < 0.3 else "G")                  # 용머리
    poly_st(cv, [(10.6, -1.0), (14.0, -3.4), (12.6, -1.2)], "g")                    # 윗턱
    poly_st(cv, [(10.6, 1.0), (14.0, 3.4), (12.6, 1.2)], "G")                       # 아랫턱
    line_st(cv, 8.6, -1.8, 5.8, -4.6, 0.55, "G")                                     # 뿔
    line_st(cv, 8.6, 1.8, 5.8, 4.6, 0.55, "G")
    cv.fill_st(lambda s, t: abs(s - 10.3) < 0.6 and 0.5 < abs(t) < 1.3, "c")         # 눈
    disc_st(cv, 1.5, 0, 1.7, "c")                                                    # 여의주
    gem_st(cv, 1.5, 0, 1.0, "c")
    return None


def qinglong_extra(cv):
    for (x, y) in [(26, 2), (30, 9), (21, 6)]:
        cv.sparkle(x, y, 1)


def baihu(cv):
    shaft(cv, 1, 30, 1.0, wrap_to=9)
    pts = [(16.6, 0.6), (13.6, 5.8), (17.4, 11.4), (25.4, 12.2), (32.6, 7.4), (31.4, 0.6)]
    area = set(poly_st(cv, pts, "b"))
    for (x, y) in list(area):
        s_, t = cv.st(x, y)
        edge = any((x + dx, y + dy) not in area for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if edge and t > 3:
            cv.set(x, y, "C" if cv.stage >= 2 else "h")
        elif abs(t) < 1.6:
            cv.set(x, y, "s")
        cv.light[(x, y)] = 0.84 + 0.035 * min(10, t)
    for k in range(3):                                                               # 세 줄 발톱 자국
        a = 18.2 + k * 3.8
        cv.fill_st(lambda s_, t, a=a: (s_, t) and abs((s_ - a) - (t - 3.0) * 0.55) < 0.55 and 3.0 < t < 10.4 and cv.st and True, "x")
    poly_st(cv, [(21.0, -0.9), (25.5, -5.2), (24.8, -0.9)], "G")                    # 뒤쪽 가시
    disc_st(cv, 31.8, 0, 1.3, "g")
    gem_st(cv, 23.8, 2.6, 1.4, "c")                                                  # 호안
    engrave(cv, list(area))
    return area


def zhuque(cv):
    flame = lambda u: 1.7 * (1 - u) + 0.45 + 0.45 * math.sin(u * 13)
    hit = blade(cv, 15.4, 33.6, flame, lambda u: 1.3 * (1 - u) + 0.4 + 0.4 * math.sin(u * 11 + 1.2),
                c=lambda u: 0.5 * math.sin(u * 7), tip=0.3, runes=(0.1, 0.8))
    for (x, y) in hit:
        s, t = cv.st(x, y)
        u = (s - 15.4) / 18.2
        k = cv.get(x, y)
        if k in ("c", "C"):
            continue
        cv.set(x, y, "x" if u < 0.3 else ("b" if u < 0.68 else "h"))
    for sg in (-1, 1):                                                               # 날개
        pts = [(15.0, sg * 1.0), (13.0, sg * 3.2), (14.2, sg * 3.5), (11.8, sg * 5.2), (13.4, sg * 5.4), (10.4, sg * 6.8), (15.8, sg * 2.2)]
        poly_st(cv, pts, "g" if sg < 0 else "G")
    grip(cv, 8.6, 14.2, 1.0)
    for dt in (-2.4, 0, 2.4):                                                        # 꼬리 깃털
        line_st(cv, 8.2, dt * 0.3, 3.0, dt, 0.55, "x")
        disc_st(cv, 2.6, dt * 1.05, 0.8, "h")
    gem_st(cv, 15.0, 0, 1.0, "c")
    return None


def zhuque_extra(cv):
    for (x, y) in [(27, 3), (23, 8), (29, 10)]:
        cv.sparkle(x, y, 1)


def xuanwu(cv):
    shaft(cv, 0.5, 23.4, 0.9, wrap_to=0)
    cv.fill_st(lambda s, t: 2 <= s <= 21.5 and abs(t - 1.25 * math.sin(s * 0.95)) < 0.55, "X")   # 휘감은 뱀
    disc_st(cv, 22.2, 1.4, 1.1, "X")
    cv.fill_st(lambda s, t: abs(s - 22.6) < 0.5 and abs(t - 1.7) < 0.5, "e")
    cv.fill_st(lambda s, t: (s - 24.0) ** 2 / 4.8 + t * t / 11.5 <= 1,
               lambda s, t: "x" if (int((s + 30) * 1.1) + int((t + 30) * 0.9)) % 3 == 0 else "g")  # 등껍질
    blade(cv, 25.6, 36.2, lambda u: 2.4 * math.sin(max(0.0, u) * math.pi * 0.85 + 0.3), tip=0.3, runes=(0.05, 0.85))
    cv.fill_st(lambda s, t: 27 <= s <= 34 and abs(t) < 0.45, "c")
    return None


WEAPONS = {
    "spirit_qinglong": ("sword", palette("3fb8ff", guard="ffd23f", grip="1f6b45", accent="ff4040", glow="bff8ff", extra="8fe0ff", outline="0a2a4a"), qinglong, qinglong_extra),
    "spirit_baihu": ("axe", palette("f4f4f4", guard="9aa3ad", grip="e8e0d0", accent="3fa9ff", glow="9fe0ff", extra="1a1a1a", outline="202028"), baihu, None),
    "spirit_zhuque": ("dagger", palette("ff8a1f", guard="ffc93c", grip="5a1010", accent="ffe066", glow="fff0a0", extra="c42a1a", outline="3a0808"), zhuque, zhuque_extra),
    "spirit_xuanwu": ("spear", palette("2fbf71", guard="7a5a2a", grip="9a7a4a", accent="ff3030", glow="a0ffcf", extra="14241a", outline="0a1a10"), xuanwu, None),
}


def weapon(name, stage=0):
    kind, pal, fn, extra = WEAPONS[name]
    cv = Canvas(pal, stage)
    area = fn(cv)
    if area is not None:
        img = axe_finish(cv, area)
        return img
    engrave(cv, [p for b in cv.blades for p in b])
    cv.outline()
    if extra:
        extra(cv)
    if stage >= 2 and cv.blades:
        allpx = [p for b in cv.blades for p in b]
        tip = max(allpx, key=lambda p: p[0] - p[1])
        cv.sparkle(min(N - 3, tip[0] + 1), max(2, tip[1] - 1), 2)
    return cv.render()


# ================================================================ 방어구 아이콘 (정면)
SHAPES = {
    0: [(8, 8), (11, 4), (16, 3), (21, 4), (24, 8), (25, 16), (25, 22), (21, 23), (21, 17), (11, 17), (11, 23), (7, 22), (7, 16)],   # 투구
    1: [(3, 7), (10, 4), (13, 7), (19, 7), (22, 4), (29, 7), (28, 14), (24, 13), (24, 28), (8, 28), (8, 13), (4, 14)],               # 갑옷
    2: [(8, 4), (24, 4), (25, 28), (18, 28), (17, 12), (15, 12), (14, 28), (7, 28)],                                                 # 바지
    3: [(5, 10), (13, 10), (13, 22), (15, 26), (15, 28), (3, 28), (3, 24), (5, 22), (19, 10), (27, 10), (27, 22), (29, 24), (29, 28), (17, 28), (17, 26), (19, 22)],
}


def _shape(cv, slot):
    if slot == 3:
        a = set(poly_xy(cv, [(5, 10), (13, 10), (13, 22), (15, 25), (15, 28), (3, 28), (3, 24), (5, 22)], "b"))
        a |= set(poly_xy(cv, [(19, 10), (27, 10), (27, 22), (29, 24), (29, 28), (17, 28), (17, 25), (19, 22)], "b"))
        return a
    return set(poly_xy(cv, SHAPES[slot], "b"))


def _rim(cv, area):
    for (x, y) in area:
        if any((x + dx, y + dy) not in area for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            cv.set(x, y, "r" if x + y < 32 else "R")


def _dome(cv, area):
    cx = sum(p[0] for p in area) / len(area)
    cy = sum(p[1] for p in area) / len(area)
    R = max(math.hypot(x - cx, y - cy) for x, y in area) or 1
    for (x, y) in area:
        cv.light[(x, y)] = 1.14 - 0.3 * min(1.0, math.hypot(x - cx + 2, y - cy + 2) / R)


PATTERNS = {
    "qinglong": lambda x, y: (x + (y // 2 % 2) * 2) % 4 == 0 and y % 2 == 0,                      # 용비늘
    "baihu": lambda x, y: (x + y // 2) % 6 in (0, 1) and (x * 3 + y) % 11 != 0,                   # 호랑이 줄무늬
    "zhuque": lambda x, y: y % 4 == 0 and (x + y // 4) % 3 != 0,                                  # 깃털 층
    "xuanwu": lambda x, y: x % 5 == 0 or (y + (x // 5) % 2 * 3) % 6 == 0,                         # 등껍질 육각
    "fiend": lambda x, y: (x * 7 + y * 3) % 13 == 0 or (x - y) % 9 == 0,                          # 균열
}

ARMOR_SETS = {
    "qinglong": palette("2e6bd9", guard="ffd23f", rim="ffd23f", accent="ff4040", glow="8fe8ff", extra="7fc8ff", outline="0a1a3a"),
    "baihu": palette("ececec", guard="bfc6ce", rim="9aa3ad", accent="3fa9ff", glow="9fe0ff", extra="1c1c1c", outline="202028"),
    "zhuque": palette("d9352e", guard="ffc93c", rim="ffc93c", accent="ffe066", glow="ffe9a0", extra="ff8a1f", outline="3a0808"),
    "xuanwu": palette("2e8c4a", guard="8a6a3a", rim="c9a13b", accent="ff3030", glow="a0ffcf", extra="6b4a22", outline="0a1a10"),
    "fiend": palette("241a2a", guard="7a1030", rim="b01030", accent="d070ff", glow="e080ff", extra="5a1a3a", outline="050308"),
}


def armor(set_id, slot):
    pal = ARMOR_SETS[set_id]
    cv = Canvas(pal)
    area = _shape(cv, slot)
    pat = PATTERNS[set_id]
    for (x, y) in area:
        if pat(x, y):
            cv.set(x, y, "x")
    _dome(cv, area)
    _rim(cv, area)
    if slot == 0:   # 투구 디테일
        cv.fill_xy(lambda x, y: 11 <= x <= 21 and 12.5 <= y <= 14, "o")                       # 눈 틈
        if set_id == "fiend":
            gem_xy(cv, 13, 13, 1.1, "c"); gem_xy(cv, 19, 13, 1.1, "c")
        if set_id == "qinglong":
            poly_xy(cv, [(9, 7), (4, 1), (6, 7)], "g"); poly_xy(cv, [(23, 7), (28, 1), (26, 7)], "g")
        elif set_id == "baihu":
            poly_xy(cv, [(9, 6), (8, 1), (12, 4)], "b"); poly_xy(cv, [(23, 6), (24, 1), (20, 4)], "b")
        elif set_id == "zhuque":
            poly_xy(cv, [(15, 3), (13, 0), (17, 1), (20, 0), (18, 4)], "E")
        elif set_id == "xuanwu":
            gem_xy(cv, 16, 7, 1.6, "e")
        else:
            poly_xy(cv, [(9, 7), (3, 3), (6, 1), (10, 5)], "r"); poly_xy(cv, [(23, 7), (29, 3), (26, 1), (22, 5)], "r")
    elif slot == 1:  # 갑옷 디테일
        if set_id == "fiend":   # 도철의 입
            cv.fill_xy(lambda x, y: 11 <= x <= 21 and 17 <= y <= 21, "o")
            for x in range(11, 22, 2):
                cv.set(x, 17, "h"); cv.set(x + 1, 21, "h")
            gem_xy(cv, 16, 11, 1.6, "c")
        else:
            gem_xy(cv, 16, 13, 2.2, "e" if set_id != "baihu" else "c")
            cv.fill_xy(lambda x, y: 9 <= x <= 23 and 22 <= y <= 23, "g")
    elif slot == 2:
        cv.fill_xy(lambda x, y: 8 <= x <= 24 and 4 <= y <= 6, "g")
        gem_xy(cv, 16, 5, 1.2, "c")
    else:
        for (x, y) in list(area):
            if 10 <= y <= 12:
                cv.set(x, y, "g")
    cv.outline()
    if set_id != "fiend":
        cv.sparkle(28, 3, 1)
    return cv.render()


# ================================================================ 기운 뽑기 결정
CRYSTAL_COL = {"fire": ("ff5a1f", "ffe066"), "wind": ("5ab8ff", "ffffff"), "dark": ("5a2a8a", "e080ff"),
               "nature": ("3fcf6f", "e8ffe0"), "earth": ("c9a13b", "fff0b0")}


def crystal(el):
    base, glow = CRYSTAL_COL[el]
    cv = Canvas(palette(base, guard="5a5a66", accent=glow, glow=glow))
    area = set()
    for pts in ([(14, 28), (12, 12), (16, 2), (20, 12), (18, 28)], [(8, 28), (5, 18), (7, 11), (11, 18), (12, 28)],
                [(20, 28), (21, 17), (25, 10), (27, 18), (24, 28)]):
        area |= set(poly_xy(cv, pts, "b"))
    for (x, y) in area:
        cv.set(x, y, "h" if (x + y) % 7 == 0 or x in (15, 8, 23) else ("s" if x in (17, 11, 25) else "b"))
        cv.light[(x, y)] = 0.8 + 0.35 * (1 - y / 30)
    cv.fill_xy(lambda x, y: 4 <= x <= 28 and 27 <= y <= 30, "g")
    gem_xy(cv, 16, 16, 2.0, "c")
    cv.outline()
    for (x, y) in [(4, 6), (27, 4), (29, 13)]:
        cv.sparkle(x, y, 1)
    return cv.render()


# ================================================================ 확장 장비: 창 · 지팡이 · 주문서
SPEAR_PAL = [palette("9aa0a8", guard="6b6b6b", grip="8a6a3a"), palette("d8dee6", guard="9aa3ad", grip="5a3a22", accent="3f7fff"),
             palette("ffd23f", guard="c9a13b", grip="5a2a10", accent="e04848"), palette("7fe8f0", guard="3fa9ff", grip="2a2a4a", accent="ffffff", glow="c8ffff"),
             palette("fff0c0", guard="ffd23f", grip="3a1a0a", accent="ff4040", glow="fffbe0")]


def spear(tier, stage=0, pal=None):
    cv = Canvas(pal or SPEAR_PAL[min(tier, len(SPEAR_PAL) - 1)], stage)
    tier = min(tier, 3) if pal is None else tier % 10
    if tier >= 4:   # v5.2.0 창 머리 6종 (무기고 III)
        _spear_head(cv, tier)
        return finish(cv)
    shaft(cv, 0.5, 25.0, 0.85, wrap_to=6 + tier * 2)
    if tier == 0:
        blade(cv, 25.4, 34.5, lambda u: 1.4 * (1 - u) + 0.3, tip=0.2, runes=(0.1, 0.8))
    elif tier == 1:
        blade(cv, 25.2, 35.2, lambda u: 1.8 * math.sin(max(0.0, u) * math.pi * 0.8 + 0.4), tip=0.3, runes=(0.1, 0.8))
        line_st(cv, 24.6, -1.8, 24.6, 1.8, 0.6, "g")
    elif tier == 2:
        blade(cv, 25.0, 35.6, lambda u: 2.0 * (1 - u) + 0.3, tip=0.25, runes=(0.1, 0.8))
        poly_st(cv, [(25.4, -1.0), (23.0, -3.2), (26.0, -1.6)], "g")
        poly_st(cv, [(25.4, 1.0), (23.0, 3.2), (26.0, 1.6)], "G")
        gem_st(cv, 24.4, 0, 1.0)
    else:
        blade(cv, 24.8, 36.0, lambda u: 2.2 * math.sin(max(0.0, u) * math.pi * 0.85 + 0.3), tip=0.3, runes=(0.05, 0.85))
        cv.fill_st(lambda s, t: 27 <= s <= 33 and abs(t) < 0.45, "c")
        for sg in (-1, 1):
            line_st(cv, 24.2, sg * 1.0, 21.5, sg * 3.6, 0.55, "g")
        gem_st(cv, 24.0, 0, 1.1, "c")
    return finish(cv)


STAFF_PAL = [palette("8a5a2b", guard="c9a13b", grip="6b4a2b", accent="7fd0ff", glow="bff4ff"),
             palette("5a3a8a", guard="c9d6ff", grip="3a2a4a", accent="d070ff", glow="f0c0ff"),
             palette("1a2a4a", guard="ffd46b", grip="2a2a4a", accent="fff0a0", glow="ffffff")]


def staff(tier, stage=0, pal=None):
    cv = Canvas(pal or STAFF_PAL[min(tier, len(STAFF_PAL) - 1)], stage)
    tier = tier % 9
    if tier >= 3:   # v5.2.0 지팡이 머리 6종 (무기고 III)
        _staff_head(cv, tier)
        return finish(cv)
    shaft(cv, 0.5, 27.5, 0.8, wrap_to=5)
    for k in range(3):
        cv.fill_st(lambda s, t, k=k: abs(s - (8 + k * 6)) < 0.6 and abs(t) < 1.2, "g")
    if tier == 0:
        disc_st(cv, 29.5, 0, 2.4, "e"); gem_st(cv, 29.5, 0, 1.6, "e")
    elif tier == 1:
        for sg in (-1, 1):
            line_st(cv, 27.0, sg * 0.8, 31.5, sg * 3.0, 0.5, "g")
        gem_st(cv, 30.2, 0, 2.2, "e")
    else:
        cv.fill_st(lambda s, t: 2.4 ** 2 <= (s - 30.5) ** 2 + t * t <= 3.4 ** 2, lambda s, t: "g" if t < 0 else "G")
        gem_st(cv, 30.5, 0, 1.7, "c")
        for a in range(0, 360, 90):
            r = math.radians(a + 45)
            disc_st(cv, 30.5 + math.cos(r) * 4.4, math.sin(r) * 4.4, 0.6, "C")
    img = finish(cv)
    return img


def _spear_head(cv, tier):
    """4 미늘창 · 5 언월도 · 6 삼지창 · 7 날개 창 · 8 물결 창 · 9 방천화극"""
    if tier == 4:
        shaft(cv, 0.5, 27.0, 0.85, wrap_to=8)
        blade(cv, 26.5, 35.0, lambda u: 1.1 * (1 - u) + 0.3, tip=0.2, runes=(0.1, 0.8))
        poly_st(cv, [(21.5, 0.6), (20.0, 5.4), (23.0, 6.2), (26.2, 5.0), (25.8, 0.6)], "b")    # 도끼날
        cv.fill_st(lambda s_, t: 20.3 <= s_ <= 25.9 and 4.6 <= t <= 6.0, "h")
        poly_st(cv, [(23.0, -0.6), (22.4, -3.6), (24.6, -0.6)], "g")                          # 갈고리
        gem_st(cv, 23.4, 2.4, 0.9)
    elif tier == 5:
        shaft(cv, 0.5, 24.0, 0.85, wrap_to=7)
        blade(cv, 23.6, 35.4, lambda u: 0.6 + 0.3 * u, lambda u: 2.4 * math.sin(min(1.0, u * 1.1) * math.pi * 0.9) + 0.4,
              c=lambda u: -0.9 * u * u, tip=0.35, runes=(0.1, 0.7))
        disc_st(cv, 23.4, 0, 1.4, "g"); gem_st(cv, 23.4, 0, 0.8)
    elif tier == 6:
        shaft(cv, 0.5, 25.0, 0.85, wrap_to=8)
        line_st(cv, 25.0, -3.4, 25.0, 3.4, 0.7, "g")                                          # 가로대
        for t0 in (-3.0, 0.0, 3.0):
            line_st(cv, 25.0, t0, 33.8 - abs(t0) * 0.6, t0 * 1.1, 0.6, "b")
            poly_st(cv, [(33.4 - abs(t0) * 0.6, t0 * 1.1 - 0.9), (35.4 - abs(t0) * 0.6, t0 * 1.1), (33.4 - abs(t0) * 0.6, t0 * 1.1 + 0.9)], "h")
        gem_st(cv, 25.0, 0, 1.0, "c")
    elif tier == 7:
        shaft(cv, 0.5, 26.0, 0.85, wrap_to=9)
        blade(cv, 25.8, 35.6, lambda u: 1.6 * (1 - u) + 0.3, tip=0.2, runes=(0.1, 0.85))
        for sg in (-1, 1):                                                                    # 펼친 날개
            poly_st(cv, [(25.6, sg * 0.8), (21.0, sg * 5.6), (22.6, sg * 3.6), (19.6, sg * 4.2), (23.8, sg * 1.2)], "g" if sg < 0 else "G")
        gem_st(cv, 25.0, 0, 1.2, "c")
    elif tier == 8:
        shaft(cv, 0.5, 24.6, 0.85, wrap_to=7)
        blade(cv, 24.4, 35.6, lambda u: 1.2 * (1 - u * 0.6) + 0.25, c=lambda u: 0.8 * math.sin(u * math.pi * 3), tip=0.25, runes=(0.1, 0.8))
        disc_st(cv, 24.2, 0, 1.5, "g"); gem_st(cv, 24.2, 0, 0.9, "e")
    else:
        shaft(cv, 0.5, 26.0, 0.85, wrap_to=10)
        blade(cv, 25.4, 36.0, lambda u: 1.3 * (1 - u) + 0.3, tip=0.2, runes=(0.05, 0.85))
        for sg in (-1, 1):                                                                    # 양쪽 초승달 날
            cv.fill_st(lambda s_, t, sg=sg: sg * t > 0.6 and 2.6 ** 2 <= (s_ - 25.0) ** 2 + (t - sg * 1.2) ** 2 <= 3.8 ** 2 and s_ > 23.0, "b")
        cv.fill_st(lambda s_, t: s_ > 23.0 and 3.5 ** 2 <= (s_ - 25.0) ** 2 + (abs(t) - 1.2) ** 2 <= 3.8 ** 2 and abs(t) > 0.6, "h")
        gem_st(cv, 25.0, 0, 1.1, "c")


def _staff_head(cv, tier):
    """3 초승달 · 4 불꽃 · 5 세계수 가지 · 6 별 · 7 새장 속 구슬 · 8 결정 다발"""
    shaft(cv, 0.5, 26.5, 0.8, wrap_to=5)
    for k in range(3):
        cv.fill_st(lambda s, t, k=k: abs(s - (8 + k * 6)) < 0.6 and abs(t) < 1.2, "g")
    if tier == 3:
        cv.fill_st(lambda s, t: (s - 30.0) ** 2 + t * t <= 3.6 ** 2 and (s - 31.4) ** 2 + (t - 0.8) ** 2 > 2.8 ** 2, lambda s, t: "g" if t < 0 else "G")
        gem_st(cv, 31.2, 0.6, 1.0, "c")
    elif tier == 4:
        for (s0, t0, h, k) in ((27.0, 0, 7.5, "e"), (27.0, -1.6, 5.0, "E"), (27.0, 1.6, 5.4, "e"), (27.6, 0, 4.2, "c")):
            poly_st(cv, [(s0, t0 - 1.6), (s0 + h, t0 + 0.4), (s0 + h * 0.55, t0 + 0.2), (s0, t0 + 1.6)], k)
        disc_st(cv, 27.0, 0, 1.4, "g")
    elif tier == 5:
        for (a, L) in ((-35, 6.0), (0, 7.5), (35, 6.0), (-65, 4.0), (65, 4.0)):
            r = math.radians(a)
            line_st(cv, 26.0, 0, 26.0 + math.cos(r) * L, math.sin(r) * L, 0.55, "w")
            disc_st(cv, 26.0 + math.cos(r) * L, math.sin(r) * L, 1.1, "c")
        gem_st(cv, 26.2, 0, 1.2, "e")
    elif tier == 6:
        pts = []
        for k in range(10):
            r = (4.2 if k % 2 == 0 else 1.8)
            a = math.radians(k * 36)
            pts.append((30.0 + math.cos(a) * r, math.sin(a) * r))
        poly_st(cv, pts, "g")
        gem_st(cv, 30.0, 0, 1.2, "c")
    elif tier == 7:
        for sg in (-1, 1):
            cv.fill_st(lambda s, t, sg=sg: sg * t > 0 and 3.0 ** 2 <= (s - 30.0) ** 2 + t * t <= 3.8 ** 2, "g" if sg < 0 else "G")
        line_st(cv, 26.4, 0, 33.8, 0, 0.35, "g")
        gem_st(cv, 30.0, 0, 2.0, "e")
    else:
        for (s0, t0, r) in ((31.0, 0, 1.6), (28.8, -2.4, 1.2), (28.8, 2.4, 1.2), (33.4, -1.4, 0.9), (33.0, 1.8, 0.9)):
            poly_st(cv, [(s0 - r * 1.6, t0), (s0, t0 - r), (s0 + r * 1.8, t0), (s0, t0 + r)], "e")
            cv.fill_st(lambda s, t, s0=s0, t0=t0, r=r: abs(t - t0) < r * 0.3 and s0 - r < s < s0 + r, "E")
        disc_st(cv, 27.0, 0, 1.3, "g")


SCROLL_COL = {"atk": "d9352e", "def": "3f7fff", "speed": "2fbf71", "exp": "e0a020", "return": "9b59ff"}


def scroll(kind):
    pal = palette("efe2c0", guard="8a5a2b", grip="6b4a2b", accent=SCROLL_COL[kind], glow="ffffff", extra="d8c8a0")
    cv = Canvas(pal)
    poly_xy(cv, [(8, 6), (24, 6), (24, 26), (8, 26)], lambda x, y: "b" if y < 23 else "s")
    for yy in (5, 26):
        cv.fill_xy(lambda x, y, yy=yy: 6 <= x <= 26 and abs(y - yy) <= 1.7, "x")
        disc_xy(cv, 6, yy, 2, "w"); disc_xy(cv, 26, yy, 2, "w")
    for y in (10, 13, 16):
        cv.fill_xy(lambda x, yy, y=y: 11 <= x <= 21 and abs(yy - y) < 0.5, "s")
    gem_xy(cv, 16, 20, 2.6, "e")
    if kind == "return":
        cv.fill_xy(lambda x, y: 2 ** 2 <= (x - 16) ** 2 + (y - 20) ** 2 <= 3.2 ** 2, "c")
    cv.outline()
    return cv.render()



# ================================================================ 활
def bow(pal, style=0, stage=0):
    """대각선 활: 위왼쪽으로 휜 활대 · 시위 · 손잡이 · 끝 장식. style 0~3 으로 모양 변화"""
    cv = Canvas(pal, stage)
    ax = (1 / math.sqrt(2), -1 / math.sqrt(2))
    nm = (-1 / math.sqrt(2), -1 / math.sqrt(2))
    mid = (16.0, 16.0)
    bend = [4.2, 5.2, 3.6, 6.0][style % 4]
    half = [11.5, 12.5, 12.0, 13.0][style % 4]
    pts = []
    for i in range(121):
        t = -1 + i / 60
        wob = 0.6 * math.sin(t * math.pi * 2) if style % 4 == 3 else 0
        pts.append((mid[0] + ax[0] * t * half + nm[0] * ((1 - t * t) * bend + wob), mid[1] + ax[1] * t * half + nm[1] * ((1 - t * t) * bend + wob), t))
    for (x, y, t) in pts:
        th = 1.5 - 0.6 * abs(t)
        for dx in range(-2, 3):
            for dy in range(-2, 3):
                px, py = int(round(x + dx * 0.5)), int(round(y + dy * 0.5))
                if math.hypot(px - x, py - y) <= th and 0 <= px < N and 0 <= py < N:
                    cv.set(px, py, "h" if (px - x) * nm[0] + (py - y) * nm[1] > 0.4 else "b")
    a, b = pts[0], pts[-1]
    cv.fill_xy(lambda x, y: abs((x - a[0]) * (b[1] - a[1]) - (y - a[1]) * (b[0] - a[0])) / math.hypot(b[0] - a[0], b[1] - a[1]) < 0.55
               and min(a[0], b[0]) - 0.5 <= x <= max(a[0], b[0]) + 0.5 and cv.get(x, y) is None, "x")
    gx, gy = mid[0] + nm[0] * bend, mid[1] + nm[1] * bend
    disc_xy(cv, gx, gy, 1.9, "w")
    for (tx, ty, _) in (pts[0], pts[-1]):
        disc_xy(cv, tx, ty, 1.3 if style < 2 else 1.7, "g")
    if style >= 1:
        gem_xy(cv, gx, gy, 1.2, "e")
    cv.outline()
    return cv.render()
