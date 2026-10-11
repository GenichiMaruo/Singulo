# Singulo 0.3.0 — 要修正・要検証事項一覧

作成日：2026-10-11  
対象：Minecraft 1.21.1 / NeoForge、main `b45f478819fa205091358a0f715edd9761d45862`、既定設定  
目標難易度：探索と工場づくりを両立する中程度  
関連資料：[バランス評価報告書](README.md)、[計算結果](calculation-results.json)

前回の調査で見つかった問題・調整候補・検証不足を集約したローカルの課題一覧。GitHubへのIssue投稿やMod本体の修正は行っていない。チェック欄はすべて未対応。

**証拠の区分：** 「静的確認」はソース／生成レシピで確認した内容、「計算評価」は仮定を明示した計算からの評価、「要実機検証」は未確認の懸念または検証タスク。「調整候補」は不具合と断定せず、現行仕様を維持する判断も可とする。Minecraftの通しプレイ・GameTestは未実施。

**優先度：** P0＝通常進行を止める問題、P1＝正式版前に対応または仕様判断が必要、P2＝体験改善・説明整備・追加検証、P3＝次版以降の改善候補。優先度は今回の評価上の提案。

## 一覧

| 完了 | ID | 優先度 | 区分 | 課題 |
|---|---|---|---|---|
| [ ] | SNG-001 | P0 | 静的確認 | 複数の時間結晶触媒を要求する4レシピが通常投入では成立しない |
| [ ] | SNG-002 | P1 | 静的確認 | 難易度プリセットと一部のレシピ関連設定が実行処理へ未接続 |
| [ ] | SNG-003 | P1 | 静的確認・要実機検証 | プレイヤー別アンカー上限設定の適用を確認できない |
| [ ] | SNG-004 | P1 | 静的確認・計算評価 | 点火後の自動給餌でH凝縮体回収条件から外れる |
| [ ] | SNG-005 | P1 | 静的確認・改善候補 | 満充電でも発電機が燃料・冷媒・触媒を消耗する |
| [ ] | SNG-006 | P1 | 計算評価・調整候補 | 殻の大量生産に必要な岩石・金属の採集負担が大きい |
| [ ] | SNG-007 | P1 | 計算評価・調整候補 | T4発電量と下位触媒運転が加工需要に対して強すぎる可能性 |
| [ ] | SNG-008 | P2 | 計算評価・調整候補 | 量子熱機関のミュオン起動で電力段階が大幅に短縮される |
| [ ] | SNG-009 | P1 | 静的確認・仕様判断 | 低純度・短寿命の時間結晶を設備素材として使える |
| [ ] | SNG-010 | P2 | 静的確認 | 触媒の一般説明と汎用加工機の実装が一致しない |
| [ ] | SNG-011 | P2 | 静的確認・説明修正 | 時間結晶・冷却塔・送電・複製等の説明値や前提がずれている |
| [ ] | SNG-012 | P2 | 計算評価・調整候補 | 初発電までの水晶調達とT2の青氷調達が重い |
| [ ] | SNG-013 | P2 | 静的確認・改善候補 | 熱電の低温源劣化と安定運転条件が分かりにくい |
| [ ] | SNG-014 | P1 | 計算評価・改善候補 | 点火の蓄電量と送電帯域の違い、旧ケーブル混在が分かりにくい |
| [ ] | SNG-015 | P2 | 計算評価・調整候補 | 時間結晶の設備素材需要と運転用需要が競合する |
| [ ] | SNG-016 | P2 | 計算評価・改善候補 | 冷却塔の共用処理と圧縮・収集ラインの実効能力が見えにくい |
| [ ] | SNG-017 | P2 | 調整候補・要実機検証 | 中後半に探索ゲートが集中し、所要時間の振れを評価できていない |
| [ ] | SNG-018 | P2 | 要実機検証 | ボス戦・撤退リセット・人数別の負担が未評価 |
| [ ] | SNG-019 | P3 | 改善候補 | 大出力発電の用途と初点火後の目標を補強したい |
| [ ] | SNG-020 | P1 | 要実機検証 | 通常投入・通し進行・設定変更・自動化・保存復帰の検証が不足 |

## SNG-001 — 複数触媒レシピの通常製作が不可能

**P0／静的確認。関連：SNG-009、010、015、020。**

`UsesItem` は最大スタック数1。精密組立台では触媒を通常入力枠に入れられず、専用枠は1つだけ。以下の材料数を満たせない。

| 対象レシピ | 必要な時間結晶触媒 | 止まる進行 |
|---|---:|---|
| `core_controller` | 4 | Pリアクター建造 |
| `singularity_encapsulator` | 2 | 特異点の種・初コアの製作 |
| `shield_tower_core` | 2 | シールド建造 |
| `wormhole_generator_core` | 2 | ワームホール生成器建造 |

**再現手順：** 精密組立台へ他の材料を入れる。時間結晶触媒を通常入力へ入れると拒否される。専用枠に1個入れ、2個目を入れようとしても入らず、レシピが成立しない。これはソースから導いた再現手順で、実機確認は未実施。

**修正候補：** 触媒1個からスタック可能な設備用「時間結晶モジュール」を作り、上記レシピの2個／4個要求を置換する。通常入力にも触媒を許す場合は、非スタック材料の必要枠数まで確認する。特異点封入台は他材料5種類＋触媒2個なので、通常入力6枠だけでは足りない。

**修正箇所・根拠：** [UsesItem][uses-item]、[MachineType][machine-type]、[MachineBlockEntity][machine] の `slotAccepts`、[MachineMenu][menu] の `quickMoveStack`、[レシピ生成元][recipes]、[精密組立台の生成レシピ][assembler-recipes]。

**完了条件：** 4レシピすべてをGUI通常操作・Shift移動・通常の自動搬入で製作できる。非合法な複数スタックを `setStackInSlot` で直接置く方法に頼らない。JEIと材料消費数も一致する。

## SNG-002 — プリセットとレシピ関連設定が実行処理へ未接続

**P1／静的確認。**

設定は定義されているが、以下の定数を `src/main/java` から参照する処理を確認できなかった。レシピは生成JSONの値を使うため、設定変更だけで説明どおりに変わる経路が見当たらない。

| 設定キー | 本来変更する内容 |
|---|---|
| `preset` | casual / standard / expert / customの一括設定 |
| `starsPerExoticBatch` | エキゾチック物質のスター数 |
| `exoticBatchSize` | エキゾチック物質の出力数 |
| `starsPerSingularityCore` | コアのスター数 |
| `allowArtificialStarCore` | 人工星核を代替に使えるか |
| `artificialStarCoreJetCondensate` | 人工星核のジェット凝縮体数 |
| `spentRecycleRatio` | 失活触媒の回収率 |
| `timeCrystalGrowthTicks` | 時間結晶の成長時間 |
| `shellRockLv3` | 殻の岩石Lv3数 |
| `shellMetalCores` | 殻の金属Lv2数 |
| `shellCoolantMb` | 殻の冷却材量 |
| `reactorPlatesPerShell` | 殻から作る炉殻の枚数 |
| `sculkPerShard` | 残響の欠片複製のスカルク数 |

**影響：** 「casualならスター不要」「育成時間を短くできる」などの案内を信用すると、想定した難易度にならない。今回のバランス調整も、該当設定だけ変更しても評価できない。

**修正候補：** 実行時にレシピへ設定を適用するか、設定を削除／未対応と明示してデータパックへ統一する。プリセットと個別設定、データパックの優先順位も決める。

**修正箇所・根拠：** [config_spec.py][config-spec]、[ServerConfig][config]、[MachineRecipe][recipe-code]、[加工処理][machine]。生成物だけでなく生成元を修正する。

**完了条件：** 上記キーを既定値以外にして、消費数・出力数・加工時間・代替素材の可否が説明どおりになる。casual / standard / expert / customを個別に確認し、JEI表示とも一致する。発電倍率など、既に接続されている設定まで無効と扱わない。

## SNG-003 — プレイヤー別アンカー上限設定の適用を確認できない

**P1／静的確認・要実機検証。**

参照走査では `MAX_ANCHORS_PER_PLAYER` も実行コードから参照されていない。設定 `maxAnchorsPerPlayer` の既定値8が、実際の設置・稼働台数を制限しているとは確認できない。半径上限 `MAX_ANCHOR_CHUNK_RADIUS` とは別項目。

**影響：** 管理者が台数上限によってチャンク保持の負荷を制限できると思っても、適用されない可能性がある。現時点では実サーバーの台数制限動作を断定しない。

**修正候補：** 所有者別に有効アンカーを数え、上限適用・解除・破壊・ログアウト・再起動時の扱いを定義する。未実装なら設定の説明を限定する。

**修正箇所・根拠：** [設定][config]、[WorldlineAnchorBlockEntity][anchor]、[AdvancedWorldlineAnchorBlockEntity][advanced-anchor]。

**完了条件：** 上限1・8・0の意味を明文化し、複数プレイヤー、再設置、再起動でもその意味どおりに動く。まず上限1で2台目がどうなるかを実機確認する。

## SNG-004 — 自動給餌によって初コア用のH凝縮体を回収できなくなる

**P1／静的確認・計算評価。**

H凝縮体は質量150〜500で5分に1個。初期質量500に対し、ペレット1個で質量が4増える。自動給餌には満充電でも最低投入があるため、自然な操作である「燃料を入れて運転」が回収条件から外れる原因になる。

独立した30分モデルでは、燃料なしで質量約452・H凝縮体6個、自動給餌・負荷なしで質量約761・0個。初コアには直接4個＋アノマリー復元2個＝6個必要。質量5,000から500まで自然蒸発を待つ場合は約80.3日の連続稼働に相当し、通常の復旧手段には重すぎる。

**修正候補：** 初回は質量450〜480程度を維持する「触媒回収運転」を既定にし、6個回収後に発電運転へ誘導する。育てすぎた炉心の安全な質量低減、または回収条件の緩和も検討する。

**修正箇所・根拠：** [PenroseReactorBlockEntity][reactor] の `run`・`evaporate`、[レシピ][recipes]、[シミュレーションCSV](penrose-simulation.csv)。

**完了条件：** 初見向け手順で初コアまで到達できる。自動給餌の前後で回収条件を把握でき、誤って質量を増やしても現実的な時間で復旧できる。無給餌30分は暫定手順として案内可能だが、運転UIの代わりにはしない。

## SNG-005 — 満充電時も消耗して余剰電力を捨てる

**P1／静的確認・改善候補。**

極低温タービンは発電条件が満たされれば窒素を消費し、量子熱機関・縮退熱炉は稼働条件が成立すれば触媒を消耗する。縮退熱炉は燃料も消費する。満充電を理由とする待機条件がなく、プレイヤーの手動停止が有利になる。

**修正候補：** 蓄電率による自動待機と再開を追加する。再開閾値に余裕を設けて毎tickのON/OFFを避ける。発電機ごとに停止中の燃焼時間・触媒寿命・冷媒の扱いを統一する。Pリアクターは質量維持が別問題なのでSNG-004で扱う。

**修正箇所・根拠：** [CryogenicTurbineBlockEntity][turbine]、[QuantumHeatEngineBlockEntity][quantum]、[DegenerateFurnaceBlockEntity][furnace]、[CatalystDeviceBlockEntity][catalyst-device]。

**完了条件：** 出力先を満充電／切断した状態で、意図しない消耗が進まない。需要再開で復帰し、冷媒・燃料・触媒が複製されない。仕様として空運転を残す場合は、消耗をGUIで明示し自動停止手段を用意する。

## SNG-006 — 殻の資材要求が中難度として採集に偏る

**P1／計算評価・調整候補。**

殻1個は岩石4,860質量＋金属162質量。鉄／銅ブロック換算ならインゴット486個。点火前の炉殻133枚は12枚出力で12バッチ、種用の殻を足して13個となり、殻だけで岩石63,180個相当＋金属6,318インゴット相当になる。

岩石300個/分を仮定しても3.51時間、金属・移動・その他資材は別。殻用の圧縮加工は75.62分・台。混成ボーナスの岩石節約は約11.1%にとどまる。

**調整候補：** まず炉殻出力12→24枚を試す。点火前の殻は7個になり、岩石34,020質量・金属3,402インゴット相当へ減る。それでも金属が重ければ殻の金属Lv2を2→1とし、1,701インゴット相当にする。代案は採掘自動化だが、新装置の追加は別の開発負担がある。

**修正箇所・根拠：** [レシピ][recipes]、[質量値][mass]、[計算付表](calculation-tables.md)。設定だけ変更する場合はSNG-002が先。

**完了条件：** Singulo単体と採掘Mod併用を分けて測定し、採集・建造・探索・純粋な待機の比率で採否を決める。表示された鉄・銅の部品材料費に圧縮用金属が含まれるか、集計条件も明記する。

## SNG-007 — T4の下位触媒発電が時間技術の報酬を先取りする

**P1／計算評価・調整候補。**

縮退熱炉は時間結晶で20 MFE/t、BE凝縮触媒でも10 MFE/t。時間結晶を初めて作る前から大出力を得られる。C空洞・圧縮・育成・レーザー等の代表的な同時負荷194 kFE/tに対し、使用率はそれぞれ約0.97%／1.94%。以降は電力設計より資材待ちが中心になる。

**調整候補：** 定格5 MFE/tを試験値とし、下位触媒の出力を発電機固有に抑える。仮に定格5%ならBE運転250 kFE/tで、次の技術への橋渡しになる。全デバイス共通の `lowerTierSpeed` 変更は避ける。5 MFE/tなら50 GFE充電は8分20秒であり、点火エネルギーまで同時に増やさない。

**修正箇所・根拠：** [縮退熱炉][furnace]、[CatalystHelper][catalyst-helper]、[設定生成元][config-spec]。

**完了条件：** 現行版と試験版でT3→T4→点火を比較し、供給余裕と待ち時間を記録する。SNG-005・006の改善と合わせて評価し、採集が重いまま電力だけ厳しくしない。

## SNG-008 — 量子熱機関のミュオン起動が直前の電源より大幅に強い

**P2／計算評価・調整候補。**

量子熱機関はミュオン触媒で100 kFE/t、10分/個。極低温タービン1基5 kFE/tから20倍となる。量子データが必要なので研究棟探索は飛ばせないが、BE触媒系統を完成させる前に電力不足を大幅に解消できる。

**調整候補：** 起動救済として残すかを仕様判断する。抑える場合は定格50〜100 kFE/t、下位触媒の発電量は定格10%程度から検証する。BE触媒加工8 kFE/tをタービン2基などで供給できる導線は残す。

**修正箇所・根拠：** [量子熱機関][quantum]、[触媒効果][catalyst-helper]、[レシピ][recipes]。

**完了条件：** ミュオンでの仮運転の位置付けが説明され、BE化に明確な利点がある。調整後も初BE触媒を作れる。

## SNG-009 — 低純度触媒でも設備材料としての価値が変わらない

**P1／静的確認・仕様判断。**

時間結晶育成は要求30,000 FE/tだが、24,000 FE/t以上の部分給電なら純度を失わない。極端な不足でも正の給電が続けば成長し、全稼働tickで不足した場合は寿命最小1 tickの時間結晶ができる。レシピの `count` 消費は残寿命を要求しないため、短寿命品も設備素材に使える。SNG-001の製作不能を解消する際に、この挙動も判断する必要がある。

**修正候補：** 設備用モジュール製作に最低純度・残寿命を要求するか、運転には不向きでも設備材料には使える仕様として明記する。80%給電で完全品質になる閾値も説明する。

**修正箇所・根拠：** [TimeCrystalIncubatorBlockEntity][incubator]、[MachineBlockEntity][machine] の `drawPartial`、[MachineRecipe][recipe-code] の材料照合。

**完了条件：** 完全給電、80%、80%未満、微小給電、給電なしを試し、完成までの進行・品質・設備レシピへの可否が決定した仕様どおりになる。

## SNG-010 — 触媒説明の適用対象が曖昧

**P2／静的確認。**

下位速度0.5倍・消耗2倍・残寿命による電力増は触媒デバイスに適用される。一方、汎用加工機はレシピ材料として個数または使用回数を消費する。BE触媒の加工はミュオン触媒を1個消費して40秒、時間結晶はBE触媒を1個消費して12分で、一般説明から予想される倍の時間にはならない。

**修正候補：** 「運転用触媒」と「加工材料としての触媒」を手引き・ツールチップ・JEIで区別する。実装へ運転用触媒を追加する場合は、初触媒を作る工程で循環依存が生じないか先に確認する。

**修正箇所・根拠：** [CatalystDeviceBlockEntity][catalyst-device]、[汎用加工][machine]、[日本語説明書][manual]、`tools/guide.py`・`tools/descriptions.py`。

**完了条件：** 装置・レシピごとに、何が減るのか、速度倍率が適用されるのかを説明から判断できる。

## SNG-011 — 数値・複製上限・建造案内の整合性

**P2／静的確認・説明修正。**

以下を個別に確認・修正する。

- [ ] 時間結晶：日本語説明書の24,000 tick・20分を、現行JSONの14,400 tick・12分へ同期する。
- [ ] 極低温タービンのコメント「塔1基で約2.5基」：高さ7は約3.57基、高さ10は5基。窒素専用運転での値であり、他レシピとの共用時は減ることも記す。
- [ ] ホライズン・バス：旧レシピ注記4 GFE/tと、登録値の実質無制限・他Modとの転送制約を整合させる。
- [ ] 残響の欠片：「拾った1個から最大17個ぶん」という注記は、一つの型の利用回数と、出力を次の型にする継続複製を区別する。
- [ ] 最小加速器：レシピ資料の管21・磁石7という成立可能構成と、既定ホロ投影の管18・磁石10を区別する。「最小直径9」という旧注記と一辺8の実装表現も統一する。
- [ ] 形付きレシピ：冒頭の素材辞書ではなく、末尾の形状上書き後の数で資料を生成する。基礎回路の銅は2個。
- [ ] 設定・触媒の説明もSNG-002・010の判断に合わせ、他言語・ゲーム内手引きの同様のずれを確認する。他言語の全件監査は今回未実施。

**修正箇所・根拠：** [manual-ja.md][manual]、[recipes.py][recipes]、[CryogenicTurbineBlockEntity][turbine]、[CoolingTowerControllerBlockEntity][tower]、[Blueprints][blueprints]、[ケーブル登録][blocks]、各説明生成元。

**完了条件：** 実行値・生成JSON・JEI・手引き・外部説明書が同じ前提を示す。可変サイズや別構成は、どの構成の数字か識別できる。

## SNG-012 — 初期素材の探索負担が大きい

**P2／計算評価・調整候補。**

セラミックにネザー水晶が必要で、通常の進行では初発電前にネザー素材を要求する。冷却塔高さ7＋タービン2基には青氷13個、高さ10への増築込みで18個。氷から全量クラフトする場合は1,053／1,458個の氷に相当する。自然採集・交易では負担が異なり、発電用の低温源は別途必要。

**調整候補：** 初期セラミックにOverworld代替レシピを用意するか、ネザー探索後に始めるModと明示する。初期冷却部品は氷塊を許可し、青氷を上位効率用にする案を試す。

**修正箇所・根拠：** [レシピ][recipes]、[資材付表](calculation-tables.md)、手引き。

**完了条件：** 異なる地形の新規ワールドで初発電・初窒素までの移動と採集時間を記録し、目標難易度に合わせて採否を決める。

## SNG-013 — 熱電の劣化条件と安定電源の案内不足

**P2／静的確認・改善候補。**

溶岩＋青氷は105.7 FE/tだが、通常世界・単独使用の計算では最初の劣化まで約38.2秒。溶岩＋水なら約7.4秒。一方、焚き火＋氷塊54.7 FE/t、マグマブロック＋青氷75.7 FE/tは許容差内で安定する。単純に「温度差が大きいほどよい」と考えると消耗を見落とす。

**修正候補：** 温度差・許容差・劣化予告に加え、手引きに安定構成と補充前提の構成を載せる。最初から8基を要求する案内ではなく、2〜4基で加工を交互に動かし増設する手順を示す。

**修正箇所・根拠：** [温度データ][thermal]、[ThermoelectricGeneratorBlockEntity][thermo]、手引き。

**完了条件：** 初見プレイヤーが、安定電源と消耗型電源を画面・説明から選べる。水の再生成、低温源の共有、ネザーでの違いは条件付きで説明する。

## SNG-014 — 点火時の帯域不足・旧ケーブル混在を診断しにくい

**P1／計算評価・改善候補。**

点火50 GFEを10秒で入れるには平均250 MFE/tが必要。SMES25基で蓄電量を確保しても、単一のトポロジカル網100 MFE/tでは10秒に20 GFEしか送れない。ネットワークの帯域は最も細いケーブルで制限されるため、Horizon幹線に旧ケーブルの枝を残しても上限が下がる。

**修正候補：** 点火前に蓄電量と受電帯域を別々に表示し、不足理由・混在ケーブルの位置を示す。失敗時の案内を「エネルギー不足」だけにしない。

**修正箇所・根拠：** [EnergyNetwork][network]、[SmesCellBlockEntity][smes]、[リアクター][reactor]、関連GUI・手引き。

**完了条件：** 同じ50 GFEでもTopo単一網では失敗、適切なHorizon網では成功することを検証し、どちらも事前に理由が分かる。まず説明・診断を整備し、点火エネルギーの削減とは分けて判断する。

## SNG-015 — 時間結晶の設備材料と運転補充分が競合する

**P2／計算評価・調整候補。**

成長12分、寿命45分なら育成槽1基で毎時5個、連続稼働デバイス3台が毎時4個を消費する。設備用に回せるのは毎時1個で、20%の生産停止でも維持余裕がなくなる。報告書の保守的な設備・在庫バスケットでは点火前に11個、成長132分＋種復元4分＝136分・台を要する。これは最小必須数でも攻略総時間でもない。

**修正候補：** SNG-001の設備モジュール化と合わせて需要を整理する。育成槽2基、運転用在庫の確保、初回建造用の要求数を検討する。12分だけを単独で短縮して生産サイクル全体の意味を失わせない。

**修正箇所・根拠：** [レシピ][recipes]、[育成槽][incubator]、[付表](calculation-tables.md)。

**完了条件：** 初回建造・発電維持・予備在庫を分けて計測し、次の設備を作るたびに既存の工場を止める必要がないか確認する。

## SNG-016 — 共用設備の実効生産能力が計画しにくい

**P2／計算評価・改善候補。**

高さ7の冷却塔は窒素142.86 mB/s。タービン2基の80 mB/sに生産時間の56%が必要で、コイル・線材・ヘリウム加工と競合する。入力ありレシピが窒素より優先されるため、専用運転時の供給可能台数を共用時へ流用できない。

また、高圧結晶54個は収集器2基・水深48なら27分、水深16なら81分。Pリアクター最大出力付近の燃料供給は5 pellet/s、通常圧縮機で理想10台、岩石質量80/sを要する。加工時間だけでは物流・チャンクロードの負担を把握できない。

**修正候補：** 現在のmB/s・item/s・実効加工時間、窒素バッファ、収集環境倍率、必要ライン台数を手引きまたはGUIへ出す。自動化例では必要な配管・ポート・出力分離・チャンク保持も示す。

**修正箇所・根拠：** [冷却塔][tower]、[レシピ選択・排出][machine]、[深海収集器][deep-sea]、[リアクター][reactor]。

**完了条件：** 専用運転と共用運転で供給不足の理由が分かる。表記どおりの構成を通常の搬送で動かし、1時間の供給／消費と停止時間を測る。

## SNG-017 — 探索ゲートの集中と地形による所要時間の振れ

**P2／調整候補・要実機検証。**

T3〜T5では培養施設、古代都市の残響の欠片・スカルク、海底、エンドの虚空の塵、ウィザー、最終施設などが重なる。遺構の配置間隔は最寄り距離や所要時間を保証しない。初回必須素材を見つけるまでの時間は未測定。

**修正候補：** 段階ごとの探索案内と先行収集目標を用意し、初回必須素材の最低収量・救済経路を点検する。量子原本は修復で再遠征を減らせ、残響の欠片は継続複製できることを伝える。すべての素材不足を再探索で解決させる案内にしない。

**修正箇所・根拠：** [レシピ][recipes]、[遺構配置][structure-sets]、[UsesHelper][uses-helper]、手引き・探索導線。

**完了条件：** 複数seedで探索先ごとの発見時間・必須収量・修復利用を記録する。配置間隔から平均探索時間を推定しただけで調整完了としない。

## SNG-018 — ボス戦とマルチプレイの実効難易度が未評価

**P2／要実機検証。**

ホライズン・ウォーデンは設定HP1,200相当、挑戦者不在のチェック5回で撤退リセットする。内部HP上限への対応としてダメージ倍率を使うため、表示・内部HPだけで戦闘時間を算出できない。攻撃機会・回避・装甲・死亡復帰が未測定であり、「HPが高すぎる」とはまだ断定しない。

**検証・調整候補：** ソロ標準装備、2人以上、他Mod装備を分けて勝率と所要時間を測る。やり直しが重ければHP・攻撃間隔・リセット猶予を個別に試す。必須ドロップの共有、施設の初回報酬と再生待ちも確認する。

**修正箇所・根拠：** [HorizonWarden][warden]、[RuinCacheBlockEntity][cache]、戦闘関連設定。

**完了条件：** 推奨装備・死亡回数・戦闘時間・復帰時間を記録し、人数別の基準を定める。想定DPSだけで適正HPを確定しない。

## SNG-019 — 大出力の用途と点火後の進行目標が弱くなる可能性

**P3／改善候補。**

Pリアクター最大2.1 GFE/tは強い最終報酬になる一方、ワームホール1対の生成20 GFEなども余裕をもって賄える。初点火後に何を作り、何を改善するかが伝わらないと、巨大発電が蓄電量を増やすだけの目的になりやすい。これは数値不具合ではなく体験上の仮説。

**改善候補：** H凝縮体6個→初コア→人工星核でスター依存解消→遠隔拠点のワームホール→時間加速による工場改良、という達成目標を置く。最大出力維持・省燃料運転・触媒回収を別の挑戦にする。

**修正箇所・根拠：** [レシピ][recipes]、[リアクター][reactor]、`tools/guide.py`・`tools/advancements.py`。

**完了条件：** プレイヤーが点火後に次の目標を選べ、各終盤装置の具体的な使い道を理解できる。新しい消費装置の追加は必須としない。

## SNG-020 — 正式版を判断するための実機検証が不足

**P1／要実機検証。**

今回行ったのはレシピ126件の時間・電力・出力数照合、静的投入条件の確認、資材展開、独立した炉心シミュレーション。配布JARの通しプレイやGameTest成功を意味しない。個別のスロット仕様テストだけではSNG-001のような進行停止を拾えない。

**実施タスク：**

- [ ] 通常操作で全必須レシピを作る。GUI・自動搬入・材料数・使用済み触媒排出まで検証する。
- [ ] 新規standardワールドで、アイテム支給・NBT直接操作なしに初コアまで到達する。
- [ ] JEIあり／なしで、素材の入手先と次の目標が分かるか確認する。
- [ ] SNG-002の各設定を変え、加工結果・JEI表示・保存／再読込を確認する。
- [ ] 部分給電・停止・再開・満充電・出力詰まり時の進捗と消耗を確認する。
- [ ] SMESの入出力方向、ケーブル混在、点火失敗・再試行を確認する。
- [ ] 初点火後にH凝縮体6個と初コアを取得する。
- [ ] チャンク離脱・再ログイン・サーバー再起動の前後で、育成・炉心・蓄電・触媒・発見済み遺構の状態を比較する。
- [ ] 自動化Mod併用で、流体の出力分離、触媒の補充と排出、圧縮ラインを1時間運転する。現時点で互換性不具合が確定したという意味ではない。
- [ ] ソロ／複数人で戦闘・初回報酬・再生を検証する。
- [ ] 配布候補JARと評価対象commitを対応付ける。1.20.1移植版も公開対象なら版ごとに主要経路を確認する。

**記録項目：** seed、Mod一覧、設定、マイルストーン時刻、採集・探索・建造・電力待ち・素材待ちの時間、死亡回数、停止原因。初発電、初窒素、初ミュオン、量子熱機関、初時間結晶、縮退熱炉、初殻、点火、H凝縮体6個、初コア、初ワームホールを追う。並行作業の時間を二重計上しない。

**完了条件：** P0が解消され、P1の対応／仕様判断が記録され、通常進行のログが残る。最初は3ワールド程度で問題を洗い出し、平均所要時間の確定には追加の計測を行う。

## 推奨する対応順序

1. SNG-001を修正し、通常投入の成功をSNG-020で確認する。
2. SNG-002・003の設定を整理する。以後の調整が実際に反映されるようにする。
3. SNG-004・005・009を判断し、回収運転・停止時消耗・品質の仕様を決める。
4. SNG-006の資材負担を試験調整し、その後にSNG-007・008の発電量を比較する。
5. SNG-010〜017の説明と工程案内を、採用した仕様へ同期する。
6. SNG-018・020の実測で正式版を判定し、SNG-019は必要に応じて次版へ回す。

調整候補は「採用しない理由と検証結果を記録する」ことでも完了にできる。未検証の懸念を、確認済みの不具合として公開しない。

## 根拠リンク

以下はすべて評価対象commitに固定したソース。

[uses-item]: https://github.com/GenichiMaruo/Singulo/blob/b45f478819fa205091358a0f715edd9761d45862/src/main/java/io/github/genichimaruo/singulo/item/UsesItem.java
[machine-type]: https://github.com/GenichiMaruo/Singulo/blob/b45f478819fa205091358a0f715edd9761d45862/src/main/java/io/github/genichimaruo/singulo/machine/MachineType.java
[machine]: https://github.com/GenichiMaruo/Singulo/blob/b45f478819fa205091358a0f715edd9761d45862/src/main/java/io/github/genichimaruo/singulo/machine/MachineBlockEntity.java
[menu]: https://github.com/GenichiMaruo/Singulo/blob/b45f478819fa205091358a0f715edd9761d45862/src/main/java/io/github/genichimaruo/singulo/machine/MachineMenu.java
[recipes]: https://github.com/GenichiMaruo/Singulo/blob/b45f478819fa205091358a0f715edd9761d45862/tools/recipes.py
[assembler-recipes]: https://github.com/GenichiMaruo/Singulo/blob/b45f478819fa205091358a0f715edd9761d45862/src/generated/resources/data/singulo/recipe/precision_assembler
[config-spec]: https://github.com/GenichiMaruo/Singulo/blob/b45f478819fa205091358a0f715edd9761d45862/tools/config_spec.py
[config]: https://github.com/GenichiMaruo/Singulo/blob/b45f478819fa205091358a0f715edd9761d45862/src/generated/java/io/github/genichimaruo/singulo/generated/ServerConfig.java
[recipe-code]: https://github.com/GenichiMaruo/Singulo/blob/b45f478819fa205091358a0f715edd9761d45862/src/main/java/io/github/genichimaruo/singulo/recipe/MachineRecipe.java
[anchor]: https://github.com/GenichiMaruo/Singulo/blob/b45f478819fa205091358a0f715edd9761d45862/src/main/java/io/github/genichimaruo/singulo/machine/WorldlineAnchorBlockEntity.java
[advanced-anchor]: https://github.com/GenichiMaruo/Singulo/blob/b45f478819fa205091358a0f715edd9761d45862/src/main/java/io/github/genichimaruo/singulo/machine/AdvancedWorldlineAnchorBlockEntity.java
[reactor]: https://github.com/GenichiMaruo/Singulo/blob/b45f478819fa205091358a0f715edd9761d45862/src/main/java/io/github/genichimaruo/singulo/reactor/PenroseReactorBlockEntity.java
[turbine]: https://github.com/GenichiMaruo/Singulo/blob/b45f478819fa205091358a0f715edd9761d45862/src/main/java/io/github/genichimaruo/singulo/machine/CryogenicTurbineBlockEntity.java
[quantum]: https://github.com/GenichiMaruo/Singulo/blob/b45f478819fa205091358a0f715edd9761d45862/src/main/java/io/github/genichimaruo/singulo/machine/QuantumHeatEngineBlockEntity.java
[furnace]: https://github.com/GenichiMaruo/Singulo/blob/b45f478819fa205091358a0f715edd9761d45862/src/main/java/io/github/genichimaruo/singulo/machine/DegenerateFurnaceBlockEntity.java
[catalyst-device]: https://github.com/GenichiMaruo/Singulo/blob/b45f478819fa205091358a0f715edd9761d45862/src/main/java/io/github/genichimaruo/singulo/machine/CatalystDeviceBlockEntity.java
[mass]: https://github.com/GenichiMaruo/Singulo/blob/b45f478819fa205091358a0f715edd9761d45862/src/generated/resources/data/singulo/mass_values/default.json
[catalyst-helper]: https://github.com/GenichiMaruo/Singulo/blob/b45f478819fa205091358a0f715edd9761d45862/src/main/java/io/github/genichimaruo/singulo/item/CatalystHelper.java
[incubator]: https://github.com/GenichiMaruo/Singulo/blob/b45f478819fa205091358a0f715edd9761d45862/src/main/java/io/github/genichimaruo/singulo/machine/TimeCrystalIncubatorBlockEntity.java
[manual]: https://github.com/GenichiMaruo/Singulo/blob/b45f478819fa205091358a0f715edd9761d45862/docs/manual-ja.md
[tower]: https://github.com/GenichiMaruo/Singulo/blob/b45f478819fa205091358a0f715edd9761d45862/src/main/java/io/github/genichimaruo/singulo/multiblock/CoolingTowerControllerBlockEntity.java
[blueprints]: https://github.com/GenichiMaruo/Singulo/blob/b45f478819fa205091358a0f715edd9761d45862/src/main/java/io/github/genichimaruo/singulo/multiblock/Blueprints.java
[blocks]: https://github.com/GenichiMaruo/Singulo/blob/b45f478819fa205091358a0f715edd9761d45862/src/main/java/io/github/genichimaruo/singulo/registry/SinguloBlocks.java
[thermal]: https://github.com/GenichiMaruo/Singulo/blob/b45f478819fa205091358a0f715edd9761d45862/src/generated/resources/data/singulo/thermal/default.json
[thermo]: https://github.com/GenichiMaruo/Singulo/blob/b45f478819fa205091358a0f715edd9761d45862/src/main/java/io/github/genichimaruo/singulo/machine/ThermoelectricGeneratorBlockEntity.java
[network]: https://github.com/GenichiMaruo/Singulo/blob/b45f478819fa205091358a0f715edd9761d45862/src/main/java/io/github/genichimaruo/singulo/cable/EnergyNetwork.java
[smes]: https://github.com/GenichiMaruo/Singulo/blob/b45f478819fa205091358a0f715edd9761d45862/src/main/java/io/github/genichimaruo/singulo/machine/SmesCellBlockEntity.java
[deep-sea]: https://github.com/GenichiMaruo/Singulo/blob/b45f478819fa205091358a0f715edd9761d45862/src/main/java/io/github/genichimaruo/singulo/nature/DeepSeaCollectorBlockEntity.java
[structure-sets]: https://github.com/GenichiMaruo/Singulo/blob/b45f478819fa205091358a0f715edd9761d45862/src/generated/resources/data/singulo/worldgen/structure_set
[uses-helper]: https://github.com/GenichiMaruo/Singulo/blob/b45f478819fa205091358a0f715edd9761d45862/src/main/java/io/github/genichimaruo/singulo/item/UsesHelper.java
[warden]: https://github.com/GenichiMaruo/Singulo/blob/b45f478819fa205091358a0f715edd9761d45862/src/main/java/io/github/genichimaruo/singulo/ruin/HorizonWarden.java
[cache]: https://github.com/GenichiMaruo/Singulo/blob/b45f478819fa205091358a0f715edd9761d45862/src/main/java/io/github/genichimaruo/singulo/ruin/RuinCacheBlockEntity.java
