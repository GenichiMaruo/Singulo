package io.github.genichimaruo.singulo.recipe;

import com.mojang.serialization.MapCodec;
import io.github.genichimaruo.singulo.machine.SmesCellBlockEntity;
import io.github.genichimaruo.singulo.registry.SinguloBlockEntities;
import io.github.genichimaruo.singulo.registry.SinguloBlocks;
import io.github.genichimaruo.singulo.registry.SinguloRecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;

/** A normal shaped upgrade that transfers only the single consumed cell's stored energy. */
public final class SmesUpgradeRecipe extends ShapedRecipe {
    private SmesUpgradeRecipe(ShapedRecipe recipe) {
        // Vanilla's result accessor does not inspect the registry provider.
        super(recipe.getGroup(), recipe.category(), recipe.pattern, recipe.getResultItem(null).copy(), recipe.showNotification());
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack result = super.assemble(input, registries);
        if (!result.is(SinguloBlocks.SMES_MODULE.get().asItem()) || result.getCount() != 1) return ItemStack.EMPTY;
        ItemStack cell = ItemStack.EMPTY;
        for (ItemStack stack : input.items()) {
            if (!stack.is(SinguloBlocks.SMES_CELL.get().asItem())) continue;
            if (!cell.isEmpty()) return ItemStack.EMPTY;
            cell = stack;
        }
        if (cell.isEmpty()) return ItemStack.EMPTY;
        var data = cell.get(DataComponents.BLOCK_ENTITY_DATA);
        if (data != null) {
            var tag = new CompoundTag();
            tag.putInt("energy", Math.clamp(data.copyTag().getInt("energy"), 0, SmesCellBlockEntity.CAPACITY));
            BlockItem.setBlockEntityData(result, SinguloBlockEntities.SMES_MODULE.get(), tag);
        }
        return result;
    }

    @Override
    public RecipeSerializer<?> getSerializer() { return SinguloRecipes.SMES_UPGRADE.get(); }

    public static final class Serializer implements RecipeSerializer<SmesUpgradeRecipe> {
        private static final MapCodec<SmesUpgradeRecipe> CODEC = ShapedRecipe.Serializer.CODEC.xmap(SmesUpgradeRecipe::new, r -> r);
        private static final StreamCodec<RegistryFriendlyByteBuf, SmesUpgradeRecipe> STREAM =
                ShapedRecipe.Serializer.STREAM_CODEC.map(SmesUpgradeRecipe::new, r -> r);
        @Override public MapCodec<SmesUpgradeRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, SmesUpgradeRecipe> streamCodec() { return STREAM; }
    }
}
