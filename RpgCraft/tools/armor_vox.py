"""
v5.10.28 방어구 3D 복셀 모델 (아머러스 워크샵 방식) — 투구 · 갑옷 · 바지 · 신발을 블록을 쌓아 조각한다.
세트별 색 · 무늬 · 장식은 armor_art.SETS (몸 색, 테두리, 보석, 무늬, 투구 장식) 를 그대로 쓴다.
인벤토리 · 손 · 아이템 액자에 보이는 아이템 모델 (입은 모습은 마인크래프트가 갑옷 무늬 텍스처로 그림).
"""
import math

import voxel_lib as VL
import boss_models as bm
import armor_art as AA
from weapon_vox import lit, mixc

VS = 0.5
C = (8.0, 8.0, 8.0)


def mapping():
    """아이템 모델 이름 → (세트, 부위)"""
    m = {}
    for (_v, _cmd, name, sid, slot) in AA.catalog():
        m[name] = (sid, slot)
    for be in ("qinglong", "baihu", "zhuque", "xuanwu"):
        for s in range(4):
            m["spirit_%s_a%d" % (be, s)] = (be, s)
    for s, fid in enumerate(["hundun", "taotie", "taowu", "qiongqi"]):
        m["fiend_" + fid] = ("fiend", s)
    return m


MAP = mapping()
CLOTH = {"stitch", "patch", "rune", "fur", "feather"}


def _pat(S, x, y, z, base):
    """세트 무늬를 표면 색에 입힘"""
    pat = S["pat"]
    i, j, k = int(x / VS), int(y / VS), int(z / VS)
    if pat == "chain":
        return lit(base, 0.82) if i % 2 == 0 else base
    if pat == "plate":
        return lit(base, 0.72) if j % 6 == 0 else lit(base, 1.1) if j % 6 == 1 else base
    if pat == "scale":
        return lit(base, 0.78) if j % 3 == 0 else base
    if pat == "crack":
        return lit(S["gem"], 1.2) if (i - j) % 11 == 0 else base
    if pat == "rune":
        return lit(S["gem"], 1.25) if j % 7 == 3 else base
    if pat == "feather":
        return lit(base, 0.8) if j % 3 == 0 else lit(base, 1.06)
    if pat == "fur":
        return lit(base, 0.88) if j % 3 == 0 else base
    if pat in ("stitch", "patch"):
        return lit(S["rim"], 1.1) if j % 8 == 0 else base
    return base


def _shell(g, c, r, colf, pri=3, inner=0.6, clip=None, pw=2):
    """속이 빈 타원체 껍데기 (clip(x,y,z) 가 False 면 그 자리는 비움). pw>2 면 각진 모양"""
    cx, cy, cz = c
    rx, ry, rz = r
    for i in range(math.floor((cx - rx) / VS), math.floor((cx + rx) / VS) + 1):
        for j in range(math.floor((cy - ry) / VS), math.floor((cy + ry) / VS) + 1):
            for k in range(math.floor((cz - rz) / VS), math.floor((cz + rz) / VS) + 1):
                x, y, z = (i + 0.5) * VS, (j + 0.5) * VS, (k + 0.5) * VS
                d = abs((x - cx) / rx) ** pw + abs((y - cy) / ry) ** pw + abs((z - cz) / rz) ** pw
                if d > 1 or d < inner:
                    continue
                if clip and not clip(x, y, z):
                    continue
                g.put(x, y, z, colf(x, y, z, d), pri)


# ------------------------------------------------------------------ 투구
def helmet(g, S):
    body, rim, gem, deco = S["body"], S["rim"], S["gem"], S["deco"]
    cloth = S["pat"] in CLOTH
    if deco == "hat":   # 마녀 모자: 넓은 챙 + 휜 고깔
        _shell(g, (8, 5.2, 8), (7.2, 0.7, 7.2), lambda x, y, z, d: _pat(S, x, y, z, body), 3, inner=0.0)
        pts = [(8, 5.6, 8), (8, 9.5, 7.6), (8.6, 12.5, 6.8), (10.4, 14.6, 5.4)]
        g.tube(pts, [4.0, 2.6, 1.3, 0.3], lambda x, y, z, d, f: _pat(S, x, y, z, body), 3, smooth=True)
        _shell(g, (8, 6.6, 8), (4.2, 0.6, 4.2), lambda *a: rim, 4, inner=0.0)
        g.dot(8, 6.6, 12.2, gem, 6, 1.0)
        return
    face = lambda x, y, z: not (z > 9.6 and 4.4 < y < 9.2 and abs(x - 8) < (3.0 if cloth else 2.4))
    _shell(g, (8, 8.4, 8), (5.2, 5.6, 5.2), lambda x, y, z, d: _pat(S, x, y, z, body if y > 6.2 else lit(body, 0.85)), 3, inner=0.55,
           clip=lambda x, y, z: y > 3.0 and face(x, y, z))
    if cloth:   # 두건: 뒤로 늘어진 천 · 그늘진 얼굴
        _shell(g, (8, 6.5, 7.2), (5.6, 5.0, 5.4), lambda x, y, z, d: lit(_pat(S, x, y, z, body), 0.9), 2, inner=0.8,
               clip=lambda x, y, z: z < 8.0 and y < 8.5 and y > 1.2)
        _shell(g, (8, 6.8, 9.2), (3.0, 2.6, 0.6), lambda *a: "14101a", 1, inner=0.0)
        g.dot(6.9, 7.2, 9.9, lit(gem, 1.3), 6, 0.6)
        g.dot(9.1, 7.2, 9.9, lit(gem, 1.3), 6, 0.6)
    else:       # 금속 투구: 테두리 띠 · 코 가리개 · 눈 틈 · 가운데 능선
        _shell(g, (8, 6.0, 8), (5.5, 0.7, 5.5), lambda x, y, z, d: rim, 4, inner=0.0, clip=face)
        g.box((7.4, 4.2, 12.4), (8.6, 8.6, 13.3), rim, 5)
        _shell(g, (8, 9.2, 8), (0.6, 5.3, 5.3), lambda x, y, z, d: lit(rim, 1.15), 5, inner=0.75, clip=lambda x, y, z: y > 8.0)
        for sx in (-1, 1):
            g.box((8 + sx * 1.0, 7.0, 12.9), (8 + sx * 2.6, 7.6, 13.3), "1a1620", 6)
        g.dot(8, 11.4, 13.2, gem, 7, 0.9)
    if deco == "crest":     # 투구 꼭대기 볏 (깃털)
        for k in range(10):
            t = k / 9
            z = 12.5 - t * 9.0
            y = 13.8 + math.sin(t * math.pi) * 1.6
            g.box((7.4, 12.5, z - 0.5), (8.6, y, z + 0.5), mixc(gem, lit(gem, 0.6), t), 6)
    elif deco == "wings":   # 옆머리 날개
        for sx in (-1, 1):
            base = (8 + sx * 5.0, 9.0, 8.0)
            for k, (dy, dz, ln) in enumerate(((3.6, -1.0, 4.4), (2.2, -2.4, 4.8), (0.6, -3.4, 4.2))):
                g.tube([base, (base[0] + sx * 1.2, base[1] + dy, base[2] + dz), (base[0] + sx * 0.6, base[1] + dy + ln * 0.5, base[2] + dz - ln * 0.5)],
                       [0.55, 0.45, 0.12], lit(rim, 1.1 - 0.1 * k), 5, smooth=True, squash=(0.4, 1))
    elif deco == "goggles":
        for sx in (-1, 1):
            g.tube([(8 + sx * 1.8, 10.6, 12.0), (8 + sx * 1.8, 10.6, 13.4)], [1.1, 1.1], rim, 6)
            g.dot(8 + sx * 1.8, 10.6, 13.5, lit(gem, 1.3), 7, 1.2)
        _shell(g, (8, 10.6, 8), (5.4, 0.45, 5.4), lambda *a: "3a2a1a", 5, inner=0.0)
    elif deco == "crown":
        for k in range(8):
            a = k * math.pi / 4
            x, z = 8 + math.cos(a) * 4.2, 8 + math.sin(a) * 4.2
            g.cone((x, 12.4, z), (x, 15.4 if k % 2 == 0 else 14.2, z), 0.7, 0.1, rim, 6)
        _shell(g, (8, 12.4, 8), (4.6, 0.6, 4.6), lambda *a: rim, 6, inner=0.5)
        g.dot(8, 13.4, 12.4, gem, 7, 1.0)
    elif deco == "fin":
        g.triangle((8, 13.0, 11.0), (8, 17.0, 6.0), (8, 12.6, 3.6), lambda p, w: mixc(rim, gem, 1 - w), 6, thick=0.4)
    elif deco == "horns":
        for sx in (-1, 1):
            g.tube([(8 + sx * 4.2, 11.0, 8.6), (8 + sx * 6.8, 12.4, 8.2), (8 + sx * 7.6, 15.2, 7.0), (8 + sx * 6.6, 17.2, 6.2)], [1.0, 0.8, 0.5, 0.12],
                   lambda x, y, z, d, f: mixc(lit(rim, 0.7), lit(gem, 1.2), f), 6, smooth=True)
    elif deco == "ears":
        for sx in (-1, 1):
            g.cone((8 + sx * 3.4, 12.6, 8.0), (8 + sx * 4.6, 16.2, 7.6), 1.4, 0.15, body, 6)
            g.cone((8 + sx * 3.4, 12.8, 8.5), (8 + sx * 4.4, 15.4, 8.2), 0.8, 0.1, lit(gem, 1.1), 7)
    elif deco == "plume":
        pts = [(8, 13.0, 9.0), (8, 15.4, 7.0), (8, 15.6, 4.0), (8, 13.4, 1.6)]
        g.tube(pts, [1.1, 1.0, 0.8, 0.2], lambda x, y, z, d, f: mixc(lit(gem, 1.2), rim, f), 6, smooth=True, squash=(0.5, 1))


# ------------------------------------------------------------------ 갑옷
def chest(g, S):
    body, rim, gem, deco = S["body"], S["rim"], S["gem"], S["deco"]
    cloth = S["pat"] in CLOTH
    neck = lambda x, y, z: not (y > 13.0 and abs(x - 8) < 2.2 and abs(z - 8) < 1.8)
    _shell(g, (8, 7.6, 8), (5.0, 6.8, 3.2), lambda x, y, z, d: _pat(S, x, y, z, body), 3, inner=0.45,
           clip=lambda x, y, z: 1.6 < y and neck(x, y, z), pw=4)
    # 가슴판 (앞으로 도드라짐) + 테두리
    _shell(g, (8, 9.6, 9.6), (3.6, 3.6, 2.2), lambda x, y, z, d: rim if d > 0.82 else lit(_pat(S, x, y, z, body), 1.12), 4, inner=0.65,
           clip=lambda x, y, z: z > 10.4)
    g.sphere((8, 10.0, 11.7), 0.95, lambda x, y, z, d: lit(gem, 1.5) if d < 0.3 else gem, 6)   # 가슴 보석
    # 허리띠 · 버클
    _shell(g, (8, 2.6, 8), (5.7, 0.8, 3.7), lambda *a: lit(rim, 0.8) if cloth else rim, 4, inner=0.6)
    g.box((7.0, 1.9, 11.2), (9.0, 3.3, 11.9), lit(rim, 1.2), 5)
    # 어깨 보호대 (두 겹)
    for sx in (-1, 1):
        c = (8 + sx * 6.0, 12.6, 8.0)
        _shell(g, c, (2.8, 2.0, 3.0), lambda x, y, z, d: rim if d > 0.85 else _pat(S, x, y, z, body), 4, inner=0.0,
               clip=lambda x, y, z: y > 11.4)
        _shell(g, (c[0] + sx * 0.4, c[1] - 1.1, c[2]), (2.5, 1.4, 2.7), lambda x, y, z, d: rim if d > 0.85 else lit(_pat(S, x, y, z, body), 0.9), 3,
               inner=0.0, clip=lambda x, y, z: y > 10.3)
        if deco in ("horns", "crest") or S["pat"] == "crack":
            g.cone((c[0] + sx * 1.0, c[1] + 1.2, c[2]), (c[0] + sx * 2.6, c[1] + 3.2, c[2] - 0.3), 0.6, 0.1, lit(gem, 1.1), 5)
        if deco == "wings":
            for k in range(3):
                g.tube([(c[0], c[1] + 0.6, c[2] - 2.4), (c[0] + sx * (1.6 + k), c[1] + 3.4 - k, c[2] - 4.2 - k * 0.6)], [0.5, 0.1], lit(rim, 1.1), 5)
    if cloth:   # 망토 · 목도리
        g.slab((8, 13.4, 4.4), (8, 0.6, 3.6), (9.6, 0, 0), 0.5, lambda x, y, z, u, v: lit(rim, 0.85 - 0.15 * u), 2)
        _shell(g, (8, 13.6, 8), (3.6, 1.0, 2.6), lambda x, y, z, d: lit(rim, 1.0), 5, inner=0.4)


# ------------------------------------------------------------------ 바지
def legs(g, S):
    body, rim, gem = S["body"], S["rim"], S["gem"]
    _shell(g, (8, 13.2, 8), (5.0, 1.4, 3.0), lambda x, y, z, d: rim, 4, inner=0.5)   # 허리띠
    g.box((7.1, 12.5, 10.6), (8.9, 13.9, 11.3), lit(rim, 1.2), 5)
    for sx in (-1, 1):
        x = 8 + sx * 2.3
        _shell(g, (x, 7.2, 8), (2.3, 6.2, 2.4), lambda x_, y, z, d: _pat(S, x_, y, z, body), 3, inner=0.45, clip=lambda x_, y, z: 1.2 < y < 12.6, pw=3)
        _shell(g, (x, 6.6, 10.0), (1.8, 1.6, 0.9), lambda x_, y, z, d: rim if d > 0.6 else lit(rim, 1.2), 5, inner=0.0)   # 무릎 보호대
        g.dot(x, 6.6, 10.9, gem, 6, 0.6)
    for sx in (-1, 1):   # 앞 허리 보호판
        g.slab((8 + sx * 2.6, 12.2, 10.6), (8 + sx * 2.9, 9.4, 11.0), (2.2, 0, 0), 0.4, lambda x, y, z, u, v: rim if abs(v) > 0.7 or u > 0.85 else lit(body, 1.05), 4)


# ------------------------------------------------------------------ 신발
def boots(g, S):
    body, rim, gem = S["body"], S["rim"], S["gem"]
    for sx in (-1, 1):
        x = 8 + sx * 2.6
        _shell(g, (x, 5.0, 7.4), (2.0, 4.0, 2.2), lambda x_, y, z, d: _pat(S, x_, y, z, body), 3, inner=0.4, clip=lambda x_, y, z: 1.0 < y < 8.6)
        _shell(g, (x, 2.0, 9.0), (2.0, 1.5, 3.4), lambda x_, y, z, d: _pat(S, x_, y, z, body), 3, inner=0.0, clip=lambda x_, y, z: y > 0.8)   # 발
        g.box((x - 2.0, 0.4, 5.4), (x + 2.0, 1.1, 12.4), lit(rim, 0.55), 4)   # 밑창
        _shell(g, (x, 2.2, 11.4), (1.7, 1.2, 1.2), lambda *a: rim, 5, inner=0.0)   # 발끝 쇠
        _shell(g, (x, 8.4, 7.4), (2.4, 0.7, 2.6), lambda *a: rim, 5, inner=0.5)   # 위 테두리
        g.dot(x, 6.4, 9.6, gem, 6, 0.6)


BUILD = [helmet, chest, legs, boots]

DISPLAY = {
    "gui": {"rotation": [18, -32, 0], "translation": [0, 0, 0], "scale": [0.9, 0.9, 0.9]},
    "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 2.5, 2], "scale": [0.42, 0.42, 0.42]},
    "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 2.5, 2], "scale": [0.42, 0.42, 0.42]},
    "firstperson_righthand": {"rotation": [0, -60, 0], "translation": [1.5, 3, 0], "scale": [0.5, 0.5, 0.5]},
    "firstperson_lefthand": {"rotation": [0, 60, 0], "translation": [1.5, 3, 0], "scale": [0.5, 0.5, 0.5]},
    "ground": {"translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4]},
    "fixed": {"rotation": [0, 180, 0], "scale": [0.75, 0.75, 0.75]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
}
GUI_SCALE = [0.95, 0.72, 0.85, 1.0]


def build(name):
    """방어구 하나 → (요소, 팔레트 색). 방어구가 아니면 None"""
    if name not in MAP:
        return None
    sid, slot = MAP[name]
    body, rim, gem, pat, deco = AA.SETS[sid]
    S = {"body": body, "rim": rim, "gem": gem, "pat": pat, "deco": deco}
    old = VL.VS
    VL.VS = VS
    try:
        g = VL.Grid()
        BUILD[slot](g, S)
        pal = bm.Palette()
        m = bm.Model(pal)
        bm.CLAMP_AT_BUILD[0] = False
        VL.emit(g, m)
    finally:
        VL.VS = old
        bm.CLAMP_AT_BUILD[0] = True
    for e in m.els:
        e.pop("_col", None)
        e.pop("_edge", None)
        for key in ("from", "to"):
            e[key] = [max(-16, min(32, round(c, 4))) for c in e[key]]
    if len(pal.colors) > 256:
        raise SystemExit("방어구 팔레트 색이 256 개를 넘음: " + name)
    disp = dict(DISPLAY)
    disp["gui"] = dict(DISPLAY["gui"], scale=[GUI_SCALE[slot]] * 3)
    return m.els, list(pal.colors), disp


def model_json(texture, els, disp):
    return {"credit": "RpgCraft armor voxels", "texture_size": [16, 16], "gui_light": "side",
            "textures": {"0": texture, "particle": texture}, "elements": els, "display": disp}
