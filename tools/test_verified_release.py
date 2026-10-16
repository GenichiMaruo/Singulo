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


if __name__ == '__main__':
    unittest.main()
