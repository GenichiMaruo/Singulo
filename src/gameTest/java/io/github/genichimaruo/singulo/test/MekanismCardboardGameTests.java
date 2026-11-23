package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.ruin.lab.PressureHullBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("singulo_mekanism")
@PrefixGameTestTemplate(false)
public final class MekanismCardboardGameTests {
    private static InteractionResult box(GameTestHelper h, BlockPos at) {
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        var stack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("mekanism:cardboard_box")));
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos abs = h.absolutePos(at);
        var hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
        return stack.getItem().onItemUseFirst(stack, new UseOnContext(h.getLevel(), player, InteractionHand.MAIN_HAND, stack, hit));
    }

    /** 封じられた耐圧殻・耐圧ガラス・溶接された蓋は段ボール箱に詰められない。封じが解けた耐圧殻は詰められる。 */
    @GameTest(template = "empty")
    public static void sealedHullCannotBeBoxed(GameTestHelper h) {
        BlockPos at = new BlockPos(2, 1, 2);
        for (Block block : new Block[] {SinguloBlocks.PRESSURE_HULL.get(), SinguloBlocks.PRESSURE_GLASS.get(), SinguloBlocks.WELDED_HATCH.get()}) {
            h.setBlock(at, block.defaultBlockState().setValue(PressureHullBlock.SEALED, true));
            box(h, at);
            h.assertTrue(h.getBlockState(at).is(block), "封じられたブロックを段ボール箱に詰められた: " + block);
        }
        h.setBlock(at, SinguloBlocks.PRESSURE_HULL.get().defaultBlockState().setValue(PressureHullBlock.SEALED, false));
        box(h, at);
        h.assertTrue(!h.getBlockState(at).is(SinguloBlocks.PRESSURE_HULL.get()), "封じが解けた耐圧殻を段ボール箱に詰められない");
        h.succeed();
    }
}
