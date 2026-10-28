"""Check actual PNG player-inventory pixels; independent of NativeImage's in-memory sampling."""
import argparse
_parser = argparse.ArgumentParser()
_parser.add_argument('--build-dir', default='build')
_build = _parser.parse_args().build_dir
import json
import pathlib
from PIL import Image


def rgba(path, max_row=None):
    # Decode PNG filters in Pillow's native code. The former Python byte-by-byte
    # decoder computed all five filter predictors for every channel of every row.
    with Image.open(path) as source:
        if source.format != "PNG":
            raise ValueError("Not a PNG: " + str(path))
        image = source.convert("RGBA")
        width, height = image.size
        if max_row is not None:
            image = image.crop((0, 0, width, min(height, max_row)))
        raw = image.tobytes()
    stride = width * 4
    return width, height, 4, [raw[i:i + stride] for i in range(0, len(raw), stride)]

root = pathlib.Path(_build + "/gui-review/screenshots")
results = []
for name in ("android-status", "android-gear", "android-decor"):
    width, height, channels, rows = rgba(root / (name + ".png"))
    assert (width, height) == (1280, 800)
    # The android skin tints the slot fill with the android's accent, so check that every empty slot centre
    # shows the same mid-grey fill rather than one fixed colour.
    left, top, scale = (640 - 212) // 2, (400 - 216) // 2, 2
    inv_x = (212 - 162) // 2 + 1
    points = [(inv_x + col * 18 + 8, 134 + row * 18 + 8) for row in range(3) for col in range(9)]
    points += [(inv_x + col * 18 + 8, 192 + 8) for col in range(1, 9)]
    pixels = []
    for x, y in points:
        x, y = (left + x) * scale, (top + y) * scale
        pixel = tuple(rows[y][x * channels:x * channels + 3])
        pixels.append(pixel)
        if pixel != pixels[0] or not all(60 <= c <= 150 for c in pixel):
            raise SystemExit(f"{name}: missing player inventory at {x},{y}: {pixel}")
    results.append({"screen": name, "checked_empty_slot_centers": len(points), "rgb": pixels[0]})
pathlib.Path(_build + "/gui-review/inventory-pixels.json").write_text(json.dumps(results, indent=2), encoding="utf-8")
print(json.dumps(results))

# Actual exported pixels, not just successful screen opens: long machine names must
# not draw their title glyphs into the header status chip, and the NO_POWER chip must show its amber icon.
metrics_path = pathlib.Path(_build + "/gui-review/machine-font-metrics.tsv")
if metrics_path.exists():
    rows = [line.split('\t') for line in metrics_path.read_text().splitlines()]
    if len(rows) != 205:
        raise SystemExit(f"Expected 205 machine GUI fixtures, got {len(rows)}")
    locales = ('ja_jp', 'en_us', 'zh_cn', 'zh_tw', 'ko_kr')
    checked_headers = 0
    rate_reference = None
    for locale in locales:
        entries = [r for r in rows if r[0] == locale]
        if len(entries) != 41:
            raise SystemExit(f"Expected 41 fixtures for {locale}, got {len(entries)}")
        for index in range(len(entries)):
            capture = root / f"machine-{locale}-{index:02d}.png"
            with Image.open(capture) as png:
                if png.format != 'PNG' or png.size != (854, 480) or png.convert('RGB').getbbox() is None:
                    raise SystemExit(f"{locale}/{index}: invalid, blank or wrong-sized GUI capture")
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
    supply_slots = 0
    for locale in locales:
        entries = [r for r in rows if r[0] == locale]
        captures = []
        for state in range(3):
            matches = [i for i, row in enumerate(entries) if row[1] == f"supply-{state}"]
            if len(matches) != 1:
                raise SystemExit(f"{locale}: missing/duplicate supply port fixture")
            capture = root / f"machine-{locale}-{matches[0]:02d}.png"
            width, height, channels, pixels = rgba(capture)
            if (width, height) != (854, 480):
                raise SystemExit("Unexpected supply port viewport")
            captures.append(capture.read_bytes())
            if state == 2:
                continue  # The intentionally open tooltip may cover inventory slots.
            left, top = (width - 176 * 2) // 2, (height - 206 * 2) // 2
            # GuiFrame inventoryY(206)=124, hotbarY(206)=182; slot centers add 8.
            points = [(8 + col * 18 + 8, 124 + row * 18 + 8) for row in range(3) for col in range(9)]
            points += [(8 + col * 18 + 8, 182 + 8) for col in range(1, 9)]
            samples = [tuple(pixels[top + y * 2][(left + x * 2) * channels:(left + x * 2) * channels + 3]) for x, y in points]
            if len(set(samples)) != 1 or not all(60 <= c <= 150 for c in samples[0]):
                raise SystemExit(f"{locale}: supply port inventory is missing/overlapped")
            supply_slots += len(samples)
        if captures[1] == captures[2]:
            raise SystemExit(f"{locale}: supply port tooltip was not rendered")
    summary = {"languages": list(locales), "machine_captures": len(rows),
               "headers_without_title_status_overlap": checked_headers, "generator_font_probes": 5, "supply_port_inventory_slots": supply_slots, "supply_port_tooltips": 5}
    pathlib.Path(_build + "/gui-review/machine-pixels.json").write_text(json.dumps(summary, indent=2))
    print(json.dumps(summary))
