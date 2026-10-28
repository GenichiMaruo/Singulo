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
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Development-only, per-test evidence. Missing/duplicate results fail validation. */
@EventBusSubscriber(modid = Singulo.MODID)
public final class TestReports implements TestReporter {
    private final List<Result> results = new ArrayList<>();
    private final List<String> expected;
    private final Path output;
    private final long started = System.nanoTime();
    private final TestReporter delegate = new LogTestReporter();

    private TestReports(Path output) {
        this.output = output;
        expected = GameTestRegistry.getAllTestFunctions().stream().map(TestFunction::testName).sorted().toList();
    }

    @SubscribeEvent
    public static void start(ServerStartedEvent event) {
        String output = System.getProperty("singulo.testReport");
        if (event.getServer() instanceof GameTestServer && output != null) {
            GlobalTestReporter.replaceWith(new TestReports(Path.of(output)));
        }
    }

    private record Result(String name, String batch, String template, boolean required,
                          String status, long millis, int timeoutTicks) {}
    private record Report(int schema, boolean complete, boolean benchmarks, long elapsedMillis,
                          List<String> expected, List<Result> results) {}

    private void record(GameTestInfo test, String status) {
        results.add(new Result(test.getTestName(), test.getTestFunction().batchName(), test.getStructureName(),
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
