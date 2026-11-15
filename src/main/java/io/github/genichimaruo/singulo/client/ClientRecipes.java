package io.github.genichimaruo.singulo.client;

import net.minecraft.world.item.crafting.RecipeMap;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;

/** Server-authoritative recipe data, refreshed by NeoForge on login and datapack reload. */
public final class ClientRecipes {
    private ClientRecipes() {}
    public static RecipeMap recipes() { return io.github.genichimaruo.singulo.recipe.SyncedRecipes.recipes(); }
    static void receive(RecipesReceivedEvent event) { io.github.genichimaruo.singulo.recipe.SyncedRecipes.receive(event.getRecipeMap()); }
    static void clear() { io.github.genichimaruo.singulo.recipe.SyncedRecipes.clear(); }
}