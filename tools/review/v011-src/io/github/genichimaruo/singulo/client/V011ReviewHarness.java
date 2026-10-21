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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Development-only capture of the 0.11.0 visuals (tools/review/v011.gradle): horizon bus power effects, vending machine sides,
 * compressed two-block held models. Writes PNGs to singulo.v011Output and stops the client.
 */
@EventBusSubscriber(modid = Singulo.MODID, value = Dist.CLIENT)
public final class V011ReviewHarness {
    /** A camera shot: where to stand, what to look at, what to hold, and the camera type. */
    record Shot(String name, Vec3 eye, Vec3 look, String main, String off, CameraType camera, int settle) {}

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
    }

    /** Client-side cable glow for the three flow levels (rows z = 0, 2, 4), re-applied every tick. */
    private static final long[] FLOWS = {50_000_000L, 300_000_000L, 3_000_000_000L};

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) throws Exception {
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
            mc.options.hideGui = shot.main().isEmpty() && shot.off().isEmpty();
            mc.options.fov().set(70);
            mc.options.setCameraType(shot.camera());
            var server = mc.getSingleplayerServer();
            server.execute(() -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.setItemInHand(InteractionHand.MAIN_HAND, item(shot.main()));
                player.setItemInHand(InteractionHand.OFF_HAND, item(shot.off()));
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
            if (wait >= shot.settle() + 2) {
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
        for (int x = -4; x <= 22; x++) {
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
        var player = level.getServer().getPlayerList().getPlayers().getFirst();
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        player.getInventory().clearContent();
        prepared = true;
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
