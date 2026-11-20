import json
from pathlib import Path
import shutil
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch

import gen_data as gen
from assembly_editor import RecipeEditor, make_edit
from assembly_shapes import load_edits
from vanilla_catalog import ITEM_NAMES


class AssemblyEditorTests(unittest.TestCase):
    def setUp(self):
        temp = TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name)
        self.editor = RecipeEditor(self.root)
        previous = gen.RECIPES.copy()
        self.addCleanup(gen.RECIPES.update, previous)

    def test_gauntlet_mirrors_each_material_and_keeps_its_cost(self):
        entry = self.editor.entries['inertial_control_gauntlet']
        rows = [entry['board'][i:i+5] for i in range(0, 25, 5)]
        self.assertTrue(all(row == row[::-1] for row in rows))
        self.assertEqual(rows[2][2], '超伝導コイル')
        for m, n in [('鋼板', 3), ('量子もつれ素子', 2), ('超伝導コイル', 1), ('スライムボール', 2)]:
            self.assertEqual(entry['board'].count(m), n)

    def test_vanilla_catalog_and_unused_ingredients_survive_regeneration(self):
        ids = self.editor.material_ids
        self.assertEqual(len(ITEM_NAMES), 1332)
        self.assertTrue(ITEM_NAMES.keys() <= set(ids.values()))
        self.assertNotIn('minecraft:air', self.editor.palette)
        self.assertNotIn('minecraft:water', self.editor.palette)
        self.assertEqual(ids['鉄インゴット'], '#forge:ingots/iron')
        self.assertEqual(ids['minecraft:iron_ingot'], 'minecraft:iron_ingot')
        rid = 'inertial_control_gauntlet'
        before = self.editor.entries[rid]
        base = gen.RECIPES[before['name']]
        cells = before['board'].copy()
        cells[1] = cells[3] = 'minecraft:nautilus_shell'
        cells[2] = 'minecraft:oak_log'
        self.editor.save(rid, cells, before['revision'])
        gen.RECIPES[before['name']] = base
        load_edits(gen.RECIPES, self.editor.edits)
        reopened = RecipeEditor(self.root)
        self.assertEqual(reopened.entries[rid]['board'], cells)
        self.assertEqual(reopened.catalog()['vanillaNames'][cells[1]], 'オウムガイの殻')
        runtime = gen.recipe_files(write=False)[0]['precision_assembler/' + rid]
        self.assertEqual(runtime, json.loads((self.editor.data / (rid + '.json')).read_bytes()))
        for item, count in [('minecraft:nautilus_shell', 2), ('minecraft:oak_log', 1)]:
            self.assertIn({'ingredient': {'item': item}, 'count': count}, runtime['ingredients'])
        with self.assertRaises(ValueError):
            reopened.save(rid, [*cells[:1], 'minecraft:made_up_material', *cells[2:]], reopened.entries[rid]['revision'])

    def test_exact_vanilla_item_cannot_shadow_a_tag_recipe(self):
        entry = self.editor.entries['android_tank_upgrade']
        cells = ['minecraft:glass' if m == 'ガラス' else m for m in entry['board']]
        self.assertNotEqual(cells, entry['board'])
        rid = 'inertial_control_gauntlet'
        current = self.editor.entries[rid]
        with self.assertRaisesRegex(ValueError, entry['name']):
            self.editor.save(rid, cells, current['revision'])

    def test_generator_rejects_tag_overlap_in_complete_and_partial_boards(self):
        target = self.editor.entries['android_tank_upgrade']
        rid = 'inertial_control_gauntlet'
        current = self.editor.entries[rid]
        cells = ['minecraft:glass' if m == 'ガラス' else m for m in target['board']]
        self.editor.edits.mkdir(parents=True)
        for extra in ('', 'minecraft:oak_log'):
            with self.subTest(extra=extra), patch.dict(gen.RECIPES):
                cells[1] = extra
                edit = make_edit(cells, self.editor.palette, current['parts'])
                (self.editor.edits / (rid + '.json')).write_text(json.dumps(edit), encoding='utf-8')
                load_edits(gen.RECIPES, self.editor.edits)
                self.assertTrue(any(target['name'] in error and current['name'] in error
                                    for error in gen.validate()))

    def test_controller_requires_a_part_from_its_own_structure(self):
        rid = 'degenerate_compactor_controller'
        entry = self.editor.entries[rid]
        parts = gen.CONTROLLER_PARTS[entry['name']]
        board = ['C空洞フレーム' if m in parts else m for m in entry['board']]
        with self.assertRaisesRegex(ValueError, '設備の構成部品'):
            self.editor.save(rid, board, entry['revision'])
        self.assertEqual(self.editor.entry(rid), entry)
        self.assertFalse(list(self.root.rglob('*.json')))
        for folder in ['src/generated/resources/assets/singulo/lang', 'tools/locales']:
            shutil.copytree(gen.ROOT / folder, self.root / folder)
        alternate = ['縮退圧縮炉装甲板' if m in parts else m for m in entry['board']]
        self.assertEqual(self.editor.save(rid, alternate, entry['revision'])['board'], alternate)

    def test_window_requires_the_frame_for_its_own_machine(self):
        rid = 'casimir_cavity_window'
        entry = self.editor.entries[rid]
        for replacement in ('', '縮退圧縮炉フレーム'):
            with self.subTest(replacement=replacement):
                board = [replacement if m == 'C空洞フレーム' else m for m in entry['board']]
                with self.assertRaisesRegex(ValueError, 'C空洞フレーム'):
                    self.editor.save(rid, board, entry['revision'])
                self.assertEqual(self.editor.entry(rid), entry)

    def test_save_reloads_in_generator_with_counts_catalyst_and_translations(self):
        for folder in ['src/generated/resources/assets/singulo/lang', 'tools/locales']:
            shutil.copytree(gen.ROOT / folder, self.root / folder)
        rid = 'casimir_cavity_frame'
        entry = self.editor.entries[rid]
        original = gen.RECIPES[entry['name']]
        cells = entry['board'].copy()
        cells[1] = cells[3] = '基礎回路'
        saved = self.editor.save(rid, cells, entry['revision'])
        self.assertEqual(saved['board'], cells)
        self.assertEqual(saved['catalysts'], entry['catalysts'])
        with self.assertRaisesRegex(ValueError, '別の編集'):
            self.editor.save(rid, entry['board'], entry['revision'])
        gen.RECIPES[entry['name']] = original
        load_edits(gen.RECIPES, self.editor.edits)
        self.assertEqual(gen.RECIPES[entry['name']]['inputs']['基礎回路'], 2)
        self.assertEqual(gen.validate(), [])
        runtime = gen.recipe_files(write=False)[0]['precision_assembler/' + rid]
        self.assertEqual(runtime, json.loads((self.editor.data / (rid + '.json')).read_bytes()))
        self.assertTrue(list(self.editor.pictures.glob('*.png')))
        lang = self.root / 'src/generated/resources/assets/singulo/lang'
        key = 'uses.singulo.basic_circuit'
        en = json.loads((lang / 'en_us.json').read_bytes())[key]
        for locale in ['zh_cn', 'zh_tw', 'ko_kr']:
            entry = json.loads((self.root / f'tools/locales/{locale}.json').read_bytes())[key]
            self.assertEqual(entry, {'source': en, 'translation': json.loads((lang / f'{locale}.json').read_bytes())[key]})

    def test_invalid_overlap_or_partial_write_preserves_previous_state(self):
        rid = 'inertial_control_gauntlet'
        current = self.editor.entries[rid]
        board = current['board'].copy()
        for invalid in [['']*25, ['鋼板', *board[1:]], self.editor.entries['android_kit']['board']]:
            with self.assertRaises(ValueError):
                self.editor.save(rid, invalid, current['revision'])
        board[1] = '鋼板'
        replace = Path.replace

        def fail_on_image(source, target):
            if target.suffix == '.png':
                raise OSError('write failed')
            return replace(source, target)

        with patch.object(Path, 'replace', fail_on_image), self.assertRaises(OSError):
            self.editor.save(rid, board, current['revision'])
        self.assertEqual(self.editor.entry(rid), current)
        self.assertFalse(list(self.root.rglob('*.json')))
