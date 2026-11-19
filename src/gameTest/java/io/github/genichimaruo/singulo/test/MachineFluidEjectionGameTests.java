package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.darkmatter.ContainmentTankBlockEntity;
import io.github.genichimaruo.singulo.darkmatter.HaloCollectorBlockEntity;
import io.github.genichimaruo.singulo.drone.DronePortBlockEntity;
import io.github.genichimaruo.singulo.drone.FluidDronePortBlockEntity;
import io.github.genichimaruo.singulo.machine.AbstractMachineBlock;
import io.github.genichimaruo.singulo.machine.DeviceMenu;
import io.github.genichimaruo.singulo.machine.MachineBlockEntity;
import io.github.genichimaruo.singulo.machine.MachineType;
import io.github.genichimaruo.singulo.multiblock.MultiblockPortBlockEntity;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.test.harness.TestScope;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class MachineFluidEjectionGameTests {
    private static final BlockPos AT = new BlockPos(2, 2, 3);

    @GameTest(template = "empty")
    public static void allOutputTanksCanEjectIntMaxEachInOneTick(GameTestHelper h) {
        var level = h.getLevel();
        h.setBlock(AT, SinguloBlocks.MACHINES.get(MachineType.ELECTROLYZER).get());
        h.setBlock(AT.east(), SinguloBlocks.CREATIVE_FLUID_TRASH.get());
        MachineBlockEntity machine = h.getBlockEntity(AT);
        machine.automationFluids().fill(new FluidStack(Fluids.WATER, 500), FluidAction.EXECUTE);
        var saved = machine.saveWithoutMetadata(level.registryAccess());
        var tanks = saved.getList("tanks", Tag.TAG_COMPOUND);
        for (int i = 1; i <= 2; i++) {
            var tank = new FluidTank(Integer.MAX_VALUE);
            tank.fill(new FluidStack(i == 1 ? Fluids.WATER : Fluids.LAVA, Integer.MAX_VALUE), FluidAction.EXECUTE);
            tanks.set(i, tank.writeToNBT(level.registryAccess(), new CompoundTag()));
        }
        h.startSequence().thenWaitUntil(() -> h.assertTrue(level.getGameTime() % 5 != 0, "Wait between old ejection ticks"))
                .thenExecute(() -> {
                    for (boolean port : new boolean[]{false, true}) {
                        machine.loadWithComponents(saved, level.registryAccess());
                        var fluids = machine.automationFluids();
                        h.assertTrue(fluids.getFluidInTank(1).getAmount() == Integer.MAX_VALUE
                                && fluids.getFluidInTank(2).getAmount() == Integer.MAX_VALUE, "Missing test fluid");
                        if (port) {
                            MultiblockPortBlockEntity.pushFluids(level, h.absolutePos(AT.east()), Direction.WEST, machine.ejectFluids());
                        } else {
                            MachineBlockEntity.serverTick(level, machine.getBlockPos(), machine.getBlockState(), machine);
                        }
                        h.assertTrue(fluids.getFluidInTank(1).isEmpty() && fluids.getFluidInTank(2).isEmpty()
                                && fluids.getFluidInTank(0).getAmount() == 500, "Output limited or input drained, port=" + port);
                    }
                }).thenSucceed();
    }

    @GameTest(template = "empty")
    public static void darkMatterAndDroneTanksEjectOnlyWhatFitsEveryTick(GameTestHelper h) {
        var level = h.getLevel();
        h.setBlock(AT, SinguloBlocks.HALO_COLLECTOR.get());
        h.setBlock(AT.east(), SinguloBlocks.CONTAINMENT_TANK.get().defaultBlockState().setValue(AbstractMachineBlock.FACING, Direction.EAST));
        h.setBlock(AT.east(2), SinguloBlocks.FLUID_DRONE_PORT.get());
        h.setBlock(AT.east(3), SinguloBlocks.FLUID_DRONE_PORT.get());
        HaloCollectorBlockEntity collector = h.getBlockEntity(AT);
        ContainmentTankBlockEntity tank = h.getBlockEntity(AT.east());
        FluidDronePortBlockEntity drone = h.getBlockEntity(AT.east(2)), next = h.getBlockEntity(AT.east(3));
        var player = TestScope.player(h);
        try {
            player.setPos(tank.getBlockPos().getCenter());
            h.assertTrue(tank.onButton(player, DeviceMenu.Tank.BUTTON_EJECT), "Could not enable tank output");
        } finally {
            player.discard();
        }
        drone.sides().setEjectEnabled(true);
        h.startSequence().thenWaitUntil(() -> h.assertTrue(level.getGameTime() % 5 != 0, "Wait between old ejection ticks"))
                .thenExecute(() -> {
                    collector.tank().fill(ContainmentTankBlockEntity.darkMatter(2_500), FluidAction.EXECUTE);
                    tank.tank().fill(ContainmentTankBlockEntity.darkMatter(60_000), FluidAction.EXECUTE);
                    drone.sendTank().fill(ContainmentTankBlockEntity.darkMatter(5_000), FluidAction.EXECUTE);
                    HaloCollectorBlockEntity.serverTick(level, collector.getBlockPos(), collector.getBlockState(), collector);
                    h.assertTrue(collector.tank().isEmpty() && tank.amount() == 62_500, "Collector waited for collection interval");
                    ContainmentTankBlockEntity.serverTick(level, tank.getBlockPos(), tank.getBlockState(), tank);
                    h.assertTrue(drone.sendTank().getFluidAmount() == 64_000 && tank.amount() == 3_500,
                            "Containment tank did not send exactly 59,000 mB");
                    drone.receiveTank().fill(new FluidStack(Fluids.LAVA, 100_000), FluidAction.EXECUTE);
                    DronePortBlockEntity.serverTick(level, drone.getBlockPos(), drone.getBlockState(), drone);
                    h.assertTrue(next.sendTank().getFluidAmount() == 64_000 && drone.receiveTank().getFluidAmount() == 36_000,
                            "Drone port waited for its item interval or lost the remainder");
                }).thenSucceed();
    }
}
