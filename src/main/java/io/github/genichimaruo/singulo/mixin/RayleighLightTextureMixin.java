package io.github.genichimaruo.singulo.mixin;

import com.mojang.blaze3d.textures.GpuTextureView;
import io.github.genichimaruo.singulo.client.RayleighLightmapBanks;
import io.github.genichimaruo.singulo.client.PreviewLighting;
import net.minecraft.client.renderer.Lightmap;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** Preserve vanilla GPU lightmap calculation and build independent artificial-sun banks. */
@Mixin(Lightmap.class)
public abstract class RayleighLightTextureMixin {
    @Shadow @Final private GpuTextureView textureView;
    @Unique private RayleighLightmapBanks singulo$banks;
    @Inject(method="render", at=@At("RETURN"))
    private void singulo$banks(LightmapRenderState state, CallbackInfo ci) {
        if (!state.needsUpdate || RayleighLightmapBanks.isNoonPass()) return;
        if (singulo$banks == null) singulo$banks = new RayleighLightmapBanks();
        singulo$banks.update(textureView, state);
    }
    @Inject(method="getTextureView", at=@At("RETURN"), cancellable=true)
    private void singulo$view(CallbackInfoReturnable<GpuTextureView> ci) {
        if (singulo$banks != null && !PreviewLighting.active()) ci.setReturnValue(singulo$banks.view());
    }
    @Inject(method="close", at=@At("HEAD"))
    private void singulo$close(CallbackInfo ci) { if (singulo$banks != null) { singulo$banks.close(); singulo$banks = null; } }
}
