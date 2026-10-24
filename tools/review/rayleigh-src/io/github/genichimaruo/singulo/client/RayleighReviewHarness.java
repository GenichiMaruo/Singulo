package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.ruin.lab.*;
import java.nio.file.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent.ClientTickEvent;

/** Development-only: compare every actual lightmap pixel against the same world's clear-noon reference. */
@EventBusSubscriber(modid = Singulo.MODID, value = Dist.CLIENT)
public final class RayleighReviewHarness {
    private static final BlockPos SAMPLE = new BlockPos(3, 241, 3);
    private static int phase, ticks, wait;
    private static volatile boolean ready;
    private static int[] reference;
    private static Path output() { return Path.of(System.getProperty("singulo.rayleighOutput")); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void prepare(ServerLevel level) {
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, level.getServer());
        level.setDayTime(6000); level.setWeatherParameters(10000, 0, false, false);
        level.setRainLevel(0); level.setThunderLevel(0); level.updateSkyBrightness();
        for (int x = -1; x <= 7; x++) for (int z = -1; z <= 7; z++) for (int y = 240; y <= 247; y++) {
            level.setBlockAndUpdate(new BlockPos(x, y, z), y == 240 || ((x == -1 || x == 7 || z == -1 || z == 7) && y < 246)
                    ? Blocks.WHITE_CONCRETE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }
        var player = level.getServer().getPlayerList().getPlayers().get(0);
        player.teleportTo(3.5, 241, 6.3); player.setNoGravity(true);
        ready = true;
    }
    private static NativeImage lightmap(Minecraft mc) throws Exception {
        LightTexture texture = mc.gameRenderer.lightTexture();
        var flicker = LightTexture.class.getDeclaredField("blockLightRedFlicker"); flicker.setAccessible(true); flicker.setFloat(texture, 0);
        var dirty = LightTexture.class.getDeclaredField("updateLightTexture"); dirty.setAccessible(true); dirty.setBoolean(texture, true);
        texture.updateLightTexture(1);
        var field = LightTexture.class.getDeclaredField("lightPixels"); field.setAccessible(true); return (NativeImage) field.get(texture);
    }
    private static void capture(Minecraft mc, String name) throws Exception {
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) { image.writeToFile(output().resolve(name + ".png")); }
    }
    private static void roof(ServerLevel level, boolean on) {
        for (int x = 0; x <= 6; x++) for (int z = 0; z <= 6; z++) level.setBlockAndUpdate(new BlockPos(x, 246, z),
                SinguloBlocks.LAB_RAYLEIGH_PANEL.get().defaultBlockState().setValue(RayleighPanelBlock.LIT, on));
    }
    @SubscribeEvent
    public static void tick(ClientTickEvent event) throws Exception {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (++ticks > 20 * 240) throw new AssertionError("Rayleigh review timed out, phase=" + phase);
        if (mc.screen instanceof net.minecraft.client.gui.screens.BackupConfirmScreen) {
            for (var widget : mc.screen.children()) if (widget instanceof net.minecraft.client.gui.components.Button button
                    && button.getMessage().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents label
                    && label.getKey().equals("selectWorld.backupJoinSkipButton") && button.active) button.onPress();
            return;
        }
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null || mc.getOverlay() != null) return;
        if (phase == 0) {
            if (net.minecraftforge.fml.ModList.get().isLoaded("embeddium")) {
                Class.forName("io.github.genichimaruo.singulo.client.SodiumNoonReviewHarness").getMethod("verify").invoke(null);
            }
            Files.createDirectories(output()); phase = 1;
            mc.getSingleplayerServer().execute(() -> prepare(mc.getSingleplayerServer().overworld())); return;
        }
        if (!ready || phase == 5) return;
        // Directly setting server weather strengths can suppress vanilla's transition packet.
        // Apply the same prescribed review weather on the client so repeated runs start identically.
        mc.level.getLevelData().setRaining(phase != 1);
        mc.level.setRainLevel(phase == 1 ? 0 : 1);
        mc.level.setThunderLevel(phase == 1 ? 0 : 1);
        mc.setScreen(null); mc.options.hideGui = true;
        mc.player.setNoGravity(true); mc.player.setDeltaMovement(Vec3.ZERO);
        mc.player.setYRot(180); mc.player.setXRot(48);
        mc.options.fov().set(60);
        if (++wait < 150) return;
        NativeImage image = lightmap(mc);
        if (net.minecraftforge.fml.ModList.get().isLoaded("distanthorizons")) DistantHorizonsReviewHarness.verify(image);
        require(image.getWidth() == 16 && image.getHeight() == 512, "No separate local-noon lightmap rows");
        if (phase == 1) {
            require(mc.level.getSkyDarken(1) == 1, "Reference is not clear noon: " + mc.level.getSkyDarken(1));
            reference = new int[256];
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                reference[y * 16 + x] = image.getPixelRGBA(x, y);
                require(image.getPixelRGBA(x, 256 + y) == reference[y * 16 + x], "Contiguous noon ramp differs from reference");
                require(image.getPixelRGBA(x, y * 16) == reference[y * 16 + x], "Local-noon lightmap differs from vanilla noon at " + x + "," + y);
            }
            capture(mc, "01-clear-noon-reference");
            phase = 2; wait = 0; ready = false;
            mc.getSingleplayerServer().execute(() -> {
                var level = mc.getSingleplayerServer().overworld(); roof(level, true);
                level.setDayTime(18000); level.setWeatherParameters(0, 10000, true, true);
                level.setRainLevel(1); level.setThunderLevel(1); level.updateSkyBrightness(); ready = true;
            });
        } else if (phase == 2) {
            if (net.minecraftforge.fml.ModList.get().isLoaded("embeddium")) {
                Class.forName("io.github.genichimaruo.singulo.client.SodiumNoonReviewHarness")
                        .getMethod("verifyEmissive", RayleighSky.LightSnapshot.class).invoke(null, RayleighSky.snapshot(mc.level));
            }
            require(mc.level.getDayTime() == 18000, "Time update not received");
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) require(image.getPixelRGBA(x, y * 16) == reference[y * 16 + x], "Night/weather changed local-noon colors");
            require(image.getPixelRGBA(0, 15) != reference[240], "Outside world also became noon");
            require(mc.level.getBrightness(LightLayer.SKY, SAMPLE) == 15 && mc.level.getRawBrightness(SAMPLE, 11) == 15, "Client has no actual sky/noon light");
            require((net.minecraft.client.renderer.LevelRenderer.getLightColor(mc.level, SAMPLE) & RayleighLighting.NOON) != 0, "Terrain/BE/particle light does not select local-noon colors");
            var state = Blocks.WHITE_CONCRETE.defaultBlockState();
            var model = mc.getBlockRenderer().getBlockModel(state);
            var quads = model.getQuads(state, net.minecraft.core.Direction.UP, net.minecraft.util.RandomSource.create());
            var lighter = new net.minecraftforge.client.model.lighting.SmoothQuadLighter(mc.getBlockColors());
            lighter.setup(mc.level, SAMPLE.below(), state);
            var buffer = new com.mojang.blaze3d.vertex.BufferBuilder(256);
            buffer.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, com.mojang.blaze3d.vertex.DefaultVertexFormat.BLOCK);
            lighter.process(buffer, new com.mojang.blaze3d.vertex.PoseStack().last(), quads.get(0), net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
            var lightField = net.minecraftforge.client.model.lighting.QuadLighter.class.getDeclaredField("lightmap");
            lightField.setAccessible(true);
            for (int packed : (int[]) lightField.get(lighter)) require((packed & RayleighLighting.NOON) != 0, "Smooth light pipeline dropped local noon");
            buffer.end().release();
            lighter.reset();
            var zombie = net.minecraft.world.entity.EntityType.ZOMBIE.create(mc.level);
            zombie.moveTo(SAMPLE.getX() + .5, SAMPLE.getY(), SAMPLE.getZ() + .5, 0, 0);
            int entityLight = mc.getEntityRenderDispatcher().getRenderer(zombie).getPackedLightCoords(zombie, 1);
            require((entityLight >>> 24 & 15) == 15, "Entity renderer does not receive full artificial sunlight");
            zombie.discard();
            capture(mc, "02-powered-rainy-night");
            phase = 3; wait = 0; ready = false;
            mc.getSingleplayerServer().execute(() -> { roof(mc.getSingleplayerServer().overworld(), false); ready = true; });
        } else if (phase == 3) {
            require(RayleighSky.light(mc.level, SAMPLE) == 0 && mc.level.getBrightness(LightLayer.SKY, SAMPLE) == 0, "Switching off leaves ghost sunlight");
            require((net.minecraft.client.renderer.LevelRenderer.getLightColor(mc.level, SAMPLE) & RayleighLighting.NOON) == 0, "Switched-off panel retains local-noon rendering");
            capture(mc, "03-unpowered-rainy-night");
            phase = 4; wait = 0; ready = false;
            mc.getSingleplayerServer().execute(() -> { roof(mc.getSingleplayerServer().overworld(), true); ready = true; });
        } else {
            require(mc.level.getBrightness(LightLayer.SKY, SAMPLE) == 15, "Re-enabling panel did not relight client");
            capture(mc, "04-repowered-rainy-night");
            Files.writeString(output().resolve("done.txt"), "256/256 artificial lightmap pixels equal vanilla clear noon\n256/256 pixels remain identical during rainy midnight\noutside lightmap retains night/weather\nclient SKY=15 / raw darkened=15\npower off SKY=0, no stale noon marker\npower on SKY=15 restored\nNeoForge smooth light pipeline and entity renderer retain local noon\n4 terrain screenshots\n");
            Singulo.LOGGER.info("SINGULO_RAYLEIGH_COMPLETE 256 4"); phase = 5; mc.stop();
        }
    }
}
