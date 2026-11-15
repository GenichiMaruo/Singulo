package io.github.genichimaruo.singulo.mixin;

import io.github.genichimaruo.singulo.client.RayleighLighting;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FluidRenderer.class)
public abstract class RayleighFluidLightMixin {
    @Inject(method = "getLightCoords", at = @At("RETURN"), cancellable = true)
    private void singulo$localNoon(BlockAndTintGetter view, BlockPos pos, CallbackInfoReturnable<Integer> ci) {
        var level = RayleighLighting.level(view);
        if (level == null) return;
        BlockPos sample = io.github.genichimaruo.singulo.ruin.lab.RayleighSky.light(level, pos) > io.github.genichimaruo.singulo.ruin.lab.RayleighSky.light(level, pos.above()) ? pos : pos.above();
        ci.setReturnValue(RayleighLighting.tag(view, sample, ci.getReturnValue()));
    }
}
