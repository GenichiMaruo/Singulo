# -*- coding: utf-8 -*-
"""recipes.py / config_spec.py から mod のデータとコードを生成する。

使い方（リポジトリのルートで）:
    uv run --no-project --python 3.12 --with pillow tools/gen_data.py

生成の前に recipes.py を検証し（段階の逆転・未定義の製作場所・循環・設定の既定値との一致・
作業台と精密組立台の枠）、問題があれば何も書き出さずに止まる。

出力:
    src/generated/resources/  レシピ・タグ・翻訳・モデル・仮テクスチャ・質量値・熱源
    src/generated/java/       アイテム登録表（GeneratedContent）、設定（SinguloConfig）
出力先は毎回消して作り直すので、手で編集しない。
"""
import json
import math
import re
import shutil
import sys
from fractions import Fraction as F
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / 'tools'))

from recipes import (RECIPES, ORDER, EXPLORE, REPAIR, RESTORED, CARDS, STAGE_NAMES, RUIN_REGEN_DAYS,  # noqa: E402
                     REPAIR_WEAR, MAX_REPAIRS, ASSEMBLER_MAX_KINDS, WORKBENCH_MAX_ITEMS)
import config_spec  # noqa: E402
import ruins  # noqa: E402
from ids import (MOD, CUSTOM_STATIONS, OUTPUT_ALIASES, VANILLA, VANILLA_FLUIDS, MASS_INPUTS, STEEL_TAG,  # noqa: E402
                 STATIONS, STAGE_COLORS, SPENT, ACCEPTS_SPENT, MIN_SIZE)

MODID = 'singulo'
PKG = 'io.github.genichimaruo.singulo'
RES = ROOT / 'src' / 'generated' / 'resources'
JAVA = ROOT / 'src' / 'generated' / 'java' / Path(*PKG.split('.')) / 'generated'
ASSETS = RES / 'assets' / MODID
DATA = RES / 'data' / MODID

warnings = []


def warn(msg):
    warnings.append(msg)


def write_json(path: Path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def mid(name):
    return f'{MODID}:{MOD[name][0]}'


# ---------------------------------------------------------------- 分類

FLUIDS = {n for n, v in MOD.items() if v[2] == 'fluid'}
STRUCTURES = {n for n, v in MOD.items() if v[2] == 'structure'}
BLOCK_KINDS = ('block', 'machine', 'part_block', 'part_glass', 'ruin_block', 'ruin_glass')
CABLES = {'copper_wire': (200, 120, 70), 'superconducting_cable': (120, 200, 240), 'topological_wire': (190, 170, 255),
          'horizon_bus': (20, 20, 26)}
CATALYST_MINUTES = {'ミュオン触媒': 20, 'BE凝縮触媒': 30, '時間結晶触媒': 45, 'シンギュラリティ・コア': 60}


def stage_of(name):
    if name in RECIPES:
        return RECIPES[name]['stage']
    if name in EXPLORE:
        return {'地表観測拠点': 1, '研究棟': 2, '封鎖培養施設': 3, '最終実験施設': 5}[EXPLORE[name]['ruin']]
    if name in RESTORED:
        return stage_of(RESTORED[name])
    for cat, spent in SPENT.items():
        if name == spent:
            return stage_of(cat)
    return {'酸素': 1, '磁気単極子': 2}.get(name, 1)


def max_uses(name):
    """使用回数つきアイテムの最大使用回数（触媒はtick単位の寿命）。"""
    if name in CATALYST_MINUTES:
        return CATALYST_MINUTES[name] * 60 * 20
    if name in EXPLORE and EXPLORE[name]['kind'] == 'data':
        return EXPLORE[name]['uses']
    if name in RESTORED:
        return EXPLORE[RESTORED[name]]['uses']
    if name == '残響の欠片（型）':
        return 16  # config echo.templateUses
    return 0


def resolve(name):
    """材料名 → レシピJSONの材料表現。"""
    if name in VANILLA_FLUIDS:
        return ('fluid', VANILLA_FLUIDS[name])
    if name in FLUIDS:
        return ('fluid', mid(name))
    if name in MASS_INPUTS:
        return ('mass', MASS_INPUTS[name])
    if name == '鋼鉄インゴット':
        return ('tag', STEEL_TAG[1:])
    if name in OUTPUT_ALIASES:
        return ('item', OUTPUT_ALIASES[name])
    if name in VANILLA:
        v = VANILLA[name]
        return ('tag', v[1:]) if v.startswith('#') else ('item', v)
    if name in MOD:
        if name in STRUCTURES:
            raise KeyError(f'{name} は構造物で、材料にできない')
        return ('item', mid(name))
    raise KeyError(f'未定義の材料: {name}')


def ingredient_json(kind, value):
    return {'tag': value} if kind == 'tag' else {'item': value}


def item_inputs(inputs, recipe_name):
    """inputs → (items, fluids, mass)。分数は使用回数で消費する材料として扱う。"""
    items, fluids, mass = [], [], None
    for name, q in inputs.items():
        kind, value = resolve(name)
        if kind == 'fluid':
            fluids.append({'fluid': value, 'amount': int(q)})
        elif kind == 'mass':
            mass = {'amount': float(q), 'metal_only': value}
        else:
            if (recipe_name, name) in ACCEPTS_SPENT:
                kind, value = 'tag', f'{MODID}:{MOD[name][0]}s'
            entry = {'ingredient': ingredient_json(kind, value)}
            if isinstance(q, F) and q.denominator != 1:
                uses = q * max_uses(name)
                if uses.denominator != 1 or uses <= 0:
                    warn(f'{recipe_name}: {name} ×{q} が使用回数の整数にならない（最大 {max_uses(name)}）')
                entry['uses'] = int(uses)
            else:
                entry['count'] = int(q)
            items.append(entry)
    return items, fluids, mass


# ---------------------------------------------------------------- レシピ

def recipe_files():
    out = {}  # 相対パス → json
    skipped = []

    def add(path, obj):
        if path in out:
            warn(f'レシピのパスが重複: {path}')
        out[path] = obj

    for name in ORDER:
        r = RECIPES[name]
        station = r['station']
        if (name in STRUCTURES and name != '圧縮ブロックLv2（混成）') or station == 'マルチブロック組み立て':
            skipped.append(f'{name}（{station}）')
            continue
        if name in FLUIDS:
            result_kind, result_id = 'fluid', mid(name)
        elif name in OUTPUT_ALIASES:
            result_kind, result_id = 'item', OUTPUT_ALIASES[name]
        else:
            result_kind, result_id = 'item', mid(name)
        rid = result_id.split(':')[1]
        if name in OUTPUT_ALIASES:
            # 同じ成果物の別レシピ（リサイクル・ストレンジ物質）はパスを分ける
            rid += '_from_strange_matter' if 'ストレンジ' in name else '_recycled' if 'リサイクル' in name else ''
        if name == '圧縮ブロックLv2（混成）':
            # 3種類以上の元ブロックから作った Lv1（データ成分 singulo:mixed_source）なら8個で済む。通常のレシピより先に試す
            (src, q), = r['inputs'].items()
            add(f'{STATIONS[station]}/compressed_block_2_mixed', {
                'type': f'{MODID}:machine', 'station': STATIONS[station], 'stage': r['stage'],
                'time': max(1, round(r['t'] * 20)), 'energy': r['fe'], 'priority': 1,
                'ingredients': [{'ingredient': {'type': 'neoforge:components', 'items': mid(src),
                                                'components': {f'{MODID}:mixed_source': True}, 'strict': False},
                                 'count': int(q)}],
                'result': {'id': mid('圧縮ブロックLv2'), 'count': 1},
            })
            continue

        if station == '作業台':
            items, fluids, mass = item_inputs(r['inputs'], name)
            slots = sum(i.get('count', 1) for i in items)
            if fluids or mass or any('uses' in i for i in items):
                warn(f'{name}: 作業台レシピに液体・質量・使用回数の材料がある')
            if slots > 9:
                skipped.append(f'{name}（材料 {slots} 個で作業台に収まらない。組立台が必要）')
                continue
            if 'shape' in r:
                # 形のあるレシピ（recipes.SHAPED の並び）
                rows, key = r['shape']
                width = max(len(row) for row in rows)
                key_json = {}
                for ch, mat in key.items():
                    one, _, _ = item_inputs({mat: 1}, name)
                    key_json[ch] = one[0]['ingredient']
                add(f'crafting/{rid}', {
                    'type': 'minecraft:crafting_shaped',
                    'category': 'misc',
                    'pattern': [row.ljust(width) for row in rows],
                    'key': key_json,
                    'result': {'id': result_id, 'count': r['out']},
                })
                continue
            ingredients = []
            for i in items:
                ingredients += [i['ingredient']] * i['count']
            add(f'crafting/{rid}', {
                'type': 'minecraft:crafting_shapeless',
                'category': 'misc',
                'ingredients': ingredients,
                'result': {'id': result_id, 'count': r['out']},
            })
        elif station in ('溶鉱炉（バニラ）', 'かまど（バニラ）'):
            (src, q), = r['inputs'].items()
            kind, value = resolve(src)
            add(f'{"blasting" if "溶鉱炉" in station else "smelting"}/{rid}', {
                'type': 'minecraft:blasting' if '溶鉱炉' in station else 'minecraft:smelting',
                'category': 'misc',
                'ingredient': ingredient_json(kind, value),
                'result': {'id': result_id, 'count': r['out']},
                'experience': 0.1,
                'cookingtime': int(r['t'] * 20),
            })
        elif station in CUSTOM_STATIONS:
            skipped.append(f'{name}（{station} の専用の処理）')
            continue
        elif station in STATIONS:
            if not r['inputs']:
                skipped.append(f'{name}（{station} の運転で直接生まれる）') if 'Pリアクター' in station else None
                if 'Pリアクター' in station:
                    continue
            items, fluids, mass = item_inputs(r['inputs'], name)
            obj = {
                'type': f'{MODID}:machine',
                'station': STATIONS[station],
                'stage': r['stage'],
                'time': max(1, round(r['t'] * 20)),
                'energy': r['fe'],
                'ingredients': items,
            }
            if fluids:
                obj['fluid_ingredients'] = fluids
            if name in MIN_SIZE:
                obj['min_size'] = MIN_SIZE[name]
            if mass:
                obj['mass'] = mass
            if result_kind == 'fluid':
                obj['fluid_results'] = [{'id': result_id, 'amount': r['out']}]
            else:
                obj['result'] = {'id': result_id, 'count': r['out']}
            if name == '水素':  # 電解槽の副産物
                obj['fluid_results'].append({'id': mid('酸素'), 'amount': r['out'] // 2})
            add(f'{STATIONS[station]}/{rid}', obj)
        else:
            warn(f'{name}: 未知の製作場所 {station}')

    # 設計書の注記にある別ルート
    add('crafting/steel_plate_by_hand', {
        'type': 'minecraft:crafting_shapeless', 'category': 'misc',
        'ingredients': [{'tag': STEEL_TAG[1:]}] * 2,
        'result': {'id': mid('鋼板'), 'count': 1},
    })
    for name, src, t in (('鋼鉄インゴット', '鋼の素', 2.5), ('ホワイトセラミック複合材', '未焼成セラミック', 2.5)):
        add(f'kiln/{MOD[name][0]}', {
            'type': f'{MODID}:machine', 'station': 'kiln', 'stage': 1,
            'time': int(t * 20), 'energy': 40,
            'ingredients': [{'ingredient': {'item': mid(src)}, 'count': 1}],
            'result': {'id': mid(name), 'count': 1},
        })

    # 修復・復元
    for src, rp in REPAIR.items():
        station = STATIONS[rp['station']]
        target = src
        for restored, orig in RESTORED.items():
            if orig == src:
                target = restored
        items, fluids, mass = item_inputs(rp['inputs'], f'{src}の修復')
        obj = {
            'type': f'{MODID}:machine', 'station': station,
            'stage': stage_of(rp['station']) if rp['station'] in RECIPES else 1,
            'time': int(rp['t'] * 20), 'energy': rp['fe'],
            'ingredients': items,
            'restore': {'item': mid(src)},
            'result': {'id': mid(target), 'count': 1},
        }
        if fluids:
            obj['fluid_ingredients'] = fluids
        add(f'{station}/restore_{MOD[src][0]}', obj)

    for path, obj in out.items():
        write_json(DATA / 'recipe' / f'{path}.json', obj)
    return out, skipped


# ---------------------------------------------------------------- 翻訳

UI_LANG = {
    # key: (ja, en)
    'itemGroup.singulo': ('Singulo', 'Singulo'),
    'tooltip.singulo.stage': ('段階%s: %s', 'Stage %s: %s'),
    'tooltip.singulo.planned': ('動作は未実装。素材として使える', 'Not functional yet; usable as a material'),
    'tooltip.singulo.uses': ('使用回数 %s / %s', 'Uses %s / %s'),
    'tooltip.singulo.lifetime': ('寿命 %s / %s', 'Lifetime %s / %s'),
    'tooltip.singulo.repairs': ('修復 %s / %s 回', 'Repaired %s / %s times'),
    'tooltip.singulo.worn_out': ('使い切った。対応する装置で修復できる', 'Exhausted. Repair it in the matching machine'),
    'tooltip.singulo.beyond_repair': ('これ以上は修復できない', 'Beyond repair'),
    'tooltip.singulo.mass': ('質量値 %s', 'Mass %s'),
    'tooltip.singulo.mass_metal': ('質量値 %s（金属）', 'Mass %s (metal)'),
    'gui.singulo.energy': ('%s / %s FE', '%s / %s FE'),
    'gui.singulo.energy_rate': ('%s FE/t', '%s FE/t'),
    'gui.singulo.empty_tank': ('空', 'Empty'),
    'gui.singulo.fluid': ('%s: %s / %s mB', '%s: %s / %s mB'),
    'gui.singulo.mass_buffer': ('質量 %s / %s', 'Mass %s / %s'),
    'gui.singulo.mode.normal': ('通常加工', 'Normal processing'),
    'gui.singulo.mode.mass': ('圧縮: %s', 'Compress: %s'),
    'gui.singulo.mode.hint': ('クリックでモード切り替え', 'Click to change mode'),
    'gui.singulo.thermo.hot': ('高温側 %s K', 'Hot side %s K'),
    'gui.singulo.thermo.cold': ('低温側 %s K', 'Cold side %s K'),
    'gui.singulo.thermo.delta': ('温度差 %s K', 'Delta T %s K'),
    'gui.singulo.thermo.none': ('熱源か低温源が隣にない', 'No heat source or heat sink adjacent'),
    'gui.singulo.thermo.melting': ('低温源が溶けている　保てる温度差 %s K', 'Cold side melting: %s K difference'),
    'gui.singulo.status.idle': ('待機', 'Idle'),
    'gui.singulo.status.running': ('稼働', 'Running'),
    'gui.singulo.status.no_power': ('電力不足', 'Not enough power'),
    'gui.singulo.status.output_full': ('出力がいっぱい', 'Output full'),
    'gui.singulo.status.not_formed': ('未形成', 'Not formed'),
    'gui.singulo.structure.cryogenic_cooling_tower': ('高さ %s', 'Height %s'),
    'gui.singulo.structure.particle_accelerator': ('一辺 %s', 'Side %s'),
    'gui.singulo.thermo.coolant': ('液体窒素 %s mB', 'Liquid nitrogen %s mB'),
    'gui.singulo.muon_collector.rate': ('収集速度 %s%%', 'Collection %s%%'),
    'gui.singulo.anchor.status': ('半径 %s チャンク ・ %s ・ %s FE/t ・ 蓄電 %s FE',
                                  'Radius %s chunks · %s · %s FE/t · stored %s FE'),
    'gui.singulo.anchor.no_catalyst': ('触媒なし', 'No catalyst'),
    'gui.singulo.structure.degenerate_compactor': ('3×3×3', '3×3×3'),
    'gui.singulo.reactor.state.unformed': ('未形成', 'Not formed'),
    'gui.singulo.reactor.state.dormant': ('停止中', 'Dormant'),
    'gui.singulo.reactor.state.igniting': ('点火中 %s%%　あと %s 秒', 'Igniting %s%% · %s s'),
    'gui.singulo.reactor.state.running': ('稼働中', 'Running'),
    'gui.singulo.reactor.mass': ('炉心質量 %s', 'Core mass %s'),
    'gui.singulo.reactor.spin': ('スピン %s / 目標 %s', 'Spin %s / target %s'),
    'gui.singulo.reactor.eta': ('降着効率 %s%%', 'Accretion efficiency %s%%'),
    'gui.singulo.reactor.output': ('出力 %s FE/t', 'Output %s FE/t'),
    'gui.singulo.reactor.buffer': ('蓄電 %s GFE', 'Buffer %s GFE'),
    'gui.singulo.reactor.mode.power': ('発電モード', 'Power mode'),
    'gui.singulo.reactor.mode.catalyst': ('触媒モード', 'Catalyst mode'),
    'gui.singulo.reactor.mode.ergo': ('エルゴ抽出モード', 'Ergo extraction mode'),
    'gui.singulo.reactor.mode.standby': ('待機', 'Standby'),
    'gui.singulo.reactor.mode.danger': ('蒸発の危険域', 'Evaporation danger zone'),
    'gui.singulo.reactor.ignite': ('点火', 'Ignite'),
    'gui.singulo.reactor.spin_target': ('スピン目標', 'Spin target'),
    'gui.singulo.reactor.slot.seed': ('種', 'Seed'),
    'gui.singulo.reactor.slot.fuel': ('燃料', 'Fuel'),
    'gui.singulo.reactor.slot.collectors': ('抽出装置', 'Collectors'),
    'gui.singulo.reactor.burst': ('炉心が蒸発しきった。シンギュラリティ・コアと点火電力で再起動できる',
                                  'The core evaporated. Restart it with a Singularity Core and ignition power'),
    'gui.singulo.reactor.ignition_failed': ('点火失敗　電力が間に合わなかった', 'Ignition failed: not enough power in time'),
    'gui.singulo.structure.casimir_cavity': ('5×5×5', '5×5×5'),
    'gui.singulo.device.no_fuel': ('燃料がない', 'No fuel'),
    'gui.singulo.device.fuel': ('燃料 %s 個', 'Fuel: %s'),
    'gui.singulo.probe.no_target': ('飛べる遺構がない', 'No known ruins'),
    'gui.singulo.probe.targets': ('登録した遺構 %s か所', '%s ruins registered'),
    'gui.singulo.probe.return': ('次の帰還まで %s 秒', 'Next return in %s s'),
    'tooltip.singulo.mixed_source': ('混成: 8個で Lv2 になる', 'Mixed: 8 make a Lv2'),
    'gui.singulo.catalyst.slot': ('触媒', 'Catalyst'),
    'gui.singulo.catalyst.none': ('触媒を入れてください', 'Insert a catalyst'),
    'gui.singulo.catalyst.unusable': ('この触媒はティアが低すぎる', 'Catalyst tier too low'),
    'gui.singulo.catalyst.effect': ('速度×%s ・ 消費×%s', 'Speed ×%s · Wear ×%s'),
    'gui.singulo.catalyst.underpowered': ('電力不足: 触媒の減りが速い', 'Underpowered: catalyst wears faster'),
    'gui.singulo.device.radius_chunks': ('半径 %s チャンク', 'Radius %s chunks'),
    'gui.singulo.device.radius_blocks': ('半径 %s ブロック', 'Radius %s blocks'),
    'gui.singulo.device.usage': ('消費 %s FE/t', 'Uses %s FE/t'),
    'gui.singulo.device.output': ('出力 %s FE/t', 'Output %s FE/t'),
    'tooltip.singulo.energy': ('蓄電 %s / %s FE', 'Energy %s / %s FE'),
    'tooltip.singulo.gauntlet.mode': ('モード: %s', 'Mode: %s'),
    'tooltip.singulo.gauntlet.hint': ('右クリック長押しで操る　スニーク＋右クリックでモード切替', 'Hold use to manipulate · Sneak-use to switch mode'),
    'gauntlet.singulo.mode.levitate': ('浮遊', 'Levitate'),
    'gauntlet.singulo.mode.pull': ('牽引', 'Pull'),
    'gauntlet.singulo.no_target': ('対象がいない', 'No target'),
    'gauntlet.singulo.no_energy': ('電力がない', 'Out of energy'),
    'gauntlet.singulo.charged': ('%s FE 充電した', 'Charged %s FE'),
    'gauntlet.singulo.mode.repel': ('斥力', 'Repel'),
    'gauntlet.singulo.mode.crush': ('圧壊', 'Crush'),
    'gauntlet.singulo.no_target_manipulator': ('対象がいない', 'No target'),
    'gauntlet.singulo.no_exotic': ('エキゾチック物質がない', 'No exotic matter'),
    'tooltip.singulo.exotic_charge': ('エキゾチック物質の残り: %s 秒', 'Exotic charge: %s s'),
    'metric_drive.singulo.mode.off': ('停止', 'Off'),
    'metric_drive.singulo.mode.low_gravity': ('低重力', 'Low gravity'),
    'metric_drive.singulo.mode.zero_g': ('無重力', 'Zero-G'),
    'metric_drive.singulo.mode.high_gravity': ('高重力', 'High gravity'),
    'metric_drive.singulo.no_exotic': ('メトリック・ドライブ: エキゾチック物質がない', 'Metric Drive: no exotic matter'),
    'tooltip.singulo.metric_drive.hint': ('持っているだけで効く　右クリックでモード切替', 'Works from your inventory · Use to switch mode'),
    'gui.singulo.anchor.core_embedded': ('コア埋め込み済み', 'Core embedded'),
    'gui.singulo.anchor.confirm_embed': ('もう一度右クリックで埋め込む　取り出せなくなる', 'Use again to embed. It cannot be removed'),
    'gui.singulo.anchor.embedded': ('シンギュラリティ・コアを埋め込んだ', 'Singularity Core embedded'),
    'gui.singulo.anchor.already_embedded': ('すでにコアが埋め込まれている', 'A core is already embedded'),
    'gui.singulo.shield.basic': ('守り: 爆発・モブの荒らし', 'Guards: explosions, mob griefing'),
    'gui.singulo.shield.full': ('守り: 爆発・荒らし・敵の湧き・侵入', 'Guards: explosions, griefing, hostile spawns, intrusion'),
    'gui.singulo.device.exotic': ('エキゾチック物質 %s 個', 'Exotic matter: %s'),
    'gauntlet.singulo.area.single': ('範囲: 視線上の1体', 'Area: single target'),
    # マルチブロック・ホロ投影機・コマンド
    'multiblock.singulo.cooling_tower': ('極低温冷却塔', 'Cryogenic Cooling Tower'),
    'multiblock.singulo.particle_accelerator': ('粒子加速器', 'Particle Accelerator'),
    'multiblock.singulo.degenerate_compactor': ('縮退圧縮炉', 'Degenerate Compactor'),
    'multiblock.singulo.casimir_cavity': ('C空洞', 'C-Cavity'),
    'multiblock.singulo.degenerate_furnace': ('縮退熱炉', 'Degenerate Furnace'),
    'multiblock.singulo.penrose_reactor': ('Pリアクター', 'P-Reactor'),
    'multiblock.singulo.event_horizon_shield': ('イベントホライズン・シールド発生塔', 'Event Horizon Shield Tower'),
    'multiblock.singulo.tipler_cylinder': ('Tシリンダー', 'T-Cylinder'),
    'multiblock.singulo.wormhole_generator': ('ワームホール生成器', 'Wormhole Generator'),
    'holo.singulo.not_controller': ('マルチブロックのコントローラに使う', 'Use it on a multiblock controller'),
    'holo.singulo.header': ('%s　大きさ %s　必要なブロック:', '%s · size %s · blocks needed:'),
    'holo.singulo.line': ('  %s: %s 個　あと %s 個', '  %s: %s, %s missing'),
    'holo.singulo.blocking': ('じゃまなブロックが %s 個ある', '%s blocks in the way'),
    'holo.singulo.complete': ('形が完成している', 'The structure is complete'),
    'tooltip.singulo.holo_projector.hint': ('コントローラに右クリックで投影、もう一度で消す。スニーク＋右クリックで大きさ切替',
                                           'Right-click a controller to project; again to hide. Sneak to change size'),
    'command.singulo.build.unknown': ('知らないマルチブロック: %s', 'Unknown multiblock: %s'),
    'command.singulo.build.size': ('その大きさは使えない: %s', 'Invalid size: %s'),
    'command.singulo.build.done': ('%s を組み立てた　%s ブロック', 'Built %s with %s blocks'),
    'gui.singulo.handbook.title': ('Singulo ハンドブック', 'Singulo Handbook'),
    'gui.singulo.handbook.progress': ('進み具合', 'Progress'),
    'gui.singulo.handbook.records': ('旧文明の記録', 'Ancient Records'),
    'gui.singulo.handbook.page': ('%s / %s', '%s / %s'),
    'gui.singulo.handbook.stage': ('段階%s', 'Stage %s'),
    'gui.singulo.handbook.next': ('次の目標', 'Next goals'),
    'gui.singulo.handbook.done_count': ('達成 %s / %s', 'Completed %s / %s'),
    'gui.singulo.handbook.locked': ('？？？', '???'),
    'gui.singulo.handbook.no_records': ('まだ解読した記録がない。遺構で記録片を探し、アーカイブ端末で解読しよう。',
                                        'No records decoded yet. Find record fragments in ruins and decode them in the Archive Terminal.'),
    'message.singulo.record_read': ('旧文明の記録「%s」を読んだ。ハンドブックの「旧文明の記録」で読める', 'You read the ancient record "%s". Find it in the handbook'),
    'message.singulo.records_complete': ('旧文明の記録はすべて読んだ', 'You have read every ancient record'),
    'message.singulo.strangelet_contained': ('ストレンジレットを磁気瓶に封じ込めた', 'Strangelet contained'),
    'tooltip.singulo.magnetic_bottle': ('残り %s / %s 回', '%s / %s uses left'),
    'message.singulo.neutrino_scan': ('鉱石 %s 個・遺構のブロック %s 個が見えた', 'Found %s ores and %s ruin blocks'),
    'message.singulo.hydrogen_leak': ('水素が漏れて引火した！', 'Leaking hydrogen ignited!'),
    'gui.singulo.slot.catalyst': ('触媒', 'Catalyst'),
    'gui.singulo.slot.fuel': ('燃料', 'Fuel'),
    'gui.singulo.slot.permit': ('許可証', 'Permit'),
    'gui.singulo.shield.permit_on': ('許可証: %s人だけが中で作業できる', 'Permit: only %s players may build inside'),
    'gui.singulo.shield.permit_off': ('許可証なし', 'No permit'),
    'gui.singulo.shield.locked': ('許可証に登録されていない', 'Not on the permit'),
    'item.singulo.shield_permit.added': ('%s を登録した　%s人', 'Registered %s · %s'),
    'item.singulo.shield_permit.already': ('%s は登録済み　%s人', '%s already registered · %s'),
    'item.singulo.shield_permit.reset': ('登録を %s だけにした', 'Only %s is registered now'),
    'item.singulo.shield_permit.empty': ('まだだれも登録されていない', 'Nobody registered yet'),
    'item.singulo.shield_permit.members': ('登録: %s人', 'Registered: %s'),
    'item.singulo.shield_permit.more': ('ほか %s人', '%s more'),
    'item.singulo.shield_permit.hint': ('右クリックで自分を、相手に右クリックでその人も登録', 'Use to register yourself; use on a player to add them'),
    'gui.singulo.reactor.interval': ('投入間隔', 'Feed interval'),
    'gui.singulo.reactor.interval.hint': ('0 で自動', '0 = auto'),
    'gui.singulo.reactor.collapse': ('炉が質量に耐えきれず崩壊していく！', 'The reactor is collapsing under the mass!'),
    'gui.singulo.reactor.recaptured': ('野良ブラックホールを炉心に取り込んだ', 'The rogue black hole is now the core'),
    'block.singulo.rogue_black_hole': ('野良ブラックホール', 'Rogue Black Hole'),
    'item.singulo.settings_card.copied': ('設定を写した', 'Settings copied'),
    'item.singulo.settings_card.pasted': ('設定を貼った', 'Settings pasted'),
    'item.singulo.settings_card.empty': ('まだ何も写していない', 'Nothing copied yet'),
    'item.singulo.settings_card.mismatch': ('種類の違う装置には貼れない', 'Different kind of machine'),
    'item.singulo.settings_card.blank': ('空', 'Blank'),
    'item.singulo.settings_card.holds.machine': ('装置の面の設定', 'Machine side settings'),
    'item.singulo.settings_card.holds.reactor': ('Pリアクターの設定', 'P-Reactor settings'),
    'item.singulo.settings_card.hint': ('スニークして使うと写す　そのまま使うと貼る', 'Sneak-use to copy · Use to paste'),
    'gui.singulo.observatory.on': ('表示 入　鉱石 %s　遺構 %s', 'Display on · ores %s · ruins %s'),
    'gui.singulo.observatory.off': ('表示 切', 'Display off'),
    'command.singulo.blackhole.done': ('%s, %s, %s に野良ブラックホールを出した　質量 %s', 'Rogue black hole at %s, %s, %s · mass %s'),
    'gui.singulo.reactor.slot.0': ('特異点の種', 'Singularity seed'),
    'gui.singulo.reactor.slot.1': ('質量ペレット', 'Mass pellets'),
    'gui.singulo.reactor.slot.2': ('ジェット・コレクター', 'Jet collectors'),
    'gui.singulo.reactor.slot.3': ('Hコレクター', 'H-collector'),
    'gui.singulo.reactor.slot.4': ('エルゴスフィア・リング', 'Ergosphere ring'),
    'gui.singulo.reactor.slot.5': ('副産物', 'By-products'),
    'gui.singulo.slot.upgrade': ('単極子アップグレード', 'Monopole upgrade'),
    'gui.singulo.sides.button': ('面の設定', 'Sides'),
    'gui.singulo.sides.items': ('アイテム', 'Items'),
    'gui.singulo.sides.fluids': ('液体', 'Fluids'),
    'gui.singulo.sides.legend': ('灰:無効 青:入力 橙:出力 緑:入出力', 'Grey off · Blue in · Orange out · Green both'),
    'gui.singulo.sides.face.front': ('前', 'Front'),
    'gui.singulo.sides.face.back': ('後', 'Back'),
    'gui.singulo.sides.face.left': ('左', 'Left'),
    'gui.singulo.sides.face.right': ('右', 'Right'),
    'gui.singulo.sides.face.top': ('上', 'Top'),
    'gui.singulo.sides.face.bottom': ('下', 'Bottom'),
    'gui.singulo.sides.mode.0': ('無効', 'Disabled'),
    'gui.singulo.sides.mode.1': ('入力', 'Input'),
    'gui.singulo.sides.mode.2': ('出力', 'Output'),
    'gui.singulo.sides.mode.3': ('入出力', 'Input & output'),
    'gui.singulo.sides.eject_on': ('自動排出: オン', 'Auto-eject: on'),
    'gui.singulo.sides.eject_off': ('自動排出: オフ', 'Auto-eject: off'),
    'gui.singulo.sides.master_on': ('自動排出 オン', 'Auto-eject ON'),
    'gui.singulo.sides.master_off': ('自動排出 オフ', 'Auto-eject OFF'),
    'gui.singulo.sides.hint': ('ドラッグで回す　クリックで切り替え　右クリックで自動排出', 'Drag to rotate · click to change · right-click for auto-eject'),
    'gui.singulo.smes.in': ('受け取り: %s FE/t', 'In: %s FE/t'),
    'gui.singulo.smes.out': ('送り出し %s FE/t', 'Output %s FE/t'),
    'gui.singulo.smes.hint': ('最大 %s FE/t。正面以外の5面から受け取る', 'Up to %s FE/t. Receives on the other five faces'),
    'ruin.singulo.observation_post': ('地表観測拠点', 'Observation Post'),
    'ruin.singulo.research_building': ('研究棟', 'Research Building'),
    'ruin.singulo.culture_facility': ('封鎖培養施設', 'Culture Facility'),
    'ruin.singulo.final_lab': ('最終実験施設', 'Final Lab'),
    'compass.singulo.target': ('探す遺構: %s', 'Target: %s'),
    'compass.singulo.found': ('%s は %s ブロック先', '%s is %s blocks away'),
    'compass.singulo.not_found': ('近くに %s が見つからない', 'No %s nearby'),
    'tooltip.singulo.creative_only': ('クリエイティブ専用', 'Creative only'),
    'tooltip.singulo.creative_catalyst.tier': ('ティア %s の触媒として働く', 'Acts as a tier %s catalyst'),
    'tooltip.singulo.builder_wand.hint': ('コントローラに使って一瞬で組み立てる。スニークで大きさ切替', 'Use on a controller to build instantly. Sneak to change size'),
    'tooltip.singulo.builder_wand.size': ('大きさ: %s', 'Size: %s'),
    'tooltip.singulo.creative_energy.hint': ('無限の電力を隣へ送っている', 'Pushing infinite energy to neighbours'),
    'pack.singulo.hd': ('Singulo HD（32×32 のテクスチャ）', 'Singulo HD (32×32 textures)'),
    'jei.singulo.uses': ('使用回数を %s 使う', 'Uses %s'),
    'jei.singulo.mass': ('質量 %s', 'Mass %s'),
    'jei.singulo.metal_mass': ('金属質量 %s', 'Metal mass %s'),
    'jei.singulo.time_energy': ('%s 秒・%s FE/t', '%s s · %s FE/t'),
    'jei.singulo.min_size': ('大きさ %s 以上', 'Size %s+'),
    'jei.singulo.found_prefix': ('入手', 'Found in'),
    'gauntlet.singulo.left_click': ('左クリックを押している間、重力を操る', 'Hold left-click to manipulate gravity'),
    'gauntlet.singulo.charging': ('ためている %s%%', 'Charging %s%%'),
    'gauntlet.singulo.thrown': ('投げた！　%s%%', 'Thrown! %s%%'),
    'gauntlet.singulo.nothing_held': ('何も持ち上げていない', 'Nothing held'),
    'tooltip.singulo.manipulator.hint': ('左クリック長押しで操る　浮遊中に右クリック長押しでため、離して投げる', 'Hold attack to manipulate · Hold use to charge, release to throw'),
    'jei.singulo.multiblock': ('マルチブロック装置', 'Multiblock Structures'),
    'jei.singulo.mb.cooling_tower': ('■ 形: くびれた塔　高さ7〜15\n■ 速さ: 高さ÷5 倍\n■ 高さ10以上: 液体ヘリウム', '■ Shape: waisted tower, 7–15 high\n■ Speed: height ÷ 5\n■ 10+ high: liquid helium'),
    'jei.singulo.mb.particle_accelerator': ('■ 形: 一辺8〜32の正方形の環\n■ 速さ: 一辺÷8 倍\n■ 副産物: 磁気単極子\n■ 危険: 大きな環で電力が減るとストレンジレットが出る', '■ Shape: square ring, side 8–32\n■ Speed: side ÷ 8\n■ By-product: magnetic monopoles\n■ Danger: strangelets when a large ring runs low on power'),
    'jei.singulo.mb.degenerate_compactor': ('■ 形: 5×5×5 の閉じたプレス\n■ 起動: 電力をつなぐ', '■ Shape: closed 5×5×5 press\n■ Start: connect power'),
    'jei.singulo.mb.casimir_cavity': ('■ 形: 5×5×5 の真空容器\n■ 起動: 電力と触媒', '■ Shape: 5×5×5 vacuum vessel\n■ Start: power and a catalyst'),
    'jei.singulo.mb.degenerate_furnace': ('■ 形: 5×5×7 の閉じた炉\n■ 起動: 時間結晶触媒と燃料\n■ 燃料: 圧縮ブロック Lv2\n■ 出力: 20 MFE/t', '■ Shape: closed 5×5×7 furnace\n■ Start: time crystal catalyst and fuel\n■ Fuel: compressed block Lv2\n■ Output: 20 MFE/t'),
    'jei.singulo.mb.penrose_reactor': ('■ 形: 交わる3本のリング　13×13×13\n■ 点火: 種を入れ、50 GFE を注ぐ\n■ 燃料: 質量ペレット\n■ 最大出力: 約 2.1 GFE/t', '■ Shape: three crossing rings, 13×13×13\n■ Ignite: insert a seed and pour in 50 GFE\n■ Fuel: mass pellets\n■ Max output: about 2.1 GFE/t'),
    'jei.singulo.mb.event_horizon_shield': ('■ 形: 基壇から尖塔へすぼまる塔　高さ9\n■ 起動: 時間結晶触媒かシンギュラリティ・コアと電力\n■ 効果: 半径32を守る', '■ Shape: tapering tower, 9 high\n■ Start: time crystal or singularity core, and power\n■ Effect: guards a radius of 32'),
    'jei.singulo.mb.tipler_cylinder': ('■ 形: 5×5×9 の格納筒\n■ 起動: 時間結晶触媒・燃料・電力\n■ 燃料: エキゾチック物質\n■ 効果: 半径16が2倍速', '■ Shape: 5×5×9 housing\n■ Start: time crystal catalyst, fuel and power\n■ Fuel: exotic matter\n■ Effect: ×2 speed within 16'),
    'jei.singulo.mb.wormhole_generator': ('■ 形: 5×5×5 の球\n■ 起動: 100 MFE/t を10秒\n■ 結果: 一対のワームホールの口', '■ Shape: 5×5×5 sphere\n■ Start: 100 MFE/t for 10 s\n■ Result: a pair of wormhole mouths'),
    'message.singulo.multiblock.not_formed': ('%s はまだ未完成', '%s is not complete yet'),
    'message.singulo.welcome': ('Singulo ハンドブックを受け取った。右クリックで開ける', 'You received the Singulo Handbook. Right-click to open it'),
    'gauntlet.singulo.area.cone': ('範囲: 前方の円錐', 'Area: forward cone'),
    'key.singulo.toggle_area': ('重力操作の範囲を切り替え', 'Toggle gravity tool area'),
    'key.categories.singulo': ('Singulo', 'Singulo'),
    'tooltip.singulo.dark_matter': ('ダークマター: %s mB', 'Dark matter: %s mB'),
    'gui.singulo.containment.status': ('ダークマター %s / %s mB', 'Dark matter %s / %s mB'),
    'gui.singulo.containment.leaking': ('ダークマター %s / %s mB　漏れている', 'Dark matter %s / %s mB · leaking'),
    'gui.singulo.halo.no_core': ('%s ブロック以内に稼働中の炉心がない', 'No running core within %s blocks'),
    'gui.singulo.halo.status': ('収集 %s mB/秒　中に %s mB', 'Collecting %s mB/s · %s mB inside'),
    'tooltip.singulo.wormhole.unstable': ('不安定: あと %s 秒で消える', 'Unstable: collapses in %s s'),
    'gui.singulo.wormhole.mouth_collapsed': ('不安定なワームホールの口が崩壊した', 'An unstable wormhole mouth collapsed'),
    'gui.singulo.wormhole.generated': ('一対の口ができた。%s 秒以内に固定化すること', 'A pair of mouths formed. Stabilize within %s s'),
    'gui.singulo.wormhole.generator': ('生成 %s / %s 秒', 'Generating %s / %s s'),
    'gui.singulo.device.not_formed': ('未完成', 'Incomplete'),
    'gui.singulo.device.generator.idle': ('電力を10秒注ぐと口ができる', 'Feed power for 10 s'),
    'gui.singulo.device.generator.working': ('生成中 %s / %s 秒', 'Generating %s / %s s'),
    'gui.singulo.device.generator.done': ('口ができた', 'Mouths ready'),
    'gui.singulo.device.generator.input': ('入力 %sFE/t　必要 %sFE/t', 'Input %sFE/t · need %sFE/t'),
    'gui.singulo.device.generator.expires': ('残り %s秒', '%ss left'),
    'gui.singulo.device.mouth.size': ('喉 %s×%s → 目標 %s×%s', 'Throat %sx%s -> %sx%s'),
    'gui.singulo.device.mouth.no_partner': ('対の口がない', 'No partner'),
    'gui.singulo.device.mouth.unloaded': ('対 %s, %s, %s　未読込', 'Pair %s, %s, %s · unloaded'),
    'gui.singulo.device.mouth.linked': ('対 %s, %s, %s', 'Pair %s,%s,%s'),
    'gui.singulo.device.mouth.cross': ('別の次元', 'Other dimension'),
    'gui.singulo.device.mouth.bandwidth': ('%sFE・%s個・%smB /t', '%sFE, %s items, %smB /t'),
    'gui.singulo.device.mouth.upkeep': ('1個で %s 分もつ', '1 lasts %s min'),
    'gui.singulo.device.mouth.upkeep_none': ('維持なし', 'No upkeep'),
    'gui.singulo.device.mouth.starving': ('燃料切れ: %s秒で縮む', 'No fuel: shrinks in %ss'),
    'gui.singulo.device.port.no_mouth': ('近くに口がない', 'No mouth nearby'),
    'gui.singulo.device.port.no_partner': ('口に対がない', 'Mouth has no partner'),
    'gui.singulo.device.port.linked': ('つながっている', 'Linked'),
    'gui.singulo.device.port.throat': ('喉 %s×%s', 'Throat %sx%s'),
    'gui.singulo.device.port.targets': ('向こう側: 電力%s・物%s・液体%s', 'Far side: %s power, %s items, %s fluids'),
    'gui.singulo.device.port.hint': ('隣の装置が向こうとつながる', 'Neighbours link through'),
    'gui.singulo.device.tank.label': ('ダークマター', 'Dark matter'),
    'gui.singulo.device.tank.contained': ('閉じ込め中', 'Contained'),
    'gui.singulo.device.tank.leaking': ('電力切れ: 毎秒1%%漏れている', 'No power: leaking 1%%/s'),
    'gui.singulo.device.tank.upkeep': ('場の維持 %s FE/t', 'Field upkeep %s FE/t'),
    'gui.singulo.device.energy_short': ('電力 %s / %s FE', 'Energy %s / %s FE'),
    'gui.singulo.device.halo.core': ('炉心の質量 %s', 'Core mass %s'),
    'gui.singulo.device.halo.no_core': ('%sブロック以内に炉心がない', 'No core within %s blocks'),
    'gui.singulo.device.halo.rate': ('捕集 %s mB/秒', 'Collecting %s mB/s'),
    'gui.singulo.device.halo.tank': ('内部 %s / %s mB', 'Buffer %s / %s mB'),
    'gui.singulo.device.halo.leak': ('隣のタンクへ毎秒送る', 'Pushes to tanks each second'),
    'gui.singulo.device.muon.altitude': ('高さ Y=%s', 'Altitude Y=%s'),
    'gui.singulo.device.muon.rate': ('収集の速さ %s%%', 'Rate %s%%'),
    'gui.singulo.device.muon.no_sky': ('空が見えない', 'No sky access'),
    'gui.singulo.device.muon.hint': ('Y=100で0、Y=200で最大', '0 at Y100, max at Y200'),
    'gui.singulo.device.detector.observe': ('観測', 'Observe'),
    'gui.singulo.device.detector.cost': ('1回 %s FE', '%s FE each'),
    'gui.singulo.device.detector.not_yet': ('まだ観測していない', 'Not observed yet'),
    'gui.singulo.stabilizer.working': ('固定化 %s%%', 'Stabilizing %s%%'),
    'gui.singulo.stabilizer.waiting': ('燃料・電力待ち', 'Waiting'),
    'gui.singulo.stabilizer.done': ('完了', 'Done'),
    'gui.singulo.stabilizer.empty': ('空き', 'Empty'),
    'gui.singulo.stabilizer.fuel': ('エキゾチック物質', 'Exotic matter'),
    'gui.singulo.wormhole.stabilizer': ('固定化 %s%% / %s%%、エキゾチック物質 %s 個', 'Stabilizing %s%% / %s%%, exotic matter: %s'),
    'gui.singulo.wormhole.status': ('喉 %1$s×%1$s　目標 %2$s×%2$s　エキゾチック物質 %3$s 個', 'Throat %1$s×%1$s · target %2$s×%2$s · exotic matter %3$s'),
    'gui.singulo.wormhole.no_partner': ('つながる口がない', 'No paired mouth'),
    'gui.singulo.wormhole.partner_unloaded': ('向こう側の口のチャンクが読み込まれていない', 'The paired mouth is not loaded'),
    'gui.singulo.wormhole_port.no_mouth': ('8ブロック以内に開いた口がない', 'No open mouth within 8 blocks'),
    'gui.singulo.wormhole_port.no_partner': ('口の向こう側がつながっていない', 'The mouth is not connected'),
    'gui.singulo.wormhole_port.status': ('向こう側の入れ物 %s 個とつながっている', 'Connected to %s handlers on the other side'),
    'entity.singulo.security_drone': ('警備ドローン', 'Security Drone'),
    'entity.singulo.horizon_warden': ('ホライズン・ウォーデン', 'Horizon Warden'),
    'entity.singulo.warden_singularity': ('ウォーデンの特異点', 'Warden Singularity'),
    'entity.singulo.horizon_bolt': ('光弾', 'Horizon Bolt'),
    'item.singulo.horizon_warden_spawn_egg': ('ホライズン・ウォーデンのスポーンエッグ', 'Horizon Warden Spawn Egg'),
    'death.attack.singulo.tidal': ('%1$s は潮汐力で引き裂かれた', '%1$s was torn apart by tidal forces'),
    'death.attack.singulo.event_horizon': ('%1$s は事象の地平線を越えた', '%1$s crossed the event horizon'),
    'death.attack.singulo.tidal.player': ('%1$s は %2$s の特異点に引き裂かれた', "%1$s was torn apart by %2$s's singularity"),
    'gui.singulo.console.awakened': ('封印が破れた。ホライズン・ウォーデンが目を覚ました', 'The seal breaks. The Horizon Warden awakens'),
    'gui.singulo.console.active': ('守護機はすでに起動している', 'The warden is already active'),
    'gui.singulo.console.open': ('保管庫の封鎖は解かれている', 'The vault is not sealed'),
    'gui.singulo.console.no_vault': ('封鎖された保管庫が近くにない', 'No sealed vault nearby'),
    'gui.singulo.console.defeated': ('守護機が沈黙した。保管庫の力場が消えた', 'The warden falls silent. The vault field dissipates'),
    'gui.singulo.console.reset': ('挑戦者がいなくなり、守護機は封印に戻った', 'No challenger remains. The warden returns to its seal'),
    'gui.singulo.cache.sealed': ('力場で封鎖されている。中央の封印コンソールから守護機を起こして倒すこと',
                                 'Sealed by a force field. Wake and defeat the warden at the central console'),
    'item.singulo.security_drone_spawn_egg': ('警備ドローンのスポーンエッグ', 'Security Drone Spawn Egg'),
    'gui.singulo.detector.result': ('重力波を検出: %s、%s', 'Gravitational waves detected: %s, %s'),
    'gui.singulo.detector.none': ('重力異常は見つからない', 'No gravity anomaly found'),
    'gui.singulo.detector.no_power': ('観測には %s FE 必要', 'An observation needs %s FE'),
    'gui.singulo.detector.band.0': ('256ブロック以内', 'within 256 blocks'),
    'gui.singulo.detector.band.1': ('256〜512ブロック', '256–512 blocks'),
    'gui.singulo.detector.band.2': ('512〜1024ブロック', '512–1024 blocks'),
    'gui.singulo.detector.band.3': ('1024〜2048ブロック', '1024–2048 blocks'),
    'gui.singulo.detector.band.4': ('2048ブロック以上', 'over 2048 blocks'),
    'direction.singulo.north': ('北', 'north'),
    'direction.singulo.northeast': ('北東', 'northeast'),
    'direction.singulo.east': ('東', 'east'),
    'direction.singulo.southeast': ('南東', 'southeast'),
    'direction.singulo.south': ('南', 'south'),
    'direction.singulo.southwest': ('南西', 'southwest'),
    'direction.singulo.west': ('西', 'west'),
    'direction.singulo.northwest': ('北西', 'northwest'),
}


def lang_files():
    ja, en = {}, {}
    for name, (iid, en_name, kind) in MOD.items():
        if kind == 'structure':
            continue
        if kind == 'fluid':
            for d, v in ((ja, name), (en, en_name)):
                d[f'fluid_type.{MODID}.{iid}'] = v
            continue
        prefix = 'block' if kind in BLOCK_KINDS else 'item'
        ja[f'{prefix}.{MODID}.{iid}'] = name
        en[f'{prefix}.{MODID}.{iid}'] = en_name
    for k, (j, e) in UI_LANG.items():
        ja[k], en[k] = j, e
    stage_en = {1: 'Startup', 2: 'Cryogenic', 3: 'Quantum', 4: 'Temporal', 5: 'Singularity'}
    for s, n in STAGE_NAMES.items():
        ja[f'stage.{MODID}.{s}'] = n
        en[f'stage.{MODID}.{s}'] = stage_en[s]
    for station, sid in STATIONS.items():
        name = station.split('（')[0]
        en_name = next((v[1] for k, v in MOD.items() if k == name), sid)
        ja[f'station.{MODID}.{sid}'] = station
        en[f'station.{MODID}.{sid}'] = en_name
    import gen_docs
    ctx = dict(MOD=MOD, RECIPES=RECIPES, EXPLORE=EXPLORE, RESTORED=RESTORED, STATIONS=STATIONS, warn=warn,
               write_json=write_json, ASSETS=ASSETS, DATA=DATA)
    gen_docs.descriptions(ctx, ja, en)
    gen_docs.guide(ctx, ja, en)
    gen_docs.advancements(ctx, ja, en)
    from records import RECORDS
    for rid, _, jt, jb, et, eb in RECORDS:
        ja[f'record.{MODID}.{rid}.title'], ja[f'record.{MODID}.{rid}.body'] = jt, jb
        en[f'record.{MODID}.{rid}.title'], en[f'record.{MODID}.{rid}.body'] = et, eb
    write_json(ASSETS / 'lang' / 'ja_jp.json', dict(sorted(ja.items())))
    write_json(ASSETS / 'lang' / 'en_us.json', dict(sorted(en.items())))
    import localization
    localization.generate(en)


# ---------------------------------------------------------------- モデル・テクスチャ

# マルチブロックのコントローラの、正面以外の外装（部品と同じテクスチャ）
CONTROLLER_SKINS = {
    'degenerate_furnace_controller': 'degenerate_furnace_shell',
    'cooling_tower_controller': 'cooling_tower_casing',
    'degenerate_compactor_controller': 'degenerate_compactor_plate',
    'casimir_cavity_controller': 'casimir_cavity_wall',
    'shield_tower_core': 'shield_tower_plinth',
    'creative_energy_source': 'creative_energy_casing',
    'tipler_core': 'tipler_housing',
    'wormhole_generator_core': 'wormhole_generator_shell',
}


def models():
    boost_overlay_model()
    black_hole_item_models()
    manipulator_staff_model()
    compass_item_model()
    for name, (iid, _, kind) in MOD.items():
        if kind in ('structure', 'fluid'):
            continue
        if kind in ('block', 'part_block', 'part_glass', 'ruin_block', 'ruin_glass'):
            model = {'parent': 'minecraft:block/cube_all', 'textures': {'all': f'{MODID}:block/{iid}'}}
            if kind == 'part_glass':
                model['render_type'] = 'minecraft:translucent'      # 色つきの半透明ガラス（中が見える）
            elif kind == 'ruin_glass':
                model['render_type'] = 'minecraft:cutout'
            write_json(ASSETS / 'models' / 'block' / f'{iid}.json', model)
            if iid == 'ruin_cache':
                # 封鎖中（sealed=true）は別の見た目
                write_json(ASSETS / 'models' / 'block' / 'ruin_cache_sealed.json',
                           {'parent': 'minecraft:block/cube_all', 'textures': {'all': f'{MODID}:block/ruin_cache_sealed'}})
                write_json(ASSETS / 'blockstates' / f'{iid}.json', {'variants': {
                    'sealed=false': {'model': f'{MODID}:block/{iid}'},
                    'sealed=true': {'model': f'{MODID}:block/ruin_cache_sealed'}}})
            else:
                write_json(ASSETS / 'blockstates' / f'{iid}.json', {'variants': {'': {'model': f'{MODID}:block/{iid}'}}})
            write_json(ASSETS / 'models' / 'item' / f'{iid}.json', {'parent': f'{MODID}:block/{iid}'})
            if iid in ('accelerator_tube', 'focusing_magnet'):
                import accelerator_assets
                accelerator_assets.models(ASSETS)
        elif kind == 'machine':
            if iid in CABLES:
                cable_models(iid)
                continue
            st = stage_of(name)
            import shapes
            skin = CONTROLLER_SKINS.get(iid)
            for state in ('', '_on'):
                els, has_glass = shapes.machine_elements(iid, on=state == '_on')
                model = {
                    'parent': 'minecraft:block/block',
                    'textures': {'top': f'{MODID}:block/machine_top_t{st}', 'side': f'{MODID}:block/machine_side_t{st}',
                                 'bottom': f'{MODID}:block/machine_bottom_t{st}', 'glass': f'{MODID}:block/machine_glass',
                                 'inner': f'{MODID}:block/machine_inner',
                                 'front': f'{MODID}:block/{iid}_front{state}', 'particle': f'{MODID}:block/machine_side_t{st}'},
                    'elements': els,
                }
                if skin:
                    # マルチブロックのコントローラは、部品と同じ外装で包む（見た目をそろえる）
                    for key in ('top', 'side', 'bottom', 'particle'):
                        model['textures'][key] = f'{MODID}:block/{skin}'
                if has_glass:
                    # 窓は切り抜きで描く（半透明にすると、ガラスが先に描かれたときに奥のくぼみが消えて向こうが透ける）
                    model['render_type'] = 'minecraft:cutout'
                write_json(ASSETS / 'models' / 'block' / f'{iid}{state}.json', model)
            multipart = []
            for facing, rot in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
                for lit in ('false', 'true'):
                    m = {'model': f'{MODID}:block/{iid}{"_on" if lit == "true" else ""}'}
                    if rot:
                        m['y'] = rot
                    multipart.append({'when': {'facing': facing, 'lit': lit}, 'apply': m})
            # 上位装置の恩恵を受けている間は、エネルギーの流れの模様を重ねる
            multipart.append({'when': {'boosted': 'true'}, 'apply': {'model': f'{MODID}:block/boost_overlay'}})
            write_json(ASSETS / 'blockstates' / f'{iid}.json', {'multipart': multipart})
            write_json(ASSETS / 'models' / 'item' / f'{iid}.json', {'parent': f'{MODID}:block/{iid}'})
        else:
            if iid in BLACK_HOLE_ITEMS or iid in ('explorer_compass', 'graviton_manipulator'):
                continue                                       # 立体のモデル・針のモデルは別に作る
            write_json(ASSETS / 'models' / 'item' / f'{iid}.json',
                       {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{MODID}:item/{iid}'}})


def boost_overlay_model():
    face = {'texture': '#flow', 'uv': [0, 0, 16, 16]}
    write_json(ASSETS / 'models' / 'block' / 'boost_overlay.json', {
        'parent': 'minecraft:block/block', 'ambientocclusion': False, 'render_type': 'minecraft:translucent',
        'textures': {'flow': f'{MODID}:block/boost_overlay', 'particle': f'{MODID}:block/boost_overlay'},
        'elements': [{'from': [-0.02, -0.02, -0.02], 'to': [16.02, 16.02, 16.02], 'shade': False,
                      'neoforge_data': {'block_light': 15, 'sky_light': 15},
                      'faces': {d: dict(face) for d in ('north', 'south', 'east', 'west', 'up', 'down')}}],
    })


def cable_models(iid):
    """ケーブル: 一辺 w（偶数）の正方形の断面。腕は面から中心の箱の縁まで（z=0〜8-w/2）、中心はまっすぐなら同じ断面の
    短い筒、分かれ目では一辺 w の箱。座標はすべて整数で、テクスチャは1ドットを1ドットのまま貼る（UV の大きさ＝面の大きさ）。
    側面は行 8-w/2〜8+w/2 の帯、切り口と分かれ目の箱は同じ帯が四角く広がる絵。段階2以上は電力が流れている間「_on」の絵。"""
    import art16
    w = art16.CABLE_WIDTHS[iid]
    lo, hi = 8 - w // 2, 8 + w // 2
    glowing = iid != 'copper_wire'
    tex = f'{MODID}:block/{iid}'

    def side(length):
        return {'uv': [0, lo, length, hi], 'texture': '#c'}

    def tube(z0, z1, cap):
        faces = {'east': side(z1 - z0), 'west': side(z1 - z0),
                 'up': {**side(z1 - z0), 'rotation': 90}, 'down': {**side(z1 - z0), 'rotation': 90}}
        if cap:
            faces['north'] = {'uv': [lo, lo, hi, hi], 'texture': '#e'}
        return {'from': [lo, lo, z0], 'to': [hi, hi, z1], 'faces': faces}

    variants = [('', tex, f'{tex}_end')] + ([('_on', f'{tex}_on', f'{tex}_end_on')] if glowing else [])
    for suffix, side_tex, end_tex in variants:
        textures = {'particle': tex, 'c': side_tex, 'e': end_tex}
        write_json(ASSETS / 'models' / 'block' / f'{iid}_arm{suffix}.json',
                   {'parent': 'minecraft:block/block', 'textures': textures, 'elements': [tube(0, lo, True)]})
        write_json(ASSETS / 'models' / 'block' / f'{iid}_straight{suffix}.json',
                   {'parent': 'minecraft:block/block', 'textures': textures, 'elements': [tube(lo, hi, False)]})
        box = {'from': [lo, lo, lo], 'to': [hi, hi, hi],
               'faces': {d: {'uv': [lo, lo, hi, hi], 'texture': '#e'} for d in ('north', 'south', 'east', 'west', 'up', 'down')}}
        write_json(ASSETS / 'models' / 'block' / f'{iid}_core{suffix}.json',
                   {'parent': 'minecraft:block/block', 'textures': textures, 'elements': [box]})
    rot = {'north': {}, 'east': {'y': 90}, 'south': {'y': 180}, 'west': {'y': 270}, 'up': {'x': 270}, 'down': {'x': 90}}
    straight_rot = {'z': {}, 'x': {'y': 90}, 'y': {'x': 90}}
    multipart = []
    for pw, suffix in ([('false', ''), ('true', '_on')] if glowing else [(None, '')]):
        def when(extra):
            return dict(extra, powered=pw) if pw is not None else dict(extra)
        multipart.append({'when': when({'straight': 'none'}), 'apply': {'model': f'{MODID}:block/{iid}_core{suffix}'}})
        for axis, r in straight_rot.items():
            multipart.append({'when': when({'straight': axis}), 'apply': {'model': f'{MODID}:block/{iid}_straight{suffix}', **r}})
        for d, r in rot.items():
            multipart.append({'when': when({d: 'true'}), 'apply': {'model': f'{MODID}:block/{iid}_arm{suffix}', **r}})
    write_json(ASSETS / 'blockstates' / f'{iid}.json', {'multipart': multipart})
    # 持ったときは、まっすぐな1本（長さ16、断面 w）
    band = {'uv': [0, lo, 16, hi], 'texture': '#c'}
    item = {'from': [0, lo, lo], 'to': [16, hi, hi], 'faces': {
        'north': band, 'south': band, 'up': band, 'down': band,
        'east': {'uv': [lo, lo, hi, hi], 'texture': '#e'}, 'west': {'uv': [lo, lo, hi, hi], 'texture': '#e'}}}
    write_json(ASSETS / 'models' / 'item' / f'{iid}.json', {
        'parent': 'minecraft:block/block', 'textures': {'particle': tex, 'c': tex, 'e': f'{tex}_end'}, 'elements': [item],
        'display': {'gui': {'rotation': [30, 45, 0], 'scale': [0.8, 0.8, 0.8]},
                    'fixed': {'rotation': [0, 90, 0], 'scale': [0.8, 0.8, 0.8]}}})

def textures():
    """白い無機質パネル＋段階色の発光ラインで仮テクスチャを描く。"""
    from PIL import Image, ImageDraw

    white, seam, shade = (236, 238, 240, 255), (196, 202, 208, 255), (214, 219, 224, 255)
    WELL_COLOR = (57, 66, 74, 255)

    def panel(size=16):
        im = Image.new('RGBA', (size, size), white)
        d = ImageDraw.Draw(im)
        d.rectangle([0, 0, size - 1, size - 1], outline=seam)
        d.line([(0, size // 2), (size - 1, size // 2)], fill=shade)
        return im, d

    def save(im, path):
        path.parent.mkdir(parents=True, exist_ok=True)
        im.save(path)

    tb = ASSETS / 'textures' / 'block'
    ti = ASSETS / 'textures' / 'item'

    im, d = panel()
    d.rectangle([3, 3, 12, 12], outline=seam)
    save(im, tb / 'machine_top.png')
    im, d = panel()
    d.line([(1, 3), (14, 3)], fill=(150, 210, 230, 255))
    save(im, tb / 'machine_side.png')

    fronts = {
        'kiln': (255, 170, 90), 'compressor': (170, 200, 220), 'electrolyzer': (120, 200, 255),
        'archive_terminal': (140, 240, 220), 'thermoelectric_generator': (255, 140, 110),
        'precision_assembler': (200, 220, 255),
    }
    for n, (iid, _, kind) in MOD.items():
        if kind == 'machine' and iid not in CABLES and iid not in fronts:
            fronts[iid] = STAGE_COLORS[stage_of(n)]
    for iid, col in fronts.items():
        for on in (False, True):
            im, d = panel()
            d.rectangle([3, 3, 12, 9], fill=(40, 48, 56, 255), outline=seam)
            glow = col + (255,) if on else tuple(int(c * 0.35) for c in col) + (255,)
            d.rectangle([4, 4, 11, 8], fill=glow if iid in ('kiln', 'thermoelectric_generator') else (40, 48, 56, 255))
            if iid not in ('kiln', 'thermoelectric_generator'):
                d.line([(4, 6), (11, 6)], fill=glow)
            d.line([(3, 12), (12, 12)], fill=glow if on else (120, 200, 230, 255))
            save(im, tb / f'{iid}_front{"_on" if on else ""}.png')

    # 描画用の白（ブラックホールの黒い球や降着円盤は頂点色で塗る）
    save(Image.new('RGBA', (16, 16), (255, 255, 255, 255)), ASSETS / 'textures' / 'misc' / 'white.png')

    # ケーブル
    for iid, core in CABLES.items():
        im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        d = ImageDraw.Draw(im)
        d.rectangle([0, 0, 15, 15], fill=(222, 226, 230, 255))
        d.rectangle([5, 5, 10, 10], fill=core + (255,))
        save(im, tb / f'{iid}.png')
        # 持ったときの絵は立体のモデル（cable_models）なので、アイテムの平たい絵は作らない

    # 警備ドローン（32×32）: 白い胴、正面の帯とレンズを強さの色（1=水色、2=琥珀、3=赤）で光らせる
    te = ASSETS / 'textures' / 'entity'
    drone_textures(te, save)
    write_json(ASSETS / 'models' / 'item' / 'security_drone_spawn_egg.json',
               {'parent': 'minecraft:item/template_spawn_egg'})

    # 遺構の建材: 同じ白いパネルが黄ばみ、ひび割れ、苔に覆われたもの
    aged, aged_seam = (226, 218, 190, 255), (180, 170, 140, 255)
    for n, (iid, _, kind) in MOD.items():
        if kind not in ('ruin_block', 'ruin_glass'):
            continue
        if kind == 'ruin_glass':
            im = Image.new('RGBA', (16, 16), (200, 220, 225, 50))
            d = ImageDraw.Draw(im)
            d.rectangle([0, 0, 15, 15], outline=aged)
            d.line([(2, 13), (8, 6), (11, 9), (14, 2)], fill=(255, 255, 255, 160))
            save(im, tb / f'{iid}.png')
            continue
        im = Image.new('RGBA', (16, 16), aged)
        d = ImageDraw.Draw(im)
        d.rectangle([0, 0, 15, 15], outline=aged_seam)
        d.line([(0, 8), (15, 8)], fill=aged_seam)
        if iid == 'cracked_ruin_panel':
            d.line([(3, 0), (6, 5), (5, 9), (9, 15)], fill=(120, 110, 90, 255))
            d.line([(6, 5), (12, 7)], fill=(120, 110, 90, 255))
        elif iid == 'mossy_ruin_panel':
            rng = __import__('random').Random(7)
            for _ in range(60):
                x, y = rng.randint(0, 15), rng.randint(8, 15) if rng.random() < 0.7 else rng.randint(0, 15)
                d.point((x, y), fill=(90, 130, 60, 255))
        elif iid == 'ruin_lamp':
            d.rectangle([4, 4, 11, 11], fill=(60, 66, 70, 255))
            d.rectangle([5, 5, 10, 10], fill=(150, 230, 255, 255))
            d.point([(5, 5), (10, 10)], fill=(90, 120, 130, 255))
        elif iid == 'ruin_cache':
            d.rectangle([2, 3, 13, 12], fill=(57, 66, 74, 255), outline=aged_seam)
            d.line([(4, 7), (11, 7)], fill=(150, 230, 255, 255))
        elif iid == 'ruin_guard_dock':
            d.ellipse([3, 3, 12, 12], fill=(57, 66, 74, 255), outline=aged_seam)
            d.ellipse([6, 6, 9, 9], fill=(255, 120, 100, 255))
        elif iid == 'seal_console':
            d.rectangle([2, 2, 13, 13], fill=(30, 34, 40, 255), outline=(150, 230, 255, 255))
            d.ellipse([5, 5, 10, 10], outline=(150, 230, 255, 255))
            d.point([(7, 7), (8, 8), (7, 8), (8, 7)], fill=(255, 255, 255, 255))
        save(im, tb / f'{iid}.png')
        if iid == 'ruin_cache':
            # 力場で封鎖された保管庫（光の線が赤）
            d.line([(4, 7), (11, 7)], fill=(255, 90, 90, 255))
            d.rectangle([1, 2, 14, 13], outline=(255, 90, 90, 255))
            save(im, tb / 'ruin_cache_sealed.png')

    warden_textures(te, save)
    write_json(ASSETS / 'models' / 'item' / 'horizon_warden_spawn_egg.json',
               {'parent': 'minecraft:item/template_spawn_egg'})

    # マルチブロックの部品
    glow = (150, 230, 255, 255)
    for n, (iid, _, kind) in MOD.items():
        if kind not in ('part_block', 'part_glass'):
            continue
        if kind == 'part_glass':
            im = Image.new('RGBA', (16, 16), (200, 230, 245, 40))
            d = ImageDraw.Draw(im)
            d.rectangle([0, 0, 15, 15], outline=white)
            d.line([(3, 12), (6, 9)], fill=(255, 255, 255, 120))
        else:
            im, d = panel()
            if 'core' in iid:
                for y in range(3, 13, 2):
                    d.line([(3, y), (12, y)], fill=(200, 120, 70, 255))
                d.rectangle([2, 2, 13, 13], outline=glow)
            elif 'port' in iid:
                d.rectangle([4, 4, 11, 11], fill=WELL_COLOR, outline=glow)
            elif 'tube' in iid:
                d.rectangle([0, 6, 15, 9], fill=WELL_COLOR)
                d.line([(0, 7), (15, 7)], fill=glow)
            elif 'magnet' in iid:
                d.rectangle([2, 2, 13, 13], fill=(70, 110, 190, 255), outline=white)
                d.line([(2, 7), (13, 7)], fill=white)
            elif iid == 'reactor_shell':
                # 縮退物質（中性子星の地殻の物質）を成形した炉殻。黒に白い継ぎ目
                im = Image.new('RGBA', (16, 16), (24, 24, 30, 255))
                d = ImageDraw.Draw(im)
                d.rectangle([0, 0, 15, 15], outline=white)
                d.line([(0, 8), (15, 8)], fill=(70, 74, 84, 255))
                d.point([(4, 4), (11, 11), (11, 4), (4, 11)], fill=glow)
            elif iid == 'gyro_drive':
                d.ellipse([2, 2, 13, 13], fill=(57, 66, 74, 255), outline=glow)
                d.line([(2, 7), (13, 7)], fill=white)
                d.line([(7, 2), (7, 13)], fill=white)
            elif iid == 'mirror_plate':
                for y in range(16):
                    c = 200 + int(50 * (1 - abs(y - 5) / 11))
                    d.line([(0, y), (15, y)], fill=(c, c, min(255, c + 8), 255))
                d.rectangle([0, 0, 15, 15], outline=seam)
                d.line([(3, 12), (12, 3)], fill=(255, 255, 255, 255))
            elif 'piston' in iid:
                d.rectangle([1, 1, 14, 14], fill=(150, 156, 164, 255), outline=seam)
                d.rectangle([5, 5, 10, 10], fill=(57, 66, 74, 255))
                d.rectangle([6, 6, 9, 9], fill=glow)
        save(im, tb / f'{iid}.png')

    # 圧縮ブロック（段階が上がるほど暗く、継ぎ目が光る）
    for name, (iid, _, kind) in MOD.items():
        if kind != 'block':
            continue
        m = re.search(r'(\d)$', iid)
        if m is None:
            continue                                   # 圧縮ブロック以外（ストレンジ物質など）は別に描く
        level = int(m.group(1))
        metal = 'metal' in iid
        base = (120, 124, 130) if not metal else (150, 140, 120)
        k = 1 - 0.18 * (level - 1)
        im = Image.new('RGBA', (16, 16), tuple(int(c * k) for c in base) + (255,))
        d = ImageDraw.Draw(im)
        step = {1: 8, 2: 4, 3: 2}.get(level, 8)
        for p in range(0, 16, step):
            d.line([(p, 0), (p, 15)], fill=tuple(int(c * k * 0.8) for c in base) + (255,))
            d.line([(0, p), (15, p)], fill=tuple(int(c * k * 0.8) for c in base) + (255,))
        d.rectangle([0, 0, 15, 15], outline=(236, 238, 240, 255))
        save(im, tb / f'{iid}.png')

    # アイテム: 名前からおおまかな形を選び、段階色で塗る
    for name, (iid, _, kind) in MOD.items():
        if kind in ('structure', 'fluid') or kind in BLOCK_KINDS:
            continue
        col = STAGE_COLORS[stage_of(name)] + (255,)
        im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        d = ImageDraw.Draw(im)
        if 'ingot' in iid or iid == 'steel_blend':
            body = (150, 156, 164, 255) if iid == 'steel_ingot' else (90, 90, 96, 255)
            d.polygon([(2, 10), (6, 6), (14, 6), (10, 10)], fill=tuple(min(255, c + 40) for c in body[:3]) + (255,))
            d.rectangle([2, 10, 10, 13], fill=body)
            d.polygon([(10, 10), (14, 6), (14, 9), (10, 13)], fill=tuple(c - 30 for c in body[:3]) + (255,))
        elif 'plate' in iid or 'substrate' in iid or 'composite' in iid or 'ceramic' in iid:
            d.rectangle([2, 3, 13, 12], fill=white, outline=seam)
            if 'substrate' in iid:
                for y in (5, 8, 11):
                    d.line([(4, y), (11, y)], fill=col)
            if iid == 'steel_plate':
                d.rectangle([2, 3, 13, 12], fill=(170, 176, 184, 255), outline=(120, 126, 134, 255))
            if iid == 'unfired_ceramic':
                d.rectangle([2, 3, 13, 12], fill=(190, 180, 170, 255), outline=(150, 140, 130, 255))
        elif 'card' in iid or 'log' in iid or 'data' in iid:
            d.rectangle([3, 2, 12, 13], fill=white, outline=seam)
            d.rectangle([5, 4, 10, 6], fill=col if 'blank' not in iid else seam)
            d.line([(5, 9), (10, 9)], fill=seam)
            d.line([(5, 11), (9, 11)], fill=seam)
            if iid.startswith('data_card'):
                d.rectangle([3, 2, 12, 13], outline=col)
        elif 'catalyst' in iid or 'core' in iid or 'seed' in iid or 'sample' in iid or 'condensate' in iid:
            d.polygon([(8, 1), (14, 8), (8, 15), (2, 8)], fill=white, outline=seam)
            d.polygon([(8, 4), (11, 8), (8, 12), (5, 8)], fill=col)
            if 'degraded' in iid or 'dormant' in iid:
                d.line([(4, 4), (12, 12)], fill=(150, 130, 90, 255))
        elif 'coil' in iid or 'wire' in iid:
            for y in range(3, 13, 2):
                d.line([(3, y), (12, y)], fill=(200, 120, 70, 255) if 'coil' in iid else col)
            d.rectangle([2, 2, 13, 13], outline=white)
        elif 'circuit' in iid or 'unit' in iid or 'module' in iid or 'trap' in iid:
            d.rectangle([2, 2, 13, 13], fill=(60, 70, 80, 255), outline=white)
            d.rectangle([5, 5, 10, 10], fill=white)
            d.point([(3, 7), (12, 7), (7, 3), (7, 12)], fill=col)
            if 'degraded' in iid:
                d.line([(2, 13), (13, 2)], fill=(150, 130, 90, 255))
        elif iid in ('mass_pellet', 'muon_bundle', 'cold_atoms', 'magnetic_monopole'):
            d.ellipse([3, 3, 12, 12], fill=(70, 74, 80, 255) if iid == 'mass_pellet' else white, outline=col)
            d.ellipse([6, 6, 9, 9], fill=col)
        elif 'element' in iid or 'matter' in iid or 'shell' in iid:
            d.ellipse([1, 1, 14, 14], outline=white)
            d.ellipse([4, 4, 11, 11], fill=(20, 20, 26, 255) if 'shell' in iid else col)
            if 'element' in iid:
                d.ellipse([1, 5, 6, 10], fill=col)
                d.ellipse([9, 5, 14, 10], fill=col)
        else:
            d.rectangle([2, 2, 13, 13], fill=white, outline=seam)
            d.rectangle([5, 5, 10, 10], fill=col)
        save(im, ti / f'{iid}.png')


# ---------------------------------------------------------------- タグ・データ
    art_textures()


def art_textures():
    """標準のテクスチャ（16×16、tools/art16.py）と、組み込みリソースパック「Singulo HD」（32×32、tools/art.py）を描く。"""
    import art
    import art16
    tb = ASSETS / 'textures' / 'block'
    ti = ASSETS / 'textures' / 'item'
    hd = RES / 'resourcepacks' / 'singulo_hd'
    hb = hd / 'assets' / MODID / 'textures' / 'block'
    hi = hd / 'assets' / MODID / 'textures' / 'item'
    import accelerator_assets
    accelerator_assets.textures(tb, 16)
    accelerator_assets.textures(hb, 32)
    write_json(hd / 'pack.mcmeta', {'pack': {'description': 'Singulo HD (32x32)', 'pack_format': 34}})
    for stage in range(1, 6):
        for face in ('side', 'top', 'bottom'):
            art16.save(art16.casing(stage, face), tb / f'machine_{face}_t{stage}.png')
            art.save(art.casing(stage, face), hb / f'machine_{face}_t{stage}.png')
        art16.save(art16.accent(stage), tb / f'machine_accent_t{stage}.png')
        art16.save(art16.machine_frame(stage), tb / f'machine_frame_t{stage}.png')
    art16.save(art16.glass(), tb / 'machine_glass.png')
    art16.save(art16.inner(), tb / 'machine_inner.png')
    for name, (iid, _, kind) in MOD.items():
        if kind == 'machine' and iid not in CABLES:
            for on in (False, True):
                sheet, frames = art16.machine_front(iid, stage_of(name), on)
                path = tb / f'{iid}_front{"_on" if on else ""}.png'
                art16.save(sheet, path)
                if frames > 1:
                    write_json(Path(str(path) + '.mcmeta'), {'animation': {'frametime': 6, 'interpolate': True}})
                art.save(art.machine_front(iid, stage_of(name), on), hb / f'{iid}_front{"_on" if on else ""}.png')
    # クリエイティブ電源の外装（星空がまたたく、動くテクスチャ）
    art16.save(art16.creative_casing_sheet(), tb / 'creative_energy_casing.png')
    write_json(tb / 'creative_energy_casing.png.mcmeta', {'animation': {'frametime': 4, 'interpolate': True}})
    art.save(art16.creative_casing(0).resize((32, 32), __import__('PIL').Image.NEAREST), hb / 'creative_energy_casing.png')
    art16.save(art16.boost_overlay(), tb / 'boost_overlay.png')
    write_json(tb / 'boost_overlay.png.mcmeta', {'animation': {'frametime': 5, 'interpolate': True}})
    for name, (iid, _, kind) in MOD.items():
        if kind in ('part_block', 'part_glass'):
            art16.save(art16.part_texture(iid), tb / f'{iid}.png')
            art.save(art.part_texture(iid), hb / f'{iid}.png')
        elif kind == 'block':
            m = re.search(r'(\d)$', iid)
            if m:
                art16.save(art16.compressed_block(int(m.group(1)), 'metal' in iid), tb / f'{iid}.png')
                art.save(art.compressed_block(int(m.group(1)), 'metal' in iid), hb / f'{iid}.png')
            elif iid == 'strange_matter':
                art16.save(art16.strange_matter(), tb / f'{iid}.png')
                art.save(art.strange_matter(), hb / f'{iid}.png')
        elif kind not in ('structure', 'fluid', 'machine') and kind not in BLOCK_KINDS:
            art16.save(art16.item_texture(iid, stage_of(name)), ti / f'{iid}.png')
            if iid in art.ITEM_ART:
                art.save(art.item_texture(iid, stage_of(name)), hi / f'{iid}.png')
    # ケーブルの被覆（電力が流れている間の芯の色つき）
    cable_glow = {'superconducting_cable': (220, 250, 255), 'topological_wire': (240, 220, 255), 'horizon_bus': (255, 255, 255)}
    for iid, core in CABLES.items():
        art16.save(art16.cable_texture(iid, core), tb / f'{iid}.png')
        art16.save(art16.cable_end(iid, core), tb / f'{iid}_end.png')
        if iid in cable_glow:
            art16.save(art16.cable_texture(iid, core, on=True, glow=cable_glow[iid]), tb / f'{iid}_on.png')
            art16.save(art16.cable_end(iid, core, on=True, glow=cable_glow[iid]), tb / f'{iid}_end_on.png')
    # 探索コンパスの針（16コマ）
    for k in range(16):
        art16.save(art16.compass_frame(k / 16), ti / f'explorer_compass_{k:02d}.png')
    black_hole_textures(art16, tb)
    manipulator_staff_texture(art16)
    # 遺構の建材など、まだのっぺりしている絵にも汚し（使用感）を入れる
    from PIL import Image
    for name, (iid, _, kind) in MOD.items():
        if kind in ('ruin_block', 'ruin_glass'):
            for path in [tb / f'{iid}.png'] + ([tb / 'ruin_cache_sealed.png'] if iid == 'ruin_cache' else []):
                if path.exists():
                    art16.save(art16.weather_existing(Image.open(path).convert('RGBA'), seed=len(iid)), path)


BLACK_HOLE_ITEMS = {
    # ID: (球の大きさ, 球のテクスチャ, 輪のテクスチャ, 輪が光るか)
    'singularity_core': (3, 'bh_dark', 'bh_ring_gold', True),
    'spent_singularity_core': (3, 'bh_grey', 'bh_ring_dull', False),
    'singularity_seed': (2, 'bh_dark', 'bh_ring_white', True),
    'dormant_singularity_seed': (2, 'bh_grey', 'bh_ring_dull', False),
    'unstable_wormhole_mouth': (3, 'bh_dark', 'bh_ring_magenta', True),
}


def black_hole_textures(art16, tb):
    from PIL import Image
    import random as _r
    rnd = _r.Random(3)
    for name, base, sheen in (('bh_dark', (8, 8, 12), (70, 40, 110)), ('bh_grey', (70, 70, 78), (120, 120, 130))):
        im = Image.new('RGBA', (16, 16), base + (255,))
        for _ in range(10):
            im.putpixel((rnd.randrange(16), rnd.randrange(16)), sheen + (255,))
        art16.save(im, tb / f'{name}.png')
    for name, c in (('bh_ring_gold', (255, 214, 140)), ('bh_ring_white', (240, 246, 255)), ('bh_ring_magenta', (255, 120, 220)),
                    ('bh_ring_dull', (110, 108, 112))):
        im = Image.new('RGBA', (16, 16), c + (255,))
        for x in range(16):
            im.putpixel((x, 7), tuple(min(255, v + 40) for v in c) + (255,))
            im.putpixel((x, 0), tuple(int(v * 0.7) for v in c) + (255,))
        art16.save(im, tb / f'{name}.png')


def black_hole_item_models():
    """ブラックホール関係のアイテムを立体に: 黒い球（1ドットずつの板を積む）と、少し傾いた光る輪。
    要素は重ならない（同じ向きの面が同じ平面に重ならない）。"""
    import math as _m
    for iid, (r, sphere, ring, glow) in BLACK_HOLE_ITEMS.items():
        c = 8
        els = []
        for y in range(-r, r):
            yc = y + 0.5
            half = max(0.5, round(_m.sqrt(max(0.0, r * r - yc * yc)) * 2) / 2)
            els.append({'from': [c - half, c + y, c - half], 'to': [c + half, c + y + 1, c + half],
                        'faces': {d: {'uv': [8 - half, 8 - half, 8 + half, 8 + half], 'texture': '#s'}
                                  for d in ('north', 'south', 'east', 'west', 'up', 'down')}})
        R = r + 2.5
        t = 0.5
        bars = [([c - R - 1, c - t, c - R - 1], [c - R, c + t, c + R + 1]),     # 左（角まで）
                ([c + R, c - t, c - R - 1], [c + R + 1, c + t, c + R + 1]),     # 右（角まで）
                ([c - R, c - t, c - R - 1], [c + R, c + t, c - R]),             # 手前（左右の間だけ）
                ([c - R, c - t, c + R], [c + R, c + t, c + R + 1])]             # 奥（左右の間だけ）
        for f, to in bars:
            e = {'from': f, 'to': to, 'rotation': {'origin': [8, 8, 8], 'axis': 'x', 'angle': 22.5},
                 'faces': {d: {'uv': [0, 6, 16, 9], 'texture': '#r'} for d in ('north', 'south', 'east', 'west', 'up', 'down')}}
            if glow:
                e['neoforge_data'] = {'block_light': 15, 'sky_light': 15}
            els.append(e)
        write_json(ASSETS / 'models' / 'item' / f'{iid}.json', {
            'parent': 'minecraft:block/block',
            'textures': {'particle': f'{MODID}:block/{sphere}', 's': f'{MODID}:block/{sphere}', 'r': f'{MODID}:block/{ring}'},
            'elements': els,
            'display': {'gui': {'rotation': [20, 30, 0], 'scale': [0.9, 0.9, 0.9]},
                        'ground': {'translation': [0, 3, 0], 'scale': [0.5, 0.5, 0.5]},
                        'thirdperson_righthand': {'rotation': [75, 45, 0], 'translation': [0, 2.5, 0], 'scale': [0.45, 0.45, 0.45]},
                        'firstperson_righthand': {'rotation': [0, 45, 0], 'scale': [0.5, 0.5, 0.5]},
                        'fixed': {'scale': [0.7, 0.7, 0.7]}},
        })

def manipulator_staff_texture(art16):
    """グラビトン・マニピュレーターの杖のテクスチャ（1枚にまとめ、uv は1ドット＝1ドット）。
    x 0..2 / y 0..10: 柄（左が明るく右が暗い金属）, x 3..7 / y 0..2: 握りの帯（側面）, x 3..7 / y 3..7: 帯の上下,
    x 8..12 / y 0..1: 襟（側面）, x 8..12 / y 2..6: 襟の上下, x 13..14 / y 0..2: 爪。"""
    im, d = art16.new()
    metal = (58, 60, 72)
    for y in range(10):
        for x, t in ((0, 0.18), (1, 0.0)):
            c = art16.lighten(metal, t) if t > 0 else metal
            if y in (0, 9):
                c = art16.darken(c, 0.25)
            im.putpixel((x, y), c + (255,))
    gold = (214, 182, 104)
    for y in range(2):
        for x in range(3, 7):
            im.putpixel((x, y), (art16.lighten(gold, 0.15) if y == 0 else art16.darken(gold, 0.12)) + (255,))
    for y in range(3, 7):
        for x in range(3, 7):
            edge = x in (3, 6) or y in (3, 6)
            im.putpixel((x, y), (art16.darken(gold, 0.18) if edge else gold) + (255,))
    collar = (86, 90, 108)
    for x in range(8, 12):
        im.putpixel((x, 0), art16.lighten(collar, 0.12) + (255,))
    for y in range(2, 6):
        for x in range(8, 12):
            edge = x in (8, 11) or y in (2, 5)
            c = art16.darken(collar, 0.2) if edge else collar
            if (x, y) in ((9, 3), (10, 4)):
                c = (120, 200, 255)                                   # 襟の上の小さな光点
            im.putpixel((x, y), c + (255,))
    for y in range(2):
        im.putpixel((13, y), (art16.lighten(gold, 0.1) if y == 0 else gold) + (255,))
    art16.save(im, ASSETS / 'textures' / 'item' / 'graviton_staff.png')


def manipulator_staff_model():
    """グラビトン・マニピュレーター: 先端に小さなブラックホールを抱えた杖（立体）。
    座標は整数（1ドット＝1）。同じ向きの面が同じ平面で重ならない。輪だけ傾けるので半ドットを使う。"""
    t = '#t'

    def box(f, to, uvs, skip=()):
        faces = {}
        for dname, uv in uvs.items():
            if dname not in skip:
                faces[dname] = {'uv': uv, 'texture': t}
        return {'from': f, 'to': to, 'faces': faces}

    def sides(uv):
        return {d: uv for d in ('north', 'south', 'east', 'west')}

    els = []
    # 柄: 2x2、y 0..9（上面は襟に隠れる）
    els.append(box([7, 0, 7], [9, 9, 9], {**sides([0, 0, 2, 9]), 'down': [0, 9, 2, 10]}))
    # 握りの帯: 4x4、y 2..4（柄を包む）
    els.append(box([6, 2, 6], [10, 4, 10], {**sides([3, 0, 7, 2]), 'up': [3, 3, 7, 7], 'down': [3, 3, 7, 7]}))
    # 襟: 4x4、y 9..10
    els.append(box([6, 9, 6], [10, 10, 10], {**sides([8, 0, 12, 1]), 'up': [8, 2, 12, 6], 'down': [8, 2, 12, 6]}))
    # 爪: 襟の四隅の外、y 10..12（球を下から抱える）
    for x, z in ((5, 5), (10, 5), (5, 10), (10, 10)):
        els.append(box([x, 10, z], [x + 1, 12, z + 1], {**sides([13, 0, 14, 2]), 'up': [13, 0, 14, 1], 'down': [13, 1, 14, 2]}))
    # ブラックホール: 1ドットずつの板を積んだ球（中心 y 13）
    for y0, y1, half in ((10, 11, 1), (11, 12, 2), (12, 14, 3), (14, 15, 2), (15, 16, 1)):
        u = 8 - half
        els.append({'from': [8 - half, y0, 8 - half], 'to': [8 + half, y1, 8 + half],
                    'faces': {dn: {'uv': [u, u, 16 - u, 16 - u] if dn in ('up', 'down') else [u, 8, 16 - u, 8 + (y1 - y0)],
                                   'texture': '#s'} for dn in ('north', 'south', 'east', 'west', 'up', 'down')}})
    # 光る輪（降着円盤）: 4本の棒を少し傾ける。角で重ならないよう、手前と奥は左右の間だけ
    c, R, h = 8, 4, 0.5
    bars = [([c - R - 1, 13 - h, c - R - 1], [c - R, 13 + h, c + R + 1]),
            ([c + R, 13 - h, c - R - 1], [c + R + 1, 13 + h, c + R + 1]),
            ([c - R, 13 - h, c - R - 1], [c + R, 13 + h, c - R]),
            ([c - R, 13 - h, c + R], [c + R, 13 + h, c + R + 1])]
    for f, to in bars:
        els.append({'from': f, 'to': to, 'rotation': {'origin': [8, 13, 8], 'axis': 'x', 'angle': 22.5},
                    'neoforge_data': {'block_light': 15, 'sky_light': 15},
                    'faces': {dn: {'uv': [0, 6, 16, 7], 'texture': '#r'}
                              for dn in ('north', 'south', 'east', 'west', 'up', 'down')}})
    write_json(ASSETS / 'models' / 'item' / 'graviton_manipulator.json', {
        'parent': 'minecraft:block/block',
        'textures': {'particle': f'{MODID}:item/graviton_staff', 't': f'{MODID}:item/graviton_staff',
                     's': f'{MODID}:block/bh_dark', 'r': f'{MODID}:block/bh_ring_white'},
        'elements': els,
        'display': {
            'gui': {'rotation': [25, -45, 0], 'translation': [0, 0, 0], 'scale': [0.85, 0.85, 0.85]},
            'ground': {'translation': [0, 2, 0], 'scale': [0.5, 0.5, 0.5]},
            'fixed': {'rotation': [0, 90, 0], 'scale': [0.8, 0.8, 0.8]},
            'thirdperson_righthand': {'rotation': [0, 0, 0], 'translation': [0, 3, 1], 'scale': [0.85, 0.85, 0.85]},
            'thirdperson_lefthand': {'rotation': [0, 0, 0], 'translation': [0, 3, 1], 'scale': [0.85, 0.85, 0.85]},
            'firstperson_righthand': {'rotation': [0, -20, 8], 'translation': [1, 3.5, 0], 'scale': [0.68, 0.68, 0.68]},
            'firstperson_lefthand': {'rotation': [0, 20, -8], 'translation': [1, 3.5, 0], 'scale': [0.68, 0.68, 0.68]},
        },
    })


# ホライズン・ウォーデンの部品とテクスチャの配置（HorizonWardenRenderer.createLayer と同じ）。
# 名前: (u, v, 幅, 高さ, 奥行き, 素材)
WARDEN_PARTS = {
    'head': (0, 0, 8, 8, 8, 'head'),
    'crest': (32, 0, 1, 4, 6, 'dark'),
    'chest': (0, 16, 12, 8, 7, 'chest'),
    'abdomen': (40, 16, 9, 6, 5, 'abdomen'),
    'core': (70, 16, 4, 4, 1, 'core'),
    'chest_plate': (0, 34, 13, 7, 1, 'armor'),
    'arm': (0, 48, 5, 8, 5, 'dark'),
    'forearm': (24, 48, 6, 9, 6, 'forearm'),
    'blade': (52, 48, 1, 12, 3, 'blade'),
    'pad': (0, 64, 7, 4, 7, 'armor'),
    'thigh': (64, 48, 5, 6, 5, 'dark'),
    'shin': (88, 48, 6, 6, 6, 'shin'),
    'vane': (32, 64, 1, 14, 2, 'vane'),
    'shard': (44, 64, 5, 6, 1, 'armor'),
}


def cube_faces(u, v, w, h, d):
    """立方体の展開図の各面（x0, y0, x1, y1、右と下は含まない）。front はモデルの正面（-Z）。"""
    return {
        'up': (u + d, v, u + d + w, v + d),
        'down': (u + d + w, v, u + d + 2 * w, v + d),
        'side_a': (u, v + d, u + d, v + d + h),
        'front': (u + d, v + d, u + d + w, v + d + h),
        'side_b': (u + d + w, v + d, u + 2 * d + w, v + d + h),
        'back': (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
    }


# 警備ドローンの部品とテクスチャの配置（SecurityDroneRenderer.createLayer と同じ）。名前: (u, v, 幅, 高さ, 奥行き, 素材)
DRONE_PARTS = {
    'core': (0, 0, 6, 5, 6, 'shell'),
    'belt': (0, 12, 7, 1, 7, 'belt'),
    'cap': (28, 0, 4, 1, 4, 'shell'),
    'antenna': (44, 0, 1, 3, 1, 'antenna'),
    'lens': (48, 0, 3, 3, 1, 'lens'),
    'barrel': (56, 0, 1, 1, 3, 'barrel'),
    'ring_x': (0, 22, 12, 1, 1, 'ring'),
    'ring_z': (0, 26, 1, 1, 10, 'ring'),
    'strut': (28, 22, 1, 1, 4, 'dark'),
    'pod': (40, 22, 3, 2, 3, 'pod'),
    'blade': (28, 30, 6, 0, 1, 'blade'),
}


def drone_textures(te, save):
    """警備ドローン: 白い胴と上のふた、暗い帯・腕・砲身、灰色の輪。光る線は別の画像（強さの色）。"""
    import random as _r
    from PIL import Image
    rng = _r.Random(7)
    shell = (232, 234, 238)
    shell_sh = (200, 204, 212)
    dark = (52, 56, 66)
    ring = (176, 182, 194)
    base = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    glow = Image.new('RGBA', (64, 64), (0, 0, 0, 0))

    def mix(c, k):
        return tuple(max(0, min(255, int(x + k))) for x in c)

    def fill(rect, col, edge=True):
        x0, y0, x1, y1 = rect
        for y in range(y0, y1):
            for x in range(x0, x1):
                c = mix(col, rng.randint(-4, 4))
                if edge and x1 - x0 >= 3 and y1 - y0 >= 3:
                    if y == y0 or x == x0:
                        c = mix(c, 12)
                    elif y == y1 - 1 or x == x1 - 1:
                        c = mix(c, -16)
                base.putpixel((x, y), c + (255,))

    def lit(rect):
        x0, y0, x1, y1 = rect
        for y in range(y0, y1):
            for x in range(x0, x1):
                glow.putpixel((x, y), (255, 255, 255, 255))

    for name, (u, v, w, h, d, mat) in DRONE_PARTS.items():
        f = cube_faces(u, v, w, h, d)
        col = {'shell': shell, 'ring': ring, 'lens': (14, 16, 20), 'blade': (70, 74, 84)}.get(mat, dark)
        for k, r in f.items():
            fill(r, shell_sh if mat == 'shell' and k in ('side_a', 'side_b', 'back') else col)
        fx0, fy0, fx1, fy1 = f['front']
        if name == 'core':
            # 正面のパネルの継ぎ目と、側面の通気口
            for x in range(fx0, fx1):
                base.putpixel((x, fy0 + 3), mix(shell, -50) + (255,))
            for side in ('side_a', 'side_b'):
                x0, y0, x1, y1 = f[side]
                for x in range(x0 + 1, x1 - 1, 2):
                    base.putpixel((x, y0 + 1), mix(shell, -70) + (255,))
        elif name == 'lens':
            lit(f['front'])
        elif name == 'belt':
            for k in ('front', 'back', 'side_a', 'side_b'):
                x0, y0, x1, y1 = f[k]
                cx = (x0 + x1) // 2
                lit((cx - 1, y0, cx + 2, y1))
        elif name == 'antenna':
            lit(f['up'])
        elif name == 'barrel':
            lit(f['front'])
        elif mat == 'ring':
            x0, y0, x1, y1 = f['up']
            for x in range(x0, x1):
                if (x - x0) % 3 == 1:
                    glow.putpixel((x, y0), (255, 255, 255, 255))
        elif name == 'pod':
            x0, y0, x1, y1 = f['down']
            lit((x0 + 1, y0 + 1, x1 - 1, y1 - 1))
    save(base, te / 'security_drone.png')
    for tier, c in {1: (120, 210, 240), 2: (240, 180, 80), 3: (232, 96, 96)}.items():
        im = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
        for y in range(64):
            for x in range(64):
                if glow.getpixel((x, y))[3]:
                    im.putpixel((x, y), c + (255,))
        save(im, te / f'security_drone_glow_{tier}.png')
    for old in (1, 2, 3):
        p = te / f'security_drone_{old}.png'
        if p.exists():
            p.unlink()


def warden_textures(te, save):
    """ホライズン・ウォーデン: 黒い躯体（継ぎ目と擦れ）、白い陶磁の外装（金の縁）、鋼の刃。光る線は別の画像（フェーズで色が変わる）。"""
    import random as _r
    from PIL import Image
    rng = _r.Random(42)
    dark = (46, 48, 58)
    seam = (28, 29, 36)
    hi = (74, 78, 92)
    armor = (228, 230, 234)
    armor_sh = (192, 196, 205)
    gold = (212, 178, 98)
    gold_sh = (160, 128, 64)
    steel = (150, 156, 170)

    def mix(c, k):
        return tuple(max(0, min(255, int(x + k))) for x in c)

    base = Image.new('RGBA', (128, 128), (0, 0, 0, 0))
    glow = Image.new('RGBA', (128, 128), (0, 0, 0, 0))
    G = (255, 255, 255, 255)        # 光の位置（色はあとでフェーズごとに付ける）

    def px(im, x, y, c):
        im.putpixel((x, y), c if len(c) == 4 else c + (255,))

    def face(rect, col, edge=True, noise=5):
        x0, y0, x1, y1 = rect
        for y in range(y0, y1):
            for x in range(x0, x1):
                c = mix(col, rng.randint(-noise, noise))
                if edge and x1 - x0 >= 3 and y1 - y0 >= 3:
                    if y == y0 or x == x0:
                        c = mix(c, 14)
                    elif y == y1 - 1 or x == x1 - 1:
                        c = mix(c, -16)
                px(base, x, y, c)

    def wear(rect, col, n):
        x0, y0, x1, y1 = rect
        for _ in range(n):
            if x1 - x0 > 2 and y1 - y0 > 2:
                px(base, rng.randrange(x0 + 1, x1 - 1), rng.randrange(y0 + 1, y1 - 1), col)

    def glow_line(points):
        for x, y in points:
            px(glow, x, y, G)

    for name, (u, v, w, h, d, mat) in WARDEN_PARTS.items():
        f = cube_faces(u, v, w, h, d)
        if mat in ('armor',):
            for k, r in f.items():
                face(r, armor if k in ('front', 'up') else armor_sh)
                x0, y0, x1, y1 = r
                if x1 - x0 >= 3 and y1 - y0 >= 3:
                    for x in range(x0, x1):
                        px(base, x, y0, gold)
                        px(base, x, y1 - 1, gold_sh)
                    for y in range(y0, y1):
                        px(base, x0, y, gold)
                        px(base, x1 - 1, y, gold_sh)
                wear(r, mix(armor, -40), 2)
            if name == 'chest_plate':
                x0, y0, x1, y1 = f['front']
                cx, cy = (x0 + x1) // 2, (y0 + y1) // 2
                for dx, dy in ((-1, -1), (0, -2), (1, -1), (2, 0), (1, 1), (0, 2), (-1, 1), (-2, 0)):
                    px(base, cx + dx, cy + dy, gold)
            if name == 'shard':
                x0, y0, x1, y1 = f['front']
                glow_line([(x, y0) for x in range(x0, x1)] + [(x0, y) for y in range(y0, y1)])
            continue
        col = {'blade': steel, 'vane': mix(dark, 8)}.get(mat, dark)
        for k, r in f.items():
            face(r, col)
            wear(r, hi, 1)
        fx0, fy0, fx1, fy1 = f['front']
        if mat == 'head':
            # 白い面頬と、目の帯
            for y in range(fy0 + 1, fy1 - 1):
                for x in range(fx0 + 1, fx1 - 1):
                    px(base, x, y, mix(armor, rng.randint(-6, 4)))
            for x in range(fx0 + 1, fx1 - 1):
                px(base, x, fy0 + 3, (12, 14, 18))
                px(base, x, fy0 + 4, (12, 14, 18))
            glow_line([(x, fy0 + 3) for x in range(fx0 + 2, fx1 - 2)] + [(x, fy0 + 4) for x in range(fx0 + 1, fx1 - 1)])
            # 側面の通気口
            for side in ('side_a', 'side_b'):
                x0, y0, x1, y1 = f[side]
                for y in (y0 + 3, y0 + 5):
                    for x in range(x0 + 2, x1 - 2):
                        px(base, x, y, seam)
            # 上面の稜線
            x0, y0, x1, y1 = f['up']
            for y in range(y0, y1):
                px(base, (x0 + x1) // 2, y, hi)
        elif name == 'crest':
            glow_line([(x, y) for x, y in ((fx0, y) for y in range(fy0, fy1))])
            x0, y0, x1, y1 = f['up']
            glow_line([(x0, y) for y in range(y0, y1)])
        elif mat == 'chest':
            cx = (fx0 + fx1) // 2
            for y in range(fy0, fy1):
                px(base, cx, y, seam)
            for x in range(fx0, fx1):
                px(base, x, fy0 + 5, seam)
            # 肩から炉心へ集まる光の線（V 字）
            pts = []
            for k in range(5):
                pts += [(fx0 + 1 + k, fy0 + 1 + k), (fx1 - 2 - k, fy0 + 1 + k)]
            glow_line(pts)
        elif mat == 'abdomen':
            for y in range(fy0 + 1, fy1, 2):
                for x in range(fx0, fx1):
                    px(base, x, y, seam)
            glow_line([((fx0 + fx1) // 2, y) for y in range(fy0, fy1)])
        elif mat == 'core':
            for y in range(fy0, fy1):
                for x in range(fx0, fx1):
                    px(base, x, y, (6, 6, 10))
            glow_line([(x, fy0) for x in range(fx0, fx1)] + [(x, fy1 - 1) for x in range(fx0, fx1)]
                      + [(fx0, y) for y in range(fy0, fy1)] + [(fx1 - 1, y) for y in range(fy0, fy1)])
        elif mat == 'forearm':
            for side in ('side_a', 'side_b'):
                x0, y0, x1, y1 = f[side]
                glow_line([((x0 + x1) // 2, y) for y in range(y0 + 1, y1 - 1)])
            for x in range(fx0, fx1):
                px(base, x, fy0 + 2, seam)
        elif mat == 'blade':
            glow_line([(x, y) for x in range(fx0, fx1) for y in range(fy0, fy1)])
            for side in ('side_a', 'side_b'):
                x0, y0, x1, y1 = f[side]
                for y in range(y0, y1):
                    px(base, x0, y, mix(steel, 40))
        elif mat == 'shin':
            for x in range(fx0, fx1):
                px(base, x, fy0, mix(dark, 20))
                px(base, x, fy0 + 1, mix(dark, 12))
            glow_line([(x, fy0 + 2) for x in range(fx0 + 1, fx1 - 1)])
        elif mat == 'vane':
            for side in ('side_a', 'side_b'):
                x0, y0, x1, y1 = f[side]
                glow_line([(x0, y) for y in range(y0, y1)])
            x0, y0, x1, y1 = f['up']
            glow_line([(x, y) for x in range(x0, x1) for y in range(y0, y1)])
    save(base, te / 'horizon_warden.png')
    for phase, c in {1: (150, 230, 255), 2: (110, 215, 255), 3: (255, 90, 90)}.items():
        im = Image.new('RGBA', (128, 128), (0, 0, 0, 0))
        for y in range(128):
            for x in range(128):
                if glow.getpixel((x, y))[3]:
                    im.putpixel((x, y), c + (255,))
        save(im, te / f'horizon_warden_glow_{phase}.png')
    for old in (1, 2, 3):
        p = te / f'horizon_warden_{old}.png'
        if p.exists():
            p.unlink()



# 効果音: 名前 → (ファイル, 字幕（日本語, 英語）, 聞こえる距離)。ファイルは src/main/resources/assets/singulo/sounds/ の下。
# 名前は SinguloSounds の登録名と同じ（"_running" で終わるものは装置が動いている間のくり返し）。
SOUNDS = {
    'black_hole.ambient': ('black_hole/ambient_loop', 'ブラックホールがうなる', 'Black hole hums', 32),
    'black_hole.formation': ('black_hole/formation', 'ブラックホールができる', 'Black hole forms', 48),
    'kiln_running': ('machine/kiln_running', '焼成炉が燃える', 'Kiln roars', 16),
    'compressor_running': ('machine/compressor_running', '圧縮機がプレスする', 'Compressor presses', 16),
    'electrolyzer_running': ('machine/electrolyzer_running', '電解槽が泡立つ', 'Electrolyzer bubbles', 16),
    'archive_terminal_running': ('machine/archive_terminal_running', 'アーカイブ端末が読み込む', 'Archive terminal reads', 16),
    'precision_assembler_running': ('machine/precision_assembler_running', '精密組立台が組み立てる', 'Assembler works', 16),
    'catalytic_reactor_running': ('machine/catalytic_reactor_running', '触媒反応器が反応する', 'Catalytic reactor reacts', 16),
    'cooling_tower_running': ('machine/cooling_tower_running', '冷却塔が冷やす', 'Cooling tower chills', 24),
    'particle_accelerator_running': ('machine/particle_accelerator_running', '粒子加速器がうなる', 'Particle accelerator hums', 32),
    'entanglement_synthesizer_running': ('machine/entanglement_synthesizer_running', '量子もつれ合成器が共鳴する', 'Entanglement synthesizer resonates', 16),
    'laser_cooler_running': ('machine/laser_cooler_running', 'レーザー冷却器が動く', 'Laser cooler runs', 16),
    'echo_resonator_running': ('machine/echo_resonator_running', '残響共鳴器が響く', 'Echo resonator rings', 16),
    'degenerate_compactor_running': ('machine/degenerate_compactor_running', '縮退圧縮炉が押しつぶす', 'Degenerate compactor crushes', 24),
    'casimir_cavity_running': ('machine/casimir_cavity_running', 'C空洞が震える', 'C-Cavity vibrates', 24),
    'time_crystal_incubator_running': ('machine/time_crystal_incubator_running', '時間結晶育成槽が育てる', 'Time crystal incubator grows', 16),
    'singularity_encapsulator_running': ('machine/singularity_encapsulator_running', '特異点封入台が封じ込める', 'Singularity encapsulator works', 20),
    'thermoelectric_generator_running': ('machine/thermoelectric_generator_running', '熱電発電機が発電する', 'Thermoelectric generator hums', 12),
    'cryogenic_turbine_running': ('machine/cryogenic_turbine_running', '極低温タービンが回る', 'Cryogenic turbine spins', 20),
    'quantum_heat_engine_running': ('machine/quantum_heat_engine_running', '量子熱機関が動く', 'Quantum heat engine runs', 16),
    'smes_cell_running': ('machine/smes_cell_running', 'SMESセルがうなる', 'SMES cell hums', 12),
    'muon_collector_running': ('machine/muon_collector_running', 'ミュオン収集器が集める', 'Muon collector gathers', 12),
    'worldline_anchor_running': ('machine/worldline_anchor_running', 'ワールドライン・アンカーが固定する', 'Worldline anchor holds', 16),
    'advanced_worldline_anchor_running': ('machine/advanced_worldline_anchor_running', '上位アンカーが固定する', 'Advanced anchor holds', 20),
    'advanced_worldline_anchor_embed': ('machine/advanced_worldline_anchor_embed', 'コアが埋め込まれる', 'Core embeds', 24),
    'inertial_stabilizer_running': ('machine/inertial_stabilizer_running', '慣性スタビライザーが場を張る', 'Inertial stabilizer hums', 16),
    'degenerate_furnace_running': ('machine/degenerate_furnace_running', '縮退熱炉が燃える', 'Degenerate furnace burns', 28),
    'degenerate_furnace_press': ('machine/degenerate_furnace_press', 'ピストンが押しつぶす', 'Pistons crush fuel', 28),
    'shield_tower_running': ('machine/shield_tower_running', 'シールドがうなる', 'Shield hums', 32),
    'tipler_cylinder_running': ('machine/tipler_cylinder_running', 'Tシリンダーが回る', 'T-Cylinder spins', 28),
    'wormhole_generator_charge': ('machine/wormhole_generator_charge', 'ワームホール生成器がためる', 'Wormhole generator charges', 32),
    'wormhole_generator_open': ('machine/wormhole_generator_open', 'ワームホールが開く', 'Wormhole opens', 32),
    'wormhole_mouth_ambient': ('machine/wormhole_mouth_ambient', 'ワームホールがざわめく', 'Wormhole murmurs', 20),
    'wormhole_stabilizer_running': ('machine/wormhole_stabilizer_running', 'ワームホール固定化装置が動く', 'Wormhole stabilizer runs', 16),
    'halo_collector_running': ('machine/halo_collector_running', 'ハロー捕集器が集める', 'Halo collector gathers', 16),
    'containment_tank_running': ('machine/containment_tank_running', '閉じ込めタンクがうなる', 'Containment tank hums', 12),
    'penrose_reactor_ignition': ('machine/penrose_reactor_ignition', 'Pリアクターが点火を始める', 'P-Reactor ignition begins', 48),
    'probe_station_launch': ('machine/probe_station_launch', '探査機が飛び立つ', 'Probe launches', 24),
    'probe_station_return': ('machine/probe_station_return', '探査機が帰ってくる', 'Probe returns', 24),
    'gravitational_wave_detector_ping': ('machine/gravitational_wave_detector_ping', '重力波検出器が反応する', 'Detector pings', 16),
    'multiblock_formed': ('machine/multiblock_formed', 'マルチブロックが完成する', 'Multiblock formed', 24),
    'graviton_manipulator_active': ('machine/graviton_manipulator_active', '重力を操る', 'Gravity manipulated', 16),
    'graviton_manipulator_charge': ('machine/graviton_manipulator_charge', '重力をためる', 'Gravity charges', 16),
    'graviton_manipulator_throw': ('machine/graviton_manipulator_throw', '投げ飛ばす', 'Hurled', 24),
}


def sounds_json():
    """assets/singulo/sounds.json と字幕。音のファイルがそろっているかも確かめる。"""
    base = Path(__file__).resolve().parent.parent / 'src' / 'main' / 'resources' / 'assets' / MODID / 'sounds'
    out = {}
    missing = []
    for name, (path, ja, en, dist) in SOUNDS.items():
        if not (base / f'{path}.ogg').exists():
            missing.append(path)
        out[name] = {'subtitle': f'subtitles.{MODID}.{name}',
                     'sounds': [{'name': f'{MODID}:{path}', 'attenuation_distance': dist}]}
        UI_LANG[f'subtitles.{MODID}.{name}'] = (ja, en)
    unused = sorted(str(p.relative_to(base)).replace('\\', '/')[:-4] for p in base.rglob('*.ogg')
                    if str(p.relative_to(base)).replace('\\', '/')[:-4] not in {v[0] for v in SOUNDS.values()})
    if missing:
        raise SystemExit(f'効果音のファイルがない: {missing}')
    if unused:
        print(f'  使っていない効果音のファイル: {unused}')
    write_json(ASSETS / 'sounds.json', out)


def compass_item_model():
    """探索コンパス: 針の向き（minecraft:angle）で16コマを切り替える。"""
    overrides = []
    n = 16
    for k in range(n):
        overrides.append({'predicate': {'angle': max(0.0, k / n - 1 / (2 * n))},
                          'model': f'{MODID}:item/explorer_compass_{k:02d}'})
        write_json(ASSETS / 'models' / 'item' / f'explorer_compass_{k:02d}.json',
                   {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{MODID}:item/explorer_compass_{k:02d}'}})
    overrides.append({'predicate': {'angle': 1 - 1 / (2 * n)}, 'model': f'{MODID}:item/explorer_compass_00'})
    write_json(ASSETS / 'models' / 'item' / 'explorer_compass.json', {
        'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{MODID}:item/explorer_compass_00'}, 'overrides': overrides})

def mid_entity(eid):
    return f'{MODID}:{eid}'


def tags():
    t = RES / 'data'
    write_json(t / 'c' / 'tags' / 'item' / 'ingots' / 'steel.json', {'values': [mid('鋼鉄インゴット')]})
    write_json(t / 'c' / 'tags' / 'item' / 'ingots.json', {'values': [mid('鋼鉄インゴット')]})
    write_json(DATA / 'tags' / 'item' / 'star_core.json',
               {'values': ['minecraft:nether_star', mid('人工星核')]})
    metal_items = ['#c:storage_blocks/iron', '#c:storage_blocks/copper', '#c:storage_blocks/gold',
                   {'id': '#c:storage_blocks/steel', 'required': False},
                   {'id': '#c:storage_blocks/tin', 'required': False},
                   {'id': '#c:storage_blocks/lead', 'required': False},
                   {'id': '#c:storage_blocks/silver', 'required': False},
                   {'id': '#c:storage_blocks/aluminum', 'required': False},
                   {'id': '#c:storage_blocks/osmium', 'required': False},
                   {'id': '#c:storage_blocks/uranium', 'required': False},
                   'minecraft:netherite_block']
    write_json(DATA / 'tags' / 'item' / 'metal_core.json', {'values': metal_items})
    for _, cat in ACCEPTS_SPENT:
        write_json(DATA / 'tags' / 'item' / f'{MOD[cat][0]}s.json', {'values': [mid(cat), mid(SPENT[cat])]})
    write_json(DATA / 'tags' / 'item' / 'helium_bearing_stone.json', {'values': [
        'minecraft:deepslate', 'minecraft:cobbled_deepslate', 'minecraft:basalt', 'minecraft:smooth_basalt']})
    write_json(DATA / 'tags' / 'block' / 'ruin_blocks.json',
               {'values': [mid(n) for n, v in MOD.items() if v[2] in ('ruin_block', 'ruin_glass')]})
    write_json(DATA / 'tags' / 'entity_type' / 'gravity_immune.json', {'values': [
        'minecraft:ender_dragon', 'minecraft:wither', 'minecraft:warden', 'minecraft:elder_guardian',
        'minecraft:enderman', 'minecraft:shulker', mid_entity('horizon_warden')]})
    write_json(t / 'c' / 'tags' / 'entity_type' / 'bosses.json', {'values': [mid_entity('horizon_warden')]})
    # 潮汐ダメージ（ウォーデンの特異点、のちにPリアクターの潮汐帯）は防具を無視する
    write_json(DATA / 'damage_type' / 'tidal.json', {'message_id': 'singulo.tidal', 'exhaustion': 0.0, 'scaling': 'never'})
    # 事象の地平線: どんな守りも効かない（不死のトーテムも、無敵も、耐性も）
    write_json(DATA / 'damage_type' / 'event_horizon.json',
               {'message_id': 'singulo.event_horizon', 'exhaustion': 0.0, 'scaling': 'never'})
    horizon_tags = ['bypasses_armor', 'bypasses_shield', 'bypasses_invulnerability', 'bypasses_cooldown',
                    'bypasses_effects', 'bypasses_resistance', 'bypasses_enchantments', 'bypasses_wolf_armor',
                    'no_knockback', 'always_kills_armor_stands', 'no_impact']
    for tag in horizon_tags:
        values = [f'{MODID}:event_horizon']
        if tag in ('bypasses_armor', 'bypasses_shield'):
            values.insert(0, f'{MODID}:tidal')
        write_json(t / 'minecraft' / 'tags' / 'damage_type' / f'{tag}.json', {'values': values})
    blocks = [mid(n) for n, v in MOD.items() if v[2] in BLOCK_KINDS]
    write_json(t / 'minecraft' / 'tags' / 'block' / 'mineable' / 'pickaxe.json', {'values': blocks})
    write_json(t / 'minecraft' / 'tags' / 'block' / 'needs_iron_tool.json',
               {'values': [mid(n) for n, v in MOD.items() if v[2] == 'block']})
    # 単純ブロックのドロップ。中身を持ち運ぶブロックはブロックエンティティのデータ成分を写す
    copy_components = {'gravitational_containment_tank': [f'{MODID}:dark_matter'],
                       'wormhole_mouth': [f'{MODID}:wormhole']}
    for n, (iid, _, kind) in MOD.items():
        if kind in BLOCK_KINDS and iid != 'strangelet':
            entry = {'type': 'minecraft:item', 'name': mid(n)}
            if iid in copy_components:
                entry['functions'] = [{'function': 'minecraft:copy_components', 'source': 'block_entity',
                                       'include': copy_components[iid]}]
            write_json(DATA / 'loot_table' / 'blocks' / f'{iid}.json', {
                'type': 'minecraft:block',
                'pools': [{'rolls': 1, 'entries': [entry],
                           'conditions': [{'condition': 'minecraft:survives_explosion'}]}],
            })


RUIN_PLACEMENT = {
    # 遺構ID: (間隔, 最小間隔, salt, バイオーム)
    'observation_post': (24, 8, 19370501, ['minecraft:plains', 'minecraft:sunflower_plains', 'minecraft:desert',
                                           'minecraft:savanna', 'minecraft:savanna_plateau', 'minecraft:snowy_plains',
                                           'minecraft:meadow', 'minecraft:badlands']),
    'research_building': (32, 10, 19370502, ['#minecraft:is_forest', '#minecraft:is_taiga', 'minecraft:plains',
                                             'minecraft:windswept_hills', 'minecraft:meadow', 'minecraft:cherry_grove']),
    'culture_facility': (36, 12, 19370503, ['#minecraft:is_overworld']),
    'final_lab': (80, 32, 19370504, ['minecraft:plains', 'minecraft:desert', 'minecraft:savanna', 'minecraft:snowy_plains',
                                     'minecraft:badlands', 'minecraft:meadow', '#minecraft:is_forest', '#minecraft:is_taiga']),
}


def ruins_data():
    """遺構の構造物・配置・中身（loot table）。中身の数は recipes.py の EXPLORE（1回の遠征で拾える数）。"""
    ruins.write_all(DATA / 'structure' / 'ruins')
    for rid, (make, placement) in ruins.RUINS.items():
        spacing, separation, salt, biomes = RUIN_PLACEMENT[rid]
        write_json(DATA / 'tags' / 'worldgen' / 'biome' / 'has_structure' / f'{rid}.json', {'values': biomes})
        write_json(DATA / 'worldgen' / 'template_pool' / 'ruins' / f'{rid}.json', {
            'fallback': 'minecraft:empty',
            'elements': [{'weight': 1, 'element': {
                'element_type': 'minecraft:single_pool_element', 'location': f'{MODID}:ruins/{rid}',
                'projection': 'rigid', 'processors': 'minecraft:empty'}}],
        })
        structure = {
            'type': 'minecraft:jigsaw',
            'biomes': f'#{MODID}:has_structure/{rid}',
            'step': 'surface_structures' if placement == 'surface' else 'underground_structures',
            'spawn_overrides': {},
            'terrain_adaptation': 'beard_thin' if placement == 'surface' else 'none',
            'start_pool': f'{MODID}:ruins/{rid}',
            'size': 1,
            'max_distance_from_center': 80,
            'use_expansion_hack': False,
        }
        if placement == 'surface':
            structure['start_height'] = {'absolute': 0}
            structure['project_start_to_heightmap'] = 'WORLD_SURFACE_WG'
        else:
            # 深層岩の層（Y=-48〜-24）に埋める
            structure['start_height'] = {'type': 'minecraft:uniform',
                                         'min_inclusive': {'absolute': -48}, 'max_inclusive': {'absolute': -24}}
        write_json(DATA / 'worldgen' / 'structure' / f'{rid}.json', structure)
        write_json(DATA / 'worldgen' / 'structure_set' / f'{rid}.json', {
            'structures': [{'structure': f'{MODID}:{rid}', 'weight': 1}],
            'placement': {'type': 'minecraft:random_spread', 'spacing': spacing, 'separation': separation, 'salt': salt},
        })
    write_json(DATA / 'tags' / 'worldgen' / 'structure' / 'ruins.json',
               {'values': [f'{MODID}:{rid}' for rid in ruins.RUINS]})
    write_json(DATA / 'tags' / 'worldgen' / 'structure' / 'gravity_anomalies.json', {'values': [f'{MODID}:final_lab']})

    # 中身: 遺構ごとに1つの保管庫へ、1回の遠征ぶんを入れる。一回限りのもの（特異点の種）は別表で初回だけ
    pools, first = {}, {}
    for name, info in EXPLORE.items():
        rid = ruins.RUIN_IDS[info['ruin']]
        lo, hi = info['per_run']
        pool = {'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': mid(name), 'functions': [
            {'function': 'minecraft:set_count', 'count': lo if lo == hi else
             {'type': 'minecraft:uniform', 'min': lo, 'max': hi}}]}]}
        (first if info['kind'] == 'once' else pools).setdefault(rid, []).append(pool)
    # 記録片（旧文明の記録）。深い遺構ほど多い
    fragments = {'observation_post': (1, 2), 'research_building': (1, 2), 'culture_facility': (2, 3), 'final_lab': (2, 3)}
    for rid, (lo, hi) in fragments.items():
        pools.setdefault(rid, []).append({'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': mid('記録片'), 'functions': [
            {'function': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': lo, 'max': hi}}]}]})
    for rid, plist in pools.items():
        write_json(DATA / 'loot_table' / 'ruins' / f'{rid}.json', {'type': 'minecraft:chest', 'pools': plist})
    for rid, plist in first.items():
        write_json(DATA / 'loot_table' / 'ruins' / f'{rid}_first.json', {'type': 'minecraft:chest', 'pools': plist})


def mass_values():
    """設計書「質量値の既定値と設定」の表。1ブロックあたり。インゴット・粉はブロックの1/9。"""
    entries = [
        ('#minecraft:logs', 0.25), ('#minecraft:planks', 0.25), ('#minecraft:leaves', 0.25),
        ('#minecraft:saplings', 0.25), ('#c:crops', 0.25), ('#c:seeds', 0.25),
        ('#minecraft:dirt', 0.5), ('#c:sands', 0.5), ('#c:gravels', 0.5), ('minecraft:snow_block', 0.5),
        ('minecraft:clay', 0.5), ('minecraft:mud', 0.5),
        ('#c:cobblestones', 1), ('#c:stones', 1), ('#minecraft:stone_bricks', 1), ('#c:netherracks', 1),
        ('#c:sandstone/blocks', 1), ('minecraft:end_stone', 1), ('minecraft:tuff', 1), ('minecraft:calcite', 1),
        ('minecraft:andesite', 1), ('minecraft:diorite', 1), ('minecraft:granite', 1),
        ('minecraft:deepslate', 1.2), ('minecraft:cobbled_deepslate', 1.2), ('minecraft:basalt', 1.2),
        ('minecraft:smooth_basalt', 1.2), ('minecraft:blackstone', 1.2), ('#c:obsidians', 1.2),
        ('#c:storage_blocks/tin', 2.7), ('#c:storage_blocks/aluminum', 2.7),
        ('#c:storage_blocks/iron', 3), ('#c:storage_blocks/copper', 3), ('#c:storage_blocks/steel', 3),
        ('#c:storage_blocks/silver', 4), ('#c:storage_blocks/lead', 4), ('#c:storage_blocks/uranium', 7),
        ('#c:storage_blocks/gold', 7), ('#c:storage_blocks/osmium', 8), ('minecraft:netherite_block', 20),
        ('#c:ingots/iron', 3 / 9), ('#c:ingots/copper', 3 / 9), ('#c:ingots/steel', 3 / 9), ('#c:ingots/gold', 7 / 9),
        ('#c:ingots/netherite', 20 / 9),
        (mid('圧縮ブロックLv1'), 9), (mid('圧縮ブロックLv2'), 81), (mid('圧縮ブロックLv3'), 729),
        (mid('金属圧縮ブロックLv1'), 9), (mid('金属圧縮ブロックLv2'), 81),
        (mid('縮退物質殻'), 6561), (mid('質量ペレット'), 16), (mid('ストレンジ物質'), 100),
    ]
    out = []
    for k, v in entries:
        e = {'tag': k[1:]} if k.startswith('#') else {'item': k}
        e['mass'] = round(v, 4)
        out.append(e)
    write_json(DATA / 'mass_values' / 'default.json', {'entries': out})


def thermal():
    """設計書「段階1の発電: 熱電発電機」の表。"""
    write_json(DATA / 'thermal' / 'default.json', {
        'hot': [
            {'block': 'minecraft:campfire', 'temperature': 800, 'requires_lit': True},
            {'block': 'minecraft:soul_campfire', 'temperature': 800, 'requires_lit': True},
            {'block': 'minecraft:magma_block', 'temperature': 1000},
            {'block': 'minecraft:lava', 'temperature': 1300},
            {'block': mid('焼成炉'), 'temperature': 900, 'requires_lit': True},
        ],
        'cold': [
            {'block': 'minecraft:water', 'temperature': 293, 'tolerance': 200, 'becomes': 'minecraft:air'},
            {'block': 'minecraft:snow_block', 'temperature': 268, 'tolerance': 300, 'becomes': 'minecraft:water'},
            {'block': 'minecraft:ice', 'temperature': 263, 'tolerance': 400, 'becomes': 'minecraft:water'},
            {'block': 'minecraft:packed_ice', 'temperature': 253, 'tolerance': 600, 'becomes': 'minecraft:ice'},
            {'block': 'minecraft:blue_ice', 'temperature': 243, 'tolerance': 900, 'becomes': 'minecraft:packed_ice'},
        ],
    })


# ---------------------------------------------------------------- Java

def java_content():
    lines = []
    simple = []
    for name, (iid, _, kind) in MOD.items():
        if kind in ('item', 'planned', 'uses', 'catalyst'):
            simple.append((iid, kind, stage_of(name), max_uses(name)))
    blocks = [(iid, stage_of(n)) for n, (iid, _, kind) in MOD.items() if kind == 'block']
    fluids = [(iid, stage_of(n)) for n, (iid, _, kind) in MOD.items() if kind == 'fluid']
    fluid_colors = {'dark_matter': 0xAA2A1E3C, 'hydrogen': 0xCCE6F5FF, 'oxygen': 0xCCF5E1E1, 'liquid_nitrogen': 0xDDBFEFFF,
                    'helium': 0xCCF5F0C8, 'liquid_helium': 0xDDE6D2FF, 'axion_condensate': 0xDDA078E6}
    gases = {'hydrogen', 'oxygen', 'helium', 'dark_matter'}
    # 復元・劣化の対応
    worn = {}
    for restored, orig in RESTORED.items():
        worn[MOD[restored][0]] = MOD[orig][0]

    lines.append(f'package {PKG}.generated;')
    lines.append('')
    lines.append('import java.util.List;')
    lines.append('import java.util.Map;')
    lines.append('')
    lines.append('/** tools/gen_data.py が recipes.py から生成。手で編集しない。 */')
    lines.append('public final class GeneratedContent {')
    lines.append('    private GeneratedContent() {}')
    lines.append('')
    lines.append('    /** kind: item / planned / uses / catalyst。maxUses は使用回数（触媒は寿命tick）。 */')
    lines.append('    public record ItemDef(String id, String kind, int stage, int maxUses) {}')
    lines.append('    public record BlockDef(String id, int stage) {}')
    lines.append('    public record FluidDef(String id, int stage, int color, boolean gas) {}')
    lines.append('')
    lines.append('    public static final List<ItemDef> ITEMS = List.of(')
    lines.append(',\n'.join(f'            new ItemDef("{i}", "{k}", {s}, {u})' for i, k, s, u in simple))
    lines.append('    );')
    lines.append('')
    lines.append('    public static final List<BlockDef> BLOCKS = List.of(')
    lines.append(',\n'.join(f'            new BlockDef("{i}", {s})' for i, s in blocks))
    lines.append('    );')
    lines.append('')
    lines.append('    public static final List<FluidDef> FLUIDS = List.of(')
    lines.append(',\n'.join(f'            new FluidDef("{i}", {s}, 0x{fluid_colors[i]:08X}, {str(i in gases).lower()})'
                            for i, s in fluids))
    lines.append('    );')
    lines.append('')
    lines.append('    /** 遺構ID → 保管庫の中身が再生するまでのゲーム内日数。 */')
    from records import RECORDS
    lines.append('    /** 旧文明の記録の ID（解読する順）。 */')
    lines.append('    public static final List<String> RECORDS = List.of(' + ', '.join(f'"{r[0]}"' for r in RECORDS) + ');')
    lines.append('')
    lines.append('    public static final Map<String, Integer> RUIN_REGEN_DAYS = Map.of(')
    lines.append((',' + chr(10)).join(f'            "{ruins.RUIN_IDS[k]}", {v}' for k, v in RUIN_REGEN_DAYS.items()))
    lines.append('    );')
    lines.append('')
    lines.append('    /** 触媒 → 使い切ったときに残る失活触媒。 */')
    lines.append('    public static final Map<String, String> SPENT_FORM = Map.of(')
    lines.append(',\n'.join(f'            "{MOD[a][0]}", "{MOD[b][0]}"' for a, b in SPENT.items()))
    lines.append('    );')
    lines.append('')
    lines.append('    /** 復元品 → 使い切ったときに戻る劣化品。 */')
    lines.append('    public static final Map<String, String> WORN_FORM = Map.of(')
    lines.append(',\n'.join(f'            "{a}", "{b}"' for a, b in worn.items()))
    lines.append('    );')
    lines.append('')
    machines = [(iid, stage_of(n)) for n, (iid, _, kind) in MOD.items() if kind in ('machine', 'part_block', 'part_glass')]
    lines.append('    /** 実装済み装置ブロックの段階（ツールチップ用）。 */')
    lines.append('    public static final Map<String, Integer> BLOCK_STAGES = Map.ofEntries(')
    lines.append(',\n'.join(f'            Map.entry("{i}", {s})' for i, s in machines))
    lines.append('    );')
    lines.append('}')
    path = JAVA / 'GeneratedContent.java'
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text('\n'.join(lines) + '\n', encoding='utf-8')


def parse_range(default, rng):
    """config_spec の範囲表記 → (型, min, max)。"""
    d = default.strip()
    if d in ('true', 'false'):
        return 'boolean', None, None
    if d.startswith('"'):
        choices = re.findall(r'"([^"]+)"', rng)
        return 'enum', choices, None
    nums = [n.replace(',', '') for n in re.findall(r'[\d,]+(?:\.\d+)?', rng.split('（')[0])]
    is_float = '.' in d or any('.' in n for n in nums)
    lo = nums[0] if nums else '0'
    hi = nums[1] if len(nums) > 1 else None
    if is_float:
        return 'double', float(lo), float(hi) if hi else 1e18
    big = int(d) > 2**31 - 1 or (hi and int(hi) > 2**31 - 1) or hi is None
    return ('long' if big else 'int'), int(lo), int(hi) if hi else None


def java_config():
    def emit(entries, cls_name):
        out = []
        sections = []
        for sec, key, default, rng, desc in entries:
            if sec not in sections:
                sections.append(sec)
        out.append('    public static final ModConfigSpec SPEC;')
        fields = []
        body = ['        ModConfigSpec.Builder b = new ModConfigSpec.Builder();']
        for sec in sections:
            body.append(f'        b.push("{sec}");')
            for s, key, default, rng, desc in entries:
                if s != sec:
                    continue
                typ, lo, hi = parse_range(default, rng)
                const = re.sub(r'(?<!^)(?=[A-Z])', '_', key).upper()
                comment = desc.replace('"', '\\"')
                rng_s = rng.replace('"', '\\"')
                body.append(f'        b.comment("{comment}", "範囲: {rng_s}");')
                if typ == 'boolean':
                    fields.append(f'    public static final ModConfigSpec.BooleanValue {const};')
                    body.append(f'        {const}_ = b.define("{key}", {default});')
                elif typ == 'enum':
                    fields.append(f'    public static final ModConfigSpec.ConfigValue<String> {const};')
                    choices = ', '.join(f'"{c}"' for c in lo)
                    body.append(f'        {const}_ = b.defineInList("{key}", {default}, java.util.Arrays.asList({choices}));')
                elif typ == 'double':
                    fields.append(f'    public static final ModConfigSpec.DoubleValue {const};')
                    body.append(f'        {const}_ = b.defineInRange("{key}", {float(default)}, {lo}, {hi});')
                elif typ == 'int':
                    fields.append(f'    public static final ModConfigSpec.IntValue {const};')
                    body.append(f'        {const}_ = b.defineInRange("{key}", {default}, {lo}, {hi if hi is not None else "Integer.MAX_VALUE"});')
                else:
                    fields.append(f'    public static final ModConfigSpec.LongValue {const};')
                    body.append(f'        {const}_ = b.defineInRange("{key}", {default}L, {lo}L, {str(hi) + "L" if hi is not None else "Long.MAX_VALUE"});')
            body.append('        b.pop();')
        # static final は static ブロックで一度だけ代入する
        assigns = []
        for f in fields:
            name = f.split()[-1].rstrip(';')
            typ = ' '.join(f.split()[3:-1])
            assigns.append((name, typ))
        decl = [f'    public static final {t} {n};' for n, t in assigns]
        tmp = [f'        {t} {n}_;' for n, t in assigns]
        fin = [f'        {n} = {n}_;' for n, _ in assigns]
        code = [f'    // ---- {cls_name} ----'] + decl + out + ['', '    static {'] + tmp + body + fin + [
            '        SPEC = b.build();', '    }']
        return code

    for entries, cls in ((config_spec.SERVER, 'ServerConfig'), (config_spec.CLIENT, 'ClientConfig')):
        lines = [f'package {PKG}.generated;', '',
                 'import net.neoforged.neoforge.common.ModConfigSpec;', '',
                 '/** tools/gen_data.py が config_spec.py から生成。手で編集しない。 */',
                 f'public final class {cls} {{', f'    private {cls}() {{}}', '']
        lines += emit(entries, cls)
        lines.append('}')
        (JAVA / f'{cls}.java').write_text('\n'.join(lines) + '\n', encoding='utf-8')


# ---------------------------------------------------------------- 検証

NON_MACHINE_STATIONS = {'作業台', '溶鉱炉（バニラ）', 'かまど（バニラ）', 'マルチブロック組み立て'}
STATION_ALIASES = {'Pリアクター（触媒モード）': 'Pリアクター',
                   'Pリアクター（発電モード）': 'Pリアクター'}


def validate():
    """recipes.py の整合性を調べ、問題の一覧を返す。"""
    errs = []
    for n, r in RECIPES.items():
        for i in r['inputs']:
            if i in RECIPES and RECIPES[i]['stage'] > r['stage']:
                errs.append(f'段階逆転: {n}(段階{r["stage"]}) ← {i}(段階{RECIPES[i]["stage"]})')
        st = STATION_ALIASES.get(r['station'], r['station'])
        if st not in NON_MACHINE_STATIONS:
            if st not in RECIPES:
                errs.append(f'未定義の製作場所: {n} @ {st}')
            elif RECIPES[st]['stage'] > r['stage']:
                errs.append(f'製作場所が後の段階: {n}(段階{r["stage"]}) @ {st}(段階{RECIPES[st]["stage"]})')
        if r['station'] == '作業台' and sum(r['inputs'].values()) > WORKBENCH_MAX_ITEMS:
            errs.append(f'作業台の枠を超える: {n}（{sum(r["inputs"].values())}個）')
        if r['station'] == '精密組立台' and len(r['inputs']) > ASSEMBLER_MAX_KINDS:
            errs.append(f'精密組立台の入力枠を超える: {n}（{len(r["inputs"])}種類）')

    # レシピの循環
    done, stack = set(), set()

    def dfs(n, path):
        if n in stack:
            errs.append('循環: ' + ' → '.join(path + [n]))
            return
        if n in done or n not in RECIPES:
            return
        stack.add(n)
        for i in RECIPES[n]['inputs']:
            dfs(i, path + [n])
        stack.discard(n)
        done.add(n)

    for n in RECIPES:
        dfs(n, [])

    # 修復の材料が、その回収物自体に依存していないか
    def needs(n, target, seen):
        if n == target:
            return True
        if n in seen or n not in RECIPES:
            return False
        seen.add(n)
        return any(needs(i, target, seen) for i in RECIPES[n]['inputs'])

    for it, rp in REPAIR.items():
        for i in rp['inputs']:
            if needs(i, it, set()):
                errs.append(f'修復の自己循環: {it} の修復に {i} が必要で、{i} は {it} を使う')
        if rp['station'] not in RECIPES:
            errs.append(f'修復の製作場所が未定義: {it} @ {rp["station"]}')

    # 設定の既定値がレシピと一致しているか
    cfg = {k: d for _, k, d, _, _ in config_spec.SERVER}
    r = RECIPES
    checks = {
        'shellRockLv3': r['縮退物質殻']['inputs']['圧縮ブロックLv3'],
        'shellMetalCores': r['縮退物質殻']['inputs']['金属圧縮ブロックLv2'],
        'shellCoolantMb': r['縮退物質殻']['inputs']['液体窒素'],
        'reactorPlatesPerShell': r['炉殻ブロック']['out'],
        'exoticBatchSize': r['エキゾチック物質']['out'],
        'starsPerExoticBatch': r['エキゾチック物質']['inputs']['ネザースター'],
        'starsPerSingularityCore': r['シンギュラリティ・コア']['inputs']['ネザースター'],
        'artificialStarCoreJetCondensate': r['人工星核']['inputs']['ジェット凝縮体'],
        'sculkPerShard': r['残響の欠片']['inputs']['スカルク'],
        'timeCrystalGrowthTicks': r['時間結晶触媒']['t'] * 20,
    }
    for k, val in checks.items():
        if str(val) != cfg[k]:
            errs.append(f'設定の既定値がレシピと不一致: {k} = {cfg[k]}（レシピは {val}）')
    if float(REPAIR_WEAR) != float(cfg['repairWear']) or MAX_REPAIRS != int(cfg['maxRepairs']):
        errs.append('設定の既定値がレシピと不一致: 修復')
    return errs


# ---------------------------------------------------------------- main


# わざと透ける（ガラス・重ね絵）以外のブロックのテクスチャ
SEE_THROUGH_BLOCK_TEXTURES = ('machine_glass', 'ruin_glass', 'cooling_tower_glass', 'degenerate_furnace_window', 'boost_overlay')


GLASS_PARTS = {iid for _, (iid, _, kind) in MOD.items() if kind == 'part_glass'}


# 空のスロットに描く、入れる物のグレーの影（textures/gui/ghost/<id>.png）
GHOSTS = {'singularity_seed': 'item', 'mass_pellet': 'item', 'jet_collector': 'item', 'hawking_collector': 'item',
          'ergosphere_ring': 'item', 'exotic_matter': 'item', 'shield_permit': 'item', 'compressed_block_2': 'block'}


def ghost_textures():
    """アイテムの絵を、色を抜いた薄いグレーの影にする（入っていると見まちがえないように）。"""
    from PIL import Image
    for iid, kind in GHOSTS.items():
        src = ASSETS / 'textures' / kind / f'{iid}.png'
        im = Image.open(src).convert('RGBA').crop((0, 0, 16, 16))
        px = im.load()
        for y in range(16):
            for x in range(16):
                r, g, b, a = px[x, y]
                if a == 0:
                    continue
                lum = 0.3 * r + 0.59 * g + 0.11 * b
                v = int(96 + lum * 0.25)                         # 暗めのグレーで、濃淡だけ残す
                px[x, y] = (v, v + 4, v + 8, int(a * 0.6))
        out = ASSETS / 'textures' / 'gui' / 'ghost' / f'{iid}.png'
        out.parent.mkdir(parents=True, exist_ok=True)
        im.save(out)


def alarm_assets():
    """炉心質量警報器: 光っているときは別のモデル。"""
    import art16
    tb = ASSETS / 'textures' / 'block'
    art16.save(art16.alarm_texture(True), tb / 'reactor_mass_alarm_on.png')
    write_json(ASSETS / 'models' / 'block' / 'reactor_mass_alarm_on.json',
               {'parent': 'minecraft:block/cube_all', 'textures': {'all': f'{MODID}:block/reactor_mass_alarm_on'}})
    write_json(ASSETS / 'blockstates' / 'reactor_mass_alarm.json', {'variants': {
        'lit=false': {'model': f'{MODID}:block/reactor_mass_alarm'},
        'lit=true': {'model': f'{MODID}:block/reactor_mass_alarm_on'}}})


def rogue_black_hole_assets():
    """野良ブラックホール: 見た目はブロックエンティティの描画だけ（ブロックのモデルは粒子用のテクスチャだけ）。"""
    write_json(ASSETS / 'models' / 'block' / 'rogue_black_hole.json',
               {'textures': {'particle': f'{MODID}:block/creative_energy_casing'}})
    write_json(ASSETS / 'blockstates' / 'rogue_black_hole.json', {'variants': {'': {'model': f'{MODID}:block/rogue_black_hole'}}})


def opaque_block_textures():
    """ブロックのテクスチャの半透明の画素を不透明にする（色はそのまま）。
    普通の不透明ブロックでは半透明は無視されて同じ見た目だが、ガラス窓のある装置（半透明で描く）では、
    半透明の線から奥（隣のブロックの面は省かれている）が透けて見えてしまうため。"""
    from PIL import Image
    roots = [ASSETS / 'textures' / 'block',
             RES / 'resourcepacks' / 'singulo_hd' / 'assets' / MODID / 'textures' / 'block']
    for root in roots:
        if not root.exists():
            continue
        for png in root.glob('*.png'):
            if png.stem in SEE_THROUGH_BLOCK_TEXTURES or png.stem in GLASS_PARTS:
                continue
            im = Image.open(png).convert('RGBA')
            px = im.load()
            changed = False
            for y in range(im.height):
                for x in range(im.width):
                    r, g, b, a = px[x, y]
                    if 0 < a < 255:
                        px[x, y] = (r, g, b, 255)
                        changed = True
            if changed:
                im.save(png)

def main():
    errs = validate()
    if errs:
        print(f'recipes.py の検証で {len(errs)} 件の問題。何も生成していない:')
        for e in errs:
            print('  x', e)
        sys.exit(1)
    print(f'検証OK（レシピ {len(RECIPES)} 件）')
    for d in (RES, ROOT / 'src' / 'generated' / 'java'):
        if d.exists():
            shutil.rmtree(d)
    recipes, skipped = recipe_files()
    sounds_json()
    lang_files()
    models()
    textures()
    opaque_block_textures()
    ghost_textures()
    rogue_black_hole_assets()
    alarm_assets()
    tags()
    mass_values()
    thermal()
    ruins_data()
    java_content()
    java_config()
    write_json(RES / 'pack.mcmeta', {'pack': {'description': 'Singulo generated resources', 'pack_format': 34}})
    # レシピの画像と一覧（docs/recipes）
    import recipe_images
    images = recipe_images.render_all(RECIPES, ORDER, STAGE_NAMES)
    print(f'レシピ {len(recipes)} 件を生成（画像 {images} 枚）')
    if skipped:
        print(f'\n生成しなかったレシピ {len(skipped)} 件:')
        for s in skipped:
            print('  -', s)
    if warnings:
        print(f'\n警告 {len(warnings)} 件:')
        for w in warnings:
            print('  !', w)
        sys.exit(1)


if __name__ == '__main__':
    main()
