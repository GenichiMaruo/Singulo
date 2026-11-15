package io.github.genichimaruo.singulo.mixin;
import io.github.genichimaruo.singulo.client.RayleighLighting;
import net.minecraft.util.LightCoordsUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Emission and max operations may alter natural/block light, but must not erase artificial sky. */
@Mixin(LightCoordsUtil.class)
public abstract class RayleighSmoothLightMixin {
    @Inject(method={"withBlock","lightCoordsWithEmission"},at=@At("RETURN"),cancellable=true)
    private static void singulo$preserve(int light,int emission,CallbackInfoReturnable<Integer> ci) {ci.setReturnValue(ci.getReturnValue()|(light&RayleighLighting.NOON));}
    @Inject(method="addSmoothBlockEmission",at=@At("RETURN"),cancellable=true)
    private static void singulo$smooth(int light,float emission,CallbackInfoReturnable<Integer> ci) {ci.setReturnValue(ci.getReturnValue()|(light&RayleighLighting.NOON));}
    @Inject(method="max",at=@At("RETURN"),cancellable=true)
    private static void singulo$max(int a,int b,CallbackInfoReturnable<Integer> ci) {ci.setReturnValue(ci.getReturnValue()|Math.max(a&RayleighLighting.NOON,b&RayleighLighting.NOON));}
}
