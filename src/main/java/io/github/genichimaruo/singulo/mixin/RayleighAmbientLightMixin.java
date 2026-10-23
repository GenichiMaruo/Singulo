package io.github.genichimaruo.singulo.mixin;

import io.github.genichimaruo.singulo.client.RayleighLighting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep the extra lightmap row bit through vanilla ambient-occlusion interpolation. */
@Mixin(targets = "net.minecraft.client.renderer.block.ModelBlockRenderer$AmbientOcclusionFace")
public abstract class RayleighAmbientLightMixin {
    @Inject(method = "blend(IIII)I", at = @At("HEAD"), cancellable = true)
    private void singulo$blend(int a, int b, int c, int d, CallbackInfoReturnable<Integer> ci) {
        if (((a | b | c | d) & RayleighLighting.NOON) == 0) return;
        ci.setReturnValue(RayleighLighting.blend(a == 0 ? d : a, b == 0 ? d : b, c == 0 ? d : c, d, .25F, .25F, .25F, .25F));
    }
    @Inject(method = "blend(IIIIFFFF)I", at = @At("HEAD"), cancellable = true)
    private void singulo$weighted(int a, int b, int c, int d, float wa, float wb, float wc, float wd, CallbackInfoReturnable<Integer> ci) {
        if (((a | b | c | d) & RayleighLighting.NOON) != 0) ci.setReturnValue(RayleighLighting.blend(a, b, c, d, wa, wb, wc, wd));
    }
}
