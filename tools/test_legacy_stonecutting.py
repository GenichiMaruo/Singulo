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

    def test_conditional_slab_count_is_not_unconditional_rolls(self):
        conditions = [{'condition': 'minecraft:block_state_property', 'block': 'singulo:lab_panel_slab', 'properties': {'type': 'double'}}]
        _, result = convert(Path('data/singulo/loot_table/blocks/lab_panel_slab.json'), {'pools': [{'rolls': 1,
            'entries': [{'functions': [{'function': 'minecraft:set_count', 'count': 2, 'conditions': conditions}]}]}]})
        self.assertEqual(result['pools'][0]['rolls'], 1)
        self.assertEqual(result['pools'][0]['entries'][0]['functions'][0]['conditions'], conditions)
