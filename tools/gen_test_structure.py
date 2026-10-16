# -*- coding: utf-8 -*-
"""GameTest 用の構造物を書き出す。自動化群の作業室は自然地形から隔離する。"""
import gzip
import struct
from pathlib import Path

OUT_DIR = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "data" / "singulo" / "structures"
# 名前 → (幅, 高さ, 奥行き)
TEMPLATES = {"empty": (8, 8, 8), "tall": (5, 13, 5), "large": (9, 11, 9), "huge": (15, 15, 15), "reactor_isolated": (64, 15, 64), "crater": (64, 24, 64)}
DATA_VERSION = 3465  # 1.20.1


def name(s):
    b = s.encode('utf-8')
    return struct.pack('>H', len(b)) + b


def tag_int(key, v):
    return b'\x03' + name(key) + struct.pack('>i', v)


def tag_string(key, v):
    return b'\x08' + name(key) + name(v)


def tag_list(key, elem_type, payloads):
    return b'\x09' + name(key) + bytes([elem_type]) + struct.pack('>i', len(payloads)) + b''.join(payloads)


def write(template, sx, sy, sz, namespace="singulo", sealed=False):
    air = tag_string('Name', 'minecraft:air') + b'\x00'
    palette = [air]
    if sealed:
        palette += [tag_string('Name', 'minecraft:stone') + b'\x00',
                    tag_string('Name', 'minecraft:sea_lantern') + b'\x00']
    blocks = []
    for y in range(sy):
        for z in range(sz):
            for x in range(sx):
                pos = b'\x09' + name('pos') + b'\x03' + struct.pack('>i', 3) + struct.pack('>iii', x, y, z)
                state = 0
                if sealed:
                    # Keep natural lava/water/gravel out while retaining crop light.
                    # Legacy templates begin one block above helper-relative y=0.
                    # station()/workshop() build the floor at y=0; keep template y=0
                    # clear so chairs and mine targets are not embedded in a floor.
                    state = 2 if y == sy - 1 else 1 if x in (0, sx - 1) or z in (0, sz - 1) else 0
                blocks.append(pos + tag_int('state', state) + b'\x00')
    body = (tag_int('DataVersion', DATA_VERSION)
            + b'\x09' + name('size') + b'\x03' + struct.pack('>i', 3) + struct.pack(">iii", sx, sy, sz)
            + tag_list('palette', 10, palette)
            + tag_list('blocks', 10, blocks)
            + tag_list('entities', 10, []))
    root = b'\x0a' + name('') + body + b'\x00'
    out = OUT_DIR.parent.parent / namespace / "structures" / f"{template}.nbt"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(gzip.compress(root))
    print(out)


def main():
    for template, size in TEMPLATES.items():
        write(template, *size)
    write("empty", 16, 8, 16, namespace="singulo_automation", sealed=True)
    write("empty", *TEMPLATES["empty"], namespace="singulo_reactor")
    write("reactor_isolated", *TEMPLATES["reactor_isolated"], namespace="singulo_reactor")
    # 分けて実行する群も同じ空テンプレートを使う。寸法変更時に複製を取り残さない。
    for namespace, templates in {'singulo_mekanism': ('empty',), 'singulo_prototype': ('empty', 'huge'),
                                 'singulo_progression': ('empty',), 'singulo_lab': ('empty', 'huge')}.items():
        target = OUT_DIR.parent.parent / namespace / 'structures'
        target.mkdir(parents=True, exist_ok=True)
        for template in templates:
            (target / f'{template}.nbt').write_bytes((OUT_DIR / f'{template}.nbt').read_bytes())


if __name__ == '__main__':
    main()
