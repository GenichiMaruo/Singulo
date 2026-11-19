# AE2などからアンドロイドの作業場を使う

Singulo 0.21.0 の `io.github.genichimaruo.singulo.api.android` を使うと、指令台に登録された作業台クラフト・機械加工・手順カードをサーバースレッドから注文できる。実行条件はゲーム内の指令台と同じで、作業場所、材料、必要な装備を持ったアンドロイドを用意する。

## 材料と納品先を接続する

1. アドオンの端末は `AndroidWorkEndpoint` を実装し、設置中保持するUUIDと所有者を返す。ケーブル上のパーツは `AndroidWorkEndpoint.CAPABILITY` を取り付けた面で公開する。
2. 所有者の操作を認証してから `AndroidWorkEndpoints.link(level, console, owner, endpoint[, face])` を呼ぶ。同じ座標には一つの面を選べる。指令台はチャンクの読込状態、所有者、設置時のUUIDを利用時にも確認する。
3. 材料の払出しと余剰品の返却用に `supplyItems()` と `supplyFluids()`、注文品の受取用に `deliveryItems()` と `deliveryFluids()` を公開する。流体はNeoForgeの `IFluidHandler` を使う。化学物質を扱うアドオンは `supplyChemicals()` と `deliveryChemicals()` で `AndroidChemicalStorage` を公開できる。同じMEストレージを見せるハンドラーは `AndroidSharedStorage` で共通のキーを返し、二重計上を防ぐ。

## 注文する

- `AndroidOrders.recipes` から読み込まれたレシピを取得する。各材料には代替候補・燃料・触媒の情報、再利用する道具には1回当たりの消耗量と必要な触媒Tierがある。副産物をパターンの確定出力に数える際には `Product.guaranteed()` を確認する。
- `AndroidOrders.submit(level, console, owner, requestId, target, count, workers, endpoint)` で完成品を指定する。流体の種類は `AndroidEscrows.fluidSample(fluidStack)`、化学物質は `AndroidRecipe.chemicalSample(id, 1)` で指定し、要求量を `count` にmB単位で渡す。端末の座標を指定すると、そこに実際に入った量が納品数になる。満杯や一時切断では残りを保持して待つ。
- 同じ依頼を再試行するときは同じUUIDを使う。`AndroidOrders.status` で納品数と完了を照会し、結果を保存してから `acknowledge` する。1回当たりに依頼できる量は `AndroidOrders.snapshot(...).maxRequestCount()` から取得できる。
- 通常注文は作業場の共有在庫を使う。AE2が渡した材料は供給端末で一度だけ在庫に反映し、納品端末が受け取った現物をMEに戻す。取消時は指令台の通常の返却先に材料が戻る。受取済みの製品も納品数に含まれる。
- 普通の作業台クラフトで依頼ごとに材料と返却物を隔離する場合は、従来の `AndroidEscrows.submit` と `claim` を使う。機械加工や手順カードをMEに公開する場合は、共有在庫を使う通常注文の経路をアドオンで選ぶ。

Applied-Singulo 0.1.1 は公開パターンを作業台クラフトに絞り、Singulo の対応範囲を `[0.20.0,0.21.0)` としている。このAPIを使うには、アドオン側で対応範囲とパターンの注文・納品処理を更新する必要がある。[パターンの選別](https://github.com/GenichiMaruo/Applied-Singulo/blob/e8106d5293f402fee416ecf34685ceac5390c0a3/src/main/java/io/github/genichimaruo/appliedsingulo/order/SinguloLinks.java#L198-L216)・[依存バージョン](https://github.com/GenichiMaruo/Applied-Singulo/blob/e8106d5293f402fee416ecf34685ceac5390c0a3/gradle.properties)
