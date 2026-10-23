"""Create a disposable world for the artificial-sunlight client review."""
from pathlib import Path
import argparse
_parser = argparse.ArgumentParser()
_parser.add_argument("--loader", choices=("forge", "neoforge"), default="neoforge")
_build = "build/" + _parser.parse_args().loader
import shutil

source = Path(_build + '/gametest/world')
if not (source / 'level.dat').is_file():
    raise SystemExit('Run GameTests first to create a disposable world')
root = Path(_build + '/rayleigh-review')
root.mkdir(parents=True, exist_ok=True)
for target in (root / 'saves/Review', root / 'screenshots'):
    if target.exists():
        shutil.rmtree(target)
shutil.copytree(source, root / 'saves/Review')
(root / 'options.txt').write_text(
    'lang:ja_jp\nguiScale:2\nmaxFps:30\nrenderDistance:4\nsimulationDistance:5\npauseOnLostFocus:false\nonboardAccessibility:false\n',
    encoding='utf-8')
