"""Prepare a disposable copy of the GameTest world for a real client GUI smoke run."""
import argparse
_parser = argparse.ArgumentParser()
_parser.add_argument('--loader', choices=('forge', 'neoforge'), default='neoforge')
_build = 'build/' + _parser.parse_args().loader

import pathlib
import shutil

root = pathlib.Path(_build + "/gui-review")
root.mkdir(parents=True, exist_ok=True)
source = pathlib.Path(_build + "/gametest/world")
if not (source / "level.dat").is_file():
    raise SystemExit("GameTest world was not saved; cannot start GUI review")
target = root / "saves/Review"
for stale in (target, root / "screenshots"):
    if stale.exists():
        shutil.rmtree(stale)
for name in ("inventory-pixels.json", "machine-pixels.json", "machine-font-metrics.tsv",
             "sides-font-metrics.tsv", "selector-font-metrics.tsv"):
    (root / name).unlink(missing_ok=True)
shutil.copytree(source, target)
# Retain tested save metadata, but generate fresh flat chunks for GUI fixtures.
# Completed server tests may leave large facilities or forced chunks in their disposable world.
for name in ("region", "entities", "poi", "DIM-1", "DIM1"):
    shutil.rmtree(target / name, ignore_errors=True)
(target / "data/forcedchunks.dat").unlink(missing_ok=True)
(root / "options.txt").write_text(
    "tutorialStep:none\nlang:ja_jp\nguiScale:2\nmaxFps:30\nrenderDistance:2\nsimulationDistance:5\npauseOnLostFocus:false\n",
    encoding="utf-8",
)

# Forge's optional early OpenGL window has a short initialization timeout that
# can expire under software-rendered CI. The actual game window remains tested.
(root / 'config').mkdir(exist_ok=True)
(root / 'config/fml.toml').write_text('earlyWindowControl = false\n', encoding='utf-8')
