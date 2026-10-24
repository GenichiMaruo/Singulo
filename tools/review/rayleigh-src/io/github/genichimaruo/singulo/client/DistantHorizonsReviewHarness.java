package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.genichimaruo.singulo.Singulo;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;

/** Inspect the real DH-uploaded GPU lightmap, not a duplicate crop implementation. */
public final class DistantHorizonsReviewHarness {
    public static void verify(NativeImage source) throws Exception {
        Class<?> injectorType = Class.forName("com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector");
        Object injector = injectorType.getField("INSTANCE").get(null);
        Object wrapper = injectorType.getMethod("get", Class.class).invoke(injector,
                Class.forName("com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper"));
        var field = wrapper.getClass().getField("lightmapByDimensionType");
        var lightmaps = (java.util.Map<?, ?>) field.get(wrapper);
        if (lightmaps.isEmpty()) throw new AssertionError("DH has not received a lightmap");
        int binding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        var pixels = MemoryUtil.memAlloc(16 * 16 * 4);
        try {
            for (Object lightmap : lightmaps.values()) {
                int id = (int) lightmap.getClass().getMethod("getOpenGlId").invoke(lightmap);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
                if (GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH) != 16
                        || GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT) != 16)
                    throw new AssertionError("DH received the expanded lightmap instead of vanilla 16x16");
                GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
                for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                    int at = (y * 16 + x) * 4, actual = 0;
                    for (int b = 0; b < 4; b++) actual |= (pixels.get(at + b) & 255) << (8 * b);
                    if (actual != source.getPixelRGBA(x, y)) throw new AssertionError("DH natural light differs at " + x + "," + y);
                }
            }
        } finally { GL11.glBindTexture(GL11.GL_TEXTURE_2D, binding); MemoryUtil.memFree(pixels); }
        Singulo.LOGGER.info("SINGULO_DH_LIGHTMAP_COMPLETE 256");
    }
}
