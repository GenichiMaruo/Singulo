package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.block.MixedGlassSlab;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class MixedGlassSlabGameTests {
    private static final BlockPos AT = new BlockPos(3, 3, 3);

    // Other GameTests in the same world operate shield towers at ground level.
    private static final BlockPos BLAST_AT = AT.above(128);

    private static BlockState mixed(boolean blackOnTop) {
        return SinguloBlocks.MIXED_GLASS_SLAB.get().defaultBlockState().setValue(MixedGlassSlab.BLACK_ON_TOP, blackOnTop);
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
                "Stacking must consume exactly one slab");
    }

    @GameTest(template = "empty")
    public static void bothColoursStackFromEitherSideAndDropSeparately(GameTestHelper h) {
        var player = h.makeMockPlayer(GameType.SURVIVAL);
        var pos = h.absolutePos(AT);
        for (boolean blackOnTop : new boolean[]{false, true}) {
            var expected = mixed(blackOnTop);
            for (boolean topFirst : new boolean[]{false, true}) {
                var first = MixedGlassSlab.half(expected, topFirst);
                var second = MixedGlassSlab.half(expected, !topFirst);
                var face = topFirst ? Direction.DOWN : Direction.UP;
                h.setBlock(AT, first.setValue(SlabBlock.WATERLOGGED, true));
                place(h, player, second.getBlock(), hit(pos, face, 0.5));
                var result = h.getBlockState(AT);
                h.assertTrue(result == expected && result.getFluidState().isEmpty()
                        && Block.isShapeFullBlock(result.getCollisionShape(h.getLevel(), pos)), "Incorrect stacked state");
                for (boolean top : new boolean[]{false, true}) {
                    var target = hit(pos, Direction.NORTH, top ? 0.75 : 0.25);
                    h.assertTrue(result.getCloneItemStack(target, h.getLevel(), pos, player).is(MixedGlassSlab.half(result, top).getBlock().asItem()),
                            "Pick block returned the wrong material");
                }
                var drops = Block.getDrops(result, h.getLevel(), pos, null);
                h.assertTrue(drops.size() == 2 && drops.stream().allMatch(s -> s.getCount() == 1)
                        && drops.stream().anyMatch(s -> s.is(first.getBlock().asItem()))
                        && drops.stream().anyMatch(s -> s.is(second.getBlock().asItem())), "Mining did not return one of each slab");
                h.assertTrue(result.skipRendering(result, Direction.EAST) && !result.skipRendering(mixed(!blackOnTop), Direction.EAST)
                        && result.skipRendering(MixedGlassSlab.half(result, true).setValue(SlabBlock.TYPE, SlabType.BOTTOM), Direction.UP),
                        "Same-material contact faces did not match the two layers");
            }
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
        player.setYRot(0);
        player.setXRot(0);
        for (boolean top : new boolean[]{false, true}) {
            var state = mixed(true);
            h.setBlock(AT, state);
            player.setPos(pos.getX() + 0.5, pos.getY() + (top ? 0.75 : 0.25) - player.getEyeHeight(), pos.getZ() - 0.75);
            var surface = MixedGlassSlab.half(state, top);
            h.assertTrue(state.getDestroyProgress(player, h.getLevel(), pos) == surface.getDestroyProgress(player, h.getLevel(), pos)
                            && state.getSoundType(h.getLevel(), pos, player) == surface.getSoundType(h.getLevel(), pos, player),
                    "Mining hardness and sound must follow the targeted half");
        }
        var lower = MixedGlassSlab.half(mixed(true), false);
        var upper = MixedGlassSlab.half(mixed(true), true);
        h.setBlock(AT, lower);
        h.setBlock(AT.east(), Blocks.AIR);
        place(h, player, upper.getBlock(), hit(pos, Direction.EAST, 0.25));
        h.assertTrue(h.getBlockState(AT) == lower && h.getBlockState(AT.east()).is(upper.getBlock()), "Clicking the occupied half merged across cells");
        place(h, player, lower.getBlock(), hit(pos, Direction.UP, 0.5));
        h.assertTrue(h.getBlockState(AT) == lower.setValue(SlabBlock.TYPE, SlabType.DOUBLE), "Same-colour double slab changed");
        h.assertTrue(new ItemStack(Items.IRON_PICKAXE).isCorrectToolForDrops(mixed(true)), "Iron pickaxe cannot harvest the mixed block");
        h.succeed();
    }

    // Isolate just the origin and target cells so the high-power case cannot damage nearby fixtures.
    private static Explosion blast(GameTestHelper h, Vec3 offset, float power, Explosion.BlockInteraction mode) {
        var pos = h.absolutePos(BLAST_AT);
        Vec3 origin = Vec3.atLowerCornerOf(pos).add(offset);
        var start = BlockPos.containing(origin);
        var calculator = new ExplosionDamageCalculator() {
            @Override public Optional<Float> getBlockExplosionResistance(Explosion explosion, BlockGetter level, BlockPos at,
                                                                           BlockState state, FluidState fluid) {
                return at.equals(pos) || at.equals(start) ? super.getBlockExplosionResistance(explosion, level, at, state, fluid)
                        : Optional.of(Float.MAX_VALUE);
            }
            @Override public boolean shouldDamageEntity(Explosion explosion, Entity entity) { return false; }
            @Override public float getKnockbackMultiplier(Entity entity) { return 0; }
        };
        var explosion = new Explosion(h.getLevel(), null, null, calculator, origin.x, origin.y, origin.z, power, false, mode,
                ParticleTypes.EXPLOSION, ParticleTypes.EXPLOSION_EMITTER, SoundEvents.GENERIC_EXPLODE);
        explosion.explode();
        explosion.finalizeExplosion(false);
        return explosion;
    }

    private static void checkDrops(GameTestHelper h, int white, int black) {
        var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(BLAST_AT)).inflate(1));
        var whiteItem = SinguloBlocks.VARIANTS.get("white_glass_panel_slab").get().asItem();
        var blackItem = SinguloBlocks.VARIANTS.get("black_reinforced_glass_slab").get().asItem();
        int whites = 0, blacks = 0;
        for (var drop : drops) {
            var stack = drop.getItem();
            if (stack.is(whiteItem)) whites += stack.getCount();
            if (stack.is(blackItem)) blacks += stack.getCount();
            drop.discard();
        }
        h.assertTrue(whites == white && blacks == black, "Unexpected explosion drops: white=" + whites + ", black=" + blacks);
    }

    @GameTest(template = "empty")
    public static void moderateBlastRespectsExposedHalfAndPreservesBlackSlab(GameTestHelper h) {
        var pos = h.absolutePos(BLAST_AT);
        for (boolean blackOnTop : new boolean[]{true, false}) {
            var state = mixed(blackOnTop);
            h.setBlock(BLAST_AT, state);
            blast(h, new Vec3(0.5, blackOnTop ? -0.05 : 1.05, 0.5), 1, Explosion.BlockInteraction.DESTROY);
            h.assertTrue(h.getBlockState(BLAST_AT) == state, "A weak blast broke the exposed white glass");
            checkDrops(h, 0, 0);
            blast(h, new Vec3(0.5, blackOnTop ? 1.05 : -0.05, 0.5), 6, Explosion.BlockInteraction.DESTROY);
            h.assertTrue(h.getBlockState(BLAST_AT) == state, "Blast penetrated the shielded black face");
            checkDrops(h, 0, 0);
            for (Vec3 origin : new Vec3[]{new Vec3(0.5, blackOnTop ? -0.05 : 1.05, 0.5),
                                          new Vec3(-0.05, blackOnTop ? 0.25 : 0.75, 0.5)}) {
                h.setBlock(BLAST_AT, state);
                var blast = blast(h, origin, 4, Explosion.BlockInteraction.DESTROY);
                h.assertTrue(h.getBlockState(BLAST_AT) == MixedGlassSlab.half(state, blackOnTop), "Black half did not survive in its original position");
                h.assertTrue(!blast.getToBlow().contains(pos), "Explosion packet would erase the surviving half");
                checkDrops(h, 1, 0);
            }
        }
        h.setBlock(BLAST_AT, Blocks.AIR);
        h.succeed();
    }

    @GameTest(template = "empty", batch = "mixed_glass_strong_blast")
    public static void strongBlastCanBreakBothAndTriggerOnlyBlastLeavesBoth(GameTestHelper h) {
        var state = mixed(true);
        h.setBlock(BLAST_AT, state);
        blast(h, new Vec3(0.5, 0.25, 0.5), 6, Explosion.BlockInteraction.TRIGGER_BLOCK);
        h.assertTrue(h.getBlockState(BLAST_AT) == state, "Trigger-only explosion broke the glass");
        checkDrops(h, 0, 0);
        var blast = blast(h, new Vec3(0.5, 0.5, 0.5), 650, Explosion.BlockInteraction.DESTROY);
        h.assertTrue(h.getBlockState(BLAST_AT).isAir(), "Strong blast left " + h.getBlockState(BLAST_AT));
        h.assertTrue(blast.getToBlow().contains(h.absolutePos(BLAST_AT)), "Full destruction was missing from the explosion packet");
        checkDrops(h, 1, 1);
        h.succeed();
    }
}
