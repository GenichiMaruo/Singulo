"""Singulo のテクスチャを描く（gen_data.py から呼ぶ）。32×32 で描く。

- 装置: 段階ごとに外装の意匠が変わる（1 銅の帯とねじ、2 霜と二重の継ぎ目、3 菫色の量子格子、4 真鍮の時計の輪、
  5 黒曜石と金の縁取りに星）。正面は装置ごとの絵柄の窓で、稼働中（_on）は光る。
- 恩恵の模様: 上位装置の恩恵を受けている装置に重ねる、エネルギーの流れの動くテクスチャ（boost_overlay）。
- マルチブロックの部品・圧縮ブロック・アイテムも、それぞれ形と色を描き分ける。
"""
import math
import random
import zlib

from PIL import Image, ImageDraw


def stable_hash(text):
    """実行ごとに変わらないハッシュ（Python の hash() は起動ごとに変わり、絵が毎回変わってしまう）。"""
    return zlib.crc32(text.encode("utf-8"))


S = 32  # テクスチャの大きさ

# 段階ごとの配色: 地・縁・飾り・発光
TIERS = {
    1: dict(base=(236, 238, 240), dark=(196, 202, 208), trim=(196, 124, 72), glow=(120, 210, 240)),
    2: dict(base=(228, 238, 246), dark=(178, 196, 212), trim=(140, 160, 182), glow=(150, 232, 255)),
    3: dict(base=(234, 232, 246), dark=(196, 190, 220), trim=(96, 74, 150), glow=(196, 164, 255)),
    4: dict(base=(242, 237, 226), dark=(210, 198, 176), trim=(200, 158, 78), glow=(255, 192, 96)),
    5: dict(base=(40, 40, 50), dark=(20, 20, 28), trim=(222, 192, 112), glow=(255, 255, 255)),
}


def rgba(c, a=255):
    return tuple(c[:3]) + (a,)


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


# ---------------------------------------------------------------- 外装（段階ごと）

def bevel(d, box, base, light=0.25, shade=0.25):
    x0, y0, x1, y1 = box
    d.rectangle(box, fill=rgba(base))
    d.line([(x0, y0), (x1, y0)], fill=rgba(lighten(base, light)))
    d.line([(x0, y0), (x0, y1)], fill=rgba(lighten(base, light)))
    d.line([(x0, y1), (x1, y1)], fill=rgba(darken(base, shade)))
    d.line([(x1, y0), (x1, y1)], fill=rgba(darken(base, shade)))


def screw(d, x, y, col):
    d.point((x, y), fill=rgba(darken(col, 0.35)))
    d.point((x + 1, y), fill=rgba(lighten(col, 0.3)))
    d.point((x, y + 1), fill=rgba(lighten(col, 0.1)))
    d.point((x + 1, y + 1), fill=rgba(darken(col, 0.2)))


def casing(stage, face='side', seed=0):
    """段階の外装。face: side / top / bottom。"""
    t = TIERS[stage]
    base, dark, trim, glow = t['base'], t['dark'], t['trim'], t['glow']
    im, d = new()
    rnd = random.Random(stage * 100 + seed)
    d.rectangle([0, 0, S - 1, S - 1], fill=rgba(dark))
    bevel(d, (1, 1, S - 2, S - 2), base)
    # わずかな地のむら
    for _ in range(40):
        x, y = rnd.randrange(2, S - 2), rnd.randrange(2, S - 2)
        c = im.getpixel((x, y))
        d.point((x, y), fill=rgba(darken(c, 0.04) if rnd.random() < 0.5 else lighten(c, 0.04)))
    if face == 'bottom':
        d.rectangle([4, 4, S - 5, S - 5], outline=rgba(darken(base, 0.12)))
        for x, y in ((2, 2), (S - 4, 2), (2, S - 4), (S - 4, S - 4)):
            d.rectangle([x, y, x + 1, y + 1], fill=rgba(darken(base, 0.3)))
        return im
    if face == 'top':
        # 排気の格子
        d.rectangle([7, 7, S - 8, S - 8], fill=rgba(darken(base, 0.55) if stage < 5 else (12, 12, 18)))
        for y in range(9, S - 8, 3):
            d.line([(8, y), (S - 9, y)], fill=rgba(darken(base, 0.25) if stage < 5 else (60, 60, 72)))
        d.rectangle([7, 7, S - 8, S - 8], outline=rgba(trim))
        d.line([(9, S - 10), (S - 10, S - 10)], fill=rgba(glow, 160))
    # 段階ごとの意匠
    if stage == 1:
        d.rectangle([1, 14, S - 2, 17], fill=rgba(trim))
        d.line([(1, 14), (S - 2, 14)], fill=rgba(lighten(trim, 0.35)))
        d.line([(1, 17), (S - 2, 17)], fill=rgba(darken(trim, 0.3)))
        for x, y in ((3, 3), (S - 5, 3), (3, S - 5), (S - 5, S - 5)):
            screw(d, x, y, (170, 176, 184))
        d.line([(4, 15), (S - 5, 15)], fill=rgba(glow)) if face == 'side' else None
    elif stage == 2:
        for y in (11, 20):
            d.line([(2, y), (S - 3, y)], fill=rgba(dark))
            d.line([(2, y + 1), (S - 3, y + 1)], fill=rgba(glow, 200))
        # 霜の角
        for cx, cy, sx, sy in ((2, 2, 1, 1), (S - 3, 2, -1, 1), (2, S - 3, 1, -1), (S - 3, S - 3, -1, -1)):
            for i in range(5):
                d.point((cx + sx * i, cy), fill=rgba((255, 255, 255)))
                d.point((cx, cy + sy * i), fill=rgba((255, 255, 255)))
                d.point((cx + sx * i, cy + sy * i // 2), fill=rgba((214, 240, 255)))
        for x in range(10, S - 10, 3):
            d.line([(x, 24), (x, 27)], fill=rgba(darken(base, 0.3)))
    elif stage == 3:
        d.rectangle([6, 6, S - 7, S - 7], fill=rgba(lighten(base, 0.1)), outline=rgba(trim))
        for x in range(8, S - 7, 4):
            for y in range(8, S - 7, 4):
                d.point((x, y), fill=rgba(glow))
                if (x + y) % 8 == 0:
                    d.point((x + 1, y), fill=rgba(lighten(glow, 0.4)))
        for x, y in ((2, 2), (S - 5, 2), (2, S - 5), (S - 5, S - 5)):
            d.rectangle([x, y, x + 2, y + 2], fill=rgba(trim))
            d.point((x + 1, y + 1), fill=rgba(glow))
    elif stage == 4:
        cx = cy = S // 2
        for r, col in ((11, trim), (10, lighten(trim, 0.3)), (8, darken(base, 0.06))):
            d.ellipse([cx - r, cy - r, cx + r - 1, cy + r - 1], outline=rgba(col))
        for k in range(12):
            a = k * math.pi / 6
            x0, y0 = cx + 9 * math.cos(a), cy + 9 * math.sin(a)
            x1, y1 = cx + 10.5 * math.cos(a), cy + 10.5 * math.sin(a)
            d.line([(x0, y0), (x1, y1)], fill=rgba(darken(trim, 0.2)))
        d.line([(cx, cy), (cx, cy - 6)], fill=rgba(darken(trim, 0.3)))
        d.line([(cx, cy), (cx + 4, cy + 2)], fill=rgba(darken(trim, 0.3)))
        d.line([(2, 2), (S - 3, 2)], fill=rgba(glow))
        for x, y in ((3, S - 5), (S - 5, S - 5)):
            screw(d, x, y, trim)
    elif stage == 5:
        rnd2 = random.Random(77 + seed)
        for _ in range(14):
            x, y = rnd2.randrange(3, S - 3), rnd2.randrange(3, S - 3)
            d.point((x, y), fill=rgba((255, 255, 255), rnd2.choice([120, 180, 255])))
        d.rectangle([1, 1, S - 2, S - 2], outline=rgba((236, 238, 240)))
        d.rectangle([2, 2, S - 3, S - 3], outline=rgba((70, 70, 84)))
        for cx, cy, sx, sy in ((2, 2, 1, 1), (S - 3, 2, -1, 1), (2, S - 3, 1, -1), (S - 3, S - 3, -1, -1)):
            for i in range(6):
                d.point((cx + sx * i, cy), fill=rgba(trim))
                d.point((cx, cy + sy * i), fill=rgba(trim))
        d.line([(6, S // 2), (S - 7, S // 2)], fill=rgba((255, 255, 255), 200))
    return im


# ---------------------------------------------------------------- 正面の絵柄

def window(d, stage, box, on):
    """正面の窓（暗いガラス）。"""
    t = TIERS[stage]
    x0, y0, x1, y1 = box
    d.rectangle([x0 - 1, y0 - 1, x1 + 1, y1 + 1], fill=rgba(t['trim']))
    glass = (18, 24, 32) if not on else (24, 36, 48)
    d.rectangle(box, fill=rgba(glass))
    d.line([(x0, y0), (x1, y0)], fill=rgba(lighten(glass, 0.25)))


def icon_color(stage, on, base=None):
    c = base or TIERS[stage]['glow']
    return rgba(c) if on else rgba(mix(c, (30, 40, 50), 0.6))


def draw_icon(d, kind, box, col, on):
    x0, y0, x1, y1 = box
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    w, h = x1 - x0, y1 - y0
    if kind == 'flame':
        d.polygon([(cx, y0 + 1), (cx + 5, cy + 2), (cx + 3, y1 - 1), (cx - 3, y1 - 1), (cx - 5, cy + 2)], fill=col)
        d.polygon([(cx, cy - 1), (cx + 2, cy + 3), (cx, y1 - 2), (cx - 2, cy + 3)], fill=rgba((255, 240, 180)) if on else col)
    elif kind == 'press':
        d.rectangle([x0 + 3, y0 + 1, x1 - 3, y0 + 4], fill=col)
        d.rectangle([cx - 1, y0 + 4, cx + 1, cy], fill=col)
        d.rectangle([x0 + 2, y1 - 3, x1 - 2, y1 - 1], fill=col)
        d.polygon([(cx - 3, cy + 1), (cx + 3, cy + 1), (cx, cy + 4)], fill=col)
    elif kind == 'bubbles':
        for bx, by, r in ((cx - 4, cy + 3, 2), (cx + 3, cy, 3), (cx - 1, cy - 4, 2), (cx + 5, cy + 5, 1)):
            d.ellipse([bx - r, by - r, bx + r, by + r], outline=col)
    elif kind == 'screen':
        for i, yy in enumerate(range(int(y0) + 2, int(y1) - 1, 3)):
            d.line([(x0 + 2, yy), (x0 + 2 + (w - 4) * (0.5 + 0.5 * ((i * 7) % 5) / 5), yy)], fill=col)
    elif kind == 'thermo':
        for i in range(int(w) - 3):
            t = i / max(1, w - 4)
            c = mix((255, 110, 80), (110, 190, 255), t)
            d.line([(x0 + 2 + i, y0 + 2), (x0 + 2 + i, y1 - 2)], fill=rgba(c) if on else rgba(mix(c, (30, 40, 50), 0.5)))
    elif kind == 'arm':
        d.line([(x0 + 2, y1 - 2), (cx, cy)], fill=col, width=2)
        d.line([(cx, cy), (x1 - 3, y0 + 3)], fill=col, width=2)
        d.ellipse([cx - 2, cy - 2, cx + 2, cy + 2], fill=col)
        d.line([(x1 - 5, y0 + 2), (x1 - 2, y0 + 5)], fill=col)
    elif kind == 'flask':
        d.rectangle([cx - 1, y0 + 1, cx + 1, cy - 2], outline=col)
        d.polygon([(cx - 2, cy - 2), (cx + 2, cy - 2), (cx + 6, y1 - 1), (cx - 6, y1 - 1)], outline=col)
        d.polygon([(cx - 4, cy + 3), (cx + 4, cy + 3), (cx + 5, y1 - 2), (cx - 5, y1 - 2)], fill=col)
    elif kind == 'fan':
        for k in range(4):
            a = k * math.pi / 2 + 0.4
            d.polygon([(cx, cy), (cx + 6 * math.cos(a), cy + 6 * math.sin(a)),
                       (cx + 6 * math.cos(a + 0.7), cy + 6 * math.sin(a + 0.7))], fill=col)
        d.ellipse([cx - 1, cy - 1, cx + 1, cy + 1], fill=rgba((255, 255, 255)))
    elif kind == 'dish':
        d.arc([cx - 7, cy - 4, cx + 7, cy + 10], 180, 360, fill=col)
        d.line([(cx, cy + 3), (cx, cy - 4)], fill=col)
        for k in range(3):
            d.point((cx - 4 + 4 * k, y0 + 1 + k), fill=col)
    elif kind == 'battery':
        d.rectangle([x0 + 3, y0 + 2, x1 - 3, y1 - 2], outline=col)
        for i in range(3):
            d.rectangle([x0 + 5 + i * 4, y0 + 4, x0 + 7 + i * 4, y1 - 4], fill=col)
    elif kind == 'anchor':
        d.line([(cx, y0 + 2), (cx, y1 - 2)], fill=col, width=2)
        d.line([(cx - 4, y0 + 4), (cx + 4, y0 + 4)], fill=col)
        d.arc([cx - 6, cy - 3, cx + 6, y1 - 1], 0, 180, fill=col)
    elif kind == 'link':
        d.ellipse([cx - 7, cy - 4, cx - 1, cy + 2], outline=col)
        d.ellipse([cx + 1, cy - 2, cx + 7, cy + 4], outline=col)
        d.line([(cx - 2, cy), (cx + 2, cy + 1)], fill=rgba((255, 255, 255)) if on else col)
    elif kind == 'beam':
        for sx in (x0 + 1, x1 - 1):
            d.line([(sx, y0 + 2), (cx, cy)], fill=col)
            d.line([(sx, y1 - 2), (cx, cy)], fill=col)
        d.ellipse([cx - 1, cy - 1, cx + 1, cy + 1], fill=rgba((255, 255, 255)))
    elif kind == 'atom':
        d.ellipse([cx - 7, cy - 3, cx + 7, cy + 3], outline=col)
        d.line([(cx - 5, cy - 5), (cx + 5, cy + 5)], fill=col)
        d.line([(cx - 5, cy + 5), (cx + 5, cy - 5)], fill=col)
        d.ellipse([cx - 1, cy - 1, cx + 1, cy + 1], fill=col)
    elif kind == 'wave':
        pts = [(x0 + 1 + i, cy + 4 * math.sin(i * 0.9) * (1 - abs(i - w / 2) / (w / 2 + 1))) for i in range(int(w) - 1)]
        d.line(pts, fill=col)
    elif kind == 'shield':
        d.polygon([(cx, y0 + 1), (cx + 6, y0 + 3), (cx + 5, cy + 3), (cx, y1 - 1), (cx - 5, cy + 3), (cx - 6, y0 + 3)], outline=col)
        d.line([(cx, y0 + 3), (cx, y1 - 3)], fill=col)
    elif kind == 'echo':
        for r in (2, 5, 8):
            d.arc([cx - r, cy - r, cx + r, cy + r], 300, 60, fill=col)
            d.arc([cx - r, cy - r, cx + r, cy + r], 120, 240, fill=col)
    elif kind == 'crystal':
        d.polygon([(cx, y0 + 1), (cx + 4, cy - 2), (cx + 4, cy + 3), (cx, y1 - 1), (cx - 4, cy + 3), (cx - 4, cy - 2)], fill=col)
        d.line([(cx, y0 + 1), (cx, y1 - 1)], fill=rgba((255, 255, 255), 180))
    elif kind == 'probe':
        d.ellipse([cx - 3, cy - 3, cx + 3, cy + 3], outline=col)
        d.line([(cx - 7, cy), (cx - 3, cy)], fill=col)
        d.line([(cx + 3, cy), (cx + 7, cy)], fill=col)
        d.point((cx, cy), fill=rgba((255, 255, 255)))
    elif kind == 'sphere':
        d.ellipse([cx - 5, cy - 5, cx + 5, cy + 5], fill=rgba((0, 0, 0)), outline=col)
        d.rectangle([cx - 7, cy - 7, cx + 7, cy + 7], outline=col)
    elif kind == 'halo':
        d.ellipse([cx - 7, cy - 3, cx + 7, cy + 3], outline=col)
        d.ellipse([cx - 2, cy - 2, cx + 2, cy + 2], fill=rgba((0, 0, 0)), outline=col)
    elif kind == 'tank':
        d.rectangle([cx - 5, y0 + 1, cx + 5, y1 - 1], outline=col)
        d.rectangle([cx - 4, cy - 1, cx + 4, y1 - 2], fill=rgba((60, 40, 90)))
        for yy in (y0 + 4, cy - 3):
            d.line([(cx - 6, yy), (cx + 6, yy)], fill=col)
    elif kind == 'dome':
        d.arc([cx - 7, cy - 5, cx + 7, cy + 9], 180, 360, fill=col)
        d.line([(cx - 7, cy + 2), (cx + 7, cy + 2)], fill=col)
        d.line([(cx, cy + 2), (cx, y1 - 1)], fill=col)
    elif kind == 'cylinder':
        d.rectangle([cx - 3, y0 + 1, cx + 3, y1 - 1], outline=col)
        for yy in range(int(y0) + 3, int(y1) - 1, 3):
            d.line([(cx - 2, yy), (cx + 2, yy - 1)], fill=col)
    elif kind == 'swirl':
        for k in range(30):
            a = k * 0.45
            r = 1 + k * 0.22
            d.point((cx + r * math.cos(a), cy + r * math.sin(a)), fill=col)
        d.ellipse([cx - 1, cy - 1, cx + 1, cy + 1], fill=rgba((0, 0, 0)))
    elif kind == 'port':
        d.ellipse([cx - 6, cy - 6, cx + 6, cy + 6], outline=col)
        d.polygon([(cx - 3, cy - 2), (cx + 2, cy), (cx - 3, cy + 2)], fill=col)
    elif kind == 'snow':
        for k in range(3):
            a = k * math.pi / 3
            d.line([(cx - 6 * math.cos(a), cy - 6 * math.sin(a)), (cx + 6 * math.cos(a), cy + 6 * math.sin(a))], fill=col)
    elif kind == 'ring':
        d.rectangle([cx - 6, cy - 6, cx + 6, cy + 6], outline=col)
        d.rectangle([cx - 4, cy - 4, cx + 4, cy + 4], outline=col)
        d.point((cx - 6, cy - 6), fill=rgba((255, 255, 255)))
    elif kind == 'inward':
        for sx, sy in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            d.line([(cx + sx * 7, cy + sy * 7), (cx + sx * 2, cy + sy * 2)], fill=col)
            d.point((cx + sx * 3 + sy, cy + sy * 3 + sx), fill=col)
            d.point((cx + sx * 3 - sy, cy + sy * 3 - sx), fill=col)
    elif kind == 'mirrors':
        d.rectangle([x0 + 2, y0 + 2, x1 - 2, y0 + 3], fill=col)
        d.rectangle([x0 + 2, y1 - 3, x1 - 2, y1 - 2], fill=col)
        for xx in range(int(x0) + 4, int(x1) - 3, 3):
            d.point((xx, cy), fill=rgba((255, 255, 255), 200))
    elif kind == 'piston':
        d.rectangle([x0 + 2, y0 + 1, x1 - 2, y0 + 4], fill=col)
        d.rectangle([x0 + 2, y1 - 4, x1 - 2, y1 - 1], fill=col)
        d.rectangle([cx - 3, cy - 2, cx + 3, cy + 2], fill=rgba((255, 200, 120)) if on else col)
    elif kind == 'blackhole':
        d.ellipse([cx - 8, cy - 3, cx + 8, cy + 3], outline=rgba((255, 200, 140)) if on else col)
        d.ellipse([cx - 4, cy - 4, cx + 4, cy + 4], fill=rgba((0, 0, 0)))
        d.arc([cx - 5, cy - 5, cx + 5, cy + 5], 200, 340, fill=col)
    else:
        d.rectangle([cx - 4, cy - 2, cx + 4, cy + 2], fill=col)


# 装置ID → (絵柄, 発光色)
MACHINE_ICONS = {
    'kiln': ('flame', (255, 150, 80)), 'compressor': ('press', None), 'electrolyzer': ('bubbles', (120, 200, 255)),
    'archive_terminal': ('screen', (140, 240, 220)), 'thermoelectric_generator': ('thermo', None),
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
    'degenerate_furnace_controller': ('piston', (255, 180, 90)), 'core_controller': ('blackhole', None),
}


def machine_front(iid, stage, on):
    if iid == 'creative_energy_source':
        import art16
        return art16.creative_front(on).resize((S, S), Image.NEAREST)
    if iid == 'degenerate_furnace_controller':
        import art16
        return art16.furnace_front(on).resize((S, S), Image.NEAREST)
    import art16 as _a16
    if iid in _a16.CONTROLLER_FRONTS:
        return _a16.controller_front(iid, on).resize((S, S), Image.NEAREST)
    im = casing(stage, 'side', seed=stable_hash(iid) % 1000)
    d = ImageDraw.Draw(im)
    box = (7, 6, S - 8, S - 10)
    window(d, stage, box, on)
    kind, color = MACHINE_ICONS.get(iid, ('generic', None))
    draw_icon(d, kind, box, icon_color(stage, on, color), on)
    # 状態ランプ
    t = TIERS[stage]
    lamp = t['glow'] if on else mix(t['glow'], (40, 50, 60), 0.7)
    d.rectangle([12, S - 6, S - 13, S - 5], fill=rgba(lamp))
    if on:
        d.line([(12, S - 7), (S - 13, S - 7)], fill=rgba(lighten(lamp, 0.5), 140))
    return im


def boost_overlay(frames=8):
    """恩恵の模様: 外装の縁と回路をなぞって流れる光（動くテクスチャ、縦に frames 枚）。"""
    sheet = Image.new('RGBA', (S, S * frames), (0, 0, 0, 0))
    # 回路の道筋（外周と、中央へ向かう枝）
    path = [(1, y) for y in range(1, S - 1)] + [(x, S - 2) for x in range(1, S - 1)] + \
           [(S - 2, y) for y in range(S - 2, 0, -1)] + [(x, 1) for x in range(S - 2, 0, -1)]
    branches = [[(x, 9) for x in range(1, 12)] + [(11, y) for y in range(9, 16)],
                [(x, S - 10) for x in range(S - 2, S - 13, -1)] + [(S - 12, y) for y in range(S - 10, 15, -1)]]
    for f in range(frames):
        im = Image.new('RGBA', (S, S), (0, 0, 0, 0))
        d = ImageDraw.Draw(im)
        for p in path:
            d.point(p, fill=(150, 235, 255, 70))
        for br in branches:
            for p in br:
                d.point(p, fill=(150, 235, 255, 55))
        n = len(path)
        for k in range(4):
            head = (f * n // frames + k * n // 4) % n
            for tail in range(8):
                x, y = path[(head - tail) % n]
                a = int(255 * (1 - tail / 8))
                d.point((x, y), fill=(255, 255, 255, a) if tail < 2 else (140, 230, 255, a))
        for br in branches:
            m = len(br)
            head = f * m // frames
            for tail in range(5):
                if 0 <= head - tail < m:
                    d.point(br[head - tail], fill=(220, 250, 255, int(230 * (1 - tail / 5))))
        sheet.paste(im, (0, S * f))
    return sheet


# ---------------------------------------------------------------- マルチブロックの部品

UPSCALED_FROM_16 = ('degenerate_furnace_frame', 'degenerate_furnace_shell', 'degenerate_furnace_piston', 'degenerate_furnace_fin',
                    'degenerate_furnace_tube', 'degenerate_furnace_window', 'multiblock_port')


def part_texture(iid):
    import art16 as _a16
    if iid in UPSCALED_FROM_16 or _a16.multiblock_part(iid) is not None:
        import art16
        return art16.part_texture(iid).resize((S, S), Image.NEAREST)
    im, d = new()
    if iid == 'heat_exchange_core':
        d.rectangle([0, 0, S - 1, S - 1], fill=(120, 76, 50, 255))
        for x in range(2, S - 1, 4):
            d.rectangle([x, 2, x + 1, S - 3], fill=(210, 140, 90, 255))
            d.line([(x, 2), (x, S - 3)], fill=(240, 180, 130, 255))
        d.rectangle([0, 14, S - 1, 17], fill=(150, 230, 255, 255))
    elif iid == 'accelerator_tube':
        d.rectangle([0, 0, S - 1, S - 1], fill=(206, 212, 220, 255))
        d.rectangle([0, 9, S - 1, S - 10], fill=(150, 200, 230, 120))
        d.line([(0, 15), (S - 1, 15)], fill=(255, 255, 255, 255))
        d.line([(0, 16), (S - 1, 16)], fill=(150, 232, 255, 255))
        for x in (3, S - 4):
            d.rectangle([x - 1, 0, x + 1, S - 1], fill=(160, 170, 182, 255))
    elif iid == 'focusing_magnet':
        d.rectangle([0, 0, S - 1, S - 1], fill=(200, 206, 214, 255))
        d.rectangle([3, 3, 14, S - 4], fill=(210, 70, 70, 255))
        d.rectangle([17, 3, S - 4, S - 4], fill=(70, 110, 210, 255))
        d.line([(15, 2), (16, S - 3)], fill=(255, 255, 255, 255))
        for y in range(6, S - 5, 4):
            d.line([(4, y), (13, y)], fill=(240, 120, 120, 255))
            d.line([(18, y), (S - 5, y)], fill=(120, 160, 240, 255))
    elif iid == 'mirror_plate':
        for y in range(S):
            for x in range(S):
                t = ((x + y) % 32) / 32
                c = mix((200, 214, 230), (250, 252, 255), abs(0.5 - t) * 2)
                d.point((x, y), fill=rgba(c))
        d.rectangle([0, 0, S - 1, S - 1], outline=(140, 150, 164, 255))
        d.line([(4, 24), (24, 4)], fill=(255, 255, 255, 255))
    elif iid == 'degenerate_furnace_piston':
        d.rectangle([0, 0, S - 1, S - 1], fill=(70, 72, 82, 255))
        d.rectangle([6, 6, S - 7, S - 7], fill=(150, 156, 164, 255), outline=(220, 224, 230, 255))
        d.rectangle([12, 12, S - 13, S - 13], fill=(255, 170, 80, 255))
        for k in range(4):
            d.line([(2 + k * 8, 2), (2 + k * 8 + 3, 2)], fill=(255, 192, 96, 255))
    elif iid == 'reactor_shell':
        d.rectangle([0, 0, S - 1, S - 1], fill=(22, 22, 30, 255))
        for row in range(0, S + 8, 8):
            for col in range(0, S + 8, 9):
                ox = col + (4 if (row // 8) % 2 else 0)
                d.polygon([(ox, row + 1), (ox + 4, row - 1), (ox + 8, row + 1), (ox + 8, row + 5), (ox + 4, row + 7), (ox, row + 5)],
                          outline=(70, 72, 90, 255))
        d.rectangle([0, 0, S - 1, S - 1], outline=(236, 238, 240, 255))
    elif iid == 'gyro_drive':
        d.rectangle([0, 0, S - 1, S - 1], fill=(30, 30, 40, 255), outline=(236, 238, 240, 255))
        d.ellipse([4, 4, S - 5, S - 5], outline=(222, 192, 112, 255))
        d.ellipse([4, 10, S - 5, S - 11], outline=(255, 255, 255, 255))
        d.ellipse([10, 4, S - 11, S - 5], outline=(150, 232, 255, 255))
        d.ellipse([14, 14, S - 15, S - 15], fill=(255, 255, 255, 255))
    elif iid == 'extraction_port':
        d.rectangle([0, 0, S - 1, S - 1], fill=(30, 30, 40, 255), outline=(236, 238, 240, 255))
        d.rectangle([7, 7, S - 8, S - 8], fill=(12, 12, 18, 255), outline=(222, 192, 112, 255))
        for k in range(4):
            a = k * math.pi / 2 + math.pi / 4
            d.line([(16 + 6 * math.cos(a), 16 + 6 * math.sin(a)), (16 + 13 * math.cos(a), 16 + 13 * math.sin(a))],
                   fill=(255, 255, 255, 255))
    elif iid == 'strangelet':
        rnd = random.Random(5)
        d.rectangle([0, 0, S - 1, S - 1], fill=(30, 10, 40, 255))
        for _ in range(140):
            x, y = rnd.randrange(S), rnd.randrange(S)
            d.point((x, y), fill=rgba(rnd.choice([(200, 120, 255), (120, 60, 180), (255, 200, 255)])))
        d.ellipse([10, 10, S - 11, S - 11], fill=(255, 230, 255, 255))
    else:
        im = casing(1, 'side')
    return im


def strange_matter():
    im, d = new()
    rnd = random.Random(9)
    d.rectangle([0, 0, S - 1, S - 1], fill=(52, 30, 70, 255))
    for _ in range(18):
        x, y = rnd.randrange(S), rnd.randrange(S)
        r = rnd.randrange(2, 5)
        c = rnd.choice([(90, 50, 120), (120, 70, 160), (70, 40, 96)])
        d.polygon([(x, y - r), (x + r, y), (x, y + r), (x - r, y)], fill=rgba(c))
        d.point((x, y - r + 1), fill=(220, 180, 255, 255))
    return im


def compressed_block(level, metal):
    im, d = new()
    base = (126, 130, 138) if not metal else (156, 144, 120)
    k = 1 - 0.2 * (level - 1)
    b = tuple(int(c * k) for c in base)
    d.rectangle([0, 0, S - 1, S - 1], fill=rgba(b))
    step = {1: 16, 2: 8, 3: 4}.get(level, 16)
    seam = (200, 230, 255) if level >= 2 else lighten(b, 0.3)
    for p in range(0, S, step):
        d.line([(p, 0), (p, S - 1)], fill=rgba(darken(b, 0.25)))
        d.line([(0, p), (S - 1, p)], fill=rgba(darken(b, 0.25)))
    for p in range(step, S, step):
        for q in range(step, S, step):
            d.point((p, q), fill=rgba(seam))
    d.rectangle([0, 0, S - 1, S - 1], outline=rgba((236, 238, 240) if level < 3 else (150, 232, 255)))
    if metal:
        d.line([(2, 2), (S // 2, 2)], fill=rgba(lighten(b, 0.45)))
    return im


# ---------------------------------------------------------------- アイテム

OUT = (26, 30, 38, 255)


def outlined(draw_fn, size=S):
    """形を描き、外側に1ドットの暗い縁を付ける。"""
    im, d = new(size)
    draw_fn(d)
    px = im.load()
    out = im.copy()
    po = out.load()
    for y in range(size):
        for x in range(size):
            if px[x, y][3] == 0:
                cols = []
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < size and 0 <= ny < size and px[nx, ny][3] > 128:
                        cols.append(px[nx, ny][:3])
                if cols:
                    # 輪郭は真っ黒にせず、内側の色をぐっと暗くした色
                    avg = tuple(sum(c[i] for c in cols) // len(cols) for i in range(3))
                    po[x, y] = rgba(mix(darken(avg, 0.62), (40, 44, 60), 0.25))
    return out


def item_ingot(color):
    def f(d):
        top, side, front = lighten(color, 0.3), darken(color, 0.25), color
        d.polygon([(5, 18), (12, 11), (27, 11), (20, 18)], fill=rgba(top))
        d.rectangle([5, 18, 20, 24], fill=rgba(front))
        d.polygon([(20, 18), (27, 11), (27, 17), (20, 24)], fill=rgba(side))
        d.line([(7, 19), (17, 19)], fill=rgba(lighten(color, 0.5)))
    return outlined(f)


def item_plate(color, lines=None, accent=None):
    def f(d):
        d.polygon([(4, 12), (20, 6), (28, 12), (12, 18)], fill=rgba(lighten(color, 0.2)))
        d.polygon([(4, 12), (12, 18), (12, 21), (4, 15)], fill=rgba(darken(color, 0.2)))
        d.polygon([(12, 18), (28, 12), (28, 15), (12, 21)], fill=rgba(darken(color, 0.1)))
        if lines:
            for k in range(lines):
                d.line([(8 + k * 3, 12 + k), (16 + k * 3, 9 + k)], fill=rgba(accent or (120, 210, 240)))
    return outlined(f)


def item_circuit(board, chip, trace):
    def f(d):
        d.rectangle([4, 6, 27, 25], fill=rgba(board))
        d.rectangle([4, 6, 27, 25], outline=rgba(darken(board, 0.3)))
        for y in (10, 14, 18, 22):
            d.line([(6, y), (11, y)], fill=rgba(trace))
            d.line([(20, y), (25, y)], fill=rgba(trace))
        d.rectangle([11, 10, 20, 21], fill=rgba(chip))
        d.rectangle([13, 12, 18, 19], outline=rgba(lighten(chip, 0.35)))
        d.point((15, 15), fill=rgba(trace))
    return outlined(f)


def item_coil(wire, core):
    def f(d):
        d.rectangle([9, 6, 22, 25], fill=rgba(core))
        for y in range(7, 25, 2):
            d.line([(7, y), (24, y)], fill=rgba(wire))
            d.point((7, y), fill=rgba(darken(wire, 0.3)))
            d.point((24, y), fill=rgba(darken(wire, 0.3)))
        d.rectangle([6, 4, 25, 6], fill=rgba(darken(core, 0.2)))
        d.rectangle([6, 25, 25, 27], fill=rgba(darken(core, 0.2)))
    return outlined(f)


def item_spool(wire):
    def f(d):
        d.ellipse([6, 6, 25, 25], fill=rgba(wire))
        for r in (8, 5):
            d.ellipse([16 - r, 16 - r, 15 + r, 15 + r], outline=rgba(lighten(wire, 0.35)))
        d.ellipse([13, 13, 18, 18], fill=rgba((40, 44, 52)))
    return outlined(f)


def item_frame(color, trim):
    def f(d):
        d.rectangle([5, 5, 26, 26], fill=rgba(color))
        d.rectangle([10, 10, 21, 21], fill=(0, 0, 0, 0))
        d.line([(5, 5), (26, 26)], fill=rgba(trim))
        d.line([(26, 5), (5, 26)], fill=rgba(trim))
        d.rectangle([10, 10, 21, 21], fill=(0, 0, 0, 0))
        for x, y in ((6, 6), (24, 6), (6, 24), (24, 24)):
            d.rectangle([x, y, x + 1, y + 1], fill=rgba(darken(color, 0.4)))
    return outlined(f)


def item_module(body, lamp):
    def f(d):
        d.rectangle([5, 8, 26, 24], fill=rgba(body))
        d.line([(5, 8), (26, 8)], fill=rgba(lighten(body, 0.3)))
        d.rectangle([8, 11, 23, 17], fill=rgba((30, 38, 48)))
        for x in range(9, 23, 4):
            d.rectangle([x, 13, x + 1, 15], fill=rgba(lamp))
        d.rectangle([9, 20, 22, 21], fill=rgba(lamp))
    return outlined(f)


def item_card(stripe, blank=False):
    def f(d):
        d.rectangle([4, 9, 27, 23], fill=(236, 238, 240, 255))
        d.rectangle([4, 9, 27, 12], fill=rgba(stripe))
        if not blank:
            d.rectangle([7, 15, 13, 20], fill=rgba((214, 180, 90)))
            for y in (15, 18):
                d.line([(16, y), (25, y)], fill=rgba(darken(stripe, 0.2)))
    return outlined(f)


def item_gem(color, facets=True):
    def f(d):
        d.polygon([(16, 3), (25, 11), (22, 27), (10, 27), (7, 11)], fill=rgba(color))
        if facets:
            d.polygon([(16, 3), (25, 11), (16, 13), (7, 11)], fill=rgba(lighten(color, 0.35)))
            d.line([(16, 13), (16, 27)], fill=rgba(darken(color, 0.2)))
            d.line([(16, 13), (10, 27)], fill=rgba(darken(color, 0.1)))
            d.point((12, 8), fill=(255, 255, 255, 255))
    return outlined(f)


def item_orb(color, ring=None, core=None, spent=False):
    def f(d):
        c = mix(color, (110, 110, 118), 0.75) if spent else color
        d.ellipse([7, 7, 24, 24], fill=rgba(c))
        d.ellipse([10, 9, 17, 15], fill=rgba(lighten(c, 0.4)))
        if core:
            d.ellipse([13, 13, 18, 18], fill=rgba(core))
        if ring:
            rc = mix(ring, (120, 120, 128), 0.75) if spent else ring
            d.arc([2, 12, 29, 20], 0, 360, fill=rgba(rc))
        if spent:
            d.line([(10, 20), (15, 15), (18, 19), (22, 13)], fill=rgba((60, 60, 66)))
    return outlined(f)


def item_vial(liquid, glow=False):
    def f(d):
        d.rectangle([12, 4, 19, 7], fill=(150, 156, 164, 255))
        d.polygon([(12, 8), (19, 8), (24, 26), (7, 26)], fill=(220, 236, 246, 120))
        d.polygon([(11, 15), (20, 15), (23, 25), (8, 25)], fill=rgba(liquid))
        if glow:
            d.point((14, 19), fill=(255, 255, 255, 255))
            d.point((17, 22), fill=(255, 255, 255, 255))
    return outlined(f)


def item_cloud(dot, n=22, seed=1):
    def f(d):
        rnd = random.Random(seed)
        d.ellipse([6, 8, 25, 23], fill=rgba(dot, 70))
        for _ in range(n):
            a, r = rnd.random() * 6.28, rnd.random() * 8
            x, y = 16 + r * math.cos(a), 16 + r * math.sin(a) * 0.8
            d.point((x, y), fill=rgba(lighten(dot, rnd.random() * 0.5)))
    return outlined(f)


def item_sphere_dark(ring, glow=None, white_seams=False):
    def f(d):
        d.ellipse([7, 7, 24, 24], fill=(10, 10, 14, 255))
        if white_seams:
            d.arc([7, 7, 24, 24], 200, 340, fill=(236, 238, 240, 255))
            d.line([(16, 7), (16, 24)], fill=(80, 80, 96, 255))
        if ring:
            d.ellipse([3, 13, 28, 19], outline=rgba(ring))
            d.rectangle([9, 13, 22, 15], fill=(10, 10, 14, 255))
        if glow:
            d.point((12, 11), fill=rgba(glow))
    return outlined(f)


def item_star(core, rays):
    def f(d):
        for k in range(8):
            a = k * math.pi / 4
            r = 13 if k % 2 == 0 else 9
            d.line([(16, 16), (16 + r * math.cos(a), 16 + r * math.sin(a))], fill=rgba(rays), width=2)
        d.ellipse([10, 10, 21, 21], fill=rgba(core))
        d.ellipse([13, 12, 17, 16], fill=(255, 255, 255, 255))
    return outlined(f)


def item_blob(color, swirl):
    def f(d):
        d.polygon([(16, 4), (25, 9), (27, 19), (20, 27), (10, 26), (5, 17), (8, 8)], fill=rgba(color))
        for k in range(20):
            a = k * 0.5
            r = 1 + k * 0.45
            d.point((16 + r * math.cos(a), 16 + r * math.sin(a)), fill=rgba(swirl))
    return outlined(f)


def item_torus(color, accent):
    def f(d):
        d.ellipse([3, 9, 28, 23], fill=rgba(color))
        d.ellipse([10, 13, 21, 19], fill=(0, 0, 0, 0))
        d.arc([3, 9, 28, 23], 190, 350, fill=rgba(accent))
    return outlined(f)


def item_book(cover, band):
    def f(d):
        d.rectangle([7, 4, 25, 27], fill=rgba(cover))
        d.rectangle([7, 4, 9, 27], fill=rgba(darken(cover, 0.3)))
        d.rectangle([10, 24, 25, 26], fill=(236, 238, 240, 255))
        d.rectangle([12, 9, 22, 11], fill=rgba(band))
        d.ellipse([14, 14, 20, 20], outline=rgba(band))
        d.point((17, 17), fill=rgba(band))
    return outlined(f)


def item_device(body, screen, beam=None):
    def f(d):
        d.rectangle([9, 10, 22, 27], fill=rgba(body))
        d.rectangle([11, 13, 20, 20], fill=rgba(screen))
        d.rectangle([12, 22, 14, 24], fill=rgba(darken(body, 0.3)))
        d.rectangle([17, 22, 19, 24], fill=rgba(darken(body, 0.3)))
        if beam:
            d.polygon([(13, 10), (18, 10), (24, 2), (7, 2)], fill=rgba(beam, 110))
    return outlined(f)


def item_glove(body, orb=None, accent=(120, 210, 240)):
    def f(d):
        d.rectangle([8, 16, 21, 28], fill=rgba(body))
        for i, x in enumerate((8, 12, 16, 20)):
            d.rectangle([x, 7 + abs(i - 1.5) * 2, x + 3, 17], fill=rgba(body))
        d.rectangle([21, 15, 25, 21], fill=rgba(body))
        d.line([(8, 22), (21, 22)], fill=rgba(accent))
        if orb:
            d.ellipse([11, 2, 20, 11], fill=rgba(orb))
            d.arc([9, 0, 22, 13], 0, 360, fill=rgba(accent))
    return outlined(f)


def item_scroll(paper, ink, torn=False):
    def f(d):
        if torn:
            d.polygon([(6, 6), (24, 4), (27, 12), (23, 27), (8, 26), (5, 17)], fill=rgba(paper))
        else:
            d.rectangle([7, 5, 25, 27], fill=rgba(paper))
            d.rectangle([5, 4, 27, 6], fill=rgba(darken(paper, 0.3)))
            d.rectangle([5, 26, 27, 28], fill=rgba(darken(paper, 0.3)))
        for y in range(10, 24, 3):
            d.line([(10, y), (21 - (y % 5), y)], fill=rgba(ink))
    return outlined(f)


def item_tablet(glow):
    def f(d):
        d.rectangle([6, 5, 25, 27], fill=(40, 46, 56, 255))
        d.rectangle([8, 7, 23, 25], fill=rgba(darken(glow, 0.6)))
        for y in range(9, 24, 3):
            d.line([(10, y), (21 - (y % 4), y)], fill=rgba(glow))
        d.rectangle([6, 5, 25, 27], outline=(236, 238, 240, 255))
    return outlined(f)


def item_pellet(color):
    def f(d):
        d.ellipse([9, 9, 22, 22], fill=rgba(color))
        d.ellipse([11, 11, 16, 15], fill=rgba(lighten(color, 0.3)))
        d.arc([9, 9, 22, 22], 30, 150, fill=rgba(darken(color, 0.3)))
    return outlined(f)


def item_tracks(color):
    def f(d):
        for k, (a, r) in enumerate(((0.3, 13), (1.4, 12), (2.6, 14), (3.9, 11), (5.1, 13))):
            pts = [(16 + t * math.cos(a + t * 0.05), 16 + t * math.sin(a + t * 0.05)) for t in range(r)]
            d.line(pts, fill=rgba(lighten(color, k * 0.1)))
        d.ellipse([13, 13, 18, 18], fill=(255, 255, 255, 255))
    return outlined(f)


def item_magnet(north_only=True):
    def f(d):
        d.rectangle([9, 5, 22, 26], fill=(210, 70, 70, 255))
        d.rectangle([12, 8, 19, 23], fill=(240, 120, 120, 255))
        d.rectangle([14, 11, 17, 20], fill=(255, 255, 255, 255))
    return outlined(f)


def item_bottle():
    def f(d):
        d.rectangle([12, 3, 19, 6], fill=(150, 156, 164, 255))
        d.ellipse([7, 7, 24, 27], fill=(220, 236, 246, 110))
        for y in (11, 17, 23):
            d.line([(7, y), (24, y)], fill=(196, 124, 72, 255))
        d.ellipse([13, 14, 18, 19], fill=(200, 120, 255, 200))
    return outlined(f)


def item_unit(body, lamp, broken=False):
    def f(d):
        d.rectangle([6, 7, 25, 25], fill=rgba(body))
        d.rectangle([9, 10, 22, 17], fill=(30, 38, 48, 255))
        d.rectangle([11, 12, 20, 15], fill=rgba(lamp))
        for x in (9, 14, 19):
            d.rectangle([x, 20, x + 2, 22], fill=rgba(darken(body, 0.3)))
        if broken:
            d.line([(7, 9), (14, 15), (12, 20), (18, 24)], fill=(40, 40, 46, 255))
            d.point((22, 9), fill=(120, 90, 60, 255))
    return outlined(f)


def item_seed(color, ring=None):
    def f(d):
        d.polygon([(16, 5), (22, 13), (20, 24), (12, 24), (10, 13)], fill=rgba(color))
        d.line([(16, 7), (16, 22)], fill=rgba(lighten(color, 0.3)))
        if ring:
            d.ellipse([5, 13, 26, 19], outline=rgba(ring))
    return outlined(f)


def item_belt(body, core):
    def f(d):
        d.rectangle([3, 13, 28, 19], fill=rgba(body))
        d.ellipse([10, 8, 21, 24], fill=rgba(darken(body, 0.2)))
        d.ellipse([13, 11, 18, 21], fill=rgba(core))
        d.line([(3, 14), (28, 14)], fill=rgba(lighten(body, 0.3)))
    return outlined(f)


def item_wire_icon(core):
    def f(d):
        d.line([(4, 27), (27, 4)], fill=(236, 238, 240, 255), width=5)
        d.line([(4, 27), (27, 4)], fill=rgba(core), width=2)
    return outlined(f)


T1, T2, T3, T4, T5 = (TIERS[i]['glow'] for i in range(1, 6))
COPPER = (214, 130, 80)
STEEL = (170, 176, 186)

ITEM_ART = {
    'steel_blend': lambda: item_pellet((90, 90, 100)),
    'steel_ingot': lambda: item_ingot(STEEL),
    'unfired_ceramic': lambda: item_plate((190, 178, 164)),
    'white_ceramic_composite': lambda: item_plate((236, 238, 240)),
    'basic_circuit': lambda: item_circuit((60, 120, 90), (40, 44, 52), COPPER),
    'handbook': lambda: item_book((40, 90, 130), T1),
    'holo_projector': lambda: item_device((220, 224, 230), (40, 70, 90), T1),
    'thermocouple_module': lambda: item_module((220, 224, 230), (255, 130, 90)),
    'basic_frame': lambda: item_frame(STEEL, (236, 238, 240)),
    'steel_plate': lambda: item_plate(STEEL),
    'ceramic_substrate': lambda: item_plate((236, 238, 240), 3, COPPER),
    'blank_data_card': lambda: item_card((200, 206, 214), blank=True),
    'data_card_observation_log': lambda: item_card(T1),
    'data_card_quantum_fragment': lambda: item_card(T3),
    'data_card_culture_data': lambda: item_card(T4),
    'mass_pellet': lambda: item_pellet((80, 84, 96)),
    'superconducting_coil': lambda: item_coil(COPPER, (200, 230, 250)),
    'superconducting_wire': lambda: item_spool((150, 210, 240)),
    'muon_bundle': lambda: item_tracks(T2),
    'muon_catalyst': lambda: item_orb((80, 150, 230), ring=T2),
    'monopole_upgrade': lambda: item_circuit((60, 50, 90), (200, 70, 70), (196, 164, 255)),
    'quantum_computing_module': lambda: item_circuit((70, 56, 110), (30, 24, 50), T3),
    'entangled_element': lambda: outlined(lambda d: (d.ellipse([4, 9, 15, 20], fill=rgba(T3)),
                                                    d.ellipse([17, 12, 28, 23], fill=rgba(lighten(T3, 0.2))),
                                                    d.line([(14, 14), (18, 17)], fill=(255, 255, 255, 255)))),
    'cold_atoms': lambda: item_cloud((150, 220, 255), seed=3),
    'optical_lattice_substrate': lambda: item_plate((210, 200, 240), 4, T3),
    'bose_condensate_catalyst': lambda: item_orb((120, 230, 240), ring=(200, 160, 255), core=(255, 255, 255)),
    'neutrino_scanner': lambda: item_device((60, 54, 90), (180, 140, 255)),
    'inertial_control_gauntlet': lambda: item_glove((220, 224, 230)),
    'degenerate_matter_shell': lambda: item_sphere_dark(None, white_seams=True),
    'time_crystal_catalyst': lambda: item_gem((255, 190, 90)),
    'exotic_matter': lambda: item_blob((60, 20, 80), (220, 160, 255)),
    'toroidal_magnetic_coil': lambda: item_torus(COPPER, T2),
    'magnetic_bottle': item_bottle,
    'gravity_field_stabilizer': lambda: item_module((40, 40, 50), (255, 255, 255)),
    'hawking_collector': lambda: item_module((30, 30, 40), (255, 140, 80)),
    'jet_collector': lambda: item_module((30, 30, 40), (120, 220, 255)),
    'ergosphere_ring': lambda: item_torus((30, 30, 40), (222, 192, 112)),
    'jet_condensate': lambda: item_vial((120, 220, 255), glow=True),
    'artificial_star_core': lambda: item_star((255, 220, 120), (255, 180, 80)),
    'hawking_condensate': lambda: item_vial((255, 140, 80), glow=True),
    'singularity_seed': lambda: item_seed((20, 20, 28), ring=(255, 255, 255)),
    'singularity_core': lambda: item_sphere_dark((255, 230, 180), glow=(255, 255, 255)),
    'metric_drive': lambda: item_belt((40, 40, 50), (200, 160, 255)),
    'graviton_manipulator': lambda: item_glove((40, 40, 50), orb=(10, 10, 14), accent=(222, 192, 112)),
    'unstable_wormhole_mouth': lambda: item_blob((30, 10, 40), (255, 120, 220)),
    'observation_log': lambda: item_scroll((230, 222, 200), (90, 110, 130)),
    'degraded_control_unit': lambda: item_unit((180, 170, 150), (90, 60, 50), broken=True),
    'control_unit': lambda: item_unit((236, 238, 240), T1),
    'quantum_data_fragment': lambda: item_scroll((220, 214, 240), T3, torn=True),
    'degraded_cold_atom_trap': lambda: item_unit((170, 176, 186), (60, 70, 90), broken=True),
    'cold_atom_trap': lambda: item_unit((210, 230, 246), (150, 220, 255)),
    'degraded_time_crystal_seed': lambda: item_seed((150, 120, 90)),
    'time_crystal_seed': lambda: item_seed((255, 190, 90)),
    'culture_data': lambda: item_scroll((236, 226, 200), T4),
    'degraded_anomaly_sample': lambda: item_vial((90, 80, 100)),
    'anomaly_sample': lambda: item_vial((60, 20, 80), glow=True),
    'dormant_singularity_seed': lambda: item_seed((90, 90, 100), ring=(150, 150, 160)),
    'spent_muon_catalyst': lambda: item_orb((80, 150, 230), ring=T2, spent=True),
    'spent_bose_condensate_catalyst': lambda: item_orb((120, 230, 240), ring=(200, 160, 255), spent=True),
    'spent_time_crystal_catalyst': lambda: outlined(lambda d: (d.polygon([(16, 3), (25, 11), (22, 27), (10, 27), (7, 11)],
                                                                         fill=(130, 118, 100, 255)),
                                                              d.line([(10, 10), (16, 18), (13, 26)], fill=(60, 56, 50, 255)))),
    'spent_singularity_core': lambda: item_sphere_dark((120, 120, 128)),
    'magnetic_monopole': lambda: item_magnet(),
    'record_fragment': lambda: item_scroll((200, 196, 186), (70, 80, 90), torn=True),
    'decoded_record': lambda: item_tablet(T1),
}


def item_texture(iid, stage):
    fn = ITEM_ART.get(iid)
    if fn is not None:
        return fn()
    # 決まっていないものは段階の色の部品
    return item_module((220, 224, 230), TIERS[stage]['glow'])


def cable_item(core):
    return item_wire_icon(core)
