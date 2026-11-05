# Singulo 0.15.0 — Minecraft 1.21.1 / NeoForge

## Features

- Add the Accretion Jet Forge, a dedicated 44×16×15 endgame multiblock that focuses black hole jets to forge Collapsar Ingots.
- Add Collapsar Billets, Collapsar Ingots, forge construction parts, and liquid helium cartridges. Equipment and tools remain the responsibility of separate mods.
- Add ignition, beam focusing, heated material models, cooling, sound effects, and configurable flash and camera shake.
- Rework black hole shadows, accretion disks, and gravitational lensing, with fallback world rendering when traced rendering is unavailable or its slots are full.
- Keep multiblock port handlers available across formation changes so cached automation connections can resume.

## Fixes

- Pause forge progress and energy consumption while any footprint chunk is unloaded; retain progress across saves between synchronization packets.
- Stop beam damage while processing is paused or the structure is broken, while retaining the black hole's independent gravity.
- Preserve the black hole's location when rebuilding a frame, reject oversized output stacks, and account for repeated coolant requirements.
- Keep accepted traced black holes from being displaced by other distortion effects.

## 追加

- ブラックホールのジェットを収束し、コラプサー・インゴットを鍛える44×16×15の降着ジェット鍛造炉を追加。
- コラプサー・ビレット／インゴット、建設部品、液体ヘリウム・カートリッジを追加。装備・ツール本体は別MOD側で扱います。
- 点火・収束・赤熱・冷却の描画と効果音、閃光・画面の揺れを調整する設定を追加。
- ブラックホールの影・降着円盤・重力レンズを更新。通常描画への切替にも対応。
- マルチブロックの形成状態が変わっても、搬入出ポート経由の自動化接続が再開できるよう改善。

## 修正

- 施設の一部が未読込の間は加工と電力消費を停止し、同期パケット間に保存しても進捗を保持。
- 加工停止中や構造破損時のビームダメージを停止。ブラックホール自体の引力は継続。
- 再形成時のブラックホールの位置を保持し、出力枠の上限超過と重複する冷媒要求を適切に処理。
- 重力レンズの上限を超えたブラックホールが不可視になる問題を防止。
