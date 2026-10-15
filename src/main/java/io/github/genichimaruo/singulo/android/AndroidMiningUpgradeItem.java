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
public class AndroidMiningUpgradeItem extends Item {
    public AndroidMiningUpgradeItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, net.minecraft.world.item.enchantment.Enchantment enchantment) {
        return enchantment == net.minecraft.world.item.enchantment.Enchantments.BLOCK_EFFICIENCY
                || enchantment == net.minecraft.world.item.enchantment.Enchantments.BLOCK_FORTUNE
                || enchantment == net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH;
    }

    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        return net.minecraft.world.item.enchantment.EnchantmentHelper.getEnchantments(book).keySet().stream()
                .allMatch(e -> canApplyAtEnchantingTable(stack, e));
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
        tooltip.add(Component.translatable("tooltip.singulo.android_mining_upgrade").withStyle(ChatFormatting.GRAY));
    }
}
