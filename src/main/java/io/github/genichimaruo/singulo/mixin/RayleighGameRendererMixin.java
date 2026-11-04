package io.github.genichimaruo.singulo.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.genichimaruo.singulo.client.RayleighLightmapAccess;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A cancelled LightTexture update still returns to its caller; finish only the missing noon pass. */
@Mixin(GameRenderer.class)
public abstract class RayleighGameRendererMixin {
    @Shadow @Final private LightTexture lightTexture;
    @Inject(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LightTexture;updateLightTexture(F)V", shift = At.Shift.AFTER))
    private void singulo$finishNoon(float partialTick, long finishTime, PoseStack pose, CallbackInfo ci) {
        ((RayleighLightmapAccess) lightTexture).singulo$ensureNoon(partialTick);
    }
}
