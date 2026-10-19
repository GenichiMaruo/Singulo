"""Regression checks for cross-loader artifact selection and release provenance."""
import copy
from pathlib import Path
import unittest
from unittest.mock import patch
import verified_release as release


class UnifiedReleaseTests(unittest.TestCase):
    def setUp(self):
        self.sha = 'a' * 40
        self.tree = 'b' * 40
        self.repo = 'owner/repo'
        jobs = [f'build-and-gametest ({loader}, {ns})'
                for loader in ('forge', 'neoforge') for ns in release.GROUPS]
        jobs += ['optional-mekanism (forge)', 'optional-mekanism (neoforge)']
        self.responses = {
            'actions/runs/1': {'conclusion': 'success', 'status': 'completed',
                              'event': 'pull_request', 'path': '.github/workflows/pr-validation.yml',
                              'head_repository': {'full_name': self.repo},
                              'head_sha': self.sha, 'run_attempt': 1},
            'actions/runs/1/jobs?per_page=100': {'jobs': [
                {'name': name, 'conclusion': 'success'} for name in jobs]},
            'git/ref/tags/v0.8.1-1.20.1F': {'object': {'type': 'commit', 'sha': self.sha}},
            f'git/commits/{self.sha}': {'tree': {'sha': self.tree}},
            'actions/runs/1/artifacts?per_page=100': {'artifacts': [
                {'id': i, 'name': f'pr-validation-{self.sha}-{loader}-{ns}', 'expired': False}
                for i, (loader, ns) in enumerate((loader, ns)
                    for loader in ('forge', 'neoforge') for ns in release.GROUPS)]},
        }

    def inspect(self, loader='forge'):
        with patch.object(release, 'api', side_effect=lambda repo, endpoint: copy.deepcopy(self.responses[endpoint])), \
                patch.object(release, 'git', side_effect=lambda source, *args: self.tree if args[-1] == 'HEAD^{tree}' else self.sha):
            return release.inspect(self.repo, 1, 'v0.8.1-1.20.1F', Path('.'), loader)

    def test_selects_only_requested_loader(self):
        for loader in ('forge', 'neoforge'):
            result = self.inspect(loader)
            self.assertEqual(result['loader'], loader)
            self.assertEqual(set(result['artifacts']), set(release.GROUPS))
            self.assertTrue(all(f'-{loader}-' in value['name'] for value in result['artifacts'].values()))

    def test_missing_loader_cannot_use_other_loader_artifacts(self):
        artifacts = self.responses['actions/runs/1/artifacts?per_page=100']['artifacts']
        artifacts[:] = [a for a in artifacts if '-neoforge-' in a['name']]
        with self.assertRaisesRegex(RuntimeError, 'Expected all required'):
            self.inspect('forge')

    def test_failure_in_either_loader_blocks_release(self):
        self.responses['actions/runs/1/jobs?per_page=100']['jobs'][-1]['conclusion'] = 'failure'
        with self.assertRaisesRegex(RuntimeError, 'All required'):
            self.inspect('forge')

    def test_unreviewed_tree_blocks_release(self):
        self.responses[f'git/commits/{self.sha}']['tree']['sha'] = 'c' * 40
        with self.assertRaisesRegex(RuntimeError, 'tree differs'):
            self.inspect()

    def test_only_integrated_branch_push_is_accepted(self):
        run = self.responses['actions/runs/1']
        run.update(event='push', head_branch='1.20.1')
        self.inspect()
        run['head_branch'] = 'unreviewed-branch'
        with self.assertRaisesRegex(RuntimeError, 'successful PR or 1.20.1'):
            self.inspect()


class ArtifactPromotionTests(unittest.TestCase):
    def test_requires_identical_jars_and_complete_gui_evidence(self):
        import json
        import tempfile
        import zipfile
        for defect in (None, 'jar', 'handbook', 'pixels', 'machine_marker', 'machine_pixels'):
            with self.subTest(defect=defect), tempfile.TemporaryDirectory() as folder:
                root = Path(folder)
                name = 'Singulo-0.10.0-1.20.1F.jar'
                metadata = {'artifacts': {ns: {'name': ns} for ns in release.GROUPS}}
                for index, (ns, count) in enumerate(release.GROUPS.items()):
                    build = root / ns
                    (build / 'libs').mkdir(parents=True)
                    with zipfile.ZipFile(build / 'libs' / name, 'w') as jar:
                        jar.writestr('payload', 'changed' if defect == 'jar' and index == 1 else 'same')
                    logs = build / 'gametest/logs'
                    logs.mkdir(parents=True)
                    (logs / 'latest.log').write_text(f'All {count} required tests passed')
                gui = root / 'singulo/gui-review'
                (gui / 'screenshots').mkdir(parents=True)
                for index in range(142):
                    (gui / 'screenshots' / f'{index}.png').touch()
                (gui / 'logs').mkdir()
                markers = ['SINGULO_GUI_COMPLETE 22', 'SINGULO_SEARCH_COMPLETE', 'SINGULO_JEI_SYNC_COMPLETE']
                if defect != 'handbook':
                    markers.append('SINGULO_HANDBOOK_SEARCH_COMPLETE')
                if defect != 'machine_marker':
                    markers.append('SINGULO_MACHINE_GUI_COMPLETE 120')
                (gui / 'machine-pixels.json').write_text(json.dumps({'languages': ['ja_jp', 'en_us', 'zh_cn', 'zh_tw', 'ko_kr'],
                    'machine_captures': 120, 'headers_without_title_status_overlap': 79 if defect == 'machine_pixels' else 80, 'generator_font_probes': 5}))
                (gui / 'logs/latest.log').write_text('\n'.join(markers))
                (gui / 'inventory-pixels.json').write_text(json.dumps([
                    {'checked_empty_slot_centers': 35}, {'checked_empty_slot_centers': 35},
                    {'checked_empty_slot_centers': 34 if defect == 'pixels' else 35}]))
                output = root / 'release' / name
                if defect is None:
                    release.promote(metadata, root, output, name)
                    self.assertEqual(output.read_bytes(), (root / 'singulo/libs' / name).read_bytes())
                    self.assertEqual(len(metadata['jar_sha256']), 64)
                else:
                    with self.assertRaises(RuntimeError):
                        release.promote(metadata, root, output, name)
                    self.assertFalse(output.exists())


if __name__ == '__main__':
    unittest.main()
