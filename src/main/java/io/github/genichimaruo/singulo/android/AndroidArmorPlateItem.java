package io.github.genichimaruo.singulo.android;

import io.github.genichimaruo.singulo.Singulo;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.world.entity.ai.attributes.Attribute;

/**
 * アンドロイド用装甲板。護衛のアンドロイドの GUI に1枚積むと、防具（胸当て）として働く（防御 +8、防具強度 +2）。
 * 防具のエンチャント（ダメージ軽減・爆発耐性・とげ など）を付けられる。プレイヤーは着られない。
 */
public class AndroidArmorPlateItem extends Item {
    public static final double ARMOR = 8;
    public static final double TOUGHNESS = 2;

    public AndroidArmorPlateItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        if (slot != EquipmentSlot.CHEST) return super.getDefaultAttributeModifiers(slot);
        return ImmutableMultimap.of(
                Attributes.ARMOR, new AttributeModifier(io.github.genichimaruo.singulo.compat.Legacy.uuid(Singulo.id("android_armor_plate")),
                        "Android armor plate", ARMOR, AttributeModifier.Operation.ADDITION),
                Attributes.ARMOR_TOUGHNESS, new AttributeModifier(io.github.genichimaruo.singulo.compat.Legacy.uuid(Singulo.id("android_armor_plate_toughness")),
                        "Android armor toughness", TOUGHNESS, AttributeModifier.Operation.ADDITION));
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return true;
    }

    @Override
    public int getEnchantmentValue() {
        return 15;
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.level.Level context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.singulo.android_armor_plate").withStyle(ChatFormatting.GRAY));
    }
    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, net.minecraft.world.item.enchantment.Enchantment enchantment) {
        return enchantment.category == net.minecraft.world.item.enchantment.EnchantmentCategory.ARMOR
                || enchantment.category == net.minecraft.world.item.enchantment.EnchantmentCategory.ARMOR_CHEST
                || super.canApplyAtEnchantingTable(stack, enchantment);
    }
}
