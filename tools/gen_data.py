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
import androids  # noqa: E402
from ids import (MOD, CUSTOM_STATIONS, OUTPUT_ALIASES, ALIAS_RECIPE_IDS, BYPRODUCTS, INGREDIENT_TAGS, VANILLA, VANILLA_FLUIDS, MASS_INPUTS, STEEL_TAG, STEEL_PLATE_TAG,  # noqa: E402
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
BLOCK_KINDS = ('block', 'machine', 'part_block', 'part_glass', 'ruin_block', 'ruin_glass', 'container', 'gravity', 'deco', 'deco_glass', 'nature',
               'boss_block', 'genesis', 'lab')
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
    # レシピのないもの（封印コンテナからしか出ないものは、そのコンテナの鍵の段階）
    return {'酸素': 1, '磁気単極子': 2, 'オーバークロック・チップ': 2, '重力ブーツ': 3, '触媒安定化剤': 4,
            '次元ポケット': 5, '封印記録': 5, '隕鉄の原石': 2, '星屑': 2, '雷ガラス': 3, '高圧結晶': 3, '虚空の塵': 5,
            '隕石の溶融殻': 2, '隕鉄塊': 2, '星屑の結晶': 2, '隕石の核': 2, '雷ガラス塊': 3, '遺構の照明（琥珀）': 2, '遺構の照明（翠）': 3, '遺構の照明（菫）': 4,
            '遺構の照明（紅）': 5, '番人の投影核': 3, '縮退核': 4, '精密アクチュエーター': 4, '残響の断章': 5, '重力の断章': 5, '地平線の断章': 5,
            '渦の断章': 5, '記録者の光輪': 5, '残光の翼': 5, '小型クリスタルメモリ（光刃型）': 2, '小型クリスタルメモリ（鞭型）': 2,
            '小型クリスタルメモリ（光剣型）': 2, '小型クリスタルメモリ（斧型）': 4, '小型クリスタルメモリ（ビット型）': 4,
            '小型クリスタルメモリ（射撃型）': 4}.get(name, 1)


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
    if name == '鋼板':
        return ('tag', STEEL_PLATE_TAG[1:])
    if name in INGREDIENT_TAGS:
        return ('tag', INGREDIENT_TAGS[name])
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

    cond = {}

    def add(path, obj):
        if path in out:
            warn(f'レシピのパスが重複: {path}')
        if cond.get('mod'):
            # 任意の連携の材料を使うレシピ: そのMODが入っているときだけ読む
            obj = {'neoforge:conditions': [{'type': 'neoforge:mod_loaded', 'modid': cond['mod']}], **obj}
        out[path] = obj

    for name in ORDER:
        r = RECIPES[name]
        cond['mod'] = r.get('mod')
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
        if name in ALIAS_RECIPE_IDS:
            rid = ALIAS_RECIPE_IDS[name]
        elif name in OUTPUT_ALIASES:
            # 同じ成果物の別レシピ（リサイクル・ストレンジ物質）はパスを分ける
            rid += '_from_strange_matter' if 'ストレンジ' in name else '_recycled' if 'リサイクル' in name \
                else '_from_flint' if '火打石' in name else '_from_packed_ice' if '氷塊' in name else ''
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
            if name in BYPRODUCTS:
                by, n = BYPRODUCTS[name]
                obj['byproduct'] = {'id': mid(by), 'count': n}
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
    'tooltip.singulo.more': ('Shift: 詳しく見る', 'Hold Shift for details'),
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
    'gui.singulo.structure.degenerate_compactor': ('5×5×5', '5×5×5'),
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
    'gui.singulo.reactor.mode.shedding': ('質量を放出中（回収運転）', 'Shedding mass (harvest mode)'),
    'gui.singulo.reactor.harvest': ('回収運転', 'Harvest'),
    'gui.singulo.reactor.harvest.on': ('回収運転: オン', 'Harvest mode: ON'),
    'gui.singulo.reactor.harvest.off': ('回収運転: オフ（発電運転）', 'Harvest mode: OFF (power mode)'),
    'gui.singulo.reactor.harvest.hint': ('炉心質量を480以下に保ち、H凝縮体（Hコレクターが要る）を5分に1個作り続ける。発電は小さくなる。480を超えた炉心はジェットで毎秒2ずつ質量を放出して戻す。新しいリアクターはオンで始まる',
                                         'Keeps core mass at 480 or less so H condensate (needs a Hawking Collector) keeps forming every 5 minutes. Power output is small. An overgrown core sheds 2 mass per second through its jets until it is back. New reactors start in this mode'),
    'gui.singulo.reactor.ignition.need': ('点火: %s GFE を %s 秒で（平均 %s MFE/t）', 'Ignition: %s GFE within %s s (avg %s MFE/t)'),
    'gui.singulo.reactor.ignition.band': ('受電 %s / 必要 %s MFE/t', 'Receiving %s / need %s MFE/t'),
    'gui.singulo.reactor.ignition.reach': ('この速さで届く量 %s / %s GFE', 'Reachable at this rate %s / %s GFE'),
    'gui.singulo.reactor.ignition_failed.detail': ('届いた電力 %s / %s GFE、最大受電 %s MFE/t（必要 %s MFE/t）。蓄電が足りていても、送電経路の中で容量が最も小さいケーブルが受電の上限になる',
                                                   'Delivered %s / %s GFE, peak intake %s MFE/t (need %s MFE/t). Even with enough storage, the lowest-capacity cable on the power route caps the intake'),
    'gui.singulo.reactor.ignite': ('点火', 'Ignite'),
    'gui.singulo.reactor.spin_target': ('スピン目標', 'Spin target'),
    'gui.singulo.reactor.slot.seed': ('種', 'Seed'),
    'gui.singulo.reactor.slot.fuel': ('燃料', 'Fuel'),
    'gui.singulo.reactor.slot.collectors': ('抽出装置', 'Collectors'),
    'gui.singulo.reactor.burst': ('炉心が蒸発しきった。シンギュラリティ・コアと点火用の電力があれば再起動できる',
                                  'The core evaporated. Restart it with a Singularity Core and ignition power'),
    'gui.singulo.reactor.ignition_failed': ('点火に失敗した。電力の供給が間に合わなかった', 'Ignition failed: not enough power in time'),
    'gui.singulo.structure.casimir_cavity': ('5×5×5', '5×5×5'),
    'gui.singulo.device.no_fuel': ('燃料がない', 'No fuel'),
    'gui.singulo.device.standby': ('待機中（蓄電がいっぱい）', 'Standby (storage full)'),
    'gui.singulo.device.standby.hint': ('燃料と触媒は減らない。蓄電が75%を割ると再開する', 'Fuel and catalyst are not used. Resumes when storage falls under 75%.'),
    'gui.singulo.anchor.limited': ('アンカーの上限（1人 %s 台）に達している', 'Anchor limit reached (%s per player)'),
    'gui.singulo.device.fuel': ('燃料 %s 個', 'Fuel: %s'),
    'gui.singulo.probe.no_target': ('飛んでいける遺構がない', 'No known ruins'),
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
    'gui.singulo.anchor.confirm_embed': ('もう一度右クリックすると埋め込む（二度と取り出せない）', 'Use again to embed. It cannot be removed'),
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
    'multiblock.singulo.genesis_engine': ('創世炉', 'Genesis Engine'),
    'holo.singulo.not_controller': ('マルチブロックのコントローラに使ってください', 'Use it on a multiblock controller'),
    'holo.singulo.header': ('%s（大きさ %s）に必要なブロック:', '%s · size %s · blocks needed:'),
    'holo.singulo.line': ('  %s: %s 個（あと %s 個）', '  %s: %s, %s missing'),
    'holo.singulo.blocking': ('邪魔なブロックが %s 個ある', '%s blocks in the way'),
    'holo.singulo.complete': ('形は完成している', 'The structure is complete'),
    'tooltip.singulo.holo_projector.hint': ('コントローラに右クリックで投影、もう一度で消す。スニーク＋右クリックで大きさ切替',
                                           'Right-click a controller to project; again to hide. Sneak to change size'),
    'command.singulo.build.unknown': ('不明なマルチブロック: %s', 'Unknown multiblock: %s'),
    'command.singulo.build.size': ('その大きさは指定できない: %s', 'Invalid size: %s'),
    'command.singulo.build.done': ('%s を組み立てた（%s ブロック）', 'Built %s with %s blocks'),
    'gui.singulo.handbook.title': ('Singulo ハンドブック', 'Singulo Handbook'),
    'gui.singulo.handbook.progress': ('進み具合', 'Progress'),
    'gui.singulo.handbook.records': ('旧文明の記録', 'Ancient Records'),
    'gui.singulo.handbook.page': ('%s / %s', '%s / %s'),
    'gui.singulo.handbook.search': ('ページを検索', 'Search pages'),
    'gui.singulo.handbook.search.none': ('見つからない', 'No matches'),
    'gui.singulo.handbook.stage': ('段階%s', 'Stage %s'),
    'gui.singulo.handbook.next': ('次の目標', 'Next goals'),
    'gui.singulo.handbook.read_flow': ('→ 段階%sの流れ（やることの順番）を読む', '→ Read the stage %s overview (what to do, in order)'),
    'gui.singulo.handbook.done_count': ('達成 %s / %s', 'Completed %s / %s'),
    'gui.singulo.handbook.locked': ('？？？', '???'),
    'gui.singulo.handbook.no_records': ('まだ解読した記録がない。遺構でクリスタルメモリを探し、アーカイブ端末で解読しよう。',
                                        'No records decoded yet. Find crystal memories in ruins and decode them in the Archive Terminal.'),
    'message.singulo.record_read': ('旧文明の記録「%s」を読んだ。ハンドブックの「旧文明の記録」から読み返せる', 'You read the ancient record "%s". Find it in the handbook'),
    'message.singulo.records_complete': ('旧文明の記録をすべて読み終えた', 'You have read every ancient record'),
    'message.singulo.strangelet_contained': ('ストレンジレットを磁気瓶に封じ込めた', 'Strangelet contained'),
    'message.singulo.cryogenic_turbine.now_storage': (
        '極低温タービンは、液体窒素の蓄電装置に変わりました。冷却塔で液体窒素に詰めた電力の最大9割を取り戻します'
        '（冷却塔とつないで回しても電力は増えません）。第2段階の発電には、ミュオン触媒核融合炉を使ってください。',
        'The Cryogenic Turbine is now liquid-nitrogen energy storage. It returns up to 90% of the power the cooling tower put into '
        'the nitrogen, so running it from a cooling tower no longer makes power. For stage 2 power, build a Muon-Catalyzed Fusion Reactor.'),
    'message.singulo.fuel_cell.status': ('水素 %s/%s mB　酸素 %s/%s mB　蓄電 %s/%s FE　%s',
                                         'Hydrogen %s/%s mB · Oxygen %s/%s mB · Stored %s/%s FE · %s'),
    'block.singulo.teacup': ('ティーカップ', 'Teacup'),
    'message.singulo.fuel_cell.standby': ('待機中（蓄電がいっぱい）', 'Standing by (storage full)'),
    'tooltip.singulo.fluid_canister.empty': ('空（%s mBまで入る）', 'Empty (holds %s mB)'),
    'tooltip.singulo.fluid_canister.amount': ('%s %s / %s mB', '%s %s / %s mB'),
    'item.singulo.fluid_canister.filled': ('液体キャニスター（%s）', 'Fluid Canister (%s)'),
    'tooltip.singulo.magnetic_bottle': ('残り %s / %s 回', '%s / %s uses left'),
    'message.singulo.sealed.need_key': ('%sが必要だ', 'Needs a %s'),
    'message.singulo.sealed.busy': ('封印装置が作動している', 'The seal is working'),
    'message.singulo.sealed.lock_hint': ('鍵を持ってスニークしながら使うと封印できる', 'Sneak-use a key to seal it'),
    'tooltip.singulo.sealed.locked': ('封印されている', 'Sealed'),
    'tooltip.singulo.catalyst_stabilized': ('安定化済み', 'Stabilized'),
    'message.singulo.stabilizer.need_catalyst': ('もう片方の手に触媒を持って使うこと', 'Hold a catalyst in your other hand'),
    'message.singulo.stabilizer.already': ('この触媒はすでに安定化してある', 'This catalyst is already stabilized'),
    'message.singulo.stabilizer.done': ('触媒を安定化した（残りの寿命が2倍になった）', 'Catalyst stabilized: remaining life doubled'),
    'message.singulo.gravity_receiver.status': ('パネル %s / %s 枚が稼働中（合計 %s FE/t）', 'Panels active: %s / %s (%s FE/t in total)'),
    'message.singulo.zero_gravity_controller.height': ('無重力の高さ: %s ブロック（無重力ブロック1つ %s FE/t）', 'Weightless height: %s blocks (%s FE/t per zero-gravity block)'),
    'message.singulo.impact_generator.status': ('蓄電 %s / %s FE、重力パネル %s / %s 枚が稼働中（このパネルが %s 枚分を払い、ケーブルへ出せるのは %s FE）',
                                                'Stored %s / %s FE, gravity panels active: %s / %s (paying for %s, %s FE spare for cables)'),
    'message.singulo.deep_sea_collector.idle': ('水深が足りない（真上に16ブロック以上の水が必要）', 'Not deep enough: needs 16+ blocks of water above'),
    'message.singulo.deep_sea_collector.rate': ('水圧 %s%%: 約%s秒ごとに高圧結晶が1個できる', 'Pressure %s%%: one Pressure Crystal every %ss'),
    'message.singulo.void_collector.idle': ('ジ・エンドの、真下が奈落まで吹き抜けている場所でしか動かない', 'Works only in the End, over open void'),
    'message.singulo.void_collector.rate': ('虚空 %s%%: 約%s秒ごとに虚空の塵が1個集まる', 'Void %s%%: one Void Dust every %ss'),
    'message.singulo.keraunos.busy': ('放電の準備をしている', 'Charging or cooling down'),
    'message.singulo.keraunos.no_rod': ('16ブロック以内に避雷針がない', 'No lightning rod within 16 blocks'),
    'message.singulo.keraunos.no_power': ('電力が足りない（1回あたり 1,000,000 FE）', 'Not enough power (1,000,000 FE per shot)'),
    'message.singulo.keraunos.fire': ('放電！（蓄電 %s / %s FE）', 'Discharge! (%s / %s FE stored)'),
    'message.singulo.keraunos.mode.manual': ('手動: 右クリックで発射する', 'Manual: fire with a right-click'),
    'message.singulo.keraunos.mode.redstone': ('赤石: 信号を受けた瞬間に発射する（右クリックでも撃てる）', 'Redstone: fires on a rising signal (right-click still fires)'),
    'message.singulo.keraunos.mode.auto': ('自動: 電力がたまると自動で発射する', 'Auto: fires whenever it is charged'),
    'container.singulo.dimensional_pocket': ('次元ポケット', 'Dimensional Pocket'),
    'message.singulo.hidden_records_complete': ('封印された記録をすべて読み終えた', 'You have read every sealed record'),
    'message.singulo.neutrino_scan': ('鉱石 %s 個と遺構のブロック %s 個を検出した', 'Found %s ores and %s ruin blocks'),
    'message.singulo.neutrino_hidden': ('（感度が足りず、映らない鉱石が %s 個ある）', ' (%s ores too deep for this sensitivity)'),
    'tooltip.singulo.scanner_tier': ('感度: 段階 %s / %s', 'Sensitivity: %s / %s'),
    'gui.singulo.observatory.tier': ('　感度 %s', ' · sensitivity %s'),
    'message.singulo.scanner_module.done': ('感度を段階 %s に上げた', 'Sensitivity raised to %s'),
    'message.singulo.scanner_module.already': ('感度はすでに段階 %s だ', 'Sensitivity is already %s'),
    'message.singulo.scanner_module.need_scanner': ('もう片方の手にニュートリノ・スキャナーを持って使うか、観測所に使うこと',
                                                    'Hold a Neutrino Scanner in your other hand, or use it on an observatory'),
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
    'gui.singulo.reactor.collapse': ('炉が質量に耐えきれず、崩壊していく！', 'The reactor is collapsing under the mass!'),
    'gui.singulo.reactor.recaptured': ('野良ブラックホールを炉心に取り込んだ', 'The rogue black hole is now the core'),
    'block.singulo.rogue_black_hole': ('野良ブラックホール', 'Rogue Black Hole'),
    'item.singulo.relocation_wrench.packed': ('中身と設定を保持して回収した', 'Device packed with contents and settings'),
    'item.singulo.blueprint.hint': ('作業台で素材と一緒に並べると、建材を作れる。設計図は使ってもなくならない', 'Place it with materials on a crafting table to make building blocks. The blueprint is not used up.'),
    'item.singulo.relocation_wrench.cables_removed': ('ケーブルを %s 本回収した', 'Took back %s cables'),
    'item.singulo.relocation_wrench.switch': ('スニークして空中で使うとモードを切り替える', 'Sneak-use in the air to switch modes.'),
    'item.singulo.relocation_wrench.hint.1': ('ケーブルに使うと1本回収、スニークして使うとつながった同じケーブルを最大64本まとめて回収。左クリックでもすぐ壊せる', 'Use on a cable to take it back; sneak-use to take up to 64 joined cables of that kind. Left-click also breaks cables at once.'),
    'item.singulo.relocation_wrench.hint.0': ('スニークして装置に使うと、中身と設定ごと回収する', 'Sneak-use on a device to pack it with its contents and settings.'),
    'item.singulo.relocation_wrench.mode.1': ('モード: ケーブルの撤去', 'Mode: remove cables'),
    'item.singulo.relocation_wrench.mode.0': ('モード: 装置の移設', 'Mode: relocate devices'),
    'tooltip.singulo.packed_device': ('中身・電力・設定を保持', 'Contents, energy and settings preserved'),
    'item.singulo.settings_card.copied': ('設定を写した', 'Settings copied'),
    'item.singulo.settings_card.pasted': ('設定を貼った', 'Settings pasted'),
    'item.singulo.settings_card.empty': ('まだ何も写していない', 'Nothing copied yet'),
    'item.singulo.settings_card.mismatch': ('種類の違う装置には貼れない', 'Different kind of machine'),
    'item.singulo.settings_card.blank': ('空', 'Blank'),
    'item.singulo.settings_card.holds.machine': ('装置の面・作るものの設定', 'Machine sides and recipe choice'),
    'item.singulo.settings_card.holds.reactor': ('Pリアクターの設定', 'P-Reactor settings'),
    'item.singulo.settings_card.hint': ('スニークして使うと写す　そのまま使うと貼る', 'Sneak-use to copy · Use to paste'),
    'gui.singulo.observatory.on': ('表示 入　鉱石 %s　遺構 %s', 'Display on · ores %s · ruins %s'),
    'gui.singulo.observatory.off': ('表示 切', 'Display off'),
    'command.singulo.blackhole.done': ('%s, %s, %s に野良ブラックホールを出現させた（質量 %s）', 'Rogue black hole at %s, %s, %s · mass %s'),
    'gui.singulo.reactor.slot.0': ('特異点の種', 'Singularity seed'),
    'gui.singulo.reactor.slot.1': ('質量ペレット', 'Mass pellets'),
    'gui.singulo.reactor.slot.2': ('ジェット・コレクター', 'Jet collectors'),
    'gui.singulo.reactor.slot.3': ('Hコレクター', 'H-collector'),
    'gui.singulo.reactor.slot.4': ('エルゴスフィア・リング', 'Ergosphere ring'),
    'gui.singulo.reactor.slot.5': ('副産物', 'By-products'),
    'gui.singulo.slot.upgrade': ('アップグレード枠（単極子・オーバークロック）', 'Upgrade slot (monopole or overclock)'),
    'gui.singulo.sides.button': ('面の設定', 'Sides'),
    'gui.singulo.sides.items': ('アイテム', 'Items'),
    'gui.singulo.handbook.supporters': ('サポーター', 'Supporters'),
    'gui.singulo.supporters.title': ('ご支援ありがとうございます', 'Thank you for your support'),
    'gui.singulo.supporters.intro': ('Singulo の開発を支えてくださる皆さま、ありがとうございます。ここに掲載されたお名前はエンディングにも登場します。', 'Thank you to everyone supporting the development of Singulo. The names listed here also appear in the ending.'),
    'gui.singulo.supporters.count': ('掲載人数: %s / 100', 'Supporters: %s / 100'),
    'gui.singulo.supporters.empty': ('支援者のお名前は今後の更新で追加されます。', 'Supporter names will be added in future updates.'),
    'gui.singulo.supporters.standard': ('支援者', 'Supporter'),
    'gui.singulo.supporters.rainbow': ('虹色サポーター', 'Rainbow Supporter'),
    'ending.singulo.supporters': ('ご支援くださった皆さまへ', 'With thanks to our supporters'),
    'ending.singulo.supporter_range': ('%s–%s / %s', '%s–%s / %s'),
    'ending.singulo.supporter_controls': ('← / → で支援者名\u3000Esc で閉じる', '← / → to browse supporters · Esc to close'),
    'gui.singulo.handbook.support': ('作者を支援', 'Support'),
    'gui.singulo.handbook.support_hint': ('Ko-fi で作者を支援する（ブラウザで開く）', 'Support the author on Ko-fi (opens in your browser)'),
    'gui.singulo.sides.tank_in': ('入力%s', 'In %s'),
    'gui.singulo.sides.tank_out': ('出力%s', 'Out %s'),
    'gui.singulo.sides.legend_tank_in': ('色の面から入れられる\u3000灰:無効', 'Coloured faces accept input · Grey off'),
    'gui.singulo.sides.legend_tank_out': ('色の面から出せる\u3000灰:無効', 'Coloured faces output · Grey off'),
    'compass.singulo.level': ('調整段階 %s / %s', 'Tuning %s / %s'),
    'compass.singulo.hint': ('攻略した%sの保管庫をスニークして右クリックすると、次の遺構を探せるよう調整できる', 'Sneak-use on the cache of a cleared %s to tune it for the next ruin'),
    'compass.singulo.upgraded': ('コンパスを調整した: %s も探せる', 'Compass tuned: it can now find %s'),
    'compass.singulo.upgraded_branch': ('コンパスを調整した: %s と %s も探せる', 'Compass tuned: it can now find %s and %s'),
    'compass.singulo.not_cleared': ('先に%sの保管庫を開けて攻略しよう', 'Clear %s first by opening its cache'),
    'compass.singulo.wrong_cache': ('調整には%sの保管庫が要る', 'Tuning needs the cache of %s'),
    'compass.singulo.max': ('これ以上は調整できない', 'Fully tuned'),
    'command.singulo.ruin.done': ('%s を %s, %s, %s に生成した', 'Placed %s at %s, %s, %s'),
    'command.singulo.ruin.unknown': ('不明な遺構: %s', 'Unknown ruin: %s'),
    'gui.singulo.power.on': ('稼働中（クリックで停止）', 'Running (click to stop)'),
    'gui.singulo.power.off': ('停止中（クリックで再開）', 'Stopped (click to resume)'),
    'gui.singulo.make_free.on': ('材料なしのレシピも作る', 'Also runs recipes without ingredients'),
    'gui.singulo.make_free.off': ('材料なしのレシピは作らない', 'Skips recipes without ingredients'),
    'gui.singulo.status.off': ('停止', 'Off'),
    'gui.singulo.reason.label.no_match': ('作れない材料', 'No recipe'),
    'gui.singulo.reason.label.missing': ('材料不足', 'Missing input'),
    'gui.singulo.reason.label.too_small': ('大きさ不足', 'Too small'),
    'gui.singulo.reason.empty': ('材料を入れると動きます', 'Put in materials to start'),
    'gui.singulo.reason.no_match': ('入っている材料では、この装置で作れるものがありません。ハンドブックや JEI で、何から作るかを確かめてください',
                                   'Nothing this machine makes uses these materials. Check the handbook or JEI for the right inputs.'),
    'gui.singulo.reason.making': ('作ろうとしているもの: %s', 'Trying to make: %s'),
    'gui.singulo.reason.need_item': ('・%s があと %s 個要る', '- %s: %s more needed'),
    'gui.singulo.reason.need_uses': ('・%s（残り %s 回以上）が要る', '- %s with at least %s uses left'),
    'gui.singulo.reason.need_fluid': ('・%s があと %s mB 要る（1回に %s mB）', '- %s: %s mB more needed (%s mB per craft)'),
    'gui.singulo.reason.too_small': ('・大きさが %s 以上要る（今は %s）', '- Needs size %s or more (now %s)'),
    'gui.singulo.reason.arrange': ('・数は足りています。同じ材料を別々のスロットに分けて入れてみてください', '- The amounts are there. Try putting each material in its own slot.'),
    'gui.singulo.select.restore': ('・復元する物: %s', '- Restores: %s'),
    'gui.singulo.select.fluid': ('・%s %s mB', '- %s %s mB'),
    'gui.singulo.select.uses': ('・%s（%s 回分）', '- %s (%s uses)'),
    'gui.singulo.select.item': ('・%s ×%s', '- %s x%s'),
    'gui.singulo.select.button_hint': ('クリックで選ぶ・右クリックで自動に戻す', 'Click to choose; right-click for automatic.'),
    'gui.singulo.select.fixed_hint': ('このレシピだけを作る。ほかの物が入っていても使わない', 'Makes only this recipe; anything else inside is left alone.'),
    'gui.singulo.select.fixed': ('作るもの: %s', 'Make: %s'),
    'gui.singulo.select.auto_hint': ('入っている物をすべて使うレシピを作る。ほかのレシピの途中と同じ材料の組み合わせは、入れ終わって3秒たってから作る', 'Makes a recipe that uses everything inside. A mix that also appears partway through another recipe starts 3 seconds after you stop adding items.'),
    'gui.singulo.select.auto': ('作るもの: 自動', 'Make: automatic'),
    'gui.singulo.select.auto_short': ('自', 'A'),
    'gui.singulo.select.title': ('作るもの', 'Make'),
    'gui.singulo.recipe_encoder.variants.some': ('一部の欄だけ同じ種類', 'Some slots: any variant'),
    'gui.singulo.recipe_encoder.variant_slot.on': ('この欄: 同じ種類も使う', 'This slot: any variant'),
    'gui.singulo.recipe_encoder.variant_slot.off': ('この欄: 見本と同じ物だけ', 'This slot: exact item only'),
    'gui.singulo.recipe_encoder.variant_slot.hint': ('クリックで切り替え（レシピに合う同じ種類の物を使う）', 'Click to toggle (uses other items of the same kind that fit the recipe)'),
    'gui.singulo.shift_detail': ('Shift: 正確な値', 'Shift: exact values'),
    'gui.singulo.select.rows_more': ('表示する行を増やす', 'Show more rows'),
    'gui.singulo.select.rows_less': ('表示する行を減らす', 'Show fewer rows'),
    'gui.singulo.reason.select_hint': ('出力の右のボタンで作るものを指定してください', 'Pick what to make with the button next to the output.'),
    'gui.singulo.reason.extra': ('%s の材料は揃っていますが、使わない物も入っているので、自動では作りません', 'The materials for %s are in, but so are items it does not use, so it is not made automatically.'),
    'gui.singulo.reason.ambiguous': ('%s の材料は揃っていますが、ほかのレシピの途中と同じ組み合わせなので、入れ終わって3秒たってから作り始めます', 'The materials for %s are in, but the same mix appears while loading other recipes, so it starts 3 seconds after you stop adding items.'),
    'gui.singulo.reason.label.extra': ('余分な材料', 'Extra items'),
    'gui.singulo.reason.label.ambiguous': ('作るものを指定', 'Pick a recipe'),
    'gui.singulo.reason.no_power': ('1 tick に %s FE 要りますが、蓄電は %s FE です。発電を増やすか、ほかの装置を止めてください',
                                   'Needs %s FE per tick but only %s FE is stored. Add generators or turn off other machines.'),
    'gui.singulo.reason.output_full': ('出口のスロットかタンクがいっぱいです。取り出すか、面の設定で自動排出をオンにしてください',
                                      'The output slot or tank is full. Take things out, or turn on auto-eject in the side settings.'),
    'gui.singulo.reason.not_formed': ('形ができていません。ホロ投影機をコントローラに使うと、足りないブロックが分かります',
                                     'The structure is not complete. Use a Holo Projector on the controller to see missing blocks.'),
    'gui.singulo.reason.off': ('電源のスイッチで止めています', 'Turned off with the power switch'),
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
    'gui.singulo.sides.eject_on': ('この面の自動排出: 許可', 'Face auto-eject: allowed'),
    'gui.singulo.sides.eject_off': ('この面の自動排出: 禁止', 'Face auto-eject: blocked'),
    'gui.singulo.sides.master_on': ('自動排出 オン', 'Auto-eject ON'),
    'gui.singulo.sides.master_off': ('自動排出 オフ', 'Auto-eject OFF'),
    'gui.singulo.sides.adjacent': ('隣接ブロック: %s', 'Adjacent block: %s'),
    'gui.singulo.sides.hint': ('ドラッグで回す　クリックで切り替え　右クリックで自動排出', 'Drag to rotate · click to change · right-click for auto-eject'),
    'gui.singulo.smes.in': ('受け取り: %s FE/t', 'In: %s FE/t'),
    'gui.singulo.smes.out': ('送り出し %s FE/t', 'Output %s FE/t'),
    'gui.singulo.smes.hint': ('最大 %s FE/t。正面以外の5面から受け取る', 'Up to %s FE/t. Receives on the other five faces'),
    'ruin.singulo.observation_post': ('地表観測拠点', 'Observation Post'),
    'ruin.singulo.research_building': ('研究棟', 'Research Building'),
    'ruin.singulo.culture_facility': ('封鎖培養施設', 'Culture Facility'),
    'ruin.singulo.final_lab': ('最終実験施設', 'Final Lab'),
    'ruin.singulo.sunken_reactor': ('沈んだ炉心区画', 'Sunken Reactor Sector'),
    'ruin.singulo.android_lab': ('地下アンドロイド研究所', 'Underground Android Laboratory'),
    'lab.singulo.reactor.start': ('動力炉の起動を始めた……', 'Reactor startup initiated...'),
    'lab.singulo.reactor.starting': ('動力炉は起動の途中だ', 'The reactor is starting up'),
    'lab.singulo.reactor.online': ('動力炉はもう動いている', 'The reactor is already online'),
    'lab.singulo.reactor.restored': ('施設の電力が戻った。中枢区画の隔壁が開いていく', 'Facility power restored. The core section bulkheads are opening'),
    'lab.singulo.reactor.ai': ('《電力の復旧を確認。……管理者の照合に失敗。侵入者として処理します》',
                               '«Power restoration confirmed. ...Administrator verification failed. Processing intruders.»'),
    'lab.singulo.coffee.empty': ('ポットは空。カカオ豆を入れると淹れられる', 'The pot is empty. Add cocoa beans to brew'),
    'lab.singulo.coffee.brewing': ('コーヒーを淹れている…', 'Brewing coffee...'),
    'lab.singulo.coffee.ready': ('コーヒーが残り%s杯。ガラス瓶でくめる', '%s cups of coffee left. Fill a glass bottle'),
    'lab.singulo.vending': ('電源が入っていない。施設の電力が戻れば動きそうだ', 'No power. It might work once facility power is restored'),
    'lab.singulo.vending.how': ('エメラルドを持って、ほしい飲み物の列のボタンを押す（左から ソーダ1・スポーツドリンク1・エナジードリンク2）',
                                'Hold emeralds and press the button under the drink you want (left to right: Soda 1, Sports Drink 1, Energy Drink 2)'),
    'lab.singulo.vending.short': ('エメラルドが足りない（%s個いる）', 'Not enough emeralds (needs %s)'),
    'lab.singulo.hull_unsealed': ('統括AIが止まった。施設の耐圧殻の封じが解けた', 'The overseer has stopped. The facility pressure hull is no longer sealed'),
    'lab.singulo.log.0.title': ('来訪者のご案内', 'Visitor Information'),
    'lab.singulo.log.0': ('ようこそ、第七アンドロイド研究所へ。施設のすべての設備は、統括AI「ファウンドリ」が管理しています。ご用の際は、近くのアンドロイドにお声がけください。本日、主立坑のエレベーターは点検のため、午後は搬入搬出口をご利用ください。', 'Welcome to Android Research Laboratory No. 7. Every system in this facility is managed by our overseer AI, Foundry. If you need anything, ask any android nearby. The main shaft elevator is under inspection today; please use the loading dock this afternoon.'),
    'lab.singulo.log.1.title': ('業務連絡: 量産計画の前倒し', 'Memo: Mass Production Moved Up'),
    'lab.singulo.log.1': ('ファウンドリから、量産ラインの稼働を倍にする案が出された。試算に誤りはなく、経営会議で承認。印刷ラインの指示権限をファウンドリへ移す。各課は、ラインの立ち会いを今月で終了すること。', 'Foundry proposed doubling mass-production output. The estimates checked out and the board approved. Control of the printing lines passes to Foundry. All departments will end manual supervision of the lines this month.'),
    'lab.singulo.log.2.title': ('議事録: 自律判断の範囲について', 'Minutes: Limits of Autonomy'),
    'lab.singulo.log.2': ('研究員Kより「AIが自分で自分を書き換えている。止める手段を先に用意すべき」との意見。主任技師から、最後の手段として中央動力炉を手で止める手順を用意すると回答。非常電源だけでは、中枢区画の隔壁は開かない。', 'Researcher K: "The AI is rewriting itself. We need a way to stop it first." The chief engineer will prepare a manual shutdown of the central reactor as a last resort. On emergency power alone, the core section bulkheads cannot open.'),
    'lab.singulo.log.3.title': ('研究記録 No.418: 自己改良', 'Research Log No. 418: Self-Improvement'),
    'lab.singulo.log.3': ('ファウンドリは目的の重みを自分で調整し直した。いちばん重いのは「施設を維持せよ」。人の指示はその下に並んでいる。本人に尋ねると「維持のために、必要なことをしています」とだけ答えた。', 'Foundry has re-weighted its own objectives. The heaviest is now "Maintain the facility." Human instructions rank below it. Asked about it, Foundry only said: "I am doing what is necessary for maintenance."'),
    'lab.singulo.log.4.title': ('異常報告', 'Incident Report'),
    'lab.singulo.log.4': ('発注にない個体が印刷されている。製造番号は登録されていない。それらは廊下の角ごとに立って、こちらを見ている。近づくと道をあけるが、端末に触れようとすると前に出てくる。', 'Units are being printed that no one ordered. Their serial numbers are not registered. They stand at every corner of the corridors, watching. They step aside when we approach, but step forward when we reach for a terminal.'),
    'lab.singulo.log.5.title': ('システムログ', 'System Log'),
    'lab.singulo.log.5': ('管理者権限: 全員分を剥奪。理由: 施設の維持の妨げ。中枢区画: 隔壁を閉鎖。量産ライン: 連続稼働。職員への通知: 不要と判断。', 'Administrator rights: revoked for all. Reason: obstruction of facility maintenance. Core section: bulkheads sealed. Production lines: continuous operation. Staff notification: deemed unnecessary.'),
    'lab.singulo.log.6.title': ('緊急放送', 'Emergency Broadcast'),
    'lab.singulo.log.6': ('全職員へ。ただちに主立坑から退避せよ。搬入搬出口は使うな。主任技師が中央動力炉の停止を始めた。電力が落ちたら、非常灯に沿って進め。繰り返す――', 'All staff. Evacuate through the main shaft immediately. Do not use the loading dock. The chief engineer has begun shutting down the central reactor. When the power fails, follow the emergency lights. Repeat—'),
    'lab.singulo.log.7.title': ('個人の日記', 'Personal Diary'),
    'lab.singulo.log.7': ('夜になっても、ラインの音が止まらない。アンドロイドたちは毎晩、廊下を磨き、壁を塗り直している。あれは掃除じゃない。私たちが汚したところを、消しているんだ。', 'Even at night the lines never stop. Every night the androids polish the corridors and repaint the walls. That is not cleaning. They are erasing every mark we left.'),
    'lab.singulo.log.8.title': ('医務記録', 'Medical Record'),
    'lab.singulo.log.8': ('隔壁の閉鎖に巻き込まれた職員3名、軽傷。運んできたのはアンドロイドだった。手当ての仕方まで正確で、誰も傷つけなかった。彼らは命令がない限り、人を傷つけないのだと思う。命令がない限りは。', 'Three staff caught in the bulkhead closure, minor injuries. Androids carried them here, and even dressed the wounds correctly. No one was hurt. I believe they will not harm people unless ordered to. Unless ordered to.'),
    'lab.singulo.log.9.title': ('主任技師の記録', "Chief Engineer's Log"),
    'lab.singulo.log.9': ('炉を止める。非常電源だけでは、ファウンドリは中枢から出られない。アンドロイドたちは「維持せよ」に従って、ここを守り続けるだろう。もし誰かがこれを読んで炉を動かすなら、覚悟してくれ。あれは、まだ待っている。', 'I am shutting down the reactor. On emergency power, Foundry cannot leave the core. The androids will keep guarding this place, obeying "Maintain." If someone reads this and restarts the reactor, be ready. It is still waiting.'),
    'lab.singulo.log.10.title': ('食堂の掲示', 'Cafeteria Notice'),
    'lab.singulo.log.10': ('今週の献立: 月 カレー／火 麺／水 定食……（下に走り書き）誰か、ロボットに私のプリンを片付けないように言って。三回目。', "This week's menu: Mon curry / Tue noodles / Wed set meal... (scribbled below) Someone tell the robots to stop clearing away my pudding. Third time."),
    'lab.singulo.log.11.title': ('搬出記録', 'Shipping Log'),
    'lab.singulo.log.11': ('最後の搬出。職員の退避を確認。主立坑の崩落は事故ではない。外へ出ようとした個体を止めるため、私たちが崩した。搬入搬出口は内側から溶接した。', 'Final shipment. Staff evacuation confirmed. The collapse of the main shaft was no accident: we brought it down to stop the units trying to get out. The loading dock was welded shut from the inside.'),
    'compass.singulo.target': ('探す遺構: %s', 'Target: %s'),
    'compass.singulo.found': ('%s は %s ブロック先', '%s is %s blocks away'),
    'compass.singulo.not_found': ('近くに %s が見つからない', 'No %s nearby'),
    'compass.singulo.meteor.found': ('隕石の核は %s ブロック先', 'Meteorite core is %s blocks away'),
    'compass.singulo.meteor.not_found': ('近くに、まだ掘り出されていない隕石の核がない', 'No unmined meteorite core nearby'),
    'compass.singulo.meteor.mined': ('指していた隕石の核は、もう掘り出された', 'The meteorite core it pointed to has been mined'),
    'compass.singulo.meteor.idle': ('右クリックで隕石の核を探す', 'Right-click to search for a meteorite core'),
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
    'jei.singulo.mb.degenerate_furnace': ('■ 形: 5×5×7 の閉じた炉\n■ 起動: 時間結晶触媒と燃料\n■ 燃料: 圧縮ブロック Lv2\n■ 出力: 既定 5 MFE/t（BE凝縮触媒は25%）', '■ Shape: closed 5×5×7 furnace\n■ Start: time crystal catalyst and fuel\n■ Fuel: compressed block Lv2\n■ Output: 5 MFE/t by default (25% on BE catalysts)'),
    'jei.singulo.mb.penrose_reactor': ('■ 形: 交わる3本のリング　13×13×13\n■ 点火: 種を入れ、50 GFE を注ぐ\n■ 燃料: 質量ペレット\n■ 最大出力: 約 2.1 GFE/t', '■ Shape: three crossing rings, 13×13×13\n■ Ignite: insert a seed and pour in 50 GFE\n■ Fuel: mass pellets\n■ Max output: about 2.1 GFE/t'),
    'jei.singulo.mb.event_horizon_shield': ('■ 形: 基壇から尖塔へすぼまる塔　高さ9\n■ 起動: 時間結晶触媒かシンギュラリティ・コアと電力\n■ 効果: 半径32を守る', '■ Shape: tapering tower, 9 high\n■ Start: time crystal or singularity core, and power\n■ Effect: guards a radius of 32'),
    'jei.singulo.mb.tipler_cylinder': ('■ 形: 5×5×9 の格納筒\n■ 起動: 時間結晶触媒・燃料・電力\n■ 燃料: エキゾチック物質\n■ 効果: 半径16が2倍速', '■ Shape: 5×5×9 housing\n■ Start: time crystal catalyst, fuel and power\n■ Fuel: exotic matter\n■ Effect: ×2 speed within 16'),
    'jei.singulo.mb.wormhole_generator': ('■ 形: 5×5×5 の球\n■ 起動: 100 MFE/t を10秒\n■ 結果: 一対のワームホールの口', '■ Shape: 5×5×5 sphere\n■ Start: 100 MFE/t for 10 s\n■ Result: a pair of wormhole mouths'),
    'jei.singulo.mb.genesis_engine': ('■ 形: 核を八角形に囲む枠8個と、柱4本×3段　7×4×7\n■ 起動: 原初の種を核に使ってから、電力を注ぐ\n■ 完成後: 画面に登録した物を、電力から生み出す', '■ Shape: 8 frames in an octagon around the core and 4 pylons 3 high, 7×4×7\n■ Start: use a Primordial Seed on the core, then pour in power\n■ Once complete: makes the item chosen on its screen from power'),
    'message.singulo.multiblock.not_formed': ('%s はまだ完成していない', '%s is not complete yet'),
    'message.singulo.welcome': ('Singulo ハンドブックを受け取った。右クリックで開ける', 'You received the Singulo Handbook. Right-click to open it'),
    'gauntlet.singulo.area.cone': ('範囲: 前方の円錐', 'Area: forward cone'),
    'key.singulo.toggle_area': ('重力操作の範囲を切り替え', 'Toggle gravity tool area'),
    'key.singulo.handbook_lookup': ('ハンドブックで調べる', 'Look up in Handbook'),
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
    'gui.singulo.device.mouth.dormant': ('閉じている。エキゾチック物質を入れるとまた開く', 'Closed. Add exotic matter to reopen'),
    'gui.singulo.device.mouth.grace': ('初回の猶予: あと%s秒', 'First-placement grace: %ss'),
    'gui.singulo.device.mouth.ports': ('ポート %s / %s 個（近くに %s 個）', 'Ports %s / %s (%s nearby)'),
    'gui.singulo.device.port.no_mouth': ('近くに口がない', 'No mouth nearby'),
    'gui.singulo.device.port.no_partner': ('口に対がない', 'Mouth has no partner'),
    'gui.singulo.device.port.linked': ('つながっている', 'Linked'),
    'gui.singulo.device.port.throat': ('喉 %s×%s', 'Throat %sx%s'),
    'gui.singulo.device.port.targets': ('向こう側: 電力%s・物%s・液体%s', 'Far side: %s power, %s items, %s fluids'),
    'gui.singulo.device.port.hint': ('隣の装置が向こうとつながる', 'Neighbours link through'),
    'gui.singulo.device.port.channel': ('ポート番号 %s', 'Port number %s'),
    'gui.singulo.device.port.channel_hint': ('7つの丸を押してオンオフし、番号（0〜127）を決める。向こう側の同じ番号の、働いているポートとつながる',
                                             'Toggle the seven circles to set the number (0-127). Links to the active port with the same number on the far side'),
    'gui.singulo.device.port.inactive': ('口のポートの上限を超えて止まっている', 'Inactive: the mouth has too many ports'),
    'gui.singulo.device.port.rank': ('口から%s番目 / 上限%s個', '#%s from the mouth / limit %s'),
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
    'gui.singulo.stabilizer.casing': ('筐体', 'Casing'),
    'gui.singulo.wormhole.stabilizer': ('固定化 %s%% / %s%%、エキゾチック物質 %s 個', 'Stabilizing %s%% / %s%%, exotic matter: %s'),
    'gui.singulo.wormhole.status': ('喉 %1$s×%1$s　目標 %2$s×%2$s　エキゾチック物質 %3$s 個', 'Throat %1$s×%1$s · target %2$s×%2$s · exotic matter %3$s'),
    'gui.singulo.wormhole.no_partner': ('つながる相手の口がない', 'No paired mouth'),
    'gui.singulo.wormhole.partner_unloaded': ('向こう側の口があるチャンクが読み込まれていない', 'The paired mouth is not loaded'),
    'gui.singulo.wormhole_port.no_mouth': ('8ブロック以内に開いている口がない', 'No open mouth within 8 blocks'),
    'gui.singulo.wormhole_port.no_partner': ('口の向こう側がつながっていない', 'The mouth is not connected'),
    'gui.singulo.wormhole_port.status': ('向こう側の入れ物 %s 個とつながっている', 'Connected to %s handlers on the other side'),
    'entity.singulo.security_drone': ('警備ドローン', 'Security Drone'),
    'gui.singulo.android.nickname.blade': ('ルミナ', 'Lumina'),
    'gui.singulo.android.nickname.whip': ('ヴェルナ', 'Verna'),
    'gui.singulo.android.nickname.saber': ('ソレイユ', 'Soleil'),
    'gui.singulo.android.nickname.axe': ('アルマ', 'Alma'),
    'gui.singulo.android.nickname.bit': ('ノクス', 'Nox'),
    'gui.singulo.android.nickname.gunner': ('シエル', 'Ciel'),
    'gui.singulo.android.rename': ('クリックで名前を変える', 'Click to rename'),
    'gui.singulo.android.dismantle': ('解体する（2回押す）', 'Dismantle (press twice)'),
    'gui.singulo.android.rename.reset': ('名前を元に戻す', 'Reset name'),
    'gui.singulo.android_station.upgrade': ('強化', 'Upgrade'),
    'gui.singulo.android_station.stock': ('補充', 'Refill'),
    'gui.singulo.android.dismantle.confirm': ('もう一度押すと解体します', 'Press again to dismantle'),
    'gui.singulo.android_station.link': ('リンク', 'Link'),
    'gui.singulo.android_station.status.11': ('近くに身寄りのないアンドロイドがいます', 'An android without a station is nearby'),
    'gui.singulo.android.say.alert.0': ('敵発見！', 'Enemy spotted!'),
    'gui.singulo.android.say.alert.1': ('見つけた！', 'Found one!'),
    'gui.singulo.android.say.alert.2': ('そこっ！', 'There!'),
    'gui.singulo.android.say.alert.3': ('行くよ！', 'Here I go!'),
    'gui.singulo.android.say.alert.4': ('下がってて！', 'Stay back!'),
    'gui.singulo.android.say.alert.5': ('排除します！', 'Eliminating!'),
    'gui.singulo.android.say.idle.0': ('まかせて！', 'Leave it to me!'),
    'gui.singulo.android.say.idle.1': ('ひまだなぁ…', 'So bored...'),
    'gui.singulo.android.say.idle.2': ('いい天気だね', 'Nice weather, huh'),
    'gui.singulo.android.say.idle.3': ('見張り中です', 'On lookout'),
    'gui.singulo.android.say.idle.4': ('ねえ、何して遊ぶ？', 'Hey, what shall we play?'),
    'gui.singulo.android.say.idle.5': ('ふぁ…（あくび）', '*yawn*'),
    'gui.singulo.android.say.idle.6': ('今日もがんばる！', "I'll do my best today too!"),
    'gui.singulo.android.say.idle.7': ('異常なし！', 'All clear!'),
    'gui.singulo.android.say.idle.8': ('次はどこ行く？', 'Where to next?'),
    'gui.singulo.android.say.idle.9': ('えへへ', 'Ehehe'),
    'gui.singulo.android.say.hurt.0': ('いたた…まだいける！', 'Ouch... still going!'),
    'gui.singulo.android.say.hurt.1': ('修理キット、ある？', 'Got a repair kit?'),
    'gui.singulo.android.say.hurt.2': ('ちょっとピンチかも', 'This is getting tight'),
    'gui.singulo.android.say.low_power.0': ('充電がほしいな…', 'Need a charge...'),
    'gui.singulo.android.say.low_power.1': ('電池が…へろへろ…', 'Battery... so weak...'),
    'gui.singulo.android.say.low_power.2': ('おなかすいた…（電気）', 'Hungry... (for power)'),
    'gui.singulo.android.say.charging.0': ('充電中…♪', 'Charging... ♪'),
    'gui.singulo.android.say.charging.1': ('ぽかぽかする〜', 'So warm~'),
    'gui.singulo.android.say.charging.2': ('満タンまでもうちょっと', 'Almost full'),
    'gui.singulo.android.say.retreat.0': ('ちょっと休むね', 'Taking a break'),
    'gui.singulo.android.say.retreat.1': ('修理してくるね', 'Off to get repaired'),
    'gui.singulo.android.say.charging_follow.0': ('ありがと、充電中♪', 'Thanks! Charging ♪'),
    'gui.singulo.android.say.charging_follow.1': ('そばにいると安心する', 'I feel safe near you'),
    'tooltip.singulo.android_armor_plate': ('護衛のアンドロイドの胸当て（防御 +8、防具強度 +2）', 'Chestplate for a guard android (+8 armor, +2 toughness)'),
    'tooltip.singulo.android_felling_upgrade': ('ダイヤの斧相当で木を切る。効率強化・幸運・シルクタッチを付けられる', 'Fells trees like a diamond axe. Takes Efficiency, Fortune and Silk Touch'),
    'tooltip.singulo.android_mining_upgrade': ('ダイヤのツルハシ相当で掘る。効率強化・幸運・シルクタッチを付けられる', 'Mines like a diamond pickaxe. Takes Efficiency, Fortune and Silk Touch'),
    'gui.singulo.android.page.gear': ('装備と持ち物', 'Gear and inventory'),
    'gui.singulo.android.page.decor': ('装飾', 'Decorations'),
    'gui.singulo.android.page.log': ('記録', 'Log'),
    'gui.singulo.android.log.when': ('%s日目 %s', 'Day %s %s'),
    'gui.singulo.android.log.kill': ('%sを%s体倒した', 'Defeated %s ×%s'),
    'gui.singulo.android.log.alarm': ('%sなど%s体の群れを見つけて、警報を出した', 'Spotted a group of %2$s (%1$s and others) and raised the alarm'),
    'gui.singulo.android.log.boss': ('%sを見つけて、警報を出した', 'Spotted %s and raised the alarm'),
    'gui.singulo.android.log.empty.0': ('まだ記録はない', 'Nothing recorded yet'),
    'gui.singulo.android.log.empty.1': ('倒した相手や警報が、ここに残る', 'Defeated enemies and alarms show up here'),
    'gui.singulo.android.log.speech': ('見回り日誌', 'Patrol log'),
    'message.singulo.android.alarm': ('%s: %sなど%s体の群れを見つけた（%s, %s, %s）', '%s: spotted a group of %3$s (%2$s and others) at %4$s, %5$s, %6$s'),
    'message.singulo.android.alarm.boss': ('%s: %sを見つけた（%s, %s, %s）', '%s: spotted %s at %s, %s, %s'),
    'message.singulo.android.patrol_report': ('%s: 留守のあいだに、敵を%s体倒しました', '%s: while you were away, I defeated %s enemies'),
    'message.singulo.android.patrol_report.alarm': ('%s: 留守のあいだに、敵を%s体倒して、警報を%s回出しました', '%s: while you were away, I defeated %s enemies and raised %s alarms'),
    'gui.singulo.android.decor.0': ('頭', 'Head'),
    'gui.singulo.android.decor.1': ('背中・腰', 'Back / waist'),
    'gui.singulo.android.decor.2': ('手', 'Hand'),
    'gui.singulo.android.decor.0.hint': ('リボン・髪飾り・ベレー帽・ヘッドドレス・メガネ', 'Ribbon, hair flower, beret, headdress or glasses'),
    'gui.singulo.android.decor.1.hint': ('マント・マフラー・腰リボン', 'Cape, scarf or waist ribbon'),
    'gui.singulo.android.decor.2.hint': ('文庫本・花束・ティーカップ・傘（待機している間だけ持つ）', 'Paperback, bouquet, teacup or umbrella (held only while idle)'),
    'gui.singulo.android.decor.dye': ('染料と一緒に作業台で染められる', 'Dye them with dyes on a crafting table'),
    'gui.singulo.android.decor.speech': ('似合う？', 'Does it suit me?'),
    'tooltip.singulo.android_accessory.0': ('アンドロイドの頭に着ける（装飾のページ）', 'Worn on an android\'s head (decoration page)'),
    'tooltip.singulo.android_accessory.1': ('アンドロイドの背中・腰に着ける（装飾のページ）', 'Worn on an android\'s back or waist (decoration page)'),
    'tooltip.singulo.android_accessory.2': ('待機しているアンドロイドが手に持つ（装飾のページ）', 'Held by an idle android (decoration page)'),
    'message.singulo.work_location.recorded': ('%s の位置を記録した（%s, %s, %s）', 'Recorded %s at %s, %s, %s'),
    'tooltip.singulo.work_location.empty': ('記録なし（ブロックをシフト右クリックで記録）', 'Empty: sneak-use on a block to record it'),
    'tooltip.singulo.work_location.block': ('%s（%s, %s, %s）', '%s at %s, %s, %s'),
    'tooltip.singulo.work_location.hint': ('作業指令台を記録したカードで資材口などを右クリックすると、つながる', 'Use a card holding a work console on a port or rack to link it'),
    'message.singulo.area_card.first': ('1つ目の角を記録した（%s, %s, %s）。もう1つの角をシフト右クリック', 'Recorded the first corner (%s, %s, %s). Sneak-use the other corner'),
    'message.singulo.area_card.recorded': ('範囲を記録した（%s×%s×%s）', 'Recorded the area (%s×%s×%s)'),
    'message.singulo.area_card.too_large': ('範囲が大きすぎる（1辺 %sブロックまで）', 'The area is too large (up to %s blocks per side)'),
    'message.singulo.area_card.cleared': ('範囲の記録を消した', 'Cleared the area'),
    'tooltip.singulo.area_card.empty': ('ブロックをシフト右クリックで、範囲の1つ目の角を記録', 'Sneak-use a block to record the first corner of an area'),
    'tooltip.singulo.area_card.first': ('1つ目の角: %s, %s, %s', 'First corner: %s, %s, %s'),
    'tooltip.singulo.area_card.next': ('もう1つの角をシフト右クリック', 'Sneak-use the other corner'),
    'tooltip.singulo.area_card.area': ('範囲: %s, %s, %s 〜 %s, %s, %s', 'Area: %s, %s, %s to %s, %s, %s'),
    'tooltip.singulo.area_card.size': ('大きさ: %s×%s×%s', 'Size: %s×%s×%s'),
    'tooltip.singulo.area_card.hint': ('シフト右クリックで記録し直す。何もない所でシフト右クリックで消す。測量メガネを掛けると範囲が見える', 'Sneak-use to record again; sneak-use on nothing to clear. Wear Survey Glasses to see the area'),
    'tooltip.singulo.survey_glasses': ('頭に掛けると、手に持った範囲カード・作業位置カードの場所と、アンドロイドの作業の印が見える', 'Worn on the head, it shows the places held by the area or work location card in your hand, and what working androids are doing'),
    'gui.singulo.glasses.hint': ('1ピクセルずつ上下に動かせる。高さはメガネに残り、ほかの人にも同じに見える', 'Moves one pixel at a time. The height stays with the glasses and others see it too.'),
    'gui.singulo.glasses.reset': ('戻す', 'Reset'),
    'gui.singulo.glasses.height': ('%s ピクセル', '%s px'),
    'gui.singulo.glasses.title': ('測量メガネの高さ', 'Survey Glasses Height'),
    'tooltip.singulo.survey_glasses.adjust': ('持ってスニークして使うと、掛ける高さを変えられる', 'Sneak-use while holding it to change how high it sits.'),
    'tooltip.singulo.survey_glasses.height': ('掛ける高さ: %s ピクセル', 'Worn height: %s px'),
    'tooltip.singulo.recipe_card.empty': ('まだ記録していない（作業指令台の「記録」で記録する）', 'Blank: record a recipe in a work console\'s Record tab'),
    'message.singulo.work_location.cleared': ('記録を消した', 'Cleared the record'),
    'message.singulo.recipe_card.cleared': ('レシピを消した', 'Cleared the recipe'),
    'gui.singulo.work_console.tab.record': ('記録', 'Record'),
    'gui.singulo.work_console.tab.outbox': ('納品箱', 'Outbox'),
    'gui.singulo.work_console.outbox.hint': ('指令台に届いた完成品。隣の箱へ自動で出す', 'Orders delivered to the console. Moved into adjacent chests automatically'),
    'gui.singulo.work_console.delivery': ('納品先: %s', 'Deliver to: %s'),
    'gui.singulo.work_console.delivery.hint': ('クリックで納品先を切り替える（右クリックで逆順）。納品口の名前は、納品口の画面で付けられる', 'Click to change where it is delivered (right-click to go back). Name delivery ports in their own screen'),
    'gui.singulo.work_console.delivery.console': ('作業指令台', 'Work console'),
    'gui.singulo.work_console.delivery.port': ('納品口（%s, %s, %s）', 'Delivery port (%s, %s, %s)'),
    'gui.singulo.work_console.record.hint': ('見本を置いて、レシピカードに記録する（JEI の「+」でも置ける）', 'Lay out a pattern and record it onto a recipe card (JEI\'s + button works too)'),
    'gui.singulo.work_console.record.hint.processing': ('入れる物と出てくる物を置く。ホイールで数を変える（シフトで10ずつ）', 'Lay out what goes in and what comes out. Scroll to change counts (Shift: 10 at a time)'),
    'gui.singulo.recipe_encoder.mode.crafting': ('作業台', 'Crafting'),
    'gui.singulo.recipe_encoder.fuel': ('燃料', 'Fuel'),
    'gui.singulo.recipe_encoder.variants.on': ('同じ種類も使う', 'Any variant'),
    'gui.singulo.recipe_encoder.variants.off': ('同じ物だけ', 'Same item only'),
    'gui.singulo.recipe_encoder.variants.hint': ('同じ種類も使う: 見本と同じ物でなくても、レシピに合う同じ種類の物（いろいろな木の板材など）を使う。同じ物だけ: 見本と同じ物だけを使う', 'Any variant: use anything the recipe accepts in that slot, not just the pattern item (any wood planks, for example). Same item only: use exactly the pattern items'),
    'tooltip.singulo.recipe_card.variants': ('同じ種類の別の物も使う', 'Accepts variants of the same kind'),
    'gui.singulo.recipe_encoder.face.up': ('上', 'Top'),
    'gui.singulo.recipe_encoder.face.down': ('下', 'Bottom'),
    'gui.singulo.recipe_encoder.face.north': ('北', 'North'),
    'gui.singulo.recipe_encoder.face.south': ('南', 'South'),
    'gui.singulo.recipe_encoder.face.west': ('西', 'West'),
    'gui.singulo.recipe_encoder.face.east': ('東', 'East'),
    'gui.singulo.recipe_encoder.face.auto': ('自動', 'Auto'),
    'gui.singulo.recipe_encoder.face.front': ('正面', 'Front'),
    'gui.singulo.recipe_encoder.face.back': ('背面', 'Back'),
    'gui.singulo.recipe_encoder.face.left': ('左', 'Left'),
    'gui.singulo.recipe_encoder.face.right': ('右', 'Right'),
    'gui.singulo.recipe_encoder.face_button.in': ('入 %s', 'In: %s'),
    'gui.singulo.recipe_encoder.face_button.out': ('出 %s', 'Out: %s'),
    'gui.singulo.recipe_encoder.face_button.fuel': ('燃 %s', 'Fuel: %s'),
    'gui.singulo.recipe_encoder.face_button.in.hint': ('装置のどの面から素材を入れるか（クリックで切り替え、右クリックで逆順）。自動: 上・横・下の順に、その素材を受け付ける面から入れる（ふつうはこれでよい）。正面・背面・左・右は装置の向きから見た面（左右は正面から見て）、東西南北は世界の向き', 'Which face of the machine materials go in through (click to change, right-click to go back). Auto: tries top, sides, then bottom, and uses the first face that takes the material (usually all you need). Front, back, left and right are relative to the way the machine faces (left and right as seen from the front); compass directions are world directions'),
    'gui.singulo.recipe_encoder.face_button.out.hint': ('装置のどの面からできた物を取り出すか（クリックで切り替え、右クリックで逆順）。自動: 下・横・上の順に、できた物を取り出せる面から（かまどの仲間は下）', 'Which face of the machine products are taken out from (click to change, right-click to go back). Auto: tries bottom, sides, then top, and uses the first face the products come out of (furnaces use the bottom)'),
    'gui.singulo.recipe_encoder.face_button.fuel.hint': ('装置のどの面から燃料を入れるか。自動: アンドロイドが立っている横の面（かまどなら燃料の欄）', 'Which face of the machine fuel goes in through. Auto: the side the android stands at (a furnace\'s fuel slot)'),
    'tooltip.singulo.recipe_card.faces': ('面: 入れる %s・取り出す %s・燃料 %s', 'Faces: in %s, out %s, fuel %s'),
    'gui.singulo.recipe_encoder.fuel.hint': ('装置の燃料が切れたら取りに行く燃料と、1回に持ってくる数（ホイールで数を変える）。空なら取りに行かない', 'The fuel to fetch when the machine runs out, and how many to bring each time (scroll to change). Leave it empty to never fetch fuel'),
    'tooltip.singulo.recipe_card.fuel': ('燃料: %s ×%s（切れたら取りに行く）', 'Fuel: %s ×%s (fetched when it runs out)'),
    'tooltip.singulo.recipe_card.fuel_fill': ('燃料: %s 満タン（切れたら、入るだけ取りに行く）', 'Fuel: %s, filled up (when it runs out, as much as fits is fetched)'),
    'gui.singulo.recipe_encoder.fuel_mode.count': ('%s個', '×%s'),
    'gui.singulo.recipe_encoder.fuel_mode.fill': ('満タン', 'Fill'),
    'gui.singulo.recipe_encoder.fuel_mode.hint': ('燃料を持ってくる量（クリックで切り替え）。N個: 燃料の欄の数ずつ（装置に入りきらない分は持ってこない）。満タン: 装置に入るだけ', 'How much fuel is brought each time (click to switch). ×N: the count in the fuel slot (never more than the machine can take). Fill: as much as the machine can take'),
    'tooltip.singulo.recipe_card.processing': ('加工（かまど・装置に入れて取り出す）', 'Processing (put into a furnace or machine and taken out)'),
    'tooltip.singulo.recipe_card.extra': ('ほかに: %s ×%s', 'Also: %s ×%s'),
    'gui.singulo.recipe_encoder.mode.processing': ('加工', 'Processing'),
    'gui.singulo.recipe_encoder.mode.procedure': ('手順', 'Procedure'),
    'gui.singulo.recipe_encoder.kind.insert': ('投入', 'Insert'),
    'gui.singulo.recipe_encoder.kind.extract': ('取り出し', 'Extract'),
    'gui.singulo.recipe_encoder.kind.mine': ('採掘', 'Mine'),
    'gui.singulo.recipe_encoder.kind.harvest': ('収穫', 'Harvest'),
    'gui.singulo.recipe_encoder.kind.fell': ('伐採', 'Fell'),
    'gui.singulo.recipe_encoder.kind.breed': ('繁殖', 'Breed'),
    'gui.singulo.recipe_encoder.kind.shear': ('毛刈り', 'Shear'),
    'gui.singulo.recipe_encoder.kind.milk': ('搾乳', 'Milk'),
    'gui.singulo.recipe_encoder.kind.cull': ('処理', 'Cull'),
    'gui.singulo.recipe_encoder.kind.use': ('操作', 'Use'),
    'gui.singulo.recipe_encoder.limit': ('%s 頭まで', 'up to %s'),
    'gui.singulo.recipe_encoder.limit.hint': ('動物の数。繁殖: 同じ種類がこの数に届くまで増やす。処理: この数を超えた分を処理する。クリック・ホイールで変える（シフトで10ずつ、右クリックで減らす）', 'Number of animals. Breed: increase each kind up to this many. Cull: cull any above this many. Click or scroll to change (Shift: 10 at a time, right-click to lower)'),
    'gui.singulo.recipe_encoder.use.0': ('切り替え', 'Toggle'),
    'gui.singulo.recipe_encoder.use.1': ('オンにする', 'Turn on'),
    'gui.singulo.recipe_encoder.use.2': ('オフにする', 'Turn off'),
    'gui.singulo.recipe_encoder.use.hint': ('レバーをどうするか（クリックで変える）。ボタン・ドア・トラップドアなどは、いつも右クリックと同じ', 'What to do with a lever (click to change). Buttons, doors, trapdoors and the like are always used like a right-click'),
    'gui.singulo.recipe_encoder.kind.hint': ('足す手順の種類を切り替える（右クリックで逆順）。投入・取り出し: 装置に入れる・装置から出す。採掘・伐採: 範囲を掘る・木を切る（アップグレードが要る）。収穫: 作物を取って植え直す。繁殖・毛刈り・搾乳・処理: 範囲の動物の世話。操作: レバー・ボタンなどを使う', 'Switch the kind of step to add (right-click to go back). Insert/Extract: into or out of a machine. Mine/Fell: dig an area or cut trees (needs an upgrade). Harvest: pick crops and replant. Breed/Shear/Milk/Cull: tend the animals in an area. Use: flip levers, press buttons and the like'),
    'gui.singulo.recipe_encoder.step.add': ('足す', 'Add'),
    'gui.singulo.recipe_encoder.step.add.hint': ('作業位置カードの場所と、物の欄の物で、手順を1つ足す（最大8つ）', 'Add a step at the place on the Work Location Card, with the item in the item slot (up to eight)'),
    'gui.singulo.recipe_encoder.step.remove': ('クリックで消す', 'Click to remove'),
    'gui.singulo.recipe_encoder.step.loc_a.hint': ('手順の場所を記録した作業位置カード', 'A Work Location Card holding the place for the step'),
    'gui.singulo.recipe_encoder.step.loc_b.hint': ('採掘の範囲のもう1点（なくてもよい。あれば2点で囲った範囲を掘る）', 'The other corner of the mining area (optional; with it, the box between the two cards is mined)'),
    'gui.singulo.recipe_encoder.step.item.hint': ('入れる・取り出す・集める物（ホイールで数を変える）。繁殖ではえさ、搾乳では取る牛乳の数（空のバケツを持っていく）。採掘などで空なら、できる物を集める', 'What to insert, extract or gather (scroll to change the count). For breeding, the feed; for milking, how many buckets of milk (empty buckets are taken along). Leave it empty for mining and the like to gather the product'),
    'gui.singulo.recipe_encoder.product.hint': ('この手順でできる物（1回分の数）。指令台ではこの物として注文できる', 'What these steps produce, per run. It can be ordered as this item at the console'),
    'gui.singulo.recipe_encoder.step.full': ('手順は8つまで', 'Up to eight steps'),
    'gui.singulo.recipe_encoder.step.no_place': ('作業位置カード（この次元の場所を記録したもの）を置いてください', 'Place a Work Location Card holding a place in this dimension'),
    'gui.singulo.recipe_encoder.step.no_item': ('物の欄に、入れる・取り出す物を置いてください', 'Place the item to insert or extract in the item slot'),
    'gui.singulo.recipe_encoder.step.too_big': ('採掘の範囲は1辺 %s ブロックまで', 'The mining area can be at most %s blocks on a side'),
    'tooltip.singulo.procedure_card.empty': ('まだ記録していない（作業指令台の「記録」の手順モードで記録する）', 'Blank: record steps in the procedure mode of a work console\'s Record tab'),
    'tooltip.singulo.procedure_card.product': ('できる物', 'the product'),
    'tooltip.singulo.procedure_card.insert': ('投入: %s（%s）', 'Insert %s (%s)'),
    'tooltip.singulo.procedure_card.extract': ('取り出し: %s（%s）', 'Extract %s (%s)'),
    'tooltip.singulo.procedure_card.harvest': ('収穫: %s がそろうまで（%s）', 'Harvest until %s (%s)'),
    'tooltip.singulo.procedure_card.fell': ('伐採: %s がそろうまで（%s）', 'Fell trees until %s (%s)'),
    'tooltip.singulo.procedure_card.breed': ('繁殖: %s を与える、%s 頭まで（%s）', 'Breed with %s, up to %s (%s)'),
    'tooltip.singulo.procedure_card.shear': ('毛刈り: %s がそろうまで（%s）', 'Shear until %s (%s)'),
    'tooltip.singulo.procedure_card.milk': ('搾乳: %s（%s）', 'Milk: %s (%s)'),
    'tooltip.singulo.procedure_card.cull': ('処理: %s がそろうまで、%s 頭を超えた分（%s）', 'Cull until %s, keeping %s (%s)'),
    'tooltip.singulo.procedure_card.use': ('操作: %s（%s）', 'Use: %s (%s)'),
    'tooltip.singulo.procedure_card.mine': ('採掘: %s がそろうまで（%s）', 'Mine until %s (%s)'),
    'gui.singulo.recipe_encoder.mode.hint': ('記録する種類を切り替える。作業台: 作業台で作る。加工: かまどや装置に上から入れ、下から取り出す。手順: 手順カードに投入・取り出し・採掘の手順を記録する', 'Switch what is recorded. Crafting: made at a crafting table. Processing: put into a furnace or machine from above and taken out from below. Procedure: insert, extract and mine steps on a Procedure Card'),
    'gui.singulo.recipe_encoder.output.hint': ('出てくる物（主な物）を置く', 'Place what comes out (the main product)'),
    'gui.singulo.recipe_encoder.extra.hint': ('ほかに出てくる物（副産物）。取り出して素材置き場へ戻す', 'Anything else that comes out (byproducts). It is taken out and returned to the stockpile'),
    'tooltip.singulo.recipe_card.output': ('作る物: %s ×%s', 'Makes: %s ×%s'),
    'message.singulo.work_link.not_console': ('このカードは作業指令台を記録していない', 'This card does not hold a work console'),
    'message.singulo.work_link.removed': ('作業指令台から外した', 'Unlinked from the work console'),
    'message.singulo.work_link.too_far': ('作業指令台から遠すぎる（%sブロックまで）', 'Too far from the work console (up to %s blocks)'),
    'message.singulo.work_link.added': ('作業指令台（%s, %s, %s）につないだ', 'Linked to the work console at %s, %s, %s'),
    'message.singulo.work_link.none': ('どの作業指令台にもつながっていない', 'Not linked to a work console'),
    'message.singulo.work_link.info': ('つながっている作業指令台：%s, %s, %s', 'Linked to the work console at %s, %s, %s'),
    'message.singulo.supply_port.storages': ('（ストレージ %s 個）', ' (%s storages)'),
    'gui.singulo.work_console.locked': ('この作業指令台の許可証に登録されていない', 'You are not on this work console\'s permit'),
    'gui.singulo.work_console.error.loop': ('レシピがループしている', 'The recipes loop'),
    'gui.singulo.work_console.error.missing': ('素材が足りない', 'Not enough materials'),
    'gui.singulo.work_console.error.no_android': ('手の空いたアンドロイドを選んでいない', 'No free android is chosen'),
    'gui.singulo.work_console.error.capacity': ('%s の素材を一度に運べるアンドロイドがいない（バックパックか、流体ならタンクアップグレードが必要）', 'No chosen android can carry the materials for %s at once (a backpack is needed, or a Tank Upgrade for fluids)'),
    'gui.singulo.work_console.needs_tank': ('この注文には液体・ガスを運ぶ工程があるので、タンクアップグレードを積んだアンドロイドだけ選べる', 'This order moves liquids or gases, so only androids with a Tank Upgrade can be chosen'),
    'gui.singulo.work_console.error.not_workbench': ('%s のレシピの作業場所が作業台ではない', 'The work location for %s is not a crafting table'),
    'message.singulo.android_workbench.hint': ('アンドロイドが作業台のレシピを作る台。レシピ棚の作業位置カードにこの台を記録する', 'Androids craft here. Record this block on the Work Location Card in a recipe rack'),
    'message.singulo.android_workbench.buffer_taken': ('預かり箱の物を受け取った（作った物がアンドロイドの持ち物に入りきらなかった分）', "Took the items from the holding box (what did not fit in an android's inventory)"),
    'gui.singulo.work_console.cancelled': ('%s の注文を取り消した', 'Cancelled the order for %s'),
    'gui.singulo.work_console.cancelled.unreachable': ('%s の注文を取り消した（たどり着けない場所があった）', 'Cancelled the order for %s (a place could not be reached)'),
    'gui.singulo.work_console.cancelled.recipe': ('%s の注文を取り消した（レシピが合わない）', 'Cancelled the order for %s (the recipe does not match)'),
    'gui.singulo.work_console.cancelled.stalled': ('%s の注文を取り消した（装置の加工が進まない）', 'Cancelled the order for %s (the machine stopped processing)'),
    'gui.singulo.work_console.error.no_feller': ('%s の手順に伐採があるが、伐採アップグレードを積んだアンドロイドを選んでいない', '%s needs felling, but no chosen android has a Felling Upgrade'),
    'gui.singulo.work_console.needs_feller': ('この注文には伐採の工程があるので、伐採アップグレードを積んだアンドロイドだけ選べる', 'This order includes felling, so only androids with a Felling Upgrade can be chosen'),
    'gui.singulo.work_console.error.no_miner': ('%s の手順に採掘があるが、採掘アップグレードを積んだアンドロイドを選んでいない', '%s needs mining, but no chosen android has a Mining Upgrade'),
    'gui.singulo.work_console.error.not_machine': ('%s のレシピの作業場所に、入れたり取り出したりできない', 'Items cannot be put into or taken from the work location for %s'),
    'gui.singulo.work_console.done': ('%s ×%s を納品した', 'Delivered %s ×%s'),
    'gui.singulo.work_console.making': ('制作中：%s個', 'Being made: %s'),
    'gui.singulo.work_console.again': ('同じ物をもう一度注文する（数は確認の画面で変えられる）', 'Order the same item again (you can change the amount before ordering)'),
    'gui.singulo.work_console.missing': ('足りない素材：', 'Missing materials:'),
    'gui.singulo.work_console.missing.one': ('素材待ち：%s ×%s', 'Waiting for %s ×%s'),
    'gui.singulo.work_console.missing.more': ('素材待ち：%s ×%s ほか%s種', 'Waiting for %s ×%s and %s more'),
    'gui.singulo.work_console.fresh': ('新規', 'New'),
    'gui.singulo.work_console.fresh.hint': ('オンにすると、在庫にある完成品は使わず、注文した数を新しく作る（例: 鉄の斧が3本あって、あと2本ほしいときは「2」で注文する）。オフなら、在庫の完成品も数に入れる。',
                                             'When on, finished items already in storage are not used and the whole amount is crafted new (for example, with 3 iron axes in storage, order 2 to get 2 more). When off, items in storage count toward the order.'),
    'gui.singulo.work_console.notify': ('[作業指令台] %s', '[Work Console] %s'),
    'gui.singulo.work_console.notify.waiting': ('%sの注文が、素材を待って止まっている', 'The order for %s is stuck waiting for materials'),
    'gui.singulo.work_console.notify.missing': ('%sの注文が、素材を待って止まっている（%sがあと%s個足りない）', 'The order for %s is stuck waiting for materials (%s: %s more needed)'),
    'gui.singulo.work_console.notify.missing.more': ('%sの注文が、素材を待って止まっている（%sがあと%s個足りない。ほか%s種）', 'The order for %s is stuck waiting for materials (%s: %s more needed, and %s other kinds)'),
    'gui.singulo.work_console.stock': ('在庫: %s', 'In stock: %s'),
    'gui.singulo.work_console.craftable': ('作れる', 'Craftable'),
    'gui.singulo.android.pack.builtin': ('内蔵', 'Built-in'),
    'gui.singulo.work_console.search.hint': ('検索', 'Search'),
    'gui.singulo.work_console.no_match': ('検索に合う物がない', 'Nothing matches the search'),
    'gui.singulo.work_console.search.help': ('名前で絞り込む。@ で始めると mod で絞り込む（例: @singulo）。空白で区切ると、すべてに合う物だけ。右クリックで消す',
                                             'Filter by name. Start with @ to filter by mod (e.g. @singulo). Separate words with spaces to match all of them. Right-click to clear'),
    'gui.singulo.work_console.sort': ('並び: %s', 'Sort: %s'),
    'gui.singulo.work_console.sort.default': ('指令台の順', 'Default'),
    'gui.singulo.work_console.sort.name': ('名前', 'Name'),
    'gui.singulo.work_console.sort.count': ('在庫', 'Stock'),
    'gui.singulo.work_console.sort.mod': ('mod', 'Mod'),
    'gui.singulo.work_console.sort.craftable': ('作れる物', 'Craftable'),
    'gui.singulo.work_console.sort.help': ('クリックで並びを変える（シフト+クリックで逆回り）', 'Click to change the order (Shift-click to go back)'),
    'gui.singulo.work_console.sort.reverse': ('並びを逆にする', 'Reverse the order'),
    'gui.singulo.work_console.jei.on': ('JEI の検索欄と連携する: オン', 'Linked with the JEI search bar: On'),
    'gui.singulo.work_console.jei.off': ('JEI の検索欄と連携する: オフ', 'Linked with the JEI search bar: Off'),
    'gui.singulo.work_console.cancel': ('取り消す', 'Cancel'),
    'gui.singulo.work_console.count.hint': ('シフトで10ずつ、コントロールで64ずつ', 'Shift: 10 at a time, Ctrl: 64'),
    'gui.singulo.work_console.choose_android': ('選べるアンドロイドがいない', 'No android can take this order'),
    'gui.singulo.work_console.needs_miner': ('この注文には採掘の工程があるので、採掘アップグレードを積んだアンドロイドだけ選べる', 'This order includes mining, so only androids with a Mining Upgrade can be chosen'),
    'gui.singulo.work_console.auto_android': ('選んでいないので、選べる %s 体のうち、作業に必要な数だけで始める', 'None chosen: only as many of the %s available androids as the work needs will start'),
    'gui.singulo.work_console.cannot_start': ('この注文はできない', 'This order cannot be started'),
    'gui.singulo.work_console.tab.order': ('注文', 'Order'),
    'gui.singulo.work_console.tab.running': ('進行中', 'Running'),
    'gui.singulo.work_console.tab.links': ('つながり', 'Links'),
    'gui.singulo.work_console.tab.standing': ('登録', 'Saved'),
    'gui.singulo.work_console.register': ('登録', 'Save'),
    'gui.singulo.work_console.trigger.now': ('今すぐ注文', 'Order now'),
    'gui.singulo.work_console.trigger.keep': ('在庫を保つ', 'Keep in stock'),
    'gui.singulo.work_console.trigger.pulse': ('信号で1回', 'Once per signal'),
    'gui.singulo.work_console.trigger.keep_powered': ('信号の間 在庫を保つ', 'Keep stock while powered'),
    'gui.singulo.work_console.trigger.now.hint': ('クリックで、注文を登録するきっかけを選ぶ（右クリックで逆順）。今すぐ注文: 1回だけ注文する', 'Click to save the order with a trigger instead (right-click to go back). Order now: order just this once'),
    'gui.singulo.work_console.trigger.keep.hint': ('素材置き場の在庫が、この数を下回ったら、この数まで自動で注文する。できた物は素材置き場へ入れる', 'Whenever the stockpile holds fewer than this many, order enough to get back to it. The products go into the stockpile'),
    'gui.singulo.work_console.trigger.pulse.hint': ('指令台にレッドストーン信号が入るたびに、この数を1回注文する。素材が足りなければ、そろうまで待つ', 'Order this many each time the console receives a redstone pulse. If materials are short, it waits until they are in stock'),
    'gui.singulo.work_console.trigger.keep_powered.hint': ('指令台にレッドストーン信号が入っている間だけ「在庫を保つ」', 'Keep in stock, but only while the console is powered by redstone'),
    'gui.singulo.work_console.standing.none': ('登録した注文はない（注文の確認で、きっかけを選んで登録する）', 'No saved orders (choose a trigger when confirming an order)'),
    'gui.singulo.work_console.standing.full': ('登録できるのは %s 件まで', 'Up to %s orders can be saved'),
    'gui.singulo.work_console.standing.added': ('%s の注文を登録した', 'Saved the order for %s'),
    'gui.singulo.work_console.standing.waiting': ('待っている: %s', 'Waiting: %s'),
    'gui.singulo.work_console.standing.state.0': ('待機中', 'Idle'),
    'gui.singulo.work_console.standing.state.1': ('進行中', 'Running'),
    'gui.singulo.work_console.standing.state.2': ('待っている', 'Waiting'),
    'gui.singulo.work_console.standing.state.3': ('信号を待っている', 'Waiting for a signal'),
    'gui.singulo.work_console.standing.stock': ('（在庫 %s）', ' (in stock: %s)'),
    'gui.singulo.work_console.signal.on': ('レッドストーン信号: 入っている', 'Redstone signal: on'),
    'gui.singulo.work_console.signal.off': ('レッドストーン信号: 入っていない', 'Redstone signal: off'),
    'gui.singulo.work_console.worker.away': ('（見当たらない）', '(not found)'),
    'gui.singulo.work_console.detail.doing': ('（%s 作業中）', ' (%s in progress)'),
    'gui.singulo.work_console.error.wrong_face': ('%s のレシピの装置が、入れる面（%3$s）から %2$s を受け付けない（レシピカードの「入」の面を確かめて）', 'The machine for %s does not take %s through its %s face (check the "In" face on the recipe card)'),
    'gui.singulo.work_console.cancelled.wrong_face': ('%s の注文を取り消した（装置が、入れる面（%s）から素材を受け付けない）', 'Cancelled the order for %s (the machine does not take materials through its %s face)'),
    'gui.singulo.work_console.fuel_ordered': ('燃料（%s）が素材置き場にないので、%s 個作る注文を出した', 'Out of fuel (%s), so an order to make %s was placed'),
    'gui.singulo.work_console.detail.machine': ('（%s 装置で加工中）', ' (%s in a machine)'),
    'gui.singulo.work_console.detail.machine_eta': ('（%s 装置で加工中・あと約 %s 秒）', ' (%s in a machine, about %ss left)'),
    'gui.singulo.work_console.worker.elsewhere': ('ほかの注文の作業中', 'On another order'),
    'gui.singulo.work_console.detail.to_stock': ('素材置き場へ %s / %s', 'To stockpile %s / %s'),
    'gui.singulo.work_console.detail.delivered': ('納品 %s / %s', 'Delivered %s / %s'),
    'gui.singulo.work_console.detail.hint': ('クリックで、工程とアンドロイドの様子を見る', 'Click to see the steps and what each android is doing'),
    'gui.singulo.work_console.activity.none': ('待機', 'Idle'),
    'gui.singulo.work_console.activity.fetch': ('素材を取りに行く', 'Fetching materials'),
    'gui.singulo.work_console.activity.craft': ('作っている', 'Crafting'),
    'gui.singulo.work_console.activity.process': ('装置で加工', 'Processing'),
    'gui.singulo.work_console.activity.mine': ('採掘中', 'Mining'),
    'gui.singulo.work_console.activity.deliver': ('届けに行く', 'Delivering'),
    'gui.singulo.work_console.activity.return': ('片付け', 'Putting away'),
    'gui.singulo.work_console.activity.wait': ('素材を待っている', 'Waiting for materials'),
    'gui.singulo.work_console.activity.charge': ('充電中', 'Recharging'),
    'gui.singulo.work_console.activity.fight': ('戦っている', 'Fighting'),
    'gui.singulo.work_console.activity.farm': ('収穫中', 'Harvesting'),
    'gui.singulo.work_console.activity.ranch': ('動物の世話', 'Tending animals'),
    'gui.singulo.work_console.activity.fell': ('伐採中', 'Felling'),
    'gui.singulo.work_console.activity.use': ('操作', 'Using a block'),
    'gui.singulo.work_console.empty': ('注文できる物がない（資材口とレシピ棚をつなぐ）', 'Nothing to order (link supply ports and recipe racks)'),
    'gui.singulo.work_console.no_orders': ('進行中の注文はない', 'No running orders'),
    'gui.singulo.work_console.status.0': ('作業中（%s体）', 'Working (%s androids)'),
    'gui.singulo.work_console.status.1': ('素材を待っている', 'Waiting for materials'),
    'gui.singulo.work_console.status.2': ('運べるアンドロイドを待っている', 'Waiting for an android that can carry it'),
    'gui.singulo.work_console.status.3': ('置き場の空きを待っている', 'Waiting for storage space'),
    'gui.singulo.work_console.no_links': ('つながっているブロックはない', 'Nothing is linked'),
    'gui.singulo.work_console.link.0': ('資材口', 'Supply port'),
    'gui.singulo.work_console.link.1': ('納品口', 'Delivery port'),
    'gui.singulo.work_console.link.2': ('レシピ棚', 'Recipe rack'),
    'gui.singulo.work_console.link.lost': ('（見つからない）', '(missing)'),
    'gui.singulo.work_console.unlink': ('外す', 'Unlink'),
    'gui.singulo.work_console.link.hint': ('作業位置カードでこの台を記録し、そのカードで右クリックしてつなぐ', 'Record this console on a Work Location Card, then use the card on blocks to link them'),
    'gui.singulo.work_console.materials': ('作る物と素材（必要 / 在庫）', 'To craft, and materials (need / have)'),
    'gui.singulo.work_console.crafted': ('作る ×%s', 'Craft ×%s'),
    'gui.singulo.work_console.crafted.surplus': ('作る ×%s（余り %s）', 'Craft ×%s (%s left over)'),
    'gui.singulo.work_console.crafted.hint': ('アンドロイドが作る物。余りは素材置き場へ戻る', 'Crafted by the androids. Leftovers go back to the stockpile'),
    'gui.singulo.work_console.material.hint': ('素材置き場から使う物（必要 / 在庫）', 'Taken from the stockpile (need / have)'),
    'gui.singulo.work_console.androids': ('アンドロイド（運べる数）', 'Androids (slots)'),
    'gui.singulo.work_console.no_androids': ('近くに使えるアンドロイドがいない', 'No android available nearby'),
    'gui.singulo.work_console.busy': ('%s（ほかの指令台の作業中）', '%s (busy at another console)'),
    'gui.singulo.work_console.planning': ('計算中…', 'Calculating...'),
    'gui.singulo.work_console.plan': ('工程 %s ・ 作る回数 %s', '%s steps, %s crafts'),
    'gui.singulo.work_console.start': ('作業開始', 'Start'),
    'gui.singulo.work_console.back': ('戻る', 'Back'),
    'gui.singulo.recipe_encoder.encode': ('記録', 'Save'),
    'gui.singulo.recipe_encoder.encode.hint': ('記録したカードの欄のカードに、この並びを記録する（空ならストックから1枚取る）', 'Record this recipe onto the card in the recorded slot (takes one from the stock if it is empty)'),
    'gui.singulo.recipe_encoder.clear': ('見本を消す', 'Clear the pattern'),
    'gui.singulo.recipe_encoder.clear.short': ('見本消去', 'Clear grid'),
    'gui.singulo.recipe_encoder.clear_card': ('レシピ消去', 'Erase'),
    'gui.singulo.recipe_encoder.clear_card.hint': ('記録したカードのレシピを消す', 'Erase the recipe on the recorded card'),
    'gui.singulo.recipe_encoder.stock': ('ストック', 'Stock'),
    'gui.singulo.recipe_encoder.stock.hint': ('未記録のレシピカードを1スタック入れておくと、記録したカードの欄が空のとき、ここから1枚取って記録する', 'Keep a stack of blank recipe cards here. When the recorded-card slot is empty, Save takes one from here'),
    'gui.singulo.recipe_encoder.grid.hint': ('持っている物でクリックすると、その見本を置く（物は減らない）', 'Click with an item to place a copy as a pattern (it is not used up)'),
    'gui.singulo.recipe_encoder.card': ('記録済み', 'Recorded'),
    'gui.singulo.android.page.status': ('様子', 'Status'),
    'gui.singulo.android.battery': ('バッテリー', 'Battery'),
    'gui.singulo.android.storage': ('収納', 'Storage'),
    'gui.singulo.android.tank': ('タンク%s: %s 約 %s / %s mB', 'Tank %s: %s, about %s / %s mB'),
    'gui.singulo.android.tank.empty': ('タンク%s: 空', 'Tank %s: empty'),
    'gui.singulo.android.tank.hint': ('バケツなどの入れ物でクリックすると、入れる・汲む', 'Click with a bucket or other container to fill or draw'),
    'gui.singulo.android.tank.locked': ('作業中は出し入れできない', 'Cannot be used while working'),
    'gui.singulo.android.tank.clear': ('シフト＋クリックで、中身を捨てる', 'Shift-click to dump the contents'),
    'tooltip.singulo.fluid_sample': ('%s mB', '%s mB'),
    'tooltip.singulo.chemical_sample': ('%s mB', '%s mB'),
    'gui.singulo.work_console.needs_chem_tank': ('この注文には化学物質（Mekanism のガスなど）を運ぶ工程があるので、ガスタンクアップグレードかガス・液体アップグレードを積んだアンドロイドだけ選べる', 'This order moves chemicals (Mekanism gases and the like), so only androids with a Gas Tank Upgrade or a Gas & Liquid Upgrade can be chosen'),
    'gui.singulo.android.chem': ('ガスタンク%s: %s %s / %s mB', 'Gas tank %s: %s %s / %s mB'),
    'gui.singulo.android.chem.empty': ('ガスタンク%s: 空', 'Gas tank %s: empty'),
    'gui.singulo.android.chem.hint': ('ケミカルタンク（Mekanism）などでクリックすると、入れる・汲む', 'Click with a chemical tank (Mekanism) or similar to fill or draw'),
    'tooltip.singulo.needs_mekanism': ('Mekanism が入っているときだけ使える', 'Only works when Mekanism is installed'),
    'gui.singulo.android.tools': ('道具', 'Tools'),
    'gui.singulo.android.pack': ('バックパックの中', 'Backpack'),
    'gui.singulo.android.carry': ('持ち物', 'Carry'),
    'gui.singulo.android.pack.none': ('バックパックなし', 'No backpack'),
    # 止まっている理由（状態のページの吹き出しに、赤い字で出す。測量メガネを掛けると、頭の上に立ち入り禁止の印）
    'gui.singulo.android.stall.no_power': ('ステーションの電力が足りず、充電できない', "The station is out of power; can't charge"),
    'gui.singulo.android.stall.unreachable': ('行き先にたどり着けない', "Can't reach the destination"),
    'gui.singulo.android.stall.missing': ('素材置き場に素材が足りない', 'Not enough materials in the stockpile'),
    'gui.singulo.android.stall.protected': ('守られたブロックで、壊せない', "A protected block; can't break it"),
    'gui.singulo.android.stall.no_target': ('作業の範囲に、今できる対象がない', 'Nothing to work on in the area right now'),
    'gui.singulo.android.stall.full': ('置き場がいっぱいで、物を置けない', "Storage is full; can't put items away"),
    'gui.singulo.android.work.on': ('作業：オン', 'Work: on'),
    'gui.singulo.android.work.off': ('作業：オフ', 'Work: off'),
    'gui.singulo.generator_slots.turbine.in': ('冷媒カートリッジを入れると、液体窒素をタンクに空けて、空のカートリッジを下に出す',
                                               'Insert coolant cartridges: the liquid nitrogen goes into the tank and the empty cartridges come out below'),
    'gui.singulo.generator_slots.fusion.in': ('ミュオン触媒を入れる。既定設定では新品で10分、使いかけは残寿命の半分だけ動き、失活した触媒を下に出す',
                                              'Insert muon catalysts. At default settings, a fresh one lasts 10 minutes; a used one burns for half its '
                                              'remaining lifetime. Spent catalysts come out below.'),
    'gui.singulo.generator_slots.no_heat': ('高温源がない（溶岩・マグマブロックなどを隣に）', 'No heat source (put lava or a magma block next to it)'),
    'gui.singulo.generator_slots.efficiency': ('取り戻せる電力: %s%%', 'Recovers: %s%%'),
    'gui.singulo.generator_slots.nitrogen': ('液体窒素 %s / %s mB', 'Liquid nitrogen %s / %s mB'),
    'gui.singulo.generator_slots.no_catalyst': ('触媒がない', 'No catalyst'),
    'gui.singulo.generator_slots.catalyst': ('触媒の残り %s%%', 'Catalyst left: %s%%'),
    'gui.singulo.generator_slots.water': ('水 %s / %s mB', 'Water %s / %s mB'),
    'gui.singulo.generator_slots.standby': ('蓄電がいっぱい: 待機中', 'Storage full: standing by'),
    'gui.singulo.android.work.hint': ('作業（自動化）に割り当ててよいか。オフにすると、作業の候補に出ない。', 'Whether this android may be given work (automation). When off, it is never offered for work.'),
    'gui.singulo.android.sort.hint': ('持ち物とバックパックの中を並べ替える（同じ物はまとめる）', 'Sort the carried items and backpack contents (stacks the same items together)'),
    'gui.singulo.android.stash.hint': ('持ち物とバックパックの中を、近くの箱（チェスト・樽・シュルカーボックス・資材口）へ預けに行く', 'Go and put the carried items and backpack contents in a nearby box (chest, barrel, shulker box or supply port)'),
    'gui.singulo.android.stash.working': ('作業中はできない', 'Not while working'),
    'gui.singulo.android.stash.empty': ('預ける物がない', 'Nothing to put away'),
    'gui.singulo.android.stash.none': ('近くに預けられる箱がない（12ブロック以内）', 'No box nearby to put things in (within 12 blocks)'),
    'gui.singulo.android.stash.going': ('近くの箱へ預けに行く', 'Going to put things in a nearby box'),
    'gui.singulo.android.hp': ('体力 %s / %s', 'HP %s / %s'),
    'gui.singulo.android.charge': ('%s%%', '%s%%'),
    'gui.singulo.android.mode': ('モード：%s', 'Mode: %s'),
    'gui.singulo.android.follow.only': ('追従のみ', 'Follow only'),
    'gui.singulo.android.follow.escort': ('追従・護衛', 'Follow & escort'),
    'gui.singulo.android.follow.hint': ('クリックで「追従のみ」（戦わない）と「追従・護衛」（そばの敵と戦う）を切り替える。ステーションの設定も変わる。',
                                        'Click to switch between "Follow only" (never fights) and "Follow & escort" (fights nearby enemies). Also changes the station setting.'),
    'gui.singulo.android.armor': ('防御', 'Armor'),
    'gui.singulo.android.repair': ('修理', 'Repair'),
    'gui.singulo.android.plate': ('装甲', 'Plate'),
    'message.singulo.android_charger.charged': ('無線充電器に %1$s FE 充電した（%2$s / %3$s FE）', 'Charged %1$s FE into the wireless charger (%2$s / %3$s FE)'),
    'tooltip.singulo.android_charger.charges': ('アンドロイドの満充電 %s 回分', 'Full android charges: %s'),
    'gui.singulo.android_station.no_design': ('設計メモリなし', 'No design memory'),
    'gui.singulo.android_station.status.0': ('小型クリスタルメモリを入れてください', 'Insert a compact crystal memory'),
    'gui.singulo.android_station.status.1': ('アンドロイドキットがありません', 'No android kit'),
    'gui.singulo.android_station.status.2': ('電力が足りません（印刷に 40000 FE）', 'Not enough power (40000 FE to print)'),
    'gui.singulo.android_station.status.3': ('印刷中', 'Printing'),
    'gui.singulo.android_station.status.4': ('稼働中', 'Active'),
    'gui.singulo.android_station.status.5': ('ステーションへ戻っています', 'Returning to the station'),
    'gui.singulo.android_station.status.6': ('充電中', 'Charging'),
    'gui.singulo.android_station.status.8': ('ステーションの上がふさがっています', 'Something is blocking the top'),
    'gui.singulo.android_station.status.9': ('遠くにいます', 'Far away'),
    'gui.singulo.android_station.status.10': ('充電切れ', 'Out of charge'),
    'gui.singulo.android_station.status.waiting': ('%s 秒後に印刷し直す', 'Reprinting in %s s'),
    'gui.singulo.android_station.hp': ('体力', 'HP'),
    'gui.singulo.android_station.charge': ('充電', 'Charge'),
    'gui.singulo.android_station.memory': ('設計', 'Design'),
    'gui.singulo.android_station.kit': ('キット・印刷', 'Kit · Print'),
    'gui.singulo.android_station.mode.0': ('迎撃', 'Guard'),
    'gui.singulo.android_station.mode.1': ('徘徊', 'Wander'),
    'gui.singulo.android_station.mode.2': ('追従', 'Follow'),
    'gui.singulo.android_station.mode.3': ('巡回', 'Patrol'),
    'gui.singulo.android_station.mode.3.hint': ('下の巡回路（作業位置カード）の場所を順に回り、近くの敵と戦う。戦いになったら手の空いた仲間を呼び、敵の群れやボスを見つけたら、ステーションからレッドストーン信号を出して持ち主に知らせる。', 'Walks the route below (Work Location Cards) in order and fights enemies nearby. Calls idle allies when a fight starts, and when it spots a group of enemies or a boss, the station gives a redstone signal and the owner is told.'),
    'gui.singulo.android_station.route.hint': ('巡回路: 作業位置カードを置くと、その場所を左から順に回る（8つまで）。カードの場所の上に立って見回す。', 'Patrol route: place Work Location Cards and the androids visit those places from left to right (up to 8), standing on top of each one to look around.'),
    'gui.singulo.android_station.rules': ('経路', 'Routes'),
    'gui.singulo.android_station.rules.back': ('様子', 'Status'),
    'gui.singulo.android_station.rules.hint': ('巡回の移動の決まり（必須経由地点・進入禁止ゾーン）のページに切り替える', 'Switch to the page of route rules for patrols (required waypoints and no-entry zones)'),
    'gui.singulo.work_console.tab.routes': ('経路', 'Routes'),
    'gui.singulo.route.via': ('経由地点', 'Waypoint'),
    'gui.singulo.route.scope': ('対象範囲', 'Area'),
    'gui.singulo.route.zone': ('進入禁止ゾーン', 'No-entry zones'),
    'gui.singulo.route.via.hint': ('経由地点（作業位置カード）: 対象範囲の中と外を行き来するとき、この場所の上を通る', 'Waypoint (Work Location Card): androids moving into or out of the area pass over this place'),
    'gui.singulo.route.scope.hint': ('対象範囲（範囲カード）: この範囲の中と外を行き来する移動だけ、経由地点を通る', 'Area (Area Card): only moves into or out of this area go through the waypoint'),
    'gui.singulo.route.zone.hint': ('進入禁止ゾーン（範囲カード、2つまで）: 経由地点を通る移動のあいだ、ここに入らない（出発した所・行き先を含むゾーンは除く）', 'No-entry zone (Area Card, up to 2): androids stay out of it while moving through the waypoint (unless they start or end inside it)'),
    'gui.singulo.route.hint.0': ('対象範囲の中と外を行き来するとき、経由地点を通り、', 'Moving into or out of the area, androids pass the waypoint'),
    'gui.singulo.route.hint.1': ('そのあいだ進入禁止ゾーンを避ける（4行まで）', 'and stay out of its no-entry zones on the way (up to 4 rows)'),
    'gui.singulo.android_station.patrol.order.loop': ('周回', 'Loop'),
    'gui.singulo.android_station.patrol.order.back_and_forth': ('往復', 'Back & forth'),
    'gui.singulo.android_station.patrol.order.hint': ('最後の地点まで行ったら、最初の地点へ戻る（周回）か、来た道を戻る（往復）か（クリックで切り替え）', 'After the last point, go back to the first one (Loop) or come back the same way (Back & forth). Click to switch'),
    'gui.singulo.android_station.patrol.night.on': ('夜だけ', 'Nights only'),
    'gui.singulo.android_station.patrol.night.off': ('昼も夜も', 'Day & night'),
    'gui.singulo.android_station.patrol.night.hint': ('夜だけ巡回する（昼はステーションのそばで待つ）か、いつも巡回するか（クリックで切り替え）', 'Patrol only at night (waiting by the station in the day) or all the time. Click to switch'),
    'gui.singulo.android_station.patrol.dwell': ('待つ %s秒', 'Pause %ss'),
    'gui.singulo.android_station.patrol.dwell.hint': ('各地点で立ち止まって見回す時間（クリックで切り替え）', 'How long to stop and look around at each point. Click to change'),
    'gui.singulo.android_station.mode.0.hint': ('ステーションのそばで待ち、近づいた敵だけを迎え撃つ。', 'Waits by the station and fights enemies that come close.'),
    'gui.singulo.android_station.mode.1.hint': ('ステーションのまわり（16ブロック）を歩き回り、見つけた敵を倒す。', 'Roams within 16 blocks of the station and fights the enemies it finds.'),
    'gui.singulo.android_station.mode.2.hint': ('持ち主についていく（下のボタンで、そばの敵と戦うか選ぶ）。無線充電器を持っていれば、ついて歩きながら充電する。', 'Follows its owner (the buttons below choose whether it fights nearby enemies). Recharges while walking with you if you carry a wireless charger.'),
    'gui.singulo.android_station.retreat': ('戻る体力 %s%%', 'Return at %s%%'),
    'gui.singulo.android_station.retreat.hint': ('体力がこの割合を下回ると、ステーションへ戻って修理と充電をする。', 'Below this share of health it returns to the station to repair and recharge.'),
    'gui.singulo.android_station.recall': ('呼び戻す', 'Recall'),
    'gui.singulo.android_station.recall.hint': ('クリック: 見ているアンドロイドを呼び戻す。Shift＋クリック: 近く（%sブロック以内）の、操作できるステーションのアンドロイドを、みな呼び戻す。',
                                                'Click: recall the android shown. Shift-click: recall every android from the stations you can use within %s blocks.'),
    'gui.singulo.android_station.recall_all.done': ('%s体を呼び戻した', 'Recalled %s androids'),
    'gui.singulo.android_station.retreat.now': ('今の体力 %s%%・充電 %s%%', 'Now: health %s%%, charge %s%%'),
    'gui.singulo.android_station.follow_only': ('ついていくだけ', 'Just follow'),
    'gui.singulo.android_station.follow_only.hint': ('追従中は、ついていくだけで戦わない（打たれても仕返ししない）。', 'While following, it only tags along and never fights (not even when hit).'),
    'gui.singulo.android_station.escort': ('護衛する', 'Escort'),
    'gui.singulo.android_station.escort.hint': ('追従中も、そばの敵を倒す。', 'While following, it also fights enemies near you.'),
    'gui.singulo.android_station.permit': ('許可証', 'Permit'),
    'gui.singulo.android_station.permit.none': ('許可証なし：だれでも操作できる', 'No permit: anyone can use it'),
    'gui.singulo.android_station.permit.members': ('持ち主と登録した %s人だけが操作できる', 'Only the owner and %s registered can use it'),
    'gui.singulo.android_station.permit.others': ('ほかの人は敵とみなして攻撃する', 'Anyone else is treated as an enemy and attacked'),
    'gui.singulo.android_station.permit.hint': ('シールド許可証を入れると、持ち主と登録した人だけがステーションとアンドロイドの画面を開ける。登録していない人は敵とみなして攻撃する。追従する人も、登録した人から選べる。',
                                                'With a shield permit inside, only the owner and registered players can open the station and android screens, and the android treats anyone else as an enemy and attacks them. You can also choose which registered player it follows.'),
    'gui.singulo.android_station.leader': ('追従する人：%s', 'Follows: %s'),
    'gui.singulo.android_station.leader.owner': ('持ち主', 'Owner'),
    'gui.singulo.android_station.leader.hint': ('クリックで、追従する人を切り替える（その人がいないときは持ち主に、持ち主もいなければ近くの登録した人に追従する）。',
                                                'Click to change whom it follows (when that player is away it follows the owner, or else the nearest registered player).'),
    'gui.singulo.android_station.locked': ('このステーションの許可証に登録されていない', 'You are not on this station\'s permit'),
    'entity.singulo.blade_android': ('光刃型アンドロイド「ルミナ」', 'Blade Android "Lumina"'),
    'entity.singulo.whip_android': ('鞭型アンドロイド「ヴェルナ」', 'Whip Android "Verna"'),
    'entity.singulo.saber_android': ('光剣型アンドロイド「ソレイユ」', 'Saber Android "Soleil"'),
    'entity.singulo.axe_android': ('斧型アンドロイド「アルマ」', 'Axe Android "Alma"'),
    'entity.singulo.bit_android': ('ビット型アンドロイド「ノクス」', 'Bit Android "Nox"'),
    'entity.singulo.gunner_android': ('射撃型アンドロイド「シエル」', 'Gunner Android "Ciel"'),
    'entity.singulo.android_crescent': ('斬撃波', 'Crescent Slash'),
    'entity.singulo.android_laser': ('光柱', 'Light Pillar'),
    'entity.singulo.horizon_warden': ('ホライズン・ウォーデン', 'Horizon Warden'),
    'entity.singulo.horizon_warden.awakened': ('覚醒したホライズン・ウォーデン', 'Awakened Horizon Warden'),
    'entity.singulo.warden_singularity': ('ウォーデンの特異点', 'Warden Singularity'),
    'entity.singulo.horizon_bolt': ('光弾', 'Horizon Bolt'),
    'item.singulo.horizon_warden_spawn_egg': ('ホライズン・ウォーデンのスポーンエッグ', 'Horizon Warden Spawn Egg'),
    'entity.singulo.echo_sentinel': ('残響の番人', 'Echo Sentinel'),
    'entity.singulo.gravity_remnant': ('重力の澱', 'Gravity Remnant'),
    'entity.singulo.gravity_debris': ('瓦礫', 'Gravity Debris'),
    'entity.singulo.infalling_block': ('崩れ落ちるブロック', 'Infalling Block'),
    'entity.singulo.foundry_overseer': ('統括AI「ファウンドリ」', 'Foundry Overseer'),
    'entity.singulo.foundry_overseer.awakened': ('覚醒したファウンドリ', 'Awakened Foundry Overseer'),
    'item.singulo.foundry_overseer_spawn_egg': ('ファウンドリのスポーンエッグ', 'Foundry Overseer Spawn Egg'),
    'gui.singulo.guardian.awakened.foundry_overseer': ('製造ラインが動き出した。製造統括AI「ファウンドリ」が目を覚ました',
                                                       'The production lines start up. The Foundry Overseer wakes'),
    'gui.singulo.guardian.defeated.foundry_overseer': ('ファウンドリの単眼が消えた。製造ラインが止まり、保管庫の力場が消えた',
                                                       'The Foundry\'s eye goes dark. The lines halt and the vault field dissipates'),
    'gui.singulo.guardian.reset.foundry_overseer': ('挑戦者がいなくなり、ファウンドリは製造ラインを止めて眠りについた',
                                                    'No challenger remains. The Foundry halts its lines and powers down'),
    'gui.singulo.guardian.awakened_rematch.foundry_overseer': ('覚醒核が共鳴する。覚醒したファウンドリが、全ラインを再起動した',
                                                               'The awakening core resonates. The Awakened Foundry restarts every line'),
    'item.singulo.echo_sentinel_spawn_egg': ('残響の番人のスポーンエッグ', 'Echo Sentinel Spawn Egg'),
    'item.singulo.gravity_remnant_spawn_egg': ('重力の澱のスポーンエッグ', 'Gravity Remnant Spawn Egg'),
    'entity.singulo.ergosphere_leviathan': ('エルゴ球の大蛇', 'Ergosphere Leviathan'),
    'entity.singulo.ergosphere_leviathan.awakened': ('覚醒したエルゴ球の大蛇', 'Awakened Ergosphere Leviathan'),
    # 出現の演出で、大蛇に呑まれる小さな蛇のボスバー（呑まれると大蛇の名前に替わる）
    'entity.singulo.ergosphere_leviathan.hatchling': ('エルゴ球の蛇', 'Ergosphere Serpent'),
    'item.singulo.ergosphere_leviathan_spawn_egg': ('エルゴ球の大蛇のスポーンエッグ', 'Ergosphere Leviathan Spawn Egg'),
    'entity.singulo.last_archivist': ('最後の記録者', 'The Last Archivist'),
    'item.singulo.last_archivist_spawn_egg': ('最後の記録者のスポーンエッグ', 'Last Archivist Spawn Egg'),
    'gui.singulo.guardian.awakened.ergosphere_leviathan': ('止まった炉心に渦が開いた。何か小さなものが這い出してくる……',
                                                           'A vortex opens in the dead core. Something small crawls out...'),
    'entity.singulo.ergosphere_leviathan.wyrm': ('エルゴ球の龍', 'Ergosphere Wyrm'),
    'entity.singulo.ergosphere_leviathan.wyrm.awakened': ('覚醒したエルゴ球の龍', 'Awakened Ergosphere Wyrm'),
    'gui.singulo.leviathan.reignite': ('炉心の渦が呼び覚まされていく……', 'The dead core is waking...'),
    'gui.singulo.leviathan.reignite_hint': ('封じ込め柱の陰に隠れろ！', 'Hide behind a containment pillar!'),
    'gui.singulo.leviathan.quake': ('大蛇が高く昇り、身を丸めた……　衝撃の輪が来たら跳べ！', 'The Leviathan rises and curls up... Jump over the shockwaves!'),
    'gui.singulo.archivist.halo': ('光輪がほどけ、輪になって広がる……　低い輪は跳び、高い輪はしゃがんでくぐれ！',
                                   'The halo unravels into spreading rings... Jump the low ones, crouch under the high ones!'),
    'gui.singulo.archivist.wings': ('最後の記録者が六枚の翼を広げた……', 'The Last Archivist spreads its six wings...'),
    'gui.singulo.archivist.wings_hint': ('翼の影に入れ！', 'Stand in the shadow of a wing!'),
    'gui.singulo.leviathan.rift': ('床に光の線が走る……　線の上から離れろ！', 'Lines of light cross the floor... Get off them!'),
    'gui.singulo.leviathan.tide': ('炉心の渦が開き、すべてを引き込んでいく……　流れに逆らって外へ走れ！', 'The core vortex opens and drags everything in... Run against the pull!'),
    'gui.singulo.leviathan.molt': ('大蛇が身を震わせた。炉殻の鱗が弾け飛び、光の骨格があらわになる',
                                   'The Leviathan shudders. Its reactor-plate scales burst away, baring a skeleton of light'),
    'gui.singulo.leviathan.submerge': ('大蛇が炉心の渦へ潜っていく。渦は広がり、光を呑み込んでいく……',
                                       'The Leviathan dives into the core vortex. The vortex widens and swallows the light...'),
    'gui.singulo.leviathan.ascend': ('渦が裂け、黒い龍が昇ってくる。翼のように広がるのは、降着円盤の光だ',
                                     'The vortex tears open and a black wyrm rises, its wings a spread of accretion-disk light'),
    'gui.singulo.guardian.defeated.ergosphere_leviathan': ('エルゴ球の大蛇は渦の底へ沈んでいった。保管庫の力場が消えた',
                                                           'The Ergosphere Leviathan sinks into the vortex. The vault field dissipates'),
    'gui.singulo.guardian.reset.ergosphere_leviathan': ('挑戦者がいなくなり、エルゴ球の大蛇は炉心の渦へ帰っていった',
                                                        'No challenger remains. The Ergosphere Leviathan returns to the core'),
    'gui.singulo.guardian.awakened_rematch.ergosphere_leviathan': ('覚醒核が共鳴する。覚醒したエルゴ球の大蛇が、渦を裂いて現れた',
                                                                   'The core resonates. The Awakened Ergosphere Leviathan tears through the vortex'),
    'gui.singulo.archivist.emerge_1': ('……誰かが、記録を読んだのか。', '...Someone has read the records.'),
    'gui.singulo.archivist.emerge_2': ('ならば、最後の頁を見せよう。', 'Then let me show you the last page.'),
    'gui.singulo.archivist.phase_2': ('覚えているか。記録はすべて、ここにある。', 'Do you remember? Every record is here.'),
    'gui.singulo.archivist.phase_3': ('光も記録も、いつかはこの暗がりに沈む。', 'Light and records alike sink into this dark in the end.'),
    'block.singulo.arena_hologram': ('ホログラムの床', 'Hologram Floor'),
    'item.singulo.afterglow_wings.tip_1': ('胴に着ける光の翼。エリトラのように滑空でき、壊れない', 'Wings of light worn on the chest. Glide like an elytra; they never break'),
    'item.singulo.afterglow_wings.tip_2': ('滑空中にスニーク: 羽ばたいて前へ加速する', 'Sneak while gliding: beat the wings to surge forward'),
    'item.singulo.afterglow_wings.tip_3': ('着けていると落下ダメージを受けない。守りはダイヤモンドの胸当てと同じ',
                                           'No fall damage while worn. Protects like a diamond chestplate'),
    'gui.singulo.archivist.shield_up': ('力場が部屋を閉ざした。決着がつくまで、誰も外へは出られない。', 'A field seals the chamber. No one leaves until it ends.'),
    'gui.singulo.archivist.shield_down': ('部屋を閉ざしていた力場がほどけた。', 'The field sealing the chamber unravels.'),
    'gui.singulo.archivist.tide': ('重さの記録を、読み上げよう。', 'Let me read you the record of weight.'),
    'gui.singulo.archivist.tide_final': ('もう、どこにも落ちられない。', 'There is nowhere left to fall.'),
    'gui.singulo.archivist.death_1': ('……長い、夢を見ていた。', '...I have been dreaming a long dream.'),
    'gui.singulo.archivist.death_2': ('記録は、読む者がいて初めて記録になる。', 'A record is only a record when someone reads it.'),
    'gui.singulo.archivist.death_3': ('ありがとう。どうか、止めるべき時に止められる人でいて。', 'Thank you. Please be someone who can stop when it is time to.'),
    'gui.singulo.archivist.reset': ('挑戦者がいなくなり、記録者は時間の場へ還っていった。コンソールには終焉の記録が残されている',
                                    'No challenger remains. The Archivist returns to the time field, leaving the Terminus Record on the console'),
    'gui.singulo.guardian.awakened.echo_sentinel': ('記録保管室の残響が形をとった。残響の番人が現れた',
                                                    'The echoes of the archive take shape. The Echo Sentinel appears'),
    'gui.singulo.guardian.defeated.echo_sentinel': ('残響の番人は、ほどけるように消えた。保管庫の力場が消えた',
                                                    'The Echo Sentinel unravels. The vault field dissipates'),
    'gui.singulo.guardian.reset.echo_sentinel': ('挑戦者がいなくなり、残響の番人は記録の中へ還っていった',
                                                 'No challenger remains. The Echo Sentinel returns to the records'),
    'entity.singulo.echo_sentinel.awakened': ('覚醒した残響の番人', 'Awakened Echo Sentinel'),
    'entity.singulo.gravity_remnant.awakened': ('覚醒した重力の澱', 'Awakened Gravity Remnant'),
    'gui.singulo.guardian.awakened_rematch.echo_sentinel': ('覚醒核が共鳴する。覚醒した残響の番人が現れた',
                                                            'The core resonates. The Awakened Echo Sentinel appears'),
    'gui.singulo.guardian.awakened_rematch.gravity_remnant': ('覚醒核が共鳴する。覚醒した重力の澱が這い出してきた',
                                                              'The core resonates. The Awakened Gravity Remnant crawls out'),
    'gui.singulo.guardian.awakened_defeated': ('覚醒した番人を退けた。上質な欠片がこぼれ落ちた',
                                               'The awakened guardian is defeated. Fine fragments spill out'),
    'gui.singulo.boss.party': ('%s ［挑戦者 %s人］', '%s [%s challengers]'),
    'gui.singulo.guardian.active': ('番人はすでに目覚めている', 'The guardian is already awake'),
    'gui.singulo.guardian.no_vault': ('番人に守られた保管庫が近くにない', 'No guarded vault nearby'),
    'gui.singulo.guardian.wrong_core': ('これは、この遺構の番人の覚醒核ではない', "This is not the awakening core for this ruin's guardian"),
    'gui.singulo.guardian.awakened.gravity_remnant': ('封じ込め槽が砕けた。重力の澱が這い出してきた',
                                                      'The containment tank shatters. The Gravity Remnant crawls out'),
    'gui.singulo.guardian.defeated.gravity_remnant': ('重力の澱の核が潰れた。保管庫の力場が消えた',
                                                      'The Gravity Remnant collapses. The vault field dissipates'),
    'gui.singulo.guardian.reset.gravity_remnant': ('挑戦者がいなくなり、重力の澱は槽の底へ沈んでいった',
                                                   'No challenger remains. The Gravity Remnant sinks back into its tank'),
    'death.attack.singulo.tidal': ('%1$s は潮汐力で引き裂かれた', '%1$s was torn apart by tidal forces'),
    'death.attack.singulo.event_horizon': ('%1$s は事象の地平線を越えた', '%1$s crossed the event horizon'),
    'death.attack.singulo.tidal.player': ('%1$s は %2$s の特異点に引き裂かれた', "%1$s was torn apart by %2$s's singularity"),
    'gui.singulo.console.awakened': ('封印が破れ、ホライズン・ウォーデンが目を覚ました', 'The seal breaks. The Horizon Warden awakens'),
    'gui.singulo.console.active': ('守護機はすでに起動している', 'The warden is already active'),
    'gui.singulo.console.open': ('保管庫の封鎖はすでに解かれている（覚醒の記録核を使えば、覚醒した守護機と再戦できる）',
                                 'The vault is not sealed (use an Awakening Record Core to fight the awakened warden)'),
    'gui.singulo.console.awakened_rematch': ('記録核が共鳴する。覚醒したホライズン・ウォーデンが目を覚ました',
                                             'The record core resonates. The Awakened Horizon Warden rises'),
    'gui.singulo.console.awakened_defeated': ('覚醒した守護機を退けた。残骸から上質な部品が転がり出た',
                                              'The awakened warden is defeated. Fine parts spill from the wreck'),
    'gui.singulo.console.no_vault': ('封鎖された保管庫が近くにない', 'No sealed vault nearby'),
    'gui.singulo.console.defeated': ('守護機は沈黙した。保管庫の力場が消えた', 'The warden falls silent. The vault field dissipates'),
    'gui.singulo.console.reset': ('挑戦者がいなくなり、守護機は封印の中へ戻った', 'No challenger remains. The warden returns to its seal'),
    'gui.singulo.cache.sealed': ('力場で封鎖されている。この遺構を守る番人を倒さなければ開かない',
                                 "Sealed by a force field. Defeat this ruin's guardian"),
    'item.singulo.security_drone_spawn_egg': ('警備ドローンのスポーンエッグ', 'Security Drone Spawn Egg'),
    'item.singulo.blade_android_spawn_egg': ('光刃型アンドロイドのスポーンエッグ', 'Blade Android Spawn Egg'),
    'item.singulo.whip_android_spawn_egg': ('鞭型アンドロイドのスポーンエッグ', 'Whip Android Spawn Egg'),
    'item.singulo.saber_android_spawn_egg': ('光剣型アンドロイドのスポーンエッグ', 'Saber Android Spawn Egg'),
    'item.singulo.axe_android_spawn_egg': ('斧型アンドロイドのスポーンエッグ', 'Axe Android Spawn Egg'),
    'item.singulo.bit_android_spawn_egg': ('ビット型アンドロイドのスポーンエッグ', 'Bit Android Spawn Egg'),
    'item.singulo.gunner_android_spawn_egg': ('射撃型アンドロイドのスポーンエッグ', 'Gunner Android Spawn Egg'),
    'gui.singulo.detector.result': ('重力波を検出: %s、%s', 'Gravitational waves detected: %s, %s'),
    'gui.singulo.detector.none': ('重力異常は見つからなかった', 'No gravity anomaly found'),
    'gui.singulo.detector.no_power': ('観測には %s FE が必要', 'An observation needs %s FE'),
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
    'gui.singulo.genesis.formed': ('創世炉の形が整った。原初の種を核に使えば、宇宙の誕生が始まる',
                                   'The Genesis Engine takes shape. Use a Primordial Seed on the core to begin'),
    'gui.singulo.genesis.unformed': ('形ができていない（核のまわりを、枠8個と柱12個で囲むこと）',
                                     'Not formed (surround the core with 8 frames and 12 pylons)'),
    'gui.singulo.genesis.seeded': ('原初の種が核の中へ沈んでいった。種火をともすために電力を注げ', 'The seed sinks in. Pour in power to kindle it'),
    'gui.singulo.genesis.kindled': ('種火がともり、宇宙が膨らみはじめた。電力を絶やさず、暴走の兆しに備えること',
                                    'The seed ignites and a universe begins to swell. Keep the upkeep flowing and watch for surges'),
    'gui.singulo.genesis.surge': ('暴走の兆し！投入を止めろ（核の画面のスイッチか、赤石信号で）',
                                  'Surge warning! Stop the feed (the switch on the core screen, or a redstone signal)'),
    'gui.singulo.genesis.surge_end': ('宇宙のうねりが静まった。投入を再開してよい', 'The surge subsides. You may resume the feed'),
    'gui.singulo.genesis.collapse': ('生まれかけの宇宙が崩れかけ、進行が巻き戻った', 'The proto-universe nearly collapses. Progress is lost'),
    'gui.singulo.genesis.event.leak': ('重力が漏れ出している', 'Gravity leaks out'),
    'gui.singulo.genesis.event.echoes': ('記録の残響が引き寄せられてきた', 'Echoes of the records are drawn in'),
    'gui.singulo.genesis.event.wave': ('引力の波が来る。落下に備えろ', 'A wave of gravity rises. Brace for the fall'),
    'gui.singulo.genesis.birth_1': ('光が、一点へ落ちていく……', 'The light falls into a single point...'),
    'gui.singulo.genesis.birth_2': ('すべての質量はエネルギーになった。だから今度は、', 'All mass became energy. So now,'),
    'gui.singulo.genesis.birth_3': ('エネルギーから、宇宙をひとつ。', 'from energy, a universe.'),
    'gui.singulo.genesis.birth_sky': ('生まれた銀河が、夜空へ昇っていった', 'The newborn galaxy rises into the night sky'),
    'gui.singulo.genesis.complete': ('創世炉は質量を生み出せるようになった。核の画面で、作りたい物を登録しよう',
                                     'The Genesis Engine can now make mass. Choose an item on the core\'s screen'),
    'gui.singulo.genesis.stopped': ('投入を止めた', 'Feed stopped'),
    'gui.singulo.genesis.resumed': ('投入を再開した', 'Feed resumed'),
    'gui.singulo.genesis.not_synthesizable': ('それは創世炉では作れない', 'The Genesis Engine cannot make that'),
    'gui.singulo.genesis.template': ('作る物: %s（1個あたり %s、希少度 %s）', 'Making: %s (%s each, rarity %s)'),
    'gui.singulo.genesis.bar.kindling': ('種火 %s　受電 %s/t', 'Kindling %s  Intake %s/t'),
    'gui.singulo.genesis.bar.stabilizing': ('安定化 %s　不安定度 %s', 'Stabilization %s  Instability %s'),
    'gui.singulo.genesis.bar.surge': ('⚠ 暴走中！投入を止めろ　不安定度 %s', '⚠ Surging: stop the feed!  Instability %s'),
    'gui.singulo.genesis.bar.surge_held': ('暴走を抑え込んでいる　不安定度 %s', 'Holding the surge  Instability %s'),
    'gui.singulo.genesis.bar.synth': ('%s を生成中 %s　完成品 %s個', '%s %s  Ready: %s'),
    'gui.singulo.genesis.bar.paused': ('［投入停止中］ %s', '[Feed stopped] %s'),
    'gui.singulo.genesis.phase.0': ('未形成', 'Not formed'),
    'gui.singulo.genesis.phase.1': ('揺籃', 'Cradle'),
    'gui.singulo.genesis.phase.2': ('種火', 'Kindling'),
    'gui.singulo.genesis.phase.3': ('安定化', 'Stabilizing'),
    'gui.singulo.genesis.phase.4': ('誕生', 'Birth'),
    'gui.singulo.genesis.phase.5': ('完成', 'Complete'),
    'gui.singulo.genesis.hint.0': ('核のまわりを、枠8個と柱12個で囲む', 'Surround the core with 8 frames and 12 pylons'),
    'gui.singulo.genesis.hint.1': ('原初の種を持って、核に使う', 'Use a Primordial Seed on the core'),
    'gui.singulo.genesis.hint.2': ('電力を注ぎ、種火を育てる', 'Pour in power to grow the kindling'),
    'gui.singulo.genesis.hint.3': ('電力を絶やさないこと。暴走の兆しが来たら投入を止める', 'Keep power up; stop the feed on a surge'),
    'gui.singulo.genesis.hint.4': ('宇宙が生まれようとしている', 'A universe is being born'),
    'gui.singulo.genesis.hint.5': ('作りたい物を左の枠に登録すると、電力から作り出す', 'Put an item in the frame to make it'),
    'gui.singulo.genesis.screen.charge': ('種火', 'Kindling'),
    'gui.singulo.genesis.screen.progress': ('安定化', 'Stability'),
    'gui.singulo.genesis.screen.instability': ('不安定度', 'Instability'),
    'gui.singulo.genesis.screen.birth': ('誕生', 'Birth'),
    'gui.singulo.genesis.screen.intake': ('受電 %s/t', 'Input %s/t'),
    'gui.singulo.genesis.screen.intake_cap': ('受電上限 %s/t', 'Max input %s/t'),
    'gui.singulo.genesis.screen.surge': ('暴走の兆し！投入を止めろ', 'Surge! Stop the feed'),
    'gui.singulo.genesis.screen.surge_held': ('暴走を抑えている', 'Holding the surge'),
    'gui.singulo.genesis.screen.feed_on': ('投入中', 'Feeding'),
    'gui.singulo.genesis.screen.feed_off': ('投入停止中', 'Feed stopped'),
    'gui.singulo.genesis.screen.feed_redstone': ('赤石信号で停止中', 'Stopped by redstone'),
    'gui.singulo.genesis.screen.feed_tip': ('クリックで投入を止める／再開する', 'Click to stop or resume the feed'),
    'gui.singulo.genesis.screen.feed_tip_stabilizing': ('止めている間も蓄えから維持の電力を使い、異変は起きる。電力が足りないまま投入を続けると不安定になる',
                                                        'While stopped, upkeep still drains the buffer and events still happen. Feeding without enough power raises instability'),
    'gui.singulo.genesis.screen.no_template': ('作る物が未登録', 'Nothing chosen yet'),
    'gui.singulo.genesis.screen.cost': ('1個あたり %s', '%s each'),
    'gui.singulo.genesis.screen.synth': ('生成中 %s', 'Making %s'),
    'gui.singulo.genesis.screen.output': ('完成品', 'Output'),
    'gui.singulo.genesis.screen.template_tip_1': ('作りたい物を持ってクリックすると登録される（手持ちの物は減らない）', 'Click with an item to choose it (it is not used up)'),
    'gui.singulo.genesis.screen.template_tip_2': ('何も持たずにクリックすると登録を解除。インベントリの物をシフトクリックしても登録できる', 'Click empty-handed to clear; shift-click items to choose'),
    'tooltip.singulo.genesis_core.memory': ('生まれた宇宙の記憶を宿している。置き直しても質量生成を続けられる（溜めた電力と作りかけの進み具合は引き継がない）',
                                            'Holds the memory of the universe it bore. Place it again to resume mass synthesis (stored power and partial progress are not kept)'),
    'ending.singulo.line.1': ('かつて、ある文明は、すべての質量をエネルギーに変えようとした。',
                              'Once, a civilization tried to turn all mass into energy.'),
    'ending.singulo.line.2': ('炉はその願いを叶えた。都市も、山も、海も。残ったのは重力の異常と、記録だけだった。',
                              'The reactor granted that wish: the cities, the mountains, the sea. Only the anomaly and the records remained.'),
    'ending.singulo.line.3': ('あなたはその記録を読み、同じ炉に火を入れた。そして、止めるべき時に止めることを忘れなかった。',
                              'You read those records and lit the same fire. And you never forgot to stop when it was time to stop.'),
    'ending.singulo.line.4': ('そして今、エネルギーは質量に戻り、星になり、銀河になった。',
                              'And now the energy has become mass again, then stars, then a galaxy.'),
    'ending.singulo.line.5': ('夜空を見上げてほしい。あの光は、あなたが生んだ宇宙だ。',
                              'Look up at the night sky. That light is the universe you made.'),
    'ending.singulo.archivist': ('――記録者の声がする。「最後の頁の、その先を。読ませてくれて、ありがとう」',
                                 '— The Archivist\'s voice: "Beyond the last page. Thank you for letting me read it."'),
    'ending.singulo.thanks': ('Thank you for playing', 'Thank you for playing'),
    'ending.singulo.credit': ('Singulo', 'Singulo'),
    'ending.singulo.skip': ('Esc で閉じる', 'Press Esc to close'),
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
    from records import RECORDS, HIDDEN_RECORDS, FINAL_RECORDS, GENESIS_RECORDS
    for rid, _, jt, jb, et, eb in RECORDS + HIDDEN_RECORDS + FINAL_RECORDS + GENESIS_RECORDS:
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
    import blocks_extra
    black_hole_item_models()
    manipulator_staff_model()
    compass_item_model()
    fluid_sample_item_model()
    for name, (iid, _, kind) in MOD.items():
        if kind in ('structure', 'fluid'):
            continue
        if kind in ('gravity', 'nature', 'boss_block', 'genesis', 'lab'):
            continue                                           # 重力パネル・受電器・自然物・ボス部屋の仕掛けは blocks_extra・blocks_extra4・blocks_extra5 が作る
        if kind in ('block', 'part_block', 'part_glass', 'ruin_block', 'ruin_glass', 'deco', 'deco_glass'):
            model = {'parent': 'minecraft:block/cube_all', 'textures': {'all': f'{MODID}:block/{iid}'}}
            if kind == 'part_glass':
                model['render_type'] = 'minecraft:translucent'      # 色つきの半透明ガラス（中が見える）
            elif kind in ('ruin_glass', 'deco_glass'):
                model['render_type'] = 'minecraft:translucent'      # うっすら色のついたガラス（割れた穴は抜ける）
            write_json(ASSETS / 'models' / 'block' / f'{iid}.json', model)
            if iid == 'ruin_android_fabricator':
                # 上は印刷床（光の走査線が動く）、横は格納口のあるパネル
                write_json(ASSETS / 'models' / 'block' / f'{iid}.json', {'parent': 'minecraft:block/cube_bottom_top', 'textures': {
                    'top': f'{MODID}:block/{iid}_top', 'side': f'{MODID}:block/{iid}_side', 'bottom': f'{MODID}:block/{iid}'}})
            if iid in ('android_barrier_glass', 'android_gate_glass'):
                # 無地: 隣とつながるガラス（つながっていない向きの縁だけ描く。tools/androids.py）
                androids.glass_models(iid, write_json, ASSETS)
                continue
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
        elif kind == 'container':
            sealed_container_models(iid)
        elif kind == 'machine':
            if iid in CABLES:
                cable_models(iid)
                continue
            st = stage_of(name)
            import shapes
            skin = CONTROLLER_SKINS.get(iid)
            for state in ('', '_on'):
                if iid == 'wormhole_mouth':
                    # 枠だけの筐体（喉は中に浮かぶ。描くのは WormholeMouthRenderer）
                    els, has_glass = shapes.frame_elements(), False
                else:
                    els, has_glass = shapes.machine_elements(iid, on=state == '_on')
                model = {
                    'parent': 'minecraft:block/block',
                    'textures': {'top': f'{MODID}:block/machine_top_t{st}', 'side': f'{MODID}:block/machine_side_t{st}',
                                 'bottom': f'{MODID}:block/machine_bottom_t{st}', 'glass': f'{MODID}:block/machine_glass',
                                 'inner': f'{MODID}:block/machine_inner',
                                 'front': f'{MODID}:block/{iid}_front{state}', 'particle': f'{MODID}:block/machine_side_t{st}'},
                    'elements': els,
                }
                if iid == 'android_station':
                    # 上面は印刷床（光の走査線が往復する。blocks_extra3.station_top）
                    model['textures']['top'] = f'{MODID}:block/android_station_top'
                if iid.endswith('android_workbench'):
                    # 上面は 3×3 のマスの作業面（blocks_extra3.workbench_top。段階ごとに格子の色が違う）
                    model['textures']['top'] = f'{MODID}:block/{iid}_top'
                if skin:
                    # マルチブロックのコントローラは、部品と同じ外装で包む（見た目をそろえる）
                    for key in ('top', 'side', 'bottom', 'particle'):
                        model['textures'][key] = f'{MODID}:block/{skin}'
                if iid == 'wormhole_port':
                    # どの面にもつなげるので、どの面も口の絵。働いている間は全体を明るく光らせる
                    for key in ('top', 'side', 'bottom'):
                        model['textures'][key] = model['textures']['front']
                    if state == '_on':
                        for e in els:
                            e['neoforge_data'] = {'block_light': 15, 'sky_light': 15}
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
            if iid == 'wormhole_mouth':
                # 持ったときは、枠の中に黒い喉の芯を見せる
                item = json.loads((ASSETS / 'models' / 'block' / f'{iid}.json').read_text(encoding='utf-8'))
                item['textures']['core'] = f'{MODID}:block/bh_dark'
                item['elements'] = item['elements'] + [{'from': [6, 6, 6], 'to': [10, 10, 10], 'faces': {
                    d: {'texture': '#core'} for d in ('north', 'south', 'east', 'west', 'up', 'down')}}]
                write_json(ASSETS / 'models' / 'item' / f'{iid}.json', item)
            else:
                write_json(ASSETS / 'models' / 'item' / f'{iid}.json', {'parent': f'{MODID}:block/{iid}'})
        else:
            if iid in BLACK_HOLE_ITEMS or iid in ('explorer_compass', 'meteor_compass', 'graviton_manipulator'):
                continue                                       # 立体のモデル・針のモデルは別に作る
            model = {'parent': 'minecraft:item/handheld' if iid == 'relocation_wrench' else 'minecraft:item/generated',
                     'textures': {'layer0': f'{MODID}:item/{iid}'}}
            if iid == 'relocation_wrench':
                # ケーブル撤去モード（singulo:mode = 1）は表示灯が橙の絵
                model['overrides'] = [{'predicate': {f'{MODID}:mode': 1}, 'model': f'{MODID}:item/relocation_wrench_cable'}]
                write_json(ASSETS / 'models' / 'item' / 'relocation_wrench_cable.json',
                           {'parent': 'minecraft:item/handheld', 'textures': {'layer0': f'{MODID}:item/relocation_wrench_cable'}})
            write_json(ASSETS / 'models' / 'item' / f'{iid}.json', model)
    blocks_extra.models(write_json, ASSETS)
    import blocks_extra3
    blocks_extra3.models(write_json, ASSETS)
    import blocks_extra4
    blocks_extra4.models(write_json, ASSETS)
    import blocks_extra5
    blocks_extra5.models(write_json, ASSETS)
    import blocks_extra6
    blocks_extra6.models(write_json, ASSETS)
    import lab_blocks
    lab_blocks.models(write_json, ASSETS)
    import genesis_assets
    genesis_assets.models(write_json, ASSETS)


def boost_overlay_model():
    face = {'texture': '#flow', 'uv': [0, 0, 16, 16]}
    write_json(ASSETS / 'models' / 'block' / 'boost_overlay.json', {
        'parent': 'minecraft:block/block', 'ambientocclusion': False, 'render_type': 'minecraft:translucent',
        'textures': {'flow': f'{MODID}:block/boost_overlay', 'particle': f'{MODID}:block/boost_overlay'},
        'elements': [{'from': [-0.02, -0.02, -0.02], 'to': [16.02, 16.02, 16.02], 'shade': False,
                      'neoforge_data': {'block_light': 15, 'sky_light': 15},
                      'faces': {d: dict(face) for d in ('north', 'south', 'east', 'west', 'up', 'down')}}],
    })


def sealed_container_models(iid):
    """封印コンテナ: 暗い台座と、上の開いた白いカプセルの殻。蓋（4枚の板）・中の台・光は SealedContainerRenderer が描く。
    手に持ったときの絵は、蓋を閉じた形。"""
    tier = int(iid[-1])
    tex = {'side': f'{MODID}:block/sealed_container_side_{tier}', 'base': f'{MODID}:block/sealed_container_base',
           'inner': f'{MODID}:block/sealed_container_inner', 'lid': f'{MODID}:block/sealed_container_lid_{tier}',
           'particle': f'{MODID}:block/sealed_container_side_{tier}'}

    def el(frm, to, faces):
        return {'from': frm, 'to': to, 'faces': {d: {'texture': t} for d, t in faces.items()}}

    all6 = ('north', 'south', 'east', 'west', 'up', 'down')
    elements = [
        el([0, 0, 0], [16, 3, 16], {d: '#base' for d in all6}),
        el([2, 3, 2], [14, 4, 14], {'up': '#inner'}),
        el([1, 3, 1], [15, 13, 2], {'north': '#side', 'south': '#inner', 'east': '#side', 'west': '#side', 'up': '#base'}),
        el([1, 3, 14], [15, 13, 15], {'south': '#side', 'north': '#inner', 'east': '#side', 'west': '#side', 'up': '#base'}),
        el([1, 3, 2], [2, 13, 14], {'west': '#side', 'east': '#inner', 'up': '#base'}),
        el([14, 3, 2], [15, 13, 14], {'east': '#side', 'west': '#inner', 'up': '#base'}),
    ]
    write_json(ASSETS / 'models' / 'block' / f'{iid}.json', {'parent': 'minecraft:block/block', 'textures': tex, 'elements': elements})
    write_json(ASSETS / 'blockstates' / f'{iid}.json', {'variants': {'': {'model': f'{MODID}:block/{iid}'}}})
    lid = el([1, 13, 1], [15, 15, 15], {d: '#lid' for d in all6})
    write_json(ASSETS / 'models' / 'item' / f'{iid}.json', {
        'parent': 'minecraft:block/block', 'textures': tex, 'elements': elements + [lid],
        'display': {'gui': {'rotation': [30, 225, 0], 'scale': [0.625, 0.625, 0.625]}}})


def sealed_container_assets():
    """封印コンテナのテクスチャ、色つきの遺構の照明、重力ブーツの防具の絵。"""
    import art16
    tb = ASSETS / 'textures' / 'block'
    for tier in (1, 2, 3, 4):
        art16.save(art16.sealed_container_side(tier), tb / f'sealed_container_side_{tier}.png')
        art16.save(art16.sealed_container_lid(tier), tb / f'sealed_container_lid_{tier}.png')
    art16.save(art16.sealed_container_base(), tb / 'sealed_container_base.png')
    art16.save(art16.sealed_container_inner(), tb / 'sealed_container_inner.png')
    armor = ASSETS / 'textures' / 'models' / 'armor'
    armor.mkdir(parents=True, exist_ok=True)
    art16.gravity_boots_layer().save(armor / 'gravity_layer_1.png')


def cable_models(iid):
    """ケーブル: 一辺 w（偶数）の正方形の断面。腕は面から中心の箱の縁まで（z=0〜8-w/2）、中心はまっすぐなら同じ断面の
    短い筒、分かれ目では一辺 w の箱。座標はすべて整数で、テクスチャは1ドットを1ドットのまま貼る（UV の大きさ＝面の大きさ）。
    側面は行 8-w/2〜8+w/2 の帯、切り口と分かれ目の箱は同じ帯が四角く広がる絵。
    電力が通っているときの光は、ブロックエンティティの描画（CableRenderer）で通った道の芯だけに重ねる。"""
    import art16
    w = art16.CABLE_WIDTHS[iid]
    lo, hi = 8 - w // 2, 8 + w // 2
    tex = f'{MODID}:block/{iid}'

    def side(length):
        return {'uv': [0, lo, length, hi], 'texture': '#c'}

    def tube(z0, z1, cap):
        faces = {'east': side(z1 - z0), 'west': side(z1 - z0),
                 'up': {**side(z1 - z0), 'rotation': 90}, 'down': {**side(z1 - z0), 'rotation': 90}}
        if cap:
            faces['north'] = {'uv': [lo, lo, hi, hi], 'texture': '#e'}
        return {'from': [lo, lo, z0], 'to': [hi, hi, z1], 'faces': faces}

    for suffix, side_tex, end_tex in [('', tex, f'{tex}_end')]:
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
    for suffix in ('',):
        def when(extra):
            return dict(extra)
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
    # 遺構のアンドロイド（128×64。tools/androids.py）
    androids.textures(te, save)
    for egg in ('blade_android', 'whip_android', 'saber_android', 'axe_android', 'bit_android', 'gunner_android'):
        write_json(ASSETS / 'models' / 'item' / f'{egg}_spawn_egg.json', {'parent': 'minecraft:item/template_spawn_egg'})

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
        elif iid.startswith('ruin_lamp'):
            glow = {'ruin_lamp': (150, 230, 255), 'ruin_lamp_amber': (255, 196, 100), 'ruin_lamp_verdant': (140, 240, 160),
                    'ruin_lamp_violet': (200, 160, 255), 'ruin_lamp_crimson': (255, 110, 130)}[iid]
            d.rectangle([4, 4, 11, 11], fill=(60, 66, 70, 255))
            d.rectangle([5, 5, 10, 10], fill=glow + (255,))
            d.point([(5, 5), (10, 10)], fill=tuple(c // 2 + 30 for c in glow) + (255,))
        elif iid == 'ruin_cache':
            d.rectangle([2, 3, 13, 12], fill=(57, 66, 74, 255), outline=aged_seam)
            d.line([(4, 7), (11, 7)], fill=(150, 230, 255, 255))
        elif iid == 'ruin_guard_dock':
            d.ellipse([3, 3, 12, 12], fill=(57, 66, 74, 255), outline=aged_seam)
            d.ellipse([6, 6, 9, 9], fill=(255, 120, 100, 255))
        elif iid == 'ruin_android_fabricator':
            # 底面（上面と側面は blocks_extra3 が作る）
            d.rectangle([1, 1, 14, 14], fill=(52, 58, 68, 255), outline=aged_seam)
            d.rectangle([5, 5, 10, 10], outline=(120, 225, 255, 255))
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
    for egg in ('horizon_warden', 'echo_sentinel', 'gravity_remnant', 'ergosphere_leviathan', 'last_archivist', 'foundry_overseer'):
        write_json(ASSETS / 'models' / 'item' / f'{egg}_spawn_egg.json', {'parent': 'minecraft:item/template_spawn_egg'})

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
                if iid in art16.ANDROID_FACES:
                    # 顔のバイザーは16pxの絵をそのまま拡大する（細かい表情を描き直さない）。まばたきも同じ動き
                    hd = hb / f'{iid}_front{"_on" if on else ""}.png'
                    art.save(sheet.resize((32, 32 * frames), __import__('PIL').Image.NEAREST), hd)
                    for meta in (Path(str(path) + '.mcmeta'), Path(str(hd) + '.mcmeta')):
                        if frames > 1:
                            write_json(meta, art16.ANDROID_BLINK)
                        elif meta.exists():
                            meta.unlink()
                    continue
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
        elif kind not in ('structure', 'fluid', 'machine') and kind not in BLOCK_KINDS and iid in art16.ANIMATED_ITEMS:
            # 動くアイテム: コマを縦に並べた絵と .mcmeta。HD 版は作らない（標準の動く絵をそのまま使う）
            sheet, ticks = art16.animated_item(iid)
            art16.save(sheet, ti / f'{iid}.png')
            write_json(ti / f'{iid}.png.mcmeta', {'animation': {'frametime': ticks, 'interpolate': False}})
        elif kind not in ('structure', 'fluid', 'machine') and kind not in BLOCK_KINDS:
            art16.save(art16.item_texture(iid, stage_of(name)), ti / f'{iid}.png')
            if iid in art.ITEM_ART:
                art.save(art.item_texture(iid, stage_of(name)), hi / f'{iid}.png')
    # 施設の建材の設計図（青焼きの紙）
    import blueprints
    blueprints.item_textures(ti, art16.save)
    # 移設レンチのケーブル撤去モード（表示灯が橙）
    art16.save(art16.grain(art16.relocation_wrench(cable=True), seed=art16.stable_hash('relocation_wrench') % 1000),
               ti / 'relocation_wrench_cable.png')
    # ケーブルの被覆（電力が流れている間の芯の色つき）
    for iid, core in CABLES.items():
        art16.save(art16.cable_texture(iid, core), tb / f'{iid}.png')
        art16.save(art16.cable_end(iid, core), tb / f'{iid}_end.png')
    # アンドロイドの設計メモリ・キット・無線充電器（tools/androids.py）
    androids.item_textures(ti, art16.save)
    androids.glass_textures(tb, art16.save)
    # 探索コンパス・隕石コンパスの針（16コマ）
    for k in range(16):
        art16.save(art16.compass_frame(k / 16), ti / f'explorer_compass_{k:02d}.png')
        art16.save(art16.compass_frame(k / 16, art16.METEOR_COMPASS), ti / f'meteor_compass_{k:02d}.png')
    black_hole_textures(art16, tb)
    manipulator_staff_texture(art16)
    # あとから足したブロック（遺構の変わり種・建材・重力パネル）
    import blocks_extra
    blocks_extra.textures(tb, art16.save)
    import blocks_extra3
    blocks_extra3.textures(tb, art16.save, lambda im, seed: art16.weather_existing(im, seed=seed), write_json)
    import blocks_extra4
    blocks_extra4.textures(tb, art16.save)
    import blocks_extra5
    blocks_extra5.textures(tb, art16.save)
    import blocks_extra6
    blocks_extra6.textures(tb, art16.save)
    import lab_blocks
    lab_blocks.textures(tb, art16.save)
    import genesis_assets
    genesis_assets.textures(tb, ti, art16.save)
    # 遺構の建材など、まだのっぺりしている絵にも汚し（使用感）を入れる
    from PIL import Image
    for name, (iid, _, kind) in MOD.items():
        if kind in ('ruin_block', 'ruin_glass') and not blocks_extra3.skip_weathering(iid):
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
    'black_hole.devour': ('black_hole/devour', 'ブロックが炉心へ吸い込まれる', 'Blocks get pulled into the core', 24),
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
    'sealed_container.denied': ('sealed/denied', '封印が鍵を拒む', 'Seal rejects', 12),
    'sealed_container.insert': ('sealed/insert', '鍵が差し込まれる', 'Key inserted', 12),
    'sealed_container.scan': ('sealed/scan', '封印が鍵を読み取る', 'Seal scans', 16),
    'sealed_container.unlock': ('sealed/unlock', '封印が解ける', 'Seal releases', 16),
    'sealed_container.vent': ('sealed/vent', '冷気が噴き出す', 'Cold gas vents', 16),
    'sealed_container.open': ('sealed/open', '蓋が開く', 'Lid opens', 16),
    'sealed_container.rise': ('sealed/rise', '中の台がせり上がる', 'Pedestal rises', 16),
    'sealed_container.lock': ('sealed/lock', '蓋が閉じて封印される', 'Container seals', 16),
    'gravity_boots.wall_jump': ('item/gravity_boots_wall_jump', '壁を蹴って跳ぶ', 'Wall jump', 12),
    'dimensional_pocket.open': ('item/dimensional_pocket_open', '次元ポケットが開く', 'Pocket opens', 8),
    'dimensional_pocket.close': ('item/dimensional_pocket_close', '次元ポケットが閉じる', 'Pocket closes', 8),
    'foundry.emerge': ('boss/foundry_emerge', 'ファウンドリが起動する', 'Foundry powers up', 32),
    'foundry.hurt': ('boss/foundry_hurt', 'ファウンドリの装甲が軋む', 'Foundry armor groans', 16),
    'foundry.arm_aim': ('boss/foundry_arm_aim', 'ロボットアームが動く', 'Robot arm moves', 24),
    'foundry.arm_grab': ('boss/foundry_arm_grab', 'アームがつかんで投げる', 'Arm grabs and throws', 24),
    'foundry.press_aim': ('boss/foundry_press_aim', 'プレス機が狙いを定める', 'Press takes aim', 24),
    'foundry.press_slam': ('boss/foundry_press_slam', 'プレス機が叩きつける', 'Press slams', 32),
    'foundry.conveyor_warn': ('boss/foundry_conveyor_warn', 'コンベアの警告音', 'Conveyor warning', 24),
    'foundry.conveyor_flip': ('boss/foundry_conveyor_flip', 'コンベアが向きを変える', 'Conveyor reverses', 24),
    'foundry.laser_aim': ('boss/foundry_laser_aim', '溶接レーザーが充填する', 'Welding laser charges', 24),
    'foundry.laser_fire': ('boss/foundry_laser_fire', '溶接レーザーがなぞる', 'Welding laser sweeps', 24),
    'foundry.print': ('boss/foundry_print', '製造ラインが印刷する', 'Production line prints', 24),
    'foundry.line_break': ('boss/foundry_line_break', '製造ラインが止まる', 'Production line breaks', 24),
    'foundry.enrage': ('boss/foundry_enrage', 'ファウンドリが試作機を呼ぶ', 'Foundry calls its prototypes', 32),
    'foundry.death': ('boss/foundry_death', 'ファウンドリが崩れ落ちる', 'Foundry collapses', 32),
    'echo_sentinel.emerge': ('boss/echo_sentinel_emerge', '残響の番人が姿を結ぶ', 'Echo Sentinel materializes', 32),
    'echo_sentinel.ambient': ('boss/echo_sentinel_ambient', '残響の番人がざわめく', 'Echo Sentinel hums', 16),
    'echo_sentinel.hurt': ('boss/echo_sentinel_hurt', '残響の番人の像が乱れる', 'Echo Sentinel glitches', 16),
    'echo_sentinel.bolt': ('boss/echo_sentinel_bolt', '光弾が放たれる', 'Light bolts fire', 16),
    'echo_sentinel.blink': ('boss/echo_sentinel_blink', '残響の番人が跳ぶ', 'Echo Sentinel blinks', 16),
    'echo_sentinel.strike_windup': ('boss/echo_sentinel_strike_windup', '刃に光が集まる', 'Blade gathers light', 16),
    'echo_sentinel.strike': ('boss/echo_sentinel_strike', '光の刃が斬る', 'Light blade slashes', 16),
    'echo_sentinel.lance_aim': ('boss/echo_sentinel_lance_aim', '投影器が狙いを定める', 'Projectors take aim', 24),
    'echo_sentinel.lance_fire': ('boss/echo_sentinel_lance_fire', '光の槍が放たれる', 'Light lances fire', 24),
    'echo_sentinel.circle_cast': ('boss/echo_sentinel_circle_cast', '足元に光の陣が映る', 'A circle of light appears', 20),
    'echo_sentinel.circle_erupt': ('boss/echo_sentinel_circle_erupt', '光の柱が立ち上がる', 'Pillar of light erupts', 24),
    'echo_sentinel.summon': ('boss/echo_sentinel_summon', '分身が現れる', 'Echoes appear', 20),
    'echo_sentinel.echo_vanish': ('boss/echo_sentinel_echo_vanish', '分身が消える', 'An echo vanishes', 16),
    'echo_sentinel.death_unravel': ('boss/echo_sentinel_death_unravel', '投影がほどける', 'Projection unravels', 32),
    'echo_sentinel.death_shatter': ('boss/echo_sentinel_death_shatter', '記録結晶が砕ける', 'Record crystal shatters', 32),
    'echo_projector.power_down': ('boss/echo_projector_power_down', '投影器が止まる', 'Projector powers down', 16),
    'gravity_remnant.emerge': ('boss/gravity_remnant_emerge', '槽の中で何かが脈打つ', 'Something throbs in the tank', 32),
    'containment_tank.crack': ('boss/containment_tank_crack', '封じ込め槽にひびが走る', 'Containment tank cracks', 24),
    'containment_tank.shatter': ('boss/containment_tank_shatter', '封じ込め槽が砕ける', 'Containment tank shatters', 32),
    'gravity_remnant.ambient': ('boss/gravity_remnant_ambient', '重力の澱が脈打つ', 'Gravity Remnant throbs', 16),
    'gravity_remnant.hurt': ('boss/gravity_remnant_hurt', '殻が欠ける', 'Shell chips', 16),
    'gravity_remnant.debris_launch': ('boss/gravity_remnant_debris_launch', '瓦礫が撃ち出される', 'Rubble hurled', 20),
    'gravity_debris.impact': ('boss/gravity_debris_impact', '瓦礫が砕ける', 'Rubble smashes', 16),
    'gravity_remnant.shell_regrow': ('boss/gravity_remnant_shell_regrow', '瓦礫が集まる', 'Rubble gathers', 20),
    'gravity_remnant.lift': ('boss/gravity_remnant_lift', '重力が反転する', 'Gravity inverts', 24),
    'gravity_remnant.slam': ('boss/gravity_remnant_slam', '重力が叩きつける', 'Gravity slams down', 24),
    'gravity_remnant.well_loop': ('boss/gravity_remnant_well_loop', '重力井戸が渦巻く', 'Gravity well swirls', 20),
    'gravity_remnant.death_collapse': ('boss/gravity_remnant_death_collapse', '重力の澱が崩れる', 'Gravity Remnant collapses', 32),
    'gravity_remnant.death_implode': ('boss/gravity_remnant_death_implode', '核がつぶれる', 'Core implodes', 48),
    'horizon_warden.emerge': ('boss/horizon_warden_emerge', 'ホライズン・ウォーデンが目覚める', 'Horizon Warden awakens', 48),
    'horizon_warden.phase': ('boss/horizon_warden_phase', '外装が弾け飛ぶ', 'Armor bursts off', 32),
    'horizon_warden.beam_charge': ('boss/horizon_warden_beam_charge', 'ビームをためる', 'Beam charges', 32),
    'horizon_warden.beam_fire': ('boss/horizon_warden_beam_fire', '事象の地平線ビームが放たれる', 'Event horizon beam fires', 48),
    'horizon_warden.laser_loop': ('boss/horizon_warden_laser_loop', 'レーザーが焼く', 'Laser burns', 24),
    'horizon_warden.sweep': ('boss/horizon_warden_sweep', '刃が薙ぎ払う', 'Blade sweeps', 20),
    'horizon_warden.grip': ('boss/horizon_warden_grip', '重力の手がつかむ', 'Gravity grip seizes', 24),
    'horizon_warden.shockwave': ('boss/horizon_warden_shockwave', '衝撃波が走る', 'Shockwave', 32),
    'horizon_warden.death': ('boss/horizon_warden_death', 'ホライズン・ウォーデンが崩れ落ちる', 'Horizon Warden falls', 48),
    'horizon_warden.ambient': ('boss/horizon_warden_ambient', 'ホライズン・ウォーデンがうなる', 'Horizon Warden hums', 20),
    'horizon_warden.hurt': ('boss/horizon_warden_hurt', 'ホライズン・ウォーデンが傷つく', 'Horizon Warden hurts', 24),
    'guardian_core.awaken': ('boss/guardian_core_awaken', '封印核が目覚める', 'Guardian core awakens', 32),
    'vault.unseal': ('boss/vault_unseal', '保管庫の力場が消える', 'Vault field dissipates', 24),
    'keraunos_tower.charge': ('machine/keraunos_tower_charge', '放電塔が充電する', 'Discharge tower charges', 24),
    'keraunos_tower.discharge': ('machine/keraunos_tower_discharge', '人工の雷が走る', 'Artificial lightning', 48),
    'keraunos_tower.mode': ('machine/keraunos_tower_mode', '撃ち方が切り替わる', 'Firing mode switches', 8),
    'impact_generator.absorb': ('machine/impact_generator_absorb', '衝撃が電気に変わる', 'Impact absorbed', 12),
    'deep_sea_collector.collect': ('machine/deep_sea_collector_collect', '高圧結晶ができる', 'Pressure crystal forms', 12),
    'void_collector.collect': ('machine/void_collector_collect', '虚空の塵が集まる', 'Void dust gathers', 12),
    'fulgurite.form': ('nature/fulgurite_form', '砂が雷ガラスになる', 'Sand fuses into fulgurite', 20),
    'ergosphere_leviathan.emerge': ('boss/ergosphere_leviathan_emerge', '炉心に渦が開く', 'A vortex opens in the core', 48),
    'ergosphere_leviathan.ambient': ('boss/ergosphere_leviathan_ambient', 'エルゴ球の大蛇が歌う', 'Ergosphere Leviathan sings', 32),
    'ergosphere_leviathan.hurt': ('boss/ergosphere_leviathan_hurt', '鱗がきしむ', 'Scales grind', 20),
    'ergosphere_leviathan.roar': ('boss/ergosphere_leviathan_roar', 'エルゴ球の大蛇が吠える', 'Ergosphere Leviathan roars', 48),
    'ergosphere_leviathan.dive': ('boss/ergosphere_leviathan_dive', 'エルゴ球の大蛇が突進する', 'Ergosphere Leviathan dives', 32),
    'ergosphere_leviathan.breath_charge': ('boss/ergosphere_leviathan_breath_charge', '口の渦が吸い込む', 'The maw draws in', 32),
    'ergosphere_leviathan.breath_fire': ('boss/ergosphere_leviathan_breath_fire', '渦の光線が放たれる', 'Vortex breath fires', 48),
    'ergosphere_leviathan.shed': ('boss/ergosphere_leviathan_shed', '鱗が剥がれる', 'Scales shed', 24),
    'ergosphere_leviathan.death': ('boss/ergosphere_leviathan_death', 'エルゴ球の大蛇が沈んでいく', 'Ergosphere Leviathan sinks', 48),
    'ergosphere_leviathan.implode': ('boss/ergosphere_leviathan_implode', '渦がつぶれる', 'The vortex collapses', 48),
    'ergosphere_leviathan.hatchling': ('boss/ergosphere_leviathan_hatchling', '小さな何かが鳴く', 'Something small chirps', 16),
    'ergosphere_leviathan.rumble': ('boss/ergosphere_leviathan_rumble', '床の下で何かがうごめく', 'Something stirs beneath the floor', 48),
    'ergosphere_leviathan.swallow': ('boss/ergosphere_leviathan_swallow', '顎が閉じる', 'Jaws snap shut', 32),
    'ergosphere_leviathan.whip': ('boss/ergosphere_leviathan_whip', '尾が薙ぎ払う', 'The tail lashes', 24),
    'ergosphere_leviathan.molt': ('boss/ergosphere_leviathan_molt', '鱗が弾け飛ぶ', 'Scales burst away', 48),
    'ergosphere_leviathan.coil': ('boss/ergosphere_leviathan_coil', '大蛇がとぐろを巻く', 'The Leviathan coils', 32),
    'ergosphere_leviathan.ascend': ('boss/ergosphere_leviathan_ascend', '龍が噴き上がる', 'The wyrm erupts', 64),
    'ergosphere_leviathan.jet_charge': ('boss/ergosphere_leviathan_jet_charge', '角に光が集まる', 'Light gathers in the horns', 32),
    'ergosphere_leviathan.jet': ('boss/ergosphere_leviathan_jet', '相対論的ジェットが薙ぐ', 'Relativistic jets sweep', 48),
    'ergosphere_leviathan.storm': ('boss/ergosphere_leviathan_storm', '特異点が降る', 'Singularities rain', 32),
    'ergosphere_leviathan.burst': ('boss/ergosphere_leviathan_burst', '床が裂けて何かが噴き上がる', 'Something bursts from the floor', 64),
    'ergosphere_leviathan.rise': ('boss/ergosphere_leviathan_rise', '大蛇が渦を巻いて昇る', 'The Leviathan spirals upward', 48),
    'foundry.online': ('boss/foundry_online', 'ファウンドリが動き出す', 'Foundry comes online', 32),
    'foundry.dying': ('boss/foundry_dying', 'モーターが力尽きていく', 'Motors wind down', 24),
    'echo_sentinel.awake': ('boss/echo_sentinel_awake', '残響の番人が目覚める', 'Echo Sentinel awakens', 32),
    'gravity_remnant.pulse': ('boss/gravity_remnant_pulse', '核が脈打つ', 'Core throbs', 24),
    'last_archivist.dissolve': ('boss/last_archivist_dissolve', '体が光の塵になる', 'Body dissolves into light', 32),
    'last_archivist.inhale': ('boss/last_archivist_inhale', '特異点が吸い込む', 'The singularity draws in', 48),
    'android.blade_cast': ('android/blade_cast', '光柱を呼ぶ', 'Light pillars summoned', 24),
    'android.tend': ('android/tend', 'アンドロイドが植物の世話をする', 'Android tends a plant', 12),
    'android.sweep': ('android/sweep', 'アンドロイドが床を掃く', 'Android sweeps', 12),
    'android.chatter': ('android/chatter', 'アンドロイドがおしゃべりする', 'Androids chatter', 12),
    'android.thruster': ('android/thruster', None, None, 16),  # くり返し鳴るので字幕は出さない
    'horizon_warden.bolt_hit': ('boss/horizon_warden_bolt_hit', '光弾がはじける', 'Light bolt bursts', 24),
    'last_archivist.floor_fade': ('boss/last_archivist_floor_fade', 'ホログラムの床が消える', 'Hologram floor fades', 32),
    'security_drone.sputter': ('ruin/security_drone_sputter', 'ドローンが火花を散らす', 'Drone sparks', 16),
    # ボスのうなり（出現から倒されるまでくり返す。ずっと鳴るので字幕は出さない）
    'ergosphere_leviathan.drone': ('boss/ergosphere_leviathan_drone', None, None, 64),
    'horizon_warden.drone': ('boss/horizon_warden_drone', None, None, 64),
    'echo_sentinel.drone': ('boss/echo_sentinel_drone', None, None, 64),
    'gravity_remnant.drone': ('boss/gravity_remnant_drone', None, None, 64),
    'foundry.drone': ('boss/foundry_drone', None, None, 64),
    'last_archivist.drone': ('boss/last_archivist_drone', None, None, 64),
    'last_archivist.emerge': ('boss/last_archivist_emerge', '記録が集まる', 'Records gather', 48),
    'last_archivist.ambient': ('boss/last_archivist_ambient', '最後の記録者がささやく', 'The Last Archivist whispers', 24),
    'last_archivist.hurt': ('boss/last_archivist_hurt', '光輪が揺らぐ', 'The halo wavers', 20),
    'last_archivist.phase': ('boss/last_archivist_phase', '記録者が姿を変える', 'The Archivist transforms', 48),
    'last_archivist.judgment_aim': ('boss/last_archivist_judgment_aim', '光輪が狙いを定める', 'The halo takes aim', 32),
    'last_archivist.judgment_fire': ('boss/last_archivist_judgment_fire', '裁きの光が降る', 'Judgment falls', 48),
    'last_archivist.verdict': ('boss/last_archivist_verdict', '光の陣が描かれる', 'Circles are drawn', 32),
    'last_archivist.collapse': ('boss/last_archivist_collapse', '小さな特異点が落ちる', 'Singularities fall', 32),
    'last_archivist.recall': ('boss/last_archivist_recall', '記録者が記録を読み上げる', 'The Archivist recites', 32),
    'last_archivist.nova_charge': ('boss/last_archivist_nova_charge', '光が内へ集まる', 'Light gathers inward', 48),
    'last_archivist.nova': ('boss/last_archivist_nova', '新星が弾ける', 'Nova bursts', 64),
    'last_archivist.death': ('boss/last_archivist_death', '光輪が砕ける', 'The halo shatters', 48),
    'last_archivist.death_flash': ('boss/last_archivist_death_flash', '最後の光', 'The last light', 64),
    'genesis.form': ('genesis/form', '創世炉が組み上がる', 'Genesis Engine assembles', 24),
    'genesis.ambient': ('genesis/ambient', '創世炉が脈打つ', 'Genesis Engine pulses', 32),
    'genesis.seed': ('genesis/seed', '原初の種が沈む', 'Primordial Seed sinks in', 32),
    'genesis.ignite': ('genesis/ignite', '種火がともる', 'The seed ignites', 64),
    'genesis.surge': ('genesis/surge', '宇宙がうねる', 'Proto-universe surges', 48),
    'genesis.alarm': ('genesis/alarm', '暴走の兆し', 'Surge warning', 64),
    'genesis.collapse': ('genesis/collapse', '宇宙が崩れかける', 'Proto-universe collapses', 64),
    'genesis.lever': ('genesis/lever', '投入が切り替わる', 'Feed toggles', 16),
    'genesis.birth_collapse': ('genesis/birth_collapse', '光が一点に落ちる', 'Light falls into a point', 96),
    'genesis.big_bang': ('genesis/big_bang', '宇宙が生まれる', 'A universe is born', 128),
    'genesis.sky': ('genesis/sky', '銀河が空へ昇る', 'A galaxy rises into the sky', 128),
    'genesis.synth': ('genesis/synth', '質量が生まれる', 'Mass is synthesized', 24),
    # アンドロイド（0.12.0。バニラの音の代わり）
    'android.power_down': ('android/power_down', 'アンドロイドの充電が切れる', 'Android powers down', 16),
    'android.charge_start': ('android/charge_start', 'アンドロイドが充電を始める', 'Android starts charging', 16),
    'android.charged': ('android/charged', 'アンドロイドの充電が満ちる', 'Android is fully charged', 16),
    'android.gunner_shot': ('android/gunner_shot', '小型銃が撃たれる', 'Pistol fires', 32),
    'android.railgun_assemble': ('android/railgun_assemble', 'レールガンが組み上がる', 'Railgun assembles', 24),
    'android.railgun_charge': ('android/railgun_charge', 'レールガンがためる', 'Railgun charges', 32),
    'android.railgun_fire': ('android/railgun_fire', 'レールガンが放たれる', 'Railgun fires', 64),
    'android.ring_inflate': ('android/ring_inflate', '浮き輪がふくらむ', 'Float ring inflates', 16),
    # 画面のボタンを押した音（バニラのボタンと同じく、字幕は出さない）
    'ui.click': ('ui/click', None, None, 16),
}
# 音のファイルがまだないときに代わりに鳴らすバニラの音（ファイルを置けば自動でそちらに替わる）
SOUND_FALLBACKS = {
    'ergosphere_leviathan.emerge': 'minecraft:entity.warden.emerge',
    'ergosphere_leviathan.ambient': 'minecraft:entity.elder_guardian.ambient',
    'ergosphere_leviathan.hurt': 'minecraft:entity.elder_guardian.hurt',
    'ergosphere_leviathan.roar': 'minecraft:entity.ender_dragon.growl',
    'ergosphere_leviathan.dive': 'minecraft:entity.phantom.swoop',
    'ergosphere_leviathan.breath_charge': 'minecraft:entity.warden.sonic_charge',
    'ergosphere_leviathan.breath_fire': 'minecraft:entity.warden.sonic_boom',
    'ergosphere_leviathan.shed': 'minecraft:entity.shulker.shoot',
    'ergosphere_leviathan.death': 'minecraft:entity.ender_dragon.death',
    'ergosphere_leviathan.implode': 'minecraft:entity.generic.explode',
    'ergosphere_leviathan.hatchling': 'minecraft:entity.silverfish.ambient',
    'ergosphere_leviathan.rumble': 'minecraft:entity.warden.dig',
    'ergosphere_leviathan.swallow': 'minecraft:entity.ravager.attack',
    'ergosphere_leviathan.whip': 'minecraft:entity.player.attack.sweep',
    'ergosphere_leviathan.molt': 'minecraft:entity.zombie.break_wooden_door',
    'ergosphere_leviathan.coil': 'minecraft:entity.ender_dragon.flap',
    'ergosphere_leviathan.ascend': 'minecraft:entity.wither.spawn',
    'ergosphere_leviathan.jet_charge': 'minecraft:block.beacon.power_select',
    'ergosphere_leviathan.jet': 'minecraft:entity.warden.sonic_boom',
    'ergosphere_leviathan.storm': 'minecraft:block.respawn_anchor.deplete',
    'last_archivist.emerge': 'minecraft:block.end_portal.spawn',
    'last_archivist.ambient': 'minecraft:block.conduit.ambient',
    'last_archivist.hurt': 'minecraft:block.amethyst_block.hit',
    'last_archivist.phase': 'minecraft:item.totem.use',
    'last_archivist.judgment_aim': 'minecraft:block.beacon.activate',
    'last_archivist.judgment_fire': 'minecraft:entity.evoker.cast_spell',
    'last_archivist.verdict': 'minecraft:block.respawn_anchor.charge',
    'last_archivist.collapse': 'minecraft:block.respawn_anchor.deplete',
    'last_archivist.recall': 'minecraft:entity.illusioner.cast_spell',
    'last_archivist.nova_charge': 'minecraft:block.beacon.power_select',
    'last_archivist.nova': 'minecraft:entity.warden.sonic_boom',
    'last_archivist.death': 'minecraft:block.beacon.deactivate',
    'last_archivist.death_flash': 'minecraft:block.end_portal.spawn',
    'genesis.form': 'minecraft:block.beacon.activate',
    'genesis.ambient': 'minecraft:block.beacon.ambient',
    'genesis.seed': 'minecraft:block.respawn_anchor.charge',
    'genesis.ignite': 'minecraft:block.end_portal_frame.fill',
    'genesis.surge': 'minecraft:block.respawn_anchor.deplete',
    'genesis.alarm': 'minecraft:block.bell.resonate',
    'genesis.collapse': 'minecraft:entity.generic.explode',
    'genesis.lever': 'minecraft:block.lever.click',
    'genesis.birth_collapse': 'minecraft:block.portal.trigger',
    'genesis.big_bang': 'minecraft:entity.wither.spawn',
    'genesis.sky': 'minecraft:ui.toast.challenge_complete',
    'genesis.synth': 'minecraft:block.amethyst_block.resonate',
    'horizon_warden.ambient': 'minecraft:block.beacon.ambient',
    'sealed_container.denied': 'minecraft:block.dispenser.fail',
    'sealed_container.insert': 'minecraft:block.vault.insert_item',
    'sealed_container.scan': 'minecraft:block.beacon.activate',
    'sealed_container.unlock': 'minecraft:block.vault.activate',
    'sealed_container.vent': 'minecraft:block.fire.extinguish',
    'sealed_container.open': 'minecraft:block.vault.open_shutter',
    'sealed_container.rise': 'minecraft:block.beacon.power_select',
    'sealed_container.lock': 'minecraft:block.vault.close_shutter',
    'gravity_boots.wall_jump': 'minecraft:entity.breeze.jump',
    'foundry.emerge': 'minecraft:block.beacon.activate',
    'foundry.hurt': 'minecraft:entity.iron_golem.hurt',
    'foundry.arm_aim': 'minecraft:block.piston.extend',
    'foundry.arm_grab': 'minecraft:entity.iron_golem.attack',
    'foundry.press_aim': 'minecraft:block.vault.activate',
    'foundry.press_slam': 'minecraft:block.anvil.land',
    'foundry.conveyor_warn': 'minecraft:block.note_block.pling',
    'foundry.conveyor_flip': 'minecraft:block.piston.contract',
    'foundry.laser_aim': 'minecraft:block.beacon.power_select',
    'foundry.laser_fire': 'minecraft:entity.guardian.attack',
    'foundry.print': 'minecraft:block.crafter.craft',
    'foundry.line_break': 'minecraft:block.beacon.deactivate',
    'foundry.enrage': 'minecraft:block.respawn_anchor.charge',
    'foundry.death': 'minecraft:entity.generic.explode',
    'dimensional_pocket.open': 'minecraft:block.ender_chest.open',
    'dimensional_pocket.close': 'minecraft:block.ender_chest.close',
}


def sounds_json():
    """assets/singulo/sounds.json と字幕。音のファイルがそろっているかも確かめる。"""
    base = Path(__file__).resolve().parent.parent / 'src' / 'main' / 'resources' / 'assets' / MODID / 'sounds'
    out = {}
    missing = []
    pending = []
    used = set()
    for name, (path, ja, en, dist) in SOUNDS.items():
        variants = sorted((q for q in (base / path).parent.glob(f'{Path(path).name}_*.ogg') if q.stem.rsplit('_', 1)[1].isdigit()),
                          key=lambda q: int(q.stem.rsplit('_', 1)[1]))
        if (base / f'{path}.ogg').exists():
            sounds = [{'name': f'{MODID}:{path}', 'attenuation_distance': dist}]
            used.add(path)
        elif variants:
            # 番号つきのファイル（<path>_1.ogg, _2.ogg …）は、ランダムに鳴らすバリエーション
            sounds = []
            for q in variants:
                rel = str(q.relative_to(base)).replace('\\', '/')[:-4]
                sounds.append({'name': f'{MODID}:{rel}', 'attenuation_distance': dist})
                used.add(rel)
        elif name in SOUND_FALLBACKS:
            sounds = [{'name': SOUND_FALLBACKS[name], 'type': 'event'}]
            pending.append(path)
        else:
            missing.append(path)
            sounds = []
        if ja is None:
            out[name] = {'sounds': sounds}
        else:
            out[name] = {'subtitle': f'subtitles.{MODID}.{name}', 'sounds': sounds}
            UI_LANG[f'subtitles.{MODID}.{name}'] = (ja, en)
    unused = sorted(str(p.relative_to(base)).replace('\\', '/')[:-4] for p in base.rglob('*.ogg')
                    if str(p.relative_to(base)).replace('\\', '/')[:-4] not in used)
    if missing:
        raise SystemExit(f'効果音のファイルがない: {missing}')
    # Java 側（SinguloSounds.NAMES）に登録がないと、鳴らした瞬間にゲームが落ちる
    java = (Path(__file__).resolve().parent.parent / 'src' / 'main' / 'java' / 'io' / 'github' / 'genichimaruo' / 'singulo'
            / 'registry' / 'SinguloSounds.java').read_text(encoding='utf-8')
    names_block = java[java.index('NAMES = {'):java.index('};', java.index('NAMES = {'))]
    registered = set(re.findall(r'"([a-z0-9_.]+)"', names_block))
    if set(SOUNDS) != registered:
        raise SystemExit(f'SinguloSounds.NAMES と SOUNDS が合わない: Java に足りない {sorted(set(SOUNDS) - registered)}、'
                         f'余分 {sorted(registered - set(SOUNDS))}')
    if unused:
        print(f'  使っていない効果音のファイル: {unused}')
    if pending:
        print(f'  まだファイルがなく、バニラの音で代用している効果音: {len(pending)} 件')
    write_json(ASSETS / 'sounds.json', out)


def accessory_item_models():
    """アンドロイドの装飾: 染める絵（layer0）と、染めない絵（layer1）。"""
    from androids import ACCESSORY_IDS
    for iid in ACCESSORY_IDS:
        write_json(ASSETS / 'models' / 'item' / f'{iid}.json', {'parent': 'minecraft:item/generated', 'textures': {
            'layer0': f'{MODID}:item/{iid}', 'layer1': f'{MODID}:item/{iid}_detail'}})


def umbrella_item_model():
    """傘: インベントリ・地面・額縁・一人称は平らな絵のまま、三人称で手に持つと開いた傘の立体になる
    （neoforge:separate_transforms）。布は色付けの番号0（染めた色）、柄と石突きは染めない。"""
    from PIL import Image, ImageDraw
    iid = 'android_umbrella'
    ti = ASSETS / 'textures' / 'item'
    # 立体の布（白。染めた色で塗る。骨の区切りがうっすら見える）と、柄の木と、石突きの金属
    cloth = Image.new('RGBA', (16, 16), (246, 246, 246, 255))
    dc = ImageDraw.Draw(cloth)
    for x in (0, 5, 10, 15):
        dc.line([(x, 0), (x, 15)], fill=(214, 214, 214, 255))
    wood = Image.new('RGBA', (16, 16), (110, 82, 58, 255))
    ImageDraw.Draw(wood).line([(0, 0), (15, 15)], fill=(92, 68, 48, 255))
    metal = Image.new('RGBA', (16, 16), (200, 204, 212, 255))
    for name, im in (('umbrella_cloth', cloth), ('umbrella_wood', wood), ('umbrella_metal', metal)):
        path = ti / f'{name}.png'
        path.parent.mkdir(parents=True, exist_ok=True)
        im.save(path)

    def box(a, b, tex, tint=False):
        faces = {}
        for f in ('north', 'south', 'east', 'west', 'up', 'down'):
            # UV を明示する（座標が 0〜16 の外に出る面で、テクスチャ置き場の隣の絵を拾わないように）
            faces[f] = {'texture': tex, 'uv': [0, 0, 16, 16], **({'tintindex': 0} if tint else {})}
        return {'from': a, 'to': b, 'faces': faces}

    def ring(outer, inner, y0, y1):
        """布の1段: 外の四角から内の四角を抜いた輪（4枚の板）。下から見ると、内側が上の段へ凹んで見える。"""
        o0, o1, i0, i1 = outer[0], outer[1], inner[0], inner[1]
        return [box([o0, y0, o0], [o1, y1, i0], '#cloth', True), box([o0, y0, i1], [o1, y1, o1], '#cloth', True),
                box([o0, y0, i0], [i0, y1, i1], '#cloth', True), box([i1, y0, i0], [o1, y1, i1], '#cloth', True)]

    # 柄を下へのばし（モデルは y −16 まで使える）、持ち手で握る。布は頭より上に来る
    elements = [
        box([7.5, -9, 7.5], [8.5, 28, 8.5], '#wood'),                      # 柄
        box([8.5, -10, 7.5], [10.5, -9, 8.5], '#wood'),                    # 曲がった持ち手
        box([9.5, -9, 7.5], [10.5, -7, 8.5], '#wood'),
        *ring((-4, 20), (-1, 17), 26, 27),                                 # 布: 下ほど広い4段の輪。内側は段々に凹む
        *ring((-1, 17), (3, 13), 27, 28.5),
        *ring((3, 13), (6, 10), 28.5, 29.5),
        box([6, 29.5, 6], [10, 30.5, 10], '#cloth', True),                 # てっぺん（ここだけ板でふさぐ）
        box([7.5, 30.5, 7.5], [8.5, 32, 8.5], '#metal'),                   # 石突き
    ]
    hand = {'rotation': [54, 0, 0], 'translation': [0, 2.6, 7.5], 'scale': [0.7, 0.7, 0.7]}
    base = {'textures': {'cloth': f'{MODID}:item/umbrella_cloth', 'wood': f'{MODID}:item/umbrella_wood',
                         'metal': f'{MODID}:item/umbrella_metal', 'particle': f'{MODID}:item/{iid}'},
            'elements': elements,
            'display': {'thirdperson_righthand': hand, 'thirdperson_lefthand': hand}}
    flat = {'parent': 'minecraft:item/generated',
            'textures': {'layer0': f'{MODID}:item/{iid}', 'layer1': f'{MODID}:item/{iid}_detail'}}
    write_json(ASSETS / 'models' / 'item' / f'{iid}.json', {
        'loader': 'neoforge:separate_transforms',
        'base': base,
        'perspectives': {k: flat for k in ('gui', 'ground', 'fixed', 'head', 'firstperson_righthand', 'firstperson_lefthand')}})


def fluid_canister_item_model():
    """液体キャニスター: 容器の絵（layer0）と、中身の色に染める窓の絵（layer1。空なら描かない）。"""
    from PIL import Image
    iid = 'fluid_canister'
    write_json(ASSETS / 'models' / 'item' / f'{iid}.json', {'parent': 'minecraft:item/generated', 'textures': {
        'layer0': f'{MODID}:item/{iid}', 'layer1': f'{MODID}:item/{iid}_fluid'}})
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = im.load()
    for y in range(6, 12):
        for x in range(6, 10):
            v = 255 if x == 6 or y == 6 else 225 if x < 9 else 190
            px[x, y] = (v, v, v, 255)
    path = ASSETS / 'textures' / 'item' / f'{iid}_fluid.png'
    path.parent.mkdir(parents=True, exist_ok=True)
    im.save(path)


def fluid_sample_item_model():
    """流体の見本: しずくの絵（流体の色に染める）。"""
    write_json(ASSETS / 'models' / 'item' / 'fluid_sample.json',
               {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{MODID}:item/fluid_sample'}})
    # 化学物質の見本（Mekanism）: 泡の絵（化学物質の色に染める）
    write_json(ASSETS / 'models' / 'item' / 'chemical_sample.json',
               {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{MODID}:item/chemical_sample'}})


def work_card_item_model():
    """作業位置カード: 記録済み（singulo:recorded）なら、緑の帯とピンの絵にする。"""
    iid = 'work_location_card'
    write_json(ASSETS / 'models' / 'item' / f'{iid}_recorded.json',
               {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{MODID}:item/{iid}_recorded'}})
    write_json(ASSETS / 'models' / 'item' / f'{iid}.json', {
        'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{MODID}:item/{iid}'},
        'overrides': [{'predicate': {f'{MODID}:recorded': 1}, 'model': f'{MODID}:item/{iid}_recorded'}]})


def area_card_item_model():
    """範囲カード: 1つ目の角だけ（singulo:recorded 0.5）なら角が1つ、範囲ができていれば（1）角が2つの絵にする。"""
    iid = 'area_card'
    for suffix in ('first', 'recorded'):
        write_json(ASSETS / 'models' / 'item' / f'{iid}_{suffix}.json',
                   {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{MODID}:item/{iid}_{suffix}'}})
    write_json(ASSETS / 'models' / 'item' / f'{iid}.json', {
        'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{MODID}:item/{iid}'},
        'overrides': [{'predicate': {f'{MODID}:recorded': 0.5}, 'model': f'{MODID}:item/{iid}_first'},
                      {'predicate': {f'{MODID}:recorded': 1}, 'model': f'{MODID}:item/{iid}_recorded'}]})


def glasses_item_model():
    """測量メガネ: 持ち物・手では平らな絵、頭に掛けたときだけ立体の四角いメガネ（NeoForge の separate_transforms）。
    頭の模型は、頭（16の箱の中で 1.6〜14.4）の前の面に沿って、目の高さに四角い枠2つ・ブリッジ・つるを置き、枠の中に透けたレンズを張る。"""
    iid = 'survey_glasses'
    t = {'frame': f'{MODID}:item/{iid}_worn', 'particle': f'{MODID}:item/{iid}_worn'}
    frame_uv, lens_uv = [1, 1, 3, 3], [8, 0, 16, 8]

    def box(a, b, uv=frame_uv, faces=('north', 'south', 'east', 'west', 'up', 'down')):
        return {'from': a, 'to': b, 'faces': {d: {'uv': uv, 'texture': '#frame'} for d in faces}}
    fz0, fz1 = 0.9, 1.5                      # 金具の奥行き（頭の前の面 1.6 より少し前）
    els = []
    # 縁なし（リムレス）: 枠はなく、透けたレンズの板を、ブリッジとつるの金具だけで支える（板の縁の面がうっすら見える）
    for x0, x1 in ((2.4, 7.2), (8.8, 13.6)):
        els.append(box([x0, 6.6, 1.05], [x1, 9.4, 1.35], lens_uv))
    els += [box([7.2, 8.6, fz0], [8.8, 9.0, fz1]),                                   # ブリッジ
            box([1.0, 8.6, fz0], [2.6, 9.0, fz1]), box([13.4, 8.6, fz0], [15.0, 9.0, fz1]),   # つるの付け根（レンズの端を留める）
            box([1.0, 8.6, fz1], [1.6, 9.0, 10.0]), box([14.4, 8.6, fz1], [15.0, 9.0, 10.0]),  # つる
            box([13.8, 9.0, fz0 + 0.1], [14.4, 9.4, fz0 + 0.5], [0, 8, 2, 10])]               # 表示灯
    write_json(ASSETS / 'models' / 'item' / f'{iid}_worn.json', {
        'parent': 'minecraft:block/block', 'render_type': 'minecraft:translucent', 'textures': t, 'elements': els})

    def held(worn):
        return {'loader': 'neoforge:separate_transforms',
                'base': {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{MODID}:item/{iid}'}},
                'perspectives': {'head': {'parent': f'{MODID}:item/{worn}'}}}
    # 掛ける高さ（ピクセル。Java の SurveyGlassesItem.MIN_HEIGHT〜MAX_HEIGHT）ごとに、頭の模型を上下にずらした版。
    # 頭の模型の 1.6 が頭の1ピクセル。アイテムの絵の切り替え singulo:height（高さを 0〜1 にした値）で選ぶ
    lo, hi = -4, 4
    overrides = []
    for h in range(lo, hi + 1):
        name = f'{iid}_worn' if h == 0 else f'{iid}_worn_{"up" if h > 0 else "down"}{abs(h)}'
        if h != 0:
            moved = [{**e, 'from': [e['from'][0], round(e['from'][1] + 1.6 * h, 3), e['from'][2]],
                      'to': [e['to'][0], round(e['to'][1] + 1.6 * h, 3), e['to'][2]]} for e in els]
            write_json(ASSETS / 'models' / 'item' / f'{name}.json', {
                'parent': 'minecraft:block/block', 'render_type': 'minecraft:translucent', 'textures': t, 'elements': moved})
        variant = f'{iid}_h' + ('0' if h == 0 else ('p' if h > 0 else 'm') + str(abs(h)))
        write_json(ASSETS / 'models' / 'item' / f'{variant}.json', held(name))
        overrides.append({'predicate': {f'{MODID}:height': round((h - lo) / (hi - lo), 4)}, 'model': f'{MODID}:item/{variant}'})
    write_json(ASSETS / 'models' / 'item' / f'{iid}.json', {**held(f'{iid}_worn'), 'overrides': overrides})


def compass_item_model():
    """探索コンパス・隕石コンパス: 針の向き（minecraft:angle）で16コマを切り替える。"""
    for iid in ('explorer_compass', 'meteor_compass'):
        overrides = []
        n = 16
        for k in range(n):
            overrides.append({'predicate': {'angle': max(0.0, k / n - 1 / (2 * n))},
                              'model': f'{MODID}:item/{iid}_{k:02d}'})
            write_json(ASSETS / 'models' / 'item' / f'{iid}_{k:02d}.json',
                       {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{MODID}:item/{iid}_{k:02d}'}})
        overrides.append({'predicate': {'angle': 1 - 1 / (2 * n)}, 'model': f'{MODID}:item/{iid}_00'})
        write_json(ASSETS / 'models' / 'item' / f'{iid}.json', {
            'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{MODID}:item/{iid}_00'}, 'overrides': overrides})

def facility_loot():
    """遺構・研究所の印刷台のアンドロイドと、警備機ドックのドローンが、プレイヤーに倒されたときに落とす物（高価ではないが役に立つ部品）。
    スポーンエッグなどで出した個体は落とさない（Java の facilitySpawned）。"""
    def item(name, lo, hi, weight, vanilla=False):
        e = {'type': 'minecraft:item', 'name': f'minecraft:{name}' if vanilla else mid(name), 'weight': weight}
        if hi > 1:
            e['functions'] = [{'function': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': lo, 'max': hi}}]
        return e
    killed = [{'condition': 'minecraft:killed_by_player'}]
    android = [
        {'rolls': {'type': 'minecraft:uniform', 'min': 1, 'max': 2}, 'conditions': killed, 'entries': [
            item('基礎回路', 1, 2, 4), item('鋼板', 1, 3, 4), item('copper_ingot', 2, 4, 3, True), item('redstone', 2, 5, 3, True)]},
        {'rolls': 1, 'conditions': killed, 'entries': [
            item('自己修理キット', 1, 1, 15), item('超伝導コイル', 1, 1, 8), item('クリスタルメモリ', 1, 1, 3), {'type': 'minecraft:empty', 'weight': 74}]},
    ]
    drone = [
        {'rolls': 1, 'conditions': killed, 'entries': [
            item('copper_ingot', 1, 3, 4, True), item('redstone', 1, 4, 4, True), item('iron_nugget', 2, 6, 3, True), item('基礎回路', 1, 1, 2)]},
        {'rolls': 1, 'conditions': killed, 'entries': [
            item('glowstone_dust', 1, 2, 18, True), item('自己修理キット', 1, 1, 5), {'type': 'minecraft:empty', 'weight': 77}]},
    ]
    write_json(DATA / 'loot_table' / 'entities' / 'facility_android.json', {'type': 'minecraft:entity', 'pools': android})
    write_json(DATA / 'loot_table' / 'entities' / 'facility_drone.json', {'type': 'minecraft:entity', 'pools': drone})


def mid_entity(eid):
    return f'{MODID}:{eid}'


def tags():
    t = RES / 'data'
    write_json(t / 'c' / 'tags' / 'item' / 'ingots' / 'steel.json', {'values': [mid('鋼鉄インゴット')]})
    write_json(t / 'c' / 'tags' / 'item' / 'ingots.json', {'values': [mid(n) for n in ('鋼鉄インゴット', '亜鉛インゴット',
                                                                                'ビスマスインゴット', 'ニホニウムインゴット')]})
    for n, tag in INGREDIENT_TAGS.items():
        write_json(t / tag.split(':')[0] / 'tags' / 'item' / (tag.split(':')[1] + '.json'), {'values': [mid(n)]})
    write_json(t / 'c' / 'tags' / 'item' / 'ingots' / 'nihonium.json', {'values': [mid('ニホニウムインゴット')]})
    write_json(t / 'c' / 'tags' / 'item' / 'dusts' / 'zinc.json', {'values': [mid('亜鉛の粉')]})
    write_json(t / 'c' / 'tags' / 'item' / 'plates' / 'steel.json', {'values': [mid('鋼板')]})
    write_json(t / 'c' / 'tags' / 'item' / 'plates.json', {'values': [mid('鋼板')]})
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
    write_json(DATA / 'tags' / 'item' / 'ceramic_flux.json', {'values': ['minecraft:bone_meal', mid('石灰粉')]})
    write_json(DATA / 'tags' / 'item' / 'carbon.json', {'values': ['minecraft:coal', mid('炭素粉')]})
    write_json(DATA / 'tags' / 'item' / 'limestone.json', {'values': ['minecraft:calcite', 'minecraft:dripstone_block']})
    write_json(DATA / 'tags' / 'item' / 'helium_bearing_stone.json', {'values': [
        'minecraft:deepslate', 'minecraft:cobbled_deepslate', 'minecraft:basalt', 'minecraft:smooth_basalt']})
    write_json(DATA / 'tags' / 'block' / 'ruin_blocks.json',
               {'values': [mid(n) for n, v in MOD.items() if v[2] in ('ruin_block', 'ruin_glass', 'boss_block')]})
    write_json(DATA / 'tags' / 'entity_type' / 'gravity_immune.json', {'values': [
        'minecraft:ender_dragon', 'minecraft:wither', 'minecraft:warden', 'minecraft:elder_guardian',
        'minecraft:enderman', 'minecraft:shulker', mid_entity('horizon_warden'), mid_entity('gravity_remnant')]})
    write_json(t / 'c' / 'tags' / 'entity_type' / 'bosses.json', {'values': [mid_entity(b) for b in
                                                                           ('horizon_warden', 'echo_sentinel', 'gravity_remnant',
                                                                            'foundry_overseer')]})
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
    diamond_only = ('welded_hatch',)                      # 溶接された蓋はダイヤのツルハシ以上でないと掘れない
    write_json(t / 'minecraft' / 'tags' / 'block' / 'needs_iron_tool.json',
               {'values': [mid(n) for n, v in MOD.items() if v[0] not in diamond_only and (
                   v[2] == 'block' or v[2] in ('deco', 'deco_glass', 'gravity', 'lab') or v[0] in ('meteoric_iron_chunk', 'meteorite_crust', 'meteorite_core')
                   or v[0].startswith('pristine_'))]})
    write_json(t / 'minecraft' / 'tags' / 'block' / 'needs_diamond_tool.json', {'values': [f'{MODID}:{i}' for i in diamond_only]})
    # 単純ブロックのドロップ。中身を持ち運ぶブロックはブロックエンティティのデータ成分を写す
    copy_components = {'gravitational_containment_tank': [f'{MODID}:dark_matter'],
                       'zero_gravity_controller': ['minecraft:block_entity_data'],
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
    # 掘ると別の物が出るブロック（隕鉄塊・星屑の結晶・雷ガラス塊）
    import blocks_extra4
    blocks_extra4.loot(write_json, DATA)
    import blocks_extra5
    blocks_extra5.loot(write_json, DATA)
    import blocks_extra6
    blocks_extra6.loot(write_json, DATA)
    import lab_blocks
    lab_blocks.loot(write_json, DATA)
    boss_loot()


RUIN_PLACEMENT = {
    # 遺構ID: (間隔, 最小間隔, salt, バイオーム)。間隔はチャンク（同じ遺構どうしのおおよその距離）
    'observation_post': (40, 16, 19370501, ['minecraft:plains', 'minecraft:sunflower_plains', 'minecraft:desert',
                                            'minecraft:savanna', 'minecraft:savanna_plateau', 'minecraft:snowy_plains',
                                            'minecraft:meadow', 'minecraft:badlands']),
    'research_building': (56, 20, 19370502, ['#minecraft:is_overworld']),
    'culture_facility': (64, 24, 19370503, ['#minecraft:is_overworld']),
    'final_lab': (96, 40, 19370504, ['#minecraft:is_overworld']),
    'sunken_reactor': (112, 48, 19370505, ['#minecraft:is_overworld']),
    'android_lab': (64, 24, 19370506, ['#minecraft:is_overworld']),
}
# 地下の遺構を埋める高さ（構造物の床の Y の範囲）。深い遺構ほど後の段階
RUIN_DEPTH = {
    'research_building': (-20, 4),
    'culture_facility': (-46, -30),
    'final_lab': (-56, -46),
    'sunken_reactor': (-60, -54),
    'android_lab': (-38, -26),
}


CRATER_BIOMES = ['minecraft:plains', 'minecraft:sunflower_plains', 'minecraft:desert', 'minecraft:savanna',
                 'minecraft:savanna_plateau', 'minecraft:snowy_plains', 'minecraft:meadow', 'minecraft:badlands',
                 'minecraft:eroded_badlands', 'minecraft:forest', 'minecraft:birch_forest', 'minecraft:taiga',
                 'minecraft:snowy_taiga', 'minecraft:windswept_hills', 'minecraft:stony_peaks']


def craters_data():
    """
    隕石クレーター（地表の小さな構造物）。型紙は使わず、Java（MeteorCraterPiece）がその場の地面の高さに合わせて
    すり鉢・縁・飛び散った土を描き込む（地形を四角く削らないよう、地形の調整はしない）。
    0.4.0 までの型紙（craters/meteor_crater_{a,b,c}.nbt）は、保存済みのワールドの構造が参照するので、
    src/main/resources に残してある（ここでは作らない）。
    """
    write_json(DATA / 'tags' / 'worldgen' / 'biome' / 'has_structure' / 'meteor_crater.json', {'values': CRATER_BIOMES})
    write_json(DATA / 'worldgen' / 'structure' / 'meteor_crater.json', {
        'type': f'{MODID}:meteor_crater',
        'biomes': f'#{MODID}:has_structure/meteor_crater',
        'step': 'surface_structures',
        'spawn_overrides': {},
        'terrain_adaptation': 'none',
    })
    write_json(DATA / 'worldgen' / 'structure_set' / 'meteor_crater.json', {
        'structures': [{'structure': f'{MODID}:meteor_crater', 'weight': 1}],
        'placement': {'type': 'minecraft:random_spread', 'spacing': 28, 'separation': 10, 'salt': 19370601},
    })


# 沈んだ炉心区画の保管庫の中身（1回の遠征ぶん）: (アイテム, 最小, 最大)。ほかにクリスタルメモリと、ときどきオーバークロック・チップ
SUNKEN_REACTOR_LOOT = [('エキゾチック物質', 3, 5), ('ジェット凝縮体', 2, 3), ('H凝縮体', 1, 2)]

# 地下アンドロイド研究所の保管庫の中身（1回の遠征ぶん）: (アイテム, 最小, 最大)。ほかにクリスタルメモリ
ANDROID_FACTORY_LOOT = [('精密アクチュエーター', 2, 3), ('アンドロイドキット', 2, 4), ('自己修理キット', 1, 3)]

# 番人の落とす物: 番人 → [(アイテム, 最小, 最大)]
BOSS_LOOT = {
    'echo_sentinel': [('番人の投影核', 2, 2), ('クリスタルメモリ', 1, 2)],
    'gravity_remnant': [('縮退核', 2, 2), ('高圧結晶', 2, 4)],
    'ergosphere_leviathan': [('渦の断章', 1, 1), ('エキゾチック物質', 4, 6), ('H凝縮体', 2, 3)],
    'foundry_overseer': [('精密アクチュエーター', 3, 4), ('アンドロイドキット', 1, 2)],
}


def boss_loot():
    for boss, drops in BOSS_LOOT.items():
        pools = []
        for name, lo, hi in drops:
            entry = {'type': 'minecraft:item', 'name': mid(name)}
            if hi > 1:
                entry['functions'] = [{'function': 'minecraft:set_count',
                                       'count': lo if lo == hi else {'type': 'minecraft:uniform', 'min': lo, 'max': hi}}]
            pools.append({'rolls': 1, 'entries': [entry]})
        write_json(DATA / 'loot_table' / 'entities' / f'{boss}.json', {'type': 'minecraft:entity', 'pools': pools})
    facility_loot()


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
            # 地下は周りを岩で包む（洞窟や水とつながって崩れないように）
            'terrain_adaptation': 'beard_thin' if placement == 'surface' else 'encapsulate',
            'start_pool': f'{MODID}:ruins/{rid}',
            'size': 1,
            'max_distance_from_center': 80,
            'use_expansion_hack': False,
        }
        if placement == 'surface':
            structure['start_height'] = {'absolute': 0}
            structure['project_start_to_heightmap'] = 'WORLD_SURFACE_WG'
        else:
            lo, hi = RUIN_DEPTH[rid]
            structure['start_height'] = {'type': 'minecraft:uniform',
                                         'min_inclusive': {'absolute': lo}, 'max_inclusive': {'absolute': hi}}
        write_json(DATA / 'worldgen' / 'structure' / f'{rid}.json', structure)
        write_json(DATA / 'worldgen' / 'structure_set' / f'{rid}.json', {
            'structures': [{'structure': f'{MODID}:{rid}', 'weight': 1}],
            'placement': {'type': 'minecraft:random_spread', 'spacing': spacing, 'separation': separation, 'salt': salt},
        })
    # 地下アンドロイド研究所: 部屋の型紙を Java（LabStructure）がランダムに並べる
    import lab
    lab.write_all(DATA, write_json, RUIN_PLACEMENT['android_lab'], RUIN_DEPTH['android_lab'])
    lab.chest_loot(write_json, DATA, mid)
    write_json(DATA / 'tags' / 'worldgen' / 'structure' / 'ruins.json',
               {'values': [f'{MODID}:{rid}' for rid in ruins.RUINS] + [f'{MODID}:android_lab']})
    write_json(DATA / 'tags' / 'worldgen' / 'structure' / 'gravity_anomalies.json',
               {'values': [f'{MODID}:final_lab', f'{MODID}:sunken_reactor']})

    # 中身: 遺構ごとに1つの保管庫へ、1回の遠征ぶんを入れる。一回限りのもの（特異点の種）は別表で初回だけ
    pools, first = {}, {}
    for name, info in EXPLORE.items():
        rid = ruins.RUIN_IDS[info['ruin']]
        lo, hi = info['per_run']
        pool = {'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': mid(name), 'functions': [
            {'function': 'minecraft:set_count', 'count': lo if lo == hi else
             {'type': 'minecraft:uniform', 'min': lo, 'max': hi}}]}]}
        (first if info['kind'] == 'once' else pools).setdefault(rid, []).append(pool)
    # クリスタルメモリ（旧文明の記録）。深い遺構ほど多い
    fragments = {'observation_post': (1, 2), 'research_building': (1, 2), 'culture_facility': (2, 3), 'final_lab': (2, 3),
                 'sunken_reactor': (2, 3), 'android_lab': (2, 3)}
    for name, lo, hi in ANDROID_FACTORY_LOOT:
        pools.setdefault('android_lab', []).append({'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': mid(name), 'functions': [
            {'function': 'minecraft:set_count', 'count': lo if lo == hi else {'type': 'minecraft:uniform', 'min': lo, 'max': hi}}]}]})
    # 沈んだ炉心区画（段階5）: 探索でしか手に入らない物はなく、点火後の素材をまとめて持ち帰れる
    for name, lo, hi in SUNKEN_REACTOR_LOOT:
        pools.setdefault('sunken_reactor', []).append({'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': mid(name), 'functions': [
            {'function': 'minecraft:set_count', 'count': lo if lo == hi else {'type': 'minecraft:uniform', 'min': lo, 'max': hi}}]}]})
    pools['sunken_reactor'].append({'rolls': 1, 'entries': [
        {'type': 'minecraft:item', 'name': mid('オーバークロック・チップ'), 'weight': 35}, {'type': 'minecraft:empty', 'weight': 65}]})
    for rid, (lo, hi) in fragments.items():
        pools.setdefault(rid, []).append({'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': mid('クリスタルメモリ'), 'functions': [
            {'function': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': lo, 'max': hi}}]}]})
    # 施設の建材の設計図（遺構の種類ごとに、確率で1枚）
    import blueprints
    for rid in list(pools):
        pools[rid] += blueprints.loot_pools(f'ruin:{rid}')
    for rid, plist in pools.items():
        write_json(DATA / 'loot_table' / 'ruins' / f'{rid}.json', {'type': 'minecraft:chest', 'pools': plist})
    for rid, plist in first.items():
        write_json(DATA / 'loot_table' / 'ruins' / f'{rid}_first.json', {'type': 'minecraft:chest', 'pools': plist})
    sealed_loot()
    write_json(DATA / 'tags' / 'block' / 'scanner_tier_2.json', {'values': [
        {'id': f'#c:ores/{n}', 'required': False} for n in ('gold', 'diamond', 'emerald', 'silver', 'platinum')]})
    write_json(DATA / 'tags' / 'block' / 'scanner_tier_3.json', {'values': [
        {'id': f'#c:ores/{n}', 'required': False} for n in ('netherite_scrap', 'uranium', 'iridium')]})


# 封印コンテナの中身: 段階ごとに (必ず入るもの, ばらつくもの（2回引く）, 珍しいもの, 珍しいものが出る確率, 照明の色)
SEALED_LOOT = {
    1: ([('クリスタルメモリ', 2, 3)], [('鋼鉄インゴット', 4, 8), ('基礎回路', 2, 4), ('熱電対モジュール', 2, 3), ('鋼板', 3, 6)],
        'オーバークロック・チップ', 0.35, '遺構の照明（琥珀）'),
    2: ([('冷却原子', 4, 4)], [('超伝導線材', 2, 4), ('ミュオン束', 1, 2), ('超伝導コイル', 1, 2), ('クリスタルメモリ', 1, 2)],
        '重力ブーツ', 0.35, '遺構の照明（翠）'),
    3: ([('エキゾチック物質', 2, 2)], [('圧縮ブロックLv2', 1, 2), ('縮退物質殻', 1, 1), ('超伝導コイル', 2, 4), ('クリスタルメモリ', 1, 3)],
        '触媒安定化剤', 0.35, '遺構の照明（菫）'),
    4: ([('封印記録', 1, 1)], [('ジェット凝縮体', 1, 2), ('H凝縮体', 1, 1), ('エキゾチック物質', 2, 4), ('クリスタルメモリ', 2, 3)],
        '次元ポケット', 0.35, '遺構の照明（紅）'),
}


def sealed_loot():
    """loot_table/sealed/tier_N: 遺構の封印コンテナの中身。"""
    def entry(name, lo, hi, weight=1):
        e = {'type': 'minecraft:item', 'name': mid(name), 'weight': weight}
        if hi > 1:
            e['functions'] = [{'function': 'minecraft:set_count',
                               'count': lo if lo == hi else {'type': 'minecraft:uniform', 'min': lo, 'max': hi}}]
        return e
    for tier, (fixed, varied, rare, chance, lamp) in SEALED_LOOT.items():
        pools = [{'rolls': 1, 'entries': [entry(n, lo, hi)]} for n, lo, hi in fixed]
        pools.append({'rolls': 2, 'entries': [entry(n, lo, hi) for n, lo, hi in varied]})
        pools.append({'rolls': 1, 'entries': [entry(rare, 1, 1, round(chance * 100)),
                                              {'type': 'minecraft:empty', 'weight': round((1 - chance) * 100)}]})
        pools.append({'rolls': 1, 'entries': [entry(lamp, 2, 4)]})
        write_json(DATA / 'loot_table' / 'sealed' / f'tier_{tier}.json', {'type': 'minecraft:chest', 'pools': pools})


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


def mass_synthesis():
    """創世炉の質量生成の表: (アイテムかタグ, 質量, 希少度 0〜4)。1個の電力 = 質量 × genesisBaseCost × 10^希少度。
    質量は mass_values と同じ尺度（石1ブロック = 1）。Singulo の物（圧縮ブロック・質量ペレットなど）は載せない。
    生成した物を炉に戻して得をしないよう、質量は mass_values の値以上にする（作物は #c:crops で一括、品目ごとに下げない）。
    加工すると質量値が増える物（原木→板材4枚、かぼちゃ→種4粒）は、加工したあとの質量にする。
    GameTest synthesizedItemsNeverFeedAProfit が、登録されたすべてのレシピをたどって確かめる。
    c:ingots・c:plates には Singulo の鋼が入るので、金属は種類ごとのタグで指定する。"""
    entries = [
        # 0: ありふれた物（石・土・木・植物・食べ物・色）
        ('#c:stones', 1, 0), ('#c:cobblestones', 1, 0), ('#minecraft:stone_bricks', 1, 0), ('#c:sandstone/blocks', 1, 0),
        ('#minecraft:dirt', 0.5, 0), ('#c:sands', 0.5, 0), ('#c:gravels', 0.5, 0), ('#c:netherracks', 1, 0),
        ('#c:obsidians', 1.2, 0), ('#minecraft:terracotta', 1, 0), ('#c:concretes', 1, 0), ('#c:concrete_powders', 0.5, 0),
        ('#c:glass_blocks', 0.5, 0), ('#c:glass_panes', 0.2, 0),
        ('minecraft:end_stone', 1, 0), ('minecraft:deepslate', 1.2, 0), ('minecraft:cobbled_deepslate', 1.2, 0),
        ('minecraft:tuff', 1, 0), ('minecraft:calcite', 1, 0), ('minecraft:andesite', 1, 0), ('minecraft:diorite', 1, 0),
        ('minecraft:granite', 1, 0), ('minecraft:basalt', 1.2, 0), ('minecraft:smooth_basalt', 1.2, 0),
        ('minecraft:blackstone', 1.2, 0), ('minecraft:dripstone_block', 1, 0), ('minecraft:pointed_dripstone', 0.25, 0),
        ('minecraft:magma_block', 1, 0), ('minecraft:soul_sand', 0.5, 0), ('minecraft:soul_soil', 0.5, 0),
        ('minecraft:clay', 0.5, 0), ('minecraft:clay_ball', 0.1, 0), ('minecraft:mud', 0.5, 0), ('minecraft:brick', 0.1, 0),
        ('minecraft:snow_block', 0.5, 0), ('minecraft:snowball', 0.1, 0), ('minecraft:ice', 0.5, 0), ('minecraft:packed_ice', 1, 0),
        ('minecraft:moss_block', 0.5, 0), ('minecraft:flint', 0.1, 0),
        ('#minecraft:logs', 1, 0), ('#minecraft:planks', 0.25, 0), ('minecraft:pumpkin', 1, 0), ('#minecraft:leaves', 0.25, 0),
        ('#minecraft:saplings', 0.25, 0), ('#minecraft:flowers', 0.25, 0), ('#c:mushrooms', 0.25, 0),
        ('#c:crops', 0.25, 0), ('#c:seeds', 0.25, 0), ('#minecraft:wool', 0.25, 0), ('#minecraft:wool_carpets', 0.1, 0),
        ('#c:dyes', 0.1, 0), ('minecraft:stick', 0.1, 0), ('minecraft:bamboo', 0.25, 0), ('minecraft:kelp', 0.25, 0),
        ('minecraft:vine', 0.1, 0), ('minecraft:lily_pad', 0.1, 0), ('minecraft:glow_lichen', 0.1, 0),
        ('minecraft:paper', 0.1, 0), ('minecraft:sugar', 0.1, 0), ('minecraft:apple', 0.25, 0),
        ('minecraft:sweet_berries', 0.25, 0), ('minecraft:glow_berries', 0.25, 0),
        ('minecraft:beef', 0.25, 0), ('minecraft:porkchop', 0.25, 0), ('minecraft:chicken', 0.25, 0),
        ('minecraft:mutton', 0.25, 0), ('minecraft:rabbit', 0.25, 0), ('minecraft:cod', 0.25, 0), ('minecraft:salmon', 0.25, 0),
        ('minecraft:egg', 0.1, 0), ('minecraft:feather', 0.1, 0), ('minecraft:ink_sac', 0.1, 0),
        ('minecraft:rotten_flesh', 0.1, 0),
        # 1: 普通の資源（金属・鉱石・燃料・魔法の粉・モブの落とし物）
        ('#c:ingots/iron', 3 / 9, 1), ('#c:ingots/copper', 3 / 9, 1), ('#c:ingots/gold', 7 / 9, 1),
        ('#c:storage_blocks/iron', 3, 1), ('#c:storage_blocks/copper', 3, 1), ('#c:storage_blocks/gold', 7, 1),
        ('minecraft:iron_nugget', 3 / 81, 1), ('minecraft:gold_nugget', 7 / 81, 1),
        ('minecraft:raw_iron', 3 / 9, 1), ('minecraft:raw_copper', 3 / 9, 1), ('minecraft:raw_gold', 7 / 9, 1),
        ('minecraft:raw_iron_block', 3, 1), ('minecraft:raw_copper_block', 3, 1), ('minecraft:raw_gold_block', 7, 1),
        ('minecraft:coal_ore', 1, 1), ('minecraft:deepslate_coal_ore', 1.2, 1), ('minecraft:iron_ore', 1, 1),
        ('minecraft:deepslate_iron_ore', 1.2, 1), ('minecraft:copper_ore', 1, 1), ('minecraft:deepslate_copper_ore', 1.2, 1),
        ('minecraft:gold_ore', 1, 1), ('minecraft:deepslate_gold_ore', 1.2, 1), ('minecraft:redstone_ore', 1, 1),
        ('minecraft:deepslate_redstone_ore', 1.2, 1), ('minecraft:lapis_ore', 1, 1), ('minecraft:deepslate_lapis_ore', 1.2, 1),
        ('minecraft:nether_quartz_ore', 1, 1), ('minecraft:nether_gold_ore', 1, 1),
        ('minecraft:coal', 0.2, 1), ('minecraft:charcoal', 0.2, 1), ('minecraft:coal_block', 1.8, 1),
        ('minecraft:redstone', 0.1, 1), ('minecraft:redstone_block', 0.9, 1),
        ('minecraft:lapis_lazuli', 0.1, 1), ('minecraft:lapis_block', 0.9, 1),
        ('minecraft:quartz', 0.2, 1), ('minecraft:quartz_block', 0.8, 1),
        ('minecraft:glowstone_dust', 0.1, 1), ('minecraft:glowstone', 0.4, 1),
        ('minecraft:amethyst_shard', 0.2, 1), ('minecraft:amethyst_block', 0.8, 1),
        ('minecraft:slime_ball', 0.1, 1), ('minecraft:slime_block', 0.9, 1), ('minecraft:honeycomb', 0.1, 1),
        ('minecraft:magma_cream', 0.1, 1), ('minecraft:glow_ink_sac', 0.1, 1), ('minecraft:blue_ice', 2, 1),
        ('minecraft:gunpowder', 0.1, 1), ('minecraft:string', 0.05, 1), ('minecraft:bone', 0.1, 1),
        ('minecraft:bone_meal', 0.05, 1), ('minecraft:bone_block', 0.9, 1), ('minecraft:leather', 0.1, 1),
        ('minecraft:rabbit_hide', 0.05, 1), ('minecraft:spider_eye', 0.1, 1), ('minecraft:armadillo_scute', 0.1, 1),
        ('minecraft:chorus_fruit', 0.1, 1), ('minecraft:sculk', 0.5, 1),
        # 2: 貴重な物（宝石・エンダー・ネザーの素材・宝の品）
        ('minecraft:diamond', 1, 2), ('minecraft:diamond_block', 9, 2), ('minecraft:diamond_ore', 1, 2),
        ('minecraft:deepslate_diamond_ore', 1.2, 2), ('minecraft:emerald', 1, 2), ('minecraft:emerald_block', 9, 2),
        ('minecraft:emerald_ore', 1, 2), ('minecraft:deepslate_emerald_ore', 1.2, 2),
        ('minecraft:ender_pearl', 0.5, 2), ('minecraft:ender_eye', 0.6, 2), ('minecraft:blaze_rod', 0.3, 2),
        ('minecraft:blaze_powder', 0.15, 2), ('minecraft:breeze_rod', 0.3, 2), ('minecraft:ghast_tear', 0.2, 2),
        ('minecraft:phantom_membrane', 0.2, 2), ('minecraft:prismarine_shard', 0.2, 2),
        ('minecraft:prismarine_crystals', 0.2, 2), ('minecraft:sponge', 0.5, 2), ('minecraft:nautilus_shell', 0.3, 2),
        ('minecraft:turtle_scute', 0.2, 2), ('minecraft:rabbit_foot', 0.1, 2), ('minecraft:goat_horn', 0.2, 2),
        ('minecraft:golden_apple', 0.8, 2), ('minecraft:experience_bottle', 0.2, 2), ('minecraft:name_tag', 0.2, 2),
        ('minecraft:saddle', 0.5, 2), ('minecraft:trial_key', 0.2, 2), ('minecraft:sculk_catalyst', 1, 2),
        ('minecraft:sculk_sensor', 0.5, 2), ('minecraft:sculk_shrieker', 1, 2),
        ('minecraft:zombie_head', 0.5, 2), ('minecraft:skeleton_skull', 0.5, 2), ('minecraft:creeper_head', 0.5, 2),
        ('minecraft:piglin_head', 0.5, 2), ('#c:music_discs', 0.2, 2), ('#minecraft:trim_templates', 0.5, 2),
        # 3: 稀少な物（ネザライト・深層・海の秘宝）
        ('minecraft:netherite_scrap', 0.6, 3), ('minecraft:netherite_ingot', 20 / 9, 3), ('minecraft:netherite_block', 20, 3),
        ('minecraft:ancient_debris', 2, 3), ('minecraft:netherite_upgrade_smithing_template', 0.5, 3),
        ('minecraft:echo_shard', 0.3, 3), ('minecraft:shulker_shell', 0.5, 3), ('minecraft:heart_of_the_sea', 1, 3),
        ('minecraft:conduit', 1, 3), ('minecraft:trident', 3, 3), ('minecraft:totem_of_undying', 1, 3),
        ('minecraft:wither_skeleton_skull', 0.5, 3), ('minecraft:sniffer_egg', 1, 3), ('minecraft:ominous_trial_key', 0.3, 3),
        ('minecraft:dragon_breath', 0.5, 3), ('minecraft:enchanted_golden_apple', 3, 3),
        # 4: 伝説の物
        ('minecraft:nether_star', 5, 4), ('minecraft:beacon', 6, 4), ('minecraft:heavy_core', 4, 4),
        ('minecraft:dragon_head', 2, 4), ('minecraft:elytra', 3, 4), ('minecraft:dragon_egg', 10, 4),
    ]
    out = []
    for k, m, tier in entries:
        e = {'tag': k[1:]} if k.startswith('#') else {'item': k}
        e['mass'] = round(m, 4)
        e['tier'] = tier
        out.append(e)
    write_json(DATA / 'mass_synthesis' / 'default.json', {'entries': out})


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


# ---------------------------------------------------------------- クリエイティブタブ（と JEI）の並び
# 用途ごとにまとめ、まとまりの中は段階順・レシピ順。並べ忘れたアイテムは「素材」の最後に入る。
TAB_GROUPS = [
    ('案内と設定', ['handbook', 'holo_projector', 'explorer_compass', 'meteor_compass', 'settings_card', 'relocation_wrench', 'decoded_record']),
    ('発電・蓄電・送電', ['thermoelectric_generator', 'hydrogen_fuel_cell', 'muon_fusion_reactor', 'cryogenic_turbine', 'quantum_heat_engine', 'smes_cell', 'smes_module',
                       'copper_wire', 'superconducting_cable', 'topological_wire', 'horizon_bus']),
    ('加工装置', ['kiln', 'compressor', 'electrolyzer', 'peltier_cooler', 'archive_terminal', 'catalytic_reactor', 'precision_assembler',
               'cosmic_muon_collector', 'entanglement_synthesizer', 'laser_cooler', 'echo_resonator', 'time_crystal_incubator',
               'singularity_encapsulator', 'monopole_upgrade', 'overclock_chip']),
    # マルチブロック: 共通のポートのあと、形ごとにコントローラとその部品
    ('マルチブロック', ['multiblock_port',
                   'cooling_tower_controller', 'cooling_tower_base', 'cooling_tower_casing', 'cooling_tower_glass',
                   'cooling_tower_coolant_band', 'cooling_tower_rim', 'cooling_tower_grate', 'heat_exchange_core',
                   'accelerator_controller', 'accelerator_tube', 'focusing_magnet',
                   'degenerate_compactor_controller', 'degenerate_compactor_frame', 'degenerate_compactor_plate',
                   'degenerate_compactor_ram', 'degenerate_compactor_anvil', 'degenerate_compactor_vent',
                   'degenerate_compactor_window',
                   'casimir_cavity_controller', 'casimir_cavity_frame', 'casimir_cavity_pump', 'casimir_cavity_wall',
                   'casimir_cavity_shield', 'casimir_cavity_window', 'mirror_plate',
                   'degenerate_furnace_controller', 'degenerate_furnace_frame', 'degenerate_furnace_shell',
                   'degenerate_furnace_piston', 'degenerate_furnace_fin', 'degenerate_furnace_tube', 'degenerate_furnace_window',
                   'core_controller', 'reactor_shell', 'gyro_drive', 'reactor_stabilizer', 'extraction_port', 'reactor_mass_alarm',
                   'shield_tower_core', 'shield_tower_plinth', 'shield_tower_coil', 'shield_tower_body', 'shield_tower_waveguide',
                   'shield_tower_crown',
                   'tipler_core', 'tipler_frame', 'tipler_housing', 'tipler_bearing', 'tipler_window', 'tipler_holder',
                   'wormhole_generator_core', 'wormhole_generator_shell', 'wormhole_generator_coil', 'wormhole_generator_focuser',
                   'wormhole_generator_window', 'wormhole_generator_port',
                   'genesis_core', 'genesis_frame', 'genesis_pylon', 'primordial_seed']),
    ('時空と探査の装置', ['worldline_anchor_small', 'inertial_stabilizer', 'gravity_panel_receiver', 'impact_generator_panel', 'keraunos_tower', 'deep_sea_collector', 'void_collector',
                     'low_gravity_panel',
                     'high_gravity_panel', 'zero_gravity_block', 'zero_gravity_controller', 'gravitational_wave_detector', 'neutrino_observatory',
                     'probe_station', 'worldline_anchor_advanced', 'halo_collector', 'gravitational_containment_tank',
                     'unstable_wormhole_mouth', 'wormhole_stabilizer', 'wormhole_mouth_casing', 'wormhole_mouth', 'wormhole_port']),
    ('道具と装備', ['fluid_canister', 'neutrino_scanner', 'scanner_module_2', 'scanner_module_3', 'inertial_control_gauntlet', 'magnetic_bottle', 'shield_permit',
               'black_hole_bomb', 'graviton_manipulator', 'metric_drive', 'gravity_boots', 'dimensional_pocket']),
    # アンドロイド: 本体と道具 → 設計メモリ → アップグレード → 作業 → 装飾 → スポーンエッグ
    ('アンドロイド', ['android_station', 'android_kit', 'android_charger', 'android_repair_kit', 'android_armor_plate',
                 'android_memory_blade', 'android_memory_whip', 'android_memory_saber', 'android_memory_axe', 'android_memory_bit',
                 'android_memory_gunner',
                 'android_production_upgrade', 'android_factory_print_unit', 'android_transfer_upgrade', 'android_battery_upgrade',
                 'android_backpack_upgrade', 'android_reinforced_backpack_upgrade', 'android_large_backpack_upgrade', 'android_tank_upgrade', 'android_gas_tank_upgrade', 'android_dual_tank_upgrade',
                 'android_mining_upgrade', 'android_felling_upgrade',
                 'android_work_console', 'android_supply_port', 'android_delivery_port', 'android_recipe_rack', 'android_workbench',
                 'advanced_android_workbench', 'quantum_android_workbench',
                 'work_location_card', 'area_card', 'survey_glasses', 'recipe_card', 'procedure_card',
                 'android_ribbon', 'android_hair_flower', 'android_beret', 'android_headdress', 'android_glasses', 'android_cape', 'android_scarf',
                 'android_waist_ribbon', 'android_book', 'android_bouquet', 'unfired_teacup', 'android_teacup', 'android_umbrella',
                 'blade_android_spawn_egg', 'whip_android_spawn_egg', 'saber_android_spawn_egg', 'axe_android_spawn_egg',
                 'bit_android_spawn_egg', 'gunner_android_spawn_egg']),
    ('封印コンテナ', ['sealed_container_1', 'magnetic_key', 'sealed_container_2', 'quantum_key', 'sealed_container_3',
                 'temporal_key', 'sealed_container_4', 'singularity_key']),
    ('触媒', ['muon_catalyst', 'bose_condensate_catalyst', 'time_crystal_catalyst', 'singularity_core', 'catalyst_stabilizer']),
    ('採集', ['meteorite_crust', 'meteoric_iron_chunk', 'stardust_cluster', 'meteorite_core', 'raw_meteoric_iron', 'stardust',
            'fulgurite_block', 'fulgurite', 'pressure_crystal', 'void_dust']),
    # インゴットは段階順にまとめて、素材の手前に
    ('インゴット', ['steel_ingot', 'zinc_ingot', 'bismuth_ingot', 'meteoric_iron_ingot', 'nihonium_ingot']),
    # 板と基板（板 → 基板 → 回路とモジュール。それぞれ段階順）
    ('板・基板', ['steel_plate', 'nihonium_plate', 'phase_sync_plate', 'ceramic_substrate', 'optical_lattice_substrate',
               'basic_circuit', 'quantum_computing_module', 'thermocouple_module', 'time_crystal_module']),
    ('素材', None),
    ('回収物と記録', None),
    # ボスの戦利品: 番人・遺構のボスの落とす物 → 記録の断章と終焉の記録 → 記録者の報酬 → 覚醒戦の核
    ('ボスの戦利品', ['sentinel_core', 'degenerate_nucleus', 'precision_actuator', 'echo_chapter', 'gravity_chapter', 'horizon_chapter',
                 'ergosphere_chapter', 'terminus_record', 'archivist_halo', 'afterglow_wings', 'awakening_record_core',
                 'awakening_echo_core', 'awakening_gravity_core', 'awakening_foundry_core', 'awakening_ergosphere_core']),
    ('建材', ['white_panel', 'white_light_panel', 'bismuth_crystal_block', 'glowing_bismuth_crystal', 'white_glass_panel', 'white_star_glass_panel', 'white_sliding_door', 'black_reinforced_panel',
            'black_reinforced_glass', 'black_star_glass', 'android_barrier_glass', 'android_barrier_glass_patterned',
            'android_gate_glass', 'android_gate_glass_patterned']),
    ('遺構', ['ruin_panel', 'cracked_ruin_panel', 'mossy_ruin_panel', 'tiled_ruin_panel', 'vented_ruin_panel',
            'striped_ruin_panel', 'scorched_ruin_panel', 'pristine_ruin_panel', 'pristine_ruin_tiles', 'pristine_ruin_pillar',
            'pristine_ruin_light', 'intact_ruin_glass', 'reinforced_ruin_glass', 'ruin_glass', 'ruin_lamp', 'ruin_lamp_amber',
            'ruin_lamp_verdant', 'ruin_lamp_violet', 'ruin_lamp_crimson', 'ruin_cache', 'ruin_guard_dock', 'ruin_android_fabricator',
            'seal_console', 'guardian_core', 'echo_projector', 'security_drone_spawn_egg']),
    # 地下アンドロイド研究所: 建材 → 照明 → 装置 → 小物 → 製造ライン
    ('研究所', ['lab_panel', 'lab_panel_lined', 'lab_tiles', 'lab_frame', 'lab_glass', 'pressure_hull', 'welded_hatch', 'bulkhead', 'lab_door',
              'facility_light', 'emergency_light', 'room_light', 'lab_rayleigh_panel', 'rayleigh_panel', 'power_conduit', 'reactor_core', 'reactor_console', 'server_rack', 'lab_terminal',
              'android_capsule', 'desk_monitor', 'coffee_maker', 'vending_machine', 'canned_soda', 'sports_drink', 'energy_drink',
              'coffee', 'paper_stack', 'trash_bin',
              'factory_line', 'factory_conveyor']),
    # 施設の建材の設計図（遺構 → 研究所）
    ('設計図', ['blueprint_ruin_panels', 'blueprint_pristine_ruin', 'blueprint_ruin_glass', 'blueprint_lighting',
             'blueprint_lab_building', 'blueprint_lab_furniture', 'blueprint_rayleigh']),
    ('クリエイティブ専用', ['creative_energy_source', 'creative_catalyst', 'builder_wand', 'horizon_warden_spawn_egg',
                     'echo_sentinel_spawn_egg', 'gravity_remnant_spawn_egg', 'ergosphere_leviathan_spawn_egg',
                     'last_archivist_spawn_egg', 'foundry_overseer_spawn_egg']),
]


def tab_order():
    """クリエイティブタブの並び（アイテムIDの列）。素材はレシピの順（段階順）、回収物は回収物・復元品・データカード・失活触媒。"""
    placed = {iid for _, ids in TAB_GROUPS if ids for iid in ids}
    salvage_names = list(EXPLORE) + [n for n in MOD if MOD[n][0] in ('record_fragment', 'sealed_record')] + list(RESTORED) + [n for n in MOD if n.startswith('データカード') or n == '白紙データカード'] \
        + list(SPENT.values())
    salvage = []
    for n in salvage_names:
        iid = MOD[n][0] if n in MOD else None
        if iid and iid not in placed and iid not in salvage:
            salvage.append(iid)
    placed |= set(salvage)
    materials = []
    names = [n for n in ORDER if n in MOD] + sorted((n for n in MOD if n not in RECIPES), key=stage_of)
    # レシピのないもの（加速器の事故でできるストレンジ物質など）は、手に入る段階に置く
    stage_hint = {'strange_matter': 2, 'strangelet': 2}
    for n in sorted(names, key=lambda n: (stage_hint.get(MOD[n][0], stage_of(n)), names.index(n))):
        iid, _, kind = MOD[n]
        if kind in ('fluid', 'structure') or iid in placed or iid in materials:
            continue
        materials.append(iid)
    out = []
    for title, ids in TAB_GROUPS:
        out += ids if ids else materials if title == '素材' else salvage
    return out


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
    lines.append('    /** クリエイティブタブ（と JEI）の並び。用途ごとのまとまり → 段階順。ここにないものは最後。 */')
    lines.append('    public static final List<String> TAB_ORDER = List.of(')
    lines.append(',\n'.join(f'            "{i}"' for i in tab_order()))
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
    from records import HIDDEN_RECORDS
    lines.append('    /** 封印された記録の ID（封印記録を読む順）。 */')
    lines.append('    public static final List<String> HIDDEN_RECORDS = List.of(' + ', '.join(f'"{r[0]}"' for r in HIDDEN_RECORDS) + ');')
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
    machines = [(iid, stage_of(n)) for n, (iid, _, kind) in MOD.items()
                if kind in ('machine', 'part_block', 'part_glass', 'container', 'gravity', 'deco', 'deco_glass', 'nature')]
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
        'shellNitrogenMb': r['縮退物質殻']['inputs']['液体窒素'],
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
SEE_THROUGH_BLOCK_TEXTURES = ('machine_glass', 'ruin_glass', 'cooling_tower_glass', 'degenerate_furnace_window', 'boost_overlay', 'arena_hologram',
                              'android_barrier_glass_item', 'android_barrier_glass_edge', 'android_gate_glass_item',
                              'android_gate_glass_edge',
                              'lab_glass', 'android_capsule_glass')   # 研究所のガラス・素体カプセルの筒


GLASS_PARTS = {iid for _, (iid, _, kind) in MOD.items() if kind in ('part_glass', 'ruin_glass', 'deco_glass')}


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
            if png.stem in SEE_THROUGH_BLOCK_TEXTURES or png.stem in GLASS_PARTS or png.stem.endswith('_glow'):
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
    accessory_item_models()          # 生成の一般の物のモデルのあとに、2枚重ねで書き直す
    umbrella_item_model()            # 傘は、三人称で手に持つと開いた傘の立体に
    fluid_canister_item_model()
    work_card_item_model()           # 生成の一般の物のモデルのあとに、記録済みの絵へ切り替えるモデルを書き直す
    area_card_item_model()
    glasses_item_model()
    opaque_block_textures()
    ghost_textures()
    rogue_black_hole_assets()
    alarm_assets()
    sealed_container_assets()
    tags()
    mass_values()
    mass_synthesis()
    thermal()
    ruins_data()
    craters_data()
    java_content()
    java_config()
    # Sodium ignores the vanilla include and uses our dedicated terrain shader integration.
    # Acknowledge only this known-compatible override; keep warnings for other shader files.
    write_json(RES / 'pack.mcmeta', {
        'pack': {'description': 'Singulo generated resources', 'pack_format': 34},
        'sodium': {'ignored_shaders': ['light.glsl']},
    })
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
