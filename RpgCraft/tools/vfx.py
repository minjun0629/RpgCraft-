"""
스킬 이펙트용 모델 (잔상 없는 순간 이펙트)
플레이어가 스킬을 쓰면 ItemDisplay 로 아래 모델을 한 번 번쩍 띄웠다가 몇 틱 뒤 지운다.
각 모델은 두 겹: 색이 입혀지는 발광 판(tintindex 0 → 가죽 말 갑옷 염색 색) + 흰 중심 판.
  9500 초승달 참격 · 9501 충격파 고리 · 9502 광선 · 9503 별 폭발
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


def _crescent(x, y):
    # 앞(+y 방향 = 텍스처 아래)으로 볼록한 초승달. 가운데가 두껍고 끝이 가늘다
    r = math.hypot(x, y + 0.55)
    ang = math.atan2(x, y + 0.55)            # 0 = 정면
    span = 1.25
    if abs(ang) > span:
        return 0, 0
    t = 1 - abs(ang) / span                  # 가운데 1, 끝 0
    width = 0.025 + 0.11 * t ** 0.8
    d = r - 1.1                              # 바깥쪽은 날카롭게, 안쪽은 부드럽게 번짐
    dd = d / (width * (1.0 if d > 0 else 2.2))
    glow = max(0, 1 - abs(dd) / 1.3) ** 1.2 * (0.3 + 0.7 * t)
    core = max(0, 1 - abs(d) / (width * 0.35)) * t ** 0.5
    return glow, core


def _ring(x, y):
    r = math.hypot(x, y)
    d = abs(r - 0.82)
    return max(0, 1 - d / 0.16) * 0.9, max(0, 1 - d / 0.035)


def _beam(x, y):
    t = 1 - abs(x) ** 3                       # 끝으로 갈수록 가늘게
    d = abs(y)
    return max(0, 1 - d / (0.22 * t + 0.02)) * t, max(0, 1 - d / (0.05 * t + 0.005)) * t


def _burst(x, y):
    r = math.hypot(x, y)
    a = math.atan2(y, x)
    spikes = abs(math.cos(a * 4)) ** 6 * 0.75 + 0.25
    lim = 0.92 * spikes
    g = max(0, 1 - r / lim) ** 1.3
    c = max(0, 1 - r / (lim * 0.45)) ** 1.5
    return g, c


SHAPES = {9500: ("slash", _crescent), 9501: ("ring", _ring), 9502: ("beam", _beam), 9503: ("burst", _burst)}


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
