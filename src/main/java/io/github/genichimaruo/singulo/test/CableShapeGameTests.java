package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.cable.CableBlock;
import io.github.genichimaruo.singulo.cable.CableProfile;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** ケーブルの当たり判定が見た目（断面）どおりで、まっすぐ敷くと中心も同じ断面になること。 */
@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class CableShapeGameTests {
    private CableShapeGameTests() {}

    @GameTest(template = "empty")
    public static void straightCableKeepsProfile(GameTestHelper helper) {
        for (int z = 1; z <= 3; z++) {
            helper.setBlock(new BlockPos(3, 1, z), SinguloBlocks.TOPOLOGICAL_WIRE.get());
        }
        BlockState mid = helper.getBlockState(new BlockPos(3, 1, 2));
        helper.assertTrue(mid.getValue(CableBlock.STRAIGHT) == CableProfile.Straight.Z, "まっすぐ扱いにならない: " + mid);
        AABB box = mid.getShape(helper.getLevel(), helper.absolutePos(new BlockPos(3, 1, 2)), CollisionContext.empty()).bounds();
        // トポロジカル導線の断面は 5〜11。まっすぐなら奥行きは 0〜16 で、幅・高さは断面のまま
        helper.assertTrue(Math.abs(box.minX - 5 / 16.0) < 1e-6 && Math.abs(box.maxX - 11 / 16.0) < 1e-6
                && box.minZ == 0 && box.maxZ == 1, "当たり判定が断面と合わない: " + box);
        BlockState end = helper.getBlockState(new BlockPos(3, 1, 1));
        helper.assertTrue(end.getValue(CableBlock.STRAIGHT) == CableProfile.Straight.NONE, "端がまっすぐ扱いになる");
        helper.succeed();
    }
}
