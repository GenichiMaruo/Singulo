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
    R('ワームホール縫合拡張',5,'精密組立台',{'潮汐繊維':4,'コラプサー・インゴット':4,'量子もつれ素子':2},t=20,fe=200_000,cat='part')
    R('コラプサー並列カセット',5,'精密組立台',{'コラプサー・インゴット':4,'超伝導コイル':2,'量子もつれ素子':2,'ニホニウム板':4},t=20,fe=200_000,cat='part')

UI = {
 'multiblock.singulo.suspension_reservoir':('重力懸垂タンク','Suspension Reservoir'),
 'multiblock.singulo.pasta_accumulator':('核パスタ積層蓄電庫','Nuclear Pasta Accumulator'),
 'multiblock.singulo.tidal_spindle':('潮汐紡糸機','Tidal Spindle'),
 'jei.singulo.mb.suspension_reservoir':('5×7×5。天井から流体を吊る。容量 16,000,000 mB。','5×7×5. Suspends fluid from above. Capacity: 16,000,000 mB.'),
 'jei.singulo.mb.pasta_accumulator':('7×7×7。容量 8 TFE、入出力は各 2 GFE/t。','7×7×7. Capacity: 8 TFE. Input and output: 2 GFE/t each.'),
 'jei.singulo.mb.tidal_spindle':('5×7×13。起動は特異点の種 + 10 GFE。加工は 20 MFE/t。','5×7×13. Start: Singularity Seed + 10 GFE. Processing: 20 MFE/t.'),
 'gui.singulo.device.forge.batch':('同時加工: %s バッチ。材料・冷却材・電力は数量分を消費する','Parallel processing: %s batches. Materials, coolant and power scale with the batch count'),
 'gui.singulo.cosmic.state.0':('構造完成','Structure complete'),
 'gui.singulo.cosmic.state.1':('構造が未完成','Structure incomplete'),
 'gui.singulo.cosmic.state.2':('構造の読み込み待ち','Waiting for structure chunks'),
 'gui.singulo.cosmic.rates':('入力 %s / 出力 %s MFE/t','In %s / Out %s MFE/t'),
 'gui.singulo.cosmic.empty':('空','Empty'),
 'gui.singulo.cosmic.amount':('%s / %s mB','%s / %s mB'),
 'gui.singulo.cosmic.ports':('制御卓の正面が出力、他の面が入力','Controller front: output. Other faces: input'),
 'gui.singulo.cosmic.seed':('起動: 特異点の種 + 10 GFE','Start: Singularity Seed + 10 GFE'),
 'gui.singulo.pipe.idle':('配管: 待機（流体の供給なし）','Pipe: idle (no fluid offered)'),
 'gui.singulo.pipe.flow':('配管: %s mB / 5 tick','Pipe: %s mB / 5 ticks'),
 'gui.singulo.pipe.blocked':('配管: 転送停止（空き・適合する入力を確認）','Pipe: blocked (check space and compatible inputs)'),
 'gui.singulo.pipe.stale':('配管: 観測データ待ち','Pipe: awaiting observation'),
 'gui.singulo.pipe.mixed':('複数の流体','Multiple fluids'),
 'gui.singulo.device.mouth.passage.0':('物流用の口','Logistics mouth'),
 'gui.singulo.device.mouth.passage.1':('通行には両端の拡張と接続が必要','Passage needs both upgrades and a connected partner'),
 'gui.singulo.device.mouth.passage.2':('通行可能。出口が危険な場合は移動しない','Passage ready. Unsafe exits prevent travel'),
}

CHAPTER = ('cosmic','singulo:nuclear_pasta','拘束と縫合','Confinement and Seams',[
 ('storage','吊り下げる・圧縮する',
  '新設備は制御卓にホログラム投影機を使うと、部品の配置が分かります。構造が崩れたり一部が未ロードになると停止し、中身を保持します。移設レンチで内容ごと運べます。制御卓を採掘した場合も、内容入りの 1 個のアイテムになります。\n'
  '重力懸垂タンクは 5×7×5、一般の液体・気体を 16,000,000 mB 保持します。天井から下へ流体が増えます。制御卓の正面が出力、他の面が入力で、既存の流体パイプをそのまま接続できます。Mekanism の別 API の chemical は対象外です。\n'
  '核パスタ積層蓄電庫は 7×7×7。容量は有限の 8 TFE、入力と出力は各 2 GFE/t です。受け取った電力だけを蓄え、層が圧縮されます。制御卓の正面が出力、他の面が入力です。',
  'Hanging storage, compressed layers',
  'Use the Holo Projector on a controller to see its structure. Broken or unloaded structures pause and keep their contents. The Relocation Wrench packs the contents; mining a controller also produces one filled item.\n'
  'The 5×7×5 Suspension Reservoir holds 16,000,000 mB of ordinary liquids or gases, hanging from its ceiling. The controller front drains; other faces fill. Connect the existing fluid pipe directly. Mekanism chemicals using a separate API are not supported.\n'
  'The 7×7×7 Nuclear Pasta Accumulator has finite capacity: 8 TFE, with 2 GFE/t input and output. It stores only received power and compresses its layers as it charges. The controller front outputs; other faces receive.'),
 ('production','潮汐力から糸へ',
  '核パスタは既存の Jet Forge で作ります。縮退物質殻 1 個、質量ペレット 32 個、液体ヘリウム 2,000 mB を使い、90 秒間 125 MFE/t で照射します（照射だけで 225 GFE）。新しい蓄電庫を先に作る必要はありません。\n'
  '潮汐紡糸機は 5×7×13。特異点の種を核のスロットに入れ、10 GFE を蓄えると封じ込め核を形成します。コラプサー・ビレット 1 個、エキゾチック物質 1 個、液体ヘリウム 500 mB を 60 秒間 20 MFE/t で加工し、潮汐繊維 4 本を巻き取ります。材料・冷却材・成果物・進行を保存し、出力が満杯なら待ちます。\n制御卓の左右の足元の開口から電力と冷却材を接続でき、正面と下をアイテムの搬入出に使えます。',
  'From tides to thread',
  'Make Nuclear Pasta in the existing Jet Forge: 1 Degenerate Matter Shell, 32 Mass Pellets and 2,000 mB liquid helium, irradiated for 90 seconds at 125 MFE/t (225 GFE for irradiation alone). No new accumulator is needed first.\n'
  'The Tidal Spindle is 5×7×13. Put a Singularity Seed in its core slot and store 10 GFE to form the confined core. Process 1 Collapsar Billet, 1 Exotic Matter and 500 mB liquid helium for 60 seconds at 20 MFE/t to wind 4 Tidal Filaments. Ingredients, coolant, output and progress are reserved and saved. A full output pauses completion.\nUse the service openings beside the controller for power and coolant, leaving its front and underside free for item input and output.'),
 ('links','縫合と並列照射',
  '潮汐繊維とコラプサー材でワームホール縫合拡張を作り、既存の口の両端に右クリックで取り付けます。両端が開いて読み込まれていると、口の上の四角い開口部を歩いて通れます。物流はそのまま使えます。出口に安全な足場と空間がない場合、移動しません。乗り物に乗っている場合も移動しません。移動直後は再転送を防ぐ待ち時間があります。\n'
  'コラプサー並列カセットを Jet Forge の制御卓に右クリックで取り付けると、同じレシピを最大 4 バッチ同時に加工します。開始時に数量を固定し、材料・冷却材・電力を数量分使います。すでに進行中の処理は取り付けても変わりません。\n'
  '測量メガネを掛けると、近くの流体パイプに転送方向の矢印が見えます。見ている配管の転送量・種類・停止状態も表示します。矢印は実際の端点間転送を結ぶ代表経路を示し、分岐や環状配管の全経路を表すものではありません。パイプ自体は流体を蓄えません。',
  'Seams and parallel irradiation',
  'Make a Wormhole Seam Upgrade from Tidal Filament and Collapsar material, then use one on each existing mouth. When both ends are open and loaded, walk through the square opening above a mouth. Existing logistics remain available. Travel is refused without safe footing and space at the exit, or while riding. A cooldown prevents immediate return trips.\n'
  'Use a Collapsar Parallel Cassette on a Jet Forge controller to process up to four batches of the same recipe together. The count is locked at the start, and materials, coolant and power scale with it. Installing a cassette does not change a run already in progress.\n'
  'Wear Survey Glasses to see transfer arrows on nearby fluid pipes, plus the amount, fluid and blocked state of the pipe you look at. Arrows show one representative connected route for actual endpoint transfers, rather than every path through branches or loops. Pipes do not store fluid.'),
])

DESC = {
 '引力拘束フレーム':('宇宙現象を精密に拘束する開放フレーム。新設備の冠・支柱・レールに使う。','An open frame that confines cosmic phenomena. Used for crowns, pillars and spindle rails.'),
 '核パスタ':('既存の降着ジェット鍛造炉で作る、封じ込めた層状の核物質。新しい蓄電庫なしで製造できる。','Confined layers of nuclear matter made in the existing Accretion Jet Forge. No new accumulator is required.'),
 '核パスタ積層板':('核パスタ蓄電庫の拘束層。容量は有限で、受け取った電力だけを蓄える。','A confinement layer for the Nuclear Pasta Accumulator. Storage is finite and holds only received energy.'),
 '重力懸垂タンク':('5×7×5 の開放ケージに 16,000,000 mB の液体・気体を保持する。流体は天井から下へ増える。制御卓の正面から排出し、他の面から注入する。','A 5×7×5 open cage holding 16,000,000 mB of liquids or gases. Fluid hangs from the ceiling and grows downwards. The controller front drains; other faces fill.'),
 '核パスタ積層蓄電庫':('7×7×7 の設備。容量 8 TFE、入出力は各 2 GFE/t。充電で層が圧縮され、放電は外側の導体へ流れる。制御卓の正面が出力。','A 7×7×7 facility storing 8 TFE, with 2 GFE/t input and output. Charging compresses its layers; discharge flows into the outer conductors. The controller front is the output.'),
 '潮汐紡糸機':('5×7×13 の設備。特異点の種と 10 GFE で核を形成し、潮汐力で素材を伸ばして繊維を巻く。加工には 20 MFE/t と液体ヘリウムが必要。停止・未ロード中は予約内容を保持する。','A 5×7×13 facility. Form its core with a Singularity Seed and 10 GFE, then stretch matter into wound filament using tidal forces. Processing needs 20 MFE/t and liquid helium. Reserved work survives stops and unloaded chunks.'),
 '潮汐繊維':('ブラックホールの潮汐力で引き伸ばした発光繊維。コラプサー材とともに、通行用のワームホールを縫い開く。','Luminous filament stretched by black-hole tidal forces. Combined with Collapsar material, it stitches open a traversable wormhole.'),
 'ワームホール縫合拡張':('既存のワームホールの口へ右クリックして取り付ける。両端が拡張済みで開いていると、上の四角い開口部を歩いて通れる。物流は維持され、安全な足場がない出口へは移動しない。','Use on an existing wormhole mouth to install. When both upgraded ends are open, walk through the square opening above it. Logistics remain available, and travel requires a safe landing.'),
 'コラプサー並列カセット':('Jet Forge の制御卓へ右クリックして取り付け、同じレシピを最大 4 バッチ同時に加工する。開始時に数量を固定し、材料・冷却材・電力を数量分使う。','Use on a Jet Forge controller to process up to four batches of the same recipe together. The count is locked at the start; materials, coolant and power scale with it.'),
}

SUMMARY = {
 '引力拘束フレーム':('新設備の冠・支柱・レール。','Crowns, pillars and spindle rails.'),
 '核パスタ':('Jet Forge で作る層状の核物質。','Layered nuclear matter made in the Jet Forge.'),
 '核パスタ積層板':('蓄電庫の拘束層。','A confinement layer for the accumulator.'),
 '重力懸垂タンク':('天井から流体を吊る大型タンク。','A large tank that suspends fluid from above.'),
 '核パスタ積層蓄電庫':('層を圧縮して蓄える有限容量の蓄電庫。','Finite energy storage in compressed layers.'),
 '潮汐紡糸機':('潮汐力で素材を伸ばし、光る糸を巻く。','Stretches matter with tidal forces and winds luminous thread.'),
 '潮汐繊維':('通行用ワームホールを縫う光る繊維。','Luminous filament for stitching traversable wormholes.'),
 'ワームホール縫合拡張':('両端の口を拡張し、歩いて通行する。','Upgrade both mouths to walk between them.'),
 'コラプサー並列カセット':('Jet Forge を最大 4 バッチへ拡張する。','Process up to four Jet Forge batches together.'),
}

def models(ctx):
    from PIL import Image, ImageDraw
    assets=ctx['ASSETS'];write=ctx['write_json']
    for iid,colour in [('cosmic_frame',(45,75,94)),('pasta_laminate',(65,49,83))]:
        im=Image.new('RGBA',(16,16),(*colour,255));d=ImageDraw.Draw(im)
        for n in (1,7,13):d.line((1,n,14,n),fill=(145,205,220,255),width=1)
        d.rectangle((0,0,15,15),outline=(180,210,220,255));d.rectangle((3,3,12,12),outline=(22,30,46,255))
        path=assets/'textures/block'/f'{iid}.png';path.parent.mkdir(parents=True,exist_ok=True);im.save(path)
        hd=ctx['RES']/'resourcepacks/singulo_hd/assets/singulo/textures/block'/f'{iid}.png'
        hd.parent.mkdir(parents=True,exist_ok=True);im.resize((64,64),Image.Resampling.NEAREST).save(hd)
        elements=[]
        boxes=[([0,0,0],[3,16,3]),([13,0,13],[16,16,16]),([0,0,13],[3,16,16]),([13,0,0],[16,16,3])]
        if iid=='cosmic_frame':
            for y in (0,13):
                boxes.extend([([3,y,0],[13,y+3,3]),([3,y,13],[13,y+3,16]),([0,y,3],[3,y+3,13]),([13,y,3],[16,y+3,13])])
        if iid=='pasta_laminate':boxes=[([1,y,1],[15,y+2,15]) for y in (1,7,13)]
        for a,b in boxes:elements.append({'from':a,'to':b,'faces':{f:{'texture':'#all'} for f in ('north','south','east','west','up','down')}})
        write(assets/'models/block'/f'{iid}.json',{'textures':{'all':f'singulo:block/{iid}','particle':f'singulo:block/{iid}'},'elements':elements})
    for iid in ('nuclear_pasta','tidal_filament','wormhole_seam_upgrade','collapsar_parallel_cassette'):
        im=Image.new('RGBA',(16,16),(0,0,0,0));d=ImageDraw.Draw(im)
        dark=(27,35,51,255);metal=(99,130,149,255);cyan=(154,227,238,255);pale=(216,244,246,255)
        if iid=='nuclear_pasta':
            for row,y in enumerate((3,6,9,12)):
                d.line([(2,y+1),(4,y),(7,y+1),(10,y),(13,y+1)],fill=dark,width=3)
                d.line([(2,y),(4,y-1),(7,y),(10,y-1),(13,y)],fill=(155,128,183,255),width=1)
                d.point((4+row*2,y),fill=cyan)
        elif iid=='tidal_filament':
            d.rectangle((5,1,10,14),fill=dark);d.rectangle((2,1,13,3),fill=metal);d.rectangle((2,12,13,14),fill=metal)
            for y in range(4,12):d.line((4,y,11,y-1),fill=pale if y%3==0 else cyan)
            d.line((11,9,14,10,14,13),fill=cyan)
        elif iid=='wormhole_seam_upgrade':
            d.rectangle((3,2,12,13),fill=dark,outline=metal);d.rectangle((5,4,10,11),outline=cyan)
            for y in (3,6,9,12):d.line((1,y,4,y+1),fill=pale);d.line((11,y+1,14,y),fill=cyan)
        else:
            d.rectangle((1,3,14,12),fill=dark,outline=metal)
            for x in (3,6,9,12):
                d.line((x,4,x,11),fill=(68,97,123,255));d.rectangle((x,5,x+1,8),fill=cyan);d.point((x,11),fill=(223,190,116,255))
        im.save(assets/'textures/item'/f'{iid}.png')
        hd=ctx['RES']/'resourcepacks/singulo_hd/assets/singulo/textures/item'/f'{iid}.png'
        hd.parent.mkdir(parents=True,exist_ok=True);im.resize((64,64),Image.Resampling.NEAREST).save(hd)
