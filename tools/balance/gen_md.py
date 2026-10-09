# -*- coding: utf-8 -*-
"""recipes.py と analysis.json から設計書Markdownを生成する。"""
import json
import sys
from pathlib import Path
HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent))  # tools/recipes.py, tools/config_spec.py
from fractions import Fraction as F
from collections import defaultdict
from recipes import (RECIPES, ORDER, EXPLORE, STAGE_NAMES, MILESTONES, RUIN_REGEN_DAYS, GEN_CAPACITY,
                     REPAIR, REPAIR_WEAR, MAX_REPAIRS, LIFE_MULT, RESTORED, CARDS)
import analyze  # noqa: F401  (analysis.json を最新化)
from config_spec import SERVER, CLIENT, DATAPACK, PRESETS

A = json.load(open(HERE / 'analysis.json', encoding='utf-8'))
L = []
w = L.append

CAT_JP = {'material': '中間素材', 'part': '構造部品', 'machine': '加工装置', 'generator': '発電機',
          'multiblock': 'マルチブロック', 'catalyst': '触媒', 'cable': 'ケーブル', 'storage': '蓄電・貯蔵',
          'tool': '道具', 'romance': '特異点技術', 'upgrade': 'アップグレード'}
CAT_ORDER = ['material', 'catalyst', 'part', 'cable', 'machine', 'generator', 'storage', 'multiblock',
             'tool', 'upgrade', 'romance']
FLUID_UNIT = {'水', '水素', '液体窒素', 'ヘリウム', '液体ヘリウム', 'アクシオン凝縮体'}


def qty(item, q):
    q = F(q)
    if item in FLUID_UNIT:
        return f'{item} {q} mB'
    if item in EXPLORE and EXPLORE[item]['uses'] > 1 and q < 1:
        uses = q * EXPLORE[item]['uses']
        return f'{item}（{uses}回分／全{EXPLORE[item]["uses"]}回）'
    if item in RESTORED and q < 1:
        u = EXPLORE[RESTORED[item]]['uses']
        return f'{item}（{q * u}回分／全{u}回）'
    if item in RECIPES and RECIPES[item]['cat'] == 'catalyst' and q < 1:
        life = {'時間結晶触媒': 45}.get(item, 1)
        return f'{item}（寿命{q * life}分ぶん）'
    if item == '残響の欠片（型）':
        return f'残響の欠片（型として{q * 16}回分／全16回）'
    if item == '質量値':
        return f'質量値 {q} ぶんのブロック'
    return f'{item} ×{q}'


def out_str(n, r):
    o = r['out']
    return f'{o} mB' if n in FLUID_UNIT else f'×{o}'


def power_str(fe):
    if not fe:
        return '—'
    if fe >= 1_000_000:
        return f'{fe / 1_000_000:g} MFE/t'
    if fe >= 1000:
        return f'{fe / 1000:g} kFE/t'
    return f'{fe} FE/t'


def time_str(t):
    if not t:
        return '即時'
    if t >= 60:
        return f'{t / 60:g}分'
    return f'{t:g}秒'


# ======================================================================
w('# Singulo 装置・素材・レシピ詳細設計')
w('')
w('対象: Minecraft 1.21.1 / NeoForge。この文書は `tools/recipes.py`（レシピの唯一の正）から `tools/balance/gen_md.py` で自動生成し、難易度の計算は `tools/balance/analyze.py`、整合性の検証は `tools/gen_data.py` で行っている。数値はプレイテスト前の初期値で、サーバー設定とデータパックで変更できる前提。')
w('')
w('## 読み方')
w('')
w('- **個数**: 材料の後ろの「×n」は1回の製作で使う数。成果物の個数は「出力」列。')
w('- **流体・ガス**: 単位はmB。装置の流体スロットで対応する容器を使うか、対応する他modの配管で輸送する。Singulo専用のガスボンベはない。')
w('- **遺構回収物**はそのままではレシピに使えない。データ系は白紙データカードに書き写し、部品系は装置で復元してから使う。どちらも使用回数制で、使い切っても修復・再復元でき、そのたびに最大使用回数が×0.75になる（3回まで）。')
w('- **作業台と精密組立台**: 段階1〜2は作業台（3×3の枠、材料は合計9個まで）。段階3以降の装置・部品・道具は、段階2の終わりに作る精密組立台で組み立てる。精密組立台は枠の形を使わず、材料の種類（6枠まで）と個数だけで指定する。電力で動くので自動化もできる。')
w('- **製作場所**: 作業台・バニラのかまど・溶鉱炉、または本modの装置。レシピごとに指定された装置を使う。速度は装置固有の倍率・触媒・単極子アップグレード・時間の場で変わり、上位段階の装置に共通する倍速や並列処理はない。')
w('- **電力**: 表の値は処理中の消費（FE/t）。総消費は「消費 × 20 × 秒数」。')
w('')

# ---- 段階の全体像
w('## 段階と到達セット')
w('')
w('各段階の「到達セット」は、次の段階に進むまでに作るものの目安。難易度検証はこのセットを原料まで展開して計算している。')
w('')
w('| 段階 | 名前 | 到達セット | 使える発電（前段階まで） |')
w('| --- | --- | --- | --- |')
for s in range(1, 6):
    ms = '、'.join(f'{k}×{v}' for k, v in MILESTONES[s].items())
    cap = GEN_CAPACITY[s - 1] if s > 1 else GEN_CAPACITY[1]
    w(f'| {s} | {STAGE_NAMES[s]} | {ms} | {power_str(cap)} |')
w('')

# ---- 製作場所ごとの一覧
w('## 製作場所ごとの製作物')
w('')
w('どの装置で何が作れるかの一覧。装置自体のレシピは段階別レシピの表にある。')
w('')
by_station = defaultdict(list)
for n in ORDER:
    by_station[RECIPES[n]['station']].append(n)
station_order = sorted(by_station, key=lambda st: (
    RECIPES[st]['stage'] if st in RECIPES else (0 if 'バニラ' in st or st == '作業台' else 9), st))
w('| 製作場所 | 段階 | 種類 | 作れるもの |')
w('| --- | --- | --- | --- |')
for st in station_order:
    if st in RECIPES:
        stg, kind = RECIPES[st]['stage'], CAT_JP[RECIPES[st]['cat']]
    elif st == 'Pリアクター（触媒モード）':
        stg, kind = 5, 'マルチブロックの運用モード'
    elif st == 'マルチブロック組み立て':
        stg, kind = '—', '設置して形成'
    else:
        stg, kind = '—', 'バニラ'
    items = '、'.join(by_station[st])
    w(f'| {st} | {stg} | {kind} | {items} |')
w('')

# ---- 段階別レシピ
w('## 段階別レシピ')
w('')
for s in range(1, 6):
    w(f'### 段階{s}: {STAGE_NAMES[s]}')
    w('')
    names = [n for n in ORDER if RECIPES[n]['stage'] == s]
    for cat in CAT_ORDER:
        group = [n for n in names if RECIPES[n]['cat'] == cat]
        if not group:
            continue
        w(f'**{CAT_JP[cat]}**')
        w('')
        w('| 成果物 | 出力 | 製作場所 | 材料 | 時間 | 電力 | 備考 |')
        w('| --- | --- | --- | --- | --- | --- | --- |')
        for n in group:
            r = RECIPES[n]
            mats = '、'.join(qty(i, q) for i, q in r['inputs'].items()) or '入力なし'
            w(f"| {n} | {out_str(n, r)} | {r['station']} | {mats} | {time_str(r['t'])} | {power_str(r['fe'])} | {r['note']} |")
        w('')

# ---- 圧縮
w('## 圧縮と質量の仕組み')
w('')
w('丸石生成機だけで縮退物質殻を無限に作れないよう、3つの仕組みを入れた。Pリアクターの燃料（質量ペレット）には適用せず、燃料は何でもよいまま残す。出力はエディントン限界で頭打ちになるので、燃料が無限でもバランスは崩れない。')
w('')
w('1. **金属の核**: 現実の中性子星は、恒星の鉄の核が重力崩壊してできる。縮退物質殻1個には、岩石の圧縮ブロックLv3を8個に加えて、金属圧縮ブロックLv2が2個要る。岩石だけでは崩壊が始まらない。金属は鉄・銅・金と、他modの金属タグを受け付ける。')
w('2. **圧縮熱**: 物質を押し縮めると熱が出る（断熱圧縮）。縮退物質殻1個ごとに液体窒素6,000 mB（質量1あたり約1 mB）を使う。冷却塔1基は毎秒100 mBなので、殻1個ぶんの冷却に約60秒かかり、縮退圧縮炉1台の処理時間とほぼ釣り合う。圧縮炉を増やすなら冷却塔も同じ数だけ要る。')
w('3. **混成ボーナス**: 圧縮ブロックLv2を作るとき、材料のLv1が3種類以上の元ブロックから作られていれば、9個でなく8個で済む。効くのはLv2の段だけで、節約は約11%にとどめた。単一の丸石ラインより、いろいろな資源ラインを組む動機になる。')
w('')

# ---- 遺構回収物
KIND_JP = {'data': '書き写し', 'part': '復元', 'once': '目覚め（一回限り）'}
w('## 遺構回収物')
w('')
w('遺構で拾ったものは、旧文明の規格のままなので直接は使えない。自分の工場で使える形にする工程を1段挟む。')
w('')
w('- **データ系**（観測ログ、量子データ片、培養データ）は原本をアーカイブ端末に置き、白紙データカードに書き写す。レシピに使うのはカードで、原本の使用回数は「書き写せる回数」になる。')
w('- **部品系**（制御ユニット、冷却原子トラップ、時間結晶の種、アノマリー・サンプル）は「劣化した○○」として出てくる。対応する装置で復元すると使用回数つきの部品になり、使い切ると劣化状態に戻って再び復元できる。')
w('- **特異点の種**は「休眠した特異点の種」として出てくる。特異点封入台で目覚めさせ、最初の点火で使い切る。')
w('- **初回の解読**: その種類の回収物を初めて書き写す・復元すると、アーカイブ端末の記録とレシピが解放される。遺物を持ち帰るたびに技術が増える手応えになる。')
w('')
w(f'修復・再復元のたびに最大使用回数が×{float(REPAIR_WEAR):g}になり、{MAX_REPAIRS}回まで直せるので、1個あたりの寿命は新品の約{float(LIFE_MULT):.2f}倍。何か所も巡らなくても、少数の遺構を定期的に回れば足りる。「自動化」は、その段階を完成させると自動探査機で回収できるようになる段階（N−2ルール）。')
w('')
w('| 回収物 | 使えるようにする工程 | 遺構 | 1回の遠征で | 使用回数 | 再生 | 自動化 |')
w('| --- | --- | --- | --- | --- | --- | --- |')
for it, info in EXPLORE.items():
    lo, hi = info['per_run']
    auto = f'段階{info["auto_stage"]}完成後' if info['auto_stage'] else '常に手動'
    rng = f'{lo}個' if lo == hi else f'{lo}〜{hi}個'
    w(f"| {it} | {KIND_JP[info['kind']]} | {info['ruin']} | {rng} | {info['uses']}回 | ゲーム内{RUIN_REGEN_DAYS[info['ruin']]}日 | {auto} |")
w('')
w('### 書き写し')
w('')
w('| カード | 原本 | 製作場所 | 材料 | 時間 |')
w('| --- | --- | --- | --- | --- |')
for card, src in CARDS.items():
    r = RECIPES[card]
    mats = '、'.join(qty(i, q) for i, q in r['inputs'].items())
    w(f"| {card} | {src} | {r['station']} | {mats} | {time_str(r['t'])} |")
w('')
w('白紙データカードは作業台でセラミック基板1個とレッドストーン2個から4枚作れる。')
w('')
w('### 復元と修復')
w('')
w('部品系の復元は、初回も使い切ったあとの再復元も同じ材料で行う。データ系の原本は、書き写せる回数がなくなったら修復する。')
w('')
w('| 回収物 | 工程 | 場所 | 材料 | 時間 | 電力 | 使用回数（新品→1回目→2回目→3回目） | 復元後の名前 |')
w('| --- | --- | --- | --- | --- | --- | --- | --- |')
inv = {v: k for k, v in RESTORED.items()}
for it, rp in REPAIR.items():
    u = EXPLORE[it]['uses']
    seq = '→'.join(str(int(u * float(REPAIR_WEAR) ** k)) for k in range(MAX_REPAIRS + 1))
    mats = '、'.join(qty(i, q) for i, q in rp['inputs'].items())
    kind = '復元' if it in inv else '修復'
    w(f"| {it} | {kind} | {rp['station']} | {mats} | {time_str(rp['t'])} | {power_str(rp['fe'])} | {seq} | {inv.get(it, '—')} |")
w('')
w('休眠した特異点の種の目覚めは段階5のレシピ表にある。修復・復元の材料はどれも、その回収物自身を必要としない（`tools/gen_data.py` の検証で循環がないことを確認済み）。')
w('')

# ---- ネットワークグラフ
w('## シンギュラリティ・コアの素材ネットワーク')
w('')
w('最上位触媒シンギュラリティ・コアに至る、加工品と遺構回収物の依存関係。バニラ素材は省き、原料の総量は次の検証の表に載せた。矢印は「材料 → 成果物」、段階ごとに枠で囲み、遺構回収物は二重枠で示す。復元品は、復元する装置の段階に置いた。')
w('')


def node_stage(n):
    if n in RECIPES:
        return RECIPES[n]['stage']
    return RECIPES[REPAIR[RESTORED[n]]['station']]['stage']


def deps(n, acc):
    if n in acc:
        return
    if n in RESTORED:
        acc.add(n)
        return
    if n not in RECIPES:
        return
    acc.add(n)
    for i in RECIPES[n]['inputs']:
        deps(i, acc)


def node_inputs(n):
    if n in RESTORED:
        return [RESTORED[n]]
    return list(RECIPES[n]['inputs'])


nodes = set()
deps('シンギュラリティ・コア', nodes)
ex_nodes = {e for n in nodes for e in node_inputs(n) if e in EXPLORE}
ids = {n: f'n{k}' for k, n in enumerate(sorted(nodes | ex_nodes))}
w('```mermaid')
w('flowchart LR')
for s in range(1, 6):
    members = [n for n in nodes if node_stage(n) == s]
    if not members:
        continue
    w(f'  subgraph S{s}["段階{s} {STAGE_NAMES[s]}"]')
    for n in sorted(members):
        w(f'    {ids[n]}["{n}"]')
    w('  end')
w('  subgraph EX["遺構回収物"]')
for n in sorted(ex_nodes):
    w(f'    {ids[n]}[["{n}"]]')
w('  end')
edges = set()
for n in nodes:
    for i in node_inputs(n):
        if i in ids:
            edges.add((ids[i], ids[n]))
for a, b in sorted(edges):
    w(f'  {a} --> {b}')
w('  classDef ex fill:#fff4e0,stroke:#c77700,stroke-width:2px')
w('  classDef core fill:#e8f0ff,stroke:#2b5fd9,stroke-width:3px')
w('  class ' + ','.join(ids[n] for n in sorted(ex_nodes)) + ' ex')
w(f"  class {ids['シンギュラリティ・コア']} core")
w('```')
w('')
w(f'コアまでの加工品・復元品は{len(nodes)}種類、関わる遺構は4種類すべて。最も貴重なのは最終実験施設の劣化したアノマリー・サンプルだが、復元品1個（使用回数4）を再復元しながら使うと、1個でコア約11個ぶんまかなえる。')
w('')

# ---- 難易度検証
R = A['results']
w('## 難易度検証')
w('')
w(f"結論: 段階3〜4の伸びは前段比2〜3倍、最終段階は{R['5']['effort_min'] / R['4']['effort_min']:.1f}倍で、エンドの「少し重い」狙いの範囲に収まった。金属の核を入れたぶん最終段階は以前より重いが、遺構回収物の修復で遠征の回数は減っている。電力は各段階とも前段階の発電で賄える。")
w('')
w('### 方法')
w('')
w('- 到達セットを原料まで再帰的に展開し、バニラ素材・遺構回収物・加工時間・加工電力を合計した（`analyze.py`）。')
w('- **手間指数**は、バニラ素材を集める時間の目安（中盤の簡単な自動化がある前提、例: 鉄インゴット0.02分、ダイヤモンド0.5分、ネザースター10分、丸石1個0.002分）と、遺構への遠征時間（観測拠点15分、研究棟25分、培養施設40分、最終実験施設60分）の合計。絶対値より、段階間の比を見るための指標。')
w('- **加工時間**は装置1台で直列に処理した延べ時間。実際は装置を複数台使う並列化や、対応するアップグレード・時間加速などで短くなる。')
w('- 段階4完成で観測拠点、段階5完成で研究棟の回収物は自動探査機で回収する前提（N−2ルール）。')
w('')
w('### 段階ごとの重さ')
w('')
w('| 段階 | 手間指数 | 前段比 | 加工時間（1台直列） | 加工電力 | 必要な遺構遠征 |')
w('| --- | --- | --- | --- | --- | --- |')
prev = None
for s in range(1, 6):
    r = R[str(s)]
    ratio = f'{r["effort_min"] / prev:.1f}倍' if prev else '—'
    vis = '、'.join(f'{k}{v}回' for k, v in r['visits'].items()) or 'なし'
    fe = r['fe']
    fe_s = f'{fe / 1e9:.2f} GFE' if fe >= 1e8 else f'{fe / 1e6:.1f} MFE'
    w(f"| {s} {STAGE_NAMES[s]} | {r['effort_min']:.0f}分 | {ratio} | {r['machine_h']:.1f}時間 | {fe_s} | {vis} |")
    prev = r['effort_min']
c = A['cum']
w(f"| 合計 | {c['effort_min']:.0f}分 | — | {c['machine_h']:.1f}時間 | {c['fe'] / 1e9:.1f} GFE | " +
  '、'.join(f'{k}{v}回' for k, v in c['visits'].items()) + ' |')
w('')
w(f"段階1→2の伸び（約{R['2']['effort_min'] / R['1']['effort_min']:.1f}倍）は、段階1そのものがごく安いため。熱電発電機8台を含めても数分で集まる。最終段階の{R['5']['effort_min'] / R['4']['effort_min']:.1f}倍は目標の3倍をわずかに超えるが、混成ボーナスを使えばもう少し下がる。手間指数の合計は約{c['effort_min'] / 60:.0f}時間ぶんの採集・遠征に当たり、設計・建築・移動を足した全体の目安（約45時間）と矛盾しない。")
w('')
w('### 最終段階の原料')
w('')
w('Pリアクター、点火用のSMESモジュール25個、ホライズン・バス16本、封入台、最初のシンギュラリティ・コアまでの原料（端数切り上げ前の合計）。')
w('')
w('| 原料 | 量 |')
w('| --- | --- |')
for k, v in sorted(R['5']['raw'].items(), key=lambda kv: -kv[1]):
    unit = ' mB' if k in FLUID_UNIT else ''
    label = {'質量値': '岩石の質量値（丸石換算の個数）', '金属質量': '金属の質量値（鉄ブロック1個＝3）'}.get(k, k)
    w(f'| {label} | {v:,.0f}{unit} |' if v >= 1 else f'| {label} | {v:g}{unit} |')
w('')
w(f"リアクター本体の炉殻だけで、丸石換算 約{A['penrose_mass']:,.0f}個の岩石と、鉄ブロック換算 約{A['penrose_metal'] / 3:,.0f}個ぶんの金属を圧縮する。丸石生成機に加えて金属の確保（鉱石処理やゴーレムトラップ）と冷却塔の増設が段階5の主な作業になる。MekanismのSPSで外殻1個ごとに処理済み核廃棄物50,000 mBを要する重さと同じ種類の「量の壁」に当たる。")
w('')
w('### 電力の成立性')
w('')
w('各段階の装置の最大消費が、前段階までの発電で賄えるかを確認した。')
w('')
w('| 段階 | 装置の最大消費 | 前段階までの発電 | 判定 | 段階の加工電力をまかなう時間 |')
w('| --- | --- | --- | --- | --- |')
for s in range(1, 6):
    p = A['power'][str(s)]
    ok = '賄える' if p['ok'] else '不足'
    w(f"| {s} | {power_str(p['peak'])} | {power_str(p['cap_prev'])} | {ok} | 約{p['supply_min']:.1f}分 |")
w('')
w('点火の50 GFEは、段階4の縮退熱炉（20 MFE/t）でSMESモジュール25個を約2分で満たせる。点火には別途ホライズン・バス（4 GFE/t）が要るので、10秒以内に流し込む条件が「送電の壁」として効く。')
w('')
w('### 触媒1個の限界費用')
w('')
w('設備が揃ったあと、触媒を1個追加で作るのにかかる量。')
w('')
w('| 触媒 | 寿命 | 加工電力 | 加工時間 | 手間指数 | 1個に要る遺構回収物 |')
w('| --- | --- | --- | --- | --- | --- |')
LIFE = {'ミュオン触媒': 20, 'BE凝縮触媒': 30, '時間結晶触媒': 45, 'シンギュラリティ・コア': 60}
for k, v in A['catalysts'].items():
    ex = '、'.join(f'{a} {b:g}' for a, b in v['explore'].items())
    w(f"| {k} | {LIFE[k]}分 | {v['fe'] / 1e6:.1f} MFE | {v['machine_min']:.0f}分 | {v['effort']:.1f}分 | {ex} |")
w('')
w('時間結晶触媒の育成は20分で、寿命45分より短い。育成槽1台で、時間結晶触媒を使う装置2台を止めずに回せる。シンギュラリティ・コアは時間結晶触媒を材料に含むので、1個に1時間強かかる。待ち時間というより「貴重な触媒を計画的に作る」重さとして残した。')
w('')
w('### 遺構の持続性')
w('')
w('触媒を消費する装置1台を止めずに回したとき、現実時間1時間あたりに要る遺構の周回数。遺構の再生はゲーム内7日（現実約2.3時間）または14日（約4.7時間）。')
w('')
w('| 触媒 | 律速になる回収物 | 1時間あたりの必要数 | 1時間あたりの周回 |')
w('| --- | --- | --- | --- |')
for k, d in A['sustain'].items():
    top = max(d.items(), key=lambda kv: kv[1]['runs_per_h'])
    w(f"| {k} | {top[0]}（{top[1]['ruin']}） | {top[1]['items_per_h']:g}個 | {top[1]['runs_per_h']:g}回 |")
w('')
w('修復制にしたことで、どの触媒でも1時間あたりの周回は0.02回前後に下がった。遺構1か所は再生まで現実約2.3〜4.7時間かかるが、それでも1か所で触媒を消費する装置を十数台以上支えられる。何か所も巡らなくても、各種の遺構を1か所ずつ押さえて定期的に回れば足りる。コアをワールドライン・アンカーに埋め込めば消費はゼロになるので、「どこにコアを使うか」の選択は残る。')
w('')
w('### Mekanismとの比較')
w('')
w('| 観点 | Mekanism（10.7系） | Singulo |')
w('| --- | --- | --- |')
w('| エンドの発電 | 核融合炉 最大約200 MFE/t（D-T燃料を毎tick 1000 mB） | Pリアクター 約2.1 GFE/t（約10倍） |')
w('| 燃料 | 専用の燃料ライン（重水素・三重水素） | 任意のブロック（質量ペレット） |')
w(f"| 最大の量の壁 | SPS外殻72個（外殻1個に処理済み核廃棄物50,000 mB） | 炉殻48枚（丸石換算 約{A['penrose_mass'] / 10000:.0f}万個＋鉄ブロック換算 約{A['penrose_metal'] / 3:,.0f}個の圧縮、冷却つき） |")
w('| 探索 | 不要 | 4種の遺構、最終はボス戦 |')
w('| 点火 | レーザーで1 GFE | 50 GFEを10秒以内（専用ケーブル必須） |')
w('')
w('量の壁は同程度にそろえ、探索・ボス・点火の条件で「少し難しい」側に寄せた。その代わり、出力と燃料の自由度で大きく上回る。')
w('')
w('### 調整した点')
w('')
w('検証の途中で見つかった問題と、レシピに入れた修正。')
w('')
w('1. **最終段階が重すぎた**: 炉殻1枚に縮退物質殻1個だと丸石換算約38万個になり、段階5の手間が前段比7.5倍に跳ねた。殻1個から炉殻を複数枚作る形に変えた（最終的に6枚、10番を参照）。')
w('2. **段階2の装置が動かなかった**: 当初の冷却塔（4 kFE/t）や加速器（8 kFE/t）は、段階1の熱電発電機では到底賄えなかった。段階2の装置を200〜400 FE/t、段階3を2〜8 kFE/tに下げ、到達セットの熱電発電機を8台にした。')
w('3. **探索素材の取り過ぎを防いだ**: 遺構回収物を使用回数制にし、1回の遠征で数十個ぶんの触媒が作れる量にした。')
w('4. **丸石だけで無限化できた**: 金属の核と圧縮熱を入れた。当初は「Lv3の9個中3個を金属」にする案だったが、炉全体で鉄インゴット約8万個ぶんになり重すぎたため、「岩石Lv3を8個＋金属Lv2を2個」に下げた。')
w('5. **遠征が多すぎた**: 遺構回収物を修復可能にし、時間結晶の種（8回）、アノマリー・サンプル（4回）、制御ユニット（16回）も使用回数制にした。触媒1台を回すのに要る周回が約1/3〜1/10に減った。')
w('6. **回収物を直接使っていた**: 遺物をそのまま工場に入れるのは不自然なので、データ系は書き写し、部品系は復元の工程を挟んだ。原本・劣化品は修復や再復元で使い続けられる。書き写しと復元の手間が増え、段階5の前段比は3.5倍から3.6倍になった。')
w('7. **残響の欠片が増やせなかった**: 時間結晶触媒1個に1個要るのに、古代都市でしか拾えなかった。段階3に残響共鳴器を入れ、拾った欠片を型にしてスカルクから複製できるようにした（型1個で最大17個ぶん）。代替の「スカルク16個でも可」は外して一本化した。')
w('8. **ネザースターが際限なく要った**: エキゾチック物質とコアにネザースターが要り、終盤ずっとウィザー周回が続いた。点火後はリアクターのジェット・コレクターが出すジェット凝縮体から人工星核を作れるようにし、ネザースターの代わりに使えるようにした。点火までに要るのは3個で、以降はゼロ。')
w('9. **ただ待つだけの時間があった**: 段階5の加工時間（1台直列）の大半が、圧縮機（約8時間）と時間結晶の育成（約7時間）だった。圧縮は縮退圧縮炉の冷却ですでに律速しているので、前段の圧縮機を4倍速にして二重の足止めをなくした。時間結晶の育成は60分から20分にし、種の復元も10分から2分にした。段階5の加工時間は約21時間から約9時間に、段階4は約6時間から約3時間に縮んだ。')
w('10. **重さの再調整**: 待ち時間を減らすと段階4が軽くなり、段階5の前段比が4倍に跳ねた。炉殻を殻1個から6枚作る形にして、3.3倍に戻した。')
w('11. **作業台の枠に収まらなかった**: 作業台のレシピ59件中21件が、材料の合計で9個を超えていた（最大は炉心制御装置の29個）。段階3以降の組み立てを精密組立台に移し、よく一緒に使う材料を中間部品にまとめた（量子もつれ素子4個→量子演算モジュール、トポロジカル導線8本→トロイダル磁気コイル、エキゾチック物質4個ほか→重力場安定化ユニット）。作業台のレシピはすべて9個以内、精密組立台は6種類以内に収まり、`tools/gen_data.py` の検証で確認している。')
w('')
w('### 残る懸念とプレイテストで見る点')
w('')
w('- **ネザースター**: 点火までに3個（グラビトン・マニピュレーターも作るなら4個）。ウィザーを3〜4回倒す必要があるので、設定で個数を減らせる（プリセット casual なら0個）。')
w(f"- **待ち時間**: 段階5の加工時間は1台直列で約{R['5']['machine_h']:.0f}時間。最も長いのは時間結晶育成槽と圧縮機で、どちらも装置を増やせば並列化できる。プレイテストで体感時間を測る。")
w('- **手間指数の重み**: 採集時間の重みは仮置き。プレイテストの実測で置き換える。')
w('')
w('## 設定')
w('')
w('数値はほぼすべて設定で変えられる。サーバー設定はワールドごとに効き、レシピや質量値などはデータパックで差し替える。下の既定値はこの設計書の数値と一致させてあり、`tools/gen_data.py` の検証で主要な項目の一致を確認している。')
w('')
w('### プリセット')
w('')
w('`general.preset` で、難易度に関わる項目をまとめて切り替えられる。個別の値を変えると自動で `custom` になる。')
w('')
w('| プリセット | 内容 |')
w('| --- | --- |')
for k, v in PRESETS.items():
    w(f'| `{k}` | {v} |')
w('')
w('### サーバー設定（config/singulo-server.toml）')
w('')
SEC_JP = {'general': '全般', 'netherStar': 'ネザースター', 'power': '電力', 'catalyst': '触媒',
          'compression': '圧縮・質量', 'ruins': '遺構', 'echo': '残響の欠片', 'hazards': '危険・世界への影響',
          'gear': '道具', 'performance': '負荷'}
cur = None
for sec_, key, default, rng, effect in SERVER:
    if sec_ != cur:
        if cur is not None:
            w('')
        w(f'**{SEC_JP[sec_]}**（`[{sec_}]`）')
        w('')
        w('| キー | 既定値 | 範囲 | 何が変わるか |')
        w('| --- | --- | --- | --- |')
        cur = sec_
    w(f'| `{key}` | `{default}` | {rng} | {effect} |')
w('')
w('### クライアント設定（config/singulo-client.toml）')
w('')
w('| キー | 既定値 | 範囲 | 何が変わるか |')
w('| --- | --- | --- | --- |')
for sec_, key, default, rng, effect in CLIENT:
    w(f'| `{sec_}.{key}` | `{default}` | {rng} | {effect} |')
w('')
w('### データパック')
w('')
w('`/reload` で反映される。KubeJSやCraftTweakerからも同じ内容を変えられる。')
w('')
w('| パス | 対象 | 変えられること |')
w('| --- | --- | --- |')
for path, target, what in DATAPACK:
    w(f'| `{path}` | {target} | {what} |')
w('')

w('## 付録: データとスクリプト')
w('')
w('- `tools/recipes.py`: レシピ・遺構回収物・到達セット・発電能力の定義。ここを直して再生成すれば、この文書の表とグラフ、modのレシピがすべて更新される。')
w('- `tools/gen_data.py`: modのデータを生成する。生成の前に、段階の逆転（後の段階の素材や装置を前の段階で使っていないか）、未定義の製作場所、レシピと修復の循環、設定の既定値とレシピの一致、作業台（9個以内）と精密組立台（6種類以内）の枠を検証する。現状はすべて通過。')
w('- `tools/balance/analyze.py`: 原料展開と難易度の計算。結果は `tools/balance/analysis.json`。')
w('- `tools/config_spec.py`: 設定項目・プリセット・データパックの仕様。modの設定クラスもここから生成する。')
w('- `tools/balance/gen_md.py`: この文書を生成する。')

open(HERE.parent.parent / 'singulo_recipes.md', 'w', encoding='utf-8').write('\n'.join(L) + '\n')
print('written', len(L), 'lines')
