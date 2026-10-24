# 0.11.2: Sodiumのチャンク描画クラッシュ

## 原因

提供された `crash-2026-10-24_11.47.55-client.txt` では、Singulo 0.11.1 / Sodium 0.6.13 / Iris 1.8.12、Minecraft 1.21.1 / NeoForge 21.1.264 を使用。シェーダーパックは無効。

砂岩のチャンクメッシュ構築中、Sodiumの `LevelSlice.getLightEngine()` が `UnsupportedOperationException` を投げている。呼出元はSinguloの `RayleighLighting.level()` → `pack()` → `RayleighRenderLightMixin`。Singuloが任意の `BlockAndTintGetter` から照明エンジンを取得できると仮定していたことが原因。

[Sodium 0.6.13の実装](https://github.com/CaffeineMC/sodium/blob/mc1.21.1-0.6.13/common/src/main/java/net/caffeinemc/mods/sodium/client/world/LevelSlice.java)でも、描画スレッドからのライブ照明データアクセスを許さず、このメソッドが常に例外を投げることを確認した。Javaの版やメモリ不足によるものではない。レイリーパネルの存在判定より前に例外となるため、通常のバニラブロックの描画でも起きる。

## 修正

ブランチ: `bugfix/0.11.2`（公開済み0.11.1のマージ結果から作成）。版番号を0.11.2に更新。

`RayleighLighting.level()` はバニラの `Level` と `RenderChunkRegion` に限って照明エンジンを参照する。独自レンダラーのスナップショットは問い合わせず、`pack()` / `tag()` も元の照明値を保持する。例外を毎ブロック捕捉する処理は追加しない。

上記のクラッシュ回避に加え、Sodium 0.6.13の標準地形シェーダーへ人工昼光を連携した（Iris併用時はシェーダーパック無効）。

`mod/singulo` の互換性警告は、バニラ用 `light.glsl` の置換をSodiumが検出するため発生していた。Sodium側ではこのincludeを使わず、上記の専用照明経路を使う。Sodium公式の `pack.mcmeta` の `sodium.ignored_shaders` に、この検証済みの `light.glsl` だけを指定する。スキャナーや他のパックの警告は変更しない。生成元の `tools/gen_data.py` にも反映し、CIでは警告の再発と昼光PNGの一致を検査する。

### Sodiumでも晴天正午と一致させる照明経路

- `RayleighSky` が完成済みの読み取り専用の照明スナップショットを公開し、Sodiumの `LevelSlice.copyData()` の時点で保持する。ワーカーからワールドのチャンクや照明エンジンへアクセスしない。
- 自然光はSodiumの元の照明計算を維持し、人工昼光は同じSodiumのスムーズ／フラット照明パイプラインへ独立して通す。AO・形状・補間方法はそのレンダラーの通常の昼間と同じになる。
- 人工昼光の補間後の8ビット座標を保持する。Sodiumの座標とマテリアルの未使用ビットへ格納し、座標精度、通常照明、マテリアルの設定、描画データのサイズを変更しない。モデルの照明との合成でも各成分を独立して扱う。
- Sodiumの標準シェーダーは自然光を元のライトマップ領域から、人工昼光を晴天正午の領域から読み、バニラ描画と同じく色ごとの最大値で合成する。自然光の時間・天候による変化は屋外に残る。
- Sodiumはコンパイル専用の任意依存。Sodium未導入時には互換用のMixinを適用せず、従来のバニラ経路を使用する。

## 回帰検証

開発用の実クライアント検証に、全メソッドが例外を投げる描画スナップショットを追加。ワーカースレッド上で `level()` / `pack()` / `tag()` が照明エンジンを呼ばず、値を変更しないことを検査する。`SINGULO_DETACHED_LIGHT_VIEW_PASSED` をログに残す。

`tools/review/v011.gradle` の `-PrendererModsDir=<ローカルのJARフォルダ>` により、配布依存を変更せず実Sodium/Irisで検証できる。ユーザーの実インスタンスやワールドは変更せず、JARのコピーと使い捨てワールドを `build/` 内で使用する。クラッシュレポート自体はコミットしない。

確認済み:

- 実Sodium 0.6.13: ワールド読込・チャンク描画・回帰検査・21枚の撮影を完走。
- 実Sodium 0.6.13＋Iris 1.8.12（シェーダーパック無効）: 同じ検証を完走。
- Sodiumなし: 人工昼光256画素が晴天正午と一致し、雨天深夜でも一致。自然光の夜間・天候、電源オフ／オン、NeoForgeのスムーズ照明とエンティティ照明を検証する既存クライアントテストも成功。
- 地形のPNG比較: 84,000画素で晴天正午と点灯中の雨天深夜が完全一致。消灯時は暗くなり、再点灯で元の画素に戻る。
- Sodiumの実頂点エンコーダーとモデル照明合成を使い、0〜240の全241段階で人工昼光を復元でき、座標60ビット・通常の照明・マテリアル・セクション番号が維持されることを確認。
- Sodium単体とSodium＋Iris（シェーダーパック無効）で、それぞれ84,000画素のPNG比較が完全一致。最初のクラッシュ回避だけの版では同じ比較が失敗することも再現した。
- 自発光モデル自身・隣接する自発光モデルから、パネルの届かない場所に人工昼光が発生しないこともSodiumの実スムーズ照明を通して確認。
- 通常の本番ビルドと必須GameTest 511件: 全件成功。0.11.2の配布JAR検査（Java 21、依存関係、ファイル名・内部版番号、検証用クラス混入なし）も成功。
- 翻訳、ゲーム内説明、モデル、進捗、支援者一覧、公開処理のPythonテスト10件: 成功。

検証環境はJava 21 / NeoForge 21.1.256。レポートに含まれる全modを同時に導入した環境や、シェーダーパック有効時の描画品質までは検証していない。

ログ: `build/review-0112-sodium.log`、`build/review-0112-sodium-iris.log`、`build/review-0112-rayleigh.log`、`build/review-0112-final.log`。画像: `build/review-0112-sodium-shots/`、`build/review-0112-sodium-iris-shots/`、`build/rayleigh-review/screenshots/`。

CIにもSodium単体の実クライアント検証・全241段階の頂点検査・PNG比較を追加。公開前のCI来歴検査でも `sodium-lighting` の成功を必須にした。

昼光連携後のログは `build/review-0112-sodium-only-noon.log`（Sodium単体）、`build/review-0112-noon-emissive.log`（Sodium＋Iris・自発光モデル回帰）、`build/review-0112-native-noon-final.log`（通常描画）、`build/review-0112-noon-final.log`（本番ビルド・全511件）。比較画像は `build/review-0112-sodium-only-noon-shots/`、`build/review-0112-native-noon-shots/`、`build/review-0112-sodium-iris-noon-shots/` に保存。

比較は各レンダラーの晴天正午を基準とする。Sodium自身が変更しているAOなど、Sodiumと通常描画の一般的な描画差をなくすものではない。独自のシェーダーパック有効時はライトマップ・頂点形式が置き換わるため、この検証の対象外。

成果物: `build/libs/Singulo-0.11.2-1.21.1N.jar`。この段階ではpush・PR作成・マージ・公開は行っていない。

### リソースパック警告の検証

Sodium単体とSodium＋Iris（シェーダーパック無効）で、スキャナーが `light.glsl` のメタデータを読み取ったログがあり、`Resource pack: mod/singulo` の互換性レポートは出なくなった。メタデータ受理を知らせるログ自体はSodiumの仕様でWARNレベルに残るが、ゲーム内の非互換警告は発生しない。

両構成で晴天正午・雨天深夜・再点灯の84,000画素比較が一致し、消灯時の暗化も確認。Iris併用の初回だけ再点灯画像が暗いままになる不一致があり、クライアントの照明値検査は成功していた。再起動して同じ検証を実行すると一致したため、この一過性の描画不一致の原因が解消されたとは断定しない。証跡は `build/review-0112-pack-warning-iris.log`（初回）、`build/review-0112-pack-warning-iris-retry.log`（再検証成功）、`build/review-0112-pack-warning-sodium.log`（Sodium単体成功）。今回の変更はパックのメタデータと生成元、CIの警告回帰検査のみで、照明コードは変更していない。
