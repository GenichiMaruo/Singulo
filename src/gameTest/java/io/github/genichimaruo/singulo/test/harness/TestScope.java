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
        helper.testInfo.addListener(scope);
        return scope;
    }

    public void defer(Runnable action) {
        if (closed) throw new IllegalStateException("Test scope already closed");
        cleanup.push(action);
    }

    @SuppressWarnings("removal")
    public static ServerPlayer player(GameTestHelper helper) {
        return ownPlayer(helper, helper.makeMockServerPlayerInLevel());
    }

    public static ServerPlayer ownPlayer(GameTestHelper helper, ServerPlayer player) {
        of(helper).defer(() -> {
            // discard() removes the entity but leaves vanilla's login/player list populated.
            if (player.getServer().getPlayerList().getPlayer(player.getUUID()) == player)
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
    @Override public void testPassed(GameTestInfo test, GameTestRunner runner) { close(); }
    @Override public void testFailed(GameTestInfo test, GameTestRunner runner) { close(); }
    @Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { close(); }
}
