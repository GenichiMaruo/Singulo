"""Creative inventory classifications must remain complete as content grows."""
import re
import unittest
from unittest.mock import patch

import gen_data as data


class CreativeTabOrderTests(unittest.TestCase):
    def setUp(self):
        self.order = data.tab_order()
        self.groups = dict(data.TAB_GROUPS)

    def contiguous(self, ids):
        indices = sorted(self.order.index(i) for i in ids)
        self.assertEqual(indices, list(range(indices[0], indices[-1] + 1)))

    def test_all_content_and_spawn_eggs_appear_once(self):
        content = {i for i, _, kind in data.MOD.values() if kind not in ('fluid', 'structure') and i not in data.ITEMLESS_BLOCKS}
        eggs = {'security_drone_spawn_egg', 'blade_android_spawn_egg', 'whip_android_spawn_egg',
                'saber_android_spawn_egg', 'axe_android_spawn_egg', 'bit_android_spawn_egg',
                'gunner_android_spawn_egg', 'horizon_warden_spawn_egg', 'echo_sentinel_spawn_egg',
                'gravity_remnant_spawn_egg', 'ergosphere_leviathan_spawn_egg',
                'last_archivist_spawn_egg', 'foundry_overseer_spawn_egg'}
        self.assertEqual(set(self.order), content | eggs)
        self.assertEqual(len(self.order), len(set(self.order)))
        self.assertEqual(set(self.order[-len(eggs):]), eggs)
        self.assertNotIn('strangelet', self.order)  # World hazard; no registered item.

    def test_ingots_and_plates_have_separate_contiguous_groups(self):
        ingots = {i for i, _, _ in data.MOD.values() if i.endswith('_ingot')} | {'collapsar_billet'}
        self.assertEqual(set(self.groups['インゴット']), ingots)
        self.contiguous(ingots)
        self.contiguous(['steel_plate', 'nihonium_plate', 'phase_sync_plate'])
        self.assertLess(self.order.index('collapsar_ingot'), self.order.index('steel_plate'))

    def test_all_devices_are_together_with_conduits_adjacent(self):
        machines = {i for i, _, kind in data.MOD.values() if kind in ('machine', 'gravity', 'genesis')}
        machines -= set(self.groups['配線と配管']) | {'creative_energy_source'}
        self.assertTrue(machines <= set(self.groups['装置']))
        self.contiguous(self.groups['装置'] + self.groups['配線と配管'])
        for late_device in ('food_printer', 'rgb_dye_station', 'horizon_vault', 'wormhole_drone_beacon'):
            self.assertIn(late_device, self.groups['装置'])

    def test_building_and_machine_parts_do_not_leak_into_materials(self):
        start = self.order.index(self.groups['圧縮素材'][-1]) + 1
        end = self.order.index(self.groups['触媒'][0])
        kinds = {i: kind for i, _, kind in data.MOD.values()}
        self.assertTrue({'steel_blend', 'basic_frame', 'nuclear_pasta', 'tidal_filament'} <= set(self.order[start:end]))
        self.assertTrue(all(kinds[i] in ('item', 'planned') for i in self.order[start:end]))
        self.contiguous(self.groups['建材'])
        self.contiguous(self.groups['マルチブロック部材'])

    def test_new_unclassified_device_stops_generation(self):
        with patch.dict(data.MOD, {'Test device': ('test_device', 'Test device', 'machine')}):
            with self.assertRaisesRegex(ValueError, 'Classify test_device'):
                data.tab_order()

    def test_duplicate_classification_stops_generation(self):
        with patch.object(data, 'TAB_GROUPS', data.TAB_GROUPS + [('duplicate', ['steel_ingot'])]):
            with self.assertRaisesRegex(ValueError, 'Duplicate'):
                data.tab_order()

    def test_checked_in_java_matches_generator(self):
        java = (data.JAVA / 'GeneratedContent.java').read_text(encoding='utf-8')
        block = java.split('TAB_ORDER = List.of(', 1)[1].split(');', 1)[0]
        self.assertEqual(re.findall(r'"([a-z0-9_]+)"', block), self.order)


if __name__ == '__main__':
    unittest.main()
