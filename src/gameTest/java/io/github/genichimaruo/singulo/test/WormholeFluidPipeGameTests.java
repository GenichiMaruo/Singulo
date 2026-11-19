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
import io.github.genichimaruo.singulo.test.harness.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import io.github.genichimaruo.singulo.test.harness.GameTestHolder;
import io.github.genichimaruo.singulo.test.harness.PrefixGameTestTemplate;

@GameTestHolder("singulo")
@PrefixGameTestTemplate(false)
public final class WormholeFluidPipeGameTests {
    private static final BlockPos LOCAL = new BlockPos(2, 2, 1);
    private static final BlockPos REMOTE = new BlockPos(5, 2, 6);

    private static void connect(GameTestHelper h) {
        long pair = h.getLevel().getRandom().nextLong();
        for (BlockPos pos : new BlockPos[]{LOCAL.west(), REMOTE.east()}) {
            h.setBlock(pos, SinguloBlocks.WORMHOLE_MOUTH.get());
            WormholeMouthBlockEntity mouth = io.github.genichimaruo.singulo.test.harness.TestBlocks.get(h, pos);
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
        MachineBlockEntity source = io.github.genichimaruo.singulo.test.harness.TestBlocks.get(h, LOCAL.above(2));
        FluidStack hydrogen = new FluidStack(SinguloFluids.get("hydrogen"), 1000);
        source.forceOutputTankForTest(hydrogen.copy());

        // The port visits its lower neighbour before the upper one.
        h.setBlock(REMOTE.below(), SinguloBlocks.MACHINES.get(MachineType.ELECTROLYZER).get());
        MachineBlockEntity electrolyzer = io.github.genichimaruo.singulo.test.harness.TestBlocks.get(h, REMOTE.below());
        h.setBlock(REMOTE.above(), SinguloBlocks.FLUID_DRONE_PORT.get());
        FluidDronePortBlockEntity receiver = io.github.genichimaruo.singulo.test.harness.TestBlocks.get(h, REMOTE.above());
        h.runAfterDelay(30, () -> {
            h.assertTrue(electrolyzer.automationFluids().getFluidInTank(0).isEmpty(), "Electrolyzer accepted piped hydrogen");
            h.assertTrue(FluidStack.matches(hydrogen, receiver.sendTank().getFluid()), "Hydrogen failed to reach the drone port");
            IFluidHandler normal = io.github.genichimaruo.singulo.compat.FluidResourceView.find(h.getLevel(), h.absolutePos(LOCAL), null);
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
        FluidDronePortBlockEntity receiver = io.github.genichimaruo.singulo.test.harness.TestBlocks.get(h, LOCAL.above(2));
        h.setBlock(REMOTE.below(), SinguloBlocks.CRYOGENIC_TURBINE.get());
        CryogenicTurbineBlockEntity turbine = io.github.genichimaruo.singulo.test.harness.TestBlocks.get(h, REMOTE.below());
        turbine.energy().setEnergy(turbine.energy().getMaxEnergyStored());
        FluidStack nitrogen = new FluidStack(SinguloFluids.get("liquid_nitrogen"), 2000);
        turbine.tank().fill(nitrogen, FluidAction.EXECUTE);
        h.runAfterDelay(30, () -> {
            h.assertTrue(receiver.sendTank().isEmpty() && FluidStack.matches(nitrogen, turbine.tank().getFluidInTank(0)),
                    "Pipe removed fuel from the remote turbine");
            IFluidHandler normal = io.github.genichimaruo.singulo.compat.FluidResourceView.find(h.getLevel(), h.absolutePos(LOCAL), null);
            h.assertTrue(normal.drain(2000, FluidAction.SIMULATE).getAmount() == 1000, "Wrong simulated mouth bandwidth");
            h.assertTrue(normal.drain(nitrogen, FluidAction.EXECUTE).getAmount() == 1000, "Normal drain did not match simulation");
            h.assertTrue(normal.drain(2000, FluidAction.EXECUTE).isEmpty() && turbine.tank().getFluidInTank(0).getAmount() == 1000,
                    "Normal drain exceeded the remaining bandwidth");
            h.succeed();
        });
    }
}
