# CurseForge ファイルのアップロード欄

プロジェクトを作ったあと、「Upload File」で入れる内容。

## File
`build/libs/singulo-0.1.0.jar`（`./gradlew build` で作る）。

## Display name
Singulo 0.1.0

## Release type
Beta

最初の公開で、まだ遊んだ人が少ないため。安定したら Release にする。

## Changelog
[changelog-0.1.0.md](changelog-0.1.0.md) の中身を貼る（Markdown）。

## Game versions
- Minecraft: 1.21.1
- Mod loader: NeoForge
- Java: Java 21
- Environment: Client, Server（両方に必要）

## Related projects
| プロジェクト | 種類 |
| --- | --- |
| Just Enough Items (JEI) | Optional Dependency |

## プロジェクト作成時のほかの欄
- **License**: All Rights Reserved（`gradle.properties` の `mod_license` と同じ）。ほかのライセンスにするなら両方を変える。
- **Description**: [description.md](description.md)。
- **Gallery**: ゲーム内のスクリーンショットを数枚（リアクター、マルチブロック、重力レンズ、遺構など）。ロゴとは別に用意する。

## 次の版を出すとき
1. `gradle.properties` の `mod_version` を上げる。
2. `changelog-<版>.md` を書く。
3. `./gradlew build` で JAR を作り、上の欄を同じように埋める。

## GitHub Actions で Beta を公開する

[Publish CurseForge Beta](../../.github/workflows/publish-curseforge.yml) は、`v<mod_version>` タグの push または手動実行で動きます。`curseforge` 環境の `CURSEFORGE_PROJECT_ID`（Secret または Variable）と `CURSEFORGE_TOKEN`（Secret）を利用します。トークンをリポジトリに記載する必要はありません。

版番号・変更履歴・翻訳を検証し、ビルドと GameTest が成功した JAR を成果物として保存したあと、同じ JAR を **Beta** としてアップロードします。タグと `gradle.properties` の版番号が一致しない場合は公開しません。再実行すると重複アップロードになる可能性があるため、成功済みの公開ジョブは再実行しないでください。

公開後の CurseForge の審査・承認はアップロードとは別の手続きです。
