package io.github.genichimaruo.singulo.registry;

import io.github.genichimaruo.singulo.cable.CableBlockEntity;
import io.github.genichimaruo.singulo.item.GravityGauntletItem;
import io.github.genichimaruo.singulo.machine.CatalystDeviceBlockEntity;
import io.github.genichimaruo.singulo.machine.MachineBlockEntity;
import io.github.genichimaruo.singulo.multiblock.PortBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

public final class SinguloCapabilities {
    private SinguloCapabilities() {}

    public static void register(RegisterCapabilitiesEvent event) {
        machine(event, SinguloBlockEntities.MACHINE.get());
        machine(event, SinguloBlockEntities.COOLING_TOWER_CONTROLLER.get());
        machine(event, SinguloBlockEntities.ACCELERATOR_CONTROLLER.get());
        machine(event, SinguloBlockEntities.DEGENERATE_COMPACTOR_CONTROLLER.get());
        machine(event, SinguloBlockEntities.CASIMIR_CAVITY_CONTROLLER.get());
        machine(event, SinguloBlockEntities.TIME_CRYSTAL_INCUBATOR.get());

        // 冷却塔の搬入出口は、形成済みのコントローラの入出力をそのまま見せる
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, SinguloBlockEntities.PORT.get(),
                (be, side) -> be.delegate(Capabilities.EnergyStorage.BLOCK));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, SinguloBlockEntities.PORT.get(),
                (be, side) -> be.delegate(Capabilities.ItemHandler.BLOCK));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, SinguloBlockEntities.PORT.get(),
                (be, side) -> be.delegate(Capabilities.FluidHandler.BLOCK));
        // マルチブロック搬入出ポートも同じ（出力の自動搬出はポート自身が行う）
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, SinguloBlockEntities.MULTIBLOCK_PORT.get(),
                (be, side) -> be.delegate(Capabilities.EnergyStorage.BLOCK));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, SinguloBlockEntities.MULTIBLOCK_PORT.get(),
                (be, side) -> be.delegate(Capabilities.ItemHandler.BLOCK));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, SinguloBlockEntities.MULTIBLOCK_PORT.get(),
                (be, side) -> be.delegate(Capabilities.FluidHandler.BLOCK));

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, SinguloBlockEntities.THERMOELECTRIC_GENERATOR.get(),
                (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, SinguloBlockEntities.CRYOGENIC_TURBINE.get(),
                (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, SinguloBlockEntities.CRYOGENIC_TURBINE.get(),
                (be, side) -> be.tank());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, SinguloBlockEntities.SMES_CELL.get(),
                (be, side) -> be.energyFor(side));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, SinguloBlockEntities.COSMIC_MUON_COLLECTOR.get(),
                (be, side) -> be.automationItems());
        catalystDevice(event, SinguloBlockEntities.WORLDLINE_ANCHOR.get());
        catalystDevice(event, SinguloBlockEntities.INERTIAL_STABILIZER.get());
        catalystDevice(event, SinguloBlockEntities.QUANTUM_HEAT_ENGINE.get());
        catalystDevice(event, SinguloBlockEntities.DEGENERATE_FURNACE.get());
        catalystDevice(event, SinguloBlockEntities.PROBE_STATION.get());
        catalystDevice(event, SinguloBlockEntities.WORLDLINE_ANCHOR_ADVANCED.get());
        catalystDevice(event, SinguloBlockEntities.SHIELD_TOWER.get());
        catalystDevice(event, SinguloBlockEntities.TIPLER_CYLINDER.get());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, SinguloBlockEntities.CREATIVE_ENERGY.get(),
                (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, SinguloBlockEntities.PENROSE_REACTOR.get(),
                (be, side) -> be.darkMatterInput());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, SinguloBlockEntities.HALO_COLLECTOR.get(),
                (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, SinguloBlockEntities.HALO_COLLECTOR.get(),
                (be, side) -> be.automationFluids());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, SinguloBlockEntities.CONTAINMENT_TANK.get(),
                (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, SinguloBlockEntities.CONTAINMENT_TANK.get(),
                (be, side) -> be.tank());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, SinguloBlockEntities.WORMHOLE_GENERATOR.get(),
                (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, SinguloBlockEntities.WORMHOLE_GENERATOR.get(),
                (be, side) -> be.automationItems());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, SinguloBlockEntities.WORMHOLE_STABILIZER.get(),
                (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, SinguloBlockEntities.WORMHOLE_STABILIZER.get(),
                (be, side) -> be.automationItems());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, SinguloBlockEntities.WORMHOLE_MOUTH.get(),
                (be, side) -> be.automationItems());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, SinguloBlockEntities.WORMHOLE_PORT.get(),
                (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, SinguloBlockEntities.WORMHOLE_PORT.get(),
                (be, side) -> be.items());
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, SinguloBlockEntities.WORMHOLE_PORT.get(),
                (be, side) -> be.fluids());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, SinguloBlockEntities.SMES_MODULE.get(),
                (be, side) -> be.energyFor(side));
        event.registerItem(Capabilities.EnergyStorage.ITEM, (stack, ctx) -> GravityGauntletItem.energy(stack),
                SinguloItems.INERTIAL_CONTROL_GAUNTLET.get(), SinguloItems.GRAVITON_MANIPULATOR.get());
        event.registerItem(Capabilities.EnergyStorage.ITEM,
                (stack, ctx) -> io.github.genichimaruo.singulo.item.NeutrinoScannerItem.energy(stack), SinguloItems.NEUTRINO_SCANNER.get());

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, SinguloBlockEntities.NEUTRINO_OBSERVATORY.get(),
                (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, SinguloBlockEntities.GRAVITATIONAL_WAVE_DETECTOR.get(),
                (be, side) -> be.energy());

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, SinguloBlockEntities.PENROSE_REACTOR.get(),
                (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, SinguloBlockEntities.PENROSE_REACTOR.get(),
                (be, side) -> be.automationItems());

        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, SinguloBlockEntities.CABLE.get(),
                CableBlockEntity::energyFor);
    }

    private static <T extends MachineBlockEntity> void machine(RegisterCapabilitiesEvent event, BlockEntityType<T> type) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, type, (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, type, (be, side) -> be.itemsFor(side));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, type, (be, side) -> be.fluidsFor(side));
    }

    private static <T extends CatalystDeviceBlockEntity> void catalystDevice(RegisterCapabilitiesEvent event,
                                                                          BlockEntityType<T> type) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, type, (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, type, (be, side) -> be.automationItems());
    }

}
