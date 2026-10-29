package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.test.harness.TestScope;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("singulo")
@PrefixGameTestTemplate(false)
public final class HarnessGameTests {
    @GameTest(template = "empty")
    public static void scopeCleansOnSuccessAndOnlyOnce(GameTestHelper h) {
        var scope = new TestScope(); var calls = new ArrayList<Integer>();
        scope.defer(() -> calls.add(1)); scope.defer(() -> calls.add(2));
        scope.testPassed(h.testInfo, null); scope.close();
        h.assertTrue(calls.equals(List.of(2, 1)), "Cleanup was not reverse-order/idempotent");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void scopeCleansRemainingResourcesWhenCleanupThrows(GameTestHelper h) {
        var scope = new TestScope(); var calls = new ArrayList<Integer>();
        scope.defer(() -> calls.add(1)); scope.defer(() -> { throw new IllegalStateException("fixture"); });
        try {
            scope.testFailed(h.testInfo, null);
            h.fail("Cleanup error was swallowed");
        } catch (IllegalStateException expected) {
            h.assertTrue(calls.equals(List.of(1)), "One failing cleanup skipped the others");
        }
        scope.close(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void failedFixtureReleasesItsLoggedInPlayer(GameTestHelper h) {
        var info = h.testInfo.copyReset();
        var fixture = new GameTestHelper(info);
        var player = TestScope.player(fixture);
        try {
            h.assertTrue(h.getLevel().getServer().getPlayerList().getPlayer(player.getUUID()) == player,
                    "Player was not actually logged in");
            // Exercise the same listener used by assertion failures and timeouts, without failing this test.
            info.getListeners().forEach(listener -> listener.testFailed(info, null));
            h.assertTrue(h.getLevel().getServer().getPlayerList().getPlayer(player.getUUID()) == null && player.isRemoved(),
                    "Failure cleanup retained a player/entity");
        } finally { info.getListeners().forEach(listener -> listener.testFailed(info, null)); }
        h.succeed();
    }
}
