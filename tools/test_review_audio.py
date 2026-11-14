"""Disposable validation clients mute music, not master/effects or personal settings."""
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


class ReviewAudioTests(unittest.TestCase):
    def test_generated_review_options_mute_only_music(self):
        repo = Path(__file__).resolve().parent.parent
        cases = [(name, [], name + '-review') for name in
                 ('gui', 'lensing', 'occlusion', 'ponder', 'rayleigh', 'stage6', 'v011')]
        cases += [('gui', ['--cosmic'], 'cosmic-gui-review'),
                  ('gui', ['--cosmic-world'], 'cosmic-review')]
        with tempfile.TemporaryDirectory(prefix='singulo-review-audio-') as tmp:
            root = Path(tmp).resolve()
            source = root / 'build/gametest/world/level.dat'
            source.parent.mkdir(parents=True)
            source.write_bytes(b'disposable-test-fixture')
            for name, args, directory in cases:
                with self.subTest(review=directory):
                    subprocess.run([sys.executable, str(repo / 'tools/review' / f'prepare_{name}.py'), *args],
                                   cwd=root, check=True, capture_output=True, text=True)
                    options = root / 'build' / directory / 'options.txt'
                    self.assertTrue(options.resolve().is_relative_to(root))
                    audio = [line for line in options.read_text(encoding='utf-8').splitlines()
                             if line.startswith('soundCategory_')]
                    self.assertEqual(audio, ['soundCategory_music:0.0'])


if __name__ == '__main__':
    unittest.main()
