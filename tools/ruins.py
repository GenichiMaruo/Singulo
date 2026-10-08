# -*- coding: utf-8 -*-
"""旧文明の遺構4種の構造物（.nbt）を作る。gen_data.py から呼ぶ。

どれも白い無機質パネルの建物が黄ばみ・ひび割れ・植物に侵食された姿にする（設計書「コンセプトと世界観」）。
崩れ方は乱数の種を固定しているので、毎回同じ形になる。
"""
import random

import nbt

DATA_VERSION = 3955  # 1.21.1
MODID = 'singulo'

PANEL = f'{MODID}:ruin_panel'
CRACKED = f'{MODID}:cracked_ruin_panel'
MOSSY = f'{MODID}:mossy_ruin_panel'
GLASS = f'{MODID}:ruin_glass'
LAMP = f'{MODID}:ruin_lamp'
CACHE = f'{MODID}:ruin_cache'
DOCK = f'{MODID}:ruin_guard_dock'
CONSOLE = f'{MODID}:seal_console'
AIR = 'minecraft:air'


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

    def scatter(self, name, count, y, area, props=None, on_air=True):
        """床の上に count 個ばらまく（苔のじゅうたんなど）。"""
        x0, z0, x1, z1 = area
        for _ in range(count):
            x, z = self.rng.randint(x0, x1), self.rng.randint(z0, z1)
            if not on_air or self.get(x, y, z) in (None, AIR):
                self.set(x, y, z, name, props)

    def cache(self, x, y, z, ruin, sealed=False):
        self.set(x, y, z, CACHE, {'sealed': 'true' if sealed else 'false'}, {'ruin': ruin})

    def dock(self, x, y, z, tier):
        self.set(x, y, z, DOCK, None, {'tier': tier})

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


def vines_on(b, x, y0, y1, z, side):
    """壁の外側に垂れるツタ。side はツタが張り付く壁の方向。"""
    for y in range(y0, y1 + 1):
        if b.rng.random() < 0.6:
            b.set(x, y, z, 'minecraft:vine', {side: 'true'})


# ---------------------------------------------------------------- 4種類の遺構

def observation_post():
    """地表観測拠点: 平原や砂漠に立つ小さな観測小屋。屋根に観測用のアンテナ。"""
    b = Builder(9, 8, 9, seed=101)
    b.fill(0, 0, 0, 8, 0, 8, PANEL)                       # 床
    b.box(0, 0, 0, 8, 4, 8, PANEL)                        # 壁と屋根
    b.fill(1, 1, 1, 7, 3, 7, AIR)
    for x in (2, 6):                                      # 窓
        b.set(x, 2, 0, GLASS)
        b.set(x, 2, 8, GLASS)
    b.set(0, 2, 4, GLASS)
    b.fill(4, 1, 8, 4, 2, 8, AIR)                         # 南の入口
    b.set(4, 4, 4, LAMP)
    b.set(1, 3, 1, LAMP)
    # 屋根のアンテナ
    b.set(4, 5, 4, PANEL)
    b.set(4, 6, 4, 'minecraft:end_rod', {'facing': 'up'})
    b.set(3, 5, 4, 'minecraft:end_rod', {'facing': 'west'})
    b.cache(2, 1, 2, 'observation_post')
    b.dock(6, 1, 6, 1)
    b.decay(cracked=0.2, mossy=0.15, missing=0.0)
    for (x, y, z), blk in list(b.blocks.items()):         # 屋根の穴
        if y == 4 and blk[0] in (PANEL, CRACKED, MOSSY) and b.rng.random() < 0.3 and (x, z) != (4, 4):
            b.blocks[(x, y, z)] = (AIR, {}, None)
    b.scatter('minecraft:moss_carpet', 8, 1, (1, 1, 7, 7))
    vines_on(b, 1, 1, 3, 4, 'west')                       # 室内の西の壁に垂れるツタ
    return b


def research_building():
    """研究棟: 2階建ての研究施設。入口は鉄の扉とボタンの電子ロック。"""
    b = Builder(13, 10, 11, seed=202)
    b.box(0, 0, 0, 12, 9, 10, PANEL)
    b.fill(1, 5, 1, 11, 5, 9, PANEL)                      # 2階の床
    for x in range(2, 11, 3):                             # 窓
        b.fill(x, 2, 0, x, 3, 0, GLASS)
        b.fill(x, 7, 0, x, 8, 0, GLASS)
        b.fill(x, 2, 10, x, 3, 10, GLASS)
    # 入口（南）: 電子ロックの鉄の扉。ボタンは内側にしかないので、外からはレッドストーンで開けるか壁を壊す
    b.set(6, 1, 10, 'minecraft:iron_door', {'facing': 'north', 'half': 'lower', 'hinge': 'left', 'open': 'false', 'powered': 'false'})
    b.set(6, 2, 10, 'minecraft:iron_door', {'facing': 'north', 'half': 'upper', 'hinge': 'left', 'open': 'false', 'powered': 'false'})
    b.set(7, 2, 9, 'minecraft:stone_button', {'face': 'wall', 'facing': 'north', 'powered': 'false'})
    # 2階へのはしごと穴
    b.fill(1, 1, 1, 1, 5, 1, 'minecraft:ladder', {'facing': 'east', 'waterlogged': 'false'})
    b.set(1, 5, 1, 'minecraft:ladder', {'facing': 'east', 'waterlogged': 'false'})
    for x, y, z in ((3, 4, 3), (9, 4, 7), (6, 8, 5), (3, 8, 7)):
        b.set(x, y, z, LAMP)
    b.cache(10, 6, 8, 'research_building')
    b.dock(3, 1, 5, 2)
    b.dock(9, 6, 3, 2)
    b.decay(cracked=0.2, mossy=0.1)
    for (x, y, z), blk in list(b.blocks.items()):         # 屋根の崩れ
        if y == 9 and blk[0] in (PANEL, CRACKED, MOSSY) and b.rng.random() < 0.2:
            b.blocks[(x, y, z)] = (AIR, {}, None)
    b.scatter('minecraft:cobweb', 6, 4, (1, 1, 11, 9))
    b.scatter('minecraft:moss_carpet', 6, 1, (2, 2, 11, 9))
    return b


def culture_facility():
    """封鎖培養施設: 深層岩の層に埋まった密閉施設。奥に極低温区画。"""
    b = Builder(15, 9, 15, seed=303)
    b.box(0, 0, 0, 14, 8, 14, PANEL)
    # 十字の通路で4部屋に区切る
    b.fill(1, 1, 7, 13, 7, 7, PANEL)
    b.fill(7, 1, 1, 7, 7, 13, PANEL)
    for x, z in ((7, 3), (7, 11), (3, 7), (11, 7)):      # 部屋の間の出入口
        b.fill(x, 1, z, x, 2, z, AIR)
    # 北東の部屋は極低温区画
    b.fill(8, 1, 1, 13, 1, 6, 'minecraft:packed_ice')
    b.fill(8, 1, 1, 13, 1, 1, 'minecraft:blue_ice')
    b.fill(9, 2, 2, 12, 2, 5, AIR)
    for x, z in ((9, 2), (12, 5), (10, 4)):
        b.set(x, 2, z, 'minecraft:powder_snow')
    b.cache(11, 2, 3, 'culture_facility')
    b.dock(3, 1, 3, 3)
    b.dock(11, 1, 11, 3)
    for x, y, z in ((3, 7, 3), (11, 7, 11), (3, 7, 11), (11, 7, 3)):
        b.set(x, y, z, LAMP)
    b.decay(cracked=0.15, mossy=0.05)
    b.scatter('minecraft:cobweb', 8, 6, (1, 1, 13, 13))
    return b


def final_lab():
    """最終実験施設: 重力異常点の中心に建つ大ホール。中央の封印コンソールに触れると守護機ホライズン・ウォーデンが起動する。"""
    b = Builder(21, 13, 21, seed=404)
    b.fill(0, 0, 0, 20, 0, 20, PANEL)                     # 床
    # 円形の壁（半径10）とドーム
    for x in range(21):
        for z in range(21):
            d = ((x - 10) ** 2 + (z - 10) ** 2) ** 0.5
            for y in range(1, 13):
                dome = (d ** 2 + ((y - 1) * 0.9) ** 2) ** 0.5
                if 9.3 <= dome <= 10.3:
                    b.set(x, y, z, GLASS if (y % 3 == 0 and y < 8) else PANEL)
                elif dome < 9.3:
                    b.set(x, y, z, AIR)
    b.fill(10, 1, 19, 10, 3, 20, AIR)                     # 南の入口
    b.fill(9, 1, 20, 11, 3, 20, AIR)
    # 中央の封印コンソール
    b.fill(8, 1, 8, 12, 1, 12, PANEL)
    b.set(10, 2, 10, CONSOLE)
    b.set(10, 3, 10, LAMP)
    b.set(10, 4, 10, 'minecraft:end_rod', {'facing': 'up'})
    b.cache(10, 2, 12, 'final_lab', sealed=True)               # 守護機を倒すまで力場で封鎖
    b.decay(cracked=0.25, mossy=0.15, only_above=1)
    for (x, y, z), blk in list(b.blocks.items()):         # ドームの崩れ
        if y >= 8 and blk[0] in (PANEL, CRACKED, MOSSY) and b.rng.random() < 0.25:
            b.blocks[(x, y, z)] = (AIR, {}, None)
    b.scatter('minecraft:moss_carpet', 20, 1, (2, 2, 18, 18))
    return b


# 遺構ID → (作る関数, 置き方)
RUINS = {
    'observation_post': (observation_post, 'surface'),
    'research_building': (research_building, 'surface'),
    'culture_facility': (culture_facility, 'deep'),
    'final_lab': (final_lab, 'surface'),
}

# 設計書の遺構名 → 遺構ID
RUIN_IDS = {
    '地表観測拠点': 'observation_post',
    '研究棟': 'research_building',
    '封鎖培養施設': 'culture_facility',
    '最終実験施設': 'final_lab',
}


def write_all(structure_dir):
    out = {}
    for rid, (make, _) in RUINS.items():
        b = make()
        nbt.write_gzip(structure_dir / f'{rid}.nbt', b.to_nbt())
        out[rid] = b
    return out
