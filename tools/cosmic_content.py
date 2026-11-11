"""0.16.0 progression, authored UI text and native model overrides."""
IDS = {
    '引力拘束フレーム': ('cosmic_frame', 'Gravitational Confinement Frame', 'part_block'),
    '核パスタ': ('nuclear_pasta', 'Nuclear Pasta', 'item'),
    '核パスタ積層板': ('pasta_laminate', 'Nuclear Pasta Laminate', 'part_block'),
    '重力懸垂タンク': ('suspension_reservoir', 'Suspension Reservoir', 'machine'),
    '核パスタ積層蓄電庫': ('pasta_accumulator', 'Nuclear Pasta Accumulator', 'machine'),
    '潮汐紡糸機': ('tidal_spindle', 'Tidal Spindle', 'machine'),
    '潮汐繊維': ('tidal_filament', 'Tidal Filament', 'item'),
    'ワームホール縫合拡張': ('wormhole_seam_upgrade', 'Wormhole Seam Upgrade', 'item'),
    'コラプサー並列カセット': ('collapsar_parallel_cassette', 'Collapsar Parallel Cassette', 'item'),
    '重力懸垂冠': ('suspension_crown', 'Suspension Crown', 'part_block'),
    '懸垂支柱': ('suspension_strut', 'Suspension Strut', 'part_block'),
    '核パスタ導体': ('pasta_conductor', 'Pasta Conductor', 'part_block'),
    '潮汐絞りリング': ('tidal_draw_ring', 'Tidal Draw Ring', 'part_block'),
    'スプール受け台': ('spindle_cradle', 'Spool Cradle', 'part_block'),
}

def recipes(R, shaped):
    R('引力拘束フレーム',3,'作業台',{'ニホニウム板':4,'ガラス':4,'超伝導コイル':1},out=4,cat='part')
    shaped['引力拘束フレーム']=(('PGP','GCG','PGP'),{'P':'ニホニウム板','G':'ガラス','C':'超伝導コイル'})
    R('重力懸垂タンク',3,'作業台',{'ニホニウム筐体':1,'無重力ブロック':2,'量子もつれ素子':2,'超伝導コイル':4},cat='machine')
    shaped['重力懸垂タンク']=(('CQC','GFG','CQC'),{'C':'超伝導コイル','Q':'量子もつれ素子','G':'無重力ブロック','F':'ニホニウム筐体'})
    R('核パスタ',5,'降着ジェット鍛造炉',{'縮退物質殻':1,'質量ペレット':32,'液体ヘリウム':2000},t=90,fe=125_000_000,
      note='既存の SMES と P リアクターで製造できる。新しい蓄電庫は不要。照射の電力は 225 GFE。')
    R('核パスタ積層板',5,'精密組立台',{'核パスタ':1,'引力拘束フレーム':1,'ニホニウム板':2,'エキゾチック物質':1},out=4,t=10,fe=200_000,cat='part')
    R('核パスタ積層蓄電庫',5,'作業台',{'ニホニウム筐体':1,'核パスタ':4,'縮退物質殻':1,'ホライズン・バス':1,'超伝導コイル':2},cat='machine')
    shaped['核パスタ積層蓄電庫']=(('PHP','CFC','PSP'),{'P':'核パスタ','H':'ホライズン・バス','C':'超伝導コイル','F':'ニホニウム筐体','S':'縮退物質殻'})
    R('潮汐紡糸機',5,'作業台',{'ニホニウム筐体':1,'コラプサー・インゴット':2,'重力場安定化ユニット':1,'超伝導コイル':4,'エキゾチック物質':1},cat='machine')
    shaped['潮汐紡糸機']=(('CQC','SFS','CIC'),{'C':'超伝導コイル','Q':'重力場安定化ユニット','S':'コラプサー・インゴット','F':'ニホニウム筐体','I':'エキゾチック物質'})
    R('潮汐繊維',5,'潮汐紡糸機',{'コラプサー・ビレット':1,'エキゾチック物質':1,'液体ヘリウム':500},out=4,t=60,fe=20_000_000,
      note='最初に特異点の種と 10 GFE で封じ込め核を形成する。素材・冷却材・成果物を予約して保存する。')
    # 0.17.0: 潮汐力で素材を伸ばしてバニラの素材を作る（冷却材なし、材料は左の枠1つ。核の形成は必要）
    tidal = '潮汐力で引き伸ばす。材料は左の枠に入れる（エキゾチック物質と液体ヘリウムは使わない）'
    R('糸（潮汐紡糸）',5,'潮汐紡糸機',{'羊毛':1},out=4,t=2,fe=1_000_000,note=tidal)
    R('鎖（潮汐紡糸）',5,'潮汐紡糸機',{'鉄インゴット':1},out=2,t=3,fe=1_000_000,note=tidal)
    R('ブレイズロッド（潮汐紡糸）',5,'潮汐紡糸機',{'ブレイズパウダー':4},t=5,fe=1_000_000,note=tidal)
    R('ファントムの皮膜（潮汐紡糸）',5,'潮汐紡糸機',{'革':1},t=5,fe=1_000_000,note=tidal)
    R('鍾乳石（潮汐紡糸）',5,'潮汐紡糸機',{'石':1},out=2,t=2,fe=1_000_000,note=tidal)
    R('ワームホール縫合拡張',5,'精密組立台',{'潮汐繊維':4,'コラプサー・インゴット':4,'量子もつれ素子':2},t=20,fe=200_000,cat='part')
    R('コラプサー並列カセット',5,'精密組立台',{'コラプサー・インゴット':4,'超伝導コイル':2,'量子もつれ素子':2,'ニホニウム板':4},t=20,fe=200_000,cat='part')
    # 無重力ブロックを超伝導コイルで挟み、鋼の冠で囲む
    R('重力懸垂冠',3,'作業台',{'ニホニウム板':4,'超伝導コイル':2,'無重力ブロック':1},out=4,cat='part')
    shaped['重力懸垂冠']=(('PCP',' G ','PCP'),{'P':'ニホニウム板','C':'超伝導コイル','G':'無重力ブロック'})
    # 枠を板で縦に継いだ細い柱
    R('懸垂支柱',3,'作業台',{'ニホニウム板':2,'引力拘束フレーム':1},out=6,cat='part')
    shaped['懸垂支柱']=(('P','F','P'),{'P':'ニホニウム板','F':'引力拘束フレーム'})
    # ホライズン・バスをコイルで巻いた母線
    R('核パスタ導体',5,'作業台',{'ホライズン・バス':1,'超伝導コイル':2},out=4,cat='part')
    shaped['核パスタ導体']=(('C','H','C'),{'C':'超伝導コイル','H':'ホライズン・バス'})
    # 真ん中が空いた輪そのものの並べ方
    R('潮汐絞りリング',5,'作業台',{'ニホニウム板':4,'超伝導コイル':4},out=8,cat='part')
    shaped['潮汐絞りリング']=(('PCP','C C','PCP'),{'P':'ニホニウム板','C':'超伝導コイル'})
    # 軸受（コイル）をフレームで受け、板の足で支える
    R('スプール受け台',5,'作業台',{'引力拘束フレーム':2,'超伝導コイル':1,'ニホニウム板':2},out=4,cat='part')
    shaped['スプール受け台']=(('PFP',' C ',' F '),{'P':'ニホニウム板','F':'引力拘束フレーム','C':'超伝導コイル'})


UI = {
 'multiblock.singulo.suspension_reservoir':('重力懸垂タンク','Suspension Reservoir'),
 'multiblock.singulo.pasta_accumulator':('核パスタ積層蓄電庫','Nuclear Pasta Accumulator'),
 'multiblock.singulo.tidal_spindle':('潮汐紡糸機','Tidal Spindle'),
 'jei.singulo.mb.suspension_reservoir':('7×9×7。八角の冠から流体を吊る。容量 16,000,000 mB。','7×9×7. Suspends fluid from an octagonal crown. Capacity: 16,000,000 mB.'),
 'jei.singulo.mb.pasta_accumulator':('7×9×7。容量 8 TFE、入出力は各 2 GFE/t。','7×9×7. Capacity: 8 TFE. Input and output: 2 GFE/t each.'),
 'jei.singulo.mb.tidal_spindle':('7×8×15。起動は特異点の種 + 10 GFE。加工は 20 MFE/t。','7×8×15. Start: Singularity Seed + 10 GFE. Processing: 20 MFE/t.'),
 'gui.singulo.device.forge.cassette':('コラプサー並列カセットを入れると、同じレシピを最大 4 バッチ同時に加工する','Holds a Collapsar Parallel Cassette, which processes up to four batches of the same recipe together'),
 'gui.singulo.device.forge.batch':('同時加工: %s バッチ。材料・冷却材・電力は数量分を消費する','Parallel processing: %s batches. Materials, coolant and power scale with the batch count'),
 'gui.singulo.cosmic.state.0':('構造完成','Structure complete'),
 'gui.singulo.cosmic.state.1':('構造が未完成','Structure incomplete'),
 'gui.singulo.cosmic.state.2':('構造の読み込み待ち','Waiting for structure chunks'),
 'gui.singulo.cosmic.rate_in':('入力 %s/t','Input %s/t'),
 'gui.singulo.cosmic.rate_out':('出力 %s/t','Output %s/t'),
 'gui.singulo.cosmic.phase.0':('ニョッキ相: 核物質が粒に分かれている','Gnocchi phase: nuclear matter in separate drops'),
 'gui.singulo.cosmic.phase.1':('スパゲッティ相: 粒がつながって棒になった','Spaghetti phase: drops have joined into rods'),
 'gui.singulo.cosmic.phase.2':('ラザニア相: 棒が広がって板になった','Lasagna phase: rods have spread into sheets'),
 'gui.singulo.cosmic.helium':('液体ヘリウム %s / %s mB','Liquid Helium %s / %s mB'),
 'gui.singulo.cosmic.slot.seed':('特異点の種: 核を作る','Singularity Seed: forms the core'),
 'gui.singulo.cosmic.slot.billet':('コラプサー・ビレット、または引き伸ばす素材（羊毛・鉄インゴット・ブレイズパウダー・革・石）','Collapsar Billet, or a material to stretch (wool, iron ingot, blaze powder, leather or stone)'),
 'gui.singulo.cosmic.slot.exotic':('エキゾチック物質','Exotic Matter'),
 'gui.singulo.cosmic.slot.output':('巻き上がった繊維','Wound filament'),
 'gui.singulo.cosmic.core.on':('封じ込め核あり','Confined core active'),
 'gui.singulo.cosmic.empty':('空','Empty'),
 'gui.singulo.cosmic.amount':('%s / %s mB','%s / %s mB'),
 'gui.singulo.cosmic.port.left':('左のポート: %s','Left port: %s'),
 'gui.singulo.cosmic.port.right':('右のポート: %s','Right port: %s'),
 'gui.singulo.cosmic.port.mode.0':('入力','Input'),
 'gui.singulo.cosmic.port.mode.1':('出力','Output'),
 'gui.singulo.cosmic.port.mode.2':('入出力','Input and output'),
 'gui.singulo.cosmic.port.click':('クリックで切り替え','Click to change'),
 'gui.singulo.cosmic.seed':('起動: 特異点の種 + 10 GFE','Start: Singularity Seed + 10 GFE'),
 'gui.singulo.pipe.idle':('配管: 待機（流体の供給なし）','Pipe: idle (no fluid offered)'),
 'gui.singulo.pipe.flow':('配管: %s mB / 5 tick','Pipe: %s mB / 5 ticks'),
 'gui.singulo.pipe.blocked':('配管: 転送停止（空き・適合する入力を確認）','Pipe: blocked (check space and compatible inputs)'),
 'gui.singulo.pipe.stale':('配管: 観測データ待ち','Pipe: awaiting observation'),
 'gui.singulo.pipe.mixed':('複数の流体','Multiple fluids'),
 'gui.singulo.device.mouth.passage.0':('物流用の口。歩いて通るには、両端の口にワームホール縫合拡張を右クリックする','Logistics mouth. To walk through, use a Wormhole Seam Upgrade on both mouths'),
 'gui.singulo.device.mouth.passage.1':('もう片方の口にも拡張を付け、両端を開いて読み込むと通行できる','Install the upgrade on the other mouth too, then keep both open and loaded to pass'),
 'gui.singulo.device.mouth.passage.2':('通行可能。出口が危険な場合は移動しない','Passage ready. Unsafe exits prevent travel'),
}

CHAPTER = ('cosmic','singulo:nuclear_pasta','拘束と縫合','Confinement and Seams',[
 ('storage','吊り下げる・圧縮する',
  '新設備の制御卓の左右は、決まった位置の搬入出ポートです。ケーブル、パイプ、ホッパーはポートへつなぎ、それぞれ入力、出力、入出力を制御卓の画面のボタンで切り替えます。はじめは正面から見て左が入力、右が出力です。制御卓にホログラム投影機を使うと、部品の配置が分かります。構造が崩れたり一部が未ロードになると停止し、中身を保持します。移設レンチで内容ごと運べます。制御卓を採掘した場合も、内容入りの 1 個のアイテムになります。\n'
  '重力懸垂タンクは 7×9×7、一般の液体・気体を 16,000,000 mB 保持します。流体は八角の冠から涙の形にぶら下がり、増えるほど伸びて膨らみます。既存の流体パイプをポートへそのまま接続できます。Mekanism の別 API の chemical は対象外です。\n'
  '核パスタ積層蓄電庫は 7×9×7。容量は有限の 8 TFE、入力と出力は各 2 GFE/t です。受け取った電力だけを蓄えます。充電につれて押し板が締まり、中の核パスタが粒、棒、板と形を変えます。',
  'Hanging storage, compressed layers',
  'The two blocks beside each controller are fixed I/O ports. Connect cables, pipes and hoppers to them, and use the buttons in the controller screen to set each one to input, output or both. By default, seen from the front, the left port is input and the right is output. Use the Holo Projector on a controller to see its structure. Broken or unloaded structures pause and keep their contents. The Relocation Wrench packs the contents; mining a controller also produces one filled item.\n'
  'The 7×9×7 Suspension Reservoir holds 16,000,000 mB of ordinary liquids or gases, hanging from its octagonal crown as a drop that lengthens and swells as it fills. Connect the existing fluid pipe directly to a port. Mekanism chemicals using a separate API are not supported.\n'
  'The 7×9×7 Nuclear Pasta Accumulator has finite capacity: 8 TFE, with 2 GFE/t input and output. It stores only received power. As it charges, its press plates close and the nuclear pasta inside changes from drops to rods to sheets. '),
 ('production','潮汐力から糸へ',
  '核パスタは既存の Jet Forge で作ります。縮退物質殻 1 個、質量ペレット 32 個、液体ヘリウム 2,000 mB を使い、90 秒間 125 MFE/t で照射します（照射だけで 225 GFE）。新しい蓄電庫を先に作る必要はありません。\n'
  '潮汐紡糸機は 7×8×15。特異点の種を核のスロットに入れ、10 GFE を蓄えると封じ込め核を形成します。コラプサー・ビレット 1 個、エキゾチック物質 1 個、液体ヘリウム 500 mB を 60 秒間 20 MFE/t で加工し、潮汐繊維 4 本を巻き取ります。材料・冷却材・成果物・進行を保存し、出力が満杯なら待ちます。\n核があれば、左の枠の素材を潮汐力で引き伸ばしてバニラの素材も作れます（1 MFE/t、冷却材は不要）。羊毛 1 → 糸 4、鉄インゴット 1 → 鎖 2、ブレイズパウダー 4 → ブレイズロッド 1、革 1 → ファントムの皮膜 1、石 1 → 鍾乳石 2。\n左のポートから電力、冷却材、材料を入れ、右のポートから繊維を取り出せます。',
  'From tides to thread',
  'Make Nuclear Pasta in the existing Jet Forge: 1 Degenerate Matter Shell, 32 Mass Pellets and 2,000 mB liquid helium, irradiated for 90 seconds at 125 MFE/t (225 GFE for irradiation alone). No new accumulator is needed first.\n'
  'The Tidal Spindle is 7×8×15. Put a Singularity Seed in its core slot and store 10 GFE to form the confined core. Process 1 Collapsar Billet, 1 Exotic Matter and 500 mB liquid helium for 60 seconds at 20 MFE/t to wind 4 Tidal Filaments. Ingredients, coolant, output and progress are reserved and saved. A full output pauses completion.\nOnce the core is formed, the spindle can also stretch vanilla materials from its left slot (1 MFE/t, no coolant): 1 Wool → 4 String, 1 Iron Ingot → 2 Chains, 4 Blaze Powder → 1 Blaze Rod, 1 Leather → 1 Phantom Membrane, 1 Stone → 2 Pointed Dripstone.\nFeed power, coolant and materials through the left port, and take filament from the right port.'),
 ('links','縫合と並列照射',
  '潮汐繊維とコラプサー材でワームホール縫合拡張を作り、既存の口の両端に右クリックで取り付けます。両端が開いて読み込まれていると、口の上の四角い開口部を歩いて通れます。物流はそのまま使えます。出口に安全な足場と空間がない場合、移動しません。乗り物に乗っている場合も移動しません。移動直後は再転送を防ぐ待ち時間があります。\n'
  'コラプサー並列カセットを鍛造炉の画面の右の枠に入れるか、手に持って鍛造炉のどこかを右クリックして取り付けると、同じレシピを最大 4 バッチ同時に加工します。開始時に数量を固定し、材料・冷却材・電力を数量分使います。すでに進行中の処理は取り付けても変わりません。\n'
  '測量メガネを掛けると、近くの流体パイプに転送方向の矢印が見えます。見ている配管の転送量・種類・停止状態も表示します。矢印は実際の端点間転送を結ぶ代表経路を示し、分岐や環状配管の全経路を表すものではありません。パイプ自体は流体を蓄えません。',
  'Seams and parallel irradiation',
  'Make a Wormhole Seam Upgrade from Tidal Filament and Collapsar material, then use one on each existing mouth. When both ends are open and loaded, walk through the square opening above a mouth. Existing logistics remain available. Travel is refused without safe footing and space at the exit, or while riding. A cooldown prevents immediate return trips.\n'
  'Put a Collapsar Parallel Cassette in the right-hand slot of the Jet Forge screen, or use it on any part of the forge, to process up to four batches of the same recipe together. The count is locked at the start, and materials, coolant and power scale with it. Installing a cassette does not change a run already in progress.\n'
  'Wear Survey Glasses to see transfer arrows on nearby fluid pipes, plus the amount, fluid and blocked state of the pipe you look at. Arrows show one representative connected route for actual endpoint transfers, rather than every path through branches or loops. Pipes do not store fluid.'),
])

DESC = {
 '引力拘束フレーム':('宇宙現象を封じ込める設備の土台と梁になる、筋交い入りの重い枠。','A heavy braced frame used for the bases and beams of cosmic containment facilities.'),
 '重力懸垂冠':('重力懸垂タンクの八角の冠。下面の発光の環が液体を吊る。','The octagonal crown of the Suspension Reservoir. The glowing ring on its underside holds the fluid up.'),
 '懸垂支柱':('重力懸垂タンクの冠を持ち上げる細い柱。','A slender strut that lifts the crown of the Suspension Reservoir.'),
 '核パスタ導体':('核パスタ積層蓄電庫の四隅に立つ、巻線の母線。','Wound bus bars at the four corners of the Nuclear Pasta Accumulator.'),
 '潮汐絞りリング':('潮汐紡糸機の胴と絞りの輪。糸を細く絞っていく。','Forms the core drum and draw rings of the Tidal Spindle, narrowing the thread.'),
 'スプール受け台':('潮汐紡糸機のスプールを支える軸受の台。','Bearing stands that hold the spool of the Tidal Spindle.'),
 '核パスタ':('既存の降着ジェット鍛造炉で作る、封じ込めた層状の核物質。新しい蓄電庫なしで製造できる。','Confined layers of nuclear matter made in the existing Accretion Jet Forge. No new accumulator is required.'),
 '核パスタ積層板':('核パスタ積層蓄電庫の上下の土台になる、層を重ねた板。','Layered plates that form the top and bottom of the Nuclear Pasta Accumulator.'),
 '重力懸垂タンク':('7×9×7 の設備。八角の冠から 16,000,000 mB の液体・気体を涙の形に吊る。左右のポートで入出力する。','A 7×9×7 facility that hangs 16,000,000 mB of liquids or gases from its octagonal crown as a single drop. Its two ports can each be set to input, output or both.'),
 '核パスタ積層蓄電庫':('7×9×7 の設備。容量 8 TFE、入出力は各 2 GFE/t。充電につれて押し板が締まり、核パスタが粒、棒、板と形を変える。左右のポートで入出力する。','A 7×9×7 facility storing 8 TFE, with 2 GFE/t input and output. Charging closes its press plates and turns the nuclear pasta from drops to rods to sheets; discharge flows through the corner conductors. Its two ports can each be set to input, output or both.'),
 '潮汐紡糸機':('7×8×15 の設備。特異点の種と 10 GFE で核を形成し、潮汐力で素材を伸ばして繊維を巻く。加工には 20 MFE/t と液体ヘリウムが必要。羊毛から糸、鉄から鎖、革からファントムの皮膜なども引き伸ばして作れる。停止・未ロード中は予約内容を保持する。','A 7×8×15 facility. Form its core with a Singularity Seed and 10 GFE, then stretch matter into wound filament using tidal forces. Processing needs 20 MFE/t and liquid helium. It can also stretch wool into string, iron into chains, leather into phantom membranes and more. Reserved work survives stops and unloaded chunks.'),
 '潮汐繊維':('ブラックホールの潮汐力で引き伸ばした発光繊維。コラプサー材とともに、通行用のワームホールを縫い開く。','Luminous filament stretched by black-hole tidal forces. Combined with Collapsar material, it stitches open a traversable wormhole.'),
 'ワームホール縫合拡張':('既存のワームホールの口へ右クリックして取り付ける。両端が拡張済みで開いていると、上の四角い開口部を歩いて通れる。物流は維持され、安全な足場がない出口へは移動しない。','Use on an existing wormhole mouth to install. When both upgraded ends are open, walk through the square opening above it. Logistics remain available, and travel requires a safe landing.'),
 'コラプサー並列カセット':('鍛造炉の画面の右の枠に入れるか、手に持って鍛造炉を右クリックして取り付け、同じレシピを最大 4 バッチ同時に加工する。開始時に数量を固定し、材料・冷却材・電力を数量分使う。','Put it in the right-hand slot of the Jet Forge screen, or use it on any part of the forge, to process up to four batches of the same recipe together. The count is locked at the start; materials, coolant and power scale with it.'),
}

SUMMARY = {
 '引力拘束フレーム':('設備の土台と梁になる枠。','A frame for facility bases and beams.'),
 '重力懸垂冠':('タンクの八角の冠。','The octagonal crown of the reservoir.'),
 '懸垂支柱':('冠を持ち上げる細い柱。','A slender strut that lifts the crown.'),
 '核パスタ導体':('蓄電庫の四隅の母線。','Corner bus bars for the accumulator.'),
 '潮汐絞りリング':('紡糸機の胴と絞りの輪。','The drum and draw rings of the spindle.'),
 'スプール受け台':('スプールを支える台。','A stand that holds the spool.'),
 '核パスタ':('Jet Forge で作る層状の核物質。','Layered nuclear matter made in the Jet Forge.'),
 '核パスタ積層板':('蓄電庫の上下の板。','The top and bottom plates of the accumulator.'),
 '重力懸垂タンク':('天井から流体を吊る大型タンク。','A large tank that suspends fluid from above.'),
 '核パスタ積層蓄電庫':('層を圧縮して蓄える有限容量の蓄電庫。','Finite energy storage in compressed layers.'),
 '潮汐紡糸機':('潮汐力で素材を伸ばし、光る糸を巻く。','Stretches matter with tidal forces and winds luminous thread.'),
 '潮汐繊維':('通行用ワームホールを縫う光る繊維。','Luminous filament for stitching traversable wormholes.'),
 'ワームホール縫合拡張':('両端の口を拡張し、歩いて通行する。','Upgrade both mouths to walk between them.'),
 'コラプサー並列カセット':('Jet Forge を最大 4 バッチへ拡張する。','Process up to four Jet Forge batches together.'),
}

def models(ctx):
    import cosmic_assets
    cosmic_assets.models(ctx)
