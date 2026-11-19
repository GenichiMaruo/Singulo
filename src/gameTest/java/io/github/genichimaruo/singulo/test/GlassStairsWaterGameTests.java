package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.block.GlassJoin;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 水に浸かったガラスの階段（0.21.0）: 水は入り、描くときは空いている区画にだけ水を描く。その区画の分け方を確かめる。
 */
@GameTestHolder("singulo")
@PrefixGameTestTemplate(false)
public final class GlassStairsWaterGameTests {
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void glassStairsHoldWaterInTheOpenPart(GameTestHelper h) {
        var block = SinguloBlocks.VARIANTS.get("black_reinforced_glass_stairs").get();
        BlockState north = block.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
        // 下の段は全部、上の段は北側（z = 0）だけ埋まる
        h.assertTrue(GlassJoin.filledOctants(north) == 0b0011_1111, "Bottom stairs facing north: " + Integer.toBinaryString(GlassJoin.filledOctants(north)));
        BlockState top = north.setValue(StairBlock.HALF, Half.TOP);
        // 上下逆: 上の段は全部、下の段は北側だけ
        h.assertTrue(GlassJoin.filledOctants(top) == 0b1100_1111, "Top stairs facing north: " + Integer.toBinaryString(GlassJoin.filledOctants(top)));
        BlockState east = north.setValue(StairBlock.FACING, Direction.EAST);
        // 東向き: 上の段は東側（x = 1）だけ
        h.assertTrue(GlassJoin.filledOctants(east) == 0b1011_1011,
                "Bottom stairs facing east: " + Integer.toBinaryString(GlassJoin.filledOctants(east)));
        // 水は入る
        BlockPos at = new BlockPos(1, 1, 1);
        h.setBlock(at, north);
        h.assertTrue(((StairBlock) block).canPlaceLiquid(h.getLevel(), h.absolutePos(at), h.getBlockState(at), Fluids.WATER),
                "Glass stairs refuse water");
        h.setBlock(at, north.setValue(StairBlock.WATERLOGGED, true));
        h.assertTrue(h.getLevel().getFluidState(h.absolutePos(at)).is(Fluids.WATER), "Waterlogged glass stairs hold no water");
        // ハーフブロック（0.21.0）: 模様のないガラス4種。アンドロイド用はぶつかり方を持つ形。下付きは下の4区画、上付きは上の4区画
        for (String id : new String[] {"white_glass_panel_slab", "black_reinforced_glass_slab", "android_gate_glass_slab", "android_barrier_glass_slab"}) {
            var slab = SinguloBlocks.VARIANTS.get(id).get();
            h.assertTrue(slab instanceof GlassJoin.Slab, id + " is not a glass slab");
            h.assertTrue(id.startsWith("android") == (slab instanceof io.github.genichimaruo.singulo.android.AndroidGlassSlab),
                    id + " has the wrong android behaviour");
            BlockState s = slab.defaultBlockState();
            h.assertTrue(GlassJoin.filledOctants(s) == 0b0011_0011, id + " bottom: " + Integer.toBinaryString(GlassJoin.filledOctants(s)));
            BlockState t = s.setValue(net.minecraft.world.level.block.SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.TOP);
            h.assertTrue(GlassJoin.filledOctants(t) == 0b1100_1100, id + " top: " + Integer.toBinaryString(GlassJoin.filledOctants(t)));
        }
        BlockPos slabAt = new BlockPos(3, 1, 1);
        h.setBlock(slabAt, SinguloBlocks.VARIANTS.get("android_gate_glass_slab").get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.SlabBlock.WATERLOGGED, true));
        h.assertTrue(h.getLevel().getFluidState(h.absolutePos(slabAt)).is(Fluids.WATER), "Waterlogged glass slab holds no water");
        h.succeed();
    }
}
