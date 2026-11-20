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
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.VoidingResourceHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.VoidFluidHandler;

public final class CreativeFluidTrashBlock extends Block {
    public static final ResourceHandler<FluidResource> SINK = new VoidingResourceHandler<>(FluidResource.EMPTY);
    public CreativeFluidTrashBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                               Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide()) {
            var access = net.neoforged.neoforge.transfer.access.ItemAccess.forStack(stack.copyWithCount(1));
            var fluids = access.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Fluid.ITEM);
            boolean canEmpty = fluids != null && java.util.stream.IntStream.range(0, fluids.size()).anyMatch(i -> fluids.getAmountAsLong(i) > 0);
            return canEmpty ? InteractionResult.SUCCESS : InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        return FluidUtil.interactWithFluidHandler(player, hand, pos, SINK, null)
                ? InteractionResult.CONSUME : InteractionResult.TRY_WITH_EMPTY_HAND;
    }
}
