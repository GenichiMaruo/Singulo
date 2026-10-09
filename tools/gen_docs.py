"""説明文・ハンドブック・進捗を生成する（gen_data.py から呼ぶ）。

- 説明: desc.singulo.<id>（説明）、howto.singulo.<id>（作り方）、uses.singulo.<id>（使い道）
- ハンドブック: assets/singulo/guide/chapters.json と guide.singulo.* の文
- 進捗: data/singulo/advancement/*.json と advancement.singulo.* の文
"""

from advancements import ADVANCEMENTS
from descriptions import DESC
from guide import CHAPTERS

MODID = 'singulo'


def descriptions(ctx, ja, en):
    """ctx: MOD, RECIPES, EXPLORE, RESTORED, STATIONS, warn"""
    MOD, RECIPES = ctx['MOD'], ctx['RECIPES']
    used_in = {}
    for rname, r in RECIPES.items():
        for inp in r['inputs']:
            used_in.setdefault(inp, []).append(rname)
    for name, restored in ctx['RESTORED'].items():
        used_in.setdefault(restored, []).append(name)

    def en_name(n):
        return MOD[n][1] if n in MOD else n

    for name, (iid, _, kind) in MOD.items():
        if kind == 'fluid':
            continue
        d = DESC.get(name)
        if d is None:
            if kind not in ('structure',):
                ctx['warn'](f'説明がない: {name}')
            continue
        ja[f'desc.{MODID}.{iid}'], en[f'desc.{MODID}.{iid}'] = d
        # 作り方
        r = RECIPES.get(name)
        if name in ctx['EXPLORE']:
            ruin = ctx['EXPLORE'][name]['ruin']
            ja[f'howto.{MODID}.{iid}'] = f'入手: 遺構「{ruin}」の保管庫'
            en[f'howto.{MODID}.{iid}'] = f'Found in: {en_name(ruin) if ruin in MOD else "ruin caches"}'
        elif name in ctx['RESTORED']:
            ja[f'howto.{MODID}.{iid}'] = f'入手: {ctx["RESTORED"][name]}を復元する'
            en[f'howto.{MODID}.{iid}'] = f'Restore a {en_name(ctx["RESTORED"][name])}'
        elif r is not None:
            station = r['station']
            st_en = en_name(station.split('（')[0]) if station.split('（')[0] in MOD else {
                '作業台': 'Crafting Table', '溶鉱炉（バニラ）': 'Blast Furnace', 'かまど（バニラ）': 'Furnace',
                'マルチブロック組み立て': 'Multiblock assembly'}.get(station, station)
            ja[f'howto.{MODID}.{iid}'] = f'作り方: {station}（段階{r["stage"]}）'
            en[f'howto.{MODID}.{iid}'] = f'Made in: {st_en} (stage {r["stage"]})'
        uses = [u for u in used_in.get(name, []) if u in MOD and MOD[u][2] != 'fluid']
        if uses:
            shown = uses[:5]
            more = len(uses) - len(shown)
            ja[f'uses.{MODID}.{iid}'] = '使い道: ' + '、'.join(shown) + (f' ほか{more}件' if more else '')
            en[f'uses.{MODID}.{iid}'] = 'Used in: ' + ', '.join(en_name(u) for u in shown) + (f' and {more} more' if more else '')


def guide(ctx, ja, en):
    chapters = []
    for cid, icon, ja_title, en_title, pages in CHAPTERS:
        ja[f'guide.{MODID}.{cid}'] = ja_title
        en[f'guide.{MODID}.{cid}'] = en_title
        ids = []
        for pid, jt, jb, et, eb in pages:
            key = f'guide.{MODID}.{cid}.{pid}'
            ja[key + '.title'], ja[key + '.body'] = jt, jb
            en[key + '.title'], en[key + '.body'] = et, eb
            ids.append(f'{cid}.{pid}')
        chapters.append({'id': cid, 'icon': icon, 'pages': ids})
    ctx['write_json'](ctx['ASSETS'] / 'guide' / 'chapters.json', {'chapters': chapters})


def advancements(ctx, ja, en):
    out = []
    for aid, parent, icon, frame, cond, stage, jt, jd, et, ed in ADVANCEMENTS:
        icon_id = icon if ':' in icon else f'{MODID}:{icon}'
        key = f'advancement.{MODID}.{aid}'
        ja[key + '.title'], ja[key + '.description'] = jt, jd
        en[key + '.title'], en[key + '.description'] = et, ed
        display = {'icon': {'id': icon_id}, 'title': {'translate': key + '.title'},
                   'description': {'translate': key + '.description'}, 'frame': frame,
                   'show_toast': parent is not None, 'announce_to_chat': parent is not None, 'hidden': False}
        if parent is None:
            display['background'] = f'{MODID}:textures/block/degenerate_furnace_frame.png'
        kind = cond[0]
        if kind == 'tick':
            crit = {'trigger': 'minecraft:tick'}
        elif kind == 'item':
            item = cond[1] if ':' in cond[1] else f'{MODID}:{cond[1]}'
            crit = {'trigger': 'minecraft:inventory_changed', 'conditions': {'items': [{'items': item}]}}
        elif kind == 'milestone':
            crit = {'trigger': f'{MODID}:milestone', 'conditions': {'id': cond[1]}}
        elif kind == 'kill':
            crit = {'trigger': 'minecraft:player_killed_entity', 'conditions': {'entity': {'type': cond[1]}}}
        else:
            raise ValueError(cond)
        adv = {'display': display, 'criteria': {'done': crit}}
        if parent is not None:
            adv['parent'] = f'{MODID}:{parent}'
        ctx['write_json'](ctx['DATA'] / 'advancement' / f'{aid}.json', adv)
        out.append({'id': aid, 'parent': parent, 'stage': stage})
    # ハンドブックが段階ごとに並べるための一覧
    ctx['write_json'](ctx['ASSETS'] / 'guide' / 'advancements.json', {'advancements': out})
