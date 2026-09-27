"""
모든 방어구 전용 아이콘 (전사·암살자·모험가 5단계, 보스 세트 5종, 확장 세트 3종)
spirit_art 의 방어구 틀(투구·갑옷·바지·신발)에 세트별 재질 무늬·테두리·장식을 입힌다.
CustomModelData 는 Java ItemRegistry 와 일치해야 한다.
"""
import spirit_art as S
from pack_art import Canvas, palette, poly_xy, gem_xy, disc_xy

SLOTS = ["helmet", "chestplate", "leggings", "boots"]

PAT = {
    "chain": lambda x, y: (x + y) % 3 == 0 or (x - y) % 3 == 0,
    "plate": lambda x, y: y % 6 == 0,
    "stitch": lambda x, y: (x % 7 == 0 and y % 2 == 0) or (y % 9 == 0 and x % 2 == 0),
    "patch": lambda x, y: (x // 6 + y // 6) % 3 == 0 and (x % 6 in (0, 5) or y % 6 in (0, 5)),
    "feather": lambda x, y: y % 4 == 0 and (x + y // 4) % 3 != 0,
    "scale": lambda x, y: (x + (y // 2 % 2) * 2) % 4 == 0 and y % 2 == 0,
    "crack": lambda x, y: (x * 7 + y * 3) % 13 == 0 or (x - y) % 9 == 0,
    "fur": lambda x, y: (x * 5 + y * 3) % 7 == 0,
    "rune": lambda x, y: (x % 8 == 3 and y % 4 != 0) or (y % 8 == 4 and x % 3 == 0),
}

# set_id: (몸 색, 테두리/장식 색, 보석 색, 무늬, 투구 장식)
SETS = {
    "warrior_0": ("8a9098", "6b6b6b", "c8ced6", "chain", None),
    "warrior_1": ("70767e", "c9a13b", "e0b030", "chain", None),
    "warrior_2": ("c8ced6", "e0b030", "e04848", "plate", "crest"),
    "warrior_3": ("8a6ab8", "d0c0ff", "e080ff", "plate", "crest"),
    "warrior_4": ("4a8ad0", "a8e0ff", "7fffff", "plate", "wings"),
    "assassin_0": ("3c5aa6", "1e2a55", "a8c0ff", "stitch", "hood"),
    "assassin_1": ("2b3f7a", "141c3a", "7fa0ff", "stitch", "hood"),
    "assassin_2": ("1e2a55", "0a0f24", "5f7fff", "stitch", "hood"),
    "assassin_3": ("7a1e1e", "2a0808", "ff4040", "stitch", "hood"),
    "assassin_4": ("5a1010", "e0b030", "ffd23f", "stitch", "hood"),
    "adventurer_0": ("9c7a4a", "5a3a1a", "e0c080", "patch", None),
    "adventurer_1": ("7a5a30", "4a2a10", "e0c080", "patch", None),
    "adventurer_2": ("6b4a2a", "3a2210", "7fc0ff", "patch", "goggles"),
    "adventurer_3": ("556b2f", "2a3a14", "e0b030", "patch", "goggles"),
    "adventurer_4": ("3e5a2a", "c9a13b", "7fffb0", "patch", "goggles"),
    "witch": ("5b2c6f", "7dff6a", "b8ff3a", "crack", "hat"),
    "dwarf": ("e0b030", "8a5a1a", "3f7fff", "plate", "crown"),
    "harpy": ("e9e6de", "8fd3ff", "3fb8ff", "feather", "wings"),
    "sea": ("1f7a8c", "ff9f8a", "f2efe6", "scale", "fin"),
    "bungbung": ("3a2a2a", "ff8a1f", "ffd23f", "crack", "horns"),
    "beast": ("7a4a22", "3a2210", "e0c080", "fur", "ears"),
    "arcane": ("5a2a9a", "d0a0ff", "f0c0ff", "rune", "hood"),
    "knight": ("c8ced6", "3f7fff", "a8e0ff", "plate", "crest"),
    "trans_1": ("e8d8b0", "ffd23f", "ff9a3a", "plate", "crest"),
    "trans_2": ("8a5ac0", "e0b0ff", "ff7ae0", "scale", "wings"),
    "trans_3": ("4a8ad0", "e070ff", "ffffff", "rune", "crown"),
    "trans_4": ("2a1a3a", "9a4ad0", "e080ff", "crack", "horns"),
    "trans_5": ("f4f4f8", "ffd23f", "7fe8ff", "plate", "wings"),
}


def catalog():
    """(바닐라 재료, CMD, 모델 이름, set_id, slot)"""
    out = []
    for t in range(5):
        mat = "chainmail" if t <= 1 else "iron" if t <= 3 else "diamond"
        for s in range(4):
            out.append((mat + "_" + SLOTS[s], 560 + t * 4 + s, "warrior_%d_%d" % (t, s), "warrior_%d" % t, s))
            out.append(("leather_" + SLOTS[s], 580 + t * 4 + s, "assassin_%d_%d" % (t, s), "assassin_%d" % t, s))
            out.append(("leather_" + SLOTS[s], 600 + t * 4 + s, "adventurer_%d_%d" % (t, s), "adventurer_%d" % t, s))
    for i, (sid, mat) in enumerate([("witch", "leather"), ("dwarf", "golden"), ("harpy", "chainmail"), ("sea", "leather"), ("bungbung", "netherite")]):
        for s in range(4):
            out.append((mat + "_" + SLOTS[s], 620 + i * 4 + s, "%s_a%d" % (sid, s), sid, s))
    for t in range(5):
        for s in range(4):
            out.append(("netherite_" + SLOTS[s], 1200 + t * 4 + s, "trans_armor_%d_%d" % (t + 1, s), "trans_%d" % (t + 1), s))
    for i, (sid, mat) in enumerate([("beast", "leather"), ("arcane", "leather"), ("knight", "iron")]):
        for s in range(4):
            out.append((mat + "_" + SLOTS[s], 640 + i * 4 + s, "set_%s_%d" % (sid, s), sid, s))
    return out


SPIRIT = {
    "qinglong": ("2e6bd9", "ffd23f", "8fe8ff", "scale", "horns"),
    "baihu": ("ececec", "9aa3ad", "3fa9ff", "fur", "ears"),
    "zhuque": ("d9352e", "ffc93c", "ffe066", "feather", "plume"),
    "xuanwu": ("2e8c4a", "c9a13b", "a0ffcf", "scale", "fin"),
    "fiend": ("241a2a", "b01030", "e080ff", "crack", "horns"),
}
SETS.update(SPIRIT)
METAL = {"plate", "chain", "crack", "scale"}


def _ell(cx, cy, rx, ry):
    return lambda x, y: ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2 <= 1


def armor(set_id, slot):
    """갑옷 형태를 부위별로 조립: 판금 층, 어깨 보호대, 리벳, 벨트, 무릎·발끝 보호대, 세트 장식"""
    body, rim, gem, pat, deco = SETS[set_id]
    metal = pat in METAL
    pal = palette(body, guard=rim, rim=rim, accent=gem, glow=gem)
    pal["x"] = S.A.mul(pal["b"], 0.7)
    pal["X"] = S.A.mul(pal["b"], 1.18)
    cv = Canvas(pal)
    area = set()
    P = lambda pred, k: area.update(cv.fill_xy(pred, k))
    trim = set()

    if slot == 0:  # ---------------- 투구
        if deco == "hat":
            P(lambda x, y: 3 <= x <= 29 and 23 <= y <= 27, "b")
            area |= set(poly_xy(cv, [(9, 24), (23, 24), (18, 10), (23, 2), (13, 9)], "b"))
            trim |= set(cv.fill_xy(lambda x, y: 9.5 <= x <= 22.5 and 20.5 <= y <= 23, "g"))
        else:
            P(lambda x, y: _ell(16, 14, 10, 10)(x, y) and y <= 14, "b")            # 돔
            P(lambda x, y: 6 <= x <= 26 and 14 <= y <= 24, "b")                      # 볼 가리개
            trim |= set(cv.fill_xy(lambda x, y: (x, y) in {(int(a), int(b)) for a, b in area} and 11 <= y <= 12.5, "g"))  # 이마 띠
            if metal:
                cv.fill_xy(lambda x, y: 14.5 <= x <= 17.5 and 7 <= y <= 20, "g")    # 코 가리개
                trim |= set(cv.fill_xy(lambda x, y: 14.5 <= x <= 17.5 and 7 <= y <= 20, "g"))
                cv.fill_xy(lambda x, y: (9 <= x <= 14 or 18 <= x <= 23) and 15 <= y <= 16.5, "o")   # 눈 틈
                for bx in (10, 12, 20, 22):
                    cv.fill_xy(lambda x, y, bx=bx: bx <= x < bx + 1 and 19 <= y <= 22, "o")       # 숨구멍
            else:
                cv.fill_xy(lambda x, y: 9 <= x <= 23 and 15 <= y <= 17, "o")
            for rx in (8, 24):
                gem_xy(cv, rx, 12, 0.9, "E")                                          # 리벳
    elif slot == 1:  # ---------------- 갑옷
        P(lambda x, y: 9 <= x <= 23 and 7 <= y <= 27, "b")                            # 몸통
        P(lambda x, y: _ell(16, 27, 7.5, 2.6)(x, y), "b")
        for cx in (7.5, 24.5):                                                        # 어깨 보호대
            P(_ell(cx, 10, 5.4, 4.2), "b")
            trim |= set(cv.fill_xy(lambda x, y, cx=cx: _ell(cx, 10, 5.4, 4.2)(x, y) and not _ell(cx, 10.8, 4.2, 3.0)(x, y) and y >= 10, "g"))
            gem_xy(cv, cx, 9, 0.9, "E")
        trim |= set(cv.fill_xy(lambda x, y: 11 <= x <= 21 and 6 <= y <= 8, "g"))       # 목깃
        if metal:
            cv.fill_xy(lambda x, y: 15.5 <= x <= 16.5 and 9 <= y <= 20, "x")           # 가슴판 가운데 선
            for yy in (21, 24):
                cv.fill_xy(lambda x, y, yy=yy: 10 <= x <= 22 and yy <= y < yy + 1, "x")  # 복부 판
        trim |= set(cv.fill_xy(lambda x, y: 9 <= x <= 23 and 18 <= y <= 19.6, "g"))    # 벨트
        gem_xy(cv, 16, 14, 2.2, "e")                                                  # 가슴 보석
        gem_xy(cv, 16, 18.8, 1.0, "E")
    elif slot == 2:  # ---------------- 바지
        P(lambda x, y: 8 <= x <= 24 and 4 <= y <= 11, "b")
        P(lambda x, y: (8 <= x <= 15 or 17 <= x <= 24) and 11 <= y <= 28, "b")
        trim |= set(cv.fill_xy(lambda x, y: 8 <= x <= 24 and 4 <= y <= 6, "g"))
        for kx in (11.5, 20.5):
            P(_ell(kx, 17, 3.4, 2.6), "b")
            trim |= set(cv.fill_xy(_ell(kx, 17, 3.4, 2.6), "g"))                      # 무릎 보호대
            gem_xy(cv, kx, 17, 0.9, "e")
        gem_xy(cv, 16, 5, 1.2, "e")
    else:  # ---------------- 신발
        for x0 in (3, 18):
            P(lambda x, y, x0=x0: x0 + 1 <= x <= x0 + 9 and 10 <= y <= 24, "b")
            P(lambda x, y, x0=x0: x0 <= x <= x0 + 11 and 22 <= y <= 27, "b")
            trim |= set(cv.fill_xy(lambda x, y, x0=x0: x0 + 1 <= x <= x0 + 9 and 10 <= y <= 12.5, "g"))   # 발목 띠
            trim |= set(cv.fill_xy(lambda x, y, x0=x0: x0 + 7 <= x <= x0 + 11 and 22 <= y <= 25, "g"))    # 발끝 보호대
            cv.fill_xy(lambda x, y, x0=x0: x0 <= x <= x0 + 11 and 26.5 <= y <= 27.5, "o")                  # 밑창
            gem_xy(cv, x0 + 5, 11, 0.8, "e")

    # 재질 무늬 (몸판에만)
    p = PAT[pat]
    for (x, y) in area:
        if (x, y) not in trim and cv.get(x, y) == "b" and p(x, y):
            cv.set(x, y, "x")
    S._dome(cv, area)
    for (x, y) in area:  # 윤곽 안쪽 밝은 테
        if cv.get(x, y) == "b" and ((x, y - 1) not in area or (x - 1, y) not in area):
            cv.set(x, y, "X")

    if slot == 0:  # 투구 장식
        if deco == "crest":
            poly_xy(cv, [(14, 5), (18, 5), (20, -1), (16, 1), (12, -1)], "e")
        elif deco == "wings":
            poly_xy(cv, [(7, 11), (1, 3), (3, 7), (0, 8), (5, 12)], "g"); poly_xy(cv, [(25, 11), (31, 3), (29, 7), (32, 8), (27, 12)], "g")
        elif deco == "goggles":
            gem_xy(cv, 12, 9, 2.0, "e"); gem_xy(cv, 20, 9, 2.0, "e")
        elif deco == "crown":
            for x in (8, 13.5, 19):
                poly_xy(cv, [(x, 6), (x + 5, 6), (x + 2.5, 0)], "g")
            gem_xy(cv, 16, 4.5, 1.2, "e")
        elif deco == "fin":
            poly_xy(cv, [(14, 5), (18, 5), (22, -1), (11, 0)], "g")
        elif deco == "horns":
            poly_xy(cv, [(8, 9), (1, 3), (3, 0), (10, 6)], "g"); poly_xy(cv, [(24, 9), (31, 3), (29, 0), (22, 6)], "g")
        elif deco == "ears":
            poly_xy(cv, [(8, 7), (6, 0), (12, 4)], "b"); poly_xy(cv, [(24, 7), (26, 0), (20, 4)], "b")
        elif deco == "plume":
            poly_xy(cv, [(15, 5), (17, 5), (22, -1), (19, 0), (16, -2), (13, 0), (10, -1)], "E")
        elif deco == "hood":
            cv.fill_xy(lambda x, y: 6 <= x <= 26 and 23 <= y <= 25, "x")
        elif deco == "hat":
            gem_xy(cv, 16, 21.8, 1.3, "e")
        if set_id == "fiend":
            gem_xy(cv, 12, 15.5, 1.2, "c"); gem_xy(cv, 20, 15.5, 1.2, "c")
    if slot == 1 and set_id == "fiend":  # 도철의 입
        cv.fill_xy(lambda x, y: 11 <= x <= 21 and 21 <= y <= 25, "o")
        for x in range(11, 22, 2):
            cv.set(x, 21, "h"); cv.set(x + 1, 25, "h")
    cv.outline()
    if set_id in SPIRIT and set_id != "fiend":
        cv.sparkle(28, 3, 1)
    return cv.render()
