# CurseForge公開手順 — Minecraft 1.20.1

共有ブランチは`1.20.1`。Forge / NeoForgeの配布ファイルは別々に作成する。Java 17が必要。Minecraft 1.21.1の開発・公開は`1.21.1`ブランチで行う。

| Loader | ローカルビルド | 配布ファイル |
| --- | --- | --- |
| Forge | `./gradlew -Ploader=forge build` | `build/forge/libs/Singulo-<版>-1.20.1F.jar` |
| NeoForge | `./gradlew -Ploader=neoforge build` | `build/neoforge/libs/Singulo-<版>-1.20.1N.jar` |

`<版>`は`gradle.properties`の`mod_version`。現在の公開ワークフローはBetaとして公開する。表示名は拡張子を除いたファイル名、ゲームバージョンは1.20.1、Javaは17、ローダーはファイルに対応する一方だけを指定する。JEIとMekanismは任意依存。

## 検証済み成果物を公開する

1. `PR validation`の両ローダー・全6名前空間と実Mekanismの計14ジョブが成功していることを確認する。
2. 全410必須GameTest、実Mekanism各2件、GUI各142枚、持ち物欄105点、機械ヘッダー80か所、数字の言語間比較5件の証跡を確認する。
3. 検証されたソースと同一のツリーを`1.20.1`へマージし、そのコミットへ`v<版>-1.20.1F`と`v<版>-1.20.1N`の変更しないタグを作成する。
4. Actionsの`Publish CurseForge Beta`を`1.20.1`で手動実行する。`verified_run_id`に成功した検証run ID、`release_tag`に対象ローダーのタグを渡す。ローダーごとに1回実行する。タグ作成だけでは公開されない。
5. ワークフローはツリー・タグ・CIジョブ・テスト件数・GUI証跡・各名前空間のJAR内容を照合し、テストしたJARをそのまま公開する。変更履歴は`changelog-<版>.md`を使用する。

`curseforge`環境の`CURSEFORGE_PROJECT_ID`（SecretまたはVariable）と`CURSEFORGE_TOKEN`（Secret）を使用する。トークンをリポジトリへ記載しない。

GitHub Releasesには変更履歴とCurseForgeへのダウンロードリンクのみを載せ、modのJARは添付しない。ワークフローはアップロード前に公開タグを予約するため、途中失敗した場合はCurseForgeの状態を確認し、重複アップロードを避ける。成功した公開ジョブは再実行しない。

アップロード後はCurseForgeの審査状況を確認する。公開後のファイル名・ゲームバージョン・ローダー・公開種別と、ダウンロードしたJARのSHA-256が検証済み成果物と一致することを確認する。
