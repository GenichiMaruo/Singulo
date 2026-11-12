# 0.18.0: アンドロイドの装飾

[変更履歴](../curseforge/changelog-0.18.0.md)

## 装飾の枠を5つに

- `AndroidAccessoryItem` の部位: `HEAD=0`・`BACK=1`（首/背中）・`HAND=2`・`FACE=3`・`WAIST=4`（`PARTS=5`）。0.17 までの3枠の番号は変えず、顔と腰をうしろに足した。持ち物の装飾の枠（`RuinAndroid.SLOT_DECOR` から `DECOR_SLOTS=5`）も、持ち物の最後なので古い保存はそのまま読める。
- メガネは顔、腰リボンは腰へ。古い保存で枠に合わない装飾は、読み込んだときに、その装飾の枠が空いていれば移す（`RuinAndroid.moveDecorToItsPart`）。
- 画面（装飾のページ）: 体の並びに合わせて2列。左に上から頭・首/背中・腰、右に顔（頭のとなり）と手（腰のとなり）（`AndroidMenu.decorX/decorY`）。
- 描画（`AndroidRenderer.Accessories`）: 頭と顔は頭、首/背中は胴、腰は腰、手は左手の変形。帽子（ベレー帽・魔女の帽子・麦わら帽子）をかぶるとアホ毛をしまう。

## 追加した装飾（10種類）

| 部位 | 装飾 | 染めると変わる所 | 作業台の形 |
| --- | --- | --- | --- |
| 頭 | 猫耳カチューシャ | 全体（耳の内側は桃色のまま） | 上の左右に羊毛、下に糸3 |
| 頭 | 魔女の帽子 | 全体（帯と留め金は金） | 羊毛のとんがりとつば、つばの真ん中に金塊 |
| 頭 | 麦わら帽子 | リボン | 小麦の山とつば、下に糸 |
| 頭 | 付け毛（ポニーテール） | 髪留め（髪は型ごとの髪の色） | 羊毛の下に糸2 |
| 頭 | 付け毛（ツインテール） | 髪留め | 左右に羊毛、その下に糸2ずつ |
| 顔 | モノクル | 縁と鎖 | ガラス板のまわりに金塊4 |
| 首/背中 | 妖精の羽 | 羽（筋は白っぽく） | 左右にファントムの皮膜、下にグロウストーンダスト |
| 腰 | エプロン | 全体 | 上に糸3、下に羊毛4 |
| 手 | ランタン | 金具（火はいつも明るい） | 鎖・ガラス板2・たいまつ・鉄インゴット（バニラのランタンとは別の形） |
| 手 | ほうき | 結び目 | 上から棒・糸・小麦 |

- 付け毛の髪の色は、型ごとの髪の明るい色（`tools/androids.py` の型ごとの色）を `AndroidRenderer.Accessories.hairColor` に写した。
- 定義: 名前 `tools/ids.py`、レシピと説明 `tools/decor_content.py`（レシピ一覧の最後に足すので、既存のレシピ画像の番号は変わらない）、絵 `tools/androids.py` の `accessory_icons`、染められるタグ `data/minecraft/tags/item/dyeable.json`。
- GameTest: `androidWearsFiveAccessoriesAtOnce`（5枠に同時に着け、各枠にはその部位だけ入る）・`oldDecorMovesToTheFaceAndWaistSlots`（古い保存の移し替え）。

## RGB染色台

- `dye/RgbDyeStationBlockEntity`・`RgbDyeStationMenu`・`client/RgbDyeStationScreen`。機械（`SimpleMachineBlock`）の仕組みで、正面は色の輪の絵（`tools/decor_content.py` の `rgb_front`）。
- 赤・緑・青・白・黒の染料の枠（タグ `c:dyes/*`）。毎 tick、入る分だけインクにする（染料1個 = 8目盛り、各色64目盛りまで）。
- 1回に使うインク（各色0〜4目盛り）: 白 = 3色の最小、黒 = 1 − 3色の最大、赤・緑・青 = それぞれ − 白。純色は色みだけ、灰色は白と黒だけ。
- 画面（幅212）: 左に色の輪（32×32 マス、1マス2ドット。中心ほど白）と明るさのつまみ（16段）、右上に染める物の枠（タグ minecraft:dyeable）・「染める」・色見本（重ねると #RRGGBB）、下に5色の染料の枠とインクの目盛り（1回に使う分の印つき）。
- 色を選ぶ・染めるは画面のボタンの番号で送る（`SET_COLOR | RGB`・`APPLY`）。選んだ色は台に保存し、画面には R・G・B の3つの値で送る。
- 壊すと、枠の物と、インクを染料に戻せる分（端数は失う）を落とす。
- レシピ: 上に赤・緑・青の染料、真ん中に白の染料・作業台・黒の染料、下に板材3。
- GameTest: `dyeStationCostFollowsTheColor`・`dyeStationTurnsDyesIntoInkAndDyes`。

## 装飾の見た目の作り直し

- 模様: `textures/entity/android_decor_grain.png`（汚し）・`android_decor_strands.png`（筋）を、体の1ピクセルに1ドットの細かさで面ごとに切り出して貼る（`FxDraw.texBox`）。
- まぶたは装飾より先に描く（メガネ・モノクルのレンズの奥行きで、まぶたが隠れていた）。
- 妖精の羽はホログラム（背中から浮かぶ、奥へ3枚の半透明の層・光る縁と筋・光の粒）。

## 確認待ち

- ゲーム内の見た目: 10種類の形・大きさ・揺れ方（型ごと・待機と移動）、画面の装飾のページの並び、アイテムの絵。
- GUI の自動撮影（tools/review）の数は、装飾のページの見た目が決まってから合わせる。
