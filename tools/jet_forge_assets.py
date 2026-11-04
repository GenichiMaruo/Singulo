# -*- coding: utf-8 -*-
"""降着ジェット鍛造炉の部品とコラプサー素材の絵（art16 から呼ぶ。HD 版は 16px の絵を拡大する）。

- 素材の色: 黒に近い砲金の板、青白いプラズマの光（ジェット）、琥珀の熱（吸熱板・回収口）
- 基礎: 重い黒鋼の床板に、溝と琥珀の誘導灯（マルチブロック搬入出ポートと入れ替える板なので落ち着いた柄）
- 拘束コイル: 太い巻線の中を青白い磁場の光が通る
- 収束リング: 段のある輪の切り口。中心の白い点に向かって光が絞られる
- 吸熱板: 縦のフィンが並ぶ銅色の板。奥は熱で赤い
- 骨組み: 斜めに組んだ梁
- 浮揚器・回収口: 照射室の天井と床の中心（紫の重力の輪と、琥珀の受け口）
- コラプサー・ビレット: 縮退物質殻に包まれた鈍い黒紫の地金。継ぎ目からジェットの青い光が漏れる
- コラプサー・インゴット: 光を吸う黒の地金に、ゆっくり流れる虹色の筋（動く絵）
"""
import math

from PIL import ImageDraw

# art16 は読み込みの途中でこのファイルの JET を使うので、art16 の関数は描くときに引く（循環して読み込むため）
import art16 as A

S = 16


def rgba(c, a=255):
    return tuple(int(v) for v in c[:3]) + (a,)


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def lighten(c, t):
    return mix(c, (255, 255, 255), t)


def darken(c, t):
    return mix(c, (0, 0, 0), t)

JET = dict(plate=(46, 48, 60), frame=(26, 27, 36), edge=(104, 110, 130), trim=(118, 196, 255), glow=(176, 226, 255),
           hot=(248, 252, 255), dark=(10, 12, 20))
HEAT = (230, 140, 60)
HEAT_HOT = (255, 214, 150)


def foundation():
    im = A.mat_plate(JET, 81, rivets=False, seam=False)
    d = ImageDraw.Draw(im)
    for y in (5, 10):
        d.line([(2, y), (S - 3, y)], fill=rgba(darken(JET['plate'], 0.35)))
        d.line([(2, y + 1), (S - 3, y + 1)], fill=rgba(lighten(JET['plate'], 0.1)))
    for x in (3, 7, 11):
        d.point((x + 1, 8), fill=rgba(HEAT))
    for x, y in ((2, 2), (S - 3, 2), (2, S - 3), (S - 3, S - 3)):
        d.point((x, y), fill=rgba(JET['edge']))
    return im


def confinement_coil():
    im = A.mat_coil(JET, 82)
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, S - 1, 1], fill=rgba(JET['frame']))
    d.rectangle([0, S - 2, S - 1, S - 1], fill=rgba(JET['frame']))
    d.line([(0, 1), (S - 1, 1)], fill=rgba(JET['edge']))
    for x in (2, 13):
        d.point((x, 0), fill=rgba(JET['trim']))
        d.point((x, S - 1), fill=rgba(JET['trim']))
    return im


def collimator():
    im = A.weathered(JET['frame'], seed=83, strength=0.9)
    d = ImageDraw.Draw(im)
    for r, col in ((7, darken(JET['edge'], 0.2)), (5, JET['edge']), (3, JET['trim'])):
        d.ellipse([7.5 - r, 7.5 - r, 7.5 + r, 7.5 + r], outline=rgba(col))
    d.ellipse([6, 6, 9, 9], fill=rgba(JET['glow']))
    d.point([(7, 7), (8, 8)], fill=rgba(JET['hot']))
    for a in range(4):
        ang = a * math.pi / 2 + math.pi / 4
        for k in range(4, 7):
            d.point((round(7.5 + k * math.cos(ang)), round(7.5 + k * math.sin(ang))), fill=rgba(lighten(JET['trim'], 0.2)))
    d.rectangle([0, 0, S - 1, S - 1], outline=rgba(darken(JET['frame'], 0.4)))
    return im


def heat_sink():
    im = A.weathered(darken(HEAT, 0.62), seed=84, strength=1.2)
    d = ImageDraw.Draw(im)
    for x in range(1, S - 1, 3):
        d.line([(x, 1), (x, S - 2)], fill=rgba(mix(HEAT, (120, 70, 40), 0.35)))
        d.line([(x + 1, 1), (x + 1, S - 2)], fill=rgba(darken(HEAT, 0.45)))
    for x in range(1, S - 1, 3):
        d.point((x, 7), fill=rgba(HEAT_HOT))
        d.point((x, 8), fill=rgba(HEAT))
    d.rectangle([0, 0, S - 1, S - 1], outline=rgba(JET['frame']))
    return im


def truss():
    im = A.mat_frame(JET, 85)
    d = ImageDraw.Draw(im)
    d.rectangle([5, 5, 10, 10], fill=rgba(JET['frame']))
    for k in range(2, S - 2):
        d.point((k, k), fill=rgba(JET['edge']))
        d.point((S - 1 - k, k), fill=rgba(darken(JET['edge'], 0.25)))
    return im


def levitator():
    im = A.mat_plate(JET, 86, rivets=False, seam=False)
    d = ImageDraw.Draw(im)
    d.ellipse([1, 1, 14, 14], fill=rgba(JET['dark']), outline=rgba(JET['edge']))
    d.ellipse([3, 3, 12, 12], outline=(180, 130, 255, 255))
    d.ellipse([5, 5, 10, 10], outline=(214, 180, 255, 255))
    d.ellipse([7, 7, 8, 8], fill=(250, 240, 255, 255))
    return im


def collector():
    im = A.mat_plate(JET, 87, rivets=True, seam=False)
    d = ImageDraw.Draw(im)
    d.rectangle([3, 3, 12, 12], fill=rgba(JET['dark']), outline=rgba(HEAT))
    for k in range(3):
        d.rectangle([4 + k, 4 + k, 11 - k, 11 - k], outline=rgba(mix(darken(HEAT, 0.6), HEAT, k / 2)))
    d.point([(7, 7), (8, 8), (7, 8), (8, 7)], fill=rgba(HEAT_HOT))
    return im


PARTS = {
    'jet_forge_foundation': foundation,
    'jet_forge_confinement_coil': confinement_coil,
    'jet_forge_collimator': collimator,
    'jet_forge_heat_sink': heat_sink,
    'jet_forge_truss': truss,
    'jet_forge_levitator': levitator,
    'jet_forge_collector': collector,
}


def part(iid):
    fn = PARTS.get(iid)
    return fn() if fn else None


# ---------------------------------------------------------------- 素材

BILLET = (58, 46, 72)
INGOT = (22, 18, 30)


def collapsar_billet():
    """縮退物質殻に包まれた太い地金。継ぎ目からジェットの青い光が漏れる。"""
    def f(d):
        d.polygon([(1, 9), (5, 5), (14, 5), (10, 9)], fill=rgba(lighten(BILLET, 0.25)))
        d.rectangle([1, 9, 10, 13], fill=rgba(BILLET))
        d.polygon([(10, 9), (14, 5), (14, 9), (10, 13)], fill=rgba(darken(BILLET, 0.3)))
        # 殻の帯（灰色）と、継ぎ目の光
        for x in (3, 7):
            d.line([(x, 9), (x, 13)], fill=(132, 128, 140, 255))
            d.line([(x + 2, 5), (x - 2 + 2, 9)], fill=(150, 146, 160, 255))
        d.line([(2, 11), (9, 11)], fill=rgba(JET['trim']))
        d.point([(5, 11), (6, 11)], fill=rgba(JET['hot']))
        d.line([(11, 10), (13, 8)], fill=rgba(darken(JET['trim'], 0.3)))
    return A.vcenter(A.outlined(f, **A.SOFT_EDGE))


def collapsar_ingot_frame(t):
    """光を吸う黒の地金に、ゆっくり流れる虹色の筋（t は 0〜1）。"""
    def hue(h):
        h %= 1.0
        k = h * 6
        i = int(k)
        f = k - i
        c = [(255, 90, 200), (150, 110, 255), (90, 200, 255), (110, 255, 210), (255, 230, 120), (255, 140, 90)]
        return mix(c[i % 6], c[(i + 1) % 6], f)

    def f(d):
        d.polygon([(2, 9), (6, 5), (13, 5), (9, 9)], fill=rgba(lighten(INGOT, 0.18)))
        d.rectangle([2, 9, 9, 12], fill=rgba(INGOT))
        d.polygon([(9, 9), (13, 5), (13, 8), (9, 12)], fill=rgba(darken(INGOT, 0.3)))
        # 上面と正面を斜めに流れる筋（明るさは中央ほど強い）
        for k in range(3):
            p = (t + k / 3) % 1.0
            x = 2 + p * 10
            col = hue(t + k * 0.21)
            for y in range(5, 9):
                xx = round(x + (8 - y) * 0.9)
                if 2 <= xx <= 13 and xx - (9 - y) >= 2 and xx <= 13 - (9 - y) + 4:
                    d.point((xx, y), fill=rgba(mix(col, (255, 255, 255), 0.2)))
            xf = round(2 + p * 7)
            if 2 <= xf <= 9:
                d.point((xf, 10), fill=rgba(col))
                d.point((xf, 11), fill=rgba(darken(col, 0.35)))
        d.line([(3, 12), (8, 12)], fill=rgba(mix(INGOT, (120, 110, 160), 0.5)))
        d.point((12, 6), fill=(255, 255, 255, 255))
    # どのコマも形は同じなので、コマごとに上下中央へ寄せても揺れない
    return A.vcenter(A.outlined(f, **A.SOFT_EDGE))


# ---------------------------------------------------------------- 加工中の赤熱（鍛造炉の描画が、熱に合わせて絵を差し替える）

# 段階ごとの熱の色（暗い赤熱 → 橙 → 黄 → 白熱）
HEAT_STAGES = ((235, 62, 18), (255, 118, 34), (255, 210, 110), (255, 248, 232))
# 赤熱の絵を作る素材（鍛造炉で加工される物と、仕上げのあとの完成品）
HEATED_ITEMS = ('collapsar_billet', 'collapsar_ingot')


def heated(im, stage):
    """素材の絵を、熱の段階（1〜4）の色に焼く。明るい所ほど白く光り、形の陰影は残す。輪郭は熱の色を暗くした縁にする。"""
    from PIL import Image
    hc = HEAT_STAGES[stage - 1]
    out = im.convert('RGBA').copy()
    px = out.load()
    src = im.convert('RGBA').load()
    w, h = out.size
    weight = 0.72 + 0.07 * stage
    for y in range(h):
        for x in range(w):
            r, g, b, a = src[x, y]
            if a == 0:
                continue
            lum = (0.3 * r + 0.59 * g + 0.11 * b) / 255
            # 輪郭（周りに透明がある）は、熱の色を暗くした縁
            edge = any(0 <= x + dx < w and 0 <= y + dy < h and src[x + dx, y + dy][3] == 0
                       for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            glow = 0.62 + 0.38 * min(1.0, lum * 1.6 + 0.15 * stage)
            hot = tuple(min(255, int(c * glow)) for c in hc)
            if edge:
                hot = darken(hot, 0.45)
            px[x, y] = rgba(mix((r, g, b), hot, weight if not edge else 0.75), a)
    return out


def heat_textures(ti, save, base):
    """赤熱の絵（<id>_heat_1〜4）。base(id) は元の絵（動く絵は最初のコマ）。"""
    for iid in HEATED_ITEMS:
        im = base(iid)
        for stage in range(1, len(HEAT_STAGES) + 1):
            save(heated(im, stage), ti / f'{iid}_heat_{stage}.png')


def heat_models(write_json, assets):
    for iid in HEATED_ITEMS:
        for stage in range(1, len(HEAT_STAGES) + 1):
            write_json(assets / 'models' / 'item' / f'{iid}_heat_{stage}.json',
                       {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'singulo:item/{iid}_heat_{stage}'}})
