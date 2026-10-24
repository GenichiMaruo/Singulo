"""Check actual PNG player-inventory pixels; independent of NativeImage's in-memory sampling."""
import argparse
_parser = argparse.ArgumentParser()
_parser.add_argument('--build-dir', default='build')
_build = _parser.parse_args().build_dir
import json
import pathlib
import struct
import zlib

def rgba(path, max_row=None):
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
    for y in range(min(height, max_row or height)):
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

root = pathlib.Path(_build + "/gui-review/screenshots")
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
pathlib.Path(_build + "/gui-review/inventory-pixels.json").write_text(json.dumps(results, indent=2), encoding="utf-8")
print(json.dumps(results))

# Actual exported pixels, not just successful screen opens: long machine names must
# not draw their title glyphs into the header status chip, and the NO_POWER chip must show its amber icon.
metrics_path = pathlib.Path(_build + "/gui-review/machine-font-metrics.tsv")
if metrics_path.exists():
    rows = [line.split('\t') for line in metrics_path.read_text().splitlines()]
    if len(rows) != 140:
        raise SystemExit(f"Expected 140 machine GUI fixtures, got {len(rows)}")
    locales = ('ja_jp', 'en_us', 'zh_cn', 'zh_tw', 'ko_kr')
    checked_headers = 0
    rate_reference = None
    for locale in locales:
        entries = [r for r in rows if r[0] == locale]
        if len(entries) != 28:
            raise SystemExit(f"Expected 28 fixtures for {locale}, got {len(entries)}")
        for index, entry in enumerate(entries[:16]):
            _, name, title_width, status_width, panel_width = entry
            panel_width = int(panel_width)
            width, height, channels, pixels = rgba(root / f"machine-{locale}-{index:02d}.png", max_row=120)
            assert (width, height) == (854, 480)
            # Machine frames are 184 tall; the status chip is 19 wide, right edge 19 px from the frame edge.
            left, top = (width - panel_width * 2) // 2, (height - 184 * 2) // 2
            x0, x1 = left + (panel_width - 38) * 2, left + (panel_width - 19) * 2
            colors = [tuple(pixels[y][x * channels:x * channels + 3])
                      for y in range(top + 8, top + 30) for x in range(x0, x1)]
            title_ink = any(45 <= r <= 57 and 58 <= g <= 70 and 68 <= b <= 80 for r, g, b in colors)
            amber_icon = any(r >= 225 and 165 <= g <= 195 and 60 <= b <= 100 for r, g, b in colors)
            if title_ink or not amber_icon:
                raise SystemExit(f"{locale}/{name}: title/status overlap or missing status icon in PNG")
            checked_headers += 1
        # ASCII output is language-independent. Catch stale/corrupt font-atlas pixels
        # after language changes rather than counting an unreadable capture as success.
        width, height, channels, pixels = rgba(root / f"machine-{locale}-18.png", max_row=240)
        left, top = (width - 176 * 2) // 2, (height - 166 * 2) // 2
        rate = [bytes(pixels[y][(left + 44) * channels:(left + 160) * channels])
                for y in range(top + 100, top + 120)]
        if rate_reference is None:
            rate_reference = rate
        elif rate != rate_reference:
            raise SystemExit(f"{locale}: generator ASCII glyph pixels differ after language switch")
    summary = {"languages": list(locales), "machine_captures": len(rows),
               "headers_without_title_status_overlap": checked_headers, "generator_font_probes": 5}
    pathlib.Path(_build + "/gui-review/machine-pixels.json").write_text(json.dumps(summary, indent=2))
    print(json.dumps(summary))
