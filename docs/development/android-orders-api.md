> 0.19.0 additions and limitations: [Android integration API](../0.19.0/android-integration.md). Source-restricted reservations are not escrow.

# Android 注文 API v1（0.18.0 開発版）

`io.github.genichimaruo.singulo.api.android.AndroidOrders` は AE2 非依存のサーバー側 API です。
導入基準: `feature/0.18.0` の `7aad405ef3184a68a536b19a82222fb2d27e3df2`。
公開版 0.17.1 には存在しません。通常の Java API であり、ネットワーク権限境界ではありません。

## 呼出し

- `submit(ServerLevel, BlockPos console, UUID actor, UUID requestId, ItemStack target, long count, List<UUID> workers, @Nullable BlockPos destination)`
- `status(ServerLevel, BlockPos console, UUID actor, UUID requestId)`
- `cancel(ServerLevel, BlockPos console, UUID actor, UUID requestId)`
- `acknowledge(ServerLevel, BlockPos console, UUID actor, UUID requestId)` → boolean

先頭3つは immutable `Receipt(requestId, state, orderId, requested, delivered, reason)` を返します。
すべてサーバースレッドから呼びます。非同期呼出しは world を読む前に例外になります。
actor はログイン済みコマンド実行者等から取得し、クライアント指定 UUID を信用しないでください。
初版は指令台の owner 本人に限定します。アンドロイドの既存操作権限も保たれます。
owner 未設定の指令台は通常の UI で所有者を設定してから利用します。

## 受付・再送

- 1〜4096個のアイテムの新規製作のみ。既存完成品の配送、液体サンプルは対象外。
- workers が空なら、操作可能な候補から既存の自動人数選択を利用します。
- destination が null なら指令台の納品箱。指定する場合は有効なリンク済み納品口が必要です。
- 受付前に UUID を呼出側で永続保存してください。同じ UUID と同じ依頼内容の再送は同じ注文の結果を返します。
- 同じ UUID で対象の種類・コンポーネント、数量、worker集合、納品先を変えると拒否します。
- 受付拒否 (`REJECTED`) は注文を作りません。理由を解消後に再試行できます。
- 受付済み注文と終端履歴を合わせ最大256件。満杯では新規受付を拒否し、古い履歴を自動削除しません。

## 状態と保存

`RUNNING` / `COMPLETED` / `CANCELLED` / `FAILED` は受付済み注文の状態です。
`COMPLETED` は既存注文の完了処理で明示記録し、指令台・納品口へ納品した数を保持します。
注文消失を成功と扱いません。履歴にある稼働中注文が見つからなければ `FAILED` になります。
`UNKNOWN` はその指令台に受付記録がない状態、`UNAVAILABLE` は指令台が未ロード・削除済み等です。
どちらも完成や安全な再発注を意味しません。

注文・履歴は指令台の NBT とともに通常保存されます。チャンク再読込/通常のサーバー再起動で保持します。
**クラッシュ耐性を持つ外部トランザクションログではありません**。保存前のプロセス強制終了で
最後の操作が失われ得ます。指令台を破壊・置換した場合もローカル履歴は引き継がれません。
その場合、呼出側は自動再送せず出力・在庫・元注文を照合してください。

終端結果を呼出側で永続保存した後 `acknowledge` で履歴を削除できます。
稼働中注文は acknowledge できません。**削除済み UUID は再利用禁止**です。
削除後の再利用は本体で検出できないため、呼出側が守る契約です。

## 材料・出力・取消の責任

この API は外部から材料を受け取りません。指令台に既存の資材口・レシピ棚・作業者が必要です。
通常の Singulo 在庫予約と作業スケジューラを使います。外部 ME の材料を消費したことにはなりません。
AE2 `pushPattern` の全受理契約や材料隔離を実装済みとは見なせません。
出力の ME 挿入、搬出経路、満杯時の保持はアドオン側の責任です。
取消は未実行分を停止しますが、既製作・既納品分を取り消しません。運搬中の材料は既存処理で返却します。
receipt の delivered だけから材料全量が返却されたと判断しないでください。

## 検証

`gradlew runGameTestServer -PtestClasses=AndroidOrderApiGameTests -PtestNamespace=singulo`
で所有権、数量・納品先、再送/衝突、取消、保存復元、消失判定、履歴上限、スレッド制限、
実際のアンドロイド製作・納品完了を検証します。配布 JAR に GameTest クラスは含めません。

## Read-only console snapshot

`snapshot(ServerLevel, BlockPos, UUID actor)` returns `ConsoleSnapshot` with
`SnapshotState.AVAILABLE`, `REJECTED` (not the current console owner), or `UNAVAILABLE`.
The immutable, detached `receipts` list contains only that actor's external API receipts.
`activeCount` counts RUNNING receipts; `historyCount` counts terminal receipts.
Neither count includes built-in UI orders or another owner's receipts.
`receiptCapacity` is 256; `remainingReceiptCapacity` reflects the **whole** ledger,
including retained previous-owner receipts; `maxRequestCount` is 4096.
Denied/unavailable snapshots have no receipts and zero counts/remaining capacity.
The static capacity/count limits remain visible. No inventory, worker or mutable order references escape.

This is a point-in-time display/reconciliation aid, **not a reservation or acceptance guarantee**.
Available space does not imply recipes, workers, materials or a delivery port are available.
Missing active orders appear as FAILED in the view without mutating persisted ledger state.
Normal status/cancel reconciliation and acknowledgement remain explicit operations.
Snapshot does not load chunks, and must run on the server thread like the other API methods.

Validation: the base `7aad405e` inventory already contained 739 required tests
(740 including the optional Mekanism namespace), while the release guard still said 732.
The 12 Android API tests bring the default suite to 751. All 751 were executed and
passed before updating the release guard to 751; no test was excluded or retried.
Commands: `gradlew --no-daemon build`, focused `runGameTestServer
-PtestClasses=AndroidOrderApiGameTests -PtestNamespace=singulo`, and full
`gradlew --no-daemon runGameTestServer -PtestTickRate=200`.
