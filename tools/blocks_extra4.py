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
    """
    星屑の結晶（交差する2枚の板に描く絵。背景は透明）: 細い結晶が束になって伸び、先が光る。
    根元は殻の煤と土で汚れて暗く、先へ行くほど澄む。面ごとに明るさが違い、ところどころ欠けて濁った所や、
    こびりついた塵の粒がある。
    """
    rnd = random.Random(67)
    im, d = new()
    px = im.load()
    # (根元 x, 根元 y, 先 x, 先 y, 太さ)
    spikes = [(4, 15, 3, 6, 2), (7, 15, 7, 2, 3), (10, 15, 12, 5, 2), (6, 15, 5, 9, 2), (9, 15, 10, 8, 2), (12, 15, 14, 11, 1)]
    deep = (78, 64, 140)          # 影の面
    body = (126, 116, 210)        # 結晶の面
    lit = (168, 214, 255)         # 光の当たる面
    soot = (54, 46, 52)           # 根元の煤
    for x0, y0, x1, y1, w in spikes:
        n = max(abs(x1 - x0), abs(y1 - y0))
        for i in range(n + 1):
            t = i / n
            x = x0 + (x1 - x0) * t
            y = y0 + (y1 - y0) * t
            half = max(0, round((w - 1) * (1 - t * 0.8) / 2 + 0.2))
            for k in range(-half, half + 1):
                qx, qy = round(x) + k, round(y)
                if not (0 <= qx < 16 and 0 <= qy < 16):
                    continue
                # 左の面は影、真ん中の筋は光る面、右は結晶の色
                c = deep if k < 0 else (lit if k == 0 else body)
                if k == 0 and half == 0:
                    c = mix(body, lit, 0.6)
                # 根元ほど煤で汚れ、先ほど澄む
                dirt = max(0.0, 0.75 - t * 1.6)
                c = mix(c, soot, dirt)
                # ざらつき（面ごとの明るさの揺らぎ）
                j = rnd.uniform(-0.08, 0.08)
                c = mix(c, (255, 255, 255), j) if j > 0 else mix(c, (0, 0, 0), -j)
                px[qx, qy] = rgba(c)
        # 先の光と、欠けて濁った所
        px[x1, y1] = (240, 248, 255, 255)
        if n > 5:
            mx, my = round(x0 + (x1 - x0) * 0.55), round(y0 + (y1 - y0) * 0.55)
            if px[mx, my][3]:
                px[mx, my] = rgba(mix(body, (150, 140, 150), 0.55))
    # こびりついた塵と土の粒（結晶の上だけ）
    for _ in range(9):
        x, y = rnd.randint(2, 14), rnd.randint(6, 15)
        if px[x, y][3]:
            px[x, y] = rgba(rnd.choice([(92, 78, 70), (70, 62, 66), (110, 98, 120)]))
    # 根元の土くれ
    for x in range(3, 14):
        if rnd.random() < 0.6:
            px[x, 15] = rgba(rnd.choice([(58, 48, 44), (74, 62, 54), (46, 40, 44)]))
    for x, y in ((6, 4), (11, 3), (3, 8)):
        if not px[x, y][3]:
            px[x, y] = (220, 190, 255, 200)
    return im


def meteorite_core():
    """隕石の核: 溶けた隕鉄の地に、星屑が溶け込んで光る脈が走る。中心ほど明るい。"""
    rnd = random.Random(113)
    iron = (88, 90, 102)
    im, d = new(rgba(iron))
    px = im.load()
    for y in range(S):
        for x in range(S):
            n = rnd.uniform(-0.09, 0.07)
            px[x, y] = rgba(mix(iron, (255, 255, 255), n) if n > 0 else mix(iron, (0, 0, 0), -n))
    # 焼けた殻の名残（四隅が暗い）
    for y in range(S):
        for x in range(S):
            e = max(abs(x - 7.5), abs(y - 7.5)) / 7.5
            if e > 0.8:
                r, g, b, a = px[x, y]
                px[x, y] = rgba(mix((r, g, b), (30, 26, 30), (e - 0.8) * 3))
    # 光る脈（枝分かれ）
    veins = [[(1, 3), (5, 6), (8, 7), (12, 5), (15, 7)], [(8, 7), (7, 11), (9, 15)], [(5, 6), (3, 10), (0, 12)],
             [(12, 5), (11, 1)]]
    for v in veins:
        d.line(v, fill=(120, 96, 210, 255), width=2)
    for v in veins:
        d.line(v, fill=(170, 220, 255, 255))
    # 中心の明るい塊
    d.ellipse([6, 5, 10, 9], fill=(150, 200, 255, 255))
    d.ellipse([7, 6, 9, 8], fill=(235, 245, 255, 255))
    for _ in range(5):
        d.point((rnd.randint(1, 14), rnd.randint(1, 14)), fill=(210, 190, 255, 255))
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


def stardust_cluster_item():
    """星屑の結晶の持ったときの絵: ブロックの絵から目立たない1行を抜いて縦を1ドット縮め、上下左右の中央に寄せる。
    （ブロックは地面から生えるので下に寄ったまま。アイテムの欄では中央に置く）"""
    import art16
    im = stardust_cluster()
    px = im.load()
    box = im.getbbox()
    top, bottom = box[1], box[3] - 1
    # 抜く行: 上下2行ずつを除いた中で、すぐ下の行といちばん似ている行（抜いても形が変わらない）
    def diff(y):
        return sum(sum(abs(a - b) for a, b in zip(px[x, y], px[x, y + 1])) for x in range(S))
    drop = min(range(top + 2, bottom - 2), key=diff)
    out = Image.new('RGBA', im.size, (0, 0, 0, 0))
    po = out.load()
    for y in range(S):
        src = y if y < drop else y + 1
        if src >= S:
            continue
        for x in range(S):
            po[x, y] = px[x, src]
    return art16.center(out)


def teacup_porcelain():
    """置いたティーカップの磁器（白。染めた色で塗る）。光の当たる上は明るく、下はわずかに影。"""
    im, d = new((248, 248, 248, 255))
    for y in range(S):
        v = 255 - y * 2
        d.line([(0, y), (S - 1, y)], fill=(v, v, v, 255))
    return im


def teacup_gold():
    """金の縁。"""
    im, d = new((226, 186, 84, 255))
    for x in range(0, S, 3):
        d.point((x, 0), fill=(250, 222, 140, 255))
    return im


def teacup_tea():
    """紅茶の面: 茶色に、光の映りこみ。"""
    im, d = new((130, 82, 52, 255))
    d.line([(4, 4), (7, 4)], fill=(176, 120, 78, 255))
    d.point((5, 5), fill=(176, 120, 78, 255))
    return im


def textures(tb, save):
    save(teacup_porcelain(), tb / 'teacup_porcelain.png')
    save(teacup_gold(), tb / 'teacup_gold.png')
    save(teacup_tea(), tb / 'teacup_tea.png')
    save(meteorite_crust(), tb / 'meteorite_crust.png')
    save(meteoric_iron_chunk(), tb / 'meteoric_iron_chunk.png')
    save(stardust_cluster(), tb / 'stardust_cluster.png')
    save(stardust_cluster_item(), tb.parent / 'item' / 'stardust_cluster.png')
    save(meteorite_core(), tb / 'meteorite_core.png')
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


def teacup_elements():
    """置いたティーカップ: 受け皿、底と4枚の壁で中が凹んだカップ、縁より低い紅茶、金の縁、東に輪の取っ手。
    磁器の面は色付けの番号0（ブロックエンティティの染めた色）。"""
    def box(a, b, tex, tint=True, faces=None):
        out = {}
        for f in faces or ('north', 'south', 'east', 'west', 'up', 'down'):
            face = {'texture': tex}
            if tint:
                face['tintindex'] = 0
            out[f] = face
        return {'from': a, 'to': b, 'faces': out}

    p, g, t = '#porcelain', '#gold', '#tea'
    return [
        box([3, 0, 3], [13, 1, 13], p),                       # 受け皿
        box([5, 1, 5], [11, 2, 11], p),                       # 底
        box([5, 2, 5], [11, 7, 6], p),                        # 北の壁
        box([5, 2, 10], [11, 7, 11], p),                      # 南の壁
        box([5, 2, 6], [6, 7, 10], p),                        # 西の壁
        box([10, 2, 6], [11, 7, 10], p),                      # 東の壁
        box([5, 7, 5], [11, 7.5, 6], g, False),               # 金の縁
        box([5, 7, 10], [11, 7.5, 11], g, False),
        box([5, 7, 6], [6, 7.5, 10], g, False),
        box([10, 7, 6], [11, 7.5, 10], g, False),
        box([6, 5, 6], [10, 5.25, 10], t, False, ('up',)),    # 紅茶（縁より低い）
        box([11, 5, 7.5], [12.75, 5.75, 8.5], p),             # 取っ手（上・下・外側）
        box([11, 2.75, 7.5], [12.75, 3.5, 8.5], p),
        box([12, 3.5, 7.5], [12.75, 5, 8.5], p),
    ]


def models(write_json, assets):
    mb = assets / 'models' / 'block'
    mi = assets / 'models' / 'item'
    bs = assets / 'blockstates'
    # 置いたティーカップ（取っ手が、置いた人から見て右に来るように回す）
    write_json(mb / 'teacup.json', {'textures': {'porcelain': f'{MODID}:block/teacup_porcelain', 'gold': f'{MODID}:block/teacup_gold',
                                                 'tea': f'{MODID}:block/teacup_tea', 'particle': f'{MODID}:block/teacup_porcelain'},
                                    'elements': teacup_elements()})
    write_json(bs / 'teacup.json', {'variants': {
        'facing=north': {'model': f'{MODID}:block/teacup'}, 'facing=east': {'model': f'{MODID}:block/teacup', 'y': 90},
        'facing=south': {'model': f'{MODID}:block/teacup', 'y': 180}, 'facing=west': {'model': f'{MODID}:block/teacup', 'y': 270}}})
    for iid in ('meteorite_crust', 'meteoric_iron_chunk', 'meteorite_core', 'fulgurite_block'):
        write_json(mb / f'{iid}.json', {'parent': 'minecraft:block/cube_all', 'textures': {'all': f'{MODID}:block/{iid}'}})
        write_json(mi / f'{iid}.json', {'parent': f'{MODID}:block/{iid}'})
    write_json(bs / 'meteorite_crust.json', {'variants': {
        'natural=false': {'model': f'{MODID}:block/meteorite_crust'},
        'natural=true': {'model': f'{MODID}:block/meteorite_crust'}}})
    for iid in ('meteoric_iron_chunk', 'meteorite_core', 'fulgurite_block'):
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
                                              'textures': {'layer0': f'{MODID}:item/stardust_cluster'}})
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
    # 置いたティーカップ: 染めた色ごと、アンドロイドの装飾のティーカップに戻る
    write_json(lt / 'teacup.json', {'type': 'minecraft:block', 'pools': [{
        'rolls': 1, 'conditions': [{'condition': 'minecraft:survives_explosion'}],
        'entries': [{'type': 'minecraft:item', 'name': f'{MODID}:android_teacup', 'functions': [
            {'function': 'minecraft:copy_components', 'source': 'block_entity', 'include': ['minecraft:dyed_color']}]}]}]})
    write_json(lt / 'meteoric_iron_chunk.json', _ore_like('meteoric_iron_chunk', 'raw_meteoric_iron', 2, 3))
    write_json(lt / 'stardust_cluster.json', _ore_like('stardust_cluster', 'stardust', 2, 4))
    # 隕石の核: 隕鉄の原石と星屑をまとめて（シルクタッチならブロックそのもの。置いてもコンパスは指さない）
    core = _ore_like('meteorite_core', 'raw_meteoric_iron', 4, 6)
    core['pools'].append({'rolls': 1, 'conditions': [{'condition': 'minecraft:inverted', 'term': _silk()}], 'entries': [
        {'type': 'minecraft:item', 'name': f'{MODID}:stardust', 'functions': [
            {'function': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': 3, 'max': 5}},
            {'function': 'minecraft:apply_bonus', 'enchantment': 'minecraft:fortune', 'formula': 'minecraft:ore_drops'},
            {'function': 'minecraft:explosion_decay'}]}]})
    write_json(lt / 'meteorite_core.json', core)
    write_json(lt / 'fulgurite_block.json', _ore_like('fulgurite_block', 'fulgurite', 2, 3))
