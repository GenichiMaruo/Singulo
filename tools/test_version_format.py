"""Release versions are X.Y.Z with an optional -rcN; release candidates publish as Beta."""
import unittest

import curseforge_guard


class VersionFormatTests(unittest.TestCase):
    def test_malformed_versions_are_rejected(self):
        for tag in ('v1.0.0-rc0', 'v1.0.0-rc', 'v1.0.0-beta1', 'v1.0', 'v1.0.0rc1', 'v1.0.0-RC1'):
            with self.assertRaises(curseforge_guard.GuardError, msg=tag):
                curseforge_guard.ref_name(tag)

    def test_lock_name_keeps_the_candidate_and_drops_the_revision(self):
        prefix = curseforge_guard.PREFIX
        self.assertEqual(curseforge_guard.ref_name('v1.0.0-rc1'), prefix + 'v1.0.0-rc1')
        self.assertEqual(curseforge_guard.ref_name('v1.0.0-rc1-r2'), prefix + 'v1.0.0-rc1')
        self.assertEqual(curseforge_guard.ref_name('v1.0.0-rc1-1.20.1N-r3'), prefix + 'v1.0.0-rc1-1.20.1N')
        self.assertNotEqual(curseforge_guard.ref_name('v1.0.0-rc1'), curseforge_guard.ref_name('v1.0.0'))


if __name__ == '__main__':
    unittest.main()
