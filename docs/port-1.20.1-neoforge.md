# Minecraft 1.20.1 / NeoForge 版（Singulo 0.7.0）

既存の `port/1.20.1-neoforge` の 0.6.0 を、`main` の 0.7.0（`933373a`）まで更新したバックポートです。`main` は Minecraft 1.21.1 専用として維持します。

## 実行環境

- Minecraft **1.20.1**、NeoForge **47.1.106**、Java **17**。
- JEI は任意。開発・検証環境では **15.20.0.129** を使用します。
- 配布用 JAR: `build/libs/Singulo-0.7.0-1.20.1N.jar`（`-dev.jar` は配布しません）。
- 公開用タグ: `v0.7.0-1.20.1N`。この変更自体は公開処理を実行しません。

## 更新内容

0.4〜0.6 の変更を取り込み、ボス戦・報酬・重力装備・隕石探索・ジェネシスの進行、アンドロイドの敵・組立・護衛・充電・修理・装備・命名に対応しました。サポーター名は攻略本とエンディングの通常／虹色の額に表示できます。実際の名簿は空のままです。

1.21.1 からの主な API 変更は以下のとおりです。

- 登録・イベント・Capability・GUI・通信を 1.20.1 の API に移植。新しい通信にはチャネルバージョン 2 を使用し、0.3.0 クライアントとの接続を拒否します。クライアントとサーバーの両方を更新してください。
- Data Components は既存の 1.20.1 用 NBT 保存に合わせ、装備・充電器・探索記録・ジェネシス記憶を保存します。
- アンドロイドの防具効果・防具エンチャント、翼の滑空・重力・落下ダメージを 1.20.1 のフックで実装します。
- 描画の頂点形式、印刷中のクリッピング、GUI、ビームの描画範囲を旧 API に対応させます。
- レシピ・戦利品・進捗・構造物・タグは 1.20.1 の複数形ディレクトリと形式で生成し、共通素材には Forge タグを使います。

1.21.1 のワールドを 1.20.1 に変換する機能はありません。既存の 1.20.1 ワールドを更新する前にはバックアップしてください。新しい遺構設備は新規生成チャンクに出現します。

## ビルド・検証

Gradle 9.8 を動かせる完全な JDK 17 以上と、Minecraft のコンパイル／実行用の JDK 17 を使用します。検証では Gradle に JDK 21、ゲームに JDK 17 を使用しました。特定 PC の Java パスは固定しません。必要なら `-Porg.gradle.java.installations.paths=/path/to/jdk17` を指定してください。

```sh
python3 tools/gen_data.py
python3 tools/check_models.py
python3 tools/localization.py --check
python3 tools/check_supporters.py
python3 tools/check_port_1201.py
./gradlew build runGameTestServer
python3 tools/check_port_1201.py --jar build/libs/Singulo-0.7.0-1.20.1N.jar
./gradlew runClient
```

生成画像には Pillow が必要です。GameTest の実行前には `build/gametest/world` と前回の `build/gametest/logs/latest.log` を削除して、前回の試験のブロック・流体・エンティティが次回に干渉するのを防ぎます。開発用や通常プレイのワールドは対象外です。

1.20.1 固有の回帰テストとして、充電器のエネルギー Capability と NBT 保存、防具板のエンチャント、翼の滑空、新規パケットの Unicode／シード値の往復を追加しました。ボスとアンドロイドの実効段差高さ（1ブロック）も確認します。詳細な実測結果は [0.6.0 検証記録](port-0.6.0-1.20.1/verification.md) を参照してください。

## NeoForge 1.20.1 のメタデータ

ModDevGradle の `enable { neoForgeVersion = "1.20.1-47.1.106" }` で公式の `net.neoforged:forge` を使用します。この世代は Java パッケージ `net.minecraftforge`、`META-INF/mods.toml`、依存 mod ID `forge` が正しい形式です。配布名の末尾は `N`、公開先ローダー指定は `neoforge` です。

0.7.0の検証・画像・公開結果は[0.7.0移植記録](port-0.7.0-1.20.1/verification.md)を参照してください。
