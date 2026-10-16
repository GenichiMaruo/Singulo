"""Copy a fresh GameTest world for the disposable stage 6 visual review."""
from pathlib import Path
import shutil

root = Path('build/stage6-review')
source = Path('build/gametest/world')
if not (source / 'level.dat').is_file():
    raise SystemExit('Run GameTests first to create a disposable world')
root.mkdir(parents=True, exist_ok=True)
for target in (root / 'saves/Review', root / 'screenshots'):
    if target.exists():
        shutil.rmtree(target)
shutil.copytree(source, root / 'saves/Review')
(root / 'options.txt').write_text(
    'lang:ja_jp\nguiScale:2\nmaxFps:30\nrenderDistance:3\nsimulationDistance:5\npauseOnLostFocus:false\n', encoding='utf-8')
