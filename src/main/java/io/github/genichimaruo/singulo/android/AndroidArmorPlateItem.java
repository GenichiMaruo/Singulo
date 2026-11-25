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
 * アンドロイド用装甲板（Mk1〜Mk3）。護衛のアンドロイドの GUI に1枚積むと、防具（胸当て）として働く。
 * 防具のエンチャント（ダメージ軽減・爆発耐性・とげ など）を付けられる。プレイヤーは着られない。
 * 上位版は前の世代の装甲板から精密組立台で作り、エンチャント・名前・修理の費用を引き継ぐ（{@code MachineRecipe}）。
 */
public class AndroidArmorPlateItem extends Item {
    /** Mk1 の防御と防具強度。 */
    public static final double ARMOR = 8;
    public static final double TOUGHNESS = 2;

    /** 段ごとの性能（防御・防具強度・ノックバック耐性・エンチャントのしやすさ）と、アンドロイドの体を包むエネルギーシールドの色。 */
    public enum Tier {
        MK1(ARMOR, TOUGHNESS, 0, 15, 0xFFFFFF, ""),
        MK2(10, 4, 0.1, 18, 0xB48CFF, "_2"),
        MK3(12, 6, 0.25, 22, 0xFF9A4A, "_3");

        public final double armor, toughness, knockback;
        public final int enchantability;
        /** エネルギーシールドの光の色（Mk1 はシールドなし）。 */
        public final int tint;
        final String suffix;

        Tier(double armor, double toughness, double knockback, int enchantability, int tint, String suffix) {
            this.armor = armor;
            this.toughness = toughness;
            this.knockback = knockback;
            this.enchantability = enchantability;
            this.tint = tint;
            this.suffix = suffix;
        }
    }

    private final Tier tier;

    public AndroidArmorPlateItem(Properties properties) {
        this(properties, Tier.MK1);
    }

    public AndroidArmorPlateItem(Properties properties, Tier tier) {
        super(properties.stacksTo(1));
        this.tier = tier;
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        if (slot != EquipmentSlot.CHEST) return super.getDefaultAttributeModifiers(slot);
        return ImmutableMultimap.of(
                Attributes.ARMOR, new AttributeModifier(io.github.genichimaruo.singulo.compat.Legacy.uuid(Singulo.id("android_armor_plate")),
                        "Android armor plate", tier.armor, AttributeModifier.Operation.ADDITION),
                Attributes.ARMOR_TOUGHNESS, new AttributeModifier(io.github.genichimaruo.singulo.compat.Legacy.uuid(Singulo.id("android_armor_plate_toughness")),
                        "Android armor toughness", tier.toughness, AttributeModifier.Operation.ADDITION),
                Attributes.KNOCKBACK_RESISTANCE, new AttributeModifier(io.github.genichimaruo.singulo.compat.Legacy.uuid(Singulo.id("android_armor_plate_knockback")),
                        "Android armor knockback resistance", tier.knockback, AttributeModifier.Operation.ADDITION));
    }

    public static Tier tierOf(ItemStack stack) {
        return stack.getItem() instanceof AndroidArmorPlateItem plate ? plate.tier : Tier.MK1;
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return true;
    }

    @Override
    public int getEnchantmentValue() {
        return tier.enchantability;
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.level.Level context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.singulo.android_armor_plate" + tier.suffix).withStyle(ChatFormatting.GRAY));
    }
    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, net.minecraft.world.item.enchantment.Enchantment enchantment) {
        return enchantment.category == net.minecraft.world.item.enchantment.EnchantmentCategory.ARMOR
                || enchantment.category == net.minecraft.world.item.enchantment.EnchantmentCategory.ARMOR_CHEST
                || super.canApplyAtEnchantingTable(stack, enchantment);
    }
}
