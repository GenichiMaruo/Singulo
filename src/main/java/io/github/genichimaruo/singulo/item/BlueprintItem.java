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
    public net.minecraft.world.item.ItemStackTemplate getCraftingRemainder(net.minecraft.world.item.ItemInstance input) {
        if (input instanceof ItemStack stack) return net.minecraft.world.item.ItemStackTemplate.fromNonEmptyStack(stack).withCount(1);
        if (input instanceof net.minecraft.world.item.ItemStackTemplate template) return template.withCount(1);
        var patch = net.minecraft.core.component.DataComponentPatch.builder();
        for (var type : net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_TYPE) copyComponent(input, patch, type);
        return new net.minecraft.world.item.ItemStackTemplate(input.typeHolder(), 1, patch.build());
    }
    private static <T> void copyComponent(net.minecraft.world.item.ItemInstance input,
                                          net.minecraft.core.component.DataComponentPatch.Builder patch,
                                          net.minecraft.core.component.DataComponentType<T> type) {
        T value = input.get(type);
        if (value == null) patch.remove(type); else patch.set(type, value);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("item.singulo.blueprint.hint").withStyle(ChatFormatting.GRAY));
    }
}
