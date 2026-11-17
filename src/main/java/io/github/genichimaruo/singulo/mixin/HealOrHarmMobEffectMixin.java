package io.github.genichimaruo.singulo.mixin;

import io.github.genichimaruo.singulo.ruin.BossEffects;
import javax.annotation.Nullable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ボスには、負傷のポーションも即時回復のポーションも回復として効く（{@link BossEffects}）。
 * 投げたポーション・残留ポーションは applyInstantenousEffect を、矢やコマンドで付けた効果は applyEffectTick を通る。
 */
@Mixin(targets = "net.minecraft.world.effect.HealOrHarmMobEffect")
public abstract class HealOrHarmMobEffectMixin {
    @Inject(method = "applyEffectTick", at = @At("HEAD"), cancellable = true)
    private void singulo$bossHealsOnTick(LivingEntity entity, int amplifier, CallbackInfoReturnable<Boolean> ci) {
        if (BossEffects.isBoss(entity)) {
            entity.heal(BossEffects.healing(amplifier, 1));
            ci.setReturnValue(true);
        }
    }

    @Inject(method = "applyInstantenousEffect", at = @At("HEAD"), cancellable = true)
    private void singulo$bossHealsInstantly(@Nullable Entity source, @Nullable Entity indirectSource, LivingEntity entity,
            int amplifier, double proximity, CallbackInfo ci) {
        if (BossEffects.isBoss(entity)) {
            entity.heal(BossEffects.healing(amplifier, proximity));
            ci.cancel();
        }
    }
}
