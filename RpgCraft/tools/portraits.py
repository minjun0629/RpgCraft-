"""
v5.10.49 몬스터 초상 — 적 정보(오른쪽 위) 둥근 문장 안에 그 몬스터의 3D 모델 얼굴을 보여 준다.
몬스터 · 바닐라 몹 · 동물 (mob_models) 과 보스 (boss_models) 모델을 3/4 정면에서 그려 위쪽(머리 · 가슴)을 동그랗게 자른다.
기본 폰트 글리프 0xE100 + 번호 (36x36 화면, 4배 텍스처). 번호표는 플러그인 리소스 portraits.yml (Java TargetHud 가 읽음).
"""
import math
import os

from PIL import Image, ImageDraw

BASE = 0xE100
SIZE = 36            # 화면 크기 (px)
SS = 4               # 텍스처 배율
SH = {"south": 1.0, "north": 0.62, "east": 0.82, "west": 0.72, "up": 1.0, "down": 0.5}


def _rot(p, rot):
    if not rot:
        return p
    ax, a, o = rot["axis"], math.radians(rot["angle"]), rot["origin"]
    x, y, z = p[0] - o[0], p[1] - o[1], p[2] - o[2]
    c, s = math.cos(a), math.sin(a)
    if ax == "x":
        y, z = y * c - z * s, y * s + z * c
    elif ax == "y":
        x, z = x * c + z * s, -x * s + z * c
    else:
        x, y = x * c - y * s, x * s + y * c
    return (x + o[0], y + o[1], z + o[2])


def render(els, tex, yaw=-28, pitch=14, size=220):
    """요소 목록 → (이미지, 내용 상자). 단순 화가 알고리즘"""
    tex = tex.convert("RGBA")
    tw = tex.width
    cyw, syw = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))
    pts_all = []
    quads = []
    for e in els:
        (x0, y0, z0), (x1, y1, z1) = e["from"], e["to"]
        r = e.get("rotation")
        faces = {"south": [(x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)], "north": [(x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0)],
                 "east": [(x1, y0, z0), (x1, y0, z1), (x1, y1, z1), (x1, y1, z0)], "west": [(x0, y0, z0), (x0, y0, z1), (x0, y1, z1), (x0, y1, z0)],
                 "up": [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)], "down": [(x0, y0, z0), (x1, y0, z0), (x1, y0, z1), (x0, y0, z1)]}
        for f, fd in e.get("faces", {}).items():
            u0, v0, u1, v1 = fd["uv"]
            px = (min(tw - 1, int((u0 + u1) / 2 * tw / 16)), min(tex.height - 1, int((v0 + v1) / 2 * tex.height / 16)))
            col = tex.getpixel(px)
            if col[3] == 0:
                continue
            col = tuple(int(c * SH[f]) for c in col[:3])
            ps = []
            for q in faces[f]:
                x, y, z = _rot(q, r)
                x, z = x * cyw - z * syw, x * syw + z * cyw
                y, z = y * cp - z * sp, y * sp + z * cp
                ps.append((x, y, z))
            pts_all += ps
            quads.append((sum(p[2] for p in ps) / 4, ps, col))
    if not pts_all:
        return None, None
    xs = [p[0] for p in pts_all]
    ys = [p[1] for p in pts_all]
    cx, cy = (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2
    span = max(max(xs) - min(xs), max(ys) - min(ys))
    sc = size / span * 0.92
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for _, ps, col in sorted(quads, key=lambda q: q[0]):
        d.polygon([(size / 2 + (p[0] - cx) * sc, size / 2 - (p[1] - cy) * sc) for p in ps], fill=col + (255,))
    return img, img.getbbox()


def portrait(img, bbox):
    """위쪽(머리 · 가슴)을 정사각형으로 잘라 동그란 초상으로"""
    x0, y0, x1, y1 = bbox
    bw, bh = x1 - x0, y1 - y0
    side = max(bw * 0.9, min(bh, bw * 1.25), bh * 0.5)
    side = min(side, max(bw, bh))
    cx = (x0 + x1) / 2
    top = y0 - side * 0.04
    crop = img.crop((int(cx - side / 2), int(top), int(cx + side / 2), int(top + side)))
    N = SIZE * SS
    crop = crop.resize((N, N), Image.LANCZOS)
    out = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    mask = Image.new("L", (N, N), 0)
    ImageDraw.Draw(mask).ellipse([2, 2, N - 3, N - 3], fill=255)
    bg = Image.new("RGBA", (N, N), (0, 0, 0, 0))   # 안쪽 은은한 어둠 (문장 원판 위)
    ImageDraw.Draw(bg).ellipse([2, 2, N - 3, N - 3], fill=(18, 16, 22, 120))
    out.paste(bg, (0, 0), mask)
    out.alpha_composite(Image.composite(crop, Image.new("RGBA", (N, N), (0, 0, 0, 0)), mask))
    out.putpixel((N - 1, 0), (0, 0, 0, 1))
    return out


def _mob(key):
    import mob_models as M
    models, pal, h, piv, kind = M.build_one(key)
    els = []
    for p, es in models.items():
        P = piv[p]
        for e in es:
            e2 = dict(e)
            e2["from"] = [e["from"][k] + P[k] - 8 for k in range(3)]
            e2["to"] = [e["to"][k] + P[k] - 8 for k in range(3)]
            els.append(e2)
    return els, pal.image()


def _boss(bid):
    import boss_models as bm
    import voxel_bosses
    pal = bm.Palette()
    m = bm.Model(pal)
    i = bm.BOSS_ORDER.index(bid)
    is_boss = i < bm.BOSS_ORDER.index("mount_wolf") or bid.startswith("field_")
    bm.CLAMP_AT_BUILD[0] = not is_boss
    try:
        if bid in voxel_bosses.BUILDERS:
            voxel_bosses.build(bid, m)
        else:
            bm.BUILDERS[bid](m)
            if bid in bm.BOSS_IDS and bid != "primordial_dragon":
                if bid not in bm.NO_GRAND:
                    bm.grand(m, bid, bm.THEME.get(bid, "ff5050"))
                bm.edging(m, bid)
                bm.majesty(m, bid, bm.THEME.get(bid, "ff5050"))
    finally:
        bm.CLAMP_AT_BUILD[0] = True
    return m.els, pal.image()


def keys():
    import mob_models as M
    import boss_models as bm
    out = [("mob", k) for k in M.BUILDERS]
    out += [("boss", b) for b in bm.BOSS_ORDER if not b.startswith(("mount_", "pet_"))]
    return out


def write(pack_dir, ns, plugin_res):
    """초상 텍스처 + 기본 폰트 provider 목록 + portraits.yml"""
    tex = os.path.join(pack_dir, "assets", ns, "textures", "font", "portrait")
    os.makedirs(tex, exist_ok=True)
    providers, lines = [], ["# 자동 생성 (tools/portraits.py): 모델 키 → 초상 글리프 번호 (0xE100 + 번호)"]
    n = 0
    for kind, key in keys():
        try:
            els, img = _mob(key) if kind == "mob" else _boss(key)
            r, bb = render(els, img)
        except Exception as ex:   # 한 모델이 실패해도 나머지는 계속
            print("초상 실패:", key, ex)
            continue
        if r is None or bb is None:
            continue
        portrait(r, bb).save(os.path.join(tex, key + ".png"))
        providers.append({"type": "bitmap", "file": "%s:font/portrait/%s.png" % (ns, key), "ascent": 11, "height": SIZE, "chars": [chr(BASE + n)]})
        lines.append("%s: %d" % (key, n))
        n += 1
    with open(os.path.join(plugin_res, "portraits.yml"), "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    print("초상 %d개" % n)
    return providers
