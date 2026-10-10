package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.multiblock.AcceleratorPartBlock;
import io.github.genichimaruo.singulo.multiblock.AcceleratorPartBlock.Segment;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class AcceleratorVisualGameTests {
    private AcceleratorVisualGameTests() {}

    @GameTest(template = "empty")
    public static void acceleratorPulseFollowsClosedTrack(GameTestHelper helper) {
        for (int side : new int[]{8, 16, 32}) {
            var ring = new io.github.genichimaruo.singulo.multiblock.AcceleratorRing(BlockPos.ZERO, side);
            double length = ring.perimeter();
            // Exercise negative tail positions and the wrap, not just a single lap.
            for (int i = -200; i <= 1200; i++) {
                double distance = length * i / 1000;
                var point = ring.point(distance);
                var wrapped = ring.point(distance + length);
                helper.assertTrue(Math.hypot(point.x() - wrapped.x(), point.z() - wrapped.z()) < 1e-8,
                        "Pulse jumps between laps for side " + side);
                helper.assertTrue(Math.abs(Math.hypot(point.tangentX(), point.tangentZ()) - 1) < 1e-8,
                        "Pulse tangent has incorrect length");
                double x = point.x(), z = point.z();
                helper.assertTrue(x >= .5 - 1e-8 && x <= side - .5 + 1e-8
                                && z >= .5 - 1e-8 && z <= side - .5 + 1e-8,
                        "Pulse leaves ring bounds");
                boolean corner = (x < 1 || x > side - 1) && (z < 1 || z > side - 1);
                if (corner) {
                    double cx = x < 1 ? 1 : side - 1, cz = z < 1 ? 1 : side - 1;
                    helper.assertTrue(Math.abs(Math.hypot(x - cx, z - cz) - .5) < 1e-8,
                            "Pulse does not follow the textured bend");
                } else {
                    double edge = Math.min(Math.min(x, side - x), Math.min(z, side - z));
                    helper.assertTrue(Math.abs(edge - .5) < 1e-8, "Pulse leaves the straight beam track");
                }
            }
            double leg = (side - 2) + Math.PI / 4;
            for (int edge = 0; edge < 4; edge++) {
                for (double join : new double[]{edge * leg, edge * leg + side - 2}) {
                    var before = ring.point(join - 1e-6);
                    var after = ring.point(join + 1e-6);
                    helper.assertTrue(Math.hypot(before.x() - after.x(), before.z() - after.z()) < 3e-6
                                    && Math.hypot(before.tangentX() - after.tangentX(),
                                                  before.tangentZ() - after.tangentZ()) < 1e-5,
                            "Pulse position or direction jumps at a corner");
                }
            }
        }
        helper.succeed();
    }

    private static Block part(int x, int z) {
        boolean corner = (x == 0 || x == 7) && (z == 0 || z == 7);
        boolean magnet = corner || (x == 3 && z == 0) || (x == 0 && z == 4) || (x == 7 && z == 3);
        return magnet ? SinguloBlocks.FOCUSING_MAGNET.get() : SinguloBlocks.ACCELERATOR_TUBE.get();
    }

    private static void check(GameTestHelper helper, boolean formed) {
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                if (x != 0 && x != 7 && z != 0 && z != 7) continue;
                BlockPos p = new BlockPos(x + 1, 1, z + 1);
                if (helper.getBlockState(p).isAir()) continue;
                Segment expected = Segment.NONE;
                if (formed) {
                    expected = z == 0 ? (x == 0 ? Segment.NORTH_WEST : x == 7 ? Segment.NORTH_EAST : Segment.NORTH)
                            : z == 7 ? (x == 0 ? Segment.SOUTH_WEST : x == 7 ? Segment.SOUTH_EAST : Segment.SOUTH)
                            : x == 0 ? Segment.WEST : Segment.EAST;
                }
                helper.assertTrue(helper.getBlockState(p).getValue(AcceleratorPartBlock.SEGMENT) == expected,
                        "Wrong accelerator segment at " + p + ": expected " + expected);
            }
        }
    }

    @GameTest(template = "huge", timeoutTicks = 60)
    public static void acceleratorConnectsBreaksAndReconnects(GameTestHelper helper) {
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                if (x == 0 || x == 7 || z == 0 || z == 7) {
                    helper.setBlock(new BlockPos(x + 1, 1, z + 1), part(x, z));
                }
            }
        }
        BlockPos gap = new BlockPos(3, 1, 1);
        BlockPos branch = new BlockPos(4, 1, 2);
        BlockPos corner = new BlockPos(1, 1, 1);
        helper.runAtTickTime(5, () -> check(helper, true));
        helper.runAtTickTime(6, () -> helper.setBlock(gap, Blocks.AIR));
        helper.runAtTickTime(10, () -> check(helper, false));
        helper.runAtTickTime(11, () -> helper.setBlock(gap, part(2, 0)));
        helper.runAtTickTime(15, () -> check(helper, true));
        helper.runAtTickTime(16, () -> helper.setBlock(branch, SinguloBlocks.ACCELERATOR_TUBE.get()));
        helper.runAtTickTime(20, () -> check(helper, false));
        helper.runAtTickTime(21, () -> helper.setBlock(branch, Blocks.AIR));
        helper.runAtTickTime(25, () -> check(helper, true));
        helper.runAtTickTime(26, () -> helper.setBlock(corner, SinguloBlocks.ACCELERATOR_TUBE.get()));
        helper.runAtTickTime(30, () -> check(helper, false));
        helper.runAtTickTime(31, () -> helper.setBlock(corner, SinguloBlocks.FOCUSING_MAGNET.get()));
        helper.runAtTickTime(35, () -> {
            check(helper, true);
            helper.succeed();
        });
    }
}
