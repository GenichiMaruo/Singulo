package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.android.RuinAndroid;
import io.github.genichimaruo.singulo.registry.SinguloEntities;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.GraphicsPreset;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** A real blade and dagger on either side of glass, photographed with a matching empty scene. */
@EventBusSubscriber(modid = Singulo.MODID, value = Dist.CLIENT)
public final class AndroidWeaponReviewHarness {
    private static final String OUTPUT = System.getProperty("singulo.androidWeaponOutput");
    private static final List<GraphicsPreset> MODES = Arrays.stream(System.getProperty("singulo.androidWeaponGraphics", "fancy,fabulous").split(","))
            .map(s -> GraphicsPreset.valueOf(s.trim().toUpperCase(Locale.ROOT))).distinct().toList();
    private static final AABB SCENE_BOUNDS = new AABB(-8, 178, -8, 9, 187, 8);
    private static final List<Scene> SCENES = List.of(Scene.values());
    private static final int FRAMES_PER_MODE = SCENES.size() * 2;
    private static int frame, settled, elapsed;
    private static boolean requested, captured, capturing, finished;
    private static volatile boolean ready;
    private static Boolean activeTransparency;

    private enum Scene {
        CLEAR("clear", 0), STAIRS_BACK("stairs-back", 2), STAIRS_FRONT("stairs-front", -2),
        RED_BACK("red-back", 2), RED_FRONT("red-front", -2), OPAQUE_FRONT("opaque-front", -2);

        final String name;
        final int z;

        Scene(String name, int z) {
            this.name = name;
            this.z = z;
        }

        BlockState block() {
            return switch (this) {
                case CLEAR -> Blocks.AIR.defaultBlockState();
                case OPAQUE_FRONT -> Blocks.GRAY_CONCRETE.defaultBlockState();
                case RED_BACK, RED_FRONT -> Blocks.RED_STAINED_GLASS.defaultBlockState();
                case STAIRS_BACK, STAIRS_FRONT -> BuiltInRegistries.BLOCK.getValue(Singulo.id("android_gate_glass_stairs"))
                        .defaultBlockState().setValue(StairBlock.FACING, Direction.EAST);
            };
        }
    }

    private static GraphicsPreset mode() { return MODES.get(frame / FRAMES_PER_MODE); }
    private static Scene scene() { return SCENES.get(frame % FRAMES_PER_MODE / 2); }
    private static boolean withSubjects() { return frame % 2 == 0; }

    private static void pose(RuinAndroid android) {
        float yaw = android.getX() < 0.5 ? 148 : -146;
        android.setYRot(yaw);
        android.setYBodyRot(yaw);
        android.setYHeadRot(yaw);
        android.yRotO = android.yBodyRotO = android.yHeadRotO = yaw;
        android.setXRot(0);
        android.xRotO = 0;
        android.setDeltaMovement(Vec3.ZERO);
    }

    private static void spawn(ServerLevel level, EntityType<? extends RuinAndroid> type, double x) {
        var android = type.create(level, net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
        android.snapTo(x, 180, 0.5, 0, 0);
        android.setNoAi(true);
        android.setPersistenceRequired();
        android.setSilent(true);
        pose(android);
        level.addFreshEntity(android);
    }

    private static void prepare(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        var level = server.overworld();
        var player = server.getPlayerList().getPlayer(mc.player.getUUID());
        if (frame == 0) {
            level.getGameRules().set(GameRules.ADVANCE_TIME, false, server);
            level.getGameRules().set(GameRules.ADVANCE_WEATHER, false, server);
            level.getGameRules().set(GameRules.SPAWN_MOBS, false, server);
            server.clockManager().setTotalTicks(level.dimensionType().defaultClock().orElseThrow(), 6000);
            server.setWeatherParameters(100000, 0, false, false);
            var gray = Blocks.GRAY_CONCRETE.defaultBlockState();
            for (var p : BlockPos.betweenClosed(-4, 179, -7, 4, 179, 4)) level.setBlockAndUpdate(p, gray);
            for (var p : BlockPos.betweenClosed(-5, 180, 4, 5, 185, 4)) level.setBlockAndUpdate(p, gray);
        }
        for (var old : level.getEntitiesOfClass(RuinAndroid.class, SCENE_BOUNDS)) old.discard();
        if (withSubjects()) {
            spawn(level, SinguloEntities.BLADE_ANDROID.get(), -0.37);
            spawn(level, SinguloEntities.BIT_ANDROID.get(), 1.37);
        }
        Scene scene = scene();
        for (int z : new int[]{-2, 2}) {
            var block = scene.z == z ? scene.block() : Blocks.AIR.defaultBlockState();
            for (var p : BlockPos.betweenClosed(-3, 180, z, 3, 182, z)) level.setBlockAndUpdate(p, block);
        }
        player.setGameMode(GameType.SPECTATOR);
        player.teleportTo(level, 0.5, 181.02 - player.getEyeHeight(), -5, java.util.Set.of(), 0, 0, false);
        ready = true;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) throws Exception {
        if (OUTPUT == null || finished) return;
        var mc = Minecraft.getInstance();
        if (++elapsed > 7200) throw new AssertionError("Android weapon review timed out at frame " + frame);
        if (mc.player == null || mc.level == null || mc.getOverlay() != null || mc.getSingleplayerServer() == null) return;
        if (captured) {
            capturing = captured = ready = requested = false;
            settled = 0;
            if (++frame == FRAMES_PER_MODE * MODES.size()) {
                for (var mode : MODES) AndroidWeaponReviewChecks.verify(Path.of(OUTPUT), mode.name().toLowerCase(Locale.ROOT));
                finished = true; // stop() may allow another client tick before shutdown.
                Singulo.LOGGER.info("SINGULO_ANDROID_WEAPON_REVIEW_COMPLETE {}", frame);
                mc.stop();
                return;
            }
        }
        if (!requested) {
            requested = true;
            if (frame == 0) KatanaGripReview.verify(mc);
            if (frame % FRAMES_PER_MODE == 0) {
                mc.options.graphicsPreset().set(mode());
                mc.options.improvedTransparency().set(mode() == GraphicsPreset.FABULOUS);
                mc.options.fov().set(40);
                mc.levelRenderer.allChanged();
            }
            mc.getSingleplayerServer().execute(() -> prepare(mc));
            return;
        }
        if (!ready) return;
        mc.setScreen(null);
        mc.options.hideGui = true;
        mc.player.setYRot(0);
        mc.player.setXRot(0);
        mc.player.setDeltaMovement(Vec3.ZERO);
        var visible = mc.level.getEntitiesOfClass(RuinAndroid.class, SCENE_BOUNDS);
        for (var android : visible) {
            android.tickCount = 120;
            pose(android);
        }
        if (visible.size() == (withSubjects() ? 2 : 0)) settled++;
    }

    @SubscribeEvent
    public static void observe(RenderLevelStageEvent.AfterTranslucentFeatures event) {
        // The 26.1 framegraph releases the translucency attachment before AfterLevel.
        if (OUTPUT != null) activeTransparency = Minecraft.getInstance().levelRenderer.getTranslucentTarget() != null;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void render(RenderLevelStageEvent.AfterLevel event) throws Exception {
        if (!ready || capturing) return;
        if (settled < (frame % FRAMES_PER_MODE == 0 ? 80 : 25)) return;
        var mc = Minecraft.getInstance();
        if (!java.util.Objects.equals(activeTransparency, mode() == GraphicsPreset.FABULOUS)) {
            throw new AssertionError("Requested graphics pipeline is unavailable: " + mode());
        }
        Path dir = Path.of(OUTPUT);
        Files.createDirectories(dir);
        String name = mode().name().toLowerCase(Locale.ROOT) + "-" + scene().name + (withSubjects() ? "-subjects.png" : "-empty.png");
        capturing = true;
        Screenshot.takeScreenshot(mc.getMainRenderTarget(), shot -> {
            try (shot) {
                shot.writeToFile(dir.resolve(name));
                mc.execute(() -> captured = true);
            } catch (java.io.IOException error) {
                mc.execute(() -> { throw new java.io.UncheckedIOException(error); });
            }
        });
    }
}
