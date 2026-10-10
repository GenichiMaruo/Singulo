# -*- coding: utf-8 -*-
"""第3弾のブロックの絵とモデル（gen_data.py から呼ぶ）。

- 遺構ガラス（新）と、割れた遺構ガラス・ひび割れた遺構パネルの描き直し（割れ方をそれらしく）
- 透けるパネル（ホワイト系・黒色強化系と、それぞれの四芒星の模様入り）
- 衝撃発電パネル
- 遺構保管庫（報酬の箱らしい立体。金の縁が光る）、警備機ドックと封印コンソール（動く絵）
"""
import math
import random

from PIL import Image, ImageDraw

from blocks_extra import AGED, AGED_SEAM, MODID, S, aged_panel, is_pristine, mix, new, rgba, white_panel

GLASS_TINT = (196, 222, 228)


def _jagged(rnd, p0, p1, steps, wobble):
    """p0 から p1 へ、少しずつ曲がるひびの折れ線。"""
    pts = [p0]
    for i in range(1, steps):
        t = i / steps
        x = p0[0] + (p1[0] - p0[0]) * t + rnd.uniform(-wobble, wobble)
        y = p0[1] + (p1[1] - p0[1]) * t + rnd.uniform(-wobble, wobble)
        pts.append((x, y))
    pts.append(p1)
    return pts


def _crack(d, pts, dark, light):
    """ひび: 暗い芯と、右下に1ドットずらした明るい欠けの縁（奥行きが出る）。"""
    d.line([(x + 1, y + 1) for x, y in pts], fill=light)
    d.line(pts, fill=dark)


def _star(d, fill, outline, center=(7.5, 7.5), r=6.5, inner=1.6):
    """四芒星（上下左右に尖り、間がくぼんだ星）。"""
    cx, cy = center
    pts = []
    for k in range(8):
        a = -math.pi / 2 + k * math.pi / 4
        rr = r if k % 2 == 0 else inner
        pts.append((cx + math.cos(a) * rr, cy + math.sin(a) * rr))
    d.polygon(pts, fill=fill, outline=outline)


# ---------------------------------------------------------------- 遺構（ひび・ガラス）

def cracked_ruin_panel():
    """ひび割れた遺構パネル: 角から走る太いひびと枝分かれ、欠けた角、こまかな剥がれ。"""
    rnd = random.Random(19)
    im, d = aged_panel()
    d.line([(0, 8), (S - 1, 8)], fill=rgba(AGED_SEAM))
    dark, light, chip = (96, 86, 66, 255), (244, 238, 216, 255), (150, 138, 108, 255)
    # 左上の角が欠けて、下地がのぞく
    d.polygon([(0, 0), (4, 0), (3, 1), (1, 3), (0, 4)], fill=chip)
    d.line([(0, 4), (1, 3), (3, 1), (4, 0)], fill=dark)
    main = _jagged(rnd, (3, 1), (12, 15), 6, 0.9)
    _crack(d, main, dark, light)
    for k in (2, 4):
        a = main[k]
        end = (min(15, a[0] + rnd.uniform(3, 5)), max(0, a[1] - rnd.uniform(1, 3)))
        _crack(d, _jagged(rnd, a, end, 3, 0.6), dark, light)
    side = main[3]
    _crack(d, _jagged(rnd, side, (0, min(15, side[1] + 4)), 3, 0.6), dark, light)
    for _ in range(7):
        d.point((rnd.randrange(1, 15), rnd.randrange(1, 15)), fill=chip)
    return im


def intact_ruin_glass():
    """遺構ガラス: 古びた枠に、うっすら色のついたガラス。斜めの映り込みと、ほこりの点。"""
    im, d = new(rgba(GLASS_TINT, 64))
    d.rectangle([0, 0, S - 1, S - 1], outline=rgba(AGED_SEAM))
    d.rectangle([1, 1, S - 2, S - 2], outline=rgba(AGED, 200))
    for k in range(5):
        d.line([(3 + k, 12 - k * 2), (5 + k, 10 - k * 2)], fill=(255, 255, 255, 110))
    d.line([(9, 13), (13, 9)], fill=(255, 255, 255, 70))
    rnd = random.Random(23)
    for _ in range(6):
        d.point((rnd.randrange(2, 14), rnd.randrange(2, 14)), fill=(150, 140, 110, 120))
    return im


def broken_ruin_glass():
    """割れた遺構ガラス: 当たった点から放射状に走るひびと輪のひび、抜け落ちた穴、角度の違う破片の濃淡。"""
    rnd = random.Random(41)
    im = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    cx, cy = 10.0, 5.5
    rays = sorted(rnd.uniform(0, 2 * math.pi) for _ in range(7))
    px = im.load()
    for y in range(S):
        for x in range(S):
            ang = math.atan2(y + 0.5 - cy, x + 0.5 - cx) % (2 * math.pi)
            sector = sum(1 for r in rays if r <= ang) % len(rays)
            dist = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            alpha = 52 + (sector * 37) % 60 + (18 if dist < 4.5 else 0)
            shade = mix(GLASS_TINT, (255, 255, 255), ((sector * 53) % 40) / 100)
            px[x, y] = rgba(shade, alpha)
    d = ImageDraw.Draw(im)
    crack, shadow = (248, 252, 255, 220), (90, 110, 116, 150)
    for r in rays:
        end = (cx + math.cos(r) * 18, cy + math.sin(r) * 18)
        pts = _jagged(rnd, (cx, cy), end, 5, 0.7)
        d.line([(x + 1, y) for x, y in pts], fill=shadow)
        d.line(pts, fill=crack)
    for radius in (3.2, 6.5):
        ring = [(cx + math.cos(a) * (radius + rnd.uniform(-0.6, 0.6)), cy + math.sin(a) * (radius + rnd.uniform(-0.6, 0.6)))
                for a in [i * 2 * math.pi / 9 for i in range(10)]]
        d.line(ring, fill=crack)
    # 当たった所の破片は抜け落ちている（ぎざぎざの穴）
    hole = [(cx + math.cos(a) * rnd.uniform(1.6, 2.8), cy + math.sin(a) * rnd.uniform(1.6, 2.8))
            for a in [i * 2 * math.pi / 8 for i in range(8)]]
    d.polygon(hole, fill=(0, 0, 0, 0))
    d.line(hole + [hole[0]], fill=(255, 255, 255, 235))
    # 隅も1か所欠けている
    d.polygon([(0, 15), (0, 11), (2, 12), (3, 14), (5, 15)], fill=(0, 0, 0, 0))
    # 古びた枠（欠けた所には残らない）
    for i in range(S):
        for x, y in ((i, 0), (i, S - 1), (0, i), (S - 1, i)):
            if px[x, y][3] > 0:
                px[x, y] = rgba(AGED_SEAM)
    return im


# ---------------------------------------------------------------- 透けるパネル（自分で作れる）

def white_glass_panel(star=False):
    im, d = new((236, 244, 250, 72))
    d.rectangle([0, 0, S - 1, S - 1], outline=(196, 202, 210, 255))
    d.line([(1, 1), (14, 1)], fill=(254, 254, 255, 230))
    d.line([(1, 1), (1, 14)], fill=(254, 254, 255, 230))
    d.line([(1, 14), (14, 14)], fill=(218, 222, 228, 230))
    d.line([(14, 1), (14, 14)], fill=(218, 222, 228, 230))
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        d.point((x, y), fill=(176, 184, 194, 255))
    if star:
        _star(d, (255, 255, 255, 150), (196, 214, 232, 235))
        d.point([(7, 7), (8, 8)], fill=(255, 255, 255, 255))
    else:
        d.line([(4, 10), (9, 5)], fill=(255, 255, 255, 120))
    return im


def black_reinforced_glass(star=False):
    im, d = new((22, 24, 32, 128))
    d.rectangle([0, 0, S - 1, S - 1], outline=(12, 12, 16, 255))
    d.rectangle([1, 1, S - 2, S - 2], outline=(46, 48, 58, 255))
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        d.point((x, y), fill=(120, 124, 136, 255))
    if star:
        _star(d, (60, 56, 92, 170), (170, 176, 210, 240))
        d.point([(7, 7), (8, 8)], fill=(220, 224, 255, 255))
    else:
        d.line([(3, 3), (12, 12)], fill=(70, 74, 88, 170))
        d.line([(12, 3), (3, 12)], fill=(70, 74, 88, 170))
    return im


# ---------------------------------------------------------------- 衝撃発電パネル

def impact_top(on):
    """天面: 白い枠の中の黒い受け面に、衝撃の輪（ためている間は琥珀色に光る）。"""
    im = white_panel()
    d = ImageDraw.Draw(im)
    d.rectangle([2, 2, 13, 13], fill=(36, 38, 46, 255), outline=(150, 160, 172, 255))
    glow = (255, 190, 80) if on else (120, 104, 80)
    d.ellipse([4, 4, 11, 11], outline=rgba(glow))
    d.ellipse([6, 6, 9, 9], outline=rgba(mix(glow, (255, 255, 255), 0.3) if on else glow))
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
        d.point((x, y), fill=rgba(glow))
    return im


def impact_side(on):
    """側面: 白い外装に、衝撃を受け止めるばね（ジグザグ）。"""
    im = white_panel()
    d = ImageDraw.Draw(im)
    d.rectangle([4, 2, 11, 13], fill=(44, 48, 58, 255))
    spring = (255, 190, 80) if on else (150, 156, 166)
    d.line([(5, 3), (10, 5), (5, 7), (10, 9), (5, 11), (10, 13)], fill=rgba(spring))
    d.line([(4, 2), (11, 2)], fill=(200, 206, 214, 255))
    return im


# ---------------------------------------------------------------- 遺構保管庫（報酬の箱）

CACHE_DARK, CACHE_BODY = (34, 36, 46), (24, 26, 36)
GOLD, GOLD_HI, GOLD_LO = (236, 186, 84), (255, 232, 160), (170, 120, 44)
SEALED_RED, SEALED_HI = (230, 70, 70), (255, 170, 160)


def _trim(sealed):
    return (SEALED_RED, SEALED_HI) if sealed else (GOLD, GOLD_HI)


def cache_base(sealed):
    im, d = new(rgba(CACHE_DARK))
    trim, _ = _trim(sealed)
    d.line([(0, 14), (S - 1, 14)], fill=rgba(trim))
    d.line([(0, 15), (S - 1, 15)], fill=rgba(mix(CACHE_DARK, (0, 0, 0), 0.4)))
    return im


def cache_body(sealed):
    """胴の側面（y 2〜11 が見える。UV では行 5〜14）: 黒い装甲に、金の四芒星の紋章。"""
    im, d = new(rgba(CACHE_BODY))
    trim, hi = _trim(sealed)
    d.rectangle([3, 6, 12, 13], outline=rgba(mix(CACHE_BODY, trim, 0.35)))
    _star(d, rgba(trim), rgba(hi), center=(7.5, 9.5), r=3.6, inner=1.0)
    d.point((7, 9), fill=rgba(hi))
    if sealed:
        for x in range(2, 14, 2):
            d.point((x, 7 + (x // 2) % 5), fill=rgba(SEALED_RED, 200))
    return im


def cache_lid(sealed):
    """蓋の側面（y 11〜14、UV では行 2〜5）: 金の帯。"""
    im, d = new(rgba(CACHE_DARK))
    trim, _ = _trim(sealed)
    d.line([(0, 2), (S - 1, 2)], fill=rgba(mix(CACHE_DARK, (255, 255, 255), 0.15)))
    d.line([(0, 4), (S - 1, 4)], fill=rgba(trim))
    for x in range(1, S, 3):
        d.point((x, 3), fill=rgba(mix(CACHE_DARK, trim, 0.5)))
    return im


def cache_top(sealed):
    im, d = new(rgba(CACHE_DARK))
    trim, hi = _trim(sealed)
    d.rectangle([0, 0, S - 1, S - 1], outline=rgba(trim))
    d.rectangle([2, 2, 13, 13], outline=rgba(mix(CACHE_DARK, trim, 0.4)))
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        d.point((x, y), fill=rgba(hi))
    return im


def cache_gold(sealed):
    base, hi, lo = (SEALED_RED, SEALED_HI, (150, 40, 40)) if sealed else (GOLD, GOLD_HI, GOLD_LO)
    im, d = new(rgba(base))
    for k in range(S):
        d.point((k, k), fill=rgba(hi))
        d.point((S - 1 - k, k), fill=rgba(lo))
    return im


# ---------------------------------------------------------------- 警備機ドックと封印コンソール（動く絵）

ANIM_FRAMES = 8


def dock_frame(k):
    """警備機ドック: 古びた白い台座の中の格納口。走査の輪が回り、中央の赤い目が脈打つ。"""
    t = k / ANIM_FRAMES
    im, d = new(rgba(AGED))
    d.rectangle([0, 0, S - 1, S - 1], outline=rgba(AGED_SEAM))
    d.line([(1, 1), (14, 1)], fill=rgba(mix(AGED, (255, 255, 255), 0.4)))
    d.ellipse([1, 1, 14, 14], fill=(40, 44, 52, 255), outline=(150, 156, 164, 255))
    for a in range(4):
        start = (t * 360 + a * 90) % 360
        d.arc([3, 3, 12, 12], start, start + 50, fill=(120, 220, 255, 255))
    d.ellipse([5, 5, 10, 10], fill=(24, 26, 32, 255))
    pulse = 0.5 + 0.5 * math.sin(2 * math.pi * t)
    eye = mix((110, 20, 20), (255, 90, 80), pulse)
    d.ellipse([6, 6, 9, 9], fill=rgba(eye))
    d.point((7, 7), fill=rgba(mix(eye, (255, 255, 255), 0.5)))
    for i, (x, y) in enumerate(((2, 2), (13, 2), (13, 13), (2, 13))):
        on = (i + k // 2) % 4 == 0
        d.point((x, y), fill=(120, 220, 255, 255) if on else (70, 80, 86, 255))
    return im


def console_frame(k):
    """封印コンソール: 黒い操作盤に、回る封印の目盛りと輪、中央の四芒星が脈打つ。"""
    t = k / ANIM_FRAMES
    im, d = new((26, 30, 38, 255))
    d.rectangle([0, 0, S - 1, S - 1], outline=(150, 230, 255, 255))
    d.rectangle([1, 1, S - 2, S - 2], outline=(40, 70, 86, 255))
    for i in range(12):
        a = 2 * math.pi * (i / 12 + t / 3)
        x, y = 7.5 + math.cos(a) * 5.6, 7.5 + math.sin(a) * 5.6
        lit = i % 3 == k % 3
        d.point((round(x), round(y)), fill=(200, 245, 255, 255) if lit else (70, 130, 150, 255))
    d.ellipse([3, 3, 12, 12], outline=(80, 170, 200, 255))
    pulse = 0.5 + 0.5 * math.sin(2 * math.pi * t)
    _star(d, rgba(mix((60, 140, 170), (190, 245, 255), pulse)), rgba(mix((120, 200, 230), (255, 255, 255), pulse)),
          r=3.8, inner=1.0)
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        d.point((x, y), fill=(150, 230, 255, 255) if pulse > 0.5 else (60, 110, 130, 255))
    return im


def _sheet(frames):
    sheet = Image.new('RGBA', (S, S * len(frames)), (0, 0, 0, 0))
    for i, f in enumerate(frames):
        sheet.paste(f, (0, S * i))
    return sheet


# 汚しを入れない（または、ここで自前で入れる）建材
NO_WEATHER = {'ruin_cache', 'ruin_guard_dock', 'seal_console', 'cracked_ruin_panel'}


def skip_weathering(iid):
    return is_pristine(iid) or iid in NO_WEATHER


def textures(tb, save, weather, write_json):
    """weather(im, seed) は汚しを足す関数（art16.weather_existing）。"""
    save(weather(cracked_ruin_panel(), 13), tb / 'cracked_ruin_panel.png')
    save(intact_ruin_glass(), tb / 'intact_ruin_glass.png')
    save(broken_ruin_glass(), tb / 'ruin_glass.png')
    save(white_glass_panel(), tb / 'white_glass_panel.png')
    save(white_glass_panel(star=True), tb / 'white_star_glass_panel.png')
    save(black_reinforced_glass(), tb / 'black_reinforced_glass.png')
    save(black_reinforced_glass(star=True), tb / 'black_star_glass.png')
    for on in (False, True):
        suffix = '_on' if on else ''
        save(impact_top(on), tb / f'impact_generator_panel_top{suffix}.png')
        save(impact_side(on), tb / f'impact_generator_panel{suffix}.png')
    for sealed in (False, True):
        suffix = '_sealed' if sealed else ''
        save(cache_base(sealed), tb / f'ruin_cache_base{suffix}.png')
        save(cache_body(sealed), tb / f'ruin_cache_body{suffix}.png')
        save(cache_lid(sealed), tb / f'ruin_cache_lid{suffix}.png')
        save(cache_top(sealed), tb / f'ruin_cache_top{suffix}.png')
        save(cache_gold(sealed), tb / f'ruin_cache_gold{suffix}.png')
    # 動く絵（コマごとに同じ汚しを入れて、ちらつかないように）
    for iid, fn, seed, ticks in (('ruin_guard_dock', dock_frame, 61, 3), ('seal_console', console_frame, 67, 4)):
        save(_sheet([weather(fn(k), seed) for k in range(ANIM_FRAMES)]), tb / f'{iid}.png')
        write_json(tb / f'{iid}.png.mcmeta', {'animation': {'frametime': ticks, 'interpolate': True}})


def _cache_model(sealed):
    suffix = '_sealed' if sealed else ''
    t = {k: f'{MODID}:block/ruin_cache_{k}{suffix}' for k in ('base', 'body', 'lid', 'top', 'gold')}
    t['particle'] = t['lid']
    glow = {'block_light': 15, 'sky_light': 15}
    sides = ('north', 'south', 'east', 'west')

    def box(f, to, faces, light=False):
        e = {'from': f, 'to': to, 'faces': faces}
        if light:
            e['neoforge_data'] = glow
        return e

    els = [
        # 台座
        box([0, 0, 0], [16, 2, 16], {**{d: {'texture': '#base'} for d in sides},
                                    'down': {'texture': '#base', 'cullface': 'down'}, 'up': {'texture': '#base'}}),
        # 胴（金の紋章）
        box([2, 2, 2], [14, 11, 14], {d: {'texture': '#body'} for d in sides}),
        # 蓋（金の帯）と天面
        box([0, 11, 0], [16, 14, 16], {**{d: {'texture': '#lid'} for d in sides}, 'up': {'texture': '#top'},
                                      'down': {'texture': '#lid'}}),
        # 天面の金の飾り（光る）
        box([6, 14, 6], [10, 15, 10], {**{d: {'texture': '#gold'} for d in sides}, 'up': {'texture': '#gold'}}, light=True),
    ]
    # 四隅の金の柱（光る）
    for x0 in (1, 14):
        for z0 in (1, 14):
            faces = {('west' if x0 == 1 else 'east'): {'texture': '#gold'},
                     ('north' if z0 == 1 else 'south'): {'texture': '#gold'}}
            els.append(box([x0, 2, z0], [x0 + 1, 11, z0 + 1], faces, light=True))
    return {'parent': 'minecraft:block/block', 'textures': t, 'elements': els}


def models(write_json, assets):
    mb = assets / 'models' / 'block'
    mi = assets / 'models' / 'item'
    bs = assets / 'blockstates'
    write_json(mb / 'ruin_cache.json', _cache_model(False))
    write_json(mb / 'ruin_cache_sealed.json', _cache_model(True))
    write_json(mi / 'ruin_cache.json', {'parent': f'{MODID}:block/ruin_cache'})
    for suffix in ('', '_on'):
        write_json(mb / f'impact_generator_panel{suffix}.json', {'parent': 'minecraft:block/cube_bottom_top', 'textures': {
            'top': f'{MODID}:block/impact_generator_panel_top{suffix}',
            'bottom': f'{MODID}:block/white_panel',
            'side': f'{MODID}:block/impact_generator_panel{suffix}'}})
    write_json(bs / 'impact_generator_panel.json', {'variants': {
        'lit=false': {'model': f'{MODID}:block/impact_generator_panel'},
        'lit=true': {'model': f'{MODID}:block/impact_generator_panel_on'}}})
    write_json(mi / 'impact_generator_panel.json', {'parent': f'{MODID}:block/impact_generator_panel'})
