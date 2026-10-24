package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.ruin.lab.RayleighPanelBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent.ClientTickEvent;

/** Real disconnect, title screen, and reopen in one JVM, with saved artificial skylight. */
@EventBusSubscriber(modid = Singulo.MODID, value = Dist.CLIENT)
public final class RejoinReviewHarness {
    private static int visits, wait;
    private static boolean entered, started;
    private static volatile long progress = System.nanoTime();

    @SubscribeEvent
    public static void tick(ClientTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (!started) {
            started = true;
            var watchdog = new Thread(() -> {
                while (true) {
                    try { Thread.sleep(1000); } catch (InterruptedException e) { return; }
                    if (System.nanoTime() - progress > 90_000_000_000L) {
                        Thread.getAllStackTraces().forEach((thread, stack) -> {
                            Singulo.LOGGER.error("REJOIN_TIMEOUT thread={} state={}", thread.getName(), thread.getState());
                            for (var frame : stack) Singulo.LOGGER.error("  at {}", frame);
                        });
                        System.exit(2);
                    }
                }
            }, "Singulo rejoin watchdog");
            watchdog.setDaemon(true); watchdog.start();
        }
        if (mc.level != null && mc.player != null && mc.getOverlay() == null) {
            if (!entered) {
                entered = true; wait = 0; visits++; progress = System.nanoTime();
                Singulo.LOGGER.info("SINGULO_REJOIN_ENTER {}", visits);
                if (visits == 1 && !Boolean.getBoolean("singulo.rejoinExisting")) mc.getSingleplayerServer().execute(() -> {
                    var level = mc.getSingleplayerServer().overworld();
                    for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++)
                        level.setBlockAndUpdate(new BlockPos(x, 246, z), SinguloBlocks.LAB_RAYLEIGH_PANEL.get().defaultBlockState().setValue(RayleighPanelBlock.LIT, true));
                    var player = level.getServer().getPlayerList().getPlayers().get(0);
                    player.teleportTo(8, 242, 8); player.setNoGravity(true);
                });
            }
            if (!Boolean.getBoolean("singulo.rejoinExisting")) {
                mc.player.setNoGravity(true); mc.player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                mc.player.setXRot(-85); mc.player.setYRot(0);
            }
            if (++wait == 100) mc.pauseGame(false);
            if (wait < 160) return;
            try (var image = net.minecraft.client.Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                var output = java.nio.file.Path.of("screenshots", "rejoin-" + visits + ".png");
                java.nio.file.Files.createDirectories(output.getParent()); image.writeToFile(output);
            } catch (java.io.IOException e) { throw new RuntimeException(e); }
            progress = System.nanoTime();
            if (visits == 5) {
                Singulo.LOGGER.info("SINGULO_REJOIN_COMPLETE 5"); mc.stop(); return;
            }
            mc.level.disconnect();
            mc.clearLevel(new TitleScreen());
            entered = false; wait = 0;
            Singulo.LOGGER.info("SINGULO_REJOIN_TITLE {}", visits);
        } else if (visits > 0 && mc.screen instanceof TitleScreen && ++wait == 30) {
            progress = System.nanoTime();
            mc.createWorldOpenFlows().loadLevel(new TitleScreen(), "Review");
        }
    }
}
