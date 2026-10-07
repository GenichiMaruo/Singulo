"""Singulo の標準テクスチャ（16×16）。

- 表面: のっぺりさせず、わずかな濃淡のむらと縁の陰、細かな擦れ（汚れではない使用感）を入れる。
- 線: 段階色の発光ラインは中央を横切らず、外装の縁に沿って内側を一周する。
- 段階: 1 銅の角金具、2 霜の角と二重の縁、3 菫色の点線の縁、4 金の縁と歯車の刻み、5 黒地に金の角金具と白い光の縁。
- 正面: 装置ごとの 8×8 の絵柄。段階3以上は稼働中に動く（縦に並べたコマ）。
"""
import math
import random

from PIL import Image, ImageDraw

S = 16

TIERS = {
    1: dict(base=(232, 234, 236), dark=(170, 176, 184), trim=(190, 118, 66), glow=(110, 205, 238)),
    2: dict(base=(226, 236, 244), dark=(160, 178, 196), trim=(130, 150, 172), glow=(150, 232, 255)),
    3: dict(base=(232, 230, 244), dark=(176, 168, 204), trim=(98, 76, 152), glow=(190, 160, 255)),
    4: dict(base=(240, 235, 222), dark=(190, 176, 150), trim=(196, 154, 72), glow=(255, 190, 92)),
    5: dict(base=(44, 44, 54), dark=(18, 18, 24), trim=(220, 188, 108), glow=(250, 250, 255)),
}


def rgba(c, a=255):
    return tuple(int(v) for v in c[:3]) + (a,)


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def lighten(c, t):
    return mix(c, (255, 255, 255), t)


def darken(c, t):
    return mix(c, (0, 0, 0), t)


def new(size=S, fill=(0, 0, 0, 0)):
    im = Image.new('RGBA', (size, size), fill)
    return im, ImageDraw.Draw(im)


def save(im, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    im.save(path)


def weathered(base, seed, edge=True, strength=1.0):
    """わずかなむらと縁の陰、擦れのある面。"""
    rnd = random.Random(seed)
    im, d = new()
    for y in range(S):
        for x in range(S):
            n = (rnd.random() - 0.5) * 0.06 * strength
            # 大きなむら（ゆっくり変わる明るさ）
            n += 0.025 * strength * math.sin(x * 0.7 + seed) * math.cos(y * 0.5 + seed * 0.3)
            c = lighten(base, n) if n > 0 else darken(base, -n)
            if edge:
                e = min(x, y, S - 1 - x, S - 1 - y)
                if e == 0:
                    c = darken(c, 0.10)
                elif e == 1:
                    c = darken(c, 0.03)
            d.point((x, y), fill=rgba(c))
    # 細かな擦れ（明るい短い線）
    for _ in range(int(3 * strength)):
        x, y = rnd.randrange(2, S - 3), rnd.randrange(2, S - 2)
        c = im.getpixel((x, y))
        d.point((x, y), fill=rgba(lighten(c, 0.08)))
        d.point((x + 1, y), fill=rgba(lighten(c, 0.05)))
    return im


def edge_line(d, inset, color, gap=1, dotted=False):
    """縁に沿って内側を一周する線（角は gap だけ空ける）。"""
    a, b = inset, S - 1 - inset
    pts = []
    for i in range(a + gap, b - gap + 1):
        pts += [(i, a), (i, b), (a, i), (b, i)]
    for k, p in enumerate(pts):
        if dotted and (p[0] + p[1]) % 2:
            continue
        d.point(p, fill=color)


def casing(stage, face='side', seed=0):
    t = TIERS[stage]
    base, dark, trim, glow = t['base'], t['dark'], t['trim'], t['glow']
    im = weathered(base, seed=stage * 31 + seed + (0 if face == 'side' else 7 if face == 'top' else 13))
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, S - 1, S - 1], outline=rgba(dark))
    if face == 'bottom':
        for x, y in ((1, 1), (S - 2, 1), (1, S - 2), (S - 2, S - 2)):
            d.point((x, y), fill=rgba(darken(base, 0.35)))
        return im
    if face == 'top':
        # 排気の格子（縁と平行な細い溝）
        g = darken(base, 0.45) if stage < 5 else (14, 14, 20)
        for y in (5, 7, 9):
            d.line([(5, y), (10, y)], fill=rgba(g))
        d.rectangle([4, 4, 11, 11], outline=rgba(trim))
    if stage == 1:
        edge_line(d, 2, rgba(glow, 200), gap=2)
        for x, y in ((1, 1), (S - 3, 1), (1, S - 3), (S - 3, S - 3)):
            d.rectangle([x, y, x + 1, y + 1], fill=rgba(trim))
            d.point((x, y), fill=rgba(lighten(trim, 0.35)))
    elif stage == 2:
        edge_line(d, 2, rgba(glow, 210), gap=1)
        edge_line(d, 3, rgba(lighten(glow, 0.5), 110), gap=3)
        for x, y in ((1, 1), (S - 2, 1), (1, S - 2), (S - 2, S - 2)):
            d.point((x, y), fill=(255, 255, 255, 255))
    elif stage == 3:
        edge_line(d, 2, rgba(glow), gap=1, dotted=True)
        for x, y in ((1, 1), (S - 2, 1), (1, S - 2), (S - 2, S - 2)):
            d.point((x, y), fill=rgba(trim))
    elif stage == 4:
        edge_line(d, 1, rgba(trim), gap=0)
        edge_line(d, 3, rgba(glow, 190), gap=2)
        for x, y in ((7, 1), (8, 1), (7, S - 2), (8, S - 2), (1, 7), (1, 8), (S - 2, 7), (S - 2, 8)):
            d.point((x, y), fill=rgba(darken(trim, 0.25)))
    elif stage == 5:
        rnd = random.Random(seed + 5)
        for _ in range(4):
            d.point((rnd.randrange(3, S - 3), rnd.randrange(3, S - 3)), fill=(255, 255, 255, rnd.choice([110, 170, 230])))
        edge_line(d, 2, rgba(glow, 220), gap=2)
        for cx, cy, sx, sy in ((1, 1, 1, 1), (S - 2, 1, -1, 1), (1, S - 2, 1, -1), (S - 2, S - 2, -1, -1)):
            for i in range(3):
                d.point((cx + sx * i, cy), fill=rgba(trim))
                d.point((cx, cy + sy * i), fill=rgba(trim))
    return im


def machine_frame(stage):
    """装置の骨組み（台座・柱・縁）: 段階色の金属に、縁に沿った面取りの明暗と擦れ。"""
    t = TIERS[stage]
    base = mix(t['trim'], (120, 126, 136), 0.35) if stage != 5 else (34, 34, 42)
    im = weathered(base, seed=stage * 57, strength=1.5)
    d = ImageDraw.Draw(im)
    d.line([(0, 0), (S - 1, 0)], fill=rgba(lighten(base, 0.3)))
    d.line([(0, 0), (0, S - 1)], fill=rgba(lighten(base, 0.2)))
    d.line([(0, S - 1), (S - 1, S - 1)], fill=rgba(darken(base, 0.35)))
    d.line([(S - 1, 0), (S - 1, S - 1)], fill=rgba(darken(base, 0.3)))
    for x, y in ((3, 3), (S - 4, 3), (3, S - 4), (S - 4, S - 4)):
        d.point((x, y), fill=rgba(darken(base, 0.45)))
        d.point((x + 1, y + 1), fill=rgba(lighten(base, 0.25)))
    if stage == 5:
        d.line([(0, 0), (S - 1, 0)], fill=rgba(t['trim']))
    return im


def glass():
    """装置の窓ガラス（半透明。うっすらした映り込みの斜線）。"""
    im, d = new(fill=(200, 230, 245, 54))
    for k in range(-S, S, 7):
        for i in range(S):
            x, y = k + i, S - 1 - i
            if 0 <= x < S:
                d.point((x, y), fill=(255, 255, 255, 96))
                if x + 1 < S:
                    d.point((x + 1, y), fill=(255, 255, 255, 60))
    d.rectangle([0, 0, S - 1, S - 1], outline=(255, 255, 255, 80))
    return im


def inner():
    """窓の内側の壁（暗い金属）。"""
    im = weathered((50, 56, 66), seed=88, strength=1.4)
    d = ImageDraw.Draw(im)
    for y in range(1, S, 4):
        d.line([(0, y), (S - 1, y)], fill=(40, 44, 52, 255))
    return im


def weather_existing(im, seed=0, strength=0.07):
    """できあがった絵に、わずかな濃淡のむら・縁の陰・擦れを足す（透明な所は触らない）。"""
    rnd = random.Random(seed)
    px = im.load()
    w, h = im.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            n = (rnd.random() - 0.5) * strength + 0.02 * math.sin(x * 0.8 + seed) * math.cos(y * 0.6)
            e = min(x, y, w - 1 - x, h - 1 - y)
            if e == 0:
                n -= 0.05
            c = lighten((r, g, b), n) if n > 0 else darken((r, g, b), -n)
            px[x, y] = rgba(c, a)
    for _ in range(3):
        x, y = rnd.randrange(1, w - 2), rnd.randrange(1, h - 1)
        r, g, b, a = px[x, y]
        if a > 0:
            px[x, y] = rgba(lighten((r, g, b), 0.08), a)
    return im


def accent(stage):
    """装置の出っ張り（煙突・アンテナなど）に使う、段階色の金属。"""
    t = TIERS[stage]
    im = weathered(darken(t['trim'], 0.05) if stage != 5 else (60, 60, 72), seed=stage * 101, strength=1.4)
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, S - 1, S - 1], outline=rgba(darken(t['trim'], 0.4)))
    edge_line(d, 2, rgba(t['glow'], 160), gap=3)
    return im


# ---------------------------------------------------------------- 8×8 の絵柄（# 色、+ 明るい、. 透明）

ICONS = {
    'flame': ["...#....", "..##....", "..###...", ".####+..", ".##++##.", "##+++##.", "##+++##.", ".######."],
    'press': ["########", "...##...", "...##...", "...##...", ".######.", "..####..", "........", "########"],
    'bubbles': ["....##..", "...#..#.", "#...##..", ".......#", "..##..#.", ".#..#...", "..##...#", "......#."],
    'screen': ["######..", "........", "####....", "........", "#####+..", "........", "###.....", "........"],
    'thermo': ["..##....", "..#+....", "..#+....", "..#+....", "..#+....", ".####...", ".####...", "..##...."],
    'arm': ["......##", ".....#+#", "....#...", "...#....", "..#.....", ".##.....", "####....", "####...."],
    'flask': ["..##....", "..##....", "..##....", ".#..#...", "#....#..", "#++++#..", "#++++#..", ".####..."],
    'fan': ["..#..#..", "...##...", "#..##..#", ".######.", ".######.", "#..##..#", "...##...", "..#..#.."],
    'dish': ["#......#", ".#....#.", "..####..", "...##...", "...##...", "...##...", "..####..", ".######."],
    'battery': ["..####..", ".######.", ".#....#.", ".#++++#.", ".#++++#.", ".#++++#.", ".#++++#.", ".######."],
    'anchor': ["...##...", "..####..", "...##...", "...##...", "#..##..#", "#..##..#", ".#.##.#.", "..####.."],
    'link': ["........", ".##..##.", "#..##..#", "#..++..#", "#..##..#", ".##..##.", "........", "........"],
    'beam': ["#......#", ".#....#.", "..#..#..", "...++...", "...++...", "..#..#..", ".#....#.", "#......#"],
    'atom': ["...##...", ".#.##.#.", "#..##..#", ".##++##.", ".##++##.", "#..##..#", ".#.##.#.", "...##..."],
    'wave': ["........", "........", "#..#....", ".##.#..#", "....#.#.", ".....#..", "........", "........"],
    'shield': [".######.", "#++++++#", "#+####+#", "#+#..#+#", ".#+##+#.", "..#++#..", "...##...", "........"],
    'echo': ["#.#..#.#", "#.#..#.#", "..#..#..", "...++...", "...++...", "..#..#..", "#.#..#.#", "#.#..#.#"],
    'crystal': ["...##...", "..#+##..", ".#++###.", ".#+####.", ".#+####.", ".#+####.", "..####..", "...##..."],
    'probe': ["...##...", "..#++#..", "########", "..#..#..", "..#..#..", ".#....#.", "#......#", "........"],
    'sphere': ["#.####.#", ".#....#.", "#.####.#", "#.####.#", "#.####.#", "#.####.#", ".#....#.", "#.####.#"],
    'halo': ["........", ".######.", "#......#", "#..##..#", "#..##..#", "#......#", ".######.", "........"],
    'tank': ["..####..", ".#....#.", ".######.", ".#++++#.", ".#++++#.", ".#++++#.", ".######.", "..####.."],
    'dome': ["........", "..####..", ".#....#.", "#......#", "#......#", "########", "...##...", "...##..."],
    'cylinder': ["..####..", "..#+.#..", "..#.+#..", "..#+.#..", "..#.+#..", "..#+.#..", "..#.+#..", "..####.."],
    'swirl': ["..####..", ".#....#.", "#..##..#", "#.#..#.#", "#.#.##.#", "#..#...#", ".#....#.", "..####.."],
    'port': ["..####..", ".#....#.", "#..#...#", "#..##..#", "#..###.#", "#..##..#", ".#.#..#.", "..####.."],
    'snow': ["#..#..#.", ".#.#.#..", "..###...", "#######.", "..###...", ".#.#.#..", "#..#..#.", "........"],
    'ring': ["########", "#......#", "#.####.#", "#.#..#.#", "#.#..#.#", "#.####.#", "#......#", "########"],
    'inward': ["...#....", "...#....", "#..#..#.", ".#.#.#..", "...+....", ".#.#.#..", "#..#..#.", "...#...."],
    'mirrors': ["########", "........", "..+..+..", ".+..+...", "..+..+..", "........", "########", "........"],
    'piston': ["########", ".######.", "...##...", "..####..", "..#++#..", "..####..", "...##...", "########"],
    'blackhole': ["........", "..####..", "+######+", "++#..#++", "++#..#++", "+######+", "..####..", "........"],
    'star': ["...#....", ".#.#.#..", "..###...", "###+###.", "..###...", ".#.#.#..", "...#....", "........"],
    'generic': ["........", ".######.", ".#....#.", ".#.++.#.", ".#.++.#.", ".#....#.", ".######.", "........"],
}

MACHINE_ICONS = {
    'kiln': ('flame', (255, 150, 80)), 'compressor': ('press', None), 'electrolyzer': ('bubbles', (120, 200, 255)),
    'archive_terminal': ('screen', (140, 240, 220)), 'thermoelectric_generator': ('thermo', (255, 120, 90)),
    'precision_assembler': ('arm', None), 'catalytic_reactor': ('flask', (150, 255, 180)),
    'cryogenic_turbine': ('fan', None), 'cosmic_muon_collector': ('dish', None), 'smes_cell': ('battery', None),
    'worldline_anchor_small': ('anchor', None), 'entanglement_synthesizer': ('link', None), 'laser_cooler': ('beam', (255, 90, 120)),
    'quantum_heat_engine': ('atom', None), 'gravitational_wave_detector': ('wave', None), 'inertial_stabilizer': ('shield', None),
    'echo_resonator': ('echo', (120, 255, 220)), 'time_crystal_incubator': ('crystal', None), 'probe_station': ('probe', None),
    'smes_module': ('battery', None), 'singularity_encapsulator': ('sphere', None), 'halo_collector': ('halo', (200, 160, 255)),
    'gravitational_containment_tank': ('tank', (200, 160, 255)), 'worldline_anchor_advanced': ('anchor', None),
    'shield_tower_core': ('dome', (180, 230, 255)), 'tipler_core': ('cylinder', None), 'wormhole_generator_core': ('swirl', (200, 160, 255)),
    'wormhole_stabilizer': ('swirl', None), 'wormhole_mouth': ('blackhole', (200, 160, 255)), 'wormhole_port': ('port', (200, 160, 255)),
    'cooling_tower_controller': ('snow', None), 'accelerator_controller': ('ring', None),
    'degenerate_compactor_controller': ('inward', None), 'casimir_cavity_controller': ('mirrors', None),
    'degenerate_furnace_controller': ('piston', (255, 180, 90)), 'core_controller': ('blackhole', (255, 200, 140)),
    'creative_energy_source': ('star', (255, 120, 255)),
}


def put_icon(d, kind, ox, oy, col, hi):
    for y, row in enumerate(ICONS.get(kind, ICONS['generic'])):
        for x, ch in enumerate(row):
            if ch == '#':
                d.point((ox + x, oy + y), fill=col)
            elif ch == '+':
                d.point((ox + x, oy + y), fill=hi)


def front_frame(iid, stage, on, frame=0, frames=1):
    """正面の1コマ。窓の中に絵柄。動く装置は明るさの波と走査線を重ねる。"""
    t = TIERS[stage]
    im = casing(stage, 'side', seed=hash(iid) % 97)
    d = ImageDraw.Draw(im)
    # 窓（縁は段階の金属、ガラスは暗い）
    d.rectangle([3, 3, 12, 12], fill=rgba(t['trim']))
    glass = (16, 22, 30) if not on else (22, 32, 44)
    d.rectangle([4, 4, 11, 11], fill=rgba(glass))
    kind, color = MACHINE_ICONS.get(iid, ('generic', None))
    c = color or t['glow']
    if on:
        pulse = 0.5 + 0.5 * math.sin(2 * math.pi * frame / max(1, frames)) if frames > 1 else 1.0
        col = rgba(mix(c, lighten(c, 0.35), pulse * 0.6))
        hi = rgba(lighten(c, 0.55 + 0.3 * pulse))
    else:
        col = rgba(mix(c, (30, 40, 50), 0.62))
        hi = rgba(mix(c, (30, 40, 50), 0.45))
    put_icon(d, kind, 4, 4, col, hi)
    if on and frames > 1:
        # 窓を上から下へ走る淡い走査線
        sy = 4 + frame * 8 // frames
        for x in range(4, 12):
            p = im.getpixel((x, sy))
            d.point((x, sy), fill=rgba(lighten(p, 0.22)))
    # 状態ランプ（窓の下、縁に沿った短い線）
    lamp = t['glow'] if on else mix(t['glow'], (40, 50, 60), 0.7)
    if on and frames > 1 and frame % 2 == 1:
        lamp = lighten(lamp, 0.4)
    d.line([(6, 13), (9, 13)], fill=rgba(lamp))
    return im


def machine_front(iid, stage, on):
    """正面のテクスチャと、動く場合のコマ数を返す。段階3以上の稼働中は8コマ。"""
    frames = 8 if on and stage >= 3 else 1
    sheet = Image.new('RGBA', (S, S * frames), (0, 0, 0, 0))
    for f in range(frames):
        sheet.paste(front_frame(iid, stage, on, f, frames), (0, S * f))
    return sheet, frames


def boost_overlay(frames=8):
    """恩恵の模様: 縁に沿って流れる光（動くテクスチャ）。"""
    sheet = Image.new('RGBA', (S, S * frames), (0, 0, 0, 0))
    path = [(x, 0) for x in range(S)] + [(S - 1, y) for y in range(1, S)] + \
           [(x, S - 1) for x in range(S - 2, -1, -1)] + [(0, y) for y in range(S - 2, 0, -1)]
    inner = [(x, 2) for x in range(2, S - 2)] + [(S - 3, y) for y in range(3, S - 2)] + \
            [(x, S - 3) for x in range(S - 4, 1, -1)] + [(2, y) for y in range(S - 4, 2, -1)]
    for f in range(frames):
        im = Image.new('RGBA', (S, S), (0, 0, 0, 0))
        d = ImageDraw.Draw(im)
        for p in path:
            d.point(p, fill=(150, 235, 255, 60))
        for lane, pts, speed in ((0, path, 1), (1, inner, -1)):
            n = len(pts)
            for k in range(2):
                head = (speed * f * n // frames + k * n // 2) % n
                for tail in range(6):
                    x, y = pts[(head - speed * tail) % n]
                    a = int(255 * (1 - tail / 6))
                    d.point((x, y), fill=(255, 255, 255, a) if tail < 2 else (140, 230, 255, a))
        sheet.paste(im, (0, S * f))
    return sheet


# ---------------------------------------------------------------- 部品・ブロック

def part_texture(iid):
    if iid == 'cooling_tower_casing':
        return casing(2, 'side', seed=3)
    if iid == 'cooling_tower_glass':
        im, d = new()
        d.rectangle([0, 0, S - 1, S - 1], outline=(214, 224, 232, 255))
        d.rectangle([1, 1, S - 2, S - 2], outline=(150, 182, 204, 255))
        d.rectangle([2, 2, S - 3, S - 3], fill=(190, 230, 250, 50))
        for i in range(3, 8):
            d.point((i, i - 1), fill=(255, 255, 255, 140))
        return im
    if iid == 'heat_exchange_core':
        im = weathered((128, 82, 54), seed=11)
        d = ImageDraw.Draw(im)
        for x in range(1, S - 1, 2):
            d.line([(x, 1), (x, S - 2)], fill=(214, 144, 92, 255))
        d.line([(0, 7), (S - 1, 7)], fill=(150, 230, 255, 255))
        d.line([(0, 8), (S - 1, 8)], fill=(110, 200, 240, 255))
        return im
    if iid == 'cooling_tower_port':
        im = casing(2, 'side', seed=4)
        d = ImageDraw.Draw(im)
        d.ellipse([4, 4, 11, 11], fill=(40, 52, 64, 255), outline=(130, 150, 172, 255))
        d.point((7, 7), fill=(150, 232, 255, 255))
        d.point((8, 8), fill=(150, 232, 255, 255))
        return im
    if iid == 'accelerator_tube':
        im = weathered((206, 212, 220), seed=12)
        d = ImageDraw.Draw(im)
        d.rectangle([0, 5, S - 1, 10], fill=(160, 205, 230, 255))
        d.line([(0, 7), (S - 1, 7)], fill=(255, 255, 255, 255))
        d.line([(0, 8), (S - 1, 8)], fill=(150, 232, 255, 255))
        for x in (1, S - 2):
            d.line([(x, 0), (x, S - 1)], fill=(156, 166, 178, 255))
        return im
    if iid == 'focusing_magnet':
        im = weathered((200, 206, 214), seed=13)
        d = ImageDraw.Draw(im)
        d.rectangle([2, 2, 6, S - 3], fill=(206, 72, 72, 255))
        d.rectangle([9, 2, S - 3, S - 3], fill=(72, 110, 206, 255))
        d.line([(7, 1), (8, S - 2)], fill=(255, 255, 255, 255))
        return im
    if iid == 'degenerate_casing':
        im = weathered((80, 82, 94), seed=14, strength=1.3)
        d = ImageDraw.Draw(im)
        d.rectangle([0, 0, S - 1, S - 1], outline=(40, 42, 50, 255))
        edge_line(d, 1, (236, 238, 240, 200), gap=1)
        for x, y in ((3, 3), (S - 4, 3), (3, S - 4), (S - 4, S - 4)):
            d.point((x, y), fill=(255, 190, 92, 255))
        return im
    if iid == 'mirror_plate':
        im, d = new()
        for y in range(S):
            for x in range(S):
                t = ((x + y) % 16) / 16
                d.point((x, y), fill=rgba(mix((200, 214, 230), (252, 253, 255), abs(0.5 - t) * 2)))
        d.rectangle([0, 0, S - 1, S - 1], outline=(140, 150, 164, 255))
        d.line([(3, 11), (11, 3)], fill=(255, 255, 255, 255))
        return im
    if iid == 'degenerate_furnace_piston':
        im = weathered((76, 78, 90), seed=15)
        d = ImageDraw.Draw(im)
        d.rectangle([3, 3, 12, 12], fill=(150, 156, 164, 255), outline=(220, 224, 230, 255))
        d.rectangle([6, 6, 9, 9], fill=(255, 170, 80, 255))
        return im
    if iid == 'reactor_shell':
        im = weathered((26, 26, 34), seed=16, strength=1.5)
        d = ImageDraw.Draw(im)
        for y in (0, 5, 10, 15):
            for x in range(0 if y % 10 == 0 else 2, S, 5):
                d.line([(x, y), (x + 2, y)], fill=(66, 68, 86, 255))
        edge_line(d, 0, (236, 238, 240, 255), gap=0)
        return im
    if iid == 'gyro_drive':
        im = weathered((32, 32, 42), seed=17)
        d = ImageDraw.Draw(im)
        d.rectangle([0, 0, S - 1, S - 1], outline=(236, 238, 240, 255))
        d.ellipse([2, 2, 13, 13], outline=(220, 188, 108, 255))
        d.ellipse([2, 5, 13, 10], outline=(255, 255, 255, 255))
        d.ellipse([5, 2, 10, 13], outline=(150, 232, 255, 255))
        d.point((7, 7), fill=(255, 255, 255, 255))
        d.point((8, 8), fill=(255, 255, 255, 255))
        return im
    if iid == 'extraction_port':
        im = weathered((32, 32, 42), seed=18)
        d = ImageDraw.Draw(im)
        d.rectangle([0, 0, S - 1, S - 1], outline=(236, 238, 240, 255))
        d.rectangle([4, 4, 11, 11], fill=(12, 12, 18, 255), outline=(220, 188, 108, 255))
        for p in ((2, 2), (S - 3, 2), (2, S - 3), (S - 3, S - 3)):
            d.point(p, fill=(255, 255, 255, 255))
        return im
    if iid == 'strangelet':
        rnd = random.Random(5)
        im, d = new()
        d.rectangle([0, 0, S - 1, S - 1], fill=(32, 10, 42, 255))
        for _ in range(50):
            d.point((rnd.randrange(S), rnd.randrange(S)), fill=rgba(rnd.choice([(200, 120, 255), (120, 60, 180), (255, 200, 255)])))
        d.ellipse([5, 5, 10, 10], fill=(255, 230, 255, 255))
        return im
    return casing(1, 'side')


def strange_matter():
    im = weathered((54, 32, 72), seed=19, strength=1.6)
    d = ImageDraw.Draw(im)
    rnd = random.Random(9)
    for _ in range(9):
        x, y = rnd.randrange(1, S - 1), rnd.randrange(1, S - 1)
        d.point((x, y), fill=(140, 90, 190, 255))
        d.point((x, y - 1), fill=(220, 180, 255, 255))
    return im


def compressed_block(level, metal):
    base = (128, 132, 140) if not metal else (158, 146, 122)
    b = tuple(int(c * (1 - 0.2 * (level - 1))) for c in base)
    im = weathered(b, seed=20 + level + (10 if metal else 0), strength=1.4)
    d = ImageDraw.Draw(im)
    step = {1: 8, 2: 4, 3: 2}.get(level, 8)
    for p in range(0, S, step):
        d.line([(p, 0), (p, S - 1)], fill=rgba(darken(b, 0.22)))
        d.line([(0, p), (S - 1, p)], fill=rgba(darken(b, 0.22)))
    if level >= 2:
        for p in range(step, S, step):
            for q in range(step, S, step):
                d.point((p, q), fill=(200, 230, 255, 255))
    d.rectangle([0, 0, S - 1, S - 1], outline=rgba((236, 238, 240) if level < 3 else (150, 232, 255)))
    return im


# ---------------------------------------------------------------- アイテム（16×16）

OUT = (24, 28, 36, 255)


def outlined(fn):
    """形を描き、外側に1ドットの輪郭を付ける。輪郭は真っ黒にせず、内側の色をぐっと暗くした色にする。"""
    im, d = new()
    fn(d)
    px = im.load()
    out = im.copy()
    po = out.load()
    edge = set()
    for y in range(S):
        for x in range(S):
            if px[x, y][3] == 0:
                cols = []
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < S and 0 <= ny < S and px[nx, ny][3] > 128:
                        cols.append(px[nx, ny][:3])
                if cols:
                    avg = tuple(sum(c[i] for c in cols) // len(cols) for i in range(3))
                    po[x, y] = rgba(mix(darken(avg, 0.62), (40, 44, 60), 0.25))
                    edge.add((x, y))
    out.info['outline'] = edge
    return out


def grain(im, seed, strength=0.09):
    """アイテムの面にも使用感を入れる: 左上から光が当たる濃淡、輪郭の内側の陰、細かなむら、小さな擦れ
    （汚れではない）。透明な所と輪郭線は触らない。"""
    rnd = random.Random(seed)
    px = im.load()
    src = im.copy().load()
    edge = im.info.get('outline', set())

    def solid(x, y):
        return 0 <= x < S and 0 <= y < S and src[x, y][3] > 200 and (x, y) not in edge

    for y in range(S):
        for x in range(S):
            if not solid(x, y):
                continue
            r, g, b, a = src[x, y]
            n = (rnd.random() - 0.5) * strength
            n += (7.5 - (x + y) / 2) * 0.008                      # 左上が明るく、右下が暗い
            if not solid(x + 1, y) or not solid(x, y + 1):          # 右下の輪郭の内側は少し暗い
                n -= 0.08
            if not solid(x - 1, y) or not solid(x, y - 1):          # 左上の輪郭の内側は少し明るい
                n += 0.06
            c = lighten((r, g, b), n) if n > 0 else darken((r, g, b), -n)
            px[x, y] = rgba(c, a)
    for _ in range(2):
        x, y = rnd.randrange(2, S - 2), rnd.randrange(2, S - 2)
        if solid(x, y):
            r, g, b, a = px[x, y]
            px[x, y] = rgba(lighten((r, g, b), 0.12), a)
    return im


def ingot(c):
    return outlined(lambda d: (d.polygon([(2, 9), (6, 5), (13, 5), (9, 9)], fill=rgba(lighten(c, 0.3))),
                               d.rectangle([2, 9, 9, 12], fill=rgba(c)),
                               d.polygon([(9, 9), (13, 5), (13, 8), (9, 12)], fill=rgba(darken(c, 0.25))),
                               d.line([(3, 10), (8, 10)], fill=rgba(lighten(c, 0.45)))))


def plate(c, stripes=0, accent=(110, 205, 238)):
    def f(d):
        d.polygon([(1, 7), (9, 3), (14, 6), (6, 10)], fill=rgba(lighten(c, 0.2)))
        d.polygon([(1, 7), (6, 10), (6, 12), (1, 9)], fill=rgba(darken(c, 0.22)))
        d.polygon([(6, 10), (14, 6), (14, 8), (6, 12)], fill=rgba(darken(c, 0.1)))
        for k in range(stripes):
            d.line([(4 + k * 2, 7), (8 + k * 2, 5)], fill=rgba(accent))
    return outlined(f)


def circuit(board, chip, trace):
    def f(d):
        d.rectangle([2, 3, 13, 12], fill=rgba(board))
        for y in (5, 7, 9, 11):
            d.point((3, y), fill=rgba(trace))
            d.point((12, y), fill=rgba(trace))
        d.rectangle([5, 5, 10, 10], fill=rgba(chip))
        d.rectangle([6, 6, 9, 9], outline=rgba(lighten(chip, 0.35)))
        d.point((7, 7), fill=rgba(trace))
    return outlined(f)


def coil(wire, core):
    def f(d):
        d.rectangle([4, 3, 11, 12], fill=rgba(core))
        for y in range(3, 13, 2):
            d.line([(3, y), (12, y)], fill=rgba(wire))
        d.rectangle([3, 2, 12, 2], fill=rgba(darken(core, 0.25)))
        d.rectangle([3, 13, 12, 13], fill=rgba(darken(core, 0.25)))
    return outlined(f)


def spool(wire):
    return outlined(lambda d: (d.ellipse([2, 2, 13, 13], fill=rgba(wire)),
                               d.ellipse([4, 4, 11, 11], outline=rgba(lighten(wire, 0.35))),
                               d.ellipse([6, 6, 9, 9], fill=(40, 44, 52, 255))))


def frame(c, trim):
    def f(d):
        d.rectangle([2, 2, 13, 13], fill=rgba(c))
        d.rectangle([5, 5, 10, 10], fill=(0, 0, 0, 0))
        d.line([(2, 2), (5, 5)], fill=rgba(trim))
        d.line([(13, 2), (10, 5)], fill=rgba(trim))
        d.line([(2, 13), (5, 10)], fill=rgba(trim))
        d.line([(13, 13), (10, 10)], fill=rgba(trim))
    return outlined(f)


def module(body, lamp):
    def f(d):
        d.rectangle([2, 4, 13, 12], fill=rgba(body))
        d.line([(2, 4), (13, 4)], fill=rgba(lighten(body, 0.3)))
        d.rectangle([4, 6, 11, 8], fill=(30, 38, 48, 255))
        for x in (5, 7, 9):
            d.point((x, 7), fill=rgba(lamp))
        d.line([(4, 10), (11, 10)], fill=rgba(lamp))
    return outlined(f)


def card(stripe, blank=False):
    def f(d):
        d.rectangle([2, 4, 13, 11], fill=(236, 238, 240, 255))
        d.rectangle([2, 4, 13, 5], fill=rgba(stripe))
        if not blank:
            d.rectangle([3, 7, 5, 9], fill=(214, 180, 90, 255))
            d.line([(7, 7), (12, 7)], fill=rgba(darken(stripe, 0.2)))
            d.line([(7, 9), (11, 9)], fill=rgba(darken(stripe, 0.2)))
    return outlined(f)


def crystal_cluster(c, seed=0, spent=False, mark=None):
    """固まった結晶の塊: 傾いた六角柱を3本寄せ、光の当たる面・陰の面・稜線で立体感を出す。"""
    if spent:
        c = mix(c, (118, 116, 120), 0.75)

    def prism(d, x, top, bottom, w, lean):
        # 柱の左面（明るい）・右面（暗い）・先端
        mid = x + w // 2
        d.polygon([(x, bottom), (x + lean, top + 2), (mid + lean, top), (mid, bottom)], fill=rgba(lighten(c, 0.28)))
        d.polygon([(mid, bottom), (mid + lean, top), (x + w + lean, top + 2), (x + w, bottom)], fill=rgba(darken(c, 0.18)))
        d.line([(mid, bottom), (mid + lean, top)], fill=rgba(lighten(c, 0.55)))
        d.point((mid + lean, top), fill=(255, 255, 255, 255) if not spent else rgba(lighten(c, 0.3)))

    def f(d):
        prism(d, 3, 6, 12, 3, -1)
        prism(d, 9, 5, 12, 3, 1)
        prism(d, 5, 2, 13, 5, 0)
        d.line([(3, 13), (12, 13)], fill=rgba(darken(c, 0.35)))
        if mark:
            d.point((7, 8), fill=rgba(mark))
            d.point((8, 9), fill=rgba(mark))
        if spent:
            d.line([(4, 9), (7, 11), (9, 8), (11, 10)], fill=(56, 54, 58, 255))
    return outlined(f)


def vial(liquid, glow=False):
    def f(d):
        d.rectangle([6, 2, 9, 3], fill=(150, 156, 164, 255))
        d.polygon([(6, 4), (9, 4), (12, 13), (3, 13)], fill=(220, 236, 246, 120))
        d.polygon([(5, 8), (10, 8), (12, 13), (3, 13)], fill=rgba(liquid))
        if glow:
            d.point((6, 10), fill=(255, 255, 255, 255))
            d.point((9, 11), fill=(255, 255, 255, 255))
    return outlined(f)


def cloud(dot, seed=3):
    def f(d):
        rnd = random.Random(seed)
        d.ellipse([2, 3, 13, 12], fill=rgba(dot, 70))
        for _ in range(12):
            a, r = rnd.random() * 6.28, rnd.random() * 5
            d.point((8 + r * math.cos(a), 8 + r * math.sin(a) * 0.8), fill=rgba(lighten(dot, rnd.random() * 0.5)))
    return outlined(f)


def blob(c, swirl):
    def f(d):
        d.polygon([(8, 2), (13, 4), (13, 10), (10, 13), (4, 12), (3, 8), (4, 3)], fill=rgba(c))
        for k in range(12):
            a = k * 0.55
            r = 0.5 + k * 0.4
            d.point((8 + r * math.cos(a), 8 + r * math.sin(a)), fill=rgba(swirl))
    return outlined(f)


def torus(c, accent_c):
    return outlined(lambda d: (d.ellipse([1, 4, 14, 11], fill=rgba(c)), d.ellipse([5, 6, 10, 9], fill=(0, 0, 0, 0)),
                               d.arc([1, 4, 14, 11], 190, 350, fill=rgba(accent_c))))


def book(cover, band):
    return outlined(lambda d: (d.rectangle([3, 2, 12, 13], fill=rgba(cover)), d.rectangle([3, 2, 4, 13], fill=rgba(darken(cover, 0.3))),
                               d.rectangle([5, 11, 12, 12], fill=(236, 238, 240, 255)), d.rectangle([6, 4, 11, 5], fill=rgba(band)),
                               d.rectangle([7, 7, 10, 9], outline=rgba(band))))


def device(body, screen, beam=None):
    def f(d):
        d.rectangle([4, 5, 11, 13], fill=rgba(body))
        d.rectangle([5, 6, 10, 9], fill=rgba(screen))
        d.point((6, 11), fill=rgba(darken(body, 0.3)))
        d.point((9, 11), fill=rgba(darken(body, 0.3)))
        if beam:
            d.polygon([(6, 5), (9, 5), (12, 2), (3, 2)], fill=rgba(beam, 110))
    return outlined(f)


def glove(body, orb=None, accent_c=(110, 205, 238)):
    """手袋。指4本と親指、手首の帯。orb があれば手のひらの上に浮かぶ宝珠（端に触れない）。"""
    def f(d):
        top = 7 if orb else 5
        d.rectangle([5, top + 3, 10, 13], fill=rgba(body))
        for i, x in enumerate((5, 7, 9)):
            d.rectangle([x, top + (0 if i == 1 else 1), x + 1, top + 3], fill=rgba(body))
        d.rectangle([11, top + 4, 11, top + 6], fill=rgba(body))
        d.line([(5, 12), (10, 12)], fill=rgba(accent_c))
        d.line([(5, top + 3), (5, 11)], fill=rgba(lighten(body, 0.2)))
        if orb:
            d.ellipse([6, 2, 9, 5], fill=rgba(orb))
            d.point((7, 3), fill=rgba(accent_c))
    return outlined(f)


def scroll(paper, ink, torn=False):
    def f(d):
        if torn:
            d.polygon([(2, 3), (12, 2), (13, 6), (12, 13), (3, 13), (2, 8)], fill=rgba(paper))
        else:
            d.rectangle([3, 3, 12, 12], fill=rgba(paper))
            d.rectangle([2, 2, 13, 3], fill=rgba(darken(paper, 0.3)))
            d.rectangle([2, 12, 13, 13], fill=rgba(darken(paper, 0.3)))
        for y in range(5, 11, 2):
            d.line([(5, y), (10 - (y % 3), y)], fill=rgba(ink))
    return outlined(f)


def tablet(glow):
    return outlined(lambda d: (d.rectangle([3, 2, 12, 13], fill=(40, 46, 56, 255)), d.rectangle([4, 3, 11, 12], fill=rgba(darken(glow, 0.6))),
                               [d.line([(5, y), (10 - (y % 3), y)], fill=rgba(glow)) for y in range(4, 12, 2)],
                               d.rectangle([3, 2, 12, 13], outline=(236, 238, 240, 255))))


def pellet(c):
    return outlined(lambda d: (d.ellipse([4, 4, 11, 11], fill=rgba(c)), d.ellipse([5, 5, 7, 7], fill=rgba(lighten(c, 0.35)))))


def tracks(c):
    def f(d):
        for k, a in enumerate((0.3, 1.5, 2.7, 3.9, 5.1)):
            d.line([(8, 8), (8 + 6 * math.cos(a), 8 + 6 * math.sin(a))], fill=rgba(lighten(c, k * 0.1)))
        d.point((8, 8), fill=(255, 255, 255, 255))
    return outlined(f)


def magnet():
    return outlined(lambda d: (d.rectangle([4, 2, 11, 13], fill=(208, 70, 70, 255)), d.rectangle([6, 4, 9, 11], fill=(240, 120, 120, 255)),
                               d.rectangle([7, 5, 8, 10], fill=(255, 255, 255, 255))))


def bottle():
    return outlined(lambda d: (d.rectangle([6, 2, 9, 3], fill=(150, 156, 164, 255)), d.ellipse([3, 4, 12, 13], fill=(220, 236, 246, 120)),
                               d.line([(3, 7), (12, 7)], fill=(190, 118, 66, 255)), d.line([(3, 11), (12, 11)], fill=(190, 118, 66, 255)),
                               d.rectangle([7, 8, 8, 9], fill=(200, 120, 255, 255))))


def unit(body, lamp, broken=False):
    def f(d):
        d.rectangle([2, 3, 13, 12], fill=rgba(body))
        d.rectangle([4, 5, 11, 8], fill=(30, 38, 48, 255))
        d.rectangle([5, 6, 10, 7], fill=rgba(lamp))
        for x in (4, 7, 10):
            d.point((x, 10), fill=rgba(darken(body, 0.3)))
        if broken:
            d.line([(3, 4), (7, 8), (6, 11)], fill=(40, 40, 46, 255))
    return outlined(f)


def seed_item(c, ring=None):
    def f(d):
        d.polygon([(8, 2), (11, 6), (10, 12), (6, 12), (5, 6)], fill=rgba(c))
        d.line([(8, 3), (8, 11)], fill=rgba(lighten(c, 0.3)))
        if ring:
            d.ellipse([2, 6, 13, 9], outline=rgba(ring))
    return outlined(f)


def belt(body, core):
    return outlined(lambda d: (d.rectangle([1, 6, 14, 9], fill=rgba(body)), d.ellipse([5, 4, 10, 11], fill=rgba(darken(body, 0.2))),
                               d.ellipse([6, 6, 9, 9], fill=rgba(core))))


def star(core, rays):
    def f(d):
        for k in range(8):
            a = k * math.pi / 4
            r = 5 if k % 2 == 0 else 4
            d.line([(8, 8), (8 + r * math.cos(a), 8 + r * math.sin(a))], fill=rgba(rays))
        d.ellipse([5, 5, 10, 10], fill=rgba(core))
        d.point((7, 6), fill=(255, 255, 255, 255))
    return outlined(f)


def shell_sphere():
    return outlined(lambda d: (d.ellipse([3, 3, 12, 12], fill=(16, 16, 22, 255)), d.arc([3, 3, 12, 12], 200, 340, fill=(236, 238, 240, 255)),
                               d.line([(8, 3), (8, 12)], fill=(80, 80, 96, 255))))


def compass_frame(angle):
    """探索コンパスの1コマ（angle は 0〜1、北が上）。"""
    im, d = new()
    d.ellipse([1, 1, 14, 14], fill=(236, 238, 240, 255), outline=(190, 118, 66, 255))
    d.ellipse([3, 3, 12, 12], fill=(30, 40, 52, 255))
    for k in range(4):
        a = k * math.pi / 2
        d.point((7.5 + 4.5 * math.cos(a), 7.5 + 4.5 * math.sin(a)), fill=(110, 205, 238, 255))
    a = angle * 2 * math.pi - math.pi / 2
    for r in range(0, 4):
        d.point((round(7.5 + r * math.cos(a)), round(7.5 + r * math.sin(a))), fill=(255, 120, 90, 255))
        d.point((round(7.5 - r * math.cos(a) * 0.7), round(7.5 - r * math.sin(a) * 0.7)), fill=(220, 224, 230, 255))
    d.point((7, 7), fill=(255, 255, 255, 255))
    return im


T1, T2, T3, T4, T5 = (TIERS[i]['glow'] for i in range(1, 6))
COPPER = (206, 126, 78)
STEEL = (172, 178, 188)

ITEM_ART = {
    'steel_blend': lambda: pellet((92, 92, 104)),
    'steel_ingot': lambda: ingot(STEEL),
    'unfired_ceramic': lambda: plate((190, 178, 164)),
    'white_ceramic_composite': lambda: plate((236, 238, 240)),
    'basic_circuit': lambda: circuit((60, 120, 90), (40, 44, 52), COPPER),
    'handbook': lambda: book((40, 90, 130), T1),
    'holo_projector': lambda: device((220, 224, 230), (40, 70, 90), T1),
    'explorer_compass': lambda: compass_frame(0.0),
    'thermocouple_module': lambda: module((220, 224, 230), (255, 130, 90)),
    'basic_frame': lambda: frame(STEEL, (236, 238, 240)),
    'steel_plate': lambda: plate(STEEL),
    'ceramic_substrate': lambda: plate((236, 238, 240), 3, COPPER),
    'blank_data_card': lambda: card((200, 206, 214), blank=True),
    'data_card_observation_log': lambda: card(T1),
    'data_card_quantum_fragment': lambda: card(T3),
    'data_card_culture_data': lambda: card(T4),
    'mass_pellet': lambda: pellet((82, 86, 98)),
    'superconducting_coil': lambda: coil(COPPER, (200, 230, 250)),
    'superconducting_wire': lambda: spool((150, 210, 240)),
    'muon_bundle': lambda: tracks(T2),
    'muon_catalyst': lambda: crystal_cluster((96, 140, 236), mark=(200, 230, 255)),
    'bose_condensate_catalyst': lambda: crystal_cluster((110, 220, 236), mark=(255, 255, 255)),
    'time_crystal_catalyst': lambda: crystal_cluster((250, 182, 84), mark=(255, 250, 220)),
    'spent_muon_catalyst': lambda: crystal_cluster((96, 140, 236), spent=True),
    'spent_bose_condensate_catalyst': lambda: crystal_cluster((110, 220, 236), spent=True),
    'spent_time_crystal_catalyst': lambda: crystal_cluster((250, 182, 84), spent=True),
    'monopole_upgrade': lambda: circuit((60, 50, 90), (200, 70, 70), (190, 160, 255)),
    'quantum_computing_module': lambda: circuit((70, 56, 110), (30, 24, 50), T3),
    'entangled_element': lambda: outlined(lambda d: (d.ellipse([1, 4, 7, 10], fill=rgba(T3)), d.ellipse([8, 5, 14, 11], fill=rgba(lighten(T3, 0.2))),
                                                    d.line([(7, 7), (8, 8)], fill=(255, 255, 255, 255)))),
    'cold_atoms': lambda: cloud((150, 220, 255)),
    'optical_lattice_substrate': lambda: plate((210, 200, 240), 4, T3),
    'neutrino_scanner': lambda: device((60, 54, 90), (180, 140, 255)),
    'inertial_control_gauntlet': lambda: glove((220, 224, 230)),
    'degenerate_matter_shell': shell_sphere,
    'exotic_matter': lambda: blob((60, 20, 80), (220, 160, 255)),
    'toroidal_magnetic_coil': lambda: torus(COPPER, T2),
    'magnetic_bottle': bottle,
    'gravity_field_stabilizer': lambda: module((40, 40, 50), (255, 255, 255)),
    'hawking_collector': lambda: module((30, 30, 40), (255, 140, 80)),
    'jet_collector': lambda: module((30, 30, 40), (120, 220, 255)),
    'ergosphere_ring': lambda: torus((30, 30, 40), (220, 188, 108)),
    'jet_condensate': lambda: vial((120, 220, 255), glow=True),
    'artificial_star_core': lambda: star((255, 220, 120), (255, 180, 80)),
    'hawking_condensate': lambda: vial((255, 140, 80), glow=True),
    'metric_drive': lambda: belt((40, 40, 50), (200, 160, 255)),
    'graviton_manipulator': lambda: glove((44, 44, 54), orb=(10, 10, 14), accent_c=(220, 188, 108)),
    'observation_log': lambda: scroll((230, 222, 200), (90, 110, 130)),
    'degraded_control_unit': lambda: unit((180, 170, 150), (90, 60, 50), broken=True),
    'control_unit': lambda: unit((236, 238, 240), T1),
    'quantum_data_fragment': lambda: scroll((220, 214, 240), T3, torn=True),
    'degraded_cold_atom_trap': lambda: unit((170, 176, 186), (60, 70, 90), broken=True),
    'cold_atom_trap': lambda: unit((210, 230, 246), (150, 220, 255)),
    'degraded_time_crystal_seed': lambda: seed_item((150, 120, 90)),
    'time_crystal_seed': lambda: seed_item((255, 190, 90)),
    'culture_data': lambda: scroll((236, 226, 200), T4),
    'degraded_anomaly_sample': lambda: vial((90, 80, 100)),
    'anomaly_sample': lambda: vial((60, 20, 80), glow=True),
    'magnetic_monopole': magnet,
    'record_fragment': lambda: scroll((200, 196, 186), (70, 80, 90), torn=True),
    'decoded_record': lambda: tablet(T1),
    'creative_catalyst': lambda: crystal_cluster((255, 120, 255), mark=(255, 255, 255)),
    'builder_wand': lambda: outlined(lambda d: (d.line([(3, 12), (10, 5)], fill=(150, 110, 70, 255), width=2),
                                               d.ellipse([10, 2, 13, 5], fill=(255, 120, 255, 255)))),
}


def item_texture(iid, stage):
    fn = ITEM_ART.get(iid)
    im = fn() if fn is not None else module((220, 224, 230), TIERS[stage]['glow'])
    return grain(im, seed=hash(iid) % 1000)


# ---------------------------------------------------------------- ケーブル

# 断面（ケーブルの軸に直交する面の長方形の集まり、0〜16 の座標）。上位ほど凹凸のある断面になる
# 断面は中心に置いた正方形の一辺（偶数）。Java の CableProfile と同じ値
CABLE_WIDTHS = {'copper_wire': 2, 'superconducting_cable': 4, 'topological_wire': 6, 'horizon_bus': 8}


def cable_bands(iid, core, on, glow):
    """ケーブルの断面の帯（中心から外へ: 芯 → 段階ごとの帯 → 被覆の縁）。幅は CABLE_WIDTHS の半分の数。"""
    jacket = (226, 230, 234)
    c = glow if on and glow else core
    half = CABLE_WIDTHS[iid] // 2
    if iid == 'copper_wire':
        return [c]                                                   # 銅むき出しの1本
    if iid == 'superconducting_cable':
        return [c, darken(jacket, 0.15)]                              # 芯と白い被覆
    if iid == 'topological_wire':
        return [c, lighten(c, 0.45) if on else mix(c, jacket, 0.5), darken(jacket, 0.18)]   # 芯・リブ・被覆
    return [c, (40, 40, 50), lighten(jacket, 0.05)][:half - 1] + [(220, 188, 108)]          # 芯・黒い層・白・金の縁


def cable_texture(iid, core, on=False, glow=None):
    """ケーブルの側面: 軸に沿った帯。行8を境に上下対称で、幅 w のケーブルは行 8-w/2 〜 8+w/2-1 を1ドットずつそのまま使う
    （引き伸ばさない）。"""
    bands = cable_bands(iid, core, on, glow)
    im, d = new()
    for k, col in enumerate(bands):
        for y in (7 - k, 8 + k):
            for x in range(S):
                n = ((x * 7 + y * 3) % 5 - 2) * 0.012
                d.point((x, y), fill=rgba(lighten(col, n) if n > 0 else darken(col, -n)))
    if on:
        for x in range(1, S, 4):
            d.point((x, 7), fill=(255, 255, 255, 255))
            d.point((x, 8), fill=(255, 255, 255, 255))
    return im


def cable_end(iid, core, on=False, glow=None):
    """ケーブルの切り口: 中心から同じ帯が四角く広がる（側面の帯と同じ色の並び）。"""
    bands = cable_bands(iid, core, on, glow)
    im, d = new()
    for k in range(len(bands) - 1, -1, -1):
        d.rectangle([7 - k, 7 - k, 8 + k, 8 + k], fill=rgba(bands[k]))
    return im
