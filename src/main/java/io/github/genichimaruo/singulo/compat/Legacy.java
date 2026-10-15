package io.github.genichimaruo.singulo.compat;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.fluids.FluidStack;

public final class Legacy {
    public static Optional<BlockPos> readBlockPos(CompoundTag tag, String key) {
        return tag.contains(key, 10) ? Optional.of(net.minecraft.nbt.NbtUtils.readBlockPos(tag.getCompound(key))) : Optional.empty();
    }
    public static FluidStack copyWithAmount(FluidStack stack, int amount) {
        FluidStack copy = stack.copy(); copy.setAmount(amount); return copy;
    }
    @SuppressWarnings("unchecked")
    public static <T extends net.minecraft.world.level.block.entity.BlockEntity> T blockEntity(net.minecraft.gametest.framework.GameTestHelper helper, BlockPos pos) {
        return (T)helper.getBlockEntity(pos);
    }
    public static java.util.UUID uuid(net.minecraft.resources.ResourceLocation id) {
        return java.util.UUID.nameUUIDFromBytes(id.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    public static boolean sameFluid(net.minecraftforge.fluids.FluidStack a, net.minecraftforge.fluids.FluidStack b) { return a.isFluidEqual(b); }
    /** Completed machines and hostiles must not affect later fixtures in their search radius. */
    public static void isolateFixture(net.minecraft.gametest.framework.GameTestHelper helper) {
        try {
            var field = java.util.Arrays.stream(net.minecraft.gametest.framework.GameTestHelper.class.getDeclaredFields())
                    .filter(f -> f.getType() == net.minecraft.gametest.framework.GameTestInfo.class).findFirst().orElseThrow();
            field.setAccessible(true);
            var info = (net.minecraft.gametest.framework.GameTestInfo) field.get(helper);
            var bounds = info.getStructureBounds();
            info.addListener(new net.minecraft.gametest.framework.GameTestListener() {
                public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo test) {}
                public void testPassed(net.minecraft.gametest.framework.GameTestInfo test) { cleanup(); }
                public void testFailed(net.minecraft.gametest.framework.GameTestInfo test) { cleanup(); }
                private void cleanup() {
                    var level = helper.getLevel();
                    // Keep forced chunks intact: another simultaneously running fixture may share one.
                    int minX = net.minecraft.util.Mth.floor(bounds.minX) >> 4;
                    int maxX = net.minecraft.util.Mth.floor(bounds.maxX) >> 4;
                    int minZ = net.minecraft.util.Mth.floor(bounds.minZ) >> 4;
                    int maxZ = net.minecraft.util.Mth.floor(bounds.maxZ) >> 4;
                    for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) {
                        var chunk = level.getChunkSource().getChunkNow(x, z);
                        if (chunk == null) continue;
                        for (var blockEntity : new java.util.ArrayList<>(chunk.getBlockEntities().values())) {
                            if (bounds.contains(net.minecraft.world.phys.Vec3.atCenterOf(blockEntity.getBlockPos()))) {
                                // Remove the inventory before the block's onRemove hook can
                                // drop test cargo into a later reactor's gravity field.
                                level.removeBlockEntity(blockEntity.getBlockPos());
                                level.removeBlock(blockEntity.getBlockPos(), false);
                            }
                        }
                    }
                    for (var entity : level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, bounds)) {
                        if (!(entity instanceof net.minecraft.world.entity.player.Player)) entity.discard();
                    }
                }
            });
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Cannot isolate GameTest fixture", ex);
        }
    }

    private Legacy() {}
    /** Forge 1.20.1's vanilla GameTest mock lacks a channel before player login hooks run. */
    public static net.minecraft.server.level.ServerPlayer mockServerPlayer(net.minecraft.gametest.framework.GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var player = new net.minecraft.server.level.ServerPlayer(server, helper.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "test-mock-player"));
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player);
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        var pos = helper.absolutePos(BlockPos.ZERO);
        player.teleportTo(helper.getLevel(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        // Keep a completed fixture's player out of later boss participant counts and inventories.
        try {
            var field = java.util.Arrays.stream(net.minecraft.gametest.framework.GameTestHelper.class.getDeclaredFields())
                    .filter(f -> f.getType() == net.minecraft.gametest.framework.GameTestInfo.class).findFirst().orElseThrow();
            field.setAccessible(true);
            var info = (net.minecraft.gametest.framework.GameTestInfo) field.get(helper);
            info.addListener(new net.minecraft.gametest.framework.GameTestListener() {
                public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo test) {}
                public void testPassed(net.minecraft.gametest.framework.GameTestInfo test) { cleanup(); }
                public void testFailed(net.minecraft.gametest.framework.GameTestInfo test) { cleanup(); }
                private void cleanup() {
                    // Automatic orders search nearby workers. Stop this completed fixture before
                    // the next batch places another workshop inside that search radius.
                    var level = helper.getLevel();
                    var origin = helper.absolutePos(net.minecraft.core.BlockPos.ZERO);
                    var bounds = new net.minecraft.world.phys.AABB(origin, helper.absolutePos(new net.minecraft.core.BlockPos(8, 8, 8)));
                    for (var android : level.getEntitiesOfClass(io.github.genichimaruo.singulo.android.RuinAndroid.class, bounds)) {
                        android.discard();
                    }
                    for (var pos : net.minecraft.core.BlockPos.betweenClosed(origin, helper.absolutePos(new net.minecraft.core.BlockPos(7, 7, 7)))) {
                        if (level.getBlockEntity(pos) instanceof io.github.genichimaruo.singulo.android.work.WorkConsoleBlockEntity console) {
                            console.orders().clear();
                            console.standing().clear();
                        }
                    }
                    server.getPlayerList().remove(player);
                    if (connection.channel() instanceof io.netty.channel.embedded.EmbeddedChannel channel) channel.finishAndReleaseAll();
                }
            });
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot register GameTest mock player cleanup", e);
        }
        return player;
    }
}
