"""Reruns must preserve exact successful producer provenance, never guess newest."""
import copy
import unittest
from release_artifact import select_artifact


class ArtifactSelectionTests(unittest.TestCase):
    def setUp(self):
        self.sha = 'b' * 40
        self.pattern = r'pr-validation-(?P<tested>[0-9a-f]{40})(?:-attempt-(?P<attempt>[1-9][0-9]*))?'
        self.job = {'id': 10, 'conclusion': 'success', 'run_attempt': 2,
                    'started_at': '2026-11-08T10:10:00Z', 'completed_at': '2026-11-08T10:20:00Z'}
        self.old = {'id': 1, 'name': f'pr-validation-{self.sha}-attempt-1', 'expired': False,
                    'created_at': '2026-11-08T10:09:00Z'}
        self.current = {'id': 2, 'name': f'pr-validation-{self.sha}-attempt-2', 'expired': False,
                        'created_at': '2026-11-08T10:19:00Z'}
        self.response = {'total_count': 2, 'artifacts': [self.old, self.current]}

    def select(self):
        return select_artifact(self.response, self.job, 2, self.pattern)

    def test_failed_producer_attempt_is_not_promoted(self):
        artifact, sha = self.select()
        self.assertEqual((artifact['id'], sha), (2, self.sha))
        self.assertEqual(artifact['producer_job_id'], 10)

    def test_unrelated_rerun_retains_original_successful_producer(self):
        self.job.update(started_at='2026-11-08T10:00:00Z', completed_at='2026-11-08T10:10:00Z')
        self.response = {'total_count': 1, 'artifacts': [self.old]}
        self.assertEqual(self.select()[0]['id'], 1)

    def test_unique_historical_unsuffixed_artifact_remains_supported(self):
        self.current['name'] = f'pr-validation-{self.sha}'
        self.response = {'total_count': 1, 'artifacts': [self.current]}
        self.assertEqual(self.select()[0]['id'], 2)

    def test_legacy_duplicate_names_remain_ambiguous_even_if_old_is_expired(self):
        self.old.update(name=f'pr-validation-{self.sha}', expired=True)
        self.current['name'] = self.old['name']
        with self.assertRaisesRegex(RuntimeError, 'Ambiguous'):
            self.select()

    def test_two_current_artifacts_are_not_chosen_by_id_or_order(self):
        self.old['created_at'] = self.current['created_at']
        with self.assertRaisesRegex(RuntimeError, 'exactly one'):
            self.select()

    def test_missing_current_output_cannot_fall_back_to_old_artifact(self):
        self.response = {'total_count': 1, 'artifacts': [self.old]}
        with self.assertRaisesRegex(RuntimeError, 'exactly one'):
            self.select()

    def test_future_attempt_is_rejected(self):
        self.current['name'] = f'pr-validation-{self.sha}-attempt-3'
        with self.assertRaisesRegex(RuntimeError, 'future'):
            self.select()

    def test_current_expired_artifact_is_rejected(self):
        self.current['expired'] = True
        with self.assertRaisesRegex(RuntimeError, 'Expired'):
            self.select()

    def test_truncated_listing_is_rejected(self):
        self.response['total_count'] = 101
        with self.assertRaisesRegex(RuntimeError, 'Incomplete'):
            self.select()

    def test_missing_total_count_is_rejected(self):
        del self.response['total_count']
        with self.assertRaisesRegex(RuntimeError, 'Incomplete'):
            self.select()

    def test_failed_job_is_rejected(self):
        self.job['conclusion'] = 'failure'
        with self.assertRaisesRegex(RuntimeError, 'must succeed'):
            self.select()

    def test_reversed_job_interval_is_rejected(self):
        self.job['started_at'] = '2026-11-08T10:30:00Z'
        with self.assertRaisesRegex(RuntimeError, 'interval'):
            self.select()

    def test_timestamp_fields_must_be_timezone_aware_and_valid(self):
        original = copy.deepcopy(self.job)
        for value in (None, '', 'not a date', '2026-11-08T10:10:00'):
            for key in ('started_at', 'completed_at'):
                with self.subTest(key=key, value=value):
                    self.job = {**original, key: value}
                    with self.assertRaisesRegex(RuntimeError, 'timestamp'):
                        self.select()
        self.job = original
        self.current['created_at'] = '2026-11-08T10:19:00'
        with self.assertRaisesRegex(RuntimeError, 'timestamp'):
            self.select()

    def test_equivalent_timestamp_offsets_are_compared_as_instants(self):
        self.current['created_at'] = '2026-11-08T19:19:00+09:00'
        self.assertEqual(self.select()[0]['id'], 2)

    def test_legacy_loader_pattern_uses_identical_selection_rules(self):
        self.pattern = r'pr-validation-(?P<tested>[0-9a-f]{40})-forge-full(?:-attempt-(?P<attempt>[1-9][0-9]*))?'
        for artifact in self.response['artifacts']:
            artifact['name'] = artifact['name'].replace('-attempt-', '-forge-full-attempt-')
        self.assertEqual(self.select()[0]['id'], 2)


if __name__ == '__main__':
    unittest.main()
