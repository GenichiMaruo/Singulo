package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.block.MixedGlassSlab;
import io.github.genichimaruo.singulo.block.MixedPanelGlassSlab;
import io.github.genichimaruo.singulo.block.MixedPanelSlab;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import io.github.genichimaruo.singulo.test.harness.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import io.github.genichimaruo.singulo.test.harness.GameTestHolder;
import io.github.genichimaruo.singulo.test.harness.PrefixGameTestTemplate;

/**
 * 0.23.0: ガラス（白・黒・コラプサー）とパネル（白・黒・コラプサー）のハーフブロックを同じマスに重ねる（{@link MixedPanelGlassSlab}）。
 * どちらを先に置いても、どちらが上でも重なり、壊すと両方が1つずつ落ちる。半分が透けるので隣を隠さず、
 * となりのガラスとの間ではガラスの半分の面を描かない。
 */
@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class MixedPanelGlassSlabGameTests {
    private static final BlockPos AT = new BlockPos(3, 3, 3);

    private static BlockHitResult hit(BlockPos pos, Direction face, double y) {
        Vec3 center = pos.getBottomCenter().add(0, y, 0);
        return new BlockHitResult(face.getAxis().isHorizontal() ? center.relative(face, 0.5) : center, face, pos, false);
    }

    private static void place(GameTestHelper h, Player player, Block held, BlockHitResult hit) {
        var stack = new ItemStack(held, 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit);
        h.assertTrue(((BlockItem) stack.getItem()).place(context).consumesAction() && stack.getCount() == 1,
                "重ねるときにハーフブロックを1つだけ使わない: " + held);
    }

    @GameTest(template = "empty")
    public static void glassAndPanelSlabsStackAndDropSeparately(GameTestHelper h) {
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        var pos = h.absolutePos(AT);
        for (var glass : MixedGlassSlab.Glass.values()) {
            for (var panel : MixedPanelSlab.Panel.values()) {
                for (boolean glassOnTop : new boolean[] {false, true}) {
                    var expected = MixedPanelGlassSlab.of(glass, panel, glassOnTop);
                    for (boolean topFirst : new boolean[] {false, true}) {
                        var first = MixedPanelGlassSlab.half(expected, topFirst);
                        var second = MixedPanelGlassSlab.half(expected, !topFirst);
                        h.setBlock(AT, first);
                        place(h, player, second.getBlock(), hit(pos, topFirst ? Direction.DOWN : Direction.UP, 0.5));
                        var result = h.getBlockState(AT);
                        h.assertTrue(result == expected && Block.isShapeFullBlock(result.getCollisionShape(h.getLevel(), pos)),
                                "重ねたブロックの状態か形がおかしい: " + result + " (expected " + expected + ")");
                        var drops = Block.getDrops(result, h.getLevel(), pos, null);
                        h.assertTrue(drops.size() == 2 && drops.stream().allMatch(s -> s.getCount() == 1)
                                && drops.stream().anyMatch(s -> s.is(first.getBlock().asItem()))
                                && drops.stream().anyMatch(s -> s.is(second.getBlock().asItem())), "壊すと両方のハーフブロックが1つずつ落ちない: " + drops);
                    }
                }
            }
        }
        // 同じ種類どうし（ガラスとガラス・パネルとパネル）は、これまでどおりの重ね方
        h.setBlock(AT, SinguloBlocks.VARIANTS.get("white_panel_slab").get().defaultBlockState());
        place(h, player, SinguloBlocks.VARIANTS.get("black_reinforced_panel_slab").get(), hit(pos, Direction.UP, 0.5));
        h.assertTrue(h.getBlockState(AT).is(SinguloBlocks.MIXED_PANEL_SLAB.get()), "パネルどうしの重ね方が変わった");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void glassHalfStaysSeeThroughAndCollapsarResists(GameTestHelper h) {
        var level = h.getLevel();
        var pos = h.absolutePos(AT);
        var mixed = MixedPanelGlassSlab.of(MixedGlassSlab.Glass.WHITE, MixedPanelSlab.Panel.BLACK, true);
        h.setBlock(AT, mixed);
        var state = h.getBlockState(AT);
        h.assertFalse(state.canOcclude() || state.isSolidRender(), "半分が透けるのに隣を隠す");
        // となりのガラスとは、ガラスの半分の面を描かない（パネルの半分はガラス越しに見えるので描く）
        var glassBlock = SinguloBlocks.WHITE_GLASS_PANEL.get().defaultBlockState();
        h.assertTrue(MixedPanelGlassSlab.halfHidden(state, true, glassBlock, Direction.EAST), "ガラスの半分と、となりのガラスの間を描く");
        h.assertFalse(MixedPanelGlassSlab.halfHidden(state, false, glassBlock, Direction.EAST), "ガラス越しのパネルの半分の面を消した");
        h.assertTrue(glassBlock.skipRendering(state, Direction.WEST), "となりのガラスが、重ねたブロックとの間の面を描く");
        // 上の面: ガラスの半分の上に、ガラスのハーフブロック（下付き）が載ると描かない
        var glassSlabAbove = SinguloBlocks.VARIANTS.get("black_reinforced_glass_slab").get().defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        h.assertTrue(state.skipRendering(glassSlabAbove, Direction.UP), "上のガラスのハーフブロックとの間を描く");
        // 不透明な1ブロックが隣なら、その面は描かない
        var panelBlock = SinguloBlocks.WHITE_PANEL.get().defaultBlockState();
        h.assertTrue(state.skipRendering(panelBlock, Direction.EAST), "不透明なブロックに隠れる面を描く");

        // コラプサーを含むと、ウィザーとエンダードラゴンに壊されない
        var wither = EntityType.WITHER.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        var dragon = EntityType.ENDER_DRAGON.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
        h.assertTrue(state.canEntityDestroy(level, pos, wither), "コラプサーのない重ねブロックがウィザーに耐えた");
        for (BlockState proof : new BlockState[] {MixedPanelGlassSlab.of(MixedGlassSlab.Glass.COLLAPSAR, MixedPanelSlab.Panel.WHITE, false),
                MixedPanelGlassSlab.of(MixedGlassSlab.Glass.BLACK, MixedPanelSlab.Panel.COLLAPSAR, true)}) {
            h.setBlock(AT, proof);
            var placed = h.getBlockState(AT);
            h.assertFalse(placed.canEntityDestroy(level, pos, wither) || placed.canEntityDestroy(level, pos, dragon),
                    "コラプサーを含む重ねブロックがウィザーかドラゴンに壊される: " + placed);
        }
        h.succeed();
    }
}
