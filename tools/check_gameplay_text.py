"""Check calculated power guidance against recipe data in all shipped languages."""
import json
import math
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent.parent
recipe = json.loads((ROOT / 'src/generated/resources/data/singulo/recipes/cryogenic_cooling_tower/liquid_nitrogen.json').read_text())
shape = (ROOT / 'src/main/java/io/github/genichimaruo/singulo/multiblock/Shapes.java').read_text()
height = int(re.search(r'TOWER_MIN_HEIGHT = (\d+)', shape).group(1))
# Height/5 speed and fixed energy per batch, as used by the cooling tower.
ticks = max(1, math.floor(recipe['time'] / (height / 5) + 0.5))
usage = math.ceil(recipe['energy'] * recipe['time'] / ticks)
amount = recipe['fluid_results'][0]['amount']
for locale in ('en_us', 'ja_jp', 'zh_cn', 'zh_tw', 'ko_kr'):
    lang = json.loads((ROOT / f'src/generated/resources/assets/singulo/lang/{locale}.json').read_text())
    flow = lang['guide.singulo.stage1.flow.body']
    if f'{usage:,} FE/t' not in flow:
        raise ValueError(f'{locale}: height-{height} tower guidance must state {usage:,} FE/t')
    if '240 FE/t' in flow:
        raise ValueError(f'{locale}: obsolete tower power guidance')
print(f'5 languages: height-{height} cooling tower {usage:,} FE/t, {usage * ticks:,} FE / {amount} mB validated')
