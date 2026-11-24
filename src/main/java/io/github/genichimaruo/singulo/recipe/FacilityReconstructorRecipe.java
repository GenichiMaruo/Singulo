package io.github.genichimaruo.singulo.recipe;

import com.mojang.serialization.MapCodec;
import io.github.genichimaruo.singulo.generated.ServerConfig;
import io.github.genichimaruo.singulo.registry.SinguloRecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;

/** Server config gates both matching and assembly, including direct consumers such as Android crafting. */
public final class FacilityReconstructorRecipe extends ShapedRecipe {
    private FacilityReconstructorRecipe(ShapedRecipe recipe) {
        super(recipe.getGroup(), recipe.category(), recipe.pattern, recipe.getResultItem(null).copy(), recipe.showNotification());
    }

    private static boolean enabled() {
        return !ServerConfig.SPEC.isLoaded() || ServerConfig.RECIPE_ENABLED.get();
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return enabled() && super.matches(input, level);
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        return enabled() ? super.assemble(input, registries) : ItemStack.EMPTY;
    }

    @Override
    public boolean isIncomplete() {
        return !enabled() || super.isIncomplete();
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SinguloRecipes.FACILITY_RECONSTRUCTOR.get();
    }

    public static final class Serializer implements RecipeSerializer<FacilityReconstructorRecipe> {
        private static final MapCodec<FacilityReconstructorRecipe> CODEC =
                ShapedRecipe.Serializer.CODEC.xmap(FacilityReconstructorRecipe::new, r -> r);
        private static final StreamCodec<RegistryFriendlyByteBuf, FacilityReconstructorRecipe> STREAM =
                ShapedRecipe.Serializer.STREAM_CODEC.map(FacilityReconstructorRecipe::new, r -> r);

        @Override public MapCodec<FacilityReconstructorRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, FacilityReconstructorRecipe> streamCodec() { return STREAM; }
    }
}
