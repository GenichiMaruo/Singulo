package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.machine.AbstractMachineBlock;
import io.github.genichimaruo.singulo.machine.SmesCellBlockEntity;
import io.github.genichimaruo.singulo.monitor.FactoryMonitor;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.test.harness.TestScope;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("singulo")
@PrefixGameTestTemplate(false)
public final class FactoryLoadGameTests {
    @GameTest(template = "empty", batch = "factory_load", timeoutTicks = 750)
    public static void circulatingSmesCellsConserveEnergyAndAppearOnMonitor(GameTestHelper h) {
        boolean benchmark = Boolean.getBoolean("singulo.testBenchmarks");
        int[] counts = benchmark ? new int[]{0, 128, 512, 1024} : new int[]{4};
        int phaseTicks = benchmark ? 160 : 24;
        ServerLevel level = h.getLevel();
        var origin = h.absolutePos(new BlockPos(0, 100, 0));
        var monitor = origin.offset(7, 0, 7);
        var placed = new ArrayList<BlockPos>();
        TestScope.of(h).defer(() -> {
            for (var pos : placed) level.removeBlock(pos, false);
        });
        long background = FactoryMonitor.scan(level, monitor).stored();
        for (int i = 0; i < counts.length; i++) {
            int count = counts[i];
            h.runAtTickTime(1L + i * phaseTicks, () -> {
                while (placed.size() < count) addRing(level, origin, placed);
            });
            h.runAtTickTime((long)(i + 1) * phaseTicks, () -> {
                if (benchmark) {
                    long[] times = level.getServer().getTickTimesNanos().clone();
                    Arrays.sort(times);
                    Singulo.LOGGER.info("SMES factory: {} cells, server tick p50 {} ms, p95 {} ms",
                            count, times[49] / 1e6, times[94] / 1e6);
                }
                for (var pos : placed) {
                    h.assertTrue(level.getBlockState(pos).getValue(AbstractMachineBlock.LIT),
                            "Cell did not deliver energy in the ring at " + pos);
                }
                long expected = background + (long) count * (SmesCellBlockEntity.CAPACITY / 2);
                int samples = benchmark ? 15 : 1;
                long[] scan = new long[samples];
                for (int sample = -2; sample < samples; sample++) {
                    long start = System.nanoTime();
                    var snapshot = FactoryMonitor.scan(level, monitor);
                    long elapsed = System.nanoTime() - start;
                    h.assertTrue(snapshot.stored() == expected, "Monitor missed energy in circulating cells");
                    if (sample >= 0) scan[sample] = elapsed;
                }
                if (benchmark) {
                    Arrays.sort(scan);
                    Singulo.LOGGER.info("SMES factory: {} cells, monitor scan median {} ms", count, scan[samples / 2] / 1e6);
                }
                if (count == counts[counts.length - 1]) h.succeed();
            });
        }
    }

    private static void addRing(ServerLevel level, BlockPos origin, List<BlockPos> placed) {
        int ring = placed.size() / 4;
        var pos = origin.offset(ring % 8 * 2, ring / 64, ring / 8 % 8 * 2);
        for (var face : List.of(Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.NORTH)) {
            var state = SinguloBlocks.SMES_CELL.get().defaultBlockState().setValue(AbstractMachineBlock.FACING, face);
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
            placed.add(pos);
            var cell = (SmesCellBlockEntity) level.getBlockEntity(pos);
            cell.energy().setEnergy(SmesCellBlockEntity.CAPACITY / 2);
            pos = pos.relative(face);
        }
    }
}
