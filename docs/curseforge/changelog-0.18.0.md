# Singulo 0.18.0 — Minecraft 1.21.1 / NeoForge

## Features

- Androids now have five decoration slots, all usable at once: head, face, neck/back, waist and hand. Glasses move to the face slot and the waist ribbon to the waist slot, so they can be worn with a hat or a cape. Androids from earlier versions move them over automatically when those slots are free.
- Add ten android decorations, all made on a crafting table and dyeable:
  - Head: Cat Ear Headband, Witch Hat, Straw Hat, Hair Extension (Ponytail), Hair Extension (Twin Tails). Hair extensions take the android's own hair color; dyeing them changes the hair tie.
  - Face: Monocle.
  - Neck/back: Fairy Wings, which flutter slowly.
  - Waist: Apron.
  - Hand (held while idle): Lantern, whose flame looks bright in the dark, and Broom.
- Add a Necktie and four diamond decorations: Diamond Necklace (neck/back), Diamond Earrings (face), Diamond Tiara (head) and Diamond Brooch (waist). Dyeing them changes the metal; the diamonds stay and sparkle now and then.
- With Mekanism installed, the back of a linked QIO Dashboard placed against an Android Work Console (or its supply and delivery ports) works like an adjacent chest: androids count, take and store items in that QIO network.
- SMES Cells, SMES Modules and the Nuclear Pasta Accumulator get charge slots in their screens. Put in anything that takes power (Singulo's gauntlets, scanner and charger, or other mods' tools and batteries); it charges from the stored power and moves to the output slot when full. Hoppers can feed SMES charge slots and take out charged items. Sneak-clicking the blocks still works.
- Add the Food Printer, Food Cartridges and five kinds of printed space food: Nutrient Bar, Layered Steak, Spiral Pasta, Honeycomb Cake and Fruit Gel Cube. Stock each nutrient row with ingredients and it keeps the cartridges topped up with protein, carbohydrate or sweet paste, whatever runs short first, and the printer builds the food you pick layer by layer inside its glass chamber on power. Some foods give a short effect.
- Add Cover Frames for every cable and pipe. Fit a frame to a cable or pipe, then set a real block into it (any full, solid, opaque block such as stone, planks or logs, which keep the facing they would be placed with) to make that spot look like the block. An empty frame lets light through. Frames can also be placed on their own and filled with a cable or pipe later. Connections and flow stay the same. Breaking a covered cable takes off the block, then the frame, and leaves the cable; sneak-right-clicking with the Relocation Wrench in relocate mode does the same straight into your inventory.
- Add the RGB Dye Station. Put red, green, blue, white and black dyes in it to make ink, pick any color on its pixel color wheel and brightness bar or type a color code, and dye anything dyeable, such as leather armor or android decorations. The ink each dye uses depends on the color.
- Android decorations look less flat: they get a worn texture, and hair, straw and bristles get fine strands. Hair extensions, the straw hat, the broom and the fairy wings (now a floating hologram) were reworked.

## Fixes

- Fix a startup crash with Celestial Canvas by composing sky-darkening changes instead of redirecting the same call twice.
- QIO automation respects the dashboard's effective security mode: only public dashboards allow access. First-tick insertions no longer double-count stock, and unused QIO handlers no longer keep worlds alive.
- Large multi-tank fluid inventories no longer overflow when their total exceeds the signed 32-bit limit.
- The Android Work Console and its supply ports no longer count items, fluids or chemicals they cannot take out (such as cartridges in a Food Printer or a machine's input slots and tanks) as stock, so androids do not plan around them and get stuck.

## 追加

- アンドロイドの装飾の枠が、頭・顔・首/背中・腰・手の5つになり、同時に着けられるようになりました。メガネは顔、腰リボンは腰の枠に移ったので、帽子やマントと一緒に着けられます。前の版のアンドロイドは、その枠が空いていれば自動で移します。
- アンドロイドの装飾を10種類追加しました。どれも作業台で作り、染料で染められます。
  - 頭：猫耳カチューシャ、魔女の帽子、麦わら帽子、付け毛（ポニーテール）、付け毛（ツインテール）。付け毛の髪はそのアンドロイド自身の髪の色になり、染めると髪留めの色が変わります。
  - 顔：モノクル。
  - 首/背中：妖精の羽（ゆっくり羽ばたきます）。
  - 腰：エプロン。
  - 手（待機している間に持つ）：ランタン（中の火が暗い所でも明るく見えます）、ほうき。
- ネクタイと、ダイヤの装飾4種を追加しました：ダイヤのネックレス（首/背中）・ダイヤのイヤリング（顔）・ダイヤのティアラ（頭）・ダイヤのブローチ（腰）。染めると金具の色が変わり、ダイヤはそのままで、ときどききらりと光ります。
- Mekanism を入れているとき、リンク済みの QIO ダッシュボードの裏をアンドロイド作業指令台（資材口・納品口も）に接すると、隣のチェストと同じように使えるようにしました。アンドロイドがその QIO の中身を数え、取り出し、しまいます。
- SMESセル・SMESモジュール・核パスタ積層蓄電庫の画面に充電の枠を付けました。電力を受け取れる物（このMODのガントレット・スキャナー・充電器、ほかのMODの道具や電池）を入れると蓄えた電力で充電し、満タンになると出てくる枠へ移ります。SMES はホッパーなどでも出し入れできます。スニークして右クリックで充電する方法もそのまま使えます。
- フードプリンター・フードカートリッジと、5種類の印刷された宇宙食（栄養バー・積層ステーキ・らせんパスタ・ハニカムケーキ・フルーツゼリーキューブ）を追加しました。タンパク質・炭水化物・甘味それぞれの枠に食材をまとめて入れておくと、足りないタンパク質・炭水化物・甘味のペーストからカートリッジに補われ、選んだ食品をガラスの印刷室で電力を使って1層ずつ印刷します。少しの間効き目のある食品もあります。
- すべてのケーブル・パイプに使えるカバーの枠を追加しました。ケーブルやパイプに枠を取り付け、本物のブロック（石・木材・丸太など、すき間のない不透明な1ブロック。向きのあるブロックは、ふつうに置いたときの向きになります）をはめ込むと、その場所がそのブロックの見た目になります。枠だけの間は光を通します。枠だけを先に置いて、あとからケーブル・パイプを入れることもできます。つながりと送る量は変わりません。カバーを付けたケーブルを壊すと、ケーブルは残してブロック → 枠の順に外れます（移設レンチ（装置の移設モード）でスニークして右クリックすると、持ち物に戻ります）。
- RGB染色台を追加しました。赤・緑・青・白・黒の染料を入れてインクにし、ドットの色の輪と明るさのつまみ、またはカラーコードの入力で好きな色を選んで、染められる物（革の防具・アンドロイドの装飾など）を染めます。使うインクは色によって変わります。
- アンドロイドの装飾がのっぺりしないよう、使い込んだ模様（汚し）を入れ、髪・麦わら・穂には細い筋を入れました。付け毛・麦わら帽子・ほうき・妖精の羽（浮かぶホログラムに）を作り直しました。

## 修正

- Celestial Canvasと同時に導入したとき、空の明るさの処理が競合して起動できない問題を修正しました。
- QIO連携は実効セキュリティ設定が公開のダッシュボードだけを利用します。アイテム投入直後の在庫の二重計上と、使い終わったQIO連携がワールドを保持し続ける問題を修正しました。
- 同じ流体を持つ複数タンクの合計が約21億mBを超えても、量が整数あふれで誤って数えられないようにしました。
- アンドロイド作業指令台と資材口が、取り出せない物・液体・化学物質（フードプリンターのカートリッジ、装置の入れる欄やタンクなど）を在庫に数えないようにしました。取れない物を当てにして作業が止まることがなくなります。
