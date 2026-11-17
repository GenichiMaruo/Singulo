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
public class AndroidFishingUpgradeItem extends Item {
    public AndroidFishingUpgradeItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, net.minecraft.world.item.enchantment.Enchantment enchantment) {
        return enchantment == net.minecraft.world.item.enchantment.Enchantments.FISHING_SPEED
                || enchantment == net.minecraft.world.item.enchantment.Enchantments.FISHING_LUCK;
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return true;
    }

    @Override
    public int getEnchantmentValue() {
        return 10;
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.level.Level context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.singulo.android_fishing_upgrade").withStyle(ChatFormatting.GRAY));
    }
}
