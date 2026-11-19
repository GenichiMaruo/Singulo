# 開発ガイド

Minecraft 1.21.1 / NeoForge / Java 21。作業対象のブランチと`gradle.properties`を最初に確認する。1.20.1のForge / NeoForge実装は別ブランチ`1.20.1`で扱う。

## 最初に読むもの

1. [0.17.0の仕様・検証](../0.17.0/README.md)、[版別資料の目次](../README.md)、[課題](../../issue.md)。
2. [プレイヤー説明](../player/ja.md)またはゲーム内ハンドブック。
3. [テストの実行と追加](testing.md)。過去レビューの件数を現在の検査条件に使わない。

## 正となる生成元

| 内容 | 編集する場所 |
|---|---|
| レシピ・配置・材料 | `tools/recipes.py`。精密組立台の基本配置は`tools/assembly_shapes.py`、エディターで保存した配置と材料は`tools/assembly_edits/`、登録名・別名・タグは`tools/ids.py` |
| 設定の既定値 | `tools/config_spec.py` |
| ハンドブック・説明文 | `tools/guide.py`、`tools/descriptions.py`。ページの図（マルチブロックの段・レシピの図・進む道）は `tools/guide_visuals.py`。宇宙設備・液体輸送・コラプサー材の章と説明は `tools/cosmic_content.py`、`tools/logistics_content.py`、`tools/collapsar_content.py` |
| 翻訳 | 英日原稿と`tools/locales/`。`source`も一致させる |
| 実際の加工補正 | `src/main/java/.../recipe/RecipeTweaks.java`。ティア・寿命・設定補正を含む |
| 機械の枠と能力 | `machine/`、`multiblock/`、`reactor/`などの実装 |
| 検査 | 新規は`src/gameTest/java/`。旧`*GameTests.java`も別ソースセットでコンパイル |

`src/generated/`は生成物。直接修正せず、生成元を変更して以下を実行する。

```sh
uv run --no-project --python 3.12 --with pillow tools/gen_data.py
python3 tools/localization.py --check
python3 tools/check_gameplay_text.py
python3 tools/check_models.py
```

生成前にレシピの段階・循環・設定・枠数を検証する。不要な画像エンコード差分や構造物NBTの再圧縮だけの差分まで混ぜない。アイテムIDや既存NBTの意味を理由なく変えない。

## 精密組立台のレシピを編集する

Windowsでは [assembly_editor.bat](../../tools/assembly_editor.bat) をダブルクリックすると、既定のブラウザーで編集画面が開く。プロジェクトのデータ生成と同じ `uv` を使う。ターミナルからは `uv run --no-project --python 3.12 --with pillow tools/assembly_editor.py` で起動できる。

1. 左の一覧で製品を選ぶ。「＋ 素材を追加」では、既存レシピで未使用のバニラ素材も日本語名やIDで検索できる（例：オウムガイの殻、`minecraft:nautilus_shell`）。`minecraft:` の候補はそのアイテムを指定し、`#` の候補はタグ内の互換素材も受け付ける。素材を選んで盤面をクリックすると、置いたマス数が必要個数になる。
2. 「左右を同時に配置」をオンにすると、反対側にも同じ素材が入る。右クリックか消しゴムで取り除ける。「元に戻す」は直前の配置に戻る。
3. 「保存」で編集元、ゲーム用のレシピJSON、説明書の組立図が更新される。専用枠の触媒とその消費量は元のレシピを引き継ぐ。JARに反映するには `gradlew.bat build` を実行する。編集ツールを終了するには起動したターミナルで Ctrl+C を押す。

マルチブロックの制御装置には、その設備を建てる部品を最低1個置く。例えば縮退圧縮炉なら、フレームの代わりに同じ炉の装甲板を使う配置も保存できる。最後の構成部品を外して保存すると、使える部品の一覧が表示される。

装甲板・コイル・窓などの部品レシピには、その設備のフレームを1回の製作につき最低1個置く。例えばC空洞の観察窓はC空洞フレーム、冷却塔ガラスは冷却塔基部を使う。共用の炉心安定化コイルは炉殻ブロック、搬入出ポートは基礎フレームを使う。編集時に必要な枠を外すと、保存時にその素材名が表示される。

バニラの名前・アイコン・素材タグは `tools/vanilla_1_21_1/` に同梱している。カタログを更新するときは、通常のGameTest実行後に `gradlew.bat -I tools/review/vanilla-catalog.gradle runClient` で開発用ワールドから再出力する。

## 実装上の重要な条件

- 在庫・返却容器・完成品は、全品の収納を確認してから素材を消費する。満杯時の地面への落下、再試行での重複、保存復元での消失を検査する。
- 装置の撤去・チャンク解放・ワールド終了を扱うキャッシュには、失効条件と容量上限を設ける。未ロードのチャンクを探索のために読み込まない。
- ケーブルの転送量は実際の経路と共有部分の容量で決める。別枝の細いケーブルで全ネットワークを制限しない。
- 一時的なNBT読込とワールド配置を区別する。位置・レベルに依存する検査は`loadAdditional`中に行わない。
- 名前・説明・翻訳・JEI・レシピ画像・説明書を同期する。加工用レシピは装置の補正後の値も確認する。
- **機械本体の新規・変更レシピは、意味のある配置の定形にする。** 部分消耗や流体は中間部品に加工して組み込む。入口装置の建設へ自分の出力を要求しない。
- 既存ワールドの設定を新しい既定値で勝手に上書きしない。今回の時間結晶は新規既定値9600 tick、旧設定14400 tickは保持する。

## ビルド・検証・公開

```sh
bash ./gradlew build
bash ./gradlew runGameTestServer
```

WindowsのPowerShellでは`./gradlew.bat`を使う。

変更中の短い再実行、全件照合、GUI・任意連携・配布物の検査は[testing.md](testing.md)を使う。実行した検査と未検証の範囲を区別して記録する。成功しても検証用コードを製品JARへ入れない。

公開が依頼されたときだけ[releasing.md](releasing.md)に従う。配布JARの公開先はCurseForgeで、GitHub Releasesへ添付しない。
