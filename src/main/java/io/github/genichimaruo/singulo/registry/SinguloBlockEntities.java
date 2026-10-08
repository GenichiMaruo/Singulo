package io.github.genichimaruo.singulo.registry;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.cable.CableBlockEntity;
import io.github.genichimaruo.singulo.machine.CosmicMuonCollectorBlockEntity;
import io.github.genichimaruo.singulo.machine.CryogenicTurbineBlockEntity;
import io.github.genichimaruo.singulo.machine.DegenerateFurnaceBlockEntity;
import io.github.genichimaruo.singulo.machine.InertialStabilizerBlockEntity;
import io.github.genichimaruo.singulo.machine.MachineBlockEntity;
import io.github.genichimaruo.singulo.machine.MachineType;
import io.github.genichimaruo.singulo.machine.ProbeStationBlockEntity;
import io.github.genichimaruo.singulo.machine.QuantumHeatEngineBlockEntity;
import io.github.genichimaruo.singulo.machine.SmesCellBlockEntity;
import io.github.genichimaruo.singulo.machine.ThermoelectricGeneratorBlockEntity;
import io.github.genichimaruo.singulo.machine.TimeCrystalIncubatorBlockEntity;
import io.github.genichimaruo.singulo.machine.WorldlineAnchorBlockEntity;
import io.github.genichimaruo.singulo.multiblock.AcceleratorControllerBlockEntity;
import io.github.genichimaruo.singulo.multiblock.CoolingTowerControllerBlockEntity;
import io.github.genichimaruo.singulo.multiblock.FixedShapeControllerBlockEntity;
import io.github.genichimaruo.singulo.multiblock.PortBlockEntity;
import io.github.genichimaruo.singulo.reactor.PenroseReactorBlockEntity;
import io.github.genichimaruo.singulo.ruin.GravitationalWaveDetectorBlockEntity;
import io.github.genichimaruo.singulo.ruin.GuardDockBlockEntity;
import io.github.genichimaruo.singulo.ruin.RuinCacheBlockEntity;
import io.github.genichimaruo.singulo.ruin.SealConsoleBlockEntity;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SinguloBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> REGISTER =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Singulo.MODID);

    /** 汎用加工装置すべてで共有する。種類はブロック側の MachineType で決まる。 */
    public static final Supplier<BlockEntityType<MachineBlockEntity>> MACHINE = REGISTER.register("machine",
            () -> build(MachineBlockEntity::new,
                    SinguloBlocks.MACHINES.values().stream().map(b -> (Block) b.get()).toArray(Block[]::new)));

    public static final Supplier<BlockEntityType<CoolingTowerControllerBlockEntity>> COOLING_TOWER_CONTROLLER =
            REGISTER.register("cooling_tower_controller", () -> build(CoolingTowerControllerBlockEntity::new,
                    SinguloBlocks.CONTROLLERS.get(MachineType.CRYOGENIC_COOLING_TOWER).get()));

    public static final Supplier<BlockEntityType<AcceleratorControllerBlockEntity>> ACCELERATOR_CONTROLLER =
            REGISTER.register("accelerator_controller", () -> build(AcceleratorControllerBlockEntity::new,
                    SinguloBlocks.CONTROLLERS.get(MachineType.PARTICLE_ACCELERATOR).get()));

    public static final Supplier<BlockEntityType<FixedShapeControllerBlockEntity.Compactor>> DEGENERATE_COMPACTOR_CONTROLLER =
            REGISTER.register("degenerate_compactor_controller", () -> build(FixedShapeControllerBlockEntity.Compactor::new,
                    SinguloBlocks.CONTROLLERS.get(MachineType.DEGENERATE_COMPACTOR).get()));

    public static final Supplier<BlockEntityType<FixedShapeControllerBlockEntity.Cavity>> CASIMIR_CAVITY_CONTROLLER =
            REGISTER.register("casimir_cavity_controller", () -> build(FixedShapeControllerBlockEntity.Cavity::new,
                    SinguloBlocks.CONTROLLERS.get(MachineType.CASIMIR_CAVITY).get()));

    public static final Supplier<BlockEntityType<TimeCrystalIncubatorBlockEntity>> TIME_CRYSTAL_INCUBATOR =
            REGISTER.register("time_crystal_incubator", () -> build(TimeCrystalIncubatorBlockEntity::new,
                    SinguloBlocks.MACHINES.get(MachineType.TIME_CRYSTAL_INCUBATOR).get()));

    public static final Supplier<BlockEntityType<DegenerateFurnaceBlockEntity>> DEGENERATE_FURNACE =
            REGISTER.register("degenerate_furnace", () -> build(DegenerateFurnaceBlockEntity::new,
                    SinguloBlocks.DEGENERATE_FURNACE_CONTROLLER.get()));

    public static final Supplier<BlockEntityType<ProbeStationBlockEntity>> PROBE_STATION =
            REGISTER.register("probe_station", () -> build(ProbeStationBlockEntity::new, SinguloBlocks.PROBE_STATION.get()));

    public static final Supplier<BlockEntityType<SmesCellBlockEntity.Module>> SMES_MODULE =
            REGISTER.register("smes_module", () -> build(SmesCellBlockEntity.Module::new, SinguloBlocks.SMES_MODULE.get()));

    public static final Supplier<BlockEntityType<PortBlockEntity>> PORT = REGISTER.register("port",
            () -> build(PortBlockEntity::new, SinguloBlocks.COOLING_TOWER_PORT.get(), SinguloBlocks.EXTRACTION_PORT.get(),
                    SinguloBlocks.WORMHOLE_GENERATOR_IO.get()));

    public static final Supplier<BlockEntityType<PenroseReactorBlockEntity>> PENROSE_REACTOR = REGISTER.register(
            "penrose_reactor", () -> build(PenroseReactorBlockEntity::new, SinguloBlocks.CORE_CONTROLLER.get()));

    public static final Supplier<BlockEntityType<ThermoelectricGeneratorBlockEntity>> THERMOELECTRIC_GENERATOR =
            REGISTER.register("thermoelectric_generator", () -> build(ThermoelectricGeneratorBlockEntity::new,
                    SinguloBlocks.THERMOELECTRIC_GENERATOR.get()));

    public static final Supplier<BlockEntityType<CryogenicTurbineBlockEntity>> CRYOGENIC_TURBINE =
            REGISTER.register("cryogenic_turbine", () -> build(CryogenicTurbineBlockEntity::new,
                    SinguloBlocks.CRYOGENIC_TURBINE.get()));

    public static final Supplier<BlockEntityType<SmesCellBlockEntity>> SMES_CELL =
            REGISTER.register("smes_cell", () -> build(SmesCellBlockEntity::new, SinguloBlocks.SMES_CELL.get()));

    public static final Supplier<BlockEntityType<CosmicMuonCollectorBlockEntity>> COSMIC_MUON_COLLECTOR =
            REGISTER.register("cosmic_muon_collector", () -> build(CosmicMuonCollectorBlockEntity::new,
                    SinguloBlocks.COSMIC_MUON_COLLECTOR.get()));

    public static final Supplier<BlockEntityType<WorldlineAnchorBlockEntity>> WORLDLINE_ANCHOR =
            REGISTER.register("worldline_anchor", () -> build(WorldlineAnchorBlockEntity::new,
                    SinguloBlocks.WORLDLINE_ANCHOR_SMALL.get()));

    public static final Supplier<BlockEntityType<io.github.genichimaruo.singulo.machine.AdvancedWorldlineAnchorBlockEntity>>
            WORLDLINE_ANCHOR_ADVANCED = REGISTER.register("worldline_anchor_advanced", () -> build(
                    io.github.genichimaruo.singulo.machine.AdvancedWorldlineAnchorBlockEntity::new, SinguloBlocks.WORLDLINE_ANCHOR_ADVANCED.get()));

    public static final Supplier<BlockEntityType<io.github.genichimaruo.singulo.machine.ShieldTowerBlockEntity>> SHIELD_TOWER =
            REGISTER.register("shield_tower", () -> build(io.github.genichimaruo.singulo.machine.ShieldTowerBlockEntity::new,
                    SinguloBlocks.SHIELD_TOWER_CORE.get()));

    public static final Supplier<BlockEntityType<io.github.genichimaruo.singulo.machine.TiplerCylinderBlockEntity>> TIPLER_CYLINDER =
            REGISTER.register("tipler_cylinder", () -> build(io.github.genichimaruo.singulo.machine.TiplerCylinderBlockEntity::new,
                    SinguloBlocks.TIPLER_CORE.get()));

    public static final Supplier<BlockEntityType<io.github.genichimaruo.singulo.darkmatter.HaloCollectorBlockEntity>> HALO_COLLECTOR =
            REGISTER.register("halo_collector", () -> build(io.github.genichimaruo.singulo.darkmatter.HaloCollectorBlockEntity::new, SinguloBlocks.HALO_COLLECTOR.get()));
    public static final Supplier<BlockEntityType<io.github.genichimaruo.singulo.darkmatter.ContainmentTankBlockEntity>> CONTAINMENT_TANK =
            REGISTER.register("containment_tank", () -> build(io.github.genichimaruo.singulo.darkmatter.ContainmentTankBlockEntity::new,
                    SinguloBlocks.CONTAINMENT_TANK.get()));
    public static final Supplier<BlockEntityType<io.github.genichimaruo.singulo.wormhole.WormholeGeneratorBlockEntity>> WORMHOLE_GENERATOR =
            REGISTER.register("wormhole_generator", () -> build(io.github.genichimaruo.singulo.wormhole.WormholeGeneratorBlockEntity::new,
                    SinguloBlocks.WORMHOLE_GENERATOR_CORE.get()));
    public static final Supplier<BlockEntityType<io.github.genichimaruo.singulo.wormhole.WormholeStabilizerBlockEntity>> WORMHOLE_STABILIZER =
            REGISTER.register("wormhole_stabilizer", () -> build(io.github.genichimaruo.singulo.wormhole.WormholeStabilizerBlockEntity::new,
                    SinguloBlocks.WORMHOLE_STABILIZER.get()));
    public static final Supplier<BlockEntityType<io.github.genichimaruo.singulo.wormhole.WormholeMouthBlockEntity>> WORMHOLE_MOUTH =
            REGISTER.register("wormhole_mouth", () -> build(io.github.genichimaruo.singulo.wormhole.WormholeMouthBlockEntity::new, SinguloBlocks.WORMHOLE_MOUTH.get()));
    public static final Supplier<BlockEntityType<io.github.genichimaruo.singulo.wormhole.WormholePortBlockEntity>> WORMHOLE_PORT =
            REGISTER.register("wormhole_port", () -> build(io.github.genichimaruo.singulo.wormhole.WormholePortBlockEntity::new, SinguloBlocks.WORMHOLE_PORT.get()));

    public static final Supplier<BlockEntityType<io.github.genichimaruo.singulo.hazard.StrangeletBlockEntity>> STRANGELET =
            REGISTER.register("strangelet", () -> build(io.github.genichimaruo.singulo.hazard.StrangeletBlockEntity::new,
                    SinguloBlocks.STRANGELET.get()));

    public static final Supplier<BlockEntityType<io.github.genichimaruo.singulo.machine.CreativeEnergyBlockEntity>> CREATIVE_ENERGY =
            REGISTER.register("creative_energy", () -> build(io.github.genichimaruo.singulo.machine.CreativeEnergyBlockEntity::new,
                    SinguloBlocks.CREATIVE_ENERGY_SOURCE.get()));

    public static final Supplier<BlockEntityType<QuantumHeatEngineBlockEntity>> QUANTUM_HEAT_ENGINE =
            REGISTER.register("quantum_heat_engine", () -> build(QuantumHeatEngineBlockEntity::new,
                    SinguloBlocks.QUANTUM_HEAT_ENGINE.get()));

    public static final Supplier<BlockEntityType<InertialStabilizerBlockEntity>> INERTIAL_STABILIZER =
            REGISTER.register("inertial_stabilizer", () -> build(InertialStabilizerBlockEntity::new,
                    SinguloBlocks.INERTIAL_STABILIZER.get()));

    public static final Supplier<BlockEntityType<RuinCacheBlockEntity>> RUIN_CACHE = REGISTER.register("ruin_cache",
            () -> build(RuinCacheBlockEntity::new, SinguloBlocks.RUIN_CACHE.get()));

    public static final Supplier<BlockEntityType<GuardDockBlockEntity>> GUARD_DOCK = REGISTER.register("guard_dock",
            () -> build(GuardDockBlockEntity::new, SinguloBlocks.RUIN_GUARD_DOCK.get()));

    public static final Supplier<BlockEntityType<SealConsoleBlockEntity>> SEAL_CONSOLE = REGISTER.register("seal_console",
            () -> build(SealConsoleBlockEntity::new, SinguloBlocks.SEAL_CONSOLE.get()));

    public static final Supplier<BlockEntityType<GravitationalWaveDetectorBlockEntity>> GRAVITATIONAL_WAVE_DETECTOR =
            REGISTER.register("gravitational_wave_detector", () -> build(GravitationalWaveDetectorBlockEntity::new,
                    SinguloBlocks.GRAVITATIONAL_WAVE_DETECTOR.get()));

    public static final Supplier<BlockEntityType<CableBlockEntity>> CABLE = REGISTER.register("cable",
            () -> build(CableBlockEntity::new, SinguloBlocks.COPPER_WIRE.get(), SinguloBlocks.SUPERCONDUCTING_CABLE.get(),
                    SinguloBlocks.TOPOLOGICAL_WIRE.get(), SinguloBlocks.HORIZON_BUS.get()));

    @SuppressWarnings("DataFlowIssue")
    private static <T extends BlockEntity> BlockEntityType<T> build(BlockEntityType.BlockEntitySupplier<T> factory, Block... blocks) {
        return BlockEntityType.Builder.of(factory, blocks).build(null);
    }

    private SinguloBlockEntities() {}
}
