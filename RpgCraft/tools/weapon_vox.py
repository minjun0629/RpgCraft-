"""
v5.10.21 무기 3D 복셀 모델 (아머러스 워크샵 방식) — 모든 무기를 작은 정육면체로 깎아 만든다.

픽셀 그림에 두께만 주던 예전 방식(voxel3d) 대신, 무기 종류마다 진짜 입체 형상을 조각한다.
 - 검: 마름모 단면 칼날(가운데 능선 · 홈 · 날 끝), 입체 가드(위로 휜 끝 · 가운데 보석), 끈을 감은 둥근 손잡이, 폼멜 보석
 - 단검: 짧고 휜 칼날 · 작은 가드 / 도끼: 부채꼴 날(한쪽 · 양날) · 뒤 가시 · 감은 자루 / 망치 · 몽둥이: 큰 머리 · 징
 - 창: 긴 자루 · 잎 모양 날 · 술 / 지팡이: 보주 · 발톱 · 고리 / 활: 휜 몸 · 감은 손잡이 · 시위 / 방패: 둥근 볼록 판 · 테 · 가운데 돌기
색은 원래 무기 그림(부품 키)에서 칼날 · 날 · 가드 · 손잡이 · 보석 색을 뽑아 쓰고, 모양(가드 · 칼날 · 폼멜)은 이름마다 다르게 고른다.
세로로 반듯하게 조각한 뒤 모든 요소를 z 축으로 -45° 돌려 바닐라 무기처럼 대각선(손잡이 왼쪽 아래 · 끝 오른쪽 위)으로 든다.
"""
import colorsys
import hashlib
import math

import voxel_lib as VL
import boss_models as bm

SIZE_VS = 0.5          # 무기 복셀 크기 (모델 좌표 1/32 블록)
C = (8.0, 8.0)         # 가운데 (x, z)


# ------------------------------------------------------------------ 색
def _hex(rgb):
    return "%02x%02x%02x" % tuple(max(0, min(255, int(round(c)))) for c in rgb[:3])


def _rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def lit(h, k):
    r, g, b = _rgb(h)
    if k >= 1:
        return _hex((r + (255 - r) * (k - 1), g + (255 - g) * (k - 1), b + (255 - b) * (k - 1)))
    return _hex((r * k, g * k, b * k))


def _lum(h):
    r, g, b = _rgb(h)
    return 0.3 * r + 0.59 * g + 0.11 * b


def _sat(h):
    r, g, b = (c / 255 for c in _rgb(h))
    return colorsys.rgb_to_hls(r, g, b)[2]


KEYMAP = {"blade": "b", "edge": "h", "shade": "s", "inlay": "x", "guard": "g", "grip": "w", "gem": "e", "glow": "c", "rim": "r"}


def palette_from(img, keys):
    """원래 그림에서 부위별 대표 색 (그림의 부품 키마다 가장 많이 쓴 색). 키가 없으면 색 빈도 · 밝기 · 채도로 추정"""
    px = {(x, y): img.getpixel((x, y)) for y in range(img.height) for x in range(img.width) if img.getpixel((x, y))[3] > 0}
    out = {}
    if keys and set(keys) == set(px):
        for g, k in KEYMAP.items():
            freq = {}
            for p, kk in keys.items():
                if kk == k:
                    h = _hex(px[p])
                    freq[h] = freq.get(h, 0) + 1
            if freq:
                out[g] = max(freq.items(), key=lambda t: t[1])[0]
    freq = {}
    for c in px.values():
        h = _hex(c)
        freq[h] = freq.get(h, 0) + 1
    common = [h for h, _ in sorted(freq.items(), key=lambda t: -t[1]) if 40 < _lum(h) < 245] or list(freq) or ["9a9aa8"]
    top = common[:12]
    by_sat = sorted(top, key=lambda h: -_sat(h))
    bright = sorted(top, key=lambda h: -_lum(h))
    dark = sorted(top, key=lambda h: _lum(h))
    out.setdefault("blade", common[0])
    out.setdefault("edge", lit(bright[0], 1.15))
    out.setdefault("shade", lit(out["blade"], 0.7))
    out.setdefault("guard", by_sat[0] if _sat(by_sat[0]) > 0.25 else lit(top[min(1, len(top) - 1)], 0.9))
    out.setdefault("grip", dark[0] if _lum(dark[0]) < 150 else "5a3a22")
    out.setdefault("gem", by_sat[0] if _sat(by_sat[0]) > 0.35 else "e04040")
    out.setdefault("glow", out["gem"])
    out.setdefault("inlay", out["glow"])
    out.setdefault("rim", out["guard"])
    if abs(_lum(out["edge"]) - _lum(out["blade"])) < 18:
        out["edge"] = lit(out["blade"], 1.35)
    return out


def stage_colors(colors, stage):
    """강화 변형 (+7 각인 · +10 완성): 같은 모양, 팔레트만 더 밝고 화려하게"""
    if stage == 0:
        return list(colors)
    out = []
    for h in colors:
        r, g, b = (c / 255 for c in _rgb(h))
        hh, ll, ss = colorsys.rgb_to_hls(r, g, b)
        if stage == 1:
            ll, ss = min(1, ll * 1.06 + 0.02), min(1, ss * 1.25)
        else:
            ll, ss = min(1, ll * 1.12 + 0.05), min(1, ss * 1.45 + 0.05)
        r, g, b = colorsys.hls_to_rgb(hh, ll, ss)
        out.append(_hex((r * 255, g * 255, b * 255)))
    return out


# ------------------------------------------------------------------ 모양 선택
def _seed(name):
    return int(hashlib.md5(name.encode()).hexdigest()[:8], 16)


def _tier(name):
    digits = "".join(ch for ch in name.split("_")[-1] if ch.isdigit())
    n = int(digits) if digits else 3
    if n >= 10:   # sword_10 ~ sword_40 같은 레벨 표기
        n = n // 10
    return max(1, min(10, n))


def kind_of(name):
    if "shield" in name:
        return "shield"
    if "bow" in name:
        return "bow"
    if "staff" in name:
        return "staff"
    if "spear" in name or name == "spirit_xuanwu":
        return "spear"
    if name.startswith("hammer"):
        return "hammer"
    if name == "weapon_club":
        return "club"
    if "axe" in name or name == "spirit_baihu":
        return "axe"
    if "dagger" in name or name == "spirit_zhuque":
        return "dagger"
    if "sword" in name or name == "spirit_qinglong":
        return "sword"
    return None


# ------------------------------------------------------------------ 기본 도형 (세로 무기 공간: 손잡이 아래 · 끝이 위, x=8 · z=8 가운데)
def _fill(g, y0, y1, prof, colf, pri=2):
    """y0~y1 를 따라 단면이 prof(y) → (가로 반폭, 세로(두께) 반폭, 모양) 인 몸통. 모양: 'round' 원 · 'diamond' 마름모 · 'box' 사각"""
    vs = VL.VS
    for j in range(math.floor(y0 / vs), math.floor(y1 / vs) + 1):
        y = (j + 0.5) * vs
        if y < y0 or y > y1:
            continue
        hw, hd, shape, *rest = prof(y)
        if hw <= 0 or hd <= 0:
            continue
        cx = C[0] + (rest[0] if rest else 0)
        for i in range(math.floor((cx - hw) / vs), math.floor((cx + hw) / vs) + 1):
            x = (i + 0.5) * vs
            for k in range(math.floor((C[1] - hd) / vs), math.floor((C[1] + hd) / vs) + 1):
                z = (k + 0.5) * vs
                u, w = (x - cx) / max(hw, 1e-6), (z - C[1]) / max(hd, 1e-6)
                if shape == "round":
                    inside = u * u + w * w <= 1.0
                elif shape == "diamond":
                    inside = abs(u) <= 1 and abs(w) <= 1 - abs(u) * 0.75
                else:
                    inside = abs(u) <= 1 and abs(w) <= 1
                if not inside and hw <= vs and hd <= vs:
                    inside = abs(x - cx) < vs and abs(z - C[1]) < vs
                if inside:
                    g.put(x, y, z, colf(x, y, z, u, w), pri)


def _grip(g, y0, y1, r, P, pri=3):
    """끈을 비스듬히 감은 둥근 손잡이"""
    dark = lit(P["grip"], 0.7)

    def col(x, y, z, u, w):
        band = int((y * 2.2 + math.atan2(w, u) * 0.6) % 2)
        return P["grip"] if band else dark
    _fill(g, y0, y1, lambda y: (r, r, "round"), col, pri)
    for yy in (y0 + 0.2, y1 - 0.2):   # 양 끝 쇠고리
        _fill(g, yy - 0.3, yy + 0.3, lambda y: (r + 0.35, r + 0.35, "round"), lambda *a: lit(P["guard"], 0.9), pri + 1)


def _pommel(g, y, P, style, pri=4):
    if style == 0:      # 둥근 보주 + 보석
        g.sphere((C[0], y, C[1]), 1.15, lambda x, yy, z, d: P["gem"] if d < 0.3 else P["guard"], pri)
    elif style == 1:    # 마름모 폼멜
        _fill(g, y - 1.3, y + 1.0, lambda yy: (1.2 * (1 - abs(yy - y + 0.15) / 1.2), 1.0 * (1 - abs(yy - y + 0.15) / 1.2), "diamond"),
              lambda *a: P["guard"], pri)
        g.dot(C[0], y, C[1] + 0.9, P["gem"], pri + 2, 0.6)
        g.dot(C[0], y, C[1] - 0.9, P["gem"], pri + 2, 0.6)
    else:               # 고리 폼멜
        g.ring((C[0], y - 0.4, C[1]), 0.9, 0.32, P["guard"], pri, axis="z")
        g.dot(C[0], y - 0.4, C[1], P["gem"], pri + 1, 0.6)


def _bar(g, x0, x1, yf, hh, hz, colf, pri):
    """x0→x1 로 뻗는 막대: 위치 yf(t), 높이 반폭 hh(t), 두께 반폭 hz(t)  (t: 0~1)"""
    vs = VL.VS
    a, b = min(x0, x1), max(x0, x1)
    for i in range(math.floor(a / vs), math.floor(b / vs) + 1):
        x = (i + 0.5) * vs
        if x < a or x > b:
            continue
        t = abs(x - x0) / max(1e-6, abs(x1 - x0))
        cy, h, dz = yf(t), hh(t), hz(t)
        for j in range(math.floor((cy - h) / vs), math.floor((cy + h) / vs) + 1):
            for k in range(math.floor((C[1] - dz) / vs), math.floor((C[1] + dz) / vs) + 1):
                y, z = (j + 0.5) * vs, (k + 0.5) * vs
                g.put(x, y, z, colf(x, y, z, t, (y - cy) / max(h, 1e-6), (z - C[1]) / max(dz, 1e-6)), pri)


def _guard(g, y, half, P, style, pri=5):
    """가드: 0 곧은 막대 · 1 끝이 위로 젖혀진 날개 · 2 칼날 쪽으로 휜 초승달 · 3 끝에 가시"""
    lift = [0.0, 1.6, 2.4, 0.0][style]
    hi, mid = lit(P["guard"], 1.25), P["guard"]

    def col(x, yy, z, t, v, w):
        if abs(v) > 0.7 or abs(w) > 0.75:
            return hi if v > 0 else lit(mid, 0.8)
        return mid
    for sd in (-1, 1):
        _bar(g, C[0] + sd * 1.0, C[0] + sd * half, lambda t: y + lift * t ** 2, lambda t: 0.75 - 0.3 * t, lambda t: 0.85 - 0.25 * t, col, pri)
        end = (C[0] + sd * half, y + lift, C[1])
        if style == 3:
            g.cone(end, (end[0] + sd * 1.4, end[1] - 0.2, end[2]), 0.55, 0.1, P["edge"], pri + 1)
        else:
            g.sphere(end, 0.7, lambda x, yy, z, d: hi if d < 0.4 else mid, pri + 1)
    _fill(g, y - 1.0, y + 1.1, lambda yy: (1.3, 1.15, "box"), lambda x, yy, z, u, w: hi if abs(u) > 0.8 or abs(w) > 0.8 else mid, pri + 1)   # 가운데 덩어리
    for sd in (-1, 1):
        g.dot(C[0], y + 0.05, C[1] + sd * 1.2, P["gem"], pri + 3, 0.9)
        g.dot(C[0], y + 0.05, C[1] + sd * 1.45, lit(P["gem"], 1.4), pri + 4, 0.5)


def _blade(g, y0, y1, width, thick, P, style, curve=0.0, pri=4, fuller=True):
    """칼날: 마름모 단면 (가운데 능선 · 날 끝 밝게 · 상감 홈). style: 0 곧은 날 · 1 잎 모양 · 2 넓은 대검 · 3 물결(플랑베르주)"""
    L = y1 - y0

    def prof(y):
        t = (y - y0) / L
        if style == 1:
            w = width * (0.8 + 0.4 * math.sin(math.pi * min(1, t * 1.1)))
        elif style == 2:
            w = width * 1.25 * (1 - 0.12 * t)
        else:
            w = width * (1 - 0.1 * t)
        if style == 3:
            w *= 1 + 0.14 * math.sin(t * math.pi * 7)
        tip = 0.8 if style != 2 else 0.86
        if t > tip:
            w *= max(0.0, 1 - (t - tip) / (1 - tip)) ** 0.85
        return (w / 2, thick / 2 * (1 - 0.3 * t), "diamond", curve * t * t * 3)

    def col(x, y, z, u, w):
        t = (y - y0) / L
        if abs(u) > 0.74 or t > 0.95:
            return P["edge"]                                   # 날
        if fuller and abs(u) < 0.22 and 0.04 < t < 0.7:
            return P["inlay"] if abs(w) > 0.3 else lit(P["inlay"], 0.75)   # 상감 홈
        if abs(u) < 0.45:
            return lit(P["blade"], 1.12)                       # 능선 쪽 광택
        return P["blade"] if u > 0 else P.get("shade", lit(P["blade"], 0.78))   # 한쪽 면은 그늘
    _fill(g, y0, y1, prof, col, pri)


def _cone_y(g, y0, y1, r0, r1, colf, pri=3, shape="round"):
    _fill(g, y0, y1, lambda y: (r0 + (r1 - r0) * (y - y0) / max(1e-6, y1 - y0),) * 2 + (shape,), colf, pri)


# ------------------------------------------------------------------ 무기 종류별
def sword(g, P, s, tier):
    total = 19.5 + min(4, tier) * 0.35
    y0 = 8 - total / 2
    gl = 3.4
    _pommel(g, y0 + 0.9, P, s % 3)
    _grip(g, y0 + 1.6, y0 + 1.6 + gl, 0.75, P)
    gy = y0 + 1.6 + gl + 0.7
    width = 3.2 + (s >> 3) % 3 * 0.35 + (0.5 if tier >= 5 else 0)
    _guard(g, gy, width * 1.25 + 0.8, P, (s >> 5) % 4)
    _blade(g, gy + 0.9, y0 + total, width, 1.5, P, (s >> 7) % 4)
    if tier >= 4 or (s >> 9) % 3 == 0:   # 칼날 아래 보석 장식
        g.dot(C[0], gy + 1.6, C[1] + 0.45, P["gem"], 7, 0.55)
        g.dot(C[0], gy + 1.6, C[1] - 0.45, P["gem"], 7, 0.55)


def dagger(g, P, s, tier):
    total = 14.0 + min(4, tier) * 0.3
    y0 = 8 - total / 2 - 1.5
    _pommel(g, y0 + 0.8, P, s % 3)
    _grip(g, y0 + 1.4, y0 + 4.2, 0.68, P)
    gy = y0 + 4.8
    _guard(g, gy, 2.4, P, (s >> 4) % 4)
    curve = [0.0, 0.25, -0.25, 0.4][(s >> 6) % 4]
    _blade(g, gy + 0.9, y0 + total, 2.6, 1.2, P, [0, 1, 0, 1][(s >> 8) % 4], curve=curve)


def axe(g, P, s, tier):
    y0, y1 = -2.5, 18.0
    _fill(g, y0, y1 - 0.5, lambda y: (0.6, 0.6, "round"), lambda x, y, z, u, w: lit(P["grip"], 0.85 + 0.15 * ((int(y * 1.5)) % 2)), 2)
    _grip(g, y0 + 0.4, y0 + 4.6, 0.72, P)
    g.sphere((C[0], y0, C[1]), 0.85, P["guard"], 4)
    hy = y1 - 3.6                       # 머리 가운데
    double = (s >> 3) % 3 == 0
    size = 4.6 + min(4, tier) * 0.25
    vs = VL.VS
    for side in ((-1, 1) if double else (-1,)):   # 날은 -x 쪽 (바닐라 도끼처럼 위 왼쪽)
        for i in range(int(size / vs) + 1):
            dist = 0.7 + (i + 0.5) * vs
            f = min(1.0, (dist - 0.7) / size)
            half = 1.3 + 2.4 * f ** 1.3          # 부채꼴로 벌어짐
            cy = hy + 0.4 * f
            th = 0.95 * (1 - f) + 0.28
            x = C[0] + side * dist
            for j in range(math.floor((cy - half) / vs), math.floor((cy + half) / vs) + 1):
                y = (j + 0.5) * vs
                v = y - cy
                if abs(v) > half:
                    continue
                edge = f > 0.86 or abs(v) > half - 0.4
                col = P["edge"] if edge else lit(P["blade"], 1.05 - 0.2 * f)
                for k in range(math.floor((C[1] - th) / vs), math.floor((C[1] + th) / vs) + 1):
                    g.put(x, y, (k + 0.5) * vs, col, 5)
    if not double:   # 뒤 가시
        g.cone((C[0] + 0.7, hy, C[1]), (C[0] + 3.0, hy - 0.6, C[1]), 0.8, 0.1, P["edge"], 5)
    _fill(g, hy - 1.6, hy + 1.6, lambda y: (1.0, 0.95, "box"), lambda *a: P["guard"], 6)   # 머리를 감싼 쇠
    g.cone((C[0], y1 - 0.6, C[1]), (C[0], y1 + 1.6, C[1]), 0.65, 0.1, P["edge"], 5)    # 위 가시
    g.dot(C[0], hy, C[1] + 1.0, P["gem"], 8, 0.6)
    g.dot(C[0], hy, C[1] - 1.0, P["gem"], 8, 0.6)


def hammer(g, P, s, tier):
    y0, y1 = -2.0, 16.5
    _fill(g, y0, y1, lambda y: (0.6, 0.6, "round"), lambda x, y, z, u, w: lit(P["grip"], 0.85 + 0.15 * (int(y * 1.5) % 2)), 2)
    _grip(g, y0 + 0.4, y0 + 4.4, 0.72, P)
    hy = y1 - 1.6
    _fill(g, hy - 2.0, hy + 2.0, lambda y: (3.2, 1.9, "box"), lambda x, y, z, u, w: P["edge"] if abs(u) > 0.82 else lit(P["blade"], 1.0 - 0.15 * abs(w)), 4)
    _fill(g, hy - 2.4, hy + 2.4, lambda y: (1.2, 2.1, "box"), lambda *a: P["guard"], 5)
    for sx in (-1, 1):
        for sy in (-1, 1):
            g.dot(C[0] + sx * 3.25, hy + sy * 1.1, C[1], P["gem"] if sy > 0 else P["guard"], 6, 0.7)


def club(g, P, s, tier):
    _cone_y(g, -2.0, 16.0, 0.7, 2.2, lambda x, y, z, u, w: lit(P["blade"], 0.85 + 0.15 * (int(y * 1.3 + u * 2) % 2)), 2)
    _grip(g, -1.6, 2.4, 0.78, P)
    for k in range(7):
        a = k * 2.1
        y = 8.5 + k * 1.05
        r = 0.7 + 1.5 * (y + 2) / 18
        g.cone((C[0] + math.cos(a) * r, y, C[1] + math.sin(a) * r), (C[0] + math.cos(a) * (r + 1.1), y + 0.3, C[1] + math.sin(a) * (r + 1.1)), 0.35, 0.08, P["edge"], 4)


def spear(g, P, s, tier):
    y0, y1 = -3.5, 13.5
    _fill(g, y0, y1, lambda y: (0.62, 0.62, "round"), lambda x, y, z, u, w: lit(P["grip"], 0.9 + 0.12 * (int(y * 0.9) % 2)), 2)
    for yy in (y0 + 3.0, y0 + 9.0, y1 - 0.5):   # 쇠고리
        _fill(g, yy - 0.35, yy + 0.35, lambda y: (0.78, 0.78, "round"), lambda *a: P["guard"], 3)
    g.sphere((C[0], y0, C[1]), 0.75, P["guard"], 3)
    _fill(g, y1 - 0.6, y1 + 0.8, lambda y: (1.1, 1.1, "box"), lambda *a: P["guard"], 4)   # 날 받침
    _blade(g, y1 + 0.6, y1 + 9.0, 3.8, 1.5, P, 1 if (s >> 3) % 2 else 0, fuller=True)
    if (s >> 5) % 2:   # 갈고리 날개
        for sx in (-1, 1):
            g.cone((C[0], y1 + 0.8, C[1]), (C[0] + sx * 3.0, y1 + 2.4, C[1]), 0.7, 0.12, P["edge"], 4)
    for k in range(5):   # 날 아래 술
        a = k * 2 * math.pi / 5
        g.tube([(C[0], y1 - 0.8, C[1]), (C[0] + math.cos(a) * 1.4, y1 - 4.0, C[1] + math.sin(a) * 1.4)], [0.35, 0.15], P["gem"], 3)


def staff(g, P, s, tier):
    y0, y1 = -3.0, 12.5
    _fill(g, y0, y1, lambda y: (0.68, 0.68, "round"), lambda x, y, z, u, w: lit(P["grip"], 0.88 + 0.14 * (int(y * 1.2 + u) % 2)), 2)
    _fill(g, y1 - 1.2, y1 + 0.8, lambda y: (1.15, 1.15, "round"), lambda *a: P["guard"], 3)   # 머리 받침
    for yy in (y0 + 4.0, y0 + 5.0):
        _fill(g, yy - 0.3, yy + 0.3, lambda y: (0.85, 0.85, "round"), lambda *a: P["guard"], 3)
    style = s % 3
    top = y1 + 3.0
    g.sphere((C[0], top, C[1]), 2.3, lambda x, y, z, d: lit(P["gem"], 1.5) if d < 0.18 else lit(P["gem"], 1.15) if d < 0.5 else P["gem"], 4)
    if style == 0:     # 감싸는 발톱
        for k in range(4):
            a = k * math.pi / 2 + 0.4
            g.tube([(C[0], y1 - 0.4, C[1]), (C[0] + math.cos(a) * 2.8, top - 0.6, C[1] + math.sin(a) * 2.8), (C[0] + math.cos(a) * 1.3, top + 2.8, C[1] + math.sin(a) * 1.3)],
                   [0.55, 0.42, 0.15], P["guard"], 5, smooth=True)
    elif style == 1:   # 고리 두 개
        g.ring((C[0], top, C[1]), 3.1, 0.38, P["guard"], 5, axis="z")
        g.ring((C[0], top, C[1]), 3.1, 0.38, P["guard"], 5, axis="y", tilt=math.pi / 2)
    else:              # 초승달 날개
        for sx in (-1, 1):
            g.tube([(C[0], y1, C[1]), (C[0] + sx * 3.3, top, C[1]), (C[0] + sx * 1.8, top + 3.4, C[1])], [0.6, 0.45, 0.12], P["guard"], 5, smooth=True)
    g.sphere((C[0], y0, C[1]), 0.7, P["guard"], 3)


def bow(g, P, s, tier):
    """활: 세로로 쥔 휜 몸 (위아래 끝이 뒤로 젖혀짐) + 감은 손잡이 + 시위"""
    top, bot = 17.5, -1.5
    bend = 3.2
    pts = [(C[0] - 0.8, bot, C[1]), (C[0] + bend * 0.75, bot + 4.0, C[1]), (C[0] + bend, 8.0, C[1]), (C[0] + bend * 0.75, top - 4.0, C[1]), (C[0] - 0.8, top, C[1])]
    g.tube(pts, [0.35, 0.62, 0.75, 0.62, 0.35], lambda x, y, z, d, f: lit(P["blade"], 1.1 if d > 0.6 else 0.9), 3, smooth=True, squash=(1, 0.8))
    _fill(g, 6.6, 9.4, lambda y: (0.85, 0.75, "round", bend), lambda x, y, z, u, w: P["grip"] if int(y * 2.4) % 2 else lit(P["grip"], 0.7), 5)
    for yy in (bot, top):
        g.sphere((C[0] - 0.8, yy, C[1]), 0.6, P["guard"], 4)
    _fill(g, bot, top, lambda y: (0.16, 0.16, "box", -0.8), lambda *a: lit(P["edge"], 1.1), 2)   # 시위
    for yy in (bot + 2.2, top - 2.2):
        g.dot(C[0] + bend * 0.62, yy, C[1] + 0.5, P["gem"], 6, 0.6)


def shield(g, P, s, tier):
    """방패: 앞(+z)을 보는 볼록한 판 · 두꺼운 테 · 가운데 돌기 · 문장"""
    style = s % 3
    rim, body, emb = P.get("rim", P["guard"]), P["blade"], P["gem"]
    vs = VL.VS

    def inside(x, y):
        u, v = (x - 8) / 6.4, (y - 8.5) / 7.2
        if style == 0:   # 히터 (위가 평평, 아래가 뾰족)
            return abs(u) <= 1 and v <= 1 and (v >= -0.2 or abs(u) <= 1 - (-0.2 - v) / 0.8 * 0.95)
        if style == 1:   # 둥근 방패
            return u * u * 1.2 + v * v <= 1.05
        return abs(u) <= 1 - max(0, -v) * 0.55 and v <= 1 and abs(u) <= 1.0   # 카이트
    cells = {}
    for i in range(math.floor(1.0 / vs), math.floor(15.0 / vs) + 1):
        for j in range(math.floor(0.5 / vs), math.floor(16.5 / vs) + 1):
            x, y = (i + 0.5) * vs, (j + 0.5) * vs
            if inside(x, y):
                cells[(i, j)] = (x, y)
    for (i, j), (x, y) in cells.items():
        edge = any((i + a, j + b) not in cells for a in (-2, -1, 0, 1, 2) for b in (-2, -1, 0, 1, 2) if abs(a) + abs(b) <= 2)
        r2 = ((x - 8) / 7.0) ** 2 + ((y - 8.5) / 8.0) ** 2
        bulge = 1.6 * (1 - min(1, r2))            # 볼록
        front = 8.6 + bulge + (0.5 if edge else 0)
        back = 7.4
        cross = (abs(x - 8) < 0.9 or abs(y - 9.5) < 0.9) and not edge
        for k in range(math.floor(back / vs), math.floor(front / vs) + 1):
            z = (k + 0.5) * vs
            col = rim if edge else (emb if cross and z > front - 0.6 else body)
            g.put(x, y, z, col, 3)
    g.sphere((8, 9.5, 9.6 + 1.2), 1.4, lambda x, y, z, d: lit(emb, 1.3) if d < 0.35 else rim, 5)   # 가운데 돌기
    for (dx, dy) in ((-4.6, 4.6), (4.6, 4.6), (-4.6, -1.6), (4.6, -1.6)):   # 징
        if inside(8 + dx, 8.5 + dy):
            g.dot(8 + dx, 8.5 + dy, 10.6, lit(rim, 1.2), 6, 0.7)


BUILDERS = {"sword": sword, "dagger": dagger, "axe": axe, "hammer": hammer, "club": club, "spear": spear, "staff": staff, "bow": bow, "shield": shield}

# 바닐라 handheld 와 같은 손 위치 · 방패는 generated (판이 앞을 봄)
DISPLAY_HELD = {
    "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
    "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "gui": {"rotation": [0, 0, 0], "scale": [0.92, 0.92, 0.92]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
    "fixed": {"rotation": [0, 180, 0], "scale": [1, 1, 1]},
}
DISPLAY_SHIELD = {   # 바닐라 item/generated 와 같은 위치 (예전 방패 모델과 같게)
    "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
    "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "gui": {"rotation": [0, 0, 0], "scale": [0.92, 0.92, 0.92]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
    "fixed": {"rotation": [0, 180, 0], "scale": [1, 1, 1]},
}


def build(name, img, keys):
    """무기 하나 → (요소 목록, 팔레트 색 목록, 종류). 무기가 아니면 None"""
    kind = kind_of(name)
    if kind is None:
        return None
    P = palette_from(img, keys)
    old = VL.VS
    VL.VS = SIZE_VS
    try:
        g = VL.Grid()
        BUILDERS[kind](g, P, _seed(name), _tier(name))
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
        if kind != "shield":   # 세로로 깎은 무기를 대각선으로 (손잡이 왼쪽 아래 → 끝 오른쪽 위)
            e["rotation"] = {"axis": "z", "angle": -45, "origin": [8, 8, 8]}
        for key in ("from", "to"):
            e[key] = [max(-16, min(32, round(c, 4))) for c in e[key]]
    if len(pal.colors) > 256:
        raise SystemExit("무기 팔레트 색이 256 개를 넘음: " + name)
    return els, list(pal.colors), kind


def model_json(texture, els, kind):
    return {"credit": "RpgCraft weapon voxels", "texture_size": [16, 16], "gui_light": "front",
            "textures": {"0": texture, "particle": texture}, "elements": els,
            "display": DISPLAY_SHIELD if kind == "shield" else DISPLAY_HELD}


def palette_image(colors):
    pal = bm.Palette()
    pal.colors = list(colors)
    return pal.image()
