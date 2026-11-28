"""Singulo ハンドブックの中身。

CHAPTERS は章の並び。各章は (id, アイコンのアイテムID, 日本語の題, 英語の題, ページの並び)。
各ページは (id, 日本語の見出し, 日本語の本文, 英語の見出し, 英語の本文)。本文の改行は段落の区切り。
「進み具合」の章は進捗（advancement）から画面側で作るので、ここには書かない。
"""

CHAPTERS = [
    ('intro', 'singulo:handbook', 'はじめに', 'Getting Started', [
        ('welcome', 'Singulo へようこそ',
         'まずは熱と冷たさを電力に変えてみよう。装置が動かなかったら、この本を開いてください。使い始めた技術に合わせて、読める項目が増えていきます。',
         'Welcome to Singulo',
         'Try turning a difference in temperature into power. When a machine gives you trouble, open this book. More topics appear as you gain experience.'),
        ('howto', 'このハンドブックの使い方',
         '左で章を選び、アイコンを押すと解説が開きます。右上は章の目次です。図の素材にカーソルを合わせると名前が出ます。\n'
         '持ち物にカーソルを合わせて H キーを押すと、読めるページから関連する説明を探せます。検索欄には装置の名前や困っている操作を入れてください。',
         'Using this book',
         'Pick a chapter and then an icon. The top-right button opens its contents. Hover over a diagram to identify a material.\n'
         'Hover over an item and press H to look it up in the pages available to you. You can also search for a machine or an operation.'),
        ('basics', '最初の配線と配管',
         '銅導線で発電機と加工装置をつなぎます。液体や気体には液体パイプを使います。\n'
         '流れないときは、装置の「面」で、送る側が出力、受ける側が入力になっているか確かめてください。',
         'Wiring and piping',
         'Connect the generator and machine with copper wire. Use fluid pipes for liquids and gases.\n'
         'If nothing moves, check Sides on both machines. Set the sending face to output and the receiving face to input.'),
    ]),
    ('stage1', 'singulo:thermoelectric_generator', '段階1 起動', 'Stage 1: Startup', [
        ('flow', '段階1の流れ',
         'まず鋼鉄と基礎回路を作り、熱電発電機を銅導線で焼成炉や圧縮機につなぎます。工場で作り方が見つからない部品に出会ったら、探索コンパスを手に外へ。\n'
         '冷却塔の準備には酸素製鋼が便利です。高さ7の塔が液体窒素を連続製造するときの目安は1,786 FE/t。給電が足りない間も加工の進み具合は保たれます。',
         'Stage 1 at a glance',
         'Start with steel and basic circuits. Wire a thermoelectric generator to a kiln or compressor. If a part has no manufacturing recipe, take an Explorer’s Compass outside.\n'
         'Oxygen steelmaking helps when building a cooling tower. A height-7 tower draws about 1,786 FE/t during continuous nitrogen production. It keeps its progress when power runs short.'),
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
         'FE/tを使います。給電不足なら進捗を保って待つので、初期は2〜4台で装置を交互に動かし、必要に応じて発電機や蓄電を増やします。定速運転を最初から満たす必要はありません。',
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
         'time. Add generation and storage as needed.'),
        ('machines', '段階1の装置',
         '焼成炉は焼く加工、圧縮機は板材の加工に使います。まず一回分だけ材料を入れ、電力と出力欄を確かめましょう。\n'
         '電解槽で水を分けるときは、両方の出力タンクに空きが必要です。気体を別々に運びたいときは、面設定でそれぞれの出口を選んでください。',
         'Stage 1 machines',
         'Use the kiln for firing and the compressor for pressing plates. Try one batch first and check the power buffer and output slot.\n'
         'When splitting water, leave room in both electrolyzer output tanks. To collect the gases separately, choose an output face for each.'),
        ('steel', '冷却塔用の鋼鉄を作る',
         '鋼鉄4個に使う石炭は、鋼の素なら8個、酸素製鋼なら2個です。\n'
         '電解槽で水バケツ1杯分を分解し、酸素500 mBを焼成炉へ。もう一方の水素1,000 mBは別のタンクに取っておけます。',
         'Make steel for the cooling tower',
         'Four steel ingots cost eight coal via Steel Blend or two with oxygen.\n'
         'Split a bucket of water and send the 500 mB of oxygen to the kiln. Store the remaining 1,000 mB of hydrogen in a separate tank.'),
    ]),
    ('stage2', 'singulo:superconducting_coil', '段階2 極低温', 'Stage 2: Cryogenic', [
        ('flow', '段階2の流れ',
         '冷却塔でできた素材の使い道を調べてみてください。精密組立台では配置も大切です。迷ったら、作りたい品のレシピを開いて盤面を見比べましょう。\n'
         '液体加工が始まらないときは、液体の種類、タンクの空き、塔の高さを確認してください。',
         'Stage 2 at a glance',
         'Check what your cooling tower’s products can make. Layout matters in the Precision Assembler. Open the recipe for your intended product and compare it with the board.\n'
         'If fluid processing stalls, check the fluid type, free tank space and tower height.'),
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
         'まずは一辺8のリングを投影して組み、水素を送ってみましょう。四隅は収束磁石で、リング全体の4分の1以上に磁石が必要です。\n'
         '加工が始まらなければ、コントローラがリングに接しているか、電力と出力の空きがあるかを確認します。',
         'Particle Accelerator and muon catalyst',
         'Start by projecting an eight-block ring and supplying it with hydrogen. Put focusing magnets at the corners and use them for at least a quarter of the ring.\n'
         'If it stalls, check that the controller touches the ring, power is available and the output has space.'),
        ('others', '精密組立台で配置を確かめる',
         '組立盤は四隅を除いた5×5です。配置図どおりに材料を重ねると、各マスから1個ずつ使って繰り返し組み立てます。JEIの「＋」で1回分、Shift＋クリックで置ける回数分を並べられます。\n'
         '触媒を使うレシピでは、触媒を専用の枠に入れ、残り寿命と、製品・失活触媒の両方の出力欄を確認してください。',
         'Checking the assembly board',
         'The 5×5 board has no corner cells. Stack parts in the pattern to repeat a craft. In JEI, click + to lay out one set, or Shift-click + for as many sets as fit.\n'
         'For catalytic recipes, use the dedicated catalyst slot. Check its remaining life and leave room for both the product and the spent catalyst.'),
    ]),
    ('stage3', 'singulo:quantum_computing_module', '段階3 量子', 'Stage 3: Quantum', [
        ('flow', '段階3の流れ',
         '量子の加工は電力と冷媒を一緒に使います。次の装置を増やす前に、一台で入力から出力まで試してみましょう。\n'
         '手元の記録や部品に用途が見当たらなければ、アーカイブ端末と探索コンパスをもう一度調べてみてください。',
         'Stage 3 at a glance',
         'Quantum processing uses both power and coolant. Run one machine through a full batch before expanding.\n'
         'If a recovered record or part has no obvious use, revisit the Archive Terminal and your Explorer’s Compass.'),
        ('quantum', '量子の装置',
         '・量子もつれ合成器: 量子もつれ素子を2個1組で作る。\n'
         '・レーザー冷却器: 冷却原子・光格子基板・BE凝縮触媒（ティア3）を作る。冷却原子トラップの復元もここ。\n'
         '・量子熱機関: 触媒で動く発電機。BE凝縮触媒で 200 kFE/t。1段下のミュオン触媒でも定格の25%（50 kFE/t）で仮運転でき、'
         'BE凝縮触媒を作るまでのつなぎになる。蓄電がいっぱいの間は待機して触媒を減らさない。',
         'Quantum machines',
         '- Entanglement Synthesizer: entangled elements, two at a time.\n- Laser Cooler: cold atoms, lattices, BE condensate catalyst.\n'
         '- Quantum Heat Engine: catalyst-driven generator, 200 kFE/t on BE catalysts; a muon catalyst runs it at 25% (50 kFE/t) as a stopgap. '
         'It waits without wearing the catalyst while its storage is full.'),
    ]),
    ('stage4', 'singulo:time_crystal_catalyst', '段階4 時間', 'Stage 4: Temporal', [
        ('flow', '段階4の流れ',
         '育成槽の純度が下がるときは、蓄電量だけでなく供給中の電力も見てください。要求電力の8割が目安です。\n'
         '大型設備はコントローラにホロ投影機を使い、足元の段から順に確かめましょう。',
         'Stage 4 at a glance',
         'If purity falls in the incubator, check the incoming power as well as stored energy. Aim for at least 80% of the requested rate.\n'
         'Use the Holo Projector on a large machine’s controller and check the structure from the bottom up.'),
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
         '時間結晶育成槽は、実際に動いた時間で触媒を育てます（8分）。要求の80%（24 kFE/t）以上の電力なら純度は落ちません。'
         'それを割った時間の割合だけ純度が下がり、寿命も短くなります。\n'
         '設備（炉心制御装置・特異点封入台・シールド発生塔コアなど）には、時間結晶を精密組立台で「時間結晶モジュール」に加工して使います。'
         '触媒枠の時間結晶から新品の寿命の2/3（既定の設定で30分）を切り出してモジュール4個にし、残り（既定で15分）の触媒はエキゾチック物質の加工やコアの材料に使えます。'
         '残りの寿命がそれに満たない触媒（使いかけ・純度の低いもの）からは作れません。寿命の設定やプリセットを変えると、切り出す量も比例して変わります。'
         '設備用と運転用の取り合いになるので、育成槽は2台あると楽です。\n'
         'C空洞（5×5×5 の真空容器、天面と底面の内側が鏡面）は、時間結晶触媒を使ってエキゾチック物質とアクシオン凝縮体を作ります。',
         'Time crystals and exotic matter',
         'The incubator grows time crystals over 8 minutes of real running time. At 80% of the requested power (24 kFE/t) or more purity is kept; '
         'time spent below that lowers purity and lifetime.\n'
         'Equipment (core controller, singularity encapsulator, shield tower core and more) takes Time Crystal Modules: the precision assembler cuts '
         'two thirds of a fresh crystal\'s life (30 minutes with default settings) into four modules from the crystal in its catalyst slot, and the rest (15 minutes by default) '
         'stays usable for exotic matter or as a core ingredient. Crystals with less life left than that (used or low-purity ones) cannot be cut; lifetime '
         'settings and presets scale the amount. Two incubators keep equipment and running needs from competing.\n'
         'The C-Cavity (5×5×5, mirror top and bottom) makes exotic matter.'),
        ('power', '大型発電と自動化',
         '縮退熱炉は蓄電が満杯になると待機します。停止しているように見えたら、搬入出ポートに電力の行き先がつながっているか確認してください。\n'
         '燃料は圧縮機の質量モードでまとめられます。炉の触媒枠も点検しましょう。',
         'Big power and automation',
         'The Degenerate Furnace waits when its energy buffer is full. If it looks idle, check the power connection at its multiblock port.\n'
         'Prepare compressed fuel in the Compressor’s mass mode and check the furnace catalyst slot.'),
    ]),
    ('stage5', 'singulo:singularity_core', '段階5 特異点', 'Stage 5: Singularity', [
        ('flow', '段階5の流れ',
         '大電力を一気に使うときは、蓄電池の容量と、時間内にケーブルを通せる電力を別々に確かめます。\n'
         '初めて起動する前に、制御画面で受電量と残り時間を確認しましょう。',
         'Stage 5 at a glance',
         'For a short, high-power operation, check two things: stored energy and how much the cables can deliver in time.\n'
         'Before your first start, check the incoming power and time limit on the controller.'),
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
    ]),
    ('multiblocks', 'singulo:holo_projector', 'マルチブロック', 'Multiblocks', [
        ('projector', 'ホロ投影機の使い方',
         'コントローラを置き、ホロ投影機で右クリックします。水色の枠には見本のブロックを置き、赤い枠は空けてください。必要数と不足数はチャットに出ます。\n'
         '大きさを選べる設備は、投影機を持ってスニークしながら使うと見本の大きさが変わります。',
         'Using the Holo Projector',
         'Place the controller and use a Holo Projector on it. Fill cyan outlines with the indicated blocks and clear red outlines. Chat shows the required and missing quantities.\n'
         'For a variable-size machine, sneak-use the projector to change the size of the preview.'),
    ]),
    ('catalysts', 'singulo:muon_catalyst', '触媒', 'Catalysts', [
        ('tiers', 'ティアと効き方',
         'ミュオン（2）・BE凝縮（3）・時間結晶（4）・シンギュラリティ・コア（5）。\n'
         '・装置と同じティア: 標準\n・1つ上: 速度+25%、減り−20%（2つ上まで重なる）\n・1つ下: 速度0.5倍、減り2倍（発電機は出力が定格の25%）\n・2つ下: 使えない\n'
         '残りが減るほど必要電力が増え（最大1.6倍）、電力が80%を割ると不完全反応で減りが1.5倍になります。\n'
         'この決まりは触媒枠で「運転」する装置（アンカー・スタビライザー・発電機・シールドなど）のものです。加工装置のレシピに材料として書かれた触媒は、'
         '寿命を決まった分だけ使います（丸ごと1個なら新品1個ぶん）。そのティア以上の触媒で払え、上位の触媒は1段ごとに減りが−20%・速さが+25%（2段まで）。'
         '下位の触媒では払えません。',
         'Tiers and effects',
         'Muon (2), BE condensate (3), time crystal (4), Singularity Core (5).\n- Same tier as the machine: '
         'standard\n- One tier higher: speed +25%, wear −20% (stacks up to two tiers higher)\n- One tier lower: '
         '×0.5 speed, ×2 wear (generators run at 25% of their rated output)\n- Two tiers lower: unusable\nAs a '
         'catalyst wears down, the power it needs rises (up to ×1.6); below 80% power, incomplete reactions '
         'make it wear ×1.5 faster.\nThese rules apply to devices that run on a catalyst slot (anchors, '
         'stabilizers, generators, shields and so on). When a processing recipe lists a catalyst as an '
         "ingredient, it uses a fixed amount of life (a whole catalyst means one fresh catalyst's worth). Any catalyst "
         'of that tier or higher can pay: each tier higher wears 20% less and works 25% quicker (up to two tiers). Lower '
         'tiers cannot pay.'),
        ('slots', '触媒スロットとリサイクル',
         '触媒を使う装置には、触媒だけが入る専用スロットがあります（画面で触媒のマークが付いた枠）。同じ触媒を16個まで予備として積めます。\n装填した触媒の残り寿命は、スロットの下のゲージで確認できます。使い切ると予備から次の触媒を装填します。失活触媒は精密組立台では「失活触媒」の枠、ほかの加工機では出力スロット、触媒装置では左の出口から取り出せます。精密組立台は、失活触媒が入らないときは枠が空くまで待ちます。\n使いかけの触媒を入れると、装填中のものと同じ種類なら残りをまとめ、空になった殻を失活触媒として出します。違う種類を入れると、装填中のものを出口へ返して入れ替えます。予備が空のときにスロットをクリックすると、装填中の触媒を取り出せます。\n失活触媒は触媒反応器で、1ティア下の触媒の材料として約25%を回収できます。',
         'Catalyst slots and recycling',
         'Machines that use catalysts have a dedicated slot that accepts only catalysts (the slot marked with a catalyst icon). It holds up to 16 of the same catalyst as a reserve.\nThe gauge below the slot shows the remaining life of the loaded catalyst. When it runs out, the machine loads the next catalyst from the reserve. Collect spent catalysts from the Spent slot on the Precision Assembler, the output slot on other processing machines, or the left exit on catalyst devices. The Precision Assembler waits if its spent catalyst will not fit.\nAdd a partly used catalyst of the same kind and its remaining life is merged into the loaded one; the empty shell comes out as a spent catalyst. Add a different kind and the loaded one goes to the exit and is replaced. Click the slot while the reserve is empty to take out the loaded catalyst.\nIn the Catalytic Reactor, about 25% of a spent catalyst can be recovered as material for the catalyst one tier lower.'),
        ('time', '時間の場',
         'Pリアクターの引力帯では時間が遅れ、触媒の減りが×0.5（装置は×0.8）。Tシリンダーの範囲では逆に×2。'
         '重なると打ち消し合います。触媒倉庫を炉の近くに置くと長持ちします。',
         'Time fields',
         "In a P-Reactor's pull zone time slows down: catalysts wear at ×0.5 (machines run at ×0.8). Inside a "
         'T-Cylinder field it is the reverse, ×2. The two cancel out where they overlap. Keeping catalyst '
         'storage near the reactor makes them last longer.'),
    ]),
    ('power', 'singulo:copper_wire', '電力と輸送', 'Power and Logistics', [
        ('cables', 'ケーブル',
         '発電機と装置を銅導線でつなぎます。同じ線を通る装置は、その線が1 tickに運べる電力を分け合います。\n'
         '蓄電があるのに装置が止まるときは、電源から順に細い区間がないか見てみましょう。',
         'Cables',
         'Wire the generator to the machine. Machines on the same cable share its transfer capacity each tick.\n'
         'If a machine stalls while the battery is charged, follow the line from the source and check for low-capacity sections.'),
        ('sides', '面ごとの設定',
         '装置の「面」で、送る面を出力、受ける面を入力にします。カーソルを合わせると、その面の隣にあるブロックが分かります。\n'
         '隣へ自動で送りたいときは、出力面と装置全体の自動排出を両方オンにします。電解槽は気体ごとに出す面を選べます。',
         'Side configuration',
         'Open Sides and set the sending face to output and the receiving face to input. Hover over a face to identify its neighbour.\n'
         'For automatic transfer, enable auto-eject on the output face and on the machine. An electrolyzer can send each gas through a different face.'),
        ('relocation', '移設レンチ',
         '移設レンチで対応する加工装置をスニーク＋右クリックすると、中身や面設定ごと回収できます。マルチブロックでは周囲の部材も別に運んでください。\n'
         '空中でスニーク＋右クリックすると、配線・配管を回収するモードに切り替わります。',
         'Relocation Wrench',
         'Sneak-use a Relocation Wrench on a supported machine to pack its contents and side settings with it. Move the surrounding multiblock parts separately.\n'
         'Sneak-use the wrench in the air to switch to cable and pipe removal.'),
        ('wormhole', 'ワームホール',
         '生成器で一対の口を作り、60秒以内に固定化装置でエキゾチック物質と口の筐体を使って固定化し、片方を運んで置きます。'
         '口から8ブロック以内のワームホール・ポートは、向こう側の口の近くにある同じポート番号（0〜127、画面の7つの丸で決める）のポートとつながり、'
         '電力・アイテム・液体を直結します。1つの口で働けるポートは喉の大きさで 8・32・128 個まで（口に近い順）で、働いているポートは光ります。'
         '通せる量も喉の大きさで決まり、7×7 では上限がなくなります。口はエキゾチック物質で維持し、切れると閉じて休みます。'
         '初めて置いたときは5分間、燃料なしで開いています。',
         'Wormholes',
         'Generate a pair of mouths, stabilize them within 60 s using exotic matter and a mouth casing, then carry one away and place it.'
         ' A port within 8 blocks of a mouth links to the port with the same number (0-127, set with the seven circles in its screen) near the twin mouth, joining power, items and fluids.'
         ' Each mouth runs 8, 32 or 128 ports depending on its throat size, nearest first, and active ports glow. Throughput also grows with the throat and is unlimited at 7x7.'
         ' Mouths run on exotic matter and close when it runs out; a newly placed mouth stays open for 5 minutes without fuel.'),
        ('capacity', '容量アップグレード',
         '加工装置の右レールの容量専用枠に1個入れると、蓄電容量が Mk1 で1,000,000、Mk2 で4,000,000、Mk3 で40,000,000 FE になります。'
         'Mk1 は段階2の素材を使って作業台で、Mk2・Mk3 は精密組立台で一つ下のアップグレードから作ります。速度アップグレードと一緒に使え、受電の上限は変わりません。\n'
         '外したときに容量を超えていた電力は消えず、容量より少なくなるまで新しい電力を受け取りません。1回に使う電力が通常の容量を超えるレシピには、容量アップグレードが要ります。',
         'Capacity upgrades',
         'One in the capacity slot on a processing machine’s right rail raises its buffer to 1,000,000 FE (Mk1), 4,000,000 FE (Mk2) or 40,000,000 FE (Mk3). '
         'Make Mk1 on a crafting table using stage 2 materials and Mk2 and Mk3 at a Precision Assembler from the previous upgrade. They work alongside speed upgrades and do not change the intake limit.\n'
         'Removing one keeps any charge above the new capacity; the machine accepts no new power until it falls below. Recipes that draw more than the normal buffer in one go need a capacity upgrade.'),
    ]),
    ('ruins', 'singulo:explorer_compass', '野外の手引き', 'Field Notes', [
        ('list', '針を頼りに歩く',
         '探索コンパスを手に持ち、空に向けて右クリックすると目標を探します。針を頼りに近づいたら、少し立ち止まって周りを見渡してみてください。',
         'Following the needle',
         'Hold an Explorer’s Compass and use it in the air to locate its target. When the needle brings you close, stop and look around.'),
        ('compass', '針が同じ場所を指すとき',
         '自分で開けた保管庫に、探索コンパスを持ってスニーク＋右クリックしてみてください。\n'
         '調整後は空中でスニーク＋右クリックすると探索先を切り替えられます。選んだら右クリックでもう一度探します。',
         'When the needle points back',
         'Take the compass back to a cache you have personally opened and sneak-use it on the cache.\n'
         'After tuning, sneak-use the compass in the air to switch targets. Use it again to take a new bearing.'),
        ('records', '持ち帰った記録を読む',
         '読めない記録が手元にあれば、電力をつないだアーカイブ端末で調べてみましょう。解読した記録は手に持って使うと、この本で読み返せます。',
         'Reading a recovered record',
         'If you find a record you cannot read, try it in a powered Archive Terminal. Use the decoded record in your hand to keep a copy in this book.'),
        ('antechamber', '閉ざされた隔壁の手前で',
         'いちばん深い遺構では、ボス部屋の手前の隔壁が閉ざされていることがあります。分かれた区画の奥にある中継器を探し、すべて起動すると隔壁が開きます。\n'
         '中継器の数は、起動したときに表示されます。開いた隔壁は閉じないので、再戦のときはまっすぐボス部屋へ向かえます。',
         'Before the sealed bulkhead',
         'In the deepest ruins, a bulkhead may seal the way to the boss room. Find the relay at the end of each wing and start them all to open it.\n'
         'Starting a relay shows how many are running. The bulkhead stays open, so rematches can head straight for the boss room.'),
    ]),
    ('androids', 'singulo:android_station', 'アンドロイド', 'Androids', [
        ('build', 'ステーションを使い始める',
         'ステーションの専用枠に手元の小型クリスタルメモリとキットを入れ、電力をつなぎます。キットは一回につき一個使い、メモリは繰り返し使えます。\n'
         '呼び戻したいときはステーションの画面を開いてください。充電や修理に戻る体力もここで設定できます。',
         'Using your station',
         'Place your Compact Crystal Memory and a kit in their station slots, then connect power. Each print uses one kit. You can reuse the memory.\n'
         'Open the station to recall your android or set the health threshold for returning to recharge and repair.'),
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
         'ステーションの動き方を「巡回」にして、巡回欄に作業位置カードを左から歩いてほしい順に置きます。\n'
         '戻り方は周回と往復から選べます。思った所を通らないときは、カードに記録した地点と、そこまで歩ける道を確かめましょう。',
         'Patrol',
         'Select Patrol at the station. Put Work Location Cards in the patrol slots from left to right in the order you want them visited.\n'
         'Choose a loop or a return trip. If a stop is skipped, check the recorded position and the walking route to it.'),
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
         '・資材口の優先: 資材口を右クリックすると、アイテムごとに優先順位（1がいちばん先）と、取り出し・返却のどちらに効くかを8件まで登録できる。'
         '登録した物は、順位の高い資材口から先に取り出し、先に戻す（いっぱいなら次の場所へ）。シフト右クリックで、つながっている指令台を表示。\n'
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
         '- Supply port priorities: right-click a supply port to register up to 8 items, each with a priority (1 goes first) for taking, '
         'returning or both. Registered items are taken from and returned to the highest-priority port first (the next one when it is '
         'full). Sneak-right-click shows the linked console.\n'
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
         '・農作業: 収穫（育ちきった作物を取って植え直す。サトウキビ・サボテンは根元を残す）、伐採（木を丸ごと切って苗木を植え直す。伐採アップグレードが要る）、畑づくり（土を耕して種・苗を植える。骨粉なら育ちかけの作物に与える。農業アップグレードが要る）、釣り（範囲の水で釣る。釣りアップグレードが要る）、繁殖（えさを与えて、決めた数まで増やす）、毛刈り・搾乳（空のバケツを'
         '持っていく）、処理（決めた数を超えた分）。名前付き・つながれた・飼われている動物と子どもには手を出さない。\n'
         '・操作: レバー（オン・オフ・切り替え）、ボタン、ドアなどを使う。仕掛けを動かす合図に。\n'
         '・液体・ガス: 資材口や指令台の隣のタンクも素材置き場になる。加工モードや手順の見本の欄を、バケツなどの入れ物でクリックするか JEI の「+」で、流体を置ける（ホイールで 100 mB ずつ、シフトで 1000 mB '
         'ずつ）。タンクアップグレードを積んだアンドロイドが、流体を装置へ運ぶ。流体を作る注文は、素材置き場のタンクへ入れる。\n'
         '・作業台で空のバケツが戻るレシピには、中身入りのバケツを記録する。実物が足りなければ、アンドロイドはバケツ1個につき対応する液体をタンクから1,000 mB使う。実物を使った分だけ空のバケツが戻る。\n'
         '・燃料: 加工モードの燃料の欄に燃料を置いて記録すると、装置の燃料が切れたとき、その燃料を素材置き場から取ってきて入れる（置いた数ずつか、欄の下のボタンで「満タン」にすると装置に入るだけ。どちらも入りきらない分は持ってこ'
         'ない。空なら取りに行かない）。素材置き場になく、レシピで作れるなら、燃料を作る注文を自動で出す（作るあいだ、アンドロイドはほかの作業をする）。\n'
         '・確率で出る物: できた数が足りなければ、足りない分の素材を素材置き場から取って、集まるまで作り直す（素材がなければ、そろうまで待つ）。収穫・伐採・牧場では、まだ育つ物があれば育つのを待つ（20分まで）。アメジストは育ち'
         'きった塊だけ取り、芽生えたアメジストは壊さない。\n'
         '・アンドロイド作業台: バニラの作業台の4倍の速さで作る。作った物がアンドロイドの持ち物に入りきらないときは、足元に落とさずに台の預かり箱へ入れ、あとで取りに来る（取りに来られなくなった物は、右クリックで受け取れる）。預かり箱に物があるあいだは、台の上にその物が浮かぶ。上位の改良型（8倍速・54枠・充電の減り6割）と量子（20倍速・81枠・充電の減り3割）は、前の段階の台を素材にして作る。',
         'Farming, fluids and fuel',
         '- Farming: Harvest (picks ripe crops and replants; sugar cane and cactus keep their base), Fell (cuts whole tr'
         'ees and replants saplings; needs a Felling Upgrade), Farm (tills soil and plants seeds, or gives bone meal to gr'
         'owing crops; needs a Farming Upgrade), Fish (fishes in an area of water; needs a Fishing Upgrade), Breed (fee'
         'ds animals up to a set number), Shear and Milk '
         '(empty buckets are taken along) and Cull (anything above a set number). Named, leashed and tamed animals and b'
         'abies are left alone.\n'
         '- Use: flips levers (on, off or toggle), presses buttons, opens doors and so on, to drive contraptions.\n'
         '- Liquids and gases: tanks next to supply ports or the console join the stockpile. Click a pattern slot in pro'
         "cessing or procedure mode with a bucket or other fluid container, or use JEI's + button, to place a fluid (scr"
         'oll for 100 mB steps, Shift for 1000 mB). Androids with a Tank Upgrade carry fluids to machines. Orders that m'
         'ake a fluid fill the stockpile tanks.\n'
         '- For a crafting table recipe that returns an empty bucket, record the filled bucket. For each filled bucket missing from stock, '
         'the android can use 1,000 mB of the matching fluid from a tank. Each physical bucket used returns one empty bucket.\n'
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
         "(right-click to take anything left behind). While the holding box has items, one of them floats above the workbench. "
         "The upgraded Advanced (8×, 54 slots, 60% charge use) and Quantum (20×, 81 slots, 30% charge use) workbenches are made from the previous tier."),
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
    ]),
    ('drones', 'singulo:drone_station', 'ドローン輸送', 'Drone Delivery', [
        ('start', 'ドローンを用意する',
         'ステーションに電力をつなぎ、手元のメモリとキットを専用の枠に入れます。発着ポートは輸送の出発点と受取先の両方に用意してください。',
         'Getting drones',
         'Power the station and place your memory and kit in their slots. Provide a Drone Port at both the sending and receiving ends.'),
        ('send', '荷物を送る',
         '行き先には、同じ次元にある同じ持ち主のアイテム用ポートが並びます。開いたワームホールの両端に航路標識があれば、別の次元のアイテム用ポートも選べます。ヘッダーの名前をクリックすると名前を付けられます。\n'
         '左上に空の梱包箱、真ん中に荷物を入れ、右の一覧で行き先を選んで「発送」を押すと、荷物を梱包して電力を払い、半径64ブロックのステーションから待機中のドローンを呼びます。'
         'ドローンが真上に来ると、天井の扉が開いて昇降台が箱を持ち上げ、ドローンの爪が掴みます。行き先でも扉が開いて箱を降ろし、受け取りの欄へ自動で開梱します。'
         '空になった箱は空き箱の欄へ戻ります。ドローンが来る前なら「取消」で元に戻せます（電力も戻る）。\n'
         'ホッパーやパイプで、空き箱と荷物を入れ、受け取りの欄から取り出せます。コンパレーターは受け取りの欄の埋まり具合を出します。',
         'Sending cargo',
         'Destinations are item ports with the same owner in this dimension. A loaded, open wormhole with a Drone Beacon at each mouth also lists matching ports in the far dimension. Click the name in the header to name the port.\n'
         'Put an empty packing crate top left and cargo in the middle, pick a destination on the right and press Send. The port packs the cargo, pays the power and calls '
         'a waiting drone from a station within 64 blocks. When the drone hovers overhead, the roof hatch opens, a lift raises the crate and the drone’s claws grab it. '
         'At the destination the hatch opens again, the crate is lowered and unpacked into the received row. Empty crates go back to the crate slot. '
         'Before the drone arrives, Cancel puts everything back (power included).\n'
         'Hoppers and pipes can insert crates and cargo and extract from the received row. A comparator reads how full the received row is.'),
        ('range', '射程と電力',
         '発送の電力は 2,000 FE＋距離1ブロックにつき 50 FE で、梱包時にポートが払います。直送はポート間の直線距離、ワームホール便は発送元から手前の口までと、向こうの口から行き先までの直線距離の合計で計算します。届く距離は梱包箱で決まります: 梱包箱160ブロック、'
         'バッテリー付き梱包箱760ブロック、大容量バッテリー付き梱包箱3,160ブロック。行き先の一覧で、届かない距離は赤く出ます。\n'
         'ドローンが読み込まれていない所を飛ぶ間も、荷物は失われません。行き先が読み込まれていれば着陸して降ろし、読み込まれていなければ、そのポートが読み込まれたときに受け取ります。'
         '直送の行き先が壊されると、荷物は発送元へ戻ります。戻せるポートがない場合は、行き先で梱包済みの箱が落ちます。',
         'Range and power',
         'The port pays 2,000 FE plus 50 FE per block when it packs the cargo. Direct flights use the straight-line distance between ports. Wormhole flights add the distance from the sender to the near mouth and from the far mouth to the destination. The crate decides how far it can go: Packing Crate 160 blocks, '
         'Battery Packing Crate 760, Large Battery Packing Crate 3,160. Destinations out of reach show their distance in red.\n'
         'Cargo is never lost while a drone flies through unloaded areas. If the destination is loaded the drone lands and unloads; if not, the port receives the crate when it loads. '
         'If the destination of a direct flight is gone, the drone takes the cargo back to the sending port. If there is no port to return it to, the packed crate drops at the delivery site.'),
        ('more', '自動搬出・輸送の様子・解体',
         '右レールの設定の段で、加工装置と同じ面の設定を開けます。面ごとに入出力を決め、自動排出をオンにすると、受け取った荷物を隣のチェストなどへ'
         '押し出します（空き箱の欄に入りきらなかった空の箱も）。\n'
         '画面の真ん中には、輸送中の便が出ます（出る便は橙、来る便は緑）。ドローン待ち・向かっている・積み込み・飛行中・荷降ろしの段階と、'
         '飛行中の進みのバーが、便ごとに見られます。\n'
         '発送できるのは、そのポートを受け持つドローン（半径64ブロックのステーションのドローン）がいるときだけです。全機が運んでいる間に発送した箱は'
         '積み込み待ちになり、近くで荷物を届け終えたドローンが、ステーションへ戻らずにそのまま取りに来ます。\n'
         'ステーションの画面で、待機中のドローンの行の×を2回押すと解体します。ドローンはステーションに降りて分解され、キットが戻ります。'
         'その枠は止まり、＋を押すとまた印刷します。',
         'Auto-export, shipments and dismantling',
         'The settings tab on the right rail opens the same side settings as processing machines. Choose input and output per face and turn on '
         'auto-eject to push received items into an adjacent chest or other storage (empty crates that do not fit the crate slot too).\n'
         'The middle of the screen lists shipments in transit: outgoing in orange, incoming in green. Each shows its stage (waiting, drone coming, '
         'loading, in flight, unloading) and a progress bar while in flight.\n'
         'You can only send while a drone serves the port (one from a station within 64 blocks). A crate sent while every drone is busy waits '
         'to be loaded, and a drone that finishes a delivery nearby flies straight to it instead of returning to its station.\n'
         'In the station screen, press the × on a waiting drone’s row twice to dismantle it: it lands on the station, comes apart and its kit '
         'comes back. That slot stops; press + to print again.'),
        ('route', '通り道と位相透過',
         'ドローンは壁や天井を抜けずに飛びます。近い行き先は、空いている所をたどる道を探し、遠い行き先は、出発点から空の見える所へ出て、'
         '地形より高い所を巡航し、行き先の上の空から降ります。建物の中のポートにも、扉や窓、天窓から入っていきます。'
         '開いた所のない部屋のポートには入れず、輸送中のページに「経路なし」と出ます（荷物は失われず、ドローンはその場で待って道を探し直します）。\n'
         '段階4の精密組立台で作る位相透過アップグレードを、ドローン・ステーションの枠の中の欄（生産アップグレードの下）に入れると、そのステーションのドローンは'
         '壁を抜けてまっすぐ近道します。壁を抜ける少し前から紫に光り、壁越しにも光る姿が見えます。',
         'Routes and phase shift',
         'Drones do not fly through walls or roofs. For nearby destinations they find a path through open space; for distant ones they leave '
         'to open sky, cruise above the terrain and come down from the sky above the destination. They enter ports inside buildings through '
         'doors, windows and skylights. A port in a room with no opening cannot be reached and shows No route in the In-transit page '
         '(the cargo is safe; the drone waits and searches again).\n'
         'Put a Phase Shift Upgrade (stage 4, Precision Assembler) in the slot inside a Drone Station’s frame (below the Production Upgrades) '
         'and its drones take shortcuts straight through walls. They glow violet from just before a wall, and the glow shows through it.'),
        ('auto', '自動発送',
         '発送のタブの下の段で、自動発送を使えます。しきい値のボタンを押すたびに、しない・1個・64個・256個・満杯（荷物の9欄すべてがいっぱい）と切り替わります。一覧で行き先を選んで「登録」を押すと、それが自動発送の行き先になります（手で送る行き先とは別に覚えます）。\n荷物の欄がしきい値までたまり、2秒たっても増えなければ（満杯ならすぐ）、登録した行き先へ自動で発送します。空き箱・射程・電力・ドローンの条件は手で送るときと同じです。ホッパーやパイプで荷物を入れ続ければ、採掘場などから拠点へ自動で運び続けられます。',
         'Auto-send',
         'The bottom row of the Send tab sets up auto-send. Each press of the threshold button cycles through off, 1, 64, 256 and full (all 9 cargo slots filled). Pick a destination in the list and press Set to make it the auto-send destination (kept separately from manual sends).\nWhen the cargo reaches the threshold and has not grown for 2 seconds (or at once when full), the port sends it to that destination. Crates, range, power and drones work as for manual sends. Keep feeding cargo with hoppers or pipes to ship from a mine to your base automatically.'),
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
         '核と同じ段に枠を置き、斜めの枠の上に柱を積みます。層別図で高さを見比べてください。\n'
         '構造が認識されたら、核を素手で使って受電と進み具合を確認できます。',
         'Building the Genesis Engine',
         'Place the frames level with the core and stack pylons on the diagonal frames. Compare their heights in the layer view.\n'
         'Once the structure forms, use the core with an empty hand to check power and progress.'),
        ('birth', '安定化中の給電',
         '警報が鳴ったら、核の画面のスイッチかレッドストーン信号で投入を一時停止します。警報が収まったら再開してください。\n'
         '停止中も維持用の電力を使います。進み具合と蓄電量は別々に確認しましょう。',
         'Power during stabilization',
         'When the alarm sounds, pause the feed with the core’s switch or a redstone signal. Resume once the alarm clears.\n'
         'Upkeep still draws power while paused. Check stored energy as well as progress.'),
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
        ('galaxies', '銀河を選ぶ・増やす',
         '次の核を準備するときは、安定化が終わる前に素材を持って核に使ってみてください。素材は一個ずつ消費されます。画面の窓にカーソルを合わせると、今の設定を確認できます。',
         'Choosing and adding galaxies',
         'When preparing another core, try using a material on it before stabilization finishes. Each use takes one item. Hover over the preview in the core screen to check its current settings.'),
    ]),
    ('hazards', 'minecraft:tnt', '危険', 'Hazards', [
        ('list', '気をつけること',
         '水素を扱う配管や容器は火元と離しておきましょう。新しい装置を試すときは周囲に空間を取り、操作画面に出る警報を確認してください。',
         'Hazards',
         'Keep hydrogen lines and containers away from open flames. Leave space around new machinery and watch the controller for warnings during the first run.'),
    ]),
]

# バランス調整後の増設・蓄電・自動回収の手順。
CHAPTERS.append(('factory_expansion', 'singulo:smes_module', '工場の増設', 'Factory Expansion', [
    ('probes', '探査機が待機しているとき',
     '探査機は登録した訪問先を順番に回ります。前回の訪問から時間がたっているか、回収物の出力欄に空きがあるかを確認してください。待機中は電力も触媒も使いません。',
     'When a probe is idle',
     'Probes visit registered destinations in order. Check the time since the last visit and leave room in the output for the collection. A waiting probe uses neither power nor catalyst.'),
]))

for _chapter in CHAPTERS:
    if _chapter[0] == 'factory_expansion':
        _chapter[4].append(('limits', '質量・出力・点火の目安',
            '金属の圧縮Lv2は質量81です。鉄・銅ブロックは1個で質量3なので、Lv1に3ブロック、Lv2に27ブロック（インゴット243個相当）を使います。鉄だけでなく銅も選べます。専用部品の熱電対・熱交換コアは用途が限られるので、最初から大量に作りすぎる必要はありません。\n'
            'Pリアクターは50 GFEを10秒以内に受け取って点火します。2 GFEのSMESモジュールなら容量上は25基が最低です。1基の出力上限は100 MFE/tなので、少なくとも3基を並列に接続し、全体を250 MFE/t以上で送れるようにします。\n'
            '質量500・スピン0の点火直後は、燃料供給を維持して約28.6 MFE/tです。2.1 GFE/tは転送上限で、最初からその量を発電するわけではありません。',
            'Mass, output and ignition',
            'Compressed metal Lv2 has mass 81. An iron or copper block has mass 3, so Lv1 uses 3 blocks and Lv2 uses 27 blocks, or 243 ingots. Copper can replace iron. Thermocouples and heat-exchange cores have limited uses, so large stockpiles are unnecessary.\n'
            'The P-Reactor needs 50 GFE within 10 seconds to ignite. At 2 GFE each, 25 SMES Modules are the capacity minimum. Each can output at most 100 MFE/t, so connect at least three in parallel and provide an aggregate transfer rate of at least 250 MFE/t.\n'
            'At initial mass 500 and spin 0, sustained generation with fuel supply is about 28.6 MFE/t. The 2.1 GFE/t transfer cap is not the initial generation rate.'))

# 0.15.0: 降着ジェット鍛造炉（創世炉の章のすぐあと）
CHAPTERS.insert([c[0] for c in CHAPTERS].index('genesis') + 1, ('jet_forge', 'singulo:jet_forge_core', '降着ジェット鍛造炉', 'Accretion Jet Forge', [
    ('build', '施設を組む',
     'コントローラの投影で土台とリングの位置を確認し、ビームの通り道と炉心の周囲は空けておきます。\n'
     '材料や冷媒を運ぶときは、土台の基礎を搬入出ポートに置き換えられます。',
     'Building the forge',
     'Project the structure from its controller to position the base and rings. Keep the beam path and the space around the core clear.\n'
     'For material and coolant transfer, replace foundation blocks with multiblock ports.'),
    ('run', '鍛造の流れ',
     '点火前に制御画面で必要な電力を確認してください。鍛造には材料、冷媒、完成品の空き枠が必要です。\n'
     '途中で電力が不足したら、蓄電量と送電経路を確かめてから給電を戻しましょう。',
     'How forging works',
     'Check the required power on the controller before ignition. A forging batch needs materials, coolant and a free product slot.\n'
     'If power runs short, check stored energy and the delivery route before restoring the supply.'),
    ('ingot', 'コラプサー・インゴット',
     'ブラックホールのジェットで鍛えた地金です。コラプサー内張り、事象の地平線倉庫、ワームホール航路標識のほか、鍛造炉の並列カセットやワームホール縫合拡張にも使います'
     '（共通タグ c:ingots/collapsar・ID singulo:collapsar_ingot）。\n'
     'コラプサー・ビレットは特異点封入台で、ネザライトインゴット・縮退物質殻・ジェット凝縮体4・エキゾチック物質2・ニホニウム板2から作ります。'
     '鍛造のレシピはデータパック（type: singulo:machine、station: accretion_jet_forge）で変えたり足したりできます。',
     'Collapsar Ingot',
     'Forge this ingot in a black hole jet. Use it for Collapsar Lining, the Event Horizon Vault, the Wormhole Drone Beacon, the Collapsar Parallel Cassette and the Wormhole Seam Upgrade '
     '(common tag c:ingots/collapsar, ID singulo:collapsar_ingot).\n'
     'Make the Collapsar Billet in the Singularity Encapsulator from a Netherite Ingot, a Degenerate Matter Shell, 4 Jet Condensate, '
     '2 Exotic Matter and 2 Nihonium Plates. Forging recipes can be changed or added with a datapack (type: singulo:machine, station: accretion_jet_forge).'),
]))

from cosmic_content import CHAPTER as _COSMIC_CHAPTER
CHAPTERS.append(_COSMIC_CHAPTER)
from logistics_content import CHAPTERS as _LOGISTICS_CHAPTERS
CHAPTERS.extend(_LOGISTICS_CHAPTERS)
from food_content import CHAPTER as _FOOD_CHAPTER
CHAPTERS.append(_FOOD_CHAPTER)
from cover_content import CHAPTER as _COVER_CHAPTER
CHAPTERS.append(_COVER_CHAPTER)
# 0.24.0 アンドロイドのプログラムのページ（アンドロイドの章の最後）
from program_content import GUIDE_PAGE as _PROGRAM_PAGE
next(c for c in CHAPTERS if c[0] == "androids")[4].append(_PROGRAM_PAGE)
