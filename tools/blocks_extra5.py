# -*- coding: utf-8 -*-
"""第5弾のブロックの絵・モデル・落とす物（gen_data.py から呼ぶ）。

- 番人の封印核: ボス部屋の床に埋まった黒い板。天面に光る八角の封印紋と、中心の目。側面は暗い装甲に光る継ぎ目
- 残響投影器: 白い台座・暗い柱・投影の頭と、頭の上の光るレンズ（番人へ伸びる光は EchoProjectorRenderer が描く）
"""
import math

from blocks_extra import MODID, S, mix, new, rgba

GLOW = (143, 234, 255)
DARK = (28, 32, 40)
STEEL = (58, 66, 78)


# ---------------------------------------------------------------- 絵

def core_top():
    """封印核の天面: 黒い板に、光る八角の紋と中心の目。"""
    im, d = new(rgba(DARK))
    d.rectangle([0, 0, S - 1, S - 1], outline=rgba(STEEL))
    c = 7.5
    for r, col in ((6.2, GLOW), (3.6, mix(GLOW, DARK, 0.35))):
        pts = [(c + r * math.cos(math.pi / 8 + k * math.pi / 4), c + r * math.sin(math.pi / 8 + k * math.pi / 4)) for k in range(8)]
        d.polygon(pts, outline=rgba(col))
    d.rectangle([6, 6, 9, 9], fill=rgba(mix(GLOW, (255, 255, 255), 0.5)))
    d.point([(7, 7), (8, 8)], fill=(255, 255, 255, 255))
    # 八方へ伸びる細い溝
    for x, y in ((7, 1), (8, 1), (7, 14), (8, 14), (1, 7), (1, 8), (14, 7), (14, 8)):
        d.point((x, y), fill=rgba(mix(GLOW, DARK, 0.5)))
    return im


def core_side():
    """封印核の側面: 暗い装甲に、上の縁と縦の継ぎ目が光る。"""
    im, d = new(rgba(STEEL))
    d.rectangle([0, 0, S - 1, 1], fill=rgba(DARK))
    d.line([(0, 2), (S - 1, 2)], fill=rgba(GLOW))
    for x in (3, 12):
        d.line([(x, 4), (x, 13)], fill=rgba(mix(GLOW, STEEL, 0.45)))
    d.rectangle([6, 6, 9, 11], outline=rgba(DARK))
    d.line([(0, S - 1), (S - 1, S - 1)], fill=rgba(DARK))
    return im


def core_bottom():
    im, d = new(rgba(DARK))
    d.rectangle([0, 0, S - 1, S - 1], outline=rgba(STEEL))
    return im


def projector_base():
    """投影器の台座: 白い外装と、細い継ぎ目。"""
    im, d = new((228, 232, 236, 255))
    d.rectangle([0, 0, S - 1, S - 1], outline=(160, 168, 178, 255))
    d.line([(0, 8), (S - 1, 8)], fill=(196, 202, 210, 255))
    d.point([(2, 2), (13, 2), (2, 13), (13, 13)], fill=(120, 130, 142, 255))
    return im


def projector_column():
    """投影器の柱: 暗い金属に、光の通る縦の筋。"""
    im, d = new(rgba(STEEL))
    d.line([(7, 0), (7, S - 1)], fill=rgba(GLOW))
    d.line([(8, 0), (8, S - 1)], fill=rgba(mix(GLOW, STEEL, 0.5)))
    for y in (3, 9):
        d.line([(0, y), (S - 1, y)], fill=rgba(DARK))
    return im


def projector_head():
    """投影器の頭: 白い輪に、光る溝。"""
    im, d = new((236, 240, 244, 255))
    d.rectangle([0, 0, S - 1, S - 1], outline=(150, 160, 172, 255))
    d.ellipse([3, 3, 12, 12], outline=rgba(GLOW))
    d.ellipse([6, 6, 9, 9], fill=rgba(mix(GLOW, DARK, 0.4)))
    return im


def projector_lens():
    """投影器のレンズ: 透きとおった水色の結晶。"""
    im, d = new(rgba(mix(GLOW, (255, 255, 255), 0.25)))
    d.rectangle([0, 0, S - 1, S - 1], outline=rgba(mix(GLOW, DARK, 0.3)))
    d.line([(2, 2), (6, 2)], fill=(255, 255, 255, 255))
    d.line([(2, 2), (2, 6)], fill=(255, 255, 255, 255))
    return im


def textures(tb, save):
    save(core_top(), tb / 'guardian_core_top.png')
    save(core_side(), tb / 'guardian_core_side.png')
    save(core_bottom(), tb / 'guardian_core_bottom.png')
    save(projector_base(), tb / 'echo_projector_base.png')
    save(projector_column(), tb / 'echo_projector_column.png')
    save(projector_head(), tb / 'echo_projector_head.png')
    save(projector_lens(), tb / 'echo_projector_lens.png')


# ---------------------------------------------------------------- モデル

def _faces(texture, skip=(), cull=None):
    out = {}
    for d in ('north', 'south', 'east', 'west', 'up', 'down'):
        if d in skip:
            continue
        f = {'texture': texture[d] if isinstance(texture, dict) else texture}
        if cull and d in cull:
            f['cullface'] = d
        out[d] = f
    return out


def models(write_json, assets):
    mb = assets / 'models' / 'block'
    mi = assets / 'models' / 'item'
    bs = assets / 'blockstates'
    glow = {'block_light': 15, 'sky_light': 15}
    sides = ('north', 'south', 'east', 'west')
    # 番人の封印核: 胴と、光る天面の1ドットの蓋
    write_json(mb / 'guardian_core.json', {
        'parent': 'minecraft:block/block',
        'textures': {'top': f'{MODID}:block/guardian_core_top', 'side': f'{MODID}:block/guardian_core_side',
                     'bottom': f'{MODID}:block/guardian_core_bottom', 'particle': f'{MODID}:block/guardian_core_side'},
        'elements': [
            {'from': [0, 0, 0], 'to': [16, 15, 16], 'faces': {
                **{d: {'texture': '#side', 'uv': [0, 1, 16, 16], 'cullface': d} for d in sides},
                'down': {'texture': '#bottom', 'cullface': 'down'}}},
            {'from': [0, 15, 0], 'to': [16, 16, 16], 'neoforge_data': glow, 'faces': {
                **{d: {'texture': '#side', 'uv': [0, 0, 16, 1], 'cullface': d} for d in sides},
                'up': {'texture': '#top', 'cullface': 'up'}}},
        ]})
    write_json(bs / 'guardian_core.json', {'variants': {'': {'model': f'{MODID}:block/guardian_core'}}})
    write_json(mi / 'guardian_core.json', {'parent': f'{MODID}:block/guardian_core'})
    # 残響投影器: 台座・柱・頭・レンズ（レンズは光る）
    write_json(mb / 'echo_projector.json', {
        'parent': 'minecraft:block/block',
        'textures': {'base': f'{MODID}:block/echo_projector_base', 'column': f'{MODID}:block/echo_projector_column',
                     'head': f'{MODID}:block/echo_projector_head', 'lens': f'{MODID}:block/echo_projector_lens',
                     'particle': f'{MODID}:block/echo_projector_base'},
        'elements': [
            {'from': [3, 0, 3], 'to': [13, 3, 13], 'faces': _faces('#base', cull=('down',))},
            {'from': [5, 3, 5], 'to': [11, 10, 11], 'faces': _faces('#column', ('up', 'down'))},
            {'from': [4, 10, 4], 'to': [12, 12, 12], 'faces': _faces('#head')},
            {'from': [6, 12, 6], 'to': [10, 13, 10], 'neoforge_data': glow, 'faces': _faces('#lens', ('down',))},
        ]})
    write_json(bs / 'echo_projector.json', {'variants': {'': {'model': f'{MODID}:block/echo_projector'}}})
    write_json(mi / 'echo_projector.json', {'parent': f'{MODID}:block/echo_projector'})


# ---------------------------------------------------------------- 落とす物

def loot(write_json, data):
    # 残響投影器は何も落とさない（番人を起こすたびに封印核が直す）
    write_json(data / 'loot_table' / 'blocks' / 'echo_projector.json', {'type': 'minecraft:block', 'pools': []})
