"""Exercise the production artifact gate with real temporary ZIPs and per-test reports."""
from contextlib import redirect_stdout
import io
import json
from pathlib import Path
import struct
import tempfile
import unittest
from unittest.mock import patch
import zipfile
import check_release_1211 as release
from check_gametests import DEFAULT_NAMESPACES, inventory


class DistributionTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source_entries = inventory()

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(); self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        (self.root / 'gametest/logs').mkdir(parents=True)
        (self.root / 'reports/gametest').mkdir(parents=True)
        (self.root / 'libs').mkdir()
        (self.root / 'gametest/logs/latest.log').write_text(f'All {release.COUNTS["neoforge"]} required tests passed :)\n')
        names = sorted(e['name'] for e in self.source_entries if e['namespace'] in DEFAULT_NAMESPACES)
        self.report = {'schema': 1, 'complete': True, 'elapsedMillis': 123, 'expected': names, 'results': [
            {'name': n, 'status': 'passed', 'required': True, 'millis': 0} for n in names]}
        self.write_report()
        self.name = 'Singulo-0.0.0-1.21.1N.jar'
        self.entries = {
            'META-INF/neoforge.mods.toml': b'[[mods]]\nmodId="singulo"\nversion="0.0.0"\n'
                b'[[dependencies.singulo]]\nmodId="neoforge"\nversionRange="[21.1,)"\n'
                b'[[dependencies.singulo]]\nmodId="minecraft"\nversionRange="[1.21.1]"\n',
            'example/Main.class': b'\xca\xfe\xba\xbe' + struct.pack('>HH', 0, 65)}

    def write_report(self):
        (self.root / 'reports/gametest/results.json').write_text(json.dumps(self.report))

    def check(self):
        with zipfile.ZipFile(self.root / 'libs' / self.name, 'w') as jar:
            for name, value in self.entries.items(): jar.writestr(name, value)
        with redirect_stdout(io.StringIO()), patch('check_gametests.inventory', return_value=self.source_entries):
            release.validate(self.root, 'neoforge', self.name)

    def test_valid_java21_distribution(self): self.check()

    def test_test_or_review_code_never_ships(self):
        for name in ['example/FooGameTests.class', 'example/FooGameTests$1.class',
                     'example/TestBuild.class', 'example/test/harness/TestReports.class', 'example/GuiReviewHarness.class']:
            with self.subTest(name=name):
                self.entries[name] = b'fixture'
                with self.assertRaisesRegex(RuntimeError, 'development'): self.check()
                del self.entries[name]

    def test_newer_java_is_rejected(self):
        self.entries['example/Main.class'] = b'\xca\xfe\xba\xbe' + struct.pack('>HH', 0, 66)
        with self.assertRaisesRegex(RuntimeError, 'newer than Java 21'): self.check()

    def test_success_count_cannot_replace_a_missing_result(self):
        self.report['results'].pop(); self.write_report()
        with self.assertRaisesRegex(RuntimeError, 'Missing'): self.check()

    def test_incomplete_report_blocks_distribution(self):
        self.report['complete'] = False; self.write_report()
        with self.assertRaisesRegex(RuntimeError, 'completed'): self.check()

    def test_old_or_concatenated_completion_lines_are_rejected(self):
        path = self.root / 'gametest/logs/latest.log'
        path.write_text(path.read_text() * 2)
        with self.assertRaisesRegex(RuntimeError, 'expected'): self.check()


if __name__ == '__main__': unittest.main()
