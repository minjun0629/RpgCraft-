"""
v5.10.45 마크에이지 4R 풍 "대상 정보" (화면 오른쪽 위): 때리거나 맞은 몬스터의 이름 · 레벨 · 체력 바.
보스바(흰색 = 투명 바) 제목에 기본 폰트 글리프로 그리고, 코어 셰이더가 약속한 글자 색(MARK)만 화면 오른쪽 끝으로 옮긴다.
 - E0A0 틀 (164x30) · E0A1~E0BA 체력 바 (0~25 단계) · E0BB~E0BD 종류 아이콘 (일반 · 정예 · 보스)
Java: kr.rpgcraft.feature.TargetHud (글자 번호 · 폭 · 색이 같아야 함)
"""
import math
import os

from PIL import Image, ImageDraw

W, H = 164, 30
BAR_X, BAR_W, BAR_H = 34, 124, 6
FRAME, BAR0, STEPS, ICON0 = 0xE0A0, 0xE0A1, 25, 0xE0BB
MARGIN = 4
# 셰이더가 옮길 글자 색 (텍스트 색 · 그 그림자 색) — Java TargetHud 와 같아야 함
MARK = ["fcfcf8", "fce080", "fc6060", "c8c8c4"]
F_OUT, F_MID, F_LO = (22, 18, 16, 255), (200, 202, 212, 255), (110, 108, 118, 255)


def _shadow(h):
    return "".join("%02x" % ((int(h[i:i + 2], 16) & 0xFC) >> 2) for i in (0, 2, 4))


def frame():
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # 판: 반투명 어두운 판 + 은 테두리 (오른쪽 끝 마름모 장식)
    d.rectangle([14, 2, W - 5, H - 3], fill=(36, 31, 29, 215), outline=F_OUT)
    d.line([(15, 3), (W - 6, 3)], fill=F_MID)
    d.line([(15, H - 4), (W - 6, H - 4)], fill=F_LO)
    cx, cy = W - 5, H // 2
    d.polygon([(cx, cy - 5), (cx + 4, cy), (cx, cy + 5), (cx - 4, cy)], fill=F_MID, outline=F_OUT)
    # 왼쪽 둥근 배지 (종류 아이콘이 위에 올라감)
    d.ellipse([1, 1, 28, 28], fill=F_OUT)
    d.ellipse([2, 2, 27, 27], fill=(196, 198, 208, 255))
    d.ellipse([4, 4, 25, 25], fill=(110, 92, 64, 255))
    d.ellipse([5, 5, 24, 24], fill=(30, 27, 30, 255))
    # 체력 바 홈
    d.rectangle([BAR_X - 1, 15, BAR_X + BAR_W, 15 + BAR_H + 1], fill=(14, 10, 12, 255))
    return img


def bar(step):
    img = Image.new("RGBA", (BAR_W, BAR_H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, BAR_W - 1, BAR_H - 1], fill=(48, 18, 22, 255))
    n = round(BAR_W * step / STEPS)
    for x in range(n):
        t = x / max(1, BAR_W - 1)
        base = (int(150 + 90 * t), int(30 + 20 * t), int(36 + 10 * t))
        for y in range(BAR_H):
            k = 1.25 if y == 0 else 1.0 if y < 4 else 0.7
            c = tuple(min(255, int(v * k)) for v in base)
            if (x + y * 2) % 10 == 0 and y < 3:
                c = tuple(min(255, v + 60) for v in c)
            img.putpixel((x, y), c + (255,))
    for i in range(1, 10):   # 10% 마디
        x = round(i * BAR_W / 10)
        for y in range(1, BAR_H - 1):
            px = img.getpixel((x, y))
            img.putpixel((x, y), tuple(int(v * 0.55) for v in px[:3]) + (255,))
    return img


def icon(kind):
    S = 52
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    k = S / 13
    if kind == "boss":
        d.polygon([(2 * k, 4 * k), (4 * k, 1 * k), (6.5 * k, 3.5 * k), (9 * k, 1 * k), (11 * k, 4 * k), (10 * k, 5 * k), (3 * k, 5 * k)], fill=(255, 208, 72, 255))
    col = (236, 232, 220, 255) if kind != "elite" else (255, 150, 120, 255)
    d.ellipse([2.5 * k, 3 * k, 10.5 * k, 10 * k], fill=col)
    d.rectangle([4 * k, 8.5 * k, 9 * k, 11.5 * k], fill=col)
    eye = (220, 40, 40, 255) if kind != "normal" else (30, 26, 34, 255)
    d.ellipse([3.8 * k, 5.6 * k, 6 * k, 7.8 * k], fill=eye)
    d.ellipse([7 * k, 5.6 * k, 9.2 * k, 7.8 * k], fill=eye)
    out = img.resize((13, 13), Image.LANCZOS)
    px = out.getpixel((12, 0))
    if px[3] == 0:
        out.putpixel((12, 0), (0, 0, 0, 1))   # 글리프 폭을 13 으로 고정 (오른쪽 끝 투명이면 폭이 줄어 자리 계산이 어긋남)
    return out


def providers(pack_dir, ns):
    tex = os.path.join(pack_dir, "assets", ns, "textures", "font", "target")
    os.makedirs(tex, exist_ok=True)
    out = []
    frame().save(os.path.join(tex, "frame.png"))
    out.append({"type": "bitmap", "file": ns + ":font/target/frame.png", "ascent": 11, "height": H, "chars": [chr(FRAME)]})
    for s in range(STEPS + 1):
        bar(s).save(os.path.join(tex, "bar_%02d.png" % s))
        out.append({"type": "bitmap", "file": ns + ":font/target/bar_%02d.png" % s, "ascent": -5, "height": BAR_H, "chars": [chr(BAR0 + s)]})
    for i, kind in enumerate(("normal", "elite", "boss")):
        icon(kind).save(os.path.join(tex, "icon_%s.png" % kind))
        out.append({"type": "bitmap", "file": ns + ":font/target/icon_%s.png" % kind, "ascent": 3, "height": 13, "chars": [chr(ICON0 + i)]})
    return out


def shader_snippet():
    """rendertype_text.vsh 에 넣을 GLSL: 약속한 색 글자는 오른쪽 끝으로, 그 그림자는 숨김"""
    def vec(h):
        return "vec3(%d.0, %d.0, %d.0)" % tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))
    marks = " || ".join("all(lessThan(abs(c255 - %s), vec3(0.6)))" % vec(h) for h in MARK)
    shads = " || ".join("all(lessThan(abs(c255 - %s), vec3(0.6)))" % vec(_shadow(h)) for h in MARK)
    return """
    // RpgCraft: 4R-style target panel - text in reserved colors is anchored to the right screen edge, its shadow hidden.
    if (abs(gl_Position.w - 1.0) < 0.0001) {
        vec3 c255 = Color.rgb * 255.0;
        if (%s) {
            gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
        } else if (%s) {
            gl_Position.x += 1.0 - (%d.0 * 0.5 + %d.0) * ProjMat[0][0];
        }
    }
""" % (shads, marks, W, MARGIN)
