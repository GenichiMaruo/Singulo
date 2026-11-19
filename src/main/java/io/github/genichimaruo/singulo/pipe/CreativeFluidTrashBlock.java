package io.github.genichimaruo.singulo.pipe;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.fluids.capability.templates.VoidFluidHandler;

public final class CreativeFluidTrashBlock extends Block {
    public CreativeFluidTrashBlock(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                 Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            boolean canEmpty = FluidUtil.getFluidHandler(stack.copyWithCount(1))
                    .filter(handler -> !handler.drain(Integer.MAX_VALUE, FluidAction.SIMULATE).isEmpty()).isPresent();
            return canEmpty ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        return FluidUtil.interactWithFluidHandler(player, hand, VoidFluidHandler.INSTANCE)
                ? InteractionResult.CONSUME : InteractionResult.PASS;
    }
}
