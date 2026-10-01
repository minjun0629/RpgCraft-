"""
v5.10.47 마크에이지 4R 적 정보 (화면 오른쪽 위) — 참고 화면을 그대로 따라:
 - 위: 청록빛 회색 그라데이션 이름 칸 (은 테두리 · 가운데 흰 이름), 오른쪽 위 모서리에 은 마름모 장식 두 개
 - 아래: 보라 → 파랑 그라데이션 체력 바 (은 테두리 · 가운데 흰 "30/30")
 - 오른쪽: 하얀 날개가 달린 어두운 둥근 문장 (뒤로 지나가는 검 · 금빛 용 메달 · 은 마름모), 정예 = 붉은 문장, 보스 = 금테 + 왕관
보스바(흰색 = 투명 바) 제목에 기본 폰트 글리프로 그리고, 코어 셰이더가 약속한 글자 색(MARK)만 화면 오른쪽 위로 옮긴다.
글리프: E0A0 틀 · E0A1~E0BA 바 채움 (0~25) · E0BB~E0BD 문장 (일반 · 정예 · 보스) · E0C0~ 바 안 숫자
Java: kr.rpgcraft.feature.TargetHud (글자 번호 · 폭 · 색이 같아야 함)
"""
import math
import os

from PIL import Image, ImageDraw

W = 184                        # 전체 폭 (보스바 가운데 정렬 기준) = 문장 오른쪽 끝
FRAME_W, FRAME_H = 110, 42     # 이름 칸 + 바 틀
BOX_X0, BOX_X1 = 2, 106        # 이름 칸 · 바 좌우
NAME_Y0, NAME_Y1 = 10, 28      # 이름 칸
BAR_Y0, BAR_Y1 = 29, 40        # 바 틀
FILL_X, FILL_Y, FILL_W, FILL_H = 4, 31, 101, 7
ASC = 22                       # 글자 기준선 = 틀 위에서 23px (이름 글자가 이름 칸 가운데에 오도록)
EMB_X, EMB, EMB_H = 98, 86, 78   # 문장 위치 (이름 칸 끝에 겹침) · 폭 · 높이
FRAME, BAR0, STEPS, EMB0, DIG0 = 0xE0A0, 0xE0A1, 25, 0xE0BB, 0xE0C0
LV0, LV_CHARS, LV_H = 0xE0D0, "Lv.0123456789", 8   # v5.10.49 이름 칸의 레벨도 4R 풍 글꼴 (글자 색으로 금빛)
DIGITS = "0123456789/,.kM"
MARGIN, DROP = -21, 22           # 오른쪽 여백 · 아래로 내리는 양 (첫 보스바여도 위가 잘리지 않게)
# 셰이더가 옮길 글자 색 — Java TargetHud 와 같아야 함 (흰 · 금 · 빨강 · 회색)
MARK = ["fcfcf8", "fce080", "fc6060", "c8c8c4", "f8fcfc"]
# v5.10.49 그림(틀 · 바 · 문장) = fcfcf8 (뒤), 글자 · 숫자 · 초상 = 나머지 색 (앞으로 당김) — 같은 문자열이어도 글꼴 그림마다 따로 그려져
# 바 채움이 숫자 위에 덮이던 문제 (텍스처별로 묶어 그리므로 순서가 보장되지 않음)
FRONT = ["fce080", "fc6060", "c8c8c4", "f8fcfc"]

SIL, SIL_L, SIL_D, OUT = (198, 202, 212, 255), (240, 242, 248, 255), (112, 116, 128, 255), (24, 22, 26, 255)


def _shadow(h):
    return "".join("%02x" % ((int(h[i:i + 2], 16) & 0xFC) >> 2) for i in (0, 2, 4))


def _lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(len(a)))


def diamond(d, cx, cy, r, fill=SIL, edge=OUT):
    d.polygon([(cx, cy - r), (cx + r, cy), (cx, cy + r), (cx - r, cy)], fill=edge)
    d.polygon([(cx, cy - r + 1), (cx + r - 1, cy), (cx, cy + r - 1), (cx - r + 1, cy)], fill=fill)
    d.polygon([(cx, cy - r + 2), (cx + r - 2, cy), (cx, cy)], fill=SIL_L)
    d.point((cx, cy), fill=SIL_D)


def silver_box(img, x0, y0, x1, y1, top, bottom):
    d = ImageDraw.Draw(img)
    d.rectangle([x0, y0, x1, y1], fill=OUT)
    d.rectangle([x0 + 1, y0 + 1, x1 - 1, y1 - 1], fill=SIL)
    d.line([(x0 + 1, y0 + 1), (x1 - 1, y0 + 1)], fill=SIL_L)
    d.line([(x0 + 1, y1 - 1), (x1 - 1, y1 - 1)], fill=SIL_D)
    d.rectangle([x0 + 2, y0 + 2, x1 - 2, y1 - 2], fill=OUT)
    h = (y1 - 3) - (y0 + 3)
    for y in range(y0 + 3, y1 - 2):
        t = (y - y0 - 3) / max(1, h)
        d.line([(x0 + 3, y), (x1 - 3, y)], fill=_lerp(top, bottom, t))


def frame():
    img = Image.new("RGBA", (FRAME_W, FRAME_H), (0, 0, 0, 0))
    # 이름 칸: 위가 밝은 청록 회색 → 아래 어두운 녹회색 (참고 화면)
    silver_box(img, BOX_X0, NAME_Y0, BOX_X1, NAME_Y1, (112, 138, 124, 235), (52, 72, 62, 235))
    d = ImageDraw.Draw(img)
    d.line([(BOX_X0 + 3, NAME_Y0 + 3), (BOX_X1 - 3, NAME_Y0 + 3)], fill=(150, 176, 162, 235))   # 윗면 반사광
    # 바 틀 (채움은 따로)
    silver_box(img, BOX_X0, BAR_Y0, BOX_X1, BAR_Y1, (26, 20, 34, 235), (16, 12, 22, 235))
    # 오른쪽 위 모서리 은 마름모 두 개 (참고 화면처럼 이름 칸 위에 걸침)
    diamond(d, BOX_X1 - 22, NAME_Y0, 7)
    diamond(d, BOX_X1 - 10, NAME_Y0 + 1, 5)
    d.point((FRAME_W - 1, FRAME_H - 1), fill=(0, 0, 0, 1))   # 글리프 폭 고정
    return img


def fill(step):
    img = Image.new("RGBA", (FILL_W, FILL_H), (0, 0, 0, 0))
    n = round(FILL_W * step / STEPS)
    left, right = (156, 70, 226), (70, 92, 232)          # 보라 → 파랑
    for x in range(FILL_W):
        for y in range(FILL_H):
            if x < n:
                c = _lerp(left, right, x / (FILL_W - 1))
                k = 1.28 if y <= 1 else 1.0 if y < FILL_H - 2 else 0.72
                c = tuple(min(255, int(v * k)) for v in c)
                img.putpixel((x, y), c + (255,))
            elif x == FILL_W - 1 and y == 0:
                img.putpixel((x, y), (0, 0, 0, 1))   # 폭 고정 (비어 있어도 같은 폭)
    if 0 < n < FILL_W:   # 채움 끝 밝은 선
        for y in range(FILL_H):
            px = img.getpixel((n - 1, y))
            img.putpixel((n - 1, y), tuple(min(255, v + 70) for v in px[:3]) + (255,))
    return img


# ------------------------------------------------------------------ v5.10.49 보스 전용 (같은 높이 · 더 넓은 진홍 · 금 틀)
B_FRAME, B_BAR0 = 0xE0E0, 0xE0E1        # 틀 · 바 채움 (0~25)
B_BOX_X1, B_FILL_W, B_EXTRA = 156, 151, 50   # 이름 칸 · 바 오른쪽 끝, 바 폭, 일반 틀보다 왼쪽으로 더 나온 폭
GOLD_, GOLD_L, GOLD_D = (232, 188, 84, 255), (255, 236, 160, 255), (120, 80, 24, 255)


def gold_box(img, x0, y0, x1, y1, top, bottom):
    d = ImageDraw.Draw(img)
    d.rectangle([x0, y0, x1, y1], fill=OUT)
    d.rectangle([x0 + 1, y0 + 1, x1 - 1, y1 - 1], fill=GOLD_)
    d.line([(x0 + 1, y0 + 1), (x1 - 1, y0 + 1)], fill=GOLD_L)
    d.line([(x0 + 1, y1 - 1), (x1 - 1, y1 - 1)], fill=GOLD_D)
    d.rectangle([x0 + 2, y0 + 2, x1 - 2, y1 - 2], fill=OUT)
    h = (y1 - 3) - (y0 + 3)
    for y in range(y0 + 3, y1 - 2):
        t = (y - y0 - 3) / max(1, h)
        d.line([(x0 + 3, y), (x1 - 3, y)], fill=_lerp(top, bottom, t))


def boss_frame():
    W2 = B_BOX_X1 + 4
    img = Image.new("RGBA", (W2, FRAME_H), (0, 0, 0, 0))
    gold_box(img, BOX_X0, NAME_Y0, B_BOX_X1, NAME_Y1, (120, 34, 40, 240), (54, 12, 18, 240))
    d = ImageDraw.Draw(img)
    d.line([(BOX_X0 + 3, NAME_Y0 + 3), (B_BOX_X1 - 3, NAME_Y0 + 3)], fill=(176, 70, 70, 240))
    gold_box(img, BOX_X0, BAR_Y0, B_BOX_X1, BAR_Y1, (26, 10, 12, 240), (14, 6, 8, 240))
    # 왼쪽 끝 해골 · 뿔 장식 + 금 마름모
    for (x, y, r) in ((BOX_X0 + 6, NAME_Y0, 6), (B_BOX_X1 - 30, NAME_Y0, 7), (B_BOX_X1 - 16, NAME_Y0 + 1, 5)):
        d.polygon([(x, y - r), (x + r, y), (x, y + r), (x - r, y)], fill=OUT)
        d.polygon([(x, y - r + 1), (x + r - 1, y), (x, y + r - 1), (x - r + 1, y)], fill=GOLD_)
        d.polygon([(x, y - r + 2), (x + r - 2, y), (x, y)], fill=GOLD_L)
    for k in range(1, 10):   # 바 마디 (틀 위 금 눈금)
        x = 4 + round(k * B_FILL_W / 10)
        d.line([(x, BAR_Y1 - 1), (x, BAR_Y1)], fill=GOLD_L)
    d.point((W2 - 1, FRAME_H - 1), fill=(0, 0, 0, 1))
    return img


def boss_fill(step):
    img = Image.new("RGBA", (B_FILL_W, FILL_H), (0, 0, 0, 0))
    n = round(B_FILL_W * step / STEPS)
    left, right = (200, 20, 34), (255, 120, 40)          # 진홍 → 주황
    for x in range(B_FILL_W):
        for y in range(FILL_H):
            if x < n:
                c = _lerp(left, right, x / (B_FILL_W - 1))
                k = 1.3 if y <= 1 else 1.0 if y < FILL_H - 2 else 0.7
                c = tuple(min(255, int(v * k)) for v in c)
                if x % round(B_FILL_W / 10) == 0 and x > 0:
                    c = tuple(int(v * 0.6) for v in c)
                img.putpixel((x, y), c + (255,))
            elif x == B_FILL_W - 1 and y == 0:
                img.putpixel((x, y), (0, 0, 0, 1))
    if 0 < n < B_FILL_W:
        for y in range(FILL_H):
            img.putpixel((n - 1, y), (255, 240, 200, 255))
    return img


def emblem(kind):
    S = 4
    N, NH = EMB * S, EMB_H * S
    img = Image.new("RGBA", (N, NH), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    cx, cy, r = 30 * S, 42 * S, 20 * S
    # 날개 (오른쪽 위 크게 · 오른쪽 아래 작게): 길쭉한 깃털을 부채꼴로 겹침
    for (wy, up, size) in ((cy - 4 * S, 1, 1.0), (cy + 8 * S, -1, 0.75)):
        for i in range(6):
            a = math.radians((62 - i * 15) * up)
            L = (38 - abs(i - 1) * 3) * S * size
            x0, y0 = cx + 6 * S, wy
            x1, y1 = x0 + math.cos(a) * L, y0 - math.sin(a) * L
            mx, my = (x0 + x1) / 2, (y0 + y1) / 2
            w = 8.5 * S * size
            feather = Image.new("RGBA", (N, NH), (0, 0, 0, 0))
            fd = ImageDraw.Draw(feather)
            fd.ellipse([mx - L / 2, my - w / 2, mx + L / 2, my + w / 2], fill=(132, 136, 150, 255))
            fd.ellipse([mx - L / 2 + S, my - w / 2 + S, mx + L / 2 - S, my + w / 2 - S], fill=(250, 250, 253, 255) if i % 2 == 0 else (228, 230, 238, 255))
            fd.line([(mx - L / 2 + 2 * S, my), (mx + L / 2 - 3 * S, my)], fill=(196, 198, 210, 255), width=S)
            feather = feather.rotate(math.degrees(a), center=(mx, my), resample=Image.BICUBIC)
            img.alpha_composite(feather)
    d = ImageDraw.Draw(img)
    # 뒤로 지나가는 검 (왼쪽 위 → 오른쪽)
    d.line([(cx - 26 * S, cy - 4 * S), (cx + 30 * S, cy + 2 * S)], fill=OUT, width=int(5 * S))
    d.line([(cx - 25 * S, cy - 4 * S), (cx + 29 * S, cy + 2 * S)], fill=(212, 216, 226, 255), width=int(3 * S))
    d.polygon([(cx + 24 * S, cy - 3 * S), (cx + 27 * S, cy + 7 * S), (cx + 25 * S, cy + 8 * S), (cx + 22 * S, cy - 2 * S)], fill=(40, 36, 44, 255))   # 가드
    # 둥근 문장
    rim = {"normal": SIL, "elite": (210, 120, 120, 255), "boss": (232, 190, 80, 255)}[kind]
    body0 = {"normal": (64, 58, 62), "elite": (92, 40, 44), "boss": (70, 52, 40)}[kind]
    d.ellipse([cx - r - 2 * S, cy - r - 2 * S, cx + r + 2 * S, cy + r + 2 * S], fill=OUT)
    d.ellipse([cx - r - S, cy - r - S, cx + r + S, cy + r + S], fill=rim)
    for k in range(r, 0, -S):   # 위가 밝은 둥근 면
        t = k / r
        c = _lerp((body0[0] + 40, body0[1] + 38, body0[2] + 40), body0, t)
        d.ellipse([cx - k, cy - k - (r - k) * 0.25, cx + k, cy + k - (r - k) * 0.25], fill=c + (255,))
    d.arc([cx - r + 3 * S, cy - r + 3 * S, cx + r - 3 * S, cy + r - 3 * S], 200, 300, fill=(150, 146, 150, 200), width=int(1.5 * S))
    # 가운데 검 자루 끝 (참고 화면의 검정 자루)
    d.rectangle([cx + r - 2 * S, cy - 1 * S, cx + r + 8 * S, cy + 3 * S], fill=(26, 24, 28, 255))
    # 금빛 용 메달 (오른쪽 아래)
    mx, my, mr = cx + 14 * S, cy + 15 * S, 7 * S
    d.ellipse([mx - mr - S, my - mr - S, mx + mr + S, my + mr + S], fill=OUT)
    d.ellipse([mx - mr, my - mr, mx + mr, my + mr], fill=(36, 30, 88, 255))
    d.arc([mx - mr + 2 * S, my - mr + 2 * S, mx + mr - 2 * S, my + mr - 2 * S], 20, 320, fill=(240, 176, 48, 255), width=int(2 * S))
    d.ellipse([mx - 2 * S, my - 3 * S, mx + 2 * S, my + S], fill=(255, 210, 90, 255))
    # 은 마름모 (오른쪽 아래 · 왼쪽 위)
    for (x, y, rr) in ((cx + 25 * S, cy + 22 * S, 4 * S), (cx - 2 * S, cy - r - 4 * S, 4 * S)):
        d.polygon([(x, y - rr), (x + rr, y), (x, y + rr), (x - rr, y)], fill=OUT)
        d.polygon([(x, y - rr + S), (x + rr - S, y), (x, y + rr - S), (x - rr + S, y)], fill=SIL)
    if kind == "boss":   # 왕관
        pts = [(cx - 10 * S, cy - r + 2 * S), (cx - 10 * S, cy - r - 8 * S), (cx - 5 * S, cy - r - 3 * S), (cx, cy - r - 10 * S),
               (cx + 5 * S, cy - r - 3 * S), (cx + 10 * S, cy - r - 8 * S), (cx + 10 * S, cy - r + 2 * S)]
        d.polygon(pts, fill=(255, 208, 72, 255), outline=OUT)
    out = img.resize((EMB, EMB_H), Image.LANCZOS)
    out.putpixel((EMB - 1, EMB_H - 1), (0, 0, 0, 1))   # 글리프 폭 고정
    return out


def digits_old(vanilla_dir):
    """(v5.10.48 까지) 바닐라 ascii.png 숫자"""
    src = Image.open(os.path.join(vanilla_dir, "ascii.png")).convert("RGBA")
    cell = src.width // 16
    out = Image.new("RGBA", (cell * len(DIGITS), cell), (0, 0, 0, 0))
    for i, ch in enumerate(DIGITS):
        c = ord(ch)
        g = src.crop(((c % 16) * cell, (c // 16) * cell, (c % 16 + 1) * cell, (c // 16 + 1) * cell))
        px = g.load()
        for y in range(cell):
            for x in range(cell):
                if px[x, y][3] > 0:
                    px[x, y] = (255, 255, 255, 255)
        out.paste(g, (i * cell, 0))
    return out, cell


def providers(pack_dir, ns):
    tex = os.path.join(pack_dir, "assets", ns, "textures", "font", "target")
    os.makedirs(tex, exist_ok=True)
    for f in os.listdir(tex):
        os.remove(os.path.join(tex, f))
    out = []
    frame().save(os.path.join(tex, "frame.png"))
    out.append({"type": "bitmap", "file": ns + ":font/target/frame.png", "ascent": ASC, "height": FRAME_H, "chars": [chr(FRAME)]})
    for s in range(STEPS + 1):
        fill(s).save(os.path.join(tex, "fill_%02d.png" % s))
        out.append({"type": "bitmap", "file": ns + ":font/target/fill_%02d.png" % s, "ascent": ASC - FILL_Y, "height": FILL_H, "chars": [chr(BAR0 + s)]})
    for i, kind in enumerate(("normal", "elite", "boss")):
        emblem(kind).save(os.path.join(tex, "emblem_%s.png" % kind))
        out.append({"type": "bitmap", "file": ns + ":font/target/emblem_%s.png" % kind, "ascent": ASC + 13, "height": EMB_H, "chars": [chr(EMB0 + i)]})
    boss_frame().save(os.path.join(tex, "boss_frame.png"))
    out.append({"type": "bitmap", "file": ns + ":font/target/boss_frame.png", "ascent": ASC, "height": FRAME_H, "chars": [chr(B_FRAME)]})
    for s in range(STEPS + 1):
        boss_fill(s).save(os.path.join(tex, "boss_fill_%02d.png" % s))
        out.append({"type": "bitmap", "file": ns + ":font/target/boss_fill_%02d.png" % s, "ascent": ASC - FILL_Y, "height": FILL_H, "chars": [chr(B_BAR0 + s)]})
    import num4r   # v5.10.49 마크에이지 4R 풍 숫자 (높이 7 = 바 안쪽 높이)
    for i, ch in enumerate(LV_CHARS):
        n = {"L": "lv_l_u", "v": "lv_v_l", ".": "lv_dot"}.get(ch, "lv_" + ch)
        num4r.glyph(ch, LV_H, stroke=1.0)[0].save(os.path.join(tex, n + ".png"))
        out.append({"type": "bitmap", "file": ns + ":font/target/" + n + ".png", "ascent": 7, "height": LV_H, "chars": [chr(LV0 + i)]})
    names = {"/": "slash", ",": "comma", ".": "dot"}
    for i, ch in enumerate(DIGITS):
        n = names.get(ch, ch if ch.isdigit() else ch.lower() + ("_u" if ch.isupper() else "_l"))
        num4r.glyph(ch, FILL_H)[0].save(os.path.join(tex, "d_" + n + ".png"))
        out.append({"type": "bitmap", "file": ns + ":font/target/d_" + n + ".png", "ascent": ASC - FILL_Y, "height": FILL_H, "chars": [chr(DIG0 + i)]})
    return out


def shader_snippet():
    """rendertype_text.vsh 에 넣을 GLSL: 약속한 색 글자는 오른쪽 위로, 그 그림자는 숨김"""
    def vec(h):
        return "vec3(%d.0, %d.0, %d.0)" % tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))
    marks = " || ".join("all(lessThan(abs(c255 - %s), vec3(0.6)))" % vec(h) for h in MARK)
    shads = " || ".join("all(lessThan(abs(c255 - %s), vec3(0.6)))" % vec(_shadow(h)) for h in MARK)
    front = " || ".join("all(lessThan(abs(c255 - %s), vec3(0.6)))" % vec(h) for h in FRONT)
    return """
    // RpgCraft: 4R-style target panel - text in reserved colors is anchored to the top-right of the screen, its shadow hidden.
    if (abs(gl_Position.w - 1.0) < 0.0001) {
        vec3 c255 = Color.rgb * 255.0;
        if (%s) {
            gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
        } else if (%s) {
            gl_Position.x += 1.0 - (%d.0 * 0.5 + %d.0) * ProjMat[0][0];
            gl_Position.y += %d.0 * ProjMat[1][1];
            if (%s) gl_Position.z -= 0.002;
        }
    }
""" % (shads, marks, W, MARGIN, DROP, front)


def preview(path):
    """참고 화면과 비교용 미리보기 (이름 · 숫자 글자는 대략)"""
    vdir = os.path.join(os.path.dirname(os.path.abspath(__file__)), "vanilla")
    bg = Image.new("RGBA", (W + 20, 100), (74, 110, 60, 255))
    base_y = 20 + ASC
    bg.alpha_composite(frame(), (10, base_y - ASC))
    bg.alpha_composite(fill(25), (10 + FILL_X, base_y - ASC + FILL_Y))
    bg.alpha_composite(emblem("normal"), (10 + EMB_X, base_y - ASC - 13))
    import num4r
    txt = "30/30"
    gl = [num4r.glyph(ch, FILL_H) for ch in txt]
    x = 10 + (BOX_X0 + BOX_X1) // 2 - sum(w + 1 for _, w in gl) // 2
    for img, w in gl:
        bg.alpha_composite(img.resize((max(1, round(img.width * FILL_H / img.height)), FILL_H), Image.LANCZOS), (x, base_y - ASC + FILL_Y))
        x += w + 1
    # 보스 판 (아래 줄)
    by = base_y + 62
    bg2 = Image.new("RGBA", (bg.width, 70), (74, 110, 60, 255))
    bx = 10
    bg2.alpha_composite(boss_frame(), (bx, 8))
    bg2.alpha_composite(boss_fill(17), (bx + FILL_X, 8 + FILL_Y))
    bg2.alpha_composite(emblem("boss"), (bx + B_BOX_X1 - 8, 8 - 13))
    full = Image.new("RGBA", (max(bg.width, bx + B_BOX_X1 + 90), bg.height + 70), (74, 110, 60, 255))
    full.alpha_composite(bg, (0, 0))
    full.alpha_composite(bg2.crop((0, 0, min(bg2.width, full.width), 70)), (0, bg.height))
    full.resize((full.width * 3, full.height * 3), Image.NEAREST).save(path)
