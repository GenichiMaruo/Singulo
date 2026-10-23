"""Create a disposable world for the artificial-sunlight client review."""
from pathlib import Path
import shutil

source = Path('build/gametest/world')
if not (source / 'level.dat').is_file():
    raise SystemExit('Run GameTests first to create a disposable world')
root = Path('build/rayleigh-review')
root.mkdir(parents=True, exist_ok=True)
for target in (root / 'saves/Review', root / 'screenshots'):
    if target.exists():
        shutil.rmtree(target)
shutil.copytree(source, root / 'saves/Review')
(root / 'options.txt').write_text(
    'lang:ja_jp\nguiScale:2\nmaxFps:30\nrenderDistance:4\nsimulationDistance:5\npauseOnLostFocus:false\nonboardAccessibility:false\n',
    encoding='utf-8')
