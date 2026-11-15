package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.renderer.RenderPipelines;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/** Screen-copy pyramid built without sampling and rendering different views of one texture. */
final class LensingCopyTarget extends RenderTarget {
    private final TextureFormat colorFormat;
    private final List<GpuTextureView> levels = new ArrayList<>();
    private final List<GpuTexture> scratchTextures = new ArrayList<>();
    private final List<GpuTextureView> scratchViews = new ArrayList<>();
    LensingCopyTarget(int width, int height, boolean stencil, TextureFormat colorFormat) {
        super("Singulo lens copy", true, stencil);
        this.colorFormat = colorFormat;
        resize(width, height);
    }
    @Override public void createBuffers(int width, int height) {
        super.createBuffers(width, height);
        colorTextureView.close(); colorTexture.close();
        // The 26.1 OpenGL backend allocates width >> level / height >> level
        // without clamping to one. Stop before either dimension becomes zero.
        int count = LensingMipLayout.levels(width, height);
        var device = RenderSystem.getDevice();
        colorTexture = device.createTexture(() -> "Singulo lens color pyramid", 15, colorFormat, width, height, 1, count);
        colorTextureView = device.createTextureView(colorTexture);
        for (int i=0; i<count; i++) {
            levels.add(device.createTextureView(colorTexture, i, 1));
            if (i>0) {
                var texture = device.createTexture(() -> "Singulo lens mip scratch", 15, colorFormat,
                        Math.max(1,width >> i), Math.max(1,height >> i), 1, 1);
                scratchTextures.add(texture);
                scratchViews.add(device.createTextureView(texture));
            }
        }
    }
    void generateMipmaps() {
        // OpenGL views share a texture's BASE_LEVEL/MAX_LEVEL state. Sampling a
        // one-mip view while drawing into another mip makes its FBO incomplete.
        // Keep the pyramid's sampled view full-range; build independent scratch
        // levels and then draw each result into the corresponding pyramid mip.
        GpuTextureView previous = colorTextureView;
        for (int i=1; i<levels.size(); i++) {
            var scratch = scratchViews.get(i-1);
            blit(previous, scratch);
            blit(scratch, levels.get(i));
            previous = scratch;
        }
    }
    private static void blit(GpuTextureView source, GpuTextureView destination) {
        try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Singulo lens mip", destination, OptionalInt.empty())) {
            pass.setPipeline(RenderPipelines.TRACY_BLIT);
            pass.bindTexture("InSampler", source, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.draw(0,3);
        }
    }
    @Override public void destroyBuffers() {
        for (var view : scratchViews) view.close();
        scratchViews.clear();
        for (var texture : scratchTextures) texture.close();
        scratchTextures.clear();
        for (var level : levels) level.close();
        levels.clear();
        super.destroyBuffers();
    }
}
