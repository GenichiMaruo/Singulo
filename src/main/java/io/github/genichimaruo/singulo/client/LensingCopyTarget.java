package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.renderer.RenderPipelines;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/** Screen-copy pyramid; each mip is generated through the backend-neutral render pass API. */
final class LensingCopyTarget extends RenderTarget {
    private final TextureFormat colorFormat;
    private final List<GpuTextureView> levels = new ArrayList<>();
    LensingCopyTarget(int width, int height, boolean stencil, TextureFormat colorFormat) {
        super("Singulo lens copy", true, stencil);
        this.colorFormat = colorFormat;
        resize(width, height);
    }
    @Override public void createBuffers(int width, int height) {
        super.createBuffers(width, height);
        colorTextureView.close(); colorTexture.close();
        int count = 32 - Integer.numberOfLeadingZeros(Math.max(width, height));
        var device = RenderSystem.getDevice();
        colorTexture = device.createTexture(() -> "Singulo lens color pyramid", 15, colorFormat, width, height, 1, count);
        colorTextureView = device.createTextureView(colorTexture);
        for (int i=0; i<count; i++) levels.add(device.createTextureView(colorTexture, i, 1));
    }
    void generateMipmaps() {
        for (int i=1; i<levels.size(); i++) {
            try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                    () -> "Singulo lens mip", levels.get(i), OptionalInt.empty())) {
                pass.setPipeline(RenderPipelines.TRACY_BLIT);
                pass.bindTexture("InSampler", levels.get(i-1), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
                pass.draw(0,3);
            }
        }
    }
    @Override public void destroyBuffers() {
        for (var level : levels) level.close();
        levels.clear();
        super.destroyBuffers();
    }
}
