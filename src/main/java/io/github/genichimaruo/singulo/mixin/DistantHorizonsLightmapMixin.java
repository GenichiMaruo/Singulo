package io.github.genichimaruo.singulo.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.platform.NativeImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** DH's standard LOD shaders sample a normalized 16x16 vanilla lightmap. */
@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.common.wrappers.misc.LightMapWrapper_neoforge", remap = false)
public abstract class DistantHorizonsLightmapMixin {
    @WrapMethod(method = "uploadLightmap")
    private void singulo$naturalLightmap(NativeImage image, Operation<Void> original) {
        if (image.getWidth() != 16 || image.getHeight() != 512) {
            original.call(image);
            return;
        }
        // Do not expose the artificial-light banks (or unused rows) to normalized DH UVs.
        // Leave the original image and shader-pack-owned lightmap IDs untouched.
        try (NativeImage natural = new NativeImage(16, 16, false)) {
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++)
                natural.setPixelRGBA(x, y, image.getPixelRGBA(x, y));
            original.call(natural);
        }
    }
}
