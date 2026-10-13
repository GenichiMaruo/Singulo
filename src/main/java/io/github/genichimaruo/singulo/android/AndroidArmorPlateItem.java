package io.github.genichimaruo.singulo.android;

import io.github.genichimaruo.singulo.Singulo;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * アンドロイド用装甲板。護衛のアンドロイドの GUI に1枚積むと、防具（胸当て）として働く（防御 +8、防具強度 +2）。
 * 防具のエンチャント（ダメージ軽減・爆発耐性・とげ など）を付けられる。プレイヤーは着られない。
 */
public class AndroidArmorPlateItem extends Item {
    public static final double ARMOR = 8;
    public static final double TOUGHNESS = 2;

    public AndroidArmorPlateItem(Properties properties) {
        super(properties.stacksTo(1).attributes(ItemAttributeModifiers.builder()
                .add(Attributes.ARMOR, new AttributeModifier(Singulo.id("android_armor_plate"), ARMOR, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.CHEST)
                .add(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(Singulo.id("android_armor_plate_toughness"), TOUGHNESS,
                        AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.CHEST)
                .build()));
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
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.singulo.android_armor_plate").withStyle(ChatFormatting.GRAY));
    }
}
