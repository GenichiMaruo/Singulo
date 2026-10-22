package io.github.genichimaruo.singulo.test;

import io.github.genichimaruo.singulo.Singulo;
import io.github.genichimaruo.singulo.generated.ServerConfig;
import io.github.genichimaruo.singulo.machine.*;
import io.github.genichimaruo.singulo.recipe.*;
import io.github.genichimaruo.singulo.registry.SinguloRecipes;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(Singulo.MODID)
@PrefixGameTestTemplate(false)
public final class RecipeIndexGameTests {
    private static List<RecipeHolder<MachineRecipe>> legacy(GameTestHelper h, MachineType type) {
        var raw = h.getLevel().getRecipeManager().getAllRecipesFor(SinguloRecipes.MACHINE.get()).stream()
                .filter(r -> r.value().station().equals(type.id())).toList();
        var out = RecipeTweaks.adjustAll(raw);
        out.sort(Comparator.comparingInt((RecipeHolder<MachineRecipe> r) -> -r.value().priority())
                .thenComparingInt(r -> -(r.value().ingredients().size() + r.value().fluidIngredients().size()
                        + (r.value().restore().isPresent() ? 1 : 0))).thenComparing(r -> r.id().toString()));
        return out;
    }

    @GameTest(template = "empty")
    public static void indexedOrderAndValuesMatchExistingSelection(GameTestHelper h) {
        for (MachineType type : MachineType.values()) {
            var actual = MachineBlockEntity.stationRecipes(h.getLevel(), type);
            var expected = legacy(h, type);
            h.assertTrue(actual.equals(expected), "Recipe priority, values or order changed for " + type);
            h.assertTrue(MachineBlockEntity.massRecipes(h.getLevel(), type).equals(expected.stream()
                    .filter(r -> r.value().isMassRecipe()).sorted(Comparator.comparing(r -> r.id().toString())).toList()),
                    "Mass recipe order changed for " + type);
        }
        // Record query time; correctness never depends on a noisy wall-clock threshold.
        var type = MachineType.PRECISION_ASSEMBLER;
        var cached = MachineBlockEntity.stationRecipes(h.getLevel(), type);
        long start = System.nanoTime();
        for (int i = 0; i < 10_000; i++)
            h.assertTrue(MachineBlockEntity.stationRecipes(h.getLevel(), type) == cached, "Repeated queries rebuild lists");
        long indexed = System.nanoTime() - start;
        start = System.nanoTime();
        for (int i = 0; i < 10_000; i++) legacy(h, type);
        Singulo.LOGGER.info("SINGULO_RECIPE_INDEX_BENCH queries=10000 indexedNs={} previousNs={}", indexed, System.nanoTime() - start);
        h.succeed();
    }

    @GameTest(template = "empty", batch = "recipe_reload")
    public static void indexedListsFollowReplacementAndRestoration(GameTestHelper h) {
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
