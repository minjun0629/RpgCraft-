"""
스킬 이펙트용 모델 (잔상 없는 순간 이펙트)
플레이어가 스킬을 쓰면 ItemDisplay 로 아래 모델을 한 번 번쩍 띄웠다가 몇 틱 뒤 지운다.
각 모델은 두 겹: 색이 입혀지는 발광 판(tintindex 0 → 가죽 말 갑옷 염색 색) + 흰 중심 판.
  9500 초승달 참격 · 9501 충격파 고리 · 9502 광선 · 9503 별 폭발
  9504 반짝임 · 9505 충격파 원판 · 9506 참격 잔상 (v5.1.3 겹 이펙트)
"""
import math
import os
from PIL import Image

S = 128  # 텍스처 해상도


def _save(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def _field(fn):
    """fn(x,y in -1..1) -> (glow alpha 0..1, core alpha 0..1)"""
    glow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    core = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    for py in range(S):
        for px in range(S):
            x = (px + 0.5) / S * 2 - 1
            y = (py + 0.5) / S * 2 - 1
            g, c = fn(x, y)
            if g > 0.01:
                glow.putpixel((px, py), (255, 255, 255, int(min(1, g) * 255)))
            if c > 0.01:
                core.putpixel((px, py), (255, 255, 255, int(min(1, c) * 255)))
    return glow, core


def _hash(i, j):
    """결정적 의사난수 0..1 (반짝이 위치용)"""
    h = (i * 374761393 + j * 668265263) & 0xFFFFFFFF
    h = (h ^ (h >> 13)) * 1274126177 & 0xFFFFFFFF
    return (h & 0xFFFF) / 65535


def _crescent(x, y):
    # v5.1.3: 날카로운 주 칼날 + 안쪽에 겹친 가는 바람결 2줄 + 부드러운 바깥 광휘 + 칼끝 반짝임
    r = math.hypot(x, y + 0.55)
    ang = math.atan2(x, y + 0.55)            # 0 = 정면
    span = 1.3
    if abs(ang) > span:
        return 0, 0
    t = 1 - abs(ang) / span                  # 가운데 1, 끝 0
    width = 0.02 + 0.12 * t ** 0.75
    d = r - 1.1
    dd = d / (width * (1.0 if d > 0 else 2.6))
    blade = max(0, 1 - abs(dd) / 1.3) ** 1.1 * (0.35 + 0.65 * t)
    halo = max(0, 1 - abs(d) / (width * 4.5)) ** 2 * 0.35 * t          # 넓게 번지는 빛
    streak = 0
    for (off, w, k) in ((-0.13, 0.018, 0.55), (-0.24, 0.012, 0.35)):     # 안쪽 바람결
        streak = max(streak, max(0, 1 - abs(r - 1.1 - off) / w) * k * max(0, t - 0.25) / 0.75)
    core = max(0, 1 - abs(d) / (width * 0.32)) * t ** 0.45
    tip = max(0, 1 - math.hypot(abs(ang) - span * 0.92, (r - 1.1) * 3) / 0.12) * 0.8   # 칼끝 반짝
    return max(blade, halo, streak, tip), max(core, tip * 0.8)


def _trail(x, y):
    # 참격 잔상: 더 넓고 흐린 초승달 (주 칼날 뒤로 한 틱 늦게 남는 여운)
    r = math.hypot(x, y + 0.5)
    ang = math.atan2(x, y + 0.5)
    span = 1.35
    if abs(ang) > span:
        return 0, 0
    t = 1 - abs(ang) / span
    d = r - 1.02
    width = 0.05 + 0.2 * t
    g = max(0, 1 - abs(d) / width) ** 1.6 * t ** 0.7 * 0.75
    # 결 무늬 (칼바람)
    g *= 0.75 + 0.25 * math.sin(r * 60)
    return g, 0


def _ring(x, y):
    # 두 겹 고리 + 룬 눈금 12개 + 안쪽 은은한 파동
    r = math.hypot(x, y)
    a = math.atan2(y, x)
    d1 = abs(r - 0.82)
    d2 = abs(r - 0.66)
    main = max(0, 1 - d1 / 0.15) ** 1.2 * 0.95
    inner = max(0, 1 - d2 / 0.03) * 0.55
    tick = 0
    if 0.70 < r < 0.78:
        seg = (a / (2 * math.pi) * 12) % 1
        tick = max(0, 1 - abs(seg - 0.5) / 0.08) * 0.8
    wave = max(0, 1 - r / 0.82) ** 3 * 0.18 if r < 0.82 else 0
    core = max(0, 1 - d1 / 0.03)
    return max(main, inner, tick, wave), core


def _beam(x, y):
    # 광선: 중심 흰 선 + 색 외피 + 가장자리 결 무늬(흐르는 느낌)
    t = 1 - abs(x) ** 3
    d = abs(y)
    outer = max(0, 1 - d / (0.24 * t + 0.02)) * t
    band = (0.8 + 0.2 * math.sin(x * 28)) if d > 0.08 * t else 1.0
    edge = max(0, 1 - abs(d - 0.17 * t) / 0.02) * 0.6 * t
    return max(outer * band, edge), max(0, 1 - d / (0.05 * t + 0.005)) * t


def _burst(x, y):
    # 8갈래 큰 별 + 4갈래 긴 섬광 + 흩날리는 반짝이
    r = math.hypot(x, y)
    a = math.atan2(y, x)
    spikes = abs(math.cos(a * 4)) ** 6 * 0.7 + 0.2
    lim = 0.9 * spikes
    g = max(0, 1 - r / lim) ** 1.3
    flare = max(0, 1 - r / 0.98) * max(0, abs(math.cos(a * 2)) ** 40) * 0.9      # 십자 섬광
    c = max(0, 1 - r / (lim * 0.45)) ** 1.5
    spark = 0
    px, py = int((x + 1) * 12), int((y + 1) * 12)
    if _hash(px, py) > 0.93 and 0.35 < r < 0.95:
        cx, cy = (px + 0.5) / 12 - 1, (py + 0.5) / 12 - 1
        spark = max(0, 1 - math.hypot(x - cx, y - cy) / 0.035)
    return max(g, flare, spark), max(c, spark)


def _spark(x, y):
    # 반짝임: 4갈래 가늘고 긴 별 (칼끝 · 타격점에 잠깐)
    r = math.hypot(x, y)
    arm = max(0, 1 - abs(x) / 0.05) * max(0, 1 - abs(y)) + max(0, 1 - abs(y) / 0.05) * max(0, 1 - abs(x))
    arm2 = (max(0, 1 - abs(x - y) / 0.06) + max(0, 1 - abs(x + y) / 0.06)) * max(0, 1 - r / 0.5) * 0.5
    glow = max(0, 1 - r / 0.35) ** 2
    return min(1, arm + arm2 + glow), max(0, 1 - r / 0.12)


def _wave(x, y):
    # 충격파 원판: 가장자리가 밝고 안쪽으로 옅어지는 파동
    r = math.hypot(x, y)
    if r > 1:
        return 0, 0
    g = r ** 3 * max(0, 1 - (r - 0.9) / 0.1 if r > 0.9 else 1) * 0.8
    return g, 0


SHAPES = {9500: ("slash", _crescent), 9501: ("ring", _ring), 9502: ("beam", _beam), 9503: ("burst", _burst),
          9504: ("spark", _spark), 9505: ("wave", _wave), 9506: ("trail", _trail)}


def write(pack_dir, ns, write_json):
    out = []
    for cmd, (name, fn) in SHAPES.items():
        glow, core = _field(fn)
        base = os.path.join(pack_dir, "assets", ns, "textures", "item", "vfx")
        _save(glow, os.path.join(base, name + "_glow.png"))
        _save(core, os.path.join(base, name + "_core.png"))
        g = ns + ":item/vfx/" + name + "_glow"
        c = ns + ":item/vfx/" + name + "_core"

        def plane(y, tex, tint):
            face = {"uv": [0, 0, 16, 16], "texture": tex}
            if tint:
                face["tintindex"] = 0
            return {"from": [0, y, 0], "to": [16, y + 0.01, 16], "shade": False, "faces": {"up": dict(face), "down": dict(face)}}

        model = {"textures": {"glow": g, "core": c, "particle": c},
                 "elements": [plane(8.0, "#glow", True), plane(8.03, "#core", False)],
                 "display": {"fixed": {"scale": [1, 1, 1]}}}
        write_json(os.path.join(pack_dir, "assets", ns, "models", "item", "vfx_" + name + ".json"), model)
        out.append((cmd, ns + ":item/vfx_" + name))
    return out
