package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.Singulo;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** Fixed environmental fog for illumination-only comparisons in the disposable review world. */
@EventBusSubscriber(modid=Singulo.MODID,value=Dist.CLIENT)
public final class RayleighReviewEnvironment {
    static boolean isolateLighting = true;
    @SubscribeEvent public static void isolateLighting(ViewportEvent.RenderFog event) {
        if (System.getProperty("singulo.rayleighOutput") == null || !isolateLighting) return;
        // 26.1 rain starts fog at -160, tinting even nearby geometry. Keep the entire room
        // fog-free in every comparison phase without changing weather or any lighting input.
        // The final fifth image disables this fixture override and records actual rain fog.
        event.setNearPlaneDistance(32.0F);
        event.setFarPlaneDistance(1024.0F);
    }
}
