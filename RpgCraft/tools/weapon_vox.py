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

SIZE_VS = 0.38         # v5.10.26 더 촘촘하게 (0.5 → 0.32, 블록 수 약 2.5배)
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


def mixc(a, b, t):
    ra, ga, ba = _rgb(a)
    rb, gb, bb = _rgb(b)
    t = max(0.0, min(1.0, t))
    return _hex((ra + (rb - ra) * t, ga + (gb - ga) * t, ba + (bb - ba) * t))


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
    if name.startswith(("fish_", "loot_", "mat_", "herb_", "wood_", "ore_", "ing_", "food_", "pet_", "mg_", "ui_")):   # v5.10.38 물고기 이름(무지개 송어=rainbow · 황새치=swordfish)이 무기로 잘못 잡히던 문제
        return None
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

    glow = P.get("glow", P["edge"])
    themed = THEME["name"] is not None

    def col(x, y, z, u, w):
        c0 = col0(x, y, z, u, w)
        if themed and abs(u) <= 0.74:   # v5.10.27 테마 무기: 어두운 속 · 거친 결
            c0 = lit(c0, 0.5 + 0.35 * abs(u) + (0.1 if VL.rnd(int(x / VL.VS), int(y / VL.VS), int(z / VL.VS)) > 0.7 else 0))
        return c0

    def col0(x, y, z, u, w):
        t = (y - y0) / L
        k = 0.42 * t ** 1.4                                    # v5.10.22 끝으로 갈수록 빛나는 그라데이션 (MMORPG 느낌)
        if abs(u) > 0.74 or t > 0.95:
            return mixc(lit(P["edge"], 1.05), lit(glow, 1.4), k)          # 날
        if fuller and abs(u) < 0.24 and 0.04 < t < 0.78:
            rune = int(t * 26) % 3 != 0 and abs(w) > 0.25     # 빛나는 룬 문양
            return lit(P["inlay"], 1.45) if rune else lit(P["inlay"], 0.7)
        if abs(u) < 0.45:
            return mixc(lit(P["blade"], 1.12), glow, k)        # 능선 쪽 광택
        return mixc(P["blade"] if u > 0 else P.get("shade", lit(P["blade"], 0.78)), glow, k * 0.8)
    _fill(g, y0, y1, prof, col, pri)


def _jagged(g, y0, y1, width, thick, P, seed, pri=4, curve=0.0, spikes=1.0):
    """v5.10.23 불꽃 · 가시 칼날 (참고: 들쭉날쭉한 실루엣 · 어두운 속 → 빛나는 가장자리 · 칼날 속을 흐르는 빛줄기)"""
    vs = VL.VS
    L = y1 - y0
    glow = P.get("glow", P["edge"])
    core, body, rim, hot = lit(P["blade"], 0.5), P["blade"], lit(glow, 1.25), lit(glow, 1.7)
    ph1, ph2 = (seed % 97) / 97.0, (seed % 89) / 89.0
    n1, n2 = 3 + seed % 2, 3 + (seed >> 3) % 2

    def saw(v):
        f = v % 1.0
        return f ** 2.2   # 위로 갈수록 길어지다 뚝 끊기는 가시

    for j in range(math.floor(y0 / vs), math.floor(y1 / vs) + 1):
        y = (j + 0.5) * vs
        t = (y - y0) / L
        if t < 0 or t > 1:
            continue
        base = width * (0.5 + 0.35 * math.sin(math.pi * min(1.0, t ** 0.7)))
        if t > 0.7:
            base *= max(0.0, 1 - (t - 0.7) / 0.3) ** 1.1   # 길고 뾰족한 끝
        cx = C[0] + curve * t * t * 3 + 0.5 * math.sin(t * 5.0 + ph1 * 6) * width * 0.12
        xl = cx - base / 2 - spikes * 0.9 * saw(t * n1 + ph1) * (1 - t) * width * 0.4
        xr = cx + base / 2 + spikes * 2.0 * saw(t * n2 + ph2) * (1 - t * 0.5) * width * 0.42   # 한쪽이 더 크게 갈라짐 (비대칭)
        if xr - xl < vs:
            xl, xr = cx - vs / 2, cx + vs / 2
        for i in range(math.floor(xl / vs), math.floor(xr / vs) + 1):
            x = (i + 0.5) * vs
            if x < xl or x > xr:
                continue
            u = (x - cx) / max(1e-6, (xr - xl) / 2)          # -1 ~ 1 (대략)
            dl = min(x - xl, xr - x)                         # 가장자리까지 거리
            th = max(vs * 0.5, min(thick / 2 * (1 - 0.3 * t), dl * 0.55 + vs * 0.3))
            vein = abs(x - (cx + 0.7 * math.sin(y * 1.6 + ph2 * 9) * width * 0.18)) < 0.32 and t < 0.86
            for k in range(math.floor((C[1] - th) / vs), math.floor((C[1] + th) / vs) + 1):
                z = (k + 0.5) * vs
                surf = abs(z - C[1]) > th - vs * 1.1
                if dl < 0.55:
                    col = hot if t > 0.55 or dl < 0.3 else rim                 # 빛나는 가장자리
                elif vein and surf:
                    col = mixc(rim, hot, t)                                     # 빛줄기
                else:
                    k2 = min(1.0, dl / (width * 0.45))
                    col = body if k2 < 0.35 else mixc(body, core, 0.45) if k2 < 0.7 else core   # 가운데가 어두운 속살 (3단계)
                g.put(x, y, z, col, pri)


def _flames(g, base, n, size, P, seed, up=1.0, spread=1.0, pri=6):
    """손잡이 · 가드에서 피어오르는 불꽃 조각 (가운데 하얗게 · 끝은 어둡게)"""
    glow = P.get("glow", P["gem"])
    cols = [lit(glow, 1.9), lit(glow, 1.45), lit(glow, 1.1), glow, lit(glow, 0.7)]
    for k in range(n):
        a = (seed * 0.37 + k * 2.39) % (2 * math.pi)
        dx = math.cos(a) * spread
        x, y, z = base[0] + dx * 0.6, base[1], base[2] + math.sin(a) * 0.4
        steps = 5 + (seed + k) % 3
        for st in range(steps):
            f = st / steps
            r = size * (1 - f * 0.75)
            x += dx * 0.55 + (VL.rnd(seed, k, st) - 0.5) * 0.8
            y += up * (0.75 + 0.4 * VL.rnd(k, st, seed))
            g.box((x - r, y - r, z - r * 0.6), (x + r, y + r, z + r * 0.6), cols[min(len(cols) - 1, int(f * len(cols)))], pri)


def _shards(g, pts, col, size=0.8, pri=7):
    """공중에 떠 있는 작은 결정 조각 (마름모) — v5.10.25 깔끔하게: 쓰지 않음"""
    return
    for (x, y, z) in pts:
        h = size
        _fill(g, y - h * 1.6, y + h * 1.6, lambda yy, y=y, h=h, x=x: (h * (1 - abs(yy - y) / (h * 1.6)), h * 0.6 * (1 - abs(yy - y) / (h * 1.6)), "diamond", x - C[0]),
              lambda xx, yy, zz, u, w, y=y: lit(col, 1.5) if yy > y and abs(u) < 0.5 else lit(col, 1.15), pri)


def _wings(g, y, half, P, pri=5):
    """가드 위로 뻗는 날개 장식 (겹겹이)"""
    glow = P.get("glow", P["gem"])
    for sd in (-1, 1):
        _bar(g, C[0] + sd * 1.2, C[0] + sd * half * 1.15, lambda t: y + 0.6 + 3.2 * t ** 1.6, lambda t: 0.55 - 0.3 * t, lambda t: 0.35,
             lambda x, yy, z, t, v, w: mixc(P["guard"], lit(glow, 1.3), t ** 1.5), pri)
        _bar(g, C[0] + sd * 1.0, C[0] + sd * half * 0.8, lambda t: y - 0.2 + 1.6 * t ** 1.4, lambda t: 0.45 - 0.2 * t, lambda t: 0.45,
             lambda x, yy, z, t, v, w: lit(P["guard"], 0.85), pri)


def _cone_y(g, y0, y1, r0, r1, colf, pri=3, shape="round"):
    _fill(g, y0, y1, lambda y: (r0 + (r1 - r0) * (y - y0) / max(1e-6, y1 - y0),) * 2 + (shape,), colf, pri)


# v5.10.24 불꽃 · 가시 칼날은 특별한 무기에만 (전부 불꽃이면 단조로움): 사신수 · 유물 · 붕붕이 · 각 계열 최상위 등급
FIERY = {"jag": False, "flame": False}


def _fiery(name, tier):
    return False, False   # v5.10.25 불꽃 · 가시 칼날 없이 모두 깔끔한 칼날
    special = name.startswith(("spirit_", "relic_", "bungbung_"))
    top = (name.startswith(("armory_", "armory2_", "armory3_")) and tier >= 7) or (name.startswith(("trans_", "bs_")) and tier >= 5)
    return special or top, special or (top and tier >= 8)


# ------------------------------------------------------------------ v5.10.27 속성 테마 장식 (참고: 마크에이지 4R 무기)
ANCH = {}
THEME = {"name": None}

NAME_THEME = {"witch": "abyss", "harpy": "frost", "sea": "frost", "bungbung": "inferno", "dwarf": "dragon", "pender": "nature",
              "spirit_qinglong": "dragon", "spirit_baihu": "holy", "spirit_zhuque": "inferno", "spirit_xuanwu": "nature",
              "relic": "abyss", "trans": "holy"}


def theme_of(name, P, tier):
    for k, v in NAME_THEME.items():
        if name.startswith(k):
            return v
    if not name.startswith(("armory", "bs_")) or tier < 3:
        return None   # 기본 무기 · 낮은 등급은 깔끔하게
    r, g, b = (c / 255 for c in _rgb(P.get("glow", P["gem"])))
    h, l, sat = colorsys.rgb_to_hls(r, g, b)
    if sat < 0.25:
        return None
    deg = h * 360
    return "inferno" if deg < 35 or deg >= 335 else "holy" if deg < 70 else "nature" if deg < 160 else "frost" if deg < 250 else "abyss"


def _spike(g, base, tip, r0, col0, col1, pri=6):
    """납작한 가시 (뿌리 어둡게 → 끝 밝게)"""
    g.tube([base, tip], [r0, 0.08], lambda x, y, z, d, f: mixc(col0, col1, f), pri, squash=(1, 0.45))


def decorate(g, P, s, tier):
    th, a = THEME["name"], ANCH
    if not th or not a:
        return
    gy, top, w = a["gy"], a["top"], a["w"]
    glow = P.get("glow", P["gem"])
    dark = lit(P["blade"], 0.45)
    L = top - gy
    if th == "abyss":      # 칼등을 따라 돋은 가시 · 옆으로 휜 갈고리 날 · 붉은 눈
        n = 6 + min(3, tier)
        for k in range(n):
            t = 0.12 + 0.8 * k / n
            y = gy + L * t
            out = w * 0.5 * (1 - 0.6 * t) + 0.2
            _spike(g, (C[0] + out - 0.3, y, C[1]), (C[0] + out + 1.6 - t, y + 1.3, C[1]), 0.55, dark, lit(glow, 1.2))
        hook = [(C[0] - w * 0.4, gy + L * 0.22, C[1]), (C[0] - w * 1.25, gy + L * 0.3, C[1]), (C[0] - w * 1.5, gy + L * 0.48, C[1]), (C[0] - w * 1.05, gy + L * 0.6, C[1])]
        g.tube(hook, [0.9, 0.75, 0.45, 0.12], lambda x, y, z, d, f: mixc(dark, glow, f * 0.8) if d < 0.75 else lit(glow, 1.25), 6, smooth=True, squash=(1, 0.45))
        eye = (C[0], gy + 1.0, C[1])
        for sz in (-1, 1):
            g.sphere((eye[0], eye[1], eye[2] + sz * 0.95), 0.95, lambda x, y, z, d: "ffffff" if d < 0.15 else "ff3030" if d < 0.6 else "8a0a14", 9)
    elif th == "nature":   # 칼날을 감은 덩굴 · 잎 · 교차한 가지
        leaf, leaf2, bark = "5aa83a", "8fd04a", "5a3a22"
        for strand in (0, math.pi):
            pts = []
            for k in range(14):
                t = k / 13
                ang = strand + t * math.pi * 3.2
                rr = w * 0.5 * (1 - 0.55 * t) + 0.35
                pts.append((C[0] + math.cos(ang) * rr, gy + 0.5 + L * 0.85 * t, C[1] + math.sin(ang) * 0.9))
            g.tube(pts, [0.42] * 7 + [0.3] * 7, lambda x, y, z, d, f: bark if d > 0.6 else lit(bark, 0.75), 6, smooth=True)
            for k in range(2, 13, 3):
                p = pts[k]
                g.ellipsoid(p, (1.0, 0.55, 0.35), lambda x, y, z, d: leaf2 if d < 0.4 else leaf, 7)
        for yy in (gy - 0.4, top - L * 0.18):
            for sx in (-1, 1):
                g.tube([(C[0] - sx * 2.6, yy - 1.6, C[1]), (C[0] + sx * 2.6, yy + 1.6, C[1])], [0.4, 0.25], bark, 7)
            g.ellipsoid((C[0] + 2.2, yy + 1.8, C[1]), (1.2, 0.6, 0.4), leaf2, 8)
    elif th == "frost":    # 가드에서 휘감아 오르는 영혼 기운 · 끝의 얼음 결정
        light = lit(glow, 1.45)
        for k in range(4):
            sd = -1 if k % 2 == 0 else 1
            y0 = gy + 0.5 + k * L * 0.18
            pts = [(C[0] + sd * w * 0.4, y0, C[1]), (C[0] + sd * (w * 0.9 + 1.0), y0 + 1.2, C[1] + 0.4), (C[0] + sd * (w * 0.75 + 1.6), y0 + 2.8, C[1]),
                   (C[0] + sd * (w * 0.5 + 0.6), y0 + 3.6, C[1] - 0.3)]
            g.tube(pts, [0.6, 0.5, 0.35, 0.12], lambda x, y, z, d, f: mixc(glow, light, f), 6, smooth=True, squash=(1, 0.6))
        _fill(g, top - 1.5, top + 3.0, lambda y: (1.1 * (1 - abs(y - top - 0.6) / 2.4), 0.7 * (1 - abs(y - top - 0.6) / 2.4), "diamond"), lambda *q: light, 6)
    elif th == "inferno":  # 가드 양쪽에서만 작은 불꽃 (칼날은 깔끔하게)
        _flames(g, (C[0] - w * 0.8, gy, C[1]), 3, 0.5, P, s, up=1.0, spread=-1.0)
        _flames(g, (C[0] + w * 0.8, gy, C[1]), 2, 0.45, P, s + 3, up=1.0, spread=1.0)
    elif th == "dragon":   # 한쪽으로 펼친 막 날개 · 빛나는 눈 · 폼멜의 뿔
        wing_base = (C[0] - w * 0.4, gy + 0.6, C[1])
        tips = [(C[0] - w * 2.4 - 2.2, gy + L * 0.62, C[1]), (C[0] - w * 2.3 - 2.6, gy + L * 0.38, C[1]), (C[0] - w * 1.9 - 2.4, gy + L * 0.15, C[1]), (C[0] - w * 1.3 - 1.6, gy - 0.8, C[1])]
        for tp in tips:
            g.tube([wing_base, tp], [0.4, 0.12], lit(P["guard"], 0.8), 6)
        for i2 in range(len(tips) - 1):
            g.triangle(wing_base, tips[i2], tips[i2 + 1], lambda p, wgt: mixc(glow, lit(glow, 0.55), wgt), 5, scallop=0.25, thick=0.3)
        for sz in (-1, 1):
            g.sphere((C[0], gy + 1.0, C[1] + sz * 0.95), 0.85, lambda x, y, z, d: "ffffff" if d < 0.2 else lit(glow, 1.3), 9)
        pom = a.get("pom", gy - 4)
        for sx in (-1, 1):
            g.cone((C[0] + sx * 0.5, pom, C[1]), (C[0] + sx * 1.8, pom - 1.6, C[1]), 0.45, 0.1, lit(P["guard"], 1.2), 6)
    elif th == "holy":     # 칼날 받침의 빛 고리 · 끝의 별
        g.ring((C[0], gy + 2.6, C[1]), w * 0.75 + 0.6, 0.25, lit(glow, 1.4), 6, axis="y", tilt=0.0)
        ty = top + 1.2
        for ang in range(0, 360, 45):
            rr = 1.6 if ang % 90 == 0 else 0.9
            g.tube([(C[0], ty, C[1]), (C[0] + math.cos(math.radians(ang)) * rr, ty + math.sin(math.radians(ang)) * rr, C[1])], [0.3, 0.08], lit(glow, 1.5), 7)


# ------------------------------------------------------------------ 무기 종류별
def sword(g, P, s, tier):
    total = 21.5 + min(4, tier) * 0.4
    y0 = 8 - total / 2
    gl = 3.4
    _pommel(g, y0 + 0.9, P, s % 3)
    _grip(g, y0 + 1.6, y0 + 1.6 + gl, 0.75, P)
    gy = y0 + 1.6 + gl + 0.7
    width = 3.7 + (s >> 3) % 3 * 0.4 + (0.5 if tier >= 5 else 0)
    _guard(g, gy, width * 1.25 + 0.9, P, (s >> 5) % 4)
    if ((s >> 11) % 3 != 0 or tier >= 4) and not FIERY["jag"]:   # 날개 장식 (매끈한 칼날만)
        _wings(g, gy, width * 1.1 + 0.8, P)
    _fill(g, gy + 0.9, gy + 2.0, lambda y: (width * 0.42, 0.95, "box"), lambda x, y, z, u, w: P["guard"] if abs(u) > 0.5 else lit(P["guard"], 1.2), 6)   # 칼날 받침 (리카소)
    g.dot(C[0], gy + 1.45, C[1] + 0.9, lit(P["gem"], 1.3), 8, 0.8)
    g.dot(C[0], gy + 1.45, C[1] - 0.9, lit(P["gem"], 1.3), 8, 0.8)
    ANCH.update(kind="blade", gy=gy, top=y0 + total, w=width, pom=y0 + 0.9)
    jag = FIERY["jag"]
    if jag:   # v5.10.23 불꽃 · 가시 칼날 + 가드에서 피어오르는 불꽃
        _jagged(g, gy + 1.6, y0 + total + 2.5, width * 1.15, 1.9, P, s, curve=[0, 0.2, -0.15][s % 3], spikes=0.7 + min(4, tier) * 0.15)
        if FIERY["flame"]:
            _flames(g, (C[0] - width * 0.9, gy, C[1]), 3 + min(3, tier), 0.55, P, s, up=1.0, spread=-1.2)
        if FIERY["flame"] and tier >= 3:
            _flames(g, (C[0], y0 + 0.4, C[1]), 3, 0.45, P, s + 7, up=-1.0, spread=0.6)   # 폼멜 아래 불꽃 꼬리
    else:
        _blade(g, gy + 1.9, y0 + total, width, 1.7, P, (s >> 7) % 4)
    if not jag and (tier >= 3 or (s >> 9) % 3 == 0):   # 칼날 옆에 떠 있는 결정 조각
        top = y0 + total
        _shards(g, [(C[0] - width * 0.5 - 1.6, top - 5.5, C[1]), (C[0] + width * 0.5 + 1.6, top - 8.5, C[1])], P.get("glow", P["gem"]), 0.75)


def dagger(g, P, s, tier):
    total = 15.5 + min(4, tier) * 0.3
    y0 = 8 - total / 2 - 1.5
    _pommel(g, y0 + 0.8, P, s % 3)
    _grip(g, y0 + 1.4, y0 + 4.2, 0.68, P)
    gy = y0 + 4.8
    _guard(g, gy, 2.4, P, (s >> 4) % 4)
    curve = [0.0, 0.25, -0.25, 0.4][(s >> 6) % 4]
    ANCH.update(kind="blade", gy=gy, top=y0 + total, w=3.0, pom=y0 + 0.8)
    if FIERY["jag"]:
        _jagged(g, gy + 0.7, y0 + total + 1.5, 3.2, 1.5, P, s, curve=curve, spikes=0.8)
        if FIERY["flame"]:
            _flames(g, (C[0] - 2.2, gy, C[1]), 3, 0.45, P, s, up=1.0, spread=-1.0)
    else:
        _blade(g, gy + 0.9, y0 + total, 3.0, 1.35, P, [0, 1, 0, 1][(s >> 8) % 4], curve=curve)


def axe(g, P, s, tier):
    y0, y1 = -2.5, 18.0
    _fill(g, y0, y1 - 0.5, lambda y: (0.6, 0.6, "round"), lambda x, y, z, u, w: lit(P["grip"], 0.85 + 0.15 * ((int(y * 1.5)) % 2)), 2)
    _grip(g, y0 + 0.4, y0 + 4.6, 0.72, P)
    g.sphere((C[0], y0, C[1]), 0.85, P["guard"], 4)
    hy = y1 - 3.6                       # 머리 가운데
    ANCH.update(kind="axe", gy=hy, top=y1, w=4.0, pom=y0)
    double = (s >> 3) % 3 == 0
    size = 5.4 + min(4, tier) * 0.3
    glow = P.get("glow", P["edge"])
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
                rune = 0.3 < f < 0.7 and abs(v) < 0.35 and int(f * 18) % 2 == 0
                col = mixc(P["edge"], lit(glow, 1.4), 0.35 * f) if edge else lit(P["inlay"], 1.45) if rune else mixc(lit(P["blade"], 1.05 - 0.2 * f), glow, 0.3 * f * f)
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
    """v5.10.41 갈색 몽둥이: 가시 · 쇠붙이 없이 투박한 나무 막대기 — 살짝 휜 굵은 가지 · 거친 나무껍질 · 옹이 · 잘린 잔가지 · 닳은 끝 · 천으로 감은 손잡이"""
    wood = P["blade"]
    bark, bark_d, inner = lit(wood, 0.82), lit(wood, 0.6), lit(wood, 1.25)
    y0, y1 = -2.2, 15.4
    bend = lambda y: 0.55 * math.sin((y - y0) / (y1 - y0) * math.pi)   # 살짝 휜 가지

    def radius(y):
        t = (y - y0) / (y1 - y0)
        return 0.8 + 1.15 * t ** 1.3 + 0.12 * math.sin(y * 1.7 + (s % 5))   # 위로 갈수록 굵고 울퉁불퉁

    def col(x, y, z, u, w):
        ang = math.atan2(w, u)
        groove = math.sin(ang * 9 + y * 0.25 + (s % 7)) > 0.55             # 세로로 갈라진 나무껍질 홈
        k = 0.92 + 0.12 * (VL.rnd(int(y * 2.5), int(ang * 6)) - 0.5)
        return bark_d if groove else lit(bark, k)
    _fill(g, y0, y1, lambda y: (radius(y), radius(y) * 0.95, "round", bend(y)), col, 2)
    top = (C[0] + bend(y1), y1, C[1])
    g.sphere(top, radius(y1) * 0.9, lambda x, y, z, d: inner if y > y1 + 0.25 else bark, 2)   # 닳아서 속살이 보이는 끝
    for k in range(3):   # 끝의 나이테
        g.dot(top[0] + (k - 1) * 0.5, y1 + radius(y1) * 0.85, top[2] + 0.2 * k, lit(wood, 1.05 - 0.12 * k), 3, 0.35)
    for (yy, a) in ((5.4, 0.9), (9.8, 3.8), (12.6, 2.2)):   # 옹이 (튀어나온 둥근 혹 + 어두운 가운데)
        r = radius(yy)
        cx, cz = C[0] + bend(yy) + math.cos(a) * r, C[1] + math.sin(a) * r
        g.sphere((cx, yy, cz), 0.62, lambda *q: bark, 3)
        g.dot(cx + math.cos(a) * 0.35, yy, cz + math.sin(a) * 0.35, bark_d, 4, 0.32)
    for (yy, a, ln) in ((7.6, 2.6, 1.8), (11.2, 0.4, 1.3)):   # 잘린 잔가지
        r = radius(yy)
        base = (C[0] + bend(yy) + math.cos(a) * (r - 0.2), yy, C[1] + math.sin(a) * (r - 0.2))
        tip = (base[0] + math.cos(a) * ln, yy + ln * 0.7, base[2] + math.sin(a) * ln)
        g.cone(base, tip, 0.45, 0.32, lambda *q: bark, 3)
        g.dot(*tip, inner, 4, 0.3)   # 잘린 면
    # 손잡이: 거친 천을 감고 끈으로 묶음
    def wrap(x, y, z, u, w):
        return lit(P["grip"], 0.75) if int((y * 2.0 + math.atan2(w, u) * 0.5) % 2) else P["grip"]
    _fill(g, y0, 3.2, lambda y: (radius(y) + 0.22, radius(y) * 0.95 + 0.22, "round", bend(y)), wrap, 3)
    for yy in (y0 + 0.3, 3.0):
        _fill(g, yy - 0.25, yy + 0.25, lambda y: (radius(y) + 0.38, radius(y) * 0.95 + 0.38, "round", bend(y)), lambda *q: lit(P["grip"], 0.55), 4)
    g.tube([(C[0] - 0.7, 3.0, C[1] + 0.5), (C[0] - 1.6, 1.8, C[1] + 0.9), (C[0] - 1.3, 0.6, C[1] + 0.6)], [0.18, 0.16, 0.12], lit(P["grip"], 0.55), 4, smooth=True)   # 늘어진 끈 끝


def spear(g, P, s, tier):
    y0, y1 = -3.5, 13.5
    _fill(g, y0, y1, lambda y: (0.62, 0.62, "round"), lambda x, y, z, u, w: lit(P["grip"], 0.9 + 0.12 * (int(y * 0.9) % 2)), 2)
    for yy in (y0 + 3.0, y0 + 9.0, y1 - 0.5):   # 쇠고리
        _fill(g, yy - 0.35, yy + 0.35, lambda y: (0.78, 0.78, "round"), lambda *a: P["guard"], 3)
    g.sphere((C[0], y0, C[1]), 0.75, P["guard"], 3)
    _fill(g, y1 - 0.6, y1 + 0.8, lambda y: (1.1, 1.1, "box"), lambda *a: P["guard"], 4)   # 날 받침
    ANCH.update(kind="blade", gy=y1, top=y1 + 9.0, w=3.8, pom=y0)
    if FIERY["jag"]:
        _jagged(g, y1 + 0.4, y1 + 9.5, 4.0, 1.6, P, s, spikes=0.8)
        if FIERY["flame"]:
            _flames(g, (C[0], y1 + 0.2, C[1]), 3, 0.4, P, s, up=-1.0, spread=0.9)
    else:
        _blade(g, y1 + 0.6, y1 + 9.0, 3.8, 1.5, P, 1 if (s >> 3) % 2 else 0, fuller=True)
    if (s >> 5) % 2:   # 갈고리 날개
        for sx in (-1, 1):
            g.cone((C[0], y1 + 0.8, C[1]), (C[0] + sx * 3.0, y1 + 2.4, C[1]), 0.7, 0.12, P["edge"], 4)
    if tier >= 3:
        _shards(g, [(C[0] - 2.6, y1 + 6.0, C[1]), (C[0] + 2.6, y1 + 4.0, C[1])], P.get("glow", P["gem"]), 0.55)
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
    ANCH.update(kind="staff", gy=y1, top=top, w=3.0, pom=y0)
    _fill(g, top - 3.0, top + 3.4, lambda y: (1.9 * (1 - abs(y - top - 0.2) / 3.2), 1.9 * (1 - abs(y - top - 0.2) / 3.2), "diamond"),
          lambda x, y, z, u, w: lit(P["gem"], 1.55) if abs(u) < 0.35 and y > top else lit(P["gem"], 1.2) if u > 0 else P["gem"], 4)   # v5.10.22 길쭉한 마력 결정
    _shards(g, [(C[0] - 3.4, top + 1.6, C[1]), (C[0] + 3.4, top - 0.6, C[1]), (C[0] - 0.4, top + 4.6, C[1])], P.get("glow", P["gem"]), 0.6)
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
    """활: 마디진 덩굴 같은 휜 몸 (나무 마디 · 잎 · 가시) + 감은 손잡이 + 가는 시위 (참고 이미지 느낌)"""
    top, bot = 18.0, -2.0
    bend = 3.4
    wood, leaf = P["blade"], P.get("glow", P["gem"])
    pts = [(C[0] - 0.9, bot, C[1]), (C[0] + bend * 0.8, bot + 4.5, C[1]), (C[0] + bend, 8.0, C[1]), (C[0] + bend * 0.8, top - 4.5, C[1]), (C[0] - 0.9, top, C[1])]
    path = VL.catmull(pts, 10)
    for i2, p in enumerate(path):   # 마디마다 굵기 · 위치가 조금씩 어긋나는 나무 몸
        if i2 % 2:
            continue
        jx = (VL.rnd(s, i2) - 0.5) * 0.7
        r0 = 0.5 + 0.25 * (1 - abs(p[1] - 8) / 10)
        g.box((p[0] - r0 + jx, p[1] - 0.45, p[2] - r0 * 0.8), (p[0] + r0 + jx, p[1] + 0.45, p[2] + r0 * 0.8),
              lit(wood, 0.85 + 0.3 * VL.rnd(i2, s)), 3)
        if i2 % 6 == 0 and abs(p[1] - 8) > 2:   # 잎 · 가시
            side = 1 if (i2 // 6) % 2 else -1
            for k in range(3):
                g.box((p[0] + side * (0.6 + k * 0.5), p[1] + k * 0.45, p[2] - 0.5 + k * 0.1), (p[0] + side * (1.2 + k * 0.5), p[1] + 0.5 + k * 0.45, p[2] + 0.5),
                      [lit(leaf, 0.75), leaf, lit(leaf, 1.3)][k], 4)
    _fill(g, 6.4, 9.6, lambda y: (0.85, 0.75, "round", bend), lambda x, y, z, u, w: P["grip"] if int(y * 2.4) % 2 else lit(P["grip"], 0.7), 5)
    for yy in (bot, top):
        g.box((C[0] - 1.5, yy - 0.5, C[1] - 0.5), (C[0] - 0.3, yy + 0.5, C[1] + 0.5), lit(P["guard"], 1.1), 4)
    _fill(g, bot, top, lambda y: (0.14, 0.14, "box", -0.9), lambda *a: lit(P["edge"], 1.15), 2)   # 시위


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
SHIELD_SCALE = 1.4   # 판 14 x 16 → 약 20 x 22 (바닐라 방패 12 x 22 와 비슷한 실제 크기)
# 바닐라 shield.json / shield_blocking.json 과 같은 손 위치 (판이 몸 옆에서 바깥을 봄)
DISPLAY_SHIELD = {
    "thirdperson_righthand": {"rotation": [0, 90, 0], "translation": [10, 6, -4], "scale": [1, 1, 1]},
    "thirdperson_lefthand": {"rotation": [0, 90, 0], "translation": [10, 6, 12], "scale": [1, 1, 1]},
    "firstperson_righthand": {"rotation": [0, 180, 5], "translation": [-10, 2, -10], "scale": [1.25, 1.25, 1.25]},
    "firstperson_lefthand": {"rotation": [0, 180, 5], "translation": [10, 0, -10], "scale": [1.25, 1.25, 1.25]},
    "gui": {"rotation": [15, -25, -5], "translation": [2, 3, 0], "scale": [0.65, 0.65, 0.65]},
    "fixed": {"rotation": [0, 180, 0], "translation": [-4.5, 4.5, -5], "scale": [0.55, 0.55, 0.55]},
    "ground": {"rotation": [0, 0, 0], "translation": [2, 4, 2], "scale": [0.25, 0.25, 0.25]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
}
DISPLAY_SHIELD_BLOCKING = {
    "thirdperson_righthand": {"rotation": [45, 135, 0], "translation": [3.51, 11, -2], "scale": [1, 1, 1]},
    "thirdperson_lefthand": {"rotation": [45, 135, 0], "translation": [13.51, 3, 5], "scale": [1, 1, 1]},
    "firstperson_righthand": {"rotation": [0, 180, -5], "translation": [-15, 5, -11], "scale": [1.25, 1.25, 1.25]},
    "firstperson_lefthand": {"rotation": [0, 180, -5], "translation": [5, 5, -11], "scale": [1.25, 1.25, 1.25]},
    "gui": {"rotation": [15, -25, -5], "translation": [2, 3, 0], "scale": [0.65, 0.65, 0.65]},
}
# 바닐라 bow.json 과 같은 손 위치
DISPLAY_BOW = dict(DISPLAY_HELD, **{
    # v5.10.53 모델을 -45° 로 세워 둔 만큼 3인칭에서는 z 를 +90° (게임은 z 회전을 먼저 적용) → 바닐라 활처럼 세로로 듦 (가로로 들던 문제)
    "thirdperson_righthand": {"rotation": [-80, 260, 50], "translation": [-1, -2, 2.5], "scale": [0.9, 0.9, 0.9]},
    "thirdperson_lefthand": {"rotation": [-80, -280, 130], "translation": [-1, -2, 2.5], "scale": [0.9, 0.9, 0.9]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
})


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
        FIERY["jag"], FIERY["flame"] = _fiery(name, _tier(name))
        ANCH.clear()
        THEME["name"] = theme_of(name, P, _tier(name)) if kind in ("sword", "dagger", "spear", "axe", "staff") else None
        BUILDERS[kind](g, P, _seed(name), _tier(name))
        if kind in ("sword", "dagger", "spear"):
            decorate(g, P, _seed(name), _tier(name))
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
        if kind == "bow":      # v5.10.40 손에서 90도 돌려 세워 들고 시위를 잡은 것처럼 (v5.10.34 의 +45 → -45)
            e["rotation"] = {"axis": "z", "angle": -45, "origin": [8, 8, 8]}
        elif kind != "shield":   # 세로로 깎은 무기를 대각선으로 (손잡이 왼쪽 아래 → 끝 오른쪽 위)
            e["rotation"] = {"axis": "z", "angle": -45, "origin": [8, 8, 8]}
        if kind == "shield":   # v5.10.34 바닐라 방패와 같은 자리 · 크기 (판 가운데 = 원점, 앞면 +z) → 실제 방패 크기로 옆에 듦
            for key in ("from", "to"):
                c = e[key]
                e[key] = [(c[0] - 8) * SHIELD_SCALE, (c[1] - 8.5) * SHIELD_SCALE, (c[2] - 7.4) * SHIELD_SCALE + 1.0]
        for key in ("from", "to"):
            e[key] = [max(-16, min(32, round(c, 4))) for c in e[key]]
    if len(pal.colors) > 256:
        raise SystemExit("무기 팔레트 색이 256 개를 넘음: " + name)
    return els, list(pal.colors), kind


def model_json(texture, els, kind):
    return {"credit": "RpgCraft weapon voxels", "texture_size": [16, 16], "gui_light": "front",
            "textures": {"0": texture, "particle": texture}, "elements": els,
            "display": DISPLAY_SHIELD if kind == "shield" else DISPLAY_BOW if kind == "bow" else DISPLAY_HELD}


def palette_image(colors):
    pal = bm.Palette()
    pal.colors = list(colors)
    return pal.image()
