26.1.2 publication additionally re-fetches the merged PR (using commit-associated PR discovery when Actions omits its association), requires one matching merged head/base/repository/tree and maintained-branch ancestry, and checks all twenty outline entity cases plus five 1280x800 images. The always-retained `cf-publication-receipt.json` records documented non-secret publisher outputs, exact JAR/provenance hashes, CI attempt and permanent lock. An accepted receipt does not assert moderator approval. Missing or inconsistent successful outputs remain unknown and require manual investigation, never automatic resubmission.

# Minecraft 26.1.2 NeoForge publication

This branch publishes only `Singulo-<mod_version>-26.1.2N.jar` with Java 25. No Forge, Mekanism or unsupported Ponder compatibility is claimed. Maintain older releases on their original branches and do not move existing tags.

1. Complete successful **pull_request** CI on the exact release tree. Both and only `build-and-gametest` and `sodium-lighting` must succeed. Merge into maintained `26.1.2` only after review.
2. Create the immutable tag `v<mod_version>-26.1.2N` on that merged commit (currently `v0.19.0-26.1.2N`). Tag creation never uploads.
3. Dispatch `publish-curseforge.yml` from `26.1.2` with that tag and its successful PR run ID. Use `release_type=auto` for the 0.x Beta channel.
4. `verified_release_2612.py` checks tag/branch/tree equality and independently binds both attempt-suffixed artifact names to their successful producer timestamps. Both must name the same tested commit and exact release tree. Missing, duplicate, stale, expired, failed or ambiguous evidence stops publication.
5. Download each archive by immutable artifact ID, verify the API SHA-256 digest, safely extract it, then recheck a complete per-file SHA-256 receipt. The production JAR and all GUI, cosmic, occlusion, lens, standard lighting, Sodium/DH, and rejoin evidence are checked before copying the tested JAR. No rebuild or validation bypass is permitted.
6. Keep the permanent shared CurseForge lock, collision-checking draft release reservation, and single upload attempt. If sending fails or is uncertain, inspect the actual CurseForge state and receipts; never automatically retry or delete the permanent lock.

Rayleigh's four zero-tolerance lighting captures use identical review-only fog distances (32..1024); a fifth image retains actual rainy fog. This isolates lighting from Minecraft 26.1's near-camera rain fog and does not change production weather. Ponder has no verified 26.1.2 dependency and is explicitly unsupported, not silently marked as passed.

## Historical release instructions (other maintained branches)

# CurseForge ファイルのアップロード

Actionsを利用できない場合の、同じ成功CI成果物と来歴検証を使う経路は
[ローカルCurseForge公開](local-curseforge.md)を参照。アップロードには別途の明示承認と作者画面での全状態確認が必要。

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

1. `mod_version`と対象版の[変更履歴](../curseforge)を更新したPRで、PR validationを成功させる。[現行の検査手順](testing.md)とPR validationの必須ジョブを通過させる。過去版のテスト件数を使わない。
2. PRを`1.21.1`へマージし、マージ後のソースが成功CIの検証対象ツリーと一致することを確認する。
3. マージコミットに`v<mod_version>`タグを作成する。既存タグは移動しない。
4. ActionsのPublish CurseForgeを`1.21.1`から実行する。`verified_run_id`に成功したPR validationの実行ID、`release_tag`に上記タグ、`release_type`に通常`auto`を指定する。
5. タグ・PR・実際のCI検証ツリー・JARの版とローダー・GameTestとGUI完了を検証してから、そのCIのJARをCurseForgeへ送信する。

`curseforge`環境の`CURSEFORGE_PROJECT_ID`（SecretまたはVariable）と`CURSEFORGE_TOKEN`（Secret）を使う。トークンをファイルへ記載しない。

版番号は`X.Y.Z`形式。公開候補は`X.Y.Z-rcN`（Nは1以上。例: `1.0.0-rc1`）で、タグは`v1.0.0-rc1`、変更履歴は`changelog-1.0.0-rc1.md`。`auto`では0.x.yと`-rcN`をBeta、1.0.0以降の正式版をReleaseとする。手動で`beta`・`release`・`alpha`も指定できる。

1.20.1の公開は[そのブランチのワークフロー](https://github.com/GenichiMaruo/Singulo/blob/1.20.1/.github/workflows/publish-curseforge.yml)に従う。1.21.1の実行ID・タグ・JARを転用しない。

## CurseForgeでの登録内容

| Minecraft | ローダー | Java |
| --- | --- | --- |
| 1.21.1 | NeoForge | 21 |
| 1.20.1 | Forge / NeoForge（ファイル別） | 17 |

- Environment: Client / Server。
- Related projects: JEIとMekanismはOptional Dependency。
- Changelog: 対象版の`docs/curseforge/changelog-<版>.md`（Markdown）。
- Description: [description.md](../curseforge/description.md)。
- License: `gradle.properties`の`mod_license`に合わせる。

## 送信後

GitHub Releaseには変更履歴とCurseForgeの案内だけを掲載し、配布JARを添付しない。

公開処理は送信前に添付ファイルのないdraftを予約する。失敗時はCurseForgeの登録状態とdraftを確認し、成功済みの送信を再実行しない。送信成功とCurseForgeの審査・一般公開は別なので、公開一覧と配布ファイルも確認する。


## Modrinth publishing

Modrinth distribution is supported alongside CurseForge. GitHub Releases still contain no distribution JARs.

- Environment `modrinth`: secret `MODRINTH_TOKEN` with **Create versions** only, and secret or variable `MODRINTH_PROJECT_ID=8KO6x1Ol`.
- The project must first be approved and publicly readable. The workflow stops while it is under review.
- Run **Publish Modrinth** manually from this Minecraft branch, using the same successful PR validation `verified_run_id` and immutable `release_tag` as CurseForge. On 1.20.1, use the F or N tag for the corresponding loader and dispatch once for each loader.
- Existing provenance, exact source-tree, GameTest and GUI checks promote the tested CI JAR without rebuilding. Tags and versions are not changed. CurseForge publication does not automatically trigger Modrinth.
- Versions use `<mod_version>-<minecraft><F|N>`; both sides are required, JEI and Mekanism are optional. `release_type=auto` selects Beta for 0.x and release candidates (`X.Y.Z-rcN`, e.g. `1.0.0-rc1`) and Release for stable 1.0.0 and later, or select a channel explicitly.
- All Modrinth publications are serialized across branches. An existing version with exactly matching filename, SHA-512, Minecraft version, loader and channel is skipped. Any collision stops for manual inspection. The already uploaded 0.14.1 files must not be duplicated.
- A failed/uncertain upload is never automatically retried. Inspect Modrinth and the `modrinth-receipt-*` workflow artifact before rerunning; the receipt records whether a POST began. Do not overwrite existing files. Publication does not create or alter GitHub Releases or submit the project for moderation.
- Run `python3 -m unittest discover -s tools -p test_publish_modrinth.py` to check upload safety without uploading.
