package io.github.genichimaruo.singulo.recipe;

import com.google.gson.JsonObject;
import io.github.genichimaruo.singulo.machine.SmesCellBlockEntity;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloRecipes;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;

/** Shaped legacy upgrade; transfer only the consumed cell's bounded stored charge. */
public final class SmesUpgradeRecipe extends ShapedRecipe {
    private SmesUpgradeRecipe(ShapedRecipe recipe) {
        super(recipe.getId(), recipe.getGroup(), recipe.category(), recipe.getRecipeWidth(), recipe.getRecipeHeight(),
                recipe.getIngredients(), recipe.getResultItem(null).copy(), recipe.showNotification());
    }

    @Override
    public ItemStack assemble(CraftingContainer input, RegistryAccess registries) {
        ItemStack result = super.assemble(input, registries);
        if (!result.is(SinguloBlocks.SMES_MODULE.get().asItem()) || result.getCount() != 1) return ItemStack.EMPTY;
        ItemStack cell = ItemStack.EMPTY;
        for (int i = 0; i < input.getContainerSize(); i++) {
            ItemStack stack = input.getItem(i);
            if (!stack.is(SinguloBlocks.SMES_CELL.get().asItem())) continue;
            if (!cell.isEmpty()) return ItemStack.EMPTY;
            cell = stack;
        }
        if (cell.isEmpty()) return ItemStack.EMPTY;
        var data = cell.getTagElement("BlockEntityTag");
        if (data != null) {
            var tag = new CompoundTag();
            tag.putInt("energy", Math.max(0, Math.min(data.getInt("energy"), SmesCellBlockEntity.CAPACITY)));
            tag.putString("id", "singulo:smes_module");
            result.addTagElement("BlockEntityTag", tag);
        }
        return result;
    }

    @Override
    public RecipeSerializer<?> getSerializer() { return SinguloRecipes.SMES_UPGRADE.get(); }

    public static final class Serializer implements RecipeSerializer<SmesUpgradeRecipe> {
        private final ShapedRecipe.Serializer delegate = new ShapedRecipe.Serializer();
        @Override public SmesUpgradeRecipe fromJson(ResourceLocation id, JsonObject json) {
            return new SmesUpgradeRecipe(delegate.fromJson(id, json));
        }
        @Override public SmesUpgradeRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            return new SmesUpgradeRecipe(delegate.fromNetwork(id, buf));
        }
        @Override public void toNetwork(FriendlyByteBuf buf, SmesUpgradeRecipe recipe) {
            delegate.toNetwork(buf, recipe);
        }
    }
}
