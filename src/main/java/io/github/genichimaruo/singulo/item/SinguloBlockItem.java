package io.github.genichimaruo.singulo.item;

import io.github.genichimaruo.singulo.registry.SinguloComponents;
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
    public void appendHoverText(ItemStack stack, net.minecraft.world.level.Level context, List<Component> tooltip, TooltipFlag flag) {
        SinguloItem.addStageLine(tooltip, stage);
        if (Boolean.TRUE.equals(SinguloComponents.get(stack, io.github.genichimaruo.singulo.registry.SinguloComponents.MIXED_SOURCE.get()))) {
            tooltip.add(Component.translatable("tooltip.singulo.mixed_source")
                    .withStyle(net.minecraft.ChatFormatting.GREEN));
        }
    }
}
