# -*- coding: utf-8 -*-
"""第6弾のブロックの絵・モデル・落とす物（gen_data.py から呼ぶ）。廃アンドロイド工場のボス部屋の仕掛け。

- 製造ライン: 壁際の印刷ベイ。暗い台座に橙の警告の縞、後ろの柱に画面、前へ張り出した印刷ヘッドの下が光る
  （ファウンドリへ伸びる光は FactoryLineRenderer が描く）。前（north）を部屋の中へ向けて置く
- 工場のコンベア: 鋼の枠にゴムのベルト。止まっているとき・動いているとき（ベルトが流れる）・向きが変わる前の警告（枠の赤い灯）
"""
import json
import zlib

from blocks_extra import MODID, S, grime, mix, new, rgba

STEEL = (88, 96, 110)
DARK = (34, 38, 46)
ORANGE = (240, 140, 60)
GLOW = (255, 176, 96)
BELT = (46, 48, 54)
BELT_HI = (74, 78, 86)
BELT_FRAMES = 16


# ---------------------------------------------------------------- 絵

def line_base():
    """製造ラインの台座: 暗い鋼に、橙と黒の警告の縞の帯。"""
    im, d = new(rgba(DARK))
    d.rectangle([0, 0, S - 1, S - 1], outline=rgba(STEEL))
    for x in range(S):
        c = ORANGE if (x // 2) % 2 == 0 else (20, 20, 24)
        d.point((x, 6), fill=rgba(c))
        d.point((x, 7), fill=rgba(c))
    d.point([(2, 2), (13, 2), (2, 13), (13, 13)], fill=rgba(mix(STEEL, (255, 255, 255), 0.3)))
    return im


def line_side():
    """製造ラインの柱: 鋼の板に、暗い画面と緑の進み具合の帯。"""
    im, d = new(rgba(STEEL))
    d.rectangle([0, 0, S - 1, S - 1], outline=rgba(DARK))
    d.rectangle([3, 3, 12, 9], fill=rgba(DARK))
    d.line([(4, 7), (9, 7)], fill=rgba((110, 220, 150)))
    d.point([(4, 5), (6, 5), (8, 5)], fill=rgba((140, 200, 230)))
    for y in (12, 14):
        d.line([(2, y), (13, y)], fill=rgba(mix(STEEL, DARK, 0.5)))
    return im


def line_head():
    """製造ラインの印刷ヘッド: 白い外装と、縁の橙の灯。"""
    im, d = new((214, 220, 228, 255))
    d.rectangle([0, 0, S - 1, S - 1], outline=rgba(STEEL))
    d.line([(1, 1), (14, 1)], fill=rgba(ORANGE))
    d.rectangle([5, 5, 10, 10], outline=(150, 158, 170, 255))
    return im


def line_lens():
    """印刷ヘッドの下の光: 橙に光る格子。"""
    im, d = new(rgba(GLOW))
    for k in range(0, S, 4):
        d.line([(k, 0), (k, S - 1)], fill=rgba(mix(GLOW, (255, 255, 255), 0.5)))
    return im


def conveyor_side(warn=False):
    """コンベアの枠: 鋼に、ローラーの丸い軸が並ぶ。上の縁は灯（警告のときは赤く光る）。"""
    im, d = new(rgba(STEEL))
    d.rectangle([0, 0, S - 1, 2], fill=rgba(DARK))
    d.line([(0, 1), (S - 1, 1)], fill=rgba((255, 70, 60) if warn else (120, 128, 140)))
    for x in (2, 7, 12):
        d.ellipse([x, 6, x + 2, 8], fill=rgba(DARK))
        d.point((x + 1, 7), fill=rgba(mix(STEEL, (255, 255, 255), 0.4)))
    d.line([(0, S - 1), (S - 1, S - 1)], fill=rgba(DARK))
    return im


def conveyor_top_frame(k):
    """ベルト（1コマ）: 黒いゴムに、横の筋が等間隔。k コマ目は筋が k ドット上（north へ）ずれる。両端は鋼の枠。"""
    im, d = new(rgba(BELT))
    for y in range(S):
        if (y + k) % 4 == 0:
            d.line([(2, y), (13, y)], fill=rgba(BELT_HI))
    for x in (0, 1, 14, 15):
        d.line([(x, 0), (x, S - 1)], fill=rgba(STEEL if x in (0, 15) else DARK))
    return im


def conveyor_bottom():
    im, d = new(rgba(DARK))
    d.rectangle([0, 0, S - 1, S - 1], outline=rgba(STEEL))
    return im


def textures(tb, save_plain):
    from PIL import Image

    def seed(name):
        return zlib.crc32(name.encode())

    def save(im, path):
        """どの絵にも使用感の汚しを入れる（アニメーションの絵は、コマごとに同じ汚しを入れてから並べる）。"""
        if im.size[1] == im.size[0]:
            grime(im, seed(path.name))
        save_plain(im, path)

    save(line_base(), tb / 'factory_line_base.png')
    save(line_side(), tb / 'factory_line_side.png')
    save(line_head(), tb / 'factory_line_head.png')
    save(line_lens(), tb / 'factory_line_lens.png')
    save(conveyor_side(), tb / 'factory_conveyor_side.png')
    save(conveyor_side(warn=True), tb / 'factory_conveyor_side_warn.png')
    save(conveyor_top_frame(0), tb / 'factory_conveyor_top.png')
    save(conveyor_bottom(), tb / 'factory_conveyor_bottom.png')
    # 動いているベルト: 筋が north へ流れる（1コマに1ドット）
    sheet = Image.new('RGBA', (S, S * BELT_FRAMES))
    for k in range(BELT_FRAMES):
        sheet.paste(grime(conveyor_top_frame(k), seed('factory_conveyor_top_moving.png')), (0, S * k))
    save(sheet, tb / 'factory_conveyor_top_moving.png')
    (tb / 'factory_conveyor_top_moving.png.mcmeta').write_text(json.dumps({'animation': {'frametime': 1}}), encoding='utf-8')


# ---------------------------------------------------------------- モデル

def _faces(texture, skip=(), cull=None):
    out = {}
    for d in ('north', 'south', 'east', 'west', 'up', 'down'):
        if d in skip:
            continue
        f = {'texture': texture}
        if cull and d in cull:
            f['cullface'] = d
        out[d] = f
    return out


FACING_Y = {'north': 0, 'east': 90, 'south': 180, 'west': 270}


def models(write_json, assets):
    mb = assets / 'models' / 'block'
    mi = assets / 'models' / 'item'
    bs = assets / 'blockstates'
    glow = {'block_light': 15, 'sky_light': 15}
    # 製造ライン: 台座・後ろの柱・前へ張り出した印刷ヘッド・ヘッドの下の光（前は north）
    write_json(mb / 'factory_line.json', {
        'parent': 'minecraft:block/block',
        'textures': {'base': f'{MODID}:block/factory_line_base', 'side': f'{MODID}:block/factory_line_side',
                     'head': f'{MODID}:block/factory_line_head', 'lens': f'{MODID}:block/factory_line_lens',
                     'particle': f'{MODID}:block/factory_line_base'},
        'elements': [
            {'from': [0, 0, 0], 'to': [16, 3, 16], 'faces': _faces('#base', cull=('down', 'north', 'south', 'east', 'west'))},
            {'from': [2, 3, 11], 'to': [14, 14, 16], 'faces': _faces('#side', ('down',), cull=('south',))},
            {'from': [3, 11, 3], 'to': [13, 14, 11], 'faces': _faces('#head', ('south',))},
            {'from': [5, 10.5, 4], 'to': [11, 11, 10], 'neoforge_data': glow, 'faces': _faces('#lens', ('up', 'south'))},
        ]})
    write_json(bs / 'factory_line.json', {'variants': {
        f'facing={f}': ({'model': f'{MODID}:block/factory_line', 'y': y} if y else {'model': f'{MODID}:block/factory_line'})
        for f, y in FACING_Y.items()}})
    write_json(mi / 'factory_line.json', {'parent': f'{MODID}:block/factory_line'})
    # 工場のコンベア: 止まっている・動いている・警告（動いていて、枠の灯が赤い）。ベルトは north へ流れる
    for name, top, side in (('factory_conveyor', 'factory_conveyor_top', 'factory_conveyor_side'),
                            ('factory_conveyor_running', 'factory_conveyor_top_moving', 'factory_conveyor_side'),
                            ('factory_conveyor_warn', 'factory_conveyor_top_moving', 'factory_conveyor_side_warn')):
        write_json(mb / f'{name}.json', {
            'parent': 'minecraft:block/cube',
            'textures': {'up': f'{MODID}:block/{top}', 'down': f'{MODID}:block/factory_conveyor_bottom',
                         'north': f'{MODID}:block/{side}', 'south': f'{MODID}:block/{side}',
                         'east': f'{MODID}:block/{side}', 'west': f'{MODID}:block/{side}',
                         'particle': f'{MODID}:block/factory_conveyor_side'}})
    variants = {}
    for f, y in FACING_Y.items():
        for running in ('false', 'true'):
            for warn in ('false', 'true'):
                model = 'factory_conveyor' if running == 'false' else ('factory_conveyor_warn' if warn == 'true' else 'factory_conveyor_running')
                v = {'model': f'{MODID}:block/{model}'}
                if y:
                    v['y'] = y
                variants[f'facing={f},running={running},warn={warn}'] = v
    write_json(bs / 'factory_conveyor.json', {'variants': variants})
    write_json(mi / 'factory_conveyor.json', {'parent': f'{MODID}:block/factory_conveyor'})


# ---------------------------------------------------------------- 落とす物

def loot(write_json, data):
    # 製造ラインは何も落とさない（番人を起こすたびに封印核が直す）。コンベアはそのまま拾える
    write_json(data / 'loot_table' / 'blocks' / 'factory_line.json', {'type': 'minecraft:block', 'pools': []})
    write_json(data / 'loot_table' / 'blocks' / 'factory_conveyor.json', {'type': 'minecraft:block', 'pools': [{
        'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': f'{MODID}:factory_conveyor'}],
        'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
