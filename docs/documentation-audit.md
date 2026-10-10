# 説明と実装の照合記録 / Documentation audit

確認日：2026-10-09。対象はこのリポジトリの現行ソース、生成済みデータと既定設定です。README、ゲーム内ハンドブック・アイテム説明、公開ページの原稿、詳細レシピ資料を確認して修正しました。新しい説明書は [日本語](manual-ja.md) / [English](manual-en.md) です。

This audit compares the repository's current implementation and default data with its documentation. The bilingual manuals describe implemented gameplay; the design document is retained as development history, not a specification for players. Server settings and data packs can change the defaults.

## 見つかったずれと対応

| 以前の記述・問題 | 実装の確認結果と対応 | 根拠 |
| --- | --- | --- |
| 英語ハンドブックの縮退圧縮炉が3×3×3 | 現行は5×5×5の閉じたプレス。英語本文を修正 | [Shapes.java](../src/main/java/io/github/genichimaruo/singulo/multiblock/Shapes.java) |
| Pリアクターの斜め45度の12か所が抽出ポート | 必須部品は炉心安定化コイル。抽出ポートと警報器は炉殻位置に置く。日英ハンドブックと新説明書を修正 | [Structures.java](../src/main/java/io/github/genichimaruo/singulo/multiblock/Structures.java)、[Blueprints.java](../src/main/java/io/github/genichimaruo/singulo/multiblock/Blueprints.java) |
| バニラの燃えているかまどを熱電発電機の熱源にできる | 既定の熱源は焚き火、魂の焚き火、マグマブロック、溶岩、稼働中のSingulo焼成炉。日英ハンドブックを修正 | [熱源生成処理](../tools/gen_data.py)、[ThermalData.java](../src/main/java/io/github/genichimaruo/singulo/data/ThermalData.java) |
| 詳細資料の古い構造部品・装置寸法 | 当時のレシピ生成元から詳細資料を再生成（調査用スクリプトはその後削除） | [recipes.py](../tools/recipes.py) |
| 専用ガスボンベで流体・ガスを運べる | 該当する登録と実装はない。対応容器・配管の説明へ変更し、生成元も修正 | [SinguloItems.java](../src/main/java/io/github/genichimaruo/singulo/registry/SinguloItems.java)、[MachineBlockEntity.java](../src/main/java/io/github/genichimaruo/singulo/machine/MachineBlockEntity.java) |
| 上位段階の装置で共通の速度×2・効率+25%、最大4並列 | 現行にその共通処理はない。装置固有倍率・触媒・単極子・時間の場の説明へ変更。複数台による並列化と区別 | [MachineBlockEntity.java](../src/main/java/io/github/genichimaruo/singulo/machine/MachineBlockEntity.java)、[CatalystHelper.java](../src/main/java/io/github/genichimaruo/singulo/item/CatalystHelper.java) |
| 圧縮機で「何でも」質量に変えられる | 質量値を持つ素材が対象。日英ハンドブックの表現を修正 | [MassValues.java](../src/main/java/io/github/genichimaruo/singulo/data/MassValues.java) |
| 全装置で面ごとの設定が使えるように読める | 通常の加工装置とマルチブロックの指定ポートを区別して説明 | [MachineBlockEntity.java](../src/main/java/io/github/genichimaruo/singulo/machine/MachineBlockEntity.java)、[PortLinks.java](../src/main/java/io/github/genichimaruo/singulo/multiblock/PortLinks.java) |
| 公開原稿で全マルチブロックが密閉型 | 加速器とPリアクターはリング型。原稿を修正し、ポート位置の例外も記載 | [Blueprints.java](../src/main/java/io/github/genichimaruo/singulo/multiblock/Blueprints.java) |
| 未焼成セラミックの素材が粘土・砂だけに見える | ネザー水晶・骨粉も含めて日英のアイテム説明を修正。投影機の重複説明も整理 | [recipes.py](../tools/recipes.py)、[descriptions.py](../tools/descriptions.py) |
| Tシリンダーが世界全体の時間を速めるように読める | 対応装置の処理を加速する説明へ変更 | [TimeFields.java](../src/main/java/io/github/genichimaruo/singulo/machine/TimeFields.java) |
| READMEが32×32を標準外装として説明する箇所がある | 標準16×16、組み込みSingulo HDが32×32と統一 | [gen_data.py](../tools/gen_data.py)、[SinguloClient.java](../src/main/java/io/github/genichimaruo/singulo/client/SinguloClient.java) |

## 設計書と現行仕様を区別した項目

[singulo_plan.md](../singulo_plan.md) は構想と変更前の案も含みます。冒頭に現行説明書への案内を追加しました。特に次は、設計書を現行仕様として読むと誤解につながります。

- **時間結晶の育成：** 現行の既定レシピは24,000稼働tick、20 TPSで20分。設計書の72,000tick・60分とは異なる。未ロード中は処理されない。[レシピ](../tools/recipes.py)と[育成槽](../src/main/java/io/github/genichimaruo/singulo/machine/TimeCrystalIncubatorBlockEntity.java)を確認。
- **装着枠：** メトリック・ドライブは持ち物に入れる実装で、Curios依存ではない。[MetricDriveItem.java](../src/main/java/io/github/genichimaruo/singulo/item/MetricDriveItem.java)を確認。
- **他modの熱との連携：** Mekanism専用の熱連携実装は確認できないため、新説明書では対応をうたわない。FE・アイテム・流体の能力連携と、専用の熱API連携を区別。
- **炉心の成長：** 旧README内でペレット質量の10%と25%が混在。現行は `PELLET_MASS = 16`、`GROWTH_FRACTION = 0.25`、ペレット由来の成長には上限がある。[PenroseReactorBlockEntity.java](../src/main/java/io/github/genichimaruo/singulo/reactor/PenroseReactorBlockEntity.java)を確認。初見向け手引きでは運転条件と危険を中心に説明。

## 新しい説明書で確認した範囲

登録・レシピ・構造判定・既定設定を照合し、5段階の進行、序盤の材料と発電、触媒のティア制約、9種類のマルチブロック、4種類の遺構、探査機の対象制限、点火、重力道具の操作、ワームホール物流を記載しました。

自動探査機は発見済み遺構だけが対象で、既定の段階4ステーションは地表観測拠点を自動化します。培養施設と最終実験施設は常に手動です。[RuinDiscovery.java](../src/main/java/io/github/genichimaruo/singulo/ruin/RuinDiscovery.java)と[ProbeStationBlockEntity.java](../src/main/java/io/github/genichimaruo/singulo/machine/ProbeStationBlockEntity.java)を確認しました。

数式が物理学由来でも、すべての挙動が現実の物理を再現するわけではありません。公開原稿も「物理学を題材にしたゲーム」と分かる表現へ変更しました。

## 維持方法と検証の範囲

- ゲーム内説明は [guide.py](../tools/guide.py) / [descriptions.py](../tools/descriptions.py) を修正し、生成済みの日英翻訳へ反映。
- レシピ資料は当時の調査用スクリプトの説明を修正し、レシピから再生成。調査用スクリプトはその後削除。バランス計算の所要時間は推定値で、実プレイ時間の保証ではない。
- ドキュメントのローカルリンク、翻訳と生成元の一致、レシピ・設定の整合性を検証。
- 今回の確認はソースと既定データの照合。全機能の実プレイ確認や、任意の他mod・データパックとの互換性保証は含まない。
