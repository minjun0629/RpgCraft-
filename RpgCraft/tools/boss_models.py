"""
보스 3D 모델 (리소스팩)

블록벤치처럼 직육면체(element)를 조립해 보스 9종의 입체 모델을 만든다.
색은 16x16 팔레트 텍스처의 한 칸씩을 참조하고, 면 방향별 음영은 게임이 자동으로 준다.
모델 좌표: x·z 0~16 이 한 블록 폭(가운데 8), y 0 = 발밑. 앞(얼굴)은 +z(south) 방향.
플러그인은 보스 몹을 투명하게 만들고 이 모델(PAPER, CustomModelData 9000+)을 ItemDisplay 로 붙여 따라다니게 한다.
"""
import json
import os
from PIL import Image

BOSS_ORDER = ["witch", "elf_queen", "dwarf_king", "harpy_queen", "sea_gatekeeper", "bungbung", "desert_nightmare", "siphonia", "kain",
              "frost_queen", "volcano_giant", "void_apostle", "thunder_god", "primordial_dragon", "vengeful_spirit", "balrog",
              "megalodon", "kraken",
              "mount_wolf", "mount_lizard", "mount_warhorse", "mount_icebear", "mount_lion", "mount_panther", "mount_griffin", "mount_dragon",
              "pet_slime", "pet_chick", "pet_bunny", "pet_fox", "pet_penguin", "pet_owl", "pet_golem", "pet_fairy", "pet_ghost",
              "pet_phoenix", "pet_dragon", "pet_star",
              # v5.4.9 필드 보스 (뒤에 붙여 기존 CustomModelData 번호 유지)
              "field_boar_king", "field_frost_bear", "field_bandit_lord", "field_ravager", "field_ancient_golem",
              "field_swamp_witch", "field_flame_knight", "field_deep_warden", "field_frost_lich"]
BOSS_IDS = {"witch", "elf_queen", "dwarf_king", "harpy_queen", "sea_gatekeeper", "bungbung", "desert_nightmare", "siphonia", "kain",
            "frost_queen", "volcano_giant", "void_apostle", "thunder_god", "primordial_dragon", "vengeful_spirit", "balrog", "kraken",
            "field_boar_king", "field_frost_bear", "field_bandit_lord", "field_ravager", "field_ancient_golem",
            "field_swamp_witch", "field_flame_knight", "field_deep_warden", "field_frost_lich"}
THEME = {"witch": "9cff4a", "elf_queen": "5affa0", "dwarf_king": "3f7fff", "harpy_queen": "6bd8ff", "sea_gatekeeper": "7fe8ff", "bungbung": "ff7a1f",
         "desert_nightmare": "ffb030", "siphonia": "5affa0", "kain": "ff2a3a", "frost_queen": "7fe8ff", "volcano_giant": "ff7a1f", "void_apostle": "c060ff",
         "thunder_god": "6bd8ff", "primordial_dragon": "c060ff", "vengeful_spirit": "7fe8ff", "balrog": "ff2a3a", "megalodon": "7fe8ff", "kraken": "c060ff",
         "field_boar_king": "8fd04a", "field_frost_bear": "7fe8ff", "field_bandit_lord": "ffb030", "field_ravager": "ff5a2a", "field_ancient_golem": "5affa0",
         "field_swamp_witch": "c8ff5a", "field_flame_knight": "ff7a1f", "field_deep_warden": "3fe8ff", "field_frost_lich": "9fd8ff"}


CLAMP_AT_BUILD = [True]   # 보스는 장식을 크게 그린 뒤 줄여서 넣으므로 만들 때는 자르지 않음
BOSS_SHRINK = 0.75        # 보스 모델: (8,8,8) 기준으로 줄여 -16~32 범위에 담고, 플러그인이 1/0.75 배로 키워 그림 (BossModelManager.MODEL_SHRINK)
BOSS_DROP = 6.0           # 줄인 뒤 아래로 내리는 양 (위쪽 왕관·후광이 32 에서 잘리지 않게). 플러그인이 0.5*scale 블록 더 올려 그림


class Palette:
    def __init__(self):
        self.colors = []

    def idx(self, hexcol):
        if hexcol not in self.colors:
            self.colors.append(hexcol)
        return self.colors.index(hexcol)

    def image(self):
        img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        for i, h in enumerate(self.colors):
            h = h.lstrip("#")
            img.putpixel((i % 16, i // 16), tuple(int(h[k:k + 2], 16) for k in (0, 2, 4)) + (255,))
        return img


class Model:
    def __init__(self, pal):
        self.pal = pal
        self.els = []
        self.hero = None   # hero() 로 만든 인체면 치수 기억 (v5.8.0 세부 장식용)

    def box(self, x1, y1, z1, x2, y2, z2, col, rot=None, shade=True):
        """rot = (axis, angle, (ox, oy, oz))  angle ∈ {-45,-22.5,0,22.5,45}"""
        i = self.pal.idx(col)
        u, v = i % 16, i // 16
        uv = [u + 0.1, v + 0.1, u + 0.9, v + 0.9]
        f = [min(x1, x2), min(y1, y2), min(z1, z2)]
        t = [max(x1, x2), max(y1, y2), max(z1, z2)]
        if CLAMP_AT_BUILD[0]:
            f = [max(-16, min(32, c)) for c in f]
            t = [max(-16, min(32, c)) for c in t]
        el = {"from": f, "to": t, "shade": shade, "_col": col,
              "faces": {d: {"uv": uv, "texture": "#0"} for d in ("north", "south", "east", "west", "up", "down")}}
        if rot:
            el["rotation"] = {"axis": rot[0], "angle": rot[1], "origin": list(rot[2])}
        self.els.append(el)
        return self

    def mark(self):
        return len(self.els)

    def hold(self, start, grip, fist, tilt=22.5, end=None):
        """v5.5.0: start 이후에 그린 무기를 손에 쥐게 옮긴다.
        grip(무기에서 손으로 잡을 점) → fist(주먹 중심)로 옮기고, 주먹을 축으로 앞으로 tilt 도 기울임 (위쪽이 앞으로).
        무기 전체를 옆으로 기울이던 z 회전은 버리고, 보석 같은 작은 장식의 x·y 회전은 유지한 채 위치만 따라 옮긴다."""
        import math as _m
        d = [fist[i] - grip[i] for i in range(3)]
        a = _m.radians(tilt)
        for e in self.els[start:end]:
            e["from"] = [e["from"][i] + d[i] for i in range(3)]
            e["to"] = [e["to"][i] + d[i] for i in range(3)]
            r = e.get("rotation")
            if r:
                r["origin"] = [r["origin"][i] + d[i] for i in range(3)]
            if r is None or r["axis"] == "z":
                if tilt:
                    e["rotation"] = {"axis": "x", "angle": tilt, "origin": list(fist)}
                elif r is not None:
                    del e["rotation"]
            elif tilt:
                c = [(e["from"][i] + e["to"][i]) / 2 for i in range(3)]
                y, z = c[1] - fist[1], c[2] - fist[2]
                ny, nz = y * _m.cos(a) - z * _m.sin(a), y * _m.sin(a) + z * _m.cos(a)
                dy, dz = ny - y, nz - z
                for k in ("from", "to"):
                    e[k][1] += dy
                    e[k][2] += dz
                r["origin"][1] += dy
                r["origin"][2] += dz
        return self

    def sym(self, x1, y1, z1, x2, y2, z2, col, rot=None):
        """x=8 기준 좌우 대칭 한 쌍"""
        self.box(x1, y1, z1, x2, y2, z2, col, rot)
        r2 = None
        if rot:
            ax, ang, o = rot
            r2 = (ax, -ang if ax in ("y", "z") else ang, (16 - o[0], o[1], o[2]))
        self.box(16 - x2, y1, z1, 16 - x1, y2, z2, col, r2)
        return self


# ---------------------------------------------------------------- 공통 인체 틀
def humanoid(m, skin, body, legs, arms=None, h=1.0, wide=1.0):
    arms = arms or body
    lw = 1.6 * wide
    m.sym(8 - lw - 1.4, 0, 6.8, 8 - 0.4, 9 * h, 9.2, legs)                     # 다리
    m.box(8 - 3.6 * wide, 9 * h, 6.2, 8 + 3.6 * wide, 19 * h, 9.8, body)      # 몸통
    m.sym(8 - 3.6 * wide - 2.6, 10 * h, 6.8, 8 - 3.6 * wide, 19 * h, 9.2, arms)  # 팔
    m.box(8 - 2.8, 19 * h, 5.4, 8 + 2.8, 24.6 * h, 11.0, skin)                 # 머리
    return 19 * h, 24.6 * h


def eyes(m, y, col, z=11.05, gap=1.4, size=0.9):
    m.sym(8 - gap - size, y, z - 0.1, 8 - gap, y + size * 0.8, z + 0.25, col)


# ---------------------------------------------------------------- 정교한 인체 틀 (관절·갑옷 조각)
def hero(m, skin, top, bottom, trim, accent, boots=None, w=1.0, h=1.0, robe=None):
    """다리(장화·정강이·무릎판·허벅지) · 허리띠 · 몸통(가슴판·중앙선) · 견갑 · 팔(위팔·팔찌·손) · 머리. robe 가 있으면 다리 대신 층진 로브"""
    boots = boots or trim
    if robe:
        for i, (rw, y0, y1) in enumerate([(5.4, 0, 3.4), (4.8, 3.4, 7.0), (4.2, 7.0, 10.4)]):
            m.box(8 - rw * w, y0 * h, 8 - rw * 0.75, 8 + rw * w, y1 * h, 8 + rw * 0.75, robe if i % 2 == 0 else bottom)
            m.box(8 - rw * w - 0.1, y0 * h, 8 + rw * 0.75 - 0.2, 8 + rw * w + 0.1, (y0 + 0.5) * h, 8 + rw * 0.75 + 0.1, trim)
    else:
        m.sym(8 - 3.6 * w, 0, 6.2, 8 - 0.5, 1.2 * h, 10.8, boots)
        m.sym(8 - 3.4 * w, 1.2 * h, 6.6, 8 - 0.6, 5.2 * h, 9.6, bottom)
        m.sym(8 - 3.5 * w, 4.6 * h, 9.4, 8 - 0.5, 6.0 * h, 10.0, trim)
        m.sym(8 - 3.4 * w, 5.8 * h, 6.5, 8 - 0.6, 10.4 * h, 9.6, bottom)
    m.box(8 - 4.2 * w, 10.2 * h, 5.8, 8 + 4.2 * w, 11.6 * h, 10.2, trim)
    m.box(7.2, 10.4 * h, 10.1, 8.8, 11.4 * h, 10.6, accent)
    m.box(8 - 4.0 * w, 11.6 * h, 5.8, 8 + 4.0 * w, 19.6 * h, 10.2, top)
    m.box(8 - 3.4 * w, 14.6 * h, 10.1, 8 + 3.4 * w, 19.2 * h, 10.7, trim)
    m.box(7.6, 12.0 * h, 10.6, 8.4, 19.0 * h, 10.9, accent)
    m.sym(8 - 6.4 * w, 18.4 * h, 5.4, 8 - 3.6 * w, 20.6 * h, 10.6, top)
    m.sym(8 - 6.6 * w, 18.2 * h, 5.3, 8 - 3.6 * w, 18.8 * h, 10.7, trim)
    m.sym(8 - 6.2 * w, 14.6 * h, 6.8, 8 - 4.0 * w, 18.4 * h, 9.2, top)
    m.sym(8 - 6.4 * w, 12.2 * h, 6.6, 8 - 3.9 * w, 14.8 * h, 9.4, trim)
    m.sym(8 - 6.2 * w, 10.6 * h, 7.0, 8 - 4.1 * w, 12.4 * h, 9.0, skin)
    m.box(5.2, 19.6 * h, 5.6, 10.8, 25.0 * h, 11.0, skin)
    # v5.5.0 세부 장식 (아머러스 워크샵 느낌): 목 가리개 · 2겹 견갑과 리벳 · 팔 보호대 테 · 가슴 문장 · 무릎 보호대 · 장화 코
    m.box(8 - 3.0 * w, 19.2 * h, 5.5, 8 + 3.0 * w, 20.2 * h, 10.9, trim)
    m.sym(8 - 6.9 * w, 17.4 * h, 5.1, 8 - 3.5 * w, 18.3 * h, 10.9, top)
    m.sym(8 - 7.0 * w, 17.2 * h, 5.0, 8 - 3.5 * w, 17.5 * h, 11.0, accent)
    for zz in (6.0, 9.4):
        m.sym(8 - 6.0 * w, 20.5 * h, zz, 8 - 5.2 * w, 20.9 * h, zz + 0.6, accent)
    m.sym(8 - 6.6 * w, 12.4 * h, 6.4, 8 - 3.8 * w, 13.0 * h, 9.6, accent)
    m.box(6.6, 16.2 * h, 10.6, 9.4, 18.2 * h, 11.0, trim, rot=("z", 45, (8, 17.2 * h, 10.8)))
    m.box(7.3, 16.7 * h, 10.9, 8.7, 17.7 * h, 11.2, accent, rot=("z", 45, (8, 17.2 * h, 11.0)))
    if not robe:
        m.sym(8 - 3.3 * w, 5.3 * h, 9.5, 8 - 0.8 * w, 6.5 * h, 10.5, trim)
        m.sym(8 - 2.4 * w, 5.6 * h, 10.4, 8 - 1.7 * w, 6.2 * h, 10.8, accent)
        m.sym(8 - 3.4 * w, 0, 10.6, 8 - 0.7, 0.9 * h, 11.4, boots)
    else:
        m.box(8 - 4.4 * w, 10.0 * h, 5.6, 8 + 4.4 * w, 10.4 * h, 10.4, accent)
    m.hero = {"skin": skin, "top": top, "bottom": bottom, "trim": trim, "accent": accent, "w": w, "h": h, "robe": robe}
    return 19.6 * h, 25.0 * h


# ---------------------------------------------------------------- 보스 9종
def witch(m):
    """오염된 마녀 — 누더기 로브, 휘어진 챙 넓은 모자, 독 가마솥 지팡이, 떠도는 독 해골, 독 안개 고리"""
    SKIN, ROBE, ROBE2, TOX, TOX2, WOOD = "8fbf6a", "3a1f52", "2a1640", "7dff6a", "b8ff3a", "5a3a1a"
    hero(m, SKIN, ROBE, ROBE2, "4a2a66", TOX, w=1.0, robe=ROBE)
    eyes(m, 22.2, TOX2, z=11.05)
    m.box(7.4, 20.4, 11.0, 8.6, 21.6, 13.2, "6f9f4a")                               # 매부리코
    m.box(7.9, 20.0, 13.0, 8.3, 20.4, 13.6, "4a7a3a")
    m.box(4.8, 18.2, 5.0, 11.2, 24.0, 5.8, "2a2a2a")                                # 헝클어진 머리
    m.sym(4.4, 19.6, 6.0, 5.4, 23.0, 10.0, "2a2a2a")
    m.box(2.0, 24.6, 3.0, 14.0, 25.4, 13.0, "241232")                              # 넓은 챙
    m.box(4.8, 25.4, 5.2, 11.2, 28.8, 11.0, ROBE2)
    m.box(5.6, 28.4, 6.0, 10.4, 31.4, 10.2, ROBE2, rot=("x", -22.5, (8, 28.4, 8)))
    m.box(6.6, 31.0, 6.8, 9.4, 33.6, 9.4, ROBE2, rot=("x", -45, (8, 31, 8)))
    m.box(4.7, 25.4, 5.1, 11.3, 26.2, 11.1, TOX)                                     # 모자 띠
    m.box(10.0, 25.6, 11.0, 11.0, 26.6, 11.4, "ffd23f")                              # 버클
    for x in (4.2, 6.2, 8.2, 10.2):                                                  # 누더기 자락
        m.box(x, 0, 11.5, x + 1.2, 2.2 + (x % 2), 12.0, ROBE2)
    _w = m.mark()
    m.box(-0.4, 0, 11.4, 0.8, 27, 12.6, WOOD)                                        # 뒤틀린 지팡이
    m.box(-1.2, 12, 11.6, 0.0, 16, 12.4, WOOD, rot=("z", 22.5, (-0.6, 14, 12)))
    m.box(-1.6, 26.4, 10.2, 2.0, 30.0, 13.8, "3a3a3a")                               # 가마솥 머리
    m.box(-1.2, 29.6, 10.6, 1.6, 30.4, 13.4, TOX)                                    # 끓는 독
    m.box(-0.2, 30.4, 11.6, 0.6, 32.0, 12.4, TOX2)
    m.hold(_w, (0.2, 11.5, 12.0), (2.85, 11.5, 8.0))   # v5.5.0: 무기를 손에 쥠
    for (x, y, z) in [(-6, 18, 6), (21, 22, 10), (18, 12, 1)]:                         # 떠도는 독 해골
        m.box(x - 1, y - 1, z - 1, x + 1, y + 1.2, z + 1, "e8e0d0")
        m.box(x - 0.6, y, z + 0.9, x - 0.1, y + 0.6, z + 1.1, TOX)
        m.box(x + 0.1, y, z + 0.9, x + 0.6, y + 0.6, z + 1.1, TOX)


def elf_queen(m):
    """엘프 여왕 — 나뭇잎 드레스, 긴 금발, 가지 왕관, 활과 화살통, 꽃잎이 흩날리는 망토"""
    SKIN, DRESS, DRESS2, GOLD, HAIR, LEAF, WOOD = "f2d7b6", "2f8a55", "1f5a3a", "ffd23f", "f6e27a", "7fe07a", "8a5a2b"
    hero(m, SKIN, DRESS, DRESS2, GOLD, "7fffd0", w=0.85, h=1.1, robe=DRESS)
    m.box(4.6, 12.0, 4.4, 11.4, 27.0, 5.4, HAIR)                                     # 긴 금발
    m.sym(4.4, 17.0, 5.2, 5.4, 27.0, 10.6, HAIR)
    m.box(4.8, 26.0, 5.2, 11.2, 27.6, 11.0, HAIR)
    for x, hh in [(5.0, 2.4), (6.4, 3.4), (8.0, 4.2), (9.6, 3.4), (11.0, 2.4)]:          # 가지 왕관
        m.box(x - 0.3, 27.4, 7.6, x + 0.3, 27.4 + hh, 8.4, WOOD)
        m.box(x - 0.8, 27.0 + hh, 7.4, x + 0.8, 27.8 + hh, 8.6, LEAF)
    m.box(7.5, 27.8, 10.9, 8.5, 28.8, 11.3, "7fffd0")
    eyes(m, 23.8, "3fe0a0", z=11.05)
    m.sym(3.8, 23.0, 7.5, 4.8, 27.0, 8.3, SKIN, rot=("z", -22.5, (5, 24, 8)))          # 뾰족 귀
    for i, y in enumerate(range(2, 20, 3)):                                         # 나뭇잎 망토
        m.box(3.4 + (i % 2), y, 3.6, 12.6 - (i % 2), y + 3, 4.4, LEAF if i % 2 else DRESS2)
    _w = m.mark()
    m.box(16.2, 6, 7.6, 17.2, 28, 8.4, WOOD, rot=("z", 22.5, (16.7, 17, 8)))            # 장궁
    m.box(15.0, 8, 7.9, 15.3, 26, 8.1, "f0ead8", rot=("z", 22.5, (16.7, 17, 8)))
    m.box(16.0, 16.4, 7.4, 17.4, 17.6, 8.6, GOLD, rot=("z", 22.5, (16.7, 17, 8)))
    m.hold(_w, (16.7, 17.0, 8.0), (12.38, 12.65, 8.0), tilt=0)   # v5.5.0: 무기를 손에 쥠
    m.box(2.8, 13, 3.0, 5.2, 21, 5.4, "6a4020", rot=("z", -22.5, (4, 17, 4.2)))         # 화살통
    for x in (3.2, 4.0, 4.8):
        m.box(x, 21, 3.6, x + 0.4, 23.4, 4.0, "e8e0d0", rot=("z", -22.5, (4, 17, 4.2)))
    for (x, y, z) in [(-5, 20, 6), (21, 16, 11), (-3, 8, 13), (19, 26, 3)]:            # 꽃잎
        m.box(x - 0.6, y, z - 0.6, x + 0.6, y + 0.3, z + 0.6, "ffb0d8", rot=("y", 45, (x, y, z)))


def dwarf_king(m):
    """드워프 왕 — 땅딸막한 판금 몸, 땋은 불꽃 수염, 룬 왕관, 금 벨트, 거대한 룬 망치, 모루 방패"""
    SKIN, ARM, ARM2, GOLD, BEARD, RUNE = "e0a878", "8a8f96", "5a5f66", "e0b030", "e06a2a", "3f7fff"
    hero(m, SKIN, ARM, "5a3a22", GOLD, RUNE, boots=ARM2, w=1.45, h=0.8)
    m.box(4.8, 9.0, 10.4, 11.2, 17.6, 12.4, BEARD)                                    # 수염
    m.box(5.6, 6.0, 11.0, 7.4, 9.2, 12.0, "c0501a")                                   # 땋은 두 갈래
    m.box(8.6, 6.0, 11.0, 10.4, 9.2, 12.0, "c0501a")
    m.box(5.5, 6.2, 11.9, 7.5, 6.8, 12.2, GOLD)
    m.box(8.5, 6.2, 11.9, 10.5, 6.8, 12.2, GOLD)
    m.box(5.0, 17.4, 10.9, 11.0, 18.2, 11.3, "c0501a")                                # 콧수염
    eyes(m, 18.0, RUNE, z=11.05)
    m.box(4.8, 19.6, 5.0, 11.2, 21.0, 11.2, GOLD)                                     # 룬 왕관
    for x in (5.0, 7.3, 9.6):
        m.box(x, 21.0, 5.2, x + 1.4, 23.4, 11.2, GOLD)
        m.box(x + 0.3, 21.6, 11.1, x + 1.1, 22.4, 11.4, RUNE)
    m.sym(1.0, 13.6, 5.2, 3.8, 17.6, 10.8, ARM2)                                      # 두꺼운 견갑
    m.sym(1.2, 17.2, 5.4, 3.6, 17.8, 10.6, GOLD)
    _w = m.mark()
    m.box(15.6, 0, 7.4, 17.2, 24, 8.8, "6b4a2b")                                      # 룬 망치
    m.box(13.4, 21.4, 4.4, 20.2, 27.6, 12.0, ARM)
    m.box(13.2, 23.6, 7.4, 20.4, 25.4, 9.0, GOLD)
    for z in (5.0, 10.8):
        m.box(13.1, 22.4, z, 20.5, 26.6, z + 0.6, RUNE)
    m.hold(_w, (16.4, 6.0, 8.1), (15.47, 9.2, 8.0))   # v5.5.0: 무기를 손에 쥠
    m.box(-2.4, 5, 4.8, -0.6, 15, 11.6, ARM2)                                         # 모루 방패
    m.box(-2.6, 8.4, 6.8, -0.4, 11.6, 9.6, GOLD)
    m.box(-2.7, 9.4, 7.8, -0.3, 10.6, 8.6, RUNE)


def harpy_queen(m):
    """하피 여왕 — 3겹 대형 날개(깃 끝 색 변화), 새 다리와 황금 발톱, 깃털 관, 폭풍 고리"""
    SKIN, BODY, LEGS, GOLD, F1, F2, F3 = "f0d0c0", "e9e6de", "8a7a6a", "e0b030", "e9e6de", "cfe6ff", "8fd3ff"
    hero(m, SKIN, BODY, LEGS, GOLD, F3, boots=GOLD, w=0.8, h=1.05)
    for i, (y, ln) in enumerate([(19, 14), (15.5, 12), (12, 9), (9, 6)]):
        col = [F1, F2, F3, "5ab0ff"][i]
        m.box(-12 + (14 - ln), y, 6.8, 5.0, y + 3.0, 8.4, col, rot=("z", 22.5, (5, y, 8)))
        m.box(11.0, y, 6.8, 28 - (14 - ln), y + 3.0, 8.4, col, rot=("z", -22.5, (11, y, 8)))
    for k in range(4):                                                              # 날개 깃 끝
        m.box(-12 + k * 3, 17.4, 6.9, -11 + k * 3, 19.2, 8.3, "5ab0ff", rot=("z", 22.5, (5, 19, 8)))
        m.box(27 - k * 3, 17.4, 6.9, 28 - k * 3, 19.2, 8.3, "5ab0ff", rot=("z", -22.5, (11, 19, 8)))
    m.box(5.6, 25.0, 6.0, 10.4, 26.6, 10.4, F3)                                      # 깃털 관
    for x in (6.0, 7.5, 9.0):
        m.box(x, 26.6, 7.0, x + 1.0, 31.0, 8.0, F2, rot=("x", -22.5, (8, 27, 8)))
    m.box(7.6, 26.8, 10.3, 8.4, 27.6, 10.8, GOLD)
    eyes(m, 22.4, "3fb8ff", z=11.05)
    m.box(7.5, 21.0, 10.9, 8.5, 21.8, 12.0, GOLD)                                    # 부리 같은 코


def sea_gatekeeper(m):
    """심해수문장 — 비늘 몸, 조개 견갑, 산호 왕관과 지느러미, 진주 흉갑, 거대한 삼지창, 물방울 고리"""
    SKIN, BODY, LEGS, CORAL, PEARL, GLOW, GOLD = "1f9bb0", "157a8a", "0f5a6a", "ff7f6a", "f2efe6", "7fffff", "e0c080"
    hero(m, SKIN, BODY, LEGS, CORAL, GLOW, w=1.35, h=1.15)
    for y in (13, 15, 17):                                                           # 비늘 줄
        m.box(3.0, y * 1.0, 10.15, 13.0, y + 0.6, 10.35, "1fb0c8")
    m.sym(-0.6, 19.4, 5.0, 3.6, 23.4, 11.0, "ff9f8a")                                 # 조개 견갑
    for k in range(3):
        m.sym(-0.4 + k * 1.2, 23.2, 5.2, 0.4 + k * 1.2, 23.8, 10.8, CORAL)
    m.box(4.6, 13.4, 10.6, 11.4, 20.4, 11.2, PEARL)                                   # 진주 흉갑
    m.box(7.2, 16.0, 11.1, 8.8, 17.6, 11.6, GLOW)
    m.box(7.0, 28.6, 5.0, 9.0, 33.0, 11.6, CORAL)                                      # 지느러미 볏
    for x, hh in [(4.6, 2.4), (5.8, 3.4), (10.2, 3.4), (11.4, 2.4)]:                    # 산호 왕관
        m.box(x - 0.4, 28.6, 7.4, x + 0.4, 28.6 + hh, 8.6, CORAL)
    eyes(m, 25.6, "ffe08a", z=11.05)
    m.sym(3.6, 21.6, 7.0, 4.8, 25.0, 9.6, "1fb0c8", rot=("z", 22.5, (4.2, 23, 8.3)))     # 아가미 지느러미
    _w = m.mark()
    m.box(-2.4, 0, 7.4, -1.0, 34, 8.8, GOLD)                                         # 삼지창
    for dx in (-4.2, -2.4, -0.6):
        m.box(dx - 0.2, 34, 7.6, dx + 1.0, 38.5 if dx == -2.4 else 37, 8.6, PEARL)
    m.box(-4.6, 33.4, 7.4, 1.4, 34.4, 8.8, GOLD)
    m.box(-2.0, 30.0, 7.3, -1.4, 31.0, 8.9, GLOW)
    m.hold(_w, (-1.7, 13.2, 8.1), (1.05, 13.2, 8.0))   # v5.5.0: 무기를 손에 쥠
    for (x, y, z) in [(-6, 10, 4), (21, 14, 12), (-4, 24, 12), (20, 26, 2)]:            # 물방울
        m.box(x - 0.7, y - 0.7, z - 0.7, x + 0.7, y + 0.9, z + 0.7, "9fe8ff", rot=("y", 45, (x, y, z)))


def bungbung(m):
    """붕붕이 — 겹겹이 타오르는 불꽃 정령: 층진 불꽃 몸, 용암 핵, 성난 눈, 불꽃 팔, 주위를 도는 불씨 고리"""
    for i, (w, y0, y1, c) in enumerate([(6.4, 4, 9, "c43c12"), (6.0, 9, 14, "ff5a1f"), (5.2, 14, 19, "ff8a2f"), (4.2, 19, 23, "ffb030"), (3.0, 23, 27, "ffd23f"), (1.6, 27, 31, "fff0a0")]):
        m.box(8 - w, y0, 8 - w, 8 + w, y1, 8 + w, c)
    for (x, z, a) in [(0.6, 8, 22.5), (15.4, 8, -22.5)]:                               # 불꽃 가시(옆)
        m.box(x - 1, 12, z - 1, x + 1, 24, z + 1, "ffb030", rot=("z", a, (x, 18, z)))
    for (x, z, a) in [(8, 0.6, -22.5), (8, 15.4, 22.5)]:
        m.box(x - 1, 12, z - 1, x + 1, 24, z + 1, "ffb030", rot=("x", a, (x, 18, z)))
    m.box(6.2, 10.2, 13.6, 9.8, 13.0, 14.6, "ffe8a0")                                 # 용암 핵
    m.box(4.0, 15.4, 13.9, 6.8, 17.4, 14.5, "2a1010")                                 # 성난 눈
    m.box(9.2, 15.4, 13.9, 12.0, 17.4, 14.5, "2a1010")
    m.box(4.8, 16.0, 14.4, 6.0, 16.8, 14.7, "fff0a0")
    m.box(10.0, 16.0, 14.4, 11.2, 16.8, 14.7, "fff0a0")
    m.box(3.6, 17.6, 13.9, 7.0, 18.2, 14.5, "2a1010", rot=("z", -22.5, (5.3, 17.9, 14)))  # 찡그린 눈썹
    m.box(9.0, 17.6, 13.9, 12.4, 18.2, 14.5, "2a1010", rot=("z", 22.5, (10.7, 17.9, 14)))
    m.box(5.6, 11.6, 13.95, 10.4, 12.8, 14.5, "2a1010")                               # 입
    m.sym(-2.6, 10, 6.6, 1.8, 13, 9.4, "ff8a2f", rot=("z", 22.5, (1.8, 11.5, 8)))       # 불꽃 팔
    m.sym(-4.2, 12.4, 7.0, -1.8, 15.4, 9.0, "ffd23f")
    import math as _m
    for k in range(8):                                                               # 불씨 고리
        a = k * _m.pi / 4
        x, z = 8 + _m.cos(a) * 12, 8 + _m.sin(a) * 12
        m.box(x - 0.7, 20 + (k % 2) * 3, z - 0.7, x + 0.7, 21.4 + (k % 2) * 3, z + 0.7, "ffd23f" if k % 2 else "ff8a2f")


def desert_nightmare(m):
    """사막의 악몽 — 붕대 감긴 파라오 미라: 줄무늬 두건, 황금 가면, 숫양 뿔, 스카라브 부적, 모래 망토, 거대한 낫, 휘감은 뱀, 떠도는 모래 구슬"""
    BAND, BAND2, GOLD, BLUE, SAND, BONE, EYE = "e8dfc4", "cdbf9c", "e8b830", "2a4a9a", "c29a5a", "f2ecd8", "ff5a1f"
    # 다리 (붕대 감김: 줄무늬)
    for y in range(0, 10, 2):
        m.sym(4.4, y, 6.6, 7.4, y + 1, 9.6, BAND)
        m.sym(4.5, y + 1, 6.7, 7.3, y + 2, 9.5, BAND2)
    m.sym(4.0, 0, 6.2, 7.6, 0.8, 11.0, GOLD)                                      # 황금 샌들
    # 허리: 황금 띠 + 앞치마 (줄무늬)
    m.box(3.2, 10.0, 5.8, 12.8, 11.4, 10.2, GOLD)
    for i in range(5):
        m.box(5.2 + i * 1.2, 5.0, 10.1, 6.2 + i * 1.2, 10.0, 10.5, GOLD if i % 2 else BLUE)
    # 몸통: 붕대 줄무늬 + 드러난 갈비뼈 + 황금 목깃
    for i, y in enumerate(range(11, 21)):
        m.box(3.4, y, 6.0, 12.6, y + 1, 10.0, BAND if i % 2 else BAND2)
    for y in (13.2, 14.6, 16.0):
        m.box(5.0, y, 9.95, 11.0, y + 0.6, 10.4, BONE)
    m.box(7.6, 12.6, 9.95, 8.4, 17.0, 10.5, BONE)
    for i in range(3):                                                          # 층진 황금 목깃
        m.box(3.6 - i * 0.6, 19.0 + i * 0.7, 5.6, 12.4 + i * 0.6, 19.7 + i * 0.7, 10.6 + i * 0.2, GOLD if i != 1 else BLUE)
    m.box(6.8, 16.4, 10.4, 9.2, 18.6, 11.0, BLUE)                                  # 스카라브 부적
    m.box(7.3, 16.9, 10.9, 8.7, 18.1, 11.3, "5ad0ff")
    # 팔 (붕대 + 황금 팔찌)
    for y in range(11, 20, 2):
        m.sym(0.8, y, 6.8, 3.4, y + 1, 9.2, BAND)
        m.sym(0.9, y + 1, 6.9, 3.3, y + 2, 9.1, BAND2)
    m.sym(0.6, 12.0, 6.6, 3.6, 13.2, 9.4, GOLD)
    m.sym(0.6, 9.4, 7.0, 3.4, 11.0, 9.0, BONE)                                     # 뼈 손
    # 머리: 줄무늬 두건 · 황금 가면 · 눈 · 수염 · 뿔
    m.box(4.6, 20.6, 5.4, 11.4, 27.8, 11.0, BLUE)
    for i in range(4):
        m.box(4.5, 21.2 + i * 1.6, 5.3, 11.5, 21.8 + i * 1.6, 11.1, GOLD)
    m.sym(3.4, 16.0, 7.0, 4.6, 26.0, 10.6, BLUE)                                   # 두건 옆 자락
    m.sym(3.3, 17.0, 6.9, 4.7, 17.6, 10.7, GOLD)
    m.sym(3.3, 19.4, 6.9, 4.7, 20.0, 10.7, GOLD)
    m.box(5.2, 21.2, 10.9, 10.8, 26.2, 11.8, GOLD)                                  # 황금 가면
    m.box(5.8, 24.0, 11.7, 7.4, 24.9, 12.1, EYE)
    m.box(8.6, 24.0, 11.7, 10.2, 24.9, 12.1, EYE)
    m.box(7.5, 18.4, 11.2, 8.5, 21.4, 11.9, GOLD)                                  # 파라오 수염
    m.box(7.4, 27.6, 10.6, 8.6, 29.6, 11.8, GOLD)                                  # 코브라 장식
    m.box(7.6, 28.8, 11.6, 8.4, 29.4, 12.4, EYE)
    m.sym(2.2, 25.4, 7.2, 4.6, 27.2, 9.4, "8a6a3a", rot=("z", 22.5, (3.4, 26.2, 8.2)))    # 숫양 뿔
    m.sym(0.8, 23.2, 7.4, 2.6, 26.6, 9.2, "7a5a2a", rot=("z", -22.5, (1.8, 25.0, 8.2)))
    m.sym(1.2, 21.4, 8.2, 2.6, 23.4, 10.2, "6a4a22")
    # 모래 망토
    m.box(3.0, 2.0, 4.0, 13.0, 20.6, 5.2, SAND, rot=("x", 22.5, (8, 20.6, 4.6)))
    m.box(3.4, 0.8, 4.6, 12.6, 2.4, 5.4, "a8844a")
    _w = m.mark()
    # 거대한 낫: 자루 · 날 · 황금 장식
    m.box(17.6, 0, 7.4, 18.8, 34, 8.6, "5a3a1a", rot=("z", -22.5, (18.2, 16, 8)))
    m.box(10.0, 29.0, 7.6, 19.0, 31.0, 8.4, "e8e0d0", rot=("z", -22.5, (18.2, 16, 8)))
    m.box(9.0, 27.6, 7.7, 11.0, 29.4, 8.3, "e8e0d0", rot=("z", -22.5, (18.2, 16, 8)))
    m.box(16.8, 30.4, 7.2, 19.6, 32.2, 8.8, GOLD, rot=("z", -22.5, (18.2, 16, 8)))
    m.hold(_w, (18.2, 10.2, 8.0), (14.0, 10.2, 8.0))   # v5.5.0: 무기를 손에 쥠
    # 휘감은 뱀
    for i, (x, y, z) in enumerate([(3.0, 12, 10.6), (5.0, 13.4, 10.8), (7.4, 14.2, 10.9), (9.8, 13.4, 10.8), (12.0, 12, 10.6), (12.8, 10.6, 8.0)]):
        m.box(x - 0.7, y - 0.5, z - 0.5, x + 0.7, y + 0.5, z + 0.3, "3a8a3a" if i % 2 else "2a6a2a")
    m.box(12.4, 10.2, 5.6, 13.6, 11.2, 6.8, "2a6a2a")
    # 떠도는 모래 구슬
    for (x, y, z) in [(-6, 16, 6), (22, 18, 10), (-4, 26, 12), (20, 8, 2)]:
        m.box(x - 1, y - 1, z - 1, x + 1, y + 1, z + 1, "e8c890", rot=("y", 45, (x, y, z)))
        m.box(x - 0.5, y - 0.5, z - 0.5, x + 0.5, y + 0.5, z + 0.5, GOLD)


def siphonia(m):
    """시포니아 — 검의 마녀: 층진 드레스, 긴 머리, 가시 왕관, 빛나는 룬 마법진, 떠도는 여섯 자루의 검, 마법봉"""
    GOWN, GOWN2, TRIM, SKIN, HAIR, GLOW, BLADE, GOLD = "1f6b45", "144a30", "7fffb0", "d8f0e0", "0a2a1c", "a8ffd0", "d8ecff", "ffd23f"
    # 층진 드레스 (아래로 넓게)
    for i, (w, y0, y1, c) in enumerate([(6.0, 0, 3, GOWN2), (5.2, 3, 7, GOWN), (4.4, 7, 11, GOWN2), (3.8, 11, 13, GOWN)]):
        m.box(8 - w, y0, 8 - w * 0.7, 8 + w, y1, 8 + w * 0.7, c)
        m.box(8 - w - 0.1, y0, 8 + w * 0.7 - 0.2, 8 + w + 0.1, y0 + 0.5, 8 + w * 0.7 + 0.1, TRIM)
    # 몸통 · 허리띠 · 가슴 장식
    m.box(4.8, 13, 6.4, 11.2, 20.4, 9.6, GOWN)
    m.box(4.6, 13, 6.3, 11.4, 13.8, 9.7, GOLD)
    m.box(7.0, 17.2, 9.5, 9.0, 19.2, 10.0, TRIM)
    m.sym(3.6, 18.8, 6.2, 5.2, 20.8, 9.8, GOWN2)                                   # 어깨
    # 팔: 넓은 소매 + 손
    m.sym(2.6, 14.0, 6.6, 4.8, 19.0, 9.4, GOWN)
    m.sym(2.0, 12.0, 6.0, 4.8, 14.2, 10.0, GOWN2)
    m.sym(2.6, 10.8, 7.2, 4.0, 12.0, 8.8, SKIN)
    # 머리 · 긴 머리카락 · 가시 왕관 · 눈
    m.box(5.4, 20.4, 5.8, 10.6, 25.6, 10.8, SKIN)
    m.box(5.0, 23.8, 5.2, 11.0, 26.4, 11.0, HAIR)
    m.box(4.8, 12.0, 4.8, 11.2, 25.4, 6.2, HAIR)                                  # 등으로 흘러내린 머리
    m.sym(4.6, 16.0, 5.2, 5.6, 25.0, 9.0, HAIR)
    eyes(m, 22.8, "3dff9a", z=10.85, gap=0.9, size=0.8)
    for x, h in [(5.6, 1.6), (6.8, 2.6), (8.0, 3.6), (9.2, 2.6), (10.4, 1.6)]:
        m.box(x - 0.35, 26.2, 7.6, x + 0.35, 26.2 + h, 8.4, GOLD)
        m.box(x - 0.2, 26.2 + h - 0.6, 7.8, x + 0.2, 26.2 + h, 8.2, TRIM)
    _w = m.mark()
    # 마법봉 (왼손)
    m.box(0.8, 4.0, 7.4, 1.8, 26.0, 8.4, "3a2a1a")
    m.box(0.0, 26.0, 6.6, 2.6, 28.6, 9.2, TRIM, rot=("y", 45, (1.3, 27.3, 7.9)))
    m.box(0.5, 26.5, 7.1, 2.1, 28.1, 8.7, GLOW)
    m.hold(_w, (1.3, 11.4, 7.9), (3.3, 11.4, 8.0))   # v5.5.0: 무기를 손에 쥠
    # 떠도는 여섯 자루의 검 (원을 그리며)
    import math as _m
    for k in range(6):
        a = k * _m.pi / 3
        x, z = 8 + _m.cos(a) * 11, 8 + _m.sin(a) * 11
        ang = [45, -45, 22.5, -22.5, 45, -22.5][k]
        y0 = 12 + (k % 2) * 4
        m.box(x - 0.5, y0, z - 0.3, x + 0.5, y0 + 12, z + 0.3, BLADE, rot=("z", ang, (x, y0 + 6, z)))
        m.box(x - 0.2, y0 + 1, z - 0.35, x + 0.2, y0 + 11, z + 0.35, TRIM, rot=("z", ang, (x, y0 + 6, z)))
        m.box(x - 1.6, y0 + 1.6, z - 0.5, x + 1.6, y0 + 2.4, z + 0.5, GOLD, rot=("z", ang, (x, y0 + 6, z)))


def kain(m):
    """카인 — 심연의 흑기사왕: 겹겹의 판금, 붉게 빛나는 핵, 가시 견갑, 뿔 왕관, 찢어진 망토, 룬 대검, 떠도는 파편"""
    DARK, STEEL, EDGE, RED, GLOW, CAPE = "15151c", "24242e", "3a3a48", "8a0a1e", "ff2a3a", "3a0610"
    # 다리: 정강이 · 무릎 가시 · 허벅지 · 쇠 장화
    m.sym(4.2, 0, 6.2, 7.4, 1.4, 11.2, STEEL)                                     # 장화 (앞으로 길게)
    m.sym(4.4, 1.4, 6.6, 7.2, 6.0, 9.6, DARK)                                     # 정강이
    m.sym(4.6, 3.0, 9.5, 7.0, 5.6, 9.9, EDGE)                                     # 정강이 판 모서리
    m.sym(4.2, 6.0, 6.3, 7.4, 7.4, 10.0, STEEL)                                   # 무릎 판
    m.sym(5.2, 6.4, 9.9, 6.4, 7.0, 11.2, RED)                                     # 무릎 가시
    m.sym(4.3, 7.4, 6.5, 7.3, 11.6, 9.6, DARK)                                    # 허벅지
    # 허리: 벨트 · 핵 버클 · 갑옷 치마
    m.box(3.4, 11.4, 5.8, 12.6, 12.8, 10.2, STEEL)
    m.box(7.1, 11.6, 10.1, 8.9, 12.6, 10.6, GLOW)
    m.box(4.0, 8.6, 9.8, 12.0, 11.6, 10.4, DARK, rot=("x", -22.5, (8, 11.6, 10)))  # 앞 치마
    m.sym(2.8, 8.4, 6.4, 4.0, 11.6, 9.6, DARK, rot=("z", -22.5, (3.4, 11.6, 8)))   # 옆 치마
    # 몸통: 기본판 · 가슴판 · 중앙 능선 · 핵 · 갈비 무늬
    m.box(3.2, 12.8, 5.6, 12.8, 21.6, 10.4, DARK)
    m.box(3.8, 16.0, 10.3, 12.2, 21.2, 11.0, STEEL)
    m.box(7.6, 13.0, 10.9, 8.4, 21.0, 11.3, EDGE)
    m.box(6.8, 17.2, 11.0, 9.2, 19.6, 11.6, RED)
    m.box(7.3, 17.7, 11.5, 8.7, 19.1, 11.9, GLOW)                                 # 붉게 빛나는 핵
    for y in (13.6, 14.8):
        m.box(4.2, y, 10.35, 11.8, y + 0.5, 10.7, EDGE)
    m.box(3.4, 13.0, 5.2, 12.6, 21.4, 5.7, STEEL)                                 # 등판
    # 견갑: 3겹 + 가시
    for i, (w, y) in enumerate([(5.2, 21.0), (4.4, 22.4), (3.4, 23.6)]):
        m.sym(8 - 4.2 - w, y, 5.0 + i * 0.3, 8 - 3.4, y + 1.6, 11.0 - i * 0.3, [STEEL, DARK, STEEL][i])
    m.sym(-1.2, 23.2, 7.4, 0.4, 27.4, 8.6, RED, rot=("z", 22.5, (-0.4, 23.2, 8)))
    m.sym(0.6, 24.4, 6.0, 1.8, 27.0, 7.2, EDGE, rot=("z", 22.5, (1.2, 24.4, 6.6)))
    m.sym(0.6, 24.4, 8.8, 1.8, 27.0, 10.0, EDGE, rot=("z", 22.5, (1.2, 24.4, 9.4)))
    # 팔: 위팔 · 건틀릿 · 주먹
    m.sym(0.8, 16.4, 6.6, 3.4, 21.0, 9.4, DARK)
    m.sym(0.4, 12.0, 6.2, 3.6, 16.4, 9.8, STEEL)
    m.sym(0.2, 13.6, 9.7, 3.8, 14.4, 10.1, RED)
    m.sym(0.6, 10.2, 6.6, 3.4, 12.0, 9.6, DARK)
    # 머리: 투구 · 눈 틈 · 턱 가리개 · 뿔 · 왕관 가시
    m.box(4.8, 21.6, 5.6, 11.2, 27.6, 11.4, DARK)
    m.box(5.2, 22.0, 11.2, 10.8, 24.2, 11.9, STEEL)
    m.box(5.4, 25.0, 11.3, 10.6, 25.8, 11.7, GLOW)                                # 붉은 눈 틈
    m.box(7.6, 22.2, 11.8, 8.4, 27.8, 12.1, EDGE)
    m.sym(3.2, 25.4, 7.6, 4.8, 27.0, 9.2, DARK)
    m.sym(1.6, 26.4, 7.8, 3.4, 28.0, 9.0, "2a0a12", rot=("z", 22.5, (2.6, 27.2, 8.4)))
    m.sym(0.6, 28.0, 7.9, 2.0, 32.0, 8.9, "2a0a12", rot=("z", -22.5, (1.2, 28.0, 8.4)))
    for x, h in [(5.4, 2.2), (6.8, 3.2), (8.0, 4.2), (9.2, 3.2), (10.6, 2.2)]:
        m.box(x - 0.4, 27.6, 7.8, x + 0.4, 27.6 + h, 8.6, RED if h > 3 else STEEL)
    # 망토: 4갈래로 흩날림
    for i, x in enumerate((3.4, 5.8, 8.2, 10.6)):
        ang = [22.5, 0, 0, -22.5][i]
        m.box(x, 1.6 + (i % 2) * 1.4, 3.6, x + 2.4, 21.4, 4.4, CAPE, rot=("x", 22.5 if i % 3 == 0 else 0, (x + 1.2, 21.4, 4)))
    m.box(3.2, 20.8, 3.8, 12.8, 21.8, 5.4, RED)                                    # 망토 걸쇠
    _w = m.mark()
    # 룬 대검: 칼날 · 룬 · 가드 · 손잡이 · 폼멜
    R = ("z", -22.5, (17.6, 14, 8))
    m.box(16.6, 12.0, 7.2, 18.6, 33.0, 8.8, "2c2c38", rot=R)
    m.box(17.3, 13.0, 7.1, 17.9, 32.0, 8.9, GLOW, rot=R)
    for y in (16, 20, 24, 28):
        m.box(16.9, y, 7.05, 18.3, y + 0.6, 8.95, RED, rot=R)
    m.box(13.6, 10.6, 6.6, 21.6, 12.0, 9.4, DARK, rot=R)
    m.box(13.2, 11.2, 7.4, 14.0, 13.4, 8.6, RED, rot=R)
    m.box(21.2, 11.2, 7.4, 22.0, 13.4, 8.6, RED, rot=R)
    m.box(17.0, 6.0, 7.4, 18.2, 10.6, 8.6, "3a0610", rot=R)
    m.box(16.7, 5.0, 7.1, 18.5, 6.2, 8.9, GLOW, rot=R)
    m.hold(_w, (17.6, 8.3, 8.0), (14.0, 11.1, 8.0))   # v5.5.0: 무기를 손에 쥠
    # 떠도는 심연 파편
    for (x, y, z, a) in [(-6, 20, 3, 45), (22, 24, 12, -45), (-4, 8, 14, 22.5), (21, 6, 2, -22.5), (8, 34, 13, 45)]:
        m.box(x - 0.6, y - 1.4, z - 0.6, x + 0.6, y + 1.4, z + 0.6, "5a1024", rot=("z", a, (x, y, z)))


def frost_queen(m):
    """서리 여왕 엘사리아 — 얼음 드레스와 서리 꽃 자락, 얼음 왕관, 긴 은발, 얼음 날개 조각, 빙정 지팡이, 떠도는 고드름"""
    SKIN, DRESS, DRESS2, ICE, ICE2, GLOW, HAIR = "dff4ff", "a8e0ff", "7fc8ff", "e8fbff", "bff4ff", "3fb8ff", "f4f8ff"
    hero(m, SKIN, DRESS, DRESS2, ICE, GLOW, w=0.9, h=1.15, robe=DRESS)
    for k in range(6):                                                              # 서리꽃 자락
        m.box(3.0 + k * 1.8, 0, 12.4, 4.2 + k * 1.8, 1.6 + (k % 3), 13.2, ICE2, rot=("x", -22.5, (3.6 + k * 1.8, 0, 12.8)))
    m.box(4.6, 13.0, 4.2, 11.4, 28.0, 5.4, HAIR)                                     # 긴 은발
    m.sym(4.4, 18.0, 5.2, 5.4, 28.0, 10.6, HAIR)
    m.box(4.8, 27.6, 5.2, 11.2, 28.6, 11.0, ICE)                                     # 얼음 왕관
    for x, hh in [(5.0, 2.0), (6.4, 3.6), (8.0, 5.0), (9.6, 3.6), (11.0, 2.0)]:
        m.box(x - 0.35, 28.6, 7.6, x + 0.35, 28.6 + hh, 8.4, ICE2)
    m.box(7.6, 29.0, 10.9, 8.4, 29.8, 11.3, GLOW)
    eyes(m, 25.6, GLOW, z=11.05)
    for sgn, ox in ((-1, 4.0), (1, 12.0)):                                             # 얼음 날개 조각
        for k in range(3):
            rz = 22.5 if sgn < 0 else -22.5
            x0 = ox - (6 + k * 2) if sgn < 0 else ox
            x1 = ox if sgn < 0 else ox + 6 + k * 2
            m.box(x0, 16 + k * 3, 3.6, x1, 17.2 + k * 3, 4.4, ICE2 if k % 2 else ICE, rot=("z", rz, (ox, 17 + k * 3, 4)))
    _w = m.mark()
    m.box(-1.0, 0, 7.4, 0.2, 31, 8.6, ICE)                                          # 빙정 지팡이
    m.box(-2.2, 30.4, 6.2, 1.4, 34.2, 9.8, GLOW, rot=("y", 45, (-0.4, 32.3, 8)))
    m.box(-1.6, 31.0, 6.8, 0.8, 33.6, 9.2, ICE2, rot=("y", 45, (-0.4, 32.3, 8)))
    m.hold(_w, (-0.4, 13.2, 8.0), (3.37, 13.2, 8.0))   # v5.5.0: 무기를 손에 쥠
    for (x, y, z, a) in [(-6, 17, 4, 22.5), (21, 13, 12, -22.5), (8, -0, -4, 45), (-4, 26, 13, 45), (20, 28, 2, -45)]:
        m.box(x - 0.6, y, z - 0.6, x + 0.6, y + 5, z + 0.6, ICE2, rot=("z", a, (x, y + 2.5, z)))


def volcano_giant(m):
    """화산의 거인 이그니르 — 바위 판으로 쌓인 몸, 용암이 흐르는 균열과 핵, 분화구 머리, 거대한 바위 주먹, 흘러내리는 용암"""
    ROCK, ROCK2, DARK, LAVA, FIRE, HOT = "4a3a3a", "3a2a2a", "2a1c1c", "ff5a1f", "ffd23f", "ff8a2f"
    m.sym(2.4, 0, 5.0, 7.2, 1.6, 11.6, DARK)                                        # 발
    m.sym(2.6, 1.6, 5.4, 7.0, 7.0, 10.8, ROCK2)
    m.sym(2.4, 6.4, 5.2, 7.2, 8.0, 11.0, ROCK)                                      # 무릎 바위
    m.sym(2.8, 8.0, 5.6, 6.8, 11.6, 10.4, ROCK2)
    m.box(1.0, 11.4, 4.0, 15.0, 25.0, 12.0, ROCK)                                   # 거대한 몸
    m.box(2.0, 11.0, 3.4, 14.0, 12.4, 12.6, DARK)
    for (x0, y0, x1, y1) in [(2.4, 13, 7.6, 17.6), (8.4, 13, 13.6, 17.6), (2.4, 18.4, 7.6, 24), (8.4, 18.4, 13.6, 24)]:
        m.box(x0, y0, 11.9, x1, y1, 12.6, ROCK2)                                     # 바위 판
    for (x, y, w_, hh) in [(7.6, 12.4, 0.8, 12.0), (2.0, 17.6, 12.0, 0.8), (4.6, 20.2, 0.5, 3.6), (11.0, 14.0, 0.5, 3.4)]:
        m.box(x, y, 12.5, x + w_, y + hh, 12.8, LAVA)                                 # 용암 균열
    m.box(6.8, 16.8, 12.6, 9.2, 19.2, 13.2, FIRE)                                    # 용암 핵
    m.box(7.3, 17.3, 13.1, 8.7, 18.7, 13.5, "fff0a0")
    m.sym(-4.6, 18.6, 4.4, 1.2, 24.6, 11.6, ROCK)                                    # 어깨 바위
    m.sym(-4.2, 12.2, 5.2, 0.8, 18.8, 10.8, ROCK2)                                   # 팔
    m.sym(-5.4, 7.0, 4.2, 1.6, 12.4, 11.8, DARK)                                     # 거대한 주먹
    m.sym(-5.2, 9.0, 11.7, 1.4, 9.6, 12.0, LAVA)
    m.box(4.2, 25.0, 5.0, 11.8, 30.2, 11.2, ROCK2)                                   # 머리
    m.box(4.8, 26.6, 11.1, 11.2, 27.6, 11.5, DARK)
    eyes(m, 27.8, FIRE, z=11.25, gap=1.2, size=1.2)
    m.box(5.0, 30.2, 5.6, 11.0, 31.4, 10.6, DARK)                                    # 분화구
    m.box(6.0, 30.8, 6.6, 10.0, 32.8, 9.6, LAVA)
    m.box(7.0, 32.6, 7.4, 9.0, 34.4, 8.8, FIRE)
    m.box(7.6, 34.2, 7.8, 8.4, 35.6, 8.4, HOT)
    for (x, y) in [(2.2, 6), (13.4, 4), (8.0, 2)]:                                     # 흘러내리는 용암
        m.box(x, y, 12.0, x + 0.8, y + 5, 12.4, HOT)
    for (x, y, z) in [(-7, 26, 6), (22, 28, 10), (8, 38, 12)]:                           # 화산재 바위
        m.box(x - 1, y - 1, z - 1, x + 1, y + 1, z + 1, DARK, rot=("y", 45, (x, y, z)))
    # v5.8.0 웅장하게: 어깨 분화구(불기둥) · 몸 가득한 용암 맥 · 녹아내린 왕관 · 등의 바위 산맥
    for sx in (-1.6, 17.6):
        x0 = sx - 1.8
        m.box(x0, 24.6, 5.4, x0 + 3.6, 26.4, 10.6, DARK)
        m.box(x0 + 0.6, 26.4, 6.2, x0 + 3.0, 27.4, 9.8, LAVA)
        m.box(x0 + 1.0, 27.4, 6.8, x0 + 2.6, 30.4, 9.2, FIRE)
        m.box(x0 + 1.4, 30.4, 7.4, x0 + 2.2, 32.6, 8.6, HOT)
    for (x0, y0, x1, y1) in [(1.4, 20.6, 4.2, 21.1), (11.8, 21.6, 14.6, 22.1), (3.2, 12.2, 3.7, 16.6), (12.4, 18.6, 12.9, 23.4), (5.2, 24.0, 10.8, 24.5)]:
        m.box(x0, y0, 12.55, x1, y1, 12.85, LAVA)
    for k, x in enumerate((4.6, 6.4, 8.0, 9.6, 11.4)):                                   # 녹아내린 왕관
        hh = 1.6 + (1.2 if k == 2 else 0.6 if k in (1, 3) else 0)
        m.box(x - 0.45, 29.6, 10.4, x + 0.45, 29.6 + hh, 11.2, HOT if k % 2 else LAVA)
    for k, z in enumerate((4.6, 6.8, 9.0)):                                              # 등의 바위 산맥
        m.box(3.0 + k, 24.0, 2.6 - k * 0.2, 13.0 - k, 26.6 + k, 4.2, ROCK2, rot=("x", 22.5, (8, 24, 3.4)))


def void_apostle(m):
    """공허의 사도 네퓨라 — 끝없이 긴 로브, 두건 속 빈 얼굴과 보랏빛 눈, 공허의 날개 파편, 회전하는 공허 고리 셋, 떠도는 눈"""
    ROBE, ROBE2, VOID, GLOW, RUNE, BONE = "1a1024", "241a34", "0a0610", "e080ff", "8a4ad0", "d8c8e8"
    for i, (w, y0, y1) in enumerate([(4.6, 0, 4), (4.0, 4, 9), (3.4, 9, 22)]):
        m.box(8 - w, y0, 8 - w * 0.8, 8 + w, y1, 8 + w * 0.8, ROBE if i % 2 == 0 else ROBE2)
    for k in range(5):                                                              # 해진 자락
        m.box(4.0 + k * 1.8, 0, 11.6, 5.0 + k * 1.8, 1.4 + (k % 2), 12.2, ROBE2)
    for y in (6, 12, 18):                                                            # 룬 띠
        m.box(4.3, y, 10.6, 11.7, y + 0.5, 10.9, RUNE)
    m.sym(1.8, 12, 6.2, 4.6, 21, 9.8, ROBE2)                                          # 늘어진 소매
    m.sym(1.6, 10.4, 6.6, 3.4, 12.0, 9.4, BONE)                                       # 앙상한 손
    m.box(4.8, 21.8, 5.4, 11.2, 28.6, 11.2, VOID)                                     # 두건
    m.box(5.6, 22.4, 11.1, 10.4, 27.4, 11.4, "000000")                                # 빈 얼굴
    eyes(m, 24.8, GLOW, z=11.35, gap=0.9, size=0.8)
    m.box(7.4, 28.4, 7.4, 8.6, 30.6, 8.6, RUNE, rot=("y", 45, (8, 29.5, 8)))           # 두건 위 결정
    m.box(7.7, 30.4, 7.7, 8.3, 31.6, 8.3, GLOW)
    for (y, r, ax) in [(10, 10, "x"), (17, 12, "z"), (24, 8, "x")]:                     # 공허 고리 3개
        m.box(8 - r, y, 7.7, 8 + r, y + 0.6, 8.3, RUNE, rot=(ax, 22.5, (8, y, 8)))
        m.box(7.7, y, 8 - r, 8.3, y + 0.6, 8 + r, RUNE, rot=("z" if ax == "x" else "x", 22.5, (8, y, 8)))
    for sgn, ox in ((-1, 4.0), (1, 12.0)):                                             # 공허 날개 파편
        for k in range(4):
            rz = 45 if sgn < 0 else -45
            x = ox + sgn * (3 + k * 2.4)
            m.box(x - 0.6, 18 + k * 1.6, 3.8, x + 0.6, 22 + k * 1.6, 4.4, VOID if k % 2 else RUNE, rot=("z", rz, (x, 20 + k * 1.6, 4.1)))
    for (x, y, z) in [(-6, 28, 8), (22, 22, 6), (8, 36, 10)]:                           # 떠도는 눈
        m.box(x - 1, y - 1, z - 1, x + 1, y + 1, z + 1, BONE)
        m.box(x - 0.5, y - 0.5, z + 0.9, x + 0.5, y + 0.5, z + 1.1, GLOW)


def thunder_god(m):
    """뇌신 토르반 — 황금 판금과 푸른 망토, 날개 달린 투구, 번개 볏, 뒤쪽 후광 고리, 번개 창, 떠도는 번개 조각"""
    SKIN, GOLD, GOLD2, CAPE, BOLT, WHITE, DARK = "e8d8b0", "e0b030", "ffd23f", "3f7fff", "6bd8ff", "fff3b0", "7a5a2a"
    hero(m, SKIN, GOLD, DARK, GOLD2, BOLT, w=1.25, h=1.2)
    for y in (14.4, 16.4, 18.4):                                                     # 복근 판
        m.box(4.6, y * 1.0, 10.65, 11.4, y + 0.5, 10.95, GOLD2)
    m.sym(-1.2, 22.0, 4.8, 3.2, 25.6, 11.2, GOLD2)                                   # 날개 모양 견갑
    m.sym(-2.6, 23.4, 6.4, 0.2, 27.2, 9.6, WHITE, rot=("z", 22.5, (-1.2, 25.3, 8)))
    m.box(4.8, 24.6, 5.2, 11.2, 30.4, 11.0, GOLD)                                     # 투구
    m.box(5.2, 26.8, 10.9, 10.8, 27.6, 11.3, BOLT)                                    # 눈 틈 (번개빛)
    m.box(7.4, 30.4, 6.6, 8.6, 34.4, 9.4, BOLT)                                       # 번개 볏
    m.box(7.2, 33.0, 6.4, 8.8, 35.4, 7.6, WHITE, rot=("x", 22.5, (8, 34, 7)))
    m.sym(3.0, 27.0, 7.0, 4.8, 28.2, 9.0, WHITE)                                      # 투구 날개
    m.sym(1.4, 28.0, 7.2, 3.2, 31.6, 8.8, WHITE, rot=("z", 22.5, (2.3, 28, 8)))
    for k in range(8):                                                               # 뒤쪽 후광
        import math as _m
        a = k * _m.pi / 4
        x, y = 8 + _m.cos(a) * 7, 28 + _m.sin(a) * 7
        m.box(x - 0.6, y - 0.6, 3.0, x + 0.6, y + 0.6, 3.6, WHITE if k % 2 else BOLT)
    m.box(3.0, 4, 3.6, 13.0, 23, 4.4, CAPE)                                           # 망토
    m.box(3.2, 3, 3.8, 5.4, 5, 4.6, CAPE, rot=("x", 22.5, (4.3, 4, 4.2)))
    m.box(10.6, 3, 3.8, 12.8, 5, 4.6, CAPE, rot=("x", 22.5, (11.7, 4, 4.2)))
    _w = m.mark()
    m.box(17.0, 0, 7.4, 18.4, 34, 8.6, WHITE)                                         # 번개 창
    m.box(16.4, 34, 7.0, 19.0, 38, 9.0, BOLT, rot=("z", 22.5, (17.7, 36, 8)))
    m.box(15.6, 30.0, 7.2, 19.8, 31.0, 8.8, GOLD2)
    m.hold(_w, (17.7, 13.8, 8.0), (14.44, 13.8, 8.0))   # v5.5.0: 무기를 손에 쥠
    for (x, y, z, a) in [(-6, 16, 6, 45), (22, 20, 10, -45), (-4, 30, 12, 22.5), (21, 8, 2, -22.5)]:
        m.box(x - 0.4, y - 2, z - 0.4, x + 0.4, y + 2, z + 0.4, BOLT, rot=("z", a, (x, y, z)))


def primordial_dragon(m):
    """태초의 용 아스트라 — v5.8.2: 작은 정육면체 수천 개로 깎은 복셀 조각 (tools/voxel_dragon.py)"""
    import voxel_dragon as vd
    g = vd.Grid()
    vd.build(g)
    vd.emit(g, m)


def vengeful_spirit(m):
    """몬스터의 원혼 — 다리 없이 떠 있는 거대한 망령: 흐려지며 흩어지는 누더기 자락(4겹), 해골 얼굴과 벌어진 턱,
    푸른 영혼불 눈, 뿔 달린 두건, 몸을 휘감은 쇠사슬, 길고 날카로운 유령 손톱, 영혼 등불, 주위를 도는 영혼 구슬 6개"""
    import math as _m
    ROBE, ROBE2, ROBE3, FADE, BONE, SOUL, SOUL2, CHAIN, VOID = "3a4a6a", "2a3a5a", "1e2c46", "15203a", "e6e8f0", "7fe8ff", "c8f8ff", "6a6a7a", "05080f"
    for i, (w, y0, y1, c) in enumerate([(2.6, 0, 2.5, FADE), (3.4, 2.5, 5, ROBE3), (4.2, 5, 8, ROBE2), (4.6, 8, 12, ROBE)]):   # 흩어지는 자락
        m.box(8 - w, y0, 8 - w * 0.8, 8 + w, y1, 8 + w * 0.8, c)
    for k in range(7):                                                                   # 찢어진 끝자락
        a = k * _m.pi * 2 / 7
        x, z = 8 + _m.cos(a) * 3.8, 8 + _m.sin(a) * 3.0
        m.box(x - 0.5, 0.5 + (k % 3) * 0.8, z - 0.5, x + 0.5, 5, z + 0.5, ROBE3 if k % 2 else FADE)
    m.box(3.6, 12, 5.0, 12.4, 21.6, 11.0, ROBE)                                          # 몸통
    m.box(4.2, 13, 10.9, 11.8, 21.0, 11.5, ROBE2)
    for y in (14.0, 15.6, 17.2):                                                          # 드러난 갈비뼈
        m.box(5.4, y, 11.4, 10.6, y + 0.5, 11.8, BONE)
    m.box(7.6, 13.6, 11.4, 8.4, 19.0, 11.9, BONE)
    m.box(7.2, 16.0, 11.8, 8.8, 17.4, 12.2, SOUL)                                         # 가슴 속 영혼불
    for k in range(4):                                                                    # 몸을 휘감은 쇠사슬
        m.box(3.2, 12.6 + k * 2.2, 10.9, 12.8, 13.2 + k * 2.2, 11.6, CHAIN, rot=("z", 22.5 if k % 2 else -22.5, (8, 13 + k * 2.2, 11.2)))
    m.sym(0.6, 17.6, 6.0, 3.8, 21.6, 10.2, ROBE2)                                          # 넓은 어깨 천
    m.sym(-0.6, 13.0, 6.6, 2.4, 18.0, 9.6, ROBE3, rot=("z", 22.5, (1, 18, 8)))              # 팔
    m.sym(-3.2, 9.4, 6.8, -0.2, 13.2, 9.4, SOUL2)                                          # 유령 손
    for k in range(3):                                                                    # 긴 손톱
        m.sym(-4.4 + k * 1.2, 6.0, 7.2 + k * 0.6, -3.8 + k * 1.2, 9.6, 7.8 + k * 0.6, BONE, rot=("x", 22.5, (-4 + k * 1.2, 9.6, 7.5)))
    m.box(4.6, 21.4, 5.0, 11.4, 28.4, 11.4, ROBE3)                                        # 두건
    m.box(5.0, 27.6, 5.4, 11.0, 29.0, 10.6, ROBE3)
    m.sym(3.4, 26.6, 7.0, 4.8, 30.0, 8.4, VOID, rot=("z", 22.5, (4.1, 26.6, 7.7)))          # 두건 뿔
    m.sym(2.6, 29.4, 7.2, 3.6, 32.0, 8.2, VOID, rot=("z", -22.5, (3.1, 29.4, 7.7)))
    m.box(5.6, 22.0, 10.8, 10.4, 27.2, 11.6, VOID)                                        # 어둠 속
    m.box(6.0, 23.6, 11.4, 10.0, 27.0, 12.0, BONE)                                        # 해골 얼굴
    m.box(6.6, 25.2, 11.9, 7.8, 26.2, 12.2, SOUL)                                         # 영혼불 눈
    m.box(8.2, 25.2, 11.9, 9.4, 26.2, 12.2, SOUL)
    m.box(6.9, 25.5, 12.1, 7.5, 25.9, 12.4, SOUL2)
    m.box(8.5, 25.5, 12.1, 9.1, 25.9, 12.4, SOUL2)
    m.box(6.4, 21.6, 11.3, 9.6, 23.4, 12.0, BONE, rot=("x", 22.5, (8, 23.4, 11.6)))        # 벌어진 턱
    for x in (6.8, 7.6, 8.4, 9.2):
        m.box(x, 23.2, 11.9, x + 0.4, 23.8, 12.2, VOID)
    m.box(17.0, 6.0, 7.6, 17.8, 18.0, 8.4, CHAIN)                                          # 영혼 등불 사슬
    m.box(16.0, 2.4, 6.6, 18.8, 6.0, 9.4, "2a2a34")                                        # 등불
    m.box(16.4, 2.8, 7.0, 18.4, 5.6, 9.0, SOUL)
    m.box(16.8, 3.4, 7.4, 18.0, 5.0, 8.6, SOUL2)
    for k in range(6):                                                                    # 도는 영혼 구슬
        a = k * _m.pi / 3
        x, z = 8 + _m.cos(a) * 12, 8 + _m.sin(a) * 12
        y = 14 + (k % 3) * 4
        m.box(x - 0.9, y - 0.9, z - 0.9, x + 0.9, y + 0.9, z + 0.9, SOUL, rot=("y", 45, (x, y, z)))
        m.box(x - 0.4, y + 0.9, z - 0.4, x + 0.4, y + 2.2, z + 0.4, SOUL2)                 # 불꽃 꼬리

def balrog(m):
    """발록 — 불꽃의 대악마: 굽은 뿔, 불타는 눈과 입, 근육질 상체와 용암 균열, 박쥐 날개, 불꽃 채찍과 도끼, 굽은 다리와 발굽, 꼬리"""
    SKIN, DARK, LAVA, FIRE, HORN, WING, BONE = "5a1a14", "2a0a08", "ff5a1f", "ffd23f", "d8c8a8", "3a0a0a", "e8e0d0"
    # 역관절 다리 · 발굽
    m.sym(3.6, 0, 7.2, 7.0, 1.2, 11.4, DARK)
    m.sym(4.0, 1.2, 8.8, 6.8, 5.0, 10.6, SKIN, rot=("x", -22.5, (5.4, 3, 9.6)))
    m.sym(3.8, 5.0, 6.0, 7.0, 9.6, 9.4, SKIN, rot=("x", 22.5, (5.4, 7, 7.6)))
    m.sym(3.4, 9.0, 5.8, 7.4, 12.0, 10.0, SKIN)                                   # 허벅지
    # 허리 · 뼈 장식 벨트
    m.box(3.0, 11.6, 5.6, 13.0, 13.0, 10.4, DARK)
    for x in (4.4, 6.4, 8.4, 10.4):
        m.box(x, 11.2, 10.3, x + 1.0, 12.8, 10.9, BONE)
    # 근육질 상체 · 용암 균열
    m.box(2.6, 13.0, 5.4, 13.4, 22.4, 10.6, SKIN)
    m.box(3.4, 17.0, 10.5, 7.6, 21.4, 11.2, SKIN)                                  # 가슴 근육
    m.box(8.4, 17.0, 10.5, 12.6, 21.4, 11.2, SKIN)
    for (x, y, w) in [(4.0, 14.0, 3.0), (9.0, 14.8, 3.2), (6.6, 16.2, 2.8), (5.0, 19.2, 1.6), (10.2, 19.6, 1.8)]:
        m.box(x, y, 11.15, x + w, y + 0.5, 11.35, LAVA)
    m.box(7.2, 13.4, 10.55, 8.8, 16.6, 11.0, FIRE)                                 # 배 한가운데 불꽃
    # 어깨 · 팔 (굵게)
    m.sym(-0.6, 19.4, 5.2, 2.8, 23.2, 10.8, SKIN)
    m.sym(-0.4, 14.2, 5.8, 2.6, 19.6, 10.2, SKIN)
    m.sym(-0.8, 11.0, 5.4, 2.8, 14.4, 10.6, DARK)                                  # 손
    m.sym(-1.0, 22.6, 7.2, 0.4, 25.4, 8.8, HORN, rot=("z", 22.5, (-0.3, 22.6, 8)))  # 어깨 가시
    # 머리 · 뿔 · 눈 · 입
    m.box(5.0, 22.4, 6.2, 11.0, 28.2, 11.6, SKIN)
    m.box(5.4, 22.6, 11.5, 10.6, 24.0, 12.2, DARK)
    m.box(5.8, 22.9, 12.1, 10.2, 23.6, 12.4, FIRE)                                 # 불타는 입
    m.box(5.6, 25.6, 11.5, 7.2, 26.6, 12.0, FIRE)                                  # 눈
    m.box(8.8, 25.6, 11.5, 10.4, 26.6, 12.0, FIRE)
    m.box(5.2, 26.6, 11.5, 10.8, 27.2, 11.9, DARK)                                 # 이마 주름
    m.sym(3.0, 26.4, 7.4, 5.2, 28.0, 9.4, HORN)                                    # 굽은 뿔
    m.sym(1.8, 27.4, 7.6, 3.4, 30.8, 9.2, HORN, rot=("z", 22.5, (2.6, 27.4, 8.4)))
    m.sym(1.0, 30.2, 7.8, 2.4, 32.0, 9.0, "b8a888", rot=("z", 45, (1.7, 30.2, 8.4)))
    # 박쥐 날개 (뼈대 + 막)
    for sign, ox in ((-1, 3.0), (1, 13.0)):
        rz = -22.5 if sign < 0 else 22.5
        x0, x1 = (ox - 13, ox) if sign < 0 else (ox, ox + 13)
        m.box(x0, 20.0, 4.0, x1, 21.0, 5.0, DARK, rot=("z", rz, (ox, 20.5, 4.5)))           # 날개 뼈
        m.box(x0, 12.0, 4.2, x1, 20.0, 4.6, WING, rot=("z", rz, (ox, 20.5, 4.5)))           # 날개 막
        for k in range(3):
            fx = ox + sign * (4 + k * 4)
            m.box(fx - 0.4, 11.0, 4.0, fx + 0.4, 20.0, 4.9, DARK, rot=("z", rz, (ox, 20.5, 4.5)))
    # 꼬리
    m.box(7.2, 6.0, 1.0, 8.8, 8.0, 6.0, SKIN, rot=("x", 22.5, (8, 7, 5.5)))
    m.box(7.4, 3.0, -3.0, 8.6, 4.6, 1.4, SKIN, rot=("x", -22.5, (8, 4, 1)))
    m.box(7.0, 2.4, -5.0, 9.0, 3.4, -3.0, HORN)
    # 불꽃 채찍 (왼손) · 도끼 (오른손)
    for k in range(6):
        m.box(-1.6 - k * 0.2, 10.0 - k * 1.6, 7.4 + k * 1.2, -0.6 - k * 0.2, 11.0 - k * 1.6, 8.4 + k * 1.2, FIRE if k % 2 else LAVA)
    _w = m.mark()
    m.box(17.0, 4.0, 7.4, 18.2, 22.0, 8.6, DARK, rot=("z", -22.5, (17.6, 13, 8)))
    m.box(17.6, 18.0, 6.6, 23.0, 24.0, 9.4, "3a3a44", rot=("z", -22.5, (17.6, 13, 8)))
    m.box(22.0, 18.6, 6.8, 23.4, 23.4, 9.2, LAVA, rot=("z", -22.5, (17.6, 13, 8)))
    m.hold(_w, (17.6, 8.0, 8.0), (15.0, 12.7, 8.0))   # v5.5.0: 무기를 손에 쥠
    # 불씨
    for (x, y, z) in [(-5, 28, 8), (21, 30, 6), (3, 34, 12), (14, 33, 3)]:
        m.box(x - 0.5, y - 0.5, z - 0.5, x + 0.5, y + 0.5, z + 0.5, FIRE)


# ---------------------------------------------------------------- 네발짐승 틀 (탈것)
def quad(m, body, belly, dark, L=14.0, H=7.0, W=6.0, leg=6.0, neck=True):
    z0 = 8 - L / 2
    m.box(8 - W / 2, leg, z0, 8 + W / 2, leg + H, z0 + L, body)                   # 몸통
    m.box(8 - W / 2 + 0.6, leg - 0.4, z0 + 1, 8 + W / 2 - 0.6, leg + 1, z0 + L - 1, belly)
    for zz in (z0 + 1.2, z0 + L - 3.2):                                           # 다리 4개 + 발
        m.sym(8 - W / 2 + 0.2, 1.0, zz, 8 - W / 2 + 2.2, leg + 1, zz + 2.0, body)
        m.sym(8 - W / 2, 0, zz - 0.2, 8 - W / 2 + 2.4, 1.2, zz + 2.4, dark)
    if neck:
        m.box(8 - 1.8, leg + H - 2, z0 + L - 1, 8 + 1.8, leg + H + 3.2, z0 + L + 2.4, body, rot=("x", -22.5, (8, leg + H, z0 + L)))
    m.box(8 - 1.2, leg + H - 1, z0 - 4, 8 + 1.2, leg + H + 0.6, z0, body, rot=("x", 22.5, (8, leg + H, z0)))   # 꼬리
    return z0, leg, H, W, L


def _head(m, z, y, col, eye, W=4.4, D=5.0, H=4.0, snout=None):
    m.box(8 - W / 2, y, z, 8 + W / 2, y + H, z + D, col)
    if snout:
        m.box(8 - W / 3, y, z + D, 8 + W / 3, y + H * 0.55, z + D + 2.2, snout)
    m.box(8 - W / 2 + 0.4, y + H * 0.6, z + D - 0.05, 8 - W / 2 + 1.3, y + H * 0.85, z + D + 0.15, eye)
    m.box(8 + W / 2 - 1.3, y + H * 0.6, z + D - 0.05, 8 + W / 2 - 0.4, y + H * 0.85, z + D + 0.15, eye)


def _saddle(m, y, z0, L, col, trim):
    m.box(8 - 3.4, y, z0 + L * 0.35, 8 + 3.4, y + 0.8, z0 + L * 0.65, col)
    m.box(8 - 3.5, y + 0.8, z0 + L * 0.35, 8 + 3.5, y + 1.6, z0 + L * 0.4, trim)
    m.sym(8 - 3.9, y - 3.5, z0 + L * 0.45, 8 - 3.4, y + 0.4, z0 + L * 0.55, trim)


def mount_wolf(m):
    z0, leg, H, W, L = quad(m, "8a8f96", "c8ccd2", "3a3a40", L=13, H=6, W=5.2, leg=6)
    _head(m, z0 + L + 1.0, leg + H + 0.5, "8a8f96", "ffd23f", snout="c8ccd2")
    m.sym(8 - 2.2, leg + H + 4.3, z0 + L + 1.4, 8 - 1.0, leg + H + 6.0, z0 + L + 2.4, "6a6f76")   # 귀
    _saddle(m, leg + H, z0, L, "6a3a1a", "c9a13b")


def mount_lizard(m):
    z0, leg, H, W, L = quad(m, "c9a15a", "f0dca0", "6a4a1a", L=16, H=4.5, W=6.5, leg=3.5, neck=False)
    _head(m, z0 + L, leg + 1.2, "c9a15a", "ff3a3a", W=4.6, D=5.5, H=3.2, snout="b08a40")
    for k in range(5):
        m.box(7.5, leg + H, z0 + 2 + k * 2.6, 8.5, leg + H + 1.4, z0 + 3.4 + k * 2.6, "e07a2a")   # 등 돌기
    m.box(7, leg + 1, z0 - 9, 9, leg + 2.4, z0, "c9a15a", rot=("x", 22.5, (8, leg + 2, z0)))
    _saddle(m, leg + H, z0, L, "5a3a2a", "e0b030")


def mount_warhorse(m):
    z0, leg, H, W, L = quad(m, "5a3a22", "7a5a3a", "2a1a10", L=15, H=7.5, W=6.0, leg=8)
    _head(m, z0 + L + 1.8, leg + H + 2.4, "5a3a22", "1a1a1a", W=3.6, D=6.0, H=3.6, snout="7a5a3a")
    m.box(7.4, leg + H - 1, z0 + L - 2, 8.6, leg + H + 6.4, z0 + L + 1.4, "1a1a1a")   # 갈기
    m.box(8 - 3.2, leg + 1, z0 + 1, 8 + 3.2, leg + H + 0.4, z0 + L - 1, "8a8f96")        # 마갑
    m.box(8 - 3.3, leg + 3, z0 + 1, 8 + 3.3, leg + 3.6, z0 + L - 1, "c9a13b")
    m.box(8 - 2, leg + H + 2.2, z0 + L + 2, 8 + 2, leg + H + 6, z0 + L + 6, "8a8f96")    # 투구
    m.box(7.6, leg + H + 6, z0 + L + 4, 8.4, leg + H + 9, z0 + L + 5, "c02a2a")          # 깃털
    _saddle(m, leg + H + 0.4, z0, L, "8a1a1a", "e0b030")


def mount_icebear(m):
    z0, leg, H, W, L = quad(m, "e8f4ff", "ffffff", "8ab0d0", L=14, H=8, W=8, leg=5.5)
    _head(m, z0 + L + 0.5, leg + H - 1, "e8f4ff", "1a2a3a", W=5, D=4.6, H=4.4, snout="d0e4f4")
    m.sym(8 - 2.6, leg + H + 3.2, z0 + L + 1, 8 - 1.4, leg + H + 4.2, z0 + L + 2, "d0e4f4")
    for (x, zz) in [(4.4, z0 + 3), (11.6, z0 + 8), (8, z0 + 5.5)]:                      # 얼음 결정
        m.box(x - 0.6, leg + H, zz, x + 0.6, leg + H + 3, zz + 1.2, "7fe8ff", rot=("z", 22.5, (x, leg + H, zz)))
    _saddle(m, leg + H, z0, L, "2a4a6a", "bff4ff")


def mount_lion(m):
    z0, leg, H, W, L = quad(m, "e0a040", "f0c880", "7a4a1a", L=14, H=6.5, W=6, leg=7)
    m.box(8 - 3.6, leg + H - 2.4, z0 + L - 1.6, 8 + 3.6, leg + H + 5.2, z0 + L + 2.4, "ff5a1f")   # 불꽃 갈기
    m.box(8 - 3.0, leg + H + 3.6, z0 + L - 1.0, 8 + 3.0, leg + H + 6.6, z0 + L + 1.6, "ffd23f")
    _head(m, z0 + L + 1.4, leg + H - 0.6, "e0a040", "ff3a1a", W=4.2, D=4.4, H=4.0, snout="f0c880")
    m.box(7.2, leg + H + 0.4, z0 - 6, 8.8, leg + H + 1.8, z0 - 4, "ff5a1f")               # 꼬리 불꽃
    _saddle(m, leg + H, z0, L, "6a1a0a", "ffd23f")


def mount_panther(m):
    z0, leg, H, W, L = quad(m, "1a1a24", "2a2a38", "0a0a10", L=15, H=5.5, W=5.2, leg=6.5)
    _head(m, z0 + L + 0.8, leg + H - 0.4, "1a1a24", "b060ff", W=3.8, D=4.4, H=3.4, snout="2a2a38")
    m.sym(8 - 1.8, leg + H + 3.0, z0 + L + 1.0, 8 - 0.8, leg + H + 4.2, z0 + L + 1.8, "1a1a24")
    for k in range(4):                                                                  # 보랏빛 무늬
        m.box(8 - 2.7, leg + 2 + k, z0 + 2 + k * 3, 8 + 2.7, leg + 2.4 + k, z0 + 2.8 + k * 3, "8a4ad0")
    _saddle(m, leg + H, z0, L, "3a1a5a", "c060ff")


def mount_griffin(m):
    z0, leg, H, W, L = quad(m, "d8b060", "f4e0a0", "6a4a1a", L=14, H=7, W=6, leg=7)
    m.box(8 - 2.4, leg + H - 1, z0 + L - 2, 8 + 2.4, leg + H + 5, z0 + L + 2, "f4f4f8")      # 흰 깃 목
    _head(m, z0 + L + 1.0, leg + H + 3.6, "f4f4f8", "ffd23f", W=3.8, D=4.0, H=3.6)
    m.box(7.2, leg + H + 3.6, z0 + L + 5, 8.8, leg + H + 5.4, z0 + L + 7.4, "e0b030", rot=("x", 22.5, (8, leg + H + 4.5, z0 + L + 5)))   # 부리
    for sg, ox in ((-1, 8 - W / 2), (1, 8 + W / 2)):                                      # 날개
        rz = 22.5 if sg < 0 else -22.5
        x0, x1 = (ox - 12, ox) if sg < 0 else (ox, ox + 12)
        m.box(x0, leg + H - 0.4, z0 + 4, x1, leg + H + 0.6, z0 + 11, "f4f4f8", rot=("z", rz, (ox, leg + H, z0 + 7)))
        m.box(x0, leg + H - 0.6, z0 + 3, x1, leg + H - 0.2, z0 + 5, "e0b030", rot=("z", rz, (ox, leg + H, z0 + 7)))
    _saddle(m, leg + H, z0, L, "6a3a1a", "e0b030")


def mount_dragon(m):
    z0, leg, H, W, L = quad(m, "3a1a5a", "c9a13b", "1a0a24", L=16, H=7, W=6.5, leg=7)
    m.box(8 - 1.8, leg + H - 1, z0 + L - 1, 8 + 1.8, leg + H + 5, z0 + L + 3.4, "3a1a5a", rot=("x", -22.5, (8, leg + H, z0 + L)))
    _head(m, z0 + L + 2.4, leg + H + 3.6, "3a1a5a", "ff3030", W=4.2, D=5.0, H=3.4, snout="4a2260")
    m.sym(8 - 2.0, leg + H + 6.6, z0 + L + 2.6, 8 - 1.2, leg + H + 9.4, z0 + L + 3.4, "fff3b0", rot=("x", 22.5, (8, leg + H + 7, z0 + L + 3)))   # 뿔
    for k in range(5):
        m.box(7.5, leg + H, z0 + 1.5 + k * 3, 8.5, leg + H + 1.6, z0 + 2.9 + k * 3, "fff3b0")
    for sg, ox in ((-1, 8 - W / 2), (1, 8 + W / 2)):
        rz = 22.5 if sg < 0 else -22.5
        x0, x1 = (ox - 14, ox) if sg < 0 else (ox, ox + 14)
        m.box(x0, leg + H, z0 + 3, x1, leg + H + 1, z0 + 4, "1a0a24", rot=("z", rz, (ox, leg + H, z0 + 8)))
        m.box(x0, leg + H + 0.1, z0 + 4, x1, leg + H + 0.5, z0 + 12, "5a2a7a", rot=("z", rz, (ox, leg + H, z0 + 8)))
    _saddle(m, leg + H, z0, L, "1a0a24", "c060ff")


def megalodon(m):
    """메갈로돈 — 흉터투성이 고대 상어: 앞뒤로 가늘어지는 몸, 짙은 등과 흰 배, 높은 등지느러미, 큰 가슴지느러미,
    초승달 꼬리, 벌어진 입과 두 줄 톱니 이빨, 빛나는 눈, 몸에 박힌 작살"""
    G, G2, G3, B, T, E, SCAR, IRON, MOUTH = "5a6a7a", "3a4a5a", "6f8090", "e8eef4", "ffffff", "7fe8ff", "a8b4c0", "5a4a3a", "3a1a22"
    m.box(3.4, 3.6, 0, 12.6, 12.4, 16, G)                                              # 몸통 (가운데가 가장 굵음)
    m.box(4.2, 4.2, -6, 11.8, 11.6, 0, G)
    m.box(5.0, 5.0, -11, 11.0, 10.8, -6, G)
    m.box(4.2, 4.2, 16, 11.8, 11.8, 22, G)
    m.box(5.0, 5.0, 22, 11.0, 11.0, 28, G)                                             # 머리
    m.box(5.8, 6.6, 28, 10.2, 10.6, 31, G)                                             # 주둥이
    m.box(4.2, 11.8, 0, 11.8, 12.9, 16, G2)                                            # 짙은 등
    m.box(4.8, 11.2, -6, 11.2, 12.1, 0, G2)
    m.box(5.2, 11.4, 16, 10.8, 12.3, 22, G2)
    m.box(5.6, 10.6, 22, 10.4, 11.5, 28.5, G2)
    m.box(4.0, 3.1, -4, 12.0, 4.6, 20, B)                                              # 흰 배
    m.box(5.4, 5.0, 23.5, 10.6, 6.8, 30.2, MOUTH)                                      # 벌린 입
    m.box(5.4, 2.4, 22, 10.6, 5.0, 29.8, B)                                            # 내려간 아래턱
    for x in (5.6, 6.7, 7.8, 8.9, 10.0):                                               # 두 줄 톱니 이빨
        m.box(x, 5.6, 29.8, x + 0.6, 6.8, 30.4, T)
        m.box(x, 5.0, 29.2, x + 0.6, 6.0, 29.8, T)
    for z in (24.6, 26.2, 27.8):
        m.sym(5.3, 5.6, z, 5.9, 6.6, z + 0.7, T)
        m.sym(5.3, 5.0, z + 0.6, 5.9, 5.8, z + 1.2, T)
    m.sym(4.85, 8.4, 25.8, 5.2, 9.6, 27.2, E)                                          # 빛나는 눈
    m.sym(4.8, 8.6, 25.4, 5.25, 9.8, 25.8, G2)
    for z in (18.6, 20.0, 21.4):                                                       # 아가미
        m.sym(4.05, 6.4, z, 4.3, 10.2, z + 0.5, G2)
    m.box(7.1, 12.4, 5, 8.9, 21, 12, G2, rot=("x", 22.5, (8, 12.4, 8.5)))              # 높은 등지느러미
    m.box(7.3, 12.4, 10.8, 8.7, 18, 12.4, G3, rot=("x", 22.5, (8, 12.4, 8.5)))
    m.box(7.4, 10.6, -9.5, 8.6, 13.6, -6.5, G2, rot=("x", 22.5, (8, 10.6, -8)))        # 작은 등지느러미
    m.sym(-1.4, 4.2, 12, 3.6, 5.4, 19, G2, rot=("z", -22.5, (3.6, 4.8, 15.5)))          # 가슴지느러미
    m.sym(2.4, 3.6, -3.5, 4.4, 4.6, 0, G2, rot=("z", -22.5, (4.4, 4.1, -1.8)))          # 배지느러미
    m.box(6.0, 6.2, -16, 10.0, 9.8, -11, G)                                            # 꼬리 자루 + 용골
    m.sym(5.6, 7.3, -16, 6.1, 8.7, -10.5, G3)
    m.box(7.2, 9, -21, 8.8, 21, -16, G2, rot=("x", -22.5, (8, 9, -17)))                # 초승달 꼬리 (위가 더 큼)
    m.box(7.2, -1.5, -20, 8.8, 7, -16, G2, rot=("x", 22.5, (8, 7, -17)))
    m.box(12.5, 7.0, 3, 12.75, 7.6, 11, SCAR, rot=("x", 22.5, (12.6, 7.3, 7)))          # 흉터
    m.box(12.5, 9.0, 5, 12.75, 9.5, 9, SCAR, rot=("x", -22.5, (12.6, 9.2, 7)))
    m.box(3.25, 8.0, 8, 3.5, 8.5, 14, SCAR, rot=("x", 22.5, (3.4, 8.2, 11)))
    m.box(5.6, 11.0, 19, 6.0, 11.6, 21.5, SCAR)
    for (x, z, s) in ((10.0, 2.0, -1), (6.0, -3.0, 1)):                                  # 박힌 작살 (줄 달린 쇠 자루)
        m.box(x - 0.3, 12.2, z - 0.3, x + 0.3, 19.0, z + 0.3, IRON, rot=("z", 22.5 * s, (x, 12.2, z)))
        m.box(x - 0.6, 12.2, z - 0.6, x + 0.6, 13.2, z + 0.6, "c8ccd2", rot=("z", 22.5 * s, (x, 12.2, z)))
        m.box(x - 0.15, 18.4, z - 0.15, x + 0.15, 18.8, z + 3.5, "d8c8a0", rot=("z", 22.5 * s, (x, 12.2, z)))


def kraken(m):
    """크라켄 — 심해의 거대 두족류: 거대한 외투막, 빛나는 두 눈, 8개의 굵은 촉수(빨판), 부리"""
    import math as _m
    S, S2, SK, E, SU = "7a1a4a", "5a0a34", "b04a7a", "ffd23f", "f0c0d8"
    m.box(2, 12, 2, 14, 30, 14, S)                                                     # 외투막
    m.box(3, 30, 3, 13, 32, 13, S2)
    for y in (16, 20, 24):
        m.box(1.9, y, 1.9, 14.1, y + 0.8, 14.1, S2)
    m.box(3, 18, 13.9, 6.4, 21.4, 14.6, E)                                             # 눈
    m.box(9.6, 18, 13.9, 13, 21.4, 14.6, E)
    m.box(4.2, 19, 14.5, 5.4, 20.6, 14.9, "1a0a0a")
    m.box(10.8, 19, 14.5, 12, 20.6, 14.9, "1a0a0a")
    m.box(6.8, 10, 12, 9.2, 12.4, 14.4, "2a1a1a")                                       # 부리
    for k in range(8):                                                                 # 촉수 8개
        a = k * _m.pi / 4
        dx, dz = _m.cos(a), _m.sin(a)
        for seg in range(4):
            r = 5 + seg * 4
            x, z = 8 + dx * r, 8 + dz * r
            y = 10 - seg * 2.4 + (1 if seg == 3 else 0)
            w = 2.4 - seg * 0.45
            m.box(x - w, y - 1.2, z - w, x + w, y + 1.2, z + w, S if seg % 2 == 0 else SK)
            m.box(x - 0.4, y - 1.3, z - 0.4, x + 0.4, y - 1.1, z + 0.4, SU)             # 빨판
        m.box(8 + dx * 21 - 0.8, 4, 8 + dz * 21 - 0.8, 8 + dx * 21 + 0.8, 10, 8 + dz * 21 + 0.8, SK,
              rot=("y", 45, (8 + dx * 21, 7, 8 + dz * 21)))                             # 치켜든 끝
    RUNE, BAR = "c060ff", "d8d0c0"
    for k in range(9):                                                                 # 외투막 위 가시 볏
        x = 3.6 + k * 1.1
        h = 2.0 + (4 - abs(k - 4)) * 0.9
        m.box(x, 32, 7.2, x + 0.8, 32 + h, 8.8, S2)
        m.box(x + 0.15, 32 + h, 7.5, x + 0.65, 32 + h + 0.7, 8.5, RUNE)
    for (y, x0, x1) in ((23.0, 4.0, 12.0), (26.6, 5.4, 10.6)):                            # 이마의 빛나는 룬
        m.box(x0, y, 13.95, x1, y + 0.5, 14.3, RUNE)
    m.box(7.6, 22.0, 13.95, 8.4, 28.0, 14.3, RUNE)
    for (x, y, z) in ((2.0, 26, 5), (13.4, 22, 9), (2.0, 14, 11), (13.4, 28, 4), (6, 31, 2.2)):   # 따개비
        m.box(x - 0.7, y - 0.7, z - 0.7, x + 0.7, y + 0.7, z + 0.7, BAR)
    m.box(2.4, 21.4, 13.9, 6.8, 22.0, 14.7, S2)                                        # 성난 눈썹
    m.box(9.2, 21.4, 13.9, 13.6, 22.0, 14.7, S2)
    for (x, s) in ((0.5, 1), (15.5, -1)):                                              # 앞에서 치켜든 거대 촉수 두 개
        for i in range(5):
            y = 10 + i * 4.2
            w = 1.9 - i * 0.25
            xx = x - s * (i * 0.9 if i < 3 else 1.8 - (i - 3) * 1.4)
            m.box(xx - w, y, 15 - w, xx + w, y + 4.4, 15 + w, S if i % 2 == 0 else SK)
            m.box(xx - 0.5, y + 1.2, 15 + w - 0.1, xx + 0.5, y + 2.2, 15 + w + 0.2, SU)
        m.box(x - s * -0.6 - 0.6, 31, 14.4, x - s * -0.6 + 0.6, 33.4, 15.6, SK, rot=("z", 22.5 * s, (x, 31, 15)))


# ---------------------------------------------------------------- 펫 (어깨 옆을 따라다니는 작은 동물, 앞 = +z)
def _eyes(m, y, z, col="1a1a1a", gap=1.6, size=1.0, shine=True):
    m.box(8 - gap - size, y, z, 8 - gap, y + size * 1.2, z + 0.3, col)
    m.box(8 + gap, y, z, 8 + gap + size, y + size * 1.2, z + 0.3, col)
    if shine:
        m.box(8 - gap - size + 0.2, y + size * 0.7, z + 0.25, 8 - gap - size + 0.6, y + size * 1.1, z + 0.4, "ffffff")
        m.box(8 + gap + 0.2, y + size * 0.7, z + 0.25, 8 + gap + 0.6, y + size * 1.1, z + 0.4, "ffffff")


def pet_slime(m):
    m.box(3, 0, 3, 13, 9, 13, "6fdc5a")                                   # 반투명 느낌 겉
    m.box(4.5, 1.5, 4.5, 11.5, 7.5, 11.5, "4fb83e")                       # 속
    m.box(3.4, 8.6, 3.4, 12.6, 9.4, 12.6, "a8f59a")                       # 윗면 하이라이트
    _eyes(m, 4.6, 13, gap=1.4, size=1.4)
    m.box(7, 3.2, 13, 9, 3.8, 13.3, "2a5a22")                             # 입
    m.box(4.2, 3.6, 13, 5.4, 4.2, 13.2, "ff9aa8")                         # 볼
    m.box(10.6, 3.6, 13, 11.8, 4.2, 13.2, "ff9aa8")


def pet_chick(m):
    m.box(4.5, 1.5, 4.5, 11.5, 8, 11.5, "ffe04a")                         # 몸
    m.box(5.5, 7.5, 6, 10.5, 11.5, 11, "ffe86a")                          # 머리
    m.box(7.2, 9, 11, 8.8, 10.2, 12.6, "ff9a2a")                          # 부리
    _eyes(m, 10, 11, gap=1.2, size=0.8)
    m.box(7.4, 11.5, 7.5, 8.6, 13, 8.5, "ffd02a")                         # 머리털
    m.sym(3.6, 3.5, 6, 4.5, 6.5, 10, "f5c830")                            # 날개
    m.sym(6.4, 0, 7.4, 7.2, 1.5, 8.2, "ff9a2a")                           # 다리
    m.sym(6, 0, 7.4, 7.6, 0.3, 9.4, "ff9a2a")


def pet_bunny(m):
    m.box(4.5, 0, 4, 11.5, 6.5, 11, "f4f0ea")                             # 몸
    m.box(5, 5.5, 8, 11, 10.5, 13, "ffffff")                              # 머리
    m.sym(5.6, 10.5, 9.6, 6.9, 16, 10.8, "ffffff", rot=("x", -22.5, (6.2, 10.5, 10.2)))   # 귀
    m.sym(5.9, 11, 10.3, 6.6, 15.2, 10.9, "ffb0c0", rot=("x", -22.5, (6.2, 10.5, 10.2)))
    _eyes(m, 8, 13, col="c0304a", gap=1.2, size=0.8)
    m.box(7.5, 7, 13, 8.5, 7.7, 13.4, "ff8aa0")                           # 코
    m.box(6.8, 2, 2.6, 9.2, 4.4, 4, "ffffff")                             # 꼬리
    m.sym(5, 0, 10.4, 6.6, 1, 12, "e8e0d6")                               # 앞발


def pet_fox(m):
    m.box(5, 2.5, 3.5, 11, 7.5, 11.5, "ff7a2a")                           # 몸
    m.box(5.4, 2.4, 8, 10.6, 5, 11.4, "fff0e0")                           # 가슴
    m.box(4.8, 6, 10, 11.2, 11, 14.5, "ff7a2a")                           # 머리
    m.box(6.3, 6, 14.5, 9.7, 8.2, 16.5, "fff0e0")                         # 주둥이
    m.box(7.4, 7.6, 16.4, 8.6, 8.5, 16.8, "1a1a1a")                       # 코
    _eyes(m, 9, 14.5, col="2a1a0a", gap=1.4, size=0.8)
    m.sym(5, 11, 11.2, 6.8, 13.4, 12.6, "ff7a2a")                         # 귀
    m.sym(5.4, 11, 11.8, 6.4, 12.6, 12.4, "2a1a0a")
    m.box(6.4, 4, -3, 9.6, 7.4, 3.6, "ff7a2a", rot=("x", 22.5, (8, 6, 3.5)))    # 꼬리
    m.box(6.4, 4.3, -5, 9.6, 7.2, -2.5, "ffd23f", rot=("x", 22.5, (8, 6, 3.5)))  # 불꽃 꼬리 끝
    for zz in (4, 9):
        m.sym(5.4, 0, zz, 6.8, 2.6, zz + 1.4, "5a2a10")


def pet_penguin(m):
    m.box(4.5, 0.8, 5, 11.5, 11, 11, "1e2430")                            # 몸
    m.box(5.3, 1, 10.6, 10.7, 9.4, 11.4, "f4f6fa")                        # 배
    m.box(5, 10.5, 5.5, 11, 15, 10.8, "1e2430")                           # 머리
    m.box(6, 11, 10.6, 10, 13.6, 11.1, "f4f6fa")                          # 얼굴
    _eyes(m, 12.4, 11.1, gap=1.0, size=0.7)
    m.box(7.2, 11.4, 11.1, 8.8, 12.2, 12.8, "ffa62a")                     # 부리
    m.sym(3.6, 3, 6.5, 4.5, 9, 9.5, "1e2430", rot=("z", 22.5, (4, 9, 8)))   # 날개
    m.sym(5.6, 0, 8.6, 7.6, 0.8, 11.6, "ffa62a")                          # 발
    m.box(5, 13.2, 8.8, 11, 13.8, 9.6, "3fb0ff")                          # 목도리
    m.box(9.8, 8.5, 9.4, 11.2, 13.8, 10.2, "3fb0ff")


def pet_owl(m):
    m.box(4.5, 1, 5, 11.5, 10, 11, "8a6a4a")                              # 몸
    m.box(5.5, 1.5, 10.6, 10.5, 8, 11.3, "d8c0a0")                        # 가슴
    m.box(4.5, 9.5, 5, 11.5, 15, 11.5, "8a6a4a")                          # 머리
    m.box(5, 10.5, 11.2, 11, 14, 11.8, "e8d8c0")                          # 얼굴 판
    m.box(5.5, 11.4, 11.7, 7.6, 13.5, 12.1, "ffd23f")                     # 큰 눈
    m.box(8.4, 11.4, 11.7, 10.5, 13.5, 12.1, "ffd23f")
    m.box(6.2, 12, 12, 7, 13, 12.3, "1a1a1a")
    m.box(9.1, 12, 12, 9.9, 13, 12.3, "1a1a1a")
    m.box(7.5, 10.6, 11.8, 8.5, 11.6, 12.8, "c08a2a")                     # 부리
    m.sym(4.6, 15, 6, 5.8, 17, 7.4, "6a4a2a")                             # 귀깃
    m.sym(3.6, 2.5, 5.5, 4.6, 9.5, 10.5, "6a4a2a")                        # 날개
    m.box(5.2, 15, 5.4, 10.8, 15.6, 11, "3a2a6a")                         # 학자 모자
    m.box(4.4, 15.6, 4.6, 11.6, 16.2, 11.8, "3a2a6a")
    m.box(10.8, 13.8, 8, 11.2, 16, 8.4, "ffd23f")                         # 모자 술


def pet_golem(m):
    STONE, MOSS, RUNE = "8a8a94", "5a8a3a", "5ad8ff"
    m.box(4, 3.5, 5, 12, 11, 11, STONE)                                   # 몸통
    m.box(5.5, 11, 6, 10.5, 15, 10.5, STONE)                              # 머리
    m.box(6, 12.5, 10.5, 10, 13.5, 10.8, RUNE)                            # 눈 띠
    m.box(7.2, 5.5, 10.9, 8.8, 9, 11.3, RUNE)                             # 가슴 룬
    m.box(6.2, 7, 10.9, 9.8, 7.6, 11.3, RUNE)
    m.sym(1.5, 3, 6.5, 4, 11, 9.5, STONE)                                 # 팔
    m.sym(1.3, 2, 6.3, 4.2, 4, 9.7, "6a6a74")                             # 주먹
    m.sym(5, 0, 6.5, 7.5, 3.5, 9.5, "6a6a74")                             # 다리
    m.box(4, 10.6, 5, 9, 11.4, 9, MOSS)                                   # 이끼
    m.box(10.5, 14.6, 7, 11, 15.4, 9, MOSS)
    m.box(5.5, 15, 7.5, 7, 16, 8.5, "ff6ab0")                             # 작은 꽃


def pet_fairy(m):
    SKIN, DRESS, WING = "ffe0c8", "5affa0", "c8fff0"
    m.box(6.5, 2, 6.5, 9.5, 7, 9.5, DRESS)                                # 드레스
    m.box(5.8, 1.5, 5.8, 10.2, 3.5, 10.2, "3ad880")
    m.box(6, 7, 6, 10, 11, 10, SKIN)                                      # 머리
    m.box(5.6, 9.5, 5.6, 10.4, 12, 9.4, "ffd23f")                         # 머리카락
    _eyes(m, 8.4, 10, col="2a6a4a", gap=0.6, size=0.7, shine=False)
    for sg in (-1, 1):                                                    # 날개 4장
        x0, x1 = (1.5, 7.5) if sg < 0 else (8.5, 14.5)
        rz = 22.5 if sg < 0 else -22.5
        m.box(x0, 6, 6.2, x1, 11.5, 6.6, WING, rot=("z", rz, (8, 8, 6.4)))
        m.box(x0 + 1, 2.5, 6.2, x1 - 1, 6.5, 6.6, "a8f0ff", rot=("z", -rz, (8, 6, 6.4)))
    m.box(9.8, 3, 8, 10.4, 9, 8.6, "c8a060")                              # 지팡이
    m.box(9.4, 9, 7.6, 10.8, 10.4, 9, "fff3b0")


def pet_ghost(m):
    BODY = "e8eeff"
    m.box(4.5, 3.5, 5, 11.5, 13, 11.5, BODY)                              # 몸
    m.box(5.2, 12.8, 5.7, 10.8, 14.2, 10.8, BODY)                         # 둥근 머리
    for k, x in enumerate((4.5, 6.3, 8.1, 9.9)):                          # 물결 치맛자락
        m.box(x, 1 + (k % 2) * 1.2, 5, x + 1.6, 3.6, 11.5, BODY)
    m.box(5.5, 9, 11.5, 7.3, 11.4, 11.8, "2a2a3a")                        # 눈
    m.box(8.7, 9, 11.5, 10.5, 11.4, 11.8, "2a2a3a")
    m.box(7, 6.6, 11.5, 9, 8.2, 11.8, "5a2a4a")                           # 입
    m.box(7.4, 6.6, 11.6, 8.6, 7.2, 11.9, "ff7aa0")                       # 혀
    m.sym(3.2, 7, 7, 4.5, 9, 9.5, BODY, rot=("z", -22.5, (4.5, 8, 8)))    # 팔
    m.box(3.8, 13.8, 7, 12.2, 14.4, 9.6, "c060ff")                        # 떠 있는 보랏빛 고리
    m.box(4.5, 14.4, 6.2, 11.5, 14.6, 10.4, "a040e0")


def pet_phoenix(m):
    RED, ORA, YEL, GOLD = "ff3a1a", "ff7a1f", "ffd23f", "fff3b0"
    m.box(5.5, 3, 5, 10.5, 8, 11, RED)                                    # 몸
    m.box(6, 7.5, 8.5, 10, 11.5, 12.5, RED)                               # 머리
    m.box(7.2, 9, 12.5, 8.8, 10, 14.2, GOLD)                              # 부리
    _eyes(m, 10, 12.5, col="1a0a0a", gap=1.0, size=0.7)
    for k in range(3):                                                    # 불꽃 볏
        m.box(7.4, 11.5 + k * 0.4, 9 + k * 1.2, 8.6, 13.5 + k * 0.9, 10 + k * 1.2, YEL if k % 2 else ORA)
    for sg in (-1, 1):                                                    # 펼친 불꽃 날개
        x0, x1 = (-2, 5.5) if sg < 0 else (10.5, 18)
        rz = -22.5 if sg < 0 else 22.5
        m.box(x0, 7, 5.5, x1, 8, 10, RED, rot=("z", rz, (8, 7.5, 8)))
        m.box(x0, 7.2, 4.5, x1, 7.8, 6, ORA, rot=("z", rz, (8, 7.5, 8)))
        m.box(x0, 7.3, 3.5, x1 - 2 * sg, 7.7, 4.5, YEL, rot=("z", rz, (8, 7.5, 8)))
    for k, (dx, c) in enumerate(((-1.5, ORA), (0, YEL), (1.5, ORA))):     # 긴 꼬리깃
        m.box(7.4 + dx, 2 - k * 0.3, -4, 8.6 + dx, 3.2 - k * 0.3, 5, c, rot=("x", -22.5, (8, 3, 5)))
    m.sym(6.4, 0.5, 7.5, 7.2, 3, 8.3, GOLD)


def pet_dragon(m):
    B, BELLY, HORN, WING = "3a1a5a", "c9a13b", "fff3b0", "7a3aa0"
    m.box(5, 3, 4, 11, 8.5, 11, B)                                        # 몸
    m.box(5.8, 3, 9, 10.2, 7, 11.4, BELLY)
    m.box(5.2, 7.5, 9, 10.8, 12, 14, B)                                   # 큰 머리
    m.box(6.2, 7.5, 14, 9.8, 9.8, 16.2, "4a2260")                         # 주둥이
    m.box(6.6, 8.8, 16, 7.4, 9.4, 16.3, "1a0a24")
    m.box(8.6, 8.8, 16, 9.4, 9.4, 16.3, "1a0a24")
    _eyes(m, 10, 14, col="ff3030", gap=1.3, size=0.9)
    m.sym(5.8, 12, 10, 6.8, 14.4, 11, HORN, rot=("x", -22.5, (6.3, 12, 10.5)))   # 뿔
    for sg in (-1, 1):                                                    # 작은 날개
        x0, x1 = (0.5, 5) if sg < 0 else (11, 15.5)
        rz = -45 if sg < 0 else 45
        m.box(x0, 8, 5, x1, 8.6, 9, WING, rot=("z", rz, (8, 8.3, 7)))
    m.box(7, 4, -2.5, 9, 6, 4, B, rot=("x", 22.5, (8, 5, 4)))              # 꼬리
    m.box(7.3, 4.5, -4.5, 8.7, 6.8, -2.5, HORN, rot=("x", 22.5, (8, 5, 4)))
    for k in range(3):
        m.box(7.6, 8.5, 5 + k * 2, 8.4, 9.6, 6 + k * 2, HORN)               # 등 가시
    for zz in (4.5, 8.5):
        m.sym(5.2, 0, zz, 6.8, 3, zz + 1.6, B)


def pet_star(m):
    import math as _m
    CORE, GLOW, TIP = "fff3b0", "ffd23f", "7fe8ff"
    m.box(5.5, 4.5, 5.5, 10.5, 9.5, 10.5, CORE)                           # 빛나는 핵
    m.box(6.5, 3.5, 6.5, 9.5, 10.5, 9.5, GLOW)
    m.box(4.5, 5.5, 6.5, 11.5, 8.5, 9.5, GLOW)
    _eyes(m, 7, 10.5, col="3a2a6a", gap=0.9, size=0.8, shine=False)
    for k in range(5):                                                    # 별 모양 다섯 갈래
        a = k * 2 * _m.pi / 5 + _m.pi / 2
        x, y = 8 + _m.cos(a) * 5.5, 7 + _m.sin(a) * 5.5
        m.box(x - 1, y - 1, 7, x + 1, y + 1, 9, TIP, rot=("z", 45, (x, y, 8)))
    for k in range(6):                                                    # 둘레를 도는 작은 별
        a = k * _m.pi / 3
        x, z = 8 + _m.cos(a) * 7, 8 + _m.sin(a) * 7
        m.box(x - 0.5, 12.5 + (k % 2), z - 0.5, x + 0.5, 13.5 + (k % 2), z + 0.5, GLOW if k % 2 else "ffffff")


# ---------------------------------------------------------------- 필드 보스 9종 (v5.4.9)
def _beast(m, fur, fur2, hoof, L, H, W, leg, shift=4.0):
    """육중한 네발 몸통 (앞 어깨 혹 · 배 · 다리 4개 + 발굽). 머리가 앞으로 나오므로 몸을 shift 만큼 뒤로 뺀다"""
    z0 = 8 - L / 2 - shift
    m.box(8 - W / 2, leg, z0, 8 + W / 2, leg + H, z0 + L, fur)
    m.box(8 - W / 2 - 0.8, leg + 2.5, z0 + L - 8, 8 + W / 2 + 0.8, leg + H + 1.4, z0 + L, fur2)     # 어깨 혹
    m.box(8 - W / 2 + 1, leg - 0.6, z0 + 2, 8 + W / 2 - 1, leg + 1, z0 + L - 2, fur2)              # 배
    for zz in (z0 + 1.5, z0 + L - 5.0):
        m.sym(8 - W / 2 + 0.3, 1.2, zz, 8 - W / 2 + 3.5, leg + 1.5, zz + 3.4, fur2)
        m.sym(8 - W / 2, 0, zz - 0.3, 8 - W / 2 + 3.8, 1.4, zz + 3.7, hoof)
    return z0


def field_boar_king(m):
    """대지의 멧돼지왕 고르반 — 거대한 몸, 등 갈기 가시, L자 엄니, 금 코뚜레와 작은 왕관, 이끼 낀 철 견갑"""
    FUR, FUR2, MANE, TUSK, EYE, GOLD, MOSS, IRON = "6b4a32", "4e3524", "2e1d12", "f0e6c8", "ff5020", "ffd23f", "5a8a3a", "7a7f88"
    leg, H, W, L = 5.5, 10.0, 11.0, 20.0
    z0 = _beast(m, FUR, FUR2, "2a2420", L, H, W, leg)
    zf = z0 + L
    m.box(4.0, leg + 2, zf, 12.0, leg + 9, zf + 6, FUR)                                    # 머리
    m.box(5.4, leg + 2, zf + 6, 10.6, leg + 5.4, zf + 8.2, "8a6048")                       # 주둥이
    m.sym(6.0, leg + 3.6, zf + 8.15, 7.2, leg + 4.6, zf + 8.4, "2a1a10")                   # 콧구멍
    m.box(6.8, leg + 1.3, zf + 7.3, 9.2, leg + 2.1, zf + 7.9, GOLD)                        # 금 코뚜레
    m.sym(4.6, leg + 6.4, zf + 5.95, 5.9, leg + 7.4, zf + 6.25, EYE)                       # 눈
    m.sym(4.0, leg + 7.3, zf + 5.9, 6.4, leg + 7.9, zf + 6.3, MANE)                        # 찌푸린 눈썹
    m.sym(3.8, leg + 2.4, zf + 5.5, 4.9, leg + 3.6, zf + 9.6, TUSK)                        # 엄니 (앞으로 → 위로)
    m.sym(3.8, leg + 3.6, zf + 8.6, 4.9, leg + 7.4, zf + 9.6, TUSK)
    m.sym(3.9, leg + 7.4, zf + 8.8, 4.8, leg + 8.4, zf + 9.4, "ffffff")
    m.sym(3.4, leg + 8.2, zf + 0.8, 5.0, leg + 11.0, zf + 2.4, FUR2, rot=("z", 22.5, (4.2, leg + 8.2, zf + 1.6)))   # 귀
    m.box(5.4, leg + 9, zf + 1.0, 10.6, leg + 9.8, zf + 4.6, GOLD)                         # 작은 왕관
    for x in (5.6, 7.5, 9.4):
        m.box(x, leg + 9.8, zf + 2.2, x + 1.0, leg + 11.6, zf + 3.2, GOLD)
    m.box(7.5, leg + 10.2, zf + 4.5, 8.5, leg + 11.0, zf + 4.8, "ff3a3a")
    for i in range(8):                                                                      # 등 갈기 가시
        z = z0 + 1.5 + i * 2.4
        hh = 2.0 + (3.0 if 2 <= i <= 6 else 1.2)
        m.box(7.1, leg + H + (1.2 if i >= 4 else 0), z, 8.9, leg + H + hh + (1.2 if i >= 4 else 0), z + 1.8, MANE, rot=("x", -22.5, (8, leg + H, z + 0.9)))
    m.box(8 - W / 2 - 1.2, leg + H - 0.5, zf - 7.5, 8 + W / 2 + 1.2, leg + H + 2.2, zf - 3, IRON)   # 철 견갑
    m.box(8 - W / 2 - 1.3, leg + H - 0.9, zf - 7.6, 8 + W / 2 + 1.3, leg + H - 0.3, zf - 2.9, GOLD)
    m.sym(8 - W / 2 - 1.4, leg + H + 1.0, zf - 6.2, 8 - W / 2 - 0.4, leg + H + 3.8, zf - 5.0, "c8ccd2")
    for (x0, y0, z) in ((8 - W / 2 - 0.15, leg + 3, z0 + 3), (8 + W / 2 - 0.2, leg + 5, z0 + 7), (8 - W / 2 - 0.15, leg + 6, z0 + 11)):
        m.box(x0, y0, z, x0 + 0.35, y0 + 2.4, z + 3.2, MOSS)                               # 이끼
    m.box(5, leg + H - 0.1, z0 + 2, 9, leg + H + 0.3, z0 + 6, MOSS)
    m.box(7.4, leg + H - 3, z0 - 2.4, 8.6, leg + H - 1.2, z0, FUR2)                        # 꼬리


def field_frost_bear(m):
    """설원의 대곰 우르사 — 산만 한 흰 곰, 등에 솟은 얼음 결정 무리, 서리 발톱, 얼음 이빨, 푸른 눈"""
    FUR, FUR2, PAW, ICE, ICE2, EYE = "e8f2fa", "c8d8e8", "5a7088", "7fe8ff", "d8fbff", "3fb8ff"
    leg, H, W, L = 7.0, 11.0, 12.0, 19.0
    z0 = _beast(m, FUR, FUR2, PAW, L, H, W, leg)
    zf = z0 + L
    m.box(3.6, leg + 3, zf - 1, 12.4, leg + 10.5, zf + 5.5, FUR)                           # 머리
    m.box(5.2, leg + 3, zf + 5.5, 10.8, leg + 6.6, zf + 8.6, FUR2)                         # 주둥이
    m.box(6.8, leg + 5.4, zf + 8.5, 9.2, leg + 6.8, zf + 9.0, "1a2a3a")                    # 코
    m.box(5.4, leg + 2.6, zf + 5.6, 10.6, leg + 3.2, zf + 8.4, "3a4a5a")                   # 벌린 입
    for x in (5.6, 7.2, 8.8, 9.8):
        m.box(x, leg + 3.2, zf + 7.8, x + 0.6, leg + 4.2, zf + 8.4, ICE2)                  # 얼음 이빨
    m.sym(4.4, leg + 7.4, zf + 5.45, 5.8, leg + 8.6, zf + 5.75, EYE)
    m.sym(3.4, leg + 10.2, zf + 0.6, 5.4, leg + 12.2, zf + 2.4, FUR2)                      # 둥근 귀
    for zz in (z0 + 1.5, z0 + L - 5.0):                                                     # 서리 발톱
        for dx in (0.4, 1.6, 2.8):
            m.sym(8 - W / 2 + dx, 0, zz + 3.5, 8 - W / 2 + dx + 0.6, 0.9, zz + 4.6, ICE2)
    for (x, z, hh, a) in ((8, z0 + 4, 7, 0), (6, z0 + 7, 5, 22.5), (10, z0 + 7, 5, -22.5), (8, z0 + 10, 9, 0),
                          (5.4, z0 + 12, 6, 22.5), (10.6, z0 + 12, 6, -22.5), (8, z0 + 15, 6, 0), (6.5, z0 + 2, 3.5, 22.5), (9.5, z0 + 2, 3.5, -22.5)):
        m.box(x - 1.0, leg + H, z - 1.0, x + 1.0, leg + H + hh, z + 1.0, ICE, rot=("z", a, (x, leg + H, z)) if a else None)      # 얼음 결정
        m.box(x - 0.45, leg + H + hh - 0.2, z - 0.45, x + 0.45, leg + H + hh + 1.6, z + 0.45, ICE2, rot=("z", a, (x, leg + H, z)) if a else None)
    m.box(8 - W / 2 - 0.2, leg + H - 2, zf - 8, 8 + W / 2 + 0.2, leg + H - 1.4, zf - 1, ICE)   # 서리 띠


def field_bandit_lord(m):
    """도적왕 바르칸 — 가죽 갑옷과 두건, 붉은 복면, 모피 깃, 금화 허리띠, 양손의 굽은 단검, 등에 멘 전리품 자루"""
    SKIN, LEATH, LEATH2, TRIM, RED, FUR, STEEL, GOLD = "c89868", "4a3a2a", "2a2420", "8a6a3a", "a8281e", "8a7a6a", "c8ccd2", "ffd23f"
    hero(m, SKIN, LEATH, LEATH2, TRIM, GOLD, boots="1a1612", w=1.1)
    m.box(4.6, 19.8, 4.8, 11.4, 26.4, 11.4, "3a2e24")                                      # 두건
    m.box(5.0, 20.6, 11.3, 11.0, 25.6, 11.6, "1a1612")                                     # 두건 속 그늘
    m.box(5.2, 20.4, 11.4, 10.8, 22.8, 11.9, RED)                                          # 붉은 복면
    m.box(9.6, 19.0, 11.4, 11.0, 21.0, 11.9, RED, rot=("z", -22.5, (10.3, 21, 11.6)))       # 복면 매듭 자락
    m.sym(5.8, 23.4, 11.5, 7.2, 24.2, 11.8, "ffd23f")                                      # 번뜩이는 눈
    m.box(7.2, 26.4, 7.0, 8.8, 28.4, 9.0, "3a2e24", rot=("x", -22.5, (8, 26.4, 8)))        # 두건 끝
    m.box(3.0, 18.6, 4.6, 13.0, 20.8, 11.2, FUR)                                           # 모피 깃
    m.box(3.4, 20.4, 5.0, 12.6, 21.2, 10.8, "a8988a")
    for x in (4.6, 6.8, 9.0, 11.2):                                                         # 금화 허리띠
        m.box(x, 10.6, 10.2, x + 0.9, 11.4, 10.6, GOLD)
    m.box(3.2, 7.8, 9.6, 5.4, 10.4, 11.2, "6a4a2a")                                        # 허리 주머니
    m.box(10.6, 7.8, 9.6, 12.8, 10.4, 11.2, "6a4a2a")
    for (x, s) in ((0.6, 1), (15.4, -1)):                                                   # 굽은 단검 두 자루
        m.box(x - 0.5, 9.0, 8.8, x + 0.5, 11.0, 10.2, "3a2a1a")
        m.box(x - 1.0, 10.8, 8.6, x + 1.0, 11.4, 10.4, GOLD)
        m.box(x - 0.4, 11.4, 9.1, x + 0.4, 17.0, 9.9, STEEL, rot=("z", -22.5 * s, (x, 11.4, 9.5)))
        m.box(x - 0.15, 11.4, 9.0, x + 0.15, 17.4, 10.0, "ffffff", rot=("z", -22.5 * s, (x, 11.4, 9.5)))
    m.box(9.0, 12.0, 1.6, 14.0, 20.0, 5.8, "8a6a3a", rot=("z", -22.5, (11.5, 16, 3.7)))    # 전리품 자루
    m.box(10.4, 19.6, 2.6, 12.6, 21.2, 4.8, "6a4a2a", rot=("z", -22.5, (11.5, 16, 3.7)))
    m.box(9.6, 17.0, 1.4, 11.0, 18.4, 2.0, GOLD, rot=("z", -22.5, (11.5, 16, 3.7)))
    m.box(3.4, 13.0, 10.6, 12.6, 14.0, 11.0, "3a2a1a", rot=("z", 22.5, (8, 13.5, 10.8)))    # 가슴 가죽끈


def field_ravager(m):
    """사막의 파괴수 카르둠 — 뿔 달린 거대 파괴수, 전쟁 안장과 깃발, 쇠사슬 갑주, 붉은 눈"""
    SKIN, SKIN2, HORN, IRON, RED, EYE, SAND = "6a5e52", "4a4038", "d8cfb8", "6a6f78", "a8281e", "ff4020", "c9a46a"
    leg, H, W, L = 8.0, 11.0, 11.0, 20.0
    z0 = _beast(m, SKIN, SKIN2, "2a2420", L, H, W, leg)
    zf = z0 + L
    m.box(4.2, leg + 1, zf, 11.8, leg + 9, zf + 7, SKIN)                                   # 머리 (아래로 처진)
    m.box(5.0, leg - 1.4, zf + 3, 11.0, leg + 1, zf + 7.4, SKIN2)                          # 아래턱
    for x in (5.4, 7.0, 8.4, 10.0):
        m.box(x, leg + 0.8, zf + 6.8, x + 0.6, leg + 2.2, zf + 7.3, HORN)                  # 이빨
    m.sym(4.3, leg + 5.6, zf + 6.95, 6.0, leg + 6.8, zf + 7.25, EYE)
    m.box(4.0, leg + 7.0, zf + 6.9, 12.0, leg + 7.8, zf + 7.3, SKIN2)                      # 이마 뼈
    for s, x in ((1, 3.2), (-1, 12.8)):                                                    # 휘어진 큰 뿔 (옆 → 위 → 앞)
        m.box(x - 1.0, leg + 6, zf + 1.5, x + 1.0, leg + 8, zf + 4, HORN)
        m.box(x - 1.0 - s * 1.6, leg + 7, zf + 1.8, x + 1.0 - s * 1.6, leg + 12, zf + 3.8, HORN, rot=("z", 22.5 * s, (x, leg + 7, zf + 2.8)))
        m.box(x - 0.7 - s * 3.2, leg + 11.2, zf + 2.2, x + 0.7 - s * 3.2, leg + 12.6, zf + 6.4, HORN)
        m.box(x - 0.4 - s * 3.2, leg + 11.5, zf + 6.4, x + 0.4 - s * 3.2, leg + 12.3, zf + 7.4, "ffffff")
    m.box(8 - W / 2 - 0.4, leg + H, z0 + 5, 8 + W / 2 + 0.4, leg + H + 1.4, z0 + 13, RED)    # 전쟁 안장
    m.sym(8 - W / 2 - 0.5, leg + 4, z0 + 5.5, 8 - W / 2 - 0.1, leg + H + 1.4, z0 + 12.5, RED)
    m.box(8 - W / 2 - 0.5, leg + H + 1.4, z0 + 5, 8 + W / 2 + 0.5, leg + H + 1.9, z0 + 6, SAND)
    m.box(8 - W / 2 - 0.5, leg + H + 1.4, z0 + 12, 8 + W / 2 + 0.5, leg + H + 1.9, z0 + 13, SAND)
    for x in (4.2, 11.8):                                                                   # 깃발 두 개
        m.box(x - 0.3, leg + H + 1.4, z0 + 3.6, x + 0.3, leg + H + 14, z0 + 4.2, "5a3a1a")
        m.box(x - 0.1, leg + H + 8.4, z0 + 4.2, x + 0.1, leg + H + 13.6, z0 + 8.2, RED)
        m.box(x - 0.12, leg + H + 9.6, z0 + 5.2, x + 0.12, leg + H + 11.6, z0 + 7.0, SAND)
        m.box(x - 0.5, leg + H + 14, z0 + 3.4, x + 0.5, leg + H + 15, z0 + 4.4, "ffd23f")
    for i in range(4):                                                                      # 쇠사슬 갑주
        m.box(8 - W / 2 - 0.3, leg + 1.5 + i * 2.2, zf - 7, 8 + W / 2 + 0.3, leg + 2.3 + i * 2.2, zf - 0.5, IRON)
    for i in range(5):
        z = z0 + 14 + i * 1.3
        m.box(7.2, leg + H + 0.8, z, 8.8, leg + H + 3.0, z + 0.9, HORN, rot=("x", -22.5, (8, leg + H, z)))


def field_ancient_golem(m):
    """고대 골렘 테라곤 — 거대한 돌 몸, 이끼와 덩굴, 몸에 새겨진 빛나는 초록 룬, 바위 주먹, 머리의 수정"""
    ST, ST2, ST3, MOSS, RUNE, VINE = "8a8680", "6a6660", "4a4640", "5a8a3a", "5affa0", "3a6a2a"
    m.sym(3.2, 0, 5.6, 7.4, 1.6, 11.2, ST3)                                                # 발
    m.sym(3.4, 1.6, 6.0, 7.2, 10.0, 10.6, ST2)                                             # 다리
    m.sym(3.2, 5.0, 10.5, 7.4, 6.6, 10.9, RUNE)
    m.box(2.0, 10.0, 4.4, 14.0, 22.0, 11.8, ST)                                            # 몸통
    m.box(3.0, 12.0, 11.7, 13.0, 21.0, 12.3, ST2)                                          # 가슴 돌판
    m.box(7.0, 14.0, 12.2, 9.0, 19.0, 12.6, RUNE)                                          # 가슴 룬 (십자)
    m.box(5.5, 15.8, 12.2, 10.5, 17.2, 12.6, RUNE)
    m.box(1.4, 20.0, 3.8, 14.6, 23.4, 12.4, ST2)                                           # 어깨 바위
    m.sym(-3.6, 17.0, 4.6, 1.8, 24.2, 11.6, ST)                                            # 어깨 덩어리
    m.sym(-3.0, 7.0, 5.4, 1.2, 17.0, 10.8, ST2)                                            # 긴 팔
    m.sym(-4.2, 1.6, 4.4, 2.2, 7.4, 11.8, ST3)                                             # 바위 주먹
    m.sym(-4.3, 3.6, 11.7, 2.3, 4.6, 12.0, RUNE)
    m.box(5.0, 23.4, 5.4, 11.0, 28.4, 11.2, ST)                                            # 머리 (작게)
    m.box(4.8, 26.6, 5.2, 11.2, 27.6, 11.4, ST3)                                           # 이마
    m.sym(5.8, 24.8, 11.15, 7.2, 25.8, 11.45, RUNE)                                        # 눈
    m.box(7.0, 28.4, 7.0, 9.0, 31.4, 9.0, RUNE, rot=("y", 45, (8, 28.4, 8)))               # 머리 수정
    m.box(7.5, 31.4, 7.5, 8.5, 32.4, 8.5, "ffffff", rot=("y", 45, (8, 31.4, 8)))
    for (x0, y0, z0, x1, y1, z1) in ((1.8, 21, 4.0, 6, 23.6, 12.5), (-3.8, 23, 4.4, 1.0, 24.4, 11.8), (10, 10, 11.6, 13, 12, 12.1), (2, 10, 4.2, 14, 11, 4.6)):
        m.box(x0, y0, z0, x1, y1, z1, MOSS)                                                # 이끼
    for x in (3.0, 12.4, -2.2):                                                             # 늘어진 덩굴
        m.box(x, 12.0, 11.9, x + 0.5, 22.0, 12.3, VINE)
    for (x, z) in ((2.6, 7.0), (13.4, 7.0)):
        m.box(x - 0.6, 13, z, x + 0.6, 21, z + 0.4, RUNE)                                  # 옆구리 룬 줄


def field_swamp_witch(m):
    """늪의 대마녀 모르가나 — 구부정한 이끼 로브, 버섯 모자, 뼈 목걸이, 초롱 지팡이, 떠도는 도깨비불"""
    SKIN, ROBE, ROBE2, MOSS, CAP, SPOT, BONE, GLOW, WOOD = "7a9a5a", "3a4a2a", "2a3620", "5a8a3a", "7a2a3a", "f0e0c0", "e8e0d0", "c8ff5a", "4a3a22"
    hero(m, SKIN, ROBE, ROBE2, MOSS, GLOW, w=1.05, robe=ROBE)
    eyes(m, 22.0, GLOW, z=11.05)
    m.box(7.3, 20.2, 11.0, 8.7, 21.4, 13.0, "5a7a3a")                                      # 매부리코
    m.box(4.6, 13.0, 4.8, 11.4, 25.0, 5.6, "4a5a3a")                                       # 헝클어진 머리
    m.box(1.8, 25.0, 1.8, 14.2, 26.6, 14.2, CAP)                                           # 버섯 모자
    m.box(3.4, 26.6, 3.4, 12.6, 29.0, 12.6, CAP)
    m.box(5.4, 29.0, 5.4, 10.6, 30.4, 10.6, CAP)
    for (x, y, z) in ((3.0, 26.2, 10.0), (11.0, 27.4, 11.8), (6.0, 29.4, 5.2), (12.4, 26.2, 4.0), (8.2, 30.2, 8.0)):
        m.box(x, y, z, x + 1.4, y + 0.6, z + 1.4, SPOT)                                    # 버섯 점
    m.box(4.6, 24.4, 4.6, 11.4, 25.0, 11.4, "e8d8b8")                                      # 주름
    for x in (5.2, 6.8, 8.4, 10.0):                                                         # 뼈 목걸이
        m.box(x, 18.4, 10.6, x + 0.8, 19.6, 11.1, BONE)
    m.box(7.4, 17.0, 10.7, 8.6, 18.4, 11.2, BONE)
    for (x, y, z, h) in ((3.6, 2, 11.0, 5), (10.8, 4, 11.0, 4), (4.2, 0, 3.0, 6), (11.2, 0, 3.4, 5)):
        m.box(x, y, z, x + 1.6, y + h, z + 0.6, MOSS)                                      # 이끼 자락
    _w = m.mark()
    m.box(15.6, 0, 7.6, 16.8, 28, 8.8, WOOD)                                                # 초롱 지팡이 (갈고리 끝에 초롱)
    m.box(16.8, 26.8, 7.6, 20.0, 28.0, 8.8, WOOD)
    m.box(18.8, 25.0, 7.9, 19.4, 26.8, 8.5, "2a2a22")
    m.box(17.6, 21.2, 6.6, 20.6, 25.0, 9.8, "2a2a22")
    m.box(17.9, 21.6, 6.5, 20.3, 24.6, 9.9, GLOW)
    m.box(17.4, 25.0, 6.4, 20.8, 25.4, 10.0, "5a5a4a")
    m.hold(_w, (16.2, 11.5, 8.2), (13.41, 11.5, 8.0))   # v5.5.0: 무기를 손에 쥠
    for (x, y, z) in ((-5, 14, 4), (-3, 24, 12), (20, 12, 2), (-6, 6, 12)):                 # 도깨비불
        m.box(x - 0.8, y - 0.8, z - 0.8, x + 0.8, y + 0.8, z + 0.8, GLOW, rot=("y", 45, (x, y, z)))
        m.box(x - 0.35, y + 0.8, z - 0.35, x + 0.35, y + 1.8, z + 0.35, "ffffff", rot=("y", 45, (x, y, z)))


def field_flame_knight(m):
    """화염 기사 이그니스 — 검게 탄 판금에 용암 이음새, 불꽃 투구 장식, 불타는 대검, 화염 방패"""
    ARM, ARM2, LAVA, FIRE, CORE, GOLD = "2a2426", "3e3638", "ff6a1a", "ffb030", "fff0a0", "c08a2a"
    hero(m, ARM2, ARM, ARM2, LAVA, CORE, boots=ARM, w=1.15, h=1.05)
    m.box(4.8, 20.4, 5.2, 11.2, 27.2, 11.6, ARM)                                           # 투구
    m.box(5.4, 23.4, 11.5, 10.6, 24.2, 11.8, LAVA)                                         # 눈 틈
    m.box(7.6, 20.8, 11.5, 8.4, 23.4, 11.8, LAVA)
    m.box(4.6, 26.6, 5.0, 11.4, 27.4, 11.8, GOLD)
    m.sym(3.8, 24.0, 7.0, 4.8, 28.0, 9.0, ARM2, rot=("z", -22.5, (4.3, 24, 8)))            # 투구 뿔
    for i, (h, c) in enumerate(((4.0, LAVA), (5.6, FIRE), (3.4, LAVA), (2.4, FIRE))):       # 불꽃 투구 장식 (뒤로 흩날림)
        z = 9.6 - i * 1.4
        m.box(7.0, 27.4, z - 0.7, 9.0, 27.4 + h, z + 0.7, c, rot=("x", -22.5, (8, 27.4, z)))
    m.box(7.4, 28.0, 8.8, 8.6, 32.0, 9.8, CORE, rot=("x", -22.5, (8, 27.4, 9.3)))
    for (y, x0, x1) in ((6.0, 4.6, 7.4), (6.0, 8.6, 11.4), (13.5, 3.6, 12.4), (17.0, 4.2, 11.8)):
        m.box(x0, y, 10.15, x1, y + 0.5, 10.75, LAVA)                                      # 용암 이음새
    m.sym(1.0, 18.0, 5.0, 3.6, 22.4, 11.0, ARM)                                            # 견갑
    m.sym(0.8, 21.6, 7.4, 1.8, 24.4, 8.6, LAVA, rot=("z", 22.5, (1.3, 21.6, 8)))
    _w = m.mark()
    m.box(16.4, 8.0, 7.2, 17.8, 11.4, 8.8, GOLD)                                           # 대검 손잡이 · 가드
    m.box(14.4, 11.4, 6.8, 19.8, 12.6, 9.2, GOLD)
    m.box(16.0, 12.6, 7.4, 18.2, 30.0, 8.6, LAVA)                                          # 불타는 칼날
    m.box(16.6, 12.6, 7.3, 17.6, 30.6, 8.7, CORE)
    for y in (15, 19, 23, 27):
        m.box(18.2, y, 7.6, 19.4, y + 2.2, 8.4, FIRE, rot=("z", -22.5, (18.2, y, 8)))      # 날의 불꽃
        m.box(14.8, y + 1, 7.6, 16.0, y + 3.0, 8.4, FIRE, rot=("z", 22.5, (16, y + 1, 8)))
    m.hold(_w, (17.1, 9.7, 8.0), (13.92, 12.08, 8.0))   # v5.5.0: 무기를 손에 쥠
    m.box(-2.6, 6.0, 4.2, -0.6, 17.0, 11.8, ARM)                                           # 화염 방패
    m.box(-2.8, 6.4, 4.6, -2.5, 16.6, 11.4, GOLD)
    m.box(-3.0, 9.6, 6.8, -2.6, 13.4, 9.2, LAVA)
    m.box(-3.1, 10.6, 7.6, -2.8, 12.4, 8.4, CORE)


def field_deep_warden(m):
    """심층의 감시자 — 워든을 닮은 거대한 청록 몸, 가슴에 박동하는 영혼 심장, 머리의 촉각 뿔, 긴 팔"""
    SK, SK2, SK3, SOUL, SOUL2, BONE = "1a3a44", "0f262e", "2a5560", "3fe8ff", "b0ffff", "d8e4e0"
    m.sym(3.4, 0, 5.4, 7.4, 1.4, 11.0, SK2)                                                # 발
    m.sym(3.6, 1.4, 6.0, 7.2, 10.4, 10.4, SK)                                              # 다리
    m.box(2.2, 10.4, 4.8, 13.8, 23.0, 11.2, SK)                                            # 몸통
    m.box(3.2, 11.0, 11.1, 12.8, 22.0, 11.5, SK3)
    m.box(5.8, 14.0, 11.4, 10.2, 20.0, 12.2, "0a1a20")                                     # 영혼 심장 (갈비뼈 속)
    m.box(6.6, 15.0, 11.6, 9.4, 19.0, 12.5, SOUL)
    m.box(7.3, 16.0, 11.8, 8.7, 18.0, 12.7, SOUL2)
    for y in (14.2, 16.4, 18.6):                                                            # 갈비뼈
        m.box(4.2, y, 11.9, 11.8, y + 0.6, 12.4, BONE)
    m.sym(-2.4, 8.0, 5.6, 2.2, 22.0, 10.4, SK)                                             # 긴 팔
    m.sym(-2.8, 4.4, 5.2, 2.6, 8.2, 10.8, SK2)                                             # 큰 손
    m.sym(-2.9, 5.2, 10.7, 2.7, 5.8, 11.0, SOUL)
    m.box(3.6, 23.0, 4.6, 12.4, 31.0, 11.6, SK)                                            # 머리 (얼굴 없음)
    m.box(4.6, 24.0, 11.5, 11.4, 29.4, 11.9, SK2)
    m.box(5.6, 25.4, 11.8, 10.4, 26.2, 12.1, SOUL)                                         # 빛나는 틈
    m.sym(2.6, 29.0, 7.0, 4.8, 35.0, 9.0, SK3, rot=("z", 22.5, (3.7, 29, 8)))              # 촉각 뿔
    m.sym(2.9, 35.0, 7.3, 4.5, 37.0, 8.7, SOUL, rot=("z", 22.5, (3.7, 29, 8)))
    for (x, y, z) in ((3.0, 12, 11.0), (12.4, 19, 11.0), (6.0, 6, 10.2), (4.0, 20.5, 4.4), (11, 13, 4.4)):
        m.box(x, y, z, x + 1.2, y + 1.2, z + 0.6, SOUL)                                    # 몸의 스컬크 빛점
    for (x, y, z) in ((-6, 20, 4), (22, 16, 12), (-4, 30, 12)):                             # 떠도는 영혼
        m.box(x - 0.9, y - 0.9, z - 0.9, x + 0.9, y + 0.9, z + 0.9, SOUL2, rot=("y", 45, (x, y, z)))


def field_frost_lich(m):
    """서리 리치 칼라스 — 해골 얼굴, 누더기 서리 로브, 얼음 왕관, 드러난 갈비뼈, 얼음 구슬 지팡이, 떠도는 룬석"""
    BONE, BONE2, ROBE, ROBE2, ICE, ICE2, GOLD = "e8e4d8", "b8b4a8", "1e2a44", "2a3a5a", "7fe8ff", "d8fbff", "a0c0e0"
    hero(m, BONE, ROBE, ROBE2, ICE, ICE2, w=0.95, h=1.1, robe=ROBE)
    m.box(5.0, 21.0, 11.0, 11.0, 22.6, 11.3, BONE2)                                        # 해골 턱
    for x in (5.6, 7.0, 8.4, 9.8):
        m.box(x, 22.0, 11.1, x + 0.6, 22.8, 11.4, "ffffff")                                # 이빨
    m.sym(5.4, 24.2, 11.0, 7.2, 26.0, 11.2, "0a0f1a")                                      # 눈구멍
    m.sym(5.9, 24.6, 11.1, 6.7, 25.4, 11.35, ICE)                                          # 푸른 눈빛
    m.box(7.6, 23.2, 11.05, 8.4, 24.0, 11.3, "0a0f1a")
    m.box(4.2, 18.0, 4.0, 11.8, 28.4, 5.6, ROBE2)                                          # 두건
    m.sym(4.0, 18.0, 5.0, 5.2, 28.4, 11.4, ROBE2)
    m.box(4.0, 27.6, 5.0, 12.0, 28.6, 11.4, ROBE2)
    m.box(4.6, 28.6, 5.6, 11.4, 29.4, 10.8, GOLD)                                          # 얼음 왕관
    for (x, h) in ((4.8, 2.4), (6.4, 3.6), (7.6, 5.0), (8.8, 3.6), (10.4, 2.4)):
        m.box(x, 29.4, 10.0, x + 0.9, 29.4 + h, 10.9, ICE)
        m.box(x + 0.2, 29.4 + h, 10.2, x + 0.7, 29.4 + h + 0.8, 10.7, ICE2)
    for y in (13.6, 15.2, 16.8, 18.4):                                                      # 드러난 갈비뼈
        m.box(5.4, y, 10.2, 10.6, y + 0.6, 10.9, BONE)
    m.box(7.7, 13.0, 10.3, 8.3, 19.6, 10.95, BONE2)
    _w = m.mark()
    m.box(-1.2, 0, 7.4, 0.2, 30, 8.8, "3a4a6a")                                            # 지팡이
    m.box(-2.4, 30, 6.2, 1.4, 33.8, 10.0, ICE, rot=("y", 45, (-0.5, 30, 8.1)))              # 얼음 구슬
    m.box(-1.6, 30.8, 7.0, 0.6, 33.0, 9.2, ICE2, rot=("y", 45, (-0.5, 30, 8.1)))
    for (a, h) in ((-22.5, 3.0), (22.5, 3.0)):
        m.box(-1.0, 28.0, 7.7, 0.0, 28.0 + h, 8.5, ICE, rot=("z", a, (-0.5, 28, 8.1)))
    m.hold(_w, (-0.5, 12.1, 8.1), (3.11, 12.65, 8.0))   # v5.5.0: 무기를 손에 쥠
    for (x, y, z) in ((20, 22, 4), (-6, 14, 12), (19, 10, 12), (-4, 24, 2)):                 # 떠도는 룬석
        m.box(x - 1.0, y - 1.4, z - 0.5, x + 1.0, y + 1.4, z + 0.5, "3a4a6a", rot=("y", 45, (x, y, z)))
        m.box(x - 0.4, y - 0.6, z - 0.6, x + 0.4, y + 0.6, z + 0.6, ICE, rot=("y", 45, (x, y, z)))
    for x in (3.8, 6.0, 9.2, 11.4):                                                         # 서리 자락
        m.box(x, 0, 11.4, x + 1.0, 1.6, 11.8, ICE)


BUILDERS = {"witch": witch, "elf_queen": elf_queen, "dwarf_king": dwarf_king, "harpy_queen": harpy_queen,
            "sea_gatekeeper": sea_gatekeeper, "bungbung": bungbung, "desert_nightmare": desert_nightmare,
            "siphonia": siphonia, "kain": kain, "frost_queen": frost_queen, "volcano_giant": volcano_giant,
            "void_apostle": void_apostle, "thunder_god": thunder_god, "primordial_dragon": primordial_dragon, "vengeful_spirit": vengeful_spirit, "balrog": balrog,
            "megalodon": megalodon, "kraken": kraken, "mount_wolf": mount_wolf, "mount_lizard": mount_lizard, "mount_warhorse": mount_warhorse,
            "mount_icebear": mount_icebear, "mount_lion": mount_lion, "mount_panther": mount_panther, "mount_griffin": mount_griffin, "mount_dragon": mount_dragon,
            "pet_slime": pet_slime, "pet_chick": pet_chick, "pet_bunny": pet_bunny, "pet_fox": pet_fox, "pet_penguin": pet_penguin, "pet_owl": pet_owl,
            "pet_golem": pet_golem, "pet_fairy": pet_fairy, "pet_ghost": pet_ghost, "pet_phoenix": pet_phoenix, "pet_dragon": pet_dragon, "pet_star": pet_star,
            "field_boar_king": field_boar_king, "field_frost_bear": field_frost_bear, "field_bandit_lord": field_bandit_lord, "field_ravager": field_ravager,
            "field_ancient_golem": field_ancient_golem, "field_swamp_witch": field_swamp_witch, "field_flame_knight": field_flame_knight,
            "field_deep_warden": field_deep_warden, "field_frost_lich": field_frost_lich}


# ---------------------------------------------------------------- 위엄 (v5.1.1): 더 웅장한 보스
DIVINE = {"elf_queen", "siphonia", "frost_queen", "thunder_god", "harpy_queen", "vengeful_spirit"}   # 빛나는 후광
DARK_CROWN = {"kain", "void_apostle", "balrog"}   # 가시 왕관 (v5.8.0: 자기 왕관 · 모자가 있는 보스는 뺌)
WINGED = {"elf_queen": "light", "siphonia": "light", "frost_queen": "light", "thunder_god": "light", "vengeful_spirit": "light",
          "void_apostle": "dark", "kain": "dark"}
CAPED = {"witch": "2a1640", "dwarf_king": "8a1a1a", "sea_gatekeeper": "0f4a5a", "desert_nightmare": "7a4a10",
         "field_bandit_lord": "3a2a1e", "field_flame_knight": "7a1a10"}
HUGE = {"bungbung", "volcano_giant", "primordial_dragon", "kraken"}
AQUATIC = {"megalodon", "kraken"}   # 물속 보스: 받침대 · 화로 없음
BEASTS = {"megalodon", "kraken", "field_boar_king", "field_frost_bear", "field_ravager"}   # 등 뒤 마법진 없음


def _core(m):
    """몸통 중심부 요소로 머리 꼭대기 · 등 위치 추정"""
    core = [e for e in m.els if 3 <= (e["from"][0] + e["to"][0]) / 2 <= 13 and 2 <= (e["from"][2] + e["to"][2]) / 2 <= 14 and e["to"][1] - e["from"][1] < 12]
    top = max(e["to"][1] for e in core) if core else 24
    mid = [e for e in core if 0.45 * top <= (e["from"][1] + e["to"][1]) / 2 <= 0.8 * top]
    back = min(e["from"][2] for e in mid) if mid else 5
    return top, back


def majesty(m, bid, col):
    import math as _m
    top, back = _core(m)
    white, gold = "ffffff", "ffd23f"
    if bid in HUGE:   # 거대한 보스는 몸 자체가 장식
        return
    sh = top * 0.74   # 어깨 높이
    # 3) 후광 / 가시 왕관 (머리 위에 떠 있음)
    hy = top + (0.9 if bid in DARK_CROWN else 2.5)   # 가시 왕관은 머리에 얹듯 낮게
    if bid in DIVINE:   # 팔각 후광 고리 (바깥 테마색 + 안쪽 흰빛) + 빛살
        for (r, t, c, lift) in ((5.4, 0.8, col, 0), (4.5, 0.5, white, 0.1)):
            L = r * 0.83
            for ang in (0, 45):
                for (dx, dz, lx, lz) in ((0, -r, L, t), (0, r, L, t), (-r, 0, t, L), (r, 0, t, L)):
                    x, z = 8 + dx, 8 + dz
                    m.box(x - lx / 2 if lx != t else x - t / 2, hy + lift, z - lz / 2 if lz != t else z - t / 2,
                          x + lx / 2 if lx != t else x + t / 2, hy + lift + 0.7, z + lz / 2 if lz != t else z + t / 2, c, rot=("y", ang, (8, hy, 8)))
        for k in range(8):
            a = k * _m.pi / 4 + _m.pi / 8
            x, z = 8 + _m.cos(a) * 6.6, 8 + _m.sin(a) * 6.6
            m.box(x - 0.3, hy - 0.4, z - 0.3, x + 0.3, hy + 2.2, z + 0.3, gold)
    elif bid in DARK_CROWN:
        for k in range(10):
            a = k * _m.pi / 5
            x, z = 8 + _m.cos(a) * 4.6, 8 + _m.sin(a) * 4.6
            h = 3.6 if k % 2 == 0 else 2.2
            m.box(x - 0.55, hy, z - 0.55, x + 0.55, hy + h, z + 0.55, col if k % 2 else "1a1a22", rot=("y", 45, (x, hy, z)))
            m.box(x - 0.3, hy + h, z - 0.3, x + 0.3, hy + h + 0.9, z + 0.3, white if k % 2 == 0 else col)
        m.box(8 - 4.8, hy - 0.4, 8 - 4.8, 8 + 4.8, hy + 0.4, 8 + 4.8, "1a1a22", rot=("y", 45, (8, hy, 8)))
    # 4) 기운 날개 (등 뒤로 펼친 깃 6장씩)
    if bid in WINGED:
        dark = WINGED[bid] == "dark"
        c1, c2 = (col, white) if not dark else ("1a0f1e", col)
        z0 = back - 0.8
        for (ang, L, dy, t) in ((45, 16, 1.2, 2.8), (22.5, 19, -0.4, 3.2), (0, 15, -2.6, 2.6)):
            m.sym(9, sh + dy - t / 2, z0 - 0.7, 9 + L, sh + dy + t / 2, z0, c1, rot=("z", ang, (9, sh + dy, z0)))                    # 깃 판
            m.sym(9.5, sh + dy + t / 2 - 0.6, z0 - 0.9, 9 + L - 0.5, sh + dy + t / 2, z0 - 0.5, c2, rot=("z", ang, (9, sh + dy, z0)))  # 윗날 빛
            for q in range(3):                                                                                                    # 깃 끝 톱니
                xq = 9 + L - 1.2 - q * 3.4
                m.sym(xq, sh + dy - t / 2 - 1.2, z0 - 0.6, xq + 1.2, sh + dy - t / 2, z0 - 0.1, c1, rot=("z", ang, (9, sh + dy, z0)))
            m.sym(8 + L + 0.6, sh + dy - 0.6, z0 - 0.8, 9 + L + 1.6, sh + dy + 0.6, z0 - 0.1, c2, rot=("z", ang, (9, sh + dy, z0)))
    # 5) 망토 (무거운 천 + 금색 테 + 문장)
    if bid in CAPED:
        cc = CAPED[bid]
        z0 = back - 0.6
        m.box(3.4, 1.2, z0 - 0.8, 12.6, sh + 0.8, z0, cc)
        m.box(2.6, 0.6, z0 - 1.6, 13.4, 3.2, z0 - 0.6, cc, rot=("x", -22.5, (8, 3.2, z0 - 0.6)))   # 바닥에 끌리는 자락
        m.box(3.2, sh + 0.4, z0 - 1.0, 12.8, sh + 1.6, z0 + 0.4, gold)                         # 어깨 걸쇠 줄
        m.box(3.2, 1.2, z0 - 0.9, 3.8, sh + 0.8, z0 - 0.7, gold)
        m.box(12.2, 1.2, z0 - 0.9, 12.8, sh + 0.8, z0 - 0.7, gold)
        m.box(6.6, sh * 0.5, z0 - 1.0, 9.4, sh * 0.5 + 2.8, z0 - 0.75, col)                    # 문장
        m.box(7.4, sh * 0.5 + 0.8, z0 - 1.1, 8.6, sh * 0.5 + 2.0, z0 - 0.9, white)


# ---------------------------------------------------------------- v5.4.9 더 웅장하게: 계단 받침대 · 화로 · 등 뒤 마법진
def _bar(m, cx, cy, z0, z1, L, t, theta, col):
    """세로 평면(XY)에서 theta(도) 방향의 막대. 회전은 22.5° 단위만 되므로 45° 넘으면 세로 막대를 반대로 기울임"""
    th = ((theta + 90) % 180) - 90
    if abs(th) <= 45:
        m.box(cx - L / 2, cy - t / 2, z0, cx + L / 2, cy + t / 2, z1, col, rot=("z", th, (cx, cy, (z0 + z1) / 2)) if th else None)
    else:
        ang = th - 90 if th > 0 else th + 90
        m.box(cx - t / 2, cy - L / 2, z0, cx + t / 2, cy + L / 2, z1, col, rot=("z", ang, (cx, cy, (z0 + z1) / 2)) if ang else None)


def _hbar(m, cx, cz, y0, y1, L, t, theta, col):
    """바닥 평면(XZ)에서 theta(도) 방향의 막대 (_bar 의 수평판)"""
    th = ((theta + 90) % 180) - 90
    if abs(th) <= 45:
        m.box(cx - L / 2, y0, cz - t / 2, cx + L / 2, y1, cz + t / 2, col, rot=("y", -th, (cx, y0, cz)) if th else None)
    else:
        ang = th - 90 if th > 0 else th + 90
        m.box(cx - t / 2, y0, cz - L / 2, cx + t / 2, y1, cz + L / 2, col, rot=("y", -ang, (cx, y0, cz)) if ang else None)


def sigil(m, cx, cy, z, R, col, white="ffffff"):
    """등 뒤에 세워진 마법진: 16각 두 겹 고리 + 8 갈래 살 + 가운데 별"""
    import math as _m
    for (r, t, c) in ((R, 0.55, col), (R * 0.72, 0.4, white)):
        seg = 2 * r * _m.tan(_m.pi / 16) + 0.25
        for k in range(16):
            a = k * 22.5
            x, y = cx + _m.cos(_m.radians(a)) * r, cy + _m.sin(_m.radians(a)) * r
            _bar(m, x, y, z - 0.3, z, seg, t, a + 90, c)
    for k in range(8):
        a = k * 22.5
        _bar(m, cx, cy, z - 0.25, z - 0.05, R * 1.25, 0.3, a, col if k % 2 else white)
    for k in range(8):
        a = k * 45 + 22.5
        x, y = cx + _m.cos(_m.radians(a)) * R * 0.86, cy + _m.sin(_m.radians(a)) * R * 0.86
        m.box(x - 0.55, y - 0.55, z - 0.45, x + 0.55, y + 0.55, z + 0.1, white, rot=("z", 45, (x, y, z)))
    m.box(cx - 1.2, cy - 1.2, z - 0.5, cx + 1.2, cy + 1.2, z + 0.1, col, rot=("z", 45, (cx, cy, z)))


def epic(m, bid, col, core, lift=1.2):
    """core = 장식을 붙이기 전의 _core(m) (왕관·날개가 머리 높이 추정을 흐리지 않게)"""
    import math as _m
    white, stone, stone2, gold = "ffffff", "2a2733", "3c3848", "ffd23f"
    top, back = core
    if bid in AQUATIC:
        return
    humanoid_like = bid not in HUGE and bid not in BEASTS
    # 몸 전체를 받침대 위로 올림
    for e in m.els:
        e["from"][1] += lift
        e["to"][1] += lift
        if "rotation" in e:
            e["rotation"]["origin"][1] += lift
    # 계단식 팔각 받침대 (돌 + 금테 + 빛나는 룬 줄)
    for i, (r, y0, y1) in enumerate(((12.0, 0, 0.45), (10.4, 0.45, 0.85), (8.8, 0.85, lift))):
        for ang in (0, 45):
            m.box(8 - r * 0.83, y0, 8 - r * 0.83, 8 + r * 0.83, y1, 8 + r * 0.83, stone if i % 2 == 0 else stone2, rot=("y", ang, (8, y0, 8)) if ang else None)
        for ang in (0, 45):
            m.box(8 - r * 0.84, y1 - 0.12, 8 - r * 0.84, 8 + r * 0.84, y1 + 0.02, 8 + r * 0.84, gold if i == 2 else col, rot=("y", ang, (8, y1, 8)) if ang else None)
    for k in range(8):                                                                   # 받침대 옆면 룬 (빛)
        a = k * _m.pi / 4 + _m.pi / 8
        x, z = 8 + _m.cos(a) * 9.6, 8 + _m.sin(a) * 9.6
        m.box(x - 0.5, 0.15, z - 0.5, x + 0.5, 0.7, z + 0.5, col, rot=("y", 45, (x, 0.4, z)))
    # 네 귀퉁이 화로 (기둥 + 받침 + 불꽃)
    for k in range(4):
        a = k * _m.pi / 2 + _m.pi / 4
        x, z = 8 + _m.cos(a) * 12.6, 8 + _m.sin(a) * 12.6
        m.box(x - 0.9, 0, z - 0.9, x + 0.9, 0.6, z + 0.9, stone2)
        m.box(x - 0.55, 0.6, z - 0.55, x + 0.55, 4.8, z + 0.55, stone)
        m.box(x - 1.1, 4.8, z - 1.1, x + 1.1, 5.5, z + 1.1, gold)
        m.box(x - 0.8, 5.5, z - 0.8, x + 0.8, 6.9, z + 0.8, col, rot=("y", 45, (x, 6, z)))
        m.box(x - 0.4, 6.9, z - 0.4, x + 0.4, 8.2, z + 0.4, white, rot=("y", 45, (x, 7.5, z)))
    if not humanoid_like:
        return
    # 등 뒤 마법진 (세워진 원형)
    sy = top * 0.62 + lift
    sigil(m, 8, sy, back - 4.2, min(10.5, top * 0.5), col)


# ---------------------------------------------------------------- v5.8.0 웅장함: 갑옷 세부 · 판 테두리 · 떠도는 유물
def _hex(c, k):
    c = c.lstrip("#")
    return "%02x%02x%02x" % tuple(max(0, min(255, int(int(c[i:i + 2], 16) * k))) for i in (0, 2, 4))


NO_GRAND = {"megalodon", "kraken", "bungbung", "primordial_dragon", "field_boar_king", "field_frost_bear", "field_ravager"}


def grand(m, bid, col):
    """hero() 인체 보스: 3단 가시 견갑 · 넓은 건틀릿 · 갑옷 치마 · 보석 벨트 (갑옷) / 어깨 망토 · 술 장식 (로브)"""
    H = m.hero
    if not H:
        return
    w, h, trim, acc, top = H["w"], H["h"], H["trim"], H["accent"], H["top"]
    dark = _hex(top, 0.6)
    # 견갑 위 3단 판 + 위로 솟은 가시 두 개씩
    m.sym(8 - 7.3 * w, 20.3 * h, 5.0, 8 - 3.3 * w, 21.1 * h, 11.0, top)
    m.sym(8 - 7.4 * w, 20.1 * h, 4.9, 8 - 3.3 * w, 20.4 * h, 11.1, trim)
    m.sym(8 - 6.6 * w, 21.1 * h, 5.6, 8 - 3.6 * w, 21.8 * h, 10.4, dark)
    for zz in (6.4, 9.0):
        m.sym(8 - 6.4 * w, 21.4 * h, zz, 8 - 5.6 * w, 24.2 * h, zz + 0.8, acc, rot=("z", 22.5, (8 - 6.0 * w, 21.4 * h, zz + 0.4)))
    # 건틀릿: 넓게 벌어진 손목 덮개 + 손등 판
    m.sym(8 - 7.0 * w, 12.9 * h, 5.9, 8 - 3.5 * w, 13.7 * h, 10.1, trim)
    m.sym(8 - 6.6 * w, 10.8 * h, 9.0, 8 - 3.9 * w, 12.2 * h, 9.5, dark)
    if not H["robe"]:
        # 가슴판 두 장 · 복부 3단 판 · 무릎 가시 · 등판 척추 능선
        m.sym(8 - 3.7 * w, 16.4 * h, 10.15, 8 - 0.3, 19.0 * h, 10.95, _hex(top, 1.12))
        m.sym(8 - 3.7 * w, 16.2 * h, 10.9, 8 - 0.3, 16.6 * h, 11.1, trim)
        for i in range(3):
            y0 = (12.2 + i * 1.3) * h
            m.box(8 - 2.8 * w + i * 0.2, y0, 10.15, 8 + 2.8 * w - i * 0.2, y0 + 1.0 * h, 10.7, dark if i % 2 == 0 else top)
        m.sym(8 - 2.5 * w, 5.3 * h, 10.4, 8 - 1.6 * w, 6.4 * h, 11.8, acc, rot=("x", -22.5, (8 - 2.0 * w, 5.8 * h, 10.8)))
        for i in range(4):
            y0 = (12.4 + i * 1.8) * h
            m.box(7.3, y0, 5.2, 8.7, y0 + 1.2 * h, 5.9, trim if i % 2 else dark, rot=("x", 22.5, (8, y0, 5.5)))
        # 갑옷 치마 (앞 3장 · 옆 1장씩) + 보석 벨트
        for i, (x0, x1) in enumerate(((5.0, 6.8), (7.1, 8.9), (9.2, 11.0))):
            m.box(x0, 7.0 * h, 10.1, x1, 10.3 * h, 10.9, top if i == 1 else dark, rot=("x", -22.5, ((x0 + x1) / 2, 10.3 * h, 10.5)))
            m.box(x0, 7.0 * h, 10.85, x1, 7.5 * h, 11.05, trim, rot=("x", -22.5, ((x0 + x1) / 2, 10.3 * h, 10.5)))
        m.sym(8 - 4.6 * w, 7.4 * h, 6.4, 8 - 3.9 * w, 10.4 * h, 9.8, dark, rot=("z", -22.5, (8 - 4.2 * w, 10.4 * h, 8)))
        m.box(6.6, 9.9 * h, 10.2, 9.4, 12.0 * h, 10.9, trim)
        m.box(7.4, 10.5 * h, 10.85, 8.6, 11.5 * h, 11.2, acc, rot=("z", 45, (8, 11.0 * h, 11)))
        m.sym(8 - 4.3 * w, 9.6 * h, 9.8, 8 - 3.2 * w, 11.0 * h, 10.9, "6a4a2a")   # 허리 주머니
    else:
        # 로브: 어깨를 덮는 망토 + 앞자락 술 장식
        m.box(8 - 6.8 * w, 17.8 * h, 4.8, 8 + 6.8 * w, 19.6 * h, 11.2, dark)
        m.box(8 - 6.9 * w, 17.6 * h, 4.7, 8 + 6.9 * w, 17.9 * h, 11.3, trim)
        for k in range(7):
            x = 8 - 6.0 * w + k * 2.0 * w
            m.box(x - 0.25, 16.2 * h, 11.0, x + 0.25, 17.7 * h, 11.35, acc if k % 2 else trim)


def relics(m, bid, col):
    """보스 둘레를 떠도는 유물 수정 4개 (테마색 겉 · 흰 속)"""
    import math as _m
    if bid in NO_GRAND or bid in AQUATIC:
        return
    top, _ = _core(m)
    for k in range(3):   # 뒤쪽 반원에 3개, 머리 높이 둘레 (몸에 붙지 않게 멀리)
        a = _m.pi * (1.15 + 0.35 * k)
        x, z, y = 8 + _m.cos(a) * 14, 8 + _m.sin(a) * 14, top * (0.78 + 0.08 * (k % 2))
        m.box(x - 0.6, y - 1.3, z - 0.6, x + 0.6, y + 1.3, z + 0.6, col, rot=("y", 45, (x, y, z)))
        m.box(x - 0.3, y - 0.7, z - 0.3, x + 0.3, y + 0.7, z + 0.3, "ffffff", rot=("y", 45, (x, y, z)))


def edging(m, bid):
    """큰 판의 앞면에 어두운 테두리 (갑옷 판처럼 보이게) — 회전 없는 큰 상자만, 머리 높이 제외"""
    if bid in AQUATIC:
        return
    top, _ = _core(m)
    extra = []
    for e in list(m.els):
        f, t = e["from"], e["to"]
        if "rotation" in e or e.get("_edge"):
            continue
        wx, hy = t[0] - f[0], t[1] - f[1]
        if wx < 2.6 or hy < 2.6 or t[2] < 9.6 or t[1] > top * 0.82 or t[2] - f[2] < 0.8:
            continue
        c = _hex(e.get("_col", "808080"), 0.55)
        z0, z1, th = t[2], t[2] + 0.06, 0.28
        for (a, b) in (((f[0], t[1] - th), (t[0], t[1])), ((f[0], f[1]), (t[0], f[1] + th)), ((f[0], f[1]), (f[0] + th, t[1])), ((t[0] - th, f[1]), (t[0], t[1]))):
            extra.append((a[0], a[1], z0, b[0], b[1], z1, c))
    for (x0, y0, z0, x1, y1, z1, c) in extra:
        m.box(x0, y0, z0, x1, y1, z1, c)
        m.els[-1]["_edge"] = True


def _display(g):
    r = lambda v: round(v * g, 3)
    return {"gui": {"rotation": [20, -30, 0], "translation": [0, -3, 0], "scale": [r(0.42)] * 3},
            "fixed": {"scale": [r(0.5)] * 3}, "ground": {"scale": [r(0.3)] * 3}}


def shrink(els, k, drop=0.0):
    """(8,8,8) 기준 k 배 (회전 원점 포함) 후 drop 만큼 내림 + -16~32 범위로 자르기"""
    def f(v):
        return [round(8 + (c - 8) * k - (drop if i == 1 else 0), 4) for i, c in enumerate(v)]
    for e in els:
        e["from"], e["to"] = f(e["from"]), f(e["to"])
        e["from"] = [max(-16, min(32, c)) for c in e["from"]]
        e["to"] = [max(-16, min(32, c)) for c in e["to"]]
        if "rotation" in e:
            e["rotation"]["origin"] = f(e["rotation"]["origin"])


def write(pack_dir, ns, write_json):
    """모델·팔레트 텍스처 생성. 반환: [(cmd, 모델 이름)]"""
    out = []
    # 아이템 모델 텍스처는 블록 아틀라스(textures/block, textures/item 폴더)에 있어야 한다.
    # textures/boss 에 두면 아틀라스에 들어가지 않아 보라/검정 '텍스처 없음'으로 보인다 → textures/item/boss 로.
    tex_dir = os.path.join(pack_dir, "assets", ns, "textures", "item", "boss")
    os.makedirs(tex_dir, exist_ok=True)
    for i, bid in enumerate(BOSS_ORDER):
        pal = Palette()
        m = Model(pal)
        is_boss = i < BOSS_ORDER.index("mount_wolf") or bid.startswith("field_")
        CLAMP_AT_BUILD[0] = not is_boss
        import voxel_bosses
        voxel = bid in voxel_bosses.BUILDERS   # v5.8.3 복셀 조각 보스: 장식 단계 없이 조각 그대로
        if voxel:
            voxel_bosses.build(bid, m)
        else:
            BUILDERS[bid](m)
        if len(pal.colors) > 256:
            raise SystemExit("보스 팔레트 색이 256 개를 넘음: %s (%d)" % (bid, len(pal.colors)))
        if bid in BOSS_IDS and not voxel and bid != "primordial_dragon":
            if bid not in NO_GRAND:
                grand(m, bid, THEME.get(bid, "ff5050"))       # v5.8.0 갑옷 세부
            edging(m, bid)                                    # v5.8.0 판 테두리
            majesty(m, bid, THEME.get(bid, "ff5050"))        # 후광/왕관 · 날개/망토 (v5.5.0: 바닥 마법진 · 받침대 · 화로 · 등 뒤 마법진 제거)
            relics(m, bid, THEME.get(bid, "ff5050"))          # v5.8.0 떠도는 유물
        if is_boss:
            shrink(m.els, BOSS_SHRINK, BOSS_DROP)
        CLAMP_AT_BUILD[0] = True
        for e in m.els:
            e.pop("_col", None)
            e.pop("_edge", None)
        pal.image().save(os.path.join(tex_dir, bid + ".png"))
        model = {"credit": "RpgCraft boss model", "texture_size": [16, 16],
                 "textures": {"0": ns + ":item/boss/" + bid, "particle": ns + ":item/boss/" + bid},
                 "elements": m.els,
                 "display": _display(1 / BOSS_SHRINK if is_boss else 1)}
        write_json(os.path.join(pack_dir, "assets", ns, "models", "boss", bid + ".json"), model)
        out.append((9000 + i, ns + ":boss/" + bid))
    return out
