package io.github.genichimaruo.singulo.pipe;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.machine.CryogenicTurbineBlockEntity;
import io.github.genichimaruo.singulo.machine.MachineBlockEntity;
import io.github.genichimaruo.singulo.machine.MachineType;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloFluids;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("singulo_scaling")
@PrefixGameTestTemplate(false)
public final class FluidPipeScalingGameTests {
    @GameTest(template = "empty")
    public static void locallyReachableWaterStillFlowsAfterNitrogenSaturatesTrunk(GameTestHelper h) {
        BlockPos start = new BlockPos(1, 2, 3);
        for (int i = 0; i < 6; i++) {
            h.setBlock(start.east(i), i == 2 ? SinguloBlocks.FLUID_PIPE.get() : SinguloBlocks.VACUUM_JACKETED_PIPE.get());
        }
        // 2台で1万mB/周期の中央の銅管を使い切る。受け手側には独立して水も汲める枝がある。
        for (int i = 0; i < 2; i++) {
            BlockPos pos = start.east(i).north();
            h.setBlock(pos, SinguloBlocks.MACHINES.get(MachineType.ELECTROLYZER).get());
            MachineBlockEntity source = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, pos);
            source.forceOutputTankForTest(new FluidStack(SinguloFluids.get("liquid_nitrogen"), 10_000));
        }
        BlockPos nitrogenSink = start.east(4).north();
        h.setBlock(nitrogenSink, SinguloBlocks.CRYOGENIC_TURBINE.get());
        BlockPos waterSink = start.east(3).north();
        h.setBlock(waterSink, SinguloBlocks.MACHINES.get(MachineType.ELECTROLYZER).get());

        FluidPipeBlockEntity first = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, start);
        FluidPipeNetwork.build(h.getLevel(), first);
        for (int i = 0; i < 2; i++) {
            FluidPipeBlockEntity pipe = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, start.east(i));
            pipe.inlet(net.minecraft.core.Direction.NORTH).fill(new FluidStack(SinguloFluids.get("liquid_nitrogen"), 10_000), IFluidHandler.FluidAction.EXECUTE);
        }
        FluidPipeBlockEntity local = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, start.east(5));
        local.inlet(net.minecraft.core.Direction.SOUTH).fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
        CryogenicTurbineBlockEntity turbine = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, nitrogenSink);
        MachineBlockEntity electrolyzer = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, waterSink);
        h.assertTrue(turbine.tank().getFluidInTank(0).getAmount() == 10_000, "Nitrogen exceeded or missed the copper pipe's budget");
        FluidStack water = electrolyzer.automationFluids().getFluidInTank(0);
        h.assertTrue(water.getFluid() == Fluids.WATER && water.getAmount() == 1000, "Local water route was skipped after trunk saturation");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "pipe_scaling")
    public static void saturatedPipeTransfersWithoutCachingEveryEndpointPair(GameTestHelper h) {
        boolean benchmark = Boolean.getBoolean("singulo.testBenchmarks");
        for (int bank : benchmark ? List.of(8, 16, 32, 64) : List.of(4)) {
            measure(h, bank, benchmark);
        }
        h.succeed();
    }

    /** 縦幹線の下半分に送液機、上半分に受液機を1本につき1台置く。全便が中央の同じ銅パイプを通る。 */
    private static void measure(GameTestHelper h, int bank, boolean benchmark) {
        var sources = new ArrayList<MachineBlockEntity>();
        var sinks = new ArrayList<IFluidHandler>();
        List<BlockPos> blocks = new ArrayList<>();
        var base = new BlockPos(3, 1, 3);
        try {
            for (int i = 0; i < bank * 2; i++) {
                BlockPos pipe = base.above(i);
                BlockPos device = pipe.east();
                h.setBlock(pipe, SinguloBlocks.FLUID_PIPE.get());
                blocks.add(pipe);
                if (i < bank) {
                    h.setBlock(device, SinguloBlocks.MACHINES.get(MachineType.ELECTROLYZER).get());
                    sources.add(io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, device));
                } else {
                    h.setBlock(device, SinguloBlocks.CRYOGENIC_TURBINE.get());
                    CryogenicTurbineBlockEntity turbine = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, device);
                    sinks.add(turbine.tank());
                }
                blocks.add(device);
            }
            FluidPipeBlockEntity first = io.github.genichimaruo.singulo.test.harness.TestFixtures.blockEntity(h, base);
            FluidPipeNetwork.build(h.getLevel(), first);
            FluidPipeNetwork network = FluidPipeNetwork.of(first);
            int charge = 10_000;
            var nitrogen = new FluidStack(SinguloFluids.get("liquid_nitrogen"), charge);
            long[] times = new long[benchmark ? 21 : bank];
            int warmups = benchmark ? 8 : 0;
            long firstNanos = 0;
            for (int i = -warmups; i < times.length; i++) {
                for (var source : sources) source.forceOutputTankForTest(nitrogen.copy());
                for (var sink : sinks) sink.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.EXECUTE);
                long start = System.nanoTime();
                network.resetCapacity();
                for (var source : sources) {
                    var pipe = (FluidPipeBlockEntity) h.getLevel().getBlockEntity(source.getBlockPos().west());
                    int sent = pipe.inlet(net.minecraft.core.Direction.EAST).fill(nitrogen, IFluidHandler.FluidAction.EXECUTE);
                    if (sent > 0) source.ejectFluids().drain(io.github.genichimaruo.singulo.compat.Legacy.copyWithAmount(nitrogen, sent), IFluidHandler.FluidAction.EXECUTE);
                }
                long nanos = System.nanoTime() - start;
                if (i == -warmups) firstNanos = nanos;
                if (i >= 0) times[i] = nanos;
                long received = sinks.stream().mapToLong(s -> s.getFluidInTank(0).getAmount()).sum();
                h.assertTrue(received == charge, "Expected 10000 mB even after the previous cycle saturated; got " + received);
            }
            h.assertTrue(network.cachedPathCount() < bank * bank, "Cached routes across every saturated endpoint pair");
            if (benchmark) {
                Arrays.sort(times);
                Singulo.LOGGER.info(String.format(Locale.ROOT,
                        "[PipeScaling] sources=%d sinks=%d endpoints=%d paths=%d moved_mB=%d cold_ms=%.3f median_ms=%.3f p95_ms=%.3f samples=%d",
                        bank, bank, bank * 2, network.cachedPathCount(), charge, firstNanos / 1e6, times[10] / 1e6, times[19] / 1e6, times.length));
            }
        } finally {
            for (BlockPos pos : blocks) h.setBlock(pos, Blocks.AIR);
        }
    }
}
