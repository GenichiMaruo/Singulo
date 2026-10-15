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
