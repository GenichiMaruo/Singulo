"""Compare the same unobstructed floor under natural noon and artificial noon at rainy midnight."""
import argparse
import json
from pathlib import Path
from PIL import Image, ImageChops, ImageStat, ImageDraw

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('directory', type=Path)
a = p.parse_args()
names = ['01-clear-noon-reference', '02-powered-rainy-night', '03-unpowered-rainy-night', '04-repowered-rainy-night']
images = [Image.open(a.directory / (name + '.png')).convert('RGB') for name in names]
if any(image.size != (1280, 800) for image in images):
    raise SystemExit('Expected four full-size 1280 x 800 captures')
region = (500, 350, 780, 650)
crops = [image.crop(region) for image in images]
if ImageChops.difference(crops[0], crops[1]).getbbox() or ImageChops.difference(crops[1], crops[3]).getbbox():
    raise SystemExit('Artificial noon differs from clear noon, or does not restore after powering on')
darkening = [x - y for x, y in zip(ImageStat.Stat(crops[1]).mean, ImageStat.Stat(crops[2]).mean)]
if min(darkening) < 100:
    raise SystemExit('Power-off capture did not become dark')
result = {'region': region, 'compared_pixels': 84000, 'different_RGB_channels_noon_vs_rainy_night': 0,
          'different_RGB_channels_powered_vs_repowered': 0, 'power_off_mean_RGB_drop': darkening}
(a.directory / 'pixels.json').write_text(json.dumps(result, indent=2) + '\n')
canvas = Image.new('RGB', (1120, 330), '#20252b')
draw = ImageDraw.Draw(canvas)
for i, (crop, label) in enumerate(zip(crops, ['Clear noon reference', 'Rainy midnight: ON', 'Rainy midnight: OFF', 'Rainy midnight: ON again'])):
    canvas.paste(crop, (280 * i, 30)); draw.text((280 * i + 8, 10), label, fill='white')
canvas.save(a.directory / 'comparison.png')
print(json.dumps(result))
