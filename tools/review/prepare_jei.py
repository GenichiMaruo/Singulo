"""Create an isolated legacy client world for JEI transfer and capacity-slot review."""
import argparse
from pathlib import Path
import shutil

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--loader', choices=('forge', 'neoforge'), default='forge')
args = parser.parse_args()
root = Path('build') / args.loader
source = root / 'gametest/world'
if not (source / 'level.dat').is_file():
    raise SystemExit('Run GameTests first to create the disposable world metadata')
review = root / 'jei-review'
world = review / 'saves/Review'
for path in (world, review / 'screenshots'):
    if path.exists(): shutil.rmtree(path)
world.mkdir(parents=True)
shutil.copy2(source / 'level.dat', world / 'level.dat')
if (source / 'serverconfig').is_dir():
    shutil.copytree(source / 'serverconfig', world / 'serverconfig')
(review / 'options.txt').write_text('lang:ja_jp\nguiScale:2\nmaxFps:30\nrenderDistance:2\nsimulationDistance:5\npauseOnLostFocus:false\nonboardAccessibility:false\n')
(review / 'config').mkdir(exist_ok=True)
(review / 'config/fml.toml').write_text('earlyWindowControl = false\n')
