# -*- coding: utf-8 -*-
"""創世炉の絵とモデル（gen_data.py から呼ぶ）。

- 創世炉の核: 黒い台に金の縁取り。天面に光る八芒の星と渦、側面に光る輪の窓
- 創世炉の枠: 黒い装甲板に金の縁と、中心に小さな星
- 創世炉の柱: 黒い柱の中を、白金の光の筋が縦に通る
- 原初の種: 金の星と、そのまわりの紫の軌道の輪。輪の上を光の粒が回り、星が脈打つ（動く絵）
"""
import math

from blocks_extra import MODID, S, mix, new, rgba

BLACK = (16, 14, 22)
PLATE = (34, 30, 44)
GOLD = (232, 190, 96)
PALE = (255, 244, 214)
DEEP_GOLD = (150, 112, 50)


def _frame_edges(d, col=GOLD, inner=DEEP_GOLD):
    d.rectangle([0, 0, S - 1, S - 1], outline=rgba(col))
    d.rectangle([1, 1, S - 2, S - 2], outline=rgba(inner))


def frame():
    im, d = new(rgba(PLATE))
    _frame_edges(d)
    # 斜めの継ぎ目と、中心の小さな星
    for k in range(2, 6):
        d.point([(k, k), (S - 1 - k, k), (k, S - 1 - k), (S - 1 - k, S - 1 - k)], fill=rgba(mix(PLATE, GOLD, 0.35)))
    d.polygon([(7.5, 4.5), (8.6, 6.9), (11, 7.5), (8.6, 8.1), (7.5, 10.5), (6.4, 8.1), (4, 7.5), (6.4, 6.9)],
              fill=rgba(mix(GOLD, PALE, 0.4)))
    d.point([(7, 7), (8, 8), (7, 8), (8, 7)], fill=rgba(PALE))
    return im


def pylon():
    im, d = new(rgba(BLACK))
    # 両脇の金の帯と、中を通る光の筋
    for x in (1, S - 2):
        d.line([(x, 0), (x, S - 1)], fill=rgba(DEEP_GOLD))
    d.rectangle([5, 0, 10, S - 1], fill=rgba(PLATE))
    d.rectangle([6, 0, 9, S - 1], fill=rgba(mix(GOLD, PALE, 0.3)))
    d.rectangle([7, 0, 8, S - 1], fill=rgba(PALE))
    for y in (0, 7, 8, S - 1):
        d.line([(2, y), (4, y)], fill=rgba(GOLD))
        d.line([(11, y), (13, y)], fill=rgba(GOLD))
    return im


def core_top():
    im, d = new(rgba(BLACK))
    _frame_edges(d)
    c = 7.5
    # 八芒の星
    pts = []
    for k in range(16):
        r = 6.6 if k % 2 == 0 else 2.6
        a = math.pi / 2 + k * math.pi / 8
        pts.append((c + r * math.cos(a), c + r * math.sin(a)))
    d.polygon(pts, fill=rgba(mix(GOLD, BLACK, 0.15)), outline=rgba(GOLD))
    # 渦の腕
    for arm in range(2):
        for i in range(10):
            t = i / 9
            a = arm * math.pi + t * 3.2
            r = 0.8 + t * 3.4
            x, y = c + r * math.cos(a), c + r * math.sin(a)
            d.point((round(x), round(y)), fill=rgba(mix(PALE, GOLD, t)))
    d.rectangle([7, 7, 8, 8], fill=(255, 255, 255, 255))
    return im


def core_side():
    im, d = new(rgba(PLATE))
    _frame_edges(d)
    d.line([(2, 2), (S - 3, 2)], fill=rgba(GOLD))
    # 光る輪の窓
    d.ellipse([4, 5, 11, 12], fill=rgba(BLACK), outline=rgba(GOLD))
    d.ellipse([6, 7, 9, 10], fill=rgba(mix(GOLD, PALE, 0.5)))
    d.point([(7, 8), (8, 8)], fill=rgba(PALE))
    d.line([(2, S - 3), (S - 3, S - 3)], fill=rgba(DEEP_GOLD))
    return im


def core_bottom():
    im, d = new(rgba(BLACK))
    _frame_edges(d, DEEP_GOLD, PLATE)
    return im


SEED_FRAMES, SEED_TICKS = 16, 3


def seed(t=0.0):
    """原初の種（1コマ）: 金の星（白い芯）と、そのまわりの紫の軌道の輪（16px の中に収まる幅）。輪の奥の半分は星の後ろ、
    手前の半分は星の上。t（0〜1）で、輪の上を光の粒が回り（奥では暗く、星の後ろでは隠れる）、星が脈打ち、四隅の星がまたたく。"""
    from PIL import Image
    im = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    px = im.load()
    ink, gold, deep, pale = (24, 28, 40), (240, 190, 80), (170, 110, 40), (255, 244, 200)
    front_c, back_c = (210, 170, 255), (120, 90, 170)

    def put(x, y, c):
        if 0 <= x < S and 0 <= y < S:
            px[x, y] = tuple(c[:3]) + (255,)

    def ring(n=90):
        for k in range(n):
            a = 2 * math.pi * k / n
            yield round(8 + 6.2 * math.cos(a) - 0.5), round(8 + 2.0 * math.sin(a) - 0.5), math.sin(a) > 0

    for x, y, front in ring():
        if not front:
            put(x, y, back_c)
    pulse = 0.5 + 0.5 * math.cos(t * 2 * math.pi)
    r = 3.6
    for y in range(S):
        for x in range(S):
            d = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if d <= r:
                q = d / r
                core = mix(pale, (255, 255, 255), pulse * 0.6)
                put(x, y, mix(core, gold, q * (1.6 - 0.4 * pulse)) if q < 0.85 else deep)
    put(7, 7, (255, 255, 255))
    for x, y, front in ring():
        if front:
            put(x, y, front_c)
    # 輪の上を回る光の粒（2つ、反対側に）
    for k in range(2):
        a = 2 * math.pi * (t + k / 2)
        x, y = round(8 + 6.2 * math.cos(a) - 0.5), round(8 + 2.0 * math.sin(a) - 0.5)
        front = math.sin(a) > 0
        if front:
            put(x, y, (255, 255, 255))
        elif math.hypot(x + 0.5 - 8, y + 0.5 - 8) > r:
            put(x, y, (190, 160, 240))                      # 奥（星に隠れない所だけ）
    # 縁（形の外側の1ドット）
    add = []
    for y in range(S):
        for x in range(S):
            if px[x, y][3] > 0:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < S and 0 <= ny < S and px[nx, ny][3] > 120:
                    add.append((x, y))
                    break
    for x, y in add:
        put(x, y, ink)
    # 四隅の星のまたたき（縁の外）
    for i, (x, y) in enumerate(((3, 3), (12, 12))):
        if (round(t * SEED_FRAMES) + i * 8) % SEED_FRAMES < 6:
            put(x, y, pale)
    return im


def seed_sheet():
    """原初の種の動く絵（コマを縦に並べた1枚）。"""
    from PIL import Image
    sheet = Image.new('RGBA', (S, S * SEED_FRAMES), (0, 0, 0, 0))
    for k in range(SEED_FRAMES):
        sheet.paste(seed(k / SEED_FRAMES), (0, k * S))
    return sheet


def textures(tb, ti, save):
    save(frame(), tb / 'genesis_frame.png')
    save(pylon(), tb / 'genesis_pylon.png')
    save(core_top(), tb / 'genesis_core_top.png')
    save(core_side(), tb / 'genesis_core_side.png')
    save(core_bottom(), tb / 'genesis_core_bottom.png')
    save(seed_sheet(), ti / 'primordial_seed.png')
    import json
    (ti / 'primordial_seed.png.mcmeta').write_text(
        json.dumps({'animation': {'frametime': SEED_TICKS, 'interpolate': False}}, indent=2) + '\n', encoding='utf-8')


def models(write_json, assets):
    mb = assets / 'models' / 'block'
    sides = ('north', 'south', 'east', 'west')
    glow = {'block_light': 15, 'sky_light': 15}
    # 創世炉の核: 胴と、光る天面の1ドットの蓋（光は GenesisCoreRenderer が上に描く）
    write_json(mb / 'genesis_core.json', {
        'parent': 'minecraft:block/block',
        'textures': {'top': f'{MODID}:block/genesis_core_top', 'side': f'{MODID}:block/genesis_core_side',
                     'bottom': f'{MODID}:block/genesis_core_bottom', 'particle': f'{MODID}:block/genesis_core_side'},
        'elements': [
            {'from': [0, 0, 0], 'to': [16, 15, 16], 'faces': {
                **{d: {'texture': '#side', 'uv': [0, 1, 16, 16], 'cullface': d} for d in sides},
                'down': {'texture': '#bottom', 'cullface': 'down'}}},
            {'from': [0, 15, 0], 'to': [16, 16, 16], 'neoforge_data': glow, 'faces': {
                **{d: {'texture': '#side', 'uv': [0, 0, 16, 1], 'cullface': d} for d in sides},
                'up': {'texture': '#top', 'cullface': 'up'}}},
        ]})
    write_json(assets / 'blockstates' / 'genesis_core.json', {'variants': {'': {'model': f'{MODID}:block/genesis_core'}}})
    write_json(assets / 'models' / 'item' / 'genesis_core.json', {'parent': f'{MODID}:block/genesis_core'})
