"""Guard known UI argument meanings and handbook page ownership in every locale."""
import json
from pathlib import Path
import re
import unittest

LANG = Path(__file__).resolve().parent.parent / 'src/generated/resources/assets/singulo/lang'

def translated_format(text, values):
    implicit = iter(values)
    return re.sub(r'%(?:(\d+)\$)?s', lambda m: values[int(m[1]) - 1] if m[1] else next(implicit), text)

class LocalizationSemanticsTests(unittest.TestCase):
    def test_ignition_values_keep_their_units(self):
        # PenroseReactorScreen.ignitionNeed supplies GFE, seconds, then average MFE/t.
        for locale in ('en_us', 'ja_jp', 'zh_cn', 'zh_tw', 'ko_kr'):
            with self.subTest(locale=locale):
                text = json.loads((LANG / f'{locale}.json').read_text(encoding='utf-8'))['gui.singulo.reactor.ignition.need']
                shown = translated_format(text, ('5.0', '10', '25'))
                self.assertRegex(shown, r'5\.0\s*GFE')
                self.assertRegex(shown, r'10\s*(?:s\b|秒|초)')
                self.assertRegex(shown, r'25\s*MFE/t')

    def test_guardian_pages_describe_their_own_encounter(self):
        names = {
            'en_us': ('Foundry Overseer', 'Ergosphere Leviathan'),
            'ja_jp': ('統括AI', 'エルゴ球の大蛇'),
            'zh_cn': ('统筹 AI', '能层巨蛇'),
            'zh_tw': ('統籌 AI', '能層巨蛇'),
            'ko_kr': ('총괄 AI', '에르고구의 거대 뱀'),
        }
        for locale, (foundry, leviathan) in names.items():
            with self.subTest(locale=locale):
                catalog = json.loads((LANG / f'{locale}.json').read_text(encoding='utf-8'))
                f = catalog['guide.singulo.ruins.foundry.body']
                l = catalog['guide.singulo.ruins.leviathan.body']
                self.assertIn(foundry, f.splitlines()[0])
                self.assertNotIn(leviathan, f)
                self.assertIn(leviathan, l.splitlines()[0])
                self.assertNotIn(foundry, l)
                self.assertEqual(len(f.splitlines()), 2)
                self.assertEqual(len(l.splitlines()), 6)

if __name__ == '__main__':
    unittest.main()
