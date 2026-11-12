package io.github.genichimaruo.singulo.storage;

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

    public HorizonVaultBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
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
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** Legacy block interaction combines held-item deposit and empty-hand extraction. */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof HorizonVaultBlockEntity vault)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            ItemStack stack = player.getItemInHand(hand);
            if (!stack.isEmpty()) {
                vault.insertFromPlayer(player, vault.accepts(stack) ? stack : ItemStack.EMPTY);
            } else if (player.isShiftKeyDown() || !vault.insertRemainingOnDoubleUse(player)) {
                vault.takeForPlayer(player, player.isShiftKeyDown());
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder params) {
        List<ItemStack> drops = super.getDrops(state, params);
        var be = params.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY);
        if (be instanceof HorizonVaultBlockEntity vault) {
            for (ItemStack stack : drops) {
                if (stack.is(state.getBlock().asItem())) vault.collectComponents(stack);
            }
        }
        return drops;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.getBlockEntity(pos) instanceof HorizonVaultBlockEntity vault) vault.applyComponentsFromItemStack(stack);
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof HorizonVaultBlockEntity vault ? vault.comparator() : 0;
    }

    /** 中身入りのアイテム: しまっている物と個数。 */
    @Override
    public void appendHoverText(ItemStack stack, @Nullable net.minecraft.world.level.BlockGetter context, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        var held = SinguloComponents.get(stack, SinguloComponents.VAULT_ITEM.get());
        ItemStack type = held == null ? null : held.copy();
        Long count = SinguloComponents.get(stack, SinguloComponents.VAULT_COUNT.get());
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
