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
        event.registerBlockEntity(Capabilities.Energy.BLOCK, SinguloBlockEntities.COSMIC_CHAMBER.get(),
                (be,side)->be.kind()==io.github.genichimaruo.singulo.cosmic.CosmicChamberBlockEntity.RESERVOIR?null:be.energyFor(side));
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, SinguloBlockEntities.COSMIC_CHAMBER.get(),
                (be,side)->be.kind()==io.github.genichimaruo.singulo.cosmic.CosmicChamberBlockEntity.ACCUMULATOR?null:be.fluidFor(side));
        event.registerBlockEntity(Capabilities.Item.BLOCK, SinguloBlockEntities.COSMIC_CHAMBER.get(),
                (be,side)->be.kind()==io.github.genichimaruo.singulo.cosmic.CosmicChamberBlockEntity.SPINDLE?be.transferItems():null);
        machine(event, SinguloBlockEntities.MACHINE.get());
        machine(event, SinguloBlockEntities.COOLING_TOWER_CONTROLLER.get());
        machine(event, SinguloBlockEntities.ACCELERATOR_CONTROLLER.get());
        machine(event, SinguloBlockEntities.DEGENERATE_COMPACTOR_CONTROLLER.get());
        machine(event, SinguloBlockEntities.CASIMIR_CAVITY_CONTROLLER.get());
        machine(event, SinguloBlockEntities.TIME_CRYSTAL_INCUBATOR.get());

        // 冷却塔の搬入出口は、形成済みのコントローラの入出力をそのまま見せる
        event.registerBlockEntity(Capabilities.Energy.BLOCK, SinguloBlockEntities.PORT.get(),
                (be, side) -> be.energyProxy());
        event.registerBlockEntity(Capabilities.Item.BLOCK, SinguloBlockEntities.PORT.get(),
                (be, side) -> be.itemProxy());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, SinguloBlockEntities.PORT.get(),
                (be, side) -> be.fluidProxy());
        // マルチブロック搬入出ポートも同じ（出力の自動搬出はポート自身が行う）
        event.registerBlockEntity(Capabilities.Energy.BLOCK, SinguloBlockEntities.MULTIBLOCK_PORT.get(),
                (be, side) -> be.energyProxy());
        event.registerBlockEntity(Capabilities.Item.BLOCK, SinguloBlockEntities.MULTIBLOCK_PORT.get(),
                (be, side) -> be.itemProxy());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, SinguloBlockEntities.MULTIBLOCK_PORT.get(),
                (be, side) -> be.fluidProxy());

        event.registerBlockEntity(Capabilities.Energy.BLOCK, SinguloBlockEntities.THERMOELECTRIC_GENERATOR.get(),
                (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, SinguloBlockEntities.CRYOGENIC_TURBINE.get(),
                (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, SinguloBlockEntities.CRYOGENIC_TURBINE.get(),
                (be, side) -> be.tank());
        event.registerBlockEntity(Capabilities.Item.BLOCK, SinguloBlockEntities.CRYOGENIC_TURBINE.get(), (be, side) -> be.transferItems());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, SinguloBlockEntities.HYDROGEN_FUEL_CELL.get(),
                (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, SinguloBlockEntities.HYDROGEN_FUEL_CELL.get(),
                (be, side) -> be.fluids());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, SinguloBlockEntities.MUON_FUSION_REACTOR.get(),
                (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, SinguloBlockEntities.MUON_FUSION_REACTOR.get(),
                (be, side) -> be.tank());
        event.registerBlockEntity(Capabilities.Item.BLOCK, SinguloBlockEntities.MUON_FUSION_REACTOR.get(), (be, side) -> be.transferItems());
        // 冷媒カートリッジは液体窒素100 mBの容器（バケツと同じく、装置を右クリックして注ぐ・汲む）
        event.registerItem(Capabilities.Fluid.ITEM, (stack, ctx) -> io.github.genichimaruo.singulo.machine.CoolantCartridges.resourceHandler(ctx),
                io.github.genichimaruo.singulo.machine.CoolantCartridges.filled(), io.github.genichimaruo.singulo.machine.CoolantCartridges.empty(),
                io.github.genichimaruo.singulo.machine.CoolantCartridges.helium());
        // 液体キャニスターは、どの液体でも1,000 mBまでの容器
        event.registerItem(Capabilities.Fluid.ITEM, (stack, ctx) -> io.github.genichimaruo.singulo.item.FluidCanisterItem.resourceHandler(ctx),
                SinguloItems.FLUID_CANISTER.get());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, SinguloBlockEntities.SMES_CELL.get(),
                (be, side) -> be.energyFor(side));
        event.registerBlockEntity(Capabilities.Item.BLOCK, SinguloBlockEntities.ANDROID_DELIVERY_PORT.get(), (be, side) -> be.transferItems());
        event.registerBlockEntity(Capabilities.Item.BLOCK, SinguloBlockEntities.ANDROID_WORK_CONSOLE.get(), (be, side) -> be.transferItems());
        event.registerBlockEntity(Capabilities.Item.BLOCK, SinguloBlockEntities.COSMIC_MUON_COLLECTOR.get(), (be, side) -> be.transferItems());
        catalystDevice(event, SinguloBlockEntities.WORLDLINE_ANCHOR.get());
        catalystDevice(event, SinguloBlockEntities.INERTIAL_STABILIZER.get());
        catalystDevice(event, SinguloBlockEntities.QUANTUM_HEAT_ENGINE.get());
        catalystDevice(event, SinguloBlockEntities.DEGENERATE_FURNACE.get());
        catalystDevice(event, SinguloBlockEntities.PROBE_STATION.get());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, SinguloBlockEntities.DRONE_STATION.get(),
                (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.Item.BLOCK, SinguloBlockEntities.DRONE_STATION.get(), (be, side) -> be.transferItems());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, SinguloBlockEntities.DRONE_PORT.get(),
                (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.Item.BLOCK, SinguloBlockEntities.DRONE_PORT.get(), (be, side) -> be.transferItemsFor(side));
        event.registerBlockEntity(Capabilities.Energy.BLOCK, SinguloBlockEntities.FOOD_PRINTER.get(), (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.Item.BLOCK, SinguloBlockEntities.FOOD_PRINTER.get(), (be, side) -> be.transferItems());
        event.registerBlockEntity(Capabilities.Item.BLOCK, SinguloBlockEntities.HORIZON_VAULT.get(), (be, side) -> be.transferItems());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, SinguloBlockEntities.FLUID_DRONE_PORT.get(),
                (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.Item.BLOCK, SinguloBlockEntities.FLUID_DRONE_PORT.get(), (be, side) -> be.transferItemsFor(side));
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, SinguloBlockEntities.FLUID_DRONE_PORT.get(),
                (be, side) -> be.sidedFluids(side));
        event.registerBlockEntity(Capabilities.Energy.BLOCK, SinguloBlockEntities.ANDROID_STATION.get(),
                (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.Item.BLOCK, SinguloBlockEntities.ANDROID_STATION.get(), (be, side) -> be.transferItems());
        catalystDevice(event, SinguloBlockEntities.WORLDLINE_ANCHOR_ADVANCED.get());
        catalystDevice(event, SinguloBlockEntities.SHIELD_TOWER.get());
        catalystDevice(event, SinguloBlockEntities.TIPLER_CYLINDER.get());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, SinguloBlockEntities.GENESIS_CORE.get(),
                (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.Item.BLOCK, SinguloBlockEntities.GENESIS_CORE.get(), (be, side) -> be.transferItems());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, SinguloBlockEntities.CREATIVE_ENERGY.get(),
                (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, SinguloBlockEntities.PENROSE_REACTOR.get(),
                (be, side) -> be.darkMatterInput());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, SinguloBlockEntities.HALO_COLLECTOR.get(),
                (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, SinguloBlockEntities.HALO_COLLECTOR.get(),
                (be, side) -> be.automationFluids());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, SinguloBlockEntities.CONTAINMENT_TANK.get(),
                (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, SinguloBlockEntities.CONTAINMENT_TANK.get(),
                (be, side) -> be.tank());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, SinguloBlockEntities.WORMHOLE_GENERATOR.get(),
                (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.Item.BLOCK, SinguloBlockEntities.WORMHOLE_GENERATOR.get(), (be, side) -> be.transferItems());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, SinguloBlockEntities.JET_FORGE.get(),
                (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.Item.BLOCK, SinguloBlockEntities.JET_FORGE.get(), (be, side) -> be.transferItems());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, SinguloBlockEntities.JET_FORGE.get(),
                (be, side) -> be.coolant());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, SinguloBlockEntities.WORMHOLE_STABILIZER.get(),
                (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.Item.BLOCK, SinguloBlockEntities.WORMHOLE_STABILIZER.get(), (be, side) -> be.transferItems());
        event.registerBlockEntity(Capabilities.Item.BLOCK, SinguloBlockEntities.WORMHOLE_MOUTH.get(), (be, side) -> be.transferItems());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, SinguloBlockEntities.WORMHOLE_PORT.get(),
                (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.Item.BLOCK, SinguloBlockEntities.WORMHOLE_PORT.get(),
                (be, side) -> be.transferItems());
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, SinguloBlockEntities.WORMHOLE_PORT.get(),
                (be, side) -> be.fluids());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, SinguloBlockEntities.GRAVITY_RECEIVER.get(),
                (be, side) -> be.energy());
        // 衝撃発電パネルは取り出すだけ（ケーブルへ送り出す）
        event.registerBlockEntity(Capabilities.Energy.BLOCK, SinguloBlockEntities.IMPACT_GENERATOR.get(),
                (be, side) -> be.output());
        event.registerBlockEntity(Capabilities.Item.BLOCK, SinguloBlockEntities.DEEP_SEA_COLLECTOR.get(), (be, side) -> be.transferItems());
        event.registerBlockEntity(Capabilities.Item.BLOCK, SinguloBlockEntities.VOID_COLLECTOR.get(), (be, side) -> be.transferItems());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, SinguloBlockEntities.KERAUNOS_TOWER.get(),
                (be, side) -> be.energy());
        // 遺構保管庫と封印コンテナの中身は、ホッパーやパイプからは出し入れできない（空の入れ物に見せる）
        event.registerBlockEntity(Capabilities.Item.BLOCK, SinguloBlockEntities.RUIN_CACHE.get(),
                (be, side) -> new io.github.genichimaruo.singulo.compat.TransactionalItemStackHandler(0));
        event.registerBlockEntity(Capabilities.Item.BLOCK, SinguloBlockEntities.SEALED_CONTAINER.get(),
                (be, side) -> new io.github.genichimaruo.singulo.compat.TransactionalItemStackHandler(0));
        event.registerBlockEntity(Capabilities.Energy.BLOCK, SinguloBlockEntities.SMES_MODULE.get(),
                (be, side) -> be.energyFor(side));
        // SMES の充電の枠（ホッパーなどで、入れる枠へ入れ、充電済みの物を取り出す）
        event.registerBlockEntity(Capabilities.Item.BLOCK, SinguloBlockEntities.SMES_CELL.get(), (be, side) -> be.transferItems());
        event.registerBlockEntity(Capabilities.Item.BLOCK, SinguloBlockEntities.SMES_MODULE.get(), (be, side) -> be.transferItems());
        event.registerItem(Capabilities.Energy.ITEM, (stack, ctx) -> new io.github.genichimaruo.singulo.energy.SinguloItemEnergy(ctx, io.github.genichimaruo.singulo.android.AndroidChargerItem.CAPACITY),
                SinguloItems.ANDROID_CHARGER.get());
        event.registerItem(Capabilities.Energy.ITEM, (stack, ctx) -> new io.github.genichimaruo.singulo.energy.SinguloItemEnergy(ctx, ((GravityGauntletItem) stack.getItem()).capacity()),
                SinguloItems.INERTIAL_CONTROL_GAUNTLET.get(), SinguloItems.GRAVITON_MANIPULATOR.get());
        event.registerItem(Capabilities.Energy.ITEM,
                (stack, ctx) -> new io.github.genichimaruo.singulo.energy.SinguloItemEnergy(ctx, io.github.genichimaruo.singulo.item.NeutrinoScannerItem.CAPACITY), SinguloItems.NEUTRINO_SCANNER.get());

        event.registerBlockEntity(Capabilities.Energy.BLOCK, SinguloBlockEntities.NEUTRINO_OBSERVATORY.get(),
                (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.Energy.BLOCK, SinguloBlockEntities.GRAVITATIONAL_WAVE_DETECTOR.get(),
                (be, side) -> be.energy());

        event.registerBlockEntity(Capabilities.Energy.BLOCK, SinguloBlockEntities.PENROSE_REACTOR.get(),
                (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.Item.BLOCK, SinguloBlockEntities.PENROSE_REACTOR.get(), (be, side) -> be.transferItems());

        event.registerBlockEntity(Capabilities.Energy.BLOCK, SinguloBlockEntities.CABLE.get(),
                CableBlockEntity::energyFor);
    }

    private static <T extends MachineBlockEntity> void machine(RegisterCapabilitiesEvent event, BlockEntityType<T> type) {
        event.registerBlockEntity(Capabilities.Energy.BLOCK, type, (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.Item.BLOCK, type, (be, side) -> be.transferItemsFor(side));
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, type, (be, side) -> be.fluidsFor(side));
    }

    private static <T extends CatalystDeviceBlockEntity> void catalystDevice(RegisterCapabilitiesEvent event,
                                                                          BlockEntityType<T> type) {
        event.registerBlockEntity(Capabilities.Energy.BLOCK, type, (be, side) -> be.energy());
        event.registerBlockEntity(Capabilities.Item.BLOCK, type, (be, side) -> be.transferItems());
    }

}
