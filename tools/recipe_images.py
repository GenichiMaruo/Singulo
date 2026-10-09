"""全レシピを画像（docs/recipes/*.png）と一覧（docs/recipes/README.md）に書き出す。gen_data.py の最後に呼ばれる。

レシピの種類ごとに、ゲームの画面に近い形で描く:
- 作業台: 3×3 の枠（形のあるレシピはその並び、不定形は左上から詰める）→ 完成品
- 装置: 材料（アイテムと液体のタンク）→ 装置の正面の絵・時間・電力 → 完成品
- かまど・溶鉱炉: 材料 → 炉 → 完成品
- マルチブロック組み立て: 部品と個数の表 → 形の名前
アイテムの絵は生成したテクスチャ（mod）と、ビルドで用意されるマイクラ本体の資源（バニラ）から取る。
"""
import io
import zipfile
from fractions import Fraction as F
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

from ids import MOD, OUTPUT_ALIASES, VANILLA, VANILLA_FLUIDS, MASS_INPUTS, STEEL_TAG

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / 'src' / 'generated' / 'resources' / 'assets' / 'singulo'
VANILLA_JAR = next(iter(sorted((ROOT / 'build' / 'moddev' / 'artifacts').glob('*minecraft-resources.jar'))), None) \
    if (ROOT / 'build' / 'moddev' / 'artifacts').exists() else None
OUT = ROOT / 'docs' / 'recipes'

Z = 3                       # 16px の絵を何倍で描くか
ICON = 16 * Z
SLOT = ICON + 12
PAD = 14
BG = (198, 198, 198)
SLOT_DARK = (139, 139, 139)
SLOT_LIGHT = (255, 255, 255)
SLOT_FILL = (139, 139, 139)
TEXT = (55, 55, 55)

FONT_PATHS = ['C:/Windows/Fonts/BIZ-UDGothicR.ttc', 'C:/Windows/Fonts/meiryo.ttc', 'C:/Windows/Fonts/YuGothM.ttc',
              '/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc']
FONT_BOLD_PATHS = ['C:/Windows/Fonts/BIZ-UDGothicB.ttc', 'C:/Windows/Fonts/meiryob.ttc'] + FONT_PATHS

# タグ・バニラの代表の絵（textures の中の名前）
TAG_ICONS = {
    'c:glass_blocks/colorless': 'block/glass', 'c:dusts/glowstone': 'item/glowstone_dust', 'c:ender_pearls': 'item/ender_pearl',
    'c:slime_balls': 'item/slime_ball', 'c:gems/diamond': 'item/diamond', 'singulo:star_core': 'item/nether_star',
    'c:gems/quartz': 'item/quartz', 'c:dusts/redstone': 'item/redstone', 'singulo:helium_bearing_stone': 'block/deepslate',
    'c:sands': 'block/sand', 'c:ingots/iron': 'item/iron_ingot', 'c:ingots/copper': 'item/copper_ingot', 'c:obsidians': 'block/obsidian',
}
VANILLA_TEXTURE_OVERRIDES = {
    'minecraft:piston': 'block/piston_side', 'minecraft:blast_furnace': 'block/blast_furnace_front',
    'minecraft:blue_ice': 'block/blue_ice', 'minecraft:sculk': 'block/sculk', 'minecraft:sculk_sensor': 'block/sculk_sensor_side',
}
# 液体の色（Java の SinguloFluids と同じ値）
FLUID_COLORS = {'dark_matter': 0x2A1E3C, 'hydrogen': 0xE6F5FF, 'oxygen': 0xF5E1E1, 'liquid_nitrogen': 0xBFEFFF,
                'helium': 0xF5F0C8, 'liquid_helium': 0xE6D2FF, 'axion_condensate': 0xA078E6, 'minecraft:water': 0x3F76E4}
# 製作場所 → 装置の正面の絵（mod の装置ID）
STATION_BLOCKS = {
    '焼成炉': 'kiln', '圧縮機': 'compressor', '電解槽': 'electrolyzer', 'アーカイブ端末': 'archive_terminal',
    '精密組立台': 'precision_assembler', '触媒反応器': 'catalytic_reactor', '極低温冷却塔': 'cooling_tower_controller',
    '粒子加速器': 'accelerator_controller', '量子もつれ合成器': 'entanglement_synthesizer', 'レーザー冷却器': 'laser_cooler',
    '縮退圧縮炉': 'degenerate_compactor_controller', 'C空洞': 'casimir_cavity_controller', '時間結晶育成槽': 'time_crystal_incubator',
    '特異点封入台': 'singularity_encapsulator', '残響共鳴器': 'echo_resonator', 'ワームホール生成器': 'wormhole_generator_core',
    'ワームホール固定化装置': 'wormhole_stabilizer', 'ハロー捕集器': 'halo_collector',
}

_jar = None
_cache = {}


def font(size, bold=False):
    for p in (FONT_BOLD_PATHS if bold else FONT_PATHS):
        if Path(p).exists():
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()


F_TITLE = font(22, True)
F_TEXT = font(16)
F_COUNT = font(18, True)
F_SMALL = font(13)


def vanilla_texture(path):
    global _jar
    if VANILLA_JAR is None:
        return None
    if _jar is None:
        _jar = zipfile.ZipFile(VANILLA_JAR)
    try:
        return Image.open(io.BytesIO(_jar.read(f'assets/minecraft/textures/{path}.png'))).convert('RGBA')
    except KeyError:
        return None


def first_frame(im):
    return im.crop((0, 0, im.width, im.width)) if im.height > im.width else im


def mod_texture(path):
    p = ASSETS / 'textures' / f'{path}.png'
    return Image.open(p).convert('RGBA') if p.exists() else None


def placeholder(label):
    im = Image.new('RGBA', (16, 16), (90, 96, 110, 255))
    return im


def icon(name):
    """材料・成果物の名前 → 16×16 の絵。"""
    if name in _cache:
        return _cache[name]
    im = None
    if name in VANILLA_FLUIDS or (name in MOD and MOD[name][2] == 'fluid'):
        key = VANILLA_FLUIDS.get(name) or MOD[name][0]
        im = fluid_icon(FLUID_COLORS.get(key, 0x88AACC))
    elif name in MASS_INPUTS:
        im = mod_texture('block/compressed_block_1')
    elif name == '鋼鉄インゴット':
        im = mod_texture('item/steel_ingot')
    elif name in OUTPUT_ALIASES:
        im = id_icon(OUTPUT_ALIASES[name])
    elif name in VANILLA:
        v = VANILLA[name]
        if v.startswith('#'):
            path = TAG_ICONS.get(v[1:])
            im = vanilla_texture(path) if path else None
        else:
            im = id_icon(v)
    elif name in MOD:
        iid, _, kind = MOD[name]
        im = mod_icon(iid, kind)
    if im is None:
        im = placeholder(name)
    im = first_frame(im).resize((16, 16), Image.NEAREST)
    _cache[name] = im
    return im


def id_icon(full_id):
    ns, path = full_id.split(':')
    if ns == 'minecraft':
        override = VANILLA_TEXTURE_OVERRIDES.get(full_id)
        if override:
            return vanilla_texture(override)
        for p in (f'item/{path}', f'block/{path}', f'block/{path}_front', f'block/{path}_side', f'block/{path}_top'):
            im = vanilla_texture(p)
            if im is not None:
                return im
        return None
    for n, (iid, _, kind) in MOD.items():
        if iid == path:
            return mod_icon(iid, kind)
    return None


def mod_icon(iid, kind):
    if kind == 'machine':
        return mod_texture(f'block/{iid}_front') or mod_texture(f'block/{iid}')
    if kind in ('block', 'part_block', 'part_glass', 'ruin_block', 'ruin_glass'):
        return mod_texture(f'block/{iid}')
    return mod_texture(f'item/{iid}') or mod_texture(f'item/{iid}_00') or mod_texture(f'block/{iid}')


def fluid_icon(rgb):
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    c = ((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, 255)
    d.polygon([(8, 1), (13, 9), (12, 13), (8, 15), (4, 13), (3, 9)], fill=c, outline=(60, 70, 90, 255))
    d.point((6, 8), fill=(255, 255, 255, 200))
    return im


# ---------------------------------------------------------------- 描く部品

def slot(img, x, y, name=None, count=None):
    d = ImageDraw.Draw(img)
    d.rectangle([x, y, x + SLOT - 1, y + SLOT - 1], fill=SLOT_FILL)
    d.line([(x, y), (x + SLOT - 1, y)], fill=(55, 55, 55))
    d.line([(x, y), (x, y + SLOT - 1)], fill=(55, 55, 55))
    d.line([(x, y + SLOT - 1), (x + SLOT - 1, y + SLOT - 1)], fill=SLOT_LIGHT)
    d.line([(x + SLOT - 1, y), (x + SLOT - 1, y + SLOT - 1)], fill=SLOT_LIGHT)
    if name is None:
        return
    ic = icon(name).resize((ICON, ICON), Image.NEAREST)
    img.alpha_composite(ic, (x + 6, y + 6))
    if count is not None and count != 1:
        label = count_label(count)
        tw = d.textlength(label, font=F_COUNT)
        for ox, oy in ((1, 1),):
            d.text((x + SLOT - 4 - tw + ox, y + SLOT - 24 + oy), label, font=F_COUNT, fill=(60, 60, 60))
        d.text((x + SLOT - 4 - tw, y + SLOT - 24), label, font=F_COUNT, fill=(255, 255, 255))


def count_label(q):
    if isinstance(q, F) and q.denominator != 1:
        return f'{q.numerator}/{q.denominator}'
    q = int(q)
    return f'{q:,}' if q < 100000 else f'{q // 1000:,}k'


def arrow(img, x, y, w):
    d = ImageDraw.Draw(img)
    cy = y + SLOT // 2
    d.rectangle([x, cy - 4, x + w - 14, cy + 4], fill=(120, 120, 120))
    d.polygon([(x + w - 16, cy - 12), (x + w, cy), (x + w - 16, cy + 12)], fill=(120, 120, 120))


def header(img, title, sub):
    d = ImageDraw.Draw(img)
    d.text((PAD, PAD - 4), title, font=F_TITLE, fill=TEXT)
    d.text((PAD, PAD + 24), sub, font=F_SMALL, fill=(90, 90, 90))


def panel(w, h):
    img = Image.new('RGBA', (w, h), BG + (255,))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, w - 1, h - 1], outline=(0, 0, 0))
    d.line([(1, 1), (w - 2, 1)], fill=(255, 255, 255))
    d.line([(1, 1), (1, h - 2)], fill=(255, 255, 255))
    d.line([(1, h - 2), (w - 2, h - 2)], fill=(85, 85, 85))
    d.line([(w - 2, 1), (w - 2, h - 2)], fill=(85, 85, 85))
    return img


TOP = PAD + 50


def is_fluid(name):
    return name in VANILLA_FLUIDS or (name in MOD and MOD[name][2] == 'fluid')


def amount_text(name, q):
    if is_fluid(name):
        return f'{int(q):,} mB'
    if name in MASS_INPUTS:
        return f'質量 {q}'
    return None


def output_cell(img, x, y, name, r):
    """完成品の枠（液体なら量を下に書く）。"""
    d = ImageDraw.Draw(img)
    if is_fluid(name):
        slot(img, x, y, name)
        d.text((x, y + SLOT + 2), f'{int(r["out"]):,} mB', font=F_SMALL, fill=TEXT)
    else:
        slot(img, x, y, name, r['out'])


def render(name, r):
    station = r['station']
    sub = f'段階{r["stage"]} · {station}'
    inputs = list(r['inputs'].items())
    if station == '作業台':
        w = PAD * 2 + SLOT * 3 + 90 + SLOT
        img = panel(max(w, 420), TOP + SLOT * 3 + PAD + 24)
        header(img, name, sub + ('' if 'shape' in r else ' · 不定形'))
        grid = [[None] * 3 for _ in range(3)]
        if 'shape' in r:
            rows, key = r['shape']
            for yy, row in enumerate(rows):
                for xx, ch in enumerate(row):
                    if ch != ' ':
                        grid[yy][xx] = key[ch]
        else:
            flat = [n for n, q in inputs for _ in range(int(q))]
            for i, n in enumerate(flat[:9]):
                grid[i // 3][i % 3] = n
        for yy in range(3):
            for xx in range(3):
                slot(img, PAD + xx * SLOT, TOP + yy * SLOT, grid[yy][xx])
        ax = PAD + SLOT * 3 + 18
        arrow(img, ax, TOP + SLOT, 56)
        output_cell(img, ax + 72, TOP + SLOT, name, r)
        return img
    if station in ('溶鉱炉（バニラ）', 'かまど（バニラ）'):
        img = panel(440, TOP + SLOT + PAD + 30)
        header(img, name, sub)
        (src, q), = inputs
        slot(img, PAD, TOP, src, q)
        furnace = 'block/blast_furnace_front' if '溶鉱炉' in station else 'block/furnace_front'
        fi = vanilla_texture(furnace)
        if fi is not None:
            img.alpha_composite(fi.resize((ICON, ICON), Image.NEAREST), (PAD + SLOT + 40, TOP + 6))
        arrow(img, PAD + SLOT + 10, TOP + SLOT // 2 + 4, 120)
        d = ImageDraw.Draw(img)
        d.text((PAD + SLOT + 20, TOP + SLOT + 2), f'{r["t"]}秒', font=F_SMALL, fill=TEXT)
        output_cell(img, PAD + SLOT + 150, TOP, name, r)
        return img
    if station == 'マルチブロック組み立て':
        cols = 4
        rows_n = (len(inputs) + cols - 1) // cols
        img = panel(PAD * 2 + cols * (SLOT + 10) + 220 + SLOT,TOP + rows_n * (SLOT + 10) + PAD + 10)
        header(img, name, f'段階{r["stage"]} · マルチブロック')
        for i, (n, q) in enumerate(inputs):
            slot(img, PAD + (i % cols) * (SLOT + 10), TOP + (i // cols) * (SLOT + 10), n, q)
        d = ImageDraw.Draw(img)
        ax = PAD + cols * (SLOT + 10) + 6
        arrow(img, ax, TOP, 50)
        # 完成した形は、部品のうちコントローラ（装置）の絵で表す
        ctrl = next((n for n, _ in inputs if n in MOD and MOD[n][2] == 'machine'), None)
        tx = ax + 62
        if ctrl is not None:
            slot(img, tx, TOP, ctrl)
            tx += SLOT + 8
        d.text((tx, TOP + SLOT // 2 - 10), name, font=F_TEXT, fill=TEXT)
        return img
    # 装置のレシピ
    cols = min(4, max(1, len(inputs)))
    rows_n = max(1, (len(inputs) + cols - 1) // cols)
    width = PAD * 2 + cols * (SLOT + 6) + 170 + SLOT + 60
    img = panel(max(width, 460), TOP + rows_n * (SLOT + 22) + PAD + 8)
    header(img, name, sub)
    d = ImageDraw.Draw(img)
    if not inputs:
        d.text((PAD, TOP + SLOT // 2 - 10), '（材料なし）', font=F_TEXT, fill=TEXT)
    for i, (n, q) in enumerate(inputs):
        x = PAD + (i % cols) * (SLOT + 6)
        y = TOP + (i // cols) * (SLOT + 22)
        text = amount_text(n, q)
        slot(img, x, y, n, None if text else q)
        if text:
            d.text((x, y + SLOT + 2), text, font=F_SMALL, fill=TEXT)
    ax = PAD + cols * (SLOT + 6) + 10
    st = STATION_BLOCKS.get(station)
    if st:
        sti = mod_texture(f'block/{st}_front')
        if sti is not None:
            img.alpha_composite(first_frame(sti).resize((ICON, ICON), Image.NEAREST), (ax + 52, TOP - 6))
    arrow(img, ax, TOP + 24, 150)
    info = []
    if r.get('t'):
        info.append(f'{r["t"]}秒')
    if r.get('fe'):
        info.append(f'{r["fe"]:,} FE/t')
    d.text((ax, TOP + SLOT + 10), ' · '.join(info), font=F_SMALL, fill=TEXT)
    output_cell(img, ax + 160, TOP + 24, name, r)
    return img


def file_id(name, index):
    iid = MOD[name][0] if name in MOD else f'recipe_{index}'
    return f'{index:03d}_{iid}'


def render_all(recipes, order, stage_names=None):
    """全レシピの画像と一覧を書き出す。"""
    OUT.mkdir(parents=True, exist_ok=True)
    for old in OUT.glob('*.png'):
        old.unlink()
    lines = ['# レシピ一覧', '',
             'すべてのレシピを、段階ごとに画像で並べたもの。`tools/gen_data.py` を実行すると自動で作り直される（手で書き換えない）。', '']
    by_stage = {}
    for i, name in enumerate(order):
        by_stage.setdefault(recipes[name]['stage'], []).append((i, name))
    for stage in sorted(by_stage):
        title = stage_names.get(stage, '') if stage_names else ''
        lines += [f'## 段階{stage}' + (f'　{title}' if title else ''), '']
        for i, name in by_stage[stage]:
            r = recipes[name]
            fid = file_id(name, i)
            render(name, r).convert('RGB').save(OUT / f'{fid}.png')
            lines.append(f'### {name}')
            lines.append('')
            lines.append(f'![{name}]({fid}.png)')
            if r.get('note'):
                lines.append('')
                lines.append(r['note'])
            lines.append('')
    (OUT / 'README.md').write_text('\n'.join(lines), encoding='utf-8')
    return len(order)
