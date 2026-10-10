# -*- coding: utf-8 -*-
"""旧文明の遺構4種の構造物（.nbt）を作る。gen_data.py から呼ぶ。

どれも白い無機質パネルの建物が黄ばみ・ひび割れ・植物に侵食された姿にする（設計書「コンセプトと世界観」）。
地上に建つのは入口となる地表観測拠点だけで、ほかの3つは地下に埋もれている（深いほど後の段階）。
崩れ方は乱数の種を固定しているので、毎回同じ形になる。
"""
import math
import random

import nbt

DATA_VERSION = 3955  # 1.21.1
MODID = 'singulo'

PANEL = f'{MODID}:ruin_panel'
CRACKED = f'{MODID}:cracked_ruin_panel'
MOSSY = f'{MODID}:mossy_ruin_panel'
# 朽ちた変わり種と、深い遺構に残る朽ちていない白い建材
WEATHERED_VARIANTS = tuple(f'{MODID}:{v}' for v in ('tiled_ruin_panel', 'vented_ruin_panel', 'striped_ruin_panel',
                                                      'scorched_ruin_panel'))
PRISTINE_VARIANTS = tuple(f'{MODID}:{v}' for v in ('pristine_ruin_panel', 'pristine_ruin_tiles'))
PRISTINE_PILLAR = f'{MODID}:pristine_ruin_pillar'
PRISTINE_LIGHT = f'{MODID}:pristine_ruin_light'
GLASS = f'{MODID}:intact_ruin_glass'
BROKEN_GLASS = f'{MODID}:ruin_glass'
LAMP = f'{MODID}:ruin_lamp'
CACHE = f'{MODID}:ruin_cache'
DOCK = f'{MODID}:ruin_guard_dock'
CONSOLE = f'{MODID}:seal_console'
CORE = f'{MODID}:guardian_core'
PROJECTOR = f'{MODID}:echo_projector'
STRIPED = f'{MODID}:striped_ruin_panel'
AIR = 'minecraft:air'
# 骨組み・装置の暗い部分
FRAME = 'minecraft:polished_deepslate'
DARK = 'minecraft:deepslate_tiles'
SCREEN = 'minecraft:tinted_glass'
CHAIN_Y = ('minecraft:chain', {'axis': 'y', 'waterlogged': 'false'})


class Builder:
    def __init__(self, sx, sy, sz, seed):
        self.size = (sx, sy, sz)
        self.blocks = {}
        self.rng = random.Random(seed)

    def set(self, x, y, z, name, props=None, data=None):
        if 0 <= x < self.size[0] and 0 <= y < self.size[1] and 0 <= z < self.size[2]:
            self.blocks[(x, y, z)] = (name, props or {}, data)

    def get(self, x, y, z):
        b = self.blocks.get((x, y, z))
        return b[0] if b else None

    def fill(self, x0, y0, z0, x1, y1, z1, name, props=None):
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    self.set(x, y, z, name, props)

    def box(self, x0, y0, z0, x1, y1, z1, wall, inside=AIR):
        """壁・床・天井を wall、中を inside で埋めた箱。"""
        self.fill(x0, y0, z0, x1, y1, z1, wall)
        if x1 - x0 >= 2 and y1 - y0 >= 2 and z1 - z0 >= 2:
            self.fill(x0 + 1, y0 + 1, z0 + 1, x1 - 1, y1 - 1, z1 - 1, inside)

    def each(self, test, y0, y1, name, props=None):
        """y0〜y1 の各段で test(x, y, z) が真のマスを埋める。"""
        for x in range(self.size[0]):
            for z in range(self.size[2]):
                for y in range(y0, y1 + 1):
                    if test(x, y, z):
                        self.set(x, y, z, name, props)

    def decay(self, cracked=0.15, mossy=0.1, missing=0.0, only_above=0):
        """パネルを確率でひび割れ・苔むし・欠けにする。"""
        for (x, y, z), (name, props, data) in list(self.blocks.items()):
            if name != PANEL or y < only_above:
                continue
            r = self.rng.random()
            if r < missing:
                self.blocks[(x, y, z)] = (AIR, {}, None)
            elif r < missing + cracked:
                self.blocks[(x, y, z)] = (CRACKED, {}, None)
            elif r < missing + cracked + mossy:
                self.blocks[(x, y, z)] = (MOSSY, {}, None)

    def vary(self, seed, weathered, pristine):
        """残ったパネルの一部を、朽ちた変わり種（weathered の割合）と、朽ちていない白い建材（pristine の割合）に替える。
        崩れ方（decay）とは別の乱数を使うので、ほかの置き方は変わらない。"""
        rnd = random.Random(seed)
        for (x, y, z), (name, props, data) in sorted(self.blocks.items()):
            if name != PANEL:
                continue
            r = rnd.random()
            if r < weathered:
                self.blocks[(x, y, z)] = (rnd.choice(WEATHERED_VARIANTS), {}, None)
            elif r < weathered + pristine:
                self.blocks[(x, y, z)] = (rnd.choice(PRISTINE_VARIANTS), {}, None)

    def break_glass(self, chance):
        """割れて抜けたガラス（chance の割合）と、ひびが入ったまま残ったガラス（その倍くらい）。"""
        for (x, y, z), (name, props, data) in list(self.blocks.items()):
            if name != GLASS:
                continue
            r = self.rng.random()
            if r < chance:
                self.blocks[(x, y, z)] = (AIR, {}, None)
            elif r < chance * 3:
                self.blocks[(x, y, z)] = (BROKEN_GLASS, {}, None)

    def scatter(self, name, count, y, area, props=None, on_air=True, floor=None):
        """y の段に count 個ばらまく（苔のじゅうたんなど）。floor を与えると、その下が floor のマスだけ。"""
        x0, z0, x1, z1 = area
        for _ in range(count):
            x, z = self.rng.randint(x0, x1), self.rng.randint(z0, z1)
            if on_air and self.get(x, y, z) not in (None, AIR):
                continue
            if floor and self.get(x, y - 1, z) not in floor:
                continue
            self.set(x, y, z, name, props)

    def cache(self, x, y, z, ruin, sealed=False):
        self.set(x, y, z, CACHE, {'sealed': 'true' if sealed else 'false'}, {'ruin': ruin})

    def dock(self, x, y, z, tier):
        self.set(x, y, z, DOCK, None, {'tier': tier})

    def sealed(self, x, y, z, tier):
        """封印コンテナ（次の段階の鍵で開く。中身は初めて開いたときに loot table singulo:sealed/tier_N から入る）。"""
        self.set(x, y, z, f'{MODID}:sealed_container_{tier}', None, {'Loot': 1})

    def to_nbt(self):
        palette, index, blocks = [], {}, []
        for (x, y, z), (name, props, data) in sorted(self.blocks.items()):
            key = (name, tuple(sorted(props.items())))
            if key not in index:
                index[key] = len(palette)
                entry = {'Name': name}
                if props:
                    entry['Properties'] = dict(props)
                palette.append(entry)
            block = {'pos': [x, y, z], 'state': index[key]}
            if data:
                block['nbt'] = dict(data)
            blocks.append(block)
        return {'DataVersion': DATA_VERSION, 'size': list(self.size), 'palette': palette,
                'blocks': blocks, 'entities': []}


def octagon(dx, dz, r):
    """中心からの差 (dx, dz) が、半径 r の八角形の中か。"""
    return max(abs(dx), abs(dz)) <= r and abs(dx) + abs(dz) <= r * 1.42


def vines_on(b, x, y0, y1, z, side):
    """壁に垂れるツタ。side はツタが張り付く壁の方向。"""
    for y in range(y0, y1 + 1):
        if b.rng.random() < 0.6 and b.get(x, y, z) in (None, AIR):
            b.set(x, y, z, 'minecraft:vine', {side: 'true'})


def tank(b, x, z, y0, y1, glass, top=LAMP, bottom=FRAME):
    """縦長の培養槽・標本槽: 台座・色ガラスの筒・光る蓋。"""
    b.set(x, y0, z, bottom)
    b.fill(x, y0 + 1, z, x, y1 - 1, z, glass)
    b.set(x, y1, z, top)


# ---------------------------------------------------------------- 4種類の遺構

def observation_post():
    """
    地表観測拠点（地上）: 八角形の観測所。ガラスのドームから旧式の望遠鏡が空へ突き出し、屋根には折れかけた
    アンテナ塔と太陽光パネルが並ぶ。中には計器の並ぶ観測室。
    """
    b = Builder(19, 19, 19, seed=101)
    c = 9
    # 基壇と床（外周は一段低い縁）
    b.each(lambda x, y, z: octagon(x - c, z - c, 8), 0, 0, PANEL)
    b.each(lambda x, y, z: octagon(x - c, z - c, 9) and not octagon(x - c, z - c, 8), 0, 0,
           'minecraft:smooth_stone_slab', {'type': 'bottom', 'waterlogged': 'false'})
    # 外壁（窓の帯は2〜3段目）と天井
    ring = lambda x, z: octagon(x - c, z - c, 8) and not octagon(x - c, z - c, 7)
    b.each(lambda x, y, z: ring(x, z), 1, 5, PANEL)
    b.each(lambda x, y, z: ring(x, z) and (x + z) % 3 != 0, 2, 3, GLASS)
    b.each(lambda x, y, z: ring(x, z) and (x + z) % 3 == 0, 4, 4, LAMP)
    b.each(lambda x, y, z: octagon(x - c, z - c, 7), 1, 5, AIR)
    b.each(lambda x, y, z: octagon(x - c, z - c, 8), 6, 6, PANEL)
    # 屋上の手すり（低い縁）
    b.each(lambda x, y, z: ring(x, z) and (x + z) % 2 == 0, 7, 7, PANEL)
    # 観測ドーム（半径5のガラス、縦のリブはパネル）。下の観測室と吹き抜け
    for x in range(19):
        for z in range(19):
            for y in range(6, 13):
                d = math.sqrt((x - c) ** 2 + (z - c) ** 2 + ((y - 6) * 1.1) ** 2)
                if 4.3 <= d <= 5.3:
                    rib = x == c or z == c or abs(x - c) == abs(z - c)
                    b.set(x, y, z, PANEL if rib else GLASS)
                elif d < 4.3:
                    b.set(x, y, z, AIR)
    b.set(c, 12, c, LAMP)
    # 望遠鏡: 台座から北の空へ斜めに伸びる筒。ドームを突き破っている
    b.fill(c, 1, c, c, 3, c, FRAME)
    b.set(c, 4, c, LAMP)
    for i, (y, z) in enumerate([(5, c), (6, c - 1), (7, c - 2), (8, c - 3), (9, c - 4), (10, c - 5), (11, c - 6)]):
        b.set(c, y, z, DARK if i % 3 else FRAME)
        if 1 <= i <= 4:
            b.set(c - 1, y, z, FRAME)
            b.set(c + 1, y, z, FRAME)
    b.set(c, 12, c - 7, 'minecraft:end_rod', {'facing': 'north'})
    # アンテナ塔（北西の屋上）: 鎖の柱と横木、先端は避雷針
    mx, mz = c - 5, c - 5
    b.set(mx, 7, mz, FRAME)
    b.fill(mx, 8, mz, mx, 16, mz, *CHAIN_Y)
    for y in (11, 14):
        b.set(mx, y, mz, FRAME)
        for dx, dz, f in ((-1, 0, 'west'), (1, 0, 'east'), (0, -1, 'north'), (0, 1, 'south')):
            b.set(mx + dx, y, mz + dz, 'minecraft:end_rod', {'facing': f})
    b.set(mx, 17, mz, 'minecraft:lightning_rod', {'facing': 'up', 'powered': 'false', 'waterlogged': 'false'})
    # 太陽光パネル（東の屋上）
    for x in range(c + 3, c + 8):
        for z in range(c - 3, c + 4):
            if octagon(x - c, z - c, 7) and (z - c) % 3 != 2:
                b.set(x, 7, z, 'minecraft:daylight_detector', {'inverted': 'false', 'power': '0'})
    # 南の入口と階段
    b.fill(c - 1, 1, c + 8, c + 1, 3, c + 8, AIR)
    b.fill(c - 1, 4, c + 8, c + 1, 4, c + 8, FRAME)
    b.fill(c - 1, 0, c + 9, c + 1, 0, c + 9, 'minecraft:smooth_stone_slab', {'type': 'bottom', 'waterlogged': 'false'})
    # 観測室: 北の壁ぎわの計器盤（画面は黒いガラス）、西の記録棚、東の作業台
    for x in range(c - 3, c + 4):
        b.set(x, 1, c - 6, PANEL)
        b.set(x, 2, c - 6, SCREEN if x % 2 else LAMP)
        b.set(x, 1, c - 5, 'minecraft:smooth_quartz_slab', {'type': 'bottom', 'waterlogged': 'false'})
    for z in range(c - 2, c + 3):
        b.set(c - 6, 1, z, FRAME)
        b.set(c - 6, 2, z, 'minecraft:chiseled_bookshelf' if z % 2 else FRAME,
              {'facing': 'east', 'slot_0_occupied': 'false', 'slot_1_occupied': 'false', 'slot_2_occupied': 'false',
               'slot_3_occupied': 'false', 'slot_4_occupied': 'false', 'slot_5_occupied': 'false'} if z % 2 else None)
    b.cache(c + 5, 1, c, 'observation_post')
    b.set(c + 5, 1, c + 2, 'minecraft:smooth_quartz_slab', {'type': 'bottom', 'waterlogged': 'false'})
    b.dock(c - 4, 1, c + 4, 1)
    b.sealed(c - 5, 1, c - 1, 1)
    # 朽ちた跡
    b.decay(cracked=0.2, mossy=0.15)
    for (x, y, z), blk in list(b.blocks.items()):         # 屋根の穴
        if y == 6 and blk[0] in (PANEL, CRACKED, MOSSY) and b.rng.random() < 0.18 and not octagon(x - c, z - c, 5):
            b.blocks[(x, y, z)] = (AIR, {}, None)
    b.break_glass(0.15)
    b.scatter('minecraft:moss_carpet', 14, 1, (c - 7, c - 7, c + 7, c + 7), floor=(PANEL, CRACKED, MOSSY))
    b.scatter('minecraft:moss_carpet', 10, 7, (c - 7, c - 7, c + 7, c + 7), floor=(PANEL, CRACKED, MOSSY))
    for z in range(c - 2, c + 3, 2):
        vines_on(b, c - 9, 1, 5, z, 'east')
        vines_on(b, c + 9, 1, 5, z, 'west')
    return b


def research_building():
    """
    研究棟（地下）: 埋もれた2階建ての研究施設。吹き抜けの中央には古い実験装置のリング、西の棟には標本槽、
    東の棟には記録装置の棚が並ぶ。2階の北東は電子ロックの扉で閉ざされた端末室。
    東の通路の先は記録保管室（ボス部屋）: 残響の番人が記録を守り、保管庫は奥の壇の上で力場に封鎖されている。
    """
    b = Builder(51, 15, 23, seed=202)
    X, Z = 26, 22
    b.box(0, 0, 0, X, 14, Z, PANEL)
    # 2階の床（中央は吹き抜け）
    b.fill(1, 7, 1, X - 1, 7, Z - 1, PANEL)
    b.fill(9, 7, 7, 17, 7, 15, AIR)
    # 吹き抜けの縁の柱と、2階の手すり（低い板）
    for x, z in ((8, 6), (18, 6), (8, 16), (18, 16)):
        b.fill(x, 1, z, x, 13, z, FRAME)
        b.set(x, 6, z, LAMP)
        b.set(x, 13, z, LAMP)
    for x in range(9, 18):
        for z in (6, 16):
            b.set(x, 8, z, 'minecraft:smooth_quartz_slab', {'type': 'bottom', 'waterlogged': 'false'})
    for z in range(7, 16):
        for x in (8, 18):
            b.set(x, 8, z, 'minecraft:smooth_quartz_slab', {'type': 'bottom', 'waterlogged': 'false'})
    # 1階の仕切り（ガラス張りの実験室）
    for x in (7, 19):
        b.fill(x, 1, 1, x, 6, Z - 1, PANEL)
        b.fill(x, 2, 2, x, 5, Z - 2, GLASS)
        b.fill(x, 1, 10, x, 3, 12, AIR)                     # 出入口
    # 西の棟: 標本槽（色ガラスの筒）
    for z in (3, 7, 11, 15, 19):
        for x in (2, 5):
            tank(b, x, z, 1, 5, 'minecraft:light_blue_stained_glass' if z % 8 == 3 else 'minecraft:cyan_stained_glass')
    # 東の棟: 記録装置の棚（パネルと光る段が交互）
    for z in range(2, Z - 1, 3):
        for x in range(21, 25):
            for y in range(1, 6):
                b.set(x, y, z, LAMP if (y == 3 and x % 2 == 0) else (DARK if y % 2 else FRAME))
    # 中央の実験装置: 床のリングと、天井まで伸びる鎖とエンドロッドの軸
    cx, cz = 13, 11
    for x in range(cx - 4, cx + 5):
        for z in range(cz - 4, cz + 5):
            d = math.hypot(x - cx, z - cz)
            if 2.6 <= d <= 3.6:
                b.set(x, 1, z, FRAME)
                b.set(x, 2, z, LAMP if (x + z) % 2 == 0 else DARK)
    b.set(cx, 1, cz, LAMP)
    b.fill(cx, 2, cz, cx, 4, cz, 'minecraft:end_rod', {'facing': 'up'})
    b.fill(cx, 5, cz, cx, 13, cz, *CHAIN_Y)
    # 階段（南の壁ぎわ、西から東へ上る）と、2階の床の穴
    for i in range(6):
        for z in (Z - 2, Z - 1):
            b.set(10 + i, 1 + i, z, 'minecraft:quartz_stairs', {'facing': 'east', 'half': 'bottom', 'shape': 'straight',
                                                                 'waterlogged': 'false'})
            b.fill(10 + i, 2 + i, z, 10 + i, 6 + i if i < 5 else 7, z, AIR)
    b.fill(10, 7, Z - 2, 15, 7, Z - 1, AIR)
    # 2階: 北東の保管室（電子ロック。ボタンは内側だけ）
    b.fill(17, 8, 1, 17, 13, 7, PANEL)
    b.fill(17, 8, 7, X - 1, 13, 7, PANEL)
    b.set(21, 8, 7, 'minecraft:iron_door', {'facing': 'south', 'half': 'lower', 'hinge': 'left', 'open': 'false', 'powered': 'false'})
    b.set(21, 9, 7, 'minecraft:iron_door', {'facing': 'south', 'half': 'upper', 'hinge': 'left', 'open': 'false', 'powered': 'false'})
    b.set(22, 9, 6, 'minecraft:stone_button', {'face': 'wall', 'facing': 'north', 'powered': 'false'})
    b.set(23, 8, 3, FRAME)                                    # 保管室だった端末室（中身は記録保管室へ移された）
    b.set(23, 9, 3, SCREEN)
    b.set(24, 8, 2, LAMP)
    b.set(19, 8, 2, FRAME)
    b.set(19, 9, 2, SCREEN)
    # 2階: 南西の事務区画（机と画面）
    for x in range(2, 7):
        for z in (14, 18):
            b.set(x, 8, z, 'minecraft:smooth_quartz_slab', {'type': 'top', 'waterlogged': 'false'})
            b.set(x, 9, z, SCREEN if x % 2 else AIR)
    # 照明（天井）
    for x in range(3, X, 5):
        for z in range(3, Z, 5):
            b.set(x, 6, z, LAMP)
            b.set(x, 14, z, LAMP)
    b.dock(13, 1, 4, 2)
    b.sealed(4, 8, 16, 2)
    b.dock(22, 8, 14, 2)
    archive_hall(b, X)
    b.decay(cracked=0.2, mossy=0.08)
    b.break_glass(0.2)
    b.scatter('minecraft:cobweb', 12, 6, (1, 1, X - 1, Z - 1))
    b.scatter('minecraft:cobweb', 10, 13, (1, 1, X - 1, Z - 1))
    b.scatter('minecraft:moss_carpet', 10, 1, (1, 1, X - 1, Z - 1))
    b.scatter('minecraft:cobweb', 8, 11, (33, 2, 49, 20))
    return b


def archive_hall(b, X):
    """
    研究棟の記録保管室（ボス部屋）。東の棟の壁から短い通路を抜けた先の、天井の高い広間。
    南北の壁には記録結晶の棚が天井近くまで積まれ、床の光の環の中心に番人の封印核が埋まっている。
    四隅の台座の残響投影器が番人の姿を映す。東の壇の上が保管庫（番人を倒すまで力場で封鎖）。
    """
    # 東の棟の壁の出入口と通路（記録棚の列の間）
    b.fill(X, 1, 9, X, 3, 10, AIR)
    b.box(X, 0, 8, 32, 4, 11, PANEL)
    b.fill(X, 1, 9, 32, 3, 10, AIR)
    for x in (28, 31):
        b.set(x, 4, 9, LAMP)
    # 広間（内側 x33〜49・z2〜20・高さ1〜11）
    x0, x1, z0, z1, top = 32, 50, 1, 21, 12
    b.box(x0, 0, z0, x1, top, z1, PANEL)
    b.fill(x0, 1, 9, x0, 3, 10, AIR)
    cx, cz = 41, 11
    # 床: 暗い帯の環と光の点、中心に封印核
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            d = math.hypot(x - cx, z - cz)
            if 5.6 <= d <= 6.6:
                b.set(x, 0, z, LAMP if (x + z) % 3 == 0 else FRAME)
            elif 2.4 <= d <= 3.1:
                b.set(x, 0, z, DARK)
    b.set(cx, 0, cz, CORE)
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        b.set(cx + dx, 0, cz + dz, FRAME)
    # 四隅の投影器（暗い台座の上）
    for dx in (-6, 6):
        for dz in (-6, 6):
            b.set(cx + dx, 1, cz + dz, FRAME)
            b.set(cx + dx, 2, cz + dz, PROJECTOR)
    # 南北の壁: 記録結晶の棚（暗い枠に黒いガラスの段、ところどころ光る）。天井近くまで
    for z, side in ((z0 + 1, 1), (z1 - 1, -1)):
        for x in range(x0 + 2, x1 - 1):
            if x in (cx - 1, cx, cx + 1):
                continue
            for y in range(1, 10):
                if x % 3 == 0:
                    b.set(x, y, z, FRAME)
                else:
                    b.set(x, y, z, LAMP if (y + x) % 7 == 0 else (SCREEN if y % 2 else DARK))
            b.set(x, 10, z, PANEL)
        # 棚の前の通路の手すり（低い板）
        for x in range(x0 + 2, x1 - 1, 2):
            b.set(x, 1, z + side, 'minecraft:smooth_quartz_slab', {'type': 'bottom', 'waterlogged': 'false'})
        # 棚の真ん中: 割れた展示ケース（記録結晶の代わりにエンドロッド）
        for x in (cx - 1, cx, cx + 1):
            b.set(x, 1, z, FRAME)
            b.set(x, 2, z, GLASS)
            b.set(x, 3, z, GLASS)
            b.set(x, 4, z, LAMP)
        b.set(cx, 2, z, 'minecraft:end_rod', {'facing': 'up'})
    # 天井: 光の環と、垂れ下がる鎖の束
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            d = math.hypot(x - cx, z - cz)
            if 4.5 <= d <= 5.3 and (x + z) % 2 == 0:
                b.set(x, top - 1, z, LAMP)
    for dx, dz in ((-3, 0), (3, 0), (0, -3), (0, 3)):
        b.fill(cx + dx, 7, cz + dz, cx + dx, top - 1, cz + dz, *CHAIN_Y)
        b.set(cx + dx, 6, cz + dz, 'minecraft:end_rod', {'facing': 'down'})
    # 東の壇と保管庫（力場で封鎖）。壇の左右は記録装置の柱
    b.fill(x1 - 4, 1, cz - 3, x1 - 1, 1, cz + 3, PANEL)
    b.fill(x1 - 2, 2, cz - 2, x1 - 1, 2, cz + 2, PANEL)
    for dz in range(-3, 4):
        b.set(x1 - 5, 1, cz + dz, 'minecraft:quartz_stairs', {'facing': 'east', 'half': 'bottom', 'shape': 'straight',
                                                                'waterlogged': 'false'})
    for dz in range(-2, 3):
        b.set(x1 - 3, 2, cz + dz, 'minecraft:quartz_stairs', {'facing': 'east', 'half': 'bottom', 'shape': 'straight',
                                                                'waterlogged': 'false'})
    b.cache(x1 - 2, 3, cz, 'research_building', sealed=True)
    for dz in (-3, 3):
        b.fill(x1 - 2, 2, cz + dz, x1 - 2, 8, cz + dz, PRISTINE_PILLAR)
        b.set(x1 - 2, 5, cz + dz, PRISTINE_LIGHT)
    b.fill(x1 - 1, 3, cz - 1, x1 - 1, 7, cz + 1, SCREEN)
    # 床の照明
    for x in range(x0 + 3, x1 - 4, 5):
        for z in (z0 + 4, z1 - 4):
            b.set(x, 0, z, LAMP)


def culture_facility():
    """
    封鎖培養施設（深い地下）: 円い密閉施設。中心は青氷の極低温コア、まわりの4つの培養室は
    割れた培養槽からあふれた植物に覆われている。南東の培養室から通路を抜けた先は隔離槽室（ボス部屋）:
    重力の澱が巨大な封じ込め槽に眠り、保管庫は奥で力場に封鎖されている。
    """
    b = Builder(57, 13, 29, seed=303)
    c = 14
    disk = lambda x, z, r: math.hypot(x - c, z - c) <= r
    b.each(lambda x, y, z: disk(x, z, 14), 0, 0, PANEL)
    b.each(lambda x, y, z: disk(x, z, 14) and not disk(x, z, 13), 1, 11, PANEL)
    b.each(lambda x, y, z: disk(x, z, 14), 12, 12, PANEL)
    b.each(lambda x, y, z: disk(x, z, 13), 1, 11, AIR)
    # 天井の光の輪
    b.each(lambda x, y, z: 9.5 <= math.hypot(x - c, z - c) <= 10.4 and (x + z) % 2 == 0, 11, 11, LAMP)
    # 培養室を分ける放射状の壁（4方向、出入口つき）
    for x in range(29):
        for z in range(29):
            d = math.hypot(x - c, z - c)
            if 5.5 <= d <= 13 and (abs(x - c) <= 0 or abs(z - c) <= 0):
                b.fill(x, 1, z, x, 10, z, PANEL)
                if 8 <= d <= 9.5:
                    b.fill(x, 1, z, x, 3, z, AIR)
    # 極低温コア: ガラスの円筒（リブはパネル）、床は青氷、中は粉雪と氷の柱
    for x in range(29):
        for z in range(29):
            d = math.hypot(x - c, z - c)
            if 4.4 <= d <= 5.4:
                rib = x == c or z == c or abs(x - c) == abs(z - c)
                b.fill(x, 1, z, x, 9, z, PANEL if rib else 'minecraft:light_blue_stained_glass')
                b.set(x, 10, z, FRAME)
            elif d < 4.4:
                b.set(x, 0, z, 'minecraft:blue_ice')
                b.fill(x, 1, z, x, 9, z, AIR)
                b.set(x, 10, z, 'minecraft:packed_ice')
    b.fill(c, 1, c, c, 9, c, 'minecraft:packed_ice')
    for x, z in ((c - 2, c - 1), (c + 2, c + 1), (c - 1, c + 2), (c + 1, c - 2)):
        b.set(x, 1, z, 'minecraft:powder_snow')
    b.fill(c - 1, 1, c + 5, c + 1, 3, c + 5, AIR)             # コアの入口（南）
    b.set(c + 2, 1, c - 2, FRAME)                             # 保管庫のあった台（中身は隔離槽室へ移された）
    b.set(c + 2, 2, c - 2, 'minecraft:light_blue_stained_glass')
    b.sealed(c - 2, 1, c + 1, 3)
    # 培養室: 割れた培養槽、苔の床、天井の胞子の花、光るカエルの明かり
    rooms = ((c - 6, c - 6), (c + 6, c - 6), (c - 6, c + 6), (c + 6, c + 6))
    for rx, rz in rooms:
        for dx, dz in ((-2, -2), (2, -2), (-2, 2), (2, 2), (0, 0)):
            x, z = rx + dx, rz + dz
            if (dx, dz) == (0, 0):
                b.set(x, 1, z, FRAME)
                b.set(x, 2, z, 'minecraft:verdant_froglight', {'axis': 'y'})
            else:
                tank(b, x, z, 1, 5, 'minecraft:lime_stained_glass', top='minecraft:ochre_froglight' if dx > 0 else LAMP)
                if b.rng.random() < 0.5:
                    b.set(x, 3, z, AIR)                             # 割れた槽
        for _ in range(14):
            x, z = rx + b.rng.randint(-3, 3), rz + b.rng.randint(-3, 3)
            if b.get(x, 1, z) in (None, AIR) and disk(x, z, 12.5):
                b.set(x, 0, z, 'minecraft:moss_block')
                b.set(x, 1, z, 'minecraft:moss_carpet')
        for _ in range(3):
            x, z = rx + b.rng.randint(-3, 3), rz + b.rng.randint(-3, 3)
            if b.get(x, 10, z) in (None, AIR):
                b.set(x, 10, z, 'minecraft:spore_blossom')
    b.dock(c - 9, 1, c - 5, 3)
    b.dock(c + 9, 1, c + 5, 3)
    containment_chamber(b, c)
    b.decay(cracked=0.15, mossy=0.1)
    b.break_glass(0.1)
    b.scatter('minecraft:cobweb', 16, 10, (1, 1, 27, 27), floor=(AIR,))
    return b


def containment_chamber(b, c):
    """
    封鎖培養施設の隔離槽室（ボス部屋）。南東の培養室の壁から通路を抜けた先の円い部屋。
    中央に菫色のガラスの巨大な封じ込め槽（床に番人の封印核）、まわりを6本の白い封じ込め柱と警告の縞の環が囲む。
    重力の乱れで瓦礫が宙に止まり、床は割れている。奥の壇の上が保管庫（番人を倒すまで力場で封鎖）。
    """
    tank_glass = 'minecraft:purple_stained_glass'
    # 培養施設の外壁から東へ伸びる通路（内側 z18〜20）
    b.fill(24, 1, 18, 28, 3, 20, AIR)
    b.box(27, 0, 17, 33, 5, 21, PANEL)
    b.fill(24, 1, 18, 33, 4, 20, AIR)
    for x in (28, 32):
        b.set(x, 5, 19, LAMP)
    # 円い部屋（中心 (44,16)、半径12）
    cx, cz, R = 44, 16, 12
    disk = lambda x, z, r: math.hypot(x - cx, z - cz) <= r
    for x in range(cx - R - 1, cx + R + 2):
        for z in range(cz - R - 1, cz + R + 2):
            if not disk(x, z, R + 0.5):
                continue
            d = math.hypot(x - cx, z - cz)
            b.set(x, 0, z, PANEL)
            b.set(x, 12, z, PANEL)
            if d > R - 0.5:
                b.fill(x, 1, z, x, 11, z, PANEL)
            else:
                b.fill(x, 1, z, x, 11, z, AIR)
                if 8.6 <= d <= 9.6:
                    b.set(x, 0, z, STRIPED)                     # 警告の縞の環
                elif d <= 4.2:
                    b.set(x, 0, z, FRAME)
    b.fill(29, 1, 18, 34, 4, 20, AIR)                           # 通路とつながる口
    for z in (17, 21):
        b.fill(33, 1, z, 33, 5, z, FRAME)
    # 封じ込め槽: 台の環・ガラスの筒・光る蓋。床の中心に封印核
    for x in range(cx - 3, cx + 4):
        for z in range(cz - 3, cz + 4):
            d = math.hypot(x - cx, z - cz)
            if d <= 2.5:
                b.set(x, 1, z, FRAME if d >= 1.5 else AIR)
                b.set(x, 9, z, FRAME if d >= 1.5 else LAMP)
                for y in range(2, 9):
                    b.set(x, y, z, tank_glass if d >= 1.5 else AIR)
    b.set(cx, 0, cz, CORE)
    b.set(cx, 1, cz, AIR)
    for dx, dz in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        b.fill(cx + dx, 10, cz + dz, cx + dx, 11, cz + dz, *CHAIN_Y)
    # 6本の封じ込め柱（白い柱と光、上は鎖）
    for k in range(6):
        a = math.pi * 2 * k / 6 + math.pi / 6
        x, z = round(cx + math.cos(a) * 8), round(cz + math.sin(a) * 8)
        b.fill(x, 1, z, x, 7, z, PRISTINE_PILLAR)
        b.set(x, 4, z, PRISTINE_LIGHT)
        b.set(x, 8, z, LAMP)
        b.fill(x, 9, z, x, 11, z, *CHAIN_Y)
    # 天井の光の輪
    for x in range(cx - R, cx + R + 1):
        for z in range(cz - R, cz + R + 1):
            d = math.hypot(x - cx, z - cz)
            if 6.5 <= d <= 7.3 and (x + z) % 2 == 0:
                b.set(x, 11, z, LAMP)
    # 奥（東）の壇と保管庫（力場で封鎖）
    for x in range(cx + 8, cx + R):
        for z in range(cz - 2, cz + 3):
            if disk(x, z, R - 0.6):
                b.set(x, 1, z, PANEL)
    for dz in range(-2, 3):
        b.set(cx + 7, 1, cz + dz, 'minecraft:quartz_stairs', {'facing': 'east', 'half': 'bottom', 'shape': 'straight',
                                                             'waterlogged': 'false'})
    b.cache(cx + 10, 2, cz, 'culture_facility', sealed=True)
    b.set(cx + 11, 2, cz, SCREEN)
    b.set(cx + 11, 3, cz, LAMP)
    # 重力の乱れ: 宙に止まった瓦礫、床の割れ目、割れた小さな槽
    for _ in range(30):
        x, y, z = cx + b.rng.randint(-10, 10), b.rng.randint(4, 10), cz + b.rng.randint(-10, 10)
        if b.get(x, y, z) == AIR and 4 < math.hypot(x - cx, z - cz) < R - 1:
            b.set(x, y, z, b.rng.choice((PANEL, CRACKED, FRAME, DARK, 'minecraft:obsidian')))
    for _ in range(26):
        x, z = cx + b.rng.randint(-R + 1, R - 1), cz + b.rng.randint(-R + 1, R - 1)
        if b.get(x, 0, z) == PANEL and 4.5 < math.hypot(x - cx, z - cz) < R - 1:
            b.set(x, 0, z, CRACKED)
    for x, z in ((cx - 7, cz - 5), (cx - 8, cz + 4), (cx + 3, cz + 9)):
        if b.get(x, 1, z) == AIR:
            tank(b, x, z, 1, 4, tank_glass, top=FRAME)
            b.set(x, 3, z, AIR)
    b.scatter('minecraft:cobweb', 10, 11, (cx - R + 1, cz - R + 1, cx + R - 1, cz + R - 1), floor=(AIR,))


def final_lab():
    """
    最終実験施設（いちばん深い地下）: 重力異常点を封じた巨大なドーム。光の環が床に刻まれ、8本の柱が
    天井を支え、宙には崩れた破片が浮いたまま止まっている。中央の壇の封印コンソールに触れると守護機
    ホライズン・ウォーデンが起動する。保管庫は壇の上で、力場で封鎖されている。
    """
    b = Builder(35, 21, 35, seed=404)
    c = 17
    R = 16
    # 床と、床に刻まれた光の環
    for x in range(35):
        for z in range(35):
            d = math.hypot(x - c, z - c)
            if d <= R:
                b.set(x, 0, z, PANEL)
                if 6.5 <= d <= 7.3 or 11.5 <= d <= 12.3:
                    b.set(x, 0, z, LAMP)
    # ドーム（縦に少しつぶした半球）
    for x in range(35):
        for z in range(35):
            for y in range(1, 21):
                d = math.sqrt((x - c) ** 2 + (z - c) ** 2 + ((y - 1) * 0.95) ** 2)
                if R - 0.6 <= d <= R + 0.5:
                    b.set(x, y, z, LAMP if (y % 5 == 0 and (x + z) % 3 == 0) else PANEL)
                elif d < R - 0.6:
                    b.set(x, y, z, AIR)
    # 8本の柱（上は鎖で天井へ）
    for k in range(8):
        a = math.pi * 2 * k / 8
        x, z = round(c + math.cos(a) * 12), round(c + math.sin(a) * 12)
        b.fill(x, 1, z, x, 9, z, PRISTINE_PILLAR)              # 朽ちていない白い柱
        b.set(x, 5, z, PRISTINE_LIGHT)
        b.set(x, 10, z, LAMP)
        top = next((y for y in range(11, 21) if b.get(x, y, z) not in (None, AIR)), 20)
        b.fill(x, 11, z, x, top - 1, z, *CHAIN_Y)
    # 中央の壇（3段）と封印コンソール。上は守護機が現れるので空けておく
    for x in range(35):
        for z in range(35):
            d = math.hypot(x - c, z - c)
            if d <= 5.3:
                b.set(x, 1, z, PANEL)
            if d <= 3.3:
                b.set(x, 2, z, FRAME)
    for dx, dz, f in ((0, 5, 'north'), (0, -5, 'south'), (5, 0, 'west'), (-5, 0, 'east')):
        b.set(c + dx, 1, c + dz, 'minecraft:quartz_stairs', {'facing': f, 'half': 'bottom', 'shape': 'straight', 'waterlogged': 'false'})
    b.set(c, 3, c, CONSOLE)
    b.fill(c, 4, c, c, 9, c, AIR)
    for dx, dz in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
        b.set(c + dx, 3, c + dz, 'minecraft:end_rod', {'facing': 'up'})
    b.cache(c, 3, c + 3, 'final_lab', sealed=True)              # 守護機を倒すまで力場で封鎖
    b.sealed(c, 3, c - 3, 4)
    # 宙に止まった破片の環（重力異常）と、ばらばらに浮かぶ欠片
    for k in range(28):
        a = math.pi * 2 * k / 28
        x, z = round(c + math.cos(a) * 8), round(c + math.sin(a) * 8)
        y = 12 + round(math.sin(a * 2))
        b.set(x, y, z, SCREEN if k % 4 == 0 else (CRACKED if k % 3 else FRAME))
    for _ in range(26):
        x, y, z = c + b.rng.randint(-11, 11), b.rng.randint(5, 15), c + b.rng.randint(-11, 11)
        if b.get(x, y, z) == AIR and math.hypot(x - c, z - c) > 4:
            b.set(x, y, z, b.rng.choice((PANEL, CRACKED, FRAME, 'minecraft:obsidian')))
    # 南の通路（崩れた入口）
    b.fill(c - 1, 1, c + R - 1, c + 1, 4, 34, AIR)
    b.fill(c - 2, 0, c + R - 1, c + 2, 0, 34, PANEL)
    b.fill(c - 2, 1, c + R, c - 2, 5, 34, PANEL)
    b.fill(c + 2, 1, c + R, c + 2, 5, 34, PANEL)
    b.fill(c - 2, 5, c + R, c + 2, 5, 34, PANEL)
    b.decay(cracked=0.22, mossy=0.12, only_above=1)
    b.scatter('minecraft:moss_carpet', 30, 1, (2, 2, 32, 32), floor=(PANEL, LAMP))
    return b


# 遺構ID → (作る関数, 置き方)。surface は地上、それ以外は地下に埋める（gen_data.RUIN_PLACEMENT の高さ）
RUINS = {
    'observation_post': (observation_post, 'surface'),
    'research_building': (research_building, 'underground'),
    'culture_facility': (culture_facility, 'underground'),
    'final_lab': (final_lab, 'underground'),
}

# 設計書の遺構名 → 遺構ID
RUIN_IDS = {
    '地表観測拠点': 'observation_post',
    '研究棟': 'research_building',
    '封鎖培養施設': 'culture_facility',
    '最終実験施設': 'final_lab',
}


# 遺構ID → (朽ちた変わり種の割合, 朽ちていない白い建材の割合)。深い遺構ほど、きれいなまま残った所が多い
VARIETY = {
    'observation_post': (0.12, 0.0),
    'research_building': (0.12, 0.04),
    'culture_facility': (0.12, 0.10),
    'final_lab': (0.08, 0.30),
}


def write_all(structure_dir):
    out = {}
    for rid, (make, _) in RUINS.items():
        b = make()
        weathered, pristine = VARIETY[rid]
        b.vary(seed=len(rid) * 7919, weathered=weathered, pristine=pristine)
        nbt.write_gzip(structure_dir / f'{rid}.nbt', b.to_nbt())
        out[rid] = b
    return out
