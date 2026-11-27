# テストの実行と追加

## 全体の検証

Minecraft 1.20.1 / Java 17 / Forge・NeoForge。現在は通常のGameTest 840件、Mekanismを入れる専用GameTest 5件、検証ツールの単体テスト100件。GUIは通常22画面と5言語×50画面を同じクライアントで確認する。Embeddium・Distant Horizonsの照明比較と再入場は別のクライアント検査を行う。

```sh
python3 -m unittest discover -s tools -p 'test_*.py'
python3 tools/check_gametests.py --inventory
bash ./gradlew -Ploader=forge build runGameTestServer
bash ./gradlew -Ploader=neoforge build runGameTestServer
python3 tools/check_release_1201.py --loader neoforge --build-dir build/neoforge
```

`check_release_1201.py`は、完了件数、ソースから列挙した全テスト名、登録されたテスト名、各成功結果の一致を確認する。結果の不足・重複・余分な結果・途中終了は失敗。製品JARのJavaバージョン・メタデータ・テストコード混入も検査する。部分実行の結果は公開用として受理しない。

GameTestは毎回ワールドと結果JSONを消してから始める。同じ作業ツリーでGameTestを並行起動しない。別実行の結果を残す場合は、次の起動前にログとJSONを別の場所へコピーする。

## 修正中の短い再検証

```sh
bash ./gradlew -Ploader=forge runGameTestServer -PtestClasses=SupplyPortGameTests,CatalystPersistenceGameTests
python3 tools/check_gametests.py --build-dir build/forge --classes SupplyPortGameTests,CatalystPersistenceGameTests
```

`testClasses`はカンマ区切りのクラス名。存在しない名前は起動前に失敗する。指定したクラスと共通ヘルパーだけをコンパイル・登録する。最後にはフィルターなしの全体実行を行う。

名前空間による従来の選択も使える。任意連携は実際のmodを入れた別実行を保つ。

```sh
bash ./gradlew -Ploader=forge runGameTestServer -PwithMekanism -PtestNamespace=singulo_mekanism
python3 tools/check_gametests.py --namespaces singulo_mekanism
```

通常の実行は200 TPSを指定する。`-PtestTickRate=20`で標準速度の検査もできる。ただしGameTestサーバー自体も通常サーバーと異なる実行ループを持つため、実時間やTPSを実プレイの性能保証として扱わない。

比較用の大量ループは通常実行から分離している。

```sh
bash ./gradlew -Ploader=forge runGameTestServer -PtestNamespace=singulo_scaling -PtestBenchmarks=true
bash ./gradlew -Ploader=forge runGameTestServer -PtestClasses=RecipeIndexGameTests -PtestBenchmarks=true
```

実行時間の大小は合否条件にしない。キャッシュの再利用、読み取り回数、同じ結果、即時の無効化といった安定した条件を通常の回帰検査に残す。

## 実クライアントの検査

```sh
python3 -m pip install -r tools/review/requirements.txt
python3 tools/review/prepare_gui.py
xvfb-run -a -s '-screen 0 1280x800x24' timeout 300s bash ./gradlew --no-configuration-cache -I tools/review/gui.gradle runClient -PmachineGuiReview
python3 tools/review/check_gui_png.py
```

GUIの自動操作は`tools/review/`に置く。機械の固定データによる画面は、画面切替後に実際の描画が2フレーム完了してから撮影する。単にtickが一定回数過ぎるのを待たない。全250枚について、画像の存在・寸法・黒画面でないことを検査し、文字と状態表示、インベントリ、資材口のツールチップは追加の画素検査を行う。

PNGはPillowで読み取る。旧デコーダーとの全RGB値の一致を確認している。撮影数や検査範囲を変更するときは、CI、画素検証、公開用の証跡確認、その単体テストを一緒に更新する。

## JEIの配置・素材転送の検査

JEIの配置・素材転送だけを確認する場合は、`prepare_gui.py`と同様に`build/gametest/world`を`build/jei-review/saves/Review`へ複製し、`build/jei-review/options.txt`に`guiScale:2`を指定して、次を実行する。

```sh
bash ./gradlew -Ploader=forge --no-configuration-cache -I tools/review/jei.gradle runClient
```

圧縮機（通常・質量）・ペルチェ冷却器・精密組立台を5言語で描画し、枠の重なりと範囲を検査する。`build/jei-review/screenshots/`に画像を保存する。登録済みJEIハンドラから素材転送パケットを送り、精密組立台の通常転送でワームホール生成器コアの材料が1回分、Shift転送でタンクアップグレードの材料が8回分、それぞれ正しいマスに入ることを確認する。組立盤拡張アップグレードの装着時には、作業コンソール256回分を一括配置し、各マスの合計数がサーバーと画面で一致することも確認する。極端な設定の焼成炉では21億個の材料・製品を枠内に描画し、実機の精密組立台でも21億個を同期して撮影する（`SINGULO_EXTREME_STOCK_COMPLETE`）。成功ログは`SINGULO_JEI_LAYOUT_COMPLETE`（5言語）と`SINGULO_JEI_TRANSFER_COMPLETE`。さらに、移動したレシピ選択・入出力設定ポップアップ越しにJEIの矢印を操作できないこと、閉じると操作が戻ること、面設定の初期表示で正面を選べて回転後は裏面も選べることを検査する（`SINGULO_POPUP_INPUT_COMPLETE`）。検証後は通常の`build`を実行し、検証コードを製品JARから除外する。

精密組立台のクライアント検査では、無給電で完成見本が出ることと、組立台のレシピ一覧で製品にカーソルを合わせると配置図が出ることも確認する。`screenshots/assembly-selector-ja_jp-*.png`に日本語の配置図を保存し、材料アイコンが表示された位置を全21マスで照合する。残り寿命750のBE凝縮触媒を使うケースでは、給電後の完成品4個と失活触媒1個がそれぞれ専用枠に入った画面を`screenshots/assembly-spent-catalyst.png`に保存する。

## テストの追加・保守方針

- 新しいGameTestは`src/gameTest/java/`内の`*GameTests.java`へ置く。既存の`src/main/java/**/GameTests.java`もGradleの`gameTest`ソースセットに振り分け、製品JARには含めない。
- `@GameTestHolder`と`@PrefixGameTestTemplate(false)`を付け、メソッド名は全クラスで重複させない。新しい名前空間を作るときは、Gradleと検査ツールの既定一覧を更新する。
- 期待する件数を変更するときは、追加・削除した検証内容をレビューする。件数だけ合わせて成功扱いにしない。
- 試行回数を増やして失敗を隠さない。乱数は固定する。世界の時刻、設定、レシピを一時変更したら、失敗時にも復元する。
- ログインするテスト用プレイヤーは`TestScope.player`または`TestScope.ownPlayer`で管理する。`discard()`だけでは参加者一覧から消えない。
- 非同期の失敗・タイムアウトでも後始末する資源は`TestScope`で管理する。完了リスナーの前にバッチランナーが次のテストを準備することがあるため、完了リスナーから共有の強制読み込みチケットを解除しない。バッチが管理するチケットはバッチランナーに任せる。
- エンティティが実際に動く範囲をテンプレート内に収める。範囲外への移動が検証対象なら、読み込み完了を明示的に確認してから動かす。時間切れを伸ばすだけで済ませない。
- 長時間の数値積算は実際のコントローラー処理を直接呼び出す検査にできる。ただし、時間・電源網・移動・チャンク状態そのものを検証するテストは通常のワールドtickで行う。
- 在庫を使う処理は不足・満杯・部分完了・保存復元・素材や完成品の総数を確認する。シミュレーションが在庫を変更しないことも確認する。満杯のときに地面へ捨ててよい前提を置かない。

詳細な結果と所要時間は`build/reports/gametest/results.json`に出力する。チャンク待ちでは標準のテスト時間切れが始まらないことがあるため、120秒間どのテストも完了しない場合は検査サーバーを異常終了させる。これは成功条件の緩和ではなく、無期限の停止を検出するための監視である。

CIはForge・NeoForgeの通常／GUI各1、共通の描画連携1、Mekanism1の計4ジョブを並列に走らせる。描画連携ジョブの9件は、専用ワールドの作成と独立した実行環境の検証も兼ねるため、通常ジョブとの重複を意図して残している。

## 0.14.0のJEI 15検査

Forgeの既存CIジョブで全レシピ配置・実転送・ポップアップ操作・5言語×容量3段階を確認する。手順と互換性は[移植記録](../0.14.0/port-1.20.1.md)を参照。

## Create/Ponderの照明互換性（0.14.1）

`python3 tools/review/prepare_ponder.py --loader forge`の後、`bash gradlew -Ploader=forge --no-configuration-cache -I tools/review/ponder.gradle -PwithEmbeddium -PwithOculus -PwithAlexsCaves runClient`で実際のCreate 6.0.8 / Ponder 1.0.91を起動する。NeoForgeは`--loader neoforge`と`-Ploader=neoforge`を指定する。Create単体は3つの追加modフラグを外す。

同じクライアントの`latest.log`に`AndroidWeaponTerrainMixin applied: terrain draws in LevelRenderer.renderLevel are wrapped`があることも確認する（ワールドの最初の描画で1回出る。Android武器の描画検査でも同じ行を確認する）。このMixinは`require = 0`で、他modが`LevelRenderer.renderLevel`を変えて包めなかった場合も起動を止めない。その場合は`AndroidWeaponTerrainMixin is not active`の警告を1回出し、Fast/Fancyの武器透過だけを無効にして通常のバッファで描く。適用の有無はMixinExtrasが設定プラグインの`postApply`より後に注入するため、実行時に包んだ地形描画が呼ばれたかで判定する。開発クライアントでは開発名のまま一致し、refmapの欠落（0.23.0の起動クラッシュ）を検出できないため、本番名（`m_109599_`・`m_172993_`）の対応は`tools/check_release_1201.py`の配布JAR検査で確認する。

プレスとシュートの各画面について、自然光0・パネル光15の実ワールドを準備してから、通常サイズのライトマップ、全256画素のプレビューとNoonバンクの一致、GPU補間、仮想Levelの分離、入れ子・例外時の状態復元を確認する。`SINGULO_PONDER_LIGHTING_COMPLETE`は2回、`SINGULO_PONDER_CAPTURE_COMPLETE`は各アイテム1回が必要。`check_ponder_png.py`で台座の画素も検査する。CIはForgeの既存ジョブ内でシェーダーパックなしの検査を行い、ジョブ数を増やさない。

Alex’s Cavesの更新置換との競合は、`rayleigh.gradle`の`-PwithEmbeddium -PwithAlexsCaves`でも検査する。このケースではDistant Horizonsを入れない（ACが同modの存在時に独自ライトマップ処理を無効化するため）。夜間・雨天・電源切替の4枚を`check_rayleigh_png.py`で比較する。

Validation client options generated by `tools/review/prepare_*.py` set only
`soundCategory_music:0.0`. Sound effects/master volume and personal game profiles are unchanged.
`tools/test_review_audio.py` verifies the disposable review configurations.
