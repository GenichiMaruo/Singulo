package io.github.genichimaruo.singulo.mixin;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.textures.GpuTexture;
import io.github.genichimaruo.singulo.client.PreviewLighting;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** DH 3.3 consumes a GPU lightmap, whose normalized UVs require vanilla's 16x16 view. */
@Pseudo
@Mixin(targets = "com.seibel.distanthorizons.common.wrappers.misc.LightMapWrapper", remap = false)
public abstract class DistantHorizonsLightmapMixin {
    @ModifyVariable(method = "setLightmapGpuTexture", at = @At("HEAD"), argsOnly = true)
    private GpuTexture singulo$naturalGpuLightmap(GpuTexture incoming) {
        var renderer = Minecraft.getInstance().gameRenderer;
        var current = renderer.levelLightmap().texture();
        // Preserve third-party/shader-pack-owned textures. Only substitute our own banks.
        if (incoming != current || current.getWidth(0) != 16 || current.getHeight(0) != 512) return incoming;
        return singulo$naturalTexture();
    }
    @ModifyVariable(method = "setLightmapId", at = @At("HEAD"), argsOnly = true)
    private int singulo$naturalGlLightmap(int incoming) {
        var current = Minecraft.getInstance().gameRenderer.levelLightmap().texture();
        if (!(current instanceof GlTexture gl) || incoming != gl.glId()
                || current.getWidth(0) != 16 || current.getHeight(0) != 512) return incoming;
        var natural = singulo$naturalTexture();
        return natural instanceof GlTexture glNatural ? glNatural.glId() : incoming;
    }
    @Unique private static GpuTexture singulo$naturalTexture() {
        GpuTexture[] natural = new GpuTexture[1];
        PreviewLighting.render(() -> natural[0] = Minecraft.getInstance().gameRenderer.levelLightmap().texture());
        return natural[0];
    }
}
