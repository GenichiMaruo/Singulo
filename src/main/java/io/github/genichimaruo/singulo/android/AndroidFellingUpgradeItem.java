package io.github.genichimaruo.singulo.android;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * 伐採アップグレード。護衛のアンドロイドに積むと、伐採の作業ができるようになる（ダイヤの斧相当の速さ）。
 * 斧のエンチャント（効率強化・幸運・シルクタッチ）を付けられる。耐久値はないので、耐久力は付けられない。
 */
public class AndroidFellingUpgradeItem extends Item {
    public AndroidFellingUpgradeItem(Properties properties) {
        super(properties.stacksTo(1));
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
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.singulo.android_felling_upgrade").withStyle(ChatFormatting.GRAY));
    }
}
