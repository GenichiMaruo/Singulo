"""Manual precision assembly diagrams. A, B, ... are indices in the recipe's ingredients array.

Each slash starts a new row; a dot marks an intentional gap. Favor the middle 3×3
unless the part count or the geometry (such as a piston shaft) calls for more space.
Catalysts are loaded in the magazine, outside the diagram.
Machines built on a frame put that frame in the middle cell. Paired tools and coils
mount on opposite sides; collector windows and tower tips sit above the mechanism.
"""
from collections import Counter
from fractions import Fraction
import json
from pathlib import Path

from ids import ACCEPTS_SPENT, ALIAS_RECIPE_IDS, CATALYST_TIERS, MASS_INPUTS, MOD, VANILLA_FLUIDS
from vanilla_catalog import TAG_MEMBERS


EDITS = Path(__file__).with_name('assembly_edits')


DIAGRAMS = {
    # Workstations and their upgrades: the existing work surface sits between drive coils
    # and above its supports. The console has a display over the central frame.
    'advanced_android_workbench': 'DBD/CAC/D.D',
    'quantum_android_workbench': 'BCB/DAD',
    'android_work_console': 'BEB/BAB/CDC',
    'android_station': 'EDE/BAB/CDC',
    'android_charger': 'DAD/BCB/DAD',
    'android_factory_print_unit': 'BCB/AAA/DCD',
    'portable_android_printer': 'A/CDC/BADAB/EAE',
    'portable_android_printer_2': 'EBE/DCACD/E.E',
    # The four tool upgrades share a mount: tool above, steel around the coil, circuit below.
    'android_kit': 'E/A.A/BCB/ADA/D',
    'android_armor_plate': 'ABA/ABCBA/ABA',
    'android_backpack_upgrade': 'B.B/CAC/D',
    'android_reinforced_backpack_upgrade': 'C.C/BAB/CDC',
    'android_large_backpack_upgrade': 'CBC/DAD/CBC',
    'android_battery_upgrade': 'D/ABA/BCB/ABA',
    'android_tank_upgrade': 'B.B/ADA/C.C',
    'android_gas_tank_upgrade': 'D/BAB/C.C',
    'android_dual_tank_upgrade': './ADB/C.C',
    'android_transfer_upgrade': 'ADA/B/C',
    'android_production_upgrade': 'A.A/BCB/D.D',
    'android_farming_upgrade': 'A/BCB/D',
    'android_felling_upgrade': 'A/BCB/D',
    'android_fishing_upgrade': 'A/BCB/D',
    'android_mining_upgrade': 'A/BCB/D',
    'capacity_upgrade_2': 'C.C/BAB/C.C',
    'capacity_upgrade_3': 'DCD/BAB/D.D',
    'drone_kit': 'ADA/CEC/B.B',
    'drone_phase_upgrade': 'ACA/DBD/A.A',
    'neutrino_scanner_module_2': 'A.A/CBC',
    'neutrino_scanner_module_3': 'B/A/C',
    # Casimir cavity: opposed mirrors, vacuum piston, shielding and framing.
    'casimir_cavity_controller': 'CBC/EAE/DFD',
    'casimir_cavity_frame': 'A/ADA/B',
    'casimir_cavity_frame_pair': '...A./..ADA/.A.B./ADA../.B...',
    'casimir_cavity_mirror_plate': 'B.B/C/E/A',
    'casimir_cavity_mirror_plate_pair': '...A./..BCB/.A.E./BCB../.E...',
    'casimir_cavity_pump': 'C/AEA/B',
    'casimir_cavity_shield': 'C/BEA',
    'casimir_cavity_wall': 'ADA/B',
    'casimir_cavity_window': 'B/ADA',
    # Compactor: opposed ram heads, anvil face, base and inlet slots.
    'degenerate_compactor_anvil': 'CAC/BEB/A',
    'degenerate_compactor_controller': 'CBC/DAD/CEC',
    'degenerate_compactor_frame': 'AAA/.D./.B.',
    'degenerate_compactor_frame_pair': '...A./..ABA/.A.D./ABA../.D...',
    'degenerate_compactor_plate': 'B/ADA',
    'degenerate_compactor_ram': 'C/AEA/B.B',
    'degenerate_compactor_vent': 'B/ADA',
    'degenerate_compactor_window': 'A.A/D/B',
    # Furnace: chamber cross-section, inward pistons, vertical exhaust and fins.
    'degenerate_furnace_controller': 'DCD/CBABC/DCD/E',
    'degenerate_furnace_fin': 'AB/D/AB',
    'degenerate_furnace_frame': 'A.A/D/B/A',
    'degenerate_furnace_frame_pair': '...A./..ADA/.A.B./ADA../.B...',
    'degenerate_furnace_piston': 'C/AEA/B.B',
    'degenerate_furnace_shell': 'B/ADA',
    'degenerate_furnace_tube': 'A/ADB/A',
    'degenerate_furnace_window': 'ADA/B',
    # Jet forge: feet, tower truss, electromagnetic confinement and axial collector.
    'jet_forge_foundation': 'C/BEB/AAA/A',
    'jet_forge_truss': 'A/B/D/A.A',
    'jet_forge_levitator': 'C/BDB/A',
    'jet_forge_confinement_coil': 'C/BAB/D/C',
    'jet_forge_collimator': 'DBD/AEA/C',
    'jet_forge_heat_sink': 'C.C/BAB/CDC',
    'jet_forge_collector': 'B/A/E/C',
    # Shield tower: launch plinth, upright waveguide, windings and radiating crown.
    'shield_tower_plinth': 'AAA/BDB',
    'shield_tower_body': 'ABA/ACA/ABA/D',
    'shield_tower_waveguide': 'B/ADA',
    'shield_tower_coil': 'A/BDB/A',
    'shield_tower_crown': 'A.A/BCB/C/D',
    'shield_tower_core': 'CEDEC/CBABC/CEDEC/F',
    # Tipler cylinder: open bearing, shaft and a clock mounted above the core frame.
    'tipler_bearing': 'AA./BDB/.AA',
    'tipler_frame': 'A/BD./A/A',
    'tipler_housing': 'B/ADA',
    'tipler_window': 'ADA/B',
    'tipler_holder': 'C.C/BDB/E/A',
    'tipler_core': 'D/CCC/CBABC/CCC/E',
    # Reactor housing modules, the rotator and its mass-monitoring circuit.
    'reactor_core_controller': 'CDC/BAB/CDC/E',
    'reactor_extraction_port': 'B.B/A/C',
    'reactor_gyro_drive': 'CBC/DED/AGA',
    'reactor_mass_alarm': 'BCB/BAB',
    'reactor_stabilizer': 'C/BAB',
    'genesis_frame': 'ABA/DCD',
    'genesis_pylon': 'C/B/ABA/E',
    # Wormholes: field loop around a core, focusing optics and socketed junctions.
    'wormhole_generator_core': 'ECE/ECDCE/CBABC/ECDCE/FCF',
    'wormhole_generator_coil': 'A/BDB/A',
    'wormhole_generator_focuser': 'DAD/BCB/E/A',
    'wormhole_generator_port': 'C/A/B',
    'wormhole_generator_shell': 'ACA/BDB/ACA/E',
    'wormhole_generator_window': 'ADA/B',
    'wormhole_mouth_casing': 'ABA/DCD/ABA',
    'wormhole_port': 'A/CBC',
    'wormhole_seam_upgrade': 'ABA/BC.CB/ABA',
    'wormhole_stabilizer': 'B/DCD/CAC/DCD',
    # Gravity panels: top surface, drive & receiver, then substrate; field devices
    # place the control processor or specimen in the field of the paired coils.
    'gravity_panel_receiver': 'C/BAB/D.D',
    'high_gravity_panel': 'AAA/B/CAC',
    'low_gravity_panel': 'CAC/B/AAA',
    'impact_generator_panel': 'B.B/CAC/BDB',
    'zero_gravity_block': 'ABA/C.C/ABA',
    'zero_gravity_controller': 'D/BAB/C',
    'advanced_worldline_anchor': 'B/EDE/DCACD/EDE',
    'inertial_stabilizer': 'CDC/BAB/CDC',
    'inertial_control_gauntlet': 'AAA/BCB/D.D',
    'gravity_field_stabilizer': 'CAC/BAB/CAC',
    'gravitational_wave_detector': 'CEC/DAD/B',
    'graviton_manipulator': 'BEB/CAD/BFB',
    'gravitational_containment_tank': 'DCD/EBABE/D.D',
    'metric_drive': 'A.A/BCDCB/ADA',
    'confinement_ring': 'ABA/C.C/ABA',
    'toroidal_magnetic_coil': 'AAA/BA.AB/AAA',
    'ergosphere_ring': 'C/CBC/CBABC/CBC/C',
    'magnetic_bottle': 'D/CAC/BA.AB/CAC',
    'empty_black_hole_vessel': 'C/ABA/A.A',
    # Collector inlets and sensor arrays above their drive or readout.
    'deep_sea_collector': 'D/EBE/BCACB/BBB',
    'void_collector': 'B/CEC/DAD/CEC',
    'halo_collector': 'C/BDB/DAD/BDB',
    'hawking_collector': 'ABA/DCD/ABA',
    'jet_collector': 'A.A/BCB/D.D',
    'neutrino_scanner': 'ABA/DCD/D.D',
    'neutrino_observatory': 'B/DAD/C',
    'laser_cooler': 'E.E/BFB/DAD/ECE',
    'entanglement_synthesizer': 'D/EBE/CAC/EBE',
    'echo_resonator': 'E/CFC/BAB/CDC',
    'keraunos_tower': 'C/DBD/DBABD/FBF/E',
    'probe_station': 'E/DFD/CAC/DBD',
    'quantum_heat_engine': 'B/DCD/CAC/DCD',
    'muon_fusion_reactor': 'C/BCB/CEAEC/BCB/C',
    'singularity_encapsulator': 'C/BFB/DAD/BEB',
    'time_crystal_incubator': 'B.B/CEC/DAD/BFB',
    # Insulation shells around a module; higher tiers replace the previous unit.
    'sealed_container_1': 'ADA/BCB/ADA',
    'sealed_container_2': 'C/BAB',
    'sealed_container_3': 'C/A/B',
    'sealed_container_4': 'B/A/C',
    'nihonium_frame': 'B.C/A/C.B',
    'pasta_laminate': 'C/ABD/C',
    'collapsar_parallel_cassette': 'A/DAD/BCACB/DAD',
    'quantum_computing_module': 'ACA/B/A.A',
    'time_crystal_module': 'CBC',
    'degenerate_nucleus_reconstructed': 'C/BAB',
    'sentinel_core_reconstructed': 'B.B/CAC',
    # Keys and access cards: a coded head joined to a toothed shaft.
    'magnetic_key': 'BAC',
    'quantum_key': 'A.A/BCB',
    'singularity_key': 'A/BC',
    'temporal_key': 'B/BAC',
    'shield_permit': 'A/B/C',
}


def assembly_id(name):
    return ALIAS_RECIPE_IDS[name] if name in ALIAS_RECIPE_IDS else MOD[name][0]


def board_materials(rows, items):
    options = []
    for item in items:
        ingredient = item['ingredient']
        if 'tag' in ingredient:
            tag = ingredient['tag']
            # Preserve equality for optional-mod tags absent from the vanilla + Singulo snapshot.
            options.append(frozenset(('#' + tag, *TAG_MEMBERS.get(tag, ()))))
        else:
            options.append(frozenset((ingredient['item'],)))
    return {cell: options[ord(symbol) - ord('A')] for cell, symbol in enumerate(''.join(rows)) if symbol != ' '}


def boards_overlap(left, right):
    # A smaller board can start crafting while a player is still assembling the larger one.
    if len(left) > len(right):
        left, right = right, left
    return all(cell in right and not choices.isdisjoint(right[cell]) for cell, choices in left.items())


def apply_edit(name, recipe, edit):
    """Use the exact positions saved by the editor; keep charges and fluids off the board."""
    rows, key = edit['rows'], edit['key']
    if not isinstance(rows, list) or len(rows) != 5 or any(not isinstance(r, str) or len(r) != 5 for r in rows):
        raise ValueError('盤面は5行×5列で指定してください。')
    if any(rows[y][x] != '.' for y in (0, 4) for x in (0, 4)):
        raise ValueError('四隅には素材を置けません。')
    symbols = set(''.join(rows)) - {'.'}
    if not symbols or not isinstance(key, dict) or set(key) != symbols or len(set(key.values())) != len(key):
        raise ValueError('盤面には素材を1個以上置き、記号と素材を一対一で指定してください。')
    ordered = [chr(65+i) for i in range(len(key))]
    if set(ordered) != symbols:
        raise ValueError('素材記号はAから順に指定してください。')
    offboard = {m: q for m, q in recipe['inputs'].items()
                if m in MASS_INPUTS or m in VANILLA_FLUIDS or (m in MOD and MOD[m][2] == 'fluid')
                or (m in CATALYST_TIERS and (name, m) not in ACCEPTS_SPENT)
                or (isinstance(q, Fraction) and q.denominator != 1)}
    counts = Counter(''.join(rows))
    inputs = {key[ch]: counts[ch] for ch in ordered}
    if inputs.keys() & offboard.keys():
        raise ValueError('専用枠の材料を盤面にも置くことはできません。')
    return {**recipe, 'inputs': {**inputs, **offboard}, 'assembly_diagram': '/'.join(rows)}


def load_edits(recipes, directory=EDITS):
    names = {assembly_id(name): name for name, r in recipes.items() if r['station'] == '精密組立台'}
    for file in sorted(directory.glob('*.json')):
        name = names[file.stem]
        recipes[name] = apply_edit(name, recipes[name], json.loads(file.read_text(encoding='utf-8')))


def assembly_rows(recipe_id, ingredients, diagram=None):
    diagram = (DIAGRAMS[recipe_id] if diagram is None else diagram).split('/')
    if len(diagram) > 5 or any(len(row) > 5 for row in diagram):
        raise ValueError(f'{recipe_id}: assembly diagram exceeds 5×5')
    blank = ' ' * 5
    aligned = []
    for row in diagram:
        left = (5 - len(row)) // 2
        aligned.append((' ' * left + row.replace('.', ' ')).ljust(5))

    # An even-height shape fits equally well above or below the middle line.
    # Put its heavier row nearer the center without moving individual parts.
    padding = 5 - len(aligned)
    candidates = []
    for top in range(padding // 2, (padding + 1) // 2 + 1):
        rows = [blank] * top + aligned + [blank] * (padding - top)
        if all(rows[y][x] == ' ' for y in (0, 4) for x in (0, 4)):
            candidates.append(rows)
    if not candidates:
        raise ValueError(f'{recipe_id}: assembly diagram uses a corner')
    rows = min(candidates, key=lambda layout: abs(sum(
        (y - 2) * len(row.replace(' ', '')) for y, row in enumerate(layout))))
    expected = {chr(ord('A') + i): item.get('count', 1)
                for i, item in enumerate(ingredients) if not item.get('uses')}
    actual = Counter(''.join(rows).replace(' ', ''))
    if actual != expected:
        raise ValueError(f'{recipe_id}: expected {expected}, found {dict(actual)}')
    return rows
