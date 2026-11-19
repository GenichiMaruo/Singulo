# Modパックの難易度と機械性能を調整する

レシピ1件の変更にはデータパック、同種の機械やボス全体の変更にはサーバー設定を使います。既存レシピのIDをゲーム内で調べる方法と、材料・液体・配置・出来高のJSON例は[レシピ調整ガイド](recipe-packs.md)にあります。

サーバー設定は初回起動後に生成される `config/singulo-server.toml` にあります。専用サーバーならサーバー側、シングルプレイならワールドを開くインスタンス側で編集します。該当する見出しの既存の値を書き換え、ワールドを開き直すかサーバーを再起動してください。データパックだけの変更は `/reload` で反映できます。

## 例　組立台に拡張を4個まで付け、焼成炉で16回分まとめて加工する

```toml
[machines]
stockUpgradeLimit = 4
extraStacksPerUpgrade = 3
machineSlotStacks = 16
machineBatchLimit = 16
machineTankCapacity = 64000
overclockSpeedMultiplier = 8.0
overclockPowerMultiplier = 12.0

[power]
machineEnergyMultiplier = 1.5
machineBufferMultiplier = 8.0
generatorOutputMultiplier = 2.0

[forge]
parallelBatchLimit = 8

[ruins]
bossHealthMultiplier = 5.0
bossDamageMultiplier = 1.25
```

この例では、通常64個スタックの材料は組立盤の1マスに最大832個入ります（64 × (1 + 4 × 3)）。通常の焼成炉なら材料・製品の各枠に1,024個、水や酸素はタンク1基につき64,000 mB入ります。精密組立台の図柄では、1回の組み立てにつき埋めたマスから各1個を使います。

## 設定一覧

以下の「整数上限」は **2,147,483,647** です。

| 区分 | キー | 既定値 | 設定範囲・単位 |
|---|---|---:|---|
| machines | `stockUpgradeLimit` | 1 | 装着数1～整数上限個 |
| machines | `extraStacksPerUpgrade` | 3 | 1個につき0～整数上限スタック増加。各マスの実個数は整数上限まで |
| machines | `machineSlotStacks` | 1 | 汎用加工装置の通常の材料・製品枠につき1～整数上限スタック。重ならない品は1個、使用回数付きの道具は材料枠に1個 |
| machines | `machineTankCapacity` | 8,000 | 汎用加工装置のレシピ用タンク1基につき1～整数上限 mB |
| machines | `machineBatchLimit` | 1 | 1工程につき1～整数上限回。材料・液体・蓄電・出力の空きで実数を決定 |
| machines | `overclockSpeedMultiplier` | 3.0 | チップ装着時の速度を0.000001～10億倍。処理時間は最短1 tick |
| machines | `overclockPowerMultiplier` | 4.0 | チップ装着時の消費電力を0～10億倍。総電力量固定の工程では短縮後の時間からFE/tを求める |
| power | `machineEnergyMultiplier` | 1.0 | 加工レシピのFE/tを0～10億倍 |
| power | `machineBufferMultiplier` | 1.0 | 汎用加工装置の蓄電容量・受電速度・容量アップグレードを1～10億倍 |
| power | `generatorOutputMultiplier` | 1.0 | 発電量を0～10億倍。熱・燃料・触媒を使う発電機、衝撃発電、Pリアクターのペレット発電が対象 |
| forge | `parallelBatchLimit` | 4 | 並列カセット装着時に1～整数上限回。材料・冷却材・電力と鍛造炉の通常の出力枠に収まる回数で開始 |
| ruins | `bossHealthMultiplier` | 1.0 | 全ボスの体力を0.000001～10億倍 |
| ruins | `bossDamageMultiplier` | 1.0 | ボスによる攻撃ダメージを0～10億倍 |

### 輸送・段階別レシピ・専用装置を調整する

| TOML の見出し | キー | 既定値と意味 |
|---|---|---|
| `[transport]` | `copperCableRate` / `superconductingCableRate` / `topologicalCableRate` / `horizonCableRate` | 2,000 / 1,000,000 / 100,000,000 / 9,223,372,036,854,775,807 FE/t |
| `[transport]` | 同じ接頭語の `CableLoss`（例 `copperCableLoss`） / `minimumCableEfficiency` | 1ブロックあたりの損失率は銅が0.001、ほかは0。経路全体で残る電力の割合の下限は0.5。各値は0～1 |
| `[transport]` | `pumpStartFe` / `pumpFeAtHighFlow` | 1 mB/t と 21億 mB/t の消費電力。既定は 100 FE/t と 10,000,000 FE/t。搬出した tick だけ、2点を直線で結んだ料金を FE 単位で切り上げる。21億 mB/t を超えても同じ傾きで増え、後者を前者以下にすると定額になる |
| `[transport]` | `pumpFePerMb` | 既定の 0 で上記の流量別料金を使う。正の値を指定すると従来の固定単価（FE/mB、最小 0.001、0.001 刻み）を使い、1 FE 未満の前払い分を次回に繰り越す |
| `[transport]` | `copperPipeRate` / `vacuumPipeRate` / `superfluidPipeRate` / `horizonPipeRate` | 2,000 / 32,000 / 512,000 / 9,223,372,036,854,775,807 mB/t。パイプ・ケーブルとも転送上限は1以上 |
| `[stages]` | `stage1TimeMultiplier` ～ `stage5TimeMultiplier` / `stage1EnergyMultiplier` ～ `stage5EnergyMultiplier` | すべて1.0。レシピの `stage` に応じて時間と電力を独立に変更。時間は0.000001～10億倍、電力は0～10億倍 |
| `[android]` | `androidTankCapacity` / `androidDualTankCapacity` | 通常32,000、複合16,000。各室の最大量。液体とMekanismの化学物質に共通 |
| `[android]` | `androidBatteryCapacity` | 基本充電量2,800。内蔵・増設バッテリー1個につき基本値の半分を加算 |
| `[android]` | `dockChargePerTick` / `portableChargePerTick` / `solarChargePerTick` / `restChargePerTick` | 28 / 28 / 14 / 10 充電量/t。0でその方式からの充電を停止。給電側が払うFEは従来通り |
| `[machines]` | `dedicatedTankMultiplier` | 1.0。燃料電池・極低温タービン・ミューオン核融合炉・降着ジェット鍛造炉・宇宙チャンバー・暗黒物質の収集と封じ込めの各タンクを0.000001～10億倍 |
| `[machines]` | `forgePhaseTimeMultiplier` | 1.0。予告60・点火80・収束90・仕上げ40・冷却100 tickのそれぞれに乗算。各工程は最低1 tick。照射時間にはレシピの段階別時間倍率が適用される |
| `[drone]` | `shipmentBaseFe` / `shipmentFePerBlock` | 1便あたり固定2,000 FE＋距離1ブロックにつき50 FE。距離分は切り上げ。距離単価を0にすると固定料金で発送 |
| `[drone]` | `crateTier1Capacity` / `crateTier2Capacity` / `crateTier3Capacity` | 箱・液体カプセルの電池は10,000 / 40,000 / 160,000 FE。送料が電池容量以内なら発送可能 |
| `[drone]` | `cargoStacksPerFlight` / `fluidCapsuleCapacity` | アイテムは発送欄9枠のうち1～9枠（既定9）、液体は既定64,000 mB。受取タンクはカプセル2個分、整数上限まで |

機械は毎 tick、各出力タンクから接続先が受け取れる量を自動排出します。配管を通す場合は、経路の輸送容量が適用されます。

ポンプの蓄電容量は 0 FE で、Singulo の電線と、複数の面から送電する発電機では、他の装置を優先してからポンプに余剰電力を渡します。同じポンプが同じ tick に送った量は、給電元や搬出面をまたいで合算します。既定では 1,000 mB を1 tickで送ると 105 FE、1 mB ずつ別々の tick に送ると毎回 100 FE です。水源・溶岩源を汲むときは、一度に1,000 mBを受け取れる搬出先と、バケツ1杯分の給電が必要です。旧版のポンプに残っていたFEは、次の送液から順に使い切ります。

たとえばレシピJSONで `"stage": 2` を指定した全レシピを時間半分・電力毎tick 1.5倍にするには、次の2行を編集します。既定の焼成炉レシピ `singulo:kiln/bismuth_crystal_block` は100 tick・40 FE/tです。ほかの倍率が1なら50 tick・60 FE/t、1回の合計は3,000 FEになります。データパックが別の名前空間で追加した `singulo:machine` レシピにも適用されます。加工時間は最も近い整数、電力は切り上げで、段階別倍率の適用後に共通電力倍率と機械・チップの補正がかかります。

```toml
[stages]
stage2TimeMultiplier = 0.5
stage2EnergyMultiplier = 1.5
```

タンクの実容量と1工程の時間は1～2,147,483,647です。容量を下げても、タンクの中身と発送済みのカプセルの残量は保持されます。新規搬入は各タンクに空きができた時点で再開します。進行中の鍛造は開始時の時間設定を記録しており、旧版で開始済みの加工は元の工程時間で完了します。

### 極端な数量のレシピを作るとき

たとえば酸素製鋼で**鋼インゴット100万個**を出すには、レシピパッチの `"result": { "count": 1000000 }` と合わせて `machineSlotStacks = 15625` 以上にします。1回の成果物が出力枠に収まると加工を開始できます。消費量も同様に `ingredients` の `count`、`fluid_ingredients` の `amount` で調整します。Singuloの機械レシピの品数・液量は整数上限まで扱えます。作業台や他Modの装置にはそれぞれの入出力枠とレシピ形式が適用されます。降着ジェット鍛造炉や潮汐紡錘などの専用設備にも固有の枠・タンクがあります。

一括加工では最初の1回を通常どおり進め、追加分には1回ごとに全工程分の電力と材料が必要です。単純なアイテム加工は材料と完成品の数量をまとめて更新します。触媒の失活、液体の振り分け、抽選など個別処理がある工程は、1台あたり1 tickに最大256回ずつ続行します。途中で材料や電力が尽きるか、完成品を置けなくなればそこでバッチが終わります。

熱・燃料・触媒を使う発電機では1 FE未満の発電端数を次のtickへ繰り越し、ワールドにも保存します。たとえば既定の水素燃料電池で9.6 FEの反応を2回行うと、19 FEが蓄電されます。

通常のFE機器の蓄電・受電・毎tickの消費は整数上限までです。超える値は上限で止まります。高電力レシピを動かすときは、要求FE/tと同時に蓄電容量・受電速度も確認してください。Pリアクターのペレット当たりのエネルギーと送電上限は `penroseEnergyPerPellet`、`penroseMaxOutput` で個別に設定できます。送電上限は炉心と全抽出ポートで共有し、自動送電と外部からの取り出しを同じ tick に合算します。衝撃発電の落下1ブロック当たりの電力は `impactEnergyPerBlock`、水素燃料電池の最大出力は `fuelCellOutput` です。ほかに `fusionReactorOutput`、`quantumEngineOutput`、`degenerateFurnaceOutput`、`cryoTurbineOutput` などがあります。

容量を下げても既存在庫は保持されます。容量を超えた枠は取り出してから補充してください。組立盤に65個以上のマスがある間は拡張を取り外せません。大量の材料を入れた機械を壊すと、ばらまく代わりに中身を保持した機械1個がドロップします。画面の `2.15G` は約21.5億個を示し、スロットにカーソルを置くと正確な個数が読めます。

### ボスごとの調整

各ボスの基礎体力は `sentinelHealth`、`remnantHealth`、`overseerHealth`、`wardenHealth`、`leviathanHealth`、`archivistHealth` で1～整数上限に設定できます。最初の3体はハードモードの基準値で、通常モードではボスごとの比率で調整されます。共通倍率・覚醒・参加人数を合成した実効体力の範囲は1～10¹⁸です。最大体力は召喚時に決まるので、新しい設定で試すときは新たに出現させてください。大きな体力でもボスバーは割合を表示し、バーの精度より小さなダメージは蓄積します。
