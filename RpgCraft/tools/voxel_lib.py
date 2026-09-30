"""복셀 조각 공통 도구 (v5.8.3) — 곡선 형태를 작은 정육면체로 깎아 보스 모델을 만든다 (아머러스 워크샵 방식)

좌표: 기존 보스 모델과 같은 공간 (x 가운데 8, 발 y=0, 앞이 +z). boss_models.shrink 로 0.75 배 축소된다.
 - Grid: 복셀 모음 (우선순위가 높은 색이 덮어씀)
 - 기본 도형: ellipsoid · tube(굵기가 변하는 관) · box · cone · slab(얇은 판) · triangle(막) · surface(휘어진 천)
 - 인체 · 네발짐승 틀과 장비(갑옷 · 로브 · 망토 · 머리칼 · 날개 · 무기)
 - emit: 속 복셀 제거 → 같은 색 병합 → 드러난 면만
"""
import math

VS = 0.75


# ------------------------------------------------------------------ 색 도우미
def hx(c, k):
    c = c.lstrip("#")
    return "%02x%02x%02x" % tuple(max(0, min(255, int(int(c[i:i + 2], 16) * k))) for i in (0, 2, 4))


def mix(a, b, t):
    a, b = a.lstrip("#"), b.lstrip("#")
    return "%02x%02x%02x" % tuple(int(int(a[i:i + 2], 16) * (1 - t) + int(b[i:i + 2], 16) * t) for i in (0, 2, 4))


def rnd(*v):
    x = 0
    for a in v:
        x = (x * 1000003) ^ (int(a * 1000) & 0xFFFFFFFF)
    x = (x ^ (x >> 13)) * 1274126177 & 0xFFFFFFFF
    return (x & 0xFFFF) / 65535.0


def tex(base, x, y, z, amt=0.12):
    """재질 느낌: 좌표마다 밝기를 조금씩 (3단계) 바꿈"""
    r = rnd(math.floor(x / VS), math.floor(y / VS), math.floor(z / VS))
    return base if r < 0.5 else hx(base, 1 - amt) if r < 0.78 else hx(base, 1 + amt)


def V(*a):
    return tuple(float(x) for x in a)


def add(a, b):
    return tuple(a[i] + b[i] for i in range(3))


def sub(a, b):
    return tuple(a[i] - b[i] for i in range(3))


def mul(a, k):
    return tuple(x * k for x in a)


def lerp(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def norm(v):
    L = math.sqrt(sum(x * x for x in v)) or 1
    return tuple(x / L for x in v)


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def dot(a, b):
    return sum(a[i] * b[i] for i in range(3))


def catmull(pts, n):
    out = []
    P = [pts[0]] + list(pts) + [pts[-1]]
    for i in range(1, len(P) - 2):
        p0, p1, p2, p3 = P[i - 1], P[i], P[i + 1], P[i + 2]
        for s in range(n):
            t = s / n
            t2, t3 = t * t, t * t * t
            out.append(tuple(0.5 * (2 * p1[k] + (-p0[k] + p2[k]) * t + (2 * p0[k] - 5 * p1[k] + 4 * p2[k] - p3[k]) * t2
                                    + (-p0[k] + 3 * p1[k] - 3 * p2[k] + p3[k]) * t3) for k in range(3)))
    out.append(tuple(pts[-1]))
    return out


def _call(colf, *a):
    return colf(*a) if callable(colf) else colf


# ------------------------------------------------------------------ 복셀 모음
class Grid:
    def __init__(self):
        self.cells = {}

    def put(self, x, y, z, col, pri=1):
        if not col:
            return
        k = (math.floor(x / VS), math.floor(y / VS), math.floor(z / VS))
        cur = self.cells.get(k)
        if cur is None or pri >= cur[0]:
            self.cells[k] = (pri, col)

    def _rng(self, a, b):
        return range(math.floor(a / VS), math.floor(b / VS) + 1)

    def ellipsoid(self, c, r, colf, pri=1, inner=None):
        """colf(x,y,z,d) — d: 중심 0 ~ 표면 1. inner 가 있으면 d<inner 인 속은 건드리지 않음 (껍데기 · 갑옷)"""
        cx, cy, cz = c
        rx, ry, rz = r
        for i in self._rng(cx - rx, cx + rx):
            for j in self._rng(cy - ry, cy + ry):
                for k in self._rng(cz - rz, cz + rz):
                    x, y, z = (i + 0.5) * VS, (j + 0.5) * VS, (k + 0.5) * VS
                    d = ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 + ((z - cz) / rz) ** 2
                    if d <= 1 and (inner is None or d >= inner):
                        self.put(x, y, z, _call(colf, x, y, z, d), pri)

    def sphere(self, c, r, colf, pri=1):
        self.ellipsoid(c, (r, r, r), colf, pri)

    def tube(self, pts, radii, colf, pri=1, smooth=False, squash=(1, 1)):
        """점들을 잇는 관. colf(x,y,z,d,f) — f: 0 ~ len(pts)-1 (구간 위치). squash=(x배, z배)"""
        if smooth and len(pts) > 2:
            n = 8
            P = catmull(pts, n)
            R = []
            for i in range(len(P)):
                f = i / (len(P) - 1) * (len(radii) - 1)
                a = min(int(f), len(radii) - 2)
                R.append(radii[a] + (radii[a + 1] - radii[a]) * (f - a))
            pts, radii = P, R
        segs = len(pts) - 1
        for a in range(segs):
            p, q = pts[a], pts[a + 1]
            L = math.dist(p, q)
            n = max(2, int(L / (VS * 0.45)))
            for t in range(n + 1):
                f = t / n
                c = lerp(p, q, f)
                r = max(radii[a] + (radii[a + 1] - radii[a]) * f, VS * 0.5)
                ff = (a + f) * ((len(radii) - 1) / segs if smooth else 1)
                self.ellipsoid(c, (r * squash[0], r, r * squash[1]), (lambda x, y, z, d, ff=ff: _call(colf, x, y, z, d, ff)) if callable(colf) else colf, pri)

    def box(self, a, b, colf, pri=1):
        for i in self._rng(min(a[0], b[0]), max(a[0], b[0]) - 1e-6):
            for j in self._rng(min(a[1], b[1]), max(a[1], b[1]) - 1e-6):
                for k in self._rng(min(a[2], b[2]), max(a[2], b[2]) - 1e-6):
                    x, y, z = (i + 0.5) * VS, (j + 0.5) * VS, (k + 0.5) * VS
                    self.put(x, y, z, _call(colf, x, y, z), pri)

    def cone(self, base, tip, r0, r1, colf, pri=1):
        """base 에서 tip 까지 반지름 r0 → r1 (가시 · 뿔 · 모자)"""
        self.tube([base, tip], [r0, r1], (lambda x, y, z, d, f: _call(colf, x, y, z, f)) if callable(colf) else colf, pri)

    def slab(self, p0, p1, wv, thick, colf, pri=1):
        """p0→p1 길이, wv(가로 방향 벡터, 길이=폭) · 두께 thick 인 판 (칼날 · 방패 · 판금). colf(x,y,z,u,v) u:길이 0~1 v:폭 -1~1"""
        L = math.dist(p0, p1)
        W = math.sqrt(dot(wv, wv))
        axis = norm(sub(p1, p0))
        wn = norm(wv)
        nrm = norm(cross(axis, wn))
        nu, nv = max(2, int(L / (VS * 0.4))), max(1, int(W / (VS * 0.4)))
        nt = max(1, int(thick / (VS * 0.5)))
        for i in range(nu + 1):
            for j in range(-nv, nv + 1):
                for t in range(nt + 1):
                    u, v = i / nu, j / max(1, nv)
                    tt = (t / nt - 0.5) * thick if nt > 0 else 0
                    p = add(add(lerp(p0, p1, u), mul(wn, v * W / 2 if nv else 0)), mul(nrm, tt))
                    self.put(p[0], p[1], p[2], _call(colf, p[0], p[1], p[2], u, v), pri)

    def triangle(self, a, b, c, colf, pri=1, scallop=0.0, thick=0):
        """막: a 안쪽 꼭짓점, b-c 바깥 변. scallop>0 이면 바깥 변을 오목하게. colf(p, w) w: a 쪽 가중치"""
        L = max(math.dist(a, b), math.dist(a, c), math.dist(b, c))
        n = max(2, int(L / (VS * 0.42)))
        nrm = norm(cross(sub(b, a), sub(c, a)))
        layers = [0] if thick <= 0 else [-thick / 2, 0, thick / 2]
        for i in range(n + 1):
            for j in range(n + 1 - i):
                u, v = i / n, j / n
                w = 1 - u - v
                if scallop and u + v > 0:
                    t = v / (u + v)
                    if w < scallop * (1 - (2 * t - 1) ** 2):
                        continue
                p = tuple(a[k] * w + b[k] * u + c[k] * v for k in range(3))
                col = _call(colf, p, w)
                for off in layers:
                    q = add(p, mul(nrm, off))
                    self.put(q[0], q[1], q[2], col, pri)

    def surface(self, fn, nu, nv, colf, pri=1):
        """휘어진 천: fn(u,v) → 점 (u,v 0~1). colf(p,u,v)"""
        for i in range(nu + 1):
            for j in range(nv + 1):
                u, v = i / nu, j / nv
                p = fn(u, v)
                if p is None:
                    continue
                self.put(p[0], p[1], p[2], _call(colf, p, u, v), pri)

    def ring(self, c, r, thick, colf, pri=1, axis="y", tilt=0.0):
        """고리 (후광 · 왕관 테 · 허리띠)"""
        n = max(12, int(2 * math.pi * r / (VS * 0.4)))
        for i in range(n):
            a = i * 2 * math.pi / n
            if axis == "y":
                p = (c[0] + math.cos(a) * r, c[1] + math.sin(a) * r * math.sin(tilt), c[2] + math.sin(a) * r * math.cos(tilt))
            else:
                p = (c[0] + math.cos(a) * r, c[1] + math.sin(a) * r, c[2])
            self.sphere(p, thick, (lambda x, y, z, d, a=a: _call(colf, x, y, z, a)) if callable(colf) else colf, pri)


# ------------------------------------------------------------------ 합치기 · 내보내기
def emit(g, m):
    solid = g.cells
    filled = set(solid)
    visible = {}
    for k, (pri, col) in solid.items():
        i, j, l = k
        if all(n in filled for n in ((i + 1, j, l), (i - 1, j, l), (i, j + 1, l), (i, j - 1, l), (i, j, l + 1), (i, j, l - 1))):
            continue
        visible[k] = col
    used = set()
    boxes = []
    for k in sorted(visible, key=lambda t: (t[1], t[2], t[0])):
        if k in used:
            continue
        col = visible[k]
        i0, j0, l0 = k

        def ok(c):
            return c in visible and c not in used and visible[c] == col
        i1 = i0
        while ok((i1 + 1, j0, l0)):
            i1 += 1
        l1 = l0
        while all(ok((i, j0, l1 + 1)) for i in range(i0, i1 + 1)):
            l1 += 1
        j1 = j0
        while all(ok((i, j1 + 1, l)) for i in range(i0, i1 + 1) for l in range(l0, l1 + 1)):
            j1 += 1
        for i in range(i0, i1 + 1):
            for j in range(j0, j1 + 1):
                for l in range(l0, l1 + 1):
                    used.add((i, j, l))
        boxes.append((i0, j0, l0, i1, j1, l1, col))
    n = 0
    for (i0, j0, l0, i1, j1, l1, col) in boxes:
        faces = set()
        if any((i0 - 1, j, l) not in filled for j in range(j0, j1 + 1) for l in range(l0, l1 + 1)): faces.add("west")
        if any((i1 + 1, j, l) not in filled for j in range(j0, j1 + 1) for l in range(l0, l1 + 1)): faces.add("east")
        if any((i, j0 - 1, l) not in filled for i in range(i0, i1 + 1) for l in range(l0, l1 + 1)): faces.add("down")
        if any((i, j1 + 1, l) not in filled for i in range(i0, i1 + 1) for l in range(l0, l1 + 1)): faces.add("up")
        if any((i, j, l0 - 1) not in filled for i in range(i0, i1 + 1) for j in range(j0, j1 + 1)): faces.add("north")
        if any((i, j, l1 + 1) not in filled for i in range(i0, i1 + 1) for j in range(j0, j1 + 1)): faces.add("south")
        if not faces:
            continue
        m.box(i0 * VS, j0 * VS, l0 * VS, (i1 + 1) * VS, (j1 + 1) * VS, (l1 + 1) * VS, col)
        e = m.els[-1]
        e["faces"] = {f: v for f, v in e["faces"].items() if f in faces}
        e["_edge"] = True
        n += 1
    return n


# ------------------------------------------------------------------ 인체 틀
class Body:
    """인체 기준점 모음 (장비를 붙일 자리)"""
    pass


def figure(g, s=1.0, w=1.0, skin="e8c4a0", top="555566", legs="444455", boots=None, arms=None, hands=None,
           weapon_arm="right", reach=True, robe=None, pri=2, bulk=1.0, head=True, face=True, eye="202020"):
    """사람 몸: 다리 · 골반 · 몸통 · 가슴 · 목 · 머리 · 팔 · 손. weapon_arm 쪽 팔은 앞으로 들어 무기를 쥠. robe 면 다리 대신 로브는 따로(robe()).
    반환: Body (hand_r · hand_l · head · head_top · chest · back_z · shoulder_r · shoulder_l · waist · hip)"""
    B = Body()
    arms = arms or top
    hands = hands or skin
    boots = boots or hx(legs, 0.7)
    Y = lambda v: v * s
    cx, cz = 8.0, 8.0
    B.s, B.w = s, w
    # 다리
    if not robe:
        for sx in (-1, 1):
            hip, knee, ank, toe = (cx + sx * 1.8 * w, Y(10.4), cz), (cx + sx * 2.0 * w, Y(5.8), cz + 0.3), (cx + sx * 2.0 * w, Y(1.4), cz - 0.1), (cx + sx * 2.0 * w, Y(0.6), cz + 1.5)
            g.tube([hip, knee], [1.75 * w * bulk, 1.25 * w * bulk], lambda x, y, z, d, f: tex(legs, x, y, z), pri)
            g.tube([knee, ank], [1.25 * w * bulk, 0.95 * w * bulk], lambda x, y, z, d, f: tex(legs, x, y, z), pri)
            g.ellipsoid(add(knee, (0, 0.2, 0.5)), (1.2 * w, 1.0, 0.9), lambda x, y, z, d: tex(hx(legs, 1.15), x, y, z), pri)
            g.tube([ank, toe], [1.05 * w, 0.9 * w], lambda x, y, z, d, f: tex(boots, x, y, z), pri + 1, squash=(1.1, 1))
            g.box((ank[0] - 1.1 * w, 0, cz - 1.4), (ank[0] + 1.1 * w, Y(0.7), cz + 2.6), lambda x, y, z: hx(boots, 0.8), pri + 1)
    # 골반 · 몸통 · 가슴
    g.ellipsoid((cx, Y(11.0), cz), (3.0 * w * bulk, Y(1.9), 2.1 * bulk), lambda x, y, z, d: tex(legs, x, y, z), pri)
    g.ellipsoid((cx, Y(14.6), cz), (3.2 * w * bulk, Y(3.4), 2.2 * bulk), lambda x, y, z, d: tex(top, x, y, z), pri)
    g.ellipsoid((cx, Y(17.6), cz + 0.2), (4.0 * w * bulk, Y(2.5), 2.5 * bulk), lambda x, y, z, d: tex(top, x, y, z), pri)
    # 목 · 머리
    g.tube([(cx, Y(19.4), cz), (cx, Y(21.2), cz + 0.2)], [1.15 * w, 1.05 * w], lambda x, y, z, d, f: skin, pri)
    B.head = (cx, Y(23.0), cz + 0.3)
    B.head_r = (2.45 * s, 2.75 * s, 2.55 * s)
    if head:
        g.ellipsoid(B.head, B.head_r, lambda x, y, z, d: tex(skin, x, y, z, 0.05), pri)
        g.ellipsoid((cx, Y(21.9), cz + 1.4), (1.7 * s, 1.2 * s, 1.7 * s), lambda x, y, z, d: skin, pri)   # 턱
        if face:
            for sx in (-1, 1):
                g.put(cx + sx * 0.95 * s, Y(23.3), cz + 0.3 + 2.45 * s, eye, pri + 5)
                g.put(cx + sx * 0.95 * s, Y(23.95), cz + 0.3 + 2.35 * s, hx(skin, 0.75), pri + 4)   # 눈썹
            g.put(cx, Y(22.6), cz + 0.3 + 2.65 * s, hx(skin, 0.88), pri + 4)                         # 코
            g.put(cx, Y(21.9), cz + 0.3 + 2.45 * s, hx(skin, 0.6), pri + 4)                          # 입
    B.head_top = (cx, Y(23.0) + 2.75 * s, cz + 0.3)
    # 팔
    for sx in (-1, 1):
        weapon = (sx == 1) == (weapon_arm == "right") and reach
        sh = (cx + sx * 4.3 * w * bulk, Y(19.0), cz)
        el = (cx + sx * 5.3 * w * bulk, Y(15.0), cz + (1.2 if weapon else 0.4))
        wr = (cx + sx * 5.3 * w * bulk, Y(13.0), cz + 3.6) if weapon else (cx + sx * 5.5 * w * bulk, Y(11.2), cz + 1.0)
        g.ellipsoid(sh, (1.7 * w * bulk, 1.7, 1.7), lambda x, y, z, d: tex(arms, x, y, z), pri)
        g.tube([sh, el], [1.35 * w * bulk, 1.1 * w * bulk], lambda x, y, z, d, f: tex(arms, x, y, z), pri)
        g.tube([el, wr], [1.1 * w * bulk, 0.9 * w * bulk], lambda x, y, z, d, f: tex(arms, x, y, z), pri)
        hand = add(wr, (0, -0.2, 0.4 if weapon else 0))
        g.ellipsoid(hand, (0.95 * w, 1.05, 0.95), lambda x, y, z, d: hands, pri + 1)
        if sx == 1:
            B.hand_r, B.shoulder_r, B.elbow_r = hand, sh, el
        else:
            B.hand_l, B.shoulder_l, B.elbow_l = hand, sh, el
    B.chest = (cx, Y(17.4), cz + 2.6 * bulk)
    B.back_z = cz - 2.6 * bulk
    B.waist = (cx, Y(12.4), cz)
    B.hip = (cx, Y(10.4), cz)
    return B


def armor(g, B, plate, trim, gem=None, pri=4, spikes=0, belt=True, greaves=True, tassets=True):
    """판금: 가슴판(두 겹) · 층진 견갑 · 팔 보호대 · 벨트 · 치마 판 · 정강이 판"""
    s, w = B.s, B.w
    cx, cz = 8.0, 8.0
    cy, ry = 17.6 * s, 2.75 * s
    pc = lambda x, y, z, d: trim if (y < cy - ry * 0.72 or y > cy + ry * 0.8 or abs(x - cx) < 0.4 and z > cz + 1.5) else tex(plate, x, y, z, 0.05)
    g.ellipsoid((cx, cy, cz + 0.4), (4.25 * w, ry, 2.75), pc, pri, inner=0.62)          # 가슴판 (위 · 아래 테 · 가운데 능선)
    g.ellipsoid((cx, 14.4 * s, cz + 0.2), (3.45 * w, 1.9 * s, 2.45), lambda x, y, z, d: tex(hx(plate, 0.88), x, y, z, 0.05), pri, inner=0.6)
    for k in range(3):   # 복부 판 줄
        g.ellipsoid((cx, (13.0 + k * 1.15) * s, cz + 0.3), (3.3 * w - k * 0.1, 0.35, 2.5), lambda x, y, z, d: trim, pri + 1, inner=0.75)
    if gem:
        g.ellipsoid((cx, 17.6 * s, cz + 3.05), (0.8, 0.8, 0.45), lambda x, y, z, d: "ffffff" if d < 0.25 else gem, pri + 3)
    for sx in (-1, 1):   # 층진 견갑
        sh = B.shoulder_r if sx == 1 else B.shoulder_l
        for k in range(3):
            c0 = add(sh, (sx * 0.4, 1.0 - k * 0.9, 0))
            g.ellipsoid(c0, (2.5 * w - k * 0.1, 1.0, 2.35), lambda x, y, z, d, c0=c0, k=k: trim if y < c0[1] - 0.55 else tex(plate if k % 2 == 0 else hx(plate, 0.88), x, y, z, 0.05), pri + k, inner=0.55)
        for t in range(spikes):
            base = add(sh, (sx * (0.8 + t * 0.9), 1.9 - t * 0.35, -0.4 + t * 0.5))
            g.cone(base, add(base, (sx * 0.9, 2.2 - t * 0.4, -0.3)), 0.55, 0.12, lambda x, y, z, f: trim if f > 0.6 else hx(plate, 0.7), pri + 3)
        el = B.elbow_r if sx == 1 else B.elbow_l
        hand = B.hand_r if sx == 1 else B.hand_l
        g.tube([lerp(el, hand, 0.25), lerp(el, hand, 0.85)], [1.35 * w, 1.2 * w], lambda x, y, z, d, f: trim if f > 0.85 or f < 0.1 else tex(plate, x, y, z, 0.05), pri)   # 팔 보호대
        if greaves and hasattr(B, "hip"):
            kx = cx + sx * 2.0 * w
            g.tube([(kx, 5.6 * s, cz + 0.6), (kx, 1.8 * s, cz + 0.4)], [1.45 * w, 1.2 * w], lambda x, y, z, d, f: trim if f < 0.08 else tex(plate, x, y, z, 0.05), pri, squash=(1, 1.05))
            g.ellipsoid((kx, 5.9 * s, cz + 1.3), (1.2 * w, 1.0, 0.9), lambda x, y, z, d: trim, pri + 1)
    if belt:
        g.ellipsoid((cx, 11.9 * s, cz), (3.4 * w, 0.75, 2.45), lambda x, y, z, d: trim, pri + 1, inner=0.7)
        g.ellipsoid((cx, 11.9 * s, cz + 2.5), (0.9, 0.8, 0.4), lambda x, y, z, d: gem or hx(trim, 1.2), pri + 2)
    if tassets:
        for k, x0 in enumerate((-2.4, -0.8, 0.8)):
            g.slab((cx + x0 * w + 0.75, 11.3 * s, cz + 2.4), (cx + x0 * w + 0.75, 7.8 * s, cz + 2.9), (1.45 * w, 0, 0), 0.5,
                   lambda x, y, z, u, v: trim if u > 0.9 or abs(v) > 0.85 else tex(plate, x, y, z, 0.05), pri + 1)


def robe(g, B, col, col2, trim, pri=2, flare=5.6, hem_ragged=False):
    """허리에서 바닥까지 넓어지는 로브 (세로 주름 · 밑단 테)"""
    s = B.s
    top_y, r0, r1 = 12.6 * s, 3.1 * B.w, flare * B.w
    for j in range(0, int(top_y / VS) + 1):
        y = (j + 0.5) * VS
        t = max(0.0, 1 - y / top_y)
        r = r0 + (r1 - r0) * (t ** 1.3)
        n = max(12, int(2 * math.pi * r / (VS * 0.5)))
        for i in range(n):
            a = i * 2 * math.pi / n
            fold = 0.25 * math.sin(a * 7)
            for dr in (0, -VS * 0.9):
                x, z = 8 + math.cos(a) * (r + fold + dr), 8 + math.sin(a) * (r + fold + dr) * 0.85
                if hem_ragged and y < 1.2 and rnd(i, 3) < 0.35:
                    continue
                c = trim if y < 0.8 else (col if int((a / (2 * math.pi)) * 14) % 2 == 0 else col2)
                g.put(x, y, z, c, pri)
        # 속 채움 (보이지 않지만 구멍 방지)
    g.ellipsoid((8, top_y * 0.55, 8), (r0 + 0.8, top_y * 0.55, (r0 + 0.8) * 0.85), lambda x, y, z, d: col2, pri - 1)


def cape(g, B, col, trim, pri=1, length=None, width=None, ragged=False, emblem=None):
    """어깨에서 바닥까지 늘어지는 망토 (주름 · 테 · 문장)"""
    s = B.s
    top = 19.4 * s
    bottom = length if length is not None else 0.8
    W = width if width is not None else 4.0 * B.w
    z0 = B.back_z - 0.3

    def fn(u, v):
        y = top - (top - bottom) * v
        x = 8 + (u * 2 - 1) * (W + v * 1.6)
        z = z0 - v * 2.6 - 0.35 * math.sin(u * math.pi * 5) * v
        if ragged and v > 0.9 and rnd(int(u * 40), 7) < 0.4:
            return None
        return (x, y, z)

    def colf(p, u, v):
        if u < 0.04 or u > 0.96 or v > 0.97:
            return trim
        if emblem and abs(u - 0.5) < 0.1 and 0.3 < v < 0.45:
            return emblem
        return col if int(u * 10) % 2 == 0 else hx(col, 0.85)
    g.surface(fn, int(2 * W / (VS * 0.4)), int((top - bottom) / (VS * 0.4)), colf, pri)
    g.ellipsoid((8, top, B.back_z + 0.4), (4.6 * B.w, 0.8, 1.6), lambda x, y, z, d: trim, pri + 3, inner=0.4)   # 어깨 걸쇠 띠


def hair(g, B, col, length=13.0, pri=3, width=2.4):
    """머리칼: 정수리 덮개 + 등으로 흘러내리는 머리 (가닥 무늬)"""
    hcx, hcy, hcz = B.head
    rx, ry, rz = B.head_r
    g.ellipsoid((hcx, hcy + 0.35, hcz - 0.35), (rx + 0.35, ry + 0.3, rz + 0.2), lambda x, y, z, d: tex(col, x, y, z) if (z < hcz + rz * 0.35 or y > hcy + ry * 0.55) else None, pri, inner=0.62)

    def fn(u, v):
        y = hcy + 0.5 - length * v
        x = hcx + (u * 2 - 1) * (width + v * 0.8)
        z = hcz - rz - 0.2 - v * 1.2 + 0.4 * math.sin(u * 9)
        return (x, y, z)
    g.surface(fn, 24, int(length / (VS * 0.4)), lambda p, u, v: col if int(u * 12) % 2 else hx(col, 0.85), pri)


def wings(g, B, c1, c2, rim=None, pri=1, span=17.0, rise=11.0, feathers=6, kind="feather"):
    """등 뒤로 펼친 날개. kind: feather(깃털 · 천사) · bat(막 · 악마) · shard(조각 · 얼음/공허)"""
    for sx in (-1, 1):
        root = (8 + sx * 1.6, 18.8 * B.s, B.back_z - 0.4)
        elbow = (8 + sx * span * 0.45, 18.8 * B.s + rise * 0.55, B.back_z - 2.6)
        tip = (8 + sx * span, 18.8 * B.s + rise, B.back_z - 4.4)
        g.tube([root, elbow, tip], [0.9, 0.7, 0.35], lambda x, y, z, d, f: hx(c1, 0.8), pri + 1, smooth=True)
        if kind == "bat":
            fingers = [(8 + sx * span * 1.05, 18.8 * B.s + rise * 0.45, B.back_z - 5), (8 + sx * span * 0.95, 18.8 * B.s - 1, B.back_z - 5),
                       (8 + sx * span * 0.62, 18.8 * B.s - 5, B.back_z - 4)]
            prev = tip
            for f in fingers:
                g.tube([elbow if f is fingers[-1] else tip, f], [0.4, 0.15], lambda x, y, z, d, ff: hx(c1, 0.7), pri + 1)
                g.triangle(tip, prev, f, lambda p, w: c2 if rnd(*p) > 0.05 else (rim or c1), pri, scallop=0.14)
                prev = f
            g.triangle(elbow, fingers[-1], root, lambda p, w: c2, pri, scallop=0.1)
            g.triangle(tip, elbow, fingers[-1], lambda p, w: c2, pri)
        elif kind == "shard":
            for k in range(feathers):
                t = (k + 1) / (feathers + 1)
                base = lerp(root, tip, t)
                L = 4 + 6 * (1 - abs(t - 0.55))
                end = add(base, (sx * 1.0, -L, -0.8))
                g.slab(base, end, (sx * 1.2, 0, 0), 0.45, lambda x, y, z, u, v: rim if u > 0.85 else (c2 if abs(v) < 0.4 else c1), pri)
        else:
            for row, (len0, c) in enumerate(((9.0, c1), (6.5, c2), (4.0, rim or hx(c2, 1.15)))):
                for k in range(feathers + row):
                    t = (k + 0.5) / (feathers + row)
                    base = lerp(lerp(root, elbow, min(1, t * 2)), tip, max(0, t * 2 - 1))
                    base = add(base, (0, -row * 1.2, 0.2 * row))
                    L = len0 * (0.55 + 0.6 * t)
                    end = add(base, (sx * 1.6 * t, -L, -0.6))
                    g.slab(base, end, (sx * 1.3, 0, 0.1), 0.45, lambda x, y, z, u, v, c=c: hx(c, 1.12) if abs(v) < 0.25 else c, pri + row)


def crown(g, center, r, col, gem=None, spikes=6, height=2.2, pri=6, tilt=0.0):
    g.ring(center, r, 0.45, col, pri)
    for k in range(spikes):
        a = k * 2 * math.pi / spikes
        base = (center[0] + math.cos(a) * r, center[1], center[2] + math.sin(a) * r)
        h = height * (1.3 if k % 2 == 0 else 0.8)
        g.cone(base, add(base, (0, h, 0)), 0.45, 0.1, col, pri)
        if gem and k % 2 == 0:
            g.sphere(add(base, (0, 0.5, 0)), 0.45, gem, pri + 1)


def halo(g, center, r, col, glow="ffffff", pri=6):
    g.ring(center, r, 0.42, col, pri, axis="y", tilt=math.pi / 2)
    g.ring(center, r - 0.6, 0.25, glow, pri, axis="y", tilt=math.pi / 2)


# ------------------------------------------------------------------ 무기
def staff(g, hand, top_y, wood, head_col, orb=None, pri=5, lean=0.25):
    bottom = (hand[0], 0.4, hand[2] - lean * hand[1])
    top = (hand[0], top_y, hand[2] + lean * (top_y - hand[1]))
    g.tube([bottom, top], [0.38, 0.32], lambda x, y, z, d, f: tex(wood, x, y, z, 0.15), pri)
    if orb:
        g.sphere(add(top, (0, 1.0, 0.25)), 1.05, lambda x, y, z, d: "ffffff" if d < 0.2 else orb, pri + 1)
    for k in range(4):
        a = k * math.pi / 2
        g.cone(add(top, (math.cos(a) * 0.5, -0.3, math.sin(a) * 0.5)), add(top, (math.cos(a) * 1.3, 1.6, math.sin(a) * 1.3)), 0.3, 0.1, head_col, pri)
    return top


def sword(g, hand, length, blade, edge, guard, pri=5, width=1.3, tilt=(0, 1, 0.35)):
    d = norm(tilt)
    tip = add(hand, mul(d, length))
    base = add(hand, mul(d, 1.0))
    g.slab(base, tip, (width, 0, 0), 0.4, lambda x, y, z, u, v: edge if abs(v) > 0.7 or u > 0.92 else (hx(blade, 1.15) if abs(v) < 0.15 else blade), pri)
    g.slab(add(hand, mul(d, 0.6)), add(hand, mul(d, 1.0)), (width * 2.6, 0, 0), 0.6, guard, pri + 1)
    g.tube([add(hand, mul(d, -1.4)), add(hand, mul(d, 0.6))], [0.35, 0.35], lambda x, y, z, d_, f: hx(guard, 0.6), pri)
    g.sphere(add(hand, mul(d, -1.6)), 0.5, guard, pri + 1)
    return tip


def hammer(g, hand, handle_len, head_size, wood, metal, trim, pri=5):
    top = add(hand, (0, handle_len * 0.7, 1.2))
    bot = add(hand, (0, -handle_len * 0.3, -0.5))
    g.tube([bot, top], [0.42, 0.4], lambda x, y, z, d, f: tex(wood, x, y, z, 0.15), pri)
    h = head_size
    g.box((top[0] - h, top[1] - h * 0.6, top[2] - h * 0.7), (top[0] + h, top[1] + h * 0.6, top[2] + h * 0.7),
          lambda x, y, z: trim if abs(x - top[0]) > h - 0.6 else tex(metal, x, y, z), pri + 1)
    return top


def spear(g, hand, length, shaft, head, pri=5, prongs=1):
    bot = add(hand, (0, -length * 0.35, -1.0))
    top = add(hand, (0, length * 0.65, 1.8))
    g.tube([bot, top], [0.35, 0.3], lambda x, y, z, d, f: tex(shaft, x, y, z, 0.1), pri)
    for k in range(prongs):
        off = (k - (prongs - 1) / 2) * 1.2
        b = add(top, (off, 0, 0))
        g.cone(b, add(b, (off * 0.3, 3.2 if k == prongs // 2 else 2.4, 0.4)), 0.5, 0.1, head, pri + 1)
    if prongs > 1:
        g.box(add(top, (-(prongs - 1) * 0.6 - 0.4, -0.3, -0.3)), add(top, ((prongs - 1) * 0.6 + 0.4, 0.4, 0.3)), head, pri + 1)
    return top


# ------------------------------------------------------------------ v5.8.3 추가: 네발짐승 · 무기 · 촉수
def quad(g, fur, belly, hoof, L=20.0, H=10.0, W=11.0, leg=6.0, shift=4.0, pri=2, hump=1.0, leg_r=1.9, fur2=None):
    """네발 몸통: 앞이 높은 몸 (엉덩이 · 가슴 · 어깨 혹) · 굵은 다리 4개와 발굽. 반환: Body (front_z · back_y · head_base · tail · legs)"""
    B = Body()
    fur2 = fur2 or hx(fur, 0.8)
    z0 = 8 - L / 2 - shift
    zf = z0 + L
    cy = leg + H / 2
    g.ellipsoid((8, cy, z0 + L * 0.3), (W / 2, H / 2, L * 0.36), lambda x, y, z, d: tex(belly if y < cy - H * 0.3 else fur, x, y, z), pri)
    g.ellipsoid((8, cy + H * 0.1, z0 + L * 0.68), (W / 2 + 0.6, H / 2 + 0.6, L * 0.38), lambda x, y, z, d: tex(belly if y < cy - H * 0.3 else fur, x, y, z), pri)
    g.ellipsoid((8, cy + H * 0.45, zf - L * 0.25), (W / 2 * 0.9 * hump, H * 0.42 * hump, L * 0.24), lambda x, y, z, d: tex(fur2, x, y, z), pri)   # 어깨 혹
    B.legs = []
    for zz, front in ((z0 + L * 0.2, False), (zf - L * 0.18, True)):
        for sx in (-1, 1):
            top = (8 + sx * (W / 2 - 1.6), cy, zz)
            knee = (8 + sx * (W / 2 - 1.4), leg * 0.5, zz + (0.8 if front else -0.8))
            foot = (8 + sx * (W / 2 - 1.4), 0.9, zz + 0.3)
            g.tube([top, knee], [leg_r * 1.25, leg_r], lambda x, y, z, d, f: tex(fur2, x, y, z), pri)
            g.tube([knee, foot], [leg_r, leg_r * 0.9], lambda x, y, z, d, f: tex(fur2, x, y, z), pri)
            g.ellipsoid((foot[0], 0.7, foot[2] + 0.4), (leg_r * 1.05, 0.75, leg_r * 1.2), lambda x, y, z, d: hoof, pri + 1)
            B.legs.append(foot)
    B.front_z = zf
    B.back_y = leg + H
    B.z0 = z0
    B.cy = cy
    B.head_base = (8, cy + H * 0.25, zf)
    B.tail = (8, cy + H * 0.2, z0)
    return B


def bow(g, hand, height, wood, string, pri=6, bend=2.4):
    """세로로 쥔 긴 활 (휜 몸 + 시위)"""
    top = add(hand, (0, height / 2, 0))
    bot = add(hand, (0, -height / 2, 0))
    pts = [add(bot, (0, 0, -0.6)), add(hand, (0, -height * 0.25, bend)), add(hand, (0, 0, bend * 0.8)), add(hand, (0, height * 0.25, bend)), add(top, (0, 0, -0.6))]
    g.tube(pts, [0.2, 0.45, 0.55, 0.45, 0.2], lambda x, y, z, d, f: tex(wood, x, y, z, 0.12), pri, smooth=True)
    g.tube([add(bot, (0, 0.2, -0.6)), add(top, (0, -0.2, -0.6))], [0.16, 0.16], string, pri)
    for p in (top, bot):
        g.sphere(add(p, (0, 0, -0.6)), 0.5, string, pri + 1)


def axe(g, hand, handle_len, wood, blade, edge, pri=6, size=3.4, double=True):
    top = add(hand, (0, handle_len * 0.7, 0.8))
    bot = add(hand, (0, -handle_len * 0.3, -0.4))
    g.tube([bot, top], [0.4, 0.38], lambda x, y, z, d, f: tex(wood, x, y, z, 0.15), pri)
    for sx in ((-1, 1) if double else (1,)):
        c = add(top, (sx * 0.4, -size * 0.5, 0))
        g.triangle(c, add(c, (sx * size * 1.2, size * 0.9, 0)), add(c, (sx * size * 1.2, -size * 0.9, 0)),
                   lambda p, w: edge if w < 0.14 else tex(blade, *p, 0.08), pri + 1, thick=0.5)
    return top


def scythe(g, hand, handle_len, wood, blade, edge, pri=6, reach=8.0):
    top = add(hand, (0, handle_len * 0.7, 0.8))
    bot = add(hand, (0, -handle_len * 0.3, -0.4))
    g.tube([bot, top], [0.4, 0.36], lambda x, y, z, d, f: tex(wood, x, y, z, 0.15), pri)
    pts = [top, add(top, (0, 1.4, reach * 0.35)), add(top, (0, 0.6, reach * 0.75)), add(top, (0, -1.8, reach))]
    g.tube(pts, [1.7, 1.4, 1.0, 0.3], lambda x, y, z, d, f: edge if y < top[1] + 0.6 - f * 0.6 else blade, pri + 1, smooth=True, squash=(0.5, 1))
    g.sphere(top, 0.8, edge, pri + 2)
    return top


def tentacle(g, pts, r0, r1, col, col2, sucker, pri=3):
    """굵기가 줄어드는 촉수 (띠 무늬 · 아래쪽 빨판)"""
    P = catmull(pts, 6)
    n = len(P)
    for i in range(n - 1):
        f = i / (n - 1)
        r = r0 + (r1 - r0) * f
        c = col if int(f * 12) % 2 == 0 else col2
        g.tube([P[i], P[i + 1]], [r, r + (r1 - r0) / (n - 1)], c, pri)
        if i % 2 == 0 and r > 0.7:
            q = add(P[i], (0, -r * 0.85, 0))
            g.sphere(q, max(0.4, r * 0.35), sucker, pri + 1)


def floaters(g, pts, col, core="ffffff", r=0.8, pri=6):
    for p in pts:
        g.sphere(p, r, lambda x, y, z, d: core if d < 0.25 else col, pri)
