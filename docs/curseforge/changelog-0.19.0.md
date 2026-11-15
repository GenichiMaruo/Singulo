# Singulo 0.19.0 — Minecraft 26.1.2 / NeoForge

## 26.1.2 port

- Port Singulo 0.19.0 to Minecraft 26.1.2 and NeoForge 26.1.2.126. Requires Java 25. This JAR is not for 1.21.1 or 1.20.1.
- Preserve the organized creative inventory, Android Work Console/addon APIs, private crafting requests, shared storage and cosmic machines from 0.19.0.
- Update item, fluid and energy transfer to the transactional NeoForge API, including rollback and commit notifications.
- Migrate saved data, attachments, recipes, models, screens and the new rendering pipeline. Existing-world upgrades still require a backup; focused migration tests do not guarantee every old modpack/world is compatible.
- Fix non-square lens mip generation, keep foreground occlusion correct, and update artificial sunlight and Distant Horizons/Sodium lightmap integration.

## Compatibility and known limits

- NeoForge only. Mekanism integration is not included in this Minecraft port.
- JEI is optional. Tested optional rendering versions: Sodium 0.9.2 and Distant Horizons 3.3.3.
- Create/Ponder integration is not supported on 26.1.2 because no official compatible dependency has been verified. No Ponder compatibility claim is made.
- The tested Iris/Complementary combinations can fail with `Missing sampler Sampler1`. This also reproduces without Singulo or Arsenal when a vanilla diamond sword is dropped. Shader-pack compatibility is therefore not guaranteed; the standard renderer and the tested Sodium/Distant Horizons path are recommended for this release.

## Validation

- 815 Singulo GameTests plus the vanilla smoke test, 140 Python checks and 111 standalone Java assertions passed locally.
- Real-client GUI, cosmic devices, fifteen simultaneous lenses, foreground occlusion, lighting pixel comparisons and repeated world re-entry were checked.

## 日本語概要

Minecraft 26.1.2・NeoForge専用の移植版です。Java 25が必要です。0.19.0のアイテム分類、アンドロイドの外部連携API、独立したクラフト依頼、各種装置を引き継ぎ、転送・保存・描画を新しいAPIへ対応しました。

Mekanism連携とCreate/Ponder連携はこの版では非対応です。Irisと一部シェーダーパックでは、Singuloを外した状態でも再現する描画エラーを確認しているため、シェーダーパック動作は保証しません。旧ワールドを移す場合は必ずバックアップしてください。
