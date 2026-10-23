package io.github.genichimaruo.singulo.machine;

import io.github.genichimaruo.singulo.recipe.MachineRecipe;
import io.github.genichimaruo.singulo.recipe.RecipeTweaks;
import io.github.genichimaruo.singulo.registry.SinguloRecipes;
import java.util.*;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;

/** Immutable station lists shared by machines and menus, rebuilt after recipe/configuration changes. */
public final class MachineRecipeIndex {
    private MachineRecipeIndex() {}
    private static final Map<RecipeManager, Snapshot> CACHE = new WeakHashMap<>();
    private record Lists(List<RecipeHolder<MachineRecipe>> station, List<RecipeHolder<MachineRecipe>> mass,
                         List<RecipeHolder<MachineRecipe>> selectable) {}
    // Values contain recipes only: never keep the weak key (or a Level) alive through the value.
    private record Snapshot(Object recipes, long revision, Map<MachineType, Lists> lists) {}
    private static final Comparator<RecipeHolder<MachineRecipe>> ID_ORDER = Comparator.comparing(h -> h.id().toString());
    private static final Comparator<RecipeHolder<MachineRecipe>> STATION_ORDER = Comparator
            .comparingInt((RecipeHolder<MachineRecipe> h) -> -h.value().priority())
            .thenComparingInt(h -> -(h.value().ingredients().size() + h.value().fluidIngredients().size()
                    + (h.value().restore().isPresent() ? 1 : 0))).thenComparing(ID_ORDER);

    static synchronized Object snapshot(Level level) { return cached(level); }

    private static Snapshot cached(Level level) {
        RecipeManager manager = level.getRecipeManager();
        Object recipes = manager.getRecipes();
        long revision = RecipeTweaks.revision();
        Snapshot known = CACHE.get(manager);
        if (known != null && known.recipes == recipes && known.revision == revision) return known;
        Map<String, List<RecipeHolder<MachineRecipe>>> byStation = new HashMap<>();
        for (var h : manager.getAllRecipesFor(SinguloRecipes.MACHINE.get()))
            byStation.computeIfAbsent(h.value().station(), k -> new ArrayList<>()).add(h);
        Map<MachineType, Lists> lists = new EnumMap<>(MachineType.class);
        for (MachineType type : MachineType.values()) {
            var station = RecipeTweaks.adjustAll(byStation.getOrDefault(type.id(), List.of()));
            station.sort(STATION_ORDER);
            List<RecipeHolder<MachineRecipe>> mass = new ArrayList<>(), selectable = new ArrayList<>();
            for (var h : station) {
                if (h.value().isMassRecipe()) mass.add(h);
                else if (!MachineBlockEntity.isFree(h.value())) selectable.add(h);
            }
            mass.sort(ID_ORDER);
            selectable.sort(ID_ORDER);
            lists.put(type, new Lists(List.copyOf(station), List.copyOf(mass), List.copyOf(selectable)));
        }
        known = new Snapshot(recipes, revision, lists);
        CACHE.put(manager, known);
        return known;
    }

    public static synchronized List<RecipeHolder<MachineRecipe>> station(Level level, MachineType type) {
        return cached(level).lists.get(type).station;
    }
    public static synchronized List<RecipeHolder<MachineRecipe>> mass(Level level, MachineType type) {
        return cached(level).lists.get(type).mass;
    }
    public static synchronized List<RecipeHolder<MachineRecipe>> selectable(Level level, MachineType type) {
        return cached(level).lists.get(type).selectable;
    }
    public static synchronized void clear() { CACHE.clear(); }
}
