"""Regression checks for cross-loader artifact selection and release provenance."""
import copy
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import verified_release as release


class UnifiedReleaseTests(unittest.TestCase):
    def setUp(self):
        self.sha = 'a' * 40
        self.tree = 'b' * 40
        self.repo = 'owner/repo'
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.source = Path(self.tmp.name)
        (self.source / 'gradle.properties').write_text('mod_version=0.8.1\nminecraft_version=1.20.1\nmod_name=Singulo\n')
        jobs = ['build-and-gametest (forge)', 'build-and-gametest (neoforge)',
                'optional-mekanism', 'renderer-lighting']
        self.responses = {
            'actions/runs/1': {'conclusion': 'success', 'status': 'completed',
                              'event': 'pull_request', 'path': '.github/workflows/pr-validation.yml',
                              'head_repository': {'full_name': self.repo},
                              'head_sha': self.sha, 'run_attempt': 1},
            'actions/runs/1/jobs?per_page=100': {'jobs': [
                {'name': name, 'conclusion': 'success'} for name in jobs]},
            'git/ref/tags/v0.8.1-1.20.1F': {'object': {'type': 'commit', 'sha': self.sha}},
            'git/ref/tags/v0.8.1-1.20.1N': {'object': {'type': 'commit', 'sha': self.sha}},
            f'compare/{self.sha}...1.20.1': {'status': 'identical'},
            f'git/commits/{self.sha}': {'tree': {'sha': self.tree}},
            'actions/runs/1/artifacts?per_page=100': {'artifacts': [
                {'id': i, 'name': f'pr-validation-{self.sha}-{loader}-{ns}', 'expired': False}
                for i, (loader, ns) in enumerate((loader, ns)
                    for loader in ('forge', 'neoforge') for ns in ("full",))]},
        }

    def inspect(self, loader='forge', tag=None):
        tag = tag or ('v0.8.1-1.20.1F' if loader == 'forge' else 'v0.8.1-1.20.1N')
        with patch.object(release, 'api', side_effect=lambda repo, endpoint: copy.deepcopy(self.responses[endpoint])), \
                patch.object(release, 'git', side_effect=lambda source, *args: '' if args[0] == 'status' else self.tree if args[-1] == 'HEAD^{tree}' else self.sha):
            return release.inspect(self.repo, 1, tag, self.source, loader)

    def test_wrong_loader_tag_is_rejected_before_api_requests(self):
        with self.assertRaisesRegex(RuntimeError, 'version and loader'):
            self.inspect('neoforge', 'v0.8.1-1.20.1F')

    def test_dirty_tag_checkout_is_rejected_before_api_access(self):
        with patch.object(release, 'git', return_value='?? injected.java'), patch.object(release, 'api') as api:
            with self.assertRaisesRegex(RuntimeError, 'must be clean'):
                release.inspect(self.repo, 1, 'v0.8.1-1.20.1F', self.source, 'forge')
        api.assert_not_called()

    def test_unmerged_tag_is_rejected_but_older_merged_tag_is_allowed(self):
        for status in ('behind', 'diverged'):
            self.responses[f'compare/{self.sha}...1.20.1']['status'] = status
            with self.assertRaisesRegex(RuntimeError, 'maintained 1.20.1 branch'):
                self.inspect()
        self.responses[f'compare/{self.sha}...1.20.1']['status'] = 'ahead'
        self.inspect()

    def test_selects_only_requested_loader(self):
        for loader in ('forge', 'neoforge'):
            result = self.inspect(loader)
            self.assertEqual(result['loader'], loader)
            self.assertEqual(set(result['artifacts']), {"full"})
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
    def test_requires_complete_test_and_gui_evidence(self):
        import json
        import tempfile
        import zipfile
        for defect in (None, 'report', 'handbook', 'pixels', 'machine_marker', 'machine_pixels', 'jar_gate'):
            with self.subTest(defect=defect), tempfile.TemporaryDirectory() as folder:
                root = Path(folder)
                name = 'Singulo-0.10.0-1.20.1F.jar'
                metadata = {'loader': 'forge', 'artifacts': {'full': {'name': 'full'}}}
                source = root / 'source'; source.mkdir()
                (source / 'gradle.properties').write_text('mod_version=0.10.0\nminecraft_version=1.20.1\nmod_name=Singulo\n')
                for index, (ns, count) in enumerate({"full": sum(release.GROUPS.values())}.items()):
                    build = root / ns
                    (build / 'libs').mkdir(parents=True)
                    with zipfile.ZipFile(build / 'libs' / name, 'w') as jar:
                        jar.writestr('payload', 'same')
                    logs = build / 'gametest/logs'
                    logs.mkdir(parents=True)
                    (logs / 'latest.log').write_text(f'All {count} required tests passed')
                from check_gametests import inventory, DEFAULT_NAMESPACES
                names = [e['name'] for e in inventory() if e['namespace'] in DEFAULT_NAMESPACES]
                report = {'schema': 1, 'complete': True, 'expected': names, 'results': [
                    {'name': n, 'status': 'passed', 'required': True, 'millis': 0} for n in names]}
                if defect == 'report': report['results'].pop()
                (root / 'full/reports/gametest').mkdir(parents=True)
                (root / 'full/reports/gametest/results.json').write_text(json.dumps(report))
                gui = root / 'full/gui-review'
                (gui / 'screenshots').mkdir(parents=True)
                for index in range(262):
                    (gui / 'screenshots' / f'{index}.png').touch()
                (gui / 'logs').mkdir()
                markers = ['SINGULO_GUI_COMPLETE 22', 'SINGULO_SEARCH_COMPLETE', 'SINGULO_JEI_SYNC_COMPLETE']
                if defect != 'handbook':
                    markers.append('SINGULO_HANDBOOK_SEARCH_COMPLETE')
                if defect != 'machine_marker':
                    markers.append('SINGULO_MACHINE_GUI_COMPLETE 240')
                (gui / 'machine-pixels.json').write_text(json.dumps({'languages': ['ja_jp', 'en_us', 'zh_cn', 'zh_tw', 'ko_kr'],
                    'machine_captures': 240, 'jet_forge_inventory_slots': 700, 'headers_without_title_status_overlap': 79 if defect == 'machine_pixels' else 80, 'generator_font_probes': 5, 'supply_port_inventory_slots': 350, 'supply_port_tooltips': 5}))
                (gui / 'logs/latest.log').write_text('\n'.join(markers))
                (gui / 'inventory-pixels.json').write_text(json.dumps([
                    {'checked_empty_slot_centers': 35}, {'checked_empty_slot_centers': 35},
                    {'checked_empty_slot_centers': 34 if defect == 'pixels' else 35}]))
                output = root / 'release' / name
                with patch.object(release, 'expected_tests', return_value=names), patch('check_port_1201.validate_jar') as jar_gate:
                    if defect == 'jar_gate': jar_gate.side_effect = RuntimeError('Wrong tagged JAR metadata')
                    if defect is None:
                        release.promote(metadata, root, output, name, source)
                        self.assertEqual(output.read_bytes(), (root / 'full/libs' / name).read_bytes())
                        self.assertEqual(len(metadata['jar_sha256']), 64)
                        jar_gate.assert_called_once_with(root / 'full/libs' / name, source=source)
                    else:
                        with self.assertRaises(RuntimeError):
                            release.promote(metadata, root, output, name, source)
                        self.assertFalse(output.exists())


class TaggedInventoryTests(unittest.TestCase):
    def setUp(self):
        import json
        from check_gametests import DEFAULT_NAMESPACES
        self.tmp = tempfile.TemporaryDirectory(); self.addCleanup(self.tmp.cleanup)
        self.source = Path(self.tmp.name)
        directory = self.source / 'src/gameTest/java'; directory.mkdir(parents=True)
        (self.source / 'tools').mkdir()
        self.names = []
        for index, namespace in enumerate(DEFAULT_NAMESPACES):
            name = f'oldcase{index}'; self.names.append(name)
            (directory / f'Old{index}GameTests.java').write_text(
                f'@GameTestHolder("{namespace}")\n@PrefixGameTestTemplate(false)\n'
                f'class Old{index}GameTests {{ @GameTest(template="empty")\n'
                f'public static void {name}(GameTestHelper h) {{ h.succeed(); }} }}')
        self.groups = {namespace: 1 for namespace in DEFAULT_NAMESPACES}
        (self.source / 'tools/gametest_groups.json').write_text(json.dumps(self.groups))
        (self.source / 'tools/check_gametests.py').write_text('raise AssertionError("tag code executed")')

    def test_old_inventory_is_independent_of_current_workflow_counts(self):
        with patch.object(release, 'GROUPS', {'new_namespace': 9999}):
            self.assertEqual(set(release.expected_tests(self.source)), set(self.names))

    def test_inconsistent_tag_groups_cannot_reduce_required_tests(self):
        import json
        self.groups['singulo'] = 0
        (self.source / 'tools/gametest_groups.json').write_text(json.dumps(self.groups))
        with self.assertRaisesRegex(RuntimeError, 'Tagged test groups'):
            release.expected_tests(self.source)


if __name__ == '__main__':
    unittest.main()
