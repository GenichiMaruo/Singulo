"""Prepare an isolated many-hole lensing client world from tested save metadata."""
from pathlib import Path
import argparse
import shutil

parser = argparse.ArgumentParser()
parser.add_argument('--build-dir', default='build')
build = Path(parser.parse_args().build_dir).resolve()
if build == Path.cwd().resolve() or not build.is_relative_to(Path.cwd().resolve()):
    raise SystemExit('The disposable build directory must be inside this checkout')
source = build / 'gametest/world/level.dat'
if not source.is_file():
    raise SystemExit('Run GameTests before preparing the preview world')
root = build / 'lensing-review'
for target in (root / 'saves/Review', root / 'screenshots'):
    if target.exists():
        shutil.rmtree(target)
(root / 'saves/Review').mkdir(parents=True)
shutil.copy2(source, root / 'saves/Review/level.dat')
(root / 'options.txt').write_text('tutorialStep:none\nlang:en_us\nguiScale:2\nmaxFps:260\nenableVsync:false\nrenderDistance:6\nsimulationDistance:5\npauseOnLostFocus:false\nsoundCategory_music:0.0\nonboardAccessibility:false\n', encoding='utf-8')
(root / 'config').mkdir(exist_ok=True)
(root / 'config/fml.toml').write_text('earlyWindowControl = false\n', encoding='utf-8')
