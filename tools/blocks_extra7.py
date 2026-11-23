# -*- coding: utf-8 -*-
"""第7弾のブロックの絵・モデル（gen_data.py から呼ぶ）。最終実験施設と沈んだ炉心区画の前室の仕掛け。どれも壊せず、何も落とさない。

- 遺構の中継器: 白い台座・暗い柱・光る結晶のレンズ。止まっている間はレンズが暗い青、起動すると明るい水色に光る
- 異常場の発生器: 床に埋める暗い板。天面の紋で場の種類が分かる（浮く=水色の上向きの矢、重い=菫の下向きの矢、
  遅い=琥珀の砂時計、速い=緑の二重の矢）。天面は光る
- 回転流の床: 暗い鋼の床に、流れる向き（facing）を指す光る山形の矢。止まると矢が消えて灰色になる
- 制動器: 黄と黒の縞の天面の箱。側面のレバーが、上がっている（赤）／下ろした（緑）
"""
import zlib

from blocks_extra import MODID, S, grime, mix, new, rgba

WHITE = (226, 230, 234)
STEEL = (64, 72, 84)
DARK = (28, 32, 40)
EDGE = (150, 158, 170)
FIELDS = {
    'lift': (120, 230, 255),
    'heavy': (190, 120, 255),
    'slow': (255, 196, 90),
    'fast': (130, 255, 150),
}


# ---------------------------------------------------------------- 絵

def relay_base():
    im, d = new(rgba(WHITE))
    d.rectangle([0, 0, S - 1, S - 1], outline=rgba(EDGE))
    d.line([(0, 9), (S - 1, 9)], fill=rgba(mix(WHITE, EDGE, 0.5)))
    d.point([(2, 2), (13, 2), (2, 13), (13, 13)], fill=rgba(STEEL))
    return im


def relay_column():
    im, d = new(rgba(STEEL))
    d.rectangle([0, 0, S - 1, S - 1], outline=rgba(DARK))
    for y in (3, 8, 13):
        d.line([(2, y), (13, y)], fill=rgba(DARK))
    return im


def relay_lens(on):
    glow = (150, 240, 255) if on else (40, 70, 110)
    im, d = new(rgba(glow))
    d.rectangle([0, 0, S - 1, S - 1], outline=rgba(mix(glow, DARK, 0.4)))
    d.rectangle([5, 5, 10, 10], fill=rgba(mix(glow, (255, 255, 255), 0.6 if on else 0.15)))
    return im


def emitter_side():
    im, d = new(rgba(STEEL))
    d.rectangle([0, 0, S - 1, S - 1], outline=rgba(DARK))
    d.line([(0, 2), (S - 1, 2)], fill=rgba(DARK))
    d.point([(3, 8), (12, 8)], fill=rgba(EDGE))
    return im


def emitter_bottom():
    im, d = new(rgba(DARK))
    d.rectangle([0, 0, S - 1, S - 1], outline=rgba(STEEL))
    return im


def emitter_top(field):
    """天面: 暗い板に光る縁と、場の紋。"""
    c = FIELDS[field]
    im, d = new(rgba(DARK))
    d.rectangle([0, 0, S - 1, S - 1], outline=rgba(mix(c, DARK, 0.3)))
    d.rectangle([1, 1, S - 2, S - 2], outline=rgba(mix(c, DARK, 0.7)))
    if field in ('lift', 'heavy'):
        up = field == 'lift'
        for k, y0 in enumerate((3, 8)):
            for i in range(4):
                y = y0 + (i if up else 3 - i)
                d.point([(7 - i, y), (8 + i, y)], fill=rgba(c))
    elif field == 'slow':
        d.line([(4, 3), (11, 3)], fill=rgba(c))
        d.line([(4, 12), (11, 12)], fill=rgba(c))
        d.line([(5, 4), (10, 11)], fill=rgba(c))
        d.line([(10, 4), (5, 11)], fill=rgba(c))
        d.rectangle([6, 10, 9, 11], fill=rgba(mix(c, (255, 255, 255), 0.4)))
    else:
        for x0 in (3, 8):
            for i in range(4):
                d.point([(x0 + i, 4 + i), (x0 + i, 11 - i)], fill=rgba(c))
    return im


def drag_top(on):
    """回転流の床の天面: 上（north）を指す山形の矢が2つ。止まっていると灰色。"""
    c = (110, 220, 255) if on else (96, 102, 112)
    base = (40, 46, 56) if on else (58, 62, 70)
    im, d = new(rgba(base))
    d.rectangle([0, 0, S - 1, S - 1], outline=rgba(mix(base, (0, 0, 0), 0.4)))
    for x in (0, S - 1):
        d.line([(x, 0), (x, S - 1)], fill=rgba(STEEL))
    for y0 in (2, 9):
        for i in range(5):
            d.point([(7 - i, y0 + i), (8 + i, y0 + i)], fill=rgba(c if i < 4 else mix(c, base, 0.5)))
    return im


def drag_side():
    im, d = new(rgba(STEEL))
    d.rectangle([0, 0, S - 1, S - 1], outline=rgba(DARK))
    d.line([(0, 1), (S - 1, 1)], fill=rgba(EDGE))
    return im


def brake_top():
    im, d = new(rgba(DARK))
    for x in range(S):
        for y in range(S):
            if ((x + y) // 3) % 2 == 0:
                d.point((x, y), fill=rgba((230, 190, 60)))
    d.rectangle([0, 0, S - 1, S - 1], outline=rgba(STEEL))
    d.rectangle([4, 4, 11, 11], fill=rgba(STEEL), outline=rgba(DARK))
    return im


def brake_side(engaged):
    """側面: 縦の溝とレバー。上がっている（赤）か、下ろした（緑）か。"""
    im, d = new(rgba(STEEL))
    d.rectangle([0, 0, S - 1, S - 1], outline=rgba(DARK))
    d.rectangle([6, 2, 9, 13], fill=rgba(DARK))
    knob = (90, 230, 120) if engaged else (230, 70, 60)
    y = 10 if engaged else 3
    d.rectangle([4, y, 11, y + 2], fill=rgba(knob), outline=rgba(mix(knob, DARK, 0.5)))
    d.line([(2, 14), (13, 14)], fill=rgba((230, 190, 60)))
    return im


def textures(tb, save_plain):
    def save(im, path):
        grime(im, zlib.crc32(path.name.encode()))
        save_plain(im, path)

    save(relay_base(), tb / 'ruin_relay_base.png')
    save(relay_column(), tb / 'ruin_relay_column.png')
    save_plain(relay_lens(False), tb / 'ruin_relay_lens.png')
    save_plain(relay_lens(True), tb / 'ruin_relay_lens_active.png')
    save(emitter_side(), tb / 'anomaly_emitter_side.png')
    save(emitter_bottom(), tb / 'anomaly_emitter_bottom.png')
    for f in FIELDS:
        save_plain(emitter_top(f), tb / f'anomaly_emitter_{f}.png')
    save_plain(drag_top(True), tb / 'frame_drag_floor_active.png')
    save(drag_top(False), tb / 'frame_drag_floor.png')
    save(drag_side(), tb / 'frame_drag_floor_side.png')
    save(brake_top(), tb / 'drag_brake_top.png')
    save(brake_side(False), tb / 'drag_brake_side.png')
    save(brake_side(True), tb / 'drag_brake_side_engaged.png')


# ---------------------------------------------------------------- モデル

def _faces(texture, skip=(), cull=()):
    out = {}
    for d in ('north', 'south', 'east', 'west', 'up', 'down'):
        if d in skip:
            continue
        f = {'texture': texture}
        if d in cull:
            f['cullface'] = d
        out[d] = f
    return out


def models(write_json, assets):
    mb = assets / 'models' / 'block'
    mi = assets / 'models' / 'item'
    bs = assets / 'blockstates'
    glow = {'block_light': 15, 'sky_light': 15}
    # 遺構の中継器: 台座・柱・笠・レンズ（起動するとレンズが光る）
    for suffix, lens in (('', 'ruin_relay_lens'), ('_active', 'ruin_relay_lens_active')):
        write_json(mb / f'ruin_relay{suffix}.json', {
            'parent': 'minecraft:block/block',
            'textures': {'base': f'{MODID}:block/ruin_relay_base', 'column': f'{MODID}:block/ruin_relay_column',
                         'lens': f'{MODID}:block/{lens}', 'particle': f'{MODID}:block/ruin_relay_base'},
            'elements': [
                {'from': [1, 0, 1], 'to': [15, 3, 15], 'faces': _faces('#base', cull=('down',))},
                {'from': [4, 3, 4], 'to': [12, 10, 12], 'faces': _faces('#column', ('up', 'down'))},
                {'from': [2, 10, 2], 'to': [14, 12, 14], 'faces': _faces('#base')},
                {'from': [5, 12, 5], 'to': [11, 14, 11], **({'neoforge_data': glow} if suffix else {}),
                 'faces': _faces('#lens', ('down',))},
            ]})
    write_json(bs / 'ruin_relay.json', {'variants': {
        'active=false': {'model': f'{MODID}:block/ruin_relay'},
        'active=true': {'model': f'{MODID}:block/ruin_relay_active'}}})
    write_json(mi / 'ruin_relay.json', {'parent': f'{MODID}:block/ruin_relay_active'})
    # 異常場の発生器: 床の板（天面が光る）
    sides = ('north', 'south', 'east', 'west')
    for f in FIELDS:
        write_json(mb / f'anomaly_emitter_{f}.json', {
            'parent': 'minecraft:block/block',
            'textures': {'top': f'{MODID}:block/anomaly_emitter_{f}', 'side': f'{MODID}:block/anomaly_emitter_side',
                         'bottom': f'{MODID}:block/anomaly_emitter_bottom', 'particle': f'{MODID}:block/anomaly_emitter_side'},
            'elements': [
                {'from': [0, 0, 0], 'to': [16, 16, 16], 'faces': {
                    **{d: {'texture': '#side', 'cullface': d} for d in sides},
                    'down': {'texture': '#bottom', 'cullface': 'down'}}},
                {'from': [0, 16, 0], 'to': [16, 16, 16], 'neoforge_data': glow, 'faces': {
                    'up': {'texture': '#top', 'cullface': 'up'}}},
            ]})
    write_json(bs / 'anomaly_emitter.json', {'variants': {
        f'field={f}': {'model': f'{MODID}:block/anomaly_emitter_{f}'} for f in FIELDS}})
    write_json(mi / 'anomaly_emitter.json', {'parent': f'{MODID}:block/anomaly_emitter_lift'})
    # 回転流の床: 天面の矢が facing を指す（動いている間は光る）
    for suffix, top in (('', 'frame_drag_floor'), ('_active', 'frame_drag_floor_active')):
        el = [{'from': [0, 0, 0], 'to': [16, 16, 16], 'faces': {
            **{d: {'texture': '#side', 'cullface': d} for d in sides},
            'down': {'texture': '#side', 'cullface': 'down'},
            'up': {'texture': '#top', 'cullface': 'up'}}}]
        if suffix:
            el = [{'from': [0, 0, 0], 'to': [16, 16, 16], 'faces': {
                **{d: {'texture': '#side', 'cullface': d} for d in sides},
                'down': {'texture': '#side', 'cullface': 'down'}}},
                {'from': [0, 16, 0], 'to': [16, 16, 16], 'neoforge_data': glow, 'faces': {
                    'up': {'texture': '#top', 'cullface': 'up'}}}]
        write_json(mb / f'frame_drag_floor{suffix}.json', {
            'parent': 'minecraft:block/block',
            'textures': {'top': f'{MODID}:block/{top}', 'side': f'{MODID}:block/frame_drag_floor_side',
                         'particle': f'{MODID}:block/frame_drag_floor_side'},
            'elements': el})
    rot = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
    variants = {}
    for facing, y in rot.items():
        for active in ('false', 'true'):
            m = {'model': f'{MODID}:block/frame_drag_floor' + ('_active' if active == 'true' else '')}
            if y:
                m['y'] = y
            variants[f'active={active},facing={facing}'] = m
    write_json(bs / 'frame_drag_floor.json', {'variants': variants})
    write_json(mi / 'frame_drag_floor.json', {'parent': f'{MODID}:block/frame_drag_floor_active'})
    # 制動器
    for suffix, side in (('', 'drag_brake_side'), ('_engaged', 'drag_brake_side_engaged')):
        write_json(mb / f'drag_brake{suffix}.json', {
            'parent': 'minecraft:block/cube_bottom_top',
            'textures': {'top': f'{MODID}:block/drag_brake_top', 'side': f'{MODID}:block/{side}',
                         'bottom': f'{MODID}:block/anomaly_emitter_bottom'}})
    write_json(bs / 'drag_brake.json', {'variants': {
        'engaged=false': {'model': f'{MODID}:block/drag_brake'},
        'engaged=true': {'model': f'{MODID}:block/drag_brake_engaged'}}})
    write_json(mi / 'drag_brake.json', {'parent': f'{MODID}:block/drag_brake'})
