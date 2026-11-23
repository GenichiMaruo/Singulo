"""Navigation and illustrations for the in-game handbook. Page IDs also serve as link targets."""

# Each topic has a representative item in the chapter contents and search results.
TOPIC_ICONS = {
    'intro.welcome': 'handbook',
    'intro.howto': 'minecraft:book',
    'intro.basics': 'copper_wire',

    'stage1.flow': 'thermoelectric_generator',
    'stage1.materials': 'steel_ingot',
    'stage1.power': 'thermoelectric_generator',
    'stage1.thermal_pairs': 'minecraft:blue_ice',
    'stage1.machines': 'compressor',
    'stage1.steel': 'kiln',

    'stage2.flow': 'superconducting_coil',
    'stage2.tower': 'cooling_tower_controller',
    'stage2.accelerator': 'accelerator_controller',
    'stage2.others': 'precision_assembler',

    'stage3.flow': 'quantum_computing_module',
    'stage3.quantum': 'laser_cooler',

    'stage4.flow': 'time_crystal_catalyst',
    'stage4.compression': 'degenerate_compactor_controller',
    'stage4.time': 'time_crystal_incubator',
    'stage4.power': 'degenerate_furnace_controller',

    'stage5.flow': 'singularity_core',
    'stage5.reactor': 'reactor_core_controller',
    'stage5.ignite': 'singularity_seed',
    'stage5.byproducts': 'hawking_condensate',

    'multiblocks.projector': 'holo_projector',

    'catalysts.tiers': 'muon_catalyst',
    'catalysts.slots': 'spent_muon_catalyst',
    'catalysts.time': 'time_crystal_module',

    'power.cables': 'copper_wire',
    'power.sides': 'multiblock_port',
    'power.relocation': 'relocation_wrench',
    'power.wormhole': 'wormhole_port',
    'power.capacity': 'capacity_upgrade_1',

    'ruins.list': 'explorer_compass',
    'ruins.compass': 'explorer_compass',
    'ruins.records': 'crystal_memory',
    'ruins.antechamber': 'ruin_relay',

    'androids.build': 'android_station',
    'androids.first': 'android_work_console',
    'androids.patrol': 'android_armor_plate',
    'androids.routes': 'survey_glasses',
    'androids.care': 'android_repair_kit',
    'androids.work': 'android_recipe_rack',
    'androids.process': 'android_mining_upgrade',
    'androids.farm': 'android_farming_upgrade',
    'androids.trouble': 'android_supply_port',

    'drones.start': 'drone_station',
    'drones.send': 'packing_crate',
    'drones.range': 'drone_station',
    'drones.more': 'drone_port',
    'drones.route': 'drone_phase_upgrade',
    'drones.auto': 'drone_port',

    'romance.shield': 'shield_tower_core',
    'romance.tipler': 'tipler_core',
    'romance.gear': 'inertial_control_gauntlet',
    'romance.darkmatter': 'gravitational_containment_tank',

    'genesis.build': 'genesis_frame',
    'genesis.birth': 'genesis_core',
    'genesis.synth': 'singularity_core',
    'genesis.galaxies': 'genesis_pylon',

    'jet_forge.build': 'jet_forge_core',
    'jet_forge.run': 'jet_forge_confinement_coil',
    'jet_forge.ingot': 'collapsar_ingot',

    'hazards.list': 'minecraft:tnt',

    'factory_expansion.probes': 'probe_station',
    'factory_expansion.limits': 'mass_pellet',

    'cosmic.storage': 'pasta_accumulator',
    'cosmic.production': 'tidal_spindle',
    'cosmic.links': 'wormhole_seam_upgrade',

    'fluids.pipes': 'fluid_pipe',
    'fluids.drone': 'fluid_canister',

    'collapsar.lining': 'collapsar_lining',
    'collapsar.vault': 'collapsar_ingot',
    'collapsar.wormhole': 'wormhole_port',

    'food.printer': 'food_printer',

    'cover.cover': 'cover_frame',
}

# Only links whose destination is visible are offered in the book.
ROUTES = {
    'intro.welcome': ['stage1.flow', 'stage2.flow', 'stage3.flow', 'stage4.flow', 'stage5.flow',
                      'ruins.list', 'power.sides', 'multiblocks.projector'],
    'stage1.flow': ['stage1.materials', 'stage1.power', 'stage1.machines', 'ruins.list', 'stage1.steel', 'stage2.tower'],
    'stage2.flow': ['stage2.others', 'stage2.accelerator', 'stage2.tower', 'ruins.compass'],
    'stage3.flow': ['stage3.quantum', 'catalysts.slots', 'ruins.compass'],
    'stage4.flow': ['stage4.time', 'stage4.power', 'stage4.compression', 'ruins.compass'],
    'stage5.flow': ['stage5.reactor', 'stage5.ignite', 'stage5.byproducts', 'genesis.build'],
}

ACTION_TITLES = {
    'stage1.materials': ('鋼鉄・セラミック・回路を作る', 'Craft steel, ceramic and circuits'),
    'stage1.power': ('熱電発電機で電力を取る', 'Generate thermoelectric power'),
    'stage1.machines': ('最初の加工機をそろえる', 'Build your first processing machines'),
    'ruins.list': ('探索コンパスを試す', 'Try the Explorer’s Compass'),
    'stage2.tower': ('冷却塔を組んで冷媒を作る', 'Build a cooling tower and produce coolant'),
    'stage2.others': ('精密組立台を用意する', 'Build a Precision Assembler'),
    'stage2.accelerator': ('ミュオン束から触媒を作る', 'Make catalysts from muon bundles'),
    'ruins.compass': ('コンパスを調整する', 'Tune your compass'),
    'stage3.quantum': ('量子の加工・発電設備をそろえる', 'Build quantum machines and power'),
    'stage4.time': ('時間結晶触媒を育てる', 'Grow time crystal catalysts'),
    'stage4.power': ('縮退熱炉で発電する', 'Generate power in a Degenerate Furnace'),
    'stage4.compression': ('縮退物質殻をプレスする', 'Press degenerate matter shells'),
    'stage5.ignite': ('電力を蓄えて炉心を点火する', 'Store energy and ignite the core'),
    'stage5.byproducts': ('副産物からコアを作る', 'Turn reactor byproducts into a core'),
}

# These captions accompany diagrams that already cover the page's main comparison.
CAPTIONS = {
    'stage1.thermal_pairs': ('発電機1台の目安。熱源と冷却材を発電機の両側に置きます。', 'Output per generator. Place the heat source and coolant on opposite sides.'),
    'stage1.steel': ('鋼鉄4個を作るときの合計。電解槽5回分の酸素で焼成炉を2回動かせます。', 'Totals for four steel ingots. Five electrolysis cycles supply two kiln batches.'),
}

# A row describes one operation or comparison. Numbers are printed below the actual item/fluid sprites.
def item(identifier, amount=''):
    return {'item': identifier if ':' in identifier else 'singulo:' + identifier, 'amount': str(amount)}


def fluid(identifier, mb):
    return {'fluid': identifier if ':' in identifier else 'singulo:' + identifier, 'amount': f'{mb:,} mB'}


def row(title, inputs, outputs=None, reading=None):
    result = {'title': title, 'inputs': inputs}
    if outputs:
        result['outputs'] = outputs
    if reading:
        result['reading'] = reading
    return result


DIAGRAMS = {
    'stage1.steel': [
        row('block.singulo.electrolyzer', [fluid('minecraft:water', 1000)], [fluid('oxygen', 500), fluid('hydrogen', 1000)]),
        row('guide.singulo.visual.oxygen_steel', [item('minecraft:iron_ingot', 4), item('minecraft:coal', 2), fluid('oxygen', 500)], [item('steel_ingot', 4)]),
        row('guide.singulo.visual.blend_steel', [item('minecraft:iron_ingot', 4), item('minecraft:coal', 8)], [item('steel_ingot', 4)]),
    ],
    'stage1.materials': [
        row('block.minecraft.crafting_table', [item('minecraft:iron_ingot', 1), item('minecraft:coal', 2)], [item('steel_blend', 1)]),
        row('block.minecraft.blast_furnace', [item('steel_blend', 1)], [item('steel_ingot', 1)]),
    ],
    'stage1.power': [row('block.singulo.thermoelectric_generator',
                         [item('minecraft:magma_block'), item('thermoelectric_generator'), item('minecraft:blue_ice')],
                         reading='≈76 FE/t')],
    'stage1.thermal_pairs': [
        row(title, [item(hot), item(cold)], reading=power)
        for title, hot, cold, power in [
            ('block.minecraft.magma_block', 'minecraft:magma_block', 'minecraft:blue_ice', '≈76 FE/t'),
            ('guide.singulo.visual.active_kiln', 'kiln', 'minecraft:blue_ice', '≈66 FE/t'),
            ('block.minecraft.campfire', 'minecraft:campfire', 'minecraft:blue_ice', '≈56 FE/t'),
            ('block.minecraft.campfire', 'minecraft:campfire', 'minecraft:packed_ice', '≈55 FE/t')]
    ],
    'stage1.machines': [
        row('block.singulo.peltier_cooler', [fluid('minecraft:water', 1000)], [item('minecraft:ice', 1)]),
        row('block.singulo.compressor', [item('minecraft:ice', 4)], [item('minecraft:packed_ice', 1)]),
        row('block.singulo.compressor', [item('minecraft:packed_ice', 4)], [item('minecraft:blue_ice', 1)]),
    ],
    'stage2.accelerator': [row('block.singulo.accelerator_controller', [fluid('hydrogen', 50)], [item('muon_bundle', 1)])],
    'stage3.quantum': [row('block.singulo.quantum_heat_engine', [item('muon_catalyst')], reading='50 kFE/t'),
                       row('block.singulo.quantum_heat_engine', [item('bose_condensate_catalyst')], reading='200 kFE/t')],
    'jet_forge.ingot': [row('block.singulo.jet_forge_core',
                            [item('collapsar_billet', 1), item('mass_pellet', 16), fluid('liquid_helium', 1000)],
                            [item('collapsar_ingot', 1)])],
    'androids.first': [row('block.minecraft.crafting_table', [item('minecraft:wheat', 3)], [item('minecraft:bread', 1)])],
}

# Same blueprint IDs as the in-world Holo Projector; the client supplies the actual blocks.
BLUEPRINTS = {
    'stage2.tower': 'cooling_tower', 'stage2.accelerator': 'particle_accelerator',
    'stage4.compression': 'degenerate_compactor', 'stage4.power': 'degenerate_furnace',
    'stage5.reactor': 'penrose_reactor', 'romance.shield': 'event_horizon_shield',
    'romance.tipler': 'tipler_cylinder', 'genesis.build': 'genesis_engine',
    'jet_forge.build': 'accretion_jet_forge', 'cosmic.production': 'tidal_spindle',
}

# Machine instructions follow industrial progress. The tower's assembly drawing is
# useful before the first tower forms, so that page has its own earlier condition.
CHAPTER_STAGES = {
    'stage2': 2, 'stage3': 3, 'stage4': 4, 'stage5': 5, 'catalysts': 2, 'drones': 2,
    'factory_expansion': 4, 'romance': 5, 'cosmic': 5, 'food': 2,
}
PAGE_STAGES = {'stage2.tower': 1, 'power.wormhole': 5, 'power.capacity': 2, 'fluids.drone': 2,
               'catalysts.time': 4, 'factory_expansion.limits': 5}

# Advancement IDs record personal discoveries. An item/ entry also accepts equipment
# the reader has in their inventory or the block they are examining at a shared base.
ANDROID_STATION = ['android_station', 'android_memory', 'android_printed', 'android_work', 'item/android_station']
ANDROID_WORK = ['android_printed', 'android_work', 'android_rack', 'android_delivery', 'item/android_work_console']
CHAPTER_DISCOVERIES = {
    'androids': ANDROID_STATION,
    'genesis': ['genesis_formed', 'genesis_birth', 'item/genesis_core'],
    'jet_forge': ['jet_forge', 'collapsar', 'item/jet_forge_core'],
    'collapsar': ['collapsar', 'item/collapsar_ingot'],
}
PAGE_DISCOVERIES = {
    'stage2.tower': ['archive', 'ruin_observation', 'tower', 'coil', 'item/cooling_tower_controller'],
    'ruins.compass': ['ruin_observation', 'ruin_research', 'ruin_culture', 'ruin_final', 'item/explorer_compass'],
    'ruins.records': ['archive', 'records', 'item/crystal_memory'],
    'stage5.byproducts': ['ignite', 'hawking', 'core'],
    'genesis.birth': ['genesis_formed', 'genesis_birth'],
    'genesis.galaxies': ['genesis_birth'],
    'genesis.synth': ['genesis_birth'],
    'factory_expansion.probes': ['probe', 'item/probe_station'],
    'androids.first': ANDROID_WORK, 'androids.routes': ANDROID_WORK, 'androids.work': ANDROID_WORK,
    'androids.process': ANDROID_WORK, 'androids.farm': ANDROID_WORK, 'androids.trouble': ANDROID_WORK,
    'androids.care': ['android_printed', 'android_charger', 'android_work'],
    'androids.patrol': ['android_printed', 'android_routes', 'android_work'],
}


def visuals_for(chapters):
    pages = {f'{cid}.{p[0]}' for cid, _, _, _, entries in chapters for p in entries}
    if pages != TOPIC_ICONS.keys():
        raise ValueError(f'Handbook topics and icons differ: {pages ^ TOPIC_ICONS.keys()}')
    targets = (set(ROUTES) | set(DIAGRAMS) | set(BLUEPRINTS) | set(ACTION_TITLES) | set(CAPTIONS)
               | set(PAGE_DISCOVERIES) | set(PAGE_STAGES)
               | {pid for route in ROUTES.values() for pid in route})
    if not targets <= pages:
        raise ValueError(f'Unknown handbook page: {targets - pages}')

    visuals = {}
    for page, icon in TOPIC_ICONS.items():
        info = {'icon': item(icon)['item']}
        chapter = page.split('.')[0]
        stage = PAGE_STAGES.get(page, CHAPTER_STAGES.get(chapter, 1))
        if stage > 1:
            info['stage'] = stage
        discoveries = PAGE_DISCOVERIES.get(page, CHAPTER_DISCOVERIES.get(chapter))
        if discoveries:
            info['discoveries'] = discoveries
        if page in ROUTES:
            info['links'] = ROUTES[page]
        if page in DIAGRAMS:
            info['rows'] = DIAGRAMS[page]
        if page in ACTION_TITLES:
            info['action'] = f'guide.singulo.action.{page}'
        if page in CAPTIONS:
            info['caption'] = f'guide.singulo.caption.{page}'
        if page in BLUEPRINTS:
            info['blueprint'] = BLUEPRINTS[page]
        if page == 'androids.work':
            info['search_items'] = [item(id)['item'] for id in (
                'android_backpack_upgrade', 'android_reinforced_backpack_upgrade', 'android_large_backpack_upgrade')]
        visuals[page] = info
    return visuals
