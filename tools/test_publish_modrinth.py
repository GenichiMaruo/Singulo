"""Offline tests: never contact Modrinth or upload files."""
import copy
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import publish_modrinth as publisher


class PublisherTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.jar = Path(self.tmp.name) / 'Singulo-0.14.1-1.20.1N.jar'
        self.jar.write_bytes(b'verified CI JAR')
        self.digest = hashlib.sha512(self.jar.read_bytes()).hexdigest()
        self.receipt = Path(self.tmp.name) / 'receipt.json'
        self.payload = {'project_id': publisher.PROJECT, 'version_number': '0.14.1-1.20.1N',
                        'loaders': ['neoforge'], 'game_versions': ['1.20.1'], 'version_type': 'beta'}
        self.version = self.payload | {'id': 'version1', 'status': 'listed', 'files': [
            {'filename': self.jar.name, 'primary': True, 'hashes': {'sha512': self.digest}}]}
        self.project = {'id': publisher.PROJECT, 'status': 'approved', 'project_type': 'mod'}

    def test_existing_exact_file_skips_without_write_token(self):
        with patch.object(publisher, 'request', side_effect=[self.project, [self.version]]) as request:
            publisher.publish(self.payload, self.jar, self.receipt)
        self.assertEqual(request.call_count, 2)
        self.assertEqual(json.loads(self.receipt.read_text())['status'], 'already_exists')

    def test_conflicting_hash_loader_game_channel_or_status_stops(self):
        changes = [{'loaders': ['forge']}, {'game_versions': ['1.21.1']},
                   {'version_type': 'release'}, {'status': 'draft'},
                   {'files': [{'filename': self.jar.name, 'primary': True, 'hashes': {'sha512': 'wrong'}}]}]
        for change in changes:
            with self.subTest(change=change), self.assertRaises(RuntimeError):
                publisher.matching_version([self.version | change], self.payload, self.jar.name, self.digest)

    def test_same_hash_under_another_number_is_a_collision(self):
        with self.assertRaises(RuntimeError):
            publisher.matching_version([self.version | {'version_number': 'other'}], self.payload,
                                       self.jar.name, self.digest)

    def test_duplicate_exact_versions_are_ambiguous(self):
        with self.assertRaises(RuntimeError):
            publisher.matching_version([self.version, self.version], self.payload, self.jar.name, self.digest)

    def test_unrelated_version_is_ignored(self):
        other = copy.deepcopy(self.version)
        other.update(version_number='0.13.0-1.20.1N')
        other['files'][0].update(filename='other.jar', hashes={'sha512': 'other'})
        self.assertIsNone(publisher.matching_version([other], self.payload, self.jar.name, self.digest))

    def test_under_review_project_blocks_before_upload(self):
        with patch.object(publisher, 'request', return_value=self.project | {'status': 'processing'}) as request:
            with self.assertRaises(RuntimeError):
                publisher.publish(self.payload, self.jar, self.receipt)
        self.assertEqual(request.call_count, 1)

    def test_wrong_target_blocks_before_network(self):
        with patch.object(publisher, 'request') as request, self.assertRaises(RuntimeError):
            publisher.publish(self.payload | {'project_id': 'wrong'}, self.jar, self.receipt)
        request.assert_not_called()

    def test_dry_run_never_uploads(self):
        with patch.object(publisher, 'request', side_effect=[self.project, []]) as request:
            publisher.publish(self.payload, self.jar, self.receipt, dry_run=True)
        self.assertEqual(request.call_count, 2)
        self.assertEqual(json.loads(self.receipt.read_text())['status'], 'dry_run')

    def test_uncertain_post_is_not_retried_and_receipt_survives(self):
        with patch.dict('os.environ', MODRINTH_TOKEN='test-only'), patch.object(
                publisher, 'request', side_effect=[self.project, [], TimeoutError('uncertain')]) as request:
            with self.assertRaises(TimeoutError):
                publisher.publish(self.payload, self.jar, self.receipt)
        self.assertEqual(request.call_count, 3)
        self.assertEqual(json.loads(self.receipt.read_text())['status'], 'upload_started_outcome_uncertain')
        self.assertNotIn('test-only', self.receipt.read_text())

    def test_upload_response_is_verified(self):
        with patch.dict('os.environ', MODRINTH_TOKEN='test-only'), patch.object(
                publisher, 'request', side_effect=[self.project, [], self.version]) as request:
            publisher.publish(self.payload, self.jar, self.receipt)
        self.assertEqual(request.call_count, 3)
        self.assertEqual(json.loads(self.receipt.read_text())['status'], 'uploaded')
        body = request.call_args.args[1]
        self.assertIn(self.jar.read_bytes(), body)
        self.assertIn(b'name="data"', body)
        self.assertIn(b'name="file"', body)


if __name__ == '__main__':
    unittest.main()
