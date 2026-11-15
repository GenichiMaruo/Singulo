package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.textures.GpuTextureView;
import io.github.genichimaruo.singulo.Singulo;

/** Inspect the real DH GPU wrapper, not a duplicate crop implementation. */
public final class DistantHorizonsReviewHarness {
    public static void verify(NativeImage source) throws Exception {
        Class<?> injectorType = Class.forName("com.seibel.distanthorizons.core.dependencyInjection.SingletonInjector");
        Object injector = injectorType.getField("INSTANCE").get(null);
        Object wrapper = injectorType.getMethod("get", Class.class).invoke(injector,
                Class.forName("com.seibel.distanthorizons.core.wrapperInterfaces.minecraft.IMinecraftRenderWrapper"));
        var lightmaps = (java.util.Map<?, ?>) wrapper.getClass().getField("lightmapByDimensionType").get(wrapper);
        if (lightmaps.isEmpty()) throw new AssertionError("DH has not received a lightmap");
        for (Object lightmap : lightmaps.values()) {
            Object textureWrapper = lightmap.getClass().getMethod("getTextureViewWrapper").invoke(lightmap);
            var view = (GpuTextureView) textureWrapper.getClass().getMethod("getTextureView").invoke(textureWrapper);
            if (view == null || view.getWidth(0) != 16 || view.getHeight(0) != 16)
                throw new AssertionError("DH received the expanded lightmap instead of vanilla 16x16");
            try (var actual = ReviewLightmap.read(view)) {
                for(int y=0;y<16;y++) for(int x=0;x<16;x++)
                    if(actual.getPixel(x,y) != source.getPixel(x,y))
                        throw new AssertionError("DH natural light differs at " + x + "," + y);
            }
        }
        Singulo.LOGGER.info("SINGULO_DH_LIGHTMAP_COMPLETE 256");
    }
}
