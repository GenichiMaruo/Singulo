"""Guard the illustrated handbook against stale assets and changed steel recipe yields."""
import json
from collections import Counter
from pathlib import Path
import unittest

from guide import CHAPTERS
from guide_visuals import visuals_for

ROOT = Path(__file__).resolve().parents[1]
GENERATED = ROOT / 'src/generated/resources'
ASSETS = GENERATED / 'assets/singulo'


def read(path):
    return json.loads(path.read_text(encoding='utf-8'))


class HandbookVisualTests(unittest.TestCase):
    def test_discovery_gates_resolve_and_keep_hidden_topics_out_of_the_initial_book(self):
        visuals = visuals_for(CHAPTERS)
        advances = {a['id'] for a in read(ASSETS / 'guide/advancements.json')['advancements']}
        for page, info in visuals.items():
            with self.subTest(page=page):
                self.assertIn(info.get('stage', 1), range(1, 6))
                for gate in info.get('discoveries', []):
                    if gate.startswith('item/'):
                        self.assertTrue((ASSETS / 'models/item' / (gate[5:] + '.json')).is_file(), gate)
                    else:
                        self.assertIn(gate, advances)
        initial = {page for page, info in visuals.items() if info.get('stage', 1) == 1 and not info.get('discoveries')}
        self.assertIn('ruins.list', initial)
        self.assertIn('stage1.power', initial)
        for page in initial:
            self.assertNotIn(page.split('.')[0], {'androids', 'genesis', 'stage2', 'stage3', 'stage4', 'stage5'})
        for page in ('androids.build', 'androids.work', 'androids.trouble'):
            gates = visuals[page]['discoveries']
            self.assertTrue(all(gate.removeprefix('item/').startswith('android_') for gate in gates))

    def test_all_published_topics_and_illustrations_resolve(self):
        book = read(ASSETS / 'guide/chapters.json')
        visuals = book['visuals']
        self.assertEqual(visuals, visuals_for(CHAPTERS))
        self.assertEqual(set(visuals), {p for c in book['chapters'] for p in c['pages']})
        models = {p.stem for base in (ASSETS, ROOT / 'src/main/resources/assets/singulo')
                  for p in (base / 'models/item').glob('*.json')}
        langs = [read(path) for path in (ASSETS / 'lang').glob('*.json')]
        for page, info in visuals.items():
            with self.subTest(page=page):
                items = [info['icon']]
                keys = [info[k] for k in ('action', 'caption') if k in info]
                for row in info.get('rows', []):
                    keys.append(row['title'])
                    for token in row['inputs'] + row.get('outputs', []):
                        if 'item' in token:
                            items.append(token['item'])
                        elif token['fluid'].startswith('singulo:'):
                            keys.append('fluid_type.singulo.' + token['fluid'].split(':')[1])
                for item in items:
                    if item.startswith('singulo:'):
                        self.assertIn(item.split(':')[1], models)
                for key in keys:
                    if '.singulo.' in key:
                        self.assertTrue(all(key in lang for lang in langs), key)

    def test_steel_comparison_matches_machine_and_crafting_recipes(self):
        recipes = GENERATED / 'data/singulo/recipes'
        electro = read(recipes / 'electrolyzer/hydrogen.json')
        kiln = read(recipes / 'kiln/steel_ingot_from_oxygen.json')
        blend = read(recipes / 'crafting/steel_blend.json')
        blast = read(recipes / 'blasting/steel_ingot.json')
        split, oxygen, ordinary = read(ASSETS / 'guide/chapters.json')['visuals']['stage1.steel']['rows']

        def amounts(tokens):
            return {t.get('item') or t['fluid']: int(t['amount'].split()[0].replace(',', '')) for t in tokens}

        water, = electro['fluid_ingredients']
        batches, remainder = divmod(amounts(split['inputs'])[water['fluid']], water['amount'])
        self.assertEqual(remainder, 0)
        gases = {t['id']: t['amount'] * batches for t in electro['fluid_results']}
        self.assertEqual(amounts(split['outputs']), gases)
        oxidant, = kiln['fluid_ingredients']
        batches, remainder = divmod(gases[oxidant['fluid']], oxidant['amount'])
        self.assertEqual(remainder, 0)
        representatives = {'forge:ingots/iron': 'minecraft:iron_ingot', 'singulo:carbon': 'minecraft:coal'}
        ingredients = {representatives[i['ingredient']['tag']]: i['count'] * batches for i in kiln['ingredients']}
        ingredients[oxidant['fluid']] = gases[oxidant['fluid']]
        self.assertEqual(amounts(oxygen['inputs']), ingredients)
        product = {kiln['result']['id']: kiln['result']['count'] * batches}
        self.assertEqual(amounts(oxygen['outputs']), product)
        self.assertEqual(amounts(ordinary['outputs']), product)
        self.assertEqual(blast['ingredient']['item'], blend['result']['item'])
        batches, remainder = divmod(product[blast['result']['item']], blast['result']['count'] * blend['result']['count'])
        self.assertEqual(remainder, 0)
        materials = Counter(representatives[t['tag']] for t in blend['ingredients'])
        self.assertEqual(amounts(ordinary['inputs']), {k: v * batches for k, v in materials.items()})


if __name__ == '__main__':
    unittest.main()
