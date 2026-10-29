package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.test.harness.TestScope;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.*;
import net.minecraftforge.gametest.*;

@GameTestHolder("singulo")
@PrefixGameTestTemplate(false)
public final class HarnessGameTests {
    @GameTest(template = "empty")
    public static void scopeCleansOnSuccessAndOnlyOnce(GameTestHelper h) {
        var scope = new TestScope(); var calls = new ArrayList<Integer>();
        scope.defer(() -> calls.add(1)); scope.defer(() -> calls.add(2));
        scope.testPassed(TestScope.info(h)); scope.close();
        h.assertTrue(calls.equals(List.of(2, 1)), "Cleanup was not reverse-order/idempotent");
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void scopeCleansRemainingResourcesWhenCleanupThrows(GameTestHelper h) {
        var scope = new TestScope(); var calls = new ArrayList<Integer>();
        scope.defer(() -> calls.add(1)); scope.defer(() -> { throw new IllegalStateException("fixture"); });
        try {
            scope.testFailed(TestScope.info(h));
            h.fail("Cleanup error was swallowed");
        } catch (IllegalStateException expected) {
            h.assertTrue(calls.equals(List.of(1)), "One failing cleanup skipped the others");
        }
        scope.close(); h.succeed();
    }

    @GameTest(template = "empty")
    public static void failedFixtureReleasesItsLoggedInPlayer(GameTestHelper h) {
        var original = TestScope.info(h);
        var info = new GameTestInfo(original.getTestFunction(), original.getRotation(), h.getLevel());
        var fixture = new GameTestHelper(info);
        var player = TestScope.ownPlayer(fixture, TestBuild.mockPlayer(fixture));
        h.assertTrue(h.getLevel().players().contains(player), "Player was not registered in the level");
        // Invoke the failure callback registered on a separate fixture, without failing this test.
        try {
            var field = java.util.Arrays.stream(GameTestInfo.class.getDeclaredFields())
                    .filter(f -> f.getGenericType() instanceof java.lang.reflect.ParameterizedType type
                        && java.util.Arrays.asList(type.getActualTypeArguments()).contains(GameTestListener.class))
                    .findFirst().orElseThrow();
            field.setAccessible(true);
            @SuppressWarnings("unchecked") var listeners = (List<GameTestListener>) field.get(info);
            try { listeners.forEach(listener -> listener.testFailed(info)); }
            finally { listeners.forEach(listener -> listener.testFailed(info)); }
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
        h.assertTrue(!h.getLevel().players().contains(player) && player.isRemoved(),
                "Failure cleanup retained a player/entity");
        h.succeed();
    }
}
