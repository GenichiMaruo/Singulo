"""Prepare a disposable copy of the GameTest world for a real client GUI smoke run."""
import pathlib
import shutil

root = pathlib.Path("build/gui-review")
root.mkdir(parents=True, exist_ok=True)
source = pathlib.Path("build/gametest/world")
if not (source / "level.dat").is_file():
    raise SystemExit("GameTest world was not saved; cannot start GUI review")
target = root / "saves/Review"
if target.exists():
    shutil.rmtree(target)
shutil.copytree(source, target)
(root / "options.txt").write_text(
    "lang:ja_jp\nguiScale:2\nmaxFps:30\nrenderDistance:2\nsimulationDistance:2\npauseOnLostFocus:false\n",
    encoding="utf-8",
)
