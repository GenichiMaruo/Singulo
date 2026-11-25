"""Mocked tests for the isolated release recovery. Never touch network or live refs."""
from datetime import datetime, timezone
import json
import unittest
from unittest.mock import Mock, patch

import recover_curseforge_0230_1211 as recovery


class RecoveryTests(unittest.TestCase):
    def env(self):
        return {"GITHUB_REPOSITORY": recovery.REPO, "GITHUB_ACTOR_ID": recovery.OWNER,
                "GITHUB_REF": f"refs/heads/{recovery.BRANCH}", "GITHUB_EVENT_NAME": "workflow_dispatch",
                "GITHUB_RUN_ATTEMPT": "1", "GITHUB_RUN_ID": "76543210",
                "AUTHOR_CHECK_DECLARATION": recovery.ATTESTATION,
                "AUTHOR_CHECKED_UTC": "2026-11-25T17:00:00Z"}

    def test_exact_fresh_owner_attestation(self):
        now = datetime(2026, 11, 25, 17, 1, tzinfo=timezone.utc)
        self.assertEqual(recovery.verify_attestation(self.env(), now)[0], 76543210)
        for key, value in [("GITHUB_ACTOR_ID", "0"), ("GITHUB_RUN_ATTEMPT", "2"),
                           ("GITHUB_REF", "refs/heads/other"), ("GITHUB_EVENT_NAME", "push"),
                           ("AUTHOR_CHECK_DECLARATION", "OK"),
                           ("AUTHOR_CHECKED_UTC", "2026-11-25T16:30:00Z"),
                           ("AUTHOR_CHECKED_UTC", "2026-11-25T17:10:00Z")]:
            with self.subTest(key=key, value=value):
                env = self.env()
                env[key] = value
                with self.assertRaises(recovery.Stop):
                    recovery.verify_attestation(env, now)

    def test_prior_recovery_ref_stops_without_ref_post(self):
        api = Mock(return_value=[{"ref": recovery.RECOVERY}])
        with self.assertRaisesRegex(recovery.Stop, "already begun"):
            recovery.acquire(api)
        self.assertEqual(api.call_count, 1)

    def test_ref_response_uncertain_is_never_retried(self):
        api = Mock(side_effect=[[], {"ref": recovery.RECOVERY}])
        with self.assertRaisesRegex(recovery.Stop, "Uncertain atomic"):
            recovery.acquire(api)
        self.assertEqual(api.call_count, 2)
        self.assertEqual(api.call_args_list[1].args,
                         ("git/refs", {"ref": recovery.RECOVERY, "sha": recovery.COMMIT}))

    def test_only_new_recovery_ref_is_created(self):
        api = Mock(side_effect=[[], {"ref": recovery.RECOVERY,
                                    "object": {"sha": recovery.COMMIT, "type": "commit"}}])
        recovery.acquire(api)
        self.assertNotEqual(recovery.RECOVERY, recovery.LOCK)
        self.assertEqual(api.call_count, 2)

    def public_row(self, id, name="old.jar"):
        return {"id": id, "projectId": int(recovery.PROJECT), "displayName": name, "fileName": name}

    def inventory_response(self, index, count, rows):
        return 200, json.dumps({"data": rows, "pagination": {"index": index, "pageSize": 50,
                                                                "totalCount": count}}).encode()

    def test_public_inventory_walks_second_page_and_blocks_duplicate(self):
        request = Mock(side_effect=[self.inventory_response(0, 51, [self.public_row(i+1) for i in range(50)]),
                                   self.inventory_response(1, 51, [self.public_row(51, recovery.FILENAME)])])
        with self.assertRaisesRegex(recovery.Stop, "Existing matching"):
            recovery.no_public_copy(request)
        self.assertEqual(request.call_count, 2)

    def test_truncated_inventory_and_http_error_fail_closed(self):
        for response in [self.inventory_response(0, 51, [self.public_row(1)]), (403, b"bad")]:
            with self.subTest(response=response):
                request = Mock(return_value=response)
                with self.assertRaises(recovery.Stop):
                    recovery.no_public_copy(request)
                self.assertEqual(request.call_count, 1)

    def test_upload_uses_one_fixed_origin_request_and_expected_metadata(self):
        request = Mock(return_value=(200, b'{"id": 43210}'))
        self.assertEqual(recovery.upload("dummy-token", "notes", b"jar", request), 43210)
        args, kwargs = request.call_args
        self.assertEqual(request.call_count, 1)
        self.assertEqual(args, (recovery.CF_HOST, f"/api/projects/{recovery.PROJECT}/upload-file"))
        self.assertEqual(kwargs["method"], "POST")
        self.assertEqual(kwargs["headers"]["X-Api-Token"], "dummy-token")
        self.assertIn(recovery.FILENAME.encode(), kwargs["body"])
        self.assertIn(b'"isMarkedForManualRelease": false', kwargs["body"])
        self.assertIn(b'"NeoForge"', kwargs["body"])
        self.assertIn(b'"Java 21"', kwargs["body"])
        self.assertNotIn(b"dummy-token", kwargs["body"])

    def test_uncertain_upload_has_no_second_post(self):
        for response in [(500, b"internal"), (302, b"redirect"), (200, b'{"id": "bad"}')]:
            with self.subTest(response=response):
                request = Mock(return_value=response)
                with self.assertRaises(recovery.Stop):
                    recovery.upload("dummy-token", "notes", b"jar", request)
                self.assertEqual(request.call_count, 1)
        request = Mock(side_effect=TimeoutError)
        with self.assertRaises(TimeoutError):
            recovery.upload("dummy-token", "notes", b"jar", request)
        self.assertEqual(request.call_count, 1)

    def test_missing_attestation_blocks_before_any_api(self):
        with patch.dict("os.environ", {}, clear=True), patch.object(recovery, "github") as github,                 patch.object(recovery, "upload") as upload:
            with self.assertRaises(recovery.Stop):
                recovery.main()
            github.assert_not_called()
            upload.assert_not_called()

    def test_another_native_publisher_blocks_recovery(self):
        api = Mock(return_value={"total_count": 1, "workflow_runs": [{"id": 100}]})
        with self.assertRaisesRegex(RuntimeError, "Other/unknown"):
            recovery.exclusive(api, 101)
        self.assertEqual(api.call_count, 1)

    def original_api(self, publish_outcome):
        return Mock(side_effect=[
            [{"ref": recovery.LOCK, "object": {"sha": recovery.COMMIT, "type": "commit"}}],
            {"object": {"sha": recovery.COMMIT, "type": "commit"}},
            {"status": "identical"},
            {"id": recovery.FAILED_RUN, "head_sha": recovery.COMMIT, "event": "workflow_dispatch",
             "path": ".github/workflows/publish-curseforge.yml", "status": "completed",
             "conclusion": "failure", "run_attempt": 1},
            {"total_count": 1, "jobs": [{"name": "publish", "conclusion": "failure", "steps": [
                {"name": k, "conclusion": v} for k, v in [
                    ("Promote the validated production JAR without rebuilding", "success"),
                    ("Acquire permanent shared CurseForge upload lock", "success"),
                    ("Reserve an asset-free release audit record", "failure"),
                    ("Publish NeoForge to CurseForge", publish_outcome),
                    ("Publish release notes with CurseForge links only", "skipped"),
                ]]}]},
            {"artifacts": [{"id": recovery.INTENT_ARTIFACT, "name": f"cf-lock-{recovery.FAILED_RUN}-1",
                            "expired": False}]},
        ])

    def test_original_must_prove_upload_skipped_and_intent_match(self):
        intent = {"status": "atomic_lock_intent", "ref": recovery.LOCK, "commit": recovery.COMMIT}
        recovery.original_proof(self.original_api("skipped"), intent)
        with self.assertRaisesRegex(recovery.Stop, "not provably skipped"):
            recovery.original_proof(self.original_api("failure"), intent)
        with self.assertRaisesRegex(recovery.Stop, "intent bytes differ"):
            recovery.original_proof(self.original_api("skipped"), {**intent, "commit": "other"})


if __name__ == "__main__":
    unittest.main()
