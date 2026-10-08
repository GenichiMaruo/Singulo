# -*- coding: utf-8 -*-
"""recipes.py の日本語名 → ゲーム内IDの対応表。

MOD: 日本語名 → (id, 英語名, 種類)
  種類:
    item       ただのアイテム
    block      設置できる単純なブロック
    machine    実装済みの装置ブロック（Java側に対応クラスがある）
    planned    装置・部品だが動作は未実装。今はアイテムとして登録する
    uses       使用回数つきのアイテム（遺構回収物・復元品・データカード）
    catalyst   触媒（寿命つき）
    fluid      液体・ガス
    structure  マルチブロック本体など、アイテムとしては存在しないもの（登録しない）
VANILLA: 日本語名 → アイテムID（'#' で始まるものはタグ）
"""

MOD = {
    # ---- 段階1 ----
    '鋼の素': ('steel_blend', 'Steel Blend', 'item'),
    '鋼鉄インゴット': ('steel_ingot', 'Steel Ingot', 'item'),
    '未焼成セラミック': ('unfired_ceramic', 'Unfired Ceramic', 'item'),
    'ホワイトセラミック複合材': ('white_ceramic_composite', 'White Ceramic Composite', 'item'),
    '基礎回路': ('basic_circuit', 'Basic Circuit', 'item'),
    'Singuloハンドブック': ('handbook', 'Singulo Handbook', 'tool'),
    'ホロ投影機': ('holo_projector', 'Holo Projector', 'tool'),
    '探索コンパス': ('explorer_compass', 'Explorer\'s Compass', 'tool'),
    # クリエイティブ専用（レシピなし）
    'クリエイティブ電源': ('creative_energy_source', 'Creative Energy Source', 'machine'),
    '無限の触媒': ('creative_catalyst', 'Infinite Catalyst', 'tool'),
    '組み立ての杖': ('builder_wand', 'Builder\'s Wand', 'tool'),
    '熱電対モジュール': ('thermocouple_module', 'Thermocouple Module', 'item'),
    '基礎フレーム': ('basic_frame', 'Basic Frame', 'item'),
    '鋼板': ('steel_plate', 'Steel Plate', 'item'),
    'セラミック基板': ('ceramic_substrate', 'Ceramic Substrate', 'item'),
    '水素': ('hydrogen', 'Hydrogen', 'fluid'),
    '銅導線': ('copper_wire', 'Copper Wire', 'machine'),
    '白紙データカード': ('blank_data_card', 'Blank Data Card', 'item'),
    'データカード（観測ログ）': ('data_card_observation_log', 'Data Card (Observation Log)', 'item'),
    '圧縮ブロックLv1': ('compressed_block_1', 'Compressed Block Lv1', 'block'),
    '質量ペレット': ('mass_pellet', 'Mass Pellet', 'item'),
    '熱電発電機': ('thermoelectric_generator', 'Thermoelectric Generator', 'machine'),
    '焼成炉': ('kiln', 'Kiln', 'machine'),
    '圧縮機': ('compressor', 'Compressor', 'machine'),
    '電解槽': ('electrolyzer', 'Electrolyzer', 'machine'),
    'アーカイブ端末': ('archive_terminal', 'Archive Terminal', 'machine'),

    # ---- 段階2 ----
    '冷却塔外壁': ('cooling_tower_casing', 'Cooling Tower Casing', 'part_block'),
    '冷却塔ガラス': ('cooling_tower_glass', 'Cooling Tower Glass', 'part_glass'),
    '熱交換コア': ('heat_exchange_core', 'Heat Exchange Core', 'part_block'),
    '冷却塔ポート': ('cooling_tower_port', 'Cooling Tower Port', 'part_block'),
    '冷却塔コントローラ': ('cooling_tower_controller', 'Cooling Tower Controller', 'machine'),
    '極低温冷却塔': ('cryogenic_cooling_tower', 'Cryogenic Cooling Tower', 'structure'),
    '冷却塔拡張（高さ10）': ('cooling_tower_extension', 'Cooling Tower Extension', 'structure'),
    '液体窒素': ('liquid_nitrogen', 'Liquid Nitrogen', 'fluid'),
    '超伝導コイル': ('superconducting_coil', 'Superconducting Coil', 'item'),
    '超伝導線材': ('superconducting_wire', 'Superconducting Wire', 'item'),
    '超伝導ケーブル': ('superconducting_cable', 'Superconducting Cable', 'machine'),
    '加速管': ('accelerator_tube', 'Accelerator Tube', 'part_block'),
    '収束磁石': ('focusing_magnet', 'Focusing Magnet', 'part_block'),
    '加速器コントローラ': ('accelerator_controller', 'Accelerator Controller', 'machine'),
    '粒子加速器': ('particle_accelerator', 'Particle Accelerator', 'structure'),
    'ミュオン束': ('muon_bundle', 'Muon Bundle', 'item'),
    '触媒反応器': ('catalytic_reactor', 'Catalytic Reactor', 'machine'),
    'ミュオン触媒': ('muon_catalyst', 'Muon Catalyst', 'catalyst'),
    '極低温タービン': ('cryogenic_turbine', 'Cryogenic Turbine', 'machine'),
    '宇宙線ミュオン収集器': ('cosmic_muon_collector', 'Cosmic Muon Collector', 'machine'),
    'SMESセル': ('smes_cell', 'SMES Cell', 'machine'),
    '精密組立台': ('precision_assembler', 'Precision Assembler', 'machine'),
    'ワールドライン・アンカー（小）': ('worldline_anchor_small', 'Worldline Anchor (Small)', 'machine'),
    '単極子アップグレード': ('monopole_upgrade', 'Monopole Upgrade', 'item'),

    # ---- 段階3 ----
    'ヘリウム': ('helium', 'Helium', 'fluid'),
    '液体ヘリウム': ('liquid_helium', 'Liquid Helium', 'fluid'),
    'データカード（量子データ片）': ('data_card_quantum_fragment', 'Data Card (Quantum Data Fragment)', 'item'),
    '量子演算モジュール': ('quantum_computing_module', 'Quantum Computing Module', 'item'),
    '量子もつれ合成器': ('entanglement_synthesizer', 'Entanglement Synthesizer', 'machine'),
    '量子もつれ素子': ('entangled_element', 'Entangled Element', 'item'),
    'レーザー冷却器': ('laser_cooler', 'Laser Cooler', 'machine'),
    '冷却原子': ('cold_atoms', 'Cold Atoms', 'item'),
    '光格子基板': ('optical_lattice_substrate', 'Optical Lattice Substrate', 'item'),
    'BE凝縮触媒': ('bose_condensate_catalyst', 'BE Condensate Catalyst', 'catalyst'),
    '量子熱機関': ('quantum_heat_engine', 'Quantum Heat Engine', 'machine'),
    '重力波検出器': ('gravitational_wave_detector', 'Gravitational Wave Detector', 'machine'),
    'ニュートリノ・スキャナー': ('neutrino_scanner', 'Neutrino Scanner', 'tool'),
    '慣性スタビライザー': ('inertial_stabilizer', 'Inertial Stabilizer', 'machine'),
    '残響共鳴器': ('echo_resonator', 'Echo Resonator', 'machine'),
    '慣性制御ガントレット': ('inertial_control_gauntlet', 'Inertial Control Gauntlet', 'tool'),

    # ---- 段階4 ----
    '圧縮ブロックLv2': ('compressed_block_2', 'Compressed Block Lv2', 'block'),
    '圧縮ブロックLv3': ('compressed_block_3', 'Compressed Block Lv3', 'block'),
    '圧縮ブロックLv2（混成）': ('compressed_block_2_mixed', 'Compressed Block Lv2 (Mixed)', 'structure'),
    '縮退炉外殻': ('degenerate_casing', 'Degenerate Casing', 'part_block'),
    '縮退圧縮炉コントローラ': ('degenerate_compactor_controller', 'Degenerate Compactor Controller', 'machine'),
    '縮退圧縮炉': ('degenerate_compactor', 'Degenerate Compactor', 'structure'),
    '金属圧縮ブロックLv1': ('compressed_metal_block_1', 'Compressed Metal Block Lv1', 'block'),
    '金属圧縮ブロックLv2': ('compressed_metal_block_2', 'Compressed Metal Block Lv2', 'block'),
    '縮退物質殻': ('degenerate_matter_shell', 'Degenerate Matter Shell', 'item'),
    '鏡面プレート': ('mirror_plate', 'Mirror Plate', 'part_block'),
    'C空洞コントローラ': ('casimir_cavity_controller', 'C-Cavity Controller', 'machine'),
    'C空洞': ('casimir_cavity', 'C-Cavity', 'structure'),
    'アクシオン凝縮体': ('axion_condensate', 'Axion Condensate', 'fluid'),
    'データカード（培養データ）': ('data_card_culture_data', 'Data Card (Culture Data)', 'item'),
    '時間結晶育成槽': ('time_crystal_incubator', 'Time Crystal Incubator', 'machine'),
    '時間結晶触媒': ('time_crystal_catalyst', 'Time Crystal Catalyst', 'catalyst'),
    'エキゾチック物質': ('exotic_matter', 'Exotic Matter', 'item'),
    '縮退熱炉ピストン部': ('degenerate_furnace_piston', 'Degenerate Furnace Piston', 'part_block'),
    '縮退熱炉コントローラ': ('degenerate_furnace_controller', 'Degenerate Furnace Controller', 'machine'),
    '縮退熱炉': ('degenerate_furnace', 'Degenerate Furnace', 'structure'),
    'トポロジカル導線': ('topological_wire', 'Topological Wire', 'machine'),
    'トロイダル磁気コイル': ('toroidal_magnetic_coil', 'Toroidal Magnetic Coil', 'item'),
    'SMESモジュール': ('smes_module', 'SMES Module', 'machine'),
    '自動探査機ステーション': ('probe_station', 'Autonomous Probe Station', 'machine'),
    '磁気瓶': ('magnetic_bottle', 'Magnetic Bottle', 'tool'),
    'ストレンジ物質': ('strange_matter', 'Strange Matter', 'block'),
    'ストレンジレット': ('strangelet', 'Strangelet', 'part_block'),

    # ---- 段階5 ----
    'ホライズン・バス': ('horizon_bus', 'Horizon Bus', 'machine'),
    '炉殻ブロック': ('reactor_shell', 'Reactor Shell', 'part_block'),
    'ジャイロ駆動部': ('gyro_drive', 'Gyro Drive', 'part_block'),
    '抽出ポート': ('extraction_port', 'Extraction Port', 'part_block'),
    '重力場安定化ユニット': ('gravity_field_stabilizer', 'Gravity Field Stabilizer', 'item'),
    '炉心制御装置': ('core_controller', 'Core Controller', 'machine'),
    'Pリアクター': ('penrose_reactor', 'P-Reactor', 'structure'),
    'Hコレクター': ('hawking_collector', 'H-Collector', 'item'),
    'ジェット・コレクター': ('jet_collector', 'Jet Collector', 'item'),
    'エルゴスフィア・リング': ('ergosphere_ring', 'Ergosphere Ring', 'item'),
    'ジェット凝縮体': ('jet_condensate', 'Jet Condensate', 'item'),
    '人工星核': ('artificial_star_core', 'Artificial Star Core', 'item'),
    'H凝縮体': ('hawking_condensate', 'H-Condensate', 'item'),
    '特異点封入台': ('singularity_encapsulator', 'Singularity Encapsulator', 'machine'),
    '特異点の種': ('singularity_seed', 'Singularity Seed', 'item'),
    'シンギュラリティ・コア': ('singularity_core', 'Singularity Core', 'catalyst'),
    'ハロー捕集器': ('halo_collector', 'Halo Collector', 'machine'),
    'ダークマター': ('dark_matter', 'Dark Matter (WIMP)', 'fluid'),
    '重力閉じ込めタンク': ('gravitational_containment_tank', 'Gravitational Containment Tank', 'machine'),
    'シールド発生塔コア': ('shield_tower_core', 'Shield Tower Core', 'machine'),
    'イベントホライズン・シールド発生塔': ('event_horizon_shield', 'Event Horizon Shield Tower', 'structure'),
    'ワールドライン・アンカー（上位）': ('worldline_anchor_advanced', 'Worldline Anchor (Advanced)', 'machine'),
    'メトリック・ドライブ': ('metric_drive', 'Metric Drive', 'tool'),
    'グラビトン・マニピュレーター': ('graviton_manipulator', 'Graviton Manipulator', 'tool'),
    'Tコア': ('tipler_core', 'T-Core', 'machine'),
    'Tシリンダー': ('tipler_cylinder', 'T-Cylinder', 'structure'),
    'ワームホール生成器コア': ('wormhole_generator_core', 'Wormhole Generator Core', 'machine'),
    'ワームホール生成器入出力口': ('wormhole_generator_io', 'Wormhole Generator I/O Port', 'part_block'),
    'ワームホール生成器': ('wormhole_generator', 'Wormhole Generator', 'structure'),
    '不安定なワームホールの口': ('unstable_wormhole_mouth', 'Unstable Wormhole Mouth', 'tool'),
    'ワームホールの口': ('wormhole_mouth', 'Wormhole Mouth', 'machine'),
    'ワームホール固定化装置': ('wormhole_stabilizer', 'Wormhole Stabilizer', 'machine'),
    'ワームホール・ポート': ('wormhole_port', 'Wormhole Port', 'machine'),

    # ---- 遺構回収物・復元品 ----
    '観測ログ': ('observation_log', 'Observation Log', 'uses'),
    '劣化した制御ユニット': ('degraded_control_unit', 'Degraded Control Unit', 'item'),
    '制御ユニット': ('control_unit', 'Control Unit', 'uses'),
    '量子データ片': ('quantum_data_fragment', 'Quantum Data Fragment', 'uses'),
    '劣化した冷却原子トラップ': ('degraded_cold_atom_trap', 'Degraded Cold Atom Trap', 'item'),
    '冷却原子トラップ': ('cold_atom_trap', 'Cold Atom Trap', 'uses'),
    '劣化した時間結晶の種': ('degraded_time_crystal_seed', 'Degraded Time Crystal Seed', 'item'),
    '時間結晶の種': ('time_crystal_seed', 'Time Crystal Seed', 'uses'),
    '培養データ': ('culture_data', 'Culture Data', 'uses'),
    '劣化したアノマリー・サンプル': ('degraded_anomaly_sample', 'Degraded Anomaly Sample', 'item'),
    'アノマリー・サンプル': ('anomaly_sample', 'Anomaly Sample', 'uses'),
    '休眠した特異点の種': ('dormant_singularity_seed', 'Dormant Singularity Seed', 'item'),

    # ---- 遺構の建材（作れない。遺構から持ち帰るだけ） ----
    '遺構パネル': ('ruin_panel', 'Ruin Panel', 'ruin_block'),
    'ひび割れた遺構パネル': ('cracked_ruin_panel', 'Cracked Ruin Panel', 'ruin_block'),
    '苔むした遺構パネル': ('mossy_ruin_panel', 'Mossy Ruin Panel', 'ruin_block'),
    '割れた遺構ガラス': ('ruin_glass', 'Broken Ruin Glass', 'ruin_glass'),
    '遺構の照明': ('ruin_lamp', 'Ruin Lamp', 'ruin_block'),
    '遺構保管庫': ('ruin_cache', 'Ruin Cache', 'ruin_block'),
    '警備機ドック': ('ruin_guard_dock', 'Guard Dock', 'ruin_block'),
    '封印コンソール': ('seal_console', 'Seal Console', 'ruin_block'),

    # ---- レシピ外で必要なもの ----
    '失活したミュオン触媒': ('spent_muon_catalyst', 'Spent Muon Catalyst', 'item'),
    '失活したBE凝縮触媒': ('spent_bose_condensate_catalyst', 'Spent BE Condensate Catalyst', 'item'),
    '失活した時間結晶触媒': ('spent_time_crystal_catalyst', 'Spent Time Crystal Catalyst', 'item'),
    '失活したシンギュラリティ・コア': ('spent_singularity_core', 'Spent Singularity Core', 'item'),
    '酸素': ('oxygen', 'Oxygen', 'fluid'),
    '磁気単極子': ('magnetic_monopole', 'Magnetic Monopole', 'item'),
    '記録片': ('record_fragment', 'Record Fragment', 'item'),
    '解読した記録': ('decoded_record', 'Decoded Record', 'tool'),
}

# recipes.py で mod の成果物として書かれているが、実体はバニラのアイテム
OUTPUT_ALIASES = {
    '縮退物質殻（ストレンジ物質）': 'singulo:degenerate_matter_shell',
    'ミュオン束（リサイクル）': 'singulo:muon_bundle',
    'ミュオン触媒（リサイクル）': 'singulo:muon_catalyst',
    'BE凝縮触媒（リサイクル）': 'singulo:bose_condensate_catalyst',
    '時間結晶触媒（リサイクル）': 'singulo:time_crystal_catalyst',
    '残響の欠片': 'minecraft:echo_shard',
}

VANILLA = {
    'アメジストの欠片': 'minecraft:amethyst_shard',
    'エンダーアイ': 'minecraft:ender_eye',
    'エンダーパール': '#c:ender_pearls',
    'ガラス': '#c:glass_blocks/colorless',
    'グロウストーンダスト': '#c:dusts/glowstone',
    'コンパス': 'minecraft:compass',
    'スカルク': 'minecraft:sculk',
    'スカルクセンサー': 'minecraft:sculk_sensor',
    'スライムボール': '#c:slime_balls',
    'ダイヤモンド': '#c:gems/diamond',
    'ネザースター': '#singulo:star_core',
    'ネザー水晶': '#c:gems/quartz',
    'バケツ': 'minecraft:bucket',
    '本': 'minecraft:book',
    'ピストン': 'minecraft:piston',
    'ファントムの皮膜': 'minecraft:phantom_membrane',
    'レッドストーン': '#c:dusts/redstone',
    '時計': 'minecraft:clock',
    '残響の欠片（型）': 'minecraft:echo_shard',
    '深層岩': '#singulo:helium_bearing_stone',
    '溶鉱炉': 'minecraft:blast_furnace',
    '石炭': 'minecraft:coal',
    '砂': '#c:sands',
    '粘土玉': 'minecraft:clay_ball',
    '鉄インゴット': '#c:ingots/iron',
    '銅インゴット': '#c:ingots/copper',
    '青氷': 'minecraft:blue_ice',
    '骨粉': 'minecraft:bone_meal',
    '黒曜石': '#c:obsidians',
}

# 液体（バニラ）
VANILLA_FLUIDS = {
    '水': 'minecraft:water',
}

# 質量で受け付ける疑似材料（圧縮機の質量バッファで扱う）
MASS_INPUTS = {
    '質量値': False,   # 任意のブロック
    '金属質量': True,  # #singulo:metal_core のみ
}

# 鋼鉄は他modの鋼鉄も受け付ける（設計の柱5）
STEEL_TAG = '#c:ingots/steel'

# 製作場所 → 装置ID（バニラの製作場所は別扱い）
STATIONS = {
    '焼成炉': 'kiln',
    '圧縮機': 'compressor',
    '電解槽': 'electrolyzer',
    '精密組立台': 'precision_assembler',
    'アーカイブ端末': 'archive_terminal',
    '極低温冷却塔': 'cryogenic_cooling_tower',
    '粒子加速器': 'particle_accelerator',
    '触媒反応器': 'catalytic_reactor',
    'レーザー冷却器': 'laser_cooler',
    '残響共鳴器': 'echo_resonator',
    '量子もつれ合成器': 'entanglement_synthesizer',
    'C空洞': 'casimir_cavity',
    '時間結晶育成槽': 'time_crystal_incubator',
    '縮退圧縮炉': 'degenerate_compactor',
    '特異点封入台': 'singularity_encapsulator',
    'Pリアクター（発電モード）': 'penrose_reactor_power',
    'Pリアクター（触媒モード）': 'penrose_reactor_catalyst',
    'ハロー捕集器': 'halo_collector',
    'ワームホール生成器': 'wormhole_generator',
    'ワームホール固定化装置': 'wormhole_stabilizer',
}
# 専用の処理で動く製作場所（レシピの JSON は作らない）
CUSTOM_STATIONS = {'ハロー捕集器', 'ワームホール生成器', 'ワームホール固定化装置'}

STAGE_COLORS = {  # 発光ラインの色（段階ごと）
    1: (120, 210, 240),
    2: (150, 230, 255),
    3: (190, 170, 255),
    4: (255, 200, 120),
    5: (255, 255, 255),
}

# 触媒 → 使い切ったときに残る失活触媒
SPENT = {
    'ミュオン触媒': '失活したミュオン触媒',
    'BE凝縮触媒': '失活したBE凝縮触媒',
    '時間結晶触媒': '失活した時間結晶触媒',
    'シンギュラリティ・コア': '失活したシンギュラリティ・コア',
}
# 失活品でも材料にできる組み合わせ（成果物, 材料の触媒）。材料はタグ singulo:<触媒ID>s で受ける
ACCEPTS_SPENT = {('BE凝縮触媒', 'ミュオン触媒')}

# マルチブロックの大きさの下限（冷却塔の高さなど）があるレシピ
MIN_SIZE = {'液体ヘリウム': 10}
