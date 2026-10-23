package io.github.genichimaruo.singulo.gravity;

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

    public ZeroGravityControllerBlock(Properties properties) {
        super(properties);
    }


    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ZeroGravityControllerBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ZeroGravityControllerBlockEntity be) {
            be.setHeight(player.isSecondaryUseActive() ? be.height() - 1
                    : be.height() == Integer.MAX_VALUE ? Integer.MAX_VALUE : be.height() + 1);
            player.displayClientMessage(Component.translatable("message.singulo.zero_gravity_controller.height", be.height(),
                    ZeroGravity.costPerBlock(be.height())), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
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
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        boolean removed = !state.is(newState.getBlock());
        super.onRemove(state, level, pos, newState, moving);
        if (removed && !level.isClientSide) {
            ZeroGravityControllerBlockEntity.notifyReceivers(level, pos);
        }
    }
    @Override
    public java.util.List<net.minecraft.world.item.ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder params) {
        var drops = super.getDrops(state, params);
        var blockEntity = params.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY);
        if (blockEntity instanceof ZeroGravityControllerBlockEntity box) {
            for (var stack : drops) {
                var tag = new net.minecraft.nbt.CompoundTag();
                tag.putInt("height", box.height());
                net.minecraft.world.item.BlockItem.setBlockEntityData(stack, box.getType(), tag);
            }
        }
        return drops;
    }
}
