"""
v5.10.32 마크에이지 4R 풍 UI.
- 판: 어두운 회갈색 가죽 · 돌 질감 + 은빛 청동 테두리 (모서리 마름모 장식)
- 슬롯: 판보다 어둡게 파인 사각 칸 (칸 사이 틈)
- 머리 장식: GUI 위에 뜨는 제목 띠 (양 끝 마름모) + 날개 달린 검 문장
- 바닐라: 핫바 · 선택 칸 · 인벤토리(E) · 상자 창도 같은 판으로
"""
import math
import os

from PIL import Image, ImageDraw

PANEL_T = (94, 84, 76)
PANEL_B = (74, 66, 60)
F_OUT = (28, 24, 22, 255)
F_HI = (204, 194, 180, 255)
F_MID = (140, 126, 112, 255)
F_LO = (70, 62, 56, 255)
F_IN = (40, 35, 32, 255)
SLOT = (44, 39, 36, 255)
SLOT_SH = (26, 23, 21, 255)
SLOT_HI = (110, 100, 92, 255)
SILVER = (220, 224, 232, 255)
SILVER_D = (96, 100, 112, 255)
BRONZE = (176, 146, 96, 255)

HEADER_W, HEADER_H, HEADER_ASCENT = 208, 80, 66   # 머리 장식 글리프: 이미지 y 53 = GUI 맨 위 (GUI 위 53px ~ GUI 안 27px)


def _n(x, y, k=3):
    return ((x * 73856093) ^ (y * 19349663)) % (2 * k + 1) - k


def _c(c, n=0, a=255):
    return (max(0, min(255, c[0] + n)), max(0, min(255, c[1] + n)), max(0, min(255, c[2] + n)), a)


def _lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def diamond(d, cx, cy, r, fill=SILVER, edge=SILVER_D, cross=True):
    """은빛 마름모 장식 (가운데 십자 홈)"""
    for dy in range(-r, r + 1):
        w = r - abs(dy)
        for dx in range(-w, w + 1):
            d.point((cx + dx, cy + dy), fill=edge if abs(dx) == w else fill)
    if cross and r >= 3:
        d.line([(cx - r + 2, cy), (cx + r - 2, cy)], fill=edge)
        d.line([(cx, cy - r + 2), (cx, cy + r - 2)], fill=edge)


def panel(d, x0, y0, x1, y1, img=None):
    """테두리 있는 판 (질감 + 세로 그라데이션 + 3겹 은청동 테두리 + 모서리 장식)"""
    px = img.load() if img is not None else None
    h = max(1, y1 - y0)
    for y in range(y0, y1 + 1):
        base = _lerp(PANEL_T, PANEL_B, (y - y0) / h)
        for x in range(x0, x1 + 1):
            n = _n(x, y, 3) + (2 if (x // 7 + y // 5) % 11 == 0 else 0)
            if px is not None:
                px[x, y] = _c(base, n)
            else:
                d.point((x, y), fill=_c(base, n))
    d.rectangle([x0, y0, x1, y1], outline=F_OUT)
    d.rectangle([x0 + 1, y0 + 1, x1 - 1, y1 - 1], outline=F_MID)
    d.line([(x0 + 1, y0 + 1), (x1 - 1, y0 + 1)], fill=F_HI)
    d.line([(x0 + 1, y0 + 1), (x0 + 1, y1 - 1)], fill=F_HI)
    d.rectangle([x0 + 2, y0 + 2, x1 - 2, y1 - 2], outline=F_IN)
    d.line([(x0 + 3, y1 - 3), (x1 - 3, y1 - 3)], fill=_c(PANEL_B, 14))
    for cx, cy in ((x0 + 1, y0 + 1), (x1 - 1, y0 + 1), (x0 + 1, y1 - 1), (x1 - 1, y1 - 1)):   # 모서리 리벳
        d.rectangle([cx - 1, cy - 1, cx + 1, cy + 1], fill=F_HI)
        d.point((cx, cy), fill=SILVER_D)


def slot(d, x, y, hi=True, gold=False):
    """18x18 칸 안에 16x16 파인 칸 (칸 사이로 판이 보여 틈이 생김)"""
    ring = (120, 106, 84, 255) if gold else (60, 54, 49, 255)
    d.rectangle([x, y, x + 17, y + 17], outline=ring)
    d.rectangle([x + 1, y + 1, x + 16, y + 16], fill=SLOT if hi else _c(SLOT, -6))
    d.line([(x + 1, y + 1), (x + 16, y + 1)], fill=SLOT_SH)
    d.line([(x + 1, y + 1), (x + 1, y + 16)], fill=SLOT_SH)
    d.line([(x + 2, y + 16), (x + 16, y + 16)], fill=SLOT_HI)
    d.line([(x + 16, y + 2), (x + 16, y + 16)], fill=SLOT_HI)
    if gold:
        d.point((x, y), fill=BRONZE); d.point((x + 17, y), fill=BRONZE)
        d.point((x, y + 17), fill=BRONZE); d.point((x + 17, y + 17), fill=BRONZE)


def big_slot(d, x, y, s=26):
    d.rectangle([x, y, x + s - 1, y + s - 1], outline=(60, 54, 49, 255))
    d.rectangle([x + 1, y + 1, x + s - 2, y + s - 2], fill=SLOT)
    d.line([(x + 1, y + 1), (x + s - 2, y + 1)], fill=SLOT_SH)
    d.line([(x + 1, y + 1), (x + 1, y + s - 2)], fill=SLOT_SH)
    d.line([(x + 2, y + s - 2), (x + s - 2, y + s - 2)], fill=SLOT_HI)
    d.line([(x + s - 2, y + 2), (x + s - 2, y + s - 2)], fill=SLOT_HI)


def divider(d, x0, x1, y):
    """가로 구분선 + 가운데 마름모"""
    d.line([(x0, y), (x1, y)], fill=F_IN)
    d.line([(x0, y + 1), (x1, y + 1)], fill=_c(PANEL_T, 18))
    diamond(d, (x0 + x1) // 2, y, 3)


def title_band(d, w):
    """판 안쪽 제목 줄 (제목 글자가 놓이는 곳)"""
    d.rectangle([4, 3, w - 5, 13], fill=(58, 51, 46, 255))
    d.line([(4, 14), (w - 5, 14)], fill=F_LO)
    d.line([(4, 3), (w - 5, 3)], fill=(46, 40, 36, 255))
    diamond(d, 4, 8, 2, cross=False)
    diamond(d, w - 5, 8, 2, cross=False)


def gui_background(rows, highlight=None, main_slots=None):
    """상자 창 배경 (176 x 114 + 18*rows). 좌표계는 예전 그대로 (슬롯 7 + 18c, 17 + 18r)"""
    w, h = 176, 114 + rows * 18
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    panel(d, 0, 0, w - 1, h - 1, img)
    title_band(d, w)
    top_h = 17 + rows * 18 + 3
    if highlight is not None and main_slots is not None and highlight == main_slots:
        for (c0, r0, c1, r1) in ((1, 2, 7, 3), (1, 4, 7, 4), (4, 0, 4, 0), (4, 5, 4, 5)):   # 메인 메뉴: 기능 묶음 판
            x0, y0 = 7 + c0 * 18 - 3, 17 + r0 * 18 - 3
            x1, y1 = 7 + (c1 + 1) * 18 + 2, 17 + (r1 + 1) * 18 + 2
            d.rectangle([x0, y0, x1, y1], fill=(64, 57, 52, 255), outline=F_LO)
            d.line([(x0, y0), (x1, y0)], fill=F_IN)
            for cx, cy in ((x0, y0), (x1, y0), (x0, y1), (x1, y1)):
                diamond(d, cx, cy, 2, cross=False)
    for r in range(rows):
        for c in range(9):
            idx = r * 9 + c
            hi = highlight is None or idx in highlight
            if highlight is not None and not hi:
                continue   # 빈 칸은 그리지 않음 (깔끔하게)
            slot(d, 7 + c * 18, 17 + r * 18, True, highlight is not None)
    divider(d, 5, w - 6, top_h - 1)
    inv_y = rows * 18 + 31
    for r in range(3):
        for c in range(9):
            slot(d, 7 + c * 18, inv_y - 1 + r * 18)
    d.line([(8, rows * 18 + 85), (w - 9, rows * 18 + 85)], fill=_c(PANEL_B, -10))
    for c in range(9):
        slot(d, 7 + c * 18, rows * 18 + 88)
    return img


# ------------------------------------------------------------------ 머리 장식 (제목 띠 + 날개 검 문장)
def _feather(d, base, ang, length, width, col, edge):
    """잎 모양 깃털 (base 에서 ang 방향, 끝으로 갈수록 살짝 넓다가 둥글게)"""
    bx, by = base
    ux, uy = math.cos(ang), math.sin(ang)
    nx, ny = -uy, ux
    pts = []
    for k in range(24):
        u = k / 24 * 2 * math.pi
        along = (1 - math.cos(u)) / 2                 # 0 → 1 → 0
        w = width / 2 * math.sin(u) * (0.55 + 0.45 * along)
        pts.append((bx + ux * length * along + nx * w, by + uy * length * along + ny * w))
    d.polygon(pts, fill=col, outline=edge)


def _wing(d, root, side):
    """천사 날개: 뿌리에서 바깥 위로 휘는 팔 → 팔을 따라 아래 · 바깥으로 늘어진 깃털 (긴 것 뒤, 짧은 것 앞)"""
    rx, ry = root
    span, lift = 50, 20
    arm = [(rx + side * span * t, ry - lift * math.sin(t * math.pi * 0.62)) for t in [i / 15 for i in range(16)]]
    layers = [   # (길이 시작 → 끝, 폭, 색, 테두리, 아래로 기우는 각)
        ((12, 26), 7, (214, 216, 224, 255), (110, 112, 126, 255), 62),
        ((9, 17), 7, (236, 237, 243, 255), (140, 142, 156, 255), 72),
        ((6, 9), 6, (252, 252, 255, 255), (168, 170, 184, 255), 84),
    ]
    for (l0, l1), wd, col, edge, tilt in layers:
        for i, (x, y) in enumerate(arm):
            t = i / (len(arm) - 1)
            ln = l0 + (l1 - l0) * t
            a = math.radians(tilt - 40 * t)          # 바깥쪽 깃털일수록 더 옆으로 뻗음
            ang = a if side > 0 else math.pi - a      # 화면 좌표: 양의 각 = 아래쪽
            ang = ang if side > 0 else ang
            _feather(d, (x, y), ang if side > 0 else math.pi - a, ln, wd, col, edge)
    for (x, y) in arm:   # 팔 윗선
        d.point((int(x), int(y) - 1), fill=(255, 255, 255, 255))


def header():
    img = Image.new("RGBA", (HEADER_W, HEADER_H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    cx = HEADER_W // 2
    # 날개 · 검 문장 (띠 뒤에서 위로)
    _wing(d, (cx + 4, 30), 1)
    _wing(d, (cx - 4, 30), -1)
    # 제목 띠: y 38 ~ 49 (GUI 위 15 ~ 4)
    y0, y1 = 38, 49
    x0, x1 = 12, HEADER_W - 13
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            d.point((x, y), fill=_c(_lerp((72, 64, 58), (48, 42, 38), (y - y0) / (y1 - y0)), _n(x, y, 2)))
    d.rectangle([x0, y0, x1, y1], outline=F_OUT)
    d.line([(x0 + 1, y0 + 1), (x1 - 1, y0 + 1)], fill=F_HI)
    d.line([(x0 + 1, y0 + 2), (x1 - 1, y0 + 2)], fill=F_MID)
    d.line([(x0 + 1, y1 - 1), (x1 - 1, y1 - 1)], fill=F_LO)
    my = (y0 + y1) // 2
    for side in (-1, 1):   # 끝 장식: 뾰족 촉 + 큰 마름모 + 작은 마름모
        ex = x0 if side < 0 else x1
        d.polygon([(ex, y0), (ex, y1), (ex - side * 8, my)], fill=F_MID, outline=F_OUT)
        diamond(d, ex + side * 7, my, 7)
        diamond(d, ex - side * 3, my, 2, cross=False)
    # 검 (띠 위로 솟음): 날 · 가드 · 손잡이 · 폼멜 + 검은 리본
    top = 2
    d.polygon([(cx - 3, top + 6), (cx, top), (cx + 3, top + 6), (cx + 3, 44), (cx - 3, 44)], fill=(204, 208, 218, 255), outline=(70, 74, 86, 255))
    d.line([(cx, top + 2), (cx, 43)], fill=(250, 252, 255, 255))
    d.line([(cx + 2, top + 6), (cx + 2, 43)], fill=(150, 154, 166, 255))
    d.rectangle([cx - 12, 12, cx + 12, 15], fill=(66, 62, 68, 255), outline=F_OUT)
    d.line([(cx - 11, 13), (cx + 11, 13)], fill=(150, 146, 152, 255))
    diamond(d, cx - 13, 13, 3, cross=False); diamond(d, cx + 13, 13, 3, cross=False)
    d.rectangle([cx - 2, 5, cx + 2, 11], fill=(52, 48, 54, 255), outline=F_OUT)
    diamond(d, cx, 3, 3, fill=(210, 214, 224, 255))
    for y in range(18, 40, 5):   # 날에 감긴 리본
        d.line([(cx - 5, y), (cx + 5, y + 3)], fill=(22, 20, 24, 255), width=2)
    d.line([(cx + 5, 21), (cx + 16, 25), (cx + 22, 33)], fill=(22, 20, 24, 255), width=2)
    d.line([(cx - 5, 31), (cx - 15, 34), (cx - 22, 30)], fill=(22, 20, 24, 255), width=2)
    return img


# ------------------------------------------------------------------ 바닐라 창 다시 칠하기
def hotbar_images():
    """핫바 182x22 + 선택 칸 24x24"""
    hb = Image.new("RGBA", (182, 22), (0, 0, 0, 0))
    d = ImageDraw.Draw(hb)
    d.rectangle([0, 0, 181, 21], fill=(52, 46, 42, 235), outline=F_OUT)
    d.line([(1, 1), (180, 1)], fill=F_MID)
    d.line([(1, 20), (180, 20)], fill=F_LO)
    for i in range(9):
        x = 1 + i * 20
        d.rectangle([x + 1, 2, x + 18, 19], fill=(34, 30, 28, 230))
        d.line([(x + 1, 2), (x + 18, 2)], fill=SLOT_SH); d.line([(x + 1, 2), (x + 1, 19)], fill=SLOT_SH)
        d.line([(x + 2, 19), (x + 18, 19)], fill=(84, 76, 70, 255)); d.line([(x + 18, 3), (x + 18, 19)], fill=(84, 76, 70, 255))
    for x in (0, 181):   # 양 끝 마름모
        diamond(d, x, 11, 3)
    sel = Image.new("RGBA", (24, 24), (0, 0, 0, 0))
    ds = ImageDraw.Draw(sel)
    ds.rectangle([0, 0, 23, 23], outline=F_OUT)
    ds.rectangle([1, 1, 22, 22], outline=SILVER)
    ds.rectangle([2, 2, 21, 21], outline=BRONZE)
    for cx, cy in ((1, 1), (22, 1), (1, 22), (22, 22)):
        ds.point((cx, cy), fill=(255, 255, 255, 255))
    diamond(ds, 11, 1, 1, cross=False)
    return hb, sel


def write_vanilla(pack_dir, widgets_path):
    """1.20.1: widgets.png (핫바) · container/inventory.png · container/generic_54.png, 1.20.2+: 스프라이트"""
    gdir = os.path.join(pack_dir, "assets", "minecraft", "textures", "gui")
    os.makedirs(os.path.join(gdir, "container"), exist_ok=True)
    hb, sel = hotbar_images()
    wd = Image.open(widgets_path).convert("RGBA")
    wd.paste(Image.new("RGBA", (182, 22), (0, 0, 0, 0)), (0, 0))
    wd.alpha_composite(hb, (0, 0))
    wd.paste(Image.new("RGBA", (24, 24), (0, 0, 0, 0)), (0, 22))
    wd.alpha_composite(sel, (0, 22))
    wd.save(os.path.join(gdir, "widgets.png"))
    sp = os.path.join(gdir, "sprites", "hud")
    os.makedirs(sp, exist_ok=True)
    hb.save(os.path.join(sp, "hotbar.png"))
    sel.save(os.path.join(sp, "hotbar_selection.png"))
    inventory_png().save(os.path.join(gdir, "container", "inventory.png"))
    generic54_png().save(os.path.join(gdir, "container", "generic_54.png"))
    # 어두운 판 위에서 안 보이는 회색 제목 글자 숨김 ('인벤토리' · '제작')
    for lang in ("ko_kr", "en_us"):
        p = os.path.join(pack_dir, "assets", "minecraft", "lang", lang + ".json")
        os.makedirs(os.path.dirname(p), exist_ok=True)
        with open(p, "w", encoding="utf-8") as f:
            f.write('{"container.inventory": "", "container.crafting": ""}')


def inventory_png():
    """플레이어 인벤토리(E) 176x166 — 바닐라 칸 위치 그대로"""
    img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    panel(d, 0, 0, 175, 165, img)
    for i in range(4):
        slot(d, 7, 7 + i * 18, True, True)            # 갑옷
    slot(d, 76, 61, True, True)                        # 보조 손
    d.rectangle([25, 7, 75, 78], fill=(30, 27, 25, 255), outline=F_IN)   # 캐릭터 창
    d.line([(26, 77), (74, 77)], fill=SLOT_HI); d.line([(74, 8), (74, 77)], fill=SLOT_HI)
    for r in range(2):
        for c in range(2):
            slot(d, 97 + c * 18, 17 + r * 18)          # 제작 2x2
    d.polygon([(135, 31), (143, 35), (135, 39)], fill=F_HI)   # 화살표
    d.line([(130, 35), (135, 35)], fill=F_HI)
    big_slot(d, 148, 22)                               # 결과 칸
    divider(d, 5, 170, 79)
    for r in range(3):
        for c in range(9):
            slot(d, 7 + c * 18, 83 + r * 18)
    d.line([(8, 138), (167, 138)], fill=_c(PANEL_B, -10))
    for c in range(9):
        slot(d, 7 + c * 18, 141)
    return img


def generic54_png():
    """상자 창: 위 6줄(0 ~ 125) + 아래 플레이어 칸(126 ~ 221). 줄 수가 적으면 게임이 위쪽만 잘라 씀"""
    img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    panel(d, 0, 0, 175, 221, img)
    d.rectangle([2, 124, 173, 127], fill=_c(PANEL_B, 0))   # 위아래 이음매가 안 보이게
    title_band(d, 176)
    for r in range(6):
        for c in range(9):
            slot(d, 7 + c * 18, 17 + r * 18)
    divider(d, 5, 170, 128)
    for r in range(3):
        for c in range(9):
            slot(d, 7 + c * 18, 139 + r * 18)
    for c in range(9):
        slot(d, 7 + c * 18, 197)
    return img


# ------------------------------------------------------------------ v5.10.33 HUD (핫바 양옆): LV 배지 · 체력/경험치 바 · 전투 수치
# Java kr.rpgcraft.pack.HudFont.build4r 와 글리프 번호 · 폭 · 높이가 같아야 함
HUD_HP, HUD_XP = 0xE400, 0xE420            # 0 ~ 20 단계
HUD_FRAME_L, HUD_FRAME_R, HUD_BADGE = 0xE440, 0xE441, 0xE442
HUD_DIG_A, HUD_DIG_B = 0xE450, 0xE470      # 위 줄(흰색) · 아래 줄(크림색) 숫자
HUD_ICON_A, HUD_ICON_B = 0xE490, 0xE4A0    # 위 줄 · 아래 줄 아이콘 (ICON_KINDS 순서)
ICON_KINDS = ["sword", "crit", "def", "potion", "skill", "skill_cd", "quick", "quick_cd"]
BAR_W, FRAME_LW, FRAME_RW, BADGE_W = 82, 118, 104, 26
# 액션바 기준선에서 위 가장자리까지 (음수 = 기준선 아래). 핫바 위 끝 = 기준선 + 43
ASC_FRAME, ASC_BADGE, ASC_TOP, ASC_BOT, ASC_DA, ASC_DB = -40, -39, -43, -53, -45, -55


def _fullw(img):
    w, h = img.size
    if img.getpixel((w - 1, 0))[3] == 0:
        img.putpixel((w - 1, 0), (0, 0, 0, 1))
    return img


def hud_bar(step, kind):
    """82x9 바: 어두운 홈 + 채움 (체력 빨강 / 경험치 초록) + 위쪽 빛"""
    img = Image.new("RGBA", (BAR_W, 9), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, BAR_W - 1, 8], fill=(22, 19, 18, 235), outline=F_OUT)
    d.line([(1, 7), (BAR_W - 2, 7)], fill=(58, 52, 48, 255))
    top, bot = ((236, 70, 78), (150, 22, 36)) if kind == "hp" else ((128, 222, 84), (52, 140, 44))
    inner = BAR_W - 2
    fill = round(inner * step / 20)
    for x in range(fill):
        for y in range(1, 8):
            c = _lerp(top, bot, (y - 1) / 6)
            if y == 1:
                c = tuple(min(255, v + 70) for v in c)
            if x % 10 == 9 and y > 1:   # 눈금
                c = tuple(int(v * 0.8) for v in c)
            img.putpixel((1 + x, y), c + (255,))
    if 0 < fill < inner:
        for y in range(1, 8):
            img.putpixel((fill, y), (255, 255, 255, 220))
    return _fullw(img)


def hud_frame(w, left):
    """바 · 수치를 받치는 판 (높이 24, 반투명 어두운 판 + 은 테두리 + 끝 마름모)"""
    img = Image.new("RGBA", (w, 24), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    x0, x1 = (8, w - 7) if left else (6, w - 7)
    d.rectangle([x0, 1, x1, 22], fill=(40, 35, 32, 205), outline=F_OUT)
    d.line([(x0 + 1, 2), (x1 - 1, 2)], fill=F_MID)
    d.line([(x0 + 1, 21), (x1 - 1, 21)], fill=F_LO)
    diamond(d, x1 + 1, 12, 5)
    if not left:
        diamond(d, x0 - 1, 12, 5)
    return _fullw(img)


LV_TXT = {"L": ["10", "10", "10", "10", "11"], "V": ["101", "101", "101", "101", "010"]}


def hud_badge():
    """둥근 레벨 배지 26x26: 은 고리 · 청동 안 고리 · 'LV' 글자 (숫자는 아래 줄 숫자 글리프로)"""
    img = Image.new("RGBA", (BADGE_W, BADGE_W), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.ellipse([0, 0, 25, 25], fill=F_OUT)
    d.ellipse([1, 1, 24, 24], fill=(196, 198, 208, 255))
    d.ellipse([3, 3, 22, 22], fill=(110, 92, 64, 255))
    d.ellipse([4, 4, 21, 21], fill=(30, 27, 30, 255))
    d.arc([4, 4, 21, 21], 200, 320, fill=(70, 66, 74, 255))
    x = 9
    for ch in "LV":
        for yy, row in enumerate(LV_TXT[ch]):
            for xx, v in enumerate(row):
                if v == "1":
                    d.point((x + xx, 7 + yy), fill=(220, 224, 236, 255))
        x += len(LV_TXT[ch][0]) + 1
    diamond(d, 21, 4, 3)
    return img


def hud_providers(icon_fn, digit_fn, chars):
    """[(이름, 이미지, ascent, 글자 번호)] — ui_pack.write_hud 가 hud.json 에 추가"""
    out = []
    for s in range(21):
        out.append(("h4_hp_%02d" % s, hud_bar(s, "hp"), ASC_TOP, HUD_HP + s))
        out.append(("h4_xp_%02d" % s, hud_bar(s, "xp"), ASC_BOT, HUD_XP + s))
    out.append(("h4_frame_l", hud_frame(FRAME_LW, True), ASC_FRAME, HUD_FRAME_L))
    out.append(("h4_frame_r", hud_frame(FRAME_RW, False), ASC_FRAME, HUD_FRAME_R))
    out.append(("h4_badge", hud_badge(), ASC_BADGE, HUD_BADGE))
    names = {"/": "slash", ",": "comma", "%": "pct", ".": "dot", "+": "plus"}
    for i, ch in enumerate(chars):
        n = names.get(ch, ch if ch.isdigit() else ch.lower() + ("_u" if ch.isupper() else "_l"))
        out.append(("h4_da_" + n, digit_fn(ch, (255, 255, 255, 255)), ASC_DA, HUD_DIG_A + i))
        out.append(("h4_db_" + n, digit_fn(ch, (255, 236, 188, 255)), ASC_DB, HUD_DIG_B + i))
    for i, k in enumerate(ICON_KINDS):
        out.append(("h4_ia_" + k, icon_fn(k), ASC_TOP, HUD_ICON_A + i))
        out.append(("h4_ib_" + k, icon_fn(k), ASC_BOT, HUD_ICON_B + i))
    return out
