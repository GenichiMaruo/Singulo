# Singulo 0.14.1 — Minecraft 1.21.1 / NeoForge

## Fixes

- Isolate Create/Ponder previews from the real world's Rayleigh-panel lighting.
- Use a vanilla-sized lightmap while rendering Ponder scenes, and restore the world lightmap afterwards, including nested rendering and exceptions.
- Preserve the existing clear-noon lighting provided by powered Rayleigh panels.

## 修正

- Create/Ponderの仮想シーンに実ワールドのレイリーパネル照明が混ざらないようにしました。
- Ponder描画中は通常サイズのライトマップを使い、終了時・入れ子描画・例外時にも元の状態へ戻します。
- 通電したレイリーパネルが晴れの真昼と同じ照明になる既存仕様は維持します。
