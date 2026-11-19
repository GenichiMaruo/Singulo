# Singulo アドオン開発ガイド

Minecraft 1.21.1、NeoForge、Java 21 向け。以下のAPIは Singulo 0.21.0 の本変更を含むビルドから使える。

既存レシピの材料や加工条件はデータパックで調整できます。[変更方法とサンプル](recipe-packs.md)

| 作りたいもの | 使用するAPI |
|---|---|
| 指令台に接続する端末、素材不足を表示する画面 | `AndroidWorkshops` で接続先・在庫・製作見積もりを読む |
| 材料を供給し製品を受け取る端末 | `AndroidWorkEndpoint` と `AndroidOrders`。[流体・化学物質・注文の手順](../0.21.0/android-workshop-api.md) |
| 工場モニターに表示する機械 | `MachineReports.CAPABILITY` で `MachineReport.Source` を公開する |
| 既存の装置で使える触媒 | `CatalystItem` と `CatalystItem.Spent` を通常のアイテムとして登録する |
| 既存機械で自分の素材を加工するレシピ | `data/<自分のmod ID>/recipe/` に `singulo:machine` のJSONを追加する。[焼成炉の例](../../src/generated/resources/data/singulo/recipe/kiln/zinc_ingot.json)・[液体を扱う電解機の例](../../src/generated/resources/data/singulo/recipe/electrolyzer/hydrogen.json) |

## 開発プロジェクトに追加する

NeoForge ModDevGradle プロジェクトの `libs/` に対応するSingulo JARを置き、Groovyの `build.gradle` に次を加える。アドオンを配布するときは、プレイヤーがSingulo本体とアドオンの両方をmodsフォルダーに入れる。

```groovy
dependencies {
    implementation files('libs/Singulo-0.21.0-1.21.1N.jar')
}
```

`META-INF/neoforge.mods.toml` に依存関係を宣言する。以下の `example_addon` は自分のmod IDに置き換える。開発版を使う間は、動作確認した本体のコミットまたはJARのハッシュも記録しておくと、同じ版番号のビルドを区別できる。

```toml
[[dependencies.example_addon]]
modId="singulo"
type="required"
versionRange="[0.21.0,0.22.0)"
ordering="AFTER"
side="BOTH"
```

## 指令台・機械・触媒を使うコード例

```java
package example.addon;

import io.github.genichimaruo.singulo.api.android.AndroidWorkshops;
import io.github.genichimaruo.singulo.api.item.CatalystItem;
import io.github.genichimaruo.singulo.api.machine.MachineReports;
import io.github.genichimaruo.singulo.machine.MachineReport;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class AddonHooks {
    // modのコンストラクターで ITEMS.register(modBus) を呼ぶ。
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("example_addon");
    public static final DeferredItem<CatalystItem.Spent> SPENT = ITEMS.register("tarnished_crystal",
            () -> new CatalystItem.Spent(new Item.Properties()));
    public static final DeferredItem<CatalystItem> CRYSTAL = ITEMS.register("resonant_crystal",
            () -> new CatalystItem(new Item.Properties(), 3, 24000, SPENT));

    // RegisterCapabilitiesEvent のリスナーから自分の機械の BlockEntityType を渡す。
    public static <T extends BlockEntity & MachineReport.Source> void registerMachine(
            RegisterCapabilitiesEvent event, BlockEntityType<T> machine) {
        event.registerBlockEntity(MachineReports.CAPABILITY, machine, (be, context) -> be);
    }

    public static Optional<AndroidWorkshops.Preview> estimate(ServerPlayer player, ItemStack card,
                                                               ItemStack target, long amount) {
        var level = player.serverLevel();
        return AndroidWorkshops.readLocationCard(card)
                .filter(location -> location.dimension().equals(level.dimension()))
                .flatMap(location -> AndroidWorkshops.resolve(level, location.pos(), player.getUUID()))
                .map(link -> AndroidWorkshops.preview(level, link.console(), player.getUUID(), target, amount, List.of()));
    }
}
```

### 作業場の照会

`resolve` は読み込み済みで所有者が一致する指令台・資材口・納品口・レシピ棚を判別する。供給専用端末なら返された `role()` が `CONSOLE` または `SUPPLY` のときだけ接続する。位置カードは別の次元も記録できるため、上の例はプレイヤーがいる次元と照合している。

`stock(level, console, owner)` は共有倉庫の予約分を引いた利用可能数を返す。`preview` は製品を新しく作るための工程、共有倉庫から使う量、選ばれたアンドロイドのタンクから使う量、不足量を返す。個数とmBは `Quantity.amount()` で読む。空の作業者リストは自動選択を表す。`MATERIALS_READY` は材料がそろうという判定で、見積もりの時点では注文も予約も作成されない。実行時の作業者・装置・納品先の条件は `AndroidOrders.submit` の結果で確認する。

ワールドを読むAPIはサーバースレッドから呼び、操作したプレイヤーなど認証済みのUUIDを渡す。在庫照会・見積もりは棚と資材口を調べるので、画面を開いたときや依頼数を変更したときに取得するとよい。

### 機械の報告

自分の機械で `MachineReport.Source.report()` を実装する。例えば `MachineReport.simple(MachineReport.State.NO_POWER, Component.translatable("example_addon.status.no_power"), storedFE, capacityFE)` を返すと、工場モニターに停止理由が出る。進捗・消費FE/t・出力FE/t・不足部品も渡したい場合は `MachineReport` のコンストラクターを使う。

登録後は、他のアドオンから `MachineReports.read(serverLevel, machinePos)` でその位置の報告を取得できる。Jade・WTHITでも同じ内容を使う。プロバイダーの有無はクライアントからも照会されるため、登録用ラムダはどちらの側でも取得できるようにする。

加工機では `remainingTicks()` も実装すると、アンドロイドが回収に戻る目安を伝えられる。単位はゲーム内tick。待機中は0、不明なら既定の−1を返す。材料と製品の受け渡しには通常のNeoForgeアイテム／流体ハンドラーと加工カードを使う。

### 触媒の寿命と排出

`CatalystItem` の第2引数はTier（2〜5）、第3引数は設定倍率を掛ける前の寿命（tick）。上の例は標準設定で20分。Tierで触媒を選ぶ既存の装置・加工レシピで使え、新品は16個まで重なり、使いかけは1個ずつになる。消耗し切ると指定した `Spent` が1個出て、精密組立台の失活触媒スロットやアンドロイドによる回収の対象になる。特定のアイテムを名指しする燃料や通常のクラフト材料は、そのレシピの材料指定に従う。
