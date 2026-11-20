package io.github.genichimaruo.singulo.android;

import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * アンドロイドが立つ所・飛ぶ所・水面を探すときも、アンドロイド用のガラスをアンドロイドとして見る（0.21.0）。
 * 透過ガラスはすり抜けるので床にもふたにもならず、遮断ガラスは止まるので床になる。
 */
@GameTestHolder("singulo")
@PrefixGameTestTemplate(false)
public final class AndroidGlassFootingGameTests {
    @GameTest(template = "empty")
    public static void androidGlassFootingFollowsTheAndroid(GameTestHelper h) throws ReflectiveOperationException {
        BlockState gate = SinguloBlocks.ANDROID_GATE_GLASS.get().defaultBlockState();
        BlockState barrier = SinguloBlocks.ANDROID_BARRIER_GLASS.get().defaultBlockState();
        var level = h.getLevel();
        RuinAndroid android = h.spawnWithNoFreeWill(SinguloEntities.BLADE_ANDROID.get(), new BlockPos(6, 4, 6));

        // 飛ぶ所: 透過ガラスの上は床がない、遮断ガラスと石の上は床がある
        h.setBlock(new BlockPos(1, 2, 1), gate);
        h.setBlock(new BlockPos(1, 2, 3), barrier);
        h.setBlock(new BlockPos(1, 2, 5), Blocks.STONE);
        BlockPos g = h.absolutePos(new BlockPos(1, 3, 1)), b = h.absolutePos(new BlockPos(1, 3, 3)), s = h.absolutePos(new BlockPos(1, 3, 5));
        h.assertFalse(android.flyable(g.getX(), g.getY(), g.getZ()), "Gate glass counts as a floor for flying");
        h.assertTrue(android.flyable(b.getX(), b.getY(), b.getZ()), "Barrier glass does not count as a floor for flying");
        h.assertTrue(android.flyable(s.getX(), s.getY(), s.getZ()), "Stone does not count as a floor for flying");

        // 作業の立ち位置: 目の前の4方向の足もとがどれも透過ガラスなら立てない。1つを遮断ガラスにすると、そこに立つ
        BlockPos target = new BlockPos(4, 3, 2);
        h.setBlock(target, Blocks.STONE);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            h.setBlock(target.relative(d).below(), gate);
            h.setBlock(target.relative(d).below(2), Blocks.AIR);
        }
        var standSpot = AndroidWorkGoal.class.getDeclaredMethod("standSpot", BlockPos.class);
        standSpot.setAccessible(true);
        var goalField = RuinAndroid.class.getDeclaredField("workGoal");
        goalField.setAccessible(true);
        Object goal = goalField.get(android);
        h.assertTrue(goal != null, "No work goal");
        h.assertTrue(standSpot.invoke(goal, h.absolutePos(target)) == null, "An android stands on gate glass");
        h.setBlock(target.east().below(), barrier);
        h.assertTrue(h.absolutePos(target.east()).equals(standSpot.invoke(goal, h.absolutePos(target))),
                "An android does not stand on barrier glass");

        // 水面: 水の上が透過ガラスなら抜けられる。石のふたは抜けられない
        BlockPos water = new BlockPos(6, 1, 1);
        h.setBlock(water, Blocks.WATER);
        h.setBlock(water.above(), gate);
        android.moveTo(h.absoluteVec(net.minecraft.world.phys.Vec3.atBottomCenterOf(water)));
        h.assertTrue(android.hasWaterSurface(), "Gate glass over water hides the surface");
        h.setBlock(water.above(), Blocks.STONE);
        h.assertFalse(android.hasWaterSurface(), "Stone over water counts as a surface");
        android.discard();
        h.succeed();
    }
}
