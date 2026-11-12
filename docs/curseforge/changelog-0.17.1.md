# Singulo 0.17.1 — Minecraft 1.20.1 / Forge & NeoForge

## Changes

- The first time you join a world, a chat message announces Singulo Arsenal, the official add-on, with a link to its CurseForge page. It appears once, and not at all if Singulo Arsenal is installed. To see it again, set `arsenalNotice` back to `true` in `singulo-client.toml`.
- Event Horizon Vaults stacked on top of each other now join up: the upper vault drops its gold base plate and its steel stand rests directly on the vault below.

## Fixes

- Add regression coverage for block-specific item tooltips, already supported on 1.20.1. Stored items and fluid amounts are unchanged.

- Stop the Accretion Jet Forge's black hole from showing in front of its own glowing jet. Looking along the forge from the exhaust or beam side, the black hole and its disk used to cover the plasma between you and the core. Now each forge draws the jet behind its core first, then its black hole, then the jet on your side. Forges lined up one behind another overlap correctly too.
- Keep the hum of a running Penrose Reactor and of rogue black holes after the game reloads its sounds, such as when you change the language. Previously the hum went silent until you left and came back.
- An emptied Event Horizon Vault now shows that it is empty. Previously it kept showing the last count, such as 1, after everything was taken out. The Android Workbench likewise stops showing its last item once emptied.
- Placing a fluid pipe, or another block that is not a cable, next to a powered cable no longer makes the cable's flowing-power light blink off for a moment.

## 変更

- 初めてワールドに入ったとき、公式アドオン Singulo Arsenal の公開をチャットで知らせるようにしました（CurseForge のページへのリンクつき）。知らせるのは1回だけで、Singulo Arsenal を入れていれば出ません。もう一度見るには、`singulo-client.toml` の `arsenalNotice` を `true` に戻します。
- 事象の地平線倉庫を縦に積むと、つながった形になるようにしました。上の倉庫は下の金の板がなくなり、黒鋼の台が下の倉庫にじかに乗ります。

## 修正

- 1.20.1ですでに対応しているパイプ・倉庫・液体ポートのアイテム説明に回帰テストを追加しました。保存された中身は変更しません。

- 降着ジェット鍛造炉のブラックホールが、手前の光るジェットの上に重なって見える問題を修正しました。排熱側やビーム側から炉の軸に沿って見ると、カメラと炉心のあいだにあるプラズマの上に、ブラックホールの影と円盤が描かれていました。炉ごとに「炉心より奥のジェット → ブラックホール → 手前のジェット」の順で描くようにしました。何基も一列に並べたときも正しく重なります。
- 言語の変更などで音の読み込み直しが入っても、稼働中のPリアクターと野良ブラックホールのうなりが消えないようにしました。これまでは、いったん離れて戻るまで鳴りませんでした。
- 事象の地平線倉庫を空にしたとき、空と表示されるようにしました。これまでは全部取り出しても、最後の個数（1など）が表示されたままでした。アンドロイド作業台も、空にしたあと最後の物が見えたままにならないようにしました。
- 電気が流れている電線の隣に液体パイプなど電線以外のブロックを置いたとき、電線の通電の光が一瞬消えないようにしました。

## Review follow-up / レビュー時の追加修正

- Match attached Jet Forge glow to the actual view-depth/horizon plane used by the lens shader, including angled views.
- Preserve immediate cable-light clearing when a source or cable is removed during the short network handover.
- 鍛造炉の付属部品の光を、斜めから見たときもレンズの実際の視点深度・地平線の判定に合わせました。
- 電線網の引継ぎ中に電源や電線を取り外した場合も、通電表示がすぐ消えるようにしました。
