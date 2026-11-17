package io.github.genichimaruo.singulo.android;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * 釣りアップグレード（0.20.0）。護衛のアンドロイドの道具の枠に積むと、手順カードの釣りができるようになる。
 * 釣り竿のエンチャント（宝釣り・入れ食い）を付けられる（タグ minecraft:enchantable/fishing）。耐久値はない。
 */
public class AndroidFishingUpgradeItem extends io.github.genichimaruo.singulo.compat.LegacyItem {
    public AndroidFishingUpgradeItem(Properties properties) {
        super(properties.enchantable(10).stacksTo(1));
    }



    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.singulo.android_fishing_upgrade").withStyle(ChatFormatting.GRAY));
    }
}
