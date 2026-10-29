package io.github.genichimaruo.singulo.test.harness;

import com.google.gson.GsonBuilder;
import io.github.genichimaruo.singulo.Singulo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.gametest.framework.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.TickEvent.ServerTickEvent;

/** Development-only, per-test evidence. Missing/duplicate results fail validation. */
@EventBusSubscriber(modid = Singulo.MODID)
public final class TestReports implements TestReporter {
    private static TestReports active;
    private long lastProgress = System.nanoTime();
    private final List<Result> results = new ArrayList<>();
    private final List<String> expected;
    private final Path output;
    private final long started = System.nanoTime();
    private final TestReporter delegate = new LogTestReporter();

    private TestReports(Path output) {
        this.output = output;
        expected = GameTestRegistry.getAllTestFunctions().stream().map(TestFunction::getTestName).sorted().toList();
    }

    @SubscribeEvent
    public static void start(ServerStartedEvent event) {
        String output = System.getProperty("singulo.testReport");
        if (event.getServer() instanceof GameTestServer && output != null) {
            active = new TestReports(Path.of(output));
            GlobalTestReporter.replaceWith(active);
        }
    }

    @SubscribeEvent
    public static void watch(ServerTickEvent event) {
        // Vanilla timeout counting only begins after structure chunks are ticking.
        // A lost chunk ticket can otherwise wait forever without a timeout failure.
        if (active != null && event.getServer() instanceof GameTestServer
                && System.nanoTime() - active.lastProgress > 120_000_000_000L) {
            throw new IllegalStateException("GameTests made no completion progress for 120 seconds; inspect chunk tickets and fixture cleanup");
        }
    }

    @SubscribeEvent
    public static void stop(ServerStoppedEvent event) {
        if (event.getServer() instanceof GameTestServer) active = null;
    }

    private record Result(String name, String batch, String template, boolean required,
                          String status, long millis, int timeoutTicks) {}
    private record Report(int schema, boolean complete, boolean benchmarks, long elapsedMillis,
                          List<String> expected, List<Result> results) {}

    private void record(GameTestInfo test, String status) {
        lastProgress = System.nanoTime();
        results.add(new Result(test.getTestName(), test.getTestFunction().getBatchName(), test.getStructureName(),
                test.isRequired(), status, test.getRunTime(), test.getTimeoutTicks()));
    }

    @Override public void onTestSuccess(GameTestInfo test) { record(test, "passed"); delegate.onTestSuccess(test); }
    @Override public void onTestFailed(GameTestInfo test) { record(test, "failed"); delegate.onTestFailed(test); }

    @Override public void finish() {
        results.sort(Comparator.comparing(Result::name));
        try {
            Files.createDirectories(output.toAbsolutePath().getParent());
            Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(new Report(
                    1, true, Boolean.getBoolean("singulo.testBenchmarks"), (System.nanoTime() - started) / 1_000_000,
                    expected, results)));
        } catch (IOException e) { throw new IllegalStateException("Cannot write GameTest evidence", e); }
        delegate.finish();
    }
}
