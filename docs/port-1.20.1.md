# Minecraft 1.20.1 / Forge 版

Singulo 0.3.0 を Minecraft 1.20.1 / Forge 47.4.26 にバックポートしたブランチです。元の 1.21.1 版は `main` に残し、1.20.1 版は `port/1.20.1-forge` で管理します。

## 実行環境

- Minecraft 1.20.1、Forge 47.4.26 以上の 1.20.1 対応版、Java 17。
- JEI は任意。開発環境では JEI 15.20.0.129 を使用します。
- 配布ファイル: `build/libs/Singulo-0.3.0-1.20.1F.jar`。

## 対応内容

登録・イベント・通信・チャンクロード・流体とアイテムの搬入出を Forge 1.20.1 の API に移植しました。アイテムの状態は NBT、プレイヤーの記録・発見履歴・次元ポケットは死亡時にも引き継ぐ Forge の保存データを使用します。

レシピ・戦利品・進捗・タグ・構造物は 1.20.1 のディレクトリと形式で生成します。共通素材タグは Forge のタグに対応させ、戦利品はスタックできないアイテムも指定個数を生成します。描画・GUI・シェーダー・JEI の連携も 1.20.1 の API に合わせています。

重力は Forge の重力属性を使用します。落下ダメージ倍率・飛行・爆発ノックバック耐性は独自属性と Forge イベントで補います。飛行を解除するときは、この mod が許可した飛行だけを解除します。

1.21.1 のワールドを 1.20.1 に移すための変換機能は含みません。1.20.1 用のワールドで使用してください。

## 開発・確認

Gradle は JDK 25、Minecraft のコンパイルと実行は JDK 17 のツールチェーンを使用します。`gradle.properties` の `org.gradle.java.home` は開発環境の JDK に合わせて変更してください。

```powershell
uv run --no-project --python 3.12 --with pillow tools/gen_data.py
uv run --no-project --python 3.12 --with pillow tools/check_models.py
uv run --no-project --python 3.12 tools/localization.py --check
./gradlew.bat build runGameTestServer
./gradlew.bat runClient -PclientSmokeTest=true
```

クライアント確認はタイトル画面とリソースの読み込み完了をログに出し、自動終了します。通常の起動は `./gradlew.bat runClient` です。

## 検証結果

2026-10-11、Forge 47.4.26 / Java 17 で以下を確認しました。

- 配布 JAR のビルドと再難読化が成功。クラスのバージョンは Java 17、Forge のメタデータと 1.20.1 のリソース配置を確認。
- GameTest 180 件すべて成功。機械、搬入出、遺構、戦利品、ボス、ワームホール、時間結晶の育成などを検証。飛行・爆発ノックバック耐性・Capability の再接続も含む。
- JEI を含むクライアントがリソースを読み込み、タイトル画面に到達。
- 生成モデルの問題 0 件。中国語簡体字・繁体字・韓国語は各 1,563 項目の翻訳と書式を検証。
