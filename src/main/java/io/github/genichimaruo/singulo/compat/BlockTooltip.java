package io.github.genichimaruo.singulo.compat;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
public interface BlockTooltip {
    void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> lines, TooltipFlag flag);
}
