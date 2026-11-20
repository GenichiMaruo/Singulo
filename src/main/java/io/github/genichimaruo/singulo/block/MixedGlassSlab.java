package io.github.genichimaruo.singulo.block;

import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import java.util.function.BiConsumer;
import org.jspecify.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Two distinct glass slabs sharing a position. The original slab items are used for placement and drops. */
public final class MixedGlassSlab extends TransparentBlock {
    public static final BooleanProperty BLACK_ON_TOP = BooleanProperty.create("black_on_top");
    public static final int BOTTOM_HIT = 1, TOP_HIT = 2;

    public interface ExplosionHits {
        int singulo$glassHits(BlockPos pos);
    }

    public MixedGlassSlab(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(BLACK_ON_TOP, false));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(BLACK_ON_TOP);
    }

    public static BlockState half(BlockState mixed, boolean top) {
        String id = mixed.getValue(BLACK_ON_TOP) == top ? "black_reinforced_glass_slab" : "white_glass_panel_slab";
        return SinguloBlocks.VARIANTS.get(id).get().defaultBlockState().setValue(SlabBlock.TYPE, top ? SlabType.TOP : SlabType.BOTTOM);
    }

    public static boolean topAt(BlockPos pos, double y) { return y >= pos.getY() + 0.5; }

    private static BlockState selected(BlockState state, BlockPos pos, HitResult hit) {
        boolean top = !(hit instanceof BlockHitResult blockHit) || !blockHit.getBlockPos().equals(pos)
                || topAt(pos, hit.getLocation().y);
        return half(state, top);
    }

    @Override public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData, Player player) {
        return new ItemStack(selected(state, pos, player.pick(player.blockInteractionRange(), 1, false)).getBlock());
    }

    @Override protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return selected(state, pos, player.pick(player.blockInteractionRange(), 1, false)).getDestroyProgress(player, level, pos);
    }

    @Override public SoundType getSoundType(BlockState state, LevelReader level, BlockPos pos, @Nullable Entity entity) {
        BlockState surface = entity instanceof Player player
                ? selected(state, pos, player.pick(player.blockInteractionRange(), 1, false)) : half(state, true);
        return surface.getSoundType(level, pos, entity);
    }

    @Override public float getExplosionResistance(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
        return half(state, topAt(pos, explosion.center().y)).getExplosionResistance(level, pos, explosion);
    }

    @Override protected boolean skipRendering(BlockState state, BlockState adjacent, Direction direction) {
        return (direction == Direction.DOWN || hidesHalf(state, true, adjacent, direction))
                && (direction == Direction.UP || hidesHalf(state, false, adjacent, direction));
    }

    private static boolean hidesHalf(BlockState state, boolean top, BlockState adjacent, Direction direction) {
        BlockState slab = half(state, top);
        Block glass = ((GlassJoin.Shaped) slab.getBlock()).base();
        BlockState neighbour = GlassJoin.matchingPart(adjacent, glass);
        return neighbour != null && GlassJoin.covered(slab, neighbour, direction);
    }

    @Override protected void onExplosionHit(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion,
                                           BiConsumer<ItemStack, BlockPos> drops) {
        if (explosion.getBlockInteraction() == Explosion.BlockInteraction.TRIGGER_BLOCK) return;
        int hits = ((ExplosionHits) explosion).singulo$glassHits(pos);
        if (hits != TOP_HIT && hits != BOTTOM_HIT) {
            super.onExplosionHit(state, level, pos, explosion, drops);
            return;
        }
        BlockState broken = half(state, hits == TOP_HIT);
        if (broken.canDropFromExplosion(level, pos, explosion)) {
            var loot = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                    .withParameter(LootContextParams.TOOL, ItemStack.EMPTY)
                    .withOptionalParameter(LootContextParams.THIS_ENTITY, explosion.getDirectSourceEntity());
            if (explosion.getBlockInteraction() == Explosion.BlockInteraction.DESTROY_WITH_DECAY)
                loot.withParameter(LootContextParams.EXPLOSION_RADIUS, explosion.radius());
            broken.spawnAfterBreak(level, pos, ItemStack.EMPTY, explosion.getIndirectSourceEntity() instanceof Player);
            broken.getDrops(loot).forEach(stack -> drops.accept(stack, pos));
        }
        level.setBlockAndUpdate(pos, half(state, hits == BOTTOM_HIT));
    }
}
