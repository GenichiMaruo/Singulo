"""Singulo ハンドブックの中身。

CHAPTERS は章の並び。各章は (id, アイコンのアイテムID, 日本語の題, 英語の題, ページの並び)。
各ページは (id, 日本語の見出し, 日本語の本文, 英語の見出し, 英語の本文)。本文の改行は段落の区切り。
「進み具合」の章は進捗（advancement）から画面側で作るので、ここには書かない。
"""

CHAPTERS = [
    ('intro', 'singulo:handbook', 'はじめに', 'Getting Started', [
        ('welcome', 'Singulo へようこそ',
         'Singulo は「質量からエネルギーを取り出す」工業modです。熱電発電から始めて、極低温・量子・時間の技術を段階的に積み上げ、'
         '最後は回転するブラックホール（Pリアクター）で発電します。\n'
         '段階は5つ。各段階には目標の装置と、次の段階に進むための鍵になる素材があります。'
         '左の「段階1」から順に読めば、何を作ればよいかが分かります。',
         'Welcome to Singulo',
         'Singulo is a tech mod about extracting energy from mass. Start with thermoelectric power, build up '
         'cryogenic, quantum and temporal technology stage by stage, and finally generate power from a '
         'spinning black hole (the P-Reactor).\nThere are five stages. Each has a goal machine and a key '
         'material that opens the next stage. Read from "Stage 1" on the left to know what to build next.'),
        ('howto', 'このハンドブックの使い方',
         '・左の章を選ぶと、右にページが出ます。下の矢印でページをめくります。\n'
         '・「進み具合」の章は工業・探索・アンドロイドの枝に沿って、達成した目標と次の目標を示します（進捗画面［L キー］の Singulo タブとも同じです）。\n'
         '・アイテムにカーソルを合わせると説明が出ます。Shift を押すと作り方と使い道も出ます。\n'
         '・Singulo のアイテムにカーソルを合わせて H キーを押すと、その物のことが書いてあるページが開きます（手に持っているときも）。'
         '章の一覧の上の検索欄に言葉を入れると、合うページを探せます。\n'
         '・マルチブロックは「ホロ投影機」をコントローラに使うと、正しい形が半透明で浮かび上がります。',
         'Using this book',
         '- Pick a chapter on the left to show its pages on the right. Turn pages with the arrows at the '
         'bottom.\n- The Progress chapter follows industry, exploration and android branches and shows completed and upcoming goals (the same as the '
         'Singulo tab of the advancements screen, L key).\n- Hover over an item for its description; hold Shift '
         'to see how it is made and what it is used for.\n- Hover over a Singulo item and press H to open the page about it (or press H while holding it). Type in the search box above the chapter list to find matching pages.\n- Use the Holo Projector on a multiblock controller '
         'to show the correct shape as a translucent outline.'),
        ('basics', '電力・触媒・遺構',
         '電力（FE）は自前のケーブルで運びます。アイテムと液体は装置の面ごとの設定と、他modのパイプで運びます。'
         '液体とガスは、液体キャニスター（1,000 mB）で装置を右クリックすれば、パイプがなくても汲んで運べます。\n'
         '段階2から「触媒」が登場します。触媒は寿命のある消耗品で、触媒を入れる装置は触媒専用のスロットを持ちます。'
         'ティアが装置より1つ上なら速く、1つ下なら遅く減りも速く、2つ下は使えません。\n'
         'ワールドには旧文明の「遺構」があり、そこでしか手に入らない記録や部品があります。'
         '段階1から地表観測拠点を探しておきましょう。',
         'Power, catalysts and ruins',
         'Power (FE) travels through Singulo cables; items and fluids use per-side settings and other mods\' pipes. '
         'Without pipes, right-click machines with a Fluid Canister (1,000 mB) to carry liquids and gases.\n'
         'From stage 2 you use catalysts: consumables with a lifetime, placed in a dedicated catalyst slot. '
         'One tier above the machine is faster; one below is slower and wears faster; two below cannot be used.\n'
         'Ancient ruins hold records and parts you cannot craft. Start looking for Observation Posts early.'),
        ('vanilla', '装置で作るバニラの素材',
         '狩りや遠出の代わりに、装置で作れるバニラの素材です。\n'
         '・圧縮機（段階1）: 丸石→砂利→砂、砂岩→砂2。雪ブロック4→氷、氷4→氷塊、氷塊4→青氷。'
         '方解石→石灰粉（骨粉の代わり）、木炭3→炭素粉2（石炭の代わり）。鉄・金の道具や防具→塊（インゴットの約3分の2）。\n'
         '・焼成炉（段階1）: 未焼成の蛍光体（亜鉛の粉＋銅＋火薬）→グロウストーンダスト4。\n'
         '・ペルチェ冷却器（段階1）: 水→氷。\n'
         '・極低温冷却塔（段階2）: 水＋液体窒素→氷塊、溶岩＋液体窒素→黒曜石。凍結粉砕で 骨→骨粉6、ブレイズロッド→パウダー4、羊毛→糸4。\n'
         '・触媒反応器（段階2）: 砂＋水→ネザー水晶。\n'
         '・量子もつれ合成器（段階3）: 量子もつれ素子＋スライムボール→エンダーパール。\n'
         '・粒子加速器（段階3）: ビスマスインゴット＋炭素粉→金インゴット。\n'
         '・縮退圧縮炉（段階4）: 石炭ブロック8→ダイヤモンド。\n'
         '・時間結晶育成槽（段階4）: 原木→石炭、丸石→苔むした丸石、銅ブロック→錆びた銅。',
         'Vanilla materials from machines',
         'Machines can make these vanilla materials, so you can skip some hunting and travel.\n'
         '- Compressor (stage 1): cobblestone to gravel to sand, sandstone to 2 sand. 4 snow blocks to ice, 4 ice to packed ice, '
         '4 packed ice to blue ice. Calcite to limestone powder (instead of bone meal), 3 charcoal to 2 carbon dust '
         '(instead of coal). Iron and gold tools and armor to nuggets (about two thirds of the ingots).\n'
         '- Kiln (stage 1): unfired phosphor (zinc dust + copper + gunpowder) to 4 glowstone dust.\n'
         '- Peltier Cooler (stage 1): water to ice.\n'
         '- Cryogenic Cooling Tower (stage 2): water + liquid nitrogen to packed ice, lava + liquid nitrogen to obsidian. '
         'Cryo-milling turns a bone into 6 bone meal, a blaze rod into 4 powder and wool into 4 string.\n'
         '- Catalytic Reactor (stage 2): sand + water to nether quartz.\n'
         '- Entanglement Synthesizer (stage 3): entangled element + slime ball to ender pearl.\n'
         '- Particle Accelerator (stage 3): bismuth ingot + carbon dust to a gold ingot.\n'
         '- Degenerate Compactor (stage 4): 8 coal blocks to a diamond.\n'
         '- Time Crystal Incubator (stage 4): logs to coal, cobblestone to mossy cobblestone, copper block to oxidized copper.'),
    ]),
    ('stage1', 'singulo:thermoelectric_generator', '段階1 起動', 'Stage 1: Startup', [
        ('flow', '段階1の流れ',
         'この段階でやること（上から順に）:\n'
         '1. 鋼鉄・セラミック・基礎回路・基礎フレームを作る（「最初の素材」）。\n'
         '2. 熱電発電機を2〜4台置き、銅導線で装置につなぐ。マグマブロック＋青氷なら溶けずに長持ちする。\n'
         '3. 焼成炉・圧縮機（鋼板）・アーカイブ端末をそろえる。探索コンパスとホロ投影機も作っておく。\n'
         '4. 探索コンパスで地表観測拠点を探し、保管庫から劣化した制御ユニットと観測ログを持ち帰る。\n'
         '5. アーカイブ端末で制御ユニットを修復し、極低温冷却塔の部品を作って、ホロ投影機を見ながら組み立てる。\n'
         '6. 冷却塔で「超伝導コイル」を作れたら段階2。\n'
         'つまずきやすい所:\n'
         '・低温側のブロックが溶ける: 溶岩や、焚き火＋水・雪・氷は短い時間で溶ける。「溶けない組み合わせ」のページを見る。\n'
         '・冷却塔が組み上がらない: 中心の列は熱交換コア、てっぺんは通気格子。ホロ投影機をコントローラに使うと、足りない所が分かる。\n'
         '・電力が足りない: 冷却塔は液体窒素を作るのに 240 FE/t ほど使う。発電機を増やすか、装置を交互に動かす。',
         'Stage 1 at a glance',
         'What to do, in order:\n'
         '1. Make steel, ceramic, basic circuits and basic frames (see "First materials").\n'
         '2. Place two to four Thermoelectric Generators and wire them to your machines with copper wire. Magma blocks w'
         'ith blue ice last without melting.\n'
         "3. Build a Kiln, a Compressor (steel plates) and an Archive Terminal. Make an Explorer's Compass and a Holo Pr"
         'ojector too.\n'
         '4. Use the compass to find the Surface Observation Post and bring back the degraded control units and observat'
         'ion logs from its cache.\n'
         '5. Restore a control unit in the Archive Terminal, make the Cryogenic Cooling Tower parts and build it followi'
         'ng the Holo Projector.\n'
         '6. Once the tower makes a Superconducting Coil, you are in stage 2.\n'
         'Common sticking points:\n'
         '- The cold block melts: lava, or a campfire with water, snow or ice, melts quickly. See the "Pairs that don\'t '
         'melt" page.\n'
         "- The tower won't form: the center column is heat exchanger cores and the top is vent grilles. Use the Holo Pr"
         'ojector on the controller to see what is missing.\n'
         '- Not enough power: the tower uses about 240 FE/t to make liquid nitrogen. Add generators or run machines in t'
         'urns.'),
        ('materials', '最初の素材',
         '1. 鉄と石炭で「鋼の素」を作り、溶鉱炉で鋼鉄インゴットにする。\n'
         '2. 粘土・ネザー水晶・骨粉・砂で「未焼成セラミック」を作り、かまどでホワイトセラミック複合材にする。'
         '骨粉の代わりに、方解石を砕いた石灰粉でもよい（作業台で1個から1個、圧縮機なら2個）。'
         'ネザーへ行く前なら、ネザー水晶の代わりに火打石2個でも作れる。\n'
         '3. セラミック・レッドストーン・銅で「基礎回路」、鋼鉄とセラミックで「基礎フレーム」を作る。\n'
         'この3つ（鋼鉄・セラミック・回路）がほとんどの装置の材料です。',
         'First materials',
         '1. Mix iron and coal into steel blend and smelt it into steel ingots in a blast furnace.\n2. Make '
         'unfired ceramic from clay, nether quartz, bone meal and sand, and fire it in a furnace into white '
         'ceramic composite. Before you reach the Nether, two flint can replace the nether quartz. Limestone powder, crushed '
         'from calcite (1 at a crafting table, 2 in the Compressor), can replace the bone meal.\n3. Make '
         'basic circuits from ceramic, redstone and copper, and basic frames from steel and ceramic.\nThese '
         'three (steel, ceramic and circuits) go into almost every machine.'),
        ('power', '最初の電源',
         '「熱電発電機」は、高温のブロックと低温のブロックに挟むと温度差で発電します（温度差1Kにつき0.10 FE/t）。'
         'マグマブロック・燃えている焚き火や焼成炉を片側に、青氷や氷塊をもう片側に置きます。\n'
         '低温のブロックには保てる温度差があり、超えると溶けて別のブロックに変わります（水は蒸発します）。'
         '溶けない組み合わせは次のページを見てください。\n'
         '電力は銅導線でつなぎます。長くつなぐと少しずつ損失があります。',
         'First power',
         'The Thermoelectric Generator produces power from a temperature difference (0.10 FE/t per kelvin): put something hot '
         '(a magma block, a lit campfire or kiln) on one side and something cold (blue ice, packed ice) on the other.\n'
         'Each cold block only tolerates so much difference; beyond that it melts into another block (water evaporates). '
         'See the next page for pairs that last.\n'
         'Connect machines with copper wire.'),
        ('thermal_pairs', '溶けない組み合わせ',
         '低温のブロックが溶けずに使い続けられる組み合わせ（発電機1台の出力）:\n'
         '・マグマブロック＋青氷: 約76 FE/t（いちばんおすすめ）\n'
         '・焼成炉（稼働中）＋青氷: 約66 FE/t\n'
         '・焚き火＋青氷: 約56 FE/t\n'
         '・焚き火＋氷塊: 約55 FE/t\n'
         '溶岩は熱すぎて、青氷でも約40秒で氷塊に変わり、さらに溶けていきます。焚き火に水・雪・氷を合わせると、1分足らずで溶けます。\n'
         '段階2の液体窒素は100 mBに約25,000 FE。高さ7の塔では稼働中に約1,786 '
         'FE/tを使います。給電不足なら進捗を保って待つので、初期は2〜4台で装置を交互に動かし、発電機と蓄電を増やして核融合炉を目指せます。定速運転を最初から満たす必要はありません。',
         'Pairs that last',
         'Pairs whose cold block never melts (output of one generator):\n'
         '- Magma block + blue ice: about 76 FE/t (best)\n'
         '- Running kiln + blue ice: about 66 FE/t\n'
         '- Campfire + blue ice: about 56 FE/t\n'
         '- Campfire + packed ice: about 55 FE/t\n'
         'Lava is too hot: even blue ice turns into packed ice in about 40 s and keeps melting. Campfires with water, '
         'snow or ice melt them in under a minute.\n'
         'Stage 2 liquid nitrogen costs about 25,000 FE per 100 mB. A height-7 tower draws about 1,786 FE/t while '
         'working. It retains progress while waiting for enough power. Start with 2–4 generators and one machine at a '
         'time, add generation and storage, then build a fusion reactor; full-speed power is not a prerequisite.'),
        ('machines', '段階1の装置',
         '・焼成炉: セラミック基板などを焼く。鋼鉄も速く焼ける。酸素を入れると、鉄2と石炭1から鋼鉄2を作る純酸素転炉になる。\n'
         '・圧縮機: 鋼板を作る。質量モードでは質量値のある素材を「質量」に変えて圧縮ブロックや質量ペレットにする。\n'
         '・電解槽: 水を水素と酸素に分ける。水素は液体キャニスターで汲んで、段階2の粒子加速器へ運べる。\n'
         '・水素燃料電池: 水素と酸素で発電する（電解槽で詰めた電力の6割を取り戻す蓄電。最大240 FE/t）。\n'
         '・ペルチェ冷却器: 電力で水を凍らせる。氷は圧縮機で氷塊・青氷にできる。\n'
         '・アーカイブ端末: 遺構の記録をデータカードに書き写し、劣化した部品を修復する。クリスタルメモリの解読もできる。',
         'Stage 1 machines',
         '- Kiln: fires ceramic substrates and more. It also makes steel faster. With oxygen it works as a basic oxygen furnace: '
         '2 iron and 1 coal make 2 steel.\n- Compressor: makes steel '
         'plates. In mass mode it turns materials with a mass value into "mass" and presses it into compressed '
         'blocks and mass pellets.\n- Electrolyzer: splits water into hydrogen and oxygen. Carry the hydrogen to the stage 2 Particle Accelerator in a Fluid Canister.\n'
         '- Hydrogen Fuel Cell: makes power from hydrogen and oxygen, returning 60% of the power the Electrolyzer put in (up to 240 FE/t).\n'
         '- Peltier Cooler: freezes water with power. Compress the ice into packed and blue ice.\n- Archive Terminal: '
         'copies ruin records onto data cards and restores degraded parts. It also decodes crystal memories.'),
    ]),
    ('stage2', 'singulo:superconducting_coil', '段階2 極低温', 'Stage 2: Cryogenic', [
        ('flow', '段階2の流れ',
         'この段階でやること:\n'
         '1. 精密組立台を作る。段階3から先の部品と装置は、ほとんどここで作る。\n'
         '2. 粒子加速器でミュオン束を作り、触媒反応器でミュオン触媒（ティア2）を作る。\n'
         '3. ミュオン触媒核融合炉（4 kFE/t）を作り、ミュオン触媒と水で電力を増やす。新品の触媒1個で10分動く（使いかけは残寿命の半分）。\n'
         '4. 冷却塔を高さ10以上にし、圧縮機で深層岩を砕いて出るヘリウムを液化する。\n'
         '5. 地表観測拠点の保管庫で探索コンパスを調整し、研究棟を探す。量子データ片と冷却原子トラップを集める（奥の残響の番人を倒すと保管庫が開く）。\n'
         '6. 量子演算モジュールと液体ヘリウムがそろえば段階3。\n'
         '今のうちに: アンドロイド組立ステーションで護衛を作れる（アンドロイドの章）。隕石クレーター（隕鉄・星屑）と氷山（青氷）も探しておく。\n'
         'つまずきやすい所:\n'
         '・液体ヘリウムができない: 冷却塔の高さが10未満だと作れない。\n'
         '・冷却塔が遅い: 液体窒素は1 mBに250 FEかかる。核融合炉ができるまでは、熱電発電機を増やす（電力が足りなくても止まらず、遅くなるだけ）。\n'
         '・触媒が使えない・すぐ減る: 装置より1つ下のティアは遅く減りも速く、2つ下は使えない。',
         'Stage 2 at a glance',
         'What to do:\n'
         '1. Build a Precision Assembler. Almost every part and machine from stage 3 on is made there.\n'
         '2. Make muon bundles in the Particle Accelerator and muon catalysts (tier 2) in the Catalytic Reactor.\n'
         '3. Build a Muon-Catalyzed Fusion Reactor (4 kFE/t) and run it on muon catalysts and water. A fresh catalyst lasts 10 minutes (a used one for half its remaining lifetime).\n'
         '4. Raise the cooling tower to height 10 or more, and liquefy the helium you get by crushing deepslate in the C'
         'ompressor.\n'
         "5. Tune the Explorer's Compass at the Surface Observation Post's cache and find the Research Building. Collect"
         ' quantum data fragments and cold atom traps (beat the Echo Sentinel at the back to open the cache).\n'
         '6. With a Quantum Computing Module and liquid helium, you are in stage 3.\n'
         'While you are here: the Android Assembly Station can print guards (see the Androids chapter). Look for meteor '
         'craters (meteoric iron, stardust) and icebergs (blue ice) too.\n'
         'Common sticking points:\n'
         '- No liquid helium: the tower must be at least 10 high.\n'
         '- The cooling tower is slow: liquid nitrogen costs 250 FE per mB. Add Thermoelectric Generators until the fusion reactor is running '
         '(short of power it slows down instead of stopping).\n'
         "- A catalyst won't work or wears fast: one tier below the machine is slower and wears faster; two below cannot"
         ' be used.'),
        ('tower', '極低温冷却塔',
         '5×5 の土台から、くびれた双曲面の塔を高さ7〜15で建てます。中心の列は熱交換コア、てっぺんは通気格子でふさぎます。'
         'コントローラは手前の面の下から2段目の中央。高いほど速く、高さ10以上で液体ヘリウムも作れます。\n'
         '何も入れなくても空気から液体窒素を作ります。銅・レッドストーン・鋼板・基板・制御ユニットと液体窒素で超伝導コイルを作ります。\n'
         '熱交換コアと冷却帯は青氷の代わりに氷塊3個でも作れます。高い塔ほど速く作れますが、そのぶん毎tickの電力も増えます'
         '（液体窒素は塔の高さによらず1 mBに250 FE。材料のある加工が窒素より先に動きます）。\n'
         '液体窒素は、空の冷媒カートリッジでコントローラを右クリックすると詰められます（冷却塔で加工しても詰まる）。'
         '冷媒カートリッジで装置を右クリックすると注げるので、パイプがなくても運べます。',
         'Cryogenic Cooling Tower',
         'Build a waisted hyperboloid tower 7–15 high on a 5×5 base. The middle column is heat exchange cores '
         'and the top is closed with a vent grate. The controller goes in the middle of the front face, second '
         'layer from the bottom. Taller towers are faster, and at 10 or more they also make liquid helium.\nIt '
         'makes liquid nitrogen from the air with no inputs. Superconducting coils are made from copper, '
         'redstone, steel plates, substrates, a control unit and liquid nitrogen.\nHeat exchange cores and '
         'coolant bands can use 3 packed ice instead of blue ice. Taller towers work faster but draw more power per tick '
         '(liquid nitrogen costs 250 FE per mB at any height; recipes with inputs run before nitrogen).\n'
         'Right-click the controller with an empty coolant cartridge to fill it with liquid nitrogen (processing it in the tower works too). '
         'Right-click a machine with a filled cartridge to pour it in, so no pipes are needed.'),
        ('accelerator', '粒子加速器とミュオン触媒',
         '加速管と収束磁石で正方形のリング（一辺8〜32）を作り、コントローラをリングに接して置きます。四隅は収束磁石、'
         '収束磁石はリングの1/4以上。水素からミュオン束を作り、ごく稀に磁気単極子が生まれます。\n'
         '触媒反応器でミュオン束からミュオン触媒（ティア2）を作ります。宇宙線ミュオン収集器を高所に置けば、電力なしでもミュオン束が少しずつ集まります。\n'
         '一辺16以上のリングなら、入力に亜鉛とビスマスのインゴットを入れると、原子核が融合して新元素ニホニウムができます。'
         '亜鉛は凝灰岩を圧縮機で砕いて、ビスマスは触媒反応器で粗銅を精錬した副産物として手に入ります。'
         'ニホニウム2個でインゴット、インゴットを圧縮機で板にし、精密組立台でニホニウム筐体を作ります。段階4〜5の装置や、終盤の耐圧・遮蔽部品に使います。'
         'ビスマスと炭素粉を入れると、ビスマスの原子核から陽子がはぎ取られて金になります（リングの大きさは問わない）。',
         'Particle Accelerator and muon catalyst',
         'Build a square ring (8–32 wide) of accelerator tubes and focusing magnets and place the controller '
         'against the ring. The corners must be focusing magnets, and magnets must make up at least a quarter '
         'of the ring. It makes muon bundles from hydrogen, and very rarely a magnetic monopole.\nThe Catalytic '
         'Reactor turns muon bundles into muon catalysts (tier 2). A Cosmic Muon Collector placed high up '
         'slowly gathers muon bundles without power.\n'
         'With a ring of side 16 or more, put zinc and bismuth ingots in the inputs to fuse their nuclei into the new element nihonium. '
         'Zinc comes from crushing tuff in the Compressor, and bismuth is a by-product of refining raw copper in the Catalytic Reactor. '
         'Two nihonium make an ingot; press ingots into plates and build nihonium frames in the Precision Assembler. '
         'Advanced stage 4 and 5 machines are built on them. Bismuth with carbon dust turns into gold as the carbon nuclei strip protons from the '
         'bismuth (any ring size).'),
        ('others', 'そのほかの段階2の装置',
         '・精密組立台: 段階3以降の部品と装置はほぼすべてここで作ります。\n'
         '・ミュオン触媒核融合炉: ミュオン触媒と水で発電（4 kFE/t）。新品の触媒1個で10分（使いかけは残寿命の半分）動き、失活した触媒が出る（触媒反応器でミュオン束に戻せる）。'
         '水はバケツで右クリックしても入る。\n'
         '・極低温タービン: 液体窒素に詰めた電力を取り戻す（溶岩の横で9割、最大4.5 kFE/t）。電力を貯めて運ぶための装置で、電力は増えない。'
         '冷媒カートリッジを入口に入れると、液体窒素を空けて空のカートリッジを出す。\n'
         '・SMESセル: 大量の電力をためる。\n'
         '・ワールドライン・アンカー（小）: 触媒を入れると周りのチャンクを読み込み続ける。\n'
         '・単極子アップグレード: 磁気単極子から作り、装置の単極子スロットに挿すと速度×2・電力効率+50%。',
         'Other stage 2 devices',
         '- Precision Assembler: makes almost every part and machine from stage 3 on.\n- Muon-Catalyzed Fusion Reactor: '
         'generates power from muon catalysts and water (4 kFE/t). A fresh catalyst lasts 10 minutes (a used one for half its remaining lifetime) and comes out spent (the Catalytic '
         'Reactor turns spent ones back into muon bundles). Water can be poured in with a bucket.\n- Cryogenic Turbine: recovers the power '
         'stored in liquid nitrogen (90% next to lava, up to 4.5 kFE/t). It stores and moves power; it does not create any. Coolant '
         'cartridges in its input slot are emptied into the tank and come out empty.\n- SMES Cell: stores a large amount of '
         'power.\n- Small Worldline Anchor: keeps the surrounding chunks loaded while it has a catalyst.\n- '
         "Monopole Upgrade: made from a magnetic monopole; in a machine's monopole slot it gives ×2 speed and "
         '+50% power efficiency.'),
    ]),
    ('stage3', 'singulo:quantum_computing_module', '段階3 量子', 'Stage 3: Quantum', [
        ('flow', '段階3の流れ',
         'この段階でやること:\n'
         '1. 量子もつれ合成器とレーザー冷却器を作る。\n'
         '2. 量子熱機関で発電する（BE凝縮触媒で 200 kFE/t。作るまではミュオン触媒で25%の仮運転）。\n'
         '3. レーザー冷却器でBE凝縮触媒（ティア3）と光格子基板を作る。\n'
         '4. 深海圧力収集器を早めに置き、高圧結晶を貯めておく（あとで殻とアンビルに約30個）。\n'
         '5. 研究棟の保管庫で探索コンパスを調整し、封鎖培養施設を探す。時間結晶の種と培養データを集める（奥の重力の澱を倒すと保管庫が開く）。同じ調整で地下アンドロイド研究所も探せる。\n'
         '6. 時間結晶の種と培養データがそろったら、段階4の時間結晶育成槽へ進む。\n'
         'つまずきやすい所:\n'
         '・封鎖培養施設がつらい: 極低温区画と強い警備機がいる。防具と回復をそろえ、護衛のアンドロイドを連れていく。\n'
         '・重力の澱に浮かされる: 落下が痛いので、重力ブーツや衝撃発電パネルが助けになる。',
         'Stage 3 at a glance',
         'What to do:\n'
         '1. Build an Entanglement Synthesizer and a Laser Cooler.\n'
         '2. Generate power with the Quantum Heat Engine (200 kFE/t on BE catalysts; until you have them, it test-runs a'
         't 25% on muon catalysts).\n'
         '3. Make BE condensate catalysts (tier 3) and optical lattice substrates in the Laser Cooler.\n'
         '4. Place Deep-Sea Pressure Collectors early and stockpile high-pressure crystals (shells and anvils need about'
         ' 30 later).\n'
         "5. Tune the compass at the Research Building's cache and find the Culture Facility. Collect time crystal seeds"
         ' and culture data (beat the Gravity Remnant at the back to open the cache). The same tuning also finds the Und'
         'erground Android Laboratory.\n'
         '6. With seeds and culture data, move on to the Time Crystal Incubator of stage 4.\n'
         'Common sticking points:\n'
         '- The Culture Facility is tough: it has a cryogenic section and strong security units. Bring armor, healing an'
         'd your guard androids.\n'
         '- The Gravity Remnant throws you up: falls hurt, so Gravity Boots or Impact Generators help.'),
        ('quantum', '量子の装置',
         '・量子もつれ合成器: 量子もつれ素子を2個1組で作る。\n'
         '・レーザー冷却器: 冷却原子・光格子基板・BE凝縮触媒（ティア3）を作る。冷却原子トラップの復元もここ。\n'
         '・量子熱機関: 触媒で動く発電機。BE凝縮触媒で 200 kFE/t。1段下のミュオン触媒でも定格の25%（50 kFE/t）で仮運転でき、'
         'BE凝縮触媒を作るまでのつなぎになる。蓄電がいっぱいの間は待機して触媒を減らさない。',
         'Quantum machines',
         '- Entanglement Synthesizer: entangled elements, two at a time.\n- Laser Cooler: cold atoms, lattices, BE condensate catalyst.\n'
         '- Quantum Heat Engine: catalyst-driven generator, 200 kFE/t on BE catalysts; a muon catalyst runs it at 25% (50 kFE/t) as a stopgap. '
         'It waits without wearing the catalyst while its storage is full.'),
        ('tools', '道具と探索',
         '・慣性制御ガントレット: 右クリック長押しで小さなモブを浮かせる・引き寄せる。電力で動き、電力を持つブロックにスニーク＋右クリックで充電。\n'
         '・慣性スタビライザー: 半径16の中で爆発がブロックを壊さない。\n'
         '・重力波検出器: 最終実験施設（重力異常点）のおおよその方角と距離帯を示す。\n'
         '・ニュートリノ・スキャナー: 地形越しに遺構と鉱石の輪郭を10秒間見せる。はじめは鉄やレッドストーンまでで、'
         '感度モジュール（Mk2: 金・ダイヤモンド・エメラルド、Mk3: 古代の残骸）を使うと深い層の鉱石も映る。観測所にも使える。',
         'Tools and exploration',
         '- Inertial Control Gauntlet: hold right-click to levitate or pull small mobs. It runs on power; '
         'sneak-right-click a block that holds power to charge it.\n- Inertial Stabilizer: stops explosions '
         'from breaking blocks within a radius of 16.\n- Gravitational Wave Detector: shows the rough direction '
         'and distance band of the Final Lab (gravity anomaly).\n- Neutrino Scanner: shows the outlines of '
         'ruins and ores through terrain for 10 seconds. At first it shows up to iron and redstone; '
         'sensitivity modules (Mk2: gold, diamond, emerald; Mk3: ancient debris) reveal deeper ores. It also '
         'works on the observatory.'),
    ]),
    ('stage4', 'singulo:time_crystal_catalyst', '段階4 時間', 'Stage 4: Temporal', [
        ('flow', '段階4の流れ',
         'この段階でやること:\n'
         '1. 時間結晶育成槽で時間結晶触媒を育てる（要求の80%以上の電力なら純度が落ちない。設備用と運転用で取り合うので、2台あると楽）。\n'
         '2. C空洞でエキゾチック物質とアクシオン凝縮体を作る。\n'
         '3. 圧縮機の質量モードで圧縮ブロックを作り、縮退熱炉（5 MFE/t）で発電する。\n'
         '4. 縮退圧縮炉で縮退物質殻を7個作る（炉殻用6・特異点の種用1）。\n'
         '5. 封鎖培養施設の保管庫で探索コンパスを調整し、最終実験施設を探す。封印コンソールで目覚めるホライズン・ウォーデンを倒し、「休眠した特異点の種」を手に入れる。\n'
         '6. 種を手に入れたら段階5。特異点封入台で目覚めさせ、Pリアクターに入れる。\n'
         'つまずきやすい所:\n'
         '・時間結晶が足りない: 時間結晶モジュール（設備用）と触媒（運転用）の両方に使う。育成槽を増やす。\n'
         '・最終実験施設が見つからない: いちばん深い地下（Y−56〜−46あたり）。重力波検出器でおおよその方角と距離が分かる。',
         'Stage 4 at a glance',
         'What to do:\n'
         '1. Grow time crystal catalysts in the Time Crystal Incubator (at 80% or more of the required power the purity '
         'does not drop; facilities and running machines compete for crystals, so two incubators help).\n'
         '2. Make exotic matter and axion condensate in the C-Cavity.\n'
         "3. Make compressed blocks in the Compressor's mass mode and generate power with the Degenerate Heat Furnace (5"
         ' MFE/t).\n'
         '4. Make seven degenerate matter shells in the Degenerate Compactor (six for reactor shells, one for the singul'
         'arity seed).\n'
         "5. Tune the compass at the Culture Facility's cache and find the Final Lab. Beat the Horizon Warden that wakes"
         ' at the Seal Console to get the Dormant Singularity Seed.\n'
         '6. With the seed, you are in stage 5: awaken it in the Singularity Encapsulator and put it in the P-Reactor.\n'
         'Common sticking points:\n'
         '- Not enough time crystals: they go into both time crystal modules (facilities) and catalysts (running). Add i'
         'ncubators.\n'
         "- Can't find the Final Lab: it is the deepest ruin (around Y -56 to -46). The Gravitational Wave Detector show"
         's its rough direction and distance.'),
        ('compression', '圧縮の連鎖',
         '圧縮機の質量モードで質量値のある素材を「質量」に変え、圧縮ブロックLv1→Lv2→Lv3 と固めます。'
         '3種類以上の元ブロックから作った Lv1 は「混成」になり、8個で Lv2 にできます。金属だけを固めた金属圧縮ブロックもあります。\n'
         '縮退圧縮炉（5×5×5 の閉じたプレス）で、縮退物質殻やリアクターの炉殻を作ります。',
         'Compression chain',
         'In mass mode the compressor turns materials with a mass value into "mass" and presses it into '
         'compressed blocks, Lv1 → Lv2 → Lv3. Lv1 made from three or more kinds of source blocks is "mixed", '
         'and only 8 are needed for Lv2. There are also compressed metal blocks made of metals only.\nThe '
         'Degenerate Compactor (a closed 5×5×5 press) makes degenerate matter shells and reactor shell blocks.'),
        ('time', '時間結晶とエキゾチック物質',
         '時間結晶育成槽は、実際に動いた時間で触媒を育てます（12分）。要求の80%（24 kFE/t）以上の電力なら純度は落ちません。'
         'それを割った時間の割合だけ純度が下がり、寿命も短くなります。\n'
         '設備（炉心制御装置・特異点封入台・シールド発生塔コアなど）には、時間結晶を精密組立台で「時間結晶モジュール」に加工して使います。'
         '触媒枠の時間結晶から新品の寿命の2/3（既定の設定で30分）を切り出し、残り（既定で15分）の触媒はエキゾチック物質の加工やコアの材料に使えます。'
         '残りの寿命がそれに満たない触媒（使いかけ・純度の低いもの）からは作れません。寿命の設定やプリセットを変えると、切り出す量も比例して変わります。'
         '設備用と運転用の取り合いになるので、育成槽は2台あると楽です。\n'
         'C空洞（5×5×5 の真空容器、天面と底面の内側が鏡面）は、時間結晶触媒を使ってエキゾチック物質とアクシオン凝縮体を作ります。',
         'Time crystals and exotic matter',
         'The incubator grows time crystals over 12 minutes of real running time. At 80% of the requested power (24 kFE/t) or more purity is kept; '
         'time spent below that lowers purity and lifetime.\n'
         'Equipment (core controller, singularity encapsulator, shield tower core and more) takes Time Crystal Modules: the precision assembler cuts '
         'two thirds of a fresh crystal\'s life (30 minutes with default settings) from the crystal in its catalyst slot, and the rest (15 minutes by default) '
         'stays usable for exotic matter or as a core ingredient. Crystals with less life left than that (used or low-purity ones) cannot be cut; lifetime '
         'settings and presets scale the amount. Two incubators keep equipment and running needs from competing.\n'
         'The C-Cavity (5×5×5, mirror top and bottom) makes exotic matter.'),
        ('power', '大型発電と自動化',
         '・縮退熱炉（5×5×7）: 圧縮ブロックLv2 を押しつぶして 5 MFE/t。時間結晶触媒を使う（BE凝縮触媒でも25%の 1.25 MFE/t で仮運転できる）。'
         '電力は搬入出ポートから自動で出る。蓄電がいっぱいの間は待機し、燃料も触媒も減らさない。\n'
         '・殻と炉殻: 縮退物質殻1個から炉殻24枚。点火までに要る殻は炉殻用6個＋特異点の種用1個。岩石と金属をまとめて集めておくと楽です。\n'
         '・自動探査機ステーション: 発見済みの地表観測拠点へ探査機を飛ばし、回収物を半分の量で持ち帰る。\n'
         '・トポロジカル導線と SMESモジュールで大電力を運び、ためる。',
         'Big power and automation',
         '- Degenerate Furnace (5×5×7): crushes compressed blocks Lv2 for 5 MFE/t. It uses a time crystal '
         'catalyst (a BE condensate catalyst runs it at 25%, 1.25 MFE/t, as a stopgap). Power leaves through '
         'its I/O ports automatically; while its storage is full it waits without using fuel or catalyst.\n- '
         'Shells: one degenerate matter shell makes 24 reactor shell blocks. Ignition needs 6 shells for the '
         'plating plus 1 for the singularity seed. Gathering rock and metal in bulk helps.\n- Probe Station: '
         'sends probes to discovered Observation Posts and brings back half the loot.\n- Topological wire and '
         'SMES modules carry and store large amounts of power.'),
    ]),
    ('stage5', 'singulo:singularity_core', '段階5 特異点', 'Stage 5: Singularity', [
        ('flow', '段階5の流れ',
         'この段階でやること:\n'
         '1. 炉殻ブロックなどPリアクターの部品をそろえ、ホロ投影機を炉心制御装置に使って、形を見ながら組み立てる。\n'
         '2. 点火のため、50 GFE を10秒以内に送れるよう、SMES とホライズン・バスで配線する（受電の目安は平均 250 MFE/t）。\n'
         '3. 特異点の種を入れて点火する。最初は回収運転でH凝縮体を集める（最初のシンギュラリティ・コアに6個）。\n'
         '4. 発電運転に切り替え、質量ペレットを自動で送り込む。\n'
         '5. シンギュラリティ・コアで特異点技術（シールド・Tシリンダー・ワームホール）を動かし、最後の目標「創世炉」へ。\n'
         'つまずきやすい所:\n'
         '・点火に失敗する: 送電経路の上限は、その経路で容量が最も小さいケーブルで決まる。画面の受電の速さと届く見込みを見て配線を直す。\n'
         '・炉に吸い込まれる: リングの内側は逃げられない強い引力で、黒い球に触れたものは必ず消える。近くで作業するときは気をつける。',
         'Stage 5 at a glance',
         'What to do:\n'
         '1. Gather the P-Reactor parts such as reactor shell blocks, and build it with the Holo Projector used on the C'
         'ore Controller.\n'
         '2. For ignition, wire SMES storage and a Horizon Bus so that 50 GFE arrives within 10 seconds (about 250 MFE/t'
         ' on average).\n'
         '3. Insert the singularity seed and ignite. Start in harvest mode to collect H condensate (six for the first Si'
         'ngularity Core).\n'
         '4. Switch to generation mode and feed mass pellets automatically.\n'
         '5. Use Singularity Cores for singularity technology (shields, the T-Cylinder, wormholes) and head for the fina'
         'l goal, the Genesis Engine.\n'
         'Common sticking points:\n'
         '- Ignition fails: a power route is limited by its lowest-capacity cable. Check the incoming rate and the estimate o'
         'n the screen and fix the wiring.\n'
         '- Getting pulled in: inside the ring the pull is inescapable, and anything touching the black sphere is destro'
         'yed. Take care when working nearby.'),
        ('reactor', 'Pリアクターを組む',
         '炉心の中心を通る3つの直交面に、半径6の円環を途切れずにつなげて作ります（各48ブロック）。3本が交わる6点はジャイロ駆動部、'
         '各円環の斜め45°の点（計12）は炉心安定化コイル、残りは炉殻ブロック（抽出ポートと警報器に交換可）、中心の 3×3×3 は空気。炉心制御装置は中心の5ブロック下です。'
         'ホロ投影機を炉心制御装置に使えば、全体が浮かび上がります。',
         'Building the reactor',
         'Build three continuous rings of radius 6 (48 blocks each) on the three orthogonal planes through the '
         'core centre. The six points where they cross are gyro drives, the 45° points of each ring (twelve in '
         'all) are reactor stabilizer coils, and the rest is reactor shell (which can be swapped for '
         'extraction ports and mass alarms). The central 3×3×3 is air. The core controller sits 5 blocks below '
         'the centre. Use the Holo Projector on the core controller to show the whole structure.'),
        ('ignite', '点火と運転',
         '種を入れて「点火」を押し、10秒以内に 50 GFE をポートかコントローラに注ぎます（ホライズン・バスを推奨）。'
         '画面には点火に要る受電（既定は平均 250 MFE/t）が出ます。点火中は受電の速さと届く見込みが出るので、足りなければ配線を見直します。'
         '送電経路の上限は、その経路で容量が最も小さいケーブルで決まり、トポロジカル導線の1経路では10秒に20 GFEしか届きません。\n'
         '点火した炉心は「回収運転」で始まります。質量を480以下に保ち、Hコレクターを入れておけばH凝縮体が5分に1個できます。'
         '最初のシンギュラリティ・コアには6個（直接4・アノマリー・サンプルの復元2）要るので、約30分そのまま回し、集まったら回収運転を切って発電運転にします。'
         '育ちすぎた炉心も、回収運転にすると毎秒2ずつ質量を放出して戻ります。\n'
         '燃料は質量ペレット。降着効率はスピンで決まり（5.7〜42.3%）、炉心質量5000・最大スピンで約 2.1 GFE/t。\n'
         'リングの外（半径24まで）は弱い引力、リングの内側は逃げられない強い引力で、黒い球に触れたものはどんなに頑丈でも必ず消えます。半径4は防具を無視する潮汐帯です。引力帯の中は時間が遅れ、装置が×0.8、触媒の減りが×0.5になります。',
         'Ignition and operation',
         'Insert the seed, press Ignite and deliver 50 GFE within 10 s through a port or the controller (a '
         'horizon bus is recommended). The screen shows the intake needed (250 MFE/t on average by default). '
         'While igniting it shows the intake and what will arrive, so rework the wiring if it falls short; a '
         'power route is capped by its lowest-capacity cable, so a single topological-wire route delivers at most 20 GFE in 10 '
         's.\nA new core starts in harvest mode: it keeps mass at 480 or below, so H-Condensate forms every 5 '
         'minutes with a Hawking Collector. The first Singularity Core needs six (four directly, two to '
         'restore the anomaly sample), so run it for about 30 minutes, then switch harvest mode off for power. '
         'Harvest mode also sheds 2 mass per second from an overgrown core.\nThe fuel is mass pellets. '
         'Accretion efficiency depends on spin (5.7–42.3%); a core of mass 5000 at full spin gives about 2.1 '
         'GFE/t.\nOutside the ring (out to radius 24) a weak pull acts, inside the ring a pull you cannot '
         'escape, and anything that touches the black sphere is destroyed no matter how tough. Radius 4 is a '
         'tidal zone that ignores armour. Time runs slower in the pull zone: machines at ×0.8, catalyst wear '
         'at ×0.5.'),
        ('byproducts', '副産物とシンギュラリティ・コア',
         'ジェット・コレクター・Hコレクター・エルゴスフィア・リングを入れると、運用モードに応じて'
         'ジェット凝縮体・H凝縮体・エキゾチック物質が生まれます。H凝縮体などからシンギュラリティ・コア（ティア5の触媒）を作れます。',
         'By-products and the Singularity Core',
         'With a Jet Collector, a Hawking Collector or an Ergosphere Ring installed, the reactor produces jet '
         'condensate, H-Condensate or exotic matter depending on how it is running. H-Condensate and other '
         'materials make the Singularity Core (the tier 5 catalyst).'),
        ('endgame', 'その先',
         'シンギュラリティ・コアと大電力で「特異点技術」が動かせます（特異点技術の章）。'
         'イベントホライズン・シールドで拠点を守り、Tシリンダーで時間を速め、ワームホールで遠くの拠点をつなぎましょう。'
         '人工星核を作れば、もうネザースターを探さなくて済みます。\n'
         '腕に自信があれば、特異点封入台で「覚醒の記録核」を作り、最終実験施設の封印コンソールで使ってみてください。'
         '体力2.5倍・攻撃1.5倍の覚醒したホライズン・ウォーデンが目覚めます。勝てばオーバークロック・チップなどが手に入り、何度でも挑めます。\n'
         '研究棟と封鎖培養施設の番人も、「覚醒の残響核」「覚醒の重力核」を番人の封印核に使えば、覚醒した姿と再戦できます。\n'
         '最終実験施設を攻略したら、探索コンパスを調整して「沈んだ炉心区画」を探しましょう。止まった二基目の炉に、エルゴ球の大蛇が棲んでいます。\n'
         '覚醒した番人たちは、倒れるときに記録の断章を落とすといいます。断章がすべてそろったら、何が読めるでしょうか。',
         'Beyond',
         'With cores and power you can run the late-game devices: shields, T-Cylinders and wormholes. Artificial star cores end the hunt for nether stars.\n'
         'For a real fight, make an Awakening Record Core in the singularity encapsulator and use it on the Final Lab\'s seal console: the Awakened Horizon Warden '
         'has 2.5× health and 1.5× damage. Beat it for overclock chips and more, as often as you like.\n'
         'The Research Building and Culture Facility guardians can be refought too: use an Awakened Echo Core or Awakened Gravity Core on their guardian core.\n'
         'Once the Final Lab is cleared, tune your explorer compass to find a Sunken Reactor Sector: the Ergosphere Leviathan lives in its dead second reactor.\n'
         'They say awakened guardians drop chapters of records as they fall. What might you read once you have them all?'),
    ]),
    ('multiblocks', 'singulo:holo_projector', 'マルチブロック', 'Multiblocks', [
        ('projector', 'ホロ投影機の使い方',
         '1. コントローラを置く（正面が自分の方を向く。形は奥へ伸びる）。\n'
         '2. ホロ投影機でコントローラを右クリックすると、正しい形が投影され、必要な数と足りない数がチャットに出る。\n'
         '3. 水色の枠に見本のブロックを置き、赤い枠のブロックを取り除く。全部そろうと投影が消え、継ぎ目に光が走って起動する。\n'
         '4. 大きさを選べる装置（冷却塔・加速器）は、スニークして使うと大きさが切り替わる。\n'
         'クリエイティブでは /singulo build <名前> [大きさ] で一発で組み立てられます。',
         'Using the Holo Projector',
         '1. Place the controller (its front faces you and the structure extends behind it).\n2. Right-click '
         'the controller with the Holo Projector. The correct shape is projected, and the required and missing '
         'counts appear in chat.\n3. Place the shown blocks in the cyan boxes and remove the blocks in the red '
         'ones. When everything is in place the projection disappears, light runs along the seams and the '
         'machine starts.\n4. For machines with a choice of size (the cooling tower and the accelerator), '
         'sneak-use the projector to switch sizes.\nIn creative mode, /singulo build <name> [size] builds it in '
         'one go.'),
        ('list', 'マルチブロック一覧',
         '・極低温冷却塔（5×5、高さ7〜15）: cooling_tower\n・粒子加速器（リング8〜32）: particle_accelerator\n'
         '・縮退圧縮炉（5×5×5）: degenerate_compactor\n・C空洞（5×5×5）: casimir_cavity\n'
         '・縮退熱炉（5×5×7）: degenerate_furnace\n・Pリアクター（13×13×13）: penrose_reactor\n'
         '・イベントホライズン・シールド発生塔（5×5、高さ9）: event_horizon_shield\n・Tシリンダー（5×5×9）: tipler_cylinder\n'
         '・ワームホール生成器（5×5×5の球）: wormhole_generator',
         'Multiblock list',
         '- Cryogenic Cooling Tower (5×5, 7–15 high): cooling_tower\n- Particle Accelerator (ring 8–32 wide): '
         'particle_accelerator\n- Degenerate Compactor (5×5×5): degenerate_compactor\n- C-Cavity (5×5×5): '
         'casimir_cavity\n- Degenerate Furnace (5×5×7): degenerate_furnace\n- P-Reactor (13×13×13): '
         'penrose_reactor\n- Event Horizon Shield Tower (5×5, 9 high): event_horizon_shield\n- T-Cylinder '
         '(5×5×9): tipler_cylinder\n- Wormhole Generator (5×5×5 sphere): wormhole_generator'),
    ]),
    ('catalysts', 'singulo:muon_catalyst', '触媒', 'Catalysts', [
        ('tiers', 'ティアと効き方',
         'ミュオン（2）・BE凝縮（3）・時間結晶（4）・シンギュラリティ・コア（5）。\n'
         '・装置と同じティア: 標準\n・1つ上: 速度+25%、減り−20%（2つ上まで重なる）\n・1つ下: 速度0.5倍、減り2倍（発電機は出力が定格の25%）\n・2つ下: 使えない\n'
         '残りが減るほど必要電力が増え（最大1.6倍）、電力が80%を割ると不完全反応で減りが1.5倍になります。\n'
         'この決まりは触媒枠で「運転」する装置（アンカー・スタビライザー・発電機・シールドなど）のものです。加工装置のレシピに材料として書かれた触媒は、'
         '個数か寿命を決まった分だけ使い、速さは変わりません（例: BE凝縮触媒の加工はミュオン触媒1個で40秒）。',
         'Tiers and effects',
         'Muon (2), BE condensate (3), time crystal (4), Singularity Core (5).\n- Same tier as the machine: '
         'standard\n- One tier higher: speed +25%, wear −20% (stacks up to two tiers higher)\n- One tier lower: '
         '×0.5 speed, ×2 wear (generators run at 25% of their rated output)\n- Two tiers lower: unusable\nAs a '
         'catalyst wears down, the power it needs rises (up to ×1.6); below 80% power, incomplete reactions '
         'make it wear ×1.5 faster.\nThese rules apply to devices that run on a catalyst slot (anchors, '
         'stabilizers, generators, shields and so on). When a processing recipe lists a catalyst as an '
         'ingredient, it uses a fixed count or amount of life and the speed does not change (for example, a BE '
         'condensate catalyst takes one muon catalyst and 40 seconds).'),
        ('slots', '触媒スロットとリサイクル',
         '触媒を使う装置には、触媒だけが入る専用スロットがあります（画面で触媒のマークが付いた枠）。ほかのアイテムは入りません。\n'
         '使い切った触媒は失活触媒になります。触媒反応器で、1ティア下の触媒の材料として約25%を回収できます。',
         'Catalyst slots and recycling',
         'Machines that use catalysts have a dedicated slot that accepts only catalysts (the slot marked with '
         'a catalyst icon). Nothing else fits.\nA used-up catalyst becomes a spent catalyst. In the Catalytic '
         'Reactor, about 25% of it can be recovered as material for the catalyst one tier lower.'),
        ('time', '時間の場',
         'Pリアクターの引力帯では時間が遅れ、触媒の減りが×0.5（装置は×0.8）。Tシリンダーの範囲では逆に×2。'
         '重なると打ち消し合います。触媒倉庫を炉の近くに置くと長持ちします。',
         'Time fields',
         "In a P-Reactor's pull zone time slows down: catalysts wear at ×0.5 (machines run at ×0.8). Inside a "
         'T-Cylinder field it is the reverse, ×2. The two cancel out where they overlap. Keeping catalyst '
         'storage near the reactor makes them last longer.'),
    ]),
    ('power', 'singulo:superconducting_cable', '電力と輸送', 'Power and Logistics', [
        ('cables', 'ケーブル',
         '銅導線（2 kFE/t、損失あり）→ 超伝導ケーブル（1 MFE/t）→ トポロジカル導線（100 MFE/t）→ ホライズン・バス（上限なし。ほかの mod の装置へは1台あたり約2.1 GFE/t まで）。'
         '送電量は、その経路で容量が最も小さいケーブルで決まり、別の分岐のケーブルには制限されません。同じケーブルを通る複数の送電は、1 tick の容量を共有します。\n受電する装置からケーブル経由で近い電源を先に使い、不足分を遠い電源で補います。同じ距離なら座標順です。近い電源が空の場合や、途中のケーブルの容量が足りない場合も、残りを別の電源が補います。ケーブルを切断すると、その配線の発光はすぐに消えます。',
         'Cables',
         'Copper wire (2 kFE/t, with losses) → superconducting cable (1 MFE/t) → topological wire (100 MFE/t) '
         '→ horizon bus (no limit; up to about 2.1 GFE/t per machine from other mods). Each route is limited by its lowest-capacity cable; unrelated branches do not lower that limit. '
         'Transfers sharing a cable share its per-tick capacity.\nSources nearer to the receiver along the cables supply power first; more distant sources cover the deficit. Equal distances use a stable coordinate order. An empty nearby source or a bottleneck on its route does not block the backup sources. Breaking a cable immediately clears the glow of that wiring.'),
        ('sides', '面ごとの設定',
         '通常の加工装置の画面右上の「面」ボタンで、6つの面それぞれを「無効・入力・出力・入出力」に切り替えられます。'
         '自動排出の全体スイッチは初期状態ではオフです。オンにすると、自動排出を許可した出力可能な面から隣接先へ搬出します。面にカーソルを合わせると隣接ブロック名が分かります。出力の面には、できたものを自動で押し出す（自動排出）設定もあります。マルチブロックは指定位置の搬入出ポートを使い、Pリアクターは抽出ポートを使います。'
         '2種類の気体を出す装置（電解槽など）は、気体ごとに出す面を決められます。画面右の電源ボタンで装置を止められ、'
         '電力だけで作れるもの（冷却塔の液体窒素など）がある装置には、それを作るかどうかのスイッチがあります。',
         'Side configuration',
         'Use the Sides button in an ordinary processing machine screen to set each face to off / input / output / both, with optional auto-eject. The master auto-eject switch defaults off; turn it on to push through output-capable faces with auto-eject enabled. Hover a face to see its adjacent block. Multiblocks use designated I/O ports; the P-Reactor uses Extraction Ports.'
         ' Machines that output two gases, such as the electrolyzer, have a tab for each gas. The power button stops a machine, and machines that can make something from power alone have a switch to skip those recipes.'),
        ('relocation', '移設レンチ',
         '移設レンチは鋼鉄インゴット3個・銅インゴット2個・基礎回路1個から作れます。スニーク＋右クリックで、加工装置・通常の発電機・SMES・触媒装置を回収できます。中身・液体・電力・進捗・面設定を保持し、再設置すると復元します。持ち物が満杯なら、その場に装置を1個落とします。\n'
         'マルチブロックはコントローラだけを回収し、周囲の部材は残ります。再設置後は形を組み直してください。Pリアクター・ワームホール装置・アンドロイド用設備などは対象外です。',
         'Relocation Wrench',
         'Craft a Relocation Wrench from 3 steel ingots, 2 copper ingots and a basic circuit. Sneak-use it to pack processing machines, conventional generators, SMES and catalyst devices. Items, fluids, energy, progress and side settings are restored on placement. A full inventory drops one packed device on the ground.\n'
         'For multiblocks only the controller is packed; the surrounding parts stay in place. Rebuild the structure after moving it. P-Reactors, wormhole devices and android facilities are not supported.'),
        ('wormhole', 'ワームホール',
         '唯一の無線化。生成器で一対の口を作り、60秒以内に固定化装置でエキゾチック物質と口の筐体を使って固定化し、片方を運んで置きます。'
         '口から8ブロック以内のワームホール・ポートは、向こう側の口の近くにある同じポート番号（0〜127、画面の7つの丸で決める）のポートとつながり、'
         '電力・アイテム・液体を直結します。1つの口で働けるポートは喉の大きさで 8・32・128 個まで（口に近い順）で、働いているポートは光ります。'
         '通せる量も喉の大きさで決まり、7×7 では上限がなくなります。口はエキゾチック物質で維持し、切れると閉じて休みます。'
         '初めて置いたときは5分間、燃料なしで開いています。',
         'Wormholes',
         'The only wireless link. Generate a pair of mouths, stabilize them within 60 s using exotic matter and a mouth casing, then carry one away and place it.'
         ' A port within 8 blocks of a mouth links to the port with the same number (0-127, set with the seven circles in its screen) near the twin mouth, joining power, items and fluids.'
         ' Each mouth runs 8, 32 or 128 ports depending on its throat size, nearest first, and active ports glow. Throughput also grows with the throat and is unlimited at 7x7.'
         ' Mouths run on exotic matter and close when it runs out; a newly placed mouth stays open for 5 minutes without fuel.'),
    ]),
    ('ruins', 'singulo:ruin_cache', '遺構と探索', 'Ruins', [
        ('list', '6つの遺構',
         '・地表観測拠点: 平原・砂漠などの地上に建つ、ただ1つの地上の遺構。白い警備ドローン。観測ログ・制御ユニット（7日で再生）。\n'
         '・研究棟: 浅い地下（Y−20〜4あたり）。警備機と、奥の記録保管室の残響の番人。冷却原子トラップ・量子データ片（7日）。\n'
         '・封鎖培養施設: 深い地下（Y−46〜−30あたり）。極低温コアと強い警備機、奥の隔離槽室の重力の澱。時間結晶の種・培養データ（14日）。\n'
         '・地下アンドロイド研究所: 中くらいの深さの地下（床は Y−38〜−26あたり）。封鎖培養施設と並ぶ分かれ道で、研究棟でコンパスを調整すると一緒に探せる'
         '（コンパスは地表まで突き抜けた主立坑を指す）。部屋の並びは毎回変わる。奥のAI中枢に統括AI「ファウンドリ」。精密アクチュエーター・アンドロイドキット（14日）。\n'
         '・最終実験施設: いちばん深い地下（Y−56〜−46あたり）の重力異常点。守護機ホライズン・ウォーデン。特異点の種・アノマリー・サンプル（14日）。\n'
         '・沈んだ炉心区画: 最終実験施設よりさらに深い地下（Y−60〜−54あたり）の、止まった二基目の炉。'
         '最終実験施設を攻略したあと、探索コンパスを調整すると探せる。エルゴ球の大蛇。エキゾチック物質・ジェット凝縮体・H凝縮体（14日）。',
         'The six ruins',
         '- Observation Post: the only ruin above ground, on plains, deserts and similar surfaces. White '
         'security drones. Observation logs and control units (regrow in 7 days).\n- Research Building: shallow '
         'underground (around Y −20 to 4). Security machines and the Echo Sentinel in the archive room at the '
         'back. Cold atom traps and quantum data fragments (7 days).\n- Culture Facility: deep underground '
         '(around Y −46 to −30). A cryogenic core, stronger security machines and the Gravity Remnant in the '
         'containment room at the back. Time crystal seeds and culture data (14 days).\n- Underground Android Laboratory: '
         'mid-depth underground (floors around Y −38 to −26). A branch beside the Culture Facility: tuning the compass at the Research Building '
         'lets it find both (it points to the main shaft, which breaks through to the surface). The room layout changes every time. '
         'The overseer AI Foundry waits in the AI core. Precision actuators and android kits (14 days).\n- Final Lab: a gravity '
         'anomaly deepest underground (around Y −56 to −46). The Horizon Warden guards it. Singularity seeds '
         'and anomaly samples (14 days).\n- Sunken Reactor Sector: a dead second reactor even deeper than the '
         'Final Lab (around Y −60 to −54). Tune the explorer compass after clearing the Final Lab to find it. '
         'The Ergosphere Leviathan. Exotic matter, jet condensate and H-Condensate (14 days).'),
        ('bosses', '遺構の番人',
         '研究棟・封鎖培養施設・最終実験施設の保管庫は、それぞれの番人を倒すまで力場で封鎖されています。中身が再生すると封鎖し直され、番人も戻ってきます。番人は部屋から挑戦者がいなくなっても3分は残り、それでも誰も戻らなければ元に'
         '戻ります（倒れて装備を取りに戻る猶予）。サーバー設定 bossHardMode をオンにすると、番人はもっと強くなります。倒したときの落とし物は床の封印核（ウォーデンは封印コンソール）の上に出て、経験値は部屋にいる挑戦者'
         'で等しく分けて直接入ります（修繕も効きます）。\n'
         '大勢で挑むと、番人は人数に合わせて強くなります（1人増えるごとに体力が5割増え、技の間も少し縮む。6人まで）。途中で加わっても体力の割合は保たれ、人が抜けても弱くはなりません。そのかわり、落とす物は戦いが始まったときの挑'
         '戦者の人数ぶん出ます（あとから加わった人のぶんは増えません）。大勢で攻撃しても、ほかの人の攻撃に邪魔されず全員の攻撃が通ります（同じ人の連打は、これまでどおり少し間を置かないと通りません）。\n'
         '・残響の番人（研究棟の記録保管室）: 部屋に入ると現れる警備ホログラム。宙を回りながら追いかける光弾を撃ち、しばらく見ていないと背後へ跳んで斬りつけてきます（跳ぶ前に光が集まる）。残響投影器は番人を守るだけでなく、一斉に'
         '光の槍も撃ちます（金色の狙いの線が出たら動くこと）。足元に光の陣が映ったら、柱が立つ前に陣の外へ。投影器が残っているほど攻撃が通りにくいので、先に壊すこと。体力が半分を切ると、1撃で消える分身を呼びます。投影器の光は本体'
         'にしか伸びません。番人の投影核を落とします。\n'
         '・重力の澱（封鎖培養施設の隔離槽室）: 封じ込め槽を破って出てくる黒い核。瓦礫の殻がある間は1撃ごとに殻が1つ剥がれる代わりにダメージが小さく、殻の瓦礫を撃ち出します。周りの相手を浮かせてから床へ叩きつけるので、重力ブー'
         'ツや衝撃発電パネルがあると楽です。体力が半分を切ると重力井戸ができ、核へ引き寄せられます（核に触れると傷つく）。井戸は引き寄せたあと力をため（光と音が前触れ）、近くの相手を吹き飛ばしてから、部屋の別の場所へ漂って移ります'
         '。縮退核を落とします。\n'
         '・ホライズン・ウォーデン（最終実験施設）: 中央の封印コンソールに触れると起動します。最後の姿で足元に出す特異点は、1秒の前触れのあとに引き寄せ始めるので、すぐ離れること。倒したあとは、覚醒の記録核をコンソールに使うと「'
         '覚醒した」個体と再戦できます（体力2.5倍・攻撃1.5倍・技の間が短く、早く本気になる）。やめて部屋を離れると記録核は戻ります。',
         'Ruin guardians',
         'The caches of the Research Building, Culture Facility and Final Lab stay sealed until you defeat their guardia'
         'n. When the loot regrows the cache reseals and the guardian returns. If every challenger leaves, a guardian wa'
         'its three minutes (time to come back after falling) and then resets. The server setting bossHardMode makes gua'
         'rdians stronger. Their drops appear on the guardian core in the floor (on the seal console for the Warden), an'
         'd their experience is split evenly among the challengers in the room and goes straight to them (Mending still '
         'works).\n'
         'Guardians grow stronger with the number of challengers: each extra player adds half of their health and shorte'
         'ns their attack gaps a little (up to six players). Joining mid-fight keeps the health fraction, and leaving ne'
         'ver weakens them. In return they drop their loot once per challenger present when the fight begins (late joine'
         "rs do not add more). Everyone's attacks land without blocking each other, though each player still needs the u"
         'sual short pause between hits.\n'
         '- Echo Sentinel (Research Building archive): a security hologram that orbits you firing homing bolts and blink'
         's behind you to strike when you stop watching it. The Echo Projectors in the corners shield it and fire light '
         'lances in volleys (move when the golden aim lines appear); step out of a projected circle before its pillar ri'
         'ses. Break the projectors first. Below half health it summons one-hit echoes; projector beams only reach the r'
         'eal one. Drops Sentinel Projection Cores.\n'
         '- Gravity Remnant (Culture Facility containment chamber): a black core that bursts from its tank. While its ru'
         'bble shell lasts it takes reduced damage and loses a piece per hit; it fires the rubble at you. It lifts nearb'
         'y foes and slams them down, so Gravity Boots or Impact Generators help. Below half health a gravity well drags'
         ' you toward the core. The well pulls, charges with a glow and a sound, bursts nearby foes outward, then drifts'
         ' the Remnant to another spot. Drops Degenerate Nuclei.\n'
         '- Horizon Warden (Final Lab): touch the central Seal Console to wake it. In its last phase it opens singularit'
         'ies at your feet; they warn for a second before they pull, so step away. After beating it, use an Awakening Re'
         'cord Core on the console for a rematch with the awakened warden (2.5× health, 1.5× damage, faster and earlier '
         'phases). Leave and the core is returned.'),
        ('foundry', '統括AIと覚醒した番人',
         '・統括AI「ファウンドリ」（地下アンドロイド研究所のAI中枢）: 中枢の中心に浮かぶ機械の頭脳で、自分では動かず施設の設備で戦います。壁の4つの製造ラインが動いている間は攻撃が通りにくい（1つにつき2割）ので、先に殴って'
         '壊すこと。ラインはときどき量産型のアンドロイドを送り出します。床に赤い印が出たらロボットアーム（印は少しの間追ってきて、止まってから降りてくる。つかまれると投げられる）、影の丸はプレス（すぐ外へ）、床の扇形の印は溶接レー'
         'ザー（床すれすれの光線が回ってくるので、跳んで越える）。床のコンベアは相手を流し、枠の灯が赤く光ったあと向きを変えます。体力が半分を切ると試作機（斧型・ビット型）を2体呼び、アームが2本になります。精密アクチュエーターと'
         '、挑戦した人それぞれに試作機の小型クリスタルメモリ（まだ持っていない型が先）を落とします。覚醒の製造核で再戦すると、本気になったとき壊したラインを印刷し直します。\n'
         '・覚醒した番人: 残響の番人と重力の澱も、倒したあとに部屋の床の封印核へ「覚醒の残響核」「覚醒の重力核」を使うと再戦できます（体力2.5倍・攻撃1.5倍・技の間が短く、体力65%で本気になる）。残響の番人は分身と光弾が増'
         'え、本気になると壊した投影器を2つまで映し直します。重力の澱は瓦礫が増え、重力井戸が広く強くなり、叩きつけを続けて放ちます。やめて部屋を離れると覚醒核は封印核の上に戻ります。',
         'The Foundry and awakened guardians',
         '- Ergosphere Leviathan (Sunken Reactor Sector): approach the dead core in the floor of the spherical chamber a'
         'nd it appears. It changes form at 2/3 and 1/3 health (invulnerable while it changes). Its wake drags you along'
         ' the way it swam: ride the flow to keep up. The glowing gold core in its tail is always a weak point.\n'
         '  Armored: reactor-plate scales, armored body. A cyan glow on the head means a dive (leave the marked spot); w'
         'hen the maw opens, do not stand in front (it pulls you in, then breathes a beam). Stay near its tail and it la'
         'shes you. After a dive grazes the floor it lingers low for a moment: your chance to strike back.'),
        ('leviathan', 'エルゴ球の大蛇',
         '・エルゴ球の大蛇（沈んだ炉心区画）: 球形の封じ込め室の床の、止まった炉心に近づくと現れます。体力の 2/3・1/3 で姿を変えます（変える間は傷つきません）。泳いだ跡の帯の中では泳ぐ向きへ流されるので、流れに乗って追い'
         'かけましょう。尾の金色に光る芯はいつも弱点です。\n'
         '\u3000装甲: 炉殻の鱗をまとい、胴は硬い。頭が水色に光ったら突進（狙われた場所から離れる）、口の渦が開いたら前に立たない（吸い寄せてから光線）。尾のそばにいると薙ぎ払われます。突進で床をかすめたあとは、しばらく床の近くで止ま'
         'るので反撃の好機です。\n'
         '\u3000骨格: 鱗を弾き飛ばし（近くにいると痛い）、光の骨格になる。胴も効くようになります。部屋の中央でとぐろを巻いて回り、まわりを押し流しながら光の欠片を撃つ。近づくと噛みつき、突進は続けて来ます。\n'
         '\u3000龍: 炉心の渦へ潜り、黒い龍となって噴き上がる。頭の両角から回る2本の光線（相対論的ジェット）、上空を回りながら足元に小さなブラックホールを降らせる嵐（光の柱が前触れ）。突進のあとに止まった場所には、飛び去るときに特異'
         '点が残ります。渦の断章を落とします。\n'
         '\u3000どの姿でも: 高く昇って身を丸めたら「地殻の波」（中心へ叩きつけ、床を伝う衝撃の輪が2つ。輪が来たら跳ぶ）。床に光の線が走ったら「重力の裂け目」（線の上から離れる。向きを変えて3回）。骨格と龍は、炉心の渦が開いて部屋じ'
         'ゅうを引き込む「引き潮」も使います（炉心の近くは傷つくので、流れに逆らって外へ走る）。\n'
         '\u3000炉心の再点火: 骨格と龍の姿では、炉心に巻きついて力を溜め、部屋じゅうへ衝撃を放ちます。画面に前触れが出たら、封じ込め柱の陰へ（柱の強化遺構ガラスも衝撃を遮ります）。',
         'The Ergosphere Leviathan',
         '  Skeleton: it blasts its scales away (painful up close) and becomes a skeleton of light; its body now takes f'
         'ull damage. It coils in the middle of the room, dragging you around while firing light shards, bites when you '
         'come close and chains its dives.\n'
         '  Wyrm: it dives into the core vortex and erupts as a black wyrm. Two relativistic jets sweep from its horns, '
         'it circles high and rains small black holes where you stand (a light pillar warns you); where it rests after a'
         ' dive, it leaves a singularity as it takes off. Drops Chapters of the Vortex.\n'
         '  In any form: when it rises and curls up, it slams the center and two shockwaves race across the floor (jump '
         'them). When lines of light cross the floor, get off them before the rift tears open (three volleys, each at a '
         'new angle). The skeleton and wyrm also open the core vortex to drag everything in (the core hurts; run against'
         ' the pull).\n'
         '  Core Reignition: as skeleton and wyrm it coils around the core, charges, and blasts the whole room. When the'
         ' warning appears, hide behind a containment pillar (the Reinforced Ruin Glass in the pillars blocks it too).\n'
         '- Foundry Overseer (AI core of the Android Laboratory): a mechanical brain floating over the core that never m'
         'oves and fights with the facility itself. While the four production lines on the walls run, it takes less dama'
         'ge (20% per line): break them first. The lines also send out mass-produced androids. A red mark on the floor i'
         's a robot arm (it follows you briefly, stops, then drops; caught, you are thrown), a shadowed circle is the pr'
         'ess (step out at once), and a fan drawn on the floor is the welding laser (a beam skims the floor as it turns;'
         ' jump over it). The floor conveyors carry you along and reverse after their frames glow red. Below half health'
         ' it calls two prototypes (Axe and Bit) and uses two arms. Drops precision actuators, and for each challenger a'
         ' compact crystal memory of a prototype (one you do not have yet comes first). Awakened with an Awakened Foundr'
         'y Core, it reprints its broken lines when enraged.\n'
         '- Awakened guardians: after beating the Echo Sentinel or Gravity Remnant, use an Awakened Echo Core or Awakene'
         "d Gravity Core on the guardian core in the room's floor (2.5× health, 1.5× damage, faster moves, enraged at 65"
         '%). The sentinel adds an echo and a bolt and re-projects up to two broken projectors when enraged; the remnant'
         ' fires more debris, widens its gravity well and chains its slams. Leave and the core is returned.'),
        ('lab', '地下アンドロイド研究所',
         '旧文明のアンドロイドの研究所。統括AI「ファウンドリ」が暴走し、職員は中央動力炉を止めて主立坑から逃げ出しました。'
         '人がいなくなった今も、アンドロイドたちは「施設を維持せよ」という最後の命令に従って施設を直し続けています。\n'
         '・入口は、地表に残る白い小さな建物（もとの受付とエレベーターホール）。エレベーターの昇降路だった崩れた主立坑を、'
         'はしごと足場で降ります。部屋と廊下は厚さ2の耐圧殻で包まれていて、統括AIを倒すまでは壊せません'
         '（倒したあとも黒曜石より硬い）。\n'
         '・専用の貨物昇降室にはクレーン・線路・コンベア・荷物置き場があり、その天井から地表まで搬入出の立坑が必ず通っています。主立坑と搬入出立坑は両方とも生成されます。部屋の手前と地表の1段下は溶接された蓋でふさがれていて、'
         '統括AIを倒すとダイヤのツルハシで掘れます。立坑は荒れていないので、はしごを掛ければ地表との近道になります。\n'
         '・主電源は落ちていて、非常灯だけが赤く灯る薄暗い施設です。中央動力炉の制御盤を右クリックすると動力炉が起動し、'
         '施設の明かりがついて、AI中枢の隔壁が開きます。そして、眠っていた統括AIも目を覚まします。\n'
         '・電力が戻ると、自販機がエメラルドで飲み物を売りはじめます（押した列の飲み物。ソーダは移動速度、スポーツドリンクは再生、'
         'エナジードリンクは採掘速度）。給湯室のコーヒーメーカーはカカオ豆で淹れ、ガラス瓶でくめます（採掘速度と眠気覚まし）。\n'
         '・部屋の並びは毎回変わります。研究室・資料室・サーバールーム・社員寮・研究員の個室の並ぶ個室区画・医務室・厨房のある食堂などが並び、警備機や印刷台が守っています。'
         '個室や厨房のスライドドアは右クリックで開き、しばらくすると自分で閉まります（研究所のドアは作れませんが、似たホワイトスライドドアは作業台で作れます）。'
         '物資の棚には研究所の部品や資料が残っています。\n'
         '・記録端末を右クリックすると、残された記録を読めます。集めると、暴走のいきさつが分かります。',
         'Underground Android Laboratory',
         'An old-civilization android laboratory. When its overseer AI, Foundry, went rogue, the staff shut down the central reactor and fled through '
         'the main shaft. Even now the androids keep repairing the facility, obeying their last order: "Maintain the facility."\n'
         '- The way in is a small white building left on the surface, the old reception and elevator hall. Climb down the collapsed main shaft, once '
         'the elevator well, on its ladders and scaffolding. The rooms and corridors are wrapped in a pressure hull two blocks thick that cannot be '
         'broken until the overseer falls (and is harder than obsidian even then).\n'
         '- A supply shaft always runs from a dedicated cargo lift room, with hoists, rails, conveyors and storage, to the surface. Both the main and supply shafts are generated. It is welded shut just above the room and one block below the '
         'surface; once the overseer falls, the welded hatches can be mined with a diamond pickaxe. The shaft is intact, so with ladders it becomes '
         'a shortcut to the surface.\n'
         '- Main power is down: only red emergency lights glow in the gloom. Use the central reactor control panel to restart the reactor: the lights come on, '
         'the AI core bulkheads open, and the dormant overseer wakes.\n'
         '- Once power is back, the vending machines sell drinks for emeralds (press the column you want: soda for Speed, sports drink for '
         'Regeneration, energy drink for Haste). The break-room coffee makers brew with cocoa beans; fill glass bottles from the pot '
         '(Haste, and it keeps you awake).\n'
         '- The room layout changes every time: research labs, archives, server rooms, dormitories, private rooms lined up along a hall, a medical bay, a cafeteria '
         'with a kitchen and more, guarded by security '
         'machines and fabricators. The sliding doors of the private rooms and kitchen open with a right-click and close by '
         'themselves after a while (the lab doors cannot be crafted, but the similar White Sliding Door is made at a Crafting Table). Supply chests still hold laboratory parts and records.\n'
         '- Use a record terminal to read what was left behind. Collect them to learn how the rampage began.'),
        ('plan', '探索の段取り',
         '段階が進むと探索先が一度に増えます。先に集めておくと、あとで工場が止まりません。\n'
         '・段階2のうち: 隕石クレーター（隕鉄・星屑）、氷山か氷樹（氷塊・青氷）。\n'
         '・段階3のうち: 研究棟。深海圧力収集器は早めに置いて、高圧結晶を貯めておく（殻とアンビルで約30個）。雷雨の日は砂に避雷針。\n'
         '・段階4の前に: 封鎖培養施設、古代都市（スカルク・残響の欠片。欠片は残響共鳴器で複製し続けられるので、1つあれば足りる）、'
         'エンドの虚空（虚空捕集器）、ウィザー（ネザースター。点火後は人工星核で代わりになる）。\n'
         '・原本の記録は修復でき、新品＋修復3回で約2.7倍使えます。何度も遠征し直すより、修復を使いましょう。\n'
         '未使用の観測ログ・量子データ片・培養データや復元部品は、同じ状態のものを64個まで重ねられます。装置の入力枠には1個だけ入り、Shiftクリックでも1個だけ投入します。使用途中のものは重なりません。\n加工で使い切った制御ユニットなどの復元部品や原本は、使用後の状態のまま出力に回ります。出力枠が埋まっている間は入力枠で待機し、そこからもホッパーやアイテムパイプで回収できます。自動搬出をオンにすると隣接先へ搬出します。修復用に投入した部品は自動で搬出しません。',
         'Planning expeditions',
         'Destinations pile up in later stages; gathering early keeps the factory running.\n'
         '- During stage 2: meteor craters (meteoric iron, stardust), icebergs or ice spikes (packed and blue ice).\n'
         '- During stage 3: Research Buildings. Place deep-sea pressure collectors early to stock pressure crystals (about 30 for shells and the anvil). '
         'Put lightning rods on sand in thunderstorms.\n'
         '- Before stage 4: Culture Facility, an ancient city (sculk and echo shards; the echo resonator keeps copying one shard), the End void '
         '(void collector), and the Wither (nether stars; artificial star cores replace them after ignition).\n'
         '- Original records can be repaired: new plus three repairs gives about 2.7× the uses. Repair before re-running a ruin.\n'
         'Unused Observation Logs, Quantum Data Fragments, Culture Data and restored parts stack to 64 when their state matches. Each machine input slot accepts only one; shift-click inserts one as well. Partly used items do not stack.\nRestored parts and original records exhausted during processing become item outputs with their usage and repair data intact. If the output slot is occupied, they wait in their input slot and can still be extracted by hoppers or item pipes. Enable automatic ejection to send them to adjacent inventories. Parts inserted for repair are not automatically ejected.'),
        ('compass', '探索コンパスの調整',
         '探索コンパスは、はじめは地表観測拠点しか探せません。攻略した（保管庫を自分で開けた）遺構の保管庫をスニークして'
         '右クリックすると、そこに残る記録で調整され、次の遺構（研究棟 → 封鎖培養施設 → 最終実験施設 → 沈んだ炉心区画）も探せるようになります。'
         '地下の遺構は、針の指す場所の下を掘って探します。',
         'Tuning the Explorer Compass',
         'At first the compass only finds Observation Posts. Sneak-use it on the cache of a ruin you have cleared to tune it '
         'for the next one: Research Building, then Culture Facility, then Final Lab, then Sunken Reactor Sector. Underground ruins lie below the spot it points to.'),
        ('sealed', '封印コンテナ',
         '各遺構には、次の段階の鍵でしか開かない封印コンテナが眠っています（磁気錠 → 量子錠 → 時間錠 → 特異点錠）。'
         '鍵は精密組立台で作り、1回で使い切ります。中には素材や色つきの照明のほか、ここでしか手に入らない珍しい道具'
         '（オーバークロック・チップ・重力ブーツ・触媒安定化剤・次元ポケット）が入っていることがあります。'
         '封印中のコンテナを無理に壊すと、中身ごと失われます。段階が進めば自分でもコンテナを作れ、鍵を持ってスニークして使うと封印できます。',
         'Sealed containers',
         'Each ruin hides a sealed container that opens only with the key of the next stage: magnetic, quantum, temporal, then singularity. Keys are made in the Precision Assembler and used up each time. Inside are materials, coloured lamps and sometimes a rare item found nowhere else: Overclock Chip, Gravity Boots, Catalyst Stabilizer or Dimensional Pocket. Breaking a sealed container destroys its contents. You can craft containers too, and sneak-use a key to seal one.'),
        ('records', 'クリスタルメモリ',
         '遺構の保管庫からときどき「クリスタルメモリ」が出ます。アーカイブ端末で電力を使って解読すると、旧文明の記録が読めます'
         '（ハンドブックの「旧文明の記録」の章に追加されます）。全部集めると、文明が滅んだ理由が分かります。',
         'Crystal memories',
         'Ruin caches sometimes hold crystal memories. Decode them with power in the Archive Terminal to read '
         'the records of the old civilization (they are added to the Ancient Records chapter of this '
         'handbook). Collect them all to learn why the civilization fell.'),
        ('gathering', 'その場所でしか採れない素材',
         '・隕石クレーター（平原・砂漠・森などの地表）: 差し渡し10ブロックほどの浅いすり鉢の中心に、半分埋まった隕石があります。'
         '隕鉄塊（鉄以上のツルハシ）から隕鉄の原石、黒い溶融殻に育つ星屑の結晶から星屑が採れます。星屑の結晶は、クレーターに最初からある殻にだけ、少しずつ育ち直します。'
         '隕石の芯には隕石の核が1つ埋まっていて、掘ると隕鉄の原石と星屑がまとめて出ます。隕石コンパスは、まだ掘り出されていない核のうち、いちばん近いものを指します。\n'
         '・雷ガラス: 雷が砂に落ちると、砂が雷ガラス塊になります。避雷針を砂の上に立てておくと、雷雨のたびに避雷針の下にできます。'
         '段階3のケラウノス放電塔があれば、電力で人工の雷を避雷針へ撃って、いつでも作れます。\n'
         '・高圧結晶（段階3）: 深海圧力収集器を、真上に水が16ブロック以上ある海底に置くと、水圧で少しずつできます（深いほど速い）。\n'
         '・虚空の塵（段階4〜5）: 虚空捕集器を、ジ・エンドで真下が奈落まで何もない所に置くと集まります。',
         'Materials found in one place',
         '- Meteor craters (plains, deserts, forests and other surface biomes): a shallow bowl about 10 blocks across with a half-buried meteorite. '
         'Mine meteoric iron chunks (iron pickaxe or better) for raw meteoric iron and stardust clusters on the black crust for stardust. '
         'Clusters slowly regrow, but only on crust still in its crater. '
         'Each meteorite has one meteorite core at its heart; mine it for raw meteoric iron and stardust together. '
         'The Meteor Compass points to the nearest core not yet mined.\n'
         '- Fulgurite: lightning striking sand turns it into fulgurite. Stand a lightning rod on sand and it forms under the rod in thunderstorms. '
         'The stage 3 Keraunos Discharge Tower fires artificial lightning at a rod on demand.\n'
         '- Pressure crystals (stage 3): a Deep-Sea Pressure Collector on the sea floor with 16+ blocks of water above slowly makes them; deeper is faster.\n'
         '- Void dust (stages 4-5): a Void Collector in the End, placed over open void, gathers it.'),
    ]),
    ('androids', 'singulo:android_station', 'アンドロイド', 'Androids', [
        ('ruin', '遺構のアンドロイド',
         'どの遺構にも、壁際に遺構の印刷台があります。サバイバルのプレイヤーが近づくと、台の型（光刃型・鞭型・光剣型のどれか）の'
         'アンドロイドを2〜3体、台の上へ下から積み上げるように印刷して出撃させます。全部倒すと、半分の確率でその型の小型クリスタルメモリが台の上に出ます。出ても出なくても、台は1日休みます。\n'
         '・光刃型「ルミナ」: 身をかがめて刃が光ったら、残像を残して詰め寄る瞬歩斬り。片膝をついて床に手を当てたら、足元に予告の輪が出て光の柱が立つ（輪から出る）。'
         '近くでは刃を広げて回転斬り。\n'
         '・鞭型「ヴェルナ」: 約6ブロック先まで届く鞭打ちと、巻きつけて引き寄せる絡め取り。鞭を頭上で回したら、床に打ち付けて一直線の衝撃波（跳べば当たらない）。\n'
         '・光剣型「ソレイユ」: 腰だめから踏み込む突きと、振る瞬間に刃が伸びる横薙ぎ、飛ぶ三日月の斬撃波。打たれると正面に光の盾を張り（正面からはあまり通らない）、'
         '構えの最中に打つと斬り返してきます。背後か横から攻めること。\n'
         '地下アンドロイド研究所には、印刷台には出てこない試作機がいます（統括AIが呼び出す）。\n'
         '・斧型「アルマ」: 硬くて重い。斧を右へ引いたら広い薙ぎ払い、頭上へ振り上げたら床へ叩きつけて前へ一直線の衝撃波（跳べば当たらない）、'
         '低くかがんだら跳びかかって着地でまわりを叩きます。\n'
         '・ビット型「ノクス」: 背中の浮遊ビットを浮かべ、離れた所から光線で撃ってきます（体力が半分を切ると6基）。間合いを取ろうとし、'
         '近づくと短剣で二連撃してから後ろへ跳びます。ビットの先端に刃が光ったら、狙った場所へビット自体が突進して斬ります。予告を見て横へ避けましょう。壁は光線も突進も止めます。\n'
         'アンドロイドには充電があり、大技を10回ほど使うと切れて、目の光が消えます。近くの印刷台へ戻るか、昼の空の下ではソーラーパネルを広げ、夜や屋内では座り込んで充電します'
         '（印刷台5秒・太陽光10秒・座り込み14秒）。充電中は攻撃してこないので、攻めどきです。',
         'Ruin androids',
         'Every ruin has Ruin Android Fabricators against its walls. When a survival player comes near, a fabricator prints two or three androids '
         'of its kind (Blade, Whip or Saber) onto its bed, built up from the feet, and sends them out. Defeat them all and, half the time, a compact crystal '
         'memory of that design appears on the fabricator. Either way, the fabricator then rests for a day.\n'
         '- Blade Android "Lumina": when she crouches and her blade flares, she dashes in leaving afterimages and slashes. When she kneels and touches '
         'the floor, rings appear at your feet and pillars of light rise (step out of the rings). Up close she spins with her blade spread.\n'
         '- Whip Android "Verna": a lash that reaches about 6 blocks and a bind that wraps and pulls you in. When she twirls the whip overhead, she slams '
         'the floor and sends a straight shockwave (jump it).\n'
         '- Saber Android "Soleil": a lunging thrust, a sweep whose blade stretches as she swings, and a flying crescent slash. When hit she raises a light '
         'shield in front (little gets through from the front) and counters if you keep striking it. Attack from the side or behind.\n'
         'The Android Laboratory holds prototypes that fabricators never print (its overseer calls them).\n'
         '- Axe Android "Alma": tough and heavy. When she draws the axe back to the right, a wide cleave follows; raised overhead, she slams the floor and '
         'sends a straight shockwave ahead (jump it); crouched low, she leaps in and strikes all around where she lands.\n'
         '- Bit Android "Nox": she floats the bits from her back and shoots you with beams from range (six bits below half health). She keeps her '
         'distance; get close and she stabs twice with a dagger, then hops back. When a blade lights up at a bit’s tip, the bit itself lunges at the marked position. Dodge sideways during the windup; walls stop both beams and lunges.\n'
         'Androids run on charge. After about ten big moves they run out and their eyes go dark. They return to a nearby fabricator, or unfold a solar '
         'panel under the open daytime sky, or sit down at night or indoors to recharge (fabricator 5 s, solar 10 s, sitting 14 s). They do not attack '
         'while charging: your chance to strike.'),
        ('build', '自分のアンドロイド',
         'アンドロイド組立ステーションに小型クリスタルメモリ（型を決める。減らない）とアンドロイドキット（印刷ごとに1つ）を入れ、電力を送ると、'
         '上に護衛のアンドロイドを1体印刷します（1回 40,000 FE）。倒されたら、キットがあれば印刷し直します。置いた人が持ち主です。\n'
         '・護衛は敵対モブ（遺構のアンドロイドやドローンも）だけを狙い、プレイヤーやボスとは戦いません。プレイヤーとボスの攻撃では傷つきません。\n'
         '・動き方: 迎撃（ステーションのそばで待つ）・徘徊（16ブロック以内を歩き回る）・追従（持ち主についていく）・巡回（決めた場所を回る）。'
         '追従中に戦うかは「ついていくだけ」と「護衛する」で選べます。\n'
         '・許可証: ステーションにシールド許可証を入れると、持ち主と登録した人だけがステーションとアンドロイドの画面を開けます。'
         '登録していない人（クリエイティブを除く）は敵とみなして攻撃し、その人の攻撃はアンドロイドにも当たります。'
         '追従する人も、持ち主か登録した人から選べます。\n'
         '・戻る体力: これを下回ると、ステーションへ戻って修理と充電をします（カーソルを合わせると、今の体力と充電が出る）。'
         '「呼び戻す」で戻すこともでき、Shift＋クリックなら近く（64ブロック以内）の操作できるステーションのアンドロイドをみな呼び戻します。\n'
         '・名前: ステーションやアンドロイドの画面で名前をクリックすると付けられます（↺で愛称に戻す）。\n'
         '・コンパレーター: ステーションから、アンドロイドの体力に応じた信号（0〜15）が出ます。\n'
         '・ステーションを壊すと、アンドロイドはその場に残ります。新しいステーションに同じ型のメモリを入れると、「リンク」でつなぎ直せます。\n'
         '・暮らし: 手が空いた護衛は、ステーションのそばで、階段・ハーフブロック・椅子に座る（夜は昼寝）、植木鉢や作物に手をかざす、床を掃く、しばらく落ちたままの物をそばのチェストへしまう、仲間とおしゃべりする、雨なら屋根の下へ（傘を持っていればそのまま）、夕焼けや夜空を眺める、など。',
         'Your own androids',
         'Put a compact crystal memory (sets the design; never used up) and android kits (one per print) in an Android Assembly Station and supply power, '
         'and it prints one guard android on top (40,000 FE each). If it falls, the station prints it again while kits last. Whoever places the station owns it.\n'
         '- Guards attack hostile mobs only (ruin androids and drones too) and never fight players or bosses. Player and boss attacks do not hurt them.\n'
         '- Modes: Guard (waits by the station), Wander (roams within 16 blocks), Follow (goes with its owner), Patrol (walks a set route). '
         'While following, choose "Just follow" or "Escort" (fights nearby enemies).\n'
         '- Permit: with a Shield Permit in the station, only the owner and registered players can open the station and android screens. '
         'The android treats anyone else (except in Creative) as an enemy and attacks them, and their attacks can hurt it. '
         'You can also choose whether it follows the owner or one of the registered players.\n'
         '- Return health: below it, the android goes back to repair and recharge (hover to see its current health and charge). '
         'Recall sends it back at once; shift-click Recall to call back every android from the stations you can use within 64 blocks.\n'
         '- Name: click the name in the station or android screen to rename it (the ↺ button restores the nickname).\n'
         '- Comparator: the station outputs a signal (0-15) from the android\'s health.\n'
         '- Break the station and the android stays where it is. Put a matching memory in a new station and press Link to bring it back.\n'
         '- Daily life: with nothing to do, guards relax near the station. They sit on stairs, slabs or chairs (napping at night), hold a hand over potted plants and crops, sweep the floor, put items left lying around into a nearby chest, chat with each other, shelter from the rain (or stay out under an umbrella if they hold one), and watch the sunset or the night sky..'),
        ('first', 'はじめての作業注文',
         '作業の自動化を、いちばん少ない手順で試す例（パンを作らせる）:\n'
         '1. ステーションで護衛を1体印刷する。\n'
         '2. 作業指令台・レシピ棚・作業台を近くに置き、指令台の隣に小麦を入れたチェストを置く（素材置き場）。\n'
         '3. 作業位置カードで指令台をシフト右クリックして記録し、そのカードでレシピ棚を右クリックしてつなぐ。\n'
         '4. 別の作業位置カードで作業台をシフト右クリックして記録し、レシピ棚の行の左に置く。\n'
         '5. 指令台の「記録」で 3×3 の横一列に小麦を3つ置き、レシピカードに記録して、レシピ棚の行の右に置く。\n'
         '6. 「注文」でパンを選び、個数を決めて作業開始。できたパンは指令台の納品箱に届く。\n'
         '注文の画面: 検索欄をクリックすると文字を打てる（@ のあとにmod名を打つと、そのmodの物だけ）。並べ替えと逆順のボタン、JEI の検索欄と同期するかの切り替えもある。アンドロイドを自動にすると、注文に必要な数だけ選'
         'んで呼ぶ。',
         'Your first work order',
         'The shortest way to try work automation (having bread made):\n'
         '1. Print one guard at a station.\n'
         '2. Place an Android Work Console, a Recipe Rack and a crafting table nearby, and a chest with wheat next to th'
         'e console (the stockpile).\n'
         '3. Shift-right-click the console with a Work Location Card to record it, then right-click the Recipe Rack with'
         ' that card to link it.\n'
         '4. Shift-right-click the crafting table with another Work Location Card and put it on the left of a rack row.\n'
         "5. In the console's Record tab, put three wheat in one row of the 3x3, record it on a Recipe Card and put the "
         'card on the right of the rack row.\n'
         "6. In the Order tab, pick bread, set a count and start. The bread arrives in the console's delivery box.\n"
         "The order screen: click the search box to type (type @ and a mod name to show only that mod's items). There ar"
         "e sort and reverse buttons, and a toggle to sync with JEI's search box. With androids set to automatic, it cal"
         'ls just as many as the order needs.'),
        ('patrol', '巡回警備',
         'ステーションの動き方を「巡回」にすると、決めた場所を順に回って見張ります。\n'
         '・巡回路: 「巡回」を選ぶと出る8つの欄に作業位置カードを置くと、その場所を左から順に回り、カードの場所の上に立って見回します。'
         '最後まで行ったら最初へ戻る（周回）か、来た道を戻る（往復）か、夜だけ回るか、各地点で立ち止まる時間を選べます。2体なら、反対側から回ります。\n'
         '・戦い: 近くの敵と戦い、片付けたら抜けた所から続けます。戦いになると、同じステーションの手の空いた仲間を呼びます（片付いたら持ち場へ戻る）。\n'
         '・警報: 敵の群れ（5体以上）かボスを見つけると、ステーションが5秒間レッドストーン信号を出し、持ち主に知らせます（1分に1回まで）。\n'
         '・記録: 倒した相手と警報は、アンドロイドの画面の「記録」のページに、新しい順に16件残ります。'
         '久しぶりにそばへ行くと、留守のあいだのことを一言知らせてくれます。',
         'Patrol',
         'Set the station\'s mode to Patrol and the androids walk a set route and keep watch.\n'
         '- Route: choosing Patrol shows eight slots. Put Work Location Cards in them and the androids visit those places from left to right, '
         'standing on top of each to look around. Choose whether they go back to the first point (Loop) or return the same way (Back & forth), '
         'whether they patrol only at night, and how long they stop at each point. Two androids start from opposite sides.\n'
         '- Fights: they fight enemies nearby and carry on from where they left off. When a fight starts, idle androids of the same station come to '
         'help (and go back afterwards).\n'
         '- Alarm: when they spot a group of enemies (5 or more) or a boss, the station gives a redstone signal for 5 seconds and the owner is told '
         '(at most once a minute).\n'
         '- Log: defeated enemies and alarms are kept on the Log page of the android screen, newest first (16 entries). '
         'Come back after a while and the android tells you what happened while you were away.'),
        ('routes', '経路と測量メガネ',
         '作業指令台の「経路」のページと、組立ステーションの「経路」（題の右のボタン）で、アンドロイドの移動の決まりを4つまで作れます'
         '（作業指令台は作業の移動に、ステーションは巡回の移動に効く）。\n'
         '・1行に、経由地点（作業位置カード）、対象範囲（範囲カード）、進入禁止ゾーン（範囲カード、2つまで）を置きます。\n'
         '・対象範囲の中と外を行き来する移動では、経由地点の上を通ってから行き先へ向かいます（拠点の扉のように）。'
         'そのあいだは、進入禁止ゾーンに入りません（出発した所・行き先がゾーンの中なら、そのゾーンは避けない）。\n'
         '・範囲カード: ブロックを2つシフト右クリックすると、その2つを角とする範囲を記録します（1辺128ブロックまで）。'
         '範囲は、記録したブロックの高さからその上2マスまでに効くので、床のブロックを記録すれば、その上を歩くアンドロイドに効きます。\n'
         '・測量メガネ: 頭に掛けると、手に持った範囲カード・作業位置カードの記録した場所が枠で見えます'
         '（範囲カードの1つ目の角だけなら、見ているブロックまでの範囲の見込みも）。作業中のアンドロイドの頭の上の印も、掛けているときだけ見えます。',
         'Routes and Survey Glasses',
         'On the Routes page of the work console, and the Routes page of the assembly station (the button right of the title), you can set up to '
         'four movement rules (the console\'s apply to work trips, the station\'s to patrols).\n'
         '- Each row takes a waypoint (Work Location Card), an area (Area Card) and up to two no-entry zones (Area Cards).\n'
         '- Moving into or out of the area, androids first pass over the waypoint, like a door to the base, and stay out of the no-entry '
         'zones on the way (unless they start or end inside one).\n'
         '- Area Card: sneak-use two blocks to record the area between them as corners (up to 128 blocks per side). Areas reach from the '
         'recorded height up two blocks, so recording floor blocks works for androids walking on them.\n'
         '- Survey Glasses: worn on the head, they outline the places recorded on the area or work location card in your hand (with only the '
         'first corner recorded, the area up to the block you look at is previewed). The icons over working androids only show while you wear them.'),
        ('care', '充電と修理と装備',
         '・充電: 護衛はステーションへ戻って5秒で満タン（電力を使い、体力も直る）。追従中は、無線充電器を持っていれば持ち主のそばで、ついて歩きながら5秒で満タン'
         '（充電器は満タンで30回分。電力を出すブロックを右クリックして充電する）。ステーションが遠く充電器もなければ、太陽光か座り込みで充電します。'
         '手が空いていてステーションから24ブロック以内にいれば、充電が減ったときは自分で満タンにしに戻ります。\n'
         '・持ち物: 自分のアンドロイドを右クリックすると画面が開きます。自己修理キット（1スタック）は、体力が戻る割合を下回ったときに1つ使って半分直します。'
         'アンドロイド用装甲板（1枚）は胸当てになり（防御 +8、防具強度 +2）、防具のエンチャントが付けられます。積むと胸と左肩に装甲が付きます。\n'
         '・ステーションの補充スロットに自己修理キットを入れておくと、1つでも減れば補充します。強化スロットに転送アップグレードを積むと、倒れたときの持ち物を'
         '次のアンドロイドへ引き継ぎます（ないとその場に落とす）。ステーションへはホッパーなどで搬入できます（外からは取り出せない）。\n'
         '・解体: アンドロイドの画面のスパナを2回押すと、上から消えていき、ステーションがすぐ新しく印刷します。',
         'Charging, repair and gear',
         '- Charging: a guard refills in 5 s back at its station (using power, and it heals too). While following, if you carry an Android Wireless Charger '
         'it comes to you and refills in 5 s as it walks with you (a full charger holds 30 refills; right-click a block that supplies power to charge it). '
         'With the station far away and no charger, it uses solar power or sits down. When it has nothing to do and is within 24 blocks '
         'of its station, it goes back on its own to top up whenever its charge is not full.\n'
         '- Inventory: right-click your android to open its screen. A stack of Self-Repair Kits: one is used to restore half its health when it drops below '
         'the return health. One Android Armor Plate works as its chestplate (+8 armor, +2 toughness) and takes armor enchantments; it shows as plating on '
         'the chest and left shoulder.\n'
         '- Kits in the station\'s refill slot top up the android whenever it has used one. A Transfer Upgrade in the upgrade slot passes a fallen android\'s '
         'inventory to the next one (otherwise it drops). Hoppers and pipes can insert into the station; nothing can be pulled out.\n'
         '- Dismantle: press the wrench in the android screen twice; it fades away from the top and the station prints a new one at once.'),
        ('upgrades', 'ステーションのアップグレード',
         '・生産アップグレード: ステーションに積むと、アンドロイドを2体まで印刷して受け持つ。上で充電できるのは1体ずつで、もう1体はそばで待って交代する。画面の名前の左の「1」「2」で、見るアンドロイドを選ぶ（動き方などの設定'
         'は2体共通）。\n'
         '・工場式印刷ユニット: ステーションのキットの横のスロットに積むと、印刷にかかる時間と電力が半分になり、上での修理が2倍速くなる。地下アンドロイド研究所で手に入る精密アクチュエーターで作る。',
         'Station upgrades',
         '- Production Upgrade: fitted in the station, it prints and looks after up to two androids. Only one charges on'
         ' top at a time; the other waits beside it and takes its turn. Use "1" and "2" left of the name to choose which'
         ' android the screen shows (settings are shared).\n'
         "- Factory Print Unit: in the slot beside the station's kit slot, it halves printing time and power and doubles"
         ' repair speed on the station. Made from precision actuators found in the Underground Android Laboratory.'),
        ('gear', 'アンドロイドのアップグレード',
         '・アンドロイドの画面の名前の左のボタンで「装備と持ち物」のページに切り替えると、アップグレードを積める。\n'
         '・バッテリーアップグレード: 1個につき充電の満タンが50%増える（2個まで）。満タンまでの充電の時間もそのぶん延びる。腰にバッテリーパックが付く。\n'
         '・バックパックアップグレード: 持ち物（5スロット）に、バックパックのスロットが加わる。3段階あり、バックパックは12・強化バックパックは18・大型バックパックは24スロット。上の段階は1つ下を材料にして作る。中に物があ'
         'るうちは外せないが、中身がそのまま入る大きいバックパックへは付け替えられる。\n'
         '・採掘アップグレード: 採掘の作業ができるようになる（ダイヤのツルハシ相当）。効率強化・幸運・シルクタッチを付けられる。\n'
         '・伐採アップグレード: 伐採の作業ができるようになる（ダイヤの斧相当）。効率強化・幸運・シルクタッチを付けられる。\n'
         '・タンクアップグレード: 液体・ガスを運べるタンクが2つ（各32,000 mB、1つに1種類）。腰にタンクが見える。中身が残っている間は外せない。画面のタンクをバケツなどでクリックすると出し入れでき、横の × をシフト＋'
         'クリックすると中身を捨てる。\n'
         '・ガスタンクアップグレード・ガス・液体アップグレード（Mekanism が入っているときだけ）: Mekanism の化学物質（ガス・注入材・顔料・スラリー）を運べるタンク。ガスタンクは化学物質を2種類（各32,000 '
         'mB）、ガス・液体は液体と化学物質を2種類ずつ（各16,000 mB）。腰にガスボンベが見える。画面のタンクはケミカルタンクなどでクリックして出し入れできる。作業では、資材口の隣のケミカルタンクを素材置き場として、化学物'
         '質を装置へ運ぶ。\n'
         '・作業のオン／オフ: 作業（自動化）に割り当ててよいかを、アンドロイドごとに決める。\n'
         '・並べ替え・預ける: バックパックの欄の右上のボタンで、持ち物とバックパックの中を並べ替えたり、近くの箱（12ブロック以内のチェスト・樽・シュルカーボックス・資材口）へ歩いて預けに行かせたりできる。',
         'Android upgrades',
         '- The button left of the name in the android screen opens the "Gear and inventory" page, where upgrades go.\n'
         '- Battery Upgrade: each adds 50% to the full charge (up to two); a full recharge takes that much longer. A bat'
         'tery pack appears on the waist.\n'
         '- Backpack Upgrades: add backpack slots to the 5-slot inventory. There are three tiers: Backpack 12, Reinforce'
         'd Backpack 18 and Large Backpack 24 slots, each made from the tier below. A backpack cannot be removed while i'
         't holds items, but you can swap in a larger one whose slots still fit the contents.\n'
         '- Mining Upgrade: lets the android do mining work (like a diamond pickaxe). Takes Efficiency, Fortune and Silk'
         ' Touch.\n'
         '- Felling Upgrade: lets the android fell trees (like a diamond axe). Takes Efficiency, Fortune and Silk Touch.'
         '\n'
         '- Tank Upgrade: two tanks for liquids and gases (32,000 mB each, one fluid per tank), shown at the waist. It c'
         'annot be removed while they hold anything. Click a tank in the screen with a bucket or other container to fill'
         ' or draw; shift-click the × beside it to dump the contents.\n'
         '- Gas Tank Upgrade and Gas & Liquid Upgrade (only with Mekanism installed): tanks for Mekanism chemicals (gase'
         's, infuse types, pigments, slurries). The Gas Tank holds two chemicals (32,000 mB each); the Gas & Liquid hold'
         's two liquids and two chemicals (16,000 mB each). Gas cylinders show at the waist. Click a tank in the screen '
         'with a chemical tank to fill or draw. For work, chemical tanks next to a supply port join the stockpile and an'
         'droids carry chemicals to machines.\n'
         '- Work on/off: choose for each android whether it may be given work (automation).\n'
         '- Sort and put away: the buttons at the top right of the backpack area sort the inventory and backpack, or send the android to put them in a nearby box (a chest, barrel, shulker box or supply port within 12 blocks).'),
        ('work', '作業の自動化',
         '自分のアンドロイドに、作業台のクラフトを任せられます。\n'
         '1. アンドロイド作業指令台を置き、作業位置カードでシフト右クリックして記録する。そのカードでレシピ棚（増やすなら資材口・納品口も）を右クリックすると、指令台につながる（64ブロック以内）。\n'
         '2. 指令台（と資材口）の隣にチェストを置く（素材置き場）。アンドロイドは、素材が実際に入っている所を回って取る。'
         '完成品は、注文で選んだ納品先（指令台の納品箱か、名前を付けた納品口）に届く。\n'
         '3. 指令台の「記録」で、3×3 に見本を置き（物は減らない。JEI の「+」でも置ける）、レシピカードに記録する。\n'
         '4. レシピ棚の行の左に、作業台を記録した作業位置カードを置き、右にレシピカードを並べる。\n'
         '5. 指令台の「注文」で作る物と個数を選び、素材が足りるのを確かめて、アンドロイドを選んで作業開始。'
         '途中の素材も、レシピがあれば自分で作ります。\n'
         '・「新規」: オンにすると、在庫にある完成品は使わず、注文した数を新しく作る（例: 鉄の斧が3本あって、あと2本ほしいときは「2」）。\n'
         '・注文の一覧では、作っている最中の物に橙の枠が付く。進行中の注文の「↻」で、同じ物をもう一度注文できる。'
         '素材を待っている注文には、足りない物と数が出る（行にカーソルを合わせると全部）。\n'
         '・画面から出した注文は、できたとき・1分以上素材を待って止まったとき・取り消されたときに、注文した人へチャットで知らせる。\n'
         'アンドロイドは5スロット（バックパックで+12・18・24）ずつ運びます。作業中は持ち物を取り出せず、終わると元の動き方に戻ります。'
         '作業中のアンドロイドにも、同じ指令台の次の注文を受け持たせられます（手が空いたら、そちらもする）。'
         'たどり着けない場所があると、その注文は取り消されます。',
         'Work automation',
         'Your androids can take over crafting table work.\n'
         '1. Place an Android Work Console and sneak-use a Work Location Card on it to record it. Use that card on recipe racks (and extra '
         'supply ports and delivery ports) to link them to the console (within 64 blocks).\n'
         '2. Put chests next to the console (or a supply port) as the stockpile. Androids go round the places that actually hold the '
         'materials. Finished orders go to the destination chosen in the order: the console\'s outbox or a named delivery port.\n'
         '3. In the console\'s Record tab, lay out a 3×3 pattern (nothing is used up; JEI\'s + button works too) and record it onto a recipe card.\n'
         '4. In a recipe rack row, put a Work Location Card holding a crafting table on the left and the recipe cards on the right.\n'
         '5. In the console\'s Order tab, pick an item and amount, check the materials are in stock, choose androids and press Start. '
         'Intermediate materials are crafted too when there is a recipe for them.\n'
         '- New: when on, finished items already in storage are not used and the whole amount is crafted new (for example, with 3 iron axes in storage, order 2 to get 2 more).\n'
         '- In the Order tab, items being made have an orange frame. The ↻ button on a running order orders the same item again. '
         'An order waiting for materials shows what is missing and how many (hover over the row for the full list).\n'
         '- Orders placed from the screen send the orderer a chat message when they finish, when they have been stuck waiting for materials for a minute, or when they are cancelled.\n'
         'Androids carry 5 slots at a time (+12, 18 or 24 with a backpack). You cannot take items from a working android; when the order is done it goes '
         'back to its usual mode. A working android can also take on more orders from the same console and works on them when it is free. '
         'If a place cannot be reached, the order is cancelled.'),
        ('process', '加工・手順・採掘',
         '指令台の「記録」のモードボタンで、記録する種類を切り替えます。\n'
         '・加工: かまどや装置のレシピ。左に入れる物、右に出てくる物（ほかに出てくる副産物も2つまで）を置き、ホイールで数を変える（JEI の「+」でも置ける）。アンドロイドは装置に素材を入れ、できた物を取り出す（面は自動。記録'
         'の「入」「出」のボタンで決めることもできる）。長くかかる加工は、待つあいだにほかの作業（受け持つほかの注文も）をして、できたら取りに戻る。副産物は素材置き場へ戻す。燃料や電力は自分で用意する。2分たっても進まなければ、注'
         '文は取り消される。\n'
         '・手順: 手順カードに、投入・取り出し・採掘の手順を8つまで記録する。作業位置カードを置いて場所を写し、物の欄に物を置いて「足す」。右にできる物を置いて記録する。手順カードは場所を自分で持つので、レシピ棚のどの行にも並べ'
         'られる。\n'
         '・採掘: 採掘アップグレードを積んだアンドロイドだけが行う。作業位置カード2枚で範囲を決めると、上の段から掘り、目当ての物がそろうまで続ける（丸石製造機にも使える）。壊せないブロック・液体・入れ物・シールドで守られた所は'
         '掘らない。掘る物がない状態が30秒続くと取り消される。',
         'Processing, procedures and mining',
         "The mode button in the console's Record tab switches what you record.\n"
         '- Processing: furnace and machine recipes. Put what goes in on the left and what comes out on the right (plus '
         "up to two byproducts), and scroll to change counts (JEI's + button works too). Androids put the items in and t"
         'ake the results out (the faces are chosen automatically, or set them with the In and Out buttons when recordin'
         'g). While a slow machine works, they do other jobs (including their other orders) and come back when it is don'
         'e. Byproducts go back to the stockpile. Provide fuel or power yourself. If nothing happens for two minutes, th'
         'e order is cancelled.\n'
         '- Procedure: record up to eight insert, extract and mine steps on a Procedure Card. Place a Work Location Card'
         ' to copy the place, put the item in the item slot and press Add. Put the product on the right and record. Proc'
         'edure cards hold their own places, so they work in any recipe rack row.\n'
         '- Mining: only androids with a Mining Upgrade do it. Two Work Location Cards mark an area; it is dug from the '
         'top layer down until enough of the wanted item is collected (it works with a cobblestone generator too). Unbre'
         'akable blocks, liquids, containers and shield-protected places are skipped. If there is nothing to dig for 30 '
         'seconds, the order is cancelled.'),
        ('farm', '農作業・液体・燃料',
         '・農作業: 収穫（育ちきった作物を取って植え直す。サトウキビ・サボテンは根元を残す）、伐採（木を丸ごと切って苗木を植え直す。伐採アップグレードが要る）、繁殖（えさを与えて、決めた数まで増やす）、毛刈り・搾乳（空のバケツを'
         '持っていく）、処理（決めた数を超えた分）。名前付き・つながれた・飼われている動物と子どもには手を出さない。\n'
         '・操作: レバー（オン・オフ・切り替え）、ボタン、ドアなどを使う。仕掛けを動かす合図に。\n'
         '・液体・ガス: 資材口や指令台の隣のタンクも素材置き場になる。加工モードや手順の見本の欄を、バケツなどの入れ物でクリックするか JEI の「+」で、流体を置ける（ホイールで 100 mB ずつ、シフトで 1000 mB '
         'ずつ）。タンクアップグレードを積んだアンドロイドが、流体を装置へ運ぶ。流体を作る注文は、素材置き場のタンクへ入れる。\n'
         '・燃料: 加工モードの燃料の欄に燃料を置いて記録すると、装置の燃料が切れたとき、その燃料を素材置き場から取ってきて入れる（置いた数ずつか、欄の下のボタンで「満タン」にすると装置に入るだけ。どちらも入りきらない分は持ってこ'
         'ない。空なら取りに行かない）。素材置き場になく、レシピで作れるなら、燃料を作る注文を自動で出す（作るあいだ、アンドロイドはほかの作業をする）。\n'
         '・確率で出る物: できた数が足りなければ、足りない分の素材を素材置き場から取って、集まるまで作り直す（素材がなければ、そろうまで待つ）。収穫・伐採・牧場では、まだ育つ物があれば育つのを待つ（20分まで）。アメジストは育ち'
         'きった塊だけ取り、芽生えたアメジストは壊さない。\n'
         '・アンドロイド作業台: バニラの作業台の4倍の速さで作る。作った物がアンドロイドの持ち物に入りきらないときは、足元に落とさずに台の預かり箱へ入れ、あとで取りに来る（取りに来られなくなった物は、右クリックで受け取れる）。預かり箱に物があるあいだは、台の上にその物が浮かぶ。',
         'Farming, fluids and fuel',
         '- Farming: Harvest (picks ripe crops and replants; sugar cane and cactus keep their base), Fell (cuts whole tr'
         'ees and replants saplings; needs a Felling Upgrade), Breed (feeds animals up to a set number), Shear and Milk '
         '(empty buckets are taken along) and Cull (anything above a set number). Named, leashed and tamed animals and b'
         'abies are left alone.\n'
         '- Use: flips levers (on, off or toggle), presses buttons, opens doors and so on, to drive contraptions.\n'
         '- Liquids and gases: tanks next to supply ports or the console join the stockpile. Click a pattern slot in pro'
         "cessing or procedure mode with a bucket or other fluid container, or use JEI's + button, to place a fluid (scr"
         'oll for 100 mB steps, Shift for 1000 mB). Androids with a Tank Upgrade carry fluids to machines. Orders that m'
         'ake a fluid fill the stockpile tanks.\n'
         '- Fuel: record a fuel in the fuel slot of the processing mode, and when the machine runs out, androids fetch t'
         'hat fuel from the stockpile and load it (the count you set each time, or as much as fits with the Fill button '
         'below the slot; never more than the machine can take. Leave it empty to never fetch fuel). If the stockpile ha'
         's none and there is a recipe for it, an order to make the fuel is placed automatically (the androids do other '
         'work meanwhile).\n'
         '- Chance outputs: if fewer come out than planned, materials for the rest are taken from the stockpile and the '
         'step repeats until enough is collected (if materials run short, it waits). Harvesting, felling and ranching wa'
         'it for things that are still growing (up to 20 minutes). Amethyst: only full clusters are picked, and budding '
         'amethyst is never broken.\n'
         "- Android Workbench: crafts four times as fast as a vanilla crafting table. When crafted items do not fit in the android's "
         "inventory, they go into the workbench's holding box instead of onto the ground, and the android comes back for them "
         "(right-click to take anything left behind). While the holding box has items, one of them floats above the workbench."),
        ('trouble', '止まっているとき',
         'アンドロイドが止まっているときは、理由を次の場所で確かめられます。\n'
         '・アンドロイドの画面の状態のページ: 吹き出しに赤い字で理由が出る。\n'
         '・作業指令台の注文の詳細: 担当のアンドロイドの名前の横に出る。\n'
         '・測量メガネを掛けると、止まっているアンドロイドの頭の上に立ち入り禁止の印が出る。\n'
         '理由と直し方:\n'
         '・ステーションの電力が足りない: 発電を増やすか、ステーションへのケーブルを確かめる。\n'
         '・行き先にたどり着けない: 段差・扉・水で道が切れていないか、経路の決まり（進入禁止ゾーン）が道をふさいでいないか確かめる。\n'
         '・素材置き場に素材が足りない: 資材口や指令台の隣のチェストに素材を足す。途中の素材は、レシピがあれば自分で作る。\n'
         '・守られたブロック: シールドやほかのmodの保護で壊せない。注文した人に壊す権限があるか確かめる。\n'
         '・作業の範囲に対象がない: 作業位置カードの範囲を見直す。作物が育つのを待っている間は止まらない（時計の印）。\n'
         '・置き場がいっぱい: 納品口や素材置き場のチェストを空ける。\n'
         '注文が取り消されたときは、指令台に理由が出ます（たどり着けない・レシピが合わない・長く進まない・入れる面が違う）。',
         'When an android stops',
         'When an android is stuck, you can see why in these places:\n'
         '- The status page of the android screen: the reason appears in red in the speech bubble.\n'
         "- Order details in the Work Console: it appears next to the assigned android's name.\n"
         "- With Survey Glasses on, a barrier icon floats over a stuck android's head.\n"
         'Reasons and fixes:\n'
         '- The station is out of power: add generation or check the cable to the station.\n'
         "- Can't reach the destination: check that steps, doors or water don't cut the path, and that a route rule (no-"
         "go zone) doesn't block it.\n"
         '- Not enough materials in the stockpile: add materials to the chests beside the supply port or the console. In'
         'termediate materials are crafted if there is a recipe.\n'
         "- A protected block: a shield or another mod's protection stops it. Check that the person who placed the order"
         ' may break it.\n'
         '- Nothing to work on in the area: check the Work Location Card area. Waiting for crops to grow does not count '
         'as stuck (clock icon).\n'
         '- Storage is full: empty the delivery port or stockpile chests.\n'
         "When an order is cancelled, the console shows why (unreachable, recipe doesn't match, no progress for a long t"
         'ime, or the wrong input face).'),
        ('decor', 'アンドロイドの装飾',
         'アンドロイドの画面の、名前の左のボタンでページを切り替えると、3枚目が「装飾」のページ。頭・背中/腰・手の枠に装飾を着けられる。'
         '戦い・作業には影響しない。どれも作業台で作り、染料と一緒に作業台で染められる（革の防具と同じ）。\n'
         '・頭: リボン・髪飾り・ベレー帽・ヘッドドレス\n'
         '・背中/腰: マント・マフラー・腰リボン（マントとマフラーの垂れは、歩くとなびく）\n'
         '・手: 文庫本・花束・ティーカップ・傘（待機している間だけ持つ。戦い・作業・充電の間はしまう）',
         'Android decorations',
         'Switch pages with the button left of the name in the android screen; the third page is Decorations, with head, back/waist and hand '
         'slots. Decorations do not affect fighting or work. They are all made on a crafting table and can be dyed there with dyes, like '
         'leather armor.\n'
         '- Head: ribbon, hair flower, beret or headdress\n'
         '- Back/waist: cape, scarf or waist ribbon (the cape and the scarf\'s tail flutter as it walks)\n'
         '- Hand: paperback, bouquet, teacup or umbrella (held only while idle; put away while fighting, working or charging)'),
        ('glass', 'アンドロイド用のガラス',
         '・アンドロイド遮断ガラス（琥珀色）: アンドロイドだけが通れない。プレイヤーやほかの生き物はすり抜けられる。拠点の出入り口に。\n'
         '・アンドロイド透過ガラス（水色）: アンドロイドだけが通れる。ほかにとってはふつうのガラス。護衛の通り道に。\n'
         'アンドロイドは道を探すときも、遮断ガラスはふさがった所、透過ガラスは空いた所として扱います。無地のものは同じガラスが隣り合うとつながって1枚に見え、'
         '模様入りは無地4枚から作れます。',
         'Android glass',
         '- Android Barrier Glass (amber): only androids cannot pass; players and other creatures go straight through. Good for doorways.\n'
         '- Android Gate Glass (cyan): only androids can pass; solid glass for everything else. Good for a guard\'s own path.\n'
         'Androids also plan their paths around it: barrier glass counts as blocked, gate glass as open. The plain kinds join seamlessly with the same glass '
         'next to them, and four plain panes make the patterned kind.'),
    ]),
    ('romance', 'singulo:tipler_core', '特異点技術', 'Singularity Tech', [
        ('shield', 'イベントホライズン・シールド',
         '半径32の中を守る塔（5×5、高さ9）。時間結晶触媒で爆発と荒らしを防ぎ、シンギュラリティ・コアなら敵の湧きと侵入も止める。電力16,384 FE/t。',
         'Event Horizon Shield', 'A tower (5×5, 9 high) that protects a radius of 32. A time crystal catalyst stops explosions and '
                                 'griefing; a Singularity Core also stops hostile spawns and intruders. Uses 16,384 FE/t.'),
        ('tipler', 'Tシリンダー',
         '半径16の時間を×2にする（5×5×9）。燃料と触媒はコアの画面から入れる。時間結晶触媒・エキゾチック物質（1分に1個）・100 kFE/t。',
         'T-Cylinder', "Speeds up time within a radius of 16 (×2; 5×5×9). Fuel and catalyst go in through the core's screen: "
                       'a time crystal catalyst, exotic matter (one per minute) and 100 kFE/t.'),
        ('gear', '身につける重力技術',
         '・メトリック・ドライブ: 持ち物に入れて、低重力・無重力（飛行）・高重力を切り替える。\n'
         '・グラビトン・マニピュレーター: 浮遊・牽引・斥力・圧壊。G キーで円錐範囲。\n'
         '・ワールドライン・アンカー（上位）: コアを埋め込むと消費なしで半径5。',
         'Gravity gear', '- Metric Drive: keep it in your inventory and switch between low gravity, zero gravity (flight) and '
                         'high gravity.\n- Graviton Manipulator: levitate, pull, repel and crush. The G key switches to a cone '
                         'area.\n- Advanced Worldline Anchor: with a core embedded, it keeps a radius of 5 loaded with no '
                         'upkeep.'),
        ('darkmatter', 'ダークマター',
         'ハロー捕集器を炉心から16ブロック以内に置くと集まる。普通の入れ物からは漏れるので、重力閉じ込めタンクに入れる。'
         '炉心に直接入れると、エディントン限界を受けずに質量になる。',
         'Dark matter', 'A Halo Collector placed within 16 blocks of a core collects it. It leaks from ordinary containers, '
                        'so keep it in a Gravitational Containment Tank. Fed directly into the core, it becomes mass without '
                        'the Eddington limit.'),
    ]),
    ('genesis', 'singulo:genesis_core', '創世炉', 'Genesis Engine', [
        ('build', '創世炉を組む',
         '旧文明は「すべての質量をエネルギーに」変えようとして滅びました。創世炉はその逆で、エネルギーから宇宙を生み出す炉です。\n'
         '・核と同じ高さに、創世炉の枠を8個置きます。核から十字方向に3マスの4か所と、斜めに2マスずつ離れた4か所です。\n'
         '・斜めの4つの枠の上に、創世炉の柱を3段ずつ（計12個）積みます。\n'
         '形ができると核が鳴ります。原初の種（作るには渦の断章が必要）を核に使うと始まります。電力はどの面からでも入ります。',
         'Building the Genesis Engine',
         'The old civilization fell trying to turn all mass into energy. The Genesis Engine does the reverse.\n'
         '- Eight Genesis Frames level with the core: three blocks out on each axis, and two-and-two on each diagonal.\n'
         '- Three Genesis Pylons stacked on each of the four diagonal frames (twelve in all).\n'
         'The core chimes when formed. Use a Primordial Seed on it to begin. Power enters from any side.'),
        ('birth', '宇宙の誕生',
         '1. 種火: 1.5 TFE を注ぎます（1 tick あたり最大 1 GFE）。Pリアクターを全開にして、1分あまりかかります。\n'
         '2. 安定化: 100 MFE/t を保ったまま、進めている時間で約4分（止めている間や電力が足りない間は進まないので、実際はもう少しかかります）。ときどき「暴走の兆し」の警報が鳴ります。警報が鳴っている間は、投入を止めてください'
         '（核を素手で使って開く画面のスイッチか、核への赤石信号で止められます）。止めずにいると不安定度が跳ね上がり、100% に達すると'
         '宇宙が崩れて進行が巻き戻ります。止めている間も蓄えから維持の電力は使われ、異変も起きます。重力の漏れ・記録の残響・引力の波にも備えましょう。シールドの中では暴走と異変が弱まり、'
         'Tシリンダーの加速の場の中では速く進みます。\n'
         '3. 誕生: 光が一点に落ち、宇宙が生まれます。生まれた銀河は、オーバーワールドの夜空に昇ります。\n'
         '核を素手で使うと画面が開き、段階・進行・不安定度・受電を確かめられます。',
         'Birth of a universe',
         '1. Kindling: pour in 1.5 TFE (at most 1 GFE per tick) — a little over a minute at full P-Reactor output.\n'
         '2. Stabilizing: hold 100 MFE/t for about four minutes of active progress (it pauses while the feed is stopped or power runs short, '
         'so expect a little longer). Now and then a surge alarm sounds: stop the feed while it lasts '
         '(the switch on the screen that opens when you use the core empty-handed, or a redstone signal). Keep feeding and instability spikes; at 100% the proto-universe '
         'collapses and progress is lost. Upkeep still drains the buffer and events still happen while stopped. Watch out for gravity leaks, echoes and gravity waves. A shield softens surges and events; '
         'a T-Cylinder field speeds things up.\n'
         '3. Birth: the light falls into a point and a universe is born. Its galaxy rises into the overworld night sky.\n'
         'Use the core empty-handed to open its screen: stage, progress, instability and input.'),
        ('synth', '質量生成',
         '宇宙を生んだ創世炉は、電力から物を作り出せます。核を素手で使って画面を開き、作りたい物を左の枠に登録してください'
         '（物を持って核を使っても登録できます）。完成品は画面右のスロットから受け取れます（下からホッパーなどで取り出すこともできます）。\n'
         '1個に必要な電力 = 質量 × 1 GFE × 10^希少度。希少度は 0（石・土など）から 4（ネザースター・エリトラ・ドラゴンの卵など）まであり、'
         '希少な物ほど桁違いに電力を食います。宇宙を生んだ核は壊しても記憶を宿したままで、別の場所に置き直せばそのまま使えます（ただし、溜めた電力と作りかけの進み具合は引き継がれません。高価な物を作っている途中では壊さないこと）。'
         '作れる物はデータパック（mass_synthesis）で変えられます。',
         'Mass synthesis',
         'A Genesis Engine that has given birth can make items from power. Use the core empty-handed to open '
         'its screen and put the item you want in the left frame (or use the item on the core). Collect '
         'results from the slot on the right (or pull them from below with a hopper).\nCost per item = mass × 1 '
         'GFE × 10^rarity. Rarity runs from 0 (stone, dirt) to 4 (nether star, elytra, dragon egg), so rarer '
         'items cost orders of magnitude more power. A broken core keeps the memory of its universe and works '
         'again wherever you place it, but stored power and partial progress are lost, so do not break it in '
         'the middle of an expensive item. The list of items is a datapack (mass_synthesis).'),
    ]),
    ('hazards', 'minecraft:tnt', '危険', 'Hazards', [
        ('list', '気をつけること',
         '・リアクターの潮汐帯（半径4）は防具を無視してダメージを受ける。\n'
         '・水素の容器が壊れると、火気の近くでは小爆発する。\n'
         '・粒子加速器を大きく回していると、まれにストレンジレットが漏れ、周りのブロックを毎秒1個ずつストレンジ物質に変えていく'
         '（半径8・最大512個で止まる。シールドの中には広がらない）。磁気瓶を使うと封じ込めて回収できる。',
         'Hazards',
         "- The reactor's tidal zone (radius 4) deals damage that ignores armour.\n- A broken hydrogen "
         'container causes a small explosion near fire.\n- A large particle accelerator may occasionally leak a '
         'strangelet, which turns one nearby block per second into strange matter (it stops at radius 8 or 512 '
         'blocks and does not spread inside a shield). Contain and collect it with a Magnetic Bottle.'),
    ]),
]
