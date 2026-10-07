package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.item.GravitonManipulatorItem;
import io.github.genichimaruo.singulo.multiblock.Blueprints;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** マニピュレーターの投げる強さと、JEI に出すマルチブロックの部品表。 */
@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class ManipulatorGameTests {
    private static final String EMPTY = "empty";

    private ManipulatorGameTests() {}

    /** ためるほど強く、ためきったら頭打ち。少し上向きに投げる。 */
    @GameTest(template = EMPTY)
    public static void manipulatorFlingScalesWithCharge(GameTestHelper helper) {
        Vec3 look = new Vec3(1, 0, 0);
        double weak = GravitonManipulatorItem.flingVelocity(look, 0).x;
        double strong = GravitonManipulatorItem.flingVelocity(look, GravitonManipulatorItem.MAX_CHARGE).x;
        double over = GravitonManipulatorItem.flingVelocity(look, GravitonManipulatorItem.MAX_CHARGE * 5).x;
        helper.assertTrue(strong > weak * 3, "ためても強くならない: " + weak + " → " + strong);
        helper.assertTrue(over == strong, "ためすぎで強くなりすぎる");
        helper.assertTrue(GravitonManipulatorItem.flingVelocity(look, 10).y > 0, "上向きの成分がない");
        helper.succeed();
    }

    /** どのマルチブロックも部品表にコントローラー（1個）と部品があること。大きさもログに出す。 */
    @GameTest(template = EMPTY)
    public static void multiblockPartLists(GameTestHelper helper) {
        for (Blueprints.Kind kind : Blueprints.Kind.values()) {
            Map<Block, Integer> parts = Blueprints.partCounts(kind);
            helper.assertTrue(parts.get(Blueprints.controllerBlock(kind)) == 1, kind.id() + " のコントローラーが1個でない");
            helper.assertTrue(parts.size() >= 2, kind.id() + " の部品がない");
            int minX = 99, minY = 99, minZ = 99, maxX = -99, maxY = -99, maxZ = -99;
            for (Map.Entry<BlockPos, BlockState> e : Blueprints.layout(kind, BlockPos.ZERO, Direction.NORTH, kind.defaultSize()).entrySet()) {
                if (e.getValue().isAir()) {
                    continue;
                }
                BlockPos p = e.getKey();
                minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX());
                minY = Math.min(minY, p.getY()); maxY = Math.max(maxY, p.getY());
                minZ = Math.min(minZ, p.getZ()); maxZ = Math.max(maxZ, p.getZ());
            }
            Singulo.LOGGER.info("[mb] {} {}x{}x{} parts={}", kind.id(), maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1, parts.size());
        }
        helper.succeed();
    }
}
