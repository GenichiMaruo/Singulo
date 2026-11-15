package io.github.genichimaruo.singulo.android;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * 採掘アップグレード。護衛のアンドロイドに積むと、採掘の作業ができるようになる（ダイヤのツルハシ相当の硬さ・速さ）。
 * ツルハシのエンチャント（効率強化・幸運・シルクタッチ）を付けられる。耐久値はないので、耐久力は付けられない。
 */
public class AndroidMiningUpgradeItem extends io.github.genichimaruo.singulo.compat.LegacyItem {
    public AndroidMiningUpgradeItem(Properties properties) {
        super(properties.enchantable(10).stacksTo(1));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.singulo.android_mining_upgrade").withStyle(ChatFormatting.GRAY));
    }
}
