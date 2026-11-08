# -*- coding: utf-8 -*-
"""宇宙現象を封じ込める設備の部品とアイテムの絵とモデル（art16 と cosmic_content から呼ぶ）。

- 共通の外装: 紫がかった黒鋼。重力の光は淡いシアン
- 重力懸垂タンク: 冠に重力の帯、下面に発光する環。細い支柱
- 核パスタ積層蓄電庫: 積層板の側面に波打つ層、導体は金の巻線
- 潮汐紡糸機: 絞りリングは同心の溝が中心へ絞られる。受け台は軸受の丸
- アイテム: 核パスタ（波打つ層の切り身）、潮汐繊維（光る糸のボビン）、縫合拡張（糸を通した針と縫い目）、並列カセット（4つの受け口）
"""
import math

from PIL import Image, ImageDraw

# art16 は読み込みの途中でこのファイルの COSMIC を使うので、art16 の関数は描くときに引く
import art16 as A

S = 16
COSMIC = dict(plate=(70, 66, 92), frame=(46, 43, 62), edge=(134, 126, 166), trim=(150, 226, 240), glow=(176, 236, 250),
              hot=(240, 252, 255), dark=(12, 12, 22))
PASTA = ((106, 76, 255), (232, 90, 192), (255, 224, 160))
THREAD = (255, 235, 192)
COPPER = (208, 140, 84)
GOLD = (232, 190, 110)


def rgba(c, a=255):
    return tuple(int(v) for v in c[:3]) + (a,)


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def lighten(c, t):
    return mix(c, (255, 255, 255), t)


def darken(c, t):
    return mix(c, (0, 0, 0), t)


def frame():
    """引力拘束フレーム: 静かな黒鋼の板。縁の面取りと、四隅の留め具だけ。"""
    base = COSMIC['frame']
    im = A.weathered(base, seed=201, strength=0.7)
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, S - 1, S - 1], outline=rgba(darken(base, 0.45)))
    d.line([(1, 1), (S - 2, 1)], fill=rgba(lighten(base, 0.2)))
    d.line([(1, 1), (1, S - 2)], fill=rgba(lighten(base, 0.14)))
    d.line([(1, S - 2), (S - 2, S - 2)], fill=rgba(darken(base, 0.22)))
    d.line([(S - 2, 1), (S - 2, S - 2)], fill=rgba(darken(base, 0.22)))
    for x, y in ((3, 3), (S - 4, 3), (3, S - 4), (S - 4, S - 4)):
        d.point((x, y), fill=rgba(COSMIC['edge']))
    return im


def laminate_side():
    """核パスタ積層板の側面: 黒鋼に挟まれ、波打ちながら積もった層。"""
    im = A.weathered(COSMIC['dark'], seed=202, strength=0.7)
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, S - 1, 1], fill=rgba(COSMIC['frame']))
    d.rectangle([0, S - 2, S - 1, S - 1], fill=rgba(COSMIC['frame']))
    d.line([(0, 1), (S - 1, 1)], fill=rgba(COSMIC['edge']))
    d.line([(0, S - 2), (S - 1, S - 2)], fill=rgba(darken(COSMIC['edge'], 0.3)))
    for row, y in enumerate((3, 6, 9, 12)):
        col = mix(PASTA[1], PASTA[2], 0.1 + 0.28 * row)
        d.line([(0, y), (S - 1, y)], fill=rgba(col))
        d.line([(0, y + 1), (S - 1, y + 1)], fill=rgba(darken(col, 0.45)))
    return im


def laminate_top():
    """核パスタ積層板の上下: 留め具の格子の下で、層が細く光る。"""
    im = A.mat_plate(COSMIC, 203, rivets=False, seam=False)
    d = ImageDraw.Draw(im)
    for y in range(3, S - 3, 2):
        d.line([(3, y), (S - 4, y)], fill=rgba(mix(PASTA[1], PASTA[2], y / S), 255))
        d.line([(3, y + 1), (S - 4, y + 1)], fill=rgba(darken(PASTA[1], 0.72)))
    d.rectangle([2, 2, S - 3, S - 3], outline=rgba(COSMIC['edge']))
    return im


def crown_side():
    """重力懸垂の冠の外周: 中央に重力の帯。"""
    im = A.mat_plate(COSMIC, 204, rivets=False, seam=False)
    d = ImageDraw.Draw(im)
    d.rectangle([0, 6, S - 1, 9], fill=rgba(COSMIC['dark']))
    for x in range(S):
        c = COSMIC['hot'] if (x + 2) % 8 < 2 else COSMIC['trim']
        d.point((x, 7), fill=rgba(c))
        d.point((x, 8), fill=rgba(darken(COSMIC['trim'], 0.25)))
    d.line([(0, 5), (S - 1, 5)], fill=rgba(COSMIC['edge']))
    d.line([(0, 10), (S - 1, 10)], fill=rgba(darken(COSMIC['edge'], 0.4)))
    for x in (2, 13):
        d.point((x, 2), fill=rgba(COSMIC['edge']))
        d.point((x, 13), fill=rgba(COSMIC['edge']))
    return im


def crown_bottom():
    """冠の下面: 液体を吊る発光の環。"""
    im = A.weathered(COSMIC['frame'], seed=205, strength=0.9)
    d = ImageDraw.Draw(im)
    for r, col in ((7, darken(COSMIC['edge'], 0.2)), (5, COSMIC['trim']), (3, darken(COSMIC['trim'], 0.35))):
        d.ellipse([7.5 - r, 7.5 - r, 7.5 + r, 7.5 + r], outline=rgba(col))
    d.ellipse([6, 6, 9, 9], fill=rgba(COSMIC['glow']))
    d.point([(7, 7), (8, 8)], fill=rgba(COSMIC['hot']))
    return im


def crown_top():
    im = A.mat_plate(COSMIC, 206, rivets=True, seam=False)
    d = ImageDraw.Draw(im)
    d.rectangle([6, 6, 9, 9], outline=rgba(darken(COSMIC['trim'], 0.3)))
    return im


def strut():
    """懸垂支柱: 細い柱。光る筋が1本通り、留め輪の所だけ明るい。"""
    im = A.weathered(COSMIC['plate'], seed=207, strength=0.9)
    d = ImageDraw.Draw(im)
    for x in range(S):
        c = lighten(COSMIC['plate'], 0.22) if x % 8 < 2 else darken(COSMIC['plate'], 0.22) if x % 8 > 5 else COSMIC['plate']
        d.line([(x, 0), (x, S - 1)], fill=rgba(c))
    for x in (3, 11):
        d.line([(x, 0), (x, S - 1)], fill=rgba(COSMIC['trim']))
    d.rectangle([0, 6, S - 1, 9], fill=rgba(COSMIC['edge']))
    d.line([(0, 7), (S - 1, 7)], fill=rgba(lighten(COSMIC['edge'], 0.3)))
    return im


def conductor():
    """導体: 金色の巻線の母線。"""
    im = A.weathered(COSMIC['dark'], seed=208, strength=0.6)
    d = ImageDraw.Draw(im)
    for y in range(S):
        c = lighten(GOLD, 0.25) if y % 3 == 0 else COPPER if y % 3 == 1 else darken(COPPER, 0.5)
        d.line([(0, y), (S - 1, y)], fill=rgba(c))
    d.rectangle([6, 0, 9, S - 1], fill=rgba(COSMIC['dark']))
    for y in range(S):
        d.point((7, y), fill=rgba(mix(PASTA[1], PASTA[2], y / S)))
        d.point((8, y), fill=rgba(darken(PASTA[1], 0.3)))
    return im


def draw_ring():
    """絞りリング: 同心の溝が中心の白い点へ絞られる。四隅は重い。"""
    base = COSMIC['plate']
    im = A.weathered(base, seed=209, strength=0.7)
    d = ImageDraw.Draw(im)
    # 磁性の薄板を重ねた輪: 横の細い溝と、1本の薄紫の線
    for y in range(2, S - 2, 3):
        d.line([(1, y), (S - 2, y)], fill=rgba(darken(base, 0.3)))
    d.line([(1, 7), (S - 2, 7)], fill=rgba((200, 184, 255)))
    d.line([(1, 8), (S - 2, 8)], fill=rgba(darken((200, 184, 255), 0.45)))
    d.rectangle([0, 0, S - 1, S - 1], outline=rgba(darken(base, 0.5)))
    return im


def cradle():
    """スプール受け台: 軸受の丸と、金の留め具。"""
    im = A.mat_plate(COSMIC, 210, rivets=False, seam=False)
    d = ImageDraw.Draw(im)
    d.ellipse([3, 3, 12, 12], fill=rgba(COSMIC['dark']), outline=rgba(COSMIC['edge']))
    d.ellipse([5, 5, 10, 10], outline=rgba(GOLD))
    d.ellipse([7, 7, 8, 8], fill=rgba(THREAD))
    for x, y in ((1, 1), (S - 2, 1), (1, S - 2), (S - 2, S - 2)):
        d.point((x, y), fill=rgba(GOLD))
    return im


PARTS = {'cosmic_frame': frame, 'pasta_laminate': laminate_side, 'suspension_crown': crown_side, 'suspension_strut': strut,
         'pasta_conductor': conductor, 'tidal_draw_ring': draw_ring, 'spindle_cradle': cradle}


def part(iid):
    return PARTS[iid]()


# ---------------------------------------------------------------- アイテム

def nuclear_pasta():
    """核パスタ: 波打つ層を重ねた切り身。上の層ほど熱い色。"""
    im, d = A.new()
    rows = [(11, PASTA[0]), (8, PASTA[1]), (5, mix(PASTA[1], PASTA[2], 0.6)), (3, PASTA[2])]
    for y0, col in rows:
        for x in range(2, 14):
            w = 0.9 * math.sin(x * 0.95 + y0)
            top = round(y0 + w)
            d.point((x, top), fill=rgba(lighten(col, 0.3)))
            d.point((x, top + 1), fill=rgba(col))
            d.point((x, top + 2), fill=rgba(darken(col, 0.62)))
    d.line([(1, 4), (1, 13)], fill=rgba(COSMIC['frame']))
    d.line([(14, 4), (14, 13)], fill=rgba(COSMIC['frame']))
    d.point([(5, 1), (10, 1)], fill=(255, 255, 255, 255))
    return im


def tidal_filament():
    """潮汐繊維: 光る糸を巻いたボビン。ほどけた端が右下へ伸びる。"""
    im, d = A.new()
    d.rectangle([3, 1, 12, 2], fill=rgba(COSMIC['edge']))
    d.line([(3, 1), (12, 1)], fill=rgba(lighten(COSMIC['edge'], 0.35)))
    d.rectangle([3, 12, 12, 13], fill=rgba(COSMIC['edge']))
    d.line([(3, 13), (12, 13)], fill=rgba(darken(COSMIC['edge'], 0.35)))
    d.rectangle([4, 3, 11, 11], fill=rgba(darken(THREAD, 0.45)))
    for y in range(3, 12):
        for x in range(4, 12):
            c = lighten(THREAD, 0.2) if (x + y * 2) % 4 == 0 else THREAD if (x + y * 2) % 4 == 1 else darken(THREAD, 0.22)
            d.point((x, y), fill=rgba(c))
    d.line([(4, 3), (4, 11)], fill=rgba(darken(THREAD, 0.4)))
    d.point([(12, 9), (13, 10), (14, 11), (14, 12), (13, 13), (13, 14), (12, 14)], fill=rgba(lighten(THREAD, 0.5)))
    return im


def wormhole_seam_upgrade():
    """ワームホール縫合拡張: 暗い裂け目を縫う光る糸と、糸を通した針。"""
    im, d = A.new()
    d.polygon([(4, 2), (7, 1), (8, 7), (7, 14), (4, 13), (5, 8)], fill=rgba((16, 12, 34)))
    d.line([(6, 2), (6, 5), (7, 8), (6, 12)], fill=rgba((122, 72, 220)))
    for y, w in ((3, 3), (6, 4), (9, 4), (12, 3)):
        d.line([(5 - w + 2, y + 1), (6 + w, y - 1)], fill=rgba(THREAD))
    # 針
    d.line([(10, 14), (14, 2)], fill=rgba(lighten(COSMIC['edge'], 0.55)))
    d.point((14, 1), fill=(255, 255, 255, 255))
    d.point([(11, 12), (10, 11), (10, 9)], fill=rgba(COSMIC['trim']))
    return im


def collapsar_parallel_cassette():
    """コラプサー並列カセット: 黒い地金の箱に、素材を抱える4つの受け口。虹色の筋が流れる。"""
    im, d = A.new()
    black = (20, 18, 30)
    d.rectangle([1, 3, 14, 12], fill=rgba(black), outline=rgba((60, 54, 84)))
    d.line([(2, 3), (13, 3)], fill=rgba((104, 96, 138)))
    d.line([(2, 12), (13, 12)], fill=rgba((36, 32, 52)))
    for i, x in enumerate((3, 6, 9, 12)):
        col = mix((120, 220, 255), (240, 120, 230), i / 3)
        d.rectangle([x - 1, 5, x, 8], outline=rgba(col))
        d.point((x, 6), fill=rgba(lighten(col, 0.6)))
        d.point((x - 1, 10), fill=rgba(GOLD))
    d.line([(4, 1), (11, 1)], fill=rgba((60, 54, 84)))
    d.line([(4, 2), (11, 2)], fill=rgba((36, 32, 52)))
    d.line([(5, 13), (10, 13)], fill=rgba((36, 32, 52)))
    return im


ITEMS = {'nuclear_pasta': nuclear_pasta, 'tidal_filament': tidal_filament, 'wormhole_seam_upgrade': wormhole_seam_upgrade,
         'collapsar_parallel_cassette': collapsar_parallel_cassette}

# 面ごとの絵が違う部品は cube_bottom_top、形が箱でない部品は要素で作る
TEXTURED = {'pasta_laminate': {'pasta_laminate_top': laminate_top},
            'suspension_crown': {'suspension_crown_bottom': crown_bottom, 'suspension_crown_top': crown_top}}


def _save(ctx, folder, name, im):
    path = ctx['ASSETS'] / 'textures' / folder / f'{name}.png'
    path.parent.mkdir(parents=True, exist_ok=True)
    im.save(path)
    hd = ctx['RES'] / 'resourcepacks/singulo_hd/assets/singulo/textures' / folder / f'{name}.png'
    hd.parent.mkdir(parents=True, exist_ok=True)
    im.resize((64, 64), Image.Resampling.NEAREST).save(hd)


def _column(a, b, tex):
    faces = {f: {'texture': f'#{tex}'} for f in ('north', 'south', 'east', 'west', 'up', 'down')}
    return {'from': a, 'to': b, 'faces': faces}


def models(ctx):
    """部品とアイテムの絵、部品のモデルを書く。"""
    write = ctx['write_json']
    assets = ctx['ASSETS']
    for iid, draw in PARTS.items():
        _save(ctx, 'block', iid, draw())
    for textures in TEXTURED.values():
        for name, draw in textures.items():
            _save(ctx, 'block', name, draw())
    for iid, draw in ITEMS.items():
        im = draw()
        # アイテムの絵は上下左右に1ドットの余白を残す
        box = im.getbbox()
        assert box and box[0] >= 1 and box[1] >= 1 and box[2] <= 15 and box[3] <= 15, (iid, box)
        _save(ctx, 'item', iid, im)

    def tex(name):
        return f'singulo:block/{name}'

    def block(iid, model):
        write(assets / 'models/block' / f'{iid}.json', model)

    for iid in ('cosmic_frame', 'tidal_draw_ring', 'spindle_cradle'):
        block(iid, {'parent': 'minecraft:block/cube_all', 'textures': {'all': tex(iid)}})
    block('pasta_laminate', {'parent': 'minecraft:block/cube_column',
                             'textures': {'side': tex('pasta_laminate'), 'end': tex('pasta_laminate_top')}})
    block('suspension_crown', {'parent': 'minecraft:block/cube_bottom_top',
                               'textures': {'side': tex('suspension_crown'), 'top': tex('suspension_crown_top'),
                                            'bottom': tex('suspension_crown_bottom')}})
    # 細い柱と留め輪（中が透けて見えるように、箱いっぱいにしない）。親を block/block にして、アイテム欄でも立体に見せる
    block('suspension_strut', {'parent': 'minecraft:block/block', 'textures': {'s': tex('suspension_strut'), 'particle': tex('suspension_strut')},
                               'elements': [_column([6, 0, 6], [10, 16, 10], 's'), _column([5, 6, 5], [11, 10, 11], 's')]})
    block('pasta_conductor', {'parent': 'minecraft:block/block', 'textures': {'c': tex('pasta_conductor'), 'particle': tex('pasta_conductor')},
                              'elements': [_column([4, 0, 4], [12, 16, 12], 'c'), _column([2, 2, 2], [14, 5, 14], 'c'),
                                           _column([2, 11, 2], [14, 14, 14], 'c')]})
