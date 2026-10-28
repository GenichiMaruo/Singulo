# テストの実行と追加

## 全体の検証

Minecraft 1.21.1 / Java 21 / NeoForge。現在は通常のGameTest 565件、Mekanismを入れる専用GameTest 1件、検証ツールの単体テスト30件。GUIは通常22画面と5言語×41画面を同じクライアントで確認する。Sodium・Distant Horizonsの照明比較と再入場は別のクライアント検査を行う。

```sh
python3 -m unittest discover -s tools -p 'test_*.py'
python3 tools/check_gametests.py --inventory
bash ./gradlew build runGameTestServer
python3 tools/check_release_1211.py --loader neoforge --build-dir build
```

`check_release_1211.py`は、完了件数、ソースから列挙した全テスト名、登録されたテスト名、各成功結果の一致を確認する。結果の不足・重複・余分な結果・途中終了は失敗。製品JARのJavaバージョン・メタデータ・テストコード混入も検査する。部分実行の結果は公開用として受理しない。

GameTestは毎回ワールドと結果JSONを消してから始める。同じ作業ツリーでGameTestを並行起動しない。別実行の結果を残す場合は、次の起動前にログとJSONを別の場所へコピーする。

## 修正中の短い再検証

```sh
bash ./gradlew runGameTestServer -PtestClasses=SupplyPortGameTests,CatalystPersistenceGameTests
python3 tools/check_gametests.py --classes SupplyPortGameTests,CatalystPersistenceGameTests
```

`testClasses`はカンマ区切りのクラス名。存在しない名前は起動前に失敗する。指定したクラスと共通ヘルパーだけをコンパイル・登録する。最後にはフィルターなしの全体実行を行う。

名前空間による従来の選択も使える。任意連携は実際のmodを入れた別実行を保つ。

```sh
bash ./gradlew runGameTestServer -PwithMekanism -PtestNamespace=singulo_mekanism
python3 tools/check_gametests.py --namespaces singulo_mekanism
```

通常の実行は200 TPSを指定する。`-PtestTickRate=20`で標準速度の検査もできる。ただしGameTestサーバー自体も通常サーバーと異なる実行ループを持つため、実時間やTPSを実プレイの性能保証として扱わない。

比較用の大量ループは通常実行から分離している。

```sh
bash ./gradlew runGameTestServer -PtestNamespace=singulo_scaling -PtestBenchmarks=true
bash ./gradlew runGameTestServer -PtestClasses=RecipeIndexGameTests -PtestBenchmarks=true
```

実行時間の大小は合否条件にしない。キャッシュの再利用、読み取り回数、同じ結果、即時の無効化といった安定した条件を通常の回帰検査に残す。

## 実クライアントの検査

```sh
python3 -m pip install -r tools/review/requirements.txt
python3 tools/review/prepare_gui.py
xvfb-run -a -s '-screen 0 1280x800x24' timeout 300s bash ./gradlew --no-configuration-cache -I tools/review/gui.gradle runClient -PmachineGuiReview
python3 tools/review/check_gui_png.py
```

GUIの自動操作は`tools/review/`に置く。機械の固定データによる画面は、画面切替後に実際の描画が2フレーム完了してから撮影する。単にtickが一定回数過ぎるのを待たない。全205枚について、画像の存在・寸法・黒画面でないことを検査し、文字と状態表示、インベントリ、資材口のツールチップは追加の画素検査を行う。

PNGはPillowで読み取る。旧デコーダーとの全RGB値の一致を確認している。撮影数や検査範囲を変更するときは、CI、画素検証、公開用の証跡確認、その単体テストを一緒に更新する。

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

CIは通常・GUI、描画連携、Mekanismの3ジョブを並列に走らせる。描画連携ジョブの9件は、専用ワールドの作成と独立した実行環境の検証も兼ねるため、通常ジョブとの重複を意図して残している。
