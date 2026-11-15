package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;

/** Actual GPU texels, not a duplicate implementation of lightmap color math. Development only. */
public final class ReviewLightmap {
    public static NativeImage read(GpuTextureView view) {
        if (!(view.texture() instanceof GlTexture texture)) throw new AssertionError("Review requires OpenGL texture readback");
        int w = texture.getWidth(0), h = texture.getHeight(0);
        int binding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        var pixels = MemoryUtil.memAlloc(w * h * 4);
        var image = new NativeImage(w, h, false);
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture.glId());
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                int i = (y * w + x) * 4;
                image.setPixel(x, y, ((pixels.get(i + 3) & 255) << 24) | ((pixels.get(i) & 255) << 16)
                        | ((pixels.get(i + 1) & 255) << 8) | (pixels.get(i + 2) & 255));
            }
            return image;
        } catch (Throwable failure) { image.close(); throw failure; }
        finally { GL11.glBindTexture(GL11.GL_TEXTURE_2D, binding); MemoryUtil.memFree(pixels); }
    }
}
