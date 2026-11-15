package io.github.genichimaruo.singulo.recipe;

import net.minecraft.world.item.crafting.RecipeMap;

/** Client recipe snapshot with no client-only types; safe for common menu code to link. */
public final class SyncedRecipes {
    private static volatile RecipeMap recipes = RecipeMap.EMPTY;
    private SyncedRecipes() {}
    public static RecipeMap recipes() { return recipes; }
    public static void receive(RecipeMap value) { recipes = java.util.Objects.requireNonNull(value); }
    public static void clear() { recipes = RecipeMap.EMPTY; }
}
