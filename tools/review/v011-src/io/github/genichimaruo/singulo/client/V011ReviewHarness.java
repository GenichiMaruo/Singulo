package io.github.genichimaruo.singulo.client;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.cable.CableBlockEntity;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent.ClientTickEvent;

/**
 * Development-only capture of the 0.11.0 visuals (tools/review/v011.gradle): horizon bus power effects, vending machine sides,
 * compressed two-block held models. Writes PNGs to singulo.v011Output and stops the client.
 */
@EventBusSubscriber(modid = Singulo.MODID, value = Dist.CLIENT)
public final class V011ReviewHarness {
    /** A camera shot: where to stand, what to look at, what to hold, and the camera type. */
    record Shot(String name, Vec3 eye, Vec3 look, String main, String off, CameraType camera, int settle, int fov, int glasses,
                boolean screen) {
        Shot(String name, Vec3 eye, Vec3 look, String main, String off, CameraType camera, int settle) {
            this(name, eye, look, main, off, camera, settle, 70, NO_GLASSES, false);
        }
    }

    private static final int NO_GLASSES = Integer.MIN_VALUE;

    private static final List<Shot> SHOTS = new ArrayList<>();
    private static int ticks, phase, index, wait;
    private static volatile boolean prepared;

    private static ItemStack item(String id) {
        return id.isEmpty() ? ItemStack.EMPTY : new ItemStack(BuiltInRegistries.ITEM.get(Singulo.id(id)));
    }

    static {
        SHOTS.add(new Shot("cables", new Vec3(2.5, 85.5, -1.5), new Vec3(2.5, 80.0, 2.5), "", "",
                CameraType.FIRST_PERSON, 30));
        SHOTS.add(new Shot("cables-close", new Vec3(-1.5, 82.0, 4.5), new Vec3(3.5, 80.5, 4.5), "", "",
                CameraType.FIRST_PERSON, 10));
        SHOTS.add(new Shot("vending", new Vec3(14.5, 82.0, -1.5), new Vec3(14.5, 81.0, 3.5), "", "",
                CameraType.FIRST_PERSON, 20));
        SHOTS.add(new Shot("vending-side", new Vec3(18.5, 82.0, 0.5), new Vec3(15.0, 81.0, 3.5), "", "",
                CameraType.FIRST_PERSON, 10));
        SHOTS.add(new Shot("held", new Vec3(12.5, 82.0, -6.5), new Vec3(12.5, 81.4, -12.5), "vending_machine",
                "android_capsule", CameraType.FIRST_PERSON, 20));
        SHOTS.add(new Shot("held-third", new Vec3(12.5, 82.0, -6.5), new Vec3(12.5, 81.4, -12.5), "vending_machine",
                "android_capsule", CameraType.THIRD_PERSON_FRONT, 20));
        SHOTS.add(new Shot("android-glasses", new Vec3(20.5, 81.8, 1.3), new Vec3(20.5, 81.3, 2.5), "", "",
                CameraType.FIRST_PERSON, 20, 50, NO_GLASSES, false));
        SHOTS.add(new Shot("android-glasses-side", new Vec3(21.4, 81.8, 1.5), new Vec3(20.5, 81.3, 2.5), "", "",
                CameraType.FIRST_PERSON, 10, 50, NO_GLASSES, false));
        SHOTS.add(new Shot("stand-glasses", new Vec3(8.5, 82.2, -5.6), new Vec3(8.5, 81.75, -3.5), "", "",
                CameraType.FIRST_PERSON, 15, 40, NO_GLASSES, false));
        SHOTS.add(new Shot("player-glasses", new Vec3(8.5, 82.0, -8.5), new Vec3(8.5, 82.0, -2.5), "", "",
                CameraType.THIRD_PERSON_FRONT, 15, 30, 2, false));
        SHOTS.add(new Shot("rayleigh-up", new Vec3(30.5, 81.7, 3.2), new Vec3(30.0, 84.0, -2.0), "", "",
                CameraType.FIRST_PERSON, 50, 80, NO_GLASSES, false));
        SHOTS.add(new Shot("rayleigh-floor", new Vec3(30.5, 82.6, 3.4), new Vec3(30.0, 80.0, -2.5), "", "",
                CameraType.FIRST_PERSON, 10, 80, NO_GLASSES, false));
        SHOTS.add(new Shot("rayleigh-lab", new Vec3(28.5, 81.7, 3.0), new Vec3(28.5, 84.0, 1.0), "", "",
                CameraType.FIRST_PERSON, 10, 80, NO_GLASSES, false));
        SHOTS.add(new Shot("solarium", new Vec3(61.5, 83.0, 2.5), new Vec3(56.0, 81.0, 12.0), "", "",
                CameraType.FIRST_PERSON, 60, 80, NO_GLASSES, false));
        SHOTS.add(new Shot("solarium-up", new Vec3(56.5, 82.0, 3.5), new Vec3(58.0, 88.0, 9.0), "", "",
                CameraType.FIRST_PERSON, 10, 80, NO_GLASSES, false));
        SHOTS.add(new Shot("outdoor", new Vec3(61.5, 86.0, 32.0), new Vec3(62.0, 81.0, 48.0), "", "",
                CameraType.FIRST_PERSON, 60, 80, NO_GLASSES, false));
        SHOTS.add(new Shot("zero-gravity", new Vec3(4.5, 84.0, 13.0), new Vec3(4.5, 79.5, 17.0), "rayleigh_panel", "zero_gravity_block",
                CameraType.FIRST_PERSON, 30, 70, NO_GLASSES, false));
        SHOTS.add(new Shot("glasses-screen", new Vec3(8.5, 82.0, -8.5), new Vec3(8.5, 82.0, -2.5), "survey_glasses", "",
                CameraType.FIRST_PERSON, 15, 70, NO_GLASSES, true));
    }

    /** Client-side cable glow for the three flow levels (rows z = 0, 2, 4), re-applied every tick. */
    private static final long[] FLOWS = {50_000_000L, 300_000_000L, 3_000_000_000L};

    @SubscribeEvent
    public static void tick(ClientTickEvent event) throws Exception {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (++ticks > 20 * 240) throw new IllegalStateException("v0.11 review timed out phase=" + phase + " shot=" + index);
        if (mc.screen instanceof net.minecraft.client.gui.screens.BackupConfirmScreen) {
            for (var child : mc.screen.children()) {
                if (child instanceof net.minecraft.client.gui.components.Button b
                        && b.getMessage().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents label
                        && label.getKey().equals("selectWorld.backupJoinSkipButton") && b.active) {
                    b.onPress();
                    return;
                }
            }
        }
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null || mc.getOverlay() != null) return;
        if (phase == 0) {
            phase = 1;
            mc.getSingleplayerServer().execute(() -> prepare(mc.getSingleplayerServer().overworld()));
            return;
        }
        if (!prepared) return;
        applyFlows(mc);
        if (index >= SHOTS.size()) {
            Files.writeString(output().resolve("done.txt"), "shots: " + SHOTS.size() + "\n");
            mc.stop();
            return;
        }
        Shot shot = SHOTS.get(index);
        if (wait == 0) {
            mc.setScreen(null);
            mc.options.hideGui = shot.main().isEmpty() && shot.off().isEmpty() && !shot.screen();
            mc.options.fov().set(shot.fov());
            mc.options.setCameraType(shot.camera());
            var server = mc.getSingleplayerServer();
            server.execute(() -> {
                var player = server.getPlayerList().getPlayers().get(0);
                player.setItemInHand(InteractionHand.MAIN_HAND, item(shot.main()));
                player.setItemInHand(InteractionHand.OFF_HAND, item(shot.off()));
                ItemStack head = ItemStack.EMPTY;
                if (shot.glasses() != NO_GLASSES) {
                    head = item("survey_glasses");
                    io.github.genichimaruo.singulo.android.work.SurveyGlassesItem.setHeight(head, shot.glasses());
                }
                player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, head);
                Vec3 d = shot.look().subtract(shot.eye());
                float yaw = (float) (Math.atan2(d.z, d.x) * 180 / Math.PI) - 90;
                float pitch = (float) (-Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * 180 / Math.PI);
                player.teleportTo(server.overworld(), shot.eye().x, shot.eye().y - player.getEyeHeight(), shot.eye().z, yaw, pitch);
            });
        }
        if (++wait >= shot.settle()) {
            Vec3 d = shot.look().subtract(mc.player.getEyePosition());
            mc.player.setYRot((float) (Math.atan2(d.z, d.x) * 180 / Math.PI) - 90);
            mc.player.setXRot((float) (-Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * 180 / Math.PI));
            if (shot.screen() && wait == shot.settle()) {
                GlassesHeightScreen.open(InteractionHand.MAIN_HAND);
            }
            if (wait >= shot.settle() + 4) {
                capture(mc, shot.name());
                index++;
                wait = 0;
            }
        }
    }

    private static void applyFlows(Minecraft mc) {
        for (int row = 0; row < FLOWS.length; row++) {
            for (int x = 0; x <= 5; x++) {
                if (mc.level.getBlockEntity(new BlockPos(x, 80, row * 2)) instanceof CableBlockEntity cable) {
                    CompoundTag tag = new CompoundTag();
                    tag.putLong("Flow", FLOWS[row]);
                    tag.putLong("FlowGeneration", 1);
                    tag.putInt("In", x == 0 ? 0 : 1 << Direction.WEST.get3DDataValue());
                    tag.putInt("Out", x == 5 ? 0 : 1 << Direction.EAST.get3DDataValue());
                    tag.putInt("Dist", x);
                    cable.handleUpdateTag(tag, mc.level.registryAccess());
                }
            }
        }
    }

    private static void prepare(ServerLevel level) {
        level.setDayTime(6000);
        level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false, level.getServer());
        for (int x = -4; x <= 40; x++) {
            for (int z = -14; z <= 8; z++) {
                level.setBlockAndUpdate(new BlockPos(x, 79, z), Blocks.SMOOTH_STONE.defaultBlockState());
                for (int y = 80; y <= 86; y++) level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
            }
        }
        // horizon bus rows: 50 MFE/t (plain), 300 MFE/t (sheath), 3 GFE/t (sheath + frames)
        for (int row = 0; row < 3; row++) {
            for (int x = 0; x <= 5; x++) {
                level.setBlockAndUpdate(new BlockPos(x, 80, row * 2), SinguloBlocks.HORIZON_BUS.get().defaultBlockState());
            }
        }
        // vending machine (powered) and android capsule, both halves
        place(level, new BlockPos(14, 80, 3), "vending_machine", s -> s.setValue(BlockStateProperties.POWERED, true));
        place(level, new BlockPos(16, 80, 3), "android_capsule", s -> s);
        // android wearing glasses (and one with a beret too, to check that they do not clash)
        for (int i = 0; i < 2; i++) {
            var android = io.github.genichimaruo.singulo.registry.SinguloEntities.BLADE_ANDROID.get().create(level);
            android.setNoAi(true);
            android.moveTo(20.5 + i * 1.6, 80, 2.5, 180, 0);
            android.setYHeadRot(180);
            android.setYBodyRot(180);
            android.makeGuard(null, new BlockPos(20, 80, 2), io.github.genichimaruo.singulo.android.RuinAndroid.MODE_GUARD, 30);
            android.inventory().setItem(io.github.genichimaruo.singulo.android.RuinAndroid.SLOT_DECOR, item("android_glasses"));
            android.setPersistenceRequired();
            level.addFreshEntity(android);
        }
        // Rayleigh sky panels: a closed room (x 25..35, z -6..4) whose ceiling (y 84) holds a lit 3x3 of crafted panels fed by
        // creative sources above, an unpowered 3x1 row, and a lit 3x2 of lab panels; daylight detectors with lamps below
        var LABP = BuiltInRegistries.BLOCK.get(Singulo.id("lab_panel")).defaultBlockState();
        for (int x = 25; x <= 35; x++) {
            for (int z = -6; z <= 4; z++) {
                level.setBlockAndUpdate(new BlockPos(x, 79, z), BuiltInRegistries.BLOCK.get(Singulo.id("lab_tiles")).defaultBlockState());
                for (int y = 80; y <= 85; y++) {
                    boolean wall = x == 25 || x == 35 || z == -6 || z == 4;
                    level.setBlockAndUpdate(new BlockPos(x, y, z), wall || y == 84 ? LABP : Blocks.AIR.defaultBlockState());
                }
            }
        }
        var made = SinguloBlocks.RAYLEIGH_PANEL.get().defaultBlockState();
        for (int x = 27; x <= 29; x++) {
            for (int z = -4; z <= -2; z++) {
                level.setBlockAndUpdate(new BlockPos(x, 84, z), made);
                level.setBlockAndUpdate(new BlockPos(x, 85, z), SinguloBlocks.CREATIVE_ENERGY_SOURCE.get().defaultBlockState());
            }
        }
        for (int x = 31; x <= 33; x++) level.setBlockAndUpdate(new BlockPos(x, 84, -4), made);
        var lab = SinguloBlocks.LAB_RAYLEIGH_PANEL.get().defaultBlockState().setValue(io.github.genichimaruo.singulo.ruin.lab.RayleighPanelBlock.LIT, true);
        for (int x = 27; x <= 29; x++) for (int z = 1; z <= 2; z++) level.setBlockAndUpdate(new BlockPos(x, 84, z), lab);
        level.setBlockAndUpdate(new BlockPos(28, 80, -3), Blocks.DAYLIGHT_DETECTOR.defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(29, 80, -3), Blocks.REDSTONE_LAMP.defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(32, 80, -4), Blocks.DAYLIGHT_DETECTOR.defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(33, 80, -4), Blocks.REDSTONE_LAMP.defaultBlockState());
        // lab rooms that replace the outdoors, with facility power restored (their ceilings are lab Rayleigh panels)
        placeRoom(level, "solarium", new BlockPos(50, 80, 0));
        placeRoom(level, "outdoor_test_ground", new BlockPos(50, 80, 30));
        // zero-gravity blocks with a control box, a panel receiver and a creative source
        for (int x = -2; x <= 10; x++) for (int z = 9; z <= 22; z++) {
            level.setBlockAndUpdate(new BlockPos(x, 79, z), Blocks.SMOOTH_STONE.defaultBlockState());
            for (int y = 80; y <= 86; y++) level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
        }
        var zombie = net.minecraft.world.entity.EntityType.ZOMBIE.create(level);
        zombie.setNoAi(true);
        zombie.setPersistenceRequired();
        zombie.moveTo(3.5, 81.5, 17.5, 180, 0);
        level.addFreshEntity(zombie);
        for (int x = 2; x <= 6; x++) level.setBlockAndUpdate(new BlockPos(x, 79, 17), SinguloBlocks.ZERO_GRAVITY_BLOCK.get().defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(7, 79, 17), SinguloBlocks.GRAVITY_PANEL_RECEIVER.get().defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(8, 79, 17), SinguloBlocks.CREATIVE_ENERGY_SOURCE.get().defaultBlockState());
        level.setBlockAndUpdate(new BlockPos(4, 80, 18), SinguloBlocks.ZERO_GRAVITY_CONTROLLER.get().defaultBlockState());
        // armor stands wearing survey glasses at -2, 0 and +3 pixels
        int[] heights = {-2, 0, 3};
        for (int i = 0; i < heights.length; i++) {
            var stand = net.minecraft.world.entity.EntityType.ARMOR_STAND.create(level);
            stand.moveTo(7.5 + i, 80, -3.5, 180, 0);
            stand.setYHeadRot(180);
            stand.setYBodyRot(180);
            ItemStack glasses = item("survey_glasses");
            io.github.genichimaruo.singulo.android.work.SurveyGlassesItem.setHeight(glasses, heights[i]);
            stand.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, glasses);
            level.addFreshEntity(stand);
        }
        var player = level.getServer().getPlayerList().getPlayers().get(0);
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        player.getInventory().clearContent();
        prepared = true;
    }

    private static void placeRoom(ServerLevel level, String name, BlockPos at) {
        var template = level.getStructureManager().getOrCreate(Singulo.id("lab/" + name));
        var size = template.getSize();
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-1, -1, -1), at.offset(size.getX(), size.getY(), size.getZ()))) {
            level.setBlock(p, Blocks.STONE.defaultBlockState(), 2);
        }
        template.placeInWorld(level, at, at, new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(),
                level.getRandom(), 2);
        io.github.genichimaruo.singulo.ruin.lab.LabPower.restore(level, new net.minecraft.world.level.levelgen.structure.BoundingBox(
                at.getX(), at.getY(), at.getZ(), at.getX() + size.getX() - 1, at.getY() + size.getY() - 1, at.getZ() + size.getZ() - 1));
    }

    private static void place(ServerLevel level, BlockPos pos, String id, java.util.function.UnaryOperator<BlockState> extra) {
        var block = BuiltInRegistries.BLOCK.get(Singulo.id(id));
        BlockState lower = extra.apply(block.defaultBlockState());
        if (lower.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            lower = lower.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        }
        level.setBlock(pos, lower.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER), 3);
        level.setBlock(pos.above(), lower.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER), 3);
    }

    private static Path output() throws Exception {
        Path out = Path.of(System.getProperty("singulo.v011Output"));
        Files.createDirectories(out);
        return out;
    }

    private static void capture(Minecraft mc, String name) throws Exception {
        try (var pixels = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            pixels.writeToFile(output().resolve(name + ".png"));
        }
    }

}
