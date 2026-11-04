# Minecraft 1.20.1の公開

1. `1.20.1`向けPRで4ジョブの検証を通す（両ローダー全件・GUI、Forgeで実Mekanismと描画連携）。
2. PRのツリーを変更せずにマージし、そのコミットに`v<version>-1.20.1F`と`v<version>-1.20.1N`を作成する。
3. `Publish CurseForge Beta`を各タグと成功したPR検証のrun IDで実行する。

公開処理は登録テスト名・各成功結果・GUI検証・JAR・Gitツリーを照合し、検証済みJARを再ビルドせずに昇格する。タグを付けただけでは公開しない。GitHub Releasesにはmod JARを添付せず、CurseForgeへのリンクだけを掲載する。


## Modrinth publishing

Modrinth distribution is supported alongside CurseForge. GitHub Releases still contain no distribution JARs.

- Environment `modrinth`: secret `MODRINTH_TOKEN` with **Create versions** only, and secret or variable `MODRINTH_PROJECT_ID=8KO6x1Ol`.
- The project must first be approved and publicly readable. The workflow stops while it is under review.
- Run **Publish Modrinth** manually from this Minecraft branch, using the same successful PR validation `verified_run_id` and immutable `release_tag` as CurseForge. On 1.20.1, use the F or N tag for the corresponding loader and dispatch once for each loader.
- Existing provenance, exact source-tree, GameTest and GUI checks promote the tested CI JAR without rebuilding. Tags and versions are not changed. CurseForge publication does not automatically trigger Modrinth.
- Versions use `<mod_version>-<minecraft><F|N>`; both sides are required, JEI and Mekanism are optional. 0.x releases are Beta (1.20.1 follows the existing Beta channel).
- All Modrinth publications are serialized across branches. An existing version with exactly matching filename, SHA-512, Minecraft version, loader and channel is skipped. Any collision stops for manual inspection. The already uploaded 0.14.1 files must not be duplicated.
- A failed/uncertain upload is never automatically retried. Inspect Modrinth and the `modrinth-receipt-*` workflow artifact before rerunning; the receipt records whether a POST began. Do not overwrite existing files. Publication does not create or alter GitHub Releases or submit the project for moderation.
- Run `python3 -m unittest discover -s tools -p test_publish_modrinth.py` to check upload safety without uploading.
