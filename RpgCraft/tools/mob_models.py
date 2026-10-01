"""일반 몬스터 3D 모델 (v5.9.0) — 보스처럼 작은 정육면체로 깎은 복셀 조각 (아머러스 워크샵 느낌)

mobs.yml 의 커스텀 몬스터마다 모델 하나. 플러그인(MobModelManager)이 몹을 투명하게 하고 이 모델을 태워 움직인다.
 - 몬스터는 한 화면에 여럿 나오므로 보스보다 굵은 복셀(0.8)로 가볍게 깎는다.
 - 만든 뒤 발을 y=0, 키를 16(1블록)에 맞춰 늘이거나 줄인다 (너비가 넘치면 더 줄이고 실제 키를 기록).
 - PAPER CustomModelData 9100+ (보스 9000+ 뒤). 순서 · 키는 플러그인 리소스 mob-models.yml 로 함께 써 준다.
"""
import math
import os

import voxel_lib as VL
from voxel_lib import (Grid, emit, figure, armor, robe, cape, hair, wings, crown, halo, staff, sword, hammer, spear,
                       quad, bow, axe, scythe, tentacle, floaters, hx, tex, rnd, add, sub, mul, lerp, norm)

MOB_VS = 1.0   # v5.10.2 프레임: 0.8 → 1.0 (요소 약 40% 감소)
CMD_BASE = 9100
UNDEAD_BASE = 16000   # v5.10.11 네크로맨서 군단원용 언데드 색 변형 (같은 모델 · 텍스처만 다름)


def undead_color(hexcol):
    """언데드화: 채도를 빼고 창백한 회녹색으로 어둡게, 밝고 진한 색(눈 · 불꽃 · 보석)은 영혼빛 청록으로"""
    import colorsys
    h = hexcol.lstrip("#")
    r, g, b = (int(h[k:k + 2], 16) / 255.0 for k in (0, 2, 4))
    hh, ll, ss = colorsys.rgb_to_hls(r, g, b)
    if max(r, g, b) > 0.78 and ss > 0.55 and ll > 0.35:   # 눈 · 빛나는 부분 → 영혼불
        glow = 0.75 + 0.25 * ll
        return "#%02x%02x%02x" % (int(90 * glow), int(240 * glow), int(255 * glow))
    grey = 0.3 * r + 0.59 * g + 0.11 * b
    mix = 0.72
    r2, g2, b2 = (grey + (c - grey) * (1 - mix) for c in (r, g, b))
    tint = (0.78, 0.95, 0.86)
    dark = 0.82
    out = [min(1.0, c * t * dark + 0.03) for c, t in zip((r2, g2, b2), tint)]
    return "#%02x%02x%02x" % tuple(int(round(c * 255)) for c in out)


# ================================================================== 공통 틀
def skull_face(g, B, bone, eye="1a1a1a", glow=None, jaw=True):
    """해골 얼굴: 눈구멍 · 코구멍 · 이빨"""
    hc, (rx, ry, rz) = B.head, B.head_r
    fz = hc[2] + rz * 0.92
    for sx in (-1, 1):   # 눈구멍 (빛나는 눈이면 그 색) — 굵은 복셀에서도 두 눈이 붙지 않게 한 칸씩
        g.dot(hc[0] + sx * 1.05, hc[1] + 0.4, fz, glow or eye, 9, 0.8)
    g.dot(hc[0], hc[1] - 0.6, fz, eye, 8, 0.6)
    if jaw:
        g.box((hc[0] - 1.2, hc[1] - 1.9, fz - 0.6), (hc[0] + 1.2, hc[1] - 1.3, fz + 0.2), eye, 7)


def ribs(g, B, bone, dark="2a2a2a"):
    s = B.s
    g.ellipsoid((8, 16.5 * s, 8.4), (2.4, 2.6 * s, 1.1), dark, 3)
    for k in range(4):
        y = (15.2 + k * 1.05) * s
        g.tube([(5.6, y, 8.6), (6.6, y + 0.2, 10.0), (9.4, y + 0.2, 10.0), (10.4, y, 8.6)], [0.3] * 4, bone, 5, smooth=True)
    g.tube([(8, 12.0 * s, 9.6), (8, 19.0 * s, 9.9)], [0.35, 0.35], hx(bone, 0.85), 5)


def skeleton(g, bone="e8e4d8", s=1.0, w=0.82, eye="1a1a1a", glow=None, robe_=None, weapon_arm="right", reach=True):
    B = figure(g, s=s, w=w, bulk=0.62, skin=bone, top=hx(bone, 0.9), legs=hx(bone, 0.85), boots=hx(bone, 0.75),
               arms=bone, hands=bone, face=False, robe=robe_, weapon_arm=weapon_arm, reach=reach)
    ribs(g, B, bone)
    skull_face(g, B, bone, eye, glow)
    return B


def hood(g, B, col, depth=2.2, point=True, pri=7):
    hc = B.head
    g.ellipsoid(add(hc, (0, 0.5, -0.4)), (B.head_r[0] + 0.7, B.head_r[1] + 0.7, B.head_r[2] + 0.6),
                lambda x, y, z, d: tex(col, x, y, z, 0.08) if z < hc[2] + depth else None, pri, inner=0.6)
    if point:
        g.cone(add(hc, (0, 2.6, -1.6)), add(hc, (0, 3.6, -4.6)), 1.4, 0.2, col, pri)


def helm(g, B, col, trim, visor="1a1a1a", horns=None, plume=None, crest=False, pri=7):
    hc = B.head
    g.ellipsoid(add(hc, (0, 0.2, 0)), (B.head_r[0] + 0.45, B.head_r[1] + 0.35, B.head_r[2] + 0.45),
                lambda x, y, z, d: trim if abs(y - hc[1] - 1.6) < 0.3 else tex(col, x, y, z, 0.06), pri)
    g.box((hc[0] - 1.8, hc[1] - 0.1, hc[2] + 2.3), (hc[0] + 1.8, hc[1] + 0.6, hc[2] + 3.3), visor, pri + 1)   # 눈 틈
    g.box((hc[0] - 0.3, hc[1] - 2.2, hc[2] + 2.5), (hc[0] + 0.3, hc[1] + 1.8, hc[2] + 3.3), trim, pri + 1)
    if horns:
        for sx in (-1, 1):
            g.tube([(hc[0] + sx * 2.4, hc[1] + 1.2, hc[2]), (hc[0] + sx * 4.0, hc[1] + 2.4, hc[2] - 0.4), (hc[0] + sx * 4.4, hc[1] + 4.6, hc[2] - 0.2)],
                   [0.7, 0.5, 0.12], horns, pri, smooth=True)
    if plume:
        for k in range(6):
            b = (hc[0], hc[1] + 2.6, hc[2] + 1.2 - k * 0.9)
            g.cone(b, add(b, (0, 2.2 - abs(k - 1.5) * 0.3, -1.4)), 0.7, 0.1, plume, pri)
    if crest:
        g.box((hc[0] - 0.25, hc[1] + 2.2, hc[2] - 2.4), (hc[0] + 0.25, hc[1] + 3.6, hc[2] + 2.0), trim, pri)


def wide_hat(g, B, col, band, brim=5.0, top=3.2, pointed=False, pri=7):
    hc = B.head
    by = hc[1] + B.head_r[1] * 0.75
    g.ellipsoid((hc[0], by, hc[2]), (brim, 0.45, brim), lambda x, y, z, d: col if d < 0.8 else hx(col, 0.8), pri)
    if pointed:
        g.tube([(hc[0], by, hc[2]), (hc[0], by + 3.2, hc[2] - 0.2), (hc[0] + 0.6, by + 5.6, hc[2] - 1.4), (hc[0] + 1.8, by + 6.4, hc[2] - 2.8)],
               [2.6, 1.8, 0.9, 0.2], lambda x, y, z, d, f: tex(col, x, y, z, 0.08), pri, smooth=True)
    else:
        g.ellipsoid((hc[0], by + top / 2, hc[2]), (2.7, top / 2, 2.7), lambda x, y, z, d: tex(col, x, y, z, 0.08), pri)
    g.ellipsoid((hc[0], by + 0.6, hc[2]), (2.8, 0.45, 2.8), band, pri + 1, inner=0.4)


def pointy_ears(g, B, skin, L=2.6, pri=7):
    hc = B.head
    for sx in (-1, 1):
        g.cone((hc[0] + sx * 2.2, hc[1] + 0.3, hc[2]), (hc[0] + sx * (2.2 + L), hc[1] + 1.4, hc[2] - 0.6), 0.8, 0.1, skin, pri)


def tusks(g, B, col="f0e6c8", pri=8):
    hc = B.head
    for sx in (-1, 1):
        g.cone((hc[0] + sx * 1.0, hc[1] - 1.6, hc[2] + 2.3), (hc[0] + sx * 1.3, hc[1] - 0.2, hc[2] + 2.9), 0.35, 0.08, col, pri)


def beast_head(g, B, col, snout, eye, size=1.0, pri=4, tusk=None, horns=None, ears=True, mane=None, open_mouth=True):
    """네발짐승 머리: 머리 · 주둥이 · 코 · 눈 · 귀 (+엄니 · 뿔 · 갈기). 반환: 머리 중심"""
    zf = B.front_z
    hc = (8, B.cy + 1.2 * size, zf + 2.2 * size)
    k = size
    g.ellipsoid(hc, (3.6 * k, 3.4 * k, 3.6 * k), lambda x, y, z, d: tex(col, x, y, z), pri)
    g.tube([add(hc, (0, -0.8 * k, 2.2 * k)), add(hc, (0, -1.4 * k, 5.0 * k))], [2.3 * k, 1.9 * k], lambda x, y, z, d, f: tex(snout, x, y, z, 0.06), pri)
    g.ellipsoid(add(hc, (0, -1.2 * k, 5.4 * k)), (1.6 * k, 1.2 * k, 0.5), hx(snout, 0.7), pri + 1)
    if open_mouth:
        g.box(add(hc, (-1.6 * k, -3.0 * k, 2.8 * k)), add(hc, (1.6 * k, -2.4 * k, 5.2 * k)), "3a1a1a", pri + 1)
    for sx in (-1, 1):
        g.dot(hc[0] + sx * 2.3 * k, hc[1] + 0.8 * k, hc[2] + 2.8 * k, eye, pri + 4, 0.8)
        if ears:
            g.slab(add(hc, (sx * 2.2 * k, 2.6 * k, -0.4)), add(hc, (sx * 3.6 * k, 4.6 * k, -1.0)), (0, 0, 1.4 * k), 0.5, hx(col, 0.85), pri)
        if tusk:
            g.tube([add(hc, (sx * 1.8 * k, -2.2 * k, 4.2 * k)), add(hc, (sx * 2.8 * k, -2.0 * k, 5.8 * k)), add(hc, (sx * 2.9 * k, 0.4 * k, 6.6 * k))],
                   [0.6 * k, 0.5 * k, 0.12], tusk, pri + 2, smooth=True)
        if horns:
            g.tube([add(hc, (sx * 2.4 * k, 2.2 * k, 0)), add(hc, (sx * 4.6 * k, 3.0 * k, -1.2 * k)), add(hc, (sx * 5.4 * k, 5.4 * k, 0.2))],
                   [0.9 * k, 0.6 * k, 0.12], horns, pri + 2, smooth=True)
    if mane:
        for i in range(8):
            b = (8, B.back_y + 0.6, zf - 1 - i * 2.0)
            g.cone(b, add(b, (0, 2.2 + (i % 3), -1.2)), 0.8, 0.1, mane, pri + 1)
    return hc


def spider(g, body, body2, leg, eye, big=1.0, abd_mark=None, pri=3, tail=None, claws=None):
    """거미 · 전갈: 머리가슴 · 배 · 다리 8개 (관절 꺾임) · 눈 여럿"""
    k = big
    ceph = (8, 6.5 * k, 11 * k - 3)
    abd = (8, 7.5 * k, 3.5 * k - 3)
    g.ellipsoid(ceph, (4.2 * k, 3.0 * k, 4.2 * k), lambda x, y, z, d: tex(body, x, y, z), pri)
    if not tail:
        g.ellipsoid(abd, (5.4 * k, 4.4 * k, 6.0 * k), lambda x, y, z, d: tex(body2 if int((z - abd[2]) / 1.6) % 2 else body, x, y, z), pri)
        if abd_mark:   # 등의 무늬 (모래시계 모양)
            for t in range(5):
                for dx in ((-0.8, 0, 0.8) if t == 2 else (0,)):
                    g.dot(8 + dx, abd[1] + 4.2 * k - abs(t - 2) * 0.35, abd[2] - 2.4 * k + t * 1.2 * k, abd_mark, pri + 3, 0.5)
    else:   # 전갈 꼬리: 마디가 위로 말려 올라가 독침
        pts = [add(ceph, (0, 0, -3 * k)), (8, 6.5 * k, -3 * k), (8, 9 * k, -8 * k), (8, 15 * k, -9 * k), (8, 19 * k, -5 * k), (8, 18.5 * k, -1.5 * k)]
        g.tube(pts, [3.2 * k, 2.6 * k, 2.0 * k, 1.6 * k, 1.3 * k, 0.9 * k], lambda x, y, z, d, f: body2 if int(f * 3) % 2 else body, pri, smooth=True)
        g.cone((8, 18.5 * k, -1.5 * k), (8, 16.2 * k, 1.0 * k), 0.9 * k, 0.1, tail, pri + 2)
    for sx in (-1, 1):
        for i in range(4):
            a = (i - 1.5) * 0.45
            hip = (8 + sx * 3.2 * k, 6.5 * k, ceph[2] - 1.0 * k + i * 0.9 * k - 1.2 * k)
            knee = (8 + sx * 8.5 * k, 11.5 * k, hip[2] + math.sin(a) * 6 * k)
            foot = (8 + sx * 12.5 * k, 0.4, hip[2] + math.sin(a) * 9.5 * k)
            g.tube([hip, knee], [0.8 * k, 0.65 * k], lambda x, y, z, d, f: tex(leg, x, y, z), pri)
            g.tube([knee, foot], [0.65 * k, 0.3 * k], lambda x, y, z, d, f: hx(leg, 0.8) if f > 0.8 else tex(leg, x, y, z), pri)
            g.sphere(knee, 0.9 * k, hx(leg, 1.15), pri + 1)
    for (dx, dy, r) in ((-1.2, 0.8, 0.55), (1.2, 0.8, 0.55), (-0.5, 1.4, 0.4), (0.5, 1.4, 0.4), (-2.0, 0.2, 0.4), (2.0, 0.2, 0.4)):
        g.dot(8 + dx * k, ceph[1] + dy * k, ceph[2] + 4.0 * k, eye, pri + 5, 0.5)
    for sx in (-1, 1):   # 송곳니 / 집게
        if claws:
            base = (8 + sx * 2.4 * k, 5.5 * k, ceph[2] + 3.5 * k)
            g.tube([base, add(base, (sx * 2.0 * k, 0, 3.0 * k)), add(base, (sx * 1.0 * k, 0, 6.0 * k))], [1.0 * k, 0.9 * k, 1.3 * k], claws, pri + 1, smooth=True)
            g.cone(add(base, (sx * 1.0 * k, 0, 6.0 * k)), add(base, (sx * -0.2 * k, 0, 8.4 * k)), 0.8 * k, 0.1, claws, pri + 2)
            g.cone(add(base, (sx * 1.4 * k, 0, 6.0 * k)), add(base, (sx * 1.8 * k, 0, 8.2 * k)), 0.6 * k, 0.1, hx(claws, 0.8), pri + 2)
        else:
            g.cone((8 + sx * 0.9 * k, 5.4 * k, ceph[2] + 3.6 * k), (8 + sx * 0.6 * k, 3.2 * k, ceph[2] + 4.4 * k), 0.5 * k, 0.1, "e8e0d0", pri + 2)


def slime(g, col, core, eye="1a1a1a", r=7.0, extra=None, pri=3):
    """젤리 몸 (밑이 평평한 반구) · 속 핵 · 얼굴 · 반짝임"""
    c = (8, r * 0.9, 8)
    g.ellipsoid(c, (r, r * 0.9, r), lambda x, y, z, d: None if y < 0 else (hx(col, 1.18) if d > 0.9 and y > c[1] + r * 0.4 else tex(col, x, y, z, 0.07)), pri)
    g.sphere(add(c, (0, -0.8, 0)), r * 0.4, core, pri - 1)
    for sx in (-1, 1):
        g.box((8 + sx * 2.2 - 0.9, c[1] + 0.4, 8 + r * 0.9 - 0.8), (8 + sx * 2.2 + 0.9, c[1] + 2.4, 8 + r * 0.9 + 0.5), eye, pri + 4)
        g.dot(8 + sx * 2.2 + 0.4, c[1] + 2.0, 8 + r * 0.95 + 0.3, "ffffff", pri + 5, 0.6)
    g.box((8 - 1.4, c[1] - 1.6, 8 + r * 0.9 - 0.8), (8 + 1.4, c[1] - 0.9, 8 + r * 0.9 + 0.5), hx(col, 0.45), pri + 4)
    for k in range(3):   # 윤기
        g.dot(8 - 3 + k * 0.9, c[1] + r * 0.6 - k * 0.3, 8 + r * 0.55, "ffffff", pri + 3, 0.7)
    return c


def flyer(g, body, wing, belly, eye, span=18.0, tail=True, head_kind="beak", pri=3, rim=None):
    """날짐승 (팬텀 · 하피 · 폭풍새): 길쭉한 몸 · 박쥐/깃 날개 · 꼬리"""
    spine = [(8, 8, -6), (8, 8.5, 0), (8, 9, 6), (8, 9.5, 11)]
    g.tube(spine, [1.2, 2.8, 3.2, 2.4], lambda x, y, z, d, f: tex(belly if y < 8 else body, x, y, z), pri, smooth=True)
    hc = (8, 9.8, 13.2)
    g.ellipsoid(hc, (2.4, 2.2, 2.6), lambda x, y, z, d: tex(body, x, y, z), pri)
    for sx in (-1, 1):
        g.dot(8 + sx * 1.5, 10.4, 15.1, eye, pri + 5, 0.9)
    if head_kind == "beak":
        g.cone((8, 9.4, 15.2), (8, 8.4, 18.4), 1.0, 0.1, "e0b040", pri + 1)
    else:
        g.box((6.6, 8.2, 14.8), (9.4, 8.8, 15.8), "2a0a0a", pri + 1)
        for x in (6.9, 7.7, 8.5, 9.1):
            g.dot(x, 8.4, 15.8, "f0f0f0", pri + 2, 0.4)
    g.kind = "flyer"
    for sx in (-1, 1):
        root = (8 + sx * 1.6, 9.6, 5)
        elbow = (8 + sx * span * 0.45, 12, 4)
        tip = (8 + sx * span, 10.5, 0)
        wname = "wing_l" if sx < 0 else "wing_r"   # v5.9.1 날갯짓
        g.rig[wname] = root
        with g.parting(wname):
            g.tube([root, elbow, tip], [0.8, 0.6, 0.2], hx(body, 0.8), pri + 1, smooth=True)
            g.triangle(root, elbow, (8 + sx * 2, 9, -2), lambda p, w: wing, pri, scallop=0.0)
            g.triangle(elbow, tip, (8 + sx * span * 0.6, 9, -4), lambda p, w: rim if (rim and w < 0.12) else wing, pri, scallop=0.15)
            g.triangle(elbow, (8 + sx * span * 0.6, 9, -4), (8 + sx * 2, 9, -2), wing, pri)
    if tail:
        g.triangle((8, 8, -6), (5, 7.8, -11), (11, 7.8, -11), wing, pri, scallop=0.2)
    return hc


def bug(g, col, col2, eye, n=5, r=2.4, pri=3, legs=True, antenna=True):
    """마디 벌레 (좀벌레 · 엔더마이트)"""
    for i in range(n):
        z = 12 - i * 3.2
        rr = r * (1.0 - abs(i - 1) * 0.12)
        g.ellipsoid((8, rr * 0.9, z), (rr * 1.3, rr * 0.9, 1.9), lambda x, y, z_, d, i=i: tex(col if i % 2 == 0 else col2, x, y, z_), pri)
        if legs and 0 < i < n - 1:
            for sx in (-1, 1):
                g.tube([(8 + sx * rr * 1.1, 0.8, z), (8 + sx * (rr * 1.1 + 1.8), 0.2, z + 0.6)], [0.3, 0.2], hx(col2, 0.7), pri)
    for sx in (-1, 1):
        g.dot(8 + sx * 1.1, r * 1.1, 13.6, eye, pri + 4, 0.7)
        if antenna:
            g.tube([(8 + sx * 0.8, r * 1.4, 13.4), (8 + sx * 2.0, r * 2.6, 16), (8 + sx * 3.0, r * 2.4, 17.6)], [0.2, 0.15, 0.1], col2, pri, smooth=True)
    g.tube([(8, r * 0.6, 12 - (n - 1) * 3.2 - 1), (8, r * 0.4, 12 - (n - 1) * 3.2 - 3.4)], [0.8, 0.2], col2, pri)


def flame_body(g, C, height=18.0, width=5.0, pri=3, base_y=4.0):
    """불꽃 몸: 아래는 가늘고 위로 부푼 불꽃 덩어리 + 솟는 불꽃 혀"""
    def fire(x, y, z, d):
        t = d + rnd(math.floor(x / VL.VS), math.floor(y / VL.VS), math.floor(z / VL.VS)) * 0.25
        return C[max(0, min(len(C) - 1, int(t * (len(C) - 0.2))))]
    g.cone((8, base_y - 2, 8), (8, base_y + height * 0.35, 8), 0.8, width * 0.6, lambda x, y, z, f: fire(x, y, z, 0.6), pri)
    c = (8, base_y + height * 0.55, 8)
    g.ellipsoid(c, (width, height * 0.32, width * 0.9), lambda x, y, z, d: fire(x, y, z, d), pri)
    for k in range(9):
        a = k * 2.399
        r = 0.8 + (k % 3) * 1.1
        b = (8 + math.cos(a) * r, c[1] + height * 0.2, 8 + math.sin(a) * r)
        tip = add(b, (math.cos(a) * 0.9, height * 0.25 + (k % 4) * 1.2, math.sin(a) * 0.8 - 0.6))
        g.tube([b, lerp(b, tip, 0.5), tip], [1.3, 0.8, 0.1], lambda x, y, z, d, f: C[min(len(C) - 1, int(f * 1.6))], pri + 1, smooth=True)
    return c


def rods(g, c, col, n=6, r=6.0, h=2.0, pri=5, glow=None):
    """주위를 도는 막대 (블레이즈 막대)"""
    for k in range(n):
        a = k * 2 * math.pi / n
        y = c[1] + (h if k % 2 else -h)
        b = (8 + math.cos(a) * r, y - 1.8, 8 + math.sin(a) * r)
        g.tube([b, add(b, (0, 3.6, 0))], [0.7, 0.7], lambda x, y_, z, d, f: (glow if glow and abs(f - 0.5) < 0.15 else tex(col, x, y_, z, 0.1)), pri)


# ================================================================== 사람형
def goblin(g):
    SKIN = "6aa84a"
    B = figure(g, s=0.72, w=1.05, bulk=1.05, skin=SKIN, top="7a5a32", legs="5a4028", boots="3a2a18", arms=SKIN, eye="ffd23f")
    pointy_ears(g, B, SKIN, L=3.2)
    hc = B.head
    g.cone(add(hc, (0, -0.2, 2.2)), add(hc, (0, -0.8, 4.2)), 0.8, 0.2, hx(SKIN, 0.9), 8)   # 큰 코
    g.ellipsoid(add(hc, (0, 1.2, 0)), (2.3, 1.2, 2.3), lambda x, y, z, d: "e0b030" if y > hc[1] + 1.0 else None, 8)   # 금 투구
    g.ellipsoid(add(hc, (0, 1.2, 0)), (2.5, 0.3, 2.5), "b08020", 8)
    tusks(g, B)
    sword(g, B.hand_r, 7, "8a6a3a", "c8a870", "5a3a1a", width=1.0)
    g.ellipsoid((9.6, 9.5, 5.2), (1.6, 1.8, 1.2), "8a6a3a", 5)   # 훔친 자루
    g.dot(9.2, 11.3, 5.6, "ffd23f", 7, 0.7)


def rotten_farmer(g):
    SKIN = "7a9a5a"
    B = figure(g, s=1.0, w=1.0, skin=SKIN, top="5a6a8a", legs="3a4a6a", boots="4a3a2a", arms="b8a888", hands=SKIN, eye="c83a2a")
    g.box((5.2, 11, 10.3), (10.8, 18.5, 10.9), lambda x, y, z: "4a5a7a" if abs(x - 8) < 2.2 else None, 5)   # 멜빵바지
    for sx in (-1, 1):
        g.box((8 + sx * 1.8 - 0.3, 16, 10.6), (8 + sx * 1.8 + 0.3, 20, 11.0), "4a5a7a", 6)
    wide_hat(g, B, "d8c070", "8a2a1a", brim=5.4, top=2.4)
    for k in range(4):
        g.dot(8 + (k - 1.5) * 1.3, 21.5, 11.4, "3a4a2a", 9, 0.5)   # 썩은 자국
    top = add(B.hand_r, (0, 10, 1.2))
    with g.parting("arm_r"):
        g.tube([add(B.hand_r, (0, -6, -0.8)), top], [0.35, 0.35], "6a4a2a", 6)
        g.slab(top, add(top, (0, -0.6, 3.2)), (0.5, 0, 0), 0.4, "8a8f96", 7)   # 괭이


def bandit(g, cloak, mask, trim, scarf=None, quiver=True):
    B = figure(g, s=1.0, w=1.0, skin="c89868", top="4a3a2a", legs="2a2420", boots="1a1612", arms="4a3a2a", eye="2a1a1a")
    hood(g, B, cloak)
    g.ellipsoid(add(B.head, (0, -1.0, 1.0)), (2.5, 1.5, 1.9), lambda x, y, z, d: mask if z > B.head[2] + 1.3 else None, 8, inner=0.4)
    cape(g, B, cloak, trim, length=6.0, width=4.2, ragged=True)
    g.ellipsoid((8, 11.9, 8), (3.4, 0.7, 2.45), "3a2a1a", 5, inner=0.7)
    g.tube([(4.6, 19.4, 10.4), (11.4, 12.4, 10.6)], [0.4, 0.4], "3a2a1a", 6)
    if scarf:
        g.ellipsoid((8, 19.6, 8.2), (2.6, 0.8, 2.4), scarf, 7)
        g.slab((9.4, 19.4, 10.2), (10.4, 16.6, 10.4), (1.0, 0, 0), 0.3, scarf, 7)
    if quiver:
        g.tube([(6.4, 12.5, 4.8), (10.0, 20.0, 4.6)], [1.0, 0.9], "5a3a1a", 5)
        for k in range(4):
            g.dot(9.6 + k * 0.3, 20.8, 4.6 + (k - 1.5) * 0.4, "e8e0d0", 6, 0.5)
    return B


def forest_bandit(g): bandit(g, "2e5a2a", "3a4a2a", "5a3a1a")
def day_bandit(g): bandit(g, "6a4a2a", "a8281e", "3a2a1a")
def canyon_raider(g): bandit(g, "a8583a", "3a2a1a", "d8a060", scarf="c83a2a")


def skeleton_captain(g):
    B = skeleton(g, glow="ff5a3a")
    wide_hat(g, B, "2a2a3a", "c8a030", brim=4.2, top=1.6)   # 대장 모자
    g.cone(add(B.head, (2.4, 3.2, 0)), add(B.head, (4.6, 5.6, -1)), 0.5, 0.1, "e83a3a", 9)   # 깃털
    cape(g, B, "6a1a1a", "c8a030", length=7.0, width=4.0, ragged=True)
    g.tube([(4.6, 19.4, 9.8), (11.4, 12.4, 10.0)], [0.35, 0.35], "5a3a1a", 6)


def bone_marksman(g):
    B = skeleton(g, glow="7fe8ff")
    hood(g, B, "3a3a44")
    g.tube([(6.4, 12.5, 4.8), (10.0, 20.0, 4.6)], [1.0, 0.9], "4a3a2a", 5)
    for k in range(5):
        g.dot(9.5 + k * 0.3, 20.8, 4.6 + (k - 2) * 0.4, "7fe8ff" if k == 2 else "e8e4d8", 6, 0.5)


def lich_(g, robe_c, trim, glow, crown_c, big=1.0, gem=None):
    B = skeleton(g, s=big, glow=glow, robe_=True)
    robe(g, B, robe_c, hx(robe_c, 0.8), trim, flare=5.8, hem_ragged=True)
    g.ellipsoid((8, 19.4 * big, 8), (4.8, 1.6, 3.2), lambda x, y, z, d: hx(robe_c, 0.8) if d > 0.5 else None, 5)
    hood(g, B, hx(robe_c, 0.9), depth=1.6, point=False)
    crown(g, add(B.head, (0, 2.4, 0)), 2.5, crown_c, gem=gem or glow, spikes=6, height=1.8)
    top = staff(g, B.hand_r, 26 * big, "3a2a3a", crown_c)
    g.sphere(add(top, (0, 1.4, 0.3)), 1.3, lambda x, y, z, d: "ffffff" if d < 0.25 else glow, 7)
    floaters(g, [(-3, 16 * big, 6), (19, 20 * big, 8)], glow, r=0.7)


def lich(g): lich_(g, "3a1a4a", "c8a030", "a04aff", "e0b030")
def ancient_lich(g): lich_(g, "1a2a3a", "7fe8ff", "3fe8ff", "e8f0ff", big=1.1, gem="3fe8ff")


def witch_(g, hat, robe_c, skin, glow, moss=False):
    B = figure(g, s=0.95, w=0.95, skin=skin, top=robe_c, legs=hx(robe_c, 0.8), arms=robe_c, robe=True, eye=glow)
    robe(g, B, robe_c, hx(robe_c, 0.8), hx(robe_c, 1.3), flare=5.4, hem_ragged=True)
    hair(g, B, "3a3a3a", length=9, width=2.4)
    g.cone(add(B.head, (0, -0.2, 2.3)), add(B.head, (0, -1.2, 4.0)), 0.6, 0.15, hx(skin, 0.9), 8)
    g.dot(B.head[0] + 0.6, B.head[1] - 0.8, B.head[2] + 3.0, hx(skin, 0.7), 9, 0.5)
    wide_hat(g, B, hat, glow, brim=5.4, pointed=True)
    hand = B.hand_r   # 물약 병
    with g.parting("arm_r"):
        g.sphere(add(hand, (0, 1.4, 0.6)), 1.1, lambda x, y, z, d: glow if y < hand[1] + 1.6 else "d8f0f8", 7)
        g.box(add(hand, (-0.35, 2.4, 0.25)), add(hand, (0.35, 3.2, 0.95)), "8a6a3a", 7)
    if moss:
        for k in range(8):
            a = k * 0.8
            g.ellipsoid((8 + math.cos(a) * 4.8, 1 + (k % 4) * 2, 8 + math.sin(a) * 4.0), (0.9, 0.6, 0.8), "5a7a3a", 4)


def swamp_hag(g): witch_(g, "2a3a24", "3a4a2a", "8aa870", "7dff6a", moss=True)
def bog_witch(g): witch_(g, "3a1a4a", "4a2a5a", "a8b890", "c060ff", moss=True)


def mummy_(g, band, band2, eye, big=1.0, dual=False, hood_c=None, gold=None):
    B = figure(g, s=big, w=1.0 * (1.2 if big > 1.2 else 1), bulk=1.0 + (big - 1) * 0.4, skin=band, top=band, legs=band2, boots=band2, arms=band, face=False)
    for k in range(int(20 * big)):   # 감긴 붕대 줄
        y = 2 + k * 1.1
        if y > 21 * big:
            break
        g.ellipsoid((8, y, 8), (3.4 * big if y > 11 * big else 2.3 * big, 0.3, 2.4 * big), lambda x, y_, z, d, k=k: band2 if k % 2 else None, 3, inner=0.7)
    hc = B.head
    for sx in (-1, 1):
        g.box((hc[0] + sx * 1.0 - 0.6, hc[1], hc[2] + 2.0), (hc[0] + sx * 1.0 + 0.6, hc[1] + 0.8, hc[2] + 2.9), "1a1410", 8)
        g.dot(hc[0] + sx * 1.0, hc[1] + 0.4, hc[2] + 2.9, eye, 9, 0.6)
    for k in range(5):   # 풀린 붕대 자락
        a = k * 1.3
        p = (8 + math.cos(a) * 3.0, 12 + k * 1.6, 8 + math.sin(a) * 2.2)
        g.slab(p, add(p, (math.cos(a) * 1.2, -3.2, math.sin(a) * 1.2)), (0.8, 0, 0.3), 0.3, band, 4)
    if hood_c:
        hood(g, B, hood_c)
    if gold:
        g.ellipsoid((8, 17.8 * big, 8.3), (4.3 * big, 2.2, 2.8 * big), lambda x, y, z, d: gold if int(y * 1.4) % 2 else "2a4aa8", 5, inner=0.65)
        g.ellipsoid(add(hc, (0, 0.8, -0.2)), (3.0 * big, 2.6 * big, 3.0 * big), lambda x, y, z, d: (gold if int(y * 1.4) % 2 else "2a4aa8") if z < hc[2] + 1.6 else None, 7, inner=0.6)
    if dual:
        for sx in (-1, 1):
            hand = B.hand_r if sx == 1 else B.hand_l
            with g.parting("arm_r" if sx == 1 else "arm_l"):
                g.tube([hand, add(hand, (sx * 0.8, 3.2, 1.8)), add(hand, (sx * 2.0, 5.4, 1.4))], [0.5, 0.4, 0.1], "d8d0c0", 7, smooth=True)
    return B


def mummy(g): mummy_(g, "d8ccb0", "b0a488", "7dff6a")
def sand_stalker(g): mummy_(g, "c8a878", "a08858", "ffb030", dual=True, hood_c="a8783a")
def dune_colossus(g):
    B = mummy_(g, "c8b088", "a89068", "ffb030", big=1.35, gold="e0b030")
    hammer(g, B.hand_r, 14, 2.6, "6a4a2a", "c8a870", "e0b030")


def sand_wraith(g):
    SAND, SAND2, EYE = "d8b878", "b8985a", "ffb030"
    for k in range(14):   # 모래 소용돌이 아래 몸
        a = k * 0.9
        r = 1.0 + k * 0.28
        g.sphere((8 + math.cos(a) * r, 1 + k * 0.9, 8 + math.sin(a) * r), 1.5 + k * 0.1, SAND if k % 2 else SAND2, 3)
    B = figure(g, s=1.0, w=1.0, skin=SAND2, top=SAND, legs=SAND, arms=SAND, robe=True, face=False)
    hood(g, B, "8a6a3a", depth=2.6)
    g.ellipsoid(add(B.head, (0, 0, 0.6)), (2.2, 2.4, 2.0), "1a1410", 6)
    for sx in (-1, 1):
        g.dot(B.head[0] + sx * 0.9, B.head[1] + 0.3, B.head[2] + 2.5, EYE, 9, 0.7)
    scythe(g, B.hand_r, 22, "5a3a1a", "c8ccd2", "f0f0f0", reach=6)


def drowned_pirate(g):
    SKIN = "5a8a8a"
    B = figure(g, s=1.0, w=1.0, skin=SKIN, top="7a2a2a", legs="3a3a4a", boots="2a2020", arms="d8d0c0", hands=SKIN, eye="7fe8ff")
    hc = B.head
    by = hc[1] + 2.2
    g.ellipsoid((hc[0], by, hc[2]), (4.2, 0.5, 3.4), "1a1a24", 7)   # 삼각 모자
    g.ellipsoid((hc[0], by + 1.2, hc[2]), (2.7, 1.4, 2.6), "1a1a24", 7)
    for a in (0, 2.1, 4.2):
        g.cone((hc[0], by, hc[2]), (hc[0] + math.sin(a) * 4.2, by + 1.0, hc[2] + math.cos(a) * 3.8), 1.0, 0.3, "1a1a24", 7)
    g.box((hc[0] - 0.6, by + 0.9, hc[2] + 2.4), (hc[0] + 0.6, by + 1.8, hc[2] + 2.8), "e8e0d0", 8)   # 해골 문양
    g.box((hc[0] + 0.3, hc[1] + 0.1, hc[2] + 2.3), (hc[0] + 1.7, hc[1] + 0.8, hc[2] + 2.8), "1a1a1a", 9)   # 안대
    for k in range(6):   # 해초 · 따개비
        p = (8 + (rnd(k, 1) - 0.5) * 7, 6 + rnd(k, 2) * 14, 10.4)
        g.tube([p, add(p, (0.4, -2.4, 0.2))], [0.35, 0.2], "3a7a3a", 6)
    g.ellipsoid((8, 11.9, 8), (3.4, 0.7, 2.45), "c8a030", 5, inner=0.7)
    pts = [add(B.hand_r, (0, 1, 0.3)), add(B.hand_r, (0.2, 4.4, 1.6)), add(B.hand_r, (1.2, 7.4, 1.4))]   # 커틀러스
    with g.parting("arm_r"):
        g.tube(pts, [0.7, 0.6, 0.15], "c8ccd2", 7, smooth=True, squash=(0.6, 1))
        g.slab(add(B.hand_r, (0, 0.6, 0.2)), add(B.hand_r, (0, 1.0, 0.3)), (2.0, 0, 0), 0.4, "c8a030", 8)


def frost_wraith(g):
    B = skeleton(g, bone="d8f0ff", glow="3fb8ff", robe_=True)
    robe(g, B, "8ab8e8", "5a88c8", "e8fbff", flare=5.2, hem_ragged=True)
    hood(g, B, "6a98d8")
    for sx in (-1, 1):
        hand = B.hand_r if sx == 1 else B.hand_l
        for t in range(3):
            g.cone(hand, add(hand, (sx * 0.3 + (t - 1) * 0.5, -1.8, 1.2)), 0.3, 0.05, "e8fbff", 7)
    for (x, y, z) in [(-2, 18, 6), (18, 22, 8), (-1, 8, 10)]:
        g.cone((x, y + 1.4, z), (x, y - 1.8, z), 0.6, 0.05, "7fe8ff", 6)


def frost_archer(g):
    B = skeleton(g, bone="d8f0ff", glow="3fb8ff")
    hood(g, B, "e8f4ff")
    g.ellipsoid((8, 19.4, 8), (4.6, 1.4, 3.0), "c8e0f0", 6)   # 털 깃
    g.tube([(6.4, 12.5, 4.8), (10.0, 20.0, 4.6)], [1.0, 0.9], "5a88c8", 5)
    for k in range(4):
        g.cone((9.6 + k * 0.3, 20.4, 4.6 + (k - 1.5) * 0.4), (9.6 + k * 0.3, 22.2, 4.6 + (k - 1.5) * 0.4), 0.3, 0.05, "7fe8ff", 6)


def frost_titan(g):
    ICE, ICE2, DEEP = "9fd8ff", "e8fbff", "4a88c8"
    B = figure(g, s=1.3, w=1.3, bulk=1.2, skin="c8e8f8", top=ICE, legs=DEEP, boots=DEEP, arms=ICE, eye="3fb8ff")
    armor(g, B, ICE, DEEP, gem="3fe8ff", spikes=2)
    for k in range(9):   # 어깨 · 등 얼음 결정
        a = (k - 4) * 0.35
        b = (8 + math.sin(a) * 5.5, 25 + math.cos(a) * 1.5, 5.4)
        g.cone(b, add(b, (math.sin(a) * 1.4, 3.0 + (k % 3) * 1.4, -1.2)), 0.9, 0.05, ICE2 if k % 2 else ICE, 6)
    g.ellipsoid(add(B.head, (0, -2.4, 2.2)), (2.2, 1.8, 1.0), ICE2, 8)   # 서리 수염
    hammer(g, B.hand_r, 16, 2.6, DEEP, ICE, ICE2)


def orc_(g, skin, armor_c, trim, weapon="axe", big=1.0, helmet=None, war_paint=None):
    B = figure(g, s=1.05 * big, w=1.2 * big, bulk=1.15, skin=skin, top=armor_c, legs="4a3a2a", boots="2a2018", arms=skin, eye="ff3a2a")
    tusks(g, B)
    pointy_ears(g, B, skin, L=1.8)
    g.ellipsoid((8, 17.6 * B.s, 8.3), (4.4 * B.w, 2.6, 2.7), lambda x, y, z, d: trim if abs(x - 8 - (y - 17.6 * B.s)) < 0.5 else tex(armor_c, x, y, z), 5, inner=0.6)
    for sx in (-1, 1):   # 뼈 견갑
        sh = B.shoulder_r if sx == 1 else B.shoulder_l
        g.ellipsoid(add(sh, (sx * 0.4, 1.0, 0)), (2.4, 1.2, 2.2), lambda x, y, z, d: tex(armor_c, x, y, z), 6, inner=0.5)
        for t in range(2):
            b = add(sh, (sx * (0.6 + t), 1.8, -0.4 + t))
            g.cone(b, add(b, (sx * 0.6, 1.8, 0)), 0.45, 0.1, "e8e0d0", 7)
    if war_paint:
        g.box(add(B.head, (-2.0, 0.9, 2.1)), add(B.head, (2.0, 1.3, 2.7)), war_paint, 8)
    if helmet:
        helm(g, B, helmet, trim, horns="e8e0d0")
    else:
        g.ellipsoid(add(B.head, (0, 2.0, -0.6)), (1.0, 1.4, 2.4), "2a1a10", 7)   # 상투
    if weapon == "axe":
        axe(g, B.hand_r, 14 * big, "5a3a1a", "8a8f96", "d8dce0", size=3.0 * big)
    else:
        hammer(g, B.hand_r, 13 * big, 2.2, "5a3a1a", "6a6f78", trim)
    return B


def orc_warrior(g): orc_(g, "5a8a3a", "6a4a2a", "8a8f96", war_paint="c83a2a")
def piglin_berserker(g):
    B = figure(g, s=1.05, w=1.25, bulk=1.2, skin="e8a0a0", top="4a3a2a", legs="3a2a20", boots="2a1a10", arms="e8a0a0", eye="ff3a2a")
    hc = B.head
    g.ellipsoid(add(hc, (0, -0.6, 2.4)), (1.6, 1.2, 1.0), "d88888", 8)   # 돼지 코
    for sx in (-1, 1):
        g.dot(hc[0] + sx * 0.6, hc[1] - 0.6, hc[2] + 3.4, "6a3a3a", 9, 0.5)
        g.slab(add(hc, (sx * 2.2, 1.4, 0)), add(hc, (sx * 4.2, 0.2, -0.4)), (0, 0, 1.4), 0.4, "d88888", 7)
    tusks(g, B, "f0d860")
    g.ellipsoid((8, 17.6, 8.3), (4.8, 2.6, 2.8), lambda x, y, z, d: tex("e0b030" if int(y) % 3 == 0 else "3a2a20", x, y, z), 5, inner=0.6)
    axe(g, B.hand_r, 16, "3a2a1a", "e0b030", "f8e8a0", size=3.4)


def raider_(g, hair_c, armor_c, trim, weapon):
    B = figure(g, s=1.0, w=1.1, bulk=1.1, skin="d8a878", top=armor_c, legs="4a3a2a", boots="2a2018", arms="d8a878", eye="3a2a1a")
    hair(g, B, hair_c, length=8, width=2.4)
    g.ellipsoid(add(B.head, (0, -2.2, 1.9)), (2.2, 1.8, 1.0), hair_c, 8)   # 수염
    g.ellipsoid((8, 19.4, 8.0), (4.8, 1.4, 3.2), lambda x, y, z, d: tex("8a7a6a", x, y, z, 0.18), 6)   # 모피 깃
    g.ellipsoid((8, 17.4, 8.3), (4.3, 2.6, 2.7), lambda x, y, z, d: tex(armor_c, x, y, z), 5, inner=0.6)
    g.tube([(4.6, 19.4, 10.4), (11.4, 12.4, 10.6)], [0.4, 0.4], trim, 6)
    if weapon == "axe":
        axe(g, B.hand_r, 13, "5a3a1a", "8a8f96", "d8dce0", double=False, size=3.2)
    else:
        sword(g, B.hand_r, 12, "b8bcc4", "e8ecf0", trim)
        g.slab(add(B.hand_l, (-1.4, 3.6, 0)), add(B.hand_l, (-1.4, -3.8, 0.3)), (0, 0, 5.0), 0.8,
               lambda x, y, z, u, v: trim if abs(v) > 0.85 or u < 0.05 or u > 0.95 else "7a2a1a", 6)
    return B


def day_raider(g): raider_(g, "a8582a", "5a4a3a", "3a2a1a", "axe")
def day_mercenary(g):
    B = raider_(g, "3a2a1a", "8a8f96", "c8a030", "sword")
    armor(g, B, "8a8f96", "c8a030", gem=None, belt=False, greaves=False, tassets=False)


def knight_(g, plate, trim, gem, cape_c, weapon="sword", horns=None, plume=None, big=1.0, skull=False, flame=None):
    if skull:
        B = skeleton(g, bone="3a3a3a", s=1.05 * big, w=0.95 * big, glow=gem)
    else:
        B = figure(g, s=1.05 * big, w=1.1 * big, bulk=1.05, skin="1a1a1a", top=plate, legs=plate, boots=hx(plate, 0.8), arms=plate, hands=hx(plate, 0.7), head=False)
    armor(g, B, plate, trim, gem=gem, spikes=2 if horns else 0)
    cape(g, B, cape_c, trim, length=2.0, width=4.4 * big, ragged=True)
    if not skull:
        helm(g, B, plate, trim, visor=gem, horns=horns, plume=plume)
    elif horns:
        helm(g, B, plate, trim, visor="1a1a1a", horns=horns)
    if flame:   # 불타는 해골 머리 (공포의 기수)
        for k in range(7):
            a = k * 0.9
            b = add(B.head, (math.cos(a) * 1.4, 2.4, math.sin(a) * 1.2 - 0.6))
            g.cone(b, add(b, (0, 2.6 + (k % 3), -1.0)), 0.8, 0.1, flame if k % 2 else "ffd84a", 8)
    if weapon == "sword":
        sword(g, B.hand_r, 14 * big, hx(plate, 1.4), gem, trim, width=1.5)
    elif weapon == "greatsword":
        sword(g, B.hand_r, 18 * big, "2a2a34", gem, trim, width=2.2, tilt=(0.1, 1, 0.3))
    elif weapon == "axe":
        axe(g, B.hand_r, 16 * big, "2a1a14", "3a3438", gem, size=3.4)
    elif weapon == "spear":
        spear(g, B.hand_r, 26 * big, "2a2a2a", gem)
    elif weapon == "mace":
        hammer(g, B.hand_r, 13 * big, 1.8, "2a2a2a", plate, trim)
    return B


def corrupted_knight(g): knight_(g, "3a3444", "7a2a8a", "c060ff", "3a1040", weapon="greatsword", horns="2a2a34")
def dread_rider(g): knight_(g, "2a2424", "8a1a0e", "ff5a1a", "3a0808", weapon="spear", skull=True, flame="ff7a1f")
def day_knight(g): knight_(g, "4a4a54", "a8281e", "ff3a2a", "5a1010", weapon="sword", plume="a8281e")
def bone_knight(g): knight_(g, "5a5a60", "8a8f96", "ff5a3a", "3a2a2a", weapon="sword", skull=True)
def void_knight(g): knight_(g, "2a1a3a", "8a60d8", "c060ff", "1a0a2a", weapon="greatsword", skull=True, horns="1a1a24")
def abyss_king(g):
    B = knight_(g, "1a1424", "c8a030", "ff2a6a", "3a0620", weapon="greatsword", skull=True, horns="1a1a24", big=1.15)
    crown(g, add(B.head, (0, 3.0, 0)), 2.5, "e0b030", gem="ff2a6a", spikes=7, height=2.0)
def treasure_guardian(g):
    B = knight_(g, "c8a030", "8a6a1a", "3fe8ff", "2a4aa8", weapon="sword", skull=True)
    for k in range(5):
        g.dot(6 + k, 11.2, 11.0, "ffd23f", 8, 0.6)
def crypt_knight(g): knight_(g, "7a7f88", "4a4f58", "7dff6a", "2a3a2a", weapon="mace", skull=True)
def grave_knight(g): knight_(g, "3a3a40", "6a6f78", "7fe8ff", "1a2030", weapon="axe", skull=True, horns="3a3a40", big=1.1)


def caster_(g, robe_c, trim, glow, skin="c8b0a0", orc=False, hood_c=None, bolt=False, skull_staff=False):
    B = figure(g, s=1.0, w=0.95, skin="5a8a3a" if orc else skin, top=robe_c, legs=hx(robe_c, 0.8), arms=robe_c, robe=True, eye=glow)
    robe(g, B, robe_c, hx(robe_c, 0.8), trim, flare=5.6)
    g.ellipsoid((8, 19.4, 8), (4.6, 1.4, 3.0), lambda x, y, z, d: trim if d > 0.6 else None, 5)
    if orc:
        tusks(g, B)
        pointy_ears(g, B, "5a8a3a", L=1.6)
        for k in range(6):   # 깃털 머리 장식
            b = add(B.head, ((k - 2.5) * 0.8, 2.2, -1.4))
            g.slab(b, add(b, ((k - 2.5) * 0.4, 3.0, -0.8)), (0.6, 0, 0), 0.3, glow if k % 2 else "c83a2a", 8)
    else:
        hood(g, B, hood_c or hx(robe_c, 0.9))
    top = staff(g, B.hand_r, 27, "4a3a2a", trim)
    if skull_staff:
        g.ellipsoid(add(top, (0, 1.2, 0.3)), (1.3, 1.3, 1.2), "e8e4d8", 7)
        for sx in (-1, 1):
            g.dot(top[0] + sx * 0.5, top[1] + 1.3, top[2] + 1.4, glow, 8, 0.5)
    else:
        g.sphere(add(top, (0, 1.3, 0.3)), 1.2, lambda x, y, z, d: "ffffff" if d < 0.25 else glow, 7)
    if bolt:
        p = add(top, (0, 2.6, 0.3))
        for k in range(4):
            q = add(p, ((0.8 if k % 2 == 0 else -0.8), 1.1, 0))
            g.tube([p, q], [0.35, 0.25], glow, 7)
            p = q
    with g.parting("arm_l"):
        g.sphere(add(B.hand_l, (0, 1.2, 0.8)), 0.9, lambda x, y, z, d: "ffffff" if d < 0.3 else glow, 7)   # 손의 마력 구
    return B


def orc_shaman(g): caster_(g, "6a4a2a", "c8a030", "7dff6a", orc=True, skull_staff=True)
def abyss_caster(g): caster_(g, "1a1030", "8a60d8", "c060ff", hood_c="120a24")
def day_warlock(g): caster_(g, "3a1a3a", "a060c0", "ff5aff", hood_c="2a1030", skull_staff=True)
def storm_caller(g): caster_(g, "1f3a6a", "e0c060", "6bd8ff", hood_c="16305a", bolt=True)


def cave_brute(g):
    B = figure(g, s=1.15, w=1.45, bulk=1.3, skin="8a9a7a", top="6a6f78", legs="4a3a2a", boots="3a2a1a", arms="8a9a7a", eye="ffd23f")
    g.ellipsoid((8, 18.6, 7.0), (5.2, 3.2, 3.4), lambda x, y, z, d: tex("7a8a6a", x, y, z), 3)   # 굽은 등
    helm(g, B, "6a6f78", "4a4f58")
    g.ellipsoid((8, 17.6 * B.s, 8.3), (5.0, 2.8, 2.9), lambda x, y, z, d: tex("6a6f78", x, y, z), 5, inner=0.6)
    axe(g, B.hand_r, 15, "4a3a2a", "6a6f78", "c8ccd2", size=3.8, double=False)
    for k in range(4):
        g.cone((8 + (k - 1.5) * 1.6, 21.6, 5.0), (8 + (k - 1.5) * 1.8, 23.2, 3.4), 0.6, 0.1, "5a5a50", 5)   # 등 돌기


def moss_troll(g):
    SKIN, MOSS = "6a7a5a", "4a7a2a"
    B = figure(g, s=1.3, w=1.5, bulk=1.35, skin=SKIN, top=SKIN, legs=hx(SKIN, 0.85), boots="4a4030", arms=SKIN, eye="ffd23f")
    g.ellipsoid((8, 20.5, 7.0), (6.0, 3.6, 4.0), lambda x, y, z, d: MOSS if y > 21 and rnd(x, z) < 0.7 else tex(SKIN, x, y, z), 3)
    tusks(g, B)
    g.ellipsoid(add(B.head, (0, -0.2, 2.6)), (1.1, 1.3, 1.0), hx(SKIN, 0.9), 8)   # 큰 코
    for k in range(10):
        p = (8 + (rnd(k, 3) - 0.5) * 11, 14 + rnd(k, 4) * 14, 5.5 + rnd(k, 5) * 5)
        g.ellipsoid(p, (1.1, 0.6, 1.0), MOSS, 5)
    hand = B.hand_r   # 통나무 몽둥이
    with g.parting("arm_r"):
        g.tube([add(hand, (0, -2, -0.6)), add(hand, (0, 10, 2.6))], [0.8, 1.6], lambda x, y, z, d, f: tex("6a4a2a", x, y, z, 0.15), 6)
        for k in range(3):
            g.cone(add(hand, (0, 6 + k * 1.6, 1.8)), add(hand, ((k - 1) * 1.6, 7 + k * 1.6, 3.4)), 0.4, 0.1, "8a8f96", 7)


def wild_golem(g):
    IRON, IRON2, RUST, VINE = "c8c8c0", "a8a8a0", "a86a3a", "4a8a3a"
    B = figure(g, s=1.3, w=1.5, bulk=1.4, skin=IRON, top=IRON, legs=IRON2, boots=IRON2, arms=IRON, hands=IRON2, face=False)
    hc = B.head
    g.box((hc[0] - 1.8, hc[1] + 0.2, hc[2] + 2.3), (hc[0] + 1.8, hc[1] + 0.9, hc[2] + 2.9), "ff3a2a", 8)
    g.box((hc[0] - 0.5, hc[1] - 1.8, hc[2] + 2.5), (hc[0] + 0.5, hc[1] - 0.2, hc[2] + 3.4), IRON2, 8)   # 코
    for k in range(14):   # 녹 · 덩굴
        p = (8 + (rnd(k, 1) - 0.5) * 12, 3 + rnd(k, 2) * 24, 8 + (rnd(k, 3) - 0.3) * 5)
        g.ellipsoid(p, (1.0, 0.7, 0.9), RUST if k % 3 else VINE, 5)
    for sx in (-1, 1):
        sh = B.shoulder_r if sx == 1 else B.shoulder_l
        g.tube([add(sh, (0, 1.4, 1)), add(sh, (sx * 0.8, -5, 2.4)), add(sh, (sx * 0.4, -10, 2.0))], [0.3, 0.25, 0.15], VINE, 6, smooth=True)
        for t in range(3):
            g.dot(sh[0] + sx * 0.5, sh[1] + 0.6 - t * 1.2, sh[2] + 1.8, "8a8a80", 7, 0.6)   # 리벳


# ================================================================== 짐승
def boar_(g, fur, fur2, eye, tusk, mane, plague=None, armor_c=None):
    B = quad(g, fur, hx(fur, 1.1), "2a2420", L=18, H=9, W=10, leg=5, hump=1.1, leg_r=1.7, fur2=fur2)
    beast_head(g, B, fur, hx(fur, 1.2), eye, size=1.1, tusk=tusk, mane=mane)
    if plague:
        for k in range(10):
            p = (8 + (rnd(k, 1) - 0.5) * 10, B.cy + (rnd(k, 2) - 0.2) * 6, B.z0 + 2 + k * 1.6)
            g.ellipsoid(p, (1.0, 0.9, 1.0), lambda x, y, z, d: "e8ff6a" if d < 0.3 else plague, 5)
    if armor_c:
        g.ellipsoid((8, B.back_y, B.front_z - 5), (6.4, 2.4, 4.0), lambda x, y, z, d: tex(armor_c, x, y, z), 5, inner=0.55)
    g.tube([B.tail, add(B.tail, (0, -1.5, -1.6))], [0.5, 0.3], fur2, 4)


def plague_boar(g): boar_(g, "7a6a5a", "5a4a3a", "c8ff3a", "e8e0b0", "3a3a2a", plague="7a9a2a")
def nether_hog(g): boar_(g, "a86a5a", "7a4a3a", "ff5a3a", "f0e6c8", "e8c8a0", armor_c="4a3a3a")


def ravager_(g, skin, skin2, horn, eye, big=1.0, saddle=None, crystal=None, moss=None, maw=False):
    B = quad(g, skin, hx(skin, 1.1), "2a2420", L=20 * big, H=11 * big, W=11 * big, leg=8 * big, hump=1.1, leg_r=2.2 * big, fur2=skin2)
    hc = beast_head(g, B, skin, skin2, eye, size=1.25 * big, horns=horn, ears=False)
    if maw:   # 크게 벌린 입 · 이빨 두 줄
        g.box(add(hc, (-2.6, -4.6, 3.0)), add(hc, (2.6, -2.8, 7.2)), "5a0a14", 7)
        for k in range(5):
            x = -2.0 + k
            g.cone(add(hc, (x, -2.8, 6.8)), add(hc, (x, -3.8, 6.8)), 0.35, 0.05, "f0e6c8", 8)
            g.cone(add(hc, (x, -4.6, 6.6)), add(hc, (x, -3.6, 6.6)), 0.35, 0.05, "f0e6c8", 8)
    if saddle:
        sc = (8, B.back_y + 1.2, B.z0 + 11 * big)
        g.ellipsoid(sc, (4.6, 1.2, 4.2), lambda x, y, z, d: saddle if d > 0.7 else "6a3a1a", 5)
        for sx in (-1, 1):
            g.slab((8 + sx * 5.4, B.back_y + 1.2, sc[2] - 3), (8 + sx * 5.9, B.cy - 2, sc[2] - 3), (0, 0, 6.0), 0.4, saddle, 5)
    if crystal:
        for k in range(10):
            b = (8 + (rnd(k, 5) - 0.5) * 6, B.back_y + 1.0, B.z0 + 3 + k * 1.5 * big)
            g.cone(b, add(b, ((b[0] - 8) * 0.3, 3 + rnd(k, 9) * 4, -0.5)), 1.0, 0.05, lambda x, y, z, f: "ffffff" if f > 0.7 else crystal, 6)
    if moss:
        for k in range(12):
            p = (8 + (rnd(k, 1) - 0.5) * 10, B.back_y - rnd(k, 2) * 2, B.z0 + 2 + k * 1.6)
            g.ellipsoid(p, (1.3, 0.6, 1.2), moss, 5)
    g.tube([B.tail, add(B.tail, (0, -2, -2.6)), add(B.tail, (0, -4.4, -3.0))], [1.0, 0.7, 0.3], skin2, 4, smooth=True)
    return B


def war_beast(g): ravager_(g, "5a4a44", "3a302c", "d8cfb8", "ff4020", saddle="a8281e")
def star_golem(g):
    ST, ST2, STAR = "3a3a5a", "2a2a44", "c8b8ff"
    B = ravager_(g, ST, ST2, "e8e0ff", STAR, crystal="a88aff")
    for k in range(14):
        p = (8 + (rnd(k, 1) - 0.5) * 11, B.cy + (rnd(k, 2) - 0.5) * 9, B.z0 + 1 + k * 1.4)
        g.dot(*p, "ffffff", 6, 0.5)
def world_eater(g): ravager_(g, "2a1a2a", "1a0a1a", "8a60d8", "ff2a6a", big=1.1, maw=True, crystal="c060ff")
def ancient_ravager(g): ravager_(g, "6a5a48", "4a3e30", "c8b890", "ffd23f", big=1.05, moss="5a8a3a", saddle="c8a030")


def void_hound(g):
    FUR, FUR2, EYE = "1e1430", "120a20", "c060ff"
    B = quad(g, FUR, hx(FUR, 1.2), "0a0612", L=14, H=6, W=7, leg=5, hump=0.9, leg_r=1.2, fur2=FUR2)
    hc = beast_head(g, B, FUR, FUR2, EYE, size=0.85)
    for k in range(9):   # 등에서 솟는 공허 가시 불꽃
        b = (8, B.back_y + 0.3, B.z0 + 1 + k * 1.4)
        g.cone(b, add(b, (0, 1.8 + (k % 3) * 0.8, -1.2)), 0.6, 0.05, EYE if k % 2 else "8a60d8", 5)
    g.tube([B.tail, add(B.tail, (0, 1.4, -2.4)), add(B.tail, (0, 3.0, -3.2))], [0.7, 0.5, 0.2], lambda x, y, z, d, f: EYE if f > 1.4 else FUR, 4, smooth=True)
    for sx in (-1, 1):
        g.dot(hc[0] + sx * 1.9, hc[1] + 1.0, hc[2] + 2.6, "ffffff", 9, 0.4)


# ================================================================== 거미 · 벌레 · 슬라임
def venom_spider(g): spider(g, "3a2a4a", "5a2a6a", "2a1a30", "7dff6a", big=0.8, abd_mark="7dff6a")
def nightmare_spider(g):
    spider(g, "1a1020", "2a1030", "120a18", "ff2a3a", big=1.15, abd_mark="ff2a3a")
    crown(g, (8, 10.8, 11.6), 2.0, "c8a030", gem="ff2a3a", spikes=5, height=1.4)
def sand_scorpion(g): spider(g, "c8903a", "a8702a", "8a5a1a", "1a1a1a", big=0.9, tail="3a2a1a", claws="b8802a")


def silverfish_swarm(g):
    for (dx, dz, k) in ((0, 0, 1.0), (-5, -6, 0.7), (5, -5, 0.75)):
        g2 = Grid()
        bug(g2, "a8acb4", "7a7f88", "1a1a1a", n=5, r=2.2 * k)
        for key, v in g2.cells.items():   # 옮겨 담기 (작은 떼)
            g.cells[(key[0] + int(dx / VL.VS), key[1], key[2] + int(dz / VL.VS))] = v


def end_crawler(g):
    bug(g, "3a1a4a", "5a2a6a", "c060ff", n=6, r=2.4)
    for k in range(5):
        g.dot(8, 4.0, 12 - k * 3.2, "c060ff", 6, 0.6)


def jelly_slime(g): slime(g, "6ad86a", "3a9a3a")
def mire_slime(g):
    c = slime(g, "6a6a3a", "4a4a2a", eye="1a1410")
    for k in range(6):   # 머리의 늪 풀 · 버섯
        a = k * 1.05
        b = (8 + math.cos(a) * 2.5, c[1] + 5.4, 8 + math.sin(a) * 2.5)
        g.tube([b, add(b, (math.cos(a) * 0.6, 2.4, math.sin(a) * 0.6))], [0.3, 0.1], "5a8a3a", 6)
    g.ellipsoid((9.8, c[1] + 6.4, 7.0), (1.4, 0.7, 1.4), "a8321e", 7)


def magma_brute(g):
    ROCK, LAVA, HOT = "3a2420", "ff5a1a", "ffb02a"

    def rock(x, y, z, d):
        if d > 0.7 and (abs(math.sin(x * 1.1 + y * 0.5)) < 0.12 or abs(math.sin(z * 0.9 - y * 0.7)) < 0.1):
            return LAVA
        return tex(ROCK, x, y, z, 0.15)
    g.box((1.5, 0, 1.5), (14.5, 12, 14.5), lambda x, y, z: rock(x, y, z, 1.0) if min(x - 1.5, 14.5 - x, z - 1.5, 14.5 - z, 12 - y) < 1.2 else LAVA, 3)
    g.box((3, 5.5, 14.0), (13, 7.5, 15.0), HOT, 5)   # 벌어진 입
    for sx in (-1, 1):
        g.box((8 + sx * 3 - 1.2, 8.5, 14.2), (8 + sx * 3 + 1.2, 10.5, 15.0), "fff0a0", 6)
    for k in range(6):
        b = (3 + k * 2, 12, 4 + (k % 2) * 6)
        g.cone(b, add(b, (0, 2 + (k % 3), 0)), 0.8, 0.1, HOT if k % 2 else LAVA, 4)


def bomber(g):
    BODY, FUSE, SPARK = "3a3a44", "8a6a3a", "ffd84a"
    g.sphere((8, 9, 8), 6.5, lambda x, y, z, d: "5a5a66" if d > 0.9 and y > 12 else tex(BODY, x, y, z, 0.06), 3)
    g.ellipsoid((8, 9, 8), (6.7, 0.7, 6.7), "c8a030", 4, inner=0.5)   # 쇠 띠
    for sx in (-1, 1):   # 짧은 다리
        g.tube([(8 + sx * 3, 3.5, 8), (8 + sx * 3.4, 0.6, 8.6)], [1.4, 1.2], "2a2a30", 3)
        g.box((8 + sx * 3.4 - 1.3, 0, 7.2), (8 + sx * 3.4 + 1.3, 0.8, 11.0), "1a1a20", 4)
        g.box((8 + sx * 2.2 - 0.9, 10.2, 13.8), (8 + sx * 2.2 + 0.9, 12.2, 14.9), "ffd23f", 6)   # 성난 눈
        g.slab((8 + sx * 1.0, 13.0, 14.4), (8 + sx * 3.4, 12.4, 14.2), (0, 0.5, 0), 0.4, "1a1a1a", 7)
    g.box((5.6, 6.4, 13.9), (10.4, 7.4, 14.8), "1a1a1a", 6)
    g.tube([(8, 15, 8), (8.6, 17, 7.6), (9.8, 18.2, 7.0)], [0.9, 0.5, 0.3], FUSE, 5, smooth=True)   # 심지
    g.sphere((10.0, 18.6, 7.0), 0.9, lambda x, y, z, d: "ffffff" if d < 0.3 else SPARK, 7)
    for k in range(5):
        a = k * 1.25
        g.dot(10 + math.cos(a) * 1.6, 18.8 + math.sin(a) * 1.2, 7, "ff7a1f", 7, 0.5)


# ================================================================== 불꽃 · 정령 · 날개
FIRE = ["ffd84a", "ffa51f", "ff7a1f", "e8401a", "a8200e"]


def ember_spirit(g):
    c = flame_body(g, FIRE, height=16, width=4.2)
    rods(g, c, "e0a030", n=6, r=5.5, glow="fff0a0")
    for sx in (-1, 1):
        g.box((8 + sx * 1.5 - 0.6, c[1] + 0.6, 8 + 3.6), (8 + sx * 1.5 + 0.6, c[1] + 1.8, 8 + 4.2), "fff4c0", 7)


def ember_imp(g):
    SKIN, HORN = "c83a1a", "2a1a14"
    B = figure(g, s=0.7, w=0.95, bulk=0.9, skin=SKIN, top=hx(SKIN, 0.85), legs=hx(SKIN, 0.75), boots=HORN, arms=SKIN, eye="ffd84a")
    for sx in (-1, 1):
        g.tube([add(B.head, (sx * 1.4, 1.4, 0)), add(B.head, (sx * 2.4, 3.0, -0.6)), add(B.head, (sx * 2.2, 4.4, -1.4))], [0.5, 0.35, 0.1], HORN, 8, smooth=True)
    wings(g, B, "5a1a10", "a8200e", rim="ff7a1f", span=9, rise=6, kind="bat")
    g.tube([(8, 8, 6), (8, 5, 2), (9, 4, -1), (11, 5, -2)], [0.5, 0.4, 0.3, 0.2], SKIN, 4, smooth=True)
    g.cone((11, 5, -2), (12.4, 6, -2.6), 0.6, 0.05, "ff7a1f", 5)
    with g.parting("arm_r"):
        g.tube([B.hand_r, add(B.hand_r, (0, 8, 1.2))], [0.3, 0.3], "3a2a1a", 6)   # 삼지 쇠스랑
        for k in (-1, 0, 1):
            g.cone(add(B.hand_r, (k * 0.8, 8, 1.2)), add(B.hand_r, (k * 0.9, 10, 1.4)), 0.3, 0.05, "ff7a1f", 7)


def inferno_lord(g):
    c = flame_body(g, FIRE, height=22, width=5.4)
    rods(g, c, "3a2420", n=8, r=7.0, h=3.0, glow="ff5a1a")
    for sx in (-1, 1):   # 불꽃 팔
        g.tube([(8 + sx * 4.4, c[1] + 2, 8), (8 + sx * 7.4, c[1] - 1, 10), (8 + sx * 8.4, c[1] + 2, 12)], [1.6, 1.2, 1.5], lambda x, y, z, d, f: FIRE[min(4, int(d * 4))], 5, smooth=True)
        g.box((8 + sx * 1.8 - 0.7, c[1] + 1.4, 8 + 4.4), (8 + sx * 1.8 + 0.7, c[1] + 2.6, 8 + 5.0), "fff4c0", 7)
    crown(g, (8, c[1] + 6.4, 8.2), 2.6, "3a2420", gem="ff5a1a", spikes=7, height=2.2)


def magma_sentinel(g):
    ROCK = "3a2420"
    c = flame_body(g, FIRE, height=17, width=3.6)
    for k in range(3):   # 돌 고리 셋
        g.ring((8, c[1] - 4 + k * 4, 8), 5.2 + k * 0.4, 0.7, lambda x, y, z, a, k=k: "ff5a1a" if int(a * 4) % 3 == 0 else ROCK, 5, axis="y", tilt=0.3 * (k - 1))
    g.ellipsoid((8, c[1] + 4.4, 9.2), (2.6, 2.4, 2.4), lambda x, y, z, d: tex(ROCK, x, y, z, 0.15), 6)   # 돌 가면
    for sx in (-1, 1):
        g.box((8 + sx * 1.0 - 0.5, c[1] + 4.6, 11.3), (8 + sx * 1.0 + 0.5, c[1] + 5.4, 11.8), "fff0a0", 8)


def soul_wisp(g):
    c = (8, 8, 8)
    g.sphere(c, 3.4, lambda x, y, z, d: "ffffff" if d < 0.3 else "a8fff8" if d < 0.7 else "3fe8ff", 4)
    for k in range(8):
        a = k * 0.8
        b = (8 + math.cos(a) * 1.4, 10, 8 + math.sin(a) * 1.4)
        g.cone(b, add(b, (math.cos(a) * 0.8, 3 + (k % 3) * 1.2, math.sin(a) * 0.8 - 1.0)), 1.0, 0.1, "7fe8ff" if k % 2 else "a8fff8", 5)
    g.tube([(8, 6, 7), (8.6, 3, 5.4), (7.8, 1, 3.8)], [1.6, 0.9, 0.2], "5ab8c8", 3, smooth=True)   # 꼬리
    for sx in (-1, 1):
        g.dot(8 + sx * 1.3, 9.0, 11.2, "1a3a4a", 7, 0.8)


def thorn_sprite(g):
    SKIN, LEAF, THORN = "a8d870", "4a8a2a", "3a2a1a"
    B = figure(g, s=0.6, w=0.85, skin=SKIN, top=LEAF, legs=hx(LEAF, 0.8), arms=SKIN, robe=True, eye="ff5aa0")
    robe(g, B, LEAF, hx(LEAF, 0.8), "ff8ac0", flare=3.4)
    wings(g, B, "c8ffd8", "8af0a0", rim="ff8ac0", span=8, rise=5, feathers=4, kind="shard")
    for k in range(8):
        a = k * 0.8
        b = add(B.head, (math.cos(a) * 1.4, 1.2, math.sin(a) * 1.4 - 0.4))
        g.cone(b, add(b, (math.cos(a) * 1.0, 1.6, math.sin(a) * 1.0)), 0.35, 0.05, THORN, 8)
    g.dot(B.head[0] + 1.2, B.head[1] + 2.0, B.head[2] + 0.8, "ff5aa0", 9, 0.9)   # 꽃
    for k in range(5):
        g.tube([(8, 3 + k * 2.4, 8), (8 + math.cos(k) * 4.6, 3.6 + k * 2.4, 8 + math.sin(k) * 4.0)], [0.25, 0.12], THORN, 4)   # 가시 덩굴


def fallen_seraph(g):
    SKIN, ARM, TRIM = "d8d0e0", "2a2438", "8a60d8"
    B = figure(g, s=1.1, w=0.95, skin=SKIN, top=ARM, legs=hx(ARM, 0.8), arms=ARM, robe=True, eye="c060ff")
    robe(g, B, ARM, hx(ARM, 0.8), TRIM, flare=5.2, hem_ragged=True)
    armor(g, B, ARM, TRIM, gem="c060ff", belt=False, greaves=False, tassets=False)
    hair(g, B, "e8e0f0", length=12, width=2.6)
    wings(g, B, "2a2438", "4a3a68", rim="c060ff", span=15, rise=10, feathers=6)
    g.ring(add(B.head, (0, 4.4, -1.4)), 2.8, 0.35, "5a4a6a", 7, axis="y", tilt=1.3)   # 부서진 후광
    sword(g, B.hand_r, 15, "3a3448", "c060ff", TRIM, width=1.6)


def night_hunter(g): flyer(g, "2a2a3a", "3a3a54", "4a4a60", "7dff6a", head_kind="maw")
def void_phantom(g): flyer(g, "1a1030", "2a1a48", "3a2a58", "c060ff", span=20, head_kind="maw", rim="c060ff")
def storm_herald(g):
    hc = flyer(g, "2a4a8a", "4a8ae0", "d8e8ff", "fff080", span=21, rim="e8f4ff")
    for k in range(5):   # 머리 깃 · 번개
        b = add(hc, (0, 1.6, -0.6 - k * 0.7))
        g.cone(b, add(b, (0, 2.0, -1.2)), 0.5, 0.05, "6bd8ff" if k % 2 else "fff080", 6)
def storm_harpy(g):
    SKIN, FEA = "e8d0b8", "8ab8e8"
    B = figure(g, s=0.9, w=0.9, skin=SKIN, top=FEA, legs="d8e8ff", boots="c89048", arms=FEA, reach=False, eye="6bd8ff")
    hair(g, B, "3a5a9a", length=9, width=2.4)
    wings(g, B, "e8f0ff", FEA, rim="3a6ab8", span=16, rise=10, feathers=6)
    for sx in (-1, 1):
        for t in range(3):
            a = (t - 1) * 0.6
            b = (8 + sx * 2.0, 0.8, 8.8)
            with g.parting("leg_r" if sx == 1 else "leg_l"):
                g.tube([b, add(b, (math.sin(a) * 1.8, -0.2, math.cos(a) * 1.8))], [0.4, 0.15], "e0b030", 7)


# ================================================================== 그림자 · 공포
def shadow_stalker(g):
    DARK, DARK2, EYE = "14101c", "241c30", "c060ff"
    B = figure(g, s=1.5, w=0.7, bulk=0.65, skin=DARK, top=DARK2, legs=DARK, boots=DARK, arms=DARK2, hands=DARK, face=False, reach=False)
    for sx in (-1, 1):
        g.box((B.head[0] + sx * 1.3 - 0.7, B.head[1] + 0.2, B.head[2] + 3.4), (B.head[0] + sx * 1.3 + 0.7, B.head[1] + 0.9, B.head[2] + 4.0), EYE, 8)
        hand = B.hand_r if sx == 1 else B.hand_l
        for t in range(3):
            g.cone(hand, add(hand, (sx * 0.3 + (t - 1) * 0.5, -2.8, 0.6)), 0.3, 0.05, DARK2, 6)
    cape(g, B, "1a1424", "3a2a4a", length=1.0, width=3.4, ragged=True)
    for k in range(10):   # 흩날리는 그림자 조각
        a = k * 1.7
        g.dot(8 + math.cos(a) * 6, 6 + k * 3.2, 8 + math.sin(a) * 5, "3a2a4a" if k % 2 else EYE, 5, 0.8)


def deep_horror(g):
    SKIN, SKIN2, GLOW = "1e2a3a", "0e1820", "3fe8ff"
    B = figure(g, s=1.45, w=0.8, bulk=0.75, skin=SKIN, top=SKIN2, legs=SKIN, boots=SKIN2, arms=SKIN, hands=SKIN2, face=False, reach=False)
    hc = B.head
    g.ellipsoid(add(hc, (0, 0.4, 0)), (3.2, 3.4, 3.2), lambda x, y, z, d: tex(SKIN, x, y, z), 7)
    g.dot(hc[0], hc[1] + 0.6, hc[2] + 3.2, GLOW, 9, 1.4)   # 외눈
    for k in range(8):   # 얼굴 촉수
        a = math.pi * (0.1 + k * 0.8 / 7)
        b = (hc[0] + math.cos(a) * 1.8, hc[1] - 1.8, hc[2] + 2.2)
        g.tube([b, add(b, (math.cos(a) * 0.6, -2.4, 0.8)), add(b, (math.cos(a) * 1.2, -4.6, 0.2))], [0.5, 0.35, 0.1], SKIN2, 8, smooth=True)
    for k in range(10):
        p = (8 + (rnd(k, 1) - 0.5) * 7, 6 + rnd(k, 2) * 22, 9.6)
        g.dot(*p, GLOW, 6, 0.6)



# ================================================================== v5.10.34 바닐라 몬스터 · 야생 동물 (아머러스 워크샵 느낌)
def vn_zombie(g):
    SKIN = "5a9a4a"
    B = figure(g, s=1.0, w=1.0, skin=SKIN, top="2a8a8a", legs="3a3a8a", boots="4a4a5a", arms=SKIN, hands=SKIN, eye="1a1a1a")
    for k in range(6):   # 찢어진 셔츠 · 썩은 자국
        g.dot(8 + (rnd(k, 1) - 0.5) * 5, 13 + rnd(k, 2) * 6, 10.6, hx("2a8a8a", 0.6), 6, 0.6)
    for sx in (-1, 1):
        g.dot(B.head[0] + sx * 1.6, B.head[1] + 1.4, B.head[2] + 2.4, hx(SKIN, 0.7), 8, 0.5)
    g.ellipsoid(add(B.head, (0, 1.6, -0.2)), (2.5, 0.7, 2.5), lambda x, y, z, d: "3a6a2a" if d > 0.4 else None, 7)   # 정수리 그늘


def vn_husk(g):
    SKIN = "b8a070"
    B = figure(g, s=1.05, w=1.05, skin=SKIN, top="7a6a4a", legs="5a4a30", boots="3a2a18", arms=SKIN, hands=SKIN, eye="3a2a10")
    for k in range(8):   # 해진 천 조각
        a = k * 0.8
        p = (8 + math.cos(a) * 3.0, 9 + (k % 4) * 1.4, 8 + math.sin(a) * 2.2)
        g.slab(p, add(p, (math.cos(a) * 0.6, -2.4, math.sin(a) * 0.6)), (0.8, 0, 0.3), 0.3, "8a7a54", 4)


def vn_drowned(g):
    SKIN = "4a8a8a"
    B = figure(g, s=1.0, w=1.0, skin=SKIN, top="3a6a7a", legs="2a4a5a", boots="2a3a40", arms=SKIN, hands=SKIN, eye="7fe8ff")
    for k in range(7):   # 해초
        p = (8 + (rnd(k, 3) - 0.5) * 6, 18 - k * 1.6, 10.4)
        g.tube([p, add(p, (0.4, -2.6, 0.3))], [0.35, 0.2], "3a8a3a", 6)
    spear(g, B.hand_r, 22, "6a8a8a", "7fd8e8", prongs=3)


def vn_skeleton(g):
    B = skeleton(g, eye="1a1a1a")
    bow(g, B.hand_l, 14, "8a6a3a", "e8e4d8")


def vn_stray(g):
    B = skeleton(g, bone="c8d8e0", eye="1a2a3a", glow="7fe8ff")
    hood(g, B, "5a6a70", depth=1.6, point=False)
    cape(g, B, "4a5a64", "8aa8b8", length=7.0, width=4.0, ragged=True)
    bow(g, B.hand_l, 14, "6a7a80", "d8f0ff")


def vn_wither_skeleton(g):
    B = skeleton(g, bone="2a2a2e", s=1.2, eye="0a0a0a", glow="ff3a2a")
    sword(g, B.hand_r, 11, "4a4a50", "8a8a90", "3a2a1a")


def vn_creeper(g):
    G1, G2, G3 = "4aa84a", "2a7a2a", "8ad88a"
    col = lambda x, y, z, d=0: [G1, G2, G3, G1][int(x * 1.7 + y * 2.3 + z * 1.3) % 4]
    g.box((5.6, 6, 6.6), (10.4, 18, 9.4), col, 3)                      # 몸
    g.box((4.8, 18, 5.2), (11.2, 24.5, 10.8), col, 3)                   # 머리
    for (x0, x1) in ((5.6, 7.4), (8.6, 10.4)):                          # 얼굴 (검은 눈 · 입)
        g.box((x0, 21.5, 10.7), (x1, 23.2, 11.0), "1a1a1a", 6)
    g.box((7.2, 19.0, 10.7), (8.8, 21.6, 11.0), "1a1a1a", 6)
    g.box((6.4, 18.4, 10.7), (9.6, 19.6, 11.0), "1a1a1a", 6)
    for (zz, name) in ((10.8, "leg_f"), (5.2, "leg_b")):               # 네 다리
        for sx in (-1, 1):
            with g.parting(name + ("l" if sx < 0 else "r")):
                g.box((8 + sx * 1.8 - 1.4, 0, zz - 1.4), (8 + sx * 1.8 + 1.4, 6.2, zz + 1.4), col, 3)
            g.rig[name + ("l" if sx < 0 else "r")] = (8 + sx * 1.8, 6.0, zz)
    g.kind = "quad"


def vn_spider(g):
    spider(g, "3a3030", "2a2222", "2a2424", "e02a2a")


def vn_cave_spider(g):
    spider(g, "1f3a44", "163038", "1a2a30", "e02a2a", big=0.75, abd_mark="4ad8e0")


def vn_enderman(g):
    B = figure(g, s=1.45, w=0.62, bulk=0.55, skin="161616", top="161616", legs="161616", boots="101010", arms="161616", hands="161616", face=False)
    hc = B.head
    for sx in (-1, 1):
        g.box((hc[0] + sx * 1.3 - 0.9, hc[1] + 0.1, hc[2] + 2.3), (hc[0] + sx * 1.3 + 0.9, hc[1] + 0.7, hc[2] + 2.8), "e080ff", 9)
        g.dot(hc[0] + sx * 1.3, hc[1] + 0.4, hc[2] + 2.8, "ffffff", 10, 0.3)
    floaters(g, [(4, 30, 8), (13, 26, 6), (6, 18, 11)], "c060ff", r=0.4)


def vn_witch(g):
    witch_(g, "3a2a4a", "5a3a6a", "a8b890", "7dff6a")


def vn_pillager(g):
    SKIN = "a8a8a0"
    B = figure(g, s=1.0, w=1.0, skin=SKIN, top="4a4a54", legs="3a3a44", boots="2a2a30", arms="5a5a64", hands=SKIN, eye="1a3a2a")
    g.cone(add(B.head, (0, -0.4, 2.3)), add(B.head, (0, -1.6, 3.8)), 0.7, 0.3, hx(SKIN, 0.85), 8)   # 큰 코
    g.ellipsoid(add(B.head, (0, 0.8, 0)), (2.5, 0.6, 2.5), lambda x, y, z, d: "2a2a30" if y > B.head[1] + 1.2 else None, 7)
    bow(g, B.hand_l, 10, "6a4a2a", "c8c8c0", bend=1.6)
    g.tube([(5.4, 19, 9.8), (10.6, 12, 10.0)], [0.35, 0.35], "6a4a2a", 6)


def vn_vindicator(g):
    SKIN = "a8a8a0"
    B = figure(g, s=1.0, w=1.05, skin=SKIN, top="3a3a44", legs="2a2a34", boots="1a1a20", arms="3a3a44", hands=SKIN, eye="1a3a2a")
    g.cone(add(B.head, (0, -0.4, 2.3)), add(B.head, (0, -1.6, 3.8)), 0.7, 0.3, hx(SKIN, 0.85), 8)
    axe(g, B.hand_r, 9, "6a4a2a", "8a8f96", "d8dce6", size=3.0, double=False)


def vn_slime(g):
    slime(g, "6ad86a", "3a9a3a")


def vn_magma_cube(g):
    slime(g, "5a1a0a", "ffb030", eye="ffd84a", r=7.0)
    for k in range(6):
        y = 2 + k * 2.0
        g.ellipsoid((8, y, 8), (7.1, 0.4, 7.1), lambda x, y_, z, d: "ff7a1f" if d > 0.85 else None, 4, inner=0.8)


def vn_phantom(g):
    flyer(g, "3a4a6a", "4a5a7a", "5a6a8a", "7dff6a", span=18, head_kind="maw")


# ---- 야생 동물
def an_cow(g):
    B = quad(g, "5a3a24", "e8e4dc", "2a2420", L=18, H=10, W=10, leg=6, hump=0.9, leg_r=1.6, fur2="4a3020")
    for k in range(6):   # 흰 얼룩
        p = (8 + (rnd(k, 4) - 0.5) * 9, B.cy + (rnd(k, 5) - 0.3) * 6, B.z0 + 3 + k * 2.4)
        g.ellipsoid(p, (2.0, 1.6, 2.0), "e8e4dc", 3)
    hc = beast_head(g, B, "5a3a24", "e8d0c0", "1a1a1a", size=1.0, horns="d8d0c0", open_mouth=False)
    g.ellipsoid(add(hc, (0, -2.6, 3.6)), (1.4, 1.0, 1.0), "f0b0b8", 6)   # 젖
    g.tube([B.tail, add(B.tail, (0, -4, -0.8))], [0.4, 0.3], "4a3020", 4)


def an_pig(g):
    B = quad(g, "f0a0a8", "f8b8c0", "c87880", L=15, H=9, W=9.5, leg=3.6, hump=0.6, leg_r=1.4, fur2="e090a0")
    hc = beast_head(g, B, "f0a0a8", "f8b8c0", "1a1a1a", size=0.95, open_mouth=False)
    g.box(add(hc, (-1.6, -1.8, 5.0)), add(hc, (1.6, 0.2, 6.0)), "e88890", 6)   # 납작 코
    for sx in (-1, 1):
        g.dot(hc[0] + sx * 0.7, hc[1] - 0.8, hc[2] + 6.0, "8a3a4a", 7, 0.4)
    g.tube([B.tail, add(B.tail, (0.6, 0.8, -0.8)), add(B.tail, (-0.4, 1.2, -1.2))], [0.35, 0.3, 0.2], "e090a0", 4, smooth=True)


def an_sheep(g):
    B = quad(g, "f0ece4", "e8e4dc", "3a3a3a", L=16, H=10, W=11, leg=5, hump=0.7, leg_r=1.3, fur2="d8d4cc")
    for k in range(22):   # 몽실몽실 양털
        p = (8 + (rnd(k, 1) - 0.5) * 10, B.cy + (rnd(k, 2) - 0.3) * 8, B.z0 + 1 + rnd(k, 3) * 15)
        g.sphere(p, 2.0, lambda x, y, z, d: "fffcf4" if d < 0.5 else "e8e4dc", 3)
    for leg_name in ("leg_fl", "leg_fr", "leg_bl", "leg_br"):
        pass
    beast_head(g, B, "d8c8b8", "d8c8b8", "1a1a1a", size=0.9, open_mouth=False)


def an_chicken(g):
    g.ellipsoid((8, 6.0, 8), (3.0, 3.0, 4.0), lambda x, y, z, d: tex("f4f4f0", x, y, z, 0.05), 3)   # 몸
    hc = (8, 10.2, 11.0)
    with g.parting("head"):
        g.ellipsoid(hc, (1.8, 2.4, 1.8), "f4f4f0", 4)
        g.box((7.0, 10.0, 12.6), (9.0, 10.8, 14.0), "f0b030", 5)            # 부리
        g.box((7.5, 8.4, 12.2), (8.5, 9.8, 12.8), "e02a2a", 5)              # 턱볏
        g.box((7.4, 12.4, 10.2), (8.6, 13.4, 11.8), "e02a2a", 5)            # 볏
        for sx in (-1, 1):
            g.dot(8 + sx * 1.6, 10.8, 12.0, "1a1a1a", 6, 0.4)
    g.rig["head"] = (8, 8.4, 10.4)
    for sx in (-1, 1):   # 날개 · 다리
        g.ellipsoid((8 + sx * 3.0, 6.2, 7.6), (0.6, 2.0, 3.0), "e4e4e0", 4)
        with g.parting("leg_" + ("l" if sx < 0 else "r")):
            g.tube([(8 + sx * 1.2, 3.4, 8), (8 + sx * 1.2, 0.4, 8.4)], [0.3, 0.3], "f0b030", 3)
            g.box((8 + sx * 1.2 - 0.8, 0, 8.0), (8 + sx * 1.2 + 0.8, 0.4, 9.6), "f0b030", 3)
        g.rig["leg_" + ("l" if sx < 0 else "r")] = (8 + sx * 1.2, 3.4, 8)
    g.cone((8, 7.0, 4.2), (8, 9.0, 3.0), 1.4, 0.4, "e8e8e4", 3)          # 꼬리깃
    g.kind = "biped_small"


def an_rabbit(g):
    g.ellipsoid((8, 4.0, 7.4), (2.6, 2.6, 3.2), lambda x, y, z, d: tex("a8865a", x, y, z, 0.06), 3)
    hc = (8, 6.6, 10.6)
    with g.parting("head"):
        g.ellipsoid(hc, (1.8, 1.7, 1.8), "a8865a", 4)
        for sx in (-1, 1):
            g.slab(add(hc, (sx * 0.8, 1.2, -0.6)), add(hc, (sx * 1.0, 4.8, -1.2)), (0, 0, 1.0), 0.5, "a8865a", 4)
            g.slab(add(hc, (sx * 0.8, 1.6, -0.5)), add(hc, (sx * 1.0, 4.2, -1.0)), (0, 0, 0.5), 0.55, "e8b8b0", 5)
            g.dot(hc[0] + sx * 1.1, hc[1] + 0.4, hc[2] + 1.5, "1a1a1a", 6, 0.35)
        g.dot(hc[0], hc[1] - 0.4, hc[2] + 1.8, "e88890", 6, 0.35)
    g.rig["head"] = (8, 5.4, 9.6)
    g.sphere((8, 5.0, 4.0), 1.1, "f4f0e8", 4)   # 꼬리
    for sx in (-1, 1):
        g.ellipsoid((8 + sx * 2.0, 1.4, 6.4), (0.9, 1.2, 2.4), "987650", 3)
        g.box((8 + sx * 1.0 - 0.5, 0, 9.2), (8 + sx * 1.0 + 0.5, 2.0, 10.2), "987650", 3)
    g.kind = "solid"


def an_wolf(g):
    B = quad(g, "d8d4cc", "f0ece4", "8a8480", L=16, H=7, W=7, leg=6, hump=1.2, leg_r=1.1, fur2="b8b4ac", simple_back=True)
    hc = beast_head(g, B, "d8d4cc", "c8c4bc", "1a1a1a", size=0.85, open_mouth=False)
    g.dot(hc[0], hc[1] - 1.0, hc[2] + 4.6, "1a1a1a", 7, 0.6)
    for i in range(6):   # 목 갈기
        g.sphere((8, B.cy + 2.0, B.front_z - 1 - i * 0.8), 2.6 - i * 0.2, "e8e4dc", 3)
    g.tube([B.tail, add(B.tail, (0, 1.0, -2.6)), add(B.tail, (0, -1.4, -5.0))], [1.0, 1.0, 0.6], "c8c4bc", 4, smooth=True)


def an_fox(g):
    B = quad(g, "e2843a", "f4f0e8", "2a2420", L=14, H=6, W=6.5, leg=4.6, hump=0.8, leg_r=1.0, fur2="c86a28", simple_back=True)   # v5.10.39 뒷다리 곧게
    for sx in (-1, 1):   # 엉덩이 아래 허벅지를 몸에 붙여 둥글게 (발목 꺾임 없이)
        g.ellipsoid((8 + sx * 1.9, B.cy - 0.6, B.z0 + 2.4), (1.4, 2.0, 1.8), lambda x, y, z, d: tex("c86a28", x, y, z), 2)
    hc = beast_head(g, B, "e2843a", "f4f0e8", "1a1a1a", size=0.8, open_mouth=False)
    g.dot(hc[0], hc[1] - 1.0, hc[2] + 4.2, "1a1a1a", 7, 0.5)
    g.tube([B.tail, add(B.tail, (0, 0.6, -3.0)), add(B.tail, (0, -1.0, -6.0))], [1.3, 1.6, 0.8], lambda x, y, z, d, f: "f4f0e8" if f > 1.6 else "e2843a", 4, smooth=True)


def an_goat(g):
    B = quad(g, "e8e4dc", "f4f0e8", "4a4440", L=16, H=9, W=8.5, leg=6, hump=0.9, leg_r=1.2, fur2="d8d4cc")
    hc = beast_head(g, B, "e8e4dc", "e8e4dc", "c8a030", size=0.85, horns="a8a090", open_mouth=False)
    g.cone(add(hc, (0, -2.6, 3.6)), add(hc, (0, -4.6, 3.4)), 0.7, 0.2, "d8d4cc", 6)   # 수염


def an_polar_bear(g):
    B = quad(g, "f4f4ee", "e8e8e2", "2a2a2a", L=20, H=12, W=12, leg=6, hump=1.1, leg_r=2.2, fur2="e4e4de")
    hc = beast_head(g, B, "f4f4ee", "e8e8e2", "1a1a1a", size=1.1, open_mouth=False)
    g.dot(hc[0], hc[1] - 1.2, hc[2] + 5.8, "1a1a1a", 7, 0.7)


VANILLA = {
    "vn_zombie": vn_zombie, "vn_husk": vn_husk, "vn_drowned": vn_drowned, "vn_skeleton": vn_skeleton, "vn_stray": vn_stray,
    "vn_wither_skeleton": vn_wither_skeleton, "vn_creeper": vn_creeper, "vn_spider": vn_spider, "vn_cave_spider": vn_cave_spider,
    "vn_enderman": vn_enderman, "vn_witch": vn_witch, "vn_pillager": vn_pillager, "vn_vindicator": vn_vindicator,
    "vn_slime": vn_slime, "vn_magma_cube": vn_magma_cube, "vn_phantom": vn_phantom,
    "an_cow": an_cow, "an_pig": an_pig, "an_sheep": an_sheep, "an_chicken": an_chicken, "an_rabbit": an_rabbit,
    "an_wolf": an_wolf, "an_fox": an_fox, "an_goat": an_goat, "an_polar_bear": an_polar_bear,
}

# ================================================================== 목록
BUILDERS = {
    "jelly_slime": jelly_slime, "goblin": goblin, "rotten_farmer": rotten_farmer, "forest_bandit": forest_bandit,
    "silverfish_swarm": silverfish_swarm, "venom_spider": venom_spider, "skeleton_captain": skeleton_captain, "bomber": bomber,
    "swamp_hag": swamp_hag, "mummy": mummy, "sand_scorpion": sand_scorpion, "drowned_pirate": drowned_pirate,
    "frost_wraith": frost_wraith, "orc_warrior": orc_warrior, "soul_wisp": soul_wisp, "orc_shaman": orc_shaman,
    "ember_spirit": ember_spirit, "magma_brute": magma_brute, "night_hunter": night_hunter, "plague_boar": plague_boar,
    "nether_hog": nether_hog, "piglin_berserker": piglin_berserker, "bone_knight": bone_knight, "shadow_stalker": shadow_stalker,
    "dune_colossus": dune_colossus, "war_beast": war_beast, "lich": lich, "corrupted_knight": corrupted_knight,
    "end_crawler": end_crawler, "void_phantom": void_phantom, "void_knight": void_knight, "star_golem": star_golem,
    "abyss_caster": abyss_caster, "frost_titan": frost_titan, "inferno_lord": inferno_lord, "dread_rider": dread_rider,
    "nightmare_spider": nightmare_spider, "storm_herald": storm_herald, "ancient_lich": ancient_lich, "world_eater": world_eater,
    "fallen_seraph": fallen_seraph, "abyss_king": abyss_king, "day_bandit": day_bandit, "day_raider": day_raider,
    "day_mercenary": day_mercenary, "day_warlock": day_warlock, "day_knight": day_knight, "treasure_guardian": treasure_guardian,
    "wild_golem": wild_golem, "moss_troll": moss_troll, "bog_witch": bog_witch, "crypt_knight": crypt_knight,
    "ember_imp": ember_imp, "sand_stalker": sand_stalker, "void_hound": void_hound, "storm_harpy": storm_harpy,
    "thorn_sprite": thorn_sprite, "bone_marksman": bone_marksman, "mire_slime": mire_slime, "frost_archer": frost_archer,
    "sand_wraith": sand_wraith, "canyon_raider": canyon_raider, "cave_brute": cave_brute, "storm_caller": storm_caller,
    "magma_sentinel": magma_sentinel, "deep_horror": deep_horror, "grave_knight": grave_knight, "ancient_ravager": ancient_ravager,
}
BUILDERS.update(VANILLA)   # v5.10.34 바닐라 몬스터 · 동물 (뒤에 붙여 기존 번호 유지)
ORDER = list(BUILDERS)   # 순서 = CustomModelData (9100 + i). 새 몬스터는 뒤에 붙여야 기존 번호가 유지됨

# 몹 판정 상자와 모양이 크게 다른 것: 표시 크기 배율 (플러그인이 몹 키 × 배율로 그림)
SIZE = {"night_hunter": 2.2, "void_phantom": 2.6, "storm_herald": 2.6, "storm_harpy": 3.0, "soul_wisp": 1.0, "thorn_sprite": 1.3,
        "fallen_seraph": 2.2, "silverfish_swarm": 2.6, "end_crawler": 2.2, "bomber": 1.0, "magma_brute": 1.0,
        "sand_scorpion": 1.4, "venom_spider": 1.5, "nightmare_spider": 1.3, "void_hound": 1.3, "ember_imp": 0.9,
        "vn_spider": 1.5, "vn_cave_spider": 1.5, "vn_phantom": 2.2, "vn_slime": 1.0, "vn_magma_cube": 1.0}


PART_ORDER = ["body", "head", "arm_l", "arm_r", "leg_l", "leg_r", "leg_fl", "leg_fr", "leg_bl", "leg_br", "wing_l", "wing_r"]


def _assign(g):
    """따로 표시하지 않은 복셀을 부위에 붙임: 머리 근처(투구 · 두건 · 모자 · 왕관) → 머리, 손 근처(물약 · 구슬) → 그 팔"""
    B = getattr(g, "body", None)
    if getattr(g, "kind", None) != "biped" or B is None:
        return
    hx_, hy, hz = B.head
    rx, ry, rz = B.head_r
    neck_y = g.rig["head"][1]
    for k, v in list(g.cells.items()):
        if v[2] is not None:
            continue
        x, y, z = (k[0] + 0.5) * VL.VS, (k[1] + 0.5) * VL.VS, (k[2] + 0.5) * VL.VS
        if y > neck_y + 0.3 and ((x - hx_) / (rx + 3.2)) ** 2 + ((y - hy) / (ry + 3.6)) ** 2 + ((z - hz) / (rz + 3.2)) ** 2 <= 1:
            g.cells[k] = (v[0], v[1], "head")
            continue
        for (h, name) in g.hands:
            if math.dist(h, (x, y, z)) < 2.0:
                g.cells[k] = (v[0], v[1], name)
                break


def build_one(bid):
    """모델 하나를 부위별로 만들어 ({부위: 요소 목록}, 팔레트, 실제 키, {부위: 관절 중심}) 로.
    부위 모델은 관절 중심이 (8,8,8) 에 오게 옮겨 두고, 관절 중심 좌표(발 y=0 · 가운데 x=z=8 기준)를 따로 돌려 준다."""
    import boss_models as bm
    old = VL.VS
    VL.VS = MOB_VS
    try:
        g = Grid()
        BUILDERS[bid](g)
        _assign(g)
        present = {v[2] for v in g.cells.values()}
        parts = ["body"] + [p for p in PART_ORDER[1:] if p in present and p in g.rig]
        for k, v in list(g.cells.items()):   # 관절 정보가 없는 부위는 몸통으로
            if v[2] is not None and v[2] not in parts:
                g.cells[k] = (v[0], v[1], None)
        pal = bm.Palette()
        bm.CLAMP_AT_BUILD[0] = False
        models = {}
        for p in parts:
            m = bm.Model(pal)
            emit(g, m, part=None if p == "body" else p)
            models[p] = m.els
    finally:
        VL.VS = old
        bm.CLAMP_AT_BUILD[0] = True
    alls = [e for els in models.values() for e in els]
    xs = [c for e in alls for c in (e["from"][0], e["to"][0])]
    ys = [c for e in alls for c in (e["from"][1], e["to"][1])]
    zs = [c for e in alls for c in (e["from"][2], e["to"][2])]
    y0, h = min(ys), max(ys) - min(ys)
    half = max(max(abs(v - 8) for v in xs), max(abs(v - 8) for v in zs))
    f = min(16.0 / h, 23.5 / half)
    piv = {"body": (8.0, y0, 8.0)}
    for p in parts[1:]:
        piv[p] = g.rig[p]
    for p, els in models.items():   # 관절 중심에서 너무 멀면 (±24 넘으면) 전체를 더 줄임
        P = piv[p]
        for e in els:
            for key in ("from", "to"):
                for i in range(3):
                    dev = abs(e[key][i] - P[i])
                    if dev > 0:
                        f = min(f, 23.8 / dev)
    norm_piv = {p: (8 + (P[0] - 8) * f, (P[1] - y0) * f, 8 + (P[2] - 8) * f) for p, P in piv.items()}
    for p, els in models.items():
        n = norm_piv[p]
        for e in els:
            for key in ("from", "to"):
                v = e[key]
                e[key] = [round(8 + (v[0] - 8) * f - (n[0] - 8), 4), round((v[1] - y0) * f - (n[1] - 8), 4), round(8 + (v[2] - 8) * f - (n[2] - 8), 4)]
            e.pop("_col", None)
            e.pop("_edge", None)
    return models, pal, round(h * f, 3), {p: tuple(round(c, 3) for c in n) for p, n in norm_piv.items()}, getattr(g, "kind", "solid")


def write(pack_dir, ns, write_json, plugin_res=None):
    """모델 · 텍스처를 쓰고 [(cmd, 모델 이름)] 반환. plugin_res 가 있으면 mob-models.yml (부위별 번호 · 관절 · 키 · 배율)도 씀"""
    import boss_models as bm
    tex_dir = os.path.join(pack_dir, "assets", ns, "textures", "item", "mob")
    os.makedirs(tex_dir, exist_ok=True)
    out, rows = [], []
    cmd, ucmd = CMD_BASE, UNDEAD_BASE
    for bid in ORDER:
        models, pal, h, piv, kind = build_one(bid)
        if len(pal.colors) > 256:
            raise SystemExit("몬스터 팔레트 색이 256 개를 넘음: %s" % bid)
        pal.image().save(os.path.join(tex_dir, bid + ".png"))
        upal = bm.Palette()
        upal.colors = [undead_color(c) for c in pal.colors]
        upal.image().save(os.path.join(tex_dir, bid + "_undead.png"))
        prow = []
        for p, els in models.items():
            name = bid if p == "body" else bid + "_" + p
            for e in els:
                if any(c < -16 or c > 32 for c in e["from"] + e["to"]):
                    raise SystemExit("몬스터 모델이 -16~32 를 넘음: %s" % name)
            model = {"credit": "RpgCraft mob model", "texture_size": [16, 16],
                     "textures": {"0": ns + ":item/mob/" + bid, "particle": ns + ":item/mob/" + bid},
                     "elements": els, "display": bm._display(1)}
            write_json(os.path.join(pack_dir, "assets", ns, "models", "mob", name + ".json"), model)
            out.append((cmd, ns + ":mob/" + name))
            umodel = dict(model, textures={"0": ns + ":item/mob/" + bid + "_undead", "particle": ns + ":item/mob/" + bid + "_undead"})
            write_json(os.path.join(pack_dir, "assets", ns, "models", "mob", name + "_undead.json"), umodel)
            out.append((ucmd, ns + ":mob/" + name + "_undead"))
            prow.append((p, cmd, piv[p], len(els), ucmd))
            cmd += 1
            ucmd += 1
        rows.append((bid, kind, h, SIZE.get(bid, 1.0), prow))
    if plugin_res:
        lines = ["# 자동 생성 (tools/mob_models.py) — 일반 몬스터 3D 모델", "# parts: 부위 → [CustomModelData, 관절 x, y, z, 언데드 CustomModelData] (모델 좌표: 발 y=0, 가운데 x=z=8, 1블록=16)", "models:"]
        for bid, kind, h, sz, prow in rows:
            lines.append("  %s:   # 요소 %d" % (bid, sum(x[3] for x in prow)))
            lines.append("    rig: %s" % kind)
            lines.append("    height: %s" % h)
            lines.append("    size: %s" % sz)
            lines.append("    parts:")
            for p, c, P, n, u in prow:
                lines.append("      %s: [%d, %s, %s, %s, %d]" % (p, c, P[0], P[1], P[2], u))
        with open(os.path.join(plugin_res, "mob-models.yml"), "w", encoding="utf-8") as fh:
            fh.write("\n".join(lines) + "\n")
    return out
