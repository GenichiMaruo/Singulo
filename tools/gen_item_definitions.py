"""Generate 26.1 item definitions after the legacy model/resource generator.

Preserves old threshold predicates and tint layers. The egg artwork is an
original deterministic two-layer pixel drawing, not a bundled Minecraft asset.
"""
from pathlib import Path
import json
import re
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/generated/resources/assets/singulo'

def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

def main():
    eggs = json.loads((ROOT / 'tools/spawn_egg_colors.json').read_text(encoding='utf-8'))
    source = (ROOT / 'src/main/java/io/github/genichimaruo/singulo/registry/SinguloItems.java').read_text(encoding='utf-8')
    accessories = set(re.findall(r'"([a-z_]+)", registryId -> new io.github.genichimaruo.singulo.android.AndroidAccessoryItem', source))
    white = {'type': 'minecraft:constant', 'value': 0xFFFFFF}
    for file in sorted((ASSETS / 'models/item').glob('*.json')):
        name = file.stem
        if name == "colored_spawn_egg" or name.startswith("fluid_capsule_shell_"): continue
        old = json.loads(file.read_text(encoding='utf-8'))
        model = {'type': 'minecraft:model', 'model': 'singulo:item/' + name}
        if name in eggs:
            model['model'] = 'singulo:item/colored_spawn_egg'
            model['tints'] = [{'type': 'minecraft:constant', 'value': c & 0xFFFFFF} for c in eggs[name]]
            # Minecraft's codec consumes signed Java ARGB integers.
            for tint in model['tints']:
                if tint['value'] >= 2**31: tint['value'] -= 2**32
        elif name in ('fluid_sample', 'chemical_sample'):
            model['tints'] = [{'type': 'singulo:dynamic', 'kind': name}]
        elif name in ('fluid_canister', 'food_cartridge'):
            model['tints'] = [white, {'type': 'singulo:dynamic', 'kind': name}]
        elif name in accessories:
            model['tints'] = [{'type': 'singulo:dynamic', 'kind': 'accessory'}]
        if old.get('overrides'):
            entries = []
            for override in old['overrides']:
                if len(override['predicate']) != 1:
                    raise ValueError(f'Explicit combined-predicate migration needed: {file}')
                threshold = next(iter(override['predicate'].values()))
                entries.append({'threshold': threshold, 'model': {'type': 'minecraft:model', 'model': override['model']}})
            model = {'type': 'minecraft:range_dispatch', 'property': 'singulo:dynamic', 'kind': name,
                     'entries': sorted(entries, key=lambda entry: entry['threshold']), 'fallback': model}
        if name in ('fluid_capsule', 'battery_fluid_capsule', 'large_battery_fluid_capsule'):
            tier = ('fluid_capsule', 'battery_fluid_capsule', 'large_battery_fluid_capsule').index(name) + 1
            shell_name = f'fluid_capsule_shell_{tier}'
            shell_file = ASSETS / 'models/item' / (shell_name + '.json')
            shell = json.loads(shell_file.read_text(encoding='utf-8'))
            shell['display'] = old.get('display', {})
            shell['gui_light'] = old.get('gui_light', 'side')
            write(shell_file, shell)
            old['parent'] = 'minecraft:block/block'
            old['elements'] = []
            write(file, old)
            model = {'type': 'minecraft:composite', 'models': [
                {'type': 'minecraft:special', 'base': 'singulo:item/' + name, 'model': {'type': 'singulo:fluid_capsule'}},
                {'type': 'minecraft:model', 'model': 'singulo:item/' + shell_name}]}
        write(ASSETS / 'items' / (name + '.json'), {'model': model})
    write(ASSETS/'models/item/colored_spawn_egg.json', {'parent':'minecraft:item/generated', 'textures':{
        'layer0':'singulo:item/spawn_egg_base','layer1':'singulo:item/spawn_egg_spots'}})
    for lang in (ASSETS / 'lang').glob('*.json'):
        data = json.loads(lang.read_text(encoding='utf-8'))
        if 'key.categories.singulo' in data:
            data['key.category.singulo.singulo'] = data['key.categories.singulo']
            write(lang, data)
    for layer in ('base','spots'):
        image = Image.new('RGBA',(16,16))
        for y in range(2,15):
            half = {2:2,3:3,4:4,5:4,6:5,7:5,8:5,9:5,10:5,11:5,12:4,13:3,14:2}[y]
            for x in range(8-half,8+half):
                shade=max(100, min(255, 250-(x-5)*9-(y-5)*4))
                if layer=='base': image.putpixel((x,y),(shade,shade,shade,255))
                elif (x-5)**2+(y-6)**2<=2 or (x-10)**2+(y-10)**2<=3 or (x-5)**2+(y-12)**2<=1:
                    image.putpixel((x,y),(shade,shade,shade,255))
        target=ASSETS/'textures/item'/('spawn_egg_'+layer+'.png')
        target.parent.mkdir(parents=True,exist_ok=True)
        image.save(target,optimize=True)

if __name__=='__main__':
    main()