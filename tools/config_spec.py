# -*- coding: utf-8 -*-
"""Singulo の設定項目（設計上の仕様）。
- server: config/singulo-server.toml（ワールドごと。サーバー側で効く。変更はワールド再読み込みで反映）
- client: config/singulo-client.toml（各プレイヤーの見た目）
- datapack: data/singulo/... の JSON（/reload で反映。KubeJS や CraftTweaker からも変更可）
default は recipes.py の既定値と一致させる。
レシピに関わる設定（netherStar・compression の殻と炉殻・timeCrystalGrowthTicks・spentRecycleRatio・sculkPerShard）は、
既定値のままならデータパックのJSONをそのまま使い、既定値から変えたときだけ実行時にJSONより優先して当てはめる
（RecipeTweaks。JEIの表示も同じ）。preset の倍率はその上に掛かる。
"""

PRESETS = {
    'casual':   'ネザースター不要（点火まで0個）、触媒寿命×2、遺構の再生日数×0.5、ウォーデンHP×0.5、縮退物質殻の圧縮量×0.5',
    'standard': '個別の設定値をそのまま使う（既定）',
    'expert':   'ネザースター×2、触媒寿命×0.75、修復回数2回まで、縮退物質殻の圧縮量×1.5、点火エネルギー×2',
    'custom':   'standard と同じ扱い（個別の値を自分で決めたことを示す名札）',
}

# (セクション, キー, 既定値, 範囲・型, 何が変わるか)
SERVER = [
    ('general', 'preset', '"standard"', '"casual" / "standard" / "expert" / "custom"',
     '難易度の倍率を個別の値の上に掛ける。casual: スター不要・触媒寿命×2・遺構の再生×0.5・ウォーデンHP×0.5・殻の圧縮量×0.5。'
     'expert: スター×2・触媒寿命×0.75・修復2回まで・殻の圧縮量×1.5・点火エネルギー×2。standard と custom は個別の値のまま'),

    ('general', 'settingsVersion', '0', '0〜100（整数）',
     '設定ファイルの版（移行の印。手で変えない）。0.3.0 のファイルを読んだとき、旧既定値のまま残っている縮退熱炉の出力・殻の金属の核・'
     '炉殻の数だけを新しい既定値に直し、1にする。自分で変えた値は残る'),

    # ネザースター
    ('netherStar', 'starsPerExoticBatch', '1', '0〜16（整数）', 'エキゾチック物質1回の製作に要るネザースターの数。0で不要'),
    ('netherStar', 'exoticBatchSize', '8', '1〜64（整数）', 'エキゾチック物質1回の製作で出る数'),
    ('netherStar', 'starsPerSingularityCore', '1', '0〜16（整数）', 'シンギュラリティ・コア1個に要るネザースターの数'),
    ('netherStar', 'allowArtificialStarCore', 'true', 'true / false', '人工星核をネザースターの代わりに使えるか（false ならSingulo の装置のレシピは本物のネザースターだけを受け付ける）'),
    ('netherStar', 'artificialStarCoreJetCondensate', '4', '1〜64（整数）', '人工星核1個に要るジェット凝縮体の数'),

    # 電力
    ('power', 'generatorOutputMultiplier', '1.0', '0.1〜100.0', 'すべての発電機の出力倍率'),
    ('power', 'thermoelectricCoefficient', '0.10', '0.001〜10.0（FE/t・K）', '熱電発電機の温度差1Kあたり出力'),
    ('power', 'cryoTurbineOutput', '5000', '1〜1,000,000（FE/t）', '極低温タービンの出力'),
    ('power', 'quantumEngineOutput', '200000', '1〜100,000,000（FE/t）', '量子熱機関の出力'),
    ('power', 'degenerateFurnaceOutput', '5000000', '1〜1,000,000,000（FE/t）', '縮退熱炉の出力'),
    ('power', 'generatorLowerTierOutput', '0.25', '0.0〜1.0', '量子熱機関・縮退熱炉に1段下の触媒を入れたときの出力（定格に対する割合）。装置の速度倍率 lowerTierSpeed とは別'),
    ('power', 'penroseEnergyPerPellet', '20000000000', '1〜（FE）', '質量ペレット1個の質量エネルギー（降着効率を掛ける前）'),
    ('power', 'wormholeGeneratorPower', '100000000', '1〜2000000000（FE/t）', 'ワームホール生成器が10秒のあいだ毎tick必要とする電力'),
    ('power', 'gravityPanelPower', '40', '0〜1,000,000（FE/t）', '重力パネル1枚が働くのに毎tick必要な電力'),
    ('power', 'penroseMaxOutput', '2100000000', '1〜（FE/t）', 'Pリアクターの出力上限'),
    ('power', 'eddingtonPelletsPerSecondPer1000Mass', '1.0', '0.1〜100.0', 'エディントン限界（炉心質量1,000あたり毎秒の投入上限）'),
    ('power', 'ignitionEnergy', '50000000000', '1〜（FE）', 'Pリアクターの点火エネルギー'),
    ('power', 'ignitionWindowSeconds', '10', '1〜600（秒）', '点火エネルギーを注ぎ込む制限時間'),
    ('power', 'machineEnergyMultiplier', '1.0', '0.0〜100.0', '加工装置の消費電力倍率'),

    # 触媒
    ('catalyst', 'lifetimeMultiplier', '1.0', '0.1〜100.0', 'すべての触媒の寿命倍率'),
    ('catalyst', 'muonLifetimeMinutes', '20', '1〜1440（分）', 'ミュオン触媒の寿命'),
    ('catalyst', 'boseLifetimeMinutes', '30', '1〜1440（分）', 'BE凝縮触媒の寿命'),
    ('catalyst', 'timeCrystalLifetimeMinutes', '45', '1〜1440（分）', '時間結晶触媒の寿命'),
    ('catalyst', 'singularityLifetimeMinutes', '60', '1〜1440（分）', 'シンギュラリティ・コアの寿命（埋め込み時は無期限）'),
    ('catalyst', 'lowerTierSpeed', '0.5', '0.0〜1.0', '1段下の触媒を入れたときの速度倍率'),
    ('catalyst', 'lowerTierConsumption', '2.0', '1.0〜10.0', '1段下の触媒を入れたときの消費倍率'),
    ('catalyst', 'underpowerThreshold', '0.8', '0.0〜1.0', '要求電力のこの割合を下回ると不完全反応になる'),
    ('catalyst', 'underpowerConsumption', '1.5', '1.0〜10.0', '不完全反応時の触媒消費倍率'),
    ('catalyst', 'spentRecycleRatio', '0.25', '0.0〜1.0', '失活触媒から1ティア下の原料を回収できる割合（既定0.25で失活触媒4個から1個。0でリサイクルなし）'),
    ('catalyst', 'timeCrystalGrowthTicks', '14400', '20〜1,000,000（tick）', '時間結晶の育成に要る稼働tick（既定12分）'),

    # 圧縮・質量
    ('compression', 'shellRockLv3', '4', '0〜9（整数）', '縮退物質殻1個に要る岩石の圧縮ブロックLv3'),
    ('compression', 'shellMetalCores', '1', '0〜9（整数）', '縮退物質殻1個に要る金属圧縮ブロックLv2（金属の核）'),
    ('compression', 'shellCoolantMb', '6000', '0〜8,000（mB）', '縮退物質殻1個の圧縮熱を除くのに要る液体窒素（ストレンジ物質版はこの半分）。プリセットの倍率を掛けても装置のタンク1つ（8,000 mB）が上限'),
    ('compression', 'reactorPlatesPerShell', '24', '1〜64（整数）', '縮退物質殻1個から作れる炉殻ブロックの数'),
    ('compression', 'mixedBonusEnabled', 'true', 'true / false', '混成ボーナス（Lv1が3種類以上ならLv2を8個で作れる）'),
    ('compression', 'mixedBonusMinKinds', '3', '2〜9（整数）', '混成ボーナスに要る元ブロックの種類数'),
    ('compression', 'compressorSpeedMultiplier', '1.0', '0.1〜100.0', '圧縮機の処理速度倍率'),

    # 遺構
    ('ruins', 'regenDaysMultiplier', '1.0', '0.0〜10.0', '遺構の中身が再生するまでの日数倍率。0で再生なし'),
    ('ruins', 'lootCountMultiplier', '1.0', '0.1〜10.0', '1回の遠征で拾える回収物の数の倍率'),
    ('ruins', 'usesMultiplier', '1.0', '0.1〜10.0', '回収物（原本・復元品）の使用回数の倍率'),
    ('ruins', 'repairWear', '0.75', '0.1〜1.0', '修復・再復元のたびに最大使用回数に掛かる倍率'),
    ('ruins', 'maxRepairs', '3', '0〜100（整数）', '修復・再復元できる回数'),
    ('ruins', 'probeAutomationOffset', '2', '1〜4（整数）', '何ティア下の回収物を自動探査機で回収できるか（N−2ルール）'),
    ('ruins', 'probeYieldMultiplier', '0.5', '0.0〜1.0', '自動探査機の回収量（手動遠征比）'),
    ('ruins', 'wardenHealth', '1200', '1〜100,000', 'ホライズン・ウォーデンのHP'),
    ('ruins', 'wardenResetOnLeave', 'true', 'true / false', '挑戦者が全員離れたら番人が戻って全回復するか（最後の記録者以外は3分の猶予がある）'),
    ('ruins', 'awakenedWardenHealthMultiplier', '2.5', '1.0〜20.0', '覚醒したホライズン・ウォーデン（攻略後の再戦）の体力の倍率（wardenHealth に掛ける）'),
    ('ruins', 'awakenedWardenDamageMultiplier', '1.5', '1.0〜10.0', '覚醒したホライズン・ウォーデンが与えるダメージの倍率'),
    ('ruins', 'leviathanHealth', '1600', '1〜100,000', 'エルゴ球の大蛇（沈んだ炉心区画のボス）のHP'),
    ('ruins', 'archivistHealth', '2400', '1〜100,000', '最後の記録者（隠しボス）のHP'),
    ('ruins', 'awakenedGuardianHealthMultiplier', '2.5', '1.0〜20.0', '覚醒した番人（残響の番人・重力の澱・エルゴ球の大蛇の再戦）の体力の倍率'),
    ('ruins', 'awakenedGuardianDamageMultiplier', '1.5', '1.0〜10.0', '覚醒した番人が与えるダメージの倍率'),
    ('ruins', 'bossHealthPerPlayer', '0.5', '0.0〜4.0', '挑戦者が1人増えるごとに増えるボスの体力（もとの体力に対する割合）'),
    ('ruins', 'bossPacePerPlayer', '0.08', '0.0〜0.5', '挑戦者が1人増えるごとに、ボスの技の間が縮む割合（0.6倍まで）'),
    ('ruins', 'bossMaxScaledPlayers', '6', '1〜32（整数）', 'ボスの強さに数える挑戦者の上限'),
    ('ruins', 'bossLootPerPlayer', 'true', 'true / false', 'ボスの落とす物と覚醒の報酬を、挑戦者の人数ぶん出すか（経験値も。オーブは皆で拾う）'),
    ('ruins', 'bossXpMultiplier', '1.0', '0.0〜10.0', 'ボスを倒したときの経験値の倍率'),

    # 残響の欠片
    ('echo', 'templateUses', '16', '1〜1000（整数）', '型の残響の欠片が割れるまでの回数'),
    ('echo', 'sculkPerShard', '16', '1〜64（整数）', '残響の欠片1個の複製に要るスカルク'),

    # 危険・世界への影響
    ('hazards', 'blackHolePullRadius', '24', '0〜64（ブロック）', '引力帯の半径。0で引力なし'),
    ('hazards', 'blackHolePullStrength', '0.04', '0.0〜1.0', 'リングの外での弱い引力の最大加速度（ブロック/tick²）'),
    ('hazards', 'blackHoleInnerPullStrength', '0.25', '0.0〜2.0', 'リングの内側での強い引力の加速度（ブロック/tick²、中心に近いほど強い）'),
    ('hazards', 'eventHorizonKill', 'true', 'true / false', '事象の地平線に触れたものを必ず消す（どんな体力・耐性・無敵も無視）'),
    ('hazards', 'tidalDamageRadius', '4', '0〜16（ブロック）', '潮汐ダメージ帯の半径。0で無効'),
    ('hazards', 'evaporationBurstDamagesWorld', 'false', 'true / false', '炉心の蒸発バーストが外殻の外を壊すか'),
    ('hazards', 'strangeletEnabled', 'true', 'true / false', 'ストレンジレットの発生'),
    ('hazards', 'strangeletMaxBlocks', '512', '0〜4096', 'ストレンジレットが変換する最大ブロック数'),
    ('hazards', 'strangeletMaxRadius', '8', '0〜32（ブロック）', 'ストレンジレットが広がる最大半径'),
    ('hazards', 'hydrogenLeakExplosion', 'true', 'true / false', '水素の容器が壊れたときの小爆発'),
    ('hazards', 'timeDilationEnabled', 'true', 'true / false', '重力時間膨張ゾーン（処理×0.8、触媒劣化×0.5）'),
    ('hazards', 'tiplerSpeedMultiplier', '2.0', '1.0〜10.0', 'Tシリンダーの加速倍率'),
    ('hazards', 'tiplerRadius', '16', '1〜32（ブロック）', 'Tシリンダーが時間を加速する半径'),
    ('hazards', 'shieldRadius', '32', '4〜64（ブロック）', 'イベントホライズン・シールドの半径（電力は半径の二乗に比例）'),

    # 創世炉
    ('genesis', 'genesisSeedEnergy', '1500000000000', '1〜（FE）', '創世炉の種火に注ぐ電力'),
    ('genesis', 'genesisMaxInput', '1000000000', '1〜（FE/t）', '創世炉が1 tick に受け取る電力の上限'),
    ('genesis', 'genesisUpkeep', '100000000', '0〜（FE/t）', '安定化のあいだ、進むのに要る維持の電力'),
    ('genesis', 'genesisBaseCost', '1000000000', '1〜（FE）', '質量生成で、質量1あたりの電力（これに 10^希少度 を掛ける）'),
    ('genesis', 'genesisEndingScreen', 'true', 'true / false', '宇宙が生まれたとき、エピローグの画面を出すか'),

    # 道具
    ('gear', 'gauntletRange', '12', '4〜32（ブロック）', '慣性制御ガントレットの射程'),
    ('gear', 'manipulatorRange', '24', '8〜64（ブロック）', 'グラビトン・マニピュレーターの射程'),

    # 負荷
    ('performance', 'pullCheckIntervalTicks', '5', '1〜100（tick）', '引力判定の間隔'),
    ('performance', 'maxAnchorChunkRadius', '5', '0〜16（チャンク）', 'ワールドライン・アンカーの最大半径'),
    ('performance', 'maxAnchorsPerPlayer', '8', '0〜1000', '1人が同時に動かせるワールドライン・アンカー（小・上位の合計）の数。0で無制限。上限を超えたアンカーは置けるが動かない（先に動き出したものが優先）。この版より前に置いたアンカーは、置き直すまで数えない'),
]

CLIENT = [
    ('render', 'gravitationalLensing', 'true', 'true / false', '重力レンズの歪み表示'),
    ('render', 'lensingWithShaderPacks', 'true', 'true / false', 'Iris系シェーダーパック使用時に専用の歪み処理を使うか（falseならフォールバック表示）'),
    ('render', 'dopplerBeaming', 'true', 'true / false', '降着円盤のドップラー・ビーミング'),
    ('render', 'lensingQuality', '"high"', '"low" / "medium" / "high"', '歪み処理の解像度'),
    ('render', 'formationEffects', 'true', 'true / false', 'マルチブロック形成時の光の演出'),
]

DATAPACK = [
    ('data/singulo/recipe/*.json', 'すべてのレシピ', '個数・製作場所・時間・電力を差し替え・追加・削除'),
    ('data/singulo/mass_values/*.json', '質量値', 'アイテム単位・タグ単位で質量値を変更。金属扱いにするタグも指定'),
    ('data/singulo/mass_synthesis/*.json', '創世炉の質量生成', '作れる物（アイテム単位・タグ単位）と、その質量・希少度（0〜4）'),
    ('data/singulo/thermal/*.json', '熱電発電機の温度', '高温源・低温源のブロック温度と維持できる温度差'),
    ('data/singulo/tags/item/star_core.json', 'ネザースターの代替', '#singulo:star_core に入れたアイテムをネザースターの代わりに使える'),
    ('data/singulo/tags/item/metal_core.json', '金属の核に使える素材', '金属圧縮ブロックに入れられる金属ブロック'),
    ('data/singulo/tags/entity_type/gravity_immune.json', '重力耐性', '浮遊・圧壊が効かないモブ'),
    ('data/singulo/loot_table/ruins/*.json', '遺構の中身', '回収物の種類と個数'),
]
