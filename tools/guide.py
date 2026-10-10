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
         'Singulo is a tech mod about extracting energy from mass. Start with thermoelectric power, climb through '
         'cryogenic, quantum and temporal tech, and finally run a spinning black hole.\n'
         'There are five stages. Read the stage chapters in order to know what to build next.'),
        ('howto', 'このハンドブックの使い方',
         '・左の章を選ぶと、右にページが出ます。下の矢印でページをめくります。\n'
         '・「進み具合」の章で、達成した目標と次の目標が分かります（進捗画面［L キー］の Singulo タブとも同じです）。\n'
         '・アイテムにカーソルを合わせると説明が出ます。Shift を押すと作り方と使い道も出ます。\n'
         '・マルチブロックは「ホロ投影機」をコントローラに使うと、正しい形が半透明で浮かび上がります。',
         'Using this book',
         '- Pick a chapter on the left; flip pages with the arrows.\n'
         '- The Progress chapter shows finished and next goals (same as the Singulo advancement tab).\n'
         '- Hover items for a description; hold Shift for recipes and uses.\n'
         '- Use the Holo Projector on a multiblock controller to see its shape.'),
        ('basics', '電力・触媒・遺構',
         '電力（FE）は自前のケーブルで運びます。アイテムと液体は装置の面ごとの設定と、他modのパイプで運びます。\n'
         '段階2から「触媒」が登場します。触媒は寿命のある消耗品で、触媒を入れる装置は触媒専用のスロットを持ちます。'
         'ティアが装置より1つ上なら速く、1つ下なら遅く減りも速く、2つ下は使えません。\n'
         'ワールドには旧文明の「遺構」があり、そこでしか手に入らない記録や部品があります。'
         '段階1から地表観測拠点を探しておきましょう。',
         'Power, catalysts and ruins',
         'Power (FE) travels through Singulo cables; items and fluids use per-side settings and other mods\' pipes.\n'
         'From stage 2 you use catalysts: consumables with a lifetime, placed in a dedicated catalyst slot. '
         'One tier above the machine is faster; one below is slower and wears faster; two below cannot be used.\n'
         'Ancient ruins hold records and parts you cannot craft. Start looking for Observation Posts early.'),
    ]),
    ('stage1', 'singulo:thermoelectric_generator', '段階1 起動', 'Stage 1: Startup', [
        ('materials', '最初の素材',
         '1. 鉄と石炭で「鋼の素」を作り、溶鉱炉で鋼鉄インゴットにする。\n'
         '2. 粘土・ネザー水晶・骨粉・砂で「未焼成セラミック」を作り、かまどでホワイトセラミック複合材にする。\n'
         '3. セラミック・レッドストーン・銅で「基礎回路」、鋼鉄とセラミックで「基礎フレーム」を作る。\n'
         'この3つ（鋼鉄・セラミック・回路）がほとんどの装置の材料です。',
         'First materials',
         '1. Iron + coal = steel blend; smelt it in a blast furnace.\n'
         '2. Clay, quartz, bone meal and sand make unfired ceramic; fire it into white ceramic.\n'
         '3. Make basic circuits and basic frames. Most machines need these.'),
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
         '段階2の極低温冷却塔（液体窒素で240 FE/t）を動かすには、マグマブロック＋青氷で4台以上、焚き火＋氷塊なら5台以上が目安です'
         '（銅導線の損失は含めていません）。',
         'Pairs that last',
         'Pairs whose cold block never melts (output of one generator):\n'
         '- Magma block + blue ice: about 76 FE/t (best)\n'
         '- Running kiln + blue ice: about 66 FE/t\n'
         '- Campfire + blue ice: about 56 FE/t\n'
         '- Campfire + packed ice: about 55 FE/t\n'
         'Lava is too hot: even blue ice turns into packed ice in about 40 s and keeps melting. Campfires with water, snow or ice melt them '
         'in under a minute.\n'
         'To run the stage 2 Cryogenic Cooling Tower (240 FE/t for liquid nitrogen), plan on 4 or more generators with magma + blue ice, or 5 or more with '
         'campfire + packed ice (before copper wire losses).'),
        ('machines', '段階1の装置',
         '・焼成炉: セラミック基板などを焼く。鋼鉄も速く焼ける。\n'
         '・圧縮機: 鋼板を作る。質量モードでは質量値のある素材を「質量」に変えて圧縮ブロックや質量ペレットにする。\n'
         '・電解槽: 水を水素と酸素に分ける。\n'
         '・アーカイブ端末: 遺構の記録をデータカードに書き写し、劣化した部品を修復する。クリスタルメモリの解読もできる。',
         'Stage 1 machines',
         '- Kiln: fires ceramic boards and steel.\n- Compressor: plates, and mass mode for compressed blocks.\n'
         '- Electrolyzer: water to hydrogen and oxygen.\n- Archive Terminal: copies records, restores parts, decodes fragments.'),
        ('goal', '段階2へ進むには',
         '段階2の鍵は「超伝導コイル」です。これは極低温冷却塔で作り、材料に地表観測拠点で拾う「制御ユニット」が要ります。\n'
         '・地表観測拠点（平原・砂漠などの地表）を探し、保管庫から劣化した制御ユニットと観測ログを持ち帰る。\n'
         '・アーカイブ端末で制御ユニットを修復する。\n'
         '・冷却塔の部品をそろえ、ホロ投影機で組み立てる。',
         'Reaching stage 2',
         'The key is the Superconducting Coil, made in the Cryogenic Cooling Tower. It needs a Control Unit from an '
         'Observation Post. Find one, restore the unit in the Archive Terminal, then build the tower.'),
    ]),
    ('stage2', 'singulo:superconducting_coil', '段階2 極低温', 'Stage 2: Cryogenic', [
        ('tower', '極低温冷却塔',
         '5×5 の土台から、くびれた双曲面の塔を高さ7〜15で建てます。中心の列は熱交換コア、てっぺんは通気格子でふさぎます。'
         'コントローラは手前の面の下から2段目の中央。高いほど速く、高さ10以上で液体ヘリウムも作れます。\n'
         '何も入れなくても空気から液体窒素を作ります。銅・レッドストーン・鋼板・基板・制御ユニットと液体窒素で超伝導コイルを作ります。',
         'Cryogenic Cooling Tower',
         'A waisted 5×5 tower, 7–15 high, with heat exchange cores up the middle. '
         'It makes liquid nitrogen from air and superconducting coils.'),
        ('accelerator', '粒子加速器とミュオン触媒',
         '加速管と収束磁石で正方形のリング（一辺8〜32）を作り、コントローラをリングに接して置きます。四隅は収束磁石、'
         '収束磁石はリングの1/4以上。水素からミュオン束を作り、ごく稀に磁気単極子が生まれます。\n'
         '触媒反応器でミュオン束からミュオン触媒（ティア2）を作ります。宇宙線ミュオン収集器を高所に置けば、電力なしでもミュオン束が少しずつ集まります。',
         'Particle Accelerator and muon catalyst',
         'Build a square ring of tubes and magnets (8–32 wide), magnets on the corners and on at least a quarter of the ring. '
         'It turns hydrogen into muon bundles (and rare monopoles). The Catalytic Reactor turns them into muon catalysts.'),
        ('others', 'そのほかの段階2の装置',
         '・精密組立台: 段階3以降の部品と装置はほぼすべてここで作ります。\n'
         '・極低温タービン: 液体窒素の気化で発電（5 kFE/t）。\n'
         '・SMESセル: 大量の電力をためる。\n'
         '・ワールドライン・アンカー（小）: 触媒を入れると周りのチャンクを読み込み続ける。\n'
         '・単極子アップグレード: 磁気単極子から作り、装置の単極子スロットに挿すと速度×2・電力効率+50%。',
         'Other stage 2 devices',
         '- Precision Assembler: makes almost everything from stage 3 on.\n- Cryogenic Turbine: power from evaporating nitrogen.\n'
         '- SMES Cell: stores energy.\n- Small Worldline Anchor: chunk loader fed by catalysts.\n'
         '- Monopole Upgrade: ×2 speed, +50% efficiency in a machine\'s monopole slot.'),
        ('goal', '段階3へ進むには',
         '段階3の鍵は「量子演算モジュール」と液体ヘリウムです。冷却塔を高さ10以上にし、圧縮機で深層岩を砕いて出るヘリウムを液化します。'
         '研究棟（地表〜浅い地下）で量子データ片も集めましょう。',
         'Reaching stage 3',
         'You need liquid helium (tower 10+ high, helium from crushed deepslate) and quantum data from Research Buildings.'),
    ]),
    ('stage3', 'singulo:quantum_computing_module', '段階3 量子', 'Stage 3: Quantum', [
        ('quantum', '量子の装置',
         '・量子もつれ合成器: 量子もつれ素子を2個1組で作る。\n'
         '・レーザー冷却器: 冷却原子・光格子基板・BE凝縮触媒（ティア3）を作る。冷却原子トラップの復元もここ。\n'
         '・量子熱機関: 触媒で動く発電機。触媒のティアが高いほど出力が上がる。',
         'Quantum machines',
         '- Entanglement Synthesizer: entangled elements, two at a time.\n- Laser Cooler: cold atoms, lattices, BE condensate catalyst.\n'
         '- Quantum Heat Engine: catalyst-driven generator.'),
        ('tools', '道具と探索',
         '・慣性制御ガントレット: 右クリック長押しで小さなモブを浮かせる・引き寄せる。電力で動き、電力を持つブロックにスニーク＋右クリックで充電。\n'
         '・慣性スタビライザー: 半径16の中で爆発がブロックを壊さない。\n'
         '・重力波検出器: 最終実験施設（重力異常点）のおおよその方角と距離帯を示す。\n'
         '・ニュートリノ・スキャナー: 地形越しに遺構と鉱石の輪郭を10秒間見せる。はじめは鉄やレッドストーンまでで、'
         '感度モジュール（Mk2: 金・ダイヤモンド・エメラルド、Mk3: 古代の残骸）を使うと深い層の鉱石も映る。観測所にも使える。',
         'Tools and exploration',
         '- Inertial Control Gauntlet: levitate or pull small mobs.\n- Inertial Stabilizer: explosion-proofs a 16-block radius.\n'
         '- Gravitational Wave Detector: points to anomalies.\n- Neutrino Scanner: shows ruins and ores through terrain.'),
        ('goal', '段階4へ進むには',
         '段階4の鍵は「エキゾチック物質」です。C空洞で作り、時間結晶触媒が要ります。'
         '封鎖培養施設（ディープスレート層）で時間結晶の種と培養データを探しましょう。極低温区画と強い警備機に注意。',
         'Reaching stage 4',
         'The key is exotic matter from the C-Cavity, which needs time crystal catalysts. Search the deep Culture Facility.'),
    ]),
    ('stage4', 'singulo:time_crystal_catalyst', '段階4 時間', 'Stage 4: Temporal', [
        ('compression', '圧縮の連鎖',
         '圧縮機の質量モードで質量値のある素材を「質量」に変え、圧縮ブロックLv1→Lv2→Lv3 と固めます。'
         '3種類以上の元ブロックから作った Lv1 は「混成」になり、8個で Lv2 にできます。金属だけを固めた金属圧縮ブロックもあります。\n'
         '縮退圧縮炉（5×5×5 の閉じたプレス）で、縮退物質殻やリアクターの炉殻を作ります。',
         'Compression chain',
         'Mass mode turns materials with mass values into compressed blocks, Lv1 to Lv3. Lv1 made from 3+ kinds of blocks is "mixed" and '
         'only 8 are needed for Lv2. The Degenerate Compactor (closed 5×5×5 press) makes degenerate shells.'),
        ('time', '時間結晶とエキゾチック物質',
         '時間結晶育成槽は、実際に動いた時間で触媒を育てます（20分）。電力が足りないと純度が下がり、寿命も短くなります。\n'
         'C空洞（5×5×5 の真空容器、天面と底面の内側が鏡面）は、時間結晶触媒を使ってエキゾチック物質とアクシオン凝縮体を作ります。',
         'Time crystals and exotic matter',
         'The incubator grows time crystals over 20 minutes of real running time; underpowering lowers purity. '
         'The C-Cavity (5×5×5, mirror top and bottom) makes exotic matter.'),
        ('power', '大型発電と自動化',
         '・縮退熱炉（5×5×7）: 圧縮ブロックLv2 を押しつぶして 20 MFE/t。時間結晶触媒を使う。電力は搬入出ポートから自動で出る。\n'
         '・自動探査機ステーション: 発見済みの地表観測拠点へ探査機を飛ばし、回収物を半分の量で持ち帰る。\n'
         '・トポロジカル導線と SMESモジュールで大電力を運び、ためる。',
         'Big power and automation',
         '- Degenerate Furnace (5×5×7): 20 MFE/t from Lv2 blocks; power leaves through I/O ports.\n- Probe Station: automates trips to discovered ruins.\n'
         '- Topological wire and SMES modules for large power.'),
        ('goal', '段階5へ進むには',
         '最終実験施設（重力異常点）で封印コンソールに触れると、守護機ホライズン・ウォーデンが目覚めます。倒すと保管庫が開き、'
         '「休眠した特異点の種」が手に入ります。特異点封入台で目覚めさせ、Pリアクターに入れて点火します。',
         'Reaching stage 5',
         'Wake and defeat the Horizon Warden at the Final Lab to get a dormant singularity seed, awaken it in the '
         'encapsulator and ignite it in the P-Reactor.'),
    ]),
    ('stage5', 'singulo:singularity_core', '段階5 特異点', 'Stage 5: Singularity', [
        ('reactor', 'Pリアクターを組む',
         '炉心の中心を通る3つの直交面に、半径6の円環を途切れずにつなげて作ります（各48ブロック）。3本が交わる6点はジャイロ駆動部、'
         '各円環の斜め45°の点（計12）は炉心安定化コイル、残りは炉殻ブロック（抽出ポートと警報器に交換可）、中心の 3×3×3 は空気。炉心制御装置は中心の5ブロック下です。'
         'ホロ投影機を炉心制御装置に使えば、全体が浮かび上がります。',
         'Building the reactor',
         'Three continuous orthogonal rings of radius 6 (48 blocks each): gyro drives where they cross, twelve stabilizer '
         'coils at the 45° points, reactor shell elsewhere (replace shell blocks with extraction ports or mass alarms), an empty 3×3×3 core. The core controller sits 5 below the centre.'),
        ('ignite', '点火と運転',
         '種を入れて「点火」を押し、10秒以内に 50 GFE をポートかコントローラに注ぎます（ホライズン・バスが必要）。'
         '燃料は質量ペレット。降着効率はスピンで決まり（5.7〜42.3%）、炉心質量5000・最大スピンで約 2.1 GFE/t。\n'
         'リングの外（半径24まで）は弱い引力、リングの内側は逃げられない強い引力で、黒い球に触れたものはどんなに頑丈でも必ず消えます。半径4は防具を無視する潮汐帯です。引力帯の中は時間が遅れ、装置が×0.8、触媒の減りが×0.5になります。',
         'Ignition and operation',
         'Insert the seed, press Ignite and deliver 50 GFE within 10 s. Burn mass pellets; efficiency depends on spin. '
         'Mind the 24-block pull zone (time runs slower there) and the 4-block tidal zone.'),
        ('byproducts', '副産物とシンギュラリティ・コア',
         'ジェット・コレクター・Hコレクター・エルゴスフィア・リングを入れると、運用モードに応じて'
         'ジェット凝縮体・H凝縮体・エキゾチック物質が生まれます。H凝縮体などからシンギュラリティ・コア（ティア5の触媒）を作れます。',
         'By-products and the Singularity Core',
         'Collectors produce jet condensate, H-Condensate and exotic matter. The Singularity Core is the tier 5 catalyst.'),
        ('endgame', 'その先',
         'シンギュラリティ・コアと大電力で「特異点技術」が動かせます（特異点技術の章）。'
         'イベントホライズン・シールドで拠点を守り、Tシリンダーで時間を速め、ワームホールで遠くの拠点をつなぎましょう。',
         'Beyond',
         'With cores and power you can run the late-game devices: shields, T-Cylinders and wormholes.'),
    ]),
    ('multiblocks', 'singulo:holo_projector', 'マルチブロック', 'Multiblocks', [
        ('projector', 'ホロ投影機の使い方',
         '1. コントローラを置く（正面が自分の方を向く。形は奥へ伸びる）。\n'
         '2. ホロ投影機でコントローラを右クリックすると、正しい形が投影され、必要な数と足りない数がチャットに出る。\n'
         '3. 水色の枠に見本のブロックを置き、赤い枠のブロックを取り除く。全部そろうと投影が消え、継ぎ目に光が走って起動する。\n'
         '4. 大きさを選べる装置（冷却塔・加速器）は、スニークして使うと大きさが切り替わる。\n'
         'クリエイティブでは /singulo build <名前> [大きさ] で一発で組み立てられます。',
         'Using the Holo Projector',
         '1. Place the controller; the structure extends behind it.\n2. Use the projector on it to see the shape and a material list.\n'
         '3. Fill the cyan boxes, clear the red ones. It lights up when complete.\n4. Sneak-use to change size.\n'
         'In creative: /singulo build <name> [size].'),
        ('list', 'マルチブロック一覧',
         '・極低温冷却塔（5×5、高さ7〜15）: cooling_tower\n・粒子加速器（リング8〜32）: particle_accelerator\n'
         '・縮退圧縮炉（5×5×5）: degenerate_compactor\n・C空洞（5×5×5）: casimir_cavity\n'
         '・縮退熱炉（5×5×7）: degenerate_furnace\n・Pリアクター（13×13×13）: penrose_reactor\n'
         '・イベントホライズン・シールド発生塔（5×5、高さ9）: event_horizon_shield\n・Tシリンダー（5×5×9）: tipler_cylinder\n'
         '・ワームホール生成器（5×5×5の球）: wormhole_generator',
         'Multiblock list',
         'cooling_tower, particle_accelerator, degenerate_compactor, casimir_cavity, degenerate_furnace, penrose_reactor, '
         'event_horizon_shield, tipler_cylinder, wormhole_generator.'),
    ]),
    ('catalysts', 'singulo:muon_catalyst', '触媒', 'Catalysts', [
        ('tiers', 'ティアと効き方',
         'ミュオン（2）・BE凝縮（3）・時間結晶（4）・シンギュラリティ・コア（5）。\n'
         '・装置と同じティア: 標準\n・1つ上: 速度+25%、減り−20%（2つ上まで重なる）\n・1つ下: 速度0.5倍、減り2倍\n・2つ下: 使えない\n'
         '残りが減るほど必要電力が増え（最大1.6倍）、電力が80%を割ると不完全反応で減りが1.5倍になります。',
         'Tiers and effects',
         'Muon (2), BE (3), Time crystal (4), Singularity core (5). Higher tier = faster and less wear; one lower = half speed and double wear; '
         'two lower = unusable. Underpowering wears catalysts faster.'),
        ('slots', '触媒スロットとリサイクル',
         '触媒を使う装置には、触媒だけが入る専用スロットがあります（画面で触媒のマークが付いた枠）。ほかのアイテムは入りません。\n'
         '使い切った触媒は失活触媒になります。触媒反応器で、1ティア下の触媒の材料として約25%を回収できます。',
         'Catalyst slots and recycling',
         'Catalyst machines have a dedicated slot that accepts only catalysts. Spent catalysts can be recycled into about 25% of a lower-tier catalyst.'),
        ('time', '時間の場',
         'Pリアクターの引力帯では時間が遅れ、触媒の減りが×0.5（装置は×0.8）。Tシリンダーの範囲では逆に×2。'
         '重なると打ち消し合います。触媒倉庫を炉の近くに置くと長持ちします。',
         'Time fields',
         'Near a running reactor catalysts wear at ×0.5; inside a T-Cylinder field ×2. The two cancel out.'),
    ]),
    ('power', 'singulo:superconducting_cable', '電力と輸送', 'Power and Logistics', [
        ('cables', 'ケーブル',
         '銅導線（2 kFE/t、損失あり）→ 超伝導ケーブル（1 MFE/t）→ トポロジカル導線（100 MFE/t）→ ホライズン・バス（上限なし。ほかの mod の装置へは1台あたり約2.1 GFE/t まで）。'
         'つながったケーブルの容量は、いちばん細いところで決まります。',
         'Cables',
         'Copper wire → superconducting cable → topological wire → horizon bus. A network carries what its weakest cable can.'),
        ('sides', '面ごとの設定',
         '通常の加工装置の画面右上の「面」ボタンで、6つの面それぞれを「無効・入力・出力・入出力」に切り替えられます。'
         '出力の面には、できたものを自動で押し出す（自動排出）設定もあります。マルチブロックは指定位置の搬入出ポートを使い、Pリアクターは抽出ポートを使います。'
         '2種類の気体を出す装置（電解槽など）は、気体ごとに出す面を決められます。画面右の電源ボタンで装置を止められ、'
         '電力だけで作れるもの（冷却塔の液体窒素など）がある装置には、それを作るかどうかのスイッチがあります。',
         'Side configuration',
         'Use the Sides button in an ordinary processing machine screen to set each face to off / input / output / both, with optional auto-eject. Multiblocks use designated I/O ports; the P-Reactor uses Extraction Ports.'
         ' Machines that output two gases, such as the electrolyzer, have a tab for each gas. The power button stops a machine, and machines that can make something from power alone have a switch to skip those recipes.'),
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
        ('list', '4つの遺構',
         '・地表観測拠点: 平原・砂漠などの地上に建つ、ただ1つの地上の遺構。白い警備ドローン。観測ログ・制御ユニット（7日で再生）。\n'
         '・研究棟: 浅い地下（Y−20〜4あたり）。警備機と、奥の記録保管室の残響の番人。冷却原子トラップ・量子データ片（7日）。\n'
         '・封鎖培養施設: 深い地下（Y−46〜−30あたり）。極低温コアと強い警備機、奥の隔離槽室の重力の澱。時間結晶の種・培養データ（14日）。\n'
         '・最終実験施設: いちばん深い地下（Y−56〜−46あたり）の重力異常点。守護機ホライズン・ウォーデン。特異点の種・アノマリー・サンプル（14日）。',
         'The four ruins',
         'Observation Post, Research Building, Culture Facility, Final Lab — each guards loot for one catalyst tier.'),
        ('bosses', '遺構の番人',
         '研究棟・封鎖培養施設・最終実験施設の保管庫は、それぞれの番人を倒すまで力場で封鎖されています。中身が再生すると封鎖し直され、番人も戻ってきます。'
         '番人は部屋から挑戦者がいなくなると元に戻ります。\n'
         '・残響の番人（研究棟の記録保管室）: 部屋に入ると現れる警備ホログラム。宙を回りながら追いかける光弾を撃ち、'
         'しばらく見ていないと背後へ跳んで斬りつけてきます（跳ぶ前に光が集まる）。残響投影器は番人を守るだけでなく、一斉に光の槍も撃ちます'
         '（金色の狙いの線が出たら動くこと）。足元に光の陣が映ったら、柱が立つ前に陣の外へ。投影器が残っているほど攻撃が通りにくいので、先に壊すこと。'
         '体力が半分を切ると、1撃で消える分身を呼びます。投影器の光は本体にしか伸びません。番人の投影核を落とします。\n'
         '・重力の澱（封鎖培養施設の隔離槽室）: 封じ込め槽を破って出てくる黒い核。瓦礫の殻がある間は1撃ごとに殻が1つ剥がれる代わりにダメージが小さく、'
         '殻の瓦礫を撃ち出します。周りの相手を浮かせてから床へ叩きつけるので、重力ブーツや衝撃発電パネルがあると楽です。'
         '体力が半分を切ると重力井戸ができ、核へ引き寄せられます（核に触れると傷つく）。縮退核を落とします。\n'
         '・ホライズン・ウォーデン（最終実験施設）: 中央の封印コンソールに触れると起動します。',
         'Ruin guardians',
         'The caches of the Research Building, Culture Facility and Final Lab stay sealed until you defeat their guardian. '
         'When the loot regrows the cache reseals and the guardian returns; guardians also reset if every challenger leaves.\n'
         '- Echo Sentinel (Research Building archive): a security hologram that orbits you firing homing bolts and blinks behind you '
         'to strike when you stop watching it. The Echo Projectors in the corners shield it and fire light lances in volleys '
         '(move when the golden aim lines appear); step out of a projected circle before its pillar rises. Break the projectors first. Below half health it '
         'summons one-hit echoes; projector beams only reach the real one. Drops Sentinel Projection Cores.\n'
         '- Gravity Remnant (Culture Facility containment chamber): a black core that bursts from its tank. While its rubble shell lasts '
         'it takes reduced damage and loses a piece per hit; it fires the rubble at you. It lifts nearby foes and slams them down, '
         'so Gravity Boots or Impact Generators help. Below half health a gravity well drags you toward the core. Drops Degenerate Nuclei.\n'
         '- Horizon Warden (Final Lab): touch the central Seal Console to wake it.'),
        ('compass', '探索コンパスの調整',
         '探索コンパスは、はじめは地表観測拠点しか探せません。攻略した（保管庫を自分で開けた）遺構の保管庫をスニークして'
         '右クリックすると、そこに残る記録で調整され、次の遺構（研究棟 → 封鎖培養施設 → 最終実験施設）も探せるようになります。'
         '地下の遺構は、針の指す場所の下を掘って探します。',
         'Tuning the Explorer Compass',
         'At first the compass only finds Observation Posts. Sneak-use it on the cache of a ruin you have cleared to tune it '
         'for the next one: Research Building, then Culture Facility, then Final Lab. Underground ruins lie below the spot it points to.'),
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
         'Ruin caches sometimes hold crystal memories. Decode them in the Archive Terminal to unlock the Ancient Records chapter.'),
        ('gathering', 'その場所でしか採れない素材',
         '・隕石クレーター（平原・砂漠・森などの地表）: 差し渡し10ブロックほどの浅いすり鉢の中心に、半分埋まった隕石があります。'
         '隕鉄塊（鉄以上のツルハシ）から隕鉄の原石、黒い溶融殻に育つ星屑の結晶から星屑が採れます。星屑の結晶は、クレーターに最初からある殻にだけ、少しずつ育ち直します。\n'
         '・雷ガラス: 雷が砂に落ちると、砂が雷ガラス塊になります。避雷針を砂の上に立てておくと、雷雨のたびに避雷針の下にできます。'
         '段階3のケラウノス放電塔があれば、電力で人工の雷を避雷針へ撃って、いつでも作れます。\n'
         '・高圧結晶（段階3）: 深海圧力収集器を、真上に水が16ブロック以上ある海底に置くと、水圧で少しずつできます（深いほど速い）。\n'
         '・虚空の塵（段階4〜5）: 虚空捕集器を、ジ・エンドで真下が奈落まで何もない所に置くと集まります。',
         'Materials found in one place',
         '- Meteor craters (plains, deserts, forests and other surface biomes): a shallow bowl about 10 blocks across with a half-buried meteorite. '
         'Mine meteoric iron chunks (iron pickaxe or better) for raw meteoric iron and stardust clusters on the black crust for stardust. '
         'Clusters slowly regrow, but only on crust still in its crater.\n'
         '- Fulgurite: lightning striking sand turns it into fulgurite. Stand a lightning rod on sand and it forms under the rod in thunderstorms. '
         'The stage 3 Keraunos Discharge Tower fires artificial lightning at a rod on demand.\n'
         '- Pressure crystals (stage 3): a Deep-Sea Pressure Collector on the sea floor with 16+ blocks of water above slowly makes them; deeper is faster.\n'
         '- Void dust (stages 4-5): a Void Collector in the End, placed over open void, gathers it.'),
    ]),
    ('romance', 'singulo:tipler_core', '特異点技術', 'Singularity Tech', [
        ('shield', 'イベントホライズン・シールド',
         '半径32の中を守る塔（5×5、高さ9）。時間結晶触媒で爆発と荒らしを防ぎ、シンギュラリティ・コアなら敵の湧きと侵入も止める。電力16,384 FE/t。',
         'Event Horizon Shield', 'Protects a 32-block radius. With a Singularity Core it also blocks hostile spawns.'),
        ('tipler', 'Tシリンダー',
         '半径16の時間を×2にする（5×5×9）。燃料と触媒はコアの画面から入れる。時間結晶触媒・エキゾチック物質（1分に1個）・100 kFE/t。',
         'T-Cylinder', 'Doubles supported machine processing within 16 blocks.'),
        ('gear', '身につける重力技術',
         '・メトリック・ドライブ: 持ち物に入れて、低重力・無重力（飛行）・高重力を切り替える。\n'
         '・グラビトン・マニピュレーター: 浮遊・牽引・斥力・圧壊。G キーで円錐範囲。\n'
         '・ワールドライン・アンカー（上位）: コアを埋め込むと消費なしで半径5。',
         'Gravity gear', 'Metric Drive, Graviton Manipulator, Advanced Worldline Anchor.'),
        ('darkmatter', 'ダークマター',
         'ハロー捕集器を炉心から16ブロック以内に置くと集まる。普通の入れ物からは漏れるので、重力閉じ込めタンクに入れる。'
         '炉心に直接入れると、エディントン限界を受けずに質量になる。',
         'Dark matter', 'Collect near a core, store in containment tanks, feed the core directly.'),
    ]),
    ('hazards', 'minecraft:tnt', '危険', 'Hazards', [
        ('list', '気をつけること',
         '・リアクターの潮汐帯（半径4）は防具を無視してダメージを受ける。\n'
         '・水素の容器が壊れると、火気の近くでは小爆発する。\n'
         '・粒子加速器を大きく回していると、まれにストレンジレットが漏れ、周りのブロックを毎秒1個ずつストレンジ物質に変えていく'
         '（半径8・最大512個で止まる。シールドの中には広がらない）。磁気瓶を使うと封じ込めて回収できる。',
         'Hazards',
         '- Tidal zone ignores armour.\n- Leaking hydrogen near fire explodes.\n'
         '- Large accelerators may leak strangelets that convert blocks into strange matter; contain them with a Magnetic Bottle.'),
    ]),
]
