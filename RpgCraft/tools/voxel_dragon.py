"""태초의 용 아스트라 — 복셀 조각 (v5.8.2, 아머러스 워크샵 방식)

곡선 형태(척추 스플라인 · 타원체 · 뼈 튜브 · 날개 막 삼각형)를 VS 크기의 작은 정육면체로 깎아 만든다.
 - 보이지 않는 속 복셀은 버리고, 같은 색끼리 이웃한 복셀은 큰 상자로 합쳐(탐욕적 병합) 요소 수를 줄인다.
 - 상자마다 바깥에 드러난 면만 남긴다 (안쪽 면 제거).
좌표는 다른 보스 모델과 같은 공간 (x 가운데 8, 앞이 +z). 이후 boss_models.shrink 로 0.75 배 축소된다.
"""
import math

import voxel_lib
VS = voxel_lib.VS   # 복셀 한 칸 크기 (모델 단위) — v5.8.4: 공통 도구와 같게 (0.5)

# 색
BACK, BACK2, SIDE, SIDE2, SIDE3 = "2a1238", "341848", "43205e", "4f2870", "5b2e80"
BELLY, BELLY2, BELLY_EDGE = "d8b048", "b8902e", "8a6a1e"
HORN, HORN2, HORN_TIP = "efe3b8", "cfc08a", "fff7d8"
GOLD, GOLD2 = "ffd23f", "e0a82a"
EYE, EYE2 = "ff3a2a", "ffd060"
MOUTH, TOOTH = "4a0a18", "fffaf0"
WING_IN, WING_MID, WING_OUT, WING_RIM = "3e1c5e", "54287c", "6c3598", "8c4ab8"
BONE, BONE2 = "2a1238", "3a1a52"
STAR, STAR2 = "eee8ff", "fff3b0"
CORE, CORE2 = "ffffff", "c8b8ff"


def _h(*v):
    """결정적 난수 (0~1) — 같은 좌표면 늘 같은 값"""
    x = 0
    for a in v:
        x = (x * 1000003) ^ (int(a * 1000) & 0xFFFFFFFF)
    x = (x ^ (x >> 13)) * 1274126177 & 0xFFFFFFFF
    return (x & 0xFFFF) / 65535.0


class Grid:
    def __init__(self):
        self.cells = {}   # (i,j,k) -> (priority, color)

    def put(self, x, y, z, col, pri):
        k = (math.floor(x / VS), math.floor(y / VS), math.floor(z / VS))
        cur = self.cells.get(k)
        if cur is None or pri >= cur[0]:
            self.cells[k] = (pri, col)

    def ellipsoid(self, c, r, colf, pri):
        cx, cy, cz = c
        rx, ry, rz = r
        for i in range(math.floor((cx - rx) / VS), math.floor((cx + rx) / VS) + 1):
            for j in range(math.floor((cy - ry) / VS), math.floor((cy + ry) / VS) + 1):
                for k in range(math.floor((cz - rz) / VS), math.floor((cz + rz) / VS) + 1):
                    x, y, z = (i + 0.5) * VS, (j + 0.5) * VS, (k + 0.5) * VS
                    d = ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 + ((z - cz) / rz) ** 2
                    if d <= 1:
                        col = colf(x, y, z, d) if callable(colf) else colf
                        if col:
                            self.put(x, y, z, col, pri)

    def tube(self, pts, radii, colf, pri, steps=6):
        """점들을 잇는 굵기가 변하는 튜브 (구를 촘촘히 이어 붙임)"""
        for a in range(len(pts) - 1):
            p, q = pts[a], pts[a + 1]
            L = math.dist(p, q)
            n = max(2, int(L / (VS * 0.5)))
            for t in range(n + 1):
                f = t / n
                c = tuple(p[i] + (q[i] - p[i]) * f for i in range(3))
                r = radii[a] + (radii[a + 1] - radii[a]) * f
                self.ellipsoid(c, (max(r, VS * 0.55),) * 3, (lambda x, y, z, d, f=f, a=a: colf(a + f, d)) if callable(colf) else colf, pri)

    def triangle(self, a, b, c, colf, pri, scallop=None):
        """날개 막: 삼각형 면을 촘촘히 찍음. scallop=(끝변 시작, 끝변 끝) 이면 바깥 변을 안쪽으로 오목하게"""
        step = VS * 0.45
        L = max(math.dist(a, b), math.dist(a, c), math.dist(b, c))
        n = max(2, int(L / step))
        for i in range(n + 1):
            for j in range(n + 1 - i):
                u, v = i / n, j / n
                w = 1 - u - v
                if scallop is not None and u + v > 0:   # a 가 안쪽 꼭짓점, b-c 가 바깥 변
                    t = v / (u + v)
                    if w < 0.14 * (1 - (2 * t - 1) ** 2):
                        continue
                p = tuple(a[k] * w + b[k] * u + c[k] * v for k in range(3))
                col = colf(p, w) if callable(colf) else colf
                self.put(p[0], p[1], p[2], col, pri)


def _catmull(pts, n):
    out = []
    P = [pts[0]] + pts + [pts[-1]]
    for i in range(1, len(P) - 2):
        p0, p1, p2, p3 = P[i - 1], P[i], P[i + 1], P[i + 2]
        for s in range(n):
            t = s / n
            t2, t3 = t * t, t * t * t
            out.append(tuple(0.5 * (2 * p1[k] + (-p0[k] + p2[k]) * t + (2 * p0[k] - 5 * p1[k] + 4 * p2[k] - p3[k]) * t2 + (-p0[k] + 3 * p1[k] - 3 * p2[k] + p3[k]) * t3) for k in range(3)))
    out.append(pts[-1])
    return out


def _norm(v):
    L = math.sqrt(sum(a * a for a in v)) or 1
    return tuple(a / L for a in v)


def _sub(a, b):
    return tuple(a[i] - b[i] for i in range(3))


def _dot(a, b):
    return sum(a[i] * b[i] for i in range(3))


def build(g):
    # ---------------------------------------------------------- 척추: 꼬리 끝 → 몸 → 목 → 머리 밑 (앞발을 든 자세)
    ctrl = [(11.0, 2.2, -20.5), (10.4, 3.6, -14.5), (9.0, 6.2, -8.5), (8.0, 9.2, -2.5), (8.0, 11.2, 3.0),
            (8.0, 13.2, 8.0), (8.0, 16.6, 11.6), (8.0, 21.2, 14.2), (8.0, 25.2, 17.2)]
    rad = [0.55, 1.3, 2.4, 4.4, 5.4, 5.0, 3.3, 2.6, 2.3]
    spine = _catmull(ctrl, 14)
    rs = []
    for i in range(len(spine)):
        f = i / (len(spine) - 1) * (len(rad) - 1)
        a = min(int(f), len(rad) - 2)
        rs.append(rad[a] + (rad[a + 1] - rad[a]) * (f - a))
    arc = [0.0]
    for i in range(1, len(spine)):
        arc.append(arc[-1] + math.dist(spine[i], spine[i - 1]))
    for i, (c, r) in enumerate(zip(spine, rs)):
        T = _norm(_sub(spine[min(i + 1, len(spine) - 1)], spine[max(i - 1, 0)]))
        U = _norm(_sub((0, 1, 0), tuple(T[k] * T[1] for k in range(3))))
        s_arc = arc[i]

        def col(x, y, z, d, c=c, U=U, r=r, s_arc=s_arc):
            off = (x - c[0], y - c[1], z - c[2])
            v = _dot(off, U) / max(r, 0.5)
            if d < 0.55:
                return BACK   # 속 (보이지 않음)
            if v < -0.38:   # 배: 황금 비늘 판 (띠 무늬)
                band = int(s_arc / 1.35) % 2
                return BELLY_EDGE if abs(v + 0.38) < 0.12 else (BELLY if band == 0 else BELLY2)
            ang = math.atan2(off[0], _dot(off, U))
            pat = (int(s_arc / 0.9) + int((ang + 4) * 2.2)) % 3
            if _h(x, y, z) < 0.025:
                return STAR if _h(z, y, x) < 0.6 else STAR2   # 별빛 반점
            if v > 0.55:
                return (BACK, BACK2, BACK)[pat]
            return (SIDE, SIDE2, SIDE3)[pat]
        g.ellipsoid(c, (r * 1.08, r * 0.95, r * 1.0), col, 1)

    # ---------------------------------------------------------- 등가시 (금 끝)
    for i in range(6, len(spine) - 18, 5):
        c, r = spine[i], rs[i]
        if r < 0.9:
            continue
        T = _norm(_sub(spine[i + 1], spine[i - 1]))
        U = _norm(_sub((0, 1, 0), tuple(T[k] * T[1] for k in range(3))))
        base = tuple(c[k] + U[k] * r * 0.85 for k in range(3))
        L = 1.2 + 1.9 * r / 5.4
        tip = tuple(base[k] + U[k] * L - T[k] * L * 0.55 for k in range(3))
        g.tube([base, tip], [0.55 + 0.1 * r, 0.12], lambda f, d: GOLD if f > 0.62 else BONE2, 3)

    # ---------------------------------------------------------- 꼬리 끝 가시 부채
    tail = spine[0]
    for (dx, dy) in ((0, 2.4), (2.2, 0.9), (-2.2, 0.9), (1.4, 1.9), (-1.4, 1.9)):
        g.tube([tail, (tail[0] + dx, tail[1] + dy, tail[2] - 2.6)], [0.5, 0.12], lambda f, d: GOLD if f > 0.5 else GOLD2, 3)

    # ---------------------------------------------------------- 머리
    def skull_col(x, y, z, d):
        if d < 0.6:
            return BACK
        if _h(x, y, z) < 0.03:
            return STAR
        return SIDE2 if y < 26.6 else (SIDE if (int(z / 0.8) + int(x / 0.8)) % 2 else SIDE3)
    g.ellipsoid((8, 27.1, 19.8), (3.3, 2.85, 3.5), skull_col, 4)                          # 두개골
    g.ellipsoid((8, 26.4, 24.0), (2.35, 1.7, 3.4), skull_col, 4)                         # 주둥이
    g.ellipsoid((8, 27.4, 24.6), (1.7, 1.0, 2.5), lambda x, y, z, d: SIDE3, 4)          # 콧등
    g.ellipsoid((8, 25.1, 23.7), (2.2, 0.62, 3.2), lambda x, y, z, d: MOUTH, 5)        # 벌린 입 속
    g.ellipsoid((8, 24.3, 23.3), (2.0, 0.95, 3.3), lambda x, y, z, d: BELLY2 if y < 24.1 else SIDE2, 4)   # 아래턱
    for sx in (-1, 1):
        for z in (21.0, 22.3, 23.6, 24.9, 26.2):                                        # 이빨 (위 · 아래)
            g.put(8 + sx * 1.55, 25.5, z, TOOTH, 6)
            g.put(8 + sx * 1.3, 24.8, z + 0.4, TOOTH, 6)
        g.put(8 + sx * 0.7, 25.3, 26.9, TOOTH, 6)                                        # 송곳니
        g.put(8 + sx * 0.7, 25.3 - VS, 26.9, TOOTH, 6)
        g.ellipsoid((8 + sx * 2.3, 27.8, 21.5), (0.7, 0.55, 0.8), lambda x, y, z, d: EYE2 if d < 0.3 else EYE, 7)   # 눈
        g.ellipsoid((8 + sx * 2.1, 28.75, 21.3), (1.15, 0.5, 1.9), lambda x, y, z, d: BACK2, 5)                    # 눈썹 능선
        g.put(8 + sx * 0.8, 26.9, 27.2, "12061c", 6)                                    # 콧구멍
        # 큰 뿔: 뒤로 휘어 올라감
        g.tube([(8 + sx * 1.6, 28.8, 19.4), (8 + sx * 2.5, 31.0, 17.0), (8 + sx * 3.2, 33.0, 14.0), (8 + sx * 3.1, 34.4, 11.4), (8 + sx * 2.6, 35.0, 9.8)],
               [0.85, 0.72, 0.55, 0.38, 0.2], lambda f, d: HORN_TIP if f > 3.3 else (HORN if int(f * 3) % 2 == 0 else HORN2), 5)
        # 작은 뿔 · 볼 가시
        g.tube([(8 + sx * 2.6, 27.9, 18.4), (8 + sx * 4.3, 28.8, 15.8), (8 + sx * 5.4, 29.4, 13.8)], [0.5, 0.35, 0.15], lambda f, d: HORN2 if f < 1.4 else HORN_TIP, 5)
        for (y0, z0) in ((26.4, 18.6), (25.4, 19.4)):
            g.tube([(8 + sx * 2.7, y0, z0), (8 + sx * 4.2, y0 - 0.4, z0 - 1.6)], [0.35, 0.1], lambda f, d: HORN2, 5)
    for k in range(4):                                                                   # 머리 뒤 볏
        z = 17.2 - k * 1.1
        g.tube([(8, 28.8 - k * 0.2, z), (8, 30.2 - k * 0.3, z - 0.9)], [0.35, 0.1], lambda f, d: GOLD if f > 0.5 else BONE2, 5)

    # ---------------------------------------------------------- 다리 · 발톱
    bone = lambda f, d: SIDE2 if d > 0.5 else BACK
    for sx in (-1, 1):
        # 뒷다리 (굵은 허벅지 → 무릎 → 발목 → 발)
        g.ellipsoid((8 + sx * 4.4, 8.4, -1.4), (2.3, 2.9, 3.0), lambda x, y, z, d: SIDE3 if d > 0.6 else BACK, 2)
        g.tube([(8 + sx * 4.8, 7.0, -0.6), (8 + sx * 5.4, 4.4, 2.2), (8 + sx * 5.1, 1.8, -0.2), (8 + sx * 5.1, 0.7, 2.0)], [1.9, 1.4, 1.0, 0.9], bone, 2)
        for dx in (-0.8, 0, 0.8):
            g.tube([(8 + sx * 5.1 + dx, 0.6, 2.6), (8 + sx * 5.1 + dx * 1.3, 0.35, 4.1), (8 + sx * 5.1 + dx * 1.4, 0.0, 4.7)], [0.36, 0.25, 0.12], lambda f, d: HORN if f > 0.8 else HORN2, 3)
        # 앞다리 (들어 올림)
        g.tube([(8 + sx * 4.0, 12.2, 7.6), (8 + sx * 5.2, 10.8, 11.6), (8 + sx * 4.8, 13.8, 14.8), (8 + sx * 4.5, 15.0, 16.4)], [1.8, 1.3, 1.0, 0.85], bone, 2)
        for dx in (-0.7, 0, 0.7):
            g.tube([(8 + sx * 4.5 + dx, 15.3, 16.9), (8 + sx * 4.5 + dx * 1.3, 15.2, 18.2), (8 + sx * 4.5 + dx * 1.4, 14.4, 18.8)], [0.32, 0.22, 0.1], lambda f, d: HORN if f > 0.9 else HORN2, 3)

    # ---------------------------------------------------------- 가슴: 별의 핵
    g.ellipsoid((8, 12.8, 13.2), (1.25, 1.25, 0.9), lambda x, y, z, d: CORE if d < 0.35 else CORE2, 8)
    for a in range(12):
        t = a * math.pi / 6
        g.put(8 + math.cos(t) * 1.8, 12.8 + math.sin(t) * 1.8, 12.9, GOLD, 7)

    # ---------------------------------------------------------- 날개: 팔뼈 · 손가락 4개 · 오목한 막
    for sx in (-1, 1):
        root, elbow, wrist = (8 + sx * 3.4, 16.2, 6.6), (8 + sx * 11.0, 24.2, 3.2), (8 + sx * 17.0, 33.0, -0.6)
        tips = [(8 + sx * 24.0, 38.8, -5.2), (8 + sx * 26.5, 30.2, -8.4), (8 + sx * 25.2, 21.0, -10.4), (8 + sx * 20.2, 12.2, -9.6)]
        trail = (8 + sx * 5.6, 12.6, -0.4)

        def mem(p, w, wrist=wrist):
            if _h(p[0], p[1], p[2]) < 0.035:
                return STAR if _h(p[2], p[0], p[1]) < 0.55 else STAR2
            dist = math.dist(p, wrist)
            return WING_IN if dist < 5 else WING_MID if dist < 10 else WING_OUT
        for a in range(3):   # 손가락 사이 막 (바깥 변 오목)
            g.triangle(wrist, tips[a], tips[a + 1], mem, 1, scallop=True)
        g.triangle(wrist, tips[3], elbow, mem, 1)
        g.triangle(elbow, tips[3], trail, mem, 1, scallop=True)
        g.triangle(root, elbow, trail, mem, 1)
        g.tube([root, elbow, wrist], [1.05, 0.8, 0.6], lambda f, d: BONE2 if d > 0.4 else BONE, 3)   # 팔뼈
        g.tube([wrist, (wrist[0] + sx * 0.4, wrist[1] + 1.8, wrist[2] + 0.6)], [0.45, 0.12], lambda f, d: GOLD, 4)   # 날개 발톱
        for t in tips:   # 손가락 뼈
            mid = tuple(wrist[k] + (t[k] - wrist[k]) * 0.5 for k in range(3))
            g.tube([wrist, mid, t], [0.42, 0.3, 0.14], lambda f, d: BONE2 if f < 1.7 else GOLD2, 3)
        for a in range(4):   # 막 가장자리 테
            p, q = tips[a], (tips[a + 1] if a < 3 else elbow)
            for s in range(12):
                f = s / 11
                if 0.2 < f < 0.8:
                    continue
                x = tuple(p[k] + (q[k] - p[k]) * f for k in range(3))
                g.put(x[0], x[1], x[2], WING_RIM, 2)

    # ---------------------------------------------------------- 주위를 떠도는 별 조각
    for (x, y, z) in [(-9, 30, 9), (25, 27, 7), (8, 40, -7), (-6, 12, 21), (22, 15, 22)]:
        g.ellipsoid((x, y, z), (0.7, 0.7, 0.7), lambda a, b, c, d: STAR if d > 0.3 else CORE, 6)


def emit(g, m):
    """v5.8.4: 공통 emit (음영 · 팔레트 줄이기 포함)"""
    return voxel_lib.emit(g, m)
