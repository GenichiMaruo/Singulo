package io.github.genichimaruo.singulo.mixin;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.util.ModelQuadUtil", remap = false)
public abstract class SodiumRayleighColorMixin {
    @Inject(method = "mergeBakedLight", at = @At("HEAD"), cancellable = true)
    private static void singulo$independentSky(int a, int b, CallbackInfoReturnable<Integer> ci) {
        if (((a | b) & 0xFF000000) == 0) return;
        // Comparing the signed upper 16 bits would discard coordinates >= 128 and
        // allow the artificial coordinate to overwrite a model's stronger natural light.
        ci.setReturnValue(Math.max(a & 0xFFFF, b & 0xFFFF)
                | Math.max(a >>> 16 & 255, b >>> 16 & 255) << 16
                | Math.max(a >>> 24, b >>> 24) << 24);
    }
}
