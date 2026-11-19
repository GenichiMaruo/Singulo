import unittest
from unittest.mock import patch

from assembly_shapes import DIAGRAMS, assembly_rows


class AssemblyCenteringTests(unittest.TestCase):
    def test_heavier_row_faces_center_from_either_side(self):
        for diagram, expected in [('AAA/A', [' AAA ', '  A  ']), ('A/AAA', ['  A  ', ' AAA '])]:
            with self.subTest(diagram=diagram), patch.dict(DIAGRAMS, {'sample': diagram}):
                rows = assembly_rows('sample', [{'count': 4}])
                self.assertEqual(rows[2], ' AAA ')
                self.assertEqual([row for row in rows if row.strip()], expected)

    def test_four_row_diagram_keeps_wide_end_clear_of_missing_corners(self):
        with patch.dict(DIAGRAMS, {'sample': 'AAAAA/AAA/A/A'}):
            rows = assembly_rows('sample', [{'count': 10}])
        self.assertEqual(rows, ['     ', 'AAAAA', ' AAA ', '  A  ', '  A  '])
