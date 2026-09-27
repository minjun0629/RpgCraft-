"""
RpgCraft 아이템 아트 라이브러리 (32x32 픽셀 아트).

무기는 '대각선 좌표계 (s, t)' 에서 부품(칼날/가드/손잡이/폼멜/장식)을 조립해 그린다.
  s = 폼멜(0) → 칼끝(~36) 방향 거리,  t = 칼날 축에서 수직 거리 (음수 = 빛을 받는 왼쪽 위)
그래서 같은 '검'이라도 칼날 윤곽 함수, 가드 모양, 장식 구성을 바꾸면 실루엣 자체가 달라진다.

stage: 0 = 기본, 1 = +7 각인(룬이 빛남), 2 = +10 완성(날 끝 발광 + 별빛)
"""
import math
from PIL import Image

N = 32
SQ = math.sqrt(0.5)
LAST_KEYS = {}  # 마지막으로 그린 캔버스의 부품 키 (3D 두께 계산용)
O = (3.0, 29.0)


# ----------------------------------------------------------------- 색
def hx(c, a=255):
    c = c.lstrip("#")
    return tuple(int(c[i:i + 2], 16) for i in (0, 2, 4)) + (a,)


def mul(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (c[3],)


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


WHITE = (255, 255, 255, 255)


def palette(blade, guard="c9a13b", grip="5a3a22", accent="e04848", glow="9ff6ff", outline=None, extra=None, rim=None):
    b = hx(blade)
    g = hx(guard)
    w = hx(grip)
    e = hx(accent)
    c = hx(glow)
    p = {
        "h": mix(mul(b, 1.45), WHITE, 0.3), "b": b, "s": mul(b, 0.66), "f": mul(b, 0.5),
        "g": g, "G": mul(g, 0.62),
        "w": w, "W": mul(w, 0.62),
        "e": e, "E": mix(e, WHITE, 0.55),
        "c": c, "C": mix(c, WHITE, 0.6),
        "o": hx(outline) if outline else mix(mul(b, 0.22), (20, 16, 28, 255), 0.5),
    }
    x = hx(extra) if extra else mul(b, 1.2)
    p["x"], p["X"] = x, mul(x, 0.65)
    r = hx(rim) if rim else g
    p["r"], p["R"] = r, mul(r, 0.6)
    return p


# ----------------------------------------------------------------- 캔버스
class Canvas:
    def __init__(self, pal, stage=0):
        self.k = {}
        self.pal = pal
        self.stage = stage
        self.blades = []
        self.light = {}   # 픽셀별 밝기 배율 (그라디언트/돔 음영)
        self.band = set() # 광택 띠

    def set(self, x, y, key):
        if 0 <= x < N and 0 <= y < N:
            if key is None:
                self.k.pop((x, y), None)
            else:
                self.k[(x, y)] = key

    def get(self, x, y):
        return self.k.get((x, y))

    @staticmethod
    def st(x, y):
        px, py = x + 0.5 - O[0], y + 0.5 - O[1]
        return (px - py) * SQ, (px + py) * SQ

    def fill_st(self, pred, key):
        hit = []
        for y in range(N):
            for x in range(N):
                s, t = self.st(x, y)
                if pred(s, t):
                    k = key(s, t) if callable(key) else key
                    if k:
                        self.set(x, y, k)
                        hit.append((x, y))
        return hit

    def fill_xy(self, pred, key):
        hit = []
        for y in range(N):
            for x in range(N):
                if pred(x + 0.5, y + 0.5):
                    k = key(x + 0.5, y + 0.5) if callable(key) else key
                    if k:
                        self.set(x, y, k)
                        hit.append((x, y))
        return hit

    # ---- 윤곽선 / 렌더
    def outline(self):
        add = {}
        for y in range(N):
            for x in range(N):
                if (x, y) in self.k:
                    continue
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    k = self.k.get((x + dx, y + dy))
                    if k and k not in ("o", "C", "*"):
                        add[(x, y)] = "o"
                        break
        self.k.update(add)

    def sparkle(self, x, y, size=2):
        """외곽선 없는 별빛 (빈 칸에만)"""
        pts = [(x, y)] + [(x + d, y) for d in range(-size, size + 1) if d] + [(x, y + d) for d in range(-size, size + 1) if d]
        for (px, py) in pts:
            if (px, py) not in self.k or self.k[(px, py)] == "o":
                self.set(px, py, "*" if (px, py) == (x, y) else "C")

    def render(self):
        """부품 키 → 색 (HD: 32 설계를 scale2x 로 64 로 부드럽게 확대한 뒤 채색).
        품질 보정: 얇은 셀렉티브 아웃라인, 좌상단 림라이트/우하단 음영, 재질 질감, 광택 띠"""
        global LAST_KEYS
        k, light, band, n = self.k, self.light, self.band, N
        if HIRES:
            k, light, band, n = _hd(self.k, self.light, self.band)
        LAST_KEYS = dict(k)
        pal = dict(self.pal)
        pal["*"] = WHITE
        body = {p for p, kk in k.items() if kk not in ("o", "*", "C")}
        seed = sum(pal["b"][:3]) * 7919
        img = Image.new("RGBA", (n, n), (0, 0, 0, 0))
        for (x, y), kk in k.items():
            c = pal.get(kk, (255, 0, 255, 255))
            if kk == "o":
                nb = [pal.get(k[q], c) for q in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)) if q in body]
                if nb:
                    avg = tuple(sum(v[i] for v in nb) // len(nb) for i in range(3)) + (255,)
                    light_side = (x + 1, y) in body or (x, y + 1) in body
                    c = mix(mul(avg, 0.42 if light_side else 0.26), pal["o"], 0.4)
            elif (x, y) in body:
                if kk in ("h", "b", "s", "f", "r", "R", "g", "G", "x", "X"):
                    nz = ((x * 73856093) ^ (y * 19349663) ^ seed) % 7 - 3
                    c = mul(c, 1 + nz * (0.016 if HIRES else 0.022))
                if kk in ("w", "W") and (x - y) % (5 if HIRES else 3) == 0:
                    c = mul(c, 1.12)
                if kk in ("b", "f", "x"):  # 칼날 베벨 그라데이션
                    nbk = [k.get(q) for q in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1))]
                    if "h" in nbk:
                        c = mix(c, pal["h"], 0.32)
                    elif "s" in nbk:
                        c = mix(c, pal["s"], 0.28)
                if kk not in ("c", "C", "e", "E"):
                    c = mul(c, light.get((x, y), 1.0))
                    if (x, y) in band and kk in ("h", "b", "f", "s"):
                        c = mix(c, WHITE, 0.38)
                if kk not in ("e", "E", "c"):
                    if (x, y - 1) not in body or (x - 1, y) not in body:
                        c = mix(c, WHITE, 0.16)
                    elif (x, y + 1) not in body or (x + 1, y) not in body:
                        c = mul(c, 0.84)
                if kk in ("e", "E"):  # 보석: 위쪽 반사광 · 아래 어둠 → 둥글게
                    up = k.get((x, y - 1)) in ("e", "E")
                    dn = k.get((x, y + 1)) in ("e", "E")
                    if not up:
                        c = mix(c, WHITE, 0.35)
                    elif not dn:
                        c = mul(c, 0.72)
            img.putpixel((x, y), c)
        return img


HIRES = True  # 64x64 고해상도 출력


def _hd(k, light, band):
    """scale2x(EPX) 로 대각선을 부드럽게 2배 확대 + 외곽선은 1픽셀로 얇게"""
    n = N * 2
    out = {}
    g = k.get
    for y in range(N):
        for x in range(N):
            P = g((x, y))
            A, B, C, D = g((x, y - 1)), g((x + 1, y)), g((x - 1, y)), g((x, y + 1))
            e0 = A if (C == A and C != D and A != B) else P
            e1 = B if (A == B and A != C and B != D) else P
            e2 = C if (D == C and D != B and C != A) else P
            e3 = D if (B == D and B != A and D != C) else P
            for dx, dy, v in ((0, 0, e0), (1, 0, e1), (0, 1, e2), (1, 1, e3)):
                if v is not None:
                    out[(2 * x + dx, 2 * y + dy)] = v
    body = {p for p, v in out.items() if v not in ("o", "*", "C")}
    for p, v in list(out.items()):  # 바깥쪽 외곽선 절반 제거
        if v == "o":
            x, y = p
            if not any((x + dx, y + dy) in body for dx in (-1, 0, 1) for dy in (-1, 0, 1)):
                del out[p]
    l2 = {(2 * x + dx, 2 * y + dy): v for (x, y), v in light.items() for dx in (0, 1) for dy in (0, 1)}
    b2 = {(2 * x + dx, 2 * y + dy) for (x, y) in band for dx in (0, 1) for dy in (0, 1)}
    return out, l2, b2, n


# ----------------------------------------------------------------- 도형 판정
def in_poly(pts, a, b):
    inside = False
    j = len(pts) - 1
    for i in range(len(pts)):
        (xi, yi), (xj, yj) = pts[i], pts[j]
        if ((yi > b) != (yj > b)) and (a < (xj - xi) * (b - yi) / (yj - yi + 1e-9) + xi):
            inside = not inside
        j = i
    return inside


def poly_st(cv, pts, key):
    return cv.fill_st(lambda s, t: in_poly(pts, s, t), key)


def poly_xy(cv, pts, key):
    return cv.fill_xy(lambda x, y: in_poly(pts, x, y), key)


def disc_st(cv, s0, t0, r, key):
    return cv.fill_st(lambda s, t: (s - s0) ** 2 + (t - t0) ** 2 <= r * r, key)


def disc_xy(cv, x0, y0, r, key):
    return cv.fill_xy(lambda x, y: (x - x0) ** 2 + (y - y0) ** 2 <= r * r, key)


def gem_st(cv, s0, t0, r=1.3, key="e"):
    hit = disc_st(cv, s0, t0, r, key)
    if hit:
        hx_, hy = min(hit, key=lambda p: p[0] + p[1])
        cv.set(hx_, hy, "E" if key == "e" else "C")
    return hit


def gem_xy(cv, x0, y0, r=1.8, key="e"):
    hit = disc_xy(cv, x0, y0, r, key)
    if hit:
        hx_, hy = min(hit, key=lambda p: p[0] + p[1])
        cv.set(hx_, hy, "E" if key == "e" else "C")
    return hit


def line_st(cv, s0, t0, s1, t1, w, key):
    def pred(s, t):
        vx, vy = s1 - s0, t1 - t0
        L2 = vx * vx + vy * vy
        u = max(0, min(1, ((s - s0) * vx + (t - t0) * vy) / (L2 + 1e-9)))
        return (s - (s0 + u * vx)) ** 2 + (t - (t0 + u * vy)) ** 2 <= w * w
    return cv.fill_st(pred, key)


# ----------------------------------------------------------------- 무기 부품
def blade(cv, s0, s1, wl, wr=None, c=None, fuller=None, tip=0.2, runes=(0.12, 0.75)):
    """wl/wr: u(0~1) -> 반폭. c: u -> 중심선 휘어짐. fuller: (u0,u1) 홈"""
    wr = wr or wl
    c = c or (lambda u: 0)

    def taper(u):
        return 1 if u < 1 - tip else max(0.0, (1 - u) / tip)

    def geo(s, t):
        u = (s - s0) / (s1 - s0)
        return u, t - c(u), wl(u) * taper(u), wr(u) * taper(u)

    def pred(s, t):
        if s < s0 or s > s1:
            return False
        u, d, L, R = geo(s, t)
        return -L - 0.35 <= d <= R + 0.35

    def key(s, t):
        u, d, L, R = geo(s, t)
        if cv.stage >= 1 and runes[0] <= u <= runes[1] and abs(d) < 0.55 and int(s) % (2 if cv.stage >= 2 else 3) == 0:
            return "c"
        if d < -L + 0.95:
            return "C" if cv.stage >= 2 and u > 0.08 else "h"
        if d > R - 0.95:
            return "s"
        if fuller and fuller[0] <= u <= fuller[1] and abs(d) < 0.6:
            return "f"
        return "b"

    hit = cv.fill_st(pred, key)
    cv.blades.append(hit)
    for (x, y) in hit:
        s_, t_ = cv.st(x, y)
        u = (s_ - s0) / (s1 - s0)
        cv.light[(x, y)] = 0.84 + 0.3 * u
        if 0.52 <= u <= 0.6 or 0.66 <= u <= 0.69:
            cv.band.add((x, y))
    return hit


def grip(cv, s0, s1, hw=1.1, wrap=True):
    def key(s, t):
        if wrap:
            return "w" if int(s * 1.4 - t) % 2 == 0 else "W"
        return "w" if t < 0.2 else "W"
    return cv.fill_st(lambda s, t: s0 <= s <= s1 and abs(t) <= hw, key)


def guard_bar(cv, sc, left, right, thick=1.5, bend=0.0, key=None):
    """bend > 0 : 양 끝이 칼날 쪽으로 휨 / < 0 : 손잡이 쪽으로 휨"""
    def pred(s, t):
        return -left <= t <= right and abs(s - (sc + bend * t * t)) <= thick / 2
    return cv.fill_st(pred, key or (lambda s, t: "g" if t < 0.5 else "G"))


def pommel(cv, s0, r=1.5, kind="round"):
    if kind == "round":
        disc_st(cv, s0, 0, r, lambda s, t: "g" if t < 0 else "G")
    elif kind == "gem":
        disc_st(cv, s0, 0, r + 0.4, "G")
        gem_st(cv, s0, 0, r - 0.3)
    elif kind == "spike":
        poly_st(cv, [(s0 + 1.5, -1.3), (s0 - 2.4, 0), (s0 + 1.5, 1.3)], "g")
    elif kind == "ring":
        cv.fill_st(lambda s, t: 0.8 ** 2 <= (s - s0) ** 2 + t * t <= (r + 0.3) ** 2, "g")
    elif kind == "skull":
        disc_st(cv, s0, 0, r + 0.3, "x")
        cv.fill_st(lambda s, t: abs(s - s0) < 0.6 and 0.3 < abs(t) < 1.1, "o")


def engrave(cv, pixels):
    """+7: 홈/문양이 룬처럼 빛남, +10: 전부 발광"""
    if cv.stage < 1:
        return
    for (x, y) in pixels:
        k = cv.get(x, y)
        if k in ("f", "x", "X") and (cv.stage >= 2 or (x + y) % 2 == 0):
            cv.set(x, y, "c")


def finish(cv, tip=None):
    engrave(cv, [p for b in cv.blades for p in b])
    cv.outline()
    if cv.stage >= 2:
        if tip is None and cv.blades:
            allpx = [p for b in cv.blades for p in b]
            if allpx:
                tip = max(allpx, key=lambda p: p[0] - p[1])
        if tip:
            cv.sparkle(min(N - 3, tip[0] + 1), max(2, tip[1] - 1), 2)
    return cv.render()


# ----------------------------------------------------------------- 검
def std_hilt(cv, guard=("bar", 3.6, 3.6, 1.5, 0.0), grip_hw=1.1, pommel_kind="round", wrap=True, gs=10.0):
    grip(cv, 2.6, gs - 1.0, grip_hw, wrap)
    kind = guard[0]
    if kind == "bar":
        guard_bar(cv, gs, guard[1], guard[2], guard[3], guard[4])
    pommel(cv, 1.6, 1.5, pommel_kind)


def const(v):
    return lambda u: v


def sword_design(name, stage=0):
    D = SWORDS[name]
    cv = Canvas(D["pal"], stage)
    D["draw"](cv)
    return finish(cv)


def _s_common(cv):  # 초보자용 대검: 짧고 넓은 단순 검
    blade(cv, 10.5, 31, const(1.7), tip=0.16)
    std_hilt(cv, ("bar", 3.2, 3.2, 1.4, 0.0), wrap=False)


def _s_steel(cv):  # 날카로운 강철장검: 긴 홈, 위로 휜 가드, 가드 보석
    blade(cv, 10.5, 35, const(1.3), fuller=(0.02, 0.78), tip=0.18)
    std_hilt(cv, ("bar", 3.8, 3.8, 1.3, 0.09))
    pommel(cv, 1.6, 1.4, "gem")
    gem_st(cv, 10.0, 0, 0.95)


def _s_war(cv):  # 길들어진 전쟁장검: 이 빠진 넓은 칼날 + 가시 가드
    nick = lambda u: 1.9 - (1.0 if int(u * 22) % 7 == 3 else 0)
    blade(cv, 10.5, 33, nick, const(1.9), tip=0.15, fuller=(0.05, 0.5))
    std_hilt(cv, ("bar", 4.0, 4.0, 1.6, -0.05))
    cv.fill_st(lambda s, t: 13 <= s <= 25 and abs(t) < 0.5 and int(s) % 2 == 0, "g")   # 금 상감
    gem_st(cv, 10.0, 0, 1.0)
    poly_st(cv, [(9.3, -4.0), (7.0, -5.6), (10.6, -4.0)], "G")
    poly_st(cv, [(9.3, 4.0), (7.0, 5.6), (10.6, 4.0)], "G")


def _s_dread(cv):  # 전장의 공포장검: 피홈 대검, 해골 폼멜, 붉은 보석
    blade(cv, 11.0, 35.5, lambda u: 2.5 - 0.7 * u, fuller=None, tip=0.14)
    cv.fill_st(lambda s, t: 13 <= s <= 30 and abs(t) < 0.55, lambda s, t: "x")
    grip(cv, 3.2, 9.6, 1.2)
    poly_st(cv, [(11.4, -1.5), (9.6, -5.2), (7.2, -6.2), (10.2, -1.2)], "g")
    poly_st(cv, [(11.4, 1.5), (9.6, 5.2), (7.2, 6.2), (10.2, 1.2)], "G")
    disc_st(cv, 10.6, 0, 1.9, "g")
    gem_st(cv, 10.6, 0, 1.2)
    pommel(cv, 1.8, 1.4, "skull")


def _s_witch(cv):  # 마녀: 뱀처럼 굽이치는 칼날 + 독 균열
    wave = lambda u: 0.8 * math.sin(u * 3 * math.pi)
    blade(cv, 10.5, 34, const(1.3), c=wave, tip=0.2)
    cv.fill_st(lambda s, t: 14 <= s <= 30 and abs(t - wave((s - 10.5) / 23.5)) < 0.5 and int(s) % 4 != 0, "x")
    grip(cv, 2.6, 9.0, 1.0)
    guard_bar(cv, 10.0, 3.2, 3.2, 1.3, -0.18)
    gem_st(cv, 10.0, 0, 1.1)
    pommel(cv, 1.5, 1.4, "ring")


def _s_dwarf(cv):  # 드워프: 두껍고 짧은 룬 대검, 상자형 가드
    blade(cv, 10.5, 31, lambda u: 2.7 - 0.5 * u, tip=0.12)
    cv.fill_st(lambda s, t: 13 <= s <= 27 and abs(t) < 0.6 and int(s) % 2 == 0, "x")
    grip(cv, 2.8, 8.4, 1.4)
    poly_st(cv, [(8.4, -3.6), (11.0, -3.6), (11.0, 3.6), (8.4, 3.6)], lambda s, t: "g" if t < 0 else "G")
    gem_st(cv, 9.7, 0, 1.2)
    poly_st(cv, [(0.4, -1.8), (2.8, -1.8), (2.8, 1.8), (0.4, 1.8)], "g")


def _s_harpy(cv):  # 하피: 휘어진 세이버 + 깃털 날개 가드
    curve = lambda u: 1.7 * u * u
    blade(cv, 10.5, 34.5, lambda u: 1.0 + 0.4 * math.sin(u * math.pi), c=curve, tip=0.22)
    grip(cv, 2.6, 9.0, 0.95)
    for i, (a, b) in enumerate([(-3.4, -0.8), (0.8, 3.4)]):
        side = -1 if i == 0 else 1
        pts = [(10.2, side * 0.8), (8.2, side * 2.2), (9.4, side * 2.6), (7.4, side * 3.6), (8.8, side * 4.0), (6.6, side * 5.0), (10.8, side * 2.0)]
        poly_st(cv, pts, "g" if side < 0 else "G")
    pommel(cv, 1.5, 1.2, "round")


def _s_sea(cv):  # 심해수문장: 산호 가시가 돋은 칼날 + 진주
    blade(cv, 10.5, 34, const(1.5), tip=0.18, fuller=(0.1, 0.7))
    for u in (0.3, 0.5, 0.7):
        s = 10.5 + 23.5 * u
        poly_st(cv, [(s, 1.4), (s - 2.4, 3.3), (s + 0.8, 1.4)], "x")
    grip(cv, 2.6, 9.0, 1.05)
    guard_bar(cv, 10.0, 3.8, 3.8, 1.5, 0.16)
    gem_st(cv, 10.0, 0, 1.3, "e")
    pommel(cv, 1.6, 1.5, "gem")


def _s_flame(cv):  # 타오르는 붕붕이: 불꽃 물결 칼날
    wave = lambda u: 0.9 * math.sin(u * 4 * math.pi) * (0.3 + u)
    blade(cv, 10.5, 35, lambda u: 1.8 - 0.6 * u, c=wave, tip=0.25)
    cv.fill_st(lambda s, t: 12 <= s <= 28 and abs(t - wave((s - 10.5) / 24.5)) < 0.55, "x")
    grip(cv, 2.6, 9.0, 1.05)
    guard_bar(cv, 10.0, 3.4, 3.4, 1.6, -0.12)
    gem_st(cv, 10.0, 0, 1.2)
    pommel(cv, 1.6, 1.4, "spike")


def _s_qinglong(cv):  # 청룡: 용비늘 검신 + 용뿔 가드 + 여의주
    blade(cv, 10.5, 35.5, lambda u: 1.8 - 0.3 * u, tip=0.2)
    cv.fill_st(lambda s, t: 12 <= s <= 31 and abs(t) < 1.2 and (int(s) + int((t + 2) * 1.5)) % 3 == 0, "x")
    grip(cv, 2.6, 9.0, 1.05)
    guard_bar(cv, 10.0, 3.0, 3.0, 1.4, 0.0)
    poly_st(cv, [(10.0, -2.6), (12.8, -4.8), (10.8, -2.2)], "g")
    poly_st(cv, [(10.0, 2.6), (12.8, 4.8), (10.8, 2.2)], "G")
    gem_st(cv, 10.0, 0, 1.3, "c")
    disc_st(cv, 1.5, 0, 1.5, "c")


def _s_relic(cv):  # 신화: 여명을 가른 서약 — 부유하는 결정 칼날
    for a, b in ((12.6, 19.0), (20.6, 27.0), (28.6, 35.6)):
        tip = 0.35 if b > 35 else 0.0
        blade(cv, a, b, lambda u: 1.9 - 0.3 * u, tip=tip if tip else 0.01, runes=(0, 1))
    grip(cv, 2.6, 8.4, 1.0)
    cv.fill_st(lambda s, t: 2.0 ** 2 <= (s - 10.4) ** 2 + t * t <= 3.1 ** 2, lambda s, t: "g" if t < 0 else "G")
    gem_st(cv, 10.4, 0, 1.4, "c")
    pommel(cv, 1.5, 1.3, "gem")


def _bs_sword(t):
    def draw(cv):
        if t == 0:
            blade(cv, 10.5, 32, const(1.5), fuller=(0.05, 0.6))
            std_hilt(cv, ("bar", 3.2, 3.2, 1.4, 0.0), wrap=False)
        elif t == 1:
            blade(cv, 10.5, 35, const(1.3), fuller=(0.02, 0.8))
            std_hilt(cv, ("bar", 3.6, 3.6, 1.3, 0.12))
            gem_st(cv, 10.0, 0, 0.9)
        elif t == 2:
            blade(cv, 10.5, 34.5, lambda u: 1.7 - 0.2 * u, fuller=(0.05, 0.7))
            cv.fill_st(lambda s, t_: 14 <= s <= 26 and abs(t_) < 0.55 and int(s) % 3 == 0, "x")
            std_hilt(cv, ("bar", 4.2, 4.2, 1.4, -0.08))
            gem_st(cv, 10.0, 0, 1.2)
            pommel(cv, 1.6, 1.4, "gem")
        elif t == 3:
            blade(cv, 10.5, 35, lambda u: 2.0 - 0.3 * u, tip=0.16)
            cv.fill_st(lambda s, t_: 12 <= s <= 31 and abs(t_) < 0.6, "x")
            grip(cv, 2.6, 9.0, 1.1)
            guard_bar(cv, 10.0, 3.4, 3.4, 1.6, 0)
            for sg in (-1, 1):
                poly_st(cv, [(10.6, sg * 3.2), (13.2, sg * 4.6), (11.4, sg * 2.6)], "G")
            gem_st(cv, 10.0, 0, 1.2, "x")
            pommel(cv, 1.6, 1.3, "spike")
        else:
            blade(cv, 10.5, 33, lambda u: 2.1, tip=0.05)
            blade(cv, 31.5, 36, const(0.9), c=lambda u: -1.3, tip=0.6)
            blade(cv, 31.5, 36, const(0.9), c=lambda u: 1.3, tip=0.6)
            cv.fill_st(lambda s, t_: 12 <= s <= 31 and abs(t_) < 0.55, "c")
            grip(cv, 2.6, 9.0, 1.05)
            for sg in (-1, 1):
                poly_st(cv, [(10.6, sg * 1.0), (8.4, sg * 3.2), (9.6, sg * 3.4), (7.6, sg * 5.0), (11.2, sg * 2.6)], "g" if sg < 0 else "G")
            gem_st(cv, 10.0, 0, 1.3, "c")
            pommel(cv, 1.6, 1.4, "gem")
    return draw


# ----------------------------------------------------------------- 단검 (짧은 칼날, 손잡이가 화면 가운데로)
def dagger_hilt(cv, gs=15.0, guard=(2.8, 2.8, 1.3, 0.0), pk="round", wrap=True):
    grip(cv, 8.4, gs - 1.0, 1.05, wrap)
    guard_bar(cv, gs, guard[0], guard[1], guard[2], guard[3])
    pommel(cv, 7.4, 1.4, pk)


def _d_basic(cv):
    blade(cv, 15.6, 30, const(1.3), tip=0.3)
    dagger_hilt(cv, wrap=False)


def _d_stiletto(cv):
    blade(cv, 15.6, 33, const(0.85), tip=0.35, fuller=(0.0, 0.6))
    dagger_hilt(cv, guard=(2.4, 2.4, 1.1, 0.2))


def _d_silver(cv):
    blade(cv, 15.6, 31, lambda u: 1.4 - 0.2 * u, c=lambda u: 1.3 * u * u, tip=0.3)
    dagger_hilt(cv, guard=(2.6, 2.6, 1.2, -0.1), pk="gem")


def _d_red(cv):
    blade(cv, 15.6, 31.5, const(1.3), lambda u: 1.3 + (0.8 if int(u * 14) % 2 == 0 and u < 0.7 else 0), tip=0.28)
    dagger_hilt(cv, guard=(3.2, 3.2, 1.4, 0.1), pk="spike")
    gem_st(cv, 15.0, 0, 1.0)


def _d_witch(cv):  # 낫처럼 휜 독 단검
    blade(cv, 15.6, 32, const(1.0), c=lambda u: 3.8 * u * u, tip=0.3)
    cv.fill_st(lambda s, t: 17 <= s <= 27 and abs(t - 3.8 * ((s - 15.6) / 16.4) ** 2) < 0.5, "x")
    dagger_hilt(cv, guard=(2.2, 2.2, 1.2, -0.2), pk="ring")
    gem_st(cv, 15.0, 0, 0.9)


def _d_dwarf(cv):  # 넓은 삭스 단검
    blade(cv, 15.6, 30.5, const(2.2), lambda u: 2.2 - 1.4 * u, tip=0.25)
    cv.fill_st(lambda s, t: 17 <= s <= 26 and abs(t + 0.5) < 0.5 and int(s) % 2 == 0, "x")
    dagger_hilt(cv, guard=(3.0, 3.0, 1.8, 0.0))


def _d_harpy(cv):  # 맹금류 발톱
    blade(cv, 15.6, 32, lambda u: 1.3 - 0.4 * u, c=lambda u: -3.4 * u * u, tip=0.35)
    dagger_hilt(cv, guard=(2.6, 2.6, 1.2, 0.25))
    poly_st(cv, [(14.4, -2.4), (12.0, -4.4), (14.8, -2.0)], "g")


def _d_sea(cv):  # 거대한 송곳니
    blade(cv, 15.6, 32, lambda u: 2.4 * (1 - u) + 0.3, tip=0.15)
    dagger_hilt(cv, guard=(3.2, 3.2, 1.5, 0.15), pk="gem")


def _d_flame(cv):  # 불꽃 크리스
    wave = lambda u: 0.9 * math.sin(u * 3.5 * math.pi)
    blade(cv, 15.6, 32, lambda u: 1.4 - 0.3 * u, c=wave, tip=0.3)
    dagger_hilt(cv, guard=(3.0, 3.0, 1.4, -0.15), pk="spike")
    gem_st(cv, 15.0, 0, 0.9)


def _d_zhuque(cv):  # 주작의 깃털 칼날
    blade(cv, 15.6, 33, lambda u: 1.6 - 0.3 * u, tip=0.3)
    for u in (0.2, 0.4, 0.6):
        s = 15.6 + 17.4 * u
        poly_st(cv, [(s, -1.4), (s - 2.0, -3.0), (s + 1.0, -1.3)], "x")
        poly_st(cv, [(s, 1.4), (s - 2.0, 3.0), (s + 1.0, 1.3)], "x")
    cv.fill_st(lambda s, t: 29 <= s <= 33.5 and abs(t) < 0.8, "c")
    dagger_hilt(cv, guard=(3.4, 3.4, 1.4, 0.2), pk="gem")


def _d_relic(cv):  # 신화: 심연의 쌍송곳니 (평행한 두 칼날)
    blade(cv, 15.6, 32.5, const(0.8), c=lambda u: -1.7, tip=0.35, runes=(0, 0.9))
    blade(cv, 16.5, 29.5, const(0.8), c=lambda u: 1.7, tip=0.4, runes=(0, 0.9))
    cv.fill_st(lambda s, t: 15.6 <= s <= 27 and abs(t) < 0.5, "c")
    dagger_hilt(cv, guard=(3.4, 3.4, 1.3, 0.0), pk="gem")


def _bs_dagger(t):
    def draw(cv):
        [_d_basic, _d_stiletto, _d_silver, _d_red, _d_relic_like][t](cv)
    return draw


def _d_relic_like(cv):
    blade(cv, 15.6, 32.5, lambda u: 1.3, tip=0.3)
    cv.fill_st(lambda s, t: 16.5 <= s <= 29 and abs(t) < 0.5, "c")
    dagger_hilt(cv, guard=(3.2, 3.2, 1.3, 0.2), pk="gem")
    gem_st(cv, 15.0, 0, 1.0, "c")


# ----------------------------------------------------------------- 도끼
def shaft(cv, s0=1.0, s1=29.0, hw=0.95, wrap_to=7.0):
    cv.fill_st(lambda s, t: s0 <= s <= s1 and abs(t) <= hw,
               lambda s, t: ("w" if int(s * 1.4 - t) % 2 == 0 else "W") if s < wrap_to else ("w" if t < 0 else "W"))


def head(cv, pts, mirror=False):
    area = set(poly_st(cv, pts, "b"))
    if mirror:
        area |= set(poly_st(cv, [(s, -t) for s, t in pts], "b"))
    for (x, y) in area:
        s, t = cv.st(x, y)
        edge = any((x + dx, y + dy) not in area for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if edge and abs(t) > 2.2:
            cv.set(x, y, "C" if cv.stage >= 2 else "h")
        elif abs(t) < 1.6:
            cv.set(x, y, "s")
        elif cv.stage >= 1 and (x + y) % 4 == 0 and not edge:
            cv.set(x, y, "c")
        cv.light[(x, y)] = 0.82 + 0.05 * min(6.0, abs(t))
    return area


def axe_finish(cv, area):
    engrave(cv, list(area))
    cv.outline()
    if cv.stage >= 2 and area:
        tip = max(area, key=lambda p: p[0] + p[1] * 0.2)
        cv.sparkle(min(N - 3, tip[0] + 1), max(2, tip[1] - 1), 2)
    return cv.render()


def axe_design(name, stage=0):
    D = AXES[name]
    cv = Canvas(D["pal"], stage)
    area = D["draw"](cv)
    return axe_finish(cv, area)


def _a_rusty(cv):
    shaft(cv, 1, 27)
    a = head(cv, [(22, 0.6), (22, 4.6), (24, 6.8), (27.6, 6.4), (28.6, 4.0), (27, 0.6)])
    for p in [(24, 5), (26, 7)]:
        pass
    return a


def _a_bearded(cv):
    shaft(cv, 1, 28)
    a = head(cv, [(22, 0.6), (16.5, 3.4), (17, 5.2), (22.6, 7.6), (28.2, 7.4), (28.2, 0.6)])
    cv.fill_st(lambda s, t: 20 <= s <= 27 and 2.6 <= t <= 3.2, "x")
    return a


def _a_double(cv):
    shaft(cv, 1, 29)
    a = head(cv, [(21, 0.6), (19.4, 4.4), (21.4, 7.2), (26.6, 7.2), (28.4, 4.2), (27.4, 0.6)], mirror=True)
    gem_st(cv, 24.0, 0, 1.2)
    return a


def _a_gold(cv):
    shaft(cv, 1, 29.5)
    a = head(cv, [(19, 0.6), (15.6, 5.4), (19.4, 9.6), (26.4, 10.4), (31.4, 6.8), (30.6, 0.6)])
    a |= set(poly_st(cv, [(29, -0.8), (35, 0), (29, 0.8)], "h"))
    gem_st(cv, 24.5, 4.2, 1.2)
    return a


def _a_witch(cv):  # 가시 초승달
    shaft(cv, 1, 28.5)
    pts = [(19, 0.6), (17.6, 3.2), (16.2, 4.2), (18.6, 5.4), (18.4, 7.4), (21.4, 7.6), (22.6, 9.6), (24.6, 8.2), (27.2, 9.4), (27.6, 7.0), (30.2, 6.2), (28.6, 3.6), (29.8, 0.6)]
    a = head(cv, pts)
    cv.fill_st(lambda s, t: 20 <= s <= 27 and 3.0 <= t <= 3.8, "x")
    return a


def _a_dwarf(cv):  # 룬 판이 박힌 무거운 양날도끼
    shaft(cv, 1, 29.5, hw=1.1)
    a = head(cv, [(20.4, 0.6), (19, 5.0), (21, 8.0), (27.4, 8.0), (29.4, 5.0), (28.2, 0.6)], mirror=True)
    for sg in (-1, 1):
        cv.fill_st(lambda s, t, sg=sg: 22 <= s <= 26.4 and 3.6 <= t * sg <= 5.4 and (int(s) + int(t)) % 2 == 0, "x")
    gem_st(cv, 24.2, 0, 1.1)
    return a


def _a_harpy(cv):  # 깃털 날개 모양 도끼날
    shaft(cv, 1, 28)
    pts = [(19.6, 0.6), (17.2, 2.8), (18.6, 3.4), (16.8, 5.2), (18.8, 5.8), (17.6, 7.6), (21.2, 8.0), (22.4, 9.6), (26.8, 9.0), (29.4, 6.0), (28.4, 0.6)]
    return head(cv, pts)


def _a_anchor(cv):  # 닻 모양 도끼
    shaft(cv, 1, 30)
    a = set()
    for sg in (-1, 1):
        a |= set(poly_st(cv, [(19.6, sg * 0.6), (18.2, sg * 4.4), (20.6, sg * 7.6), (24.2, sg * 8.6), (22.4, sg * 6.6), (21.4, sg * 4.0), (22.4, sg * 0.6)], "b"))
    cv.fill_st(lambda s, t: 1.3 ** 2 <= (s - 31.0) ** 2 + t * t <= 2.5 ** 2, "g")
    a2 = head(cv, [(0, 0), (0, 0.01), (0.01, 0)])
    for (x, y) in a:
        s, t = cv.st(x, y)
        edge = any((x + dx, y + dy) not in a for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        cv.set(x, y, ("C" if cv.stage >= 2 else "h") if edge and abs(t) > 2.5 else "b")
    return a | a2


def _a_flame(cv):  # 불꽃 혀가 솟는 도끼
    shaft(cv, 1, 28.5)
    pts = [(19.4, 0.6), (17.4, 4.4), (15.2, 5.2), (18.4, 6.4), (18.6, 8.4), (22.2, 8.2), (22.8, 10.6), (25.0, 8.8), (28.0, 10.0), (28.2, 7.4), (31.4, 5.6), (29.6, 3.4), (30.2, 0.6)]
    a = head(cv, pts)
    cv.fill_st(lambda s, t: 21 <= s <= 27 and 2.6 <= t <= 4.6, "x")
    return a


def _a_baihu(cv):  # 백호: 호랑이 줄무늬 대형 초승달
    shaft(cv, 1, 30)
    a = head(cv, [(18, 0.6), (14.6, 5.6), (18.4, 10.6), (26.0, 11.2), (32.0, 7.2), (31.0, 0.6)])
    for (x, y) in list(a):
        s, t = cv.st(x, y)
        if 3.0 < t < 9.6 and (int(s * 0.9) + int(t * 0.8)) % 4 == 0 and cv.get(x, y) == "b":
            cv.set(x, y, "x")
    gem_st(cv, 24.4, 3.8, 1.3, "c")
    return a


def _a_relic(cv):  # 신화: 일식 — 가운데가 뚫린 고리형 도끼날
    shaft(cv, 1, 24)
    ring = set(cv.fill_st(lambda s, t: 3.4 ** 2 <= (s - 28.4) ** 2 + (t - 1.8) ** 2 <= 6.6 ** 2, "b"))
    for (x, y) in ring:
        s, t = cv.st(x, y)
        r = math.hypot(s - 28.4, t - 1.8)
        if r > 5.7:
            cv.set(x, y, "c" if (x + y) % 3 else "C")
        elif r < 4.2:
            cv.set(x, y, "s")
    cv.fill_st(lambda s, t: 21.8 <= s <= 25 and abs(t) <= 1.0, "g")
    return ring


def _bs_axe(t):
    return [_a_rusty, _a_bearded, _a_double, _a_gold, _a_baihu_like][t]


def _a_baihu_like(cv):
    shaft(cv, 1, 30)
    a = head(cv, [(18, 0.6), (15.4, 5.0), (18.4, 9.8), (26.0, 10.6), (31.6, 6.8), (30.8, 0.6)], mirror=False)
    cv.fill_st(lambda s, t: 20 <= s <= 28 and 3.2 <= t <= 4.2, "c")
    return a


# ----------------------------------------------------------------- 창 (현무)
def spear_xuanwu(stage=0):
    cv = Canvas(palette("2fbf71", guard="3a6b4a", grip="4a3222", accent="d9352e", glow="a0ffcf", extra="7ad9a0"), stage)
    shaft(cv, 0.5, 24, 0.9, wrap_to=24)
    cv.fill_st(lambda s, t: (s - 23.4) ** 2 / 4.5 + t * t / 9 <= 1, lambda s, t: "x" if (int(s * 1.2) + int(t * 1.2)) % 3 == 0 else "g")
    blade(cv, 24.8, 36, lambda u: 2.2 * math.sin(max(0.0, u) * math.pi * 0.85 + 0.35), tip=0.3, runes=(0.1, 0.8))
    cv.fill_st(lambda s, t: 26 <= s <= 33 and abs(t) < 0.5, "c")
    cv.fill_st(lambda s, t: 21 <= s <= 22.2 and 1.2 < t < 3.4, "e")
    return finish(cv)


# ----------------------------------------------------------------- 방패 (정면, xy 좌표)
def shield_mask(kind):
    cx = 15.5
    if kind == "round":
        return lambda x, y: (x - cx) ** 2 + (y - 16) ** 2 <= 13.2 ** 2
    if kind == "heater":
        return lambda x, y: 3 <= x <= 29 and 3 <= y <= 29 and (y <= 17 or abs(x - 16) <= 13 * (1 - (y - 17) / 12.5) ** 0.8)
    if kind == "kite":
        return lambda x, y: 2 <= y <= 30.5 and abs(x - 16) <= (11 * math.sqrt(max(0, 1 - ((y - 8) / 6.5) ** 2)) if y < 8 else 11 * (1 - (y - 8) / 23))
    if kind == "tower":
        return lambda x, y: 5 <= x <= 27 and 2 <= y <= 30 and not (y < 5 and (x < 7 or x > 25))
    if kind == "pent":
        return lambda x, y: in_poly([(16, 1.5), (29.5, 11), (25, 29.5), (7, 29.5), (2.5, 11)], x, y)
    if kind == "shell":  # 가리비: 아래 경첩에서 부채꼴로 펼쳐지고 윗변이 물결
        def m(x, y):
            dx, dy = x - 16, 29.5 - y
            if dy < 0:
                return False
            ang = math.atan2(dx, dy)
            if abs(ang) > 1.15:
                return False
            return math.hypot(dx, dy) <= 24 + 1.6 * math.cos(ang * 11)
        return m
    if kind == "sun":
        def m(x, y):
            r = math.hypot(x - 16, y - 16)
            ang = math.atan2(y - 16, x - 16)
            return r <= 10.5 + 3.4 * max(0, math.cos(ang * 8)) ** 3
        return m
    if kind == "winged":
        base = shield_mask("heater")
        return lambda x, y: (base(x, y) and 7 <= x <= 25) or in_poly([(7, 6), (1, 3), (2.5, 9), (0.8, 12), (4, 14), (7, 16)], x, y) \
            or in_poly([(25, 6), (31, 3), (29.5, 9), (31.2, 12), (28, 14), (25, 16)], x, y)
    raise ValueError(kind)


def shield_design(kind, pal, emblem, stage=0, studs=True, wood=False, rim_w=1):
    cv = Canvas(pal, stage)
    m = shield_mask(kind)
    area = set(cv.fill_xy(m, "b"))
    for (x, y) in area:
        edge = any((x + dx, y + dy) not in area for dx in range(-rim_w, rim_w + 1) for dy in range(-rim_w, rim_w + 1))
        if edge:
            cv.set(x, y, ("C" if (x + y) % 2 else "r") if stage >= 2 else ("r" if x + y < 32 else "R"))
        elif wood and x % 5 == 0:
            cv.set(x, y, "s")
        elif x + y > 36:
            cv.set(x, y, "s")
    cxs = sum(p[0] for p in area) / len(area)
    cys = sum(p[1] for p in area) / len(area)
    R = max(((x - cxs) ** 2 + (y - cys) ** 2) ** 0.5 for (x, y) in area) or 1
    for (x, y) in area:
        dx, dy = (x - cxs + 3) / R, (y - cys + 3) / R
        cv.light[(x, y)] = 1.16 - 0.34 * min(1.0, (dx * dx + dy * dy) ** 0.5)
    emblem(cv, stage)
    if studs:
        for (x, y) in [(7, 7), (24, 7), (7, 20), (24, 20)]:
            if (x, y) in area and cv.get(x, y) not in ("r", "R"):
                cv.set(x, y, "g")
    cv.outline()
    if stage >= 2:
        cv.sparkle(27, 3, 2)
    return cv.render()


def em_key(cv):
    return "c" if cv.stage >= 1 else "e"


def em_boss(cv, st):  # 방패 중앙 돌기
    gem_xy(cv, 15.5, 15.5, 2.8, "g")
    disc_xy(cv, 15.5, 15.5, 1.3, "G")


def em_cross(cv, st):
    k = em_key(cv)
    cv.fill_xy(lambda x, y: (14.5 <= x <= 17.5 and 7 <= y <= 25) or (9 <= x <= 22 and 11.5 <= y <= 14.5), k)


def em_lion(cv, st):  # 별 문장 + 왕관
    k = em_key(cv)
    pts = []
    for i in range(10):
        a = -math.pi / 2 + i * math.pi / 5
        r = 7 if i % 2 == 0 else 3
        pts.append((16 + r * math.cos(a), 16 + r * math.sin(a)))
    poly_xy(cv, pts, k)
    poly_xy(cv, [(12, 7), (13.5, 4), (16, 6), (18.5, 4), (20, 7)], "g")


def em_eye(cv, st):
    k = em_key(cv)
    cv.fill_xy(lambda x, y: ((x - 16) / 7.5) ** 2 + ((y - 15) / 4) ** 2 <= 1, "x")
    disc_xy(cv, 16, 15, 3, k)
    disc_xy(cv, 16, 15, 1.2, "o")


def em_rune(cv, st):
    k = em_key(cv)
    cv.fill_xy(lambda x, y: 4.5 ** 2 <= (x - 16) ** 2 + (y - 16) ** 2 <= 6 ** 2, "g")
    cv.fill_xy(lambda x, y: (abs(x - 16) < 1 and 11 <= y <= 21) or (abs(y - x + 1) < 0.9 and 12 <= y <= 19) or (abs(y + x - 31) < 0.9 and 12 <= y <= 19), k)


def em_wing(cv, st):
    k = em_key(cv)
    poly_xy(cv, [(16, 9), (19, 14), (16, 24), (13, 14)], k)


def em_shell(cv, st):
    inside = lambda x, y: cv.get(int(x), int(y)) in ("b", "s")
    for i in range(-4, 5):
        a = i * 0.24
        cv.fill_xy(lambda x, y, a=a: inside(x, y) and abs(math.atan2(x - 16, 29.5 - y) - a) < 0.045 * 24 / max(1, math.hypot(x - 16, 29.5 - y)), "s")
    gem_xy(cv, 16, 24, 2.2, "c" if cv.stage >= 1 else "x")


def em_sun(cv, st):
    k = em_key(cv)
    disc_xy(cv, 16, 16, 5.2, "x")
    disc_xy(cv, 16, 16, 3.2, k)


def em_core(cv, st):  # 신화 방패: 결정 코어 + 궤도
    cv.fill_xy(lambda x, y: 8.5 ** 2 <= (x - 16) ** 2 + (y - 16) ** 2 <= 9.8 ** 2 and (int(math.degrees(math.atan2(y - 16, x - 16)) + 360) // 30) % 2 == 0, "c")
    poly_xy(cv, [(16, 8), (21, 16), (16, 24), (11, 16)], "x")
    poly_xy(cv, [(16, 11), (18.5, 16), (16, 21), (13.5, 16)], "c")
    cv.set(15, 12, "C")


def relic_shield(stage=0):
    """신화 방패: 네 조각으로 분리되어 떠 있는 방패 + 중앙 코어"""
    pal = palette("eef3ff", guard="ffd46b", accent="7fd8ff", glow="9ff6ff", extra="7f9cff", rim="ffd46b")
    cv = Canvas(pal, stage)
    base = shield_mask("heater")
    area = set()
    for (x, y) in [(x, y) for y in range(N) for x in range(N) if base(x + 0.5, y + 0.5)]:
        gap = abs(x + 0.5 - 16) < 1.1 or abs(y + 0.5 - 15) < 1.1
        if not gap:
            area.add((x, y))
            cv.set(x, y, "b")
    for (x, y) in area:
        edge = any((x + dx, y + dy) not in area for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if edge:
            cv.set(x, y, "r" if x + y < 32 else "R")
        elif x + y > 36:
            cv.set(x, y, "s")
    em_core(cv, stage)
    cv.outline()
    if stage >= 2:
        cv.sparkle(27, 3, 2)
    return cv.render()


# ----------------------------------------------------------------- 도구 / 망치
def hammer(tier, stage=0):
    pals = [palette("b08a5a", grip="6b4a2b", extra="8a6a42"), palette("8f8f8f", grip="5a3a22", extra="6b4a2b"),
            palette("c9ced6", grip="3a2a1c", accent="3f7fff", extra="4a3a2a"), palette("ffd23f", grip="3a1c10", accent="e04848", extra="7a3a1c")]
    cv = Canvas(pals[tier], stage)
    shaft(cv, 1, 24, 0.95, wrap_to=8)
    if tier == 0:
        a = head(cv, [(21, -4.4), (27, -4.4), (27, 4.4), (21, 4.4)])
    elif tier == 1:
        a = head(cv, [(20.6, -5.2), (27.8, -5.6), (28.2, 5.6), (20.4, 5.2)])
    elif tier == 2:
        a = head(cv, [(21, -4.6), (27, -4.6), (27, 3.0), (29, 6.6), (24, 5.0), (21, 4.6)])
        gem_st(cv, 24, 0, 1.3)
    else:
        a = head(cv, [(20, -6.0), (28, -6.4), (29.4, -3.0), (29.4, 3.0), (28, 6.4), (20, 6.0), (18.6, 0)])
        poly_st(cv, [(28.6, -1), (34, 0), (28.6, 1)], "h")
        gem_st(cv, 24.2, 0, 1.5)
    return axe_finish(cv, a)


def pickaxe(tier, stage=0):
    pals = [palette("8a8f96", grip="8a5a2b"), palette("dfe4ea", grip="5a3a22", accent="3f7fff"), palette("5fe0e8", grip="3a2a1c", accent="ffd23f", glow="c8ffff")]
    cv = Canvas(pals[tier], stage)
    shaft(cv, 1, 24.5, 0.95, wrap_to=6 if tier else 0)
    arc = set()
    for i in range(41):
        a = -1.25 + 2.5 * i / 40
        sc, tc = 18.0 + 8.0 * math.cos(a), 8.6 * math.sin(a)
        w = 1.45 - 0.8 * abs(a) / 1.25
        arc |= set(disc_st(cv, sc, tc, max(0.6, w), "b"))
    for (x, y) in arc:
        s, t = cv.st(x, y)
        if math.hypot(s - 18, t) > 8.2:
            cv.set(x, y, "h")
        elif math.hypot(s - 18, t) < 7.3:
            cv.set(x, y, "s")
    cv.fill_st(lambda s, t: 24 <= s <= 27 and abs(t) < 1.6, "G" if tier == 0 else "g")
    if tier == 2:
        gem_st(cv, 25.5, 0, 1.1)
    return axe_finish(cv, arc)


# ----------------------------------------------------------------- 소모품 / 재료 (xy 좌표)
def potion(tier):
    liq = ["ff6b6b", "e03131", "a61e4d", "6a0f5a"][tier]
    pal = palette("dfe8f0", guard="ffd23f", grip="8a5a2b", accent=liq, glow="ffb0d0", extra="b8c8d8")
    cv = Canvas(pal)
    if tier == 0:  # 작은 약병
        glass = cv.fill_xy(lambda x, y: 12 <= x <= 20 and 9 <= y <= 27 or (x - 16) ** 2 + (y - 27) ** 2 <= 16, "b")
        cv.fill_xy(lambda x, y: 12.8 <= x <= 19.2 and 14 <= y <= 30 and ((x - 16) ** 2 + (y - 27) ** 2 <= 10 or y <= 27), "e")
        cv.fill_xy(lambda x, y: 13 <= x <= 19 and 5 <= y <= 9, "w")
    elif tier == 1:  # 둥근 플라스크
        cv.fill_xy(lambda x, y: 13 <= x <= 19 and 5 <= y <= 12, "b")
        disc_xy(cv, 16, 20, 9.5, "b")
        cv.fill_xy(lambda x, y: (x - 16) ** 2 + (y - 20) ** 2 <= 8 ** 2 and y >= 16, "e")
        cv.fill_xy(lambda x, y: 12.5 <= x <= 19.5 and 2 <= y <= 5, "w")
    elif tier == 2:  # 금테 장신 병
        cv.fill_xy(lambda x, y: 13.5 <= x <= 18.5 and 4 <= y <= 11, "b")
        poly_xy(cv, [(13.5, 11), (18.5, 11), (24, 16), (24, 29), (8, 29), (8, 16)], "b")
        poly_xy(cv, [(9, 17), (23, 17), (23, 28), (9, 28)], "e")
        cv.fill_xy(lambda x, y: 12.5 <= x <= 19.5 and 10 <= y <= 12, "g")
        cv.fill_xy(lambda x, y: 11 <= x <= 21 and 21 <= y <= 24, "x")
        cv.fill_xy(lambda x, y: 13 <= x <= 19 and 1.5 <= y <= 4, "w")
    else:  # 보석 세공 결정 병
        poly_xy(cv, [(16, 8), (26, 18), (16, 30), (6, 18)], "b")
        poly_xy(cv, [(16, 11), (23.5, 18.5), (16, 27.5), (8.5, 18.5)], "e")
        cv.fill_xy(lambda x, y: 14 <= x <= 18 and 3 <= y <= 9, "b")
        cv.fill_xy(lambda x, y: 12.5 <= x <= 19.5 and 1 <= y <= 4, "g")
        poly_xy(cv, [(6, 18), (1.5, 13), (4, 20)], "g")
        poly_xy(cv, [(26, 18), (30.5, 13), (28, 20)], "g")
        cv.set(14, 15, "C"); cv.set(13, 16, "C")
    for (x, y) in [(13, 17), (13, 18), (13, 19), (14, 16)]:
        if cv.get(x, y):
            cv.set(x, y, "E")
    cv.outline()
    if tier == 3:
        cv.sparkle(26, 7, 2)
    return cv.render()


def ticket(kind):
    colors = {"check": "2f9e44", "protect": "3f7fff", "rate": "2fbf71", "war": "d9352e", "rune": "9b59ff", "totem": "e0a020", "reset": "7a7a7a"}
    pal = palette("efe6cf", guard="c9a13b", grip="a08a5a", accent=colors[kind], glow="ffffff", extra="d8ccb0")
    cv = Canvas(pal)
    if kind in ("protect", "war", "reset"):  # 두루마리
        cv.fill_xy(lambda x, y: 6 <= x <= 26 and 6 <= y <= 26, lambda x, y: "b" if y < 23 else "s")
        for yy in (5, 26):
            cv.fill_xy(lambda x, y, yy=yy: 4 <= x <= 28 and abs(y - yy) <= 1.6, "x")
            disc_xy(cv, 4, yy, 1.8, "w"); disc_xy(cv, 28, yy, 1.8, "w")
        if kind == "protect":
            poly_xy(cv, [(11, 9), (21, 9), (21, 16), (16, 22), (11, 16)], "e")
            cv.fill_xy(lambda x, y: 15 <= x <= 17 and 10 <= y <= 19, "E")
        elif kind == "war":
            cv.fill_xy(lambda x, y: abs((x - 16) - (y - 15)) < 1.1 and 9 <= y <= 21, "o")
            cv.fill_xy(lambda x, y: abs((x - 16) + (y - 15)) < 1.1 and 9 <= y <= 21, "o")
            disc_xy(cv, 16, 15, 2.2, "e")
        else:
            poly_xy(cv, [(11, 9), (21, 9), (16, 15)], "e")
            poly_xy(cv, [(16, 15), (21, 21), (11, 21)], "e")
            cv.fill_xy(lambda x, y: 10 <= x <= 22 and (8 <= y <= 9 or 21 <= y <= 22), "g")
    elif kind == "totem":  # 금화 메달
        disc_xy(cv, 16, 16, 12.5, "g")
        disc_xy(cv, 16, 16, 10.5, "G")
        disc_xy(cv, 16, 16, 9.5, "g")
        cv.fill_xy(lambda x, y: 12 <= x <= 20 and 9 <= y <= 23, "e")
        cv.fill_xy(lambda x, y: 13.5 <= x <= 14.5 and 12 <= y <= 13 or 17.5 <= x <= 18.5 and 12 <= y <= 13, "o")
        cv.fill_xy(lambda x, y: 13 <= x <= 19 and 17 <= y <= 18, "o")
        poly_xy(cv, [(12, 10), (8, 7), (12, 13)], "e"); poly_xy(cv, [(20, 10), (24, 7), (20, 13)], "e")
    else:  # 카드형 티켓
        cv.fill_xy(lambda x, y: 3 <= x <= 29 and 8 <= y <= 24, lambda x, y: "b" if y < 22 else "s")
        cv.fill_xy(lambda x, y: 6 <= x <= 7 and 8 <= y <= 24, "e")
        for yy in range(9, 24, 3):
            cv.set(6, yy, "E")
        if kind == "check":
            cv.fill_xy(lambda x, y: 10 <= x <= 26 and 20 <= y <= 20.8, "x")
            for (a, b) in [((11, 11), (13, 18)), ((13, 18), (15, 13)), ((15, 13), (17, 18)), ((17, 18), (19, 11))]:
                cv.fill_xy(lambda x, y, a=a, b=b: _seg_d(x, y, a, b) < 0.8, "e")
            cv.fill_xy(lambda x, y: 10 <= x <= 20 and 14 <= y <= 15, "E")
        elif kind == "rate":
            poly_xy(cv, [(18, 10), (24, 16), (20.5, 16), (20.5, 22), (15.5, 22), (15.5, 16), (12, 16)], "e")
            cv.fill_xy(lambda x, y: 9 <= x <= 11 and 11 <= y <= 13, "g")
        else:  # rune
            cv.fill_xy(lambda x, y: 4.5 ** 2 <= (x - 17) ** 2 + (y - 16) ** 2 <= 5.8 ** 2, "e")
            cv.fill_xy(lambda x, y: abs(x - 17) < 0.8 and 12 <= y <= 20 or abs((x - 17) - (y - 16)) < 0.8 and 13 <= y <= 17, "e")
    cv.outline()
    return cv.render()


def _seg_d(x, y, a, b):
    vx, vy = b[0] - a[0], b[1] - a[1]
    u = max(0, min(1, ((x - a[0]) * vx + (y - a[1]) * vy) / (vx * vx + vy * vy)))
    return math.hypot(x - (a[0] + u * vx), y - (a[1] + u * vy))


def rune_stone(tier):
    pal = [palette("8f8f96", accent="d8e0ff", glow="d8e0ff"), palette("5f7fbf", guard="c9d6ff", accent="a8e0ff", glow="bff4ff"),
           palette("7a3fb0", guard="ffd46b", accent="ffe066", glow="fff0a0")][tier]
    cv = Canvas(pal)
    if tier == 0:
        disc_xy(cv, 16, 16, 12, "b")
        cv.fill_xy(lambda x, y: (x - 16) ** 2 + (y - 16) ** 2 <= 144 and x + y > 36, "s")
    elif tier == 1:
        pts = [(16 + 12.5 * math.cos(math.pi / 3 * i + math.pi / 6), 16 + 12.5 * math.sin(math.pi / 3 * i + math.pi / 6)) for i in range(6)]
        poly_xy(cv, pts, lambda x, y: "b" if x + y < 34 else "s")
    else:
        pts = [(16 + 13 * math.cos(math.pi / 4 * i + math.pi / 8), 16 + 13 * math.sin(math.pi / 4 * i + math.pi / 8)) for i in range(8)]
        area = set(poly_xy(cv, pts, "g"))
        pts2 = [(16 + 10.5 * math.cos(math.pi / 4 * i + math.pi / 8), 16 + 10.5 * math.sin(math.pi / 4 * i + math.pi / 8)) for i in range(8)]
        poly_xy(cv, pts2, lambda x, y: "b" if x + y < 34 else "s")
    k = "e" if tier == 0 else "c"
    cv.fill_xy(lambda x, y: abs(x - 16) < 1 and 9 <= y <= 23, k)
    if tier >= 1:
        cv.fill_xy(lambda x, y: abs((x - 16) - (y - 12) * 0.9) < 0.9 and 12 <= y <= 17, k)
        cv.fill_xy(lambda x, y: abs((x - 16) + (y - 12) * 0.9) < 0.9 and 12 <= y <= 17, k)
    if tier >= 2:
        cv.fill_xy(lambda x, y: abs(y - 20) < 0.8 and 11 <= x <= 21, k)
        cv.set(16, 9, "C")
    cv.outline()
    if tier == 2:
        cv.sparkle(26, 5, 2)
    return cv.render()


def shard(el):
    P = {"fire": ("ff6a2b", "ffe066"), "wind": ("7fd0ff", "ffffff"), "dark": ("4a2a6a", "c07fff"), "nature": ("3fbf5f", "e8ffe0"), "earth": ("c08a3f", "ffe0a0")}
    base, glow = P[el]
    cv = Canvas(palette(base, accent=glow, glow=glow))
    if el == "fire":
        pts = [(16, 2), (20, 10), (24, 9), (23, 18), (21, 27), (11, 27), (8, 19), (10, 12), (13, 14)]
    elif el == "wind":
        pts = [(24, 3), (26, 8), (20, 18), (12, 28), (9, 26), (13, 17), (18, 8)]
    elif el == "dark":
        pts = [(15, 2), (19, 9), (26, 7), (22, 16), (25, 26), (15, 22), (7, 28), (10, 17), (5, 9), (12, 10)]
    elif el == "nature":
        pts = [(24, 4), (26, 14), (20, 24), (10, 28), (6, 24), (9, 14), (16, 7)]
    else:
        pts = [(12, 4), (21, 4), (26, 12), (23, 27), (10, 27), (6, 13)]
    area = set(poly_xy(cv, pts, "b"))
    for (x, y) in area:
        if x + y < 26:
            cv.set(x, y, "h")
        elif x + y > 40:
            cv.set(x, y, "s")
    if el == "nature":
        cv.fill_xy(lambda x, y: _seg_d(x, y, (22, 7), (9, 26)) < 0.7, "e")
    elif el == "wind":
        cv.fill_xy(lambda x, y: _seg_d(x, y, (24, 5), (11, 26)) < 0.7, "e")
    else:
        cv.fill_xy(lambda x, y: _seg_d(x, y, (16, 6), (16, 24)) < 0.7, "e")
    cv.outline()
    cv.sparkle(7, 6, 1)
    return cv.render()


def orb(el):
    P = {"fire": ("ff4a1f", "ffe066"), "wind": ("3fa9ff", "ffffff"), "dark": ("3a1a5a", "d07fff"), "nature": ("2fbf71", "e0ffe8"), "earth": ("c9a13b", "fff0b0")}
    base, glow = P[el]
    cv = Canvas(palette(base, accent=glow, glow=glow))
    area = set(disc_xy(cv, 16, 16, 11.5, "b"))
    for (x, y) in area:
        d = math.hypot(x + 0.5 - 12, y + 0.5 - 12)
        cv.set(x, y, "h" if d < 4 else "b" if d < 11 else "s")
    for (x, y) in [(9, 8), (10, 8), (9, 9)]:
        cv.set(x, y, "*")
    k = "e"
    if el == "fire":
        poly_xy(cv, [(16, 8), (20, 15), (19, 22), (13, 22), (12, 15), (14.5, 13)], k)
    elif el == "wind":
        cv.fill_xy(lambda x, y: abs(math.hypot(x - 16, y - 16) - (2 + (math.atan2(y - 16, x - 16) + math.pi) * 1.1)) < 0.8, k)
    elif el == "dark":
        cv.fill_xy(lambda x, y: ((x - 16) / 7) ** 2 + ((y - 16) / 3.5) ** 2 <= 1, k)
        disc_xy(cv, 16, 16, 2, "o")
    elif el == "nature":
        poly_xy(cv, [(10, 22), (14, 12), (22, 9), (19, 18)], k)
        cv.fill_xy(lambda x, y: _seg_d(x, y, (10, 22), (20, 11)) < 0.6, "s")
    else:
        poly_xy(cv, [(8, 22), (14, 12), (17, 16), (19, 13), (24, 22)], k)
    cv.outline()
    for (x, y) in [(3, 5), (28, 9), (5, 27), (27, 26)]:
        cv.sparkle(x, y, 1)
    return cv.render()


def ore(kind):
    P = {"black": ("2a2a30", "8a8aff"), "green": ("6b6b70", "3fdc6a"), "red": ("6b5f5f", "ff4a4a"), "blue": ("5f6470", "4a8aff"), "gray": ("9aa0a8", "dfe4ea")}
    base, gemc = P[kind]
    cv = Canvas(palette(base, accent=gemc, glow=gemc))
    shapes = {"black": [(8, 8), (16, 3), (26, 9), (27, 22), (17, 29), (5, 23)], "green": [(5, 12), (13, 5), (24, 6), (28, 17), (22, 28), (8, 26)],
              "red": [(6, 9), (18, 4), (27, 13), (24, 27), (11, 28), (4, 19)], "blue": [(9, 5), (22, 4), (28, 15), (21, 28), (7, 26), (3, 15)],
              "gray": [(5, 13), (11, 6), (22, 6), (28, 14), (25, 25), (13, 28), (4, 22)]}
    area = set(poly_xy(cv, shapes[kind], "b"))
    for (x, y) in area:
        if x + y < 20:
            cv.set(x, y, "h")
        elif x + y > 42:
            cv.set(x, y, "s")
    if kind == "black":
        for (a, b) in [((10, 22), (15, 9)), ((15, 24), (21, 11)), ((20, 24), (24, 15))]:
            cv.fill_xy(lambda x, y, a=a, b=b: _seg_d(x, y, a, b) < 1.4, "e")
    elif kind == "gray":
        cv.fill_xy(lambda x, y: (x * 7 + y * 13) % 11 == 0 and (int(x), int(y)) in area, "s")
    else:
        for (gx, gy) in [(12, 14), (19, 11), (17, 20), (22, 19)]:
            gem_xy(cv, gx, gy, 2.4 if kind != "red" else 2.0)
    cv.outline()
    return cv.render()


# ----------------------------------------------------------------- 카탈로그 (Java CustomModelData 와 일치)
def P(blade, **kw):
    return palette(blade, **kw)


SWORDS = {
    "sword_10": {"pal": P("a0a6ad", guard="6b6b6b", grip="7a4f2b"), "draw": _s_common},
    "sword_20": {"pal": P("d2d8e0", guard="c9a13b", grip="3a2a4a"), "draw": _s_steel},
    "sword_30": {"pal": P("a8b0b8", guard="5c6570", grip="8a1f1f", accent="c9a13b"), "draw": _s_war},
    "sword_40": {"pal": P("5c6570", guard="2a2a30", grip="3a1a1a", accent="e0202a", extra="b8262f"), "draw": _s_dread},
    "witch_sword": {"pal": P("8a4fb8", guard="3a2a4a", grip="1f1a24", accent="7dff6a", extra="5fe05a"), "draw": _s_witch},
    "dwarf_sword": {"pal": P("c9ced6", guard="e0b030", grip="6b3f1f", accent="3f7fff", extra="ffd23f"), "draw": _s_dwarf},
    "harpy_sword": {"pal": P("e9e6de", guard="8fd3ff", grip="dfe8f0", accent="7fd0ff"), "draw": _s_harpy},
    "sea_sword": {"pal": P("1f9bb0", guard="e0c080", grip="0f4a5a", accent="f2efe6", extra="ff7f6a"), "draw": _s_sea},
    "bungbung_sword": {"pal": P("ff8a1f", guard="2a1a1a", grip="1a1010", accent="ffd23f", extra="ffe066"), "draw": _s_flame},
    "spirit_qinglong": {"pal": P("3fb8ff", guard="ffd23f", grip="1f6b45", accent="a8f0ff", glow="bff8ff", extra="8fe0ff"), "draw": _s_qinglong},
    "relic_sword": {"pal": P("cfe6ff", guard="ffd46b", grip="2a2a4a", accent="9ff6ff", glow="9ff6ff", outline="1a1a3a"), "draw": _s_relic},
}
BS_PAL = [P("b0b8c0", guard="6b6b6b", grip="5a3a22"), P("9fd0ff", guard="dfe8f0", grip="2a3a5a", accent="3f7fff"),
          P("b07fff", guard="ffd23f", grip="3a1f4a", accent="e04848", extra="ffd23f"), P("4a4a52", guard="8a8a8a", grip="2a1a10", accent="ff6a1f", extra="ff8a1f"),
          P("ff4f6f", guard="ffd46b", grip="3a1020", accent="ffe066", glow="ffb0c0")]
for _t in range(5):
    SWORDS["bs_sword_%d" % (_t + 1)] = {"pal": BS_PAL[_t], "draw": _bs_sword(_t)}

DAGGERS = {
    "dagger_10": {"pal": P("a0a6ad", guard="6b6b6b", grip="7a4f2b"), "draw": _d_basic},
    "dagger_20": {"pal": P("e6ecf2", guard="c9a13b", grip="3a2a4a"), "draw": _d_stiletto},
    "dagger_30": {"pal": P("cfd8e8", guard="9fb7d9", grip="2a3a5a", accent="3f7fff"), "draw": _d_silver},
    "dagger_40": {"pal": P("c43a3a", guard="3a2a2a", grip="1a1010", accent="ffd23f"), "draw": _d_red},
    "witch_dagger": {"pal": P("8a4fb8", guard="3a2a4a", grip="1f1a24", accent="7dff6a", extra="5fe05a"), "draw": _d_witch},
    "dwarf_dagger": {"pal": P("c9ced6", guard="e0b030", grip="6b3f1f", accent="3f7fff", extra="ffd23f"), "draw": _d_dwarf},
    "harpy_dagger": {"pal": P("e9e6de", guard="8fd3ff", grip="dfe8f0", accent="7fd0ff"), "draw": _d_harpy},
    "sea_dagger": {"pal": P("f2efe6", guard="1f9bb0", grip="0f4a5a", accent="1f9bb0"), "draw": _d_sea},
    "bungbung_dagger": {"pal": P("ff8a1f", guard="2a1a1a", grip="1a1010", accent="ffd23f"), "draw": _d_flame},
    "spirit_zhuque": {"pal": P("e8342a", guard="ffc93c", grip="3a1010", accent="ffe066", glow="ffe066", extra="ffc93c"), "draw": _d_zhuque},
    "relic_dagger": {"pal": P("1c2440", guard="53ffd8", grip="0f1424", accent="53ffd8", glow="53ffd8", outline="05070f"), "draw": _d_relic},
}
for _t in range(5):
    DAGGERS["bs_dagger_%d" % (_t + 1)] = {"pal": BS_PAL[_t], "draw": _bs_dagger(_t)}

AXES = {
    "axe_10": {"pal": P("9a5a34", grip="6b4a2b", extra="7a5a3a"), "draw": _a_rusty},
    "axe_20": {"pal": P("5f8fd0", grip="5a3a22", extra="6b4a2b"), "draw": _a_bearded},
    "axe_30": {"pal": P("c8ced6", grip="3a2a1c", extra="4a3a2a"), "draw": _a_double},
    "axe_40": {"pal": P("e8b83a", grip="3a1c10", accent="e04848", extra="4a2a1a"), "draw": _a_gold},
    "witch_axe": {"pal": P("8a4fb8", grip="1f1a24", accent="7dff6a", extra="5fe05a"), "draw": _a_witch},
    "dwarf_axe": {"pal": P("c9ced6", grip="6b3f1f", accent="3f7fff", extra="ffd23f"), "draw": _a_dwarf},
    "harpy_axe": {"pal": P("e9e6de", grip="8a7a6a", extra="b8a898"), "draw": _a_harpy},
    "sea_axe": {"pal": P("1f9bb0", guard="e0c080", grip="0f4a5a", extra="1a5f6b"), "draw": _a_anchor},
    "bungbung_axe": {"pal": P("ff8a1f", grip="1a1010", extra="ffe066"), "draw": _a_flame},
    "spirit_baihu": {"pal": P("f0f0f0", grip="2a2a2a", accent="3fa9ff", glow="9fe0ff", extra="2a2a2a"), "draw": _a_baihu},
    "relic_axe": {"pal": P("1a1a22", guard="ffb300", grip="1a1a1a", glow="ffb300", extra="3a2a1a", outline="050505"), "draw": _a_relic},
}
for _t in range(5):
    AXES["bs_axe_%d" % (_t + 1)] = {"pal": BS_PAL[_t], "draw": _bs_axe(_t)}

SHIELDS = {
    "shield_10": ("round", P("9a6a3a", guard="8a8a8a", rim="6b6b6b"), em_boss, dict(wood=True, studs=False)),
    "shield_20": ("heater", P("b8c0c8", guard="6b6b6b", rim="7a7f86", accent="3f7fff"), em_wing, {}),
    "shield_30": ("kite", P("8a2a2a", guard="c9a13b", rim="c9ced6", accent="f2efe6"), em_cross, {}),
    "shield_40": ("tower", P("c9a13b", guard="ffe066", rim="8a6a1f", accent="e04848"), em_lion, dict(rim_w=2)),
    "witch_shield": ("pent", P("4a2a6a", guard="7dff6a", rim="2a1a3a", accent="7dff6a", extra="c8b8d8"), em_eye, {}),
    "dwarf_shield": ("round", P("8a6a3a", guard="e0b030", rim="c9ced6", accent="3f7fff"), em_rune, dict(rim_w=2)),
    "harpy_shield": ("winged", P("e9e6de", guard="8fd3ff", rim="8fd3ff", accent="7fd0ff"), em_wing, {}),
    "sea_shield": ("shell", P("ff9f8a", guard="f2efe6", rim="e06a5a", accent="1f9bb0", extra="f2efe6"), em_shell, dict(studs=False)),
    "bungbung_shield": ("sun", P("e05020", guard="ffd23f", rim="ffd23f", accent="ffe066", extra="ff8a1f"), em_sun, dict(studs=False)),
}
BS_SHIELD = [("round", em_boss), ("heater", em_cross), ("kite", em_wing), ("tower", em_rune), ("winged", em_core)]
for _t in range(5):
    SHIELDS["bs_shield_%d" % (_t + 1)] = (BS_SHIELD[_t][0], BS_PAL[_t], BS_SHIELD[_t][1], dict(rim_w=2 if _t >= 3 else 1))


def weapon_image(name, stage=0):
    if name in SWORDS:
        return sword_design(name, stage)
    if name in DAGGERS:
        D = DAGGERS[name]
        cv = Canvas(D["pal"], stage)
        D["draw"](cv)
        return finish(cv)
    if name in AXES:
        return axe_design(name, stage)
    if name in SHIELDS:
        kind, pal, em, opt = SHIELDS[name]
        return shield_design(kind, pal, em, stage, **opt)
    if name == "relic_shield":
        return relic_shield(stage)
    if name == "spirit_xuanwu":
        return spear_xuanwu(stage)
    raise KeyError(name)
