# Singulo 0.11.0 — Minecraft 1.20.1

Forge 47.4.26 / NeoForge 47.1.106、Java 17。両ローダーを共通ソースからビルドする。CIのコンパイラは17.0.21に固定する。旧Minecraftバイトコードの重複した引数名でMixin注釈処理が失敗しないよう、匿名サブクラスの親コンストラクタには明示した引数名を持つ互換クラスを使用する。配布物はJava 17形式。

```sh
bash ./gradlew -Ploader=forge build runGameTestServer
bash ./gradlew -Ploader=neoforge build runGameTestServer
```

0.11.0 の装置移設、物流、使用回数付きアイテム、経路別送電、施設、パネル、GUI、説明文を移植した。保存は1.20.1のNBTとForge能力APIを利用する。移設した装置は個別に保持し、未使用の回数付きアイテムはスタックできる。

レシピ354件の素材・数量・時間・電力を、1.21.1公開ソース `5df4aeda0aac1aa4913223467773d26038f7b506` から独立に正規化したハッシュで照合する。`recipe-parity.json` と `tools/check_recipe_parity.py` を参照。

1.20.1の `RecipeManager.getRecipes()` は呼び出すたびにコレクションを作るため、キャッシュの世代判定には再読み込み時に置き換わる内部マップを使用する。Mixinの登録と本番用refmapは配布JARの検査にも含める。人工太陽光のForge描画経路には1.20.1の `QuadLighter.process` を使う。

必須GameTestは9群、合計515件。追加で各ローダーのMekanism連携2件と実クライアントGUIを検証する。通常のビルドには検査用クライアントハーネスを含めない。

公開は成功したGitHub Actions検証の成果物を昇格する既存手順で行う。タグは `v0.11.0-1.20.1F` / `v0.11.0-1.20.1N`。GitHub ReleasesにJARは添付しない。
