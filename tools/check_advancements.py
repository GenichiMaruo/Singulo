"""Validate the generated progression tree, criteria, icons and handbook index."""
import json
from pathlib import Path
from advancements import ADVANCEMENTS, validate_advancements

ROOT = Path(__file__).resolve().parent.parent
DATA = ROOT / 'src/generated/resources/data/singulo/advancement'
ASSETS = ROOT / 'src/generated/resources/assets/singulo'
validate_advancements()
expected = {a[0] for a in ADVANCEMENTS}
actual = {p.stem for p in DATA.glob('*.json')}
if expected != actual:
    raise ValueError(f'Generated advancement IDs differ: {expected ^ actual}')
index = json.loads((ASSETS / 'guide/advancements.json').read_text())['advancements']
if index != [{'id': a[0], 'parent': a[1], 'stage': a[5]} for a in ADVANCEMENTS]:
    raise ValueError('Handbook progression index is out of sync')
for aid, parent, icon, frame, cond, stage, *_ in ADVANCEMENTS:
    adv = json.loads((DATA / f'{aid}.json').read_text())
    if adv.get('parent') != (f'singulo:{parent}' if parent else None):
        raise ValueError(f'{aid}: stale parent')
    if set(adv['criteria']) != {'done'}:
        raise ValueError(f'{aid}: changed criterion key would invalidate existing progress')
    icon_id = adv['display']['icon']['id']
    if icon_id.startswith('singulo:') and not (ASSETS / 'models/item' / (icon_id.split(':')[1] + '.json')).is_file():
        raise ValueError(f'{aid}: nonexistent icon {icon_id}')
    for lang in ('ja_jp', 'en_us', 'zh_cn', 'zh_tw', 'ko_kr'):
        translations = json.loads((ASSETS / 'lang' / f'{lang}.json').read_text())
        for key in ('title', 'description'):
            if adv['display'][key]['translate'] not in translations:
                raise ValueError(f'{aid}: missing {lang} {key}')
print(f'{len(expected)} advancements: tree, criteria, icons, handbook and 5 languages validated')
