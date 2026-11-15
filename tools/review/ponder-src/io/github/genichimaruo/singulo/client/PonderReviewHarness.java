package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.Singulo;
import java.nio.file.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Real Ponder scene capture; all dependencies accessed reflectively to keep Create optional. */
@EventBusSubscriber(modid = Singulo.MODID, value = Dist.CLIENT)
public final class PonderReviewHarness {
    private static int ticks, phase, wait;
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void verify(Minecraft mc) throws Exception {
        var type = Class.forName("net.createmod.ponder.api.level.PonderLevel");
        var view = (net.minecraft.world.level.Level) type.getConstructor(net.minecraft.core.BlockPos.class, net.minecraft.world.level.Level.class)
                .newInstance(net.minecraft.core.BlockPos.ZERO, mc.level);
        require(view.getLightEngine() == mc.level.getLightEngine(), "Not a borrowed-engine preview");
        require(RayleighLighting.level(mc.level) == mc.level, "Real-world lighting disabled");
        require(RayleighLighting.level(view) == null, "Preview borrowed the real-world sunlight");
        int normal = net.minecraft.util.LightCoordsUtil.FULL_BRIGHT;
        require(RayleighLighting.pack(view, net.minecraft.core.BlockPos.ZERO, normal) == normal, "Preview packed-light changed");
        require(RayleighLighting.tag(view, net.minecraft.core.BlockPos.ZERO, normal) == normal, "Preview AO-light changed");
        var worldView = mc.gameRenderer.levelLightmap();
        require(worldView.texture().getHeight(0) == 512, "World noon banks removed");
        try (var main = ReviewLightmap.read(worldView)) {
            PreviewLighting.render(() -> {
                require(PreviewLighting.active(), "Preview scope missing");
                var natural = mc.gameRenderer.levelLightmap();
                require(natural.texture().getWidth(0) == 16 && natural.texture().getHeight(0) == 16, "Preview texture is not vanilla-sized");
                try (var image = ReviewLightmap.read(natural)) {
                    for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++)
                        require(image.getPixel(x,y) == main.getPixel(x,y), "Natural preview color differs");
                }
                PreviewLighting.render(() -> {
                    require(PreviewLighting.active(), "Nested preview lost scope");
                    require(mc.gameRenderer.levelLightmap() == natural, "Nested scope lost texture");
                });
                require(mc.gameRenderer.levelLightmap() == natural, "Nested scope changed outer texture");
            });
        }
        try { PreviewLighting.render(() -> { throw new IllegalArgumentException("scope cleanup probe"); }); }
        catch (IllegalArgumentException expected) { require(expected.getMessage().equals("scope cleanup probe"), "Unexpected scope error"); }
        require(!PreviewLighting.active() && mc.gameRenderer.levelLightmap() == worldView, "Preview state leaked");
        Singulo.LOGGER.info("SINGULO_PONDER_LIGHTING_COMPLETE borrowed-level 256-pixels nested-scope exception-cleanup");
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) throws Exception {
        var mc = Minecraft.getInstance();
        if (phase == 2) return;
        if (++ticks > 4800) throw new AssertionError("Ponder review timeout phase=" + phase);
        if (mc.level == null || mc.player == null || mc.getOverlay() != null) return;
        if (phase == 0) {
            var type = Class.forName("net.createmod.ponder.foundation.ui.PonderUI");
            mc.setScreen((Screen) type.getMethod("of", Identifier.class).invoke(null, Identifier.parse("create:mechanical_press")));
            phase = 1; return;
        }
        if (++wait < 160) return;
        verify(mc);
        Path output = Path.of(System.getProperty("singulo.ponderOutput")); Files.createDirectories(output);
        try (var image = ReviewScreenshot.take(mc.getMainRenderTarget())) { image.writeToFile(output.resolve("ponder.png")); }
        Singulo.LOGGER.info("SINGULO_PONDER_CAPTURE_COMPLETE");
        mc.stop(); phase = 2;
    }
}
