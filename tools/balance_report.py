"""Render the selected balance audit into CSV, Markdown and standalone charts.

Run after balance_audit.py. Plotting requires matplotlib; arithmetic tests do not.
"""
from pathlib import Path
import csv
import argparse
import os
import json

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / 'docs/0.13.0/balance-adjusted'


def number(value):
    return f'{value:,.4f}'.rstrip('0').rstrip('.') if value % 1 else f'{value:,.0f}'


def render():
    global OUT
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out-dir', type=Path, default=OUT)
    parser.add_argument('--report', default='../balance-adjustment.md')
    args = parser.parse_args()
    OUT = args.out_dir
    cache = ROOT / 'build/balance/plot-cache'
    cache.mkdir(parents=True, exist_ok=True)
    os.environ.setdefault('MPLCONFIGDIR', str(cache))
    import matplotlib
    matplotlib.use('Agg')
    matplotlib.rcParams['svg.hashsalt'] = 'singulo-balance-0130'
    import matplotlib.pyplot as plt
    r = json.loads((ROOT / 'build/balance/results.json').read_text())
    OUT.mkdir(parents=True, exist_ok=True)
    # Only evaluated machinery and progression scenarios are serialized here.
    (OUT / 'bills.json').write_text(json.dumps(r, ensure_ascii=False, indent=2) + '\n')
    fields = ['tier', 'device', 'name', 'basis', 'resource', 'quantity']
    with (OUT / 'raw-materials.csv').open('w', newline='') as f:
        writer = csv.writer(f, lineterminator="\n"); writer.writerow(fields)
        for machine in r['machines']:
            for basis in ['integer', 'amortized']:
                for resource, quantity in machine[basis]['raw'].items():
                    writer.writerow([machine['stage'], machine['id'], machine['name'], basis, resource, quantity])
    lines = ['# 全装置の原料内訳（0.13.0開発版）', '',
             f'評価条件は[本報告]({args.report})を参照。初回一式＝整数バッチ、平均＝余剰を使い切る場合。',
             '下位加工設備は既設とし、その建設費は各行へ重ねていない。完成構造とコントローラを二重加算しないこと。',
             '水などの液体はmB、その他は個。石炭欄は材料用で、かまど燃料は別記。消耗品の残使用回数はJSONに収録。',
             '加工時間は全工程の仕事量の合計で、並列工場での経過時間ではない。', '']
    for stage in range(1, 6):
        rows = [m for m in r['machines'] if m['stage'] == stage]
        lines += [f'## Tier {stage}（{len(rows)}項目）', '']
        for m in rows:
            a, b = m['amortized'], m['integer']
            lines += [f"### {m['name']}", '', f"`{m['id']}`", '',
                      '| 原料 | 初回一式 | 平均 |', '|---|---:|---:|']
            for key in sorted(set(a['raw']) | set(b['raw'])):
                lines.append(f"| `{key}` | {number(b['raw'].get(key, 0))} | {number(a['raw'].get(key, 0))} |")
            lines += ['', f"初回加工電力: **{number(b['energy_fe']/1e6)} MFE**。加工仕事量: **{number(b['serial_machine_seconds']/60)}分**。別途かまど燃料: 石炭換算 **{number(b['vanilla_fuel_coal_equivalent'])}個**。", '']
            if b['life']:
                lines += ['消費する使用回数: ' + '、'.join(f'`{k}` {number(v)}' for k, v in b['life'].items()) + '。', '']
            if b['reusable_assets']:
                lines += ['別途必要な再使用可能な設計図: ' + '、'.join(f'`{x}`' for x in b['reusable_assets']) + '。', '']
    (OUT / 'all-machines.md').write_text('\n'.join(lines).rstrip() + '\n')
    with (OUT / 'progression.csv').open('w', newline='') as f:
        writer = csv.writer(f, lineterminator="\n")
        writer.writerow(['scenario', 'basis', 'iron', 'copper', 'carbon_coal', 'cobblestone', 'diamond', 'energy_fe', 'serial_seconds', 'furnace_coal_equivalent'])
        for basis in ['milestones', 'cumulative']:
            for stage, b in r[basis].items():
                writer.writerow([stage, basis, *[b['raw'].get('minecraft:'+key, 0) for key in ['raw_iron', 'raw_copper', 'coal', 'cobblestone', 'diamond']], b['energy_fe'], b['serial_machine_seconds'], b['vanilla_fuel_coal_equivalent']])
    tiers = list(range(1, 6)); kits = [r['milestones'][str(x)] for x in tiers]
    fig, axes = plt.subplots(1, 3, figsize=(14, 4.3), constrained_layout=True)
    colors = ['#6c94a8', '#69b8af', '#8d83c9', '#dcaa55', '#cf7180']
    for key, label, offset in [('raw_iron', 'Raw iron', -.24), ('raw_copper', 'Raw copper', 0), ('coal', 'Carbon coal', .24)]:
        axes[0].bar([x+offset for x in tiers], [b['raw'].get('minecraft:'+key, 0) for b in kits], width=.23, label=label)
    axes[0].set_ylabel('Items'); axes[0].set_yscale('log'); axes[0].legend(fontsize=8)
    axes[1].bar(tiers, [b['energy_fe']/1e6 for b in kits], color=colors)
    axes[1].set_yscale('log'); axes[1].set_ylabel('Recipe energy (MFE, log)')
    axes[2].bar(tiers, [b['serial_machine_seconds']/60 for b in kits], color=colors)
    axes[2].set_ylabel('Summed recipe workload (minutes)')
    for ax in axes:
        ax.set_xticks(tiers); ax.set_xlabel('Incremental tier kit'); ax.grid(axis='y', alpha=.2)
    fig.suptitle('Canonical progression kits: construction and initial consumables\nNot playtime; excludes mining, combat, generator fuel and 50 GFE reactor ignition', fontsize=11)
    for ext in ['svg', 'png']: fig.savefig(OUT / ('progression.'+ext), dpi=160)
    plt.close(fig)
    fig, ax = plt.subplots(figsize=(9, 4.5), constrained_layout=True)
    labels = ['Thermo\n(1000K / 240K)', 'Muon fusion', 'Quantum engine', 'Degenerate furnace', 'P reactor\ninitial mass, spin 0']
    values = [76, 4000, 200000, 5000000, 28595479]
    ax.bar(range(5), values, color=colors); ax.set_yscale('log'); ax.set_ylabel('FE/t (log)'); ax.set_xticks(range(5), labels, fontsize=8)
    for i, value in enumerate(values): ax.text(i, value*1.2, f'{value:,.0f}', ha='center', fontsize=8)
    ax.set_ylim(30, 1e8); ax.grid(axis='y', alpha=.2)
    ax.set_title('Default sustained generation examples\nP reactor initial Eddington-limited estimate; not its 2.1 GFE/t transfer cap', fontsize=11)
    for ext in ['svg', 'png']: fig.savefig(OUT / ('generation.'+ext), dpi=160)
    plt.close(fig)
    for path in OUT.glob('*.svg'):
        path.write_text('\n'.join(line.rstrip() for line in path.read_text().splitlines()) + '\n')
    print(f'Rendered {len(r["machines"])} device bills to {OUT}')


if __name__ == '__main__':
    render()
