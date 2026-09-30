"""보스 복셀 조각 (v5.8.3) — voxel_lib 로 보스마다 새로 깎음. 태초의 용은 voxel_dragon.py"""
import math

from voxel_lib import (Grid, emit, figure, armor, robe, cape, hair, wings, crown, halo, staff, sword, hammer, spear,
                       quad, bow, axe, scythe, tentacle, floaters, catmull,
                       hx, mix, tex, rnd, add, sub, mul, lerp, norm, VS)


def _floaters(g, pts, col, core="ffffff", r=0.8, pri=6):
    for p in pts:
        g.sphere(p, r, lambda x, y, z, d: core if d < 0.25 else col, pri)


# =================================================================== 오염된 마녀
def witch(g):
    SKIN, ROBE, ROBE2, TOX, TOX2, WOOD, HAIR = "8fbf6a", "3a1f52", "2a1640", "7dff6a", "b8ff3a", "5a3a1a", "22202a"
    B = figure(g, s=1.0, w=0.95, skin=SKIN, top=ROBE, legs=ROBE2, robe=True, eye=TOX2)
    robe(g, B, ROBE, ROBE2, "4a2a66", flare=5.8, hem_ragged=True)
    g.ellipsoid((8, 18.6, 8), (4.6, 1.5, 3.0), lambda x, y, z, d: "2a1640" if d > 0.5 else None, 4)          # 어깨 숄
    for k in range(9):                                                                                        # 숄 누더기
        a = -math.pi * 0.9 + k * math.pi * 1.8 / 8
        p = (8 + math.cos(a) * 4.4, 17.4, 8 + math.sin(a) * 2.9)
        g.cone(p, add(p, (0, -1.8 - (k % 3) * 0.6, 0)), 0.45, 0.15, "2a1640", 4)
    hair(g, B, HAIR, length=10, width=2.6)
    g.tube([(8, 22.8, 10.6), (8, 22.3, 12.0), (8, 21.4, 12.6)], [0.5, 0.42, 0.25], "6f9f4a", 6, smooth=True)   # 매부리코
    g.dot(8.6, 22.0, 11.9, "4a7a3a", 7)                                                                       # 사마귀
    # 모자: 넓은 챙 + 휘어진 원뿔 + 독 띠 + 버클
    g.ellipsoid((8, 25.3, 8.3), (6.6, 0.45, 6.2), lambda x, y, z, d: "241232" if d < 0.85 else "1a0c26", 6)
    g.tube([(8, 25.4, 8.3), (8, 29.0, 8.0), (8.3, 32.0, 7.2), (9.4, 33.6, 5.8), (11.0, 33.4, 4.8)], [3.0, 2.2, 1.3, 0.7, 0.3],
           lambda x, y, z, d, f: tex("2a1640", x, y, z), 6, smooth=True)
    g.ellipsoid((8, 26.2, 8.3), (3.15, 0.55, 3.15), lambda x, y, z, d: TOX if d > 0.6 else None, 7, inner=0.5)
    g.box((9.8, 25.7, 10.9), (11.0, 26.8, 11.6), "ffd23f", 8)
    # 독 가마솥 지팡이 (오른손)
    top = staff(g, B.hand_r, 30.0, WOOD, "3a3a3a", pri=5)
    g.ellipsoid(add(top, (0, 1.2, 0)), (1.8, 1.3, 1.8), lambda x, y, z, d: "3a3a3a" if d > 0.45 else None, 6, inner=0.4)
    g.ellipsoid(add(top, (0, 1.9, 0)), (1.4, 0.4, 1.4), TOX, 7)
    for k in range(5):
        g.sphere(add(top, (math.cos(k) * 0.8, 2.6 + k * 0.7, math.sin(k) * 0.8)), 0.35 + 0.1 * (k % 2), TOX2, 7)   # 독 거품
    # 떠도는 독 해골
    for (x, y, z) in [(-5.5, 19, 5), (21, 23, 10), (18.5, 12, 1)]:
        g.ellipsoid((x, y, z), (1.2, 1.2, 1.1), "e8e0d0", 6)
        g.box((x - 0.7, y - 1.3, z - 0.3), (x + 0.7, y - 0.6, z + 1.0), "d8d0c0", 6)
        for sx in (-1, 1):
            g.dot(x + sx * 0.45, y + 0.1, z + 1.0, TOX, 8)


# =================================================================== 드워프 왕
def dwarf_king(g):
    SKIN, ARM, ARM2, GOLD, BEARD, RUNE, RED = "e0a878", "8a8f96", "5a5f66", "e0b030", "e06a2a", "3f7fff", "8a1a1a"
    B = figure(g, s=0.82, w=1.45, bulk=1.08, skin=SKIN, top=ARM, legs="5a3a22", boots=ARM2, arms=ARM)
    armor(g, B, ARM, GOLD, gem=RUNE, spikes=0)
    cape(g, B, RED, GOLD, length=0.9, width=5.2, emblem=GOLD)
    # 수염: 풍성한 덩어리 + 땋은 두 갈래 + 금 고리
    hc = B.head
    g.ellipsoid((8, hc[1] - 3.0, hc[2] + 1.9), (2.9, 3.6, 1.6), lambda x, y, z, d: tex(BEARD, x, y, z, 0.15), 7)
    g.ellipsoid((8, hc[1] - 0.6, hc[2] + 2.3), (2.4, 0.6, 0.6), hx(BEARD, 0.85), 8)   # 콧수염
    for sx in (-1, 1):
        p0 = (8 + sx * 1.3, hc[1] - 5.4, hc[2] + 2.3)
        g.tube([p0, add(p0, (sx * 0.2, -2.6, 0.2)), add(p0, (sx * 0.1, -4.6, 0.0))], [0.8, 0.65, 0.45], lambda x, y, z, d, f: "c0501a" if int(f * 4) % 2 else BEARD, 7, smooth=True)
        g.ellipsoid(add(p0, (sx * 0.15, -2.2, 0.2)), (0.95, 0.35, 0.95), GOLD, 8)
    g.ellipsoid((8, hc[1] + 1.2, hc[2] - 0.2), (B.head_r[0] + 0.3, 1.4, B.head_r[2] + 0.3), lambda x, y, z, d: tex(hx(BEARD, 0.9), x, y, z) if z < hc[2] + 1 else None, 7, inner=0.55)   # 머리칼
    # 룬 왕관
    crown(g, (8, hc[1] + 2.0, hc[2]), 2.3, GOLD, gem=RUNE, spikes=8, height=2.0)
    # 룬 망치 (오른손) · 모루 방패 (왼손)
    top = hammer(g, B.hand_r, 16, 3.2, "6b4a2b", ARM, GOLD)
    for dz in (-1.9, 1.9):
        g.box((top[0] - 2.35, top[1] - 0.4, top[2] + dz - 0.2), (top[0] + 2.35, top[1] + 0.4, top[2] + dz + 0.2), RUNE, 8)
    hl = B.hand_l
    g.slab(add(hl, (-1.2, 4.2, 0)), add(hl, (-1.2, -4.6, 0.4)), (0, 0, 6.2), 1.0, lambda x, y, z, u, v: GOLD if abs(v) > 0.85 or u < 0.06 or u > 0.94 else tex(ARM2, x, y, z), 6)
    g.ellipsoid(add(hl, (-1.9, 0, 0.2)), (0.4, 1.4, 1.4), lambda x, y, z, d: RUNE if d < 0.4 else GOLD, 7)


# =================================================================== 카인 (심연의 흑기사왕)
def kain(g):
    DARK, STEEL, EDGE, RED, GLOW, CAPE = "15151c", "24242e", "3a3a48", "8a0a1e", "ff2a3a", "3a0610"
    B = figure(g, s=1.05, w=1.12, bulk=1.05, skin=DARK, top=DARK, legs=STEEL, boots=STEEL, arms=STEEL, hands=DARK, head=False)
    armor(g, B, STEEL, EDGE, gem=GLOW, spikes=3)
    cape(g, B, CAPE, RED, length=0.6, width=4.8, ragged=True)
    wings(g, B, "1a0f1e", "2a0a16", rim=RED, span=17, rise=10, kind="bat")
    hc = B.head
    # 뿔 투구: 둥근 투구 · 붉은 눈 틈 · 턱 가리개 · 큰 뿔 두 쌍 · 가시 볏
    g.ellipsoid(hc, (2.8, 3.0, 2.9), lambda x, y, z, d: tex(DARK, x, y, z), 6)
    g.box((5.6, hc[1] + 0.1, hc[2] + 2.3), (10.4, hc[1] + 0.9, hc[2] + 3.2), GLOW, 8)
    g.ellipsoid((8, hc[1] - 1.4, hc[2] + 1.6), (2.2, 1.3, 1.6), lambda x, y, z, d: EDGE if d > 0.6 else STEEL, 7)
    g.box((7.6, hc[1] - 2.2, hc[2] + 2.6), (8.4, hc[1] + 2.9, hc[2] + 3.2), EDGE, 8)
    for sx in (-1, 1):
        g.tube([(8 + sx * 2.4, hc[1] + 1.0, hc[2]), (8 + sx * 4.4, hc[1] + 2.4, hc[2] - 0.6), (8 + sx * 5.2, hc[1] + 5.2, hc[2] - 1.4), (8 + sx * 4.6, hc[1] + 7.2, hc[2] - 2.4)],
               [0.8, 0.65, 0.45, 0.15], lambda x, y, z, d, f: "2a0a12" if f < 2.2 else RED, 7, smooth=True)
    for k in range(5):
        g.cone((8, hc[1] + 2.4, hc[2] + 1.2 - k * 0.9), (8, hc[1] + 4.2 - abs(k - 1) * 0.4, hc[2] + 0.8 - k * 1.1), 0.4, 0.1, RED if k == 1 else EDGE, 7)
    # 룬 대검 (오른손): 검은 칼날 · 붉은 룬 줄
    sword(g, B.hand_r, 17, "2c2c38", GLOW, DARK, width=2.0, tilt=(0.1, 1, 0.3))
    for (x, y, z) in [(-6, 20, 3), (22, 24, 12), (-4, 8, 14), (21, 6, 2)]:
        g.cone((x, y - 1.4, z), (x, y + 1.4, z), 0.6, 0.1, "5a1024", 6)


# =================================================================== 뇌신 토르반
def thunder_god(g):
    SKIN, GOLD, GOLD2, DARK, BOLT, CAPE, WHITE = "f0d0b0", "f2c23a", "c8962a", "5a4a2a", "6bd8ff", "1f5ab8", "f4f8ff"
    B = figure(g, s=1.12, w=1.22, bulk=1.05, skin=SKIN, top=GOLD, legs=DARK, boots=GOLD2, arms=GOLD)
    armor(g, B, GOLD, "9a6414", gem=BOLT, spikes=0)
    cape(g, B, CAPE, BOLT, length=0.8, width=4.6, emblem=WHITE)
    wings(g, B, "cfeeff", BOLT, rim=WHITE, span=15, rise=12, kind="feather")
    hc = B.head
    g.ellipsoid((8, hc[1] + 0.9, hc[2] - 0.1), (2.9, 2.3, 2.95), lambda x, y, z, d: tex(GOLD, x, y, z) if y > hc[1] + 0.2 or z < hc[2] else None, 6, inner=0.6)   # 투구
    g.box((7.5, hc[1] - 0.6, hc[2] + 2.3), (8.5, hc[1] + 2.4, hc[2] + 3.0), GOLD2, 7)   # 코 가리개
    for sx in (-1, 1):   # 투구 날개
        for k in range(3):
            base = (8 + sx * 2.6, hc[1] + 1.2 + k * 0.6, hc[2] - 0.2 - k * 0.5)
            g.slab(base, add(base, (sx * (3.2 - k * 0.6), 2.2 + k * 0.4, -1.0)), (0, 0, 0.9), 0.35, WHITE if k != 1 else "cfeeff", 7)
    for k in range(4):   # 번개 볏
        g.cone((8, hc[1] + 3.0, hc[2] + 0.6 - k * 0.9), (8, hc[1] + 5.4 - k * 0.3, hc[2] - k * 1.0), 0.4, 0.1, BOLT, 7)
    g.ellipsoid((8, hc[1] - 2.2, hc[2] + 2.0), (1.9, 1.6, 1.0), "e8e8f0", 7)   # 흰 수염
    halo(g, (8, hc[1] + 5.0, hc[2] - 1.0), 3.4, GOLD, WHITE)
    # 번개 창 (오른손): 지그재그 날
    top = spear(g, B.hand_r, 30, WHITE, BOLT)
    p = top
    for k in range(4):
        q = add(p, ((0.9 if k % 2 == 0 else -0.9), 1.2, 0.2))
        g.tube([p, q], [0.45, 0.35], BOLT, 7)
        p = q
    for (x, y, z) in [(-6, 16, 6), (22, 20, 10), (-4, 30, 12), (21, 8, 2)]:
        g.tube([(x, y - 2, z), (x + 0.8, y - 0.5, z), (x - 0.6, y + 0.5, z), (x + 0.4, y + 2, z)], [0.35, 0.3, 0.3, 0.15], BOLT, 6)


# =================================================================== 엘프 여왕
def elf_queen(g):
    SKIN, DRESS, DRESS2, LEAF, GOLD, HAIR, WOOD, GLOW = "f4dcc0", "2f8a4a", "1f6a38", "6ad06a", "f2d060", "f0d890", "7a5a2a", "5affa0"
    B = figure(g, s=1.05, w=0.9, skin=SKIN, top=DRESS, legs=DRESS2, arms=SKIN, robe=True, eye="2a8a4a", weapon_arm="left")
    robe(g, B, DRESS, DRESS2, GOLD, flare=6.2)
    for k in range(14):   # 드레스 자락의 잎
        a = k * 2 * math.pi / 14
        p = (8 + math.cos(a) * 5.4, 1.6 + (k % 3) * 1.8, 8 + math.sin(a) * 4.7)
        g.slab(p, add(p, (math.cos(a) * 1.4, -1.4, math.sin(a) * 1.4)), (math.sin(a) * 1.2, 0, -math.cos(a) * 1.2), 0.35, LEAF, 4)
    g.ellipsoid((8, 17.6, 8.3), (3.9, 2.4, 2.5), lambda x, y, z, d: GOLD if y > 19.2 else tex(DRESS, x, y, z), 4, inner=0.6)   # 코르셋
    for k in range(5):
        g.dot(8, 15.8 + k * 0.9, 10.8, GOLD, 6)
    hair(g, B, HAIR, length=16, width=2.8)
    hc = B.head
    for sx in (-1, 1):   # 뾰족한 귀
        g.cone((8 + sx * 2.3, hc[1] + 0.2, hc[2]), (8 + sx * 4.6, hc[1] + 2.0, hc[2] - 0.8), 0.6, 0.12, SKIN, 7)
    # 가지 왕관 (잎이 달린 휘어진 가지)
    for k in range(7):
        a = math.pi * (0.1 + k * 0.8 / 6)
        base = (8 + math.cos(a) * 2.4, hc[1] + 2.2, hc[2] + math.sin(a) * 1.2 - 0.3)
        tip = add(base, (math.cos(a) * 1.6, 3.2 - abs(k - 3) * 0.5, -0.4))
        g.tube([base, lerp(base, tip, 0.5), tip], [0.35, 0.3, 0.15], WOOD, 7, smooth=True)
        g.sphere(tip, 0.55, LEAF if k % 2 else GLOW, 8)
    g.ring((8, hc[1] + 2.1, hc[2]), 2.45, 0.4, GOLD, 7)
    g.sphere((8, hc[1] + 2.4, hc[2] + 2.3), 0.55, GLOW, 8)
    # 긴 활 (왼손) · 화살통 (등)
    bow(g, B.hand_l, 20, WOOD, "e8f0d8", bend=2.8)
    g.tube([(6.8, 11.5, B.back_z - 0.6), (10.6, 20.5, B.back_z - 1.2)], [1.1, 1.0], lambda x, y, z, d, f: "5a3a1a" if f < 0.92 else GOLD, 5)
    for k in range(5):
        b = (10.2 + k * 0.25, 20.6, B.back_z - 1.2 + (k - 2) * 0.35)
        g.tube([b, add(b, (0.5, 2.2, 0))], [0.14, 0.14], "d8c8a0", 6)
        g.slab(add(b, (0.4, 1.8, 0)), add(b, (0.6, 2.8, 0)), (0.6, 0, 0), 0.2, "ffffff", 7)
    g.tube([add(B.hand_r, (0, 0, 0)), add(B.hand_r, (-0.6, 1.0, 5.0))], [0.14, 0.14], "d8c8a0", 6)   # 오른손 화살
    g.cone(add(B.hand_r, (-0.6, 1.0, 5.0)), add(B.hand_r, (-0.7, 1.2, 6.2)), 0.35, 0.05, "c8ccd2", 7)
    wings(g, B, LEAF, "9af0a0", rim=GLOW, span=14, rise=9, feathers=5)
    halo(g, (8, hc[1] + 4.6, hc[2] - 1.6), 3.0, GOLD, GLOW)
    floaters(g, [(-4, 14, 6), (20, 18, 10), (-3, 26, 10), (20, 9, 3)], GLOW, r=0.6)


# =================================================================== 하피 여왕
def harpy_queen(g):
    SKIN, FEA, FEA2, FEA3, GOLD, TAL, EYE = "e8d0b8", "f4f4f8", "8ab8e8", "3a6ab8", "ffd23f", "3a3a3a", "6bd8ff"
    B = figure(g, s=1.0, w=0.92, skin=SKIN, top=FEA2, legs=FEA, boots="c89048", arms=FEA, reach=False, eye=EYE)
    # 새 다리: 깃털 허벅지 · 비늘 정강이 · 황금 발톱
    for sx in (-1, 1):
        g.ellipsoid((8 + sx * 2.0, 8.4, 8.0), (2.1, 3.0, 2.1), lambda x, y, z, d: tex(FEA if y > 7 else FEA2, x, y, z), 5)
        for t in range(3):
            a = (t - 1) * 0.6
            base = (8 + sx * 2.0, 0.8, 8.8)
            g.tube([base, add(base, (math.sin(a) * 2.2, -0.2, math.cos(a) * 2.2)), add(base, (math.sin(a) * 2.8, -0.7, math.cos(a) * 2.9))], [0.5, 0.35, 0.12], lambda x, y, z, d, f: GOLD if f > 1.2 else "c89048", 7, smooth=True)
    # 깃털 치마 (층)
    for row in range(3):
        for k in range(12):
            a = k * 2 * math.pi / 12 + row * 0.25
            p = (8 + math.cos(a) * (3.2 + row * 0.3), 12.2 - row * 1.3, 8 + math.sin(a) * 2.5)
            g.slab(p, add(p, (math.cos(a) * 0.8, -3.0, math.sin(a) * 0.8)), (-math.sin(a) * 1.3, 0, math.cos(a) * 1.3), 0.35,
                   lambda x, y, z, u, v, row=row: FEA3 if u > 0.8 else (FEA if row == 0 else FEA2), 3 + row)
    g.ellipsoid((8, 18.4, 8.2), (4.6, 1.6, 3.0), lambda x, y, z, d: tex(FEA, x, y, z), 5, inner=0.5)   # 깃털 깃
    hair(g, B, "3a5a9a", length=11, width=2.6)
    hc = B.head
    for k in range(9):   # 깃털 관 (부채꼴)
        a = math.pi * (0.15 + k * 0.7 / 8)
        base = (8 + math.cos(a) * 1.8, hc[1] + 2.2, hc[2] - 0.6)
        g.slab(base, add(base, (math.cos(a) * 2.4, 4.2 - abs(k - 4) * 0.5, -1.2)), (0, 0, 1.0), 0.3,
               lambda x, y, z, u, v, k=k: GOLD if u > 0.85 else (FEA3 if k % 2 else EYE), 7)
    g.ring((8, hc[1] + 2.0, hc[2]), 2.4, 0.4, GOLD, 7)
    for sx in (-1, 1):   # 팔 아래 깃털
        sh = B.shoulder_r if sx == 1 else B.shoulder_l
        hand = B.hand_r if sx == 1 else B.hand_l
        for k in range(4):
            b = lerp(sh, hand, 0.2 + k * 0.22)
            g.slab(b, add(b, (sx * 2.6, -1.8, -1.0)), (0, 0, 1.0), 0.3, FEA2 if k % 2 else FEA, 4)
        for t in range(3):
            g.cone(hand, add(hand, (sx * 0.4 + (t - 1) * 0.4, -1.6, 0.8)), 0.3, 0.08, TAL, 6)   # 손톱
    wings(g, B, FEA, FEA2, rim=FEA3, span=21, rise=13, feathers=8)
    halo(g, (8, hc[1] + 5.2, hc[2] - 1.8), 3.2, GOLD, EYE)
    for k in range(10):   # 폭풍 깃털
        a = k * 0.63
        p = (8 + math.cos(a) * 12, 6 + k * 2.4, 8 + math.sin(a) * 10)
        g.slab(p, add(p, (0.6, 1.8, 0.4)), (0.7, 0, 0), 0.25, FEA if k % 2 else EYE, 6)


# =================================================================== 심해수문장
def sea_gatekeeper(g):
    SKIN, SCALE, SCALE2, CORAL, PEARL, GOLD, TEAL, FIN = "5a9aa8", "2a7a8a", "1a5a6a", "ff6a7a", "f4f0ff", "e0c060", "3fc8c8", "7fe8ff"
    B = figure(g, s=1.1, w=1.18, bulk=1.05, skin=SKIN, top=SCALE, legs=SCALE2, boots=SCALE2, arms=SCALE, eye="ffffff")
    armor(g, B, TEAL, CORAL, gem=PEARL, spikes=0)
    for sx in (-1, 1):   # 조개 견갑 (부채 주름)
        sh = B.shoulder_r if sx == 1 else B.shoulder_l
        for k in range(7):
            a = (k - 3) * 0.28
            b = add(sh, (sx * 0.6, 1.6, 0))
            g.slab(b, add(b, (sx * 3.2 * math.cos(a), 1.2, 3.2 * math.sin(a))), (0, 0.5, 0), 0.4, CORAL if k % 2 else "ffb0b8", 7)
    cape(g, B, "1a4a6a", FIN, length=0.6, width=4.6, ragged=True)
    hc = B.head
    for sx in (-1, 1):   # 귀 지느러미
        for k in range(3):
            b = (8 + sx * 2.3, hc[1] - 0.6 + k * 0.8, hc[2] - 0.4)
            g.slab(b, add(b, (sx * 2.2, 0.8 + k * 0.3, -1.4)), (0, 0.6, 0), 0.3, FIN, 7)
    for k in range(5):   # 등 지느러미 볏
        b = (8, hc[1] + 2.4 - k * 0.4, hc[2] - k * 1.1)
        g.slab(b, add(b, (0, 2.6 - k * 0.3, -1.2)), (0, 0, 1.0), 0.3, FIN if k % 2 else TEAL, 7)
    # 산호 왕관 (갈라지는 가지)
    for k in range(6):
        a = k * 2 * math.pi / 6
        base = (8 + math.cos(a) * 2.3, hc[1] + 2.0, hc[2] + math.sin(a) * 2.3)
        mid = add(base, (math.cos(a) * 0.6, 1.8, math.sin(a) * 0.6))
        g.tube([base, mid], [0.4, 0.3], CORAL, 8)
        for t in (-1, 1):
            g.tube([mid, add(mid, (math.cos(a + t * 0.8) * 1.0, 1.4, math.sin(a + t * 0.8) * 1.0))], [0.3, 0.15], CORAL, 8)
    g.ring((8, hc[1] + 2.0, hc[2]), 2.4, 0.4, GOLD, 8)
    g.sphere((8, hc[1] + 2.2, hc[2] + 2.5), 0.6, PEARL, 9)
    # 삼지창 (오른손)
    top = spear(g, B.hand_r, 32, GOLD, "d8f8ff", prongs=3)
    g.sphere(add(top, (0, -0.8, 0)), 0.8, PEARL, 7)
    # 방패 조개 (왼손)
    hl = B.hand_l
    for k in range(9):
        a = (k - 4) * 0.2
        g.slab(add(hl, (-1.4, -3.4, 0)), add(hl, (-1.4 + math.sin(a) * 0.4, -3.4 + 7.2 * math.cos(a), 7.2 * math.sin(a))), (0, 0, 0.9), 0.5,
               lambda x, y, z, u, v, k=k: GOLD if u > 0.94 else ("ffb0b8" if k % 2 else CORAL), 6)
    floaters(g, [(-5, 14, 6), (21, 19, 10), (-4, 27, 11), (20, 8, 3), (-2, 6, 12)], FIN, r=0.7)   # 물방울


# =================================================================== 붕붕이 (불꽃 정령)
def bungbung(g):
    C = ["ffd84a", "ffa51f", "ff7a1f", "e8401a", "a8200e"]
    CORE, EYE = "fff4c0", "2a0a00"

    def fire(x, y, z, d, base=0.0):
        t = d + rnd(math.floor(x / VS), math.floor(y / VS), math.floor(z / VS)) * 0.25 + base
        return C[max(0, min(4, int(t * 3.2)))]
    # 몸: 아래는 가늘고 위로 부푼 불꽃 덩어리
    g.cone((8, 0.5, 8), (8, 8, 8), 1.2, 3.8, lambda x, y, z, f: fire(x, y, z, 0.6, 0.2), 3)
    g.ellipsoid((8, 13, 8), (6.2, 6.6, 5.6), lambda x, y, z, d: fire(x, y, z, d), 3)
    g.ellipsoid((8, 13, 9.5), (3.0, 3.2, 3.0), CORE, 2)
    g.ellipsoid((8, 21, 8.3), (5.0, 4.6, 4.6), lambda x, y, z, d: fire(x, y, z, d), 3)   # 머리
    # 불꽃 혀 (위로 솟는 뾰족한 불꽃 여러 갈래)
    for k in range(13):
        a = k * 2.399
        r = 1.2 + (k % 4) * 0.9
        base = (8 + math.cos(a) * r, 24.0, 8.3 + math.sin(a) * r * 0.9)
        h = 4.5 + (k % 5) * 1.6
        tip = add(base, (math.cos(a) * 1.4, h, math.sin(a) * 1.0 - 0.8))
        g.tube([base, lerp(base, tip, 0.5), tip], [1.4, 0.9, 0.12], lambda x, y, z, d, f: C[min(4, int(f * 1.6))], 4, smooth=True)
    # 성난 얼굴
    for sx in (-1, 1):
        g.ellipsoid((8 + sx * 1.8, 21.8, 12.4), (1.0, 1.3, 0.6), CORE, 7)
        g.ellipsoid((8 + sx * 1.8, 21.6, 12.8), (0.45, 0.7, 0.35), EYE, 8)
        g.slab((8 + sx * 0.6, 23.0, 12.6), (8 + sx * 3.0, 24.0, 12.3), (0, 0.5, 0), 0.4, "a8200e", 8)   # 눈썹
    g.box((6.2, 18.6, 12.2), (9.8, 19.6, 12.9), "5a1000", 7)
    for x in (6.5, 7.6, 8.7, 9.4):
        g.dot(x, 19.3, 12.8, CORE, 8)
    # 불꽃 팔: 굵게 뻗은 불꽃 주먹
    for sx in (-1, 1):
        pts = [(8 + sx * 5.2, 15.5, 8), (8 + sx * 8.6, 14.0, 9.5), (8 + sx * 10.2, 16.5, 11.5)]
        g.tube(pts, [2.0, 1.5, 1.8], lambda x, y, z, d, f: fire(x, y, z, d * 0.8), 4, smooth=True)
        g.sphere((8 + sx * 10.4, 17.2, 12.0), 2.1, lambda x, y, z, d: fire(x, y, z, d * 0.7), 5)
        for t in range(3):
            b = (8 + sx * (10.2 + (t - 1) * 0.9), 18.8, 12.0)
            g.cone(b, add(b, (sx * 0.4, 3.2, -0.4)), 0.8, 0.1, lambda x, y, z, f: C[min(4, int(f * 3))], 6)
    # 떠도는 불씨 고리
    for k in range(14):
        a = k * 2 * math.pi / 14
        p = (8 + math.cos(a) * 11.5, 11 + math.sin(a * 3) * 2.5, 8 + math.sin(a) * 10)
        g.sphere(p, 0.9 if k % 2 else 0.6, lambda x, y, z, d: "fff4c0" if d < 0.3 else C[1 + k % 3], 6)
    for k in range(6):   # 아래로 흐르는 불똥
        a = k * 1.05
        p = (8 + math.cos(a) * 3.0, 4.0 + (k % 3), 8 + math.sin(a) * 2.6)
        g.cone(p, add(p, (math.cos(a) * 1.8, -3.0, math.sin(a) * 1.8)), 0.7, 0.1, C[2 + k % 3], 3)


# =================================================================== 사막의 악몽 (파라오 미라)
def desert_nightmare(g):
    BAND, BAND2, GOLD, GOLD2, BLUE, SAND, EYE, SNAKE = "d8ccb0", "b0a488", "ffc83a", "c8902a", "2a4aa8", "c9a46a", "ffb030", "4a8a3a"
    B = figure(g, s=1.12, w=1.05, skin=BAND, top=BAND, legs=BAND2, boots=BAND2, arms=BAND, head=False)
    # 붕대 줄무늬: 몸통 둘레에 비스듬한 띠
    for k in range(18):
        y = 3 + k * 1.05
        g.ellipsoid((8, y, 8), (3.3 if y > 11 else 2.2, 0.3, 2.3), lambda x, y_, z, d, k=k: BAND2 if k % 2 else None, 3, inner=0.7)
    g.ellipsoid((8, 17.8, 8.3), (4.3, 2.8, 2.7), lambda x, y, z, d: GOLD if y > 18.9 else (BLUE if int(y * 1.3) % 2 else GOLD), 5, inner=0.62)   # 황금 목깃 (줄무늬)
    g.box((5.2, 9.4, 10.0), (10.8, 12.2, 10.6), lambda x, y, z: GOLD if int(x) % 2 else BLUE, 5)   # 허리 천
    g.slab((8, 11.0, 10.4), (8, 3.6, 10.9), (2.4, 0, 0), 0.4, lambda x, y, z, u, v: GOLD if abs(v) > 0.8 or u > 0.92 else BLUE, 5)
    hc = B.head
    # 황금 가면 얼굴
    g.ellipsoid(hc, (2.5, 2.8, 2.6), lambda x, y, z, d: tex(GOLD, x, y, z, 0.06), 6)
    for sx in (-1, 1):
        g.box((8 + sx * 0.5 - (1.4 if sx < 0 else 0), hc[1] + 0.1, hc[2] + 2.3), (8 + sx * 0.5 + (1.4 if sx > 0 else 0), hc[1] + 0.8, hc[2] + 2.9), "1a1a2a", 7)
        g.dot(8 + sx * 1.2, hc[1] + 0.45, hc[2] + 2.85, EYE, 9)
    g.box((7.5, hc[1] - 3.6, hc[2] + 1.8), (8.5, hc[1] - 1.6, hc[2] + 2.6), lambda x, y, z: GOLD if int(y * 2) % 2 else BLUE, 7)   # 수염 장식
    # 네메스 두건 (옆으로 늘어진 줄무늬 천)
    g.ellipsoid((8, hc[1] + 0.8, hc[2] - 0.2), (3.1, 2.6, 3.0), lambda x, y, z, d: (GOLD if int(y * 1.4) % 2 else BLUE) if z < hc[2] + 1.8 else None, 6, inner=0.6)
    for sx in (-1, 1):
        g.slab((8 + sx * 2.9, hc[1] + 0.5, hc[2] + 0.8), (8 + sx * 3.4, hc[1] - 5.4, hc[2] + 1.4), (0, 0, 2.4), 0.5,
               lambda x, y, z, u, v: GOLD if int(u * 8) % 2 else BLUE, 6)
    g.cone((8, hc[1] + 2.6, hc[2] + 2.0), (8, hc[1] + 4.2, hc[2] + 3.0), 0.55, 0.2, SNAKE, 8)   # 이마의 코브라
    g.sphere((8, hc[1] + 4.2, hc[2] + 3.1), 0.55, SNAKE, 8)
    # 숫양 뿔 (말려 들어가는 나선)
    for sx in (-1, 1):
        pts = []
        for t in range(9):
            a = t * 0.75
            r = 2.4 - t * 0.2
            pts.append((8 + sx * (3.0 + r * 0.6 + t * 0.12), hc[1] + 1.8 + math.sin(a) * r, hc[2] - 0.3 + math.cos(a) * r - 1.5))
        g.tube(pts, [0.9 - i * 0.08 for i in range(9)], lambda x, y, z, d, f: "e0d4b0" if int(f * 2) % 2 else "b8a888", 7, smooth=True)
    # 모래 망토 · 큰 낫 · 뱀
    cape(g, B, SAND, GOLD2, length=0.6, width=5.0, ragged=True, emblem=GOLD)
    scythe(g, B.hand_r, 26, "3a2a1a", "c8ccd2", GOLD, reach=10)
    body = [(8 - 5.0, 10, 9), (8 - 3.0, 12.5, 11), (8 + 1.5, 13.5, 11.2), (8 + 4.2, 15.5, 10.4), (8 + 3.0, 18.0, 11.4), (8 + 1.0, 19.4, 12.4)]
    g.tube(body, [0.6, 0.75, 0.8, 0.75, 0.6, 0.5], lambda x, y, z, d, f: SNAKE if int(f * 3) % 2 else "2a5a2a", 8, smooth=True)
    g.ellipsoid((8 + 0.6, 19.8, 13.2), (0.8, 0.6, 1.1), SNAKE, 9)
    g.dot(8 + 0.2, 20.1, 13.9, "ff3a3a", 10)
    floaters(g, [(-5, 16, 5), (21, 22, 10), (-4, 28, 11), (20, 8, 2)], "ffcf6a", r=0.9)
    for k in range(12):   # 발치의 모래 소용돌이
        a = k * 2 * math.pi / 12
        g.sphere((8 + math.cos(a) * 7.5, 0.6 + (k % 3) * 0.5, 8 + math.sin(a) * 6.5), 0.6, SAND if k % 2 else "e0c890", 3)


# =================================================================== 시포니아 (검의 마녀)
def siphonia(g):
    SKIN, DRESS, DRESS2, TRIM, HAIR, GLOW, STEEL, GOLD = "f4dccc", "2a6a4a", "1a4a34", "d8e8d0", "1a1a24", "5affa0", "d8e0e8", "f2d060"
    B = figure(g, s=1.05, w=0.9, skin=SKIN, top=DRESS, legs=DRESS2, arms=DRESS, robe=True, eye=GLOW)
    robe(g, B, DRESS, DRESS2, GOLD, flare=6.4)
    for row in range(3):   # 층진 드레스 자락
        y = 10.5 - row * 3.2
        g.ellipsoid((8, y, 8), (4.2 + row * 0.8, 1.2, 3.6 + row * 0.7), lambda x, y_, z, d, row=row: TRIM if d > 0.85 else (DRESS if row % 2 else DRESS2), 3, inner=0.55)
    g.ellipsoid((8, 17.6, 8.3), (3.9, 2.4, 2.5), lambda x, y, z, d: TRIM if y > 19.3 else tex(DRESS2, x, y, z), 4, inner=0.6)
    hair(g, B, HAIR, length=17, width=3.0)
    hc = B.head
    # 가시 왕관
    g.ring((8, hc[1] + 2.0, hc[2]), 2.4, 0.35, "2a2a2a", 7)
    for k in range(10):
        a = k * 2 * math.pi / 10
        base = (8 + math.cos(a) * 2.4, hc[1] + 2.0, hc[2] + math.sin(a) * 2.4)
        g.cone(base, add(base, (math.cos(a) * 0.7, 1.4 + (k % 2) * 1.2, math.sin(a) * 0.7)), 0.3, 0.06, "2a2a2a" if k % 2 else GLOW, 7)
    # 마법봉 (오른손)
    g.tube([B.hand_r, add(B.hand_r, (0, 4.6, 1.6))], [0.28, 0.24], GOLD, 6)
    g.sphere(add(B.hand_r, (0, 5.3, 1.9)), 0.8, lambda x, y, z, d: "ffffff" if d < 0.3 else GLOW, 7)
    # 떠도는 여섯 자루의 검 (등 뒤 부채꼴, 칼끝이 아래)
    for k in range(6):
        a = math.pi * (0.12 + k * 0.76 / 5)
        c = (8 + math.cos(a) * 11, 14 + math.sin(a) * 9, B.back_z - 3.0)
        d = norm((math.cos(a), math.sin(a), 0))
        hilt = add(c, mul(d, 3.8))
        tip = add(c, mul(d, -5.0))
        g.slab(hilt, tip, (-d[1] * 1.2, d[0] * 1.2, 0), 0.35, lambda x, y, z, u, v: GLOW if abs(v) < 0.2 else STEEL, 6)
        g.slab(add(hilt, mul(d, 0.2)), add(hilt, mul(d, 0.8)), (-d[1] * 3.2, d[0] * 3.2, 0), 0.5, GOLD, 7)
        g.tube([add(hilt, mul(d, 0.8)), add(hilt, mul(d, 2.4))], [0.3, 0.3], "3a2a2a", 7)
        g.sphere(add(hilt, mul(d, 2.6)), 0.45, GLOW, 8)
    wings(g, B, "d8fff0", GLOW, rim="ffffff", span=13, rise=8, feathers=5, kind="shard")
    halo(g, (8, hc[1] + 4.8, hc[2] - 1.8), 3.2, GOLD, GLOW)
    g.ring((8, 0.4, 8), 8.5, 0.35, GLOW, 2, axis="y")   # 발밑 룬 원


# =================================================================== 서리 여왕
def frost_queen(g):
    SKIN, DRESS, DRESS2, ICE, ICE2, HAIR, SILV, DEEP = "eef4ff", "9fd8ff", "6ab0e8", "7fe8ff", "e8fbff", "e8eef8", "c8d8f0", "3a6ab8"
    B = figure(g, s=1.08, w=0.9, skin=SKIN, top=DRESS, legs=DRESS2, arms=DRESS, robe=True, eye="3fb8ff")
    robe(g, B, DRESS, DRESS2, ICE2, flare=6.6)
    for k in range(16):   # 서리 꽃 자락 (얼음 가시)
        a = k * 2 * math.pi / 16
        p = (8 + math.cos(a) * 6.2, 0.8, 8 + math.sin(a) * 5.4)
        g.cone(p, add(p, (math.cos(a) * 1.8, 2.2 + (k % 3), math.sin(a) * 1.6)), 0.6, 0.1, ICE if k % 2 else ICE2, 4)
    g.ellipsoid((8, 17.6, 8.3), (3.9, 2.4, 2.5), lambda x, y, z, d: ICE2 if y > 19.3 else tex(DRESS, x, y, z), 4, inner=0.6)
    for k in range(5):   # 가슴 서리 문양
        g.dot(8 + (k - 2) * 0.7, 17.0 + abs(k - 2) * 0.6, 10.9, ICE2, 6)
    hair(g, B, HAIR, length=17, width=3.0)
    hc = B.head
    # 얼음 왕관 (길쭉한 결정)
    g.ring((8, hc[1] + 2.0, hc[2]), 2.4, 0.35, SILV, 7)
    for k in range(9):
        a = math.pi * (0.1 + k * 0.8 / 8)
        base = (8 + math.cos(a) * 2.3, hc[1] + 2.0, hc[2] + math.sin(a) * 1.8)
        h = 2.0 + (4 - abs(k - 4)) * 0.9
        g.cone(base, add(base, (math.cos(a) * 0.5, h, -0.3)), 0.45, 0.05, lambda x, y, z, f: ICE2 if f > 0.6 else ICE, 8)
    # 빙정 지팡이 (오른손)
    top = staff(g, B.hand_r, 32, SILV, ICE, pri=5)
    for k in range(6):
        a = k * math.pi / 3
        g.cone(add(top, (0, 1.2, 0)), add(top, (math.cos(a) * 1.8, 1.2 + math.sin(a) * 1.8, 0.3)), 0.4, 0.05, ICE2, 7)
    g.cone(add(top, (0, 0.2, 0)), add(top, (0, 4.4, 0.4)), 0.8, 0.1, ICE, 7)
    wings(g, B, ICE, ICE2, rim="ffffff", span=16, rise=11, feathers=7, kind="shard")
    for (x, y, z) in [(-5, 20, 5), (21, 24, 10), (-4, 10, 12), (20, 12, 2), (-2, 30, 8)]:   # 떠도는 고드름
        g.cone((x, y + 1.6, z), (x, y - 2.2, z), 0.7, 0.05, lambda x_, y_, z_, f: ICE2 if f < 0.3 else ICE, 6)
    g.ring((8, 0.4, 8), 8.5, 0.35, ICE2, 2, axis="y")


# =================================================================== 화산의 거인
def volcano_giant(g):
    ROCK, ROCK2, ROCK3, LAVA, HOT, CORE = "4a3a34", "2e2420", "6a5448", "ff6a1a", "ffb02a", "fff0a0"

    def rock(x, y, z, d, base=ROCK):
        # 바위 판 사이로 용암 균열 (좌표 격자 모양 균열선)
        cx_ = abs(math.sin(x * 0.9 + y * 0.35)) < 0.09 or abs(math.sin(z * 0.8 - y * 0.5)) < 0.07
        if cx_ and d > 0.7:
            return LAVA if rnd(x, y, z) > 0.3 else HOT
        r = rnd(math.floor(x / 1.5), math.floor(y / 1.5), math.floor(z / 1.5))
        return base if r < 0.45 else ROCK2 if r < 0.75 else ROCK3
    # 다리: 굵은 바위 기둥
    for sx in (-1, 1):
        g.tube([(8 + sx * 3.2, 11, 8), (8 + sx * 3.8, 5.5, 8.4), (8 + sx * 3.6, 1.0, 8.2)], [3.0, 2.7, 3.0], lambda x, y, z, d, f: rock(x, y, z, d), 3, smooth=True)
        g.box((8 + sx * 3.6 - 3.2, 0, 5.0), (8 + sx * 3.6 + 3.2, 1.6, 12.2), lambda x, y, z: rock(x, y, z, 1.0, ROCK2), 3)
    # 몸: 거대한 가슴 · 불룩한 배 · 용암 핵
    g.ellipsoid((8, 13.5, 8), (6.0, 4.6, 4.6), lambda x, y, z, d: rock(x, y, z, d), 3)
    g.ellipsoid((8, 20, 8.2), (8.4, 5.4, 5.6), lambda x, y, z, d: rock(x, y, z, d), 3)
    g.ellipsoid((8, 19, 13.2), (2.4, 2.4, 1.2), lambda x, y, z, d: CORE if d < 0.35 else HOT if d < 0.7 else LAVA, 6)
    for k in range(6):   # 핵에서 퍼지는 균열
        a = k * math.pi / 3 + 0.3
        g.tube([(8 + math.cos(a) * 2.2, 19 + math.sin(a) * 2.2, 13.2), (8 + math.cos(a) * 5.2, 19 + math.sin(a) * 4.0, 12.2)], [0.45, 0.3], LAVA, 5)
    # 어깨 분화구 (연기 구멍 + 용암)
    for sx in (-1, 1):
        sh = (8 + sx * 8.8, 23.5, 8)
        g.ellipsoid(sh, (3.8, 3.4, 3.8), lambda x, y, z, d: rock(x, y, z, d, ROCK3), 4)
        g.tube([add(sh, (0, 2.4, 0)), add(sh, (sx * 0.6, 5.2, -0.4))], [2.0, 1.2], lambda x, y, z, d, f: ROCK2, 4)
        g.ellipsoid(add(sh, (sx * 0.6, 5.2, -0.4)), (1.1, 0.4, 1.1), HOT, 6)
        g.sphere(add(sh, (sx * 1.2, 7.4, -0.8)), 0.8, "5a5050", 5)   # 연기
        g.sphere(add(sh, (sx * 1.8, 9.2, -1.2)), 1.1, "6a6262", 5)
        # 팔 · 거대한 바위 주먹
        el = (8 + sx * 11.2, 16.5, 9.5)
        fist = (8 + sx * 11.6, 10.0, 11.5)
        g.tube([sh, el], [3.0, 2.4], lambda x, y, z, d, f: rock(x, y, z, d), 3)
        g.tube([el, fist], [2.4, 2.6], lambda x, y, z, d, f: rock(x, y, z, d), 3)
        g.ellipsoid(fist, (3.6, 3.4, 3.6), lambda x, y, z, d: rock(x, y, z, d, ROCK3), 4)
        for t in range(4):
            g.sphere((fist[0] + (t - 1.5) * 1.5, fist[1] - 0.6, fist[2] + 3.2), 1.0, ROCK2, 5)   # 손가락 마디
        for t in range(3):   # 흘러내리는 용암 방울
            g.cone(add(fist, ((t - 1) * 1.3, -3.0, 0.8)), add(fist, ((t - 1) * 1.3, -5.2 - t, 0.8)), 0.6, 0.15, LAVA, 5)
    # 분화구 머리
    hc = (8, 28.2, 9.2)
    g.ellipsoid(hc, (4.0, 3.4, 3.6), lambda x, y, z, d: rock(x, y, z, d), 4)
    g.ellipsoid(add(hc, (0, 3.0, -0.3)), (3.2, 1.2, 3.0), lambda x, y, z, d: ROCK2 if d > 0.5 else None, 5, inner=0.3)
    g.ellipsoid(add(hc, (0, 2.9, -0.3)), (2.3, 0.6, 2.1), lambda x, y, z, d: CORE if d < 0.3 else HOT, 6)
    for k in range(7):   # 분화구에서 솟는 불기둥
        a = k * 0.9
        b = add(hc, (math.cos(a) * 1.2, 3.2, math.sin(a) * 1.0 - 0.3))
        g.cone(b, add(b, (math.cos(a) * 0.8, 2.5 + (k % 3) * 1.5, math.sin(a) * 0.6)), 0.7, 0.1, lambda x, y, z, f: HOT if f < 0.5 else LAVA, 6)
    g.box((5.4, hc[1] + 0.2, hc[2] + 3.0), (7.2, hc[1] + 1.2, hc[2] + 3.8), CORE, 8)   # 눈
    g.box((8.8, hc[1] + 0.2, hc[2] + 3.0), (10.6, hc[1] + 1.2, hc[2] + 3.8), CORE, 8)
    g.box((5.0, hc[1] + 1.2, hc[2] + 2.9), (11.0, hc[1] + 2.0, hc[2] + 3.9), ROCK2, 8)   # 성난 이마
    g.box((5.8, hc[1] - 2.2, hc[2] + 2.8), (10.2, hc[1] - 1.0, hc[2] + 3.6), LAVA, 8)   # 입
    for k in range(9):   # 등에 솟은 흑요석 가시
        a = (k - 4) * 0.33
        b = (8 + math.sin(a) * 6.5, 21 + math.cos(a) * 3.0, 3.4)
        g.cone(b, add(b, (math.sin(a) * 2.0, 3.0 + (k % 2) * 2.0, -2.2)), 1.0, 0.1, lambda x, y, z, f: "1a1418" if f < 0.7 else LAVA, 4)
    for k in range(10):   # 발밑 용암 웅덩이
        a = k * 2 * math.pi / 10
        g.ellipsoid((8 + math.cos(a) * 8, 0.3, 8 + math.sin(a) * 7), (1.6, 0.4, 1.2), LAVA if k % 2 else HOT, 2)


# =================================================================== 공허의 사도
def void_apostle(g):
    ROBE, ROBE2, TRIM, VOID, GLOW, DARK, STAR = "1a1030", "2a1a48", "8a60d8", "0a0612", "c060ff", "05030a", "e8d8ff"
    B = figure(g, s=1.15, w=1.0, skin=DARK, top=ROBE, legs=ROBE2, arms=ROBE, hands=DARK, robe=True, head=False, reach=False)
    robe(g, B, ROBE, ROBE2, TRIM, flare=7.2, hem_ragged=True)
    for k in range(24):   # 밑단이 공허로 흩어짐
        a = k * 2 * math.pi / 24
        p = (8 + math.cos(a) * 7.4, 0.3, 8 + math.sin(a) * 6.2)
        g.cone(p, add(p, (math.cos(a) * 1.6, -0.2 + (k % 3) * 0.6, math.sin(a) * 1.4)), 0.5, 0.1, GLOW if k % 4 == 0 else ROBE, 3)
    g.ellipsoid((8, 18.6 * 1.15, 8), (5.0, 1.8, 3.4), lambda x, y, z, d: ROBE2 if d > 0.5 else None, 4)   # 어깨 망토
    for k in range(7):   # 로브 앞 룬 띠
        g.box((7.4, 3 + k * 2.6, 13.3 - k * 0.15), (8.6, 4.4 + k * 2.6, 13.8 - k * 0.15), GLOW if k % 2 else TRIM, 5)
    hc = B.head
    # 깊은 두건 · 빈 얼굴 · 빛나는 눈
    g.ellipsoid(add(hc, (0, 0.6, -0.4)), (3.3, 3.6, 3.4), lambda x, y, z, d: tex(ROBE, x, y, z) if z < hc[2] + 2.4 else None, 6, inner=0.55)
    g.cone(add(hc, (0, 3.2, -1.2)), add(hc, (0, 6.0, -3.6)), 1.6, 0.2, ROBE, 6)
    g.ellipsoid(add(hc, (0, 0, 0.6)), (2.4, 2.8, 2.2), VOID, 5)
    for sx in (-1, 1):
        g.ellipsoid(add(hc, (sx * 1.0, 0.4, 2.6)), (0.5, 0.35, 0.3), "ffffff", 8)
        g.ellipsoid(add(hc, (sx * 1.0, 0.4, 2.3)), (0.9, 0.7, 0.5), GLOW, 7)
    # 손 사이의 공허 구
    for sx in (-1, 1):
        hand = B.hand_r if sx == 1 else B.hand_l
        g.tube([hand, add(hand, (-sx * 1.2, 1.6, 2.6))], [0.9, 0.6], ROBE2, 5)
    g.sphere((8, 13.4, 14.2), 2.2, lambda x, y, z, d: STAR if d < 0.1 else VOID if d < 0.6 else GLOW, 7)
    # 회전하는 공허 고리 셋 (기울어진 원)
    for k, (r, tilt) in enumerate(((9.5, 0.35), (11.5, -0.5), (13.0, 1.2))):
        g.ring((8, 17 + k * 1.5, 8), r, 0.35, lambda x, y, z, a, k=k: GLOW if int(a * 3) % 2 else TRIM, 5, axis="y", tilt=tilt)
    wings(g, B, "2a1a48", GLOW, rim=STAR, span=16, rise=12, feathers=7, kind="shard")
    for (x, y, z) in [(-6, 24, 6), (22, 28, 10), (-4, 10, 13), (21, 12, 3), (8, 36, 2)]:   # 떠도는 눈
        g.ellipsoid((x, y, z), (1.4, 1.0, 1.0), lambda x_, y_, z_, d: STAR if d < 0.9 else GLOW, 6)
        g.ellipsoid((x, y, z + 0.9), (0.55, 0.55, 0.3), VOID, 7)
    for k in range(10):   # 떠도는 별 조각
        a = k * 2.4
        g.dot(8 + math.cos(a) * 15, 6 + k * 3.2, 8 + math.sin(a) * 12, STAR, 6)


# =================================================================== 몬스터의 원혼
def vengeful_spirit(g):
    GHOST, GHOST2, GHOST3, BONE, GLOW, CHAIN, DARK = "c8e8f0", "8ab8c8", "4a7888", "e8e4d8", "7fe8ff", "6a7078", "1a2a30"
    # 다리 없는 거대한 몸: 아래로 갈수록 가늘어지며 흩어지는 누더기 자락 (4겹)
    for row in range(4):
        r0 = 6.0 - row * 0.6
        for k in range(18):
            a = k * 2 * math.pi / 18 + row * 0.17
            top = (8 + math.cos(a) * (r0 - 1.8), 16 - row * 1.6, 8 + math.sin(a) * (r0 - 2.2))
            L = 9 + (k * 7 % 5) + row * 1.4
            end = (8 + math.cos(a) * (r0 + 1.2), 16 - row * 1.6 - L, 8 + math.sin(a) * (r0 + 0.4))
            if end[1] < 0.3:
                end = (end[0], 0.3, end[2])
            g.slab(top, end, (-math.sin(a) * 2.6, 0, math.cos(a) * 2.6), 0.4,
                   lambda x, y, z, u, v, row=row: [GHOST, GHOST2, GHOST3, DARK][min(3, row + int(u * 2.2))] if not (u > 0.8 and rnd(x, y, z) < 0.4) else None, 3 + row)
    g.ellipsoid((8, 18, 8), (5.6, 5.4, 4.2), lambda x, y, z, d: tex(GHOST2, x, y, z), 3)
    # 드러난 갈비뼈 · 영혼 핵
    for k in range(4):
        g.ellipsoid((8, 16.4 + k * 1.4, 10.5), (4.0 - k * 0.2, 0.4, 1.8), lambda x, y, z, d: BONE if z > 10.9 else None, 6, inner=0.6)
    g.box((7.5, 15.5, 11.6), (8.5, 21.8, 12.2), BONE, 6)
    g.sphere((8, 18.2, 10.3), 1.6, lambda x, y, z, d: "ffffff" if d < 0.3 else GLOW, 5)
    # 두건 속 해골 (벌어진 턱)
    hc = (8, 26.0, 9.4)
    g.ellipsoid(add(hc, (0, 0.7, -0.6)), (4.0, 4.4, 4.0), lambda x, y, z, d: tex(GHOST3, x, y, z) if z < hc[2] + 2.6 else None, 6, inner=0.5)
    g.cone(add(hc, (0, 3.8, -2.0)), add(hc, (0, 6.4, -5.2)), 1.8, 0.2, GHOST3, 6)
    g.ellipsoid(hc, (2.6, 2.8, 2.6), BONE, 5)
    for sx in (-1, 1):
        g.ellipsoid(add(hc, (sx * 1.1, 0.4, 2.2)), (0.8, 0.9, 0.6), DARK, 7)
        g.dot(hc[0] + sx * 1.1, hc[1] + 0.4, hc[2] + 2.5, GLOW, 9)
    g.dot(8, hc[1] - 0.8, hc[2] + 2.6, DARK, 7)
    g.box((6.2, hc[1] - 4.6, hc[2] + 0.6), (9.8, hc[1] - 3.4, hc[2] + 2.6), BONE, 6)   # 벌어진 아래턱
    g.box((6.6, hc[1] - 3.4, hc[2] + 1.4), (9.4, hc[1] - 2.0, hc[2] + 2.4), DARK, 6)
    for x in (6.5, 7.4, 8.3, 9.2):
        g.dot(x, hc[1] - 1.9, hc[2] + 2.3, BONE, 8)
        g.dot(x, hc[1] - 3.3, hc[2] + 2.3, BONE, 8)
    # 긴 팔 · 뼈 발톱 · 끊어진 사슬
    for sx in (-1, 1):
        sh = (8 + sx * 5.4, 21.5, 8.5)
        el = (8 + sx * 10.0, 17.0, 11.0)
        wr = (8 + sx * 11.5, 13.0, 14.0)
        g.tube([sh, el, wr], [2.0, 1.4, 1.1], lambda x, y, z, d, f: GHOST2 if f < 1.2 else GHOST, 4, smooth=True)
        for k in range(3):   # 소매 누더기
            b = lerp(sh, el, 0.6 + k * 0.2)
            g.slab(b, add(b, (sx * 0.4, -4.5 - k, -0.6)), (0, 0, 1.8), 0.3, GHOST3, 4)
        for t in range(4):
            a = (t - 1.5) * 0.35
            g.tube([wr, add(wr, (sx * 0.4 + math.sin(a) * 1.4, -0.8, 2.0)), add(wr, (sx * 0.8 + math.sin(a) * 1.8, -2.8, 3.0))], [0.35, 0.3, 0.1], BONE, 6, smooth=True)
        g.ellipsoid(add(wr, (0, 0.6, -0.8)), (1.4, 0.7, 1.4), CHAIN, 6)   # 족쇄
        for k in range(7):
            p = add(wr, (sx * (0.5 + k * 0.4), -1.0 - k * 1.3, -1.2 - k * 0.4))
            g.ring(p, 0.5, 0.18, CHAIN, 6, axis="z" if k % 2 else "y")
    # 영혼 등불 · 떠도는 넋
    g.tube([(8, 31, 7.5), (8, 34, 7.5)], [0.2, 0.2], CHAIN, 5)
    for (x, y, z) in [(-6, 20, 6), (22, 24, 10), (-5, 8, 12), (21, 10, 3), (3, 32, 3), (14, 34, 4)]:
        g.sphere((x, y, z), 1.0, lambda x_, y_, z_, d: "ffffff" if d < 0.3 else GLOW, 6)
        g.cone((x, y - 0.4, z - 0.4), (x + 0.4, y - 3.0, z - 1.8), 0.7, 0.1, GHOST, 5)


# =================================================================== 발록 (불꽃의 대악마)
def balrog(g):
    SKIN, SKIN2, LAVA, HOT, HORN, EYE, FIRE, DARK = "3a1a14", "24100c", "ff4a1a", "ffb02a", "1a1414", "fff080", "ff7a1f", "120808"

    def hide(x, y, z, d, base=SKIN):
        if d > 0.72 and (abs(math.sin(x * 1.1 + y * 0.6)) < 0.08 or abs(math.sin(z * 1.3 + y * 0.9)) < 0.06):
            return LAVA
        return tex(base, x, y, z, 0.15)
    # 굽은 다리 · 발굽
    for sx in (-1, 1):
        pts = [(8 + sx * 3.0, 12.5, 7.5), (8 + sx * 3.8, 8.0, 10.0), (8 + sx * 3.8, 4.4, 6.4), (8 + sx * 3.8, 0.9, 8.2)]
        g.tube(pts, [2.6, 2.0, 1.4, 1.2], lambda x, y, z, d, f: hide(x, y, z, d), 3, smooth=True)
        g.ellipsoid((8 + sx * 3.8, 0.7, 8.8), (1.6, 0.8, 2.0), HORN, 4)
    # 근육질 상체 (역삼각형)
    g.ellipsoid((8, 13.5, 8), (4.2, 3.4, 3.2), lambda x, y, z, d: hide(x, y, z, d), 3)
    g.ellipsoid((8, 19.5, 8.6), (7.6, 5.2, 4.6), lambda x, y, z, d: hide(x, y, z, d), 3)
    for sx in (-1, 1):   # 가슴 근육 · 복근 균열
        g.ellipsoid((8 + sx * 2.6, 20.6, 12.2), (2.8, 2.2, 1.2), lambda x, y, z, d: hide(x, y, z, d, SKIN2), 4)
    for k in range(3):
        g.box((6.4, 13.0 + k * 1.6, 10.6), (9.6, 13.4 + k * 1.6, 11.4), LAVA, 5)
    g.box((7.8, 12.6, 10.8), (8.2, 17.6, 11.4), LAVA, 5)
    # 머리: 튀어나온 턱 · 불타는 눈과 입 · 굽은 뿔 · 불꽃 갈기
    hc = (8, 27.0, 10.2)
    g.ellipsoid(hc, (3.2, 3.2, 3.2), lambda x, y, z, d: hide(x, y, z, d), 5)
    g.ellipsoid(add(hc, (0, -1.8, 1.6)), (2.6, 1.6, 2.2), lambda x, y, z, d: hide(x, y, z, d, SKIN2), 5)
    for sx in (-1, 1):
        g.box((8 + sx * 1.3 - 0.8, hc[1] + 0.4, hc[2] + 2.8), (8 + sx * 1.3 + 0.8, hc[1] + 1.1, hc[2] + 3.4), EYE, 8)
        g.slab((8 + sx * 0.3, hc[1] + 1.6, hc[2] + 3.1), (8 + sx * 2.9, hc[1] + 1.1, hc[2] + 2.9), (0, 0.6, 0), 0.5, DARK, 8)
        pts = [(8 + sx * 2.6, hc[1] + 1.8, hc[2] - 0.4), (8 + sx * 5.6, hc[1] + 2.6, hc[2] - 1.6), (8 + sx * 7.4, hc[1] + 5.4, hc[2] - 1.0), (8 + sx * 6.6, hc[1] + 8.2, hc[2] + 1.0)]
        g.tube(pts, [1.2, 0.9, 0.55, 0.12], lambda x, y, z, d, f: HORN if f < 2.3 else "5a4a40", 6, smooth=True)
        for t in range(3):   # 엄니
            g.cone((8 + sx * (0.7 + t * 0.5), hc[1] - 2.6, hc[2] + 3.2), (8 + sx * (0.7 + t * 0.5), hc[1] - 1.2 + t * 0.2, hc[2] + 3.6), 0.3, 0.05, "f0e0c0", 8)
    g.box((6.2, hc[1] - 2.8, hc[2] + 3.4), (9.8, hc[1] - 1.8, hc[2] + 3.9), HOT, 7)
    for k in range(11):   # 불꽃 갈기
        a = math.pi * (0.1 + k * 0.8 / 10)
        b = (8 + math.cos(a) * 2.8, hc[1] + 1.8 + math.sin(a) * 1.2, hc[2] - 2.0)
        g.cone(b, add(b, (math.cos(a) * 2.0, 3.5 + (k % 3) * 1.5, -2.4)), 1.0, 0.1, lambda x, y, z, f: HOT if f < 0.3 else FIRE if f < 0.7 else LAVA, 6)
    # 팔: 오른손 불꽃 채찍 · 왼손 도끼
    for sx in (-1, 1):
        sh = (8 + sx * 8.0, 22.0, 8.4)
        el = (8 + sx * 10.8, 16.4, 10.6)
        wr = (8 + sx * 11.4, 12.4, 13.8)
        g.sphere(sh, 3.0, lambda x, y, z, d: hide(x, y, z, d, SKIN2), 4)
        g.tube([sh, el, wr], [2.6, 2.0, 1.8], lambda x, y, z, d, f: hide(x, y, z, d), 3, smooth=True)
        g.sphere(wr, 1.9, lambda x, y, z, d: hide(x, y, z, d, SKIN2), 4)
        for t in range(4):
            g.cone(add(sh, ((t - 1.5) * 1.0, 2.2, -0.6)), add(sh, (sx * 0.8 + (t - 1.5) * 1.2, 4.2, -1.6)), 0.5, 0.05, HORN, 5)
    whip = [(19.4, 12.4, 14.4), (22, 10, 18), (23, 5, 20), (20, 1.5, 21), (15, 1.0, 22), (10, 2.5, 24), (6, 1.0, 25)]
    g.tube(whip, [0.7, 0.6, 0.55, 0.45, 0.4, 0.3, 0.15], lambda x, y, z, d, f: HOT if d < 0.4 else FIRE if int(f * 3) % 2 else LAVA, 6, smooth=True)
    axe(g, (8 - 11.4, 12.4, 13.8), 18, "2a1a14", "3a3438", LAVA, size=3.6)
    # 박쥐 날개 · 꼬리
    B = type("B", (), {})()
    B.s, B.back_z = 1.25, 4.0
    wings(g, B, "2a1410", "4a1a10", rim=LAVA, span=22, rise=12, kind="bat")
    g.tube([(8, 12, 5), (8, 8, 1), (8, 4, -2), (10, 1.5, -5), (13, 1.2, -6)], [1.6, 1.2, 0.9, 0.6, 0.3], lambda x, y, z, d, f: hide(x, y, z, d), 3, smooth=True)
    g.cone((13, 1.2, -6), (15.2, 1.6, -6.8), 0.9, 0.1, FIRE, 4)
    for k in range(12):   # 몸에서 솟는 불티
        a = k * 2.1
        p = (8 + math.cos(a) * 13, 4 + k * 2.6, 8 + math.sin(a) * 11)
        g.cone(p, add(p, (0, 1.6, 0)), 0.5, 0.1, HOT if k % 2 else FIRE, 6)


# =================================================================== 메갈로돈
def megalodon(g):
    G, G2, G3, BELLY, T, E, SCAR, IRON, MOUTH = "5a6a7a", "3a4a5a", "6f8090", "e8eef4", "ffffff", "7fe8ff", "a8b4c0", "5a4a3a", "3a1a22"
    # 몸: 척추 곡선을 따라 굵기가 변하는 몸 (등은 짙고 배는 흼)
    spine = [(8, 8.0, -15), (8, 8.2, -9), (8, 8.6, -1), (8, 8.6, 8), (8, 8.4, 17), (8, 8.0, 24), (8, 7.6, 30)]
    radii = [1.6, 2.8, 4.4, 5.0, 4.6, 3.6, 2.0]
    P = catmull(spine, 6)
    for i in range(len(P)):
        f = i / (len(P) - 1) * (len(radii) - 1)
        a = min(int(f), len(radii) - 2)
        r = radii[a] + (radii[a + 1] - radii[a]) * (f - a)
        cy = P[i][1]
        g.ellipsoid(P[i], (r * 0.95, r, 1.2), lambda x, y, z, d, cy=cy, r=r: (BELLY if y < cy - r * 0.35 else G3 if y < cy + r * 0.1 else tex(G2 if y > cy + r * 0.55 else G, x, y, z, 0.06)), 3)
    # 벌린 입 (위턱 · 아래턱 · 톱니 이빨 두 줄)
    g.ellipsoid((8, 6.6, 26.5), (3.0, 1.6, 3.6), MOUTH, 5)
    g.ellipsoid((8, 4.2, 25.5), (3.2, 1.4, 3.8), lambda x, y, z, d: BELLY if y < 4.6 else G3, 5)
    for k in range(9):
        a = math.pi * (0.12 + k * 0.76 / 8)
        p = (8 + math.cos(a) * 2.8, 7.6, 26.2 + math.sin(a) * 3.4)
        g.cone(p, add(p, (0, -1.3, 0)), 0.35, 0.05, T, 7)
        q = (8 + math.cos(a) * 2.8, 5.2, 25.6 + math.sin(a) * 3.4)
        g.cone(q, add(q, (0, 1.2, 0)), 0.35, 0.05, T, 7)
    for sx in (-1, 1):
        g.ellipsoid((8 + sx * 3.2, 9.8, 24.5), (0.4, 0.7, 0.8), E, 7)   # 빛나는 눈
        for k in range(5):   # 아가미
            g.slab((8 + sx * 4.3, 6.2, 17.0 + k * 1.0), (8 + sx * 4.3, 10.4, 17.4 + k * 1.0), (0, 0, 0.3), 0.3, G2, 6)
    # 지느러미
    g.triangle((8, 13.0, 3), (8, 22.0, -0.8), (8, 13.0, 11), lambda p, w: G2 if w > 0.2 else G3, 4, scallop=0.12, thick=0.5)   # 높은 등지느러미
    g.triangle((8, 9.6, -10), (8, 12.6, -12.5), (8, 9.8, -7), G2, 4, thick=0.4)
    for sx in (-1, 1):
        g.triangle((8 + sx * 3.6, 5.6, 16), (8 + sx * 11.0, 1.6, 9), (8 + sx * 4.4, 5.0, 10.5), lambda p, w: G2 if w < 0.4 else G, 4, scallop=0.1, thick=0.5)   # 가슴지느러미
        g.triangle((8 + sx * 2.4, 5.4, -3), (8 + sx * 5.5, 3.4, -6.5), (8 + sx * 2.6, 5.4, -6), G2, 4, thick=0.4)
    # 초승달 꼬리
    g.triangle((8, 8.0, -15), (8, 21.0, -22), (8, 9.0, -18.5), G2, 4, scallop=0.1, thick=0.6)
    g.triangle((8, 8.0, -15), (8, -1.0, -20.5), (8, 7.0, -18.5), G2, 4, scallop=0.1, thick=0.6)
    # 흉터 · 박힌 작살
    for (x0, y0, z0, dz) in ((12.6, 8.4, 4, 5), (3.4, 9.0, 9, 4), (11.8, 10.4, 16, 3)):
        g.tube([(x0, y0, z0), (x0, y0 + 0.8, z0 + dz)], [0.2, 0.2], SCAR, 7)
    for (x, z, s) in ((10.0, 2.0, 1), (6.0, -4.0, -1)):
        b = (x, 12.4, z)
        e = (x + s * 2.4, 19.0, z - 1.0)
        g.tube([b, e], [0.3, 0.3], IRON, 6)
        g.cone(b, add(b, (0, -1.2, 0)), 0.6, 0.1, "c8ccd2", 6)
        g.tube([e, (e[0] + s * 1.6, e[1] - 2.0, e[2] - 3.5), (e[0] + s * 3.0, e[1] - 5.0, e[2] - 6.0)], [0.12, 0.12, 0.12], "d8c8a0", 6, smooth=True)


# =================================================================== 크라켄
def kraken(g):
    S, S2, SK, E, SU, RUNE, BAR = "7a1a4a", "5a0a34", "b04a7a", "ffd23f", "f0c0d8", "c060ff", "d8d0c0"
    # 거대한 외투막 (위로 길쭉한 주머니 + 끝의 지느러미)
    g.ellipsoid((8, 22, 7.5), (6.8, 10.0, 6.4), lambda x, y, z, d: tex(S2 if int(y / 1.8) % 3 == 0 and d > 0.8 else S, x, y, z, 0.08), 3)
    g.ellipsoid((8, 13.5, 8), (6.2, 4.4, 6.0), lambda x, y, z, d: tex(S, x, y, z, 0.08), 3)
    for sx in (-1, 1):
        g.triangle((8 + sx * 4.0, 30, 7.5), (8 + sx * 10.0, 32.5, 7.0), (8 + sx * 4.6, 25, 7.5), lambda p, w: S2 if w < 0.3 else SK, 4, scallop=0.1, thick=0.5)
    for k in range(9):   # 외투막 가시 볏
        y = 17 + k * 1.9
        g.cone((8, y, 1.5 + abs(k - 4) * 0.1), (8, y + 1.2, -1.2), 0.7, 0.1, lambda x, y_, z, f: RUNE if f > 0.7 else S2, 5)
    # 빛나는 두 눈 · 성난 눈썹 · 이마 룬
    for sx in (-1, 1):
        g.ellipsoid((8 + sx * 3.4, 15.8, 12.6), (1.9, 1.7, 1.2), E, 6)
        g.ellipsoid((8 + sx * 3.4, 15.8, 13.6), (0.5, 1.2, 0.4), "1a0a0a", 7)
        g.slab((8 + sx * 1.6, 18.2, 13.2), (8 + sx * 5.6, 17.4, 12.4), (0, 0.8, 0), 0.6, S2, 7)
    for k in range(3):
        g.ring((8, 21.5 + k * 2.4, 13.7 - k * 0.5), 0.9 + k * 0.3, 0.2, RUNE, 6, axis="z")
    g.ellipsoid((8, 10.8, 12.4), (1.3, 1.2, 1.2), "2a1a1a", 6)   # 부리
    # 8개의 굵은 촉수 (바깥으로 뻗다 끝이 말려 올라감)
    for k in range(8):
        a = k * math.pi / 4 + math.pi / 8
        dx, dz = math.cos(a), math.sin(a)
        pts = [(8 + dx * 3, 10, 8 + dz * 3), (8 + dx * 8, 5, 8 + dz * 8), (8 + dx * 13, 2.2, 8 + dz * 13), (8 + dx * 17, 3.5, 8 + dz * 17),
               (8 + dx * 18.5, 7, 8 + dz * 18.5), (8 + dx * 17, 9.5, 8 + dz * 17)]
        tentacle(g, pts, 2.3, 0.35, S if k % 2 else SK, S2, SU, 3)
    # 앞에서 치켜든 거대 촉수 두 개
    for sx in (-1, 1):
        pts = [(8 + sx * 4, 10, 11), (8 + sx * 9, 12, 15), (8 + sx * 11, 19, 17), (8 + sx * 10, 26, 16), (8 + sx * 7, 30, 14.5), (8 + sx * 5.5, 28.5, 14)]
        tentacle(g, pts, 2.0, 0.3, SK, S, SU, 4)
    for (x, y, z) in ((2.0, 26, 3.5), (14.2, 22, 5), (3.0, 14, 3), (13.6, 29, 6), (6, 31, 2.8)):   # 따개비
        g.sphere((x, y, z), 0.8, lambda x_, y_, z_, d: "8a8070" if d < 0.3 else BAR, 5)
    floaters(g, [(-6, 20, 8), (22, 24, 8), (-4, 30, 6), (20, 14, 2)], RUNE, r=0.7)


# =================================================================== 필드: 대지의 멧돼지왕
def field_boar_king(g):
    FUR, FUR2, MANE, TUSK, EYE, GOLD, MOSS, IRON, SNOUT = "6b4a32", "4e3524", "2e1d12", "f0e6c8", "ff5020", "ffd23f", "5a8a3a", "7a7f88", "8a6048"
    B = quad(g, FUR, "7a5a42", "2a2420", L=22, H=11, W=12, leg=5.5, hump=1.15, leg_r=2.0, fur2=FUR2)
    zf = B.front_z
    hc = (8, B.cy + 1.5, zf + 2.5)
    g.ellipsoid(hc, (4.4, 4.2, 4.4), lambda x, y, z, d: tex(FUR, x, y, z), 4)                 # 머리
    g.tube([add(hc, (0, -1.0, 2.8)), add(hc, (0, -1.8, 6.4))], [2.8, 2.4], lambda x, y, z, d, f: tex(SNOUT, x, y, z, 0.06), 4)   # 주둥이
    g.ellipsoid(add(hc, (0, -1.8, 7.0)), (2.4, 2.0, 0.6), "b08068", 5)
    for sx in (-1, 1):
        g.dot(8 + sx * 0.9, hc[1] - 1.6, hc[2] + 7.5, "2a1a10", 7)                            # 콧구멍
        g.ellipsoid(add(hc, (sx * 2.6, 1.2, 3.4)), (0.6, 0.6, 0.4), EYE, 7)
        g.slab(add(hc, (sx * 1.2, 2.2, 3.8)), add(hc, (sx * 3.6, 1.6, 3.4)), (0, 0.6, 0), 0.5, MANE, 7)   # 찌푸린 눈썹
        g.tube([add(hc, (sx * 2.2, -2.4, 5.4)), add(hc, (sx * 3.6, -2.2, 7.6)), add(hc, (sx * 3.8, 0.8, 8.8)), add(hc, (sx * 3.0, 3.2, 8.4))],
               [0.8, 0.7, 0.5, 0.12], TUSK, 7, smooth=True)                                        # 휜 엄니
        g.slab(add(hc, (sx * 3.0, 3.0, 0.4)), add(hc, (sx * 5.2, 5.6, -0.6)), (0, 0, 1.6), 0.5, FUR2, 5)   # 귀
    g.ring(add(hc, (0, -2.4, 7.2)), 0.9, 0.25, GOLD, 7, axis="z")                            # 금 코뚜레
    crown(g, add(hc, (0, 3.8, 0)), 2.2, GOLD, gem="ff3a3a", spikes=6, height=1.6)
    for i in range(12):                                                                        # 등 갈기 가시
        z = B.z0 + 2 + i * 1.8
        h = 2.5 + 3.0 * math.sin(i / 11 * math.pi)
        b = (8, B.back_y + 1.0 + (1.8 if i > 6 else 0), z)
        g.cone(b, add(b, (0, h, -1.4)), 0.9, 0.12, MANE, 5)
    for sx in (-1, 1):                                                                         # 이끼 낀 철 견갑
        c = (8 + sx * 5.2, B.back_y, zf - 5)
        g.ellipsoid(c, (2.6, 2.4, 3.6), lambda x, y, z, d, c=c: GOLD if y < c[1] - 1.6 else (MOSS if rnd(x, y, z) < 0.2 else tex(IRON, x, y, z)), 5, inner=0.5)
        g.cone(add(c, (sx * 1.2, 1.6, 0)), add(c, (sx * 2.6, 4.0, -0.6)), 0.7, 0.1, "c8ccd2", 6)
    for k in range(8):
        p = (8 + (rnd(k, 1) - 0.5) * 9, B.back_y + 0.4, B.z0 + 3 + k * 2)
        g.ellipsoid(p, (1.2, 0.4, 1.0), MOSS, 4)
    g.tube([B.tail, add(B.tail, (0, -1.5, -1.6)), add(B.tail, (0.6, -2.4, -1.2))], [0.6, 0.45, 0.3], FUR2, 4, smooth=True)


# =================================================================== 필드: 설원의 대곰
def field_frost_bear(g):
    FUR, FUR2, PAW, ICE, ICE2, EYE, NOSE = "e8f2fa", "c8d8e8", "5a7088", "7fe8ff", "d8fbff", "3fb8ff", "1a2a3a"
    B = quad(g, FUR, FUR2, PAW, L=21, H=12, W=13, leg=7.0, hump=1.2, leg_r=2.3, fur2=FUR2)
    zf = B.front_z
    hc = (8, B.cy + 3.0, zf + 2.0)
    g.ellipsoid(hc, (4.6, 4.0, 4.2), lambda x, y, z, d: tex(FUR, x, y, z, 0.06), 4)
    g.tube([add(hc, (0, -1.0, 2.8)), add(hc, (0, -1.4, 5.8))], [2.6, 2.0], lambda x, y, z, d, f: FUR2, 4)
    g.ellipsoid(add(hc, (0, -0.8, 6.2)), (1.2, 0.8, 0.6), NOSE, 6)
    g.box(add(hc, (-1.8, -3.4, 3.2)), add(hc, (1.8, -2.6, 6.0)), "3a4a5a", 5)                  # 벌린 입
    for x in (-1.4, -0.5, 0.5, 1.4):
        g.cone(add(hc, (x, -2.4, 5.6)), add(hc, (x, -3.2, 5.6)), 0.3, 0.05, ICE2, 7)
    for sx in (-1, 1):
        g.ellipsoid(add(hc, (sx * 2.2, 1.0, 3.4)), (0.6, 0.6, 0.4), EYE, 7)
        g.sphere(add(hc, (sx * 3.0, 3.6, -0.6)), 1.3, lambda x, y, z, d: FUR2 if d > 0.4 else "a8b8c8", 5)   # 둥근 귀
    for foot in B.legs:                                                                          # 서리 발톱
        for t in range(3):
            b = add(foot, ((t - 1) * 0.8, 0, 1.8))
            g.cone(b, add(b, (0, -0.6, 1.4)), 0.35, 0.05, ICE2, 6)
    # 등에 솟은 얼음 결정 무리
    for k in range(14):
        u = rnd(k, 3)
        z = B.z0 + 3 + k * 1.2
        x = 8 + (rnd(k, 5) - 0.5) * 7
        h = 3 + 6 * math.sin(k / 13 * math.pi) * (0.6 + 0.4 * u)
        b = (x, B.back_y + 1.6, z)
        g.cone(b, add(b, ((x - 8) * 0.3, h, -0.6)), 1.0 + u * 0.4, 0.1, lambda x_, y_, z_, f: ICE2 if f > 0.55 else ICE, 5)
    for k in range(7):   # 몸에 붙은 서리
        p = (8 + (rnd(k, 9) - 0.5) * 12, B.cy + (rnd(k, 8) - 0.3) * 6, B.z0 + 2 + k * 2.6)
        g.ellipsoid(p, (1.0, 0.5, 1.0), ICE2, 4)


# =================================================================== 필드: 도적왕
def field_bandit_lord(g):
    SKIN, LEATH, LEATH2, TRIM, RED, FUR, STEEL, GOLD = "c89868", "4a3a2a", "2a2420", "8a6a3a", "a8281e", "8a7a6a", "c8ccd2", "ffd23f"
    B = figure(g, s=1.0, w=1.1, skin=SKIN, top=LEATH, legs=LEATH2, boots="1a1612", arms=LEATH, reach=True)
    g.ellipsoid((8, 17.6, 8.3), (4.2, 2.6, 2.6), lambda x, y, z, d: TRIM if abs(x - 8 - (y - 17.6) * 0.8) < 0.5 else tex(LEATH2, x, y, z), 4, inner=0.62)   # 가죽 흉갑 · 사선 끈
    g.ellipsoid((8, 11.9, 8), (3.4, 0.7, 2.45), LEATH2, 5, inner=0.7)
    for k in range(7):
        a = -0.9 + k * 0.3
        g.ellipsoid((8 + math.sin(a) * 3.3, 11.9, 8 + math.cos(a) * 2.4), (0.5, 0.5, 0.3), GOLD, 6)   # 금화 허리띠
    for sx in (-1, 1):
        g.ellipsoid((8 + sx * 3.2, 9.8, 10.0), (1.2, 1.4, 0.9), lambda x, y, z, d: tex("6a4a2a", x, y, z), 5)   # 주머니
    g.ellipsoid((8, 19.4, 8.0), (5.0, 1.6, 3.4), lambda x, y, z, d: tex(FUR, x, y, z, 0.18), 6)   # 모피 깃
    hc = B.head
    g.ellipsoid(add(hc, (0, 0.5, -0.3)), (3.1, 3.3, 3.1), lambda x, y, z, d: tex("3a2e24", x, y, z) if z < hc[2] + 2.0 or y > hc[1] + 1.8 else None, 7, inner=0.6)   # 두건
    g.cone(add(hc, (0, 2.4, -1.4)), add(hc, (0.4, 3.4, -5.2)), 1.4, 0.2, "3a2e24", 7)
    g.ellipsoid(add(hc, (0, -1.0, 1.0)), (2.6, 1.6, 1.9), lambda x, y, z, d: RED if z > hc[2] + 1.2 else None, 8, inner=0.4)   # 붉은 복면
    g.slab(add(hc, (2.2, -1.2, 0)), add(hc, (3.2, -4.6, -0.8)), (0, 0, 0.9), 0.3, RED, 8)
    for sx in (-1, 1):
        g.dot(8 + sx * 0.95, hc[1], hc[2] + 2.5, "ffd23f", 10)
    # 양손의 굽은 단검
    for sx in (-1, 1):
        hand = B.hand_r if sx == 1 else B.hand_l
        g.slab(add(hand, (0, 0.6, 0.2)), add(hand, (0, 0.9, 0.3)), (1.8, 0, 0), 0.4, GOLD, 7)
        pts = [add(hand, (0, 1.0, 0.3)), add(hand, (sx * 0.4, 3.4, 1.2)), add(hand, (sx * 1.6, 5.8, 1.2)), add(hand, (sx * 3.2, 7.0, 0.4))]
        g.tube(pts, [0.75, 0.65, 0.5, 0.2], lambda x, y, z, d, f: "ffffff" if d > 0.7 else STEEL, 7, smooth=True, squash=(0.7, 1))
    # 등의 전리품 자루 · 망토
    cape(g, B, "5a2a1e", "3a1a12", length=4.0, width=4.2, ragged=True)
    g.ellipsoid((10.4, 16.4, B.back_z - 3.4), (3.0, 3.6, 2.4), lambda x, y, z, d: tex("8a6a3a", x, y, z, 0.15), 6)
    g.ellipsoid((10.4, 20.0, B.back_z - 3.4), (1.2, 0.7, 1.0), "6a4a2a", 7)
    for k in range(4):
        g.sphere((9.2 + k * 0.7, 20.6 + (k % 2) * 0.4, B.back_z - 3.0), 0.45, GOLD, 8)


# =================================================================== 필드: 사막의 파괴수
def field_ravager(g):
    SKIN, SKIN2, HORN, IRON, RED, EYE, SAND, WOOD = "6a5e52", "4a4038", "d8cfb8", "6a6f78", "a8281e", "ff4020", "c9a46a", "5a3a1a"
    B = quad(g, SKIN, "7a6e62", "2a2420", L=22, H=12, W=12, leg=8.0, hump=1.1, leg_r=2.3, fur2=SKIN2)
    zf = B.front_z
    hc = (8, B.cy - 0.5, zf + 3.5)
    g.ellipsoid(hc, (4.2, 4.6, 5.2), lambda x, y, z, d: tex(SKIN, x, y, z), 4)                     # 처진 긴 머리
    g.ellipsoid(add(hc, (0, -2.2, 3.8)), (3.2, 1.6, 2.4), lambda x, y, z, d: SKIN2, 4)             # 아래턱
    g.box(add(hc, (-2.4, -1.8, 5.2)), add(hc, (2.4, -0.6, 6.2)), "2a1a1a", 5)
    for x in (-1.8, -0.6, 0.6, 1.8):
        g.cone(add(hc, (x, -0.6, 5.8)), add(hc, (x, -1.8, 5.8)), 0.35, 0.05, HORN, 7)
    for sx in (-1, 1):
        g.ellipsoid(add(hc, (sx * 2.6, 1.2, 4.0)), (0.6, 0.5, 0.4), EYE, 7)
        pts = [add(hc, (sx * 3.2, 3.0, 0)), add(hc, (sx * 6.2, 4.0, -1.0)), add(hc, (sx * 7.4, 7.4, 0.6)), add(hc, (sx * 6.0, 9.6, 2.6))]
        g.tube(pts, [1.3, 1.0, 0.6, 0.12], lambda x, y, z, d, f: HORN if f < 2.4 else "f4ecd8", 6, smooth=True)   # 큰 뿔
    # 쇠사슬 갑주 (머리 · 목)
    g.ellipsoid(add(hc, (0, 1.0, -1.2)), (4.6, 4.6, 3.4), lambda x, y, z, d: IRON if (math.floor(x / VS) + math.floor(y / VS)) % 2 else hx(IRON, 0.75), 5, inner=0.8)
    # 전쟁 안장 · 깃발
    sc = (8, B.back_y + 1.8, B.z0 + 11)
    g.ellipsoid(sc, (5.0, 1.4, 4.6), lambda x, y, z, d: RED if d > 0.7 else tex("6a3a1a", x, y, z), 5)
    for sx in (-1, 1):
        g.slab((8 + sx * 5.6, B.back_y + 1.6, sc[2] - 3.4), (8 + sx * 6.2, B.cy - 2.6, sc[2] - 3.4), (0, 0, 7.0), 0.4,
               lambda x, y, z, u, v: SAND if abs(v) > 0.85 or u > 0.9 else RED, 5)
    for sx in (-1, 1):
        b = (8 + sx * 3.2, sc[1] + 0.6, sc[2] - 3.0)
        g.tube([b, add(b, (0, 12, -1.0))], [0.3, 0.3], WOOD, 6)
        g.slab(add(b, (0, 11.8, -1.0)), add(b, (0, 6.8, -1.0)), (0, 0, -4.6), 0.3,
               lambda x, y, z, u, v: SAND if abs(v) > 0.8 else (EYE if abs(v) < 0.25 and 0.3 < u < 0.6 else RED), 6)
        g.cone(add(b, (0, 12, -1.0)), add(b, (0, 13.4, -1.0)), 0.5, 0.05, "c8ccd2", 6)
    for k in range(6):   # 등 가시
        b = (8, B.back_y + 0.8, B.z0 + 2 + k * 1.3)
        g.cone(b, add(b, (0, 2.0, -1.2)), 0.7, 0.1, HORN, 5)
    g.tube([B.tail, add(B.tail, (0, -2, -3)), add(B.tail, (0, -5, -3.6))], [1.2, 0.8, 0.3], SKIN2, 4, smooth=True)


# =================================================================== 필드: 고대 골렘
def field_ancient_golem(g):
    ST, ST2, ST3, MOSS, MOSS2, RUNE, CRYS, VINE = "7a7a72", "5a5a54", "9a9a90", "5a8a3a", "3a6a2a", "5affa0", "8affd0", "2a5a2a"

    def stone(x, y, z, d):
        if d > 0.75 and rnd(math.floor(x / 1.5), math.floor(y / 1.2), math.floor(z / 1.5)) < 0.18:
            return MOSS if rnd(x, z) < 0.6 else MOSS2
        r = rnd(math.floor(x / 1.5), math.floor(y / 1.5), math.floor(z / 1.5))
        return ST if r < 0.5 else ST2 if r < 0.78 else ST3
    for sx in (-1, 1):   # 다리 기둥
        g.box((8 + sx * 3.4 - 2.4, 0, 5.6), (8 + sx * 3.4 + 2.4, 11, 10.6), lambda x, y, z: stone(x, y, z, 1), 3)
        g.box((8 + sx * 3.4 - 2.8, 0, 5.0), (8 + sx * 3.4 + 2.8, 1.6, 12.0), lambda x, y, z: ST2, 3)
    g.box((2.6, 10, 4.6), (13.4, 14.5, 11.4), lambda x, y, z: stone(x, y, z, 1), 3)                 # 허리
    g.ellipsoid((8, 20, 8.2), (8.2, 6.4, 5.2), stone, 3)                                            # 거대한 가슴
    for (a, b) in (((8, 15.2, 13.3), (8, 24.6, 13.0)), ((3.6, 20.2, 12.6), (12.4, 20.2, 12.6))):   # 빛나는 룬 십자
        g.tube([a, b], [0.45, 0.45], RUNE, 6)
    g.ellipsoid((8, 20.2, 13.2), (1.4, 1.4, 0.6), lambda x, y, z, d: "ffffff" if d < 0.3 else RUNE, 7)
    for sx in (-1, 1):   # 어깨 바위 · 팔 · 주먹
        sh = (8 + sx * 9.2, 23, 8)
        g.ellipsoid(sh, (3.8, 3.6, 3.8), stone, 4)
        g.box((sh[0] - 2.2, 12.5, 6.0), (sh[0] + 2.2, 22, 10.2), lambda x, y, z: stone(x, y, z, 1), 3)
        g.ellipsoid((sh[0] + sx * 0.4, 9.6, 8.8), (3.6, 3.6, 3.6), stone, 4)
        for t in range(3):
            g.tube([(sh[0] + (t - 1) * 1.8, 18, 10.4), (sh[0] + (t - 1) * 1.8, 14, 10.6)], [0.35, 0.35], RUNE, 5)
        for t in range(3):   # 늘어진 덩굴
            b = (sh[0] + (t - 1) * 1.4, 25.8, 8 + (t - 1) * 1.2)
            g.tube([b, add(b, (sx * 0.8, -3, 1.0)), add(b, (sx * 0.4, -6, 1.8))], [0.3, 0.25, 0.15], VINE, 5, smooth=True)
    # 머리 (가슴에 파묻힌 작은 머리) · 수정
    hc = (8, 27.4, 9.6)
    g.box((hc[0] - 3, hc[1] - 2.4, hc[2] - 2.6), (hc[0] + 3, hc[1] + 2.2, hc[2] + 2.6), lambda x, y, z: stone(x, y, z, 1), 4)
    g.box((hc[0] - 2.2, hc[1] - 0.2, hc[2] + 2.5), (hc[0] + 2.2, hc[1] + 0.6, hc[2] + 2.9), RUNE, 6)
    g.box((hc[0] - 3.2, hc[1] + 0.6, hc[2] + 2.3), (hc[0] + 3.2, hc[1] + 1.5, hc[2] + 3.0), ST2, 6)
    for k in range(5):
        a = (k - 2) * 0.4
        b = (hc[0] + math.sin(a) * 1.6, hc[1] + 2.0, hc[2] - 0.4)
        g.cone(b, add(b, (math.sin(a) * 1.4, 3.0 + (2 - abs(k - 2)) * 1.4, -0.4)), 0.8, 0.05, lambda x, y, z, f: "ffffff" if f > 0.8 else CRYS, 6)
    for k in range(6):   # 등에 자란 수정 · 풀
        b = (8 + (k - 2.5) * 2.4, 24 - (k % 2) * 2, 3.4)
        g.cone(b, add(b, ((k - 2.5) * 0.4, 3.0, -2.0)), 0.8, 0.05, CRYS if k % 2 else MOSS, 5)
    for k in range(8):
        a = k * 2 * math.pi / 8
        g.cone((8 + math.cos(a) * 7, 0.2, 8 + math.sin(a) * 6), (8 + math.cos(a) * 7.2, 1.8, 8 + math.sin(a) * 6.2), 0.9, 0.1, ST2, 2)


# =================================================================== 필드: 늪의 대마녀
def field_swamp_witch(g):
    SKIN, ROBE, ROBE2, MOSS, CAP, CAP2, BONE, WISP, WOOD = "8aa870", "3a4a2a", "2a3a1e", "5a7a3a", "a8321e", "f0e0c0", "e8e0cc", "c8ff5a", "4a3a22"
    B = figure(g, s=0.95, w=1.0, skin=SKIN, top=ROBE, legs=ROBE2, arms=ROBE, robe=True, eye=WISP)
    robe(g, B, ROBE, ROBE2, MOSS, flare=6.4, hem_ragged=True)
    for k in range(12):   # 로브에 붙은 이끼 뭉치
        a = k * 2.2
        g.ellipsoid((8 + math.cos(a) * 4.8, 2 + (k % 5) * 1.8, 8 + math.sin(a) * 4.0), (1.0, 0.7, 0.8), MOSS, 3)
    g.ellipsoid((8, 17.8, 7.4), (4.6, 2.8, 3.4), lambda x, y, z, d: tex(ROBE2, x, y, z), 4)   # 구부정한 등
    g.ellipsoid((8, 19.8, 6.4), (3.6, 1.8, 2.6), lambda x, y, z, d: MOSS if rnd(x, y, z) < 0.35 else ROBE2, 4)
    hair(g, B, "4a4a3a", length=12, width=2.6)
    hc = B.head
    g.tube([add(hc, (0, -0.2, 2.4)), add(hc, (0, -0.8, 3.8)), add(hc, (0, -1.8, 4.2))], [0.5, 0.4, 0.2], "6a8a50", 7, smooth=True)   # 매부리코
    # 버섯 모자 (넓은 갓 + 흰 점)
    g.ellipsoid(add(hc, (0, 2.6, 0)), (6.0, 2.6, 5.6), lambda x, y, z, d: (CAP2 if rnd(math.floor(x / 1.4), math.floor(z / 1.4)) < 0.2 else CAP) if y > hc[1] + 2.6 else "c8b89a", 6, inner=0.55)
    g.ellipsoid(add(hc, (0, 2.6, 0)), (5.8, 0.4, 5.4), "c8b89a", 6)
    for k in range(3):   # 작은 버섯들
        p = add(hc, (-4 + k * 1.5, 4.8 - abs(k - 1) * 0.6, -2.4))
        g.tube([add(p, (0, -1, 0)), p], [0.3, 0.3], CAP2, 7)
        g.ellipsoid(add(p, (0, 0.3, 0)), (0.9, 0.5, 0.9), CAP, 7)
    # 뼈 목걸이
    for k in range(9):
        a = -0.9 + k * 0.225
        p = (8 + math.sin(a) * 2.8, 19.2 - math.cos(a * 1.5) * 1.2, 8 + math.cos(a) * 2.9)
        g.sphere(p, 0.45, BONE if k != 4 else "ff5a3a", 7)
    g.ellipsoid((8, 17.4, 11.0), (0.9, 1.0, 0.6), BONE, 7)
    # 초롱 지팡이 (굽은 끝에 매달린 초롱)
    hand = B.hand_r
    top = add(hand, (0, 12, 1.6))
    g.tube([add(hand, (0, -12.6, -1.0)), top, add(top, (1.6, 1.8, 0.4)), add(top, (3.2, 0.8, 0.6))], [0.4, 0.35, 0.3, 0.25], lambda x, y, z, d, f: tex(WOOD, x, y, z, 0.15), 5, smooth=True)
    lp = add(top, (3.2, -1.6, 0.6))
    g.tube([add(top, (3.2, 0.8, 0.6)), lp], [0.12, 0.12], "3a3a3a", 6)
    g.box(add(lp, (-1.0, -2.6, -1.0)), add(lp, (1.0, 0, 1.0)), lambda x, y, z: "3a3a3a" if abs(x - lp[0]) > 0.6 or abs(z - lp[2]) > 0.6 else WISP, 6)
    for (x, y, z) in [(-5, 14, 6), (21, 18, 10), (-3, 24, 11), (20, 8, 3), (1, 30, 4)]:   # 도깨비불
        g.sphere((x, y, z), 0.9, lambda x_, y_, z_, d: "ffffff" if d < 0.3 else WISP, 6)
        g.cone((x, y + 0.5, z), (x + 0.3, y + 2.4, z - 0.4), 0.6, 0.1, WISP, 6)


# =================================================================== 필드: 화염 기사
def field_flame_knight(g):
    BLACK, STEEL, LAVA, HOT, FIRE, RED, GOLD = "1e1a1a", "2e2a2a", "ff5a1a", "ffb02a", "ff7a1f", "8a1a0e", "c8902a"
    B = figure(g, s=1.05, w=1.12, skin=BLACK, top=BLACK, legs=STEEL, boots=STEEL, arms=STEEL, hands=BLACK, head=False)
    armor(g, B, STEEL, LAVA, gem=HOT, spikes=2)
    cape(g, B, RED, FIRE, length=0.6, width=4.6, ragged=True)
    hc = B.head
    g.ellipsoid(hc, (2.8, 3.0, 2.9), lambda x, y, z, d: tex(BLACK, x, y, z), 6)   # 투구
    g.box((5.8, hc[1] + 0.1, hc[2] + 2.4), (10.2, hc[1] + 0.8, hc[2] + 3.1), HOT, 8)   # 눈 틈
    for k in range(4):
        g.box((6.4 + k * 1.0, hc[1] - 1.8, hc[2] + 2.5), (6.8 + k * 1.0, hc[1] - 0.4, hc[2] + 3.0), LAVA, 8)   # 숨구멍
    g.box((7.6, hc[1] - 2.4, hc[2] + 2.6), (8.4, hc[1] + 2.8, hc[2] + 3.2), GOLD, 8)
    for k in range(8):   # 불꽃 깃털 장식
        b = (8, hc[1] + 2.6, hc[2] + 1.0 - k * 0.8)
        g.cone(b, add(b, (0, 3.6 - abs(k - 2) * 0.35, -1.6)), 0.8, 0.1, lambda x, y, z, f: HOT if f < 0.35 else FIRE if f < 0.7 else LAVA, 7)
    # 불타는 대검 (오른손) · 화염 방패 (왼손)
    tip = sword(g, B.hand_r, 18, "3a3434", HOT, GOLD, width=2.2, tilt=(0.1, 1, 0.3))
    for k in range(6):
        p = lerp(add(B.hand_r, (0, 2, 0.7)), tip, 0.15 + k * 0.15)
        g.cone(add(p, (1.2, 0, 0)), add(p, (2.4, 1.4, -0.4)), 0.5, 0.05, FIRE, 7)
        g.cone(add(p, (-1.2, 0, 0)), add(p, (-2.4, 1.4, -0.4)), 0.5, 0.05, FIRE, 7)
    hl = B.hand_l
    g.slab(add(hl, (-1.4, 4.6, 0.4)), add(hl, (-1.4, -5.4, 1.2)), (0, 0, 6.4), 1.0,
           lambda x, y, z, u, v: GOLD if abs(v) > 0.85 or u < 0.05 else (LAVA if abs(v) < 0.12 or abs(u - 0.4) < 0.04 else tex(BLACK, x, y, z)), 6)
    g.ellipsoid(add(hl, (-2.1, 0.4, 1.0)), (0.5, 1.4, 1.4), lambda x, y, z, d: HOT if d < 0.4 else LAVA, 7)
    for k in range(10):   # 불티
        a = k * 2.3
        p = (8 + math.cos(a) * 12, 3 + k * 2.8, 8 + math.sin(a) * 10)
        g.cone(p, add(p, (0, 1.6, 0)), 0.5, 0.1, HOT if k % 2 else FIRE, 6)


# =================================================================== 필드: 심층의 감시자
def field_deep_warden(g):
    SKIN, SKIN2, DARK, TEAL, GLOW, BONE, SOUL = "1e3a44", "12282e", "0a161a", "2a8a8a", "3fe8ff", "b8c8c0", "a8fff8"
    for sx in (-1, 1):   # 굵은 다리
        g.tube([(8 + sx * 3.2, 12, 8), (8 + sx * 3.6, 6, 8.6), (8 + sx * 3.4, 1.0, 8.2)], [2.6, 2.2, 2.4], lambda x, y, z, d, f: tex(SKIN, x, y, z, 0.1), 3, smooth=True)
        g.ellipsoid((8 + sx * 3.4, 0.7, 9.2), (2.4, 0.8, 2.8), SKIN2, 4)
    g.ellipsoid((8, 14, 8), (5.2, 4.0, 4.0), lambda x, y, z, d: tex(SKIN, x, y, z, 0.1), 3)
    g.ellipsoid((8, 21, 8.3), (7.4, 5.6, 4.6), lambda x, y, z, d: tex(SKIN2 if rnd(math.floor(x), math.floor(y)) < 0.3 else SKIN, x, y, z, 0.1), 3)
    # 갈비뼈 우리 속 박동하는 영혼 심장
    g.ellipsoid((8, 20.5, 12.0), (3.6, 3.6, 1.2), DARK, 5)
    g.sphere((8, 20.5, 11.8), 1.8, lambda x, y, z, d: "ffffff" if d < 0.2 else SOUL if d < 0.6 else GLOW, 6)
    for k in range(5):
        y = 17.6 + k * 1.45
        g.tube([(3.6, y, 11.2), (6.0, y + 0.3, 12.9), (10.0, y + 0.3, 12.9), (12.4, y, 11.2)], [0.35, 0.35, 0.35, 0.35], BONE, 7, smooth=True)
    for k in range(18):   # 스컬크 반점
        p = (8 + (rnd(k, 1) - 0.5) * 14, 10 + rnd(k, 2) * 14, 8 + (rnd(k, 3) - 0.3) * 9)
        g.ellipsoid(p, (0.9, 0.9, 0.9), lambda x, y, z, d: GLOW if d < 0.25 else TEAL, 4)
    # 얼굴 없는 머리 (빛나는 틈) · 촉각 뿔
    hc = (8, 29.0, 9.0)
    g.ellipsoid(hc, (4.0, 3.6, 3.6), lambda x, y, z, d: tex(SKIN, x, y, z, 0.08), 5)
    g.box((5.0, hc[1] - 0.6, hc[2] + 3.0), (11.0, hc[1] + 0.2, hc[2] + 3.8), GLOW, 7)
    g.box((5.6, hc[1] - 2.6, hc[2] + 2.8), (10.4, hc[1] - 1.6, hc[2] + 3.6), DARK, 7)
    for sx in (-1, 1):
        pts = [(8 + sx * 3.2, hc[1] + 2.0, hc[2]), (8 + sx * 6.4, hc[1] + 4.2, hc[2] - 0.8), (8 + sx * 8.4, hc[1] + 7.6, hc[2] - 0.4), (8 + sx * 8.0, hc[1] + 9.8, hc[2] + 0.8)]
        g.tube(pts, [1.2, 0.9, 0.6, 0.2], lambda x, y, z, d, f: TEAL if f < 2 else GLOW, 6, smooth=True)
        for t in (1, 2):
            b = pts[t]
            g.tube([b, add(b, (sx * 1.8, 1.6, -0.6))], [0.45, 0.12], TEAL, 6)
    # 긴 팔 (바닥까지 늘어짐)
    for sx in (-1, 1):
        sh = (8 + sx * 7.6, 23.5, 8.2)
        g.sphere(sh, 2.8, lambda x, y, z, d: tex(SKIN2, x, y, z, 0.1), 4)
        g.tube([sh, (8 + sx * 10.4, 15, 9.4), (8 + sx * 10.8, 7.0, 10.8)], [2.3, 1.9, 1.7], lambda x, y, z, d, f: tex(SKIN, x, y, z, 0.1), 3, smooth=True)
        hand = (8 + sx * 10.8, 5.4, 11.0)
        g.sphere(hand, 2.0, SKIN2, 4)
        for t in range(4):
            g.cone(add(hand, ((t - 1.5) * 0.9, -1.2, 1.2)), add(hand, ((t - 1.5) * 1.1, -3.8, 2.4)), 0.5, 0.1, DARK, 5)
        for k in range(3):
            g.ellipsoid(add(sh, (sx * 0.8, -4 - k * 3.2, 1.8)), (0.6, 0.6, 0.6), GLOW, 5)
    for (x, y, z) in [(-6, 22, 6), (22, 26, 10), (-4, 10, 12), (21, 12, 3)]:   # 떠도는 영혼
        g.sphere((x, y, z), 0.9, lambda x_, y_, z_, d: "ffffff" if d < 0.3 else SOUL, 6)


# =================================================================== 필드: 서리 리치
def field_frost_lich(g):
    BONE, BONE2, ROBE, ROBE2, TRIM, ICE, ICE2, DARK = "e8e4d8", "b8b4a8", "1e2a4a", "2a3a6a", "9fd8ff", "7fe8ff", "e8fbff", "0a0e1a"
    B = figure(g, s=1.1, w=0.95, skin=BONE, top=ROBE, legs=ROBE2, arms=ROBE, hands=BONE, robe=True, head=False)
    robe(g, B, ROBE, ROBE2, TRIM, flare=6.4, hem_ragged=True)
    # 앞이 트인 로브 사이로 드러난 갈비뼈
    g.ellipsoid((8, 17.6 * 1.1, 10.2), (2.6, 3.0, 1.0), DARK, 5)
    for k in range(5):
        y = 16.4 + k * 1.2
        g.tube([(5.8, y, 10.2), (7.0, y + 0.3, 11.2), (9.0, y + 0.3, 11.2), (10.2, y, 10.2)], [0.3, 0.3, 0.3, 0.3], BONE, 7, smooth=True)
    g.tube([(8, 15.6, 10.9), (8, 22.6, 10.9)], [0.35, 0.35], BONE2, 7)
    g.sphere((8, 19.0, 10.2), 0.9, lambda x, y, z, d: "ffffff" if d < 0.3 else ICE, 8)   # 얼어붙은 심장
    for sx in (-1, 1):   # 로브 앞자락 테
        g.slab((8 + sx * 2.8, 24, 10.4), (8 + sx * 3.6, 1, 13.0), (0, 0, 0.6), 0.5, TRIM, 6)
    g.ellipsoid((8, 21.2, 8), (5.2, 1.8, 3.4), lambda x, y, z, d: ROBE2 if d > 0.5 else None, 5)   # 어깨 망토
    for sx in (-1, 1):   # 얼음 견갑
        sh = B.shoulder_r if sx == 1 else B.shoulder_l
        for t in range(3):
            g.cone(add(sh, (sx * 0.6, 1.2, (t - 1) * 1.0)), add(sh, (sx * (2.4 + t * 0.3), 3.8 - abs(t - 1), (t - 1) * 1.4)), 0.6, 0.05, ICE, 7)
    hc = B.head
    # 해골 머리 · 두건 · 얼음 왕관
    g.ellipsoid(hc, (2.3, 2.6, 2.4), lambda x, y, z, d: tex(BONE, x, y, z, 0.06), 6)
    for sx in (-1, 1):
        g.ellipsoid(add(hc, (sx * 0.95, 0.3, 2.0)), (0.7, 0.8, 0.5), DARK, 8)
        g.dot(hc[0] + sx * 0.95, hc[1] + 0.3, hc[2] + 2.35, ICE, 10)
    g.dot(8, hc[1] - 0.9, hc[2] + 2.4, DARK, 8)
    g.box((6.6, hc[1] - 2.6, hc[2] + 0.8), (9.4, hc[1] - 1.4, hc[2] + 2.2), BONE2, 6)
    for x in (6.9, 7.6, 8.3, 9.0):
        g.dot(x, hc[1] - 1.6, hc[2] + 2.2, BONE, 8)
    g.ellipsoid(add(hc, (0, 0.6, -0.5)), (3.1, 3.4, 3.2), lambda x, y, z, d: tex(ROBE, x, y, z) if z < hc[2] + 1.5 else None, 7, inner=0.6)
    g.ring((8, hc[1] + 2.6, hc[2]), 2.6, 0.35, TRIM, 8)
    for k in range(7):
        a = math.pi * (0.1 + k * 0.8 / 6)
        b = (8 + math.cos(a) * 2.6, hc[1] + 2.6, hc[2] + math.sin(a) * 2.2)
        g.cone(b, add(b, (math.cos(a) * 0.5, 2.0 + (3 - abs(k - 3)) * 0.8, 0)), 0.45, 0.05, lambda x, y, z, f: ICE2 if f > 0.6 else ICE, 8)
    # 얼음 구슬 지팡이
    top = staff(g, B.hand_r, 32, "3a4a6a", ICE, orb=None)
    g.sphere(add(top, (0, 1.4, 0.3)), 1.5, lambda x, y, z, d: "ffffff" if d < 0.2 else ICE2 if d < 0.5 else ICE, 7)
    for k in range(4):
        a = k * math.pi / 2 + 0.4
        g.cone(add(top, (0, 1.4, 0.3)), add(top, (math.cos(a) * 2.6, 1.4 + math.sin(a) * 2.6, 0.3)), 0.4, 0.05, ICE2, 6)
    for (x, y, z) in [(-5, 18, 6), (21, 22, 10), (-4, 8, 12), (20, 10, 3)]:   # 떠도는 룬석
        g.box((x - 1, y - 1.4, z - 0.6), (x + 1, y + 1.4, z + 0.6), "5a6478", 6)
        g.box((x - 0.3, y - 0.8, z + 0.5), (x + 0.3, y + 0.8, z + 0.8), ICE, 7)
    g.ring((8, 0.4, 8), 8.5, 0.35, ICE2, 2, axis="y")


BUILDERS = {"witch": witch, "dwarf_king": dwarf_king, "kain": kain, "thunder_god": thunder_god,
            "elf_queen": elf_queen, "harpy_queen": harpy_queen, "sea_gatekeeper": sea_gatekeeper, "bungbung": bungbung,
            "desert_nightmare": desert_nightmare, "siphonia": siphonia, "frost_queen": frost_queen, "volcano_giant": volcano_giant,
            "void_apostle": void_apostle, "vengeful_spirit": vengeful_spirit, "balrog": balrog, "megalodon": megalodon, "kraken": kraken,
            "field_boar_king": field_boar_king, "field_frost_bear": field_frost_bear, "field_bandit_lord": field_bandit_lord,
            "field_ravager": field_ravager, "field_ancient_golem": field_ancient_golem, "field_swamp_witch": field_swamp_witch,
            "field_flame_knight": field_flame_knight, "field_deep_warden": field_deep_warden, "field_frost_lich": field_frost_lich}


def build(bid, m):
    g = Grid()
    BUILDERS[bid](g)
    return emit(g, m)
