# Minecraft 1.20.1の公開

1. `1.20.1`向けPRで4ジョブの検証を通す（両ローダー全件・GUI、Forgeで実Mekanismと描画連携）。
2. PRのツリーを変更せずにマージし、そのコミットに`v<version>-1.20.1F`と`v<version>-1.20.1N`を作成する。
3. `Publish CurseForge Beta`を各タグと成功したPR検証のrun IDで実行する。

公開処理は登録テスト名・各成功結果・GUI検証・JAR・Gitツリーを照合し、検証済みJARを再ビルドせずに昇格する。タグを付けただけでは公開しない。GitHub Releasesにはmod JARを添付せず、CurseForgeへのリンクだけを掲載する。
