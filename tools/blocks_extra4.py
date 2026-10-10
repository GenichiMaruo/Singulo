# -*- coding: utf-8 -*-
"""第4弾のブロックの絵・モデル・落とす物（gen_data.py から呼ぶ）。

- 隕石クレーターの自然物: 隕石の溶融殻（焦げた殻に、空気で削られた指の跡のようなくぼみと、溶けて流れた筋）、
  隕鉄塊（黒い殻の割れ目から、鉄とニッケルの結晶の筋が見える銀色の金属）、星屑の結晶（紫水晶の芽のような光る結晶）
- 雷ガラス塊（砂に、雷で溶けて枝分かれしたガラスの管が走る）
- ケラウノス放電塔の立体（台座・芯・3段の銅のコイル・放電冠）。放電球と雷は KeraunosTowerRenderer が描く
"""
import math
import random

from PIL import Image, ImageDraw

from blocks_extra import MODID, S, mix, new, rgba


# ---------------------------------------------------------------- 絵

def meteorite_crust():
    rnd = random.Random(91)
    base = (38, 34, 36)
    im, d = new(rgba(base))
    px = im.load()
    for y in range(S):
        for x in range(S):
            n = rnd.uniform(-0.06, 0.06)
            px[x, y] = rgba(mix(base, (255, 255, 255), n) if n > 0 else mix(base, (0, 0, 0), -n))
    # 溶けて流れた筋（斜めに）
    for k in range(4):
        y0 = 2 + k * 4 + rnd.randint(-1, 1)
        d.line([(0, y0), (5, y0 + 1), (10, y0), (15, y0 + 2)], fill=(74, 62, 58, 255))
    # 空気で削られたくぼみ（指の跡）
    for _ in range(5):
        cx, cy = rnd.randint(2, 13), rnd.randint(2, 13)
        d.ellipse([cx - 2, cy - 1, cx + 1, cy + 1], fill=(22, 20, 22, 255))
        d.point((cx - 1, cy - 1), fill=(88, 76, 70, 255))
    # ガラス質の光る粒
    for _ in range(3):
        d.point((rnd.randint(0, 15), rnd.randint(0, 15)), fill=(150, 170, 200, 255))
    return im


def meteoric_iron_chunk():
    rnd = random.Random(97)
    im = meteorite_crust()
    d = ImageDraw.Draw(im)
    metal = (172, 176, 186)
    # 割れ目からのぞく金属の面
    for cx, cy, r in ((5, 6, 3), (11, 10, 3), (10, 3, 2)):
        d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=rgba(metal))
        # ウィドマンシュテッテン構造（交差する細い筋）
        for k in range(-r, r + 1, 2):
            d.line([(cx - r, cy + k), (cx + r, cy + k - r)], fill=rgba(mix(metal, (0, 0, 0), 0.18)))
        d.point((cx - 1, cy - 1), fill=(240, 244, 250, 255))
        d.arc([cx - r, cy - r, cx + r, cy + r], 30, 150, fill=(110, 112, 120, 255))
    for _ in range(4):
        d.point((rnd.randint(1, 14), rnd.randint(1, 14)), fill=(210, 214, 222, 255))
    return im


def stardust_cluster():
    """星屑の結晶（交差する2枚の板に描く絵。背景は透明）: 細い結晶が束になって伸び、先が光る。"""
    im, d = new()
    spikes = [(4, 15, 3, 6), (7, 15, 7, 2), (10, 15, 12, 5), (6, 15, 5, 9), (9, 15, 10, 8)]
    for x0, y0, x1, y1 in spikes:
        d.line([(x0, y0), (x1, y1)], fill=(120, 110, 200, 255), width=2)
        d.line([(x0, y0), (x1, y1)], fill=(160, 210, 255, 255))
        d.point((x1, y1), fill=(255, 255, 255, 255))
    for x, y in ((6, 4), (11, 3), (3, 8)):
        d.point((x, y), fill=(220, 190, 255, 255))
    return im


def fulgurite_block():
    rnd = random.Random(73)
    sand = (219, 207, 163)
    im, d = new(rgba(sand))
    px = im.load()
    for y in range(S):
        for x in range(S):
            n = rnd.uniform(-0.06, 0.05)
            px[x, y] = rgba(mix(sand, (255, 255, 255), n) if n > 0 else mix(sand, (0, 0, 0), -n))
    scorch = (150, 126, 90)
    glass = (240, 232, 205)
    # 雷の通り道（枝分かれ）。まわりは焦げ、芯は溶けて光るガラス
    paths = [[(7, 0), (8, 4), (6, 8), (8, 12), (7, 15)], [(6, 8), (2, 10), (0, 13)], [(8, 4), (12, 6), (15, 5)]]
    for path in paths:
        d.line(path, fill=rgba(scorch), width=3)
    for path in paths:
        d.line(path, fill=rgba(glass))
    d.point((8, 4), fill=(255, 255, 255, 255))
    d.point((6, 8), fill=(255, 255, 255, 255))
    return im


def keraunos_coil(on):
    """放電塔のコイル: 銅線をきつく巻いた帯。働いている間は青白く光る。"""
    copper = (200, 120, 70) if not on else (150, 220, 255)
    im, d = new(rgba(mix(copper, (0, 0, 0), 0.3)))
    for y in range(0, S, 2):
        d.line([(0, y), (S - 1, y)], fill=rgba(copper))
    d.line([(0, 1), (S - 1, 1)], fill=rgba(mix(copper, (255, 255, 255), 0.35)))
    return im


def keraunos_crown(on):
    """放電冠: 白い台に、放電の通る青い溝。"""
    im, d = new((236, 240, 244, 255))
    d.rectangle([0, 0, S - 1, S - 1], outline=(150, 160, 172, 255))
    glow = (150, 220, 255) if on else (90, 120, 140)
    d.ellipse([3, 3, 12, 12], outline=rgba(glow))
    d.ellipse([6, 6, 9, 9], fill=rgba(glow))
    return im


def textures(tb, save):
    save(meteorite_crust(), tb / 'meteorite_crust.png')
    save(meteoric_iron_chunk(), tb / 'meteoric_iron_chunk.png')
    save(stardust_cluster(), tb / 'stardust_cluster.png')
    save(fulgurite_block(), tb / 'fulgurite_block.png')
    for on in (False, True):
        suffix = '_on' if on else ''
        save(keraunos_coil(on), tb / f'keraunos_tower_coil{suffix}.png')
        save(keraunos_crown(on), tb / f'keraunos_tower_crown{suffix}.png')


# ---------------------------------------------------------------- モデル

def _faces(textures, skip=()):
    return {d: {'texture': textures[d] if isinstance(textures, dict) else textures}
            for d in ('north', 'south', 'east', 'west', 'up', 'down') if d not in skip}


def keraunos_elements(on):
    glow = {'block_light': 15, 'sky_light': 15}
    els = [
        # 台座（白い装置の外装）
        {'from': [1, 0, 1], 'to': [15, 3, 15], 'faces': {**_faces('#side', ('up', 'down')), 'up': {'texture': '#top'},
                                                        'down': {'texture': '#bottom', 'cullface': 'down'}}},
        # 芯（暗い金属）
        {'from': [6, 3, 6], 'to': [10, 13, 10], 'faces': _faces('#inner', ('up', 'down'))},
    ]
    # 3段のコイル
    for y0 in (4, 7, 10):
        e = {'from': [4, y0, 4], 'to': [12, y0 + 2, 12], 'faces': _faces('#coil')}
        if on:
            e['neoforge_data'] = glow
        els.append(e)
    # 放電冠と、四隅の放電針
    crown = {'from': [3, 13, 3], 'to': [13, 14, 13], 'faces': _faces('#crown')}
    if on:
        crown['neoforge_data'] = glow
    els.append(crown)
    for x0 in (3, 11):
        for z0 in (3, 11):
            els.append({'from': [x0, 14, z0], 'to': [x0 + 2, 16, z0 + 2], 'faces': _faces('#inner', ('down',))})
    return els


def models(write_json, assets):
    mb = assets / 'models' / 'block'
    mi = assets / 'models' / 'item'
    bs = assets / 'blockstates'
    for iid in ('meteorite_crust', 'meteoric_iron_chunk', 'fulgurite_block'):
        write_json(mb / f'{iid}.json', {'parent': 'minecraft:block/cube_all', 'textures': {'all': f'{MODID}:block/{iid}'}})
        write_json(mi / f'{iid}.json', {'parent': f'{MODID}:block/{iid}'})
    write_json(bs / 'meteorite_crust.json', {'variants': {
        'natural=false': {'model': f'{MODID}:block/meteorite_crust'},
        'natural=true': {'model': f'{MODID}:block/meteorite_crust'}}})
    for iid in ('meteoric_iron_chunk', 'fulgurite_block'):
        write_json(bs / f'{iid}.json', {'variants': {'': {'model': f'{MODID}:block/{iid}'}}})
    # 星屑の結晶（紫水晶の芽と同じく、向きで回す）
    write_json(mb / 'stardust_cluster.json', {'parent': 'minecraft:block/cross', 'render_type': 'minecraft:cutout',
                                              'textures': {'cross': f'{MODID}:block/stardust_cluster'}})
    model = f'{MODID}:block/stardust_cluster'
    write_json(bs / 'stardust_cluster.json', {'variants': {
        'facing=down': {'model': model, 'x': 180}, 'facing=up': {'model': model},
        'facing=north': {'model': model, 'x': 90}, 'facing=south': {'model': model, 'x': 90, 'y': 180},
        'facing=east': {'model': model, 'x': 90, 'y': 90}, 'facing=west': {'model': model, 'x': 90, 'y': 270}}})
    write_json(mi / 'stardust_cluster.json', {'parent': 'minecraft:item/generated',
                                              'textures': {'layer0': f'{MODID}:block/stardust_cluster'}})
    # ケラウノス放電塔（装置のモデルを、塔の立体で置き換える。向きの回転は装置と同じ状態定義のまま）
    for suffix, on in (('', False), ('_on', True)):
        write_json(mb / f'keraunos_tower{suffix}.json', {
            'parent': 'minecraft:block/block',
            'textures': {'side': f'{MODID}:block/machine_side_t3', 'top': f'{MODID}:block/machine_top_t3',
                         'bottom': f'{MODID}:block/machine_bottom_t3', 'inner': f'{MODID}:block/machine_inner',
                         'coil': f'{MODID}:block/keraunos_tower_coil{suffix}', 'crown': f'{MODID}:block/keraunos_tower_crown{suffix}',
                         'particle': f'{MODID}:block/machine_side_t3'},
            'elements': keraunos_elements(on)})


# ---------------------------------------------------------------- 落とす物

def _silk():
    return {'condition': 'minecraft:match_tool', 'predicate': {'predicates': {
        'minecraft:enchantments': [{'enchantments': 'minecraft:silk_touch', 'levels': {'min': 1}}]}}}


def _ore_like(block, item, lo, hi):
    """シルクタッチならブロックそのもの、ほかは item を lo〜hi 個（幸運で増える）。"""
    return {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:alternatives', 'children': [
        {'type': 'minecraft:item', 'name': f'{MODID}:{block}', 'conditions': [_silk()]},
        {'type': 'minecraft:item', 'name': f'{MODID}:{item}', 'functions': [
            {'function': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': lo, 'max': hi}},
            {'function': 'minecraft:apply_bonus', 'enchantment': 'minecraft:fortune', 'formula': 'minecraft:ore_drops'},
            {'function': 'minecraft:explosion_decay'}]}]}]}]}


def loot(write_json, data):
    lt = data / 'loot_table' / 'blocks'
    write_json(lt / 'meteoric_iron_chunk.json', _ore_like('meteoric_iron_chunk', 'raw_meteoric_iron', 2, 3))
    write_json(lt / 'stardust_cluster.json', _ore_like('stardust_cluster', 'stardust', 2, 4))
    write_json(lt / 'fulgurite_block.json', _ore_like('fulgurite_block', 'fulgurite', 2, 3))
