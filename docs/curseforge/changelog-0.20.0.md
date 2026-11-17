# Singulo 0.20.0 — Minecraft 1.21.1 / NeoForge

## Features

- Add stairs, slabs and walls for ten building blocks: White and Black Reinforced Panels, Ruin Panels (plain, cracked, mossy and tiled), Pristine Ruin Panels and Tiles, and Lab Panels and Floor Tiles. Craft them in the vanilla shapes or on a stonecutter. Ruins and laboratories generated from this version on use them, and ruin stairs and slabs crack and gather moss like the panels. White Glass Panels and Black Reinforced Glass get translucent stairs.
- Add stairs for Android Gate Glass and Android Barrier Glass, plain and patterned. They keep the rule of their glass: only androids pass gate glass, and only androids are stopped by barrier glass.
- Add the Glowing Black Reinforced Panel, a Black Reinforced Panel that gives off light (level 15). Eight panels around glowstone dust make eight.
- Add the Cat Tail, an android decoration for the waist slot that sways behind the android. It can be dyed.
- Add the Fishing Upgrade and the Farming Upgrade for androids. Procedure cards gain a Fish step (fish in an area of water until enough is caught) and a Farm step (till soil and plant the supplied seeds, or give bone meal to growing crops). Both tool slots now accept any of the Mining, Felling, Fishing and Farming Upgrades.
- Players on the owner's vanilla team (/team) can use android stations, androids, drone stations, drone ports and work consoles without being registered on a Shield Permit. The server setting `shareWithTeam` turns this off.
- Add the client settings `flashIntensity` and `screenShake` to soften every large flash and screen shake, including bosses, the Genesis Core and the Accretion Jet Forge.
- JEI shows how to use items that go into machine slots rather than recipes, such as the collectors, the singularity seed, coolant cartridges and machine upgrades.

## Changes

- Reorder the creative inventory and JEI by function and progression. Machines come before materials, each multiblock controller is followed by its parts, and android and drone kits, memories, upgrades and decorations stay with their devices.
- Restored salvage is listed before the matching degraded items, awakening cores follow the order the bosses are fought, and the Last Archivist's chapters, record and halo follow them.
- The Unstable Wormhole Mouth and boss spawn eggs no longer appear in the creative inventory. Boss spawn eggs are available only through commands.
- The Body Capsule recipe now follows its shape: a lab frame canopy with the blueprint, glass around a white ceramic body, and a lab frame base. Its inventory icon fills the slot.
- Kitchen chests and barrels in the Underground Android Laboratory cafeteria now also hold printed space food and vending machine and coffee maker drinks.
- The four-pointed star on Starred White Glass Panels and Starred Black Reinforced Glass is now symmetric about the centre, more star-like and more translucent.
- Glass blocks and stairs of the same glass no longer draw the faces where they meet. Plain android glass and its stairs look like one connected pane.
- Compressed Blocks and Compressed Metal Blocks have a new look: a block clamped by corner brackets, with lights on the lower edge that count the level.
- Neutrino Sensitivity Modules have their own icon, an upright cartridge marked with ν, so they no longer look like the Gravity Field Stabilizer and the collectors.
- Bosses ignore harmful effects. Both Instant Damage and Instant Health heal them, whether splashed, lingering, on arrows or applied by command.

- Registry IDs are made consistent ahead of 1.0.0. Existing worlds keep their items, placed blocks, machine contents and selected recipes; old IDs load as the new ones. Datapacks, scripts and commands that name the old IDs need updating:
  - Items: `record_fragment` → `crystal_memory`, `bh_container` → `empty_black_hole_vessel`, `micro_black_hole` → `micro_black_hole_vessel`, `scanner_module_2` / `scanner_module_3` → `neutrino_scanner_module_2` / `neutrino_scanner_module_3`
  - Blocks: `ruin_glass` → `broken_ruin_glass`, `core_controller` → `reactor_core_controller`, `gyro_drive` → `reactor_gyro_drive`, `extraction_port` → `reactor_extraction_port`, `reactor_core` → `lab_reactor_core`, `reactor_console` → `lab_reactor_console`, `mirror_plate` → `casimir_cavity_mirror_plate`, `focusing_magnet` → `accelerator_focusing_magnet`, `heat_exchange_core` → `cooling_tower_heat_exchange_core`, `spindle_cradle` → `tidal_spindle_cradle`, `worldline_anchor_small` → `worldline_anchor`, `worldline_anchor_advanced` → `advanced_worldline_anchor`
  - Recipes named after these items follow the new IDs. Block entity types now match their block IDs.
- The English name of the Awakening Record Core is now Awakened Record Core, matching the other awakened cores.
- Release candidates can be published as `X.Y.Z-rcN` (for example `1.0.0-rc1`); they are released on the Beta channel.

## Fixes

- Androids no longer avoid Android Gate Glass when finding a path, try to climb onto it, or jump while walking through it.
- The Last Archivist's wing bones no longer flicker. They are now shaded, slightly translucent bones with joints.
- Glowing beams no longer alternate between bright and dark faces as the camera moves.

## 追加

- ホワイトパネル・黒色強化パネル・遺構パネル（無地・ひび割れ・苔むし・タイル）・無垢の遺構パネルとタイル・研究所の白いパネルと床タイルの、階段・ハーフブロック・塀を追加しました。作業台でバニラと同じ形に並べるか、石切台で作れます。この版から新しく生まれる遺構と研究所ではこれらが置かれ、遺構の階段とハーフブロックもパネルと同じようにひび割れ、苔むします。ホワイトガラスパネルと黒色強化ガラスには、透ける階段を追加しました。
- アンドロイド透過ガラスとアンドロイド遮断ガラス（無地・模様入り）の階段を追加しました。元のガラスと同じく、透過ガラスの階段はアンドロイドだけが通り抜け、遮断ガラスの階段はアンドロイドだけが通れません。
- 発光黒色強化パネルを追加しました。黒色強化パネルと同じ硬さで、明るく光ります（明るさ15）。黒色強化パネル8枚でグロウストーンダストを囲むと8枚できます。
- アンドロイドの装飾に、腰の後ろで揺れる猫のしっぽを追加しました。腰の枠に着け、染料で染められます。
- アンドロイドの釣りアップグレードと農業アップグレードを追加しました。手順カードに、釣り（範囲の水で、そろうまで釣る）と畑づくり（土を耕して持ってきた種・苗を植える、骨粉なら育ちかけの作物に与える）の手順が加わります。2つの道具の枠には、採掘・伐採・釣り・農業のどのアップグレードでも積めるようにしました。
- 持ち主と同じバニラのチーム（/team）の人は、シールド許可証に登録しなくても、アンドロイドの組立ステーション・アンドロイド・ドローンのステーションと発着ポート・作業指令台を使えるようにしました。サーバー設定 `shareWithTeam` で切れます。
- ボス・創世炉・降着ジェット鍛造炉などの大きな閃光と画面の揺れをまとめて弱めるクライアント設定 `flashIntensity`・`screenShake` を追加しました。
- コレクターや特異点の種、冷媒カートリッジ、装置のアップグレードなど、レシピではなく装置の枠に入れて使う物の使い方を、JEI に表示するようにしました。

## 変更

- クリエイティブタブと JEI の並びを、機能ごと・進行順に整理しました。機械を素材より先に置き、マルチブロックはコントローラーの直後に部材を並べ、アンドロイドとドローンのキット・メモリー・アップグレード・装飾をそれぞれの設備と一緒にまとめました。
- 復元品のあとに対応する劣化品を同じ順に並べ、覚醒の核はボスを倒す順に、そのあとに最後の記録者の断章・終焉の記録・光輪を置きました。
- 不安定なワームホールの口とボスのスポーンエッグをクリエイティブタブから外しました。ボスのスポーンエッグはコマンドでのみ入手できます。
- 素体カプセルのレシピを形に合わせ、構造材の天蓋と設計図、ガラスに囲まれた白いセラミックの素体、構造材の台座にしました。持ち物欄のアイコンも欄いっぱいの大きさにしました。
- 地下アンドロイド研究所の食堂の厨房の箱と樽に、フードプリンターの印刷食品と、自動販売機・コーヒーメーカーの飲み物も入るようにしました。
- 四芒星ホワイトガラスパネルと四芒星黒色強化ガラスの星の模様を、中心について対称にし、より星らしく、透けるようにしました。
- 同じガラスのブロックと階段が接する面は描かないようにしました。無地のアンドロイド用ガラスとその階段は、つながった1枚のガラスに見えます。
- 圧縮ブロックと金属圧縮ブロックの見た目を、角金具で締めた塊に変えました。下の縁の灯りの数でレベルが分かります。
- ニュートリノ感度モジュールのアイコンを、ν の字が入った縦長のカートリッジに変えました。重力場安定化ユニットやコレクターと見分けられます。
- ボスは悪い効果を受けなくなりました。負傷と即時回復のポーションは、投げても、残留でも、矢でも、コマンドでも、どちらもボスを回復します。

- 1.0.0 に向けて登録 ID の表記揺れをそろえました。既存のワールドのアイテム・置いたブロック・機械の中身・選んでいたレシピはそのまま引き継がれ、古い ID は新しい ID として読み込まれます。古い ID を書いたデータパック・スクリプト・コマンドは書き換えが必要です（変更の一覧は英語の項目を参照）。
- 覚醒の記録核の英語名を、ほかの覚醒の核にそろえて Awakened Record Core にしました。
- 公開候補を `X.Y.Z-rcN`（例: `1.0.0-rc1`）として公開できるようにしました。Beta として公開されます。

## 修正

- アンドロイドが道を探すときにアンドロイド透過ガラスを避けたり、上に登ろうとしたり、通り抜けるときにジャンプしたりする問題を直しました。
- 最後の記録者の翼の骨がちらつかないようにしました。骨は陰影のある、少し透ける関節つきの骨になりました。
- 光の帯が、視点を動かすと明るい面と暗い面で入れ替わって見える問題を直しました。
