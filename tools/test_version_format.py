"""Release versions are X.Y.Z with an optional -rcN; release candidates publish as Beta."""
import unittest

import curseforge_guard
import local_curseforge


class VersionFormatTests(unittest.TestCase):
    def test_release_candidates_are_accepted_and_beta(self):
        t = local_curseforge.target('v1.0.0-rc1')
        self.assertEqual((t['version'], t['channel'], t['filename']),
                         ('1.0.0-rc1', 'beta', 'Singulo-1.0.0-rc1-1.21.1N.jar'))
        t = local_curseforge.target('v1.0.0-rc2-1.20.1F')
        self.assertEqual((t['version'], t['minecraft'], t['loader'], t['channel']), ('1.0.0-rc2', '1.20.1', 'forge', 'beta'))

    def test_stable_channel_follows_major_version(self):
        self.assertEqual(local_curseforge.target('v0.20.0')['channel'], 'beta')
        self.assertEqual(local_curseforge.target('v1.0.0')['channel'], 'release')
        self.assertEqual(local_curseforge.target('v1.0.0-rc1', 'release')['channel'], 'release')

    def test_malformed_versions_are_rejected(self):
        for tag in ('v1.0.0-rc0', 'v1.0.0-rc', 'v1.0.0-beta1', 'v1.0', 'v1.0.0rc1', 'v1.0.0-RC1'):
            with self.assertRaises(local_curseforge.Stop, msg=tag):
                local_curseforge.target(tag)
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
