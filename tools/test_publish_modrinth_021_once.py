"""Offline regressions for the one-off Singulo 0.21.0 Modrinth transaction."""
from contextlib import redirect_stdout
import hashlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import publish_modrinth_021_once as mod


class OneOffTests(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        self.receipt = self.root / 'receipt.jsonl'
        self.env = patch.dict(mod.os.environ, {
            'MODRINTH_TOKEN': 'fixture-upload',
            'MODRINTH_PROJECT_ID': mod.PROJECT,
        }, clear=True)
        self.env.start()
        self.addCleanup(self.env.stop)
        fixtures = [f'fixture-{i}'.encode() for i in range(4)]
        self.builds = tuple(mod.Build(b.game, b.loader, hashlib.sha512(fixture).hexdigest())
                            for b, fixture in zip(mod.BUILDS, fixtures))
        p = patch.object(mod, 'BUILDS', self.builds)
        p.start()
        self.addCleanup(p.stop)
        for build, fixture in zip(self.builds, fixtures):
            (self.root / build.name).write_bytes(fixture)
        self.versions = []
        self.posts = []
        self.status = 'processing'
        self.project_ids = None
        self.supported = {'1.21.1', '1.20.1', '26.1.2'}
        self.private_read = True
        self.anonymous_version_200 = False
        self.timeout = False

    def listed(self, build, **changes):
        return mod.payload(build, 'fixture notes') | {
            'id': 'fixture' + str(self.builds.index(build)),
            'files': [{'primary': True, 'filename': build.name, 'hashes': {'sha512': build.sha512}}],
        } | changes

    def api(self, path, data=None, headers=None):
        if path in (mod.PROJECT_ROUTE, mod.VERSION_ROUTE):
            if not headers or not self.private_read:
                if path == mod.VERSION_ROUTE and self.anonymous_version_200:
                    return []
                raise RuntimeError('Modrinth HTTP 404; inspect the release before retrying')
            if path == mod.VERSION_ROUTE:
                return list(self.versions)
            return {'id': mod.PROJECT, 'slug': 'singulo', 'project_type': 'mod',
                    'status': self.status, 'versions': self.project_ids if self.project_ids is not None
                    else [v['id'] for v in self.versions]}
        if path == '/tag/game_version':
            return [{'version': x} for x in self.supported]
        if path == '/tag/loader':
            return [{'name': 'forge'}, {'name': 'neoforge'}]
        if path != '/version':
            raise AssertionError(f'Unexpected API endpoint: {path}')
        self.assertEqual(headers['Authorization'], 'fixture-upload')
        sent = json.loads(data.split(b'\r\n\r\n')[1].split(b'\r\n--')[0])
        b = self.builds[len(self.posts)]
        self.assertEqual(sent, mod.payload(b, 'fixture notes'))
        self.assertIn((self.root / b.name).read_bytes(), data)
        self.assertEqual(self.states()[-1], 'submission_started_outcome_uncertain')
        self.posts.append(b.number)
        if self.timeout:
            raise TimeoutError('simulated ambiguous POST')
        result = self.listed(b)
        self.versions.append(result)
        return result

    def dispatch(self, execute=False):
        with patch.object(mod, 'request', side_effect=self.api), redirect_stdout(io.StringIO()):
            mod.run(mod.stage(self.root), 'fixture notes', self.receipt, execute=execute)

    def states(self):
        return [json.loads(row)['state'] for row in self.receipt.read_text().splitlines()]

    def test_dry_run_no_posts_and_all_four_exact_targets(self):
        self.dispatch()
        self.assertEqual(self.posts, [])
        self.assertEqual(self.states(), ['dry_run_not_submitted'] * 4)

    def test_execute_serial_positive_readback(self):
        self.dispatch(execute=True)
        self.assertEqual(self.posts, [b.number for b in self.builds])
        self.assertEqual(self.states(),
                         ['submission_started_outcome_uncertain', 'accepted_listed'] * 4)

    def test_already_listed_four_skips_every_post(self):
        self.versions = [self.listed(b) for b in self.builds]
        self.dispatch(execute=True)
        self.assertEqual(self.posts, [])
        self.assertEqual(self.states(), ['already_listed'] * 4)

    def test_partial_batch_conflict_duplicate_missing_auth_and_changed_inventory(self):
        b = self.builds[0]
        cases = [
            ([self.listed(b)], 'Partial'),
            ([self.listed(b, status='draft')], 'conflicts'),
            ([self.listed(b, game_versions=['1.21'])], 'conflicts'),
            ([self.listed(b, version_number='0.21.0')], 'Unexpected existing'),
            ([self.listed(b), self.listed(b, id='duplicat')], 'Ambiguous'),
            ([self.listed(b, files=[])], 'conflicts'),
        ]
        for versions, expected in cases:
            with self.subTest(expected=expected):
                self.versions = versions
                with self.assertRaisesRegex(RuntimeError, expected):
                    self.dispatch(execute=True)
                self.assertEqual(self.posts, [])
        self.versions = []
        self.private_read = False
        with self.assertRaisesRegex(RuntimeError, '404'):
            self.dispatch(execute=True)
        self.private_read = True
        self.project_ids = ['missing1']
        with self.assertRaisesRegex(RuntimeError, 'inventories disagree'):
            self.dispatch(execute=True)
        self.assertEqual(self.posts, [])

    def test_ambiguous_post_never_retries_or_submits_later_builds(self):
        self.timeout = True
        with self.assertRaises(TimeoutError):
            self.dispatch(execute=True)
        self.assertEqual(self.posts, [self.builds[0].number])
        self.assertEqual(self.states(), ['submission_started_outcome_uncertain'])

    def test_reject_project_state_digests_unavailable_versions_and_anonymous_fallback(self):
        self.status = 'approved'
        with self.assertRaisesRegex(RuntimeError, 'processing'):
            self.dispatch(execute=True)
        self.status = 'processing'
        self.anonymous_version_200 = True
        with self.assertRaisesRegex(RuntimeError, 'not hidden'):
            self.dispatch(execute=True)
        self.anonymous_version_200 = False
        self.supported.remove('26.1.2')
        with self.assertRaisesRegex(RuntimeError, 'catalog'):
            self.dispatch(execute=True)
        (self.root / self.builds[0].name).write_bytes(b'tampered')
        with self.assertRaisesRegex(RuntimeError, 'SHA-512'):
            self.dispatch(execute=True)
        self.assertEqual(self.posts, [])


if __name__ == '__main__':
    unittest.main()
