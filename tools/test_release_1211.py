"""Release provenance must reject mixed trees, missing artifacts and failed validation (1.21.1 is NeoForge only from 0.9.0)."""
import copy
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import verified_release_1211 as release


class ProvenanceTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.source = Path(self.tmp.name)
        (self.source / 'gradle.properties').write_text('mod_version=0.8.1\nminecraft_version=1.21.1\nmod_name=Singulo\n')
        self.sha, self.tested, self.tree = 'a' * 40, 'b' * 40, 'c' * 40
        self.data = {
            'actions/runs/1': {'status': 'completed', 'conclusion': 'success', 'event': 'pull_request',
                              'path': '.github/workflows/pr-validation.yml', 'head_repository': {'full_name': 'owner/repo'},
                              'head_sha': self.sha, 'run_attempt': 1},
            'actions/runs/1/jobs?per_page=100': {'jobs': [{'name': n, 'conclusion': 'success'} for n in ['build-and-gametest', 'optional-mekanism']]},
            'git/ref/tags/v0.8.1': {'object': {'type': 'commit', 'sha': self.sha}},
            'git/commits/' + self.sha: {'tree': {'sha': self.tree}},
            'git/commits/' + self.tested: {'tree': {'sha': self.tree}},
            'actions/runs/1/artifacts?per_page=100': {'artifacts': [
                {'id': i, 'name': n + '-validation-' + self.tested, 'expired': False} for i, n in enumerate(['pr'])]},
        }

    def inspect(self):
        with patch.object(release, 'api', side_effect=lambda repo, endpoint: copy.deepcopy(self.data[endpoint])), \
             patch.object(release, 'git', side_effect=lambda source, *args: self.tree if args[-1] == 'HEAD^{tree}' else self.sha):
            return release.inspect('owner/repo', 1, 'v0.8.1', self.source)

    def test_neoforge_selected_from_actual_tested_commit(self):
        m = self.inspect()
        self.assertEqual(set(m['artifacts']), {'neoforge'})
        self.assertEqual(m['tested_commit'], self.tested)

    def test_mekanism_failure_blocks_upload(self):
        self.data['actions/runs/1/jobs?per_page=100']['jobs'][-1]['conclusion'] = 'failure'
        with self.assertRaisesRegex(RuntimeError, 'Mekanism validation'):
            self.inspect()

    def test_leftover_forge_job_is_rejected(self):
        self.data['actions/runs/1/jobs?per_page=100']['jobs'].append({'name': 'forge', 'conclusion': 'success'})
        with self.assertRaisesRegex(RuntimeError, 'Mekanism validation'):
            self.inspect()

    def test_missing_artifact_is_rejected(self):
        self.data['actions/runs/1/artifacts?per_page=100']['artifacts'].pop()
        with self.assertRaisesRegex(RuntimeError, 'NeoForge artifact'):
            self.inspect()

    def test_actual_merge_tree_must_match_release(self):
        self.data['git/commits/' + self.tested]['tree']['sha'] = 'd' * 40
        with self.assertRaisesRegex(RuntimeError, 'Actual tested merge tree'):
            self.inspect()

    def test_wrong_version_is_rejected_before_api_access(self):
        (self.source / 'gradle.properties').write_text('mod_version=0.8.2\nminecraft_version=1.21.1\nmod_name=Singulo\n')
        with self.assertRaisesRegex(RuntimeError, 'Tag must match'):
            self.inspect()


class PromotionTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.base = Path(self.tmp.name)
        self.root = self.base / 'artifacts' / 'validated'
        self.gui = self.root / 'gui-review'
        (self.gui / 'logs').mkdir(parents=True)
        (self.gui / 'screenshots').mkdir()
        (self.root / 'libs').mkdir()
        self.jar = 'Singulo-0.10.0-1.21.1N.jar'
        (self.root / 'libs' / self.jar).write_bytes(b'the exact validated jar')
        self.metadata = {'mod_name': 'Singulo', 'version': '0.10.0',
                         'artifacts': {'neoforge': {'name': 'validated'}}}
        self.markers = 'SINGULO_GUI_COMPLETE 22\nSINGULO_MACHINE_GUI_COMPLETE 120\nSINGULO_SEARCH_COMPLETE\nSINGULO_HANDBOOK_SEARCH_COMPLETE\nSINGULO_JEI_SYNC_COMPLETE\n'
        (self.gui / 'logs/latest.log').write_text(self.markers)
        (self.gui / 'inventory-pixels.json').write_text(json.dumps([{'checked_empty_slot_centers': 35}] * 3))
        self.machine = {'languages': ['ja_jp', 'en_us', 'zh_cn', 'zh_tw', 'ko_kr'], 'machine_captures': 120,
                        'headers_without_title_status_overlap': 80, 'generator_font_probes': 5}
        (self.gui / 'machine-pixels.json').write_text(json.dumps(self.machine))
        for i in range(142):
            (self.gui / 'screenshots' / f'{i}.png').touch()

    def promote(self):
        with patch.object(release, 'validate'):
            return release.promote(self.metadata, self.base / 'artifacts', self.base / 'output')

    def test_full_gui_evidence_promotes_exact_validated_bytes(self):
        self.promote()
        self.assertEqual((self.base / 'output' / self.jar).read_bytes(), b'the exact validated jar')

    def test_legacy_gui_completion_is_not_enough(self):
        (self.gui / 'logs/latest.log').write_text(self.markers.replace('SINGULO_MACHINE_GUI_COMPLETE 120\n', ''))
        with self.assertRaisesRegex(RuntimeError, 'GUI/search validation'):
            self.promote()

    def test_incomplete_machine_pixel_evidence_blocks_promotion(self):
        self.machine['headers_without_title_status_overlap'] = 79
        (self.gui / 'machine-pixels.json').write_text(json.dumps(self.machine))
        with self.assertRaisesRegex(RuntimeError, 'machine PNG verification'):
            self.promote()


if __name__ == '__main__':
    unittest.main()
