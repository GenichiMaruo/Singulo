# Minecraft 1.20.1 / NeoForge 版

`port/1.20.1-forge` の Singulo 0.3.0 を基に、`port/1.20.1-neoforge` で NeoForge 47.1.106 専用版を管理します。Minecraft 1.21.1 / NeoForge 版は `main`、1.20.1 / Forge 版は元のブランチを使用します。

## 実行環境

- Minecraft 1.20.1、NeoForge 47.1.106、Java 17。
- JEI は任意。開発環境では JEI 15.20.0.129 を使用します。
- 配布ファイル: `build/libs/Singulo-0.3.0-1.20.1N.jar`。

## ビルド設定

ModDevGradle の Legacy プラグインで `enable { neoForgeVersion = "1.20.1-47.1.106" }` を指定し、公式の `net.neoforged:forge` に対してコンパイル・再難読化します。このバージョンの NeoForge は `net.minecraftforge` の Java パッケージ、`META-INF/mods.toml`、依存 mod ID `forge` を使用するため、それらは維持します。依存バージョンを 47.1.106 に設定し、配布名の末尾と公開ローダーをそれぞれ `N`、`neoforge` に変更します。

1.20.1 のデータ形式・通信・描画などのバックポート内容は [Forge 版の移植記録](port-1.20.1.md) を参照してください。1.21.1 のワールドを変換する機能は含みません。

## 検証手順

Gradle は JDK 25、Minecraft のコンパイルと実行は Java 17 を使用します。

```powershell
./gradlew.bat build runGameTestServer
./gradlew.bat runClient -PclientSmokeTest=true
uv run --no-project --python 3.12 tools/localization.py --check
```

公開タグは `v0.3.0-1.20.1N`。GitHub Actions でもビルドと GameTest に成功した同じ JAR を CurseForge と GitHub Release にアップロードします。

## 検証結果

2026-10-11、NeoForge 47.1.106 / Java 17 で確認しました。

- ビルドと再難読化が成功。配布 JAR の 425 クラスが Java 17 形式で、依存バージョン・Minecraft バージョン・Access Transformer を確認。
- GameTest 180 件すべて成功。機械、搬入出、遺構、戦利品、ボス、ワームホール、時間結晶、飛行・爆発ノックバック耐性・Capability を検証。
- JEI 15.20.0.129 を含むクライアントでリソース読み込みとタイトル画面到達を確認。
- 中国語簡体字・繁体字・韓国語の各 1,563 項目の翻訳と書式チェックが成功。
