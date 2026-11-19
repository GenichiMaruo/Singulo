# Modパック向けのレシピ調整

Singulo 0.21.0 のこの機能を含むビルド（Minecraft 1.21.1 / NeoForge）では、既存レシピの変えたい項目だけをデータパックのJSONに書けます。Singuloの機械加工、精密組立台、通常の作業台やかまどなど、Minecraftのレシピとして登録されるものが対象です。

機械の入出力容量、同時加工数、消費電力、発電量、ボスの強さを調整する方法は[Modパックの難易度と機械性能](pack-balance.md)にまとめています。

## 酸素製鋼で試す

1. Minecraftインスタンス（専用サーバーの場合はサーバー側）の `config/singulo/datapacks/` に [サンプルのフォルダー](../examples/recipe-pack/) またはサンプルZIPを置きます。フォルダーを使う場合は `config/singulo/datapacks/recipe-pack/pack.mcmeta` が見えれば正しい配置です。ZIPの場合は、開いてすぐに `pack.mcmeta` と `data/` が見える構造にします。
2. ワールドを開き、管理者権限で `/reload` を実行します。`/datapack list enabled` で有効化を確認できます。新しいパックが無効の場合は `/datapack enable` の入力候補から自分のパックを選んでください。
3. 給電した焼成炉に鉄インゴット2個、炭素材料1個、酸素125 mBを入れます。サンプルでは標準設定の加工時間が80から60 tickに短くなり、鋼インゴットが2個から3個に増えます。

この変更を書いたファイルは [kiln/steel_ingot_from_oxygen.json](../examples/recipe-pack/data/singulo/recipe/kiln/steel_ingot_from_oxygen.json) です。

```json
{
  "singulo:patch": {
    "time": 60,
    "fluid_ingredients": { "0": { "amount": 125 } },
    "result": { "count": 3 }
  }
}
```

`"0"` は最初の液体材料を指します。液体の種類、鉄と炭素の指定、消費電力は元レシピから引き継ぎます。サンプル中の各JSONは個別に使えるので、採用するものだけを残せます。

## 編集したいレシピを探す

管理者権限で `/singulo recipe show singulo:kiln/steel_ingot_from_oxygen` を実行すると、データパック内のファイルの置き場所と、パックの変更を重ねたJSONが表示されます。ゲーム内では下線付きのパスをクリックするとJSONをコピーできます。`show` の後でTabキーを押すと、読み込まれているレシピIDから選べます。

レシピIDが `singulo:kiln/steel_ingot_from_oxygen` なら、パックの中に作るファイルは `data/singulo/recipe/kiln/steel_ingot_from_oxygen.json` です。出力アイテムが同じでも、加工方法が違うとレシピIDが異なることがあります。

| やりたいこと | 書き方 |
|---|---|
| 加工時間・消費電力を変える | 機械レシピの `time`（tick）、`energy`（FE/t）を指定 |
| 材料の数・加工後の個数を変える | 機械レシピの先頭の材料を3個にするなら `"ingredients": { "0": { "count": 3 } }`。製品は `"result": { "count": 3 }` |
| 材料のタグを特定のアイテムに変える | 機械の材料要素に `"ingredient": { "tag": null, "item": "minecraft:diamond" }` を指定。`null` で元のタグを外す |
| 作業台の材料を差し替える | `key` の記号に別の材料を指定。[組立盤拡張アップグレードの中央をダイヤモンドにする例](../examples/recipe-pack/data/singulo/recipe/crafting/assembly_stock_upgrade.json) |
| 精密組立台の配置を変える | `shape` の行を上から `"0"`〜`"4"` で指定。[C空洞フレームの上下の材料を交換する例](../examples/recipe-pack/data/singulo/recipe/precision_assembler/casimir_cavity_frame.json) |
| 既存レシピを無効にする | `"singulo:patch": { "neoforge:conditions": [{ "type": "neoforge:false" }] }` と書く |
| 新しいレシピを追加する | `data/<自分の名前空間>/recipe/<名前>.json` に `type` を含む完全なレシピを書く。[機械レシピの実例](../../src/generated/resources/data/singulo/recipe/kiln/zinc_ingot.json) |

精密組立台の `shape` は5文字×5行です。四隅は空白で、`A` は `ingredients` の1番目、`B` は2番目の材料に対応します。通常素材は `count` と同じ数だけ文字を置きます。`uses` が正の触媒は専用スロットで使うため、盤面ではその文字を空けてください。

## 複数のパックを配布する場合

- Modパックに同梱する場合は `config/singulo/datapacks/<パック名>/` または `config/singulo/datapacks/<パック名>.zip` を配布ファイルに含めてください。そのインスタンスの新規・既存ワールドで既定有効になります。パックIDは `singulo/<フォルダー名またはZIP名>` です。ワールド単位の無効化と優先順位変更には通常の `/datapack` コマンドが使えます。
- 1つのワールドだけを変えたい場合は、同じパックを `<ワールド>/datapacks/` に置けます。この場合のパックIDは `file/<フォルダー名またはZIP名>` です。
- 変更ファイルの最上位には `singulo:patch` だけを書きます。省略した項目は優先順位が一段下のレシピから引き継ぎます。複数のパックが同じ値を変更するときは、優先順位が高いパックの値が使われます。
- 配列の一部を変えるときは `{ "0": … }` のように既存の位置を指定します。材料を追加・削除したり順序を変えたりするときは、変更後の配列全体を `[ … ]` で書きます。省略可能な項目ごと外すときは `"fluid_ingredients": null` のように指定します。
- 完全なJSONで既存レシピを置き換えることもできます。そのファイルより下にあるレシピや変更はそこで上書きされます。対象レシピがない、または配列の位置が範囲外の場合は、そのレシピが登録されません。サーバーの `logs/latest.log` に対象のレシピIDと原因が出ます。
- `/singulo recipe show` はデータパック適用後、サーバーのバランス設定を適用する前の値を表示します。個別の設定を既定値から変更している場合や難易度プリセットを使う場合は、該当する値にその調整も加わります。アンドロイドに材料や出来高を手動記録したレシピは、変更後の数量で登録し直してください。

サンプルZIPはリポジトリで `./gradlew recipePackExample` を実行しても作れます。生成先は `build/distributions/Singulo-recipe-pack-example.zip` です。
