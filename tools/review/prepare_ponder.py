"""Prepare an isolated Create/Ponder client world from tested save metadata."""
from pathlib import Path
import shutil

source = Path('build/gametest/gametestserver/gametestworld/level.dat')
if not source.is_file():
    raise SystemExit('Run GameTests before preparing the preview world')
root = Path('build/ponder-review')
for target in (root / 'saves/Review', root / 'screenshots'):
    if target.exists():
        shutil.rmtree(target)
(root / 'saves/Review').mkdir(parents=True)
shutil.copy2(source, root / 'saves/Review/level.dat')
# Since 26.1, dimensions/world-generation metadata is stored outside level.dat.
worldgen = source.parent / 'data/minecraft/world_gen_settings.dat'
if not worldgen.is_file():
    raise SystemExit('Missing 26.1 world generation metadata; run GameTests first')
shutil.copytree(source.parent / 'data', root / 'saves/Review/data')
(root / 'options.txt').write_text('tutorialStep:none\nlang:en_us\nguiScale:2\nmaxFps:30\nrenderDistance:2\nsimulationDistance:5\npauseOnLostFocus:false\nsoundCategory_music:0.0\nsoundCategory_master:0.0\nonboardAccessibility:false\n', encoding='utf-8')
(root / 'config').mkdir(exist_ok=True)
(root / 'config/fml.toml').write_text('earlyWindowControl = false\n', encoding='utf-8')
