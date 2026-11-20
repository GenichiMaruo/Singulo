package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.android.RuinAndroid;
import io.github.genichimaruo.singulo.registry.SinguloEntities;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;

/** A real blade and dagger on either side of glass, photographed with a matching empty scene. */
@EventBusSubscriber(modid = Singulo.MODID, value = Dist.CLIENT)
public final class AndroidWeaponReviewHarness {
    private static final String OUTPUT = System.getProperty("singulo.androidWeaponOutput");
    private static final List<GraphicsStatus> MODES = Arrays.stream(System.getProperty("singulo.androidWeaponGraphics", "fancy,fabulous").split(","))
            .map(s -> GraphicsStatus.valueOf(s.trim().toUpperCase(Locale.ROOT))).distinct().toList();
    private static final AABB SCENE_BOUNDS = new AABB(-8, 178, -8, 9, 187, 8);
    private static final List<Scene> SCENES = List.of(Scene.values());
    private static final int FRAMES_PER_MODE = SCENES.size() * 2;
    private static int frame, settled, elapsed;
    private static boolean requested, captured, complete;
    private static volatile boolean ready;

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
                case STAIRS_BACK, STAIRS_FRONT -> BuiltInRegistries.BLOCK.get(Singulo.id("android_gate_glass_stairs"))
                        .defaultBlockState().setValue(StairBlock.FACING, Direction.EAST);
            };
        }
    }

    private static GraphicsStatus mode() { return MODES.get(frame / FRAMES_PER_MODE); }
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
        var android = type.create(level);
        android.moveTo(x, 180, 0.5, 0, 0);
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
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
            level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
            level.setDayTime(6000);
            level.setWeatherParameters(100000, 0, false, false);
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
        player.teleportTo(level, 0.5, 181.02 - player.getEyeHeight(), -5, 0, 0);
        ready = true;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) throws Exception {
        if (event.phase != TickEvent.Phase.END) return;
        if (OUTPUT == null || complete) return;
        var mc = Minecraft.getInstance();
        if (++elapsed > 7200) throw new AssertionError("Android weapon review timed out at frame " + frame);
        if (mc.player == null || mc.level == null || mc.getOverlay() != null || mc.getSingleplayerServer() == null) return;
        if (captured) {
            captured = ready = requested = false;
            settled = 0;
            if (++frame == FRAMES_PER_MODE * MODES.size()) {
                for (var mode : MODES) AndroidWeaponReviewChecks.verify(Path.of(OUTPUT), mode.name().toLowerCase(Locale.ROOT));
                Singulo.LOGGER.info("SINGULO_ANDROID_WEAPON_REVIEW_COMPLETE {}", frame);
                complete = true;
                mc.stop();
                return;
            }
        }
        if (!requested) {
            requested = true;
            if (frame % FRAMES_PER_MODE == 0) {
                mc.options.graphicsMode().set(mode());
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

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void render(RenderLevelStageEvent event) throws Exception {
        if (!ready || captured || event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        if (settled < (frame % FRAMES_PER_MODE == 0 ? 80 : 25)) return;
        var mc = Minecraft.getInstance();
        if (mc.options.graphicsMode().get() != mode()
                || (mc.levelRenderer.getTranslucentTarget() != null) != (mode() == GraphicsStatus.FABULOUS)) {
            throw new AssertionError("Requested graphics pipeline is unavailable: " + mode());
        }
        Path dir = Path.of(OUTPUT);
        Files.createDirectories(dir);
        String name = mode().name().toLowerCase(Locale.ROOT) + "-" + scene().name + (withSubjects() ? "-subjects.png" : "-empty.png");
        try (var shot = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            shot.writeToFile(dir.resolve(name).toFile());
        }
        captured = true;
    }
}
