"""Check actual PNG player-inventory pixels; independent of NativeImage's in-memory sampling."""
import json
import pathlib
import struct
import zlib

def rgba(path):
    data = path.read_bytes()
    assert data[:8] == b"\x89PNG\r\n\x1a\n"
    at, payload = 8, bytearray()
    while at < len(data):
        size = struct.unpack(">I", data[at:at + 4])[0]
        kind = data[at + 4:at + 8]
        body = data[at + 8:at + 8 + size]
        at += size + 12
        if kind == b"IHDR":
            width, height, depth, color, compression, filtering, interlace = struct.unpack(">IIBBBBB", body)
            assert depth == 8 and color in (2, 6) and interlace == 0
        elif kind == b"IDAT":
            payload.extend(body)
    channels = 4 if color == 6 else 3
    stride = width * channels
    raw = zlib.decompress(payload)
    previous = bytearray(stride)
    rows = []
    def paeth(a, b, c):
        p = a + b - c
        distances = abs(p - a), abs(p - b), abs(p - c)
        return (a, b, c)[distances.index(min(distances))]
    for y in range(height):
        start = y * (stride + 1)
        mode, row = raw[start], bytearray(raw[start + 1:start + 1 + stride])
        for x in range(stride):
            a = row[x - channels] if x >= channels else 0
            b = previous[x]
            c = previous[x - channels] if x >= channels else 0
            predictor = (0, a, b, (a + b) // 2, paeth(a, b, c))[mode]
            row[x] = (row[x] + predictor) & 255
        rows.append(row)
        previous = row
    return width, height, channels, rows

root = pathlib.Path("build/gui-review/screenshots")
results = []
for name in ("android-status", "android-gear", "android-decor"):
    width, height, channels, rows = rgba(root / (name + ".png"))
    assert (width, height) == (1280, 800)
    left, top, scale = (640 - 210) // 2, (400 - 216) // 2, 2
    points = [(25 + col * 18 + 8, 134 + row * 18 + 8) for row in range(3) for col in range(9)]
    points += [(25 + col * 18 + 8, 192 + 8) for col in range(1, 9)]
    pixels = []
    for x, y in points:
        x, y = (left + x) * scale, (top + y) * scale
        pixel = tuple(rows[y][x * channels:x * channels + 3])
        pixels.append(pixel)
        if pixel != (139, 143, 152):
            raise SystemExit(f"{name}: missing player inventory at {x},{y}: {pixel}")
    results.append({"screen": name, "checked_empty_slot_centers": len(points), "rgb": pixels[0]})
pathlib.Path("build/gui-review/inventory-pixels.json").write_text(json.dumps(results, indent=2), encoding="utf-8")
print(json.dumps(results))
