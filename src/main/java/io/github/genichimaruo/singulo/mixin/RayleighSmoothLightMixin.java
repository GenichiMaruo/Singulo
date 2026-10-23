package io.github.genichimaruo.singulo.mixin;

import io.github.genichimaruo.singulo.client.RayleighLighting;
import net.minecraft.client.renderer.LightTexture;
import net.neoforged.neoforge.client.model.lighting.SmoothQuadLighter;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

/** NeoForge's alternative smooth lighter normalizes sky values to [0,1]; tag only its final result. */
@Mixin(SmoothQuadLighter.class)
public abstract class RayleighSmoothLightMixin {
    @Redirect(method = "computeLightingAt", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LightTexture;sky(I)I"))
    private int singulo$unpack(int packed) { return LightTexture.sky(packed & ~RayleighLighting.NOON); }
}
