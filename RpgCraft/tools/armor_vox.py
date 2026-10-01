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
def _helm(g, colf, rx, rz, yb, ry, y0, open_f, t=1.0, pw=3.0, flare=0.16, pri=3):
    """투구 몸통: 아래는 각진 원통 · 위는 둥근 돔. 뒤 · 옆 아랫단은 목 가리개처럼 벌어짐.
    open_f(x,y,z) 가 True 면 비움 (얼굴 구멍). colf(x,y,z,inner) — inner 는 안쪽 면(어두운 안감)"""
    for i in range(math.floor((8 - rx * 1.4) / VS), math.floor((8 + rx * 1.4) / VS) + 1):
        for j in range(math.floor(y0 / VS), math.floor((yb + ry) / VS) + 1):
            for k in range(math.floor((8 - rz * 1.4) / VS), math.floor((8 + rz * 1.4) / VS) + 1):
                x, y, z = (i + 0.5) * VS, (j + 0.5) * VS, (k + 0.5) * VS
                if y < y0:
                    continue
                f = 1 + flare * max(0.0, (y0 + 2.6 - y) / 2.6) * (1.0 if z < 9.0 else 0.4)

                def dd(rx_, rz_, ry_):
                    h = (abs(x - 8) / (rx_ * f)) ** pw + (abs(z - 8) / (rz_ * f)) ** pw
                    return h ** (2 / pw) + (max(0.0, y - yb) / ry_) ** 2
                if dd(rx, rz, ry) > 1:
                    continue
                din = dd(rx - t, rz - t, ry - t)
                if din <= 1 and y > y0 + 0.01:
                    continue
                if open_f(x, y, z):
                    continue
                g.put(x, y, z, colf(x, y, z, din < 1.35), pri)


def _hood(g, S):
    body, rim, gem = S["body"], S["rim"], S["gem"]
    shade = lit(body, 0.42)
    # 어깨까지 덮는 짧은 망토 (두건 아래단)
    _shell(g, (8, 3.4, 7.6), (6.6, 1.6, 6.0), lambda x, y, z, d: rim if y < 2.4 else lit(_pat(S, x, y, z, body), 0.92), 2, inner=0.55,
           clip=lambda x, y, z: y < 4.6 and not (z > 10.5 and abs(x - 8) < 1.2))
    # 두건: 앞이 크게 열리고, 뒤로 뾰족한 끝
    hole = lambda x, y, z: z > 8.6 and ((x - 8) / 3.3) ** 2 + ((y - 8.2) / 3.6) ** 2 < 1
    edge = lambda x, y, z: z > 8.2 and ((x - 8) / 3.9) ** 2 + ((y - 8.2) / 4.2) ** 2 < 1
    _shell(g, (8, 8.4, 7.6), (5.0, 5.6, 5.4), lambda x, y, z, d: shade if d < 0.78 else rim if edge(x, y, z) else _pat(S, x, y, z, body), 3,
           inner=0.6, clip=lambda x, y, z: y > 3.6 and not hole(x, y, z))
    g.tube([(8, 12.4, 5.0), (8, 14.4, 3.4), (8, 15.0, 1.6), (8, 14.2, 0.4)], [2.4, 1.6, 0.8, 0.2],
           lambda x, y, z, d, f: lit(_pat(S, x, y, z, body), 0.92), 3, smooth=True, squash=(0.7, 1))
    # 그늘진 얼굴 속 + 아래를 가린 복면
    _shell(g, (8, 8.0, 8.0), (3.3, 3.6, 0.5), lambda *a: "14101a", 1, inner=0.0)
    _shell(g, (8, 5.6, 8.4), (3.9, 1.5, 3.4), lambda x, y, z, d: lit(rim, 0.75) if int(y / VS) % 2 else lit(rim, 0.9), 4, inner=0.55,
           clip=lambda x, y, z: z > 8.6)


def _cap(g, S):
    """모험가 가죽 모자: 둥근 정수리 + 귀덮개 + 앞 챙"""
    body, rim, gem = S["body"], S["rim"], S["gem"]
    open_f = lambda x, y, z: (z > 8.8 and y < 9.4) or (y < 7.4 and abs(x - 8) < 3.4) or (y < 6.0 and z < 4.8)
    _helm(g, lambda x, y, z, inner: lit(body, 0.45) if inner else rim if y < 9.9 and z > 8.4 else _pat(S, x, y, z, body),
          4.7, 5.0, 8.6, 5.2, 3.6, open_f, pw=2.4, flare=0.0)
    g.slab((8, 9.4, 12.6), (8, 9.1, 14.6), (7.4, 0, 0), 0.5, lambda x, y, z, u, v: rim if abs(v) > 0.88 or u > 0.8 else lit(body, 0.85), 4)   # 챙
    for sx in (-1, 1):   # 귀덮개 끈 · 단추
        g.box((8 + sx * 4.6, 3.6, 7.6), (8 + sx * 5.0, 4.6, 8.4), lit(rim, 0.8), 5)
        g.dot(8 + sx * 4.9, 6.2, 8.0, lit(rim, 1.2), 5, 0.6)
    _shell(g, (8, 13.7, 8), (0.6, 0.4, 0.6), lambda *a: rim, 5, inner=0.0)


def helmet(g, S):
    body, rim, gem, deco, pat = S["body"], S["rim"], S["gem"], S["deco"], S["pat"]
    if deco == "hat":   # 마녀 모자: 넓은 챙 + 휜 고깔
        _shell(g, (8, 5.2, 8), (7.2, 0.7, 7.2), lambda x, y, z, d: _pat(S, x, y, z, body), 3, inner=0.0)
        pts = [(8, 5.6, 8), (8, 9.5, 7.6), (8.6, 12.5, 6.8), (10.4, 14.6, 5.4)]
        g.tube(pts, [4.0, 2.6, 1.3, 0.3], lambda x, y, z, d, f: _pat(S, x, y, z, body), 3, smooth=True)
        _shell(g, (8, 6.6, 8), (4.2, 0.6, 4.2), lambda *a: rim, 4, inner=0.0)
        g.dot(8, 6.6, 12.2, gem, 6, 1.0)
        return
    if deco == "hood":
        _hood(g, S)
        return
    if pat == "patch":
        _cap(g, S)
    else:
        # 금속 투구. 판금은 T자 얼굴 틈 (코 가리개), 나머지는 볼 가리개가 있는 열린 얼굴
        tee = pat == "plate"
        if tee:
            hole = lambda x, y, z, m=0.0: z > 8.6 and ((7.9 - m < y < 9.3 + m and abs(x - 8) < 3.5 + m) or (3.0 < y < 9.3 + m and 0.55 - m < abs(x - 8) < 1.6 + m))
        else:
            hole = lambda x, y, z, m=0.0: z > 8.6 and 3.0 < y < 9.5 + m and abs(x - 8) < 2.7 + m - max(0.0, (6.0 - y) * 0.3)
        brow = lambda y, z: z > 7.0 and 9.6 < y < 10.5

        def colf(x, y, z, inner):
            if inner:
                return lit(body, 0.4)
            if y < 4.0 or hole(x, y, z, 0.55) or brow(y, z):
                return rim
            return _pat(S, x, y, z, body if y > 6.0 else lit(body, 0.9))
        _helm(g, colf, 4.6, 5.0, 9.0, 5.2, 3.4, hole)
        # 머리 꼭대기 능선 · 리벳 · 이마 보석
        arc = [(8, 9.0 + 5.4 * math.sin(a), 8 + 5.4 * math.cos(a)) for a in [math.pi * (0.12 + 0.76 * t / 8) for t in range(9)]]
        g.tube(arc, [0.5] * 9, lit(rim, 1.15), 5)
        for k in range(10):
            a = k * math.pi / 5
            g.dot(8 + math.cos(a) * 4.75, 3.8, 8 + math.sin(a) * 5.15, lit(rim, 1.3), 5, 0.5)
        g.dot(8, 10.6, 13.0, gem, 7, 0.9)
    if deco == "crest":     # 투구 꼭대기 볏 (깃털)
        for k in range(10):
            t = k / 9
            z = 12.5 - t * 9.0
            y = 14.4 + math.sin(t * math.pi) * 2.0
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
    adorned = sid in ("qinglong", "baihu", "zhuque", "xuanwu", "fiend")   # v5.10.53 사신수 · 사흉수: 전용 장식 (spirit_vox) 이 기본 장식을 대신함
    S = {"body": body, "rim": rim, "gem": gem, "pat": pat, "deco": None if adorned else deco}
    old = VL.VS
    VL.VS = VS
    try:
        g = VL.Grid()
        BUILD[slot](g, S)
        if adorned:
            import spirit_vox
            spirit_vox.adorn(g, sid, slot)
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
    sc = GUI_SCALE[slot]
    if adorned:   # 장식이 커진 만큼 슬롯을 넘지 않게 줄임 (가운데 맞춤)
        xs = [c for e in m.els for c in (e["from"][0], e["to"][0])]
        ys = [c for e in m.els for c in (e["from"][1], e["to"][1])]
        zs = [c for e in m.els for c in (e["from"][2], e["to"][2])]
        ext = max(max(xs) - min(xs), max(ys) - min(ys), (max(zs) - min(zs)) * 0.8)
        sc = round(min(sc, 15.0 / ext), 3)
        tr = [round(-((max(xs) + min(xs)) / 2 - 8) * sc, 2), round(-((max(ys) + min(ys)) / 2 - 8) * sc, 2), 0]
        disp["gui"] = dict(DISPLAY["gui"], scale=[sc] * 3, translation=tr)
    else:
        disp["gui"] = dict(DISPLAY["gui"], scale=[sc] * 3)
    return m.els, list(pal.colors), disp


def model_json(texture, els, disp):
    return {"credit": "RpgCraft armor voxels", "texture_size": [16, 16], "gui_light": "side",
            "textures": {"0": texture, "particle": texture}, "elements": els, "display": disp}
