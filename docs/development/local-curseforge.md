# GenPC-MainからのローカルCurseForge公開

GitHub Actionsの実行環境が使えない場合の、Singulo専用の代替経路。
**既に成功した正式CIの成果物が必要**。ローカルのビルドやテスト結果を成功CIに置き換える機能ではない。
1.21.1 NeoForge、1.20.1 Forge / NeoForgeをそれぞれ1ファイルずつ扱う。Modrinthは対象外。

この機能を作った時点ではアップロード、認証情報の設定、権限変更、マージを行っていない。
利用前にコードをレビューし、この経路での公開と認証情報利用について別途承認を得ること。

**現時点の送信は無効**。対応する維持ブランチのActionsに、同じ
[原子的ロックプロトコル](curseforge-atomic-lock.md)が導入済みと検証できた場合だけ送信へ進む。
未導入、旧コード、異なるヘルパーではprepareは可能だが `upload_protocol_ready=false` となり、uploadは停止する。
導入には本ブランチの1.21.1変更と、別の1.20.1修正ブランチのレビュー・承認・マージが必要。

## 準備のみ（リモートは読み取り）

Python 3.12、Git、既存の認証済みGitHub CLI `gh` が必要。準備にはCurseForgeトークンは不要。
GitHubのAPIとCI成果物ダウンロードは利用するが、Actionsのジョブは起動しない。
GitHub自体が利用不能、成果物が期限切れ、または対象版の成功CIがない場合は停止する。

1. 正式なリリースPRをマージし、成功CIの**全ツリー**との一致を確認して不変タグを作る。
2. 公開安全対策を取り込んだ維持ブランチのGitオブジェクトをローカルに取得する。
3. タグの読み取り用worktreeを作る。未追跡・無視対象ファイルも置かない。
4. 状態、キャッシュ、バンドルはタグworktreeの外に置く。既存バンドルは上書きしない。

PowerShellの例（パスは実際の作業場所に変更する）。以下は準備だけを実行する。

```powershell
git fetch --no-tags https://github.com/GenichiMaruo/Singulo.git 1.21.1 1.20.1
git fetch https://github.com/GenichiMaruo/Singulo.git tag v0.15.1
git worktree add --detach D:\releases\singulo-tag-1211 v0.15.1
python tools/local_curseforge.py prepare `
  --tag v0.15.1 --run 42002407263 `
  --source D:\releases\singulo-tag-1211 --validator-repo . `
  --archive-cache D:\releases\ci-cache --bundle D:\releases\plan-0151-1211
```

`--validator-repo` は対応する維持ブランチのオブジェクトを持つリポジトリ。
その作業コピー内のPythonは実行せず、GitHub上の現行維持ブランチSHAからGitアーカイブを作り、
隔離したPythonで既存の `verified_release_1211.py` / `verified_release.py` のcheckとpromoteを実行する。
**タグ内のPythonは実行しない**。
CIの全ジョブ・リポジトリ・タグ・HEAD/実テストマージツリー・GameTest・GUI・Java/loaderを従来どおり検証する。
さらに、ダウンロードZIPをGitHubが返す成果物のSHA-256と照合してから展開する。

1.20.1はタグworktreeと引数を次のようにする。`--run` は両方 `42002426072`。

| タグ | 対象 | バンドル例 |
| --- | --- | --- |
| `v0.15.1-1.20.1F` | Forge / Java 17 | `D:\releases\plan-0151-1201F` |
| `v0.15.1-1.20.1N` | NeoForge / Java 17 | `D:\releases\plan-0151-1201N` |

各タグは対応する不変タグを取得して別の読み取りworktreeにする。1.21.1のrun/JARを流用しない。
以後の版では、その版の実際の成功CIのIDを指定する。タグの再配置、検証の無効化、再ビルドはしない。
prepareは `plan.json`、確認ログ、検証器の固定スナップショット、元の成果物、promoted JARをローカルに保存する。
画面に出る `plan_sha256` とJARのSHA-256、タグ、対象版・loader・changelogをレビューする。

## 全状態の事前確認（必須の手動工程）

公式Upload APIの文書には、作者の審査中・処理中・手動公開待ちを含む全ファイル一覧APIが記載されていない。
公開一覧だけでは審査中の重複を検出できない。このツールは未公開の内部APIや他者のAPIキーを利用しない。
**全状態の確認は自動取得ではなく、作者による記録を信頼する**。この点は機械的には保証できない。

作者画面でプロジェクト1734662を開き、公開・審査中・手動公開待ち・処理中・却下・アーカイブを含む
全ページを確認する。他のPC・担当者・手動操作・自動処理が同時に送信しないことも確認する。
表示やアクセスに不足がある場合、または状態が不明な場合は**アップロードしない**。
確認した全ファイルのID・正確なファイル名・状態をJSONに記録する。確認時刻から5分以内だけ有効。
`sha256` は判明している場合だけ任意で付けられる。同じファイル名、または同じSHAのファイルは状態を問わず送信を拒否する。
未知の状態を勝手に公開扱いに変換しない。

次は**未確認状態のテンプレート**。真に確認できた場合だけ値と時刻と全ファイル行を埋める。
トークン、メールアドレスなどの個人情報は入れない。

```json
{
  "schema": 1,
  "project_id": 1734662,
  "checked_at": "REPLACE_WITH_ACTUAL_UTC_ISO8601_TIME",
  "all_statuses_accessible": false,
  "all_pages_checked": false,
  "no_other_publishers": false,
  "files": []
}
```

ファイル行の形式は `{"id": 123, "filename": "Singulo-...jar", "status": "pending"}`。
状態は `published` / `pending` / `manual_release` / `processing` / `rejected` / `archived`。
空配列は本当にファイルが一件もないプロジェクトに限る。現在のSinguloでは過去の全ファイルを記録する必要がある。
このJSONは実際の作者画面を確認したという人の証明であり、APIから取得した情報と偽って扱わない。

## 送信（将来の明示承認後にだけ実行）

GitHub CLIには既存リポジトリのRelease作成権限が必要。新たな権限をこのツールが設定することはない。
CurseForgeのトークンは送信時に隠し入力する。環境変数・引数・URL・設定ファイルからは受け取らず、保存しない。
非対話端末や隠し入力できない環境は拒否する。チャット、シェル履歴、画面共有、ログに貼り付けない。
Python文字列を確実にメモリ消去する保証はないため、信頼できるPCでのみ利用する。

```powershell
python tools/local_curseforge.py upload `
  --plan D:\releases\plan-0151-1211\plan.json `
  --plan-sha256 <prepareで確認した64桁のSHA-256> `
  --inventory D:\releases\author-inventory.json `
  --state-dir D:\releases\singulo-persistent-state
```

実行時に最新維持ブランチの検証器で来歴と元のJARを再検証する。
維持ブランチ、来歴、メタデータ、JARが計画時から変わった場合は停止し、新しい計画のレビューが必要。
ロック取得後のPOST直前にも、タグ、維持ブランチSHA、元CIの成功状態・試行番号、Gitロック参照、draftのID・状態・添付なしを再確認する。
表示された対象とSHAを確認し、指定された完全な確認文を入力してからトークンを隠し入力する。
プロジェクトIDはSinguloの1734662に固定。MC、loader、Java、Client/ServerのIDは送信直前の公式カタログから
種類ごとに一意に解決し、曖昧なら停止する。JEIとMekanismはOptional、changelogは検証済みタグのMarkdown。
既存と同様、0.xは既定でBeta。チャンネル変更はprepareの `--channel` で明示して再レビューする。
`isMarkedForManualRelease=false` のため、承認後はCF側で一般公開され得る。

## 予約・不確実な結果・復旧

- ローカルではOSのプロジェクトロックを保持する。同じ永続stateディレクトリを使い、同期フォルダへ移動・削除しない。
- GitHubの認証済みRelease一覧を全ページ確認する。タグ別GETはdraftを返さない場合があるため使わない。
  複数draftが同じタグで存在しても全て検出し、既存draft/公開Releaseがあれば停止する。
- **draft Releaseは排他ロックではない**。ローカルと修正版Actionsの両方が
  `refs/tags/singulo-cf-upload-lock-v1/<正規タグ>` をcreate-onlyで作成する。
  Git参照名の一意性により一つの作成だけが成功する。失敗・不明な応答では既存参照を採用せず停止する。
  同じ版の改訂タグも同じファイル名のロックを使う。更新・削除・自動解除は実装しない。
- ロック取得後のdraft Releaseは配布物なしの監査記録。作成応答のIDで以後の状態を取得し、タグ別GETに頼らない。
  旧版Actionsが待機/実行中なら停止する。旧ワークフローの再実行、手動送信、ロック削除など、
  プロトコルを無視する操作までは排他できない。導入時に旧実行を完了させ、担当者間で並行操作を止める必要がある。
- 予約意図、予約ID、送信開始意図を順に原子的置換とfsyncで保存する。送信開始が永続化できなければPOSTしない。
  POSTは最大1回。認証情報付きリダイレクト、エラー時の別メタデータ再送、429/5xxの再試行はしない。
- 実際に送るJARは検証済みハッシュと照合した同じメモリ上のbytes。後からディスクを変更しても送信内容を差し替えない。
- timeout、切断、応答破損、5xx、割り込み、クラッシュ、成功後の記録失敗は不確実として扱う。
  ローカルreceiptとGitHub予約を残し、次の呼出しでも自動再送しない。
  ディスクや電源障害時の完全な永続性はOS/ハードウェアに依存するが、別途の永続Git参照も再送を遮断する。
- 返却IDを得ても状態は `submitted_awaiting_moderation`。アップロード成功と一般公開は別。
  GitHub Releaseはdraftのまま残す。自動で公開ノートに変更しない。

不明な結果では、作者画面の全状態とCFのファイル内容・ハッシュ・版・loaderを確認する。
別の端末で再実行したり、stateやdraftを削除して再送したりしない。

```powershell
python tools/local_curseforge.py reconcile `
  --receipt D:\releases\singulo-persistent-state\v0.15.1.json `
  --inventory D:\releases\author-inventory.json
```

reconcileは新しい手動観測を別ファイルに残すだけで、元のreceipt/予約は変更せず、再送許可も作らない。
一覧にない場合も受理済み・表示遅延の可能性がある。CurseForge側で未受理と確定できるまで停止する。
本ツールに強制解除・削除・リセット・再送コマンドはない。再送が必要な場合は、その根拠と新たな明示承認を伴う別作業にする。
一般公開と内容の一致が確認できた後のGitHub Releaseノート整理も、別途レビューして実施する。

## 検証範囲

`python -m unittest discover -s tools -p test_local_curseforge.py` はモック／ローカルだけで実行する。
実トークン、実際のCF送信、審査結果、全状態一覧の真偽はオフラインテストでは検証できない。
既存の成功CI成果物を使ったprepareは読み取りだけで別途検証する。これは新しいCI成功ではない。

旧コミット2e48f09bではdraftの検索と一意性について誤った仮定があり、独立レビューで検出された。
修正版のテストは同一タグの複数draftを許し、タグGETが404でもIDでdraftを取得する実際の意味論に合わせている。
原子的Git参照の作成、不確実な応答、旧Actions混在時の拒否もオフラインで検査する。
実際のGit参照作成・CF送信による検証と修正版に対する第三者の承認は未実施。

一次資料: [CurseForge Upload API](https://support.curseforge.com/support/solutions/articles/9000197321-curseforge-api)、
[固定済みmc-publishのUpload API実装](https://github.com/Kira-NT/mc-publish/blob/52307b03863581dec6b652b83e597aec02ebb075/src/platforms/curseforge/curseforge-upload-api-client.ts)。
