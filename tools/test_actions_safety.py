"""Keep publication and PR-validation security boundaries explicit, without live uploads."""
from pathlib import Path
import re
import unittest

WORKFLOWS = Path(__file__).resolve().parent.parent / '.github/workflows'


class WorkflowSafetyTests(unittest.TestCase):
    def test_every_external_action_is_pinned_to_a_full_commit(self):
        for path in WORKFLOWS.glob('*.yml'):
            for action in re.findall(r'^\s*- uses: (\S+)', path.read_text(), re.M):
                with self.subTest(workflow=path.name, action=action):
                    self.assertRegex(action, r'^[^@]+@[0-9a-f]{40}$')

    def test_checkouts_do_not_leave_git_credentials_in_the_workspace(self):
        for path in WORKFLOWS.glob('*.yml'):
            blocks = re.findall(r'^      - uses: actions/checkout@[^\n]+\n(?:(?!      - ).*\n)*', path.read_text(), re.M)
            self.assertTrue(blocks, path.name)
            for block in blocks:
                self.assertIn('          persist-credentials: false\n', block, path.name)

    def test_publishers_are_manual_and_restricted_to_the_maintained_branch(self):
        for name in ('publish-curseforge.yml', 'publish-modrinth.yml'):
            text = (WORKFLOWS / name).read_text()
            with self.subTest(workflow=name):
                self.assertIn('  workflow_dispatch:\n', text)
                self.assertNotRegex(text, r'(?m)^  (?:push|pull_request|pull_request_target|workflow_run):')
                self.assertIn("    if: github.ref == 'refs/heads/1.21.1'\n", text)
                self.assertNotRegex(text, r'(?m)^      (?:GH_TOKEN|MODRINTH_TOKEN|CURSEFORGE_TOKEN):')

    def test_curseforge_never_retries_an_uncertain_publication(self):
        text = (WORKFLOWS / 'publish-curseforge.yml').read_text()
        block = re.search(r'      - name: Publish NeoForge to CurseForge\n(.*?)(?=      - name:|\Z)', text, re.S)[1]
        self.assertEqual(re.findall(r'^          retry-attempts: (\d+)\s*$', block, re.M), ['1'])
        self.assertNotIn('curseforge-retry-attempts:', block)
        self.assertIn('          fail-mode: fail\n', block)
        self.assertLess(text.index('gh release create '), text.index('uses: Kira-NT/mc-publish@'))

    def test_pull_requests_have_read_only_permissions_and_no_publication_secrets(self):
        text = (WORKFLOWS / 'pr-validation.yml').read_text()
        self.assertIn('  pull_request:\n', text)
        self.assertIn('permissions:\n  contents: read\n', text)
        self.assertNotIn('secrets.', text)
        self.assertNotIn('contents: write', text)
        self.assertNotIn('pull_request_target:', text)

    def test_publication_executes_trusted_validation_code(self):
        for name in ('publish-curseforge.yml', 'publish-modrinth.yml'):
            text = (WORKFLOWS / name).read_text()
            with self.subTest(workflow=name):
                self.assertIn('python3 tools/verified_release_1211.py check ', text)
                self.assertIn('python3 tools/verified_release_1211.py promote ', text)
                self.assertNotRegex(text, r'python3\s+release-source/tools/')

    def test_no_python_runs_from_the_tag_checkout(self):
        for name in ('publish-curseforge.yml', 'publish-modrinth.yml'):
            text = (WORKFLOWS / name).read_text()
            for step in re.split(r'(?m)^      - ', text):
                if 'working-directory: release-source' in step:
                    with self.subTest(workflow=name):
                        self.assertNotRegex(step, r'(?m)^\s*(?:run:\s*)?python(?:3)?\s')

    def test_validation_jobs_have_bounded_runtime_and_cancel_obsolete_runs(self):
        text = (WORKFLOWS / 'pr-validation.yml').read_text()
        limits = [int(x) for x in re.findall(r'^    timeout-minutes: (\d+)\s*$', text, re.M)]
        self.assertEqual(len(limits), 3)
        self.assertTrue(all(1 <= x <= 35 for x in limits))
        self.assertIn('  cancel-in-progress: true\n', text)


if __name__ == '__main__':
    unittest.main()
