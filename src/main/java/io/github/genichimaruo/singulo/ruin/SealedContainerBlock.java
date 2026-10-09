package io.github.genichimaruo.singulo.ruin;

import com.mojang.serialization.MapCodec;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 封印コンテナ（段階1〜4）。封印中は、その段階の鍵を持って使うと鍵を1本使って開く（解錠の演出のあと中身を取り出せる）。
 * 開いているときに鍵を持ってスニークして使うと、また封印する。
 * 封印中は壊しにくく（黒曜石より硬い）、無理に壊すと中身ごと失われる（何も落とさない）。開いていれば普通のチェストと同じ。
 */
public class SealedContainerBlock extends BaseEntityBlock {
    public static final MapCodec<SealedContainerBlock> CODEC = simpleCodec(p -> new SealedContainerBlock(p, 1));
    private static final VoxelShape SHAPE = Shapes.or(box(0, 0, 0, 16, 3, 16), box(1, 3, 1, 15, 15, 15));
    /** 封印中の壊しにくさ（黒曜石は50）。 */
    static final float SEALED_HARDNESS = 80;

    private final int tier;

    public SealedContainerBlock(Properties properties, int tier) {
        super(properties);
        this.tier = tier;
    }

    public int tier() {
        return tier;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SealedContainerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, SinguloBlockEntities.SEALED_CONTAINER.get(),
                level.isClientSide ? SealedContainerBlockEntity::clientTick : SealedContainerBlockEntity::serverTick);
    }

    /** 鍵を持って使う: 封印中なら開け、開いていてスニークしていれば封印する。 */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof SealedContainerBlockEntity box)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        boolean isKey = SealedContainerBlockEntity.isKey(stack, tier);
        if (box.phase() == SealedContainerBlockEntity.Phase.OPEN && !(isKey && player.isSecondaryUseActive())) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            box.interact(player, stack);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof SealedContainerBlockEntity box) {
            box.interact(player, ItemStack.EMPTY);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** 封印中はとても硬い。 */
    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof SealedContainerBlockEntity box && box.phase() != SealedContainerBlockEntity.Phase.OPEN) {
            float speed = player.getDigSpeed(state, pos);
            return speed / SEALED_HARDNESS / (player.hasCorrectToolForDrops(state) ? 30 : 100);
        }
        return super.getDestroyProgress(state, player, level, pos);
    }

    /** 封印中に壊されたら何も落とさない（中身ごと失われる）。 */
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        BlockEntity be = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (be instanceof SealedContainerBlockEntity box && box.phase() != SealedContainerBlockEntity.Phase.OPEN) {
            return List.of();
        }
        return super.getDrops(state, params);
    }

    /** 開いているときに壊したら、中身をまき散らす（チェストと同じ）。封印中は中身ごと消える。 */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof SealedContainerBlockEntity box) {
            if (box.phase() == SealedContainerBlockEntity.Phase.OPEN) {
                Containers.dropContents(level, pos, box);
            } else if (level instanceof ServerLevel server) {
                box.shatter(server);
            }
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    /** 封印中は、継ぎ目から細い光の粒がときどき漏れる。 */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof SealedContainerBlockEntity box && box.phase() == SealedContainerBlockEntity.Phase.SEALED
                && random.nextInt(6) == 0) {
            level.addParticle(net.minecraft.core.particles.ParticleTypes.END_ROD, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.9,
                    pos.getY() + 0.85, pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.9, 0, 0.01, 0);
        }
    }
}
