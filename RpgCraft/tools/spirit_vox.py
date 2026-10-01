"""
v5.10.53 사신수 · 사흉수 장비 3D 모델 — 일반 무기 생성기 대신 하나씩 손으로 조각한 전용 모델 (히든 무기 hjw_vox 와 같은 방식).
 무기 (세로로 조각: 손잡이 ≈ y 0, 끝이 위 → z 축 -45° 로 대각선)
 - spirit_qinglong 청룡검 : 용 머리 가드의 벌린 입에서 뻗어 나온 청옥 대검 · 칼날을 휘감아 오르는 청룡 · 금빛 지느러미 · 끝의 여의주 · 구름
 - spirit_baihu    백호부 : 줄무늬 은빛 자루 · 거대한 초승달 양날 도끼 · 가운데 포효하는 백호 머리 · 날에 새긴 푸른 발톱 자국 · 번개
 - spirit_zhuque   주작검 : 불꽃 모양 단검 (붉은 → 주황 → 흰 심) · 날개를 편 주작 머리 가드 · 폼멜에서 흩날리는 긴 꼬리 깃 · 불티
 - spirit_xuanwu   현무창 : 흑옥 자루를 감고 오르는 흑사 · 육각 무늬 거북 등딱지 가드 · 물결 가지가 달린 비취 창날 · 청록 술
 방어구 (armor_vox 위에 덧붙이는 장식) — 청룡 뿔 · 백호 머리 · 주작 불꽃 깃 · 현무 등딱지, 사흉수 (혼돈 · 도철 · 도올 · 궁기) 각 부위 전용 장식
가장 밝은 색(채널 하나라도 240 이상)은 음영을 받지 않아 빛나 보인다.
"""
import math

import voxel_lib as VL
import hjw_vox as H
from hjw_vox import CX, CZ, mix, lit, hsh, column, extrude, star4

WEAPONS = ("spirit_qinglong", "spirit_baihu", "spirit_zhuque", "spirit_xuanwu")


def _cloud(g, x, y, s, col, col2, pri=8):
    """작은 상서로운 구름 (동글동글 세 덩이 + 말린 꼬리)"""
    for dx, dy, r in ((-1.0, 0, 0.9), (0, 0.5, 1.15), (1.1, 0.1, 0.85)):
        g.sphere((x + dx * s, y + dy * s, CZ), r * s, lambda xx, yy, zz, d: col if d < 0.7 else col2, pri)
    g.tube([(x + 1.8 * s, y, CZ), (x + 2.6 * s, y - 0.4 * s, CZ), (x + 2.4 * s, y - 1.0 * s, CZ)], [0.35 * s, 0.25 * s, 0.1], col2, pri, smooth=True)


# ====================================================================== 청룡검
def qinglong(g):
    JADE, JADE2, JADE_D = "3a8cf0", "7ac4ff", "1c3f8a"
    EDGE, GLOW = "e8f8ff", "8ff4ff"
    GOLD, GOLD_H, GOLD_D = "e0a828", "ffe27a", "8a5a10"
    SCALE, SCALE2, BELLY = "2a64d0", "18408e", "f0dca0"
    # 손잡이: 금실로 감은 청색 비단
    column(g, -6.0, 2.0, lambda y: 0.85, lambda x, y, z, a: GOLD if int(y * 2.2 + a * 0.7) % 3 == 0 else "1e3a80", 4)
    # 폼멜: 용 꼬리 지느러미 + 금 고리
    column(g, -7.0, -6.0, lambda y: 1.1, lambda *a: GOLD_H, 5)
    for sd in (-1, 1):
        g.tube([(CX, -7.0, CZ), (CX + sd * 1.6, -8.6, CZ), (CX + sd * 2.8, -9.4, CZ), (CX + sd * 2.2, -10.6, CZ)], [0.7, 0.55, 0.35, 0.08],
               lambda x, y, z, d, f: GOLD_H if f < 1.2 else GOLD, 5, smooth=True, squash=(1, 0.45))
    g.sphere((CX, -7.8, CZ), 0.85, lambda x, y, z, d: GLOW if d < 0.4 else JADE2, 7)
    # 가드: 용 머리 (입을 벌려 칼날을 뿜어 냄) — 위턱 +z, 아래턱 -z 로 칼날 옆에서 봐도 보이게 x 쪽으로 긴 머리
    HY = 4.0
    g.ellipsoid((CX, HY, CZ), (3.0, 2.0, 1.9), lambda x, y, z, d: SCALE if (x * 2 + y * 3) % 2 < 1.2 else SCALE2, 5)
    for sd in (-1, 1):
        # 주둥이 · 턱 (양옆으로 길게)
        g.tube([(CX + sd * 2.2, HY + 0.4, CZ), (CX + sd * 4.6, HY + 0.9, CZ), (CX + sd * 5.6, HY + 0.6, CZ)], [1.3, 0.95, 0.6], SCALE, 5, smooth=True)
        g.tube([(CX + sd * 2.2, HY - 0.8, CZ), (CX + sd * 4.2, HY - 1.6, CZ), (CX + sd * 5.0, HY - 1.4, CZ)], [0.9, 0.65, 0.4], BELLY, 5, smooth=True)
        for k in range(4):   # 이빨
            g.cone((CX + sd * (2.8 + k * 0.7), HY + 0.0, CZ), (CX + sd * (2.9 + k * 0.7), HY - 0.8, CZ), 0.3, 0.06, "fffaf0", 7)
        g.dot(CX + sd * 2.4, HY + 1.2, CZ + 1.5, "ffee66", 9, 0.7)   # 눈
        g.dot(CX + sd * 2.4, HY + 1.2, CZ - 1.5, "ffee66", 9, 0.7)
        # 녹각 같은 뿔 (갈래 둘)
        g.tube([(CX + sd * 1.2, HY + 1.6, CZ), (CX + sd * 0.4, HY + 4.0, CZ - 0.4), (CX - sd * 0.8, HY + 6.4, CZ - 0.6), (CX - sd * 2.4, HY + 7.6, CZ - 0.6)],
               [0.65, 0.5, 0.35, 0.08], lambda x, y, z, d, f: mix(GOLD_H, GOLD, f / 3), 6, smooth=True)
        g.tube([(CX + sd * 0.6, HY + 4.0, CZ - 0.4), (CX + sd * 2.0, HY + 5.6, CZ - 0.5), (CX + sd * 2.0, HY + 6.8, CZ - 0.5)], [0.4, 0.25, 0.06], GOLD, 6, smooth=True)
        # 수염 (금빛, 아래로 길게 휘날림)
        g.tube([(CX + sd * 5.2, HY + 0.2, CZ), (CX + sd * 6.6, HY - 1.0, CZ + 0.3), (CX + sd * 6.8, HY - 3.4, CZ + 0.5), (CX + sd * 5.8, HY - 5.2, CZ + 0.4), (CX + sd * 6.4, HY - 6.6, CZ + 0.2)],
               [0.35, 0.3, 0.25, 0.2, 0.06], lambda x, y, z, d, f: GOLD_H if f < 2 else GOLD, 6, smooth=True)
        # 갈기 (머리 뒤쪽 불꽃 같은 지느러미)
        for k in range(4):
            g.cone((CX + sd * (0.6 + k * 0.6), HY + 0.8, CZ - 1.2), (CX + sd * (1.4 + k * 1.0), HY - 1.6 - k * 0.5, CZ - 1.9), 0.5, 0.06, GOLD if k % 2 else GOLD_H, 4)
    # 칼날: 청옥 대검 (가운데 빛나는 홈, 흰 날)
    def blade(x, y):
        if y < HY + 1.0 or y > 31.0:
            return None
        t = (y - (HY + 1.0)) / (31.0 - HY - 1.0)
        w = 2.05 * (1 - max(0.0, t - 0.78) / 0.22) ** 0.8 + (0.35 if t < 0.12 else 0)
        if t > 0.97:
            w = 0.3
        if abs(x - CX) > w:
            return None
        u = abs(x - CX) / max(0.01, w)
        hz = 0.75 * (1 - u * 0.7)

        def col(x, y, z, wz, u=u, t=t):
            if u > 0.82:
                return EDGE
            if u < 0.18 and 0.05 < t < 0.85:
                return GLOW if int(y * 1.6) % 4 else "ffffff"
            if u < 0.3:
                return JADE_D
            return JADE2 if u > 0.65 else JADE
        return hz, col
    extrude(g, CX - 3, CX + 3, HY, 31.5, blade, 4)
    # 칼날을 휘감아 오르는 청룡 (나선, 배는 금빛)
    pts = []
    for k in range(15):
        t = k / 14
        a = t * math.pi * 3.2 + 0.6
        y = HY + 2.0 + t * 21.0
        r = 3.0 - t * 0.9
        pts.append((CX + math.cos(a) * r, y, CZ + math.sin(a) * r * 0.9))
    radii = [1.05 - 0.6 * (k / 14) ** 1.2 for k in range(15)]

    def body(x, y, z, d, f):
        if d > 0.78 and z > CZ and hsh(round(f * 6)) > 0.2:
            return BELLY
        return SCALE if (int(f * 7) + int(y)) % 2 else SCALE2
    g.tube(pts, radii, body, 6, smooth=True)
    for k in range(1, 14):   # 등지느러미 (금)
        p, q = pts[k], pts[k + 1]
        nx, nz = p[0] - CX, p[2] - CZ
        L = math.hypot(nx, nz) or 1
        g.cone(p, (p[0] + nx / L * 1.1, p[1] + 0.9, p[2] + nz / L * 1.1), 0.42 * radii[k] + 0.1, 0.06, GOLD_H if k % 2 else GOLD, 7)
    # 용 머리 (나선 끝, 끝 쪽을 바라봄)
    hx, hy, hz = pts[-1]
    g.ellipsoid((hx, hy + 0.6, hz), (1.0, 1.3, 1.0), SCALE, 7)
    g.tube([(hx, hy + 1.2, hz), (hx + 0.3, hy + 2.6, hz + 0.3)], [0.75, 0.45], SCALE, 7)
    g.dot(hx + 0.6, hy + 1.2, hz + 0.6, "ffee66", 9, 0.5)
    for sd in (-1, 1):
        g.tube([(hx + sd * 0.4, hy + 1.4, hz - 0.4), (hx + sd * 1.2, hy + 2.6, hz - 0.9), (hx + sd * 1.0, hy + 3.4, hz - 1.4)], [0.3, 0.2, 0.05], GOLD_H, 8, smooth=True)
    # 여의주 (끝에 떠 있는 빛나는 구슬 + 불꽃 고리)
    g.sphere((CX, 34.0, CZ), 1.4, lambda x, y, z, d: "ffffff" if d < 0.25 else GLOW if d < 0.6 else "60d8ff", 9)
    for k in range(6):
        a = k * math.pi / 3
        g.cone((CX + math.cos(a) * 1.4, 34.0 + math.sin(a) * 1.4, CZ), (CX + math.cos(a + 0.5) * 2.6, 34.0 + math.sin(a + 0.5) * 2.6, CZ), 0.35, 0.05, GLOW, 8)
    # 구름
    _cloud(g, CX - 4.6, 12.0, 0.9, "f4fbff", "a8d8ff")
    _cloud(g, CX + 4.4, 21.0, 0.8, "f4fbff", "a8d8ff")
    star4(g, (CX - 3.4, 28.0, CZ), 1.2, GLOW, "ffffff")
    star4(g, (CX + 3.6, 9.0, CZ), 0.9, GLOW, "ffffff")


# ====================================================================== 백호부
def baihu(g):
    FUR, FUR2, STRIPE = "f4f4f6", "d6d8de", "1c1c24"
    STEEL, STEEL_H, STEEL_D = "b8c4d4", "eef4ff", "6a7486"
    BOLT, BOLT2 = "8fe0ff", "e8fbff"
    EYE = "4ad0ff"
    # 자루: 은빛 · 검은 줄무늬
    column(g, -10.0, 22.0, lambda y: 0.8, lambda x, y, z, a: STRIPE if (y * 0.9 + math.sin(a * 2) * 0.4) % 3.2 < 0.7 else FUR2, 3)
    column(g, -3.0, 3.0, lambda y: 0.95, lambda x, y, z, a: "2a3a5a" if int(y * 2.4 + a * 0.6) % 2 else "1a2440", 4)
    for yy in (-3.3, 3.3, 9.0):
        column(g, yy - 0.35, yy + 0.35, lambda y: 1.15, lambda *a: STEEL_H, 5)
    # 아래 끝: 호랑이 발톱 장식
    column(g, -11.0, -10.0, lambda y: 1.1, lambda *a: STEEL_H, 5)
    for k in range(3):
        a = (k - 1) * 0.6
        g.tube([(CX, -11.0, CZ), (CX + math.sin(a) * 1.4, -12.4, CZ), (CX + math.sin(a) * 1.8, -13.8, CZ + 0.4)], [0.5, 0.35, 0.06], "fffaf0", 6, smooth=True)
    # 도끼날: 큰 초승달 (-x 쪽) + 작은 날 (+x 쪽)
    HY = 18.0

    def bit(x, y, side, R, span):
        dx = (x - CX) * side
        if dx < 0.6:
            return None
        dy = y - HY
        # 바깥 날 원호: 중심 (CX - side*(R-span), HY)
        rr = math.hypot(dx + (R - span), dy)
        if rr > R:
            return None
        hh = 2.6 + dx * 1.0   # 자루에서 멀어질수록 위아래로 넓어짐
        if abs(dy) > hh:
            return None
        u = (R - rr) / max(0.01, R)   # 0 날 → 1 안쪽
        hz = 0.25 + 0.65 * min(1.0, u * 4)

        def col(x, y, z, wz, u=u, dx=dx, dy=dy):
            if u < 0.035:
                return BOLT2
            if u < 0.07:
                return STEEL_H
            # 발톱 자국 (빛나는 세 줄)
            if side < 0 and abs(wz) > 0.6:
                for k in range(3):
                    if abs((dy - (k - 1) * 2.2) - (dx - span * 0.55) * 0.55) < 0.42 and 2.0 < dx < span - 1.0:
                        return BOLT
            if abs(dx - 1.4) < 0.3:
                return STEEL_D
            return STEEL if (dx + dy * 0.3) % 2.4 > 0.4 else lit(STEEL, 0.9)
        return hz, col
    extrude(g, CX - 13.0, CX - 0.5, HY - 13, HY + 13, lambda x, y: bit(x, y, -1, 12.5, 12.0), 4)
    extrude(g, CX + 0.5, CX + 7.0, HY - 7, HY + 7, lambda x, y: bit(x, y, 1, 7.5, 5.8), 4)
    # 날 뿌리 받침 (금속 띠 + 리벳)
    g.box((CX - 1.6, HY - 2.6, CZ - 1.0), (CX + 1.6, HY + 2.6, CZ + 1.0), lambda x, y, z: STEEL_H if abs(y - HY) > 2.1 else STEEL_D, 5)
    # 백호 머리 (날 가운데, 앞을 보고 포효)
    TX, TY, TZ = CX, HY + 4.6, CZ + 0.6
    g.ellipsoid((TX, TY, TZ), (2.9, 2.6, 2.3), lambda x, y, z, d: STRIPE if (abs(x - TX) > 1.0 and int((y - TY) * 1.6 + abs(x - TX) * 0.8) % 3 == 0 and d > 0.5) else FUR, 6)
    g.ellipsoid((TX, TY - 1.0, TZ + 1.7), (1.4, 1.0, 1.0), FUR2, 7)   # 주둥이
    g.ellipsoid((TX, TY - 1.9, TZ + 1.5), (1.1, 0.55, 0.8), "3a1a22", 7)   # 벌린 입
    g.dot(TX, TY - 0.6, TZ + 2.7, "2a2a30", 8, 0.6)   # 코
    for sd in (-1, 1):
        g.dot(TX + sd * 1.0, TY + 0.5, TZ + 1.9, EYE, 10, 0.6)
        g.tube([(TX + sd * 1.4, TY + 0.9, TZ + 1.6), (TX + sd * 0.6, TY + 1.3, TZ + 2.0)], [0.15, 0.15], STRIPE, 8)   # 눈썹 줄무늬
        g.cone((TX + sd * 1.7, TY + 1.8, TZ - 0.2), (TX + sd * 2.3, TY + 3.2, TZ - 0.3), 0.8, 0.1, FUR, 7)   # 귀
        g.dot(TX + sd * 2.0, TY + 2.3, TZ + 0.2, "f0a8b8", 8, 0.45)
        g.cone((TX + sd * 0.55, TY - 1.5, TZ + 2.1), (TX + sd * 0.55, TY - 2.6, TZ + 2.1), 0.3, 0.05, "ffffff", 9)   # 송곳니
        for k in range(3):   # 수염 · 갈기 털
            g.cone((TX + sd * 2.0, TY - 0.8 + k * 0.7, TZ), (TX + sd * (3.6 + k * 0.3), TY - 1.8 + k * 0.9, TZ - 0.4), 0.55, 0.06, FUR2 if k % 2 else FUR, 6)
    for k in range(3):   # 이마 王 줄무늬
        g.box((TX - 0.8, TY + 1.0 + k * 0.45, TZ + 2.0), (TX + 0.8, TY + 1.2 + k * 0.45, TZ + 2.4), STRIPE, 9)
    # 날 위 창끝 (도끼 꼭대기)
    g.cone((CX, HY + 6.8, CZ), (CX, HY + 12.0, CZ), 1.1, 0.06, lambda x, y, z, f: STEEL_H if f > 0.5 else STEEL, 5)
    # 번개 (도끼 둘레)
    for (pts, w) in (([(CX - 11.0, HY + 8.0), (CX - 9.2, HY + 6.0), (CX - 10.2, HY + 5.4), (CX - 8.0, HY + 2.6)], 0.32),
                     ([(CX + 6.6, HY - 5.0), (CX + 7.8, HY - 7.0), (CX + 6.8, HY - 7.4), (CX + 8.2, HY - 9.6)], 0.28),
                     ([(CX - 3.0, HY + 12.0), (CX - 1.6, HY + 10.6), (CX - 2.4, HY + 10.2), (CX - 1.0, HY + 8.6)], 0.26)):
        g.tube([(x, y, CZ) for x, y in pts], [w] * len(pts), lambda x, y, z, d, f: BOLT2 if d < 0.5 else BOLT, 9)
    star4(g, (CX + 4.4, HY + 8.4, CZ), 1.0, BOLT, "ffffff")
    star4(g, (CX - 6.0, HY - 9.4, CZ), 0.9, BOLT, "ffffff")


# ====================================================================== 주작검
def zhuque(g):
    RED, RED2, RED_D = "e0302a", "ff5a2a", "8a1414"
    ORA, YEL, WHITE = "ff9a2a", "ffd84a", "fff8e0"
    GOLD, GOLD_H = "e0a020", "ffe070"
    # 손잡이 · 가드 아래
    column(g, -4.0, 1.4, lambda y: 0.8, lambda x, y, z, a: GOLD if int(y * 2.6 + a * 0.6) % 3 == 0 else "6a1010", 4)
    column(g, -4.8, -4.0, lambda y: 1.05, lambda *a: GOLD_H, 5)
    # 폼멜에서 흩날리는 긴 꼬리 깃 (세 갈래, 끝에 눈 무늬)
    for k, (dx, ln) in enumerate(((-1, 8.6), (0, 10.4), (1, 8.0))):
        pts = [(CX, -4.8, CZ), (CX + dx * 1.4, -7.4, CZ), (CX + dx * 2.6 + 0.8, -10.0, CZ), (CX + dx * 3.0 + 2.2, -4.8 - ln, CZ)]
        g.tube(pts, [0.55, 0.65, 0.75, 0.15], lambda x, y, z, d, f: mix(RED, ORA, f / 3) if d < 0.75 else RED_D, 5, smooth=True, squash=(1, 0.4))
        ex, ey = pts[-1][0] - 0.3, pts[-1][1] + 1.6
        g.ellipsoid((ex, ey, CZ), (1.0, 1.4, 0.4), lambda x, y, z, d: "40e0ff" if d < 0.25 else YEL if d < 0.6 else RED, 7)
    # 가드: 날개를 활짝 편 주작
    HY = 2.6
    g.ellipsoid((CX, HY, CZ), (2.0, 1.7, 1.5), RED, 6)
    g.ellipsoid((CX, HY + 1.9, CZ + 0.4), (1.0, 1.1, 1.0), RED2, 7)   # 머리
    g.cone((CX, HY + 1.9, CZ + 1.2), (CX, HY + 1.5, CZ + 2.6), 0.5, 0.06, GOLD_H, 8)   # 부리
    for sd in (-1, 1):
        g.dot(CX + sd * 0.55, HY + 2.3, CZ + 1.0, YEL, 9, 0.4)
    for k in range(3):   # 머리 깃 (불꽃 볏)
        g.cone((CX, HY + 2.6, CZ - 0.2), (CX + (k - 1) * 0.9, HY + 4.6 + (1 - abs(k - 1)) * 0.8, CZ - 0.6), 0.45, 0.05, YEL if k == 1 else ORA, 8)
    for sd in (-1, 1):
        for k in range(9):   # 날개 깃 (안쪽 짧고 바깥 길게, 위로 휨)
            t = k / 8
            base = (CX + sd * (1.2 + t * 1.6), HY + 0.4 + t * 0.6, CZ - 0.2)
            tip = (CX + sd * (4.0 + t * 3.6), HY + 1.6 + t * 4.2 - (t ** 2) * 1.2, CZ - 0.2)
            g.tube([base, ((base[0] + tip[0]) / 2, (base[1] + tip[1]) / 2 + 0.6, CZ - 0.2), tip], [1.05, 0.85, 0.12],
                   lambda x, y, z, d, f, t=t: mix(RED, mix(ORA, YEL, t), f / 2) if d < 0.8 else RED_D, 5, smooth=True, squash=(1, 0.5))
        g.tube([(CX + sd * 1.0, HY + 0.6, CZ + 0.2), (CX + sd * 3.6, HY + 1.4, CZ + 0.2), (CX + sd * 7.4, HY + 5.4, CZ + 0.2)], [0.35, 0.3, 0.06], GOLD_H, 6, smooth=True)
    # 칼날: 불꽃 모양 (물결치며 좁아짐) — 가운데가 흰 불
    def blade(x, y):
        if y < HY + 2.6 or y > 24.0:
            return None
        t = (y - HY - 2.6) / (24.0 - HY - 2.6)
        wav = math.sin(t * math.pi * 3.0) * 0.55 * (1 - t)
        w = 1.9 * (1 - t ** 1.6) + 0.15
        c = CX + wav
        if abs(x - c) > w:
            return None
        u = abs(x - c) / max(0.01, w)
        hz = 0.65 * (1 - u * 0.75)

        def col(x, y, z, wz, u=u, t=t):
            if u < 0.22 and t < 0.9:
                return WHITE
            if u < 0.45:
                return YEL
            if u < 0.75:
                return ORA
            return RED2 if (y * 1.3) % 2 > 0.6 else RED
        return hz, col
    extrude(g, CX - 3, CX + 3, HY + 2, 24.5, blade, 4)
    # 날 옆 불꽃 혀 (번갈아 솟음)
    for k in range(5):
        t = k / 4
        y = HY + 5.0 + t * 13.0
        sd = -1 if k % 2 else 1
        w = 1.9 * (1 - ((y - HY - 2.6) / (24.0 - HY - 2.6)) ** 1.6)
        g.tube([(CX + sd * w, y, CZ), (CX + sd * (w + 1.2), y + 1.4, CZ), (CX + sd * (w + 0.8), y + 3.0, CZ)], [0.55, 0.4, 0.06],
               lambda x, y, z, d, f: YEL if f > 1.2 else ORA, 5, smooth=True, squash=(1, 0.5))
    # 불티
    for (x, y, s) in ((CX - 4.0, 16.0, 0.55), (CX + 3.6, 21.0, 0.5), (CX - 2.6, 26.0, 0.45), (CX + 4.8, 11.0, 0.5), (CX + 1.4, 27.6, 0.4)):
        g.sphere((x, y, CZ), s, lambda xx, yy, zz, d: WHITE if d < 0.4 else YEL, 10)
        g.cone((x, y + s * 0.6, CZ), (x + 0.2, y + s * 2.8, CZ), s * 0.55, 0.05, ORA, 9)


# ====================================================================== 현무창
def xuanwu(g):
    OBS, OBS2 = "1a2a26", "26403a"
    SNAKE, SNAKE2, SBELLY = "203a30", "2e5a46", "c8d8a0"
    SHELL, SHELL2, SHELL_D = "3a8a50", "5ab86a", "1e4a2a"
    GOLD, GOLD_H = "c9a13b", "ffe07a"
    JADE, JADE2, ICE = "40d0a0", "a0ffd8", "eafff6"
    # 자루: 흑옥
    column(g, -12.0, 22.0, lambda y: 0.75, lambda x, y, z, a: OBS2 if (y * 1.5) % 2 < 1 else OBS, 3)
    column(g, -3.0, 3.0, lambda y: 0.9, lambda x, y, z, a: "2a5040" if int(y * 2.4 + a * 0.6) % 2 else "16302a", 4)
    for yy in (-3.3, 3.3):
        column(g, yy - 0.35, yy + 0.35, lambda y: 1.1, lambda *a: GOLD_H, 5)
    column(g, -13.0, -12.0, lambda y: 1.0, lambda *a: GOLD, 5)
    g.cone((CX, -13.0, CZ), (CX, -15.4, CZ), 0.9, 0.08, GOLD_H, 5)   # 물미
    # 자루를 감아 오르는 흑사 (뱀)
    pts = []
    for k in range(17):
        t = k / 16
        a = t * math.pi * 4.6
        pts.append((CX + math.cos(a) * 1.25, -10.0 + t * 29.0, CZ + math.sin(a) * 1.25))
    g.tube(pts, [0.42 + 0.18 * math.sin(k / 16 * math.pi) for k in range(17)],
           lambda x, y, z, d, f: SBELLY if (d > 0.8 and z > CZ + 0.8) else (SNAKE2 if int(f * 3) % 2 else SNAKE), 5, smooth=True)
    # 뱀 머리 (거북 등 아래에서 고개를 들고 앞을 노려봄)
    sx, sy, sz = pts[-1]
    g.ellipsoid((sx + 0.6, sy + 0.4, sz + 0.8), (1.0, 0.75, 1.2), SNAKE2, 7)
    g.dot(sx + 1.1, sy + 0.8, sz + 1.6, "ff4040", 10, 0.45)
    g.dot(sx + 0.1, sy + 0.8, sz + 1.6, "ff4040", 10, 0.45)
    g.tube([(sx + 0.6, sy, sz + 1.9), (sx + 0.6, sy - 0.4, sz + 2.8), (sx + 0.2, sy - 0.5, sz + 3.2)], [0.12, 0.12, 0.05], "ff6060", 9)   # 혀
    # 가드: 거북 등딱지 (육각 무늬 · 금 테)
    GY = 22.5

    def shell_col(x, y, z, d):
        if d > 0.86:
            return GOLD
        hx = (x - CX) * 1.1
        hy = (y - GY) * 1.6 + (z - CZ) * 0.6
        q = round(hy / 1.6)
        r = round((hx - (q % 2) * 0.9) / 1.8)
        cxh, cyh = r * 1.8 + (q % 2) * 0.9, q * 1.6
        e = max(abs(hx - cxh) / 0.9, abs(hy - cyh) / 0.8)
        return SHELL_D if e > 0.82 else (SHELL2 if (q + r) % 2 else SHELL)
    g.ellipsoid((CX, GY, CZ), (5.0, 2.4, 3.6), shell_col, 5)
    for k in range(10):   # 테두리 톱니
        a = k * math.pi / 5
        g.cone((CX + math.cos(a) * 4.0, GY - 0.6, CZ + math.sin(a) * 2.9), (CX + math.cos(a) * 5.2, GY - 1.0, CZ + math.sin(a) * 3.8), 0.5, 0.08, GOLD_H, 6)
    g.sphere((CX, GY + 1.8, CZ + 0.4), 0.85, lambda x, y, z, d: ICE if d < 0.3 else JADE, 8)   # 등 위 보옥
    # 창날: 비취 · 얼음 (넓은 잎 + 물결 가지 두 쌍)
    def head(x, y):
        y0, y1 = GY + 1.6, 38.0
        if y < y0 or y > y1:
            return None
        t = (y - y0) / (y1 - y0)
        w = 3.4 * math.sin(min(1.0, t * 1.25) * math.pi * 0.62) * (1 - t ** 3) + 0.25
        if abs(x - CX) > w:
            return None
        u = abs(x - CX) / max(0.01, w)
        hz = 0.7 * (1 - u * 0.7)

        def col(x, y, z, wz, u=u, t=t):
            if u > 0.82:
                return ICE
            if u < 0.16:
                return JADE2 if int(y * 1.4) % 3 else ICE
            return JADE if (u + t * 3) % 0.8 > 0.2 else lit(JADE, 0.85)
        return hz, col
    extrude(g, CX - 4.5, CX + 4.5, GY + 1, 38.5, head, 5)
    for sd in (-1, 1):
        for (by, ln) in ((GY + 3.0, 6.4), (GY + 7.4, 4.8)):   # 물결 가지 (말려 올라감)
            g.tube([(CX + sd * 1.4, by, CZ), (CX + sd * (ln - 0.6), by + 0.6, CZ), (CX + sd * ln, by + 2.2, CZ), (CX + sd * (ln - 1.2), by + 3.0, CZ), (CX + sd * (ln - 1.6), by + 2.2, CZ)],
                   [0.6, 0.5, 0.35, 0.22, 0.08], lambda x, y, z, d, f: ICE if d > 0.7 else JADE, 5, smooth=True, squash=(1, 0.55))
    # 청록 술 (거북 등 아래로)
    for k in range(7):
        a = (k - 3) * 0.28
        g.tube([(CX, GY - 1.6, CZ), (CX + math.sin(a) * 2.2, GY - 4.0, CZ + math.cos(a) * 0.6), (CX + math.sin(a) * 3.0, GY - 6.8, CZ + math.cos(a) * 0.4)],
               [0.4, 0.35, 0.12], lambda x, y, z, d, f: "30c0b0" if f < 1.4 else "70f0e0", 4, smooth=True)
    # 물방울 · 얼음 조각
    for (x, y, s) in ((CX - 4.6, 31.0, 0.5), (CX + 4.4, 34.0, 0.45), (CX - 3.0, 40.0, 0.4), (CX + 5.2, 27.0, 0.45)):
        g.sphere((x, y, CZ), s, lambda xx, yy, zz, d: ICE if d < 0.5 else JADE2, 10)
    star4(g, (CX + 2.6, 40.6, CZ), 1.0, JADE2, ICE)


BUILDERS = {"spirit_qinglong": qinglong, "spirit_baihu": baihu, "spirit_zhuque": zhuque, "spirit_xuanwu": xuanwu}


def build(name):
    """무기 하나 → (요소, 팔레트 색, display). hjw_vox 와 같은 방식"""
    H.BUILDERS[name] = BUILDERS[name]
    try:
        return H.build(name)
    finally:
        H.BUILDERS.pop(name, None)   # hjw_vox.write 가 히든 무기만 돌도록


def model_json(texture, els, disp):
    return {"credit": "RpgCraft spirit weapon", "texture_size": [16, 16], "gui_light": "front",
            "textures": {"0": texture, "particle": texture}, "elements": els, "display": disp}


# ====================================================================== 방어구 장식 (armor_vox 위에 덧붙임)
def _dragon_horns(g, gold, gold_h, y=11.0):
    for sx in (-1, 1):   # 사슴뿔 모양 두 갈래
        main = [(8 + sx * 3.6, y, 7.6), (8 + sx * 5.2, y + 2.0, 6.6), (8 + sx * 5.6, y + 4.6, 5.0), (8 + sx * 4.8, y + 6.6, 3.6)]
        g.tube(main, [0.75, 0.6, 0.45, 0.1], lambda x, yy, z, d, f: mix(gold_h, gold, f / 3), 7, smooth=True)
        g.tube([(8 + sx * 5.2, y + 2.2, 6.6), (8 + sx * 6.8, y + 3.2, 7.0), (8 + sx * 7.2, y + 4.6, 7.4)], [0.45, 0.3, 0.08], gold, 7, smooth=True)
        g.tube([(8 + sx * 5.5, y + 4.2, 5.2), (8 + sx * 6.6, y + 5.6, 4.8)], [0.35, 0.08], gold_h, 7)


def _whiskers(g, y, z, col, sx_list=(-1, 1)):
    for sx in sx_list:
        g.tube([(8 + sx * 2.4, y, z), (8 + sx * 5.0, y - 0.8, z + 0.6), (8 + sx * 6.6, y - 3.0, z + 0.2), (8 + sx * 6.0, y - 5.4, z - 0.4)],
               [0.3, 0.25, 0.2, 0.06], col, 7, smooth=True)


def _feather_fan(g, base, n, spread, length, cols, z_back=-1.0, pri=6, up=1.0):
    """base 에서 부채꼴로 뻗는 불꽃 깃털 (위쪽 반원)"""
    bx, by, bz = base
    for k in range(n):
        a = math.pi / 2 + (k - (n - 1) / 2) * spread
        ln = length * (1 - abs(k - (n - 1) / 2) / n * 0.5)
        tip = (bx + math.cos(a) * ln, by + math.sin(a) * ln * up, bz + z_back)
        mid = (bx + math.cos(a) * ln * 0.5, by + math.sin(a) * ln * 0.55 * up, bz + z_back * 0.5)
        g.tube([base, mid, tip], [0.7, 0.6, 0.08], lambda x, y, z, d, f: mix(cols[0], cols[1], f / 2) if d < 0.75 else cols[2], pri, smooth=True, squash=(1, 0.45))


def _wing(g, root, sx, span, rise, cols, pri=3, n=6, z=None):
    """등 뒤 날개 (깃 n 개, 바깥으로 길어짐)"""
    rx, ry, rz = root
    zz = rz if z is None else z
    for k in range(n):
        t = k / (n - 1)
        tip = (rx + sx * span * (0.45 + 0.55 * t), ry + rise * (1 - t) - 2.0 * t, zz - 0.6 * t)
        mid = (rx + sx * span * 0.35 * (0.6 + t), ry + rise * 0.55 * (1 - t * 0.5), zz)
        g.tube([root, mid, tip], [0.75, 0.6, 0.1], lambda x, y, z, d, f, t=t: mix(cols[0], cols[1], f / 2) if d < 0.75 else cols[2], pri, smooth=True, squash=(1, 0.4))
    g.tube([root, (rx + sx * span * 0.4, ry + rise * 0.8, zz), (rx + sx * span * 0.9, ry + rise * 0.6, zz)], [0.5, 0.4, 0.1], cols[3], pri + 1, smooth=True)


def _hex(x, y, base, dark, cell=1.7):
    q = round(y / (cell * 0.87))
    r = round((x - (q % 2) * cell / 2) / cell)
    cxh, cyh = r * cell + (q % 2) * cell / 2, q * cell * 0.87
    e = max(abs(x - cxh) / (cell / 2), abs(y - cyh) / (cell * 0.45))
    return dark if e > 0.84 else base


def _tiger_face(g, c, s, fur, stripe, eye, pri=7):
    """정면을 보는 호랑이 얼굴 (+z)"""
    x0, y0, z0 = c
    g.ellipsoid(c, (2.2 * s, 1.9 * s, 1.5 * s), lambda x, y, z, d: stripe if (abs(x - x0) > 0.9 * s and int((y - y0) / s * 1.8 + abs(x - x0) / s) % 3 == 0 and d > 0.4) else fur, pri)
    g.ellipsoid((x0, y0 - 0.8 * s, z0 + 1.2 * s), (1.2 * s, 0.8 * s, 0.7 * s), lit(fur, 0.9), pri + 1)
    g.dot(x0, y0 - 0.5 * s, z0 + 1.9 * s, "2a2a30", pri + 2, 0.5 * s)
    for sd in (-1, 1):
        g.dot(x0 + sd * 0.85 * s, y0 + 0.35 * s, z0 + 1.35 * s, eye, pri + 3, 0.5 * s)
        g.cone((x0 + sd * 1.5 * s, y0 + 1.4 * s, z0), (x0 + sd * 2.0 * s, y0 + 2.6 * s, z0 - 0.2 * s), 0.7 * s, 0.1, fur, pri)
        g.cone((x0 + sd * 0.5 * s, y0 - 1.3 * s, z0 + 1.6 * s), (x0 + sd * 0.5 * s, y0 - 2.2 * s, z0 + 1.6 * s), 0.25 * s, 0.05, "ffffff", pri + 3)
    for k in range(3):
        g.box((x0 - 0.7 * s, y0 + (0.9 + k * 0.4) * s, z0 + 1.15 * s), (x0 + 0.7 * s, y0 + (1.07 + k * 0.4) * s, z0 + 1.55 * s), stripe, pri + 2)


def _claws(g, x, y, z, col, n=3, ln=1.6, pri=7):
    for k in range(n):
        dx = (k - (n - 1) / 2) * 0.9
        g.tube([(x + dx, y, z), (x + dx, y - 0.2, z + ln * 0.6), (x + dx * 1.1, y - 0.9, z + ln)], [0.35, 0.25, 0.05], col, pri, smooth=True)


def _maw(g, c, w, h, fang, inner, rim, pri=7):
    """크게 벌린 아가리 (+z 쪽) — 도철"""
    x0, y0, z0 = c
    g.ellipsoid(c, (w, h, 0.5), lambda x, y, z, d: inner if d < 0.75 else rim, pri)
    for k in range(7):
        x = x0 - w * 0.8 + k * (w * 1.6 / 6)
        g.cone((x, y0 + h * 0.85, z0 + 0.4), (x, y0 + h * 0.85 - (1.0 if k % 2 else 1.5), z0 + 0.6), 0.32, 0.05, fang, pri + 2)
        g.cone((x + 0.1, y0 - h * 0.85, z0 + 0.4), (x + 0.1, y0 - h * 0.85 + (1.0 if k % 2 else 1.4), z0 + 0.6), 0.3, 0.05, fang, pri + 2)


def adorn(g, sid, slot):
    """armor_vox 모델 위에 사신수 · 사흉수 장식을 덧붙임"""
    if sid == "qinglong":
        GOLD, GOLD_H, GLOW, SC = "e0a828", "ffe27a", "8ff4ff", "2a64d0"
        if slot == 0:
            _dragon_horns(g, GOLD, GOLD_H)
            g.tube([(8, 10.2, 13.2), (8, 11.6, 13.8), (8, 12.6, 13.0)], [0.9, 0.7, 0.3], SC, 7, smooth=True)   # 이마 용 콧등
            g.sphere((8, 11.4, 14.1), 0.75, lambda x, y, z, d: "ffffff" if d < 0.3 else GLOW, 9)
            _whiskers(g, 10.4, 12.6, GOLD_H)
            for k in range(6):   # 정수리 지느러미 (뒤로)
                z = 11.5 - k * 1.5
                g.cone((8, 13.6 - k * 0.2, z), (8, 15.6 - k * 0.3, z - 0.9), 0.55, 0.06, GOLD_H if k % 2 else GOLD, 7)
        elif slot == 1:
            pts = [(2.6, 14.0, 9.0), (5.0, 12.6, 11.6), (8.6, 9.8, 12.2), (11.6, 6.8, 11.4), (13.4, 3.6, 9.2), (12.6, 1.0, 5.4), (8.0, 2.0, 3.6), (3.6, 4.6, 4.2)]
            g.tube(pts, [1.2, 1.15, 1.1, 1.0, 0.9, 0.75, 0.55, 0.15],
                   lambda x, y, z, d, f: "f0dca0" if (d > 0.8 and z > 10.6) else (SC if int(f * 2.5) % 2 else "18408e"), 6, smooth=True)
            for k in range(1, 7):
                p = pts[k]
                g.cone(p, (p[0], p[1] + 1.4, p[2] - 0.4), 0.45, 0.06, GOLD_H if k % 2 else GOLD, 7)
            hx, hy, hz = pts[0]   # 왼쪽 어깨 위 용 머리
            g.ellipsoid((hx, hy + 0.6, hz + 0.8), (1.4, 1.1, 1.8), SC, 7)
            g.dot(hx + 0.6, hy + 1.2, hz + 2.2, "ffee66", 9, 0.5)
            g.dot(hx - 0.6, hy + 1.2, hz + 2.2, "ffee66", 9, 0.5)
            for sd in (-1, 1):
                g.tube([(hx + sd * 0.6, hy + 1.4, hz), (hx + sd * 1.4, hy + 3.0, hz - 0.8), (hx + sd * 1.0, hy + 4.0, hz - 1.6)], [0.35, 0.25, 0.06], GOLD_H, 8, smooth=True)
            g.sphere((8, 10.0, 12.4), 1.3, lambda x, y, z, d: "ffffff" if d < 0.3 else GLOW, 9)   # 여의주
        elif slot == 2:
            for sx in (-1, 1):
                for k in range(3):   # 비늘 겹 허리 보호대
                    g.slab((8 + sx * 2.4, 12.0 - k * 1.6, 10.6 + k * 0.1), (8 + sx * 2.6, 10.0 - k * 1.6, 11.0 + k * 0.1), (3.6, 0, 0), 0.4,
                           lambda x, y, z, u, v: GOLD_H if u > 0.8 else (SC if abs(v) < 0.7 else GOLD), 6)
                _claws(g, 8 + sx * 2.3, 7.4, 10.8, GOLD_H, n=2, ln=1.2)
        else:
            for sx in (-1, 1):
                _claws(g, 8 + sx * 2.6, 1.4, 12.2, GOLD_H)
                g.tube([(8 + sx * 4.4, 7.4, 7.4), (8 + sx * 6.4, 9.0, 6.4), (8 + sx * 6.8, 10.6, 5.2)], [0.6, 0.45, 0.08], GOLD, 7, smooth=True, squash=(0.5, 1))
    elif sid == "baihu":
        FUR, STRIPE, EYE, STEEL = "f4f4f6", "1c1c24", "4ad0ff", "c8d0dc"
        if slot == 0:
            _tiger_face(g, (8, 11.8, 11.8), 1.25, FUR, STRIPE, EYE)
            for k in range(5):   # 정수리 줄무늬 갈기
                g.cone((8 + (k - 2) * 1.2, 13.0, 8.6), (8 + (k - 2) * 1.6, 15.0 - abs(k - 2) * 0.5, 6.6), 0.6, 0.08, STRIPE if k % 2 else FUR, 7)
            for sx in (-1, 1):   # 볼 털
                for k in range(3):
                    g.cone((8 + sx * 4.6, 6.0 + k * 1.2, 9.4), (8 + sx * 6.4, 5.2 + k * 1.4, 8.6), 0.6, 0.08, FUR, 7)
        elif slot == 1:
            for sx in (-1, 1):
                _tiger_face(g, (8 + sx * 6.2, 13.6, 9.8), 0.95, FUR, STRIPE, EYE)
            for k in range(9):   # 털 목도리
                a = k * math.pi / 8
                g.cone((8 + math.cos(a) * 3.6, 13.6, 8 + math.sin(a) * 2.2 + 0.6), (8 + math.cos(a) * 4.4, 15.0, 8 + math.sin(a) * 2.8 + 0.6), 0.7, 0.1, FUR, 6)
            for k in range(3):
                g.tube([(5.6 + k * 0.8, 8.6 + k * 1.4, 11.2), (10.4 + k * 0.8, 6.2 + k * 1.4, 11.2)], [0.25, 0.25], "4ad0ff", 7)   # 빛나는 발톱 자국
        elif slot == 2:
            for sx in (-1, 1):
                _claws(g, 8 + sx * 2.3, 7.6, 10.8, "ffffff", n=3, ln=1.3)
                for k in range(4):
                    g.box((8 + sx * 0.4, 10.6 - k * 2.2, 10.2), (8 + sx * 4.4, 10.9 - k * 2.2, 10.7), STRIPE, 6)
        else:
            for sx in (-1, 1):
                _claws(g, 8 + sx * 2.6, 1.2, 12.0, "ffffff", n=4, ln=1.4)
                for k in range(6):
                    a = k * math.pi / 3
                    g.cone((8 + sx * 2.6 + math.cos(a) * 2.2, 8.6, 7.4 + math.sin(a) * 2.4), (8 + sx * 2.6 + math.cos(a) * 3.0, 9.8, 7.4 + math.sin(a) * 3.2), 0.6, 0.1, FUR, 6)
    elif sid == "zhuque":
        RED, ORA, YEL, GOLD = "e0302a", "ff9a2a", "ffe066", "ffc93c"
        cols = (ORA, YEL, RED, GOLD)
        if slot == 0:
            _feather_fan(g, (8, 12.6, 5.6), 7, 0.33, 7.0, (RED, YEL, "8a1414"), z_back=-1.6, pri=7)
            g.cone((8, 10.6, 13.2), (8, 9.4, 15.2), 0.9, 0.08, GOLD, 8)   # 부리 모양 이마 장식
            for sx in (-1, 1):
                _feather_fan(g, (8 + sx * 4.8, 9.6, 8.6), 3, 0.35, 3.6, (ORA, YEL, RED), z_back=-1.0, pri=7)
        elif slot == 1:
            for sx in (-1, 1):
                _wing(g, (8 + sx * 2.6, 11.6, 4.0), sx, 9.0, 6.0, cols, pri=4, z=3.6)
            g.sphere((8, 10.0, 12.2), 1.2, lambda x, y, z, d: "fffbe0" if d < 0.35 else YEL, 9)
            _feather_fan(g, (8, 10.0, 11.4), 5, 0.42, 3.0, (ORA, YEL, RED), z_back=0.4, pri=7)
        elif slot == 2:
            for sx in (-1, 1):
                for k in range(4):   # 깃털 치마
                    g.tube([(8 + sx * (1.2 + k * 0.9), 12.4, 10.8), (8 + sx * (1.6 + k * 1.2), 9.4, 11.4), (8 + sx * (1.8 + k * 1.4), 7.0 - k * 0.3, 11.0)],
                           [0.7, 0.55, 0.1], lambda x, y, z, d, f: mix(RED, YEL, f / 2), 6, smooth=True, squash=(1, 0.45))
        else:
            for sx in (-1, 1):
                _feather_fan(g, (8 + sx * 4.4, 6.2, 7.4), 4, 0.32, 4.2, (ORA, YEL, RED), z_back=-0.8, pri=7)
                for k in range(3):
                    g.cone((8 + sx * 2.6 + (k - 1) * 1.0, 1.4, 12.2), (8 + sx * 2.6 + (k - 1) * 1.1, 3.0, 13.4), 0.5, 0.06, YEL if k == 1 else ORA, 7)
    elif sid == "xuanwu":
        SH, SH2, SHD, GOLD, JADE = "3a8a50", "5ab86a", "1e4a2a", "c9a13b", "a0ffcf"
        SN, SN2 = "203a30", "2e5a46"
        if slot == 0:
            g.ellipsoid((8, 10.0, 7.6), (5.9, 4.6, 6.1), lambda x, y, z, d: None if y < 9.4 else GOLD if y < 10.4 else _hex(x, z, SH2 if y > 13.0 else SH, SHD, 2.2), 6, inner=0.8)
            pts = [(13.0, 8.6, 6.0), (12.6, 9.0, 10.6), (8.0, 9.2, 13.4), (4.0, 9.4, 11.6), (3.2, 10.6, 7.8), (6.0, 14.6, 6.6), (8.0, 15.4, 9.8)]
            g.tube(pts, [0.7, 0.7, 0.65, 0.6, 0.55, 0.5, 0.35], lambda x, y, z, d, f: SN2 if int(f * 2) % 2 else SN, 7, smooth=True)
            hx, hy, hz = pts[-1]
            g.ellipsoid((hx, hy + 0.4, hz + 0.8), (0.9, 0.6, 1.1), SN2, 8)
            g.dot(hx + 0.45, hy + 0.7, hz + 1.5, "ff4040", 10, 0.4)
            g.dot(hx - 0.45, hy + 0.7, hz + 1.5, "ff4040", 10, 0.4)
            g.sphere((8, 12.0, 13.4), 0.8, lambda x, y, z, d: "eafff6" if d < 0.3 else JADE, 9)
        elif slot == 1:
            g.ellipsoid((8, 8.4, 3.0), (6.4, 7.4, 3.2), lambda x, y, z, d: GOLD if d > 0.93 else _hex(x, y, SH2 if int(x * 0.6 + y * 0.6) % 2 else SH, SHD), 5, inner=0.0)
            for k in range(12):   # 등딱지 테두리 톱니
                a = k * math.pi / 6
                g.cone((8 + math.cos(a) * 6.0, 8.4 + math.sin(a) * 7.0, 2.6), (8 + math.cos(a) * 7.2, 8.4 + math.sin(a) * 8.2, 2.2), 0.55, 0.08, GOLD, 6)
            for k in range(3):   # 앞 배딱지 판
                g.slab((8, 11.8 - k * 3.0, 11.4), (8, 9.6 - k * 3.0, 11.6), (5.6 - k * 0.6, 0, 0), 0.4, lambda x, y, z, u, v: GOLD if abs(v) > 0.85 or u > 0.85 else "d8c890", 6)
            g.sphere((8, 10.4, 12.4), 1.0, lambda x, y, z, d: "eafff6" if d < 0.3 else JADE, 9)
        elif slot == 2:
            for sx in (-1, 1):
                g.ellipsoid((8 + sx * 2.4, 9.6, 10.6), (2.2, 2.6, 0.8), lambda x, y, z, d: GOLD if d > 0.8 else _hex(x * 1.4, y * 1.4, SH2, SHD), 6)
                g.ellipsoid((8 + sx * 2.3, 6.6, 10.8), (1.7, 1.5, 0.8), lambda x, y, z, d: GOLD if d > 0.75 else SH, 7)
        else:
            for sx in (-1, 1):
                g.ellipsoid((8 + sx * 2.6, 2.4, 10.4), (2.2, 1.6, 2.0), lambda x, y, z, d: GOLD if d > 0.85 else _hex(x * 1.6, z * 1.6, SH2, SHD), 6, inner=0.7)
                pts = [(8 + sx * 2.6 + 2.2, 4.0, 5.6), (8 + sx * 2.6 + 2.4, 5.6, 9.0), (8 + sx * 2.6 - 2.2, 7.0, 9.4), (8 + sx * 2.6 - 2.4, 8.2, 6.0)]
                g.tube(pts, [0.45, 0.45, 0.4, 0.15], lambda x, y, z, d, f: SN2 if int(f * 2) % 2 else SN, 7, smooth=True)
    elif sid == "fiend":
        CRIM, CRIM_H, VOID, PURP, BONE = "b01030", "ff3050", "120a18", "e080ff", "e8dcc0"
        if slot == 0:   # 혼돈: 얼굴 없는 투구 · 소용돌이 · 네 날개
            g.ellipsoid((8, 7.6, 12.8), (3.0, 3.6, 0.7), lambda x, y, z, d: CRIM_H if abs(math.atan2(y - 7.6, x - 8) * 1.2 - math.hypot(x - 8, y - 7.6) * 1.6) % 1.6 < 0.35 else VOID, 8)
            for sx in (-1, 1):
                for k in range(2):
                    _wing(g, (8 + sx * 4.4, 10.0 - k * 2.6, 6.6), sx, 5.4 - k * 1.2, 3.4 - k * 1.4, ("3a1a3a", "6a2a6a", "1a0a1a", CRIM), pri=6, n=4)
            for k in range(5):
                a = k * 2 * math.pi / 5 + 0.3
                g.sphere((8 + math.cos(a) * 6.6, 15.0 + math.sin(a) * 1.2, 8 + math.sin(a) * 4.0), 0.55, lambda x, y, z, d: "ffffff" if d < 0.3 else PURP, 9)
            g.cone((8, 13.6, 8.0), (8, 17.2, 7.4), 1.0, 0.1, CRIM, 7)
        elif slot == 1:   # 도철: 가슴의 거대한 아가리 · 말린 뿔 무늬 · 어깨 가시
            _maw(g, (8, 7.4, 11.4), 3.4, 2.2, BONE, "3a0010", CRIM)
            for sx in (-1, 1):
                g.dot(8 + sx * 2.2, 11.0, 11.8, CRIM_H, 10, 0.9)   # 눈
                g.tube([(8 + sx * 1.0, 12.4, 11.6), (8 + sx * 3.4, 13.2, 11.4), (8 + sx * 4.4, 11.6, 11.2), (8 + sx * 3.4, 10.4, 11.4)], [0.5, 0.45, 0.35, 0.15], CRIM, 8, smooth=True)   # 말린 뿔 무늬
                for k in range(3):
                    g.cone((8 + sx * 6.0, 13.8, 6.6 + k * 1.6), (8 + sx * 7.6, 17.0 - k * 0.6, 6.0 + k * 1.6), 0.7, 0.08, lambda x, y, z, f: mix("3a2a40", BONE, f), 7)
        elif slot == 2:   # 도올: 무릎에서 솟은 멧돼지 엄니 · 허리 갈기
            for sx in (-1, 1):
                g.tube([(8 + sx * 2.3, 6.6, 10.8), (8 + sx * 3.8, 7.6, 12.6), (8 + sx * 4.2, 10.0, 13.0), (8 + sx * 3.4, 11.4, 12.4)], [0.65, 0.5, 0.3, 0.06], BONE, 8, smooth=True)
                g.dot(8 + sx * 2.3, 6.6, 11.2, CRIM_H, 9, 0.7)
            for k in range(9):
                x = 3.2 + k * 1.2
                g.cone((x, 13.0, 10.6), (x + 0.2, 10.6 - (k % 2) * 0.8, 11.6), 0.5, 0.08, "4a2030" if k % 2 else CRIM, 7)
        else:   # 궁기: 발목의 박쥐 날개 · 발톱
            for sx in (-1, 1):
                _wing(g, (8 + sx * 4.4, 6.6, 7.0), sx, 5.6, 4.4, ("3a1a3a", "7a2040", "1a0a1a", CRIM_H), pri=6, n=4)
                _claws(g, 8 + sx * 2.6, 1.2, 12.2, BONE, n=3, ln=1.5)
