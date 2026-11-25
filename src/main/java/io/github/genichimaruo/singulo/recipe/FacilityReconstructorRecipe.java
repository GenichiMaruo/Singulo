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
public final class FacilityReconstructorRecipe extends net.minecraft.world.item.crafting.NormalCraftingRecipe {
    private final ShapedRecipe delegate;

    private FacilityReconstructorRecipe(ShapedRecipe recipe) {
        super(new net.minecraft.world.item.crafting.Recipe.CommonInfo(recipe.showNotification()),
                new net.minecraft.world.item.crafting.CraftingRecipe.CraftingBookInfo(recipe.category(), recipe.group()));
        this.delegate = recipe;
    }

    private static boolean enabled() {
        return !ServerConfig.SPEC.isLoaded() || ServerConfig.RECIPE_ENABLED.get();
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return enabled() && delegate.matches(input, level);
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        return enabled() ? delegate.assemble(input) : ItemStack.EMPTY;
    }

    @Override
    protected net.minecraft.world.item.crafting.PlacementInfo createPlacementInfo() {
        return delegate.placementInfo();
    }

    @Override
    public java.util.List<net.minecraft.world.item.crafting.display.RecipeDisplay> display() {
        return enabled() ? delegate.display() : java.util.List.of();
    }

    @Override
    public RecipeSerializer<FacilityReconstructorRecipe> getSerializer() {
        return SinguloRecipes.FACILITY_RECONSTRUCTOR.get();
    }

    private static final MapCodec<FacilityReconstructorRecipe> CODEC =
            ShapedRecipe.MAP_CODEC.xmap(FacilityReconstructorRecipe::new, r -> r.delegate);
    private static final StreamCodec<RegistryFriendlyByteBuf, FacilityReconstructorRecipe> STREAM =
            ShapedRecipe.STREAM_CODEC.map(FacilityReconstructorRecipe::new, r -> r.delegate);
    public static final RecipeSerializer<FacilityReconstructorRecipe> SERIALIZER = new RecipeSerializer<>(CODEC, STREAM);
}
