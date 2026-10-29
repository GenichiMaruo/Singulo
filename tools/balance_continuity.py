"""Audit whether late factories retain demand for early intermediates.

Uses the same effective recipes and canonical-route assumptions as balance_audit.
Counts gross production, not a sum of incompatible tiers or recipe stage labels.
"""
import csv
import argparse
import json
import os
from pathlib import Path
from balance_audit import Model, Bill

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / 'docs/reviews/balance-0.13.0'
SELECTED = [
    ('steel_ingot', 'Steel ingot'), ('white_ceramic_composite', 'White ceramic'),
    ('basic_circuit', 'Basic circuit'), ('steel_plate', 'Steel plate'),
    ('ceramic_substrate', 'Ceramic substrate'), ('basic_frame', 'Basic frame'),
    ('thermocouple_module', 'Thermocouple'), ('heat_exchange_core', 'Heat exchange core'),
    ('superconducting_coil', 'Superconducting coil'), ('superconducting_wire', 'Superconducting wire'),
    ('meteoric_iron_ingot', 'Meteoric iron'), ('quantum_computing_module', 'Quantum module'),
    ('entangled_element', 'Entangled element'), ('cold_atoms', 'Cold atoms'),
    ('optical_lattice_substrate', 'Optical lattice'), ('nihonium_plate', 'Nihonium plate'),
    ('phase_sync_plate', 'Phase sync plate'), ('muon_catalyst', 'Muon catalyst'),
    ('bose_condensate_catalyst', 'BE catalyst'), ('time_crystal_catalyst', 'Time catalyst'),
    ('time_crystal_module', 'Time module'), ('degenerate_matter_shell', 'Degenerate shell'),
]


def produced(model, item, summary):
    route = model.routes[item]
    return summary['operations'].get(route['id'], 0) * route['count']


def main():
    global OUT
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out-dir', type=Path, default=OUT)
    parser.add_argument('--report', default='../material-continuity-0.13.0.md')
    args = parser.parse_args()
    OUT = args.out_dir
    cache = ROOT / 'build/balance/plot-cache'
    cache.mkdir(parents=True, exist_ok=True)
    os.environ.setdefault('MPLCONFIGDIR', str(cache))
    import matplotlib
    matplotlib.use('Agg')
    matplotlib.rcParams['svg.hashsalt'] = 'singulo-balance-continuity-0130'
    import matplotlib.pyplot as plt
    import numpy as np

    model = Model(json.loads((ROOT / 'build/balance/runtime.json').read_text()))
    # Fluids have fluid: keys in the cost model but item-like catalogue IDs.
    for item, meta in list(model.meta.items()):
        if 'fluid:'+item in model.routes:
            model.meta['fluid:'+item] = dict(meta, unit='mB')
    model.meta['singulo:bismuth_ingot'] = dict(name='ビスマスインゴット', stage=2, category='material')
    results = json.loads((ROOT / 'build/balance/results.json').read_text())
    assert {x['id'] for x in results['machines']} == set(model.targets), 'Re-run balance_audit first'
    # Materials included in ordinary device chains and the main catalyst/shell loop.
    # Structure-specific parts are retained in the CSV, not mistaken for generic high-tier materials.
    core_loops = ['muon_catalyst', 'bose_condensate_catalyst', 'time_crystal_catalyst', 'singularity_core',
                  'degenerate_matter_shell', 'primordial_seed']
    operations = {key for x in results['machines'] for key in x['amortized']['operations']}
    for name in core_loops:
        b = Bill(model); b.need('singulo:'+name, 1); operations.update(b.operations)
    materials = sorted((item for item, meta in model.meta.items()
                        if meta['category'] in {'material', 'part', 'catalyst'}
                        and item in model.routes and model.routes[item]['id'] in operations),
                       key=lambda item: (model.meta[item]['stage'], item))
    continuity = []
    for item in materials:
        coverage = {str(t): [] for t in range(1, 6)}
        for device in results['machines']:
            if device['id'] != item and produced(model, item, device['amortized']) > 0:
                coverage[str(device['stage'])].append(device['id'])
        continuity.append(dict(id=item, **model.meta[item], consumers=coverage,
                               kit_production={str(t): produced(model, item, results['milestones'][str(t)]) for t in range(1, 6)}))
    # Look through same-tier housings to their underlying general materials.
    # Boss drops and machine/power prerequisites are reported separately; this is not an unlock test.
    devices = []
    for device in results['machines']:
        item = device['id']
        used = [x for x in materials if x != item and model.meta[x]['category'] in {'material', 'catalyst'}
                and produced(model, x, device['amortized']) > 0]
        maximum = max((model.meta[x]['stage'] for x in used), default=0)
        direct = [dict(item=x, quantity=float(n), tier=model.meta.get(x, {}).get('stage'))
                  for x, n in model.deps(model.routes[item])]
        devices.append(dict(id=item, name=device['name'], tier=device['stage'],
                            highest_embodied_generic_material_tier=maximum, direct=direct,
                            materials=used))
    loops = {}
    for name in core_loops:
        b = Bill(model, False); b.need('singulo:'+name, 1); summary = b.summary()
        loops[name] = {item: produced(model, item, summary) for item in materials
                       if item != 'singulo:'+name and produced(model, item, summary) > 0}
    OUT.mkdir(parents=True, exist_ok=True)
    (OUT / 'continuity.json').write_text(json.dumps(dict(materials=continuity, devices=devices, fresh_loops=loops), ensure_ascii=False, indent=2)+'\n')
    with (OUT / 'continuity.csv').open('w', newline='') as f:
        w = csv.writer(f, lineterminator='\n')
        w.writerow(['material', 'name', 'tier', 'unit', *[f'T{t}_device_coverage' for t in range(1, 6)],
                    *[f'T{t}_incremental_kit_production' for t in range(1, 6)]])
        for row in continuity:
            w.writerow([row['id'], row['name'], row['stage'], row.get('unit', 'item'),
                        *[len(row['consumers'][str(t)]) for t in range(1, 6)],
                        *[row['kit_production'][str(t)] for t in range(1, 6)]])
    lines = ['# 素材の継続需要：全件表', '', f'[評価条件・判定]({args.report})を参照。', '',
             '消費先数は標準ルートで各装置の部品を再帰展開した件数。同一装置内は一度だけ数える。',
             'コントローラ単体と完成構造を含み、全装置の同時建設台数ではない。素材自体の出力は消費先から除く。',
             '専用の外装・窓なども掲載するが、後のティアで使わないこと自体を問題と判定しない。',
             '液体・気体の数量はmB。CSVに単位を収録。', '',
             '| 素材・部品 | 名目Tier | T1消費先 | T2 | T3 | T4 | T5 |', '|---|---:|---:|---:|---:|---:|---:|']
    for row in continuity:
        lines.append('| '+row['name']+' | '+str(row['stage'])+' | '+' | '.join(str(len(row['consumers'][str(t)])) for t in range(1, 6))+' |')
    lines += ['', '## 装置側からの確認', '',
              '専用外装の名目Tierで判定せず、その下の汎用素材・触媒まで展開した最大Tier。',
              '起動設備、使用電力、装填する燃料・触媒、ボス品、生成器の資本費はこの列には含まない。',
              '従って「数値が装置Tier未満」だけでは進行の抜け道を意味しない。0は、この条件に当たる投入素材がないことを表す。', '',
              '| 装置 | 名目Tier | 内包する汎用素材の最大Tier |', '|---|---:|---:|']
    for row in devices:
        lines.append(f"| {row['name']} | {row['tier']} | {row['highest_embodied_generic_material_tier']} |")
    (OUT / 'continuity-all.md').write_text('\n'.join(lines).rstrip()+'\n')
    byid = {x['id']: x for x in continuity}
    values = np.array([[byid['singulo:'+name]['kit_production'][str(t)] for t in range(1, 6)] for name, _ in SELECTED])
    fig, ax = plt.subplots(figsize=(8.4, 10), constrained_layout=True)
    image = ax.imshow(np.log10(values+1), cmap='YlGnBu', aspect='auto')
    ax.set_xticks(range(5), [f'Tier {t} kit' for t in range(1, 6)])
    ax.set_yticks(range(len(SELECTED)), [label for _, label in SELECTED], fontsize=9)
    for i in range(values.shape[0]):
        for j in range(5):
            ax.text(j, i, f'{values[i,j]:g}', ha='center', va='center', fontsize=8,
                    color='white' if values[i,j] > 50 else '#222222')
    ax.set_title('Earlier intermediates produced for later construction\nWhole batches incl. leftovers; standard routes, no repair/recycling', fontsize=11)
    fig.colorbar(image, ax=ax, label='log10(produced items + 1)', fraction=.04)
    fig.savefig(OUT/'continuity.png', dpi=160)
    fig.savefig(OUT/'continuity.svg')
    plt.close(fig)
    path = OUT/'continuity.svg'
    path.write_text('\n'.join(line.rstrip() for line in path.read_text().splitlines())+'\n')
    print(f'{len(materials)} ordinary material/part chains; {len(devices)} device checks')


if __name__ == '__main__':
    main()
