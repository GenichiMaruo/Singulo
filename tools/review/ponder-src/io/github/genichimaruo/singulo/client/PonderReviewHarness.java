package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.Singulo;
import java.nio.file.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent.ClientTickEvent;

/** Real Ponder scene capture; all dependencies accessed reflectively to keep Create optional. */
@Mod.EventBusSubscriber(modid = Singulo.MODID, value = Dist.CLIENT)
public final class PonderReviewHarness {
    private static int ticks, phase, wait;
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void verify(Minecraft mc) throws Exception {
        PonderReviewFixture.verify(mc);
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
        require(mc.level.getSkyDarken(1) == 1, "Preview reference is not clear noon");
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++)
            require(main.getPixelRGBA(x, 256 + y) == main.getPixelRGBA(x, y), "Noon bank mismatch at " + x + "," + y + ": " + Integer.toHexString(main.getPixelRGBA(x, 256 + y)) + " vs " + Integer.toHexString(main.getPixelRGBA(x, y)));
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
    @SubscribeEvent public static void tick(ClientTickEvent event) throws Exception {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (phase == 2) return;
        if (++ticks > 4800) throw new AssertionError("Ponder review timeout phase=" + phase);
        if (mc.level == null || mc.player == null || mc.getOverlay() != null) return;
        mc.level.setDayTime(6000); mc.level.getLevelData().setRaining(false);
        mc.level.setRainLevel(0); mc.level.setThunderLevel(0);
        if (phase == 0) {
            if (mc.getSingleplayerServer() == null || !PonderReviewFixture.prepare(mc)) return;
            var type = Class.forName("net.createmod.ponder.foundation.ui.PonderUI");
            mc.setScreen((Screen) type.getMethod("of", ResourceLocation.class).invoke(null, new ResourceLocation("create:mechanical_press")));
            phase = 1; return;
        }
        if (++wait < 160) return;
        verify(mc);
        Path output = Path.of(System.getProperty("singulo.ponderOutput")); Files.createDirectories(output);
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(output.resolve(phase == 1 ? "ponder.png" : "ponder-chute.png")); }
        Singulo.LOGGER.info("SINGULO_PONDER_CAPTURE_COMPLETE {}", phase == 1 ? "create:mechanical_press" : "create:chute");
        if (phase == 1) {
            phase = 3; wait = 0;
            var type = Class.forName("net.createmod.ponder.foundation.ui.PonderUI");
            mc.setScreen((Screen) type.getMethod("of", ResourceLocation.class).invoke(null, new ResourceLocation("create:chute")));
            return;
        }
        mc.stop(); phase = 2;
    }
}
