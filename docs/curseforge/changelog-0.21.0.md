# Singulo 0.21.0 — Minecraft 1.21.1 / NeoForge

## Features

- Jade and WTHIT support: looking at a machine shows its status and why it has stopped, progress, power used or generated, the catalyst left, and for an unfinished multiblock the parts it still needs. Looking at a part of a multiblock shows its controller (Jade only). Both mods stay optional.
- The Desk Monitor now opens a factory monitor: power generated, used and stored by machines within 16 blocks, the stalled machines nearest first with their reasons and direction, and how the drone stations are doing. Each Server Rack within 2 blocks of the monitor widens the range by 8 blocks, up to 4 racks.
- Before a Genesis Engine gives birth, materials used on its core shape the galaxy: a dye sets the arm color, a Blaze Rod makes a barred spiral, a Slime Ball an elliptical galaxy, an Eye of Ender a ring galaxy, Magma Cream an irregular galaxy, and a Nether Star makes it grand (1.5 times larger in the night sky). Galaxies born before this version look the same as before.
- Using another Primordial Seed on a Genesis Engine that has given birth kindles it again for the next galaxy, so one engine can fill the night sky (up to eight galaxies). The epilogue plays only for its first galaxy.
- Glass slabs for White Glass Panel, Black Reinforced Glass, Android Gate Glass and Android Barrier Glass. Like the glass stairs, they hide the seams with the same glass, and a full glass block next to them draws only the part they leave open. The android glass slabs let androids through (or stop them) like the glass itself.
- Portable Android Printer: a briefcase printer for androids on the go. Put a compact crystal memory and android kits in it, then right-click the ground: the case pops open, prints one android on the bed inside, closes and returns to your inventory. The android follows you as an android without a station, and can be linked to an Android Assembly Station later. The Portable Android Printer Mk2 holds three sets (three types of android) and prints the next set each time; its screen shows which one comes next and lets you choose it. Each print uses twice the power of the station, and the printer charges as much as one print of the loaded type needs; charge the printer in the charging slot of a power storage. Self-repair kits stocked in it go to the printed android, and while you carry the printer it refills your nearby androids without a station. An android armor plate in it is fitted to the printed android.
- Guard androids pick up items lying nearby, such as drops from enemies they defeat (not items thrown by players), and then store them in a nearby chest or in a work console or supply port. Two new buttons on the gear page switch picking up on or off and choose where to store: chests first, work storage first, or keep the items.
- Manual precision assembly: the Precision Assembler has a 21-cell board (a 5×5 square without its corners). Place up to one stack per cell following the product's pattern and put the catalyst in its own slot. When the layout is complete and the machine has enough power, it consumes one part from each occupied cell per craft. To see a pattern, hover over a product in the assembler's recipe list, or look it up in JEI. Click JEI's + button to lay out one batch or Shift-click it for as many as fit. Androids place parts on the same board using their processing cards. Spent catalysts go to their own output slot, which pipes and hoppers can empty.
- Paired recipes make two batches at once for the Degenerate Compactor, C-Cavity and Degenerate Furnace frames and the Mirror Plate. Each pattern places its two sets diagonally on one board with one catalyst, giving 8 frames or 2 mirror plates.
- Handbook visual guides: multiblock pages show the structure layer by layer, with a list of blocks for each layer. Process pages show recipe diagrams. The first chapter starts by asking what you are working on and links to the matching pages. Next goals suggests only early technical goals until you complete them, so later goals stay hidden.
- The Electrolyzer's oxygen tank tooltip suggests feeding oxygen into a Kiln, which makes steel with less coal. The handbook shows the same oxygen steelmaking route.

## Changes

- Multiblock part recipes now use their facility's base part. Cooling tower parts use a Cooling Tower Base and accelerator parts a Basic Frame. Other facilities use their own base part: the C-Cavity Frame, Degenerate Compactor Frame, Degenerate Furnace Frame, T-Cylinder Frame, Jet Forge Truss, Shield Tower Plinth or Wormhole Generator Shell. Controllers use at least one part of their facility.
- The Android Assembly Station shows the print cost of the loaded android type. A Factory Print Unit halves it.
- Item names in advancements, screens and the handbook now match the items. Examples are Compact Crystal Memory, Degraded Control Unit and Decoded Record. The drone port hints describe auto-send more precisely.
- Handbook pages are shorter and start with what to do.
- Upgrading from an older version: materials loose in a Precision Assembler are returned to your inventory when you open its screen. Anything that does not fit stays until the next time you open it, or drops when the assembler is broken. A recipe that was in progress starts over on the board.

## Fixes

- Upgrading a packed SMES Cell preserves the items in both charging slots and their individual charge levels.
- An Android Assembly Station with a Transfer Upgrade restores an idle android's tank contents when it reprints the android. Cargo drones finish dismantling and return their kit after a world reload.
- Singulo fluid pipes now respect each remote machine's fluid filter and input-only fuel tanks when connected through a wormhole.
- Each player must confirm their own permanent core installation in an Advanced Worldline Anchor. On Peaceful difficulty, ruin consoles keep boss summoning items in the player's hand.
- Probe expeditions now apply the server's loot count multiplier before calculating their share of a manual expedition's yield.
- Automatic deposits move only the items a guard picked up. Cargo from older saves stays with the guard until you use the manual deposit button.
- Upgrading a Portable Android Printer to Mk2 keeps its loaded supplies and charge. Changing to a lower-cost memory preserves excess charge for later prints. Opening the printer and dismantling a guard now keep self-repair kit counts consistent.
- The Time Crystal Incubator waits for output space when a new catalyst has a different lifetime from the one in its output slot. Reloading a world preserves the low-power growth history and the mix of materials already fed into a compressor.
- The factory monitor lists a Genesis Engine with a missing structural part as stalled. Replacing the part restores its previous phase.
- Androids no longer pick a spot on Android Gate Glass to stand on while working or when leaving water, and no longer treat it as a floor while flying or as a lid over water. Android Barrier Glass now counts as a floor for them.
- Waterlogged glass stairs draw their water like other stairs but no longer show water inside the glass (not with Sodium).
- A finished Genesis Engine no longer shows what it is synthesizing above the hotbar every time you walk near it. Its screen still shows the progress.
- A cargo drone keeps its loaded crate and brings it home when its destination port is removed or replaced during pickup or delivery. A guard android keeps its equipment when it falls, even with mob loot turned off. A guard that dies in another dimension is reprinted at its home station. A guard whose station was removed in an older save links to a new station.
- SMES Cells share their input rate across all sources and their output across pulling, pushing and the charging slots.
- Android work orders: androids can use liquid from tanks in place of a filled bucket, and only the slots that used a real bucket return an empty bucket. Androids collect both Electrolyzer products, freeze lava and water with supplied nitrogen and refuel furnaces with lava buckets. When a tool runs out, they put a new one back into the machine.
- Two work consoles can no longer reserve the same ingot or tank. Procedure cards keep the dimension they were recorded in and do not act on the same coordinates in another dimension.
- With Fast or Fancy graphics, android weapon glows now draw correctly with glass, water and other translucent blocks behind or in front of them.
- The Laser Cooler pays a recipe's liquid helium from both input tanks together and waits until they hold enough.
- Precision Assembler: JEI repair transfers pick only an exhausted Observation Log. A board that lacks only its catalyst names the product it will make. The assembler waits for space in the spent catalyst slot without using up catalyst life.

## 追加

- Jade・WTHIT に対応しました。装置を見るだけで、状態と止まっている理由・進み具合・使っている（作っている）電力・触媒の残り、組み上がっていないマルチブロックでは足りない部品が分かります。マルチブロックの部品を見ると、そのコントローラーの様子が出ます（Jade のみ）。どちらも入れなくても遊べます。
- 机のモニターを使うと、工場モニターが開くようにしました。まわり16ブロックの装置の発電・消費・蓄電、止まっている装置（近い順に、理由と方角つき）、ドローン・ステーションの様子が分かります。モニターから2ブロック以内にサーバーラックを置くと、1台ごとに範囲が8ブロック広がります（4台まで）。
- 創世炉で宇宙が生まれる前に、素材を核に使うと銀河の形と色を選べるようにしました。染料で腕の色、ブレイズロッドで棒渦巻銀河、スライムボールで楕円銀河、エンダーアイで環状銀河、マグマクリームで不規則銀河、ネザースターで大きな銀河（夜空で1.5倍）になります。これまでに生まれた銀河の見た目は変わりません。
- 宇宙を生んだ創世炉にもう一度原初の種を使うと、種火からやり直して次の銀河を生めるようにしました。1つの炉で夜空を飾れます（8個まで）。エピローグは最初の銀河のときだけ流れます。
- ホワイトガラスパネル・黒色強化ガラス・アンドロイド透過ガラス・アンドロイド遮断ガラスのハーフブロックを追加しました。ガラスの階段と同じく、同じガラスとの境目は描かず、隣の同じガラスのブロックは、ハーフブロックに隠れていない所だけを描きます。アンドロイド用のガラスのハーフブロックは、元のガラスと同じくアンドロイドを通します（遮断ガラスは止めます）。
- 携帯型アンドロイド印刷機を追加しました。カバン型の印刷機で、小型クリスタルメモリとアンドロイドキットを入れて地面を右クリックすると、カバンがパカっと開いて中の印刷面でアンドロイドを1体印刷し、閉じて持ち物へ戻ります。印刷したアンドロイドは、ついて歩く身寄りのないアンドロイドになり、あとで組立ステーションにリンクできます。携帯型アンドロイド印刷機（Mk2）は3組（3種類のアンドロイド）を積め、印刷するたびに次の組で1体ずつ印刷します。次に印刷する組は画面で分かり、選ぶこともできます。印刷ごとに組立ステーションの2倍の電力を使い（入れたメモリの型の1体分だけ充電できます）、蓄電装置の充電の枠で充電します。自己修理キットを入れておくと、印刷したアンドロイドに持たせ、持ち歩いている間は近くの身寄りのないアンドロイドに補充します。アンドロイド用装甲板を入れておくと、印刷したアンドロイドに積みます。
- 護衛のアンドロイドが、近くに落ちている物（倒した敵の落とし物など。プレイヤーが投げた物は除く）を拾い、近くのチェストか、作業指令台・資材口へ預けるようにしました。装備のページの新しいボタンで、拾うかどうかと、預け先（チェスト優先・作業優先・預けない）を切り替えられます。
- 精密組立台で、部品を手で組み立てるようにしました。組立盤は5×5から四隅を除いた21マスです。製品の配置図に合わせて各マスに1スタックまで部品を置き、触媒は専用の枠に入れます。配置がそろい、電力が足りると、各マスから1個ずつ使って組み立てます。配置図は、組立台のレシピ一覧で製品にカーソルを合わせるか、JEI で確認できます。JEI の＋ボタンで1回分、Shift＋クリックで置ける回数分を並べられます。アンドロイドも加工カードを使って、同じ組立盤に部品を置きます。使い終わった触媒は専用の出力枠に入り、パイプやホッパーで取り出せます。
- 縮退圧縮炉・C空洞・縮退熱炉のフレームと、C空洞の鏡面プレートに、2回分をまとめて作るレシピを追加しました。1枚の組立盤に2組の部品を斜めに並べ、触媒1個でフレーム8個、または鏡面プレート2枚ができます。
- ハンドブックに図を加えました。マルチブロックのページでは構造を1段ずつ表示し、段ごとに必要なブロックの一覧も出ます。工程のページにはレシピの図が付きます。最初の章では、取り組んでいることを選ぶと該当するページへ進めます。「次の目標」には、達成するまで序盤の技術の目標だけを表示し、先の目標は伏せます。
- 電解槽の酸素タンクにカーソルを合わせると、酸素を焼成炉に入れると少ない石炭で鋼鉄を作れることが分かります。ハンドブックにも、この酸素製鋼の手順を載せました。

## 変更

- マルチブロックの部品のレシピには、その設備の土台になる部品を使います。冷却塔の部品には冷却塔基部、加速器の部品には基礎フレームを使います。ほかの設備では、C空洞フレーム・縮退圧縮炉フレーム・縮退熱炉フレーム・Tシリンダーフレーム・ジェット鍛造炉の骨組み・シールド発生塔基壇・ワームホール生成器外殻のうち、その設備のものを使います。コントローラーには、その設備の部品を1個以上使います。
- アンドロイド組立ステーションの画面に、入れたアンドロイドの型の印刷に必要な電力を表示します。工場式印刷ユニットを付けると半分になります。
- 進捗・画面・ハンドブックに出てくるアイテム名を、実際のアイテム名にそろえました（小型クリスタルメモリ・劣化した制御ユニット・解読した記録など）。ドローン発着ポートの自動発送の説明も詳しくしました。
- ハンドブックの文章を短くし、何をすればよいかを先に書きました。
- 以前のバージョンから更新した場合: 精密組立台に入っていた材料は、画面を開くと持ち物に戻ります。入りきらない分は、次に開いたときに戻るか、組立台を壊すとアイテムとして落ちます。加工途中だったレシピは、組立盤で最初からやり直しになります。

## 修正

- 移設レンチで梱包したSMESセルを強化すると、充電中・充電済みの両方の枠にある装備と、それぞれの充電量を引き継ぎます。
- 転送アップグレード付きの組立ステーションは、作業をしていなかったアンドロイドもタンクの中身ごと再印刷します。輸送ドローンは解体途中でワールドを読み込み直しても解体を続け、キットを返却します。
- ワームホール経由でつないだ液体パイプが、遠隔の装置ごとに搬入できる液体を判定し、入力専用の燃料タンクからの吸い出しを防ぐようになりました。
- 上位ワールドライン・アンカーへのコアの永久埋め込みには、使うプレイヤー本人の確認が必要になりました。ピースフルでは遺構のボスを呼ぶアイテムが手元に残ります。
- 自動探査機は、遠征の回収量倍率を反映したうえで、手動遠征に対する割合から回収数を計算します。
- 護衛が自動で預けるのは自分で拾った分だけです。以前のセーブで持っていた荷物は護衛に残り、手動の「近くの箱へ預ける」でまとめて移せます。
- 携帯型アンドロイド印刷機をMk2に強化すると、装填した物と電力を引き継ぎます。消費電力の少ない型に替えても、余った電力は次の印刷に使えます。印刷機の画面操作中や護衛の解体中に、自己修理キットが増減する問題も修正しました。
- 時間結晶育成槽は、完成する触媒の寿命が出力枠の触媒と異なるとき、取り出すまで待機します。ワールドを再読み込みしても、育成中の電力不足と、圧縮機が取り込んだ素材の種類を引き継ぎます。
- 創世炉の部品を外すと工場モニターの停止一覧に載り、部品を戻すと元の段階に復帰します。
- アンドロイドが、作業する立ち位置や水から上がる場所にアンドロイド透過ガラスの上を選ばないようにしました。飛ぶときの床や、水面のふたとしても扱いません。アンドロイド遮断ガラスの上は、アンドロイドの床として扱います。
- 水に浸かったガラスの階段で、透けるガラスの中まで水が見えていたのを直しました。水はふつうの階段と同じに見え、ガラスの中には入っていないように見えます（Sodium を入れているときは除く）。
- 宇宙を生んだ創世炉のそばを通るたびに、生成中の物が画面下に出ていたのをなくしました。進み具合は創世炉の画面で見られます。
- 輸送ドローンが荷を積み込む途中や届ける途中に行き先のポートが撤去・交換されても、積んだ箱を持ったまま元のポートへ戻ります。護衛のアンドロイドは、Mob のドロップを無効にしていても、倒れたときに装備を失いません。別の次元で倒れた護衛は、元の組立ステーションで再印刷されます。以前のセーブでステーションを撤去された護衛は、新しいステーションにリンクできます。
- SMESセルは、受け取る電力の上限を複数の給電元で分け合い、送り出す電力も吸い出し・送り出し・充電の枠で分け合います。
- アンドロイドの作業注文: 中身入りのバケツの代わりにタンクの液体を使えるようにしました。空のバケツが戻るのは、実物のバケツを使った欄の分だけです。電解槽の2つの生成物をどちらも回収し、渡された液体窒素で溶岩や水を固め、溶岩バケツでかまどに燃料を足します。道具を使い切ったときは、新しい道具を機械に入れ直します。
- 2台の作業指令台が、同じインゴットや同じタンクを取り合わないようにしました。手順カードは記録した次元を覚え、別の次元の同じ座標では作業しません。
- 描画設定が「処理優先」「描画優先」のとき、アンドロイドの武器の光が、前後にあるガラスや水などの半透明のブロックと正しく重なるようにしました。
- レーザー冷却器は、2つの入力タンクの液体ヘリウムを合わせて使い、必要な量がそろうまで待ちます。
- 精密組立台: JEI の修理の転送で、使い切った観測ログだけを選びます。触媒だけが足りないときは、できあがる製品の名前を表示します。使い終わった触媒の出力枠が空くまで、触媒の寿命を減らさずに待ちます。
