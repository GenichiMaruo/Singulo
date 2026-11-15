package io.github.genichimaruo.singulo.mixin;
import io.github.genichimaruo.singulo.client.RayleighLighting;
import net.minecraft.util.LightCoordsUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Retain vanilla natural interpolation exactly, adding a separate artificial component. */
@Mixin(LightCoordsUtil.class)
public abstract class RayleighAmbientLightMixin {
    @Inject(method="smoothBlend",at=@At("RETURN"),cancellable=true)
    private static void singulo$blend(int a,int b,int c,int d,CallbackInfoReturnable<Integer> ci) {
        int sun=RayleighLighting.blend(a,b,c,d,.25F,.25F,.25F,.25F)&RayleighLighting.NOON;
        ci.setReturnValue(ci.getReturnValue()|sun);
    }
    @Inject(method="smoothWeightedBlend",at=@At("RETURN"),cancellable=true)
    private static void singulo$weighted(int a,int b,int c,int d,float wa,float wb,float wc,float wd,CallbackInfoReturnable<Integer> ci) {
        ci.setReturnValue(ci.getReturnValue()|(RayleighLighting.blend(a,b,c,d,wa,wb,wc,wd)&RayleighLighting.NOON));
    }
}
