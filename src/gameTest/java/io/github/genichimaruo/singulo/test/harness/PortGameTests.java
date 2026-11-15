package io.github.genichimaruo.singulo.test.harness;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Development-only registry migration. Reflection preserves compiler-evaluated timeout constants exactly. */
@EventBusSubscriber(modid = "singulo")
public final class PortGameTests {
    public record Definition(Identifier id, Identifier template, Identifier environment, String batch, int timeout, Method method) { }
    private static final class Definitions { static final List<Definition> ALL = read(); }
    public static List<Definition> definitions() { return Definitions.ALL; }
    private static List<Definition> read() {
        var stream = PortGameTests.class.getResourceAsStream("/singulo-test-suites.list");
        if (stream == null) throw new IllegalStateException("Missing generated GameTest suite inventory");
        var out = new ArrayList<Definition>();
        var ids = new HashSet<Identifier>();
        try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            for (String name : reader.lines().filter(s -> !s.isBlank()).toList()) {
                var suite = Class.forName(name, false, PortGameTests.class.getClassLoader());
                var holder = suite.getAnnotation(GameTestHolder.class);
                String namespace = holder == null ? "singulo" : holder.value();
                String enabled = System.getProperty("neoforge.enabledGameTestNamespaces", "");
                if (!enabled.isBlank() && !java.util.Arrays.asList(enabled.split(",")).contains(namespace)) continue;
                for (Method method : suite.getDeclaredMethods()) {
                    var annotation = method.getAnnotation(GameTest.class);
                    if (annotation == null) continue;
                    if (!Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 1 || method.getParameterTypes()[0] != GameTestHelper.class)
                        throw new IllegalStateException("Invalid GameTest signature: " + method);
                    var id = Identifier.fromNamespaceAndPath(namespace, (suite.getSimpleName() + "." + method.getName()).toLowerCase(Locale.ROOT));
                    if (!ids.add(id)) throw new IllegalStateException("Duplicate GameTest id: " + id);
                    String template = annotation.template();
                    var prefix = method.getAnnotation(PrefixGameTestTemplate.class);
                    if (prefix == null) prefix = suite.getAnnotation(PrefixGameTestTemplate.class);
                    if (template.isEmpty()) template = method.getName().toLowerCase(Locale.ROOT);
                    if ((prefix == null || prefix.value()) && !template.contains(":")) template = suite.getSimpleName().toLowerCase(Locale.ROOT) + "." + template;
                    var templateId = template.contains(":") ? Identifier.parse(template) : Identifier.fromNamespaceAndPath(namespace, template);
                    // Separate environments preserve legacy batch isolation, not just names in a report.
                    var environment = Identifier.fromNamespaceAndPath(namespace, "batch/" + annotation.batch().toLowerCase(Locale.ROOT));
                    out.add(new Definition(id, templateId, environment, annotation.batch(), annotation.timeoutTicks(), method));
                }
            }
        } catch (Exception e) { throw new IllegalStateException("Cannot load GameTest inventory", e); }
        out.sort(Comparator.comparing(d -> d.id().toString()));
        return List.copyOf(out);
    }
    @SubscribeEvent public static void functions(RegisterEvent event) {
        event.register(Registries.TEST_ENVIRONMENT_DEFINITION_TYPE, helper -> helper.register(
            Identifier.fromNamespaceAndPath("singulo", "legacy_batch"), LegacyBatchEnvironment.CODEC));
        event.register(Registries.TEST_FUNCTION, helper -> {
            for (var test : definitions()) helper.register(test.id(), (Consumer<GameTestHelper>) context -> {
                try { test.method().invoke(null, context); }
                catch (ReflectiveOperationException e) {
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    if (cause instanceof RuntimeException runtime) throw runtime;
                    if (cause instanceof AssertionError assertion) throw new RuntimeException(assertion.getMessage(), assertion);
                    if (cause instanceof Error error) throw error;
                    throw new RuntimeException("GameTest failed: " + test.id() + ": " + cause.getMessage(), cause);
                }
            });
        });
    }
    @SubscribeEvent public static void tests(RegisterGameTestsEvent event) {
        var environments = new HashMap<Identifier, net.minecraft.core.Holder<net.minecraft.gametest.framework.TestEnvironmentDefinition<?>>>();
        for (var test : definitions()) {
            var environment = environments.computeIfAbsent(test.environment(), id -> event.registerEnvironment(id, new LegacyBatchEnvironment(id.toString())));
            event.registerTest(test.id(), new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, test.id()),
                new TestData<>(environment, test.template(), test.timeout(), 0, true)));
        }
    }
}
