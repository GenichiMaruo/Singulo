package io.github.genichimaruo.singulo.api.android;

import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

/** Detached recipe description, not an execution or material-reservation guarantee.
 * Quantities for liquid samples are millibuckets; other quantities are item counts.
 * Alternatives are display candidates; ingredientJson preserves the actual predicate.
 * Catalysts can wear out and fuel is replenished as needed, not necessarily once per craft.
 */
public record AndroidRecipe(String mode, BlockPos location, List<Product> outputs,
                            List<Material> materials, boolean fuelFill) {
    public AndroidRecipe {
        Objects.requireNonNull(mode); location = location.immutable();
        outputs = List.copyOf(outputs); materials = List.copyOf(materials);
    }
    public record Product(ItemStack sample, long count, boolean liquid) {
        public Product { sample = sample.copyWithCount(1); }
        @Override public ItemStack sample() { return sample.copy(); }
    }
    public record Material(ItemStack sample, long count, List<ItemStack> alternatives,
                           String ingredientJson, boolean catalyst, boolean fuel, boolean liquid) {
        public Material {
            sample = sample.copyWithCount(1);
            alternatives = alternatives.stream().map(ItemStack::copy).toList();
            Objects.requireNonNull(ingredientJson);
        }
        @Override public ItemStack sample() { return sample.copy(); }
        @Override public List<ItemStack> alternatives() { return alternatives.stream().map(ItemStack::copy).toList(); }
    }
}
