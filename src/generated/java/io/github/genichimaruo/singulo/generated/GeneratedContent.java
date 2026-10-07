package io.github.genichimaruo.singulo.generated;

import java.util.List;
import java.util.Map;

/** tools/gen_data.py が recipes.py から生成。手で編集しない。 */
public final class GeneratedContent {
    private GeneratedContent() {}

    /** kind: item / planned / uses / catalyst。maxUses は使用回数（触媒は寿命tick）。 */
    public record ItemDef(String id, String kind, int stage, int maxUses) {}
    public record BlockDef(String id, int stage) {}
    public record FluidDef(String id, int stage, int color, boolean gas) {}

    public static final List<ItemDef> ITEMS = List.of(
            new ItemDef("steel_blend", "item", 1, 0),
            new ItemDef("steel_ingot", "item", 1, 0),
            new ItemDef("unfired_ceramic", "item", 1, 0),
            new ItemDef("white_ceramic_composite", "item", 1, 0),
            new ItemDef("basic_circuit", "item", 1, 0),
            new ItemDef("thermocouple_module", "item", 1, 0),
            new ItemDef("basic_frame", "item", 1, 0),
            new ItemDef("steel_plate", "item", 1, 0),
            new ItemDef("ceramic_substrate", "item", 1, 0),
            new ItemDef("blank_data_card", "item", 1, 0),
            new ItemDef("data_card_observation_log", "item", 1, 0),
            new ItemDef("mass_pellet", "item", 1, 0),
            new ItemDef("superconducting_coil", "item", 2, 0),
            new ItemDef("superconducting_wire", "item", 2, 0),
            new ItemDef("muon_bundle", "item", 2, 0),
            new ItemDef("muon_catalyst", "catalyst", 2, 24000),
            new ItemDef("monopole_upgrade", "item", 2, 0),
            new ItemDef("data_card_quantum_fragment", "item", 3, 0),
            new ItemDef("quantum_computing_module", "item", 3, 0),
            new ItemDef("entangled_element", "item", 3, 0),
            new ItemDef("cold_atoms", "item", 3, 0),
            new ItemDef("optical_lattice_substrate", "item", 3, 0),
            new ItemDef("bose_condensate_catalyst", "catalyst", 3, 36000),
            new ItemDef("degenerate_matter_shell", "item", 4, 0),
            new ItemDef("data_card_culture_data", "item", 4, 0),
            new ItemDef("time_crystal_catalyst", "catalyst", 4, 54000),
            new ItemDef("exotic_matter", "item", 4, 0),
            new ItemDef("toroidal_magnetic_coil", "item", 4, 0),
            new ItemDef("gravity_field_stabilizer", "item", 5, 0),
            new ItemDef("hawking_collector", "item", 5, 0),
            new ItemDef("jet_collector", "item", 5, 0),
            new ItemDef("ergosphere_ring", "item", 5, 0),
            new ItemDef("jet_condensate", "item", 5, 0),
            new ItemDef("artificial_star_core", "item", 5, 0),
            new ItemDef("hawking_condensate", "item", 5, 0),
            new ItemDef("singularity_seed", "item", 5, 0),
            new ItemDef("singularity_core", "catalyst", 5, 72000),
            new ItemDef("observation_log", "uses", 1, 16),
            new ItemDef("degraded_control_unit", "item", 1, 0),
            new ItemDef("control_unit", "uses", 1, 16),
            new ItemDef("quantum_data_fragment", "uses", 2, 16),
            new ItemDef("degraded_cold_atom_trap", "item", 2, 0),
            new ItemDef("cold_atom_trap", "uses", 2, 32),
            new ItemDef("degraded_time_crystal_seed", "item", 3, 0),
            new ItemDef("time_crystal_seed", "uses", 3, 8),
            new ItemDef("culture_data", "uses", 3, 16),
            new ItemDef("degraded_anomaly_sample", "item", 5, 0),
            new ItemDef("anomaly_sample", "uses", 5, 4),
            new ItemDef("dormant_singularity_seed", "item", 5, 0),
            new ItemDef("spent_muon_catalyst", "item", 2, 0),
            new ItemDef("spent_bose_condensate_catalyst", "item", 3, 0),
            new ItemDef("spent_time_crystal_catalyst", "item", 4, 0),
            new ItemDef("spent_singularity_core", "item", 5, 0),
            new ItemDef("magnetic_monopole", "item", 2, 0),
            new ItemDef("record_fragment", "item", 1, 0)
    );

    public static final List<BlockDef> BLOCKS = List.of(
            new BlockDef("compressed_block_1", 1),
            new BlockDef("compressed_block_2", 4),
            new BlockDef("compressed_block_3", 4),
            new BlockDef("compressed_metal_block_1", 4),
            new BlockDef("compressed_metal_block_2", 4),
            new BlockDef("strange_matter", 1)
    );

    public static final List<FluidDef> FLUIDS = List.of(
            new FluidDef("hydrogen", 1, 0xCCE6F5FF, true),
            new FluidDef("liquid_nitrogen", 2, 0xDDBFEFFF, false),
            new FluidDef("helium", 3, 0xCCF5F0C8, true),
            new FluidDef("liquid_helium", 3, 0xDDE6D2FF, false),
            new FluidDef("axion_condensate", 4, 0xDDA078E6, false),
            new FluidDef("dark_matter", 5, 0xAA2A1E3C, true),
            new FluidDef("oxygen", 1, 0xCCF5E1E1, true)
    );

    /** 遺構ID → 保管庫の中身が再生するまでのゲーム内日数。 */
    /** 旧文明の記録の ID（解読する順）。 */
    public static final List<String> RECORDS = List.of("r01", "r02", "r03", "r04", "r05", "r06", "r07", "r08", "r09", "r10", "r11", "r12", "r13", "r14", "r15", "r16", "r17", "r18", "r19", "r20");

    public static final Map<String, Integer> RUIN_REGEN_DAYS = Map.of(
            "observation_post", 7,
            "research_building", 7,
            "culture_facility", 14,
            "final_lab", 14
    );

    /** 触媒 → 使い切ったときに残る失活触媒。 */
    public static final Map<String, String> SPENT_FORM = Map.of(
            "muon_catalyst", "spent_muon_catalyst",
            "bose_condensate_catalyst", "spent_bose_condensate_catalyst",
            "time_crystal_catalyst", "spent_time_crystal_catalyst",
            "singularity_core", "spent_singularity_core"
    );

    /** 復元品 → 使い切ったときに戻る劣化品。 */
    public static final Map<String, String> WORN_FORM = Map.of(
            "control_unit", "degraded_control_unit",
            "cold_atom_trap", "degraded_cold_atom_trap",
            "time_crystal_seed", "degraded_time_crystal_seed",
            "anomaly_sample", "degraded_anomaly_sample"
    );

    /** 実装済み装置ブロックの段階（ツールチップ用）。 */
    public static final Map<String, Integer> BLOCK_STAGES = Map.ofEntries(
            Map.entry("creative_energy_source", 1),
            Map.entry("copper_wire", 1),
            Map.entry("thermoelectric_generator", 1),
            Map.entry("kiln", 1),
            Map.entry("compressor", 1),
            Map.entry("electrolyzer", 1),
            Map.entry("archive_terminal", 1),
            Map.entry("cooling_tower_casing", 2),
            Map.entry("cooling_tower_glass", 2),
            Map.entry("heat_exchange_core", 2),
            Map.entry("cooling_tower_port", 2),
            Map.entry("cooling_tower_controller", 2),
            Map.entry("superconducting_cable", 2),
            Map.entry("accelerator_tube", 2),
            Map.entry("focusing_magnet", 2),
            Map.entry("accelerator_controller", 2),
            Map.entry("catalytic_reactor", 2),
            Map.entry("cryogenic_turbine", 2),
            Map.entry("cosmic_muon_collector", 2),
            Map.entry("smes_cell", 2),
            Map.entry("precision_assembler", 2),
            Map.entry("worldline_anchor_small", 2),
            Map.entry("entanglement_synthesizer", 3),
            Map.entry("laser_cooler", 3),
            Map.entry("quantum_heat_engine", 3),
            Map.entry("gravitational_wave_detector", 3),
            Map.entry("inertial_stabilizer", 3),
            Map.entry("echo_resonator", 3),
            Map.entry("degenerate_casing", 4),
            Map.entry("degenerate_compactor_controller", 4),
            Map.entry("mirror_plate", 4),
            Map.entry("casimir_cavity_controller", 4),
            Map.entry("time_crystal_incubator", 4),
            Map.entry("degenerate_furnace_piston", 4),
            Map.entry("degenerate_furnace_controller", 4),
            Map.entry("topological_wire", 4),
            Map.entry("smes_module", 4),
            Map.entry("probe_station", 4),
            Map.entry("strangelet", 1),
            Map.entry("horizon_bus", 5),
            Map.entry("reactor_shell", 5),
            Map.entry("gyro_drive", 5),
            Map.entry("extraction_port", 5),
            Map.entry("core_controller", 5),
            Map.entry("singularity_encapsulator", 5),
            Map.entry("halo_collector", 5),
            Map.entry("gravitational_containment_tank", 5),
            Map.entry("shield_tower_core", 5),
            Map.entry("worldline_anchor_advanced", 5),
            Map.entry("tipler_core", 5),
            Map.entry("wormhole_generator_core", 5),
            Map.entry("wormhole_mouth", 5),
            Map.entry("wormhole_stabilizer", 5),
            Map.entry("wormhole_port", 5)
    );
}
