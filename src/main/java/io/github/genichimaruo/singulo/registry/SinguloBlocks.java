package io.github.genichimaruo.singulo.registry;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.cable.CableBlock;
import io.github.genichimaruo.singulo.generated.GeneratedContent;
import io.github.genichimaruo.singulo.generated.GeneratedContent.BlockDef;
import io.github.genichimaruo.singulo.machine.AbstractMachineBlock;
import io.github.genichimaruo.singulo.machine.CatalystDeviceBlockEntity;
import io.github.genichimaruo.singulo.machine.CosmicMuonCollectorBlockEntity;
import io.github.genichimaruo.singulo.machine.CryogenicTurbineBlockEntity;
import io.github.genichimaruo.singulo.machine.DegenerateFurnaceBlockEntity;
import io.github.genichimaruo.singulo.machine.InertialStabilizerBlockEntity;
import io.github.genichimaruo.singulo.machine.MachineBlock;
import io.github.genichimaruo.singulo.machine.MachineType;
import io.github.genichimaruo.singulo.machine.ProbeStationBlockEntity;
import io.github.genichimaruo.singulo.machine.QuantumHeatEngineBlockEntity;
import io.github.genichimaruo.singulo.machine.SimpleMachineBlock;
import io.github.genichimaruo.singulo.machine.SmesCellBlockEntity;
import io.github.genichimaruo.singulo.machine.ThermoelectricGeneratorBlock;
import io.github.genichimaruo.singulo.machine.WorldlineAnchorBlockEntity;
import io.github.genichimaruo.singulo.multiblock.GlassPartBlock;
import io.github.genichimaruo.singulo.multiblock.MultiblockControllerBlock;
import io.github.genichimaruo.singulo.multiblock.MultiblockPart;
import io.github.genichimaruo.singulo.multiblock.PartBlock;
import io.github.genichimaruo.singulo.multiblock.PortBlock;
import io.github.genichimaruo.singulo.reactor.PenroseReactorBlockEntity;
import io.github.genichimaruo.singulo.ruin.GravitationalWaveDetectorBlockEntity;
import io.github.genichimaruo.singulo.ruin.GuardDockBlock;
import io.github.genichimaruo.singulo.ruin.RuinCacheBlock;
import io.github.genichimaruo.singulo.ruin.SealConsoleBlock;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SinguloBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Singulo.MODID);

    /** 圧縮ブロックなど、ただ置けるだけのブロック（生成表から）。 */
    public static final Map<String, DeferredBlock<Block>> SIMPLE = new LinkedHashMap<>();

    static {
        for (BlockDef def : GeneratedContent.BLOCKS) {
            SIMPLE.put(def.id(), BLOCKS.registerSimpleBlock(def.id(), BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE).strength(5.0F, 12.0F).requiresCorrectToolForDrops()
                    .sound(SoundType.NETHERITE_BLOCK)));
        }
    }

    /** 汎用加工装置（1マス）。 */
    public static final Map<MachineType, DeferredBlock<MachineBlock>> MACHINES = new EnumMap<>(MachineType.class);
    /** マルチブロックのコントローラ。 */
    public static final Map<MachineType, DeferredBlock<MultiblockControllerBlock>> CONTROLLERS = new EnumMap<>(MachineType.class);

    static {
        for (MachineType type : MachineType.values()) {
            if (!type.isMultiblock()) {
                MACHINES.put(type, BLOCKS.register(type.id(), () -> new MachineBlock(machineProperties(), type)));
            }
        }
        CONTROLLERS.put(MachineType.CRYOGENIC_COOLING_TOWER, BLOCKS.register("cooling_tower_controller",
                () -> new MultiblockControllerBlock(machineProperties(), MachineType.CRYOGENIC_COOLING_TOWER)));
        CONTROLLERS.put(MachineType.PARTICLE_ACCELERATOR, BLOCKS.register("accelerator_controller",
                () -> new MultiblockControllerBlock(machineProperties(), MachineType.PARTICLE_ACCELERATOR)));
        CONTROLLERS.put(MachineType.DEGENERATE_COMPACTOR, BLOCKS.register("degenerate_compactor_controller",
                () -> new MultiblockControllerBlock(machineProperties(), MachineType.DEGENERATE_COMPACTOR)));
        CONTROLLERS.put(MachineType.CASIMIR_CAVITY, BLOCKS.register("casimir_cavity_controller",
                () -> new MultiblockControllerBlock(machineProperties(), MachineType.CASIMIR_CAVITY)));
    }

    // ---- マルチブロックの部品（決まった形のもの。形は multiblock/Shapes）
    public static final DeferredBlock<PartBlock> COOLING_TOWER_BASE = part("cooling_tower_base", MultiblockPart.Role.STRUCTURE);
    public static final DeferredBlock<PartBlock> COOLING_TOWER_CASING = part("cooling_tower_casing", MultiblockPart.Role.STRUCTURE);
    public static final DeferredBlock<GlassPartBlock> COOLING_TOWER_GLASS = glassPart("cooling_tower_glass");
    public static final DeferredBlock<PartBlock> COOLING_TOWER_COOLANT_BAND = part("cooling_tower_coolant_band", MultiblockPart.Role.STRUCTURE);
    public static final DeferredBlock<PartBlock> COOLING_TOWER_RIM = part("cooling_tower_rim", MultiblockPart.Role.STRUCTURE);
    public static final DeferredBlock<GlassPartBlock> COOLING_TOWER_GRATE = glassPart("cooling_tower_grate");
    public static final DeferredBlock<PartBlock> HEAT_EXCHANGE_CORE = part("heat_exchange_core", MultiblockPart.Role.STRUCTURE);
    public static final DeferredBlock<PartBlock> DEGENERATE_COMPACTOR_FRAME = part("degenerate_compactor_frame", MultiblockPart.Role.STRUCTURE);
    public static final DeferredBlock<PartBlock> DEGENERATE_COMPACTOR_PLATE = part("degenerate_compactor_plate", MultiblockPart.Role.STRUCTURE);
    public static final DeferredBlock<PartBlock> DEGENERATE_COMPACTOR_RAM = part("degenerate_compactor_ram", MultiblockPart.Role.STRUCTURE);
    public static final DeferredBlock<PartBlock> DEGENERATE_COMPACTOR_ANVIL = part("degenerate_compactor_anvil", MultiblockPart.Role.STRUCTURE);
    public static final DeferredBlock<PartBlock> DEGENERATE_COMPACTOR_VENT = part("degenerate_compactor_vent", MultiblockPart.Role.STRUCTURE);
    public static final DeferredBlock<GlassPartBlock> DEGENERATE_COMPACTOR_WINDOW = glassPart("degenerate_compactor_window");
    public static final DeferredBlock<PartBlock> CASIMIR_CAVITY_FRAME = part("casimir_cavity_frame", MultiblockPart.Role.STRUCTURE);
    public static final DeferredBlock<PartBlock> CASIMIR_CAVITY_PUMP = part("casimir_cavity_pump", MultiblockPart.Role.STRUCTURE);
    public static final DeferredBlock<PartBlock> MIRROR_PLATE = part("mirror_plate", MultiblockPart.Role.STRUCTURE);
    public static final DeferredBlock<PartBlock> CASIMIR_CAVITY_WALL = part("casimir_cavity_wall", MultiblockPart.Role.STRUCTURE);
    public static final DeferredBlock<PartBlock> CASIMIR_CAVITY_SHIELD = part("casimir_cavity_shield", MultiblockPart.Role.STRUCTURE);
    public static final DeferredBlock<GlassPartBlock> CASIMIR_CAVITY_WINDOW = glassPart("casimir_cavity_window");
    public static final DeferredBlock<PartBlock> DEGENERATE_FURNACE_FRAME = part("degenerate_furnace_frame", MultiblockPart.Role.STRUCTURE);
    public static final DeferredBlock<PartBlock> DEGENERATE_FURNACE_SHELL = part("degenerate_furnace_shell", MultiblockPart.Role.STRUCTURE);
    public static final DeferredBlock<PartBlock> DEGENERATE_FURNACE_PISTON = part("degenerate_furnace_piston", MultiblockPart.Role.STRUCTURE);
    public static final DeferredBlock<PartBlock> DEGENERATE_FURNACE_FIN = part("degenerate_furnace_fin", MultiblockPart.Role.STRUCTURE);
    public static final DeferredBlock<PartBlock> DEGENERATE_FURNACE_TUBE = part("degenerate_furnace_tube", MultiblockPart.Role.STRUCTURE);
    public static final DeferredBlock<GlassPartBlock> DEGENERATE_FURNACE_WINDOW = glassPart("degenerate_furnace_window");
    public static final DeferredBlock<PartBlock> SHIELD_TOWER_PLINTH = part("shield_tower_plinth", MultiblockPart.Role.STRUCTURE);
    public static final DeferredBlock<PartBlock> SHIELD_TOWER_COIL = part("shield_tower_coil", MultiblockPart.Role.STRUCTURE);
    public static final DeferredBlock<PartBlock> SHIELD_TOWER_BODY = part("shield_tower_body", MultiblockPart.Role.STRUCTURE);
    public static final DeferredBlock<GlassPartBlock> SHIELD_TOWER_WAVEGUIDE = glassPart("shield_tower_waveguide");
    public static final DeferredBlock<PartBlock> SHIELD_TOWER_CROWN = part("shield_tower_crown", MultiblockPart.Role.STRUCTURE);
    public static final DeferredBlock<PartBlock> TIPLER_FRAME = part("tipler_frame", MultiblockPart.Role.STRUCTURE);
    public static final DeferredBlock<PartBlock> TIPLER_HOUSING = part("tipler_housing", MultiblockPart.Role.STRUCTURE);
    public static final DeferredBlock<PartBlock> TIPLER_BEARING = part("tipler_bearing", MultiblockPart.Role.STRUCTURE);
    public static final DeferredBlock<GlassPartBlock> TIPLER_WINDOW = glassPart("tipler_window");
    public static final DeferredBlock<PartBlock> TIPLER_HOLDER = part("tipler_holder", MultiblockPart.Role.STRUCTURE);
    public static final DeferredBlock<PartBlock> WORMHOLE_GENERATOR_SHELL = part("wormhole_generator_shell", MultiblockPart.Role.STRUCTURE);
    public static final DeferredBlock<PartBlock> WORMHOLE_GENERATOR_COIL = part("wormhole_generator_coil", MultiblockPart.Role.STRUCTURE);
    public static final DeferredBlock<PartBlock> WORMHOLE_GENERATOR_FOCUSER = part("wormhole_generator_focuser", MultiblockPart.Role.STRUCTURE);
    public static final DeferredBlock<GlassPartBlock> WORMHOLE_GENERATOR_WINDOW = glassPart("wormhole_generator_window");
    // 粒子加速器・ペンローズ・リアクター
    public static final DeferredBlock<PartBlock> ACCELERATOR_TUBE = BLOCKS.register("accelerator_tube", () -> new io.github.genichimaruo.singulo.multiblock.AcceleratorPartBlock(partProperties(), MultiblockPart.Role.ACCELERATOR_TUBE));
    public static final DeferredBlock<PartBlock> FOCUSING_MAGNET = BLOCKS.register("focusing_magnet", () -> new io.github.genichimaruo.singulo.multiblock.AcceleratorPartBlock(partProperties(), MultiblockPart.Role.FOCUSING_MAGNET));
    public static final DeferredBlock<PartBlock> REACTOR_SHELL = part("reactor_shell", MultiblockPart.Role.REACTOR_SHELL);
    public static final DeferredBlock<PartBlock> GYRO_DRIVE = part("gyro_drive", MultiblockPart.Role.GYRO_DRIVE);
    public static final DeferredBlock<PortBlock> EXTRACTION_PORT = BLOCKS.register("extraction_port",
            () -> new PortBlock(partProperties(), MultiblockPart.Role.EXTRACTION_PORT));
    public static final DeferredBlock<PartBlock> REACTOR_STABILIZER = part("reactor_stabilizer", MultiblockPart.Role.REACTOR_STABILIZER);
    /** 炉心質量警報器（炉殻の代わりにどこにでも置ける。質量が上限に達すると赤石信号を出す）。 */
    public static final DeferredBlock<io.github.genichimaruo.singulo.multiblock.SignalPartBlock> REACTOR_MASS_ALARM = BLOCKS.register(
            "reactor_mass_alarm", () -> new io.github.genichimaruo.singulo.multiblock.SignalPartBlock(partProperties(),
                    MultiblockPart.Role.MASS_ALARM));
    /** マルチブロック搬入出ポート（どのマルチブロックでも、外装板の代わりに置ける。加速器はコントローラの左右）。 */
    public static final DeferredBlock<io.github.genichimaruo.singulo.multiblock.MultiblockPortBlock> MULTIBLOCK_PORT =
            BLOCKS.register("multiblock_port", () -> new io.github.genichimaruo.singulo.multiblock.MultiblockPortBlock(partProperties()));
    /** ワームホール生成器ポート（生成器の外殻の代わりに置く受電・搬出口。働きはマルチブロック搬入出ポートと同じ）。 */
    public static final DeferredBlock<io.github.genichimaruo.singulo.multiblock.MultiblockPortBlock> WORMHOLE_GENERATOR_PORT =
            BLOCKS.register("wormhole_generator_port", () -> new io.github.genichimaruo.singulo.multiblock.MultiblockPortBlock(partProperties()));

    /** 野良ブラックホール（リアクターが崩壊したあとに残る。壊せない）。 */
    public static final DeferredBlock<io.github.genichimaruo.singulo.reactor.RogueBlackHoleBlock> ROGUE_BLACK_HOLE = BLOCKS.register(
            "rogue_black_hole", () -> new io.github.genichimaruo.singulo.reactor.RogueBlackHoleBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK).strength(-1.0F, 3_600_000.0F).noLootTable().noCollission().noOcclusion()
                    .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));
    /** 炉心制御装置（ペンローズ・リアクターのコントローラ）。 */
    public static final DeferredBlock<SimpleMachineBlock<PenroseReactorBlockEntity>> CORE_CONTROLLER = BLOCKS.register(
            "core_controller", () -> new SimpleMachineBlock<>(machineProperties(),
                    SinguloBlockEntities.PENROSE_REACTOR, PenroseReactorBlockEntity::new, PenroseReactorBlockEntity::serverTick));

    // ---- 1マスの装置（汎用加工装置以外）
    public static final DeferredBlock<ThermoelectricGeneratorBlock> THERMOELECTRIC_GENERATOR = BLOCKS.register(
            "thermoelectric_generator", () -> new ThermoelectricGeneratorBlock(machineProperties()));
    public static final DeferredBlock<SimpleMachineBlock<CryogenicTurbineBlockEntity>> CRYOGENIC_TURBINE = BLOCKS.register(
            "cryogenic_turbine", () -> new SimpleMachineBlock<>(machineProperties(),
                    SinguloBlockEntities.CRYOGENIC_TURBINE, CryogenicTurbineBlockEntity::new, CryogenicTurbineBlockEntity::serverTick));
    public static final DeferredBlock<SimpleMachineBlock<SmesCellBlockEntity>> SMES_CELL = BLOCKS.register(
            "smes_cell", () -> new SimpleMachineBlock<>(machineProperties(),
                    SinguloBlockEntities.SMES_CELL, SmesCellBlockEntity::new, SmesCellBlockEntity::serverTick));
    public static final DeferredBlock<SimpleMachineBlock<CosmicMuonCollectorBlockEntity>> COSMIC_MUON_COLLECTOR = BLOCKS.register(
            "cosmic_muon_collector", () -> new SimpleMachineBlock<>(machineProperties(),
                    SinguloBlockEntities.COSMIC_MUON_COLLECTOR, CosmicMuonCollectorBlockEntity::new,
                    CosmicMuonCollectorBlockEntity::serverTick));
    public static final DeferredBlock<SimpleMachineBlock<QuantumHeatEngineBlockEntity>> QUANTUM_HEAT_ENGINE = BLOCKS.register(
            "quantum_heat_engine", () -> new SimpleMachineBlock<>(machineProperties(),
                    SinguloBlockEntities.QUANTUM_HEAT_ENGINE, QuantumHeatEngineBlockEntity::new, CatalystDeviceBlockEntity::serverTick));
    public static final DeferredBlock<SimpleMachineBlock<InertialStabilizerBlockEntity>> INERTIAL_STABILIZER = BLOCKS.register(
            "inertial_stabilizer", () -> new SimpleMachineBlock<>(machineProperties(),
                    SinguloBlockEntities.INERTIAL_STABILIZER, InertialStabilizerBlockEntity::new, CatalystDeviceBlockEntity::serverTick));
    public static final DeferredBlock<SimpleMachineBlock<DegenerateFurnaceBlockEntity>> DEGENERATE_FURNACE_CONTROLLER =
            BLOCKS.register("degenerate_furnace_controller", () -> new SimpleMachineBlock<>(machineProperties(),
                    SinguloBlockEntities.DEGENERATE_FURNACE, DegenerateFurnaceBlockEntity::new, CatalystDeviceBlockEntity::serverTick));
    public static final DeferredBlock<SimpleMachineBlock<ProbeStationBlockEntity>> PROBE_STATION = BLOCKS.register(
            "probe_station", () -> new SimpleMachineBlock<>(machineProperties(),
                    SinguloBlockEntities.PROBE_STATION, ProbeStationBlockEntity::new, CatalystDeviceBlockEntity::serverTick));
    public static final DeferredBlock<SimpleMachineBlock<SmesCellBlockEntity.Module>> SMES_MODULE = BLOCKS.register(
            "smes_module", () -> new SimpleMachineBlock<>(machineProperties(),
                    SinguloBlockEntities.SMES_MODULE, SmesCellBlockEntity.Module::new, SmesCellBlockEntity::serverTick));
    public static final DeferredBlock<SimpleMachineBlock<WorldlineAnchorBlockEntity>> WORLDLINE_ANCHOR_SMALL = BLOCKS.register(
            "worldline_anchor_small", () -> new SimpleMachineBlock<>(machineProperties(),
                    SinguloBlockEntities.WORLDLINE_ANCHOR, WorldlineAnchorBlockEntity::new, CatalystDeviceBlockEntity::serverTick));

    // ---- 段階5の特異点技術
    public static final DeferredBlock<SimpleMachineBlock<io.github.genichimaruo.singulo.machine.AdvancedWorldlineAnchorBlockEntity>>
            WORLDLINE_ANCHOR_ADVANCED = BLOCKS.register("worldline_anchor_advanced", () -> new SimpleMachineBlock<>(machineProperties(),
                    SinguloBlockEntities.WORLDLINE_ANCHOR_ADVANCED,
                    io.github.genichimaruo.singulo.machine.AdvancedWorldlineAnchorBlockEntity::new, CatalystDeviceBlockEntity::serverTick));
    /** イベントホライズン・シールド発生塔のコア（塔の底の中央に置く）。 */
    public static final DeferredBlock<SimpleMachineBlock<io.github.genichimaruo.singulo.machine.ShieldTowerBlockEntity>> SHIELD_TOWER_CORE =
            BLOCKS.register("shield_tower_core", () -> new SimpleMachineBlock<>(machineProperties(),
                    SinguloBlockEntities.SHIELD_TOWER, io.github.genichimaruo.singulo.machine.ShieldTowerBlockEntity::new,
                    CatalystDeviceBlockEntity::serverTick));
    public static final DeferredBlock<SimpleMachineBlock<io.github.genichimaruo.singulo.darkmatter.HaloCollectorBlockEntity>> HALO_COLLECTOR =
            BLOCKS.register("halo_collector", () -> new SimpleMachineBlock<>(machineProperties(),
                    SinguloBlockEntities.HALO_COLLECTOR, io.github.genichimaruo.singulo.darkmatter.HaloCollectorBlockEntity::new, io.github.genichimaruo.singulo.darkmatter.HaloCollectorBlockEntity::serverTick));
    public static final DeferredBlock<SimpleMachineBlock<io.github.genichimaruo.singulo.darkmatter.ContainmentTankBlockEntity>> CONTAINMENT_TANK =
            BLOCKS.register("gravitational_containment_tank", () -> new SimpleMachineBlock<>(machineProperties(),
                    SinguloBlockEntities.CONTAINMENT_TANK, io.github.genichimaruo.singulo.darkmatter.ContainmentTankBlockEntity::new,
                    io.github.genichimaruo.singulo.darkmatter.ContainmentTankBlockEntity::serverTick));
    /** ワームホール生成器のコア（3×3×3 の底の中央に置く）。 */
    public static final DeferredBlock<SimpleMachineBlock<io.github.genichimaruo.singulo.wormhole.WormholeGeneratorBlockEntity>> WORMHOLE_GENERATOR_CORE =
            BLOCKS.register("wormhole_generator_core", () -> new SimpleMachineBlock<>(machineProperties(),
                    SinguloBlockEntities.WORMHOLE_GENERATOR, io.github.genichimaruo.singulo.wormhole.WormholeGeneratorBlockEntity::new,
                    io.github.genichimaruo.singulo.wormhole.WormholeGeneratorBlockEntity::serverTick));
    public static final DeferredBlock<SimpleMachineBlock<io.github.genichimaruo.singulo.wormhole.WormholeStabilizerBlockEntity>> WORMHOLE_STABILIZER =
            BLOCKS.register("wormhole_stabilizer", () -> new SimpleMachineBlock<>(machineProperties(),
                    SinguloBlockEntities.WORMHOLE_STABILIZER, io.github.genichimaruo.singulo.wormhole.WormholeStabilizerBlockEntity::new,
                    io.github.genichimaruo.singulo.wormhole.WormholeStabilizerBlockEntity::serverTick));
    /** ワームホールの口（固定化した口を置いたもの）。 */
    public static final DeferredBlock<SimpleMachineBlock<io.github.genichimaruo.singulo.wormhole.WormholeMouthBlockEntity>> WORMHOLE_MOUTH =
            BLOCKS.register("wormhole_mouth", () -> new SimpleMachineBlock<>(machineProperties().noOcclusion(),
                    SinguloBlockEntities.WORMHOLE_MOUTH, io.github.genichimaruo.singulo.wormhole.WormholeMouthBlockEntity::new, io.github.genichimaruo.singulo.wormhole.WormholeMouthBlockEntity::serverTick));
    public static final DeferredBlock<SimpleMachineBlock<io.github.genichimaruo.singulo.wormhole.WormholePortBlockEntity>> WORMHOLE_PORT =
            BLOCKS.register("wormhole_port", () -> new SimpleMachineBlock<>(machineProperties(),
                    SinguloBlockEntities.WORMHOLE_PORT, io.github.genichimaruo.singulo.wormhole.WormholePortBlockEntity::new,
                    io.github.genichimaruo.singulo.wormhole.WormholePortBlockEntity::serverTick));
    /** クリエイティブ電源（クリエイティブ専用）。 */
    public static final DeferredBlock<SimpleMachineBlock<io.github.genichimaruo.singulo.machine.CreativeEnergyBlockEntity>> CREATIVE_ENERGY_SOURCE =
            BLOCKS.register("creative_energy_source", () -> new SimpleMachineBlock<>(machineProperties().strength(-1.0F, 3_600_000.0F),
                    SinguloBlockEntities.CREATIVE_ENERGY, io.github.genichimaruo.singulo.machine.CreativeEnergyBlockEntity::new,
                    io.github.genichimaruo.singulo.machine.CreativeEnergyBlockEntity::serverTick));
    /** ストレンジレット（危険。壊せず、磁気瓶で取り除く）。 */
    public static final DeferredBlock<io.github.genichimaruo.singulo.hazard.StrangeletBlock> STRANGELET = BLOCKS.register("strangelet",
            () -> new io.github.genichimaruo.singulo.hazard.StrangeletBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE)
                    .strength(-1.0F, 3_600_000.0F).noLootTable().lightLevel(s -> 10).sound(SoundType.SCULK)));
    /** ティプラー・シリンダーのコア（底の中央に置く）。 */
    public static final DeferredBlock<SimpleMachineBlock<io.github.genichimaruo.singulo.machine.TiplerCylinderBlockEntity>> TIPLER_CORE =
            BLOCKS.register("tipler_core", () -> new SimpleMachineBlock<>(machineProperties(),
                    SinguloBlockEntities.TIPLER_CYLINDER, io.github.genichimaruo.singulo.machine.TiplerCylinderBlockEntity::new,
                    CatalystDeviceBlockEntity::serverTick, io.github.genichimaruo.singulo.machine.TiplerCylinderBlockEntity::clientTick));

    // ---- 遺構（作れない。遺構の建材と、壊せない保管庫・警備機ドック）
    public static final DeferredBlock<Block> RUIN_PANEL = ruinPanel("ruin_panel");
    public static final DeferredBlock<Block> CRACKED_RUIN_PANEL = ruinPanel("cracked_ruin_panel");
    public static final DeferredBlock<Block> MOSSY_RUIN_PANEL = ruinPanel("mossy_ruin_panel");
    /** 割れた遺構ガラスと遺構ガラス（透けて見える。硬さ・爆発への強さ・音は遺構パネルと同じ）。 */
    public static final DeferredBlock<TransparentBlock> RUIN_GLASS = glass("ruin_glass", MapColor.NONE, 4.0F, 9.0F, SoundType.STONE);
    public static final DeferredBlock<TransparentBlock> INTACT_RUIN_GLASS = glass("intact_ruin_glass", MapColor.NONE, 4.0F, 9.0F,
            SoundType.STONE);
    public static final DeferredBlock<Block> RUIN_LAMP = BLOCKS.registerSimpleBlock("ruin_lamp",
            BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(2.0F).sound(SoundType.GLASS).lightLevel(s -> 12));
    public static final DeferredBlock<Block> RUIN_LAMP_AMBER = ruinLamp("ruin_lamp_amber");
    public static final DeferredBlock<Block> RUIN_LAMP_VERDANT = ruinLamp("ruin_lamp_verdant");
    public static final DeferredBlock<Block> RUIN_LAMP_VIOLET = ruinLamp("ruin_lamp_violet");
    public static final DeferredBlock<Block> RUIN_LAMP_CRIMSON = ruinLamp("ruin_lamp_crimson");
    // 遺構パネルの変わり種（朽ちたもの）
    public static final DeferredBlock<Block> TILED_RUIN_PANEL = ruinPanel("tiled_ruin_panel");
    public static final DeferredBlock<Block> VENTED_RUIN_PANEL = ruinPanel("vented_ruin_panel");
    public static final DeferredBlock<Block> STRIPED_RUIN_PANEL = ruinPanel("striped_ruin_panel");
    public static final DeferredBlock<Block> SCORCHED_RUIN_PANEL = ruinPanel("scorched_ruin_panel");
    // 深い遺構に残る、朽ちていない白い建材（硬く、鉄以上のツルハシで掘る）
    public static final DeferredBlock<Block> PRISTINE_RUIN_PANEL = pristine("pristine_ruin_panel", 0);
    public static final DeferredBlock<Block> PRISTINE_RUIN_TILES = pristine("pristine_ruin_tiles", 0);
    /** 柱は原木のように、置く向きで縦・横に倒れる。 */
    public static final DeferredBlock<net.minecraft.world.level.block.RotatedPillarBlock> PRISTINE_RUIN_PILLAR = BLOCKS.register(
            "pristine_ruin_pillar", () -> new net.minecraft.world.level.block.RotatedPillarBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.SNOW).strength(8.0F, 30.0F).requiresCorrectToolForDrops().sound(SoundType.METAL)));
    public static final DeferredBlock<Block> PRISTINE_RUIN_LIGHT = pristine("pristine_ruin_light", 11);

    // ---- 自分で作れる建材（遺構の建材とは別の、新しい白いパネル）
    public static final DeferredBlock<Block> WHITE_PANEL = BLOCKS.registerSimpleBlock("white_panel",
            BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(3.0F, 9.0F).requiresCorrectToolForDrops()
                    .sound(SoundType.METAL));
    public static final DeferredBlock<Block> WHITE_LIGHT_PANEL = BLOCKS.registerSimpleBlock("white_light_panel",
            BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(3.0F, 9.0F).requiresCorrectToolForDrops()
                    .sound(SoundType.GLASS).lightLevel(s -> 15));
    /** 黒色強化パネル（黒曜石なみに爆発に強い）。 */
    public static final DeferredBlock<Block> BLACK_REINFORCED_PANEL = BLOCKS.registerSimpleBlock("black_reinforced_panel",
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(20.0F, 1200.0F).requiresCorrectToolForDrops()
                    .sound(SoundType.NETHERITE_BLOCK));

    /** 透けるパネル（ホワイトパネルと黒色強化パネルの系統と、四芒星の模様入り）。音はそれぞれのパネルと同じ。 */
    public static final DeferredBlock<TransparentBlock> WHITE_GLASS_PANEL = glass("white_glass_panel", MapColor.SNOW, 3.0F, 9.0F,
            SoundType.METAL);
    public static final DeferredBlock<TransparentBlock> WHITE_STAR_GLASS_PANEL = glass("white_star_glass_panel", MapColor.SNOW, 3.0F, 9.0F,
            SoundType.METAL);
    public static final DeferredBlock<TransparentBlock> BLACK_REINFORCED_GLASS = glass("black_reinforced_glass", MapColor.COLOR_BLACK,
            20.0F, 1200.0F, SoundType.NETHERITE_BLOCK);
    public static final DeferredBlock<TransparentBlock> BLACK_STAR_GLASS = glass("black_star_glass", MapColor.COLOR_BLACK,
            20.0F, 1200.0F, SoundType.NETHERITE_BLOCK);

    // ---- 隕石クレーター（地表の小さな構造物）と、雷ガラス
    /** 隕石の溶融殻（クレーターに最初からあるものだけ、星屑の結晶を育てる）。 */
    public static final DeferredBlock<io.github.genichimaruo.singulo.nature.MeteoriteCrustBlock> METEORITE_CRUST = BLOCKS.register(
            "meteorite_crust", () -> new io.github.genichimaruo.singulo.nature.MeteoriteCrustBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK).strength(5.0F, 9.0F).requiresCorrectToolForDrops().sound(SoundType.BASALT)
                    .randomTicks()));
    /** 隕鉄塊（掘ると隕鉄の原石が出る。鉄以上のツルハシ）。 */
    public static final DeferredBlock<Block> METEORIC_IRON_CHUNK = BLOCKS.registerSimpleBlock("meteoric_iron_chunk",
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(6.0F, 9.0F).requiresCorrectToolForDrops()
                    .sound(SoundType.ANCIENT_DEBRIS));
    /** 星屑の結晶（隕石の溶融殻の表面に育つ。壊すと星屑）。 */
    public static final DeferredBlock<net.minecraft.world.level.block.AmethystClusterBlock> STARDUST_CLUSTER = BLOCKS.register(
            "stardust_cluster", () -> new net.minecraft.world.level.block.AmethystClusterBlock(7.0F, 3.0F, BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE).strength(1.5F).sound(SoundType.AMETHYST_CLUSTER).noOcclusion()
                    .lightLevel(s -> 6).forceSolidOn().pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)));
    /** 雷ガラス塊（雷が落ちた砂が溶けて固まったもの。壊すと雷ガラス）。 */
    public static final DeferredBlock<Block> FULGURITE_BLOCK = BLOCKS.registerSimpleBlock("fulgurite_block",
            BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(0.8F).sound(SoundType.GLASS));
    /** 深海圧力収集器と虚空捕集器（その場所に置かないと動かない）。 */
    public static final DeferredBlock<SimpleMachineBlock<io.github.genichimaruo.singulo.nature.DeepSeaCollectorBlockEntity>> DEEP_SEA_COLLECTOR =
            BLOCKS.register("deep_sea_collector", () -> new SimpleMachineBlock<>(machineProperties(),
                    SinguloBlockEntities.DEEP_SEA_COLLECTOR, io.github.genichimaruo.singulo.nature.DeepSeaCollectorBlockEntity::new,
                    io.github.genichimaruo.singulo.nature.EnvironmentCollectorBlockEntity::serverTick));
    public static final DeferredBlock<SimpleMachineBlock<io.github.genichimaruo.singulo.nature.VoidCollectorBlockEntity>> VOID_COLLECTOR =
            BLOCKS.register("void_collector", () -> new SimpleMachineBlock<>(machineProperties(),
                    SinguloBlockEntities.VOID_COLLECTOR, io.github.genichimaruo.singulo.nature.VoidCollectorBlockEntity::new,
                    io.github.genichimaruo.singulo.nature.EnvironmentCollectorBlockEntity::serverTick));
    /** ケラウノス放電塔（人工の雷を避雷針へ撃つ）。 */
    public static final DeferredBlock<SimpleMachineBlock<io.github.genichimaruo.singulo.nature.KeraunosTowerBlockEntity>> KERAUNOS_TOWER =
            BLOCKS.register("keraunos_tower", () -> new SimpleMachineBlock<>(machineProperties().noOcclusion(),
                    SinguloBlockEntities.KERAUNOS_TOWER, io.github.genichimaruo.singulo.nature.KeraunosTowerBlockEntity::new,
                    io.github.genichimaruo.singulo.nature.KeraunosTowerBlockEntity::serverTick));

    // ---- 重力パネル（電力の届いたパネルに乗ると、次の着地まで重力が変わる）
    public static final DeferredBlock<io.github.genichimaruo.singulo.gravity.GravityPanelBlock> LOW_GRAVITY_PANEL = BLOCKS.register(
            "low_gravity_panel", () -> new io.github.genichimaruo.singulo.gravity.GravityPanelBlock(gravityProperties(MapColor.SNOW),
                    io.github.genichimaruo.singulo.gravity.GravityPanelBlock.Kind.LOW));
    public static final DeferredBlock<io.github.genichimaruo.singulo.gravity.GravityPanelBlock> HIGH_GRAVITY_PANEL = BLOCKS.register(
            "high_gravity_panel", () -> new io.github.genichimaruo.singulo.gravity.GravityPanelBlock(gravityProperties(MapColor.COLOR_BLACK),
                    io.github.genichimaruo.singulo.gravity.GravityPanelBlock.Kind.HIGH));
    public static final DeferredBlock<io.github.genichimaruo.singulo.gravity.GravityReceiverBlock> GRAVITY_PANEL_RECEIVER = BLOCKS.register(
            "gravity_panel_receiver", () -> new io.github.genichimaruo.singulo.gravity.GravityReceiverBlock(machineProperties()));
    /** 衝撃発電パネル（上に落ちても落下ダメージを受けず、落ちた高さに応じてわずかに発電し、重力パネルへ配る）。 */
    public static final DeferredBlock<io.github.genichimaruo.singulo.gravity.ImpactGeneratorBlock> IMPACT_GENERATOR = BLOCKS.register(
            "impact_generator_panel", () -> new io.github.genichimaruo.singulo.gravity.ImpactGeneratorBlock(gravityProperties(MapColor.SNOW)));
    /** 封印コンテナ（段階1〜4の鍵で開く）。開いていれば普通に壊せ、封印中はとても硬い（SealedContainerBlock）。 */
    public static final java.util.List<DeferredBlock<io.github.genichimaruo.singulo.ruin.SealedContainerBlock>> SEALED_CONTAINERS =
            java.util.stream.IntStream.rangeClosed(1, 4).mapToObj(tier -> BLOCKS.register("sealed_container_" + tier,
                    () -> new io.github.genichimaruo.singulo.ruin.SealedContainerBlock(BlockBehaviour.Properties.of()
                            .mapColor(MapColor.SNOW).strength(5.0F, 1200.0F).requiresCorrectToolForDrops()
                            .sound(SoundType.NETHERITE_BLOCK).noOcclusion(), tier))).toList();
    public static final DeferredBlock<RuinCacheBlock> RUIN_CACHE = BLOCKS.register("ruin_cache",
            () -> new RuinCacheBlock(unbreakable().noOcclusion().lightLevel(s -> 6)));
    public static final DeferredBlock<GuardDockBlock> RUIN_GUARD_DOCK = BLOCKS.register("ruin_guard_dock",
            () -> new GuardDockBlock(unbreakable()));
    public static final DeferredBlock<SealConsoleBlock> SEAL_CONSOLE = BLOCKS.register("seal_console",
            () -> new SealConsoleBlock(unbreakable().lightLevel(s -> 10)));
    /** 番人の封印核（研究棟・封鎖培養施設のボス部屋の床。壊せない）。 */
    public static final DeferredBlock<io.github.genichimaruo.singulo.ruin.GuardianCoreBlock> GUARDIAN_CORE = BLOCKS.register("guardian_core",
            () -> new io.github.genichimaruo.singulo.ruin.GuardianCoreBlock(unbreakable().lightLevel(s -> 9)));
    /** 残響投影器（素手でも壊せ、何も落とさない）。 */
    public static final DeferredBlock<io.github.genichimaruo.singulo.ruin.EchoProjectorBlock> ECHO_PROJECTOR = BLOCKS.register("echo_projector",
            () -> new io.github.genichimaruo.singulo.ruin.EchoProjectorBlock(BlockBehaviour.Properties.of().mapColor(MapColor.DIAMOND)
                    .strength(1.5F, 6.0F).sound(SoundType.AMETHYST).lightLevel(s -> 8).noOcclusion()));
    /** ニュートリノ観測所（設置型のニュートリノ・スキャナー）。 */
    public static final DeferredBlock<SimpleMachineBlock<io.github.genichimaruo.singulo.machine.NeutrinoObservatoryBlockEntity>> NEUTRINO_OBSERVATORY =
            BLOCKS.register("neutrino_observatory", () -> new SimpleMachineBlock<>(machineProperties(),
                    SinguloBlockEntities.NEUTRINO_OBSERVATORY, io.github.genichimaruo.singulo.machine.NeutrinoObservatoryBlockEntity::new,
                    io.github.genichimaruo.singulo.machine.NeutrinoObservatoryBlockEntity::serverTick));
    public static final DeferredBlock<SimpleMachineBlock<GravitationalWaveDetectorBlockEntity>> GRAVITATIONAL_WAVE_DETECTOR =
            BLOCKS.register("gravitational_wave_detector", () -> new SimpleMachineBlock<>(machineProperties(),
                    SinguloBlockEntities.GRAVITATIONAL_WAVE_DETECTOR, GravitationalWaveDetectorBlockEntity::new, (l, p, s, be) -> {}));

    // ---- ケーブル
    public static final DeferredBlock<CableBlock> COPPER_WIRE = BLOCKS.register("copper_wire",
            () -> new CableBlock(cableProperties(SoundType.COPPER), 2_000, 0.001, io.github.genichimaruo.singulo.cable.CableProfile.COPPER));
    public static final DeferredBlock<CableBlock> SUPERCONDUCTING_CABLE = BLOCKS.register("superconducting_cable",
            () -> new CableBlock(cableProperties(SoundType.METAL), 1_000_000, 0, io.github.genichimaruo.singulo.cable.CableProfile.SUPERCONDUCTING));
    public static final DeferredBlock<CableBlock> TOPOLOGICAL_WIRE = BLOCKS.register("topological_wire",
            () -> new CableBlock(cableProperties(SoundType.METAL), 100_000_000, 0, io.github.genichimaruo.singulo.cable.CableProfile.TOPOLOGICAL));
    /** 上限なし。mod の装置どうしは int（約 2.1 GFE/t）を超えて受け渡す（ほかの mod の装置へは1台あたり int まで）。 */
    public static final DeferredBlock<CableBlock> HORIZON_BUS = BLOCKS.register("horizon_bus",
            () -> new CableBlock(cableProperties(SoundType.NETHERITE_BLOCK), Long.MAX_VALUE, 0, io.github.genichimaruo.singulo.cable.CableProfile.HORIZON));

    private static DeferredBlock<Block> ruinLamp(String id) {
        return BLOCKS.registerSimpleBlock(id, BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(2.0F)
                .sound(SoundType.GLASS).lightLevel(s -> 12));
    }

    private static DeferredBlock<Block> ruinPanel(String id) {
        return BLOCKS.registerSimpleBlock(id, BlockBehaviour.Properties.of().mapColor(MapColor.SAND)
                .strength(4.0F, 9.0F).requiresCorrectToolForDrops().sound(SoundType.STONE));
    }

    /** 朽ちていない遺構の建材: 硬く（鉄以上のツルハシ）、爆発にも強い。 */
    private static DeferredBlock<Block> pristine(String id, int light) {
        return BLOCKS.registerSimpleBlock(id, BlockBehaviour.Properties.of().mapColor(MapColor.SNOW)
                .strength(8.0F, 30.0F).requiresCorrectToolForDrops().sound(SoundType.METAL).lightLevel(s -> light));
    }

    /** 透けるブロック（ガラスのように、同じブロックどうしの間の面は描かない）。ツルハシで掘る。 */
    private static DeferredBlock<TransparentBlock> glass(String id, MapColor color, float hardness, float resistance, SoundType sound) {
        return BLOCKS.register(id, () -> new TransparentBlock(BlockBehaviour.Properties.of().mapColor(color)
                .strength(hardness, resistance).requiresCorrectToolForDrops().sound(sound).noOcclusion()
                .isViewBlocking((s, l, p) -> false).isSuffocating((s, l, p) -> false).isRedstoneConductor((s, l, p) -> false)
                .isValidSpawn((s, l, p, e) -> false)));
    }

    private static BlockBehaviour.Properties gravityProperties(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(4.0F, 12.0F).requiresCorrectToolForDrops()
                .sound(SoundType.METAL);
    }

    /** 壊せない（保管庫やドックを持ち帰って自動化されないように）。 */
    static BlockBehaviour.Properties unbreakable() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(-1.0F, 3_600_000.0F).noLootTable()
                .sound(SoundType.STONE);
    }

    private static DeferredBlock<PartBlock> part(String id, MultiblockPart.Role role) {
        return BLOCKS.register(id, () -> new PartBlock(partProperties(), role));
    }

    /** 部品の窓（色つきの半透明ガラス。中が見える）。 */
    private static DeferredBlock<GlassPartBlock> glassPart(String id) {
        return BLOCKS.register(id, () -> new GlassPartBlock(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(3.0F, 6.0F)
                .sound(SoundType.GLASS).noOcclusion().isViewBlocking((s, l, p) -> false)
                .isSuffocating((s, l, p) -> false).isRedstoneConductor((s, l, p) -> false)));
    }

    static BlockBehaviour.Properties partProperties() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(3.0F, 6.0F)
                .requiresCorrectToolForDrops().sound(SoundType.METAL);
    }

    static BlockBehaviour.Properties cableProperties(SoundType sound) {
        return BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(0.5F).sound(sound).noOcclusion();
    }

    static BlockBehaviour.Properties machineProperties() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(3.5F, 6.0F)
                .requiresCorrectToolForDrops().sound(SoundType.METAL)
                .lightLevel(state -> state.hasProperty(AbstractMachineBlock.LIT) && state.getValue(AbstractMachineBlock.LIT) ? 7 : 0);
    }

    private SinguloBlocks() {}
}
