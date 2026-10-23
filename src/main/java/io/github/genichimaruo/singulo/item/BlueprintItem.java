package io.github.genichimaruo.singulo.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * 施設の建材の設計図。遺構・研究所の収納から見つかる。作業台で素材と一緒に並べると、その種類の建材（遺構パネル・照明・研究所の調度品など）を
 * 作れる。設計図は使ってもなくならない（作業台に残る）。作れるものは JEI・ハンドブックのレシピで見られる。
 */
public class BlueprintItem extends SinguloItem {
    public BlueprintItem(Properties properties, int stage) {
        super(properties.stacksTo(1), stage, false);
    }

    /** 作業台で使っても、設計図そのものが残る。 */
    @Override
    public boolean hasCraftingRemainingItem(ItemStack stack) {
        return true;
    }

    @Override
    public ItemStack getCraftingRemainingItem(ItemStack stack) {
        return stack.copyWithCount(1);
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.level.Level context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("item.singulo.blueprint.hint").withStyle(ChatFormatting.GRAY));
    }
}
