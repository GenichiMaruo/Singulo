package io.github.genichimaruo.singulo.android;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * アンドロイド用武装モジュール。護衛のアンドロイドの武装モジュールの枠に1つ積むと、すべての技に効く（{@link AndroidWeaponModules}）。
 *
 * <ul>
 * <li>出力増幅器（Mk1〜Mk3）: すべての技のダメージが上がる。剣のエンチャント（ダメージ増加・アンデッド特効・虫特効・火属性・
 *     ノックバック・ドロップ増加）を付けられ、アンドロイドの攻撃全体に効く。上位版は前の世代から作り、エンチャントを引き継ぐ。</li>
 * <li>特化モジュール（重撃・連撃・吸収・貫通・凍結・対番人）: 出力増幅器の代わりに積んで、戦い方を変える。エンチャントは付けられない。</li>
 * </ul>
 */
public class AndroidWeaponModuleItem extends Item {
    public enum Kind {
        AMP_1("android_weapon_module", 1.3F, 15, 0x78E1FF),
        AMP_2("android_weapon_module_2", 1.6F, 18, 0xB48CFF),
        AMP_3("android_weapon_module_3", 2.0F, 22, 0xFF9A4A),
        /** 大技のダメージ ×2.2（ほかの技はそのまま）。 */
        HEAVY("heavy_strike_module", 1, 0, 0xFF5A5A),
        /** 技の待ち時間 ×0.75（1回の威力は同じ）。 */
        RAPID("rapid_strike_module", 1, 0, 0x7CFF8A),
        /** 与えたダメージの1割だけ体力が戻る。 */
        DRAIN("drain_module", 1, 0, 0xD45CFF),
        /** 相手の防具の守りを4割無視する。 */
        PIERCE("piercing_module", 1, 0, 0xFFD24A),
        /** 当てた相手を2秒鈍らせる。 */
        FROST("frost_module", 1, 0, 0x9FE8FF),
        /** ボスと戦うようになり、ボス（番人・ウォーデンなど）へのダメージ ×1.8。 */
        WARDEN("warden_breaker_module", 1, 0, 0xFFFFFF);

        public final String id;
        /** すべての技にかかるダメージの倍率（出力増幅器だけ 1 より大きい）。 */
        public final float multiplier;
        /** エンチャントのしやすさ（0 はエンチャントを付けられない）。 */
        public final int enchantability;
        public final int color;

        Kind(String id, float multiplier, int enchantability, int color) {
            this.id = id;
            this.multiplier = multiplier;
            this.enchantability = enchantability;
            this.color = color;
        }

        public boolean amplifier() {
            return enchantability > 0;
        }
    }

    public static final float HEAVY_BIG = 2.2F, HEAVY_NORMAL = 1.0F;
    public static final float RAPID_PACE = 0.75F;
    public static final float DRAIN_SHARE = 0.1F;
    public static final float PIERCE_SHARE = 0.4F;
    public static final int FROST_TICKS = 40;
    public static final float WARDEN_BONUS = 1.8F;

    private final Kind kind;

    public AndroidWeaponModuleItem(Properties properties, Kind kind) {
        super(properties.stacksTo(1));
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    // 1.21 attaches amplifiers to the sword item tag; legacy enchanting uses this stack hook instead.
    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        return kind.amplifier() && enchantment.category == EnchantmentCategory.WEAPON;
    }

    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        return kind.amplifier() && EnchantmentHelper.getEnchantments(book).keySet().stream()
                .allMatch(enchantment -> canApplyAtEnchantingTable(stack, enchantment));
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return kind.amplifier();
    }

    @Override
    public int getEnchantmentValue() {
        return kind.enchantability;
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.level.Level context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.singulo." + kind.id).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.singulo.android_weapon_module.slot").withStyle(ChatFormatting.DARK_GRAY));
    }
}
