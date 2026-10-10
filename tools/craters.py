# -*- coding: utf-8 -*-
"""隕石クレーター（地表の小さな構造物）の .nbt を作る。gen_data.py から呼ぶ。

巨大な穴ではなく、差し渡し10〜12ブロックほどの浅いすり鉢。自然に見えるよう、
- 縁は角度ごとに半径が揺らぎ（きれいな円にしない）、外へ向かってなだらかに盛り上がって消える
- すり鉢の底は中心ほど焼けて溶けた岩（滑らかな玄武岩・ブラックストーン）、外側は砂利と荒れた土
- 縁の外には、飛び散った土や岩がまばらに散る（遠いほど少ない）
- 中心には、少し傾いて半分埋まった隕石。黒い溶融殻に包まれ、ところどころ隕鉄が顔を出し、表面に星屑の結晶が育つ
- まだ冷めきっていない所（マグマブロック）が底に1〜2か所
地面の高さ（テンプレートの y=SURFACE）より上の空気と、すり鉢の中の空気は置き、ほかの何も置かないマスは元の地形のまま残す。
"""
import math
import random

from ruins import Builder

MODID = 'singulo'
SIZE = 23
HEIGHT = 10
SURFACE = 5          # テンプレートの、元の地面のすぐ上（最初の空気）の段
C = SIZE // 2

CRUST = (f'{MODID}:meteorite_crust', {'natural': 'true'})
IRON = f'{MODID}:meteoric_iron_chunk'
CLUSTER = f'{MODID}:stardust_cluster'
AIR = 'minecraft:air'

# 種ごとの形（乱数の種, 基本の半径, 隕石の大きさ）
VARIANTS = {
    'meteor_crater_a': (7101, 4.8, 1.9),
    'meteor_crater_b': (7102, 5.3, 2.2),
    'meteor_crater_c': (7103, 5.8, 2.0),
}


def pick(rnd, table):
    """(名前, 重み) の表から1つ選ぶ。"""
    total = sum(w for _, w in table)
    r = rnd.uniform(0, total)
    for name, w in table:
        r -= w
        if r <= 0:
            return name
    return table[-1][0]


def crater(seed, base_radius, meteor_size):
    rnd = random.Random(seed)
    b = Builder(SIZE, HEIGHT, SIZE, seed=seed)
    ph = [rnd.uniform(0, 2 * math.pi) for _ in range(3)]

    def radius(theta):
        return base_radius + 0.7 * math.sin(3 * theta + ph[0]) + 0.4 * math.sin(5 * theta + ph[1]) + 0.25 * math.sin(7 * theta + ph[2])

    floor = {}
    for x in range(SIZE):
        for z in range(SIZE):
            dx, dz = x - C, z - C
            d = math.hypot(dx, dz)
            r = radius(math.atan2(dz, dx))
            if d < r:
                # すり鉢: 中心ほど深い（放物線）。底の上はすべて空気（草や木も消える）
                depth = 3.0 * (1 - (d / r) ** 2) + 0.4 + rnd.uniform(-0.25, 0.25)
                top = SURFACE - 1 - int(round(depth))
                floor[(x, z)] = top
                for y in range(top + 1, HEIGHT):
                    b.set(x, y, z, AIR)
                k = d / r
                if k < 0.35:
                    mat = pick(rnd, [('minecraft:smooth_basalt', 5), ('minecraft:blackstone', 3), ('minecraft:basalt', 1),
                                     ('minecraft:tuff', 1)])
                elif k < 0.7:
                    mat = pick(rnd, [('minecraft:tuff', 3), ('minecraft:gravel', 3), ('minecraft:coarse_dirt', 2),
                                     ('minecraft:blackstone', 1)])
                else:
                    mat = pick(rnd, [('minecraft:coarse_dirt', 4), ('minecraft:gravel', 3), ('minecraft:dirt', 2),
                                     ('minecraft:tuff', 1)])
                b.set(x, top, z, mat)
                # 底の下を岩でふさぐ（洞窟とつながって抜けないように）
                b.set(x, top - 1, z, pick(rnd, [('minecraft:tuff', 2), ('minecraft:stone', 3), ('minecraft:cobbled_deepslate', 1)]))
                if top - 2 >= 0:
                    b.set(x, top - 2, z, 'minecraft:stone')
            elif d < r + 2.4:
                # 縁: 外へ向かってなだらかに下がる盛り上がり。上の方は少し草が戻っている
                h = 1.7 * (1 - (d - r) / 2.4) + rnd.uniform(-0.3, 0.3)
                n = int(round(h))
                for i in range(n):
                    y = SURFACE + i
                    top = i == n - 1
                    if top and rnd.random() < 0.35:
                        b.set(x, y, z, 'minecraft:grass_block', {'snowy': 'false'})
                    else:
                        b.set(x, y, z, pick(rnd, [('minecraft:coarse_dirt', 4), ('minecraft:rooted_dirt', 1), ('minecraft:gravel', 2),
                                                 ('minecraft:dirt', 2), ('minecraft:tuff', 1)]))
                for y in range(SURFACE + n, HEIGHT):
                    b.set(x, y, z, AIR)
            elif d < r + 4.5:
                # 飛び散った土や岩（遠いほど少ない）。地面の一番上だけを替え、ときどき小さな岩が転がる
                p = 0.55 * (1 - (d - r - 2.4) / 2.1)
                if rnd.random() < p:
                    b.set(x, SURFACE - 1, z, pick(rnd, [('minecraft:coarse_dirt', 4), ('minecraft:gravel', 3), ('minecraft:tuff', 1)]))
                if rnd.random() < p * 0.12:
                    b.set(x, SURFACE, z, pick(rnd, [('minecraft:tuff', 2), ('minecraft:cobbled_deepslate', 2), ('minecraft:blackstone', 1)]))

    # 隕石: 少し中心からずれ、傾いて半分埋まっている。外側は溶融殻、中は隕鉄が多い
    ox, oz = rnd.uniform(-0.6, 0.6), rnd.uniform(-0.6, 0.6)
    cx, cz = C + ox, C + oz
    base = floor.get((C, C), SURFACE - 4)
    cy = base + 0.7
    rx, ry, rz = meteor_size * 1.1, meteor_size * 0.85, meteor_size * 0.95
    tilt = rnd.uniform(-0.35, 0.35)
    shell = []
    for x in range(int(cx - rx - 2), int(cx + rx + 3)):
        for z in range(int(cz - rz - 2), int(cz + rz + 3)):
            for y in range(int(cy - ry - 2), int(cy + ry + 3)):
                px, py, pz = x + 0.5 - cx, y + 0.5 - cy, z + 0.5 - cz
                # 傾き（x と y を混ぜる）とでこぼこ
                qx, qy = px * math.cos(tilt) - py * math.sin(tilt), px * math.sin(tilt) + py * math.cos(tilt)
                bump = 0.18 * math.sin(3 * qx + seed) * math.cos(2 * pz + seed * 0.3)
                e = (qx / rx) ** 2 + (qy / ry) ** 2 + (pz / rz) ** 2 - bump
                if e <= 1.0:
                    outer = e > 0.45
                    if outer:
                        block = IRON if rnd.random() < 0.22 else CRUST
                    else:
                        block = IRON if rnd.random() < 0.6 else CRUST
                    if isinstance(block, tuple):
                        b.set(x, y, z, *block)
                        if outer:
                            shell.append((x, y, z))
                    else:
                        b.set(x, y, z, block)
    # 殻の表面に育った星屑の結晶（上向きと横向き）
    rnd.shuffle(shell)
    placed = 0
    for x, y, z in shell:
        for (dx, dy, dz, facing) in ((0, 1, 0, 'up'), (1, 0, 0, 'east'), (-1, 0, 0, 'west'), (0, 0, 1, 'south'), (0, 0, -1, 'north')):
            if placed >= 6:
                break
            nx, ny, nz = x + dx, y + dy, z + dz
            if b.get(nx, ny, nz) == AIR and rnd.random() < 0.35:
                b.set(nx, ny, nz, CLUSTER, {'facing': facing, 'waterlogged': 'false'})
                placed += 1
    # まだ熱い所（隕石のすぐ脇の底）と、飛び散った小さな欠片
    spots = [(x, z) for (x, z), top in floor.items() if 1.5 < math.hypot(x - cx, z - cz) < 3.2]
    rnd.shuffle(spots)
    for x, z in spots[:2]:
        b.set(x, floor[(x, z)], z, 'minecraft:magma_block')
    for x, z in spots[2:5]:
        top = floor[(x, z)]
        if b.get(x, top + 1, z) == AIR:
            if rnd.random() < 0.5:
                b.set(x, top + 1, z, IRON)
            else:
                b.set(x, top + 1, z, *CRUST)
    return b


def write_all(structure_dir):
    import nbt
    out = {}
    for name, (seed, r, size) in VARIANTS.items():
        b = crater(seed, r, size)
        nbt.write_gzip(structure_dir / f'{name}.nbt', b.to_nbt())
        out[name] = b
    return out
