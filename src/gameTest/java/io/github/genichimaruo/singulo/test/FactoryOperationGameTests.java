package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.drone.FluidCapsuleItem;
import io.github.genichimaruo.singulo.drone.FluidDronePortBlockEntity;
import io.github.genichimaruo.singulo.energy.LongEnergyStorage;
import io.github.genichimaruo.singulo.generated.ServerConfig;
import io.github.genichimaruo.singulo.machine.AbstractMachineBlock;
import io.github.genichimaruo.singulo.machine.CreativeEnergyBlockEntity;
import io.github.genichimaruo.singulo.machine.HydrogenFuelCellBlockEntity;
import io.github.genichimaruo.singulo.machine.SmesCellBlockEntity;
import io.github.genichimaruo.singulo.multiblock.Structures;
import io.github.genichimaruo.singulo.pipe.FluidPipeBlockEntity;
import io.github.genichimaruo.singulo.reactor.PenroseReactorBlockEntity;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloFluids;
import io.github.genichimaruo.singulo.registry.SinguloItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class FactoryOperationGameTests {
    private static final BlockPos AT = new BlockPos(3, 2, 3);

    @GameTest(template = "empty")
    public static void fuelCellConsumesOnlyWhatFitsInBuffer(GameTestHelper h) {
        h.setBlock(AT, SinguloBlocks.HYDROGEN_FUEL_CELL.get());
        HydrogenFuelCellBlockEntity cell = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, AT);
        int old = ServerConfig.FUEL_CELL_OUTPUT.get();
        try {
            ServerConfig.FUEL_CELL_OUTPUT.set(48_000);
            cell.energy().setEnergy(45_200);
            cell.hydrogen().fill(new FluidStack(SinguloFluids.get("hydrogen"), 10_000), FluidAction.EXECUTE);
            cell.oxygen().fill(new FluidStack(SinguloFluids.get("oxygen"), 5_000), FluidAction.EXECUTE);
            HydrogenFuelCellBlockEntity.serverTick(h.getLevel(), cell.getBlockPos(), cell.getBlockState(), cell);
            h.assertTrue(cell.energy().getEnergyStored() == 50_000 && cell.rate() == 4_800
                    && cell.hydrogen().getFluidAmount() == 9_000 && cell.oxygen().getFluidAmount() == 4_500,
                    "Filling 4800 FE of headroom should consume 1000 mB hydrogen and 500 mB oxygen");
        } finally {
            ServerConfig.FUEL_CELL_OUTPUT.set(old);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void automatedCapsulePourDebitsTheUsedStack(GameTestHelper h) {
        h.setBlock(AT, SinguloBlocks.FLUID_DRONE_PORT.get());
        FluidDronePortBlockEntity port = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, AT);
        var tank = port.sendTank();
        tank.fill(new FluidStack(Fluids.LAVA, tank.getCapacity() - 200), FluidAction.EXECUTE);
        var empty = new ItemStack(SinguloItems.FLUID_CAPSULE.get());
        var capsule = FluidCapsuleItem.pack(empty, new FluidStack(Fluids.LAVA, 1_000));
        var pos = port.getBlockPos();
        var hit = new BlockHitResult(pos.getCenter(), Direction.UP, pos, false);
        var context = new UseOnContext(h.getLevel(), null, InteractionHand.MAIN_HAND, capsule, hit) {};
        capsule.useOn(context);
        h.assertTrue(tank.getFluidAmount() == tank.getCapacity() && FluidCapsuleItem.fluid(capsule).getAmount() == 800,
                "Partial automated pour must debit exactly 200 mB from the original capsule");
        tank.drain(Integer.MAX_VALUE, FluidAction.EXECUTE);
        capsule.useOn(context);
        capsule.useOn(context);
        h.assertTrue(tank.getFluidAmount() == 800 && ItemStack.isSameItemSameTags(capsule, empty),
                "Empty capsule should stack again and a repeated pour must supply no more fluid");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void fuelCellPowersMachineBeforeDirectPump(GameTestHelper h) {
        h.setBlock(AT, SinguloBlocks.FLUID_PUMP.get());
        h.setBlock(AT.north(), SinguloBlocks.FLUID_DRONE_PORT.get());
        h.setBlock(AT.east(), SinguloBlocks.FLUID_DRONE_PORT.get());
        h.setBlock(AT.above(), SinguloBlocks.HYDROGEN_FUEL_CELL.get());
        h.setBlock(AT.above(2), SinguloBlocks.SMES_CELL.get().defaultBlockState()
                .setValue(AbstractMachineBlock.FACING, Direction.SOUTH));
        HydrogenFuelCellBlockEntity cell = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, AT.above());
        SmesCellBlockEntity machine = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, AT.above(2));
        FluidDronePortBlockEntity source = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, AT.north()), sink = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, AT.east());
        source.receiveTank().fill(new FluidStack(Fluids.WATER, 1_000), FluidAction.EXECUTE);
        cell.energy().setEnergy(200);
        HydrogenFuelCellBlockEntity.serverTick(h.getLevel(), cell.getBlockPos(), cell.getBlockState(), cell);
        h.assertTrue(machine.energy().getEnergyStored() == 200 && sink.sendTank().isEmpty(),
                "Pump consumed power before the neighboring machine");
        machine.energy().setEnergy(machine.energy().getMaxEnergyStored());
        cell.energy().setEnergy(200);
        HydrogenFuelCellBlockEntity.serverTick(h.getLevel(), cell.getBlockPos(), cell.getBlockState(), cell);
        h.assertTrue(sink.sendTank().getFluidAmount() > 0, "Pump did not receive surplus power");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void energyExportDoesNotLoadAdjacentChunk(GameTestHelper h) {
        var level = h.getLevel();
        var far = h.absolutePos(AT).offset(40_000, 0, 40_000);
        var pos = new BlockPos((far.getX() & ~15) + 15, far.getY(), (far.getZ() & ~15) + 8);
        level.getChunkAt(pos);
        h.assertTrue(!level.isLoaded(pos.east()), "Expected an unloaded adjacent chunk before export");
        var state = SinguloBlocks.CREATIVE_ENERGY_SOURCE.get().defaultBlockState();
        CreativeEnergyBlockEntity.serverTick(level, pos, state, new CreativeEnergyBlockEntity(pos, state));
        h.assertTrue(!level.isLoaded(pos.east()), "Passive FE output loaded a neighboring chunk");
        Structures.findReactor(level, pos);
        Structures.findRing(level, pos);
        Structures.ringDirection(level, pos);
        Structures.acceleratorPorts(level, pos, Direction.NORTH);
        h.assertTrue(!level.isLoaded(pos.east()), "Structure inspection loaded a neighboring chunk");
        var controller = SinguloBlocks.CORE_CONTROLLER.get().defaultBlockState();
        var reactor = new PenroseReactorBlockEntity(pos, controller);
        reactor.setLevel(level);
        reactor.forceCore(0, 0);
        PenroseReactorBlockEntity.serverTick(level, pos, controller, reactor);
        h.assertTrue(!level.isLoaded(pos.east()) && reactor.state() == PenroseReactorBlockEntity.State.DORMANT,
                "Partial chunk data must not invalidate the reactor");
        level.getChunkAt(pos.east());
        PenroseReactorBlockEntity.serverTick(level, pos, controller, reactor);
        h.assertTrue(reactor.state() == PenroseReactorBlockEntity.State.UNFORMED, "Structure not checked when chunks returned");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void reactorSharesOutputLimitAcrossPushAndPull(GameTestHelper h) {
        h.setBlock(AT, SinguloBlocks.CORE_CONTROLLER.get());
        h.setBlock(AT.east(), SinguloBlocks.SMES_CELL.get().defaultBlockState()
                .setValue(AbstractMachineBlock.FACING, Direction.EAST));
        PenroseReactorBlockEntity reactor = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, AT);
        SmesCellBlockEntity sink = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, AT.east());
        var registry = h.getLevel().registryAccess();
        long limit = ServerConfig.PENROSE_MAX_OUTPUT.get();
        var saved = reactor.saveWithoutMetadata();
        saved.putLong("buffer", limit * 3);
        reactor.load(saved);
        var output = (LongEnergyStorage) reactor.energy();
        h.assertTrue(output.extractLong(100, false) == 0, "An inactive reactor allowed extraction");
        reactor.forceCore(500, 0);
        h.assertTrue(output.extractLong(-1, false) == 0 && reactor.buffer() == limit * 3, "Negative draw changed the buffer");
        h.assertTrue(output.extractLong(Long.MAX_VALUE, true) == limit && reactor.buffer() == limit * 3,
                "Simulated extraction exceeded the per-tick limit or consumed FE");
        h.assertTrue(output.extractLong(limit - 50, false) == limit - 50, "External draw failed");
        PenroseReactorBlockEntity.serverTick(h.getLevel(), reactor.getBlockPos(), reactor.getBlockState(), reactor);
        h.assertTrue(sink.energy().getEnergyStored() == 50 && output.extractLong(1, false) == 0
                && reactor.buffer() == 2 * limit, "Push and pull did not share the same tick budget");
        h.runAfterDelay(1, () -> {
            h.assertTrue(output.extractLong(100, true) == 100, "Next tick's budget did not reset");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void surveyReceivesFullHorizonDuctRate(GameTestHelper h) {
        h.setBlock(AT, SinguloBlocks.HORIZON_DUCT.get());
        h.setBlock(AT.east(), SinguloBlocks.CREATIVE_FLUID_TRASH.get());
        FluidPipeBlockEntity pipe = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, AT);
        var inlet = pipe.inlet(Direction.WEST);
        var water = new FluidStack(Fluids.WATER, Integer.MAX_VALUE);
        // One ten-tick sampling window at the rate of two full int-sized outputs per tick.
        for (int i = 0; i < 20; i++) {
            h.assertTrue(inlet.fill(water, FluidAction.EXECUTE) == Integer.MAX_VALUE, "Horizon duct throttled input");
        }
        var registry = h.getLevel().registryAccess();
        var display = new FluidPipeBlockEntity(pipe.getBlockPos(), pipe.getBlockState());
        h.startSequence().thenWaitUntil(() -> {
            display.load(pipe.getUpdateTag());
            h.assertTrue(display.flowRate() == 2L * Integer.MAX_VALUE, "Survey flow was truncated at the single-transfer limit");
        }).thenExecute(() -> {
            for (int i = 0; i < 20; i++) inlet.fill(io.github.genichimaruo.singulo.compat.Legacy.copyWithAmount(water, 2_000_000_000), FluidAction.EXECUTE);
        }).thenWaitUntil(() -> {
            display.load(pipe.getUpdateTag());
            h.assertTrue(display.flowRate() == 4_000_000_000L, "Survey kept the previous rate in the same display bucket");
        }).thenSucceed();
    }
}
