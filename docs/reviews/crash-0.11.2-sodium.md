# 0.11.2: Sodiumのチャンク描画クラッシュ

## 原因

提供された `crash-2026-10-24_11.47.55-client.txt` では、Singulo 0.11.1 / Sodium 0.6.13 / Iris 1.8.12、Minecraft 1.21.1 / NeoForge 21.1.264 を使用。シェーダーパックは無効。

砂岩のチャンクメッシュ構築中、Sodiumの `LevelSlice.getLightEngine()` が `UnsupportedOperationException` を投げている。呼出元はSinguloの `RayleighLighting.level()` → `pack()` → `RayleighRenderLightMixin`。Singuloが任意の `BlockAndTintGetter` から照明エンジンを取得できると仮定していたことが原因。

[Sodium 0.6.13の実装](https://github.com/CaffeineMC/sodium/blob/mc1.21.1-0.6.13/common/src/main/java/net/caffeinemc/mods/sodium/client/world/LevelSlice.java)でも、描画スレッドからのライブ照明データアクセスを許さず、このメソッドが常に例外を投げることを確認した。Javaの版やメモリ不足によるものではない。レイリーパネルの存在判定より前に例外となるため、通常のバニラブロックの描画でも起きる。

## 修正

ブランチ: `bugfix/0.11.2`（公開済み0.11.1のマージ結果から作成）。版番号を0.11.2に更新。

`RayleighLighting.level()` はバニラの `Level` と `RenderChunkRegion` に限って照明エンジンを参照する。独自レンダラーのスナップショットは問い合わせず、`pack()` / `tag()` も元の照明値を保持する。Sodiumへのコンパイル時依存や、例外を毎ブロック捕捉する処理は追加しない。

バニラ描画の人工昼光は維持する。この修正はSodium/Irisの独自シェーダーに人工昼光の拡張座標を実装するものではない。Sodiumのシェーダーinclude非対応警告も今回の修正対象外。

## 回帰検証

開発用の実クライアント検証に、全メソッドが例外を投げる描画スナップショットを追加。ワーカースレッド上で `level()` / `pack()` / `tag()` が照明エンジンを呼ばず、値を変更しないことを検査する。`SINGULO_DETACHED_LIGHT_VIEW_PASSED` をログに残す。

`tools/review/v011.gradle` の `-PrendererModsDir=<ローカルのJARフォルダ>` により、配布依存を変更せず実Sodium/Irisで検証できる。ユーザーの実インスタンスやワールドは変更せず、JARのコピーと使い捨てワールドを `build/` 内で使用する。クラッシュレポート自体はコミットしない。

確認済み:

- 実Sodium 0.6.13: ワールド読込・チャンク描画・回帰検査・21枚の撮影を完走。
- 実Sodium 0.6.13＋Iris 1.8.12（シェーダーパック無効）: 同じ検証を完走。
- Sodiumなし: 人工昼光256画素が晴天正午と一致し、雨天深夜でも一致。自然光の夜間・天候、電源オフ／オン、NeoForgeのスムーズ照明とエンティティ照明を検証する既存クライアントテストも成功。
- 地形のPNG比較: 84,000画素で晴天正午と点灯中の雨天深夜が完全一致。消灯時は暗くなり、再点灯で元の画素に戻る。
- 通常の本番ビルドと必須GameTest 511件: 全件成功。0.11.2の配布JAR検査（Java 21、依存関係、ファイル名・内部版番号、検証用クラス混入なし）も成功。
- 翻訳、ゲーム内説明、モデル、進捗、支援者一覧、公開処理のPythonテスト9件: 成功。

検証環境はJava 21 / NeoForge 21.1.256。レポートに含まれる全modを同時に導入した環境や、シェーダーパック有効時の描画品質までは検証していない。

ログ: `build/review-0112-sodium.log`、`build/review-0112-sodium-iris.log`、`build/review-0112-rayleigh.log`、`build/review-0112-final.log`。画像: `build/review-0112-sodium-shots/`、`build/review-0112-sodium-iris-shots/`、`build/rayleigh-review/screenshots/`。

成果物: `build/libs/Singulo-0.11.2-1.21.1N.jar`。この段階ではpush・PR作成・マージ・公開は行っていない。
