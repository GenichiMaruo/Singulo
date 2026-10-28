"""Regression tests for fail-closed GameTest evidence and source inventory."""
import copy
from pathlib import Path
import tempfile
import unittest
from check_gametests import check, inventory, validate_report


class ReportTests(unittest.TestCase):
    def setUp(self):
        self.report = {'schema': 1, 'complete': True, 'expected': ['alpha', 'beta'], 'results': [
            {'name': name, 'status': 'passed', 'required': True, 'millis': 1} for name in ('alpha', 'beta')]}

    def check(self):
        return validate_report(self.report, ['alpha', 'beta'])

    def test_exact_results_pass_regardless_of_order(self):
        self.report['results'].reverse()
        self.assertEqual(self.check(), 2)

    def test_same_count_different_test_is_rejected(self):
        self.report['results'][1]['name'] = 'unexpected'
        with self.assertRaises(RuntimeError): self.check()

    def test_duplicate_success_is_rejected(self):
        self.report['results'][1]['name'] = 'alpha'
        with self.assertRaises(RuntimeError): self.check()

    def test_missing_result_is_rejected(self):
        self.report['results'].pop()
        with self.assertRaises(RuntimeError): self.check()

    def test_wrong_registration_is_rejected(self):
        self.report['expected'][1] = 'unexpected'
        with self.assertRaises(RuntimeError): self.check()

    def test_incomplete_or_failed_reports_are_rejected(self):
        for field, value in [('complete', False), ('schema', 9)]:
            with self.subTest(field=field):
                bad = copy.deepcopy(self.report); bad[field] = value
                with self.assertRaises(RuntimeError): validate_report(bad, ['alpha', 'beta'])
        for field, value in [('status', 'failed'), ('required', False), ('millis', -1)]:
            with self.subTest(field=field):
                bad = copy.deepcopy(self.report); bad['results'][0][field] = value
                with self.assertRaises(RuntimeError): validate_report(bad, ['alpha', 'beta'])

    def test_empty_selection_is_rejected(self):
        with self.assertRaises(RuntimeError): validate_report({'schema': 1, 'complete': True}, [])


class InventoryTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(); self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.dir = self.root / 'src/main/java'; self.dir.mkdir(parents=True)

    def source(self, name='Example', method='example', attrs='template = EMPTY'):
        return ('@GameTestHolder(Singulo.MODID)\n@PrefixGameTestTemplate(false)\n'
                'public class ' + name + ' { private static final String EMPTY = "empty";\n'
                '@GameTest(' + attrs + ')\n@SuppressWarnings("removal")\n'
                'public static void ' + method + '(GameTestHelper h) throws Exception { h.succeed(); } }')

    def test_comments_and_annotations_are_understood(self):
        (self.dir / 'ExampleGameTests.java').write_text('// @GameTest(not a real declaration)\n' + self.source())
        self.assertEqual(inventory(self.root)[0]['name'], 'example')

    def test_duplicate_names_across_classes_are_rejected(self):
        (self.dir / 'AGameTests.java').write_text(self.source('A'))
        (self.dir / 'BGameTests.java').write_text(self.source('B'))
        with self.assertRaisesRegex(RuntimeError, 'Duplicate'): inventory(self.root)

    def test_incompatible_declarations_fail_instead_of_being_skipped(self):
        for attrs in ['template = EMPTY, required = false', 'template = EMPTY, attempts = 3']:
            with self.subTest(attrs=attrs):
                (self.dir / 'ExampleGameTests.java').write_text(self.source(attrs=attrs))
                with self.assertRaises(RuntimeError): inventory(self.root)
        (self.dir / 'ExampleGameTests.java').write_text(self.source().replace('public static void', 'public void'))
        with self.assertRaisesRegex(RuntimeError, 'Unparsed'): inventory(self.root)

    def test_test_outside_the_development_source_pattern_is_rejected(self):
        (self.dir / 'Example.java').write_text(self.source())
        with self.assertRaisesRegex(RuntimeError, 'source set'): inventory(self.root)

    def test_new_namespace_cannot_silently_escape_the_full_suite(self):
        (self.dir / 'ExampleGameTests.java').write_text(self.source().replace('Singulo.MODID', '"unlisted"'))
        with self.assertRaisesRegex(RuntimeError, 'namespace is absent'): check(self.root, source=self.root)

    def test_absent_source_is_not_zero_test_success(self):
        with self.assertRaisesRegex(RuntimeError, 'No GameTests'): inventory(self.root)


if __name__ == '__main__':
    unittest.main()
