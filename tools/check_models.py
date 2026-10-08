"""生成したモデルを確かめる。

1. 装置・ケーブルのブロックモデルの要素が1マス（0〜16）に収まっているか（恩恵の模様の重ね絵は除く）
2. 同じ向きの面が同じ平面で重なっていないか（重なるとちらつく）。回転のある要素どうしは、回転が同じものだけ比べる

使い方: uv run --no-project --python 3.12 tools/check_models.py
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / 'src' / 'generated' / 'resources' / 'assets' / 'singulo' / 'models'
EPS = 1e-6
CABLES = ('copper_wire', 'superconducting_cable', 'topological_wire', 'horizon_bus')

# 面 → (固定する軸, 値は from/to のどちら, 面の2つの軸)
FACES = {'north': (2, 0, (0, 1)), 'south': (2, 1, (0, 1)), 'west': (0, 0, (1, 2)), 'east': (0, 1, (1, 2)),
         'down': (1, 0, (0, 2)), 'up': (1, 1, (0, 2))}


def faces_of(e):
    f, t = e['from'], e['to']
    for name in e.get('faces', {}):
        axis, side, (a, b) = FACES[name]
        plane = (f if side == 0 else t)[axis]
        rect = (min(f[a], t[a]), min(f[b], t[b]), max(f[a], t[a]), max(f[b], t[b]))
        if rect[2] - rect[0] < EPS or rect[3] - rect[1] < EPS:
            continue
        yield name, plane, rect, json.dumps(e.get('rotation'), sort_keys=True)


def overlap(r1, r2):
    return min(r1[2], r2[2]) - max(r1[0], r2[0]) > EPS and min(r1[3], r2[3]) - max(r1[1], r2[1]) > EPS


def main():
    problems = []
    for path in sorted(ROOT.rglob('*.json')):
        model = json.loads(path.read_text(encoding='utf-8'))
        els = model.get('elements')
        if not els:
            continue
        rel = path.relative_to(ROOT).as_posix()
        if rel.startswith('block/') and 'boost_overlay' not in rel:
            for e in els:
                if min(e['from'] + e['to']) < -EPS or max(e['from'] + e['to']) > 16 + EPS:
                    problems.append(f'{rel}: 1マスからはみ出している {e["from"]} → {e["to"]}')
        if any(rel.startswith(f'block/{c}') or rel == f'item/{c}.json' for c in CABLES):
            # ケーブル: 座標は整数（1ドットの幅は1ドットだけ）、断面の幅は8ドットまで
            for e in els:
                for v in e['from'] + e['to']:
                    if abs(v - round(v)) > EPS:
                        problems.append(f'{rel}: 整数でない座標 {e["from"]} → {e["to"]}')
                        break
            for e in els:
                f, t = e['from'], e['to']
                long = max(range(3), key=lambda k: t[k] - f[k]) if rel.startswith('item/') else None
                for k in range(3):
                    if k != long and t[k] - f[k] > 8 + EPS and 'core' not in rel:
                        problems.append(f'{rel}: 幅が8ドットを超える {f} → {t}')
        if any(rel.startswith(f'block/{c}') or rel == f'item/{c}.json' for c in CABLES):
            # 1ドットを1ドットのまま貼っているか（UV の大きさ＝面の大きさ。90°・270°回すときは縦横を入れ替える）
            for e in els:
                for name, spec in e.get('faces', {}).items():
                    axis, side_, (a, b) = FACES[name]
                    size = (abs(e['to'][a] - e['from'][a]), abs(e['to'][b] - e['from'][b]))
                    uv = spec.get('uv')
                    if uv is None:
                        continue
                    uvs = (abs(uv[2] - uv[0]), abs(uv[3] - uv[1]))
                    if spec.get('rotation', 0) in (90, 270):
                        uvs = (uvs[1], uvs[0])
                    if name in ('up', 'down'):
                        size = (size[0], size[1])
                    if sorted(uvs) != sorted(size) or abs(uvs[0] - size[0]) > EPS and abs(uvs[0] - size[1]) > EPS:
                        problems.append(f'{rel}: {name} 面のテクスチャが引き伸ばされている（面 {size}、UV {uvs}）')
        faces = []
        for i, e in enumerate(els):
            for face in faces_of(e):
                faces.append((i,) + face)
        for x in range(len(faces)):
            for y in range(x + 1, len(faces)):
                i, n1, p1, r1, rot1 = faces[x]
                j, n2, p2, r2, rot2 = faces[y]
                if i != j and n1 == n2 and rot1 == rot2 and abs(p1 - p2) < EPS and overlap(r1, r2):
                    problems.append(f'{rel}: 要素{i}と要素{j}の {n1} 面が重なる（平面 {p1}）')
    for p in problems:
        print('x', p)
    print(f'確認したモデルの問題: {len(problems)} 件')
    sys.exit(1 if problems else 0)


if __name__ == '__main__':
    main()
