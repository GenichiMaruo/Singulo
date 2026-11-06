# PR #62: Actionsとマージ前の検査

2026-11-06。対象は1.21.1用のPR検証、CurseForge公開、Modrinth公開と、両公開フローが使う検証・昇格コード。
公開ワークフローの実行、シークレット値の読み出し、請求設定・リポジトリ保護設定の変更はしていない。
保護設定については秘密を含まない状態だけを読み取り確認した。

## 修正した問題

### CurseForgeへの不確実な送信を自動再試行していた

固定された [mc-publishのaction.yml](https://github.com/Kira-NT/mc-publish/blob/52307b03863581dec6b652b83e597aec02ebb075/action.yml) の `retry-attempts` 既定値は2。
[GenericPlatformUploader.upload](https://github.com/Kira-NT/mc-publish/blob/52307b03863581dec6b652b83e597aec02ebb075/src/platforms/generic-platform-uploader.ts) は再試行対象のエラーで `uploadCore` を再実行する。
送信先がファイルを受理した後で5xxを返した場合、GitHub Releaseのdraft予約があっても、同じジョブ内の2回目の投稿は防げない。

`retry-attempts: 1` を明示し、`fail-mode: fail` とdraft予約による手動再実行防止を維持した。
回帰検査で1試行だけであること、プラットフォーム別の再試行上書きがないこと、draft予約が送信より前であることを要求する。
実際の不確実な投稿や二重投稿を発生させて試験したものではない。
action内部の既知の入力エラーに対する修正処理とは区別する。

**1.20.1側にも同じ既定値の問題が残る**。
読み取り時のブランチは `7d7cd7f8b4985fe75e93935df34a745eef826914`。
同じ固定actionを使い、`retry-attempts` の指定がない。今回のPRには別ブランチの修正を混ぜていない。

### 過去の成果物を現ブランチのテスト一覧で誤拒否していた

検証コードは信頼するworkflow-refから実行する必要があるが、比較するテスト一覧まで同じ参照元に固定されていた。
PC上の0.15.1成果物を現在の検証コードへ渡すと `expected 701 successful tests, got ['662']` で拒否することを再現した。

`promote` から、事前にコミット・タグ・ツリーを照合した `release-source` を検証関数へ明示的に渡すよう修正。
信頼済みの検証コードが、そのソースにあるJavaのテスト宣言を読み取り、件数だけでなく個々の登録名と結果も照合する。
タグに含まれるPython検証コードは実行しない。
通常のPR/ローカル検証では、現在の701件という固定ガードも維持する。

修正後は同じ0.15.1成果物の662件を正しく受理した。
一時的な旧版ソースを使い、件数が同じでも別のテストを混ぜた結果は拒否し、タグ内のPythonを実行しない回帰検査も追加した。
これは検証関数の試験であり、その過去版の再公開はしていない。

### 未マージのタグからも公開できる余地があった

タグと成功CIのツリー照合だけでは、そのタグが維持ブランチへ取り込まれたとは限らない。
タグのコミットを基点、`1.21.1` を比較先とするGitHubの比較結果が `ahead` または `identical` の場合だけ昇格を許可する。
`behind`・`diverged` は拒否する。過去のマージ済みタグは受理できることも単体検証する。
CurseForgeの手動起動にも、既存Modrinthと同じ `refs/heads/1.21.1` のガードを追加した。

## 権限・秘密・依存Action

- PR検証は `pull_request`、`contents: read`。公開用シークレットを参照せず、`pull_request_target` や公開の自動連鎖も使わない。
- 公開は両サイトとも手動起動だけ。入力値は環境変数と引用済みの引数で渡し、shell script内へ式を直接展開しない構成を維持した。
- 全Actionを上流リポジトリで確認した40桁のコミットへ固定した。`mc-publish` の既存SHAも維持する。
- 全checkoutに `persist-credentials: false` を設定。Git資格情報を作業ツリーのGit設定へ残さない。
- 公開ジョブ全体の `GH_TOKEN` 環境変数をやめ、API検証・昇格・Release予約と説明更新の各ステップだけへ渡す。
  これはプロセス環境の露出範囲を減らす対策であり、job token自体の権限をステップごとに分離したという意味ではない。
- Modrinthは `contents: read` / `actions: read`。CurseForgeの `contents: write` はassetなしReleaseの予約・説明更新に必要で、`actions: read` は検証済み成果物の取得に使う。
- サイト用トークンは公開ステップだけで参照する。値・実際のトークンスコープ・有効期限は検査していない。
- Modrinthの既存ハッシュ照合、公開済みファイルの厳密な重複回避、不確実なPOSTの非再試行とreceipt保存を確認し、維持した。

権限最小化・環境変数による入力処理・上流コミットへの固定の根拠は [GitHubのSecure use reference](https://docs.github.com/en/actions/reference/security/secure-use)。

## 成果物・実行量・検査

公開はタグだけでビルドや送信を開始しない。成功したPR検証の3ジョブ、タグのコミット、実際に試験したマージツリーを照合し、CIのJARを再ビルドせず昇格する。
JARのJava 21・Minecraft/NeoForge・版・開発用クラスの混入、テストの完全一致、262画面のGUI証拠を検査する。
0.16.0で追加した35画面・18状態・シェーダー再読み込みは、PRワークフロー内でも成功を必須とする。

PR検証は3ジョブのまま。タイムアウトは35分・20分・15分（各上限の合計70 runner-minutes）、JavaのCPU数とGradleのworker数は2へ制限する。
古い同一PR実行はキャンセルする。公開は15分で打ち切り、重複実行の制御と、入力・ツリーの検査に失敗したときの停止を維持した。
検査を間引いたり、失敗を無視して成功にする変更はしていない。

- 公式配布とSHA-256を照合した **actionlint 1.7.12** で3ワークフローのYAML・式・既知のAction入力を検査し、問題なし。
  ShellCheckとpyflakesはこのWindows検証では未実行。
  全14個の複数行Bashブロックは、実行せず `bash -n` でも構文検査して成功した。
- Python **65件**が成功。Actions境界、誤ったタグ・ツリー、未マージタグ、変更されたタグ作業コピー、タグ内Pythonの非実行、旧版成果物の件数・名前、Java/JAR検査、Modrinthの重複・不確実応答を含む。
- 通常GameTest **701件**と配布JAR検査も成功。実2クライアント・再接続・チャンクの実アンロード/再ロード、両画面へのポート変更の同期を追加確認した。
- 専用サーバーの通常終了・再起動で、3装置の内容・long蓄電・予約成果物・進行・左右ポート設定の保持、再形成後のポート再接続を確認した。

実ネットワークの記録画像は [クライアントA](images/native-spindle-client-a.png)・[クライアントB](images/native-spindle-client-b.png)。
ポート変更パケットを処理し、両画面が同期した後に2フレーム描画されてから撮影した。
これはループバックに限定したofflineの検証サーバーで、画像内のチャット署名警告はその検証設定によるもの。
ユーザーの通常のサーバー設定は変更していない。

## CIとGitHubの保護設定は別

PR #62のrun `42182540523` は再実行（attempt 2）も全3ジョブがステップ開始前に失敗した。
診断は `The job was not started because your account is locked due to a billing issue.`。
請求設定は変更せず、失敗を無視した公開や、ローカル結果をGitHub CI成功として扱うこともしていない。

読み取り時点で `1.21.1` は `protected=false`、唯一のbranch ruleset `protect_rule` は `enforcement=disabled`。
必須status checkは設定されておらず、`curseforge`・`modrinth` 環境にも保護規則・deployment branch policyはない。
上の「必須」は**ワークフローと公開前検査が成功を要求する**という意味で、GitHubがマージを強制的に阻止する設定とは異なる。
タグも、検証時のコミット照合とサーバー側の不変性保護を混同しない。
これらの権限・保護設定は今回変更していない。

ローカルで見つかった阻害要因は修正・再検証済み。マージと公開は行っていない。
リモートCIの正常実行と、実際に変更をマージするユーザーの判断は引き続き必要。

## 公開安全性修正の維持ブランチへの分離

0.15.1公開に0.16.0全体の取り込みを必要とさせないため、安全対策を別ブランチへ用意した。
1.21.1向けは [PR #63](https://github.com/GenichiMaruo/Singulo/pull/63)、1.20.1向けは
[`fix/release-actions-1201`](https://github.com/GenichiMaruo/Singulo/tree/fix/release-actions-1201)。
各維持ブランチの製品ソース・Gradle設定・版番号と、#60/#61のHEADは変えていない。
1.20.1ではタグ内のPython検証コードの実行をなくし、信頼済み検証コードでタグのテスト一覧・Java 17・loader・版を検査する。
共通の検証では、無視対象のファイルを含めタグ作業コピーが変更されていれば公開を拒否する。

既存の0.15.1 CI成果物をローカル昇格し、準備済み3 JARとバイト単位で一致することを確認した。
[取り込み順の記録](https://github.com/GenichiMaruo/Singulo/blob/ccaf33d10ca9ae50fd13fba9864e1cb8726dbea3/docs/development/actions-release-safety.md)に従い、
既存成果物を使う場合はリリースPRのマージと全ツリー照合・タグ作成を先に行い、その後に安全な公開コードを取り込む必要がある。
安全対策を先にマージした場合は旧CIのツリーが一致しなくなるので、検証を緩めずリリースPRのCIと成果物を更新する。
今回、実際のマージ・タグ作成・公開・条件付きタスク変更は行っていない。
