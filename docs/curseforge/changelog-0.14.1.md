# Singulo 0.14.1 — Minecraft 1.20.1 / Forge & NeoForge

## Fixes

- Isolate Create/Ponder previews from the real world's Rayleigh-panel lighting.
- Use a vanilla-sized lightmap while rendering Ponder scenes, and restore the world lightmap afterwards, including nested rendering and exceptions.
- Preserve the existing clear-noon lighting provided by powered Rayleigh panels.
- Fix skipped Rayleigh noon-lightmap updates when Alex’s Caves replaces vanilla biome lighting. Keep its normal biome lighting unchanged.

## 修正

- Create/Ponderの仮想シーンに実ワールドのレイリーパネル照明が混ざらないようにしました。
- Ponder描画中は通常サイズのライトマップを使い、終了時・入れ子描画・例外時にも元の状態へ戻します。
- 通電したレイリーパネルが晴れの真昼と同じ照明になる既存仕様は維持します。
- Alex’s Caves併用時にレイリーパネル用の昼光ライトマップが更新されない問題を修正しました。通常のバイオーム照明は維持します。
