package io.github.genichimaruo.singulo.mixin;

import io.github.genichimaruo.singulo.client.RayleighLighting;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class RayleighEntityLightMixin {
    @Inject(method = "getPackedLightCoords", at = @At("RETURN"), cancellable = true)
    private void singulo$localNoon(Entity entity, float tick, CallbackInfoReturnable<Integer> ci) {
        ci.setReturnValue(RayleighLighting.pack(entity.level(), BlockPos.containing(entity.getLightProbePosition(tick)), ci.getReturnValue()));
    }
}
