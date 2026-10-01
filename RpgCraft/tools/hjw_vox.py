"""
v5.10.44 히든 직업 전용 무기 3D 모델 — 일반 무기보다 훨씬 크고 화려하게 하나씩 손으로 조각한다.
 - hjw_a 명계의 낫     : 흑요석 자루 · 척추뼈 마디 · 뿔 달린 해골 머리 · 거대한 초승달 날 (안쪽 날이 청록 영혼빛) · 찢어진 망토 자락 · 떠다니는 혼불
 - hjw_b 성운검 스텔라 : 밤하늘을 담은 대검 (성운 소용돌이 · 별) · 빛나는 날 · 황금 천체의(아스트롤라베) 가드 · 궤도 고리와 행성 · 초승달 폼멜 · 떠 있는 별 조각
 - hjw_c 망자의 홀     : 서로 꼬인 뼈 자루 · 숫양 뿔 해골 · 해골 위 발톱 우리에 갇힌 초록 영혼 구슬 · 떠도는 룬 고리 · 매달린 작은 해골 장식
 - hjw_d 시간의 바늘   : 시계 바늘 모양 레이피어 (속이 빈 장식 고리 · 스페이드 끝) · 시계판 가드 (로마 숫자 눈금) · 뒤의 톱니바퀴 · 모래시계 폼멜 · 시간의 고리
세로로 조각(손잡이 ≈ y 0, 끝이 위)한 뒤 다른 무기처럼 z 축 -45° 로 돌려 대각선으로 든다.
가장 밝은 색(채널 하나라도 240 이상)은 음영을 받지 않아 빛나 보인다.
"""
import math

import voxel_lib as VL
import boss_models as bm

VS = 0.4
CX, CZ = 8.0, 8.0
CMD = 7700   # Java ItemRegistry hjw_* .model(7700) 과 같아야 함 (재질이 달라 네 무기가 같은 번호를 씀)
MATERIAL = {"hjw_a": "netherite_hoe", "hjw_b": "netherite_sword", "hjw_c": "blaze_rod", "hjw_d": "echo_shard"}


def _h(rgb):
    return "%02x%02x%02x" % tuple(max(0, min(255, int(round(c)))) for c in rgb)


def _rgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    A, B = _rgb(a), _rgb(b)
    return _h(tuple(A[i] + (B[i] - A[i]) * t for i in range(3)))


def lit(h, k):
    r = _rgb(h)
    if k >= 1:
        return _h(tuple(c + (255 - c) * (k - 1) for c in r))
    return _h(tuple(c * k for c in r))


def hsh(*v):
    x = 0
    for a in v:
        x = (x * 1103515245 + int(math.floor(a * 7.31)) * 2654435761 + 12345) & 0xFFFFFFFF
    x ^= x >> 13
    x = (x * 1274126177) & 0xFFFFFFFF
    return (x & 0xFFFF) / 65535.0


def extrude(g, x0, x1, y0, y1, fn, pri=3):
    """2D 실루엣을 두께로: fn(x, y) → None 또는 (반두께, colf(x,y,z,w)) — w: 두께 방향 -1~1"""
    for i in range(math.floor(x0 / VS), math.floor(x1 / VS) + 1):
        x = (i + 0.5) * VS
        for j in range(math.floor(y0 / VS), math.floor(y1 / VS) + 1):
            y = (j + 0.5) * VS
            r = fn(x, y)
            if not r:
                continue
            hz, colf = r
            hz = max(hz, VS * 0.5)
            for k in range(math.floor((CZ - hz) / VS), math.floor((CZ + hz - 1e-6) / VS) + 1):
                z = (k + 0.5) * VS
                g.put(x, y, z, colf(x, y, z, (z - CZ) / hz), pri)


def column(g, y0, y1, rf, colf, pri=3, cx=CX):
    """세로 원기둥 (반지름 rf(y)) — colf(x,y,z,a) a: 둘레 각도"""
    for j in range(math.floor(y0 / VS), math.floor(y1 / VS) + 1):
        y = (j + 0.5) * VS
        if y < y0 or y > y1:
            continue
        r = rf(y)
        if r <= 0:
            continue
        r = max(r, VS * 0.6)
        for i in range(math.floor((cx - r) / VS), math.floor((cx + r) / VS) + 1):
            x = (i + 0.5) * VS
            for k in range(math.floor((CZ - r) / VS), math.floor((CZ + r) / VS) + 1):
                z = (k + 0.5) * VS
                if (x - cx) ** 2 + (z - CZ) ** 2 <= r * r:
                    g.put(x, y, z, colf(x, y, z, math.atan2(z - CZ, x - cx)), pri)


def star4(g, c, r, col, core, pri=9, thick=0.45):
    """4갈래 반짝임 (떠 있는 별)"""
    x, y, z = c
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        g.tube([(x, y, z), (x + dx * r, y + dy * r, z)], [thick, 0.12], col, pri)
    g.dot(x, y, z, core, pri + 1, 0.8)


def gear(g, c, r, teeth, col, dark, pri=4, thick=0.5, hole=0.35):
    cx, cy, cz = c
    for i in range(math.floor((cx - r - 0.8) / VS), math.floor((cx + r + 0.8) / VS) + 1):
        x = (i + 0.5) * VS
        for j in range(math.floor((cy - r - 0.8) / VS), math.floor((cy + r + 0.8) / VS) + 1):
            y = (j + 0.5) * VS
            d = math.hypot(x - cx, y - cy)
            a = math.atan2(y - cy, x - cx)
            tooth = (math.cos(a * teeth) > 0.25)
            R = r + (0.65 if tooth else 0)
            if d > R or d < r * hole:
                continue
            spoke = any(abs(math.sin(a - s * math.pi / 3)) * d < 0.38 for s in range(3))
            if r * hole + 0.3 < d < r * 0.72 and not spoke:
                continue   # 바퀴살 사이 구멍
            colr = lit(col, 1.15) if d > r - 0.3 else col if d > r * 0.72 else dark
            for k in range(math.floor((cz - thick) / VS), math.floor((cz + thick - 1e-6) / VS) + 1):
                g.put(x, y, (k + 0.5) * VS, colr, pri)


# ====================================================================== A 명계의 낫
def scythe(g):
    OBS, OBS2 = "2a2438", "3e3654"
    BONE, BONE_D = "dcd2b4", "8a8068"
    SOUL, SOUL2, SOUL3 = "4af8e0", "a8fff2", "e8fffb"
    STEEL, STEEL_H = "403a5c", "7a72a0"
    CLOTH, CLOTH2 = "2a1238", "44205a"
    # 자루: 흑요석 + 나선 영혼 상감
    def shaft_col(x, y, z, a):
        s = (a / (2 * math.pi) * 2 + y * 0.32) % 1.0
        if s < 0.12:
            return SOUL
        return OBS2 if (y * 1.7) % 2 < 1 else OBS
    column(g, -10.0, 25.0, lambda y: 0.72, shaft_col, 3)
    # 손잡이: 핏빛 가죽 감기
    column(g, -2.6, 2.6, lambda y: 0.92, lambda x, y, z, a: "5a1820" if int(y * 2.4 + a * 0.6) % 2 else "3a0e14", 4)
    for yy in (-2.9, 2.9):
        column(g, yy - 0.35, yy + 0.35, lambda y: 1.1, lambda *a: STEEL_H, 5)
    # 척추뼈 마디 (자루 위쪽)
    for n, yy in enumerate((5.0, 8.4, 11.8, 15.2, 18.6)):
        column(g, yy - 0.55, yy + 0.55, lambda y, yy=yy: 1.25 - abs(y - yy) * 0.6, lambda x, y, z, a: BONE if y > yy - 0.1 else BONE_D, 5)
        for sd in (-1, 1):
            g.cone((CX + sd * 1.0, yy, CZ), (CX + sd * 2.2, yy - 0.9, CZ), 0.38, 0.1, BONE, 6)
        g.cone((CX, yy, CZ - 1.0), (CX, yy - 0.6, CZ - 2.0), 0.32, 0.1, BONE_D, 6)
    # 아래 끝: 휘어진 뼈 가시 + 영혼 보석
    column(g, -11.0, -10.0, lambda y: 1.0, lambda *a: STEEL_H, 5)
    g.tube([(CX, -11.0, CZ), (CX + 0.4, -13.0, CZ), (CX + 1.4, -14.6, CZ)], [0.75, 0.45, 0.08], BONE, 5, smooth=True)
    g.dot(CX, -10.5, CZ + 1.0, SOUL2, 8, 0.8)
    # 머리: 뿔 달린 해골
    HY = 26.6
    g.ellipsoid((CX, HY, CZ + 0.2), (2.2, 2.0, 2.0), lambda x, y, z, d: BONE if d < 0.85 else lit(BONE, 0.85), 6)
    g.ellipsoid((CX, HY - 1.7, CZ + 0.9), (1.4, 0.8, 1.2), lambda *a: BONE_D, 6)   # 턱
    for sd in (-1, 1):
        g.ellipsoid((CX + sd * 0.85, HY + 0.1, CZ + 1.85), (0.55, 0.55, 0.45), "120c18", 8)   # 눈구멍
        g.dot(CX + sd * 0.85, HY + 0.1, CZ + 2.15, SOUL3, 9, 0.5)
        g.tube([(CX + sd * 1.6, HY + 1.1, CZ), (CX + sd * 3.2, HY + 2.6, CZ - 0.4), (CX + sd * 3.6, HY + 4.6, CZ - 0.6), (CX + sd * 2.8, HY + 5.6, CZ - 0.4)],
               [0.7, 0.55, 0.35, 0.08], lambda x, y, z, d, f: mix(BONE, "3a3428", f / 3), 6, smooth=True)
    for k in range(5):   # 이빨
        g.dot(CX - 0.8 + k * 0.4, HY - 1.15, CZ + 1.95, "f4ecd4", 9, 0.4)
    # 날: 거대한 초승달 (중심 O, 바깥 반지름 R, 안쪽 날이 영혼빛)
    O, R = (10.6, 12.0), 15.6
    A0, A1 = math.radians(98), math.radians(204)

    def blade(x, y):
        dx, dy = x - O[0], y - O[1]
        r = math.hypot(dx, dy)
        a = math.atan2(dy, dx)
        if a < 0:
            a += 2 * math.pi
        if not (A0 <= a <= A1):
            return None
        t = (a - A0) / (A1 - A0)
        w = 7.4 * (1 - t) ** 0.7 + 0.35
        notch = 0.0
        if 0.08 < t < 0.7:   # 등 쪽 톱니
            ph = (t * 11) % 1.0
            notch = 0.9 * max(0.0, 1 - abs(ph - 0.5) * 4) if t < 0.62 else 0
        rin, rout = R - w, R + notch
        if r < rin or r > rout:
            return None
        u = (r - rin) / max(0.01, w)   # 0 안쪽 날 → 1 등
        hz = 0.25 + 0.75 * min(1.0, u * 1.4) * (1 - t * 0.6)

        def col(x, y, z, wz, u=u, t=t, r=r):
            if r > R:
                return STEEL_H
            if u < 0.09:
                return SOUL3
            if u < 0.2:
                return SOUL2
            if u < 0.32:
                return mix(SOUL, STEEL, (u - 0.2) / 0.12)
            if abs(u - 0.62) < 0.07 and hsh(round(t * 40)) > 0.35:   # 룬 새김 (빛남)
                return SOUL
            if u > 0.9:
                return STEEL_H
            return mix(STEEL, "3e3858", hsh(round(x * 2), round(y * 2)) * 0.6) if abs(wz) > 0.7 else STEEL
        return hz, col
    extrude(g, -9, 12, 2, 29, blade, 4)
    # 날 뿌리 받침 (해골 뒤 왕관 모양 쇠)
    g.ellipsoid((CX - 0.3, HY - 0.6, CZ - 0.6), (1.8, 2.4, 1.0), lambda x, y, z, d: STEEL_H if d > 0.7 else STEEL, 5)
    for k in range(4):
        a = math.radians(60 + k * 22)
        g.cone((CX + math.cos(a) * 1.2, HY + 1.4, CZ - 0.6), (CX + math.cos(a) * 2.4, HY + 3.6 + (k % 2), CZ - 0.6), 0.4, 0.08, STEEL_H, 6)
    # 반대쪽 작은 갈고리 날
    g.tube([(CX + 1.2, HY - 0.2, CZ), (CX + 3.6, HY + 0.6, CZ), (CX + 5.2, HY - 1.6, CZ), (CX + 5.0, HY - 3.6, CZ)],
           [0.75, 0.6, 0.35, 0.08], lambda x, y, z, d, f: SOUL2 if d > 0.7 and y < HY - 0.5 else STEEL, 5, smooth=True)
    # 찢어진 망토 자락 (해골 아래에서 휘날림)
    def cloth(u, v):
        if v > 0.25 + 0.75 * (1 - abs(math.sin(u * 9.5)) * 0.6) - u * 0.15:
            return None
        x = CX + 0.8 + u * 7.0 + math.sin(v * 3.0) * 0.6
        y = HY - 2.0 - v * 7.5 - u * 2.0 + math.sin(u * 6) * 0.5
        z = CZ - 0.6 + math.sin(u * 5 + v * 2) * 0.5
        return (x, y, z)
    g.surface(cloth, 46, 42, lambda p, u, v: CLOTH2 if v < 0.08 else ("4af8e0" if 0.08 <= v < 0.13 else CLOTH if (u * 9) % 1 > 0.2 else "1a0a26"), 2)
    # 떠다니는 혼불
    for (x, y, s) in ((1.5, 24.0, 0.9), (-3.0, 16.5, 0.75), (13.0, 21.0, 0.7), (4.5, 30.5, 0.6), (-5.6, 10.0, 0.6)):
        g.sphere((x, y, CZ), s, lambda xx, yy, zz, d: SOUL3 if d < 0.4 else SOUL2, 10)
        g.cone((x, y + s * 0.6, CZ), (x + 0.4, y + s * 2.6, CZ), s * 0.6, 0.06, SOUL, 9)


# ====================================================================== B 성운검 스텔라
def stella(g):
    GOLD, GOLD_H, GOLD_D = "e8b84a", "fff0a8", "8a6018"
    NIGHT, NIGHT2, NEB1, NEB2, NEB3 = "120a3a", "24166a", "6a3ad0", "ff6ad8", "40d8ff"
    EDGE, EDGE2 = "f4f8ff", "b8d8ff"
    y0b, y1b = 4.6, 31.0
    # 칼날: 넓은 대검 (끝은 길게 뾰족) · 마름모 단면
    def blade(x, y):
        if y < y0b or y > y1b:
            return None
        t = (y - y0b) / (y1b - y0b)
        w = 2.6 * (1 - 0.18 * t) if t < 0.78 else 2.6 * 0.86 * ((1 - t) / 0.22) ** 0.8
        w += 0.35 * math.sin(t * math.pi) if t < 0.78 else 0
        dx = abs(x - CX)
        if dx > w:
            return None
        u = dx / max(w, 0.01)
        hz = max(0.2, 1.15 * (1 - u * 0.8))

        def col(x, y, z, wz, u=u, t=t):
            if u > 0.8:
                return EDGE if u > 0.9 else EDGE2
            if u < 0.08 and t < 0.82:
                return "c8f0ff"   # 가운데 빛줄기
            n = math.sin(x * 1.3 + y * 0.55) + math.sin(y * 0.9 - x * 0.7 + 1.7) * 0.8 + math.sin((x + y) * 0.35) * 0.6
            h = hsh(round(x / VS), round(y / VS), round(z / VS))
            if h > 0.965:
                return "ffffff"
            if h > 0.94:
                return "fff4c0"
            base = mix(NIGHT, NIGHT2, t * 0.6 + u * 0.4)
            if n > 1.2:
                return mix(base, NEB2, min(1, (n - 1.2) * 0.9))
            if n < -1.1:
                return mix(base, NEB3, min(1, (-n - 1.1) * 0.9))
            if n > 0.5:
                return mix(base, NEB1, (n - 0.5) * 0.9)
            return base
        return hz, col
    extrude(g, CX - 3.5, CX + 3.5, y0b, y1b + 0.5, blade, 4)
    # 칼날 받침 (리카소) + 4갈래 별 문장
    extrude(g, CX - 2.2, CX + 2.2, 3.6, 6.6, lambda x, y: (1.3, lambda xx, yy, zz, w: GOLD_H if abs(w) > 0.8 else GOLD) if abs(x - CX) < 2.0 - (y - 3.6) * 0.35 else None, 6)
    # 천체의 가드: 세로 고리 + 별빛 살 + 기운 궤도 고리와 행성
    GY = 3.4
    g.ring((CX, GY, CZ), 3.7, 0.42, lambda x, y, z, a: GOLD_H if int(a * 6 / math.pi) % 2 else GOLD, 6, axis="z")
    g.ring((CX, GY, CZ), 3.0, 0.22, GOLD_D, 6, axis="z")
    for k in range(8):   # 고리 바깥 별빛 살
        a = k * math.pi / 4 + math.pi / 8
        L = 6.8 if k % 2 == 0 else 5.0
        if math.sin(a) > 0.6:
            continue   # 칼날 쪽은 비움
        g.tube([(CX + math.cos(a) * 3.6, GY + math.sin(a) * 3.6, CZ), (CX + math.cos(a) * L, GY + math.sin(a) * L, CZ)], [0.42, 0.06],
               lambda x, y, z, d, f: GOLD_H if f > 0.5 else GOLD, 6)
    for sd in (-1, 1):   # 위로 젖혀진 날개 가드
        g.tube([(CX + sd * 2.0, GY + 0.4, CZ), (CX + sd * 5.2, GY + 1.3, CZ), (CX + sd * 7.6, GY + 3.6, CZ), (CX + sd * 8.2, GY + 5.8, CZ)],
               [0.75, 0.6, 0.4, 0.08], lambda x, y, z, d, f: GOLD_H if d > 0.75 else GOLD, 6, smooth=True)
        g.dot(CX + sd * 5.2, GY + 1.3, CZ + 0.7, NEB3, 8, 0.7)
    g.ring((CX, GY, CZ), 5.2, 0.2, "d8e0ff", 5, axis="y", tilt=math.radians(62))   # 기운 궤도
    for (a, r, col) in ((0.6, 0.75, "4a8aff"), (2.9, 0.6, "ff9a4a"), (4.4, 0.5, "c8a0ff")):
        p = (CX + math.cos(a) * 5.2, GY + math.sin(a) * 5.2 * math.sin(math.radians(62)), CZ + math.sin(a) * 5.2 * math.cos(math.radians(62)))
        g.sphere(p, r, lambda x, y, z, d, col=col: lit(col, 1.3) if d < 0.3 else col, 8)
    # 가운데 별 보석
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        g.tube([(CX, GY, CZ), (CX + dx * 2.4, GY + dy * 2.4, CZ)], [0.9, 0.1], lambda x, y, z, d, f: GOLD_H if f > 0.4 else GOLD, 7)
    g.sphere((CX, GY, CZ + 0.4), 1.05, lambda x, y, z, d: "ffffff" if d < 0.35 else "a8e8ff", 9)
    # 손잡이: 남색 + 금실
    column(g, -2.8, 2.0, lambda y: 0.85, lambda x, y, z, a: GOLD if int(y * 2.6 + a * 0.64) % 3 == 0 else "1a1448", 4)
    # 폼멜: 초승달 + 별
    PY = -4.0
    moon = [(CX + math.cos(a) * 1.9, PY + math.sin(a) * 1.9, CZ) for a in [math.radians(60 + i * 12) for i in range(21)]]
    g.tube(moon, [0.12 + 0.75 * math.sin(math.pi * i / 20) for i in range(21)], lambda x, y, z, d, f: GOLD_H if d > 0.7 else GOLD, 5)
    star4(g, (CX, PY - 2.8, CZ), 1.4, GOLD_H, "ffffff", 7, 0.4)
    g.tube([(CX, PY - 1.6, CZ), (CX, PY - 2.4, CZ)], [0.35, 0.3], GOLD, 6)
    # 떠 있는 별 조각 + 별자리 점
    for (x, y, r) in ((CX - 5.2, 13.0, 1.5), (CX + 5.0, 18.5, 1.8), (CX - 4.4, 25.5, 1.2), (CX + 3.6, 29.5, 1.0), (CX + 5.6, 9.6, 1.0)):
        star4(g, (x, y, CZ), r, "c8f0ff", "ffffff", 9)


# ====================================================================== C 망자의 홀
def scepter(g):
    BONE, BONE_D, BONE_DD = "ddd4b6", "a09478", "5e5444"
    IRON, IRON_H = "2e2838", "5e5874"
    SOUL, SOUL2, SOUL3 = "7aff5a", "c4ffa8", "f2fff0"
    VIO, VIO2 = "8a3ad0", "c890ff"
    # 꼬인 두 가닥 자루 (뼈 · 검은 쇠)
    for ph, col in ((0.0, BONE_D), (math.pi, IRON)):
        pts = [(CX + math.cos(ph + y * 0.55) * 0.55, y, CZ + math.sin(ph + y * 0.55) * 0.55) for y in [-9.5 + i * 0.5 for i in range(57)]]
        g.tube(pts, [0.55] * len(pts), col, 3)
    column(g, -9.5, 19.0, lambda y: 0.6, lambda *a: "3a3244", 2)
    # 손잡이 감기 + 쇠고리
    column(g, -2.6, 2.6, lambda y: 1.0, lambda x, y, z, a: "3a2a48" if int(y * 2.4 + a * 0.64) % 2 else "22182c", 4)
    for yy in (-3.0, 3.0, 9.0, 14.0):
        column(g, yy - 0.4, yy + 0.4, lambda y: 1.2, lambda x, y, z, a: IRON_H if int(a * 4) % 2 else IRON, 5)
        for k in range(4):
            a = k * math.pi / 2 + 0.4
            g.cone((CX + math.cos(a) * 1.1, yy, CZ + math.sin(a) * 1.1), (CX + math.cos(a) * 1.9, yy - 0.5, CZ + math.sin(a) * 1.9), 0.28, 0.06, BONE, 6)
    # 아래 끝: 뼈 손가락이 쥔 작은 구슬
    g.sphere((CX, -11.0, CZ), 0.95, lambda x, y, z, d: SOUL3 if d < 0.35 else SOUL, 6)
    for k in range(4):
        a = k * math.pi / 2 + 0.3
        g.tube([(CX + math.cos(a) * 0.5, -9.3, CZ + math.sin(a) * 0.5), (CX + math.cos(a) * 1.4, -10.4, CZ + math.sin(a) * 1.4),
                (CX + math.cos(a) * 0.8, -12.0, CZ + math.sin(a) * 0.8)], [0.32, 0.28, 0.12], BONE, 7, smooth=True)
    # 해골
    SY = 21.8
    g.ellipsoid((CX, SY, CZ), (2.6, 2.5, 2.5), lambda x, y, z, d: BONE if (y - SY) > -0.6 or d < 0.8 else BONE_D, 6)
    g.ellipsoid((CX, SY - 2.0, CZ + 0.7), (1.7, 1.0, 1.6), lambda x, y, z, d: BONE_D, 6)
    for sd in (-1, 1):
        g.ellipsoid((CX + sd * 1.0, SY + 0.0, CZ + 2.1), (0.72, 0.7, 0.55), "0e0a10", 8)
        g.dot(CX + sd * 1.0, SY + 0.0, CZ + 2.45, SOUL3, 9, 0.55)
        g.dot(CX + sd * 1.0, SY + 0.0, CZ + 2.2, SOUL, 8, 0.85)
        # 숫양 뿔 (둥글게 말림)
        pts = []
        for i in range(15):
            t = i / 14
            a = t * math.pi * 1.6
            rr = 2.4 * (1 - t * 0.45)
            pts.append((CX + sd * (2.0 + math.sin(a) * rr * 0.9 + t * 0.8), SY + 1.0 + math.cos(a) * rr - rr * 0.4, CZ - 0.5 + t * 1.6))
        g.tube(pts, [0.85 - 0.6 * i / 14 for i in range(15)], lambda x, y, z, d, f: BONE_DD if int(f * 1.6) % 2 else BONE_D, 6)
    g.ellipsoid((CX, SY - 0.9, CZ + 2.3), (0.35, 0.45, 0.3), "1a1418", 8)   # 코
    for k in range(6):
        g.dot(CX - 1.0 + k * 0.4, SY - 1.5, CZ + 2.3, "f4eedc", 9, 0.42)
    # 해골 위 발톱 우리 + 영혼 구슬
    OY = 27.4
    g.sphere((CX, OY, CZ), 2.2, lambda x, y, z, d: SOUL3 if d < 0.25 else SOUL2 if d < 0.6 else SOUL, 7)
    for k in range(5):
        a = k * 2 * math.pi / 5 + 0.3
        g.tube([(CX + math.cos(a) * 1.3, SY + 2.0, CZ + math.sin(a) * 1.3), (CX + math.cos(a) * 3.1, OY - 0.4, CZ + math.sin(a) * 3.1),
                (CX + math.cos(a) * 2.2, OY + 2.4, CZ + math.sin(a) * 2.2), (CX + math.cos(a) * 0.5, OY + 3.5, CZ + math.sin(a) * 0.5)],
               [0.5, 0.45, 0.32, 0.08], lambda x, y, z, d, f: BONE if f < 2 else BONE_D, 8, smooth=True)
    # 떠도는 룬 고리 (끊어진 점들)
    for k in range(18):
        a = k * 2 * math.pi / 18
        if k % 3 == 2:
            continue
        p = (CX + math.cos(a) * 4.6, OY - 0.6 + math.sin(a) * 4.6 * 0.42, CZ + math.sin(a) * 4.6 * 0.9)
        g.dot(p[0], p[1], p[2], VIO2 if k % 2 else VIO, 9, 0.6)
    # 매달린 작은 해골 장식 (사슬)
    for sd in (-1, 1):
        x0 = CX + sd * 1.8
        for i in range(5):
            g.dot(x0 + sd * 0.1 * i, SY - 2.2 - i * 0.55, CZ + 0.6, IRON_H if i % 2 else "6a6478", 6, 0.36)
        cy = SY - 5.2
        g.ellipsoid((x0 + sd * 0.4, cy, CZ + 0.6), (0.85, 0.8, 0.75), BONE, 6)
        g.dot(x0 + sd * 0.4 - 0.3, cy + 0.05, CZ + 1.3, "1a1018", 7, 0.36)
        g.dot(x0 + sd * 0.4 + 0.3, cy + 0.05, CZ + 1.3, "1a1018", 7, 0.36)
    # 떠다니는 영혼 불꽃
    for (x, y, s) in ((CX - 4.4, 18.0, 0.65), (CX + 4.6, 24.0, 0.7), (CX - 3.0, 31.0, 0.55), (CX + 3.4, 14.6, 0.5)):
        g.sphere((x, y, CZ), s, lambda xx, yy, zz, d: SOUL3 if d < 0.4 else SOUL, 10)
        g.cone((x, y + s * 0.6, CZ), (x - 0.3, y + s * 2.6, CZ), s * 0.6, 0.06, SOUL2, 9)


# ====================================================================== D 시간의 바늘
def needle(g):
    GOLD, GOLD_H, GOLD_D = "d8a840", "fff0b0", "7a5418"
    BLUE, BLUE_H = "1e3a8a", "3a6ad8"
    TIME, TIME2 = "5af0ff", "d0fcff"
    IVORY, INK = "f2ead6", "2a2030"
    # 칼날: 시계 바늘 (가는 몸 · 속 빈 마름모 고리 · 속 빈 동그라미 · 스페이드 끝)
    yb = 7.0

    def blade(x, y):
        dx = abs(x - CX)
        if y < yb or y > 30.0:
            return None
        inside = False
        core = 0.0
        if dx < 0.55 + (0.25 if y < 9 else 0):
            inside, core = True, dx / 0.8
        # 마름모 고리 (y 11~16)
        my, mh, mw = 13.5, 2.6, 2.2
        dd = abs(y - my) / mh + dx / mw
        if 0.72 < dd < 1.0 and abs(y - my) <= mh:
            inside, core = True, 0.9
        if dd <= 0.72 and dx >= 0.55:
            inside = False
        # 동그라미 고리 (y 20)
        oy, orr = 20.0, 1.8
        dr = math.hypot(dx, y - oy)
        if 1.25 < dr < orr:
            inside, core = True, 0.9
        elif dr <= 1.25 and dx >= 0.55:
            inside = False
        # 스페이드 끝 (y 24~30)
        if y >= 23.6:
            t = (y - 23.6) / 6.4
            w = 2.3 * math.sin(math.pi * min(0.5, t * 1.4) ) if t < 0.36 else 2.3 * max(0.0, (1 - t) / 0.64)
            if dx < w:
                inside, core = True, dx / max(0.01, w)
        if not inside:
            return None
        hz = 0.75 if core < 0.6 else 0.45

        def col(x, y, z, wz, core=core, dx=dx):
            if dx < 0.2 and y > 8:
                return TIME2 if int(y * 1.4) % 3 else TIME   # 가운데 빛나는 시간의 실
            if core > 0.85:
                return GOLD_H
            return BLUE_H if abs(wz) > 0.6 else BLUE
        return hz, col
    extrude(g, CX - 3, CX + 3, yb, 30.5, blade, 4)
    # 마름모 · 동그라미 고리 금테 강조
    g.ring((CX, 20.0, CZ), 1.55, 0.28, GOLD, 5, axis="z")
    # 시계판 가드
    GY = 5.0
    extrude(g, CX - 5.2, CX + 5.2, GY - 5.2, GY + 5.2,
            lambda x, y: (0.4, (lambda xx, yy, zz, w, x=x, y=y: _dial(x - CX, y - GY, IVORY, INK, GOLD, GOLD_H, TIME))) if math.hypot(x - CX, y - GY) <= 4.7 else None, 5)
    g.ring((CX, GY, CZ), 4.75, 0.5, lambda x, y, z, a: GOLD_H if int(a * 12 / (2 * math.pi) + 0.5) % 3 == 0 else GOLD, 6, axis="z")
    for k in range(4):   # 시계판 위 · 아래 · 옆 장식 돌기
        a = k * math.pi / 2
        p = (CX + math.cos(a) * 5.5, GY + math.sin(a) * 5.5, CZ)
        if k == 1:
            continue
        g.sphere(p, 0.75, lambda x, y, z, d: TIME if d < 0.4 else GOLD, 7)
    # 작은 시침 · 분침 (판 앞)
    g.tube([(CX, GY, CZ + 0.6), (CX - 1.6, GY + 2.2, CZ + 0.6)], [0.3, 0.12], INK, 8)
    g.tube([(CX, GY, CZ + 0.6), (CX + 2.9, GY - 1.2, CZ + 0.6)], [0.25, 0.1], INK, 8)
    g.dot(CX, GY, CZ + 0.7, GOLD_H, 9, 0.7)
    # 뒤의 톱니바퀴
    gear(g, (CX - 4.4, GY + 3.2, CZ - 0.7), 2.4, 10, "b88a38", "6a4a18", 3, 0.35)
    gear(g, (CX + 4.6, GY - 2.8, CZ - 0.7), 2.0, 8, "c89a48", "6a4a18", 3, 0.35)
    gear(g, (CX + 3.8, GY + 4.4, CZ - 1.0), 1.4, 7, "a87a30", "5a3a10", 2, 0.3)
    # 손잡이: 금 고리 + 파란 가죽
    column(g, -2.4, 0.4 + 0.2, lambda y: 0.78, lambda x, y, z, a: GOLD if int(y * 2.6) % 3 == 0 else "1a2a5a", 4)
    # 모래시계 폼멜
    PY = -4.6
    column(g, PY - 2.0, PY + 2.0, lambda y: 0.25 + abs(y - PY) * 0.55, lambda x, y, z, a: "ffd870" if (y < PY - 0.6 or (y < PY + 0.4 and abs(x - CX) < 0.3)) else "d8f0ff", 4)
    for yy in (PY - 2.2, PY + 2.2):
        column(g, yy - 0.3, yy + 0.3, lambda y: 1.55, lambda *a: GOLD, 5)
    for k in range(3):
        a = k * 2 * math.pi / 3
        g.tube([(CX + math.cos(a) * 1.25, PY - 2.0, CZ + math.sin(a) * 1.25), (CX + math.cos(a) * 1.25, PY + 2.0, CZ + math.sin(a) * 1.25)], [0.22, 0.22], GOLD_D, 6)
    column(g, PY + 2.5, -2.4, lambda y: 0.5, lambda *a: GOLD_D, 4)
    # 시간의 고리 (칼날을 비스듬히 감는 점선 고리) + 떠 있는 톱니 조각
    for ry, rr in ((16.6, 3.4), (25.0, 2.6)):
        for k in range(22):
            if k % 4 == 3:
                continue
            a = k * 2 * math.pi / 22
            g.dot(CX + math.cos(a) * rr, ry + math.sin(a) * rr * 0.35, CZ + math.sin(a) * rr, TIME2 if k % 2 else TIME, 9, 0.5)
    gear(g, (CX - 4.2, 23.0, CZ), 0.9, 6, GOLD, GOLD_D, 8, 0.25, 0.3)
    gear(g, (CX + 4.0, 11.0, CZ), 1.0, 6, GOLD, GOLD_D, 8, 0.25, 0.3)
    star4(g, (CX + 3.0, 31.0, CZ), 1.1, TIME2, "ffffff", 9, 0.35)


def _dial(x, y, IVORY, INK, GOLD, GOLD_H, TIME):
    d = math.hypot(x, y)
    a = math.atan2(y, x)
    if d > 4.1:
        return GOLD
    if 3.0 < d < 3.9:   # 눈금 12개 (3 · 6 · 9 · 12 는 굵게)
        k = a / (2 * math.pi) * 12
        off = abs(k - round(k))
        main = round(k) % 3 == 0
        if off < (0.16 if main else 0.08):
            return INK
        return IVORY
    if d < 1.0:
        return TIME
    if 1.9 < d < 2.15:
        return lit(IVORY, 0.85)
    return IVORY


BUILDERS = {"hjw_a": scythe, "hjw_b": stella, "hjw_c": scepter, "hjw_d": needle}


def _display(els):
    """돌린 뒤 크기에 맞춰 GUI 축소 · 가운데 맞춤 (큰 무기라 슬롯을 넘지 않게)"""
    xs, ys = [], []
    c45 = math.cos(math.radians(-45))
    s45 = math.sin(math.radians(-45))
    for e in els:
        for x in (e["from"][0], e["to"][0]):
            for y in (e["from"][1], e["to"][1]):
                dx, dy = x - 8, y - 8
                xs.append(8 + dx * c45 - dy * s45)
                ys.append(8 + dx * s45 + dy * c45)
    w, h = max(xs) - min(xs), max(ys) - min(ys)
    cx, cy = (max(xs) + min(xs)) / 2, (max(ys) + min(ys)) / 2
    s = round(min(1.0, 16.8 / max(w, h)), 3)
    tr = [round(-(cx - 8) * s, 2), round(-(cy - 8) * s, 2), 0]
    return {
        "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
        "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
        "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.6, 0.6, 0.6]},
        "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.6, 0.6, 0.6]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4]},
        "gui": {"rotation": [0, 0, 0], "translation": tr, "scale": [s, s, s]},
        "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
        "fixed": {"rotation": [0, 180, 0], "translation": [tr[0] * -1, tr[1], 0], "scale": [s, s, s]},
    }


def build(name):
    old = VL.VS
    VL.VS = VS
    try:
        g = VL.Grid()
        BUILDERS[name](g)
        g.cells = {k: v for k, v in g.cells.items() if v[1]}
        pal = bm.Palette()
        m = bm.Model(pal)
        bm.CLAMP_AT_BUILD[0] = False
        VL.emit(g, m)
    finally:
        VL.VS = old
        bm.CLAMP_AT_BUILD[0] = True
    els = m.els
    for e in els:
        e.pop("_col", None)
        e.pop("_edge", None)
        e["rotation"] = {"axis": "z", "angle": -45, "origin": [8, 8, 8]}
        for key in ("from", "to"):
            e[key] = [max(-16, min(32, round(c, 4))) for c in e[key]]
    if len(pal.colors) > 256:
        raise SystemExit("히든 무기 팔레트 색이 256 개를 넘음: " + name)
    return els, list(pal.colors), _display(els)


def write(pack, ns, write_json):
    """모델 · 팔레트 텍스처를 쓰고 (바닐라 재질, CMD, 모델) 목록을 돌려줌. 강화(+7 · +10) 번호도 같은 모델"""
    import os
    out = []
    total = 0
    for name in BUILDERS:
        els, cols, disp = build(name)
        total += len(els)
        tex = os.path.join(pack, "assets", ns, "textures", "item", "hjw", name + ".png")
        os.makedirs(os.path.dirname(tex), exist_ok=True)
        pal = bm.Palette()
        pal.colors = cols
        pal.image().save(tex)
        write_json(os.path.join(pack, "assets", ns, "models", "item", "hjw", name + ".json"),
                   {"credit": "RpgCraft hidden weapon", "texture_size": [16, 16], "gui_light": "front",
                    "textures": {"0": ns + ":item/hjw/" + name, "particle": ns + ":item/hjw/" + name}, "elements": els, "display": disp})
        for off in (0, 3000, 5000):
            out.append((MATERIAL[name], CMD + off, ns + ":item/hjw/" + name))
    return out, total
