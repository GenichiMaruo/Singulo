package io.github.genichimaruo.singulo.client;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.ruin.lab.*;
import java.nio.file.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.Lightmap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Development-only: compare every actual lightmap pixel against the same world's clear-noon reference. */
@EventBusSubscriber(modid = Singulo.MODID, value = Dist.CLIENT)
public final class RayleighReviewHarness {
    private static final BlockPos SAMPLE = new BlockPos(3, 241, 3);
    private static int phase, ticks, wait, settledTicks;
    private static volatile boolean ready;
    private static int[] reference;
    private static Path output() { return Path.of(System.getProperty("singulo.rayleighOutput")); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void prepare(ServerLevel level) {
        level.getGameRules().set(GameRules.ADVANCE_TIME, false, level.getServer());
        level.getGameRules().set(GameRules.ADVANCE_WEATHER, false, level.getServer());
        level.getServer().clockManager().setTotalTicks(level.dimensionType().defaultClock().orElseThrow(), 6000); level.getServer().setWeatherParameters(10000, 0, false, false);
        level.setRainLevel(0); level.setThunderLevel(0); level.updateSkyBrightness();
        // Rejoin review intentionally saves a 16x16 roof in this same disposable Review world.
        // Remove those owned panels before rebuilding the smaller lighting fixture; otherwise
        // the emissive probe at (10,241,10) incorrectly remains inside the old sunlight field.
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            BlockPos oldPanel = new BlockPos(x, 246, z);
            if (level.getBlockState(oldPanel).is(SinguloBlocks.LAB_RAYLEIGH_PANEL.get()))
                level.setBlockAndUpdate(oldPanel, Blocks.AIR.defaultBlockState());
        }
        for (int x = -1; x <= 7; x++) for (int z = -1; z <= 7; z++) for (int y = 240; y <= 247; y++) {
            level.setBlockAndUpdate(new BlockPos(x, y, z), y == 240 || ((x == -1 || x == 7 || z == -1 || z == 7) && y < 246)
                    ? Blocks.WHITE_CONCRETE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }
        var player = level.getServer().getPlayerList().getPlayers().getFirst();
        player.teleportTo(3.5, 241, 6.3); player.setNoGravity(true);
        ready = true;
    }
    private static NativeImage lightmap(Minecraft mc) throws Exception {
        var renderer = mc.gameRenderer;
        var extractorField = renderer.getClass().getDeclaredField("lightmapRenderStateExtractor");
        extractorField.setAccessible(true);
        var extractor = (net.minecraft.client.renderer.LightmapRenderStateExtractor) extractorField.get(renderer);
        var flicker = extractor.getClass().getDeclaredField("blockLightFlicker"); flicker.setAccessible(true); flicker.setFloat(extractor, 0);
        var dirty = extractor.getClass().getDeclaredField("needsUpdate"); dirty.setAccessible(true); dirty.setBoolean(extractor, true);
        var state = new net.minecraft.client.renderer.state.LightmapRenderState();
        extractor.extract(state, 1);
        var lightField = renderer.getClass().getDeclaredField("lightmap"); lightField.setAccessible(true);
        ((Lightmap) lightField.get(renderer)).render(state);
        return ReviewLightmap.read(renderer.levelLightmap());
    }
    private static void capture(Minecraft mc, String name) throws Exception {
        try (var image = ReviewScreenshot.take(mc.getMainRenderTarget())) { image.writeToFile(output().resolve(name + ".png")); }
    }
    private static void roof(ServerLevel level, boolean on) {
        for (int x = 0; x <= 6; x++) for (int z = 0; z <= 6; z++) level.setBlockAndUpdate(new BlockPos(x, 246, z),
                SinguloBlocks.LAB_RAYLEIGH_PANEL.get().defaultBlockState().setValue(RayleighPanelBlock.LIT, on));
    }
    private static boolean fixtureUpdatesSettled(Minecraft mc) throws Exception {
        if (phase < 2 || phase > 4) return true;
        int matching = 0;
        for (int x = 0; x <= 6; x++) for (int z = 0; z <= 6; z++) {
            var state = mc.level.getBlockState(new BlockPos(x, 246, z));
            if (state.is(SinguloBlocks.LAB_RAYLEIGH_PANEL.get()) && state.getValue(RayleighPanelBlock.LIT) == (phase != 3)) matching++;
        }
        var field = net.minecraft.client.multiplayer.ClientLevel.class.getDeclaredField("lightUpdateQueue");
        field.setAccessible(true);
        int queued = ((java.util.Deque<?>) field.get(mc.level)).size();
        boolean work = mc.level.getLightEngine().hasLightWork();
        // Observe packet/lighting completion, not the expected light result. A stable but
        // incorrect sunlight field must still fail the original assertions below.
        if (matching != 49 || queued != 0 || work) {
            settledTicks = 0;
            if (wait % 20 == 0) Singulo.LOGGER.info("RAYLEIGH_FIXTURE_PENDING phase={} roof={}/49 queued={} work={}", phase, matching, queued, work);
            return false;
        }
        return ++settledTicks >= 20;
    }
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) throws Exception {
        Minecraft mc = Minecraft.getInstance();
        if (++ticks > 20 * 240) throw new AssertionError("Rayleigh review timed out, phase=" + phase);
        if (mc.screen instanceof net.minecraft.client.gui.screens.BackupConfirmScreen) {
            for (var widget : mc.screen.children()) if (widget instanceof net.minecraft.client.gui.components.Button button
                    && button.getMessage().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents label
                    && label.getKey().equals("selectWorld.backupJoinSkipButton") && button.active) button.onPress(new net.minecraft.client.input.InputWithModifiers() { public int input() { return 257; } public int modifiers() { return 0; } });
            return;
        }
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null || mc.getOverlay() != null) return;
        if (phase == 0) {
            if (net.neoforged.fml.ModList.get().isLoaded("sodium")) {
                Class.forName("io.github.genichimaruo.singulo.client.SodiumNoonReviewHarness").getMethod("verify").invoke(null);
            }
            Files.createDirectories(output()); phase = 1;
            mc.getSingleplayerServer().execute(() -> prepare(mc.getSingleplayerServer().overworld())); return;
        }
        if (!ready || phase == 6) return;
        // Directly setting server weather strengths can suppress vanilla's transition packet.
        // Apply the same prescribed review weather on the client so repeated runs start identically.
        // Rain and thunder strengths below are the client-side weather inputs.
        mc.level.setRainLevel(phase == 1 ? 0 : 1);
        mc.level.setThunderLevel(phase == 1 ? 0 : 1);
        mc.setScreen(null); mc.options.hideGui = true;
        mc.player.setNoGravity(true); mc.player.setDeltaMovement(Vec3.ZERO);
        mc.player.setYRot(180); mc.player.setXRot(48);
        mc.options.fov().set(60);
        if (++wait < 150) return;
        if (!fixtureUpdatesSettled(mc)) return;
        if (!net.neoforged.fml.ModList.get().isLoaded("sodium")) {
            var vf=mc.levelRenderer.getClass().getDeclaredField("viewArea");vf.setAccessible(true);
            var area=vf.get(mc.levelRenderer);
            if(area==null)return;
            var get=net.minecraft.client.renderer.ViewArea.class.getDeclaredMethod("getRenderSectionAt",BlockPos.class);get.setAccessible(true);
            var section=(net.minecraft.client.renderer.chunk.SectionRenderDispatcher.RenderSection)get.invoke(area,SAMPLE.below());
            if(section==null||!mc.levelRenderer.isSectionCompiledAndVisible(SAMPLE.below()))return;
            float visibility=section.getVisibility(net.minecraft.util.Util.getMillis());
            Singulo.LOGGER.info("RAYLEIGH_CAPTURE_VISIBILITY phase={} value={}",phase,visibility);
            if(visibility<1.0F)return;
        }
        try (NativeImage image = lightmap(mc)) {
        if (phase <= 4 && net.neoforged.fml.ModList.get().isLoaded("distanthorizons")) DistantHorizonsReviewHarness.verify(image);
        require(image.getWidth() == 16 && image.getHeight() == 512, "No separate local-noon lightmap rows");
        if (phase == 1) {
            require(mc.level.environmentAttributes().getValue(net.minecraft.world.attribute.EnvironmentAttributes.SKY_LIGHT_FACTOR, SAMPLE) == 1, "Reference is not clear noon: " + mc.level.environmentAttributes().getValue(net.minecraft.world.attribute.EnvironmentAttributes.SKY_LIGHT_FACTOR, SAMPLE));
            reference = new int[256];
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                reference[y * 16 + x] = image.getPixel(x, y);
                require(image.getPixel(x, 256 + y) == reference[y * 16 + x], "Contiguous noon ramp differs from reference at " + x + "," + y + " actual=" + Integer.toHexString(image.getPixel(x, 256 + y)) + " reference=" + Integer.toHexString(reference[y * 16 + x]));
                require(image.getPixel(x, y * 16) == reference[y * 16 + x], "Local-noon lightmap differs from vanilla noon at " + x + "," + y);
            }
            capture(mc, "01-clear-noon-reference");
            phase = 2; wait = 0; settledTicks = 0; ready = false;
            mc.getSingleplayerServer().execute(() -> {
                var level = mc.getSingleplayerServer().overworld(); roof(level, true);
                level.getServer().clockManager().setTotalTicks(level.dimensionType().defaultClock().orElseThrow(), 18000); level.getServer().setWeatherParameters(0, 10000, true, true);
                level.setRainLevel(1); level.setThunderLevel(1); level.updateSkyBrightness(); ready = true;
            });
        } else if (phase == 2) {
            if (net.neoforged.fml.ModList.get().isLoaded("sodium")) {
                Class.forName("io.github.genichimaruo.singulo.client.SodiumNoonReviewHarness")
                        .getMethod("verifyEmissive", RayleighSky.LightSnapshot.class).invoke(null, RayleighSky.snapshot(mc.level));
            }
            require(mc.level.getDefaultClockTime() == 18000, "Time update not received");
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) require(image.getPixel(x, y * 16) == reference[y * 16 + x], "Night/weather changed local-noon colors");
            require(image.getPixel(0, 15) != reference[240], "Outside world also became noon");
            require(mc.level.getBrightness(LightLayer.SKY, SAMPLE) == 15 && mc.level.getRawBrightness(SAMPLE, 11) == 15, "Client has no actual sky/noon light");
            require((net.minecraft.client.renderer.LevelRenderer.getLightCoords(mc.level, SAMPLE) & RayleighLighting.NOON) != 0, "Terrain/BE/particle light does not select local-noon colors");
            var state = Blocks.WHITE_CONCRETE.defaultBlockState();
            var model = mc.getModelManager().getBlockStateModelSet().get(state);
            var parts = new java.util.ArrayList<net.minecraft.client.renderer.block.dispatch.BlockStateModelPart>();
            model.collectParts(mc.level, SAMPLE.below(), state, net.minecraft.util.RandomSource.create(), parts);
            var quads = parts.stream().flatMap(part -> part.getQuads(net.minecraft.core.Direction.UP).stream()).toList();
            require(!quads.isEmpty(), "Concrete review model has no upward quads");
            var lighter = new net.neoforged.neoforge.client.model.ao.EnhancedBlockModelLighter();
            // NeoForge reset() begins a block and supplies the shared light cache to FullFaceCalculator.
            lighter.reset();
            var outputQuad = new com.mojang.blaze3d.vertex.QuadInstance();
            lighter.prepareQuadAmbientOcclusion(mc.level, state, SAMPLE.below(), quads.getFirst(), outputQuad);
            for (int i = 0; i < 4; i++) require((outputQuad.getLightCoords(i) & RayleighLighting.NOON) != 0, "NeoForge smooth light pipeline dropped local noon");
            lighter.reset();
            var zombie = net.minecraft.world.entity.EntityType.ZOMBIE.create(mc.level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            zombie.snapTo(SAMPLE.getX() + .5, SAMPLE.getY(), SAMPLE.getZ() + .5, 0, 0);
            int entityLight = mc.getEntityRenderDispatcher().getRenderer(zombie).getPackedLightCoords(zombie, 1);
            require((entityLight >>> 24 & 15) == 15, "Entity renderer does not receive full artificial sunlight");
            zombie.discard();
            capture(mc, "02-powered-rainy-night");
            phase = 3; wait = 0; settledTicks = 0; ready = false;
            mc.getSingleplayerServer().execute(() -> {
                var level = mc.getSingleplayerServer().overworld(); roof(level, false);
                require(RayleighSky.light(level, SAMPLE) == 0, "Server retains artificial sunlight after roof power removal");
                ready = true;
            });
        } else if (phase == 3) {
            require(RayleighSky.light(mc.level, SAMPLE) == 0 && mc.level.getBrightness(LightLayer.SKY, SAMPLE) == 0, "Switching off leaves ghost sunlight: artificial=" + RayleighSky.light(mc.level, SAMPLE) + " sky=" + mc.level.getBrightness(LightLayer.SKY, SAMPLE) + " stableTicks=" + settledTicks);
            require((net.minecraft.client.renderer.LevelRenderer.getLightCoords(mc.level, SAMPLE) & RayleighLighting.NOON) == 0, "Switched-off panel retains local-noon rendering");
            capture(mc, "03-unpowered-rainy-night");
            phase = 4; wait = 0; settledTicks = 0; ready = false;
            mc.getSingleplayerServer().execute(() -> { roof(mc.getSingleplayerServer().overworld(), true); ready = true; });
        } else if (phase == 4) {
            require(mc.level.getBrightness(LightLayer.SKY, SAMPLE) == 15, "Re-enabling panel did not relight client");
            capture(mc, "04-repowered-rainy-night");
            RayleighReviewEnvironment.isolateLighting = false;
            phase = 5; wait = 0;
        } else {
            capture(mc, "05-powered-natural-rain-fog");
            Files.writeString(output().resolve("done.txt"), "256/256 artificial lightmap pixels equal vanilla clear noon\n256/256 pixels remain identical during rainy midnight\noutside lightmap retains night/weather\nclient SKY=15 / raw darkened=15\npower off SKY=0, no stale noon marker\npower on SKY=15 restored\nNeoForge smooth light pipeline and entity renderer retain local noon\n4 lighting-isolated terrain screenshots; fixed environmental fog 32..1024\n1 additional unmodified rainy-fog screenshot\n");
            Singulo.LOGGER.info("SINGULO_RAYLEIGH_COMPLETE 256 4"); phase = 6; mc.stop();
        }
        }
    }
}
