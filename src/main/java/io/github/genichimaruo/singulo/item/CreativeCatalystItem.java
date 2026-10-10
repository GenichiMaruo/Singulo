package io.github.genichimaruo.singulo.item;

import io.github.genichimaruo.singulo.registry.SinguloComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * 無限の触媒（クリエイティブ専用）。減らない触媒で、右クリックするとティア（2〜5）が切り替わる。
 * 触媒装置の検証用。加工装置のレシピの材料にはならない。
 */
public class CreativeCatalystItem extends UsesItem {
    public CreativeCatalystItem(Properties properties) {
        super(properties.stacksTo(1), 5, 1, true);
    }

    public static int tier(ItemStack stack) {
        return Math.max(2, Math.min(5, SinguloComponents.getOrDefault(stack, SinguloComponents.CATALYST_TIER.get(), 5)));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            int next = tier(stack) >= 5 ? 2 : tier(stack) + 1;
            SinguloComponents.set(stack, SinguloComponents.CATALYST_TIER.get(), next);
            player.displayClientMessage(Component.translatable("tooltip.singulo.creative_catalyst.tier", next), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.level.Level context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.singulo.creative_catalyst.tier", tier(stack)).withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("tooltip.singulo.creative_only").withStyle(ChatFormatting.DARK_PURPLE));
    }
}
