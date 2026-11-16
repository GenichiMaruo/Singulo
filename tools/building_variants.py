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
# 石切台で元の建材1個からできる数
STONECUT = {'stairs': 1, 'slab': 2, 'wall': 1}

# 変種の ID → (元の ID, 形)
VARIANTS = {f'{prefix}_{shape}': (base_id, shape) for _, base_id, prefix, _ in BASES for shape, *_ in SHAPES}
# 遺構の建材（ニュートリノの探知で遺構と見なす）
RUIN_VARIANTS = {v for v, (base, _) in VARIANTS.items() if 'ruin' in base}
# 鉄以上のツルハシで掘る（元の建材と同じ）
IRON_VARIANTS = {v for v, (base, _) in VARIANTS.items() if base.startswith(('pristine_', 'lab_', 'black_', 'white_'))}

IDS = {}
DESC = {}
for _ja, _base, _prefix, _en in BASES:
    for _shape, _sja, _sen, *_ in SHAPES:
        _name = f'{_ja}の{_sja}'
        IDS[_name] = (f'{_prefix}_{_shape}', f'{_en} {_sen}', 'variant')
        DESC[_name] = {
            'stairs': (f'{_ja}を階段の形にしたもの。', f'Stairs made from {_en}.'),
            'slab': (f'{_ja}を半分の高さにしたもの。2つ重ねると1ブロックになる。', f'A half-height {_en}. Two make a full block.'),
            'wall': (f'{_ja}の塀。柵のように隣とつながる。', f'A {_en} wall that connects like a fence.'),
        }[_shape]


def recipes(R, shaped, stage_of):
    """作業台のレシピ（元の建材をバニラと同じ形に並べる）。stage_of(元の名前) で元の建材の段階を得る。"""
    for ja, _base, _prefix, _en in BASES:
        for shape, sja, _sen, rows, count, out in SHAPES:
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


def models(write_json, assets):
    """ブロックの状態・モデル・アイテムのモデル（バニラの階段・ハーフブロック・塀の形に、元の建材の絵を貼る）。"""
    bs, mb, mi = assets / 'blockstates', assets / 'models' / 'block', assets / 'models' / 'item'
    for variant, (base, shape) in VARIANTS.items():
        tex = f'{MODID}:block/{base}'
        m = f'{MODID}:block/{variant}'
        if shape == 'stairs':
            for suffix, parent in (('', 'stairs'), ('_inner', 'inner_stairs'), ('_outer', 'outer_stairs')):
                write_json(mb / f'{variant}{suffix}.json', {'parent': f'minecraft:block/{parent}',
                                                           'textures': {'bottom': tex, 'top': tex, 'side': tex}})
            write_json(bs / f'{variant}.json', {'variants': stairs_variants(m)})
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


def stairs_variants(m):
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
                if half == 'top' or rot:
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
