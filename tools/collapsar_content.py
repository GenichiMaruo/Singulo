# -*- coding: utf-8 -*-
"""0.17.0: コラプサー材の使い道（コラプサー内張り・事象の地平線倉庫・ワームホール航路標識）。

ID・レシピ・画面の文言・ハンドブックの章・説明文と、3つのブロックの絵とモデル。logistics_content から読み込んで足す。
内張りを置ける位置と効き目は Java の CollapsarLining・ShapeSpec.allowLining、倉庫は storage.HorizonVaultBlockEntity、
ワームホール便は drone.WormholeRelay。
"""
from pathlib import Path

MODID = 'singulo'

IDS = {
    'コラプサー内張り': ('collapsar_lining', 'Collapsar Lining', 'part_block'),
    '事象の地平線倉庫': ('horizon_vault', 'Event Horizon Vault', 'machine'),
    'ワームホール航路標識': ('wormhole_drone_beacon', 'Wormhole Drone Beacon', 'machine'),
}

# 一般の装置のモデル（筐体と正面の絵）を作らず、ここで書くブロック
CUSTOM_BLOCKS = {'horizon_vault', 'wormhole_drone_beacon'}


def recipes(R, shaped):
    # コラプサーの芯を鋼板とニホニウム板で挟んだ板（1つの塊から4枚）
    R('コラプサー内張り', 5, '作業台', {'ニホニウム板': 4, '鋼板': 4, 'コラプサー・インゴット': 1}, out=4, cat='part',
      note='縮退圧縮炉・C空洞・極低温冷却塔の外装板の位置に置くと、1枚ごとに加工が5%速くなる（20枚で2倍）')
    shaped['コラプサー内張り'] = (('NPN', 'PCP', 'NPN'), {'N': 'ニホニウム板', 'P': '鋼板', 'C': 'コラプサー・インゴット'})
    # ガラスの室の芯に核パスタ（封じ込めた核物質）、左右にコラプサーの支え
    R('事象の地平線倉庫', 5, '作業台', {'ニホニウム板': 4, 'ガラス': 2, 'コラプサー・インゴット': 2, '核パスタ': 1}, cat='machine',
      note='1種類の物を、事実上無制限（約922京個）までしまう')
    shaped['事象の地平線倉庫'] = (('PGP', 'CNC', 'PGP'), {'P': 'ニホニウム板', 'G': 'ガラス', 'C': 'コラプサー・インゴット', 'N': '核パスタ'})
    # ニホニウム板の台と柱に、コラプサーの芯、てっぺんに量子もつれ素子の灯
    R('ワームホール航路標識', 5, '作業台', {'ニホニウム板': 5, 'コラプサー・インゴット': 1, '量子もつれ素子': 1}, cat='machine',
      note='ワームホールの両方の口のそば（4ブロック以内）に置くと、ドローンが口で荷物を受け渡して別の次元へ送る')
    shaped['ワームホール航路標識'] = ((' E ', 'NCN', 'NNN'), {'E': '量子もつれ素子', 'N': 'ニホニウム板', 'C': 'コラプサー・インゴット'})


UI = {
    'gui.singulo.lining.count': ('コラプサー内張り %s 枚：加工 ×%s', 'Collapsar Lining: %s (processing ×%s)'),
    'gui.singulo.lining.hint': ('外装板の位置に置ける。ホロ投影機で金色に光る（1枚 +5%、20枚で2倍）',
                                'Fits in casing positions; the Holo Projector shows them in gold (+5% each, 2× at 20)'),
    'holo.singulo.lining': ('内張り %s / %s 枚（加工 ×%s）・金色の外装板あと %s か所', 'Lining %s / %s (processing ×%s) · %s gold casing spots left'),
    'holo.singulo.lining.report': ('コラプサー内張り：外装板の位置 %s か所に置ける（いま %s 枚。%s 枚で最大の2倍）',
                                   'Collapsar Lining: fits in %s casing positions (%s placed; %s for the maximum 2×)'),
    'tooltip.singulo.horizon_vault.contents': ('%s × %s', '%s × %s'),
    'tooltip.singulo.horizon_vault.capacity': ('1種類の物を %s 個までしまえる', 'Holds up to %s of one item'),
    'gui.singulo.drone_port.via_wormhole': ('ワームホール経由：%s', 'Via wormhole: %s'),
    'holo.singulo.ports': ('搬入出ポートに替えられる外装板 %s か所（水色の枠）', 'Casing blocks you can swap for ports: %s (cyan frames)'),
    'holo.singulo.ports.report': ('搬入出ポート：外装板の位置 %s か所に置ける（投影の水色の枠）', 'Multiblock ports fit in %s casing positions (cyan frames in the projection)'),
}

CHAPTER = ('collapsar', 'singulo:collapsar_ingot', 'コラプサーの使い道', 'Using Collapsar', [
    ('lining', 'コラプサー内張り',
     'コラプサー内張りは、加工をするマルチブロック（縮退圧縮炉・C空洞・極低温冷却塔）の外装板の位置に、外装板の代わりに置ける板です。'
     '1枚ごとに加工が5%速くなり、20枚で2倍になります（冷却塔は高さの倍率に掛け合わせ）。1回の加工の電力は変わらず、毎 tick の電力が増えます。\n'
     '置ける場所は、ホロ投影機で分かります。完成したマルチブロックの制御装置に投影機を使うと、内張りに替えられる外装板が脈打つ金色の枠で光り、'
     '内張り済みの位置はうすい金色の枠になります。画面の下に枚数と速さが出て、チャットには置ける場所の数が出ます。搬入出ポートの位置は示しません（替えると口がなくなるため）。\n'
     '制御装置の画面では、形の大きさの印にカーソルを合わせると、内張りの枚数と速さが見られます。',
     'Collapsar Lining',
     'Collapsar Lining replaces casing blocks on processing multiblocks: the Degenerate Compactor, Casimir Cavity and Cryogenic Cooling Tower. '
     'Each lining speeds processing up by 5%, up to 2× at 20 (the cooling tower multiplies this with its height bonus). Energy per operation stays the same, so power per tick rises.\n'
     'Use the Holo Projector on a finished controller to see where lining fits: casing blocks you can swap pulse in gold, and lined spots show a faint gold frame. '
     'The bar below shows the count and speed, and chat lists how many spots there are. Port positions are not marked, since swapping them would remove the port.\n'
     'In the controller screen, hover the structure size to see the lining count and speed.'),
    ('vault', '事象の地平線倉庫',
     '事象の地平線倉庫は、1種類の物を中の小さなブラックホールに、事実上無制限（約922京個）までしまうブロックです。中身が増えるほどブラックホールが大きくなり（1兆個でいっぱいに見える）、しまった物がその周りを回ります。4面の台に個数が、四隅の柱に埋まり具合の光が出ます。\n'
     '物を持って使うと手の物を入れ、すばやくもう一度使うと持ち物の同じ物を全部入れます。素手で使うと1スタック、素手でスニークして使うと1個取り出します。'
     'ホッパー・パイプ・アンドロイド・ドローンの発着ポートからは、1つの欄の入れ物として出し入れできます。入りきらない分は受け取りません。\n'
     '壊すと中身ごとアイテムになり、置き直すと戻ります。中身の入った倉庫や梱包済みの箱は、倉庫に入れられません。コンパレーターは埋まり具合を出します。',
     'Event Horizon Vault',
     'The Event Horizon Vault stores a practically unlimited amount (about 9.2 quintillion) of one item inside a small black hole. The hole grows as it fills (it looks full at a trillion), the stored item orbits it, the plates on all four sides show the count, and light on the corner pillars shows how full it is.\n'
     'Use an item on it to insert what you hold; use it again quickly to insert every matching item in your inventory. Use it empty-handed to take a stack, or sneak to take one. '
     'Hoppers, pipes, androids and drone ports see it as a single slot. It never accepts more than fits.\n'
     'Breaking it keeps the contents in the item, restored when placed. Filled vaults and packed crates cannot go inside a vault. A comparator reads how full it is.'),
    ('wormhole', 'ワームホール便',
     'ワームホールの両方の口のそば（4ブロック以内）にワームホール航路標識を置くと、ドローンがその口を通って別の次元のポートへ荷物を送れます。'
     '発着ポートの行き先の一覧に、口の先の次元の同じ持ち主のポートが紫の印つきで並びます。道のりは、こちらのポートから口まで＋向こうの口からポートまでで、射程と電力もこの道のりで決まります。\n'
     'ドローンは荷物をこちらの口まで運び、口へ入れて帰ります。向こうの口のそばのドローン・ステーション（64ブロック以内）のドローンが、口から荷物を受け取って届けます。向こう側にもステーションが要ります。\n'
     '荷物は、運ぶドローン・口で待つ間の記録・向こうのドローンの順にいつも1か所にあり、失われません。口が閉じていたり読み込まれていないときは、ドローンは口の上で待ちます。口を壊すと、そこで待っていた荷物は梱包済みの箱のまま落ちます。',
     'Wormhole delivery',
     'Place a Wormhole Drone Beacon within 4 blocks of both mouths of a wormhole, and drones can send cargo through it to ports in another dimension. '
     'Ports on the far side with the same owner appear in the destination list with a purple mark. Distance is from your port to the mouth plus from the far mouth to the port, and range and power follow it.\n'
     'A drone carries the crate to this mouth and passes it through. A drone from a station within 64 blocks of the far mouth picks it up and delivers it, so the far side needs a station too.\n'
     'The cargo is always in one place: the carrying drone, the record at the mouth, then the far drone. If a mouth is closed or unloaded, drones wait above it. Breaking a mouth drops any crate waiting there.'),
])

DESC = {
    'コラプサー内張り': ('加工をするマルチブロック（縮退圧縮炉・C空洞・極低温冷却塔）の外装板の位置に置ける板。1枚ごとに加工が5%速くなり、20枚で2倍。置ける場所はホロ投影機で金色に光る。',
                  'A plate for casing positions on processing multiblocks (Degenerate Compactor, Casimir Cavity, Cryogenic Cooling Tower). Each one speeds processing by 5%, up to 2× at 20. The Holo Projector shows where it fits in gold.'),
    '事象の地平線倉庫': ('1種類の物を中の小さなブラックホールに、事実上無制限（約922京個）までしまう。手で入れる・取り出す、ホッパーやパイプで出し入れできる。壊しても中身を失わない。',
                  'Stores a practically unlimited amount (about 9.2 quintillion) of one item inside a small black hole. Insert and take by hand, or connect hoppers and pipes. Breaking it keeps the contents.'),
    'ワームホール航路標識': ('ワームホールの両方の口のそばに置くと、ドローンが口で荷物を受け渡し、別の次元のポートへ送る。向こう側にもドローン・ステーションが要る。',
                    'Place one near both mouths of a wormhole and drones hand cargo through it to ports in another dimension. The far side also needs a Drone Station.'),
}

SUMMARY = {
    'コラプサー内張り': ('マルチブロックの加工を速くする板。', 'A plate that speeds up multiblock processing.'),
    '事象の地平線倉庫': ('1種類の物を大量にしまう倉庫。', 'Mass storage for one item.'),
    'ワームホール航路標識': ('ドローンをワームホールに通す標識。', 'Lets drones use a wormhole.'),
}


# ---------------------------------------------------------------- 絵

GOLD = (220, 188, 108)
GOLD_HI = (255, 230, 160)
STEEL = (52, 46, 66)
STEEL_HI = (86, 78, 108)
VIOLET = (150, 110, 255)


def _blend(a, b, k):
    return tuple(int(x + (y - x) * k) for x, y in zip(a, b))


def lining_texture():
    """コラプサー内張り: 使い込んだ紫がかった黒鋼の板（色むら・研磨の筋・縁の擦れ）に、金の縁と継ぎ目の十字、四隅の鋲、真ん中にかすかな紫の光。"""
    import surface
    im = surface.metal(STEEL, 'lining', grain=0.07, streak='h', scuffs=5, edge=0.1)
    px = im.load()

    def put(x, y, c):
        px[x, y] = tuple(c) + (255,)
    for i in range(16):
        for x, y in ((i, 0), (i, 15), (0, i), (15, i)):
            put(x, y, GOLD if (i + x + y) % 5 else GOLD_HI)
    for i in range(1, 15):
        put(7, i, _blend(GOLD, STEEL, 0.45))
        put(i, 8, _blend(GOLD, STEEL, 0.45))
    for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
        put(x, y, GOLD_HI)
        put(x + 1, y + 1, (24, 20, 30))
    for x, y, k in ((7, 7, 0.7), (8, 7, 0.5), (7, 9, 0.5), (8, 9, 0.7)):
        put(x, y, _blend(STEEL, VIOLET, k))
    return im


def vault_textures():
    """倉庫の材質（それぞれ 16×16、既定の UV で貼る）: 黒鋼（継ぎ目・鋲・研磨の筋）、金（刻みの線）、
    ガラス（映り込み）、個数の台（暗いガラスに走査線）、蓋のレンズ（紫の光）。"""
    import surface
    metal = surface.panels(STEEL, 'vault-metal', size=8, rivets=True)
    gold = surface.metal(GOLD, 'vault-gold', grain=0.06, streak=None, scuffs=4, edge=0.12)
    glass = surface.glass((180, 190, 230), 'vault-glass', alpha=22, streaks=1)
    plate = surface.metal((16, 14, 22), 'vault-plate', grain=0.05, streak=None, scuffs=0, edge=0)
    pp = plate.load()
    for y in range(0, 16, 2):
        for x in range(16):
            c = pp[x, y]
            pp[x, y] = tuple(min(255, v + 6) for v in c[:3]) + (255,)
    lens = surface.glow(VIOLET, 'vault-lens')
    return {'metal': metal, 'gold': gold, 'glass': glass, 'plate': plate, 'lens': lens}


def beacon_textures():
    """航路標識の材質: 黒鋼（縦の研磨の筋・継ぎ目・鋲）、金（研磨の筋）、光る紫の灯。"""
    import surface
    metal = surface.panels(STEEL, 'beacon-metal', size=8, rivets=True)
    gold = surface.metal(GOLD, 'beacon-gold', grain=0.06, streak=None, scuffs=4, edge=0.12)
    glow = surface.glow(VIOLET, 'beacon-glow')
    return {'metal': metal, 'gold': gold, 'glow': glow}


# ---------------------------------------------------------------- モデル

ALL = ('north', 'south', 'east', 'west', 'up', 'down')


def _el(f, t, key, glow=False):
    """要素1つ（全部の面。UV は既定のまま＝ブロックの座標から決まり、どの面も1ドット＝1/16ブロック）。"""
    e = {'from': list(f), 'to': list(t), 'faces': {n: {'texture': key} for n in ALL}}
    if glow:
        e['neoforge_data'] = {'block_light': 15, 'sky_light': 15}
    return e


def vault_models(write, assets):
    """倉庫（上から見て点対称。4面とも同じ）。低い台（金の台座 0〜1、黒鋼の台 1〜4、4面に個数の台）の上に、
    立方体のガラスの箱（x, z 2〜14、y 4〜16）が乗る。箱の12本の辺が太さ2ドットの枠（下と上の辺は金の継ぎ輪、
    縦の辺は黒鋼の柱、上の四隅は光る発生器）で、ガラスは面から1ドット内側。外側には余白を残す。
    ガラスは別のモデル（半透明）。"""
    tex = {k: f'{MODID}:block/horizon_vault_{k}' for k in ('metal', 'gold', 'glass', 'plate', 'lens')}
    # 座標と厚みはすべて整数のドット（貼る絵のドット数と長さをそろえる。1ドットに満たない帯を作らない）
    solid = [
        # 低い台: 金の台座と黒鋼の台（1〜4）、4面に一段出た個数の台
        _el((0, 0, 0), (16, 1, 16), '#gold'),
        _el((1, 1, 1), (15, 4, 15), '#metal'),
        _el((3, 1, 0), (13, 3, 1), '#plate'), _el((3, 1, 15), (13, 3, 16), '#plate'),
        _el((0, 1, 3), (1, 3, 13), '#plate'), _el((15, 1, 3), (16, 3, 13), '#plate'),
        # 室の床の、場を作る光る板
        _el((5, 4, 5), (11, 5, 11), '#lens', glow=True),
        # 台に乗る立方体のガラスの箱（2〜14、4〜16）の12本の辺の枠（太さ2ドット）。下の辺は金の継ぎ輪
        _el((2, 4, 2), (14, 6, 4), '#gold'), _el((2, 4, 12), (14, 6, 14), '#gold'),
        _el((2, 4, 4), (4, 6, 12), '#gold'), _el((12, 4, 4), (14, 6, 12), '#gold'),
        # 上の辺も金の継ぎ輪（四隅は光る発生器）
        _el((4, 14, 2), (12, 16, 4), '#gold'), _el((4, 14, 12), (12, 16, 14), '#gold'),
        _el((2, 14, 4), (4, 16, 12), '#gold'), _el((12, 14, 4), (14, 16, 12), '#gold'),
    ]
    # 縦の辺は黒鋼の柱（2×2ドット）。上の四隅の光る発生器（2×2×2）
    for x0, z0 in ((2, 2), (12, 2), (2, 12), (12, 12)):
        solid.append(_el((x0, 6, z0), (x0 + 2, 14, z0 + 2), '#metal'))
        solid.append(_el((x0, 14, z0), (x0 + 2, 16, z0 + 2), '#lens', glow=True))
    # ごくうすいガラス（厚さ1ドット）。箱の面から1ドット内側（角で重ならないよう、北と南は x 4〜12）
    glass = [
        _el((4, 6, 3), (12, 14, 4), '#glass'),
        _el((4, 6, 12), (12, 14, 13), '#glass'),
        _el((3, 6, 4), (4, 14, 12), '#glass'),
        _el((12, 6, 4), (13, 14, 12), '#glass'),
        _el((4, 14, 4), (12, 15, 12), '#glass'),                       # 天面のガラス（上の枠の1ドット下）
    ]
    textures = {**tex, 'particle': tex['metal']}
    write(assets / 'models' / 'block' / 'horizon_vault.json', {
        'parent': 'minecraft:block/block', 'textures': textures, 'render_type': 'minecraft:cutout', 'elements': solid})
    write(assets / 'models' / 'block' / 'horizon_vault_glass.json', {
        'parent': 'minecraft:block/block', 'textures': textures, 'render_type': 'minecraft:translucent', 'elements': glass})
    rot = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
    multipart = []
    for facing, y in rot.items():
        for model in ('horizon_vault', 'horizon_vault_glass'):
            apply = {'model': f'{MODID}:block/{model}'}
            if y:
                apply['y'] = y
            multipart.append({'when': {'facing': facing}, 'apply': apply})
    write(assets / 'blockstates' / 'horizon_vault.json', {'multipart': multipart})
    write(assets / 'models' / 'item' / 'horizon_vault.json', {'parent': f'{MODID}:block/horizon_vault'})


def beacon_models(write, assets):
    """航路標識: 金の台（0〜1）と黒鋼の台（1〜3）、黒鋼の柱（3〜11）、柱から4本の金の腕で支えて浮かぶ光る輪（7〜8）、
    金の冠（11〜12）、てっぺんの光る紫の灯（12〜15）。"""
    tex = {k: f'{MODID}:block/wormhole_drone_beacon_{k}' for k in ('metal', 'gold', 'glow')}
    els = [
        _el((3, 0, 3), (13, 1, 13), '#gold'),
        _el((4, 1, 4), (12, 3, 12), '#metal'),
        _el((6, 3, 6), (10, 11, 10), '#metal'),
        _el((2, 7, 2), (14, 8, 3), '#glow', glow=True), _el((2, 7, 13), (14, 8, 14), '#glow', glow=True),
        _el((2, 7, 3), (3, 8, 13), '#glow', glow=True), _el((13, 7, 3), (14, 8, 13), '#glow', glow=True),
        _el((3, 7, 7), (6, 8, 9), '#gold'), _el((10, 7, 7), (13, 8, 9), '#gold'),
        _el((7, 7, 3), (9, 8, 6), '#gold'), _el((7, 7, 10), (9, 8, 13), '#gold'),
        _el((5, 11, 5), (11, 12, 11), '#gold'),
        _el((7, 12, 7), (9, 15, 9), '#glow', glow=True),
    ]
    write(assets / 'models' / 'block' / 'wormhole_drone_beacon.json', {
        'parent': 'minecraft:block/block', 'textures': {**tex, 'particle': tex['metal']}, 'render_type': 'minecraft:cutout', 'elements': els})
    write(assets / 'blockstates' / 'wormhole_drone_beacon.json', {'variants': {'': {'model': f'{MODID}:block/wormhole_drone_beacon'}}})
    write(assets / 'models' / 'item' / 'wormhole_drone_beacon.json', {'parent': f'{MODID}:block/wormhole_drone_beacon'})


def models(ctx):
    """絵とモデル（gen_data の一般の生成のあとに書く。内張りの一般の部品の絵も上書きする）。"""
    import art16
    from PIL import Image
    write = ctx['write_json']
    assets = Path(ctx['ASSETS'])
    tb = assets / 'textures' / 'block'
    hb = Path(ctx['RES']) / 'resourcepacks' / 'singulo_hd' / 'assets' / MODID / 'textures' / 'block'
    images = {'collapsar_lining': lining_texture()}
    images.update({f'horizon_vault_{k}': v for k, v in vault_textures().items()})
    images.update({f'wormhole_drone_beacon_{k}': v for k, v in beacon_textures().items()})
    for name, im in images.items():
        art16.save(im, tb / f'{name}.png')
    # 組み込みの HD パックの内張り（一般の部品の絵が残らないように、同じ絵を2倍に）
    art16.save(images['collapsar_lining'].resize((32, 32), Image.NEAREST), hb / 'collapsar_lining.png')
    vault_models(write, assets)
    beacon_models(write, assets)
