package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.block.MixedPanelSlab;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import io.github.genichimaruo.singulo.test.harness.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import io.github.genichimaruo.singulo.test.harness.GameTestHolder;
import io.github.genichimaruo.singulo.test.harness.PrefixGameTestTemplate;

/** 白いパネルと黒色強化パネルのハーフブロックを同じマスに重ねる（0.23.0。ガラスの重ねハーフブロックと同じ決まり）。 */
@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class MixedPanelSlabGameTests {
    private static final BlockPos AT = new BlockPos(3, 3, 3);

    private static BlockState mixed(boolean blackOnTop) {
        return blackOnTop ? MixedPanelSlab.of(MixedPanelSlab.Panel.WHITE, MixedPanelSlab.Panel.BLACK)
                : MixedPanelSlab.of(MixedPanelSlab.Panel.BLACK, MixedPanelSlab.Panel.WHITE);
    }

    private static BlockHitResult hit(BlockPos pos, Direction face, double y) {
        Vec3 center = pos.getBottomCenter().add(0, y, 0);
        return new BlockHitResult(face.getAxis().isHorizontal() ? center.relative(face, 0.5) : center, face, pos, false);
    }

    private static void place(GameTestHelper h, Player player, Block held, BlockHitResult hit) {
        var stack = new ItemStack(held, 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit);
        h.assertTrue(((BlockItem) stack.getItem()).place(context).consumesAction() && stack.getCount() == 1,
                "重ねるときにハーフブロックを1つだけ使わない");
    }

    @GameTest(template = "empty")
    public static void bothPanelsStackFromEitherSideAndDropSeparately(GameTestHelper h) {
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        var pos = h.absolutePos(AT);
        for (boolean blackOnTop : new boolean[]{false, true}) {
            var expected = mixed(blackOnTop);
            for (boolean topFirst : new boolean[]{false, true}) {
                var first = MixedPanelSlab.half(expected, topFirst);
                var second = MixedPanelSlab.half(expected, !topFirst);
                h.setBlock(AT, first);
                place(h, player, second.getBlock(), hit(pos, topFirst ? Direction.DOWN : Direction.UP, 0.5));
                var result = h.getBlockState(AT);
                h.assertTrue(result == expected && Block.isShapeFullBlock(result.getCollisionShape(h.getLevel(), pos)),
                        "重ねたブロックの状態か形がおかしい");
                for (boolean top : new boolean[]{false, true}) {
                    player.snapTo(new Vec3(pos.getX()+0.5, pos.getY()+(top ? 0.75 : 0.25)-player.getEyeHeight(), pos.getZ()-1.5), 0, 0);
                    h.assertTrue(result.getCloneItemStack(pos, h.getLevel(), false, player)
                            .is(MixedPanelSlab.half(result, top).getBlock().asItem()), "ブロックの選択で、狙った方のハーフブロックを取らない");
                }
                var drops = Block.getDrops(result, h.getLevel(), pos, null);
                h.assertTrue(drops.size() == 2 && drops.stream().allMatch(s -> s.getCount() == 1)
                        && drops.stream().anyMatch(s -> s.is(first.getBlock().asItem()))
                        && drops.stream().anyMatch(s -> s.is(second.getBlock().asItem())), "壊すと両方のハーフブロックが1つずつ落ちない");
            }
        }
        // 掘る速さは狙った方のハーフブロックに合わせる
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
        player.setYRot(0);
        player.setXRot(0);
        for (boolean top : new boolean[]{false, true}) {
            var state = mixed(true);
            h.setBlock(AT, state);
            player.setPos(pos.getX() + 0.5, pos.getY() + (top ? 0.75 : 0.25) - player.getEyeHeight(), pos.getZ() - 0.75);
            var surface = MixedPanelSlab.half(state, top);
            h.assertTrue(state.getDestroyProgress(player, h.getLevel(), pos) == surface.getDestroyProgress(player, h.getLevel(), pos),
                    "掘る速さが狙った方のハーフブロックと違う");
        }
        // 埋まっている側の面をクリックしたら、となりのマスに置く（重ねない）。同じ色どうしはふつうの2枚重ね
        var lower = MixedPanelSlab.half(mixed(true), false);
        var upper = MixedPanelSlab.half(mixed(true), true);
        h.setBlock(AT, lower);
        h.setBlock(AT.east(), Blocks.AIR);
        place(h, player, upper.getBlock(), hit(pos, Direction.EAST, 0.25));
        h.assertTrue(h.getBlockState(AT) == lower && h.getBlockState(AT.east()).is(upper.getBlock()), "となりのマスをクリックしたのに重なった");
        place(h, player, lower.getBlock(), hit(pos, Direction.UP, 0.5));
        h.assertTrue(h.getBlockState(AT) == lower.setValue(SlabBlock.TYPE, SlabType.DOUBLE), "同じ色の2枚重ねが変わった");
        h.assertTrue(new ItemStack(Items.IRON_PICKAXE).isCorrectToolForDrops(mixed(true)), "鉄のツルハシで重ねたブロックを掘れない");
        h.succeed();
    }

    /**
     * コラプサー強化パネルのハーフブロックも、白・黒のどちらとも重ねられる。コラプサーを含む重ねハーフブロックと、コラプサー強化パネルの
     * ブロック・変種は、ウィザーとエンダードラゴンに壊されない。爆発への強さは、爆発の高さの方のハーフブロックに合わせる。
     */
    @GameTest(template = "empty")
    public static void collapsarStacksWithEitherPanelAndResistsTheWither(GameTestHelper h) {
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        var pos = h.absolutePos(AT);
        var collapsarSlab = SinguloBlocks.VARIANTS.get("collapsar_reinforced_panel_slab").get();
        for (var other : new MixedPanelSlab.Panel[]{MixedPanelSlab.Panel.WHITE, MixedPanelSlab.Panel.BLACK}) {
            var otherSlab = SinguloBlocks.VARIANTS.get(other.base + "_slab").get();
            h.setBlock(AT, otherSlab.defaultBlockState());
            place(h, player, collapsarSlab, hit(pos, Direction.UP, 0.5));
            var result = h.getBlockState(AT);
            h.assertTrue(result == MixedPanelSlab.of(other, MixedPanelSlab.Panel.COLLAPSAR), "コラプサーが上に重ならない: " + result);
            var drops = Block.getDrops(result, h.getLevel(), pos, null);
            h.assertTrue(drops.size() == 2 && drops.stream().anyMatch(s -> s.is(collapsarSlab.asItem()))
                    && drops.stream().anyMatch(s -> s.is(otherSlab.asItem())), "重ねたコラプサーのハーフブロックが両方落ちない");
            var wither = net.minecraft.world.entity.EntityType.WITHER.create(h.getLevel(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            h.assertFalse(result.canEntityDestroy(h.getLevel(), pos, wither), "コラプサーを含む重ねハーフブロックをウィザーが壊せる");
        }
        h.assertTrue(MixedPanelSlab.of(MixedPanelSlab.Panel.WHITE, MixedPanelSlab.Panel.BLACK)
                .canEntityDestroy(h.getLevel(), pos, net.minecraft.world.entity.EntityType.WITHER.create(h.getLevel(), net.minecraft.world.entity.EntitySpawnReason.COMMAND)),
                "コラプサーを含まない重ねハーフブロックまでウィザーに強くなった");
        for (String id : new String[]{"collapsar_reinforced_panel_stairs", "collapsar_reinforced_panel_slab", "collapsar_reinforced_panel_wall"}) {
            var state = SinguloBlocks.VARIANTS.get(id).get().defaultBlockState();
            h.assertTrue(state.is(net.minecraft.tags.BlockTags.WITHER_IMMUNE) && state.is(net.minecraft.tags.BlockTags.DRAGON_IMMUNE),
                    "ウィザー・ドラゴンに壊されないタグがない: " + id);
        }
        var full = SinguloBlocks.COLLAPSAR_REINFORCED_PANEL.get().defaultBlockState();
        h.assertTrue(full.is(net.minecraft.tags.BlockTags.WITHER_IMMUNE) && full.is(net.minecraft.tags.BlockTags.DRAGON_IMMUNE)
                && full.getBlock().getExplosionResistance() >= 1_000_000, "コラプサー強化パネルが爆発・ウィザーに強くない");
        h.assertFalse(new ItemStack(Items.IRON_PICKAXE).isCorrectToolForDrops(full), "コラプサー強化パネルを鉄のツルハシで掘れる");
        h.assertTrue(new ItemStack(Items.DIAMOND_PICKAXE).isCorrectToolForDrops(full), "コラプサー強化パネルをダイヤのツルハシで掘れない");
        // 発光コラプサー強化パネルと、コラプサー強化ガラス（とその階段・ハーフブロック）も同じ強さ
        var light = SinguloBlocks.COLLAPSAR_REINFORCED_LIGHT_PANEL.get().defaultBlockState();
        h.assertTrue(light.getLightEmission() == 15, "発光コラプサー強化パネルが明るさ15で光らない");
        for (var state : new BlockState[]{light, SinguloBlocks.COLLAPSAR_REINFORCED_GLASS.get().defaultBlockState(),
                SinguloBlocks.COLLAPSAR_STAR_GLASS.get().defaultBlockState(),
                SinguloBlocks.VARIANTS.get("collapsar_reinforced_glass_stairs").get().defaultBlockState(),
                SinguloBlocks.VARIANTS.get("collapsar_reinforced_glass_slab").get().defaultBlockState()}) {
            h.assertTrue(state.is(net.minecraft.tags.BlockTags.WITHER_IMMUNE) && state.is(net.minecraft.tags.BlockTags.DRAGON_IMMUNE)
                    && state.getBlock().getExplosionResistance() >= 1_000_000 && !new ItemStack(Items.IRON_PICKAXE).isCorrectToolForDrops(state)
                    && new ItemStack(Items.DIAMOND_PICKAXE).isCorrectToolForDrops(state), "コラプサーの強さがない: " + state);
        }
        h.assertTrue(SinguloBlocks.VARIANTS.get("collapsar_reinforced_glass_slab").get() instanceof io.github.genichimaruo.singulo.block.GlassJoin.Slab
                && SinguloBlocks.VARIANTS.get("collapsar_reinforced_glass_stairs").get() instanceof io.github.genichimaruo.singulo.block.GlassJoin.Stairs,
                "コラプサー強化ガラスの階段・ハーフブロックが、元のガラスとの境目を消すガラスになっていない");
        h.succeed();
    }

    /**
     * コラプサー強化ガラスのハーフブロックは、白・黒のガラスのハーフブロックと重ねられる（新しいブロック mixed_collapsar_glass_slab）。
     * 白と黒は、これまでどおりの白黒の重ねハーフブロック（保存された状態を変えない）。
     * コラプサーを含むと、ウィザーに壊されず、両方を落とすにはダイヤ以上のツルハシが要る。
     */
    @GameTest(template = "empty")
    public static void collapsarGlassStacksWithWhiteAndBlackGlass(GameTestHelper h) {
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        var pos = h.absolutePos(AT);
        var collapsarSlab = SinguloBlocks.VARIANTS.get("collapsar_reinforced_glass_slab").get();
        for (var other : new io.github.genichimaruo.singulo.block.MixedGlassSlab.Glass[]{
                io.github.genichimaruo.singulo.block.MixedGlassSlab.Glass.WHITE, io.github.genichimaruo.singulo.block.MixedGlassSlab.Glass.BLACK}) {
            var otherSlab = SinguloBlocks.VARIANTS.get(other.base + "_slab").get();
            for (boolean collapsarOnTop : new boolean[]{false, true}) {
                var first = collapsarOnTop ? otherSlab : collapsarSlab;
                var second = collapsarOnTop ? collapsarSlab : otherSlab;
                h.setBlock(AT, first.defaultBlockState());
                place(h, player, second, hit(pos, Direction.UP, 0.5));
                var result = h.getBlockState(AT);
                h.assertTrue(result.is(SinguloBlocks.MIXED_COLLAPSAR_GLASS_SLAB.get())
                        && result.getValue(io.github.genichimaruo.singulo.block.MixedGlassSlab.Collapsar.COLLAPSAR_ON_TOP) == collapsarOnTop
                        && result.getValue(io.github.genichimaruo.singulo.block.MixedGlassSlab.Collapsar.OTHER) == other,
                        "コラプサーガラスの重ねハーフブロックにならない: " + result);
                h.assertTrue(io.github.genichimaruo.singulo.block.MixedGlassSlab.half(result, collapsarOnTop).is(collapsarSlab)
                        && io.github.genichimaruo.singulo.block.MixedGlassSlab.half(result, !collapsarOnTop).is(otherSlab), "上下のハーフブロックが違う");
                var drops = Block.getDrops(result, h.getLevel(), pos, null);
                h.assertTrue(drops.size() == 2 && drops.stream().anyMatch(s -> s.is(collapsarSlab.asItem()))
                        && drops.stream().anyMatch(s -> s.is(otherSlab.asItem())), "壊すと両方のハーフブロックが1つずつ落ちない");
                h.assertFalse(result.canEntityDestroy(h.getLevel(), pos, net.minecraft.world.entity.EntityType.WITHER.create(h.getLevel(), net.minecraft.world.entity.EntitySpawnReason.COMMAND)),
                        "コラプサーガラスを含む重ねハーフブロックをウィザーが壊せる");
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
                h.assertFalse(result.canHarvestBlock(h.getLevel(), pos, player), "鉄のツルハシでコラプサーのハーフブロックまで落とせる");
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
                h.assertTrue(result.canHarvestBlock(h.getLevel(), pos, player), "ダイヤのツルハシで落とせない");
            }
        }
        // 白と黒は、元からの白黒の重ねハーフブロック
        h.setBlock(AT, SinguloBlocks.VARIANTS.get("white_glass_panel_slab").get().defaultBlockState());
        place(h, player, SinguloBlocks.VARIANTS.get("black_reinforced_glass_slab").get(), hit(pos, Direction.UP, 0.5));
        h.assertTrue(h.getBlockState(AT) == SinguloBlocks.MIXED_GLASS_SLAB.get().defaultBlockState()
                .setValue(io.github.genichimaruo.singulo.block.MixedGlassSlab.BLACK_ON_TOP, true), "白と黒の重ね方が変わった");
        h.succeed();
    }
}
