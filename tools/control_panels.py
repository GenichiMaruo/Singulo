"""中央制御室の焼付塗装鋼板、アナログ計器、操作卓。モデルの正面は北。"""
import math
from PIL import Image, ImageDraw

from lab_blocks import FACING_Y, _orientable, _rot

STEEL = '#647775'
EDGE = '#324445'
ENAMEL = '#b5c4b5'
INK = '#243334'
FACE = '#e6dec0'
TEAL = '#66c7b6'
AMBER = '#eeb765'


def plate(color, rivets=True):
    """16×16 の焼付塗装鋼板: 暗い縁と、左上の明るい面取り。rivets で四隅の留め具。"""
    image = Image.new('RGB', (16, 16), color)
    d = ImageDraw.Draw(image)
    d.rectangle((0, 0, 15, 15), outline=EDGE)
    d.line((1, 14, 1, 1, 14, 1), fill='#c9d2c4')
    if rivets:
        d.point([(2, 2), (13, 2), (2, 13), (13, 13)], fill=INK)
    return image, d


def textures(directory, save):
    def store(name, image):
        save(image.convert('RGBA'), directory / (name + '.png'))

    # 計器盤の筐体: 扉の枠、取っ手、下の通気口
    im, d = plate(ENAMEL, rivets=False)
    d.rectangle((2, 2, 13, 13), outline='#8fa198')
    d.line((11, 5, 11, 7), fill=EDGE)
    for y in (10, 12):
        d.line((4, y, 8, y), fill=STEEL)
    store('control_cabinet', im)

    # 冷却管の外板: 上下2本の締め帯と、帯の両端のボルト
    im, d = plate('#547e7a', rivets=False)
    for y in (3, 11):
        d.rectangle((0, y, 15, y + 1), fill='#456160')
        d.line((0, y, 15, y), fill='#91a8a0')
        d.point([(2, y + 1), (13, y + 1)], fill=INK)
    d.rectangle((0, 0, 15, 15), outline=EDGE)
    store('coolant_cladding', im)

    # 指針計器: 黒い枠の白い文字盤、目盛り（右端は赤）、赤い指針。下の銘板の点の数が系統（channel）
    im, d = plate(ENAMEL, rivets=False)
    d.rectangle((2, 2, 13, 11), fill=INK)
    d.rectangle((3, 3, 12, 10), fill=FACE)
    d.point([(2, 2), (13, 2), (2, 11), (13, 11)], fill=ENAMEL)
    d.point([(4, 8), (4, 6), (5, 4), (7, 4), (9, 4)], fill=INK)
    d.point([(11, 5), (12, 7)], fill='#aa5540')
    d.rectangle((5, 13, 10, 14), fill=STEEL)
    for channel, needle in enumerate(((6, 7), (8, 6), (10, 7))):
        gauge = im.copy()
        dial = ImageDraw.Draw(gauge)
        dial.line((8, 9, *needle), fill='#a33d2d')
        dial.point((8, 9), fill=INK)
        for k in range(channel + 1):
            dial.point((6 + 2 * k, 14), fill=FACE)
        store('dial_panel' if channel == 0 else f'dial_panel_{channel}', gauge)

    # 系統図: 往路（水色、y=5）と戻り（琥珀、y=12）の線が隣の盤へ続く。真ん中にポンプ・熱交換器・ヒーター
    im, d = plate('#324c50', rivets=False)
    d.line((0, 5, 4, 5, 4, 9, 11, 9, 11, 5, 15, 5), fill=TEAL)
    d.line((0, 12, 2, 12, 2, 3, 13, 3, 13, 12, 15, 12), fill=AMBER)
    d.rectangle((6, 8, 9, 11), fill=INK, outline=TEAL)
    d.point((7, 9), fill=TEAL)
    d.rectangle((9, 1, 10, 3), fill=INK, outline=AMBER)
    store('mimic_panel', im)

    for channel in (1, 2):
        im, d = plate('#324c50', rivets=False)
        d.line((0, 5, 15, 5), fill=TEAL)
        d.line((0, 12, 15, 12), fill=AMBER)
        if channel == 1:
            d.rectangle((4, 2, 11, 9), fill=INK, outline=TEAL)
            d.point([(4, 2), (11, 2), (4, 9), (11, 9)], fill='#324c50')
            for x in (6, 9):
                d.line((x, 4, x, 7), fill=FACE)
            d.line((8, 10, 8, 12), fill=AMBER)
        else:
            d.rectangle((3, 3, 12, 8), fill=INK, outline=AMBER)
            d.point([(4, 6), (5, 6), (6, 5), (7, 6), (8, 5), (9, 6), (10, 5), (11, 6)], fill=TEAL)
            d.line((8, 9, 8, 12), fill=AMBER)
        store(f'mimic_panel_{channel}', im)

    # 警報窓: 3列×4段の小窓（消えた灯と、いくつか琥珀・赤に点いた灯）
    im, d = plate(EDGE, rivets=False)
    lamps = ('#819590', '#90baa5', '#dbba7c', '#819590', '#a0bbaa', '#d69a60',
             '#7a938e', '#abb8a3', '#899e95', '#b98b77', '#899e95', '#a0ad9e')
    for index, color in enumerate(lamps):
        x, y = 2 + 4 * (index % 3), 2 + 3 * (index // 3)
        d.rectangle((x, y, x + 2, y + 1), fill=color)
    store('annunciator_panel', im)

    # 操作卓の天板: 上に表示灯、真ん中にトグルスイッチ、手前に小さな計器
    im, d = plate(ENAMEL, rivets=False)
    d.line((3, 2, 4, 2), fill=TEAL)
    d.line((11, 2, 12, 2), fill='#b0604e')
    for x in (3, 7, 11):
        d.rectangle((x, 4, x + 1, 5), fill=STEEL)
        d.line((x, 5, x, 7), fill=INK)
        d.rectangle((x - 1, 9, x + 2, 13), fill=EDGE)
        d.rectangle((x, 10, x + 1, 12), fill=FACE)
        d.point((x + 1, 10), fill=INK)
    store('operator_switches', im)

    # 椅子の布: 縫い目の筋
    im, d = plate('#395153', rivets=False)
    for y in (5, 10):
        d.line((2, y, 13, y), fill='#4c6666')
    store('operator_upholstery', im)


def models(write, assets):
    mb, mi, bs = assets/'models/block', assets/'models/item', assets/'blockstates'
    prefix = 'singulo:block/'
    for name in ('control_cabinet', 'coolant_cladding'):
        write(mb/(name+'.json'), {'parent':'minecraft:block/cube_all','textures':{'all':prefix+name}})
        write(bs/(name+'.json'), {'variants':{'':{'model':prefix+name}}})
        write(mi/(name+'.json'), {'parent':prefix+name})

    def element(start, end, surface):
        return {'from':start,'to':end,'faces':{face:{'texture':'#'+surface} for face in ('up','down','north','south','west','east')}}

    deck = element([0, 11, 1], [16, 13, 15], 'cabinet')
    deck['rotation'] = {'origin':[8,12,8], 'axis':'x','angle':-22.5}
    deck['faces']['up'] = {'texture':'#switches','uv':[0,0,16,16]}
    furniture = {
        'operator_desk':[element([1,0,3],[15,2,15],'steel'), element([1,2,4],[15,12,15],'cabinet'), deck],
        'operator_chair':[element([2,0,6],[14,2,10],'steel'), element([6,0,2],[10,2,14],'steel'),
                          element([6,2,6],[10,7,10],'steel'), element([2,7,2],[14,10,14],'cloth'),
                          element([3,10,12],[13,16,14],'cloth')]}
    for name in ('dial_panel', 'mimic_panel', 'annunciator_panel', *furniture):
        if name in furniture:
            model = {'parent':'minecraft:block/block','textures':{'particle':prefix+'control_cabinet',
                     'steel':prefix+'lab_frame','cabinet':prefix+'control_cabinet','switches':prefix+'operator_switches',
                     'cloth':prefix+'operator_upholstery'},'elements':furniture[name]}
        else:
            model = _orientable(name, 'control_cabinet', 'control_cabinet')
        write(mb/(name+'.json'), model)
        if name in ('mimic_panel', 'dial_panel'):
            variants = {}
            for channel in range(3):
                part = name if channel == 0 else f'{name}_{channel}'
                if channel:
                    write(mb/(part+'.json'), _orientable(part, 'control_cabinet', 'control_cabinet'))
                for facing, angle in FACING_Y.items():
                    variants[f'channel={channel},facing={facing}'] = _rot(part, angle)
        else:
            variants = {f'facing={f}':_rot(name,y) for f,y in FACING_Y.items()}
        write(bs/(name+'.json'), {'variants':variants})
        write(mi/(name+'.json'), {'parent':prefix+name})
