package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.reactor.RogueBlackHoleBlockEntity;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Real GPU regression: five visible holes plus a competing remnant distortion. */
@EventBusSubscriber(modid = Singulo.MODID, value = Dist.CLIENT)
public final class LensingReviewHarness {
    private static int ticks, frames;
    private static boolean requested, complete, checkedFrame;
    private static volatile boolean prepared;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (++ticks > 2400) throw new AssertionError("Five-hole rendering review timed out");
        if (mc.level == null || mc.player == null || mc.getOverlay() != null || complete) return;
        if (!requested) {
            requested = true;
            mc.getSingleplayerServer().execute(() -> {
                var server = mc.getSingleplayerServer();
                var level = server.overworld();
                level.setDayTime(6000);
                level.setWeatherParameters(6000, 0, false, false);
                for (int i = 0; i < 5; i++) {
                    BlockPos p = new BlockPos(i * 10, 80, 0);
                    level.getChunkAt(p);
                    level.setBlockAndUpdate(p, SinguloBlocks.ROGUE_BLACK_HOLE.get().defaultBlockState());
                    var hole = (RogueBlackHoleBlockEntity) level.getBlockEntity(p);
                    hole.setStable(true);
                    hole.setCore(5000, 0.8);
                }
                var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                player.setGameMode(GameType.SPECTATOR);
                player.teleportTo(level, 20.5, 82, 52.5, 180, 3.5F);
                prepared = true;
            });
        }
        if (prepared) {
            mc.setScreen(null);
            mc.options.hideGui = true;
            mc.player.setYRot(180);
            mc.player.setXRot(3.5F);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void stage(RenderLevelStageEvent event) throws Exception {
        if (!prepared || complete || event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) return;
        var field = GravitationalLensing.class.getDeclaredField("SOURCES");
        field.setAccessible(true);
        var sources = (List<?>) field.get(null);
        int traced = 0;
        for (Object source : sources) {
            var disk = source.getClass().getDeclaredMethod("disk");
            disk.setAccessible(true);
            if (disk.invoke(source) != null) traced++;
        }
        if (traced == 4 && sources.size() >= 5) {
            GravitationalLensing.addDistortion(new Vec3(20.5, 80.5, 20), 0.2F, 0.08F, 1.0F);
            checkedFrame = true;
        }
    }

    @SubscribeEvent
    public static void rendered(RenderFrameEvent.Post event) throws Exception {
        if (!checkedFrame || complete) return;
        checkedFrame = false;
        if (++frames < 30) return;
        Minecraft mc = Minecraft.getInstance();
        Path output = Path.of(System.getProperty("singulo.lensingOutput"));
        Files.createDirectories(output);
        try (var png = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            png.writeToFile(output.resolve("five-holes-and-remnant.png"));
            if (png.getWidth() != 1280 || png.getHeight() != 800) throw new AssertionError("Unexpected lensing viewport");
            for (int center : new int[]{447, 544, 640, 737, 833}) {
                int black = 0;
                for (int y = 390; y < 410; y++) for (int x = center - 10; x < center + 10; x++) {
                    int pixel = png.getPixelRGBA(x, y);
                    if ((pixel & 255) < 20 && (pixel >> 8 & 255) < 20 && (pixel >> 16 & 255) < 20) black++;
                }
                if (black < 100) throw new AssertionError("Black-hole shadow missing at screen x=" + center);
            }
        }
        Singulo.LOGGER.info("SINGULO_LENSING_FIVE_COMPLETE traced=4 fallback=1 remnant=1 frames={}", frames);
        complete = true;
        mc.stop();
    }
}
