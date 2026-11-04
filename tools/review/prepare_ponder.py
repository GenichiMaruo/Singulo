"""Prepare an isolated legacy Create/Ponder client world from tested save metadata."""
import argparse
from pathlib import Path
import shutil

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--loader', choices=('forge', 'neoforge'), default='forge')
args = parser.parse_args()
base = Path('build') / args.loader
source = base / 'gametest/world/level.dat'
if not source.is_file():
    raise SystemExit('Run GameTests before preparing the preview world')
root = base / 'ponder-review'
for target in (root / 'saves/Review', root / 'screenshots'):
    if target.exists():
        shutil.rmtree(target)
(root / 'saves/Review').mkdir(parents=True)
shutil.copy2(source, root / 'saves/Review/level.dat')
(root / 'options.txt').write_text('tutorialStep:none\nlang:en_us\nguiScale:2\nmaxFps:30\nrenderDistance:2\nsimulationDistance:5\npauseOnLostFocus:false\nonboardAccessibility:false\n', encoding='utf-8')
(root / 'config').mkdir(exist_ok=True)
(root / 'config/fml.toml').write_text('earlyWindowControl = false\n', encoding='utf-8')

(root / 'config/oculus.properties').write_text('enableShaders=false\nshaderPack=\n', encoding='utf-8')
