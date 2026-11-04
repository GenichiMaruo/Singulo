package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.Singulo;
import java.nio.file.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
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
        int normal = net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;
        require(RayleighLighting.pack(view, net.minecraft.core.BlockPos.ZERO, normal) == normal, "Preview packed-light changed");
        require(RayleighLighting.tag(view, net.minecraft.core.BlockPos.ZERO, normal) == normal, "Preview AO-light changed");
        var light = mc.gameRenderer.lightTexture();
        var field = light.getClass().getDeclaredField("singulo$preview"); field.setAccessible(true);
        var copy = (net.minecraft.client.renderer.texture.DynamicTexture) field.get(light);
        var image = copy.getPixels();
        require(image.getWidth() == 16 && image.getHeight() == 16, "Preview texture is not vanilla-sized");
        var mainField = light.getClass().getDeclaredField("lightTexture"); mainField.setAccessible(true);
        var main = ((net.minecraft.client.renderer.texture.DynamicTexture) mainField.get(light)).getPixels();
        require(main.getHeight() == 512, "World noon banks removed");
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++)
            require(image.getPixelRGBA(x,y) == main.getPixelRGBA(x,y), "Natural preview color differs");
        int previous = com.mojang.blaze3d.systems.RenderSystem.getShaderTexture(2);
        PreviewLighting.render(() -> {
            require(PreviewLighting.active(), "Preview scope missing");
            require(org.lwjgl.opengl.GL11.glGetTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER) == org.lwjgl.opengl.GL11.GL_LINEAR,
                    "Preview interpolation is not linear");
            require(org.lwjgl.opengl.GL11.glGetTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER) == org.lwjgl.opengl.GL11.GL_LINEAR,
                    "Preview magnification is not linear");
            require(com.mojang.blaze3d.systems.RenderSystem.getShaderTexture(2) == copy.getId(), "Preview not bound");
            PreviewLighting.render(() -> require(PreviewLighting.active(), "Nested preview lost scope"));
            require(com.mojang.blaze3d.systems.RenderSystem.getShaderTexture(2) == copy.getId(), "Nested scope lost texture");
        });
        try { PreviewLighting.render(() -> { throw new IllegalArgumentException("scope cleanup probe"); }); }
        catch (IllegalArgumentException expected) { require(expected.getMessage().equals("scope cleanup probe"), "Unexpected scope error"); }
        require(!PreviewLighting.active() && com.mojang.blaze3d.systems.RenderSystem.getShaderTexture(2) == previous, "Preview state leaked");
        Singulo.LOGGER.info("SINGULO_PONDER_LIGHTING_COMPLETE borrowed-level 256-pixels nested-scope exception-cleanup");
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) throws Exception {
        var mc = Minecraft.getInstance();
        if (phase == 2) return;
        if (++ticks > 4800) throw new AssertionError("Ponder review timeout phase=" + phase);
        if (mc.level == null || mc.player == null || mc.getOverlay() != null) return;
        if (phase == 0) {
            var type = Class.forName("net.createmod.ponder.foundation.ui.PonderUI");
            mc.setScreen((Screen) type.getMethod("of", ResourceLocation.class).invoke(null, ResourceLocation.parse("create:mechanical_press")));
            phase = 1; return;
        }
        if (++wait < 160) return;
        verify(mc);
        Path output = Path.of(System.getProperty("singulo.ponderOutput")); Files.createDirectories(output);
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(output.resolve("ponder.png")); }
        Singulo.LOGGER.info("SINGULO_PONDER_CAPTURE_COMPLETE");
        mc.stop(); phase = 2;
    }
}
