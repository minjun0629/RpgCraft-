"""
RpgCraft 서버 UI 리소스 (리소스팩)

1) 커스텀 HUD 폰트 rpgcraft:hud  — 액션바로 그리는 체력바/아이콘/숫자
   · 바닐라 하트·갑옷·배고픔·산소 자리를 그대로 사용 (그 아이콘은 icons.png 에서 투명 처리)
   · 1행 (하트 줄):  [❤ 체력 바 + 숫자]            [포션 n] [퀵스킬 쿨] [방어 %]
   · 2행 (갑옷 줄):  [⚔ 공격력]                     [✦ 크리 %]
2) icons.png: 하트/갑옷/배고픔/산소 숨김, 경험치 바를 금색 테마로
3) 기본 폰트 글리프: 서버 로고(사이드바·탭·타이틀), 레벨 등급 배지(채팅), 메뉴별 배경
!! 글리프 코드/폭/ascent 는 Java (kr.rpgcraft.pack.HudFont, PackManager) 와 반드시 일치해야 한다.
"""
import json
import os
from PIL import Image, ImageDraw

NS = "rpgcraft"

# ------------------------------------------------------------------ HUD 글리프 정의 (Java HudFont 와 동일)
HP_STEPS = 20
HP_BASE = 0xE200          # E200~E214 체력 바 0~20단계 (81x9)
ICON = {"potion": 0xE220, "skill": 0xE221, "skill_cd": 0xE222, "def": 0xE223, "sword": 0xE230, "crit": 0xE231, "quick": 0xE232, "quick_cd": 0xE233}
DIGITS_ROW1 = 0xE240      # 0-9 / , % . k M s +
DIGITS_ROW2 = 0xE260
DIGIT_CHARS = "0123456789/,%.kMs+"
ASC_ROW1, ASC_ROW2 = -26, -15          # 하트 줄 / 갑옷 줄 (위 가장자리 기준)
ASC_DIG1, ASC_DIG2 = ASC_ROW1 - 2, ASC_ROW2 - 2

LOGO = 0xE030
BADGE_BASE = 0xE040        # E040~E045 레벨 등급 배지

# 3x5 숫자/기호 픽셀 폰트
GLYPH5 = {
    "0": ["111", "101", "101", "101", "111"], "1": ["010", "110", "010", "010", "111"], "2": ["111", "001", "111", "100", "111"],
    "3": ["111", "001", "111", "001", "111"], "4": ["101", "101", "111", "001", "001"], "5": ["111", "100", "111", "001", "111"],
    "6": ["111", "100", "111", "101", "111"], "7": ["111", "001", "010", "010", "010"], "8": ["111", "101", "111", "101", "111"],
    "9": ["111", "101", "111", "001", "111"], "/": ["001", "001", "010", "100", "100"], ",": ["0", "0", "0", "1", "1"],
    "%": ["11001", "11010", "00100", "01011", "10011"], ".": ["0", "0", "0", "0", "1"], "k": ["100", "101", "110", "101", "101"],
    "M": ["10001", "11011", "10101", "10001", "10001"], "s": ["000", "011", "110", "011", "110"], "+": ["000", "010", "111", "010", "000"],
}


def _full_width(img):
    """폰트 폭은 '가장 오른쪽 불투명 픽셀'로 정해지므로, 오른쪽 위에 거의 투명한 점을 찍어 폭을 고정"""
    w, h = img.size
    if img.getpixel((w - 1, 0))[3] == 0:
        img.putpixel((w - 1, 0), (0, 0, 0, 1))
    return img


def digit_img(ch, color=(255, 255, 255, 255)):
    rows = GLYPH5[ch]
    img = Image.new("RGBA", (len(rows[0]), 5), (0, 0, 0, 0))
    for y, r in enumerate(rows):
        for x, v in enumerate(r):
            if v == "1":
                img.putpixel((x, y), color)
    return _full_width(img)


def hp_bar(step):
    img = Image.new("RGBA", (81, 9), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    heart = ["0110110", "1111111", "1111111", "0111110", "0011100", "0001000"]
    for y, r in enumerate(heart):
        for x, v in enumerate(r):
            if v == "1":
                img.putpixel((1 + x, 1 + y), (255, 70, 80, 255) if not (x == 1 and y == 1) else (255, 200, 200, 255))
    d.rectangle([10, 0, 80, 8], fill=(24, 10, 14, 235))
    d.rectangle([10, 0, 80, 8], outline=(120, 90, 40, 255))
    inner = 69
    fill = round(inner * step / HP_STEPS)
    for x in range(fill):
        for y in range(1, 8):
            t = y / 7
            r, g, b = int(235 - 60 * t), int(60 - 30 * t), int(70 - 30 * t)
            if y == 1:
                r, g, b = 255, 150, 150
            img.putpixel((11 + x, y), (r, g, b, 255))
    if 0 < fill < inner:
        for y in range(1, 8):
            img.putpixel((11 + fill - 1, y), (255, 220, 220, 255))
    return _full_width(img)


def icon(kind):
    img = Image.new("RGBA", (9, 9), (0, 0, 0, 0))
    P = {
        "potion": (["000111000", "000101000", "000101000", "001111100", "011111110", "011111110", "011111110", "001111100", "000000000"],
                   {"1": (255, 90, 110, 255)}),
        "skill": (["000011110", "000111100", "001111000", "011111110", "000011100", "000111000", "001110000", "001100000", "011000000"],
                  {"1": (255, 214, 64, 255)}),
        "skill_cd": (["000011110", "000111100", "001111000", "011111110", "000011100", "000111000", "001110000", "001100000", "011000000"],
                     {"1": (110, 110, 120, 255)}),
        "def": (["011111110", "011111110", "011111110", "011111110", "001111100", "001111100", "000111000", "000010000", "000000000"],
                {"1": (90, 170, 255, 255)}),
        "sword": (["000000011", "000000111", "000001110", "000011100", "010111000", "001110000", "001100000", "010010000", "100000000"],
                  {"1": (230, 230, 240, 255)}),
        "quick": (["000111000", "001111100", "011010110", "011111110", "011101110", "001111100", "000111000", "000000000", "000000000"],
                  {"1": (180, 120, 255, 255)}),
        "quick_cd": (["000111000", "001111100", "011010110", "011111110", "011101110", "001111100", "000111000", "000000000", "000000000"],
                     {"1": (110, 110, 120, 255)}),
        "crit": (["000010000", "000111000", "111111111", "011111110", "001111100", "011101110", "011000110", "000000000", "000000000"],
                 {"1": (255, 214, 64, 255)}),
    }
    rows, pal = P[kind]
    for y, r in enumerate(rows):
        for x, v in enumerate(r):
            if v in pal:
                img.putpixel((x, y), pal[v])
    # 윤곽선
    out = img.copy()
    for y in range(9):
        for x in range(9):
            if img.getpixel((x, y))[3] == 0 and any(0 <= x + dx < 9 and 0 <= y + dy < 9 and img.getpixel((x + dx, y + dy))[3] > 10
                                                   for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                out.putpixel((x, y), (20, 16, 24, 220))
    return _full_width(out)


# ------------------------------------------------------------------ 로고 / 배지
LETTERS = {
    "R": ["11110", "10001", "10001", "11110", "10100", "10010", "10001"], "P": ["11110", "10001", "10001", "11110", "10000", "10000", "10000"],
    "G": ["01110", "10001", "10000", "10111", "10001", "10001", "01111"], "C": ["01110", "10001", "10000", "10000", "10000", "10001", "01110"],
    "A": ["01110", "10001", "10001", "11111", "10001", "10001", "10001"], "F": ["11111", "10000", "10000", "11110", "10000", "10000", "10000"],
    "T": ["11111", "00100", "00100", "00100", "00100", "00100", "00100"],
}


def logo(sword_img):
    word = "RPGCRAFT"
    W = 18 + len(word) * 12
    img = Image.new("RGBA", (W + 2, 18), (0, 0, 0, 0))
    img.alpha_composite(sword_img.resize((16, 16), Image.NEAREST), (0, 1))
    x0 = 20
    mask = set()
    for i, ch in enumerate(word):
        for y, r in enumerate(LETTERS[ch]):
            for x, v in enumerate(r):
                if v == "1":
                    for dx in range(2):
                        for dy in range(2):
                            mask.add((x0 + i * 12 + x * 2 + dx, 2 + y * 2 + dy))
    for (x, y) in mask:
        t = (y - 2) / 14
        c = (int(255 - 40 * t), int(236 - 90 * t), int(150 - 110 * t), 255) if y > 3 else (255, 250, 220, 255)
        img.putpixel((x, y), c)
    for (x, y) in list(mask):
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1), (1, 1)):
            q = (x + dx, y + dy)
            if q not in mask and 0 <= q[0] < img.width and 0 <= q[1] < img.height and img.getpixel(q)[3] == 0:
                img.putpixel(q, (60, 30, 10, 255))
    return img


BADGE_TIERS = [((110, 200, 90), "leaf"), ((196, 120, 60), "dot"), ((200, 210, 225), "dot2"),
               ((255, 205, 60), "star"), ((110, 230, 255), "gem"), ((230, 110, 255), "crown")]


def badge(i):
    col, sym = BADGE_TIERS[i]
    img = Image.new("RGBA", (9, 9), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.ellipse([0, 0, 8, 8], fill=col + (255,), outline=(30, 24, 34, 255))
    dark = (40, 30, 40, 255)
    pts = {"leaf": [(4, 2), (5, 3), (5, 4), (4, 5), (3, 4), (3, 3), (4, 6)], "dot": [(4, 4)], "dot2": [(3, 4), (5, 4)],
           "star": [(4, 2), (3, 4), (4, 4), (5, 4), (2, 4), (6, 4), (3, 6), (5, 6), (4, 5)], "gem": [(4, 2), (3, 3), (5, 3), (2, 4), (6, 4), (3, 5), (5, 5), (4, 6)],
           "crown": [(2, 3), (4, 2), (6, 3), (2, 4), (3, 4), (4, 4), (5, 4), (6, 4), (2, 5), (3, 5), (4, 5), (5, 5), (6, 5)]}[sym]
    for p in pts:
        img.putpixel(p, dark)
    img.putpixel((2, 1), (255, 255, 255, 200))
    return img


# ------------------------------------------------------------------ icons.png (바닐라 기반 수정)
def icons_png(vanilla_path):
    img = Image.open(vanilla_path).convert("RGBA")
    # 하트(y0~9), 갑옷/탈것 하트(9~18), 산소(18~27), 배고픔(27~36), 하드코어 하트(45~54): x>=16 영역 투명 (조준점·핑 아이콘은 유지)
    for y in range(0, 54):
        for x in range(16, 256):
            img.putpixel((x, y), (0, 0, 0, 0))
    # 경험치 바 (배경 y64~68, 채움 y69~73, 폭 182) → 금색 테마
    for x in range(182):
        for y in range(5):
            edge = y in (0, 4) or x in (0, 181)
            img.putpixel((x, 64 + y), (70, 52, 20, 255) if edge else (26, 22, 30, 255))
            t = y / 4
            fill = (int(255 - 30 * t), int(222 - 90 * t), int(90 - 60 * t), 255)
            if y == 1:
                fill = (255, 245, 190, 255)
            img.putpixel((x, 69 + y), (120, 80, 20, 255) if edge else fill)
    return img


# ------------------------------------------------------------------ 메뉴별 배경 레이아웃 (Java GUI 배경 키와 일치)
GUI_LAYOUTS = {
    # 키: (글리프, 줄 수, 표시할 슬롯, 장식)
    "shop": ("\ue001", 6, set(range(45)) | {45, 48, 49, 50, 53}, "footer"),
    "enhance": ("\ue002", 3, {10, 12, 14, 16}, "enhance"),
    "rune": ("\ue003", 3, {11, 13, 15, 22}, "rune"),
    "stat": ("\ue004", 3, {4, 11, 13, 15, 22}, None),
    "potion": ("\ue005", 3, {10, 12, 14, 16, 22}, None),
    "quest": ("\ue006", 3, {4, 11, 13, 15, 18}, None),
    "settings": ("\ue007", 3, {10, 11, 12, 13, 14, 15, 18}, None),
    "spirit": ("\ue008", 6, {1, 2, 3, 4, 5, 10, 12, 14, 16, 18, 19, 20, 21, 23, 24, 25, 26, 27, 28, 29, 30, 32, 33, 34, 35, 38, 39, 40, 41, 49}, "spirit"),
}


def slot_xy(idx):
    return 7 + (idx % 9) * 18, 17 + (idx // 9) * 18


def decorate(img, kind, rows):
    d = ImageDraw.Draw(img)
    gold = (201, 161, 59, 255)
    faint = (86, 76, 104, 255)
    if kind == "footer":
        y = 17 + 5 * 18 - 2
        d.rectangle([4, y, 171, y + 21], outline=gold)
        d.line([(5, y - 3), (170, y - 3)], fill=(110, 84, 30, 255))
    if kind == "enhance":
        def sil(idx, pts):
            x0, y0 = slot_xy(idx)
            for (x, y) in pts:
                d.point((x0 + 1 + x, y0 + 1 + y), fill=faint)
        sil(10, [(4 + i, 11 - i) for i in range(9)] + [(5 + i, 11 - i) for i in range(8)] + [(2, 10), (3, 11), (4, 12), (2, 13), (1, 14)])
        sil(12, [(x, y) for y in range(3, 13) for x in range(4, 12) if y < 9 or abs(x - 7.5) < (13 - y)])
        sil(14, [(x, y) for x in range(4, 12) for y in range(4, 12) if (x - 7.5) ** 2 + (y - 7.5) ** 2 < 12])
        sil(16, [(x, y) for x in range(3, 13) for y in range(5, 8)] + [(x, y) for x in range(6, 10) for y in range(8, 11)] + [(x, 11) for x in range(4, 12)])
        for a, b in ((10, 12), (12, 14), (14, 16)):
            xa, ya = slot_xy(a)
            xb, _ = slot_xy(b)
            cx, cy = (xa + 18 + xb) // 2, ya + 9
            d.polygon([(cx - 2, cy - 3), (cx + 2, cy), (cx - 2, cy + 3)], fill=gold)
    if kind == "spirit":
        groups = [((1, 5), (170, 110, 255)), ((10, 10), (107, 216, 255)), ((12, 12), (244, 244, 244)), ((14, 14), (255, 122, 31)), ((16, 16), (47, 191, 113)),
                  ((18, 21), (107, 216, 255)), ((23, 26), (244, 244, 244)), ((27, 30), (255, 122, 31)), ((32, 35), (47, 191, 113)), ((38, 41), (176, 16, 48))]
        for (a, b), col in groups:
            xa, ya = slot_xy(a)
            xb, _ = slot_xy(b)
            d.rectangle([xa - 2, ya - 2, xb + 19, ya + 19], outline=col + (255,))
            d.rectangle([xa - 1, ya - 1, xb + 18, ya + 18], outline=tuple(int(v * 0.45) for v in col) + (255,))
    if kind == "rune":
        for idx in (11, 13, 15):
            x0, y0 = slot_xy(idx)
            d.ellipse([x0 - 3, y0 - 3, x0 + 20, y0 + 20], outline=(150, 100, 220, 255))
    return img


def gui_layout_images(gui_background):
    """build 스크립트의 gui_background(rows, highlight) 로 기본 판을 그리고 메뉴별 장식을 얹는다"""
    out = {}
    for key, (ch, rows, slots, kind) in GUI_LAYOUTS.items():
        img = gui_background(rows, slots)
        out[key] = (ch, rows, decorate(img, kind, rows))
    return out


# ------------------------------------------------------------------ 폰트 JSON
def space_advances():
    adv = {}
    for i in range(9):
        adv[chr(0xF801 + i)] = -(2 ** i)
        adv[chr(0xF821 + i)] = 2 ** i
    return adv


def write_hud(pack_dir, write_json):
    tex_dir = os.path.join(pack_dir, "assets", NS, "textures", "hud")
    os.makedirs(tex_dir, exist_ok=True)
    providers = [{"type": "space", "advances": space_advances()}]

    def add(name, img, asc, ch):
        img.save(os.path.join(tex_dir, name + ".png"))
        providers.append({"type": "bitmap", "file": NS + ":hud/" + name + ".png", "ascent": asc, "height": img.height, "chars": [chr(ch)]})

    for s in range(HP_STEPS + 1):
        add("hp_%02d" % s, hp_bar(s), ASC_ROW1, HP_BASE + s)
    for k, cp in ICON.items():
        add("icon_" + k, icon(k), ASC_ROW1 if cp < 0xE230 else ASC_ROW2, cp)
    names = {"/": "slash", ",": "comma", "%": "pct", ".": "dot", "+": "plus"}
    for i, ch in enumerate(DIGIT_CHARS):
        # 리소스 경로는 소문자/숫자/_ 만 허용 (대문자가 있으면 글꼴 전체가 로드 실패)
        n = names.get(ch, ch if ch.isdigit() else ch.lower() + ("_u" if ch.isupper() else "_l"))
        add("d1_" + n, digit_img(ch), ASC_DIG1, DIGITS_ROW1 + i)
        add("d2_" + n, digit_img(ch, (255, 235, 180, 255)), ASC_DIG2, DIGITS_ROW2 + i)
    write_json(os.path.join(pack_dir, "assets", NS, "font", "hud.json"), {"providers": providers})
    return len(providers)


def default_font_extra(pack_dir, sword_img):
    """기본 폰트에 넣을 로고/배지 provider 목록"""
    tex_dir = os.path.join(pack_dir, "assets", NS, "textures", "font")
    os.makedirs(tex_dir, exist_ok=True)
    lg = logo(sword_img)
    lg.save(os.path.join(tex_dir, "logo.png"))
    prov = [{"type": "bitmap", "file": NS + ":font/logo.png", "ascent": 12, "height": lg.height, "chars": [chr(LOGO)]}]
    for i in range(len(BADGE_TIERS)):
        b = badge(i)
        b.save(os.path.join(tex_dir, "badge_%d.png" % i))
        prov.append({"type": "bitmap", "file": NS + ":font/badge_%d.png" % i, "ascent": 7, "height": 9, "chars": [chr(BADGE_BASE + i)]})
    return prov, lg


# ------------------------------------------------------------------ 미리보기 (게임 화면 하단 합성)
def hud_preview(pack_dir, widgets_path, icons_img, out_path):
    """HudFont 와 같은 규칙으로 화면 하단을 합성해 배치를 검증"""
    W, H = 360, 90
    sc = 3
    img = Image.new("RGBA", (W, H), (96, 140, 70, 255))
    d = ImageDraw.Draw(img)
    for y in range(H):
        for x in range(W):
            if (x // 12 + y // 12) % 2 == 0:
                img.putpixel((x, y), (88, 132, 64, 255))
    cx = W // 2
    wid = Image.open(widgets_path).convert("RGBA")
    img.alpha_composite(wid.crop((0, 0, 182, 22)), (cx - 91, H - 22))
    img.alpha_composite(wid.crop((0, 22, 24, 46)), (cx - 91 - 1, H - 23))
    img.alpha_composite(icons_img.crop((0, 64, 182, 69)), (cx - 91, H - 29))
    img.alpha_composite(icons_img.crop((0, 69, 120, 74)), (cx - 91, H - 29))
    tex = os.path.join(pack_dir, "assets", NS, "textures", "hud")
    base = H - 65  # 액션바 기준선

    def put(name, x, asc):
        g = Image.open(os.path.join(tex, name + ".png")).convert("RGBA")
        img.alpha_composite(g, (x, base - asc))
        return g.width + 1

    x = cx - 91
    put("hp_15", x, ASC_ROW1)
    txt = "15230/20000"
    tw = sum(4 for _ in txt)
    tx = cx - 91 + 45 - tw // 2
    for ch in txt:
        tx += put("d1_" + ({"/": "slash"}.get(ch, ch)), tx, ASC_DIG1)  # noqa
    x = cx + 10
    x += put("icon_potion", x, ASC_ROW1)
    for ch in "12":
        x += put("d1_" + ch, x, ASC_DIG1)
    x += 3
    x += put("icon_skill_cd", x, ASC_ROW1)
    for ch in "8s":
        x += put("d1_" + ({"s": "s_l"}.get(ch, ch)), x, ASC_DIG1)
    x += 3
    x += put("icon_def", x, ASC_ROW1)
    for ch in "24.5%":
        x += put("d1_" + ({".": "dot", "%": "pct"}.get(ch, ch)), x, ASC_DIG1)
    x = cx - 91
    x += put("icon_sword", x, ASC_ROW2)
    for ch in "1284":
        x += put("d2_" + ch, x, ASC_DIG2)
    x = cx + 10
    x += put("icon_crit", x, ASC_ROW2)
    for ch in "32.5%":
        x += put("d2_" + ({".": "dot", "%": "pct"}.get(ch, ch)), x, ASC_DIG2)
    img.resize((W * sc, H * sc), Image.NEAREST).save(out_path)


# ------------------------------------------------------------------ 1.20.2+ HUD 스프라이트
HEART_KINDS = ["", "hardcore_", "absorbing_", "hardcore_absorbing_", "poisoned_", "hardcore_poisoned_", "withered_",
               "hardcore_withered_", "frozen_", "hardcore_frozen_"]


def write_modern_hud_sprites(pack_dir):
    """1.20.2 부터 icons.png 대신 gui/sprites/hud/* 를 쓰므로 같은 효과를 스프라이트로도 적용"""
    base = os.path.join(pack_dir, "assets", "minecraft", "textures", "gui", "sprites", "hud")
    empty9 = Image.new("RGBA", (9, 9), (0, 0, 0, 0))
    names = []
    for k in HEART_KINDS:
        for part in ("full", "half"):
            names += ["heart/%s%s" % (k, part), "heart/%s%s_blinking" % (k, part)]
    names += ["heart/container", "heart/container_blinking", "heart/container_hardcore", "heart/container_hardcore_blinking",
              "heart/vehicle_container", "heart/vehicle_full", "heart/vehicle_half",
              "food_empty", "food_empty_hunger", "food_full", "food_full_hunger", "food_half", "food_half_hunger",
              "armor_empty", "armor_half", "armor_full", "air", "air_bursting", "air_empty"]
    for n in names:
        p = os.path.join(base, n + ".png")
        os.makedirs(os.path.dirname(p), exist_ok=True)
        empty9.save(p)
    bg = Image.new("RGBA", (182, 5), (26, 22, 30, 255))
    fg = Image.new("RGBA", (182, 5), (0, 0, 0, 0))
    for x in range(182):
        for y in range(5):
            edge = y in (0, 4) or x in (0, 181)
            if edge:
                bg.putpixel((x, y), (70, 52, 20, 255))
            t = y / 4
            c = (255, 245, 190, 255) if y == 1 else (int(255 - 30 * t), int(222 - 90 * t), int(90 - 60 * t), 255)
            fg.putpixel((x, y), (120, 80, 20, 255) if edge else c)
    bg.save(os.path.join(base, "experience_bar_background.png"))
    fg.save(os.path.join(base, "experience_bar_progress.png"))


# ------------------------------------------------------------------ 1.21.4+ 아이템 정의 (items/*.json)
_ITEM_FALLBACK = {
    "potion": {"type": "minecraft:model", "model": "minecraft:item/potion", "tints": [{"type": "minecraft:potion", "default": -13083194}]},
    "firework_star": {"type": "minecraft:model", "model": "minecraft:item/firework_star",
                      "tints": [{"type": "minecraft:constant", "value": -1}, {"type": "minecraft:firework", "default": -7697782}]},
    "shield": {"type": "minecraft:condition", "property": "minecraft:using_item",
               "on_true": {"type": "minecraft:special", "base": "minecraft:item/shield_blocking", "model": {"type": "minecraft:shield"}},
               "on_false": {"type": "minecraft:special", "base": "minecraft:item/shield", "model": {"type": "minecraft:shield"}}},
}


def write_item_definitions(pack_dir, overrides, write_json):
    """1.21.4 부터 models/item 의 overrides 대신 items/<아이템>.json 의 range_dispatch 로 CustomModelData 를 연결한다.
    1.21.3 이하 클라이언트는 이 폴더를 무시하므로 함께 넣어도 안전하다."""
    skip = {"amethyst_cluster", "leather_horse_armor"} | {m + "_" + p for m in ("leather", "netherite") for p in ("helmet", "chestplate", "leggings", "boots")}
    for vanilla, lst in overrides.items():
        if vanilla in skip:  # 1.21.4+ 방어구 정의는 트림 구조가 복잡해 기본값 유지
            continue
        fallback = _ITEM_FALLBACK.get(vanilla, {"type": "minecraft:model", "model": "minecraft:item/" + vanilla})
        entries = [{"threshold": cmd, "model": {"type": "minecraft:model", "model": model}} for cmd, model in sorted(lst)]
        write_json(os.path.join(pack_dir, "assets", "minecraft", "items", vanilla + ".json"),
                   {"model": {"type": "minecraft:range_dispatch", "property": "minecraft:custom_model_data", "index": 0,
                              "entries": entries, "fallback": fallback}})




# ------------------------------------------------------------------ 사이드바 빨간 점수 숨김 (1.20.1 코어 셰이더, GLSL 은 ASCII 만)
HIDE_SCORE_SNIPPET = """
    // RpgCraft: hide red sidebar score numbers (0xFF5555) drawn at the far right edge of the GUI.
    if (abs(gl_Position.w - 1.0) < 0.0001 && gl_Position.x > 0.88
            && Color.r > 0.99 && abs(Color.g - 0.3333) < 0.01 && abs(Color.b - 0.3333) < 0.01) {
        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
    }
"""


def write_score_shader(pack_dir, vanilla_vsh):
    src = open(vanilla_vsh, encoding="utf-8").read().rstrip()
    assert src.endswith("}"), "unexpected vanilla shader"
    out = src[:-1].rstrip() + "\n" + HIDE_SCORE_SNIPPET + "}\n"
    d = os.path.join(pack_dir, "assets", "minecraft", "shaders", "core")
    os.makedirs(d, exist_ok=True)
    with open(os.path.join(d, "rendertype_text.vsh"), "w", encoding="utf-8", newline="\n") as f:
        f.write(out)


# ------------------------------------------------------------------ 파티 HUD (화면 왼쪽)
PARTY_ROWS = 5
PARTY_TOP = 150          # 첫 줄 이름의 ascent (액션바 기준선 위로)
PARTY_GAP = 19           # 줄 간격 (작게)
PARTY_STEPS = 20
# 글리프: E300 파티원 아이콘, E301 파티장 아이콘, E310~E324 체력 바, E330~E344 경험치 바


def ascii_widths(vanilla_dir):
    """바닐라 ascii.png 에서 0x20~0x7E 글자 폭(advance) 계산 → Java 표"""
    img = Image.open(os.path.join(vanilla_dir, "ascii.png")).convert("RGBA")
    cell = img.width // 16
    out = []
    for code in range(0x20, 0x7F):
        if code == 0x20:
            out.append(4)
            continue
        cx, cy = (code % 16) * cell, (code // 16) * cell
        w = 0
        for x in range(cell):
            if any(img.getpixel((cx + x, cy + y))[3] > 0 for y in range(cell)):
                w = x + 1
        out.append(int(w * 8 / cell) + 1)
    return out


def _bar(step, w, h, full, empty, edge):
    img = Image.new("RGBA", (w, h), edge)
    fill = round((w - 2) * step / PARTY_STEPS)
    for x in range(1, w - 1):
        for y in range(1, h - 1):
            img.putpixel((x, y), full if x - 1 < fill else empty)
    return _full_width(img)


def _person(leader):
    img = Image.new("RGBA", (9, 9), (0, 0, 0, 0))  # (7px 로 줄여 사용)
    col = (255, 214, 64, 255) if leader else (140, 200, 255, 255)
    rows = ["000111000", "001111100", "001111100", "000111000", "011111110", "111111111", "111111111", "111111111", "000000000"]
    if leader:
        rows[0] = "101010101"
        rows[1] = "111111111"
    for y, r in enumerate(rows):
        for x, v in enumerate(r):
            if v == "1":
                img.putpixel((x, y), col)
    return _full_width(img)


PAD = 176  # 글리프 이미지 전체 높이 (마인크래프트 규칙: ascent 는 height 보다 클 수 없음 → 아래쪽을 투명하게 채워 높임)


def _pad(img):
    out = Image.new("RGBA", (img.width, PAD), (0, 0, 0, 0))
    out.paste(img, (0, 0))
    if out.getpixel((out.width - 1, 0))[3] == 0:
        out.putpixel((out.width - 1, 0), (0, 0, 0, 1))
    return out


def write_party_fonts(pack_dir, write_json, vanilla_dir):
    """파티 HUD 글꼴 (줄마다 높이가 다른 5개). 이전 버전은 ascent 가 height 보다 커서 글꼴 전체가 깨졌음 → 모든 글리프를 PAD 높이로"""
    tex = os.path.join(pack_dir, "assets", NS, "textures", "hud", "party")
    os.makedirs(tex, exist_ok=True)
    _pad(_person(False).resize((7, 7), Image.NEAREST)).save(os.path.join(tex, "member.png"))
    _pad(_person(True).resize((7, 7), Image.NEAREST)).save(os.path.join(tex, "leader.png"))
    for s in range(PARTY_STEPS + 1):
        hp = _bar(s, 41, 3, (235, 60, 70, 255), (60, 20, 25, 220), (20, 10, 14, 220))
        xp = _bar(s, 41, 2, (255, 214, 64, 255), (50, 44, 20, 220), (20, 16, 8, 220))
        # 체력 바는 이름 아래 9px, 경험치 바는 13px (작은 표시)
        hp2 = Image.new("RGBA", (41, 12), (0, 0, 0, 0)); hp2.paste(hp, (0, 9))
        xp2 = Image.new("RGBA", (41, 15), (0, 0, 0, 0)); xp2.paste(xp, (0, 13))
        _pad(hp2).save(os.path.join(tex, "hp_%02d.png" % s))
        _pad(xp2).save(os.path.join(tex, "xp_%02d.png" % s))
    # 바닐라 ascii 글꼴을 칸마다 아래로 늘린 사본
    src = Image.open(os.path.join(vanilla_dir, "ascii.png")).convert("RGBA")
    cell = src.width // 16
    tall = Image.new("RGBA", (src.width, PAD * 16), (0, 0, 0, 0))
    scale = 8 / cell
    for row in range(16):
        strip = src.crop((0, row * cell, src.width, (row + 1) * cell))
        if cell != 8:
            strip = strip.resize((int(src.width * scale), 8), Image.NEAREST)
        tall.paste(strip, (0, row * PAD))
    if cell != 8:
        tall = tall.crop((0, 0, int(src.width * scale), PAD * 16))
    tall.save(os.path.join(tex, "ascii_tall.png"))
    inc = json.load(open(os.path.join(vanilla_dir, "include_default.json"), encoding="utf-8"))
    ascii_p = [p for p in inc["providers"] if p.get("file") == "minecraft:font/ascii.png"][0]
    for row in range(PARTY_ROWS):
        a = PARTY_TOP - row * PARTY_GAP
        prov = [{"type": "space", "advances": dict(space_advances(), **{" ": 4})},
                {"type": "bitmap", "file": NS + ":hud/party/ascii_tall.png", "ascent": a, "height": PAD, "chars": ascii_p["chars"]},
                {"type": "bitmap", "file": NS + ":hud/party/member.png", "ascent": a, "height": PAD, "chars": ["\ue300"]},
                {"type": "bitmap", "file": NS + ":hud/party/leader.png", "ascent": a, "height": PAD, "chars": ["\ue301"]}]
        for s in range(PARTY_STEPS + 1):
            prov.append({"type": "bitmap", "file": NS + ":hud/party/hp_%02d.png" % s, "ascent": a, "height": PAD, "chars": [chr(0xE310 + s)]})
            prov.append({"type": "bitmap", "file": NS + ":hud/party/xp_%02d.png" % s, "ascent": a, "height": PAD, "chars": [chr(0xE330 + s)]})
        for p in prov:
            if p["type"] == "bitmap":
                assert p["ascent"] <= p["height"], p
        write_json(os.path.join(pack_dir, "assets", NS, "font", "party%d.json" % row), {"providers": prov})


# ------------------------------------------------------------------ 보스 체력바 (v5.1.0 디자인)
# 보스바 색 → (진한 색, 기본 색, 밝은 색). WHITE 는 나침반 문구용 투명 바라서 그리지 않는다.
BOSS_BAR_COLORS = {
    "pink": ((150, 30, 100), (255, 95, 185), (255, 190, 230)),
    "blue": ((20, 70, 170), (70, 160, 255), (185, 230, 255)),
    "red": ((130, 10, 20), (235, 45, 55), (255, 160, 140)),
    "green": ((20, 110, 40), (75, 215, 100), (190, 255, 190)),
    "yellow": ((160, 110, 10), (255, 205, 55), (255, 245, 180)),
    "purple": ((80, 20, 150), (175, 85, 255), (230, 190, 255)),
}
BAR_W, BAR_H = 182, 5
FRAME_W, FRAME_H, FRAME_MARGIN = 202, 11, 10   # 보스바 둘레 장식 틀 (바 양옆 10px)
BOSS_FRAME = 0xE050


def _lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def boss_bar_images(dark, mid, light):
    """(배경, 채움) 182x5. 배경: 어두운 테두리 + 색이 비치는 홈. 채움: 위 밝게·아래 진하게 + 왼→오 그라데이션 + 사선 광택"""
    bg = Image.new("RGBA", (BAR_W, BAR_H), (0, 0, 0, 0))
    fg = Image.new("RGBA", (BAR_W, BAR_H), (0, 0, 0, 0))
    edge = (18, 10, 14, 255)
    groove = _lerp((12, 8, 10), dark, 0.35)
    for x in range(BAR_W):
        for y in range(BAR_H):
            if y in (0, 4) or x in (0, BAR_W - 1):
                bg.putpixel((x, y), edge)
            else:
                g = _lerp(groove, (0, 0, 0), 0.25) if y == 3 else groove
                bg.putpixel((x, y), g + (240,))
            if y in (0, 4) or x == 0 or x == BAR_W - 1:
                continue   # 채움은 테두리 안쪽만 → 조금만 차 있어도 테두리가 깨지지 않음
            t = x / (BAR_W - 1)
            base = _lerp(_lerp(mid, dark, 0.35), mid, min(1, t * 1.4))       # 왼쪽이 약간 진함
            row = {1: _lerp(base, light, 0.55), 2: base, 3: _lerp(base, dark, 0.55)}[y]
            if (x + y * 2) % 9 == 0 and y < 3:                                 # 사선 광택
                row = _lerp(row, (255, 255, 255), 0.35)
            fg.putpixel((x, y), row + (255,))
    return bg, fg


def boss_bar_notches(n):
    """마디 오버레이 (배경용, 채움용)"""
    bg = Image.new("RGBA", (BAR_W, BAR_H), (0, 0, 0, 0))
    fg = Image.new("RGBA", (BAR_W, BAR_H), (0, 0, 0, 0))
    for i in range(1, n):
        x = round(i * BAR_W / n)
        for y in (1, 2, 3):
            bg.putpixel((x, y), (0, 0, 0, 150))
            fg.putpixel((x, y), (10, 4, 8, 170))
            if x + 1 < BAR_W - 1:
                fg.putpixel((x + 1, y), (255, 255, 255, 55))
    return bg, fg


def write_boss_bars(pack_dir, vanilla_bars_path):
    """1.20.1: textures/gui/bars.png / 1.20.2+: textures/gui/sprites/boss_bar/*.png"""
    order = ["pink", "blue", "red", "green", "yellow", "purple", "white"]
    bars = Image.open(vanilla_bars_path).convert("RGBA")
    sp = os.path.join(pack_dir, "assets", "minecraft", "textures", "gui", "sprites", "boss_bar")
    os.makedirs(sp, exist_ok=True)
    clear = Image.new("RGBA", (BAR_W, BAR_H), (0, 0, 0, 0))
    for i, name in enumerate(order):
        if name == "white":   # 나침반 문구 전용 → 투명
            bg = fg = clear
        else:
            bg, fg = boss_bar_images(*BOSS_BAR_COLORS[name])
        bars.paste(bg, (0, i * 10))
        bars.paste(fg, (0, i * 10 + 5))
        bg.save(os.path.join(sp, name + "_background.png"))
        fg.save(os.path.join(sp, name + "_progress.png"))
    for k, n in enumerate((6, 10, 12, 20)):
        bg, fg = boss_bar_notches(n)
        bars.paste(bg, (0, 80 + k * 10))
        bars.paste(fg, (0, 85 + k * 10))
        bg.save(os.path.join(sp, "notched_%d_background.png" % n))
        fg.save(os.path.join(sp, "notched_%d_progress.png" % n))
    bars.save(os.path.join(pack_dir, "assets", "minecraft", "textures", "gui", "bars.png"))
    return bars


def boss_frame():
    """보스바를 감싸는 금속 틀 (제목 글리프). 가운데 창은 투명 → 바가 보인다.
    글리프 윗부분 두 줄은 보스 이름 글자와 겹치므로 양 끝 장식만 그린다."""
    img = Image.new("RGBA", (FRAME_W, FRAME_H), (0, 0, 0, 0))
    gold, gold_d, gold_l, dark = (214, 170, 72, 255), (120, 84, 30, 255), (255, 232, 150, 255), (22, 14, 18, 255)
    L, R = FRAME_MARGIN - 1, FRAME_MARGIN + BAR_W          # 9 / 192 : 바 바로 바깥 세로줄
    for x in range(L - 1, R + 2):                           # 위·아래 테두리 (2줄: 금 + 그림자)
        img.putpixel((x, 2), gold_l if x % 6 == 0 else gold)
        img.putpixel((x, 8), gold)
        img.putpixel((x, 9), gold_d)
    for y in range(2, 10):                                  # 양옆 세로 테두리
        for x, c in ((L - 1, dark), (L, gold), (R, gold), (R + 1, dark)):
            img.putpixel((x, y), c)
    # 왼쪽 끝: 해골 문장
    skull = ["..###..", ".#####.", "##.#.##", "#######", ".##.##.", "..#.#.."]
    for yy, row in enumerate(skull):
        for xx, ch in enumerate(row):
            if ch == "#":
                img.putpixel((xx, 2 + yy), (235, 228, 210, 255))
            elif ch == "." and 0 < xx < 6 and 0 < yy < 5:
                img.putpixel((xx, 2 + yy), dark)
    for x in range(0, 7):
        img.putpixel((x, 8), gold)
        img.putpixel((x, 9), gold_d)
    # 오른쪽 끝: 날개 장식
    wing = ["....##", "..####", ".#####", "######", "..####", "....##"]
    for yy, row in enumerate(wing):
        for xx, ch in enumerate(row):
            if ch == "#":
                img.putpixel((R + 2 + xx, 2 + yy), gold if (xx + yy) % 3 else gold_l)
    # 양 끝 위 뾰족 장식 (이름 글자와 겹치지 않는 가장자리)
    for x0 in (L - 3, R + 1):
        img.putpixel((x0 + 1, 0), gold_l)
        img.putpixel((x0 + 1, 1), gold)
    # 아래 가운데 보석
    c = FRAME_W // 2
    for dx, dy, col in ((0, 8, (255, 60, 60, 255)), (-1, 9, (200, 20, 30, 255)), (0, 9, (255, 120, 110, 255)), (1, 9, (200, 20, 30, 255)), (0, 10, (150, 10, 20, 255))):
        img.putpixel((c + dx, dy), col)
    for dx in (-3, -2, 2, 3):
        img.putpixel((c + dx, 9), gold_l)
    return img


def boss_frame_provider(pack_dir):
    """기본 폰트에 넣을 보스바 틀 글리프 (제목 글자 기준 아래로: ascent 0 → 틀 윗줄이 바 위 2px)"""
    tex_dir = os.path.join(pack_dir, "assets", NS, "textures", "font")
    os.makedirs(tex_dir, exist_ok=True)
    boss_frame().save(os.path.join(tex_dir, "boss_frame.png"))
    # 제목 글자 y 에서 바는 +9 ~ +13. 틀 이미지의 바 창(3~7행)이 거기에 오도록 윗변 = +6 → ascent 1
    return {"type": "bitmap", "file": NS + ":font/boss_frame.png", "ascent": 1, "height": FRAME_H, "chars": [chr(BOSS_FRAME)]}


def boss_bar_preview(out_path, scale=4):
    """보스바 디자인 미리보기 (틀 + 색별 바 + 마디)"""
    names = ["red", "purple", "blue", "yellow", "green", "pink"]
    fills = [0.85, 0.6, 0.35, 1.0, 0.15, 0.5]
    gap = 6
    img = Image.new("RGBA", (FRAME_W + 16, len(names) * (FRAME_H + gap) + gap), (72, 96, 60, 255))
    for i, name in enumerate(names):
        bg, fg = boss_bar_images(*BOSS_BAR_COLORS[name])
        nb, nf = boss_bar_notches(10)
        bar = Image.new("RGBA", (BAR_W, BAR_H), (0, 0, 0, 0))
        bar.alpha_composite(bg)
        bar.alpha_composite(nb)
        prog = Image.new("RGBA", (BAR_W, BAR_H), (0, 0, 0, 0))
        prog.alpha_composite(fg)
        prog.alpha_composite(nf)
        bar.alpha_composite(prog.crop((0, 0, int(BAR_W * fills[i]), BAR_H)))
        row = Image.new("RGBA", (FRAME_W, FRAME_H), (0, 0, 0, 0))
        row.alpha_composite(bar, (FRAME_MARGIN, 3))
        row.alpha_composite(boss_frame())
        img.alpha_composite(row, (8, gap + i * (FRAME_H + gap)))
    img.resize((img.width * scale, img.height * scale), Image.NEAREST).save(out_path)
