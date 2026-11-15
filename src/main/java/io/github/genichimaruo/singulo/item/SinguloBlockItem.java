package io.github.genichimaruo.singulo.item;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

public class SinguloBlockItem extends BlockItem {
    private final int stage;

    public SinguloBlockItem(Block block, Properties properties, int stage) {
        super(block, properties);
        this.stage = stage;
    }

    @Override
    public boolean canFitInsideContainerItems(ItemStack stack) {
        return !stack.has(io.github.genichimaruo.singulo.registry.SinguloComponents.VAULT_COUNT.get())
                && super.canFitInsideContainerItems(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, net.minecraft.world.item.component.TooltipDisplay display,
                                java.util.function.Consumer<Component> output, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, output, flag);
        List<Component> tooltip = new java.util.ArrayList<>();
        appendHoverText(stack, context, tooltip, flag);
        tooltip.forEach(output);
    }
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (getBlock() instanceof io.github.genichimaruo.singulo.compat.BlockTooltip block) block.appendHoverText(stack, context, tooltip, flag);
        SinguloItem.addStageLine(tooltip, stage);
        if (stack.has(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA)
                && Integer.valueOf(1).equals(stack.get(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE))) {
            tooltip.add(Component.translatable("tooltip.singulo.packed_device").withStyle(net.minecraft.ChatFormatting.AQUA));
        }
        if (Boolean.TRUE.equals(stack.get(io.github.genichimaruo.singulo.registry.SinguloComponents.MIXED_SOURCE.get()))) {
            tooltip.add(Component.translatable("tooltip.singulo.mixed_source")
                    .withStyle(net.minecraft.ChatFormatting.GREEN));
        }
    }
}
