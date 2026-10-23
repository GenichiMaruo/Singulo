# -*- coding: utf-8 -*-
"""施設の建材の設計図（gen_data.py から呼ぶ）。

遺構・研究所の建材は、収納から見つかる設計図があれば作業台で作れる。設計図は種類ごとに1枚（遺構パネル・照明・研究所の調度品など）で、
作業台で素材と一緒に並べると建材ができ、設計図は作業台に残る（Java の BlueprintItem）。レシピは recipes.py の末尾。
壊せない・仕掛けのあるブロック（保管庫・封印コンソール・耐圧殻・隔壁・制御盤・炉心・記録端末・ボス部屋の仕掛けなど）は作れないまま。

ここで決めるもの: 設計図の一覧（名前・作れるもの・見つかる収納）と、設計図の絵。
"""
from PIL import Image, ImageDraw

# 名前 → (ID, 英名, 見つかる収納 {loot table の種類: 出る確率}, 絵の印)
# 収納の種類: 'ruin:<遺構ID>' は遺構の保管庫（gen_data の loot_table/ruins）、'lab:<表>' は研究所の収納（lab.py の chests/lab_*）
BLUEPRINTS = {
    '遺構パネルの設計図': ('blueprint_ruin_panels', 'Ruin Panel Blueprint',
                         {'ruin:observation_post': 0.45, 'ruin:research_building': 0.30}, 'panel'),
    '無垢の遺構建材の設計図': ('blueprint_pristine_ruin', 'Pristine Ruin Material Blueprint',
                           {'ruin:research_building': 0.30, 'ruin:culture_facility': 0.40, 'ruin:final_lab': 0.30}, 'pillar'),
    '遺構ガラスの設計図': ('blueprint_ruin_glass', 'Ruin Glass Blueprint',
                         {'ruin:research_building': 0.25, 'ruin:culture_facility': 0.30, 'ruin:final_lab': 0.40}, 'glass'),
    '照明の設計図': ('blueprint_lighting', 'Lighting Blueprint',
                   {'ruin:observation_post': 0.20, 'ruin:culture_facility': 0.25, 'lab:lab_supplies': 0.25}, 'lamp'),
    '研究所建材の設計図': ('blueprint_lab_building', 'Laboratory Material Blueprint',
                         {'lab:lab_supplies': 0.35, 'lab:lab_research': 0.15}, 'grid'),
    '研究所の調度品の設計図': ('blueprint_lab_furniture', 'Laboratory Furnishing Blueprint',
                           {'lab:lab_office': 0.35, 'lab:lab_quarters': 0.20, 'lab:lab_kitchen': 0.35}, 'monitor'),
    'レイリー散乱パネルの設計図': ('blueprint_rayleigh', 'Rayleigh Sky Panel Blueprint',
                               {'lab:lab_research': 0.30}, 'sky'),
}

DESCRIPTIONS = {
    '遺構パネルの設計図': ('遺構の外壁を再現する図面。', 'Plans for recreating ruin walls.'),
    '無垢の遺構建材の設計図': ('傷のない遺構建材の図面。', 'Plans for pristine ruin materials.'),
    '遺構ガラスの設計図': ('遺構のガラスを再現する図面。', 'Plans for recreating ruin glass.'),
    '照明の設計図': ('旧文明の照明器具の図面。', 'Plans for ancient lighting fixtures.'),
    '研究所建材の設計図': ('研究所の壁や床の図面。', 'Plans for laboratory walls and floors.'),
    '研究所の調度品の設計図': ('旧文明の家具や設備の図面。', 'Plans for ancient furniture and fixtures.'),
    'レイリー散乱パネルの設計図': ('人工の青空を映すパネルの図面。', 'Plans for an artificial blue-sky panel.'),
}

# 設計図の紙の色（青焼き）と線の色
PAPER, PAPER_DARK, LINE, LINE_DIM = (44, 92, 168), (30, 64, 124), (226, 238, 252), (120, 160, 214)


def icon(glyph):
    """設計図の絵: 青焼きの紙（右下が少しめくれる）に方眼と、作れるものの印（白い線）。"""
    im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rectangle([1, 2, 14, 14], fill=PAPER, outline=PAPER_DARK)
    d.polygon([(14, 11), (14, 14), (11, 14)], fill=(0, 0, 0, 0))       # めくれた角
    d.polygon([(11, 14), (14, 11), (11, 11)], fill=(186, 206, 236))
    for k in (5, 8, 11):
        d.line([(2, k), (13, k)], fill=LINE_DIM)
        d.line([(k - 1, 3), (k - 1, 13)], fill=LINE_DIM)
    w = LINE
    if glyph == 'panel':                                                 # 縁取りした板と、ひび
        d.rectangle([4, 4, 10, 10], outline=w)
        d.line([(6, 6), (8, 8)], fill=w)
    elif glyph == 'pillar':                                              # 柱
        d.rectangle([6, 4, 8, 11], outline=w)
        d.line([(5, 4), (9, 4)], fill=w)
        d.line([(5, 11), (9, 11)], fill=w)
    elif glyph == 'glass':                                               # 窓と光の筋
        d.rectangle([4, 4, 10, 10], outline=w)
        d.line([(5, 8), (8, 5)], fill=w)
    elif glyph == 'lamp':                                                # 灯と光の線
        d.rectangle([6, 6, 8, 8], fill=w)
        for x, y in ((7, 3), (7, 11), (3, 7), (11, 7)):
            d.point((x, y), fill=w)
    elif glyph == 'grid':                                                # 床タイルの格子
        d.rectangle([4, 4, 10, 10], outline=w)
        d.line([(7, 4), (7, 10)], fill=w)
        d.line([(4, 7), (10, 7)], fill=w)
    elif glyph == 'monitor':                                             # 机のモニター
        d.rectangle([4, 4, 10, 8], outline=w)
        d.line([(7, 9), (7, 10)], fill=w)
        d.line([(5, 11), (9, 11)], fill=w)
    elif glyph == 'sky':                                                 # 天井の空（下に光）
        d.rectangle([3, 4, 11, 6], fill=(150, 200, 250), outline=w)
        for x in (4, 7, 10):
            d.line([(x, 8), (x, 10)], fill=w)
    return im


def item_textures(ti, save):
    for name, (iid, _, _, glyph) in BLUEPRINTS.items():
        save(icon(glyph), ti / f'{iid}.png')


def loot_pools(kind):
    """収納の種類（'ruin:observation_post' など）に入れる、設計図の引き（1枚ずつ、確率で）。"""
    pools = []
    for name, (iid, _, where, _) in BLUEPRINTS.items():
        p = where.get(kind)
        if p:
            weight = round(p * 100)
            pools.append({'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': f'singulo:{iid}', 'weight': weight},
                                                  {'type': 'minecraft:empty', 'weight': 100 - weight}]})
    return pools
