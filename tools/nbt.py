# -*- coding: utf-8 -*-
"""最小限のNBT書き出し（構造物ファイル用）。

Python の値を型から推測して書く:
    str → String, bool → Byte, int → Int, float → Double,
    dict → Compound, list[int] → List(Int), list[dict] → List(Compound), list[str] → List(String)
"""
import gzip
import struct

END, BYTE, INT, DOUBLE, STRING, LIST, COMPOUND = 0, 1, 3, 6, 8, 9, 10


def _name(s):
    b = s.encode('utf-8')
    return struct.pack('>H', len(b)) + b


def _type_of(v):
    if isinstance(v, bool):
        return BYTE
    if isinstance(v, int):
        return INT
    if isinstance(v, float):
        return DOUBLE
    if isinstance(v, str):
        return STRING
    if isinstance(v, list):
        return LIST
    if isinstance(v, dict):
        return COMPOUND
    raise TypeError(f'NBTにできない値: {v!r}')


def _payload(v):
    t = _type_of(v)
    if t == BYTE:
        return struct.pack('>b', 1 if v else 0)
    if t == INT:
        return struct.pack('>i', v)
    if t == DOUBLE:
        return struct.pack('>d', v)
    if t == STRING:
        return _name(v)
    if t == LIST:
        elem = _type_of(v[0]) if v else END
        return bytes([elem]) + struct.pack('>i', len(v)) + b''.join(_payload(x) for x in v)
    body = b''.join(bytes([_type_of(val)]) + _name(key) + _payload(val) for key, val in v.items())
    return body + bytes([END])


def write_gzip(path, root):
    data = bytes([COMPOUND]) + _name('') + _payload(root)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(gzip.compress(data))
