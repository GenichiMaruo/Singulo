package io.github.genichimaruo.singulo.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** DH's normalized lightmap must contain only the standard natural-light bank. */
@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.common.wrappers.misc.LightMapWrapper_forge", remap = false)
public abstract class DistantHorizonsLightmapMixin {
    @Shadow public abstract void uploadLightmap(NativeImage image);

    @Inject(method = "uploadLightmap", at = @At("HEAD"), cancellable = true)
    private void singulo$naturalLightmap(NativeImage image, CallbackInfo ci) {
        if (image.getWidth() != 16 || image.getHeight() != 512) return;
        try (NativeImage natural = new NativeImage(16, 16, false)) {
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++)
                natural.setPixelRGBA(x, y, image.getPixelRGBA(x, y));
            // The 16x16 call passes the guard and runs DH's original upload and GL-state handling.
            uploadLightmap(natural);
        }
        ci.cancel();
    }
}
