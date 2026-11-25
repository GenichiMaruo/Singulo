# -*- coding: utf-8 -*-
"""旧文明の遺構5種の構造物（.nbt）を作る。gen_data.py から呼ぶ。

最終実験施設と沈んだ炉心区画は、ボス部屋の手前に前室を持つ。前室の区画の奥の中継器をすべて起動すると、
ボス部屋の前の隔壁が開く。

どれも白い無機質パネルの建物が黄ばみ・ひび割れ・植物に侵食された姿にする（設計書「コンセプトと世界観」）。
地上に建つのは入口となる地表観測拠点だけで、ほかの3つは地下に埋もれている（深いほど後の段階）。
崩れ方は乱数の種を固定しているので、毎回同じ形になる。
"""
import math
import json
import random

import nbt

DATA_VERSION = 3465  # 1.20.1
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
REINFORCED_GLASS = f'{MODID}:reinforced_ruin_glass'
BROKEN_GLASS = f'{MODID}:broken_ruin_glass'
LAMP = f'{MODID}:ruin_lamp'
CACHE = f'{MODID}:ruin_cache'
DOCK = f'{MODID}:ruin_guard_dock'
FABRICATOR = f'{MODID}:ruin_android_fabricator'
CONSOLE = f'{MODID}:seal_console'
CORE = f'{MODID}:guardian_core'
PROJECTOR = f'{MODID}:echo_projector'
LINE = f'{MODID}:factory_line'
CONVEYOR = f'{MODID}:factory_conveyor'
STRIPED = f'{MODID}:striped_ruin_panel'
# 遺構パネルの階段・ハーフブロック（0.20.0。それまではバニラのクォーツで代用していた）
PANEL_STAIRS = f'{MODID}:ruin_panel_stairs'
PANEL_SLAB = f'{MODID}:ruin_panel_slab'
# 遺構パネルの塀の柱（隣とつながらない1本立ち）
PANEL_WALL_POST = (f'{MODID}:ruin_panel_wall', {'up': 'true', 'north': 'none', 'east': 'none', 'south': 'none', 'west': 'none',
                                                'waterlogged': 'false'})
# 崩れ方: パネルと、その階段・ハーフブロックが、ひび割れ・苔むした同じ形になる
DECAYED = {PANEL: (CRACKED, MOSSY),
           PANEL_STAIRS: (f'{MODID}:cracked_ruin_panel_stairs', f'{MODID}:mossy_ruin_panel_stairs'),
           PANEL_SLAB: (f'{MODID}:cracked_ruin_panel_slab', f'{MODID}:mossy_ruin_panel_slab')}
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
        self.entities = []
        self.rng = random.Random(seed)

    def entity(self, x, y, z, data):
        """構造物に入れる実体（(x, y, z) のマスの床の真ん中に立つ）。data は id を含む実体の NBT。"""
        self.entities.append({'pos': [x + 0.5, float(y), z + 0.5], 'blockPos': [x, y, z], 'nbt': dict(data)})

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
        """パネル（と階段・ハーフブロック）を確率でひび割れ・苔むし・欠けにする。向きなどの状態はそのまま。"""
        for (x, y, z), (name, props, data) in list(self.blocks.items()):
            if name not in DECAYED or y < only_above:
                continue
            r = self.rng.random()
            if r < missing:
                self.blocks[(x, y, z)] = (AIR, {}, None)
            elif r < missing + cracked:
                self.blocks[(x, y, z)] = (DECAYED[name][0], props, None)
            elif r < missing + cracked + mossy:
                self.blocks[(x, y, z)] = (DECAYED[name][1], props, None)

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
        data = {'ruin': ruin}
        if sealed:
            data['reconstruction_revision'] = 1
        self.set(x, y, z, CACHE, {'sealed': 'true' if sealed else 'false'}, data)

    def dock(self, x, y, z, tier):
        self.set(x, y, z, DOCK, None, {'tier': tier})

    def fabricator(self, x, y, z, radius=6):
        """
        遺構の印刷台を (x, y, z) の近くの壁際に置く。床があり、そのマスと上の3マスが空いていて（アンドロイドが立てる）、
        すぐ横の同じ高さと1つ上が壁の所を探す。
        """
        def solid(name):
            return name not in (None, AIR) and not any(w in name for w in ('slab', 'stairs', 'carpet', 'cobweb', 'end_rod'))

        spots = sorted(((dx, dz) for dx in range(-radius, radius + 1) for dz in range(-radius, radius + 1)),
                       key=lambda d: d[0] * d[0] + d[1] * d[1])
        for dx, dz in spots:
            px, pz = x + dx, z + dz
            if not solid(self.get(px, y - 1, pz)):
                continue
            if not all(self.get(px, y + k, pz) == AIR for k in range(4)):
                continue
            if any(solid(self.get(px + ox, y, pz + oz)) and solid(self.get(px + ox, y + 1, pz + oz))
                   for ox, oz in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                self.set(px, y, pz, FABRICATOR)
                return px, y, pz
        raise ValueError(f'遺構の印刷台を置ける壁際がない: {(x, y, z)}')

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
                'blocks': blocks, 'entities': list(self.entities)}


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
    # 屋上の手すり（1つおきの塀の柱）
    b.each(lambda x, y, z: ring(x, z) and (x + z) % 2 == 0, 7, 7, *PANEL_WALL_POST)
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
        b.set(x, 1, c - 5, PANEL_SLAB, {'type': 'bottom', 'waterlogged': 'false'})
    for z in range(c - 2, c + 3):
        b.set(c - 6, 1, z, FRAME)
        b.set(c - 6, 2, z, 'minecraft:chiseled_bookshelf' if z % 2 else FRAME,
              {'facing': 'east', 'slot_0_occupied': 'false', 'slot_1_occupied': 'false', 'slot_2_occupied': 'false',
               'slot_3_occupied': 'false', 'slot_4_occupied': 'false', 'slot_5_occupied': 'false'} if z % 2 else None)
    b.cache(c + 5, 1, c, 'observation_post')
    b.set(c + 5, 1, c + 2, PANEL_SLAB, {'type': 'bottom', 'waterlogged': 'false'})
    b.dock(c - 4, 1, c + 4, 1)
    b.sealed(c - 5, 1, c - 1, 1)
    b.fabricator(c + 3, 1, c - 4)
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
            b.set(x, 8, z, PANEL_SLAB, {'type': 'bottom', 'waterlogged': 'false'})
    for z in range(7, 16):
        for x in (8, 18):
            b.set(x, 8, z, PANEL_SLAB, {'type': 'bottom', 'waterlogged': 'false'})
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
            b.set(10 + i, 1 + i, z, PANEL_STAIRS, {'facing': 'east', 'half': 'bottom', 'shape': 'straight',
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
            b.set(x, 8, z, PANEL_SLAB, {'type': 'top', 'waterlogged': 'false'})
            b.set(x, 9, z, SCREEN if x % 2 else AIR)
    # 照明（天井）
    for x in range(3, X, 5):
        for z in range(3, Z, 5):
            b.set(x, 6, z, LAMP)
            b.set(x, 14, z, LAMP)
    b.dock(13, 1, 4, 2)
    b.sealed(4, 8, 16, 2)
    b.dock(22, 8, 14, 2)
    b.fabricator(5, 1, 6)
    b.fabricator(12, 8, 6)
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
            b.set(x, 1, z + side, PANEL_SLAB, {'type': 'bottom', 'waterlogged': 'false'})
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
        b.set(x1 - 5, 1, cz + dz, PANEL_STAIRS, {'facing': 'east', 'half': 'bottom', 'shape': 'straight',
                                                                'waterlogged': 'false'})
    for dz in range(-2, 3):
        b.set(x1 - 3, 2, cz + dz, PANEL_STAIRS, {'facing': 'east', 'half': 'bottom', 'shape': 'straight',
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
    b.fabricator(c, 1, c + 9)
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
        b.set(cx + 7, 1, cz + dz, PANEL_STAIRS, {'facing': 'east', 'half': 'bottom', 'shape': 'straight',
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


class Shifted:
    """Builder を、ずらした原点から使う（ボス部屋を前室のある大きな構造物の中へ置くため）。"""

    def __init__(self, b, ox, oy, oz):
        self.b, self.ox, self.oy, self.oz = b, ox, oy, oz
        self.rng = b.rng

    def set(self, x, y, z, name, props=None, data=None):
        self.b.set(x + self.ox, y + self.oy, z + self.oz, name, props, data)

    def get(self, x, y, z):
        return self.b.get(x + self.ox, y + self.oy, z + self.oz)

    def fill(self, x0, y0, z0, x1, y1, z1, name, props=None):
        self.b.fill(x0 + self.ox, y0 + self.oy, z0 + self.oz, x1 + self.ox, y1 + self.oy, z1 + self.oz, name, props)

    def box(self, x0, y0, z0, x1, y1, z1, wall, inside=AIR):
        self.b.box(x0 + self.ox, y0 + self.oy, z0 + self.oz, x1 + self.ox, y1 + self.oy, z1 + self.oz, wall, inside)

    def cache(self, x, y, z, ruin, sealed=False):
        self.b.cache(x + self.ox, y + self.oy, z + self.oz, ruin, sealed)

    def sealed(self, x, y, z, tier):
        self.b.sealed(x + self.ox, y + self.oy, z + self.oz, tier)


# 前室の仕掛け
RELAY = f'{MODID}:ruin_relay'
EMITTER = f'{MODID}:anomaly_emitter'
DRAG_FLOOR = f'{MODID}:frame_drag_floor'
BRAKE = f'{MODID}:drag_brake'
BULKHEAD = f'{MODID}:bulkhead'
TERMINAL = f'{MODID}:lab_terminal'
WATER = 'minecraft:water'

# 点火管制区画の白いセラミックと光学ガラス、第二炉の耐熱外板と緑青の送水管。
CERAMIC = f'{MODID}:pristine_ruin_tiles'
VENT = f'{MODID}:vented_ruin_panel'
GRAPHITE = f'{MODID}:lab_frame'
OPTIC = 'minecraft:light_blue_stained_glass'
PHASE_GLASS = 'minecraft:purple_stained_glass'
COPPER = 'minecraft:waxed_exposed_cut_copper'
PATINA = f'{MODID}:coolant_cladding'
GRILLE = 'minecraft:waxed_exposed_copper_grate'


def wall_band(b, x0, z0, x1, z1, low, high, material):
    """四方の壁にだけ腰壁・廻縁を通す。開口と設備はこの後で置く。"""
    for x in (x0, x1):
        b.fill(x, low, z0, x, high, z1, material)
    for z in (z0, z1):
        b.fill(x0 + 1, low, z, x1 - 1, high, z, material)


# 部屋の札の文字。遊ぶ人の言語によらず、施設の表示板らしい英語の大文字2行にする
SIGN_TEXT = {
    'changing': ('STAFF', 'LOCKERS'), 'security': ('SECURITY', 'CHECKPOINT'),
    'ignition_control': ('IGNITION', 'CONTROL'), 'ignition_chamber': ('IGNITION', 'CHAMBER'), 'transfer_lock': ('TRANSFER', 'LOCK'),
    'observation_gallery': ('VIEWING', 'GALLERY'), 'gravity_test': ('GRAVITY', 'TEST HALL'), 'time_standard': ('TIME', 'STANDARDS'),
    'metrology': ('STANDARDS', 'VAULT'), 'coil_power': ('COIL', 'POWER'), 'hangar': ('VESSEL', 'HANGAR'),
    'reactor_control': ('UNIT 2', 'CONTROL'), 'reactor_lock': ('REACTOR', 'LOCK'), 'inspection_ring': ('INSPECTION', 'RING'),
    'cooling': ('COOLANT', 'SYSTEM'), 'diving_air': ('DIVER', 'AIR'), 'extraction': ('EXTRACTION', 'TOWER'),
    'weighbridge': ('CHARGE', 'WEIGHING'), 'elevator': ('ELEVATOR', ''), 'inspection': ('HULL', 'REPAIR'),
}


def room_sign(b, x, y, z, facing, label, arrow=''):
    first, second = SIGN_TEXT[label]
    lines = [{'text': first}, {'text': second}, {'text': arrow}, {'text': ''}]
    b.set(x, y, z, 'minecraft:warped_wall_sign', {'facing': facing},
          {'id': 'minecraft:sign', 'is_waxed': True, 'front_text': {
              'messages': [json.dumps(line) for line in lines], 'color': 'white', 'has_glowing_text': True}})


def corridor_z(b, cx, y, z0, z1):
    """南北に走る通路（内のり3×4、床・壁・天井は遺構パネル）。"""
    b.fill(cx - 2, y, z0, cx + 2, y + 5, z1, PANEL)
    b.fill(cx - 1, y + 1, z0, cx + 1, y + 4, z1, AIR)


def corridor_x(b, cz, y, x0, x1):
    """東西に走る通路（内のり3×4）。"""
    b.fill(x0, y, cz - 2, x1, y + 5, cz + 2, PANEL)
    b.fill(x0, y + 1, cz - 1, x1, y + 4, cz + 1, AIR)


def bulkhead(b, x0, y0, z0, x1, y1, z1):
    """ボス部屋の前の隔壁（施設の中継器がそろうと開く）。"""
    b.fill(x0, y0, z0, x1, y1, z1, BULKHEAD, {'open': 'false', 'wired': 'true'})


def seal_chamber(b, cx, cz, floor, gate_z, radius, center_y, vertical_radius, bottom):
    """内装と南側の入室路を一体の圧力容器で包む。斜面・角も隣の断面に重ねる。"""
    sections = {}
    for x in range(cx - radius, cx + radius + 1):
        for z in range(cz - radius, gate_z):
            radial = ((x - cx) ** 2 + (z - cz) ** 2) / radius ** 2
            if radial <= 1:
                height = vertical_radius * math.sqrt(1 - radial)
                sections[x, z] = (max(bottom, math.ceil(center_y - height)), math.floor(center_y + height))
            if abs(x - cx) <= 1 and z >= cz:
                lo, hi = sections.get((x, z), (floor + 1, floor + 4))
                sections[x, z] = (min(lo, floor + 1), max(hi, floor + 4))
    for x in range(cx - radius - 1, cx + radius + 2):
        for z in range(cz - radius - 1, gate_z + 1):
            neighbours = [sections[u, v] for u in range(x - 1, x + 2) for v in range(z - 1, z + 2) if (u, v) in sections]
            if not neighbours:
                continue
            low, high = min(s[0] for s in neighbours) - 1, max(s[1] for s in neighbours) + 1
            inside_low, inside_high = sections.get((x, z), (high + 1, high))
            for y in (*range(low, inside_low), *range(inside_high + 1, high + 1)):
                b.set(x, y, z, f'{MODID}:pressure_hull', {'sealed': 'true'})
    bulkhead(b, cx - 1, floor + 1, gate_z, cx + 1, floor + 4, gate_z)
    # 各中継器に同じ施設の配線図を持たせる。隣の施設や未読み込みの区画を誤って数えない。
    peers = sorted(p for p, block in b.blocks.items() if block[0] == RELAY)
    for x, y, z in peers:
        data = b.blocks[x, y, z][2]
        data['stations'] = [[u - x, v - y, w - z] for u, v, w in peers]
        data['door'] = [cx - x, floor + 1 - y, gate_z - z]


def relay(b, x, y, z, mode, guardian=None, waterlogged=False):
    """遺構の中継器（mode: plain / guarded / extract / drain。guarded は中ボスの種類 guardian。水中なら waterlogged）。"""
    data = {'mode': mode}
    if guardian:
        data['guardian'] = guardian
    b.set(x, y, z, RELAY, {'active': 'false', 'waterlogged': 'true' if waterlogged else 'false'}, data)


def emitter(b, x, y, z, field):
    """異常場の発生器（field: lift / heavy / slow / fast。上の 5×7×5 に場を張る）。"""
    b.set(x, y, z, EMITTER, {'field': field})


def terminal(b, x, y, z, facing, log):
    """記録端末（研究所と同じ端末。12〜15 は前室の記録）。"""
    b.set(x, y, z, TERMINAL, {'facing': facing, 'log': str(log)})


def ladder(b, x, y0, y1, z, facing, water_to=None):
    """はしご（facing の向きを向き、反対側の壁につく）。water_to 以下の段は水浸し。"""
    for y in range(y0, y1 + 1):
        wet = water_to is not None and y <= water_to
        b.set(x, y, z, 'minecraft:ladder', {'facing': facing, 'waterlogged': 'true' if wet else 'false'})


# 止まったままの職員の姿勢（防具立ての Pose。度）
WORKER_POSES = {
    'walk': {'Head': (-5, 0, 0), 'LeftArm': (-35, 0, -6), 'RightArm': (35, 0, 6), 'LeftLeg': (28, 0, 0), 'RightLeg': (-28, 0, 0)},
    'reach': {'Head': (-15, 0, 0), 'RightArm': (-95, -10, 0), 'LeftArm': (-10, 0, -12), 'LeftLeg': (-8, 0, 0), 'RightLeg': (12, 0, 0)},
    'fall': {'Head': (30, 0, 0), 'Body': (14, 0, 0), 'LeftArm': (-65, 0, -30), 'RightArm': (-65, 0, 30), 'LeftLeg': (-22, 0, 0),
             'RightLeg': (12, 0, 0)},
}


def frozen_worker(b, x, y, z, yaw, pose):
    """時間の場で、動きの途中のまま止まった職員の姿（白衣を着た防具立て。壊せず、装備も取れない）。"""
    def item(name, rgb):
        return {'id': f'minecraft:{name}', 'count': 1, 'components': {'minecraft:dyed_color': {'rgb': rgb}}}
    nbt_data = {
        'id': 'minecraft:armor_stand',
        'Invulnerable': True,
        'ShowArms': True,
        'NoBasePlate': True,
        'DisabledSlots': 4144959,
        'Rotation': [nbt.Float(yaw), nbt.Float(0)],
        'Pose': {part: [nbt.Float(v) for v in angles] for part, angles in WORKER_POSES[pose].items()},
        'ArmorItems': [item('leather_boots', 0x5A6270), item('leather_leggings', 0x7A8494),
                       item('leather_chestplate', 0xEEF1F4), item('leather_helmet', 0xD6DCE2)],
        'HandItems': [{}, {}],
    }
    b.entity(x, y, z, nbt_data)


def _final_dome(b):
    """
    最終実験施設のドーム（ボス部屋）。重力異常点を封じた巨大なドームで、光の環が床に刻まれ、8本の柱が天井を支え、
    宙には崩れた破片が浮いたまま止まっている。中央の壇の封印コンソールに触れると守護機ホライズン・ウォーデンが起動する。
    保管庫は壇の上で、力場で封鎖されている。b はドームの西北の角を原点にした座標（{@link Shifted}）。
    """
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
        b.set(c + dx, 1, c + dz, PANEL_STAIRS, {'facing': f, 'half': 'bottom', 'shape': 'straight', 'waterlogged': 'false'})
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
    # 南の通路（隔壁まで。隔壁の外は移送前室）
    b.fill(c - 2, 0, c + R - 1, c + 2, 5, 35, PANEL)
    b.fill(c - 1, 1, c + R - 1, c + 1, 4, 35, AIR)


# ------------------------------------------------------------------ 前室の共通の部品

PRESSURE_GLASS = f'{MODID}:pressure_glass'
CABINET = f'{MODID}:control_cabinet'
LAB_TILES = f'{MODID}:lab_tiles'
LINED = f'{MODID}:lab_panel_lined'
SERVER_RACK = f'{MODID}:server_rack'
SLIDING_DOOR = f'{MODID}:lab_door'
FACILITY_LIGHT = (f'{MODID}:facility_light', {'powered': 'true'})
# 主電源の落ちた遺構では、非常灯だけが赤く灯っている
EMERGENCY_LIGHT = (f'{MODID}:emergency_light', {'powered': 'false'})


def _facing(dx, dz):
    """水平の向き（大きい方の軸）を、ブロックの向きの名前に。"""
    if abs(dx) >= abs(dz):
        return 'east' if dx > 0 else 'west'
    return 'south' if dz > 0 else 'north'


WALL = f'{MODID}:ruin_panel_wall'
# 塀がつながらない、薄い・小さい・形のあるブロック（名前に含む語）
NOT_WALL_SUPPORT = ('slab', 'stairs', 'sign', 'door', 'desk', 'chair', 'rod', 'chain', 'bars', 'carpet', 'button', 'ladder', 'rail',
                    'cobweb', 'end_rod', 'observer', 'monitor', 'paper', 'bin', 'cluster', 'grindstone', 'conveyor', 'fabricator',
                    'relay', 'brake', 'emitter', 'dock', 'terminal', 'sealed_container', 'ruin_cache', 'console', 'core', 'panel')


def connect_walls(b):
    """
    塀（遺構パネルの塀）を、隣の塀と、隣の詰まったブロック（壁など）につなぐ。構造物は置くときに隣とのつながりを計算しない
    ので、ここで決めておく。まっすぐ続く所は柱を立てず、端と曲がり角だけ柱にする（バニラの塀と同じ）。
    """
    def joins(x, y, z):
        name = b.get(x, y, z)
        if name == WALL:
            return True
        if name in (None, AIR) or name.startswith('minecraft:water'):
            return False
        short = name.split(':')[1]
        return not any(w in short for w in NOT_WALL_SUPPORT) or short.endswith('ruin_panel')
    sides = {'north': (0, -1), 'south': (0, 1), 'west': (-1, 0), 'east': (1, 0)}
    for (x, y, z), (name, props, data) in list(b.blocks.items()):
        if name != WALL:
            continue
        on = {side: joins(x + dx, y, z + dz) for side, (dx, dz) in sides.items()}
        straight = (on['north'] and on['south'] and not on['east'] and not on['west']) or                    (on['east'] and on['west'] and not on['north'] and not on['south'])
        state = {side: 'low' if c else 'none' for side, c in on.items()}
        state['up'] = 'false' if straight and b.get(x, y + 1, z) in (None, AIR) else 'true'
        state['waterlogged'] = props.get('waterlogged', 'false')
        b.blocks[(x, y, z)] = (name, state, data)


def rooms(b, boxes, between=None):
    """
    部屋の箱（x0, y0, z0, x1, y1, z1, 壁, 床）を、先に全部の壁を立ててから中を空けて作る。隣り合う部屋は壁を共有し、
    重なった部屋はひと続きになる。between は壁を立てたあと中を空ける前に呼ぶ（回廊など、壁を共有する別の形）。床が None の
    部屋は床を張らない。
    """
    for x0, y0, z0, x1, y1, z1, wall, _ in boxes:
        b.fill(x0, y0, z0, x1, y1, z1, wall)
    if between:
        between()
    for x0, y0, z0, x1, y1, z1, _, floor in boxes:
        b.fill(x0 + 1, y0 + 1, z0 + 1, x1 - 1, y1 - 1, z1 - 1, AIR)
        if floor:
            b.fill(x0 + 1, y0, z0 + 1, x1 - 1, y0, z1 - 1, floor)


def opening(b, x0, y0, z0, x1, y1, z1):
    """壁の開口（出入口）。"""
    b.fill(x0, y0, z0, x1, y1, z1, AIR)


def door(b, x, y, z, facing):
    """研究所の引き戸（2段。facing は扉の面の向き。north/south なら東西に延びる壁にはまる）。"""
    for half, dy in (('lower', 0), ('upper', 1)):
        b.set(x, y + dy, z, SLIDING_DOOR, {'facing': facing, 'half': half, 'open': 'false', 'powered': 'false'})


def bars(b, x0, z0, x1, z1, y0, y1, skip=()):
    """鉄格子の仕切り（x か z のどちらかにまっすぐ）。skip のマス（x, z）は通り口として空ける。"""
    along_x = z0 == z1
    props = {'north': 'false', 'south': 'false', 'east': 'false', 'west': 'false', 'waterlogged': 'false'}
    props.update({'east': 'true', 'west': 'true'} if along_x else {'north': 'true', 'south': 'true'})
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if (x, z) not in skip:
                b.fill(x, y0, z, x, y1, z, 'minecraft:iron_bars', props)


def instrument_bank(b, x, y, z, facing, panels):
    """正面の床に立って読める高さに計器を並べ、同じ筐体の上段に警報窓を入れる。"""
    mimic_section = 0
    for i, panel in enumerate(panels):
        px = x + (i if facing in ('north', 'south') else 0)
        pz = z + (i if facing in ('east', 'west') else 0)
        b.fill(px, y + 1, pz, px, y + 3, pz, CABINET)
        b.set(px, y + 4, pz, LINED)
        props = {'facing': facing}
        if panel == 'mimic_panel':
            props['channel'] = str(mimic_section % 3)
            mimic_section += 1
        elif panel == 'dial_panel':
            props['channel'] = str(i % 3)
        b.set(px, y + 2, pz, f'{MODID}:{panel}', props)
        if i % 3 == 1:
            b.set(px, y + 3, pz, f'{MODID}:annunciator_panel', {'facing': facing})


def operating_desk(b, x, y, z, width, facing='south'):
    """一列の操作卓。運転員側（facing）に傾斜面を向け、その後ろに椅子を置く。x, z は西端（または北端）の机。"""
    dx, dz = {'south': (0, 1), 'north': (0, -1), 'east': (1, 0), 'west': (-1, 0)}[facing]
    across = facing in ('east', 'west')
    back = {'south': 'north', 'north': 'south', 'east': 'west', 'west': 'east'}[facing]
    for offset in range(width):
        px, pz = (x, z + offset) if across else (x + offset, z)
        b.set(px, y + 1, pz, f'{MODID}:operator_desk', {'facing': facing})
        if offset % 3 == 1:
            b.set(px + dx, y + 1, pz + dz, f'{MODID}:operator_chair', {'facing': back})


def viewports(b, cx, cz, r0, r1, y0, y1, inside, window_at=None):
    """
    ボス部屋の壁に観測窓を抜く。中心から r0〜r1 の帯の、窓の角度（22.5° ごとに幅 14.5°）の列の y0〜y1 の段にある壁・
    耐圧殻・岩（何もないマス）を耐圧ガラスに替える。窓の上下の段と窓のない肋の列は、何もないマスだけ鋼の骨組みで埋める
    （耐圧殻は残す）。inside(x, y, z) が真のマス（ボス部屋の中）と空気のマス（通路）はそのまま。南北の軸の5列（隔壁の枠）
    と、window_at(角度) が偽の所には窓を抜かない。
    """
    for x in range(math.floor(cx - r1) - 1, math.ceil(cx + r1) + 2):
        for z in range(math.floor(cz - r1) - 1, math.ceil(cz + r1) + 2):
            r = math.hypot(x - cx, z - cz)
            if not r0 <= r < r1:
                continue
            a = math.degrees(math.atan2(z - cz, x - cx)) % 360
            window = 4 <= a % 22.5 <= 18.5 and abs(x - cx) > 2 and (window_at is None or window_at(a))
            for y in range(y0 - 1, y1 + 2):
                current = b.get(x, y, z)
                if current == AIR or inside(x, y, z):
                    continue
                if window and y0 <= y <= y1:
                    b.set(x, y, z, PRESSURE_GLASS, {'sealed': 'true'})
                elif current is None:
                    b.set(x, y, z, FRAME)


# ------------------------------------------------------------------ 最終実験施設

# 床の高さ（その下は重力勾配の試験槽の落とし穴）、ドームの中心、観測回廊の内のり（ドームの中心からの半径）
FL_BASE = 5
FL_DOME = (52, 26)
FL_GALLERY = (20.5, 23.5)
FL_GATE = FL_DOME[1] + 19                 # 隔壁の z（PressureRoomSeal の reach と同じ 19）
# 入口（探索コンパスが真上を指す。ExplorerCompassItem.ENTRANCES と同じ）: 西の連絡坑道の、岩に埋もれた端
FL_ENTRANCE = (0, FL_BASE + 1, 65)


def final_lab():
    """
    最終実験施設「点火棟」（いちばん深い地下）。北の点火室（ドーム、{@link _final_dome}）を観測回廊が一周し、回廊が施設の
    背骨になっている。職員は西の連絡坑道から保安検査所・更衣洗浄室を通って南の点火中央制御室に入り、移送前室を抜けて回廊へ出た。
    封じ込め容器は南東の搬入格納庫から、搬送機が専用の搬送路で移送前室まで運んだ。
    <ul>
      <li>西: 重力勾配試験棟。床の抜けた試験槽で、宙に止まった試料台車を足場に、重い場と浮く場を越えて西の受け台へ渡る</li>
      <li>東: 時間標準棟。遅い場と速い場の場コイルが並ぶ往復の比較廊下。止まったままの職員と、遺構の印刷台。南に標準器保管室</li>
      <li>南東: 搬入格納庫。封じ込め容器の吊り台が並び、中央の荷役台の中継器に近づくと封じ込め搬送機が起動する</li>
    </ul>
    3つの中継器をすべて起動すると、点火室の隔壁が開く（そろうまで守護機は目覚めない）。回廊の内側の壁は耐圧ガラスの
    観測窓で、点火室の中を一周見渡せる。
    """
    b = Builder(104, 26, 92, seed=404)
    Y = FL_BASE
    cx, cz = FL_DOME
    _final_dome(Shifted(b, cx - 17, Y, cz - 17))
    rooms(b, [
        (1, 0, 15, 28, 20, 37, PANEL, DARK),               # 重力勾配試験棟（床は試験槽の底）
        (76, Y, 15, 103, Y + 7, 40, PANEL, CERAMIC),       # 時間標準棟
        (42, Y, 45, 62, Y + 7, 55, PANEL, CERAMIC),        # 移送前室
        (38, Y, 55, 66, Y + 8, 76, CERAMIC, LAB_TILES),    # 点火中央制御室
        (62, Y, 46, 88, Y + 8, 52, GRAPHITE, DARK),        # 搬送路（東西）
        (82, Y, 46, 88, Y + 8, 57, GRAPHITE, DARK),        # 搬送路（南北）
        (70, Y, 57, 100, Y + 12, 87, PANEL, DARK),         # 搬入格納庫
        (82, Y, 87, 88, Y + 6, 90, GRAPHITE, STRIPED),     # 搬送機の充電台（格納庫の南の壁のくぼみ）
        (66, Y, 63, 70, Y + 5, 67, CERAMIC, LAB_TILES),    # 制御室と格納庫の連絡通路
        (0, Y, 62, 14, Y + 5, 68, PANEL, PANEL),           # 連絡坑道
        (14, Y, 58, 27, Y + 6, 72, CERAMIC, LAB_TILES),    # 保安検査所
        (27, Y, 59, 38, Y + 6, 71, CERAMIC, LAB_TILES),    # 更衣・洗浄
    ], between=lambda: _gallery_shell(b, Y))
    # 出入口
    opening(b, cx - 1, Y + 1, cz + 16, cx + 1, Y + 4, FL_GATE - 1)      # 点火室から隔壁まで
    b.fill(cx - 1, Y, cz + 16, cx + 1, Y, FL_GATE - 1, PANEL)
    opening(b, 28, Y + 1, cz - 1, 28, Y + 4, cz + 1)                    # 回廊 → 重力勾配試験棟
    opening(b, 76, Y + 1, cz - 1, 76, Y + 4, cz + 1)                    # 回廊 → 時間標準棟
    opening(b, cx - 1, Y + 1, 55, cx + 1, Y + 4, 55)                    # 移送前室 → 制御室
    opening(b, 62, Y + 1, 47, 62, Y + 7, 51)                            # 移送前室 → 搬送路
    opening(b, 83, Y + 1, 57, 87, Y + 7, 57)                            # 搬送路 → 格納庫
    opening(b, 83, Y + 1, 87, 87, Y + 5, 87)                            # 格納庫 → 充電台
    opening(b, 66, Y + 1, 64, 66, Y + 4, 66)                            # 制御室 → 連絡通路
    opening(b, 70, Y + 1, 64, 70, Y + 4, 66)                            # 連絡通路 → 格納庫
    opening(b, 14, Y + 1, 64, 14, Y + 4, 66)                            # 坑道 → 保安検査所
    opening(b, 0, Y + 1, 63, 0, Y + 4, 67)                              # 坑道の西の端（岩に埋もれている）
    _gallery(b, Y)
    _transfer_lock(b, Y)
    _ignition_control(b, Y)
    _gravity_hall(b, Y)
    _time_wing(b, Y)
    _freight_route(b, Y)
    _hangar(b, Y)
    _staff_entrance(b, Y)
    _fl_android_bays(b, Y)
    seal_chamber(b, cx, cz, Y, FL_GATE, 16, Y + 1, 17, Y)

    def in_dome(x, y, z):
        return math.sqrt((x - cx) ** 2 + (z - cz) ** 2 + ((y - Y - 1) * 0.95) ** 2) < 16 - 0.6
    viewports(b, cx, cz, 14.5, FL_GALLERY[0], Y + 1, Y + 4, in_dome)
    connect_walls(b)
    b.decay(cracked=0.22, mossy=0.12, only_above=Y + 1)
    b.scatter('minecraft:moss_carpet', 30, Y + 1, (cx - 15, cz - 15, cx + 15, cz + 15), floor=(PANEL, LAMP))
    b.scatter('minecraft:moss_carpet', 40, Y + 1, (cx - 24, cz - 24, cx + 24, cz + 24), floor=(CERAMIC,))
    b.scatter('minecraft:moss_carpet', 14, Y + 1, (1, 63, 13, 67), floor=(PANEL,))
    return b


def _fl_android_bays(b, Y):
    """
    施設のあちこちに残った遺構の印刷台（整備用のアンドロイドを印刷した台）。近づくと2〜3体ずつ印刷する。中継器の近くの
    台から出たアンドロイドを倒すまで、中継器は起動できない。
    """
    spots = [
        (16, Y + 1, 70, 2),                                     # 保安検査所（待合の奥）
        (28, Y + 1, 61, 2),                                     # 更衣室（ロッカーの脇）
        (39, Y + 1, 68, 2), (65, Y + 1, 68, 2),                 # 点火中央制御室（両側の壁ぎわ）
        (44, Y + 1, 53, 2),                                     # 移送前室
        (*_gallery_point(160, 23)[:1], Y + 1, _gallery_point(160, 23)[1], 2),   # 観測回廊（南西・北・東）
        (*_gallery_point(270, 23)[:1], Y + 1, _gallery_point(270, 23)[1], 2),
        (*_gallery_point(20, 23)[:1], Y + 1, _gallery_point(20, 23)[1], 2),
        (2, Y + 6, 28, 2),                                      # 重力勾配試験棟の西の受け台（中継器の脇）
        (101, Y + 1, 38, 2),                                    # 場コイルの電源室
        (78, Y + 1, 38, 2),                                     # 標準器保管室
        (75, Y + 1, 47, 2),                                     # 搬送路
    ]
    for x, y, z, r in spots:
        b.fabricator(x, y, z, radius=r)


def _gallery_shell(b, Y):
    """観測回廊の床・天井・外壁と、内側の肋の壁（窓は {@link viewports} が点火室の殻と一緒に抜く）。"""
    cx, cz = FL_DOME
    r_in, r_out = FL_GALLERY
    for x in range(cx - 26, cx + 27):
        for z in range(max(0, cz - 26), cz + 27):
            r = math.hypot(x - cx, z - cz)
            if r_in - 1 <= r < r_in:
                b.fill(x, Y, z, x, Y + 6, z, FRAME)
            elif r_in <= r < r_out:
                b.set(x, Y, z, CERAMIC)
                b.fill(x, Y + 1, z, x, Y + 5, z, AIR)
                b.set(x, Y + 6, z, PANEL)
            elif r_out <= r < r_out + 1.1:
                b.fill(x, Y, z, x, Y + 6, z, PANEL)


def _gallery_point(angle, r):
    cx, cz = FL_DOME
    a = math.radians(angle)
    return round(cx + math.cos(a) * r), round(cz + math.sin(a) * r)


def _gallery(b, Y):
    """
    観測回廊。点火室を一周する廊下で、内側は耐圧ガラスの窓と鋼の肋が交互に並ぶ。点火試験の間、研究者はここから
    点火室を見ていた。肋ごとに記録カメラ、いくつかの窓の前に観測卓。天井ぎわを冷却管と配線棚が一周し、外壁には非常灯と
    非常用の酸素ボンベ。北西・北東の肋の陰に警備機ドック。
    """
    cx, cz = FL_DOME
    for k in range(16):
        rib, win = k * 22.5, k * 22.5 + 11.25
        x, z = _gallery_point(win, 22)
        b.set(x, Y + 6, z, *FACILITY_LIGHT)
        x, z = _gallery_point(rib, 22)
        b.set(x, Y, z, PRISTINE_LIGHT)                               # 肋の前の床の誘導灯
        x, z = _gallery_point(rib, 21)
        b.set(x, Y + 5, z, 'minecraft:observer', {'facing': _facing(cx - x, cz - z), 'powered': 'false'})   # 記録カメラ
        if k % 4 == 2:
            x, z = _gallery_point(rib, 24)
            b.set(x, Y + 3, z, *EMERGENCY_LIGHT)
            x, z = _gallery_point(rib, 23)
            if b.get(x, Y + 1, z) == AIR:
                b.set(x, Y + 1, z, COPPER)                               # 非常用の酸素ボンベ
                b.set(x, Y + 2, z, 'minecraft:lightning_rod', {'facing': 'up', 'powered': 'false', 'waterlogged': 'false'})
    # 観測卓（窓に向かって座る）
    for k in (1, 6, 9, 14):
        win = k * 22.5 + 11.25
        for da in (-3, 3):
            x, z = _gallery_point(win + da, 21)
            out = _facing(x - cx, z - cz)
            b.set(x, Y + 1, z, f'{MODID}:operator_desk', {'facing': out})
            x2, z2 = _gallery_point(win + da, 22)
            if (x2, z2) != (x, z):
                b.set(x2, Y + 1, z2, f'{MODID}:operator_chair', {'facing': _facing(cx - x2, cz - z2)})
    # 天井ぎわの冷却管（外側）と配線棚（内側）。出入口の上は空ける
    for x in range(cx - 25, cx + 26):
        for z in range(max(0, cz - 25), cz + 26):
            r = math.hypot(x - cx, z - cz)
            if b.get(x, Y + 5, z) != AIR:
                continue
            if 22.6 <= r < 23.5 and b.get(x, Y + 4, z) == AIR:
                b.set(x, Y + 5, z, PATINA)
            elif 20.5 <= r < 21.3:
                b.set(x, Y + 5, z, GRAPHITE)
    for angle in (236.25, 303.75):
        x, z = _gallery_point(angle, 23)
        b.dock(x, Y + 1, z, 3)
    room_sign(b, 29, Y + 4, cz - 2, 'east', 'gravity_test')
    room_sign(b, 75, Y + 4, cz - 2, 'west', 'time_standard')
    for x, z in (_gallery_point(160, 23), _gallery_point(20, 23)):
        if b.get(x, Y + 3, z) == AIR:
            room_sign(b, x, Y + 3, z, _facing(cx - x, cz - z), 'observation_gallery')


def _transfer_lock(b, Y):
    """
    移送前室。点火室の隔壁の前で、制御室から来た職員と、搬送路を来た封じ込め容器がここで合流した。隔壁の前は立入禁止の枠、
    東に容器を待たせた空の受け台、隔壁の左右に圧力計と隔壁の操作盤。回廊がこの部屋を横切り、隔壁の左右の観測窓から点火室が見える。
    """
    cx = FL_DOME[0]
    b.fill(cx - 3, Y, 46, cx + 3, Y, 48, STRIPED)
    b.fill(cx - 1, Y, 46, cx + 1, Y, 48, FRAME)
    # 容器の待機台（空。焼けた跡が残る）
    b.fill(56, Y, 50, 60, Y, 53, FRAME)
    b.fill(57, Y, 51, 59, Y, 52, f'{MODID}:scorched_ruin_panel')
    for x in (56, 60):
        for z in (50, 53):
            b.set(x, Y + 1, z, *PANEL_WALL_POST)
    b.fill(56, Y, 49, 60, Y, 49, STRIPED)
    # 隔壁の操作盤と圧力計（西）、非常灯（東西の壁）
    instrument_bank(b, cx - 5, Y, 46, 'south', ('mimic_panel', 'dial_panel'))
    instrument_bank(b, cx + 4, Y, 46, 'south', ('dial_panel', 'mimic_panel'))
    b.set(42, Y + 3, 52, *EMERGENCY_LIGHT)
    b.set(62, Y + 3, 53, *EMERGENCY_LIGHT)
    for x in (45, 52, 59):
        b.set(x, Y + 7, 51, *FACILITY_LIGHT)
    room_sign(b, cx, Y + 5, 46, 'south', 'ignition_chamber', '↑')
    room_sign(b, cx, Y + 5, 54, 'north', 'transfer_lock')


def _ignition_control(b, Y):
    """
    点火中央制御室。北の窓から移送前室と隔壁を直接見られる。窓の下に監視卓、前後2列の運転卓、南の一段高い当直長の壇（記録端末
    と引継ぎ書）、両側の壁に計測盤、南の壁に記録装置の架。西は更衣洗浄室から、東は格納庫への連絡通路へ。
    """
    cx = FL_DOME[0]
    for xa, xb in ((43, 49), (55, 61)):
        b.fill(xa, Y + 2, 55, xb, Y + 4, 55, REINFORCED_GLASS)
        for x in range(xa, xb + 1):
            b.set(x, Y + 1, 56, CABINET)
            if x % 2:
                b.set(x, Y + 2, 56, f'{MODID}:desk_monitor', {'facing': 'south'})
    operating_desk(b, 41, Y, 60, 8)
    operating_desk(b, 56, Y, 60, 8)
    operating_desk(b, 43, Y, 65, 6)
    operating_desk(b, 56, Y, 65, 6)
    for x, facing in ((39, 'east'), (65, 'west')):
        instrument_bank(b, x, Y, 57, facing, ('dial_panel', 'mimic_panel', 'mimic_panel', 'dial_panel', 'annunciator_panel', 'dial_panel'))
    # 当直長の壇
    b.fill(45, Y + 1, 70, 59, Y + 1, 75, LAB_TILES)
    for x in range(cx - 3, cx + 4):
        b.set(x, Y + 1, 69, PANEL_STAIRS, {'facing': 'south', 'half': 'bottom', 'shape': 'straight', 'waterlogged': 'false'})
    for x in (*range(45, cx - 3), *range(cx + 4, 60)):
        b.set(x, Y + 2, 70, *PANEL_WALL_POST)
    operating_desk(b, cx - 3, Y + 1, 71, 7)
    b.set(cx - 4, Y + 2, 71, CABINET)
    terminal(b, cx - 4, Y + 3, 71, 'south', 12)
    b.set(cx + 4, Y + 2, 71, CABINET)
    b.set(cx + 4, Y + 3, 71, f'{MODID}:paper_stack', {'facing': 'south'})
    for x in (*range(40, 45), *range(60, 65)):
        b.fill(x, Y + 1, 75, x, Y + 2, 75, SERVER_RACK, {'facing': 'north', 'powered': 'false'})
    b.dock(40, Y + 1, 70, 3)
    b.dock(64, Y + 1, 70, 3)
    # 床下の配線溝の蓋と、天井の照明
    for x in (cx - 2, cx + 2):
        b.fill(x, Y, 57, x, Y, 68, GRAPHITE)
    for x in (42, 47, 57, 62):
        for z in (58, 63, 68, 73):
            b.fill(x, Y + 8, z, x + 2, Y + 8, z, *FACILITY_LIGHT)
    room_sign(b, cx, Y + 5, 56, 'south', 'ignition_control')


def _gravity_hall(b, Y):
    """
    重力勾配試験棟（西）。床を抜いた試験槽で、天井の走行クレーンが試料を載せた台車を東の操作台から西の受け台まで運んだ。
    異常で台車は宙に止まり、挑戦者はその台車を足場に渡る。重い場（heavy）の上の隙間は跳びにくく、浮く場（lift）に乗ると
    上の段へ持ち上がる。落ちると試験槽の底から南の壁ぞいの階段で操作台へ戻る。西の受け台に中継器と警備機ドック。
    操作台の北の隅には試験を指揮した計測室（窓から試験槽を見下ろす）。
    """
    zc = FL_DOME[1]
    U = Y + 5
    # 試験槽の底: 検出器の列（足場の下を照らす）
    for x in (5, 11, 17, 23):
        b.fill(x, 0, 16, x, 0, 36, FRAME)
        b.fill(x, 0, zc - 3, x, 0, zc + 3, 'minecraft:sea_lantern')
    # 壁: 深さの目盛り（大目盛りは張り出す）と、ガラス張りの計測器
    for z in (15, 37):
        face = z + (1 if z == 15 else -1)
        for x in (4, 10, 16):
            b.fill(x, 2, z, x, 18, z, CERAMIC)
            for y in range(3, 19, 3):
                b.fill(x, y, z, x + (2 if y % 6 == 0 else 1), y, z, PRISTINE_LIGHT)
        for x in (7, 13, 19):
            b.fill(x - 1, 11, z, x + 1, 16, z, FRAME)
            b.fill(x, 12, z, x, 15, z, PRISTINE_LIGHT)
            b.fill(x, 12, face, x, 15, face, OPTIC)
        for x in range(3, 28, 6):
            b.set(x, 17, z, LAMP)
    # 操作台（東）と、北の隅の計測室
    b.fill(22, Y, 16, 27, Y, 36, CERAMIC)
    for z in (*range(21, zc - 1), *range(zc + 2, 34)):
        b.set(22, Y + 1, z, *PANEL_WALL_POST)
    b.fill(23, Y + 1, 16, 27, Y + 4, 20, CERAMIC)
    b.fill(24, Y + 1, 16, 27, Y + 3, 19, AIR)
    b.fill(23, Y + 2, 16, 23, Y + 3, 19, REINFORCED_GLASS)
    door(b, 26, Y + 1, 20, 'south')
    for z in range(16, 20):
        b.set(24, Y + 1, z, f'{MODID}:operator_desk', {'facing': 'east'})
    for z in (17, 19):
        b.set(25, Y + 1, z, f'{MODID}:operator_chair', {'facing': 'west'})
    b.fill(27, Y + 1, 16, 27, Y + 2, 16, SERVER_RACK, {'facing': 'west', 'powered': 'false'})
    b.set(28, Y + 3, 18, *EMERGENCY_LIGHT)
    # 走行クレーンの軌条と、台車を吊るした鎖
    for z in (zc - 2, zc + 2):
        b.fill(2, 19, z, 27, 19, z, FRAME)

    def carriage(x0, x1, y, z0, z1):
        b.fill(x0, y, z0, x1, y, z1, FRAME)
        b.fill(x0, y - 1, z0 + 1, x1, y - 1, z1 - 1, GRAPHITE)
        for x in (x0, x1):
            for z in (zc - 2, zc + 2):
                b.fill(x, y + 1, z, x, 18, z, *CHAIN_Y)
    # 2マスの隙間（下に重い場）→ 台車 → 浮く場 → 上の段の台車 → 2マスの隙間（下に重い場）→ 西の受け台
    emitter(b, 20, 1, zc, 'heavy')
    carriage(17, 19, Y, zc - 2, zc + 2)
    b.fill(15, 1, zc, 15, Y - 1, zc, FRAME)
    b.fill(15, 2, zc, 15, 3, zc, COPPER)
    emitter(b, 15, Y, zc, 'lift')
    carriage(9, 12, U, zc - 3, zc + 3)
    b.fill(7, 1, zc, 7, Y, zc, FRAME)
    emitter(b, 7, Y + 1, zc, 'heavy')
    for ex, ey in ((20, 1), (15, 1), (7, 1)):
        for dx, dz in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
            b.set(ex + dx, ey, zc + dz, GRILLE)                          # 発生器の台座の放熱格子
    # 西の受け台: 試料の保管棚、中継器、警備機ドック
    b.fill(2, U, 16, 6, U, 36, CERAMIC)
    for z in (16, 36):
        b.fill(6, 1, z, 6, U - 1, z, FRAME)
    for z in (*range(17, zc - 1), *range(zc + 2, 36)):
        b.set(6, U + 1, z, *PANEL_WALL_POST)
    for z in (*range(19, 23), *range(30, 34)):
        b.fill(2, U + 1, z, 2, U + 2, z, CABINET)
        b.set(2, U + 3, z, OPTIC)
    relay(b, 3, U + 1, zc, 'plain')
    b.dock(3, U + 1, 17, 3)
    b.dock(3, U + 1, 35, 3)
    # 試料の重り（クレーンから吊ったまま、隙間の上で止まっている）
    b.fill(20, 15, zc, 20, 19, zc, *CHAIN_Y)
    b.set(20, 14, zc, 'minecraft:crying_obsidian')
    b.set(20, 13, zc, FRAME)
    # 底から操作台へ戻る階段（南の壁ぞい）
    for i, x in enumerate(range(17, 22)):
        for z in (35, 36):
            b.fill(x, 1, z, x, i, z, PANEL)
            b.set(x, i + 1, z, PANEL_STAIRS, {'facing': 'east', 'half': 'bottom', 'shape': 'straight', 'waterlogged': 'false'})
    # 宙に浮いたまま止まった破片（浮く場の上と、跳ぶ高さは空けておく）
    for _ in range(36):
        x, y, z = b.rng.randint(3, 26), b.rng.randint(U + 4, 17), b.rng.randint(17, 35)
        if abs(x - 15) > 3 and b.get(x, y, z) == AIR:
            b.set(x, y, z, b.rng.choice((CRACKED, FRAME, 'minecraft:obsidian', SCREEN)))


def _time_wing(b, Y):
    """
    時間標準棟（東）。時間の場の中で時計を比べた往復の比較廊下。回廊から往路（南）を東へ進み、東の端で折り返して復路（北）を
    西へ戻る。往路と復路の間の厚い壁には時間標準器が収まり、両側の窓から読めた。床の発生器が遅い場（slow）と速い場（fast）を
    張り、場の境目ごとに場コイルの枠が立つ。敵（印刷台のアンドロイド）にも同じように効く。遅い場には、動きの途中で止まった
    ままの職員の姿が残る。南に標準器保管室（封印コンテナ）と場コイルの電源室。
    """
    zA, zB = 26, 18
    b.fill(77, Y + 1, 21, 97, Y + 6, 23, GRAPHITE)                   # 往路と復路の間の壁（中に標準器）
    b.fill(77, Y + 1, 29, 102, Y + 6, 29, PANEL)                     # 南の部屋との壁
    b.fill(89, Y + 1, 30, 89, Y + 6, 39, PANEL)
    for x in (82, 88, 94):
        lens = PHASE_GLASS if x == 88 else OPTIC
        for z in (21, 23):
            b.fill(x, Y + 2, z, x + 1, Y + 4, z, lens)
        b.fill(x, Y + 2, 22, x + 1, Y + 4, 22, AIR)
        b.set(x, Y + 2, 22, PRISTINE_LIGHT)
        b.set(x + 1, Y + 2, 22, 'minecraft:amethyst_block')
        b.set(x + 1, Y + 3, 22, 'minecraft:amethyst_cluster', {'facing': 'up', 'waterlogged': 'false'})
    b.fill(100, Y + 1, 22, 100, Y + 6, 22, PRISTINE_PILLAR)          # 折り返しの柱

    def field(x, zc, kind):
        emitter(b, x, Y, zc, kind)
        trim = 'minecraft:purple_concrete' if kind == 'slow' else 'minecraft:cyan_concrete'
        for z in (zc - 2, zc + 2):
            b.fill(x - 2, Y, z, x + 2, Y, z, trim)
        b.set(x, Y + 7, zc, 'minecraft:sea_lantern')

    def coil(x, zc):
        """場の境目の場コイルの枠（壁と天井を巡る銅の帯）。"""
        for z in (zc - 3, zc + 3):
            if b.get(x, Y + 1, z) not in (AIR, None):
                b.fill(x, Y + 1, z, x, Y + 6, z, COPPER)
        b.fill(x, Y + 7, zc - 2, x, Y + 7, zc + 2, COPPER)
    for x, kind in ((82, 'slow'), (88, 'fast'), (94, 'slow')):
        field(x, zA, kind)
    for x, kind in ((96, 'fast'), (90, 'slow'), (84, 'fast')):
        field(x, zB, kind)
    for x in (79, 85, 91, 97):
        coil(x, zA)
    for x in (81, 87, 93, 99):
        coil(x, zB)
    for x in range(78, 102, 4):
        for z in (zA, zB):
            if b.get(x, Y + 7, z) == PANEL:
                b.set(x, Y + 7, z, *FACILITY_LIGHT)
    frozen_worker(b, 82, Y + 1, 27, 270, 'walk')
    frozen_worker(b, 94, Y + 1, 24, 180, 'reach')
    frozen_worker(b, 90, Y + 1, 17, 90, 'fall')
    b.fabricator(91, Y + 1, 28, radius=2)
    b.fabricator(87, Y + 1, 16, radius=2)
    relay(b, 78, Y + 1, zB, 'plain')
    # 標準器保管室（西）: 標準器の架が2列、奥に封印コンテナ（時間錠）
    door(b, 83, Y + 1, 29, 'north')
    room_sign(b, 82, Y + 3, 28, 'north', 'metrology')
    for z in (32, 35):
        for x in range(79, 88):
            b.fill(x, Y + 1, z, x, Y + 2, z, CABINET)
            b.set(x, Y + 3, z, PHASE_GLASS if x % 2 else OPTIC)
        b.fill(79, Y + 6, z, 87, Y + 6, z, *FACILITY_LIGHT)
    b.sealed(87, Y + 1, 38, 3)
    # 場コイルの電源室（東）: 変圧器と、配線棚、配電盤
    opening(b, 95, Y + 1, 29, 97, Y + 4, 29)
    room_sign(b, 98, Y + 3, 28, 'north', 'coil_power')
    for x in (91, 95, 99):
        b.fill(x, Y + 1, 34, x + 1, Y + 3, 35, COPPER)
        b.fill(x, Y + 1, 36, x + 1, Y + 1, 36, GRILLE)
        b.set(x, Y + 4, 34, 'minecraft:lightning_rod', {'facing': 'up', 'powered': 'false', 'waterlogged': 'false'})
        b.set(x + 1, Y + 4, 35, 'minecraft:lightning_rod', {'facing': 'up', 'powered': 'false', 'waterlogged': 'false'})
    b.fill(90, Y + 6, 34, 102, Y + 6, 35, GRAPHITE)
    instrument_bank(b, 93, Y, 39, 'north', ('dial_panel', 'dial_panel', 'mimic_panel', 'dial_panel', 'dial_panel'))
    b.set(101, Y + 3, 30, *EMERGENCY_LIGHT)


def _freight_route(b, Y):
    """搬送路。格納庫から移送前室まで、封じ込め容器を運ぶ搬送機が通った天井の高い通路。床に誘導の銅帯、壁ぎわに縞の帯。"""
    for x in range(63, 88):
        b.set(x, Y, 47, STRIPED)
        b.set(x, Y, 51, STRIPED)
    b.fill(63, Y, 49, 85, Y, 49, COPPER)
    for z in range(47, 57):
        b.set(83, Y, z, STRIPED)
        b.set(87, Y, z, STRIPED)
    b.fill(85, Y, 49, 85, Y, 56, COPPER)
    for x in range(66, 88, 6):
        b.set(x, Y + 8, 49, LAMP)
    for z in (54,):
        b.set(85, Y + 8, z, LAMP)
    # 移送前室の入口の、荷の高さを測る枠
    b.fill(63, Y + 1, 47, 63, Y + 7, 47, STRIPED)
    b.fill(63, Y + 1, 51, 63, Y + 7, 51, STRIPED)
    b.fill(63, Y + 7, 47, 63, Y + 7, 51, STRIPED)


def _hangar(b, Y):
    """
    搬入格納庫（南東）。地上から荷物昇降機で下ろした封じ込め容器を、吊り台に並べて点火まで保管した。容器の吊り台が4つ並ぶ
    （1つは搬送機が運び出したまま空）。中央の荷役台の中継器に挑戦者が近づくと、封じ込め搬送機が起動する（倒すと中継器を
    起動できる）。吊り台の柱は、突進してくる搬送機をぶつける壁になる。西の壁ぎわに搬送機の点検ピット（格子の蓋）と点検記録の
    端末、南の壁のくぼみに搬送機の充電台、北東の隅に崩れた荷物昇降機、南東の隅に部品庫（封印コンテナ）。
    """
    cx, cz = 85, 72
    wall_band(b, 70, 57, 100, 87, Y + 1, Y + 3, DARK)
    wall_band(b, 70, 57, 100, 87, Y + 4, Y + 9, VENT)
    opening(b, 83, Y + 1, 57, 87, Y + 7, 57)
    opening(b, 83, Y + 1, 87, 87, Y + 5, 87)
    opening(b, 70, Y + 1, 64, 70, Y + 4, 66)
    # 床: 荷役台（中心）と、搬送機の走路の縞
    for x in range(71, 100):
        for z in range(58, 87):
            d = math.hypot(x - cx, z - cz)
            if d <= 4.3:
                b.set(x, Y, z, FRAME)
            elif 5.5 <= d <= 6.2:
                b.set(x, Y, z, STRIPED)
    for x in (83, 87):
        b.fill(x, Y, 58, x, Y, 65, STRIPED)
    relay(b, cx, Y + 1, cz, 'guarded', 'containment_hauler')
    # 封じ込め容器の吊り台
    for gx, gz in ((78, 65), (92, 65), (78, 79), (92, 79)):
        b.fill(gx - 1, Y, gz - 1, gx + 1, Y, gz + 1, FRAME)
        for lx in (gx - 2, gx + 2):
            b.fill(lx, Y + 1, gz, lx, Y + 8, gz, PRISTINE_PILLAR)
        b.fill(gx - 2, Y + 9, gz, gx + 2, Y + 9, gz, FRAME)
        b.fill(gx, Y + 6, gz, gx, Y + 8, gz, *CHAIN_Y)
        b.set(gx, Y + 1, gz, FRAME)                                 # 受け台
        for dx, dz in ((-1, 0), (1, 0)):
            b.set(gx + dx, Y, gz + dz, 'minecraft:sea_lantern')       # 足元の検査灯
        if (gx, gz) == (92, 79):
            continue                                                # 搬送機が運び出した空の吊り台
        b.fill(gx, Y + 2, gz, gx, Y + 4, gz, OPTIC)                 # 封じ込め容器（青いガラスの筒）
        b.set(gx, Y + 5, gz, FRAME)
    # 天井の走行クレーン（昇降機の手前まで）
    for x in (72, 98):
        b.fill(x, Y + 11, 65, x, Y + 11, 86, GRAPHITE)
        b.set(x, Y + 11, 65, 'minecraft:red_concrete')
    b.fill(72, Y + 10, 74, 98, Y + 10, 74, STRIPED)
    b.fill(80, Y + 7, 74, 80, Y + 9, 74, *CHAIN_Y)
    b.set(80, Y + 6, 74, 'minecraft:grindstone', {'face': 'ceiling', 'facing': 'east'})
    for x in range(73, 100, 5):
        for z in range(60, 87, 5):
            b.set(x, Y + 12, z, LAMP)
    # 点検ピット（西）: 格子の蓋の下に昇降台と作業灯。脇に点検記録の端末
    b.fill(72, Y - 3, 67, 76, Y - 1, 77, PANEL)
    b.fill(73, Y - 2, 68, 75, Y - 1, 76, AIR)
    for z in (69, 75):
        b.fill(74, Y - 2, z, 74, Y - 1, z, COPPER)                   # 昇降台の柱
    for z in (68, 72, 76):
        b.set(72, Y - 2, z, PRISTINE_LIGHT)
        b.set(76, Y - 2, z, PRISTINE_LIGHT)
    b.fill(73, Y, 68, 75, Y, 76, GRILLE)
    for z in range(67, 78):
        b.set(72, Y, z, STRIPED)
        b.set(76, Y, z, STRIPED)
    b.fill(73, Y, 67, 75, Y, 67, STRIPED)
    b.fill(73, Y, 77, 75, Y, 77, STRIPED)
    b.set(71, Y + 1, 72, CABINET)
    terminal(b, 71, Y + 2, 72, 'east', 13)
    b.set(71, Y + 1, 70, f'{MODID}:paper_stack', {'facing': 'east'})
    # 充電台（南の壁のくぼみ）: 奥の壁の銅の接点
    b.fill(83, Y + 1, 90, 87, Y + 2, 90, COPPER)
    for x in (84, 86):
        b.set(x, Y + 3, 89, 'minecraft:lightning_rod', {'facing': 'north', 'powered': 'false', 'waterlogged': 'false'})
    b.set(85, Y + 5, 89, *EMERGENCY_LIGHT)
    # 崩れた荷物昇降機（北東）: 荷台と、上の立坑をふさぐ瓦礫
    b.fill(94, Y, 58, 99, Y, 63, FRAME)
    b.fill(95, Y + 12, 59, 98, Y + 12, 62, AIR)
    b.box(94, Y + 12, 58, 99, 25, 63, FRAME)
    b.fill(95, Y + 12, 59, 98, 22, 62, AIR)
    for x in (95, 98):
        for z in (59, 62):
            b.fill(x, Y + 1, z, x, 21, z, *CHAIN_Y)
    for y in range(22, 25):
        for x in range(95, 99):
            for z in range(59, 63):
                b.set(x, y, z, b.rng.choice((CRACKED, 'minecraft:cobbled_deepslate', 'minecraft:gravel', PANEL)))
    b.fill(93, Y, 58, 93, Y, 64, STRIPED)
    b.fill(93, Y, 64, 99, Y, 64, STRIPED)
    # 部品庫（南東）: 鉄格子の仕切りと棚、封印コンテナ
    bars(b, 93, 81, 99, 81, Y + 1, Y + 3, skip={(95, 81)})
    bars(b, 93, 82, 93, 86, Y + 1, Y + 3)
    for x in (96, 97, 98, 99):
        b.fill(x, Y + 1, 86, x, Y + 3, 86, FRAME)
        b.set(x, Y + 2, 86, PANEL_SLAB, {'type': 'top', 'waterlogged': 'false'})
    b.sealed(98, Y + 1, 84, 4)
    room_sign(b, cx, Y + 8, 58, 'south', 'hangar')


def _staff_entrance(b, Y):
    """
    職員の入構路。西の連絡坑道（人員輸送の軌道が残る。西の端は岩に埋もれている）から保安検査所に入り、身分照合のゲートを
    通って、更衣・洗浄室で私服から作業着に着替え、洗浄室を抜けて点火中央制御室へ。
    """
    # 連絡坑道: 4マスおきの支保の枠と、床の軌道
    for x in range(2, 14, 4):
        for z in (63, 67):
            b.fill(x, Y + 1, z, x, Y + 4, z, FRAME)
        b.fill(x, Y + 4, 63, x, Y + 4, 67, FRAME)
        b.set(x, Y + 5, 65, LAMP)
    b.fill(1, Y + 1, 65, 12, Y + 1, 65, 'minecraft:rail', {'shape': 'east_west', 'waterlogged': 'false'})
    b.set(13, Y + 1, 65, PANEL_SLAB, {'type': 'bottom', 'waterlogged': 'false'})        # 車止め
    for y in range(Y + 1, Y + 4):
        for z in range(63, 68):
            if b.rng.random() < 0.55 - 0.15 * (y - Y - 1):
                b.set(0, y, z, b.rng.choice(('minecraft:cobbled_deepslate', 'minecraft:gravel', CRACKED)))
    b.fabricator(6, Y + 1, 64, radius=2)
    # 保安検査所: 北に守衛室（受付の窓と監視画面）、真ん中に照合ゲートの並ぶ鉄格子の仕切り
    b.fill(15, Y + 1, 62, 24, Y + 5, 62, CERAMIC)
    b.fill(25, Y + 1, 59, 25, Y + 5, 62, CERAMIC)
    b.fill(16, Y + 1, 62, 23, Y + 1, 62, CABINET)
    b.fill(16, Y + 2, 62, 23, Y + 3, 62, REINFORCED_GLASS)
    for x in (18, 21):
        b.set(x, Y + 2, 62, f'{MODID}:desk_monitor', {'facing': 'north'})
    for x in (17, 20, 23):
        b.set(x, Y + 1, 61, f'{MODID}:operator_chair', {'facing': 'south'})
    b.fill(15, Y + 1, 59, 15, Y + 2, 61, SERVER_RACK, {'facing': 'east', 'powered': 'false'})
    door(b, 25, Y + 1, 60, 'east')
    b.set(24, Y + 4, 59, *EMERGENCY_LIGHT)
    bars(b, 21, 63, 21, 71, Y + 1, Y + 3, skip={(21, 65), (21, 69)})
    for gz in (65, 69):
        for z in (gz - 1, gz + 1):
            b.fill(21, Y + 1, z, 21, Y + 3, z, FRAME)
        b.set(21, Y + 3, gz, 'minecraft:sea_lantern')                # 照合ゲートの走査灯
        b.set(20, Y + 2, gz - 1, 'minecraft:stone_button', {'face': 'wall', 'facing': 'west'})
    for x in range(16, 20):
        b.set(x, Y + 1, 71, PANEL_SLAB, {'type': 'bottom', 'waterlogged': 'false'})   # 待合の長椅子
    for x in (17, 23):
        b.fill(x, Y + 6, 66, x, Y + 6, 68, *FACILITY_LIGHT)
    room_sign(b, 15, Y + 5, 65, 'east', 'security')
    # 更衣・洗浄室: 西は私服のロッカー、真ん中は洗浄の通路（格子の床とシャワー）、東は作業着の棚
    door(b, 27, Y + 1, 65, 'east')
    for z in (60, 70):
        for x in range(28, 32):
            b.fill(x, Y + 1, z, x, Y + 3, z, VENT)
            b.set(x, Y + 2, z + (1 if z == 60 else -1), 'minecraft:stone_button',
                  {'face': 'wall', 'facing': 'south' if z == 60 else 'north'})
    b.fill(29, Y + 1, 63, 30, Y + 1, 63, PANEL_SLAB, {'type': 'bottom', 'waterlogged': 'false'})
    b.fill(29, Y + 1, 67, 30, Y + 1, 67, PANEL_SLAB, {'type': 'bottom', 'waterlogged': 'false'})
    for x in (32, 35):
        b.fill(x, Y + 1, 60, x, Y + 5, 70, CERAMIC)
        b.fill(x, Y + 1, 64, x, Y + 3, 66, AIR)
    b.fill(33, Y, 60, 34, Y, 70, GRILLE)
    for z in range(61, 70, 2):
        for x in (33, 34):
            b.set(x, Y + 4, z, 'minecraft:lightning_rod', {'facing': 'down', 'powered': 'false', 'waterlogged': 'false'})
    for z in (*range(60, 63), *range(68, 71)):
        b.fill(37, Y + 1, z, 37, Y + 3, z, CABINET)
        b.set(36, Y + 1, z, PANEL_SLAB, {'type': 'bottom', 'waterlogged': 'false'})
    door(b, 38, Y + 1, 65, 'east')
    for x in (30, 36):
        b.fill(x, Y + 6, 64, x, Y + 6, 66, *FACILITY_LIGHT)
    room_sign(b, 28, Y + 4, 64, 'east', 'changing')


# ------------------------------------------------------------------ 沈んだ炉心区画

def _sunken_sphere(b):
    """
    沈んだ炉心区画の封じ込め室（ボス部屋）。旧文明が二基目のペンローズ炉を据えた球形の部屋で、点火の事故で炉は止まり、
    区画ごと岩の底へ沈んだ。床の中心には止まった炉心の跡（番人の封印核）、まわりに6本の封じ込め柱と途切れた足場の環。
    宙には剥がれた炉殻の破片が止まっている。炉心の渦にはエルゴ球の大蛇が眠り、北の壇の保管庫は力場で封鎖されている。
    南の通路から入る。b は球の西北の角を原点にした座標（{@link Shifted}）。
    """
    cx, cz, cy = 23, 23, 18                 # 球の中心
    R, RY = 21, 16                          # 水平の半径と、縦の半径（少しつぶした球）
    FLOOR = 8
    def dist(x, y, z):
        return math.sqrt((x - cx) ** 2 + (z - cz) ** 2 + ((y - cy) * R / RY) ** 2)
    for x in range(47):
        for z in range(47):
            for y in range(0, 36):
                d = dist(x, y, z)
                if R - 0.8 <= d <= R + 0.5:
                    seam = y % 6 == 0 and (x + z) % 3 == 0
                    b.set(x, y, z, LAMP if seam else (FRAME if (x * 7 + z * 3 + y) % 11 == 0 else PANEL))
                elif d < R - 0.8:
                    if y < FLOOR:
                        b.set(x, y, z, DARK)
                    elif y == FLOOR:
                        b.set(x, y, z, PANEL)
                    else:
                        b.set(x, y, z, AIR)
    # 床の模様: 止まった炉心の台（中心）、光の環、警告の縞の環（大蛇が泳ぐ下）
    for x in range(47):
        for z in range(47):
            if b.get(x, FLOOR, z) != PANEL:
                continue
            d = math.hypot(x - cx, z - cz)
            if d <= 3.3:
                b.set(x, FLOOR, z, FRAME)
            elif 5.5 <= d <= 6.2 and (x + z) % 2 == 0:
                b.set(x, FLOOR, z, LAMP)
            elif 9.5 <= d <= 10.4:
                b.set(x, FLOOR, z, STRIPED)
            elif 14.5 <= d <= 15.2 and (x + z) % 3 == 0:
                b.set(x, FLOOR, z, LAMP)
    b.set(cx, FLOOR, cz, CORE)              # 番人の封印核（上は大蛇が這い出すので空けておく）
    for dx, dz in ((-2, 0), (2, 0), (0, -2), (0, 2)):
        b.set(cx + dx, FLOOR + 1, cz + dz, 'minecraft:end_rod', {'facing': 'up'})
    # 6本の封じ込め柱（白い柱と光、上は鎖で天井へ）
    for k in range(6):
        a = math.pi * 2 * k / 6
        x, z = round(cx + math.cos(a) * 13), round(cz + math.sin(a) * 13)
        b.fill(x, FLOOR + 1, z, x, FLOOR + 11, z, PRISTINE_PILLAR)
        b.set(x, FLOOR + 2, z, REINFORCED_GLASS)            # 床から2段目は強化ガラス（陰に隠れても、炉心の光が透けて見える）
        b.set(x, FLOOR + 6, z, PRISTINE_LIGHT)
        b.set(x, FLOOR + 12, z, LAMP)
        top = next((y for y in range(FLOOR + 13, 36) if b.get(x, y, z) not in (None, AIR)), 35)
        b.fill(x, FLOOR + 13, z, x, top - 1, z, *CHAIN_Y)
    # 途切れた足場の環（高さ FLOOR+9、半径15〜16。4か所が崩れ落ちている）
    slab = ('minecraft:polished_deepslate_slab', {'type': 'bottom', 'waterlogged': 'false'})
    for x in range(47):
        for z in range(47):
            d = math.hypot(x - cx, z - cz)
            a = math.degrees(math.atan2(z - cz, x - cx)) % 360
            if 14.6 <= d <= 16.4 and not any(abs(a - g) < 9 for g in (35, 125, 215, 305)):
                if b.get(x, FLOOR + 9, z) == AIR:
                    b.set(x, FLOOR + 9, z, *slab)
    # 北の壇と保管庫（大蛇を倒すまで力場で封鎖）
    vz = cz - 14
    for x in range(cx - 3, cx + 4):
        for z in range(vz - 2, vz + 2):
            if b.get(x, FLOOR + 1, z) == AIR:
                b.set(x, FLOOR + 1, z, PANEL)
    for dx in range(-3, 4):
        if b.get(cx + dx, FLOOR + 1, vz + 2) == AIR:
            b.set(cx + dx, FLOOR + 1, vz + 2, PANEL_STAIRS, {'facing': 'north', 'half': 'bottom', 'shape': 'straight',
                                                                      'waterlogged': 'false'})
    b.cache(cx, FLOOR + 2, vz, 'sunken_reactor', sealed=True)
    b.set(cx, FLOOR + 2, vz - 1, SCREEN)
    b.set(cx, FLOOR + 3, vz - 1, LAMP)
    b.sealed(cx - 2, FLOOR + 2, vz, 4)
    b.set(cx + 2, FLOOR + 2, vz, FRAME)
    b.set(cx + 2, FLOOR + 3, vz, 'minecraft:end_rod', {'facing': 'up'})
    # 南の通路（隔壁まで。隔壁の外は炉室前室）
    for z in range(cz + 14, 46):
        b.fill(cx - 2, FLOOR, z, cx + 2, FLOOR + 5, z, PANEL)
        b.fill(cx - 1, FLOOR + 1, z, cx + 1, FLOOR + 4, z, AIR)
        if z % 4 == 0:
            b.set(cx, FLOOR + 5, z, LAMP)
    # 剥がれた炉殻の破片が宙に止まっている（重力の乱れ）
    for _ in range(60):
        x, y, z = cx + b.rng.randint(-17, 17), b.rng.randint(FLOOR + 3, 32), cz + b.rng.randint(-17, 17)
        if b.get(x, y, z) == AIR and math.hypot(x - cx, z - cz) > 4 and dist(x, y, z) < R - 3:
            b.set(x, y, z, b.rng.choice((FRAME, DARK, CRACKED, 'minecraft:obsidian', 'minecraft:crying_obsidian')))
    # 床の割れ目
    for _ in range(30):
        x, z = cx + b.rng.randint(-15, 15), cz + b.rng.randint(-15, 15)
        if b.get(x, FLOOR, z) == PANEL and math.hypot(x - cx, z - cz) > 4:
            b.set(x, FLOOR, z, CRACKED)


# 沈んだ炉心区画の前室。球の西北の角、球の中心、床の高さ、隔壁の z（PressureRoomSeal の reach と同じ 23）
SR_SPHERE = (8, 7)
SR_CX, SR_CZ, SR_FLOOR = 31, 30, 8
SR_GATE = SR_CZ + 23
# 入口（探索コンパスが真上を指す。ExplorerCompassItem.ENTRANCES と同じ）: 崩れた昇降機の立坑の上の端
SR_ENTRANCE = (48, 35, 82)
# 外周点検回廊: 内のりの半径（内側は観測窓と肋、外側は外壁）と、通る角度（南の炉室前室の左右をあけたC字。
# 度、atan2(z, x) を RING_A0 から測る）
RING_IN, RING_OUT = 23, 26
RING_A0, RING_A1 = 110, 430
RING_SEGMENTS = 4
RING_STATION = 5                     # 各区画の終わりの、流れない踊り場（度）
RING_PITS = (18, 40, 62)             # 各区画の落とし穴の位置（区画の始まりからの度）
RING_PIT_WIDTH = 5


def sunken_reactor():
    """
    沈んだ炉心区画「第二炉」（最も深い地下、段階5）。北に球形の炉室（ボス部屋、{@link _sunken_sphere}）があり、その
    まわりを外周点検回廊がC字に巡る。職員は崩れた昇降機から昇降機ホールに降り、中央通路を北へ進んで第二炉中央制御室に入った。
    制御室の北は炉室前室（隔壁）、西は冷却系統、東は抽出塔。中央通路の南は炉殻の修理場。
    <ul>
      <li>外周点検回廊: 炉の回転に引きずられて、床が入口の方へ流れ続ける。各区画の終わりの制動器で止まる。内側の壁は耐圧ガラスの
          観測窓で、炉室の中が見える</li>
      <li>西: 冷却系統。潜水準備室の立坑から水没した保守トンネルを泳ぎ、天井の空気だまりで息を継いで、ポンプ室の排水弁の中継器で水を抜く</li>
      <li>東: 抽出塔。中央の渦の穴へ物（や敵）を落とすとエネルギーが返り、たまると穴の底の中継器が起動する。修理場から出た
          炉殻の破片も、搬送路でここへ運んで投げ込んでいた</li>
      <li>南: 炉殻の修理場。中央の中継器に近づくと、暴走した炉殻修理機が起動する</li>
    </ul>
    4つの中継器をすべて起動すると、炉室の隔壁が開く（そろうまで大蛇は目覚めない）。前室にはエルゴ球の大蛇とその眷属を
    置かない（ボス部屋で初めて出会うように）。
    """
    b = Builder(68, 36, 112, seed=505)
    F = SR_FLOOR
    _sunken_sphere(Shifted(b, SR_SPHERE[0], 0, SR_SPHERE[1]))
    rooms(b, [
        (25, F, 53, 37, F + 7, 62, PANEL, CERAMIC),        # 炉室前室
        (20, F, 53, 25, F + 5, 59, PANEL, PANEL),          # 外周点検回廊の入口
        (18, F, 62, 44, F + 8, 78, CERAMIC, LAB_TILES),    # 第二炉中央制御室
        (2, F, 64, 18, F + 6, 78, PANEL, PATINA),          # 潜水準備室
        (44, F, 66, 49, F + 6, 74, CERAMIC, LAB_TILES),    # 投入準備室（抽出塔の前）
        (28, F, 78, 34, F + 6, 86, CERAMIC, LAB_TILES),    # 中央通路
        (34, F, 78, 52, F + 7, 86, CERAMIC, LAB_TILES),    # 昇降機ホール
        (16, F, 86, 46, F + 12, 111, PANEL, GRAPHITE),     # 炉殻の修理場
        (46, F, 89, 58, F + 6, 93, GRAPHITE, DARK),        # 破片の搬送路（東西）
        (54, F, 77, 58, F + 6, 93, GRAPHITE, DARK),        # 破片の搬送路（南北）
    ])
    opening(b, 25, F + 1, 56, 25, F + 4, 58)                      # 前室 → 回廊の入口
    opening(b, SR_CX - 1, F + 1, 62, SR_CX + 1, F + 4, 62)        # 前室 → 制御室
    opening(b, 18, F + 1, 69, 18, F + 4, 71)                      # 制御室 → 潜水準備室
    opening(b, 44, F + 1, 69, 44, F + 4, 71)                      # 制御室 → 投入準備室
    opening(b, SR_CX - 1, F + 1, 78, SR_CX + 1, F + 4, 78)        # 制御室 → 中央通路
    opening(b, 34, F + 1, 81, 34, F + 4, 83)                      # 中央通路 → 昇降機ホール
    opening(b, SR_CX - 1, F + 1, 86, SR_CX + 1, F + 5, 86)        # 中央通路 → 修理場
    opening(b, 46, F + 1, 90, 46, F + 4, 92)                      # 修理場 → 破片の搬送路
    _drag_ring(b, F)
    _reactor_lock(b, F)
    _unit2_control(b, F)
    _diving_room(b, F)
    _flooded_wing(b, F)
    _extraction_tower(b, F)
    _elevator_hall(b, F)
    _workshop(b, F)
    _sr_android_bays(b, F)
    seal_chamber(b, SR_CX, SR_CZ, F, SR_GATE, 21, 18, 16, 2)

    def in_sphere(x, y, z):
        return math.sqrt((x - SR_CX) ** 2 + (z - SR_CZ) ** 2 + ((y - 18) * 21 / 16) ** 2) < 21 - 0.8

    def by_ring(a):
        t = a if a >= RING_A0 else a + 360
        return RING_A0 + 2 <= t <= RING_A1 - 2
    viewports(b, SR_CX, SR_CZ, 14, RING_IN, F + 1, F + 4, in_sphere, by_ring)
    # 隔壁の左右の観測窓（炉室前室から炉室をのぞく）
    viewports(b, SR_CX, SR_CZ, 14, RING_IN, F + 1, F + 4, in_sphere,
              lambda a: 73 <= a <= 82 or 98 <= a <= 107)
    for x in (*range(26, 29), *range(34, 37)):
        b.fill(x, F + 1, SR_GATE, x, F + 4, SR_GATE, PRESSURE_GLASS, {'sealed': 'true'})
    connect_walls(b)
    b.decay(cracked=0.18, mossy=0.04, only_above=F + 1)
    b.scatter('minecraft:cobweb', 14, 30, (SR_SPHERE[0] + 6, SR_SPHERE[1] + 6, SR_SPHERE[0] + 40, SR_SPHERE[1] + 40), floor=(AIR,))
    b.scatter('minecraft:cobweb', 10, F + 6, (35, 79, 51, 87), floor=(AIR,))
    b.scatter('minecraft:cobweb', 8, F + 5, (3, 65, 17, 77), floor=(AIR,))
    return b


def _sr_android_bays(b, F):
    """
    第二炉の各所に残った遺構の印刷台（保守用のアンドロイドを印刷した台）。近づくと2〜3体ずつ印刷する。外周点検回廊の
    途中の踊り場の台から出たアンドロイドは、流れる床の上で襲ってくる。
    """
    seg = (RING_A1 - RING_A0) / RING_SEGMENTS
    ring = [_ring_point(RING_A0 + (k + 1) * seg - RING_STATION / 2, RING_IN + 0.4) for k in (1,)]
    spots = [
        (50, F + 1, 85, 2),                                     # 昇降機ホール
        (19, F + 1, 66, 2), (43, F + 1, 66, 2),                 # 第二炉中央制御室
        (26, F + 1, 60, 2),                                     # 炉室前室
        (16, F + 1, 76, 2),                                     # 潜水準備室
        (45, F + 1, 72, 2),                                     # 投入準備室
        (55, F + 1, 88, 2),                                     # 破片の搬送路
        (29, F + 1, 84, 2),                                     # 中央通路
    ] + [(x, F + 1, z, 2) for x, z in ring]
    for x, y, z, r in spots:
        b.fabricator(x, y, z, radius=r)


def _ring_cell(x, z):
    """外周点検回廊の (x, z) の、球の中心からの半径と、回廊の角度（RING_A0 から増える向き）。"""
    r = math.hypot(x - SR_CX, z - SR_CZ)
    a = math.degrees(math.atan2(z - SR_CZ, x - SR_CX)) % 360
    t = a if a >= RING_A0 else a + 360
    return r, t


def _ring_point(t, r):
    a = math.radians(t)
    return round(SR_CX + math.cos(a) * r), round(SR_CZ + math.sin(a) * r)


def _drag_ring(b, F):
    """
    外周点検回廊（フレームドラッグの環状回廊）。炉室の球のまわりをC字に巡る幅3の廊下で、炉の回転に引きずられた床が入口
    （炉室前室の西）の方へ流れ続ける。4つの区画に分かれ、各区画に3つの落とし穴（底から入口側のはしごで戻る）。区画の終わりの
    踊り場の制動器を下ろすと、その区画の流れが止まる。最後の区画の踊り場に中継器。区画1・3の踊り場には警備機ドック。
    内側の壁は耐圧ガラスの観測窓と鋼の肋（窓は {@link viewports} が抜く）、外側の壁には導体と発光帯が巡る。
    """
    seg = (RING_A1 - RING_A0) / RING_SEGMENTS
    for x in range(b.size[0]):
        for z in range(0, 60):
            r, t = _ring_cell(x, z)
            cap = RING_A1 < t <= RING_A1 + 3 or t >= 360 + RING_A0 - 3      # C字の両端をふさぐ壁
            if not (RING_IN - 1 <= r < RING_OUT + 1 and (RING_A0 <= t <= RING_A1 or cap)):
                continue
            inside = RING_IN <= r < RING_OUT and RING_A0 <= t <= RING_A1
            b.fill(x, F - 4, z, x, F + 6, z, FRAME if r < RING_IN and not cap else PANEL)
            if not inside:
                if r >= RING_OUT and not cap:
                    # 円周に沿った二重の導体と、進む向きに沿って繰り返す短い発光帯。
                    b.fill(x, F + 1, z, x, F + 5, z, GRAPHITE)
                    b.set(x, F + 2, z, COPPER)
                    b.set(x, F + 5, z, PRISTINE_LIGHT if int(t) % 16 < 3 else PATINA)
                continue
            b.fill(x, F + 1, z, x, F + 5, z, AIR)
            b.set(x, F + 6, z, PANEL)
            k = min(RING_SEGMENTS - 1, int((t - RING_A0) // seg))
            rel = t - RING_A0 - k * seg
            a = math.radians(t)
            up = (-math.sin(a), math.cos(a))                     # 進む向き（t が増える向き）
            if rel >= seg - RING_STATION or t >= RING_A1 - RING_STATION:
                b.set(x, F, z, PANEL)                              # 流れない踊り場
                continue
            pit = next((p for p in RING_PITS if p <= rel < p + RING_PIT_WIDTH), None)
            if pit is not None:
                b.fill(x, F - 3, z, x, F, z, AIR)
                b.set(x, F - 4, z, PANEL)
                if rel < pit + 2.0:
                    ladder(b, x, F - 3, F, z, _facing(*up))          # 入口側の壁のはしご（落ちたら戻る）
                continue
            b.set(x, F, z, DRAG_FLOOR, {'facing': _facing(-up[0], -up[1]), 'active': 'true', 'segment': str(k)})
            if (round(t) % 10 == 0) and abs(r - 24.5) < 0.6:
                b.set(x, F + 6, z, LAMP)
    # 踊り場: 制動器（外壁ぞい）と、区画1・3の警備機ドック（内壁ぞい）。最後の踊り場に中継器
    for k in range(RING_SEGMENTS):
        t = RING_A0 + (k + 1) * seg - RING_STATION / 2
        bx, bz = _ring_point(t, RING_OUT - 0.7)
        b.set(bx, F + 1, bz, BRAKE, {'engaged': 'false', 'segment': str(k)})
        if k in (0, 2):
            dx, dz = _ring_point(t, RING_IN + 0.4)
            b.dock(dx, F + 1, dz, 3)
    rx, rz = _ring_point(RING_A1 - RING_STATION / 2 - 0.5, RING_IN + 0.6)          # 制動器より内側（窓ぎわ）
    relay(b, rx, F + 1, rz, 'plain')


def _reactor_lock(b, F):
    """
    炉室前室。炉室の隔壁の前の気密室で、隔壁の左右に炉室をのぞく観測窓。西の扉から外周点検回廊へ（入口の壁に回廊の制動の
    注意書き）。隔壁の前は立入禁止の縞、壁に圧力計と隔壁の操作盤、南の制御室とは窓でつながる。
    """
    b.fill(SR_CX - 2, F, 54, SR_CX + 2, F, 56, STRIPED)
    b.fill(SR_CX - 1, F, 54, SR_CX + 1, F, 55, FRAME)
    instrument_bank(b, 36, F, 56, 'west', ('mimic_panel', 'dial_panel', 'dial_panel'))
    b.set(25, F + 3, 60, *EMERGENCY_LIGHT)
    b.set(37, F + 3, 60, *EMERGENCY_LIGHT)
    for x in (27, 31, 35):
        b.set(x, F + 7, 58, *FACILITY_LIGHT)
    room_sign(b, SR_CX, F + 5, 54, 'south', 'reactor_lock', '↑')
    # 回廊の入口: 回廊の始まりの端の壁を抜き、床を張る。回廊を示す札
    opening(b, 21, F + 1, 54, 24, F + 4, 58)
    for x in range(21, 25):
        for z in range(54, 59):
            if b.get(x, F, z) != DRAG_FLOOR:
                b.set(x, F, z, PANEL)
    b.fill(21, F, 58, 24, F, 58, STRIPED)
    room_sign(b, 26, F + 5, 57, 'east', 'inspection_ring')


def _unit2_control(b, F):
    """
    第二炉中央制御室。北の窓から炉室前室と隔壁が見える。窓の左右の北の壁に系統の計器盤、前後2列の運転卓、南の当直長の壇
    （記録端末）、東西の壁に冷却系統と抽出塔の計器。西は潜水準備室、東は抽出塔の投入準備室、南は中央通路。
    """
    cx = SR_CX
    for xa, xb in ((27, 29), (33, 35)):
        b.fill(xa, F + 2, 62, xb, F + 4, 62, REINFORCED_GLASS)
    instrument_bank(b, 19, F, 63, 'south', ('dial_panel', 'mimic_panel', 'mimic_panel', 'mimic_panel', 'dial_panel', 'annunciator_panel', 'dial_panel'))
    instrument_bank(b, 37, F, 63, 'south', ('dial_panel', 'annunciator_panel', 'dial_panel', 'mimic_panel', 'mimic_panel', 'dial_panel', 'dial_panel'))
    for x in range(27, 36):
        if b.get(x, F + 1, 63) == AIR and not cx - 1 <= x <= cx + 1:
            b.set(x, F + 1, 63, CABINET)
            if x % 2:
                b.set(x, F + 2, 63, f'{MODID}:desk_monitor', {'facing': 'south'})
    operating_desk(b, 21, F, 66, 7)
    operating_desk(b, 35, F, 66, 7)
    operating_desk(b, 23, F, 70, 5)
    operating_desk(b, 35, F, 70, 5)
    instrument_bank(b, 19, F, 73, 'east', ('dial_panel', 'mimic_panel', 'dial_panel', 'dial_panel'))
    instrument_bank(b, 43, F, 73, 'west', ('dial_panel', 'dial_panel', 'mimic_panel', 'dial_panel'))
    # 当直長の卓（南。中央は中央通路への道）
    operating_desk(b, 25, F, 75, 5)
    operating_desk(b, 33, F, 75, 5)
    b.set(24, F + 1, 75, CABINET)
    terminal(b, 24, F + 2, 75, 'south', 14)
    b.set(38, F + 1, 75, CABINET)
    b.set(38, F + 2, 75, f'{MODID}:paper_stack', {'facing': 'south'})
    for x in (*range(19, 24), *range(39, 44)):
        b.fill(x, F + 1, 77, x, F + 2, 77, SERVER_RACK, {'facing': 'north', 'powered': 'false'})
    for x in (29, 33):
        b.fill(x, F, 63, x, F, 72, PATINA)
    for x in (22, 28, 34, 40):
        for z in (65, 70, 75):
            b.fill(x, F + 8, z, x + 2, F + 8, z, *FACILITY_LIGHT)
    room_sign(b, cx, F + 5, 63, 'south', 'reactor_control')


def _diving_room(b, F):
    """
    潜水準備室（冷却系統の点検口）。床の立坑から水没した保守トンネルへ潜る。立坑の上に巻上機の梁、北の壁に潜水用の空気ボンベの
    架、西の壁に潜水員のロッカー、南の壁に冷却系統の計器盤。
    """
    # 立坑のまわりの手すり（東の切れ目から降りる）
    for x in range(11, 16):
        for z in range(69, 74):
            if (x in (11, 15) or z in (69, 73)) and (x, z) != (15, 71):
                b.set(x, F + 1, z, *PANEL_WALL_POST)
    b.fill(11, F + 5, 71, 15, F + 5, 71, FRAME)
    b.fill(13, F + 3, 71, 13, F + 4, 71, *CHAIN_Y)
    for x in (4, 6, 8, 10, 12):
        b.fill(x, F + 1, 65, x, F + 2, 65, COPPER)
        b.set(x, F + 3, 65, 'minecraft:lightning_rod', {'facing': 'up', 'powered': 'false', 'waterlogged': 'false'})
    b.fill(3, F + 4, 65, 17, F + 4, 65, PATINA)
    for z in range(67, 77, 2):
        b.fill(3, F + 1, z, 3, F + 3, z, VENT)
        b.set(4, F + 2, z, 'minecraft:stone_button', {'face': 'wall', 'facing': 'east'})
    b.fill(6, F + 1, 70, 6, F + 1, 73, PANEL_SLAB)
    instrument_bank(b, 8, F, 77, 'north', ('dial_panel', 'mimic_panel', 'dial_panel', 'dial_panel'))
    b.set(18, F + 3, 66, *EMERGENCY_LIGHT)
    for x in (5, 9, 13):
        b.fill(x, F + 6, 67, x, F + 6, 68, *FACILITY_LIGHT)
    room_sign(b, 4, F + 2, 66, 'east', 'diving_air')
    room_sign(b, 17, F + 5, 70, 'west', 'cooling')


def _flooded_wing(b, F):
    """
    水没した冷却系統（西）。潜水準備室の床の立坑から水へ潜り、冷却管に沿った保守トンネルを南へ泳ぐ。天井の空気だまりで
    息を継ぎ、西へ折れた先の半ば水没したポンプ室の底に、排水弁の中継器がある（起動すると系統の水が抜ける）。
    """
    W = 4                                                       # トンネルの水面の高さ（y1〜4 が水）
    b.box(11, 0, 69, 15, F, 73, PANEL)
    b.fill(12, 1, 70, 14, F - 2, 72, WATER)
    b.fill(12, F - 1, 70, 14, F, 72, AIR)
    ladder(b, 12, 1, F, 71, 'east', water_to=F - 2)
    # 南へ伸びる水没したトンネル（空気だまり2か所）。両側の壁を冷却管が通り、水中誘導灯が並ぶ
    b.box(11, 0, 73, 15, W + 1, 100, PANEL)
    for x in (11, 15):
        b.fill(x, 1, 74, x, W, 99, PATINA)
        for z in range(76, 97, 5):
            b.fill(x, 1, z, x, W, z, COPPER)
            b.set(x, 2, z, 'minecraft:sea_lantern')
    b.fill(12, 1, 73, 14, W, 99, WATER)
    for z in range(76, 99, 6):
        b.set(13, W + 1, z, LAMP)
    for pz in (83, 92):
        b.box(11, W, pz - 1, 15, W + 3, pz + 1, PANEL)
        b.fill(12, 1, pz - 1, 14, W, pz + 1, WATER)
        b.fill(12, W + 1, pz - 1, 14, W + 2, pz + 1, AIR)
        b.set(13, W + 3, pz, LAMP)
    # 西へ折れるトンネル（空気だまり1か所）
    b.box(2, 0, 96, 15, W + 1, 100, PANEL)
    b.fill(3, 1, 97, 14, W, 99, WATER)
    b.fill(12, 1, 96, 14, W, 99, WATER)                         # 南のトンネルとのつなぎ目
    b.box(6, W, 96, 9, W + 3, 100, PANEL)
    b.fill(7, 1, 97, 8, W, 99, WATER)
    b.fill(7, W + 1, 97, 8, W + 2, 99, AIR)
    # 半ば水没したポンプ室（上の方に空気が残っている）
    b.box(1, 0, 100, 13, 7, 111, PANEL)
    wall_band(b, 1, 100, 13, 111, 1, 6, PATINA)
    wall_band(b, 1, 100, 13, 111, W, W, COPPER)
    b.fill(2, 1, 101, 12, W, 110, WATER)
    b.fill(2, W + 1, 101, 12, 6, 110, AIR)
    b.fill(3, 1, 100, 6, W, 100, WATER)
    for px, pz in ((4, 103), (10, 103), (4, 108), (10, 108)):
        b.fill(px, 1, pz, px, 6, pz, FRAME)
        b.set(px, 5, pz, PRISTINE_LIGHT)
    b.set(7, 7, 105, LAMP)
    # 吸い込み口の櫛状の格子と、ポンプの上を結ぶ二本の集合管。
    for x in range(3, 12):
        b.set(x, 2, 111, FRAME if x % 2 else VENT)
    for z in (103, 108):
        b.fill(4, 6, z, 10, 6, z, COPPER)
        b.set(7, 6, z, 'minecraft:sea_lantern')
    relay(b, 7, 1, 106, 'drain', waterlogged=True)          # 水没したポンプ室の底
    terminal(b, 2, 1, 108, 'east', 15)


def _extraction_tower(b, F):
    """
    抽出塔（東）。ペンローズ過程で炉からエネルギーを取り出した塔で、円い塔の床の真ん中に渦の穴があり、底に抽出の中継器がある。
    穴へ落とした物は渦に呑まれ、重さに応じたエネルギーが返る（ブロックは重い）。印刷台のアンドロイドを突き落としてもよい。
    落ちたら北側のはしごで上がる。西の投入準備室で投げ込む物を量り、南の搬送路からは修理場の破片が届いた。
    """
    cx, cz, R = 57, 70, 8
    top = F + 16
    for x in range(cx - R - 1, cx + R + 2):
        for z in range(cz - R - 1, cz + R + 2):
            d = math.hypot(x - cx, z - cz)
            if d > R + 0.5:
                continue
            if d > R - 0.5:
                # 円筒の内張りを巡る三段のコイル。導体の切れ目から紫色の絶縁体がのぞく。
                b.fill(x, F, z, x, top + 1, z, GRAPHITE)
                for y in (F + 3, F + 8, F + 13):
                    b.set(x, y, z, COPPER)
                    b.set(x, y + 1, z, 'minecraft:pearlescent_froglight' if x == cx or z == cz else 'minecraft:purpur_pillar')
                continue
            b.fill(x, 1, z, x, F - 1, z, DARK)
            b.set(x, F, z, STRIPED if 2.6 < d <= 3.6 else PANEL)
            b.fill(x, F + 1, z, x, top, z, AIR)
            b.set(x, top + 1, z, PANEL)
            if d <= 2.6:
                b.fill(x, 2, z, x, F, z, AIR)                   # 渦の穴
                b.set(x, 1, z, PANEL)
    opening(b, 48, F + 1, 69, cx - R + 1, F + 4, 71)            # 西の出入口（投入準備室から）
    opening(b, 55, F + 1, cz + R - 1, 57, F + 4, cz + R + 1)    # 南の出入口（破片の搬送路から）
    relay(b, cx, 2, cz, 'extract')
    ladder(b, cx, 2, F, cz - 2, 'south')
    for k in range(4):
        a = math.pi / 4 + k * math.pi / 2
        px, pz = round(cx + math.cos(a) * 6), round(cz + math.sin(a) * 6)
        b.fill(px, F + 1, pz, px, top, pz, PRISTINE_PILLAR)
        b.set(px, F + 8, pz, PRISTINE_LIGHT)
        for y in (F + 3, F + 8, F + 13):
            # 接点が壁のコイルと同じ高さで光る。中心の渦は上からも見通せる。
            b.set(px, y, pz, 'minecraft:pearlescent_froglight')
            b.set(px, y + 1, pz, PHASE_GLASS)
    for k in range(16):
        a = math.pi * 2 * k / 16
        b.set(round(cx + math.cos(a) * R), F + 9, round(cz + math.sin(a) * R), LAMP)
    b.fabricator(cx + 3, F + 1, cz - R + 1, radius=3)
    # 投入準備室: 秤の台と、投げ込む物を載せた台車
    b.fill(45, F, 67, 46, F, 68, FRAME)
    b.set(45, F + 1, 67, f'{MODID}:dial_panel', {'facing': 'south', 'channel': '0'})
    b.fill(46, F + 1, 72, 47, F + 1, 73, FRAME)
    b.set(46, F + 2, 73, 'minecraft:cobbled_deepslate')
    b.fill(45, F + 6, 70, 48, F + 6, 70, *FACILITY_LIGHT)
    room_sign(b, 45, F + 5, 68, 'east', 'weighbridge')
    room_sign(b, 47, F + 4, 73, 'north', 'extraction', '→')


def _elevator_hall(b, F):
    """
    昇降機ホール（入口）。地上から職員を下ろした昇降機の到着階で、籠は立坑の底に落ちて崩れ、立坑の上の端は瓦礫でふさがって
    いる（はしごが立坑の東の壁に残る）。守衛の受付、掲示板、待合の長椅子。西の中央通路から制御室へ。
    """
    # 立坑（東の端。ホールの天井を抜けて上へ）
    b.box(46, F + 1, 80, 50, 35, 84, PANEL)
    for y in range(F + 9, 33, 4):
        wall_band(b, 46, 80, 50, 84, y, y, COPPER)
        b.set(46, y + 1, 82, PRISTINE_LIGHT)
    b.fill(47, F + 1, 81, 49, 34, 83, AIR)
    b.fill(46, F + 1, 81, 46, F + 4, 83, AIR)                   # 籠の出入口
    for z in (80, 84):
        b.fill(46, F + 1, z, 46, F + 5, z, FRAME)
    b.fill(46, F + 5, 81, 46, F + 5, 83, FRAME)
    b.fill(47, F, 81, 49, F, 83, DARK)
    ladder(b, 49, F + 1, 34, 82, 'west')
    for y in range(31, 35):
        for x in range(47, 49):
            for z in range(81, 84):
                b.set(x, y, z, b.rng.choice((CRACKED, 'minecraft:cobbled_deepslate', 'minecraft:gravel', PANEL)))
    b.set(48, F + 1, 82, FRAME)                                 # 落ちてきた籠の残骸
    b.set(47, F + 1, 83, PANEL_SLAB, {'type': 'bottom', 'waterlogged': 'false'})
    b.set(47, F + 1, 81, f'{MODID}:cracked_ruin_panel_slab', {'type': 'bottom', 'waterlogged': 'false'})
    b.fill(45, F, 80, 45, F, 84, STRIPED)
    # 守衛の受付（南）と掲示板（北の壁）、待合の長椅子
    operating_desk(b, 38, F, 84, 4)
    b.set(37, F + 1, 84, CABINET)
    b.set(37, F + 2, 84, f'{MODID}:desk_monitor', {'facing': 'south'})
    b.fill(37, F + 2, 78, 42, F + 3, 78, LINED)
    for x in range(37, 43):
        b.set(x, F + 1, 79, PANEL_SLAB, {'type': 'bottom', 'waterlogged': 'false'})
    b.set(43, F + 1, 79, f'{MODID}:trash_bin', {'facing': 'south'})
    for x in (37, 42):
        b.fill(x, F + 7, 81, x + 1, F + 7, 83, *FACILITY_LIGHT)
    b.set(52, F + 3, 85, *EMERGENCY_LIGHT)
    room_sign(b, 45, F + 5, 82, 'west', 'elevator')


def _workshop(b, F):
    """
    炉殻の修理場（南）。剥がれた炉殻の板を、壁ぎわの溶接架台で挟んで直し、天井の梁の吊り具で運んだ。直せない破片は東の
    搬送路で抽出塔へ送って投げ込んだ。中央の中継器に挑戦者が近づくと、暴走した炉殻修理機が起動する（倒すと中継器を起動
    できる）。南東の隅の工具庫に封印コンテナ（特異点錠）。
    """
    cx, cz = SR_CX, 98
    wall_band(b, 16, 86, 46, 111, F + 1, F + 10, GRAPHITE)
    wall_band(b, 16, 86, 46, 111, F + 3, F + 3, COPPER)
    opening(b, SR_CX - 1, F + 1, 86, SR_CX + 1, F + 5, 86)
    opening(b, 46, F + 1, 90, 46, F + 4, 92)
    # 炉殻を挟む溶接架台。骨組みが手前へ張り出し、奥の作業灯を格子越しに見せる
    for wall, inner in ((16, 17), (46, 45)):
        for z in (96, 104):
            b.fill(wall, F + 4, z - 1, wall, F + 6, z + 1, 'minecraft:ochre_froglight')
            b.fill(inner, F + 4, z - 1, inner, F + 6, z + 1, GRILLE)
            for end in (z - 2, z + 2):
                b.fill(inner, F + 1, end, inner, F + 8, end, COPPER)
    for x in (22, 36):
        b.fill(x, F + 1, 110, x + 2, F + 1, 110, FRAME)
        b.set(x, F + 2, 110, 'minecraft:grindstone', {'face': 'floor', 'facing': 'north'})
        b.set(x + 2, F + 2, 110, 'minecraft:polished_basalt', {'axis': 'x'})
    for x in range(17, 46):
        for z in range(87, 111):
            d = math.hypot(x - cx, z - cz)
            if d <= 2.4:
                b.set(x, F, z, FRAME)
            elif 5.0 <= d <= 5.7:
                b.set(x, F, z, STRIPED)
    relay(b, cx, F + 1, cz, 'guarded', 'hull_repairer')
    for z in range(89, 110, 5):
        b.fill(17, F + 11, z, 45, F + 11, z, FRAME)
        for x in (24, 38):
            b.fill(x, F + 8, z, x, F + 10, z, *CHAIN_Y)
        b.set(cx, F + 12, z, LAMP)
    # 修理を待つ炉殻の破片（反った暗い板）を載せた架台
    for px, pz in ((22, 91), (40, 91), (22, 105), (40, 105)):
        b.fill(px - 2, F, pz - 1, px + 2, F, pz + 1, STRIPED)
        for i in range(5):
            for j in range(3):
                y = F + 1 + j + (1 if i in (0, 4) else 0)
                b.set(px + i - 2, y, pz, b.rng.choice((DARK, DARK, FRAME, 'minecraft:crying_obsidian')))
    # 破片の搬送口（東）: 止まったコンベアが抽出塔の方へ続く
    for x in range(42, 57):
        conveyor(b, x, F + 1, 92, 'east')
    for z in range(79, 93):
        conveyor(b, 57, F + 1, z, 'north')
    for x in range(48, 58, 4):
        b.set(x, F + 6, 91, LAMP)
    for z in range(80, 92, 4):
        b.set(56, F + 6, z, LAMP)
    # 工具庫（南東の隅）: 鉄格子の仕切りと棚、封印コンテナ
    bars(b, 40, 106, 45, 106, F + 1, F + 3, skip={(44, 106)})
    bars(b, 40, 107, 40, 110, F + 1, F + 3)
    for z in (108, 109, 110):
        b.fill(45, F + 1, z, 45, F + 3, z, FRAME)
        b.set(45, F + 2, z, PANEL_SLAB, {'type': 'top', 'waterlogged': 'false'})
    b.sealed(43, F + 1, 109, 4)
    room_sign(b, cx, F + 6, 87, 'south', 'inspection')


def conveyor(b, x, y, z, facing):
    """止まっている工場のコンベア（facing の向きへ流れる）。"""
    b.set(x, y, z, CONVEYOR, {'facing': facing, 'running': 'false', 'warn': 'false'})


# 遺構ID → (作る関数, 置き方)。surface は地上、それ以外は地下に埋める（gen_data.RUIN_PLACEMENT の高さ）
RUINS = {
    'observation_post': (observation_post, 'surface'),
    'research_building': (research_building, 'underground'),
    'culture_facility': (culture_facility, 'underground'),
    'final_lab': (final_lab, 'underground'),
    'sunken_reactor': (sunken_reactor, 'underground'),
}

# 設計書の遺構名 → 遺構ID
RUIN_IDS = {
    '地表観測拠点': 'observation_post',
    '研究棟': 'research_building',
    '封鎖培養施設': 'culture_facility',
    '最終実験施設': 'final_lab',
    '沈んだ炉心区画': 'sunken_reactor',
}


# 遺構ID → (朽ちた変わり種の割合, 朽ちていない白い建材の割合)。深い遺構ほど、きれいなまま残った所が多い
VARIETY = {
    'observation_post': (0.12, 0.0),
    'research_building': (0.12, 0.04),
    'culture_facility': (0.12, 0.10),
    'final_lab': (0.08, 0.30),
    'sunken_reactor': (0.10, 0.20),
}


EXPANDED_TEMPLATES = {'final_lab', 'sunken_reactor'}


def template_name(rid):
    # Do not overwrite template IDs already stored in Jigsaw pieces in existing worlds.
    return f'{rid}_antechamber' if rid in EXPANDED_TEMPLATES else rid


def write_all(structure_dir):
    out = {}
    for rid, (make, _) in RUINS.items():
        b = make()
        weathered, pristine = VARIETY[rid]
        b.vary(seed=len(rid) * 7919, weathered=weathered, pristine=pristine)
        nbt.write_gzip(structure_dir / f'{template_name(rid)}.nbt', b.to_nbt())
        out[rid] = b
    return out
