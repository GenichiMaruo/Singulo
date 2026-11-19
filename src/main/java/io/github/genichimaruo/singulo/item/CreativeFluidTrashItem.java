package io.github.genichimaruo.singulo.item;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.fluids.capability.templates.VoidFluidHandler;

public final class CreativeFluidTrashItem extends SinguloBlockItem {
    public CreativeFluidTrashItem(Block block, Properties properties) {
        super(block, properties, 6);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        // Placing the bin against a tank must not let the tank fill the held bin and void its contents.
        if (context.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, context.getClickedPos(), context.getClickedFace()) != null) {
            return useOn(context);
        }
        return InteractionResult.PASS;
    }

    public static final class Handler extends VoidFluidHandler implements IFluidHandlerItem {
        private final ItemStack stack;

        public Handler(ItemStack stack) {
            this.stack = stack;
        }

        @Override
        public ItemStack getContainer() {
            return stack;
        }
    }
}
