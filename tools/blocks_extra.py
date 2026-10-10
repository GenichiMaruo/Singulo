# -*- coding: utf-8 -*-
"""あとから足したブロックの絵とモデル（gen_data.py から呼ぶ）。

- 遺構パネルの変わり種（朽ちたもの: タイル・通気・帯・焦げ）と、深い遺構に残る朽ちていない白い建材（pristine_*）
- 自分で作れる建材（ホワイトパネル・発光ホワイトパネル・黒色強化パネル）。遺構の建材と見分けられるよう、
  面取りの縁と四隅の留め具のある、新品の工業製品らしい絵にする
- 重力パネル（低重力・高重力）と受電器。パネルは隣の同じパネルとつながる見た目にする:
  地の絵は縁のない（並べると模様が続く）絵、つながっていない辺にだけ縁取りの絵を重ねる（切り抜きで描く）
"""
import random

from PIL import Image, ImageDraw

MODID = 'singulo'
S = 16

# ---------------------------------------------------------------- 色

AGED, AGED_SEAM = (226, 218, 190), (180, 170, 140)
PURE, PURE_SEAM, PURE_SHADE, PURE_ACCENT = (246, 248, 250), (210, 216, 224), (230, 234, 240), (150, 206, 236)

# 重力パネル: (地, 縁の外側, 縁の内側, 矢印（止まっている）, 矢印（働いている）, 矢印の芯（働いている）)
GRAVITY = {
    'low_gravity_panel': dict(base=(224, 232, 240), outer=(132, 148, 168), inner=(252, 253, 255), off=(176, 196, 214),
                              on=(80, 200, 255), core=(214, 248, 255), up=True),
    'high_gravity_panel': dict(base=(42, 38, 54), outer=(14, 12, 20), inner=(100, 90, 126), off=(84, 74, 104),
                               on=(255, 96, 196), core=(255, 214, 244), up=False),
}

# 面ごとに、隣の向き → その面の絵のどの辺に当たるか（マインクラフトの面の UV の向きに合わせる）
EDGE_OF = {
    'up': {'north': 'top', 'south': 'bottom', 'west': 'left', 'east': 'right'},
    'down': {'south': 'top', 'north': 'bottom', 'west': 'left', 'east': 'right'},
    'north': {'up': 'top', 'down': 'bottom', 'east': 'left', 'west': 'right'},
    'south': {'up': 'top', 'down': 'bottom', 'west': 'left', 'east': 'right'},
    'west': {'up': 'top', 'down': 'bottom', 'north': 'left', 'south': 'right'},
    'east': {'up': 'top', 'down': 'bottom', 'south': 'left', 'north': 'right'},
}
DIRS = ('north', 'south', 'east', 'west', 'up', 'down')


def rgba(c, a=255):
    return tuple(int(v) for v in c[:3]) + (a,)


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def new(fill=(0, 0, 0, 0)):
    im = Image.new('RGBA', (S, S), fill)
    return im, ImageDraw.Draw(im)


# ---------------------------------------------------------------- 遺構（朽ちたもの。あとで gen_data が汚しを足す）

def aged_panel():
    im, d = new(rgba(AGED))
    d.rectangle([0, 0, S - 1, S - 1], outline=rgba(AGED_SEAM))
    return im, d


def tiled_ruin_panel():
    im, d = aged_panel()
    rnd = random.Random(31)
    for ty in range(0, S, 4):
        for tx in range(0, S, 4):
            k = rnd.uniform(-0.06, 0.05)
            c = mix(AGED, (255, 255, 255), k) if k > 0 else mix(AGED, (0, 0, 0), -k)
            d.rectangle([tx + 1, ty + 1, tx + 3, ty + 3], fill=rgba(c))
    for k in range(0, S, 4):
        d.line([(k, 0), (k, S - 1)], fill=rgba(AGED_SEAM))
        d.line([(0, k), (S - 1, k)], fill=rgba(AGED_SEAM))
    return im


def vented_ruin_panel():
    im, d = aged_panel()
    d.rectangle([2, 2, 13, 13], fill=(70, 68, 60, 255))
    for y in (3, 6, 9, 12):
        d.line([(2, y), (13, y)], fill=rgba(AGED_SEAM))
        d.line([(2, y + 1), (13, y + 1)], fill=(52, 50, 44, 255))
    d.rectangle([2, 2, 13, 13], outline=rgba(mix(AGED_SEAM, (0, 0, 0), 0.2)))
    return im


def striped_ruin_panel():
    im, d = aged_panel()
    d.line([(0, 3), (S - 1, 3)], fill=rgba(AGED_SEAM))
    d.line([(0, 12), (S - 1, 12)], fill=rgba(AGED_SEAM))
    d.rectangle([1, 6, 14, 9], fill=(84, 92, 90, 255))
    rnd = random.Random(57)
    for x in range(1, 15):
        if rnd.random() < 0.55:
            d.line([(x, 7), (x, 8)], fill=(118, 160, 162, 255))       # 消えかけた光の帯
    return im


def scorched_ruin_panel():
    im, d = aged_panel()
    d.line([(0, 8), (S - 1, 8)], fill=rgba(AGED_SEAM))
    rnd = random.Random(83)
    px = im.load()
    for _ in range(5):
        cx, cy, r = rnd.uniform(2, 13), rnd.uniform(2, 13), rnd.uniform(2.5, 5)
        for y in range(S):
            for x in range(S):
                dist = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
                if dist < r:
                    k = 0.55 * (1 - dist / r)
                    c = px[x, y]
                    px[x, y] = rgba(mix(c, (58, 50, 42), k))
    return im


# ---------------------------------------------------------------- 遺構（朽ちていない白い建材）

def _pure_bevel(d, inset=0):
    a, b = inset, S - 1 - inset
    d.line([(a, a), (b, a)], fill=(255, 255, 255, 255))
    d.line([(a, a), (a, b)], fill=(255, 255, 255, 255))
    d.line([(a, b), (b, b)], fill=rgba(PURE_SHADE))
    d.line([(b, a), (b, b)], fill=rgba(PURE_SHADE))


def pristine_ruin_panel():
    im, d = new(rgba(PURE))
    d.rectangle([0, 0, S - 1, S - 1], outline=rgba(PURE_SEAM))
    _pure_bevel(d, 1)
    # 細い溝と、淡い青の差し色
    d.line([(3, 11), (12, 11)], fill=rgba(PURE_SEAM))
    d.line([(3, 12), (12, 12)], fill=(255, 255, 255, 255))
    d.point([(3, 4), (4, 4)], fill=rgba(PURE_ACCENT))
    return im


def pristine_ruin_tiles():
    im, d = new(rgba(PURE))
    for ox in (0, 8):
        for oy in (0, 8):
            d.rectangle([ox, oy, ox + 7, oy + 7], outline=rgba(PURE_SEAM))
            d.line([(ox + 1, oy + 1), (ox + 6, oy + 1)], fill=(255, 255, 255, 255))
            d.line([(ox + 1, oy + 1), (ox + 1, oy + 6)], fill=(255, 255, 255, 255))
            d.line([(ox + 1, oy + 6), (ox + 6, oy + 6)], fill=rgba(PURE_SHADE))
            d.line([(ox + 6, oy + 1), (ox + 6, oy + 6)], fill=rgba(PURE_SHADE))
    d.point((8, 8), fill=rgba(PURE_ACCENT))
    return im


def pristine_ruin_pillar():
    """柱の側面: 縦の溝（フルート）。上下の端に帯。"""
    im, d = new(rgba(PURE))
    for x in (2, 6, 10, 14):
        d.line([(x, 2), (x, 13)], fill=rgba(PURE_SHADE))
        d.line([(x - 1, 2), (x - 1, 13)], fill=(255, 255, 255, 255))
    for y in (0, 15):
        d.line([(0, y), (S - 1, y)], fill=rgba(PURE_SEAM))
    d.line([(0, 1), (S - 1, 1)], fill=(255, 255, 255, 255))
    d.line([(0, 14), (S - 1, 14)], fill=rgba(PURE_SHADE))
    return im


def pristine_ruin_pillar_top():
    im, d = new(rgba(PURE))
    d.rectangle([0, 0, S - 1, S - 1], outline=rgba(PURE_SEAM))
    d.ellipse([2, 2, 13, 13], outline=rgba(PURE_SEAM))
    d.ellipse([3, 3, 12, 12], outline=(255, 255, 255, 255))
    d.ellipse([6, 6, 9, 9], fill=rgba(PURE_ACCENT))
    return im


def pristine_ruin_light():
    im, d = new(rgba(PURE))
    d.rectangle([0, 0, S - 1, S - 1], outline=rgba(PURE_SEAM))
    _pure_bevel(d, 1)
    d.rectangle([1, 6, 14, 9], fill=(206, 232, 244, 255))
    d.line([(2, 7), (13, 7)], fill=(232, 252, 255, 255))
    d.line([(2, 8), (13, 8)], fill=(214, 246, 255, 255))
    return im


# ---------------------------------------------------------------- 自分で作れる建材

def white_panel():
    """新品の白いパネル: 面取りの縁と、四隅の小さな留め具（遺構の黄ばんだパネルとは別物）。"""
    base = (240, 242, 245)
    im, d = new(rgba(base))
    d.rectangle([0, 0, S - 1, S - 1], outline=(196, 202, 210, 255))
    d.line([(1, 1), (14, 1)], fill=(254, 254, 255, 255))
    d.line([(1, 1), (1, 14)], fill=(254, 254, 255, 255))
    d.line([(1, 14), (14, 14)], fill=(218, 222, 228, 255))
    d.line([(14, 1), (14, 14)], fill=(218, 222, 228, 255))
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
        d.point((x, y), fill=(176, 184, 194, 255))
    return im


def white_light_panel():
    """明るく光る白いパネル: 白い枠の中に、中心ほど明るい光の面。"""
    im, d = new((240, 242, 245, 255))
    d.rectangle([0, 0, S - 1, S - 1], outline=(196, 202, 210, 255))
    for y in range(2, 14):
        for x in range(2, 14):
            k = max(abs(x - 7.5), abs(y - 7.5)) / 6.0
            d.point((x, y), fill=rgba(mix((255, 255, 255), (206, 232, 248), k * k)))
    d.rectangle([1, 1, 14, 14], outline=(226, 230, 236, 255))
    return im


def black_reinforced_panel():
    """黒色強化パネル: 黒い装甲板に、角から角への補強の筋と四隅の鋲。"""
    base = (30, 32, 38)
    im, d = new(rgba(base))
    d.line([(2, 2), (13, 13)], fill=(46, 48, 56, 255))
    d.line([(13, 2), (2, 13)], fill=(46, 48, 56, 255))
    d.rectangle([5, 5, 10, 10], fill=(24, 25, 30, 255), outline=(58, 60, 70, 255))
    d.rectangle([0, 0, S - 1, S - 1], outline=(12, 12, 16, 255))
    d.line([(1, 1), (14, 1)], fill=(64, 66, 76, 255))
    d.line([(1, 1), (1, 14)], fill=(64, 66, 76, 255))
    d.line([(1, 14), (14, 14)], fill=(20, 20, 24, 255))
    d.line([(14, 1), (14, 14)], fill=(20, 20, 24, 255))
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
        d.point((x, y), fill=(120, 124, 136, 255))
    return im


# ---------------------------------------------------------------- 重力パネル

def gravity_base(iid, on):
    """縁のない地の絵（並べると模様が続く）。上向き（低重力）か下向き（高重力）の山形の矢印を2つ。"""
    g = GRAVITY[iid]
    im, d = new(rgba(g['base']))
    col = g['on'] if on else g['off']
    for oy in (2, 9):
        pts = [(3, oy + 4), (8, oy - 1), (12, oy + 4)] if g['up'] else [(3, oy), (8, oy + 5), (12, oy)]
        d.line(pts, fill=rgba(col), width=1)
        d.line([(p[0], p[1] + 1) for p in pts], fill=rgba(col), width=1)
        if on:
            d.line(pts, fill=rgba(g['core']), width=1)
    if on:
        # 働いている間は、地にもうっすら光の粒
        rnd = random.Random(len(iid))
        for _ in range(6):
            d.point((rnd.randrange(S), rnd.randrange(S)), fill=rgba(mix(g['base'], g['on'], 0.5)))
    return im


def gravity_edge(iid, side):
    """縁取りの1辺（切り抜き）。外側の行は暗く、内側の行は明るい。角は外側の色で、隣の辺と同じ色になる。"""
    g = GRAVITY[iid]
    im, d = new()
    outer, inner = rgba(g['outer']), rgba(g['inner'])
    if side == 'top':
        d.line([(0, 0), (S - 1, 0)], fill=outer)
        d.line([(1, 1), (S - 2, 1)], fill=inner)
    elif side == 'bottom':
        d.line([(0, S - 1), (S - 1, S - 1)], fill=outer)
        d.line([(1, S - 2), (S - 2, S - 2)], fill=inner)
    elif side == 'left':
        d.line([(0, 0), (0, S - 1)], fill=outer)
        d.line([(1, 1), (1, S - 2)], fill=inner)
    else:
        d.line([(S - 1, 0), (S - 1, S - 1)], fill=outer)
        d.line([(S - 2, 1), (S - 2, S - 2)], fill=inner)
    return im


def gravity_framed(iid):
    """持ったときと壊すときの粒の絵: 地の絵に4辺の縁取り。"""
    im = gravity_base(iid, False)
    for side in ('top', 'bottom', 'left', 'right'):
        im.alpha_composite(gravity_edge(iid, side))
    return im


def receiver_side(on):
    """受電器の側面: 白い外装に、電力の差し込み口。"""
    im = white_panel()
    d = ImageDraw.Draw(im)
    d.rectangle([4, 4, 11, 11], fill=(40, 48, 58, 255), outline=(150, 160, 172, 255))
    glow = (120, 220, 255) if on else (70, 96, 116)
    d.rectangle([6, 6, 9, 9], fill=rgba(glow))
    d.point([(6, 6)], fill=(230, 250, 255, 255) if on else rgba(mix(glow, (255, 255, 255), 0.2)))
    return im


def receiver_top(on):
    """受電器の天面: 重力パネルへ配るコイルの輪。"""
    im = white_panel()
    d = ImageDraw.Draw(im)
    glow = (120, 220, 255) if on else (120, 138, 156)
    d.ellipse([2, 2, 13, 13], fill=(44, 50, 60, 255), outline=(150, 160, 172, 255))
    d.ellipse([4, 4, 11, 11], outline=rgba(glow))
    d.ellipse([6, 6, 9, 9], fill=rgba(glow if on else (84, 96, 108)))
    return im


# ---------------------------------------------------------------- 書き出し

RUIN_ART = {
    'tiled_ruin_panel': tiled_ruin_panel, 'vented_ruin_panel': vented_ruin_panel,
    'striped_ruin_panel': striped_ruin_panel, 'scorched_ruin_panel': scorched_ruin_panel,
    'pristine_ruin_panel': pristine_ruin_panel, 'pristine_ruin_tiles': pristine_ruin_tiles,
    'pristine_ruin_pillar': pristine_ruin_pillar, 'pristine_ruin_light': pristine_ruin_light,
}
DECO_ART = {'white_panel': white_panel, 'white_light_panel': white_light_panel,
            'black_reinforced_panel': black_reinforced_panel}


def is_pristine(iid):
    """汚しを入れない（朽ちていない）建材か。"""
    return iid.startswith('pristine_')


def textures(tb, save):
    for iid, fn in {**RUIN_ART, **DECO_ART}.items():
        save(fn(), tb / f'{iid}.png')
    save(pristine_ruin_pillar_top(), tb / 'pristine_ruin_pillar_top.png')
    for iid in GRAVITY:
        save(gravity_base(iid, False), tb / f'{iid}_base.png')
        save(gravity_base(iid, True), tb / f'{iid}_base_on.png')
        for side in ('top', 'bottom', 'left', 'right'):
            save(gravity_edge(iid, side), tb / f'{iid}_edge_{side}.png')
        save(gravity_framed(iid), tb / f'{iid}.png')
    for on in (False, True):
        suffix = '_on' if on else ''
        save(receiver_side(on), tb / f'gravity_panel_receiver{suffix}.png')
        save(receiver_top(on), tb / f'gravity_panel_receiver_top{suffix}.png')


def _cube(textures):
    """1マスの立方体（面ごとのテクスチャ名）。どの面も隣が詰まっていれば隠す。"""
    return {'from': [0, 0, 0], 'to': [16, 16, 16],
            'faces': {d: {'uv': [0, 0, 16, 16], 'texture': textures[d], 'cullface': d} for d in DIRS}}


def models(write_json, assets):
    mb = assets / 'models' / 'block'
    mi = assets / 'models' / 'item'
    bs = assets / 'blockstates'
    # 柱（上下の端は丸い断面）。原木と同じく、軸の向きで回す
    pillar_tex = {'end': f'{MODID}:block/pristine_ruin_pillar_top', 'side': f'{MODID}:block/pristine_ruin_pillar'}
    write_json(mb / 'pristine_ruin_pillar.json', {'parent': 'minecraft:block/cube_column', 'textures': pillar_tex})
    write_json(mb / 'pristine_ruin_pillar_horizontal.json', {'parent': 'minecraft:block/cube_column_horizontal', 'textures': pillar_tex})
    write_json(bs / 'pristine_ruin_pillar.json', {'variants': {
        'axis=y': {'model': f'{MODID}:block/pristine_ruin_pillar'},
        'axis=z': {'model': f'{MODID}:block/pristine_ruin_pillar_horizontal', 'x': 90},
        'axis=x': {'model': f'{MODID}:block/pristine_ruin_pillar_horizontal', 'x': 90, 'y': 90}}})
    # 重力パネル: 地（働いているかで2つ）＋つながっていない辺の縁取り（隣の向きごとに1つ）
    for iid in GRAVITY:
        for suffix in ('', '_on'):
            write_json(mb / f'{iid}_base{suffix}.json', {
                'parent': 'minecraft:block/block',
                'textures': {'particle': f'{MODID}:block/{iid}', 'all': f'{MODID}:block/{iid}_base{suffix}'},
                'elements': [_cube({d: '#all' for d in DIRS})]})
        for nd in DIRS:
            faces = {}
            for face, edges in EDGE_OF.items():
                if nd in edges:
                    faces[face] = {'uv': [0, 0, 16, 16], 'texture': f'#{edges[nd]}', 'cullface': face}
            write_json(mb / f'{iid}_edge_{nd}.json', {
                'parent': 'minecraft:block/block',
                'render_type': 'minecraft:cutout',
                'textures': {'particle': f'{MODID}:block/{iid}',
                             **{s: f'{MODID}:block/{iid}_edge_{s}' for s in ('top', 'bottom', 'left', 'right')}},
                'elements': [{'from': [0, 0, 0], 'to': [16, 16, 16], 'faces': faces}]})
        multipart = [{'when': {'powered': 'false'}, 'apply': {'model': f'{MODID}:block/{iid}_base'}},
                     {'when': {'powered': 'true'}, 'apply': {'model': f'{MODID}:block/{iid}_base_on'}}]
        for nd in DIRS:
            multipart.append({'when': {nd: 'false'}, 'apply': {'model': f'{MODID}:block/{iid}_edge_{nd}'}})
        write_json(bs / f'{iid}.json', {'multipart': multipart})
        write_json(mi / f'{iid}.json', {'parent': 'minecraft:block/cube_all', 'textures': {'all': f'{MODID}:block/{iid}'}})
    # 受電器
    for suffix in ('', '_on'):
        write_json(mb / f'gravity_panel_receiver{suffix}.json', {'parent': 'minecraft:block/cube_bottom_top', 'textures': {
            'top': f'{MODID}:block/gravity_panel_receiver_top{suffix}',
            'bottom': f'{MODID}:block/white_panel',
            'side': f'{MODID}:block/gravity_panel_receiver{suffix}'}})
    write_json(bs / 'gravity_panel_receiver.json', {'variants': {
        'lit=false': {'model': f'{MODID}:block/gravity_panel_receiver'},
        'lit=true': {'model': f'{MODID}:block/gravity_panel_receiver_on'}}})
    write_json(mi / 'gravity_panel_receiver.json', {'parent': f'{MODID}:block/gravity_panel_receiver'})
