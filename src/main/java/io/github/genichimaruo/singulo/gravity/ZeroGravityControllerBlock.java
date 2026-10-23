package io.github.genichimaruo.singulo.gravity;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 無重力コントロールボックス（{@link ZeroGravityControllerBlockEntity}）。無重力ブロックに付けて使う。
 * 右クリックで無重力の高さを1ブロック上げ、スニークして右クリックで1ブロック下げる。高さと電力を知らせる。
 */
public class ZeroGravityControllerBlock extends BaseEntityBlock {
    public static final MapCodec<ZeroGravityControllerBlock> CODEC = simpleCodec(ZeroGravityControllerBlock::new);

    public ZeroGravityControllerBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ZeroGravityControllerBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ZeroGravityControllerBlockEntity be) {
            be.setHeight(player.isSecondaryUseActive() ? be.height() - 1
                    : be.height() == Integer.MAX_VALUE ? Integer.MAX_VALUE : be.height() + 1);
            player.displayClientMessage(Component.translatable("message.singulo.zero_gravity_controller.height", be.height(),
                    ZeroGravity.costPerBlock(be.height())), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        if (!level.isClientSide && !oldState.is(state.getBlock())) {
            ZeroGravityControllerBlockEntity.notifyReceivers(level, pos);
        }
    }

    /** 置いたら、同じ集まりのボックスの高さに合わせる（ブロックのデータを読んだあと）。 */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable net.minecraft.world.entity.LivingEntity placer,
                            net.minecraft.world.item.ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ZeroGravityControllerBlockEntity be) {
            be.adoptGroupHeight();
            ZeroGravityControllerBlockEntity.notifyReceivers(level, pos);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        boolean removed = !state.is(newState.getBlock());
        super.onRemove(state, level, pos, newState, moving);
        if (removed && !level.isClientSide) {
            ZeroGravityControllerBlockEntity.notifyReceivers(level, pos);
        }
    }
}
