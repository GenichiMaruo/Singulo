"""Reject a black Ponder scene in the fixed 1280x800 mechanical-press review."""
import argparse
import json
from PIL import Image

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('image', nargs='?', default='build/ponder-review/screenshots/ponder.png')
args = parser.parse_args()
with Image.open(args.image) as source:
    image = source.convert('RGB')
    if image.size != (1280, 800):
        raise SystemExit(f'Unexpected review dimensions: {image.size}')
    # The baseplate occupies this region; dark background and the tooltip are insufficient to pass.
    region = image.crop((450, 350, 800, 570))
    pixels = region.load()
    bright = sum(min(pixels[x, y]) > 120 for y in range(region.height) for x in range(region.width))
    if bright < 5000:
        raise SystemExit(f'Ponder scene appears black: only {bright} bright baseplate pixels')
    print(json.dumps({'scene': 'create:mechanical_press', 'bright_baseplate_pixels': bright, 'minimum': 5000}))
