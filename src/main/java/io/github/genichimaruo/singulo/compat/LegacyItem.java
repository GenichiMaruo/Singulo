package io.github.genichimaruo.singulo.compat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** Preserves existing tooltip composition while using the extracted tooltip API. */
public class LegacyItem extends Item {
    public LegacyItem(Properties properties) { super(properties); }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> output, TooltipFlag flag) {
        List<Component> lines = new ArrayList<>();
        appendHoverText(stack, context, lines, flag);
        lines.forEach(output);
    }

    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
    }

    @Override
    public void inventoryTick(ItemStack stack, net.minecraft.server.level.ServerLevel level,
                              net.minecraft.world.entity.Entity owner, net.minecraft.world.entity.EquipmentSlot equippedSlot) {
        boolean selected = owner instanceof net.minecraft.world.entity.LivingEntity living && living.getMainHandItem() == stack;
        inventoryTick(stack, level, owner, -1, selected);
    }
    public void inventoryTick(ItemStack stack, net.minecraft.world.level.Level level,
                              net.minecraft.world.entity.Entity owner, int inventorySlot, boolean selected) {}
}
