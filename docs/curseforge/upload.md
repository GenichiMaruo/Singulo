# CurseForge ファイルのアップロード

## 対象と配布ファイル

Minecraft 1.21.1は0.9.0以降NeoForgeのみ。Forge版の最終版は0.8.1。1.20.1のForge / NeoForge版は`1.20.1`ブランチで扱う。

| Minecraft / ローダー | ブランチ | 手元でのビルド | 配布ファイル |
| --- | --- | --- | --- |
| 1.21.1 / NeoForge | `1.21.1` | `./gradlew build` | `build/libs/Singulo-<版>-1.21.1N.jar` |
| 1.20.1 / Forge | `1.20.1` | `./gradlew -Ploader=forge build` | `build/forge/libs/Singulo-<版>-1.20.1F.jar` |
| 1.20.1 / NeoForge | `1.20.1` | `./gradlew -Ploader=neoforge build` | `build/neoforge/libs/Singulo-<版>-1.20.1N.jar` |

`<版>`は対象ブランチの`gradle.properties`の`mod_version`。`N`はNeoForge、`F`はForge。表示名もJAR名から`.jar`を除いたものとし、Beta / Releaseは名前に含めない。

手元のビルドは確認用。正式な送信には、下記の成功済みCI成果物を使用し、公開直前に再ビルドしない。

## Minecraft 1.21.1の公開手順

[公開ワークフロー](../../.github/workflows/publish-curseforge.yml)は**手動実行のみ**。タグのpushだけでは公開しない。

1. `mod_version`と[変更履歴](changelog-0.9.0.md)を更新したPRで、PR validationを成功させる。GameTest・GUI・Mekanism連携の両ジョブが必要。
2. PRを`1.21.1`へマージし、マージ後のソースが成功CIの検証対象ツリーと一致することを確認する。
3. マージコミットに`v<mod_version>`タグを作成する。既存タグは移動しない。
4. ActionsのPublish CurseForgeを`1.21.1`から実行する。`verified_run_id`に成功したPR validationの実行ID、`release_tag`に上記タグ、`release_type`に通常`auto`を指定する。
5. タグ・PR・実際のCI検証ツリー・JARの版とローダー・GameTestとGUI完了を検証してから、そのCIのJARをCurseForgeへ送信する。

`curseforge`環境の`CURSEFORGE_PROJECT_ID`（SecretまたはVariable）と`CURSEFORGE_TOKEN`（Secret）を使う。トークンをファイルへ記載しない。

版番号は`X.Y.Z`形式。`auto`では0.x.yをBeta、1.0.0以降をReleaseとする。手動で`beta`・`release`・`alpha`も指定できる。

1.20.1の公開は[そのブランチのワークフロー](https://github.com/GenichiMaruo/Singulo/blob/1.20.1/.github/workflows/publish-curseforge.yml)に従う。1.21.1の実行ID・タグ・JARを転用しない。

## CurseForgeでの登録内容

| Minecraft | ローダー | Java |
| --- | --- | --- |
| 1.21.1 | NeoForge | 21 |
| 1.20.1 | Forge / NeoForge（ファイル別） | 17 |

- Environment: Client / Server。
- Related projects: JEIとMekanismはOptional Dependency。
- Changelog: 対象版の`docs/curseforge/changelog-<版>.md`（Markdown）。
- Description: [description.md](description.md)。
- License: `gradle.properties`の`mod_license`に合わせる。

## 送信後

GitHub Releaseには変更履歴とCurseForgeの案内だけを掲載し、配布JARを添付しない。

公開処理は送信前に添付ファイルのないdraftを予約する。失敗時はCurseForgeの登録状態とdraftを確認し、成功済みの送信を再実行しない。送信成功とCurseForgeの審査・一般公開は別なので、公開一覧と配布ファイルも確認する。
