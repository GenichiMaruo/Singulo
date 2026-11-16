# Singulo 0.20.0 — Minecraft 1.21.1 / NeoForge

## Changes

- Reorder the creative inventory and JEI by function and progression. Machines come before materials, each multiblock controller is followed by its parts, and android and drone kits, memories, upgrades and decorations stay with their devices.
- Restored salvage is listed before the matching degraded items, awakening cores follow the order the bosses are fought, and the Last Archivist's chapters, record and halo follow them.
- The Unstable Wormhole Mouth and boss spawn eggs no longer appear in the creative inventory. Boss spawn eggs are available only through commands.
- The Body Capsule recipe now follows its shape: a lab frame canopy with the blueprint, glass around a white ceramic body, and a lab frame base. Its inventory icon fills the slot.
- Kitchen chests and barrels in the Underground Android Laboratory cafeteria now also hold printed space food and vending machine and coffee maker drinks.
- Bosses ignore harmful effects. Both Instant Damage and Instant Health heal them, whether splashed, lingering, on arrows or applied by command.

- Registry IDs are made consistent ahead of 1.0.0. Existing worlds keep their items, placed blocks, machine contents and selected recipes; old IDs load as the new ones. Datapacks, scripts and commands that name the old IDs need updating:
  - Items: `record_fragment` → `crystal_memory`, `bh_container` → `empty_black_hole_vessel`, `micro_black_hole` → `micro_black_hole_vessel`, `scanner_module_2` / `scanner_module_3` → `neutrino_scanner_module_2` / `neutrino_scanner_module_3`
  - Blocks: `ruin_glass` → `broken_ruin_glass`, `core_controller` → `reactor_core_controller`, `gyro_drive` → `reactor_gyro_drive`, `extraction_port` → `reactor_extraction_port`, `reactor_core` → `lab_reactor_core`, `reactor_console` → `lab_reactor_console`, `mirror_plate` → `casimir_cavity_mirror_plate`, `focusing_magnet` → `accelerator_focusing_magnet`, `heat_exchange_core` → `cooling_tower_heat_exchange_core`, `spindle_cradle` → `tidal_spindle_cradle`, `worldline_anchor_small` → `worldline_anchor`, `worldline_anchor_advanced` → `advanced_worldline_anchor`
  - Recipes named after these items follow the new IDs. Block entity types now match their block IDs.
- The English name of the Awakening Record Core is now Awakened Record Core, matching the other awakened cores.
- Release candidates can be published as `X.Y.Z-rcN` (for example `1.0.0-rc1`); they are released on the Beta channel.

## Fixes

- The Last Archivist's wing bones no longer flicker. They are now shaded, slightly translucent bones with joints.
- Glowing beams no longer alternate between bright and dark faces as the camera moves.

## 変更

- クリエイティブタブと JEI の並びを、機能ごと・進行順に整理しました。機械を素材より先に置き、マルチブロックはコントローラーの直後に部材を並べ、アンドロイドとドローンのキット・メモリー・アップグレード・装飾をそれぞれの設備と一緒にまとめました。
- 復元品のあとに対応する劣化品を同じ順に並べ、覚醒の核はボスを倒す順に、そのあとに最後の記録者の断章・終焉の記録・光輪を置きました。
- 不安定なワームホールの口とボスのスポーンエッグをクリエイティブタブから外しました。ボスのスポーンエッグはコマンドでのみ入手できます。
- 素体カプセルのレシピを形に合わせ、構造材の天蓋と設計図、ガラスに囲まれた白いセラミックの素体、構造材の台座にしました。持ち物欄のアイコンも欄いっぱいの大きさにしました。
- 地下アンドロイド研究所の食堂の厨房の箱と樽に、フードプリンターの印刷食品と、自動販売機・コーヒーメーカーの飲み物も入るようにしました。
- ボスは悪い効果を受けなくなりました。負傷と即時回復のポーションは、投げても、残留でも、矢でも、コマンドでも、どちらもボスを回復します。

- 1.0.0 に向けて登録 ID の表記揺れをそろえました。既存のワールドのアイテム・置いたブロック・機械の中身・選んでいたレシピはそのまま引き継がれ、古い ID は新しい ID として読み込まれます。古い ID を書いたデータパック・スクリプト・コマンドは書き換えが必要です（変更の一覧は英語の項目を参照）。
- 覚醒の記録核の英語名を、ほかの覚醒の核にそろえて Awakened Record Core にしました。
- 公開候補を `X.Y.Z-rcN`（例: `1.0.0-rc1`）として公開できるようにしました。Beta として公開されます。

## 修正

- 最後の記録者の翼の骨がちらつかないようにしました。骨は陰影のある、少し透ける関節つきの骨になりました。
- 光の帯が、視点を動かすと明るい面と暗い面で入れ替わって見える問題を直しました。
