package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.drone.FluidDronePortBlockEntity;
import io.github.genichimaruo.singulo.machine.CryogenicTurbineBlockEntity;
import io.github.genichimaruo.singulo.machine.MachineBlockEntity;
import io.github.genichimaruo.singulo.machine.MachineType;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloFluids;
import io.github.genichimaruo.singulo.wormhole.WormholeData;
import io.github.genichimaruo.singulo.wormhole.WormholeMouthBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import io.github.genichimaruo.singulo.compat.Capabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("singulo")
@PrefixGameTestTemplate(false)
public final class WormholeFluidPipeGameTests {
    private static final BlockPos LOCAL = new BlockPos(2, 2, 1);
    private static final BlockPos REMOTE = new BlockPos(5, 2, 6);

    private static void connect(GameTestHelper h) {
        long pair = h.getLevel().random.nextLong();
        for (BlockPos pos : new BlockPos[]{LOCAL.west(), REMOTE.east()}) {
            h.setBlock(pos, SinguloBlocks.WORMHOLE_MOUTH.get());
            WormholeMouthBlockEntity mouth = h.getBlockEntity(pos);
            mouth.setData(new WormholeData(pair, 0));
        }
        h.setBlock(LOCAL, SinguloBlocks.WORMHOLE_PORT.get());
        h.setBlock(REMOTE, SinguloBlocks.WORMHOLE_PORT.get());
        h.setBlock(LOCAL.above(), SinguloBlocks.FLUID_PIPE.get());
    }

    @GameTest(template = "empty")
    public static void pipeChecksEachRemoteInput(GameTestHelper h) {
        connect(h);
        h.setBlock(LOCAL.above(2), SinguloBlocks.MACHINES.get(MachineType.ELECTROLYZER).get());
        MachineBlockEntity source = h.getBlockEntity(LOCAL.above(2));
        FluidStack hydrogen = new FluidStack(SinguloFluids.get("hydrogen"), 1000);
        source.forceOutputTankForTest(hydrogen.copy());

        // The port visits its lower neighbour before the upper one.
        h.setBlock(REMOTE.below(), SinguloBlocks.MACHINES.get(MachineType.ELECTROLYZER).get());
        MachineBlockEntity electrolyzer = h.getBlockEntity(REMOTE.below());
        h.setBlock(REMOTE.above(), SinguloBlocks.FLUID_DRONE_PORT.get());
        FluidDronePortBlockEntity receiver = h.getBlockEntity(REMOTE.above());
        h.runAfterDelay(30, () -> {
            h.assertTrue(electrolyzer.automationFluids().getFluidInTank(0).isEmpty(), "Electrolyzer accepted piped hydrogen");
            h.assertTrue(FluidStack.matches(hydrogen, receiver.sendTank().getFluid()), "Hydrogen failed to reach the drone port");
            IFluidHandler normal = io.github.genichimaruo.singulo.compat.Capabilities.get(h.getLevel(), Capabilities.FluidHandler.BLOCK, h.absolutePos(LOCAL), null);
            h.assertTrue(normal.fill(hydrogen, FluidAction.SIMULATE) == 1000, "Normal fill simulation was filtered");
            h.assertTrue(electrolyzer.automationFluids().getFluidInTank(0).isEmpty(), "Simulation changed the electrolyzer");
            h.assertTrue(normal.fill(hydrogen, FluidAction.EXECUTE) == 1000
                    && FluidStack.matches(hydrogen, electrolyzer.automationFluids().getFluidInTank(0)),
                    "Normal fill no longer reaches the first available tank");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void pipeLeavesRemoteTurbineFuelInPlace(GameTestHelper h) {
        connect(h);
        h.setBlock(LOCAL.above(2), SinguloBlocks.FLUID_DRONE_PORT.get());
        FluidDronePortBlockEntity receiver = h.getBlockEntity(LOCAL.above(2));
        h.setBlock(REMOTE.below(), SinguloBlocks.CRYOGENIC_TURBINE.get());
        CryogenicTurbineBlockEntity turbine = h.getBlockEntity(REMOTE.below());
        turbine.energy().setEnergy(turbine.energy().getMaxEnergyStored());
        FluidStack nitrogen = new FluidStack(SinguloFluids.get("liquid_nitrogen"), 2000);
        turbine.tank().fill(nitrogen, FluidAction.EXECUTE);
        h.runAfterDelay(30, () -> {
            h.assertTrue(receiver.sendTank().isEmpty() && FluidStack.matches(nitrogen, turbine.tank().getFluidInTank(0)),
                    "Pipe removed fuel from the remote turbine");
            IFluidHandler normal = io.github.genichimaruo.singulo.compat.Capabilities.get(h.getLevel(), Capabilities.FluidHandler.BLOCK, h.absolutePos(LOCAL), null);
            h.assertTrue(normal.drain(2000, FluidAction.SIMULATE).getAmount() == 1000, "Wrong simulated mouth bandwidth");
            h.assertTrue(normal.drain(nitrogen, FluidAction.EXECUTE).getAmount() == 1000, "Normal drain did not match simulation");
            h.assertTrue(normal.drain(2000, FluidAction.EXECUTE).isEmpty() && turbine.tank().getFluidInTank(0).getAmount() == 1000,
                    "Normal drain exceeded the remaining bandwidth");
            h.succeed();
        });
    }
}
