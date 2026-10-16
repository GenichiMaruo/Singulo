"""進捗（advancement）の木。ハンドブックの「進み具合」の章もこれを使う。

ADVANCEMENTS は (id, 親, アイコン, 枠, 条件, 段階, 日本語の題, 日本語の説明, 英語の題, 英語の説明)。
条件:
  ('tick',)                 入ったらすぐ（根）
  ('item', 'xxx')           singulo:xxx を手に入れる（minecraft: で始まればバニラ）
  ('milestone', 'id')       Singulo の出来事（formed/<マルチブロック>, discover/<遺構>, ignite, wormhole_open, records_all など）
  ('kill', 'entity_id')     倒す
枠: task / goal / challenge
"""

# 隠し進捗（達成するまで一覧に出ない）
HIDDEN_ADVANCEMENTS = {'archivist'}

ADVANCEMENTS = [
    ('root', None, 'handbook', 'task', ('tick',), 1,
     'Singulo', '質量からエネルギーを取り出す。まずはハンドブックを開こう',
     'Singulo', 'Extract energy from mass. Open the handbook to begin'),
    # ---------------- 段階1
    ('steel', 'root', 'steel_ingot', 'task', ('item', 'steel_ingot'), 1,
     '鋼の時代', '鋼鉄インゴットを作る', 'Age of Steel', 'Make a steel ingot'),
    ('ceramic', 'steel', 'white_ceramic_composite', 'task', ('item', 'white_ceramic_composite'), 1,
     '白い外装', 'ホワイトセラミック複合材を焼く', 'White Casing', 'Fire white ceramic'),
    ('circuit', 'ceramic', 'basic_circuit', 'task', ('item', 'basic_circuit'), 1,
     '最初の回路', '基礎回路を作る', 'First Circuit', 'Make a basic circuit'),
    ('thermo', 'circuit', 'thermoelectric_generator', 'goal', ('item', 'thermoelectric_generator'), 1,
     '温度差', '熱電発電機を作る。高温と低温のブロックで挟むと発電する', 'Temperature Gradient',
     'Build a thermoelectric generator and put it between hot and cold'),
    ('compressor', 'thermo', 'compressor', 'task', ('item', 'compressor'), 1,
     'すべては質量', '圧縮機を作る。質量モードで何でも固められる', 'Everything Is Mass', 'Build a compressor'),
    ('projector', 'circuit', 'holo_projector', 'task', ('item', 'holo_projector'), 1,
     '設計図は光で', 'ホロ投影機を作る。コントローラに使うと形が見える', 'Blueprints of Light',
     'Make a holo projector and use it on a controller'),
    ('archive', 'compressor', 'archive_terminal', 'task', ('item', 'archive_terminal'), 1,
     '旧文明の端末', 'アーカイブ端末を作る', 'Ancient Terminal', 'Build an archive terminal'),
    ('ruin_observation', 'archive', 'observation_log', 'goal', ('milestone', 'discover/observation_post'), 1,
     '旧文明の痕跡', '地表観測拠点の保管庫を開ける', 'Traces of the Ancients', 'Open an observation post cache'),
    # ---------------- 段階2
    ('tower', 'ruin_observation', 'cooling_tower_controller', 'goal', ('milestone', 'formed/cooling_tower'), 2,
     '絶対零度へ', '極低温冷却塔を組み上げる', 'Toward Absolute Zero', 'Form a cryogenic cooling tower'),
    ('coil', 'tower', 'superconducting_coil', 'goal', ('item', 'superconducting_coil'), 2,
     '抵抗ゼロ', '超伝導コイルを作る', 'Zero Resistance', 'Make a superconducting coil'),
    ('assembler', 'coil', 'precision_assembler', 'task', ('item', 'precision_assembler'), 2,
     '精密な手', '精密組立台を作る', 'Precise Hands', 'Build a precision assembler'),
    ('accelerator', 'coil', 'accelerator_controller', 'goal', ('milestone', 'formed/particle_accelerator'), 2,
     '衝突', '粒子加速器を組み上げる', 'Collision Course', 'Form a particle accelerator'),
    ('muon', 'accelerator', 'muon_catalyst', 'task', ('item', 'muon_catalyst'), 2,
     '最初の触媒', 'ミュオン触媒を作る', 'First Catalyst', 'Make a muon catalyst'),
    ('monopole', 'accelerator', 'magnetic_monopole', 'challenge', ('item', 'magnetic_monopole'), 2,
     '北だけの磁石', '磁気単極子を手に入れる', 'North Only', 'Obtain a magnetic monopole'),
    ('anchor', 'muon', 'worldline_anchor_small', 'task', ('item', 'worldline_anchor_small'), 2,
     '世界線の錨', 'ワールドライン・アンカー（小）を作る', 'Worldline Anchor', 'Build a small worldline anchor'),
    # ---------------- 段階3
    ('quantum', 'assembler', 'quantum_computing_module', 'goal', ('item', 'quantum_computing_module'), 3,
     '量子の時代', '量子演算モジュールを作る', 'Quantum Age', 'Make a quantum computing module'),
    ('entangled', 'quantum', 'entangled_element', 'task', ('item', 'entangled_element'), 3,
     '不気味な遠隔作用', '量子もつれ素子を作る', 'Spooky Action', 'Make entangled elements'),
    ('ruin_research', 'quantum', 'quantum_data_fragment', 'task', ('milestone', 'discover/research_building'), 3,
     '研究棟', '研究棟の保管庫を開ける', 'Research Building', 'Open a research building cache'),
    ('bose', 'entangled', 'bose_condensate_catalyst', 'task', ('item', 'bose_condensate_catalyst'), 3,
     '凝縮の合唱', 'BE凝縮触媒を作る', 'Condensate Choir', 'Make a BE condensate catalyst'),
    ('gauntlet', 'quantum', 'inertial_control_gauntlet', 'task', ('item', 'inertial_control_gauntlet'), 3,
     '慣性を手に', '慣性制御ガントレットを作る', 'Inertia in Hand', 'Make an inertial control gauntlet'),
    ('detector', 'quantum', 'gravitational_wave_detector', 'task', ('item', 'gravitational_wave_detector'), 3,
     '時空のさざ波', '重力波検出器を作る', 'Ripples in Spacetime', 'Build a gravitational wave detector'),
    # ---------------- 段階4
    ('compactor', 'bose', 'degenerate_compactor_controller', 'goal', ('milestone', 'formed/degenerate_compactor'), 4,
     '縮退圧', '縮退圧縮炉を組み上げる', 'Degeneracy Pressure', 'Form a degenerate compactor'),
    ('ruin_culture', 'bose', 'culture_data', 'task', ('milestone', 'discover/culture_facility'), 4,
     '封鎖区画', '封鎖培養施設の保管庫を開ける', 'Sealed Section', 'Open a culture facility cache'),
    ('crystal', 'ruin_culture', 'time_crystal_catalyst', 'goal', ('item', 'time_crystal_catalyst'), 4,
     '時間の結晶', '時間結晶触媒を育てる', 'Crystal of Time', 'Grow a time crystal catalyst'),
    ('cavity', 'compactor', 'casimir_cavity_controller', 'task', ('milestone', 'formed/casimir_cavity'), 4,
     '何もない空間の力', 'C空洞を組み上げる', 'The Force of Nothing', 'Form a C-Cavity'),
    ('exotic', 'cavity', 'exotic_matter', 'goal', ('item', 'exotic_matter'), 4,
     '負のエネルギー', 'エキゾチック物質を作る', 'Negative Energy', 'Make exotic matter'),
    ('furnace', 'compactor', 'degenerate_furnace_controller', 'task', ('milestone', 'formed/degenerate_furnace'), 4,
     '中性子星の炉', '縮退熱炉を組み上げる', 'Neutron Star Furnace', 'Form a degenerate furnace'),
    ('probe', 'crystal', 'probe_station', 'task', ('item', 'probe_station'), 4,
     '無人探査', '自動探査機ステーションを作る', 'Unmanned Expedition', 'Build a probe station'),
    # ---------------- 段階5
    ('ruin_final', 'exotic', 'anomaly_sample', 'goal', ('milestone', 'discover/final_lab'), 5,
     '重力異常点', '最終実験施設にたどり着く', 'Gravitational Anomaly', 'Reach the final lab'),
    ('warden', 'ruin_final', 'seal_console', 'challenge', ('kill', 'singulo:horizon_warden'), 5,
     '地平線の番人', 'ホライズン・ウォーデンを倒す', 'Horizon Keeper', 'Defeat the Horizon Warden'),
    ('seed', 'warden', 'singularity_seed', 'task', ('item', 'singularity_seed'), 5,
     '特異点の種', '特異点の種を目覚めさせる', 'Seed of a Singularity', 'Awaken a singularity seed'),
    ('reactor', 'seed', 'core_controller', 'goal', ('milestone', 'formed/penrose_reactor'), 5,
     'ジャイロスコープ', 'Pリアクターを組み上げる', 'Gyroscope', 'Form a P-Reactor'),
    ('ignite', 'reactor', 'core_controller', 'challenge', ('milestone', 'ignite'), 5,
     '事象の地平線', 'リアクターを点火し、ブラックホールを生む', 'Event Horizon', 'Ignite the reactor'),
    ('hawking', 'ignite', 'hawking_condensate', 'task', ('item', 'hawking_condensate'), 5,
     'ホーキングの雫', '回収運転のブラックホールからH凝縮体を受け取る。最初のコアには6個（直接4・アノマリー・サンプルの復元2）',
     'Hawking Drop', 'Collect H condensate from a reactor in harvest mode. The first core needs six'),
    ('core', 'hawking', 'singularity_core', 'goal', ('item', 'singularity_core'), 5,
     'シンギュラリティ', 'シンギュラリティ・コアを作る', 'Singularity', 'Make a singularity core'),
    ('shield', 'core', 'shield_tower_core', 'task', ('milestone', 'formed/event_horizon_shield'), 5,
     '地平線の盾', 'イベントホライズン・シールド発生塔を組み上げる', 'Horizon Shield', 'Form a shield tower'),
    ('tipler', 'core', 'tipler_core', 'task', ('milestone', 'formed/tipler_cylinder'), 5,
     '時間を回す', 'Tシリンダーを組み上げる', 'Spinning Time', 'Form a T-Cylinder'),
    ('wormhole', 'core', 'wormhole_port', 'challenge', ('milestone', 'wormhole_open'), 5,
     '橋', 'ワームホールの口をつなぐ', 'Bridge', 'Connect a pair of wormhole mouths'),
    ('dark', 'ignite', 'halo_collector', 'task', ('milestone', 'dark_matter'), 5,
     '見えない質量', 'ダークマターを集める', 'Invisible Mass', 'Collect dark matter'),
    ('metric', 'core', 'metric_drive', 'task', ('item', 'metric_drive'), 5,
     '計量を曲げる', 'メトリック・ドライブを作る', 'Bending the Metric', 'Make a metric drive'),
    ('manipulator', 'core', 'graviton_manipulator', 'task', ('item', 'graviton_manipulator'), 5,
     '重力子', 'グラビトン・マニピュレーターを作る', 'Graviton', 'Make a graviton manipulator'),
    ('star_core', 'core', 'artificial_star_core', 'task', ('item', 'artificial_star_core'), 5,
     '星を作る', '人工星核を作る。もうネザースターを探さなくていい', 'Forging a Star', 'Make an artificial star core'),
    ('awakened', 'core', 'awakening_record_core', 'challenge', ('milestone', 'awakened_warden'), 5,
     '目覚めた地平線', '覚醒したホライズン・ウォーデンを倒す', 'Horizon Reawakened', 'Defeat the Awakened Horizon Warden'),
    ('awakened_echo', 'hawking', 'awakening_echo_core', 'challenge', ('milestone', 'awakened_echo_sentinel'), 5,
     '鳴りやまない残響', '覚醒した残響の番人を倒す', 'Echoes That Linger', 'Defeat the Awakened Echo Sentinel'),
    ('awakened_gravity', 'hawking', 'awakening_gravity_core', 'challenge', ('milestone', 'awakened_gravity_remnant'), 5,
     '重さの記憶', '覚醒した重力の澱を倒す', 'Memory of Weight', 'Defeat the Awakened Gravity Remnant'),
    ('ruin_sunken', 'warden', 'reactor_shell', 'goal', ('milestone', 'discover/sunken_reactor'), 5,
     '沈んだ炉', '沈んだ炉心区画の保管庫を開ける', 'The Sunken Reactor', 'Open the cache of a Sunken Reactor Sector'),
    ('leviathan', 'ruin_sunken', 'ergosphere_chapter', 'challenge', ('kill', 'singulo:ergosphere_leviathan'), 5,
     '渦を泳ぐもの', 'エルゴ球の大蛇を倒す', 'It Swims the Ergosphere', 'Defeat the Ergosphere Leviathan'),
    ('awakened_leviathan', 'leviathan', 'awakening_ergosphere_core', 'challenge', ('milestone', 'awakened_ergosphere_leviathan'), 5,
     '目覚めた渦', '覚醒したエルゴ球の大蛇を倒す', 'The Vortex Wakes', 'Defeat the Awakened Ergosphere Leviathan'),
    ('archivist', 'awakened', 'archivist_halo', 'challenge', ('milestone', 'last_archivist'), 5,
     '最後の頁', '最後の記録者を眠らせる', 'The Last Page', 'Lay the Last Archivist to rest'),
    ('records', 'archive', 'record_fragment', 'challenge', ('milestone', 'records_all'), 5,
     '滅びの記録', '旧文明の記録をすべて解読する', 'Record of the Fall', 'Decode every ancient record'),
    ('genesis_formed', 'leviathan', 'genesis_core', 'goal', ('milestone', 'genesis_formed'), 5,
     '揺籃', '創世炉を組み上げる', 'The Cradle', 'Assemble a Genesis Engine'),
    ('genesis_birth', 'genesis_formed', 'primordial_seed', 'challenge', ('milestone', 'genesis_birth'), 5,
     '最初の頁', '創世炉で宇宙を生む', 'The First Page', 'Give birth to a universe in the Genesis Engine'),
    ('synth_legendary', 'genesis_birth', 'genesis_pylon', 'challenge', ('milestone', 'synth_legendary'), 5,
     '無から有へ', '創世炉で伝説の物（希少度4）を作る', 'Something from Nothing', 'Synthesize a legendary item (rarity 4) in the Genesis Engine'),
]


# 0.8.0 第6段階: 既存IDと done 条件名は維持して達成履歴を引き継ぐ。
# parent は表示上の導線であり、製品のティア解禁や実績の発火を制限しない。
# 探索を工業の枝へ埋め込まず、通常ボスから分岐を見渡せるようにする。
PARENTS = {
    'circuit': 'steel', 'ceramic': 'steel', 'archive': 'circuit',
    'compressor': 'thermo', 'projector': 'compressor', 'tower': 'compressor',
    'quantum': 'muon', 'ruin_research': 'ruin_observation', 'bose': 'entangled',
    'ruin_culture': 'echo', 'crystal': 'gravity', 'probe': 'ruin_research',
    'ruin_final': 'gravity', 'ruin_sunken': 'warden',
    'awakened_echo': 'echo', 'awakened_gravity': 'gravity', 'awakened': 'warden',
    'records': 'archive', 'archivist': 'awakened',
}
# ラストヒットに依存させない。同じ部屋にいる生存中の挑戦者へ報酬と同じ範囲で発火する。
CONDITIONS = {
    'warden': ('milestone', 'defeat/horizon_warden'),
    'leviathan': ('milestone', 'defeat/ergosphere_leviathan'),
}
DESCRIPTIONS = {
    'tower': ('極低温冷却塔を組み上げる。観測拠点の回収物で超伝導コイルへ進もう',
              'Form a cryogenic cooling tower. Recover observation-post materials to make superconducting coils'),
    'ruin_final': ('重力の澱を越え、最終実験施設の入口にたどり着く',
                   'After the Gravity Remnant, reach the entrance to the final lab'),
    'warden': ('ホライズン・ウォーデン討伐時、挑戦者として部屋にいる',
               'Be a surviving challenger in the arena when the Horizon Warden falls'),
    'leviathan': ('エルゴ球の大蛇討伐時、挑戦者として部屋にいる',
                  'Be a surviving challenger in the arena when the Ergosphere Leviathan falls'),
}
ADVANCEMENTS = [
    (aid, PARENTS.get(aid, parent), icon, frame, CONDITIONS.get(aid, cond), stage,
     jt, DESCRIPTIONS.get(aid, (jd, ed))[0], et, DESCRIPTIONS.get(aid, (jd, ed))[1])
    for aid, parent, icon, frame, cond, stage, jt, jd, et, ed in ADVANCEMENTS
]
ADVANCEMENTS += [
    ('compass', 'archive', 'meteor_compass', 'task', ('item', 'meteor_compass'), 1,
     '次の痕跡へ', '探索コンパスを手に入れ、探索先を選ぶ', 'The Next Trace', 'Obtain an exploration compass to choose your next expedition'),
    ('echo', 'ruin_research', 'echo_resonator', 'challenge', ('milestone', 'defeat/echo_sentinel'), 3,
     '残響を越えて', '残響の番人討伐時、挑戦者として部屋にいる', 'Beyond the Echo', 'Be a surviving challenger in the arena when the Echo Sentinel falls'),
    ('gravity', 'ruin_culture', 'culture_data', 'challenge', ('milestone', 'defeat/gravity_remnant'), 4,
     '重さをほどく', '重力の澱討伐時、挑戦者として部屋にいる', 'Unravelling Gravity', 'Be a surviving challenger in the arena when the Gravity Remnant falls'),
    ('lab_power', 'echo', 'reactor_console', 'goal', ('milestone', 'lab_power'), 4,
     '眠る工場に灯を', '地下アンドロイド研究所の動力復旧に立ち会う', 'Lights in the Sleeping Factory', 'Be present when power returns to the underground android laboratory'),
    ('foundry', 'lab_power', 'factory_line', 'challenge', ('milestone', 'defeat/foundry_overseer'), 4,
     '最後の命令', '統括AIファウンドリ討伐時、挑戦者として部屋にいる', 'The Last Directive', 'Be a surviving challenger in the arena when the Foundry Overseer falls'),
    ('ruin_android_lab', 'foundry', 'precision_actuator', 'goal', ('milestone', 'discover/android_lab'), 4,
     '保たれた研究所', '地下アンドロイド研究所の保管庫を開ける', 'The Preserved Laboratory', 'Open the cache of the underground android laboratory'),
    ('awakened_foundry', 'foundry', 'awakening_foundry_core', 'challenge', ('milestone', 'awakened_foundry_overseer'), 5,
     '再起動する意思', '覚醒した統括AIファウンドリを倒す', 'A Will Rebooted', 'Defeat the Awakened Foundry Overseer'),
    ('android_station', 'assembler', 'android_station', 'goal', ('item', 'android_station'), 2,
     '迎える準備', 'アンドロイド組立ステーションを手に入れる。メモリ・キット・電力を用意しよう',
     'Ready to Welcome', 'Obtain an Android Assembly Station; prepare a memory, a kit and power'),
    ('android_memory', 'android_station', 'android_memory_blade', 'task', ('any_item', 'android_memory_blade', 'android_memory_whip', 'android_memory_saber'), 2,
     '小さな設計図', '遺構の印刷台から旧型の設計メモリを1種類手に入れる', 'A Little Blueprint', 'Obtain any ordinary android design memory from a ruin fabricator'),
    ('android_printed', 'android_memory', 'android_kit', 'goal', ('milestone', 'android_printed'), 2,
     'はじめまして', '自分の組立ステーションで護衛の印刷を完了する', 'Hello There', 'Finish printing a guard at your own assembly station'),
    ('android_charger', 'android_printed', 'android_charger', 'task', ('item', 'android_charger'), 2,
     'そばで充電', 'アンドロイド用無線充電器を手に入れる', 'Charge Nearby', 'Obtain an Android Wireless Charger'),
    ('android_work', 'android_printed', 'android_work_console', 'goal', ('item', 'android_work_console'), 2,
     '仕事を頼もう', '作業指令台を手に入れる。素材置き場とレシピをつなごう', 'A Job to Do', 'Obtain a Work Console; connect supplies and recipes'),
    ('android_recipe', 'android_work', 'recipe_card', 'task', ('item', 'recipe_card'), 2,
     '作り方を渡す', 'レシピカードを手に入れ、作業指令台で作り方を記録しよう', 'Pass on a Recipe', 'Obtain a Recipe Card and record a recipe at the Work Console'),
    ('android_delivery', 'android_recipe', 'android_delivery_port', 'goal', ('milestone', 'android_order_complete'), 2,
     'できました', '自分が頼んだ注文を必要数まで納品してもらう', 'Order Ready', 'Receive the full requested quantity of an order you placed'),
    ('android_rack', 'android_recipe', 'android_recipe_rack', 'task', ('item', 'android_recipe_rack'), 2,
     '工場の手引き', 'アンドロイドレシピ棚を手に入れる', 'Factory Handbook', 'Obtain an Android Recipe Rack'),
    ('android_routes', 'android_work', 'area_card', 'task', ('item', 'area_card'), 2,
     '通り道を考える', '範囲カードを手に入れる。地点カードと合わせて経路を決めよう', 'Plan the Way', 'Obtain an Area Card; combine it with location cards to plan routes'),
    ('android_survey', 'android_routes', 'survey_glasses', 'task', ('item', 'survey_glasses'), 2,
     '見える道しるべ', '測量メガネを手に入れる', 'Visible Waypoints', 'Obtain Survey Glasses'),
    ('android_prototypes', 'foundry', 'android_memory_bit', 'challenge', ('all_items', 'android_memory_axe', 'android_memory_bit'), 4,
     '二つの可能性', 'アルマとノクスの設計メモリを両方そろえて持つ（統括AIを2回倒せばそろう）',
     'Two Possibilities', 'Hold both Alma and Nox design memories together (two Overseer victories guarantee both)'),
    ('android_factory_print', 'ruin_android_lab', 'android_factory_print_unit', 'task', ('item', 'android_factory_print_unit'), 4,
     '工場の技術を拠点へ', '工場式印刷ユニットを手に入れる', 'Factory Technology at Home', 'Obtain a Factory Print Unit'),
]


def validate_advancements():
    """木の循環・孤立・未定義親・段階逆転を、生成より前に止める。"""
    by_id = {a[0]: a for a in ADVANCEMENTS}
    if len(by_id) != len(ADVANCEMENTS):
        raise ValueError('duplicate advancement ID')
    if [a[0] for a in ADVANCEMENTS if a[1] is None] != ['root']:
        raise ValueError('expected exactly one root')
    for aid, parent, icon, frame, cond, stage, *_ in ADVANCEMENTS:
        seen = {aid}
        node = parent
        while node is not None:
            if node not in by_id or node in seen:
                raise ValueError(f'{aid}: missing parent or cycle at {node}')
            seen.add(node)
            if by_id[node][5] > stage:
                raise ValueError(f'{aid}: parent has a later tier: {node}')
            node = by_id[node][1]
        if frame not in ('task', 'goal', 'challenge') or cond[0] not in ('tick', 'item', 'any_item', 'all_items', 'milestone', 'kill'):
            raise ValueError(f'{aid}: invalid frame or criterion')
