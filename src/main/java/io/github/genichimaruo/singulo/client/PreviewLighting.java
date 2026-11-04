package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;

/** A render-thread scope: virtual scenes use the vanilla-sized copy, never the world's noon banks. */
public final class PreviewLighting {
    private static int depth;
    private PreviewLighting() {}
    public static boolean active() { return depth != 0; }
    public static void render(Runnable draw) {
        RenderSystem.assertOnRenderThread();
        int previous = RenderSystem.getShaderTexture(2);
        depth++;
        try {
            Minecraft.getInstance().gameRenderer.lightTexture().turnOnLightLayer();
            draw.run();
        } finally {
            depth--;
            RenderSystem.setShaderTexture(2, previous);
        }
    }
}
