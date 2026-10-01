"""
v5.10.55 탈것 · 펫 복셀 조각 — 예전 상자 모델 대신 곡선으로 깎은 3D 모델 (보스 모델과 같은 공간: x 가운데 8, 발 y=0, 앞이 +z).
 탈것 8종: 잿빛 늑대 · 사막 도마뱀 · 기사의 군마 · 빙하 곰 · 불꽃 사자 · 그림자 표범 · 황금 그리핀 · 심연의 용
   안장 위 높이(=Java MountManager.Mount 의 saddle 값)는 build 때 재서 SADDLE 에 적어 둠 → 탑승 높이 · 크기가 맞게.
 펫 12종: 슬라임 · 병아리 · 토끼 · 여우 · 펭귄 · 부엉이 · 골렘 · 요정 · 유령 · 불사조 · 아기 용 · 별의 정령 (작고 귀엽게, 더 촘촘한 복셀)
가장 밝은 색(채널 하나라도 240 이상)은 음영을 받지 않아 빛나 보인다.
"""
import math

import voxel_lib as VL
from voxel_lib import quad, tex, hx, mix, rnd, add, lerp

SADDLE = {}


def _top(g, x, z, r=0.8):
    """(x, z) 근처에서 가장 높은 복셀 윗면"""
    vs = VL.VS
    best = 0.0
    for (i, j, k) in g.cells:
        if abs((i + 0.5) * vs - x) <= r and abs((k + 0.5) * vs - z) <= r:
            best = max(best, (j + 1) * vs)
    return best


def saddle(g, B, z, seat, trim, cloth=None, gem=None):
    """등 위 안장 (+ 양옆 등자 · 아래 천). 반환: 앉는 자리 높이"""
    y = _top(g, 8, z)
    g.ellipsoid((8, y + 0.3, z), (3.0, 0.9, 3.2), lambda x, yy, zz, d: hx(seat, 1.15) if yy > y + 0.7 else seat, 7)
    g.ellipsoid((8, y + 0.9, z - 2.6), (2.4, 1.3, 0.9), seat, 7)   # 뒤 등받이
    g.ellipsoid((8, y + 0.8, z + 2.8), (1.0, 1.0, 0.7), trim, 8)   # 앞 손잡이
    g.ring((8, y + 0.2, z), 3.1, 0.25, trim, 8, axis="y", tilt=0.0)
    if cloth:
        for sx in (-1, 1):
            g.slab((8 + sx * 3.6, y - 0.2, z), (8 + sx * 4.4, y - 4.4, z), (0, 0, 5.0), 0.4,
                   lambda x, yy, zz, u, v: trim if (u > 0.85 or abs(v) > 0.85) else cloth, 6)
            if gem:
                g.dot(8 + sx * 4.3, y - 2.4, z, gem, 9, 0.8)
    for sx in (-1, 1):   # 등자
        g.tube([(8 + sx * 3.0, y, z), (8 + sx * 3.9, y - 3.4, z)], [0.15, 0.15], "3a2a1a", 6)
        g.ring((8 + sx * 3.9, y - 3.9, z), 0.5, 0.18, trim, 8, axis="z")
    SADDLE["_last"] = y + 1.2
    return y + 1.2


def eyes(g, c, sx_gap, col, pupil=None, size=0.55, z_out=0.0, pri=9):
    x, y, z = c
    for sx in (-1, 1):
        g.dot(x + sx * sx_gap, y, z + z_out, col, pri, size)
        if pupil:
            g.dot(x + sx * sx_gap, y, z + z_out + 0.2, pupil, pri + 1, size * 0.5)


def feather_wing(g, root, sx, span, rise, c1, c2, rim, pri=4, rows=3, feathers=7, fold=0.0):
    """네발짐승 등에서 펼친 깃털 날개 (sx: -1 왼쪽 · 1 오른쪽)"""
    rx, ry, rz = root
    elbow = (rx + sx * span * 0.42, ry + rise * 0.75, rz - 2.0 - fold)
    tip = (rx + sx * span, ry + rise, rz - 5.0 - fold * 2)
    g.tube([root, elbow, tip], [1.0, 0.75, 0.3], hx(c1, 0.85), pri + 1, smooth=True)
    for row, (len0, c) in enumerate(((8.5, c1), (6.0, c2), (3.6, rim))):
        if row >= rows:
            break
        n = feathers + row
        for k in range(n):
            t = (k + 0.5) / n
            base = lerp(lerp(root, elbow, min(1, t * 2)), tip, max(0, t * 2 - 1))
            base = add(base, (0, -row * 1.0, 0.25 * row))
            L = len0 * (0.5 + 0.7 * t)
            end = add(base, (sx * 1.2 * t, -L * 0.8, -L * 0.55))
            g.slab(base, end, (sx * 1.2, 0, 0.15), 0.45,
                   lambda x, y, z, u, v, c=c: hx(c, 0.72) if abs(v) < 0.14 else (hx(c, 1.1) if abs(v) < 0.45 else hx(c, 1.0 - 0.2 * u)), pri + row)


def bat_wing(g, root, sx, span, rise, bone, membrane, rim, pri=4):
    rx, ry, rz = root
    elbow = (rx + sx * span * 0.4, ry + rise * 0.8, rz - 1.5)
    tip = (rx + sx * span, ry + rise, rz - 3.5)
    g.tube([root, elbow, tip], [1.0, 0.8, 0.3], bone, pri + 1, smooth=True)
    fingers = [(rx + sx * span * 1.02, ry + rise * 0.35, rz - 5.5), (rx + sx * span * 0.86, ry - 1.5, rz - 6.5), (rx + sx * span * 0.55, ry - 3.5, rz - 6.5)]
    prev = tip
    for f in fingers:
        g.tube([elbow if f is fingers[-1] else tip, f], [0.45, 0.15], bone, pri + 1)
        g.triangle(tip, prev, f, lambda p, w: rim if w < 0.05 else (membrane if rnd(*p) > 0.12 else hx(membrane, 1.2)), pri, scallop=0.14, thick=0.3)
        prev = f
    g.triangle(elbow, fingers[-1], root, lambda p, w: membrane, pri, scallop=0.1, thick=0.3)
    g.triangle(tip, elbow, fingers[-1], lambda p, w: membrane, pri, thick=0.3)
    g.cone(tip, add(tip, (sx * 0.8, 1.6, 0)), 0.4, 0.06, rim, pri + 2)   # 날개 끝 발톱


def furry_tail(g, base, pts_rel, r, col, tip=None, pri=5):
    pts = [base] + [add(base, p) for p in pts_rel]
    n = len(pts)
    radii = [r * (0.6 + 0.6 * math.sin((i + 0.5) / n * math.pi)) for i in range(n)]
    radii[-1] = r * 0.3
    g.tube(pts, radii, lambda x, y, z, d, f: (tip if tip and f > n - 2.2 else tex(col, x, y, z)), pri, smooth=True)


# ====================================================================== 탈것
def mount_wolf(g):
    FUR, FUR2, BELLY, PAW, EYE = "8a8f96", "6a6f78", "d0d4da", "3a3a40", "ffc83a"
    B = quad(g, FUR, BELLY, PAW, L=18, H=7.5, W=7.0, leg=6.0, shift=3.0, hump=1.1, leg_r=1.25, fur2=FUR2, simple_back=True)
    zf = B.front_z
    hc = (8, B.cy + 3.2, zf + 1.6)
    # 목 갈기 (풍성한 털)
    for k in range(14):
        a = k / 13 * math.pi
        p = (8 + math.cos(a) * 3.6, B.cy + 1.6 + math.sin(a) * 2.6, zf - 1.5)
        g.cone(p, add(p, (math.cos(a) * 1.4, math.sin(a) * 1.0 + 0.4, -2.6)), 1.0, 0.12, FUR2 if k % 2 else FUR, 4)
    g.ellipsoid(hc, (2.8, 2.5, 2.8), lambda x, y, z, d: tex(FUR, x, y, z), 5)
    g.tube([add(hc, (0, -0.6, 1.8)), add(hc, (0, -1.0, 4.6))], [1.6, 1.1], lambda x, y, z, d, f: BELLY if y < hc[1] - 1.2 else FUR, 5)   # 주둥이
    g.ellipsoid(add(hc, (0, -0.6, 4.9)), (0.7, 0.55, 0.4), "1a1a1e", 7)
    g.tube([add(hc, (-0.8, -1.8, 3.6)), add(hc, (0.8, -1.8, 3.6))], [0.25, 0.25], "5a2a2a", 7)   # 입
    eyes(g, add(hc, (0, 0.7, 2.4)), 1.25, EYE, "1a1a1a", size=0.6)
    for sx in (-1, 1):
        g.cone(add(hc, (sx * 1.6, 1.8, -0.4)), add(hc, (sx * 2.0, 4.6, -1.0)), 1.0, 0.1, FUR2, 6)   # 귀
        g.cone(add(hc, (sx * 1.6, 2.0, -0.1)), add(hc, (sx * 1.9, 4.0, -0.5)), 0.5, 0.05, "c89aa0", 7)
    furry_tail(g, B.tail, [(0, 0.6, -2.4), (0, -0.8, -5.0), (0, -2.8, -6.6)], 1.5, FUR, tip=BELLY)
    saddle(g, B, B.z0 + 18 * 0.5, "6a3a1a", "c9a13b", cloth="4a5a7a", gem="7fc8ff")


def mount_lizard(g):
    SK, SK2, BELLY, CLAW, EYE, SPK = "c9a15a", "a8823e", "f0dca0", "5a3a1a", "ff3a3a", "e07a2a"
    B = quad(g, SK, BELLY, CLAW, L=20, H=5.0, W=8.0, leg=3.2, shift=2.5, hump=0.8, leg_r=1.3, fur2=SK2, simple_back=True)
    zf = B.front_z
    hc = (8, B.cy + 0.8, zf + 1.6)
    g.ellipsoid(hc, (2.8, 1.8, 3.2), lambda x, y, z, d: tex(SK, x, y, z), 5)
    g.ellipsoid(add(hc, (0, -0.4, 3.0)), (2.0, 1.2, 2.2), lambda x, y, z, d: BELLY if y < hc[1] - 0.8 else tex(SK, x, y, z), 5)
    eyes(g, add(hc, (0, 1.0, 1.2)), 2.1, "ffd23f", EYE, size=0.7)
    g.tube([add(hc, (-1.6, -0.9, 4.4)), add(hc, (1.6, -0.9, 4.4))], [0.2, 0.2], "5a2a1a", 7)
    for sx in (-1, 1):   # 목 프릴
        g.triangle(add(hc, (sx * 2.0, 0.5, -1.0)), add(hc, (sx * 4.8, 3.0, -2.2)), add(hc, (sx * 4.6, -1.6, -2.6)),
                   lambda p, w: SPK if w < 0.25 else "f0b050", 4, scallop=0.12, thick=0.3)
    for k in range(9):   # 등 돌기
        z = B.z0 + 2 + k * 2.0
        p = (8, _top(g, 8, z) - 0.2, z)
        g.cone(p, add(p, (0, 1.6 + (k % 3 == 1) * 0.8, -0.6)), 0.6, 0.08, SPK, 6)
    tail = [B.tail, add(B.tail, (0, -0.4, -4.0)), add(B.tail, (1.2, -1.2, -8.0)), add(B.tail, (2.8, -2.0, -11.0)), add(B.tail, (2.4, -2.4, -13.4))]
    g.tube(tail, [2.0, 1.6, 1.1, 0.6, 0.2], lambda x, y, z, d, f: SPK if int(f * 3) % 2 and d > 0.6 else tex(SK, x, y, z), 4, smooth=True)
    for k in range(10):   # 모래 무늬
        p = (8 + (rnd(k, 3) - 0.5) * 6, _top(g, 8, B.z0 + 3 + k * 1.6) - 0.3, B.z0 + 3 + k * 1.6)
        g.dot(p[0], p[1], p[2], SK2, 6, 0.7)
    saddle(g, B, B.z0 + 10.5, "5a3a2a", "e0b030", cloth="c84a2a", gem="ffd23f")


def mount_warhorse(g):
    COAT, COAT2, MANE, HOOF = "5a3a22", "4a2e1a", "1a1414", "2a2420"
    STEEL, STEEL_H, GOLD, RED = "a8b0bc", "dde4ee", "e0b030", "a01a1a"
    B = quad(g, COAT, COAT2, HOOF, L=21, H=8.0, W=7.6, leg=8.5, shift=3.0, hump=1.0, leg_r=1.25, fur2=COAT2)
    zf = B.front_z
    # 목 · 머리 (앞으로 비스듬히)
    neck = [(8, B.cy + 2.0, zf - 3.0), (8, B.cy + 5.0, zf - 0.4), (8, B.cy + 7.4, zf + 1.4)]
    g.tube(neck, [2.6, 2.2, 1.8], lambda x, y, z, d, f: tex(COAT, x, y, z), 4, smooth=True)
    hc = (8, B.cy + 8.0, zf + 2.6)
    g.ellipsoid(hc, (1.9, 1.9, 2.4), lambda x, y, z, d: tex(COAT, x, y, z), 5)
    g.tube([add(hc, (0, -0.6, 1.4)), add(hc, (0, -1.6, 4.6))], [1.6, 1.3], lambda x, y, z, d, f: tex(COAT, x, y, z), 5)
    g.ellipsoid(add(hc, (0, -1.8, 4.8)), (1.3, 1.1, 0.6), "3a2a20", 6)
    # 갈기 · 꼬리
    for k in range(10):
        t = k / 9
        p = lerp(neck[0], neck[-1], t)
        p = (8, p[1] + 1.8, p[2] - 1.0)
        g.slab(p, add(p, (0, -2.4 - (k % 2) * 0.8, -1.6)), (0.8, 0, 0), 0.6, MANE, 6)
    g.tube([B.tail, add(B.tail, (0, -0.6, -2.0)), add(B.tail, (0, -4.0, -3.2)), add(B.tail, (0, -7.0, -3.0))], [1.0, 1.1, 0.9, 0.3], MANE, 5, smooth=True)
    # 투구 (챈프런) · 깃털
    g.ellipsoid(add(hc, (0, 0.4, 0.8)), (2.1, 1.6, 2.6), lambda x, y, z, d: GOLD if d > 0.9 else tex(STEEL, x, y, z, 0.06), 6, inner=0.6)
    g.tube([add(hc, (0, 1.2, 2.6)), add(hc, (0, -0.8, 4.4))], [0.9, 0.7], lambda x, y, z, d, f: STEEL_H if d > 0.6 else STEEL, 7)
    g.cone(add(hc, (0, 1.8, 2.0)), add(hc, (0, 3.4, 3.6)), 0.5, 0.08, GOLD, 8)   # 이마 뿔
    eyes(g, add(hc, (0, 0.6, 1.0)), 1.9, "1a1a1a", size=0.6)
    for k in range(6):   # 붉은 깃털 장식
        g.tube([add(hc, (0, 1.6, -0.6)), add(hc, (0, 3.6 + k * 0.3, -1.4 - k * 0.6)), add(hc, (0, 4.6 + k * 0.2, -3.4 - k * 0.7))], [0.5, 0.45, 0.1],
               RED if k % 2 else "d02a2a", 7, smooth=True, squash=(0.5, 1))
    for sx in (-1, 1):
        g.cone(add(hc, (sx * 1.1, 1.8, -1.0)), add(hc, (sx * 1.3, 3.2, -1.4)), 0.5, 0.08, COAT2, 7)   # 귀
    # 마갑: 목 판 · 가슴 판 · 옆구리 천 (붉은 바탕 금 테 · 사자 문장)
    g.tube(neck, [2.9, 2.5, 2.1], lambda x, y, z, d, f: (GOLD if int(f * 4) % 2 == 0 and d > 0.9 else tex(STEEL, x, y, z, 0.06)) if y > B.cy + 1.0 else None, 6)
    g.ellipsoid((8, B.cy + 0.4, zf - 1.6), (3.9, 3.0, 2.0), lambda x, y, z, d: GOLD if d > 0.9 else tex(STEEL, x, y, z, 0.06), 6, inner=0.75)
    g.dot(8, B.cy + 0.6, zf + 0.4, GOLD, 9, 1.0)
    # 옆구리 천 (몸을 감싸 늘어진 붉은 천 · 금 테 · 가운데 금 문장)
    zm, cy = B.z0 + 21 * 0.5, B.cy

    def cloth(x, y, z, d):
        if y > cy + 2.2:
            return None
        if y < cy - 3.6 or abs(z - zm) > 7.0:
            return None
        if y < cy - 2.9 or abs(z - zm) > 6.3 or y > cy + 1.6:
            return GOLD
        if abs(z - zm) < 1.4 and abs(y - (cy - 0.8)) < 1.2:
            return "ffd84a"
        return RED
    g.ellipsoid((8, cy, zm), (4.6, 4.8, 8.4), cloth, 6, inner=0.82)
    # 반짝이는 편자 · 털신
    for f in B.legs:
        g.ellipsoid((f[0], 1.6, f[2] + 0.3), (1.5, 0.9, 1.6), "f4f0e8", 5)
    saddle(g, B, B.z0 + 21 * 0.48, "6a1a1a", GOLD)


def mount_icebear(g):
    FUR, FUR2, PAW, ICE, ICE2, EYE, NOSE = "eef6fc", "c8d8e8", "5a7088", "7fe8ff", "d8fbff", "3fb8ff", "1a2a3a"
    B = quad(g, FUR, FUR2, PAW, L=20, H=9.0, W=10.0, leg=5.8, shift=3.0, hump=1.25, leg_r=1.9, fur2=FUR2)
    zf = B.front_z
    hc = (8, B.cy + 2.2, zf + 1.6)
    g.ellipsoid(hc, (3.4, 3.0, 3.2), lambda x, y, z, d: tex(FUR, x, y, z, 0.06), 5)
    g.tube([add(hc, (0, -0.8, 2.2)), add(hc, (0, -1.1, 4.6))], [2.0, 1.5], FUR2, 5)
    g.ellipsoid(add(hc, (0, -0.8, 5.0)), (1.0, 0.7, 0.5), NOSE, 7)
    eyes(g, add(hc, (0, 0.8, 2.6)), 1.4, EYE, "ffffff", size=0.65)
    for sx in (-1, 1):
        g.sphere(add(hc, (sx * 2.4, 2.4, -0.6)), 1.0, FUR2, 6)   # 둥근 귀
    # 얼음 갑주: 어깨 · 머리 위 왕관 결정
    for sx in (-1, 1):
        c = (8 + sx * 4.6, B.back_y + 0.6, zf - 4.0)
        g.ellipsoid(c, (2.6, 2.0, 3.4), lambda x, y, z, d: ICE2 if d > 0.85 else ICE, 6, inner=0.55)
        for k in range(3):
            b = add(c, (sx * 0.8, 1.2, -1.6 + k * 1.6))
            g.cone(b, add(b, (sx * (1.2 + k * 0.3), 2.6 + (k == 1) * 1.4, -0.4)), 0.7, 0.08, ICE2 if k == 1 else ICE, 7)
    for k in range(5):
        a = math.pi * (0.2 + 0.6 * k / 4)
        b = add(hc, (math.cos(a) * 1.8, 2.4, -0.8 + math.sin(a) * 0.4))
        g.cone(b, add(b, (math.cos(a) * 0.6, 1.4 + (k == 2) * 1.2, 0)), 0.5, 0.06, ICE2 if k == 2 else ICE, 8)
    for k in range(6):   # 등의 얼음 결정
        z = B.z0 + 3 + k * 1.6
        if abs(z - (B.z0 + 10)) < 3.6:
            continue
        p = (8 + (rnd(k, 7) - 0.5) * 4, _top(g, 8, z) - 0.4, z)
        g.cone(p, add(p, (0, 2.0 + rnd(k) * 1.4, -0.5)), 0.7, 0.08, ICE, 7)
    furry_tail(g, B.tail, [(0, 0.4, -1.4), (0, 0, -2.2)], 1.3, FUR)
    for f in B.legs:   # 서리 발톱
        for k in range(3):
            g.cone((f[0] + (k - 1) * 0.6, 0.5, f[2] + 1.4), (f[0] + (k - 1) * 0.7, 0.2, f[2] + 2.2), 0.3, 0.05, ICE2, 8)
    saddle(g, B, B.z0 + 10, "2a4a6a", "bff4ff", cloth="3a6a9a", gem=ICE)


def mount_lion(g):
    FUR, FUR2, BELLY, PAW, EYE = "e0a040", "c88a30", "f0c880", "7a4a1a", "ff3a1a"
    F1, F2, F3, F4 = "ff4a1a", "ff8a1f", "ffd23f", "fff6c0"
    B = quad(g, FUR, BELLY, PAW, L=19, H=7.5, W=7.6, leg=6.8, shift=3.0, hump=1.1, leg_r=1.45, fur2=FUR2)
    zf = B.front_z
    hc = (8, B.cy + 3.0, zf + 1.8)
    # 불꽃 갈기 (머리 둘레 3겹)
    for ring_i, (rr, ln, cols) in enumerate(((3.8, 3.8, (F1, F2)), (3.0, 2.8, (F2, F3)), (2.3, 1.8, (F3, F4)))):
        n = 16 - ring_i * 3
        for k in range(n):
            a = k / n * 2 * math.pi
            p = add(hc, (math.cos(a) * rr, math.sin(a) * rr * 0.9, -1.4 + ring_i * 0.5))
            tip = add(p, (math.cos(a) * ln * 0.6, math.sin(a) * ln * 0.6 + ln * 0.5, -ln * 0.8))
            g.cone(p, tip, 1.0 - ring_i * 0.15, 0.08, lambda x, y, z, f, c=cols: c[0] if f < 0.5 else c[1], 4 + ring_i)
    g.ellipsoid(hc, (2.7, 2.6, 2.6), lambda x, y, z, d: tex(FUR, x, y, z, 0.06), 7)
    g.tube([add(hc, (0, -0.8, 1.6)), add(hc, (0, -1.2, 3.8))], [1.6, 1.3], BELLY, 7)
    g.ellipsoid(add(hc, (0, -0.6, 4.1)), (0.8, 0.5, 0.4), "6a2a1a", 8)
    eyes(g, add(hc, (0, 0.8, 2.2)), 1.2, F3, EYE, size=0.6)
    for sx in (-1, 1):
        g.cone(add(hc, (sx * 0.5, -1.9, 3.4)), add(hc, (sx * 0.5, -2.8, 3.5)), 0.3, 0.05, "ffffff", 9)   # 송곳니
        g.sphere(add(hc, (sx * 2.0, 2.2, -0.2)), 0.8, FUR2, 8)
    # 꼬리 + 불꽃 끝
    tail = [B.tail, add(B.tail, (0, 0.8, -3.0)), add(B.tail, (0.8, 2.6, -6.0)), add(B.tail, (0.6, 5.0, -7.4))]
    g.tube(tail, [0.7, 0.6, 0.5, 0.4], FUR, 5, smooth=True)
    for k in range(5):
        a = k / 5 * 2 * math.pi
        b = tail[-1]
        g.cone(b, add(b, (math.cos(a) * 1.0, 2.4 + (k % 2), math.sin(a) * 1.0)), 0.9, 0.06, F2 if k % 2 else F3, 6)
    g.sphere(add(tail[-1], (0, 0.6, 0)), 0.8, F4, 8)
    for f in B.legs:   # 발목 불꽃
        for k in range(3):
            a = k / 3 * 2 * math.pi
            g.cone((f[0] + math.cos(a) * 1.2, 2.4, f[2] + math.sin(a) * 1.2), (f[0] + math.cos(a) * 1.5, 4.0, f[2] + math.sin(a) * 1.5 - 0.8), 0.5, 0.05, F2, 6)
    for (x, y, z) in ((2.0, B.back_y + 4, B.z0 + 4), (14.0, B.back_y + 3, B.z0 + 12), (8.0, B.back_y + 6, B.z0 - 2)):   # 불티
        g.sphere((x, y, z), 0.5, F4, 9)
    saddle(g, B, B.z0 + 9.2, "6a1a0a", "ffd23f", cloth="8a2a10", gem=F3)


def mount_panther(g):
    FUR, FUR2, PAW, RUNE, RUNE2, EYE = "1c1c28", "282838", "0a0a12", "9a4aff", "e0b0ff", "d080ff"
    B = quad(g, FUR, FUR2, PAW, L=20, H=6.5, W=6.6, leg=6.4, shift=3.0, hump=1.0, leg_r=1.2, fur2=FUR2, simple_back=True)
    zf = B.front_z
    hc = (8, B.cy + 2.0, zf + 1.4)
    g.ellipsoid(hc, (2.4, 2.1, 2.5), lambda x, y, z, d: tex(FUR, x, y, z, 0.08), 5)
    g.tube([add(hc, (0, -0.6, 1.6)), add(hc, (0, -0.9, 3.4))], [1.3, 1.0], FUR2, 5)
    g.ellipsoid(add(hc, (0, -0.6, 3.6)), (0.6, 0.4, 0.3), "4a3a5a", 7)
    for sx in (-1, 1):   # 가늘게 빛나는 눈 + 흐르는 빛
        g.tube([add(hc, (sx * 0.8, 0.6, 2.2)), add(hc, (sx * 1.6, 0.9, 1.8))], [0.3, 0.2], EYE, 9)
        g.tube([add(hc, (sx * 1.6, 0.9, 1.8)), add(hc, (sx * 2.6, 1.0, -0.6)), add(hc, (sx * 3.2, 1.4, -3.0))], [0.2, 0.15, 0.05], RUNE, 8)
        g.cone(add(hc, (sx * 1.4, 1.6, -0.4)), add(hc, (sx * 1.9, 3.4, -0.8)), 0.8, 0.08, FUR, 6)
        g.cone(add(hc, (sx * 0.4, -1.6, 3.0)), add(hc, (sx * 0.4, -2.4, 3.1)), 0.25, 0.05, "f0f0ff", 9)
    # 몸의 빛나는 룬 줄무늬
    for k in range(7):
        z = B.z0 + 2.4 + k * 2.2
        for sx in (-1, 1):
            top = _top(g, 8, z)
            for j in range(6):
                y = top - 0.6 - j * 0.5
                g.put(8 + sx * (2.6 + j * 0.25), y, z + j * 0.15, RUNE if j < 4 else RUNE2, 7)
    # 길게 휘어지는 꼬리 + 그림자 연기
    tail = [B.tail, add(B.tail, (0, 1.0, -3.4)), add(B.tail, (1.6, 3.0, -6.4)), add(B.tail, (3.4, 3.4, -8.0)), add(B.tail, (4.6, 2.2, -8.4))]
    g.tube(tail, [0.8, 0.7, 0.6, 0.45, 0.2], lambda x, y, z, d, f: RUNE if f > 3.2 else FUR, 5, smooth=True)
    for (x, y, z, s) in ((3.0, B.back_y + 2.0, B.z0 + 3, 0.9), (13.0, B.back_y + 1.4, B.z0 + 14, 0.8), (6.0, 1.4, B.z0 - 2, 1.0), (11.0, 1.2, zf, 0.9)):
        g.sphere((x, y, z), s, lambda xx, yy, zz, d: RUNE2 if d < 0.3 else "4a2a7a", 3)
    saddle(g, B, B.z0 + 10, "2a1a3a", "c060ff", cloth="1a1028", gem=RUNE2)


def mount_griffin(g):
    FUR, FUR2, BELLY, PAW = "d8b060", "b89040", "f4e0a0", "6a4a1a"
    WHITE, WHITE2, GOLD, BEAK, EYE = "f8f8fa", "dcdce4", "f0c030", "f0b020", "ffd23f"
    B = quad(g, FUR, BELLY, PAW, L=19, H=7.5, W=7.6, leg=7.2, shift=3.0, hump=1.1, leg_r=1.4, fur2=FUR2)
    zf = B.front_z
    # 앞다리는 독수리 발톱 (금빛 비늘)
    for f in B.legs:
        if f[2] > 8:
            g.tube([(f[0], 3.6, f[2]), (f[0], 0.8, f[2] + 0.4)], [1.1, 0.9], GOLD, 6)
            for k in range(3):
                g.tube([(f[0] + (k - 1) * 0.6, 0.8, f[2] + 0.8), (f[0] + (k - 1) * 0.8, 0.4, f[2] + 2.0), (f[0] + (k - 1) * 0.8, 0.0, f[2] + 2.4)], [0.35, 0.25, 0.06], "2a2018", 7)
    # 흰 깃털 목 · 독수리 머리
    neck = [(8, B.cy + 1.8, zf - 2.6), (8, B.cy + 4.6, zf - 0.2), (8, B.cy + 6.6, zf + 1.0)]
    g.tube(neck, [3.0, 2.5, 2.0], lambda x, y, z, d, f: WHITE2 if rnd(round(x), round(y), round(z)) < 0.3 else WHITE, 5, smooth=True)
    for k in range(10):
        p = lerp(neck[0], neck[-1], k / 9)
        for sx in (-1, 1):
            g.cone(add(p, (sx * 2.0, 0, -0.4)), add(p, (sx * 2.6, -1.6, -1.6)), 0.7, 0.08, WHITE, 5)
    hc = (8, B.cy + 7.4, zf + 1.6)
    g.ellipsoid(hc, (2.0, 2.0, 2.2), WHITE, 6)
    g.tube([add(hc, (0, 0.2, 1.6)), add(hc, (0, -0.4, 3.2)), add(hc, (0, -1.6, 3.8))], [1.1, 0.7, 0.2], BEAK, 7, smooth=True)   # 굽은 부리
    eyes(g, add(hc, (0, 0.5, 1.4)), 1.35, EYE, "1a1a1a", size=0.6)
    for sx in (-1, 1):
        g.slab(add(hc, (sx * 1.2, 1.4, 1.2)), add(hc, (sx * 1.8, 1.1, 0.0)), (0, 0.5, 0), 0.4, "a88a50", 8)   # 눈썹
        for k in range(3):   # 머리 뒤 깃 볏
            g.cone(add(hc, (sx * 0.6, 1.4, -1.0)), add(hc, (sx * (0.8 + k * 0.4), 2.4 + k * 0.4, -3.2 - k * 0.6)), 0.5, 0.06, GOLD if k == 2 else WHITE, 7)
    # 큰 날개 (흰색 → 금 끝)
    for sx in (-1, 1):
        root = (8 + sx * 3.0, B.back_y + 0.4, zf - 6.0)
        feather_wing(g, root, sx, 14.0, 8.0, WHITE, "f4e8c0", GOLD, pri=4)
    # 사자 꼬리 + 깃털 술
    tail = [B.tail, add(B.tail, (0, 0.4, -3.0)), add(B.tail, (0, -1.0, -6.0)), add(B.tail, (0, -2.6, -7.4))]
    g.tube(tail, [0.6, 0.5, 0.45, 0.3], FUR, 5, smooth=True)
    for k in range(4):
        g.cone(tail[-1], add(tail[-1], ((k - 1.5) * 0.6, -1.6, -1.4)), 0.6, 0.06, WHITE if k % 2 else GOLD, 6)
    saddle(g, B, B.z0 + 9.8, "6a3a1a", "e0b030", cloth="2a4aa0", gem="ffd23f")


def mount_dragon(g):
    SC, SC2, BELLY, CLAW, HORN, EYE = "3a1a5a", "2a1044", "c9a13b", "1a0a24", "fff3b0", "ff3030"
    MEM, GLOW = "5a2a7a", "c060ff"
    B = quad(g, SC, BELLY, CLAW, L=21, H=8.0, W=8.0, leg=6.8, shift=3.0, hump=1.15, leg_r=1.6, fur2=SC2)
    zf = B.front_z
    neck = [(8, B.cy + 2.0, zf - 3.0), (8, B.cy + 5.0, zf - 0.6), (8, B.cy + 7.8, zf + 0.4), (8, B.cy + 9.2, zf + 2.4)]
    g.tube(neck, [2.6, 2.2, 1.9, 1.7], lambda x, y, z, d, f: BELLY if (z > neck[0][2] + 1 and d > 0.75 and y < neck[0][1] + f * 2.5 + 1.5 and z > 8) else tex(SC, x, y, z, 0.1), 4, smooth=True)
    hc = (8, B.cy + 9.6, zf + 3.6)
    g.ellipsoid(hc, (2.2, 1.9, 2.6), lambda x, y, z, d: tex(SC, x, y, z, 0.08), 6)
    g.tube([add(hc, (0, -0.2, 1.8)), add(hc, (0, -0.6, 4.6))], [1.5, 1.0], lambda x, y, z, d, f: tex(SC, x, y, z), 6)   # 주둥이
    g.tube([add(hc, (0, -1.4, 1.2)), add(hc, (0, -1.8, 4.0))], [1.1, 0.7], SC2, 6)   # 아래턱
    for sx in (-1, 1):
        g.dot(8 + sx * 0.6, hc[1] - 0.2, hc[2] + 4.8, "1a0a1a", 8, 0.4)
        g.tube([add(hc, (sx * 1.0, 1.2, -0.6)), add(hc, (sx * 1.8, 2.6, -2.4)), add(hc, (sx * 1.6, 3.4, -4.6))], [0.6, 0.4, 0.08], HORN, 7, smooth=True)   # 뿔
        g.tube([add(hc, (sx * 1.8, 0.0, -0.4)), add(hc, (sx * 2.8, 0.4, -2.0))], [0.35, 0.06], HORN, 7)
        for k in range(3):
            g.cone(add(hc, (sx * 1.0, -1.4, 2.0 + k * 0.8)), add(hc, (sx * 1.0, -2.0, 2.0 + k * 0.8)), 0.22, 0.05, "ffffff", 8)
    eyes(g, add(hc, (0, 0.7, 1.6)), 1.5, EYE, "ffd23f", size=0.6)
    for k in range(12):   # 등 가시 (목부터 꼬리까지)
        z = zf - 1 - k * 1.8
        p = (8, _top(g, 8, z) - 0.3, z)
        g.cone(p, add(p, (0, 1.4 + 0.6 * math.sin(k / 11 * math.pi), -0.7)), 0.55, 0.08, HORN, 7)
    for k in range(6):
        p = lerp(neck[0], neck[-1], k / 5)
        g.cone(add(p, (0, 1.6, -0.3)), add(p, (0, 2.8, -1.2)), 0.45, 0.06, HORN, 7)
    for sx in (-1, 1):   # 박쥐 날개
        root = (8 + sx * 3.2, B.back_y + 0.4, zf - 5.0)
        bat_wing(g, root, sx, 15.0, 8.0, SC2, MEM, GLOW, pri=4)
    tail = [B.tail, add(B.tail, (0, 0.2, -3.6)), add(B.tail, (1.6, -0.8, -7.6)), add(B.tail, (3.6, -2.0, -10.0)), add(B.tail, (4.6, -2.6, -11.6))]
    g.tube(tail, [2.0, 1.5, 1.0, 0.6, 0.3], lambda x, y, z, d, f: tex(SC, x, y, z), 4, smooth=True)
    tip = tail[-1]
    g.triangle(tip, add(tip, (1.6, 1.6, -1.2)), add(tip, (-1.0, 0.8, -2.0)), lambda p, w: GLOW if w < 0.4 else MEM, 6, thick=0.3)
    for (x, y, z) in ((1.0, B.back_y + 5, B.z0 + 2), (15.0, B.back_y + 4, B.z0 + 15), (8.0, 1.0, zf + 2)):
        g.sphere((x, y, z), 0.5, lambda xx, yy, zz, d: "f4e0ff" if d < 0.4 else GLOW, 9)
    saddle(g, B, B.z0 + 9.6, "1a0a24", "c060ff", cloth="2a1040", gem=GLOW)


# ====================================================================== 펫 (작고 동글동글 · 큰 눈)
def _cute_eyes(g, c, gap, col="1a1a24", size=0.9, z_out=0.0, pri=10):
    x, y, z = c
    for sx in (-1, 1):
        g.ellipsoid((x + sx * gap, y, z + z_out), (size * 0.55, size * 0.75, 0.35), col, pri)
        g.dot(x + sx * gap - 0.15, y + size * 0.3, z + z_out + 0.3, "ffffff", pri + 1, 0.35)


def _blush(g, c, gap, col="ff9aa8", pri=9):
    x, y, z = c
    for sx in (-1, 1):
        g.ellipsoid((x + sx * gap, y, z), (0.6, 0.3, 0.25), col, pri)


def pet_slime(g):
    G1, G2, G3, CORE = "6fdc5a", "4fb83e", "c4ffb0", "2a8a2a"
    g.ellipsoid((8, 4.6, 8), (5.4, 4.6, 5.2), lambda x, y, z, d: G3 if (y > 7.2 and z > 7) else (G1 if d > 0.5 else G2), 4)
    g.ellipsoid((8, 0.9, 8), (5.6, 0.9, 5.4), G2, 4)   # 퍼진 밑
    g.sphere((6.4, 4.0, 9.2), 1.1, CORE, 3)   # 속 덩어리 (살짝 비침)
    g.sphere((9.8, 3.0, 7.4), 0.8, CORE, 3)
    _cute_eyes(g, (8, 5.2, 12.9), 1.7, size=1.3)
    g.tube([(7.2, 3.4, 13.0), (8.0, 3.0, 13.1), (8.8, 3.4, 13.0)], [0.22, 0.22, 0.22], "2a5a22", 10, smooth=True)
    _blush(g, (8, 4.0, 12.9), 3.0)
    g.sphere((5.6, 8.0, 10.0), 0.6, "ffffff", 9)   # 반짝
    g.ellipsoid((8, 9.4, 8), (1.4, 0.5, 1.4), "ffd23f", 8)   # 작은 왕관
    for k in range(5):
        a = k / 5 * 2 * math.pi
        g.cone((8 + math.cos(a) * 1.3, 9.6, 8 + math.sin(a) * 1.3), (8 + math.cos(a) * 1.3, 10.8, 8 + math.sin(a) * 1.3), 0.35, 0.05, "ffd23f", 8)
    g.dot(8, 9.8, 9.4, "ff3a5a", 9, 0.5)


def pet_chick(g):
    Y, Y2, OR, WING = "ffe04a", "fff08a", "ff9a2a", "f5c830"
    g.ellipsoid((8, 4.4, 7.6), (3.8, 3.6, 3.8), lambda x, y, z, d: Y2 if (z > 9.6 and y < 5) else Y, 4)
    g.sphere((8, 9.2, 8.6), 3.0, lambda x, y, z, d: Y2 if d > 0.85 and y > 10.5 else Y, 5)
    g.tube([(8, 9.0, 11.4), (8, 8.6, 12.8)], [0.8, 0.2], OR, 7)
    _cute_eyes(g, (8, 9.8, 11.3), 1.25, size=0.9)
    _blush(g, (8, 8.6, 11.2), 1.9)
    for k in range(3):   # 머리털
        g.tube([(8, 11.8, 8.6), (8 + (k - 1) * 0.6, 13.0, 8.4 + (k - 1) * 0.2), (8 + (k - 1) * 0.9, 13.6, 7.8)], [0.35, 0.25, 0.08], WING, 7, smooth=True)
    for sx in (-1, 1):
        g.ellipsoid((8 + sx * 3.6, 4.8, 7.4), (0.7, 2.0, 2.4), WING, 6)   # 날개
        g.tube([(8 + sx * 1.2, 1.4, 8.0), (8 + sx * 1.2, 0.3, 8.2)], [0.3, 0.3], OR, 5)
        for k in range(3):
            g.tube([(8 + sx * 1.2, 0.3, 8.2), (8 + sx * 1.2 + (k - 1) * 0.6, 0.2, 9.4)], [0.25, 0.2], OR, 5)
    g.ellipsoid((8, 2.2, 4.0), (1.4, 1.0, 1.0), WING, 4)   # 꼬리
    g.ring((8, 11.4, 8.6), 2.5, 0.3, "ff6a8a", 8, axis="y")   # 리본 띠
    g.sphere((10.4, 11.8, 9.6), 0.7, "ff6a8a", 9)


def pet_bunny(g):
    W, W2, PINK, EYE = "fbf8f4", "e8e0d6", "ffb0c0", "c0304a"
    g.ellipsoid((8, 3.6, 7.2), (3.6, 3.4, 3.8), lambda x, y, z, d: tex(W, x, y, z, 0.04), 4)
    g.sphere((8, 8.2, 9.0), 2.9, W, 5)
    for sx in (-1, 1):
        ear = [(8 + sx * 1.0, 10.4, 8.6), (8 + sx * 1.4, 13.0, 8.0), (8 + sx * 1.8, 15.0, 7.0), (8 + sx * 2.6, 15.8, 5.8)]
        g.tube(ear, [0.9, 1.0, 0.8, 0.3], W, 6, smooth=True, squash=(1, 0.55))
        g.tube([add(p, (0, 0, 0.45)) for p in ear[:3]], [0.5, 0.55, 0.4], PINK, 7, smooth=True, squash=(1, 0.3))
        g.ellipsoid((8 + sx * 1.6, 1.0, 10.0), (0.9, 0.8, 1.4), W2, 5)   # 앞발
        g.ellipsoid((8 + sx * 2.4, 1.0, 5.6), (1.2, 0.9, 2.0), W2, 5)    # 뒷발
    _cute_eyes(g, (8, 8.6, 11.6), 1.25, col=EYE, size=0.95)
    g.ellipsoid((8, 7.4, 11.9), (0.45, 0.3, 0.25), "ff7a90", 10)
    _blush(g, (8, 7.4, 11.5), 2.0)
    g.sphere((8, 3.6, 3.2), 1.3, "ffffff", 5)   # 솜꼬리
    g.tube([(6.4, 10.6, 10.6), (9.6, 10.6, 10.6)], [0.3, 0.3], "6ad0ff", 8)   # 머리띠
    g.sphere((9.8, 10.9, 10.4), 0.6, "ff8ab0", 9)
    g.ellipsoid((8, 4.6, 10.6), (1.0, 1.0, 0.5), "ff8a2a", 7)   # 안고 있는 당근
    g.cone((8, 5.4, 10.8), (8, 6.6, 10.6), 0.4, 0.1, "4ab83a", 8)


def pet_fox(g):
    OR, OR2, CREAM, DARK, F1, F2 = "ff7a2a", "e86018", "fff0e0", "2a1a0a", "ffd23f", "fff6c0"
    g.ellipsoid((8, 4.2, 7.0), (2.8, 3.0, 3.6), lambda x, y, z, d: CREAM if z > 9 and y < 5.4 else tex(OR, x, y, z, 0.06), 4)
    hc = (8, 8.4, 9.4)
    g.ellipsoid(hc, (3.0, 2.6, 2.6), lambda x, y, z, d: CREAM if y < 8.0 and abs(x - 8) > 0.6 else OR, 5)
    g.tube([(8, 7.8, 11.4), (8, 7.4, 13.2)], [1.1, 0.5], CREAM, 6)
    g.dot(8, 7.6, 13.5, DARK, 9, 0.6)
    _cute_eyes(g, (8, 8.8, 11.6), 1.3, col=DARK, size=0.85)
    for sx in (-1, 1):
        g.cone((8 + sx * 1.6, 10.2, 9.0), (8 + sx * 2.6, 13.2, 8.4), 1.1, 0.12, OR, 6)
        g.cone((8 + sx * 1.6, 10.4, 9.4), (8 + sx * 2.4, 12.6, 8.9), 0.55, 0.06, DARK, 7)
        g.tube([(8 + sx * 1.4, 2.6, 9.0), (8 + sx * 1.4, 0.4, 9.6)], [0.6, 0.55], DARK, 5)
        g.tube([(8 + sx * 1.8, 3.0, 5.2), (8 + sx * 1.8, 0.4, 5.0)], [0.7, 0.6], DARK, 5)
    # 크고 복슬한 꼬리 + 불꽃 끝
    tail = [(8, 4.0, 3.8), (8.4, 6.0, 1.6), (9.4, 9.0, 1.4), (10.0, 11.4, 2.6)]
    g.tube(tail, [1.2, 1.9, 1.8, 1.2], lambda x, y, z, d, f: (CREAM if f > 2.2 else tex(OR, x, y, z, 0.06)), 5, smooth=True)
    for k in range(5):
        a = k / 5 * 2 * math.pi
        g.cone((10.0, 12.0, 2.6), (10.0 + math.cos(a) * 0.9, 14.2 + (k % 2) * 0.6, 2.6 + math.sin(a) * 0.9), 0.7, 0.06, F1 if k % 2 else "ff9a2a", 7)
    g.sphere((10.0, 12.6, 2.6), 0.6, F2, 9)
    g.ring((8, 6.4, 8.8), 2.0, 0.3, "3a8aff", 8, axis="y")   # 목 방울
    g.sphere((8, 5.8, 10.8), 0.55, "ffd23f", 9)


def pet_penguin(g):
    BK, BK2, WH, OR, SC = "1e2430", "2a3244", "f4f6fa", "ffa62a", "3fb0ff"
    g.ellipsoid((8, 6.4, 8), (3.8, 5.8, 3.6), lambda x, y, z, d: WH if (z > 9.4 and abs(x - 8) < 2.8 and y < 10.4) else tex(BK, x, y, z, 0.06), 4)
    g.ellipsoid((8, 12.4, 8.2), (3.0, 2.6, 2.8), lambda x, y, z, d: WH if (z > 9.6 and abs(x - 8) < 2.0 and y < 12.8) else BK, 5)
    _cute_eyes(g, (8, 12.2, 10.6), 1.0, size=0.8)
    g.cone((8, 11.6, 10.6), (8, 11.2, 12.2), 0.6, 0.1, OR, 8)
    _blush(g, (8, 11.4, 10.4), 1.7)
    for sx in (-1, 1):
        g.tube([(8 + sx * 3.4, 9.0, 8.0), (8 + sx * 4.4, 6.4, 8.2), (8 + sx * 4.8, 4.0, 8.6)], [0.9, 0.7, 0.3], BK2, 5, smooth=True, squash=(0.5, 1))
        g.ellipsoid((8 + sx * 1.4, 0.4, 9.4), (1.0, 0.4, 1.5), OR, 5)
    # 목도리 (휘날리는 끝)
    g.ring((8, 10.0, 8.1), 3.0, 0.5, SC, 7, axis="y")
    g.slab((10.0, 9.8, 10.6), (11.4, 7.0, 11.2), (1.0, 0, 0), 0.4, lambda x, y, z, u, v: "ffffff" if u > 0.8 else SC, 7)
    g.ellipsoid((8, 14.8, 8.2), (1.6, 0.8, 1.6), SC, 7)   # 털모자
    g.sphere((8, 15.8, 8.2), 0.8, "ffffff", 8)


def pet_owl(g):
    BR, BR2, CHEST, FACE, EYE, HAT, GOLD = "8a6a4a", "6a4a2a", "d8c0a0", "ecdcc4", "ffd23f", "3a2a6a", "ffd23f"
    g.ellipsoid((8, 5.4, 8), (3.8, 4.8, 3.4), lambda x, y, z, d: (CHEST if (z > 9.4 and y < 8) else tex(BR, x, y, z, 0.06)), 4)
    for k in range(6):   # 가슴 깃 무늬
        g.dot(6.6 + (k % 3) * 1.4, 4.0 + (k // 3) * 1.6, 11.2, BR2, 8, 0.45)
    g.ellipsoid((8, 11.0, 8.4), (3.6, 3.0, 3.0), BR, 5)
    g.ellipsoid((8, 11.0, 10.6), (3.0, 2.3, 0.9), FACE, 6)
    for sx in (-1, 1):
        g.sphere((8 + sx * 1.35, 11.3, 11.2), 1.1, EYE, 8)
        g.sphere((8 + sx * 1.35, 11.3, 11.9), 0.55, "1a1a1a", 9)
        g.dot(8 + sx * 1.35 - 0.2, 11.6, 12.3, "ffffff", 10, 0.3)
        g.cone((8 + sx * 2.4, 13.4, 8.6), (8 + sx * 3.2, 15.2, 8.0), 0.8, 0.1, BR2, 6)   # 귀깃
        g.ellipsoid((8 + sx * 3.6, 5.6, 7.6), (0.9, 3.4, 2.4), BR2, 6)   # 날개
        for k in range(3):
            g.tube([(8 + sx * 1.0, 0.8, 8.8), (8 + sx * (1.0 + (k - 1) * 0.5), 0.3, 10.0)], [0.25, 0.15], "c08a2a", 6)
    g.cone((8, 10.4, 11.4), (8, 9.6, 12.2), 0.5, 0.1, "c08a2a", 9)
    # 학자 모자 (사각 판 + 술) · 동그란 안경 · 책
    g.ellipsoid((8, 13.8, 8.4), (2.4, 0.9, 2.4), HAT, 7)
    g.box((5.0, 14.4, 5.4), (11.0, 14.9, 11.4), HAT, 8)
    g.tube([(8, 15.0, 8.4), (10.6, 15.0, 10.8), (10.8, 13.4, 11.0)], [0.15, 0.15, 0.15], GOLD, 9)
    g.sphere((10.8, 13.0, 11.0), 0.45, GOLD, 9)
    for sx in (-1, 1):
        g.ring((8 + sx * 1.35, 11.3, 11.9), 1.25, 0.15, GOLD, 9, axis="z")
    g.box((6.0, 2.4, 10.8), (10.0, 5.0, 12.0), lambda x, y, z: "a02a2a" if z > 11.5 or y > 4.7 or y < 2.7 else "f4ecd8", 7)   # 책


def pet_golem(g):
    ST, ST2, ST3, MOSS, RUNE, RUNE2 = "8a8a94", "6a6a74", "a4a4ae", "5a8a3a", "5ad8ff", "c8f8ff"
    g.ellipsoid((8, 7.4, 8), (4.6, 4.2, 3.4), lambda x, y, z, d: tex(ST, x, y, z, 0.1), 4)
    g.ellipsoid((8, 12.6, 8.4), (2.8, 2.2, 2.4), lambda x, y, z, d: tex(ST3, x, y, z, 0.08), 5)
    g.tube([(6.0, 12.8, 10.6), (10.0, 12.8, 10.6)], [0.35, 0.35], RUNE2, 9)   # 빛나는 눈 띠
    for sx in (-1, 1):
        g.dot(8 + sx * 1.0, 12.8, 10.9, "ffffff", 10, 0.5)
        # 큰 팔 + 주먹
        g.tube([(8 + sx * 4.4, 9.4, 8.0), (8 + sx * 5.6, 6.4, 8.6), (8 + sx * 5.8, 3.4, 9.2)], [1.6, 1.3, 1.2], lambda x, y, z, d, f: tex(ST2, x, y, z, 0.1), 5, smooth=True)
        g.sphere((8 + sx * 5.8, 2.6, 9.4), 1.8, lambda x, y, z, d: tex(ST2, x, y, z, 0.1), 6)
        g.tube([(8 + sx * 2.0, 3.6, 8.0), (8 + sx * 2.2, 0.8, 8.2)], [1.4, 1.5], ST2, 5)   # 다리
        g.dot(8 + sx * 5.8, 4.4, 10.4, RUNE, 9, 0.6)
    # 가슴 룬 (빛나는 원 + 십자)
    g.ring((8, 7.8, 11.2), 1.6, 0.25, RUNE, 9, axis="z")
    g.tube([(8, 6.0, 11.3), (8, 9.6, 11.3)], [0.25, 0.25], RUNE, 9)
    g.tube([(6.2, 7.8, 11.3), (9.8, 7.8, 11.3)], [0.25, 0.25], RUNE, 9)
    g.sphere((8, 7.8, 11.4), 0.5, RUNE2, 10)
    for k in range(7):   # 이끼 · 작은 꽃 · 버섯
        a = k / 7 * 2 * math.pi
        g.ellipsoid((8 + math.cos(a) * 2.6, 14.2 - abs(math.sin(a)) * 0.4, 8.4 + math.sin(a) * 1.6), (1.0, 0.4, 0.8), MOSS, 7)
    g.sphere((6.8, 15.0, 8.8), 0.5, "ff6ab0", 8)
    g.sphere((9.6, 15.0, 7.8), 0.45, "ffffff", 8)
    g.tube([(9.4, 14.6, 9.4), (9.4, 15.6, 9.4)], [0.2, 0.2], "e8dcc0", 8)
    g.ellipsoid((9.4, 15.8, 9.4), (0.8, 0.4, 0.8), "e04a3a", 9)
    for (x, y) in ((4.4, 9.4), (11.0, 6.0), (6.0, 5.0)):   # 금 · 균열
        g.tube([(x, y, 11.0), (x + 0.6, y - 0.8, 11.0), (x + 0.2, y - 1.6, 11.0)], [0.12, 0.12, 0.12], ST2, 8)


def pet_fairy(g):
    SKIN, DRESS, DRESS2, HAIR, WING, WING2, GLOW = "ffe0c8", "5affa0", "3ad880", "ffd23f", "c8fff0", "a8f0ff", "fffbe0"
    g.ellipsoid((8, 4.2, 8), (2.6, 2.6, 2.4), lambda x, y, z, d: DRESS2 if y < 2.6 else DRESS, 5)   # 꽃잎 치마
    for k in range(8):
        a = k / 8 * 2 * math.pi
        g.slab((8 + math.cos(a) * 1.6, 3.6, 8 + math.sin(a) * 1.6), (8 + math.cos(a) * 3.0, 1.6, 8 + math.sin(a) * 3.0), (-math.sin(a) * 1.4, 0, math.cos(a) * 1.4), 0.35,
               lambda x, y, z, u, v: DRESS if u < 0.7 else "c8ffd8", 5)
    g.ellipsoid((8, 7.0, 8), (1.5, 1.8, 1.3), DRESS, 5)
    g.sphere((8, 10.4, 8.2), 2.4, SKIN, 6)
    g.ellipsoid((8, 11.4, 7.6), (2.7, 2.0, 2.4), lambda x, y, z, d: HAIR if (z < 9.4 or y > 11.8) else None, 7)
    g.tube([(9.8, 11.4, 7.0), (10.6, 9.4, 6.6), (10.2, 7.6, 6.8)], [0.8, 0.6, 0.3], HAIR, 7, smooth=True)   # 옆 머리 다발
    _cute_eyes(g, (8, 10.2, 10.3), 0.95, col="2a8a6a", size=0.75)
    _blush(g, (8, 9.4, 10.2), 1.6)
    for k in range(5):   # 꽃 화관
        a = k / 5 * math.pi * 1.2 - 0.1
        g.sphere((8 + math.cos(a) * 2.2, 12.6, 8.2 + math.sin(a) * 0.8), 0.5, ("ff8ab0", "ffffff", "ffd23f")[k % 3], 9)
    for sx in (-1, 1):   # 반짝이는 날개 4장
        g.triangle((8 + sx * 0.8, 8.0, 6.6), (8 + sx * 6.4, 13.6, 5.4), (8 + sx * 5.6, 8.0, 5.6), lambda p, w: WING2 if w < 0.1 else WING, 4, scallop=0.12, thick=0.2)
        g.triangle((8 + sx * 0.8, 7.0, 6.6), (8 + sx * 4.4, 6.6, 5.8), (8 + sx * 3.4, 3.2, 6.0), lambda p, w: WING if w < 0.1 else WING2, 4, scallop=0.12, thick=0.2)
        g.tube([(8 + sx * 1.6, 6.8, 8.2), (8 + sx * 2.4, 5.6, 9.0)], [0.4, 0.35], SKIN, 6)
    g.tube([(10.4, 4.4, 9.4), (10.8, 9.6, 9.8)], [0.2, 0.2], "c8a060", 7)   # 지팡이 + 별
    for k in range(5):
        a = k / 5 * 2 * math.pi + math.pi / 2
        g.cone((10.8, 10.4, 9.8), (10.8 + math.cos(a) * 1.2, 10.4 + math.sin(a) * 1.2, 9.8), 0.4, 0.06, GLOW, 9)
    for (x, y, z) in ((3.0, 12.0, 8.0), (13.0, 10.0, 9.0), (5.0, 2.0, 10.0), (12.0, 3.0, 6.0)):
        g.dot(x, y, z, GLOW, 10, 0.4)


def pet_ghost(g):
    BODY, BODY2, EYE, PURP, PURP2 = "eef2ff", "c8d0f0", "2a2a3a", "c060ff", "f0d8ff"
    g.ellipsoid((8, 8.6, 8), (4.0, 4.6, 3.6), lambda x, y, z, d: BODY2 if d > 0.85 and y < 7 else BODY, 4)
    for k in range(6):   # 물결 자락 (아래로 갈수록 가늘게 꼬리처럼)
        a = k / 6 * 2 * math.pi
        b = (8 + math.cos(a) * 2.8, 5.0, 8 + math.sin(a) * 2.6)
        g.tube([b, add(b, (math.cos(a) * 0.6, -1.8, math.sin(a) * 0.6 - 0.4)), add(b, (math.cos(a + 0.6) * 1.0, -3.4, math.sin(a + 0.6) * 1.0 - 1.0))],
               [1.5, 1.0, 0.25], BODY, 4, smooth=True)
    _cute_eyes(g, (8, 9.8, 11.4), 1.4, col=EYE, size=1.2)
    g.ellipsoid((8, 7.6, 11.4), (0.8, 0.9, 0.3), "5a2a4a", 9)
    g.ellipsoid((8, 7.1, 11.6), (0.5, 0.35, 0.25), "ff7aa0", 10)
    _blush(g, (8, 8.4, 11.2), 2.4)
    for sx in (-1, 1):
        g.tube([(8 + sx * 3.6, 8.6, 8.6), (8 + sx * 4.8, 9.6, 9.6), (8 + sx * 5.0, 10.8, 10.0)], [0.9, 0.6, 0.4], BODY, 5, smooth=True)
    g.ring((8, 14.6, 8), 2.4, 0.3, PURP, 8, axis="y", tilt=0.25)   # 떠 있는 고리
    for (x, y, z) in ((2.6, 11.0, 7.0), (13.6, 6.0, 9.0), (12.0, 13.0, 6.0)):   # 도깨비불
        g.sphere((x, y, z), 0.7, lambda xx, yy, zz, d: PURP2 if d < 0.35 else PURP, 9)
        g.cone((x, y + 0.5, z), (x + 0.2, y + 1.8, z), 0.5, 0.05, PURP, 8)


def pet_phoenix(g):
    RED, RED2, ORA, YEL, GOLD, WHITE = "ff3a1a", "c81a10", "ff8a1f", "ffd23f", "fff3b0", "fffbe8"
    g.ellipsoid((8, 6.0, 8), (2.8, 3.0, 3.2), lambda x, y, z, d: YEL if (z > 10 and y < 6.5) else RED, 5)
    hc = (8, 10.2, 9.6)
    g.sphere(hc, 2.4, RED, 6)
    g.cone((8, 9.8, 11.6), (8, 9.0, 13.4), 0.7, 0.08, GOLD, 8)
    _cute_eyes(g, (8, 10.6, 11.6), 1.05, col="2a0a0a", size=0.75)
    for k in range(5):   # 불꽃 볏
        g.tube([(8, 12.2, 9.4), (8 + (k - 2) * 0.5, 14.0 + (2 - abs(k - 2)) * 0.6, 8.8 - k * 0.3), (8 + (k - 2) * 0.7, 15.0 + (2 - abs(k - 2)) * 0.8, 7.6 - k * 0.5)],
               [0.4, 0.3, 0.06], YEL if k % 2 else ORA, 8, smooth=True)
    for sx in (-1, 1):   # 펼친 불꽃 날개
        for k in range(6):
            t = k / 5
            base = (8 + sx * (2.4 + t * 1.2), 7.4 + t * 1.0, 7.6 - t * 0.6)
            tip = (8 + sx * (5.4 + t * 2.6), 7.8 + t * 5.6 - t * t * 1.6, 7.0 - t * 1.2)
            g.tube([base, ((base[0] + tip[0]) / 2, (base[1] + tip[1]) / 2 + 0.6, base[2]), tip], [0.8, 0.6, 0.08],
                   lambda x, y, z, d, f, t=t: mix(RED, mix(ORA, YEL, t), f / 2), 5, smooth=True, squash=(1, 0.5))
    for k, (dx, c) in enumerate(((-1.2, ORA), (0, YEL), (1.2, ORA))):   # 긴 꼬리 깃 (끝에 눈 무늬)
        pts = [(8 + dx * 0.4, 4.4, 5.2), (8 + dx, 2.4, 3.0), (8 + dx * 1.6, 0.8, 0.6), (8 + dx * 2.0, 0.6, -1.6)]
        g.tube(pts, [0.6, 0.6, 0.5, 0.2], c, 5, smooth=True, squash=(1, 0.4))
        g.ellipsoid(pts[-1], (0.8, 0.5, 0.8), lambda x, y, z, d: "40e0ff" if d < 0.3 else YEL, 7)
    for sx in (-1, 1):
        g.tube([(8 + sx * 0.9, 3.4, 8.6), (8 + sx * 0.9, 1.0, 9.0)], [0.25, 0.2], GOLD, 5)
    for (x, y, z) in ((3.0, 13.0, 9.0), (13.0, 12.0, 7.0), (11.0, 3.0, 11.0)):
        g.sphere((x, y, z), 0.45, WHITE, 10)


def pet_dragon(g):
    SC, SC2, BELLY, HORN, WING, EYE = "4a2a7a", "3a1a5a", "e8c870", "fff3b0", "8a4ab0", "ffd23f"
    g.ellipsoid((8, 4.8, 7.6), (3.2, 3.4, 3.4), lambda x, y, z, d: BELLY if (z > 9.6 and abs(x - 8) < 2.0) else tex(SC, x, y, z, 0.06), 4)
    for k in range(4):   # 배 줄
        g.tube([(6.6, 2.8 + k * 1.2, 10.9), (9.4, 2.8 + k * 1.2, 10.9)], [0.15, 0.15], hx(BELLY, 0.8), 7)
    hc = (8, 10.2, 9.0)
    g.ellipsoid(hc, (3.2, 2.8, 3.0), lambda x, y, z, d: tex(SC, x, y, z, 0.05), 5)
    g.ellipsoid((8, 9.2, 11.8), (1.9, 1.4, 1.4), SC, 6)   # 주둥이
    for sx in (-1, 1):
        g.dot(8 + sx * 0.7, 9.6, 13.1, SC2, 9, 0.35)
        g.tube([(8 + sx * 1.6, 12.4, 8.4), (8 + sx * 2.4, 14.0, 7.0), (8 + sx * 2.2, 15.0, 5.6)], [0.5, 0.35, 0.08], HORN, 7, smooth=True)
        g.cone((8 + sx * 3.0, 10.6, 8.6), (8 + sx * 4.2, 11.4, 7.8), 0.5, 0.05, WING, 6)   # 귀 지느러미
        # 작은 박쥐 날개
        root = (8 + sx * 2.4, 7.6, 5.6)
        tip = (8 + sx * 7.0, 11.6, 4.0)
        g.tube([root, tip], [0.4, 0.2], SC2, 6)
        g.triangle(root, tip, (8 + sx * 6.0, 6.0, 4.4), lambda p, w: WING if w < 0.8 else hx(WING, 0.8), 4, scallop=0.15, thick=0.2)
        g.triangle(tip, (8 + sx * 6.0, 6.0, 4.4), (8 + sx * 7.4, 8.0, 3.8), lambda p, w: WING, 4, scallop=0.15, thick=0.2)
        g.ellipsoid((8 + sx * 1.8, 1.0, 9.0), (1.0, 1.0, 1.3), SC2, 5)
        for k in range(3):
            g.dot(8 + sx * 1.8 + (k - 1) * 0.5, 0.5, 10.2, HORN, 8, 0.3)
    _cute_eyes(g, (8, 10.8, 11.4), 1.4, col=EYE, size=1.1)
    for sx in (-1, 1):
        g.ellipsoid((8 + sx * 1.4, 10.8, 11.9), (0.25, 0.6, 0.2), "1a0a1a", 12)   # 세로 동공
    for k in range(4):   # 등 가시
        g.cone((8, 11.0 - k * 1.8, 6.0 - k * 0.9), (8, 12.0 - k * 1.8, 5.0 - k * 0.9), 0.45, 0.06, HORN, 7)
    tail = [(8, 3.4, 4.6), (8.6, 2.0, 2.4), (10.2, 1.4, 1.0), (11.8, 2.2, 0.6)]
    g.tube(tail, [1.2, 0.9, 0.6, 0.25], SC, 5, smooth=True)
    g.triangle(tail[-1], add(tail[-1], (1.4, 1.2, 0)), add(tail[-1], (0.8, -0.8, 0)), lambda p, w: HORN, 6, thick=0.2)
    g.sphere((11.0, 6.6, 12.6), 0.9, lambda x, y, z, d: "ffffff" if d < 0.3 else "ff7a3a", 9)   # 입김 불꽃
    g.sphere((12.4, 7.2, 13.6), 0.6, "ffd23f", 9)


def pet_star(g):
    CORE, GLOW, TIP, TIP2 = "fff3b0", "ffd23f", "7fe8ff", "e8fbff"
    # 통통한 다섯 갈래 별 (앞을 봄)
    pts = []
    for k in range(10):
        a = k * math.pi / 5 + math.pi / 2
        r = 5.6 if k % 2 == 0 else 2.6
        pts.append((8 + math.cos(a) * r, 8 + math.sin(a) * r))

    def inside(x, y):
        c = False
        n = len(pts)
        for i in range(n):
            x1, y1 = pts[i]
            x2, y2 = pts[(i + 1) % n]
            if (y1 > y) != (y2 > y) and x < (x2 - x1) * (y - y1) / (y2 - y1) + x1:
                c = not c
        return c
    vs = VL.VS
    for i in range(int(1 / vs), int(16 / vs)):
        x = (i + 0.5) * vs
        for j in range(int(1 / vs), int(15 / vs)):
            y = (j + 0.5) * vs
            if not inside(x, y):
                continue
            dc = math.hypot(x - 8, y - 8)
            hz = max(0.4, 2.0 - dc * 0.3)
            for k in range(int((8 - hz) / vs), int((8 + hz) / vs) + 1):
                z = (k + 0.5) * vs
                g.put(x, y, z, TIP if dc > 4.4 else (GLOW if dc > 2.6 else CORE), 5)
    _cute_eyes(g, (8, 8.6, 10.1), 1.15, col="3a2a6a", size=0.9)
    g.tube([(7.2, 6.8, 10.0), (8.0, 6.4, 10.1), (8.8, 6.8, 10.0)], [0.2, 0.2, 0.2], "3a2a6a", 11, smooth=True)
    _blush(g, (8, 7.4, 9.9), 2.0)
    for k in range(6):   # 둘레를 도는 작은 별
        a = k * math.pi / 3
        x, z = 8 + math.cos(a) * 7.0, 8 + math.sin(a) * 4.0
        y = 13.0 + (k % 2) * 1.2
        for d in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            g.tube([(x, y, z), (x + d[0] * 0.9, y + d[1] * 0.9, z)], [0.35, 0.08], TIP2 if k % 2 else GLOW, 8)
    g.ring((8, 8, 8), 7.2, 0.25, TIP, 4, axis="y", tilt=0.35)


BUILDERS = {"mount_wolf": mount_wolf, "mount_lizard": mount_lizard, "mount_warhorse": mount_warhorse, "mount_icebear": mount_icebear,
            "mount_lion": mount_lion, "mount_panther": mount_panther, "mount_griffin": mount_griffin, "mount_dragon": mount_dragon,
            "pet_slime": pet_slime, "pet_chick": pet_chick, "pet_bunny": pet_bunny, "pet_fox": pet_fox, "pet_penguin": pet_penguin,
            "pet_owl": pet_owl, "pet_golem": pet_golem, "pet_fairy": pet_fairy, "pet_ghost": pet_ghost, "pet_phoenix": pet_phoenix,
            "pet_dragon": pet_dragon, "pet_star": pet_star}


def build(bid, m):
    """탈것 0.5 · 펫 0.35 복셀로 조각 → 모델 m 에 요소를 넣음"""
    old = VL.VS
    VL.VS = 0.5 if bid.startswith("mount_") else 0.35
    try:
        g = VL.Grid()
        BUILDERS[bid](g)
        if bid.startswith("mount_"):
            SADDLE[bid] = round(SADDLE.pop("_last"), 1)
        return VL.emit(g, m)
    finally:
        VL.VS = old
