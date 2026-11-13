# -*- coding: utf-8 -*-
"""0.18.0: アンドロイドの装飾の追加（装飾の枠を頭・顔・首/背中・腰・手の5つにした）。
名前は ids.py、絵は androids.py の accessory_icons、着けたときの形は AndroidRenderer.Accessories。
レシピはどれも作業台の形を決めたレシピで、一覧の番号がずれないよう recipes.py の最後に足す。
"""

DYE = 'アンドロイドの装飾。染料で染められる'

# RGB染色台（machine。正面の絵は rgb_front）
IDS = {
    'RGB染色台': ('rgb_dye_station', 'RGB Dye Station', 'machine'),
}

UI = {
    'gui.singulo.rgb_dye_station.apply': ('染める', 'Dye'),
    'gui.singulo.rgb_dye_station.wheel': ('色みと鮮やかさ（クリックかドラッグで選ぶ）', 'Hue and saturation (click or drag)'),
    'gui.singulo.rgb_dye_station.brightness': ('明るさ', 'Brightness'),
    'gui.singulo.rgb_dye_station.cost': ('1回に使うインク', 'Ink per dye'),
    'gui.singulo.rgb_dye_station.cost.0': ('赤 %s', 'Red %s'),
    'gui.singulo.rgb_dye_station.cost.1': ('緑 %s', 'Green %s'),
    'gui.singulo.rgb_dye_station.cost.2': ('青 %s', 'Blue %s'),
    'gui.singulo.rgb_dye_station.cost.3': ('白 %s', 'White %s'),
    'gui.singulo.rgb_dye_station.cost.4': ('黒 %s', 'Black %s'),
    'gui.singulo.rgb_dye_station.ink.0': ('赤のインク %s / %s', 'Red ink %s / %s'),
    'gui.singulo.rgb_dye_station.ink.1': ('緑のインク %s / %s', 'Green ink %s / %s'),
    'gui.singulo.rgb_dye_station.ink.2': ('青のインク %s / %s', 'Blue ink %s / %s'),
    'gui.singulo.rgb_dye_station.ink.3': ('白のインク %s / %s', 'White ink %s / %s'),
    'gui.singulo.rgb_dye_station.ink.4': ('黒のインク %s / %s', 'Black ink %s / %s'),
    'gui.singulo.rgb_dye_station.no_target': ('染める物を入れる', 'Put in something to dye'),
    'gui.singulo.rgb_dye_station.hex': ('カラーコード（#RRGGBB）', 'Color code (#RRGGBB)'),
}


def rgb_front(stage):
    """RGB染色台の正面: 外装に、暗いくぼみの中のドットの色の輪（中心ほど白い）と、下に赤・緑・青の小さな染料のしずく。"""
    import colorsys
    import art16
    from art16 import rgba
    im = art16.casing(stage, 'side', seed=59)
    px = im.load()
    for y in range(2, 13):
        for x in range(2, 14):
            px[x, y] = rgba((34, 32, 40)) if 3 <= x <= 12 and 3 <= y <= 11 else rgba((70, 66, 78))
    for y in range(3, 12):
        for x in range(3, 13):
            dx, dy = x - 7.5, y - 7.0
            r = (dx * dx + dy * dy) ** 0.5
            if r <= 4.4:
                import math
                h = (math.atan2(dy, dx) / (2 * math.pi)) % 1
                cr, cg, cb = colorsys.hsv_to_rgb(h, min(1, r / 4.0), 1.0)
                px[x, y] = rgba((int(cr * 255), int(cg * 255), int(cb * 255)))
    px[6, 5] = rgba((255, 255, 255))                 # 光
    for x, c in ((5, (216, 58, 58)), (7, (76, 176, 76)), (9, (58, 98, 216))):
        px[x, 13] = rgba(c)
        px[x + 1, 13] = rgba(tuple(min(255, v + 40) for v in c))
    return im



def recipes(R, shaped):
    # 猫耳: 上の左右に羊毛の耳、下に糸のカチューシャ
    R('猫耳カチューシャ', 1, '作業台', {'羊毛': 2, '糸': 3}, cat='tool', note=DYE)
    shaped['猫耳カチューシャ'] = (('W W', 'SSS'), {'W': '羊毛', 'S': '糸'})
    # 魔女の帽子: 上へ細いとんがりと広いつば、つばの真ん中に金の留め金
    R('魔女の帽子', 1, '作業台', {'羊毛': 4, '金塊': 1}, cat='tool', note=DYE)
    shaped['魔女の帽子'] = ((' W ', ' W ', 'WGW'), {'W': '羊毛', 'G': '金塊'})
    # 麦わら帽子: 小麦の山とつば、その下に糸のリボン
    R('麦わら帽子', 1, '作業台', {'小麦': 4, '糸': 1}, cat='tool', note=DYE + '（リボンの色）')
    shaped['麦わら帽子'] = ((' H ', 'HHH', ' S '), {'H': '小麦', 'S': '糸'})
    # 付け毛: 上に羊毛の髪留め、下へ垂れる糸の房（ツインテールは左右に1本ずつ）
    R('付け毛（ポニーテール）', 1, '作業台', {'羊毛': 1, '糸': 2}, cat='tool',
      note='アンドロイドの装飾。髪はアンドロイド自身の髪の色。染料で染められる（髪留めの色）')
    shaped['付け毛（ポニーテール）'] = (('W', 'S', 'S'), {'W': '羊毛', 'S': '糸'})
    R('付け毛（ツインテール）', 1, '作業台', {'羊毛': 2, '糸': 4}, cat='tool',
      note='アンドロイドの装飾。髪はアンドロイド自身の髪の色。染料で染められる（髪留めの色）')
    shaped['付け毛（ツインテール）'] = (('W W', 'S S', 'S S'), {'W': '羊毛', 'S': '糸'})
    # モノクル: ガラス板のレンズのまわりに金の縁
    R('モノクル', 1, '作業台', {'金塊': 4, 'ガラス板': 1}, cat='tool', note=DYE + '（縁の色）')
    shaped['モノクル'] = ((' N ', 'NGN', ' N '), {'N': '金塊', 'G': 'ガラス板'})
    # 妖精の羽: 左右にファントムの皮膜の羽、真ん中の下に光る粉
    R('妖精の羽', 1, '作業台', {'ファントムの皮膜': 2, 'グロウストーンダスト': 1}, cat='tool', note=DYE)
    shaped['妖精の羽'] = (('P P', ' G '), {'P': 'ファントムの皮膜', 'G': 'グロウストーンダスト'})
    # エプロン: 上に糸の腰ひも、下に羊毛の前掛け
    R('エプロン', 1, '作業台', {'糸': 3, '羊毛': 4}, cat='tool', note=DYE)
    shaped['エプロン'] = (('SSS', 'WWW', ' W '), {'S': '糸', 'W': '羊毛'})
    # ランタン: 上に吊る鎖、左右にガラス板、真ん中にたいまつ、下に鉄の台（バニラのランタンとは別の形）
    R('ランタン', 1, '作業台', {'鎖': 1, 'ガラス板': 2, 'たいまつ': 1, '鉄インゴット': 1}, cat='tool', note=DYE + '（金具の色）')
    shaped['ランタン'] = ((' C ', 'GTG', ' I '), {'C': '鎖', 'G': 'ガラス板', 'T': 'たいまつ', 'I': '鉄インゴット'})
    # ほうき: 上から柄（棒）、結び目（糸）、穂（小麦）
    R('ほうき', 1, '作業台', {'棒': 1, '糸': 1, '小麦': 1}, cat='tool', note=DYE + '（結び目の色）')
    shaped['ほうき'] = (('R', 'S', 'H'), {'R': '棒', 'S': '糸', 'H': '小麦'})
    # RGB染色台: 上に赤・緑・青の染料、真ん中に白の染料・作業台・黒の染料、下に板材
    R('RGB染色台', 1, '作業台', {'赤色の染料': 1, '緑色の染料': 1, '青色の染料': 1, '白色の染料': 1, '黒色の染料': 1,
                              '作業台（ブロック）': 1, '板材': 3}, cat='machine',
      note='赤・緑・青・白・黒の染料をインクにして、染められる物を色の輪から選んだ好きな色に染める')
    shaped['RGB染色台'] = (('RGB', 'WCK', 'PPP'), {'R': '赤色の染料', 'G': '緑色の染料', 'B': '青色の染料', 'W': '白色の染料',
                                                  'C': '作業台（ブロック）', 'K': '黒色の染料', 'P': '板材'})
    # ネクタイ: 上に糸の結び目、下へ羊毛
    R('ネクタイ', 1, '作業台', {'糸': 1, '羊毛': 2}, cat='tool', note=DYE)
    shaped['ネクタイ'] = (('S', 'W', 'W'), {'S': '糸', 'W': '羊毛'})
    # ダイヤの装飾（染めると金具の色が変わる。ダイヤはそのまま）
    JEWEL = 'アンドロイドの装飾。染料で染められる（金具の色）'
    R('ダイヤのネックレス', 1, '作業台', {'金塊': 3, 'ダイヤモンド': 1}, cat='tool', note=JEWEL)
    shaped['ダイヤのネックレス'] = (('N N', ' N ', ' D '), {'N': '金塊', 'D': 'ダイヤモンド'})
    R('ダイヤのイヤリング', 1, '作業台', {'金塊': 2, 'ダイヤモンド': 2}, cat='tool', note=JEWEL)
    shaped['ダイヤのイヤリング'] = (('N N', 'D D'), {'N': '金塊', 'D': 'ダイヤモンド'})
    R('ダイヤのティアラ', 1, '作業台', {'金塊': 5, 'ダイヤモンド': 1}, cat='tool', note=JEWEL)
    shaped['ダイヤのティアラ'] = (('NDN', 'NNN'), {'N': '金塊', 'D': 'ダイヤモンド'})
    R('ダイヤのブローチ', 1, '作業台', {'金塊': 4, 'ダイヤモンド': 1}, cat='tool', note=JEWEL)
    shaped['ダイヤのブローチ'] = ((' N ', 'NDN', ' N '), {'N': '金塊', 'D': 'ダイヤモンド'})


_DYE_JA = '染料と一緒に作業台で染められる。'
_DYE_EN = 'Dye it with dyes on a crafting table.'

DESC = {
    'ネクタイ': ('首の結び目から胸へ下がる、斜めの縞のネクタイ。装飾のページの首・背中の枠に着ける。' + _DYE_JA,
             'A striped necktie from a knot at the collar down the chest. Goes in the neck / back slot of the decoration page. ' + _DYE_EN),
    'ダイヤのネックレス': ('首を回る細い鎖が胸元でＶに下がり、先にダイヤが光る。装飾のページの首・背中の枠に着ける。'
                   '染料と一緒に作業台で染めると、鎖と台の色が変わる（ダイヤはそのまま）。',
                   'A fine chain that dips into a V with a diamond pendant. Goes in the neck / back slot of the decoration page. '
                   'Dyeing it on a crafting table changes the chain and setting; the diamond stays.'),
    'ダイヤのイヤリング': ('両耳の下で揺れるダイヤ。装飾のページの顔の枠に着ける（メガネと同じ枠）。'
                   '染料と一緒に作業台で染めると、金具の色が変わる（ダイヤはそのまま）。',
                   'Diamonds that sway below both ears. Goes in the face slot of the decoration page, like glasses. '
                   'Dyeing it on a crafting table changes the metal; the diamonds stay.'),
    'ダイヤのティアラ': ('頭の上に乗る、3つの山にダイヤをちりばめた小さな冠。装飾のページの頭の枠に着ける。'
                  '染料と一緒に作業台で染めると、金具の色が変わる（ダイヤはそのまま）。',
                  'A small crown with three peaks set with diamonds. Goes in the head slot of the decoration page. '
                  'Dyeing it on a crafting table changes the metal; the diamonds stay.'),
    'ダイヤのブローチ': ('腰の前に留める、リボン結びの真ん中にダイヤのブローチ。装飾のページの腰の枠に着ける。'
                  '染料と一緒に作業台で染めると、リボンの色が変わる（ダイヤはそのまま）。',
                  'A bow brooch with a diamond knot, pinned on the front of the waist. Goes in the waist slot of the decoration page. '
                  'Dyeing it on a crafting table changes the bow; the diamond stays.'),
    'RGB染色台': ('赤・緑・青・白・黒の染料をそれぞれの枠に入れるとインクになり（染料1個で8目盛り、各色64目盛りまで）、'
                '画面の色の輪と明るさで選んだ好きな色に、染められる物（革の防具・アンドロイドの装飾など）を染める。'
                '1回に使うインクは選んだ色で決まり、純色なら色みのインクだけ、灰色なら白と黒だけを使う（各色4目盛りまで）。'
                '壊すと、中の染料と、インクを染料に戻せる分を落とす。',
                'Put red, green, blue, white and black dyes in their slots to turn them into ink (8 units per dye, up to 64 of each), '
                'then dye anything dyeable, such as leather armor or android decorations, any color you pick on the color wheel and '
                'brightness bar. The ink used depends on the color: a pure hue uses only its colored inks, a gray only white and black '
                '(up to 4 units of each). Breaking it drops its dyes and as many dyes as its ink can make again.'),
    '猫耳カチューシャ': ('アンドロイドの頭に着ける、猫の耳がついたカチューシャ。装飾のページの頭の枠に着ける。' + _DYE_JA,
                    'A headband with cat ears for an android. Goes in the head slot of the decoration page. ' + _DYE_EN),
    '魔女の帽子': ('先が折れたとんがり帽子。装飾のページの頭の枠に着ける。' + _DYE_JA,
               'A pointed hat with a bent tip. Goes in the head slot of the decoration page. ' + _DYE_EN),
    '麦わら帽子': ('リボンを巻いた麦わら帽子。装飾のページの頭の枠に着ける。染料と一緒に作業台で染めると、リボンの色が変わる。',
               'A straw hat with a ribbon. Goes in the head slot of the decoration page. Dyeing it on a crafting table changes the ribbon.'),
    '付け毛（ポニーテール）': ('後頭部の高い所でまとめるポニーテール。髪はアンドロイド自身の髪の色になる。装飾のページの頭の枠に着ける。'
                     '染料と一緒に作業台で染めると、髪留めの色が変わる。',
                     'A high ponytail in the android\'s own hair color. Goes in the head slot of the decoration page. '
                     'Dyeing it on a crafting table changes the hair tie.'),
    '付け毛（ツインテール）': ('頭の左右で結ぶツインテール。髪はアンドロイド自身の髪の色になる。装飾のページの頭の枠に着ける。'
                     '染料と一緒に作業台で染めると、髪留めの色が変わる。',
                     'Twin tails in the android\'s own hair color. Goes in the head slot of the decoration page. '
                     'Dyeing it on a crafting table changes the hair ties.'),
    'モノクル': ('右目にはめる、鎖のついた片眼鏡。装飾のページの顔の枠に着ける（メガネと同じ枠）。染料と一緒に作業台で染めると、縁の色が変わる。',
             'A monocle on a chain for the right eye. Goes in the face slot of the decoration page, like glasses. '
             'Dyeing it on a crafting table changes the rim.'),
    '妖精の羽': ('背中でゆっくり羽ばたく、透きとおった4枚の羽。装飾のページの首・背中の枠に着ける。' + _DYE_JA,
             'Four clear wings that flutter slowly on an android\'s back. Goes in the neck / back slot of the decoration page. ' + _DYE_EN),
    'エプロン': ('すそにフリルのついたエプロン。装飾のページの腰の枠に着ける。' + _DYE_JA,
             'An apron with a frilled hem. Goes in the waist slot of the decoration page. ' + _DYE_EN),
    'ランタン': ('待機しているアンドロイドが手から下げるランタン。中の火は暗い所でも明るく見える（まわりは照らさない）。'
             '装飾のページの手の枠に着ける。染料と一緒に作業台で染めると、金具の色が変わる。',
             'A lantern an android carries while idle. Its flame looks bright even in the dark, though it does not light the area. '
             'Goes in the hand slot of the decoration page. Dyeing it on a crafting table changes the metal frame.'),
    'ほうき': ('待機しているアンドロイドが立てて持つほうき。装飾のページの手の枠に着ける。染料と一緒に作業台で染めると、結び目の色が変わる。',
            'A broom an android holds upright while idle. Goes in the hand slot of the decoration page. '
            'Dyeing it on a crafting table changes the binding.'),
}

SUMMARY = {
    'ネクタイ': ('アンドロイドの胸元のネクタイ。', 'A necktie for an android.'),
    'ダイヤのネックレス': ('胸元にダイヤが下がるネックレス。', 'A necklace with a diamond pendant.'),
    'ダイヤのイヤリング': ('両耳に揺れるダイヤのイヤリング。', 'Diamond earrings that sway.'),
    'ダイヤのティアラ': ('ダイヤをちりばめた小さな冠。', 'A small crown set with diamonds.'),
    'ダイヤのブローチ': ('腰の前に留めるダイヤのブローチ。', 'A diamond brooch for the waist.'),
    'RGB染色台': ('色の輪から選んだ好きな色に染める台。', 'Dyes things any color you pick on a color wheel.'),
    '猫耳カチューシャ': ('アンドロイドの頭に着ける猫耳。', 'Cat ears for an android.'),
    '魔女の帽子': ('アンドロイドがかぶるとんがり帽子。', 'A pointed hat for an android.'),
    '麦わら帽子': ('アンドロイドがかぶる麦わら帽子。', 'A straw hat for an android.'),
    '付け毛（ポニーテール）': ('アンドロイドの髪をポニーテールにする。', 'Gives an android a ponytail.'),
    '付け毛（ツインテール）': ('アンドロイドの髪をツインテールにする。', 'Gives an android twin tails.'),
    'モノクル': ('アンドロイドの右目にはめる片眼鏡。', 'A monocle for an android\'s right eye.'),
    '妖精の羽': ('アンドロイドの背中で羽ばたく羽。', 'Wings that flutter on an android\'s back.'),
    'エプロン': ('アンドロイドの腰に着けるエプロン。', 'An apron for an android.'),
    'ランタン': ('待機しているアンドロイドが下げるランタン。', 'A lantern an idle android carries.'),
    'ほうき': ('待機しているアンドロイドが持つほうき。', 'A broom an idle android holds.'),
}
