package io.github.genichimaruo.singulo.storage;

import com.mojang.serialization.MapCodec;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import io.github.genichimaruo.singulo.registry.SinguloComponents;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

/** 事象の地平線倉庫のブロック。正面（個数を出す台の向き）は置いた人のほう。使い方は {@link HorizonVaultBlockEntity}。 */
public class HorizonVaultBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final MapCodec<HorizonVaultBlock> CODEC = simpleCodec(HorizonVaultBlock::new);

    public HorizonVaultBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** 物を持って使う: 入れる（すばやく2回で、持ち物の同じ物を全部）。 */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof HorizonVaultBlockEntity vault)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        // しまえない物を持っていても、すばやく2回使えば持ち物の同じ物を全部入れられる（手の物は入れない）
        if (!level.isClientSide) {
            vault.insertFromPlayer(player, vault.accepts(stack) ? stack : ItemStack.EMPTY);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** 素手で使う: 1スタック取り出す（スニークなら1個）。 */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof HorizonVaultBlockEntity vault && !level.isClientSide) {
            vault.takeForPlayer(player, player.isShiftKeyDown());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof HorizonVaultBlockEntity vault ? vault.comparator() : 0;
    }

    /** 中身入りのアイテム: しまっている物と個数。 */
    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        var held = stack.get(SinguloComponents.VAULT_ITEM.get());
        ItemStack type = held == null ? null : held.copyOne();
        Long count = stack.get(SinguloComponents.VAULT_COUNT.get());
        if (type != null && !type.isEmpty() && count != null && count > 0) {
            lines.add(Component.translatable("tooltip.singulo.horizon_vault.contents", type.getHoverName(), String.format("%,d", count))
                    .withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(Component.translatable("tooltip.singulo.horizon_vault.capacity", String.format("%,d", HorizonVaultBlockEntity.CAPACITY))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HorizonVaultBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, SinguloBlockEntities.HORIZON_VAULT.get(), HorizonVaultBlockEntity::serverTick);
    }
}
