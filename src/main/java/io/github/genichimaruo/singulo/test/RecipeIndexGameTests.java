package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.generated.ServerConfig;
import io.github.genichimaruo.singulo.machine.*;
import io.github.genichimaruo.singulo.recipe.*;
import io.github.genichimaruo.singulo.registry.SinguloRecipes;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraftforge.gametest.*;

@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class RecipeIndexGameTests {
    @GameTest(template = "empty")
    public static void equalRecipesKeepIndependentIdsAcrossLogins(GameTestHelper h) {
        var first = new MachineRecipe("cryogenic_cooling_tower", 2, 20, 10, List.of(), List.of(),
                Optional.empty(), Optional.empty(), net.minecraft.world.item.ItemStack.EMPTY, List.of(), 0, 0,
                net.minecraft.world.item.ItemStack.EMPTY);
        var second = new MachineRecipe(first.station(), first.stage(), first.time(), first.energy(),
                first.ingredients(), first.fluidIngredients(), first.mass(), first.restore(), first.result(),
                first.fluidResults(), first.minSize(), first.priority(), first.byproduct());
        h.assertTrue(first.equals(second), "Fixture must reproduce equal recipe instances");
        var id1 = Singulo.id("id_login_one");
        var id2 = Singulo.id("id_login_two");
        first.withId(id1); second.withId(id2);
        h.assertTrue(first.getId().equals(id1) && second.getId().equals(id2),
                "Equal recipe instances overwrite each other's ids across logins");
        h.succeed();
    }

    private static List<MachineRecipe> legacy(GameTestHelper h, MachineType type) {
        var raw = h.getLevel().getRecipeManager().getAllRecipesFor(SinguloRecipes.MACHINE.get()).stream()
                .filter(r -> r.value().station().equals(type.id())).toList();
        var out = RecipeTweaks.adjustAll(raw);
        out.sort(Comparator.comparingInt((MachineRecipe r) -> -r.value().priority())
                .thenComparingInt(r -> -(r.value().ingredients().size() + r.value().fluidIngredients().size()
                        + (r.value().restore().isPresent() ? 1 : 0))).thenComparing(r -> r.id().toString()));
        return out;
    }

    @GameTest(template = "empty")
    public static void indexedOrderAndValuesMatchExistingSelection(GameTestHelper h) {
        io.github.genichimaruo.singulo.test.harness.TestFixtures.isolateFixture(h);
        for (MachineType type : MachineType.values()) {
            var actual = MachineBlockEntity.stationRecipes(h.getLevel(), type);
            var expected = legacy(h, type);
            h.assertTrue(actual.equals(expected), "Recipe priority, values or order changed for " + type);
            h.assertTrue(MachineBlockEntity.massRecipes(h.getLevel(), type).equals(expected.stream()
                    .filter(r -> r.value().isMassRecipe()).sorted(Comparator.comparing(r -> r.id().toString())).toList()),
                    "Mass recipe order changed for " + type);
        }
        // Reuse is a correctness invariant; expensive timing comparisons are opt-in.
        var stable = MachineBlockEntity.stationRecipes(h.getLevel(), MachineType.PRECISION_ASSEMBLER);
        for (int i = 0; i < 3; i++) h.assertTrue(MachineBlockEntity.stationRecipes(h.getLevel(), MachineType.PRECISION_ASSEMBLER) == stable,
                "Repeated queries rebuild lists");
        if (Boolean.getBoolean("singulo.testBenchmarks")) {
            var type = MachineType.PRECISION_ASSEMBLER;
            var cached = MachineBlockEntity.stationRecipes(h.getLevel(), type);
            long start = System.nanoTime();
            for (int i = 0; i < 10_000; i++)
                h.assertTrue(MachineBlockEntity.stationRecipes(h.getLevel(), type) == cached, "Repeated queries rebuild lists");
            long indexed = System.nanoTime() - start;
            start = System.nanoTime();
            for (int i = 0; i < 10_000; i++) legacy(h, type);
            Singulo.LOGGER.info("SINGULO_RECIPE_INDEX_BENCH queries=10000 indexedNs={} previousNs={}", indexed, System.nanoTime() - start);
        }
        h.succeed();
    }

    @GameTest(template = "empty", batch = "recipe_reload")
    public static void indexedListsFollowReplacementAndRestoration(GameTestHelper h) {
        io.github.genichimaruo.singulo.test.harness.TestFixtures.isolateFixture(h);
        var manager = h.getLevel().getRecipeManager();
        var original = List.copyOf(manager.getRecipes());
        var before = MachineBlockEntity.stationRecipes(h.getLevel(), MachineType.KILN);
        h.assertTrue(!before.isEmpty(), "Missing kiln recipes");
        try {
            manager.replaceRecipes(List.of());
            h.assertTrue(MachineBlockEntity.stationRecipes(h.getLevel(), MachineType.KILN).isEmpty(), "Reload kept removed recipes");
        } finally { manager.replaceRecipes(original); }
        h.assertTrue(MachineBlockEntity.stationRecipes(h.getLevel(), MachineType.KILN).equals(before), "Recipes not restored");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "recipe_index_config")
    public static void indexedValuesFollowConfigurationChanges(GameTestHelper h) {
        io.github.genichimaruo.singulo.test.harness.TestFixtures.isolateFixture(h);
        int old = ServerConfig.TIME_CRYSTAL_GROWTH_TICKS.get();
        try {
            var before = MachineBlockEntity.stationRecipes(h.getLevel(), MachineType.TIME_CRYSTAL_INCUBATOR);
            ServerConfig.TIME_CRYSTAL_GROWTH_TICKS.set(1234);
            RecipeTweaks.invalidate();
            var changed = MachineBlockEntity.stationRecipes(h.getLevel(), MachineType.TIME_CRYSTAL_INCUBATOR);
            h.assertTrue(changed != before && changed.equals(legacy(h, MachineType.TIME_CRYSTAL_INCUBATOR)), "Changed config kept old recipe values");
            h.assertTrue(changed.stream().anyMatch(r -> r.value().time() == 1234), "New growth time absent");
        } finally {
            ServerConfig.TIME_CRYSTAL_GROWTH_TICKS.set(old);
            RecipeTweaks.invalidate();
        }
        h.succeed();
    }
}
