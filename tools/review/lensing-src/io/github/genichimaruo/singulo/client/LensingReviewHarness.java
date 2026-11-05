package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.generated.ClientConfig;
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
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

/** Real GPU regression: fifteen visible, overlapping holes that must all be traced, plus a competing remnant distortion. */
@EventBusSubscriber(modid = Singulo.MODID, value = Dist.CLIENT)
public final class LensingReviewHarness {
    private static final int[] ROWS = {80, 90, 100};
    private static final int COLUMNS = 5;
    private static final int HOLES = ROWS.length * COLUMNS;
    private static int ticks, frames;
    private static final float[][] screen = new float[HOLES][];
    private static final int SETTLE = 120, SAMPLE = 300;
    private static int measured;
    private static long sampleStart;
    private static double lensedMs;
    private static boolean requested, complete, checkedFrame, verified;
    private static volatile boolean prepared;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (++ticks > 2400) throw new AssertionError("Many-hole rendering review timed out");
        if (mc.level == null || mc.player == null || mc.getOverlay() != null || complete) return;
        if (!requested) {
            requested = true;
            mc.getSingleplayerServer().execute(() -> {
                var server = mc.getSingleplayerServer();
                var level = server.overworld();
                level.setDayTime(6000);
                level.setWeatherParameters(6000, 0, false, false);
                for (int row : ROWS) for (int i = 0; i < COLUMNS; i++) {
                    BlockPos p = new BlockPos(i * 10, row, 0);
                    level.getChunkAt(p);
                    level.setBlockAndUpdate(p, SinguloBlocks.ROGUE_BLACK_HOLE.get().defaultBlockState());
                    var hole = (RogueBlackHoleBlockEntity) level.getBlockEntity(p);
                    hole.setStable(true);
                    hole.setCore(5000, 0.8);
                }
                var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                player.setGameMode(GameType.SPECTATOR);
                player.teleportTo(level, 20.5, 88, 62.5, 180, 0);
                prepared = true;
            });
        }
        if (prepared) {
            mc.setScreen(null);
            mc.options.hideGui = true;
            mc.player.setYRot(180);
            mc.player.setXRot(0);
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
        if (traced == HOLES) {
            GravitationalLensing.addDistortion(new Vec3(20.5, 80.5, 20), 0.2F, 0.08F, 1.0F);
            project(event);
            checkedFrame = true;
        }
    }

    /** Where each hole center lands on the screen this frame (pixels, y down). */
    private static void project(RenderLevelStageEvent event) {
        Vec3 cam = event.getCamera().getPosition();
        Matrix4f view = new Matrix4f().rotation(event.getCamera().rotation().conjugate(new Quaternionf()));
        int n = 0;
        for (int row : ROWS) for (int i = 0; i < COLUMNS; i++) {
            Vector4f v = new Vector4f((float) (i * 10 + 0.5 - cam.x), (float) (row + 0.5 - cam.y), (float) (0.5 - cam.z), 1);
            view.transform(v);
            event.getProjectionMatrix().transform(v);
            screen[n++] = new float[]{(v.x / v.w * 0.5F + 0.5F) * 1280, (0.5F - v.y / v.w * 0.5F) * 800};
        }
    }

    /** Average frame time over SAMPLE frames, after SETTLE frames, with lensing on and then off. */
    private static boolean measure() {
        if (++measured <= SETTLE) {
            sampleStart = System.nanoTime();
            return false;
        }
        if (measured < SETTLE + SAMPLE) return false;
        double ms = (System.nanoTime() - sampleStart) / 1.0e6 / (SAMPLE - 1);
        measured = 0;
        if (lensedMs == 0) {
            lensedMs = ms;
            ClientConfig.GRAVITATIONAL_LENSING.set(false);
            return false;
        }
        ClientConfig.GRAVITATIONAL_LENSING.set(true);
        Singulo.LOGGER.info("SINGULO_LENSING_FRAME_TIME holes={} lensedMs={} unlensedMs={}", HOLES, lensedMs, ms);
        return true;
    }

    @SubscribeEvent
    public static void rendered(RenderFrameEvent.Post event) throws Exception {
        if (verified && !complete && measure()) {
            complete = true;
            Minecraft.getInstance().stop();
            return;
        }
        if (!checkedFrame || complete || verified) return;
        checkedFrame = false;
        if (++frames < 30) return;
        Minecraft mc = Minecraft.getInstance();
        Path output = Path.of(System.getProperty("singulo.lensingOutput"));
        Files.createDirectories(output);
        try (var png = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            png.writeToFile(output.resolve("many-holes-and-remnant.png"));
            if (png.getWidth() != 1280 || png.getHeight() != 800) throw new AssertionError("Unexpected lensing viewport");
            // Traced holes draw nothing in the world, so a dark center proves the shader drew this hole.
            for (float[] center : screen) {
                int black = 0;
                int cx = (int) center[0];
                int cy = (int) center[1];
                for (int y = cy - 4; y < cy + 4; y++) for (int x = cx - 4; x < cx + 4; x++) {
                    int pixel = png.getPixelRGBA(x, y);
                    if ((pixel & 255) < 20 && (pixel >> 8 & 255) < 20 && (pixel >> 16 & 255) < 20) black++;
                }
                if (black < 40) throw new AssertionError("Black-hole shadow missing at screen " + cx + "," + cy);
            }
        }
        Singulo.LOGGER.info("SINGULO_LENSING_MANY_COMPLETE traced={} remnant=1 frames={} fps={}", HOLES, frames, mc.getFps());
        verified = true;
    }
}
