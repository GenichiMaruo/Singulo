"""Offline tests only: never authenticate to or upload to CurseForge."""
from datetime import timedelta
import io
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import Mock, patch
import urllib.error
import zipfile

import local_curseforge as release


class LocalReleaseTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.jar = b'original-tested-jar-bytes'
        self.plan = {'schema': 1, 'project_id': release.PROJECT, 'upload_protocol_ready': True,
            'inputs': {'tag': 'v0.15.1', 'run': 12, 'source': str(self.root / 'source'),
                       'validator_repo': str(self.root / 'repo'), 'archive_cache': str(self.root / 'cache'), 'channel': 'auto'},
            'target': release.target('v0.15.1'), 'provenance': {'commit': 'a' * 40},
            'validator_commit': 'b' * 40,
            'payload': {'displayName': 'Singulo-0.15.1-1.21.1N', 'changelog': 'Reviewed changes',
                        'changelogType': 'markdown', 'releaseType': 'beta', 'isMarkedForManualRelease': False,
                        'relations': {'projects': [{'slug': n, 'type': 'optionalDependency'} for n in ('jei', 'mekanism')]}},
            'jar_sha256': release.digest(self.jar), 'jar_size': len(self.jar)}
        self.inventory = self.root / 'inventory.json'
        self.inventory_data = {'schema': 1, 'project_id': release.PROJECT, 'checked_at': release.now().isoformat(),
            'all_statuses_accessible': True, 'all_pages_checked': True, 'no_other_publishers': True, 'files': []}
        self.write_inventory()
        self.state = self.root / 'state'
        self.receipt = self.state / 'v0.15.1.json'
        self.cf = Mock()
        self.cf.request.side_effect = lambda path, *args: {'id': 77} if args else []
        self.factory = Mock(return_value=self.cf)
        self.token = Mock(return_value='fake-test-credential')
        def github(endpoint, payload=None, **kwargs):
            if endpoint.startswith('actions/workflows/'):
                return {'workflow_runs': []}
            if endpoint.startswith('releases?'):
                return []
            if endpoint == 'git/refs':
                return {'ref': payload['ref'], 'object': {'type': 'commit', 'sha': payload['sha']}}
            return {'id': 41, 'draft': True, 'tag_name': 'v0.15.1', 'assets': []}
        self.gh = Mock(side_effect=github)
        self.catalog = patch.object(release, 'metadata_for', side_effect=lambda cf, p: dict(p['payload'], gameVersions=[1, 2, 3, 4, 5]))
        self.catalog.start()
        self.addCleanup(self.catalog.stop)
        self.remote_check_patch = patch.object(release, 'check_remote_plan')
        self.remote_check = self.remote_check_patch.start()
        self.addCleanup(self.remote_check_patch.stop)

    def write_inventory(self):
        self.inventory.write_text(json.dumps(self.inventory_data))

    def upload(self, **kwargs):
        with patch.object(release, 'gh', self.gh):
            return release.upload_verified(self.plan, kwargs.pop('jar', self.jar), self.inventory,
                self.state, kwargs.pop('confirm', lambda p: p), self.token, self.factory, **kwargs)

    def test_success_has_durable_intent_before_exactly_one_post(self):
        def send(path, body, content_type):
            intent = json.loads(self.receipt.read_text())
            self.assertEqual(intent['status'], 'upload_started_outcome_uncertain')
            self.assertEqual(intent['jar_sha256'], release.digest(self.jar))
            self.assertEqual(intent['request_sha256'], release.digest(body))
            self.assertIn(self.jar, body)
            self.assertIn(b'name="metadata"', body)
            self.assertIn(b'name="file"', body)
            self.assertIn('multipart/form-data', content_type)
            return {'id': 77}
        self.cf.request.side_effect = send
        result = self.upload()
        self.assertEqual(result['status'], 'submitted_awaiting_moderation')
        self.assertEqual(result['file_id'], 77)
        self.cf.request.assert_called_once()
        self.assertNotIn('fake-test-credential', self.receipt.read_text())
        self.assertEqual([c.args[0] for c in self.gh.call_args_list if len(c.args) > 1], ['git/refs', 'releases'])

    def test_faithful_draft_visibility_by_id_allows_one_upload(self):
        self.remote_check_patch.stop()  # Exercise the real final remote check.
        self.plan['provenance']['run_attempt'] = 1
        lock_ref = release.guard.ref_name('v0.15.1')
        draft = {'id': 41, 'draft': True, 'tag_name': 'v0.15.1', 'assets': []}
        def api(endpoint, payload=None, **kwargs):
            if endpoint.startswith('actions/workflows/'):
                return {'workflow_runs': []}
            if endpoint.startswith('releases?'):
                return []
            if endpoint == 'git/refs':
                return {'ref': lock_ref, 'object': {'type': 'commit', 'sha': 'a' * 40}}
            if endpoint == 'releases' or endpoint == 'releases/41':
                return draft
            if endpoint == 'git/ref/tags/v0.15.1':
                return {'object': {'type': 'commit', 'sha': 'a' * 40}}
            if endpoint == 'git/ref/heads/1.21.1':
                return {'object': {'type': 'commit', 'sha': 'b' * 40}}
            if endpoint == 'actions/runs/12':
                return {'status': 'completed', 'conclusion': 'success', 'run_attempt': 1}
            if endpoint == 'git/ref/' + lock_ref.removeprefix('refs/'):
                return {'ref': lock_ref, 'object': {'type': 'commit', 'sha': 'a' * 40}}
            raise AssertionError('Draft by-tag lookup is 404 and must never be used: ' + endpoint)
        self.gh.side_effect = api
        result = self.upload()
        self.assertEqual(result['status'], 'submitted_awaiting_moderation')
        self.cf.request.assert_called_once()

    def test_each_transport_failure_or_interruption_remains_uncertain_and_cannot_resend(self):
        for error in (TimeoutError(), ConnectionResetError(), KeyboardInterrupt(),
                      release.Stop('HTTP 500'), ValueError('invalid JSON')):
            with self.subTest(error=type(error).__name__), tempfile.TemporaryDirectory() as folder:
                self.state = Path(folder)
                self.receipt = self.state / 'v0.15.1.json'
                self.cf.reset_mock()
                self.cf.request.side_effect = error
                with self.assertRaises(release.Stop):
                    self.upload()
                self.assertEqual(json.loads(self.receipt.read_text())['status'], 'upload_started_outcome_uncertain')
                with self.assertRaisesRegex(release.Stop, 'Receipt already exists'):
                    self.upload()
                self.cf.request.assert_called_once()

    def test_malformed_upload_response_is_uncertain(self):
        for response in ({}, {'id': True}, {'id': -1}, None):
            with self.subTest(response=response), tempfile.TemporaryDirectory() as folder:
                self.state = Path(folder)
                self.cf.request.side_effect = None
                self.cf.request.return_value = response
                with self.assertRaises(release.Stop):
                    self.upload()
                self.assertEqual(json.loads((self.state / 'v0.15.1.json').read_text())['status'], 'upload_started_outcome_uncertain')

    def test_shared_existing_reservation_blocks_even_with_no_local_receipt(self):
        self.gh.side_effect = lambda endpoint, **kw: ({'workflow_runs': []} if endpoint.startswith('actions/workflows/')
            else [{'id': 45, 'draft': True, 'tag_name': 'v0.15.1'}])
        with self.assertRaisesRegex(release.Stop, 'Shared release reservation'):
            self.upload()
        self.token.assert_not_called()
        self.cf.request.assert_not_called()

    def test_legacy_actions_protocol_blocks_before_any_remote_write_or_token(self):
        self.plan['upload_protocol_ready'] = False
        with self.assertRaisesRegex(release.Stop, 'has not adopted'):
            self.upload()
        self.gh.assert_not_called()
        self.token.assert_not_called()

    def test_remote_create_conflict_or_uncertainty_never_posts_to_cf(self):
        def request(endpoint, payload=None, **kwargs):
            if endpoint.startswith('actions/workflows/'):
                return {'workflow_runs': []}
            if payload:
                raise release.Stop('Conflict or disconnected reservation response')
            return []
        self.gh.side_effect = request
        with self.assertRaises(release.Stop):
            self.upload()
        self.assertEqual(json.loads(self.receipt.read_text())['status'], 'atomic_lock_intent')
        self.cf.request.assert_not_called()

    def test_cross_machine_race_loser_cannot_upload(self):
        # Drafts are NOT unique. Only create-only Git refs are the mutex.
        created = []
        drafts = []
        def github(endpoint, payload=None, **kwargs):
            if endpoint.startswith('actions/workflows/'):
                return {'workflow_runs': []}
            if payload is None:
                return []  # Both machines can observe the same pre-create list.
            if endpoint == 'git/refs':
                if created:
                    raise release.Stop('GitHub 422 reference already exists')
                created.append(payload)
                return {'ref': payload['ref'], 'object': {'type': 'commit', 'sha': payload['sha']}}
            drafts.append(payload)  # GitHub would permit multiple drafts.
            return {'id': 40 + len(drafts), 'draft': True, 'tag_name': 'v0.15.1', 'assets': []}
        self.gh.side_effect = github
        self.upload()
        self.state = self.root / 'other-machine'
        with self.assertRaises(release.Stop):
            self.upload()
        self.cf.request.assert_called_once()
        self.assertEqual(len(drafts), 1)

    def test_failed_intent_persistence_never_posts(self):
        original = release.save
        calls = []
        def save(path, data):
            calls.append(data['status'])
            if data['status'] == 'upload_started_outcome_uncertain':
                raise OSError('disk full')
            original(path, data)
        with patch.object(release, 'save', side_effect=save), self.assertRaises(release.Stop):
            self.upload()
        self.cf.request.assert_not_called()
        self.assertEqual(json.loads(self.receipt.read_text())['status'], 'reserved')

    def test_success_receipt_write_failure_still_blocks_resend(self):
        original = release.save
        def save(path, data):
            if data['status'] == 'submitted_awaiting_moderation':
                raise OSError('disk full after accepted POST')
            original(path, data)
        with patch.object(release, 'save', side_effect=save), self.assertRaises(release.Stop):
            self.upload()
        with self.assertRaises(release.Stop):
            self.upload()
        self.cf.request.assert_called_once()

    def test_changed_bytes_and_declined_confirmation_never_post(self):
        with self.assertRaisesRegex(release.Stop, 'Bytes to send'):
            self.upload(jar=b'tampered')
        self.gh.assert_not_called()
        with self.assertRaisesRegex(release.Stop, 'not confirmed'):
            self.upload(confirm=lambda phrase: 'no')
        self.token.assert_not_called()
        self.cf.request.assert_not_called()

    def test_pending_public_manual_rejected_and_archived_collisions_block(self):
        for status in ('pending', 'published', 'manual_release', 'processing', 'rejected', 'archived'):
            with self.subTest(status=status):
                self.inventory_data['files'] = [{'id': 7, 'filename': self.plan['target']['filename'], 'status': status}]
                self.write_inventory()
                with self.assertRaisesRegex(release.Stop, 'collides'):
                    self.upload()
        self.gh.assert_not_called()
        self.token.assert_not_called()

    def test_same_bytes_under_another_filename_block(self):
        self.inventory_data['files'] = [{'id': 7, 'filename': 'renamed.jar', 'status': 'pending', 'sha256': release.digest(self.jar)}]
        self.write_inventory()
        with self.assertRaisesRegex(release.Stop, 'collides'):
            self.upload()

    def test_inaccessible_incomplete_concurrent_or_stale_inventory_blocks(self):
        for key in ('all_statuses_accessible', 'all_pages_checked', 'no_other_publishers'):
            with self.subTest(key=key):
                self.inventory_data[key] = False
                self.write_inventory()
                with self.assertRaises(release.Stop):
                    self.upload()
                self.inventory_data[key] = True
        self.inventory_data['checked_at'] = (release.now() - timedelta(minutes=6)).isoformat()
        self.write_inventory()
        with self.assertRaisesRegex(release.Stop, 'stale'):
            self.upload()
        self.token.assert_not_called()

    def test_malformed_inventory_and_duplicate_ids_block(self):
        cases = [None, {}, {'id': True, 'filename': 'x', 'status': 'pending'},
                 {'id': 1, 'filename': 'x', 'status': 'unknown'},
                 {'id': 1, 'filename': 'x', 'status': 'pending', 'sha256': 'bad'}]
        for row in cases:
            with self.subTest(row=row):
                self.inventory_data['files'] = [row]
                self.write_inventory()
                with self.assertRaises((release.Stop, TypeError)):
                    self.upload()
        self.inventory_data['files'] = [{'id': 1, 'filename': 'x', 'status': 'pending'}] * 2
        self.write_inventory()
        with self.assertRaises(release.Stop):
            self.upload()

    def test_inventory_can_expire_during_token_entry_and_blocks_post(self):
        def prompt():
            self.inventory_data['checked_at'] = (release.now() - timedelta(minutes=6)).isoformat()
            self.write_inventory()
            return 'fake-test-credential'
        self.token.side_effect = prompt
        with self.assertRaisesRegex(release.Stop, 'stale'):
            self.upload()
        self.cf.request.assert_not_called()

    def test_last_moment_provenance_change_blocks_before_upload_intent(self):
        self.remote_check.side_effect = release.Stop('tag changed')
        with self.assertRaises(release.Stop):
            self.upload()
        self.assertEqual(json.loads(self.receipt.read_text())['status'], 'reserved')
        self.cf.request.assert_not_called()

    def test_reconciliation_never_clears_receipt_or_allows_resend_when_absent(self):
        self.cf.request.side_effect = TimeoutError()
        with self.assertRaises(release.Stop):
            self.upload()
        before = self.receipt.read_bytes()
        observed = release.reconcile(self.receipt, self.inventory)
        self.assertFalse(observed['resend_allowed'])
        self.assertEqual(before, self.receipt.read_bytes())
        with self.assertRaises(release.Stop):
            self.upload()

    def test_other_process_cannot_acquire_local_project_lock(self):
        code = ('import sys; from pathlib import Path; sys.path.insert(0,sys.argv[1]); '
                'import local_curseforge as r;\n'
                'try:\n with r.lock(Path(sys.argv[2])): pass\n'
                'except r.Stop: sys.exit(42)\n')
        with release.lock(self.state):
            child = subprocess.run([sys.executable, '-I', '-c', code, str(Path(release.__file__).parent), str(self.state)],
                                   capture_output=True, timeout=10)
        self.assertEqual(child.returncode, 42, child.stderr)
        with release.lock(self.state):
            pass  # OS lock released; persistent receipts remain independent.

    def test_plan_hash_and_malformed_inputs(self):
        path = self.root / 'plan.json'
        path.write_bytes(release.canonical(self.plan))
        self.assertEqual(release.load_plan(path, release.digest(path.read_bytes())), self.plan)
        path.write_bytes(path.read_bytes() + b' ')
        with self.assertRaisesRegex(release.Stop, 'hash mismatch'):
            release.load_plan(path, release.digest(release.canonical(self.plan)))
        for bad in ('../../x', 'v0.15.1-1.20.1F-r1', 'v0.15.1\n', 'v1.20.1F', 'v0.15.1;echo'):
            with self.subTest(tag=bad), self.assertRaises(release.Stop):
                release.target(bad)
        for value in (True, 0, -1, '12'):
            with self.subTest(run=value), self.assertRaises(release.Stop):
                release.validate_inputs(dict(self.plan['inputs'], run=value))

    def test_loader_channel_and_metadata(self):
        for tag, mc, loader, java, channel in [('v0.15.1', '1.21.1', 'neoforge', 21, 'beta'),
                ('v0.15.1-1.20.1F', '1.20.1', 'forge', 17, 'beta'),
                ('v1.0.0-1.20.1N', '1.20.1', 'neoforge', 17, 'release')]:
            result = release.target(tag)
            self.assertEqual((result['minecraft'], result['loader'], result['java'], result['channel']), (mc, loader, java, channel))
        self.assertEqual(release.target('v0.15.1', 'alpha')['channel'], 'alpha')

    def test_unsafe_archives_and_duplicate_paths_are_rejected(self):
        with self.assertRaises(release.Stop):
            release.safe_name('a\\b')
        for names in (['../escape'], ['/absolute'], ['C:/secret'], ['.git/config'], ['A', 'a']):
            data = io.BytesIO()
            with zipfile.ZipFile(data, 'w') as archive:
                for name in names:
                    archive.writestr(name, b'data')
            with self.subTest(names=names), self.assertRaises(release.Stop):
                release.unpack_zip(data.getvalue(), self.root / 'out')

    def test_prepare_binds_downloaded_archive_to_github_digest_before_promotion(self):
        source = Path(self.plan['inputs']['source'])
        (source / 'docs/curseforge').mkdir(parents=True)
        (source / 'docs/curseforge/changelog-0.15.1.md').write_text('Reviewed changes')
        cache = Path(self.plan['inputs']['archive_cache'])
        cache.mkdir()
        binary = io.BytesIO()
        with zipfile.ZipFile(binary, 'w') as archive:
            archive.writestr('libs/test.jar', self.jar)
        (cache / '1.zip').write_bytes(binary.getvalue())
        archive_info = {'id': 1, 'name': 'ci-artifact', 'expired': False, 'workflow_run': {'id': 12},
                        'size_in_bytes': len(binary.getvalue()), 'digest': 'sha256:' + release.digest(binary.getvalue())}
        def github(endpoint, *args, **kwargs):
            return {'object': {'sha': 'b' * 40}} if endpoint.startswith('git/') else archive_info
        calls = []
        def validator(snapshot, args):
            calls.append(args[1])
            if args[1] == 'check':
                path = Path(args[args.index('--metadata') + 1])
                path.write_text(json.dumps({'commit': 'a' * 40, 'artifacts': {'neoforge': {'id': 1, 'name': 'ci-artifact'}}}))
            else:
                output = Path(args[args.index('--output') + 1])
                (output / self.plan['target']['filename']).write_bytes(self.jar)
            return b'verified\n'
        with patch.object(release, 'gh', side_effect=github), patch.object(release, 'validator_snapshot'), \
             patch.object(release, 'run_validator', side_effect=validator):
            plan, jar = release.prepare(self.plan['inputs'], self.root / 'bundle')
            self.assertEqual(plan['jar_sha256'], release.digest(jar))
            self.assertEqual(calls, ['check', 'promote'])
            for invalid in ('sha256:' + '0' * 64, ''):
                archive_info['digest'] = invalid
                calls.clear()
                with self.assertRaises(release.Stop):
                    release.prepare(self.plan['inputs'], self.root / ('bad-' + str(len(invalid))))
                self.assertEqual(calls, ['check'])

    def test_child_validator_is_isolated_utf8_and_ignores_untrusted_pythonpath(self):
        tools = self.root / 'snapshot/tools'
        tools.mkdir(parents=True)
        (tools / 'probe.py').write_text('import sys; import marker; print(sys.flags.isolated, sys.flags.utf8_mode, marker.VALUE)')
        (tools / 'marker.py').write_text('VALUE="trusted"')
        evil = self.root / 'evil'
        evil.mkdir()
        (evil / 'marker.py').write_text('raise RuntimeError("untrusted import")')
        with patch.dict(release.os.environ, {'PYTHONPATH': str(evil)}):
            result = release.run_validator(tools.parent, ['probe.py'])
        self.assertEqual(result.strip(), b'1 1 trusted')

    def test_upload_cli_rechecks_plan_and_stops_on_changed_provenance(self):
        path = self.root / 'plan.json'
        path.write_bytes(release.canonical(self.plan))
        changed = dict(self.plan, validator_commit='c' * 40)
        args = ['local_curseforge.py', 'upload', '--plan', str(path), '--plan-sha256', release.digest(path.read_bytes()),
                '--inventory', str(self.inventory), '--state-dir', str(self.state)]
        with patch.object(sys, 'argv', args), patch.object(sys.stdin, 'isatty', return_value=True), \
             patch.object(release, 'prepare', return_value=(changed, self.jar)), patch.object(release, 'upload_verified') as send:
            with self.assertRaisesRegex(release.Stop, 'changed since plan'):
                release.main()
            send.assert_not_called()


class TransportTests(unittest.TestCase):
    def test_cli_help_succeeds_and_unknown_credential_argument_is_not_echoed(self):
        script = str(Path(release.__file__))
        help_result = subprocess.run([sys.executable, script, '--help'], capture_output=True, timeout=10)
        self.assertEqual(help_result.returncode, 0)
        bad = subprocess.run([sys.executable, script, 'upload', '--token', 'fake-private-value'], capture_output=True, timeout=10)
        self.assertNotEqual(bad.returncode, 0)
        self.assertNotIn(b'fake-private-value', bad.stdout + bad.stderr)

    def test_final_remote_check_binds_tag_ci_branch_and_reservation(self):
        plan = {'inputs': {'tag': 'v0.15.1', 'run': 12}, 'target': {'minecraft': '1.21.1'},
                'provenance': {'commit': 'a' * 40, 'run_attempt': 1}, 'validator_commit': 'b' * 40}
        data = {'git/ref/tags/v0.15.1': {'object': {'type': 'commit', 'sha': 'a' * 40}},
                'git/ref/heads/1.21.1': {'object': {'type': 'commit', 'sha': 'b' * 40}},
                'actions/runs/12': {'status': 'completed', 'conclusion': 'success', 'run_attempt': 1},
                'releases/41': {'id': 41, 'draft': True, 'tag_name': 'v0.15.1', 'assets': []},
                'git/ref/tags/singulo-cf-upload-lock-v1/v0.15.1': {
                    'ref': release.guard.ref_name('v0.15.1'), 'object': {'type': 'commit', 'sha': 'a' * 40}}}
        with patch.object(release, 'gh', side_effect=lambda endpoint: data[endpoint]):
            release.check_remote_plan(plan, 41)
            for section, key, value in [('git/ref/tags/v0.15.1', 'object', {'type': 'commit', 'sha': 'c' * 40}),
                    ('git/ref/heads/1.21.1', 'object', {'type': 'commit', 'sha': 'c' * 40}),
                    ('actions/runs/12', 'run_attempt', 2), ('actions/runs/12', 'conclusion', 'failure'),
                    ('releases/41', 'id', 99), ('releases/41', 'assets', [{'id': 2}]),
                    ('releases/41', 'draft', False)]:
                original = data[section][key]
                data[section][key] = value
                with self.subTest(section=section, key=key), self.assertRaises(release.Stop):
                    release.check_remote_plan(plan, 41)
                data[section][key] = original

    def test_http_failures_and_redirects_do_not_retry_or_leak(self):
        for code in (301, 302, 307, 308, 400, 401, 429, 500, 503):
            opener = Mock()
            opener.open.side_effect = urllib.error.HTTPError('url', code, 'secret-token echoed by server', {}, None)
            with self.subTest(code=code), patch.object(release.urllib.request, 'build_opener', return_value=opener) as build:
                with self.assertRaises(release.Stop) as error:
                    release.CF('secret-token').request('/projects/1734662/upload-file', b'bytes', 'multipart/form-data')
                self.assertNotIn('secret-token', str(error.exception))
                opener.open.assert_called_once()
                handler = build.call_args.args[0]()
                self.assertIsNone(handler.redirect_request(None, None, code, '', {}, 'https://attacker.invalid'))
                request = opener.open.call_args.args[0]
                self.assertEqual(request.full_url, release.API + '/projects/1734662/upload-file')
                self.assertNotIn('secret-token', request.full_url)

    def test_credential_controls_and_noninteractive_entry_fail(self):
        for token in ('', 'a\nb', 'a b', 'a\rb', 'a' * 4097):
            with self.subTest(token_length=len(token)), self.assertRaises(release.Stop):
                release.CF(token)
        with patch.object(sys.stdin, 'isatty', return_value=False), self.assertRaises(release.Stop):
            release.interactive_token()

    def test_catalog_must_have_exact_game_loader_java_client_and_server(self):
        # Unpatch metadata resolver from the other test class: use real resolver.
        types = [{'id': n, 'slug': slug} for n, slug in enumerate(['minecraft-1-21', 'modloader', 'java', 'environment'], 1)]
        versions = [{'id': n, 'gameVersionTypeID': t, 'name': name} for n, (t, name) in enumerate([
            (1, '1.21.1'), (2, 'NeoForge'), (3, 'Java 21'), (4, 'Client'), (4, 'Server')], 1)]
        cf = Mock()
        cf.request.side_effect = [versions, types]
        plan = {'target': release.target('v0.15.1'), 'payload': {'releaseType': 'beta'}}
        self.assertEqual(release.metadata_for(cf, plan)['gameVersions'], [1, 2, 3, 4, 5])
        for catalog in (versions[:-1], versions + [versions[0]]):
            cf.request.side_effect = [catalog, types]
            with self.assertRaises(release.Stop):
                release.metadata_for(cf, plan)

    def test_github_missing_is_only_a_confirmed_404(self):
        for status, code in ((404, 1), (500, 1), (200, 0)):
            response = subprocess.CompletedProcess([], code, f'HTTP/2.0 {status} X\r\nX: y\r\n\r\n{{"id":1}}'.encode())
            with self.subTest(status=status), patch.object(release.subprocess, 'run', return_value=response):
                if status == 500:
                    with self.assertRaises(release.Stop):
                        release.gh('releases/tags/v0.15.1', missing=True)
                else:
                    self.assertEqual(release.gh('releases/tags/v0.15.1', missing=True), None if status == 404 else {'id': 1})


if __name__ == '__main__':
    unittest.main()
