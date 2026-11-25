package io.github.genichimaruo.singulo.android;

import io.github.genichimaruo.singulo.android.AndroidWeaponModuleItem.Kind;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

/**
 * 武装モジュールの効き目。技のダメージは型ごとの値を直接与えている（攻撃力の属性は使わない）ので、護衛のアンドロイドが与える
 * ダメージを1か所で受けて、積んでいるモジュールの効果をかける。モジュールはアンドロイドの利き手の装備に写してあるので、
 * ドロップ増加は通常の仕組みのまま効く。
 */
public final class AndroidWeaponModules {
    private AndroidWeaponModules() {}

    /** 重撃モジュールが大きく効く大技（技の状態）。 */
    private static final Set<Integer> BIG_MOVES = Set.of(
            BladeAndroid.STATE_SPIN, BladeAndroid.STATE_CAST,
            WhipAndroid.STATE_SLAM,
            SaberAndroid.STATE_THRUST, SaberAndroid.STATE_WAVE,
            AxeAndroid.STATE_SLAM, AxeAndroid.STATE_LEAP,
            GunnerAndroid.STATE_RAIL_CHARGE, GunnerAndroid.STATE_RAIL_FIRE,
            KatanaAndroid.STATE_IAI_FLASH, KatanaAndroid.STATE_IAI_ZANSHIN,
            SparkAndroid.STATE_TENRAI_STRIKE);

    public static boolean bigMove(int state) {
        return BIG_MOVES.contains(state);
    }

    /** 攻撃した護衛のアンドロイドと、そのモジュール（なければ null）。 */
    @Nullable
    private static RuinAndroid attacker(net.minecraft.world.damagesource.DamageSource source) {
        return source.getEntity() instanceof RuinAndroid a && a.isGuard() && a.weaponModule() != null ? a : null;
    }

    /** ダメージが決まる前: 倍率・剣のエンチャントのダメージ・防具の無視。 */
    public static void onIncoming(LivingHurtEvent event) {
        RuinAndroid a = attacker(event.getSource());
        if (a == null || !(a.level() instanceof ServerLevel level)) return;
        ItemStack stack = a.getMainHandItem();
        Kind kind = a.weaponModule();
        LivingEntity target = event.getEntity();
        float amount = event.getAmount() * modifier(kind, a.state(), target);
        if (kind.amplifier()) {
            amount = amount + EnchantmentHelper.getDamageBonus(stack, target.getMobType());
        }
        if (kind == Kind.PIERCE) {
            float armor = target.getArmorValue();
            float toughness = (float) target.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS);
            float reduced = net.minecraft.world.damagesource.CombatRules.getDamageAfterAbsorb(amount, armor, toughness);
            float desired = reduced + (amount - reduced) * AndroidWeaponModuleItem.PIERCE_SHARE;
            float low = amount, high = Math.max(amount, desired * 5.0F);
            for (int i = 0; i < 16; i++) {
                float mid = (low + high) * 0.5F;
                if (net.minecraft.world.damagesource.CombatRules.getDamageAfterAbsorb(mid, armor, toughness) < desired) low = mid;
                else high = mid;
            }
            amount = (low + high) * 0.5F;
        }
        event.setAmount(amount);
    }

    /** モジュールによるダメージの倍率（エンチャントの分は別）。 */
    public static float modifier(Kind kind, int state, LivingEntity target) {
        return switch (kind) {
            case AMP_1, AMP_2, AMP_3 -> kind.multiplier;
            case HEAVY -> bigMove(state) ? AndroidWeaponModuleItem.HEAVY_BIG : AndroidWeaponModuleItem.HEAVY_NORMAL;
            case WARDEN -> RuinAndroid.isBoss(target) ? AndroidWeaponModuleItem.WARDEN_BONUS : 1;
            default -> 1;
        };
    }

    /** ダメージが入ったあと: 火属性・ノックバック（出力増幅器のエンチャント）、吸収、凍結。 */
    public static void onDamaged(LivingDamageEvent event) {
        RuinAndroid a = attacker(event.getSource());
        if (a == null || !(a.level() instanceof ServerLevel level) || event.getAmount() <= 0) return;
        LivingEntity target = event.getEntity();
        Kind kind = a.weaponModule();
        switch (kind) {
            case AMP_1, AMP_2, AMP_3 -> {
                ItemStack stack = a.getMainHandItem();
                EnchantmentHelper.doPostDamageEffects(a, target);
                float knockback = EnchantmentHelper.getKnockbackBonus(a);
                if (knockback > 0) {
                    float yaw = a.getYRot() * Mth.DEG_TO_RAD;
                    target.knockback(knockback * 0.5, Mth.sin(yaw), -Mth.cos(yaw));
                }
            }
            case DRAIN -> a.heal(event.getAmount() * AndroidWeaponModuleItem.DRAIN_SHARE);
            case FROST -> target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, AndroidWeaponModuleItem.FROST_TICKS, 1), a);
            default -> {
            }
        }
    }
}
