package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.Singulo;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Review-only startup defaults; never included in the distribution. */
@EventBusSubscriber(modid = "singulo")
public final class ReviewEnvironment {
    private ReviewEnvironment() {}
    @SubscribeEvent
    public static void start(ServerStartedEvent event) {
        var server = event.getServer();
        var root = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        // Refuse to mutate any ordinary play world, even in a review-enabled client.
        if (!root.getFileName().toString().equals("Review")) return;
        server.overworld().getGameRules().set(GameRules.ADVANCE_WEATHER, false, server);
        server.setWeatherParameters(1000000, 0, false, false);
        // Weather-specific harnesses may deliberately set rain later; no per-tick override.
        Singulo.LOGGER.info("SINGULO_REVIEW_WEATHER clear advance_weather=false world=Review");
    }
}
