"""
RpgCraft 3D 아이템 모델 생성기 (픽셀 → 입체 복셀).

32x32 텍스처의 각 픽셀에 '두께'를 주고, 같은 두께끼리 사각형으로 묶어(greedy meshing)
Minecraft 모델 element(직육면체)로 만든다. 앞/뒷면은 텍스처 그대로, 옆면은 가장자리 픽셀 색을 쓴다.

두께 규칙 (pack_art 의 부품 키 기준, 단위 = 모델 좌표 1/16 블록)
  칼날   : 날 끝 1.0 · 중앙 능선 1.5  → 가운데가 도톰한 칼날
  가드   : 3.0 (칼날보다 두껍게 튀어나옴)
  손잡이 : 2.0,   보석/폼멜 보석: 3.5 (가장 돌출)
  도끼   : 날 1.0 → 몸통 2.0 → 자루 쪽 2.5
  방패   : 판 2.0 · 테두리 3.0 · 문장 2.5 · 징 3.0
  소모품 : 가장자리에서 멀수록 두꺼워지는 둥근 형태 (포션/보주/결정/광석/룬), 티켓은 얇은 종이
"""
from collections import deque

N = 32
PX = 16.0 / N  # 모델 좌표에서 픽셀 1칸 크기

WEAPON = {"h": 0.9, "s": 0.9, "b": 1.5, "f": 1.5, "c": 1.5, "C": 1.0, "x": 1.5, "X": 1.5,
          "g": 3.0, "G": 3.0, "w": 2.0, "W": 2.0, "e": 3.5, "E": 3.5, "r": 2.0, "R": 2.0, "*": 0.5}
AXE = dict(WEAPON, b=2.0, s=2.5, h=1.0, c=2.0, C=1.0, x=2.0, X=2.0)
SHIELD = {"b": 2.0, "s": 2.0, "h": 2.0, "f": 2.0, "r": 3.0, "R": 3.0, "e": 2.5, "E": 2.5, "c": 2.5, "C": 3.0,
          "x": 2.5, "X": 2.5, "g": 3.0, "G": 3.0, "w": 2.0, "W": 2.0, "*": 0.5}

# 바닐라 item/handheld · item/generated 와 같은 표시 위치
HANDHELD_DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
    "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
    "fixed": {"rotation": [0, 180, 0], "scale": [1, 1, 1]},
}
GENERATED_DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
    "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
    "fixed": {"rotation": [0, 180, 0], "scale": [1, 1, 1]},
}


def _nb(p):
    x, y = p
    return ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1))


def depth_from_keys(keys, table):
    d = {p: table.get(k, 1.5) for p, k in keys.items() if k != "o"}
    for p, k in keys.items():
        if k == "o":
            nb = [d[q] for q in _nb(p) if q in d]
            d[p] = max(nb) if nb else 1.0
    return d


def mask_of(img):
    return {(x, y) for y in range(img.height) for x in range(img.width) if img.getpixel((x, y))[3] > 0}


def depth_from_alpha(img, base=1.0, step=0.5, maxd=3.0, flat=None):
    if img.width > 32:  # 고해상도: 가장자리 거리 1칸 = 0.25 단위
        step /= 2
    """가장자리 거리 기반 두께 (둥글게 부푼 형태). flat 이면 일정한 두께"""
    m = mask_of(img)
    if flat is not None:
        return {p: flat for p in m}
    dist = {}
    q = deque()
    for p in m:
        if any(n not in m for n in _nb(p)):
            dist[p] = 1
            q.append(p)
    while q:
        p = q.popleft()
        for n in _nb(p):
            if n in m and n not in dist:
                dist[n] = dist[p] + 1
                q.append(n)
    return {p: min(maxd, base + step * (dist.get(p, 1) - 1)) for p in m}


def _rects(depth, n=N):
    used, out = set(), []
    for y in range(n):
        for x in range(n):
            p = (x, y)
            if p in used or p not in depth:
                continue
            dv = depth[p]
            w = 1
            while (x + w, y) in depth and depth[(x + w, y)] == dv and (x + w, y) not in used:
                w += 1
            h = 1
            while all((x + i, y + h) in depth and depth[(x + i, y + h)] == dv and (x + i, y + h) not in used for i in range(w)):
                h += 1
            for i in range(w):
                for j in range(h):
                    used.add((x + i, y + j))
            out.append((x, y, w, h, dv))
    return out


def _r(v):
    v = round(v, 3)
    return int(v) if v == int(v) else v


def elements(depth, n=N):
    PX = 16.0 / n
    els = []
    for (x, y, w, h, dv) in _rects(depth, n):
        x0, x1 = x * PX, (x + w) * PX
        y1, y0 = 16 - y * PX, 16 - (y + h) * PX
        z0, z1 = 8 - dv / 2, 8 + dv / 2
        u0, v0, u1, v1 = x * PX, y * PX, (x + w) * PX, (y + h) * PX
        faces = {
            "south": {"uv": [u0, v0, u1, v1]},
            "north": {"uv": [u1, v0, u0, v1]},
        }
        thinner = lambda q: depth.get(q, 0) < dv
        if any(thinner((x + w, y + j)) for j in range(h)):
            faces["east"] = {"uv": [u1 - PX, v0, u1, v1]}
        if any(thinner((x - 1, y + j)) for j in range(h)):
            faces["west"] = {"uv": [u0, v0, u0 + PX, v1]}
        if any(thinner((x + i, y - 1)) for i in range(w)):
            faces["up"] = {"uv": [u0, v0, u1, v0 + PX]}
        if any(thinner((x + i, y + h)) for i in range(w)):
            faces["down"] = {"uv": [u0, v1 - PX, u1, v1]}
        for f in faces.values():
            f["uv"] = [_r(v) for v in f["uv"]]
            f["texture"] = "#0"
        els.append({"from": [_r(x0), _r(y0), _r(z0)], "to": [_r(x1), _r(y1), _r(z1)], "faces": faces})
    return els


def model(texture, depth, handheld, n=N):
    return {
        "credit": "RpgCraft voxel3d",
        "texture_size": [n, n],
        "gui_light": "front",
        "textures": {"0": texture, "particle": texture},
        "elements": elements(depth, n),
        "display": HANDHELD_DISPLAY if handheld else GENERATED_DISPLAY,
    }


def table_for(name):
    if "shield" in name:
        return SHIELD
    if "axe" in name or "hammer" in name or "tool_gather" in name or name == "spirit_baihu":
        return AXE
    return WEAPON


def misc_depth(name, img):
    if name.startswith("ticket") or name == "check":
        return depth_from_alpha(img, flat=0.75)
    if name.startswith(("potion", "essence")):
        return depth_from_alpha(img, base=1.0, step=0.5, maxd=4.0)
    return depth_from_alpha(img, base=1.0, step=0.5, maxd=3.0)
