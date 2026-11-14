# Singulo 0.19.0 — Minecraft 1.20.1 / Forge・NeoForge

## Features

- Reorganize the creative inventory and JEI item order by type. Ingots, plates, circuit components, intermediate materials, devices, multiblock parts and building blocks each stay together. Android and drone facilities, the Food Printer, RGB Dye Station and newer cosmic devices join the device group. All spawn eggs are at the end.
- Add guarded Android Work Console endpoints and recipe snapshots for addon integration. Endpoint identities and owners are checked before external orders access a console.
- Add private crafting requests through `AndroidEscrows`. Each request tracks its own supplied materials, intermediate products, completed products and returns; repeated submissions are idempotent, and cancellation waits for carried materials to be recovered before refunds become available.
- Private requests support chains of ordinary crafting steps. Machine processing is rejected before taking supplied materials because external machine inputs and outputs cannot yet be reserved and recovered safely across cancellation or reload. Normal Android machine orders and recipe discovery remain available.
- Add `AndroidSharedStorage` so addons can identify several access blocks that expose the same storage. Android stock counting and extraction visit the shared storage once.

## Fixes

- Androids can continue taking materials from a storage slot that holds more than one normal stack.
- Private crafting sizes each trip to the android's actual carrying space and compacts remaining materials before inserting products.
- Fluid claims now preview the same amount they withdraw, combine matching fluid slots when draining, and preserve large totals without integer overflow. Recovery also retains fluid cargo when a request owner cannot be identified.

## 追加

- クリエイティブタブと JEI のアイテム順を種類ごとに整理しました。インゴット、プレート、基板と回路、中間素材、装置、マルチブロック部材、建材をそれぞれまとめました。アンドロイドやドローンの設備、フードプリンター、RGB 染色台、新しい宇宙設備も装置のまとまりに置き、すべてのスポーンエッグを最後に移しました。
- アドオン向けに、所有者と装置の識別子を検証するアンドロイド作業指令台 API と、利用可能なレシピを取得する API を追加しました。
- `AndroidEscrows` による依頼ごとの独立したクラフト材料管理を追加しました。提供材料、中間素材、完成品、返却品を依頼別に保管します。同じ依頼の再送で材料を二重に受け取らず、取消時は運搬中の材料を回収してから返却を許可します。
- 独立した依頼では通常のクラフトを連続して実行できます。機械加工は、取消や再読込をまたいで外部装置の材料・成果物を安全に予約・回収する仕組みがまだないため、材料を受け取る前に拒否します。通常のアンドロイドの機械加工依頼とレシピ取得は引き続き利用できます。
- `AndroidSharedStorage` を追加しました。同じ共有ストレージを複数のブロックから接続するアドオンでも、在庫の二重計上や重複した取り出しを防げます。

## 修正

- 通常のスタック上限を超える量を保持するストレージの同じ欄から、アンドロイドが続けて材料を取り出せるようにしました。
- 独立したクラフト依頼では、一度に運ぶ量をアンドロイドの運搬容量に合わせ、完成品をしまう前に残りの材料をまとめるようにしました。
- 流体の受け取りの事前確認と実処理で量が一致するようにし、複数の欄に分かれた同じ流体をまとめて取り出せるようにしました。大容量の合計で整数あふれを起こさず、依頼の所有者を特定できない場合も運搬中の流体を回収記録に保持します。
