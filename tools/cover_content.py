# -*- coding: utf-8 -*-
"""0.18.0: ケーブル・パイプのカバー（カバーの枠）。

カバーの枠をケーブル・パイプ（全8種類）に右クリックで取り付け、枠に本物のブロックを右クリックではめ込むと、その場所がそのブロックの
見た目になる（見た目は、はめ込んだブロックそのもの）。つながりと送る量は変わらない。移設レンチ（装置の移設モード）でスニークして
右クリックすると、ブロック → 枠の順に外す。はめ込めるのは、すき間のない不透明な1ブロック（向きがあってもよく、その場所に
ふつうに置いたときの向きになる）。枠だけの間は、ガラスのように光を通す。持ったときの枠は、ブロックと同じ3Dのモデル。
決まりと動きは Java の cable.ConduitCover、描き方は client.CoverModel。ここは ID・レシピ・文言・説明・ハンドブック・枠の絵とモデル、
電線・パイプのブロックの状態のファイルに、枠の部品を足すこと。レシピは一覧の番号がずれないよう recipes.py の最後に足す。
"""
import json
from pathlib import Path

MODID = 'singulo'

IDS = {
    'カバーの枠': ('cover_frame', 'Cover Frame', 'tool'),
}

# 枠を足す電線・パイプ（全種類）
CONDUITS = ('copper_wire', 'superconducting_cable', 'topological_wire', 'horizon_bus',
            'fluid_pipe', 'vacuum_jacketed_pipe', 'superfluid_conduit', 'horizon_duct')


def recipes(R, shaped):
    # カバーの枠: 四隅の鉄インゴットを鉄格子でつないだ、中の空いた枠（8個できる）
    R('カバーの枠', 1, '作業台', {'鉄インゴット': 4, '鉄格子': 4}, out=8, cat='tool',
      note='ケーブル・パイプに取り付け、ブロックをはめ込んで、そのブロックの見た目にする')
    shaped['カバーの枠'] = (('IBI', 'B B', 'IBI'), {'I': '鉄インゴット', 'B': '鉄格子'})


UI = {
    'message.singulo.cover.not_allowed': ('%sははめ込めない（すき間のない不透明な1ブロックだけ）',
                                          "%s can't be set in a cover (only full, solid, opaque blocks)"),
    'block.singulo.cover_frame': ('カバーの枠', 'Cover Frame'),
    'tooltip.singulo.cover_frame.fit': ('ケーブル・パイプを右クリックで取り付ける。ほかの場所には枠だけを置け、あとからケーブル・パイプを入れられる',
                                        'Right-click a cable or pipe to fit it. Elsewhere it places as an empty frame you can put a cable or pipe into later'),
    'tooltip.singulo.cover_frame.inlay': ('取り付けた枠をブロックで右クリックすると、はめ込んでその見た目になる',
                                          'Right-click the fitted frame with a block to set it in and take its look'),
    'tooltip.singulo.cover_frame.remove': ('壊すと、ケーブルは残してブロック → 枠の順に外れる',
                                           'Breaking it takes off the block, then the frame, and leaves the cable'),
}

CHAPTER = ('cover', 'singulo:cover_frame', 'ケーブルのカバー', 'Cable Covers', [
    ('cover', 'カバーの枠',
     'カバーの枠は、ケーブルやパイプを、ほかのブロックの見た目にする枠です。作業台で、鉄インゴット4つと鉄格子4つから8個作ります。\n'
     '枠を持ってケーブルやパイプ（全種類）を右クリックすると、枠が取り付きます。取り付けた枠を、石や木材などのブロックで右クリックすると、'
     'そのブロックを1つはめ込み、その場所がはめ込んだブロックの見た目になります。見た目は、はめ込んだ本物のブロックそのものです。'
     '向きのあるブロックは、その場所にふつうに置いたときと同じ向きになります（丸太は右クリックした面の向き、くり抜かれたカボチャは顔がこちらを向きます）。\n'
     'カバーを付けても、つながりや送れる量は変わりません。当たり判定は1ブロックいっぱいになります。枠だけの間はガラスのように光を通し、ブロックをはめ込むと、ふつうのブロックと同じように光をさえぎります。'
     'はめ込めるのは、すき間のない不透明な1ブロックです（ガラス・葉・階段・ハーフブロック・チェストやかまどのような中に物を持つブロックは使えません）。\n'
     '外すときは、カバーを付けたケーブルを壊します。ケーブルは残り、1回目ではめ込んだブロック、2回目で枠が外れて落ちます'
     '（ブロック入りのときは、はめ込んだブロックを掘る速さで壊れます）。移設レンチ（装置の移設モード）でスニークして右クリックしても、同じ順に外れて持ち物に戻ります。'
     'ブロックを替えるときは、外してから別のブロックをはめ込みます。撤去モードのレンチでは、ケーブルごと外れ、枠とブロックも一緒に戻ります。\n'
     '枠は、ケーブルのないところにも、ふつうのブロックのように置けます。置いた枠をケーブルやパイプで右クリックすると、その中にケーブルやパイプが入ります'
     '（スニークすると、ケーブルやパイプは枠の隣に置けます）。先に枠を並べておいて、あとから配線を通せます。'
     'ブロックをはめ込んでいない枠のケーブルを撤去モードのレンチで外すと、ケーブルだけが外れ、枠はその場に残ります。',
     'Cover Frame',
     'A Cover Frame makes a cable or pipe look like another block. Craft 8 on a crafting table from 4 iron ingots and 4 iron bars.\n'
     'Right-click any cable or pipe with a frame to fit it. Then right-click the fitted frame with a block such as stone or planks: '
     'one block is set in, and that spot takes its look. What you see is the real block you set in. '
     'A block that faces a direction keeps the facing it would get if placed there normally (a log follows the clicked face, a carved pumpkin faces you).\n'
     'A cover changes neither the connections nor how much flows. It collides as a full block. An empty frame lets light through like glass; once a block is set in, it blocks light like an ordinary block. '
     'Only full, solid, opaque blocks can be set in: glass, leaves, stairs, slabs, and blocks that hold items, such as chests and furnaces, cannot.\n'
     'To take a cover off, break the covered cable: the cable stays, and the first break drops the block, the second the frame '
     '(with a block set in, it breaks as fast as that block would). Sneak-right-clicking with the Relocation Wrench in relocate mode takes them off in the same order, straight into your inventory. '
     'To change the block, take it off and set in another. The wrench in cable mode removes the whole cable, returning the frame and block too.\n'
     'A frame can also be placed on its own like an ordinary block. Right-click a placed frame with a cable or pipe to put it inside '
     '(sneak to place the cable or pipe next to the frame instead), so you can lay out frames first and run the wiring later. '
     'Removing the cable from a frame with no block in it, with the wrench in cable mode, takes only the cable and leaves the frame in place.'),
])

DESC = {
    'カバーの枠': ('ケーブル・パイプに取り付け、本物のブロックをはめ込んで、その見た目にする枠。つながりと送る量は変わらない。'
               '壊すと、ケーブルは残してブロック → 枠の順に外れる。',
               'A frame fitted to a cable or pipe; set a real block into it to take that block\'s look. Connections and flow stay the same. '
               'Breaking it takes off the block, then the frame, and leaves the cable.'),
}

SUMMARY = {
    'カバーの枠': ('ケーブル・パイプをほかのブロックの見た目にする。', 'Makes a cable or pipe look like another block.'),
}


# ---------------------------------------------------------------- 絵とモデル

def frame_texture():
    """ブロックの枠の材質（16×16）: 磨いた鉄に、ふちの面取りの明暗とびょう。"""
    import surface
    im = surface.metal((170, 176, 186), 'cover-frame', grain=0.05, streak='v', scuffs=2, edge=0.14)
    px = im.load()
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14), (1, 8), (14, 8)):
        px[x, y] = (230, 234, 240, 255)
    return im


def frame_model():
    """枠のモデル: 1ブロックの12本の辺に、太さ2ドットの鉄の棒。
    ブロックの外側の面にぴったり乗る棒の面には cullface を付ける（隣にふつうのブロック・ブロック入りのカバーがあると隠れて描かない）。
    付けないと、その面は隣のブロックの中の明るさで描かれ、隣が詰まったブロックだと真っ黒になる。"""
    tex = f'{MODID}:block/cover_frame'
    outer = {'west': (0, 0), 'east': (0, 16), 'down': (1, 0), 'up': (1, 16), 'north': (2, 0), 'south': (2, 16)}

    def bar(f, t):
        faces = {}
        for d, (axis, edge) in outer.items():
            face = {'texture': '#f'}
            if (f[axis] if edge == 0 else t[axis]) == edge:
                face['cullface'] = d
            faces[d] = face
        return {'from': list(f), 'to': list(t), 'faces': faces}

    elements = []
    for x in (0, 14):
        for z in (0, 14):
            elements.append(bar((x, 0, z), (x + 2, 16, z + 2)))           # 縦の4本
    for y in (0, 14):
        for z in (0, 14):
            elements.append(bar((2, y, z), (14, y + 2, z + 2)))           # 東西の4本
        for x in (0, 14):
            elements.append(bar((x, y, 2), (x + 2, y + 2, 14)))           # 南北の4本
    return {'parent': 'minecraft:block/block', 'textures': {'particle': tex, 'f': tex}, 'elements': elements}


def models(ctx):
    """枠の絵とモデル、電線・パイプのブロックの状態に枠を足す（電線・パイプのモデルを書いたあとに呼ぶ）。"""
    import art16
    write = ctx['write_json']
    assets = Path(ctx['ASSETS'])
    art16.save(frame_texture(), assets / 'textures' / 'block' / 'cover_frame.png')
    write(assets / 'models' / 'block' / 'cover_frame.json', frame_model())
    # 持ったときも、ブロックと同じ3Dの枠（ブロックの持ち方の向き・大きさ）
    write(assets / 'models' / 'item' / 'cover_frame.json', {'parent': f'{MODID}:block/cover_frame'})
    # 置いた枠のブロック（ケーブルのない枠）: 見た目は同じ枠のモデル、壊すと枠を落とす
    write(assets / 'blockstates' / 'cover_frame.json', {'variants': {'': {'model': f'{MODID}:block/cover_frame'}}})
    loot = Path(ctx['RES']) / 'data' / MODID / 'loot_table' / 'blocks' / 'cover_frame.json'
    write(loot, {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': f'{MODID}:cover_frame'}],
                                                       'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    part = {'when': {'cover': 'frame|block'}, 'apply': {'model': f'{MODID}:block/cover_frame'}}
    for iid in CONDUITS:
        path = assets / 'blockstates' / f'{iid}.json'
        state = json.loads(path.read_text(encoding='utf-8'))
        state['multipart'] = [p for p in state['multipart'] if p != part] + [part]
        write(path, state)
