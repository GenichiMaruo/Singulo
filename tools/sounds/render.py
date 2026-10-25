# -*- coding: utf-8 -*-
"""Singulo の効果音をすべて作り直す（tools/sounds/recipes.py のレシピから、src/main/resources/assets/singulo/sounds/ へ）。

    uv run --no-project --python 3.12 --with numpy --with scipy --with soundfile python tools/sounds/render.py [--only 部分一致] [--check]

- 22050 Hz のモノラル、Vorbis（音質は控えめにして、mod を軽くする）。
- 'loop' は継ぎ目なく繰り返せる長さ（recipes.LOOP 秒）で作る。'shot' は鳴り終わりの無音を切る。
- variants のある音は <path>_1.ogg … に分けて書き、番号なしのファイルは消す（gen_data が番号つきを束ねる）。
- --check: tools/gen_data.py の SOUNDS のパスに、レシピがそろっているかだけ確かめる。
"""
import argparse
import ast
import re
import sys
from pathlib import Path

import numpy as np
import soundfile as sf

sys.path.insert(0, str(Path(__file__).resolve().parent))
from dsp import SR, loop, normalize, fade  # noqa: E402
from recipes import RECIPES, LOOP  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'src' / 'main' / 'resources' / 'assets' / 'singulo' / 'sounds'
QUALITY = 0.68          # libsndfile の compression_level（大きいほど小さく、音は荒くなる）


def sound_paths():
    """tools/gen_data.py の SOUNDS（イベント名 → (パス, …)）から、音のファイルのパスを読む。"""
    src = (ROOT / 'tools' / 'gen_data.py').read_text(encoding='utf-8')
    start = src.index('SOUNDS = {')
    end = src.index('\n}\n', start)
    table = ast.literal_eval(src[start + len('SOUNDS = '):end + 2])
    return {v[0] for v in table.values()}


def trim(x, floor_db=-60):
    """鳴り終わりの無音（floor_db より小さい所）を切って、短くフェードする。"""
    thr = 10 ** (floor_db / 20) * np.max(np.abs(x))
    idx = np.nonzero(np.abs(x) > thr)[0]
    end = min(len(x), (idx[-1] if len(idx) else len(x)) + int(0.05 * SR))
    return fade(x[:end], 0.002, min(0.08, end / SR / 4))


def write(path, x):
    path.parent.mkdir(parents=True, exist_ok=True)
    x = np.clip(x, -1, 1).astype('float32')
    with sf.SoundFile(str(path), 'w', SR, 1, format='OGG', subtype='VORBIS', compression_level=QUALITY) as f:
        for i in range(0, len(x), 4096):            # 一度に大きく書くと、libsndfile が落ちることがある
            f.write(x[i:i + 4096])


def render(path, spec):
    seeds = range(1, spec['variants'] + 1) if spec['variants'] else [0]
    written = []
    for v in seeds:
        seed = _seed(path) + v * 101
        if spec['kind'] == 'loop':
            x = loop(lambda dur: spec['fn'](dur, seed), LOOP)
            x = normalize(x, -3.0, spec['level'])
        else:
            x = trim(np.asarray(spec['fn'](None, seed), dtype=float))
            x = normalize(x, -1.0, spec['level'])
        name = f'{path}_{v}.ogg' if spec['variants'] else f'{path}.ogg'
        write(OUT / name, x)
        written.append(name)
    # 版の数が変わったときの、古いファイルを消す
    base = OUT / f'{path}.ogg'
    if spec['variants'] and base.exists():
        base.unlink()
    for old in (OUT / path).parent.glob(f'{Path(path).name}_*.ogg'):
        tail = old.stem[len(Path(path).name) + 1:]
        if tail.isdigit() and (not spec['variants'] or int(tail) > spec['variants']):
            old.unlink()
    return written


def _seed(path):
    """パスから決まる乱数の種（Python の hash は実行ごとに変わるので、自前で計算する）。"""
    h = 0
    for c in path:
        h = (h * 131 + ord(c)) % 1_000_003
    return h


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--only', default='')
    ap.add_argument('--check', action='store_true')
    a = ap.parse_args()
    paths = sound_paths()
    missing = sorted(paths - set(RECIPES))
    extra = sorted(set(RECIPES) - paths)
    if missing or extra:
        print('レシピがない音:', missing)
        print('SOUNDS にない音（gen_data に足す）:', extra)
        if a.check:
            sys.exit(1)
    if a.check:
        print('ok', len(paths))
        return
    total = 0
    for path, spec in RECIPES.items():
        if a.only and not re.search(a.only, path):
            continue
        for name in render(path, spec):
            total += (OUT / name).stat().st_size
            print(f'{(OUT / name).stat().st_size / 1024:7.1f} KB  {name}', flush=True)
    print(f'合計 {total / 1e6:.2f} MB')


if __name__ == '__main__':
    main()
