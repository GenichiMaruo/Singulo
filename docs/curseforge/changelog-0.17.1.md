# Singulo 0.17.1 — Minecraft 1.21.1 / NeoForge

## Fixes

- Stop the Accretion Jet Forge's black hole from showing in front of its own glowing jet. Looking along the forge from the exhaust or beam side, the black hole and its disk used to cover the plasma between you and the core. Now each forge draws the jet behind its core first, then its black hole, then the jet on your side. Forges lined up one behind another overlap correctly too.
- Keep the hum of a running Penrose Reactor and of rogue black holes after the game reloads its sounds, such as when you change the language. Previously the hum went silent until you left and came back.

## 修正

- 降着ジェット鍛造炉のブラックホールが、手前の光るジェットの上に重なって見える問題を修正しました。排熱側やビーム側から炉の軸に沿って見ると、カメラと炉心のあいだにあるプラズマの上に、ブラックホールの影と円盤が描かれていました。炉ごとに「炉心より奥のジェット → ブラックホール → 手前のジェット」の順で描くようにしました。何基も一列に並べたときも正しく重なります。
- 言語の変更などで音の読み込み直しが入っても、稼働中のPリアクターと野良ブラックホールのうなりが消えないようにしました。これまでは、いったん離れて戻るまで鳴りませんでした。
