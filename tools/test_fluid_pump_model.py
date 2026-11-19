"""The pump model uses the same native texel scale for the gauge, couplings and castings."""
import json
from pathlib import Path
import unittest

from PIL import Image

ASSETS = Path(__file__).resolve().parents[1] / 'src/generated/resources/assets/singulo'


class FluidPumpModelTests(unittest.TestCase):
    def test_each_rendered_face_has_one_texel_per_model_unit(self):
        model = json.loads((ASSETS / 'models/block/fluid_pump.json').read_text())
        for texture in set(model['textures'].values()):
            with Image.open(ASSETS / 'textures' / (texture.split(':')[1] + '.png')) as tile:
                self.assertEqual(tile.size, (16, 16))
        for element in model['elements']:
            dx, dy, dz = (b - a for a, b in zip(element['from'], element['to']))
            for side, face in element['faces'].items():
                u, v, end_u, end_v = face['uv']
                self.assertTrue(all(isinstance(n, int) and 0 <= n <= 16 for n in face['uv']))
                size = (dx, dz) if side in ('up', 'down') else (dz, dy) if side in ('east', 'west') else (dx, dy)
                self.assertEqual((end_u - u, end_v - v), size, (element, side))

    def test_outlet_lips_and_recessed_bores_are_unobstructed(self):
        model = json.loads((ASSETS / 'models/block/fluid_pump.json').read_text())
        for side, axis, positive in [('up', 1, True), ('down', 1, False), ('east', 0, True),
                                     ('west', 0, False), ('south', 2, True)]:
            u, v = [i for i in range(3) if i != axis]
            visible = {}
            for part in model['elements']:
                if side not in part['faces']:
                    continue
                depth = part['to'][axis] if positive else 16 - part['from'][axis]
                for x in range(part['from'][u], part['to'][u]):
                    for y in range(part['from'][v], part['to'][v]):
                        if (x, y) not in visible or depth > visible[x, y][0]:
                            visible[x, y] = (depth, part['faces'][side]['texture'])
            for x in range(5, 11):
                for y in range(5, 11):
                    rim = x in (5, 10) or y in (5, 10)
                    self.assertEqual(visible[x, y], (16, '#steel') if rim else (15, '#socket'), (side, x, y))
