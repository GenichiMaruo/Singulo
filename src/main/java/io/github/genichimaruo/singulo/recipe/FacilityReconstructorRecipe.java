package io.github.genichimaruo.singulo.recipe;

import com.google.gson.JsonObject;
import io.github.genichimaruo.singulo.generated.ServerConfig;
import io.github.genichimaruo.singulo.registry.SinguloRecipes;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;

/** Server config gates both matching and assembly, including direct consumers such as Android crafting. */
public final class FacilityReconstructorRecipe extends ShapedRecipe {
    private FacilityReconstructorRecipe(ShapedRecipe recipe) {
        super(recipe.getId(), recipe.getGroup(), recipe.category(), recipe.getRecipeWidth(), recipe.getRecipeHeight(),
                recipe.getIngredients(), recipe.getResultItem(null).copy(), recipe.showNotification());
    }

    private static boolean enabled() {
        return !ServerConfig.SPEC.isLoaded() || ServerConfig.RECIPE_ENABLED.get();
    }

    @Override public boolean matches(CraftingContainer input, Level level) {
        return enabled() && super.matches(input, level);
    }

    @Override public ItemStack assemble(CraftingContainer input, RegistryAccess registries) {
        return enabled() ? super.assemble(input, registries) : ItemStack.EMPTY;
    }

    @Override public boolean isIncomplete() { return !enabled() || super.isIncomplete(); }

    @Override public RecipeSerializer<?> getSerializer() { return SinguloRecipes.FACILITY_RECONSTRUCTOR.get(); }

    public static final class Serializer implements RecipeSerializer<FacilityReconstructorRecipe> {
        private final ShapedRecipe.Serializer delegate = new ShapedRecipe.Serializer();
        @Override public FacilityReconstructorRecipe fromJson(ResourceLocation id, JsonObject json) {
            return new FacilityReconstructorRecipe(delegate.fromJson(id, json));
        }
        @Override public FacilityReconstructorRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            return new FacilityReconstructorRecipe(delegate.fromNetwork(id, buf));
        }
        @Override public void toNetwork(FriendlyByteBuf buf, FacilityReconstructorRecipe recipe) {
            delegate.toNetwork(buf, recipe);
        }
    }
}
