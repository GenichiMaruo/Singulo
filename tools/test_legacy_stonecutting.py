import unittest
from pathlib import Path
from legacy_data import convert

class LegacyStonecuttingTests(unittest.TestCase):
    def test_preserves_output_and_count(self):
        path, result = convert(Path('data/singulo/recipe/stonecutting/example.json'), {
            'type': 'minecraft:stonecutting', 'ingredient': {'item': 'singulo:white_panel'},
            'result': {'id': 'singulo:white_panel_slab', 'count': 2}})
        self.assertEqual(path.as_posix(), 'data/singulo/recipes/stonecutting/example.json')
        self.assertEqual(result['result'], 'singulo:white_panel_slab')
        self.assertEqual(result['count'], 2)
