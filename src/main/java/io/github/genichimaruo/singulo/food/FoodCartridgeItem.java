package io.github.genichimaruo.singulo.food;

import io.github.genichimaruo.singulo.item.SinguloItem;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * フードカートリッジ。フードプリンターの枠に入れて、1種類のペーストを {@link FoodPaste#CAPACITY} までためる。
 * 中身は物の情報に持ち、壊したプリンターから落ちてもそのまま。空のものどうしは重なる。
 */
public class FoodCartridgeItem extends SinguloItem {
    public FoodCartridgeItem(Properties properties, int stage) {
        super(properties.stacksTo(16), stage, false);
    }

    @Override
    public Component getName(ItemStack stack) {
        int kind = FoodPaste.kind(stack);
        return kind < 0 ? super.getName(stack) : Component.translatable("item.singulo.food_cartridge.filled", FoodPaste.name(kind));
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return FoodPaste.kind(stack) >= 0;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * FoodPaste.amount(stack) / FoodPaste.CAPACITY);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        int kind = FoodPaste.kind(stack);
        return kind < 0 ? 0xFFFFFF : FoodPaste.COLORS[kind];
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        int kind = FoodPaste.kind(stack);
        tooltip.add(kind < 0 ? Component.translatable("tooltip.singulo.food_cartridge.empty", FoodPaste.CAPACITY).withStyle(ChatFormatting.GRAY)
                : Component.translatable("tooltip.singulo.food_cartridge.amount", FoodPaste.name(kind), FoodPaste.amount(stack), FoodPaste.CAPACITY)
                        .withStyle(ChatFormatting.GRAY));
    }
}
