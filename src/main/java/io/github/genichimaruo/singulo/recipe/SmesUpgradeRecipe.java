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
public final class SmesUpgradeRecipe extends NormalCraftingRecipe {
    private final ShapedRecipe delegate;
    private SmesUpgradeRecipe(ShapedRecipe recipe) {
        // Vanilla's result accessor does not inspect the registry provider.
        super(new Recipe.CommonInfo(recipe.showNotification()), new CraftingRecipe.CraftingBookInfo(recipe.category(), recipe.group()));
        this.delegate = recipe;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        ItemStack result = delegate.assemble(input);
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
            tag.putInt("energy", Math.clamp(data.copyTagWithoutId().getIntOr("energy", 0), 0, SmesCellBlockEntity.CAPACITY));
            result.set(DataComponents.BLOCK_ENTITY_DATA, net.minecraft.world.item.component.TypedEntityData.of(SinguloBlockEntities.SMES_MODULE.get(), tag));
        }
        return result;
    }

    @Override
    public RecipeSerializer<SmesUpgradeRecipe> getSerializer() { return SinguloRecipes.SMES_UPGRADE.get(); }

    public ShapedRecipe shapedRecipe() { return delegate; }
    @Override public boolean matches(CraftingInput input, net.minecraft.world.level.Level level) {
        return delegate.matches(input, level);
    }
    @Override protected PlacementInfo createPlacementInfo() { return delegate.placementInfo(); }
    @Override public java.util.List<net.minecraft.world.item.crafting.display.RecipeDisplay> display() { return delegate.display(); }
    public static final MapCodec<SmesUpgradeRecipe> CODEC = ShapedRecipe.MAP_CODEC.xmap(SmesUpgradeRecipe::new, r -> r.delegate);
    public static final StreamCodec<RegistryFriendlyByteBuf, SmesUpgradeRecipe> STREAM_CODEC = ShapedRecipe.STREAM_CODEC.map(SmesUpgradeRecipe::new, r -> r.delegate);
    public static final RecipeSerializer<SmesUpgradeRecipe> SERIALIZER = new RecipeSerializer<>(CODEC, STREAM_CODEC);
}
