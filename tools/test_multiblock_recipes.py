import json
import unittest

import gen_data as gen


class MultiblockPartRecipeTests(unittest.TestCase):
    def test_every_recipe_for_a_structural_part_consumes_its_frame(self):
        frames = {gen.mid(part): gen.mid(frame) for part, frame in gen.PART_FRAMES.items()}
        found = set()
        for path in (gen.DATA / 'recipe').rglob('*.json'):
            recipe = json.loads(path.read_bytes())
            product = recipe.get('result', {}).get('id')
            if product not in frames:
                continue
            with self.subTest(recipe=path.stem):
                frame = {'item': frames[product]}
                if 'pattern' in recipe:
                    required = any(recipe['key'][ch] == frame for row in recipe['pattern'] for ch in row if ch != ' ')
                else:
                    slots = [i for i, part in enumerate(recipe['ingredients'])
                             if part['ingredient'] == frame and part.get('count', 1) >= 1 and not part.get('uses')]
                    if 'shape' in recipe:
                        required = any(chr(ord('A') + i) in ''.join(recipe['shape']) for i in slots)
                    else:
                        required = bool(slots)
                self.assertTrue(required, f'{product} requires {frames[product]}')
                found.add(product)
        self.assertEqual(found, set(frames))
