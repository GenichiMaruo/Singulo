"""Creative inventory classifications must remain complete as content grows."""
import re
import unittest
from unittest.mock import patch

import building_variants
import gen_data as data


MULTIBLOCKS = {
    'cooling_tower_controller': 'cooling_tower_',
    'degenerate_compactor_controller': 'degenerate_compactor_',
    'casimir_cavity_controller': 'casimir_cavity_',
    'degenerate_furnace_controller': 'degenerate_furnace_',
    'shield_tower_core': 'shield_tower_',
    'tipler_core': 'tipler_',
    'wormhole_generator_core': 'wormhole_generator_',
    'genesis_core': 'genesis_',
    'jet_forge_core': 'jet_forge_',
}


class CreativeTabOrderTests(unittest.TestCase):
    def setUp(self):
        self.order = data.tab_order()
        self.groups = dict(data.TAB_GROUPS)
        self.kinds = {i: kind for i, _, kind in data.MOD.values()}

    def contiguous(self, ids):
        indices = sorted(self.order.index(i) for i in ids)
        self.assertEqual(indices, list(range(indices[0], indices[-1] + 1)))

    def span(self, *titles):
        # 建材の階段・ハーフブロック・塀は、元の建材と同じまとまりに入る
        ids = [i for t in titles for i in self.groups[t]]
        ids += [v for v, (base, _shape) in building_variants.VARIANTS.items() if base in ids]
        self.contiguous(ids)
        return ids

    def test_all_content_and_spawn_eggs_appear_once(self):
        content = {i for i, _, kind in data.MOD.values() if kind not in ('fluid', 'structure')
                   and i not in data.ITEMLESS_BLOCKS and i not in data.TAB_EXCLUDED}
        eggs = {'security_drone_spawn_egg', 'blade_android_spawn_egg', 'whip_android_spawn_egg',
                'saber_android_spawn_egg', 'axe_android_spawn_egg', 'bit_android_spawn_egg',
                'gunner_android_spawn_egg'}
        self.assertEqual(set(self.order), content | eggs)
        self.assertFalse([i for i in self.order if i.endswith('_spawn_egg') and i[:-len('_spawn_egg')] in
                          ('horizon_warden', 'echo_sentinel', 'gravity_remnant', 'ergosphere_leviathan',
                           'last_archivist', 'foundry_overseer')])  # Boss eggs are command-only.
        self.assertEqual(len(self.order), len(set(self.order)))
        self.assertEqual(set(self.order[-len(eggs):]), eggs)
        self.assertNotIn('strangelet', self.order)  # World hazard; no registered item.
        self.assertNotIn('unstable_wormhole_mouth', self.order)  # Vanishes when taken; only useful as a pair.

    def test_machines_come_before_materials(self):
        devices = self.span('発電と蓄電', '配線と配管', '加工機', '機械のアップグレード', 'マルチブロック',
                            '重力と宇宙の設備', 'ワームホール')
        machines = {i for i, kind in self.kinds.items() if kind in ('machine', 'gravity', 'genesis')}
        machines -= {'creative_energy_source', 'food_printer', 'rgb_dye_station'}
        machines -= {i for i in machines if 'android' in i or 'drone' in i or i == 'horizon_vault'}
        self.assertTrue(machines <= set(devices), machines - set(devices))
        self.assertLess(max(self.order.index(i) for i in devices), self.order.index('steel_ingot'))

    def test_multiblock_parts_follow_their_controller(self):
        for controller, prefix in MULTIBLOCKS.items():
            ids = [i for i in self.order if i.startswith(prefix) and not i.endswith('_spawn_egg')]
            self.assertEqual(ids[0], controller)
            self.contiguous(ids)
        part_kinds = {i for i, kind in self.kinds.items()
                      if kind in ('part_block', 'part_glass') and i not in data.ITEMLESS_BLOCKS}
        self.assertTrue(part_kinds <= set(self.groups['マルチブロック']))

    def test_android_and_drone_content_stays_together(self):
        android = set(self.span('アンドロイド', 'アンドロイドのアップグレード', 'アンドロイド装飾'))
        building = {'android_barrier_glass', 'android_barrier_glass_patterned', 'android_gate_glass',
                    'android_gate_glass_patterned', 'android_capsule'}
        for iid in self.order:
            if iid.startswith('android_') and iid not in building:
                self.assertIn(iid, android)
        drone = set(self.groups['ドローン'])
        for iid in self.order:
            if 'drone' in iid and not iid.endswith('_spawn_egg'):
                self.assertIn(iid, drone)
        self.span('アンドロイド装飾', 'ドローン')

    def test_ingots_and_plates_have_separate_contiguous_groups(self):
        ingots = {i for i, _, _ in data.MOD.values() if i.endswith('_ingot')} | {'collapsar_billet'}
        self.assertEqual(set(self.groups['インゴット']), ingots)
        self.contiguous(ingots)
        self.contiguous(['steel_plate', 'nihonium_plate', 'phase_sync_plate'])
        self.assertLess(self.order.index('collapsar_ingot'), self.order.index('steel_plate'))

    def test_intermediate_materials_hold_only_plain_items(self):
        start = self.order.index(self.groups['圧縮素材'][-1]) + 1
        end = self.order.index(self.groups['白い建材'][0])
        middle = self.order[start:end]
        self.assertTrue({'steel_blend', 'basic_frame', 'nuclear_pasta', 'tidal_filament'} <= set(middle))
        self.assertTrue(all(self.kinds[i] in ('item', 'planned') for i in middle))
        self.assertFalse({'android_kit', 'drone_kit', 'drone_memory', 'android_memory_blade'} & set(middle))
        self.span('白い建材', '遺構の建材', '研究所の建材と調度品', '設計図')

    def test_degraded_salvage_follows_restored_items_in_the_same_order(self):
        restored = [data.MOD[n][0] for n in data.RESTORED]
        degraded = [data.MOD[n][0] for n in data.RESTORED.values()]
        self.contiguous(restored + degraded)
        self.assertEqual(self.order[self.order.index(restored[0]):][:len(restored) * 2], restored + degraded)

    def test_awakening_cores_follow_boss_order(self):
        cores = [i for i in self.order if i.startswith('awakening_')]
        self.assertEqual(cores, ['awakening_echo_core', 'awakening_gravity_core', 'awakening_foundry_core',
                                 'awakening_record_core', 'awakening_ergosphere_core'])
        last = self.order.index('awakening_ergosphere_core')
        self.assertEqual(self.order[last + 1:last + 7],
                         ['echo_chapter', 'gravity_chapter', 'horizon_chapter', 'ergosphere_chapter',
                          'terminus_record', 'archivist_halo'])

    def test_cartridges_and_reactor_equipment_sit_with_their_multiblock(self):
        tower = self.order.index('cooling_tower_heat_exchange_core')
        self.assertEqual(self.order[tower + 1:tower + 4],
                         ['empty_coolant_cartridge', 'coolant_cartridge', 'helium_cartridge'])
        alarm = self.order.index('reactor_mass_alarm')
        self.assertEqual(set(self.order[alarm + 1:alarm + 8]),
                         {'gravity_field_stabilizer', 'confinement_ring', 'singularity_seed', 'mass_pellet',
                          'jet_collector', 'hawking_collector', 'ergosphere_ring'})

    def test_building_variants_follow_their_base(self):
        for base in {b for b, _ in building_variants.VARIANTS.values()}:
            i = self.order.index(base)
            shapes = [s for b, s in building_variants.VARIANTS.values() if b == base]
            self.assertEqual([v.rsplit('_', 1)[1] for v in self.order[i + 1:i + 1 + len(shapes)]], shapes, base)

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
