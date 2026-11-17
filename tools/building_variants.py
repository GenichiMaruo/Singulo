# -*- coding: utf-8 -*-
"""0.20.0: 建材の階段・ハーフブロック・塀。

元の建材と同じ絵・硬さ・音を使う（Java の SinguloBlocks.registerVariants が元のブロックの性質を写す）。
作業台では元の建材をバニラと同じ形に並べ、石切台では元の建材1個から作る（設計図は元の建材を作るときだけ要る）。
新しく生まれる研究所・遺構では、白い建材の代わりに置いていたバニラのクォーツの階段・ハーフブロックをこれに替える
（lab.py・ruins.py）。レシピは一覧の番号がずれないよう recipes.py の最後に足す。
"""

MODID = 'singulo'

# (元の建材の名前, 元の ID, 変種の ID の頭, 英語名の頭)。タイルはバニラと同じく単数にする（deepslate_tiles → deepslate_tile_stairs）
BASES = (
    ('ホワイトパネル', 'white_panel', 'white_panel', 'White Panel'),
    ('黒色強化パネル', 'black_reinforced_panel', 'black_reinforced_panel', 'Black Reinforced Panel'),
    ('遺構パネル', 'ruin_panel', 'ruin_panel', 'Ruin Panel'),
    ('ひび割れた遺構パネル', 'cracked_ruin_panel', 'cracked_ruin_panel', 'Cracked Ruin Panel'),
    ('苔むした遺構パネル', 'mossy_ruin_panel', 'mossy_ruin_panel', 'Mossy Ruin Panel'),
    ('遺構のタイルパネル', 'tiled_ruin_panel', 'tiled_ruin_panel', 'Tiled Ruin Panel'),
    ('無垢の遺構パネル', 'pristine_ruin_panel', 'pristine_ruin_panel', 'Pristine Ruin Panel'),
    ('無垢の遺構タイル', 'pristine_ruin_tiles', 'pristine_ruin_tile', 'Pristine Ruin Tile'),
    ('研究所の白いパネル', 'lab_panel', 'lab_panel', 'Lab Panel'),
    ('研究所の床タイル', 'lab_tiles', 'lab_tile', 'Lab Floor Tile'),
)

# (形, 日本語, 英語, 作業台の形, 作業台で使う数, できる数)
SHAPES = (
    ('stairs', '階段', 'Stairs', ('B  ', 'BB ', 'BBB'), 6, 4),
    ('slab', 'ハーフブロック', 'Slab', ('BBB',), 3, 6),
    ('wall', '塀', 'Wall', ('BBB', 'BBB'), 6, 6),
)
# 透けるガラスは階段だけ（0.20.0 で足した。レシピは一覧の番号がずれないよう recipes.py の最後で glass_recipes を呼ぶ）
GLASS_BASES = (
    ('ホワイトガラスパネル', 'white_glass_panel', 'white_glass_panel', 'White Glass Panel'),
    ('黒色強化ガラス', 'black_reinforced_glass', 'black_reinforced_glass', 'Black Reinforced Glass'),
    # アンドロイドだけが通れるガラス（無地は、となりの同じガラスとつながって見える。Java の ConnectedGlassModel）
    ('アンドロイド透過ガラス', 'android_gate_glass', 'android_gate_glass', 'Android Gate Glass'),
    ('模様入りアンドロイド透過ガラス', 'android_gate_glass_patterned', 'android_gate_glass_patterned', 'Patterned Android Gate Glass'),
    # アンドロイドだけが通れないガラス（無地は、となりの同じガラスとつながって見える）
    ('アンドロイド遮断ガラス', 'android_barrier_glass', 'android_barrier_glass', 'Android Barrier Glass'),
    ('模様入りアンドロイド遮断ガラス', 'android_barrier_glass_patterned', 'android_barrier_glass_patterned', 'Patterned Android Barrier Glass'),
)
GLASS_SHAPES = tuple(s for s in SHAPES if s[0] == 'stairs')
# 0.21.0 ガラスのハーフブロック（模様のないガラス4種。レシピは recipes.py の最後で足す）
GLASS_SLAB_BASES = tuple(b for b in GLASS_BASES
                         if b[1] in ('white_glass_panel', 'black_reinforced_glass', 'android_gate_glass', 'android_barrier_glass'))
GLASS_SLAB_SHAPES = tuple(s for s in SHAPES if s[0] == 'slab')
# 石切台で元の建材1個からできる数
STONECUT = {'stairs': 1, 'slab': 2, 'wall': 1}

# 変種の ID → (元の ID, 形)
VARIANTS = {f'{prefix}_{shape}': (base_id, shape) for _, base_id, prefix, _ in BASES for shape, *_ in SHAPES}
VARIANTS.update({f'{prefix}_{shape}': (base_id, shape) for _, base_id, prefix, _ in GLASS_BASES for shape, *_ in GLASS_SHAPES})
VARIANTS.update({f'{prefix}_{shape}': (base_id, shape) for _, base_id, prefix, _ in GLASS_SLAB_BASES for shape, *_ in GLASS_SLAB_SHAPES})
# 半透明で描く変種（ガラス）
GLASS_VARIANTS = {v for v, (base, _) in VARIANTS.items() if base in {b for _, b, _, _ in GLASS_BASES}}
# 遺構の建材（ニュートリノの探知で遺構と見なす）
RUIN_VARIANTS = {v for v, (base, _) in VARIANTS.items() if 'ruin' in base}
# 鉄以上のツルハシで掘る（元の建材と同じ）
IRON_VARIANTS = {v for v, (base, _) in VARIANTS.items() if base.startswith(('pristine_', 'lab_', 'black_', 'white_', 'android_'))}

IDS = {}
DESC = {}
for _ja, _base, _prefix, _en, _shapes in ([b + (SHAPES,) for b in BASES] + [b + (GLASS_SHAPES,) for b in GLASS_BASES]
                                         + [b + (GLASS_SLAB_SHAPES,) for b in GLASS_SLAB_BASES]):
    for _shape, _sja, _sen, *_ in _shapes:
        _name = f'{_ja}の{_sja}'
        IDS[_name] = (f'{_prefix}_{_shape}', f'{_en} {_sen}', 'variant')
        DESC[_name] = {
            'stairs': (f'{_ja}を階段の形にしたもの。', f'Stairs made from {_en}.'),
            'slab': (f'{_ja}を半分の高さにしたもの。2つ重ねると1ブロックになる。', f'A half-height {_en}. Two make a full block.'),
            'wall': (f'{_ja}の塀。柵のように隣とつながる。', f'A {_en} wall that connects like a fence.'),
        }[_shape]


def recipes(R, shaped, stage_of, bases=BASES, shapes=SHAPES):
    """作業台のレシピ（元の建材をバニラと同じ形に並べる）。stage_of(元の名前) で元の建材の段階を得る。"""
    for ja, _base, _prefix, _en in bases:
        for shape, sja, _sen, rows, count, out in shapes:
            name = f'{ja}の{sja}'
            R(name, stage_of(ja), '作業台', {ja: count}, out=out, cat='part')
            shaped[name] = (rows, {'B': ja})


def stonecutting(write_json, data):
    """石切台: 元の建材1個から、階段1・ハーフブロック2・塀1。"""
    for variant, (base, shape) in VARIANTS.items():
        write_json(data / 'recipe' / 'stonecutting' / f'{variant}.json', {
            'type': 'minecraft:stonecutting',
            'ingredient': {'item': f'{MODID}:{base}'},
            'result': {'id': f'{MODID}:{variant}', 'count': STONECUT[shape]},
        })


# 階段の上の段に入っている 1/8 の箱（東向き・下付きのバニラの形。(x, z) は 0 が西・北、1 が東・南）。下の段は4つとも入る
STAIR_TOPS = {
    'stairs': {(1, 0), (1, 1)},
    'inner_stairs': {(1, 0), (1, 1), (0, 1)},
    'outer_stairs': {(1, 1)},
}
# 面の向き → (隣の箱へのずれ, 面の名前)
_FACES = {'down': (0, -1, 0), 'up': (0, 1, 0), 'north': (0, 0, -1), 'south': (0, 0, 1), 'west': (-1, 0, 0), 'east': (1, 0, 0)}


def _face_uv(name, f, t):
    """面の位置どおりの UV（Minecraft の既定と同じ）。"""
    return {'down': [f[0], 16 - t[2], t[0], 16 - f[2]], 'up': [f[0], f[2], t[0], t[2]],
            'north': [16 - t[0], 16 - t[1], 16 - f[0], 16 - f[1]], 'south': [f[0], 16 - t[1], t[0], 16 - f[1]],
            'west': [f[2], 16 - t[1], t[2], 16 - f[1]], 'east': [16 - t[2], 16 - t[1], 16 - f[2], 16 - f[1]]}[name]


# ガラスの枠の描き方（元のガラスの絵と同じ色。tools/blocks_extra3.py の white_glass_panel・black_reinforced_glass）
GLASS_STYLE = {
    'white_glass_panel': {'fill': (236, 244, 250, 72), 'edge': (196, 202, 210, 255), 'light': (254, 254, 255, 230),
                          'dark': (218, 222, 228, 230), 'dot': (176, 184, 194, 255)},
    'black_reinforced_glass': {'fill': (22, 24, 32, 128), 'edge': (12, 12, 16, 255), 'light': (46, 48, 58, 255),
                               'dark': (46, 48, 58, 255), 'dot': (120, 124, 136, 255)},
    # アンドロイド用のガラス: 中身（と模様）は元の絵のまま、縁は元の1ドットの縁（tools/androids.py）
    'android_gate_glass': {'android': 'android_gate_glass', 'patterned': False},
    'android_gate_glass_patterned': {'android': 'android_gate_glass', 'patterned': True},
    'android_barrier_glass': {'android': 'android_barrier_glass', 'patterned': False},
    'android_barrier_glass_patterned': {'android': 'android_barrier_glass', 'patterned': True},
}


def android_face_texture(mask, style):
    """アンドロイド用のガラス: 元の中身（模様入りは模様も）を見える所だけ残し、その形の外周に元の1ドットの縁。"""
    from PIL import Image
    import androids
    iid = style['android']
    frame, glow, fill = androids.GLASSES[iid]
    base = androids.glass_pattern(iid, frame, glow, fill, Image) if style['patterned'] else androids.glass_fill(fill, Image)
    src = base.load()
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = im.load()
    for (u, v) in mask:
        edge = any((u + du, v + dv) not in mask for du, dv in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        px[u, v] = androids.edge_color(frame, glow, u + v) if edge else src[u, v]
    return im


def glass_face_texture(mask, style):
    """見える面（mask: UV の画素の集まり）の形に沿って、元のガラスと同じ枠を描く。外の画素は透明（見えない）。"""
    from PIL import Image
    if 'android' in style:
        return android_face_texture(mask, style)
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = im.load()

    def inside(u, v):
        return (u, v) in mask

    edge = {(u, v) for (u, v) in mask if not all(inside(u + du, v + dv) for du, dv in ((1, 0), (-1, 0), (0, 1), (0, -1)))}
    for (u, v) in mask:
        if (u, v) in edge:
            px[u, v] = style['edge']
            continue
        px[u, v] = style['fill']
        # 枠の内側の線: 外が上か左なら明るく、下か右なら暗く（元の絵の光の当たり方）
        if (u - 1, v) in edge or (u, v - 1) in edge:
            px[u, v] = style['light']
        elif (u + 1, v) in edge or (u, v + 1) in edge:
            px[u, v] = style['dark']
    # 角の鋲: 出っぱった角から内へ2画素の所
    for (u, v) in mask:
        for du in (1, -1):
            for dv in (1, -1):
                if (inside(u - du, v) and not inside(u - 2 * du, v) and inside(u, v - dv) and not inside(u, v - 2 * dv)
                        and inside(u - du, v - dv) and not inside(u - 2 * du, v - 2 * dv)):
                    px[u, v] = style['dot']
    return im


def glass_join_textures(textures):
    """ガラスの階段に接するガラスの面のうち、階段に隠れずに見える所の枠の絵（Java の GlassJoinModel が貼る）。
    面を UV の4つの 1/4 に分け、見える 1/4 の組（bits: 1 左上・2 右上・4 左下・8 右下）ごとに <元のガラス>_join_<bits>.png。"""
    quarters = {1: (0, 0), 2: (8, 0), 4: (0, 8), 8: (8, 8)}
    for base, style in GLASS_STYLE.items():
        for bits in range(1, 16):
            mask = {(u0 + du, v0 + dv) for bit, (u0, v0) in quarters.items() if bits & bit for du in range(8) for dv in range(8)}
            glass_face_texture(mask, style).save(textures / f'{base}_join_{bits}.png')


def glass_stairs_model(tex, tops, name=None, base=None, textures=None, cells=None):
    """透けるガラスの階段: 形を 1/8 の箱に分け、箱どうしが接する内側の面は描かない。

    バニラの階段（下の板と上の段の2つの箱）のままだと、下の板の天面のうち上の段に隠れた所と、上の段の底面がガラス越しに見え、
    ブロックの途中に枠の線が出る。見える面ごとに、その形に沿って枠を描いた絵（textures の下に name_<向き><位置>.png）を作って貼り、
    踏み板・蹴上げ・側面の L 字のどれにも枠が回るようにする。絵は形と一緒に回す（状態の uvlock を使わない）。
    cells を渡すと、その 1/8 の箱の集まり（ハーフブロック）で作る。
    """
    if cells is None:
        cells = {(x, 0, z) for x in (0, 1) for z in (0, 1)} | {(x, 1, z) for x, z in tops}
    elements = []
    planes = {}                    # (向き, 面の位置) → UV の画素
    for (x, y, z) in sorted(cells):
        f, t = [x * 8, y * 8, z * 8], [x * 8 + 8, y * 8 + 8, z * 8 + 8]
        faces = {}
        for fname, (dx, dy, dz) in _FACES.items():
            n = (x + dx, y + dy, z + dz)
            if n in cells:
                continue                                   # 内側の面（となりの箱に隠れる）
            axis = 0 if dx else 1 if dy else 2
            at = (t if (dx + dy + dz) > 0 else f)[axis]
            key = f'{fname}{at}'
            uv = _face_uv(fname, f, t)
            planes.setdefault(key, set()).update((u, v) for u in range(uv[0], uv[2]) for v in range(uv[1], uv[3]))
            face = {'texture': f'#{key}', 'uv': uv}
            if not all(0 <= c <= 1 for c in n):
                face['cullface'] = fname                   # ブロックの外周の面
            faces[fname] = face
        elements.append({'from': f, 'to': t, 'faces': faces})
    tex_map = {'particle': tex}
    for key, mask in planes.items():
        tex_map[key] = f'{MODID}:block/{name}_{key}'
        if textures is not None:
            glass_face_texture(mask, GLASS_STYLE[base]).save(textures / f'{name}_{key}.png')
    return {'parent': 'minecraft:block/block', 'render_type': 'minecraft:translucent',
            'textures': tex_map, 'elements': elements,
            'display': {'gui': {'rotation': [30, 135, 0], 'translation': [0, 0, 0], 'scale': [0.625, 0.625, 0.625]},
                        'head': {'rotation': [0, -90, 0], 'translation': [0, 0, 0], 'scale': [1, 1, 1]},
                        'thirdperson_lefthand': {'rotation': [75, -135, 0], 'translation': [0, 2.5, 0],
                                                 'scale': [0.375, 0.375, 0.375]}}}


def models(write_json, assets):
    """ブロックの状態・モデル・アイテムのモデル（バニラの階段・ハーフブロック・塀の形に、元の建材の絵を貼る）。"""
    bs, mb, mi = assets / 'blockstates', assets / 'models' / 'block', assets / 'models' / 'item'
    (assets / 'textures' / 'block').mkdir(parents=True, exist_ok=True)
    glass_join_textures(assets / 'textures' / 'block')     # ガラスの階段に接するガラスの面
    for variant, (base, shape) in VARIANTS.items():
        tex = f'{MODID}:block/{base}'
        m = f'{MODID}:block/{variant}'
        if shape == 'stairs':
            for suffix, parent in (('', 'stairs'), ('_inner', 'inner_stairs'), ('_outer', 'outer_stairs')):
                if variant in GLASS_VARIANTS:
                    tb = assets / 'textures' / 'block'
                    tb.mkdir(parents=True, exist_ok=True)
                    model = glass_stairs_model(tex, STAIR_TOPS[parent], f'{variant}{suffix}', base, tb)
                else:
                    model = {'parent': f'minecraft:block/{parent}', 'textures': {'bottom': tex, 'top': tex, 'side': tex}}
                write_json(mb / f'{variant}{suffix}.json', model)
            # ガラスは面ごとの枠の絵を形と一緒に回す（uvlock だと絵が世界に固定され、枠が形からずれる）
            write_json(bs / f'{variant}.json', {'variants': stairs_variants(m, uvlock=variant not in GLASS_VARIANTS)})
            write_json(mi / f'{variant}.json', {'parent': m})
        elif shape == 'slab' and variant in GLASS_VARIANTS:
            # ガラスのハーフブロック: 階段と同じく、見える面の形に沿った枠の絵。2つ重ねると元のガラス
            tb = assets / 'textures' / 'block'
            for suffix, y in (('', 0), ('_top', 1)):
                cells = {(x, y, z) for x in (0, 1) for z in (0, 1)}
                write_json(mb / f'{variant}{suffix}.json', glass_stairs_model(tex, (), f'{variant}{suffix}', base, tb, cells))
            write_json(bs / f'{variant}.json', {'variants': {
                'type=bottom': {'model': m}, 'type=top': {'model': f'{m}_top'}, 'type=double': {'model': f'{MODID}:block/{base}'}}})
            write_json(mi / f'{variant}.json', {'parent': m})
        elif shape == 'slab':
            write_json(mb / f'{variant}.json', {'parent': 'minecraft:block/slab', 'textures': {'bottom': tex, 'top': tex, 'side': tex}})
            write_json(mb / f'{variant}_top.json', {'parent': 'minecraft:block/slab_top',
                                                   'textures': {'bottom': tex, 'top': tex, 'side': tex}})
            write_json(bs / f'{variant}.json', {'variants': {
                'type=bottom': {'model': m}, 'type=top': {'model': f'{m}_top'}, 'type=double': {'model': f'{MODID}:block/{base}'}}})
            write_json(mi / f'{variant}.json', {'parent': m})
        else:
            for suffix, parent in (('_post', 'template_wall_post'), ('_side', 'template_wall_side'),
                                   ('_side_tall', 'template_wall_side_tall'), ('_inventory', 'wall_inventory')):
                write_json(mb / f'{variant}{suffix}.json', {'parent': f'minecraft:block/{parent}', 'textures': {'wall': tex}})
            write_json(bs / f'{variant}.json', {'multipart': wall_multipart(m)})
            write_json(mi / f'{variant}.json', {'parent': f'{m}_inventory'})


def stairs_variants(m, uvlock=True):
    """バニラの階段と同じ向きと形の割り当て。"""
    y = {'east': 0, 'south': 90, 'west': 180, 'north': 270}
    out = {}
    for facing, base_y in y.items():
        for half in ('bottom', 'top'):
            for shape in ('straight', 'inner_left', 'inner_right', 'outer_left', 'outer_right'):
                model = m + ('_inner' if shape.startswith('inner') else '_outer' if shape.startswith('outer') else '')
                rot = base_y
                if half == 'bottom' and shape in ('inner_left', 'outer_left'):
                    rot -= 90
                if half == 'top' and shape in ('inner_right', 'outer_right'):
                    rot += 90
                entry = {'model': model}
                if half == 'top':
                    entry['x'] = 180
                rot %= 360
                if rot:
                    entry['y'] = rot
                if uvlock and (half == 'top' or rot):
                    entry['uvlock'] = True
                out[f'facing={facing},half={half},shape={shape}'] = entry
    return out


def wall_multipart(m):
    """バニラの塀と同じ、柱と4方向の低い・高い側面。"""
    parts = [{'when': {'up': 'true'}, 'apply': {'model': f'{m}_post'}}]
    for side, rot in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
        for height, suffix in (('low', '_side'), ('tall', '_side_tall')):
            apply = {'model': f'{m}{suffix}', 'uvlock': True}
            if rot:
                apply['y'] = rot
            parts.append({'when': {side: height}, 'apply': apply})
    return parts


def loot(write_json, data):
    """ハーフブロックは2つ重ねたものを壊すと2個（バニラと同じ）。階段・塀は1個。"""
    for variant, (_base, shape) in VARIANTS.items():
        entry = {'type': 'minecraft:item', 'name': f'{MODID}:{variant}'}
        if shape == 'slab':
            entry['functions'] = [{'function': 'minecraft:set_count', 'count': 2, 'add': False, 'conditions': [{
                'condition': 'minecraft:block_state_property', 'block': f'{MODID}:{variant}', 'properties': {'type': 'double'}}]},
                {'function': 'minecraft:explosion_decay'}]
        write_json(data / 'loot_table' / 'blocks' / f'{variant}.json', {
            'type': 'minecraft:block',
            'pools': [{'rolls': 1, 'entries': [entry]}
                      | ({} if shape == 'slab' else {'conditions': [{'condition': 'minecraft:survives_explosion'}]})],
        })


def tags(write_json, data):
    """バニラの階段・ハーフブロック・塀のタグ（ブロックとアイテム。他modや塀のつながりが見る）。"""
    for shape, tag in (('stairs', 'stairs'), ('slab', 'slabs'), ('wall', 'walls')):
        values = [f'{MODID}:{v}' for v, (_b, s) in VARIANTS.items() if s == shape]
        write_json(data.parent / 'minecraft' / 'tags' / 'block' / f'{tag}.json', {'replace': False, 'values': values})
        write_json(data.parent / 'minecraft' / 'tags' / 'item' / f'{tag}.json', {'replace': False, 'values': values})
