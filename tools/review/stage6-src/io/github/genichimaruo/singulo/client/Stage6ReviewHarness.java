package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.android.BitAndroid;
import io.github.genichimaruo.singulo.registry.SinguloEntities;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent;

/** Disposable in-game capture of beams, blade lunges, trailing movement and the advancement tree. */
@EventBusSubscriber(modid = Singulo.MODID, value = Dist.CLIENT)
public final class Stage6ReviewHarness {
    private static volatile boolean prepared;
    private static volatile long started = -1;
    private static BitAndroid bit;
    private static Zombie target;
    private static int ticks, phase, wait;
    private static boolean observedRecall, observedRecallCulling;
    private static final Set<Integer> captured = new HashSet<>();

    @SubscribeEvent
    public static void client(TickEvent.ClientTickEvent event) throws Exception {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (++ticks > 20 * 180) throw new IllegalStateException("Stage 6 visual review timed out");
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null || mc.getOverlay() != null) return;
        if (phase == 0) {
            phase = 1;
            mc.getSingleplayerServer().execute(() -> prepare(mc));
            return;
        }
        if (!prepared) return;
        if (phase == 1) {
            if (mc.screen != null) return;
            if (started < 0) {
                if (++wait < 40) return;
                started = mc.level.getGameTime();
            }
            mc.options.fov().set(55);
            mc.options.hideGui = true;
            Vec3 d = new Vec3(0.5, 82.4, 2.5).subtract(mc.player.getEyePosition());
            mc.player.setYRot((float) (Math.atan2(d.z, d.x) * 180 / Math.PI) - 90);
            mc.player.setXRot((float) (-Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * 180 / Math.PI));
            int elapsed = (int) (mc.level.getGameTime() - started);
            int frame = elapsed / 3;
            if (elapsed >= 15 && elapsed < 200 && captured.add(frame)) capture(mc, String.format("bits-%03d", elapsed));
            var localBit = mc.level.getEntitiesOfClass(BitAndroid.class, mc.player.getBoundingBox().inflate(30)).stream().findFirst().orElse(null);
            if (elapsed > 170 && localBit != null && localBit.deployed() == 0 && localBit.recallingFrom(0) > 0) {
                observedRecall = true;
                var edge = new net.minecraft.client.renderer.culling.Frustum(new org.joml.Matrix4f(), new org.joml.Matrix4f()) {
                    @Override
                    public boolean isVisible(net.minecraft.world.phys.AABB box) {
                        return box.getXsize() > 2.5 || box.getZsize() > 2.5;
                    }
                };
                // Reject the body alone, accept the volume containing the floating bits.
                var renderer = mc.getEntityRenderDispatcher().getRenderer(localBit);
                Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
                if (!localBit.shouldRender(camera.x, camera.y, camera.z))
                    throw new IllegalStateException("Recall culling fixture is beyond render distance");
                if (!renderer.shouldRender(localBit, edge, camera.x, camera.y, camera.z))
                    throw new IllegalStateException("Recalling bits culled when only the body is outside the frustum");
                observedRecallCulling = true;
            }
            if (elapsed > 205) {
                if (!observedRecall || !observedRecallCulling || localBit == null || localBit.recallingFrom(0) != 0)
                    throw new IllegalStateException("Recall animation did not begin and finish on client");
                phase = 2;
                mc.options.hideGui = false;
                mc.getSingleplayerServer().execute(() -> {
                    ServerPlayer player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                    for (var advancement : mc.getSingleplayerServer().getAdvancements().getAllAdvancements()) {
                        if (advancement.getId().getNamespace().equals(Singulo.MODID)) player.getAdvancements().award(advancement, "done");
                    }
                });
            }
        } else if (++wait % 30 == 0) {
            if (phase == 2) {
                org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), 10, 10);
                mc.setScreen(new AdvancementsScreen(mc.player.connection.getAdvancements()));
                phase++;
            } else if (phase == 3) {
                capture(mc, "advancements-start-ja");
                mc.screen.mouseDragged(320, 200, 0, 0, 0);
                mc.screen.mouseDragged(320, 200, 0, -450, -100);
                phase++;
            } else if (phase == 4) {
                capture(mc, "advancements-middle-ja");
                mc.screen.mouseDragged(320, 200, 0, -450, 50);
                phase++;
            } else if (phase == 5) {
                capture(mc, "advancements-end-ja");
                mc.setScreen(null);
                mc.getSingleplayerServer().execute(() -> {
                    var server = mc.getSingleplayerServer();
                    var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                    player.getAdvancements().revoke(server.getAdvancements().getAdvancement(Singulo.id("archivist")), "done");
                });
                phase++;
            } else if (phase == 6) {
                openProgress(mc);
                if (progressText(mc).contains(net.minecraft.network.chat.Component.translatable("advancement.singulo.archivist.title").getString()))
                    throw new IllegalStateException("Hidden advancement leaked before completion");
                phase++;
            } else if (phase == 7) {
                capture(mc, "handbook-hidden-before-ja");
                mc.setScreen(null);
                mc.getSingleplayerServer().execute(() -> {
                    var server = mc.getSingleplayerServer();
                    var player = server.getPlayerList().getPlayer(mc.player.getUUID());
                    player.getAdvancements().award(server.getAdvancements().getAdvancement(Singulo.id("archivist")), "done");
                });
                phase++;
            } else if (phase == 8) {
                openProgress(mc);
                if (!progressText(mc).contains(net.minecraft.network.chat.Component.translatable("advancement.singulo.archivist.title").getString()))
                    throw new IllegalStateException("Hidden advancement missing after completion");
                phase++;
            } else if (phase == 9) {
                capture(mc, "handbook-hidden-after-ja");
                Singulo.LOGGER.info("SINGULO_STAGE6_VISUAL_COMPLETE combatFrames={} advancements=3 handbook=2 recall=true recallCulling=true hiddenBefore=false hiddenAfter=true", captured.size());
                mc.stop();
                phase++;
            }
        }
    }

    @SubscribeEvent
    public static void server(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!prepared || started < 0 || bit == null || event.getServer() != Minecraft.getInstance().getSingleplayerServer()) return;
        int t = (int) (bit.level().getGameTime() - started);
        var level = (net.minecraft.server.level.ServerLevel) bit.level();
        if (t == 170) bit.setDeployed(0);
        if (t == 25) bit.fire(level, 0, target);
        if (t == 50 || t == 150) bit.slash(level, 1, target);
        if (t >= 100 && t <= 135) {
            bit.setPos(bit.getX() + 0.06, bit.getY(), bit.getZ());
            bit.yBodyRot = 90;
            bit.setYRot(90);
        }
    }

    private static void prepare(Minecraft mc) {
        ServerPlayer player = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
        var level = player.serverLevel();
        player.setGameMode(GameType.SPECTATOR);
        player.teleportTo(7, 83, 10);
        level.setDayTime(6000);
        level.setWeatherParameters(10000, 0, false, false);
        for (BlockPos p : BlockPos.betweenClosed(-5, 80, -5, 12, 86, 12)) {
            level.setBlockAndUpdate(p, p.getY() == 80 ? Blocks.SMOOTH_STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }
        bit = SinguloEntities.BIT_ANDROID.get().create(level);
        bit.setNoAi(true);
        bit.setNoGravity(true);
        bit.moveTo(0.5, 81, 0.5, 0, 0);
        bit.makeGuard(player.getUUID(), new BlockPos(0, 80, 0), 0, 0);
        bit.setDeployed(BitAndroid.BITS_OUT);
        level.addFreshEntity(bit);
        target = EntityType.HUSK.create(level);
        target.setNoAi(true);
        target.setNoGravity(true);
        target.moveTo(0.5, 81, 8.5, 180, 0);
        target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(200);
        target.setHealth(200);
        level.addFreshEntity(target);
        prepared = true;
    }

    private static Object field(Object owner, String name) throws Exception {
        var field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static void openProgress(Minecraft mc) throws Exception {
        mc.setScreen(new HandbookScreen());
        for (var setting : new String[]{"chapter", "page", "scroll"}) {
            var field = HandbookScreen.class.getDeclaredField(setting);
            field.setAccessible(true);
            field.setInt(mc.screen, setting.equals("chapter") ? 1 : setting.equals("page") ? 4 : 0);
        }
    }

    private static String progressText(Minecraft mc) throws Exception {
        var chapters = (java.util.List<?>) field(mc.screen, "chapters");
        var text = new StringBuilder();
        for (Object page : (java.util.List<?>) field(chapters.get(1), "pages")) {
            for (Object line : (java.util.List<?>) field(page, "lines")) {
                text.append(((net.minecraft.network.chat.Component) field(line, "text")).getString()).append('\n');
            }
        }
        return text.toString();
    }

    private static void capture(Minecraft mc, String name) throws Exception {
        Path root = Path.of(System.getProperty("singulo.stage6Output"));
        Files.createDirectories(root);
        if (mc.getOverlay() != null || mc.screen instanceof net.minecraft.client.gui.screens.ReceivingLevelScreen)
            throw new IllegalStateException("Capture obscured by loading: " + name);
        org.lwjgl.opengl.GL11.glFinish();
        try (var pixels = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            pixels.writeToFile(root.resolve(name + ".png"));
        }
        Singulo.LOGGER.info("SINGULO_STAGE6_CAPTURE {}", name);
    }
}
