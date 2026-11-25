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
        super(properties.stacksTo(1).attributes(modifiers(tier)));
        this.tier = tier;
    }

    private static ItemAttributeModifiers modifiers(Tier tier) {
        // 修飾子の ID は Mk1 のまま（どの段も胸当ての枠にしか入らない）
        var b = ItemAttributeModifiers.builder()
                .add(Attributes.ARMOR, new AttributeModifier(Singulo.id("android_armor_plate"), tier.armor, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.CHEST)
                .add(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(Singulo.id("android_armor_plate_toughness"), tier.toughness,
                        AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.CHEST);
        if (tier.knockback > 0) {
            b.add(Attributes.KNOCKBACK_RESISTANCE, new AttributeModifier(Singulo.id("android_armor_plate_knockback"), tier.knockback,
                    AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.CHEST);
        }
        return b.build();
    }

    public Tier tier() {
        return tier;
    }

    public static Tier tierOf(ItemStack stack) {
        return stack.getItem() instanceof AndroidArmorPlateItem p ? p.tier : Tier.MK1;
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
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.singulo.android_armor_plate" + tier.suffix).withStyle(ChatFormatting.GRAY));
    }
}
