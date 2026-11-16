package io.github.genichimaruo.singulo.android;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * 農業アップグレード（0.20.0）。護衛のアンドロイドの道具の枠に積むと、手順カードの畑づくり
 * （土を耕して種・苗を植える。骨粉なら育ちかけの作物に与える）ができるようになる。
 */
public class AndroidFarmingUpgradeItem extends Item {
    public AndroidFarmingUpgradeItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.singulo.android_farming_upgrade").withStyle(ChatFormatting.GRAY));
    }
}
