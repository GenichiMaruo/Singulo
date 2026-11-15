package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import java.util.concurrent.atomic.AtomicBoolean;
import org.lwjgl.opengl.GL11;

/** Actual GPU texels through the official fenced buffer readback, never a native client pointer. */
public final class ReviewLightmap {
    public static NativeImage read(GpuTextureView view) {
        RenderSystem.assertOnRenderThread();
        var texture = view.texture();
        if (texture.getFormat() != TextureFormat.RGBA8) throw new AssertionError("Expected RGBA8 lightmap");
        int w = texture.getWidth(0), h = texture.getHeight(0);
        var device = RenderSystem.getDevice();
        var encoder = device.createCommandEncoder();
        // Same MAP_READ | COPY_DST buffer and full-texture copy API used by Screenshot.
        try (var buffer = device.createBuffer(() -> "Review lightmap readback", 9, Math.multiplyExact((long) w * h, 4))) {
            var ready = new AtomicBoolean();
            // Other screenshot users may leave pack row length behind. Normalize and restore all
            // pixel-pack parameters while the official encoder binds its own bounded GPU buffer.
            int[] names = {GL11.GL_PACK_ALIGNMENT, GL11.GL_PACK_ROW_LENGTH, GL11.GL_PACK_SKIP_ROWS,
                    GL11.GL_PACK_SKIP_PIXELS, GL11.GL_PACK_SWAP_BYTES, GL11.GL_PACK_LSB_FIRST,
                    org.lwjgl.opengl.GL12.GL_PACK_IMAGE_HEIGHT, org.lwjgl.opengl.GL12.GL_PACK_SKIP_IMAGES};
            int packBuffer = GL11.glGetInteger(org.lwjgl.opengl.GL21.GL_PIXEL_PACK_BUFFER_BINDING);
            int[] previous = new int[names.length];
            for (int i = 0; i < names.length; i++) previous[i] = GL11.glGetInteger(names[i]);
            try {
                for (int name : names) GL11.glPixelStorei(name, name == GL11.GL_PACK_ALIGNMENT ? 1 : 0);
                encoder.copyTextureToBuffer(texture, buffer, 0L, () -> ready.set(true), 0);
            } finally {
                for (int i = 0; i < names.length; i++) GL11.glPixelStorei(names[i], previous[i]);
                com.mojang.blaze3d.opengl.GlStateManager._glBindBuffer(org.lwjgl.opengl.GL21.GL_PIXEL_PACK_BUFFER, packBuffer);
            }
            long deadline = System.nanoTime() + 10_000_000_000L;
            GL11.glFinish();
            while (!ready.get() && System.nanoTime() < deadline) {
                RenderSystem.executePendingTasks();
                if (!ready.get()) java.util.concurrent.locks.LockSupport.parkNanos(1_000_000L);
            }
            if (!ready.get()) throw new AssertionError("Timed out waiting for fenced lightmap readback");
            var image = new NativeImage(w, h, false);
            try (var mapped = encoder.mapBuffer(buffer, true, false)) {
                var pixels = mapped.data();
                for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                    int i = (y * w + x) * 4;
                    // Keep texture row order: lightmap banks are indexed by raw GPU y, unlike screenshots.
                    image.setPixel(x, y, ((pixels.get(i + 3) & 255) << 24) | ((pixels.get(i) & 255) << 16)
                            | ((pixels.get(i + 1) & 255) << 8) | (pixels.get(i + 2) & 255));
                }
                return image;
            } catch (Throwable failure) { image.close(); throw failure; }
        }
    }
}
