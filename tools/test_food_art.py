"""Check the printed food and cartridge pixel art: a closed outline whose colors follow the neighboring fill."""
import colorsys
import unittest

import food_content as fc


def arts():
    yield from fc.FOOD_ART.items()
    yield 'food_cartridge', fc.CARTRIDGE_ART


class FoodArtTests(unittest.TestCase):
    def test_outline_closes_every_icon(self):
        for name, art in arts():
            with self.subTest(name):
                self.assertEqual(fc.outline_gaps(art[0]), [], 'fill pixels touching transparency')
                self.assertEqual(fc.stray_outline(art[0]), [], 'outline pixels with no fill next to them')

    def test_outline_is_a_darker_shade_of_its_neighbor(self):
        for name, art in arts():
            rows, pal = art[0], art[1]
            with self.subTest(name):
                self.assertFalse(set(pal) & set(fc.OUTLINE), 'outline colors must come from the neighbors, not the palette')
                for (x, y), c in fc.outline_colors(*art).items():
                    near = [pal[rows[y + dy][x + dx]] for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))
                            if 0 <= y + dy < 16 and 0 <= x + dx < 16 and rows[y + dy][x + dx] not in '.' + fc.OUTLINE]
                    lightest = max((colorsys.rgb_to_hsv(*(t / 255 for t in n))[2] for n in near), default=1)
                    self.assertLess(colorsys.rgb_to_hsv(*(t / 255 for t in c))[2], lightest, (x, y))

    def test_cartridge_fits_in_14_by_14(self):
        box = fc.cartridge_icons()[0].getbbox()
        self.assertLessEqual(box[2] - box[0], 14)
        self.assertLessEqual(box[3] - box[1], 14)


if __name__ == '__main__':
    unittest.main()
