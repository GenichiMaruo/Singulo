"""Offline contract tests: drafts may duplicate; unique Git refs may not."""
from pathlib import Path
import tempfile
import unittest
import curseforge_guard as guard


class GuardTests(unittest.TestCase):
    def test_complete_paginated_listing_finds_multiple_drafts_for_same_tag(self):
        calls = []
        def api(endpoint):
            calls.append(endpoint)
            if endpoint.endswith('page=1'):
                return [{'id': i, 'tag_name': 'old'} for i in range(1, 101)]
            return [{'id': 101, 'tag_name': 'v0.15.1', 'draft': True},
                    {'id': 102, 'tag_name': 'v0.15.1', 'draft': True}]
        self.assertEqual([r['id'] for r in guard.releases_for_tag(api, 'v0.15.1')], [101, 102])
        self.assertEqual(len(calls), 2)
        self.assertTrue(all('releases?' in c for c in calls))

    def test_fetch_own_draft_by_id_when_tag_endpoint_would_be_404(self):
        calls = []
        def api(endpoint):
            calls.append(endpoint)
            if endpoint == 'releases/101':
                return {'id': 101, 'tag_name': 'v0.15.1', 'draft': True, 'assets': []}
            if endpoint.startswith('git/ref/'):
                return {'ref': guard.ref_name('v0.15.1'), 'object': {'type': 'commit', 'sha': 'a' * 40}}
            raise AssertionError('GET releases/tags would return 404; must not be called')
        guard.check_owned(api, 'v0.15.1', 'a' * 40, 101)
        self.assertIn('releases/101', calls)

    def test_atomic_ref_not_draft_is_shared_across_local_and_actions(self):
        refs, drafts, calls = {}, [], []
        def api(endpoint, payload):
            calls.append(endpoint)
            if endpoint == 'releases':
                drafts.append(payload)
                return {'id': len(drafts)}  # Same tag can have multiple drafts.
            self.assertEqual(endpoint, 'git/refs')
            if payload['ref'] in refs:
                raise guard.GuardError('422 reference already exists')
            refs[payload['ref']] = payload['sha']
            return {'ref': payload['ref'], 'object': {'type': 'commit', 'sha': payload['sha'], 'url': 'url'}}
        api('releases', {'tag_name': 'v0.15.1'})
        api('releases', {'tag_name': 'v0.15.1'})
        self.assertEqual(len(drafts), 2)
        guard.acquire(api, 'v0.15.1', 'a' * 40)  # Local wins.
        with self.assertRaises(guard.GuardError):
            guard.acquire(api, 'v0.15.1', 'a' * 40)  # Actions loses; no adopt/retry.
        self.assertEqual(calls.count('git/refs'), 2)

    def test_uncertain_ref_create_is_not_retried(self):
        calls = []
        def api(endpoint, payload):
            calls.append(payload)
            raise TimeoutError()
        with self.assertRaises(TimeoutError):
            guard.acquire(api, 'v0.15.1', 'a' * 40)
        self.assertEqual(len(calls), 1)

    def test_revision_does_not_bypass_filename_lock_and_bad_tags_stop(self):
        self.assertEqual(guard.ref_name('v0.15.1-1.20.1F-r2'), guard.ref_name('v0.15.1-1.20.1F'))
        for tag in ('../x', 'v0.15.1;command', 'v0.15.1\n'):
            with self.subTest(tag=tag), self.assertRaises(guard.GuardError):
                guard.ref_name(tag)

    def test_protocol_not_deployed_or_changed_is_not_ready(self):
        root = Path(guard.__file__).resolve().parent.parent
        self.assertTrue(guard.workflow_ready(root))
        with tempfile.TemporaryDirectory() as tmp:
            dest = Path(tmp)
            (dest / 'tools').mkdir()
            (dest / '.github/workflows').mkdir(parents=True)
            self.assertFalse(guard.workflow_ready(dest))
            helper = dest / 'tools/curseforge_guard.py'
            helper.write_bytes(Path(guard.__file__).read_bytes().replace(b'\r\n', b'\n').replace(b'\n', b'\r\n'))
            workflow = dest / '.github/workflows/publish-curseforge.yml'
            (dest / 'gradle.properties').write_bytes((root / 'gradle.properties').read_bytes())
            workflow.write_bytes((root / '.github/workflows/publish-curseforge.yml').read_bytes())
            self.assertTrue(guard.workflow_ready(dest))
            workflow.write_text(workflow.read_text().replace('      - name: Acquire permanent', '      - if: false\n        name: Acquire permanent'))
            self.assertFalse(guard.workflow_ready(dest))
            workflow.write_text('gh release create old\nuses: Kira-NT/mc-publish@old\n')
            self.assertFalse(guard.workflow_ready(dest))

    def test_active_legacy_or_other_publisher_blocks_but_own_run_is_allowed(self):
        def api(endpoint):
            return {'workflow_runs': [{'id': 11}]} if 'in_progress' in endpoint else {'workflow_runs': []}
        guard.no_active_publishers(api, 11)
        with self.assertRaises(guard.GuardError):
            guard.no_active_publishers(api)
        with self.assertRaises(guard.GuardError):
            guard.no_active_publishers(lambda endpoint: {})
        for row in ({}, {'id': None}, {'id': True}, {'id': 0}):
            with self.subTest(row=row), self.assertRaises(guard.GuardError):
                guard.no_active_publishers(lambda endpoint: {'workflow_runs': [row]})


if __name__ == '__main__':
    unittest.main()
