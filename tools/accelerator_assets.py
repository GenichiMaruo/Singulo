"""Continuous accelerator tracks, shared by standard and HD resource packs."""
import json
import math
import random
from pathlib import Path
from PIL import Image

SEGMENTS = ('north', 'east', 'south', 'west',
            'north_west', 'north_east', 'south_east', 'south_west')
PARTS = ('accelerator_tube', 'focusing_magnet')


def track(size, magnet=False, corner=False):
    """Quiet brushed casing and restrained coil detail around an unbroken track."""
    image = Image.new('RGBA', (size, size))
    scale = size / 16
    rng = random.Random(731 + 97 * magnet + 13 * corner + size)
    distances = {}

    def finish(base, x, y, strength=1.0):
        # Small grain plus slowly varying reflectance, as in art16.weathered.
        grain = rng.uniform(-.8, .8) * strength
        sheen = (math.sin(x / scale * .7 + 2) * math.cos(y / scale * .5) * 1.2
                 + (x - y) / size * .8) * strength
        return tuple(max(0, min(255, round(c + grain + sheen))) for c in base) + (255,)

    for y in range(size):
        for x in range(size):
            signed = ((math.hypot(x - (size - .5), y - (size - .5)) - size / 2)
                      if corner else y - (size - 1) / 2) / scale
            distance = abs(signed)
            distances[x, y] = distance
            along = (math.atan2(size - .5 - y, size - .5 - x) * 32 / math.pi
                     if corner else x / scale)
            base = (206, 212, 220)
            # Recessed red/blue windings retain the original magnet identity.
            if magnet and 1.5 <= x / scale < 14.5 and 1.5 <= y / scale < 14.5:
                red = signed < 0
                base = (172, 126, 130) if red else (126, 149, 181)
                phase = int(along) % 6
                if phase == 0:
                    base = (179, 133, 137) if red else (133, 156, 188)
                elif phase == 1:
                    base = (169, 123, 127) if red else (123, 146, 178)
            # One narrow, shallow retaining band; no bright bevel stack or rivets.
            if 2 <= along < 3:
                base = (194, 202, 212)
            # Very light edge shading keeps adjoining blocks visually calm.
            edge = min(x, y, size - 1 - x, size - 1 - y) / scale
            if edge < 1:
                base = tuple(round(c * .98) for c in base)
            elif edge < 2:
                base = tuple(round(c * .995) for c in base)
            color = finish(base, x, y)
            if 3 <= distance < 4:
                color = finish((214, 223, 231) if signed < 0 else (185, 197, 209), x, y, .6)
            # Keep the full beam cross section exact at every adjoining edge.
            if distance < 3:
                color = (112, 138, 158, 255)
            if distance < 2:
                color = (40, 72, 92, 255)
            if distance < 1:
                color = (170, 242, 255, 255)
            image.putpixel((x, y), color)

    # Paired light/dark marks suggest brushing and tiny surface scuffs, not grime.
    for _ in range(1 if size == 16 else 2):
        x = rng.randrange(2, size - 3)
        y = rng.randrange(2, size - 2)
        for dx in range(max(2, round(2 * scale))):
            px = x + dx
            if px >= size - 2 or distances[px, y] < 4:
                continue
            old = image.getpixel((px, y))
            delta = 3 if dx == 0 else 2
            image.putpixel((px, y), tuple(min(255, c + delta) for c in old[:3]) + (255,))
            if distances[px, y + 1] >= 4:
                old = image.getpixel((px, y + 1))
                image.putpixel((px, y + 1), tuple(max(0, c - 1) for c in old[:3]) + (255,))

    return image


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')


def models(assets):
    for part in PARTS:
        variants = {'segment=none': {'model': f'singulo:block/{part}'}}
        for segment in SEGMENTS:
            name = f'{part}_{segment}'
            texture = f'singulo:block/{name}'
            write_json(assets / 'models' / 'block' / f'{name}.json', {
                'parent': 'minecraft:block/cube',
                'textures': {'particle': f'singulo:block/{part}_connected_side',
                             'up': texture, 'down': texture + '_bottom',
                             **{face: f'singulo:block/{part}_connected_side'
                                for face in ('north', 'east', 'south', 'west')}}})
            variants[f'segment={segment}'] = {'model': f'singulo:block/{name}'}
        write_json(assets / 'blockstates' / f'{part}.json', {'variants': variants})


def textures(block_root, size):
    block_root.mkdir(parents=True, exist_ok=True)
    pulse_texture(block_root.parent / 'misc', size * 2, max(8, size // 2))
    for part in PARTS:
        magnet = part == 'focusing_magnet'
        straight, corner = track(size, magnet), track(size, magnet, True)
        for index, segment in enumerate(SEGMENTS):
            image = straight if index < 4 else corner
            image = image.rotate(-90 * (index % 4))
            image.save(block_root / f'{part}_{segment}.png')
            image.transpose(Image.Transpose.FLIP_TOP_BOTTOM).save(block_root / f'{part}_{segment}_bottom.png')
        straight.save(block_root / f'{part}_connected_side.png')


def pulse_texture(root, width, height):
    """Soft beam cross section with a bright moving head and a fading trail."""
    image = Image.new('RGBA', (width, height))
    for y in range(height):
        v = abs(y / (height - 1) * 2 - 1)
        cross = max(0, 1 - v * v) ** 3
        for x in range(width):
            u = x / (width - 1)
            envelope = u ** 1.6 * min(1, (1 - u) * 16)
            alpha = round(255 * cross * envelope)
            image.putpixel((x, y), (255, 255, 255, alpha))
    root.mkdir(parents=True, exist_ok=True)
    image.save(root / 'accelerator_pulse.png')


def generate(assets, hd_assets):
    models(assets)
    textures(assets / 'textures' / 'block', 16)
    textures(hd_assets / 'textures' / 'block', 32)


if __name__ == '__main__':
    root = Path(__file__).resolve().parent.parent / 'src' / 'generated' / 'resources'
    generate(root / 'assets' / 'singulo',
             root / 'resourcepacks' / 'singulo_hd' / 'assets' / 'singulo')
