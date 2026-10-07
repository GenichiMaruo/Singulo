# -*- coding: utf-8 -*-
"""レシピを原料まで展開し、段階ごとの重さを検証する。"""
import math, json
import sys
from pathlib import Path
HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))  # tools/recipes.py, tools/config_spec.py
from fractions import Fraction as F
from collections import defaultdict
from recipes import (RECIPES, EXPLORE, MILESTONES, STAGE_NAMES, RUIN_REGEN_DAYS, GEN_CAPACITY,
                     REPAIR, MAX_REPAIRS, LIFE_MULT, RESTORED, POST_IGNITION, STAR_SUBST)

FLUIDS = {'水', '水素', '液体窒素', 'ヘリウム', '液体ヘリウム', 'アクシオン凝縮体'}

# 採集の手間（分/個）。中盤の簡単な自動化がある前提の目安。質量値は丸石1個ぶん。
EFFORT = {
    '鉄インゴット': 0.02, '石炭': 0.01, '銅インゴット': 0.01, 'レッドストーン': 0.01, '粘土玉': 0.02,
    'ネザー水晶': 0.02, '骨粉': 0.01, '砂': 0.005, 'ガラス': 0.01, 'ダイヤモンド': 0.5,
    'アメジストの欠片': 0.03, '残響の欠片（型）': 3.0, 'スカルク': 0.01, 'スカルクセンサー': 0.5, 'エンダーパール': 0.1, 'ネザースター': 10.0,
    '黒曜石': 0.05, '金属質量': 0.04, '青氷': 0.05, '深層岩': 0.002, '質量値': 0.002, 'グロウストーンダスト': 0.02,
    'ファントムの皮膜': 0.3, 'スライムボール': 0.05, 'ピストン': 0.05, 'バケツ': 0.06, '溶鉱炉': 0.2,
    'コンパス': 0.1, '時計': 0.1, 'エンダーアイ': 0.15, '水': 0.0, '磁気単極子': 0.0,
}
VISIT_MIN = {'地表観測拠点': 15, '研究棟': 25, '封鎖培養施設': 40, '最終実験施設': 60}


class Acc:
    def __init__(self):
        self.raw = defaultdict(F)       # バニラ素材
        self.explore = defaultdict(F)   # 遺構回収物（個数換算）
        self.fe = F(0)                  # 加工に使う総電力 FE
        self.station_s = defaultdict(F) # 装置ごとの延べ処理秒数
        self.crafts = defaultdict(F)    # レシピごとの実行回数

    def add(self, other, k=1):
        for d1, d2 in ((self.raw, other.raw), (self.explore, other.explore),
                       (self.station_s, other.station_s), (self.crafts, other.crafts)):
            for a, v in d2.items():
                d1[a] += v * k
        self.fe += other.fe * k


_memo = {}
_memo_guard = set()
_subst = {}  # 有効な代替（点火後は STAR_SUBST）

def expand(item):
    """item 1単位（流体は1 mB）を作るのに要るもの。"""
    item = _subst.get(item, item)
    key = (item, bool(_subst))
    if key in _memo:
        return _memo[key]
    acc = Acc()
    if item in RESTORED:
        # 復元品1個（新品の使用回数ぶん）。元の回収物を初回復元＋再復元3回で使い切る
        src = RESTORED[item]
        rep = REPAIR[src]
        acc.explore[src] += 1 / LIFE_MULT
        k = F(1 + MAX_REPAIRS) / LIFE_MULT
        acc.fe += F(rep['fe']) * 20 * rep['t'] * k
        acc.station_s[rep['station']] += F(rep['t']) * k
        for inp, q in rep['inputs'].items():
            acc.add(expand(inp), F(q) * k)
    elif item in EXPLORE:
        if item in REPAIR:
            # 新品1個を修復しながら使い切る：寿命は LIFE_MULT 倍、その間に MAX_REPAIRS 回修復
            acc.explore[item] += 1 / LIFE_MULT
            rep = REPAIR[item]
            k = F(MAX_REPAIRS) / LIFE_MULT
            acc.fe += F(rep['fe']) * 20 * rep['t'] * k
            acc.station_s[rep['station']] += F(rep['t']) * k
            for inp, q in rep['inputs'].items():
                _memo_guard.add(item)
                acc.add(expand(inp), F(q) * k)
                _memo_guard.discard(item)
        else:
            acc.explore[item] += 1
    elif item not in RECIPES:
        acc.raw[item] += 1
    else:
        r = RECIPES[item]
        per = F(1, r['out'])
        acc.crafts[item] += per
        acc.fe += F(r['fe']) * 20 * r['t'] * per
        if r['t']:
            acc.station_s[r['station']] += F(r['t']) * per
        for inp, q in r['inputs'].items():
            sub = expand(inp)
            acc.add(sub, F(q) * per)
    _memo[key] = acc
    return acc


def expand_set(targets, post=False):
    acc = Acc()
    for it, q in targets.items():
        if post and it in POST_IGNITION:
            _subst.update(STAR_SUBST)
            acc.add(expand(it), q)
            _subst.clear()
        else:
            acc.add(expand(it), q)
    return acc


def expand_post(item):
    _subst.update(STAR_SUBST)
    r = expand(item)
    _subst.clear()
    return r


def explore_items_needed(acc):
    """データ系は使用回数→個数。"""
    out = {}
    for it, v in acc.explore.items():
        out[it] = float(v)  # すでにレシピ側で uses を分数にしているので個数換算済み
    return out


def visits(acc, automated_stage=None):
    need = defaultdict(float)
    for it, v in acc.explore.items():
        info = EXPLORE[it]
        if automated_stage and info['auto_stage'] and info['auto_stage'] <= automated_stage:
            continue
        avg = sum(info['per_run']) / 2
        need[info['ruin']] = max(need[info['ruin']], float(v) / avg)
    return {r: max(1, math.ceil(x)) for r, x in need.items() if x > 0}


def effort_minutes(acc, visit_map):
    m = sum(float(v) * EFFORT.get(k, 0.05) for k, v in acc.raw.items() if k not in FLUIDS or k == '水')
    m += sum(VISIT_MIN[r] * n for r, n in visit_map.items())
    return m


results = {}
cum = Acc()
cum_visits = defaultdict(int)
for s in range(1, 6):
    acc = expand_set(MILESTONES[s], post=(s == 5))
    # 段階4完成で観測拠点、段階5完成で研究棟の回収物を探査機が自動回収（N−2ルール）
    v = visits(acc, automated_stage=s - 1 if s >= 5 else None)
    cum.add(acc)
    for r, n in v.items():
        cum_visits[r] += n
    machine_h = float(sum(acc.station_s.values())) / 3600
    results[s] = dict(
        raw={k: round(float(x), 1) for k, x in sorted(acc.raw.items(), key=lambda kv: -float(kv[1]) * EFFORT.get(kv[0], 0.05))},
        explore={k: round(float(x), 2) for k, x in acc.explore.items()},
        visits=v,
        fe=float(acc.fe),
        machine_h=machine_h,
        station_h={k: round(float(x) / 3600, 2) for k, x in acc.station_s.items()},
        effort_min=effort_minutes(acc, v),
    )

# シンギュラリティ・コア1個（継続生産時、炉はある前提）の限界費用
core = expand_post('シンギュラリティ・コア')
core_res = dict(raw={k: round(float(x), 2) for k, x in core.raw.items()},
                explore={k: round(float(x), 3) for k, x in core.explore.items()},
                fe=float(core.fe), machine_h=float(sum(core.station_s.values())) / 3600)

# 段階ごとの触媒1個の限界費用
cat_res = {}
for c in ['ミュオン触媒', 'BE凝縮触媒', '時間結晶触媒', 'シンギュラリティ・コア']:
    a = expand_post(c) if c == 'シンギュラリティ・コア' else expand(c)
    cat_res[c] = dict(fe=float(a.fe), machine_min=float(sum(a.station_s.values())) / 60,
                      effort=effort_minutes(a, {}),
                      explore={k: round(float(x), 3) for k, x in a.explore.items()},
                      raw_top={k: round(float(x), 2) for k, x in sorted(a.raw.items(), key=lambda kv: -float(kv[1]))[:8]})

# 遺構の持続性：段階ごとの触媒を1台ぶん回し続けるのに要る遺構周回（現実時間1時間あたり）
LIFE_MIN = {'ミュオン触媒': 20, 'BE凝縮触媒': 30, '時間結晶触媒': 45, 'シンギュラリティ・コア': 60}
sustain = {}
for c, life in LIFE_MIN.items():
    per_h = 60 / life
    a = expand_post(c) if c == 'シンギュラリティ・コア' else expand(c)
    d = {}
    for it, v in a.explore.items():
        info = EXPLORE[it]
        avg = sum(info['per_run']) / 2
        d[it] = dict(items_per_h=round(float(v) * per_h, 3), runs_per_h=round(float(v) * per_h / avg, 3),
                     ruin=info['ruin'])
    sustain[c] = d

# Pリアクター本体の質量（丸石換算）
reactor = expand('Pリアクター')
penrose_mass = float(reactor.raw.get('質量値', 0))
penrose_metal = float(reactor.raw.get('金属質量', 0))

out = dict(results=results, cum=dict(
    fe=float(cum.fe), machine_h=float(sum(cum.station_s.values())) / 3600,
    visits=dict(cum_visits), effort_min=sum(results[s]['effort_min'] for s in results)),
    core=core_res, catalysts=cat_res, sustain=sustain, penrose_mass=penrose_mass, penrose_metal=penrose_metal)
# 電力の成立性：段階Sの装置の最大消費が、段階S-1までの発電で賄えるか
power = {}
for s in range(1, 6):
    peak = max([r['fe'] for r in RECIPES.values() if r['stage'] == s] + [0])
    cap = GEN_CAPACITY[s - 1] if s > 1 else GEN_CAPACITY[1]
    need = results[s]['fe']
    power[s] = dict(peak=peak, cap_prev=cap, ok=peak <= cap,
                    supply_min=need / cap / 20 / 60 if cap else None)
out['power'] = power
# ネザースターの必要数（点火前まで）
out['nether_stars'] = {s: results[s]['raw'].get('ネザースター', 0) for s in results}
json.dump(out, open(HERE / 'analysis.json', 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
print('ネザースター', out['nether_stars'])
for s, p in power.items():
    print('電力', s, p)

for s in range(1, 6):
    r = results[s]
    print(f"段階{s}: 手間 {r['effort_min']:.0f}分, 加工 {r['machine_h']:.2f}h, 電力 {r['fe']/1e9:.3f} GFE, 遺構 {r['visits']}")
print('累計', out['cum'])
print('炉の質量値', penrose_mass)
for c, v in cat_res.items():
    print(c, round(v['fe']/1e6, 2), 'MFE', round(v['machine_min'], 1), 'min', round(v['effort'], 2), v['explore'])
print(json.dumps(sustain, ensure_ascii=False))
print('段5 raw', results[5]['raw'])
print('段4 raw', results[4]['raw'])
