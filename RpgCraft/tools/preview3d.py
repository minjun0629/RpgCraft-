#!/usr/bin/env python3
"""3D 모델 미리보기 (간이 소프트웨어 렌더러). python3 tools/preview3d.py 이름1 이름2 ... -o out.png"""
import json, math, os, sys
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BASE = os.path.join(ROOT, "resourcepack", "assets", "rpgcraft")
SHADE = {"south": 1.0, "north": 0.6, "east": 0.78, "west": 0.7, "up": 0.92, "down": 0.5}


def render(name, yaw=-30, pitch=20, roll=0, size=300):
    m = json.load(open(os.path.join(BASE, "models", "item", name + ".json")))
    while "elements" not in m:
        m = dict(json.load(open(os.path.join(BASE, "models", "item", m["parent"].split(":")[1].replace("item/", "") + ".json"))), textures=m.get("textures", m.get("textures")))
    tex = Image.open(os.path.join(BASE, "textures", "item", name + ".png")).convert("RGBA")
    cy, sy = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))
    sc = size / 20

    def P(x, y, z):
        x, y, z = x - 8, y - 8, z - 8
        x, z = x * cy - z * sy, x * sy + z * cy
        y, z = y * cp - z * sp, y * sp + z * cp
        return (size / 2 + x * sc, size / 2 - y * sc, z)

    quads = []
    for el in m["elements"]:
        (x0, y0, z0), (x1, y1, z1) = el["from"], el["to"]
        for f, fd in el["faces"].items():
            u0, v0, u1, v1 = fd["uv"]
            ua, ub = sorted((u0, u1)); va, vb = sorted((v0, v1))
            f2 = tex.width / 16
            pxs = range(int(ua * f2), max(int(ua * f2) + 1, int(round(ub * f2))))
            pys = range(int(va * f2), max(int(va * f2) + 1, int(round(vb * f2))))
            for px in pxs:
                for py in pys:
                    col = tex.getpixel((min(tex.width - 1, px), min(tex.height - 1, py)))
                    if col[3] == 0:
                        continue
                    col = tuple(int(c * SHADE[f]) for c in col[:3])
                    X0, X1 = px / f2, (px + 1) / f2
                    Y1, Y0 = 16 - py / f2, 16 - (py + 1) / f2
                    if f in ("south", "north"):
                        z = z1 if f == "south" else z0
                        xa, xb = (X0, X1) if f == "south" else (16 - X1 + 0, 16 - X0) if False else (X0, X1)
                        c = [(xa, Y0, z), (xb, Y0, z), (xb, Y1, z), (xa, Y1, z)]
                    elif f in ("east", "west"):
                        x = x1 if f == "east" else x0
                        c = [(x, Y0, z0), (x, Y0, z1), (x, Y1, z1), (x, Y1, z0)]
                    else:
                        y = y1 if f == "up" else y0
                        c = [(X0, y, z0), (X1, y, z0), (X1, y, z1), (X0, y, z1)]
                    pts = [P(*q) for q in c]
                    quads.append((sum(p[2] for p in pts) / 4, [(p[0], p[1]) for p in pts], col))
    img = Image.new("RGBA", (size, size), (40, 38, 48, 255))
    d = ImageDraw.Draw(img)
    for _, pts, col in sorted(quads, key=lambda q: q[0]):
        d.polygon(pts, fill=col)
    return img


if __name__ == "__main__":
    args = sys.argv[1:]
    out = "preview3d.png"
    if "-o" in args:
        out = args[args.index("-o") + 1]
        args = args[:args.index("-o")]
    cols = 5
    rows = (len(args) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * 300, rows * 300), (40, 38, 48, 255))
    for i, n in enumerate(args):
        sheet.paste(render(n), ((i % cols) * 300, (i // cols) * 300))
    sheet.save(out)
    print(out)
