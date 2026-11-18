package io.github.genichimaruo.singulo.android.work;

import java.util.List;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.FluidUtil;

/** Matches a plain source-fluid bucket to a slot that the active recipe empties and returns. */
public final class BucketCrafting {
    private BucketCrafting() {}

    public static ItemStack fluid(ItemStack stack) {
        if (!(stack.getItem() instanceof BucketItem || stack.is(Items.MILK_BUCKET)) || !stack.getComponentsPatch().isEmpty()) {
            return ItemStack.EMPTY;
        }
        // Only the fluid's own bucket is interchangeable. A fish bucket carries additional contents.
        return FluidUtil.getFluidContained(stack).filter(f -> f.getAmount() == FluidType.BUCKET_VOLUME
                && f.getFluid().getBucket() == stack.getItem()).map(FluidSamples::of).orElse(ItemStack.EMPTY);
    }

    static int eligible(Level level, RecipeData recipe) {
        if (!RecipeData.MODE_CRAFTING.equals(recipe.mode())) {
            return 0;
        }
        int mask = 0;
        for (int i = 0; i < recipe.grid().size(); i++) {
            if (!fluid(recipe.grid().get(i)).isEmpty()) {
                mask |= 1 << i;
            }
        }
        if (mask == 0) {
            return 0;
        }
        var grid = CraftingInput.ofPositioned(3, 3, recipe.gridList());
        var match = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid.input(), level);
        if (match.isEmpty()) {
            return 0;
        }
        var remainders = match.get().value().getRemainingItems(grid.input());
        for (int i = 0; i < 9; i++) {
            if ((mask & (1 << i)) != 0 && emptyBucketIndex(grid, remainders, i) < 0) {
                mask &= ~(1 << i);
            }
        }
        return mask;
    }

    /** Maps a full 3×3 card position to Minecraft's trimmed remainder grid. */
    public static int emptyBucketIndex(CraftingInput.Positioned grid, List<ItemStack> remainders, int slot) {
        int x = slot % 3 - grid.left();
        int y = slot / 3 - grid.top();
        int index = x + y * grid.input().width();
        if (x < 0 || x >= grid.input().width() || y < 0 || y >= grid.input().height() || index >= remainders.size()) {
            return -1;
        }
        ItemStack remainder = remainders.get(index);
        return remainder.is(Items.BUCKET) && remainder.getCount() == 1 && remainder.getComponentsPatch().isEmpty() ? index : -1;
    }
}
