package io.github.genichimaruo.singulo.test.harness;

import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;

/** Own resources until the test ends, including assertion failure and timeout. */
public final class TestScope implements GameTestListener {
    private final Deque<Runnable> cleanup = new ArrayDeque<>();
    private boolean closed;

    public static TestScope of(GameTestHelper helper) {
        var scope = new TestScope();
        try { TestFixtures.addCleanupListener(info(helper), scope); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        return scope;
    }

    public static GameTestInfo info(GameTestHelper helper) {
        try {
            var field = java.util.Arrays.stream(GameTestHelper.class.getDeclaredFields())
                    .filter(f -> f.getType() == GameTestInfo.class).findFirst().orElseThrow();
            field.setAccessible(true);
            return (GameTestInfo) field.get(helper);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }

    public void defer(Runnable action) {
        if (closed) throw new IllegalStateException("Test scope already closed");
        cleanup.push(action);
    }

    @SuppressWarnings("removal")
    public static ServerPlayer player(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = new ServerPlayer(level.getServer(), level,
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "test-mock-player"));
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        var channel = new io.netty.channel.embedded.EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player);
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        var pos = helper.absolutePos(net.minecraft.core.BlockPos.ZERO);
        player.teleportTo(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0, 0);
        of(helper).defer(channel::finishAndReleaseAll);
        return ownPlayer(helper, player);
    }

    public static ServerPlayer ownPlayer(GameTestHelper helper, ServerPlayer player) {
        of(helper).defer(() -> {
            // discard() removes the entity but leaves vanilla's login/player list populated.
            if (player.getServer().getPlayerList().getPlayers().contains(player))
                player.getServer().getPlayerList().remove(player);
            player.discard();
        });
        return player;
    }

    public void close() {
        if (closed) return;
        closed = true;
        RuntimeException error = null;
        while (!cleanup.isEmpty()) {
            try { cleanup.pop().run(); }
            catch (RuntimeException e) {
                if (error == null) error = e; else error.addSuppressed(e);
            }
        }
        if (error != null) throw error;
    }

    @Override public void testStructureLoaded(GameTestInfo test) {}
    @Override public void testPassed(GameTestInfo test) { close(); }
    @Override public void testFailed(GameTestInfo test) { close(); }
}
