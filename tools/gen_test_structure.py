# -*- coding: utf-8 -*-
"""GameTest 用の空の構造物（src/main/resources/data/singulo/structure/*.nbt）を書き出す。"""
import gzip
import struct
from pathlib import Path

OUT_DIR = Path(__file__).resolve().parent.parent / "src" / "main" / "resources" / "data" / "singulo" / "structure"
# 名前 → (幅, 高さ, 奥行き)
TEMPLATES = {"empty": (8, 8, 8), "tall": (5, 13, 5), "large": (9, 11, 9), "huge": (15, 15, 15)}
DATA_VERSION = 3955  # 1.21.1


def name(s):
    b = s.encode('utf-8')
    return struct.pack('>H', len(b)) + b


def tag_int(key, v):
    return b'\x03' + name(key) + struct.pack('>i', v)


def tag_string(key, v):
    return b'\x08' + name(key) + name(v)


def tag_list(key, elem_type, payloads):
    return b'\x09' + name(key) + bytes([elem_type]) + struct.pack('>i', len(payloads)) + b''.join(payloads)


def write(template, sx, sy, sz):
    air = tag_string('Name', 'minecraft:air') + b'\x00'
    blocks = []
    for y in range(sy):
        for z in range(sz):
            for x in range(sx):
                pos = b'\x09' + name('pos') + b'\x03' + struct.pack('>i', 3) + struct.pack('>iii', x, y, z)
                blocks.append(pos + tag_int('state', 0) + b'\x00')
    body = (tag_int('DataVersion', DATA_VERSION)
            + b'\x09' + name('size') + b'\x03' + struct.pack('>i', 3) + struct.pack(">iii", sx, sy, sz)
            + tag_list('palette', 10, [air])
            + tag_list('blocks', 10, blocks)
            + tag_list('entities', 10, []))
    root = b'\x0a' + name('') + body + b'\x00'
    out = OUT_DIR / f"{template}.nbt"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(gzip.compress(root))
    print(out)


def main():
    for template, size in TEMPLATES.items():
        write(template, *size)


if __name__ == '__main__':
    main()
