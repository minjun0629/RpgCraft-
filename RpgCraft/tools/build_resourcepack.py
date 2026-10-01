#!/usr/bin/env python3
"""
RpgCraft 리소스팩 빌더 (pack_format 15 + 1.20.2~1.21.x 호환 레이어)

- 무기/도구/소모품 텍스처는 tools/pack_art.py 의 디자인 정의로 32x32 픽셀 아트를 그린다.
- 강화 가능한 무기는 +7(각인) / +10(완성) 변형 텍스처가 자동으로 함께 만들어진다.
  (CustomModelData = 기본 번호 + 3000 / + 5000)
- 텍스처는 "없는 파일만" 생성한다. 직접 그린 PNG 로 교체한 뒤 다시 실행해도 덮어쓰지 않는다.
  전체를 다시 그리려면 --force
- 완성 zip 을 dist/ 와 src/main/resources/resourcepack.zip 에 만든다.

사용법:  python3 tools/build_resourcepack.py [--force]
필요:    Pillow (pip install pillow)
"""
import hashlib
import json
import os
import shutil
import sys
import zipfile

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import pack_art as art  # noqa: E402
import voxel3d  # noqa: E402
import spirit_art  # noqa: E402
import boss_models  # noqa: E402
import armor_art  # noqa: E402
import ui_pack  # noqa: E402
import weapon_vox  # noqa: E402  v5.10.21 무기 3D 복셀
import armor_vox  # noqa: E402  v5.10.28 방어구 3D 복셀
import ui4r  # noqa: E402  v5.10.32 마크에이지 4R 풍 UI

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PACK = os.path.join(ROOT, "resourcepack")
FORCE = "--force" in sys.argv
FLAT = "--flat" in sys.argv  # 3D 대신 평면(바닐라식) 모델
NS = "rpgcraft"
STAGE_OFFSET = {1: 3000, 2: 5000}

# (바닐라 재료, CustomModelData, 모델 이름, handheld, 이미지 함수, 강화 변형 여부)
ITEMS = []


def add(vanilla, cmd, name, handheld, img, variants=False):
    ITEMS.append((vanilla, cmd, name, handheld, img, variants))


def _axe_flip(img):
    """도끼: 머리(날)가 손잡이의 앞쪽(바닐라 도끼와 같은 쪽)에 오도록 손잡이 대각선을 기준으로 뒤집음.
    (그대로 두면 손에 들었을 때 날이 손잡이 뒤로 가려 막대기만 보였음) — 3D 모델용 키 맵도 같이 뒤집는다"""
    from PIL import Image as _I
    n = img.width
    out = img.transpose(_I.TRANSVERSE)
    keys = getattr(art, "LAST_KEYS", None)
    if isinstance(keys, dict) and keys:
        art.LAST_KEYS = {(n - 1 - y, n - 1 - x): k for (x, y), k in keys.items()}
    return out


def weapon(vanilla, cmd, name, variants):
    handheld = vanilla != "shield"
    base = lambda st=0, n=name: spirit_art.weapon(n, st) if n in spirit_art.WEAPONS else art.weapon_image(n, st)
    if "axe" in name or name == "spirit_baihu":
        add(vanilla, cmd, name, handheld, lambda st=0, b=base: _axe_flip(b(st)), variants)
    else:
        add(vanilla, cmd, name, handheld, base, variants)


def build_items():
    sword_mat = ["stone_sword", "iron_sword", "iron_sword", "diamond_sword"]
    axe_mat = ["stone_axe", "iron_axe", "iron_axe", "diamond_axe"]
    for i in range(4):
        lv = (i + 1) * 10
        weapon(sword_mat[i], 100 + i, "sword_%d" % lv, True)
        weapon("shears", 100 + i, "dagger_%d" % lv, True)
        weapon(axe_mat[i], 100 + i, "axe_%d" % lv, True)
        weapon("shield", 100 + i, "shield_%d" % lv, True)
    for si, name in enumerate(["witch", "dwarf", "harpy", "sea", "bungbung"]):
        tier = "netherite" if si == 4 else "diamond" if si == 3 else "iron"
        base = 200 + si * 10
        weapon(tier + "_sword", base, name + "_sword", False)
        weapon("shears", base + 1, name + "_dagger", False)
        weapon(tier + "_axe", base + 2, name + "_axe", False)
        weapon("shield", base + 3, name + "_shield", False)
    weapon("shears", 300, "spirit_zhuque", True)
    weapon("netherite_sword", 301, "spirit_qinglong", True)
    weapon("netherite_axe", 302, "spirit_baihu", True)
    weapon("netherite_shovel", 303, "spirit_xuanwu", True)
    for t in range(5):
        mat = "iron" if t <= 1 else "diamond" if t <= 3 else "netherite"
        weapon(mat + "_sword", 150 + t, "bs_sword_%d" % (t + 1), True)
        weapon(mat + "_axe", 150 + t, "bs_axe_%d" % (t + 1), True)
        weapon("shears", 150 + t, "bs_dagger_%d" % (t + 1), True)
        weapon("shield", 150 + t, "bs_shield_%d" % (t + 1), True)
    weapon("netherite_sword", 900, "relic_sword", True)
    weapon("shears", 900, "relic_dagger", True)
    weapon("netherite_axe", 900, "relic_axe", True)
    weapon("shield", 900, "relic_shield", True)

    # 사신수 방어구 (가죽) · 사흉수 갑주 (네더라이트) · 기운 뽑기 결정
    slots = ["helmet", "chestplate", "leggings", "boots"]
    for b, beast in enumerate(["qinglong", "baihu", "zhuque", "xuanwu"]):
        for s in range(4):
            add("leather_" + slots[s], 530 + b * 4 + s, "spirit_%s_a%d" % (beast, s), False, lambda st=0, be=beast, s=s: armor_art.armor(be, s))
    for s, fid in enumerate(["hundun", "taotie", "taowu", "qiongqi"]):
        add("netherite_" + slots[s], 550 + s, "fiend_" + fid, False, lambda st=0, s=s: armor_art.armor("fiend", s))
    for i, el in enumerate(["fire", "wind", "dark", "nature", "earth"]):
        add("amethyst_cluster", 510 + i, "crystal_" + el, False, lambda st=0, e=el: spirit_art.crystal(e))
    # 모든 방어구 전용 아이콘 (Java CustomModelData 와 일치)
    for (vanilla, cmd, name, set_id, slot) in armor_art.catalog():
        add(vanilla, cmd, name, False, lambda st=0, si=set_id, sl=slot: armor_art.armor(si, sl))
    # 무기고 (Lv.5~95, 종류별 8가지) — Java ItemRegistry.registerArmory 와 같은 규칙
    arm_lv = [5, 15, 25, 35, 50, 65, 80, 95]
    tier_mat = lambda L: "wooden" if L < 10 else "stone" if L < 20 else "iron" if L < 40 else "golden" if L < 60 else "diamond" if L < 85 else "netherite"
    cols = [("9aa0a8", "6b6b6b", "5a3a22", "c0c0c0"), ("d8dee6", "3f7fff", "2a2a4a", "7fbfff"), ("e8e8f0", "c9a13b", "5a2a10", "ffe066"),
            ("7fd0ff", "3fa9ff", "1a2a4a", "c8f0ff"), ("3a3a4a", "8a4ad0", "1a1024", "e080ff"), ("ffb070", "e04848", "3a1a0a", "ffe066"),
            ("f0f8ff", "7fe8ff", "2a3a5a", "ffffff"), ("c040ff", "ff3a6a", "1a0a1a", "ff9ad0")]
    P2 = lambda i: art.P(cols[i][0], guard=cols[i][1], grip=cols[i][2], accent=cols[i][3], glow=cols[i][3])
    sw_base = ["sword_10", "sword_20", "sword_30", "witch_sword", "sea_sword", "dwarf_sword", "bs_sword_4", "harpy_sword"]
    dg_base = ["dagger_10", "dagger_20", "dagger_30", "witch_dagger", "sea_dagger", "dwarf_dagger", "bs_dagger_4", "harpy_dagger"]
    ax_base = ["axe_10", "axe_20", "axe_30", "witch_axe", "sea_axe", "dwarf_axe", "bs_axe_4", "harpy_axe"]
    sh_base = ["shield_10", "shield_20", "shield_30", "witch_shield", "sea_shield", "dwarf_shield", "bs_shield_4", "harpy_shield"]
    for i in range(8):
        L, n = arm_lv[i], i + 1
        art.SWORDS["armory_sword_%d" % n] = {"pal": P2(i), "draw": art.SWORDS[sw_base[i]]["draw"]}
        weapon(tier_mat(L) + "_sword", 1299 + n, "armory_sword_%d" % n, True)
        art.DAGGERS["armory_dagger_%d" % n] = {"pal": P2(i), "draw": art.DAGGERS[dg_base[i]]["draw"]}
        weapon("shears", 1299 + n, "armory_dagger_%d" % n, True)
        art.AXES["armory_axe_%d" % n] = {"pal": P2(i), "draw": art.AXES[ax_base[i]]["draw"]}
        weapon(tier_mat(L) + "_axe", 1299 + n, "armory_axe_%d" % n, True)
        b = art.SHIELDS[sh_base[i]]
        art.SHIELDS["armory_shield_%d" % n] = (b[0], P2(i), b[2], b[3])
        weapon("shield", 1299 + n, "armory_shield_%d" % n, True)
        add("bow", 1299 + n, "armory_bow_%d" % n, True, lambda st=0, i=i: spirit_art.bow(art.P(cols[i][2] if i < 4 else cols[i][0], guard=cols[i][1], grip=cols[i][2], accent=cols[i][3], glow=cols[i][3], extra="e8e0d0"), i % 4, st), True)
        add("stick", 1299 + n, "armory_staff_%d" % n, True, lambda st=0, i=i: spirit_art.staff(i % 3, st, art.P(cols[i][2], guard=cols[i][1], grip=cols[i][2], accent=cols[i][3], glow=cols[i][3])), True)
        add(tier_mat(L) + "_shovel", 1299 + n, "armory_spear_%d" % n, True, lambda st=0, i=i: spirit_art.spear(i % 4, st, P2(i)), True)
    # 장신구 · 큐브 · 주문서 · 부적 · 봉인석 · 보물 지도 · 물고기 · 각인석 (고해상도 아이콘 + 입체 모델)
    import misc_art as MA
    for k, (kind, mat, fn) in enumerate([("ring", "gold_nugget", MA.ring), ("neck", "heart_of_the_sea", MA.necklace), ("ear", "amethyst_shard", MA.earring)]):
        for t in (1, 2, 3):
            add(mat, 1400 + k * 3 + t - 1, "acc_%s_%d" % (kind, t), False, lambda st=0, fn=fn, t=t: fn(t))
    add("paper", 1420, "potential_scroll", False, lambda st=0: MA.scroll())
    for li, ln in enumerate("ABCD"):   # v5.10.34 히든 전직서: 계열 · 단계마다 다른 그림 (ENCHANTED_BOOK 2100+)
        for tr in (1, 2, 3):
            add("enchanted_book", 2100 + li * 3 + tr - 1, "hj_scroll_%s%d" % (ln.lower(), tr), False, lambda st=0, ln=ln, tr=tr: MA.hj_scroll(ln, tr))
    add("red_dye", 1421, "cube_red", False, lambda st=0: MA.cube("c02a2a", "ffb0a0"))
    add("light_blue_dye", 1422, "cube_master", False, lambda st=0: MA.cube("2a5ac0", "a0e0ff"))
    add("ghast_tear", 1423, "spirit_summon", False, lambda st=0: MA.talisman())
    add("fire_charge", 1424, "balrog_seal", False, lambda st=0: MA.balrog_seal())
    add("map", 1425, "treasure_map", False, lambda st=0: MA.treasure_map())
    for i, (fid, mat) in enumerate([("fish_small", "cod"), ("fish_carp", "cod"), ("fish_salmon", "salmon"), ("fish_deep", "pufferfish"),
                                    ("fish_gold", "tropical_fish"), ("fish_treasure", "chest_minecart"), ("fish_legend", "tropical_fish")]):
        add(mat, 1430 + i, fid, False, (lambda st=0: MA.chest()) if fid == "fish_treasure" else (lambda st=0, fid=fid: MA.fish(fid)))
    import fish_data                                                                   # v5.2.0 어종 (1600 + 순번)
    for i, spec in enumerate(fish_data.FISH_SPECIES):
        add(spec[2].lower(), 1600 + i, spec[0], False, lambda st=0, spec=spec: MA.sea(spec))
    add("chest_minecart", 1436, "boss_chest", False, lambda st=0: MA.chest())          # 보스 상자 (이전엔 모델이 빠져 있었음)
    add("echo_shard", 1446, "boss_crystal", False, lambda st=0: MA.boss_crystal())     # 보스 수정
    for i, kind in enumerate(["tyrant", "immortal", "storm_eye", "midas", "judge"]):
        add("echo_shard", 1440 + i, "legend_seal_" + kind, False, lambda st=0, kind=kind: MA.legend_seal(kind))
    add("iron_nugget", 1445, "loot_steel", False, lambda st=0: MA.steel_shard())
    loot_mats = ["rabbit_hide", "porkchop", "feather", "string", "nautilus_shell", "leather", "bone", "slime_ball", "prismarine_shard", "fermented_spider_eye",
                 "glowstone_dust", "ink_sac", "brick", "paper", "light_blue_dye", "blaze_powder", "phantom_membrane", "spider_eye", "nether_star", "gold_nugget", "red_dye"]
    for i, lid in enumerate(MA.LOOT_IDS):
        add(loot_mats[i], 1450 + i, lid, False, lambda st=0, lid=lid: MA.loot(lid))
    for i, (gid, gmat) in enumerate(MA.GOODS):   # 잡화 상점 재료 · 약초 · 목재 · 결정
        add(gmat, 1480 + i, gid, False, lambda st=0, gid=gid: MA.goods(gid))
    add("stick", 1520, "weapon_club", True, lambda st=0: MA.club())   # 갈색 몽둥이
    # 무기고 II (Lv.10~110)
    arm2_lv = [10, 20, 30, 45, 60, 75, 90, 110]
    cols2 = [("b0a080", "7a5a2a", "3a2a1a", "e8d8b0"), ("c0c8d0", "2a4a8a", "1a1a2a", "e8e0a0"), ("f0d890", "c9a13b", "6a3a10", "fff0c0"),
             ("bff4ff", "7fc8ff", "2a4a6a", "ffffff"), ("ff8a3a", "c02020", "2a0a0a", "ffe066"), ("e0e8ff", "8a9ad0", "1a2040", "fff8c0"),
             ("ffe066", "3fa9ff", "1a2a4a", "ffffff"), ("5a3a2a", "c9a13b", "1a0a0a", "ff4040")]
    Q = lambda i: art.P(cols2[i][0], guard=cols2[i][1], grip=cols2[i][2], accent=cols2[i][3], glow=cols2[i][3])
    b2 = {"sword": ["bs_sword_1", "bungbung_sword", "bs_sword_2", "sea_sword", "bs_sword_4", "relic_sword", "spirit_qinglong", "bs_sword_5"],
          "dagger": ["bs_dagger_1", "bungbung_dagger", "bs_dagger_2", "sea_dagger", "bs_dagger_4", "relic_dagger", "spirit_zhuque", "bs_dagger_5"],
          "axe": ["bs_axe_1", "bungbung_axe", "bs_axe_2", "sea_axe", "bs_axe_4", "relic_axe", "spirit_baihu", "bs_axe_5"],
          "shield": ["bs_shield_1", "bungbung_shield", "bs_shield_2", "sea_shield", "bs_shield_4", "witch_shield", "harpy_shield", "bs_shield_5"]}
    for i in range(8):
        L, n = arm2_lv[i], i + 1
        art.SWORDS["armory2_sword_%d" % n] = {"pal": Q(i), "draw": art.SWORDS[b2["sword"][i]]["draw"]}
        weapon(tier_mat(L) + "_sword", 1309 + n, "armory2_sword_%d" % n, True)
        art.DAGGERS["armory2_dagger_%d" % n] = {"pal": Q(i), "draw": art.DAGGERS[b2["dagger"][i]]["draw"]}
        weapon("shears", 1309 + n, "armory2_dagger_%d" % n, True)
        art.AXES["armory2_axe_%d" % n] = {"pal": Q(i), "draw": art.AXES[b2["axe"][i]]["draw"]}
        weapon(tier_mat(L) + "_axe", 1309 + n, "armory2_axe_%d" % n, True)
        sb = art.SHIELDS[b2["shield"][i]]
        art.SHIELDS["armory2_shield_%d" % n] = (sb[0], Q(i), sb[2], sb[3])
        weapon("shield", 1309 + n, "armory2_shield_%d" % n, True)
        add("bow", 1309 + n, "armory2_bow_%d" % n, True, lambda st=0, i=i: spirit_art.bow(art.P(cols2[i][0], guard=cols2[i][1], grip=cols2[i][2], accent=cols2[i][3], glow=cols2[i][3], extra="f0e8d8"), (i + 2) % 4, st), True)
        add("stick", 1309 + n, "armory2_staff_%d" % n, True, lambda st=0, i=i: spirit_art.staff((i + 1) % 3, st, art.P(cols2[i][2], guard=cols2[i][1], grip=cols2[i][2], accent=cols2[i][3], glow=cols2[i][3])), True)
        add(tier_mat(L) + "_shovel", 1309 + n, "armory2_spear_%d" % n, True, lambda st=0, i=i: spirit_art.spear((i + 2) % 4, st, Q(i)), True)
    # 무기고 III: 지팡이 · 창 10종씩 (v5.2.0, 새 머리 모양)
    arm3_lv = [12, 22, 35, 48, 58, 70, 85, 100, 115, 130]
    cols3 = [("e0d8b0", "c9a13b", "3a2a1a", "fff3b0"), ("ff9a3a", "c02020", "2a0a0a", "ffe066"), ("7fc86a", "5a3a1a", "3a2a10", "c0ffb0"),
             ("fff0a0", "3f5fbf", "1a1a3a", "ffffff"), ("c0a0ff", "5a3a8a", "1a1024", "e0c0ff"), ("7fe8f0", "3fa9ff", "1a2a4a", "c8ffff"),
             ("ffe066", "6a4ad0", "1a1a3a", "ffffff"), ("bff4ff", "7fc8ff", "2a4a6a", "ffffff"), ("ff7ae0", "3a2a8a", "0a0a2a", "ffd0ff"),
             ("ffd23f", "c02020", "1a0a0a", "fff3d0")]
    R3 = lambda i: art.P(cols3[i][0], guard=cols3[i][1], grip=cols3[i][2], accent=cols3[i][3], glow=cols3[i][3])
    staff3 = [3, 4, 5, 6, 7, 8, 3, 4, 6, 8]
    spear3 = [4, 5, 6, 7, 8, 9, 4, 5, 6, 9]
    for i in range(10):
        L, n = arm3_lv[i], i + 1
        add("stick", 1329 + n, "armory3_staff_%d" % n, True, lambda st=0, i=i: spirit_art.staff(staff3[i], st, art.P(cols3[i][2], guard=cols3[i][1], grip=cols3[i][2], accent=cols3[i][3], glow=cols3[i][3])), True)
        add(tier_mat(L) + "_shovel", 1329 + n, "armory3_spear_%d" % n, True, lambda st=0, i=i: spirit_art.spear(spear3[i], st, R3(i)), True)
    # 초월 장비 5단계 · 뺀더의 명작 (기존 디자인 틀 + 새 색 조합)
    P = art.P
    tpal = [P("ffe9c8", guard="ffd23f", grip="5a3a22", accent="ff9a3a", glow="fff3d0"),
            P("c090ff", guard="6a3aa0", grip="2a1a3a", accent="ff7ae0", glow="f0d0ff"),
            P("7fd0ff", guard="e070ff", grip="1a2a4a", accent="ff9ad0", glow="e0f8ff"),
            P("4a3a6a", guard="1a1024", grip="120a1a", accent="e080ff", glow="e080ff"),
            P("ffffff", guard="ffd23f", grip="c9a13b", accent="7fe8ff", glow="fff3b0")]
    bases = {"sword": ["bs_sword_3", "witch_sword", "harpy_sword", "relic_sword", "bs_sword_5"],
             "dagger": ["bs_dagger_3", "witch_dagger", "harpy_dagger", "relic_dagger", "bs_dagger_5"],
             "axe": ["bs_axe_3", "witch_axe", "harpy_axe", "relic_axe", "bs_axe_5"],
             "shield": ["bs_shield_3", "witch_shield", "harpy_shield", "sea_shield", "bs_shield_5"]}
    dicts = {"sword": art.SWORDS, "dagger": art.DAGGERS, "axe": art.AXES}
    mats = {"sword": "netherite_sword", "dagger": "shears", "axe": "netherite_axe", "shield": "shield"}

    def clone(kind, base, name, pal):
        if kind == "shield":
            b = art.SHIELDS[base]
            art.SHIELDS[name] = (b[0], pal, b[2], b[3])
        else:
            dicts[kind][name] = {"pal": pal, "draw": dicts[kind][base]["draw"]}

    for t in range(5):
        for kind, bl in bases.items():
            name = "trans_%s_%d" % (kind, t + 1)
            clone(kind, bl[t], name, tpal[t])
            weapon(mats[kind], 1101 + t, name, True)
    gold = P("fff0c0", guard="ffd23f", grip="3a1a0a", accent="ff4040", glow="fffbe0")
    for kind, base, cmd in (("sword", "spirit_qinglong", 950), ("dagger", "spirit_zhuque", 951), ("axe", "spirit_baihu", 952), ("shield", "bs_shield_5", 953)):
        name = "pender_" + kind
        if kind == "shield":
            clone(kind, base, name, gold)
        else:
            dicts[kind][name] = {"pal": gold, "draw": dicts[kind]["relic_" + kind]["draw"] if kind != "sword" else dicts[kind]["relic_sword"]["draw"]}
        weapon(mats[kind], cmd, name, True)
    add("netherite_shovel", 954, "pender_spear", True, lambda st=0: spirit_art.spear(4, st), True)
    # 확장: 창 · 지팡이 (강화 변형 포함) · 주문서
    for i, mat in enumerate(["stone_shovel", "iron_shovel", "golden_shovel", "diamond_shovel"]):
        add(mat, 100 + i, "spear_%d" % ((i + 1) * 10), True, lambda st=0, i=i: spirit_art.spear(i, st), True)
    for i in range(3):
        add("stick", 100 + i, "staff_%d" % (i + 1), True, lambda st=0, i=i: spirit_art.staff(i, st), True)
    for k, kind in enumerate(["atk", "def", "speed", "exp", "return"]):
        add("paper", 20 + k, "scroll_" + kind, False, lambda st=0, kd=kind: spirit_art.scroll(kd))
    add("paper", 1, "check", False, lambda st=0: art.ticket("check"))
    for cmd, name, kind in [(10, "ticket_protect", "protect"), (11, "ticket_rate10", "rate"), (12, "ticket_war", "war"),
                            (13, "ticket_rune", "rune"), (14, "ticket_totem", "totem"), (15, "ticket_stat_reset", "reset")]:
        add("paper", cmd, name, False, lambda st=0, k=kind: art.ticket(k))
    for i in range(4):
        add("stone_axe", 400 + i, "hammer_%d" % i, True, lambda st=0, i=i: art.hammer(i))
    for i in range(3):
        add("firework_star", 600 + i, "rune_%d" % (i + 1), False, lambda st=0, i=i: art.rune_stone(i))
    for mat, el in [("blaze_powder", "fire"), ("feather", "wind"), ("ink_sac", "dark"), ("slime_ball", "nature"), ("flint", "earth")]:
        add(mat, 500, "shard_" + el, False, lambda st=0, e=el: art.shard(e))
    for mat, el, hh in [("blaze_rod", "fire", True), ("heart_of_the_sea", "wind", False), ("dragon_breath", "dark", False),
                        ("scute", "nature", False), ("raw_gold", "earth", False)]:
        add(mat, 520, "essence_" + el, hh, lambda st=0, e=el: art.orb(e))
    for i, mat in enumerate(["wooden_pickaxe", "iron_pickaxe", "diamond_pickaxe"]):
        add(mat, 700 + i, "tool_gather_%d" % (i + 1), True, lambda st=0, i=i: art.pickaxe(i))
    for i in range(4):
        add("rabbit_foot", 700 + i, "potion_%d" % (i + 1), False, lambda st=0, i=i: art.potion(i))   # v5.4.22: 64개씩 쌓이게 토끼발에 씌움
    for mat, kind in [("netherite_scrap", "black"), ("emerald", "green"), ("redstone", "red"), ("lapis_lazuli", "blue"), ("clay_ball", "gray")]:
        add(mat, 800, "ore_" + kind, False, lambda st=0, k=kind: art.ore(k))


HANDHELD = {"wooden_sword", "golden_sword", "wooden_axe", "golden_axe", "wooden_shovel", "netherite_shovel", "stone_shovel", "iron_shovel", "golden_shovel", "diamond_shovel", "stick", "stone_sword", "iron_sword", "diamond_sword", "netherite_sword", "stone_axe", "iron_axe", "diamond_axe", "netherite_axe",
            "netherite_shovel", "wooden_pickaxe", "iron_pickaxe", "diamond_pickaxe", "blaze_rod"}
SPECIAL_BASE = {
    "potion": {"parent": "minecraft:item/generated", "textures": {"layer0": "minecraft:item/potion_overlay", "layer1": "minecraft:item/potion"}},
    "firework_star": {"parent": "minecraft:item/generated", "textures": {"layer0": "minecraft:item/firework_star", "layer1": "minecraft:item/firework_star_overlay"}},
    "gray_stained_glass_pane": {"parent": "minecraft:item/generated", "textures": {"layer0": "minecraft:block/gray_stained_glass"}},
    "black_stained_glass_pane": {"parent": "minecraft:item/generated", "textures": {"layer0": "minecraft:block/black_stained_glass"}},
    "shield": {
        "parent": "builtin/entity", "gui_light": "front", "textures": {"particle": "block/dark_oak_planks"},
        "display": {
            "thirdperson_righthand": {"rotation": [0, 90, 0], "translation": [10, 6, -4], "scale": [1, 1, 1]},
            "thirdperson_lefthand": {"rotation": [0, 90, 0], "translation": [10, 6, 12], "scale": [1, 1, 1]},
            "firstperson_righthand": {"rotation": [0, 180, 5], "translation": [-10, 2, -10], "scale": [1.25, 1.25, 1.25]},
            "firstperson_lefthand": {"rotation": [0, 180, 5], "translation": [10, 0, -10], "scale": [1.25, 1.25, 1.25]},
            "gui": {"rotation": [15, -25, -5], "translation": [2, 3, 0], "scale": [0.65, 0.65, 0.65]},
            "fixed": {"rotation": [0, 180, 0], "translation": [-4.5, 4.5, -5], "scale": [0.55, 0.55, 0.55]},
            "ground": {"rotation": [0, 0, 0], "translation": [2, 4, 2], "scale": [0.25, 0.25, 0.25]}},
        "overrides": [{"predicate": {"blocking": 1}, "model": "item/shield_blocking"}]},
}

# 방패 커스텀 모델: 평면 아이템이지만 들었을 때 방패처럼 보이도록 표시 위치 조정
SHIELD_DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, 90, 0], "translation": [0, 3, 1.5], "scale": [1.0, 1.0, 1.0]},
    "thirdperson_lefthand": {"rotation": [0, -90, 0], "translation": [0, 3, 1.5], "scale": [1.0, 1.0, 1.0]},
    "firstperson_righthand": {"rotation": [0, -80, 5], "translation": [-2, 2.5, 1], "scale": [0.9, 0.9, 0.9]},
    "firstperson_lefthand": {"rotation": [0, 80, -5], "translation": [-2, 2.5, 1], "scale": [0.9, 0.9, 0.9]},
}


def base_model(v):
    vf = os.path.join(os.path.dirname(os.path.abspath(__file__)), "vanilla", "models", v + ".json")
    if os.path.exists(vf):  # 바닐라 원본 유지 (방어구 트림 표시 등)
        return json.load(open(vf, encoding="utf-8"))
    if v in SPECIAL_BASE:
        return json.loads(json.dumps(SPECIAL_BASE[v]))
    return {"parent": "minecraft:item/handheld" if v in HANDHELD else "minecraft:item/generated", "textures": {"layer0": "minecraft:item/" + v}}


# ============================================================ GUI 배경 (폰트 글리프)
GUI_GLYPHS = {"main": ("\ue000", 6), "rows1": ("\ue011", 1), "rows2": ("\ue012", 2), "rows3": ("\ue013", 3),
              "rows4": ("\ue014", 4), "rows5": ("\ue015", 5), "rows6": ("\ue016", 6)}
MAIN_SLOTS = {4, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43, 49}


def _noise(x, y, k=3):
    return ((x * 73856093) ^ (y * 19349663)) % (2 * k + 1) - k


def _px(d, x, y, c):
    d.point((x, y), fill=c)


def _diamond(d, cx, cy, r, c, edge):
    for dy in range(-r, r + 1):
        w = r - abs(dy)
        for dx in range(-w, w + 1):
            _px(d, cx + dx, cy + dy, edge if abs(dx) == w else c)


def _slot(d, x, y, hi, gold):
    """18x18 슬롯 — v5.10.32 마크에이지 4R 풍 (ui4r)"""
    ui4r.slot(d, x, y, hi, gold)


def gui_background(rows, highlight=None):
    """상자 창 배경 — v5.10.32 마크에이지 4R 풍: 회갈색 판 + 은청동 테두리 + 파인 칸 (ui4r)"""
    return ui4r.gui_background(rows, highlight, MAIN_SLOTS)


def pack_icon():
    img = Image.new("RGBA", (64, 64), (24, 20, 30, 255))
    d = ImageDraw.Draw(img)
    for y in range(64):
        for x in range(64):
            v = int(20 + 18 * (1 - ((x - 32) ** 2 + (y - 32) ** 2) ** 0.5 / 45))
            d.point((x, y), fill=(v + 6, v, v + 14, 255))
    d.rectangle([0, 0, 63, 63], outline=(201, 161, 59, 255), width=3)
    d.rectangle([3, 3, 60, 60], outline=(110, 84, 30, 255))
    sw = art.weapon_image("relic_sword", 2).resize((48, 48), Image.NEAREST)
    img.alpha_composite(sw, (8, 8))
    return img



# ============================================================ 빌드
def write_json(path, obj, compact=False):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        if compact:
            json.dump(obj, f, ensure_ascii=False, separators=(",", ":"))
        else:
            json.dump(obj, f, ensure_ascii=False, indent=2)


def model_3d(name, img_fn, handheld, tex_path):
    """입체 모델. 디자인 부품 키로 두께를 정하되, 직접 그린 PNG 로 윤곽이 바뀌었으면 PNG 기준으로 계산"""
    png = Image.open(tex_path).convert("RGBA")
    if name in art.SWORDS or name in art.DAGGERS or name in art.AXES or name in art.SHIELDS or name.startswith(("relic_", "spirit_", "hammer", "tool_gather", "spear_", "staff_")):
        img_fn(0)
        keys = art.LAST_KEYS
        if set(keys.keys()) == voxel3d.mask_of(png):
            depth = voxel3d.depth_from_keys(keys, voxel3d.table_for(name))
        else:
            depth = voxel3d.depth_from_alpha(png, base=1.0, step=0.5, maxd=3.0)
    else:
        depth = voxel3d.misc_depth(name, png)
    if handheld:   # 무기: 가장자리로 갈수록 얇게 깎아 조각한 듯한 입체감 (가운데 능선이 도드라짐)
        dist = voxel3d.depth_from_alpha(png, base=1.0, step=1.0, maxd=4.0)
        depth = {p: round(d * (0.55 + 0.45 * min(1.0, (dist.get(p, 1.0) - 1.0) / 3.0)) * 4) / 4 for p, d in depth.items()}
    return voxel3d.model(NS + ":item/" + name, depth, handheld, png.width)


def save_png(path, img_fn):
    if os.path.exists(path) and not FORCE:
        return False
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img_fn().save(path)
    return True


def item_model(name, handheld, vanilla):
    m = {"parent": "minecraft:item/handheld" if handheld else "minecraft:item/generated", "textures": {"layer0": NS + ":item/" + name}}
    return m


def protect_zip(path):
    """리소스팩 뜯어보기 방지 (v5.5.0): 모든 파일의 CRC32 칸을 0 으로 지운다.
    마인크래프트는 java.util.zip.ZipFile 로 읽어 CRC 를 확인하지 않으므로 그대로 적용되지만,
    알집 · 반디집 · 7-Zip · 윈도우 탐색기 같은 압축 프로그램은 "CRC 오류 / 손상된 파일"로 풀기를 거부하거나 깨진 파일을 낸다.
    (완벽한 보호는 불가능 — 작정하고 뜯는 사람은 막을 수 없고, 가볍게 뜯어보는 것을 막는 용도)
    끄려면 환경 변수 RPGCRAFT_PACK_PROTECT=0"""
    import struct
    data = bytearray(open(path, "rb").read())
    eocd = data.rfind(b"PK\x05\x06")   # 중앙 디렉터리 끝 → 실제 헤더 위치만 정확히 따라감 (압축된 내용 속 우연한 "PK.." 는 건드리지 않음)
    if eocd < 0:
        raise SystemExit("zip 중앙 디렉터리를 찾지 못함")
    count, cd_size, cd_off = struct.unpack_from("<HII", data, eocd + 10)
    i = cd_off
    for _ in range(count):
        if data[i:i + 4] != b"PK\x01\x02":
            raise SystemExit("zip 중앙 디렉터리 형식 오류")
        n_len, e_len, c_len = struct.unpack_from("<HHH", data, i + 28)
        local = struct.unpack_from("<I", data, i + 42)[0]
        if data[local:local + 4] != b"PK\x03\x04":
            raise SystemExit("zip 로컬 헤더 형식 오류")
        data[i + 16:i + 20] = b"\0\0\0\0"          # 중앙 디렉터리 CRC
        data[local + 14:local + 18] = b"\0\0\0\0"  # 로컬 헤더 CRC
        i += 46 + n_len + e_len + c_len
    open(path, "wb").write(bytes(data))


BGM_SITUATIONS = ("town", "field", "battle", "boss", "dungeon")
# 게임 속 필드 음악(바닐라) — 필드 곡이 하나라도 있으면 겹치지 않게 비운다 (메뉴 화면 · 엔더 드래곤 · 엔딩 음악은 그대로)
VANILLA_MUSIC = ["music.game", "music.creative", "music.under_water", "music.end", "music.nether.basalt_deltas", "music.nether.crimson_forest",
                 "music.nether.nether_wastes", "music.nether.soul_sand_valley", "music.nether.warped_forest"] + [
    "music.overworld." + b for b in ("deep_dark", "dripstone_caves", "grove", "jagged_peaks", "lush_caves", "swamp", "jungle", "old_growth_taiga",
                                     "meadow", "cherry_grove", "frozen_peaks", "snowy_slopes", "stony_peaks", "forest", "flower_forest", "desert",
                                     "badlands", "bamboo_jungle", "sparse_jungle")]


def ogg_seconds(path):
    """Ogg Vorbis 길이(초): 마지막 페이지의 granule position / 샘플레이트"""
    import struct
    data = open(path, "rb").read()
    i = data.find(b"\x01vorbis")
    if i < 0:
        raise SystemExit("Ogg Vorbis 파일이 아님: " + path)
    rate = struct.unpack_from("<I", data, i + 12)[0]
    last = data.rfind(b"OggS")
    granule = struct.unpack_from("<q", data, last + 6)[0]
    return max(1, int(round(granule / float(rate))))


def write_bgm():
    """상황별 배경음악 (v5.5.0): tools/bgm/<상황>/*.ogg → rpgcraft:bgm.<상황>.<번호>
    곡 길이는 assets/rpgcraft/bgm_tracks.txt 에 적어 두고 플러그인이 읽어서 반복 시점을 계산한다."""
    import re as _re
    src = os.path.join(os.path.dirname(os.path.abspath(__file__)), "bgm")
    out = os.path.join(PACK, "assets", NS, "sounds", "bgm")
    shutil.rmtree(out, ignore_errors=True)
    for f in ("sounds.json", "bgm_tracks.txt"):
        if os.path.exists(os.path.join(PACK, "assets", NS, f)):
            os.remove(os.path.join(PACK, "assets", NS, f))
    vs = os.path.join(PACK, "assets", "minecraft", "sounds.json")
    if os.path.exists(vs):
        os.remove(vs)
    events, lines, counts = {}, ["# 상황 사운드이름 길이(초) — tools/build_resourcepack.py 가 자동 생성"], {}
    for sit in BGM_SITUATIONS:
        d = os.path.join(src, sit)
        files = sorted(f for f in os.listdir(d) if f.lower().endswith(".ogg")) if os.path.isdir(d) else []
        for n, f in enumerate(files, 1):
            name = _re.sub(r"[^a-z0-9_]+", "_", os.path.splitext(f)[0].lower()).strip("_") or "track"
            os.makedirs(os.path.join(out, sit), exist_ok=True)
            shutil.copyfile(os.path.join(d, f), os.path.join(out, sit, name + ".ogg"))
            ev = "bgm.%s.%d" % (sit, n)
            events[ev] = {"sounds": [{"name": NS + ":bgm/%s/%s" % (sit, name), "stream": True, "attenuation_distance": 256}]}
            lines.append("%s %s:%s %d" % (sit, NS, ev, ogg_seconds(os.path.join(d, f))))
        counts[sit] = len(files)
    if events:
        write_json(os.path.join(PACK, "assets", NS, "sounds.json"), events)
        with open(os.path.join(PACK, "assets", NS, "bgm_tracks.txt"), "w", encoding="utf-8") as fh:
            fh.write("\n".join(lines) + "\n")
    if counts.get("field"):
        write_json(os.path.join(PACK, "assets", "minecraft", "sounds.json"), {m: {"replace": True, "sounds": []} for m in VANILLA_MUSIC})
    print("배경음악: " + " / ".join("%s %d곡" % (k, v) for k, v in counts.items()))


def main():
    build_items()
    made = 0
    textures = 0
    write_json(os.path.join(PACK, "pack.mcmeta"), {"pack": {"pack_format": 15, "supported_formats": {"min_inclusive": 15, "max_inclusive": 999}, "description": "\u00a76\u00a7lRpgCraft \u00a7r\u00a77전용 리소스팩 \u00a78· 3D 무기 · 메뉴 디자인"}})
    if save_png(os.path.join(PACK, "pack.png"), pack_icon):
        made += 1

    overrides = {}
    elements_total = 0
    for vanilla, cmd, name, handheld, img, variants in ITEMS:
        stages = [0, 1, 2] if variants else [0]
        for st in stages:
            mname = name if st == 0 else name + ("_e7" if st == 1 else "_e10")
            tex = os.path.join(PACK, "assets", NS, "textures", "item", mname + ".png")
            if save_png(tex, lambda img=img, st=st: img(st)):
                made += 1
            textures += 1
            path = os.path.join(PACK, "assets", NS, "models", "item", mname + ".json")
            if not FLAT and name in armor_vox.MAP and st == 0:   # v5.10.28 방어구: 블록을 쌓아 깎은 3D 모델
                av = armor_vox.build(name)
                els, acols, adisp = av
                elements_total += len(els)
                atex = os.path.join(PACK, "assets", NS, "textures", "item", "avox", mname + ".png")
                os.makedirs(os.path.dirname(atex), exist_ok=True)
                weapon_vox.palette_image(acols).save(atex)
                write_json(path, armor_vox.model_json(NS + ":item/avox/" + mname, els, adisp), compact=True)
            elif not FLAT and weapon_vox.kind_of(name):   # v5.10.21 무기: 블록을 쌓아 깎은 3D 모델 (팔레트 텍스처, 강화 변형은 팔레트만 더 화려하게)
                if st == 0:
                    img(0)
                    wv = weapon_vox.build(name, Image.open(tex).convert("RGBA"), dict(getattr(art, "LAST_KEYS", {}) or {}))
                    els, wcols, wkind = wv
                    elements_total += len(els)
                    write_json(path, weapon_vox.model_json(NS + ":item/wvox/" + mname, els, wkind), compact=True)
                wtex = os.path.join(PACK, "assets", NS, "textures", "item", "wvox", mname + ".png")
                os.makedirs(os.path.dirname(wtex), exist_ok=True)
                weapon_vox.palette_image(weapon_vox.stage_colors(wcols, st)).save(wtex)
                if st > 0:
                    write_json(path, {"parent": NS + ":item/" + name, "textures": {"0": NS + ":item/wvox/" + mname, "particle": NS + ":item/wvox/" + mname}})
            elif FLAT:
                write_json(path, item_model(mname, handheld, vanilla))
            elif st == 0:
                m3 = model_3d(name, img, handheld, tex)
                elements_total += len(m3["elements"])
                write_json(path, m3, compact=True)
            else:  # 강화 변형: 같은 입체 형상을 쓰고 텍스처만 교체
                write_json(path, {"parent": NS + ":item/" + name, "textures": {"0": NS + ":item/" + mname, "particle": NS + ":item/" + mname}})
            overrides.setdefault(vanilla, []).append((cmd + STAGE_OFFSET.get(st, 0), NS + ":item/" + mname))

    # 스킬 이펙트 모델 (LEATHER_HORSE_ARMOR 9500+, 염색 색 = 이펙트 색)
    import vfx
    for cmd, model in vfx.write(PACK, NS, lambda p, o: write_json(p, o)):
        overrides.setdefault("leather_horse_armor", []).append((cmd, model))
    # 미니게임 아이콘 (PAPER CustomModelData 12000+, v5.6.0)
    import minigame_art
    for cmd, model in minigame_art.write_icons(PACK, NS, lambda p, o: write_json(p, o)):
        overrides.setdefault("paper", []).append((cmd, model))
    # v5.10.34 4R 풍 메뉴 아이콘 (PAPER CustomModelData 12600+, 회색 +100) + Java UiIcon 자동 생성
    import ui_icons
    for cmd, model in ui_icons.write_icons(PACK, NS, lambda p, o: write_json(p, o)):
        overrides.setdefault("paper", []).append((cmd, model))
    ui_icons.write_java(os.path.join(ROOT, "src", "main", "java", "kr", "rpgcraft", "gui", "UiIcon.java"))
    import dmg_skin   # v5.10.35 대미지 스킨 아이템 아이콘 (PAPER 12801+)
    for cmd, model in dmg_skin.write_icons(PACK, NS, lambda p, o: write_json(p, o)):
        overrides.setdefault("paper", []).append((cmd, model))
    import board_art   # v5.10.45 보드게임 그림 (PAPER 13400+)
    for cmd, model in board_art.write_icons(PACK, NS, lambda p, o: write_json(p, o)):
        overrides.setdefault("paper", []).append((cmd, model))
    import hjw_vox   # v5.10.44 히든 직업 전용 무기 (각 재질 CMD 7700, 강화 +3000 · +5000 도 같은 모델)
    hj_ov, hj_n = hjw_vox.write(PACK, NS, lambda p, o: write_json(p, o, compact=True))
    elements_total += hj_n
    for mat, cmd, model in hj_ov:
        overrides.setdefault(mat, []).append((cmd, model))
    # 보스 3D 모델 (PAPER CustomModelData 9000+)
    for cmd, model in boss_models.write(PACK, NS, lambda p, o: write_json(p, o, compact=True)):
        overrides.setdefault("paper", []).append((cmd, model))
    # 일반 몬스터 3D 모델 (PAPER CustomModelData 9100+, v5.9.0) — 번호 · 키는 플러그인 리소스 mob-models.yml 로도 씀
    import mob_models
    for cmd, model in mob_models.write(PACK, NS, lambda p, o: write_json(p, o, compact=True),
                                       plugin_res=os.path.join(ROOT, "src", "main", "resources")):
        overrides.setdefault("paper", []).append((cmd, model))
    used = [c for c, _ in overrides.get("paper", [])]
    if len(used) != len(set(used)):
        raise SystemExit("PAPER CustomModelData 번호가 겹침: %s" % sorted({c for c in used if used.count(c) > 1}))
    save_png(os.path.join(PACK, "assets", NS, "textures", "item", "empty.png"), lambda: Image.new("RGBA", (16, 16), (0, 0, 0, 0)))
    write_json(os.path.join(PACK, "assets", NS, "models", "item", "empty.json"),
               {"parent": "minecraft:item/generated", "textures": {"layer0": NS + ":item/empty"}})
    for pane in ("gray_stained_glass_pane", "black_stained_glass_pane"):
        overrides.setdefault(pane, []).append((1, NS + ":item/empty"))

    # 예전 병 포션(POTION 700~703)도 같은 모델로 보이게 (플러그인이 접속 때 새 포션으로 바꾸기 전까지)
    for i in range(4):
        overrides.setdefault("potion", []).append((700 + i, NS + ":item/potion_%d" % (i + 1)))

    for vanilla, lst in overrides.items():
        m = base_model(vanilla)
        ov = m.setdefault("overrides", [])
        cmds = [c for c, _ in lst]
        if len(cmds) != len(set(cmds)):
            raise SystemExit("CustomModelData 중복: " + vanilla)
        for cmd, model in sorted(lst):
            ov.append({"predicate": {"custom_model_data": cmd}, "model": model})
            if vanilla == "shield":   # v5.10.34 막을 때는 바닐라처럼 앞으로 들어 올림 (같은 번호 · blocking 이 뒤에 와야 이김)
                ns, path = model.split(":", 1)
                write_json(os.path.join(PACK, "assets", ns, "models", path + "_blocking.json"),
                           {"parent": model, "display": weapon_vox.DISPLAY_SHIELD_BLOCKING})
                ov.append({"predicate": {"blocking": 1, "custom_model_data": cmd}, "model": model + "_blocking"})
        write_json(os.path.join(PACK, "assets", "minecraft", "models", "item", vanilla + ".json"), m)

    providers = []
    adv = {}
    for i in range(9):
        adv[chr(0xF801 + i)] = -(2 ** i)
        adv[chr(0xF821 + i)] = 2 ** i
    providers.append({"type": "space", "advances": adv})
    for key, (ch, rows) in GUI_GLYPHS.items():
        tex = os.path.join(PACK, "assets", NS, "textures", "gui", key + ".png")
        os.makedirs(os.path.dirname(tex), exist_ok=True)   # 창 배경은 매번 새로 그림 (디자인이 바뀌어도 예전 그림이 남지 않게)
        gui_background(rows, MAIN_SLOTS if key == "main" else None).save(tex)
        providers.append({"type": "bitmap", "file": NS + ":gui/" + key + ".png", "ascent": 13, "height": 114 + rows * 18, "chars": [ch]})
    # 메뉴별 전용 배경
    for key, (ch, rows, img) in ui_pack.gui_layout_images(gui_background).items():
        img.save(os.path.join(PACK, "assets", NS, "textures", "gui", key + ".png"))
        providers.append({"type": "bitmap", "file": NS + ":gui/" + key + ".png", "ascent": 13, "height": 114 + rows * 18, "chars": [ch]})
    # v5.10.32 GUI 머리 장식 (제목 띠 + 날개 검 문장) — 모든 창 제목 앞에 붙음
    ui4r.header().save(os.path.join(PACK, "assets", NS, "textures", "gui", "header.png"))
    providers.append({"type": "bitmap", "file": NS + ":gui/header.png", "ascent": ui4r.HEADER_ASCENT, "height": ui4r.HEADER_H, "chars": ["\ue020"]})
    # 로고 · 레벨 배지
    extra, logo_img = ui_pack.default_font_extra(PACK, art.weapon_image("relic_sword", 2))
    providers.extend(extra)
    providers.append(ui_pack.boss_frame_provider(PACK))   # 보스바 장식 틀
    providers.extend(ui4r.sidebar_providers(PACK, NS))   # v5.10.34 사이드바 아이콘 · 구분선
    providers.append(ui4r.compass_provider(PACK, NS))   # v5.10.34 나침반 틀
    import num4r   # v5.10.49 4R 풍 기본 대미지 숫자 + Java 폭 표
    providers.extend(num4r.damage_providers(PACK, NS))
    num4r.write_java(os.path.join(ROOT, "src", "main", "java", "kr", "rpgcraft", "pack", "Num4R.java"), num4r.hud_table())
    import target_hud   # v5.10.45 대상 정보 틀 · 체력 바 (보스바 제목)
    providers.extend(target_hud.providers(PACK, NS))
    import portraits   # v5.10.49 몬스터 · 보스 초상 (적 정보 문장 안) + portraits.yml
    providers.extend(portraits.write(PACK, NS, os.path.join(ROOT, "src", "main", "resources")))
    import dmg_skin   # v5.10.35 대미지 스킨 숫자 글꼴
    providers.extend(dmg_skin.providers(PACK, NS))
    # 바닐라 기본 폰트 참조 (다른 팩과 합쳐지지 않는 환경에서도 글자가 깨지지 않도록)
    for ref in ("minecraft:include/space", "minecraft:include/default", "minecraft:include/unifont"):
        providers.append({"type": "reference", "id": ref})
    write_json(os.path.join(PACK, "assets", "minecraft", "font", "default.json"), {"providers": providers})

    # 커스텀 HUD 폰트 + 바닐라 하트/배고픔 숨김 + 금색 경험치 바
    hud_n = ui_pack.write_hud(PACK, write_json)
    vdir = os.path.join(os.path.dirname(os.path.abspath(__file__)), "vanilla")
    ui_pack.write_party_fonts(PACK, write_json, vdir)
    widths = ui_pack.ascii_widths(vdir)
    jw = os.path.join(ROOT, "src", "main", "java", "kr", "rpgcraft", "pack", "AsciiWidths.java")
    with open(jw, "w", encoding="utf-8") as f:
        f.write("package kr.rpgcraft.pack;\n\n/** 자동 생성 (tools/build_resourcepack.py): 바닐라 ascii 글꼴 0x20~0x7E 글자 폭 */\n"
                "public final class AsciiWidths {\n    private AsciiWidths() {}\n\n    public static final int[] W = {" + ", ".join(map(str, widths)) + "};\n\n"
                "    public static int of(char c) {\n        return c >= 0x20 && c < 0x7F ? W[c - 0x20] : 6;\n    }\n}\n")
    icons = ui_pack.icons_png(os.path.join(os.path.dirname(os.path.abspath(__file__)), "vanilla", "icons.png"))
    os.makedirs(os.path.join(PACK, "assets", "minecraft", "textures", "gui"), exist_ok=True)
    icons.save(os.path.join(PACK, "assets", "minecraft", "textures", "gui", "icons.png"))
    os.makedirs(os.path.join(ROOT, "dist"), exist_ok=True)
    ui_pack.hud_preview(PACK, os.path.join(os.path.dirname(os.path.abspath(__file__)), "vanilla", "widgets.png"), icons,
                        os.path.join(ROOT, "dist", "hud-preview.png"))
    logo_img.resize((logo_img.width * 4, logo_img.height * 4), Image.NEAREST).save(os.path.join(ROOT, "dist", "logo-preview.png"))
    print("HUD 글리프 %d개" % hud_n)
    # 1.20.2+ 클라이언트: 하트/배고픔/갑옷/산소 스프라이트 숨김 + 금색 경험치 바 (1.20.1 은 icons.png 사용)
    ui_pack.write_modern_hud_sprites(PACK)
    # 1.20.1 사이드바 오른쪽 빨간 점수 숫자 숨김 (화면 가장 오른쪽 끝의 빨간 GUI 글자만)
    import target_hud   # v5.10.45 4R 풍 대상 정보 (오른쪽 위)
    ui_pack.write_score_shader(PACK, os.path.join(os.path.dirname(os.path.abspath(__file__)), "vanilla", "rendertype_text.vsh"), target_hud.shader_snippet())
    # 보스 체력바 디자인 (1.20.1 bars.png + 1.20.2+ 스프라이트). WHITE 는 나침반 문구용 투명 바
    ui_pack.write_boss_bars(PACK, os.path.join(os.path.dirname(os.path.abspath(__file__)), "vanilla", "bars.png"))
    ui_pack.boss_bar_preview(os.path.join(ROOT, "dist", "bossbar-preview.png"))
    # 1.21.4+ 클라이언트: 새 아이템 정의 형식(items/*.json)으로 커스텀 모델 연결
    ui_pack.write_item_definitions(PACK, overrides, write_json)

    ui4r.write_vanilla(PACK, os.path.join(os.path.dirname(os.path.abspath(__file__)), "vanilla", "widgets.png"))   # v5.10.32 핫바 · 인벤토리 · 상자
    write_bgm()
    validate()

    dist = os.path.join(ROOT, "dist")
    os.makedirs(dist, exist_ok=True)
    out = os.path.join(dist, "RpgCraft-ResourcePack.zip")
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        for base, _, files in os.walk(PACK):
            for f in sorted(files):
                full = os.path.join(base, f)
                z.write(full, os.path.relpath(full, PACK))
    if os.environ.get("RPGCRAFT_PACK_PROTECT", "1") != "0":
        protect_zip(out)
    shutil.copyfile(out, os.path.join(ROOT, "src", "main", "resources", "resourcepack.zip"))
    sha1 = hashlib.sha1(open(out, "rb").read()).hexdigest()
    print("아이템 %d종 / 텍스처 %d장 (새로 그림 %d) / 3D 요소 %d개" % (len(ITEMS), textures, made, elements_total))
    print("리소스팩: %s (%d KB)  SHA-1 %s" % (out, os.path.getsize(out) // 1024, sha1))


def validate():
    """모든 모델의 parent/texture 참조가 실제로 존재하는지, JSON 이 올바른지 검사"""
    problems = []
    import re as _re
    for base, _, files in os.walk(os.path.join(PACK, "assets")):
        for f in files:
            rel = os.path.relpath(os.path.join(base, f), os.path.join(PACK, "assets")).replace(os.sep, "/")
            if not _re.fullmatch(r"[a-z0-9_.\-/]+", rel):
                problems.append("경로에 허용되지 않는 문자(대문자 등): " + rel)
    vanilla_ok = ("minecraft:item/", "minecraft:block/", "item/", "block/", "builtin/")
    for base, _, files in os.walk(os.path.join(PACK, "assets")):
        for f in files:
            if not f.endswith(".json"):
                continue
            path = os.path.join(base, f)
            try:
                data = json.load(open(path, encoding="utf-8"))
            except Exception as e:
                problems.append("JSON 오류 %s: %s" % (path, e))
                continue
            refs = list(data.get("textures", {}).values()) + [o["model"] for o in data.get("overrides", [])]
            par = data.get("parent", "")
            if par.startswith(NS + ":") and not os.path.exists(os.path.join(PACK, "assets", NS, "models", par.split(":", 1)[1] + ".json")):
                problems.append("부모 모델 없음 %s -> %s" % (f, par))
            for el in data.get("elements", []):
                if any(v < -16 or v > 32 for v in el["from"] + el["to"]) or any(a > b for a, b in zip(el["from"], el["to"])):
                    problems.append("요소 범위 오류 %s %s" % (f, el))
                for face in el["faces"].values():
                    if any(v < 0 or v > 16 for v in face["uv"]) or not str(face.get("texture", "")).startswith("#"):
                        problems.append("UV 오류 %s %s" % (f, face))
            for r in refs:
                if r.startswith(NS + ":"):
                    kind = "textures" if r in data.get("textures", {}).values() else "models"  # (models/boss 포함)
                    ext = ".png" if kind == "textures" else ".json"
                    p = os.path.join(PACK, "assets", NS, kind, r.split(":", 1)[1] + ext)
                    if not os.path.exists(p):
                        problems.append("참조 없음 %s -> %s" % (f, r))
                elif not r.startswith(vanilla_ok):
                    problems.append("알 수 없는 참조 %s -> %s" % (f, r))
            for p in data.get("providers", []):
                if p.get("type") == "bitmap":
                    if p.get("ascent", 7) > p.get("height", 8):
                        problems.append("글꼴 ascent 가 height 보다 큼 (글꼴 전체가 깨짐) " + f + " " + p["file"])
                    ns, rel = p["file"].split(":", 1)
                    fp = os.path.join(PACK, "assets", ns, "textures", rel)
                    if not os.path.exists(fp) and ns != "minecraft":   # 바닐라 글꼴 이미지는 게임에 내장
                        problems.append("폰트 이미지 없음 " + p["file"])
    if problems:
        raise SystemExit("\n".join(problems))
    print("검증 통과: 모든 모델/텍스처 참조 정상")


if __name__ == "__main__":
    main()
