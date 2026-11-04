# -*- coding: utf-8 -*-
"""降着ジェット鍛造炉の形（JetForgeLayout.java と同じ定義）。部品の個数を数え、レシピの「マルチブロック組み立て」と説明に使う。

座標は ShapeSpec と同じ: x は横（ジェットの軸。0 が排熱側、増えるほど加工側）、y は高さ、z は奥行き（0 が手前）。
GameTest jetForgePartCountsMatchTools が Java の部品表とこの数を照らし合わせる。
"""

W, H, D = 44, 16, 17
CX, CY, CZ = 14, 0, 3
PLINTH_X0, PLINTH_X1, PLINTH_Z0, PLINTH_Z1 = 10, 18, 3, 13
AXIS_Y, AXIS_Z = 8, 8
CORE_X = 14
COIL_X, COIL_R = (11, 17), 7
LENS_X, LENS_R = (22, 27, 32), (5, 4, 3)
CAGE_X0, CAGE_X1, TARGET_X, CAGE_HALF = 35, 41, 38, 3
STOP_X = 43
SINK_X1, SINK_HALF = 2, 4


def ring(r):
    """半径 r の円を、辺でつながるように並べた点（Structures.RING_POINTS と同じ作り方）。"""
    import math
    pts = []
    for k in range(3600):
        a = 2 * math.pi * k / 3600
        # Java の Math.round と同じ丸め（0.5 は切り上げ）
        u, v = int(math.floor(r * math.cos(a) + 0.5)), int(math.floor(r * math.sin(a) + 0.5))
        if not pts or pts[-1] != (u, v):
            pts.append((u, v))
    if pts[0] == pts[-1]:
        pts.pop()
    out = []
    for i, p in enumerate(pts):
        q = pts[(i + 1) % len(pts)]
        out.append(p)
        if abs(p[0] - q[0]) == 1 and abs(p[1] - q[1]) == 1:
            c1, c2 = (q[0], p[1]), (p[0], q[1])
            e1 = abs(math.hypot(*c1) - r)
            e2 = abs(math.hypot(*c2) - r)
            out.append(c1 if e1 < e2 else c2)
    return set(out)


RINGS = {r: ring(r) for r in {COIL_R, *LENS_R}}


def slot(x, y, z):
    """(種類, 部品)。種類は part / panel / air / controller / ignore。"""
    dy, dz = y - AXIS_Y, z - AXIS_Z
    ady, adz = abs(dy), abs(dz)
    m = max(ady, adz)
    rr = dy * dy + dz * dz
    if (x, y, z) == (CX, CY, CZ):
        return 'controller', None
    if y == 0:
        corner = x in (PLINTH_X0, PLINTH_X1) and z in (PLINTH_Z0, PLINTH_Z1)
        if 7 <= z <= 9 or (PLINTH_X0 <= x <= PLINTH_X1 and PLINTH_Z0 <= z <= PLINTH_Z1 and not corner):
            return 'panel', 'foundation'
        return 'ignore', None
    if (x, y, z) == (CORE_X, AXIS_Y, AXIS_Z):
        return 'ignore', None
    # 排熱側: 奥の板と、ジェットを受ける杯の壁
    if x <= SINK_X1:
        if m > SINK_HALF or (ady == SINK_HALF and adz == SINK_HALF):
            return ('part', 'truss') if adz <= 1 and dy < -SINK_HALF else ('ignore', None)
        if x == 0 or m == SINK_HALF:
            return 'part', 'heat_sink'
        return 'air', None
    # 炉心: 2つの拘束コイルと、その間を結ぶ4本の梁
    if x in COIL_X and (dy, dz) in RINGS[COIL_R]:
        return 'part', 'stabilizer' if ady == adz else 'confinement_coil'
    if COIL_X[0] < x < COIL_X[1] and (dy, dz) in {(COIL_R, 0), (-COIL_R, 0), (0, COIL_R), (0, -COIL_R)}:
        return 'part', 'truss'
    if (COIL_X[0] <= x <= COIL_X[1] and rr <= 25) or (x - CORE_X) ** 2 + rr <= 30:
        return 'air', None
    # 収束リング（だんだん細く）と、それを支える柱
    for lx, lr in zip(LENS_X, LENS_R):
        if x == lx:
            if (dy, dz) in RINGS[lr]:
                return 'part', 'collimator'
            if dz == 0 and dy < -lr:
                return 'part', 'truss'
    # 照射室: 両端の四角い枠・四隅の桁・上の浮揚器と下の回収口
    if CAGE_X0 <= x <= CAGE_X1:
        if x in (CAGE_X0, CAGE_X1):
            if m == CAGE_HALF:
                return 'part', 'truss'
            if m < CAGE_HALF:
                return 'air', None
            if dz == 0 and dy < -CAGE_HALF:
                return 'part', 'truss'
            return 'ignore', None
        if ady == CAGE_HALF and adz == CAGE_HALF:
            return 'part', 'truss'
        if x == TARGET_X and ady == CAGE_HALF and adz < CAGE_HALF:
            if dz == 0:
                return 'part', 'levitator' if dy > 0 else 'collector'
            return 'part', 'truss'
        if x == TARGET_X and dz == 0 and dy < -CAGE_HALF:
            return 'part', 'truss'
        if m < CAGE_HALF:
            return 'air', None
        return 'ignore', None
    # 後ろのビーム止め
    if x == STOP_X:
        if ady <= 2 and adz <= 2:
            return 'part', 'heat_sink'
        if adz <= 1 and dy < -2:
            return 'part', 'truss'
        return 'ignore', None
    # ビームの通り道
    if 3 <= x <= 42 and ady <= 1 and adz <= 1:
        return 'air', None
    return 'ignore', None


def counts():
    out = {}
    air = 0
    for x in range(W):
        for y in range(H):
            for z in range(D):
                kind, part = slot(x, y, z)
                if part:
                    out[part] = out.get(part, 0) + 1
                elif kind == 'air':
                    air += 1
    return out, air


if __name__ == '__main__':
    c, air = counts()
    print(c, 'total', sum(c.values()), 'air', air)
