# -*- coding: utf-8 -*-
"""0.18.0: 印刷された食品（フードプリンター・フードカートリッジ・5種類の宇宙食）。

食材をフードプリンターの食材の枠に入れると、タンパク質・炭水化物・甘味のペーストになって、それぞれのフードカートリッジへ入る。
画面で選んだ食品を、ペーストと電力で1層ずつ印刷する。食品の値と形・ペーストの分け方は Java の food.PrintedFood・food.FoodPaste、
印刷の動きは client.FoodPrinterRenderer。ここは ID・レシピ・文言・説明・ハンドブックの章・ブロックの絵とモデル・食品の絵・タグ。
レシピは作業台の形を決めたレシピで、一覧の番号がずれないよう recipes.py の最後に足す。
"""
from pathlib import Path

MODID = 'singulo'

IDS = {
    'フードプリンター': ('food_printer', 'Food Printer', 'machine'),
    'フードカートリッジ': ('food_cartridge', 'Food Cartridge', 'tool'),
    '栄養バー': ('nutrient_bar', 'Nutrient Bar', 'item'),
    '積層ステーキ': ('printed_steak', 'Layered Steak', 'item'),
    'らせんパスタ': ('spiral_pasta', 'Spiral Pasta', 'item'),
    'ハニカムケーキ': ('honeycomb_cake', 'Honeycomb Cake', 'item'),
    'フルーツゼリーキューブ': ('fruit_gel_cube', 'Fruit Gel Cube', 'item'),
}

FOODS = ('nutrient_bar', 'printed_steak', 'spiral_pasta', 'honeycomb_cake', 'fruit_gel_cube')

# 一般の装置のモデル（筐体と正面の絵）を作らず、ここで書くブロック
CUSTOM_BLOCKS = {'food_printer'}

# レシピに使うバニラの物（ids.VANILLA に足す）
VANILLA = {'ガラス瓶': 'minecraft:glass_bottle'}

# ペーストの種類（FoodPaste の番号の順）と色（GUI・カートリッジの窓・印刷の光）
PASTES = (('protein', (214, 96, 96)), ('carb', (232, 200, 120)), ('sweet', (236, 120, 200)))

# 食材の分け方（タグ singulo:food_paste/<種類>）。他modの食材は共通タグで受け付ける（なければ無視）
PASTE_TAGS = {
    'protein': ['minecraft:beef', 'minecraft:cooked_beef', 'minecraft:porkchop', 'minecraft:cooked_porkchop', 'minecraft:chicken',
                'minecraft:cooked_chicken', 'minecraft:mutton', 'minecraft:cooked_mutton', 'minecraft:rabbit', 'minecraft:cooked_rabbit',
                'minecraft:cod', 'minecraft:cooked_cod', 'minecraft:salmon', 'minecraft:cooked_salmon', 'minecraft:egg',
                '#c:foods/raw_meat', '#c:foods/cooked_meat', '#c:foods/raw_fish', '#c:foods/cooked_fish'],
    'carb': ['minecraft:wheat', 'minecraft:bread', 'minecraft:potato', 'minecraft:baked_potato', 'minecraft:carrot',
             'minecraft:beetroot', 'minecraft:dried_kelp', '#c:foods/bread', '#c:foods/vegetable'],
    'sweet': ['minecraft:apple', 'minecraft:melon_slice', 'minecraft:sweet_berries', 'minecraft:glow_berries', 'minecraft:sugar',
              'minecraft:cocoa_beans', 'minecraft:honeycomb', 'minecraft:cookie', 'minecraft:pumpkin_pie',
              '#c:foods/fruit', '#c:foods/berry', '#c:foods/cookie', '#c:foods/candy'],
}


def recipes(R, shaped):
    # フードプリンター: 上に食材を入れるホッパー、真ん中にガラスの印刷室とノズルを動かすピストン、下に制御の回路。外は鋼板
    R('フードプリンター', 1, '作業台', {'鋼板': 4, 'ホッパー': 1, 'ガラス': 2, 'ピストン': 1, '基礎回路': 1}, cat='machine',
      note='食材をペーストにしてフードカートリッジにため、選んだ宇宙食を電力で1層ずつ印刷する')
    shaped['フードプリンター'] = (('PHP', 'GXG', 'PCP'), {'P': '鋼板', 'H': 'ホッパー', 'G': 'ガラス', 'X': 'ピストン', 'C': '基礎回路'})
    # フードカートリッジ: 上に鋼板のふた、左右にガラス板の窓、真ん中にペーストをためる瓶、下に銅の注ぎ口
    R('フードカートリッジ', 1, '作業台', {'鋼板': 1, 'ガラス板': 2, 'ガラス瓶': 1, '銅インゴット': 1}, cat='tool',
      note='フードプリンターに入れて、ペーストを64までためる。空になっても使い続けられる')
    shaped['フードカートリッジ'] = ((' P ', 'NBN', ' W '), {'P': '鋼板', 'N': 'ガラス板', 'B': 'ガラス瓶', 'W': '銅インゴット'})


UI = {
    'gui.singulo.food_printer.ingredient': ('%sの食材', 'Ingredients for %s'),
    'gui.singulo.food_printer.ingredient.protein': ('肉・魚・卵', 'Meat, fish and eggs'),
    'gui.singulo.food_printer.ingredient.carb': ('穀物・野菜', 'Grains and vegetables'),
    'gui.singulo.food_printer.ingredient.sweet': ('果物・砂糖・ハチミツの巣', 'Fruit, sugar and honeycomb'),
    'gui.singulo.food_printer.ingredient.hint': ('多めに入れておくと、足りないペーストから順に補う', 'Stock plenty and the paste that runs short is refilled first'),
    'gui.singulo.food_printer.cartridge': ('%sのフードカートリッジ', 'Food Cartridge for %s'),
    'gui.singulo.food_printer.output': ('印刷した食品', 'Printed food'),
    'gui.singulo.food_printer.auto.hint': ('ペーストが足りる食品を印刷する（出口にある食品を優先）', 'Prints any food the paste allows (the food in the output first)'),
    'gui.singulo.food_printer.fixed.hint': ('この食品だけを、ペーストが足りる間印刷する', 'Prints only this food while the paste lasts'),
    'gui.singulo.food_printer.nothing': ('ペーストが足りる食品がない', 'No food has enough paste'),
    'gui.singulo.food_printer.status.short': ('ペースト不足', 'Short of paste'),
    'gui.singulo.food_printer.cost': ('1個に使うペースト', 'Paste per item'),
    'gui.singulo.food_printer.cost.line': ('%s %s', '%s %s'),
    'gui.singulo.food_printer.time': ('%s秒・%s FE', '%s s · %s FE'),
    'gui.singulo.food_printer.progress': ('印刷 %s%%', 'Printing %s%%'),
    'gui.singulo.food_printer.paste': ('%s %s / %s', '%s %s / %s'),
    'gui.singulo.food_printer.no_cartridge': ('カートリッジがない', 'No cartridge'),
    'food_paste.singulo.protein': ('タンパク質', 'Protein'),
    'food_paste.singulo.carb': ('炭水化物', 'Carbohydrate'),
    'food_paste.singulo.sweet': ('甘味', 'Sweet'),
    'tooltip.singulo.food_cartridge.empty': ('空（ペーストを %s まで入れられる）', 'Empty (holds %s paste)'),
    'tooltip.singulo.food_cartridge.amount': ('%sのペースト %s / %s', '%s paste %s / %s'),
    'item.singulo.food_cartridge.filled': ('フードカートリッジ（%s）', 'Food Cartridge (%s)'),
}

CHAPTER = ('food', 'singulo:nutrient_bar', '印刷された食品', 'Printed Food', [
    ('printer', 'フードプリンター',
     'フードプリンターは、食材をペーストにして、宇宙食を1層ずつ印刷する装置です。電力（1 tickあたり 20 FE）で動きます。\n'
     '画面の左は、上からタンパク質・炭水化物・甘味の行です。各行の右の枠にフードカートリッジを入れると、その種類のペーストを64までためます。'
     '各行の左の枠に、その種類の食材をまとめて入れておきます。肉・魚・卵はタンパク質、穀物・野菜は炭水化物、果物・砂糖・ハチミツの巣は甘味です。'
     '選んだ食品に足りない種類から、次に少ない種類の順で、入る量だけカートリッジへ補われます。入りきらない食材は枠で待ちます。'
     '食材1個のペーストは、その食べ物の満腹度の分です（小麦と卵は2、砂糖とカカオ豆は1、ハチミツの巣は3）。\n'
     '出口の上のボタンで、作る食品を選びます（右クリックで自動に戻ります）。ペーストが足りて出口が空いている間、続けて印刷します。'
     '自動では、出口にある食品を優先して、ペーストが足りる食品を印刷します。止めるときは、右上の電源を切ります（食材をペーストにするのは続けます）。'
     'ガラスの印刷室では、ノズルが前後左右に走って、食品を下の層から形どおりに積み上げます。後ろの窓では、カートリッジのペーストの量が光ります。\n'
     'ホッパーなどからは、食材とカートリッジを入れられ、印刷した食品を取り出せます。壊すと、中の物とカートリッジ（ペーストはそのまま）を落とします。',
     'Food Printer',
     'The Food Printer turns ingredients into paste and prints space food layer by layer. It runs on power (20 FE per tick).\n'
     'The left of the screen has a row each for protein, carbohydrate and sweet, from the top. A Food Cartridge in the right slot of a row holds up to 64 of that paste. '
     'Stock the left slot of each row with that kind of ingredient: meat, fish and eggs for protein; grains and vegetables for carbohydrate; fruit, sugar and honeycomb for sweet. '
     'The cartridges are refilled while they have room, the kind the chosen food is short of first, then the lowest. Ingredients that do not fit wait in their slot. '
     'One ingredient gives as much paste as the food it restores (wheat and eggs give 2, sugar and cocoa beans 1, honeycomb 3).\n'
     'Pick the food with the button above the output (right-click returns to automatic). The printer keeps printing it while there is paste and room in the output. '
     'On automatic it prints any food the paste allows, the food already in the output first. To stop, switch it off at the top right (it keeps turning ingredients into paste). '
     'Inside the glass chamber, the nozzle runs back and forth and builds the food up from the bottom layer. The windows on the back glow with how much paste each cartridge holds.\n'
     'Hoppers can put in ingredients and cartridges and take out printed food. Breaking it drops its items, and cartridges keep their paste.'),
    ('foods', '宇宙食',
     '栄養バー: 炭水化物4・タンパク質2。すぐに食べられる（満腹度6）。\n'
     '積層ステーキ: タンパク質8。焼き目と脂の筋まで印刷したステーキ（満腹度9）。\n'
     'らせんパスタ: 炭水化物6・タンパク質2。トマトソースのパスタ。採掘速度上昇（1分）（満腹度8）。\n'
     'ハニカムケーキ: 甘味4・炭水化物3。ハチミツの六角形のケーキ。移動速度上昇（1分）（満腹度7）。\n'
     'フルーツゼリーキューブ: 甘味4。4色の層のゼリー。満腹でも食べられ、再生（5秒）（満腹度3）。',
     'Space food',
     'Nutrient Bar: 4 carbohydrate, 2 protein. Quick to eat (6 hunger).\n'
     'Layered Steak: 8 protein. Printed down to the sear marks and marbling (9 hunger).\n'
     'Spiral Pasta: 6 carbohydrate, 2 protein. Pasta with tomato sauce. Haste for 1 minute (8 hunger).\n'
     'Honeycomb Cake: 4 sweet, 3 carbohydrate. A cake topped with honey hexagons. Speed for 1 minute (7 hunger).\n'
     'Fruit Gel Cube: 4 sweet. Four layers of jelly. Edible when full, with 5 seconds of Regeneration (3 hunger).'),
])

DESC = {
    'フードプリンター': ('タンパク質・炭水化物・甘味それぞれの枠の食材を、足りないペーストからフードカートリッジに補い、選んだ宇宙食を電力で1層ずつ印刷する。'
                  'ガラスの印刷室で、ノズルが食品を形どおりに積み上げる。',
                  'Refills Food Cartridges with the protein, carbohydrate or sweet paste that runs short from the ingredients in each row, then prints the space food you pick, '
                  'layer by layer, on power. A nozzle builds the food up inside the glass chamber.'),
    'フードカートリッジ': ('フードプリンターに入れる、ペーストの入れ物。1種類のペーストを64までためる。空になれば別の種類にも使える。',
                   'A paste container for the Food Printer. Holds up to 64 of one kind of paste, and can switch kinds once empty.'),
    '栄養バー': ('印刷した栄養バー。すぐに食べられる。', 'A printed nutrient bar. Quick to eat.'),
    '積層ステーキ': ('焼き目と脂の筋まで印刷したステーキ。', 'A steak printed down to its sear marks and marbling.'),
    'らせんパスタ': ('トマトソースのらせんパスタ。食べると採掘が速くなる。', 'Spiral pasta with tomato sauce. Eating it gives Haste.'),
    'ハニカムケーキ': ('ハチミツの六角形のケーキ。食べると足が速くなる。', 'A cake topped with honey hexagons. Eating it gives Speed.'),
    'フルーツゼリーキューブ': ('4色の層のゼリー。満腹でも食べられ、少し回復する。', 'Four layers of jelly. Edible when full, and heals a little.'),
}

SUMMARY = {
    'フードプリンター': ('食材から宇宙食を印刷する装置。', 'Prints space food from ingredients.'),
    'フードカートリッジ': ('フードプリンターのペーストの入れ物。', 'Holds paste for the Food Printer.'),
}


# ---------------------------------------------------------------- ブロックの絵

BODY = (224, 228, 232)
STEEL = (64, 68, 78)
RAIL = (172, 178, 188)
GREEN = (120, 236, 160)


def _put(px, x, y, c):
    px[x, y] = tuple(c[:3]) + (255,)


def printer_textures():
    """プリンターの材質（どれも 16×16。既定の UV で貼る）:
    body 白い樹脂の外板（継ぎ目・色むら）、base 黒鋼の台、panel 台の正面（表示窓と3色のランプ）、inner 印刷室の奥（暗い格子）、
    bed 印刷台（温める線の格子）、rail 研いだ鋼のレール、top 天板（換気の溝）、feed 食材の入口（暗い穴とふち）、
    glass うすいガラス、light 光る帯（緑）、window カートリッジの窓のまわり（暗い溝）。"""
    import surface
    body = surface.panels(BODY, 'fp-body', size=8, rivets=False, grain=0.05)
    base = surface.metal(STEEL, 'fp-base', grain=0.06, streak='h', scuffs=4, edge=0.1)
    panel = base.copy()
    pp = panel.load()
    # 台の正面は y 0〜2（絵の 14〜15 行）。左に表示窓（暗い緑に点の文字）、右に3色のランプ
    for x in range(3, 9):
        for y in (14, 15):
            _put(pp, x, y, (26, 40, 34))
    for x in (4, 6, 7):
        _put(pp, x, 14, GREEN)
    _put(pp, 5, 15, (70, 150, 104))
    for i, (_, c) in enumerate(PASTES):
        _put(pp, 10 + 2 * i, 14, c)
        _put(pp, 10 + 2 * i, 15, tuple(int(v * 0.55) for v in c))
    inner = surface.metal((40, 42, 50), 'fp-inner', grain=0.05, streak=None, scuffs=0, edge=0)
    ip = inner.load()
    for i in range(0, 16, 4):
        for j in range(16):
            _put(ip, i, j, (48, 51, 60))
            _put(ip, j, i, (48, 51, 60))
    bed = surface.metal((58, 60, 70), 'fp-bed', grain=0.04, streak=None, scuffs=2, edge=0)
    bp = bed.load()
    for i in range(16):
        for j in range(0, 16, 2):
            _put(bp, i, j, (82, 86, 98))
    for x, y in ((7, 8), (8, 7), (9, 9), (6, 6)):
        _put(bp, x, y, (150, 96, 70))                      # 温める線の、ほのかな赤み
    rail = surface.metal(RAIL, 'fp-rail', grain=0.05, streak='v', scuffs=3, edge=0.12)
    top = body.copy()
    tp = top.load()
    for y in (2, 4, 11, 13):
        for x in range(3, 13):
            _put(tp, x, y, (150, 156, 164))                  # 換気の溝
            _put(tp, x, y + 1, (238, 240, 244)) if x % 3 else None
    feed = surface.metal((30, 30, 34), 'fp-feed', grain=0.04, streak=None, scuffs=0, edge=0)
    fp = feed.load()
    for i in range(16):
        for x, y in ((i, 0), (i, 15), (0, i), (15, i)):
            _put(fp, x, y, (120, 124, 134))
    glass = surface.glass((200, 226, 236), 'fp-glass', alpha=42, streaks=2)
    light = surface.glow(GREEN, 'fp-light')
    window = surface.metal((34, 36, 42), 'fp-window', grain=0.03, streak='v', scuffs=0, edge=0)
    return {'body': body, 'base': base, 'panel': panel, 'inner': inner, 'bed': bed, 'rail': rail, 'top': top, 'feed': feed,
            'glass': glass, 'light': light, 'window': window}


# ---------------------------------------------------------------- モデル（どの座標もドット単位）

ALL = ('north', 'south', 'east', 'west', 'up', 'down')


def _el(f, t, key, glow=False, **faces):
    e = {'from': list(f), 'to': list(t), 'faces': {n: {'texture': faces.get(n, key)} for n in ALL}}
    if glow:
        e['neoforge_data'] = {'block_light': 15, 'sky_light': 15}
    return e


# 印刷台（BER と同じ座標）: x 4〜12、z 3〜9、上面 y 4
BED = (4, 3, 12, 9)


def printer_models(write, assets):
    """フードプリンター（正面が北）。黒鋼の台（0〜2。正面に表示窓とランプ）の上に、前に白い枠のガラスの印刷室（x 2〜14、z 1〜11、
    y 2〜12）、後ろにカートリッジの塔（z 11〜15。背面に3つの縦長の窓）、上に天板（12〜14）と食材の入口（14〜16）。
    印刷室の床に印刷台、天井の左右にノズルの橋を走らせるレール。動く橋・ノズル・食品・カートリッジの中身は FoodPrinterRenderer。"""
    tex = {k: f'{MODID}:block/food_printer_{k}' for k in ('body', 'base', 'panel', 'inner', 'bed', 'rail', 'top', 'feed', 'glass',
                                                           'light', 'window')}
    solid = [
        _el((1, 0, 1), (15, 2, 15), '#base', north='#panel'),
        _el((2, 2, 1), (14, 3, 11), '#body', up='#inner'),                           # 印刷室の床
        _el((BED[0], 3, BED[1]), (BED[2], 4, BED[3]), '#bed'),                       # 印刷台
        _el((2, 3, 1), (3, 12, 2), '#body'), _el((13, 3, 1), (14, 12, 2), '#body'),  # 正面の枠の柱
        _el((1, 2, 11), (15, 12, 14), '#body', north='#inner', south='#window'),     # カートリッジの塔（前は印刷室の奥の壁）
        # 背面の窓のまわりの枠（窓は x 3〜5・7〜9・11〜13、y 4〜10）
        _el((1, 2, 14), (15, 4, 15), '#body'), _el((1, 10, 14), (15, 12, 15), '#body'),
        _el((1, 4, 14), (3, 10, 15), '#body'), _el((5, 4, 14), (7, 10, 15), '#body'),
        _el((9, 4, 14), (11, 10, 15), '#body'), _el((13, 4, 14), (15, 10, 15), '#body'),
        # 天井の左右のレール（ガラスの内側）
        _el((3, 11, 2), (4, 12, 11), '#rail'), _el((12, 11, 2), (13, 12, 11), '#rail'),
        _el((1, 12, 1), (15, 14, 15), '#body', up='#top'),                           # 天板
        _el((5, 12, 0), (11, 13, 1), '#light', glow=True),                           # 正面の光る帯
        # 食材の入口（じょうご）
        _el((5, 14, 5), (11, 15, 11), '#base'),
        _el((6, 15, 6), (10, 16, 10), '#base', up='#feed'),
    ]
    glass = [
        _el((3, 3, 1), (13, 12, 2), '#glass'),                                       # 正面
        _el((2, 3, 2), (3, 12, 11), '#glass'), _el((13, 3, 2), (14, 12, 11), '#glass'),   # 左右
    ]
    textures = {**tex, 'particle': tex['body']}
    write(assets / 'models' / 'block' / 'food_printer.json', {
        'parent': 'minecraft:block/block', 'textures': textures, 'render_type': 'minecraft:cutout', 'elements': solid})
    write(assets / 'models' / 'block' / 'food_printer_glass.json', {
        'parent': 'minecraft:block/block', 'textures': textures, 'render_type': 'minecraft:translucent', 'elements': glass})
    rot = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
    multipart = []
    for facing, y in rot.items():
        for model in ('food_printer', 'food_printer_glass'):
            apply = {'model': f'{MODID}:block/{model}'}
            if y:
                apply['y'] = y
            multipart.append({'when': {'facing': facing}, 'apply': apply})
    write(assets / 'blockstates' / 'food_printer.json', {'multipart': multipart})
    write(assets / 'models' / 'item' / 'food_printer.json', {'parent': f'{MODID}:block/food_printer'})


# ---------------------------------------------------------------- 食品とカートリッジの絵（16×16）

#: 輪郭の文字。色は決め打ちにせず、となりの色から作る（outline_colors）。
OUTLINE = 'oO'
_SIDES = ((1, 0), (-1, 0), (0, 1), (0, -1))
_CORNERS = ((1, 1), (1, -1), (-1, 1), (-1, -1))


def _at(rows, x, y):
    return rows[y][x] if 0 <= y < len(rows) and 0 <= x < len(rows[y]) else '.'


def outline_gaps(rows):
    """輪郭の抜け: 透明（絵の外を含む）と上下左右で接している、輪郭でない色のドットの位置。空なら輪郭が全体を囲んでいる。"""
    return [(x, y) for y, row in enumerate(rows) for x, ch in enumerate(row)
            if ch != '.' and ch not in OUTLINE and any(_at(rows, x + dx, y + dy) == '.' for dx, dy in _SIDES)]


def stray_outline(rows):
    """色のドットに上下左右でも斜めでも接していない輪郭のドット（色を決められない、浮いた輪郭）の位置。"""
    return [(x, y) for y, row in enumerate(rows) for x, ch in enumerate(row)
            if ch in OUTLINE and all(_at(rows, x + dx, y + dy) in '.' + OUTLINE for dx, dy in _SIDES + _CORNERS)]


def _shade(c):
    """輪郭の色: となりの色と同じ色相のまま、暗く少し鮮やかにする。"""
    import colorsys
    h, s, v = colorsys.rgb_to_hsv(*(t / 255 for t in c))
    r, g, b = colorsys.hsv_to_rgb(h, min(1.0, s * 1.15 + 0.08), v * 0.42)
    return round(r * 255), round(g * 255), round(b * 255)


def outline_colors(rows, pal, sources=None):
    """輪郭のドットごとの色: 上下左右の色のドット（なければ斜め）の平均を _shade で暗くする。
    sources は輪郭の文字ごとに、色を取る文字（器とパスタのように、別の物の輪郭がとなり合う絵で混ぜないため）。"""
    sources = sources or {}
    out = {}
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch not in OUTLINE:
                continue
            allowed = sources.get(ch)
            near = []
            for ring in (_SIDES, _CORNERS):
                near = [pal[n] for n in (_at(rows, x + dx, y + dy) for dx, dy in ring)
                        if n != '.' and n not in OUTLINE and (allowed is None or n in allowed)]
                if near:
                    break
            assert near, f'輪郭 ({x}, {y}) の色を取るとなりの色がない'
            out[(x, y)] = _shade(tuple(sum(c[i] for c in near) / len(near) for i in range(3)))
    return out


def _pixmap(rows, pal, sources=None):
    """文字の並びの絵（1文字1ドット、'.' は透明）。色は pal の文字ごと、輪郭（o・O）はとなりの色から。
    輪郭が全体を囲んでいない・浮いた輪郭があるときは、作らずに止める。"""
    from PIL import Image
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), [len(r) for r in rows]
    assert not outline_gaps(rows), f'輪郭の抜け: {outline_gaps(rows)}'
    assert not stray_outline(rows), f'浮いた輪郭: {stray_outline(rows)}'
    edge = outline_colors(rows, pal, sources)
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = im.load()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != '.':
                px[x, y] = tuple(edge[(x, y)] if ch in OUTLINE else pal[ch][:3]) + (255,)
    return im


# 光は左上から。どの絵も、輪郭・影・中間・明るい面・つやの数段で描き、のっぺりさせない。
# 値は (並び, 色) か (並び, 色, 輪郭の色を取る文字)。輪郭（o・O）の色は書かない（となりの色から作る）。
FOOD_ART = {
    # 栄養バー: 斜めから見たオーツの板（上の面は明るく、手前は焼き色）にチョコの粒。右の端は銀の包み（緑の帯）が残る
    'nutrient_bar': ([
        '................',
        '................',
        '................',
        '................',
        '.ooooooooooooo..',
        'oTTtTTtTTaAAAbo.',
        'otTCtTtcTaAaAbbo',
        'oFFcFFfFFcbaabbo',
        'oFfFFcFFfFbaLabo',
        'offfffffffbbbbbo',
        '.ooooooooooooooo',
        '................',
        '................',
        '................',
        '................',
        '................',
    ], {'T': (238, 204, 140), 't': (216, 174, 110), 'C': (140, 90, 56), 'c': (96, 58, 34),
        'F': (194, 148, 86), 'f': (158, 114, 62), 'A': (234, 240, 248), 'a': (176, 186, 202), 'b': (112, 122, 140),
        'L': (90, 200, 140)}),
    # 積層ステーキ: 上の面は焼き目（斜めの網の跡とつや）、手前の切り口は桃色の層に白い脂の筋
    'printed_steak': ([
        '................',
        '................',
        '................',
        '....oooooooo....',
        '..ooSGgSSGgSoo..',
        '.oSGSgSSsgSSsgo.',
        'oSsSgSSsgSSsgSso',
        'osSgSsSgSsSgsSso',
        'omMMfMMmMfMMMmmo',
        'odmfMMmMMmfMmmdo',
        'oddmmdmmdmmddddo',
        '.oooooooooooooo.',
        '................',
        '................',
        '................',
        '................',
    ], {'S': (178, 98, 58), 's': (140, 70, 40), 'g': (66, 32, 20), 'G': (232, 172, 120),
        'M': (222, 112, 104), 'm': (184, 64, 58), 'd': (132, 40, 38), 'f': (246, 226, 212)}),
    # らせんパスタ: 白い器に、ねじれたパスタ（明暗の斜めの筋）を山に盛り、トマトソースとバジル、粉チーズ
    'spiral_pasta': ([
        '................',
        '................',
        '......oooo......',
        '....ooPPpqoo....',
        '...oPqpRRrpPo...',
        '..oPprRRrrsqpo..',
        '.oPqprRcgGsrpPo.',
        '.opPqprsgsrqpqo.',
        '.oqpPqpssqpPqpo.',
        'OVWWWWWWWWWWWWwO',
        '.OWWWWWWWWWWWwO.',
        '..OwWWWWWWWWwO..',
        '...OOwwwwwwOO...',
        '....OOOOOOOO....',
        '................',
        '................',
    ], {'P': (252, 224, 142), 'p': (234, 192, 100), 'q': (192, 140, 62),
        'R': (242, 98, 74), 'r': (204, 46, 36), 's': (138, 26, 24), 'c': (255, 248, 222),
        'G': (124, 204, 92), 'g': (58, 138, 52),
        'V': (255, 255, 255), 'W': (236, 240, 245), 'w': (194, 202, 214)},
        # パスタの山の輪郭（o）はパスタとソースから、器の輪郭（O）は器から色を取る
        {'o': 'PpqRrscGg', 'O': 'VWw'}),
    # ハニカムケーキ: 上はハチミツの六角形のつや、ふちから手前へ垂れる。スポンジ・クリーム・スポンジの層
    'honeycomb_cake': ([
        '................',
        '................',
        '................',
        '...oooooooooo...',
        '..oHHxHHxHHxho..',
        '.oHxhHxhHxhHxho.',
        '.ohhjhhjhhjhhjo.',
        '.oKhKKhKKKKhKko.',
        '.oKKKKhKKKKKKko.',
        '.occCCCCCCCCcco.',
        '.okKKkKKkKKkkno.',
        '.onkknkknkknnno.',
        '..oooooooooooo..',
        '................',
        '................',
        '................',
    ], {'H': (255, 216, 96), 'h': (240, 170, 40), 'j': (194, 118, 20), 'x': (206, 132, 24),
        'K': (248, 216, 152), 'k': (226, 184, 112), 'n': (186, 140, 78), 'C': (255, 250, 238), 'c': (230, 220, 202)}),
    # フルーツゼリーキューブ: 斜めから見た立方体。手前は赤・橙・黄・緑の層、右の面は暗く、上にミントの葉。左に縦のつや
    'fruit_gel_cube': ([
        '................',
        '....ooooooooooo.',
        '...oTWWtTTMmTto.',
        '..otTWtTTmMTtgo.',
        '.oooooooooooggo.',
        '.oGWGGGGGGGoggo.',
        '.oGwGGGGGGGoggo.',
        '.oYWYYYYYYYoyyo.',
        '.oYYYYYYYYYoyyo.',
        '.oAAAAAAAAAoaao.',
        '.oAwAAAAAAAoaao.',
        '.oEEEEEEEEEoeo..',
        '.oEEEEEEEEEoo...',
        '.ooooooooooo....',
        '................',
        '................',
    ], {'T': (196, 242, 176), 't': (152, 226, 132), 'W': (255, 255, 255), 'w': (236, 248, 255),
        'M': (84, 196, 116), 'm': (40, 128, 70), 'G': (122, 212, 110), 'g': (78, 158, 80),
        'Y': (252, 222, 84), 'y': (198, 168, 50), 'A': (250, 152, 62), 'a': (198, 108, 40),
        'E': (222, 62, 92), 'e': (158, 38, 68)}),
}


def food_icons():
    import art16
    return {name: art16.center(_pixmap(*art)) for name, art in FOOD_ART.items()}


# フードカートリッジ: 鋼のふた、白い筒（左が明るく右が暗い）、縦長の窓、銅の注ぎ口。14×14 の内側に収める
CARTRIDGE_ART = ([
    '................',
    '....oooooooo....',
    '...oSSSSssddo...',
    '...osssssdddo...',
    '...oooooooooo...',
    '...oWWwwwwwvo...',
    '...oWwnnnnwvo...',
    '...oWwnXXnwvo...',
    '...oWwnXXnwvo...',
    '...oWwnXXnwvo...',
    '...oWwnnnnwvo...',
    '...oWwwwwwvvo...',
    '....oooCcooo....',
    '......oCko......',
    '......oooo......',
    '................',
], {'S': (182, 190, 202), 's': (124, 132, 146), 'd': (82, 88, 100),
    'W': (252, 253, 255), 'w': (226, 230, 236), 'v': (184, 190, 200), 'n': (52, 56, 66), 'X': (70, 76, 88),
    'C': (240, 170, 110), 'c': (206, 126, 78), 'k': (150, 84, 50)})


def cartridge_icons():
    """フードカートリッジの絵と、窓の中身（layer1。ペーストの色に染める。左の列を明るく）。"""
    from PIL import Image
    rows, pal = CARTRIDGE_ART
    base = _pixmap(rows, pal)
    box = base.getbbox()
    assert box[2] - box[0] <= 14 and box[3] - box[1] <= 14, box
    window = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = window.load()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch == 'X':
                v = 255 if row.index('X') == x else 200
                px[x, y] = (v, v, v, 255)
    return base, window


def models(ctx):
    """絵とモデルとタグ（gen_data の一般の生成のあとに書く。食品とカートリッジの一般の絵・モデルは上書きする）。"""
    import art16
    write = ctx['write_json']
    assets = Path(ctx['ASSETS'])
    tb = assets / 'textures' / 'block'
    ti = assets / 'textures' / 'item'
    for name, im in printer_textures().items():
        art16.save(im, tb / f'food_printer_{name}.png')
    printer_models(write, assets)
    for name, im in food_icons().items():
        art16.save(im, ti / f'{name}.png')
    base, window = cartridge_icons()
    art16.save(base, ti / 'food_cartridge.png')
    art16.save(window, ti / 'food_cartridge_paste.png')
    write(assets / 'models' / 'item' / 'food_cartridge.json', {'parent': 'minecraft:item/generated', 'textures': {
        'layer0': f'{MODID}:item/food_cartridge', 'layer1': f'{MODID}:item/food_cartridge_paste'}})
    data = Path(ctx['RES']) / 'data' / MODID / 'tags' / 'item' / 'food_paste'
    for kind, values in PASTE_TAGS.items():
        write(data / f'{kind}.json', {'values': [v if not v.startswith('#') else {'id': v, 'required': False} for v in values]})
