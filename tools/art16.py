"""Singulo の標準テクスチャ（16×16）。

- 表面: のっぺりさせず、わずかな濃淡のむらと縁の陰、細かな擦れ（汚れではない使用感）を入れる。
- 線: 段階色の発光ラインは中央を横切らず、外装の縁に沿って内側を一周する。
- 段階: 1 銅の角金具、2 霜の角と二重の縁、3 菫色の点線の縁、4 金の縁と歯車の刻み、5 黒地に金の角金具と白い光の縁。
- 正面: 装置ごとの 8×8 の絵柄。段階3以上は稼働中に動く（縦に並べたコマ）。
"""
import math
import random
import zlib

from PIL import Image, ImageDraw


def stable_hash(text):
    """実行ごとに変わらないハッシュ（Python の hash() は起動ごとに変わり、絵が毎回変わってしまう）。"""
    return zlib.crc32(text.encode("utf-8"))


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
    """装置の窓ガラス。切り抜きで描く（半透明だと、描く順番によって奥の壁が消え、向こうの景色が透けて見えるため）。
    ガラス自体は透明で、映り込みの短い斜線だけを点で残す。"""
    im, d = new()
    for k in (-6, 3):
        for i in range(4):
            x, y = 9 + k + i, 3 + 3 - i + (0 if k < 0 else 5)
            if 0 <= x < S and 0 <= y < S:
                d.point((x, y), fill=(236, 246, 252, 255))
    d.point((12, 4), fill=(236, 246, 252, 255))
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
    'neutrino_observatory': ('dish', (190, 150, 255)),
    'deep_sea_collector': ('bubbles', (90, 150, 255)), 'void_collector': ('halo', (170, 120, 240)),
    'keraunos_tower': ('star', (150, 220, 255)),
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
    im = casing(stage, 'side', seed=stable_hash(iid) % 97)
    d = ImageDraw.Draw(im)
    # 窓（縁は段階の金属、ガラスは暗い）
    d.rectangle([3, 3, 12, 12], fill=rgba(t['trim']))
    glass = (16, 22, 30) if not on else (22, 32, 44)
    d.rectangle([4, 4, 11, 11], fill=rgba(glass))
    kind, color = MACHINE_ICONS.get(iid, ('generic', None))
    c = color or t['glow']
    if on:
        # 明るさの波は控えめに（強弱の差を小さく）
        pulse = 0.5 + 0.5 * math.sin(2 * math.pi * frame / max(1, frames)) if frames > 1 else 1.0
        col = rgba(mix(c, lighten(c, 0.35), 0.35 + pulse * 0.2))
        hi = rgba(lighten(c, 0.62 + 0.1 * pulse))
    else:
        col = rgba(mix(c, (30, 40, 50), 0.62))
        hi = rgba(mix(c, (30, 40, 50), 0.45))
    put_icon(d, kind, 4, 4, col, hi)
    if on and frames > 1:
        # 窓を上から下へ走る淡い走査線
        sy = 4 + frame * 8 // frames
        for x in range(4, 12):
            p = im.getpixel((x, sy))
            d.point((x, sy), fill=rgba(lighten(p, 0.08)))
    # 状態ランプ（窓の下、縁に沿った短い線）
    lamp = t['glow'] if on else mix(t['glow'], (40, 50, 60), 0.7)
    if on and frames > 1:
        lamp = lighten(lamp, 0.1 + 0.1 * math.sin(2 * math.pi * frame / frames))
    d.line([(6, 13), (9, 13)], fill=rgba(lamp))
    return im


# ---------------------------------------------------------------- クリエイティブ電源（無限の電力。星空と虹色の縁）

CREATIVE_FRAMES = 8


def creative_casing(frame=0, frames=CREATIVE_FRAMES):
    """クリエイティブ電源の外装: 深い宇宙の黒紫に、またたく星。縁は虹色にゆっくり移ろう。"""
    rnd = random.Random(77)
    im, d = new(fill=(18, 10, 32, 255))
    for y in range(S):
        for x in range(S):
            n = 0.5 + 0.5 * math.sin(x * 0.6 + y * 0.35) * math.cos(y * 0.5 - x * 0.2)
            d.point((x, y), fill=rgba(mix((14, 8, 28), (52, 22, 78), n * 0.7)))
    t = 2 * math.pi * frame / frames
    for k in range(14):
        x, y = rnd.randrange(2, S - 2), rnd.randrange(2, S - 2)
        ph = rnd.random() * 2 * math.pi
        b = 0.5 + 0.5 * math.sin(t + ph)
        d.point((x, y), fill=rgba(mix((120, 90, 170), (255, 255, 255), b)))
        if b > 0.8:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                d.point((x + dx, y + dy), fill=rgba(mix((60, 40, 100), (220, 200, 255), b - 0.5)))
    # 虹色の縁（色相が縁に沿って流れる）
    import colorsys
    edge = [(x, 0) for x in range(S)] + [(S - 1, y) for y in range(1, S)] + \
           [(x, S - 1) for x in range(S - 2, -1, -1)] + [(0, y) for y in range(S - 2, 0, -1)]
    for i, (x, y) in enumerate(edge):
        h = (i / len(edge) + frame / frames) % 1.0
        r, g, b = colorsys.hsv_to_rgb(h, 0.55, 1.0)
        d.point((x, y), fill=(int(r * 255), int(g * 255), int(b * 255), 255))
    for x, y in ((1, 1), (S - 2, 1), (1, S - 2), (S - 2, S - 2)):
        d.point((x, y), fill=(255, 255, 255, 255))
    return im


def creative_casing_sheet():
    sheet = Image.new('RGBA', (S, S * CREATIVE_FRAMES), (0, 0, 0, 0))
    for f in range(CREATIVE_FRAMES):
        sheet.paste(creative_casing(f), (0, S * f))
    return sheet


def creative_front(on, frame=0, frames=CREATIVE_FRAMES):
    """クリエイティブ電源の正面: 星空の外装の中央に、脈打つ星（無限の電力のしるし）。"""
    im = creative_casing(frame, frames)
    d = ImageDraw.Draw(im)
    t = 2 * math.pi * frame / frames
    p = 0.5 + 0.5 * math.sin(t) if on else 0.0
    core = mix((255, 150, 255), (255, 255, 255), p)
    ray = mix((200, 90, 220), (255, 200, 255), p)
    r = 4 + (1 if p > 0.6 else 0)
    for k in range(1, r + 1):
        a = 1 - k / (r + 1)
        c = rgba(mix((40, 20, 60), ray, a))
        for dx, dy in ((k, 0), (-k, 0), (0, k), (0, -k)):
            d.point((7 + dx, 7 + dy), fill=c)
            d.point((8 + dx, 8 + dy), fill=c)
    for k in (1, 2):
        for x, y in ((7 - k, 7 - k), (8 + k, 7 - k), (7 - k, 8 + k), (8 + k, 8 + k)):
            d.point((x, y), fill=rgba(mix((60, 30, 90), ray, 0.7 - 0.25 * k)))
    d.rectangle([6, 6, 9, 9], fill=rgba(core))
    d.rectangle([7, 7, 8, 8], fill=(255, 255, 255, 255))
    return im


def machine_front(iid, stage, on):
    """正面のテクスチャと、動く場合のコマ数を返す。段階3以上の稼働中は8コマ。"""
    frames = 8 if on and stage >= 3 or iid == 'creative_energy_source' else 1
    sheet = Image.new('RGBA', (S, S * frames), (0, 0, 0, 0))
    for f in range(frames):
        if iid == 'creative_energy_source':
            sheet.paste(creative_front(on, f, frames), (0, S * f))
        elif iid == 'degenerate_furnace_controller':
            sheet.paste(furnace_front(on, f, frames), (0, S * f))
        elif iid in CONTROLLER_FRONTS:
            sheet.paste(controller_front(iid, on, f, frames), (0, S * f))
        else:
            sheet.paste(front_frame(iid, stage, on, f, frames), (0, S * f))
    return sheet, frames


def boost_overlay(frames=16):
    """恩恵の模様: 縁から少し内側の1本の道を、やわらかい光がゆっくり巡る（動くテクスチャ）。
    縁そのものは光らせない。明るさの差は小さく、常にうっすら光っている道の上を、少しだけ明るい所が流れる。"""
    sheet = Image.new('RGBA', (S, S * frames), (0, 0, 0, 0))
    lane = [(x, 3) for x in range(3, S - 3)] + [(S - 4, y) for y in range(4, S - 3)] + \
           [(x, S - 4) for x in range(S - 5, 2, -1)] + [(3, y) for y in range(S - 5, 3, -1)]
    n = len(lane)
    for f in range(frames):
        im = Image.new('RGBA', (S, S), (0, 0, 0, 0))
        d = ImageDraw.Draw(im)
        for k, (x, y) in enumerate(lane):
            # 道に沿ったなだらかな明るさの波（2つの山）。最も暗い所でも 0.6、明るい所で 1.0
            phase = 2 * math.pi * (k / n * 2 - f / frames)
            w = 0.8 + 0.2 * math.cos(phase)
            d.point((x, y), fill=(int(150 + 60 * w), int(220 + 25 * w), 255, int(150 * w)))
        sheet.paste(im, (0, S * f))
    return sheet


# ---------------------------------------------------------------- 縮退熱炉（コントローラと部品で同じ素材・配色）

FURNACE = dict(
    steel=(66, 64, 72),       # 外装板の黒鉄
    frame=(44, 42, 50),       # 枠の重い鋼材
    edge=(104, 100, 110),     # 面取りの明るい縁
    copper=(186, 112, 62),    # 磨いた銅の金具
    heat=(255, 138, 56),      # 熱の光
    hot=(255, 226, 176),      # 白熱
)


def _bevel(d, color, light, dark, inset=0):
    a, b = inset, S - 1 - inset
    d.line([(a, a), (b, a)], fill=rgba(light))
    d.line([(a, a), (a, b)], fill=rgba(light))
    d.line([(a, b), (b, b)], fill=rgba(dark))
    d.line([(b, a), (b, b)], fill=rgba(dark))


def furnace_shell(seed=40):
    """外装板: 黒鉄の板。面取りの縁と、四隅の銅の鋲。マルチブロック搬入出ポートと入れ替えられる板。"""
    f = FURNACE
    im = weathered(f['steel'], seed=seed, strength=1.2)
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, S - 1, S - 1], outline=rgba(darken(f['steel'], 0.45)))
    _bevel(d, f['steel'], lighten(f['steel'], 0.18), darken(f['steel'], 0.25), inset=1)
    # 板を留める細い溝
    d.line([(4, 7), (11, 7)], fill=rgba(darken(f['steel'], 0.3)))
    d.line([(4, 8), (11, 8)], fill=rgba(lighten(f['steel'], 0.08)))
    for x, y in ((3, 3), (S - 4, 3), (3, S - 4), (S - 4, S - 4)):
        d.point((x, y), fill=rgba(f['copper']))
        d.point((x + 1, y + 1), fill=rgba(darken(f['copper'], 0.4)))
    return im


def furnace_part(iid):
    f = FURNACE
    if iid == 'degenerate_furnace_shell':
        return furnace_shell()
    if iid == 'degenerate_furnace_frame':
        # 枠: 重い鋼材の梁。太い面取りと、銅のボルト
        im = weathered(f['frame'], seed=41, strength=1.4)
        d = ImageDraw.Draw(im)
        d.rectangle([0, 0, S - 1, S - 1], outline=rgba(darken(f['frame'], 0.5)))
        _bevel(d, f['frame'], f['edge'], darken(f['frame'], 0.35), inset=1)
        _bevel(d, f['frame'], lighten(f['frame'], 0.12), darken(f['frame'], 0.2), inset=2)
        d.rectangle([5, 5, 10, 10], outline=rgba(darken(f['frame'], 0.3)))
        d.rectangle([6, 6, 9, 9], fill=rgba(lighten(f['frame'], 0.06)))
        for x, y in ((3, 3), (S - 4, 3), (3, S - 4), (S - 4, S - 4)):
            d.point((x, y), fill=rgba(lighten(f['copper'], 0.15)))
        return im
    if iid == 'degenerate_furnace_piston':
        # 圧縮ピストン: 外から見える丸い頭。銅の輪の奥に、押しつぶす熱がわずかに見える
        im = furnace_shell(seed=42)
        d = ImageDraw.Draw(im)
        d.ellipse([2, 2, 13, 13], fill=rgba(f['frame']), outline=rgba(f['copper']))
        d.ellipse([4, 4, 11, 11], fill=rgba(lighten(f['frame'], 0.15)), outline=rgba(darken(f['copper'], 0.3)))
        d.ellipse([6, 6, 9, 9], fill=rgba(darken(f['heat'], 0.25)))
        d.point((7, 7), fill=rgba(f['heat']))
        d.point((8, 8), fill=rgba(darken(f['heat'], 0.1)))
        for x, y in ((7, 3), (7, 12), (3, 7), (12, 7)):
            d.point((x, y), fill=rgba(f['edge']))
        return im
    if iid == 'degenerate_furnace_fin':
        # 放熱フィン: 縦に並ぶ薄い板。すき間の奥がうっすら赤い
        im = weathered(f['steel'], seed=43)
        d = ImageDraw.Draw(im)
        for x in range(1, S - 1):
            if x % 3 == 1:
                d.line([(x, 2), (x, S - 3)], fill=rgba(lighten(f['steel'], 0.22)))
            elif x % 3 == 2:
                d.line([(x, 2), (x, S - 3)], fill=rgba(darken(f['steel'], 0.15)))
            else:
                for y in range(2, S - 2):
                    k = (y - 2) / (S - 5)
                    d.point((x, y), fill=rgba(mix((26, 22, 26), (120, 52, 30), k * 0.8)))
        d.rectangle([0, 0, S - 1, 1], fill=rgba(f['frame']))
        d.rectangle([0, S - 2, S - 1, S - 1], fill=rgba(f['frame']))
        d.line([(0, 1), (S - 1, 1)], fill=rgba(darken(f['copper'], 0.2)))
        d.line([(0, S - 2), (S - 1, S - 2)], fill=rgba(darken(f['copper'], 0.2)))
        return im
    if iid == 'degenerate_furnace_tube':
        # 熱交換管: 銅の管が3本。継ぎ目の帯
        im, d = new(fill=rgba((30, 28, 32)))
        for x0 in (1, 6, 11):
            for x in range(x0, x0 + 4):
                k = (x - x0) / 3
                c = mix(lighten(f['copper'], 0.3), darken(f['copper'], 0.45), k)
                d.line([(x, 0), (x, S - 1)], fill=rgba(c))
            for y in (3, 12):
                d.line([(x0, y), (x0 + 3, y)], fill=rgba(f['frame']))
        d.rectangle([0, 0, S - 1, S - 1], outline=rgba(darken(f['frame'], 0.4)))
        return im
    if iid == 'degenerate_furnace_window':
        # 観察窓: 琥珀色の耐熱ガラス（半透明）。鋼の縁と銅の留め具
        im, d = new(fill=(255, 176, 96, 64))
        d.rectangle([0, 0, S - 1, S - 1], outline=rgba(f['frame']))
        d.rectangle([1, 1, S - 2, S - 2], outline=rgba(darken(f['copper'], 0.15)))
        for x, y in ((1, 1), (S - 2, 1), (1, S - 2), (S - 2, S - 2)):
            d.point((x, y), fill=rgba(lighten(f['copper'], 0.2)))
        for i in range(4, 9):
            d.point((i, i - 1), fill=(255, 240, 220, 120))
        d.point((10, 4), fill=(255, 240, 220, 90))
        return im
    return None


def furnace_front(on, frame=0, frames=1):
    """縮退熱炉コントローラの正面: 外装板と同じ黒鉄の板に、炉の中をのぞく窓と熱の計器。稼働中は炎がゆらぐ。"""
    f = FURNACE
    im = furnace_shell(seed=44)
    d = ImageDraw.Draw(im)
    # 窓の枠（銅）と、奥の炉
    d.rectangle([3, 3, 12, 10], fill=rgba(f['frame']), outline=rgba(f['copper']))
    t = 2 * math.pi * frame / max(1, frames)
    for y in range(4, 10):
        for x in range(4, 12):
            if not on:
                c = (30, 24, 26) if (x + y) % 5 else (40, 30, 30)
            else:
                # 下ほど熱い。ゆっくりした揺らぎ（強弱は控えめ）
                k = (y - 4) / 5
                w = 0.5 + 0.5 * math.sin(t + x * 0.9 + y * 0.4)
                h = min(1.0, 0.35 + 0.55 * k + 0.12 * w)
                c = mix(darken(f['heat'], 0.55), f['hot'], max(0.0, h - 0.45) * 1.6) if h > 0.45 else mix((60, 26, 18), darken(f['heat'], 0.55), h / 0.45)
            d.point((x, y), fill=rgba(c))
    # 押しつぶす2本のピストン（窓の上下から中心へ）
    d.line([(7, 4), (7, 5)], fill=rgba(f['edge']))
    d.line([(8, 4), (8, 5)], fill=rgba(f['edge']))
    d.line([(7, 8), (7, 9)], fill=rgba(f['edge']))
    d.line([(8, 8), (8, 9)], fill=rgba(f['edge']))
    # 熱の計器（窓の下の横棒）
    d.rectangle([3, 12, 12, 13], fill=rgba(darken(f['frame'], 0.3)))
    if on:
        n = 7 + int(round(1.5 + 1.5 * math.sin(t)))
        for x in range(4, 4 + n):
            k = (x - 4) / 9
            d.point((x, 12), fill=rgba(mix(f['heat'], f['hot'], k)))
            d.point((x, 13), fill=rgba(darken(mix(f['heat'], f['hot'], k), 0.25)))
    else:
        d.point((4, 12), fill=rgba(darken(f['heat'], 0.5)))
    return im



# ---------------------------------------------------------------- マルチブロックの素材（コントローラと部品で同じ素材・配色）

MATERIALS = {
    # 冷却塔: 白いコンクリートと鋼、氷の青
    'tower': dict(plate=(198, 210, 220), frame=(118, 128, 140), edge=(236, 241, 246), trim=(96, 150, 190), glow=(150, 232, 255),
                  hot=(232, 250, 255), dark=(40, 56, 72)),
    # 縮退圧縮炉: 青黒い鋼と、縮退物質の紫
    'compactor': dict(plate=(78, 80, 96), frame=(50, 50, 64), edge=(120, 120, 142), trim=(150, 110, 220), glow=(200, 160, 255),
                      hot=(240, 228, 255), dark=(24, 22, 34)),
    # C空洞: 白い実験容器と、真空のシアン
    'cavity': dict(plate=(216, 222, 232), frame=(146, 156, 172), edge=(244, 247, 251), trim=(86, 168, 208), glow=(140, 230, 255),
                   hot=(230, 252, 255), dark=(30, 44, 58)),
    # シールド発生塔: 紺の石と白い装甲、金の縁
    'shield': dict(plate=(206, 212, 226), frame=(54, 58, 80), edge=(236, 240, 250), trim=(220, 188, 108), glow=(170, 220, 255),
                   hot=(242, 250, 255), dark=(26, 30, 46)),
    # Tシリンダー: 黒と金、時間結晶の紫
    'tipler': dict(plate=(46, 46, 58), frame=(28, 28, 36), edge=(92, 90, 106), trim=(220, 188, 108), glow=(200, 160, 255),
                   hot=(250, 240, 255), dark=(14, 14, 20)),
    # ワームホール生成器: 深い紫の殻と、マゼンタの場
    'wormhole': dict(plate=(48, 36, 66), frame=(26, 20, 38), edge=(100, 80, 128), trim=(176, 112, 232), glow=(214, 144, 255),
                     hot=(250, 232, 255), dark=(12, 8, 20)),
}


def mat_plate(m, seed, rivets=True, seam=True):
    """外装板: 素材の板。面取りの縁と、四隅の鋲。"""
    base = m['plate']
    im = weathered(base, seed=seed, strength=1.1)
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, S - 1, S - 1], outline=rgba(darken(base, 0.4)))
    _bevel(d, base, lighten(base, 0.18), darken(base, 0.22), inset=1)
    if seam:
        d.line([(4, 7), (11, 7)], fill=rgba(darken(base, 0.2)))
        d.line([(4, 8), (11, 8)], fill=rgba(lighten(base, 0.08)))
    if rivets:
        for x, y in ((3, 3), (S - 4, 3), (3, S - 4), (S - 4, S - 4)):
            d.point((x, y), fill=rgba(m['trim']))
            d.point((x + 1, y + 1), fill=rgba(darken(m['trim'], 0.4)))
    return im


def mat_frame(m, seed):
    """枠: 重い鋼材の梁。太い面取りと、縁取りの色のボルト。"""
    base = m['frame']
    im = weathered(base, seed=seed, strength=1.3)
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, S - 1, S - 1], outline=rgba(darken(base, 0.5)))
    _bevel(d, base, m['edge'], darken(base, 0.35), inset=1)
    _bevel(d, base, lighten(base, 0.12), darken(base, 0.2), inset=2)
    d.rectangle([5, 5, 10, 10], outline=rgba(darken(base, 0.3)))
    d.rectangle([6, 6, 9, 9], fill=rgba(lighten(base, 0.06)))
    for x, y in ((3, 3), (S - 4, 3), (3, S - 4), (S - 4, S - 4)):
        d.point((x, y), fill=rgba(lighten(m['trim'], 0.1)))
    return im


def mat_window(m, tint, alpha=64):
    """観察窓: 色つきの半透明ガラス。枠と留め具。"""
    im, d = new(fill=tint + (alpha,))
    d.rectangle([0, 0, S - 1, S - 1], outline=rgba(m['frame']))
    d.rectangle([1, 1, S - 2, S - 2], outline=rgba(darken(m['trim'], 0.1)))
    for x, y in ((1, 1), (S - 2, 1), (1, S - 2), (S - 2, S - 2)):
        d.point((x, y), fill=rgba(lighten(m['trim'], 0.2)))
    for i in range(4, 9):
        d.point((i, i - 1), fill=(255, 255, 255, 110))
    d.point((10, 4), fill=(255, 255, 255, 80))
    return im


def mat_coil(m, seed, vertical=False):
    """コイル: 枠の中に巻いた線。中心に光る芯。"""
    im = weathered(m['dark'], seed=seed, strength=0.8)
    d = ImageDraw.Draw(im)
    for k in range(2, S - 2):
        c = lighten(m['trim'], 0.25) if k % 2 == 0 else darken(m['trim'], 0.35)
        if vertical:
            d.line([(k, 2), (k, S - 3)], fill=rgba(c))
        else:
            d.line([(2, k), (S - 3, k)], fill=rgba(c))
    if vertical:
        d.line([(2, 7), (S - 3, 7)], fill=rgba(m['glow']))
        d.line([(2, 8), (S - 3, 8)], fill=rgba(lighten(m['glow'], 0.4)))
    else:
        d.line([(7, 2), (7, S - 3)], fill=rgba(m['glow']))
        d.line([(8, 2), (8, S - 3)], fill=rgba(lighten(m['glow'], 0.4)))
    d.rectangle([0, 0, S - 1, S - 1], outline=rgba(m['frame']))
    d.rectangle([1, 1, S - 2, S - 2], outline=rgba(darken(m['frame'], 0.3)))
    return im


def multiblock_part(iid):
    """縮退熱炉以外のマルチブロックの部品。対応しないものは None。"""
    T, C, V, SH, TI, W = (MATERIALS[k] for k in ('tower', 'compactor', 'cavity', 'shield', 'tipler', 'wormhole'))
    # ---- 冷却塔
    if iid == 'cooling_tower_base':
        im = weathered((148, 152, 158), seed=50, strength=1.4)
        d = ImageDraw.Draw(im)
        for y in (4, 9, 14):
            d.line([(0, y), (S - 1, y)], fill=(118, 122, 128, 255))
        for y0, xs in ((0, (5, 12)), (5, (2, 9)), (10, (6, 13))):
            for x in xs:
                d.line([(x, y0), (x, y0 + 3)], fill=(118, 122, 128, 255))
        d.rectangle([0, 0, S - 1, S - 1], outline=(96, 100, 106, 255))
        return im
    if iid == 'cooling_tower_casing':
        im = mat_plate(T, 51, seam=False)
        d = ImageDraw.Draw(im)
        for x in (5, 10):
            d.line([(x, 2), (x, S - 3)], fill=rgba(darken(T['plate'], 0.1)))
            d.line([(x + 1, 2), (x + 1, S - 3)], fill=rgba(lighten(T['plate'], 0.1)))
        return im
    if iid == 'cooling_tower_glass':
        return mat_window(T, (180, 228, 250), 60)
    if iid == 'cooling_tower_coolant_band':
        im = weathered(T['frame'], seed=52)
        d = ImageDraw.Draw(im)
        for y0 in (3, 9):
            d.rectangle([0, y0, S - 1, y0 + 3], fill=rgba(darken(T['trim'], 0.2)))
            d.line([(0, y0 + 1), (S - 1, y0 + 1)], fill=rgba(T['glow']))
            d.line([(0, y0), (S - 1, y0)], fill=rgba(lighten(T['glow'], 0.6)))
            for x in range(1, S, 5):
                d.line([(x, y0), (x, y0 + 3)], fill=rgba(T['frame']))
        d.rectangle([0, 0, S - 1, S - 1], outline=rgba(darken(T['frame'], 0.3)))
        return im
    if iid == 'cooling_tower_rim':
        im = mat_frame(T, 53)
        d = ImageDraw.Draw(im)
        d.line([(1, 2), (S - 2, 2)], fill=rgba(T['edge']))
        d.line([(1, 3), (S - 2, 3)], fill=rgba(T['trim']))
        return im
    if iid == 'cooling_tower_grate':
        im, d = new()
        for k in range(0, S, 4):
            d.line([(k, 0), (k, S - 1)], fill=rgba(T['frame']))
            d.line([(0, k), (S - 1, k)], fill=rgba(T['frame']))
            d.line([(k + 1, 0), (k + 1, S - 1)], fill=rgba(darken(T['frame'], 0.3)))
        d.rectangle([0, 0, S - 1, S - 1], outline=rgba(darken(T['frame'], 0.3)))
        return im
    # ---- 縮退圧縮炉
    if iid == 'degenerate_compactor_plate':
        return mat_plate(C, 54)
    if iid == 'degenerate_compactor_frame':
        return mat_frame(C, 55)
    if iid == 'degenerate_compactor_ram':
        im = mat_plate(C, 56, rivets=False, seam=False)
        d = ImageDraw.Draw(im)
        d.rectangle([2, 2, 13, 13], fill=rgba(C['frame']), outline=rgba(C['trim']))
        for k in range(3, 13, 3):
            d.line([(k, 3), (k + 2, 3)], fill=rgba((230, 200, 80)))
            d.line([(k, 12), (k + 2, 12)], fill=rgba((230, 200, 80)))
        d.rectangle([5, 5, 10, 10], fill=rgba(lighten(C['frame'], 0.2)), outline=rgba(C['edge']))
        d.rectangle([7, 7, 8, 8], fill=rgba(C['glow']))
        return im
    if iid == 'degenerate_compactor_anvil':
        im = weathered(C['dark'], seed=57, strength=1.2)
        d = ImageDraw.Draw(im)
        d.rectangle([0, 0, S - 1, S - 1], outline=rgba(C['frame']))
        d.rectangle([2, 2, 13, 13], fill=rgba(darken(C['plate'], 0.3)), outline=rgba(C['edge']))
        d.line([(3, 3), (12, 12)], fill=rgba(darken(C['trim'], 0.3)))
        d.line([(12, 3), (3, 12)], fill=rgba(darken(C['trim'], 0.3)))
        d.rectangle([6, 6, 9, 9], fill=rgba(darken(C['glow'], 0.4)))
        return im
    if iid == 'degenerate_compactor_vent':
        im = mat_plate(C, 58, rivets=False, seam=False)
        d = ImageDraw.Draw(im)
        for y in range(3, 13, 3):
            d.rectangle([2, y, 13, y + 1], fill=rgba(C['dark']))
            d.line([(2, y), (13, y)], fill=rgba(darken(C['trim'], 0.5)))
        return im
    if iid == 'degenerate_compactor_window':
        return mat_window(C, (190, 150, 255), 60)
    # ---- C空洞
    if iid == 'casimir_cavity_wall':
        im = mat_plate(V, 59, seam=False)
        d = ImageDraw.Draw(im)
        d.line([(3, 12), (12, 12)], fill=rgba(V['trim']))
        return im
    if iid == 'casimir_cavity_frame':
        return mat_frame(V, 60)
    if iid == 'casimir_cavity_pump':
        im = mat_frame(V, 61)
        d = ImageDraw.Draw(im)
        d.ellipse([2, 2, 13, 13], fill=rgba(V['dark']), outline=rgba(V['edge']))
        d.line([(7, 3), (8, 12)], fill=rgba(V['glow']))
        d.line([(3, 8), (12, 7)], fill=rgba(V['glow']))
        d.point((7, 7), fill=rgba(V['hot']))
        d.point((8, 8), fill=rgba(V['hot']))
        return im
    if iid == 'casimir_cavity_shield':
        return mat_coil(V, 62, vertical=True)
    if iid == 'casimir_cavity_window':
        return mat_window(V, (170, 236, 255), 56)
    # ---- シールド発生塔
    if iid == 'shield_tower_plinth':
        im = weathered(SH['frame'], seed=63, strength=1.3)
        d = ImageDraw.Draw(im)
        d.rectangle([0, 0, S - 1, S - 1], outline=rgba(darken(SH['frame'], 0.4)))
        _bevel(d, SH['frame'], lighten(SH['frame'], 0.25), darken(SH['frame'], 0.3), inset=1)
        d.line([(2, 12), (13, 12)], fill=rgba(SH['trim']))
        d.line([(2, 3), (13, 3)], fill=rgba(darken(SH['trim'], 0.3)))
        return im
    if iid == 'shield_tower_coil':
        return mat_coil(SH, 64)
    if iid == 'shield_tower_body':
        im = mat_plate(SH, 65, rivets=True, seam=False)
        d = ImageDraw.Draw(im)
        d.line([(7, 2), (7, S - 3)], fill=rgba(darken(SH['plate'], 0.15)))
        d.line([(8, 2), (8, S - 3)], fill=rgba(lighten(SH['plate'], 0.1)))
        return im
    if iid == 'shield_tower_waveguide':
        im, d = new(fill=(170, 220, 255, 56))
        d.rectangle([0, 0, S - 1, S - 1], outline=rgba(SH['trim']))
        for x in (4, 11):
            d.line([(x, 1), (x, S - 2)], fill=(220, 240, 255, 120))
        d.line([(7, 1), (7, S - 2)], fill=(255, 255, 255, 90))
        return im
    if iid == 'shield_tower_crown':
        im = weathered(darken(SH['trim'], 0.15), seed=66, strength=1.0)
        d = ImageDraw.Draw(im)
        d.rectangle([0, 0, S - 1, S - 1], outline=rgba(darken(SH['trim'], 0.5)))
        d.polygon([(8, 2), (13, 8), (8, 13), (3, 8)], fill=rgba(SH['edge']), outline=rgba(lighten(SH['trim'], 0.3)))
        d.polygon([(8, 5), (10, 8), (8, 10), (6, 8)], fill=rgba(SH['glow']))
        return im
    # ---- Tシリンダー
    if iid == 'tipler_housing':
        return mat_plate(TI, 67)
    if iid == 'tipler_frame':
        return mat_frame(TI, 68)
    if iid == 'tipler_bearing':
        im = mat_plate(TI, 69, rivets=False, seam=False)
        d = ImageDraw.Draw(im)
        d.ellipse([2, 2, 13, 13], outline=rgba(TI['trim']))
        d.ellipse([4, 4, 11, 11], fill=rgba(TI['frame']), outline=rgba(darken(TI['trim'], 0.3)))
        d.ellipse([6, 6, 9, 9], fill=rgba(TI['edge']))
        for x, y in ((7, 3), (7, 12), (3, 7), (12, 7)):
            d.point((x, y), fill=rgba(lighten(TI['trim'], 0.3)))
        return im
    if iid == 'tipler_window':
        return mat_window(TI, (200, 170, 255), 52)
    if iid == 'tipler_holder':
        im = mat_plate(TI, 70, rivets=False, seam=False)
        d = ImageDraw.Draw(im)
        d.rectangle([4, 2, 11, 13], fill=rgba(TI['dark']), outline=rgba(TI['trim']))
        d.polygon([(8, 3), (10, 7), (8, 12), (6, 7)], fill=rgba(TI['glow']))
        d.line([(8, 4), (8, 10)], fill=rgba(TI['hot']))
        return im
    # ---- ワームホール生成器
    # ---- Pリアクターの追加部品（炉殻と同じ黒い素材）
    if iid == 'reactor_stabilizer':
        im = weathered((26, 26, 34), seed=74, strength=1.4)
        d = ImageDraw.Draw(im)
        d.rectangle([0, 0, S - 1, S - 1], outline=(236, 238, 240, 255))
        d.ellipse([2, 2, 13, 13], outline=(220, 188, 108, 255))
        d.ellipse([4, 4, 11, 11], outline=(150, 120, 70, 255))
        d.ellipse([6, 6, 9, 9], fill=(150, 232, 255, 255))
        for x, y in ((7, 1), (1, 7), (14, 8), (8, 14)):
            d.point((x, y), fill=(220, 188, 108, 255))
        return im
    if iid == 'reactor_mass_alarm':
        return alarm_texture(False)
    if iid == 'wormhole_generator_shell':
        im = mat_plate(W, 71, seam=False)
        d = ImageDraw.Draw(im)
        d.arc([2, 2, 13, 13], 200, 340, fill=rgba(darken(W['trim'], 0.25)))
        return im
    if iid == 'wormhole_generator_coil':
        return mat_coil(W, 72)
    if iid == 'wormhole_generator_focuser':
        im = mat_plate(W, 73, rivets=False, seam=False)
        d = ImageDraw.Draw(im)
        d.ellipse([1, 1, 14, 14], fill=rgba(W['dark']), outline=rgba(W['trim']))
        d.ellipse([4, 4, 11, 11], outline=rgba(W['glow']))
        d.ellipse([6, 6, 9, 9], fill=rgba(W['hot']))
        return im
    if iid == 'wormhole_generator_window':
        return mat_window(W, (200, 150, 255), 56)
    if iid == 'wormhole_generator_port':
        # 外殻と同じ板に、受電口（白い矢印）と搬出口（赤紫の矢印）
        im = mat_plate(W, 74, seam=False)
        d = ImageDraw.Draw(im)
        d.rectangle([3, 3, 12, 12], fill=rgba(W['dark']), outline=rgba(W['trim']))
        w, o = (230, 234, 238, 255), rgba(W['glow'])
        d.line([(4, 6), (8, 6)], fill=w)
        d.point([(7, 5), (7, 7), (6, 4), (6, 8)], fill=w)
        d.line([(7, 9), (11, 9)], fill=o)
        d.point([(8, 8), (8, 10), (9, 7), (9, 11)], fill=o)
        for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
            d.point((x, y), fill=rgba(W['hot']))
        return im
    return None


def alarm_texture(on):
    """炉心質量警報器: 黒い炉殻に、赤いランプと警告の縞。"""
    im = weathered((26, 26, 34), seed=75, strength=1.3)
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, S - 1, S - 1], outline=(236, 238, 240, 255))
    for k in range(0, S, 4):
        d.line([(k, 1), (k + 2, 1)], fill=(230, 190, 60, 255))
        d.line([(k + 2, S - 2), (k + 4, S - 2)], fill=(230, 190, 60, 255))
    lamp = (255, 70, 60) if on else (110, 30, 30)
    d.ellipse([4, 4, 11, 11], fill=rgba(darken(lamp, 0.3)), outline=(160, 160, 170, 255))
    d.ellipse([5, 5, 10, 10], fill=rgba(lamp))
    d.point((6, 6), fill=rgba(lighten(lamp, 0.6)))
    return im


# コントローラの正面: (外装の部品, 素材, 絵柄)
CONTROLLER_FRONTS = {
    'cooling_tower_controller': ('cooling_tower_casing', 'tower', 'snow'),
    'degenerate_compactor_controller': ('degenerate_compactor_plate', 'compactor', 'inward'),
    'casimir_cavity_controller': ('casimir_cavity_wall', 'cavity', 'mirrors'),
    'shield_tower_core': ('shield_tower_plinth', 'shield', 'dome'),
    'tipler_core': ('tipler_housing', 'tipler', 'cylinder'),
    'wormhole_generator_core': ('wormhole_generator_shell', 'wormhole', 'swirl'),
}


def controller_front(iid, on, frame=0, frames=1):
    """マルチブロックのコントローラの正面: 部品と同じ外装の板に、操作盤の画面と計器。"""
    skin, mat, icon = CONTROLLER_FRONTS[iid]
    m = MATERIALS[mat]
    im = multiblock_part(skin)
    d = ImageDraw.Draw(im)
    d.rectangle([3, 3, 12, 10], fill=rgba(m['dark']), outline=rgba(m['trim']))
    t = 2 * math.pi * frame / max(1, frames)
    if on:
        pulse = 0.5 + 0.5 * math.sin(t)
        col = rgba(mix(m['glow'], lighten(m['glow'], 0.3), 0.35 + 0.2 * pulse))
        hi = rgba(lighten(m['glow'], 0.62 + 0.1 * pulse))
    else:
        col = rgba(mix(m['glow'], m['dark'], 0.65))
        hi = rgba(mix(m['glow'], m['dark'], 0.45))
    # 8×8 の絵柄の中央 8×6 を画面に入れる
    rows = ICONS.get(icon, ICONS['generic'])[1:7]
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == '#':
                d.point((4 + x, 4 + y), fill=col)
            elif ch == '+':
                d.point((4 + x, 4 + y), fill=hi)
    d.rectangle([3, 12, 12, 13], fill=rgba(darken(m['frame'], 0.3)))
    if on:
        n = 7 + int(round(1.5 + 1.5 * math.sin(t)))
        for x in range(4, 4 + n):
            k = (x - 4) / 9
            d.point((x, 12), fill=rgba(mix(m['glow'], m['hot'], k)))
            d.point((x, 13), fill=rgba(darken(mix(m['glow'], m['hot'], k), 0.25)))
    else:
        d.point((4, 12), fill=rgba(darken(m['glow'], 0.5)))
    return im


# ---------------------------------------------------------------- マルチブロック搬入出ポート（どの機械にも置くので、落ち着いた中間の色）

def multiblock_port():
    im = weathered((84, 88, 96), seed=45, strength=1.1)
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, S - 1, S - 1], outline=(40, 42, 48, 255))
    _bevel(d, (84, 88, 96), (128, 132, 140), (56, 58, 66), inset=1)
    d.rectangle([3, 3, 12, 12], fill=(26, 28, 34, 255), outline=(150, 156, 166, 255))
    # 入る矢印（白、右向き）と出る矢印（琥珀、左向き）
    w, o = (230, 234, 238, 255), (255, 196, 110, 255)
    d.line([(4, 6), (8, 6)], fill=w)
    d.point([(7, 5), (7, 7), (6, 4), (6, 8)], fill=w)
    d.line([(7, 9), (11, 9)], fill=o)
    d.point([(8, 8), (8, 10), (9, 7), (9, 11)], fill=o)
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
        d.point((x, y), fill=(200, 206, 214, 255))
    return im


# ---------------------------------------------------------------- 部品・ブロック

def part_texture(iid):
    fp = furnace_part(iid)
    if fp is None:
        fp = multiblock_part(iid)
    if fp is not None:
        return fp
    if iid == 'multiblock_port':
        return multiblock_port()
    if iid == 'heat_exchange_core':
        im = weathered((128, 82, 54), seed=11)
        d = ImageDraw.Draw(im)
        for x in range(1, S - 1, 2):
            d.line([(x, 1), (x, S - 2)], fill=(214, 144, 92, 255))
        d.line([(0, 7), (S - 1, 7)], fill=(150, 230, 255, 255))
        d.line([(0, 8), (S - 1, 8)], fill=(110, 200, 240, 255))
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


# ---------------------------------------------------------------- 隕石・雷・深海・虚空の素材と、中間素材

def raw_meteoric_iron():
    """隕鉄の原石: ごつごつした焦げ茶の塊に、銀色の金属の粒がのぞく。"""
    body = (92, 78, 70)

    def f(d):
        d.polygon([(3, 7), (5, 3), (10, 2), (13, 5), (13, 10), (10, 13), (5, 13), (2, 10)], fill=rgba(body))
        d.polygon([(5, 3), (10, 2), (12, 4), (7, 6)], fill=rgba(lighten(body, 0.18)))
        d.polygon([(10, 13), (13, 10), (13, 8), (9, 11)], fill=rgba(darken(body, 0.3)))
        for x, y in ((6, 8), (9, 6), (10, 9), (5, 11), (8, 10)):
            d.point((x, y), fill=(206, 212, 220, 255))
        d.point((9, 7), fill=(245, 248, 252, 255))
    return outlined(f)


def meteoric_ingot():
    """隕鉄インゴット: 銀灰色の地に、鉄とニッケルの結晶の筋（ウィドマンシュテッテン構造）。"""
    c = (150, 154, 164)

    def f(d):
        d.polygon([(2, 9), (6, 5), (13, 5), (9, 9)], fill=rgba(lighten(c, 0.3)))
        d.rectangle([2, 9, 9, 12], fill=rgba(c))
        d.polygon([(9, 9), (13, 5), (13, 8), (9, 12)], fill=rgba(darken(c, 0.25)))
        for k in range(3):
            d.line([(4 + k * 2, 8), (6 + k * 2, 6)], fill=rgba(darken(c, 0.12)))
        d.line([(3, 11), (5, 9)], fill=rgba(darken(c, 0.15)))
        d.line([(6, 12), (8, 10)], fill=rgba(darken(c, 0.15)))
        d.point((7, 10), fill=rgba(lighten(c, 0.5)))
    return outlined(f)


def stardust():
    """星屑: 小さな山に、水色と菫色にまたたく粒。"""
    def f(d):
        d.polygon([(2, 12), (5, 8), (8, 7), (11, 8), (14, 12), (8, 13)], fill=(70, 76, 110, 255))
        d.polygon([(5, 9), (8, 7), (11, 8), (8, 10)], fill=(104, 112, 160, 255))
        for x, y, col in ((5, 10, (150, 230, 255)), (8, 8, (255, 255, 255)), (10, 10, (210, 170, 255)), (7, 11, (150, 230, 255)),
                          (12, 11, (210, 170, 255))):
            d.point((x, y), fill=rgba(col))
        # 上に舞う粒
        for x, y in ((4, 4), (9, 3), (12, 5)):
            d.point((x, y), fill=(200, 236, 255, 255))
        d.line([(9, 2), (9, 4)], fill=(255, 255, 255, 200))
        d.line([(8, 3), (10, 3)], fill=(255, 255, 255, 200))
    return outlined(f)


def fulgurite():
    """雷ガラス: 枝分かれした管状の天然ガラス。外はざらついた砂色、中は溶けて光るガラス。"""
    sand, glass = (196, 170, 120), (236, 224, 190)

    def f(d):
        d.line([(3, 13), (6, 9), (8, 7), (12, 2)], fill=rgba(sand), width=3)
        d.line([(6, 9), (3, 5)], fill=rgba(sand), width=2)
        d.line([(8, 7), (12, 9)], fill=rgba(sand), width=2)
        d.line([(4, 12), (7, 8), (11, 3)], fill=rgba(glass))
        d.point((11, 3), fill=(255, 255, 255, 255))
        d.point((3, 5), fill=rgba(lighten(sand, 0.3)))
    return outlined(f)


def void_dust():
    """虚空の塵: 黒紫の渦に、かすかな星の粒。"""
    def f(d):
        d.ellipse([3, 3, 12, 12], fill=(30, 18, 46, 255))
        d.arc([4, 4, 11, 11], 200, 360, fill=(140, 90, 210, 255))
        d.arc([5, 5, 10, 10], 20, 180, fill=(190, 140, 255, 255))
        d.point((8, 8), fill=(240, 220, 255, 255))
        for x, y in ((5, 6), (10, 5), (9, 10)):
            d.point((x, y), fill=(170, 130, 230, 255))
    return outlined(f)


def coolant_cartridge():
    """冷媒カートリッジ: 鋼の容器に水色の帯と霜。口金の弁。"""
    steel = (176, 184, 196)

    def f(d):
        d.rectangle([5, 3, 10, 13], fill=rgba(steel))
        d.rectangle([5, 3, 6, 13], fill=rgba(lighten(steel, 0.25)))
        d.rectangle([9, 3, 10, 13], fill=rgba(darken(steel, 0.2)))
        d.rectangle([5, 7, 10, 9], fill=(120, 220, 255, 255))
        d.line([(6, 8), (9, 8)], fill=(230, 250, 255, 255))
        d.rectangle([6, 1, 9, 2], fill=rgba(darken(steel, 0.35)))
        for x, y in ((6, 11), (8, 5), (9, 12)):
            d.point((x, y), fill=(236, 248, 255, 255))
    return outlined(f)


def phase_sync_plate():
    """位相同期板: 薄い光格子の板に、そろった波の縞（琥珀と菫）。"""
    def f(d):
        d.polygon([(1, 7), (9, 3), (14, 6), (6, 10)], fill=(214, 220, 236, 255))
        d.polygon([(1, 7), (6, 10), (6, 12), (1, 9)], fill=(150, 156, 176, 255))
        d.polygon([(6, 10), (14, 6), (14, 8), (6, 12)], fill=(176, 182, 200, 255))
        for k in range(4):
            col = (255, 196, 110) if k % 2 == 0 else (200, 160, 255)
            d.line([(3 + k * 2, 7 + (k % 2)), (7 + k * 2, 5 + (k % 2))], fill=rgba(col))
    return outlined(f)


def degenerate_precursor():
    """縮退前駆体: 押し固められた黒い立方体。ひびから菫色の光が漏れる。"""
    c = (40, 36, 56)

    def f(d):
        d.polygon([(3, 6), (8, 3), (13, 6), (8, 9)], fill=rgba(lighten(c, 0.25)))
        d.polygon([(3, 6), (8, 9), (8, 14), (3, 11)], fill=rgba(c))
        d.polygon([(8, 9), (13, 6), (13, 11), (8, 14)], fill=rgba(darken(c, 0.3)))
        d.line([(5, 8), (6, 11)], fill=(200, 150, 255, 255))
        d.line([(10, 9), (11, 11), (10, 12)], fill=(200, 150, 255, 255))
        d.point((8, 6), fill=(230, 210, 255, 255))
    return outlined(f)


def crystal_memory():
    """クリスタルメモリ（旧文明の記録媒体）: 透き通った六角の水晶板。中に光で刻まれた記録の層が、うっすら光って見える。
    地は半透明で、向こうが透ける。縁と刻まれた層だけ不透明。"""
    im, d = new()
    body = (190, 236, 250)
    pts = [(5, 2), (11, 2), (14, 7), (11, 13), (5, 13), (2, 7)]
    d.polygon(pts, fill=rgba(body, 120))
    # 上半分は光が当たって少し明るい（それでも透ける）
    d.polygon([(5, 2), (11, 2), (14, 7), (2, 7)], fill=rgba(lighten(body, 0.35), 140))
    # 中に刻まれた記録の層（細い光の線と、データの点）
    for y, x0, x1 in ((5, 5, 11), (8, 4, 12), (10, 5, 11)):
        d.line([(x0, y), (x1, y)], fill=(110, 220, 255, 200))
    for x, y in ((6, 6), (9, 6), (7, 9), (10, 9), (6, 11), (9, 11)):
        d.point((x, y), fill=(230, 252, 255, 255))
    # 縁（面取りの稜線）と映り込み
    d.line(pts + [pts[0]], fill=(120, 170, 196, 255))
    d.line([(5, 3), (8, 3)], fill=(255, 255, 255, 230))
    d.point((4, 5), fill=(255, 255, 255, 220))
    return im


def sentinel_core():
    """番人の投影核: 白い枠にはまった細長い水色の八面体の結晶。中心が強く光り、まわりに投影の光の輪。
    結晶は少し透け、枠と稜線だけ不透明。"""
    im, d = new()
    glow = (143, 234, 255)
    # 投影の光の輪（薄く透ける）
    d.ellipse([1, 4, 14, 11], outline=rgba(glow, 110))
    # 結晶（縦長の八面体: 上下の三角を2色で）
    d.polygon([(8, 1), (12, 7), (8, 8), (4, 7)], fill=rgba(lighten(glow, 0.35), 200))
    d.polygon([(4, 7), (8, 8), (8, 15), ], fill=rgba(glow, 180))
    d.polygon([(8, 8), (12, 7), (8, 15)], fill=rgba(darken(glow, 0.25), 200))
    d.line([(8, 1), (12, 7), (8, 15), (4, 7), (8, 1)], fill=(90, 160, 196, 255))
    # 白い留め枠（左右の爪）
    d.rectangle([2, 7, 3, 8], fill=(232, 236, 240, 255))
    d.rectangle([12, 7, 13, 8], fill=(232, 236, 240, 255))
    # 中心の光
    d.rectangle([7, 6, 8, 8], fill=(240, 254, 255, 255))
    d.point((7, 3), fill=(255, 255, 255, 230))
    return im


def degenerate_nucleus():
    """縮退核: 光を飲む黒い球に、菫色の縁の光と、傾いた細い輪。まわりに小さな瓦礫の粒が止まっている。"""
    def f(d):
        d.ellipse([2, 2, 13, 13], fill=(70, 40, 120, 255))
        d.ellipse([3, 3, 12, 12], fill=(160, 110, 240, 255))
        d.ellipse([4, 4, 11, 11], fill=(8, 6, 14, 255))
        d.line([(1, 10), (5, 8), (10, 7), (14, 5)], fill=(225, 200, 255, 255))
        d.point((6, 6), fill=(60, 50, 80, 255))
        for x, y in ((1, 3), (13, 12), (14, 2)):
            d.point((x, y), fill=(140, 140, 150, 255))
    return outlined(f)


def confinement_ring():
    """磁気閉じ込め環: 銀の隕鉄の芯に、赤紫のトポロジカル導線を巻いた輪。"""
    def f(d):
        d.ellipse([2, 3, 13, 12], fill=(150, 154, 164, 255))
        d.ellipse([5, 5, 10, 10], fill=(0, 0, 0, 0))
        for a in range(0, 360, 40):
            x = 7.5 + 5 * math.cos(math.radians(a))
            y = 7.5 + 4.2 * math.sin(math.radians(a))
            d.point((round(x), round(y)), fill=(220, 120, 255, 255))
        d.arc([2, 3, 13, 12], 200, 300, fill=(230, 236, 246, 255))
    return outlined(f)


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
    # 針: ドットを数点打つだけだと、斜めの向きで途切れる。画素ごとに、針（中心から先へ細くなる線分）に
    # どれだけ覆われるかを 4×4 の点で数え、半分近く覆われていれば塗る（どの向きでも途切れない）
    a = angle * 2 * math.pi - math.pi / 2
    dx, dy = math.cos(a), math.sin(a)
    px = im.load()

    def coverage(x, y, length, width):
        hit = 0
        for sx in range(4):
            for sy in range(4):
                qx = x + (sx + 0.5) / 4 - 8
                qy = y + (sy + 0.5) / 4 - 8
                t = qx * dx + qy * dy                     # 針の向きに沿った位置
                n = abs(-qx * dy + qy * dx)               # 針からの距離
                if 0 <= t <= length and n <= width * (1 - 0.6 * t / length):
                    hit += 1
        return hit / 16

    # 真上・真下・真左・真右のときは、針の軸が画素の境目に重なって2ドットの太さになるので、
    # 先端の2ドットぶんは片側だけ残して細く尖らせる
    cardinal = abs(angle * 4 - round(angle * 4)) < 1 / 16
    for y in range(3, 13):
        for x in range(3, 13):
            if coverage(x, y, 4.6, 0.75) >= 0.3:
                qx, qy = x + 0.5 - 8, y + 0.5 - 8
                along = qx * dx + qy * dy
                side = -qx * dy + qy * dx
                if cardinal and along > 4.6 - 2.2 and side > 0:
                    continue
                px[x, y] = (255, 110, 80, 255)            # 北を指す赤い先
    dx, dy = -dx, -dy
    for y in range(3, 13):
        for x in range(3, 13):
            if px[x, y] != (255, 110, 80, 255) and coverage(x, y, 3.0, 0.6) >= 0.3:
                px[x, y] = (210, 214, 222, 255)           # 反対側の白い尾
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
    'wormhole_mouth_casing': lambda: frame((58, 48, 82), (214, 144, 255)),
    'raw_meteoric_iron': raw_meteoric_iron,
    'meteoric_iron_ingot': meteoric_ingot,
    'stardust': stardust,
    'fulgurite': fulgurite,
    'pressure_crystal': lambda: crystal_cluster((60, 104, 206), seed=5, mark=(210, 235, 255)),
    'void_dust': void_dust,
    'coolant_cartridge': coolant_cartridge,
    'phase_sync_plate': phase_sync_plate,
    'degenerate_precursor': degenerate_precursor,
    'confinement_ring': confinement_ring,
    'sentinel_core': sentinel_core,
    'degenerate_nucleus': degenerate_nucleus,
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
    'record_fragment': crystal_memory,
    'decoded_record': lambda: tablet(T1),
    'creative_catalyst': lambda: crystal_cluster((255, 120, 255), mark=(255, 255, 255)),
    'shield_permit': lambda: permit_card(),
    'bh_container': lambda: vessel(None),
    'micro_black_hole': lambda: vessel((200, 140, 255)),
    'black_hole_bomb': lambda: bomb_item(),
    'settings_card': lambda: settings_card(),
    'builder_wand': lambda: outlined(lambda d: (d.line([(3, 12), (10, 5)], fill=(150, 110, 70, 255), width=2),
                                               d.ellipse([10, 2, 13, 5], fill=(255, 120, 255, 255)))),
    'scanner_module_2': lambda: module((60, 54, 90), (250, 200, 90)),
    'scanner_module_3': lambda: module((40, 36, 60), (120, 230, 255)),
    'magnetic_key': lambda: seal_key(1),
    'quantum_key': lambda: seal_key(2),
    'temporal_key': lambda: seal_key(3),
    'singularity_key': lambda: seal_key(4),
    'overclock_chip': lambda: overclock_chip(),
    'gravity_boots': lambda: gravity_boots(),
    'catalyst_stabilizer': lambda: catalyst_stabilizer(),
    'dimensional_pocket': lambda: dimensional_pocket(),
    'sealed_record': lambda: sealed_record(),
}

# ---------------------------------------------------------------- 封印コンテナと鍵

# 封印の段階の色（鍵の芯・コンテナの継ぎ目の光）。Java の SealedContainerRenderer と同じ値
SEAL_COLORS = {1: (110, 205, 238), 2: (190, 160, 255), 3: (250, 182, 84), 4: (255, 110, 150)}


def seal_key(tier):
    """封印コンテナの鍵: 白い持ち手と、段階の色に光る差し込み部（段が増えるほど歯が多い）。"""
    im, d = new()
    c = SEAL_COLORS[tier]
    d.rounded_rectangle([1, 5, 7, 11], radius=2, fill=(232, 236, 240, 255), outline=(120, 126, 140, 255))
    d.rectangle([3, 7, 5, 9], fill=rgba(c))
    d.rectangle([7, 7, 14, 9], fill=(150, 156, 170, 255))
    d.line([(8, 8), (14, 8)], fill=rgba(c))
    for k in range(tier):
        d.point((13 - k * 2, 10), fill=(120, 126, 140, 255))
    d.point((2, 6), fill=(255, 255, 255, 255))
    return im


def overclock_chip():
    """オーバークロック・チップ: 熱で赤く光るチップと放熱フィン。"""
    im, d = new()
    d.rectangle([3, 3, 12, 12], fill=(40, 40, 50, 255), outline=(120, 126, 140, 255))
    for x in range(4, 12, 2):
        d.line([(x, 1), (x, 2)], fill=(200, 180, 120, 255))
        d.line([(x, 13), (x, 14)], fill=(200, 180, 120, 255))
    d.rectangle([5, 5, 10, 10], fill=(255, 110, 60, 255))
    d.rectangle([6, 6, 9, 9], fill=(255, 220, 120, 255))
    d.point((7, 7), fill=(255, 255, 255, 255))
    return im


def gravity_boots():
    """重力ブーツ: 白い装甲のブーツ。かかとに青く光る重力素子。"""
    im, d = new()
    d.polygon([(4, 2), (9, 2), (9, 10), (14, 11), (14, 14), (3, 14), (3, 9)], fill=(230, 234, 240, 255),
              outline=(120, 126, 140, 255))
    d.line([(4, 12), (13, 12)], fill=(70, 74, 84, 255))
    d.line([(4, 6), (8, 6)], fill=(110, 205, 238, 255))
    d.rectangle([3, 10, 5, 13], fill=(110, 205, 238, 255))
    d.point((4, 11), fill=(255, 255, 255, 255))
    return im


def catalyst_stabilizer():
    """触媒安定化剤: 金の口金の小瓶に、金色に光る液。"""
    im, d = new()
    d.rectangle([6, 1, 9, 3], fill=(220, 188, 108, 255))
    d.ellipse([3, 4, 12, 14], fill=(230, 236, 244, 110), outline=(150, 160, 175, 255))
    d.ellipse([4, 8, 11, 13], fill=(250, 200, 90, 255))
    d.point((6, 9), fill=(255, 255, 220, 255))
    d.point((5, 6), fill=(255, 255, 255, 220))
    return im


def dimensional_pocket():
    """次元ポケット: 黒い小袋の口に、紫に渦巻く空間。"""
    im, d = new()
    d.polygon([(3, 5), (12, 5), (13, 14), (2, 14)], fill=(44, 40, 56, 255), outline=(120, 110, 150, 255))
    d.rectangle([3, 3, 12, 5], fill=(220, 188, 108, 255))
    d.ellipse([5, 7, 10, 12], fill=(20, 10, 30, 255), outline=(190, 140, 255, 255))
    d.point((7, 9), fill=(255, 255, 255, 255))
    d.point((9, 10), fill=(190, 140, 255, 255))
    return im


def sealed_record():
    """封印記録: 黒い記録板に、桃色の封印の帯。"""
    im = tablet((255, 110, 150))
    d = ImageDraw.Draw(im)
    d.line([(2, 8), (13, 8)], fill=(255, 110, 150, 255))
    d.point((7, 8), fill=(255, 255, 255, 255))
    return im


def sealed_container_side(tier):
    """封印コンテナの側面: 白いカプセルの外装と縦の継ぎ目。継ぎ目の芯は段階の色（描画で光らせる）。"""
    c = SEAL_COLORS[tier]
    im, d = new(fill=(230, 233, 237, 255))
    d.rectangle([0, 0, 15, 15], outline=(196, 200, 206, 255))
    d.line([(0, 2), (15, 2)], fill=(196, 200, 206, 255))
    d.line([(0, 13), (15, 13)], fill=(196, 200, 206, 255))
    for x in (3, 12):
        d.line([(x, 3), (x, 12)], fill=(150, 156, 166, 255))
    d.line([(7, 4), (7, 11)], fill=rgba(darken(c, 0.15)))
    d.line([(8, 4), (8, 11)], fill=rgba(c))
    for y in (5, 10):
        d.point([(5, y), (10, y)], fill=(170, 176, 186, 255))
    return weather_existing(im, seed=tier, strength=0.04)


def sealed_container_base():
    """封印コンテナの台座: 暗い金属の縁取り。"""
    im, d = new(fill=(52, 56, 64, 255))
    d.rectangle([0, 0, 15, 15], outline=(80, 86, 96, 255))
    for x in range(1, 15, 3):
        d.line([(x, 1), (x, 14)], fill=(44, 48, 55, 255))
    return im


def sealed_container_lid(tier):
    """蓋（4枚の板で1つ）。ブロックの中のドットの位置そのままに貼る（板は x・z とも 1〜8 と 8〜15、厚さは y 13〜15）。
    天面: 外周の縁と、十字の合わせ目、板ごとの段階の色の筋、中央の留め具。行1〜2は板の側面にも使う（縁の帯）。"""
    c = SEAL_COLORS[tier]
    white, rim, seam = (236, 239, 243), (190, 194, 200), (120, 126, 136)
    im, d = new(fill=rgba(white))
    # 外周の縁（側面の2段もここを使う）
    d.rectangle([1, 1, 14, 14], outline=rgba(rim))
    d.rectangle([2, 2, 13, 13], outline=rgba(lighten(white, 0.4)))
    d.line([(1, 2), (14, 2)], fill=rgba(darken(c, 0.1)))
    # 十字の合わせ目（4枚の板の境目）
    d.line([(7, 1), (7, 14)], fill=rgba(seam))
    d.line([(8, 1), (8, 14)], fill=rgba(darken(white, 0.12)))
    d.line([(1, 7), (14, 7)], fill=rgba(seam))
    d.line([(1, 8), (14, 8)], fill=rgba(darken(white, 0.12)))
    # 板ごとの段階の色の筋（外の角へ向かう）
    for x0, y0, dx, dy in ((3, 3, 1, 1), (12, 3, -1, 1), (3, 12, 1, -1), (12, 12, -1, -1)):
        d.line([(x0, y0), (x0 + 2 * dx, y0 + 2 * dy)], fill=rgba(c))
    # 中央の留め具
    d.rectangle([6, 6, 9, 9], fill=(200, 204, 212, 255), outline=(170, 176, 186, 255))
    d.point([(7, 7), (8, 8)], fill=rgba(lighten(c, 0.3)))
    return im


def sealed_container_inner():
    """コンテナの内側: 冷えた暗い金属と、底の光る輪。"""
    im, d = new(fill=(34, 38, 46, 255))
    d.rectangle([0, 0, 15, 15], outline=(60, 66, 76, 255))
    d.ellipse([3, 3, 12, 12], outline=(90, 150, 180, 255))
    return im


def gravity_boots_layer():
    """重力ブーツを履いたときの絵（防具の1枚目、64×32）。ブーツは脚の下半分に描かれる。"""
    from PIL import Image
    im = Image.new('RGBA', (64, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    # 脚の展開図（u=0〜16, v=16〜32）。下側（足首から下）だけ塗る
    d.rectangle([0, 22, 15, 31], fill=(230, 234, 240, 255))
    d.rectangle([4, 16, 11, 19], fill=(230, 234, 240, 255))
    d.line([(0, 22), (15, 22)], fill=(120, 126, 140, 255))
    d.line([(0, 28), (15, 28)], fill=(70, 74, 84, 255))
    for x in (1, 5, 9, 13):
        d.point((x, 25), fill=(110, 205, 238, 255))
    d.line([(0, 30), (15, 30)], fill=(110, 205, 238, 255))
    return im


def vessel(ring):
    """BH格納容器: 鋼の枠に収まった球。中にブラックホールがあれば黒い芯と紫の輪。"""
    im, d = new()
    d.ellipse([3, 3, 12, 12], fill=(200, 220, 236, 90) if ring is None else (30, 18, 44, 255), outline=(150, 160, 175, 255))
    if ring is not None:
        d.ellipse([5, 7, 10, 9], outline=rgba(ring))
        d.ellipse([6, 6, 9, 9], fill=(4, 2, 8, 255))
        d.point((8, 7), fill=(255, 255, 255, 255))
    else:
        d.point((5, 5), fill=(255, 255, 255, 200))
    for x in (2, 13):
        d.line([(x, 4), (x, 11)], fill=(120, 126, 140, 255))
    d.line([(4, 2), (11, 2)], fill=(120, 126, 140, 255))
    d.line([(4, 13), (11, 13)], fill=(120, 126, 140, 255))
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
        d.point((x, y), fill=(220, 188, 108, 255))
    return im


def bomb_item():
    """ブラックホール爆弾: 黒い球に4枚のひれと、紫に光る筋。"""
    im, d = new()
    d.ellipse([3, 4, 12, 13], fill=(30, 26, 40, 255), outline=(90, 80, 110, 255))
    d.polygon([(7, 1), (9, 1), (9, 4), (7, 4)], fill=(150, 156, 170, 255))
    d.line([(4, 8), (11, 8)], fill=(200, 140, 255, 255))
    d.line([(7, 5), (7, 12)], fill=(150, 90, 220, 255))
    d.point((6, 6), fill=(255, 255, 255, 255))
    d.point((10, 2), fill=(255, 200, 80, 255))
    return im


def settings_card():
    """設定カード: 白いカードに、写す・貼るの矢印。"""
    im, d = new()
    d.rectangle([2, 3, 13, 12], fill=(236, 240, 246, 255), outline=(140, 150, 165, 255))
    d.rectangle([3, 4, 12, 5], fill=(110, 205, 238, 255))
    d.line([(4, 8), (8, 8)], fill=(60, 110, 170, 255))
    d.point([(7, 7), (7, 9)], fill=(60, 110, 170, 255))
    d.line([(7, 10), (11, 10)], fill=(240, 160, 60, 255))
    d.point([(8, 9), (8, 11)], fill=(240, 160, 60, 255))
    return im


def permit_card():
    """シールド許可証: 白いカードに、金の縁と青いシールドの紋章、名前の欄。"""
    im, d = new()
    d.rectangle([1, 3, 14, 12], fill=(236, 240, 248, 255), outline=(150, 156, 170, 255))
    d.rectangle([2, 4, 13, 11], outline=(220, 188, 108, 255))
    # シールドの紋章（左）
    d.polygon([(4, 5), (8, 5), (8, 8), (6, 10), (4, 8)], fill=(110, 180, 240, 255), outline=(60, 110, 170, 255))
    d.point((6, 7), fill=(255, 255, 255, 255))
    # 名前の欄（右）
    for y in (6, 8):
        d.line([(9, y), (12, y)], fill=(120, 126, 140, 255))
    d.line([(9, 10), (11, 10)], fill=(220, 188, 108, 255))
    return im


# ---------------------------------------------------------------- 動くアイテムの絵（縦に並べたコマ。gen_data.py が .mcmeta を付ける）

def _glow_pt(d, x, y, c, a=255):
    d.point((round(x), round(y)), fill=rgba(c, a))


def anim_micro_black_hole(t):
    """小型BH格納容器: 枠の中の黒い芯のまわりを、紫の降着円盤の光が回る。"""
    im, d = new()
    d.ellipse([3, 3, 12, 12], fill=(30, 18, 44, 255), outline=(150, 160, 175, 255))
    # 円盤（傾いた楕円）の上を、明るい点が回る
    d.ellipse([4, 7, 11, 9], outline=(120, 70, 170, 255))
    for k in range(3):
        a = (t + k / 3) * 2 * math.pi
        x = 7.5 + 3.4 * math.cos(a)
        y = 8 + 0.9 * math.sin(a)
        _glow_pt(d, x, y, (230, 190, 255) if k == 0 else (190, 140, 255))
    d.ellipse([6, 6, 9, 9], fill=(4, 2, 8, 255))
    pulse = 0.5 + 0.5 * math.sin(t * 2 * math.pi)
    d.point((8, 7), fill=rgba(mix((120, 80, 160), (255, 255, 255), pulse)))
    for x in (2, 13):
        d.line([(x, 4), (x, 11)], fill=(120, 126, 140, 255))
    d.line([(4, 2), (11, 2)], fill=(120, 126, 140, 255))
    d.line([(4, 13), (11, 13)], fill=(120, 126, 140, 255))
    for x, y in ((3, 3), (12, 3), (3, 12), (12, 12)):
        d.point((x, y), fill=(220, 188, 108, 255))
    return im


def anim_exotic_matter(t):
    """エキゾチック物質: 紫の塊の中で渦がゆっくり回り、明るさが脈打つ。"""
    pulse = 0.5 + 0.5 * math.sin(t * 2 * math.pi)
    body = mix((60, 20, 80), (90, 34, 120), pulse * 0.6)

    def f(d):
        d.polygon([(8, 2), (13, 4), (13, 10), (10, 13), (4, 12), (3, 8), (4, 3)], fill=rgba(body))
        for k in range(12):
            a = k * 0.55 + t * 2 * math.pi
            r = 0.5 + k * 0.4
            c = mix((220, 160, 255), (255, 240, 255), pulse) if k > 8 else (220, 160, 255)
            d.point((8 + r * math.cos(a), 8 + r * math.sin(a)), fill=rgba(c))
    return outlined(f)


def _anim_vial(t, liquid, glow, bubbles=True, sparkle=None):
    """液の入った小瓶: 液面がゆれ、光る液なら泡が昇る。sparkle を与えると瓶の中に光の粒がまたたく。"""
    wave = round(math.sin(t * 2 * math.pi))

    def f(d):
        d.rectangle([6, 2, 9, 3], fill=(150, 156, 164, 255))
        d.polygon([(6, 4), (9, 4), (12, 13), (3, 13)], fill=(220, 236, 246, 120))
        top = 8 + wave * 0.5
        d.polygon([(5, top), (10, top - wave * 0.5), (12, 13), (3, 13)], fill=rgba(liquid))
        if glow and bubbles:
            for k in range(2):
                y = 12 - ((t + k * 0.5) % 1.0) * 4
                d.point((6 + k * 3, y), fill=(255, 255, 255, 255))
        if sparkle:
            for k in range(3):
                ph = (t * 3 + k * 0.37) % 1.0
                if ph < 0.35:
                    d.point((5 + k * 2, 10 + (k % 2)), fill=rgba(sparkle))
    return outlined(f)


def anim_star_core(t):
    """人工星核: 光の筋がまたたきながらゆっくり回り、芯が脈打つ。"""
    pulse = 0.5 + 0.5 * math.sin(t * 2 * math.pi)

    def f(d):
        for k in range(8):
            a = k * math.pi / 4 + t * math.pi / 2
            r = (5 if k % 2 == 0 else 4) + (1 if (k + round(t * 8)) % 4 == 0 else 0)
            d.line([(8, 8), (8 + r * math.cos(a), 8 + r * math.sin(a))], fill=rgba(mix((255, 180, 80), (255, 240, 200), pulse)))
        d.ellipse([5, 5, 10, 10], fill=rgba(mix((255, 220, 120), (255, 250, 220), pulse)))
        d.point((7, 6), fill=(255, 255, 255, 255))
    return outlined(f)


def anim_bomb(t):
    """ブラックホール爆弾: 紫の筋が脈打ち、頭の信管が赤く点滅する。"""
    pulse = 0.5 + 0.5 * math.sin(t * 2 * math.pi)
    im, d = new()
    d.ellipse([3, 4, 12, 13], fill=(30, 26, 40, 255), outline=(90, 80, 110, 255))
    d.polygon([(7, 1), (9, 1), (9, 4), (7, 4)], fill=(150, 156, 170, 255))
    d.line([(4, 8), (11, 8)], fill=rgba(mix((150, 90, 220), (230, 190, 255), pulse)))
    d.line([(7, 5), (7, 12)], fill=rgba(mix((110, 60, 180), (200, 140, 255), pulse)))
    d.point((6, 6), fill=(255, 255, 255, 255))
    blink = (t * 2) % 1.0 < 0.5
    d.point((10, 2), fill=(255, 90, 70, 255) if blink else (120, 60, 50, 255))
    return im


def anim_pocket(t):
    """次元ポケット: 口の中の紫の空間が渦を巻く。"""
    im, d = new()
    d.polygon([(3, 5), (12, 5), (13, 14), (2, 14)], fill=(44, 40, 56, 255), outline=(120, 110, 150, 255))
    d.rectangle([3, 3, 12, 5], fill=(220, 188, 108, 255))
    d.ellipse([5, 7, 10, 12], fill=(20, 10, 30, 255), outline=(190, 140, 255, 255))
    for k in range(3):
        a = (t + k / 3) * 2 * math.pi
        d.point((7.5 + 1.4 * math.cos(a), 9.5 + 1.4 * math.sin(a)), fill=(200, 160, 255, 255) if k else (255, 255, 255, 255))
    return im


# ID: (コマを描く関数（t は 0〜1）, コマ数, 1コマの tick 数)
ANIMATED_ITEMS = {
    'micro_black_hole': (anim_micro_black_hole, 12, 2),
    'exotic_matter': (anim_exotic_matter, 12, 3),
    'jet_condensate': (lambda t: _anim_vial(t, (120, 220, 255), True), 8, 3),
    'hawking_condensate': (lambda t: _anim_vial(t, (255, 140, 80), True), 8, 3),
    'anomaly_sample': (lambda t: _anim_vial(t, (60, 20, 80), True, bubbles=False, sparkle=(220, 160, 255)), 12, 3),
    'degraded_anomaly_sample': (lambda t: _anim_vial(t, (90, 80, 100), False, sparkle=(150, 140, 160)), 12, 4),
    'black_hole_bomb': (anim_bomb, 10, 2),
    'artificial_star_core': (anim_star_core, 12, 2),
    'dimensional_pocket': (anim_pocket, 12, 2),
}


def animated_item(iid):
    """動くアイテムの絵（コマを縦に並べた1枚）と、1コマの tick 数。どのコマにも同じ汚しを入れる。"""
    from PIL import Image
    fn, frames, ticks = ANIMATED_ITEMS[iid]
    sheet = Image.new('RGBA', (S, S * frames), (0, 0, 0, 0))
    for k in range(frames):
        sheet.paste(grain(fn(k / frames), seed=stable_hash(iid) % 1000), (0, k * S))
    return sheet, ticks


def item_texture(iid, stage):
    fn = ITEM_ART.get(iid)
    im = fn() if fn is not None else module((220, 224, 230), TIERS[stage]['glow'])
    return grain(im, seed=stable_hash(iid) % 1000)


# ---------------------------------------------------------------- ケーブル

# 断面（ケーブルの軸に直交する面の長方形の集まり、0〜16 の座標）。上位ほど凹凸のある断面になる
# 断面は中心に置いた正方形の一辺（偶数）。Java の CableProfile と同じ値
CABLE_WIDTHS = {'copper_wire': 4, 'superconducting_cable': 4, 'topological_wire': 6, 'horizon_bus': 8}


def cable_bands(iid, core, on, glow):
    """ケーブルの断面の帯（中心から外へ: 芯 → 段階ごとの帯 → 被覆の縁）。幅は CABLE_WIDTHS の半分の数。"""
    jacket = (226, 230, 234)
    c = glow if on and glow else core
    half = CABLE_WIDTHS[iid] // 2
    if iid == 'copper_wire':
        return [c, darken(c, 0.22)]                                   # 銅むき出しの1本（縁は少し酸化した色）
    if iid == 'superconducting_cable':
        return [c, darken(jacket, 0.15)]                              # 芯と白い被覆
    if iid == 'topological_wire':
        return [c, lighten(c, 0.45) if on else mix(c, jacket, 0.5), darken(jacket, 0.18)]   # 芯・リブ・被覆
    return ([c, (40, 40, 50), lighten(jacket, 0.05)][:half - 1]
            + [(220, 188, 108)])                                      # 芯・黒い層・白・金の縁


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
