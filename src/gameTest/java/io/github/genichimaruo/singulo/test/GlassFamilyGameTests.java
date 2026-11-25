package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** 0.23.0: この作品のガラス（アンドロイド用を除く）は、種類が違っても接する面を描かない。 */
@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class GlassFamilyGameTests {
    private GlassFamilyGameTests() {}

    private static BlockState slab(String id, SlabType type) {
        return SinguloBlocks.VARIANTS.get(id).get().defaultBlockState().setValue(SlabBlock.TYPE, type);
    }

    @GameTest(template = "empty")
    public static void differentGlassesHideSharedFaces(GameTestHelper h) {
        BlockState white = SinguloBlocks.WHITE_GLASS_PANEL.get().defaultBlockState();
        BlockState black = SinguloBlocks.BLACK_REINFORCED_GLASS.get().defaultBlockState();
        BlockState collapsarStar = SinguloBlocks.COLLAPSAR_STAR_GLASS.get().defaultBlockState();
        BlockState whiteStar = SinguloBlocks.WHITE_STAR_GLASS_PANEL.get().defaultBlockState();
        BlockState ruin = SinguloBlocks.INTACT_RUIN_GLASS.get().defaultBlockState();
        BlockState lab = SinguloBlocks.LAB_GLASS.get().defaultBlockState();
        BlockState android = SinguloBlocks.ANDROID_BARRIER_GLASS.get().defaultBlockState();
        for (BlockState[] pair : new BlockState[][]{{white, black}, {black, collapsarStar}, {whiteStar, white}, {ruin, lab}, {lab, white}}) {
            h.assertTrue(pair[0].skipRendering(pair[1], Direction.EAST) && pair[1].skipRendering(pair[0], Direction.WEST),
                    "違う種類のガラスの間の面を描いている: " + pair[0] + " / " + pair[1]);
        }
        h.assertFalse(white.skipRendering(android, Direction.EAST), "アンドロイド用のガラスとの間の面まで消した");
        // 違うガラスのハーフブロック（上付き）: 上の別のガラスのブロックとの間の面を描かない。横の面は半分しか覆わないので描く
        BlockState blackSlab = slab("black_reinforced_glass_slab", SlabType.TOP);
        h.assertTrue(blackSlab.skipRendering(white, Direction.UP), "ハーフブロックと上のガラスの間の面を描いている");
        h.assertTrue(white.skipRendering(blackSlab, Direction.DOWN), "ガラスと下のハーフブロックの間の面を描いている");
        h.assertFalse(white.skipRendering(blackSlab, Direction.EAST), "半分しか覆わない横の面を消した");
        // 重ねハーフブロック（白黒）とコラプサーのガラスの間も描かない
        BlockState mixed = SinguloBlocks.MIXED_GLASS_SLAB.get().defaultBlockState();
        h.assertTrue(collapsarStar.skipRendering(mixed, Direction.NORTH) && mixed.skipRendering(collapsarStar, Direction.SOUTH),
                "重ねハーフブロックと別のガラスの間の面を描いている");
        h.succeed();
    }
}
