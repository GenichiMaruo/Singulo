# CurseForge ファイルのアップロード欄

プロジェクトを作ったあと、「Upload File」で入れる内容。

## File
対応ブランチで `./gradlew build` を実行します。

| Minecraft | ブランチ | 配布ファイル |
| --- | --- | --- |
| 1.21.1 / NeoForge | `main` | `build/libs/Singulo-0.4.0-1.21.1N.jar` |
| 1.20.1 / Forge | `port/1.20.1-forge` | `build/libs/Singulo-0.3.0-1.20.1F.jar` |
| 1.20.1 / NeoForge | `port/1.20.1-neoforge` | `build/libs/Singulo-0.3.0-1.20.1N.jar` |

## Display name
Singulo-<版>-1.21.1N、Singulo-<版>-1.20.1F、または Singulo-<版>-1.20.1N（例: Singulo-0.4.0-1.21.1N）

命名規則は `mod名-modバージョン-Minecraftバージョン＋ローダー記号`。`N` は NeoForge、`F` は Forge を表します。1.21.1版は NeoForge、1.20.1版は Forge と NeoForge の専用ファイルを用意します。Beta / Release は Release type で指定し、名前には入れません。

## Release type
1.0.0 より前は Beta、1.0.0 以降の正式な版は Release。

GitHub Actions では自動で決まります（下の「GitHub Actions で公開する」）。

## Changelog
`changelog-<版>.md`（例: [changelog-0.4.0.md](changelog-0.4.0.md)）の中身を貼る（Markdown）。

## Game versions
| Minecraft | Mod loader | Java |
| --- | --- | --- |
| 1.21.1 | NeoForge | Java 21 |
| 1.20.1 | Forge | Java 17 |
| 1.20.1 | NeoForge | Java 17 |

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

## GitHub Actions で公開する

[公開ワークフロー](../../.github/workflows/publish-curseforge.yml) は、`main` では `v<mod_version>`、1.20.1 Forgeブランチでは `v<mod_version>-1.20.1F`、NeoForgeブランチでは `v<mod_version>-1.20.1N` タグの push または手動実行で動きます。`curseforge` 環境の `CURSEFORGE_PROJECT_ID`（Secret または Variable）と `CURSEFORGE_TOKEN`（Secret）を利用します。トークンをリポジトリに記載する必要はありません。

版番号・変更履歴・翻訳を検証し、ビルドと GameTest が成功した JAR を成果物として保存したあと、同じ JAR をアップロードします。

公開の種類（Release type）は、既定（`auto`）では版番号から決まります。

| `mod_version` | 公開の種類 |
| --- | --- |
| `0.x.y`（例: 0.4.0） | Beta |
| `1.0.0` 以降で、`-beta` などの付かない版（例: 1.0.0、1.2.3） | **Release** |
| 後ろに `-alpha`・`-beta.1`・`-rc1` などが付く版（例: 1.0.0-beta.1） | Beta |

手動実行（Actions タブの「Run workflow」）では、`release_type` に `beta`・`release`・`alpha` を選んで上書きできます。タグと `gradle.properties` の版番号が一致しない場合は公開しません。再実行すると重複アップロードになる可能性があるため、成功済みの公開ジョブは再実行しないでください。

公開後の CurseForge の審査・承認はアップロードとは別の手続きです。

1.20.1ブランチのタグ公開では、CurseForgeへのアップロードが成功すると、同じJARをMinecraftバージョン入りのGitHub Releaseにも添付します。
