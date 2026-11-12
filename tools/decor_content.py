# -*- coding: utf-8 -*-
"""0.18.0: アンドロイドの装飾の追加（装飾の枠を頭・顔・首/背中・腰・手の5つにした）。
名前は ids.py、絵は androids.py の accessory_icons、着けたときの形は AndroidRenderer.Accessories。
レシピはどれも作業台の形を決めたレシピで、一覧の番号がずれないよう recipes.py の最後に足す。
"""

DYE = 'アンドロイドの装飾。染料で染められる'


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


_DYE_JA = '染料と一緒に作業台で染められる。'
_DYE_EN = 'Dye it with dyes on a crafting table.'

DESC = {
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
